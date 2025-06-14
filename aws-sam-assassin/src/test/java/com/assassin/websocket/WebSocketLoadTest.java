package com.assassin.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assassin.util.AuthorizationUtils;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class WebSocketLoadTest {

    @Mock
    private AuthorizationUtils authUtils;
    
    @Mock
    private WebSocketEventBroadcaster eventBroadcaster;
    
    @Mock
    private DecodedJWT jwt;
    
    private GameWebSocketServer server;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        // Set required environment variables for DynamoDB tables
        System.setProperty("PLAYER_TABLE_NAME", "test-players-table");
        System.setProperty("SAFE_ZONES_TABLE_NAME", "test-safe-zones-table");
        System.setProperty("NOTIFICATIONS_TABLE_NAME", "test-notifications-table");
        
        server = new GameWebSocketServer(authUtils, eventBroadcaster);
        objectMapper = new ObjectMapper();
        
        // Setup auth mocks with lenient stubbing
        try {
            lenient().when(authUtils.validateAndDecodeToken(any())).thenReturn(jwt);
            lenient().when(authUtils.getUserIdFromToken(jwt)).thenAnswer(invocation -> 
                "user_" + System.nanoTime() % 10000); // Generate unique user IDs
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Test
    void testConcurrentConnections() throws Exception {
        // Arrange
        int numberOfConnections = 50;
        CountDownLatch connectionsLatch = new CountDownLatch(numberOfConnections);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // Act - Create multiple concurrent connections
        for (int i = 0; i < numberOfConnections; i++) {
            final int connectionId = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    Session session = createMockSession("session_" + connectionId, "user_" + connectionId);
                    server.onOpen(session);
                    connectionsLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
            futures.add(future);
        }
        
        // Wait for all connections to complete
        assertTrue(connectionsLatch.await(10, TimeUnit.SECONDS), 
                  "All connections should complete within 10 seconds");
        
        // Assert
        assertEquals(numberOfConnections, GameWebSocketServer.sessions.size());
        
        // Cleanup
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        GameWebSocketServer.sessions.clear();
    }
    
    @Test
    void testHighThroughputMessages() throws Exception {
        // Arrange
        int numberOfSessions = 10;
        int messagesPerSession = 20;
        CountDownLatch messagesLatch = new CountDownLatch(numberOfSessions * messagesPerSession);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        List<Session> sessions = new ArrayList<>();
        for (int i = 0; i < numberOfSessions; i++) {
            Session session = createMockSession("session_" + i, "user_" + i);
            server.onOpen(session);
            sessions.add(session);
        }
        
        // Act - Send multiple messages concurrently
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int sessionIndex = 0; sessionIndex < numberOfSessions; sessionIndex++) {
            final Session session = sessions.get(sessionIndex);
            final int finalSessionIndex = sessionIndex;
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int msgIndex = 0; msgIndex < messagesPerSession; msgIndex++) {
                    try {
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("latitude", 40.7128 + (finalSessionIndex * 0.001));
                        payload.put("longitude", -74.0060 + (msgIndex * 0.001));
                        payload.put("accuracy", 5.0);
                        payload.put("gameId", "load_test_game");
                        
                        WebSocketMessage message = new WebSocketMessage("player_update", payload, 
                                                                        System.currentTimeMillis(), null);
                        String messageJson = objectMapper.writeValueAsString(message);
                        
                        server.onMessage(messageJson, session);
                        messagesLatch.countDown();
                        
                        // Small delay to simulate realistic message timing
                        Thread.sleep(10);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }, executor);
            futures.add(future);
        }
        
        // Wait for all messages to be processed
        assertTrue(messagesLatch.await(30, TimeUnit.SECONDS), 
                  "All messages should be processed within 30 seconds");
        
        // Assert - Verify that the event broadcaster was called for each valid message
        verify(eventBroadcaster, times(numberOfSessions * messagesPerSession))
            .broadcastPlayerLocationUpdate(any(), any(), any(), any(), eq("load_test_game"));
        
        // Cleanup
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        GameWebSocketServer.sessions.clear();
    }
    
    @Test
    void testConcurrentMessageTypes() throws Exception {
        // Arrange
        int numberOfSessions = 5;
        Session[] sessions = new Session[numberOfSessions];
        
        for (int i = 0; i < numberOfSessions; i++) {
            sessions[i] = createMockSession("session_" + i, "user_" + i);
            server.onOpen(sessions[i]);
        }
        
        CountDownLatch messagesLatch = new CountDownLatch(numberOfSessions * 3); // 3 message types per session
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        // Act - Send different message types concurrently
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < numberOfSessions; i++) {
            final Session session = sessions[i];
            final String userId = "user_" + i;
            
            // Player update message
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("latitude", 40.7128);
                    payload.put("longitude", -74.0060);
                    payload.put("gameId", "concurrent_test_game");
                    
                    WebSocketMessage message = new WebSocketMessage("player_update", payload, 
                                                                    System.currentTimeMillis(), null);
                    server.onMessage(objectMapper.writeValueAsString(message), session);
                    messagesLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor));
            
            // Kill notification message
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("killerId", userId);
                    payload.put("victimId", "victim_" + userId);
                    payload.put("gameId", "concurrent_test_game");
                    
                    WebSocketMessage message = new WebSocketMessage("kill_notification", payload, 
                                                                    System.currentTimeMillis(), null);
                    server.onMessage(objectMapper.writeValueAsString(message), session);
                    messagesLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor));
            
            // Zone change message
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Object> zoneData = new HashMap<>();
                    zoneData.put("radius", 100.0);
                    
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("gameId", "concurrent_test_game");
                    payload.put("zoneType", "shrinking_zone");
                    payload.put("zoneData", zoneData);
                    
                    WebSocketMessage message = new WebSocketMessage("zone_change", payload, 
                                                                    System.currentTimeMillis(), null);
                    server.onMessage(objectMapper.writeValueAsString(message), session);
                    messagesLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor));
        }
        
        // Wait for all messages to complete
        assertTrue(messagesLatch.await(15, TimeUnit.SECONDS), 
                  "All concurrent messages should complete within 15 seconds");
        
        // Assert - Verify appropriate service calls were made
        verify(eventBroadcaster, times(numberOfSessions))
            .broadcastPlayerLocationUpdate(any(), any(), any(), any(), eq("concurrent_test_game"));
        verify(eventBroadcaster, times(numberOfSessions))
            .broadcastZoneChange(eq("concurrent_test_game"), eq("shrinking_zone"), any());
        
        // Cleanup
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        GameWebSocketServer.sessions.clear();
    }
    
    @Test
    void testConnectionDropouts() throws Exception {
        // Arrange
        int numberOfSessions = 20;
        List<Session> sessions = new ArrayList<>();
        
        for (int i = 0; i < numberOfSessions; i++) {
            Session session = createMockSession("session_" + i, "user_" + i);
            server.onOpen(session);
            sessions.add(session);
        }
        
        assertEquals(numberOfSessions, GameWebSocketServer.sessions.size());
        
        // Act - Simulate random connection drops
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch dropoutLatch = new CountDownLatch(numberOfSessions / 2);
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfSessions / 2; i++) {
            final Session session = sessions.get(i);
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    // Simulate connection drop
                    server.onClose(session, null);
                    dropoutLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor));
        }
        
        assertTrue(dropoutLatch.await(10, TimeUnit.SECONDS), 
                  "Connection drops should complete within 10 seconds");
        
        // Assert - Half the sessions should be removed
        assertEquals(numberOfSessions / 2, GameWebSocketServer.sessions.size());
        
        // Cleanup
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        GameWebSocketServer.sessions.clear();
    }
    
    @Test
    void testMemoryUsageUnderLoad() throws Exception {
        // Arrange
        int numberOfSessions = 100;
        int messagesPerSession = 10;
        
        // Measure initial memory
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // Suggest garbage collection
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Act - Create sessions and send messages
        List<Session> sessions = new ArrayList<>();
        for (int i = 0; i < numberOfSessions; i++) {
            Session session = createMockSession("session_" + i, "user_" + i);
            server.onOpen(session);
            sessions.add(session);
        }
        
        // Send messages
        for (Session session : sessions) {
            for (int j = 0; j < messagesPerSession; j++) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("latitude", 40.7128);
                payload.put("longitude", -74.0060);
                payload.put("gameId", "memory_test_game");
                
                WebSocketMessage message = new WebSocketMessage("player_update", payload, 
                                                                System.currentTimeMillis(), null);
                server.onMessage(objectMapper.writeValueAsString(message), session);
            }
        }
        
        // Measure memory after load
        runtime.gc();
        long memoryAfterLoad = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = memoryAfterLoad - initialMemory;
        
        // Clean up sessions
        for (Session session : sessions) {
            server.onClose(session, null);
        }
        
        runtime.gc();
        long memoryAfterCleanup = runtime.totalMemory() - runtime.freeMemory();
        
        // Assert - Memory should not increase excessively (less than 50MB for this test)
        assertTrue(memoryIncrease < 50 * 1024 * 1024, 
                  "Memory increase should be reasonable: " + (memoryIncrease / 1024 / 1024) + "MB");
        
        // Memory should be mostly cleaned up after session removal
        // Note: This is a best-effort check as GC behavior is not deterministic
        long memoryAfterCleanupIncrease = memoryAfterCleanup - initialMemory;
        assertTrue(memoryAfterCleanupIncrease < memoryIncrease + (10 * 1024 * 1024), 
                  "Memory should not increase significantly after cleanup: " + 
                  (memoryAfterCleanupIncrease / 1024 / 1024) + "MB vs " + 
                  (memoryIncrease / 1024 / 1024) + "MB");
        
        assertEquals(0, GameWebSocketServer.sessions.size(), "All sessions should be removed");
    }
    
    @Test
    void testErrorHandlingUnderLoad() throws Exception {
        // Arrange
        int numberOfSessions = 10;
        int invalidMessagesPerSession = 5;
        AtomicInteger errorCount = new AtomicInteger(0);
        
        List<Session> sessions = new ArrayList<>();
        for (int i = 0; i < numberOfSessions; i++) {
            Session session = createMockSession("session_" + i, "user_" + i);
            server.onOpen(session);
            sessions.add(session);
        }
        
        // Act - Send invalid messages that should trigger errors
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch errorLatch = new CountDownLatch(numberOfSessions * invalidMessagesPerSession);
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Session session : sessions) {
            futures.add(CompletableFuture.runAsync(() -> {
                for (int j = 0; j < invalidMessagesPerSession; j++) {
                    try {
                        // Send invalid message (missing required fields)
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("latitude", 91.0); // Invalid latitude
                        payload.put("gameId", "error_test_game");
                        // Missing longitude
                        
                        WebSocketMessage message = new WebSocketMessage("player_update", payload, 
                                                                        System.currentTimeMillis(), null);
                        server.onMessage(objectMapper.writeValueAsString(message), session);
                        errorLatch.countDown();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        errorLatch.countDown();
                    }
                }
            }, executor));
        }
        
        assertTrue(errorLatch.await(15, TimeUnit.SECONDS), 
                  "Error handling should complete within 15 seconds");
        
        // Assert - System should handle errors gracefully without crashing
        assertEquals(numberOfSessions, GameWebSocketServer.sessions.size(), 
                    "All sessions should still be active despite errors");
        
        // No valid location updates should have been processed due to invalid coordinates
        verify(eventBroadcaster, never()).broadcastPlayerLocationUpdate(any(), any(), any(), any(), any());
        
        // Cleanup
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        GameWebSocketServer.sessions.clear();
    }
    
    /**
     * Helper method to create a mock session with proper setup
     */
    private Session createMockSession(String sessionId, String userId) throws Exception {
        Session session = mock(Session.class);
        RemoteEndpoint.Basic basicRemote = mock(RemoteEndpoint.Basic.class);
        Map<String, Object> userProperties = new ConcurrentHashMap<>();
        URI uri = new URI("ws://localhost:8080/ws/game?token=valid_token");
        
        lenient().when(session.getId()).thenReturn(sessionId);
        lenient().when(session.getUserProperties()).thenReturn(userProperties);
        lenient().when(session.getBasicRemote()).thenReturn(basicRemote);
        lenient().when(session.getRequestURI()).thenReturn(uri);
        lenient().when(session.isOpen()).thenReturn(true);
        
        // Set userId in properties (simulating successful authentication)
        userProperties.put("userId", userId);
        
        return session;
    }
} 