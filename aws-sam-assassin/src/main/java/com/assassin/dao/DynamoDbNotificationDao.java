package com.assassin.dao;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.model.Notification;
import com.assassin.util.DynamoDbClientProvider;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

/**
 * DynamoDB implementation of the NotificationDao interface.
 */
public class DynamoDbNotificationDao implements NotificationDao {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbNotificationDao.class);
    // Use a constant for the env/property key
    private static final String NOTIFICATIONS_TABLE_ENV_VAR = "NOTIFICATIONS_TABLE_NAME";
    private final DynamoDbTable<Notification> notificationTable;
    private final String tableName; // Store the resolved table name

    public DynamoDbNotificationDao() {
        this.tableName = getTableName(); // Resolve table name first
        if (this.tableName == null || this.tableName.isEmpty()) {
            throw new IllegalStateException("Could not determine Notifications table name from System Property or Environment Variable '" + NOTIFICATIONS_TABLE_ENV_VAR + "'");
        }
        
        DynamoDbClient ddbClient = DynamoDbClientProvider.getClient(); 
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                                                                      .dynamoDbClient(ddbClient)
                                                                      .build(); 
        // Use the resolved tableName                                                                    
        this.notificationTable = enhancedClient.table(this.tableName, TableSchema.fromBean(Notification.class));
        logger.info("Initialized DynamoDbNotificationDao with table: {}", this.tableName);
    }
    
    // Helper method to get table name from environment variable or system property
    private String getTableName() {
        String tableNameFromProp = System.getProperty(NOTIFICATIONS_TABLE_ENV_VAR);
        String tableNameFromEnv = System.getenv(NOTIFICATIONS_TABLE_ENV_VAR);

        if (tableNameFromProp != null && !tableNameFromProp.isEmpty()) {
            logger.debug("Using Notifications table name from System Property: {}", tableNameFromProp);
            return tableNameFromProp;
        }
        if (tableNameFromEnv != null && !tableNameFromEnv.isEmpty()) {
             logger.debug("Using Notifications table name from Environment Variable: {}", tableNameFromEnv);
            return tableNameFromEnv;
        }
        logger.error("Notifications table name not found in System Property or Environment Variable ({})", NOTIFICATIONS_TABLE_ENV_VAR);
        return null; // Or throw an exception
    }

    /**
     * Saves a notification record.
     *
     * @param notification The Notification object to save.
     */
    @Override
    public void saveNotification(Notification notification) {
        if (notification == null) {
            logger.error("Cannot save null notification");
            throw new IllegalArgumentException("Notification cannot be null");
        }
        
        try {
            notificationTable.putItem(notification);
            logger.info("Saved notification with ID: {}, for recipient: {}", 
                        notification.getNotificationId(), notification.getRecipientPlayerId());
        } catch (DynamoDbException e) {
            logger.error("Error saving notification: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save notification: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a specific notification by its composite key.
     *
     * @param recipientPlayerId The recipient's player ID (partition key).
     * @param timestamp The notification timestamp (sort key).
     * @return An Optional containing the Notification if found, otherwise empty.
     */
    @Override
    public Optional<Notification> getNotification(String recipientPlayerId, String timestamp) {
        if (recipientPlayerId == null || recipientPlayerId.isEmpty()) {
            logger.error("Cannot get notification with null or empty recipientPlayerId");
            throw new IllegalArgumentException("recipientPlayerId cannot be null or empty");
        }
        
        if (timestamp == null || timestamp.isEmpty()) {
            logger.error("Cannot get notification with null or empty timestamp");
            throw new IllegalArgumentException("timestamp cannot be null or empty");
        }
        
        try {
            Key key = Key.builder()
                        .partitionValue(recipientPlayerId)
                        .sortValue(timestamp)
                        .build();
                        
            Notification notification = notificationTable.getItem(key);
            
            if (notification == null) {
                logger.info("No notification found for recipientPlayerId: {} and timestamp: {}", 
                           recipientPlayerId, timestamp);
                return Optional.empty();
            }
            
            logger.info("Retrieved notification for recipientPlayerId: {} and timestamp: {}", 
                       recipientPlayerId, timestamp);
            return Optional.of(notification);
        } catch (DynamoDbException e) {
            logger.error("Error retrieving notification: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve notification: " + e.getMessage(), e);
        }
    }

    /**
     * Finds all notifications for a specific player, optionally after a given timestamp.
     *
     * @param recipientPlayerId The ID of the player whose notifications to retrieve.
     * @param sinceTimestamp Optional ISO 8601 timestamp to filter notifications after this time.
     * @param limit The maximum number of notifications to return.
     * @return A list of Notification objects, ordered by timestamp (typically descending).
     */
    @Override
    public List<Notification> findNotificationsByPlayer(String recipientPlayerId, String sinceTimestamp, int limit) {
        if (recipientPlayerId == null || recipientPlayerId.isEmpty()) {
            logger.error("Cannot find notifications with null or empty recipientPlayerId");
            throw new IllegalArgumentException("recipientPlayerId cannot be null or empty");
        }
        
        if (limit <= 0) {
            logger.warn("Invalid limit: {}. Using default limit of 50", limit);
            limit = 50; // Default to 50 if invalid limit
        }
        
        try {
            QueryConditional queryConditional;
            
            // Create base query on partition key (recipientPlayerId)
            if (sinceTimestamp != null && !sinceTimestamp.isEmpty()) {
                // Query for notifications after the specified timestamp
                Key key = Key.builder()
                          .partitionValue(recipientPlayerId)
                          .sortValue(sinceTimestamp)
                          .build();
                queryConditional = QueryConditional.sortGreaterThan(key);
                
                logger.debug("Querying notifications for player: {} since timestamp: {}", 
                           recipientPlayerId, sinceTimestamp);
            } else {
                // Query for all notifications for this player
                queryConditional = QueryConditional
                    .keyEqualTo(Key.builder()
                                   .partitionValue(recipientPlayerId)
                                   .build());
                logger.debug("Querying all notifications for player: {}", recipientPlayerId);
            }
            
            // Build the query request with a limit
            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                                                                   .queryConditional(queryConditional)
                                                                   .limit(limit)
                                                                   .scanIndexForward(false) // Descending order (newest first)
                                                                   .build();
            
            // Execute the query and collect results
            PageIterable<Notification> pages = notificationTable.query(queryRequest);
            List<Notification> notifications = pages.items().stream()
                                                   .limit(limit)
                                                   .collect(Collectors.toList());
            
            logger.info("Found {} notifications for player: {}", notifications.size(), recipientPlayerId);
            return notifications;
        } catch (DynamoDbException e) {
            logger.error("Error finding notifications: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to find notifications: " + e.getMessage(), e);
        }
    }

    /**
     * Marks a specific notification as read.
     *
     * @param recipientPlayerId The recipient's player ID (partition key).
     * @param notificationId The unique ID of the notification to update.
     * @return The updated Notification object, or Optional.empty() if not found or update failed.
     */
    @Override
    public Optional<Notification> markNotificationAsRead(String recipientPlayerId, String notificationId) {
        if (recipientPlayerId == null || recipientPlayerId.isEmpty()) {
            logger.error("Cannot mark notification as read with null or empty recipientPlayerId");
            throw new IllegalArgumentException("recipientPlayerId cannot be null or empty");
        }
        
        if (notificationId == null || notificationId.isEmpty()) {
            logger.error("Cannot mark notification as read with null or empty notificationId");
            throw new IllegalArgumentException("notificationId cannot be null or empty");
        }
        
        // Since notificationId is the primary key and recipientId is needed for the key
        // but not part of the primary key itself according to the likely schema,
        // we first need to retrieve the notification to get the sort key (timestamp)
        // This assumes notificationId is unique and can be queried via a GSI or is the sort key.
        // *** Assuming 'NotificationId' is the sort key for this example implementation ***
        // If NotificationId is NOT the sort key, this logic needs adjustment (e.g., query a GSI).
        
        try {
            Key key = Key.builder()
                        .partitionValue(recipientPlayerId)
                        .sortValue(notificationId) // Assuming notificationId IS the sort key
                        .build();
            
            // Create an update expression to set the 'status' attribute to "READ"
            Notification updatePayload = new Notification();
            updatePayload.setRecipientPlayerId(recipientPlayerId);
            updatePayload.setNotificationId(notificationId);
            updatePayload.setStatus("READ"); // Update status field

            // Update the item, only setting the 'status' field
            // ignoreNulls(true) ensures only non-null fields in updatePayload are applied.
            Notification result = notificationTable.updateItem(r -> r.item(updatePayload).ignoreNulls(true));

            if (result != null) {
                logger.info("Successfully marked notification as read: recipient={}, id={}", recipientPlayerId, notificationId);
                return Optional.of(result);
            } else {
                // This case might occur if the item didn't exist, though updateItem usually returns the updated item.
                logger.warn("Notification not found or update failed for recipient={}, id={}", recipientPlayerId, notificationId);
                return Optional.empty(); 
            }
            
        } catch (DynamoDbException e) {
            logger.error("Error marking notification as read for recipient={}, id={}: {}", 
                       recipientPlayerId, notificationId, e.getMessage(), e);
            // Depending on the error (e.g., conditional check failure vs. service error), 
            // you might handle this differently. For simplicity, wrap in RuntimeException.
            throw new RuntimeException("Failed to mark notification as read: " + e.getMessage(), e);
        } catch (Exception e) { // Catch potential NPEs or other issues
            logger.error("Unexpected error marking notification as read for recipient={}, id={}: {}", 
                       recipientPlayerId, notificationId, e.getMessage(), e);
            throw new RuntimeException("Unexpected error marking notification as read", e);
        }
    }
}