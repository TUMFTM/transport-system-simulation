package de.tum.ftm.agentsim.ts.routing;

import java.io.IOException;

/**
 * Exception for Routing Errors
 *
 * @author Julian Erhard, Manfred Klöppel
 */
public class RoutingException extends IOException {
    public RoutingException(String e) {
        super(e);
    }
}
