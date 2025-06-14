package com.assassin.websocket;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.model.Coordinate;
import com.assassin.model.Kill;
import com.assassin.model.Player;
import com.assassin.service.LocationService;
import com.assassin.service.ProximityDetectionService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service responsible for broadcasting real-time game events via WebSocket.
 * Integrates with existing game services to provide live updates to connected clients.
 */
public class WebSocketEventBroadcaster {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventBroadcaster.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final PlayerDao playerDao;
    private final LocationService locationService;
    private final ProximityDetectionService proximityService;
    
    // Reference to the WebSocket server for broadcasting
    private GameWebSocketServer webSocketServer;
    
    /**
     * Default constructor with default dependencies
     */
    public WebSocketEventBroadcaster() {
        this(new DynamoDbPlayerDao(), new LocationService(), new ProximityDetectionService());
    }
    
    /**
     * Constructor for dependency injection
     */
    public WebSocketEventBroadcaster(PlayerDao playerDao, LocationService locationService, 
                                   ProximityDetectionService proximityService) {
        this.playerDao = playerDao;
        this.locationService = locationService;
        this.proximityService = proximityService;
    }
    
    /**
     * Set the WebSocket server reference for broadcasting
     */
    public void setWebSocketServer(GameWebSocketServer webSocketServer) {
        this.webSocketServer = webSocketServer;
    }
    
    /**
     * Broadcast a player location update to relevant players in the same game
     */
    public void broadcastPlayerLocationUpdate(String playerId, Double latitude, Double longitude, 
                                            Double accuracy, String gameId) {
        try {
            // Update the player's location in the backend
            locationService.updatePlayerLocation(playerId, latitude, longitude, accuracy);
            
            // Get the updated player info
            Player player = playerDao.getPlayerById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + playerId));
            
            // Create the broadcast message
            Map<String, Object> payload = new HashMap<>();
            payload.put("playerId", playerId);
            payload.put("playerName", player.getPlayerName());
            payload.put("latitude", latitude);
            payload.put("longitude", longitude);
            payload.put("accuracy", accuracy);
            payload.put("gameId", gameId);
            payload.put("timestamp", Instant.now().toEpochMilli());
            
            WebSocketMessage message = new WebSocketMessage(
                "player_update", 
                payload, 
                Instant.now().toEpochMilli(), 
                null
            );
            
            // Broadcast to players in the same game (excluding the sender)
            broadcastToGamePlayers(gameId, message, playerId);
            
