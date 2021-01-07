package de.tum.ftm.agentsim.ts.log;

import org.pmw.tinylog.Logger;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * This database table contains the information of all processed travel-requests.
 * Data is inserted whenever a travel-request is completed or if a assignment failed.
 * @author Manfred Klöppel
 */
public class DBTableTrips extends DBTable {

    DBTableTrips(DBLog dbLog) {
        super(dbLog);
    }

    /**
     * Definition of the database-table
     */
    public void initializeTable() {
        try {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("DROP TABLE IF EXISTS log_trips");
            stmt.executeUpdate("CREATE TABLE log_trips ("
                    + "request_id INTEGER, "                // 1
                    + "person_id INTEGER, "                 // 2
                    + "vehicle_id INTEGER, "                // 3
                    + "additional_pax INTEGER, "            // 4
                    + "orig_start_time TEXT, "              // 5
                    + "factored_stop_time TEXT, "           // 6
                    + "orig_start_lat REAL, "               // 7
                    + "orig_start_lon REAL, "               // 8
                    + "orig_stop_lat REAL, "                // 9
                    + "orig_stop_lon REAL, "                // 10
                    + "orig_distance_km REAL, "             // 11
                    + "factored_duration_min REAL, "        // 12
                    + "start_lat REAL, "                    // 13
                    + "start_lon REAL, "                    // 14
                    + "stop_lat REAL, "                     // 15
                    + "stop_lon REAL, "                     // 16
                    + "time_trip_assigned TEXT, "           // 17
                    + "time_trip_pickup_latest TEXT, "      // 18
                    + "time_trip_picked_up TEXT, "          // 19
                    + "time_trip_departure TEXT, "          // 20
                    + "time_trip_drop_off_latest TEXT, "    // 21
                    + "time_trip_drop_off TEXT, "           // 22
                    + "time_trip_completed TEXT, "          // 23
                    + "driving_distance REAL, "             // 24
                    + "driving_duration REAL, "             // 25
                    + "status TEXT, "                       // 26
                    + "trip_was_shared INTEGER, "           // 27
                    + "route_wkt TEXT)");                   // 28

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
            PreparedStatement ps = connection.prepareStatement("INSERT INTO log_trips VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            for (DBTableEntry b : buffer) {
                ps.setLong(1, b.getRequestID());
                ps.setLong(2, b.getPersonID());
                ps.setLong(3, b.getVehicleID());
                ps.setInt(4, b.getAdditionalPassengers());
                ps.setString(5, b.getOrigStartTime().toString());
                ps.setString(6, b.getOrigStopTime().toString());
                ps.setDouble(7, b.getOrigStartPosition().getLat());
                ps.setDouble(8, b.getOrigStartPosition().getLon());
                ps.setDouble(9, b.getOrigStopPosition().getLat());
                ps.setDouble(10, b.getOrigStopPosition().getLon());
                ps.setDouble(11, b.getOrigDistanceKM());
                ps.setDouble(12, b.getOrigDurationMIN());

                if (b.getStartPosition() == null || b.getStopPosition() == null) {
                    ps.setNull(13,java.sql.Types.NULL);
                    ps.setNull(14,java.sql.Types.NULL);
                    ps.setNull(15,java.sql.Types.NULL);
                    ps.setNull(16,java.sql.Types.NULL);
                } else {
                    ps.setDouble(13, b.getStartPosition().getLat());
                    ps.setDouble(14, b.getStartPosition().getLon());
                    ps.setDouble(15, b.getStopPosition().getLat());
                    ps.setDouble(16, b.getStopPosition().getLon());
                }
                ps.setString(17, b.getTimeTripAssigned());
                ps.setString(18, b.getTimeTripPickupLatest());
                ps.setString(19, b.getTimeTripPickedUp());
                ps.setString(20, b.getTimeTripDeparture());
                ps.setString(21, b.getTimeTripDropoffLatest());
                ps.setString(22, b.getTimeTripDroppedOff());
                ps.setString(23, b.getTimeTripCompleted());
                ps.setDouble(24, b.getDrivingDistanceKM());
                ps.setDouble(25, b.getDrivingDurationMIN());
                ps.setString(26, b.getStatus());
                ps.setBoolean(27, b.getBookingWasShared());
                ps.setString(28, b.getGeomWKT());
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
        calculateGeometry();
    }

    /**
     * Calculate SpatiaLite Geometry for coordinates for visualisation in GIS-applications
     */
    private void calculateGeometry() {
        try {
            Statement stmt = connection.createStatement();

            Logger.info("Postprocessing log_trips: Creating geometry columns...");
            stmt.executeUpdate("SELECT AddGeometryColumn ('log_trips', 'orig_start_geom', 4326, 'POINT', 2)");
            stmt.executeUpdate("SELECT AddGeometryColumn ('log_trips', 'orig_stop_geom', 4326, 'POINT', 2)");
            stmt.executeUpdate("SELECT AddGeometryColumn ('log_trips', 'start_geom', 4326, 'POINT', 2)");
            stmt.executeUpdate("SELECT AddGeometryColumn ('log_trips', 'stop_geom', 4326, 'POINT', 2)");
            stmt.executeUpdate("SELECT AddGeometryColumn ('log_trips', 'route_geom', 4326, 'MULTILINESTRING', 2)");

            String sql = "UPDATE log_trips SET " +
                    "orig_start_geom=MakePoint(orig_start_lon, orig_start_lat, 4326)," +
                    "orig_stop_geom=MakePoint(orig_stop_lon, orig_stop_lat, 4326)," +
                    "start_geom=MakePoint(start_lon, start_lat, 4326)," +
                    "stop_geom=MakePoint(stop_lon, stop_lat, 4326)";

            Logger.info("Postprocessing log_trips: Calculating geometry for start/stop locations...");
            stmt.executeUpdate(sql);
            Logger.info("Postprocessing log_trips: Calculating route geometries...");
            stmt.executeUpdate("UPDATE log_trips SET route_geom=MLineFromText(route_wkt, 4326) WHERE status = 'COMPLETED'");

            connection.commit();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
