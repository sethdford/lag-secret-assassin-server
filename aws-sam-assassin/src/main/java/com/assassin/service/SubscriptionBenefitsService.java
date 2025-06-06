package com.assassin.service;

import com.assassin.model.Player;
import com.assassin.model.SubscriptionTier;
import com.assassin.dao.PlayerDao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Service for managing and applying tier-specific subscription benefits.
 * Handles currency bonuses, free items, priority queue access, and other 
 * subscription-related perks based on player's current tier.
 */
public class SubscriptionBenefitsService {
    
    private static final Logger logger = Logger.getLogger(SubscriptionBenefitsService.class.getName());
    
    private final SubscriptionService subscriptionService;
    private final SubscriptionTierService subscriptionTierService;
    private final PlayerDao playerDao;
    
    /**
     * Constructor for SubscriptionBenefitsService.
     * 
     * @param subscriptionService Service for subscription management
     * @param subscriptionTierService Service for tier information
     * @param playerDao DAO for player data persistence
     */
    public SubscriptionBenefitsService(SubscriptionService subscriptionService,
                                     SubscriptionTierService subscriptionTierService,
                                     PlayerDao playerDao) {
        this.subscriptionService = subscriptionService;
        this.subscriptionTierService = subscriptionTierService;
        this.playerDao = playerDao;
    }
    
    /**
     * Apply daily login bonus based on player's subscription tier.
     * 
     * @param playerId The player ID
     * @return BenefitResult with applied bonus details
     */
    public BenefitResult applyDailyLoginBonus(String playerId) {
        try {
            Optional<Player> playerOpt = playerDao.getPlayerById(playerId);
            if (playerOpt.isEmpty()) {
                return new BenefitResult(false, "Player not found", null);
            }
            
            Player player = playerOpt.get();
            SubscriptionService.SubscriptionInfo subscription = subscriptionService.getPlayerSubscription(playerId);
            
            if (!subscription.isActive()) {
                // Basic tier gets standard bonus
                return applyBasicDailyBonus(player);
            }
            
            // Apply tier-specific bonus
            return applyTierDailyBonus(player, subscription.getTierId());
            
        } catch (Exception e) {
            logger.severe("Error applying daily login bonus for player " + playerId + ": " + e.getMessage());
            return new BenefitResult(false, "Error applying bonus: " + e.getMessage(), null);
        }
    }
    
    /**
     * Get tier-specific currency multiplier for earnings.
     * 
     * @param playerId The player ID
     * @return Currency multiplier (1.0 = no bonus, 1.5 = 50% bonus, etc.)
     */
    public double getCurrencyMultiplier(String playerId) {
        try {
            SubscriptionService.SubscriptionInfo subscription = subscriptionService.getPlayerSubscription(playerId);
            
            Optional<SubscriptionTier> tierOpt = subscriptionTierService.getTierById(subscription.getTierId());
            if (tierOpt.isEmpty()) {
                return 1.0; // No bonus for unknown tier
            }
            
            SubscriptionTier tier = tierOpt.get();
            switch (tier) {
                case BASIC: return 1.0;
                case HUNTER: return 1.25; // 25% bonus
                case ASSASSIN: return 1.5; // 50% bonus  
                case ELITE: return 2.0; // 100% bonus
                default: return 1.0;
            }
            
        } catch (Exception e) {
            logger.warning("Error getting currency multiplier for player " + playerId + ": " + e.getMessage());
            return 1.0; // Safe default
        }
    }
    
    /**
     * Apply basic tier daily bonus (for free players).
     */
    private BenefitResult applyBasicDailyBonus(Player player) {
        int bonusCoins = 10; // Basic daily bonus
        
        Map<String, Object> bonusDetails = Map.of(
                "coins", bonusCoins,
                "tier", "basic",
                "type", "daily_login"
        );
        
        logger.info("Applied basic daily bonus of " + bonusCoins + " coins to player " + player.getPlayerID());
        
        return new BenefitResult(true, "Basic daily bonus applied", bonusDetails);
    }
    
    /**
     * Apply tier-specific daily bonus for paid subscribers.
     */
    private BenefitResult applyTierDailyBonus(Player player, String tierId) {
        int bonusCoins;
        Map<String, Object> bonusDetails = new HashMap<>();
        
        // Calculate tier-specific bonus
        switch (tierId) {
            case "hunter":
                bonusCoins = 25; // Hunter gets 2.5x basic bonus
                bonusDetails.put("extra_items", 1); // Bonus item
                break;
            case "assassin":
                bonusCoins = 50; // Assassin gets 5x basic bonus
                bonusDetails.put("extra_items", 2); // More bonus items
                bonusDetails.put("xp_bonus", 1.2); // 20% XP bonus
                break;
            case "elite":
                bonusCoins = 100; // Elite gets 10x basic bonus
                bonusDetails.put("extra_items", 5); // Premium items
                bonusDetails.put("xp_bonus", 1.5); // 50% XP bonus
                bonusDetails.put("rare_item_chance", 0.1); // 10% chance for rare item
                break;
            default:
                bonusCoins = 10; // Default to basic
        }
        
        bonusDetails.put("coins", bonusCoins);
        bonusDetails.put("tier", tierId);
        bonusDetails.put("type", "daily_login");
        
        logger.info("Applied " + tierId + " tier daily bonus of " + bonusCoins + " coins to player " + player.getPlayerID());
        
        return new BenefitResult(true, tierId + " tier daily bonus applied", bonusDetails);
    }
    
    /**
     * Result class for benefit application operations.
     */
    public static class BenefitResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> benefitDetails;
        
        public BenefitResult(boolean success, String message, Map<String, Object> benefitDetails) {
            this.success = success;
            this.message = message;
            this.benefitDetails = benefitDetails;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Map<String, Object> getBenefitDetails() { return benefitDetails; }
        
        @Override
        public String toString() {
            return "BenefitResult{" +
                    "success=" + success +
                    ", message='" + message + '\'' +
                    ", benefitDetails=" + benefitDetails +
                    '}';
        }
    }
} 