package de.tum.ftm.agentsim.ts.simobjects.rebalancing;

import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.events.Event_RebalancingManager;
import de.tum.ftm.agentsim.ts.routing.CityGridRouter;
import de.tum.ftm.agentsim.ts.simobjects.SimObject;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectStatus;
import de.tum.ftm.agentsim.ts.simobjects.Vehicle;
import de.tum.ftm.agentsim.ts.utils.Position;
import de.tum.ftm.agentsim.ts.utils.SimTime;
import de.tum.ftm.agentsim.ts.utils.UtilGeometry;
import de.tum.ftm.agentsim.ts.utils.UtilHeapMemoryInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.shape.random.RandomPointsBuilder;
import org.pmw.tinylog.Level;
import org.pmw.tinylog.Logger;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a static agent, which relocates the vehicles using a pre-defined vehicle distribution over
 * city districts
 *
 * @author Manfred Klöppel
 */
public class RebalancingManagerR5 extends SimObject implements RebalancingManagerInterface {

    TreeMap<SimTime, RelocationTimeData> rebalancingDataMap = new TreeMap<>();   // Vehicle Distribution data over time for each city district
    List<RelocationDistrict> districtList = new ArrayList<>();                            // List of city districts
    Event_RebalancingManager lastRebalancingEvent;

    // Singleton
    private static RebalancingManagerR5 INSTANCE = new RebalancingManagerR5();

    private RebalancingManagerR5() {
        super(0, null);
        importRelocationData(Config.REBALANCING_MAP_PATH);

        // Delete log file, if it already exits
        if (Config.LOG_REBALANCING) {
            File file = new File(Config.REBALANCING_LOG_PATH);
            try {
                boolean result = Files.deleteIfExists(file.toPath());
            } catch (IOException i) {
                Logger.error(i);
            }
        }
    }

    /**
     * Get the singleton instance.
     * @return RelocationManager Instance
     */
    public static RebalancingManagerR5 getInstance() {
        return INSTANCE;
    }


    /**
     * Adds all of the relocation events to the simulation event-queue
     * Adds relocation event every 5 minutes for idle vehicles, to smooth relocation movement
     */
    public void setupRebalancingEvents() {

        Logger.info("Setting up Rebalancing Events...");
        for (var updateEvent : rebalancingDataMap.entrySet()) {
            var rebalancingEvent = new Event_RebalancingManager(updateEvent.getKey());
            scenario.addEvent(rebalancingEvent);
            lastRebalancingEvent = rebalancingEvent;
        }

        if(!rebalancingDataMap.isEmpty()) {
            SimTime firstRebalancingTime = rebalancingDataMap.firstKey();
            SimTime lastRebalancingTime = rebalancingDataMap.lastKey();

            SimTime iterationTime = new SimTime(firstRebalancingTime);
            while (iterationTime.isLessThan(lastRebalancingTime)) {
                // insert events for every 5 simulation minute
                if (!rebalancingDataMap.containsKey(iterationTime)) scenario.addEvent(new Event_RebalancingManager(iterationTime));
                iterationTime = new SimTime(iterationTime, 1000*60*5);
            }
        }
    }

    @Override
    public void checkRebalancing() {
        if (rebalancingDataMap.containsKey(SimTime.now())) {
            // Logger.info("Relocating Fleet");
            relocateFleet();
            if (Logger.getLevel() == Level.DEBUG) {
                Logger.info(SimTime.now());
                UtilHeapMemoryInfo.showInfo();
            }
        } else {
            // Logger.info("Relocating IDLE Fleet");

            List<Vehicle> fleet = new ArrayList<>(scenario.getSimObjectController().getFleet().values());

            fleet.stream()
                    .filter(v -> (v.getStatus().equals(SimObjectStatus.VEHICLE_IDLE)))
                    .forEach(this::relocateSingleVehicle);

            // Logger.info("Completed Relocating IDLE Fleet");

        }
    }

