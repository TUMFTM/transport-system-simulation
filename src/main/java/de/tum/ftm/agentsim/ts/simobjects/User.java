package de.tum.ftm.agentsim.ts.simobjects;

import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.log.DBLog;
import de.tum.ftm.agentsim.ts.log.DBTableEntry;
import de.tum.ftm.agentsim.ts.routing.route.Route;
import de.tum.ftm.agentsim.ts.routing.route.RouteStep;
import de.tum.ftm.agentsim.ts.utils.Position;
import de.tum.ftm.agentsim.ts.utils.SimTime;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.PriorityQueue;

/**
 * Represents an User-Agent which extends SimObjectRoutable. One user can have multiple travel-requests.
 *
 * @author Manfred Klöppel
 */
public class User extends SimObjectRoutable {

    // Currently active travel-request
    private TripRequest currentRequest;

    // List of all travel-requests ordered by time
    private PriorityQueue<TripRequest> userTripRequestsList = new PriorityQueue<>();

    public User(long personID) {
        // Idle users only have a fictive position (0,0). This position is updated, when a request is active
        super(personID, new Position(0,0));
        this.status = SimObjectStatus.USER_IDLE;
    }

    /**
     * Resets a user to idle, e.g. after a travel-request is completed
     */
    public void resetUser() {
        status = SimObjectStatus.USER_IDLE;
        currentRequest = null;
        setPosition(new Position(0,0));
    }

    @Override
    public int hashCode() {
        return (int) id;
    }

    public void addRequest(TripRequest request) {
        userTripRequestsList.add(request);
    }

    public TripRequest getCurrentRequest() {
        return currentRequest;
    }

    public void setCurrentRequest(TripRequest currentRequest) {
        this.currentRequest = currentRequest;
    }

    public PriorityQueue<TripRequest> getUserTripRequestsList() {
        return userTripRequestsList;
    }

    /**
     * Inner class to represent a travel-request of a user. Each trip of a user is a single TravelRequest.
     * The TravelRequest is used to store the input-data for each original trip, as well as to store the
     * simulated results of a request.
     */
    public static class TripRequest implements Comparable<TripRequest> {
        private User user;
        public Status travelRequestStatus;
        private int partitionID = 9999;         // Partition the user is currently allocated to (Optimised Partition Assignment only)

        // Original request data loaded from input-database
        private long requestID;
        private SimTime requestStart;
        private SimTime requestEnd;
        private Position originalRequestOrigin;
        private Position originalRequestDestination;
        private int requestAdditionalPersons;
        private double requestDistance;         // kilometers
        private double requestDuration;         // minutes
        
        // Data generated during simulation (after a request is processed, it is called a booking)
        private SimTime tripAssigned;           // Time is set after request is assigned to vehicle
        private SimTime tripPickupLatest;       // Time, before which the user needs to be picked up. Time is set after user is assigned to vehicle
        private SimTime tripPickup;             // Time, at which the vehicle arrives at the position of the requesting user
        private SimTime tripDeparture;          // Time, at which the vehicle starts moving, after user was picked up.
        private SimTime tripDropoffLatest;      // Time, before which the user needs to be dropped off. Time is set after a user is picked up
        private SimTime tripDropoff;            // Time, when the vehicle arrives at the destination of the user
        private SimTime tripCompleted;          // Time, after the user completed alighting
        private double tripDistanceKM;          // Distance travelled during the booking
        private double tripDurationMinutes;     // Duration travelled during the booking
        private Route routeHistory;             // Route, which was travelled during the booking
        private Long vehicleID;                 // The vehicle, which the user travelled on
        private boolean tripWasShared = false;  // Indicator, if any part of the route was shared with another travel-request
        private Position tripOrigin;            // Position, where the request is picked up
        private Position tripDestination;       // Position, where the request is dropped off

        private int assignmentCounter = 0;           // Counter, how many times the request was assigned to a vehicle (Optimised Partition Assignment only)
        private int revokedAssignmentCounter = 0;    // Counter, how many times the assignment of the request to a vehicle was revoked (Optimised Partition Assignment only)

        // Block variable to prevent deletion of route history, before it was saved to the database
        boolean routeHistoryBlocked = true;

