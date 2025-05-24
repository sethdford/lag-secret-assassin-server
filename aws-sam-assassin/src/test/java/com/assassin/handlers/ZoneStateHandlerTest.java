package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.dao.GameDao;
import com.assassin.dao.GameZoneStateDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GameStateException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.GameZoneState;
import com.assassin.service.ShrinkingZoneService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZoneStateHandlerTest {

    @Mock
    private GameDao gameDao;

    @Mock
    private GameZoneStateDao gameZoneStateDao;

    @Mock
    private ShrinkingZoneService shrinkingZoneService;

    @Mock
    private Context context;

    private ZoneStateHandler handler;
    private final Gson gson = new Gson();

    private Game testGame;
    private GameZoneState testZoneState;

    @BeforeEach
    void setUp() {
        handler = new ZoneStateHandler(gameDao, gameZoneStateDao, shrinkingZoneService);
        
        // Setup test game with shrinking zone enabled
        testGame = new Game();
        testGame.setGameID("test-game-1");
        testGame.setShrinkingZoneEnabled(true);
        testGame.setStatus("ACTIVE");

        // Setup test zone state
        testZoneState = new GameZoneState();
        testZoneState.setGameId("test-game-1");
        testZoneState.setCurrentStageIndex(2);
        testZoneState.setCurrentPhase(GameZoneState.ZonePhase.SHRINKING.name());
        testZoneState.setCurrentRadiusMeters(1500.0);
        testZoneState.setCurrentCenter(new Coordinate(40.7128, -74.0060)); // NYC coordinates
        testZoneState.setPhaseEndTime(Instant.now().plusSeconds(300).toString()); // 5 minutes from now
        testZoneState.setLastUpdated(Instant.now().toString());
        testZoneState.setNextRadiusMeters(1000.0);
    }

    @Test
    void getZoneState_ShouldReturnDetailedState_WhenZoneEnabled() throws Exception {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/games/test-game-1/zone/state");
        when(gameDao.getGameById("test-game-1")).thenReturn(Optional.of(testGame));
        when(shrinkingZoneService.advanceZoneState("test-game-1")).thenReturn(Optional.of(testZoneState));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        
        JsonObject responseJson = gson.fromJson(response.getBody(), JsonObject.class);
        assertEquals("test-game-1", responseJson.get("gameId").getAsString());
        assertTrue(responseJson.get("shrinkingZoneEnabled").getAsBoolean());
        assertEquals(2, responseJson.get("currentStageIndex").getAsInt());
        assertEquals("SHRINKING", responseJson.get("currentPhase").getAsString());
        assertEquals(1500.0, responseJson.get("currentRadiusMeters").getAsDouble());
        assertEquals(1000.0, responseJson.get("nextRadiusMeters").getAsDouble());
        
        JsonObject center = responseJson.getAsJsonObject("currentCenter");
        assertEquals(40.7128, center.get("latitude").getAsDouble());
        assertEquals(-74.0060, center.get("longitude").getAsDouble());
        
        assertTrue(responseJson.has("timeRemainingSeconds"));
        assertTrue(responseJson.get("timeRemainingSeconds").getAsLong() > 0);
        
        verify(gameDao).getGameById("test-game-1");
        verify(shrinkingZoneService).advanceZoneState("test-game-1");
    }

    @Test
    void getZoneState_ShouldReturnDisabledMessage_WhenZoneDisabled() throws Exception {
        // Arrange
        testGame.setShrinkingZoneEnabled(false);
        APIGatewayProxyRequestEvent request = createRequest("GET", "/games/test-game-1/zone/state");
        when(gameDao.getGameById("test-game-1")).thenReturn(Optional.of(testGame));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        
        JsonObject responseJson = gson.fromJson(response.getBody(), JsonObject.class);
        assertEquals("test-game-1", responseJson.get("gameId").getAsString());
        assertFalse(responseJson.get("shrinkingZoneEnabled").getAsBoolean());
        assertTrue(responseJson.has("message"));
        
        verify(gameDao).getGameById("test-game-1");
        verifyNoInteractions(shrinkingZoneService);
    }

    @Test
    void getZoneState_ShouldReturn404_WhenGameNotFound() throws Exception {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/games/nonexistent-game/zone/state");
        when(gameDao.getGameById("nonexistent-game")).thenReturn(Optional.empty());

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Game not found"));
        
        verify(gameDao).getGameById("nonexistent-game");
        verifyNoInteractions(shrinkingZoneService);
    }

    @Test
    void getZoneState_ShouldReturn404_WhenZoneStateNotFound() throws Exception {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/games/test-game-1/zone/state");
        when(gameDao.getGameById("test-game-1")).thenReturn(Optional.of(testGame));
        when(shrinkingZoneService.advanceZoneState("test-game-1")).thenReturn(Optional.empty());

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Zone state not found"));
        
        verify(gameDao).getGameById("test-game-1");
        verify(shrinkingZoneService).advanceZoneState("test-game-1");
    }

    @Test
    void getZoneState_ShouldReturn400_WhenGameIdMissing() throws Exception {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/games//zone/state");
        request.setPathParameters(new HashMap<>()); // Empty path parameters

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Zone state endpoint not found"));
        
        verifyNoInteractions(gameDao, shrinkingZoneService);
    }

    @Test
    void getZoneStatus_ShouldReturnSimpleStatus_WhenZoneActive() throws Exception {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/games/test-game-1/zone/status");
        Coordinate center = new Coordinate(40.7128, -74.0060);
        
        when(gameDao.getGameById("test-game-1")).thenReturn(Optional.of(testGame));
        when(shrinkingZoneService.getCurrentZoneCenter("test-game-1")).thenReturn(Optional.of(center));
        when(shrinkingZoneService.getCurrentZoneRadius("test-game-1")).thenReturn(Optional.of(1500.0));
        when(gameZoneStateDao.getGameZoneState("test-game-1")).thenReturn(Optional.of(testZoneState));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        
        JsonObject responseJson = gson.fromJson(response.getBody(), JsonObject.class);
        assertEquals("test-game-1", responseJson.get("gameId").getAsString());
        assertTrue(responseJson.get("shrinkingZoneEnabled").getAsBoolean());
        assertEquals("active", responseJson.get("status").getAsString());
        assertEquals(1500.0, responseJson.get("currentRadiusMeters").getAsDouble());
        assertEquals("SHRINKING", responseJson.get("currentPhase").getAsString());
        assertEquals(2, responseJson.get("currentStageIndex").getAsInt());
        
        JsonObject centerJson = responseJson.getAsJsonObject("currentCenter");
        assertEquals(40.7128, centerJson.get("latitude").getAsDouble());
        assertEquals(-74.0060, centerJson.get("longitude").getAsDouble());
        
        verify(gameDao).getGameById("test-game-1");
        verify(shrinkingZoneService).getCurrentZoneCenter("test-game-1");
        verify(shrinkingZoneService).getCurrentZoneRadius("test-game-1");
        verify(gameZoneStateDao).getGameZoneState("test-game-1");
    }

    @Test
    void getZoneStatus_ShouldReturnDisabledStatus_WhenZoneDisabled() throws Exception {
        // Arrange
        testGame.setShrinkingZoneEnabled(false);
        APIGatewayProxyRequestEvent request = createRequest("GET", "/games/test-game-1/zone/status");
        when(gameDao.getGameById("test-game-1")).thenReturn(Optional.of(testGame));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        
        JsonObject responseJson = gson.fromJson(response.getBody(), JsonObject.class);
        assertEquals("test-game-1", responseJson.get("gameId").getAsString());
        assertFalse(responseJson.get("shrinkingZoneEnabled").getAsBoolean());
        assertEquals("disabled", responseJson.get("status").getAsString());
        
        verify(gameDao).getGameById("test-game-1");
        verifyNoInteractions(shrinkingZoneService, gameZoneStateDao);
    }

    @Test
    void getZoneStatus_ShouldReturnNotAvailable_WhenZoneStateEmpty() throws Exception {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/games/test-game-1/zone/status");
        when(gameDao.getGameById("test-game-1")).thenReturn(Optional.of(testGame));
        when(shrinkingZoneService.getCurrentZoneCenter("test-game-1")).thenReturn(Optional.empty());
        when(shrinkingZoneService.getCurrentZoneRadius("test-game-1")).thenReturn(Optional.empty());

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        
        JsonObject responseJson = gson.fromJson(response.getBody(), JsonObject.class);
        assertEquals("test-game-1", responseJson.get("gameId").getAsString());
        assertTrue(responseJson.get("shrinkingZoneEnabled").getAsBoolean());
        assertEquals("not_available", responseJson.get("status").getAsString());
        assertTrue(responseJson.has("message"));
        
        verify(gameDao).getGameById("test-game-1");
        verify(shrinkingZoneService).getCurrentZoneCenter("test-game-1");
        verify(shrinkingZoneService).getCurrentZoneRadius("test-game-1");
    }

    @Test
    void handleRequest_ShouldReturn404_WhenRouteNotFound() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/games/test-game-1/zone/invalid");

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Zone state endpoint not found"));
        
        verifyNoInteractions(gameDao, shrinkingZoneService, gameZoneStateDao);
    }

    @Test
    void handleRequest_ShouldReturn500_WhenGameStateExceptionThrown() throws Exception {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/games/test-game-1/zone/state");
        when(gameDao.getGameById("test-game-1")).thenReturn(Optional.of(testGame));
        when(shrinkingZoneService.advanceZoneState("test-game-1"))
                .thenThrow(new GameStateException("Zone configuration error"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Zone state error"));
        
        verify(gameDao).getGameById("test-game-1");
        verify(shrinkingZoneService).advanceZoneState("test-game-1");
    }

    private APIGatewayProxyRequestEvent createRequest(String httpMethod, String path) {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod(httpMethod);
        request.setPath(path);
        
        Map<String, String> pathParameters = new HashMap<>();
        if (path.contains("/games/")) {
            String[] parts = path.split("/");
            if (parts.length > 2) {
                pathParameters.put("gameId", parts[2]);
            }
        }
        request.setPathParameters(pathParameters);
        
        return request;
    }
} 