    /**
     * Main relocation function. Checks the current distribution of vehicles over all city-districts and compares
     * it with the target-distribution. Relocates the vehicles, if necessary.
     */
    public void relocateFleet() {
        List<Vehicle> fleet = new ArrayList<>(scenario.getSimObjectController().getFleet().values());

        // Update vehicle position
        scenario.getSimObjectController().updateFleetPositions(true);

        // Update district info of all vehicles, except relocating vehicles; then update district data about vehicle count; re-enable all vehicles for relocation
        districtList.parallelStream().forEach(district -> {
            fleet.stream()
                    .filter(v -> (v.getStatus() != SimObjectStatus.VEHICLE_RELOCATING && v.getPosition().geoFunc().within(district.districtGeometry)))
                    .forEach(v -> v.setCurrentCityDistrict(district));
        });
        districtList.forEach(district -> district.calculateVehicleBalance(fleet, rebalancingDataMap));

        if (Config.LOG_REBALANCING) logRelocationTarget(false);

        // List is sorted according to excess/missing vehicles
        // (first: district with highest shortage of vehicles, last: district with most excess vehicles)
        Collections.sort(districtList);
        RelocationDistrict highestShortage = districtList.get(0);

        // send vehicles to new district (- vehicles missing in district, + vehicles too much in district) until
        // there are no districts with any deficit anymore
        int relocationCounter = 0;
        while (highestShortage.getVehicleBalanceDeviation() < 1) {
            int destinationDistrictID = highestShortage.getCityDistrictID();
            RelocationDistrict destinationDistrict = highestShortage;

            Position relocationTarget = generateRelocationTarget(highestShortage);

            ArrayList<Vehicle> vehicleListAvailableForRelocation = getAvailableVehicles(fleet, destinationDistrict, relocationTarget);

            if (vehicleListAvailableForRelocation.size() > 0) {
                Vehicle relocationVehicle = getClosestVehicle(vehicleListAvailableForRelocation, relocationTarget);

                // update deviation numbers
                destinationDistrict.incrementVehiclesInDistrict();
                relocationVehicle.getCurrentCityDistrict().decrementVehiclesInDistrict();

                // relocate vehicle
                Logger.trace("Vehicle {} is relocated from district {} to district {}",
                        relocationVehicle.getId(), relocationVehicle.getCurrentCityDistrict().getCityDistrictID(), destinationDistrictID);
                relocationVehicle.relocate(relocationTarget, destinationDistrict);
                relocationCounter += 1;

                // update list of districts according to new excess/missing vehicles
                Collections.sort(districtList);
                highestShortage = districtList.get(0);
            } else {
                Logger.trace("No vehicles available for relocation");
                break;
            }
        }
        if (Config.LOG_REBALANCING) logRelocationResult();
        Logger.trace(String.format("%s Vehicles relocated", relocationCounter));
    }

    private Vehicle getClosestVehicle(List<Vehicle> fleet, Position relocationTarget) {
        long minDistance = Long.MAX_VALUE;
        Vehicle returnVehicle = fleet.get(0);
        for (Vehicle v : fleet) {
            long durationToTarget = v.calculateDurationToPosition(relocationTarget, CityGridRouter.getInstance());
            if (durationToTarget < minDistance) {
                returnVehicle = v;
                minDistance = durationToTarget;
            }
        }
        return returnVehicle;
    }