        // Default Constructor
        public TripRequest(User user, long requestID, SimTime requestStart, SimTime requestEnd, Position originalRequestOrigin,
                           Position originalRequestDestination, int requestAdditionalPersons,
                           double requestDistance) {
            // Duration of the original trip is calculated as time difference between the start- and end-time 
            // of the original trip. It is assumed that the original trip is a direct drive from the start- to the
            // end-point
            this.requestDuration = (double) Duration.between(requestStart.getTime(),
                    requestEnd.getTime()).toSeconds()/60 * Config.TRAVEL_TIME_FACTOR_CAR;
            this.user = user;
            this.requestID = requestID;
            this.requestStart = requestStart;
            this.requestEnd = new SimTime(requestStart, (long) (requestDuration*60*1000));
            this.originalRequestOrigin = originalRequestOrigin;
            this.originalRequestDestination = originalRequestDestination;
            this.requestAdditionalPersons = requestAdditionalPersons;
            this.requestDistance = requestDistance;
            this.travelRequestStatus = Status.OPEN;
        }

        // Copy Constructor (used during setup of jSprit-VRP-problem to create a temporary request without modifying the original request)
        public TripRequest(TripRequest other) {
            this.user = other.user;
            this.travelRequestStatus = other.travelRequestStatus;
            this.partitionID = other.partitionID;
            this.requestID = other.requestID;
            this.requestStart = other.requestStart;
            this.requestEnd = other.requestEnd;
            this.originalRequestOrigin = other.originalRequestOrigin;
            this.originalRequestDestination = other.originalRequestDestination;
            this.requestAdditionalPersons = other.requestAdditionalPersons;
            this.requestDistance = other.requestDistance;
            this.requestDuration = other.requestDuration;
            this.tripAssigned = other.tripAssigned;
            this.tripPickupLatest = other.tripPickupLatest;
            this.tripPickup = other.tripPickup;
            this.tripDeparture = other.tripDeparture;
            this.tripDropoffLatest = other.tripDropoffLatest;
            this.tripDropoff = other.tripDropoff;
            this.tripCompleted = other.tripCompleted;
            this.tripDistanceKM = other.tripDistanceKM;
            this.tripDurationMinutes = other.tripDurationMinutes;
            this.routeHistory = other.routeHistory;
            this.vehicleID = other.vehicleID;
            this.tripWasShared = other.tripWasShared;
            this.assignmentCounter = other.assignmentCounter;
            this.revokedAssignmentCounter = other.revokedAssignmentCounter;
            this.routeHistoryBlocked = other.routeHistoryBlocked;
            this.tripOrigin = other.tripOrigin;
            this.tripDestination = other.tripDestination;
        }


        /**
         * Definition of status for a travel-request. The travel request status is OPEN as long until it is completed
         * or failed.
         */
        public enum Status {
            OPEN,               // Travel-request not completed
            COMPLETED,          // Travel-request successfully completed
            FAILED,             // Travel-request failed (e.g. no vehicle found)
            FAILED_USER_BUSY    // Travel-request failed, because user was not IDLE (e.g. still on another trip)
        }


        /**
         * Sets the time, when the request/booking is assigned to a vehicle
         * @param tripAssigned Current simulation-time when request is assigned to the vehicle
         */
        private void setTripAssigned(SimTime tripAssigned) {
            this.tripAssigned = tripAssigned;
            incAssingmentCounter();
        }


        /**
         * Remove a request/booking from a vehicle, which had already been assigned. User travel-request still active
         */
        public void revokeBookingAssignment() {
            this.tripAssigned = null;
            this.tripPickupLatest = null;
            this.vehicleID = null;
            getUser().setStatus(SimObjectStatus.USER_REQUESTING_PICKUP);
            incUnassingmentCounter();
        }


