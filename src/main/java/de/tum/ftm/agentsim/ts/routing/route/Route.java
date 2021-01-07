package de.tum.ftm.agentsim.ts.routing.route;

import de.tum.ftm.agentsim.ts.utils.Position;
import de.tum.ftm.agentsim.ts.utils.SimTime;

import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class represents a route on which a SimObjectRoutable can travel along.
 * Each route consists of RouteSteps, which can be either stationary or enroute.
 *
 * @author Manfred Klöppel
 */
public class Route {

    // Counter for overall amount of routes
    private static AtomicInteger routeCount = new AtomicInteger(0);

    // List of RouteSteps in this route, ordered
    private TreeSet<RouteStep> routeSteps = new TreeSet<>();

    private int routeID;
    private Position routeOrigin;
	private Position routeDestination;
	private SimTime routeStartTime;
	private SimTime routeEndTime;
	private long routeDurationMS;         // Millis
    private double routeDistanceM;       // Meters

    /**
     * Create empty route with a start time
     * @param routeStartTime start time of the route
     */
	public Route(SimTime routeStartTime) {
        routeID = makeRouteID();
        this.routeStartTime = routeStartTime;
        this.routeEndTime = routeStartTime;
    }

    /**
     * Create route using an initial RouteStep
     * @param routeStep Initial Routestep
     */
	public Route(RouteStep routeStep) {
		routeID = makeRouteID();
		routeSteps.add(routeStep);
        this.routeStartTime = routeStep.getStartTime();
        this.routeEndTime = routeStep.getEndTime();
        this.routeDurationMS = routeStep.durationMS;
        this.routeDistanceM = routeStep.getDistanceM();
	}


    /**
     * Append a new routeStep to the route. If @param updateRouteStepTime is true, the timestamps of the new routeStep
     * will be modified, so that the new routeStep is added to the end of the route.
     *
     * @param routeStep           New routeStep which should be added to the route
     * @param updateRouteStepTime If true, timestamps of routeStep will be adjusted
     */
    public void appendRouteStep(RouteStep routeStep, Boolean updateRouteStepTime) {

        if (updateRouteStepTime) {
            // Update timestamp of new routeStep
            routeStep.updateStartTime(getRouteEndTime());
        }
        routeSteps.add(routeStep);

        // Update the start- and end-time of the whole route, and update the durationMS
        setRouteStartTime(routeSteps.first().getStartTime());
        setRouteEndTime(routeSteps.last().getEndTime());

        // Update the start-/and end-point of the route
        this.routeOrigin = routeSteps.first().getStartPosition();
        this.routeDestination = routeSteps.last().getEndPosition();

        // Update total route distanceM
        this.routeDistanceM += routeStep.getDistanceM();
        this.routeDurationMS += routeStep.getDurationMS();
    }


    /**
     * Creates multilinestring WKT which contains linestrings of all enroute segments of the route
     * @return String in WKT-format
     */
    public StringBuilder createMultiLineStringWKTFromRoute() {
        int enrtStepCounter = 0;

        StringBuilder wkt = new StringBuilder("MULTILINESTRING(");
        for (RouteStep rs : routeSteps) {
            if (rs instanceof RouteStepEnroute) {
                StringBuilder linestringWKT = rs.getWKTFromRouteStep().delete(0, 10);

                // Make sure, that linestring has at least two points (points are separated by ",")
                if (linestringWKT.toString().contains(",")) {
                    wkt.append(linestringWKT);
                    wkt.append(", ");
                    enrtStepCounter++;
                }
            }
        }
        wkt.delete(wkt.length()-2, wkt.length());
        wkt.append(")");

        return enrtStepCounter > 0 ? wkt : new StringBuilder("");
    }


	/**
	 * Create a route id and increment the static counter.
	 * @return A unique route id.
	 */
	private static int makeRouteID() {
		return routeCount.incrementAndGet();
	}

    /**
     * Calculates the total route distance as sum of all RouteStepEnroutes
     * @return Total route distance in kilometers
     */
    public double calculateRouteDistanceKM() {
        double distanceSum = 0;
        for (RouteStep rteStep : routeSteps) {
            if (rteStep instanceof RouteStepEnroute) {
                distanceSum += rteStep.distanceM;
            }
        }
        return distanceSum/1000;
    }

    /**
     * Checks if a given timestamp is within the start- and end-time of the route
     * @param futureTime Timestamp
     * @return true, if the timestamp is within the route-times, else returns false
     */
    public boolean isTimeWithinRoute(SimTime futureTime) {
        return (futureTime.isGreaterOrEqualThan(routeStartTime) && (futureTime.isLessOrEqualThan(routeEndTime)));
    }

    /**
     * Returns the position on the route at a given time
     * @param time Timestamp
     * @return Position at the given timestamp
     * @throws RuntimeException If the position cannot be determined
     */
    public Position getPositionAtTime(SimTime time) throws RuntimeException {
        assert isTimeWithinRoute(time) : "Time not within Route!!";

        try {
            RouteStep currentRouteStep = this.routeSteps.floor(new RouteStepVoid(time));
            return currentRouteStep.getPositionAtTime(time.getTimeMillis());
        } catch (Exception e) {
            throw new RuntimeException("Position on Route can not be determined!");
        }
    }

    /**
     * Removes a specific RouteStep from the route
     * @param routeStep RouteStep which should be removed
     */
    public void removeRouteStep(RouteStep routeStep) {
        routeSteps.remove(routeStep);

        // Update total route distanceM / durationMS
        this.routeDistanceM -= routeStep.getDistanceM();
        this.routeDurationMS -= routeStep.getDurationMS();
    }

	// --- GETTER & SETTERS
    public SimTime getRouteEndTime() {
        return routeEndTime;
    }
    public void setRouteEndTime(SimTime routeEndTime) {
        this.routeEndTime = routeEndTime;
    }
    public void setRouteStartTime(SimTime routeStartTime) {
        this.routeStartTime = routeStartTime;
    }
    public TreeSet<RouteStep> getRouteSteps() {
        return routeSteps == null ? null : routeSteps;
    }
    public double getRouteDurationMIN() {
        return ((double) routeDurationMS)/1000/60;
    }
    public double getRouteDistanceKM() {
        return routeDistanceM /1000;
    }
    public int getRouteID() {
        return routeID;
    }
    public Position getRouteOrigin() {
        return routeOrigin;
    }
    public Position getRouteDestination() {
        return routeDestination;
    }
}
