package com.assassin.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.CloseReason;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assassin.util.AuthorizationUtils;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class GameWebSocketServerIntegrationTest {

    @Mock
    private AuthorizationUtils authUtils;
    
    @Mock
    private WebSocketEventBroadcaster eventBroadcaster;
    
    @Mock
    private Session session;
    
    @Mock
    private RemoteEndpoint.Basic basicRemote;
    
    @Mock
    private DecodedJWT jwt;
    
    private GameWebSocketServer server;
    private ObjectMapper objectMapper;
    private Map<String, Object> userProperties;
    
    @BeforeEach
    void setUp() {
        // Set required environment variables for DynamoDB tables
        System.setProperty("PLAYER_TABLE_NAME", "test-players-table");
        System.setProperty("SAFE_ZONES_TABLE_NAME", "test-safe-zones-table");
        System.setProperty("NOTIFICATIONS_TABLE_NAME", "test-notifications-table");
        
        server = new GameWebSocketServer(authUtils, eventBroadcaster);
        objectMapper = new ObjectMapper();
        userProperties = new ConcurrentHashMap<>();
        
        // Setup session mocks with lenient stubbing
        lenient().when(session.getUserProperties()).thenReturn(userProperties);
        lenient().when(session.getBasicRemote()).thenReturn(basicRemote);
        lenient().when(session.getId()).thenReturn("session123");
        lenient().when(session.isOpen()).thenReturn(true);
    }
    
    @Test
    void testOnOpen_ValidToken() throws Exception {
        // Arrange
        String token = "valid.jwt.token";
        String userId = "user123";
        URI uri = new URI("ws://localhost:8080/ws/game?token=" + token);
        
        when(session.getRequestURI()).thenReturn(uri);
        when(authUtils.validateAndDecodeToken(token)).thenReturn(jwt);
        when(authUtils.getUserIdFromToken(jwt)).thenReturn(userId);
        
        // Act
        server.onOpen(session);
        
        // Assert
        verify(authUtils).validateAndDecodeToken(token);
        verify(authUtils).getUserIdFromToken(jwt);
        assertEquals(userId, userProperties.get("userId"));
        assertTrue(GameWebSocketServer.sessions.containsKey("session123"));
    }
    
    @Test
    void testOnOpen_MissingToken() throws Exception {
        // Arrange
        URI uri = new URI("ws://localhost:8080/ws/game");
        when(session.getRequestURI()).thenReturn(uri);
        
        // Act
        server.onOpen(session);
        
        // Assert
        verify(session).close(any(CloseReason.class));
        assertFalse(GameWebSocketServer.sessions.containsKey("session123"));
    }
    
    @Test
    void testOnOpen_InvalidToken() throws Exception {
        // Arrange
        String token = "invalid.jwt.token";
        URI uri = new URI("ws://localhost:8080/ws/game?token=" + token);
        
        when(session.getRequestURI()).thenReturn(uri);
        when(authUtils.validateAndDecodeToken(token)).thenThrow(new RuntimeException("Invalid token"));
        
        // Act
        server.onOpen(session);
        
        // Assert
        verify(session).close(any(CloseReason.class));
        assertFalse(GameWebSocketServer.sessions.containsKey("session123"));
    }
    
    @Test
    void testHandlePlayerUpdate_ValidMessage() throws Exception {
        // Arrange
        userProperties.put("userId", "player123");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("latitude", 40.7128);
        payload.put("longitude", -74.0060);
        payload.put("accuracy", 5.0);
        payload.put("gameId", "game456");
        
        WebSocketMessage message = new WebSocketMessage("player_update", payload, 
                                                        System.currentTimeMillis(), null);
        String messageJson = objectMapper.writeValueAsString(message);
        
        // Act
        server.onMessage(messageJson, session);
        
        // Assert
        verify(eventBroadcaster).broadcastPlayerLocationUpdate("player123", 40.7128, -74.0060, 5.0, "game456");
    }
    
    @Test
    void testHandlePlayerUpdate_MissingLatitude() throws Exception {
        // Arrange
        userProperties.put("userId", "player123");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("longitude", -74.0060);
        payload.put("gameId", "game456");
        
        WebSocketMessage message = new WebSocketMessage("player_update", payload, 
                                                        System.currentTimeMillis(), null);
        String messageJson = objectMapper.writeValueAsString(message);
        
        // Act
        server.onMessage(messageJson, session);
        
        // Assert
        verify(eventBroadcaster, never()).broadcastPlayerLocationUpdate(any(), any(), any(), any(), any());
        verify(basicRemote).sendText(contains("error"));
    }
    
    @Test
    void testHandlePlayerUpdate_InvalidCoordinates() throws Exception {
        // Arrange
        userProperties.put("userId", "player123");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("latitude", 91.0); // Invalid latitude
        payload.put("longitude", -74.0060);
        payload.put("gameId", "game456");
        
        WebSocketMessage message = new WebSocketMessage("player_update", payload, 
                                                        System.currentTimeMillis(), null);
        String messageJson = objectMapper.writeValueAsString(message);
        
        // Act
        server.onMessage(messageJson, session);
        
        // Assert
        verify(eventBroadcaster, never()).broadcastPlayerLocationUpdate(any(), any(), any(), any(), any());
        verify(basicRemote).sendText(contains("Invalid coordinates"));
    }
    
    @Test
    void testHandlePlayerUpdate_StringCoordinates() throws Exception {
        // Arrange
        userProperties.put("userId", "player123");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("latitude", "40.7128"); // String instead of number
        payload.put("longitude", "-74.0060");
        payload.put("accuracy", "5.0");
        payload.put("gameId", "game456");
        
        WebSocketMessage message = new WebSocketMessage("player_update", payload, 
                                                        System.currentTimeMillis(), null);
        String messageJson = objectMapper.writeValueAsString(message);
        
        // Act
        server.onMessage(messageJson, session);
        
        // Assert
        verify(eventBroadcaster).broadcastPlayerLocationUpdate("player123", 40.7128, -74.0060, 5.0, "game456");
    }
    
    @Test
    void testHandleKillNotification_ValidMessage() throws Exception {
        // Arrange
        userProperties.put("userId", "killer123");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("killerId", "killer123");
        payload.put("victimId", "victim456");
        payload.put("gameId", "game789");
        
        WebSocketMessage message = new WebSocketMessage("kill_notification", payload, 
                                                        System.currentTimeMillis(), null);
        String messageJson = objectMapper.writeValueAsString(message);
        
        // Act
        server.onMessage(messageJson, session);
        
        // Assert - should broadcast the message (not use eventBroadcaster for kill notifications yet)
        // This is because kill notifications need to go through the kill verification process
        verify(eventBroadcaster, never()).broadcastKillNotification(any(), any(), any());
    }
    
    @Test
    void testHandleKillNotification_UnauthorizedUser() throws Exception {
        // Arrange
        userProperties.put("userId", "user123");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("killerId", "killer456"); // Different from userId
        payload.put("victimId", "victim789");
        payload.put("gameId", "game123");
        
        WebSocketMessage message = new WebSocketMessage("kill_notification", payload, 
                                                        System.currentTimeMillis(), null);
        String messageJson = objectMapper.writeValueAsString(message);
        
        // Act
        server.onMessage(messageJson, session);
        
        // Assert
        verify(basicRemote).sendText(contains("Unauthorized"));
    }
    
    @Test
    void testHandleZoneChange_ValidMessage() throws Exception {
        // Arrange
        userProperties.put("userId", "admin123");
        
        Map<String, Object> zoneData = new HashMap<>();
        zoneData.put("radius", 100.0);
        zoneData.put("centerLat", 40.7128);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", "game123");
        payload.put("zoneType", "shrinking_zone");
        payload.put("zoneData", zoneData);
        
        WebSocketMessage message = new WebSocketMessage("zone_change", payload, 
                                                        System.currentTimeMillis(), null);
        String messageJson = objectMapper.writeValueAsString(message);
        
        // Act
        server.onMessage(messageJson, session);
        
        // Assert
        verify(eventBroadcaster).broadcastZoneChange("game123", "shrinking_zone", zoneData);
    }
    
    @Test
    void testHandleZoneChange_MissingGameId() throws Exception {
        // Arrange
        userProperties.put("userId", "admin123");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("zoneType", "shrinking_zone");
        
        WebSocketMessage message = new WebSocketMessage("zone_change", payload, 
                                                        System.currentTimeMillis(), null);
        String messageJson = objectMapper.writeValueAsString(message);
        
        // Act
        server.onMessage(messageJson, session);
        
        // Assert
        verify(eventBroadcaster, never()).broadcastZoneChange(any(), any(), any());
        verify(basicRemote).sendText(contains("missing gameId"));
    }
    
    @Test
    void testHandleZoneChange_NullZoneData() throws Exception {
        // Arrange
        userProperties.put("userId", "admin123");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", "game123");
        payload.put("zoneType", "shrinking_zone");
        // zoneData is null
        
        WebSocketMessage message = new WebSocketMessage("zone_change", payload, 
                                                        System.currentTimeMillis(), null);
        String messageJson = objectMapper.writeValueAsString(message);
        
        // Act
        server.onMessage(messageJson, session);
        
        // Assert
        ArgumentCaptor<Map<String, Object>> zoneDataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventBroadcaster).broadcastZoneChange(eq("game123"), eq("shrinking_zone"), zoneDataCaptor.capture());
        
        Map<String, Object> capturedZoneData = zoneDataCaptor.getValue();
        assertNotNull(capturedZoneData);
        assertTrue(capturedZoneData.isEmpty());
    }
    
    @Test
    void testUnknownMessageType() throws Exception {
        // Arrange
        userProperties.put("userId", "user123");
        
        WebSocketMessage message = new WebSocketMessage("unknown_type", new HashMap<>(), 
                                                        System.currentTimeMillis(), null);
        String messageJson = objectMapper.writeValueAsString(message);
        
        // Act
        server.onMessage(messageJson, session);
        
        // Assert - should not throw exception, just log unknown type
        verify(eventBroadcaster, never()).broadcastPlayerLocationUpdate(any(), any(), any(), any(), any());
        verify(eventBroadcaster, never()).broadcastZoneChange(any(), any(), any());
    }
    
    @Test
    void testInvalidJsonMessage() throws Exception {
        // Arrange
        userProperties.put("userId", "user123");
        String invalidJson = "{invalid json}";
        
        // Act
        server.onMessage(invalidJson, session);
        
        // Assert - should handle gracefully without throwing exception
        verify(eventBroadcaster, never()).broadcastPlayerLocationUpdate(any(), any(), any(), any(), any());
    }
    
    @Test
    void testOnClose() {
        // Arrange
        GameWebSocketServer.sessions.put("session123", session);
        CloseReason reason = new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Normal close");
        
        // Act
        server.onClose(session, reason);
        
        // Assert
        assertFalse(GameWebSocketServer.sessions.containsKey("session123"));
    }
    
    @Test
    void testOnError() {
        // Arrange
        Throwable error = new RuntimeException("Test error");
        
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> {
            server.onError(session, error);
        });
    }
    
    @Test
    void testGetDoubleFromPayload_NumberValue() throws Exception {
        // Arrange
        userProperties.put("userId", "user123");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("latitude", 40.7128);
        payload.put("longitude", -74);
        payload.put("gameId", "game123");
        
        WebSocketMessage message = new WebSocketMessage("player_update", payload, 
                                                        System.currentTimeMillis(), null);
        String messageJson = objectMapper.writeValueAsString(message);
        
        // Act
        server.onMessage(messageJson, session);
        
        // Assert
        verify(eventBroadcaster).broadcastPlayerLocationUpdate("user123", 40.7128, -74.0, null, "game123");
    }
    
    @Test
    void testGetDoubleFromPayload_InvalidStringValue() throws Exception {
        // Arrange
        userProperties.put("userId", "user123");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("latitude", "not_a_number");
        payload.put("longitude", -74.0);
        payload.put("gameId", "game123");
        
        WebSocketMessage message = new WebSocketMessage("player_update", payload, 
                                                        System.currentTimeMillis(), null);
        String messageJson = objectMapper.writeValueAsString(message);
        
        // Act
        server.onMessage(messageJson, session);
        
        // Assert
        verify(eventBroadcaster, never()).broadcastPlayerLocationUpdate(any(), any(), any(), any(), any());
        verify(basicRemote).sendText(contains("missing latitude, longitude, or gameId"));
    }
    
    @Test
    void testSendErrorToSession_IOError() throws Exception {
        // Arrange
        userProperties.put("userId", "user123");
        doThrow(new IOException("Connection error")).when(basicRemote).sendText(any());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("latitude", 91.0); // Invalid to trigger error
        payload.put("longitude", -74.0);
        payload.put("gameId", "game123");
        
        WebSocketMessage message = new WebSocketMessage("player_update", payload, 
                                                        System.currentTimeMillis(), null);
        String messageJson = objectMapper.writeValueAsString(message);
        
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> {
            server.onMessage(messageJson, session);
        });
    }
} 