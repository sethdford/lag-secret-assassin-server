package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.dao.GameDao;
import com.assassin.dao.GameZoneStateDao;
import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbGameZoneStateDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GameStateException;
import com.assassin.model.Game;
import com.assassin.model.GameZoneState;
import com.assassin.service.ShrinkingZoneService;
import com.assassin.util.ApiGatewayResponseBuilder;
import com.assassin.util.RequestUtils;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lambda handler for zone state API endpoints.
 * Provides endpoints to query current shrinking zone state (center, radius, timer).
 */
public class ZoneStateHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ZoneStateHandler.class);
    private static final Gson gson = new Gson();
    
    private final GameDao gameDao;
    private final GameZoneStateDao gameZoneStateDao;
    private final ShrinkingZoneService shrinkingZoneService;

    public ZoneStateHandler() {
        this.gameDao = new DynamoDbGameDao();
        this.gameZoneStateDao = new DynamoDbGameZoneStateDao();
        this.shrinkingZoneService = new ShrinkingZoneService(gameDao, gameZoneStateDao, null); // No PlayerDao needed for queries
    }

    public ZoneStateHandler(GameDao gameDao, GameZoneStateDao gameZoneStateDao, ShrinkingZoneService shrinkingZoneService) {
        this.gameDao = gameDao;
        this.gameZoneStateDao = gameZoneStateDao;
        this.shrinkingZoneService = shrinkingZoneService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String path = request.getPath();
        String httpMethod = request.getHttpMethod();
        logger.info("ZoneStateHandler received request: Method={}, Path={}", httpMethod, path);

        try {
            // GET /games/{gameId}/zone/state
            if ("GET".equals(httpMethod) && path.matches("/games/[^/]+/zone/state")) {
                return getZoneState(request);
            }
            // GET /games/{gameId}/zone/status
            else if ("GET".equals(httpMethod) && path.matches("/games/[^/]+/zone/status")) {
                return getZoneStatus(request);
            }
            else {
                logger.warn("Route not found in ZoneStateHandler: {} {}", httpMethod, path);
                return ApiGatewayResponseBuilder.buildErrorResponse(404, "Zone state endpoint not found");
            }
        } catch (GameNotFoundException e) {
            logger.warn("Game not found: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(404, "Game not found: " + e.getMessage());
        } catch (GameStateException e) {
            logger.error("Game state error: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Zone state error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error processing zone state request: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Internal server error");
        }
    }

    /**
     * GET /games/{gameId}/zone/state
     * Returns detailed zone state including center, radius, phase, timers, and stage info.
     */
    private APIGatewayProxyResponseEvent getZoneState(APIGatewayProxyRequestEvent request) 
            throws GameNotFoundException, GameStateException {
        String gameId = request.getPathParameters().get("gameId");
        if (gameId == null || gameId.isEmpty()) {
            return ApiGatewayResponseBuilder.buildErrorResponse(400, "Missing gameId path parameter");
        }

        logger.info("Getting zone state for game: {}", gameId);

        // Get game and check if shrinking zone is enabled
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found: " + gameId));

        if (Boolean.FALSE.equals(game.getShrinkingZoneEnabled())) {
            logger.info("Shrinking zone not enabled for game {}.", gameId);
            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(Map.of(
                "gameId", gameId,
                "shrinkingZoneEnabled", false,
                "message", "Shrinking zone is not enabled for this game"
            )));
        }

        // Get current zone state (this will advance state if needed)
        Optional<GameZoneState> stateOpt = shrinkingZoneService.advanceZoneState(gameId);
        if (stateOpt.isEmpty()) {
            return ApiGatewayResponseBuilder.buildErrorResponse(404, "Zone state not found for game: " + gameId);
        }

        GameZoneState state = stateOpt.get();
        
        // Calculate time remaining for current phase
        long timeRemainingSeconds = 0;
        if (state.getPhaseEndTime() != null) {
            try {
                Instant phaseEndTime = Instant.parse(state.getPhaseEndTime());
                Instant now = Instant.now();
                timeRemainingSeconds = Math.max(0, phaseEndTime.getEpochSecond() - now.getEpochSecond());
            } catch (Exception e) {
                logger.warn("Could not parse phase end time: {}", state.getPhaseEndTime());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("gameId", gameId);
        response.put("shrinkingZoneEnabled", true);
        response.put("currentStageIndex", state.getCurrentStageIndex());
        response.put("currentPhase", state.getCurrentPhase());
        response.put("currentRadiusMeters", state.getCurrentRadiusMeters());
        
        if (state.getCurrentCenter() != null) {
            Map<String, Double> center = new HashMap<>();
            center.put("latitude", state.getCurrentCenter().getLatitude());
            center.put("longitude", state.getCurrentCenter().getLongitude());
            response.put("currentCenter", center);
        }
        
        response.put("phaseEndTime", state.getPhaseEndTime());
        response.put("timeRemainingSeconds", timeRemainingSeconds);
        response.put("lastUpdated", state.getLastUpdated());
        
        // Include next radius if available
        if (state.getNextRadiusMeters() != null) {
            response.put("nextRadiusMeters", state.getNextRadiusMeters());
        }

        logger.info("Successfully retrieved zone state for game {}: phase={}, radius={}m, timeRemaining={}s", 
                   gameId, state.getCurrentPhase(), state.getCurrentRadiusMeters(), timeRemainingSeconds);

        return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));
    }

    /**
     * GET /games/{gameId}/zone/status
     * Returns simplified zone status summary for client UI.
     */
    private APIGatewayProxyResponseEvent getZoneStatus(APIGatewayProxyRequestEvent request) 
            throws GameNotFoundException, GameStateException {
        String gameId = request.getPathParameters().get("gameId");
        if (gameId == null || gameId.isEmpty()) {
            return ApiGatewayResponseBuilder.buildErrorResponse(400, "Missing gameId path parameter");
        }

        logger.info("Getting zone status for game: {}", gameId);

        // Get game and check if shrinking zone is enabled
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found: " + gameId));

        if (Boolean.FALSE.equals(game.getShrinkingZoneEnabled())) {
            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(Map.of(
                "gameId", gameId,
                "shrinkingZoneEnabled", false,
                "status", "disabled"
            )));
        }

        // Get current zone center and radius
        Optional<com.assassin.model.Coordinate> centerOpt = shrinkingZoneService.getCurrentZoneCenter(gameId);
        Optional<Double> radiusOpt = shrinkingZoneService.getCurrentZoneRadius(gameId);

        Map<String, Object> response = new HashMap<>();
        response.put("gameId", gameId);
        response.put("shrinkingZoneEnabled", true);

        if (centerOpt.isPresent() && radiusOpt.isPresent()) {
            response.put("status", "active");
            
            Map<String, Double> center = new HashMap<>();
            center.put("latitude", centerOpt.get().getLatitude());
            center.put("longitude", centerOpt.get().getLongitude());
            response.put("currentCenter", center);
            response.put("currentRadiusMeters", radiusOpt.get());
            
            // Get zone state for phase info
            Optional<GameZoneState> stateOpt = gameZoneStateDao.getGameZoneState(gameId);
            if (stateOpt.isPresent()) {
                GameZoneState state = stateOpt.get();
                response.put("currentPhase", state.getCurrentPhase());
                response.put("currentStageIndex", state.getCurrentStageIndex());
                
                // Calculate time remaining
                if (state.getPhaseEndTime() != null) {
                    try {
                        Instant phaseEndTime = Instant.parse(state.getPhaseEndTime());
                        Instant now = Instant.now();
                        long timeRemainingSeconds = Math.max(0, phaseEndTime.getEpochSecond() - now.getEpochSecond());
                        response.put("timeRemainingSeconds", timeRemainingSeconds);
                    } catch (Exception e) {
                        logger.warn("Could not parse phase end time: {}", state.getPhaseEndTime());
                    }
                }
            }
        } else {
            response.put("status", "not_available");
            response.put("message", "Zone state not yet initialized or game ended");
        }

        logger.info("Successfully retrieved zone status for game {}: status={}", gameId, response.get("status"));

        return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));
    }
} 