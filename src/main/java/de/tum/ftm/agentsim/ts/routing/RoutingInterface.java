package de.tum.ftm.agentsim.ts.routing;

import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.routing.route.RouteStepEnroute;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectRoutable;
import de.tum.ftm.agentsim.ts.utils.Position;

/**
 * This interface defines general methods for routing information which need to be implemented.
 * TravelTimeFactors are used to adapt routing-duration results and are centrally located here.
 *
 * @author Manfred Klöppel
 */
public interface RoutingInterface {

    /**
     * Each routing implementation should count the number of calls for statistical analysis
     * @return Count of routing-calls
     */
    long getRoutingCallCounter();

    /**
     * Get routing information between two locations
     *
     * @param from  Start-Position
     * @param to    End-Position
     * @param type  Mode of Travel
     * @param time  Time offset in milliseconds to adjust timing at routing-positions
     * @return      Returns a Track containing the distance, duration, and the positions of travel
     * @throws RoutingException Throws an exception if no route was found
     */
    RouteStepEnroute.EnrouteTrack calculateRoute(Position from, Position to, SimObjectRoutable.Type type, long time) throws RoutingException;

    /**
     * Returns the travel time factor for a given travel mode
     *
     * @param type Travel Mode (e.g. car, foot etc.)
     * @return Travel time factor as Double value
     */
    default double getTravelTimeFactor(SimObjectRoutable.Type type) {
        double travelTimeFactor = 0;
        switch (type) {
            case FOOT:
                travelTimeFactor = Config.TRAVEL_TIME_FACTOR_FOOT;
                break;
            case CAR:
                travelTimeFactor = Config.TRAVEL_TIME_FACTOR_CAR;
                break;
            case VOID:
                travelTimeFactor = 1;
                break;
        }
        return travelTimeFactor;
    }
}
