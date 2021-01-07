package de.tum.ftm.agentsim.ts.routing.route;

import com.graphhopper.util.gpx.GPXEntry;
import de.tum.ftm.agentsim.ts.routing.GraphHopperRouter;
import de.tum.ftm.agentsim.ts.routing.RoutingException;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectRoutable;
import de.tum.ftm.agentsim.ts.utils.Position;
import de.tum.ftm.agentsim.ts.utils.SimTime;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * This RouteStep describes a RouteStep during which an agent is moving.
 *
 * @author Manfred Klöppel
 */
public class RouteStepEnroute extends RouteStep {

    private Position from;
    private Position to;
    private EnrouteTrack track;
    private GraphHopperRouter hopper;

    /**
     * Create a RouteStepEnroute without specifying a start-time. (Start-time will be 0)
     */
    public RouteStepEnroute(Position from, Position to, StepType type) throws RoutingException {
        this(from, to, type, new SimTime(0));
    }

    /**
     * Create a RouteStepEnroute with specifying a start-time and end-time (overriding the duration/end-time calculated
     * by the routing algorithm).
     */
    public RouteStepEnroute(Position from, Position to, StepType type, SimTime startTime, SimTime endTime) throws RoutingException {
        this(from, to, type, startTime);
        this.endTime.setTime(endTime.getTimeMillis());
        this.durationMS = endTime.getTimeMillis()-startTime.getTimeMillis();
    }

    /**
     * Create a RouteStepEnroute with specifying a start-time.
     */
    public RouteStepEnroute(Position from, Position to, StepType type, SimTime startTime) throws RoutingException {
        super(new SimTime(startTime.getTimeMillis()), 0, type);
        assert (type == StepType.ENROUTE || type == StepType.ENROUTE_RELOCATION || type == StepType.VOID): "RouteStepEnroute must be of type ENROUTE/VOID";

        this.hopper = GraphHopperRouter.getInstance();
        switch (type) {
            case ENROUTE: case ENROUTE_RELOCATION:
                this.track = hopper.calculateRoute(from, to, SimObjectRoutable.Type.CAR, 0);
                break;
            case VOID:
                this.track = hopper.calculateRoute(from, to, SimObjectRoutable.Type.VOID, 0);
                break;
            default:
                assert false: "Invalid StepType for RouteStepEnroute";
        }

        this.from = track.getTrack().firstEntry().getValue();
        this.to = track.getTrack().lastEntry().getValue();

        // Update endTime/durationMS & distanceM
        this.durationMS = track.getDurationMS();  // automatically updates endtime
        setDistanceM(track.getDistanceM());
        this.endTime.setTime(startTime.getTimeMillis() + durationMS);

        if (startTime.getTimeMillis() != 0) track.updateTrackTimes(startTime.getTimeMillis());
    }


    /**
     * Updates starttime and stoptime according to new starttime
     * @param newStartTime
     */
    @Override
    public void updateStartTime(SimTime newStartTime) {
        this.startTime = newStartTime;
        this.endTime.setTime(newStartTime.getTimeMillis() + durationMS);

        track.updateTrackTimes(newStartTime.getTimeMillis());
    }


    /**
     * @return WKT-representation of a Linestring-Geometry
     */
    @Override
    public StringBuilder getWKTFromRouteStep() {
        StringBuilder wkt = new StringBuilder("LINESTRING(");
        for (Position p : track.getTrack().values()) {
            wkt.append(String.format("%s %s, ", p.getLon(), p.getLat()));
        }

        wkt.delete(wkt.length()-2, wkt.length());
        wkt.append(")");

        return wkt;
    }


    @Override
    public Position getStartPosition() {
        return from;
    }

    @Override
    public Position getEndPosition() {
        return to;
    }

    @Override
    public boolean isInterruptible() {
        return true;
    }

    @Override
    public Position getPositionAtTime(long timeMillis) {
        return track.getPositionAtTime(timeMillis);
    }

    @Override
    public double getRemainingDistanceKM(long timeMillis) {
        return track.getRemainingDistanceKM(timeMillis);
    }

