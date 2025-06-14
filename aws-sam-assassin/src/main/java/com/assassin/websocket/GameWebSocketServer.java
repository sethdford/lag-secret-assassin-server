package com.assassin.websocket;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.assassin.util.AuthorizationUtils;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwk.JwkException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket server endpoint for real-time game updates (player positions, kills, zone changes).
 * Uses JSR 356 standard API. To be deployed in a Java EE container (e.g., Tomcat, Jetty).
 */
@ServerEndpoint("/ws/game")
public class GameWebSocketServer {
    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketServer.class);
    
    // Track all active sessions
    static final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthorizationUtils authUtils;
    
    // Integration with backend services
    private final WebSocketEventBroadcaster eventBroadcaster;
    
    /**
     * Default constructor for container deployment
     */
    public GameWebSocketServer() {
        this(new AuthorizationUtils());
    }
    
    /**
     * Constructor for dependency injection (testing)
     */
    public GameWebSocketServer(AuthorizationUtils authUtils) {
        this.authUtils = authUtils;
        this.eventBroadcaster = new WebSocketEventBroadcaster();
        // Set this server as the broadcaster's WebSocket server
        this.eventBroadcaster.setWebSocketServer(this);
    }
    
    /**
     * Constructor with full dependency injection
     */
    public GameWebSocketServer(AuthorizationUtils authUtils, WebSocketEventBroadcaster eventBroadcaster) {
        this.authUtils = authUtils;
        this.eventBroadcaster = eventBroadcaster;
        // Set this server as the broadcaster's WebSocket server
        this.eventBroadcaster.setWebSocketServer(this);
    }

    @OnOpen
    public void onOpen(Session session) {
        String token = getQueryParam(session, "token");
        if (token == null || token.isEmpty()) {
            closeSession(session, "Missing authentication token");
            return;
        }
        try {
            DecodedJWT jwt = authUtils.validateAndDecodeToken(token);
            String userId = authUtils.getUserIdFromToken(jwt);
            session.getUserProperties().put("userId", userId);
            sessions.put(session.getId(), session);
            System.out.println("WebSocket opened: " + session.getId() + " for user: " + userId);
        } catch (JWTVerificationException | JwkException e) {
            closeSession(session, "Invalid authentication token: " + e.getMessage());
        } catch (RuntimeException e) {
            closeSession(session, "Authentication error: " + e.getMessage());
        }
    }

    private String getQueryParam(Session session, String key) {
        try {
            URI uri = session.getRequestURI();
            String query = uri.getQuery();
            if (query == null) return null;
            for (String param : query.split("&")) {
                String[] parts = param.split("=", 2);
                if (parts.length == 2 && parts[0].equals(key)) {
                    return parts[1];
                }
            }
        } catch (RuntimeException e) {
            // Ignore
        }
        return null;
    }

    void closeSession(Session session, String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
        } catch (IOException e) {
            // Ignore
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            WebSocketMessage wsMessage = objectMapper.readValue(message, WebSocketMessage.class);
            System.out.println("Received message from " + session.getId() + ": " + wsMessage);
            switch (wsMessage.getType()) {
                case "player_update":
                    handlePlayerUpdate(wsMessage, session);
                    break;
                case "kill_notification":
                    handleKillNotification(wsMessage, session);
                    break;
                case "zone_change":
                    handleZoneChange(wsMessage, session);
                    break;
                default:
                    System.out.println("Unknown message type: " + wsMessage.getType());
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            System.err.println("Failed to parse JSON message: " + e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("Failed to parse message: " + e.getMessage());
        }
    }

    private void handlePlayerUpdate(WebSocketMessage message, Session session) {
        String userId = (String) session.getUserProperties().get("userId");
        
        try {
            // Extract location data from payload
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) message.getPayload();
            
            Double latitude = getDoubleFromPayload(payload, "latitude");
            Double longitude = getDoubleFromPayload(payload, "longitude");
            Double accuracy = getDoubleFromPayload(payload, "accuracy");
            String gameId = (String) payload.get("gameId");
            
            // Validate required fields
            if (latitude == null || longitude == null || gameId == null) {
                logger.warn("Invalid player update from user {}: missing required fields", userId);
                sendErrorToSession(session, "Invalid player update: missing latitude, longitude, or gameId");
                return;
            }
            
            // Validate coordinate ranges
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                logger.warn("Invalid coordinates from user {}: lat={}, lon={}", userId, latitude, longitude);
                sendErrorToSession(session, "Invalid coordinates: latitude must be [-90,90], longitude must be [-180,180]");
                return;
            }
            
            // Use the event broadcaster to handle the update and broadcast
            eventBroadcaster.broadcastPlayerLocationUpdate(userId, latitude, longitude, accuracy, gameId);
            
            logger.debug("Processed player update from user: {} in game: {}", userId, gameId);
            
        } catch (RuntimeException e) {
            logger.error("Failed to process player update from user {}: {}", userId, e.getMessage(), e);
            sendErrorToSession(session, "Failed to process player update: " + e.getMessage());
        }
    }

    private void handleKillNotification(WebSocketMessage message, Session session) {
        String userId = (String) session.getUserProperties().get("userId");
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) message.getPayload();
            
            String killerId = (String) payload.get("killerId");
            String victimId = (String) payload.get("victimId");
            String gameId = (String) payload.get("gameId");
            
            // Validate that the user is authorized to report this kill
            if (!userId.equals(killerId)) {
                logger.warn("User {} attempted to report kill for different killer {}", userId, killerId);
                sendErrorToSession(session, "Unauthorized: You can only report your own kills");
                return;
            }
            
            // Validate required fields
            if (killerId == null || victimId == null || gameId == null) {
                logger.warn("Invalid kill notification from user {}: missing required fields", userId);
                sendErrorToSession(session, "Invalid kill notification: missing killerId, victimId, or gameId");
                return;
            }
            
            // Note: In a real implementation, this would trigger the kill verification process
            // For now, we'll just broadcast the notification
            logger.info("Kill notification received: {} -> {} in game {}", killerId, victimId, gameId);
            
            // Broadcast to all players in the game
            broadcastExceptSender(message, session);
            
        } catch (RuntimeException e) {
            logger.error("Failed to process kill notification from user {}: {}", userId, e.getMessage(), e);
            sendErrorToSession(session, "Failed to process kill notification: " + e.getMessage());
        }
    }

    private void handleZoneChange(WebSocketMessage message, Session session) {
        String userId = (String) session.getUserProperties().get("userId");
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) message.getPayload();
            
            String gameId = (String) payload.get("gameId");
            String zoneType = (String) payload.get("zoneType");
            
            // Validate required fields
            if (gameId == null || zoneType == null) {
                logger.warn("Invalid zone change from user {}: missing required fields", userId);
                sendErrorToSession(session, "Invalid zone change: missing gameId or zoneType");
                return;
            }
            
            // TODO: In a real implementation, verify that the user has admin permissions for this game
            // For now, we'll allow any authenticated user to trigger zone changes
            
            @SuppressWarnings("unchecked")
            Map<String, Object> zoneData = (Map<String, Object>) payload.get("zoneData");
            if (zoneData == null) {
                zoneData = new HashMap<>();
            }
            
            // Use the event broadcaster to handle the zone change
            eventBroadcaster.broadcastZoneChange(gameId, zoneType, zoneData);
            
            logger.info("Zone change processed: {} in game {} by user {}", zoneType, gameId, userId);
            
        } catch (RuntimeException e) {
            logger.error("Failed to process zone change from user {}: {}", userId, e.getMessage(), e);
            sendErrorToSession(session, "Failed to process zone change: " + e.getMessage());
        }
    }

    private void broadcastExceptSender(WebSocketMessage message, Session sender) {
        sessions.values().forEach(s -> {
            if (s.isOpen() && !s.getId().equals(sender.getId())) {
                try {
                    s.getBasicRemote().sendText(serializeMessage(message));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private String serializeMessage(WebSocketMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            System.err.println("Failed to serialize message: " + e.getMessage());
            return "{}";
        } catch (RuntimeException e) {
            return "{}";
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        sessions.remove(session.getId());
        System.out.println("WebSocket closed: " + session.getId() + " Reason: " + reason);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("WebSocket error for session " + session.getId() + ": " + throwable.getMessage());
    }
    
    /**
     * Safely extract a Double value from a payload map
     */
    private Double getDoubleFromPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse double value for key {}: {}", key, value);
                return null;
            }
        }
        return null;
    }
    
    /**
     * Send an error message to a specific session
     */
    private void sendErrorToSession(Session session, String errorMessage) {
        try {
            WebSocketMessage errorMsg = new WebSocketMessage("error", 
                Map.of("message", errorMessage), 
                System.currentTimeMillis(), 
                null);
            session.getBasicRemote().sendText(serializeMessage(errorMsg));
        } catch (IOException e) {
            logger.error("Failed to send error message to session {}: {}", session.getId(), e.getMessage());
        }
    }
} 