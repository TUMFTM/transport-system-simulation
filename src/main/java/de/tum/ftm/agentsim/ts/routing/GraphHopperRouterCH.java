package de.tum.ftm.agentsim.ts.routing;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.isochrone.algorithm.DelaunayTriangulationIsolineBuilder;
import com.graphhopper.isochrone.algorithm.Isochrone;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PMap;
import com.graphhopper.util.gpx.GPXEntry;
import com.graphhopper.util.gpx.GpxFromInstructions;
import com.graphhopper.util.shapes.GHPoint;
import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.routing.route.RouteStepEnroute;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectRoutable;
import de.tum.ftm.agentsim.ts.utils.Position;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This implements the GraphHopper Routing Engine using Contraction Hierachies
 */
public class GraphHopperRouterCH implements RoutingInterface {

    private GraphHopper hopper;
    private DelaunayTriangulationIsolineBuilder delaunayTriangulationIsolineBuilder;
    private GeometryFactory geometryFactory = new GeometryFactory();
    private static AtomicLong callCounter = new AtomicLong(0);

    // Singleton
    private static GraphHopperRouterCH INSTANCE = new GraphHopperRouterCH();


    /**
     * Private constructor for GraphHopperRouterCH.
     * Call GraphHopperRouterCH.getInstance() to obtain a instance of this class.
     */
    private GraphHopperRouterCH() {
        delaunayTriangulationIsolineBuilder = new DelaunayTriangulationIsolineBuilder();
        hopper = new GraphHopperOSM();
        hopper.forDesktop();    // set performance settings
        hopper.setGraphHopperLocation(Config.GRAPHHOPPER_CH_FOLDER_GRAPH);
        hopper.setMinNetworkSize(200, 200);

        hopper.setEncodingManager(EncodingManager.create("car,foot"));

        hopper.setDataReaderFile(Config.GRAPHHOPPER_OSM_FILE);  // set path to OSM input file
        hopper.importOrLoad();
    }


    /**
     * Get the singleton instance.
     * @return GraphHopper Instance
     */
    public static GraphHopperRouterCH getInstance() {
        return INSTANCE;
    }


    /**
     * Request a PathWrapper object for a route between <from> and <to>. If a start-position contains a heading
     * the heading will be taken into consideration for the route
     * @param from Start point of the route
     * @param to End point of the route
     * @param vehicleType car or foot
     * @return A routed path
     * @throws RoutingException Error thrown, if no route could be calculated
     */
    private PathWrapper calculatePath(Position from, Position to, SimObjectRoutable.Type vehicleType) throws RoutingException {
        GHRequest request;
        request = new GHRequest().addPoint(new GHPoint(from.getY(), from.getX())).addPoint(new GHPoint(to.getY(), to.getX()));
        request.setWeighting("fastest");

        // Set GraphHopper vehicle type
        switch (vehicleType) {
            case CAR:
                request.setVehicle("car");
                break;
            case FOOT:
                request.setVehicle("foot");
                break;
            default:
                request.setVehicle("car");
                break;
        }

        GHResponse resp = hopper.route(request);

        // Check GraphHopper route for errors
        if (resp.hasErrors()) {
            var errors = resp.getErrors();
            StringBuilder builder = new StringBuilder();
            for (var err : errors) {
                builder.append(err);
            }
            throw new RoutingException("Could not compute route: " + builder.toString());
        }

        callCounter.incrementAndGet();
        return resp.getBest();
    }


    /**
     * Calculates the route between the provided Positions and returns the route as EnrouteTrack. Travel durations are
     * corrected by travel time factor.
     * @param from Start point of route
     * @param to End point of route
     * @param vehicleType foot or car
     * @return Returns a Track containing the distance, duration, and the positions of travel
     */
    public RouteStepEnroute.EnrouteTrack calculateRoute(Position from, Position to, SimObjectRoutable.Type vehicleType, long startTime) throws RoutingException {

        // Calculate Route
        PathWrapper path = calculatePath(from, to, vehicleType);
        InstructionList instructionList = path.getInstructions();
        List<GPXEntry> gpxPath = GpxFromInstructions.createGPXList(instructionList);

        // Update Travel times with travel time factor
        double travelTimeFactor = getTravelTimeFactor(vehicleType);
        var pathTime = interpolateGPXTimes(gpxPath);

        TreeMap<Long, Position> trackMap = new TreeMap<>();
        for (GraphHopperRouterCH.TimePosition entry : pathTime) {
            trackMap.put(
                    (long) (entry.time * travelTimeFactor) + startTime, // adjusted starttime
                    entry.position										// position
            );
        }

        return new RouteStepEnroute.EnrouteTrack(path.getDistance(), trackMap.lastKey(), trackMap);
    }

