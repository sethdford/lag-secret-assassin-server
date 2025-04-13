package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.GameDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GamePersistenceException;
import com.assassin.model.Coordinate;
import com.assassin.util.AuthorizationUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Handles requests related to managing game settings and state.
 */
public class GameManagementHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(GameManagementHandler.class);
    private final GameDao gameDao;
    private final Gson gson = new Gson();
    private static final Type COORDINATE_LIST_TYPE = new TypeToken<List<Coordinate>>() {}.getType();

    public GameManagementHandler() {
        this(new DynamoDbGameDao());
    }

    // Constructor for testing
    public GameManagementHandler(GameDao gameDao) {
        this.gameDao = gameDao;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        String httpMethod = input.getHttpMethod();
        String path = input.getPath();

        logger.info("Received {} request for path: {}", httpMethod, path);

        try {
            // TODO: Add proper authorization checks (e.g., is user game admin or system admin)
            // String requestingUserId = AuthorizationUtils.getUserId(input);

            if ("PUT".equals(httpMethod) && path.matches("/games/[^/]+/boundary")) {
                return updateBoundary(input);
            }
            // TODO: Add other game management endpoints (e.g., update status, settings)

            return com.assassin.util.ApiGatewayResponse.builder()
                    .setStatusCode(404)
                    .setBody(new com.assassin.util.ErrorResponse("Not Found: The requested game management resource does not exist.").toJson())
                    .build();

        } catch (com.assassin.exception.InvalidRequestException | JsonSyntaxException e) {
            logger.warn("Bad request: {}", e.getMessage());
            return com.assassin.util.ApiGatewayResponse.builder()
                    .setStatusCode(400)
                    .setBody(new com.assassin.util.ErrorResponse(e.getMessage()).toJson())
                    .build();
        } catch (GameNotFoundException e) {
            logger.warn("Game not found: {}", e.getMessage());
            return com.assassin.util.ApiGatewayResponse.builder()
                    .setStatusCode(404)
                    .setBody(new com.assassin.util.ErrorResponse(e.getMessage()).toJson())
                    .build();
        } catch (GamePersistenceException e) {
            logger.error("Database error: {}", e.getMessage(), e);
            return com.assassin.util.ApiGatewayResponse.builder()
                    .setStatusCode(500)
                    .setBody(new com.assassin.util.ErrorResponse("Internal server error during game operation.").toJson())
                    .build();
        } catch (Exception e) {
            logger.error("Unexpected error in GameManagementHandler: {}", e.getMessage(), e);
            return com.assassin.util.ApiGatewayResponse.builder()
                    .setStatusCode(500)
                    .setBody(new com.assassin.util.ErrorResponse("An unexpected error occurred.").toJson())
                    .build();
        }
    }

    private APIGatewayProxyResponseEvent updateBoundary(APIGatewayProxyRequestEvent input) 
            throws com.assassin.exception.InvalidRequestException, GameNotFoundException, GamePersistenceException {
        
        String gameId = getGameIdFromPath(input.getPath());
        if (gameId == null) {
            throw new com.assassin.exception.InvalidRequestException("Missing gameId in path.");
        }

        String requestBody = input.getBody();
        if (requestBody == null || requestBody.isEmpty()) {
            throw new com.assassin.exception.InvalidRequestException("Request body is empty or missing.");
        }

        List<Coordinate> boundary = gson.fromJson(requestBody, COORDINATE_LIST_TYPE);

        if (boundary == null || boundary.size() < 3) { // Need at least 3 points for a valid polygon
            throw new com.assassin.exception.InvalidRequestException("Invalid boundary data: Must be a list of at least 3 coordinates.");
        }
        
        // TODO: Add validation for coordinate values and polygon shape (e.g., not self-intersecting)

        gameDao.updateGameBoundary(gameId, boundary);

        logger.info("Successfully updated boundary for game {}", gameId);
        return com.assassin.util.ApiGatewayResponse.builder()
                .setStatusCode(200)
                .setBody("{\"message\":\"Game boundary updated successfully.\"}")
                .build();
    }

    private String getGameIdFromPath(String path) {
        // Extracts gameId from paths like /games/{gameId}/boundary
        String[] parts = path.split("/");
        if (parts.length >= 3 && "games".equals(parts[1])) {
            return parts[2];
        }
        return null;
    }
} 