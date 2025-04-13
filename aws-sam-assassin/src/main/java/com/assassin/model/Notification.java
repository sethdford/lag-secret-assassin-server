package com.assassin.model;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a notification message within the system.
 */
public class Notification {

    private String notificationId; // Unique ID for the notification
    private String recipientPlayerId; // ID of the player receiving the notification
    private String type; // Type of notification (e.g., "KILL_VERIFIED", "TARGET_ASSIGNED", "GAME_START", "ADMIN_MESSAGE")
    private String message; // Human-readable message content
    private String timestamp; // ISO 8601 timestamp when the event occurred or notification was generated
    private Map<String, String> data; // Optional structured data related to the notification (e.g., killerId, victimId)
    private String status; // Status of the notification (e.g., "UNREAD", "READ", "ARCHIVED") - may not be needed initially

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
    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public String getRecipientPlayerId() {
        return recipientPlayerId;
    }

    public void setRecipientPlayerId(String recipientPlayerId) {
        this.recipientPlayerId = recipientPlayerId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Notification{" +
               "notificationId='" + notificationId + '\'' +
               ", recipientPlayerId='" + recipientPlayerId + '\'' +
               ", type='" + type + '\'' +
               ", message='" + message + '\'' +
               ", timestamp='" + timestamp + '\'' +
               ", data=" + data +
               ", status='" + status + '\'' +
               '}';
    }
} 