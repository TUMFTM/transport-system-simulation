package de.tum.ftm.agentsim.ts.events;


import de.tum.ftm.agentsim.ts.Scenario;
import de.tum.ftm.agentsim.ts.log.DBLog;
import de.tum.ftm.agentsim.ts.utils.SimTime;
import org.pmw.tinylog.Logger;

/**
 * Basic Event class used to control the simulation flow.
 * Events require a SimTime-timestamp
 *
 * @author Michael Wittmann, Manfred Klöppel
 */
public abstract class Event implements Comparable<Event> {

    public static Scenario scenario;
    public static DBLog dbLog;

    // The time of the event
    SimTime scheduledTime;

    /**
     * @param scheduledTime time the event is scheduled
     */
    public Event(SimTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    /**
     * This action will be executed when the event is processed.
     * Override this method in subclasses for specific action.
     */
    public void action() {
        Logger.info("No action defined for event!");
    }

    public SimTime getScheduledTime() {
        return scheduledTime;
    }
    public void setScheduledTime(SimTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    /**
     * Events are compared according to their timestamp
     */
    @Override
    public int compareTo(Event o) {
        return this.scheduledTime.compareTo(o.getScheduledTime());
    }

    @Override
    public String toString() {
        return scheduledTime.toString();
    }
}
