package de.tum.ftm.agentsim.ts.events;

import de.tum.ftm.agentsim.ts.simobjects.rebalancing.RebalancingManagerInterface;
import de.tum.ftm.agentsim.ts.utils.SimTime;

/**
 * This event triggers the execution of the relocation algorithm.
 * During initialisation of the simulation this type of event is added in configured intervals to the event queue.
 * @author Manfred Klöppel
 */
public class Event_RebalancingManager extends Event {

    public static RebalancingManagerInterface mgr;

    public Event_RebalancingManager(SimTime scheduledTime) {
        super(scheduledTime);
    }

    @Override
    public void action() {
        // Check the current distribution of vehicles and relocate vehicle, if required
        mgr.checkRebalancing();
    }
}
