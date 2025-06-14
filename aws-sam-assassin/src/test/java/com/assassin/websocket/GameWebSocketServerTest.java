package com.assassin.websocket;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.assassin.util.AuthorizationUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import javax.websocket.CloseReason;
import javax.websocket.Session;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameWebSocketServerTest {
    @Mock
    private Session session;

    @Test
    void testOnOpen_missingToken_closesSession() throws IOException {
        // Set required environment variables for DynamoDB tables
        System.setProperty("PLAYER_TABLE_NAME", "test-players-table");
        System.setProperty("SAFE_ZONES_TABLE_NAME", "test-safe-zones-table");
        System.setProperty("NOTIFICATIONS_TABLE_NAME", "test-notifications-table");
        
        session = mock(Session.class);
        when(session.getRequestURI()).thenReturn(URI.create("ws://localhost/ws/game"));
        doNothing().when(session).close(any(CloseReason.class));
        AuthorizationUtils mockAuth = mock(AuthorizationUtils.class);
        GameWebSocketServer server = new GameWebSocketServer(mockAuth);
        server.onOpen(session);
        verify(session).close(argThat(reason -> reason.getReasonPhrase().contains("Missing authentication token")));
    }

    @Test
    void testOnOpen_invalidToken_closesSession() throws IOException {
        // Set required environment variables for DynamoDB tables
        System.setProperty("PLAYER_TABLE_NAME", "test-players-table");
        System.setProperty("SAFE_ZONES_TABLE_NAME", "test-safe-zones-table");
        System.setProperty("NOTIFICATIONS_TABLE_NAME", "test-notifications-table");
        
        session = mock(Session.class);
        when(session.getRequestURI()).thenReturn(URI.create("ws://localhost/ws/game?token=badtoken"));
        doNothing().when(session).close(any(CloseReason.class));
        AuthorizationUtils mockAuth = mock(AuthorizationUtils.class);
        try {
            when(mockAuth.validateAndDecodeToken(anyString())).thenThrow(new com.auth0.jwt.exceptions.JWTVerificationException("Invalid"));
        } catch (Exception ignored) {}
        GameWebSocketServer server = new GameWebSocketServer(mockAuth);
        server.onOpen(session);
        verify(session).close(argThat(reason -> reason.getReasonPhrase().contains("Invalid authentication token")));
    }

    @Test
    void testOnOpen_validToken_storesUserId() throws Exception {
        // Set required environment variables for DynamoDB tables
        System.setProperty("PLAYER_TABLE_NAME", "test-players-table");
        System.setProperty("SAFE_ZONES_TABLE_NAME", "test-safe-zones-table");
        System.setProperty("NOTIFICATIONS_TABLE_NAME", "test-notifications-table");
        
        session = mock(Session.class);
        when(session.getRequestURI()).thenReturn(URI.create("ws://localhost/ws/game?token=goodtoken"));
        Map<String, Object> userProps = new HashMap<>();
        when(session.getUserProperties()).thenReturn(userProps);
        doNothing().when(session).close(any(CloseReason.class));
        AuthorizationUtils mockAuth = mock(AuthorizationUtils.class);
        DecodedJWT mockJwt = mock(DecodedJWT.class);
        when(mockAuth.validateAndDecodeToken(anyString())).thenReturn(mockJwt);
        when(mockAuth.getUserIdFromToken(mockJwt)).thenReturn("test-user");
        GameWebSocketServer server = new GameWebSocketServer(mockAuth);
        server.onOpen(session);
        assertEquals("test-user", userProps.get("userId"));
    }

    @Test
    void testOnMessage_routesByType() throws Exception {
        // Set required environment variables for DynamoDB tables
        System.setProperty("PLAYER_TABLE_NAME", "test-players-table");
        System.setProperty("SAFE_ZONES_TABLE_NAME", "test-safe-zones-table");
        System.setProperty("NOTIFICATIONS_TABLE_NAME", "test-notifications-table");
        
        session = mock(Session.class);
        when(session.getId()).thenReturn("session1");
        Map<String, Object> userProps = new HashMap<>();
        userProps.put("userId", "test-user");
        when(session.getUserProperties()).thenReturn(userProps);
        AuthorizationUtils mockAuth = mock(AuthorizationUtils.class);
        GameWebSocketServer server = new GameWebSocketServer(mockAuth);
        String playerUpdateMsg = "{\"type\":\"player_update\",\"payload\":{\"playerId\":\"abc\"}}";
        // Should not throw
        server.onMessage(playerUpdateMsg, session);
    }

    // Additional tests for valid/invalid token and onMessage can be added here
} 