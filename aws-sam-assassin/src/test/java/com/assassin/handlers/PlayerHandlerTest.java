package com.assassin.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Comprehensive unit tests for PlayerHandler.
 * Special focus on target assignment endpoint testing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PlayerHandlerTest {

    @Mock
    private PlayerDao mockPlayerDao;
    
    @Mock
    private Context mockContext;
    
    private PlayerHandler playerHandler;
    private Gson gson;
    
    @BeforeEach
    void setUp() {
        playerHandler = new PlayerHandler(mockPlayerDao);
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    // ========================================
    // TARGET ASSIGNMENT ENDPOINT TESTS
    // ========================================

    @Test
    void getMyTarget_ValidPlayerWithTarget_ReturnsTargetInfo() {
        // Arrange
        String playerId = "player123";
        String targetId = "target456";
        String targetName = "Target Player";
        
        Player player = createTestPlayer(playerId, "Test Player");
        player.setTargetID(targetId);
        player.setTargetName(targetName);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + playerId);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players/me/target")
            .withHttpMethod("GET")
            .withHeaders(headers);
        
        // Mock HandlerUtils and DAO
        try (MockedStatic<HandlerUtils> handlerUtils = Mockito.mockStatic(HandlerUtils.class)) {
            handlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(request)).thenReturn(Optional.of(playerId));
            handlerUtils.when(HandlerUtils::getResponseHeaders).thenCallRealMethod();
            handlerUtils.when(() -> HandlerUtils.isPreflightRequest(request)).thenCallRealMethod();
            
            when(mockPlayerDao.getPlayerById(playerId)).thenReturn(Optional.of(player));
            
            // Act
            APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
            
            // Assert
            assertEquals(200, response.getStatusCode());
            Map<String, String> targetInfo = gson.fromJson(response.getBody(), 
                new TypeToken<Map<String, String>>(){}.getType());
            assertEquals(targetId, targetInfo.get("targetId"));
            assertEquals(targetName, targetInfo.get("targetName"));
            
            verify(mockPlayerDao).getPlayerById(playerId);
        }
    }

    @Test
    void getMyTarget_ValidPlayerWithoutTarget_ReturnsNA() {
        // Arrange
        String playerId = "player123";
        
        Player player = createTestPlayer(playerId, "Test Player");
        player.setTargetID(null);
        player.setTargetName(null);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + playerId);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players/me/target")
            .withHttpMethod("GET")
            .withHeaders(headers);
        
        // Mock HandlerUtils and DAO
        try (MockedStatic<HandlerUtils> handlerUtils = Mockito.mockStatic(HandlerUtils.class)) {
            handlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(request)).thenReturn(Optional.of(playerId));
            handlerUtils.when(HandlerUtils::getResponseHeaders).thenCallRealMethod();
            handlerUtils.when(() -> HandlerUtils.isPreflightRequest(request)).thenCallRealMethod();
            
            when(mockPlayerDao.getPlayerById(playerId)).thenReturn(Optional.of(player));
            
            // Act
            APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
            
            // Assert
            assertEquals(200, response.getStatusCode());
            Map<String, String> targetInfo = gson.fromJson(response.getBody(), 
                new TypeToken<Map<String, String>>(){}.getType());
            assertEquals("N/A", targetInfo.get("targetId"));
            assertEquals("N/A", targetInfo.get("targetName"));
            
            verify(mockPlayerDao).getPlayerById(playerId);
        }
    }

    @Test
    void getMyTarget_PlayerNotFound_ReturnsNotFound() {
        // Arrange
        String playerId = "nonexistent123";
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + playerId);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players/me/target")
            .withHttpMethod("GET")
            .withHeaders(headers);
        
        // Mock HandlerUtils and DAO
        try (MockedStatic<HandlerUtils> handlerUtils = Mockito.mockStatic(HandlerUtils.class)) {
            handlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(request)).thenReturn(Optional.of(playerId));
            handlerUtils.when(HandlerUtils::getResponseHeaders).thenCallRealMethod();
            handlerUtils.when(() -> HandlerUtils.isPreflightRequest(request)).thenCallRealMethod();
            
            when(mockPlayerDao.getPlayerById(playerId)).thenReturn(Optional.empty());
            
            // Act
            APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
            
            // Assert
            assertEquals(404, response.getStatusCode());
            Map<String, String> errorBody = gson.fromJson(response.getBody(), 
                new TypeToken<Map<String, String>>(){}.getType());
            assertEquals("Player not found", errorBody.get("message"));
            
            verify(mockPlayerDao).getPlayerById(playerId);
        }
    }

    @Test
    void getMyTarget_NoAuthorizationHeader_ReturnsInternalServerError() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players/me/target")
            .withHttpMethod("GET")
            .withHeaders(new HashMap<>()); // No authorization header
        
        // Mock HandlerUtils to throw ValidationException
        try (MockedStatic<HandlerUtils> handlerUtils = Mockito.mockStatic(HandlerUtils.class)) {
            handlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(request))
                .thenThrow(new ValidationException("Player ID not found in request context."));
            handlerUtils.when(HandlerUtils::getResponseHeaders).thenCallRealMethod();
            handlerUtils.when(() -> HandlerUtils.isPreflightRequest(request)).thenCallRealMethod();
            
            // Act
            APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
            
            // Assert
            assertEquals(500, response.getStatusCode());
            Map<String, String> errorBody = gson.fromJson(response.getBody(), 
                new TypeToken<Map<String, String>>(){}.getType());
            assertEquals("Internal Server Error", errorBody.get("message"));
        }
    }

    @Test
    void getMyTarget_DatabaseError_ReturnsInternalServerError() {
        // Arrange
        String playerId = "player123";
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + playerId);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players/me/target")
            .withHttpMethod("GET")
            .withHeaders(headers);
        
        // Mock HandlerUtils and DAO to throw exception
        try (MockedStatic<HandlerUtils> handlerUtils = Mockito.mockStatic(HandlerUtils.class)) {
            handlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(request)).thenReturn(Optional.of(playerId));
            handlerUtils.when(HandlerUtils::getResponseHeaders).thenCallRealMethod();
            handlerUtils.when(() -> HandlerUtils.isPreflightRequest(request)).thenCallRealMethod();
            
            when(mockPlayerDao.getPlayerById(playerId)).thenThrow(new RuntimeException("Database connection failed"));
            
            // Act
            APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
            
            // Assert
            assertEquals(500, response.getStatusCode());
            Map<String, String> errorBody = gson.fromJson(response.getBody(), 
                new TypeToken<Map<String, String>>(){}.getType());
            assertEquals("Internal Server Error", errorBody.get("message"));
            
            verify(mockPlayerDao).getPlayerById(playerId);
        }
    }

    // ========================================
    // OTHER ENDPOINT TESTS
    // ========================================

    @Test
    void getAllPlayers_ReturnsPlayerList() {
        // Arrange
        List<Player> players = List.of(
            createTestPlayer("player1", "Player One"),
            createTestPlayer("player2", "Player Two")
        );
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players")
            .withHttpMethod("GET");
        
        when(mockPlayerDao.getAllPlayers()).thenReturn(players);
        
        // Act
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
        
        // Assert
        assertEquals(200, response.getStatusCode());
        List<Player> responseBody = gson.fromJson(response.getBody(), 
            new TypeToken<List<Player>>(){}.getType());
        assertEquals(2, responseBody.size());
        assertEquals("player1", responseBody.get(0).getPlayerID());
        assertEquals("player2", responseBody.get(1).getPlayerID());
        
        verify(mockPlayerDao).getAllPlayers();
    }

    @Test
    void createPlayer_ValidPlayer_ReturnsCreated() {
        // Arrange
        Player player = createTestPlayer("newPlayer", "New Player");
        String playerJson = gson.toJson(player);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players")
            .withHttpMethod("POST")
            .withBody(playerJson);
        
        // Act
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
        
        // Assert
        assertEquals(201, response.getStatusCode());
        Player responsePlayer = gson.fromJson(response.getBody(), Player.class);
        assertEquals("newPlayer", responsePlayer.getPlayerID());
        assertEquals("New Player", responsePlayer.getPlayerName());
        
        verify(mockPlayerDao).savePlayer(any(Player.class));
    }

    @Test
    void getPlayer_ExistingPlayer_ReturnsPlayer() {
        // Arrange
        String playerId = "player123";
        Player player = createTestPlayer(playerId, "Test Player");
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players/" + playerId)
            .withHttpMethod("GET");
        
        when(mockPlayerDao.getPlayerById(playerId)).thenReturn(Optional.of(player));
        
        // Act
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
        
        // Assert
        assertEquals(200, response.getStatusCode());
        Player responsePlayer = gson.fromJson(response.getBody(), Player.class);
        assertEquals(playerId, responsePlayer.getPlayerID());
        assertEquals("Test Player", responsePlayer.getPlayerName());
        
        verify(mockPlayerDao).getPlayerById(playerId);
    }

    @Test
    void getPlayer_NonExistentPlayer_ReturnsNotFound() {
        // Arrange
        String playerId = "nonexistent";
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players/" + playerId)
            .withHttpMethod("GET");
        
        when(mockPlayerDao.getPlayerById(playerId)).thenReturn(Optional.empty());
        
        // Act
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
        
        // Assert
        assertEquals(404, response.getStatusCode());
        Map<String, String> errorBody = gson.fromJson(response.getBody(), 
            new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Player not found", errorBody.get("message"));
        
        verify(mockPlayerDao).getPlayerById(playerId);
    }

    @Test
    void updatePlayer_ExistingPlayer_ReturnsUpdated() {
        // Arrange
        String playerId = "player123";
        Player existingPlayer = createTestPlayer(playerId, "Old Name");
        Player updatedPlayer = createTestPlayer(playerId, "New Name");
        String playerJson = gson.toJson(updatedPlayer);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players/" + playerId)
            .withHttpMethod("PUT")
            .withBody(playerJson);
        
        when(mockPlayerDao.getPlayerById(playerId)).thenReturn(Optional.of(existingPlayer));
        
        // Act
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
        
        // Assert
        assertEquals(200, response.getStatusCode());
        Player responsePlayer = gson.fromJson(response.getBody(), Player.class);
        assertEquals(playerId, responsePlayer.getPlayerID());
        assertEquals("New Name", responsePlayer.getPlayerName());
        
        verify(mockPlayerDao).getPlayerById(playerId);
        verify(mockPlayerDao).savePlayer(any(Player.class));
    }

    @Test
    void updatePlayer_NonExistentPlayer_ReturnsNotFound() {
        // Arrange
        String playerId = "nonexistent";
        Player updatedPlayer = createTestPlayer(playerId, "New Name");
        String playerJson = gson.toJson(updatedPlayer);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players/" + playerId)
            .withHttpMethod("PUT")
            .withBody(playerJson);
        
        when(mockPlayerDao.getPlayerById(playerId)).thenReturn(Optional.empty());
        
        // Act
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
        
        // Assert
        assertEquals(404, response.getStatusCode());
        Map<String, String> errorBody = gson.fromJson(response.getBody(), 
            new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Player not found", errorBody.get("message"));
        
        verify(mockPlayerDao).getPlayerById(playerId);
        verify(mockPlayerDao, never()).savePlayer(any(Player.class));
    }

    @Test
    void deletePlayer_ExistingPlayer_ReturnsNoContent() throws PlayerNotFoundException {
        // Arrange
        String playerId = "player123";
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players/" + playerId)
            .withHttpMethod("DELETE");
        
        // Act
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
        
        // Assert
        assertEquals(204, response.getStatusCode());
        
        verify(mockPlayerDao).deletePlayer(playerId);
    }

    @Test
    void deletePlayer_NonExistentPlayer_ReturnsNotFound() throws PlayerNotFoundException {
        // Arrange
        String playerId = "nonexistent";
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players/" + playerId)
            .withHttpMethod("DELETE");
        
        doThrow(new PlayerNotFoundException("Player not found: " + playerId))
            .when(mockPlayerDao).deletePlayer(playerId);
        
        // Act
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
        
        // Assert
        assertEquals(404, response.getStatusCode());
        Map<String, String> errorBody = gson.fromJson(response.getBody(), 
            new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Player not found: " + playerId, errorBody.get("message"));
        
        verify(mockPlayerDao).deletePlayer(playerId);
    }

    @Test
    void getMe_ValidAuthentication_ReturnsPlayerProfile() {
        // Arrange
        String playerId = "player123";
        Player player = createTestPlayer(playerId, "Test Player");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + playerId);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players/me")
            .withHttpMethod("GET")
            .withHeaders(headers);
        
        // Mock HandlerUtils and DAO
        try (MockedStatic<HandlerUtils> handlerUtils = Mockito.mockStatic(HandlerUtils.class)) {
            handlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(request)).thenReturn(Optional.of(playerId));
            handlerUtils.when(HandlerUtils::getResponseHeaders).thenCallRealMethod();
            handlerUtils.when(() -> HandlerUtils.isPreflightRequest(request)).thenCallRealMethod();
            
            when(mockPlayerDao.getPlayerById(playerId)).thenReturn(Optional.of(player));
            
            // Act
            APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
            
            // Assert
            assertEquals(200, response.getStatusCode());
            Player responsePlayer = gson.fromJson(response.getBody(), Player.class);
            assertEquals(playerId, responsePlayer.getPlayerID());
            assertEquals("Test Player", responsePlayer.getPlayerName());
            
            verify(mockPlayerDao).getPlayerById(playerId);
        }
    }

    @Test
    void handleRequest_InvalidRoute_ReturnsNotFound() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players/invalid/route")
            .withHttpMethod("GET");
        
        // Act
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
        
        // Assert
        assertEquals(404, response.getStatusCode());
        Map<String, String> errorBody = gson.fromJson(response.getBody(), 
            new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Route not found", errorBody.get("message"));
    }

    @Test
    void handleRequest_InvalidJson_ReturnsInternalServerError() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/players")
            .withHttpMethod("POST")
            .withBody("{invalid json}");
        
        // Act
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
        
        // Assert
        assertEquals(500, response.getStatusCode());
        Map<String, String> errorBody = gson.fromJson(response.getBody(), 
            new TypeToken<Map<String, String>>(){}.getType());
        assertEquals("Internal Server Error", errorBody.get("message"));
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    private Player createTestPlayer(String playerId, String playerName) {
        Player player = new Player();
        player.setPlayerID(playerId);
        player.setPlayerName(playerName);
        player.setEmail(playerName.toLowerCase().replace(" ", "") + "@test.com");
        player.setStatus(PlayerStatus.ACTIVE.name());
        player.setLatitude(40.7128);
        player.setLongitude(-74.0060);
        return player;
    }
} 