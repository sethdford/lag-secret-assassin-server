package com.assassin.handlers;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GamePersistenceException;
import com.assassin.exception.GameStateException;
import com.assassin.exception.UnauthorizedException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.service.GameService;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

/**
 * Handles requests related to managing game settings and state.
 */
public class GameManagementHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(GameManagementHandler.class);
    private final GameService gameService;
    private final Gson gson = new Gson();
    private static final Type COORDINATE_LIST_TYPE = new TypeToken<List<Coordinate>>() {}.getType();

    /**
     * Default constructor, initializes dependencies.
     */
    public GameManagementHandler() {
        // Ideally use dependency injection
        this.gameService = new GameService(); 
    }

    /**
     * Constructor for testing with mock dependencies.
     */
    public GameManagementHandler(GameService gameService) {
        this.gameService = gameService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        String httpMethod = input.getHttpMethod();
        String path = input.getPath();
        logger.info("Received Game Management request: Method={}, Path={}", httpMethod, path);

        Optional<String> requestingPlayerIdOpt = HandlerUtils.getPlayerIdFromRequest(input);
        if (requestingPlayerIdOpt.isEmpty()) {
            logger.warn("Admin request received without authenticated player ID.");
            return HandlerUtils.createErrorResponse(401, "Unauthorized: Missing authentication token.");
        }
        String requestingPlayerId = requestingPlayerIdOpt.get();
        logger.info("Request authorized for player ID: {}", requestingPlayerId);

        // TODO: Implement finer-grained authorization check for admin roles based on requestingPlayerId

        try {
            // Replace Java 21+ switch patterns with if-else if for Java 17
            if ("POST".equals(httpMethod) && "/admin/games".equals(path)) {
                // Admin creates a new game
                Game gameInput = gson.fromJson(input.getBody(), Game.class);
                if (gameInput == null || gameInput.getGameName() == null || gameInput.getGameName().trim().isEmpty()) {
                    throw new ValidationException("Game name is required to create a game.");
                }
                // Use requestingPlayerId as the admin
                Game createdGame = gameService.createGame(gameInput.getGameName(), requestingPlayerId);
                return HandlerUtils.createApiResponse(201, gson.toJson(createdGame));
            } else if ("POST".equals(httpMethod) && path.matches("/admin/games/[^/]+/start")) {
                 String gameId = extractGameIdFromPath(path, "/start");
                 // Admin starts a game - NOTE: startGameAndAssignTargets doesn't currently check admin rights
                 // TODO: Add admin authorization check in GameService.startGameAndAssignTargets or here
                 gameService.startGameAndAssignTargets(gameId);
                 return HandlerUtils.createApiResponse(200, "{\"message\": \"Game " + gameId + " started successfully\"}");
            } else if ("POST".equals(httpMethod) && path.matches("/admin/games/[^/]+/end")) {
                 String gameId = extractGameIdFromPath(path, "/end");
                 // Admin ends a game
                 gameService.forceEndGame(gameId, requestingPlayerId);
                 return HandlerUtils.createApiResponse(200, "{\"message\": \"Game " + gameId + " ended successfully\"}");
            } else if ("POST".equals(httpMethod) && path.matches("/admin/games/[^/]+/players")) {
                 String gameId = extractGameIdFromPath(path, "/players");
                 // Admin adds a player to a game
                 AddPlayerRequest addPlayerReq = gson.fromJson(input.getBody(), AddPlayerRequest.class);
                 if (addPlayerReq == null || addPlayerReq.getPlayerId() == null || addPlayerReq.getPlayerId().trim().isEmpty()) {
                     throw new ValidationException("Player ID is required to add a player.");
                 }
                 // TODO: Implement adminAddPlayerToGame in GameService
                 // gameService.adminAddPlayerToGame(gameId, addPlayerReq.getPlayerId(), requestingPlayerId);
                 logger.warn("Admin add player functionality (POST /admin/games/{}/players) not implemented yet.", gameId);
                 return HandlerUtils.createErrorResponse(501, "Admin add player functionality not implemented.");
            } else if ("DELETE".equals(httpMethod) && path.matches("/admin/games/[^/]+/players/[^/]+$")) {
                 String[] parts = path.split("/");
                 String gameId = parts[3];
                 String playerIdToRemove = parts[5];
                 // Admin removes a player from a game
                 gameService.removePlayerFromGame(gameId, playerIdToRemove, requestingPlayerId);
                 return HandlerUtils.createApiResponse(200, "{\"message\": \"Player " + playerIdToRemove + " removed from game " + gameId + "\"}");
            } else {
                logger.warn("Admin route not found for method {} and path {}", httpMethod, path);
                return HandlerUtils.createErrorResponse(404, "Admin route not found");
            }

        } catch (GameNotFoundException e) {
            logger.warn("Game not found for admin operation: {}", e.getMessage());
            return HandlerUtils.createErrorResponse(500, "An internal server error occurred.");
        } catch (com.assassin.exception.InvalidRequestException | JsonSyntaxException e) {
            logger.warn("Bad request: {}", e.getMessage());
            return HandlerUtils.createErrorResponse(400, e.getMessage());
        } catch (GamePersistenceException e) {
            logger.error("Database error: {}", e.getMessage(), e);
            return HandlerUtils.createErrorResponse(500, "Internal server error during game operation.");
        } catch (Exception e) {
            logger.error("Unexpected error in GameManagementHandler: {}", e.getMessage(), e);
            return HandlerUtils.createErrorResponse(500, "An unexpected error occurred.");
        }
    }

    /**
     * Updates the game boundary.
     * Assumes the caller has already verified admin privileges.
     */
    private APIGatewayProxyResponseEvent updateBoundary(APIGatewayProxyRequestEvent input) {
        String gameId = input.getPathParameters().get("gameId");
        String requestBody = input.getBody();
        logger.info("Attempting to update boundary for game ID: {} with body: {}", gameId, requestBody);

        Optional<String> requestingPlayerIdOpt = HandlerUtils.getPlayerIdFromRequest(input);
        if (requestingPlayerIdOpt.isEmpty()) {
             return HandlerUtils.createErrorResponse(401, "Unauthorized: Missing authentication token.");
        }
        String requestingPlayerId = requestingPlayerIdOpt.get();

        if (gameId == null || gameId.trim().isEmpty()) {
            return HandlerUtils.createErrorResponse(400, "Missing 'gameId' path parameter.");
        }
        if (requestBody == null || requestBody.trim().isEmpty()) {
            return HandlerUtils.createErrorResponse(400, "Request body with boundary coordinates is required.");
        }

        try {
            List<Coordinate> boundary = gson.fromJson(requestBody, COORDINATE_LIST_TYPE);
            if (boundary == null || boundary.size() < 3) { // Basic validation: need at least 3 points for a polygon
                throw new ValidationException("Invalid boundary data: requires at least 3 coordinates.");
            }
            // TODO: Add more robust polygon validation (e.g., non-self-intersecting)

            // Call service method with requestingPlayerId
            gameService.updateGameBoundary(gameId, boundary, requestingPlayerId);

            logger.info("Successfully updated boundary for game {}", gameId);
            return HandlerUtils.createApiResponse(200, "{\"message\":\"Game boundary updated successfully.\"}");
        }
        // Catch specific exceptions first
        catch (GameNotFoundException e) {
            logger.warn("Game not found while updating boundary: {}", e.getMessage());
            return HandlerUtils.createErrorResponse(404, e.getMessage());
        } catch (UnauthorizedException e) {
             logger.warn("Unauthorized attempt to update boundary: {}", e.getMessage());
             return HandlerUtils.createErrorResponse(403, e.getMessage());
        } catch (GameStateException e) {
             logger.warn("Invalid game state for boundary update: {}", e.getMessage());
             return HandlerUtils.createErrorResponse(409, e.getMessage()); // 409 Conflict might be appropriate
        } catch (ValidationException | JsonSyntaxException e) {
            logger.warn("Invalid boundary data provided: {}", e.getMessage());
            return HandlerUtils.createErrorResponse(400, "Invalid boundary data: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error updating game boundary for game {}: {}", gameId, e.getMessage(), e);
            return HandlerUtils.createErrorResponse(500, "Failed to update game boundary due to an internal error.");
        }
    }

    private String getGameIdFromPath(String path) {
        // Extracts gameId from paths like /games/{gameId}/boundary
        String[] parts = path.split("/");
        if (parts.length >= 3 && "games".equals(parts[1])) {
            return parts[2];
        }
        return null;
    }

    private String extractGameIdFromPath(String path, String suffix) {
        // Extracts gameId from paths like /admin/games/{gameId}/start
        String[] parts = path.split("/");
        if (parts.length >= 3 && "admin".equals(parts[1]) && "games".equals(parts[2])) {
            return parts[3];
        }
        return null;
    }
} 