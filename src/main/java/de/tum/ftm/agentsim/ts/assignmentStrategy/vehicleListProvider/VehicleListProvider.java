package de.tum.ftm.agentsim.ts.assignmentStrategy.vehicleListProvider;

import de.tum.ftm.agentsim.ts.simobjects.Vehicle;
import java.util.ArrayList;

/**
 * Interface to define common methods for classes which are used to determine the available vehicles for a
 * request
 *
 * @author Manfred Klöppel
 */
public interface VehicleListProvider {

    /**
     * Method to return a list of vehicles to which the travel-request could be assigned
     *
     * @param onlyIdleVehicles Only return vehicles which are idle
     * @return A list of vehicles
     */
    ArrayList<Vehicle> getVehicleList(boolean onlyIdleVehicles);
}
