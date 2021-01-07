package de.tum.ftm.agentsim.ts.simobjects.rebalancing;

import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectStatus;
import de.tum.ftm.agentsim.ts.simobjects.Vehicle;
import de.tum.ftm.agentsim.ts.utils.Position;
import de.tum.ftm.agentsim.ts.utils.SimTime;
import org.locationtech.jts.geom.Geometry;

import java.util.List;
import java.util.TreeMap;

/**
 * Inner class to store the information for a city district
 */
public class RelocationDistrict implements Comparable<RelocationDistrict> {
    int cityDistrictID;
    Geometry districtGeometry;
    int fleetSize;
    int currentDistrictRequestDemand = 0;

    double currentTimeRequestShare = 0;
    int targetVehiclesInDistrictCount = 0;
    int currentVehiclesInDistrictCount = 0;
    int vehicleBalance = 0;  // + if too many vehicles, - if too less vehicles
    double vehicleBalanceDeviation = 0;

    RelocationDistrict(int cityDistrictID, Geometry districtGeometry) {
        this.cityDistrictID = cityDistrictID;
        this.districtGeometry = districtGeometry;
    }

    /**
     * Calculate the current distribution of vehicles in the city districts
     *
     * @param fleet Fleet of the vehicles
     * @param relocationDataMap Distribution data for all districts for a given time
     */
    void calculateVehicleBalance(List<Vehicle> fleet, TreeMap<SimTime, RelocationTimeData> relocationDataMap) {
        RelocationTimeData currentTimeRelocationData = relocationDataMap.ceilingEntry(SimTime.now()).getValue();
        fleetSize = fleet.size();

        // What percentage of all requests in this time-window are in this district
        currentTimeRequestShare = currentTimeRelocationData.getDistrictShares().get(cityDistrictID);

        // Absolute amount of requests in this district in this time-window
        currentDistrictRequestDemand = (int) Math.ceil(currentTimeRelocationData.getAmtRequests() * currentTimeRequestShare);

        // Target-amount of vehicles which should be in this district according to the share of requests during this time-window
        targetVehiclesInDistrictCount = (int) Math.round(currentTimeRequestShare * fleetSize);

        // Determine current amount of vehicles in this district
        currentVehiclesInDistrictCount = (int) fleet.stream()
                .filter(v -> v.getCurrentCityDistrict() == this)
                .count();

        calculateDistrictVehicleDeviation();
    }


    /**
     * Calculate the actual number of surplus/missing vehicles in a district
     * vehicleBalance: (-) vehicles missing in district, (+) too many vehicles in district)
     * vehicleBalanceDeviation: > 1, if surplus vehicles in district; < 1, if too less vehicles in district
     *
     */
    private void calculateDistrictVehicleDeviation() {
        // Maximum target-amount of vehicles in a district is limited to the amount of requests in this district
        if (targetVehiclesInDistrictCount > currentDistrictRequestDemand) targetVehiclesInDistrictCount = currentDistrictRequestDemand;

        // Calculate absolute vehicle balance (only for better understanding / logging)
        vehicleBalance = currentVehiclesInDistrictCount - targetVehiclesInDistrictCount;

        // calculate vehicle excess/shortage ratio; > 1, if surplus vehicles in district; < 1, if too few vehicles in district
        if (targetVehiclesInDistrictCount == 0) {
            vehicleBalanceDeviation = currentVehiclesInDistrictCount+1;
        } else {
            vehicleBalanceDeviation = (double) currentVehiclesInDistrictCount / targetVehiclesInDistrictCount;
        }
    }

    @Override
    public int compareTo(RelocationDistrict o) {
        if (this.vehicleBalanceDeviation == o.vehicleBalanceDeviation) {
            double thisDev = this.vehicleBalanceDeviation + (double) this.cityDistrictID/1000;
            double thatDev = o.vehicleBalanceDeviation + (double) o.cityDistrictID/1000;
            return Double.compare(thisDev, thatDev);
        } else {
            return Double.compare(this.vehicleBalanceDeviation, o.vehicleBalanceDeviation);
        }
    }

    public int getCityDistrictID() {
        return cityDistrictID;
    }

    public int getVehicleDeviation() {
        return vehicleBalance;
    }

    public double getVehicleBalanceDeviation() {
        return vehicleBalanceDeviation;
    }

    public synchronized void decrementVehiclesInDistrict() {
        this.currentVehiclesInDistrictCount--;
        calculateDistrictVehicleDeviation();
    }

    public synchronized void incrementVehiclesInDistrict() {
        this.currentVehiclesInDistrictCount++;
        calculateDistrictVehicleDeviation();
    }

    public int getCurrentVehiclesInDistrictCount() {
        return currentVehiclesInDistrictCount;
    }

    public void setCurrentVehiclesInDistrictCount(int currentVehiclesInDistrictCount) {
        this.currentVehiclesInDistrictCount = currentVehiclesInDistrictCount;
    }

    public Position getDistrictCenterPosition() {
        return new Position(districtGeometry.getCentroid().getX(), districtGeometry.getCentroid().getY());
    }
}
