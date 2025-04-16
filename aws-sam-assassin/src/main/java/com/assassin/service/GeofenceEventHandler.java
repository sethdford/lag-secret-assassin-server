package com.assassin.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbGameZoneStateDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.DynamoDbSafeZoneDao;
import com.assassin.dao.PlayerDao;
import com.assassin.service.GeofenceManager.GeofenceEvent;

/**
 * Handles geofence events triggered by the GeofenceManager.
 * Responsible for executing business logic in response to boundary crossing events.
 */
public class GeofenceEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(GeofenceEventHandler.class);
    
    private final PlayerDao playerDao;
    private final GeofenceManager geofenceManager;
    private final Map<String, Long> playerWarnings; // playerId+gameId -> timestamp of last warning
    
    // Constants for event handling
    private static final long WARNING_COOLDOWN_MS = 60000; // 1 minute between warnings
    private static final double SEVERE_WARNING_THRESHOLD_METERS = 10.0; // Distance for severe warnings
    
    /**
     * Creates a new GeofenceEventHandler with the necessary dependencies.
     * 
     * @param playerDao For updating player state
     * @param geofenceManager For registering event listeners
     */
    public GeofenceEventHandler(PlayerDao playerDao, GeofenceManager geofenceManager) {
        this.playerDao = playerDao;
        this.geofenceManager = geofenceManager;
        this.playerWarnings = new ConcurrentHashMap<>();
    }
    
    /**
     * Default constructor that initializes with default dependencies.
     */
    public GeofenceEventHandler() {
        this(new DynamoDbPlayerDao(), new GeofenceManager(
             new MapConfigurationService(
                 new DynamoDbGameDao(), 
                 new DynamoDbGameZoneStateDao(),
                 new DynamoDbSafeZoneDao(),
                 new ShrinkingZoneService(
                     new DynamoDbGameDao(),
                     new DynamoDbGameZoneStateDao(),
                     new DynamoDbPlayerDao()
                 )
             )));
    }
    
    /**
     * Registers this handler to receive boundary events for a specific player in a game.
     * 
     * @param gameId The game ID
     * @param playerId The player ID
     */
    public void registerForBoundaryEvents(String gameId, String playerId) {
        logger.debug("Registering for boundary events for player {} in game {}", playerId, gameId);
        geofenceManager.registerBoundaryEventListener(gameId, playerId, this::handleBoundaryEvent);
    }
    
    /**
     * Unregisters this handler from receiving boundary events for a specific player in a game.
     * 
     * @param gameId The game ID
     * @param playerId The player ID
     */
    public void unregisterFromBoundaryEvents(String gameId, String playerId) {
        logger.debug("Unregistering from boundary events for player {} in game {}", playerId, gameId);
        geofenceManager.unregisterBoundaryEventListener(gameId, playerId);
    }
    
    /**
     * Handles a boundary event by taking appropriate action based on the event type.
     * 
     * @param event The geofence event to handle
     */
    private void handleBoundaryEvent(GeofenceEvent event) {
        if (event == null) {
            return;
        }
        
        String gameId = event.getGameId();
        String playerId = event.getPlayerId();
        String boundaryKey = generateBoundaryKey(gameId, playerId);
        
        switch (event.getEventType()) {
            case EXIT_BOUNDARY:
                // Player has left the game boundary - send notification, log violation, etc.
                logger.warn("BOUNDARY VIOLATION: Player {} has exited the game boundary for game {}", 
                           playerId, gameId);
                
                // In a real implementation, we might:
                // 1. Update player state to mark them as outside boundary
                // 2. Send push notification to the player
                // 3. Update UI to show warning
                // 4. Apply game penalties if configured
                
                // For now, we'll just log it
                break;
                
            case ENTER_BOUNDARY:
                // Player has entered the game boundary - clear any outstanding warnings
                logger.info("Player {} has entered the game boundary for game {}", 
                           playerId, gameId);
                
                // Clear any warnings for this player
                playerWarnings.remove(boundaryKey);
                
                // In a real implementation, we might:
                // 1. Update player state to mark them as inside boundary
                // 2. Send push notification to the player
                // 3. Update UI to clear warnings
                
                break;
                
            case APPROACHING_BOUNDARY:
                // Player is getting close to boundary - warn them
                // Only send warning if we haven't recently sent one (prevent spam)
                long now = System.currentTimeMillis();
                Long lastWarningTime = playerWarnings.get(boundaryKey);
                
                if (lastWarningTime == null || (now - lastWarningTime) > WARNING_COOLDOWN_MS) {
                    double distance = event.getDistanceToBoundary();
                    boolean isSevere = distance <= SEVERE_WARNING_THRESHOLD_METERS;
                    
                    logger.info("BOUNDARY WARNING: Player {} is {} the game boundary for game {}, distance: {}m", 
                               playerId, 
                               isSevere ? "dangerously close to" : "approaching",
                               gameId, 
                               String.format("%.2f", distance));
                    
                    // Update warning timestamp
                    playerWarnings.put(boundaryKey, now);
                    
                    // In a real implementation, we might:
                    // 1. Send push notification to the player with distance
                    // 2. Update UI to show warning with distance indicator
                    // 3. Trigger haptic feedback or sound on mobile device
                }
                break;
                
            default:
                logger.warn("Unknown boundary event type: {}", event.getEventType());
                break;
        }
    }
    
    /**
     * Generates a composite key for the player warnings map.
     * 
     * @param gameId The game ID
     * @param playerId The player ID
     * @return A composite key string
     */
    private String generateBoundaryKey(String gameId, String playerId) {
        return gameId + ":" + playerId;
    }
} 