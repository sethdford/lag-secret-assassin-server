package com.assassin.service;

import com.assassin.dao.PlayerDao;
import com.assassin.model.Player;
import com.assassin.model.SubscriptionTier;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Service for managing player subscriptions, integrating tier management,
 * Stripe payment processing, and player data persistence.
 */
public class SubscriptionService {
    
    private static final Logger logger = Logger.getLogger(SubscriptionService.class.getName());
    
    private final SubscriptionTierService subscriptionTierService;
    private final StripeSubscriptionService stripeSubscriptionService;
    private final PlayerDao playerDao;
    
    /**
     * Constructor for SubscriptionService.
     * 
     * @param subscriptionTierService Service for tier management
     * @param stripeSubscriptionService Service for Stripe integration
     * @param playerDao DAO for player data persistence
     */
    public SubscriptionService(SubscriptionTierService subscriptionTierService,
                             StripeSubscriptionService stripeSubscriptionService,
                             PlayerDao playerDao) {
        this.subscriptionTierService = subscriptionTierService;
        this.stripeSubscriptionService = stripeSubscriptionService;
        this.playerDao = playerDao;
    }
    
    /**
     * Get all available subscription tiers.
     * 
     * @return List of available subscription tiers
     */
    public List<SubscriptionTier> getAvailableTiers() {
        return subscriptionTierService.getAllTiers();
    }
    
    /**
     * Get only purchasable subscription tiers (excludes free tiers).
     * 
     * @return List of purchasable subscription tiers
     */
    public List<SubscriptionTier> getPurchasableTiers() {
        return subscriptionTierService.getPurchasableTiers();
    }
    
    /**
     * Subscribe a player to a specific tier via Stripe checkout.
     * 
     * @param playerId The player ID
     * @param tierId The subscription tier ID
     * @param customerEmail The customer email for Stripe
     * @param successUrl URL to redirect to on successful payment
     * @param cancelUrl URL to redirect to on cancelled payment
     * @return Stripe checkout session URL
     * @throws IllegalArgumentException If tier or player not found
     * @throws StripeException If Stripe operation fails
     */
    public String subscribePlayer(String playerId, String tierId, String customerEmail, 
                                String successUrl, String cancelUrl) throws StripeException {
        
        // Validate tier exists
        Optional<SubscriptionTier> tier = subscriptionTierService.getTierById(tierId);
        if (tier.isEmpty()) {
            throw new IllegalArgumentException("Invalid subscription tier: " + tierId);
        }
        
        // Validate player exists
        Optional<Player> player = playerDao.getPlayerById(playerId);
        if (player.isEmpty()) {
            throw new IllegalArgumentException("Player not found: " + playerId);
        }
        
        // For basic tier, just update player directly without Stripe
        if ("basic".equals(tierId)) {
            updatePlayerSubscription(playerId, tierId, null);
            return successUrl; // Return success URL directly for free tier
        }
        
        // Create Stripe checkout session for paid tiers
        Session session = stripeSubscriptionService.createCheckoutSession(
                tierId, customerEmail, successUrl, cancelUrl, playerId);
        return session.getUrl();
    }
    
