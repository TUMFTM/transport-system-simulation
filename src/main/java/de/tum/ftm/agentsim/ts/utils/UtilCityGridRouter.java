package de.tum.ftm.agentsim.ts.utils;

import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.routing.GraphHopperRouterCH;
import de.tum.ftm.agentsim.ts.routing.RoutingException;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectRoutable;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;
import org.locationtech.jts.geom.Geometry;
import org.pmw.tinylog.Logger;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is the utility class for the CityGridRouter which provides basic functionality to load or create the
 * CityGrid as well as to query the CityGrid
 *
 * @author Manfred Klöppel, Alexander Schulz
 */
public class UtilCityGridRouter implements Serializable {

    // Duration in seconds, distance in meter
    private CityRoutingGridCell[][] grid;

    // CityGrid spatial definition
    private double gridLeftLon, gridTopLat, gridRightLon, gridBottomLat;
    private double deltaLat, deltaLon;
    private int gridWidthCells, gridHeightCells;

    private static final double EARTH_RADIUS = 6378.137; //km

    private static GraphHopperRouterCH graphHopperRouter = GraphHopperRouterCH.getInstance();
    private AtomicInteger calculationCounter = new AtomicInteger(0); // counter for how many routing operations were done
    private AtomicInteger failedCalculationCounter = new AtomicInteger(0); // counter for how many routing operations failed

    // If the serial version UID is not defined here, it will be computed as a hash of the object,
    // which in our case would fail since we do not include the city object in the file
    private static final long serialVersionUID = 1L;

    /**
     * @param gridTopLeftPoint Point of top-left corner of grid
     * @param cellLength       cell size in meters (cells are square)
     * @param minGridWidth     Width of overall grid in km
     * @param minGridHeight    Width of overall grid in km
     */
    public UtilCityGridRouter(Position gridTopLeftPoint, int cellLength, int minGridWidth, int minGridHeight) {
        this.deltaLat = (cellLength / (EARTH_RADIUS * 1000)) * (180 / Math.PI);
        this.deltaLon = (cellLength / (EARTH_RADIUS * 1000)) * (180 / Math.PI) / Math.cos(gridTopLeftPoint.getLat() * Math.PI / 180);

        this.gridLeftLon = gridTopLeftPoint.getX();
        this.gridTopLat = gridTopLeftPoint.getY();

        this.gridWidthCells = (int) Math.ceil((double) minGridWidth * 1000 / cellLength);
        this.gridHeightCells = (int) Math.ceil((double) minGridHeight * 1000 / cellLength);
        this.gridRightLon = this.gridLeftLon + gridWidthCells * deltaLon;
        this.gridBottomLat = this.gridTopLat - gridHeightCells * deltaLat;
    }


