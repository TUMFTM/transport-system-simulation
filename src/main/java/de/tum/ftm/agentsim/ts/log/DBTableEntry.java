package de.tum.ftm.agentsim.ts.log;

import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.routing.route.Route;
import de.tum.ftm.agentsim.ts.utils.Position;
import de.tum.ftm.agentsim.ts.utils.SimTime;

/**
 * This class provides the data structure for all database-table-entries.
 * All possible data fields are defined here. This class makes use of the "Builder Pattern",
 * so that db-entries can be populated with data in a flexible way.
 *
 * @author Manfred Klöppel
 */
public class DBTableEntry {

    // DBTableBookings
    private final Long requestID;
    private final Long personID;
    private final Integer additionalPassengers;
    private final SimTime origStartTime;
    private final SimTime origStopTime;
    private final Position origStartPosition;
    private final Position origStopPosition;
    private final Position startPosition;
    private final Position stopPosition;
    private final Double origDistanceKM;
    private final Double origDurationMIN;
    private final Long vehicleID;
    private final SimTime timeTripAssigned;
    private final SimTime timeTripPickupLatest;
    private final SimTime timeTripPickedUp;
    private final SimTime timeTripDeparture;
    private final SimTime timeTripDropoffLatest;
    private final SimTime timeTripDroppedOff;
    private final SimTime timeTripCompleted;
    private final Double drivingDistanceKM;
    private final Double drivingDurationMIN;
    private final Route route;
    private final Boolean bookingWasShared;
    private final String status;

    // DBTableVehicles
    private final Integer maxSimultaneousRequests;
    private final Integer maxSimultaneousUsers;
    private final Integer servedRequests;
    private final Integer servedPassengers;
    private final Long durationIdleSumMS;
    private final Long durationIdleMaxMS;
    private final Long durationRelocationSumMS;
    private final Long durationBusyDrivingSumMS;
    private final Long durationBusyDwellingSumMS;
    private final Double energyConsumptionKwh;

    // DBTableSimObjectStatus
    private final Long objectID;
    private final String objectType;
    private final SimTime currentTime;
    private final Integer currentPaxCount;
    private final Integer currentRequestsCount;
    private final Position currentPosition;

    // DBTableRoutes
    private final Integer routeID;
    private final String stepType;
    private final String geomWKT;

    public static class Builder {

        // Required Parameters
        // - none -

        // Optional Parameters - initialized to default values
        private Long bookingID                      = null;
        private Long personID                       = null;
        private Integer additionalPassengers        = null;
        private SimTime origStartTime               = null;
        private SimTime origStopTime                = null;
        private Position origStartPosition          = null;
        private Position origStopPosition           = null;
        private Position stopPosition               = null;
        private Position startPosition              = null;
        private Double origDistanceKM               = null;
        private Double origDurationMIN              = null;
        private Long vehicleID                      = null;
        private SimTime timeBookingAssigned         = null;
        private SimTime timeBookingPickupLatest     = null;
        private SimTime timeBookingPickedUp         = null;
        private SimTime timeBookingDeparture        = null;
        private SimTime timeBookingDropoffLatest    = null;
        private SimTime timeBookingDroppedOff       = null;
        private SimTime timeBookingCompleted        = null;
        private Double drivingDistanceKM            = null;
        private Double drivingDurationMIN           = null;
        private Route route                         = null;
        private Boolean bookingWasShared            = null;
        private String status                       = null;
        private Integer maxSimultaneousRequests     = null;
        private Integer maxSimultaneousUsers        = null;
        private Integer servedRequests              = null;
        private Long objectID                       = null;
        private String objectType                   = null;
        private SimTime currentTime                 = null;
        private Integer currentPaxCount             = null;
        private Integer currentRequestsCount        = null;
        private Position currentPosition            = null;
        private Integer routeID                     = null;
        private String stepType                     = null;
        private String geomWKT                      = null;
        private Integer servedPassengers            = null;
        private Long durationIdleSumMS              = null;
        private Long durationIdleMaxMS              = null;
        private Long durationRelocationSumMS        = null;
        private Long durationBusyDrivingSumMS       = null;
        private Long durationBusyDwellingSumMS      = null;
        private Double energyConsumptionKwh         = null;

