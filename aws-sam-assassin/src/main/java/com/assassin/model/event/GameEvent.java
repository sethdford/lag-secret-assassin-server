package com.assassin.model.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all game events published to EventBridge.
 * Provides common fields and metadata for event correlation and tracing.
 */
public class GameEvent {
    private String eventId;
    private EventType eventType;
    private String source;
    private String version;
    private Instant timestamp;
    private Map<String, Object> detail;
    private String correlationId;
    private String traceId;
    private String gameId;
    private String playerId;
    private EventPriority priority;
    private String environment;

    // Default constructor for serialization
    public GameEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.version = "1.0";
        this.source = "assassin-game";
        this.priority = EventPriority.NORMAL;
    }

    public GameEvent(EventType eventType, Map<String, Object> detail) {
        this();
        this.eventType = eventType;
        this.detail = detail;
        this.priority = eventType.isCritical() ? EventPriority.HIGH : EventPriority.NORMAL;
    }

    public GameEvent(EventType eventType, Map<String, Object> detail, String gameId, String playerId) {
        this(eventType, detail);
        this.gameId = gameId;
        this.playerId = playerId;
    }

    // Getters and Setters
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public void setDetail(Map<String, Object> detail) {
        this.detail = detail;
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

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public EventPriority getPriority() {
        return priority;
    }

    public void setPriority(EventPriority priority) {
        this.priority = priority;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    /**
     * Get the EventBridge event pattern for this event
     */
    public String getEventPattern() {
        return eventType.getEventName();
    }

    /**
     * Get the event category (e.g., "player", "game", "security")
     */
    public String getCategory() {
        return eventType.getCategory();
    }

    /**
     * Get the event action (e.g., "created", "updated", "deleted")
     */
    public String getAction() {
        return eventType.getAction();
    }

    /**
     * Check if this event requires immediate processing
     */
    public boolean isCritical() {
        return eventType.isCritical();
    }

    /**
     * Check if this event should be persisted for audit purposes
     */
    public boolean requiresAuditLog() {
        return eventType.requiresAuditLog();
    }

    /**
     * Add contextual information to the event detail
     */
    public void addDetail(String key, Object value) {
        if (detail == null) {
            detail = new java.util.HashMap<>();
        }
        detail.put(key, value);
    }

    /**
     * Get a specific detail value
     */
    @SuppressWarnings("unchecked")
    public <T> T getDetailValue(String key, Class<T> type) {
        if (detail == null) {
            return null;
        }
        Object value = detail.get(key);
        return type.isInstance(value) ? (T) value : null;
    }

    @Override
    public String toString() {
        return "GameEvent{" +
               "eventId='" + eventId + '\'' +
               ", eventType=" + eventType +
               ", source='" + source + '\'' +
               ", timestamp=" + timestamp +
               ", gameId='" + gameId + '\'' +
               ", playerId='" + playerId + '\'' +
               ", priority=" + priority +
               ", correlationId='" + correlationId + '\'' +
               '}';
    }
}