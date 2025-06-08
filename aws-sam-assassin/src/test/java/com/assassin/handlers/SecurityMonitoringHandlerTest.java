package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.service.SecurityMonitoringService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Type;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityMonitoringHandlerTest {

    @Mock
    private SecurityMonitoringService securityMonitoringService;

    @Mock
    private Context context;

    private SecurityMonitoringHandler handler;
    private final Gson gson = new Gson();

    @BeforeEach
    void setUp() {
        handler = new SecurityMonitoringHandler(securityMonitoringService);
    }

    @Test
    void testHandleSecurityReport_Success() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/security-monitoring/report");
        request.setQueryStringParameters(Map.of("hours", "24"));

        SecurityMonitoringService.SecurityMonitoringReport mockReport = 
            new SecurityMonitoringService.SecurityMonitoringReport();
        mockReport.setTotalEvents(100);
        mockReport.setTimeRange("24 hours");
        mockReport.setActiveBlocks(5);
        mockReport.setGeneratedAt("2024-01-01T12:00:00Z");

        when(securityMonitoringService.generateSecurityReport(24)).thenReturn(mockReport);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("100"));
        assertTrue(response.getBody().contains("24 hours"));

        verify(securityMonitoringService).generateSecurityReport(24);
    }

    @Test
    void testHandleSecurityReport_DefaultHours() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/security-monitoring/report");

        SecurityMonitoringService.SecurityMonitoringReport mockReport = 
            new SecurityMonitoringService.SecurityMonitoringReport();
        mockReport.setTotalEvents(50);
        mockReport.setTimeRange("24 hours");

        when(securityMonitoringService.generateSecurityReport(24)).thenReturn(mockReport);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        verify(securityMonitoringService).generateSecurityReport(24);
    }

    @Test
    void testHandleSecurityReport_ServiceException() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/security-monitoring/report");

        when(securityMonitoringService.generateSecurityReport(anyInt()))
            .thenThrow(new RuntimeException("Database error"));

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Failed to generate security report"));
        assertTrue(response.getBody().contains("Database error"));
    }

    @Test
    void testHandleSecurityAlerts_Success() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/security-monitoring/alerts");
        request.setQueryStringParameters(Map.of("hours", "2"));

        List<SecurityMonitoringService.SecurityAlert> mockAlerts = List.of(
            new SecurityMonitoringService.SecurityAlert("HIGH", "Rate Limiting", "Excessive requests from IP"),
            new SecurityMonitoringService.SecurityAlert("MEDIUM", "Suspicious Activity", "Unusual pattern detected")
        );

        when(securityMonitoringService.checkSecurityAlerts(2)).thenReturn(mockAlerts);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        
        Type responseType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> responseData = gson.fromJson(response.getBody(), responseType);
        
        assertEquals(2.0, responseData.get("alertCount"));
        assertEquals("2 hours", responseData.get("timeRange"));
        assertNotNull(responseData.get("alerts"));

        verify(securityMonitoringService).checkSecurityAlerts(2);
    }

    @Test
    void testHandleSecurityMetrics_Success() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/security-monitoring/metrics");

        SecurityMonitoringService.SecurityMetrics mockMetrics = 
            new SecurityMonitoringService.SecurityMetrics();
        mockMetrics.setTotalEvents(1000);
        mockMetrics.setEventsPerHour(50);
        mockMetrics.setUniqueSourceIPs(10);
        mockMetrics.setHighSeverityEvents(5);
        mockMetrics.setActiveBlocks(2);
        mockMetrics.setTimeRange("24 hours");

        when(securityMonitoringService.getSecurityMetrics(24)).thenReturn(mockMetrics);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("1000"));

        verify(securityMonitoringService).getSecurityMetrics(24);
    }

    @Test
    void testHandleTopThreats_Success() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/security-monitoring/threats");
        request.setQueryStringParameters(Map.of("hours", "12", "limit", "5"));

        // Mock empty threat list for simplicity
        when(securityMonitoringService.getTopThreatIPs(12, 5)).thenReturn(List.of());

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        
        Type responseType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> responseData = gson.fromJson(response.getBody(), responseType);
        
        assertEquals(0.0, responseData.get("threatCount"));
        assertEquals("12 hours", responseData.get("timeRange"));
        assertEquals(5.0, responseData.get("limit"));

        verify(securityMonitoringService).getTopThreatIPs(12, 5);
    }

    @Test
    void testHandleAutomatedResponse_Success() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("POST", "/security-monitoring/response");
        request.setBody("{\"ip\": \"192.168.1.1\", \"hours\": 2}");

        SecurityMonitoringService.SecurityResponse mockResponse = 
            new SecurityMonitoringService.SecurityResponse("BLOCKED", "High threat score detected");

        when(securityMonitoringService.performAutomatedResponse("192.168.1.1", 2))
            .thenReturn(mockResponse);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("BLOCKED"));

        verify(securityMonitoringService).performAutomatedResponse("192.168.1.1", 2);
    }

    @Test
    void testHandleAutomatedResponse_MissingIp() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("POST", "/security-monitoring/response");
        request.setBody("{\"hours\": 2}");

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("IP address is required"));
        verifyNoInteractions(securityMonitoringService);
    }

    @Test
    void testHandleAutomatedResponse_EmptyBody() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("POST", "/security-monitoring/response");
        request.setBody("");

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Request body is required"));
        verifyNoInteractions(securityMonitoringService);
    }

    @Test
    void testHandleHealthCheck_Success() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/security-monitoring/health");

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        
        Type responseType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> responseData = gson.fromJson(response.getBody(), responseType);
        
        assertEquals("healthy", responseData.get("status"));
        assertEquals("SecurityMonitoringHandler", responseData.get("service"));
        assertEquals("1.0.0", responseData.get("version"));
        assertNotNull(responseData.get("timestamp"));

        verifyNoInteractions(securityMonitoringService);
    }

    @Test
    void testHandleUnknownEndpoint() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/security-monitoring/unknown");

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Endpoint not found"));
        verifyNoInteractions(securityMonitoringService);
    }

    @Test
    void testHandlerConstructorWithoutDependencyInjection() {
        // This test demonstrates that the default constructor requires environment variables
        // In a real scenario, these would be set by the Lambda runtime
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            new SecurityMonitoringHandler();
        });
        assertTrue(exception.getMessage().contains("environment variable is not set"));
    }

    // Helper method to create request events
    private APIGatewayProxyRequestEvent createRequest(String method, String path) {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod(method);
        request.setPath(path);
        return request;
    }
} 