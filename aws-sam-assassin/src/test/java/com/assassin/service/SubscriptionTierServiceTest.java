package com.assassin.service;

import com.assassin.model.SubscriptionTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

@DisplayName("SubscriptionTierService Tests")
class SubscriptionTierServiceTest {

    private SubscriptionTierService subscriptionTierService;

    @BeforeEach
    void setUp() {
        subscriptionTierService = new SubscriptionTierService();
    }

    @Test
    @DisplayName("Should return all subscription tiers")
    void shouldReturnAllSubscriptionTiers() {
        List<SubscriptionTier> tiers = subscriptionTierService.getAllTiers();
        
        assertEquals(4, tiers.size());
        assertEquals(SubscriptionTier.BASIC, tiers.get(0));
        assertEquals(SubscriptionTier.HUNTER, tiers.get(1));
        assertEquals(SubscriptionTier.ASSASSIN, tiers.get(2));
        assertEquals(SubscriptionTier.ELITE, tiers.get(3));
    }

    @Test
    @DisplayName("Should find tier by valid tier ID")
    void shouldFindTierByValidTierId() {
        Optional<SubscriptionTier> basic = subscriptionTierService.getTierById("basic");
        Optional<SubscriptionTier> hunter = subscriptionTierService.getTierById("hunter");
        Optional<SubscriptionTier> assassin = subscriptionTierService.getTierById("assassin");
        Optional<SubscriptionTier> elite = subscriptionTierService.getTierById("elite");
        
        assertTrue(basic.isPresent());
        assertEquals(SubscriptionTier.BASIC, basic.get());
        
        assertTrue(hunter.isPresent());
        assertEquals(SubscriptionTier.HUNTER, hunter.get());
        
        assertTrue(assassin.isPresent());
        assertEquals(SubscriptionTier.ASSASSIN, assassin.get());
        
        assertTrue(elite.isPresent());
        assertEquals(SubscriptionTier.ELITE, elite.get());
    }

    @Test
    @DisplayName("Should return empty for invalid tier ID")
    void shouldReturnEmptyForInvalidTierId() {
        Optional<SubscriptionTier> invalid = subscriptionTierService.getTierById("invalid");
        Optional<SubscriptionTier> nullTier = subscriptionTierService.getTierById(null);
        
        assertFalse(invalid.isPresent());
        assertFalse(nullTier.isPresent());
    }

    @Test
    @DisplayName("Should return BASIC as default tier")
    void shouldReturnBasicAsDefaultTier() {
        SubscriptionTier defaultTier = subscriptionTierService.getDefaultTier();
        assertEquals(SubscriptionTier.BASIC, defaultTier);
    }

    @Test
    @DisplayName("Should validate tier IDs correctly")
    void shouldValidateTierIdsCorrectly() {
        assertTrue(subscriptionTierService.isValidTierId("basic"));
        assertTrue(subscriptionTierService.isValidTierId("hunter"));
        assertTrue(subscriptionTierService.isValidTierId("assassin"));
        assertTrue(subscriptionTierService.isValidTierId("elite"));
        
        assertFalse(subscriptionTierService.isValidTierId("invalid"));
        assertFalse(subscriptionTierService.isValidTierId(null));
        assertFalse(subscriptionTierService.isValidTierId(""));
    }

    @Test
    @DisplayName("Should return purchasable tiers (non-free)")
    void shouldReturnPurchasableTiers() {
        List<SubscriptionTier> purchasable = subscriptionTierService.getPurchasableTiers();
        
        assertEquals(3, purchasable.size());
        assertFalse(purchasable.contains(SubscriptionTier.BASIC));
        assertTrue(purchasable.contains(SubscriptionTier.HUNTER));
        assertTrue(purchasable.contains(SubscriptionTier.ASSASSIN));
        assertTrue(purchasable.contains(SubscriptionTier.ELITE));
    }

    @Test
    @DisplayName("Should compare tier privileges correctly")
    void shouldCompareTierPrivilegesCorrectly() {
        // Higher tier comparisons
        assertTrue(subscriptionTierService.hasHigherPrivileges(SubscriptionTier.HUNTER, SubscriptionTier.BASIC));
        assertTrue(subscriptionTierService.hasHigherPrivileges(SubscriptionTier.ASSASSIN, SubscriptionTier.HUNTER));
        assertTrue(subscriptionTierService.hasHigherPrivileges(SubscriptionTier.ELITE, SubscriptionTier.ASSASSIN));
        assertTrue(subscriptionTierService.hasHigherPrivileges(SubscriptionTier.ELITE, SubscriptionTier.BASIC));
        
        // Lower tier comparisons
        assertFalse(subscriptionTierService.hasHigherPrivileges(SubscriptionTier.BASIC, SubscriptionTier.HUNTER));
        assertFalse(subscriptionTierService.hasHigherPrivileges(SubscriptionTier.HUNTER, SubscriptionTier.ASSASSIN));
        
        // Same tier comparisons
        assertFalse(subscriptionTierService.hasHigherPrivileges(SubscriptionTier.BASIC, SubscriptionTier.BASIC));
        
        // Null comparisons
        assertFalse(subscriptionTierService.hasHigherPrivileges(null, SubscriptionTier.BASIC));
        assertFalse(subscriptionTierService.hasHigherPrivileges(SubscriptionTier.BASIC, null));
    }

