package de.tum.ftm.agentsim.ts.routing.route;

import de.tum.ftm.agentsim.ts.utils.Position;
import de.tum.ftm.agentsim.ts.utils.SimTime;

/**
 * This is an empty RouteStep for preparation purposes.
 *
 * @author Manfred Klöppel
 */
public class RouteStepVoid extends RouteStep {

    public RouteStepVoid(SimTime startTime) {
        super(startTime, 0, StepType.VOID);
    }

    @Override
    public Position getStartPosition() {
        return null;
    }

    @Override
    public Position getEndPosition() {
        return null;
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public Position getPositionAtTime(long timeMillis) {
        return null;
    }

    @Override
    public void setDurationMS(long newDuration) {

    }

    @Override
    public void updateStartTime(SimTime newStartTime) {

    }

    @Override
    public StringBuilder getWKTFromRouteStep() {
        return null;
    }

    @Override
    public double getRemainingDistanceKM(long timeMillis) {
        return 0;
    }
}
