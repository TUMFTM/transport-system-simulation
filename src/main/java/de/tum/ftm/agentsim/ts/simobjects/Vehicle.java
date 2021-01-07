package de.tum.ftm.agentsim.ts.simobjects;

import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.events.Event;
import de.tum.ftm.agentsim.ts.events.Event_VehicleNextActivity;
import de.tum.ftm.agentsim.ts.routing.RoutingException;
import de.tum.ftm.agentsim.ts.routing.RoutingInterface;
import de.tum.ftm.agentsim.ts.routing.route.Route;
import de.tum.ftm.agentsim.ts.routing.route.RouteStep;
import de.tum.ftm.agentsim.ts.routing.route.RouteStepEnroute;
import de.tum.ftm.agentsim.ts.routing.route.RouteStepStationary;
import de.tum.ftm.agentsim.ts.simobjects.rebalancing.RelocationDistrict;
import de.tum.ftm.agentsim.ts.utils.Position;
import de.tum.ftm.agentsim.ts.utils.SimTime;
import org.pmw.tinylog.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Represents a Vehicle-Agent which extends SimObjectRoutable. Contains the logic for picking-up/dropping-off
 * Users and logging of movement.
 * Energy consumption is calculated based on driven distance and numbers of passengers on board.
 *
 * @author Manfred Klöppel
 */
public class Vehicle extends SimObjectRoutable {

    // Vehicle Parameters
    private int passengers = 0;                 // Number of passengers on the vehicle
    private int capacity;                       // Passenger capacity of the vehicle
    private double kwhPer100Km = 0;             // Energy consumption in Kwh per 100 km
    private double kwhPer100KmPerPax = 0;       // Energy consumption in Kwh per 100 km per Person on board
    private Event upcomingEvent;                // Upcoming event of the vehicle, which is stored in Master-EventList
    private HashMap<Long, User.TripRequest> userRequestMap;   // Map of User-TravelRequests assigned to vehicle. Key is the request-ID, Value the travelRequest-Object
    private RelocationDistrict currentCityDistrict;     // ID of the current/assigned city district (only used for relocation-purpose)

    // Logging values
    private int maxSimultaneousRequests = 0;    // Max simultaneous requests in vehicle (for logging)
    private int maxSimultaneousPassengers = 0;  // Max simultaneous persons in vehicle (for logging)
    private int servedRequests = 0;             // Number of requests served by vehicle (for logging)
    private int servedPassengers = 0;           // Number of persons served by vehicle (for logging)
    private long durationIdleSumMS = 0;
    private long durationIdleMaxMS = 0;
    private long durationRelocationSumMS = 0;
    private long durationBusyDrivingSumMS = 0;
    private long durationBusyDwellingSumMS = 0;

    private double energyConsumptionKwh = 0;    // Total energy consumption in Kwh


    public Vehicle(long id, Position position, int capacity, double kwhPer100Km, double kwhPer100KmPerPax) {
        super(id, position);
        this.status = SimObjectStatus.VEHICLE_IDLE;
        this.capacity = capacity;
        this.kwhPer100Km = kwhPer100Km;
        this.kwhPer100KmPerPax = kwhPer100KmPerPax;
        userRequestMap = new HashMap<>();
    }


    /**
     * Adds a request to the vehicle and updates the status of the user to USER_WAITING_FOR_PICKUP as well as calculates
     * the latest possible time for pickup of the user. Call Vehicle.updateRoute(route) first, to avoid faulty request
     * amount, when ENROUTE-Trips until assignment of request are stored to route-history
     *
     * @param userRequest TravelRequest which is assigned to the vehicle
     */
    public void addRequestToVehicle(User.TripRequest userRequest) {
        userRequest.setBookingPickupLatest(this, false);
        userRequest.getUser().setStatus(SimObjectStatus.USER_WAITING_FOR_PICKUP);
        userRequestMap.put(userRequest.getRequestID(), userRequest);
    }


