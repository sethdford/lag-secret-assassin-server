package com.assassin.service;

import com.assassin.model.SubscriptionTier;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.param.*;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.net.Webhook;

import java.util.*;
import java.util.logging.Logger;

/**
 * Service for handling Stripe integration for subscription management.
 * Manages Stripe products, prices, checkout sessions, and webhook events.
 */
public class StripeSubscriptionService {
    
    private static final Logger logger = Logger.getLogger(StripeSubscriptionService.class.getName());
    
    private final String stripeSecretKey;
    private final String stripeWebhookSecret;
    private final SubscriptionTierService subscriptionTierService;
    
    /**
     * Constructor for StripeSubscriptionService.
     */
    public StripeSubscriptionService(String stripeSecretKey, String stripeWebhookSecret) {
        this.stripeSecretKey = stripeSecretKey;
        this.stripeWebhookSecret = stripeWebhookSecret;
        this.subscriptionTierService = new SubscriptionTierService();
        
        // Initialize Stripe API
        Stripe.apiKey = stripeSecretKey;
    }
    
    /**
     * Create or update Stripe products for all subscription tiers.
     * This is typically called once during setup or when tiers change.
     * 
     * @return Map of tier ID to Stripe Product ID
     * @throws StripeException if Stripe API call fails
     */
    public Map<String, String> setupStripeProducts() throws StripeException {
        Map<String, String> tierToProductMap = new HashMap<>();
        
        List<SubscriptionTier> purchasableTiers = subscriptionTierService.getPurchasableTiers();
        
        for (SubscriptionTier tier : purchasableTiers) {
            Product product = createOrUpdateProduct(tier);
            Price price = createOrUpdatePrice(product.getId(), tier);
            
            tierToProductMap.put(tier.getTierId(), product.getId());
            
            logger.info(String.format("Setup Stripe product for tier %s: productId=%s, priceId=%s", 
                    tier.getTierId(), product.getId(), price.getId()));
        }
        
        return tierToProductMap;
    }
    
    /**
     * Create a Stripe checkout session for subscription purchase.
     * 
     * @param tierId The subscription tier to purchase
     * @param customerEmail Customer's email address
     * @param successUrl URL to redirect on successful payment
     * @param cancelUrl URL to redirect on cancelled payment
     * @param customerId Optional existing Stripe customer ID
     * @return Stripe checkout session
     * @throws StripeException if Stripe API call fails
     */
    public Session createCheckoutSession(String tierId, String customerEmail, 
                                       String successUrl, String cancelUrl, 
                                       String customerId) throws StripeException {
        
        Optional<SubscriptionTier> tierOpt = subscriptionTierService.getTierById(tierId);
        if (tierOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid subscription tier: " + tierId);
        }
        
        SubscriptionTier tier = tierOpt.get();
        
        // Find or create the Stripe price for this tier
        String priceId = findPriceIdForTier(tier);
        
        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
                .putMetadata("tier_id", tierId)
                .putMetadata("customer_email", customerEmail);
        
        // Set customer if provided
        if (customerId != null && !customerId.trim().isEmpty()) {
            paramsBuilder.setCustomer(customerId);
        } else {
            paramsBuilder.setCustomerEmail(customerEmail);
        }
        
        SessionCreateParams params = paramsBuilder.build();
        
        Session session = Session.create(params);
        
        logger.info(String.format("Created checkout session for tier %s: sessionId=%s", 
                tierId, session.getId()));
        
        return session;
    }
    