    @Test
    @DisplayName("Should check feature access correctly")
    void shouldCheckFeatureAccessCorrectly() {
        // Public games - all tiers should have access
        assertTrue(subscriptionTierService.hasFeatureAccess("basic", "public_games"));
        assertTrue(subscriptionTierService.hasFeatureAccess("hunter", "public_games"));
        assertTrue(subscriptionTierService.hasFeatureAccess("assassin", "public_games"));
        assertTrue(subscriptionTierService.hasFeatureAccess("elite", "public_games"));
        
        // Private games - only paid tiers
        assertFalse(subscriptionTierService.hasFeatureAccess("basic", "private_games"));
        assertTrue(subscriptionTierService.hasFeatureAccess("hunter", "private_games"));
        assertTrue(subscriptionTierService.hasFeatureAccess("assassin", "private_games"));
        assertTrue(subscriptionTierService.hasFeatureAccess("elite", "private_games"));
        
        // Advanced stats - only Assassin and Elite
        assertFalse(subscriptionTierService.hasFeatureAccess("basic", "advanced_stats"));
        assertFalse(subscriptionTierService.hasFeatureAccess("hunter", "advanced_stats"));
        assertTrue(subscriptionTierService.hasFeatureAccess("assassin", "advanced_stats"));
        assertTrue(subscriptionTierService.hasFeatureAccess("elite", "advanced_stats"));
        
        // Priority support - only Elite
        assertFalse(subscriptionTierService.hasFeatureAccess("basic", "priority_support"));
        assertFalse(subscriptionTierService.hasFeatureAccess("hunter", "priority_support"));
        assertFalse(subscriptionTierService.hasFeatureAccess("assassin", "priority_support"));
        assertTrue(subscriptionTierService.hasFeatureAccess("elite", "priority_support"));
        
        // Custom themes - paid tiers
        assertFalse(subscriptionTierService.hasFeatureAccess("basic", "custom_themes"));
        assertTrue(subscriptionTierService.hasFeatureAccess("hunter", "custom_themes"));
        assertTrue(subscriptionTierService.hasFeatureAccess("assassin", "custom_themes"));
        assertTrue(subscriptionTierService.hasFeatureAccess("elite", "custom_themes"));
        
        // Unknown feature
        assertFalse(subscriptionTierService.hasFeatureAccess("basic", "unknown_feature"));
        
        // Invalid tier
        assertFalse(subscriptionTierService.hasFeatureAccess("invalid", "public_games"));
    }

    @Test
    @DisplayName("Should return correct max concurrent games by tier")
    void shouldReturnCorrectMaxConcurrentGamesByTier() {
        assertEquals(1, subscriptionTierService.getMaxConcurrentGames("basic"));
        assertEquals(3, subscriptionTierService.getMaxConcurrentGames("hunter"));
        assertEquals(5, subscriptionTierService.getMaxConcurrentGames("assassin"));
        assertEquals(10, subscriptionTierService.getMaxConcurrentGames("elite"));
        assertEquals(0, subscriptionTierService.getMaxConcurrentGames("invalid"));
    }

    @Test
    @DisplayName("Should return correct notification frequency by tier")
    void shouldReturnCorrectNotificationFrequencyByTier() {
        assertEquals(60, subscriptionTierService.getNotificationFrequencyMinutes("basic"));
        assertEquals(30, subscriptionTierService.getNotificationFrequencyMinutes("hunter"));
        assertEquals(15, subscriptionTierService.getNotificationFrequencyMinutes("assassin"));
        assertEquals(5, subscriptionTierService.getNotificationFrequencyMinutes("elite"));
        assertEquals(60, subscriptionTierService.getNotificationFrequencyMinutes("invalid"));
    }

    @Test
    @DisplayName("Should return correct tier configuration values")
    void shouldReturnCorrectTierConfigurationValues() {
        // Max concurrent games
        assertEquals(1, subscriptionTierService.getTierConfig("basic", "maxConcurrentGames"));
        assertEquals(3, subscriptionTierService.getTierConfig("hunter", "maxConcurrentGames"));
        
        // Notification frequency
        assertEquals(60, subscriptionTierService.getTierConfig("basic", "notificationFrequencyMinutes"));
        assertEquals(30, subscriptionTierService.getTierConfig("hunter", "notificationFrequencyMinutes"));
        
        // Priority level
        assertEquals(1, subscriptionTierService.getTierConfig("basic", "priorityLevel"));
        assertEquals(4, subscriptionTierService.getTierConfig("elite", "priorityLevel"));
        
        // Unknown config key
        assertNull(subscriptionTierService.getTierConfig("basic", "unknown"));
        
        // Invalid tier
        assertNull(subscriptionTierService.getTierConfig("invalid", "maxConcurrentGames"));
    }
} 