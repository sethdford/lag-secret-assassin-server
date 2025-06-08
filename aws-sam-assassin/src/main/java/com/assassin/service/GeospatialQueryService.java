package com.assassin.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.model.Coordinate;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;
import com.assassin.util.GeoUtils;

/**
 * Service for optimized geospatial queries and spatial database operations.
 * Provides efficient spatial indexing and querying capabilities for the interactive map.
 */
public class GeospatialQueryService {

    private static final Logger logger = LoggerFactory.getLogger(GeospatialQueryService.class);
    
    private final DynamoDbPlayerDao playerDao;
    private final LocationService locationService;
    
    // Spatial indexing constants
    private static final double GRID_SIZE_DEGREES = 0.001; // ~100m grid cells
    private static final int MAX_RESULTS_PER_QUERY = 100;
    
    public GeospatialQueryService() {
        this.playerDao = new DynamoDbPlayerDao();
        this.locationService = new LocationService();
    }
    
    public GeospatialQueryService(DynamoDbPlayerDao playerDao, LocationService locationService) {
        this.playerDao = playerDao;
        this.locationService = locationService;
    }

    /**
     * Find all players within a specified radius of a given coordinate.
     *
     * @param gameId The game ID to search within
     * @param center The center coordinate for the search
     * @param radiusMeters The search radius in meters
     * @param includeInactive Whether to include inactive players
     * @return List of players within the radius
     */
    public List<Player> findPlayersWithinRadius(String gameId, Coordinate center, double radiusMeters, boolean includeInactive) {
        logger.debug("Finding players within {}m of {},{} in game {}", radiusMeters, center.getLatitude(), center.getLongitude(), gameId);
        
        List<Player> allPlayers = playerDao.getPlayersByGameId(gameId);
        List<Player> nearbyPlayers = new ArrayList<>();
        
        for (Player player : allPlayers) {
            // Skip players without location data
            if (player.getLatitude() == null || player.getLongitude() == null) {
                continue;
            }
            
            // Filter by status if needed
            if (!includeInactive && !isPlayerActive(player)) {
                continue;
            }
            
            // Check privacy settings
            if (!player.getLocationSharingEnabled()) {
                continue;
            }
            
            Coordinate playerLocation = new Coordinate(player.getLatitude(), player.getLongitude());
            double distance = GeoUtils.calculateDistance(center, playerLocation);
            
            if (distance <= radiusMeters) {
                nearbyPlayers.add(player);
            }
        }
        
        logger.debug("Found {} players within radius", nearbyPlayers.size());
        return nearbyPlayers.stream()
                .limit(MAX_RESULTS_PER_QUERY)
                .collect(Collectors.toList());
    }

    /**
     * Generate a spatial grid for efficient proximity queries.
     * Divides the game area into grid cells for faster spatial lookups.
     *
     * @param gameId The game ID
     * @param bounds The bounding box for the grid
     * @return Map of grid cell IDs to player lists
     */
    public Map<String, List<Player>> generateSpatialGrid(String gameId, BoundingBox bounds) {
        logger.debug("Generating spatial grid for game {} with bounds {}", gameId, bounds);
        
        Map<String, List<Player>> spatialGrid = new HashMap<>();
        List<Player> activePlayers = getActivePlayersWithLocation(gameId);
        
        for (Player player : activePlayers) {
            String gridCellId = calculateGridCellId(player.getLatitude(), player.getLongitude());
            spatialGrid.computeIfAbsent(gridCellId, k -> new ArrayList<>()).add(player);
        }
        
        logger.debug("Generated spatial grid with {} cells containing {} players", spatialGrid.size(), activePlayers.size());
        return spatialGrid;
    }

    /**
     * Find the nearest players to a given coordinate.
     *
     * @param gameId The game ID
     * @param center The center coordinate
     * @param maxResults Maximum number of results to return
     * @return List of players sorted by distance (nearest first)
     */
    public List<PlayerDistanceResult> findNearestPlayers(String gameId, Coordinate center, int maxResults) {
        logger.debug("Finding {} nearest players to {},{} in game {}", maxResults, center.getLatitude(), center.getLongitude(), gameId);
        
        List<Player> activePlayers = getActivePlayersWithLocation(gameId);
        List<PlayerDistanceResult> results = new ArrayList<>();
        
        for (Player player : activePlayers) {
            if (!player.getLocationSharingEnabled()) {
                continue;
            }
            
            Coordinate playerLocation = new Coordinate(player.getLatitude(), player.getLongitude());
            double distance = GeoUtils.calculateDistance(center, playerLocation);
            
            results.add(new PlayerDistanceResult(player, distance));
        }
        
        // Sort by distance and limit results
        return results.stream()
                .sorted((a, b) -> Double.compare(a.getDistance(), b.getDistance()))
                .limit(Math.min(maxResults, MAX_RESULTS_PER_QUERY))
                .collect(Collectors.toList());
    }

