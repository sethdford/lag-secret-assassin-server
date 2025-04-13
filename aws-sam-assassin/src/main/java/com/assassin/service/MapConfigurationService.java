package com.assassin.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.GameDao;
import com.assassin.dao.GameZoneStateDao;
import com.assassin.dao.SafeZoneDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GameStateException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.util.GeoUtils;

/**
 * Service for managing map configurations and game boundaries.
 * Provides methods for retrieving and validating map configurations for different game modes.
 */
public class MapConfigurationService {
    private static final Logger logger = LoggerFactory.getLogger(MapConfigurationService.class);
    
    private final GameDao gameDao;
    private final GameZoneStateDao gameZoneStateDao;
    private final SafeZoneDao safeZoneDao;
    private final ShrinkingZoneService shrinkingZoneService;
    
    // Cache map boundaries by gameId to reduce database reads
    private final Map<String, List<Coordinate>> gameBoundaryCache;
    
    // Default map boundaries as fallback
    private static final List<Coordinate> DEFAULT_GAME_BOUNDARY;
    
    // Maximum map size (diagonal distance) in meters
    private static final double MAX_MAP_SIZE_METERS = 5000.0;
    
    static {
        // Initialize default game boundary (example: rectangle around San Francisco)
        List<Coordinate> defaultBoundary = new ArrayList<>();
        defaultBoundary.add(new Coordinate(37.808, -122.409)); // North-West
        defaultBoundary.add(new Coordinate(37.808, -122.347)); // North-East
        defaultBoundary.add(new Coordinate(37.735, -122.347)); // South-East
        defaultBoundary.add(new Coordinate(37.735, -122.409)); // South-West
        DEFAULT_GAME_BOUNDARY = Collections.unmodifiableList(defaultBoundary);
    }
    
    /**
     * Constructs a new MapConfigurationService with the necessary dependencies.
     * 
     * @param gameDao For retrieving game configuration
     * @param gameZoneStateDao For retrieving current zone state
     * @param safeZoneDao For retrieving safe zone configuration
     * @param shrinkingZoneService For handling shrinking zone mechanics
     */
    public MapConfigurationService(
            GameDao gameDao, 
            GameZoneStateDao gameZoneStateDao,
            SafeZoneDao safeZoneDao,
            ShrinkingZoneService shrinkingZoneService) {
        this.gameDao = gameDao;
        this.gameZoneStateDao = gameZoneStateDao;
        this.safeZoneDao = safeZoneDao;
        this.shrinkingZoneService = shrinkingZoneService;
        this.gameBoundaryCache = new HashMap<>();
    }
    
    /**
     * Get the game boundary for a specific game.
     * First tries to retrieve from cache, then from database, then falls back to default.
     * 
     * @param gameId The game ID to retrieve boundaries for
     * @return List of coordinates defining the game boundary
     */
    public List<Coordinate> getGameBoundary(String gameId) {
        // Check cache first
        if (gameBoundaryCache.containsKey(gameId)) {
            return gameBoundaryCache.get(gameId);
        }
        
        try {
            // Attempt to retrieve game and its configuration
            Optional<Game> gameOpt = gameDao.getGameById(gameId);
            
            if (gameOpt.isPresent()) {
                Game game = gameOpt.get();
                List<Coordinate> boundary = game.getBoundary();
                
                if (boundary != null && boundary.size() >= 3) {
                    // Cache and return the boundary
                    gameBoundaryCache.put(gameId, boundary);
                    return boundary;
                }
            }
            
            logger.warn("No valid boundary found for game {}, using default", gameId);
            // Fall back to default if no valid boundary found
            gameBoundaryCache.put(gameId, DEFAULT_GAME_BOUNDARY);
            return DEFAULT_GAME_BOUNDARY;
            
        } catch (Exception e) {
            logger.error("Error retrieving game boundary for game {}: {}", gameId, e.getMessage());
            return DEFAULT_GAME_BOUNDARY;
        }
    }
    