        public Builder() {
        }

        public Builder bookingID(long bookingID) {
            this.bookingID = bookingID;
            return this;
        }
        public Builder personID(long personID) {
            this.personID = personID;
            return this;
        }
        public Builder additionalPassengers(int additionalPassengers) {
            this.additionalPassengers = additionalPassengers;
            return this;
        }
        public Builder origStartTime(SimTime origStartTime) {
            this.origStartTime = origStartTime;
            return this;
        }
        public Builder origStopTime(SimTime origStopTime) {
            this.origStopTime = origStopTime;
            return this;
        }
        public Builder origStartPosition(Position origStartPosition) {
            this.origStartPosition = origStartPosition;
            return this;
        }
        public Builder origStopPosition(Position origStopPosition) {
            this.origStopPosition = origStopPosition;
            return this;
        }
        public Builder startPosition(Position startPosition) {
            this.startPosition = startPosition;
            return this;
        }
        public Builder stopPosition(Position stopPosition) {
            this.stopPosition = stopPosition;
            return this;
        }
        public Builder origDistanceKM(double origDistance) {
            this.origDistanceKM = origDistance;
            return this;
        }
        public Builder origDurationMIN(double origDuration) {
            this.origDurationMIN = origDuration;
            return this;
        }
        public Builder energyConsumptionKwh(double energyConsumptionKwh) {
            this.energyConsumptionKwh = energyConsumptionKwh;
            return this;
        }
        public Builder vehicleID(Long vehicleID) {
            this.vehicleID = vehicleID;
            return this;
        }
        public Builder timeBookingAssigned(SimTime timeBookingAssigned) {
            this.timeBookingAssigned = timeBookingAssigned;
            return this;
        }
        public Builder timeBookingPickupLatest(SimTime timeBookingPickupLatest) {
            this.timeBookingPickupLatest = timeBookingPickupLatest;
            return this;
        }
        public Builder timeBookingPickedUp(SimTime timeBookingPickedUp) {
            this.timeBookingPickedUp = timeBookingPickedUp;
            return this;
        }
        public Builder timeBookingDeparture(SimTime timeBookingDeparture) {
            this.timeBookingDeparture = timeBookingDeparture;
            return this;
        }
        public Builder timeBookingDropoffLatest(SimTime timeBookingDropoffLatest) {
            this.timeBookingDropoffLatest = timeBookingDropoffLatest;
            return this;
        }
        public Builder timeBookingDroppedOff(SimTime timeBookingDroppedOff) {
            this.timeBookingDroppedOff = timeBookingDroppedOff;
            return this;
        }
        public Builder timeBookingCompleted(SimTime timeBookingCompleted) {
            this.timeBookingCompleted = timeBookingCompleted;
            return this;
        }
        public Builder drivingDistanceKM(double drivingDistance) {
            this.drivingDistanceKM = drivingDistance;
            return this;
        }
        public Builder drivingDurationMIN(double drivingDuration) {
            this.drivingDurationMIN = drivingDuration;
            return this;
        }
        public Builder bookingWasShared(Boolean status) {
            this.bookingWasShared = status;
            return this;
        }
        public Builder status(String status) {
            this.status = status;
            return this;
        }
        public Builder route(Route route) {
            this.route = route;
            return this;
        }
        public Builder maxSimultaneousRequests(Integer maxSimultaneousRequests) {
            this.maxSimultaneousRequests = maxSimultaneousRequests;
            return this;
        }
        public Builder maxSimultaneousUsers(Integer maxSimultaneousUsers) {
            this.maxSimultaneousUsers = maxSimultaneousUsers;
            return this;
        }
        public Builder servedRequests(Integer servedRequests) {
            this.servedRequests = servedRequests;
            return this;
        }
        public Builder servedPassengers(Integer servedPassengers) {
            this.servedPassengers = servedPassengers;
            return this;
        }
        public Builder durationIdleSumMS(Long durationIdleSumMS) {
            this.durationIdleSumMS = durationIdleSumMS;
            return this;
        }
        public Builder durationIdleMaxMS(Long durationIdleMaxMS) {
            this.durationIdleMaxMS = durationIdleMaxMS;
            return this;
        }
        public Builder durationBusyDrivingSumMS(Long durationBusyDrivingSumMS) {
            this.durationBusyDrivingSumMS = durationBusyDrivingSumMS;
            return this;
        }
        public Builder durationBusyDwellingSumMS(Long durationBusyDwellingSumMS) {
            this.durationBusyDwellingSumMS = durationBusyDwellingSumMS;
            return this;
        }
        public Builder durationRelocationSumMS(Long durationRelocationSumMS) {
            this.durationRelocationSumMS = durationRelocationSumMS;
            return this;
        }
        public Builder objectID(Long objectID) {
            this.objectID = objectID;
            return this;
        }
        public Builder objectType(String objectType) {
            this.objectType = objectType;
            return this;
        }
        public Builder currentTime(SimTime currentTime) {
            this.currentTime = currentTime;
            return this;
        }
        public Builder currentPaxCount(Integer currentPaxCount) {
            this.currentPaxCount = currentPaxCount;
            return this;
        }
        public Builder currentRequestsCount(Integer currentRequestsCount) {
            this.currentRequestsCount = currentRequestsCount;
            return this;
        }
        public Builder currentPosition(Position currentPosition) {
            this.currentPosition = currentPosition;
            return this;
        }
        public Builder routeID(Integer routeID) {
            this.routeID = routeID;
            return this;
        }
        public Builder stepType(String stepType) {
            this.stepType = stepType;
            return this;
        }
        public Builder geomWKT(String geomWKT) {
            this.geomWKT = geomWKT;
            return this;
        }

