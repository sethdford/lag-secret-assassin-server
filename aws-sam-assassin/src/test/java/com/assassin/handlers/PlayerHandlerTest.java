package com.assassin.handlers;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.dao.PlayerDao;
import com.assassin.model.Player;
import com.assassin.service.PlayerService;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@ExtendWith(MockitoExtension.class)
class PlayerHandlerTest {

    @Mock
    private PlayerService mockPlayerService;
    @Mock
    private Context mockContext;
    @Mock
    private PlayerDao playerDao;

    @InjectMocks
    private PlayerHandler playerHandler;

    private final Gson gson = new Gson();
    private final String testPlayerId = "test-player-123"; // Consolidated to one declaration
    private MockedStatic<HandlerUtils> mockedHandlerUtils;

    @BeforeEach
    void setUp() {
        mockedHandlerUtils = mockStatic(HandlerUtils.class);
    }

    @AfterEach
    void tearDown() {
        mockedHandlerUtils.close();
    }

    // Helper method to create test requests for brevity, if it was present before and removed by mistake.
    // If not, this can be omitted and tests can create requests directly.
    private APIGatewayProxyRequestEvent createTestRequest(String httpMethod, String path, String body, Map<String, String> pathParameters, Map<String, String> cognitoContext) {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withHttpMethod(httpMethod)
            .withPath(path)
            .withBody(body)
            .withPathParameters(pathParameters);
        if (cognitoContext != null) {
            APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
            requestContext.setAuthorizer(Map.of("claims", (Map<String, Object>) (Map<?, ?>) cognitoContext));
            request.setRequestContext(requestContext);
        }
        return request;
    }

    // --- Tests for GET /players/{id} ---
    @Test
    void getPlayerById_success() {
        Player mockPlayer = new Player();
        mockPlayer.setPlayerID(testPlayerId);
        mockPlayer.setPlayerName("TestUser");
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(mockPlayer));

