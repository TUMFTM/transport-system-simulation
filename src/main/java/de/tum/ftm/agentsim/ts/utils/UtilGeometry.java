package de.tum.ftm.agentsim.ts.utils;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.WKTReader;

/**
 * Helper class to create geometry-objects. Includes a method to create a WKT-String (well-known-text)
 * representation of a geometry
 *
 * @author Manfred Klöppel
 */
public class UtilGeometry {

    private static GeometryFactory fact = new GeometryFactory();
    private static WKTReader wktRdr = new WKTReader(fact);

    public static Point makePoint(double x, double y) {
        return fact.createPoint(new Coordinate(x,y));
    }

    public static Geometry makeGeometryFromWKT(String wkt) {
        try {
            return wktRdr.read(wkt);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
