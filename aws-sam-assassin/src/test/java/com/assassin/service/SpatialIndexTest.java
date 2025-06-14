package com.assassin.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.assassin.model.Coordinate;
import com.assassin.service.SpatialIndex.BoundingBox;
import com.assassin.service.SpatialIndex.ElementDistancePair;
import com.assassin.service.SpatialIndex.IndexStatistics;
import com.assassin.service.SpatialIndex.SpatialElement;

class SpatialIndexTest {

    private SpatialIndex<TestSpatialElement> spatialIndex;
    private BoundingBox testBounds;
    
    // Test data
    private List<TestSpatialElement> testElements;
    
    @BeforeEach
    void setUp() {
        // Create a test area covering a small region (approximately 1km x 1km)
        testBounds = new BoundingBox(
            new Coordinate(40.7000, -74.0100), // Southwest corner
            new Coordinate(40.7100, -74.0000)  // Northeast corner
        );
        
        spatialIndex = new SpatialIndex<>(testBounds);
        
        // Create test elements distributed across the area
        testElements = createTestElements();
        
        // Insert test elements into the index
        for (TestSpatialElement element : testElements) {
            spatialIndex.insert(element);
        }
    }
    
    @Test
    void testInsertAndBasicQuery() {
        // Test that all elements were inserted
        IndexStatistics stats = spatialIndex.getStatistics();
        assertEquals(testElements.size(), stats.totalElements, "All elements should be inserted");
        assertTrue(stats.leafNodes > 0, "Should have leaf nodes");
    }
    
    @Test
    void testRadiusQuery() {
        // Test radius query around the center of our test area
        Coordinate center = new Coordinate(40.7050, -74.0050);
        double radius = 200; // 200 meters
        
        List<TestSpatialElement> results = spatialIndex.findWithinRadius(center, radius);
        
        assertNotNull(results, "Results should not be null");
        assertTrue(results.size() > 0, "Should find some elements within radius");
        
        // Verify all results are actually within the radius
        for (TestSpatialElement element : results) {
            double distance = calculateDistance(center, element.getLocation());
            assertTrue(distance <= radius, 
                String.format("Element %s at distance %.2f should be within radius %.2f", 
                             element.getId(), distance, radius));
        }
    }
    
    @Test
    void testBoundingBoxQuery() {
        // Test bounding box query for a smaller area within our test bounds
        BoundingBox queryBounds = new BoundingBox(
            new Coordinate(40.7020, -74.0080),
            new Coordinate(40.7080, -74.0020)
        );
        
        List<TestSpatialElement> results = spatialIndex.findWithinBounds(queryBounds);
        
        assertNotNull(results, "Results should not be null");
        
        // Verify all results are actually within the bounding box
        for (TestSpatialElement element : results) {
            assertTrue(queryBounds.contains(element.getLocation()),
                String.format("Element %s should be within query bounds", element.getId()));
        }
    }
    
