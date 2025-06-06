package com.assassin.service;

import com.assassin.dao.PlayerDao;
import com.assassin.model.Player;
import com.assassin.model.SubscriptionTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@DisplayName("SubscriptionBenefitsService Tests")
class SubscriptionBenefitsServiceTest {

    @Mock
    private SubscriptionService subscriptionService;
    
    @Mock
    private SubscriptionTierService subscriptionTierService;
    
    @Mock
    private PlayerDao playerDao;
    
    private SubscriptionBenefitsService benefitsService;
    private final String testPlayerId = "test-player-123";
    private Player testPlayer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        benefitsService = new SubscriptionBenefitsService(subscriptionService, subscriptionTierService, playerDao);
        
        testPlayer = new Player();
        testPlayer.setPlayerID(testPlayerId);
    }

    @Test
    @DisplayName("Should apply basic daily bonus for free tier players")
    void shouldApplyBasicDailyBonusForFreeTier() {
        // Arrange
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(testPlayer));
        
        SubscriptionService.SubscriptionInfo inactiveSubscription = 
                new SubscriptionService.SubscriptionInfo("basic", false, null, false);
        when(subscriptionService.getPlayerSubscription(testPlayerId)).thenReturn(inactiveSubscription);

        // Act
        SubscriptionBenefitsService.BenefitResult result = benefitsService.applyDailyLoginBonus(testPlayerId);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("Basic daily bonus applied", result.getMessage());
        assertNotNull(result.getBenefitDetails());
        assertEquals(10, result.getBenefitDetails().get("coins"));
        assertEquals("basic", result.getBenefitDetails().get("tier"));
        assertEquals("daily_login", result.getBenefitDetails().get("type"));
    }

    @Test
    @DisplayName("Should apply hunter tier daily bonus")
    void shouldApplyHunterTierDailyBonus() {
        // Arrange
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(testPlayer));
        
        SubscriptionService.SubscriptionInfo activeSubscription = 
                new SubscriptionService.SubscriptionInfo("hunter", true, Instant.parse("2025-12-31T23:59:59Z"), false);
        when(subscriptionService.getPlayerSubscription(testPlayerId)).thenReturn(activeSubscription);

        // Act
        SubscriptionBenefitsService.BenefitResult result = benefitsService.applyDailyLoginBonus(testPlayerId);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("hunter tier daily bonus applied", result.getMessage());
        assertNotNull(result.getBenefitDetails());
        assertEquals(25, result.getBenefitDetails().get("coins"));
        assertEquals("hunter", result.getBenefitDetails().get("tier"));
        assertEquals(1, result.getBenefitDetails().get("extra_items"));
    }

    @Test
    @DisplayName("Should apply assassin tier daily bonus with XP boost")
    void shouldApplyAssassinTierDailyBonus() {
        // Arrange
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(testPlayer));
        
        SubscriptionService.SubscriptionInfo activeSubscription = 
                new SubscriptionService.SubscriptionInfo("assassin", true, Instant.parse("2025-12-31T23:59:59Z"), false);
        when(subscriptionService.getPlayerSubscription(testPlayerId)).thenReturn(activeSubscription);

        // Act
        SubscriptionBenefitsService.BenefitResult result = benefitsService.applyDailyLoginBonus(testPlayerId);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("assassin tier daily bonus applied", result.getMessage());
        assertNotNull(result.getBenefitDetails());
        assertEquals(50, result.getBenefitDetails().get("coins"));
        assertEquals("assassin", result.getBenefitDetails().get("tier"));
        assertEquals(2, result.getBenefitDetails().get("extra_items"));
        assertEquals(1.2, result.getBenefitDetails().get("xp_bonus"));
    }

    @Test
    @DisplayName("Should apply elite tier daily bonus with premium benefits")
    void shouldApplyEliteTierDailyBonus() {
        // Arrange
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.of(testPlayer));
        
        SubscriptionService.SubscriptionInfo activeSubscription = 
                new SubscriptionService.SubscriptionInfo("elite", true, Instant.parse("2025-12-31T23:59:59Z"), false);
        when(subscriptionService.getPlayerSubscription(testPlayerId)).thenReturn(activeSubscription);

        // Act
        SubscriptionBenefitsService.BenefitResult result = benefitsService.applyDailyLoginBonus(testPlayerId);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("elite tier daily bonus applied", result.getMessage());
        assertNotNull(result.getBenefitDetails());
        assertEquals(100, result.getBenefitDetails().get("coins"));
        assertEquals("elite", result.getBenefitDetails().get("tier"));
        assertEquals(5, result.getBenefitDetails().get("extra_items"));
        assertEquals(1.5, result.getBenefitDetails().get("xp_bonus"));
        assertEquals(0.1, result.getBenefitDetails().get("rare_item_chance"));
    }

    @Test
    @DisplayName("Should return error when player not found")
    void shouldReturnErrorWhenPlayerNotFound() {
        // Arrange
        when(playerDao.getPlayerById(testPlayerId)).thenReturn(Optional.empty());

        // Act
        SubscriptionBenefitsService.BenefitResult result = benefitsService.applyDailyLoginBonus(testPlayerId);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Player not found", result.getMessage());
        assertNull(result.getBenefitDetails());
    }

    @Test
    @DisplayName("Should get basic currency multiplier for free tier")
    void shouldGetBasicCurrencyMultiplierForFreeTier() {
        // Arrange
        SubscriptionService.SubscriptionInfo inactiveSubscription = 
                new SubscriptionService.SubscriptionInfo("basic", false, null, false);
        when(subscriptionService.getPlayerSubscription(testPlayerId)).thenReturn(inactiveSubscription);
        when(subscriptionTierService.getTierById("basic")).thenReturn(Optional.of(SubscriptionTier.BASIC));

        // Act
        double multiplier = benefitsService.getCurrencyMultiplier(testPlayerId);

        // Assert
        assertEquals(1.0, multiplier, 0.001);
    }

    @Test
    @DisplayName("Should get hunter currency multiplier")
    void shouldGetHunterCurrencyMultiplier() {
        // Arrange
        SubscriptionService.SubscriptionInfo activeSubscription = 
                new SubscriptionService.SubscriptionInfo("hunter", true, Instant.parse("2025-12-31T23:59:59Z"), false);
        when(subscriptionService.getPlayerSubscription(testPlayerId)).thenReturn(activeSubscription);
        when(subscriptionTierService.getTierById("hunter")).thenReturn(Optional.of(SubscriptionTier.HUNTER));

        // Act
        double multiplier = benefitsService.getCurrencyMultiplier(testPlayerId);

        // Assert
        assertEquals(1.25, multiplier, 0.001);
    }

    @Test
    @DisplayName("Should get assassin currency multiplier")
    void shouldGetAssassinCurrencyMultiplier() {
        // Arrange
        SubscriptionService.SubscriptionInfo activeSubscription = 
                new SubscriptionService.SubscriptionInfo("assassin", true, Instant.parse("2025-12-31T23:59:59Z"), false);
        when(subscriptionService.getPlayerSubscription(testPlayerId)).thenReturn(activeSubscription);
        when(subscriptionTierService.getTierById("assassin")).thenReturn(Optional.of(SubscriptionTier.ASSASSIN));

        // Act
        double multiplier = benefitsService.getCurrencyMultiplier(testPlayerId);

        // Assert
        assertEquals(1.5, multiplier, 0.001);
    }

    @Test
    @DisplayName("Should get elite currency multiplier")
    void shouldGetEliteCurrencyMultiplier() {
        // Arrange
        SubscriptionService.SubscriptionInfo activeSubscription = 
                new SubscriptionService.SubscriptionInfo("elite", true, Instant.parse("2025-12-31T23:59:59Z"), false);
        when(subscriptionService.getPlayerSubscription(testPlayerId)).thenReturn(activeSubscription);
        when(subscriptionTierService.getTierById("elite")).thenReturn(Optional.of(SubscriptionTier.ELITE));

        // Act
        double multiplier = benefitsService.getCurrencyMultiplier(testPlayerId);

        // Assert
        assertEquals(2.0, multiplier, 0.001);
    }

    @Test
    @DisplayName("Should return default multiplier when tier not found")
    void shouldReturnDefaultMultiplierWhenTierNotFound() {
        // Arrange
        SubscriptionService.SubscriptionInfo activeSubscription = 
                new SubscriptionService.SubscriptionInfo("unknown", true, Instant.parse("2025-12-31T23:59:59Z"), false);
        when(subscriptionService.getPlayerSubscription(testPlayerId)).thenReturn(activeSubscription);
        when(subscriptionTierService.getTierById("unknown")).thenReturn(Optional.empty());

        // Act
        double multiplier = benefitsService.getCurrencyMultiplier(testPlayerId);

        // Assert
        assertEquals(1.0, multiplier, 0.001);
    }

    @Test
    @DisplayName("Should handle service exceptions gracefully")
    void shouldHandleServiceExceptionsGracefully() {
        // Arrange
        when(playerDao.getPlayerById(testPlayerId)).thenThrow(new RuntimeException("Database error"));

        // Act
        SubscriptionBenefitsService.BenefitResult result = benefitsService.applyDailyLoginBonus(testPlayerId);

        // Assert
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Error applying bonus"));
        assertNull(result.getBenefitDetails());
    }

    @Test
    @DisplayName("Should handle currency multiplier exceptions gracefully")
    void shouldHandleCurrencyMultiplierExceptionsGracefully() {
        // Arrange
        when(subscriptionService.getPlayerSubscription(testPlayerId)).thenThrow(new RuntimeException("Service error"));

        // Act
        double multiplier = benefitsService.getCurrencyMultiplier(testPlayerId);

        // Assert
        assertEquals(1.0, multiplier, 0.001); // Safe default
    }
} 