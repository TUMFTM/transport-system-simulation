package de.tum.ftm.agentsim.ts.log;

import org.pmw.tinylog.Logger;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * This database table contains the information of the status of simulation objects at specific times.
 * Entries are entered whenever the Event "LogSimObjectStatus" occurs.
 * @author Manfred Klöppel
 */
public class DBTableSimObjectStatus extends DBTable {

    DBTableSimObjectStatus(DBLog dbLog) {
        super(dbLog);
    }

    /**
     * Definition of the database-table
     */
    public void initializeTable() {
        try {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("DROP TABLE IF EXISTS log_simobject_status");
            stmt.executeUpdate("CREATE TABLE log_simobject_status ("
                    + "id INTEGER, "            // 1
                    + "object_type TEXT, "      // 2
                    + "time_ms INTEGER,"        // 3
                    + "time STRING,"            // 4
                    + "status TEXT, "           // 5
                    + "pax_count INTEGER, "     // 6
                    + "request_count INTEGER, " // 7
                    + "longitude REAL, "        // 8
                    + "latitude REAL)");        // 9

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
            PreparedStatement ps = connection.prepareStatement("INSERT INTO log_simobject_status VALUES (?,?,?,?,?,?,?,?,?)");
            for (DBTableEntry b : buffer) {
                ps.setLong(1, b.getObjectID());
                ps.setString(2, b.getObjectType());
                ps.setLong(3, b.getCurrentTime().getTimeMillis());
                ps.setString(4, b.getCurrentTime().toString());
                ps.setString(5, b.getStatus());
                ps.setInt(6, b.getCurrentPaxCount());
                ps.setInt(7, b.getCurrentRequestsCount());
                ps.setDouble(8, b.getCurrentPosition().getLon());
                ps.setDouble(9, b.getCurrentPosition().getLat());
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
        Logger.info("Postprocessing log_simobject_status: Calculating geometries...");
        try {
            Statement stmt = connection.createStatement();

            stmt.executeUpdate("SELECT AddGeometryColumn ('log_simobject_status', 'geom', 4326, 'POINT', 2)");
            stmt.executeUpdate("UPDATE log_simobject_status SET geom=MakePoint(longitude, latitude, 4326)");

            connection.commit();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
