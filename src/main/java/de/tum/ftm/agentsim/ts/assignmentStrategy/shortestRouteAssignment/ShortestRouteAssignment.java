package de.tum.ftm.agentsim.ts.assignmentStrategy.shortestRouteAssignment;


import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.Scenario;
import de.tum.ftm.agentsim.ts.log.DBLog;
import de.tum.ftm.agentsim.ts.log.DBTableEntry;
import de.tum.ftm.agentsim.ts.assignmentStrategy.AssignmentStrategyInterface;
import de.tum.ftm.agentsim.ts.assignmentStrategy.jspritSolver.SingleRequestJSpritSolver;
import de.tum.ftm.agentsim.ts.assignmentStrategy.vehicleListProvider.*;
import de.tum.ftm.agentsim.ts.routing.RoutingException;
import de.tum.ftm.agentsim.ts.routing.route.Route;
import de.tum.ftm.agentsim.ts.routing.route.RouteStep;
import de.tum.ftm.agentsim.ts.routing.route.RouteStepEnroute;
import de.tum.ftm.agentsim.ts.routing.route.RouteStepStationary;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectStatus;
import de.tum.ftm.agentsim.ts.simobjects.User;
import de.tum.ftm.agentsim.ts.simobjects.Vehicle;
import de.tum.ftm.agentsim.ts.utils.SimTime;
import org.pmw.tinylog.Logger;

import java.util.*;
import java.util.stream.Stream;

import static de.tum.ftm.agentsim.ts.simobjects.User.TripRequest.Status.FAILED;

/**
 * This assignment strategy processes each request individually. For each request all surrounding vehicles
 * are searched. The vehicles are ordered by ascending duration to pick the user up. This assignment strategy
 * trys to assign the request to all available vehicles and determines the incurred additional distance (VMT) for each
 * vehicle. It is tried to assign the request to the vehicle with the least additional VMT. If the request cannot be
 * assigned, the request is tried to be assigned to the vehicle with the next-lowest VMT.
 * An assignment can fail, if no suitable solution is found by the jSprit-solver or because no suitable route
 * could be established from a valid jSprit solution.
 * @author Manfred Klöppel
 */
public abstract class ShortestRouteAssignment implements AssignmentStrategyInterface {

    private Scenario scenario;
    private LinkedList<User.TripRequest> requestBuffer;
    private AssignmentStatistics statistics = new AssignmentStatistics();

    // Set to store all requests with the calculated routes, Ordered by the extra-VMT of the calculated routes
    private TreeSet<RequestRoutes> allRequestRoutes = new TreeSet<>();

    ShortestRouteAssignment(Scenario scenario) {
        this.scenario = scenario;
        requestBuffer = new LinkedList<>();
    }

    /**
     * Requests are added to a buffer, which will be processed at every "ProcessRequestBuffer"-Event
     *
     * @param userRequest is a request with a request time
     */
    @Override
    public void processNewRequest(User.TripRequest userRequest) {
        requestBuffer.add(userRequest);
        Logger.trace("Added RequestID {} to RequestBuffer", userRequest.getRequestID());
    }

    /**
     * Processes all requests in the request buffer if request buffer is not empty and logs failed travel-requests
     */
    public void processRequestBuffer() {
        Logger.trace("Processing RequestBuffer");
        int bufferSize = requestBuffer.size();
        if (requestBuffer.size() > 0) {
            // Update vehicle positions
            scenario.getSimObjectController().updateFleetPositions(true);

            // Set to store all requests with the calculated routes, Ordered by the extra-VMT of the calculated routes
            allRequestRoutes.clear();

            // Delegate decision whether to use parallel or sequential stream to sub-class, calculate possible vehicle routes
            var requestStream = createStream(requestBuffer);
            requestStream.forEach(request -> addVehicleRoutesToList(calculateVehicleRoutes(request)));

            // Assign the "best" route of each request to a vehicle. Request which cannot be assigned are returned
            ArrayList<User.TripRequest> assignmentResults = determineBestRouteToVehicleAssignment(allRequestRoutes);

            // Re-Add requests to the request buffer which have not exceeded the maximum waiting time, else log as failed request
            requestBuffer.clear();
            assignmentResults.forEach(request -> {
                        SimTime latestAssignment = new SimTime(request.getRequestStart(), Config.MAX_WAITING_TIME_SECONDS * 1000);
                        SimTime nextAssignment = new SimTime(SimTime.now(), Config.REQUEST_BUFFER_SECONDS * 1000);

                        if (latestAssignment.isLessOrEqualThan(nextAssignment) || !Config.REPEATED_ASSIGNMENT) {
                            logFailedBooking(request);
                        } else {
                            requestBuffer.add(request);
                        }
                    });
            allRequestRoutes.clear();
        }
    }

