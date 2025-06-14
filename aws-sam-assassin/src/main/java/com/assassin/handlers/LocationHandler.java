package com.assassin.handlers;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.InvalidLocationException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.PlayerPersistenceException;
import com.assassin.exception.AntiCheatViolationException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Coordinate;
import com.assassin.model.LocationUpdateInput;
import com.assassin.service.AntiCheatService;
import com.assassin.service.LocationService;
import com.assassin.util.GsonUtil;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Handles incoming requests related to player location updates.
 */
public class LocationHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(LocationHandler.class);
    private static final Gson gson = GsonUtil.getGson();
    private final LocationService locationService;
    private final AntiCheatService antiCheatService;

    public LocationHandler() {
        this(new LocationService(), new AntiCheatService());
    }

    // Constructor for dependency injection
    public LocationHandler(LocationService locationService) {
        this(locationService, new AntiCheatService());
    }
    
    // Full constructor for dependency injection
    public LocationHandler(LocationService locationService, AntiCheatService antiCheatService) {
        this.locationService = locationService;
        this.antiCheatService = antiCheatService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(HandlerUtils.getResponseHeaders());

        logger.info("LocationHandler received request: Method={}, Path={}", httpMethod, path);

        try {
            // Currently only handles POST /location
            if ("POST".equals(httpMethod) && "/location".equals(path)) {
                return updateLocation(request, response);
            } else {
                logger.warn("Route not found in LocationHandler: {} {}", httpMethod, path);
                return response
                        .withStatusCode(404)
                        .withBody(gson.toJson(Map.of("message", "Route not found")));
            }
        } catch (ValidationException | IllegalArgumentException | com.google.gson.JsonSyntaxException e) {
            logger.warn("Invalid input processing location request: {}", e.getMessage());
            return response
                    .withStatusCode(400)
                    .withBody(gson.toJson(Map.of("message", "Invalid request data: " + e.getMessage())));
        } catch (PlayerNotFoundException | GameNotFoundException e) {
            logger.warn("Resource not found during location update: {}", e.getMessage());
            return response
                    .withStatusCode(404)
                    .withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (InvalidLocationException e) {
            logger.warn("Invalid location reported: {}", e.getMessage());
            return response
                    .withStatusCode(400)
                    .withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (PlayerPersistenceException e) {
            logger.error("Persistence error processing location request: {}", e.getMessage(), e);
            return response
                    .withStatusCode(500)
                    .withBody(gson.toJson(Map.of("message", "Database error processing location", "error", e.getClass().getSimpleName())));
        } catch (RuntimeException e) {
            logger.error("Error processing location request: {}", e.getMessage(), e);
            return response
                    .withStatusCode(500)
                    .withBody(gson.toJson(Map.of("message", "Internal Server Error", "error", e.getClass().getSimpleName())));
        }
    }

    /**
     * Handles POST /location request to update a player's location.
     *
     * @param request  the API Gateway request
     * @param response the API Gateway response object
     * @return the completed API Gateway response
     */
    private APIGatewayProxyResponseEvent updateLocation(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) 
        throws PlayerNotFoundException, GameNotFoundException, InvalidLocationException, PlayerPersistenceException, 
               IllegalArgumentException, com.google.gson.JsonSyntaxException {
        String playerId = null;
        try {
            // 1. Get Player ID from Authenticated User
            playerId = HandlerUtils.getPlayerIdFromRequest(request)
                    .orElseThrow(() -> new ValidationException("Player ID not found in request context."));
            logger.debug("Attempting location update for player ID: {}", playerId);

            // 2. Parse Request Body
            LocationUpdateInput locationInput = gson.fromJson(request.getBody(), LocationUpdateInput.class);
            if (locationInput == null || locationInput.getLatitude() == null || locationInput.getLongitude() == null) {
                logger.warn("Invalid location update payload received for player {}", playerId);
                throw new IllegalArgumentException("Invalid location data in request body (latitude/longitude required)");
            }

            // 3. Anti-Cheat Validation
            Coordinate newLocation = new Coordinate();
            newLocation.setLatitude(locationInput.getLatitude());
            newLocation.setLongitude(locationInput.getLongitude());
            newLocation.setAccuracy(locationInput.getAccuracy());
            
            // Extract device information from headers for fingerprinting
            Map<String, String> headers = request.getHeaders();
            String userAgent = headers != null ? headers.get("User-Agent") : null;
            String deviceFingerprint = generateDeviceFingerprint(headers);
            Map<String, Object> deviceMetadata = extractDeviceMetadata(headers);
            
            // Validate location update through anti-cheat system
            AntiCheatService.LocationValidationResult validationResult = antiCheatService.validateLocationUpdate(
                playerId, newLocation, deviceFingerprint, deviceMetadata);
            
            if (!validationResult.isValid()) {
                logger.warn("Anti-cheat validation failed for player {} - violations: {}", 
                           playerId, validationResult.getViolations().size());
                
                // Trigger automated response for severe violations
                for (AntiCheatService.CheatViolation violation : validationResult.getViolations()) {
                    if (violation.getSeverity() >= 7) {
                        antiCheatService.triggerAutomatedResponse(playerId, violation.getCheatType(), violation.getSeverity());
                    }
                }
                
                // For critical violations, reject the location update
                boolean hasCriticalViolations = validationResult.getViolations().stream()
                    .anyMatch(v -> v.getSeverity() >= 9);
                
                if (hasCriticalViolations) {
                    return response
                        .withStatusCode(400)
                        .withBody(gson.toJson(Map.of(
                            "message", "Location update rejected due to anti-cheat violations",
                            "violations", validationResult.getViolations()
                        )));
                }
            }

            // 4. Delegate to LocationService
            // LocationService handles fetching player/game, validation, boundary checks, and persistence.
            locationService.updatePlayerLocation(
                playerId,
                locationInput.getLatitude(),
                locationInput.getLongitude(),
                locationInput.getAccuracy()
            );

            logger.info("Successfully processed location update for player ID: {}", playerId);

            // 4. Return Success Response (No Content or minimal confirmation)
            return response
                    .withStatusCode(204); // 204 No Content is appropriate for updates with no body needed

        } catch (com.google.gson.JsonSyntaxException e) {
            logger.warn("Failed to parse location update JSON for player {}: {}", playerId, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            // Catch potential errors from getPlayerIdFromRequest or DAO
            logger.error("Error updating location for player {}: {}", playerId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Generates a device fingerprint from request headers
     */
    private String generateDeviceFingerprint(Map<String, String> headers) {
        if (headers == null) {
            return "unknown";
        }
        
        StringBuilder fingerprint = new StringBuilder();
        
        // Collect identifying headers
        String userAgent = headers.get("User-Agent");
        String acceptLanguage = headers.get("Accept-Language");
        String acceptEncoding = headers.get("Accept-Encoding");
        String deviceId = headers.get("X-Device-ID"); // Custom header from mobile app
        
        fingerprint.append(userAgent != null ? userAgent : "").append("|");
        fingerprint.append(acceptLanguage != null ? acceptLanguage : "").append("|");
        fingerprint.append(acceptEncoding != null ? acceptEncoding : "").append("|");
        fingerprint.append(deviceId != null ? deviceId : "");
        
        // Simple hash to create consistent fingerprint
        return Integer.toString(fingerprint.toString().hashCode());
    }
    
    /**
     * Extracts device metadata from request headers
     */
    private Map<String, Object> extractDeviceMetadata(Map<String, String> headers) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        
        if (headers == null) {
            return metadata;
        }
        
        // Extract useful metadata
        metadata.put("userAgent", headers.get("User-Agent"));
        metadata.put("acceptLanguage", headers.get("Accept-Language"));
        metadata.put("deviceId", headers.get("X-Device-ID"));
        metadata.put("appVersion", headers.get("X-App-Version"));
        metadata.put("platform", headers.get("X-Platform"));
        metadata.put("osVersion", headers.get("X-OS-Version"));
        
        // Remove null values
        metadata.entrySet().removeIf(entry -> entry.getValue() == null);
        
        return metadata;
    }
} 