package com.assassin.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.model.Coordinate;
import com.assassin.util.GeoUtils;

/**
 * Manages geofencing functionality for game boundaries and handles boundary crossing events.
 * This class is responsible for:
 * 1. Creating geofences from game boundary definitions
 * 2. Managing monitoring of player positions relative to boundaries
 * 3. Detecting when players cross boundaries
 * 4. Triggering events when players enter/exit game areas
 */
public class GeofenceManager {
    private static final Logger logger = LoggerFactory.getLogger(GeofenceManager.class);
    
    // Distance in meters that determines when a player is approaching a boundary
    private static final double BOUNDARY_APPROACH_THRESHOLD_METERS = 50.0;
    
    private final MapConfigurationService mapConfigurationService;
    
    // Cache of player locations, mapped by playerId -> last known coordinate
    private final Map<String, Coordinate> playerLocationCache;
    
    // Cache of player boundary status, mapped by playerId+gameId -> boolean (true if inside boundary)
    private final Map<String, Boolean> playerBoundaryStatusCache;
    
    // Listeners for boundary crossing events, mapped by gameId -> (playerId, listener)
    private final Map<String, Map<String, Consumer<GeofenceEvent>>> boundaryEventListeners;
    
    /**
     * Event types for geofence boundary crossings
     */
    public enum GeofenceEventType {
        ENTER_BOUNDARY,
        EXIT_BOUNDARY,
        APPROACHING_BOUNDARY
    }
    
    /**
     * Event data for geofence events
     */
    public static class GeofenceEvent {
        private final String gameId;
        private final String playerId;
        private final Coordinate playerLocation;
        private final GeofenceEventType eventType;
        private final double distanceToBoundary;
        
        public GeofenceEvent(String gameId, String playerId, Coordinate playerLocation, 
                            GeofenceEventType eventType, double distanceToBoundary) {
            this.gameId = gameId;
            this.playerId = playerId;
            this.playerLocation = playerLocation;
            this.eventType = eventType;
            this.distanceToBoundary = distanceToBoundary;
        }
        
        public String getGameId() {
            return gameId;
        }
        
        public String getPlayerId() {
            return playerId;
        }
        
        public Coordinate getPlayerLocation() {
            return playerLocation;
        }
        
        public GeofenceEventType getEventType() {
            return eventType;
        }
        
        public double getDistanceToBoundary() {
            return distanceToBoundary;
        }
    }
    
    /**
     * Creates a new GeofenceManager with the necessary dependencies.
     * 
     * @param mapConfigurationService For retrieving game boundaries
     */
    public GeofenceManager(MapConfigurationService mapConfigurationService) {
        this.mapConfigurationService = mapConfigurationService;
        this.playerLocationCache = new ConcurrentHashMap<>();
        this.playerBoundaryStatusCache = new ConcurrentHashMap<>();
        this.boundaryEventListeners = new ConcurrentHashMap<>();
    }
    