    synchronized private void addVehicleRoutesToList(RequestRoutes requestRoutes) {
        allRequestRoutes.add(requestRoutes);
    }

    /**
     * Assigns requests to vehicles by processing the ordered set which contains all requests with their calculated
     * routes. The requests are processed in ascending order of the extra VMT the assignment would incur. If a vehicle
     * is not available anymore, because another request was assigned to it, a new route with this vehicle
     * is calculated.
     * @param allRequestRoutes Ordered Set which contains all requests with all possible routes
     * @return List with all TravelRequests which could not be assigned
     */
    private ArrayList<User.TripRequest> determineBestRouteToVehicleAssignment(TreeSet<RequestRoutes> allRequestRoutes) {
        ArrayList<User.TripRequest> returnList = new ArrayList<>();
        Set<Vehicle> unavailableVehicles = new HashSet<>();
        while (!allRequestRoutes.isEmpty()) {
            RequestRoutes bestVMTRequest = allRequestRoutes.pollFirst(); // pollFirst() removes entry from set
            assert bestVMTRequest != null;

            VehicleRoutePair bestVehicleRoute = bestVMTRequest.getBestVehicleRoutePair();

            boolean requestIsAssigned = false;
            if (bestVehicleRoute != null) {
                Vehicle veh = bestVehicleRoute.getVehicle();
                if (!unavailableVehicles.contains(veh)) {
                    requestIsAssigned = assignRouteToVehicle(veh, bestVehicleRoute, bestVMTRequest.getTravelRequest());
                    if (requestIsAssigned) unavailableVehicles.add(veh);
                } else {
                    // recalculate the route for the vehicle where another request had been assigned to in the meantime
                    Logger.trace("Trying to make a new route for request {} on vehicle {}", bestVMTRequest.getTravelRequest().getRequestID(), veh.getId());
                    Route updatedRoute = makejSpritAssignmentRoute(veh, bestVMTRequest.getTravelRequest());

                    if (updatedRoute != null) {
                        // calculate new extraVMT
                        double originalRouteDistanceM = (bestVehicleRoute.getRoute().getRouteDistanceKM() - bestVehicleRoute.extraVMT);
                        double newVMT = updatedRoute.getRouteDistanceKM() - originalRouteDistanceM;

                        if (newVMT <= bestVMTRequest.getLowestVMT()) {
                            requestIsAssigned = assignRouteToVehicle(
                                    veh,
                                    new VehicleRoutePair(veh, updatedRoute, newVMT),
                                    bestVMTRequest.getTravelRequest()
                            );
                        } else {
                            bestVMTRequest.addVehicleRoutePair(new VehicleRoutePair(veh, updatedRoute, newVMT));
                        }
                    }
                }
                if (!requestIsAssigned) {
                    // error during assignment (e.g. vehicle is not available), re-add the request to the resultSet
                    allRequestRoutes.add(bestVMTRequest);
                }
            } else {
                // No routes were found for this travel request, therefore re-add it to the requestBuffer
                Logger.trace("No routes were calculated for request {}", bestVMTRequest.getTravelRequest().getRequestID());
                returnList.add(bestVMTRequest.getTravelRequest());
            }
        }
        return returnList;
    }

    private boolean assignRouteToVehicle(Vehicle veh, VehicleRoutePair bestVehicleRoute, User.TripRequest travelRequest) {
        try {
            veh.updateRoute(bestVehicleRoute.getRoute());
            veh.addRequestToVehicle(travelRequest);
            Logger.trace("Request {} assigned to vehicle {} with extra VMT {}", travelRequest.getRequestID(), veh.getId(), bestVehicleRoute.extraVMT);

            // Request was assigned
            return true;
        } catch (RoutingException e) {
            Logger.error("Could not assign route to Vehicle {}, Request {}", veh.getId(), travelRequest.getRequestID());
        }
        return false;
    }

    /**
     * Log a failed travel-request
     */
    private void logFailedBooking(User.TripRequest failedRequest) {
        failedRequest.travelRequestStatus = FAILED;
        failedRequest.getUser().setStatus(SimObjectStatus.USER_IDLE);
        DBLog.dbTableTrips.addLogEntry(new DBTableEntry.Builder()
                .bookingID(failedRequest.getRequestID())
                .personID(failedRequest.getUser().getId())
                .additionalPassengers(failedRequest.getRequestAdditionalPersons())
                .origStartTime(failedRequest.getRequestStart())
                .origStopTime(failedRequest.getRequestEnd())
                .origStartPosition(failedRequest.getOriginalRequestOrigin())
                .origStopPosition(failedRequest.getOriginalRequestDestination())
                .origDistanceKM(failedRequest.getRequestDistance())
                .origDurationMIN(failedRequest.getRequestDuration())
                .status(failedRequest.travelRequestStatus.toString())
                .bookingWasShared(false)
                .build());
        scenario.incFailedRequestsCnt();
    }

