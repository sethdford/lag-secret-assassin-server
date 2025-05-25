package com.assassin.handlers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Player;
import com.assassin.service.PlayerService;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

/**
 * Handler for player management operations.
 * Processes API Gateway requests for player CRUD operations.
 */
public class PlayerHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(PlayerHandler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final PlayerDao playerDao;
    private final PlayerService playerService;
    
    /**
     * Default constructor, initializes DAO and Service.
     */
    public PlayerHandler() {
        this.playerDao = new DynamoDbPlayerDao();
        this.playerService = new PlayerService(this.playerDao);
    }
    
    /**
     * Constructor with dependency injection for testability.
     * 
     * @param playerDao The DAO for player operations
     * @param playerService The Service for player operations
     */
    public PlayerHandler(PlayerDao playerDao, PlayerService playerService) {
        this.playerDao = playerDao;
        this.playerService = playerService;
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
        logger.info("Received player request: Method={}, Path={}", request.getHttpMethod(), request.getPath());
        String path = request.getPath();
        String httpMethod = request.getHttpMethod();
        
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(HandlerUtils.getResponseHeaders());
        
        try {
            // Route based on path and HTTP method
            if ("/players".equals(path) && "GET".equals(httpMethod)) {
                return getAllPlayers(response);
            } else if ("/players".equals(path) && "POST".equals(httpMethod)) {
                return createPlayer(request, response);
            } else if (path.matches("/players/me/target") && "GET".equals(httpMethod)) {
                return getMyTarget(request, response);
            } else if (path.matches("/players/me/settings/location-visibility") && "GET".equals(httpMethod)) {
                return handleGetLocationVisibilitySettings(request, response);
            } else if (path.matches("/players/me/settings/location-visibility") && "PUT".equals(httpMethod)) {
                return handleUpdateLocationVisibilitySettings(request, response);
            } else if (path.matches("/players/me/location/pause") && "POST".equals(httpMethod)) {
                return handlePauseLocationSharing(request, response);
            } else if (path.matches("/players/me/location/resume") && "POST".equals(httpMethod)) {
                return handleResumeLocationSharing(request, response);
            } else if (path.matches("/players/me/settings/location-precision") && "PUT".equals(httpMethod)) {
                return handleUpdateLocationPrecisionSettings(request, response);
            } else if (path.matches("/players/me") && "GET".equals(httpMethod)) {
                return getMe(request, response);
            } else if (path.matches("/players/[^/]+") && "GET".equals(httpMethod)) {
                String playerId = getResourceIdFromPath(path);
                return getPlayer(playerId, response);
            } else if (path.matches("/players/[^/]+") && "PUT".equals(httpMethod)) {
                String playerId = getResourceIdFromPath(path);
                return updatePlayer(playerId, request, response);
            } else if (path.matches("/players/[^/]+") && "DELETE".equals(httpMethod)) {
                String playerId = getResourceIdFromPath(path);
                return deletePlayer(playerId, response);
            } else if (("PUT".equals(httpMethod) || "POST".equals(httpMethod)) && path.matches("/players/([\\w-]+)/status")) {
                // Admin endpoint, not implemented yet
                return response.withStatusCode(501).withBody(gson.toJson(Map.of("message", "Admin player status update not implemented")));
            } else if ("GET".equals(httpMethod) && "/players/me/settings/location-visibility".equals(path)) {
                return handleGetLocationVisibilitySettings(request, response);
            } else if ("PUT".equals(httpMethod) && "/players/me/settings/location-visibility".equals(path)) {
                return handleUpdateLocationVisibilitySettings(request, response);
            } else if ("POST".equals(httpMethod) && "/players/me/location/pause".equals(path)) {
                return handlePauseLocationSharing(request, response);
            } else if ("POST".equals(httpMethod) && "/players/me/location/resume".equals(path)) {
                return handleResumeLocationSharing(request, response);
            } else if ("PUT".equals(httpMethod) && "/players/me/settings/location-precision".equals(path)) {
                return handleUpdateLocationPrecisionSettings(request, response);
            } else if ("GET".equals(httpMethod) && "/players/me/settings/location-precision".equals(path)) {
                return handleGetLocationPrecisionSettings(request, response);            
            } else {
                logger.warn("Route not found in PlayerHandler: {} {}", httpMethod, path);
                return response
                        .withStatusCode(404)
                        .withBody(gson.toJson(Map.of("message", "Route not found")));
            }
        } catch (PlayerNotFoundException e) {
            logger.warn("Player operation failed: {}", e.getMessage());
            return response
                    .withStatusCode(404)
                    .withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (ValidationException | JsonSyntaxException | IllegalArgumentException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return response
                    .withStatusCode(400)
                    .withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (Exception e) {
            logger.error("Error processing player request: {}", e.getMessage(), e);
            return response
                    .withStatusCode(500)
                    .withBody(gson.toJson(Map.of("message", "Internal Server Error")));
        }
    }
    
    /**
     * Handles GET /players request to retrieve all players.
     *
     * @param response the API Gateway response object
     * @return the completed API Gateway response
     */
    private APIGatewayProxyResponseEvent getAllPlayers(APIGatewayProxyResponseEvent response) {
        logger.info("Getting all players");
        List<Player> players = playerDao.getAllPlayers(); // Now calls the implemented DAO method
        
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(players));
    }
    
    /**
     * Handles POST /players request to create a new player.
     *
     * @param request the API Gateway request
     * @param response the API Gateway response object
     * @return the completed API Gateway response
     */
    private APIGatewayProxyResponseEvent createPlayer(APIGatewayProxyRequestEvent request, 
                                                     APIGatewayProxyResponseEvent response) {
        // Consider adding input validation (e.g., required fields) here or in a service layer
        Player player = gson.fromJson(request.getBody(), Player.class);
        logger.info("Creating player: {}", player.getPlayerID());
        // In a real app, you might generate the ID server-side instead of trusting the client
        playerDao.savePlayer(player);
        
        return response
                .withStatusCode(201)
                .withBody(gson.toJson(player));
    }
    
    /**
     * Handles GET /players/{playerId} request to retrieve a specific player.
     *
     * @param playerId the ID of the player to retrieve
     * @param response the API Gateway response object
     * @return the completed API Gateway response
     */
    private APIGatewayProxyResponseEvent getPlayer(String playerId, 
                                                  APIGatewayProxyResponseEvent response) {
        logger.info("Getting player by ID: {}", playerId);
        Optional<Player> playerOpt = playerDao.getPlayerById(playerId);
        if (playerOpt.isPresent()) {
            return response
                    .withStatusCode(200)
                    .withBody(gson.toJson(playerOpt.get()));
        } else {
            logger.warn("Player not found: {}", playerId);
            return response
                    .withStatusCode(404)
                    .withBody(gson.toJson(Map.of("message", "Player not found")));
        }
    }
    
    /**
     * Handles PUT /players/{playerId} request to update a player.
     *
     * @param playerId the ID of the player to update
     * @param request the API Gateway request
     * @param response the API Gateway response object
     * @return the completed API Gateway response
     */
    private APIGatewayProxyResponseEvent updatePlayer(String playerId, 
                                                     APIGatewayProxyRequestEvent request,
                                                     APIGatewayProxyResponseEvent response) {
         logger.info("Updating player by ID: {}", playerId);
       // First, check if the player exists (optional, savePlayer is effectively an upsert)
        Optional<Player> existingPlayerOpt = playerDao.getPlayerById(playerId);
        if (existingPlayerOpt.isEmpty()) {
            logger.warn("Attempted to update non-existent player: {}", playerId);
            return response
                    .withStatusCode(404)
                    .withBody(gson.toJson(Map.of("message", "Player not found")));
        }
        
        // Consider adding validation for the updated player data
        Player updatedPlayer = gson.fromJson(request.getBody(), Player.class);
        updatedPlayer.setPlayerID(playerId); // Ensure ID matches path parameter, ignore ID from body if present
        
        playerDao.savePlayer(updatedPlayer);
        logger.info("Successfully updated player: {}", playerId);
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(updatedPlayer));
    }
    
    /**
     * Handles DELETE /players/{playerId} request to delete a player.
     *
     * @param playerId the ID of the player to delete
     * @param response the API Gateway response object
     * @return the completed API Gateway response
     */
    private APIGatewayProxyResponseEvent deletePlayer(String playerId, APIGatewayProxyResponseEvent response) throws PlayerNotFoundException {
        logger.info("Deleting player by ID: {}", playerId);
        playerDao.deletePlayer(playerId); // Will throw PlayerNotFoundException if not found
        logger.info("Successfully deleted player: {}", playerId);
        return response.withStatusCode(204); // No content on successful deletion
    }
    
    /**
     * Handles GET /players/me request to retrieve the authenticated player's profile.
     */
    private APIGatewayProxyResponseEvent getMe(APIGatewayProxyRequestEvent request,
                                               APIGatewayProxyResponseEvent response) {
        try {
            String playerId = HandlerUtils.getPlayerIdFromRequest(request)
                    .orElseThrow(() -> new ValidationException("Player ID not found in request context."));
            logger.info("Getting profile for authenticated player ID: {}", playerId);
            return getPlayer(playerId, response); // Reuse the existing getPlayer logic
        } catch (Exception e) {
            logger.error("Error getting authenticated player profile: {}", e.getMessage(), e);
            return response
                    .withStatusCode(500)
                    .withBody(gson.toJson(Map.of("message", "Internal Server Error")));
        }
    }

    /**
     * Handles GET /players/me/target to retrieve the authenticated player's target.
     */
    private APIGatewayProxyResponseEvent getMyTarget(APIGatewayProxyRequestEvent request,
                                                   APIGatewayProxyResponseEvent response) {
        try {
            String playerId = HandlerUtils.getPlayerIdFromRequest(request)
                    .orElseThrow(() -> new ValidationException("Player ID not found in request context."));
            logger.info("Getting target for player ID: {}", playerId);

            Optional<Player> playerOpt = playerDao.getPlayerById(playerId);
            if (playerOpt.isPresent()) {
                Player player = playerOpt.get();
                // Create a simple map or a dedicated DTO for the response
                Map<String, String> targetInfo = Map.of(
                    "targetId", player.getTargetID() != null ? player.getTargetID() : "N/A",
                    "targetName", player.getTargetName() != null ? player.getTargetName() : "N/A" 
                );
                return response
                        .withStatusCode(200)
                        .withBody(gson.toJson(targetInfo));
            } else {
                logger.warn("Authenticated player not found in DB: {}", playerId);
                return response
                        .withStatusCode(404)
                        .withBody(gson.toJson(Map.of("message", "Player not found")));
            }
        } catch (ValidationException e) {
             logger.warn("Validation error getting player target: {}", e.getMessage());
            return response
                    .withStatusCode(400)
                    .withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (Exception e) {
            logger.error("Error getting player target: {}", e.getMessage(), e);
            return response
                    .withStatusCode(500)
                    .withBody(gson.toJson(Map.of("message", "Internal Server Error")));
        }
    }
    
    /**
     * Extracts resource ID from a path like /resource/{id}
     */
    private String getResourceIdFromPath(String path) {
        String[] parts = path.split("/");
        return parts[parts.length - 1];
    }

    /**
     * Handles GET /players/me/settings/location-visibility
     */
    private APIGatewayProxyResponseEvent handleGetLocationVisibilitySettings(APIGatewayProxyRequestEvent request, 
                                                                             APIGatewayProxyResponseEvent response) {
        String playerId = HandlerUtils.getPlayerIdFromRequest(request)
                .orElseThrow(() -> new ValidationException("Player ID not found in request context."));
        
        logger.info("Getting location visibility settings for player ID: {}", playerId);
        Optional<Player.LocationVisibility> visibilityOpt = playerService.getLocationVisibilitySettings(playerId);

        if (visibilityOpt.isPresent()) {
            return response
                    .withStatusCode(200)
                    .withBody(gson.toJson(Map.of("locationVisibility", visibilityOpt.get().name())));
        } else {
            // This case implies player was found, but settings weren't (shouldn't happen if player exists with default)
            // or player was not found by the service. PlayerService should throw PlayerNotFoundException.
            // For robustness, let's assume if service returns empty, it's akin to not found for this specific data.
            logger.warn("Location visibility settings not found for player ID: {}", playerId);
            return response
                    .withStatusCode(404)
                    .withBody(gson.toJson(Map.of("message", "Location visibility settings not found or player does not exist.")));
        }
    }

    /**
     * Handles PUT /players/me/settings/location-visibility
     */
    private APIGatewayProxyResponseEvent handleUpdateLocationVisibilitySettings(APIGatewayProxyRequestEvent request, 
                                                                                APIGatewayProxyResponseEvent response) {
        String playerId = HandlerUtils.getPlayerIdFromRequest(request)
            .orElseThrow(() -> new ValidationException("Player ID not found in request context."));

        String requestBody = request.getBody();
        if (requestBody == null || requestBody.trim().isEmpty()) {
            logger.warn("Update location visibility request for player {} has missing or empty body", playerId);
            return response
                .withStatusCode(400)
                .withBody(gson.toJson(Map.of("message", "Request body is missing or empty.")));
        }

        Map<String, String> bodyMap;
        try {
            bodyMap = gson.fromJson(requestBody, new TypeToken<Map<String, String>>(){}.getType());
            if (bodyMap == null) { // Handle case where body is "null" as a string, or other GSON quirks
                 throw new ValidationException("Request body is invalid or could not be parsed.");
            }
        } catch (JsonSyntaxException e) {
            logger.warn("Invalid JSON in request body for player {}: {}", playerId, e.getMessage());
            return response
                .withStatusCode(400)
                .withBody(gson.toJson(Map.of("message", "Invalid JSON format in request body.")));
        }
        
        String visibilityString = bodyMap.get("locationVisibility");
        if (visibilityString == null || visibilityString.trim().isEmpty()) {
            logger.warn("Update location visibility request for player {} is missing 'locationVisibility' field.", playerId);
            return response
                .withStatusCode(400)
                .withBody(gson.toJson(Map.of("message", "Missing 'locationVisibility' in request body.")));
        }

        Player.LocationVisibility visibility;
        try {
            visibility = Player.LocationVisibility.valueOf(visibilityString.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid value for 'locationVisibility' for player {}: {}", playerId, visibilityString);
            return response
                .withStatusCode(400)
                .withBody(gson.toJson(Map.of("message", "Invalid value for 'locationVisibility'. Valid values are: " + 
                                             java.util.Arrays.stream(Player.LocationVisibility.values())
                                                             .map(Enum::name)
                                                             .collect(java.util.stream.Collectors.joining(", ")))));
        }

        logger.info("Updating location visibility for player ID: {} to {}", playerId, visibility);
        Player updatedPlayer = playerService.updateLocationVisibilitySettings(playerId, visibility);

        return response
                .withStatusCode(200)
                .withBody(gson.toJson(Map.of("message", "Successfully updated location visibility settings.", 
                                             "locationVisibility", updatedPlayer.getLocationVisibility().name())));
    }

    /**
     * Handles POST /players/me/location/pause to pause location sharing for the authenticated player.
     */
    private APIGatewayProxyResponseEvent handlePauseLocationSharing(APIGatewayProxyRequestEvent request,
                                                                    APIGatewayProxyResponseEvent response) {
        try {
            Optional<String> playerIdOpt = HandlerUtils.getPlayerIdFromRequest(request);
            if (playerIdOpt.isEmpty()) {
                return response.withStatusCode(401).withBody(gson.toJson(Map.of("message", "Unauthorized: Missing player ID")));
            }
            String playerId = playerIdOpt.get();

            Player player = playerService.pauseLocationSharing(playerId);
            // Ensure player and cooldown are not null before creating the response map
            String cooldownUntil = player.getLocationPauseCooldownUntil();
            Map<String, Object> responseBody = Map.of(
                "message", "Successfully paused location sharing.", 
                "cooldownUntil", cooldownUntil != null ? cooldownUntil : "N/A"
            );
            return response.withStatusCode(200).withBody(gson.toJson(responseBody));
        } catch (PlayerNotFoundException e) {
            logger.warn("Player operation failed: {}", e.getMessage());
            return response
                    .withStatusCode(404)
                    .withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (Exception e) {
            logger.error("Error processing player request: {}", e.getMessage(), e);
            return response
                    .withStatusCode(500)
                    .withBody(gson.toJson(Map.of("message", "Internal Server Error")));
        }
    }

    /**
     * Handles POST /players/me/location/resume to resume location sharing for the authenticated player.
     */
    private APIGatewayProxyResponseEvent handleResumeLocationSharing(APIGatewayProxyRequestEvent request,
                                                                     APIGatewayProxyResponseEvent response) {
        try {
            Optional<String> playerIdOpt = HandlerUtils.getPlayerIdFromRequest(request);
            if (playerIdOpt.isEmpty()) {
                return response.withStatusCode(401).withBody(gson.toJson(Map.of("message", "Unauthorized: Missing player ID")));
            }
            String playerId = playerIdOpt.get();

            playerService.resumeLocationSharing(playerId);
            return response.withStatusCode(200).withBody(gson.toJson(Map.of("message", "Successfully resumed location sharing.")));
        } catch (PlayerNotFoundException e) {
            logger.warn("Player operation failed: {}", e.getMessage());
            return response
                    .withStatusCode(404)
                    .withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (Exception e) {
            logger.error("Error processing player request: {}", e.getMessage(), e);
            return response
                    .withStatusCode(500)
                    .withBody(gson.toJson(Map.of("message", "Internal Server Error")));
        }
    }

    /**
     * Handles PUT /players/me/settings/location-precision
     */
    private APIGatewayProxyResponseEvent handleUpdateLocationPrecisionSettings(APIGatewayProxyRequestEvent request, 
                                                                                APIGatewayProxyResponseEvent response) {
        String playerId = HandlerUtils.getPlayerIdFromRequest(request)
            .orElseThrow(() -> new ValidationException("Player ID not found in request context."));

        String requestBody = request.getBody();
        if (requestBody == null || requestBody.trim().isEmpty()) {
            logger.warn("Update location precision request for player {} has missing or empty body", playerId);
            return response
                .withStatusCode(400)
                .withBody(gson.toJson(Map.of("message", "Request body is missing or empty.")));
        }

        Map<String, String> bodyMap;
        try {
            bodyMap = gson.fromJson(requestBody, new TypeToken<Map<String, String>>(){}.getType());
            if (bodyMap == null) {
                 throw new ValidationException("Request body is invalid or could not be parsed.");
            }
        } catch (JsonSyntaxException e) {
            logger.warn("Invalid JSON in request body for player {}: {}", playerId, e.getMessage());
            return response
                .withStatusCode(400)
                .withBody(gson.toJson(Map.of("message", "Invalid JSON format in request body.")));
        }
        
        String precisionString = bodyMap.get("locationPrecision");
        if (precisionString == null || precisionString.trim().isEmpty()) {
            logger.warn("Update location precision request for player {} is missing 'locationPrecision' field.", playerId);
            return response
                .withStatusCode(400)
                .withBody(gson.toJson(Map.of("message", "Missing 'locationPrecision' in request body.")));
        }

        Player.LocationPrecision precision;
        try {
            precision = Player.LocationPrecision.valueOf(precisionString.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid value for 'locationPrecision' for player {}: {}", playerId, precisionString);
            return response
                .withStatusCode(400)
                .withBody(gson.toJson(Map.of("message", "Invalid value for 'locationPrecision'. Valid values are: " + 
                                             java.util.Arrays.stream(Player.LocationPrecision.values())
                                                             .map(Enum::name)
                                                             .collect(java.util.stream.Collectors.joining(", ")))));
        }

        logger.info("Updating location precision for player ID: {} to {}", playerId, precision);
        Player updatedPlayer = playerService.updateLocationPrecisionSettings(playerId, precision);

        return response
                .withStatusCode(200)
                .withBody(gson.toJson(Map.of("message", "Successfully updated location precision settings.", 
                                             "locationPrecision", updatedPlayer.getLocationPrecision().name())));
    }

    private APIGatewayProxyResponseEvent handleGetLocationPrecisionSettings(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) {
        String playerId = HandlerUtils.getPlayerIdFromRequest(request)
                .orElseThrow(() -> new ValidationException("Player ID not found in request context."));

        logger.debug("Handling get location precision settings for player ID: {}", playerId);

        Player.LocationPrecision precision = playerService.getLocationPrecisionSettings(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("Player or location precision settings not found for ID: " + playerId));
        
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(Map.of("locationPrecision", precision.name())));
    }
} 