package de.tum.ftm.agentsim.ts.events;

import de.tum.ftm.agentsim.ts.simobjects.Vehicle;
import de.tum.ftm.agentsim.ts.utils.SimTime;

/**
 * This event is required for the control of vehicle-actions
 * @author Manfred Klöppel
 */
public class Event_VehicleNextActivity extends Event {

    private Vehicle vehicle;

    public Event_VehicleNextActivity(SimTime scheduledTime, Vehicle vehicle) {
        super(scheduledTime);
        this.vehicle = vehicle;
    }

    /**
     * Execute the current vehicle-action
     */
    @Override
    public void action() {
        vehicle.nextActivity();
    }
}
