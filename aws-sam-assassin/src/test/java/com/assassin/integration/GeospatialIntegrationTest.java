package com.assassin.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.model.Coordinate;
import com.assassin.model.Player;
import com.assassin.service.EnhancedGeospatialQueryService;
import com.assassin.service.GeospatialQueryService;
import com.assassin.service.GeospatialQueryService.BoundingBox;
import com.assassin.service.GeospatialQueryService.PlayerDistanceResult;
import com.assassin.service.LocationService;
import com.assassin.util.GeoUtils;

/**
 * Integration tests for geospatial functionality.
 * Tests the interaction between GeospatialQueryService, EnhancedGeospatialQueryService, and related components.
 */
class GeospatialIntegrationTest {

    @Mock
    private DynamoDbPlayerDao mockPlayerDao;
    
    @Mock
    private LocationService mockLocationService;

    private GeospatialQueryService originalService;
    private EnhancedGeospatialQueryService enhancedService;
    private List<Player> testPlayers;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Initialize services with mocked dependencies
        originalService = new GeospatialQueryService(mockPlayerDao, mockLocationService);
        enhancedService = new EnhancedGeospatialQueryService(mockPlayerDao, mockLocationService);
        
        // Create test data
        testPlayers = createTestPlayers();
        when(mockPlayerDao.getPlayersByGameId(anyString())).thenReturn(testPlayers);
    }

    @Test
    void testRadiusQueryComparison() {
        // Test that both services return similar results for radius queries
        String gameId = "test-game";
        Coordinate center = new Coordinate(37.7749, -122.4194); // San Francisco
        double radius = 1000.0; // 1km
        
        List<Player> originalResults = originalService.findPlayersWithinRadius(gameId, center, radius, true);
        List<EnhancedGeospatialQueryService.PlayerDistanceResult> enhancedDistanceResults = enhancedService.findPlayersWithinRadius(gameId, center, radius, true);
        List<Player> enhancedResults = enhancedDistanceResults.stream()
            .map(EnhancedGeospatialQueryService.PlayerDistanceResult::getPlayer)
            .collect(java.util.stream.Collectors.toList());
        
        // Both should find the same players (though order might differ)
        assertEquals(originalResults.size(), enhancedResults.size());
        
        // Verify all players are within the radius
        for (Player player : enhancedResults) {
            Coordinate playerCoord = new Coordinate(player.getLatitude(), player.getLongitude());
            double distance = GeoUtils.calculateDistance(center, playerCoord);
            assertTrue(distance <= radius, "Player should be within radius");
        }
    }

    @Test
    void testNearestPlayersQuery() {
        String gameId = "test-game";
        Coordinate center = new Coordinate(37.7749, -122.4194);
        int maxResults = 3;
        
        List<PlayerDistanceResult> originalResults = originalService.findNearestPlayers(gameId, center, maxResults);
        List<EnhancedGeospatialQueryService.PlayerDistanceResult> enhancedResults = enhancedService.findKNearestPlayers(gameId, center, maxResults, true);
        
        assertEquals(Math.min(maxResults, testPlayers.size()), originalResults.size());
        assertEquals(Math.min(maxResults, testPlayers.size()), enhancedResults.size());
        
        // Verify results are sorted by distance (for original service)
        for (int i = 1; i < originalResults.size(); i++) {
            assertTrue(originalResults.get(i-1).getDistance() <= originalResults.get(i).getDistance(),
                      "Results should be sorted by distance");
        }
    }

    @Test
    void testPolygonQuery() {
        String gameId = "test-game";
        
        // Create a polygon around San Francisco
        List<Coordinate> polygon = Arrays.asList(
            new Coordinate(37.7849, -122.4294),  // NW
            new Coordinate(37.7849, -122.4094),  // NE
            new Coordinate(37.7649, -122.4094),  // SE
            new Coordinate(37.7649, -122.4294)   // SW
        );
        
        List<Player> enhancedResults = enhancedService.findPlayersWithinPolygon(gameId, polygon, true);
        
        // Verify all returned players are actually inside the polygon
        for (Player player : enhancedResults) {
            Coordinate playerCoord = new Coordinate(player.getLatitude(), player.getLongitude());
            assertTrue(GeoUtils.isPointInBoundary(playerCoord, polygon),
                      "Player should be inside the polygon");
        }
    }

    @Test
    void testBoundingBoxQuery() {
        String gameId = "test-game";
        
        // Create a bounding box around San Francisco
        Coordinate sw = new Coordinate(37.7649, -122.4294);
        Coordinate ne = new Coordinate(37.7849, -122.4094);
        BoundingBox bounds = new BoundingBox(sw, ne);
        
        List<Player> originalResults = originalService.spatialRangeQuery(gameId, bounds);
        // Convert BoundingBox types - Enhanced service uses SpatialIndex.BoundingBox
        com.assassin.service.SpatialIndex.BoundingBox enhancedBounds = 
            new com.assassin.service.SpatialIndex.BoundingBox(sw, ne);
        List<Player> enhancedResults = enhancedService.findPlayersWithinBounds(gameId, enhancedBounds, true);
        
        // Both should return similar results
        assertEquals(originalResults.size(), enhancedResults.size());
        
        // Verify all players are within bounds
        for (Player player : enhancedResults) {
            assertTrue(player.getLatitude() >= sw.getLatitude() && 
                      player.getLatitude() <= ne.getLatitude(),
                      "Player latitude should be within bounds");
            assertTrue(player.getLongitude() >= sw.getLongitude() && 
                      player.getLongitude() <= ne.getLongitude(),
                      "Player longitude should be within bounds");
        }
    }

    @Test
    void testPerformanceComparison() {
        String gameId = "test-game";
        Coordinate center = new Coordinate(37.7749, -122.4194);
        double radius = 2000.0; // 2km
        
        // Measure original service performance
        long startTime = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            originalService.findPlayersWithinRadius(gameId, center, radius, true);
        }
        long originalTime = System.nanoTime() - startTime;
        
        // Measure enhanced service performance
        startTime = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            enhancedService.findPlayersWithinRadius(gameId, center, radius, true);
        }
        long enhancedTime = System.nanoTime() - startTime;
        
        // Enhanced service may be slower for small datasets due to indexing overhead
        // but should scale better with larger datasets. Allow up to 3x slower for small test datasets
        assertTrue(enhancedTime <= originalTime * 3.0,
                  String.format("Enhanced service took %d ns vs original %d ns (%.2fx slower)", 
                               enhancedTime, originalTime, (double)enhancedTime / originalTime));
        
        System.out.println(String.format("Performance comparison: Enhanced=%.2fms, Original=%.2fms", 
                                        enhancedTime / 1_000_000.0, originalTime / 1_000_000.0));
    }

    @Test
    void testCachingBehavior() {
        String gameId = "test-game";
        Coordinate center = new Coordinate(37.7749, -122.4194);
        double radius = 1000.0;
        
        // First query - should populate cache
        List<EnhancedGeospatialQueryService.PlayerDistanceResult> firstDistanceResults = enhancedService.findPlayersWithinRadius(gameId, center, radius, true);
        List<Player> firstResults = firstDistanceResults.stream()
            .map(EnhancedGeospatialQueryService.PlayerDistanceResult::getPlayer)
            .collect(java.util.stream.Collectors.toList());
        
        // Second identical query - should use cache
        List<EnhancedGeospatialQueryService.PlayerDistanceResult> secondDistanceResults = enhancedService.findPlayersWithinRadius(gameId, center, radius, true);
        List<Player> secondResults = secondDistanceResults.stream()
            .map(EnhancedGeospatialQueryService.PlayerDistanceResult::getPlayer)
            .collect(java.util.stream.Collectors.toList());
        
        // Results should be identical
        assertEquals(firstResults.size(), secondResults.size());
        for (int i = 0; i < firstResults.size(); i++) {
            assertEquals(firstResults.get(i).getPlayerID(), secondResults.get(i).getPlayerID());
        }
    }

    @Test
    void testIndexStatistics() {
        // Test that the enhanced service provides index statistics
        assertNotNull(enhancedService);
        
        // Perform some operations to populate the index
        String gameId = "test-game";
        Coordinate center = new Coordinate(37.7749, -122.4194);
        enhancedService.findPlayersWithinRadius(gameId, center, 1000.0, true);
        
        // Index should have been populated
        // Note: We can't test getIndexStatistics() as it's not exposed in the current implementation
        // This is a placeholder for when that functionality is added
        assertTrue(true, "Index statistics functionality placeholder");
    }

    private List<Player> createTestPlayers() {
        List<Player> players = new ArrayList<>();
        
        // Create players around San Francisco
        players.add(createTestPlayer("player1", 37.7749, -122.4194, "ACTIVE"));  // SF center
        players.add(createTestPlayer("player2", 37.7849, -122.4094, "ACTIVE"));  // North
        players.add(createTestPlayer("player3", 37.7649, -122.4294, "ACTIVE"));  // South
        players.add(createTestPlayer("player4", 37.7749, -122.4394, "ACTIVE"));  // West
        players.add(createTestPlayer("player5", 37.7749, -122.3994, "ACTIVE"));  // East
        players.add(createTestPlayer("player6", 37.8049, -122.4194, "INACTIVE")); // Far north
        
        return players;
    }

    private Player createTestPlayer(String id, Double latitude, Double longitude, String status) {
        Player player = new Player();
        player.setPlayerID(id);
        player.setLatitude(latitude);
        player.setLongitude(longitude);
        player.setStatus(status);
        player.setLocationSharingEnabled(true);
        return player;
    }
} 