    /**
     * Updates a player's location and checks for any boundary crossing events.
     * 
     * @param gameId The game ID
     * @param playerId The player ID
     * @param newLocation The player's new location
     * @return Optional containing a GeofenceEvent if a boundary event occurred, empty otherwise
     */
    public Optional<GeofenceEvent> updatePlayerLocation(String gameId, String playerId, Coordinate newLocation) {
        if (gameId == null || playerId == null || newLocation == null) {
            logger.warn("Cannot update player location with null parameters: gameId={}, playerId={}, location={}", 
                        gameId, playerId, newLocation);
            return Optional.empty();
        }
        
        logger.debug("Updating location for player {} in game {} to ({}, {})", 
                    playerId, gameId, newLocation.getLatitude(), newLocation.getLongitude());
        
        // Store previous location and update cache
        Coordinate previousLocation = playerLocationCache.put(playerId, newLocation);
        
        // Check if the player is within the game boundary
        boolean isInBoundary = mapConfigurationService.isCoordinateInGameBoundary(gameId, newLocation);
        
        // Generate a composite key for player+game boundary status
        String boundaryStatusKey = generateBoundaryStatusKey(gameId, playerId);
        Boolean wasInBoundary = playerBoundaryStatusCache.get(boundaryStatusKey);
        
        // If we've never seen this player before, just update the status and return
        if (wasInBoundary == null) {
            playerBoundaryStatusCache.put(boundaryStatusKey, isInBoundary);
            logger.debug("First location update for player {} in game {}, is inside boundary: {}", 
                        playerId, gameId, isInBoundary);
            return Optional.empty();
        }
        
        // Check if boundary status has changed
        if (wasInBoundary != isInBoundary) {
            playerBoundaryStatusCache.put(boundaryStatusKey, isInBoundary);
            
            GeofenceEventType eventType = isInBoundary 
                ? GeofenceEventType.ENTER_BOUNDARY 
                : GeofenceEventType.EXIT_BOUNDARY;
                
            // Calculate approximate distance to boundary (simplified)
            double distanceToBoundary = calculateApproximateDistanceToBoundary(gameId, newLocation);
            
            GeofenceEvent event = new GeofenceEvent(gameId, playerId, newLocation, eventType, distanceToBoundary);
            
            // Trigger listeners
            triggerBoundaryEvent(event);
            
            logger.info("Player {} has {} the boundary for game {}", 
                        playerId, isInBoundary ? "entered" : "exited", gameId);
                        
            return Optional.of(event);
        }
        
        // If player is inside but approaching boundary, warn them
        if (isInBoundary) {
            double distanceToBoundary = calculateApproximateDistanceToBoundary(gameId, newLocation);
            if (distanceToBoundary <= BOUNDARY_APPROACH_THRESHOLD_METERS) {
                GeofenceEvent event = new GeofenceEvent(
                    gameId, playerId, newLocation, GeofenceEventType.APPROACHING_BOUNDARY, distanceToBoundary);
                
                // Trigger approach warning
                triggerBoundaryEvent(event);
                
                logger.debug("Player {} is approaching boundary for game {}, distance: {}m", 
                            playerId, gameId, String.format("%.2f", distanceToBoundary));
                            
                return Optional.of(event);
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Calculates an approximate distance from a coordinate to the nearest point on the game boundary.
     * This is a simplified implementation that could be improved for accuracy and performance.
     * 
     * @param gameId The game ID
     * @param location The coordinate to check
     * @return Approximate distance in meters to the boundary
     */
    private double calculateApproximateDistanceToBoundary(String gameId, Coordinate location) {
        // Get the game boundary
        List<Coordinate> boundary = mapConfigurationService.getGameBoundary(gameId);
        
        // If we're outside the boundary, we use a negative distance
        boolean isInside = GeoUtils.isPointInBoundary(location, boundary);
        
        // Find the minimum distance to any boundary segment
        double minDistance = Double.MAX_VALUE;
        
        // Iterate through boundary segments
        for (int i = 0; i < boundary.size(); i++) {
            Coordinate p1 = boundary.get(i);
            Coordinate p2 = boundary.get((i + 1) % boundary.size()); // Loop back to start for last segment
            
            // Calculate distance to this boundary segment
            double segmentDistance = GeoUtils.distanceToLineSegment(
                location.getLatitude(), location.getLongitude(),
                p1.getLatitude(), p1.getLongitude(),
                p2.getLatitude(), p2.getLongitude());
                
            minDistance = Math.min(minDistance, segmentDistance);
        }
        
        // Return negative distance if outside (to differentiate inside/outside)
        return isInside ? minDistance : -minDistance;
    }
    
    /**
     * Registers a listener for boundary crossing events for a specific player in a specific game.
     * 
     * @param gameId The game ID
     * @param playerId The player ID
     * @param listener Consumer function to handle GeofenceEvent objects
     */
    public void registerBoundaryEventListener(String gameId, String playerId, Consumer<GeofenceEvent> listener) {
        if (gameId == null || playerId == null || listener == null) {
            logger.warn("Cannot register boundary event listener with null parameters");
            return;
        }
        
        boundaryEventListeners
            .computeIfAbsent(gameId, k -> new ConcurrentHashMap<>())
            .put(playerId, listener);
            
        logger.debug("Registered boundary event listener for player {} in game {}", playerId, gameId);
    }
    
    /**
     * Unregisters a boundary event listener for a specific player in a specific game.
     * 
     * @param gameId The game ID
     * @param playerId The player ID
     */
    public void unregisterBoundaryEventListener(String gameId, String playerId) {
        if (gameId == null || playerId == null) {
            return;
        }
        
        Map<String, Consumer<GeofenceEvent>> gameListeners = boundaryEventListeners.get(gameId);
        if (gameListeners != null) {
            gameListeners.remove(playerId);
            logger.debug("Unregistered boundary event listener for player {} in game {}", playerId, gameId);
        }
    }
    
    /**
     * Clears all registered listeners and cached data for a specific game.
     * Should be called when a game ends or when geofencing is no longer needed.
     * 
     * @param gameId The game ID to clear
     */
    public void clearGameGeofences(String gameId) {
        if (gameId == null) {
            return;
        }
        
        // Remove all listeners for this game
        boundaryEventListeners.remove(gameId);
        
        // Remove all boundary status cache entries for this game
        playerBoundaryStatusCache.keySet().removeIf(key -> key.startsWith(gameId + ":"));
        
        logger.info("Cleared all geofence data for game {}", gameId);
    }
    
    /**
     * Triggers appropriate boundary event listeners for an event.
     * 
     * @param event The boundary crossing event
     */
    private void triggerBoundaryEvent(GeofenceEvent event) {
        Map<String, Consumer<GeofenceEvent>> gameListeners = boundaryEventListeners.get(event.getGameId());
        if (gameListeners != null) {
            // Trigger specific player listener if exists
            Consumer<GeofenceEvent> playerListener = gameListeners.get(event.getPlayerId());
            if (playerListener != null) {
                playerListener.accept(event);
            }
        }
    }
    
    /**
     * Generates a composite key for the player boundary status cache.
     * 
     * @param gameId The game ID
     * @param playerId The player ID
     * @return A composite key string
     */
    private String generateBoundaryStatusKey(String gameId, String playerId) {
        return gameId + ":" + playerId;
    }
} 