    /**
     * Sets or updates the current route of the vehicle. Updates the routeHistory of the vehicle and all passengers on
     * the vehicle by inserting a "ROUTE_CHANGE" routeStep. In case the vehicle was in an "ENROUTE"-routeStep, the
     * route to the current position is stored to routeHistory. In case the vehicle is in an "PICKUP/DROPOFF"-routeStep
     * the updated/new route will only start after the current routeStep is completed.
     *
     * Call "addRequestToVehicle()" first, to ensure correct data of passengers/requests is stored in routeHistory.
     *
     * @param newRoute Updated/new route for this vehicle
     */
    public void updateRoute(Route newRoute) throws RoutingException {
        Logger.trace("Route update for Vehicle {} (Status: {}))", this.getId(), this.getStatus());

        // ---------- BEGIN update route history of vehicle and passengers

        // Create stationary routeStep for routeHistory to indicate position of route change
        RouteStepStationary rsts;
        if ((getCurrentRouteStep() != null) && (!getCurrentRouteStep().isInterruptible())) {  // true, if vehicle is currently at Pickup/Dropoff
                rsts = new RouteStepStationary(new SimTime(
                        getCurrentRouteStep().getEndTime().getTimeMillis()), 1,
                        RouteStep.StepType.ROUTE_UPDATE, this.getPosition());
        } else {
            rsts = new RouteStepStationary(new SimTime(SimTime.now().getTimeMillis()), 1,
                    RouteStep.StepType.ROUTE_UPDATE, this.getPosition());
        }
        // Update the passenger & request count for rsts
        rsts.setRequestsAndPassengerCount(this);

        // If vehicle has no routeHistory yet, create one
        if (routeHistory == null) {
            logIdleDuration();
            routeHistory = new Route(rsts);
        }

        // Only do the following, if vehicle was not IDLE
        if (upcomingEvent != null && getCurrentRouteStep() != null) {
            // If the vehicle is currently "ENROUTE", a routeStepStationary-ROUTE_CHANGE at the current position is
            // created and stored to routeHistory
            RouteStepEnroute rste = null;
            if (getCurrentRouteStep().isInterruptible()) {
                rste = new RouteStepEnroute(getCurrentRouteStep().getStartPosition(), this.getPosition(),
                        getCurrentRouteStep().getStepType(), getCurrentRouteStep().getStartTime(), SimTime.now());

                // Update the passenger & request count for rste
                rste.setRequestsAndPassengerCount(this);
                this.routeHistory.appendRouteStep(rste, false);

                // Update vehicle energy consumption & vehicle driving duration
                updateVehicleDurations(rste, false);
                energyConsumptionKwh += calculateEnergyConsumption(rste.getDistanceM()/1000, passengers);
            }
            // Add "ROUTE_CHANGE" routeStep to routeHistory
            this.routeHistory.appendRouteStep(rsts, false);

            // Update the routeHistory of all users, which are already in the vehicle
            for (User.TripRequest req : userRequestMap.values()) {
                if (req.getUser().getStatus() == SimObjectStatus.USER_IN_TRANSIT) {
                    if (getCurrentRouteStep().isInterruptible()) {
                        req.getRouteHistory().appendRouteStep(rste, false);

                        // If more passengers are onboard than persons included in this request, this trip is shared
                        if (this.passengers > req.getTotalPersons()) {
                            req.setTripIsShared();
                        }
                    }
                    req.getRouteHistory().appendRouteStep(rsts, false);
                }
            }
        } else {
            logIdleDuration();
        }
        // ---------- END update route history vehicle and passengers

        if (newRoute != null) {
            // following steps should not be done, if vehicle is picking-up or dropping off
            if (getCurrentRouteStep() == null || getCurrentRouteStep().isInterruptible()) {
                // Clear upcoming event from scenario-event-list; no effect, if upcomingEvent == null
                scenario.removeEvent(upcomingEvent);

                // set next routestep, and add the remaining routesteps to the upcoming route
                setCurrentRouteStep(newRoute.getRouteSteps().first());
                newRoute.getRouteSteps().remove(getCurrentRouteStep());

                // create upcoming event from current routestep
                this.upcomingEvent = new Event_VehicleNextActivity(new SimTime(getCurrentRouteStep().getEndTime()), this);
                scenario.addEvent(upcomingEvent);
            }

            this.route = newRoute;
            if (currentRouteStep.getStepType() == RouteStep.StepType.ENROUTE_RELOCATION) {
                this.setStatus(SimObjectStatus.VEHICLE_RELOCATING);
            } else {
                this.setStatus(SimObjectStatus.VEHICLE_IN_SERVICE);
            }
        } else {
            // Case of stopping the vehicle (e.g. request-assignments to the vehicle are revoked)
            Logger.trace("Vehicle {} is unassigned", id);
            scenario.removeEvent(upcomingEvent);
            setCurrentRouteStep(null);
            getUserRequestMap().clear();
            route = null;
            this.setStatus(SimObjectStatus.VEHICLE_IDLE);
            if (Config.ENABLE_REBALANCING) {
                if (scenario.getSimObjectController().getRelocationManager().upcomingRelocationEvents()) {
                    scenario.getSimObjectController().getRelocationManager().relocateSingleVehicle(this);
                }
            }
        }
        Logger.trace("New status of Vehicle {}: {}", this.getId(), this.getStatus());
    }

