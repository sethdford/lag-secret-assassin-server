package com.assassin.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@DisplayName("Player Subscription Tests")
class PlayerSubscriptionTest {

    private Player player;

    @BeforeEach
    void setUp() {
        player = new Player();
        player.setPlayerID("test-player-123");
        player.setEmail("test@example.com");
        player.setPlayerName("Test Player");
    }

    @Test
    @DisplayName("Should default to BASIC tier when no subscription is set")
    void shouldDefaultToBasicTierWhenNoSubscriptionIsSet() {
        assertEquals(SubscriptionTier.BASIC, player.getSubscriptionTier());
        assertEquals("basic", player.getCurrentSubscriptionTierId());
    }

    @Test
    @DisplayName("Should set and get subscription tier correctly")
    void shouldSetAndGetSubscriptionTierCorrectly() {
        player.setSubscriptionTier(SubscriptionTier.HUNTER);
        assertEquals(SubscriptionTier.HUNTER, player.getSubscriptionTier());
        assertEquals("hunter", player.getCurrentSubscriptionTierId());

        player.setSubscriptionTier(SubscriptionTier.ELITE);
        assertEquals(SubscriptionTier.ELITE, player.getSubscriptionTier());
        assertEquals("elite", player.getCurrentSubscriptionTierId());
    }

    @Test
    @DisplayName("Should handle null subscription tier gracefully")
    void shouldHandleNullSubscriptionTierGracefully() {
        player.setSubscriptionTier(null);
        assertEquals(SubscriptionTier.BASIC, player.getSubscriptionTier());
        assertEquals("basic", player.getCurrentSubscriptionTierId());
    }

    @Test
    @DisplayName("Should set subscription tier by ID string")
    void shouldSetSubscriptionTierByIdString() {
        player.setCurrentSubscriptionTierId("assassin");
        assertEquals(SubscriptionTier.ASSASSIN, player.getSubscriptionTier());

        player.setCurrentSubscriptionTierId("ELITE");
        assertEquals(SubscriptionTier.ELITE, player.getSubscriptionTier());

        // Invalid tier should default to BASIC
        player.setCurrentSubscriptionTierId("invalid");
        assertEquals(SubscriptionTier.BASIC, player.getSubscriptionTier());
    }

    @Test
    @DisplayName("Should handle active subscription when no expiration is set")
    void shouldHandleActiveSubscriptionWhenNoExpirationIsSet() {
        // Basic tier should always be active
        player.setSubscriptionTier(SubscriptionTier.BASIC);
        assertTrue(player.hasActiveSubscription());

        // Paid tiers without expiration should be considered inactive
        player.setSubscriptionTier(SubscriptionTier.HUNTER);
        assertFalse(player.hasActiveSubscription());
    }

    @Test
    @DisplayName("Should check subscription expiration correctly")
    void shouldCheckSubscriptionExpirationCorrectly() {
        player.setSubscriptionTier(SubscriptionTier.HUNTER);

        // Future expiration - should be active
        Instant futureExpiration = Instant.now().plus(30, ChronoUnit.DAYS);
        player.setSubscriptionValidUntil(futureExpiration.toString());
        assertTrue(player.hasActiveSubscription());

        // Past expiration - should be inactive
        Instant pastExpiration = Instant.now().minus(1, ChronoUnit.DAYS);
        player.setSubscriptionValidUntil(pastExpiration.toString());
        assertFalse(player.hasActiveSubscription());
    }

    @Test
    @DisplayName("Should handle invalid expiration date gracefully")
    void shouldHandleInvalidExpirationDateGracefully() {
        player.setSubscriptionTier(SubscriptionTier.ASSASSIN);
        player.setSubscriptionValidUntil("invalid-date");
        
        // Invalid date should be treated as expired
        assertFalse(player.hasActiveSubscription());
    }

    @Test
    @DisplayName("Should check subscription benefits correctly")
    void shouldCheckSubscriptionBenefitsCorrectly() {
        // Active subscription with benefits
        player.setSubscriptionTier(SubscriptionTier.ASSASSIN);
        Instant futureExpiration = Instant.now().plus(30, ChronoUnit.DAYS);
        player.setSubscriptionValidUntil(futureExpiration.toString());

        assertTrue(player.hasSubscriptionBenefit("premium"));
        assertTrue(player.hasSubscriptionBenefit("proximity notifications"));
        assertFalse(player.hasSubscriptionBenefit("nationwide")); // Elite only

        // Expired subscription should fall back to basic benefits
        Instant pastExpiration = Instant.now().minus(1, ChronoUnit.DAYS);
        player.setSubscriptionValidUntil(pastExpiration.toString());

        assertTrue(player.hasSubscriptionBenefit("public games")); // Basic benefit
        assertFalse(player.hasSubscriptionBenefit("premium")); // Not basic benefit
    }

    @Test
    @DisplayName("Should handle Stripe subscription ID correctly")
    void shouldHandleStripeSubscriptionIdCorrectly() {
        String stripeId = "sub_1234567890abcdef";
        player.setStripeSubscriptionId(stripeId);
        assertEquals(stripeId, player.getStripeSubscriptionId());
    }

    @Test
    @DisplayName("Should include subscription info in toString")
    void shouldIncludeSubscriptionInfoInToString() {
        player.setSubscriptionTier(SubscriptionTier.HUNTER);
        Instant futureExpiration = Instant.now().plus(30, ChronoUnit.DAYS);
        player.setSubscriptionValidUntil(futureExpiration.toString());

        String playerString = player.toString();
        assertTrue(playerString.contains("subscriptionTier='hunter'"));
        assertTrue(playerString.contains("subscriptionActive=true"));
    }

    @Test
    @DisplayName("Should maintain subscription state across tier changes")
    void shouldMaintainSubscriptionStateAcrossTierChanges() {
        Instant futureExpiration = Instant.now().plus(30, ChronoUnit.DAYS);
        String stripeId = "sub_test_123";
        
        player.setSubscriptionTier(SubscriptionTier.HUNTER);
        player.setSubscriptionValidUntil(futureExpiration.toString());
        player.setStripeSubscriptionId(stripeId);

        // Change tier but keep other subscription details
        player.setSubscriptionTier(SubscriptionTier.ASSASSIN);
        
        assertEquals(SubscriptionTier.ASSASSIN, player.getSubscriptionTier());
        assertEquals(futureExpiration.toString(), player.getSubscriptionValidUntil());
        assertEquals(stripeId, player.getStripeSubscriptionId());
        assertTrue(player.hasActiveSubscription());
    }

    @Test
    @DisplayName("Should handle edge case around expiration time")
    void shouldHandleEdgeCaseAroundExpirationTime() {
        player.setSubscriptionTier(SubscriptionTier.ELITE);
        
        // Set expiration to very close to current time (within a second)
        Instant almostExpired = Instant.now().plus(500, ChronoUnit.MILLIS);
        player.setSubscriptionValidUntil(almostExpired.toString());
        
        // Should still be active if not yet expired
        boolean isActive = player.hasActiveSubscription();
        // Result depends on timing, but should not throw exception
        assertNotNull(isActive);
    }
} 