        /**
         * Determines the latest time, before a user needs to be picked up. This is done after a user was assigned
         * to a vehicle.
         * The flag "simulationOnly" is used during the assignment process to try out possible assignments
         * without changing the status of the original request
         *
         * @param vehicle The vehicle the user is assigned to
         * @param simulationOnly Flag to denote that this call of the function is only for a simulated assignment during the execution of the algorithm
         */
        public void setBookingPickupLatest(Vehicle vehicle, boolean simulationOnly) {
            setTripAssigned(SimTime.now());
            this.vehicleID = vehicle.id;
            this.tripPickupLatest = new SimTime(this.requestStart, (long) Config.MAX_WAITING_TIME_SECONDS * 1000);

            // Only change the status of the user, if the request is really assigned to a vehicle
            if (!simulationOnly) this.getUser().setStatus(SimObjectStatus.USER_WAITING_FOR_PICKUP);
        }


        /**
         * Resembles the action of the user being picked up by the vehicle. Sets the time of pickup and the time
         * of the latest dropoff.
         * The flag "simulationOnly" is used during the process to try out possible assignments
         * without changing the status of the original request
         *
         * @param vehicleID Vehicle which picks up the user
         * @param departureTimestamp Pickup-time is END-time of pickup-routestep
         * @param simulationOnly Flag to denote that this call of the function is only for a simulated assignment during the execution of the assignment strategy
         */
        public void pickup(long vehicleID, SimTime departureTimestamp, Position currentPosition, boolean simulationOnly) {
            this.tripPickup = SimTime.now();
            this.tripDeparture = departureTimestamp;
            this.setTripDropoffLatest(departureTimestamp);

            this.tripOrigin = currentPosition.copyPosition();
            this.vehicleID = vehicleID;

            // Only change the status of the user, if the request is really assigned to a vehicle
            if (!simulationOnly) this.getUser().setStatus(SimObjectStatus.USER_IN_TRANSIT);
        }


        /**
         * Check if the pickup-process of a user is completed
         *
         * @return if pickup time is in the future (pickup time > simtime.now()) return false
         */
        public boolean isBoardingCompleted() {
            return getTripDeparture().getTimeMillis() - SimTime.now().getTimeMillis() < 0;
        }


        /**
         * Calculates the latest time, before a user needs to be dropped-off. Two different methods are implemented:
         * 1) During validation, the method according to Alonso-Mora is used, where a static maximum in-vehicle is set
         * 2) The maximum in-vehicle time is calculated according to the length of the original trip. Either an
         *    factor or a fixed value is used to determine the max in-vehicle time, depending which duration
         *    is greater
         *
         * @param departureTimeStamp Time, when the the vehicle departs
         */
        private void setTripDropoffLatest(SimTime departureTimeStamp) {
            if (Config.ENABLE_ALONSO_TRAVEL_DELAY_MODE) {
                this.tripDropoffLatest = new SimTime(this.requestStart, (long) ((Config.USER_ALONSO_MAX_DELAY_SECONDS + this.requestDuration * 60) * 1000));
            } else {
                SimTime elongationTime = new SimTime(departureTimeStamp, (long) (Config.MAX_IN_VEH_TIME_ELONGATION_FACTOR * this.requestDuration * 60 * 1000));
                SimTime acceptableTime = new SimTime(departureTimeStamp, (long) ((Config.ACCEPTABLE_TIME_IN_VEH_SECONDS + this.requestDuration * 60) * 1000));
                this.tripDropoffLatest = elongationTime.isGreaterThan(acceptableTime) ? elongationTime : acceptableTime;
            }
        }


        /**
         * This function resembles the action of dropping-off the user at his destination.
         * The duration and distance of the trip is calculated.
         *
         * Dropoff time is the time when the dropoff-routestep BEGINS!
         */
        public void dropoff(Position currentPosition) {
            this.setTripDropoff(SimTime.now());
            this.tripDestination = currentPosition.copyPosition();

            this.tripDurationMinutes = ((double) ChronoUnit.SECONDS.between(tripDeparture.getTime(), tripDropoff.getTime()))/60;
            this.tripDistanceKM = routeHistory.getRouteDistanceKM();
        }