    /**
     * Cancel a player's subscription.
     * 
     * @param playerId The player ID
     * @return true if cancellation was successful, false otherwise
     */
    public boolean cancelSubscription(String playerId) {
        try {
            Optional<Player> playerOpt = playerDao.getPlayerById(playerId);
            if (playerOpt.isEmpty()) {
                logger.warning("Cannot cancel subscription: Player not found: " + playerId);
                return false;
            }
            
            Player player = playerOpt.get();
            String stripeSubscriptionId = player.getStripeSubscriptionId();
            
            // If player has a Stripe subscription, cancel it
            if (stripeSubscriptionId != null && !stripeSubscriptionId.isEmpty()) {
                try {
                    Subscription cancelledSubscription = stripeSubscriptionService.cancelSubscription(stripeSubscriptionId);
                    if (cancelledSubscription == null) {
                        logger.warning("Failed to cancel Stripe subscription: " + stripeSubscriptionId);
                        // Continue to update player anyway - we don't want to block the operation
                    }
                } catch (StripeException e) {
                    logger.severe("Error cancelling Stripe subscription: " + e.getMessage());
                    // Continue to update player anyway
                }
            }
            
            // Update player to basic tier
            updatePlayerSubscription(playerId, "basic", null);
            
            logger.info("Successfully cancelled subscription for player: " + playerId);
            return true;
            
        } catch (Exception e) {
            logger.severe("Error cancelling subscription for player " + playerId + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get a player's current subscription information.
     * 
     * @param playerId The player ID
     * @return SubscriptionInfo with current subscription details
     */
    public SubscriptionInfo getPlayerSubscription(String playerId) {
        Optional<Player> playerOpt = playerDao.getPlayerById(playerId);
        if (playerOpt.isEmpty()) {
            return new SubscriptionInfo("basic", false, null, false);
        }
        
        Player player = playerOpt.get();
        String currentTierId = player.getCurrentSubscriptionTierId();
        String validUntilStr = player.getSubscriptionValidUntil();
        
        Instant validUntil = null;
        if (validUntilStr != null) {
            try {
                validUntil = Instant.parse(validUntilStr);
            } catch (Exception e) {
                logger.warning("Invalid subscription valid until date: " + validUntilStr);
            }
        }
        
        boolean isActive = isSubscriptionActive(player);
        boolean isExpiring = isSubscriptionExpiring(player);
        
        return new SubscriptionInfo(currentTierId, isActive, validUntil, isExpiring);
    }
    
    /**
     * Check if a player has entitlement to a specific feature.
     * 
     * @param playerId The player ID
     * @param feature The feature to check (e.g., "priority_queue", "advanced_stats")
     * @return true if player has access to the feature
     */
    public boolean checkPlayerEntitlement(String playerId, String feature) {
        Optional<Player> playerOpt = playerDao.getPlayerById(playerId);
        if (playerOpt.isEmpty()) {
            return false;
        }
        
        Player player = playerOpt.get();
        
        // If subscription is not active, only basic features are available
        if (!isSubscriptionActive(player)) {
            return subscriptionTierService.hasFeatureAccess("basic", feature);
        }
        
        String currentTierId = player.getCurrentSubscriptionTierId();
        return subscriptionTierService.hasFeatureAccess(currentTierId, feature);
    }
    
    /**
     * Process Stripe webhook events to update player subscriptions.
     * 
     * @param payload The webhook payload
     * @param sigHeader The webhook signature header
     * @return WebhookResult indicating processing status
     */
    public WebhookResult processWebhook(String payload, String sigHeader) {
        try {
            StripeSubscriptionService.SubscriptionWebhookResult webhookResult = 
                    stripeSubscriptionService.handleWebhook(payload, sigHeader);
            
            if (webhookResult.isError()) {
                return new WebhookResult(false, "Webhook validation failed: " + webhookResult.getStatus());
            }
            
            // Process different event types
            String eventType = webhookResult.getEventType();
            String tierId = webhookResult.getTierId();
            
            switch (eventType) {
                case "checkout.session.completed":
                case "customer.subscription.created":
                case "invoice.payment_succeeded":
                    // Extract customer ID and update player subscription
                    String customerId = (String) webhookResult.getData().get("customer_id");
                    String subscriptionId = (String) webhookResult.getData().get("subscription_id");
                    
                    if (customerId != null && tierId != null) {
                        // In a real implementation, you'd map customer ID to player ID
                        // For now, assume the customer ID metadata contains the player ID
                        String playerId = (String) webhookResult.getData().get("player_id");
                        if (playerId != null) {
                            updatePlayerSubscription(playerId, tierId, subscriptionId);
                        }
                    }
                    break;
                    
                case "customer.subscription.deleted":
                case "invoice.payment_failed":
                    // Handle subscription cancellation or failed payment
                    String failedPlayerId = (String) webhookResult.getData().get("player_id");
                    if (failedPlayerId != null) {
                        updatePlayerSubscription(failedPlayerId, "basic", null);
                    }
                    break;
                    
                default:
                    logger.info("Unhandled webhook event type: " + eventType);
            }
            
            return new WebhookResult(true, "Webhook processed successfully");
            
        } catch (Exception e) {
            logger.severe("Error processing webhook: " + e.getMessage());
            return new WebhookResult(false, "Error processing webhook: " + e.getMessage());
        }
    }
    
    /**
     * Update a player's subscription information.
     * 
     * @param playerId The player ID
     * @param tierId The new subscription tier ID
     * @param stripeSubscriptionId The Stripe subscription ID (null for basic tier)
     */
    private void updatePlayerSubscription(String playerId, String tierId, String stripeSubscriptionId) {
        try {
            Optional<Player> playerOpt = playerDao.getPlayerById(playerId);
            if (playerOpt.isEmpty()) {
                logger.warning("Cannot update subscription: Player not found: " + playerId);
                return;
            }
            
            Player player = playerOpt.get();
            player.setCurrentSubscriptionTierId(tierId);
            player.setStripeSubscriptionId(stripeSubscriptionId);
            
            // Set subscription validity period
            if ("basic".equals(tierId)) {
                player.setSubscriptionValidUntil(null); // Basic is always valid
            } else {
                // Set subscription valid for 30 days from now
                Instant validUntil = Instant.now().plus(30, ChronoUnit.DAYS);
                player.setSubscriptionValidUntil(validUntil.toString());
            }
            
            playerDao.savePlayer(player);
            
            logger.info("Updated subscription for player " + playerId + " to tier " + tierId);
            
        } catch (Exception e) {
            logger.severe("Error updating player subscription: " + e.getMessage());
        }
    }
    
    /**
     * Check if a player's subscription is currently active.
     */
    private boolean isSubscriptionActive(Player player) {
        String tierId = player.getCurrentSubscriptionTierId();
        
        // Basic tier is always active
        if ("basic".equals(tierId)) {
            return true;
        }
        
        // Check if subscription is still valid
        String validUntilStr = player.getSubscriptionValidUntil();
        if (validUntilStr == null) {
            return false;
        }
        
        try {
            Instant validUntil = Instant.parse(validUntilStr);
            return validUntil.isAfter(Instant.now());
        } catch (Exception e) {
            logger.warning("Invalid subscription valid until date: " + validUntilStr);
            return false;
        }
    }
    
    /**
     * Check if a player's subscription is expiring soon (within 7 days).
     */
    private boolean isSubscriptionExpiring(Player player) {
        String tierId = player.getCurrentSubscriptionTierId();
        
        // Basic tier never expires
        if ("basic".equals(tierId)) {
            return false;
        }
        
        String validUntilStr = player.getSubscriptionValidUntil();
        if (validUntilStr == null) {
            return true; // Consider null as expiring
        }
        
        try {
            Instant validUntil = Instant.parse(validUntilStr);
            Instant sevenDaysFromNow = Instant.now().plus(7, ChronoUnit.DAYS);
            return validUntil.isBefore(sevenDaysFromNow);
        } catch (Exception e) {
            logger.warning("Invalid subscription valid until date: " + validUntilStr);
            return true; // Consider invalid dates as expiring
        }
    }
    
    /**
     * Data class for subscription information.
     */
    public static class SubscriptionInfo {
        private final String tierId;
        private final boolean isActive;
        private final Instant validUntil;
        private final boolean isExpiring;
        
        public SubscriptionInfo(String tierId, boolean isActive, Instant validUntil, boolean isExpiring) {
            this.tierId = tierId;
            this.isActive = isActive;
            this.validUntil = validUntil;
            this.isExpiring = isExpiring;
        }
        
        public String getTierId() { return tierId; }
        public boolean isActive() { return isActive; }
        public Instant getValidUntil() { return validUntil; }
        public boolean isExpiring() { return isExpiring; }
        
        @Override
        public String toString() {
            return "SubscriptionInfo{" +
                    "tierId='" + tierId + '\'' +
                    ", isActive=" + isActive +
                    ", validUntil=" + validUntil +
                    ", isExpiring=" + isExpiring +
                    '}';
        }
    }
    
    /**
     * Data class for webhook processing results.
     */
    public static class WebhookResult {
        private final boolean success;
        private final String message;
        
        public WebhookResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        
        @Override
        public String toString() {
            return "WebhookResult{" +
                    "success=" + success +
                    ", message='" + message + '\'' +
                    '}';
        }
    }
} 