    /**
     * Handle Stripe webhook events for subscription management.
     * 
     * @param payload The webhook payload
     * @param sigHeader The Stripe signature header
     * @return SubscriptionWebhookResult containing event details
     * @throws StripeException if webhook verification fails
     */
    public SubscriptionWebhookResult handleWebhook(String payload, String sigHeader) throws StripeException {
        Event event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
        
        logger.info(String.format("Processing Stripe webhook event: %s", event.getType()));
        
        return switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutSessionCompleted(event);
            case "customer.subscription.created" -> handleSubscriptionCreated(event);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "invoice.payment_succeeded" -> handleInvoicePaymentSucceeded(event);
            case "invoice.payment_failed" -> handleInvoicePaymentFailed(event);
            default -> {
                logger.info(String.format("Unhandled webhook event type: %s", event.getType()));
                yield new SubscriptionWebhookResult(event.getType(), "unhandled", null, null);
            }
        };
    }
    
    /**
     * Cancel a subscription in Stripe.
     * 
     * @param subscriptionId The Stripe subscription ID
     * @return The cancelled subscription
     * @throws StripeException if Stripe API call fails
     */
    public Subscription cancelSubscription(String subscriptionId) throws StripeException {
        Subscription subscription = Subscription.retrieve(subscriptionId);
        
        SubscriptionCancelParams params = SubscriptionCancelParams.builder()
                .setInvoiceNow(true)
                .setProrate(true)
                .build();
        
        Subscription cancelledSubscription = subscription.cancel(params);
        
        logger.info(String.format("Cancelled subscription: %s", subscriptionId));
        
        return cancelledSubscription;
    }
    
    /**
     * Get subscription details from Stripe.
     * 
     * @param subscriptionId The Stripe subscription ID
     * @return Subscription details
     * @throws StripeException if Stripe API call fails
     */
    public Subscription getSubscription(String subscriptionId) throws StripeException {
        return Subscription.retrieve(subscriptionId);
    }
    
    // Private helper methods
    
    private Product createOrUpdateProduct(SubscriptionTier tier) throws StripeException {
        // Try to find existing product first - we'll search by name for simplicity
        ProductListParams listParams = ProductListParams.builder()
                .setActive(true)
                .build();
        
        ProductCollection products = Product.list(listParams);
        
        // Look for existing product with matching name
        for (Product product : products.getData()) {
            if (product.getName().contains(tier.getDisplayName())) {
                return product; // Return existing product
            }
        }
        
        // Create new product
        ProductCreateParams params = ProductCreateParams.builder()
                .setName(String.format("Assassin Game - %s Tier", tier.getDisplayName()))
                .setDescription(String.format("%s subscription tier with enhanced features", tier.getDisplayName()))
                .putMetadata("tier_id", tier.getTierId())
                .setType(ProductCreateParams.Type.SERVICE)
                .build();
        
        return Product.create(params);
    }
    
    private Price createOrUpdatePrice(String productId, SubscriptionTier tier) throws StripeException {
        // Try to find existing price first
        PriceListParams listParams = PriceListParams.builder()
                .setProduct(productId)
                .setActive(true)
                .build();
        
        PriceCollection prices = Price.list(listParams);
        
        if (!prices.getData().isEmpty()) {
            return prices.getData().get(0); // Return existing price
        }
        
        // Create new price
        PriceCreateParams params = PriceCreateParams.builder()
                .setProduct(productId)
                .setUnitAmount(tier.getMonthlyPriceCents())
                .setCurrency("usd")
                .setRecurring(PriceCreateParams.Recurring.builder()
                        .setInterval(PriceCreateParams.Recurring.Interval.MONTH)
                        .build())
                .putMetadata("tier_id", tier.getTierId())
                .build();
        
        return Price.create(params);
    }
    
    private String findPriceIdForTier(SubscriptionTier tier) throws StripeException {
        // For simplicity, we'll find by searching all active prices
        PriceListParams params = PriceListParams.builder()
                .setActive(true)
                .build();
        
        PriceCollection prices = Price.list(params);
        
        // Look for price with matching tier metadata
        for (Price price : prices.getData()) {
            if (price.getMetadata() != null && 
                tier.getTierId().equals(price.getMetadata().get("tier_id"))) {
                return price.getId();
            }
        }
        
        throw new IllegalStateException("No Stripe price found for tier: " + tier.getTierId());
    }
    
    private SubscriptionWebhookResult handleCheckoutSessionCompleted(Event event) {
        Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
        if (session != null) {
            String tierId = session.getMetadata().get("tier_id");
            String customerEmail = session.getMetadata().get("customer_email");
            
            return new SubscriptionWebhookResult(
                    event.getType(), 
                    "success", 
                    tierId, 
                    Map.of(
                            "customer_email", customerEmail,
                            "session_id", session.getId(),
                            "subscription_id", session.getSubscription()
                    )
            );
        }
        
        return new SubscriptionWebhookResult(event.getType(), "error", null, null);
    }
    
    private SubscriptionWebhookResult handleSubscriptionCreated(Event event) {
        Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (subscription != null) {
            String tierId = extractTierIdFromSubscription(subscription);
            
            return new SubscriptionWebhookResult(
                    event.getType(), 
                    "success", 
                    tierId, 
                    Map.of(
                            "subscription_id", subscription.getId(),
                            "customer_id", subscription.getCustomer(),
                            "status", subscription.getStatus()
                    )
            );
        }
        
        return new SubscriptionWebhookResult(event.getType(), "error", null, null);
    }
    
    private SubscriptionWebhookResult handleSubscriptionUpdated(Event event) {
        Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (subscription != null) {
            String tierId = extractTierIdFromSubscription(subscription);
            
            return new SubscriptionWebhookResult(
                    event.getType(), 
                    "success", 
                    tierId, 
                    Map.of(
                            "subscription_id", subscription.getId(),
                            "customer_id", subscription.getCustomer(),
                            "status", subscription.getStatus()
                    )
            );
        }
        
        return new SubscriptionWebhookResult(event.getType(), "error", null, null);
    }
    
    private SubscriptionWebhookResult handleSubscriptionDeleted(Event event) {
        Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (subscription != null) {
            return new SubscriptionWebhookResult(
                    event.getType(), 
                    "success", 
                    "basic", // Default back to basic tier
                    Map.of(
                            "subscription_id", subscription.getId(),
                            "customer_id", subscription.getCustomer()
                    )
            );
        }
        
        return new SubscriptionWebhookResult(event.getType(), "error", null, null);
    }
    
    private SubscriptionWebhookResult handleInvoicePaymentSucceeded(Event event) {
        // Simplified invoice handling - just return basic info
        return new SubscriptionWebhookResult(
                event.getType(), 
                "success", 
                null, 
                Map.of("event_id", event.getId())
        );
    }
    
    private SubscriptionWebhookResult handleInvoicePaymentFailed(Event event) {
        // Simplified invoice handling - just return basic info
        return new SubscriptionWebhookResult(
                event.getType(), 
                "failed", 
                null, 
                Map.of("event_id", event.getId())
        );
    }
    
    private String extractTierIdFromSubscription(Subscription subscription) {
        // Try to get tier ID from subscription metadata first
        if (subscription.getMetadata() != null && subscription.getMetadata().containsKey("tier_id")) {
            return subscription.getMetadata().get("tier_id");
        }
        
        // Try to get from price metadata
        if (!subscription.getItems().getData().isEmpty()) {
            SubscriptionItem item = subscription.getItems().getData().get(0);
            Price price = item.getPrice();
            if (price.getMetadata() != null && price.getMetadata().containsKey("tier_id")) {
                return price.getMetadata().get("tier_id");
            }
        }
        
        return "basic"; // Default fallback
    }
    
    /**
     * Result class for webhook processing.
     */
    public static class SubscriptionWebhookResult {
        private final String eventType;
        private final String status;
        private final String tierId;
        private final Map<String, Object> data;
        
        public SubscriptionWebhookResult(String eventType, String status, String tierId, Map<String, Object> data) {
            this.eventType = eventType;
            this.status = status;
            this.tierId = tierId;
            this.data = data != null ? data : new HashMap<>();
        }
        
        public String getEventType() { return eventType; }
        public String getStatus() { return status; }
        public String getTierId() { return tierId; }
        public Map<String, Object> getData() { return data; }
        
        public boolean isSuccess() { return "success".equals(status); }
        public boolean isError() { return "error".equals(status); }
        public boolean isFailed() { return "failed".equals(status); }
    }
} 