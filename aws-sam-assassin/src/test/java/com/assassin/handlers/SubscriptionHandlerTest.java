package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.model.SubscriptionTier;
import com.assassin.service.SubscriptionService;
import com.assassin.service.SubscriptionTierService;
import com.assassin.util.HandlerUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;

@DisplayName("SubscriptionHandler Tests")
class SubscriptionHandlerTest {

    @Mock
    private SubscriptionService subscriptionService;
    
    @Mock
    private SubscriptionTierService subscriptionTierService;
    
    @Mock
    private Context context;
    
    private SubscriptionHandler subscriptionHandler;
    private final String testPlayerId = "player-123";
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        subscriptionHandler = new SubscriptionHandler(subscriptionService, subscriptionTierService);
    }
    
    @Test
    @DisplayName("Should return all subscription tiers on GET /subscriptions/tiers")
    void shouldReturnAllSubscriptionTiers() {
        // Arrange
        List<SubscriptionTier> mockTiers = Arrays.asList(
                SubscriptionTier.BASIC,
                SubscriptionTier.HUNTER,
                SubscriptionTier.ASSASSIN,
                SubscriptionTier.ELITE
        );
        when(subscriptionTierService.getAllTiers()).thenReturn(mockTiers);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/subscriptions/tiers")
                .withHttpMethod("GET");
        
        try (MockedStatic<HandlerUtils> mockedHandlerUtils = mockStatic(HandlerUtils.class)) {
            mockedHandlerUtils.when(HandlerUtils::getResponseHeaders).thenReturn(new HashMap<>());
            
            // Act
            APIGatewayProxyResponseEvent response = subscriptionHandler.handleRequest(request, context);
            
            // Assert
            assertEquals(200, response.getStatusCode());
            assertTrue(response.getBody().contains("BASIC"));
            assertTrue(response.getBody().contains("HUNTER"));
            assertTrue(response.getBody().contains("ASSASSIN"));
            assertTrue(response.getBody().contains("ELITE"));
            verify(subscriptionTierService).getAllTiers();
        }
    }
    
    @Test
    @DisplayName("Should return player subscription on GET /players/me/subscription")
    void shouldReturnPlayerSubscription() {
        // Arrange
        SubscriptionService.SubscriptionInfo mockSubscription = 
                new SubscriptionService.SubscriptionInfo("hunter", true, Instant.now().plusSeconds(86400), false);
        
        when(subscriptionService.getPlayerSubscription(testPlayerId)).thenReturn(mockSubscription);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/players/me/subscription")
                .withHttpMethod("GET");
        
        try (MockedStatic<HandlerUtils> mockedHandlerUtils = mockStatic(HandlerUtils.class)) {
            mockedHandlerUtils.when(HandlerUtils::getResponseHeaders).thenReturn(new HashMap<>());
            mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(request))
                    .thenReturn(Optional.of(testPlayerId));
            
            // Act
            APIGatewayProxyResponseEvent response = subscriptionHandler.handleRequest(request, context);
            
            // Assert
            assertEquals(200, response.getStatusCode());
            assertTrue(response.getBody().contains("hunter"));
            assertTrue(response.getBody().contains("true"));
            verify(subscriptionService).getPlayerSubscription(testPlayerId);
        }
    }
    
    @Test
    @DisplayName("Should subscribe player to basic tier on POST /players/me/subscription")
    void shouldSubscribePlayerToBasicTier() throws Exception {
        // Arrange
        when(subscriptionTierService.getTierById("basic")).thenReturn(Optional.of(SubscriptionTier.BASIC));
        when(subscriptionService.subscribePlayer(anyString(), eq("basic"), anyString(), anyString(), anyString()))
                .thenReturn("/subscription/success");
        
        String requestBody = "{\"tierId\":\"basic\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/players/me/subscription")
                .withHttpMethod("POST")
                .withBody(requestBody);
        
        try (MockedStatic<HandlerUtils> mockedHandlerUtils = mockStatic(HandlerUtils.class)) {
            mockedHandlerUtils.when(HandlerUtils::getResponseHeaders).thenReturn(new HashMap<>());
            mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(request))
                    .thenReturn(Optional.of(testPlayerId));
            
            // Act
            APIGatewayProxyResponseEvent response = subscriptionHandler.handleRequest(request, context);
            
            // Assert
            assertEquals(200, response.getStatusCode());
            assertTrue(response.getBody().contains("Successfully subscribed to basic tier"));
            verify(subscriptionService).subscribePlayer(eq(testPlayerId), eq("basic"), eq(""), eq(""), eq(""));
        }
    }
    
    @Test
    @DisplayName("Should create checkout session for paid tier on POST /players/me/subscription")
    void shouldCreateCheckoutSessionForPaidTier() throws Exception {
        // Arrange
        String checkoutUrl = "https://checkout.stripe.com/session_123";
        when(subscriptionTierService.getTierById("hunter")).thenReturn(Optional.of(SubscriptionTier.HUNTER));
        when(subscriptionService.subscribePlayer(anyString(), eq("hunter"), anyString(), anyString(), anyString()))
                .thenReturn(checkoutUrl);
        
        String requestBody = "{\"tierId\":\"hunter\",\"customerEmail\":\"test@example.com\",\"successUrl\":\"/success\",\"cancelUrl\":\"/cancel\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/players/me/subscription")
                .withHttpMethod("POST")
                .withBody(requestBody);
        
        try (MockedStatic<HandlerUtils> mockedHandlerUtils = mockStatic(HandlerUtils.class)) {
            mockedHandlerUtils.when(HandlerUtils::getResponseHeaders).thenReturn(new HashMap<>());
            mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(request))
                    .thenReturn(Optional.of(testPlayerId));
            
            // Act
            APIGatewayProxyResponseEvent response = subscriptionHandler.handleRequest(request, context);
            
            // Assert
            assertEquals(200, response.getStatusCode());
            assertTrue(response.getBody().contains("checkoutUrl"));
            assertTrue(response.getBody().contains(checkoutUrl));
            verify(subscriptionService).subscribePlayer(eq(testPlayerId), eq("hunter"), eq("test@example.com"), eq("/success"), eq("/cancel"));
        }
    }
    
    @Test
    @DisplayName("Should cancel subscription on DELETE /players/me/subscription")
    void shouldCancelSubscription() {
        // Arrange
        when(subscriptionService.cancelSubscription(testPlayerId)).thenReturn(true);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/players/me/subscription")
                .withHttpMethod("DELETE");
        
        try (MockedStatic<HandlerUtils> mockedHandlerUtils = mockStatic(HandlerUtils.class)) {
            mockedHandlerUtils.when(HandlerUtils::getResponseHeaders).thenReturn(new HashMap<>());
            mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(request))
                    .thenReturn(Optional.of(testPlayerId));
            
            // Act
            APIGatewayProxyResponseEvent response = subscriptionHandler.handleRequest(request, context);
            
            // Assert
            assertEquals(200, response.getStatusCode());
            assertTrue(response.getBody().contains("Subscription cancelled successfully"));
            verify(subscriptionService).cancelSubscription(testPlayerId);
        }
    }
    
    @Test
    @DisplayName("Should process webhook on POST /subscriptions/webhook")
    void shouldProcessWebhook() {
        // Arrange
        String payload = "{\"type\":\"checkout.session.completed\"}";
        String signature = "whsec_test_signature";
        
        SubscriptionService.WebhookResult webhookResult = new SubscriptionService.WebhookResult(true, "Success");
        when(subscriptionService.processWebhook(payload, signature)).thenReturn(webhookResult);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/subscriptions/webhook")
                .withHttpMethod("POST")
                .withBody(payload)
                .withHeaders(Map.of("Stripe-Signature", signature));
        
        try (MockedStatic<HandlerUtils> mockedHandlerUtils = mockStatic(HandlerUtils.class)) {
            mockedHandlerUtils.when(HandlerUtils::getResponseHeaders).thenReturn(new HashMap<>());
            
            // Act
            APIGatewayProxyResponseEvent response = subscriptionHandler.handleRequest(request, context);
            
            // Assert
            assertEquals(200, response.getStatusCode());
            assertTrue(response.getBody().contains("Webhook processed successfully"));
            verify(subscriptionService).processWebhook(payload, signature);
        }
    }
    
    @Test
    @DisplayName("Should return 400 for invalid tier ID")
    void shouldReturn400ForInvalidTier() {
        // Arrange
        when(subscriptionTierService.getTierById("invalid")).thenReturn(Optional.empty());
        
        String requestBody = "{\"tierId\":\"invalid\"}";
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/players/me/subscription")
                .withHttpMethod("POST")
                .withBody(requestBody);
        
        try (MockedStatic<HandlerUtils> mockedHandlerUtils = mockStatic(HandlerUtils.class)) {
            mockedHandlerUtils.when(HandlerUtils::getResponseHeaders).thenReturn(new HashMap<>());
            mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(request))
                    .thenReturn(Optional.of(testPlayerId));
            
            // Act
            APIGatewayProxyResponseEvent response = subscriptionHandler.handleRequest(request, context);
            
            // Assert
            assertEquals(400, response.getStatusCode());
            assertTrue(response.getBody().contains("Invalid subscription tier"));
        }
    }
    
    @Test
    @DisplayName("Should return 400 for missing player ID")
    void shouldReturn400ForMissingPlayerId() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/players/me/subscription")
                .withHttpMethod("GET");
        
        try (MockedStatic<HandlerUtils> mockedHandlerUtils = mockStatic(HandlerUtils.class)) {
            mockedHandlerUtils.when(HandlerUtils::getResponseHeaders).thenReturn(new HashMap<>());
            mockedHandlerUtils.when(() -> HandlerUtils.getPlayerIdFromRequest(request))
                    .thenReturn(Optional.empty());
            
            // Act
            APIGatewayProxyResponseEvent response = subscriptionHandler.handleRequest(request, context);
            
            // Assert
            assertEquals(400, response.getStatusCode());
            assertTrue(response.getBody().contains("Player ID not found"));
        }
    }
    
    @Test
    @DisplayName("Should return 404 for unknown route")
    void shouldReturn404ForUnknownRoute() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/unknown/route")
                .withHttpMethod("GET");
        
        try (MockedStatic<HandlerUtils> mockedHandlerUtils = mockStatic(HandlerUtils.class)) {
            mockedHandlerUtils.when(HandlerUtils::getResponseHeaders).thenReturn(new HashMap<>());
            
            // Act
            APIGatewayProxyResponseEvent response = subscriptionHandler.handleRequest(request, context);
            
            // Assert
            assertEquals(404, response.getStatusCode());
            assertTrue(response.getBody().contains("Route not found"));
        }
    }
    
    @Test
    @DisplayName("Should return 400 for missing webhook signature")
    void shouldReturn400ForMissingWebhookSignature() {
        // Arrange
        String payload = "{\"type\":\"checkout.session.completed\"}";
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/subscriptions/webhook")
                .withHttpMethod("POST")
                .withBody(payload)
                .withHeaders(new HashMap<>()); // No Stripe-Signature header
        
        try (MockedStatic<HandlerUtils> mockedHandlerUtils = mockStatic(HandlerUtils.class)) {
            mockedHandlerUtils.when(HandlerUtils::getResponseHeaders).thenReturn(new HashMap<>());
            
            // Act
            APIGatewayProxyResponseEvent response = subscriptionHandler.handleRequest(request, context);
            
            // Assert
            assertEquals(400, response.getStatusCode());
            assertTrue(response.getBody().contains("Missing Stripe-Signature header"));
        }
    }
} 