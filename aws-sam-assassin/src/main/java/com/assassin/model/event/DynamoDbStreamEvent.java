package com.assassin.model.event;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a DynamoDB Stream event for processing table changes.
 * This wraps the AWS DynamoDB Stream record with additional metadata.
 */
public class DynamoDbStreamEvent {
    private String eventId;
    private String eventName; // INSERT, MODIFY, REMOVE
    private String tableName;
    private Instant timestamp;
    private Map<String, Object> oldImage;
    private Map<String, Object> newImage;
    private String correlationId;
    private String traceId;
    private StreamEventType streamEventType;

    public DynamoDbStreamEvent() {
        this.timestamp = Instant.now();
    }

    public DynamoDbStreamEvent(String eventName, String tableName, 
                              Map<String, Object> oldImage, Map<String, Object> newImage) {
        this();
        this.eventName = eventName;
        this.tableName = tableName;
        this.oldImage = oldImage;
        this.newImage = newImage;
        this.streamEventType = StreamEventType.fromEventName(eventName);
    }

    // Getters and Setters
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
        this.streamEventType = StreamEventType.fromEventName(eventName);
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getOldImage() {
        return oldImage;
    }

    public void setOldImage(Map<String, Object> oldImage) {
        this.oldImage = oldImage;
    }

    public Map<String, Object> getNewImage() {
        return newImage;
    }

    public void setNewImage(Map<String, Object> newImage) {
        this.newImage = newImage;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public StreamEventType getStreamEventType() {
        return streamEventType;
    }

    public void setStreamEventType(StreamEventType streamEventType) {
        this.streamEventType = streamEventType;
    }

    /**
     * Check if this is an insert operation
     */
    public boolean isInsert() {
        return streamEventType == StreamEventType.INSERT;
    }

    /**
     * Check if this is a modify operation
     */
    public boolean isModify() {
        return streamEventType == StreamEventType.MODIFY;
    }

    /**
     * Check if this is a remove operation
     */
    public boolean isRemove() {
        return streamEventType == StreamEventType.REMOVE;
    }

    /**
     * Get a value from the new image
     */
    @SuppressWarnings("unchecked")
    public <T> T getNewValue(String key, Class<T> type) {
        if (newImage == null) {
            return null;
        }
        Object value = newImage.get(key);
        return type.isInstance(value) ? (T) value : null;
    }

    /**
     * Get a value from the old image
     */
    @SuppressWarnings("unchecked")
    public <T> T getOldValue(String key, Class<T> type) {
        if (oldImage == null) {
            return null;
        }
        Object value = oldImage.get(key);
        return type.isInstance(value) ? (T) value : null;
    }

    /**
     * Check if a specific field was changed
     */
    public boolean wasFieldChanged(String fieldName) {
        if (!isModify()) {
            return false;
        }
        
        Object oldValue = oldImage != null ? oldImage.get(fieldName) : null;
        Object newValue = newImage != null ? newImage.get(fieldName) : null;
        
        if (oldValue == null && newValue == null) {
            return false;
        }
        if (oldValue == null || newValue == null) {
            return true;
        }
        
        return !oldValue.equals(newValue);
    }

    /**
     * Get the primary key from the record (works for both old and new images)
     */
    public String getPrimaryKey() {
        Map<String, Object> image = newImage != null ? newImage : oldImage;
        if (image == null) {
            return null;
        }
        
        // Try common primary key field names
        String[] keyFields = {"id", "playerId", "gameId", "killId", "safeZoneId", "transactionId", "reportId"};
        for (String field : keyFields) {
            Object value = image.get(field);
            if (value != null) {
                return value.toString();
            }
        }
        
        return null;
    }

    @Override
    public String toString() {
        return "DynamoDbStreamEvent{" +
               "eventId='" + eventId + '\'' +
               ", eventName='" + eventName + '\'' +
               ", tableName='" + tableName + '\'' +
               ", timestamp=" + timestamp +
               ", streamEventType=" + streamEventType +
               ", primaryKey='" + getPrimaryKey() + '\'' +
               '}';
    }

    /**
     * Enumeration of DynamoDB Stream event types
     */
    public enum StreamEventType {
        INSERT,
        MODIFY,
        REMOVE;

        public static StreamEventType fromEventName(String eventName) {
            if (eventName == null) {
                return null;
            }
            try {
                return valueOf(eventName.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}