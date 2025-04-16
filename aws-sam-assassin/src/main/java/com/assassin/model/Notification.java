package com.assassin.model;

import java.time.Instant;
import java.util.Map;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Represents a notification message within the system.
 */
@DynamoDbBean
public class Notification {

    private String notificationId; // Unique ID for the notification
    private String recipientPlayerId; // ID of the player receiving the notification (partition key)
    private String gameId;
    private String type; // Type of notification (e.g., "KILL_VERIFIED", "TARGET_ASSIGNED", "GAME_START", "ADMIN_MESSAGE")
    private String title;
    private String message; // Human-readable message content
    private String timestamp; // ISO 8601 timestamp when the event occurred or notification was generated (sort key)
    private Map<String, String> data; // Optional structured data related to the notification (e.g., killerId, victimId)
    private String status; // Status of the notification (e.g., "UNREAD", "READ", "ARCHIVED") - may not be needed initially
    private boolean read;

    // Default constructor
    public Notification() {
        this.timestamp = Instant.now().toString();
        this.status = "UNREAD"; // Default status
    }

    // Parameterized constructor (optional)
    public Notification(String recipientPlayerId, String type, String message, Map<String, String> data) {
        this(); // Call default constructor for timestamp and status
        this.recipientPlayerId = recipientPlayerId;
        this.type = type;
        this.message = message;
        this.data = data;
        // ID might be assigned by the service or database
    }

    // Getters and Setters
    @DynamoDbAttribute("NotificationID")
    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("RecipientPlayerID")
    public String getRecipientPlayerId() {
        return recipientPlayerId;
    }

    public void setRecipientPlayerId(String recipientPlayerId) {
        this.recipientPlayerId = recipientPlayerId;
    }

    @DynamoDbAttribute("GameID")
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    @DynamoDbAttribute("Type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @DynamoDbAttribute("Title")
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @DynamoDbAttribute("Message")
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("Timestamp")
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @DynamoDbAttribute("Data")
    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

    @DynamoDbAttribute("Status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @DynamoDbAttribute("Read")
    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    @Override
    public String toString() {
        return "Notification{" +
               "notificationId='" + notificationId + '\'' +
               ", recipientPlayerId='" + recipientPlayerId + '\'' +
               ", gameId='" + gameId + '\'' +
               ", type='" + type + '\'' +
               ", title='" + title + '\'' +
               ", message='" + message + '\'' +
               ", timestamp='" + timestamp + '\'' +
               ", data=" + data +
               ", status='" + status + '\'' +
               ", read=" + read +
               '}';
    }
} 