        public DBTableEntry build() {
            return new DBTableEntry(this);
        }
    }

    public DBTableEntry(Builder builder) {
        this.requestID = builder.bookingID;
        this.personID = builder.personID;
        this.additionalPassengers = builder.additionalPassengers;
        this.origStartTime = builder.origStartTime;
        this.origStopTime = builder.origStopTime;
        this.origStartPosition = builder.origStartPosition;
        this.origStopPosition = builder.origStopPosition;
        this.stopPosition = builder.stopPosition;
        this.startPosition = builder.startPosition;
        this.origDistanceKM = builder.origDistanceKM;
        this.origDurationMIN = builder.origDurationMIN;
        this.vehicleID = builder.vehicleID;
        this.timeTripAssigned = builder.timeBookingAssigned;
        this.timeTripPickupLatest = builder.timeBookingPickupLatest;
        this.timeTripPickedUp = builder.timeBookingPickedUp;
        this.timeTripDeparture = builder.timeBookingDeparture;
        this.timeTripDropoffLatest = builder.timeBookingDropoffLatest;
        this.timeTripDroppedOff = builder.timeBookingDroppedOff;
        this.timeTripCompleted = builder.timeBookingCompleted;
        this.drivingDistanceKM = builder.drivingDistanceKM;
        this.drivingDurationMIN = builder.drivingDurationMIN;
        this.route = builder.route;
        this.status = builder.status;
        this.maxSimultaneousRequests = builder.maxSimultaneousRequests;
        this.maxSimultaneousUsers = builder.maxSimultaneousUsers;
        this.servedRequests = builder.servedRequests;
        this.servedPassengers = builder.servedPassengers;
        this.durationBusyDrivingSumMS = builder.durationBusyDrivingSumMS;
        this.durationBusyDwellingSumMS = builder.durationBusyDwellingSumMS;
        this.durationIdleMaxMS = builder.durationIdleMaxMS;
        this.durationIdleSumMS = builder.durationIdleSumMS;
        this.durationRelocationSumMS = builder.durationRelocationSumMS;
        this.objectID = builder.objectID;
        this.objectType = builder.objectType;
        this.currentTime = builder.currentTime;
        this.currentPaxCount = builder.currentPaxCount;
        this.currentRequestsCount = builder.currentRequestsCount;
        this.currentPosition = builder.currentPosition;
        this.routeID = builder.routeID;
        this.stepType = builder.stepType;
        this.geomWKT = builder.geomWKT;
        this.bookingWasShared = builder.bookingWasShared;
        this.energyConsumptionKwh = builder.energyConsumptionKwh;
    }

