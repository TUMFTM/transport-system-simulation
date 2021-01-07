package de.tum.ftm.agentsim.ts.assignmentStrategy.shortestRouteAssignment;

import de.tum.ftm.agentsim.ts.Scenario;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * Delegation-Pattern: This class returns a parallel stream of the requests for parallel processing
 *
 * @author Manfred Klöppel, Julian Erhard
 */
public class ParallelSRA extends ShortestRouteAssignment {

    public ParallelSRA(Scenario scenario) {
        super(scenario);
    }

    /**
     * Create a parallel stream.
     */
    @Override
    public <T> Stream<T> createStream(Collection<T> collection) {
        return collection.parallelStream();
    }

    @Override
    public String getStrategyType() {
        return "Parallel Shortest Route Assignment";
    }
}
