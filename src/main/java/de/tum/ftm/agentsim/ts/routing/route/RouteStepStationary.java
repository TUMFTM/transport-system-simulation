package de.tum.ftm.agentsim.ts.routing.route;

import de.tum.ftm.agentsim.ts.utils.Position;
import de.tum.ftm.agentsim.ts.utils.SimTime;

/**
 * This RouteStep describes a RouteStep during which an agent is stationary.
 *
 * @author Manfred Klöppel
 */
public class RouteStepStationary extends RouteStep {

    private Position position;
    private long requestID;

    /**
     * General constructor for a stationary RouteStep
     */
    public RouteStepStationary(SimTime startTime, long durationInMillis, StepType type, Position position) {
        super(startTime, durationInMillis, type);
        assert type != StepType.ENROUTE: "RouteStepStationary cannot be of type ENROUTE";
        assert type != StepType.PICKUP: "RouteStepStationary without Request-ID cannot be of type PICKUP/DROPOFF";
        assert type != StepType.DROPOFF: "RouteStepStationary without Request-ID cannot be of type PICKUP/DROPOFF";

        this.position = position;
    }


    /**
     * Constructor for a stationary RouteStep of type Drop-off/Pickup where the corresponding request-ID is required
     */
    public RouteStepStationary(SimTime startTime, long durationInMillis, StepType type, Position position, long requestID) {
        super(startTime, durationInMillis, type);
        assert (type == StepType.PICKUP || type == StepType.DROPOFF): "RouteStepStationary with requestID must be PICKUP or DROPOFF";
        this.position = position;
        this.requestID = requestID;
    }

    public long getRequestID() {
        return requestID;
    }

    @Override
    public Position getStartPosition() {
        return position;
    }

    @Override
    public Position getEndPosition() {
        return position;
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public Position getPositionAtTime(long timeMillis) {
        return position;
    }

    @Override
    public double getRemainingDistanceKM(long timeMillis) {
        return 0;
    }

    @Override
    public void setDurationMS(long newDuration) {
        this.durationMS = newDuration;
        this.endTime.setTime(startTime.getTimeMillis() + durationMS);
    }

    /**
     * Updates starttime and stoptime according to new starttime
     * @param newStartTime
     */
    @Override
    public void updateStartTime(SimTime newStartTime) {
        this.startTime = newStartTime;
        this.endTime.setTime(newStartTime.getTimeMillis() + durationMS);
    }

    /**
     * @return WKT-representation of a Point-Geometry
     */
    @Override
    public StringBuilder getWKTFromRouteStep() {
        StringBuilder wkt = new StringBuilder("POINT(");
        wkt.append(String.format("%s %s)", position.getLon(), position.getLat()));
        return wkt;
    }
}
