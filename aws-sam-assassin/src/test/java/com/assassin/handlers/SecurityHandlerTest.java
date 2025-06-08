package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.service.SecurityService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityHandler Tests")
class SecurityHandlerTest {

    @Mock
    private SecurityService mockSecurityService;

    @Mock
    private Context mockContext;

    private SecurityHandler securityHandler;
    private Gson gson;

    @BeforeEach
    void setUp() {
        securityHandler = new SecurityHandler(mockSecurityService);
        gson = new Gson();
    }

    @Test
    @DisplayName("Should handle rate limit check successfully")
    void shouldHandleRateLimitCheckSuccessfully() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("POST", "/security/rate-limit");
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("endpoint", "/games");
        request.setBody(gson.toJson(requestBody));

        SecurityService.RateLimitResult rateLimitResult = 
            new SecurityService.RateLimitResult(true, "Request allowed", 0);
        when(mockSecurityService.checkRateLimit(anyString(), eq("/games")))
            .thenReturn(rateLimitResult);

        // Act
        APIGatewayProxyResponseEvent response = securityHandler.handleRequest(request, mockContext);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"allowed\":true"));
        assertTrue(response.getBody().contains("\"message\":\"Request allowed\""));
        verify(mockSecurityService).checkRateLimit(anyString(), eq("/games"));
    }

    @Test
    @DisplayName("Should handle rate limit exceeded")
    void shouldHandleRateLimitExceeded() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("POST", "/security/rate-limit");
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("endpoint", "/games");
        request.setBody(gson.toJson(requestBody));

        SecurityService.RateLimitResult rateLimitResult = 
            new SecurityService.RateLimitResult(false, "Rate limit exceeded", 60);
        when(mockSecurityService.checkRateLimit(anyString(), eq("/games")))
            .thenReturn(rateLimitResult);

        // Act
        APIGatewayProxyResponseEvent response = securityHandler.handleRequest(request, mockContext);

        // Assert
        assertEquals(429, response.getStatusCode());
        assertTrue(response.getBody().contains("\"allowed\":false"));
        assertTrue(response.getBody().contains("\"retryAfterSeconds\":60"));
        assertNotNull(response.getHeaders());
        assertEquals("60", response.getHeaders().get("Retry-After"));
    }

    @Test
    @DisplayName("Should handle abuse detection successfully")
    void shouldHandleAbuseDetectionSuccessfully() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("POST", "/security/abuse-detection");
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userID", "user123");
        requestBody.addProperty("endpoint", "/games");
        request.setBody(gson.toJson(requestBody));

        SecurityService.AbuseDetectionResult abuseResult = 
            new SecurityService.AbuseDetectionResult(false, "No abuse detected", false);
        when(mockSecurityService.detectAbuse(anyString(), eq("user123"), eq("/games"), anyString()))
            .thenReturn(abuseResult);

        // Act
        APIGatewayProxyResponseEvent response = securityHandler.handleRequest(request, mockContext);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"abuseDetected\":false"));
        assertTrue(response.getBody().contains("\"shouldBlock\":false"));
        verify(mockSecurityService).detectAbuse(anyString(), eq("user123"), eq("/games"), anyString());
    }

    @Test
    @DisplayName("Should handle abuse detection with blocking")
    void shouldHandleAbuseDetectionWithBlocking() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("POST", "/security/abuse-detection");
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userID", "user123");
        requestBody.addProperty("endpoint", "/games");
        request.setBody(gson.toJson(requestBody));

        SecurityService.AbuseDetectionResult abuseResult = 
            new SecurityService.AbuseDetectionResult(true, "Excessive requests detected", true);
        when(mockSecurityService.detectAbuse(anyString(), eq("user123"), eq("/games"), anyString()))
            .thenReturn(abuseResult);

        // Act
        APIGatewayProxyResponseEvent response = securityHandler.handleRequest(request, mockContext);

        // Assert
        assertEquals(403, response.getStatusCode());
        assertTrue(response.getBody().contains("\"abuseDetected\":true"));
        assertTrue(response.getBody().contains("\"shouldBlock\":true"));
        assertTrue(response.getBody().contains("\"reason\":\"Excessive requests detected\""));
    }

    @Test
    @DisplayName("Should handle location spoofing check successfully")
    void shouldHandleLocationSpoofingCheckSuccessfully() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("POST", "/security/location-spoofing");
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userID", "user123");
        requestBody.addProperty("latitude", 40.7128);
        requestBody.addProperty("longitude", -74.0060);
        requestBody.addProperty("timestamp", "2023-01-01T12:00:00Z");
        request.setBody(gson.toJson(requestBody));

        SecurityService.LocationSpoofingResult spoofingResult = 
            new SecurityService.LocationSpoofingResult(false, "Normal movement", 25.5);
        when(mockSecurityService.detectLocationSpoofing(eq("user123"), eq(40.7128), eq(-74.0060), eq("2023-01-01T12:00:00Z")))
            .thenReturn(spoofingResult);

        // Act
        APIGatewayProxyResponseEvent response = securityHandler.handleRequest(request, mockContext);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"spoofingDetected\":false"));
        assertTrue(response.getBody().contains("\"speedKmh\":25.5"));
        verify(mockSecurityService).detectLocationSpoofing(eq("user123"), eq(40.7128), eq(-74.0060), eq("2023-01-01T12:00:00Z"));
    }

    @Test
    @DisplayName("Should handle location spoofing detection")
    void shouldHandleLocationSpoofingDetection() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("POST", "/security/location-spoofing");
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userID", "user123");
        requestBody.addProperty("latitude", 40.7128);
        requestBody.addProperty("longitude", -74.0060);
        requestBody.addProperty("timestamp", "2023-01-01T12:00:00Z");
        request.setBody(gson.toJson(requestBody));

        SecurityService.LocationSpoofingResult spoofingResult = 
            new SecurityService.LocationSpoofingResult(true, "Speed too high: 500.0 km/h", 500.0);
        when(mockSecurityService.detectLocationSpoofing(eq("user123"), eq(40.7128), eq(-74.0060), eq("2023-01-01T12:00:00Z")))
            .thenReturn(spoofingResult);

        // Act
        APIGatewayProxyResponseEvent response = securityHandler.handleRequest(request, mockContext);

        // Assert
        assertEquals(422, response.getStatusCode());
        assertTrue(response.getBody().contains("\"spoofingDetected\":true"));
        assertTrue(response.getBody().contains("\"speedKmh\":500.0"));
        assertTrue(response.getBody().contains("\"message\":\"Speed too high: 500.0 km/h\""));
    }

    @Test
    @DisplayName("Should handle security event logging successfully")
    void shouldHandleSecurityEventLoggingSuccessfully() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("POST", "/security/log-event");
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userID", "user123");
        requestBody.addProperty("eventType", "LOGIN_ATTEMPT");
        requestBody.addProperty("endpoint", "/auth/login");
        requestBody.addProperty("statusCode", 200);
        request.setBody(gson.toJson(requestBody));

        // Act
        APIGatewayProxyResponseEvent response = securityHandler.handleRequest(request, mockContext);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"success\":true"));
        assertTrue(response.getBody().contains("\"eventType\":\"LOGIN_ATTEMPT\""));
        verify(mockSecurityService).logSecurityEvent(anyString(), eq("user123"), eq("LOGIN_ATTEMPT"), 
                                                    eq("/auth/login"), eq(200), anyString(), any());
    }

    @Test
    @DisplayName("Should handle health check successfully")
    void shouldHandleHealthCheckSuccessfully() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/security/health");

        // Act
        APIGatewayProxyResponseEvent response = securityHandler.handleRequest(request, mockContext);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"healthy\""));
        assertTrue(response.getBody().contains("\"service\":\"security-handler\""));
        assertTrue(response.getBody().contains("\"timestamp\""));
    }

    @Test
    @DisplayName("Should return 404 for unknown endpoints")
    void shouldReturn404ForUnknownEndpoints() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("GET", "/security/unknown");

        // Act
        APIGatewayProxyResponseEvent response = securityHandler.handleRequest(request, mockContext);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Security endpoint not found"));
    }

    @Test
    @DisplayName("Should return 400 for missing endpoint in rate limit check")
    void shouldReturn400ForMissingEndpointInRateLimitCheck() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("POST", "/security/rate-limit");
        JsonObject requestBody = new JsonObject();
        // Missing endpoint field
        request.setBody(gson.toJson(requestBody));

        // Act
        APIGatewayProxyResponseEvent response = securityHandler.handleRequest(request, mockContext);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Missing required field: endpoint"));
    }

    @Test
    @DisplayName("Should return 400 for missing fields in location spoofing check")
    void shouldReturn400ForMissingFieldsInLocationSpoofingCheck() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("POST", "/security/location-spoofing");
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userID", "user123");
        // Missing latitude, longitude, timestamp
        request.setBody(gson.toJson(requestBody));

        // Act
        APIGatewayProxyResponseEvent response = securityHandler.handleRequest(request, mockContext);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Missing required fields: userID, latitude, longitude, timestamp"));
    }

    @Test
    @DisplayName("Should return 400 for missing event type in log event")
    void shouldReturn400ForMissingEventTypeInLogEvent() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("POST", "/security/log-event");
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userID", "user123");
        // Missing eventType field
        request.setBody(gson.toJson(requestBody));

        // Act
        APIGatewayProxyResponseEvent response = securityHandler.handleRequest(request, mockContext);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Missing required field: eventType"));
    }

    @Test
    @DisplayName("Should handle service exceptions gracefully")
    void shouldHandleServiceExceptionsGracefully() {
        // Arrange
        APIGatewayProxyRequestEvent request = createRequest("POST", "/security/rate-limit");
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("endpoint", "/games");
        request.setBody(gson.toJson(requestBody));

        when(mockSecurityService.checkRateLimit(anyString(), anyString()))
            .thenThrow(new RuntimeException("Database connection failed"));

        // Act
        APIGatewayProxyResponseEvent response = securityHandler.handleRequest(request, mockContext);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Rate limit check failed"));
    }

    private APIGatewayProxyRequestEvent createRequest(String httpMethod, String path) {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod(httpMethod);
        request.setPath(path);
        
        // Set up headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "TestAgent/1.0");
        request.setHeaders(headers);
        
        // Set up request context with source IP
        APIGatewayProxyRequestEvent.ProxyRequestContext context = 
            new APIGatewayProxyRequestEvent.ProxyRequestContext();
        APIGatewayProxyRequestEvent.RequestIdentity identity = 
            new APIGatewayProxyRequestEvent.RequestIdentity();
        identity.setSourceIp("192.168.1.100");
        context.setIdentity(identity);
        request.setRequestContext(context);
        
        return request;
    }
} 