    private void updateVehicleDurations(RouteStep rs, boolean simulationIsInterrupted) {
        long duration;
        if (simulationIsInterrupted) {
            duration = SimTime.now().getTimeMillis() - rs.getStartTime().getTimeMillis();
        } else {
            duration = rs.getDurationMS();
        }

        switch (rs.getStepType()) {
            case ENROUTE:
                durationBusyDrivingSumMS += duration;
                break;
            case ENROUTE_RELOCATION:
                durationRelocationSumMS += duration;
                break;
            case DROPOFF: case PICKUP:
                durationBusyDwellingSumMS += duration;
                break;
            default:
                break;
        }
    }

    public void logIdleDuration() {
        if (status == SimObjectStatus.VEHICLE_IDLE) {
            long idleDurationMS;
            if (routeHistory != null) {
                idleDurationMS = SimTime.now().getTimeMillis() - this.routeHistory.getRouteEndTime().getTimeMillis();
            } else {
                idleDurationMS = SimTime.now().getTimeMillis() - SimTime.getSimulationStart().getTimeMillis();
            }
            durationIdleSumMS += Math.max(0, idleDurationMS);
            durationIdleMaxMS = Math.max(durationIdleMaxMS, idleDurationMS);
        } else {
            // Simulation was interrupted
            updateVehicleDurations(currentRouteStep, true);
        }
    }

