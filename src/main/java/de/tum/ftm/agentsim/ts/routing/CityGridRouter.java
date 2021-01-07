package de.tum.ftm.agentsim.ts.routing;

import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.routing.route.RouteStepEnroute;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectRoutable;
import de.tum.ftm.agentsim.ts.utils.Position;
import de.tum.ftm.agentsim.ts.utils.UtilCityGridRouter;
import org.pmw.tinylog.Logger;

import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of the CityGridRouter, which determines travel duration and distance via a matrix-lookup.
 */
public class CityGridRouter implements RoutingInterface {

    private static AtomicLong callCounter = new AtomicLong(0);

    private static UtilCityGridRouter routingGrid; // duration in Seconds

    // Singleton instance
    private static CityGridRouter INSTANCE = new CityGridRouter();

    private CityGridRouter() {
        routingGrid = null;
    }

    public static CityGridRouter getInstance() {
        return INSTANCE;
    }


    /**
     * Loads the CityGrid from a file. If the file is not found, a new CityGrid is calculated and saved to file.
     */
    public void loadCityGridData() {
        routingGrid = UtilCityGridRouter.loadFromFile(Config.DURATION_GRID_PATH);

        // If file could not be loaded or has not been calculated before, calculate it now
        if (routingGrid == null) {
            Logger.info("Duration Grid File not found, create new grid...");

            Logger.info("Creating Grid...");
            routingGrid = new UtilCityGridRouter(
                    new Position(Config.GRID_TOP_LEFT_LONGITUDE,Config.GRID_TOP_LEFT_LATITUDE),
                    Config.DURATION_GRID_CELL_LENGTH,
                    Config.GRID_MIN_WIDTH_KM,
                    Config.GRID_MIN_HEIGHT_KM);

            Logger.info("Calculating Grid Durations...");
            routingGrid.fillMatrix();

            try {
                routingGrid.saveToFile(Config.DURATION_GRID_PATH);
                Logger.info("Saved Grid Map File!");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Logger.info("City Grid loaded!");
        }

        // If configured, write the grid-information (longitude, latitude, validity) to a CSV-file
        if (Config.WRITE_DURATION_GRID_TO_CSV) routingGrid.writeGridToCSV(Config.DURATION_GRID_PATH);
    }


    /**
     * Private function to get distance and duration information from the CityGrid
     *
     * @param from  start-position
     * @param to    end-position
     * @return      Duration in millis and distance in meters
     * @throws RoutingException If CityGrid was not loaded or not data was returned from the CityGrid
     */
    private GridDurationAndDistance getGridDurationAndDistance(Position from, Position to) throws RoutingException {
        if (routingGrid != null) {
            callCounter.incrementAndGet();
            return new GridDurationAndDistance(
                    routingGrid.getDuration(from, to), // millis
                    routingGrid.getDistance(from, to)  // meters
            );
        }
        else throw new RoutingException("CityGrid not loaded!");
    }


    /**
     * Calculates the route between the provided Positions and returns the route as EnrouteTrack. Travel durations are
     * corrected by travel time factor.
     * If no route is returned by the CityGrid, a route using GraphHopper is calculated as fallback.
     *
     * @param from Start point of route
     * @param to End point of route
     * @param type mode of travel
     * @param time starttime of returned track
     * @return Returns a Track containing the distance, duration, and the positions of travel
     */
    @Override
    public RouteStepEnroute.EnrouteTrack calculateRoute(Position from, Position to, SimObjectRoutable.Type type, long time) throws RoutingException {
        try {
            // Adjust travel duration by travel time factor
            double travelTimeFactor = getTravelTimeFactor(type);

            var gridRouteData = getGridDurationAndDistance(from, to);
            long adjustedDuration = (long) (gridRouteData.getDuration() * travelTimeFactor);

            // Tracks from routing grid only contain the start and stop location
            var trackMap = new TreeMap<Long, Position>();
            trackMap.put(time, from);
            trackMap.put(time + adjustedDuration, to);

            return new RouteStepEnroute.EnrouteTrack(gridRouteData.getDistance(), adjustedDuration, trackMap);

        } catch (RoutingException re) {
            // Fallback to GraphHopper, if no Route Info is provided by the CityGrid
            if (Config.ENABLE_GRIDROUTER_WARNINGS) {
                Logger.warn("Routing with CityGrid failed, fallback to GraphHopper ({},{} to {},{})",
                        from.getY(), from.getX(), to.getY(), to.getX());
            }
            return GraphHopperRouter.getInstance().calculateRoute(from, to, type, time);
        }
    }


    /**
     * @return Number of calls of the CityGridRouter
     */
    public long getRoutingCallCounter() {
        return callCounter.get();
    }


    /**
     * Helper class to store both duration and distance information returned from the CityGrid
     */
    static class GridDurationAndDistance {

        private long duration;
        private double distance;

        GridDurationAndDistance(long duration, double distance) {
            this.duration = duration;
            this.distance = distance;
        }

        long getDuration() {
            return duration;
        }
        double getDistance() {
            return distance;
        }
    }
}
