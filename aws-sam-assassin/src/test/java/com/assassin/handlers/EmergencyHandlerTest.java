package com.assassin.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GameStateException;
import com.assassin.exception.UnauthorizedException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Game;
import com.assassin.service.EmergencyService;
import com.assassin.util.RequestUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Unit tests for EmergencyHandler.
 * Tests all emergency endpoints including pause, resume, and status checks.
 */
@ExtendWith(MockitoExtension.class)
class EmergencyHandlerTest {

    @Mock
    private EmergencyService emergencyService;

    @Mock
    private Context context;

    private EmergencyHandler emergencyHandler;
    private final Gson gson = new Gson();

    private final String gameId = "test-game-123";
    private final String adminPlayerId = "admin-player-456";
    private final String emergencyReason = "Safety concern reported";

    @BeforeEach
    void setUp() {
        emergencyHandler = new EmergencyHandler(emergencyService);
    }

    // ==================== Route Resolution Tests ====================

    @Test
    void handleRequest_MissingGameIdPathParameter_ReturnsBadRequest() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games//emergency/pause")
            .withHttpMethod("POST")
            .withPathParameters(null);

        // Act
        APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Missing required gameId path parameter"));
    }

    @Test
    void handleRequest_EmptyPathParameters_ReturnsBadRequest() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/123/emergency/pause")
            .withHttpMethod("POST")
            .withPathParameters(new HashMap<>());

        // Act
        APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Missing required gameId path parameter"));
    }

    @Test
    void handleRequest_InvalidEndpoint_ReturnsNotFound() {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/invalid")
            .withHttpMethod("POST")
            .withPathParameters(pathParams);

        // Act
        APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Emergency endpoint not found"));
    }

    @Test
    void handleRequest_UnexpectedException_ReturnsInternalServerError() {
        // Arrange - Create a mock request that will cause an exception during request processing
        // Use a spy on the request to make getPathParameters() throw an exception
        APIGatewayProxyRequestEvent request = Mockito.spy(new APIGatewayProxyRequestEvent());
        when(request.getPath()).thenReturn("/games/" + gameId + "/emergency/pause");
        when(request.getHttpMethod()).thenReturn("POST");
        when(request.getPathParameters()).thenThrow(new RuntimeException("Unexpected error"));

        // Act
        APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Internal server error"));
    }

    // ==================== Pause Game Tests ====================

    @Test
    void handlePauseGame_Success_ValidRequest() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("reason", emergencyReason);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/pause")
            .withHttpMethod("POST")
            .withPathParameters(pathParams)
            .withBody(gson.toJson(requestBody));

        Game updatedGame = createMockGame();
        updatedGame.setEmergencyPause(true);
        updatedGame.setEmergencyReason(emergencyReason);
        updatedGame.setEmergencyTimestamp("2023-04-01T12:00:00Z");
        updatedGame.setEmergencyTriggeredBy(adminPlayerId);

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn(adminPlayerId);

            when(emergencyService.pauseGame(gameId, emergencyReason, adminPlayerId))
                .thenReturn(updatedGame);

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(200, response.getStatusCode());
            
            JsonObject responseBody = gson.fromJson(response.getBody(), JsonObject.class);
            assertEquals("Game paused successfully due to emergency", responseBody.get("message").getAsString());
            assertEquals(gameId, responseBody.get("gameId").getAsString());
            assertTrue(responseBody.get("emergencyPause").getAsBoolean());
            assertEquals(emergencyReason, responseBody.get("emergencyReason").getAsString());
            assertEquals("2023-04-01T12:00:00Z", responseBody.get("emergencyTimestamp").getAsString());
            assertEquals(adminPlayerId, responseBody.get("emergencyTriggeredBy").getAsString());

            verify(emergencyService).pauseGame(gameId, emergencyReason, adminPlayerId);
        }
    }

    @Test
    void handlePauseGame_MissingAuthentication_ReturnsUnauthorized() {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("reason", emergencyReason);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/pause")
            .withHttpMethod("POST")
            .withPathParameters(pathParams)
            .withBody(gson.toJson(requestBody));

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn(null);

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(401, response.getStatusCode());
            assertTrue(response.getBody().contains("Authentication required"));
            verify(emergencyService, never()).pauseGame(any(), any(), any());
        }
    }

    @Test
    void handlePauseGame_EmptyPlayerId_ReturnsUnauthorized() {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("reason", emergencyReason);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/pause")
            .withHttpMethod("POST")
            .withPathParameters(pathParams)
            .withBody(gson.toJson(requestBody));

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn("");

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(401, response.getStatusCode());
            assertTrue(response.getBody().contains("Authentication required"));
            verify(emergencyService, never()).pauseGame(any(), any(), any());
        }
    }

    @Test
    void handlePauseGame_MissingReason_ReturnsBadRequest() {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/pause")
            .withHttpMethod("POST")
            .withPathParameters(pathParams)
            .withBody("{}");

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn(adminPlayerId);

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(400, response.getStatusCode());
            assertTrue(response.getBody().contains("Emergency reason is required"));
            verify(emergencyService, never()).pauseGame(any(), any(), any());
        }
    }

    @Test
    void handlePauseGame_EmptyReason_ReturnsBadRequest() {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("reason", "");

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/pause")
            .withHttpMethod("POST")
            .withPathParameters(pathParams)
            .withBody(gson.toJson(requestBody));

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn(adminPlayerId);

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(400, response.getStatusCode());
            assertTrue(response.getBody().contains("Emergency reason is required"));
            verify(emergencyService, never()).pauseGame(any(), any(), any());
        }
    }

    @Test
    void handlePauseGame_ValidationException_ReturnsBadRequest() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("reason", emergencyReason);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/pause")
            .withHttpMethod("POST")
            .withPathParameters(pathParams)
            .withBody(gson.toJson(requestBody));

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn(adminPlayerId);

            when(emergencyService.pauseGame(gameId, emergencyReason, adminPlayerId))
                .thenThrow(new ValidationException("Invalid game ID"));

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(400, response.getStatusCode());
            assertTrue(response.getBody().contains("Invalid game ID"));
        }
    }

    @Test
    void handlePauseGame_UnauthorizedException_ReturnsForbidden() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("reason", emergencyReason);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/pause")
            .withHttpMethod("POST")
            .withPathParameters(pathParams)
            .withBody(gson.toJson(requestBody));

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn(adminPlayerId);

            when(emergencyService.pauseGame(gameId, emergencyReason, adminPlayerId))
                .thenThrow(new UnauthorizedException("Only administrators can pause games"));

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(403, response.getStatusCode());
            assertTrue(response.getBody().contains("Only administrators can pause games"));
        }
    }

    @Test
    void handlePauseGame_GameNotFoundException_ReturnsNotFound() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("reason", emergencyReason);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/pause")
            .withHttpMethod("POST")
            .withPathParameters(pathParams)
            .withBody(gson.toJson(requestBody));

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn(adminPlayerId);

            when(emergencyService.pauseGame(gameId, emergencyReason, adminPlayerId))
                .thenThrow(new GameNotFoundException("Game not found"));

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(404, response.getStatusCode());
            assertTrue(response.getBody().contains("Game not found"));
        }
    }

    @Test
    void handlePauseGame_GameStateException_ReturnsConflict() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("reason", emergencyReason);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/pause")
            .withHttpMethod("POST")
            .withPathParameters(pathParams)
            .withBody(gson.toJson(requestBody));

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn(adminPlayerId);

            when(emergencyService.pauseGame(gameId, emergencyReason, adminPlayerId))
                .thenThrow(new GameStateException("Game is already paused"));

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(409, response.getStatusCode());
            assertTrue(response.getBody().contains("Game is already paused"));
        }
    }

    @Test
    void handlePauseGame_UnexpectedException_ReturnsInternalServerError() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("reason", emergencyReason);

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/pause")
            .withHttpMethod("POST")
            .withPathParameters(pathParams)
            .withBody(gson.toJson(requestBody));

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn(adminPlayerId);

            when(emergencyService.pauseGame(gameId, emergencyReason, adminPlayerId))
                .thenThrow(new RuntimeException("Database connection failed"));

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(500, response.getStatusCode());
            assertTrue(response.getBody().contains("Failed to pause game"));
        }
    }

    // ==================== Resume Game Tests ====================

    @Test
    void handleResumeGame_Success_ValidRequest() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/resume")
            .withHttpMethod("POST")
            .withPathParameters(pathParams);

        Game updatedGame = createMockGame();
        updatedGame.setEmergencyPause(false);

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn(adminPlayerId);

            when(emergencyService.resumeGame(gameId, adminPlayerId))
                .thenReturn(updatedGame);

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(200, response.getStatusCode());
            
            JsonObject responseBody = gson.fromJson(response.getBody(), JsonObject.class);
            assertEquals("Game resumed successfully from emergency pause", responseBody.get("message").getAsString());
            assertEquals(gameId, responseBody.get("gameId").getAsString());
            assertFalse(responseBody.get("emergencyPause").getAsBoolean());

            verify(emergencyService).resumeGame(gameId, adminPlayerId);
        }
    }

    @Test
    void handleResumeGame_MissingAuthentication_ReturnsUnauthorized() {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/resume")
            .withHttpMethod("POST")
            .withPathParameters(pathParams);

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn(null);

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(401, response.getStatusCode());
            assertTrue(response.getBody().contains("Authentication required"));
            verify(emergencyService, never()).resumeGame(any(), any());
        }
    }

    @Test
    void handleResumeGame_ValidationException_ReturnsBadRequest() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/resume")
            .withHttpMethod("POST")
            .withPathParameters(pathParams);

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn(adminPlayerId);

            when(emergencyService.resumeGame(gameId, adminPlayerId))
                .thenThrow(new ValidationException("Invalid game ID"));

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(400, response.getStatusCode());
            assertTrue(response.getBody().contains("Invalid game ID"));
        }
    }

    @Test
    void handleResumeGame_UnauthorizedException_ReturnsForbidden() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/resume")
            .withHttpMethod("POST")
            .withPathParameters(pathParams);

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn(adminPlayerId);

            when(emergencyService.resumeGame(gameId, adminPlayerId))
                .thenThrow(new UnauthorizedException("Only administrators can resume games"));

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(403, response.getStatusCode());
            assertTrue(response.getBody().contains("Only administrators can resume games"));
        }
    }

    @Test
    void handleResumeGame_GameNotFoundException_ReturnsNotFound() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/resume")
            .withHttpMethod("POST")
            .withPathParameters(pathParams);

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn(adminPlayerId);

            when(emergencyService.resumeGame(gameId, adminPlayerId))
                .thenThrow(new GameNotFoundException("Game not found"));

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(404, response.getStatusCode());
            assertTrue(response.getBody().contains("Game not found"));
        }
    }

    @Test
    void handleResumeGame_GameStateException_ReturnsConflict() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/resume")
            .withHttpMethod("POST")
            .withPathParameters(pathParams);

        try (MockedStatic<RequestUtils> requestUtilsMock = Mockito.mockStatic(RequestUtils.class)) {
            requestUtilsMock.when(() -> RequestUtils.getPlayerIdFromRequest(request))
                .thenReturn(adminPlayerId);

            when(emergencyService.resumeGame(gameId, adminPlayerId))
                .thenThrow(new GameStateException("Game is not paused"));

            // Act
            APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

            // Assert
            assertEquals(409, response.getStatusCode());
            assertTrue(response.getBody().contains("Game is not paused"));
        }
    }

    // ==================== Get Emergency Status Tests ====================

    @Test
    void handleGetEmergencyStatus_Success_GameInEmergencyPause() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/status")
            .withHttpMethod("GET")
            .withPathParameters(pathParams);

        EmergencyService.EmergencyStatus status = new EmergencyService.EmergencyStatus(
            gameId, true, emergencyReason, "2023-04-01T12:00:00Z", adminPlayerId);

        when(emergencyService.getEmergencyStatus(gameId)).thenReturn(status);

        // Act
        APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        
        JsonObject responseBody = gson.fromJson(response.getBody(), JsonObject.class);
        assertEquals(gameId, responseBody.get("gameId").getAsString());
        assertTrue(responseBody.get("isInEmergencyPause").getAsBoolean());
        assertEquals(emergencyReason, responseBody.get("reason").getAsString());
        assertEquals("2023-04-01T12:00:00Z", responseBody.get("timestamp").getAsString());
        assertEquals(adminPlayerId, responseBody.get("triggeredBy").getAsString());

        verify(emergencyService).getEmergencyStatus(gameId);
    }

    @Test
    void handleGetEmergencyStatus_Success_GameNotInEmergencyPause() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/status")
            .withHttpMethod("GET")
            .withPathParameters(pathParams);

        EmergencyService.EmergencyStatus status = new EmergencyService.EmergencyStatus(
            gameId, false, null, null, null);

        when(emergencyService.getEmergencyStatus(gameId)).thenReturn(status);

        // Act
        APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        
        JsonObject responseBody = gson.fromJson(response.getBody(), JsonObject.class);
        assertEquals(gameId, responseBody.get("gameId").getAsString());
        assertFalse(responseBody.get("isInEmergencyPause").getAsBoolean());
        
        // When EmergencyStatus fields are null, Gson addProperty() omits them from JSON
        // So we need to check if they exist before checking if they're null
        if (responseBody.has("reason")) {
            assertTrue(responseBody.get("reason").isJsonNull());
        }
        if (responseBody.has("timestamp")) {
            assertTrue(responseBody.get("timestamp").isJsonNull());
        }
        if (responseBody.has("triggeredBy")) {
            assertTrue(responseBody.get("triggeredBy").isJsonNull());
        }

        verify(emergencyService).getEmergencyStatus(gameId);
    }

    @Test
    void handleGetEmergencyStatus_ValidationException_ReturnsBadRequest() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/status")
            .withHttpMethod("GET")
            .withPathParameters(pathParams);

        when(emergencyService.getEmergencyStatus(gameId))
            .thenThrow(new ValidationException("Invalid game ID"));

        // Act
        APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid game ID"));
    }

    @Test
    void handleGetEmergencyStatus_GameNotFoundException_ReturnsNotFound() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/status")
            .withHttpMethod("GET")
            .withPathParameters(pathParams);

        when(emergencyService.getEmergencyStatus(gameId))
            .thenThrow(new GameNotFoundException("Game not found"));

        // Act
        APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Game not found"));
    }

    @Test
    void handleGetEmergencyStatus_UnexpectedException_ReturnsInternalServerError() throws Exception {
        // Arrange
        Map<String, String> pathParams = Map.of("gameId", gameId);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/games/" + gameId + "/emergency/status")
            .withHttpMethod("GET")
            .withPathParameters(pathParams);

        when(emergencyService.getEmergencyStatus(gameId))
            .thenThrow(new RuntimeException("Database connection failed"));

        // Act
        APIGatewayProxyResponseEvent response = emergencyHandler.handleRequest(request, context);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Failed to get emergency status"));
    }

    // ==================== Helper Methods ====================

    private Game createMockGame() {
        Game game = new Game();
        game.setGameID(gameId);
        game.setAdminPlayerID(adminPlayerId);
        game.setEmergencyPause(false);
        return game;
    }
} 