    public Long getRequestID() {
        return requestID;
    }

    public Long getPersonID() {
        return personID;
    }

    public Integer getAdditionalPassengers() {
        return additionalPassengers;
    }

    public SimTime getOrigStartTime() {
        return origStartTime;
    }

    public SimTime getOrigStopTime() {
        return origStopTime;
    }

    public Position getOrigStartPosition() {
        return origStartPosition;
    }

    public Position getOrigStopPosition() {
        return origStopPosition;
    }

    public Position getStartPosition() {
        return startPosition;
    }

    public Position getStopPosition() {
        return stopPosition;
    }

    public Double getOrigDistanceKM() {
        return origDistanceKM;
    }

    public Double getOrigDurationMIN() {
        return origDurationMIN;
    }

    public Long getVehicleID() {
        return vehicleID == null ? 0 : vehicleID;
    }

    public String getTimeTripAssigned() {
        return timeTripAssigned == null ? null : timeTripAssigned.toString();
    }

    public String getTimeTripPickupLatest() {
        return timeTripPickupLatest == null ? null : timeTripPickupLatest.toString();
    }

    public String getTimeTripPickedUp() {
        return timeTripPickedUp == null ? null : timeTripPickedUp.toString();
    }

    public String getTimeTripDeparture() {
        return timeTripDeparture == null ? null : timeTripDeparture.toString();
    }

    public String getTimeTripDropoffLatest() {
        return timeTripDropoffLatest == null ? null : timeTripDropoffLatest.toString();
    }

    public String getTimeTripDroppedOff() {
        return timeTripDroppedOff == null ? null : timeTripDroppedOff.toString();
    }

    public String getTimeTripCompleted() {
        return timeTripCompleted == null ? null : timeTripCompleted.toString();
    }

    public Double getDrivingDistanceKM() {
        return drivingDistanceKM == null ? 0 : drivingDistanceKM;
    }

    public Double getDrivingDurationMIN() {
        return drivingDurationMIN == null ? 0 : drivingDurationMIN;
    }

    public Double getEnergyConsumptionKwh() {
        return energyConsumptionKwh == null ? 0 : energyConsumptionKwh;
    }

    public String getRoute() {
        return route == null ? null : route.createMultiLineStringWKTFromRoute().toString();
    }

    public String getStatus() {
        return status;
    }

    public Integer getMaxSimultaneousRequests() {
        return maxSimultaneousRequests;
    }

    public Integer getMaxSimultaneousUsers() {
        return maxSimultaneousUsers;
    }

    public Integer getServedRequests() {
        return servedRequests;
    }

    public Integer getServedPassengers() {
        return servedPassengers;
    }

    public Long getDurationIdleSumMS() {
        return durationIdleSumMS;
    }

    public Long getDurationIdleMaxMS() {
        return durationIdleMaxMS;
    }

    public Long getDurationRelocationSumMS() {
        return durationRelocationSumMS;
    }

    public Long getDurationBusyDrivingSumMS() {
        return durationBusyDrivingSumMS;
    }

    public Long getDurationBusyDwellingSumMS() {
        return durationBusyDwellingSumMS;
    }

    public Long getObjectID() {
        return objectID;
    }

    public String getObjectType() {
        return objectType;
    }

    public SimTime getCurrentTime() {
        return currentTime;
    }

    public Integer getCurrentPaxCount() {
        return currentPaxCount == null ? 9999 : currentPaxCount;
    }

    public Integer getCurrentRequestsCount() {
        return currentRequestsCount == null ? 9999 : currentRequestsCount;
    }

    public Position getCurrentPosition() {
        return currentPosition;
    }

    public Integer getRouteID() {
        return routeID;
    }

    public String getStepType() {
        return stepType;
    }

    public String getGeomWKT() {
        if (Config.ENABLE_LOG_ROUTEHISTORY) {
            return geomWKT;
        } else {
            return null;
        }
    }

    public Boolean getBookingWasShared() {
        return bookingWasShared;
    }
}