            logger.debug("Broadcasted location update for player {} in game {}", playerId, gameId);
            
        } catch (RuntimeException e) {
            logger.error("Failed to broadcast player location update for player {}: {}", playerId, e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast a kill notification to all players in the game
     */
    public void broadcastKillNotification(Kill kill, String killerName, String victimName) {
        try {
            // Get the game ID from the victim (kills should have game context)
            Player victim = playerDao.getPlayerById(kill.getVictimID())
                .orElseThrow(() -> new PlayerNotFoundException("Victim not found: " + kill.getVictimID()));
            
            String gameId = victim.getGameID();
            
            // Create the broadcast message
            Map<String, Object> payload = new HashMap<>();
            payload.put("killId", kill.getKillerID() + "_" + kill.getTime());
            payload.put("killerId", kill.getKillerID());
            payload.put("killerName", killerName);
            payload.put("victimId", kill.getVictimID());
            payload.put("victimName", victimName);
            payload.put("gameId", gameId);
            payload.put("timestamp", kill.getTime());
            payload.put("verificationMethod", kill.getVerificationMethod());
            
            WebSocketMessage message = new WebSocketMessage(
                "kill_notification", 
                payload, 
                Instant.now().toEpochMilli(), 
                null
            );
            
            // Broadcast to all players in the game
            broadcastToGamePlayers(gameId, message, null);
            
            logger.info("Broadcasted kill notification: {} eliminated {} in game {}", 
                       killerName, victimName, gameId);
            
        } catch (RuntimeException e) {
            logger.error("Failed to broadcast kill notification for kill {}: {}", 
                        kill.getKillerID() + "_" + kill.getTime(), e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast a zone change notification to all players in the game
     */
    public void broadcastZoneChange(String gameId, String zoneType, Map<String, Object> zoneData) {
        try {
            // Create the broadcast message
            Map<String, Object> payload = new HashMap<>();
            payload.put("gameId", gameId);
            payload.put("zoneType", zoneType);
            payload.put("zoneData", zoneData);
            payload.put("timestamp", Instant.now().toEpochMilli());
            
            WebSocketMessage message = new WebSocketMessage(
                "zone_change", 
                payload, 
                Instant.now().toEpochMilli(), 
                null
            );
            
            // Broadcast to all players in the game
            broadcastToGamePlayers(gameId, message, null);
            
            logger.info("Broadcasted zone change notification for game {}: {}", gameId, zoneType);
            
        } catch (RuntimeException e) {
            logger.error("Failed to broadcast zone change for game {}: {}", gameId, e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast a proximity alert to specific players
     */
    public void broadcastProximityAlert(String gameId, String playerId, String targetId, 
                                      double distance, String alertType) {
        try {
            // Create the broadcast message
            Map<String, Object> payload = new HashMap<>();
            payload.put("gameId", gameId);
            payload.put("playerId", playerId);
            payload.put("targetId", targetId);
            payload.put("distance", distance);
            payload.put("alertType", alertType); // "target_nearby" or "hunter_nearby"
            payload.put("timestamp", Instant.now().toEpochMilli());
            
            WebSocketMessage message = new WebSocketMessage(
                "proximity_alert", 
                payload, 
                Instant.now().toEpochMilli(), 
                null
            );
            
            // Send to specific player only
            broadcastToSpecificPlayer(playerId, message);
            
            logger.debug("Broadcasted proximity alert to player {} in game {}: {} at {}m", 
                        playerId, gameId, alertType, distance);
            
        } catch (RuntimeException e) {
            logger.error("Failed to broadcast proximity alert for player {}: {}", playerId, e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast game status updates (game start, end, etc.)
     */
    public void broadcastGameStatusUpdate(String gameId, String status, Map<String, Object> statusData) {
        try {
            // Create the broadcast message
            Map<String, Object> payload = new HashMap<>();
            payload.put("gameId", gameId);
            payload.put("status", status);
            payload.put("statusData", statusData);
            payload.put("timestamp", Instant.now().toEpochMilli());
            
            WebSocketMessage message = new WebSocketMessage(
                "game_status_update", 
                payload, 
                Instant.now().toEpochMilli(), 
                null
            );
            
            // Broadcast to all players in the game
            broadcastToGamePlayers(gameId, message, null);
            
            logger.info("Broadcasted game status update for game {}: {}", gameId, status);
            
        } catch (RuntimeException e) {
            logger.error("Failed to broadcast game status update for game {}: {}", gameId, e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast a message to all players in a specific game
     */
    private void broadcastToGamePlayers(String gameId, WebSocketMessage message, String excludePlayerId) {
        if (webSocketServer == null) {
            logger.warn("WebSocket server not set, cannot broadcast message");
            return;
        }
        
        try {
            // Get all players in the game
            List<Player> gamePlayers = playerDao.getPlayersByGameId(gameId);
            
            // Broadcast to each connected player (except excluded one)
            for (Player player : gamePlayers) {
                if (excludePlayerId != null && player.getPlayerID().equals(excludePlayerId)) {
                    continue; // Skip the excluded player
                }
                
                broadcastToSpecificPlayer(player.getPlayerID(), message);
            }
            
        } catch (RuntimeException e) {
            logger.error("Failed to broadcast to game players for game {}: {}", gameId, e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast a message to a specific player
     */
    private void broadcastToSpecificPlayer(String playerId, WebSocketMessage message) {
        if (webSocketServer == null) {
            logger.warn("WebSocket server not set, cannot broadcast message");
            return;
        }
        
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            
            // Find sessions for this player and send the message
            GameWebSocketServer.sessions.values().forEach(session -> {
                try {
                    String sessionUserId = (String) session.getUserProperties().get("userId");
                    if (playerId.equals(sessionUserId) && session.isOpen()) {
                        session.getBasicRemote().sendText(messageJson);
                        logger.debug("Sent message to player {} via session {}", playerId, session.getId());
                    }
                } catch (IOException e) {
                    logger.error("Failed to send message to player {} via session {}: {}", 
                                playerId, session.getId(), e.getMessage());
                }
            });
            
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Failed to serialize message for player {}: {}", playerId, e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.error("Failed to serialize or send message to player {}: {}", playerId, e.getMessage(), e);
        }
    }
} 