    /**
     * This function is called whenever a VehicleNextEvent occurs. It updates the routeHistory of the vehicle and
     * any passenger and inserts the upcoming event for the vehicle in the simulation event-queue
     */
    public void nextActivity() {
        // ---------- BEGIN update route history of vehicle and passengers
        // At this stage "currentRouteStep" has been processed and is now actually the routeStep just passed

        // Store past routeStep to vehicle and user routeHistory
        currentRouteStep.setRequestsAndPassengerCount(this);
        this.routeHistory.appendRouteStep(getCurrentRouteStep(), false);
        updateVehicleDurations(getCurrentRouteStep(), false);

        for (User.TripRequest req : userRequestMap.values()) {
            if (req.getUser().getStatus() == SimObjectStatus.USER_IN_TRANSIT) {
                req.getRouteHistory().appendRouteStep(getCurrentRouteStep(), false);

                // If more passengers are onboard than persons included in this request and the routestep was not pickup/dropoff, this trip is shared
                if ((this.passengers > req.getTotalPersons()) && currentRouteStep.isInterruptible()) {
                    req.setTripIsShared();
                }
            }
        }
        // ---------- END update route history vehicle and passengers

        // Actions on now ending routestep
        if (currentRouteStep instanceof RouteStepEnroute) {
            // Update vehicle energy consumption
            energyConsumptionKwh += calculateEnergyConsumption(currentRouteStep.getDistanceM()/1000, passengers);
        } else if (currentRouteStep instanceof RouteStepStationary) {
            if (getCurrentRouteStep().getStepType() == RouteStep.StepType.DROPOFF) {
                // Set trip-completed time and reset User after he alighted
                getUserRequestMap().get(((RouteStepStationary) getCurrentRouteStep()).getRequestID()).completeTrip();

                // Update vehicle passenger/request counters
                int alightingPassengers = getUserRequestMap().get(((RouteStepStationary) getCurrentRouteStep())
                        .getRequestID()).getTotalPersons();
                passengers -= alightingPassengers;
                servedPassengers += alightingPassengers;
                servedRequests += 1;

                userRequestMap.remove(((RouteStepStationary) getCurrentRouteStep()).getRequestID());
            }
        }

        // Set next routeStep, if available
        if (route.getRouteSteps().size() > 0) {
            setCurrentRouteStep(route.getRouteSteps().first());
            route.getRouteSteps().remove(getCurrentRouteStep());
            upcomingEvent = new Event_VehicleNextActivity(new SimTime(getCurrentRouteStep().getEndTime()), this);
            scenario.addEvent(upcomingEvent);

            // Process route step
            switch (getCurrentRouteStep().getStepType()) {
                case DROPOFF:
                    // Update route history for affected request, as it will not be in the vehicle during the next
                    // routehistory-update anymore
//                    getUserRequestMap().get(((RouteStepStationary) getCurrentRouteStep()).getRequestID())
//                            .getRouteHistory().appendRouteStep(getCurrentRouteStep(), false);
                    getUserRequestMap().get(((RouteStepStationary) getCurrentRouteStep()).getRequestID()).dropoff(getCurrentRouteStep().getStartPosition());
                    Logger.trace("Vehicle {} dropping off request {}", this.getId(), getUserRequestMap().get(((RouteStepStationary) getCurrentRouteStep()).getRequestID()));

                    break;
                case PICKUP:
                    // Use endtime of pickup routestep as "pickup time"
                    getUserRequestMap().get(((RouteStepStationary) getCurrentRouteStep()).getRequestID())
                            .pickup(this.getId(), getCurrentRouteStep().getEndTime(), getCurrentRouteStep().getStartPosition(), false);
                    // Initialize RouteHistory for this Request
                    getUserRequestMap().get(((RouteStepStationary) getCurrentRouteStep()).getRequestID())
                            .initRouteHistory(getCurrentRouteStep());
                    Logger.trace("Vehicle {} picking up request {}", this.getId(), getUserRequestMap().get(((RouteStepStationary) getCurrentRouteStep()).getRequestID()));

                    // Update vehicle passenger/request counters
                    passengers += getUserRequestMap().get(((RouteStepStationary) getCurrentRouteStep()).getRequestID()).getTotalPersons();
                    updateMaxSimultaneousRequestsAndUsers();
                    if (passengers > capacity) {
                        Logger.error("More persons on-board than seats! Vehicle-ID: {}, Passengers: {}", id, passengers);
                    }
                    break;
                default:
                    Logger.trace("Vehicle {} going to next event", this.getId());
                    break;
            }
        } else {
            Logger.trace("Vehicle {} finished route", this.getId());
            // No next routeStep -> route finished -> Therefore reset the vehicle
            route = null;
            setCurrentRouteStep(null);
            upcomingEvent = null;
            SimObjectStatus oldStatus = this.status;
            this.setStatus(SimObjectStatus.VEHICLE_IDLE);

            if (Config.ENABLE_REBALANCING) {
                if (scenario.getSimObjectController().getRelocationManager().upcomingRelocationEvents() && oldStatus != SimObjectStatus.VEHICLE_RELOCATING) {
                    scenario.getSimObjectController().getRelocationManager().relocateSingleVehicle(this);
                }
            }
        }
    }


    /**
     * Checks the current amount of requests assigned to the vehicle and persons in the vehicle and updates
     * the corresponding maximum values for logging. This function should be called after each PICKUP.
     */
    private void updateMaxSimultaneousRequestsAndUsers() {
        int requestCount = userRequestMap.size();
        if (requestCount > maxSimultaneousRequests) maxSimultaneousRequests = requestCount;

        if (passengers > maxSimultaneousPassengers) {
            maxSimultaneousPassengers = passengers;
        }
    }


    /**
     * Updates the position of the vehicle and of all users which are on the vehicle.
     */
    @Override
    public void updatePosition() {
        super.updatePosition();

        for (User.TripRequest u : userRequestMap.values()) {
            if (u.getUser().getStatus() == SimObjectStatus.USER_IN_TRANSIT &&
                    (u.getUser().getCurrentRequest().getVehicleID() == this.id)) {
                u.getUser().setPosition(this.position);
            }
        }
    }


    /**
     * Returns the consumed energy for a given distance with amount of passengers
     *
     * @param drivingDistanceKM driving distance in kilometers
     * @param passengers number of passengers on board
     * @return amount of consumed energy in Kwh
     */
    private double calculateEnergyConsumption(double drivingDistanceKM, int passengers) {
        return drivingDistanceKM * (kwhPer100Km + passengers * kwhPer100KmPerPax) / 100;
    }


