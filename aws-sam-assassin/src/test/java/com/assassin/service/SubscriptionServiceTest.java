package com.assassin.service;

import com.assassin.dao.PlayerDao;
import com.assassin.model.Player;
import com.assassin.model.SubscriptionTier;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@DisplayName("SubscriptionService Tests")
class SubscriptionServiceTest {

    @Mock
    private SubscriptionTierService subscriptionTierService;
    
    @Mock
    private StripeSubscriptionService stripeSubscriptionService;
    
    @Mock
    private PlayerDao playerDao;
    
    private SubscriptionService subscriptionService;
    
    private Player testPlayer;
    private final String testPlayerId = "player123";
    private final String testEmail = "test@example.com";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        subscriptionService = new SubscriptionService(
                subscriptionTierService, 
                stripeSubscriptionService, 
                playerDao);
        
        testPlayer = new Player();
        testPlayer.setPlayerID(testPlayerId);
        testPlayer.setEmail(testEmail);
        testPlayer.setCurrentSubscriptionTierId("basic");
    }

    @Test
    @DisplayName("Should return all available tiers")
    void shouldReturnAllAvailableTiers() {
        // Given
        List<SubscriptionTier> expectedTiers = Arrays.asList(
                SubscriptionTier.BASIC,
                SubscriptionTier.HUNTER,
                SubscriptionTier.ASSASSIN,
                SubscriptionTier.ELITE
        );
        when(subscriptionTierService.getAllTiers()).thenReturn(expectedTiers);

        // When
        List<SubscriptionTier> result = subscriptionService.getAvailableTiers();

        // Then
        assertEquals(expectedTiers, result);
        verify(subscriptionTierService).getAllTiers();
    }

    @Test
    @DisplayName("Should return only purchasable tiers")
    void shouldReturnOnlyPurchasableTiers() {
        // Given
        List<SubscriptionTier> expectedTiers = Arrays.asList(
                SubscriptionTier.HUNTER,
                SubscriptionTier.ASSASSIN,
                SubscriptionTier.ELITE
        );
        when(subscriptionTierService.getPurchasableTiers()).thenReturn(expectedTiers);

        // When
        List<SubscriptionTier> result = subscriptionService.getPurchasableTiers();

        // Then
        assertEquals(expectedTiers, result);
        verify(subscriptionTierService).getPurchasableTiers();
    }

    @Test
    @DisplayName("Should handle basic tier subscription without Stripe")
    void shouldHandleBasicTierSubscriptionWithoutStripe() throws StripeException {
        // Given
        String tierId = "basic";
        String successUrl = "https://example.com/success";
        String cancelUrl = "https://example.com/cancel";
        
        when(subscriptionTierService.getTierById(tierId)).thenReturn(Optional.of(SubscriptionTier.BASIC));
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(testPlayer));

        // When
        String result = subscriptionService.subscribePlayer(
                testPlayerId, tierId, testEmail, successUrl, cancelUrl);

        // Then
        assertEquals(successUrl, result);
        verify(playerDao).savePlayer(testPlayer);
        assertEquals(tierId, testPlayer.getCurrentSubscriptionTierId());
        assertNull(testPlayer.getSubscriptionValidUntil());
        verifyNoInteractions(stripeSubscriptionService);
    }

    @Test
    @DisplayName("Should create Stripe checkout session for paid tiers")
    void shouldCreateStripeCheckoutSessionForPaidTiers() throws StripeException {
        // Given
        String tierId = "hunter";
        String successUrl = "https://example.com/success";
        String cancelUrl = "https://example.com/cancel";
        String sessionUrl = "https://checkout.stripe.com/pay/session123";
        
        Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn(sessionUrl);
        
        when(subscriptionTierService.getTierById(tierId)).thenReturn(Optional.of(SubscriptionTier.HUNTER));
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(testPlayer));
        when(stripeSubscriptionService.createCheckoutSession(
                tierId, testEmail, successUrl, cancelUrl, testPlayerId))
                .thenReturn(mockSession);

        // When
        String result = subscriptionService.subscribePlayer(
                testPlayerId, tierId, testEmail, successUrl, cancelUrl);

        // Then
        assertEquals(sessionUrl, result);
        verify(stripeSubscriptionService).createCheckoutSession(
                tierId, testEmail, successUrl, cancelUrl, testPlayerId);
    }

    @Test
    @DisplayName("Should throw exception for invalid tier")
    void shouldThrowExceptionForInvalidTier() {
        // Given
        String invalidTierId = "invalid";
        when(subscriptionTierService.getTierById(invalidTierId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            subscriptionService.subscribePlayer(
                    testPlayerId, invalidTierId, testEmail, "success", "cancel");
        });
    }

    @Test
    @DisplayName("Should throw exception for non-existent player")
    void shouldThrowExceptionForNonExistentPlayer() {
        // Given
        String tierId = "hunter";
        when(subscriptionTierService.getTierById(tierId)).thenReturn(Optional.of(SubscriptionTier.HUNTER));
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            subscriptionService.subscribePlayer(
                    testPlayerId, tierId, testEmail, "success", "cancel");
        });
    }

    @Test
    @DisplayName("Should successfully cancel subscription")
    void shouldSuccessfullyCancelSubscription() throws StripeException {
        // Given
        testPlayer.setStripeSubscriptionId("sub_123");
        testPlayer.setCurrentSubscriptionTierId("hunter");
        
        Subscription mockSubscription = mock(Subscription.class);
        
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(testPlayer));
        when(stripeSubscriptionService.cancelSubscription("sub_123")).thenReturn(mockSubscription);

        // When
        boolean result = subscriptionService.cancelSubscription(testPlayerId);

        // Then
        assertTrue(result);
        verify(stripeSubscriptionService).cancelSubscription("sub_123");
        verify(playerDao).savePlayer(testPlayer);
        assertEquals("basic", testPlayer.getCurrentSubscriptionTierId());
        assertNull(testPlayer.getSubscriptionValidUntil());
    }

    @Test
    @DisplayName("Should handle cancellation for player without Stripe subscription")
    void shouldHandleCancellationForPlayerWithoutStripeSubscription() {
        // Given
        testPlayer.setStripeSubscriptionId(null);
        testPlayer.setCurrentSubscriptionTierId("hunter");
        
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(testPlayer));

        // When
        boolean result = subscriptionService.cancelSubscription(testPlayerId);

        // Then
        assertTrue(result);
        verifyNoInteractions(stripeSubscriptionService);
        verify(playerDao).savePlayer(testPlayer);
        assertEquals("basic", testPlayer.getCurrentSubscriptionTierId());
    }

    @Test
    @DisplayName("Should return false when cancelling non-existent player")
    void shouldReturnFalseWhenCancellingNonExistentPlayer() {
        // Given
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.empty());

        // When
        boolean result = subscriptionService.cancelSubscription(testPlayerId);

        // Then
        assertFalse(result);
        verifyNoInteractions(stripeSubscriptionService);
    }

    @Test
    @DisplayName("Should get player subscription info for basic tier")
    void shouldGetPlayerSubscriptionInfoForBasicTier() {
        // Given
        testPlayer.setCurrentSubscriptionTierId("basic");
        testPlayer.setSubscriptionValidUntil(null);
        
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(testPlayer));

        // When
        SubscriptionService.SubscriptionInfo result = subscriptionService.getPlayerSubscription(testPlayerId);

        // Then
        assertEquals("basic", result.getTierId());
        assertTrue(result.isActive());
        assertNull(result.getValidUntil());
        assertFalse(result.isExpiring());
    }

    @Test
    @DisplayName("Should get player subscription info for active paid tier")
    void shouldGetPlayerSubscriptionInfoForActivePaidTier() {
        // Given
        String futureDate = Instant.now().plus(15, ChronoUnit.DAYS).toString();
        testPlayer.setCurrentSubscriptionTierId("hunter");
        testPlayer.setSubscriptionValidUntil(futureDate);
        
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(testPlayer));

        // When
        SubscriptionService.SubscriptionInfo result = subscriptionService.getPlayerSubscription(testPlayerId);

        // Then
        assertEquals("hunter", result.getTierId());
        assertTrue(result.isActive());
        assertNotNull(result.getValidUntil());
        assertFalse(result.isExpiring()); // NOT within 7 days (15 days out)
    }

    @Test
    @DisplayName("Should get player subscription info for expired tier")
    void shouldGetPlayerSubscriptionInfoForExpiredTier() {
        // Given
        String pastDate = Instant.now().minus(5, ChronoUnit.DAYS).toString();
        testPlayer.setCurrentSubscriptionTierId("hunter");
        testPlayer.setSubscriptionValidUntil(pastDate);
        
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(testPlayer));

        // When
        SubscriptionService.SubscriptionInfo result = subscriptionService.getPlayerSubscription(testPlayerId);

        // Then
        assertEquals("hunter", result.getTierId());
        assertFalse(result.isActive());
        assertNotNull(result.getValidUntil());
        assertTrue(result.isExpiring());
    }

    @Test
    @DisplayName("Should return default subscription info for non-existent player")
    void shouldReturnDefaultSubscriptionInfoForNonExistentPlayer() {
        // Given
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.empty());

        // When
        SubscriptionService.SubscriptionInfo result = subscriptionService.getPlayerSubscription(testPlayerId);

        // Then
        assertEquals("basic", result.getTierId());
        assertFalse(result.isActive());
        assertNull(result.getValidUntil());
        assertFalse(result.isExpiring());
    }

    @Test
    @DisplayName("Should check player entitlement for active subscription")
    void shouldCheckPlayerEntitlementForActiveSubscription() {
        // Given
        String feature = "priority_queue";
        String futureDate = Instant.now().plus(15, ChronoUnit.DAYS).toString();
        testPlayer.setCurrentSubscriptionTierId("hunter");
        testPlayer.setSubscriptionValidUntil(futureDate);
        
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(testPlayer));
        when(subscriptionTierService.hasFeatureAccess("hunter", feature)).thenReturn(true);

        // When
        boolean result = subscriptionService.checkPlayerEntitlement(testPlayerId, feature);

        // Then
        assertTrue(result);
        verify(subscriptionTierService).hasFeatureAccess("hunter", feature);
    }

    @Test
    @DisplayName("Should check player entitlement for inactive subscription")
    void shouldCheckPlayerEntitlementForInactiveSubscription() {
        // Given
        String feature = "priority_queue";
        String pastDate = Instant.now().minus(5, ChronoUnit.DAYS).toString();
        testPlayer.setCurrentSubscriptionTierId("hunter");
        testPlayer.setSubscriptionValidUntil(pastDate);
        
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(testPlayer));
        when(subscriptionTierService.hasFeatureAccess("basic", feature)).thenReturn(false);

        // When
        boolean result = subscriptionService.checkPlayerEntitlement(testPlayerId, feature);

        // Then
        assertFalse(result);
        verify(subscriptionTierService).hasFeatureAccess("basic", feature);
    }

    @Test
    @DisplayName("Should return false for entitlement check on non-existent player")
    void shouldReturnFalseForEntitlementCheckOnNonExistentPlayer() {
        // Given
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.empty());

        // When
        boolean result = subscriptionService.checkPlayerEntitlement(testPlayerId, "any_feature");

        // Then
        assertFalse(result);
        verifyNoInteractions(subscriptionTierService);
    }

    @Test
    @DisplayName("Should process successful webhook")
    void shouldProcessSuccessfulWebhook() throws StripeException {
        // Given
        String payload = "webhook_payload";
        String sigHeader = "stripe_signature";
        
        StripeSubscriptionService.SubscriptionWebhookResult webhookResult = 
                new StripeSubscriptionService.SubscriptionWebhookResult(
                        "checkout.session.completed", 
                        "success", 
                        "hunter", 
                        Map.of(
                                "player_id", testPlayerId, 
                                "subscription_id", "sub_123",
                                "customer_id", "cus_123"));
        
        when(stripeSubscriptionService.handleWebhook(payload, sigHeader)).thenReturn(webhookResult);
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(testPlayer));

        // When
        SubscriptionService.WebhookResult result = subscriptionService.processWebhook(payload, sigHeader);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Webhook processed successfully", result.getMessage());
        verify(playerDao).savePlayer(testPlayer);
        assertEquals("hunter", testPlayer.getCurrentSubscriptionTierId());
        assertEquals("sub_123", testPlayer.getStripeSubscriptionId());
    }

    @Test
    @DisplayName("Should handle webhook validation failure")
    void shouldHandleWebhookValidationFailure() throws StripeException {
        // Given
        String payload = "invalid_payload";
        String sigHeader = "invalid_signature";
        
        StripeSubscriptionService.SubscriptionWebhookResult webhookResult = 
                new StripeSubscriptionService.SubscriptionWebhookResult(
                        "unknown", 
                        "error", 
                        null, 
                        null);
        
        when(stripeSubscriptionService.handleWebhook(payload, sigHeader)).thenReturn(webhookResult);

        // When
        SubscriptionService.WebhookResult result = subscriptionService.processWebhook(payload, sigHeader);

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Webhook validation failed"));
        verifyNoInteractions(playerDao);
    }

    @Test
    @DisplayName("Should handle webhook processing exception")
    void shouldHandleWebhookProcessingException() throws StripeException {
        // Given
        String payload = "webhook_payload";
        String sigHeader = "stripe_signature";
        
        when(stripeSubscriptionService.handleWebhook(payload, sigHeader))
                .thenThrow(new RuntimeException("Stripe webhook error"));

        // When
        SubscriptionService.WebhookResult result = subscriptionService.processWebhook(payload, sigHeader);

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Error processing webhook"));
    }

    @Test
    @DisplayName("Should handle subscription deletion webhook")
    void shouldHandleSubscriptionDeletionWebhook() throws StripeException {
        // Given
        String payload = "webhook_payload";
        String sigHeader = "stripe_signature";
        
        StripeSubscriptionService.SubscriptionWebhookResult webhookResult = 
                new StripeSubscriptionService.SubscriptionWebhookResult(
                        "customer.subscription.deleted", 
                        "success", 
                        "basic", 
                        Map.of("player_id", testPlayerId));
        
        when(stripeSubscriptionService.handleWebhook(payload, sigHeader)).thenReturn(webhookResult);
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(testPlayer));

        // When
        SubscriptionService.WebhookResult result = subscriptionService.processWebhook(payload, sigHeader);

        // Then
        assertTrue(result.isSuccess());
        verify(playerDao).savePlayer(testPlayer);
        assertEquals("basic", testPlayer.getCurrentSubscriptionTierId());
        assertNull(testPlayer.getSubscriptionValidUntil());
    }

    @Test
    @DisplayName("Should handle unhandled webhook event types")
    void shouldHandleUnhandledWebhookEventTypes() throws StripeException {
        // Given
        String payload = "webhook_payload";
        String sigHeader = "stripe_signature";
        
        StripeSubscriptionService.SubscriptionWebhookResult webhookResult = 
                new StripeSubscriptionService.SubscriptionWebhookResult(
                        "some.unhandled.event", 
                        "success", 
                        null, 
                        null);
        
        when(stripeSubscriptionService.handleWebhook(payload, sigHeader)).thenReturn(webhookResult);

        // When
        SubscriptionService.WebhookResult result = subscriptionService.processWebhook(payload, sigHeader);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Webhook processed successfully", result.getMessage());
        verifyNoInteractions(playerDao);
    }
} 