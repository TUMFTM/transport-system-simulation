package de.tum.ftm.agentsim.ts.events;

import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.log.DBLog;
import de.tum.ftm.agentsim.ts.log.DBTableEntry;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectController;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectStatus;
import de.tum.ftm.agentsim.ts.simobjects.User;
import de.tum.ftm.agentsim.ts.simobjects.Vehicle;
import de.tum.ftm.agentsim.ts.utils.SimTime;

/**
 * This event triggers the logging of the object status of the Vehicle and User Agents during the simulation.
 * The event is executed in configured intervals. Each time this event is executed, a new event is inserted
 * into the event queue, until no more User or Vehicle events are in the event queue.
 * @author Manfred Klöppel
 */
public class Event_LogSimObjectStatus extends Event {

    public static SimObjectController simObjectController;

    public Event_LogSimObjectStatus(SimTime scheduledTime) {
        super(scheduledTime);
    }

    @Override
    public void action() {

        // Log all vehicles
        logVehicles();

        // Log all Users
        logUsers();

        // Set next event to update all SimObjectStatus
        setNextUpdate();
    }

    /**
     * Insert the next logging event to the event-queue, as long as there are still User and Vehicle events
     */
    private void setNextUpdate() {
        if (scenario.areUserOrVehicleEventsInQueue()) {
            scenario.addEvent(new Event_LogSimObjectStatus(new SimTime(SimTime.now().getTimeMillis() + Config.SIMOBJECT_MAP_UPDATE_FREQUENCY_SECONDS*1000)));
        }
    }

    /**
     * Create log-entries for each vehicle
     */
    private void logVehicles() {
        for (Vehicle v : simObjectController.getFleet().values()) {
            DBLog.dbTableSimObjectStatus.addLogEntry(new DBTableEntry.Builder()
                    .objectID(v.getId())
                    .objectType(v.getClass().getName())
                    .currentTime(SimTime.now())
                    .status(v.getStatus().toString())
                    .currentPosition(v.getPosition())
                    .currentRequestsCount(v.getUserRequestMap().size())
                    .currentPaxCount(v.getPassengers())
                    .build());
        }
    }

    /**
     * Create log-entries for each user
     */
    private void logUsers() {
        for (User u : simObjectController.getUsers().values()) {
            if (u.getStatus() != SimObjectStatus.USER_IDLE) {
                DBLog.dbTableSimObjectStatus.addLogEntry(new DBTableEntry.Builder()
                        .objectID(u.getId())
                        .objectType(u.getClass().getName())
                        .currentTime(SimTime.now())
                        .status(u.getStatus().toString())
                        .currentPosition(u.getPosition())
                        .build());
            }
        }
    }
}
