package de.tum.ftm.agentsim.ts.log;

import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.utils.UtilUnitConvert;
import de.tum.ftm.agentsim.ts.utils.UtilStrings;
import de.tum.ftm.agentsim.ts.utils.UtilSQLiteConnection;
import org.pmw.tinylog.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;


/**
 * This class is used to log simulation results to a output SQLite Database.
 * Log-data will be pushed to the Database in batches. As long as the defined batch size is not reached the data remains in local memory.
 * Make sure to flush the DBLog when finishing the simulation.
 *
 * @author Manfred Klöppel, Michael Wittmann
 */
public class DBLog {

    String path;

    // SQLite Connection
    private Connection connection;
    private LocalDateTime logTime;
    private Long logTime_ms;

    // Database tables for logging
    public static DBTableTrips              dbTableTrips;
    public static DBTableVehicles           dbTableVehicles;
    public static DBTableSimObjectStatus    dbTableSimObjectStatus;
    public static DBTableRoutes             dbTableRoutes;

    // Array to hold log-tables
    private ArrayList<DBTable> allDBTables = new ArrayList<>();

    public DBLog(String folderPath, String dbName) {
        connectToDBFile(folderPath, dbName);
        writeConfigToDB();
        createDBTables();
    }

    /**
     * Creates/Connects to a given database-path and creates the folder if necessary. Existing files will be
     * overwritten.
     * The current date/time can be appended to the database-name if configured.
     * @param folderPath Folder path, where SQLite-database will be stored
     * @param dbName Name of the database-file
     */
    private void connectToDBFile(String folderPath, String dbName) {
        // Create Output directory, if it does not exists
        File logDir = new File(folderPath);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        // Create filename (either with or without datetime)
        if (Config.DB_NAME_APPEND_DATETIME) {
            path = String.format("%s%s_%s.db",
                    folderPath,
                    dbName,
                    UtilStrings.dateToStringFormatYYYYMMDD_HHMMSS(new Date()));
        } else {
            path = String.format("%s%s.db",
                    folderPath,
                    dbName
            );
        }

        // Delete file, if it already exits
        File file = new File(path);
        try {
            boolean result = Files.deleteIfExists(file.toPath());
        } catch (IOException i) {
            Logger.error(i);
        }

        connection = UtilSQLiteConnection.openConnection(path, true);
    }

    /**
     * Create and initialize database tables
     */
    void createDBTables() {
        dbTableTrips            = new DBTableTrips(this);
        dbTableVehicles         = new DBTableVehicles(this);
        dbTableSimObjectStatus  = new DBTableSimObjectStatus(this);
        dbTableRoutes           = new DBTableRoutes(this);

        allDBTables.add(dbTableRoutes);
        allDBTables.add(dbTableTrips);
        allDBTables.add(dbTableVehicles); // make sure, dbTableRoutes will always be processed before dbTableVehicles
        allDBTables.add(dbTableSimObjectStatus);

        for (DBTable t : allDBTables) {
            t.initializeTable();
        }
    }

    /**
     * Flush the buffer in local memory to the database
     */
    public void flushBuffers() {
        for (DBTable t : allDBTables) {
            t.writeBufferToDB();
        }
        Logger.trace("Flushed buffers to DB");
    }

    /**
     * Convenience function to both flush the buffer to the database and then do postprocessing
     */
    public void flushBuffersAndPostprocess() {
        flushBuffers();

        if (Config.ENABLE_POSTPROCESSING) {
            for (DBTable t : allDBTables) {
                t.postprocessing();
            }
        }
    }

    /**
     * Close the connection to the database
     */
    public void close() {
        UtilSQLiteConnection.closeConnection(connection);
    }

    /**
     * Writes all configuration-parameters to the database
     */
    void writeConfigToDB() {
        try {
            Statement stmt = connection.createStatement();

            stmt.executeUpdate("DROP TABLE IF EXISTS log_configuration");
            stmt.executeUpdate("CREATE TABLE log_configuration (Key TEXT, Value TEXT)");

            PreparedStatement ps = connection.prepareStatement("INSERT INTO log_configuration VALUES (?,?)");

            for (Map.Entry<String, String> pair : Config.configMap.entrySet()) {
                ps.setString(1, pair.getKey());
                ps.setString(2, pair.getValue());
                ps.executeUpdate();
            }

            connection.commit();
            ps.close();
            stmt.close();
            Logger.trace("Configuration written to DB.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Writes the duration of the simulation to the database
     * @param simulationDuration Duration of the simulation
     */
    public void writeSimTimeToConfigDB(String simulationDuration) {
        try {
            Statement stmt = connection.createStatement();
            stmt.execute(String.format("INSERT INTO log_configuration VALUES ('%s','%s')", "Simulation Duration", simulationDuration));

            connection.commit();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Writes information about call-counters to the database
     */
    public void writeCallCountersToDB(Map<String, Long> callCounterMap) {
        try {
            Statement stmt = connection.createStatement();

            stmt.executeUpdate("DROP TABLE IF EXISTS log_callcounters");
            stmt.executeUpdate("CREATE TABLE log_callcounters (counter_name TEXT, count INTEGER)");

            PreparedStatement ps = connection.prepareStatement("INSERT INTO log_callcounters VALUES (?,?)");
            for (Map.Entry<String, Long> pair : callCounterMap.entrySet()) {
                ps.setString(1, pair.getKey());
                ps.setLong(2, pair.getValue());
                ps.executeUpdate();
            }

            connection.commit();
            ps.close();
            stmt.close();
            Logger.trace("Call Counters written to DB.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        return connection;
    }
    public LocalDateTime getLogTime() {
        return logTime;
    }
    public void setLogTime(LocalDateTime logTime) {
        this.logTime = logTime;
        this.logTime_ms = UtilUnitConvert.LocalDateTimeToMillis(logTime);
    }
    public Long getLogTime_ms() {
        return logTime_ms;
    }
    public void setLogTime(Long logTime_ms) {
        this.logTime_ms = logTime_ms;
        this.logTime = UtilUnitConvert.MillisToLocalDateTime(logTime_ms);
    }
    public DBTableTrips getDbTableBookings() {
        return dbTableTrips;
    }
    public DBTableSimObjectStatus getDbTableSimObjectStatus() {
        return dbTableSimObjectStatus;
    }
    public DBTableVehicles getDbTableVehicles() {
        return dbTableVehicles;
    }
}