        APIGatewayProxyRequestEvent request = createTestRequest("GET", "/players/" + testPlayerId, null, Map.of("id", testPlayerId), null);
        request.setPathParameters(null);
        request.setPath("/players/" + testPlayerId);

        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);

        assertEquals(200, response.getStatusCode());
        // Further assertions on body
    }

    // ... other existing tests for GET /players/{id}

    // --- Tests for PUT /players/{id} ---
    // ... (existing tests using createTestRequest or direct setup)

    // --- Tests for DELETE /players/{id} ---
    // ... (existing tests using createTestRequest or direct setup)

    // --- Tests for GET /players/me ---
    @Test
    void handleGetMyPlayerProfile_success() {
        mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(any(APIGatewayProxyRequestEvent.class))).thenReturn(Optional.of(testPlayerId));
        Player mockPlayer = new Player();
        mockPlayer.setPlayerID(testPlayerId);
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(mockPlayer));

        APIGatewayProxyRequestEvent request = createTestRequest("GET", "/players/me", null, null, Map.of("sub", testPlayerId)); // Using helper
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);

        assertEquals(200, response.getStatusCode());
    }
    
    // ... other existing tests for GET /players/me ...

    // --- Tests for PUT /players/me ---
    // ... (existing tests using createTestRequest or direct setup, and HandlerUtils mock)

    // --- Tests for GET /players/me/settings/location-visibility ---
    @Test
    void handleGetLocationVisibilitySettings_success() {
        mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(any(APIGatewayProxyRequestEvent.class))).thenReturn(Optional.of(testPlayerId));
        when(mockPlayerService.getLocationVisibilitySettings(testPlayerId)).thenReturn(Optional.of(Player.LocationVisibility.VISIBLE_TO_ALL_IN_GAME));

        APIGatewayProxyRequestEvent request = createTestRequest("GET", "/players/me/settings/location-visibility", null, null, Map.of("sub", testPlayerId));
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);

        assertEquals(200, response.getStatusCode());
        Map<String, Object> responseBodyMap = gson.fromJson(response.getBody(), new TypeToken<Map<String, Object>>(){}.getType());
        assertEquals(Player.LocationVisibility.VISIBLE_TO_ALL_IN_GAME.name(), responseBodyMap.get("locationVisibility"));
    }

    @Test
    void handleGetLocationVisibilitySettings_playerNotFound_returns404() {
        mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(any(APIGatewayProxyRequestEvent.class))).thenReturn(Optional.of(testPlayerId));
        when(mockPlayerService.getLocationVisibilitySettings(testPlayerId)).thenReturn(Optional.empty());

        APIGatewayProxyRequestEvent request = createTestRequest("GET", "/players/me/settings/location-visibility", null, null, Map.of("sub", testPlayerId));
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);

        assertEquals(404, response.getStatusCode());
        Map<String, Object> responseBodyMap = gson.fromJson(response.getBody(), new TypeToken<Map<String, Object>>(){}.getType());
        assertTrue(responseBodyMap.get("message").toString().contains("Location visibility settings not found or player does not exist."));
    }

    @Test
    void handleGetLocationVisibilitySettings_noPlayerIdInContext_returns400() {
        mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(any(APIGatewayProxyRequestEvent.class))).thenReturn(Optional.empty());

        APIGatewayProxyRequestEvent request = createTestRequest("GET", "/players/me/settings/location-visibility", null, null, null); // No cognito context implies no player id from HandlerUtils
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);

        assertEquals(400, response.getStatusCode());
        Map<String, Object> responseBodyMap = gson.fromJson(response.getBody(), new TypeToken<Map<String, Object>>(){}.getType());
        assertTrue(responseBodyMap.get("message").toString().contains("Player ID not found"));
    }

    // --- Tests for PUT /players/me/settings/location-visibility ---
    // ... (Ensure HandlerUtils mock is used here if calls to HandlerUtils.getPlayerIdFromRequest are made)
    @Test
    void handleUpdateLocationVisibilitySettings_success() {
        mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(any(APIGatewayProxyRequestEvent.class))).thenReturn(Optional.of(testPlayerId));
        String requestBody = gson.toJson(Map.of("locationVisibility", "VISIBLE_TO_FRIENDS_IN_GAME"));
        Player updatedPlayer = new Player();
        updatedPlayer.setPlayerID(testPlayerId);
        updatedPlayer.setLocationVisibility(Player.LocationVisibility.VISIBLE_TO_FRIENDS_IN_GAME);

        when(mockPlayerService.updateLocationVisibilitySettings(eq(testPlayerId), eq(Player.LocationVisibility.VISIBLE_TO_FRIENDS_IN_GAME)))
            .thenReturn(updatedPlayer);

        APIGatewayProxyRequestEvent request = createTestRequest("PUT", "/players/me/settings/location-visibility", requestBody, null, Map.of("sub", testPlayerId));
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);

        assertEquals(200, response.getStatusCode());
        // ... more assertions
    }


    // --- Tests for POST /players/me/location/pause ---
    // ... (Ensure HandlerUtils mock is used here if calls to HandlerUtils.getPlayerIdFromRequest are made)
    @Test
    void handlePauseLocationSharing_success() {
        mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(any(APIGatewayProxyRequestEvent.class))).thenReturn(Optional.of(testPlayerId));
        Player pausedPlayer = new Player(); // Assume service returns the updated player
        pausedPlayer.setPlayerID(testPlayerId);
        pausedPlayer.setLocationSharingPaused(true);
        when(mockPlayerService.pauseLocationSharing(testPlayerId)).thenReturn(pausedPlayer);

        APIGatewayProxyRequestEvent request = createTestRequest("POST", "/players/me/location/pause", null, null, Map.of("sub", testPlayerId));
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
        assertEquals(200, response.getStatusCode());
        // ... assertions
    }

    // --- Tests for POST /players/me/location/resume ---
    // ... (Ensure HandlerUtils mock is used here if calls to HandlerUtils.getPlayerIdFromRequest are made)
    @Test
    void handleResumeLocationSharing_success() {
        mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(any(APIGatewayProxyRequestEvent.class))).thenReturn(Optional.of(testPlayerId));
        Player resumedPlayer = new Player();
        resumedPlayer.setPlayerID(testPlayerId);
        resumedPlayer.setLocationSharingPaused(false);
        when(mockPlayerService.resumeLocationSharing(testPlayerId)).thenReturn(resumedPlayer);

        APIGatewayProxyRequestEvent request = createTestRequest("POST", "/players/me/location/resume", null, null, Map.of("sub", testPlayerId));
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
        assertEquals(200, response.getStatusCode());
        // ... assertions
    }


    // --- Tests for PUT /players/me/settings/location-precision ---
    // ... (Ensure HandlerUtils mock is used here if calls to HandlerUtils.getPlayerIdFromRequest are made)
    @Test
    void handleUpdateLocationPrecisionSettings_success() {
        mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(any(APIGatewayProxyRequestEvent.class))).thenReturn(Optional.of(testPlayerId));
        String requestBody = gson.toJson(Map.of("locationPrecision", "REDUCED_500M"));
        Player updatedPlayer = new Player();
        updatedPlayer.setPlayerID(testPlayerId);
        updatedPlayer.setLocationPrecision(Player.LocationPrecision.REDUCED_500M);
        when(mockPlayerService.updateLocationPrecisionSettings(eq(testPlayerId), eq(Player.LocationPrecision.REDUCED_500M)))
            .thenReturn(updatedPlayer);

        APIGatewayProxyRequestEvent request = createTestRequest("PUT", "/players/me/settings/location-precision", requestBody, null, Map.of("sub", testPlayerId));
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
        assertEquals(200, response.getStatusCode());
        // ... assertions
    }

    // --- Tests for GET /players/me/settings/location-precision ---
    @Test
    void handleGetLocationPrecisionSettings_success() {
        mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(any(APIGatewayProxyRequestEvent.class))).thenReturn(Optional.of(testPlayerId));
        when(mockPlayerService.getLocationPrecisionSettings(testPlayerId)).thenReturn(Optional.of(Player.LocationPrecision.REDUCED_100M));

        APIGatewayProxyRequestEvent request = createTestRequest("GET", "/players/me/settings/location-precision", null, null, Map.of("sub", testPlayerId));
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);

        assertEquals(200, response.getStatusCode());
        Map<String, Object> responseBodyMap = gson.fromJson(response.getBody(), new TypeToken<Map<String, Object>>(){}.getType());
        assertEquals(Player.LocationPrecision.REDUCED_100M.name(), responseBodyMap.get("locationPrecision"));
    }

    @Test
    void handleGetLocationPrecisionSettings_playerNotFound_returns404() {
        mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(any(APIGatewayProxyRequestEvent.class))).thenReturn(Optional.of(testPlayerId));
        when(mockPlayerService.getLocationPrecisionSettings(testPlayerId)).thenReturn(Optional.empty());

        APIGatewayProxyRequestEvent request = createTestRequest("GET", "/players/me/settings/location-precision", null, null, Map.of("sub", testPlayerId));
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);

        assertEquals(404, response.getStatusCode());
         Map<String, Object> responseBodyMap = gson.fromJson(response.getBody(), new TypeToken<Map<String, Object>>(){}.getType());
        assertTrue(responseBodyMap.get("message").toString().contains("Player or location precision settings not found"));
    }

    @Test
    void handleGetLocationPrecisionSettings_noPlayerIdInContext_returns400() {
        mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(any(APIGatewayProxyRequestEvent.class))).thenReturn(Optional.empty());

        APIGatewayProxyRequestEvent request = createTestRequest("GET", "/players/me/settings/location-precision", null, null, null);
        APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);

        assertEquals(400, response.getStatusCode());
        Map<String, Object> responseBodyMap = gson.fromJson(response.getBody(), new TypeToken<Map<String, Object>>(){}.getType());
        assertTrue(responseBodyMap.get("message").toString().contains("Player ID not found"));
    }

}