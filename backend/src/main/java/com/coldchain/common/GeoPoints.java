package com.coldchain.common;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public final class GeoPoints {

    private static final int WGS84_SRID = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), WGS84_SRID);

    private GeoPoints() {
    }

    public static Point of(double lat, double lon) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
    }

    public static double lat(Point point) {
        return point.getY();
    }

    public static double lon(Point point) {
        return point.getX();
    }
}
