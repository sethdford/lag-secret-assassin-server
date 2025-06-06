package com.assassin.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Map;

@DisplayName("SubscriptionTier Tests")
class SubscriptionTierTest {

    @Test
    @DisplayName("Should have correct tier IDs and display names")
    void shouldHaveCorrectTierIdsAndDisplayNames() {
        assertEquals("basic", SubscriptionTier.BASIC.getTierId());
        assertEquals("Basic", SubscriptionTier.BASIC.getDisplayName());
        
        assertEquals("hunter", SubscriptionTier.HUNTER.getTierId());
        assertEquals("Hunter", SubscriptionTier.HUNTER.getDisplayName());
        
        assertEquals("assassin", SubscriptionTier.ASSASSIN.getTierId());
        assertEquals("Assassin", SubscriptionTier.ASSASSIN.getDisplayName());
        
        assertEquals("elite", SubscriptionTier.ELITE.getTierId());
        assertEquals("Elite", SubscriptionTier.ELITE.getDisplayName());
    }

    @Test
    @DisplayName("Should have correct pricing")
    void shouldHaveCorrectPricing() {
        assertEquals(new BigDecimal("0.00"), SubscriptionTier.BASIC.getMonthlyPriceUsd());
        assertEquals(Long.valueOf(0), SubscriptionTier.BASIC.getMonthlyPriceCents());
        
        assertEquals(new BigDecimal("4.99"), SubscriptionTier.HUNTER.getMonthlyPriceUsd());
        assertEquals(Long.valueOf(499), SubscriptionTier.HUNTER.getMonthlyPriceCents());
        
        assertEquals(new BigDecimal("9.99"), SubscriptionTier.ASSASSIN.getMonthlyPriceUsd());
        assertEquals(Long.valueOf(999), SubscriptionTier.ASSASSIN.getMonthlyPriceCents());
        
        assertEquals(new BigDecimal("19.99"), SubscriptionTier.ELITE.getMonthlyPriceUsd());
        assertEquals(Long.valueOf(1999), SubscriptionTier.ELITE.getMonthlyPriceCents());
    }

    @Test
    @DisplayName("Should find tier by ID correctly")
    void shouldFindTierByIdCorrectly() {
        assertEquals(SubscriptionTier.BASIC, SubscriptionTier.fromTierId("basic"));
        assertEquals(SubscriptionTier.HUNTER, SubscriptionTier.fromTierId("hunter"));
        assertEquals(SubscriptionTier.ASSASSIN, SubscriptionTier.fromTierId("assassin"));
        assertEquals(SubscriptionTier.ELITE, SubscriptionTier.fromTierId("elite"));
    }

    @Test
    @DisplayName("Should handle case-insensitive tier ID lookup")
    void shouldHandleCaseInsensitiveTierIdLookup() {
        assertEquals(SubscriptionTier.BASIC, SubscriptionTier.fromTierId("BASIC"));
        assertEquals(SubscriptionTier.HUNTER, SubscriptionTier.fromTierId("Hunter"));
        assertEquals(SubscriptionTier.ASSASSIN, SubscriptionTier.fromTierId("ASSASSIN"));
        assertEquals(SubscriptionTier.ELITE, SubscriptionTier.fromTierId("Elite"));
    }

    @Test
    @DisplayName("Should default to BASIC for null or invalid tier IDs")
    void shouldDefaultToBasicForNullOrInvalidTierIds() {
        assertEquals(SubscriptionTier.BASIC, SubscriptionTier.fromTierId(null));
        assertEquals(SubscriptionTier.BASIC, SubscriptionTier.fromTierId(""));
        assertEquals(SubscriptionTier.BASIC, SubscriptionTier.fromTierId("   "));
        assertEquals(SubscriptionTier.BASIC, SubscriptionTier.fromTierId("invalid"));
        assertEquals(SubscriptionTier.BASIC, SubscriptionTier.fromTierId("nonexistent"));
    }

    @Test
    @DisplayName("Should have correct benefit permissions")
    void shouldHaveCorrectBenefitPermissions() {
        // Premium proximity notifications
        assertFalse(SubscriptionTier.BASIC.hasPremiumProximityNotifications());
        assertFalse(SubscriptionTier.HUNTER.hasPremiumProximityNotifications());
        assertTrue(SubscriptionTier.ASSASSIN.hasPremiumProximityNotifications());
        assertTrue(SubscriptionTier.ELITE.hasPremiumProximityNotifications());

        // Private safe zone purchases
        assertFalse(SubscriptionTier.BASIC.canPurchasePrivateSafeZones());
        assertFalse(SubscriptionTier.HUNTER.canPurchasePrivateSafeZones());
        assertTrue(SubscriptionTier.ASSASSIN.canPurchasePrivateSafeZones());
        assertTrue(SubscriptionTier.ELITE.canPurchasePrivateSafeZones());

        // Nationwide event access
        assertFalse(SubscriptionTier.BASIC.hasNationwideEventAccess());
        assertFalse(SubscriptionTier.HUNTER.hasNationwideEventAccess());
        assertFalse(SubscriptionTier.ASSASSIN.hasNationwideEventAccess());
        assertTrue(SubscriptionTier.ELITE.hasNationwideEventAccess());

        // Custom game creation
        assertFalse(SubscriptionTier.BASIC.canCreateCustomGames());
        assertFalse(SubscriptionTier.HUNTER.canCreateCustomGames());
        assertTrue(SubscriptionTier.ASSASSIN.canCreateCustomGames());
        assertTrue(SubscriptionTier.ELITE.canCreateCustomGames());

        // Priority support
        assertFalse(SubscriptionTier.BASIC.hasPrioritySupport());
        assertTrue(SubscriptionTier.HUNTER.hasPrioritySupport());
        assertTrue(SubscriptionTier.ASSASSIN.hasPrioritySupport());
        assertTrue(SubscriptionTier.ELITE.hasPrioritySupport());
    }

