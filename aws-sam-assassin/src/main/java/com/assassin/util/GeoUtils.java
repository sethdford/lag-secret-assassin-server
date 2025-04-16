package com.assassin.util;

import java.util.List;

import com.assassin.model.Coordinate;

/**
 * Utility class for geographical calculations.
 * Provides methods for point-in-polygon checking, distance calculations, and other geo operations.
 */
public class GeoUtils {

    // Earth's radius in meters
    private static final double EARTH_RADIUS_METERS = 6371000; 
    
    // Constants for coordinate validation
    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    /**
     * Checks if a given point is inside a polygon using the Ray Casting algorithm.
     * 
     * @param point The point to check
     * @param polygon A list of coordinates defining the polygon boundary
     * @return true if the point is inside the polygon, false otherwise
     */
    public static boolean isPointInBoundary(Coordinate point, List<Coordinate> polygon) {
        if (point == null || polygon == null || polygon.size() < 3) {
            return false; // Invalid input
        }

        boolean inside = false;
        int polygonSize = polygon.size();

        double x = point.getLatitude();
        double y = point.getLongitude();

        for (int i = 0, j = polygonSize - 1; i < polygonSize; j = i++) {
            Coordinate vertexI = polygon.get(i);
            Coordinate vertexJ = polygon.get(j);

            double xi = vertexI.getLatitude();
            double yi = vertexI.getLongitude();
            double xj = vertexJ.getLatitude();
            double yj = vertexJ.getLongitude();

            boolean intersect = ((yi > y) != (yj > y)) && 
                                (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
            if (intersect) {
                inside = !inside;
            }
        }

        return inside;
    }

    /**
     * Calculates the great-circle distance between two coordinates using the Haversine formula.
     * 
     * @param coord1 The first coordinate
     * @param coord2 The second coordinate
     * @return The distance in meters
     */
    public static double calculateDistance(Coordinate coord1, Coordinate coord2) {
        if (coord1 == null || coord2 == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }
        
        return calculateDistance(
            coord1.getLatitude(), coord1.getLongitude(),
            coord2.getLatitude(), coord2.getLongitude()
        );
    }
    
    /**
     * Calculates the great-circle distance between two points using the Haversine formula.
     * 
     * @param lat1 Latitude of point 1 in degrees
     * @param lon1 Longitude of point 1 in degrees
     * @param lat2 Latitude of point 2 in degrees
     * @param lon2 Longitude of point 2 in degrees
     * @return The distance in meters
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Convert degrees to radians
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        // Haversine formula
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }
    
    /**
     * Validates if the given coordinates are within valid ranges.
     * 
     * @param latitude Latitude in degrees
     * @param longitude Longitude in degrees
     * @return true if coordinates are valid, false otherwise
     */
    public static boolean isValidCoordinate(double latitude, double longitude) {
        return latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE &&
               longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }
    
    /**
     * Calculates the bearing (azimuth) between two points in degrees.
     * Returns a value between 0 and 360, where 0 is North, 90 is East, etc.
     * 
     * @param lat1 Latitude of point 1 in degrees
     * @param lon1 Longitude of point 1 in degrees
     * @param lat2 Latitude of point 2 in degrees
     * @param lon2 Longitude of point 2 in degrees
     * @return The bearing in degrees from 0 to 360
     */
    public static double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        // Convert to radians
        lat1 = Math.toRadians(lat1);
        lon1 = Math.toRadians(lon1);
        lat2 = Math.toRadians(lat2);
        lon2 = Math.toRadians(lon2);
        
        // Calculate bearing
        double y = Math.sin(lon2 - lon1) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - 
                   Math.sin(lat1) * Math.cos(lat2) * Math.cos(lon2 - lon1);
        
        double bearing = Math.atan2(y, x);
        
        // Convert to degrees and normalize to 0-360
        bearing = Math.toDegrees(bearing);
        bearing = (bearing + 360) % 360;
        
        return bearing;
    }
    
    /**
     * Returns a human-readable direction from a bearing.
     * 
     * @param bearing Bearing in degrees (0-360)
     * @return Cardinal or intercardinal direction (N, NE, E, etc.)
     */
    public static String getBearingAsDirection(double bearing) {
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW", "N"};
        return directions[(int)Math.round(bearing / 45)];
    }
    
    /**
     * Calculates a destination point given a starting point, bearing and distance.
     * 
     * @param startLat Starting latitude in degrees
     * @param startLon Starting longitude in degrees
     * @param bearing Bearing in degrees (0 = North, 90 = East, etc.)
     * @param distanceMeters Distance in meters
     * @return A new Coordinate representing the destination point
     */
    public static Coordinate calculateDestinationPoint(
            double startLat, double startLon, double bearing, double distanceMeters) {
        
        // Convert to radians
        double lat1 = Math.toRadians(startLat);
        double lon1 = Math.toRadians(startLon);
        double brng = Math.toRadians(bearing);
        double d = distanceMeters / EARTH_RADIUS_METERS;
        
        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(d) + 
                      Math.cos(lat1) * Math.sin(d) * Math.cos(brng));
        double lon2 = lon1 + Math.atan2(Math.sin(brng) * Math.sin(d) * Math.cos(lat1),
                      Math.cos(d) - Math.sin(lat1) * Math.sin(lat2));
        
        // Normalize longitude to -180 to +180
        lon2 = ((lon2 + 3 * Math.PI) % (2 * Math.PI)) - Math.PI;
        
