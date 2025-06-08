package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.config.StripeClientProvider;
import com.assassin.dao.TransactionDao;
import com.assassin.model.Transaction;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PaymentHandler Tests with Mocked Stripe")
class PaymentHandlerMockTest {

    @Mock
    private TransactionDao mockTransactionDao;

    @Mock
    private Context mockContext;

    @Mock
    private PaymentIntent mockPaymentIntent;

    private PaymentHandler paymentHandler;
    private final Gson gson = new Gson();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        paymentHandler = new PaymentHandler(mockTransactionDao);
    }

    @Nested
    @DisplayName("Route Handling Tests")
    class RouteHandlingTests {

        @Test
        @DisplayName("Should return 404 for unknown routes")
        void shouldReturn404ForUnknownRoute() {
            try (MockedStatic<StripeClientProvider> mockedStripeProvider = Mockito.mockStatic(StripeClientProvider.class)) {
                mockedStripeProvider.when(StripeClientProvider::isSdkInitialized).thenReturn(true);

                APIGatewayProxyRequestEvent request = createRequest("GET", "/unknown/route", null, null, null);
                
                APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(request, mockContext);
                
                assertEquals(404, response.getStatusCode());
                assertTrue(response.getBody().contains("Not Found"));
            }
        }

        @Test
        @DisplayName("Should handle valid pay-entry-fee route")
        void shouldHandleValidPayEntryFeeRoute() {
            try (MockedStatic<StripeClientProvider> mockedStripeProvider = Mockito.mockStatic(StripeClientProvider.class);
                 MockedStatic<PaymentIntent> mockedPaymentIntent = Mockito.mockStatic(PaymentIntent.class)) {
                
                mockedStripeProvider.when(StripeClientProvider::isSdkInitialized).thenReturn(true);
                
                when(mockPaymentIntent.getId()).thenReturn("pi_test123");
                when(mockPaymentIntent.getStatus()).thenReturn("succeeded");
                when(mockPaymentIntent.getClientSecret()).thenReturn("pi_test123_secret");
                when(mockPaymentIntent.getPaymentMethod()).thenReturn("pm_test123");
                
                mockedPaymentIntent.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                    .thenReturn(mockPaymentIntent);

                Map<String, String> pathParams = new HashMap<>();
                pathParams.put("gameId", "game123");
                
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer player123");
                
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("paymentMethodId", "pm_test123");
                
                APIGatewayProxyRequestEvent request = createRequest("POST", "/games/game123/pay-entry-fee", 
                    pathParams, headers, gson.toJson(requestBody));
                
                APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(request, mockContext);
                
                assertEquals(200, response.getStatusCode());
                assertTrue(response.getBody().contains("Payment successful"));
                verify(mockTransactionDao).saveTransaction(any(Transaction.class));
            }
        }
    }

    @Nested
    @DisplayName("Payment Processing Tests")
    class PaymentProcessingTests {

        @Test
        @DisplayName("Should successfully process payment with defaults")
        void shouldSuccessfullyProcessPaymentWithDefaults() {
            try (MockedStatic<StripeClientProvider> mockedStripeProvider = Mockito.mockStatic(StripeClientProvider.class);
                 MockedStatic<PaymentIntent> mockedPaymentIntent = Mockito.mockStatic(PaymentIntent.class)) {
                
                mockedStripeProvider.when(StripeClientProvider::isSdkInitialized).thenReturn(true);
                
                when(mockPaymentIntent.getId()).thenReturn("pi_test123");
                when(mockPaymentIntent.getStatus()).thenReturn("succeeded");
                when(mockPaymentIntent.getClientSecret()).thenReturn("pi_test123_secret");
                when(mockPaymentIntent.getPaymentMethod()).thenReturn("pm_test123");
                
                mockedPaymentIntent.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                    .thenReturn(mockPaymentIntent);

                APIGatewayProxyRequestEvent request = createValidPaymentRequest("game123", "player123", "pm_test123");
                
                APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(request, mockContext);
                
                assertEquals(200, response.getStatusCode());
                assertTrue(response.getBody().contains("Payment successful"));
                verify(mockTransactionDao).saveTransaction(argThat(transaction -> 
                    transaction.getGameId().equals("game123") &&
                    transaction.getPlayerId().equals("player123") &&
                    transaction.getAmount().equals(1000L) &&
                    transaction.getCurrency().equals("USD") &&
                    transaction.getStatus() == Transaction.TransactionStatus.COMPLETED
                ));
            }
        }

        @Test
        @DisplayName("Should handle payment requiring action")
        void shouldHandlePaymentRequiringAction() {
            try (MockedStatic<StripeClientProvider> mockedStripeProvider = Mockito.mockStatic(StripeClientProvider.class);
                 MockedStatic<PaymentIntent> mockedPaymentIntent = Mockito.mockStatic(PaymentIntent.class)) {
                
                mockedStripeProvider.when(StripeClientProvider::isSdkInitialized).thenReturn(true);
                
                when(mockPaymentIntent.getId()).thenReturn("pi_test123");
                when(mockPaymentIntent.getStatus()).thenReturn("requires_action");
                when(mockPaymentIntent.getClientSecret()).thenReturn("pi_test123_secret");
                when(mockPaymentIntent.getPaymentMethod()).thenReturn("pm_test123");
                
                mockedPaymentIntent.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                    .thenReturn(mockPaymentIntent);

                APIGatewayProxyRequestEvent request = createValidPaymentRequest("game123", "player123", "pm_test123");
                
                APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(request, mockContext);
                
                assertEquals(200, response.getStatusCode());
                assertTrue(response.getBody().contains("requires further action"));
                verify(mockTransactionDao).saveTransaction(argThat(transaction -> 
                    transaction.getStatus() == Transaction.TransactionStatus.PENDING
                ));
            }
        }

        @Test
        @DisplayName("Should handle failed payment status")
        void shouldHandleFailedPaymentStatus() {
            try (MockedStatic<StripeClientProvider> mockedStripeProvider = Mockito.mockStatic(StripeClientProvider.class);
                 MockedStatic<PaymentIntent> mockedPaymentIntent = Mockito.mockStatic(PaymentIntent.class)) {
                
                mockedStripeProvider.when(StripeClientProvider::isSdkInitialized).thenReturn(true);
                
                when(mockPaymentIntent.getId()).thenReturn("pi_test123");
                when(mockPaymentIntent.getStatus()).thenReturn("failed");
                when(mockPaymentIntent.getClientSecret()).thenReturn("pi_test123_secret");
                when(mockPaymentIntent.getPaymentMethod()).thenReturn("pm_test123");
                
                mockedPaymentIntent.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                    .thenReturn(mockPaymentIntent);

                APIGatewayProxyRequestEvent request = createValidPaymentRequest("game123", "player123", "pm_test123");
                
                APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(request, mockContext);
                
                assertEquals(400, response.getStatusCode());
                assertTrue(response.getBody().contains("Payment processing failed"));
                verify(mockTransactionDao).saveTransaction(argThat(transaction -> 
                    transaction.getStatus() == Transaction.TransactionStatus.FAILED
                ));
            }
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should return 400 when player ID is missing")
        void shouldReturn400WhenPlayerIdMissing() {
            try (MockedStatic<StripeClientProvider> mockedStripeProvider = Mockito.mockStatic(StripeClientProvider.class)) {
                mockedStripeProvider.when(StripeClientProvider::isSdkInitialized).thenReturn(true);

                Map<String, String> pathParams = new HashMap<>();
                pathParams.put("gameId", "game123");
                
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("paymentMethodId", "pm_test123");
                
                APIGatewayProxyRequestEvent request = createRequest("POST", "/games/game123/pay-entry-fee", 
                    pathParams, new HashMap<>(), gson.toJson(requestBody));
                
                APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(request, mockContext);
                
                assertEquals(400, response.getStatusCode());
                assertTrue(response.getBody().contains("Player ID is missing"));
            }
        }

        @Test
        @DisplayName("Should return 400 when payment method ID is missing")
        void shouldReturn400WhenPaymentMethodIdMissing() {
            try (MockedStatic<StripeClientProvider> mockedStripeProvider = Mockito.mockStatic(StripeClientProvider.class)) {
                mockedStripeProvider.when(StripeClientProvider::isSdkInitialized).thenReturn(true);

                Map<String, String> pathParams = new HashMap<>();
                pathParams.put("gameId", "game123");
                
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer player123");
                
                JsonObject requestBody = new JsonObject();
                // Missing paymentMethodId
                
                APIGatewayProxyRequestEvent request = createRequest("POST", "/games/game123/pay-entry-fee", 
                    pathParams, headers, gson.toJson(requestBody));
                
                APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(request, mockContext);
                
                assertEquals(400, response.getStatusCode());
                assertTrue(response.getBody().contains("paymentMethodId is required"));
            }
        }

            @Test
    @DisplayName("Should handle missing game ID")
    void shouldHandleMissingGameId() {
        try (MockedStatic<StripeClientProvider> mockedStripeProvider = Mockito.mockStatic(StripeClientProvider.class)) {
            mockedStripeProvider.when(StripeClientProvider::isSdkInitialized).thenReturn(true);

            Map<String, String> pathParams = new HashMap<>();
            pathParams.put("gameId", ""); // Empty game ID
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer player123");
            
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("paymentMethodId", "pm_test123");
            
            APIGatewayProxyRequestEvent request = createRequest("POST", "/games/{gameId}/pay-entry-fee", 
                pathParams, headers, gson.toJson(requestBody));
            
            APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(request, mockContext);
            
            assertEquals(400, response.getStatusCode());
            assertTrue(response.getBody().contains("Game ID is missing"));
        }
    }
    }

    @Nested
    @DisplayName("Exception Handling Tests")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("Should handle Stripe exceptions")
        void shouldHandleStripeException() {
            try (MockedStatic<StripeClientProvider> mockedStripeProvider = Mockito.mockStatic(StripeClientProvider.class);
                 MockedStatic<PaymentIntent> mockedPaymentIntent = Mockito.mockStatic(PaymentIntent.class)) {
                
                mockedStripeProvider.when(StripeClientProvider::isSdkInitialized).thenReturn(true);
                
                StripeException stripeException = mock(StripeException.class);
                when(stripeException.getMessage()).thenReturn("Test Stripe error");
                
                mockedPaymentIntent.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                    .thenThrow(stripeException);

                APIGatewayProxyRequestEvent request = createValidPaymentRequest("game123", "player123", "pm_test123");
                
                APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(request, mockContext);
                
                assertEquals(500, response.getStatusCode());
                assertTrue(response.getBody().contains("Payment processing failed"));
                verify(mockTransactionDao).saveTransaction(argThat(transaction -> 
                    transaction.getStatus() == Transaction.TransactionStatus.FAILED
                ));
            }
        }

        @Test
        @DisplayName("Should handle DAO exceptions")
        void shouldHandleDaoException() {
            try (MockedStatic<StripeClientProvider> mockedStripeProvider = Mockito.mockStatic(StripeClientProvider.class);
                 MockedStatic<PaymentIntent> mockedPaymentIntent = Mockito.mockStatic(PaymentIntent.class)) {
                
                mockedStripeProvider.when(StripeClientProvider::isSdkInitialized).thenReturn(true);
                
                when(mockPaymentIntent.getId()).thenReturn("pi_test123");
                when(mockPaymentIntent.getStatus()).thenReturn("succeeded");
                when(mockPaymentIntent.getClientSecret()).thenReturn("pi_test123_secret");
                when(mockPaymentIntent.getPaymentMethod()).thenReturn("pm_test123");
                
                mockedPaymentIntent.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                    .thenReturn(mockPaymentIntent);

                doThrow(new RuntimeException("DAO error")).when(mockTransactionDao).saveTransaction(any(Transaction.class));

                APIGatewayProxyRequestEvent request = createValidPaymentRequest("game123", "player123", "pm_test123");
                
                APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(request, mockContext);
                
                assertEquals(500, response.getStatusCode());
                assertTrue(response.getBody().contains("unexpected error"));
            }
        }

        @Test
        @DisplayName("Should handle JSON parsing exceptions")
        void shouldHandleJsonParsingException() {
            try (MockedStatic<StripeClientProvider> mockedStripeProvider = Mockito.mockStatic(StripeClientProvider.class)) {
                mockedStripeProvider.when(StripeClientProvider::isSdkInitialized).thenReturn(true);

                Map<String, String> pathParams = new HashMap<>();
                pathParams.put("gameId", "game123");
                
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer player123");
                
                APIGatewayProxyRequestEvent request = createRequest("POST", "/games/game123/pay-entry-fee", 
                    pathParams, headers, "invalid json");
                
                APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(request, mockContext);
                
                assertEquals(500, response.getStatusCode());
                assertTrue(response.getBody().contains("unexpected error"));
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle custom amount and currency")
        void shouldHandleCustomAmountAndCurrency() {
            try (MockedStatic<StripeClientProvider> mockedStripeProvider = Mockito.mockStatic(StripeClientProvider.class);
                 MockedStatic<PaymentIntent> mockedPaymentIntent = Mockito.mockStatic(PaymentIntent.class)) {
                
                mockedStripeProvider.when(StripeClientProvider::isSdkInitialized).thenReturn(true);
                
                when(mockPaymentIntent.getId()).thenReturn("pi_test123");
                when(mockPaymentIntent.getStatus()).thenReturn("succeeded");
                when(mockPaymentIntent.getClientSecret()).thenReturn("pi_test123_secret");
                when(mockPaymentIntent.getPaymentMethod()).thenReturn("pm_test123");
                
                mockedPaymentIntent.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                    .thenReturn(mockPaymentIntent);

                Map<String, String> pathParams = new HashMap<>();
                pathParams.put("gameId", "game123");
                
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer player123");
                
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("paymentMethodId", "pm_test123");
                requestBody.addProperty("amount", 2000);
                requestBody.addProperty("currency", "eur");
                
                APIGatewayProxyRequestEvent request = createRequest("POST", "/games/game123/pay-entry-fee", 
                    pathParams, headers, gson.toJson(requestBody));
                
                APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(request, mockContext);
                
                assertEquals(200, response.getStatusCode());
                verify(mockTransactionDao).saveTransaction(argThat(transaction -> 
                    transaction.getAmount().equals(2000L) &&
                    transaction.getCurrency().equals("EUR")
                ));
            }
        }

        @Test
        @DisplayName("Should return 500 when Stripe SDK not initialized")
        void shouldReturn500WhenStripeNotInitialized() {
            try (MockedStatic<StripeClientProvider> mockedStripeProvider = Mockito.mockStatic(StripeClientProvider.class)) {
                mockedStripeProvider.when(StripeClientProvider::isSdkInitialized).thenReturn(false);

                APIGatewayProxyRequestEvent request = createValidPaymentRequest("game123", "player123", "pm_test123");
                
                APIGatewayProxyResponseEvent response = paymentHandler.handleRequest(request, mockContext);
                
                assertEquals(500, response.getStatusCode());
                assertTrue(response.getBody().contains("Payment processing system is currently unavailable"));
            }
        }
    }

    // Helper methods
    private APIGatewayProxyRequestEvent createRequest(String httpMethod, String path, 
                                                     Map<String, String> pathParameters,
                                                     Map<String, String> headers, String body) {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod(httpMethod);
        request.setPath(path);
        request.setPathParameters(pathParameters);
        request.setHeaders(headers != null ? headers : new HashMap<>());
        request.setBody(body);
        
        // Set up request context for player ID extraction
        APIGatewayProxyRequestEvent.ProxyRequestContext context = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, String> claims = new HashMap<>();
        
        // Extract player ID from Authorization header if present
        if (headers != null && headers.containsKey("Authorization")) {
            String authHeader = headers.get("Authorization");
            if (authHeader.startsWith("Bearer ")) {
                String playerId = authHeader.substring(7); // Remove "Bearer " prefix
                claims.put("sub", playerId);
            }
        }
        
        authorizer.put("claims", claims);
        context.setAuthorizer(authorizer);
        request.setRequestContext(context);
        
        return request;
    }

    private APIGatewayProxyRequestEvent createValidPaymentRequest(String gameId, String playerId, String paymentMethodId) {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("gameId", gameId);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + playerId);
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("paymentMethodId", paymentMethodId);
        
        return createRequest("POST", "/games/" + gameId + "/pay-entry-fee", pathParams, headers, gson.toJson(requestBody));
    }
} 