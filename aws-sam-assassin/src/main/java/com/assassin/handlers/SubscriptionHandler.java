package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.exception.ValidationException;
import com.assassin.model.SubscriptionTier;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.PlayerDao;
import com.assassin.service.SubscriptionService;
import com.assassin.service.SubscriptionTierService;
import com.assassin.service.StripeSubscriptionService;
import com.assassin.util.HandlerUtils;
import com.assassin.util.GsonUtil;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.stripe.exception.StripeException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handler for subscription management operations.
 * Processes API Gateway requests for subscription-related operations.
 */
public class SubscriptionHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionHandler.class);
    private static final Gson gson = GsonUtil.getGson();
    
    private final SubscriptionService subscriptionService;
    private final SubscriptionTierService subscriptionTierService;
    
    /**
     * Default constructor, initializes services.
     */
    public SubscriptionHandler() {
        this.subscriptionTierService = new SubscriptionTierService();
        PlayerDao playerDao = new DynamoDbPlayerDao();
        StripeSubscriptionService stripeService = new StripeSubscriptionService("test_key", "test_webhook_secret");
        this.subscriptionService = new SubscriptionService(subscriptionTierService, stripeService, playerDao);
    }
    
    /**
     * Constructor with dependency injection for testability.
     * 
     * @param subscriptionService The subscription service
     * @param subscriptionTierService The subscription tier service
     */
    public SubscriptionHandler(SubscriptionService subscriptionService, SubscriptionTierService subscriptionTierService) {
        this.subscriptionService = subscriptionService;
        this.subscriptionTierService = subscriptionTierService;
    }

    /**
     * Handles incoming API Gateway requests.
     *
     * @param request the incoming API Gateway request
     * @param context the Lambda context
     * @return API Gateway response with appropriate status code and body
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received subscription request: Method={}, Path={}", request.getHttpMethod(), request.getPath());
        String path = request.getPath();
        String httpMethod = request.getHttpMethod();
        
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(HandlerUtils.getResponseHeaders());
        
        try {
            // Route based on path and HTTP method
            if ("/subscriptions/tiers".equals(path) && "GET".equals(httpMethod)) {
                return getSubscriptionTiers(response);
            } else if ("/players/me/subscription".equals(path) && "GET".equals(httpMethod)) {
                return getMySubscription(request, response);
            } else if ("/players/me/subscription".equals(path) && "POST".equals(httpMethod)) {
                return subscribePlayer(request, response);
            } else if ("/players/me/subscription".equals(path) && "DELETE".equals(httpMethod)) {
                return cancelMySubscription(request, response);
            } else if ("/subscriptions/webhook".equals(path) && "POST".equals(httpMethod)) {
                return handleWebhook(request, response);
            } else {
                return response
                        .withStatusCode(404)
                        .withBody(gson.toJson(Map.of("message", "Route not found")));
            }
        } catch (ValidationException e) {
            logger.warn("Validation error: {}", e.getMessage());
            return response
                    .withStatusCode(400)
                    .withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (Exception e) {
            logger.error("Error processing subscription request: {}", e.getMessage(), e);
            return response
                    .withStatusCode(500)
                    .withBody(gson.toJson(Map.of("message", "Internal Server Error")));
        }
    }
    
    /**
     * Handles GET /subscriptions/tiers request to retrieve all available subscription tiers.
     *
     * @param response the API Gateway response object
     * @return the completed API Gateway response
     */
    private APIGatewayProxyResponseEvent getSubscriptionTiers(APIGatewayProxyResponseEvent response) {
        logger.info("Getting all subscription tiers");
        List<SubscriptionTier> tiers = subscriptionTierService.getAllTiers();
        
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(tiers));
    }
    
    /**
     * Handles GET /players/me/subscription request to retrieve current player's subscription.
     *
     * @param request the API Gateway request
     * @param response the API Gateway response object
     * @return the completed API Gateway response
     */
    private APIGatewayProxyResponseEvent getMySubscription(APIGatewayProxyRequestEvent request, 
                                                         APIGatewayProxyResponseEvent response) {
        String playerId = HandlerUtils.getPlayerIdFromRequest(request)
                .orElseThrow(() -> new ValidationException("Player ID not found in request context."));
        logger.info("Getting subscription for player ID: {}", playerId);
        
        SubscriptionService.SubscriptionInfo subscription = subscriptionService.getPlayerSubscription(playerId);
        
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(subscription));
    }
    
    /**
     * Handles POST /players/me/subscription request to subscribe current player to a tier.
     *
     * @param request the API Gateway request
     * @param response the API Gateway response object
     * @return the completed API Gateway response
     */
    private APIGatewayProxyResponseEvent subscribePlayer(APIGatewayProxyRequestEvent request, 
                                                       APIGatewayProxyResponseEvent response) throws StripeException {
        String playerId = HandlerUtils.getPlayerIdFromRequest(request)
                .orElseThrow(() -> new ValidationException("Player ID not found in request context."));
        logger.info("Processing subscription request for player ID: {}", playerId);
        
        // Parse request body
        SubscriptionRequest subscriptionRequest = gson.fromJson(request.getBody(), SubscriptionRequest.class);
        if (subscriptionRequest == null) {
            throw new ValidationException("Request body is required");
        }
        
        if (subscriptionRequest.tierId == null || subscriptionRequest.tierId.trim().isEmpty()) {
            throw new ValidationException("tierId is required");
        }
        
        // Validate tier exists
        if (!subscriptionTierService.getTierById(subscriptionRequest.tierId).isPresent()) {
            throw new ValidationException("Invalid subscription tier: " + subscriptionRequest.tierId);
        }
        
        // For free tier, handle directly
        if ("basic".equals(subscriptionRequest.tierId)) {
            String result = subscriptionService.subscribePlayer(
                    playerId, 
                    subscriptionRequest.tierId, 
                    "", // No email needed for free tier
                    "", // No success URL needed
                    ""  // No cancel URL needed
            );
            
            return response
                    .withStatusCode(200)
                    .withBody(gson.toJson(Map.of("message", "Successfully subscribed to basic tier")));
        }
        
        // For paid tiers, create Stripe checkout session
        String checkoutUrl = subscriptionService.subscribePlayer(
                playerId, 
                subscriptionRequest.tierId, 
                subscriptionRequest.customerEmail != null ? subscriptionRequest.customerEmail : "",
                subscriptionRequest.successUrl != null ? subscriptionRequest.successUrl : "/subscription/success",
                subscriptionRequest.cancelUrl != null ? subscriptionRequest.cancelUrl : "/subscription/cancel"
        );
        
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(Map.of("checkoutUrl", checkoutUrl)));
    }
    
    /**
     * Handles DELETE /players/me/subscription request to cancel current player's subscription.
     *
     * @param request the API Gateway request
     * @param response the API Gateway response object
     * @return the completed API Gateway response
     */
    private APIGatewayProxyResponseEvent cancelMySubscription(APIGatewayProxyRequestEvent request, 
                                                            APIGatewayProxyResponseEvent response) {
        String playerId = HandlerUtils.getPlayerIdFromRequest(request)
                .orElseThrow(() -> new ValidationException("Player ID not found in request context."));
        logger.info("Cancelling subscription for player ID: {}", playerId);
        
        boolean success = subscriptionService.cancelSubscription(playerId);
        
        if (success) {
            return response
                    .withStatusCode(200)
                    .withBody(gson.toJson(Map.of("message", "Subscription cancelled successfully")));
        } else {
            return response
                    .withStatusCode(400)
                    .withBody(gson.toJson(Map.of("message", "No active subscription to cancel")));
        }
    }
    
    /**
     * Handles POST /subscriptions/webhook request for Stripe webhook events.
     *
     * @param request the API Gateway request
     * @param response the API Gateway response object
     * @return the completed API Gateway response
     */
    private APIGatewayProxyResponseEvent handleWebhook(APIGatewayProxyRequestEvent request, 
                                                     APIGatewayProxyResponseEvent response) {
        String signature = request.getHeaders() != null ? request.getHeaders().get("Stripe-Signature") : null;
        if (signature == null || signature.trim().isEmpty()) {
            throw new ValidationException("Missing Stripe-Signature header");
        }
        
        String payload = request.getBody();
        logger.info("Processing Stripe webhook");
        
        SubscriptionService.WebhookResult result = subscriptionService.processWebhook(payload, signature);
        
        if (result.isSuccess()) {
            return response
                    .withStatusCode(200)
                    .withBody(gson.toJson(Map.of("message", "Webhook processed successfully")));
        } else {
            return response
                    .withStatusCode(400)
                    .withBody(gson.toJson(Map.of("message", result.getMessage())));
        }
    }
    
    /**
     * Request object for subscription creation.
     */
    public static class SubscriptionRequest {
        public String tierId;
        public String customerEmail;
        public String successUrl;
        public String cancelUrl;
    }
} 