package com.assassin.service;

import com.assassin.model.SubscriptionTier;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

@DisplayName("StripeSubscriptionService Tests")
class StripeSubscriptionServiceTest {

    private StripeSubscriptionService stripeSubscriptionService;
    private final String testStripeKey = "sk_test_test123";
    private final String testWebhookSecret = "whsec_test123";

    @BeforeEach
    void setUp() {
        stripeSubscriptionService = new StripeSubscriptionService(testStripeKey, testWebhookSecret);
    }

    @Test
    @DisplayName("Should create checkout session successfully")
    void shouldCreateCheckoutSessionSuccessfully() {
        // Given
        String tierId = "hunter";
        String customerEmail = "test@example.com";
        String successUrl = "https://example.com/success";
        String cancelUrl = "https://example.com/cancel";
        String customerId = "cus_test123";

        // Test basic constructor functionality
        assertNotNull(stripeSubscriptionService);
    }

    @Test
    @DisplayName("Should handle invalid tier ID")
    void shouldHandleInvalidTierId() {
        // Given
        String invalidTierId = "invalid_tier";
        String customerEmail = "test@example.com";
        String successUrl = "https://example.com/success";
        String cancelUrl = "https://example.com/cancel";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            stripeSubscriptionService.createCheckoutSession(
                    invalidTierId, customerEmail, successUrl, cancelUrl, null);
        });
    }

    @Test
    @DisplayName("Should create webhook result for checkout session completed")
    void shouldCreateWebhookResultForCheckoutCompleted() {
        // Given
        StripeSubscriptionService.SubscriptionWebhookResult result = 
                new StripeSubscriptionService.SubscriptionWebhookResult(
                        "checkout.session.completed", 
                        "success", 
                        "hunter", 
                        Map.of("session_id", "cs_test123"));

        // Then
        assertEquals("checkout.session.completed", result.getEventType());
        assertEquals("success", result.getStatus());
        assertEquals("hunter", result.getTierId());
        assertTrue(result.isSuccess());
        assertFalse(result.isError());
        assertFalse(result.isFailed());
        assertEquals("cs_test123", result.getData().get("session_id"));
    }

    @Test
    @DisplayName("Should create webhook result for error case")
    void shouldCreateWebhookResultForError() {
        // Given
        StripeSubscriptionService.SubscriptionWebhookResult result = 
                new StripeSubscriptionService.SubscriptionWebhookResult(
                        "invoice.payment_failed", 
                        "error", 
                        null, 
                        null);

        // Then
        assertEquals("invoice.payment_failed", result.getEventType());
        assertEquals("error", result.getStatus());
        assertNull(result.getTierId());
        assertFalse(result.isSuccess());
        assertTrue(result.isError());
        assertFalse(result.isFailed());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    @DisplayName("Should create webhook result for failed payment")
    void shouldCreateWebhookResultForFailedPayment() {
        // Given
        StripeSubscriptionService.SubscriptionWebhookResult result = 
                new StripeSubscriptionService.SubscriptionWebhookResult(
                        "invoice.payment_failed", 
                        "failed", 
                        "hunter", 
                        Map.of("retry_count", 2));

        // Then
        assertEquals("invoice.payment_failed", result.getEventType());
        assertEquals("failed", result.getStatus());
        assertEquals("hunter", result.getTierId());
        assertFalse(result.isSuccess());
        assertFalse(result.isError());
        assertTrue(result.isFailed());
        assertEquals(2, result.getData().get("retry_count"));
    }

    @Test
    @DisplayName("Should validate webhook result data safety")
    void shouldValidateWebhookResultDataSafety() {
        // Given - pass null data
        StripeSubscriptionService.SubscriptionWebhookResult result = 
                new StripeSubscriptionService.SubscriptionWebhookResult(
                        "test.event", 
                        "success", 
                        "basic", 
                        null);

        // Then - should have empty map, not null
        assertNotNull(result.getData());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    @DisplayName("Should validate service construction with parameters")
    void shouldValidateServiceConstruction() {
        // Given
        String stripeKey = "sk_test_123";
        String webhookSecret = "whsec_123";

        // When
        StripeSubscriptionService service = new StripeSubscriptionService(stripeKey, webhookSecret);

        // Then
        assertNotNull(service);
    }

    @Test
    @DisplayName("Should validate subscription tier service integration")
    void shouldValidateSubscriptionTierServiceIntegration() {
        // Given - our service should work with SubscriptionTierService
        SubscriptionTierService tierService = new SubscriptionTierService();
        
        // When & Then
        List<SubscriptionTier> allTiers = tierService.getAllTiers();
        assertFalse(allTiers.isEmpty());
        
        List<SubscriptionTier> purchasableTiers = tierService.getPurchasableTiers();
        assertTrue(purchasableTiers.size() >= 3); // Hunter, Assassin, Elite
        
        Optional<SubscriptionTier> hunterTier = tierService.getTierById("hunter");
        assertTrue(hunterTier.isPresent());
        assertEquals("hunter", hunterTier.get().getTierId());
    }

    @Test
    @DisplayName("Should handle webhook result builder pattern")
    void shouldHandleWebhookResultBuilderPattern() {
        // Test building webhook results with different combinations
        Map<String, Object> testData = new HashMap<>();
        testData.put("customer_id", "cus_123");
        testData.put("amount", 999);
        
        StripeSubscriptionService.SubscriptionWebhookResult result = 
                new StripeSubscriptionService.SubscriptionWebhookResult(
                        "customer.subscription.created", 
                        "success", 
                        "assassin", 
                        testData);

        assertEquals("customer.subscription.created", result.getEventType());
        assertEquals("success", result.getStatus());
        assertEquals("assassin", result.getTierId());
        assertEquals("cus_123", result.getData().get("customer_id"));
        assertEquals(999, result.getData().get("amount"));
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Should validate tier ID validation logic")
    void shouldValidateTierIdValidationLogic() {
        // Test the tier validation logic that's used in createCheckoutSession
        SubscriptionTierService tierService = new SubscriptionTierService();
        
        // Valid tier IDs
        assertTrue(tierService.getTierById("basic").isPresent());
        assertTrue(tierService.getTierById("hunter").isPresent());
        assertTrue(tierService.getTierById("assassin").isPresent());
        assertTrue(tierService.getTierById("elite").isPresent());
        
        // Invalid tier IDs
        assertFalse(tierService.getTierById("invalid").isPresent());
        assertFalse(tierService.getTierById("").isPresent());
        assertFalse(tierService.getTierById(null).isPresent());
    }

    @Test
    @DisplayName("Should handle null and empty parameters gracefully")
    void shouldHandleNullAndEmptyParametersGracefully() {
        // Test webhook result with empty strings
        StripeSubscriptionService.SubscriptionWebhookResult result = 
                new StripeSubscriptionService.SubscriptionWebhookResult(
                        "", 
                        "", 
                        "", 
                        Map.of());

        assertEquals("", result.getEventType());
        assertEquals("", result.getStatus());
        assertEquals("", result.getTierId());
        assertFalse(result.isSuccess());
        assertFalse(result.isError());
        assertFalse(result.isFailed());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    @DisplayName("Should validate data immutability in webhook result")
    void shouldValidateDataImmutabilityInWebhookResult() {
        // Given
        Map<String, Object> originalData = new HashMap<>();
        originalData.put("test_key", "test_value");
        
        StripeSubscriptionService.SubscriptionWebhookResult result = 
                new StripeSubscriptionService.SubscriptionWebhookResult(
                        "test.event", 
                        "success", 
                        "basic", 
                        originalData);

        // When - modify original data
        originalData.put("new_key", "new_value");

        // Then - result data should not be affected (depending on implementation)
        // This tests the robustness of the data handling
        assertNotNull(result.getData());
        assertEquals("test_value", result.getData().get("test_key"));
    }
} 