package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.config.StripeClientProvider;
import com.assassin.dao.DynamoDbPlayerDao; // Assuming direct use for now
import com.assassin.dao.PlayerDao;
import com.assassin.dao.SubscriptionTierDao; // Added
import com.assassin.dao.DynamoDbSubscriptionTierDao; // Added
import com.assassin.model.Player;
import com.assassin.model.SubscriptionTier; // Added
import com.assassin.util.ApiGatewayResponseBuilder;
import com.google.gson.JsonSyntaxException;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

public class StripeWebhookHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(StripeWebhookHandler.class);
    private final PlayerDao playerDao;
    private final SubscriptionTierDao subscriptionTierDao; // Added
    private final String stripeWebhookSecret;

    public StripeWebhookHandler() {
        StripeClientProvider.initialize(); // Ensures Stripe API key is set
        this.playerDao = new DynamoDbPlayerDao();
        this.subscriptionTierDao = new DynamoDbSubscriptionTierDao(); // Added
        this.stripeWebhookSecret = System.getenv("STRIPE_WEBHOOK_SECRET");
        if (this.stripeWebhookSecret == null || this.stripeWebhookSecret.trim().isEmpty()) {
            LOG.error("STRIPE_WEBHOOK_SECRET environment variable is not set.");
            // In a real scenario, this might prevent the Lambda from initializing or throw an error.
            // For now, we log and continue, but webhook verification will fail.
        }
    }

    // Constructor for testing
    public StripeWebhookHandler(PlayerDao playerDao, SubscriptionTierDao subscriptionTierDao, String stripeWebhookSecret) {
        StripeClientProvider.initialize();
        this.playerDao = playerDao;
        this.subscriptionTierDao = subscriptionTierDao;
        this.stripeWebhookSecret = stripeWebhookSecret;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        LOG.info("StripeWebhookHandler received request. Headers: {}", request.getHeaders());
        LOG.info("StripeWebhookHandler received body: {}", request.getBody());

        if (stripeWebhookSecret == null) {
            LOG.error("Webhook secret is not configured. Cannot process Stripe event.");
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Webhook processing misconfiguration.");
        }

        Event event;
        try {
            String sigHeader = request.getHeaders().get("Stripe-Signature");
            if (sigHeader == null) {
                 LOG.warn("Stripe-Signature header missing!");
                 return ApiGatewayResponseBuilder.buildErrorResponse(400, "Stripe-Signature header missing.");
            }
            event = Webhook.constructEvent(request.getBody(), sigHeader, stripeWebhookSecret);
        } catch (JsonSyntaxException e) {
            LOG.error("Error parsing Stripe webhook JSON: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(400, "Invalid webhook payload.");
        } catch (SignatureVerificationException e) {
            LOG.error("Stripe signature verification failed: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(400, "Webhook signature verification failed.");
        } catch (Exception e) {
            LOG.error("Unexpected error constructing Stripe event: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Error processing webhook event.");
        }

        LOG.info("Received Stripe event: ID={}, Type={}", event.getId(), event.getType());

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = null;
        if (dataObjectDeserializer.getObject().isPresent()) {
            stripeObject = dataObjectDeserializer.getObject().get();
        } else {
            LOG.warn("Stripe event data object is empty for event ID: {}", event.getId());
            // Depending on the event type, this might be an error or expected.
        }

        try {
            switch (event.getType()) {
                case "checkout.session.completed":
                    if (stripeObject instanceof Session) {
                        Session checkoutSession = (Session) stripeObject;
                        LOG.info("Processing checkout.session.completed for session ID: {}", checkoutSession.getId());
                        handleCheckoutSessionCompleted(checkoutSession);
                    } else {
                        LOG.warn("Expected Session object for checkout.session.completed, but got: {}",
                                 stripeObject != null ? stripeObject.getClass().getName() : "null");
                    }
                    break;
                case "invoice.payment_succeeded":
                    if (stripeObject instanceof Invoice) {
                        Invoice invoice = (Invoice) stripeObject;
                        LOG.info("Processing invoice.payment_succeeded for invoice ID: {}", invoice.getId());
                        handleInvoicePaymentSucceeded(event, stripeObject);
                    } else {
                        LOG.warn("Expected Invoice object for invoice.payment_succeeded, but got: {}",
                                 stripeObject != null ? stripeObject.getClass().getName() : "null");
                    }
                    break;
                // Add cases for other relevant events:
                // case "customer.subscription.created":
                // case "customer.subscription.updated":
                // case "customer.subscription.deleted":
                // case "invoice.payment_failed":
                default:
                    LOG.info("Unhandled Stripe event type: {}", event.getType());
            }
        } catch (Exception e) {
            LOG.error("Error handling Stripe event type {}: {}", event.getType(), e.getMessage(), e);
            // Return 500 to signal Stripe to retry if appropriate, or 200 if we've handled it as best we can.
            // For critical errors, 500 is safer to ensure retry.
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Error processing webhook event: " + event.getType());
        }

        // Return 200 to Stripe to acknowledge receipt
        com.google.gson.Gson gson = new com.google.gson.Gson();
        return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(Map.of("received", true)));
    }

    private void handleCheckoutSessionCompleted(Session session) {
        LOG.info("Handling checkout.session.completed for session ID: {}", session.getId());
        String clientReferenceId = session.getClientReferenceId(); // This should be our PlayerID
        String stripeCustomerId = session.getCustomer();
        String stripeSubscriptionId = session.getSubscription();

        if (clientReferenceId == null || stripeCustomerId == null || stripeSubscriptionId == null) {
            LOG.error("Checkout session {} missing critical information: clientReferenceId, customerId, or subscriptionId.", session.getId());
            return;
        }

        try {
            Optional<Player> playerOpt = playerDao.getPlayerById(clientReferenceId);
            if (playerOpt.isEmpty()) {
                LOG.error("Player not found for clientReferenceId (PlayerID): {} from session ID: {}", clientReferenceId, session.getId());
                return;
            }
            Player player = playerOpt.get();

            // Retrieve the full Subscription object to get details like current_period_end and price_id/tier_id
            Subscription stripeSubscription = Subscription.retrieve(stripeSubscriptionId);
            
            String tierId = null;
            if (stripeSubscription.getItems() != null && !stripeSubscription.getItems().getData().isEmpty()) {
                // Assuming the first item in the subscription is the primary tier
                // And that the tier_id is stored in the Price's metadata
                com.stripe.model.Price price = stripeSubscription.getItems().getData().get(0).getPrice();
                if (price != null && price.getMetadata() != null) {
                    tierId = price.getMetadata().get("tier_id");
                }
                if (tierId == null && price != null) { // Fallback to Price ID if tier_id metadata not found
                    tierId = price.getId(); 
                    LOG.warn("tier_id not found in Price metadata for subscription {}. Using Price ID {} as tierId. Ensure Stripe Price metadata is set.", stripeSubscriptionId, tierId);
                }
            }

            if (tierId == null) {
                LOG.error("Could not determine tierId for subscription {} from session {}", stripeSubscriptionId, session.getId());
                return;
            }
            
            // Validate the tierId from Stripe against our system
            Optional<SubscriptionTier> tierOpt = subscriptionTierDao.getTierById(tierId);
            if (tierOpt.isEmpty()) {
                LOG.error("SubscriptionTier with ID '{}' from Stripe Price metadata/ID not found in our system for subscription {}.", tierId, stripeSubscriptionId);
                // Potentially create a default/fallback tier record or raise an alert
                return;
            }

            player.setCurrentSubscriptionTierId(tierId);
            player.setStripeCustomerId(stripeCustomerId);
            player.setStripeSubscriptionId(stripeSubscriptionId);

            // Convert Stripe's UNIX timestamp (seconds) to ISO 8601 string
            Long currentPeriodEndSeconds = ((com.stripe.model.Subscription) stripeSubscription).getCurrentPeriodEnd();
            if (currentPeriodEndSeconds == null) {
                LOG.error("Subscription {} for session {} has null current_period_end.", stripeSubscriptionId, session.getId());
                // Decide how to handle this case, maybe skip updating validUntil or set a default/error state
                return; // Or throw an exception
            }
            Instant instant = Instant.ofEpochSecond(currentPeriodEndSeconds);
            String validUntil = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            player.setSubscriptionValidUntil(validUntil);

            playerDao.savePlayer(player);
            LOG.info("Player {} updated with Stripe subscription details: Tier={}, SubID={}, CustID={}, ValidUntil={}",
                    player.getPlayerID(), tierId, stripeSubscriptionId, stripeCustomerId, validUntil);

        } catch (StripeException e) {
            LOG.error("Stripe API error processing checkout session {}: PlayerID={}, StripeSubID={}. Error: {}",
                    session.getId(), clientReferenceId, stripeSubscriptionId, e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("Unexpected error processing checkout session {}: PlayerID={}, StripeSubID={}. Error: {}",
                    session.getId(), clientReferenceId, stripeSubscriptionId, e.getMessage(), e);
        }
    }

    private void handleInvoicePaymentSucceeded(Event event, StripeObject stripeObject) {
        if (stripeObject instanceof Invoice) {
            Invoice invoice = (Invoice) stripeObject;
            LOG.info("Handling invoice.payment_succeeded event for Invoice ID: {}", invoice.getId());

            String subscriptionId = ((com.stripe.model.Invoice) invoice).getSubscription(); // Get the subscription ID (String)
            if (subscriptionId == null) {
                LOG.error("Invoice {} does not have an associated subscription ID.", invoice.getId());
                return;
            }

            try {
                Subscription stripeSubscription = Subscription.retrieve(subscriptionId); // Retrieve the Subscription object
                if (stripeSubscription == null) {
                    LOG.error("Could not retrieve subscription {} for invoice {}.", subscriptionId, invoice.getId());
                    return;
                }

                LOG.info("Successfully retrieved subscription {} for invoice {}", stripeSubscription.getId(), invoice.getId());

                // Associate subscription with player's session if customer ID matches
                String customerId = stripeSubscription.getCustomer();
                if (customerId != null) {
                    Optional<Player> playerOpt = playerDao.findPlayerByStripeSubscriptionId(stripeSubscription.getId());
                    if (playerOpt.isPresent()) {
                        Player player = playerOpt.get();

                        Long currentPeriodEndSeconds = ((com.stripe.model.Subscription) stripeSubscription).getCurrentPeriodEnd();
                        if (currentPeriodEndSeconds == null) {
                            LOG.error("Subscription {} for session {} has null current_period_end.", stripeSubscription.getId(), player.getPlayerID());
                            // Decide how to handle this case, maybe skip updating validUntil or set a default/error state
                            return; 
                        }
                        String validUntil = Instant.ofEpochSecond(currentPeriodEndSeconds).toString();
                        player.setSubscriptionValidUntil(validUntil);

                        playerDao.savePlayer(player);
                        LOG.info("Updated player {} with subscription ID {} and valid until {}.",
                                player.getPlayerID(), stripeSubscription.getId(), validUntil);
                    } else {
                        LOG.warn("No player found for Stripe subscription ID: {} (Invoice: {})",
                                stripeSubscription.getId(), invoice.getId());
                    }
                } else {
                    LOG.warn("Subscription {} (Invoice: {}) has no customer ID.", stripeSubscription.getId(), invoice.getId());
                }
            } catch (StripeException e) {
                LOG.error("StripeException while retrieving subscription {} for invoice {}: {}", subscriptionId, invoice.getId(), e.getMessage());
            }
        } else {
            LOG.warn("Received invoice.payment_succeeded event, but StripeObject was not an Invoice. Actual type: {}",
                    stripeObject != null ? stripeObject.getClass().getName() : "null");
        }
    }

    // Placeholder for other event handlers
    // private void handleSubscriptionCreated(Subscription subscription) { ... }
    // private void handleSubscriptionUpdated(Subscription subscription) { ... }
    // private void handleSubscriptionDeleted(Subscription subscription) { ... }
    // private void handleInvoicePaymentFailed(Invoice invoice) { ... }
} 