        /**
         * The user finished alighting of the vehicle and status of the user is updated.
         * This marks also the successful completion of a request/booking and is stored to the database.
         *
         * Trip-Completed time is the time when the dropoff-routestep ENDS!
         */
        public void completeTrip() {
            this.tripCompleted = SimTime.now();
            this.setTravelRequestStatus(Status.COMPLETED);

            // Increment number of processed requests
            scenario.incProcessedRequestsCnt();

            // Log results to the database
            DBLog.dbTableTrips.addLogEntry(
                    new DBTableEntry.Builder()
                            .bookingID(requestID)
                            .personID(user.getId())
                            .additionalPassengers(requestAdditionalPersons)
                            .origStartTime(requestStart)
                            .origStopTime(requestEnd)
                            .origStartPosition(originalRequestOrigin)
                            .origStopPosition(originalRequestDestination)
                            .startPosition(tripOrigin)
                            .stopPosition(tripDestination)
                            .origDistanceKM(requestDistance)
                            .origDurationMIN(requestDuration)
                            .vehicleID(vehicleID)
                            .timeBookingAssigned(tripAssigned)
                            .timeBookingPickupLatest(tripPickupLatest)
                            .timeBookingPickedUp(tripPickup)
                            .timeBookingDeparture(tripDeparture)
                            .timeBookingDropoffLatest(tripDropoffLatest)
                            .timeBookingDroppedOff(tripDropoff)
                            .timeBookingCompleted(tripCompleted)
                            .drivingDistanceKM(tripDistanceKM)
                            .drivingDurationMIN(tripDurationMinutes)
                            .status(travelRequestStatus.toString())
                            .bookingWasShared(tripWasShared)
                            .geomWKT(getRouteHistory().createMultiLineStringWKTFromRoute().toString())
                            .build()
            );

            // RouteHistory is stored to DB, therefore the data can be purged now
            routeHistoryBlocked = false;

            // Reset user
            this.getUser().resetUser();
        }

        /**
         * @param tripDropoff Time when the vehicle drops-off the user
         */
        private void setTripDropoff(SimTime tripDropoff) {
            assert this.wasPickedUp(): "Request needs to be picked up before drop-off!";
            this.tripDropoff = tripDropoff;
        }

        // Getters & Setters
        public SimTime getRequestStart() {
                return requestStart;
        }
        public long getRequestID() {
            return requestID;
        }
        public User getUser() {
            return user;
        }
        public SimTime getRequestEnd() {
            return requestEnd;
        }
        public Position getOriginalRequestOrigin() {
            return originalRequestOrigin;
        }
        public Position getOriginalRequestDestination() {
            return originalRequestDestination;
        }
        public int getTotalPersons() { return 1 + requestAdditionalPersons; }
        public boolean wasPickedUp() { return tripDeparture != null; }
        public boolean wasDroppedOff() { return tripDropoff != null; }
        public boolean wasAssigned() { return tripAssigned != null; }
        public SimTime getTripDropoffLatest() { return tripDropoffLatest; }
        public SimTime getTripPickupLatest() { return tripPickupLatest; }
        public double getRequestDuration() {
            return requestDuration;
        }
        public int getRequestAdditionalPersons() {
            return requestAdditionalPersons;
        }
        public double getRequestDistance() {
            return requestDistance;
        }
        public Route getRouteHistory() {
            return routeHistory;
        }
        public void initRouteHistory(RouteStep routeStep) {
            this.routeHistory = new Route(routeStep);
        }
        public void setTravelRequestStatus(Status travelRequestStatus) {
            this.travelRequestStatus = travelRequestStatus;
        }
        public long getVehicleID() {
            return vehicleID;
        }
        public Status getTravelRequestStatus() {
            return travelRequestStatus;
        }
        public boolean isRouteHistoryBlocked() {
            return routeHistoryBlocked;
        }
        public int getAssignmentCounter() {
            return assignmentCounter;
        }
        public void incAssingmentCounter() {
            this.assignmentCounter += 1;
        }
        public int getRevokedAssignmentCounter() {
            return revokedAssignmentCounter;
        }
        public void incUnassingmentCounter() {
            this.revokedAssignmentCounter += 1;
        }
        public SimTime getTripDeparture() {
            return tripDeparture;
        }
        public void setTripIsShared() {
            this.tripWasShared = true;
        }
        public int getPartitionID() {
            return partitionID;
        }
        public void setPartitionID(int partitionID) {
            this.partitionID = partitionID;
        }

        @Override
        public int compareTo(TripRequest o) {
            return this.requestStart.compareTo(o.requestStart);
        }

        @Override
        public String toString() {
            return super.toString();
        }
    }
}