    /**
     * This method calculates all possible vehicle-assignments for a request.
     *
     * @param newRequest       New travel-request, which should be assigned
     * @return true, if the request was assigned, else returns false
     */
    private RequestRoutes calculateVehicleRoutes(User.TripRequest newRequest) {
        RequestRoutes requestRoutes = new RequestRoutes(newRequest);

        Logger.trace("Processing RequestID {}", newRequest.getRequestID());

        // Get list of available vehicles using a VehicleListProvider according to Config
        VehicleListProvider vehicleListProvider = VehicleListProviderBuilder.getVehicleListProvider(newRequest);
        ArrayList<Vehicle> vehicleList = vehicleListProvider.getVehicleList(false);
        Logger.trace("{} vehicles available after filter", vehicleList.size());

        // Calculate possible routes for selected vehicles
        vehicleList.forEach(veh -> {
            Logger.trace("Processing vehicle {}", veh.getId());

            // Store current length of vehicle route to compare it later
            double currentRouteLength = veh.getRemainingRouteDistanceKM();

            try {
                // If vehicle has no open requests, calculate route directly, else use jSprit for solving
                Route newRoute = makejSpritAssignmentRoute(veh, newRequest);

                // If a valid route was calculated, evaluate extra distance and store the result
                if (newRoute != null) {
                    double VMTDifference = newRoute.getRouteDistanceKM() - currentRouteLength;
                    requestRoutes.addVehicleRoutePair(new VehicleRoutePair(veh, newRoute, VMTDifference));
                }
            } catch (Exception e) {
                Logger.error(e);
                Logger.error("Assignment Error because of Jsprit/Routing");
                Logger.error("Vehicle {}, Request {}", veh.getId(), newRequest.getRequestID());
            }
        });

        // Request could not be assigned
        return requestRoutes;
    }

    /**
     * This method tries to assign a travel-request to a vehicle using the jSprit solver.
     * It the assignment succeeds, a valid route is returned, else null is returned
     *
     * @param veh Idle vehicle
     * @param newRequest New travel-request
     * @return valid route, if assignment was successful, otherwise null
     */
    private Route makejSpritAssignmentRoute(Vehicle veh, User.TripRequest newRequest) {
        SingleRequestJSpritSolver solver = new SingleRequestJSpritSolver();

        VehicleRoutingProblem vrp = solver.buildVRP(veh, newRequest);
        VehicleRoutingProblemSolution bestSolution = solver.solveVRP(vrp);
        statistics.incJspritTotalCalls(1);

        Route newRoute = null;
        // If a solution is found, try to create a valid route
        if (bestSolution != null) {
            newRoute = solver.makeRouteFromSolution(bestSolution, veh, newRequest);

            statistics.incJspritValidCalls(1);
            if (newRoute != null) statistics.incJspritValidRouteCalls(1);

            // Plot jSprit-solution, if configured
            if (Config.PRINT_JSPRIT_SOLUTION_INFO) {
                solver.plotSolution(vrp, bestSolution);
            }
        }
        return newRoute;
    }

    /**
     * In case of the attempt to assign a travel-request to an empty vehicle, a direct assignment is tried.
     * It the assignment succeeds, a valid route is returned, else null is returned
     *
     * @param vehicle Idle vehicle
     * @param request New travel-request
     * @return valid route, if assignment was successful, otherwise null
     */
    private Route makeDirectAssignmentRoute(Vehicle vehicle, User.TripRequest request) {
        statistics.incDirectAssignmentTotalCalls(1);

        try {
            // Create copy of the original request to avoid alteration of the original request in case this assignment fails
            User.TripRequest tempUserRequest = new User.TripRequest(request);
            tempUserRequest.setBookingPickupLatest(vehicle, true);

            // Make new route. If current RouteStep is not interruptible (e.g. Pickup/Dropoff),
            // let new route start after current RouteStep finished
            Route route;
            if ((vehicle.getCurrentRouteStep() != null) && (!vehicle.getCurrentRouteStep().isInterruptible())) {
                route = new Route(vehicle.getCurrentRouteStep().getEndTime());
            } else {
                route = new Route(SimTime.now());
            }

            // Calculate duration for pickup/dropoff in milliseconds
            long pickupDropoffDuration = (Config.VEHICLE_PICKUP_DROPOFF_DELAY_SECONDS +
                    Config.PICKUP_DROPOFF_DURATION_PER_PERSON_SECONDS * tempUserRequest.getTotalPersons()) * 1000;

            // Make RouteStepEnroute for the RouteStep to pickup the request
            route.appendRouteStep(new RouteStepEnroute(vehicle.getPosition(), tempUserRequest.getOriginalRequestOrigin(), RouteStep.StepType.ENROUTE), true);

            // Make sure that vehicle arrives at tempUserRequest before "latest pickup time"
            if (route.getRouteEndTime().isLessThan(tempUserRequest.getTripPickupLatest())) {
                // Make the Pickup-RouteStepStationary
                route.appendRouteStep(new RouteStepStationary(SimTime.now(), pickupDropoffDuration,
                        RouteStep.StepType.PICKUP, route.getRouteDestination(), tempUserRequest.getRequestID()), true);

                // Make the RouteStepEnroute to the drop-off point
                route.appendRouteStep(new RouteStepEnroute(route.getRouteDestination(), tempUserRequest.getOriginalRequestDestination(), RouteStep.StepType.ENROUTE), true);

                // Make the Drop-off-RouteStepStationary
                route.appendRouteStep(new RouteStepStationary(SimTime.now(), pickupDropoffDuration,
                        RouteStep.StepType.DROPOFF, route.getRouteDestination(), tempUserRequest.getRequestID()), true);

                // Return the valid route
                statistics.incDirectAssignmentValidRouteCalls(1);
                return route;
            } else {
                throw new RoutingException("Route violates pickup-time constraint");
            }
        } catch (RoutingException re) {
            return null;
        }
    }

