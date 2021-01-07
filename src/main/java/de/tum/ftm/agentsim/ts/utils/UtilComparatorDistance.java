package de.tum.ftm.agentsim.ts.utils;

import de.tum.ftm.agentsim.ts.simobjects.SimObject;

import java.util.Comparator;

/**
 * Compares the distance of a single source Point to a list of possible target Points.
 *
 * @author Michael Wittmann, Manfred Klöppel
 */
public class UtilComparatorDistance implements Comparator<SimObject> {

    private Position pos;

    public UtilComparatorDistance(Position pos) {
        super();
        this.pos = pos;
    }

    @Override
    public int compare(SimObject o1, SimObject o2) {
        double dist1 = o1.getPosition().haversineDistance(pos);
        double dist2 = o2.getPosition().haversineDistance(pos);
        return Double.compare(dist1, dist2);
    }
}

