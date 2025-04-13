package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.exception.PersistenceException;
import com.assassin.exception.ValidationException;
import com.assassin.exception.SafeZoneNotFoundException;
import com.assassin.exception.UnauthorizedException;
import com.assassin.model.SafeZone;
import com.assassin.service.SafeZoneService;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SafeZoneHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(SafeZoneHandler.class);
    private static final Gson gson = new GsonBuilder().create();
    private final SafeZoneService safeZoneService;

    public SafeZoneHandler() {
        this(new SafeZoneService());
    }

    // Constructor for dependency injection
    public SafeZoneHandler(SafeZoneService safeZoneService) {
        this.safeZoneService = safeZoneService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(HandlerUtils.getResponseHeaders());

        logger.info("SafeZoneHandler received request: Method={}, Path={}", httpMethod, path);

        try {
            // Routing
            if ("POST".equals(httpMethod) && path.matches("/games/[^/]+/safezones")) {
                return createSafeZone(request, response);
            } else if ("GET".equals(httpMethod) && path.matches("/games/[^/]+/safezones")) {
                return getSafeZonesByGame(request, response);
            } else if ("GET".equals(httpMethod) && path.matches("/safezones/[^/]+")) {
                return getSafeZoneById(request, response);
            } else if ("PUT".equals(httpMethod) && path.matches("/safezones/[^/]+")) {
                return updateSafeZone(request, response);
            } else if ("DELETE".equals(httpMethod) && path.matches("/safezones/[^/]+")) {
                 return deleteSafeZone(request, response);
            } else {
                logger.warn("Route not found in SafeZoneHandler: {} {}", httpMethod, path);
                return response.withStatusCode(404).withBody(gson.toJson(Map.of("message", "Route not found")));
            }
        } catch (ValidationException e) {
            logger.warn("Validation error processing safe zone request: {}", e.getMessage());
            return response.withStatusCode(400).withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (PersistenceException e) {
             logger.error("Persistence error processing safe zone request: {}", e.getMessage(), e);
             return response.withStatusCode(500).withBody(gson.toJson(Map.of("message", "Database error", "error", e.getClass().getSimpleName())));
        } catch (SafeZoneNotFoundException e) {
            logger.warn("Safe zone not found: {}", e.getMessage());
            return response.withStatusCode(404).withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (UnauthorizedException e) {
            logger.warn("Authorization failed: {}", e.getMessage());
            return response.withStatusCode(403).withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (Exception e) {
            logger.error("Error processing safe zone request: {}", e.getMessage(), e);
            return response.withStatusCode(500).withBody(gson.toJson(Map.of("message", "Internal Server Error", "error", e.getClass().getSimpleName())));
        }
    }

    // POST /games/{gameId}/safezones
    private APIGatewayProxyResponseEvent createSafeZone(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) {
        try {
            String gameId = request.getPathParameters().get("gameId");
            SafeZone inputZone = gson.fromJson(request.getBody(), SafeZone.class);

            if (inputZone == null) {
                 throw new ValidationException("Request body cannot be empty.");
            }
            inputZone.setGameId(gameId); // Ensure gameId from path is set

            SafeZone createdZone = safeZoneService.createSafeZone(inputZone);
            return response.withStatusCode(201).withBody(gson.toJson(createdZone));

        } catch (JsonSyntaxException e) {
            logger.warn("Failed to parse create safe zone JSON: {}", e.getMessage());
            return response.withStatusCode(400).withBody(gson.toJson(Map.of("message", "Invalid JSON format in request body")));
        }
        // ValidationException and PersistenceException caught by main handler
    }

    // GET /games/{gameId}/safezones
    private APIGatewayProxyResponseEvent getSafeZonesByGame(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) {
        String gameId = request.getPathParameters().get("gameId");
        if (gameId == null || gameId.isEmpty()) {
            return response.withStatusCode(400).withBody(gson.toJson(Map.of("message", "Missing gameId path parameter")));
        }
        List<SafeZone> safeZones = safeZoneService.getSafeZonesForGame(gameId);
        return response.withStatusCode(200).withBody(gson.toJson(safeZones));
    }

    // GET /safezones/{safeZoneId}
    private APIGatewayProxyResponseEvent getSafeZoneById(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) {
        String safeZoneId = request.getPathParameters().get("safeZoneId");
         if (safeZoneId == null || safeZoneId.isEmpty()) {
            return response.withStatusCode(400).withBody(gson.toJson(Map.of("message", "Missing safeZoneId path parameter")));
        }
        Optional<SafeZone> safeZoneOpt = safeZoneService.getSafeZone(safeZoneId);
        return safeZoneOpt
                .map(sz -> response.withStatusCode(200).withBody(gson.toJson(sz)))
                .orElseGet(() -> response.withStatusCode(404).withBody(gson.toJson(Map.of("message", "Safe zone not found"))));
    }

    // PUT /safezones/{safeZoneId}
    private APIGatewayProxyResponseEvent updateSafeZone(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response)
            throws SafeZoneNotFoundException, ValidationException, UnauthorizedException {
        String safeZoneId = request.getPathParameters().get("safeZoneId");
        String requestingPlayerId = HandlerUtils.getPlayerIdFromRequest(request);

        if (safeZoneId == null || safeZoneId.isEmpty()) {
           return response.withStatusCode(400).withBody(gson.toJson(Map.of("message", "Missing safeZoneId path parameter")));
        }
        
        try {
            SafeZone updates = gson.fromJson(request.getBody(), SafeZone.class);
            if (updates == null) {
                 throw new ValidationException("Request body cannot be empty.");
            }
            // Ensure ID from path matches body if present, or ignore body ID
            // updates.setSafeZoneId(safeZoneId); // Service layer should handle this

            SafeZone updatedZone = safeZoneService.updateSafeZone(safeZoneId, updates, requestingPlayerId);
            return response.withStatusCode(200).withBody(gson.toJson(updatedZone));

        } catch (JsonSyntaxException e) {
            logger.warn("Failed to parse update safe zone JSON: {}", e.getMessage());
            return response.withStatusCode(400).withBody(gson.toJson(Map.of("message", "Invalid JSON format in request body")));
        }
        // Other exceptions (Validation, NotFound, Unauthorized, Persistence) caught by main handler
    }

    // DELETE /safezones/{safeZoneId}
    private APIGatewayProxyResponseEvent deleteSafeZone(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) {
        String safeZoneId = request.getPathParameters().get("safeZoneId");
         if (safeZoneId == null || safeZoneId.isEmpty()) {
            return response.withStatusCode(400).withBody(gson.toJson(Map.of("message", "Missing safeZoneId path parameter")));
        }
        // Add authentication/authorization checks here - who can delete?
        safeZoneService.deleteSafeZone(safeZoneId);
        // Return 204 No Content on successful deletion
        return response.withStatusCode(204);
    }
} 