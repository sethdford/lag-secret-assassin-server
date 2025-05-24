package com.assassin.service;

import com.assassin.config.StripeClientProvider;
import com.assassin.dao.PlayerDao;
import com.assassin.dao.SubscriptionTierDao;
import com.assassin.exception.NotFoundException;
import com.assassin.exception.SubscriptionException;
import com.assassin.model.Player;
import com.assassin.model.SubscriptionTier;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.Price;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SubscriptionServiceImpl implements SubscriptionService {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionServiceImpl.class);
    private final PlayerDao playerDao;
    private final SubscriptionTierDao subscriptionTierDao;
    // private final String stripeApiKey; // Handled by StripeClientProvider

    public SubscriptionServiceImpl(PlayerDao playerDao, SubscriptionTierDao subscriptionTierDao) {
        this.playerDao = playerDao;
        this.subscriptionTierDao = subscriptionTierDao;
        // Ensure Stripe SDK is initialized (idempotent call)
        StripeClientProvider.initialize(); 
    }

    @Override
    public List<SubscriptionTier> getAvailableTiers() {
        return subscriptionTierDao.getAllActiveTiersOrdered();
    }

    @Override
    public Optional<SubscriptionTier> getTierById(String tierId) {
        return subscriptionTierDao.getTierById(tierId);
    }

    @Override
    public Session subscribePlayer(String playerId, String tierId, String successUrl, String cancelUrl) throws StripeException {
        LOG.info("Attempting to subscribe player {} to tier {}", playerId, tierId);
        Player player = playerDao.getPlayerById(playerId)
                .orElseThrow(() -> new NotFoundException("Player not found with ID: " + playerId));

        SubscriptionTier tier = subscriptionTierDao.getTierById(tierId)
                .orElseThrow(() -> new NotFoundException("Subscription tier not found with ID: " + tierId));

        if (!tier.getIsActive()) {
            throw new IllegalArgumentException("Subscription tier " + tierId + " is not active.");
        }

        // Retrieve or create Stripe Customer
        String stripeCustomerId = player.getStripeCustomerId();
        if (stripeCustomerId == null || stripeCustomerId.trim().isEmpty()) {
            CustomerCreateParams customerParams = CustomerCreateParams.builder()
                    .setEmail(player.getEmail())
                    .setName(player.getPlayerName()) // Assuming Player model has getName()
                    .putMetadata("assassin_player_id", player.getPlayerID())
                    .build();
            Customer customer = Customer.create(customerParams);
            stripeCustomerId = customer.getId();
            player.setStripeCustomerId(stripeCustomerId);
            playerDao.savePlayer(player); // Save updated player with Stripe Customer ID
            LOG.info("Created Stripe Customer {} for player {}", stripeCustomerId, playerId);
        }

        // Use tier.getTierId() as the Stripe Price ID.
        // This assumes Stripe Price IDs are set to match our internal tier IDs.
        String stripePriceId = tier.getTierId(); 
        if (stripePriceId == null || stripePriceId.isEmpty()) {
             LOG.error("Stripe Price ID (derived from tierId) is not configured for tierId: {}. Cannot create Stripe Checkout session.", tierId);
            throw new SubscriptionException("Stripe Price ID missing for tier: " + tierId);
        }

        SessionCreateParams.LineItem.PriceData priceData = SessionCreateParams.LineItem.PriceData.builder()
            .setCurrency(tier.getCurrency().toLowerCase()) // Assuming tier has currency, e.g., "USD"
            .setUnitAmount(tier.getMonthlyPriceCents())    // Assuming tier has monthlyPriceCents
            .setProductData(
                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                    .setName(tier.getName())
                    .setDescription(tier.getDescription())
                    // .putMetadata("tier_id", tier.getTierId()) // Metadata on product if needed
                    .build()
            )
            // If using a fixed Stripe Price ID instead of dynamic price_data:
            // .setPrice(stripePriceId) // This would be used if PriceData is not built dynamically.
            .setRecurring(SessionCreateParams.LineItem.PriceData.Recurring.builder()
                .setInterval(SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH) // Assuming monthly
                .build())
            .build();

        // If using fixed Price ID, the LineItem creation is simpler:
        // SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
        // .setPrice(stripePriceId)
        // .setQuantity(1L)
        // .build();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setCustomer(stripeCustomerId) 
                .addLineItem(SessionCreateParams.LineItem.builder()
                    .setPrice(stripePriceId) // Use the Stripe Price ID associated with the tier
                    .setQuantity(1L)
                    .build())
                .putMetadata("assassin_player_id", playerId) // For webhook reconciliation
                .putMetadata("assassin_tier_id", tierId)    // For webhook reconciliation
                .setClientReferenceId(playerId) // Useful for linking back in webhooks if metadata isn't used/preferred
                .build();

        Session session = Session.create(params);
        LOG.info("Created Stripe Checkout Session {} for player {} to subscribe to tier {}", session.getId(), playerId, tierId);
        return session;
    }

    @Override
    public boolean cancelSubscription(String playerId) throws StripeException {
        LOG.info("Attempting to cancel subscription for player {}", playerId);
        Player player = playerDao.getPlayerById(playerId)
                .orElseThrow(() -> new NotFoundException("Player not found with ID: " + playerId));

        String stripeSubscriptionId = player.getStripeSubscriptionId();
        if (stripeSubscriptionId == null || stripeSubscriptionId.trim().isEmpty()) {
            LOG.warn("Player {} has no Stripe subscription ID. Cannot cancel.", playerId);
            throw new IllegalArgumentException("Player does not have an active subscription to cancel.");
        }

        try {
            Subscription subscription = Subscription.retrieve(stripeSubscriptionId);
            if ("canceled".equals(subscription.getStatus()) || subscription.getCancelAtPeriodEnd()) {
                LOG.info("Subscription {} for player {} is already canceled or set to cancel at period end.", stripeSubscriptionId, playerId);
                return true; // Already canceled or pending cancellation
            }

            SubscriptionCancelParams params = SubscriptionCancelParams.builder().build(); // Default cancels at period end
            // To cancel immediately: SubscriptionCancelParams.builder().setInvoiceNow(true).setProrate(true).build();
            Subscription canceledSubscription = subscription.cancel(params);

            LOG.info("Successfully initiated cancellation for Stripe subscription {} (Player {}). New status: {}. Will cancel at: {}", 
                canceledSubscription.getId(), playerId, canceledSubscription.getStatus(), 
                Instant.ofEpochSecond(canceledSubscription.getCancelAt()).toString());
            
            // Update player record immediately or wait for webhook?
            // For now, let webhook handle final state update for consistency.
            // player.setSubscriptionStatus("CANCELED_AT_PERIOD_END");
            // player.setSubscriptionValidUntil(Instant.ofEpochSecond(canceledSubscription.getCancelAt()).toString());
            // playerDao.savePlayer(player);

            return true;
        } catch (StripeException e) {
            LOG.error("Stripe API error canceling subscription {} for player {}: {}", stripeSubscriptionId, playerId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Optional<SubscriptionTier> getPlayerSubscriptionTier(String playerId) {
        return playerDao.getPlayerById(playerId)
                .flatMap(player -> {
                    if (player.getCurrentSubscriptionTierId() != null && player.getSubscriptionValidUntil() != null) {
                        // Optional: Add check here if subscriptionValidUntil is in the past
                        // Instant validUntil = OffsetDateTime.parse(player.getSubscriptionValidUntil()).toInstant();
                        // if (validUntil.isAfter(Instant.now())) { ... }
                        return subscriptionTierDao.getTierById(player.getCurrentSubscriptionTierId());
                    }
                    return Optional.empty();
                });
    }
    
    @Override
    public Optional<Player> getPlayerSubscriptionDetails(String playerId) {
        return playerDao.getPlayerById(playerId);
    }

    @Override
    public boolean checkPlayerEntitlement(String playerId, String benefitKey) {
        // Basic example: Actual entitlement logic might involve checking SubscriptionBenefits
        Optional<SubscriptionTier> tierOpt = getPlayerSubscriptionTier(playerId);
        if (tierOpt.isPresent()) {
            SubscriptionTier tier = tierOpt.get();
            // Example: Check if tier name matches, or if a specific benefit is in tier.getBenefits()
            if ("PRIORITY_ACCESS".equalsIgnoreCase(benefitKey)) {
                // Assuming Elite tier has priority access, or a more direct check on benefits object
                return "Elite".equalsIgnoreCase(tier.getName()) || (tier.getBenefits() != null && tier.getBenefits().getPriorityAccess());
            }
            // Add more benefit checks here...
            LOG.warn("Benefit key '{}' not recognized for player {}", benefitKey, playerId);
        }
        return false;
    }

    // --- Webhook Processing Methods ---
    // These methods are designed to be called by StripeWebhookHandler

    @Override
    public void processCheckoutSessionCompleted(Session checkoutSession) throws StripeException {
        LOG.info("SubscriptionService processing checkout.session.completed for session ID: {}", checkoutSession.getId());
        String clientReferenceId = checkoutSession.getClientReferenceId(); // This should be our Player ID
        String stripeCustomerId = checkoutSession.getCustomer();
        String stripeSubscriptionId = checkoutSession.getSubscription();

        if (clientReferenceId == null || stripeCustomerId == null || stripeSubscriptionId == null) {
            LOG.error("Webhook: Checkout session {} missing critical info (PlayerID, CustomerID, or SubscriptionID).", checkoutSession.getId());
            throw new SubscriptionException("Webhook data incomplete for checkout session: " + checkoutSession.getId());
        }

        Player player = playerDao.getPlayerById(clientReferenceId)
                .orElseThrow(() -> {
                    LOG.error("Webhook: Player not found for clientReferenceId (PlayerID): {} from session ID: {}", clientReferenceId, checkoutSession.getId());
                    return new NotFoundException("Webhook: Player not found: " + clientReferenceId);
                });

        Subscription stripeSubscription = Subscription.retrieve(stripeSubscriptionId);
        String tierId = extractTierIdFromStripeSubscription(stripeSubscription);
        if (tierId == null) {
            LOG.error("Webhook: Could not determine tierId for subscription {} from session {}. Player update aborted.", stripeSubscriptionId, checkoutSession.getId());
             throw new SubscriptionException("Webhook: Could not determine tierId for subscription: " + stripeSubscriptionId);
        }
        
        // Validate tierId from Stripe against our system
        subscriptionTierDao.getTierById(tierId)
            .orElseThrow(() -> {
                LOG.error("Webhook: SubscriptionTier with ID '{}' from Stripe not found in our system for subscription {}.", tierId, stripeSubscriptionId);
                return new NotFoundException("Webhook: Invalid tier ID from Stripe: " + tierId);
            });

        player.setCurrentSubscriptionTierId(tierId);
        player.setStripeCustomerId(stripeCustomerId); // Ensure it's set/updated
        player.setStripeSubscriptionId(stripeSubscriptionId);
        player.setSubscriptionValidUntil(formatEpochSecond(stripeSubscription.getCurrentPeriodEnd()));
        
        playerDao.savePlayer(player);
        LOG.info("Webhook: Player {} updated via checkout session {}. Tier: {}, StripeSub: {}, ValidUntil: {}", 
                 player.getPlayerID(), checkoutSession.getId(), tierId, stripeSubscriptionId, player.getSubscriptionValidUntil());
    }

    @Override
    public void processInvoicePaymentSucceeded(Invoice invoice) throws StripeException {
        LOG.info("SubscriptionService processing invoice.payment_succeeded for invoice ID: {}, Subscription ID: {}", 
                 invoice.getId(), invoice.getSubscription());
        String stripeSubscriptionId = invoice.getSubscription();

        if (stripeSubscriptionId == null) {
            LOG.warn("Webhook: Invoice {} payment succeeded, but no subscription ID associated. Likely a one-time payment, skipping player update.", invoice.getId());
            return;
        }

        Player player = playerDao.findPlayerByStripeSubscriptionId(stripeSubscriptionId)
                .orElseThrow(() -> {
                    LOG.error("Webhook: Player not found for Stripe Subscription ID: {} from invoice ID: {}.", stripeSubscriptionId, invoice.getId());
                    return new NotFoundException("Webhook: Player not found by Stripe Sub ID: " + stripeSubscriptionId);
                });

        Subscription stripeSubscription = Subscription.retrieve(stripeSubscriptionId);
        String tierId = extractTierIdFromStripeSubscription(stripeSubscription);
        if (tierId == null) { // Should ideally not happen if subscription exists
            LOG.warn("Webhook: Could not determine tierId for subscription {} during invoice processing. Retaining existing tier for player {}.", stripeSubscriptionId, player.getPlayerID());
        } else {
             // Validate tierId from Stripe against our system
            subscriptionTierDao.getTierById(tierId)
                .orElseThrow(() -> {
                    LOG.error("Webhook: SubscriptionTier with ID '{}' from Stripe not found in our system for subscription {} during invoice processing.", tierId, stripeSubscriptionId);
                    return new NotFoundException("Webhook: Invalid tier ID from Stripe: " + tierId);
                });
            player.setCurrentSubscriptionTierId(tierId); // Update tier in case of upgrade/downgrade reflected in invoice
        }
        
        player.setSubscriptionValidUntil(formatEpochSecond(stripeSubscription.getCurrentPeriodEnd()));
        playerDao.savePlayer(player);
        LOG.info("Webhook: Player {} subscription renewal processed via invoice {}. New ValidUntil: {}. Current Tier: {}", 
                 player.getPlayerID(), invoice.getId(), player.getSubscriptionValidUntil(), player.getCurrentSubscriptionTierId());
    }

    @Override
    public void processSubscriptionUpdate(Subscription subscription, String eventType) throws StripeException {
        LOG.info("SubscriptionService processing {} for subscription: {}", eventType, subscription.getId());
        String stripeSubscriptionId = subscription.getId();

        Player player = playerDao.findPlayerByStripeSubscriptionId(stripeSubscriptionId)
                .orElseThrow(() -> {
                    LOG.error("Webhook: Player not found for Stripe Subscription ID: {} during {} event.", stripeSubscriptionId, eventType);
                    return new NotFoundException("Webhook: Player not found for Stripe Sub ID during update: " + stripeSubscriptionId);
                });

        if ("customer.subscription.deleted".equals(eventType) || "canceled".equals(subscription.getStatus())) {
            LOG.info("Webhook: Subscription {} for player {} was canceled/deleted. Clearing local subscription fields.", stripeSubscriptionId, player.getPlayerID());
            player.setCurrentSubscriptionTierId(null);
            // player.setStripeSubscriptionId(null); // Consider if Stripe Sub ID should be cleared or kept for history
            player.setSubscriptionValidUntil(null); 
        } else {
            // For "customer.subscription.updated", "customer.subscription.trial_will_end", etc.
            String newTierId = extractTierIdFromStripeSubscription(subscription);
            if (newTierId != null) {
                subscriptionTierDao.getTierById(newTierId) // Validate tier
                    .orElseThrow(() -> {
                        LOG.error("Webhook: SubscriptionTier with ID '{}' from Stripe not found in system during {} event for sub {}.", 
                                  newTierId, eventType, stripeSubscriptionId);
                        return new NotFoundException("Webhook: Invalid tier ID from Stripe during update: " + newTierId);
                    });
                player.setCurrentSubscriptionTierId(newTierId);
            } else {
                 LOG.warn("Webhook: Could not determine tierId for subscription {} during {} event. Player's tier ID not updated.", stripeSubscriptionId, eventType);
            }

            if (subscription.getCurrentPeriodEnd() != null) {
                 player.setSubscriptionValidUntil(formatEpochSecond(subscription.getCurrentPeriodEnd()));
            } else if (subscription.getEndedAt() != null) { // If subscription truly ended
                 player.setSubscriptionValidUntil(formatEpochSecond(subscription.getEndedAt()));
            } else if ("canceled".equals(subscription.getStatus()) && subscription.getCancelAt() != null) {
                 player.setSubscriptionValidUntil(formatEpochSecond(subscription.getCancelAt()));
            } else if (!"active".equals(subscription.getStatus()) && !"trialing".equals(subscription.getStatus())){
                 LOG.warn("Subscription {} has status {} and null currentPeriodEnd/endedAt. ValidUntil not updated.", stripeSubscriptionId, subscription.getStatus());
            }
            // If currentPeriodEnd is null, but status is active/trialing, it's odd. Log and perhaps don't update validUntil.
        }
        
        playerDao.savePlayer(player);
        LOG.info("Webhook: Player {} subscription details updated via event {}. Tier: {}, ValidUntil: {}. Stripe Status: {}", 
                 player.getPlayerID(), eventType, player.getCurrentSubscriptionTierId(), player.getSubscriptionValidUntil(), subscription.getStatus());
    }
    
    // --- Helper Methods ---

    private String extractTierIdFromStripeSubscription(Subscription stripeSubscription) {
        String tierId = null;
        if (stripeSubscription.getItems() != null && !stripeSubscription.getItems().getData().isEmpty()) {
            com.stripe.model.SubscriptionItem firstItem = stripeSubscription.getItems().getData().get(0);
            if (firstItem.getPrice() != null) {
                Price price = firstItem.getPrice();
                if (price.getMetadata() != null && price.getMetadata().containsKey("tier_id")) {
                    tierId = price.getMetadata().get("tier_id");
                } else {
                     // Fallback: Use the Stripe Price ID itself as our tier_id if no metadata
                    tierId = price.getId();
                    LOG.warn("tier_id not found in Price metadata for Stripe Price ID: {}. Using Price ID itself as tier_id. Ensure Stripe Price metadata is set if this is not intended.", price.getId());
                }
            }
        }
        if (tierId == null) {
             LOG.error("Could not determine tierId from Stripe subscription object ID: {}. Items or Price data might be missing or misconfigured.", stripeSubscription.getId());
        }
        return tierId;
    }

    private String formatEpochSecond(Long epochSecond) {
        if (epochSecond == null) return null;
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC)
                           .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
} 