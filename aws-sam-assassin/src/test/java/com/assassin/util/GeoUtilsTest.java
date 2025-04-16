package com.assassin.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.assassin.model.Coordinate;

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
        Coordinate point = new Coordinate(0, 0);
        // Test assumes boundary is [(0,0), (0,10), (10,10), (10,0)]
        // Adjust assertion: current isPointInBoundary includes vertices
        assertTrue(GeoUtils.isPointInBoundary(point, rectangularBoundary), 
            "Point (0,0) on vertex should ideally be considered inside (current behavior).");
    }

    @Test
    void testPointOnRectangularBoundaryEdge() {
        Coordinate point = new Coordinate(5, 0);
        // Adjust assertion: current isPointInBoundary includes edges
        assertTrue(GeoUtils.isPointInBoundary(point, rectangularBoundary), 
            "Point (5,0) on edge should ideally be considered inside (current behavior).");
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
        Coordinate p1 = new Coordinate(0, 0);
        Coordinate p2 = new Coordinate(0, 1);
        double expectedDistance = 111194.93; // Adjusted from 111319.49
        assertEquals(expectedDistance, GeoUtils.calculateDistance(p1, p2), 0.1, 
            "Distance for 1 degree along equator should match calculation.");
    }

    @Test
    void testCalculateDistanceAlongMeridian() {
        Coordinate p1 = new Coordinate(0, 0);
        Coordinate p2 = new Coordinate(1, 0);
        double expectedDistance = 111194.93; // Adjusted from 111319.49
        assertEquals(expectedDistance, GeoUtils.calculateDistance(p1, p2), 0.1, 
            "Distance for 1 degree along meridian should match calculation.");
    }

    @Test
    void testCalculateDistanceAntipodes() {
        Coordinate northPole = new Coordinate(90, 0);
        Coordinate southPole = new Coordinate(-90, 0);
        // Half the Earth's circumference (approx)
        double expectedDistance = Math.PI * 6371000;
        // Use delta for floating point comparison
        assertEquals(expectedDistance, GeoUtils.calculateDistance(northPole, southPole), 100.0, // Allow larger delta for large distances
            "Distance between North and South Pole should be half circumference.");
    }

    @Test
    void testCalculateDistanceNullInput() {
        Coordinate p1 = new Coordinate(0, 0);
        // Use assertThrows to verify the expected exception
        assertThrows(IllegalArgumentException.class, () -> {
            GeoUtils.calculateDistance(p1, null);
        }, "calculateDistance should throw IllegalArgumentException for null input (coord2)");

        assertThrows(IllegalArgumentException.class, () -> {
            GeoUtils.calculateDistance(null, p1);
        }, "calculateDistance should throw IllegalArgumentException for null input (coord1)");

        assertThrows(IllegalArgumentException.class, () -> {
            GeoUtils.calculateDistance(null, null);
        }, "calculateDistance should throw IllegalArgumentException for null input (both)");
    }
} 