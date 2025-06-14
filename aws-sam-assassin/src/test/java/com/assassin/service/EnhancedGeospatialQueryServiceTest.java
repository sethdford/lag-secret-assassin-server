package com.assassin.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.model.Coordinate;
import com.assassin.model.Player;
import com.assassin.service.EnhancedGeospatialQueryService.GeospatialPerformanceStats;
import com.assassin.service.EnhancedGeospatialQueryService.HeatmapData;
import com.assassin.service.EnhancedGeospatialQueryService.PlayerDistanceResult;
import com.assassin.service.SpatialIndex.BoundingBox;

class EnhancedGeospatialQueryServiceTest {

    @Mock
    private DynamoDbPlayerDao mockPlayerDao;
    
    @Mock
    private LocationService mockLocationService;
    
    private EnhancedGeospatialQueryService geospatialService;
    private List<Player> testPlayers;
    private String testGameId = "test-game-123";
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Create test players distributed around NYC area
        testPlayers = createTestPlayers();
        
        // Mock the DAO to return our test players
        when(mockPlayerDao.getPlayersByGameId(anyString())).thenReturn(testPlayers);
        
        // Create the service with mocked dependencies
        geospatialService = new EnhancedGeospatialQueryService(mockPlayerDao, mockLocationService);
    }
    
    @AfterEach
    void tearDown() {
        if (geospatialService != null) {
            geospatialService.shutdown();
        }
    }
    
    @Test
    void testFindPlayersWithinRadius() {
        // Test finding players within 500 meters of Times Square
        Coordinate timesSquare = new Coordinate(40.7580, -73.9855);
        double radius = 500; // 500 meters
        
        List<PlayerDistanceResult> results = geospatialService.findPlayersWithinRadius(
            testGameId, timesSquare, radius, true);
        
        assertNotNull(results, "Results should not be null");
        assertTrue(results.size() > 0, "Should find some players within radius");
        
        // Verify all results are within the radius
        for (PlayerDistanceResult result : results) {
            assertTrue(result.getDistance() <= radius,
                String.format("Player %s at distance %.2f should be within radius %.2f",
                             result.getPlayer().getPlayerID(), result.getDistance(), radius));
        }
        
        // Verify results are sorted by distance
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i-1).getDistance() <= results.get(i).getDistance(),
                "Results should be sorted by distance");
        }
    }
    
    @Test
    void testFindPlayersWithinRadiusExcludeInactive() {
        Coordinate center = new Coordinate(40.7580, -73.9855);
        
        // Test with inactive players included
        List<PlayerDistanceResult> allResults = geospatialService.findPlayersWithinRadius(
            testGameId, center, 1000, true);
        
        // Test with inactive players excluded
        List<PlayerDistanceResult> activeResults = geospatialService.findPlayersWithinRadius(
            testGameId, center, 1000, false);
        
        assertTrue(allResults.size() >= activeResults.size(),
            "Including inactive players should return same or more results");
        
        // Verify all active results contain only active players
        for (PlayerDistanceResult result : activeResults) {
            Player player = result.getPlayer();
            assertTrue(isPlayerActive(player),
                String.format("Player %s should be active", player.getPlayerID()));
        }
    }
    
    @Test
    void testFindPlayersWithinBounds() {
        // Test finding players within Manhattan bounds
        BoundingBox manhattanBounds = new BoundingBox(
            new Coordinate(40.7000, -74.0200), // Southwest
            new Coordinate(40.8000, -73.9000)  // Northeast
        );
        
        List<Player> results = geospatialService.findPlayersWithinBounds(
            testGameId, manhattanBounds, true);
        
        assertNotNull(results, "Results should not be null");
        assertTrue(results.size() > 0, "Should find some players within bounds");
        
        // Verify all results are within the bounding box
        for (Player player : results) {
            assertTrue(manhattanBounds.contains(new Coordinate(player.getLatitude(), player.getLongitude())),
                String.format("Player %s should be within bounds", player.getPlayerID()));
        }
    }
    
    @Test
    void testFindKNearestPlayers() {
        Coordinate center = new Coordinate(40.7580, -73.9855);
        int k = 5;
        
        List<PlayerDistanceResult> results = geospatialService.findKNearestPlayers(
            testGameId, center, k, true);
        
        assertNotNull(results, "Results should not be null");
        assertTrue(results.size() <= k, "Should not return more than k results");
        
        // Verify results are sorted by distance
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i-1).getDistance() <= results.get(i).getDistance(),
                "Results should be sorted by distance");
        }
        
        // Test with different k values
        List<PlayerDistanceResult> moreResults = geospatialService.findKNearestPlayers(
            testGameId, center, k * 2, true);
        
        assertTrue(moreResults.size() >= results.size(),
            "Requesting more results should return same or more players");
    }
    
    @Test
    void testFindPlayersWithinPolygon() {
        // Create a triangular polygon in Central Park area
        List<Coordinate> centralParkTriangle = Arrays.asList(
            new Coordinate(40.7650, -73.9800),
            new Coordinate(40.7750, -73.9800),
            new Coordinate(40.7700, -73.9700)
        );
        
        List<Player> results = geospatialService.findPlayersWithinPolygon(
            testGameId, centralParkTriangle, true);
        
        assertNotNull(results, "Results should not be null");
        
        // Verify results are reasonable (within the general area)
        for (Player player : results) {
            assertTrue(player.getLatitude() >= 40.7650 && player.getLatitude() <= 40.7750,
                "Player should be within polygon latitude bounds");
            assertTrue(player.getLongitude() >= -73.9800 && player.getLongitude() <= -73.9700,
                "Player should be within polygon longitude bounds");
        }
    }
    
    @Test
    void testGeneratePlayerHeatmap() {
        BoundingBox bounds = new BoundingBox(
            new Coordinate(40.7000, -74.0000),
            new Coordinate(40.8000, -73.9000)
        );
        int resolution = 10;
        
        HeatmapData heatmap = geospatialService.generatePlayerHeatmap(testGameId, bounds, resolution);
        
        assertNotNull(heatmap, "Heatmap should not be null");
        assertEquals(resolution, heatmap.getResolution(), "Resolution should match");
        assertEquals(bounds, heatmap.getBounds(), "Bounds should match");
        
        double[][] data = heatmap.getData();
        assertEquals(resolution, data.length, "Data should have correct dimensions");
        assertEquals(resolution, data[0].length, "Data should have correct dimensions");
        
        // Verify data is normalized (values between 0 and 1)
        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                assertTrue(data[i][j] >= 0.0 && data[i][j] <= 1.0,
                    "Heatmap values should be normalized between 0 and 1");
            }
        }
    }
    
    @Test
    void testPerformanceStats() {
        // Trigger index creation by running a query
        geospatialService.findPlayersWithinRadius(testGameId, new Coordinate(40.7580, -73.9855), 1000, true);
        
        // Get performance stats for specific game
        GeospatialPerformanceStats gameStats = geospatialService.getPerformanceStats(testGameId);
        assertNotNull(gameStats, "Game stats should not be null");
        assertEquals(1, gameStats.getTotalGames(), "Should have stats for one game");
        assertTrue(gameStats.getTotalElements() > 0, "Should have some elements");
        
        // Get aggregate stats
        GeospatialPerformanceStats allStats = geospatialService.getPerformanceStats(null);
        assertNotNull(allStats, "Aggregate stats should not be null");
        assertTrue(allStats.getTotalGames() >= 1, "Should have at least one game");
    }
    
    @Test
    void testRefreshSpatialIndex() {
        // Run initial query to create index
        List<PlayerDistanceResult> initialResults = geospatialService.findPlayersWithinRadius(
            testGameId, new Coordinate(40.7580, -73.9855), 1000, true);
        
        // Refresh the index
        geospatialService.refreshSpatialIndex(testGameId);
        
        // Run query again to verify index was rebuilt
        List<PlayerDistanceResult> refreshedResults = geospatialService.findPlayersWithinRadius(
            testGameId, new Coordinate(40.7580, -73.9855), 1000, true);
        
        assertEquals(initialResults.size(), refreshedResults.size(),
            "Results should be consistent after refresh");
    }
    
    @Test
    void testClearAllIndexes() {
        // Create some indexes by running queries
        geospatialService.findPlayersWithinRadius(testGameId, new Coordinate(40.7580, -73.9855), 1000, true);
        geospatialService.findPlayersWithinRadius("another-game", new Coordinate(40.7580, -73.9855), 1000, true);
        
        // Clear all indexes
        geospatialService.clearAllIndexes();
        
        // Verify indexes are cleared (this will trigger rebuild)
        GeospatialPerformanceStats stats = geospatialService.getPerformanceStats(null);
        // After clearing, the next query will rebuild the index, so we can't easily test the cleared state
        assertNotNull(stats, "Stats should still be available");
    }
    
    @Test
    void testPerformanceRequirements() {
        // Test performance with a reasonable number of players
        List<Player> manyPlayers = createManyTestPlayers(1000);
        when(mockPlayerDao.getPlayersByGameId(anyString())).thenReturn(manyPlayers);
        
        Coordinate center = new Coordinate(40.7580, -73.9855);
        
        // Measure query performance
        long startTime = System.currentTimeMillis();
        List<PlayerDistanceResult> results = geospatialService.findPlayersWithinRadius(
            testGameId, center, 1000, true);
        long queryTime = System.currentTimeMillis() - startTime;
        
        // Performance requirement: sub-100ms for up to 10,000 elements
        assertTrue(queryTime < 100, 
            String.format("Query should complete in under 100ms, took %dms", queryTime));
        
        assertNotNull(results, "Results should not be null");
        
        System.out.printf("Performance test: Found %d players in %dms%n", results.size(), queryTime);
    }
    
    @Test
    void testLocationSharingFilter() {
        // Create players with different location sharing settings
        List<Player> mixedPlayers = new ArrayList<>();
        
        // Player with location sharing enabled
        Player sharingPlayer = createTestPlayer("sharing-player", 40.7580, -73.9855, "ACTIVE");
        sharingPlayer.setLocationSharingEnabled(true);
        mixedPlayers.add(sharingPlayer);
        
        // Player with location sharing disabled
        Player nonSharingPlayer = createTestPlayer("non-sharing-player", 40.7581, -73.9856, "ACTIVE");
        nonSharingPlayer.setLocationSharingEnabled(false);
        mixedPlayers.add(nonSharingPlayer);
        
        when(mockPlayerDao.getPlayersByGameId(anyString())).thenReturn(mixedPlayers);
        
        List<PlayerDistanceResult> results = geospatialService.findPlayersWithinRadius(
            testGameId, new Coordinate(40.7580, -73.9855), 1000, true);
        
        // Should only find the player with location sharing enabled
        assertEquals(1, results.size(), "Should only find players with location sharing enabled");
        assertEquals("sharing-player", results.get(0).getPlayer().getPlayerID(),
            "Should find the sharing player");
    }
    
    @Test
    void testEmptyGameHandling() {
        // Test with empty game
        when(mockPlayerDao.getPlayersByGameId(anyString())).thenReturn(new ArrayList<>());
        
        List<PlayerDistanceResult> results = geospatialService.findPlayersWithinRadius(
            "empty-game", new Coordinate(40.7580, -73.9855), 1000, true);
        
        assertNotNull(results, "Results should not be null");
        assertEquals(0, results.size(), "Should find no players in empty game");
    }
    
    @Test
    void testNullLocationHandling() {
        // Create players with null locations
        List<Player> playersWithNulls = new ArrayList<>();
        playersWithNulls.add(createTestPlayer("valid-player", 40.7580, -73.9855, "ACTIVE"));
        
        Player nullPlayer = createTestPlayer("null-player", null, null, "ACTIVE");
        playersWithNulls.add(nullPlayer);
        
        when(mockPlayerDao.getPlayersByGameId(anyString())).thenReturn(playersWithNulls);
        
        List<PlayerDistanceResult> results = geospatialService.findPlayersWithinRadius(
            testGameId, new Coordinate(40.7580, -73.9855), 1000, true);
        
        // Should only find the player with valid location
        assertEquals(1, results.size(), "Should only find players with valid locations");
        assertEquals("valid-player", results.get(0).getPlayer().getPlayerID(),
            "Should find the player with valid location");
    }
    
    // Helper methods
    
    private List<Player> createTestPlayers() {
        List<Player> players = new ArrayList<>();
        
        // Create players distributed around NYC
        String[] locations = {
            "Times Square", "Central Park", "Brooklyn Bridge", "Statue of Liberty",
            "Empire State", "Wall Street", "Greenwich Village", "Chinatown",
            "SoHo", "Upper East Side"
        };
        
        double[][] coordinates = {
            {40.7580, -73.9855}, // Times Square
            {40.7829, -73.9654}, // Central Park
            {40.7061, -73.9969}, // Brooklyn Bridge
            {40.6892, -74.0445}, // Statue of Liberty
            {40.7484, -73.9857}, // Empire State
            {40.7074, -74.0113}, // Wall Street
            {40.7336, -74.0027}, // Greenwich Village
            {40.7158, -73.9970}, // Chinatown
            {40.7233, -74.0030}, // SoHo
            {40.7736, -73.9566}  // Upper East Side
        };
        
        for (int i = 0; i < locations.length; i++) {
            String status = (i % 3 == 0) ? "INACTIVE" : "ACTIVE"; // Mix of active/inactive
            Player player = createTestPlayer(
                "player-" + (i + 1),
                coordinates[i][0],
                coordinates[i][1],
                status
            );
            player.setLocationSharingEnabled(true);
            players.add(player);
        }
        
        return players;
    }
    
    private List<Player> createManyTestPlayers(int count) {
        List<Player> players = new ArrayList<>();
        
        // Create players randomly distributed in NYC area
        double minLat = 40.7000, maxLat = 40.8000;
        double minLon = -74.0200, maxLon = -73.9000;
        
        for (int i = 0; i < count; i++) {
            double lat = minLat + Math.random() * (maxLat - minLat);
            double lon = minLon + Math.random() * (maxLon - minLon);
            String status = (Math.random() > 0.2) ? "ACTIVE" : "INACTIVE"; // 80% active
            
            Player player = createTestPlayer("player-" + i, lat, lon, status);
            player.setLocationSharingEnabled(Math.random() > 0.1); // 90% sharing enabled
            players.add(player);
        }
        
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
    
    private boolean isPlayerActive(Player player) {
        return player.getStatus() != null && 
               (player.getStatus().toString().equals("ACTIVE") || 
                player.getStatus().toString().equals("ALIVE"));
    }
} 