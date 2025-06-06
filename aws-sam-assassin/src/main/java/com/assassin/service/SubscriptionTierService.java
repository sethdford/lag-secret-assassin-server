package com.assassin.service;

import com.assassin.model.SubscriptionTier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Map;

/**
 * Service for managing subscription tier operations and business logic.
 * Provides access to subscription tier information and tier-related operations.
 */
public class SubscriptionTierService {
    
    /**
     * Get all available subscription tiers in order from basic to elite.
     * 
     * @return List of all subscription tiers ordered by level
     */
    public List<SubscriptionTier> getAllTiers() {
        return Arrays.asList(SubscriptionTier.values());
    }
    
    /**
     * Get a subscription tier by its tier ID.
     * 
     * @param tierId The tier ID (e.g., "basic", "hunter", "assassin", "elite")
     * @return Optional containing the tier if found, empty otherwise
     */
    public Optional<SubscriptionTier> getTierById(String tierId) {
        if (tierId == null) {
            return Optional.empty();
        }
        
        return Arrays.stream(SubscriptionTier.values())
                .filter(tier -> tier.getTierId().equals(tierId.toLowerCase()))
                .findFirst();
    }
    
    /**
     * Get the default subscription tier for new users.
     * 
     * @return The basic subscription tier
     */
    public SubscriptionTier getDefaultTier() {
        return SubscriptionTier.BASIC;
    }
    
    /**
     * Check if a given tier ID is valid.
     * 
     * @param tierId The tier ID to validate
     * @return true if the tier ID is valid, false otherwise
     */
    public boolean isValidTierId(String tierId) {
        return getTierById(tierId).isPresent();
    }
    
    /**
     * Get subscription tiers that are available for purchase (non-free tiers).
     * 
     * @return List of purchasable subscription tiers
     */
    public List<SubscriptionTier> getPurchasableTiers() {
        return Arrays.stream(SubscriptionTier.values())
                .filter(tier -> tier.getMonthlyPriceUsd().compareTo(java.math.BigDecimal.ZERO) > 0)
                .toList();
    }
    
    /**
     * Check if one tier has higher privileges than another.
     * 
     * @param tierA First tier to compare
     * @param tierB Second tier to compare
     * @return true if tierA has higher privileges than tierB
     */
    public boolean hasHigherPrivileges(SubscriptionTier tierA, SubscriptionTier tierB) {
        if (tierA == null || tierB == null) {
            return false;
        }
        return tierA.ordinal() > tierB.ordinal();
    }
    
    /**
     * Check if a player with the given tier ID has access to a specific feature.
     * This is a placeholder for more complex entitlement logic.
     * 
     * @param tierId The player's subscription tier ID
     * @param featureName The feature name to check access for
     * @return true if the tier has access to the feature
     */
    public boolean hasFeatureAccess(String tierId, String featureName) {
        Optional<SubscriptionTier> tier = getTierById(tierId);
        if (tier.isEmpty()) {
            return false;
        }
        
        // Basic feature access logic - can be enhanced with more complex rules
        switch (featureName.toLowerCase()) {
            case "public_games":
                return true; // All tiers can access public games
            case "private_games":
                return tier.get() != SubscriptionTier.BASIC;
            case "advanced_stats":
                return tier.get() == SubscriptionTier.ASSASSIN || tier.get() == SubscriptionTier.ELITE;
            case "priority_support":
                return tier.get() == SubscriptionTier.ELITE;
            case "custom_themes":
                return tier.get() != SubscriptionTier.BASIC;
            default:
                return false;
        }
    }
    
    /**
     * Get a tier-specific configuration value.
     * 
     * @param tierId The subscription tier ID
     * @param configKey The configuration key
     * @return The configuration value for the tier, or null if not found
     */
    public Object getTierConfig(String tierId, String configKey) {
        Optional<SubscriptionTier> tier = getTierById(tierId);
        if (tier.isEmpty()) {
            return null;
        }
        
        // Define tier-specific configurations
        return switch (configKey.toLowerCase()) {
            case "maxconcurrentgames" -> getMaxConcurrentGamesForTier(tier.get());
            case "notificationfrequencyminutes" -> getNotificationFrequencyForTier(tier.get());
            case "prioritylevel" -> tier.get().getNotificationPriority();
            case "monthlypricecentts" -> tier.get().getMonthlyPriceCents();
            case "monthlypriceusd" -> tier.get().getMonthlyPriceUsd();
            default -> null;
        };
    }
    
    /**
     * Get the maximum number of games a tier can participate in simultaneously.
     * 
     * @param tierId The subscription tier ID
     * @return Maximum concurrent games, or 0 if tier not found
     */
    public int getMaxConcurrentGames(String tierId) {
        Optional<SubscriptionTier> tier = getTierById(tierId);
        return tier.map(this::getMaxConcurrentGamesForTier).orElse(0);
    }
    
    /**
     * Get the notification frequency for a tier.
     * 
     * @param tierId The subscription tier ID
     * @return Notification frequency in minutes, or 60 if tier not found
     */
    public int getNotificationFrequencyMinutes(String tierId) {
        Optional<SubscriptionTier> tier = getTierById(tierId);
        return tier.map(this::getNotificationFrequencyForTier).orElse(60);
    }
    
    /**
     * Private helper to get max concurrent games for a specific tier.
     */
    private int getMaxConcurrentGamesForTier(SubscriptionTier tier) {
        return switch (tier) {
            case BASIC -> 1;
            case HUNTER -> 3;
            case ASSASSIN -> 5;
            case ELITE -> 10;
        };
    }
    
    /**
     * Private helper to get notification frequency for a specific tier.
     */
    private int getNotificationFrequencyForTier(SubscriptionTier tier) {
        return switch (tier) {
            case BASIC -> 60;    // 1 hour
            case HUNTER -> 30;   // 30 minutes
            case ASSASSIN -> 15; // 15 minutes
            case ELITE -> 5;     // 5 minutes
        };
    }
} 