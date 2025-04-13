package com.assassin.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.KillNotFoundException;
import com.assassin.exception.PlayerActionNotAllowedException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Kill;
import com.assassin.service.KillService;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

/**
 * Handler for Kill reporting API requests.
 */
public class KillHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(KillHandler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final KillService killService;

    // Default constructor initializes the service
    public KillHandler() {
        this.killService = new KillService();
    }

    // Constructor for dependency injection (testing)
    public KillHandler(KillService killService) {
        this.killService = killService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received kill request: Method={}, Path={}", request.getHttpMethod(), request.getPath());
        
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(HandlerUtils.getResponseHeaders());

        try {
            // Handle OPTIONS for CORS preflight requests
            if (HandlerUtils.isPreflightRequest(request)) {
                return response
                    .withStatusCode(200)
                    .withHeaders(HandlerUtils.getPreflightResponseHeaders());
            }
            
            // Route to appropriate handler method
            if ("POST".equals(httpMethod) && ("/die".equals(path) || "/kills".equals(path))) {
                return reportKill(request, response);
            } else if ("GET".equals(httpMethod) && path.matches("/kills/killer/.*")) {
                return getKillsByKiller(request, response);
            } else if ("GET".equals(httpMethod) && path.matches("/kills/victim/.*")) {
                return getKillsByVictim(request, response);
            } else if ("GET".equals(httpMethod) && path.matches("/kills/recent.*?")) {
                return getRecentKills(request, response);
            } else if ("POST".equals(httpMethod) && path.matches("/kills/.+/.+/verify")) {
                return verifyKill(request, response);
            } else if ("GET".equals(httpMethod) && path.matches("/games/.+/timeline")) {
                return getGameTimeline(request, response);
            } else {
                return response.withStatusCode(404)
                        .withBody(gson.toJson(Map.of("message", "Route not found: " + path)));
            }
        } catch (ValidationException e) {
            logger.warn("Validation failed processing kill request: {}", e.getMessage());
            return response.withStatusCode(400).withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (JsonSyntaxException e) {
            logger.error("Invalid JSON input for kill request: {}", e.getMessage());
            return response.withStatusCode(400).withBody(gson.toJson(Map.of("message", "Invalid JSON format")));
        } catch (KillNotFoundException | PlayerActionNotAllowedException e) {
            logger.warn("Kill verification failed: {}", e.getMessage());
            return response.withStatusCode(404).withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (Exception e) {
            logger.error("Internal server error processing kill request", e);
            return response.withStatusCode(500).withBody(gson.toJson(Map.of("message", "Internal Server Error")));
        }
    }

    private APIGatewayProxyResponseEvent reportKill(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) throws ValidationException {
        String killerId = HandlerUtils.getPlayerIdFromRequest(request);
        
        // Parse the entire request body into a Map
        Map<String, Object> requestBodyMap = gson.fromJson(request.getBody(), new TypeToken<Map<String, Object>>(){}.getType());

        // For testing, if we can't get the killer ID from the auth context, try using the one from the request body
        if (killerId == null) {
            killerId = (String) requestBodyMap.get("killerID");
        }
        
        // Extract fields from the Map
        String victimId = (String) requestBodyMap.get("victimID");
        Double latitude = (Double) requestBodyMap.get("latitude");
        Double longitude = (Double) requestBodyMap.get("longitude");
        String verificationMethod = (String) requestBodyMap.get("verificationMethod");
        
        // Extract verificationData (it's already a Map in the JSON, Gson might parse it correctly)
        Map<String, String> verificationData = null;
        Object rawVerificationData = requestBodyMap.get("verificationData");
        if (rawVerificationData instanceof Map) {
            try {
                // Attempt to cast/convert. This might need more robust handling depending on Gson's behavior.
                verificationData = (Map<String, String>) rawVerificationData;
            } catch (ClassCastException e) {
                logger.warn("Could not cast verificationData to Map<String, String>");
                // Handle error or use default empty map
                verificationData = new HashMap<>(); 
            }
        } else {
             verificationData = new HashMap<>();
        }
        
        // Call the updated service method
        try {
             Kill reportedKill = killService.reportKill(killerId, victimId, latitude, longitude, 
                                                     verificationMethod, verificationData);
            // Return the created Kill object
            return response.withStatusCode(201).withBody(gson.toJson(reportedKill));
        } catch (GameNotFoundException e) {
            logger.warn("Game not found during kill report: {}", e.getMessage());
            return response.withStatusCode(404).withBody(gson.toJson(Map.of("message", e.getMessage())));
        }
    }
    
    private APIGatewayProxyResponseEvent getKillsByKiller(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) {
        // Extract killer ID from path parameters
        Map<String, String> pathParams = request.getPathParameters();
        String killerId = pathParams.get("killerID");
        
        if (killerId == null || killerId.trim().isEmpty()) {
            return response.withStatusCode(400)
                    .withBody(gson.toJson(Map.of("message", "Missing killerID parameter")));
        }
        
        // Get kills by killer
        List<Kill> kills = killService.getKillsByKiller(killerId);
        
        // Return the list of kills
        return response.withStatusCode(200).withBody(gson.toJson(kills));
    }
    
    private APIGatewayProxyResponseEvent getKillsByVictim(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) {
        // Extract victim ID from path parameters
        Map<String, String> pathParams = request.getPathParameters();
        String victimId = pathParams.get("victimID");
        
        if (victimId == null || victimId.trim().isEmpty()) {
            return response.withStatusCode(400)
                    .withBody(gson.toJson(Map.of("message", "Missing victimID parameter")));
        }
        
        // Get kills by victim
        List<Kill> kills = killService.getKillsByVictim(victimId);
        
        // Return the list of kills
        return response.withStatusCode(200).withBody(gson.toJson(kills));
    }
    
    private APIGatewayProxyResponseEvent getRecentKills(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) {
        // Extract limit parameter if provided, default to 10
        int limit = 10;
        
        Map<String, String> queryParams = request.getQueryStringParameters();
        if (queryParams != null && queryParams.containsKey("limit")) {
            try {
                limit = Integer.parseInt(queryParams.get("limit"));
                // Enforce reasonable limits (1-50)
                limit = Math.max(1, Math.min(50, limit));
            } catch (NumberFormatException e) {
                logger.warn("Invalid limit parameter: {}", queryParams.get("limit"));
                // Continue with default limit
            }
        }
        
        logger.info("Getting recent kills with limit: {}", limit);
        
        try {
            // Get recent kills
            List<Kill> kills = killService.findRecentKills(limit);
            
            // Return the list of kills
            return response.withStatusCode(200).withBody(gson.toJson(kills));
        } catch (KillNotFoundException e) {
             logger.warn("No recent kills found: {}", e.getMessage());
            return response.withStatusCode(404)
                    .withBody(gson.toJson(Map.of("message", "No recent kills found")));
        } catch (Exception e) {
            logger.error("Error getting recent kills: {}", e.getMessage(), e);
            return response.withStatusCode(500)
                    .withBody(gson.toJson(Map.of("message", "Error retrieving recent kills: " + e.getMessage())));
        }
    }

    private APIGatewayProxyResponseEvent verifyKill(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response)
            throws KillNotFoundException, ValidationException, PlayerActionNotAllowedException {
                
        String verifierId = HandlerUtils.getPlayerIdFromRequest(request);
        if (verifierId == null) {
             logger.warn("Verifier ID not found in request context for kill verification.");
        }

        Map<String, String> pathParams = request.getPathParameters();
        String killerId = pathParams.get("killerId"); 
        String killTime = pathParams.get("killTime"); 
        
        if (killerId == null || killerId.trim().isEmpty() || killTime == null || killTime.trim().isEmpty()) {
            throw new ValidationException("Missing killerId or killTime in path parameters.");
        }

        Map<String, String> verificationInput = gson.fromJson(request.getBody(), 
                new TypeToken<Map<String, String>>(){}.getType());
        if (verificationInput == null) {
             verificationInput = new HashMap<>();
        }
        
        logger.info("Attempting kill verification: Killer={}, Time={}, Verifier={}", killerId, killTime, verifierId);
        
        Kill updatedKill = killService.verifyKill(killerId, killTime, verifierId, verificationInput);
        
        return response.withStatusCode(200).withBody(gson.toJson(updatedKill));
    }

    private APIGatewayProxyResponseEvent getGameTimeline(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) {
        Map<String, String> pathParams = request.getPathParameters();
        String gameId = pathParams.get("gameID");
        
        if (gameId == null || gameId.trim().isEmpty()) {
            logger.warn("Missing gameID parameter for timeline request");
            return response.withStatusCode(400)
                    .withBody(gson.toJson(Map.of("message", "Missing gameID path parameter")));
        }
        
        logger.info("Getting timeline for gameID: {}", gameId);
        
        try {
            // Call the actual service method now
            List<Kill> timelineKills = killService.getKillsByGameId(gameId); 
            
            // Transform Kill objects into timeline events
            List<Map<String, Object>> timelineEvents = transformKillsToTimelineEvents(timelineKills);
            
            return response.withStatusCode(200).withBody(gson.toJson(timelineEvents));
            
        } catch (GameNotFoundException e) {
            logger.warn("Game not found while fetching timeline for gameID {}: {}", gameId, e.getMessage());
            return response.withStatusCode(404).withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (Exception e) {
            logger.error("Error fetching timeline for gameID {}: {}", gameId, e.getMessage(), e);
             return response.withStatusCode(500)
                    .withBody(gson.toJson(Map.of("message", "Error retrieving game timeline: " + e.getMessage())));
        }
    }
    
    /**
     * Transforms Kill objects into timeline event objects with an eventType field.
     */
    private List<Map<String, Object>> transformKillsToTimelineEvents(List<Kill> kills) {
        return kills.stream()
            .map(kill -> {
                Map<String, Object> event = new HashMap<>();
                event.put("eventType", "KILL");
                event.put("time", kill.getTime());
                event.put("killerID", kill.getKillerID());
                event.put("victimID", kill.getVictimID());
                event.put("latitude", kill.getLatitude());
                event.put("longitude", kill.getLongitude());
                event.put("verificationMethod", kill.getVerificationMethod());
                event.put("verificationStatus", kill.getVerificationStatus());
                return event;
            })
            .sorted((e1, e2) -> ((String)e1.get("time")).compareTo((String)e2.get("time"))) // Sort by time ascending
            .collect(Collectors.toList());
    }
} 