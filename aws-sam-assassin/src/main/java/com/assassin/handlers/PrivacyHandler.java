package com.assassin.handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Player;
import com.assassin.util.ApiGatewayResponseBuilder;
import com.assassin.util.GsonUtil;

/**
 * Handles privacy-related API requests for managing player location sharing and privacy settings.
 */
public class PrivacyHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(PrivacyHandler.class);
    private final DynamoDbPlayerDao playerDao;

    public PrivacyHandler() {
        this.playerDao = new DynamoDbPlayerDao();
    }

    // Constructor for testing
    public PrivacyHandler(DynamoDbPlayerDao playerDao) {
        this.playerDao = playerDao;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            logger.info("Processing privacy request: {} {}", 
                request.getHttpMethod(), request.getPath());

            String httpMethod = request.getHttpMethod();
            String path = request.getPath();
            Map<String, String> pathParameters = request.getPathParameters();

            if ("GET".equals(httpMethod) && path.contains("/players/") && path.endsWith("/privacy")) {
                return getPrivacySettings(request, context);
            } else if ("PUT".equals(httpMethod) && path.contains("/players/") && path.endsWith("/privacy")) {
                return updatePrivacySettings(request, context);
            } else if ("POST".equals(httpMethod) && path.contains("/players/") && path.endsWith("/privacy/location-sharing")) {
                return updateLocationSharing(request, context);
            } else if ("POST".equals(httpMethod) && path.contains("/players/") && path.endsWith("/privacy/visibility")) {
                return updateLocationVisibility(request, context);
            } else {
                return ApiGatewayResponseBuilder.buildErrorResponse(404, "Privacy endpoint not found");
            }

        } catch (PlayerNotFoundException e) {
            logger.warn("Player not found: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(404, e.getMessage());
        } catch (ValidationException e) {
            logger.warn("Validation error: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(400, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in privacy handler", e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Internal server error");
        }
    }

    /**
     * Get current privacy settings for the authenticated player.
     */
    private APIGatewayProxyResponseEvent getPrivacySettings(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String playerId = request.getPathParameters().get("playerId");
            if (playerId == null) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Player ID is required");
            }

            // Verify player access
            String requestingPlayerId = extractPlayerIdFromContext(request.getRequestContext());
            if (!playerId.equals(requestingPlayerId)) {
                return ApiGatewayResponseBuilder.buildErrorResponse(403, "Can only access your own privacy settings");
            }

            // Get player
            var playerOpt = playerDao.getPlayerById(playerId);
            if (!playerOpt.isPresent()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(404, "Player not found");
            }

            Player player = playerOpt.get();
            
            // Build privacy settings response
            Map<String, Object> privacySettings = new HashMap<>();
            privacySettings.put("playerId", playerId);
            privacySettings.put("locationSharingEnabled", player.getLocationSharingEnabled());
            privacySettings.put("locationVisibility", player.getLocationVisibility());
            privacySettings.put("proximityAlertsEnabled", player.getProximityAlertsEnabled());
            privacySettings.put("trackingHistoryEnabled", player.getTrackingHistoryEnabled());

            Map<String, Object> response = new HashMap<>();
            response.put("privacySettings", privacySettings);
            response.put("lastUpdated", System.currentTimeMillis());

            return ApiGatewayResponseBuilder.buildResponse(200, GsonUtil.getGson().toJson(response));

        } catch (Exception e) {
            logger.error("Error getting privacy settings", e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Error retrieving privacy settings");
        }
    }

    /**
     * Update privacy settings for the authenticated player.
     */
    private APIGatewayProxyResponseEvent updatePrivacySettings(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String playerId = request.getPathParameters().get("playerId");
            if (playerId == null) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Player ID is required");
            }

            // Verify player access
            String requestingPlayerId = extractPlayerIdFromContext(request.getRequestContext());
            if (!playerId.equals(requestingPlayerId)) {
                return ApiGatewayResponseBuilder.buildErrorResponse(403, "Can only update your own privacy settings");
            }

            // Parse request body
            if (request.getBody() == null || request.getBody().trim().isEmpty()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Request body is required");
            }
            
            JsonObject requestBody = JsonParser.parseString(request.getBody()).getAsJsonObject();

            // Get player
            var playerOpt = playerDao.getPlayerById(playerId);
            if (!playerOpt.isPresent()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(404, "Player not found");
            }

            Player player = playerOpt.get();
            
            // Update privacy settings from request
            if (requestBody.has("locationSharingEnabled")) {
                player.setLocationSharingEnabled(requestBody.get("locationSharingEnabled").getAsBoolean());
            }
            if (requestBody.has("locationVisibility")) {
                player.setLocationVisibility(requestBody.get("locationVisibility").getAsString());
            }
            if (requestBody.has("proximityAlertsEnabled")) {
                player.setProximityAlertsEnabled(requestBody.get("proximityAlertsEnabled").getAsBoolean());
            }
            if (requestBody.has("trackingHistoryEnabled")) {
                player.setTrackingHistoryEnabled(requestBody.get("trackingHistoryEnabled").getAsBoolean());
            }

            // Save updated player
            playerDao.savePlayer(player);

            // Build response
            Map<String, Object> privacySettings = new HashMap<>();
            privacySettings.put("playerId", playerId);
            privacySettings.put("locationSharingEnabled", player.getLocationSharingEnabled());
            privacySettings.put("locationVisibility", player.getLocationVisibility());
            privacySettings.put("proximityAlertsEnabled", player.getProximityAlertsEnabled());
            privacySettings.put("trackingHistoryEnabled", player.getTrackingHistoryEnabled());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Privacy settings updated successfully");
            response.put("privacySettings", privacySettings);
            response.put("updatedAt", System.currentTimeMillis());

            return ApiGatewayResponseBuilder.buildResponse(200, GsonUtil.getGson().toJson(response));

        } catch (Exception e) {
            logger.error("Error updating privacy settings", e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Error updating privacy settings");
        }
    }

    /**
     * Toggle location sharing on/off for the authenticated player.
     */
    private APIGatewayProxyResponseEvent updateLocationSharing(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String playerId = request.getPathParameters().get("playerId");
            if (playerId == null) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Player ID is required");
            }

            // Verify player access
            String requestingPlayerId = extractPlayerIdFromContext(request.getRequestContext());
            if (!playerId.equals(requestingPlayerId)) {
                return ApiGatewayResponseBuilder.buildErrorResponse(403, "Can only update your own location sharing settings");
            }

            // Parse request body
            if (request.getBody() == null || request.getBody().trim().isEmpty()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Request body is required");
            }
            
            JsonObject requestBody = JsonParser.parseString(request.getBody()).getAsJsonObject();
            if (!requestBody.has("enabled")) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "enabled field is required");
            }

            boolean enabled = requestBody.get("enabled").getAsBoolean();

            // Get player
            var playerOpt = playerDao.getPlayerById(playerId);
            if (!playerOpt.isPresent()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(404, "Player not found");
            }

            Player player = playerOpt.get();
            player.setLocationSharingEnabled(enabled);
            
            // Save updated player
            playerDao.savePlayer(player);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Location sharing updated successfully");
            response.put("playerId", playerId);
            response.put("locationSharingEnabled", enabled);
            response.put("updatedAt", System.currentTimeMillis());

            return ApiGatewayResponseBuilder.buildResponse(200, GsonUtil.getGson().toJson(response));

        } catch (Exception e) {
            logger.error("Error updating location sharing", e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Error updating location sharing");
        }
    }

    private APIGatewayProxyResponseEvent updateLocationVisibility(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String playerId = request.getPathParameters().get("playerId");
            if (playerId == null) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Player ID is required");
            }

            // Verify player access
            String requestingPlayerId = extractPlayerIdFromContext(request.getRequestContext());
            if (!playerId.equals(requestingPlayerId)) {
                return ApiGatewayResponseBuilder.buildErrorResponse(403, "Can only update your own location visibility settings");
            }

            // Parse request body
            if (request.getBody() == null || request.getBody().trim().isEmpty()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Request body is required");
            }
            
            JsonObject requestBody = JsonParser.parseString(request.getBody()).getAsJsonObject();
            if (!requestBody.has("visibility")) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "visibility field is required");
            }

            String visibility = requestBody.get("visibility").getAsString();
            
            // Validate visibility level
            if (!isValidVisibilityLevel(visibility)) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, 
                    "Invalid visibility level. Must be one of: GAME_ONLY, TEAM_ONLY, FRIENDS_ONLY, PRIVATE");
            }

            // Get player
            var playerOpt = playerDao.getPlayerById(playerId);
            if (!playerOpt.isPresent()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(404, "Player not found");
            }

            Player player = playerOpt.get();
            player.setLocationVisibility(visibility);
            
            // Save updated player
            playerDao.savePlayer(player);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Location visibility updated successfully");
            response.put("playerId", playerId);
            response.put("locationVisibility", visibility);
            response.put("updatedAt", System.currentTimeMillis());

            return ApiGatewayResponseBuilder.buildResponse(200, GsonUtil.getGson().toJson(response));

        } catch (Exception e) {
            logger.error("Error updating location visibility", e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Error updating location visibility");
        }
    }

    private boolean isValidVisibilityLevel(String visibility) {
        return "GAME_ONLY".equals(visibility) || 
               "TEAM_ONLY".equals(visibility) || 
               "FRIENDS_ONLY".equals(visibility) || 
               "PRIVATE".equals(visibility);
    }



    private String extractPlayerIdFromContext(APIGatewayProxyRequestEvent.ProxyRequestContext requestContext) {
        try {
            // Extract player ID from Cognito claims
            Map<String, Object> claims = requestContext.getAuthorizer();
            if (claims != null && claims.containsKey("claims")) {
                @SuppressWarnings("unchecked")
                Map<String, String> cognitoClaims = (Map<String, String>) claims.get("claims");
                return cognitoClaims.get("sub"); // Cognito user ID
            }
            return null;
        } catch (Exception e) {
            logger.warn("Could not extract player ID from request context", e);
            return null;
        }
    }
} 