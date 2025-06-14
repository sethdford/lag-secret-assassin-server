package com.assassin.service;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.DynamoDbNotificationDao;
import com.assassin.dao.NotificationDao;
import com.assassin.model.Notification;
import com.assassin.model.WebSocketConnection;
import com.assassin.util.DynamoDbClientProvider;
import com.assassin.util.GsonUtil;
import com.google.gson.Gson;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

/**
 * Service responsible for handling and sending notifications.
 * Includes logic for persisting notifications and sending them via WebSocket.
 */
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private static final Gson gson = GsonUtil.getGson();
    private final NotificationDao notificationDao;
    private final DynamoDbTable<WebSocketConnection> connectionsTable;
    private final DynamoDbIndex<WebSocketConnection> connectionsByPlayerIndex;
    private static final String CONNECTIONS_TABLE_NAME = System.getenv("CONNECTIONS_TABLE_NAME");
    private static final String PLAYER_ID_INDEX_NAME = "PlayerIdIndex";

    // Lazy initialization for the ApiGatewayManagementApiClient
    private ApiGatewayManagementApiClient apiGatewayManagementApiClient = null;

    // Default constructor initializes the DAOs and index
    public NotificationService() {
        this(new DynamoDbNotificationDao());
    }

    // Constructor for dependency injection (testing)
    public NotificationService(NotificationDao notificationDao) {
        this.notificationDao = notificationDao;
        DynamoDbClient ddbClient = DynamoDbClientProvider.getClient();
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(ddbClient)
            .build();
        // Handle potential null table name during initialization (e.g., in unit tests without env vars)
        if (CONNECTIONS_TABLE_NAME != null) {
            this.connectionsTable = enhancedClient.table(CONNECTIONS_TABLE_NAME, TableSchema.fromBean(WebSocketConnection.class));
            this.connectionsByPlayerIndex = connectionsTable.index(PLAYER_ID_INDEX_NAME);
            logger.info("NotificationService initialized with DAO and WebSocket Connections Table: {}", CONNECTIONS_TABLE_NAME);
        } else {
            this.connectionsTable = null;
            this.connectionsByPlayerIndex = null;
            logger.warn("CONNECTIONS_TABLE_NAME environment variable not set. WebSocket functionality will be disabled.");
        }
    }

    /**
     * Initializes the API Gateway Management API client based on the provided endpoint.
     * This should be called with the correct endpoint before attempting to send messages.
     * 
     * @param endpoint The API Gateway Management API endpoint (HTTPS).
     */
    private ApiGatewayManagementApiClient getManagementApiClient(String endpoint) {
        // Lazy initialization or re-initialization if endpoint changes
        if (this.apiGatewayManagementApiClient == null || 
            !this.apiGatewayManagementApiClient.serviceClientConfiguration().endpointOverride().map(URI::toString).orElse("").equals(endpoint)) {
            
            if (endpoint == null || endpoint.isEmpty() || !endpoint.startsWith("https://")) {
                 logger.error("Invalid or missing HTTPS endpoint provided for ApiGatewayManagementApiClient: '{}'", endpoint);
                 // Return null or throw an exception, depending on desired behavior
                 return null; 
            }
            logger.info("Initializing ApiGatewayManagementApiClient with endpoint: {}", endpoint);
            this.apiGatewayManagementApiClient = ApiGatewayManagementApiClient.builder()
                .endpointOverride(URI.create(endpoint))
                // Consider region configuration if needed
                // .region(Region.of(System.getenv("AWS_REGION")))
                .build();
        }
        return this.apiGatewayManagementApiClient;
    }

    /**
     * Sends a notification: persists it and attempts to push it via WebSocket.
     * Reads WEBSOCKET_API_ENDPOINT environment variable for the WebSocket API endpoint.
     *
     * @param notification The notification object to send.
     */
    public void sendNotification(Notification notification) {
        String webSocketApiEndpoint = System.getenv("WEBSOCKET_API_ENDPOINT"); // Get endpoint from env

        if (notification == null) {
            logger.warn("Attempted to send a null notification.");
            return;
        }
        
        // Assign a unique ID if not already set
        if (notification.getNotificationId() == null || notification.getNotificationId().isEmpty()) {
             notification.setNotificationId(java.util.UUID.randomUUID().toString());
        }

        // 1. Persist the notification first
        try {
            notificationDao.saveNotification(notification);
            logger.info("Notification persisted successfully: ID={}", notification.getNotificationId());
        } catch (DynamoDbException e) {
            logger.error("CRITICAL: Failed to persist notification: ID={}, Error={}", notification.getNotificationId(), e.getMessage(), e);
            // Decide if sending should be aborted if persistence fails
            // For critical notifications, maybe throw RuntimeException here
            // throw new RuntimeException("Failed to persist critical notification", e);
            // For less critical ones, log and potentially continue to WebSocket send attempt
        }

        // 2. Send real-time notification via WebSocket if configured and possible
        if (connectionsTable == null || connectionsByPlayerIndex == null) {
             logger.warn("WebSocket connection table not initialized. Skipping real-time send for notification ID: {}", notification.getNotificationId());
        } else if (webSocketApiEndpoint == null || webSocketApiEndpoint.isEmpty()) {
            logger.warn("WEBSOCKET_API_ENDPOINT environment variable is not set. Skipping real-time send for notification ID: {}", notification.getNotificationId());
        } else if (notification.getRecipientPlayerId() == null || notification.getRecipientPlayerId().isEmpty()) {
             logger.warn("Cannot send real-time notification because recipientPlayerId is null or empty. Notification ID: {}", notification.getNotificationId());
        } else {
            // Proceed with WebSocket send attempt
            String recipientPlayerId = notification.getRecipientPlayerId();
            logger.info("Attempting to send real-time notification via WebSocket to player: {}", recipientPlayerId);

            try {
                // Query the GSI to find connection IDs for the recipient
                QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(recipientPlayerId).build());
                QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder().queryConditional(queryConditional).build();
                
                // The endpoint for ApiGatewayManagementApiClient needs to be https
                String managementApiEndpoint = webSocketApiEndpoint.replaceFirst("^wss://", "https://"); 
                ApiGatewayManagementApiClient apiClient = getManagementApiClient(managementApiEndpoint);
                
                if (apiClient == null) {
                    logger.error("ApiGatewayManagementApiClient is null, cannot send WebSocket message for notification ID {}", notification.getNotificationId());
                    return; // Exit if client couldn't be initialized
                }
                
                String notificationJson = gson.toJson(notification);
                final int[] successfulSends = {0}; // Use array for modification in lambda

                connectionsByPlayerIndex.query(queryRequest)
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .forEach(connection -> {
                        String connectionId = connection.getConnectionId();
                        logger.debug("Found active connection for player {}: {}", recipientPlayerId, connectionId);
                        try {
                            PostToConnectionRequest postRequest = PostToConnectionRequest.builder()
                                .connectionId(connectionId)
                                .data(SdkBytes.fromUtf8String(notificationJson))
                                .build();
                            apiClient.postToConnection(postRequest);
                            logger.info("Successfully sent notification to connection: {}", connectionId);
                            successfulSends[0]++;
                        } catch (GoneException ge) {
                            // Connection is no longer valid, remove it from the table
                            logger.warn("Attempted to send to stale connection {}. Removing.", connectionId);
                            removeStaleConnection(connectionId);
                        } catch (RuntimeException e) {
                            // Log other errors during postToConnection but potentially continue with other connections
                            logger.error("Failed to send notification to connection {}: {}", connectionId, e.getMessage(), e);
                        }
                    });
                
                if (successfulSends[0] > 0) {
                     logger.info("Successfully sent notification to {} active connections for player {}", successfulSends[0], recipientPlayerId);
                } else {
                    logger.warn("No active WebSocket connections found for player {} to send notification ID {}", recipientPlayerId, notification.getNotificationId());
                    // TODO: Trigger push notification logic here if needed
                }

            } catch (RuntimeException e) {
                // Log errors related to querying connections or initializing the API client
                logger.error("Error preparing or sending WebSocket message for player {}: {}", recipientPlayerId, e.getMessage(), e);
            }
        }

        logger.debug("Finished processing notification ID: {}", notification.getNotificationId());
    }

    /**
     * Removes a stale connection record from the connections table.
     * 
     * @param connectionId The ID of the stale connection.
     */
    private void removeStaleConnection(String connectionId) {
        if (connectionId == null || connectionId.isEmpty() || connectionsTable == null) return;
        try {
            Key key = Key.builder().partitionValue(connectionId).build();
            connectionsTable.deleteItem(key);
            logger.info("Removed stale connection: {}", connectionId);
        } catch (RuntimeException e) {
            logger.error("Failed to remove stale connection {}: {}", connectionId, e.getMessage(), e);
        }
    }

    /**
     * Gets a specific notification by recipient ID and notification ID.
     *
     * @param recipientPlayerId The ID of the player who received the notification
     * @param notificationId The ID of the notification to retrieve
     * @return An Optional containing the Notification if found, otherwise empty
     */
    public Optional<Notification> getNotification(String recipientPlayerId, String notificationId) {
        if (recipientPlayerId == null || recipientPlayerId.isEmpty()) {
            logger.warn("Cannot get notification with null or empty recipientPlayerId");
            throw new IllegalArgumentException("recipientPlayerId cannot be null or empty");
        }
        
        if (notificationId == null || notificationId.isEmpty()) {
            logger.warn("Cannot get notification with null or empty notificationId");
            throw new IllegalArgumentException("notificationId cannot be null or empty");
        }
        
        logger.info("Getting notification for recipient: {}, notification ID: {}", 
                   recipientPlayerId, notificationId);
        
        try {
            // Note: This implementation assumes notificationId is the same as timestamp
            // In a real system, you might need a more sophisticated lookup mechanism
            return notificationDao.getNotification(recipientPlayerId, notificationId);
        } catch (RuntimeException e) {
            logger.error("Error retrieving notification: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve notification", e);
        }
    }
    
    /**
     * Gets notifications for a specific player, optionally starting from a specific timestamp.
     *
     * @param playerId The ID of the player whose notifications to retrieve
     * @param sinceTimestamp Optional ISO 8601 timestamp to filter notifications after this time
     * @param limit The maximum number of notifications to return
     * @return A list of Notification objects, ordered by timestamp (typically descending)
     */
    public List<Notification> getNotificationsForPlayer(String playerId, String sinceTimestamp, int limit) {
        if (playerId == null || playerId.isEmpty()) {
            logger.warn("Cannot get notifications with null or empty playerId");
            throw new IllegalArgumentException("playerId cannot be null or empty");
        }
        
        if (limit <= 0) {
            logger.warn("Invalid limit: {}. Using default limit of 50", limit);
            limit = 50;
        }
        
        logger.info("Getting notifications for player: {}, since: {}, limit: {}", 
                   playerId, sinceTimestamp != null ? sinceTimestamp : "beginning", limit);
        
        try {
            return notificationDao.findNotificationsByPlayer(playerId, sinceTimestamp, limit);
        } catch (RuntimeException e) {
            logger.error("Error retrieving notifications for player: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve notifications for player", e);
        }
    }
    
    /**
     * Marks a specific notification as read for a recipient.
     *
     * @param recipientPlayerId The ID of the player who received the notification
     * @param notificationId The ID of the notification to mark as read
     * @return true if the notification was successfully marked as read, false otherwise
     */
    public boolean markNotificationAsRead(String recipientPlayerId, String notificationId) {
        // Add null/empty checks before calling DAO
        if (recipientPlayerId == null || recipientPlayerId.isEmpty()) {
            logger.warn("Attempted to mark notification as read with null or empty recipientPlayerId.");
            return false;
        }
        if (notificationId == null || notificationId.isEmpty()) {
            logger.warn("Attempted to mark notification as read with null or empty notificationId for recipient: {}", recipientPlayerId);
            return false;
        }
        
        logger.info("Marking notification as read for recipient: {}, notification ID: {}", 
                   recipientPlayerId, notificationId);
                   
        try {
            // Call the DAO method which returns Optional<Notification>
            Optional<Notification> updatedNotification = notificationDao.markNotificationAsRead(recipientPlayerId, notificationId);
            // Return true if the DAO call was successful (Optional is present)
            return updatedNotification.isPresent(); 
        } catch (RuntimeException e) {
            logger.error("Error marking notification as read for recipient {}, ID {}: {}", 
                       recipientPlayerId, notificationId, e.getMessage(), e);
            // Depending on DAO behavior, you might want to throw or return false
            return false; 
        }
    }

    // Potential future methods:
    // public List<Notification> getNotificationsForPlayer(String playerId, Instant since)
    // public void registerDeviceForPush(String playerId, String deviceToken, String platform)
} 