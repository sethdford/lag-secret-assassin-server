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
import com.assassin.exception.ValidationException;
import com.assassin.model.LocationUpdateInput;
import com.assassin.service.LocationService;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Handles incoming requests related to player location updates.
 */
public class LocationHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(LocationHandler.class);
    private static final Gson gson = new GsonBuilder().create(); // No pretty printing for efficiency
    private final LocationService locationService;

    public LocationHandler() {
        this(new LocationService());
    }

    // Constructor for dependency injection
    public LocationHandler(LocationService locationService) {
        this.locationService = locationService;
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
        } catch (Exception e) {
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

            // 3. Delegate to LocationService
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
        } catch (Exception e) {
            // Catch potential errors from getPlayerIdFromRequest or DAO
            logger.error("Error updating location for player {}: {}", playerId, e.getMessage(), e);
            throw e;
        }
    }
} 