    /**
     * Calculates the timestamp for route points where Graphhopper did not include them.
     * The calculation interpolates the duration between positions with given duration
     * @param gpxPath Path returned from Graphhopper with missing timestamps
     * @return List with Position with Timestamps
     */
    private List<GraphHopperRouterCH.TimePosition> interpolateGPXTimes(List<GPXEntry> gpxPath) {
        Long stepBegin = 0L;

        List<GraphHopperRouterCH.TimePosition> finalList = new ArrayList<>();
        List<GraphHopperRouterCH.TimePosition> tempList = new ArrayList<>();

        for (GPXEntry entry : gpxPath) {
            if (entry.getTime() != null && tempList.size() == 0) {
                stepBegin = entry.getTime();
                tempList.add(new GraphHopperRouterCH.TimePosition(entry.getTime(), new Position(entry.getPoint().getLon(), entry.getPoint().getLat())));
            } else if (entry.getTime() == null) {
                tempList.add(new GraphHopperRouterCH.TimePosition(null, new Position(entry.getPoint().getLon(), entry.getPoint().getLat())));
            } else {
                Long stepEnd = entry.getTime();

                if (tempList.size() > 1) {
                    long stepDuration = (stepEnd - stepBegin) / tempList.size();

                    int i = 0;
                    for (var step : tempList) {
                        step.time = stepBegin + stepDuration * i;
                        i++;
                    }
                }
                finalList.addAll(tempList);

                tempList.clear();
                tempList.add(new GraphHopperRouterCH.TimePosition(stepEnd, new Position(entry.getPoint().getLon(), entry.getPoint().getLat())));
                stepBegin = stepEnd;
            }
        }
        finalList.addAll(tempList);

        // update heading info
        updateHeadingInfo(finalList);
        return finalList;
    }

    /**
     * Adds heading information to a Position in a route. The heading is the direction from the first point
     * to the second point. The heading of the last point is copied from the one before the last point.
     * @param timePositionList List with positions without heading-information
     */
    private void updateHeadingInfo(List<GraphHopperRouterCH.TimePosition> timePositionList) {
        if (timePositionList.size() > 1) {
            for (int i = 0; i < timePositionList.size() - 1; i++) {
                timePositionList.get(i).position.setHeadingToPosition(timePositionList.get(i + 1).position);
            }
            timePositionList.get(timePositionList.size() - 1).position.setHeading(timePositionList.get(timePositionList.size() - 2).position.getHeading());
        }
    }

    /**
     * Helper class to store timestamps with positions
     */
    private static class TimePosition {
        Long time;
        Position position;

        public TimePosition(Long time, Position position) {
            this.time = time;
            this.position = position;
        }
    }


    /**
     * Compute the closest valid point to a given location.
     * @param point location (possibly invalid for routing).
     * @return nearest valid point on street, else NULL
     */
    public Position getClosestPointOnStreet(Position point) {
        LocationIndex l;
        QueryResult query = hopper.getLocationIndex().findClosest(point.getY(), point.getX(), EdgeFilter.ALL_EDGES);
        if (query.isValid()) {
            var snappedPoint = query.getSnappedPoint();
            return new Position(snappedPoint.lon, snappedPoint.lat);
        }
        return null;
    }

    /**
     * Calculates an isochrone for a given position. The time is calculated for car travel
     *
     * @param pos The position for which the isochrone is calculated
     * @param duration Duration for isochrone given in seconds
     * @param toCenter false if the isochrone is calculated from the given position,
     *                 true if the isochrone is calculated towards the given position
     * @return Polygon-Geometry which describes the isochrone
     */
    public Geometry getIsochrone(Position pos, int duration, boolean toCenter) {
        GHPoint isochroneCenter = new GHPoint(pos.getLat(), pos.getLon());

        // get encoder from GraphHopper instance
        EncodingManager encodingManager = hopper.getEncodingManager();
        FlagEncoder encoder = encodingManager.getEncoder("car");

        // pick the closest point on the graph to the query point and generate a query graph
        QueryResult qr = hopper.getLocationIndex().findClosest(isochroneCenter.lat, isochroneCenter.lon, DefaultEdgeFilter.allEdges(encoder));

        Graph graph = hopper.getGraphHopperStorage();
        QueryGraph queryGraph = new QueryGraph(graph);
        queryGraph.lookup(Collections.singletonList(qr));

        // calculate isochrone from query graph
        PMap pMap = new PMap();
        Isochrone isochrone = new Isochrone(queryGraph, new FastestWeighting(encoder, pMap), toCenter);
        isochrone.setTimeLimit(duration); // seconds

        List<List<Coordinate>> res = isochrone.searchGPS(qr.getClosestNode(), 1);

        ArrayList<JsonFeature> features = new ArrayList<>();
        List<Coordinate[]> polygonShells = delaunayTriangulationIsolineBuilder.calcList(res, res.size() - 1);
        for (Coordinate[] polygonShell : polygonShells) {
            JsonFeature feature = new JsonFeature();
            HashMap<String, Object> properties = new HashMap<>();
            properties.put("bucket", features.size());
            feature.setProperties(properties);
            feature.setGeometry(geometryFactory.createPolygon(polygonShell));
            features.add(feature);
        }

        return features.get(0).getGeometry();
    }

    @Override
    public long getRoutingCallCounter() {
        return callCounter.get();
    }
}