        return new Coordinate(Math.toDegrees(lat2), Math.toDegrees(lon2));
    }
    
    /**
     * Calculates a bounding box (min/max lat/lon) for a circle around a point.
     * Useful for efficiently filtering points before precise distance calculations.
     * 
     * @param centerLat Center latitude in degrees
     * @param centerLon Center longitude in degrees
     * @param radiusMeters Radius in meters
     * @return An array of [minLat, minLon, maxLat, maxLon] defining the bounding box
     */
    public static double[] calculateBoundingBox(double centerLat, double centerLon, double radiusMeters) {
        // Angular distance in radians on a great circle
        double radDist = radiusMeters / EARTH_RADIUS_METERS;
        
        double radLat = Math.toRadians(centerLat);
        double radLon = Math.toRadians(centerLon);
        
        double minLat = radLat - radDist;
        double maxLat = radLat + radDist;
        
        // Compensate for degrees longitude getting smaller with increasing latitude
        double deltaLon;
        if (minLat > -Math.PI/2 && maxLat < Math.PI/2) {
            double minLatCos = Math.cos(minLat);
            double maxLatCos = Math.cos(maxLat);
            deltaLon = radDist / Math.min(minLatCos, maxLatCos);
        } else {
            // If the radius extends to a pole, the bounding box must include all longitudes
            minLat = Math.max(minLat, -Math.PI/2);
            maxLat = Math.min(maxLat, Math.PI/2);
            deltaLon = Math.PI; // 180 degrees
        }
        
        double minLon = radLon - deltaLon;
        double maxLon = radLon + deltaLon;
        
        // Convert back to degrees and normalize
        minLat = Math.toDegrees(minLat);
        minLon = Math.toDegrees(minLon);
        maxLat = Math.toDegrees(maxLat);
        maxLon = Math.toDegrees(maxLon);
        
        // Normalize longitude to -180 to 180
        if (minLon < -180) minLon += 360;
        if (maxLon > 180) maxLon -= 360;
        
        return new double[] {minLat, minLon, maxLat, maxLon};
    }

    /**
     * Calculates the distance from a point to a line segment in meters.
     * 
     * @param pointLat Latitude of the point
     * @param pointLon Longitude of the point
     * @param lineLat1 Latitude of the first endpoint of the line segment
     * @param lineLon1 Longitude of the first endpoint of the line segment
     * @param lineLat2 Latitude of the second endpoint of the line segment
     * @param lineLon2 Longitude of the second endpoint of the line segment
     * @return Distance in meters from the point to the closest point on the line segment
     */
    public static double distanceToLineSegment(double pointLat, double pointLon, 
                                        double lineLat1, double lineLon1, 
                                        double lineLat2, double lineLon2) {
        // Convert to radians for calculations
        double pLat = Math.toRadians(pointLat);
        double pLon = Math.toRadians(pointLon);
        double l1Lat = Math.toRadians(lineLat1);
        double l1Lon = Math.toRadians(lineLon1);
        double l2Lat = Math.toRadians(lineLat2);
        double l2Lon = Math.toRadians(lineLon2);
        
        // Validate inputs
        if (l1Lat == l2Lat && l1Lon == l2Lon) {
            // Line segment is actually a point, so just calculate distance to that point
            return calculateDistanceRadians(pLat, pLon, l1Lat, l1Lon);
        }
        
        // Step 1: Find the closest point on the infinite line
        // Calculate direction vector of the line
        double dLon = l2Lon - l1Lon;
        double dLat = l2Lat - l1Lat;
        
        // Calculate the squared length of the line segment
        double lineSegmentLengthSquared = dLon * dLon + dLat * dLat;
        
        // Calculate projection scalar parameter t (0 <= t <= 1 for points on the segment)
        double t = ((pLon - l1Lon) * dLon + (pLat - l1Lat) * dLat) / lineSegmentLengthSquared;
        
        // Clamp t to the line segment bounds
        t = Math.max(0, Math.min(1, t));
        
        // Calculate the closest point on the line segment
        double closestLat = l1Lat + t * dLat;
        double closestLon = l1Lon + t * dLon;
        
        // Step 2: Calculate distance from point to closest point on segment
        return calculateDistanceRadians(pLat, pLon, closestLat, closestLon);
    }

    /**
     * Helper method that calculates the distance between two points given in radians.
     * Uses the Haversine formula.
     * 
     * @param lat1 Latitude of point 1 in radians
     * @param lon1 Longitude of point 1 in radians
     * @param lat2 Latitude of point 2 in radians
     * @param lon2 Longitude of point 2 in radians
     * @return Distance in meters
     */
    private static double calculateDistanceRadians(double lat1, double lon1, double lat2, double lon2) {
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + 
                   Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Calculates the geometric centroid of a polygon.
     * For simple polygons, this often approximates the visual center.
     * Note: This is a simplified calculation (average of vertices) and may not be
     * accurate for complex, self-intersecting, or geographically large polygons
     * where Earth's curvature is significant.
     *
     * @param polygon A list of coordinates defining the polygon boundary (at least 3 points).
     * @return The calculated centroid Coordinate, or null if input is invalid.
     */
    public static Coordinate calculateCentroid(List<Coordinate> polygon) {
        if (polygon == null || polygon.isEmpty()) {
            return null;
        }

        double sumLat = 0.0;
        double sumLon = 0.0;
        int n = polygon.size();

        // If the last point is the same as the first, ignore it for centroid calculation
        if (n > 1 && polygon.get(0).equals(polygon.get(n - 1))) {
            n--;
        }

        if (n < 1) { // Need at least one unique point
             return null;
        }
        
        for (int i = 0; i < n; i++) {
            sumLat += polygon.get(i).getLatitude();
            sumLon += polygon.get(i).getLongitude();
        }

        return new Coordinate(sumLat / n, sumLon / n);
    }
} 