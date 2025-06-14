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
import com.assassin.exception.ValidationException;
import com.assassin.model.Notification;
import com.assassin.service.NotificationService;
import com.assassin.util.HandlerUtils;
import com.assassin.util.GsonUtil;
import com.google.gson.Gson;

/**
 * Handles API Gateway requests related to notifications.
 */
public class NotificationHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(NotificationHandler.class);
    private static final Gson gson = GsonUtil.getGson();
    private final NotificationService notificationService;

    /**
     * Default constructor, initializes service.
     */
    public NotificationHandler() {
        this.notificationService = new NotificationService();
    }

    /**
     * Constructor with dependency injection for testability.
     * 
     * @param notificationService The service for notification operations
     */
    public NotificationHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Processes the incoming request based on path and HTTP method.
     *
     * @param request the incoming API Gateway request
     * @param context the Lambda context
     * @return API Gateway response with appropriate status code and body
     */
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Processing notification request: Method={}, Path={}", request.getHttpMethod(), request.getPath());
        String path = request.getPath();
        String httpMethod = request.getHttpMethod();
        
        try {
            // Route based on path and HTTP method
            if (path.matches("/notifications") && "POST".equals(httpMethod)) {
                return createNotification(request);
            } else if (path.matches("/notifications/player/[^/]+") && "GET".equals(httpMethod)) {
                String playerId = path.substring("/notifications/player/".length());
                Map<String, String> queryParams = request.getQueryStringParameters();
                String sinceTimestamp = queryParams != null ? queryParams.get("since") : null;
                int limit = queryParams != null && queryParams.containsKey("limit") ? 
                            Integer.parseInt(queryParams.get("limit")) : 50;
                
                return getPlayerNotifications(playerId, sinceTimestamp, limit);
            } else if (path.matches("/notifications/[^/]+") && "GET".equals(httpMethod)) {
                String notificationId = getNotificationIdFromPath(path);
                String recipientId = request.getQueryStringParameters() != null ? 
                                    request.getQueryStringParameters().get("recipientId") : null;
                
                if (recipientId == null || recipientId.isEmpty()) {
                    return HandlerUtils.createErrorResponse(400, "Required query parameter 'recipientId' is missing");
                }
                
                return getNotification(recipientId, notificationId);
            } else {
                return HandlerUtils.createErrorResponse(404, "Route not found");
            }
        } catch (ValidationException e) {
            logger.warn("Validation error: {}", e.getMessage());
            return HandlerUtils.createErrorResponse(400, e.getMessage());
        } catch (NumberFormatException e) {
            logger.warn("Invalid number format: {}", e.getMessage());
            return HandlerUtils.createErrorResponse(400, "Invalid number format in request parameters");
        } catch (RuntimeException e) {
            logger.error("Error processing notification request: {}", e.getMessage(), e);
            return HandlerUtils.createErrorResponse(500, "Internal Server Error");
        }
    }
    
    /**
     * Creates a new notification.
     *
     * @param request the API Gateway request
     * @return the API Gateway response
     */
    private APIGatewayProxyResponseEvent createNotification(APIGatewayProxyRequestEvent request) {
        if (request.getBody() == null || request.getBody().isEmpty()) {
            return HandlerUtils.createErrorResponse(400, "Request body is required");
        }
        
        try {
            // Parse the JSON request
            String requestBody = request.getBody();
            Notification notification = gson.fromJson(requestBody, Notification.class);
            
            // Validate required fields
            if (notification.getRecipientPlayerId() == null || notification.getRecipientPlayerId().isEmpty()) {
                return HandlerUtils.createErrorResponse(400, "recipientPlayerId is required");
            }
            
            if (notification.getType() == null || notification.getType().isEmpty()) {
                return HandlerUtils.createErrorResponse(400, "type is required");
            }
            
            if (notification.getMessage() == null || notification.getMessage().isEmpty()) {
                return HandlerUtils.createErrorResponse(400, "message is required");
            }
            
            // Send the notification
            notificationService.sendNotification(notification);
            
            // Return the created notification
            return HandlerUtils.createApiResponse(201, gson.toJson(notification));
        } catch (RuntimeException e) {
            logger.error("Error creating notification: {}", e.getMessage(), e);
            return HandlerUtils.createErrorResponse(400, "Invalid notification data: " + e.getMessage());
        }
    }
    
    /**
     * Gets notifications for a specific player.
     *
     * @param playerId the ID of the player to retrieve notifications for
     * @param sinceTimestamp retrieve notifications after this timestamp (optional)
     * @param limit maximum number of notifications to retrieve
     * @return the API Gateway response with a list of notifications
     */
    private APIGatewayProxyResponseEvent getPlayerNotifications(String playerId, String sinceTimestamp, int limit) {
        logger.info("Getting notifications for player: {}, since: {}, limit: {}", 
                   playerId, sinceTimestamp != null ? sinceTimestamp : "beginning", limit);
        
        if (playerId == null || playerId.isEmpty()) {
            return HandlerUtils.createErrorResponse(400, "Player ID is required");
        }
        
        try {
            List<Notification> notifications = notificationService.getNotificationsForPlayer(playerId, sinceTimestamp, limit);
            return HandlerUtils.createApiResponse(200, gson.toJson(notifications));
        } catch (RuntimeException e) {
            logger.error("Error retrieving notifications for player: {}", e.getMessage(), e);
            return HandlerUtils.createErrorResponse(500, "Error retrieving notifications: " + e.getMessage());
        }
    }
    
    /**
     * Gets a specific notification by player ID and timestamp.
     *
     * @param recipientId the ID of the recipient player
     * @param notificationId the ID of the notification
     * @return the API Gateway response with the notification
     */
    private APIGatewayProxyResponseEvent getNotification(String recipientId, String notificationId) {
        logger.info("Getting notification. Recipient ID: {}, Notification ID: {}", recipientId, notificationId);
        
        try {
            Optional<Notification> notification = notificationService.getNotification(recipientId, notificationId);
            
            if (notification.isPresent()) {
                return HandlerUtils.createApiResponse(200, gson.toJson(notification.get()));
            } else {
                return HandlerUtils.createErrorResponse(404, "Notification not found");
            }
        } catch (RuntimeException e) {
            logger.error("Error retrieving notification: {}", e.getMessage(), e);
            return HandlerUtils.createErrorResponse(500, "Error retrieving notification: " + e.getMessage());
        }
    }
    
    /**
     * Extracts the notification ID from the path.
     *
     * @param path the API Gateway path
     * @return the notification ID
     */
    private String getNotificationIdFromPath(String path) {
        return path.substring("/notifications/".length());
    }
} 