    /**
     * Checks if a coordinate is within the game's boundary.
     * 
     * @param gameId Game ID to check boundary against
     * @param coordinate Coordinate to check
     * @return true if the coordinate is within the boundary, false otherwise
     */
    public boolean isCoordinateInGameBoundary(String gameId, Coordinate coordinate) {
        if (coordinate == null) {
            return false;
        }
        
        List<Coordinate> boundary = getGameBoundary(gameId);
        return GeoUtils.isPointInBoundary(coordinate, boundary);
    }
    
    /**
     * Checks if a coordinate is within the active shrinking zone for a game.
     * 
     * @param gameId The game ID to check against
     * @param coordinate The coordinate to validate
     * @return true if the coordinate is in the active zone, false otherwise
     */
    private boolean isCoordinateInActiveZone(String gameId, Coordinate coordinate) {
        try {
            // Get the current zone center and radius
            Optional<Coordinate> centerOpt = shrinkingZoneService.getCurrentZoneCenter(gameId);
            Optional<Double> radiusOpt = shrinkingZoneService.getCurrentZoneRadius(gameId);
            
            if (centerOpt.isEmpty() || radiusOpt.isEmpty()) {
                logger.debug("No active zone found for game {}", gameId);
                return false;
            }
            
            Coordinate center = centerOpt.get();
            double radiusMeters = radiusOpt.get();
            
            // Calculate distance from coordinate to zone center
            double distanceMeters = GeoUtils.calculateDistance(
                coordinate.getLatitude(), coordinate.getLongitude(),
                center.getLatitude(), center.getLongitude());
                
            boolean inZone = distanceMeters <= radiusMeters;
            if (!inZone) {
                logger.debug("Coordinate is outside active zone for game {}. Distance: {}, Zone radius: {}", 
                    gameId, distanceMeters, radiusMeters);
            }
            
            return inZone;
        } catch (GameNotFoundException | GameStateException e) {
            logger.error("Error checking if coordinate is in active zone for game {}: {}", gameId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Validates a coordinate based on basic geographic constraints and game-specific boundaries.
     * 
     * @param gameId The game ID for boundary checking
     * @param coordinate The coordinate to validate
     * @return true if the coordinate is valid, false otherwise
     */
    public boolean validateCoordinate(String gameId, Coordinate coordinate) {
        if (coordinate == null) {
            return false;
        }
        
        // Basic geographic validation
        if (!GeoUtils.isValidCoordinate(coordinate.getLatitude(), coordinate.getLongitude())) {
            logger.warn("Invalid coordinate values for game {}: {}", gameId, coordinate);
            return false;
        }
        
        // Check if coordinate is within game boundary
        boolean inGameBoundary = isCoordinateInGameBoundary(gameId, coordinate);
        
        // For active games with shrinking zones, also check if in active zone
        try {
            Optional<Game> gameOpt = gameDao.getGameById(gameId);
            if (gameOpt.isPresent() && "ACTIVE".equals(gameOpt.get().getStatus())) {
                Game game = gameOpt.get();
                
                // If shrinking zone mechanic is enabled for this game
                if (Boolean.TRUE.equals(game.getShrinkingZoneEnabled())) {
                    return isCoordinateInActiveZone(gameId, coordinate);
                }
            }
        } catch (Exception e) {
            logger.error("Error validating coordinate for game {}: {}", gameId, e.getMessage());
        }
        
        // Default to basic game boundary check
        return inGameBoundary;
    }
    
    /**
     * Clears the boundary cache for a specific game or all games.
     * 
     * @param gameId The game ID to clear, or null to clear all
     */
    public void clearBoundaryCache(String gameId) {
        if (gameId == null) {
            gameBoundaryCache.clear();
            logger.debug("Cleared all game boundary caches");
        } else {
            gameBoundaryCache.remove(gameId);
            logger.debug("Cleared boundary cache for game {}", gameId);
        }
    }
    
    /**
     * Gets the maximum allowed map size (diagonal distance) in meters.
     * 
     * @return Maximum map size in meters
     */
    public double getMaxMapSizeMeters() {
        return MAX_MAP_SIZE_METERS;
    }
} 