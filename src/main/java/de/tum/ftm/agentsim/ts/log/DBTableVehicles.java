package de.tum.ftm.agentsim.ts.log;

import de.tum.ftm.agentsim.ts.utils.UtilStringCompress;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;
import org.pmw.tinylog.Logger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This database table contains the summary information of all vehicle-agents.
 * The data is obtained after the simulation is finished.
 * @author Manfred Klöppel
 */
public class DBTableVehicles extends DBTable {

    DBTableVehicles(DBLog dbLog) {
        super(dbLog);
    }

    /**
     * Definition of the database-table
     */
    public void initializeTable() {
        try {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("DROP TABLE IF EXISTS log_stats_vehicle");
            stmt.executeUpdate("CREATE TABLE log_stats_vehicle ("
                    + "vehicle_id INTEGER, "            // 1
                    + "driving_distance_km REAL, "      // 2
                    + "driving_duration_min REAL, "     // 3
                    + "energy_consumption_kwh REAL, "   // 4
                    + "served_requests INTEGER, "       // 5
                    + "served_passengers INTEGER, "     // 6
                    + "max_simultaneous_req INTEGER, "  // 7
                    + "max_simultaneous_pax INTEGER, "  // 8
                    + "dur_idle_max_min REAL, "         // 9
                    + "dur_idle_sum_min REAL, "         // 10
                    + "dur_relocation_sum_min REAL, "   // 11
                    + "dur_busy_drive_sum_min REAL, "   // 12
                    + "dur_busy_dwell_sum_min REAL, "   // 13
                    + "route_wkt TEXT)");               // 14
            connection.commit();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Implementation of the function to write the buffer to the database
     */
    public void writeBufferToDB() {
        try {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO log_stats_vehicle VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            for (DBTableEntry b : buffer) {
                ps.setLong(1, b.getVehicleID());
                ps.setDouble(2, b.getDrivingDistanceKM());
                ps.setDouble(3, b.getDrivingDurationMIN());
                ps.setDouble(4, b.getEnergyConsumptionKwh());
                ps.setInt(5, b.getServedRequests());
                ps.setInt(6, b.getServedPassengers());
                ps.setInt(7, b.getMaxSimultaneousRequests());
                ps.setInt(8, b.getMaxSimultaneousUsers());
                ps.setDouble(9, (double) b.getDurationIdleMaxMS()/60000);
                ps.setDouble(10, (double) b.getDurationIdleSumMS()/60000);
                ps.setDouble(11, (double) b.getDurationRelocationSumMS()/60000);
                ps.setDouble(12, (double) b.getDurationBusyDrivingSumMS()/60000);
                ps.setDouble(13, (double) b.getDurationBusyDwellingSumMS()/60000);
                ps.setString(14, null);
                ps.addBatch();
            }
            ps.executeBatch();
            connection.commit();
            ps.close();
            buffer.clear();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Postprocessing database operation after all data was inserted into the database
     */
    @Override
    public void postprocessing() {
        Logger.info("Postprocessing log_stats_vehicle: Creating db indexes...");
        createTableIndexes();
        Logger.info("Postprocessing log_stats_vehicle: Calculating vehicle route history...");
        calculateRouteInfo();
    }

    /**
     * Create database-indexes for improved data-retrieval from database
     */
    private void createTableIndexes() {
        try {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_object_id ON log_routes (object_type, object_id, step_type)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_veh_id ON log_stats_vehicle (vehicle_id)");

            connection.commit();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * For every vehicle the driven route is summarized and stored to the database as a WKT-geometry
     * for analysis in GIS-applications
     */
    private void calculateRouteInfo() {
        try {
            // for every vehicle id load the routes segments and make route
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT DISTINCT object_id FROM log_routes WHERE object_type LIKE '%Vehicle'");

            // Create list with unique vehicle IDs
            ArrayList<Integer> vehicleIdList = new ArrayList<>();
            while (rs.next()) {
                vehicleIdList.add(rs.getInt("object_id"));
            }

            // Load all enroute routestep data
            // Key: Vehicle-ID, Value: Array of compresses routestrings
            Logger.info("Loading route-data from DB...");
            HashMap<Integer, List<byte[]>> routeData = new HashMap<>();
            rs = stmt.executeQuery("SELECT object_id, geom_wkt FROM log_routes WHERE object_type LIKE '%Vehicle' " +
                    "AND step_type LIKE 'ENROUTE%' ORDER BY object_id, time_begin ASC");
            List<byte[]> compressedWktList = new ArrayList<>();
            int currentVehicleID = 0;
            while (rs.next()) {
                int vehicleID = rs.getInt("object_id");
                if (vehicleID != currentVehicleID && compressedWktList.size()>0) {
                    routeData.put(currentVehicleID, new ArrayList<byte[]>(compressedWktList));
                    compressedWktList.clear();
                    currentVehicleID = vehicleID;
                } else {
                    currentVehicleID = vehicleID;
                    // compress the string to reduce memory requirement
                    byte[] wkt = UtilStringCompress.compress(rs.getString("geom_wkt"));
                    compressedWktList.add(wkt);
                }
            }
            if (compressedWktList.size() > 0) routeData.put(currentVehicleID, new ArrayList<byte[]>(compressedWktList));

            // insert information to db
            PreparedStatement ps = connection.prepareStatement("UPDATE log_stats_vehicle SET route_wkt = ? WHERE vehicle_id = ?");

            try (ProgressBar pb = new ProgressBar(String.format("%s INFO:\t ",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))), vehicleIdList.size(),
                    ProgressBarStyle.ASCII)) {

                for (HashMap.Entry<Integer, List<byte[]>> entry : routeData.entrySet()) {

                    StringBuilder wkt = createMultiLineStringWKTFromByteArray(entry.getValue());
                    ps.setString(1, wkt.toString());
                    ps.setInt(2, entry.getKey());

                    ps.addBatch();
                    pb.step();

                    // Commit every 500 entries to avoid out-of-memory error
                    if (pb.getCurrent() % 500 == 0 && pb.getCurrent() > 0) {
                        ps.executeBatch();
                        connection.commit();
                    }
                }
            } catch (Exception e) {
                Logger.error(e);
            }

            ps.executeBatch();
            connection.commit();

            ps.close();
            stmt.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a MULTILINESTRING-Geometry as WKT (well-known-text)
     * @param byteArray Array with compressed Geometry/WKT-information
     * @return MULTILINESTRING-Geometry as WKT-String
     * @throws Exception If error while decompression occurred
     */
    private StringBuilder createMultiLineStringWKTFromByteArray(List<byte[]> byteArray) throws Exception {
        StringBuilder wkt = new StringBuilder("MULTILINESTRING(");
        for (byte[] compressedWkt : byteArray) {
            String linestringWKT = UtilStringCompress.decompress(compressedWkt).replace("LINESTRING(", "");

            // Make sure, that linestring has at least two points (points are separated by ",")
            if (linestringWKT.contains(",")) {
                wkt.append(linestringWKT);
                wkt.append(", ");
            }
        }
        wkt.delete(wkt.length()-2, wkt.length());
        wkt.append(")");
        return wkt;
    }
}