    private ArrayList<Vehicle> getAvailableVehicles(List<Vehicle> fleet, RelocationDistrict destinationDistrict, Position relocationTarget) {
        return fleet.parallelStream()
                .filter(v ->
                        (v.getStatus() == SimObjectStatus.VEHICLE_RELOCATING || v.getStatus() == SimObjectStatus.VEHICLE_IDLE)
                                && v.getCurrentCityDistrict() != destinationDistrict
                                && v.getCurrentCityDistrict().getVehicleBalanceDeviation()>1) // has more vehicles than target
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Logs the current and target vehicle amount within a relocation district at the current relocation-event
     * @param idleMode Records, which relocation-mode is present
     */
    private void logRelocationTarget(boolean idleMode) {
        File logFile = new File(Config.REBALANCING_LOG_PATH);

        // Sort district list according to district-ID for correct printout
        districtList.sort(Comparator.comparingInt(RelocationDistrict::getCityDistrictID));

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true)))
        {
            int lineLength = districtList.size() * 5 + 12;
            String timeString = String.format("%s-%s", SimTime.now(), idleMode);
            String headerString = String.format("%-" + lineLength + "s", timeString);
            headerString = headerString.replace(" ", "-") + "\n";

            StringBuilder districtString = new StringBuilder("DISTRICT        ");
            for (var district : districtList) {
                districtString.append(String.format("%-5s", district.getCityDistrictID()));
            }
            districtString.append("\n");

            StringBuilder logCurrentString = new StringBuilder("CURRENT         ");
            for (var district : districtList) {
                logCurrentString.append(String.format("%-5s", district.currentVehiclesInDistrictCount));
            }
            logCurrentString.append("\n");

            StringBuilder logTargetString = new StringBuilder("TARGET          ");
            for (var district : districtList) {
                logTargetString.append(String.format("%-5s", district.targetVehiclesInDistrictCount));
            }
            logTargetString.append("\n");

            bw.write(headerString + districtString + logCurrentString + logTargetString);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Logs the result after the processing of an relocation-event
     */
    private void logRelocationResult() {
        File logFile = new File(Config.REBALANCING_LOG_PATH);

        // Sort district list according to district-ID for correct printout
        districtList.sort(Comparator.comparingInt(RelocationDistrict::getCityDistrictID));

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true)))
        {
            int lineLength = districtList.size() * 5 + 12;
            String footerString = String.format("%" + lineLength + "s", "         ");
            footerString = footerString.replace(" ", "-") + "\n\n";

            StringBuilder logResultString = new StringBuilder("RESULT          ");
            for (var district : districtList) {
                logResultString.append(String.format("%-5s", district.currentVehiclesInDistrictCount));
            }
            logResultString.append("\n");

            bw.write(logResultString + footerString);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * After a vehicle finished all its jobs and switches to the IDLE-state, it can query the relocation-manager
     * to relocate to a relocation-district with too few vehicles
     * @param vehicle The vehicle which should be relocated
     */
    public void relocateSingleVehicle(Vehicle vehicle) {
        if (vehicle.getCurrentCityDistrict() != null) {
            // Determine which district the vehicle is in currently
            for (var district : districtList) {
                if (vehicle.getPosition().geoFunc().within(district.districtGeometry)) {
                    var currentDistrictID = district.cityDistrictID;

                    // Update the number of vehicles in the district where the vehicle was allocated, in case the
                    // vehicle has changed the district since the last execution of the relocation manager
                    if (currentDistrictID != vehicle.getCurrentCityDistrict().cityDistrictID) {
                        vehicle.getCurrentCityDistrict().decrementVehiclesInDistrict();
                        district.incrementVehiclesInDistrict();
                        vehicle.setCurrentCityDistrict(district);
                    }

                    ArrayList<RelocationDistrict> minusList = districtList.stream()
                            .filter(d -> d.getVehicleBalanceDeviation() < 0.8 && !vehicle.getCurrentCityDistrict().equals(d))
                            .collect(Collectors.toCollection(ArrayList::new));

                    if (minusList.size() > 0) {
                        long min_duration = Long.MAX_VALUE;
                        RelocationDistrict relocationTargetDistrict = minusList.get(0);

                        for (RelocationDistrict district1 : minusList) {
                            long durationToDistrict = vehicle.calculateDurationToPosition(district1.getDistrictCenterPosition(), CityGridRouter.getInstance());
                            if (durationToDistrict < min_duration) {
                                relocationTargetDistrict = district1;
                                min_duration = durationToDistrict;
                            }
                        }
                        // update deviation numbers
                        relocationTargetDistrict.incrementVehiclesInDistrict();
                        vehicle.getCurrentCityDistrict().decrementVehiclesInDistrict();

                        // relocate vehicle
                        Logger.trace("Single Vehicle {} is relocated from district {} to district {}",
                                vehicle.getId(), vehicle.getCurrentCityDistrict().getCityDistrictID(), relocationTargetDistrict.getCityDistrictID());
                        vehicle.relocate(generateRelocationTarget(relocationTargetDistrict), relocationTargetDistrict);
                    }
                    return;
                }
            }
        }
    }


    /**
     * Generate random point for relocation in a given district
     *
     * @param district The district within which the random point should be generated
     * @return New random Position within given district
     */
    private Position generateRelocationTarget(RelocationDistrict district) {
        RandomPointsBuilder rndPointBuilder = new RandomPointsBuilder();
        rndPointBuilder.setNumPoints(1);
        rndPointBuilder.setExtent(district.districtGeometry);
        Point randomPoint = rndPointBuilder.getGeometry().getCentroid();
        return new Position(randomPoint.getX(), randomPoint.getY());
    }


    /**
     * Import of the Relocation data
     *
     * @param path Path to the relocation data
     */
    private void importRelocationData(String path) {
        //JSON parser object to parse read file

        try {
            String content = Files.readString(Paths.get(path), Charset.defaultCharset());
            JSONObject jsonFile = new JSONObject(content);

            // Load timestamps and total demand
            JSONObject absoluteData = (JSONObject) jsonFile.get("total_absolute_demand");
            JSONArray timeDataArray = absoluteData.names();

            for (var timeStamp : timeDataArray) {
                SimTime time = new SimTime(LocalDateTime.parse(timeStamp.toString(), DateTimeFormatter.ofPattern("yyy-MM-dd HH:mm")));
                rebalancingDataMap.put(time, new RelocationTimeData(time, (int) absoluteData.get(timeStamp.toString())));
            }

            JSONObject hourlyDistrictData = (JSONObject) jsonFile.get("data");
            JSONArray districtNames = hourlyDistrictData.names();

            boolean addToDistrictList = true;
            for (Map.Entry<SimTime, RelocationTimeData> entry : rebalancingDataMap.entrySet()) {
                for (var districtIDString : districtNames) {
                    JSONObject districtData = (JSONObject) hourlyDistrictData.get(districtIDString.toString());
                    JSONObject districtTimeData = (JSONObject) districtData.get("time_data");
                    String timestring = entry.getKey().toString();
                    double share = (double)(districtTimeData).get(timestring);

                    entry.getValue().getDistrictShares().put(Integer.parseInt(districtIDString.toString()), share);

                    // Load geometry data of districts
                    String geomDataString = (String) ((JSONObject) hourlyDistrictData.get(districtIDString.toString())).get("geom");
                    Geometry geom = UtilGeometry.makeGeometryFromWKT(geomDataString);
                    if (addToDistrictList) districtList.add(new RelocationDistrict(Integer.parseInt(districtIDString.toString()), geom));
                }
                addToDistrictList = false;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return True, if the last relocation event is later than the current Simulation-Time, else false
     */
    public boolean upcomingRelocationEvents() {
        return lastRebalancingEvent.getScheduledTime().isGreaterThan(SimTime.now());
    }

    public String getRelocationManagerType() {
        return "R5 RebalancingManager";
    }

}
