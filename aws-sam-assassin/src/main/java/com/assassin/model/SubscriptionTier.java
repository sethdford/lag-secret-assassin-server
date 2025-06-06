package com.assassin.model;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Enum representing subscription tiers for the Assassin Game with associated pricing and benefits.
 * Each tier offers different levels of access and perks within the game ecosystem.
 */
public enum SubscriptionTier {
    
    BASIC("basic", "Basic", 
          new BigDecimal("0.00"), 
          Arrays.asList(
              "Access to public games",
              "Basic game statistics",
              "Standard notification frequency"
          )),
    
    HUNTER("hunter", "Hunter", 
           new BigDecimal("4.99"), 
           Arrays.asList(
               "All Basic benefits",
               "Access to premium game modes",
               "Advanced kill tracking statistics",
               "Priority customer support",
               "Reduced proximity alert cooldowns"
           )),
    
    ASSASSIN("assassin", "Assassin", 
             new BigDecimal("9.99"), 
             Arrays.asList(
                 "All Hunter benefits",
                 "Premium proximity notifications with distance",
                 "Enhanced kill verification methods",
                 "Access to private safe zone purchases",
                 "Custom game creation privileges",
                 "Exclusive seasonal events access"
             )),
    
    ELITE("elite", "Elite", 
          new BigDecimal("19.99"), 
          Arrays.asList(
              "All Assassin benefits",
              "Nationwide event access",
              "Premium intelligence perks",
              "Priority game matchmaking",
              "Exclusive Elite-only tournaments",
              "Advanced analytics dashboard",
              "White-glove customer service"
          ));

    private final String tierId;
    private final String displayName;
    private final BigDecimal monthlyPriceUsd;
    private final List<String> benefits;

    SubscriptionTier(String tierId, String displayName, BigDecimal monthlyPriceUsd, List<String> benefits) {
        this.tierId = tierId;
        this.displayName = displayName;
        this.monthlyPriceUsd = monthlyPriceUsd;
        this.benefits = benefits;
    }

    /**
     * Get the tier identifier (used in database and API).
     */
    public String getTierId() {
        return tierId;
    }

    /**
     * Get the human-readable display name for the tier.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the monthly subscription price in USD.
     */
    public BigDecimal getMonthlyPriceUsd() {
        return monthlyPriceUsd;
    }

    /**
     * Get the monthly price in cents (for Stripe integration).
     */
    public Long getMonthlyPriceCents() {
        return monthlyPriceUsd.multiply(new BigDecimal("100")).longValue();
    }

    /**
     * Get the list of benefits included with this tier.
     */
    public List<String> getBenefits() {
        return benefits;
    }

    /**
     * Check if this tier includes a specific benefit or feature.
     */
    public boolean hasBenefit(String benefit) {
        return benefits.stream()
                .anyMatch(b -> b.toLowerCase().contains(benefit.toLowerCase()));
    }

    /**
     * Check if this tier has premium proximity notifications.
     */
    public boolean hasPremiumProximityNotifications() {
        return this == ASSASSIN || this == ELITE;
    }

    /**
     * Check if this tier can purchase private safe zones.
     */
    public boolean canPurchasePrivateSafeZones() {
        return this == ASSASSIN || this == ELITE;
    }

    /**
     * Check if this tier has access to nationwide events.
     */
    public boolean hasNationwideEventAccess() {
        return this == ELITE;
    }

    /**
     * Check if this tier can create custom games.
     */
    public boolean canCreateCustomGames() {
        return this == ASSASSIN || this == ELITE;
    }

    /**
     * Check if this tier has priority support.
     */
    public boolean hasPrioritySupport() {
        return this == HUNTER || this == ASSASSIN || this == ELITE;
    }

    /**
     * Get the notification priority level for this tier.
     * Higher values indicate higher priority.
     */
    public int getNotificationPriority() {
        return switch (this) {
            case BASIC -> 1;
            case HUNTER -> 2;
            case ASSASSIN -> 3;
            case ELITE -> 4;
        };
    }

    /**
     * Find a SubscriptionTier by its tier ID.
     */
    public static SubscriptionTier fromTierId(String tierId) {
        if (tierId == null || tierId.trim().isEmpty()) {
            return BASIC; // Default to basic for null/empty
        }
        
        for (SubscriptionTier tier : values()) {
            if (tier.getTierId().equalsIgnoreCase(tierId.trim())) {
                return tier;
            }
        }
        
        return BASIC; // Default fallback
    }

    /**
     * Get a map representation of the tier for JSON serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("tierId", tierId);
        map.put("displayName", displayName);
        map.put("monthlyPriceUsd", monthlyPriceUsd);
        map.put("monthlyPriceCents", getMonthlyPriceCents());
        map.put("benefits", benefits);
        return map;
    }

    /**
     * Check if this tier is higher than another tier.
     */
    public boolean isHigherThan(SubscriptionTier other) {
        return this.ordinal() > other.ordinal();
    }

    /**
     * Check if this tier allows upgrading to another tier.
     */
    public boolean canUpgradeTo(SubscriptionTier target) {
        return target.ordinal() > this.ordinal();
    }

    /**
     * Check if this tier allows downgrading to another tier.
     */
    public boolean canDowngradeTo(SubscriptionTier target) {
        return target.ordinal() < this.ordinal();
    }
} 