    @Override
    public void setDurationMS(long newDuration) {
        assert false: "Duration can not be set for a RouteStepEnroute";
    }


    /**
     * Inner class which stores the routing information as a GPX-Track
     */
    public static class EnrouteTrack {
        private double distanceM;    // Meters
        private long durationMS;      // Millis
        private TreeMap<Long, Position> track = new TreeMap<>();    // Sequence of Route-Positions with timestamp

        /**
         * Creates an EnrouteTrack by providing a GraphHopper-gpxList
         *
         * @param distanceM Distance in meters
         * @param durationMS Duration in milliseconds
         * @param gpxList Graphhopper GPX-List
         */
        public EnrouteTrack(double distanceM, long durationMS, List<GPXEntry> gpxList) {
            this.distanceM = distanceM;
            this.durationMS = durationMS;

            for (GPXEntry entry : gpxList) {
                track.put(entry.getTime(), new Position(entry.getPoint().getLon(), entry.getPoint().getLat()));
            }
        }

        /**
         * Creates an EnrouteTrack with track
         *
         * @param distanceM Distance in meters
         * @param durationMS Duration in milliseconds
         * @param trackPositions Track
         */
        public EnrouteTrack(double distanceM, long durationMS, TreeMap<Long, Position> trackPositions) {
            this.distanceM = distanceM;
            this.durationMS = durationMS;

            this.track = trackPositions;
        }

        /**
         * Updates the timestamps of the track according to a given starttime in millis
         * @param newStartTime new start time of track in millis
         */
        public void updateTrackTimes(long newStartTime) {
            TreeMap<Long, Position> adjustedTrack = new TreeMap<>();
            long minKey = track.firstKey();

            for (Map.Entry<Long, Position> entry : track.entrySet()) {
                adjustedTrack.put(entry.getKey()-minKey+newStartTime, entry.getValue());
            }

            this.track = adjustedTrack;
        }


        /**
         * Determines the current position in the given track depending on the given simulation time. Asserts if
         * provided time is within bounds of track-times.
         * @param timeMillis time given in milliseconds for which the position should be returned
         * @return returns the current position according to the track
         */
        public Position getPositionAtTime(long timeMillis) {
            assert this.track.firstKey() <= timeMillis : "RouteStep is in the future!";
            assert this.track.lastKey() >= timeMillis : "RouteStep is in the past!";

            Map.Entry<Long, Position> prev = this.track.floorEntry((timeMillis));
            Map.Entry<Long, Position> next = this.track.ceilingEntry((timeMillis));

            long timeDelta = next.getKey() - prev.getKey();
            long timeDeltaNow = timeMillis - prev.getKey();
            assert timeDeltaNow >= 0 : "Timedelta needs to be > 0";
            int heading = (int) Math.round(prev.getValue().headingToPosition(next.getValue()));

            if (timeDelta > 0) {
                double progress = (double) timeDeltaNow / timeDelta;

                double newLon = (next.getValue().getLon() - prev.getValue().getLon()) * progress + prev.getValue().getLon();
                double newLat = (next.getValue().getLat() - prev.getValue().getLat()) * progress + prev.getValue().getLat();

                return new Position(newLon, newLat, heading);
            } else {
                return new Position(prev.getValue().getLon(), prev.getValue().getLat(), heading);
            }
        }


        /**
         * Returns the remaining distanceM of the current RouteStep as a linear function depending on the elapsed time
         * @param timeMillis current time
         * @return remaining distanceM in KM
         */
        public double getRemainingDistanceKM(long timeMillis) {
            assert this.track.firstKey() <= timeMillis : "RouteStep is in the future!";
            assert this.track.lastKey() >= timeMillis : "RouteStep is in the past!";

            if (durationMS > 0) {
                return (1 - ((double) (this.track.lastKey() - timeMillis) / durationMS)) * distanceM / 1000;
            } else {
                return 0;
            }
        }

        public double getDistanceM() {
            return distanceM;
        }
        public long getDurationMS() {
            return durationMS;
        }
        public TreeMap<Long, Position> getTrack() {
            return track;
        }
    }
}
