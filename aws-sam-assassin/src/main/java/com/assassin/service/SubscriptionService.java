package com.assassin.service;

import com.assassin.model.Player;
import com.assassin.model.SubscriptionTier;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session; // For subscribePlayer response
import com.stripe.model.Invoice; // Added import
import com.stripe.model.Subscription; // Added import

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing player subscriptions and tiers.
 */
public interface SubscriptionService {

    /**
     * Retrieves all available and active subscription tiers, ordered by display order.
     *
     * @return A list of active SubscriptionTiers.
     */
    List<SubscriptionTier> getAvailableTiers();

    /**
     * Retrieves a specific subscription tier by its ID.
     *
     * @param tierId The ID of the tier.
     * @return An Optional containing the SubscriptionTier if found, otherwise empty.
     */
    Optional<SubscriptionTier> getTierById(String tierId);

    /**
     * Creates a Stripe Checkout Session for a player to subscribe to a specific tier.
     *
     * @param playerId The ID of the player subscribing.
     * @param tierId The ID of the subscription tier.
     * @param successUrl The URL to redirect to on successful payment.
     * @param cancelUrl The URL to redirect to on canceled payment.
     * @return The Stripe Checkout Session.
     * @throws StripeException if there's an error interacting with Stripe.
     * @throws IllegalArgumentException if player or tier is not found or invalid.
     */
    Session subscribePlayer(String playerId, String tierId, String successUrl, String cancelUrl) throws StripeException;

    /**
     * Cancels a player's active Stripe subscription.
     * This will typically set the subscription to cancel at the end of the current billing period.
     *
     * @param playerId The ID of the player whose subscription is to be canceled.
     * @return true if cancellation was successfully initiated with Stripe, false otherwise.
     * @throws StripeException if there's an error interacting with Stripe.
     * @throws IllegalArgumentException if player is not found or has no active subscription.
     */
    boolean cancelSubscription(String playerId) throws StripeException;

    /**
     * Gets the current subscription details for a player.
     *
     * @param playerId The ID of the player.
     * @return An Optional containing the Player's active SubscriptionTier if they have one, otherwise empty.
     *         This might also return a DTO with more details like validity, status from Stripe etc.
     *         For now, returning the tier directly linked to the player.
     */
    Optional<SubscriptionTier> getPlayerSubscriptionTier(String playerId);
    
    /**
     * Gets the Player object which includes subscription details.
     * @param playerId The ID of the player.
     * @return Optional of Player.
     */
    Optional<Player> getPlayerSubscriptionDetails(String playerId);


    /**
     * Checks if a player is entitled to a specific benefit based on their current subscription.
     * (This is a placeholder, specific benefit checks might be more granular).
     *
     * @param playerId The ID of the player.
     * @param benefitKey A key representing the benefit to check (e.g., "PRIORITY_ACCESS", "MONTHLY_CURRENCY_BONUS").
     * @return true if the player is entitled, false otherwise.
     */
    boolean checkPlayerEntitlement(String playerId, String benefitKey);

    // Methods to be called by StripeWebhookHandler to update player state
    // These could also be part of a more specific internal service if SubscriptionService grows too large.

    /**
     * Updates a player's subscription details based on a completed Stripe Checkout Session.
     * Called by the StripeWebhookHandler.
     *
     * @param checkoutSession The completed Stripe Checkout Session object.
     */
    void processCheckoutSessionCompleted(Session checkoutSession) throws StripeException;

    /**
     * Updates a player's subscription details based on a successful Stripe invoice payment (renewal).
     * Called by the StripeWebhookHandler.
     *
     * @param invoice The Stripe Invoice object for a payment_succeeded event.
     */
    void processInvoicePaymentSucceeded(Invoice invoice) throws StripeException;

    /**
     * Handles updates to a player's subscription status from Stripe (e.g., updated, deleted, trial_will_end).
     * Called by the StripeWebhookHandler.
     *
     * @param subscription The Stripe Subscription object.
     * @param eventType The type of the Stripe event (e.g., "customer.subscription.updated", "customer.subscription.deleted").
     */
    void processSubscriptionUpdate(Subscription subscription, String eventType) throws StripeException;
} 