package de.tum.ftm.agentsim.ts.simobjects;

import de.tum.ftm.agentsim.ts.Scenario;
import de.tum.ftm.agentsim.ts.log.DBLog;
import de.tum.ftm.agentsim.ts.utils.Position;

/**
 * Base Element for all physical simulation objects/agents
 *
 * @author Manfred Klöppel, Michael Wittmann
 */

public abstract class SimObject {

    protected long id;                          // Object Id
    protected Position position;                // Elements Position
    public static Scenario scenario;            // Scenario the element is placed in
    public static DBLog dbLog;
    SimObjectStatus status;

    public SimObject(long id, Position position) {
        this.id = id;
        this.position = position;
    }

    public long getId() {
        return id;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public void setStatus(SimObjectStatus status) {
        this.status = status;
    }

    public SimObjectStatus getStatus() {
        return status;
    }

}
