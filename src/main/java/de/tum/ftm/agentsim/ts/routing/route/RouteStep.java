package de.tum.ftm.agentsim.ts.routing.route;

import de.tum.ftm.agentsim.ts.simobjects.SimObjectStatus;
import de.tum.ftm.agentsim.ts.simobjects.User;
import de.tum.ftm.agentsim.ts.simobjects.Vehicle;
import de.tum.ftm.agentsim.ts.utils.Position;
import de.tum.ftm.agentsim.ts.utils.SimTime;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A class that represents an elementary step on a route. RouteSteps can be either stationary (e.g. pickup/dropoff)
 * or enroute (e.g. vehicles drives to the next stop). Each RouteStep is of a specific type (e.g. Enroute_Relocation)
 *
 * @author Manfred Klöppel
 */
public abstract class RouteStep implements Comparable<RouteStep> {

	private static AtomicInteger routeStepCount = new AtomicInteger(0);

	int routeStepID;
	SimTime startTime;
	SimTime endTime;
	long durationMS;      // Milliseconds
	double distanceM;     // Meters
	StepType type;
	private int passengerCount = 0;
	private int requestCount = 0;


    public RouteStep(SimTime startTime, long durationInMillis, StepType type) {
        this.routeStepID = makeRouteStepID();
    	this.startTime = startTime;
        this.durationMS = durationInMillis;
        this.type = type;
        this.distanceM = 0;

        this.endTime = new SimTime(startTime.getTimeMillis() + durationInMillis);
    }

    public abstract Position getStartPosition();
    public abstract Position getEndPosition();
	public abstract boolean isInterruptible();
	public abstract Position getPositionAtTime(long timeMillis);
	public abstract void setDurationMS(long newDuration);
    public abstract void updateStartTime(SimTime newStartTime);
    public abstract double getRemainingDistanceKM(long timeMillis);
	abstract public StringBuilder getWKTFromRouteStep();


	/**
	 * Definition of different RouteStep-Types
	 */
	public enum StepType{
	    // Stationary RouteSteps
		PICKUP,					// Pickup of a User
		DROPOFF, 				// Drop-off of a User
		ROUTE_UPDATE, 			// Marks the location where a vehicle received an updated route
		RELOCATION_ARRIVED,		// Marks the location where a vehicle finished a repositioning due to relocation

        // Moving/Enroute RouteSteps
        ENROUTE, 				// Vehicle enroute to the next stop (e.g. pickup/drop-off)
		ENROUTE_RELOCATION,		// Vehicle enroute due to relocation

		// "Empty" StepType (e.g. for preparation process of database)
		VOID
	}


	/**
	 * Create a routeStepID and increment the static counter.
	 * @return A unique routeStepID.
	 */
	private static int makeRouteStepID() {
		return routeStepCount.getAndIncrement();
	}


	/**
	 * Convenience function to set both requests- and passenger-count simultaneously
	 * @param v vehicle of which the information about current request- and passenger-number aboard is stored
	 */
	public void setRequestsAndPassengerCount(Vehicle v) {
		this.passengerCount = v.getPassengers();
		requestCount = 0;

		for (User.TripRequest req : v.getUserRequestMap().values()) {
			if (req.getUser().getStatus() == SimObjectStatus.USER_IN_TRANSIT) {
				requestCount += 1;
			}
		}
	}


	/**
	 * Check if a RouteStep is during a given timestamp
	 * @param timestamp Timestamp which should be checked
	 * @return True, if RouteStep is during the timestamp, else false
	 */
	public boolean isFutureTimeWithinRouteStep(SimTime timestamp) {
		return (timestamp.isGreaterOrEqualThan(startTime) && (timestamp.isLessOrEqualThan(endTime)));
	}

	public int getPassengerCount() {
		return passengerCount;
	}
	public void setPassengerCount(int passengerCount) {
		this.passengerCount = passengerCount;
	}
	public int getRequestCount() {
		return requestCount;
	}
	public void setRequestCount(int requestCount) {
		this.requestCount = requestCount;
	}
	public double getDistanceM() {
		return distanceM;
	}
	public long getDurationMS() {
		return durationMS;
	}
	public void setDistanceM(double distanceM) {
		this.distanceM = distanceM;
	}
	public SimTime getStartTime() {
		return startTime;
	}
	public SimTime getEndTime() {
		return endTime;
	}
	public StepType getStepType() {
		return type;
	}


	/**
	 * Two routeSteps are compared by their start-time. If the start-time is the same, then the time with the shorter
	 * durationMS is "smaller". If the durationMS is the also equal, then the ID of the routeStep decides the order.
	 * @param o other routeStep to compare to
	 * @return compareTo-Integer
	 */
	@Override
	public int compareTo(RouteStep o) {
		if (this.startTime.getTimeMillis() == o.getStartTime().getTimeMillis()) {
			Long thisTime = this.startTime.getTimeMillis() + this.durationMS;
			Long thatTime = o.getStartTime().getTimeMillis() + o.getDurationMS();
			if (thisTime.equals(thatTime)) {
				Integer thisID = this.routeStepID;
				Integer otherID = o.routeStepID;
				return thisID.compareTo(otherID);
			} else {
				return thisTime.compareTo(thatTime);
			}
		} else {
			return this.startTime.compareTo(o.getStartTime());
		}
	}

	@Override
	public int hashCode() {
		return routeStepID;
	}
}