    /**
     * Generate heatmap data for player activity visualization.
     *
     * @param gameId The game ID
     * @param bounds The bounding box for the heatmap
     * @param gridResolution The resolution of the heatmap grid
     * @return Heatmap data as a 2D array of activity intensities
     */
    public HeatmapData generateActivityHeatmap(String gameId, BoundingBox bounds, int gridResolution) {
        logger.debug("Generating activity heatmap for game {} with resolution {}", gameId, gridResolution);
        
        double latStep = (bounds.getNorthEast().getLatitude() - bounds.getSouthWest().getLatitude()) / gridResolution;
        double lonStep = (bounds.getNorthEast().getLongitude() - bounds.getSouthWest().getLongitude()) / gridResolution;
        
        double[][] heatmapData = new double[gridResolution][gridResolution];
        List<Player> activePlayers = getActivePlayersWithLocation(gameId);
        
        for (Player player : activePlayers) {
            if (!player.getLocationSharingEnabled()) {
                continue;
            }
            
            // Calculate grid position
            int latIndex = (int) ((player.getLatitude() - bounds.getSouthWest().getLatitude()) / latStep);
            int lonIndex = (int) ((player.getLongitude() - bounds.getSouthWest().getLongitude()) / lonStep);
            
            // Ensure indices are within bounds
            if (latIndex >= 0 && latIndex < gridResolution && lonIndex >= 0 && lonIndex < gridResolution) {
                heatmapData[latIndex][lonIndex] += 1.0;
            }
        }
        
        // Apply smoothing and normalization
        heatmapData = applySmoothingFilter(heatmapData);
        heatmapData = normalizeHeatmapData(heatmapData);
        
        logger.debug("Generated heatmap with {}x{} resolution", gridResolution, gridResolution);
        return new HeatmapData(heatmapData, bounds, gridResolution);
    }

    /**
     * Perform a spatial range query to find all players within a rectangular area.
     *
     * @param gameId The game ID
     * @param bounds The bounding rectangle
     * @return List of players within the bounds
     */
    public List<Player> spatialRangeQuery(String gameId, BoundingBox bounds) {
        logger.debug("Performing spatial range query for game {} with bounds {}", gameId, bounds);
        
        List<Player> activePlayers = getActivePlayersWithLocation(gameId);
        List<Player> playersInBounds = new ArrayList<>();
        
        for (Player player : activePlayers) {
            if (!player.getLocationSharingEnabled()) {
                continue;
            }
            
            if (isPlayerInBounds(player, bounds)) {
                playersInBounds.add(player);
            }
        }
        
        logger.debug("Found {} players within bounds", playersInBounds.size());
        return playersInBounds;
    }

    // Helper methods

    private List<Player> getActivePlayersWithLocation(String gameId) {
        return playerDao.getPlayersByGameId(gameId).stream()
                .filter(this::isPlayerActive)
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .collect(Collectors.toList());
    }

    private boolean isPlayerActive(Player player) {
        String status = player.getStatus();
        return PlayerStatus.ACTIVE.name().equals(status) || 
               PlayerStatus.PENDING_DEATH.name().equals(status);
    }

    private String calculateGridCellId(double latitude, double longitude) {
        int latGrid = (int) (latitude / GRID_SIZE_DEGREES);
        int lonGrid = (int) (longitude / GRID_SIZE_DEGREES);
        return latGrid + "," + lonGrid;
    }

    private boolean isPlayerInBounds(Player player, BoundingBox bounds) {
        double lat = player.getLatitude();
        double lon = player.getLongitude();
        
        return lat >= bounds.getSouthWest().getLatitude() &&
               lat <= bounds.getNorthEast().getLatitude() &&
               lon >= bounds.getSouthWest().getLongitude() &&
               lon <= bounds.getNorthEast().getLongitude();
    }

    private double[][] applySmoothingFilter(double[][] data) {
        int rows = data.length;
        int cols = data[0].length;
        double[][] smoothed = new double[rows][cols];
        
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                double sum = 0;
                int count = 0;
                
                // Apply 3x3 smoothing kernel
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
        
        // Find maximum value
        for (double[] row : data) {
            for (double value : row) {
                max = Math.max(max, value);
            }
        }
        
        // Normalize to 0-1 range
        if (max > 0) {
            for (int i = 0; i < data.length; i++) {
                for (int j = 0; j < data[i].length; j++) {
                    data[i][j] /= max;
                }
            }
        }
        
        return data;
    }

    // Data classes

    public static class PlayerDistanceResult {
        private final Player player;
        private final double distance;

        public PlayerDistanceResult(Player player, double distance) {
            this.player = player;
            this.distance = distance;
        }

        public Player getPlayer() { return player; }
        public double getDistance() { return distance; }
    }

    public static class BoundingBox {
        private final Coordinate southWest;
        private final Coordinate northEast;

        public BoundingBox(Coordinate southWest, Coordinate northEast) {
            this.southWest = southWest;
            this.northEast = northEast;
        }

        public Coordinate getSouthWest() { return southWest; }
        public Coordinate getNorthEast() { return northEast; }

        @Override
        public String toString() {
            return String.format("BoundingBox{SW=%s, NE=%s}", southWest, northEast);
        }
    }

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
} 