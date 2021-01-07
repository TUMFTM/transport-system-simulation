package de.tum.ftm.agentsim.ts.simobjects.rebalancing;

import de.tum.ftm.agentsim.ts.utils.SimTime;

import java.util.HashMap;

/**
 * Class to store the distribution data for all districts for a given time
 */
class RelocationTimeData {
    SimTime time;
    int amtRequests;
    HashMap<Integer, Double> districtShares = new HashMap<>();

    public RelocationTimeData(SimTime time, int amtRequests) {
        this.time = time;
        this.amtRequests = amtRequests;
    }

    public HashMap<Integer, Double> getDistrictShares() {
        return districtShares;
    }

    public int getAmtRequests() {
        return amtRequests;
    }
}
