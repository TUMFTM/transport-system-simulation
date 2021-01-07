package de.tum.ftm.agentsim.ts.simobjects.rebalancing;

import de.tum.ftm.agentsim.ts.simobjects.Vehicle;

public interface RebalancingManagerInterface {

    void setupRebalancingEvents();

    void checkRebalancing();

    void relocateSingleVehicle(Vehicle vehicle);

    String getRelocationManagerType();

    boolean upcomingRelocationEvents();

}
