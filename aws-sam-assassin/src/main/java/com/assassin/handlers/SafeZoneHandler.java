package com.assassin.handlers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.exception.PersistenceException;
import com.assassin.exception.SafeZoneNotFoundException;
import com.assassin.exception.UnauthorizedException;
import com.assassin.exception.ValidationException;
import com.assassin.model.SafeZone;
import com.assassin.service.SafeZoneService;
import com.assassin.util.GsonUtil;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class SafeZoneHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(SafeZoneHandler.class);
    private static final Gson gson = GsonUtil.getGson();
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
            } else if ("PUT".equals(httpMethod) && path.matches("/games/[^/]+/safezones/[^/]+/location")) {
                return relocateSafeZoneHandler(request, response);
            } else if ("GET".equals(httpMethod) && path.matches("/players/[^/]+/safezones")) {
                return getPlayerOwnedSafeZones(request, response);
            } else if ("GET".equals(httpMethod) && path.matches("/games/[^/]+/safezones/active")) {
                return getActiveGameSafeZones(request, response);
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
            String requestBody = request.getBody();
            if (requestBody == null || requestBody.isEmpty()) {
                 throw new ValidationException("Request body cannot be empty.");
            }

            // Parse the body to get the type first
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyMap = gson.fromJson(requestBody, Map.class);
            String typeString = (String) bodyMap.get("type");
            if (typeString == null) {
                throw new ValidationException("Safe zone 'type' is required in the request body.");
            }

            SafeZone.SafeZoneType type;
            try {
                type = SafeZone.SafeZoneType.valueOf(typeString.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Invalid safe zone type: " + typeString);
            }

            // Now deserialize to SafeZone knowing it might have extra fields
            SafeZone inputZone = gson.fromJson(requestBody, SafeZone.class); // gson will ignore extra fields not in SafeZone base
            inputZone.setGameId(gameId); // Ensure gameId from path is set
            inputZone.setType(type); // Explicitly set type from parsed value

            // Get common fields, createdBy might come from request or Cognito context
            String createdBy = HandlerUtils.getPlayerIdFromRequest(request)
                                .orElse((String) bodyMap.getOrDefault("createdBy", null));
            if (createdBy == null || createdBy.isEmpty()) {
                 // For PUBLIC zones created by admin, createdBy might be in body.
                 // For PRIVATE/RELOCATABLE, usually the cognito user.
                 // This logic might need refinement based on auth rules.
                 throw new ValidationException("'createdBy' is required, either from Cognito context or request body.");
            }
            inputZone.setCreatedBy(createdBy);

            SafeZone createdZone;
            switch (type) {
                case PUBLIC:
                    createdZone = safeZoneService.createPublicSafeZone(gameId, inputZone.getName(), inputZone.getDescription(),
                                                                 inputZone.getLatitude(), inputZone.getLongitude(), 
                                                                 inputZone.getRadiusMeters(), createdBy);
                    break;
                case PRIVATE:
                    @SuppressWarnings("unchecked")
                    Set<String> authorizedPlayerIds = bodyMap.containsKey("authorizedPlayerIds") ? 
                                                        new HashSet<>((List<String>) bodyMap.get("authorizedPlayerIds")) : 
                                                        new HashSet<>();
                    createdZone = safeZoneService.createPrivateSafeZone(gameId, inputZone.getName(), inputZone.getDescription(),
                                                                  inputZone.getLatitude(), inputZone.getLongitude(), 
                                                                  inputZone.getRadiusMeters(), createdBy, authorizedPlayerIds);
                    break;
                case TIMED:
                    String startTime = (String) bodyMap.get("startTime");
                    String endTime = (String) bodyMap.get("endTime");
                    createdZone = safeZoneService.createTimedSafeZone(gameId, inputZone.getName(), inputZone.getDescription(),
                                                                inputZone.getLatitude(), inputZone.getLongitude(), 
                                                                inputZone.getRadiusMeters(), createdBy, startTime, endTime);
                    break;
                case RELOCATABLE:
                    createdZone = safeZoneService.createRelocatableSafeZone(gameId, inputZone.getName(), inputZone.getDescription(),
                                                                    inputZone.getLatitude(), inputZone.getLongitude(), 
                                                                    inputZone.getRadiusMeters(), createdBy);
                    break;
                default:
                    throw new ValidationException("Unsupported safe zone type: " + type);
            }
            
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

        Map<String, String> queryParams = request.getQueryStringParameters();
        List<SafeZone> safeZones;

        String typeFilter = null;
        boolean activeOnly = false;

        if (queryParams != null) {
            typeFilter = queryParams.get("type");
            activeOnly = Boolean.parseBoolean(queryParams.getOrDefault("activeOnly", "false"));
        }

        if (typeFilter != null && !typeFilter.isEmpty()) {
            SafeZone.SafeZoneType typeEnum;
            try {
                typeEnum = SafeZone.SafeZoneType.valueOf(typeFilter.toUpperCase());
            } catch (IllegalArgumentException e) {
                return response.withStatusCode(400).withBody(gson.toJson(Map.of("message", "Invalid safe zone type filter: " + typeFilter)));
            }
            logger.info("Fetching safe zones for game {} of type {}", gameId, typeEnum);
            safeZones = safeZoneService.getSafeZonesByType(gameId, typeEnum);
            // If activeOnly is also true, filter these results further
            if (activeOnly) {
                long currentTime = Instant.now().toEpochMilli();
                safeZones = safeZones.stream().filter(zone -> zone.isActiveAt(currentTime)).collect(java.util.stream.Collectors.toList());
            }
        } else if (activeOnly) {
            logger.info("Fetching active safe zones for game {}", gameId);
            safeZones = safeZoneService.getActiveZonesForGame(gameId, Instant.now().toEpochMilli());
        } else {
            logger.info("Fetching all safe zones for game {}", gameId);
            safeZones = safeZoneService.getSafeZonesForGame(gameId);
        }
        
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
        String requestingPlayerId = HandlerUtils.getPlayerIdFromRequest(request)
                .orElseThrow(() -> new ValidationException("Player ID not found in request context."));

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

            SafeZone updatedZone = safeZoneService.updateSafeZone(safeZoneId, updates);
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

    // PUT /games/{gameId}/safezones/{safeZoneId}/location
    private APIGatewayProxyResponseEvent relocateSafeZoneHandler(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response)
            throws SafeZoneNotFoundException, ValidationException, PersistenceException {
        String gameId = request.getPathParameters().get("gameId"); // gameId from path, useful for auth/context
        String safeZoneId = request.getPathParameters().get("safeZoneId");
        String requestingPlayerId = HandlerUtils.getPlayerIdFromRequest(request)
                .orElseThrow(() -> new ValidationException("Player ID not found in request context for relocation."));
        
        if (safeZoneId == null || safeZoneId.isEmpty()) {
           return response.withStatusCode(400).withBody(gson.toJson(Map.of("message", "Missing safeZoneId path parameter")));
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyMap = gson.fromJson(request.getBody(), Map.class);
            if (bodyMap == null || !bodyMap.containsKey("latitude") || !bodyMap.containsKey("longitude")) {
                throw new ValidationException("Request body must contain 'latitude' and 'longitude'.");
            }
            Double newLatitude = (Double) bodyMap.get("latitude");
            Double newLongitude = (Double) bodyMap.get("longitude");

            SafeZone relocatedZone = safeZoneService.relocateZone(safeZoneId, requestingPlayerId, newLatitude, newLongitude);
            return response.withStatusCode(200).withBody(gson.toJson(relocatedZone));
        } catch (JsonSyntaxException e) {
            logger.warn("Failed to parse relocate safe zone JSON: {}", e.getMessage());
            return response.withStatusCode(400).withBody(gson.toJson(Map.of("message", "Invalid JSON format in request body")));
        }
    }

    // GET /players/{playerId}/safezones
    private APIGatewayProxyResponseEvent getPlayerOwnedSafeZones(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) {
        String playerId = request.getPathParameters().get("playerId");
        // Optional: String gameIdQueryParam = request.getQueryStringParameters() != null ? request.getQueryStringParameters().get("gameId") : null;
        // For now, let's assume we want player's zones across all their games, or a specific game if provided.
        // The DAO method getSafeZonesByOwner needs a gameId. If we want all games, service layer needs to iterate or DAO needs a new method.
        // For simplicity, let's require a gameId query param for now, or fetch for a default/current game if applicable.
        // This example will assume a gameId is implicitly available or not strictly required by this endpoint logic yet.
        // A better approach: this endpoint should probably be /games/{gameId}/players/{playerId}/safezones
        // Or /games/{gameId}/safezones?ownerId={playerId}

        // For now, let's assume this implies fetching zones created by the player for a specific game obtained from context or query
        // Let's use a query parameter 'gameId' for this example.
        String gameId = request.getQueryStringParameters() != null ? request.getQueryStringParameters().get("gameId") : null;
        if (gameId == null || gameId.isEmpty()) {
             return response.withStatusCode(400).withBody(gson.toJson(Map.of("message", "Query parameter 'gameId' is required to fetch player-owned safe zones.")));
        }
        if (playerId == null || playerId.isEmpty()) {
            return response.withStatusCode(400).withBody(gson.toJson(Map.of("message", "Missing playerId path parameter")));
        }

        // Auth check: Does the requesting user have permission to see this player's zones?
        // String requestingPlayerId = HandlerUtils.getPlayerIdFromRequest(request).orElse(null);
        // if (requestingPlayerId == null || !requestingPlayerId.equals(playerId)) { // Basic check: can only see your own zones
        //     return response.withStatusCode(403).withBody(gson.toJson(Map.of("message", "Unauthorized to view this player\'s safe zones.")));
        // }

        List<SafeZone> safeZones = safeZoneService.getSafeZonesByOwner(gameId, playerId);
        return response.withStatusCode(200).withBody(gson.toJson(safeZones));
    }

    // GET /games/{gameId}/safezones/active
    private APIGatewayProxyResponseEvent getActiveGameSafeZones(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) {
        String gameId = request.getPathParameters().get("gameId");
        if (gameId == null || gameId.isEmpty()) {
            return response.withStatusCode(400).withBody(gson.toJson(Map.of("message", "Missing gameId path parameter")));
        }
        long currentTime = Instant.now().toEpochMilli();
        List<SafeZone> activeSafeZones = safeZoneService.getActiveZonesForGame(gameId, currentTime);
        return response.withStatusCode(200).withBody(gson.toJson(activeSafeZones));
    }
} 