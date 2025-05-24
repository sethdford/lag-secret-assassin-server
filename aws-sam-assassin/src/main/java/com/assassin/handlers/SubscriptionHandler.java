package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.DynamoDbSubscriptionTierDao;
import com.assassin.exception.NotFoundException;
import com.assassin.model.Player;
import com.assassin.model.SubscriptionTier;
import com.assassin.service.SubscriptionService;
import com.assassin.service.SubscriptionServiceImpl;
import com.assassin.util.ApiGatewayResponseBuilder;
import com.assassin.util.RequestUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SubscriptionHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionHandler.class);
    private static final Gson GSON = new Gson();
    private final SubscriptionService subscriptionService;

    public SubscriptionHandler() {
        // In a real app, use dependency injection (e.g., Dagger, Spring) or a service locator pattern
        this.subscriptionService = new SubscriptionServiceImpl(
                new DynamoDbPlayerDao(), 
                new DynamoDbSubscriptionTierDao()
        );
    }

    // Constructor for testing with mocks
    public SubscriptionHandler(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String path = request.getPath();
        String httpMethod = request.getHttpMethod();
        LOG.info("SubscriptionHandler received {} request for path: {}", httpMethod, path);

        try {
            // Route: GET /subscriptions/tiers
            if ("GET".equals(httpMethod) && path.equals("/subscriptions/tiers")) {
                return getAvailableTiers(request, context);
            }

            // Route: POST /players/me/subscription
            if ("POST".equals(httpMethod) && path.matches("/players/me/subscription")) { 
                return createSubscription(request, context);
            }

            // Route: GET /players/me/subscription
            if ("GET".equals(httpMethod) && path.matches("/players/me/subscription")) {
                return getPlayerSubscription(request, context);
            }

            // Route: DELETE /players/me/subscription
            if ("DELETE".equals(httpMethod) && path.matches("/players/me/subscription")) {
                return cancelPlayerSubscription(request, context);
            }
            
            return ApiGatewayResponseBuilder.buildErrorResponse(404, "Not Found: The requested subscription resource or action was not found.");

        } catch (NotFoundException e) {
            LOG.warn("Resource not found: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid argument: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(400, e.getMessage());
        } catch (StripeException e) {
            LOG.error("Stripe API error: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(502, "Payment provider error: " + e.getMessage()); // 502 Bad Gateway for upstream errors
        } catch (Exception e) {
            LOG.error("Unexpected error in SubscriptionHandler: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Internal server error processing subscription request.");
        }
    }

    private APIGatewayProxyResponseEvent getAvailableTiers(APIGatewayProxyRequestEvent request, Context context) {
        LOG.info("Handling GET /subscriptions/tiers");
        List<SubscriptionTier> tiers = subscriptionService.getAvailableTiers();
        return ApiGatewayResponseBuilder.buildResponse(200, GSON.toJson(tiers));
    }

    private APIGatewayProxyResponseEvent createSubscription(APIGatewayProxyRequestEvent request, Context context) throws StripeException {
        LOG.info("Handling POST /players/me/subscription");
        String playerId = RequestUtils.getPlayerIdFromRequest(request);
        if (playerId == null) {
            return ApiGatewayResponseBuilder.buildErrorResponse(401, "Unauthorized: Player ID not found in request context.");
        }

        JsonObject body = GSON.fromJson(request.getBody(), JsonObject.class);
        String tierId = body.has("tierId") ? body.get("tierId").getAsString() : null;
        String successUrl = body.has("successUrl") ? body.get("successUrl").getAsString() : null;
        String cancelUrl = body.has("cancelUrl") ? body.get("cancelUrl").getAsString() : null;

        if (tierId == null || successUrl == null || cancelUrl == null) {
            return ApiGatewayResponseBuilder.buildErrorResponse(400, "Missing required fields: tierId, successUrl, cancelUrl.");
        }

        Session checkoutSession = subscriptionService.subscribePlayer(playerId, tierId, successUrl, cancelUrl);
        return ApiGatewayResponseBuilder.buildResponse(200, GSON.toJson(Map.of("checkoutSessionUrl", checkoutSession.getUrl(), "checkoutSessionId", checkoutSession.getId())));
    }

    private APIGatewayProxyResponseEvent getPlayerSubscription(APIGatewayProxyRequestEvent request, Context context) {
        LOG.info("Handling GET /players/me/subscription");
        String playerId = RequestUtils.getPlayerIdFromRequest(request);
        if (playerId == null) {
            return ApiGatewayResponseBuilder.buildErrorResponse(401, "Unauthorized: Player ID not found in request context.");
        }

        Optional<Player> playerOpt = subscriptionService.getPlayerSubscriptionDetails(playerId);
        if (playerOpt.isPresent()) {
            Player player = playerOpt.get();
            // Create a DTO to send back, avoid sending the full Player object if it contains sensitive info not needed here.
            Map<String, Object> responseData = Map.of(
                "playerId", player.getPlayerID(),
                "currentSubscriptionTierId", player.getCurrentSubscriptionTierId() != null ? player.getCurrentSubscriptionTierId() : "N/A",
                "subscriptionValidUntil", player.getSubscriptionValidUntil() != null ? player.getSubscriptionValidUntil() : "N/A",
                "stripeCustomerId", player.getStripeCustomerId() != null ? player.getStripeCustomerId() : "N/A",
                "stripeSubscriptionId", player.getStripeSubscriptionId() != null ? player.getStripeSubscriptionId() : "N/A"
            );
            return ApiGatewayResponseBuilder.buildResponse(200, GSON.toJson(responseData));
        } else {
             return ApiGatewayResponseBuilder.buildErrorResponse(404, "Player or subscription details not found.");
        }
    }

    private APIGatewayProxyResponseEvent cancelPlayerSubscription(APIGatewayProxyRequestEvent request, Context context) throws StripeException {
        LOG.info("Handling DELETE /players/me/subscription");
        String playerId = RequestUtils.getPlayerIdFromRequest(request);
        if (playerId == null) {
            return ApiGatewayResponseBuilder.buildErrorResponse(401, "Unauthorized: Player ID not found in request context.");
        }

        boolean canceled = subscriptionService.cancelSubscription(playerId);
        if (canceled) {
            return ApiGatewayResponseBuilder.buildResponse(200, GSON.toJson(Map.of("message", "Subscription cancellation initiated successfully.")));
        } else {
            // This path might not be reached if service throws exception for failures
            return ApiGatewayResponseBuilder.buildResponse(400, GSON.toJson(Map.of("message", "Failed to initiate subscription cancellation or subscription already inactive.")));
        }
    }
} 