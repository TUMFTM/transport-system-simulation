package de.tum.ftm.agentsim.ts.utils;

import de.tum.ftm.agentsim.ts.Config;
import org.sqlite.SQLiteConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Helper class to connect to a SQLite-Database. Optionally Spatialite can be enabled by providing the
 * correct path info to the Spatialite-Extension
 *
 * @author Manfred Klöppel
 */
public class UtilSQLiteConnection {

    public static Connection openConnection(String filepath, boolean initializeSpatiaLite) {
        try {
            Class.forName("org.sqlite.JDBC");

            SQLiteConfig config = new SQLiteConfig();
            config.enableLoadExtension(true);

            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + filepath, config.toProperties());

            if (initializeSpatiaLite) {
                // Enabling SpatiaLite Spatial Metadata
                // This automatically initializes SPATIAL_REF_SYS and GEOMETRY_COLUMNS
                Statement stmt = conn.createStatement();
                stmt.execute(String.format("SELECT load_extension('%s')", Config.SPATIALITE_PATH));
                stmt.execute("SELECT InitSpatialMetadata(1)");
                stmt.close();
            }

            conn.setAutoCommit(false);
            return conn;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void closeConnection(Connection connection) {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
