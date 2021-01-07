package de.tum.ftm.agentsim.ts.events;

import de.tum.ftm.agentsim.ts.log.DBLog;
import de.tum.ftm.agentsim.ts.log.DBTableEntry;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectStatus;
import de.tum.ftm.agentsim.ts.simobjects.User;
import de.tum.ftm.agentsim.ts.utils.SimTime;
import org.pmw.tinylog.Logger;

/**
 * This event is scheduled whenever a user makes a travel-request.
 * @author Manfred Klöppel
 */
public class Event_UserRequest extends Event {

    private User.TripRequest userRequest;
    private static int counter = 0;

    public Event_UserRequest(SimTime scheduledTime, User.TripRequest userRequest) {
        super(scheduledTime);
        this.userRequest = userRequest;
    }

    /**
     * A user makes a new travel-request
     */
    @Override
    public void action() {
        Logger.trace("Request-ID: {}", userRequest.getRequestID());

        // Check, that the User is IDLE
        if (userRequest.getUser().getStatus().equals(SimObjectStatus.USER_IDLE)) {

            // Update the status of the user and add request to the assignment-algorithm
            userRequest.getUser().setStatus(SimObjectStatus.USER_REQUESTING_PICKUP);
            userRequest.getUser().setPosition(userRequest.getOriginalRequestOrigin());
            userRequest.getUser().setCurrentRequest(userRequest);
            scenario.assignmentStrategy.processNewRequest(userRequest);
            counter += 1;
            Logger.trace("No. of processed requests: {}", counter);
        } else {
            // If the user is not idle, a failed travel-request is logged
            Logger.error("UserID {} is not idle, cannot start new request!", userRequest.getUser().getId());
            userRequest.travelRequestStatus = User.TripRequest.Status.FAILED_USER_BUSY;

            // Log Failed booking
            DBLog.dbTableTrips.addLogEntry(new DBTableEntry.Builder()
                    .bookingID(userRequest.getRequestID())
                    .personID(userRequest.getUser().getId())
                    .additionalPassengers(userRequest.getRequestAdditionalPersons())
                    .origStartTime(userRequest.getRequestStart())
                    .origStopTime(userRequest.getRequestEnd())
                    .origStartPosition(userRequest.getOriginalRequestOrigin())
                    .origStopPosition(userRequest.getOriginalRequestDestination())
                    .origDistanceKM(userRequest.getRequestDistance())
                    .origDurationMIN(userRequest.getRequestDuration())
                    .status(userRequest.travelRequestStatus.toString())
                    .build());
            scenario.incFailedRequestsCnt();
        }
    }

    @Override
    public String toString() {
        return String.format("%s UserID: %s", this.scheduledTime.toString(), this.userRequest.getUser().getId());
    }
}