    @Test
    void testKNearestNeighbor() {
        Coordinate queryPoint = new Coordinate(40.7050, -74.0050);
        int k = 5;
        
        List<ElementDistancePair<TestSpatialElement>> results = spatialIndex.findKNearest(queryPoint, k);
        
        assertNotNull(results, "Results should not be null");
        assertTrue(results.size() <= k, "Should not return more than k results");
        
        // Verify results are sorted by distance
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i-1).getDistance() <= results.get(i).getDistance(),
                "Results should be sorted by distance");
        }
        
        // Verify distances are correct
        for (ElementDistancePair<TestSpatialElement> pair : results) {
            double expectedDistance = calculateDistance(queryPoint, pair.getElement().getLocation());
            assertEquals(expectedDistance, pair.getDistance(), 1.0, // 1 meter tolerance
                "Distance should be calculated correctly");
        }
    }
    
    @Test
    void testPolygonQuery() {
        // Create a triangular polygon within our test area
        List<Coordinate> polygon = Arrays.asList(
            new Coordinate(40.7030, -74.0070),
            new Coordinate(40.7070, -74.0070),
            new Coordinate(40.7050, -74.0030)
        );
        
        List<TestSpatialElement> results = spatialIndex.findWithinPolygon(polygon);
        
        assertNotNull(results, "Results should not be null");
        
        // Verify all results are actually within the polygon
        // Note: This is a basic test - more complex polygon testing would require
        // more sophisticated point-in-polygon verification
        for (TestSpatialElement element : results) {
            // For this test, we'll just verify the results are reasonable
            assertTrue(element.getLocation().getLatitude() >= 40.7030 && 
                      element.getLocation().getLatitude() <= 40.7070,
                "Element should be within polygon latitude bounds");
        }
    }
    
    @Test
    void testRemoveElement() {
        // Get initial count
        IndexStatistics initialStats = spatialIndex.getStatistics();
        int initialCount = initialStats.totalElements;
        
        // Remove an element
        TestSpatialElement elementToRemove = testElements.get(0);
        boolean removed = spatialIndex.remove(elementToRemove);
        
        assertTrue(removed, "Element should be successfully removed");
        
        // Verify count decreased
        IndexStatistics afterStats = spatialIndex.getStatistics();
        assertEquals(initialCount - 1, afterStats.totalElements, "Element count should decrease by 1");
        
        // Verify element is no longer found in queries
        List<TestSpatialElement> results = spatialIndex.findWithinRadius(
            elementToRemove.getLocation(), 10);
        assertFalse(results.contains(elementToRemove), "Removed element should not be found in queries");
    }
    
    @Test
    void testUpdateElement() {
        TestSpatialElement element = testElements.get(0);
        Coordinate originalLocation = element.getLocation();
        
        // Move the element to a new location
        Coordinate newLocation = new Coordinate(40.7060, -74.0040);
        element.updateLocation(newLocation);
        
        // Update in the index
        spatialIndex.update(element);
        
        // Verify element is found at new location
        List<TestSpatialElement> results = spatialIndex.findWithinRadius(newLocation, 10);
        assertTrue(results.contains(element), "Updated element should be found at new location");
        
        // Verify element is not found at old location
        List<TestSpatialElement> oldResults = spatialIndex.findWithinRadius(originalLocation, 10);
        assertFalse(oldResults.contains(element), "Updated element should not be found at old location");
    }
    
    @Test
    void testPerformanceWithManyElements() {
        // Clear existing index and create a larger one
        spatialIndex.clear();
        
        // Create many test elements (1000)
        List<TestSpatialElement> manyElements = createManyTestElements(1000);
        
        // Measure insertion time
        long insertStart = System.currentTimeMillis();
        for (TestSpatialElement element : manyElements) {
            spatialIndex.insert(element);
        }
        long insertTime = System.currentTimeMillis() - insertStart;
        
        // Measure query time
        Coordinate center = new Coordinate(40.7050, -74.0050);
        long queryStart = System.currentTimeMillis();
        List<TestSpatialElement> results = spatialIndex.findWithinRadius(center, 500);
        long queryTime = System.currentTimeMillis() - queryStart;
        
        // Performance assertions (should be well under 100ms for 1000 elements)
        assertTrue(insertTime < 1000, "Insertion of 1000 elements should take less than 1 second");
        assertTrue(queryTime < 100, "Query should take less than 100ms");
        
        // Verify index statistics
        IndexStatistics stats = spatialIndex.getStatistics();
        assertEquals(1000, stats.totalElements, "Should have 1000 elements");
        assertTrue(stats.maxDepth > 0, "Should have some depth");
        
        System.out.printf("Performance test: Inserted %d elements in %dms, queried in %dms%n", 
                         manyElements.size(), insertTime, queryTime);
        System.out.printf("Index stats: %s%n", stats);
    }
    
    @Test
    void testBoundingBoxIntersection() {
        BoundingBox box1 = new BoundingBox(
            new Coordinate(40.7000, -74.0100),
            new Coordinate(40.7050, -74.0050)
        );
        
        BoundingBox box2 = new BoundingBox(
            new Coordinate(40.7025, -74.0075),
            new Coordinate(40.7075, -74.0025)
        );
        
        BoundingBox box3 = new BoundingBox(
            new Coordinate(40.7100, -74.0000),
            new Coordinate(40.7150, 74.0050)
        );
        
        assertTrue(box1.intersects(box2), "Overlapping boxes should intersect");
        assertFalse(box1.intersects(box3), "Non-overlapping boxes should not intersect");
    }
    
    @Test
    void testBoundingBoxContainment() {
        BoundingBox box = new BoundingBox(
            new Coordinate(40.7000, -74.0100),
            new Coordinate(40.7100, -74.0000)
        );
        
        Coordinate insidePoint = new Coordinate(40.7050, -74.0050);
        Coordinate outsidePoint = new Coordinate(40.6950, -74.0050);
        Coordinate edgePoint = new Coordinate(40.7000, -74.0050);
        
        assertTrue(box.contains(insidePoint), "Point inside box should be contained");
        assertFalse(box.contains(outsidePoint), "Point outside box should not be contained");
        assertTrue(box.contains(edgePoint), "Point on edge should be contained");
    }
    
    @Test
    void testEmptyQueries() {
        // Query an area with no elements
        BoundingBox emptyArea = new BoundingBox(
            new Coordinate(41.0000, -75.0000),
            new Coordinate(41.0100, -74.9900)
        );
        
        List<TestSpatialElement> results = spatialIndex.findWithinBounds(emptyArea);
        assertNotNull(results, "Results should not be null");
        assertEquals(0, results.size(), "Should find no elements in empty area");
        
        // Test radius query in empty area
        List<TestSpatialElement> radiusResults = spatialIndex.findWithinRadius(
            new Coordinate(41.0050, -74.9950), 100);
        assertEquals(0, radiusResults.size(), "Should find no elements in empty area");
    }
    
    // Helper methods
    
    private List<TestSpatialElement> createTestElements() {
        List<TestSpatialElement> elements = new ArrayList<>();
        
        // Create a grid of test elements
        double latStep = 0.001; // ~111 meters
        double lonStep = 0.001; // ~85 meters at this latitude
        
        int id = 1;
        for (double lat = 40.7010; lat <= 40.7090; lat += latStep) {
            for (double lon = -74.0090; lon <= -74.0010; lon += lonStep) {
                elements.add(new TestSpatialElement(String.valueOf(id++), new Coordinate(lat, lon)));
            }
        }
        
        return elements;
    }
    
    private List<TestSpatialElement> createManyTestElements(int count) {
        List<TestSpatialElement> elements = new ArrayList<>();
        
        // Create random elements within the test bounds
        double latRange = testBounds.getNorthEast().getLatitude() - testBounds.getSouthWest().getLatitude();
        double lonRange = testBounds.getNorthEast().getLongitude() - testBounds.getSouthWest().getLongitude();
        
        for (int i = 0; i < count; i++) {
            double lat = testBounds.getSouthWest().getLatitude() + Math.random() * latRange;
            double lon = testBounds.getSouthWest().getLongitude() + Math.random() * lonRange;
            elements.add(new TestSpatialElement(String.valueOf(i), new Coordinate(lat, lon)));
        }
        
        return elements;
    }
    
    private double calculateDistance(Coordinate c1, Coordinate c2) {
        // Simple haversine distance calculation for testing
        final double R = 6371000; // Earth radius in meters
        double lat1Rad = Math.toRadians(c1.getLatitude());
        double lat2Rad = Math.toRadians(c2.getLatitude());
        double deltaLatRad = Math.toRadians(c2.getLatitude() - c1.getLatitude());
        double deltaLonRad = Math.toRadians(c2.getLongitude() - c1.getLongitude());
        
        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    // Test implementation of SpatialElement
    private static class TestSpatialElement implements SpatialElement {
        private final String id;
        private Coordinate location;
        
        public TestSpatialElement(String id, Coordinate location) {
            this.id = id;
            this.location = location;
        }
        
        @Override
        public String getId() {
            return id;
        }
        
        @Override
        public Coordinate getLocation() {
            return location;
        }
        
        public void updateLocation(Coordinate newLocation) {
            this.location = newLocation;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestSpatialElement that = (TestSpatialElement) obj;
            return id.equals(that.id);
        }
        
        @Override
        public int hashCode() {
            return id.hashCode();
        }
        
        @Override
        public String toString() {
            return String.format("TestElement[id=%s, location=%s]", id, location);
        }
    }
} 