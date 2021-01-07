package de.tum.ftm.agentsim.ts.assignmentStrategy.jspritSolver;

import com.graphhopper.jsprit.analysis.toolbox.AlgorithmSearchProgressChartListener;
import com.graphhopper.jsprit.analysis.toolbox.Plotter;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.state.StateId;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.algorithm.state.UpdateMaxTimeInVehicle;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager;
import com.graphhopper.jsprit.core.problem.constraint.MaxTimeInVehicleConstraint;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.job.Delivery;
import com.graphhopper.jsprit.core.problem.job.Shipment;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.activity.*;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl.Builder;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.reporting.SolutionPrinter;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;
import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.routing.GraphHopperRouter;
import de.tum.ftm.agentsim.ts.routing.RoutingInterface;
import de.tum.ftm.agentsim.ts.routing.route.Route;
import de.tum.ftm.agentsim.ts.routing.route.RouteStep;
import de.tum.ftm.agentsim.ts.routing.route.RouteStepEnroute;
import de.tum.ftm.agentsim.ts.routing.route.RouteStepStationary;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectRoutable;
import de.tum.ftm.agentsim.ts.simobjects.User;
import de.tum.ftm.agentsim.ts.simobjects.Vehicle;
import de.tum.ftm.agentsim.ts.utils.Position;
import de.tum.ftm.agentsim.ts.utils.SimTime;
import de.tum.ftm.agentsim.ts.routing.CityGridRouter;
import org.pmw.tinylog.Logger;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class to create and solve a VRP-Problem with a single vehicle and a single new travel-request.
 *
 * @author Manfred Klöppel, Alexander Schulz
 */
public class SingleRequestJSpritSolver {

    private AtomicInteger locationIndex = new AtomicInteger(0);
    private final int CAPACITY_INDEX = 0;

    /**
     * Method to define/build the VRP problem. The vehicle and requests are replicated with the corresponding jSprit-
     * Objects. A travel-request which is already assigned to the vehicle, but has not yet been picked up is a
     * "Shipment". If the travel-request was already picked up, it is called a "Delivery".
     * During this step also the latest pickup/drop-off times are calculated
     *
     * @param simVehicle The vehicle, which the new travel-request should be assigned to
     * @param newRequest The new travel-request
     * @return A jSprit VRP-Problem
     */
    public VehicleRoutingProblem buildVRP(Vehicle simVehicle, User.TripRequest newRequest) {

        // Create a temporary array which holds a copy of the requests, so that modifications do not alter the original requests
        ArrayList<User.TripRequest> tempUserRequestList = createTempVehRequestList(simVehicle, newRequest);

        // Get all unique stops with the passenger count
        Map<Position, LocationRequestCount> locationMap = createRequestCountAtLocationMap(tempUserRequestList);

        // create arrays to hold shipments and deliveries
        ArrayList<Shipment> shipmentList = new ArrayList<>();
        ArrayList<Delivery> deliveriesList = new ArrayList<>();

        for (User.TripRequest request : tempUserRequestList) {
            if (!request.wasPickedUp()) {
                // Request, which still needs to be picked up & dropped off -> create a shipment
                shipmentList.add(makeShipment(request, locationMap));

            } else if (request.wasPickedUp() && !request.wasDroppedOff()) {
                // Request is onboard of the vehicle -> create a delivery
                deliveriesList.add(makeDelivery(request, locationMap));
            }
        }

        // Setup the Builder for the VRP
        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
        vrpBuilder.addVehicle(makeJspritVehicle(simVehicle));
        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);

        // Add shipments/deliveries to vrp problem
        vrpBuilder.addAllJobs(shipmentList);
        vrpBuilder.addAllJobs(deliveriesList);

        // Create custom cost matrix, which contains the durations between all requests/pickups/dropoffs
        VehicleRoutingTransportCosts costMatrix = buildCostMatrix(vrpBuilder.build().getAllLocations());