    /**
     * Specifies on how to create a stream on a collection. Can specify a parallel or sequential stream.
     */
    public abstract <T> Stream<T> createStream(Collection<T> collection);

    private static class RequestRoutes implements Comparable<RequestRoutes> {
        private double lowestVMT = Double.MAX_VALUE;
        private User.TripRequest travelRequest;
        private TreeSet<VehicleRoutePair> vehicleRoutes = new TreeSet<>();

        RequestRoutes(User.TripRequest travelRequest) {
            this.travelRequest = travelRequest;
        }

        synchronized void addVehicleRoutePair(VehicleRoutePair vehicleRoutePair) {
            vehicleRoutes.add(vehicleRoutePair);

            // Update lowestVMT value
            lowestVMT = vehicleRoutes.first().extraVMT;
        }

        public double getLowestVMT() {
            return lowestVMT;
        }

        public User.TripRequest getTravelRequest() {
            return travelRequest;
        }

        synchronized public VehicleRoutePair getBestVehicleRoutePair() {
            VehicleRoutePair bestPair = vehicleRoutes.pollFirst();

            if (vehicleRoutes.size() == 0) lowestVMT = Double.MAX_VALUE;
            else lowestVMT = vehicleRoutes.first().extraVMT;

            return bestPair;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;

            return false;
        }

        @Override
        public int compareTo(RequestRoutes o) {
            if (lowestVMT != o.lowestVMT) {
                return Double.compare(this.lowestVMT, o.lowestVMT);
            } else if (equals(o)){
                return 0;
            } else {
                return 1;
            }
        }
    }

    private static class VehicleRoutePair implements Comparable<VehicleRoutePair> {
        private Vehicle vehicle;
        private Route route;
        private double extraVMT;

        VehicleRoutePair(Vehicle vehicle, Route route, double extraVMT) {
            this.vehicle = vehicle;
            this.route = route;
            this.extraVMT = extraVMT;
        }

        public Vehicle getVehicle() {
            return vehicle;
        }

        public Route getRoute() {
            return route;
        }

        public double getExtraVMT() {
            return extraVMT;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;

            return false;
        }

        @Override
        public int compareTo(VehicleRoutePair o) {
            if (extraVMT != o.extraVMT) {
                return Double.compare(extraVMT, o.extraVMT);
            } else if (equals(o)){
                return 0;
            } else {
                return 1;
            }
        }
    }

    /**
     * @return Map with assignment statistics
     */
    public Map<String, Long> getAssignmentStatistics() {
        HashMap<String, Long> counterMap = new LinkedHashMap<>();
        counterMap.put("Total JSprit Calls", statistics.getJspritTotalCalls().get());
        counterMap.put("JSprit Calls with valid Solution", statistics.getJspritValidCalls().get());
        counterMap.put("JSprit Calls with valid Route", statistics.getJspritValidRouteCalls().get());
        counterMap.put("Total Direct Assignment Calls", statistics.getDirectAssignmentTotalCalls().get());
        counterMap.put("Direct Assignment Calls with valid Route", statistics.getDirectAssignmentValidRouteCalls().get());
        counterMap.put("Successful Assignment on first try", (long) statistics.getSuccessfulAssignmentOnFirstTry().get());
        counterMap.put("Successful Assignment on second try", (long) statistics.getSuccessfulAssignmentOnSecondTry().get());
        counterMap.put("Total second try assignments", (long) statistics.getTotalSecondTryAssignments().get());

        return counterMap;
    }
}