    @Test
    @DisplayName("Should have correct notification priorities")
    void shouldHaveCorrectNotificationPriorities() {
        assertEquals(1, SubscriptionTier.BASIC.getNotificationPriority());
        assertEquals(2, SubscriptionTier.HUNTER.getNotificationPriority());
        assertEquals(3, SubscriptionTier.ASSASSIN.getNotificationPriority());
        assertEquals(4, SubscriptionTier.ELITE.getNotificationPriority());
    }

    @Test
    @DisplayName("Should correctly identify tier comparisons")
    void shouldCorrectlyIdentifyTierComparisons() {
        // isHigherThan tests
        assertTrue(SubscriptionTier.HUNTER.isHigherThan(SubscriptionTier.BASIC));
        assertTrue(SubscriptionTier.ASSASSIN.isHigherThan(SubscriptionTier.HUNTER));
        assertTrue(SubscriptionTier.ELITE.isHigherThan(SubscriptionTier.ASSASSIN));
        assertFalse(SubscriptionTier.BASIC.isHigherThan(SubscriptionTier.HUNTER));

        // canUpgradeTo tests
        assertTrue(SubscriptionTier.BASIC.canUpgradeTo(SubscriptionTier.HUNTER));
        assertTrue(SubscriptionTier.HUNTER.canUpgradeTo(SubscriptionTier.ELITE));
        assertFalse(SubscriptionTier.ELITE.canUpgradeTo(SubscriptionTier.BASIC));
        assertFalse(SubscriptionTier.ASSASSIN.canUpgradeTo(SubscriptionTier.HUNTER));

        // canDowngradeTo tests
        assertTrue(SubscriptionTier.ELITE.canDowngradeTo(SubscriptionTier.BASIC));
        assertTrue(SubscriptionTier.ASSASSIN.canDowngradeTo(SubscriptionTier.HUNTER));
        assertFalse(SubscriptionTier.BASIC.canDowngradeTo(SubscriptionTier.HUNTER));
        assertFalse(SubscriptionTier.HUNTER.canDowngradeTo(SubscriptionTier.ELITE));
    }

    @Test
    @DisplayName("Should detect benefits correctly")
    void shouldDetectBenefitsCorrectly() {
        assertTrue(SubscriptionTier.BASIC.hasBenefit("public games"));
        assertTrue(SubscriptionTier.HUNTER.hasBenefit("premium game modes"));
        assertTrue(SubscriptionTier.ASSASSIN.hasBenefit("proximity notifications"));
        assertTrue(SubscriptionTier.ELITE.hasBenefit("nationwide"));
        
        // Case insensitive benefit search
        assertTrue(SubscriptionTier.ELITE.hasBenefit("NATIONWIDE"));
        assertTrue(SubscriptionTier.ASSASSIN.hasBenefit("Private Safe Zone"));
        
        // Benefits not present
        assertFalse(SubscriptionTier.BASIC.hasBenefit("premium"));
        assertFalse(SubscriptionTier.HUNTER.hasBenefit("nationwide"));
    }

    @Test
    @DisplayName("Should serialize to map correctly")
    void shouldSerializeToMapCorrectly() {
        Map<String, Object> hunterMap = SubscriptionTier.HUNTER.toMap();
        
        assertEquals("hunter", hunterMap.get("tierId"));
        assertEquals("Hunter", hunterMap.get("displayName"));
        assertEquals(new BigDecimal("4.99"), hunterMap.get("monthlyPriceUsd"));
        assertEquals(Long.valueOf(499), hunterMap.get("monthlyPriceCents"));
        assertNotNull(hunterMap.get("benefits"));
        assertTrue(hunterMap.get("benefits") instanceof java.util.List);
    }

    @Test
    @DisplayName("Should have non-empty benefit lists")
    void shouldHaveNonEmptyBenefitLists() {
        assertFalse(SubscriptionTier.BASIC.getBenefits().isEmpty());
        assertFalse(SubscriptionTier.HUNTER.getBenefits().isEmpty());
        assertFalse(SubscriptionTier.ASSASSIN.getBenefits().isEmpty());
        assertFalse(SubscriptionTier.ELITE.getBenefits().isEmpty());
    }

    @Test
    @DisplayName("Should maintain proper tier ordering")
    void shouldMaintainProperTierOrdering() {
        SubscriptionTier[] expectedOrder = {
            SubscriptionTier.BASIC,
            SubscriptionTier.HUNTER,
            SubscriptionTier.ASSASSIN,
            SubscriptionTier.ELITE
        };
        
        SubscriptionTier[] actualOrder = SubscriptionTier.values();
        assertArrayEquals(expectedOrder, actualOrder);
    }
} 