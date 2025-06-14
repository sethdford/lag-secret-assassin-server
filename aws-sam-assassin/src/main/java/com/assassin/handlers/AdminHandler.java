package com.assassin.handlers;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.DynamoDbKillDao;
import com.assassin.model.Game;
import com.assassin.model.Player;
import com.assassin.model.Kill;
import com.assassin.util.ApiGatewayResponseBuilder;
import com.assassin.util.GsonUtil;

/**
 * Handler for administrative functions and game monitoring.
 */
public class AdminHandler {

    private static final Logger logger = LoggerFactory.getLogger(AdminHandler.class);

    private final DynamoDbGameDao gameDao;
    private final DynamoDbPlayerDao playerDao;
    private final DynamoDbKillDao killDao;

    public AdminHandler() {
        this.gameDao = new DynamoDbGameDao();
        this.playerDao = new DynamoDbPlayerDao();
        this.killDao = new DynamoDbKillDao();
    }

    public AdminHandler(DynamoDbGameDao gameDao, DynamoDbPlayerDao playerDao, DynamoDbKillDao killDao) {
        this.gameDao = gameDao;
        this.playerDao = playerDao;
        this.killDao = killDao;
    }

    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            logger.info("Processing admin request: {} {}", 
                request.getHttpMethod(), request.getPath());

            String httpMethod = request.getHttpMethod();
            String path = request.getPath();

            // Handle different admin endpoints
            if ("GET".equals(httpMethod)) {
                if (path.contains("/admin/games/") && path.endsWith("/overview")) {
                    return getGameOverview(request, context);
                } else if (path.equals("/admin/system/health")) {
                    return getSystemHealth(request, context);
                }
            } else if ("POST".equals(httpMethod)) {
                if (path.contains("/admin/games/") && path.endsWith("/actions/start")) {
                    return startGame(request, context);
                } else if (path.contains("/admin/games/") && path.endsWith("/actions/end")) {
                    return endGame(request, context);
                }
            }

            return ApiGatewayResponseBuilder.buildErrorResponse(404, "Admin endpoint not found");

        } catch (RuntimeException e) {
            logger.error("Error processing admin request", e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Internal server error");
        }
    }

    private APIGatewayProxyResponseEvent getGameOverview(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String gameId = request.getPathParameters().get("gameId");
            if (gameId == null) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Game ID is required");
            }

            Optional<Game> gameOpt = gameDao.getGameById(gameId);
            if (!gameOpt.isPresent()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(404, "Game not found");
            }

            Game game = gameOpt.get();
            Map<String, Object> overview = new HashMap<>();
            
            // Basic game info
            overview.put("gameId", gameId);
            overview.put("gameName", game.getGameName());
            overview.put("status", game.getStatus());
            overview.put("createdAt", game.getCreatedAt());
            overview.put("adminPlayerId", game.getAdminPlayerID());
            
            // Player statistics
            List<String> playerIds = game.getPlayerIDs();
            int totalPlayers = playerIds != null ? playerIds.size() : 0;
            overview.put("totalPlayers", totalPlayers);
            
            // Kill statistics
            List<Kill> kills = killDao.findKillsByGameId(gameId);
            overview.put("totalKills", kills.size());
            
            // Game settings
            overview.put("shrinkingZoneEnabled", game.getShrinkingZoneEnabled());
            overview.put("mapId", game.getMapId());

            return ApiGatewayResponseBuilder.buildResponse(200, GsonUtil.getGson().toJson(overview));

        } catch (RuntimeException e) {
            logger.error("Error getting game overview", e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Error retrieving game overview");
        }
    }

    private APIGatewayProxyResponseEvent getSystemHealth(APIGatewayProxyRequestEvent request, Context context) {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "healthy");
            health.put("timestamp", System.currentTimeMillis());
            
            // System metrics
            Map<String, Object> metrics = new HashMap<>();
            
            // Get active games count
            List<Game> activeGames = gameDao.listGamesByStatus("ACTIVE");
            metrics.put("activeGames", activeGames.size());
            
            // Database connectivity test
            try {
                gameDao.listGamesByStatus("ACTIVE");
                health.put("database", "connected");
            } catch (RuntimeException e) {
                health.put("database", "error");
                health.put("status", "degraded");
            }
            
            health.put("metrics", metrics);

            return ApiGatewayResponseBuilder.buildResponse(200, GsonUtil.getGson().toJson(health));

        } catch (RuntimeException e) {
            logger.error("Error getting system health", e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Error retrieving system health");
        }
    }

    private APIGatewayProxyResponseEvent startGame(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String gameId = request.getPathParameters().get("gameId");
            if (gameId == null) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Game ID is required");
            }

            Optional<Game> gameOpt = gameDao.getGameById(gameId);
            if (!gameOpt.isPresent()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(404, "Game not found");
            }

            Game game = gameOpt.get();
            
            // Start the game
            game.setStartTimeEpochMillis(System.currentTimeMillis());
            game.setStatus("ACTIVE");
            gameDao.saveGame(game);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Game started successfully");
            response.put("gameId", gameId);
            response.put("startTime", game.getStartTimeEpochMillis());

            return ApiGatewayResponseBuilder.buildResponse(200, GsonUtil.getGson().toJson(response));

        } catch (RuntimeException e) {
            logger.error("Error starting game", e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Error starting game");
        }
    }

    private APIGatewayProxyResponseEvent endGame(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String gameId = request.getPathParameters().get("gameId");
            if (gameId == null) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Game ID is required");
            }

            Optional<Game> gameOpt = gameDao.getGameById(gameId);
            if (!gameOpt.isPresent()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(404, "Game not found");
            }

            Game game = gameOpt.get();
            
            // End the game
            game.setStatus("COMPLETED");
            gameDao.saveGame(game);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Game ended successfully");
            response.put("gameId", gameId);
            response.put("endTime", System.currentTimeMillis());

            return ApiGatewayResponseBuilder.buildResponse(200, GsonUtil.getGson().toJson(response));

        } catch (RuntimeException e) {
            logger.error("Error ending game", e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Error ending game");
        }
    }
}