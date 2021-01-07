package de.tum.ftm.agentsim.ts.utils;

import com.graphhopper.util.shapes.GHPoint;
import org.locationtech.jts.geom.Point;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Position object with helper functions and Geometry-Implementation for geometry-functions
 *
 * @author Manfred Klöppel, Julian Erhard, Alexander Schulz
 */
public class Position implements Cloneable, Serializable {
	private double x;					// Longitude
	private double y;					// Latitude
	private Integer heading = null;		// optional Heading
	private Point point;				// Geometry representation of Position

	private static final double EARTH_RADIUS_KM = 6372.8; // km

	public Position(double x_lon, double y_lat) {
		this.x = x_lon;
		this.y = y_lat;
        this.point = UtilGeometry.makePoint(x_lon, y_lat);
	}

	public Position(double x_lon, double y_lat, int heading) {
		this.x = x_lon;
		this.y = y_lat;
		this.heading = heading;
		this.point = UtilGeometry.makePoint(x_lon, y_lat);
	}

	public Position(GHPoint point) {
		this.x = point.getLon();
		this.y = point.getLat();
	}


	/**
	 * Calculates the Haversine-distance between two positions/locations. Distance is returned in kilometers
	 * @param other The position to which the distance should be calculated to
	 * @return Distance between this and the other position in kilometers
	 */
	public double haversineDistance(Position other) {
		var deltaLat = Math.toRadians(this.y - other.y);
		var deltaLong = Math.toRadians(this.x - other.x);

		var thisLat = Math.toRadians(this.y);
		var otherLat = Math.toRadians(other.y);

		var a = Math.pow(Math.sin(deltaLat / 2), 2)
				+ Math.cos(thisLat) * Math.cos(otherLat) * Math.pow(Math.sin(deltaLong / 2), 2);
		var c = 2 * Math.asin(Math.sqrt(a));

		return EARTH_RADIUS_KM * c;
	}

	/**
	 * Calculates the heading towards a second position
	 * @param other The position towards which the heading should be calculated
	 * @return Heading in degrees
	 */
	public double headingToPosition(Position other) {
	    var lat1 = Math.toRadians(this.getLat());
	    var lon1 = Math.toRadians(this.getLon());
	    var lat2 = Math.toRadians(other.getLat());
	    var lon2 = Math.toRadians(other.getLon());

        var y = Math.sin(lon2-lon1) * Math.cos(lat2);
        var x = Math.cos(lat1)*Math.sin(lat2) -
                Math.sin(lat1)*Math.cos(lat2)*Math.cos(lon2-lon1);

        var heading = Math.toDegrees(Math.atan2(y, x));
        return heading < 0 ? heading+360 : heading;
    }

	/**
	 * @return A copy of the original position
	 */
	public Position copyPosition() {
		if (heading != null) {
			return new Position(x, y, heading);
		} else {
			return new Position(x, y);
		}
	}

	public double getLon() {
		return getX();
	}
	public void setLon(double x) {
		setX(x);
	}
	public double getX() {
		return x;
	}
	public void setX(double x) {
		this.x = x;
		this.point = UtilGeometry.makePoint(x, this.y);
	}

	public double getLat() {
	    return getY();
    }
	public void setLat(double y) {
		setY(y);
	}
	public double getY() {
		return y;
	}
	public void setY(double y) {
		this.y = y;
        this.point = UtilGeometry.makePoint(this.x, y);
    }
	public Integer getHeading() {
		if (heading == null) return null;
		else return heading;
	}
	public void setHeading(int heading) {
		this.heading = heading;
	}
	public void setHeadingToPosition(Position otherPos) {
		this.heading = (int) Math.round(headingToPosition(otherPos));
	}

	/**
	 * @return Point-Geometry for geometric functions (within, buffer etc.)
	 */
    public Point geoFunc() {
        return point;
    }

    @Override
	public String toString() {
		return "POINT(" + this.getX() + " " + this.getY() + ")";
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;

		Position other = (Position) obj;
		if (Double.doubleToLongBits(x) != Double.doubleToLongBits(other.x))
			return false;
		if (Double.doubleToLongBits(y) != Double.doubleToLongBits(other.y))
			return false;

		return true;
	}

	@Override
	public int hashCode() {
		return (int) (x*100000 + y*100000);
	}


	/**
	 * Write Position-Object to file
	 */
	private void writeObject(ObjectOutputStream s) throws IOException {
		s.writeDouble(x);
		s.writeDouble(y);
	}

	/**
	 * Read Position-Object from file
	 */
	private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
		x = s.readDouble();
		y = s.readDouble();
	}
}
