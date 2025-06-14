package com.assassin.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.model.Coordinate;
import com.assassin.model.Player;
import com.assassin.service.SpatialIndex.BoundingBox;
import com.assassin.service.SpatialIndex.ElementDistancePair;
import com.assassin.service.SpatialIndex.SpatialElement;
import com.assassin.util.GeoUtils;

/**
 * Enhanced geospatial query service that provides high-performance spatial indexing and querying.
 * Implements requirements DSR-1.1 through DSR-1.4 for comprehensive geospatial operations.
 * 
 * Features:
 * - Spatial indexing with sub-100ms query performance for up to 10,000 elements
 * - Point-in-polygon testing
 * - Radius-based proximity searches  
 * - Bounding box queries
 * - K-nearest neighbor searches
 * - Caching for frequently accessed regions
 * - Thread-safe operations
 */
public class EnhancedGeospatialQueryService {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedGeospatialQueryService.class);
    
    // Performance constants
    private static final int MAX_RESULTS_PER_QUERY = 1000;
    private static final long CACHE_REFRESH_INTERVAL_MS = 10000; // 10 seconds
    private static final int SPATIAL_INDEX_MAX_ELEMENTS = 50;
    private static final int SPATIAL_INDEX_MAX_DEPTH = 8;
    
    // Dependencies
    private final DynamoDbPlayerDao playerDao;
    private final LocationService locationService;
    
    // Spatial indexes per game for optimal performance
    private final ConcurrentHashMap<String, SpatialIndex<PlayerSpatialElement>> gameIndexes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> indexLastUpdated = new ConcurrentHashMap<>();
    
    // Background refresh service
    private final ScheduledExecutorService refreshService = Executors.newScheduledThreadPool(2);
    
    public EnhancedGeospatialQueryService() {
        this.playerDao = new DynamoDbPlayerDao();
        this.locationService = new LocationService();
        startBackgroundRefresh();
    }
    
    public EnhancedGeospatialQueryService(DynamoDbPlayerDao playerDao, LocationService locationService) {
        this.playerDao = playerDao;
        this.locationService = locationService;
        startBackgroundRefresh();
    }
    
    /**
     * Finds all players within a specified radius of a coordinate.
     * Optimized for sub-100ms performance with up to 10,000 players.
     * 
     * @param gameId The game ID to search within
     * @param center The center coordinate for the search
     * @param radiusMeters The search radius in meters
     * @param includeInactive Whether to include inactive players
     * @return List of players within the radius, sorted by distance
     */
    public List<PlayerDistanceResult> findPlayersWithinRadius(String gameId, Coordinate center, 
                                                             double radiusMeters, boolean includeInactive) {
        long startTime = System.currentTimeMillis();
        logger.debug("Finding players within {}m of {},{} in game {}", 
                    radiusMeters, center.getLatitude(), center.getLongitude(), gameId);
        
        SpatialIndex<PlayerSpatialElement> index = getOrCreateSpatialIndex(gameId);
        List<PlayerSpatialElement> spatialElements = index.findWithinRadius(center, radiusMeters);
        
        List<PlayerDistanceResult> results = spatialElements.stream()
            .filter(element -> includeInactive || isPlayerActive(element.getPlayer()))
            .filter(element -> element.getPlayer().getLocationSharingEnabled())
            .map(element -> {
                double distance = GeoUtils.calculateDistance(center, element.getLocation());
                return new PlayerDistanceResult(element.getPlayer(), distance);
            })
            .sorted((a, b) -> Double.compare(a.getDistance(), b.getDistance()))
            .limit(MAX_RESULTS_PER_QUERY)
            .collect(Collectors.toList());
        
        long duration = System.currentTimeMillis() - startTime;
        logger.debug("Found {} players within radius in {}ms", results.size(), duration);
        
        return results;
    }
    
    /**
     * Finds all players within a bounding box.
     * 
     * @param gameId The game ID to search within
     * @param bounds The bounding box for the search
     * @param includeInactive Whether to include inactive players
     * @return List of players within the bounding box
     */
    public List<Player> findPlayersWithinBounds(String gameId, BoundingBox bounds, boolean includeInactive) {
        long startTime = System.currentTimeMillis();
        logger.debug("Finding players within bounds {} in game {}", bounds, gameId);
        
        SpatialIndex<PlayerSpatialElement> index = getOrCreateSpatialIndex(gameId);
        List<PlayerSpatialElement> spatialElements = index.findWithinBounds(bounds);
        
        List<Player> results = spatialElements.stream()
            .filter(element -> includeInactive || isPlayerActive(element.getPlayer()))
            .filter(element -> element.getPlayer().getLocationSharingEnabled())
            .map(PlayerSpatialElement::getPlayer)
            .limit(MAX_RESULTS_PER_QUERY)
            .collect(Collectors.toList());
        
        long duration = System.currentTimeMillis() - startTime;
        logger.debug("Found {} players within bounds in {}ms", results.size(), duration);
        
        return results;
    }
    
    /**
     * Finds the K nearest players to a coordinate.
     * 
     * @param gameId The game ID to search within
     * @param center The center coordinate
     * @param k Maximum number of results to return
     * @param includeInactive Whether to include inactive players
     * @return List of players sorted by distance (nearest first)
     */
    public List<PlayerDistanceResult> findKNearestPlayers(String gameId, Coordinate center, int k, boolean includeInactive) {
        long startTime = System.currentTimeMillis();
        logger.debug("Finding {} nearest players to {},{} in game {}", 
                    k, center.getLatitude(), center.getLongitude(), gameId);
        
        SpatialIndex<PlayerSpatialElement> index = getOrCreateSpatialIndex(gameId);
        
        // Get more candidates than needed to account for filtering
        int candidateCount = Math.min(k * 3, MAX_RESULTS_PER_QUERY);
        List<ElementDistancePair<PlayerSpatialElement>> candidates = index.findKNearest(center, candidateCount);
        
        List<PlayerDistanceResult> results = candidates.stream()
            .filter(pair -> includeInactive || isPlayerActive(pair.getElement().getPlayer()))
            .filter(pair -> pair.getElement().getPlayer().getLocationSharingEnabled())
            .map(pair -> new PlayerDistanceResult(pair.getElement().getPlayer(), pair.getDistance()))
            .limit(k)
            .collect(Collectors.toList());
        
        long duration = System.currentTimeMillis() - startTime;
        logger.debug("Found {} nearest players in {}ms", results.size(), duration);
        
        return results;
    }
    
    /**
     * Finds all players within a polygon boundary.
     * 
     * @param gameId The game ID to search within
     * @param polygon List of coordinates defining the polygon boundary
     * @param includeInactive Whether to include inactive players
     * @return List of players within the polygon
     */
    public List<Player> findPlayersWithinPolygon(String gameId, List<Coordinate> polygon, boolean includeInactive) {
        long startTime = System.currentTimeMillis();
        logger.debug("Finding players within polygon with {} vertices in game {}", polygon.size(), gameId);
        
        SpatialIndex<PlayerSpatialElement> index = getOrCreateSpatialIndex(gameId);
        List<PlayerSpatialElement> spatialElements = index.findWithinPolygon(polygon);
        
        List<Player> results = spatialElements.stream()
            .filter(element -> includeInactive || isPlayerActive(element.getPlayer()))
            .filter(element -> element.getPlayer().getLocationSharingEnabled())
            .map(PlayerSpatialElement::getPlayer)
            .limit(MAX_RESULTS_PER_QUERY)
            .collect(Collectors.toList());
        
        long duration = System.currentTimeMillis() - startTime;
        logger.debug("Found {} players within polygon in {}ms", results.size(), duration);
        
        return results;
    }
    
    /**
     * Generates a spatial grid for heatmap visualization.
     * 
     * @param gameId The game ID
     * @param bounds The bounding box for the grid
     * @param gridResolution The resolution of the grid
     * @return Heatmap data as a 2D array
     */
    public HeatmapData generatePlayerHeatmap(String gameId, BoundingBox bounds, int gridResolution) {
        long startTime = System.currentTimeMillis();
        logger.debug("Generating player heatmap for game {} with resolution {}", gameId, gridResolution);
        
        List<Player> players = findPlayersWithinBounds(gameId, bounds, true);
        
        double latStep = (bounds.getNorthEast().getLatitude() - bounds.getSouthWest().getLatitude()) / gridResolution;
        double lonStep = (bounds.getNorthEast().getLongitude() - bounds.getSouthWest().getLongitude()) / gridResolution;
        
        double[][] heatmapData = new double[gridResolution][gridResolution];
        
        for (Player player : players) {
            if (player.getLatitude() == null || player.getLongitude() == null) {
                continue;
            }
            
            int latIndex = (int) ((player.getLatitude() - bounds.getSouthWest().getLatitude()) / latStep);
            int lonIndex = (int) ((player.getLongitude() - bounds.getSouthWest().getLongitude()) / lonStep);
            
            if (latIndex >= 0 && latIndex < gridResolution && lonIndex >= 0 && lonIndex < gridResolution) {
                heatmapData[latIndex][lonIndex] += 1.0;
            }
        }
        
        // Apply smoothing and normalization
        heatmapData = applySmoothingFilter(heatmapData);
        heatmapData = normalizeHeatmapData(heatmapData);
        
        long duration = System.currentTimeMillis() - startTime;
        logger.debug("Generated heatmap in {}ms", duration);
        
        return new HeatmapData(heatmapData, bounds, gridResolution);
    }
    
    /**
     * Gets performance statistics for the spatial indexes.
     * 
     * @param gameId The game ID (optional, if null returns aggregate stats)
     * @return Performance statistics
     */
    public GeospatialPerformanceStats getPerformanceStats(String gameId) {
        GeospatialPerformanceStats stats = new GeospatialPerformanceStats();
        
        if (gameId != null) {
            SpatialIndex<PlayerSpatialElement> index = gameIndexes.get(gameId);
            if (index != null) {
                SpatialIndex.IndexStatistics indexStats = index.getStatistics();
                stats.addGameStats(gameId, indexStats);
            }
        } else {
            // Aggregate stats for all games
            for (Map.Entry<String, SpatialIndex<PlayerSpatialElement>> entry : gameIndexes.entrySet()) {
                SpatialIndex.IndexStatistics indexStats = entry.getValue().getStatistics();
                stats.addGameStats(entry.getKey(), indexStats);
            }
        }
        
        return stats;
    }
    
    /**
     * Forces a refresh of the spatial index for a game.
     * 
     * @param gameId The game ID to refresh
     */
    public void refreshSpatialIndex(String gameId) {
        logger.info("Manually refreshing spatial index for game {}", gameId);
        gameIndexes.remove(gameId);
        indexLastUpdated.remove(gameId);
        getOrCreateSpatialIndex(gameId); // This will rebuild the index
    }
    
    /**
     * Clears all spatial indexes (useful for testing or memory management).
     */
    public void clearAllIndexes() {
        logger.info("Clearing all spatial indexes");
        gameIndexes.clear();
        indexLastUpdated.clear();
    }
    
    /**
     * Shuts down the background refresh service.
     */
    public void shutdown() {
        logger.info("Shutting down enhanced geospatial query service");
        refreshService.shutdown();
        try {
            if (!refreshService.awaitTermination(5, TimeUnit.SECONDS)) {
                refreshService.shutdownNow();
            }
        } catch (InterruptedException e) {
            refreshService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // Private helper methods
    
    private SpatialIndex<PlayerSpatialElement> getOrCreateSpatialIndex(String gameId) {
        SpatialIndex<PlayerSpatialElement> index = gameIndexes.get(gameId);
        Long lastUpdated = indexLastUpdated.get(gameId);
        
        // Check if index needs refresh
        if (index == null || lastUpdated == null || 
            System.currentTimeMillis() - lastUpdated > CACHE_REFRESH_INTERVAL_MS) {
            
            synchronized (this) {
                // Double-check pattern
                index = gameIndexes.get(gameId);
                lastUpdated = indexLastUpdated.get(gameId);
                
                if (index == null || lastUpdated == null || 
                    System.currentTimeMillis() - lastUpdated > CACHE_REFRESH_INTERVAL_MS) {
                    
                    index = buildSpatialIndex(gameId);
                    gameIndexes.put(gameId, index);
                    indexLastUpdated.put(gameId, System.currentTimeMillis());
                }
            }
        }
        
        return index;
    }
    
    private SpatialIndex<PlayerSpatialElement> buildSpatialIndex(String gameId) {
        long startTime = System.currentTimeMillis();
        logger.debug("Building spatial index for game {}", gameId);
        
        List<Player> players = playerDao.getPlayersByGameId(gameId);
        
        // Calculate bounding box for all players
        BoundingBox bounds = calculatePlayerBounds(players);
        
        // Create spatial index with optimized configuration
        SpatialIndex<PlayerSpatialElement> index = new SpatialIndex<>(
            bounds, SPATIAL_INDEX_MAX_ELEMENTS, SPATIAL_INDEX_MAX_DEPTH, 0.0001);
        
        // Add all players with valid locations
        for (Player player : players) {
            if (player.getLatitude() != null && player.getLongitude() != null) {
                PlayerSpatialElement element = new PlayerSpatialElement(player);
                index.insert(element);
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        SpatialIndex.IndexStatistics stats = index.getStatistics();
        logger.info("Built spatial index for game {} with {} players in {}ms. Stats: {}", 
                   gameId, stats.totalElements, duration, stats);
        
        return index;
    }
    
    private BoundingBox calculatePlayerBounds(List<Player> players) {
        if (players.isEmpty()) {
            // Default bounds if no players
            return new BoundingBox(new Coordinate(-90, -180), new Coordinate(90, 180));
        }
        
        double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
        boolean hasValidLocation = false;
        
        for (Player player : players) {
            if (player.getLatitude() != null && player.getLongitude() != null) {
                minLat = Math.min(minLat, player.getLatitude());
                maxLat = Math.max(maxLat, player.getLatitude());
                minLon = Math.min(minLon, player.getLongitude());
                maxLon = Math.max(maxLon, player.getLongitude());
                hasValidLocation = true;
            }
        }
        
        if (!hasValidLocation) {
            // Default bounds if no valid locations
            return new BoundingBox(new Coordinate(-90, -180), new Coordinate(90, 180));
        }
        
        // Add small padding to bounds
        double latPadding = Math.max(0.001, (maxLat - minLat) * 0.1);
        double lonPadding = Math.max(0.001, (maxLon - minLon) * 0.1);
        
        return new BoundingBox(
            new Coordinate(minLat - latPadding, minLon - lonPadding),
            new Coordinate(maxLat + latPadding, maxLon + lonPadding)
        );
    }
    
    private boolean isPlayerActive(Player player) {
        return player.getStatus() != null && 
               (player.getStatus().toString().equals("ACTIVE") || 
                player.getStatus().toString().equals("ALIVE"));
    }
    
    private void startBackgroundRefresh() {
        refreshService.scheduleAtFixedRate(() -> {
            try {
                // Clean up old indexes
                long cutoffTime = System.currentTimeMillis() - (CACHE_REFRESH_INTERVAL_MS * 3);
                indexLastUpdated.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
                gameIndexes.entrySet().removeIf(entry -> !indexLastUpdated.containsKey(entry.getKey()));
                
                logger.debug("Background cleanup completed. Active indexes: {}", gameIndexes.size());
            } catch (RuntimeException e) {
                logger.error("Error during background refresh", e);
            }
        }, CACHE_REFRESH_INTERVAL_MS, CACHE_REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    private double[][] applySmoothingFilter(double[][] data) {
        int rows = data.length;
        int cols = data[0].length;
        double[][] smoothed = new double[rows][cols];
        
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                double sum = 0;
                int count = 0;
                
                // 3x3 kernel
                for (int di = -1; di <= 1; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        int ni = i + di;
                        int nj = j + dj;
                        if (ni >= 0 && ni < rows && nj >= 0 && nj < cols) {
                            sum += data[ni][nj];
                            count++;
                        }
                    }
                }
                
                smoothed[i][j] = count > 0 ? sum / count : 0;
            }
        }
        
        return smoothed;
    }
    
    private double[][] normalizeHeatmapData(double[][] data) {
        double max = 0;
        for (double[] row : data) {
            for (double value : row) {
                max = Math.max(max, value);
            }
        }
        
        if (max == 0) return data;
        
        double[][] normalized = new double[data.length][data[0].length];
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                normalized[i][j] = data[i][j] / max;
            }
        }
        
        return normalized;
    }
    
    // Inner classes and data structures
    
    /**
     * Wrapper class to make Player objects compatible with SpatialIndex.
     */
    private static class PlayerSpatialElement implements SpatialElement {
        private final Player player;
        private final Coordinate location;
        
        public PlayerSpatialElement(Player player) {
            this.player = player;
            this.location = new Coordinate(player.getLatitude(), player.getLongitude());
        }
        
        @Override
        public Coordinate getLocation() {
            return location;
        }
        
        @Override
        public String getId() {
            return player.getPlayerID();
        }
        
        public Player getPlayer() {
            return player;
        }
    }
    
    /**
     * Result class for player distance queries.
     */
    public static class PlayerDistanceResult {
        private final Player player;
        private final double distance;
        
        public PlayerDistanceResult(Player player, double distance) {
            this.player = player;
            this.distance = distance;
        }
        
        public Player getPlayer() { return player; }
        public double getDistance() { return distance; }
        
        @Override
        public String toString() {
            return String.format("PlayerDistanceResult[player=%s, distance=%.2fm]", 
                               player.getPlayerID(), distance);
        }
    }
    
    /**
     * Heatmap data structure.
     */
    public static class HeatmapData {
        private final double[][] data;
        private final BoundingBox bounds;
        private final int resolution;
        
        public HeatmapData(double[][] data, BoundingBox bounds, int resolution) {
            this.data = data;
            this.bounds = bounds;
            this.resolution = resolution;
        }
        
        public double[][] getData() { return data; }
        public BoundingBox getBounds() { return bounds; }
        public int getResolution() { return resolution; }
    }
    
    /**
     * Performance statistics for geospatial operations.
     */
    public static class GeospatialPerformanceStats {
        private final Map<String, SpatialIndex.IndexStatistics> gameStats = new HashMap<>();
        private int totalGames = 0;
        private int totalElements = 0;
        
        public void addGameStats(String gameId, SpatialIndex.IndexStatistics stats) {
            gameStats.put(gameId, stats);
            totalGames++;
            totalElements += stats.totalElements;
        }
        
        public Map<String, SpatialIndex.IndexStatistics> getGameStats() { return gameStats; }
        public int getTotalGames() { return totalGames; }
        public int getTotalElements() { return totalElements; }
        
        @Override
        public String toString() {
            return String.format("GeospatialPerformanceStats[totalGames=%d, totalElements=%d]", 
                               totalGames, totalElements);
        }
    }
} 