package de.tum.ftm.agentsim.ts.events;

import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.utils.SimTime;

/**
 * This event triggers the execution of the assignment algorithm.
 * The event is executed in configured intervals. Each time this event is executed, a new event is inserted
 * into the event queue, until no more User or Vehicle events are in the event queue.
 * @author Manfred Klöppel
 */
public class Event_ProcessRequestBuffer extends Event {

    public Event_ProcessRequestBuffer(SimTime scheduledTime) {
        super(scheduledTime);
    }

    @Override
    public void action() {
        // Process all travel-requests in the buffer
        scenario.assignmentStrategy.processRequestBuffer();

        // Insert the next logging event to the event-queue, as long as there are still User and Vehicle events
        if (scenario.areUserOrVehicleEventsInQueue()) {
            scenario.addEvent(new Event_ProcessRequestBuffer(new SimTime(SimTime.now().getTimeMillis() + Config.REQUEST_BUFFER_SECONDS *1000)));
        }
    }
}
