package com.assassin.handlers;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GameStateException;
import com.assassin.exception.UnauthorizedException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Game;
import com.assassin.service.EmergencyService;
import com.assassin.util.ApiGatewayResponseBuilder;
import com.assassin.util.RequestUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for emergency operations including game pause, resume, and status checks.
 * Provides safety mechanisms for immediate game control.
 */
public class EmergencyHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(EmergencyHandler.class);
    private static final Gson gson = new Gson();
    private final EmergencyService emergencyService;

    // Default constructor
    public EmergencyHandler() {
        this.emergencyService = new EmergencyService();
    }

    // Constructor for dependency injection (testing)
    public EmergencyHandler(EmergencyService emergencyService) {
        this.emergencyService = emergencyService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String method = request.getHttpMethod();
        String path = request.getPath();
        logger.info("Processing emergency request: {} {}", method, path);

        try {
            Map<String, String> pathParameters = request.getPathParameters();
            if (pathParameters == null || !pathParameters.containsKey("gameId")) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Missing required gameId path parameter");
            }

            String gameId = pathParameters.get("gameId");
            
            // Route to appropriate handler based on path and method
            if (path.endsWith("/emergency/pause") && "POST".equals(method)) {
                return handlePauseGame(request, gameId, context);
            } else if (path.endsWith("/emergency/resume") && "POST".equals(method)) {
                return handleResumeGame(request, gameId, context);
            } else if (path.endsWith("/emergency/status") && "GET".equals(method)) {
                return handleGetEmergencyStatus(request, gameId, context);
            } else {
                return ApiGatewayResponseBuilder.buildErrorResponse(404, "Emergency endpoint not found");
            }

        } catch (Exception e) {
            logger.error("Unexpected error in emergency handler: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Internal server error");
        }
    }

    /**
     * Handles emergency pause requests.
     * POST /games/{gameId}/emergency/pause
     */
    private APIGatewayProxyResponseEvent handlePauseGame(APIGatewayProxyRequestEvent request, String gameId, Context context) {
        logger.info("Handling emergency pause request for game {}", gameId);

        try {
            // Extract requesting player ID from authentication context
            String requestingPlayerId = RequestUtils.getPlayerIdFromRequest(request);
            if (requestingPlayerId == null || requestingPlayerId.trim().isEmpty()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(401, "Authentication required");
            }

            // Parse request body for reason
            String reason = extractEmergencyReason(request.getBody());
            if (reason == null || reason.trim().isEmpty()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Emergency reason is required");
            }

            // Call emergency service to pause the game
            Game updatedGame = emergencyService.pauseGame(gameId, reason, requestingPlayerId);

            // Build response with emergency status
            JsonObject response = new JsonObject();
            response.addProperty("message", "Game paused successfully due to emergency");
            response.addProperty("gameId", updatedGame.getGameID());
            response.addProperty("emergencyPause", updatedGame.getEmergencyPause());
            response.addProperty("emergencyReason", updatedGame.getEmergencyReason());
            response.addProperty("emergencyTimestamp", updatedGame.getEmergencyTimestamp());
            response.addProperty("emergencyTriggeredBy", updatedGame.getEmergencyTriggeredBy());

            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));

        } catch (ValidationException e) {
            logger.warn("Validation error in emergency pause: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(400, e.getMessage());
        } catch (UnauthorizedException e) {
            logger.warn("Unauthorized emergency pause attempt: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(403, e.getMessage());
        } catch (GameNotFoundException e) {
            logger.warn("Game not found for emergency pause: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(404, e.getMessage());
        } catch (GameStateException e) {
            logger.warn("Invalid game state for emergency pause: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(409, e.getMessage());
        } catch (Exception e) {
            logger.error("Error processing emergency pause: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Failed to pause game");
        }
    }

    /**
     * Handles emergency resume requests.
     * POST /games/{gameId}/emergency/resume
     */
    private APIGatewayProxyResponseEvent handleResumeGame(APIGatewayProxyRequestEvent request, String gameId, Context context) {
        logger.info("Handling emergency resume request for game {}", gameId);

        try {
            // Extract requesting player ID from authentication context
            String requestingPlayerId = RequestUtils.getPlayerIdFromRequest(request);
            if (requestingPlayerId == null || requestingPlayerId.trim().isEmpty()) {
                return ApiGatewayResponseBuilder.buildErrorResponse(401, "Authentication required");
            }

            // Call emergency service to resume the game
            Game updatedGame = emergencyService.resumeGame(gameId, requestingPlayerId);

            // Build response
            JsonObject response = new JsonObject();
            response.addProperty("message", "Game resumed successfully from emergency pause");
            response.addProperty("gameId", updatedGame.getGameID());
            response.addProperty("emergencyPause", updatedGame.getEmergencyPause());

            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));

        } catch (ValidationException e) {
            logger.warn("Validation error in emergency resume: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(400, e.getMessage());
        } catch (UnauthorizedException e) {
            logger.warn("Unauthorized emergency resume attempt: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(403, e.getMessage());
        } catch (GameNotFoundException e) {
            logger.warn("Game not found for emergency resume: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(404, e.getMessage());
        } catch (GameStateException e) {
            logger.warn("Invalid game state for emergency resume: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(409, e.getMessage());
        } catch (Exception e) {
            logger.error("Error processing emergency resume: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Failed to resume game");
        }
    }

    /**
     * Handles emergency status requests.
     * GET /games/{gameId}/emergency/status
     */
    private APIGatewayProxyResponseEvent handleGetEmergencyStatus(APIGatewayProxyRequestEvent request, String gameId, Context context) {
        logger.info("Handling emergency status request for game {}", gameId);

        try {
            // Get emergency status
            EmergencyService.EmergencyStatus status = emergencyService.getEmergencyStatus(gameId);

            // Build response
            JsonObject response = new JsonObject();
            response.addProperty("gameId", status.getGameId());
            response.addProperty("isInEmergencyPause", status.isInEmergencyPause());
            response.addProperty("reason", status.getReason());
            response.addProperty("timestamp", status.getTimestamp());
            response.addProperty("triggeredBy", status.getTriggeredBy());

            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));

        } catch (ValidationException e) {
            logger.warn("Validation error in emergency status: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(400, e.getMessage());
        } catch (GameNotFoundException e) {
            logger.warn("Game not found for emergency status: {}", e.getMessage());
            return ApiGatewayResponseBuilder.buildErrorResponse(404, e.getMessage());
        } catch (Exception e) {
            logger.error("Error getting emergency status: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Failed to get emergency status");
        }
    }

    /**
     * Extracts the emergency reason from the request body.
     * Expected format: {"reason": "emergency description"}
     */
    private String extractEmergencyReason(String requestBody) {
        if (requestBody == null || requestBody.trim().isEmpty()) {
            return null;
        }

        try {
            JsonObject jsonObject = gson.fromJson(requestBody, JsonObject.class);
            if (jsonObject.has("reason") && jsonObject.get("reason").isJsonPrimitive()) {
                return jsonObject.get("reason").getAsString();
            }
        } catch (JsonSyntaxException e) {
            logger.warn("Invalid JSON in request body: {}", e.getMessage());
        }

        return null;
    }
} 