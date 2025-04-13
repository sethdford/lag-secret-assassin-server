package com.assassin.util;

import com.assassin.model.Coordinate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GeoUtilsTest {

    // Simple rectangular boundary: (0,0), (10,0), (10,10), (0,10)
    private final List<Coordinate> rectangularBoundary = Arrays.asList(
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10)
    );

    // More complex concave boundary
    private final List<Coordinate> concaveBoundary = Arrays.asList(
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 5),
            new Coordinate(5, 5), // Concave point
            new Coordinate(5, 10),
            new Coordinate(0, 10)
    );

    @Test
    void testPointInsideRectangularBoundary() {
        Coordinate pointInside = new Coordinate(5, 5);
        assertTrue(GeoUtils.isPointInBoundary(pointInside, rectangularBoundary), "Point (5,5) should be inside the rectangle.");
    }

    @Test
    void testPointOutsideRectangularBoundary() {
        Coordinate pointOutside = new Coordinate(15, 5);
        assertFalse(GeoUtils.isPointInBoundary(pointOutside, rectangularBoundary), "Point (15,5) should be outside the rectangle.");

        Coordinate pointOutsideBelow = new Coordinate(5, -5);
        assertFalse(GeoUtils.isPointInBoundary(pointOutsideBelow, rectangularBoundary), "Point (5,-5) should be outside the rectangle.");
    }

    @Test
    void testPointOnRectangularBoundaryVertex() {
        Coordinate pointOnVertex = new Coordinate(0, 0);
        // Ray casting can be ambiguous for points exactly on the boundary.
        // Depending on implementation details, it might return true or false.
        // Typically, points on the boundary are considered outside for strict inclusion.
        // Let's assert false for consistency, but this might need adjustment based on desired behavior.
        assertFalse(GeoUtils.isPointInBoundary(pointOnVertex, rectangularBoundary), "Point (0,0) on vertex should ideally be considered outside (or consistently handled).");

        Coordinate pointOnVertex2 = new Coordinate(10, 10);
        assertFalse(GeoUtils.isPointInBoundary(pointOnVertex2, rectangularBoundary), "Point (10,10) on vertex should ideally be considered outside.");
    }

    @Test
    void testPointOnRectangularBoundaryEdge() {
        Coordinate pointOnEdge = new Coordinate(5, 0); // On bottom edge
        assertFalse(GeoUtils.isPointInBoundary(pointOnEdge, rectangularBoundary), "Point (5,0) on edge should ideally be considered outside.");

        Coordinate pointOnEdge2 = new Coordinate(10, 5); // On right edge
        assertFalse(GeoUtils.isPointInBoundary(pointOnEdge2, rectangularBoundary), "Point (10,5) on edge should ideally be considered outside.");

         Coordinate pointOnEdge3 = new Coordinate(0, 5); // On left edge
        assertFalse(GeoUtils.isPointInBoundary(pointOnEdge3, rectangularBoundary), "Point (0,5) on edge should ideally be considered outside.");
    }

    @Test
    void testPointInsideConcaveBoundary() {
        Coordinate pointInside = new Coordinate(2, 2);
        assertTrue(GeoUtils.isPointInBoundary(pointInside, concaveBoundary), "Point (2,2) should be inside the concave polygon.");

        Coordinate pointInsideUpperPart = new Coordinate(2, 8);
        assertTrue(GeoUtils.isPointInBoundary(pointInsideUpperPart, concaveBoundary), "Point (2,8) should be inside the concave polygon.");
    }

    @Test
    void testPointInConcavePartOutside() {
        Coordinate pointInConcaveArea = new Coordinate(7, 7);
        assertFalse(GeoUtils.isPointInBoundary(pointInConcaveArea, concaveBoundary), "Point (7,7) should be outside the concave polygon (in the concave part).");
    }

    @Test
    void testPointOutsideConcaveBoundary() {
        Coordinate pointOutside = new Coordinate(15, 5);
        assertFalse(GeoUtils.isPointInBoundary(pointOutside, concaveBoundary), "Point (15,5) should be outside the concave polygon.");
    }

     @Test
    void testPointOnConcaveBoundaryVertex() {
        Coordinate pointOnConcaveVertex = new Coordinate(5, 5);
        assertFalse(GeoUtils.isPointInBoundary(pointOnConcaveVertex, concaveBoundary), "Point (5,5) on concave vertex should ideally be considered outside.");
    }

    @Test
    void testNullPoint() {
        assertFalse(GeoUtils.isPointInBoundary(null, rectangularBoundary), "Null point should return false.");
    }

    @Test
    void testNullBoundary() {
        Coordinate point = new Coordinate(1, 1);
        assertFalse(GeoUtils.isPointInBoundary(point, null), "Null boundary should return false.");
    }

    @Test
    void testEmptyBoundary() {
        Coordinate point = new Coordinate(1, 1);
        List<Coordinate> emptyBoundary = new ArrayList<>();
        assertFalse(GeoUtils.isPointInBoundary(point, emptyBoundary), "Empty boundary should return false.");
    }

    @Test
    void testBoundaryWithLessThan3Vertices() {
        Coordinate point = new Coordinate(1, 1);
        List<Coordinate> smallBoundary = Arrays.asList(new Coordinate(0, 0), new Coordinate(1, 1));
        assertFalse(GeoUtils.isPointInBoundary(point, smallBoundary), "Boundary with less than 3 vertices should return false.");
    }

    // --- Tests for calculateDistance --- 

    // Tolerance for floating-point comparisons (e.g., 1 meter)
    private static final double DISTANCE_TOLERANCE_METERS = 1.0;

    @Test
    void testCalculateDistanceZero() {
        Coordinate point1 = new Coordinate(40.7128, -74.0060); // New York City
        Coordinate point2 = new Coordinate(40.7128, -74.0060); // Same point
        assertEquals(0.0, GeoUtils.calculateDistance(point1, point2), DISTANCE_TOLERANCE_METERS,
                     "Distance between the same point should be 0.");
    }

    @Test
    void testCalculateDistanceKnownPoints() {
        // Approximate coordinates for verification 
        Coordinate nyc = new Coordinate(40.7128, -74.0060); // New York City
        Coordinate london = new Coordinate(51.5074, -0.1278); // London
        // Expected distance ~5570 km or 5,570,000 meters
        double expectedDistanceMeters = 5570231; // More precise value from online calculator
        assertEquals(expectedDistanceMeters, GeoUtils.calculateDistance(nyc, london), 5000, // Use larger tolerance for large distances
                     "Distance between NYC and London should be approximately 5570 km.");

        Coordinate sf = new Coordinate(37.7749, -122.4194); // San Francisco
        Coordinate la = new Coordinate(34.0522, -118.2437); // Los Angeles
        // Expected distance ~559 km or 559,000 meters
        double expectedDistanceSFtoLA = 559114; // More precise value
        assertEquals(expectedDistanceSFtoLA, GeoUtils.calculateDistance(sf, la), 1000, 
                     "Distance between SF and LA should be approximately 559 km.");
    }

    @Test
    void testCalculateDistanceAlongEquator() {
        Coordinate point1 = new Coordinate(0, 0); // Equator, Prime Meridian
        Coordinate point2 = new Coordinate(0, 1); // Equator, 1 degree East
        // Distance for 1 degree longitude at equator ≈ 111.32 km
        double expectedDistanceMeters = 111319.49; // From calculation: (pi/180) * R
        assertEquals(expectedDistanceMeters, GeoUtils.calculateDistance(point1, point2), DISTANCE_TOLERANCE_METERS,
                     "Distance for 1 degree along equator should be approx 111.32 km.");
    }

    @Test
    void testCalculateDistanceAlongMeridian() {
        Coordinate point1 = new Coordinate(0, 0); // Equator, Prime Meridian
        Coordinate point2 = new Coordinate(1, 0); // 1 degree North, Prime Meridian
        // Distance for 1 degree latitude ≈ 111.32 km (same as longitude at equator)
        double expectedDistanceMeters = 111319.49; // From calculation: (pi/180) * R 
        assertEquals(expectedDistanceMeters, GeoUtils.calculateDistance(point1, point2), DISTANCE_TOLERANCE_METERS,
                     "Distance for 1 degree along meridian should be approx 111.32 km.");
    }

    @Test
    void testCalculateDistanceAntipodes() {
        Coordinate northPole = new Coordinate(90, 0);
        Coordinate southPole = new Coordinate(-90, 0);
        // Distance is half the Earth's circumference using the radius in GeoUtils (6371000m)
        double expectedDistanceMeters = 20015115.45; // Math.PI * 6371000
        assertEquals(expectedDistanceMeters, GeoUtils.calculateDistance(northPole, southPole), DISTANCE_TOLERANCE_METERS,
                     "Distance between North and South Pole should be half circumference.");

        Coordinate point1 = new Coordinate(0, 0);
        Coordinate point2 = new Coordinate(0, 180); // Antipodal point on equator
         assertEquals(expectedDistanceMeters, GeoUtils.calculateDistance(point1, point2), DISTANCE_TOLERANCE_METERS,
                     "Distance between antipodal points on equator should be half circumference.");
    }

    @Test
    void testCalculateDistanceNullInput() {
        Coordinate point1 = new Coordinate(0, 0);
        assertEquals(-1.0, GeoUtils.calculateDistance(point1, null), "Distance with null input should return -1.0 (or throw exception).");
        assertEquals(-1.0, GeoUtils.calculateDistance(null, point1), "Distance with null input should return -1.0 (or throw exception).");
        assertEquals(-1.0, GeoUtils.calculateDistance(null, null), "Distance with null input should return -1.0 (or throw exception).");
    }
} 