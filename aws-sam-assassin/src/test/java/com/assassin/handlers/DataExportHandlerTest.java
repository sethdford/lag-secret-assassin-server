package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.model.export.GameStatisticsExport;
import com.assassin.model.export.LocationHeatmapData;
import com.assassin.model.export.PlayerPerformanceExport;
import com.assassin.service.DataExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataExportHandlerTest {

    @Mock
    private DataExportService dataExportService;

    @Mock
    private Context context;

    private DataExportHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        handler = new DataExportHandler(dataExportService);
        objectMapper = new ObjectMapper();
    }

    @Test
    void testExportGameStatistics_JSON() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/export/games", 
            Map.of("format", "json", "limit", "10"));
        
        List<GameStatisticsExport> mockData = Arrays.asList(
            createMockGameStatistics("game1", "Test Game 1"),
            createMockGameStatistics("game2", "Test Game 2")
        );
        
        when(dataExportService.exportGameStatistics(null, null, null, 10))
            .thenReturn(mockData);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        assertTrue(response.getBody().contains("game1"));
        assertTrue(response.getBody().contains("Test Game 1"));
        
        verify(dataExportService).exportGameStatistics(null, null, null, 10);
    }

    @Test
    void testExportGameStatistics_CSV() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/export/games", 
            Map.of("format", "csv", "status", "ACTIVE"));
        
        List<GameStatisticsExport> mockData = Arrays.asList(
            createMockGameStatistics("game1", "Test Game 1")
        );
        
        when(dataExportService.exportGameStatistics(null, null, "ACTIVE", 100))
            .thenReturn(mockData);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertEquals("text/csv", response.getHeaders().get("Content-Type"));
        assertTrue(response.getBody().contains("game_id,game_name"));
        assertTrue(response.getBody().contains("game1,Test Game 1"));
        assertTrue(response.getHeaders().get("Content-Disposition").contains("attachment"));
        
        verify(dataExportService).exportGameStatistics(null, null, "ACTIVE", 100);
    }

    @Test
    void testExportPlayerPerformance_JSON() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/export/players", 
            Map.of("player_ids", "player1,player2", "format", "json"));
        
        List<PlayerPerformanceExport> mockData = Arrays.asList(
            createMockPlayerPerformance("player_123", "ACTIVE"),
            createMockPlayerPerformance("player_456", "ACTIVE")
        );
        
        when(dataExportService.exportPlayerPerformance(eq(null), eq(null), 
            eq(Arrays.asList("player1", "player2")), eq(100)))
            .thenReturn(mockData);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        assertTrue(response.getBody().contains("player_123"));
        
        verify(dataExportService).exportPlayerPerformance(eq(null), eq(null), 
            eq(Arrays.asList("player1", "player2")), eq(100));
    }

    @Test
    void testExportLocationHeatmap_JSON() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/export/locations", 
            Map.of("game_id", "game123", "event_type", "kill"));
        
        List<LocationHeatmapData> mockData = Arrays.asList(
            createMockLocationData(40.7128, -74.0060, "game123"),
            createMockLocationData(40.7589, -73.9851, "game123")
        );
        
        when(dataExportService.exportLocationHeatmapData(null, null, "game123", "kill", 100))
            .thenReturn(mockData);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        assertTrue(response.getBody().contains("40.7128"));
        assertTrue(response.getBody().contains("game123"));
        
        verify(dataExportService).exportLocationHeatmapData(null, null, "game123", "kill", 100);
    }

    @Test
    void testExportAggregatedStatistics() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/export/statistics", 
            Map.of("start_date", "2024-01-01T00:00:00Z"));
        
        Map<String, Object> mockStats = Map.of(
            "total_games", 50,
            "total_players", 200,
            "total_kills", 1500
        );
        
        when(dataExportService.getAggregatedStatistics("2024-01-01T00:00:00Z", null))
            .thenReturn(mockStats);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        assertTrue(response.getBody().contains("total_games"));
        assertTrue(response.getBody().contains("50"));
        
        verify(dataExportService).getAggregatedStatistics("2024-01-01T00:00:00Z", null);
    }

    @Test
    void testUnsupportedHttpMethod() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("POST", "/export/games", null);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(405, response.getStatusCode());
        assertTrue(response.getBody().contains("Method Not Allowed"));
    }

    @Test
    void testUnsupportedRoute() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/export/unknown", null);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Not Found"));
    }

    @Test
    void testServiceException() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/export/games", null);
        
        when(dataExportService.exportGameStatistics(any(), any(), any(), anyInt()))
            .thenThrow(new RuntimeException("Database error"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Failed to export game statistics"));
    }

    @Test
    void testLimitParsing() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/export/games", 
            Map.of("limit", "invalid"));
        
        when(dataExportService.exportGameStatistics(any(), any(), any(), eq(100)))
            .thenReturn(Arrays.asList());

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        verify(dataExportService).exportGameStatistics(any(), any(), any(), eq(100)); // Default limit
    }

    @Test
    void testLimitExceedsMaximum() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/export/games", 
            Map.of("limit", "50000"));
        
        when(dataExportService.exportGameStatistics(any(), any(), any(), eq(10000)))
            .thenReturn(Arrays.asList());

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        verify(dataExportService).exportGameStatistics(any(), any(), any(), eq(10000)); // Max limit
    }

    @Test
    void testCORSHeaders() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/export/games", null);
        
        when(dataExportService.exportGameStatistics(any(), any(), any(), anyInt()))
            .thenReturn(Arrays.asList());

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals("*", response.getHeaders().get("Access-Control-Allow-Origin"));
        assertEquals("GET, OPTIONS", response.getHeaders().get("Access-Control-Allow-Methods"));
        assertEquals("Content-Type, Authorization", response.getHeaders().get("Access-Control-Allow-Headers"));
    }

    // Helper methods

    private APIGatewayProxyRequestEvent createRequest(String method, String path, Map<String, String> queryParams) {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod(method);
        request.setPath(path);
        request.setQueryStringParameters(queryParams);
        return request;
    }

    private GameStatisticsExport createMockGameStatistics(String gameId, String gameName) {
        GameStatisticsExport export = new GameStatisticsExport();
        export.setGameId(gameId);
        export.setGameName(gameName);
        export.setStatus("ACTIVE");
        export.setCreatedAt("2024-01-01T00:00:00Z");
        export.setPlayerCount(10);
        export.setTotalKills(25);
        export.setCompletionRate(75.0);
        return export;
    }

    private PlayerPerformanceExport createMockPlayerPerformance(String playerId, String status) {
        PlayerPerformanceExport export = new PlayerPerformanceExport();
        export.setPlayerId(playerId);
        export.setStatus(status);
        export.setTotalKills(15);
        export.setTotalDeaths(5);
        export.setKillDeathRatio(3.0);
        export.setGamesPlayed(10);
        export.setAverageKillsPerGame(1.5);
        export.setAccuracyRate(85.0);
        export.setSuccessRate(60.0);
        return export;
    }

    private LocationHeatmapData createMockLocationData(double lat, double lng, String gameId) {
        LocationHeatmapData data = new LocationHeatmapData();
        data.setLatitude(lat);
        data.setLongitude(lng);
        data.setGameId(gameId);
        data.setEventType("kill");
        data.setIntensity(1.0);
        data.setTimestamp("2024-01-01T12:00:00Z");
        data.setKillerId("player_123");
        data.setVictimId("player_456");
        data.setVerificationStatus("VERIFIED");
        data.setZoneType("normal");
        return data;
    }
} 