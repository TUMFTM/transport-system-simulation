package de.tum.ftm.agentsim.ts.log;

import de.tum.ftm.agentsim.ts.Config;

import java.sql.Connection;
import java.util.ArrayList;


/**
 * Abstract definition of class to represent a database-table. For each database-table the information is stored
 * as DBTableEntry.
 * @author Manfred Klöppel
 */
public abstract class DBTable {

    ArrayList<DBTableEntry> buffer;
    int maxBufferSize;
    DBLog dbLog;
    Connection connection;

    DBTable(DBLog dbLog) {
        this.dbLog = dbLog;
        connection = dbLog.getConnection();
        maxBufferSize = Config.DB_BATCH_SIZE;
        buffer = new ArrayList<>(Config.DB_BATCH_SIZE);
    }

    /**
     * Logging entries are added with this function to the buffer. If the buffer reaches the maximum buffer size,
     * the buffer is written to the database
     * @param bufferEntry Item which is written to the buffer
     */
    public synchronized void addLogEntry(DBTableEntry bufferEntry) {
        if (buffer.size() == maxBufferSize) {
            writeBufferToDB();
        }
        buffer.add(bufferEntry);
    }

    /**
     * Function to define and setup a table
     */
    abstract public void initializeTable();

    /**
     * Function to write the buffer to the database
     */
    abstract public void writeBufferToDB();

    /**
     * Function, which can be called after logging is completed to conduct postprocessing.
     * If any postprocessing is required, this function need to be overwritten.
     */
    public void postprocessing() {};
}

