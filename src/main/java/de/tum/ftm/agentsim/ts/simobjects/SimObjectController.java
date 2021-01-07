package de.tum.ftm.agentsim.ts.simobjects;

import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.Scenario;
import de.tum.ftm.agentsim.ts.events.Event_RebalancingManager;
import de.tum.ftm.agentsim.ts.events.Event_UserRequest;
import de.tum.ftm.agentsim.ts.events.Event;
import de.tum.ftm.agentsim.ts.log.DBLog;
import de.tum.ftm.agentsim.ts.log.DBTableEntry;
import de.tum.ftm.agentsim.ts.routing.route.RouteStep;
import de.tum.ftm.agentsim.ts.routing.route.RouteStepEnroute;
import de.tum.ftm.agentsim.ts.simobjects.rebalancing.*;
import de.tum.ftm.agentsim.ts.utils.Position;
import de.tum.ftm.agentsim.ts.utils.SimTime;
import de.tum.ftm.agentsim.ts.utils.UtilSQLiteConnection;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.pmw.tinylog.Logger;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


/**
 * Class to control all objects in the simulation
 *
 * @author Manfred Klöppel
 */
public class SimObjectController {

    private HashMap<Long, Vehicle> fleet;                   // Vehicle agents
    private HashMap<Long, User> users;                      // User agents
    private HashMap<Long, User.TripRequest> requests;     // Separate list for all requests of all users
    private RebalancingManagerInterface relocationManager;

    // Time when the position of an agent was updated. Here set to 0 for initialisation
    private SimTime locationTimeStamp = new SimTime(0);

    public SimObjectController(Scenario scenario, DBLog dbLog) {
        fleet = new HashMap<>();
        users = new HashMap<>();
        requests = new HashMap<>();

        SimObject.scenario = scenario;
        SimObject.dbLog = dbLog;
    }