    /**
     * Returns the durationMIN the vehicle needs from its current position to the target position
     *
     * @param targetPosition position to which the vehicle should go
     * @return durationMIN in milliseconds
     */
    public long calculateDurationToPosition(Position targetPosition, RoutingInterface router) {
        long duration = Long.MAX_VALUE;

        try {
            var route = router.calculateRoute(this.position, targetPosition, Type.CAR, 0);
            duration = route.getDurationMS();

        } catch (Exception e) {
            Logger.error(e);
            Logger.error("Error while trying to route from {},{} to {},{}", this.position.getX(), this.position.getY(), targetPosition.getX(), targetPosition.getY());
        }
        return duration;
    }


    /**
     * Relocate idle vehicle to new location
     * @param targetPosition new location the vehicle will relocate to
     */
    public void relocate(Position targetPosition, RelocationDistrict targetDistrict) {

        assert (status == SimObjectStatus.VEHICLE_IDLE) || (status == SimObjectStatus.VEHICLE_RELOCATING): "Vehicle is not IDLE or ENROUTE_RELOCATION, cannot relocate";

        try {
            Route route = new Route(SimTime.now());

            // make ENROUTE to new location
            RouteStepEnroute rste = new RouteStepEnroute(position, targetPosition, RouteStep.StepType.ENROUTE_RELOCATION);
            route.appendRouteStep(rste, true);

            // make stationary point for destination
            route.appendRouteStep(new RouteStepStationary(SimTime.now(), 0,
                    RouteStep.StepType.RELOCATION_ARRIVED, rste.getEndPosition()), true);

            updateRoute(route);
            setCurrentCityDistrict(targetDistrict);

        } catch (Exception e) {
            Logger.error(e);
        }
    }


    /**
     * Checks if the provided number of persons would fit in the vehicle
     *
     * @param numberOfPassengers number of passengers which should fit in the vehicle
     * @return returns true, if the passengers would fit in the vehicle, otherwise returns false
     */
    public boolean hasVacantSeats(int numberOfPassengers) {
        return (capacity - passengers - numberOfPassengers) >= 0;
    }


    /**
     * @return true, if the vehicle is currently fully occupied
     */
    public boolean isFullyOccupied() {
        return capacity - passengers == 0;
    }


    /**
     * @return A list with all travel-requests which are waiting for pickup
     */
    public ArrayList<User.TripRequest> getRequestsWaitingForPickup() {
        return userRequestMap.values().stream()
                .filter(userRequest -> userRequest.getUser().getStatus() == SimObjectStatus.USER_WAITING_FOR_PICKUP)
                .collect(Collectors.toCollection(ArrayList::new));
    }


    // --- GETTER & SETTERS
    @Override
    public void setCurrentRouteStep(RouteStep currentRouteStep) {
        super.setCurrentRouteStep(currentRouteStep);
    }

    public double getDrivingDistanceKM() {
        if (routeHistory != null) {
            return routeHistory.getRouteDistanceKM();
        } else {
            return 0.0;
        }
    }

    public double getDrivingDurationMIN() {
        if (routeHistory != null) {
            return routeHistory.getRouteDurationMIN();
        } else {
            return 0.0;
        }
    }

    public double getEnergyConsumptionKwh() {
        return energyConsumptionKwh;
    }

    public int getMaxSimultaneousRequests() {
        return maxSimultaneousRequests;
    }

    public int getMaxSimultaneousPassengers() {
        return maxSimultaneousPassengers;
    }

    public int getServedRequests() {
        return servedRequests;
    }

    public int getPassengers() {
        return passengers;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getServedPassengers() {
        return servedPassengers;
    }

    public long getDurationIdleSumMS() {
        return durationIdleSumMS;
    }

    public long getDurationIdleMaxMS() {
        return durationIdleMaxMS;
    }

    public long getDurationRelocationSumMS() {
        return durationRelocationSumMS;
    }

    public long getDurationBusyDrivingSumMS() {
        return durationBusyDrivingSumMS;
    }

    public long getDurationBusyDwellingSumMS() {
        return durationBusyDwellingSumMS;
    }

    public HashMap<Long, User.TripRequest> getUserRequestMap() {
        return userRequestMap;
    }

    public RelocationDistrict getCurrentCityDistrict() {
        return currentCityDistrict;
    }

    public void setCurrentCityDistrict(RelocationDistrict currentCityDistrict) {
        this.currentCityDistrict = currentCityDistrict;
    }
}
