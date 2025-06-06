package com.assassin.handlers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler; // Assuming Game model exists
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent; // Assuming GameService exists
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GameStateException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.PlayerPersistenceException;
import com.assassin.exception.UnauthorizedException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Game;
import com.assassin.model.Coordinate; // Import Coordinate
import com.assassin.service.GameService;
import com.assassin.util.GsonUtil;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken; // Import TypeToken
import java.lang.reflect.Type; // Import Type

/**
 * Handler for Game management API requests.
 */
public class GameHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(GameHandler.class);
    private static final Gson gson = GsonUtil.getGson();

    private final GameService gameService;

    // Regex patterns for path matching
    private static final Pattern PLAYER_IN_GAME_PATTERN = Pattern.compile("/games/([^/]+)/players/([^/]+)$");
    private static final Pattern GAME_ACTION_PATTERN = Pattern.compile("/games/([^/]+)/(\\w+)$");
    private static final Pattern GAME_BOUNDARY_PATTERN = Pattern.compile("/games/([^/]+)/boundary$");
    private static final Pattern GAME_ID_PATTERN = Pattern.compile("/games/([^/]+)$");

    // Default constructor initializes the service
    public GameHandler() {
        this.gameService = new GameService();
    }

    // Constructor for dependency injection (testing)
    public GameHandler(GameService gameService) {
        this.gameService = gameService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received game request: Method={}, Path={}", request.getHttpMethod(), request.getPath());
        
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(HandlerUtils.getResponseHeaders()); // Use utility method

        try {
            // Routing based on HTTP method and path
            Matcher playerInGameMatcher = PLAYER_IN_GAME_PATTERN.matcher(path);
            Matcher gameActionMatcher = GAME_ACTION_PATTERN.matcher(path);
            Matcher gameIdMatcher = GAME_ID_PATTERN.matcher(path);
            Matcher gameBoundaryMatcher = GAME_BOUNDARY_PATTERN.matcher(path); // Matcher for boundary path

            if ("POST".equals(httpMethod) && "/games".equals(path)) {
                return createGame(request, response);
            } else if ("GET".equals(httpMethod) && "/games".equals(path)) {
                return listGames(request, response);
            } else if ("GET".equals(httpMethod) && gameIdMatcher.matches()) {
                String gameId = gameIdMatcher.group(1);
                return getGame(gameId, response);
            } else if ("PUT".equals(httpMethod) && gameBoundaryMatcher.matches()) { // Handle PUT /games/{gameId}/boundary
                String gameId = gameBoundaryMatcher.group(1);
                return updateGameBoundary(gameId, request, response);
            } else if ("POST".equals(httpMethod) && gameActionMatcher.matches()) {
                String gameId = gameActionMatcher.group(1);
                String action = gameActionMatcher.group(2);
                if ("join".equals(action)) {
                    return joinGame(gameId, request, response);
                } else if ("start".equals(action)) {
                    return startGame(gameId, request, response);
                } else if ("end".equals(action)) { // New Admin Action: Force End Game
                    return forceEndGame(gameId, request, response);
                }
            } else if ("DELETE".equals(httpMethod) && playerInGameMatcher.matches()) {
                // New Admin Action: Remove Player from Pending Game
                String gameId = playerInGameMatcher.group(1);
                String playerIdToRemove = playerInGameMatcher.group(2);
                return removePlayerFromGame(gameId, playerIdToRemove, request, response);
            } 

            // If no routes match
            return response.withStatusCode(404).withBody(gson.toJson(Map.of("error", "Route not found")));
           
        } catch (GameNotFoundException | PlayerNotFoundException e) {
            logger.warn("Resource not found: {}", e.getMessage());
            return response.withStatusCode(404).withBody(gson.toJson(Map.of("error", e.getMessage())));
        } catch (UnauthorizedException e) {
            logger.warn("Authorization failed: {}", e.getMessage());
            return response.withStatusCode(403).withBody(gson.toJson(Map.of("error", e.getMessage())));
        } catch (ValidationException | GameStateException | IllegalArgumentException e) {
            logger.warn("Invalid request or state: {}", e.getMessage());
            return response.withStatusCode(400).withBody(gson.toJson(Map.of("error", e.getMessage())));
        } catch (JsonSyntaxException e) {
            logger.error("Invalid JSON input: {}\nBody: {}", e.getMessage(), request.getBody()); // Log body on parse error
            return response.withStatusCode(400).withBody(gson.toJson(Map.of("error", "Invalid JSON format: " + e.getMessage())));
        } catch (PlayerPersistenceException e) {
            logger.error("Database error processing game request: {}", e.getMessage(), e);
            return response.withStatusCode(500).withBody(gson.toJson(Map.of("error", "Database error occurred")));
        } catch (Exception e) {
            logger.error("Internal server error processing game request", e);
            return response.withStatusCode(500).withBody(gson.toJson(Map.of("error", "Internal Server Error")));
        }
    }

    private APIGatewayProxyResponseEvent createGame(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) throws ValidationException {
        JsonObject body = gson.fromJson(request.getBody(), JsonObject.class);
        String gameName = Optional.ofNullable(body.get("gameName")).map(com.google.gson.JsonElement::getAsString).orElse(null);
        String playerId = HandlerUtils.getPlayerIdFromRequest(request)
                .orElseThrow(() -> new ValidationException("Player ID not found in request context."));

        Game createdGame = gameService.createGame(gameName, playerId);
        return response.withStatusCode(201).withBody(gson.toJson(createdGame));
    }

    private APIGatewayProxyResponseEvent listGames(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) {
        // Filter by status query parameter, defaulting to PENDING?
        String status = Optional.ofNullable(request.getQueryStringParameters())
                                .map(params -> params.get("status"))
                                .orElse("PENDING"); // Default or list all?
        
        List<Game> games = gameService.listGames(status.toUpperCase());
        return response.withStatusCode(200).withBody(gson.toJson(games));
    }

    private APIGatewayProxyResponseEvent getGame(String gameId, APIGatewayProxyResponseEvent response) throws GameNotFoundException {
        Game game = gameService.getGame(gameId);
        return response.withStatusCode(200).withBody(gson.toJson(game));
    }

    private APIGatewayProxyResponseEvent joinGame(String gameId, APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) throws GameNotFoundException, ValidationException {
        String playerId = HandlerUtils.getPlayerIdFromRequest(request)
                .orElseThrow(() -> new ValidationException("Player ID not found in request context."));
        Game updatedGame = gameService.joinGame(gameId, playerId);
        return response.withStatusCode(200).withBody(gson.toJson(updatedGame));
    }

    private APIGatewayProxyResponseEvent startGame(String gameId, APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) 
            throws GameNotFoundException, GameStateException, PlayerPersistenceException {
        logger.info("Handling request to start game: {}", gameId);
        gameService.startGameAndAssignTargets(gameId);
        
        return response.withStatusCode(200).withBody(gson.toJson(Map.of("message", "Game started successfully and targets assigned.")));
    }

    private APIGatewayProxyResponseEvent forceEndGame(String gameId, APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) throws GameNotFoundException, ValidationException {
        String requestingPlayerId = HandlerUtils.getPlayerIdFromRequest(request)
                .orElseThrow(() -> new ValidationException("Player ID not found in request context."));
        logger.info("Request from {} to force end game {}", requestingPlayerId, gameId);
        Game updatedGame = gameService.forceEndGame(gameId, requestingPlayerId);
        return response.withStatusCode(200).withBody(gson.toJson(updatedGame));
    }

    private APIGatewayProxyResponseEvent removePlayerFromGame(String gameId, String playerIdToRemove, APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) throws GameNotFoundException, ValidationException {
        String requestingPlayerId = HandlerUtils.getPlayerIdFromRequest(request)
                .orElseThrow(() -> new ValidationException("Player ID not found in request context."));
        logger.info("Request from {} to remove player {} from game {}", requestingPlayerId, playerIdToRemove, gameId);
        Game updatedGame = gameService.removePlayerFromGame(gameId, playerIdToRemove, requestingPlayerId);
        return response.withStatusCode(200).withBody(gson.toJson(updatedGame));
    }

    // New private method to handle updating the game boundary
    private APIGatewayProxyResponseEvent updateGameBoundary(String gameId, APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response)
            throws GameNotFoundException, ValidationException, JsonSyntaxException, UnauthorizedException {

        String requestingPlayerId = HandlerUtils.getPlayerIdFromRequest(request)
                 .orElseThrow(() -> new ValidationException("Player ID not found in request context."));
        logger.info("Request from {} to update boundary for game {}", requestingPlayerId, gameId);

        // Define the type for List<Coordinate>
        Type coordinateListType = new TypeToken<List<Coordinate>>() {}.getType();

        // Parse the request body into a List<Coordinate>
        List<Coordinate> boundary;
        try {
            boundary = gson.fromJson(request.getBody(), coordinateListType);
            if (boundary == null) {
                throw new ValidationException("Request body cannot be null.");
            }
            // Add more validation? E.g., polygon needs at least 3 points?
            if (boundary.size() < 3) {
                throw new ValidationException("Game boundary must contain at least 3 points.");
            }
            // Validate individual coordinates?
             for (Coordinate coord : boundary) {
                 // Remove null check as getLatitude/getLongitude return primitive double
                 // if (coord.getLatitude() == null || coord.getLongitude() == null) {
                 //    throw new ValidationException("Coordinates in the boundary list cannot be null.");
                 // }
                 // Basic range check (can be more strict if needed)
                 if (coord.getLatitude() < -90 || coord.getLatitude() > 90 || coord.getLongitude() < -180 || coord.getLongitude() > 180) {
                    throw new ValidationException("Invalid latitude or longitude value in boundary: " + coord);
                 }
             }
        } catch (JsonSyntaxException e) {
            logger.warn("Failed to parse boundary JSON for game {}: {}", gameId, e.getMessage());
            throw e; // Re-throw to be caught by the main handler
        }


        // Call the service layer to update the boundary
        // TODO: Implement gameService.updateGameBoundary method
        Game updatedGame = gameService.updateGameBoundary(gameId, boundary, requestingPlayerId);

        return response.withStatusCode(200).withBody(gson.toJson(updatedGame));
    }
} 