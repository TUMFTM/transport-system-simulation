package de.tum.ftm.agentsim.ts.assignmentStrategy.closestVehicleAssignment;

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
import de.tum.ftm.agentsim.ts.simobjects.User.TripRequest;
import de.tum.ftm.agentsim.ts.simobjects.Vehicle;
import de.tum.ftm.agentsim.ts.utils.SimTime;
import org.pmw.tinylog.Logger;

import java.util.*;
import java.util.stream.Stream;

import static de.tum.ftm.agentsim.ts.simobjects.User.TripRequest.Status.FAILED;

/**
 * This assignment strategy processes each request individually. For each request all surrounding vehicles
 * are searched. The vehicles are ordered by ascending duration to pick the user up. Beginning with the closest
 * vehicle (shortest arrival time) it is tried to assign the request to the vehicle. If the request cannot be
 * assigned, the request is tried to be assigned to the next closest vehicle.
 * An assignment can fail, if no suitable solution is found by the jSprit-solver or because no suitable route
 * could be established from a valid jSprit solution.
 *
 * @author Manfred Klöppel
 */
public abstract class ClosestVehicleAssignment implements AssignmentStrategyInterface {

    private Scenario scenario;
    private LinkedList<TripRequest> requestBuffer;
    private AssignmentStatistics statistics = new AssignmentStatistics();

    // Map to store the result of the assignment-attempt for each request
    private Map<TripRequest, Boolean> assignmentResults = new HashMap<>();

    ClosestVehicleAssignment(Scenario scenario) {
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
     * Processes all requests in the request buffer if request buffer is not empty and logs failed travel-requests.
     * It can be configured to try a second-assignment, where only idle vehicles are taken into consideration
     */
    public void processRequestBuffer() {
        Logger.trace("Processing RequestBuffer");
        int bufferSize = requestBuffer.size();
        if (requestBuffer.size() > 0) {
            // Update vehicle positions
            scenario.getSimObjectController().updateFleetPositions(true);

            // Delegate decision whether to use parallel or sequential stream to sub-class
            var requestStream = createStream(requestBuffer);

            // Prepare map to store the result of the assignment-attempt for each request
            assignmentResults.clear();

            // Try to assign requests to vehicles
            requestStream.forEach(request -> storeAssignmentResult(request, matchRequestToVehicle(request, false)));
            statistics.incSuccessfulAssignmentOnFirstTry(bufferSize - requestBuffer.size());

            // Re-Add requests to the request buffer which have not exceeded the maximum waiting time, else log as failed request
            requestBuffer.clear();
            assignmentResults.entrySet().stream()
                    .filter(assignmentResult -> !assignmentResult.getValue())  // only keep failed requests
                    .forEach(entry -> {
                        TripRequest request = entry.getKey();
                        SimTime latestAssignment = new SimTime(request.getRequestStart(), Config.MAX_WAITING_TIME_SECONDS * 1000);
                        SimTime nextAssignment = new SimTime(SimTime.now(), Config.REQUEST_BUFFER_SECONDS * 1000);

                        if (latestAssignment.isLessOrEqualThan(nextAssignment) || !Config.REPEATED_ASSIGNMENT) {
                            logFailedBooking(request);
                        } else {
                            requestBuffer.add(request);
                        }
                    });
        }
    }

    synchronized private void storeAssignmentResult(User.TripRequest request, boolean assignmentResult) {
        Logger.trace("Request {} was assigned: {}", request.getRequestID(), assignmentResult);
        assignmentResults.put(request, assignmentResult);
    }

    /**
     * This method does the actual assignment-attempts of request to the vehicles.
     * A flag of only-idle-vehicles can be set, to try to increase the likelihood of assignment in some scenarios,
     * due to look for idle vehicles only
     *
     * @param newRequest       New travel-request, which should be assigned
     * @param onlyIdleVehicles Flag, if the request should only be assigned to idle vehicles
     * @return true, if the request was assigned, else returns false
     */
    private boolean matchRequestToVehicle(TripRequest newRequest, boolean onlyIdleVehicles) {

        Logger.trace("Processing RequestID {}", newRequest.getRequestID());

        // Get list of available vehicles using a VehicleListProvider according to Config
        VehicleListProvider vehicleListProvider = VehicleListProviderBuilder.getVehicleListProvider(newRequest);
        ArrayList<Vehicle> vehicleList = vehicleListProvider.getVehicleList(onlyIdleVehicles);
        Logger.trace("{} vehicles available after filter", vehicleList.size());

        // Check-variable, if request was successfully assigned to a vehicle
        boolean requestIsAssigned = false;

        Iterator itr = vehicleList.iterator();
        while (!requestIsAssigned && itr.hasNext()) {
            Vehicle veh = (Vehicle) itr.next();

            // Synchronize vehicle to avoid simultaneous assignment to the same vehicle by different threads
            synchronized (veh) {
                Logger.trace("Processing vehicle {}", veh.getId());

                try {
                    // If vehicle has no open requests, calculate route directly, else use jSprit for solving
                    Route newRoute = makejSpritAssignmentRoute(veh, newRequest);

                    // If a valid route was calculated, add request and route to vehicle
                    if (newRoute != null) {
                        veh.updateRoute(newRoute);
                        veh.addRequestToVehicle(newRequest);
                        requestIsAssigned = true;
                        Logger.trace("Request {} is assigned to Vehicle {}", newRequest.getRequestID(), veh.getId());
                    }
                } catch (Exception e) {
                    Logger.error(e);
                    Logger.error("Assignment Error because of Jsprit/Routing");
                    Logger.error("Vehicle {}, Request {}", veh.getId(), newRequest.getRequestID());
                }
            }
        }

        return requestIsAssigned;
    }

    /**
     * This method tries to assign a travel-request to a vehicle using the jSprit solver.
     * It the assignment succeeds, a valid route is returned, else null is returned
     *
     * @param veh        Idle vehicle
     * @param newRequest New travel-request
     * @return valid route, if assignment was successful, otherwise null
     */
    private Route makejSpritAssignmentRoute(Vehicle veh, TripRequest newRequest) {
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
            TripRequest tempUserRequest = new TripRequest(request);
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
     * Log a failed travel-request
     */
    private void logFailedBooking(TripRequest failedRequest) {
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
     * Specifies on how to create a stream on a collection. Can specify a parallel or sequential stream.
     */
    public abstract <T> Stream<T> createStream(Collection<T> collection);

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