    /**
     * Fill the CityGrid with distance and duration information.
     * If a City-Validity-Area-Geometry is provided, unnecessary routing-operations as well as storage space is
     * avoided.
     */
    public void fillMatrix() {
        // Initiate Grid with empty cells
        Logger.info("Initializing routing grid...");

        // Only calculate/store information for grid cells which are within a valid area
        Geometry cityArea = null;
        if (Config.USE_GRID_VALIDITY_AREA) {
            Logger.info("Using Grid Validity Area");
            cityArea = UtilGeometry.makeGeometryFromWKT(Config.GRID_VALIDITY_AREA_WKT);
        }

        // Populate grid with distance/duration information
        List<CityRoutingGridCell> cellList = new ArrayList<>();
        grid = new CityRoutingGridCell[gridWidthCells][gridHeightCells];
        for (int y = 0; y < gridHeightCells; y++) {
            for (int x = 0; x < gridWidthCells; x++) {
                Position center = getCenterForCell(x, y);

                CityRoutingGridCell cell;
                if (Config.USE_GRID_VALIDITY_AREA && cityArea != null) {
                    Geometry cellArea = UtilGeometry.makeGeometryFromWKT(makePolygonWKTForCell(x, y));
                    cell = new CityRoutingGridCell(x, y, center, cellArea.intersects(cityArea));
                } else {
                    cell = new CityRoutingGridCell(x, y, center, true);
                }
                grid[x][y] = cell;
                cellList.add(cell);
            }
        }

        Logger.info("Calculation of grid route info...");
        try (ProgressBar pb = new ProgressBar(String.format("%s INFO:\t ",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))),
                (long) Math.pow(gridHeightCells * gridWidthCells, 2), ProgressBarStyle.ASCII)) {

            cellList.parallelStream().forEach(cell -> {
                cell.calcRouteInfoToAll();
                pb.stepBy(gridHeightCells * gridWidthCells);
            });
        }
        Logger.info("Calculation count: {} ({} failed calculations)", calculationCounter.get(), failedCalculationCounter.get());
    }


    /**
     * @param x x-index
     * @param y y-index
     * @return Cell at x-/y-indexes
     */
    private CityRoutingGridCell getCell(int x, int y) {
        return grid[x][y];
    }


    /**
     * Calculates the center position of a cell referenced by x-/y-index
     *
     * @param x x-index
     * @param y y-index
     * @return Center location of cell at x-/y-indexes
     */
    private Position getCenterForCell(int x, int y) {
        return new Position(gridLeftLon + (x + 0.5) * deltaLon, gridTopLat - (y + 0.5) * deltaLat);
    }

    /**
     * Returns the x-index of the grid for a given longitude
     *
     * @param LonX Longitude
     * @return Grid x-index
     */
    private int getLonIndex(double LonX) {
        return (int) Math.floor((LonX - gridLeftLon) / deltaLon);
    }


    /**
     * Returns the y-index of the grid for a given latitude
     *
     * @param LatY Latitude
     * @return Grid y-index
     */
    private int getLatIndex(double LatY) {
        return (int) Math.floor((gridTopLat - LatY) / deltaLat);
    }


    /**
     * Returns the corresponding cell for a given Position
     *
     * @param p Position (containing longitude/latitude)
     * @return cell which contains the position
     */
    private CityRoutingGridCell getCellForCoordinates(Position p) {
        return getCell(getLonIndex(p.getX()), getLatIndex(p.getY()));
    }


    /**
     * Returns the duration between two locations
     *
     * @param from start location
     * @param to   stop location
     * @return duration in milliseconds
     */
    public long getDuration(Position from, Position to) throws RoutingException {
        try {
            var fromCell = getCellForCoordinates(from);
            var toCell = getCellForCoordinates(to);
            return (long) (1000 * fromCell.getDurationTo(toCell.x, toCell.y));
        } catch (Exception e) {
            throw new RoutingException("Could not get duration from grid)");
        }
    }


    /**
     * Returns the distance between two locations
     *
     * @param from start location
     * @param to   stop location
     * @return distance in meters
     */
    public double getDistance(Position from, Position to) throws RoutingException {
        try {
            var fromCell = getCellForCoordinates(from);
            var toCell = getCellForCoordinates(to);
            return (double) (fromCell.getDistanceTo(toCell.x, toCell.y));
        } catch (Exception e) {
            throw new RoutingException("Could not get distance from grid)");
        }
    }


    /**
     * General method to write an object to file
     *
     * @param filePath File path where object will be saved to
     * @throws FileNotFoundException File not found
     * @throws IOException           Error while writing
     */
    public void saveToFile(String filePath) throws FileNotFoundException, IOException {
        FileOutputStream fileStream = new FileOutputStream(new File(filePath));
        ObjectOutputStream os = new ObjectOutputStream(fileStream);
        os.writeObject(this);
        os.close();
        fileStream.close();
    }


    /**
     * Load an existing CityGrid from file, returns NULL, if file does not exist
     *
     * @param filePath Path to file
     * @return CityGrid, or NULL if file not found
     */
    public static UtilCityGridRouter loadFromFile(String filePath) {
        FileInputStream fileStream = null;
        ObjectInputStream is = null;
        UtilCityGridRouter grid = null;
        try {
            fileStream = new FileInputStream(new File(filePath));
            is = new ObjectInputStream(fileStream);

            grid = (UtilCityGridRouter) is.readObject();
            is.close();
            fileStream.close();
        } catch (Exception e) {
            try {
                if (fileStream != null) fileStream.close();
                if (is != null) is.close();
            } catch (IOException ioe) {
                e.printStackTrace();
            }
        }
        return grid;
    }


    /**
     * Writes the coordinates of each cell of the grid to a csv file (e.g. for visualisation in QGIS)
     *
     * @param filePath Output file path
     */
    public void writeGridToCSV(String filePath) {
        try {
            filePath = filePath + ".csv";
            FileWriter writer = new FileWriter(filePath);

            writer.append("CELL POLYGON WKT;CELL VALID\n");

            for (int column = 0; column < gridWidthCells; column++) {
                for (int row = 0; row < gridHeightCells; row++) {
                    boolean isCellValid = grid[column][row].isCellValid();
                    writer.append(String.format("%s;%s\n",
                            makePolygonWKTForCell(column, row),
                            isCellValid));
                }
            }
            writer.flush();
            writer.close();
        } catch (Exception e) {
            Logger.error(e);
        }
    }


    /**
     * Create a Polygon-Geometry as WKT-String for a cell
     *
     * @param column x-index
     * @param row    y-index
     * @return String of Cell as a WKT-Geometry
     */
    private String makePolygonWKTForCell(int column, int row) {
        double left = gridLeftLon + column * deltaLon;
        double right = gridLeftLon + (column + 1) * deltaLon;

        double top = gridTopLat - row * deltaLat;
        double bottom = gridTopLat - (row + 1) * deltaLat;

        return String.format("POLYGON ((%s %s, %s %s, %s %s, %s %s, %s %s))",
                left, top,
                right, top,
                right, bottom,
                left, bottom,
                left, top);
    }


    /**
     * Writes CityGrid object to file
     *
     * @param s Object
     * @throws IOException Error while writing
     */
    private void writeObject(ObjectOutputStream s) throws IOException {
        s.writeObject(grid);
        s.writeInt(gridWidthCells);
        s.writeInt(gridHeightCells);
        s.writeDouble(deltaLon);
        s.writeDouble(deltaLat);
        s.writeDouble(gridLeftLon);
        s.writeDouble(gridTopLat);
        s.writeDouble(gridRightLon);
        s.writeDouble(gridBottomLat);
    }

    /**
     * Reads CityGrid object from file
     *
     * @param s Object
     * @throws IOException            Error while reading
     * @throws ClassNotFoundException Error while reading
     */
    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        grid = (CityRoutingGridCell[][]) s.readObject();
        gridWidthCells = s.readInt();
        gridHeightCells = s.readInt();
        deltaLon = s.readDouble();
        deltaLat = s.readDouble();
        gridLeftLon = s.readDouble();
        gridTopLat = s.readDouble();
        gridRightLon = s.readDouble();
        gridBottomLat = s.readDouble();
    }

    public double getDeltaLat() {
        return deltaLat;
    }

    public double getDeltaLon() {
        return deltaLon;
    }

    public int getGridWidthCells() {
        return gridWidthCells;
    }

    public int getGridHeightCells() {
        return gridHeightCells;
    }


    /**
     * CityRoutingGrid consist of this CityRoutingGridCell, which contains the duration/distance information
     * to all of the other CityRoutingGridCells
     */
    public class CityRoutingGridCell implements Serializable {

        // if the serial version UID is not defined like here, it will be computed as a hash of the object,
        // which in our case would fail since we do not include the city object in the file
        private static final long serialVersionUID = 1L;

        private int x, y;
        private short[][] durations = null; // seconds
        private short[][] distances = null; // meters
        private Position center;
        private boolean isCellValid;

        CityRoutingGridCell(int x, int y, Position center, boolean cellIsValid) {
            if (cellIsValid) {
                Position closestValidPoint = graphHopperRouter.getClosestPointOnStreet(center);
                if (closestValidPoint != null) {
                    center = closestValidPoint;
                    isCellValid = true;
                    this.durations = new short[gridWidthCells][gridHeightCells];
                    this.distances = new short[gridWidthCells][gridHeightCells];
                } else {
                    isCellValid = false;
                }
            } else {
                isCellValid = false;
            }
            this.x = x;
            this.y = y;
            this.center = center;
        }


        /**
         * Calculates the duration/distance info to all other valid CityGridRoutingCells using GraphHopper.
         * No travel time factor is applied. In case no route is calculated, the maximum value of the Short value
         * is stored.
         */
        void calcRouteInfoToAll() {
            if (!isCellValid) return;
            for (int x = 0; x < gridWidthCells; x++) {
                for (int y = 0; y < gridHeightCells; y++) {
                    if (getCell(x, y).isCellValid()) {
                        try {
                            var routeInfo = graphHopperRouter.calculateRoute(this.getCenter(), getCell(x, y).getCenter(),
                                    SimObjectRoutable.Type.VOID, 0);

                            durations[x][y] = (short) (routeInfo.getDurationMS() / 1000);
                            distances[x][y] = (short) (Math.round(routeInfo.getDistanceM()));
                            calculationCounter.incrementAndGet();
                        } catch (IOException e) {
                            durations[x][y] = Short.MAX_VALUE;
                            distances[x][y] = Short.MAX_VALUE;
                            failedCalculationCounter.incrementAndGet();
                        }
                    } else {
                        durations[x][y] = Short.MAX_VALUE;
                        distances[x][y] = Short.MAX_VALUE;
                    }
                }
            }
        }

        /**
         * Write CityRoutingGridCell object to file
         */
        private void writeObject(ObjectOutputStream s) throws IOException {
            s.writeInt(x);
            s.writeInt(y);
            s.writeObject(durations);
            s.writeObject(distances);
            s.writeDouble(center.getX());
            s.writeDouble(center.getY());
            s.writeBoolean(isCellValid);
        }

        /**
         * Read CityRoutingGridCell object from file
         */
        private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
            x = s.readInt();
            y = s.readInt();
            durations = (short[][]) s.readObject();
            distances = (short[][]) s.readObject();
            center = new Position(s.readDouble(), s.readDouble());
            isCellValid = s.readBoolean();
        }

        void setDurationToCell(int x, int y, short duration) {
            durations[x][y] = duration;
        }

        short getDurationTo(int x, int y) {
            return durations[x][y];
        }

        short getDistanceTo(int x, int y) {
            return distances[x][y];
        }

        Position getCenter() {
            return center;
        }

        boolean isCellValid() {
            return isCellValid;
        }
    }

}
