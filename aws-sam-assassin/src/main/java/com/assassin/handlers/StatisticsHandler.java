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
import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbKillDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.GameDao;
import com.assassin.dao.KillDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.model.Player;
import com.assassin.model.PlayerStats;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;

/**
 * Handles API requests related to game statistics and leaderboards.
 */
public class StatisticsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsHandler.class);
    private static final int DEFAULT_LEADERBOARD_LIMIT = 10; // Default number of players to show
    private final PlayerDao playerDao;
    private final KillDao killDao; // Add KillDao
    private final GameDao gameDao; // Add GameDao
    private final Gson gson = new Gson();

    public StatisticsHandler() {
        // Use constructor injection if using a framework, otherwise instantiate directly
        this.playerDao = new DynamoDbPlayerDao();
        this.killDao = new DynamoDbKillDao(); // Instantiate KillDao
        this.gameDao = new DynamoDbGameDao(); // Instantiate GameDao
    }

    // Constructor for dependency injection/testing
    public StatisticsHandler(PlayerDao playerDao, KillDao killDao, GameDao gameDao) {
        this.playerDao = playerDao;
        this.killDao = killDao;
        this.gameDao = gameDao;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        String httpMethod = input.getHttpMethod();
        String path = input.getPath();
        logger.info("Received {} request for path: {}", httpMethod, path);

        // Route based on path
        if ("GET".equalsIgnoreCase(httpMethod)) {
            if (path.endsWith("/leaderboard/kills")) {
                return getKillsLeaderboard(input);
            } else if (path.matches(".*/stats/player/[^/]+$")) { // Match /stats/player/{playerId}
                 // Extract playerId from path
                String[] pathParts = path.split("/");
                String playerId = pathParts[pathParts.length - 1];
                return getPlayerStatistics(playerId);
            }
        }
        
        // Default fallback for unsupported routes
        logger.warn("Unsupported route: {} {}", httpMethod, path);
        return HandlerUtils.createErrorResponse(404, "Not Found");
    }

    /**
     * Handles GET /leaderboard/kills requests.
     */
    private APIGatewayProxyResponseEvent getKillsLeaderboard(APIGatewayProxyRequestEvent input) {
        try {
            // Determine limit from query parameters, default if not provided
            int limit = DEFAULT_LEADERBOARD_LIMIT;
            Map<String, String> queryParams = input.getQueryStringParameters();
            if (queryParams != null && queryParams.containsKey("limit")) {
                try {
                    limit = Integer.parseInt(queryParams.get("limit"));
                    if (limit <= 0 || limit > 100) { // Add reasonable bounds
                        logger.warn("Invalid limit parameter: {}. Using default.", queryParams.get("limit"));
                        limit = DEFAULT_LEADERBOARD_LIMIT;
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid number format for limit parameter: {}. Using default.", queryParams.get("limit"));
                    limit = DEFAULT_LEADERBOARD_LIMIT;
                }
            }
            
            // Determine status from query parameters, default to ACTIVE if not provided
            String targetStatus = "ACTIVE"; // Default
            if (queryParams != null && queryParams.containsKey("status")) {
                String requestedStatus = queryParams.get("status").trim();
                if (!requestedStatus.isEmpty()) {
                    targetStatus = requestedStatus.toUpperCase(); // Use provided status, uppercased
                } else {
                    logger.warn("Empty status parameter provided, using default ACTIVE.");
                }
            }

            // Construct the partition key for the GSI
            String statusPartitionKey = "STATUS#" + targetStatus;
            // TODO: Make this configurable or dynamic based on game status if needed - Removed TODO as it's now configurable
            // String statusPartitionKey = "STATUS#ACTIVE"; 

            logger.info("Fetching kill leaderboard with limit: {} for status: {}", limit, targetStatus);
            List<Player> leaderboard = playerDao.getLeaderboardByKillCount(statusPartitionKey, limit);

            String responseBody = gson.toJson(leaderboard);
            return HandlerUtils.createApiResponse(200, responseBody);
                    
        } catch (Exception e) {
            logger.error("Error fetching kill leaderboard: {}", e.getMessage(), e);
            return HandlerUtils.createErrorResponse(500, "Failed to fetch kill leaderboard");
        }
    }
    
    /**
     * Handles GET /stats/player/{playerId} requests.
     */
    private APIGatewayProxyResponseEvent getPlayerStatistics(String playerId) {
        logger.info("Fetching statistics for player: {}", playerId);
        try {
            // 1. Get basic player info (we need kill count from here)
            Optional<Player> playerOpt = playerDao.getPlayerById(playerId);
            if (playerOpt.isEmpty()) {
                logger.warn("Player not found for stats request: {}", playerId);
                throw new PlayerNotFoundException("Player with ID " + playerId + " not found.");
            }
            Player player = playerOpt.get();
            int totalKills = player.getKillCount(); // Assuming Player object has killCount

            // 2. Get total deaths
            int totalDeaths = killDao.countDeathsByVictim(playerId);

            // 3. Get games played
            int gamesPlayed = gameDao.countGamesPlayedByPlayer(playerId);

            // 4. Get wins
            int wins = gameDao.countWinsByPlayer(playerId);

            // 5. Construct PlayerStats object using constructor and setters (workaround for builder issue)
            PlayerStats stats = new PlayerStats();
            stats.setPlayerId(playerId);
            stats.setKills(totalKills);
            stats.setDeaths(totalDeaths);
            // TODO: Add getters for gamesPlayed and wins in PlayerStats if needed, or implement retrieval in DAOs
            // stats.setGamesPlayed(gamesPlayed); // Needs implementation
            // stats.setWins(wins); // Needs implementation
            stats.setPoints(totalKills * 10 - totalDeaths * 5); // Example points calculation

            logger.info("Successfully fetched stats for player {}: {}", playerId, stats);
            String responseBody = gson.toJson(stats);
            return HandlerUtils.createApiResponse(200, responseBody);
                    
        } catch (PlayerNotFoundException e) {
             logger.warn("Player not found for stats request: {}", playerId);
             return HandlerUtils.createErrorResponse(404, "Player not found: " + playerId);
        } catch (Exception e) {
            logger.error("Error fetching statistics for player {}: {}", playerId, e.getMessage(), e);
            return HandlerUtils.createErrorResponse(500, "Failed to fetch statistics for player " + playerId);
        }
    }
} 