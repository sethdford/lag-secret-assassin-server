package com.assassin.model.event;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Context information for event processing including tracing, correlation, and metadata.
 * This provides a consistent way to pass contextual information through the event processing pipeline.
 */
public class EventContext {
    private String correlationId;
    private String traceId;
    private String requestId;
    private String userId;
    private String gameId;
    private String sessionId;
    private Instant startTime;
    private String source;
    private Map<String, String> metadata;
    private EventPriority priority;

    public EventContext() {
        this.startTime = Instant.now();
        this.metadata = new HashMap<>();
        this.priority = EventPriority.NORMAL;
    }

    public EventContext(String correlationId, String traceId) {
        this();
        this.correlationId = correlationId;
        this.traceId = traceId;
    }

    // Getters and Setters
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

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public EventPriority getPriority() {
        return priority;
    }

    public void setPriority(EventPriority priority) {
        this.priority = priority;
    }

    /**
     * Add metadata key-value pair
     */
    public void addMetadata(String key, String value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }

    /**
     * Get metadata value by key
     */
    public String getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }

    /**
     * Calculate processing duration from start time
     */
    public long getProcessingDurationMs() {
        return Instant.now().toEpochMilli() - startTime.toEpochMilli();
    }

    /**
     * Create a child context with the same correlation/trace information
     */
    public EventContext createChildContext() {
        EventContext child = new EventContext(correlationId, traceId);
        child.setRequestId(requestId);
        child.setUserId(userId);
        child.setGameId(gameId);
        child.setSessionId(sessionId);
        child.setSource(source);
        child.setPriority(priority);
        
        // Copy metadata
        if (metadata != null) {
            child.setMetadata(new HashMap<>(metadata));
        }
        
        return child;
    }

    /**
     * Convert context to a map for logging or event details
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        
        if (correlationId != null) map.put("correlationId", correlationId);
        if (traceId != null) map.put("traceId", traceId);
        if (requestId != null) map.put("requestId", requestId);
        if (userId != null) map.put("userId", userId);
        if (gameId != null) map.put("gameId", gameId);
        if (sessionId != null) map.put("sessionId", sessionId);
        if (source != null) map.put("source", source);
        if (priority != null) map.put("priority", priority.name());
        if (startTime != null) map.put("startTime", startTime.toString());
        if (metadata != null && !metadata.isEmpty()) map.put("metadata", metadata);
        
        return map;
    }

    /**
     * Create context from a map (for deserialization)
     */
    public static EventContext fromMap(Map<String, Object> map) {
        EventContext context = new EventContext();
        
        if (map.containsKey("correlationId")) {
            context.setCorrelationId((String) map.get("correlationId"));
        }
        if (map.containsKey("traceId")) {
            context.setTraceId((String) map.get("traceId"));
        }
        if (map.containsKey("requestId")) {
            context.setRequestId((String) map.get("requestId"));
        }
        if (map.containsKey("userId")) {
            context.setUserId((String) map.get("userId"));
        }
        if (map.containsKey("gameId")) {
            context.setGameId((String) map.get("gameId"));
        }
        if (map.containsKey("sessionId")) {
            context.setSessionId((String) map.get("sessionId"));
        }
        if (map.containsKey("source")) {
            context.setSource((String) map.get("source"));
        }
        if (map.containsKey("priority")) {
            try {
                context.setPriority(EventPriority.valueOf((String) map.get("priority")));
            } catch (IllegalArgumentException e) {
                // Keep default priority if invalid
            }
        }
        if (map.containsKey("startTime")) {
            try {
                context.setStartTime(Instant.parse((String) map.get("startTime")));
            } catch (Exception e) {
                // Keep current time if parsing fails
            }
        }
        if (map.containsKey("metadata")) {
            @SuppressWarnings("unchecked")
            Map<String, String> metadataMap = (Map<String, String>) map.get("metadata");
            context.setMetadata(metadataMap);
        }
        
        return context;
    }

    @Override
    public String toString() {
        return "EventContext{" +
               "correlationId='" + correlationId + '\'' +
               ", traceId='" + traceId + '\'' +
               ", requestId='" + requestId + '\'' +
               ", userId='" + userId + '\'' +
               ", gameId='" + gameId + '\'' +
               ", source='" + source + '\'' +
               ", priority=" + priority +
               ", processingDurationMs=" + getProcessingDurationMs() +
               '}';
    }
}