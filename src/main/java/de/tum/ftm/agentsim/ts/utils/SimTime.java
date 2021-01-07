package de.tum.ftm.agentsim.ts.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Simulation Time Object, which represents a point in time. Time is stores in Milliseconds or as LocalDateTime
 * for improved human readability. Provides functions to compare to timestamps with each other.
 *
 * The current time of the simulation is stored as a static field.
 *
 * @author Manfred Klöppel
 */
public class SimTime implements Comparable<SimTime> {

    // The current time of the simulation
    private static SimTime simulationTime = new SimTime(0);
    private static SimTime simulationStart = new SimTime(0);

    private long timeMillis;
    private LocalDateTime time;

    // --- SimTime CONSTRUCTORS ---
    /**
     * Create new SimTime object by providing a LocalDateTime
     */
    public SimTime(LocalDateTime time) {
        setTime(time);
    }

    /**
     * Create new SimTime object by providing another SimTime Object
     */
    public SimTime(SimTime time) {
        setTime(time.getTimeMillis());
    }

    /**
     * Create new SimTime object by providing time in Milliseconds
     */
    public SimTime(long timeMillis) {
        setTime(timeMillis);
    }

    /**
     * Creates a new SimTime Object by taking an existing SimTime Object and adding a duration in Milliseconds
     *
     * @param time An existing SimTime Object
     * @param durationInMillis The duration in Milliseconds, which should be added to time
     */
    public SimTime(SimTime time, long durationInMillis) {
        setTime(time.getTimeMillis()+durationInMillis);
    }
    // --- End of SimTime CONSTRUCTORS ---


    /**
     * @return Get the current simulation time
     */
    public static SimTime now() {
        return simulationTime;
    }

    /**
     * Updates the overall time of the simulation. Simulation time can move forward only!
     * Make sure SimTime.initialize was called first!
     *
     * @param newSimulationTime The new time, the simulation time is updated to
     */
    public static void updateSimulationTime(SimTime newSimulationTime) {
        assert newSimulationTime.getTimeMillis() >= now().getTimeMillis() : "Simulation time can only move forward!";
        SimTime.simulationTime = newSimulationTime;
    }

    /**
     * Compares if a SimTime-Object is greater/later than another SimTime-Object
     * @param other SimTime-Object to compare with
     * @return true, if this SimTime object is greater/later than the other SimTime-Object, else returns false
     */
    public boolean isGreaterThan(SimTime other) {
        return this.compareTo(other) > 0;
    }

    /**
     * Compares if a SimTime-Object is greater/later or equal to another SimTime-Object
     * @param other SimTime-Object to compare with
     * @return true, if this SimTime object is greater/later or equal to the other SimTime-Object, else returns false
     */
    public boolean isGreaterOrEqualThan(SimTime other) {
        return this.compareTo(other) >= 0;
    }

    /**
     * Compares if a SimTime-Object is smaller/earlier than another SimTime-Object
     * @param other SimTime-Object to compare with
     * @return true, if this SimTime object is smaller/earlier than the other SimTime-Object, else returns false
     */
    public boolean isLessThan(SimTime other) {
        return this.compareTo(other) < 0;
    }

    /**
     * Compares if a SimTime-Object is smaller/earlier or equal to another SimTime-Object
     * @param other SimTime-Object to compare with
     * @return true, if this SimTime object is smaller/earlier or equal to the other SimTime-Object, else returns false
     */
    public boolean isLessOrEqualThan(SimTime other) {
        return this.compareTo(other) <= 0;
    }

    public void setTime(long timeMillis) {
        this.timeMillis = timeMillis;
        this.time = Instant.ofEpochMilli(timeMillis).atZone(ZoneOffset.ofHours(0)).toLocalDateTime();
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
        this.timeMillis = time.toInstant(ZoneOffset.ofHours(0)).toEpochMilli();
    }

    public static SimTime getSimulationStart() {
        return simulationStart;
    }

    public static void setSimulationStart(SimTime simulationStart) {
        SimTime.simulationStart = simulationStart;
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public LocalDateTime getTime() {
        return time;
    }

    @Override
    public int compareTo(SimTime o) {
        return Long.compare(this.timeMillis, o.timeMillis);
    }

    @Override
    public String toString() {
        return time.toString();
    }
}
