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
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Player;
import com.assassin.util.ApiGatewayResponseBuilder;
import com.assassin.util.GsonUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
        logger.info("Privacy request: {} {}", request.getHttpMethod(), request.getPath());

        try {
            String method = request.getHttpMethod();
            String path = request.getPath();

            if ("GET".equals(method) && path.contains("/privacy/settings")) {
                return getPrivacySettings(request);
            } else if ("PUT".equals(method) && path.contains("/privacy/settings")) {
                return updatePrivacySettings(request);
            } else if ("POST".equals(method) && path.contains("/privacy/location-sharing")) {
                return toggleLocationSharing(request);
            } else if ("GET".equals(method) && path.contains("/privacy/data-export")) {
                return exportPlayerData(request);
            } else if ("DELETE".equals(method) && path.contains("/privacy/data")) {
                return deletePlayerData(request);
            } else {
                return ApiGatewayResponseBuilder.buildErrorResponse(404, "Endpoint not found");
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
    private APIGatewayProxyResponseEvent getPrivacySettings(APIGatewayProxyRequestEvent request) {
        String playerId = extractPlayerIdFromCognito(request);
        
        Optional<Player> playerOpt = playerDao.getPlayerById(playerId);
        if (!playerOpt.isPresent()) {
            throw new PlayerNotFoundException("Player not found: " + playerId);
        }

        Player player = playerOpt.get();
        
        Map<String, Object> privacySettings = new HashMap<>();
        privacySettings.put("locationSharingEnabled", player.getLocationSharingEnabled());
        privacySettings.put("locationVisibility", player.getLocationVisibility());
        privacySettings.put("proximityAlertsEnabled", player.getProximityAlertsEnabled());
        privacySettings.put("trackingHistoryEnabled", player.getTrackingHistoryEnabled());
        
        logger.info("Retrieved privacy settings for player {}", playerId);
        return ApiGatewayResponseBuilder.buildResponse(200, GsonUtil.toJson(privacySettings));
    }

    /**
     * Update privacy settings for the authenticated player.
     */
    private APIGatewayProxyResponseEvent updatePrivacySettings(APIGatewayProxyRequestEvent request) {
        String playerId = extractPlayerIdFromCognito(request);
        String requestBody = request.getBody();

        if (requestBody == null || requestBody.trim().isEmpty()) {
            throw new ValidationException("Request body is required");
        }

        Optional<Player> playerOpt = playerDao.getPlayerById(playerId);
        if (!playerOpt.isPresent()) {
            throw new PlayerNotFoundException("Player not found: " + playerId);
        }

        Player player = playerOpt.get();

        try {
            JsonNode json = JsonUtil.parseJson(requestBody);
            
            // Update location sharing settings
            if (json.has("locationSharingEnabled")) {
                player.setLocationSharingEnabled(json.get("locationSharingEnabled").asBoolean());
            }
            
            if (json.has("locationVisibility")) {
                String visibility = json.get("locationVisibility").asText();
                validateLocationVisibility(visibility);
                player.setLocationVisibility(visibility);
            }
            
            if (json.has("proximityAlertsEnabled")) {
                player.setProximityAlertsEnabled(json.get("proximityAlertsEnabled").asBoolean());
            }
            
            if (json.has("trackingHistoryEnabled")) {
                player.setTrackingHistoryEnabled(json.get("trackingHistoryEnabled").asBoolean());
            }

            // Save updated player
            playerDao.savePlayer(player);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Privacy settings updated successfully");
            response.put("settings", Map.of(
                "locationSharingEnabled", player.getLocationSharingEnabled(),
                "locationVisibility", player.getLocationVisibility(),
                "proximityAlertsEnabled", player.getProximityAlertsEnabled(),
                "trackingHistoryEnabled", player.getTrackingHistoryEnabled()
            ));

            logger.info("Updated privacy settings for player {}", playerId);
            return ApiGatewayResponseBuilder.build(200, response);

        } catch (Exception e) {
            logger.error("Error updating privacy settings for player {}", playerId, e);
            throw new ValidationException("Invalid request format: " + e.getMessage());
        }
    }

    /**
     * Toggle location sharing on/off for the authenticated player.
     */
    private APIGatewayProxyResponseEvent toggleLocationSharing(APIGatewayProxyRequestEvent request) {
        String playerId = extractPlayerIdFromCognito(request);
        
        Optional<Player> playerOpt = playerDao.getPlayerById(playerId);
        if (!playerOpt.isPresent()) {
            throw new PlayerNotFoundException("Player not found: " + playerId);
        }

        Player player = playerOpt.get();
        boolean newSharingState = !player.getLocationSharingEnabled();
        player.setLocationSharingEnabled(newSharingState);
        
        playerDao.savePlayer(player);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Location sharing " + (newSharingState ? "enabled" : "disabled"));
        response.put("locationSharingEnabled", newSharingState);

        logger.info("Toggled location sharing for player {} to {}", playerId, newSharingState);
        return ApiGatewayResponseBuilder.build(200, response);
    }

    /**
     * Export player data for GDPR compliance.
     */
    private APIGatewayProxyResponseEvent exportPlayerData(APIGatewayProxyRequestEvent request) {
        String playerId = extractPlayerIdFromCognito(request);
        
        Optional<Player> playerOpt = playerDao.getPlayerById(playerId);
        if (!playerOpt.isPresent()) {
            throw new PlayerNotFoundException("Player not found: " + playerId);
        }

        Player player = playerOpt.get();
        
        // Create a sanitized export of player data
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("playerId", player.getPlayerID());
        exportData.put("playerName", player.getPlayerName());
        exportData.put("email", player.getEmail());
        exportData.put("killCount", player.getKillCount());
        exportData.put("status", player.getStatus());
        exportData.put("locationSharingEnabled", player.getLocationSharingEnabled());
        exportData.put("locationVisibility", player.getLocationVisibility());
        exportData.put("proximityAlertsEnabled", player.getProximityAlertsEnabled());
        exportData.put("trackingHistoryEnabled", player.getTrackingHistoryEnabled());
        exportData.put("subscriptionTier", player.getCurrentSubscriptionTierId());
        exportData.put("exportTimestamp", java.time.Instant.now().toString());

        logger.info("Exported data for player {}", playerId);
        return ApiGatewayResponseBuilder.build(200, exportData);
    }

    /**
     * Delete player data for GDPR compliance (right to be forgotten).
     */
    private APIGatewayProxyResponseEvent deletePlayerData(APIGatewayProxyRequestEvent request) {
        String playerId = extractPlayerIdFromCognito(request);
        
        // Note: This is a simplified implementation. In a real system, you would need to:
        // 1. Check if player is in an active game (prevent deletion)
        // 2. Anonymize historical data instead of deleting
        // 3. Remove from all related tables (kills, games, etc.)
        // 4. Send confirmation email
        
        Optional<Player> playerOpt = playerDao.getPlayerById(playerId);
        if (!playerOpt.isPresent()) {
            throw new PlayerNotFoundException("Player not found: " + playerId);
        }

        Player player = playerOpt.get();
        
        // Check if player is in an active game
        if ("ACTIVE".equals(player.getStatus()) || "PENDING".equals(player.getStatus())) {
            throw new ValidationException("Cannot delete data while player is in an active game");
        }

        // For now, just mark as deleted rather than actually deleting
        player.setActive(false);
        player.setEmail("deleted@example.com");
        player.setPlayerName("Deleted User");
        player.setLocationSharingEnabled(false);
        playerDao.savePlayer(player);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Player data deletion initiated. Account has been deactivated.");
        response.put("deletionTimestamp", java.time.Instant.now().toString());

        logger.info("Initiated data deletion for player {}", playerId);
        return ApiGatewayResponseBuilder.build(200, response);
    }

    /**
     * Validate location visibility setting.
     */
    private void validateLocationVisibility(String visibility) {
        if (visibility == null || (!visibility.equals("GAME_ONLY") && 
                                  !visibility.equals("TEAM_ONLY") && 
                                  !visibility.equals("FRIENDS_ONLY") && 
                                  !visibility.equals("PRIVATE"))) {
            throw new ValidationException("Invalid location visibility. Must be one of: GAME_ONLY, TEAM_ONLY, FRIENDS_ONLY, PRIVATE");
        }
    }

    /**
     * Extract player ID from Cognito claims in the request.
     */
    private String extractPlayerIdFromCognito(APIGatewayProxyRequestEvent request) {
        Map<String, Object> requestContext = request.getRequestContext();
        if (requestContext != null && requestContext.containsKey("authorizer")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> authorizer = (Map<String, Object>) requestContext.get("authorizer");
            if (authorizer != null && authorizer.containsKey("claims")) {
                @SuppressWarnings("unchecked")
                Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
                if (claims != null && claims.containsKey("sub")) {
                    return claims.get("sub");
                }
            }
        }
        throw new ValidationException("Unable to extract player ID from request");
    }
} 