package de.tum.ftm.agentsim.ts.log;

import org.pmw.tinylog.Logger;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * This database table contains the information of all routes done by the agents during the simulation.
 * Entries are added whenever the Event "LogSimObjectRouteHistory" occurs.
 * @author Manfred Klöppel
 */
public class DBTableRoutes extends DBTable {

    public DBTableRoutes(DBLog dbLog) {
        super(dbLog);
    }

    /**
     * Definition of the database-table
     */
    @Override
    public void initializeTable() {
        try {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("DROP TABLE IF EXISTS log_routes");
            stmt.executeUpdate("CREATE TABLE log_routes ("
                    + "route_id INTEGER, "      // 1
                    + "object_type TEXT, "      // 2
                    + "object_id INTEGER, "     // 3
                    + "time_begin TEXT, "       // 4
                    + "time_end TEXT, "         // 5
                    + "step_type TEXT, "        // 6
                    + "pax_count INTEGER, "     // 7
                    + "request_count INTEGER, " // 8
                    + "duration_min REAL, "     // 9
                    + "distance_km REAL, "      // 10
                    + "geom_wkt TEXT)");        // 11

            connection.commit();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Implementation of the function to write the buffer to the database
     */
    @Override
    public void writeBufferToDB() {
        try {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO log_routes VALUES (?,?,?,?,?,?,?,?,?,?,?)");
            for (DBTableEntry b : buffer) {
                ps.setInt(1, b.getRouteID());
                ps.setString(2, b.getObjectType());
                ps.setLong(3, b.getObjectID());
                ps.setString(4, b.getOrigStartTime().toString());
                ps.setString(5, b.getOrigStopTime().toString());
                ps.setString(6, b.getStepType());
                ps.setInt(7, b.getCurrentPaxCount());
                ps.setInt(8, b.getCurrentRequestsCount());
                ps.setDouble(9, b.getDrivingDurationMIN());
                ps.setDouble(10, b.getDrivingDistanceKM());
                ps.setString(11, b.getGeomWKT());
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
        Logger.info("Postprocessing log_routes: Calculating geometries...");

        try {
            Statement stmt = connection.createStatement();

            stmt.executeUpdate("SELECT AddGeometryColumn ('log_routes', 'geom_point', 4326, 'POINT', 2)");
            stmt.executeUpdate("UPDATE log_routes SET geom_point=PointFromText(geom_wkt, 4326) WHERE step_type NOT LIKE 'ENROUTE%'");
            stmt.executeUpdate("SELECT AddGeometryColumn ('log_routes', 'geom_line', 4326, 'LINESTRING', 2)");
            stmt.executeUpdate("UPDATE log_routes SET geom_line=LineStringFromText(geom_wkt, 4326) WHERE step_type LIKE 'ENROUTE%'");

            connection.commit();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