        // Build and return the VRP Problem
        return vrpBuilder.setRoutingCost(costMatrix).build();
    }

    /**
     * Creates a temporary list which contains all requests already assigned to a vehicle plus the new request
     * @param simVehicle current vehicle
     * @param newUserRequest the new request
     * @return list with all requests
     */
    private ArrayList<User.TripRequest> createTempVehRequestList(Vehicle simVehicle, User.TripRequest newUserRequest) {
        // create a temporary array which holds a copy of the requests
        ArrayList<User.TripRequest> tempUserRequestList = new ArrayList<>(simVehicle.getUserRequestMap().values());

        // create a temporary copy of the new request
        User.TripRequest tempUserRequest = new User.TripRequest(newUserRequest);
        tempUserRequest.setBookingPickupLatest(simVehicle, true);
        tempUserRequestList.add(tempUserRequest);
        return tempUserRequestList;
    }

    /**
     * Creates hashmap with all unique Positions and sums up the number of persons at a position to
     * correctly determine the pickup/dropoff duration
     * @param tempUserRequestList all travel-requests of this VRP
     * @return Map with all unique positions and the count of persons boarding/alighting at this stop
     */
    private Map<Position, LocationRequestCount> createRequestCountAtLocationMap(List<User.TripRequest> tempUserRequestList) {
        // Create hashmap with all unique Positions and sum up the number of persons at a position to correctly determine pickup/dropoff duration
        Map<Position, LocationRequestCount> locationMap = new HashMap<>();
        for (var req : tempUserRequestList) {
            Position origin = req.getOriginalRequestOrigin();
            if (locationMap.containsKey(origin)) {
                locationMap.get(origin).incRequestAndPassengerCount(req.getTotalPersons());
            } else {
                locationMap.put(origin, new LocationRequestCount(req.getTotalPersons(), 1));
            }

            Position destination = req.getOriginalRequestDestination();
            if (locationMap.containsKey(destination)) {
                locationMap.get(destination).incRequestAndPassengerCount(req.getTotalPersons());
            } else {
                locationMap.put(destination, new LocationRequestCount(req.getTotalPersons(), 1));
            }
        }
        return locationMap;
    }

    /**
     * Creates a jSprit-Shipment from a TravelRequest, which has not yet been picked-up by the vehicle.
     * Sets the time-restrictions for pickup and maximum in vehicle time
     * @param request TravelRequest-Object
     * @param locationMap Map of all locations to determine the duration for pickup and drop-off
     * @return jSprit-Shipment Object
     */
    private Shipment makeShipment(User.TripRequest request, Map<Position, LocationRequestCount> locationMap) {
        Location pickupLocation = getLocationFromPosition(request.getOriginalRequestOrigin());
        Location deliveryLocation = getLocationFromPosition(request.getOriginalRequestDestination());

        var shipmentBuilder = Shipment.Builder.newInstance(String.format("%s", request.getRequestID()));
        shipmentBuilder
                .setName(String.format("%s", request.getUser().getId()))
                .addSizeDimension(CAPACITY_INDEX, request.getTotalPersons())
                .setPickupLocation(pickupLocation)
                .setPickupTimeWindow(TimeWindow.newInstance(0, request.getTripPickupLatest()
                        .getTimeMillis()-SimTime.now().getTimeMillis()))
                .setDeliveryLocation(deliveryLocation)
                .setMaxTimeInVehicle(calcMaxTimeInVehicle(request))
                .setPickupServiceTime(getServiceDuration(locationMap, request.getOriginalRequestOrigin()))
                .setDeliveryServiceTime(getServiceDuration(locationMap, request.getOriginalRequestDestination()));

        return shipmentBuilder.build();
    }

    /**
     * Helper function to determine the maximum in-vehicle time dependent on the configuration
     * @param request original TravelRequest
     * @return Maximum duration of the request in the vehicle in Milliseconds
     */
    private long calcMaxTimeInVehicle(User.TripRequest request) {
        if (Config.ENABLE_ALONSO_TRAVEL_DELAY_MODE) {
            return (long) (1000 * (request.getRequestDuration() * 60 + Config.USER_ALONSO_MAX_DELAY_SECONDS));
        } else {
            long elongationMaxDurationInVehicle = (long) (request.getRequestDuration() * 60 * 1000 * Config.MAX_IN_VEH_TIME_ELONGATION_FACTOR);
            long acceptableMaxDurationInVehicle = (long) Config.ACCEPTABLE_TIME_IN_VEH_SECONDS * 1000;

            return Math.max(elongationMaxDurationInVehicle, acceptableMaxDurationInVehicle);
        }
    }

    /**
     * Creates a jSprit-Delivery from a TravelRequest, which has already been picked-up by the vehicle.
     * Sets the time-restrictions for drop-off
     * @param request TravelRequest-Object
     * @param locationMap Map of all locations to determine the duration for pickup and drop-off
     * @return jSprit-Shipment Object
     */
    private Delivery makeDelivery(User.TripRequest request, Map<Position, LocationRequestCount> locationMap) {
        Location deliveryLocation = getLocationFromPosition(request.getOriginalRequestDestination());

        double timeWindowStart = 0;
        // if request is still boarding, make sure, that delivery timewindows starts only after boarding is completed
        if (!request.isBoardingCompleted()) {
            timeWindowStart = (double) (request.getTripDeparture().getTimeMillis() - SimTime.now().getTimeMillis());
            assert timeWindowStart >= 0 : String.format("timeWindowStart must be positive! (%s)", timeWindowStart);
        }

        var deliveryBuilder = Delivery.Builder.newInstance(String.format("%s", request.getRequestID()));
        deliveryBuilder
                .setName(String.format("%s", request.getUser().getId()))
                .addSizeDimension(CAPACITY_INDEX, request.getTotalPersons())
                .setLocation(deliveryLocation)
                .setTimeWindow(TimeWindow.newInstance(timeWindowStart, (request.getTripDropoffLatest()
                        .getTimeMillis())-SimTime.now().getTimeMillis()))
                .setServiceTime(getServiceDuration(locationMap, request.getOriginalRequestDestination()));

        return deliveryBuilder.build();
    }

    /**
     * Calculates the duration for dropoff/pickup at a given position depending on the number of persons alighting/
     * boarding at this specific position. Returns the dropoff/alighting duration per person.
     * @param locationMap contains all positions of all requests and the number of persons alighting&boarding at this position
     * @param pos position for which the dropoff/pickup duration should be calculated
     * @return returns the pickup/dropoff duration for one person at this position in milliseconds
     */
    private double getServiceDuration(Map<Position, LocationRequestCount> locationMap, Position pos) {
        return ((Config.VEHICLE_PICKUP_DROPOFF_DELAY_SECONDS + Config.PICKUP_DROPOFF_DURATION_PER_PERSON_SECONDS
                * (double) locationMap.get(pos).getPassengerCount()) / (double) locationMap.get(pos).getRequestCount()) * 1000;
    }

    /**
     * Creates a jSprit-Vehicle from the original Vehicle
     * @param simVehicle Original Vehicle
     * @return jSprit-Vehicle Object
     */
    private VehicleImpl makeJspritVehicle(Vehicle simVehicle) {
        // create the vehicle type
        VehicleType vehicleType = VehicleTypeImpl.Builder.newInstance("VT " + String.format("%s", simVehicle.getCapacity()))
                .addCapacityDimension(CAPACITY_INDEX, simVehicle.getCapacity())
                .setCostPerTransportTime(1)
                .setCostPerDistance(1)
                .setCostPerWaitingTime(0)
                .setFixedCost(0)
                .build();

        Location vehicleLocation = getLocationFromPosition(simVehicle.getPosition());

        // If the vehicle is currently stationary (RouteStepStationary e.g. during pickup/drop-off cannot be interrupted)
        // the solution needs to consider this delay until the vehicle can move again
        long delayMillis = 0;
        if ((simVehicle.getCurrentRouteStep() != null) && (!simVehicle.getCurrentRouteStep().isInterruptible())) {
            delayMillis = simVehicle.getCurrentRouteStep().getEndTime().getTimeMillis() - SimTime.now().getTimeMillis();
        }

        return Builder.newInstance(String.format("Vehicle %s", simVehicle.getId()))
                .setStartLocation(vehicleLocation)
                .setType(vehicleType)
                .setReturnToDepot(false)
                .setEarliestStart(delayMillis)
                .build();
    }

    /**
     * Get a jSprit-Location Object from the original position-object. Each jSprit-Location requires an unique index
     * @param pos original Position-Object
     * @return jSprit-Location
     */
    private Location getLocationFromPosition(Position pos) {
        int index = locationIndex.getAndIncrement();

        try {
            return Location.Builder.newInstance()
                    .setIndex(index)
                    .setCoordinate(Coordinate.newInstance(pos.getX(), pos.getY()))
                    .build();
        } catch (Exception e) {
            Logger.error(e);
            Logger.error("Index {} for Position {}", index, pos);
            Logger.error("2nd return of location");
            return Location.Builder.newInstance()
                    .setIndex(index)
                    .setCoordinate(Coordinate.newInstance(pos.getX(), pos.getY()))
                    .build();
        }
    }

    /**
     * Creates a custom cost matrix with distances and durations between all stops. The router to calculate
     * the route information can be configured via the Config-file
     * @param allLocations All locations from the travel-requests
     * @return Custom cost matrix, which contains the durations between all requests/pickups/dropoffs
     */
    private VehicleRoutingTransportCosts buildCostMatrix(Collection<Location> allLocations) {

        // Create asymmetric distance matrix (false-parameter)
        VehicleRoutingTransportCostsMatrix.Builder builder = VehicleRoutingTransportCostsMatrix.Builder.newInstance(false);
        for (Location fromLocation : allLocations) {
            for (Location toLocation : allLocations) {
                if (!fromLocation.equals(toLocation)) {
                    Position from = new Position(fromLocation.getCoordinate().getX(), fromLocation.getCoordinate().getY());
                    Position to = new Position(toLocation.getCoordinate().getX(), toLocation.getCoordinate().getY());

                    // Initialize duration and distance to maximum values, for the case no route is found between origin and destination
                    long duration = Long.MAX_VALUE;
                    double distance = Double.MAX_VALUE;

                    // Calculate distance and duration
                    RoutingInterface router = Config.USE_GRID_ROUTER ? CityGridRouter.getInstance() : GraphHopperRouter.getInstance();
                    try {
                        var route = router.calculateRoute(from, to, SimObjectRoutable.Type.CAR, 0);
                        duration = route.getDurationMS();
                        distance = route.getDistanceM();
                    } catch (Exception e) {
                        Logger.error(e);
                        Logger.error("Error while trying to route from {},{} to {},{}", from.getX(), from.getY(), to.getX(), to.getY());
                    }
                    builder.addTransportTime(
                            fromLocation.getId(),
                            toLocation.getId(),
                            duration);
                    builder.addTransportDistance(
                            fromLocation.getId(),
                            toLocation.getId(),
                            distance);
                } else {
                    // Set distance and duration to 0, if origin and destination are equal
                    builder.addTransportTime(
                            fromLocation.getId(),
                            toLocation.getId(),
                            0);
                    builder.addTransportDistance(
                            fromLocation.getId(),
                            toLocation.getId(),
                            0);
                }
            }
        }
        return builder.build();
    }


    /**
     * Function to solve the VRP
     * @param vrp Prepared VRP-Problem
     * @return Returns null, if no solution is found, else returns the solution
     */
    public VehicleRoutingProblemSolution solveVRP(VehicleRoutingProblem vrp) {
        // Add state- and constraint manager, in order for JSprit to allow using the MaxTimeInVehicle constraints
        StateManager stateManager = new StateManager(vrp);
        StateId id = stateManager.createStateId("max-time");
        StateId openJobsId = stateManager.createStateId("open-jobs-id");
        stateManager.addStateUpdater(new UpdateMaxTimeInVehicle(stateManager, id, vrp.getTransportCosts(),
                vrp.getActivityCosts(), openJobsId));

        ConstraintManager constraintManager = new ConstraintManager(vrp, stateManager);
        constraintManager.addConstraint(new MaxTimeInVehicleConstraint(vrp.getTransportCosts(), vrp.getActivityCosts(),
                id, stateManager, vrp, openJobsId), ConstraintManager.Priority.CRITICAL);

        VehicleRoutingAlgorithm vra = Jsprit.Builder.newInstance(vrp)
                .setStateAndConstraintManager(stateManager, constraintManager).buildAlgorithm();

        // Plot Solution Scores
        if (Config.PRINT_JSPRIT_SOLUTION_INFO) {
            vra.getAlgorithmListeners().addListener(new AlgorithmSearchProgressChartListener("jsprit_output/sol_progress.png"));
        }
        vra.setMaxIterations(Config.JSPRIT_MAX_ITERATIONS);

        // Get the solution
        Collection<VehicleRoutingProblemSolution> solutions = vra.searchSolutions();
        VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

        // Check, if a solution was found
        var routes = bestSolution.getRoutes();
        if (routes.size() != 1 || bestSolution.getUnassignedJobs().size() != 0) {
            Logger.trace("jSprit: No solution found!");
            return null;
        }
        return bestSolution;
    }


    /**
     * Make a valid Route for a vehicle of a Jsprit-Solution. The validity of the solution is checked according
     * to the time limits for picking-up/dropping-off of all requests on the route.
     *
     * @param solution Calculated jSprit-Solution
     * @param vehicle The vehicle, this route should be assigned to
     * @param newRequest The new TravelRequest
     * @return Null, if no valid route is found, else the route is returned
     */
    public Route makeRouteFromSolution(VehicleRoutingProblemSolution solution, Vehicle vehicle, User.TripRequest newRequest) {
        try {
            var routes = solution.getRoutes();
            var activities = routes.iterator().next().getTourActivities().getActivities();
            Route route; // New route

            // Create a temporary map which holds a copy of all requests
            HashMap<Long, User.TripRequest> tempRequestMap = new HashMap<>();
            vehicle.getUserRequestMap().forEach((key, value) -> tempRequestMap.put(key, new User.TripRequest(value)));

            User.TripRequest tempUserRequest = new User.TripRequest(newRequest);
            tempUserRequest.setBookingPickupLatest(vehicle, true);
            tempRequestMap.put(tempUserRequest.getRequestID(), tempUserRequest);

            // Make new route. If current RouteStep is not interruptible (e.g. Pickup/Dropoff), let new route start
            // after current RouteStep finished
            if ((vehicle.getCurrentRouteStep() != null) && (!vehicle.getCurrentRouteStep().isInterruptible())) {
                route = new Route(vehicle.getCurrentRouteStep().getEndTime());
            } else {
                route = new Route(SimTime.now());
            }

            // Previous activity position is the current position of the vehicle
            Position prevActPosition = vehicle.getPosition().copyPosition();
            RouteStep.StepType prevStepType = RouteStep.StepType.ENROUTE;

            boolean timeStampsValid = true;  // Check flag, if route is still valid
            for (TourActivity act : activities) {
                Position actPosition = new Position(act.getLocation().getCoordinate().getX(), act.getLocation().getCoordinate().getY());

                // Append RouteStepEnroute for Route from previous activity to current activity
                // If distance is less than 50 m, no route is added, instead it is assumed that the two actions happen at the same point
                RouteStepEnroute rste = new RouteStepEnroute(prevActPosition, actPosition, RouteStep.StepType.ENROUTE);
                if (!(rste.getDistanceM() < 50 && !(prevStepType == RouteStep.StepType.ENROUTE))) {
                    route.appendRouteStep(rste, true);
                }
                // Set heading of stop depending on arrival heading of vehicle, replace with actual position on road
                // if (rste.getEndPosition().getHeading() != null) actPosition.setHeading(rste.getEndPosition().getHeading());
                actPosition = rste.getEndPosition().copyPosition();

                // Determine if the activity is a pickup or dropoff and get the according request-ID
                long requestID = 0;
                RouteStep.StepType stepType = null;
                ActivityType activityType = ActivityType.valueOf(act.getClass().getSimpleName());

                switch (activityType) {
                    case DeliverShipment:
                        requestID = Long.parseLong(((DeliverShipment) act).getJob().getId());
                        stepType = RouteStep.StepType.DROPOFF;
                        break;
                    case DeliverService:
                        requestID = Long.parseLong(((DeliverService) act).getJob().getId());
                        stepType = RouteStep.StepType.DROPOFF;
                        break;
                    case PickupShipment:
                        requestID = Long.parseLong(((PickupShipment) act).getJob().getId());
                        stepType = RouteStep.StepType.PICKUP;
                        break;
                }

                // Append RouteStepStationary to Route
                SimTime activityStartTime = new SimTime((long) act.getArrTime());
                RouteStepStationary rsts = new RouteStepStationary(
                        activityStartTime, (long) act.getOperationTime(),
                        stepType, actPosition, requestID);
                route.appendRouteStep(rsts, true);

                // Verify timestamps of last added RouteStepStationary
                User.TripRequest currentRequest = tempRequestMap.get(rsts.getRequestID());
                switch (rsts.getStepType()) {
                    case PICKUP:
                        if (rsts.getStartTime().isGreaterThan((tempRequestMap.get(rsts.getRequestID())).getTripPickupLatest())) {
                            timeStampsValid = false;
                        }
                        if (!currentRequest.wasPickedUp()) currentRequest.pickup(vehicle.getId(), rsts.getEndTime(), actPosition,true);
                        break;
                    case DROPOFF:
                        SimTime bookingDropoffLatest = (tempRequestMap.get(rsts.getRequestID())).getTripDropoffLatest();
                        if (rsts.getStartTime().isGreaterThan(bookingDropoffLatest)) {
                            timeStampsValid = false;
                        }
                        break;
                }
                if (!timeStampsValid) return null;

                prevActPosition = actPosition;
                prevStepType = stepType;
            }

            return route;
        } catch (IOException e) {
            Logger.error(e);
        }
        return null;
    }

    /**
     * Different Activity Types for creating a vehicle-route
     */
    enum ActivityType {
        DeliverService,
        PickupShipment,
        DeliverShipment
    }


    /**
     * Helper function to plot the VRP-Problem and the Solution
     * @param vrp VRP-Problem
     * @param bestSolution VRP-Solution
     */
    public void plotSolution(VehicleRoutingProblem vrp, VehicleRoutingProblemSolution bestSolution) {
        // Plot the problem
        Plotter plotter = new Plotter(vrp);
        plotter.plot("jsprit_output/vrp_problem.png", "VRP Problem");

        // Print solution results
        SolutionPrinter.print(vrp, bestSolution, SolutionPrinter.Print.CONCISE);
        SolutionPrinter.print(vrp, bestSolution, SolutionPrinter.Print.VERBOSE);

        // Plot the problem with solution
        Plotter solution_plotter = new Plotter(vrp, bestSolution).setLabel(Plotter.Label.ID);
        solution_plotter.plot("jsprit_output/vrp_problem_solution.png", "VRP Problem Solution");
    }

    private class LocationRequestCount {
        int passengerCount;
        int requestCount;

        LocationRequestCount(int passengerCount, int requestCount) {
            this.passengerCount = passengerCount;
            this.requestCount = requestCount;
        }

        int getPassengerCount() {
            return passengerCount;
        }

        void setPassengerCount(int passengerCount) {
            this.passengerCount = passengerCount;
        }

        void incRequestAndPassengerCount(int passengerCount) {
            this.requestCount += 1;
            this.passengerCount += passengerCount;
        }

        int getRequestCount() {
            return requestCount;
        }

        void setRequestCount(int requestCount) {
            this.requestCount = requestCount;
        }
    }
}
