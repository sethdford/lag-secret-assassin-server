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
import com.assassin.util.GsonUtil;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;

/**
 * Handler for player management operations.
 * Processes API Gateway requests for player CRUD operations.
 */
public class PlayerHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(PlayerHandler.class);
    private static final Gson gson = GsonUtil.getGson();
    private final PlayerDao playerDao;
    
    /**
     * Default constructor, initializes DAO.
     */
    public PlayerHandler() {
        this.playerDao = new DynamoDbPlayerDao();
    }
    
    /**
     * Constructor with dependency injection for testability.
     * 
     * @param playerDao The DAO for player operations
     */
    public PlayerHandler(PlayerDao playerDao) {
        this.playerDao = playerDao;
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
            } else {
                return response
                        .withStatusCode(404)
                        .withBody(gson.toJson(Map.of("message", "Route not found")));
            }
        } catch (PlayerNotFoundException e) {
            logger.warn("Player operation failed: {}", e.getMessage());
            return response
                    .withStatusCode(404)
                    .withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
            // Catch potential errors from getPlayerIdFromRequest or DAO
            logger.error("Error getting target for player: {}", e.getMessage(), e);
            return response
                    .withStatusCode(500)
                    .withBody(gson.toJson(Map.of("message", "Internal Server Error")));
        }
    }
    
    // Helper to extract resource ID from path like /resource/{id}
    private String getResourceIdFromPath(String path) {
        try {
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (RuntimeException e) {
            logger.error("Could not extract resource ID from path: {}", path, e);
            throw new IllegalArgumentException("Invalid resource path format");
        }
    }
} 