    /**
     * Creating the vehicles of the fleet by loading the information from CSV
     *
     * @param filepath Path to CSV containing vehicle information
     */
    public void loadVehiclesFromCSV(String filepath) {
        Reader in;
        try {
            in = new FileReader(filepath);
            Iterable<CSVRecord> records = CSVFormat.EXCEL.withDelimiter(',').withHeader().parse(in);

            for (CSVRecord record : records) {
                long vehicleID = Long.parseLong(record.get("vehicle_id"));

                fleet.put(vehicleID, new Vehicle(
                        vehicleID,
                        new Position(Double.parseDouble(record.get("lon")), Double.parseDouble(record.get("lat"))),
                        Integer.parseInt(record.get("capacity")),
                        Double.parseDouble(record.get("kwh_per_100km")),
                        Double.parseDouble(record.get("kwh_per_100km_per_pax"))
                ));
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The input database containing all requests needs to be processed before it can be used for simulation.
     * The preparation calculates the direct driving durationMIN from origin to destination for each request
     * and stores the route information to the DB.
     * The arrival timestamp is corrected according to the driving durationMIN. Depending on the configuration, only
     * DB-entries are updated, which have no route-information yet.
     * @param forceDataUpdate If true, all db-entries are updated, even if route/durationMIN information is already
     *                        available.
     */
    public void prepareDBInputData(boolean forceDataUpdate) {
        String sqlForcedUpdate = forceDataUpdate ? "" : " WHERE route IS NULL";

        try {
            Connection conn = UtilSQLiteConnection.openConnection(Config.REQUESTS_INPUT_FILE, false);

            // Determine total number of requests which need to be processed
            Statement stmt = conn.createStatement();
            stmt.execute(String.format("SELECT load_extension('%s')", Config.SPATIALITE_PATH));
            String sql = "SELECT count(*) as cnt FROM requests" + sqlForcedUpdate;
            ResultSet rs = stmt.executeQuery(sql);

            if (rs.next()) {
                int entryCount = rs.getInt("cnt");

                if (entryCount > 0 || forceDataUpdate) {
                    Logger.info("{} entries need to be updated.", entryCount);

                    // Create index for faster update of table
                    stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_requests_booking_id ON requests (booking_id)");
                    conn.commit();

                    // Query requests from DB
                    sql = "SELECT * FROM requests" + sqlForcedUpdate;
                    rs = stmt.executeQuery(sql);

                    // Store all requests in an Arraylist as ImportRequest objects
                    List<ImportRequest> importRequestList = new ArrayList<>();
                    try (ProgressBar pb = new ProgressBar(String.format("%s INFO:\t ",
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))), entryCount, ProgressBarStyle.ASCII)) {
                        while (rs.next()) {
                            importRequestList.add(new ImportRequest(
                                    rs.getInt("booking_id"),
                                    new Position(rs.getDouble("o_lon"), rs.getDouble("o_lat")),
                                    new Position(rs.getDouble("d_lon"), rs.getDouble("d_lat"))
                            ));

                            // Update db-route info in batches of 100000 to avoid excessive memory consumption
                            if (importRequestList.size() == 100000) {
                                updateDBInfo(importRequestList, conn, pb);
                                importRequestList.clear();
                            }
                        }
                        // Update the db-route info for the remaining requests
                        updateDBInfo(importRequestList, conn, pb);
                    }

                    // Update the request durations for all requests
                    Logger.info("Updating durations...");
                    sql = "UPDATE requests SET d_time = datetime(o_time, '+' || CAST(duration_min AS text) || ' minutes')";
                    stmt.executeUpdate(sql);
                    conn.commit();
                } else {
                    Logger.info("No update of input data required.");
                }

                stmt.close();
            }
            UtilSQLiteConnection.closeConnection(conn);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Updates the details of the provided requests and updates the entries in the database
     *
     * @param importRequestList Requests which need to be updated
     * @param conn              Connection Object to the input database
     * @param pb                ProgressBar Object to update the progressbar
     * @throws SQLException     Thrown, if an error while updating the database occurs
     */
    private void updateDBInfo(List<ImportRequest> importRequestList, Connection conn, ProgressBar pb) throws SQLException {
        if (importRequestList.size() > 0) {
            // Calculate durations for requests in parallel stream
            var stream = importRequestList.parallelStream();
            stream.forEach((ImportRequest p) -> {
                p.setRequestDetails();
                pb.step();
            });

            // Write results to DB
            Logger.trace("Writing DB prepared statements for {} requests...", importRequestList.size());

            PreparedStatement pstmt = conn.prepareStatement("UPDATE requests SET duration_min = ?, dist_km = ?, route = ? WHERE booking_id = ?");

            for (ImportRequest r : importRequestList) {
                pstmt.setDouble(1, r.getDurationMIN());
                pstmt.setDouble(2, r.getDistanceKM());
                pstmt.setString(3, r.getRoute());
                pstmt.setInt(4, r.getId());
                pstmt.addBatch();
            }
            Logger.trace("Executing prepared statements...");
            pstmt.executeBatch();
            conn.commit();
            pstmt.close();
        }
    }

    /**
     * Loads User and ImportRequest Information from DB. Creates Users and assigns their Requests.
     * @param filepath Path to DB.
     */
    public void loadUsersWithRequestsFromDB(String filepath) {
        try {
            Connection inputDBConnection = UtilSQLiteConnection.openConnection(filepath, false);

            Statement stmt = inputDBConnection.createStatement();
            String sql = "SELECT count(*) AS cnt FROM requests";
            ResultSet rs = stmt.executeQuery(sql);
            int requestCount = rs.getInt("cnt");

            sql = "SELECT * FROM requests";
            rs = stmt.executeQuery(sql);

            Map<User.TripRequest, String> tempList = new HashMap<>();

            try (ProgressBar pb = new ProgressBar(String.format("%s INFO:\t ",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))), requestCount, ProgressBarStyle.ASCII)) {
                while (rs.next()) {
                    long userID = rs.getLong("person_id");

                    // Create new user, if it does not exist yet, else load it.
                    if (!users.containsKey(userID)) {
                        users.put(userID, new User(userID));
                    }
                    User user = users.get(userID);

                    User.TripRequest userRequest = new User.TripRequest(
                            user,
                            rs.getLong("booking_id"),
                            new SimTime(LocalDateTime.parse(rs.getString("o_time"), DateTimeFormatter.ofPattern("yyy-MM-dd HH:mm:ss"))),
                            new SimTime(LocalDateTime.parse(rs.getString("d_time"), DateTimeFormatter.ofPattern("yyy-MM-dd HH:mm:ss"))),
                            new Position(rs.getDouble("o_lon"), rs.getDouble("o_lat")),
                            new Position(rs.getDouble("d_lon"), rs.getDouble("d_lat")),
                            rs.getInt("additional_persons"),
                            rs.getDouble("dist_km"));

                    user.addRequest(userRequest);
                    requests.put(userRequest.getRequestID(), userRequest);
                    pb.step();
                }
            }

            stmt.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Iterates over the list of users and returns a list of all requests
     * @return list of all requests from all users
     */
    public PriorityQueue<Event> getRequestsForTaskList() {
        PriorityQueue<Event> requestPriorityQueue = new PriorityQueue<Event>();
        for (User user : this.users.values()) {
            for (User.TripRequest userRequest : user.getUserTripRequestsList()) {
                requestPriorityQueue.add(new Event_UserRequest(new SimTime(userRequest.getRequestStart()), userRequest));
            }
        }
        return requestPriorityQueue;
    }

    /**
     * Updates the position of all vehicles in fleet, if the age of the position is exceeded
     *
     * @param forcedUpdate If true, positions are updated even if age of position is not exceeded
     */
    public void updateFleetPositions(Boolean forcedUpdate) {
        if ((SimTime.now().getTimeMillis() > locationTimeStamp.getTimeMillis()+Config.MAX_POSITION_AGE_SECONDS*1000) || forcedUpdate) {
            fleet.values().parallelStream().forEach(Vehicle::updatePosition);
        }
    }

    /**
     * Logs the statistics of all vehicles to the DB
     */
    public void logVehicleStats() {
        for (Vehicle v : fleet.values()) {
            v.logIdleDuration();

            DBLog.dbTableVehicles.addLogEntry(new DBTableEntry.Builder()
                    .vehicleID(v.getId())
                    .servedRequests(v.getServedRequests())
                    .servedPassengers(v.getServedPassengers())
                    .drivingDistanceKM(v.getDrivingDistanceKM())
                    .drivingDurationMIN(v.getDrivingDurationMIN())
                    .energyConsumptionKwh(v.getEnergyConsumptionKwh())
                    .maxSimultaneousRequests(v.getMaxSimultaneousRequests())
                    .maxSimultaneousUsers(v.getMaxSimultaneousPassengers())
                    .durationBusyDrivingSumMS(v.getDurationBusyDrivingSumMS())
                    .durationBusyDwellingSumMS(v.getDurationBusyDwellingSumMS())
                    .durationIdleMaxMS(v.getDurationIdleMaxMS())
                    .durationIdleSumMS(v.getDurationIdleSumMS())
                    .durationRelocationSumMS(v.getDurationRelocationSumMS())
                    .build());
        }
    }

    /**
     * Sets up the relocationManager by loading the relocation-data and setting up the relocations-events in
     * the simulation event-queue
     */
    public void setupRelocationManager() {
        switch (Config.REBALANCING_MANAGER_TYPE) {
            case "R5":
                this.relocationManager = RebalancingManagerR5.getInstance();
                break;
            default:
                Logger.error("Rebalancing Strategy not recognized!");
                throw new RuntimeException("Rebalancing Strategy not recognized!");
        }
        Logger.info("Selected Rebalancing Strategy: {}", relocationManager.getRelocationManagerType());

        Event_RebalancingManager.mgr = relocationManager;

        // Setup relocation events
        relocationManager.setupRebalancingEvents();
    }

    public HashMap<Long, Vehicle> getFleet() {
        return fleet;
    }

    public HashMap<Long, User> getUsers() {
        return users;
    }

    public HashMap<Long, User.TripRequest> getRequests() {
        return requests;
    }

    public RebalancingManagerInterface getRelocationManager() {
        return relocationManager;
    }

    /**
     * Helper class to store the information of all requests for batch processing of routing information
     */
    private class ImportRequest {
        int id;
        Position origin;
        Position destination;
        double durationMIN;
        double distanceKM;
        String route;

        ImportRequest(int id, Position origin, Position destination) {
            this.id = id;
            this.origin = origin;
            this.destination = destination;
        }

        /**
         * Calculate the durationMIN and distanceKM for a request. The RouteStep-Type "VOID" sets
         * the Travel-Time-Factor to 1.
         */
        void setRequestDetails() {
            try {
                var rste = new RouteStepEnroute(origin, destination, RouteStep.StepType.VOID);

                durationMIN = ((double) rste.getDurationMS()) / 1000 / 60;   // minutes
                distanceKM = rste.getDistanceM() / 1000;                    // kilometers

                if (Config.STORE_ROUTE_WKT_IN_INPUT_DATA) {
                    route = rste.getWKTFromRouteStep().toString();
                } else {
                    route = "RTE OK";
                }

            } catch (Exception e) {
                Logger.error("ImportRequest {} Route Error", id);
                Logger.error(e);
            }
        }

        public int getId() {
            return id;
        }

        public Position getOrigin() {
            return origin;
        }

        public Position getDestination() {
            return destination;
        }

        public double getDurationMIN() {
            return durationMIN;
        }

        public double getDistanceKM() {
            return distanceKM;
        }

        public String getRoute() {
            return route;
        }
    }
}
