package de.tum.ftm.agentsim.ts.assignmentStrategy.vehicleListProvider;

import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.simobjects.User;

/**
 * Helper class to build the VehicleListProvider as selected in the configuration-file
 * @author Manfred Klöppel
 */
public class VehicleListProviderBuilder {
    public static VehicleListProvider getVehicleListProvider(User.TripRequest newRequest) {
        switch (Config.VEHICLE_SEARCH_MODE) {
            case "ISOCHRONE":
                return new VehicleListProviderIsochrone(newRequest);
            default:
                throw new RuntimeException("Vehicle Search Mode not recognized!");
        }
    }
}
