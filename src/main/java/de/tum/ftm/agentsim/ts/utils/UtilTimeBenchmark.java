package de.tum.ftm.agentsim.ts.utils;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Helper class to measure the execution time of specific code parts which are executed in loops.
 * Calculates min/max and avg/median execution time.
 *
 * Usage:
 * UtilTimeBenchmark timer = new UtilTimeBenchmark();
 * for (var i : elements) {
 *     timer.start();
 *     // Code which should be timed
 *     timer.stop();
 * }
 * timer.printStats();
 *
 * @author Manfred Klöppel
 */
public class UtilTimeBenchmark {

    private static double min = Double.MAX_VALUE;
    private static double max = 0;
    private static double avg;
    private static long noOfMeasurements = 0;
    private static double sumOfMeasurements = 0;

    private long t1;
    private long t2;
    private double duration; // milliseconds
    private static ArrayList<Double> durationList = new ArrayList<>();

    public UtilTimeBenchmark() {
    }

    /**
     * Start timer
     */
    public void start() {
        this.t1 = System.nanoTime();
    }

    /**
     * Stop timer
     */
    public void stop() {
        this.t2 = System.nanoTime();
        this.duration = ((t2 - t1) * 1e-6);

        if (duration < min) {
            min = duration;
        }
        if (duration > max) {
            max = duration;
        }
        durationList.add(duration);
        updateAvg(duration);
    }

    /**
     * Updates the average duration
     */
    private synchronized static void updateAvg(double duration) {
        noOfMeasurements++;
        sumOfMeasurements += duration;
        avg = sumOfMeasurements/noOfMeasurements;
    }

    public void printResult() {
        if (t2 > t1) {
            System.out.println(String.format("Duration: %.2f ms", duration));
        }
    }

    /**
     * Call this function after the loop is completed to print out the results
     */
    public static void printStats() {
        Collections.sort(durationList);
        double median;
        if (durationList.size() % 2 == 0)
            median = durationList.get(durationList.size()/2) + durationList.get(durationList.size()/2 - 1)/2;
        else
            median = durationList.get(durationList.size()/2);

        System.out.println(String.format("min: %.2f ms, max: %.2f ms, avg: %.2f ms, med: %.2f ms, no of measurments: %d", min, max, avg, median, noOfMeasurements));
    }
}
