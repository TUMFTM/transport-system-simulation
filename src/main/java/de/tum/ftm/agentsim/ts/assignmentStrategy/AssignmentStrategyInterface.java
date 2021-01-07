package de.tum.ftm.agentsim.ts.assignmentStrategy;

import de.tum.ftm.agentsim.ts.simobjects.User;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Interface to define common methods for classes which implement an assignment algorithm, which employs
 * a buffer to process multiple requests in batches.
 * Provides a default method to store assignment statistics.
 *
 * @author Manfred Klöppel, Julian Erhard
 */
public interface AssignmentStrategyInterface {

    /**
     * A new request was announced and needs to be processed by the assignment algorithm
     */
	void processNewRequest(User.TripRequest userRequest);

    /**
     * Process any buffer which holds multiple requests
     */
    void processRequestBuffer();

    /**
     * Identifies the selected assignment-algorithm
     */
	String getStrategyType();

    /**
     * Each assignment algorithm should return statistics about assignment calls
     */
    Map<String, Long> getAssignmentStatistics();

    /**
     * Helper class to store assignment statistics
     */
	class AssignmentStatistics {
        private AtomicLong jspritTotalCalls = new AtomicLong(0);
        private AtomicLong jspritValidCalls = new AtomicLong(0);
        private AtomicLong jspritValidRouteCalls = new AtomicLong(0);
        private AtomicLong directAssignmentTotalCalls = new AtomicLong(0);
        private AtomicLong directAssignmentValidRouteCalls = new AtomicLong(0);
        private AtomicInteger successfulAssignmentOnFirstTry = new AtomicInteger(0);
        private AtomicInteger successfulAssignmentOnSecondTry = new AtomicInteger(0);
        private AtomicInteger totalSecondTryAssignments = new AtomicInteger(0);

        public AtomicLong getJspritTotalCalls() {
            return jspritTotalCalls;
        }

        public void incJspritTotalCalls(long increment) {
            this.jspritTotalCalls.addAndGet(increment);
        }

        public AtomicLong getJspritValidCalls() {
            return jspritValidCalls;
        }

        public void incJspritValidCalls(long increment) {
            this.jspritValidCalls.addAndGet(increment);
        }

        public AtomicLong getJspritValidRouteCalls() {
            return jspritValidRouteCalls;
        }

        public void incJspritValidRouteCalls(long increment) {
            this.jspritValidRouteCalls.addAndGet(increment);
        }

        public AtomicLong getDirectAssignmentTotalCalls() {
            return directAssignmentTotalCalls;
        }

        public void incDirectAssignmentTotalCalls(long increment) {
            this.directAssignmentTotalCalls.addAndGet(increment);
        }

        public AtomicLong getDirectAssignmentValidRouteCalls() {
            return directAssignmentValidRouteCalls;
        }

        public void incDirectAssignmentValidRouteCalls(long increment) {
            this.directAssignmentValidRouteCalls.addAndGet(increment);
        }

        public AtomicInteger getSuccessfulAssignmentOnFirstTry() {
            return successfulAssignmentOnFirstTry;
        }

        public void incSuccessfulAssignmentOnFirstTry(int increment) {
            this.successfulAssignmentOnFirstTry.addAndGet(increment);
        }

        public AtomicInteger getSuccessfulAssignmentOnSecondTry() {
            return successfulAssignmentOnSecondTry;
        }

        public void incSuccessfulAssignmentOnSecondTry(int increment) {
            this.successfulAssignmentOnSecondTry.addAndGet(increment);
        }

        public AtomicInteger getTotalSecondTryAssignments() {
            return totalSecondTryAssignments;
        }

        public void incTotalSecondTryAssignments(int increment) {
            this.totalSecondTryAssignments.addAndGet(increment);
        }
    }
}
