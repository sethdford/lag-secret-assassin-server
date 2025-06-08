package com.assassin.handlers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Game;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;
import com.assassin.service.GameService;
import com.assassin.service.GeospatialQueryService;
import com.assassin.service.LocationService;
import com.assassin.service.MapConfigurationService;
import com.assassin.util.GsonUtil;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;

/**
 * Handles administrative API requests for game monitoring, player management,
 * and progress timeline visualization.
 */
public class AdminHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(AdminHandler.class);
    private static final Gson gson = GsonUtil.getGson();
    
    private final DynamoDbGameDao gameDao;
    private final DynamoDbPlayerDao playerDao;
    private final GameService gameService;
    private final LocationService locationService;
    private final GeospatialQueryService geospatialQueryService;
    private final MapConfigurationService mapConfigurationService;

    public AdminHandler() {
        this.gameDao = new DynamoDbGameDao();
        this.playerDao = new DynamoDbPlayerDao();
        this.gameService = new GameService();
        this.locationService = new LocationService();
        this.geospatialQueryService = new GeospatialQueryService();
        this.mapConfigurationService = new MapConfigurationService();
    }

    // Constructor for dependency injection (testing)
    public AdminHandler(DynamoDbGameDao gameDao, 
                       DynamoDbPlayerDao playerDao,
                       GameService gameService,
                       LocationService locationService,
                       GeospatialQueryService geospatialQueryService,
                       MapConfigurationService mapConfigurationService) {
        this.gameDao = gameDao;
        this.playerDao = playerDao;
        this.gameService = gameService;
        this.locationService = locationService;
        this.geospatialQueryService = geospatialQueryService;
        this.mapConfigurationService = mapConfigurationService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(HandlerUtils.getResponseHeaders());

        logger.info("AdminHandler received request: Method={}, Path={}", httpMethod, path);

        try {
            // Verify admin permissions (simplified - in production would check roles)
            if (!isAdminRequest(request)) {
                return response
                        .withStatusCode(403)
                        .withBody(gson.toJson(Map.of("message", "Admin access required")));
            }

            // Route requests based on path and method
            if ("GET".equals(httpMethod)) {
                if (path.matches("/admin/games/[^/]+/timeline")) {
                    return getGameTimeline(request, response);
                } else if (path.matches("/admin/games/[^/]+/overview")) {
                    return getGameOverview(request, response);
                } else if (path.matches("/admin/games/[^/]+/players")) {
                    return getGamePlayers(request, response);
                } else if (path.matches("/admin/games/[^/]+/analytics")) {
                    return getGameAnalytics(request, response);
                } else if (path.matches("/admin/games")) {
                    return getAllGames(request, response);
                } else if (path.matches("/admin/system/health")) {
                    return getSystemHealth(request, response);
                }
            } else if ("POST".equals(httpMethod)) {
                if (path.matches("/admin/games/[^/]+/actions/pause")) {
                    return pauseGame(request, response);
                } else if (path.matches("/admin/games/[^/]+/actions/resume")) {
                    return resumeGame(request, response);
                } else if (path.matches("/admin/games/[^/]+/actions/end")) {
                    return endGame(request, response);
                }
            }
            
            logger.warn("Route not found in AdminHandler: {} {}", httpMethod, path);
            return response
                    .withStatusCode(404)
                    .withBody(gson.toJson(Map.of("message", "Route not found")));
                    
        } catch (ValidationException | IllegalArgumentException e) {
            logger.warn("Invalid input processing admin request: {}", e.getMessage());
            return response
                    .withStatusCode(400)
                    .withBody(gson.toJson(Map.of("message", "Invalid request data: " + e.getMessage())));
        } catch (PlayerNotFoundException | GameNotFoundException e) {
            logger.warn("Resource not found during admin request: {}", e.getMessage());
            return response
                    .withStatusCode(404)
                    .withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (Exception e) {
            logger.error("Error processing admin request: {}", e.getMessage(), e);
            return response
                    .withStatusCode(500)
                    .withBody(gson.toJson(Map.of("message", "Internal Server Error", "error", e.getClass().getSimpleName())));
        }
    }

    /**
     * GET /admin/games/{gameId}/timeline
     * Returns a detailed timeline of game events and progress.
     */
    private APIGatewayProxyResponseEvent getGameTimeline(APIGatewayProxyRequestEvent request, 
                                                        APIGatewayProxyResponseEvent response) 
            throws GameNotFoundException {
        String gameId = extractGameIdFromPath(request.getPath());
        
        Optional<Game> gameOpt = gameDao.getGameById(gameId);
        if (!gameOpt.isPresent()) {
            throw new GameNotFoundException("Game not found: " + gameId);
        }
        
        Game game = gameOpt.get();
        List<Player> players = playerDao.getPlayersByGameId(gameId);
        
        Map<String, Object> timeline = new HashMap<>();
        timeline.put("gameId", gameId);
        timeline.put("gameName", game.getGameName());
        timeline.put("status", game.getStatus());
        timeline.put("startTime", game.getStartTime());
        timeline.put("endTime", game.getEndTime());
        
        // Generate timeline events
        List<Map<String, Object>> events = generateTimelineEvents(game, players);
        timeline.put("events", events);
        
        // Add current statistics
        Map<String, Object> currentStats = generateCurrentStats(players);
        timeline.put("currentStats", currentStats);
        
        logger.info("Generated timeline for game: {}", gameId);
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(timeline));
    }

    /**
     * GET /admin/games/{gameId}/overview
     * Returns a comprehensive overview of the game state.
     */
    private APIGatewayProxyResponseEvent getGameOverview(APIGatewayProxyRequestEvent request, 
                                                        APIGatewayProxyResponseEvent response) 
            throws GameNotFoundException {
        String gameId = extractGameIdFromPath(request.getPath());
        
        Optional<Game> gameOpt = gameDao.getGameById(gameId);
        if (!gameOpt.isPresent()) {
            throw new GameNotFoundException("Game not found: " + gameId);
        }
        
        Game game = gameOpt.get();
        List<Player> players = playerDao.getPlayersByGameId(gameId);
        
        Map<String, Object> overview = new HashMap<>();
        overview.put("game", game);
        overview.put("playerCount", players.size());
        overview.put("activePlayerCount", players.stream().filter(p -> PlayerStatus.ACTIVE.name().equals(p.getStatus())).count());
        overview.put("eliminatedPlayerCount", players.stream().filter(p -> PlayerStatus.DEAD.name().equals(p.getStatus())).count());
        
        // Add location statistics
        long playersWithLocation = players.stream()
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .count();
        overview.put("playersWithLocation", playersWithLocation);
        overview.put("locationCoverage", players.isEmpty() ? 0 : (double) playersWithLocation / players.size());
        
        // Add recent activity
        List<Map<String, Object>> recentActivity = generateRecentActivity(players);
        overview.put("recentActivity", recentActivity);
        
        logger.info("Generated overview for game: {}", gameId);
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(overview));
    }

    /**
     * GET /admin/games/{gameId}/players
     * Returns detailed player information for the game.
     */
    private APIGatewayProxyResponseEvent getGamePlayers(APIGatewayProxyRequestEvent request, 
                                                       APIGatewayProxyResponseEvent response) 
            throws GameNotFoundException {
        String gameId = extractGameIdFromPath(request.getPath());
        
        List<Player> players = playerDao.getPlayersByGameId(gameId);
        if (players.isEmpty()) {
            throw new GameNotFoundException("No players found for game: " + gameId);
        }
        
        // Transform players for admin view (include sensitive data)
        List<Map<String, Object>> adminPlayerData = players.stream()
                .map(this::transformPlayerForAdmin)
                .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("gameId", gameId);
        result.put("players", adminPlayerData);
        result.put("totalCount", players.size());
        
        logger.info("Retrieved {} players for game: {}", players.size(), gameId);
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(result));
    }

    /**
     * GET /admin/games/{gameId}/analytics
     * Returns analytics and metrics for the game.
     */
    private APIGatewayProxyResponseEvent getGameAnalytics(APIGatewayProxyRequestEvent request, 
                                                         APIGatewayProxyResponseEvent response) 
            throws GameNotFoundException {
        String gameId = extractGameIdFromPath(request.getPath());
        
        List<Player> players = playerDao.getPlayersByGameId(gameId);
        if (players.isEmpty()) {
            throw new GameNotFoundException("No players found for game: " + gameId);
        }
        
        Map<String, Object> analytics = new HashMap<>();
        
        // Player distribution by status
        Map<String, Long> statusDistribution = players.stream()
                .collect(Collectors.groupingBy(Player::getStatus, Collectors.counting()));
        analytics.put("statusDistribution", statusDistribution);
        
        // Kill statistics
        Map<String, Object> killStats = new HashMap<>();
        killStats.put("totalKills", players.stream().mapToInt(Player::getKillCount).sum());
        killStats.put("averageKills", players.stream().mapToInt(Player::getKillCount).average().orElse(0.0));
        killStats.put("topKiller", players.stream()
                .max((p1, p2) -> Integer.compare(p1.getKillCount(), p2.getKillCount()))
                .map(Player::getPlayerName)
                .orElse("None"));
        analytics.put("killStatistics", killStats);
        
        // Location analytics
        Map<String, Object> locationStats = new HashMap<>();
        long playersWithLocation = players.stream()
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .count();
        locationStats.put("playersWithLocation", playersWithLocation);
        locationStats.put("locationCoverage", players.isEmpty() ? 0 : (double) playersWithLocation / players.size());
        analytics.put("locationStatistics", locationStats);
        
        // Game duration analytics
        Optional<Game> gameOpt = gameDao.getGameById(gameId);
        if (gameOpt.isPresent()) {
            Game game = gameOpt.get();
            if (game.getStartTime() != null) {
                Instant startTime = Instant.parse(game.getStartTime());
                Instant currentTime = game.getEndTime() != null ? Instant.parse(game.getEndTime()) : Instant.now();
                long durationMinutes = ChronoUnit.MINUTES.between(startTime, currentTime);
                
                Map<String, Object> durationStats = new HashMap<>();
                durationStats.put("durationMinutes", durationMinutes);
                durationStats.put("durationHours", durationMinutes / 60.0);
                durationStats.put("isActive", game.getEndTime() == null);
                analytics.put("durationStatistics", durationStats);
            }
        }
        
        logger.info("Generated analytics for game: {}", gameId);
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(analytics));
    }

    /**
     * GET /admin/games
     * Returns a list of all games with summary information.
     */
    private APIGatewayProxyResponseEvent getAllGames(APIGatewayProxyRequestEvent request, 
                                                    APIGatewayProxyResponseEvent response) {
        List<Game> games = gameDao.getAllGames();
        
        List<Map<String, Object>> gamesSummary = games.stream()
                .map(this::transformGameForAdmin)
                .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("games", gamesSummary);
        result.put("totalCount", games.size());
        
        logger.info("Retrieved {} games for admin", games.size());
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(result));
    }

    /**
     * GET /admin/system/health
     * Returns system health and status information.
     */
    private APIGatewayProxyResponseEvent getSystemHealth(APIGatewayProxyRequestEvent request, 
                                                        APIGatewayProxyResponseEvent response) {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("timestamp", Instant.now().toString());
        health.put("version", "1.0.0");
        
        // Add basic system metrics
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalGames", gameDao.getAllGames().size());
        metrics.put("activeGames", gameDao.getAllGames().stream()
                .filter(g -> "ACTIVE".equals(g.getStatus()))
                .count());
        health.put("metrics", metrics);
        
        logger.info("System health check completed");
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(health));
    }

    /**
     * POST /admin/games/{gameId}/actions/pause
     * Pauses an active game.
     */
    private APIGatewayProxyResponseEvent pauseGame(APIGatewayProxyRequestEvent request, 
                                                  APIGatewayProxyResponseEvent response) 
            throws GameNotFoundException {
        String gameId = extractGameIdFromPath(request.getPath());
        
        // Implementation would call GameService to pause the game
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Game pause functionality not yet implemented");
        result.put("gameId", gameId);
        
        logger.info("Pause requested for game: {}", gameId);
        return response
                .withStatusCode(501)
                .withBody(gson.toJson(result));
    }

    /**
     * POST /admin/games/{gameId}/actions/resume
     * Resumes a paused game.
     */
    private APIGatewayProxyResponseEvent resumeGame(APIGatewayProxyRequestEvent request, 
                                                   APIGatewayProxyResponseEvent response) 
            throws GameNotFoundException {
        String gameId = extractGameIdFromPath(request.getPath());
        
        // Implementation would call GameService to resume the game
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Game resume functionality not yet implemented");
        result.put("gameId", gameId);
        
        logger.info("Resume requested for game: {}", gameId);
        return response
                .withStatusCode(501)
                .withBody(gson.toJson(result));
    }

    /**
     * POST /admin/games/{gameId}/actions/end
     * Ends an active game.
     */
    private APIGatewayProxyResponseEvent endGame(APIGatewayProxyRequestEvent request, 
                                                APIGatewayProxyResponseEvent response) 
            throws GameNotFoundException {
        String gameId = extractGameIdFromPath(request.getPath());
        
        // Implementation would call GameService to end the game
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Game end functionality not yet implemented");
        result.put("gameId", gameId);
        
        logger.info("End requested for game: {}", gameId);
        return response
                .withStatusCode(501)
                .withBody(gson.toJson(result));
    }

    // Helper methods

    private boolean isAdminRequest(APIGatewayProxyRequestEvent request) {
        // Simplified admin check - in production would verify JWT claims or roles
        Map<String, Object> requestContext = request.getRequestContext();
        if (requestContext != null && requestContext.containsKey("authorizer")) {
            // For now, just check if user is authenticated
            return HandlerUtils.getPlayerIdFromRequest(request).isPresent();
        }
        return false;
    }

    private String extractGameIdFromPath(String path) {
        String[] pathParts = path.split("/");
        if (pathParts.length >= 4 && "admin".equals(pathParts[1]) && "games".equals(pathParts[2])) {
            return pathParts[3];
        }
        throw new ValidationException("Invalid path format - gameId not found");
    }

    private List<Map<String, Object>> generateTimelineEvents(Game game, List<Player> players) {
        List<Map<String, Object>> events = new ArrayList<>();
        
        // Game start event
        if (game.getStartTime() != null) {
            Map<String, Object> startEvent = new HashMap<>();
            startEvent.put("type", "GAME_START");
            startEvent.put("timestamp", game.getStartTime());
            startEvent.put("description", "Game started");
            startEvent.put("playerCount", players.size());
            events.add(startEvent);
        }
        
        // Player elimination events (simplified - would need kill history)
        long eliminatedCount = players.stream()
                .filter(p -> PlayerStatus.DEAD.name().equals(p.getStatus()))
                .count();
        
        if (eliminatedCount > 0) {
            Map<String, Object> eliminationEvent = new HashMap<>();
            eliminationEvent.put("type", "ELIMINATIONS");
            eliminationEvent.put("timestamp", Instant.now().toString());
            eliminationEvent.put("description", eliminatedCount + " players eliminated");
            eliminationEvent.put("eliminatedCount", eliminatedCount);
            events.add(eliminationEvent);
        }
        
        // Game end event
        if (game.getEndTime() != null) {
            Map<String, Object> endEvent = new HashMap<>();
            endEvent.put("type", "GAME_END");
            endEvent.put("timestamp", game.getEndTime());
            endEvent.put("description", "Game ended");
            events.add(endEvent);
        }
        
        return events;
    }

    private Map<String, Object> generateCurrentStats(List<Player> players) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPlayers", players.size());
        stats.put("activePlayers", players.stream().filter(p -> PlayerStatus.ACTIVE.name().equals(p.getStatus())).count());
        stats.put("eliminatedPlayers", players.stream().filter(p -> PlayerStatus.DEAD.name().equals(p.getStatus())).count());
        stats.put("totalKills", players.stream().mapToInt(Player::getKillCount).sum());
        return stats;
    }

    private List<Map<String, Object>> generateRecentActivity(List<Player> players) {
        List<Map<String, Object>> activity = new ArrayList<>();
        
        // Add recent location updates (simplified)
        players.stream()
                .filter(p -> p.getLocationTimestamp() != null)
                .limit(5)
                .forEach(p -> {
                    Map<String, Object> locationUpdate = new HashMap<>();
                    locationUpdate.put("type", "LOCATION_UPDATE");
                    locationUpdate.put("playerName", p.getPlayerName());
                    locationUpdate.put("timestamp", p.getLocationTimestamp());
                    activity.add(locationUpdate);
                });
        
        return activity;
    }

    private Map<String, Object> transformPlayerForAdmin(Player player) {
        Map<String, Object> adminPlayer = new HashMap<>();
        adminPlayer.put("playerId", player.getPlayerID());
        adminPlayer.put("playerName", player.getPlayerName());
        adminPlayer.put("email", player.getEmail());
        adminPlayer.put("status", player.getStatus());
        adminPlayer.put("killCount", player.getKillCount());
        adminPlayer.put("targetId", player.getTargetID());
        adminPlayer.put("targetName", player.getTargetName());
        adminPlayer.put("latitude", player.getLatitude());
        adminPlayer.put("longitude", player.getLongitude());
        adminPlayer.put("locationTimestamp", player.getLocationTimestamp());
        adminPlayer.put("locationSharingEnabled", player.getLocationSharingEnabled());
        return adminPlayer;
    }

    private Map<String, Object> transformGameForAdmin(Game game) {
        Map<String, Object> adminGame = new HashMap<>();
        adminGame.put("gameId", game.getGameID());
        adminGame.put("gameName", game.getGameName());
        adminGame.put("status", game.getStatus());
        adminGame.put("startTime", game.getStartTime());
        adminGame.put("endTime", game.getEndTime());
        adminGame.put("maxPlayers", game.getMaxPlayers());
        adminGame.put("entryFee", game.getEntryFee());
        return adminGame;
    }
} 