package com.assassin.handlers;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.exception.ValidationException;
import com.assassin.model.Kill;
import com.assassin.service.KillService;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Unit tests for KillHandler.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class KillHandlerTest {

    @Mock
    private KillService mockKillService;
    
    @Mock
    private Context mockContext;
    
    private KillHandler killHandler;
    private Gson gson;
    
    @BeforeEach
    void setUp() {
        killHandler = new KillHandler(mockKillService);
        gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    @Test
    void handleRequest_ReportKill_ReturnsCreatedWithKill() throws ValidationException {
        // Arrange
        String killerId = "killer123";
        String victimId = "victim456";
        Double latitude = 40.7128;
        Double longitude = -74.0060;
        String currentTime = Instant.now().toString();
        String verificationMethod = "GPS";
        Map<String, String> verificationData = Map.of();
        
        // Create the request with auth header and body
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + killerId); // Assuming auth token is player ID
        
        String requestBody = "{"
            + "\"victimID\":\"" + victimId + "\"," // Use victimID (camelCase) to match the model
            + "\"latitude\":" + latitude + ","
            + "\"longitude\":" + longitude + ","
            + "\"verificationMethod\":\"" + verificationMethod + "\","
            + "\"verificationData\":{}"
            + "}";
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/die")
            .withHttpMethod("POST")
            .withHeaders(headers)
            .withBody(requestBody);
        
        // Create kill object that service will return
        Kill returnedKill = new Kill();
        returnedKill.setKillerID(killerId);
        returnedKill.setVictimID(victimId);
        returnedKill.setLatitude(latitude);
        returnedKill.setLongitude(longitude);
        returnedKill.setTime(currentTime);
        returnedKill.setVerificationMethod(verificationMethod);
        returnedKill.setVerificationStatus("PENDING");
        returnedKill.setVerificationData(verificationData);
        
        // Mock HandlerUtils.getPlayerIdFromRequest to return killerId
        try (MockedStatic<HandlerUtils> handlerUtils = Mockito.mockStatic(HandlerUtils.class)) {
            handlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(request)).thenReturn(Optional.of(killerId));
            handlerUtils.when(HandlerUtils::getResponseHeaders).thenCallRealMethod();
            handlerUtils.when(() -> HandlerUtils.isPreflightRequest(request)).thenCallRealMethod();
            
            // Mock service method
            when(mockKillService.reportKill(eq(killerId), eq(victimId), eq(latitude), eq(longitude), eq(verificationMethod), eq(verificationData)))
                .thenReturn(returnedKill);
            
            // Act
            APIGatewayProxyResponseEvent response = killHandler.handleRequest(request, mockContext);
            
            // Assert
            assertEquals(201, response.getStatusCode()); // Created
            Kill responseKill = gson.fromJson(response.getBody(), Kill.class);
            assertEquals(killerId, responseKill.getKillerID());
            assertEquals(victimId, responseKill.getVictimID());
            assertEquals(latitude, responseKill.getLatitude());
            assertEquals(longitude, responseKill.getLongitude());
            assertEquals(currentTime, responseKill.getTime());
            
            // Verify service called correctly
            verify(mockKillService).reportKill(eq(killerId), eq(victimId), eq(latitude), eq(longitude), eq(verificationMethod), eq(verificationData));
        }
    }
    
    @Test
    void handleRequest_ReportKill_ValidationException_ReturnsBadRequest() throws ValidationException {
        // Arrange
        String killerId = "killer123";
        String victimId = "victim456";
        Double latitude = 40.7128;
        Double longitude = -74.0060;
        String verificationMethod = "GPS";
        Map<String, String> verificationData = Map.of();
        
        // Create the request with auth header and body
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + killerId);
        
        String requestBody = "{"
            + "\"victimID\":\"" + victimId + "\"," // Use victimID (camelCase) to match the model
            + "\"latitude\":" + latitude + ","
            + "\"longitude\":" + longitude + ","
            + "\"verificationMethod\":\"" + verificationMethod + "\","
            + "\"verificationData\":{}"
            + "}";
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/die")
            .withHttpMethod("POST")
            .withHeaders(headers)
            .withBody(requestBody);
        
        // Error message for validation exception
        String errorMessage = "Reported victim is not the killer's current target";
        
        // Mock HandlerUtils.getPlayerIdFromRequest to return killerId
        try (MockedStatic<HandlerUtils> handlerUtils = Mockito.mockStatic(HandlerUtils.class)) {
            handlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(request)).thenReturn(Optional.of(killerId));
            handlerUtils.when(HandlerUtils::getResponseHeaders).thenCallRealMethod();
            handlerUtils.when(() -> HandlerUtils.isPreflightRequest(request)).thenCallRealMethod();
            
            // Use lenient() for Mockito to avoid strict stubbing issues
            lenient().when(mockKillService.reportKill(eq(killerId), eq(victimId), eq(latitude), eq(longitude), eq(verificationMethod), eq(verificationData)))
                .thenThrow(new ValidationException(errorMessage));
            
            // Act
            APIGatewayProxyResponseEvent response = killHandler.handleRequest(request, mockContext);
            
            // Assert
            assertEquals(400, response.getStatusCode()); // Bad Request
            Map<String, String> errorBody = gson.fromJson(response.getBody(), 
                new TypeToken<Map<String, String>>(){}.getType());
            assertEquals("Validation error: " + errorMessage, errorBody.get("message"));
        }
    }
    
    @Test
    void handleRequest_InvalidJson_ReturnsBadRequest() {
        // Arrange
        String killerId = "killer123";
        
        // Create the request with auth header and invalid JSON body
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + killerId);
        
        String invalidJson = "{\"victimID\":\"victim456\", invalidJson}"; // Invalid JSON syntax
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/die")
            .withHttpMethod("POST")
            .withHeaders(headers)
            .withBody(invalidJson);
        
        // Mock HandlerUtils methods
        try (MockedStatic<HandlerUtils> handlerUtils = Mockito.mockStatic(HandlerUtils.class)) {
            handlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(request)).thenReturn(Optional.of(killerId));
            handlerUtils.when(HandlerUtils::getResponseHeaders).thenCallRealMethod();
            handlerUtils.when(() -> HandlerUtils.isPreflightRequest(request)).thenCallRealMethod();
            
            // Act
            APIGatewayProxyResponseEvent response = killHandler.handleRequest(request, mockContext);
            
            // Assert
            assertEquals(400, response.getStatusCode()); // Bad Request
            Map<String, String> errorBody = gson.fromJson(response.getBody(), 
                new TypeToken<Map<String, String>>(){}.getType());
            assertEquals("Invalid JSON format", errorBody.get("message"));
        }
    }
    
    @Test
    void handleRequest_InvalidRoute_ReturnsNotFound() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
            .withPath("/invalid")
            .withHttpMethod("GET");
        
        // Mock HandlerUtils.getResponseHeaders
        try (MockedStatic<HandlerUtils> handlerUtils = Mockito.mockStatic(HandlerUtils.class)) {
            handlerUtils.when(HandlerUtils::getResponseHeaders).thenCallRealMethod();
            handlerUtils.when(() -> HandlerUtils.isPreflightRequest(request)).thenCallRealMethod();
            
            // Act
            APIGatewayProxyResponseEvent response = killHandler.handleRequest(request, mockContext);
            
            // Assert
            assertEquals(404, response.getStatusCode());
            Map<String, String> errorBody = gson.fromJson(response.getBody(), 
                new TypeToken<Map<String, String>>(){}.getType());
            assertEquals("Route not found: /invalid", errorBody.get("message"));
        }
    }
} 