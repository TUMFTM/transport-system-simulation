package de.tum.ftm.agentsim.ts.assignmentStrategy.closestVehicleAssignment;

import de.tum.ftm.agentsim.ts.Scenario;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * Delegation-Pattern: This class returns a sequential stream of the requests for sequential processing
 *
 * @author Manfred Klöppel, Julian Erhard
 */
public class SequentialCVA extends ClosestVehicleAssignment {

	public SequentialCVA(Scenario scenario) {
		super(scenario);
	}

	/**
	 * Create a sequential stream.
	 */
	@Override
	public <T> Stream<T> createStream(Collection<T> collection) {
		return collection.stream();
	}

	@Override
	public String getStrategyType() {
		return "Sequential Closest Vehicle Assignment";
	}
}
