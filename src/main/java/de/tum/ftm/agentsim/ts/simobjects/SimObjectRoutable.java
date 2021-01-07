package de.tum.ftm.agentsim.ts.simobjects;

import de.tum.ftm.agentsim.ts.routing.route.Route;
import de.tum.ftm.agentsim.ts.routing.route.RouteStep;
import de.tum.ftm.agentsim.ts.utils.Position;
import de.tum.ftm.agentsim.ts.utils.SimTime;

/**
 * Object/Agent which extends SimObject and provides functions for calculating and storing routes
 * and determining the position on a route
 *
 * @author Manfred Klöppel
 */
public abstract class SimObjectRoutable extends SimObject {

    protected Route route;                  // Current route, the object is following
    protected RouteStep currentRouteStep;   // Current routeStep, the object is travelling on
    Route routeHistory;                     // Historic routeSteps

    //public static RoutingInterface router;  // Routing instance used for routing operations

    public SimObjectRoutable(long id, Position position) {
        super(id, position);
    }

    /**
     * Updates current position of object in according to the current simulation-time, if a current routeStep is present.
     */
    public void updatePosition() {
        if (currentRouteStep != null) {
            this.position = currentRouteStep.getPositionAtTime(SimTime.now().getTimeMillis());
        }
    }

    /**
     * Calculates the remaining duration on route in dependence of the current simulation-time.
     *
     * @return  Remaining duration on route in minutes
     */
    public double getRemainingRouteDurationMIN() {
        return (double)(this.getRoute().getRouteEndTime().getTimeMillis()-SimTime.now().getTimeMillis())/1000/60;
    }

    /**
     * Calculates the remaining distance on the route in dependence of the current simulation-time.
     *
     * @return Remaining distance on route in kilometers
     */
    public double getRemainingRouteDistanceKM() {
        if (currentRouteStep != null && route != null) {
            return route.calculateRouteDistanceKM() + currentRouteStep.getRemainingDistanceKM(SimTime.now().getTimeMillis());
        } else {
            return 0;
        }
    }

    public RouteStep getCurrentRouteStep() {
        return currentRouteStep;
    }

    public void setCurrentRouteStep(RouteStep currentRouteStep) {
        this.currentRouteStep = currentRouteStep;
    }

    public Route getRouteHistory() {
        return routeHistory;
    }

    public Route getRoute() {
        return route;
    }

    /**
     * Definition of the type of the simObjectRoutable, which is used to determine the speed used when calculating
     * routing durations and directions.
     */
    public enum Type {
        FOOT, CAR, VOID
    }
}
