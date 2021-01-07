package de.tum.ftm.agentsim.ts.assignmentStrategy.vehicleListProvider;

import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.routing.CityGridRouter;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectStatus;
import de.tum.ftm.agentsim.ts.simobjects.User;
import de.tum.ftm.agentsim.ts.simobjects.Vehicle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;

import static de.tum.ftm.agentsim.ts.simobjects.SimObject.scenario;

/**
 * Implementation of the VehicleListProvider-Interface. This implementation returns a sorted list of vehicles in the
 * surroundings of the travel-request with following restrictions:
 * - vehicles within the maximum waiting time (config-file)
 * - vehicles have vacant seats considered
 * - number of returned vehicles is limited (config-file)
 * - vehicle list is sorted according to the estimated travel time to the travel-request
 *
 * - duration for the vehicle to reach the request is calculated with the RoutingGrid, therefore a
 * search-range-factor (config-file) is added to include extra vehicles, which might be ignored,
 * if the RoutingGrid returned a too long travel-time but the GraphHopper Routing would return a shorter travel-time
 *
 * @author Manfred Klöppel
 */
public class VehicleListProviderIsochrone implements VehicleListProvider {

    private User.TripRequest userRequest;

    public VehicleListProviderIsochrone(User.TripRequest request) {
        this.userRequest = request;
    }

    /**
     * Iteratively searches for vehicles around the position of the request. Iteration will stop, if more vehicles
     * than the maximum list size are found.
     *
     * @param onlyIdleVehicles Only return vehicles which are idle
     * @return List of vehicles
     */
    @Override
    public ArrayList<Vehicle> getVehicleList(boolean onlyIdleVehicles) {

        // Vehicle list is ordered by ascending travel-duration of the vehicle to the user
        TreeSet<VehicleScore> vehicleList = new TreeSet<>();

        // Iteratively increase search radius to first return closer vehicles
        for (double searchRangeFactor = 0.2; searchRangeFactor <= 1; searchRangeFactor += 0.2) {
            int searchRange = (int) Math.round(searchRangeFactor
                    * Config.MAX_WAITING_TIME_SECONDS);

            if (onlyIdleVehicles) {
                scenario.getSimObjectController().getFleet().values()
                        .stream()
                        .filter(x -> (x.calculateDurationToPosition(userRequest.getOriginalRequestOrigin(), CityGridRouter.getInstance()) / 1000) <= searchRange
                                && x.getStatus() == SimObjectStatus.VEHICLE_IDLE)
                        .forEach(vehicle -> {
                            double score = calculateVehicleScore(vehicle);
                            vehicleList.add(new VehicleScore(vehicle, score));
                        });
            } else {
                scenario.getSimObjectController().getFleet().values()
                        .stream()
                        .filter(x -> (x.calculateDurationToPosition(userRequest.getOriginalRequestOrigin(), CityGridRouter.getInstance()) / 1000) <= searchRange
                                && x.hasVacantSeats(userRequest.getTotalPersons()))
                        .forEach(vehicle -> {
                            double score = calculateVehicleScore(vehicle);
                            vehicleList.add(new VehicleScore(vehicle, score));
                        });
            }

            // return, if the vehicle filter list size is reached
            if (vehicleList.size() > Config.VEHICLE_FILTER_LIST_SIZE) {
                break;
            } else {
                vehicleList.clear();
            }
        }
        int selectionRange = Math.min(Config.VEHICLE_FILTER_LIST_SIZE, vehicleList.size());
        Iterator iterator = vehicleList.iterator();
        ArrayList<Vehicle> returnList = new ArrayList<>();
        for (int i=0; i < selectionRange; i++) {
            if (iterator.hasNext()) {
                returnList.add(((VehicleScore) iterator.next()).getVehicle());
            } else {
                break;
            }
        }
        return returnList;
    }

    /**
     * Calculates a score for a vehicle to determine the order of the vehicles. In this case, vehicle ordering
     * happens according to the travel distance to reach the travel-request
     * @param vehicle Vehicle for which the scoring is calculated
     * @return A score for the vehicle depending on the travel-distance towards the travel-request
     */
    private double calculateVehicleScore(Vehicle vehicle) {
        // Calculate duration score with CityGridRouter in seconds
        double durationToUserScore = (double) vehicle.calculateDurationToPosition(userRequest.getOriginalRequestOrigin(), CityGridRouter.getInstance()) / 1000;

        // Calculate vehicle status score
        double vehicleStatusScore = 0;

        // Calculate vehicle passenger count score
        double vehiclePassengerCountScore = 0;

        // Return vehicle score
        return durationToUserScore + vehicleStatusScore + vehiclePassengerCountScore;
    }

    /**
     * Helper Class to store both the vehicle and the calculated vehicle score for correct
     * order in TreeSet
     */
    private static class VehicleScore implements Comparable<VehicleScore> {
        private Vehicle vehicle;
        private Double vehicleScore;

        VehicleScore(Vehicle vehicle, Double vehicleScore) {
            this.vehicle = vehicle;
            this.vehicleScore = vehicleScore;
        }

        public Vehicle getVehicle() {
            return vehicle;
        }

        public Double getVehicleScore() {
            return vehicleScore;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        /**
         * Compare two VehicleScore Objects for ordering. Returns 1 if both Vehicles have the same score,
         * to add both vehicles to the TreeSet, even if their score is the same.
         * Returns 0, if the comparing vehicle is the same, which results therein that the vehicle is added only
         * once to the set
         */
        @Override
        public int compareTo(VehicleScore o) {
            if (!vehicleScore.equals(o.vehicleScore)) {
                return Double.compare(vehicleScore, o.vehicleScore);
            } else if (equals(o)){
                return 0;
            } else {
                return 1;
            }
        }
    }
}
