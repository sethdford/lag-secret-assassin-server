package com.assassin.model.event;

/**
 * Enumeration of all event types that can be published through the event system.
 * These events represent significant business domain activities that other services may need to react to.
 */
public enum EventType {
    // Player Events
    PLAYER_CREATED("assassin.player.created"),
    PLAYER_UPDATED("assassin.player.updated"),
    PLAYER_STATUS_CHANGED("assassin.player.status_changed"),
    PLAYER_LOCATION_UPDATED("assassin.player.location_updated"),
    PLAYER_JOINED_GAME("assassin.player.joined_game"),
    PLAYER_LEFT_GAME("assassin.player.left_game"),
    PLAYER_ELIMINATED("assassin.player.eliminated"),
    PLAYER_SUBSCRIPTION_CHANGED("assassin.player.subscription_changed"),
    
    // Game Events
    GAME_CREATED("assassin.game.created"),
    GAME_STARTED("assassin.game.started"),
    GAME_ENDED("assassin.game.ended"),
    GAME_STATUS_CHANGED("assassin.game.status_changed"),
    GAME_BOUNDARY_UPDATED("assassin.game.boundary_updated"),
    
    // Kill Events
    KILL_REPORTED("assassin.kill.reported"),
    KILL_VERIFIED("assassin.kill.verified"),
    KILL_DISPUTED("assassin.kill.disputed"),
    KILL_CONFIRMED("assassin.kill.confirmed"),
    
    // Zone Events
    ZONE_SHRINK_STARTED("assassin.zone.shrink_started"),
    ZONE_SHRINK_COMPLETED("assassin.zone.shrink_completed"),
    ZONE_DAMAGE_APPLIED("assassin.zone.damage_applied"),
    PLAYER_ENTERED_ZONE("assassin.zone.player_entered"),
    PLAYER_EXITED_ZONE("assassin.zone.player_exited"),
    
    // Safe Zone Events
    SAFE_ZONE_CREATED("assassin.safe_zone.created"),
    SAFE_ZONE_UPDATED("assassin.safe_zone.updated"),
    SAFE_ZONE_DELETED("assassin.safe_zone.deleted"),
    PLAYER_ENTERED_SAFE_ZONE("assassin.safe_zone.player_entered"),
    PLAYER_EXITED_SAFE_ZONE("assassin.safe_zone.player_exited"),
    
    // Security Events
    SECURITY_THREAT_DETECTED("assassin.security.threat_detected"),
    SECURITY_VIOLATION_OCCURRED("assassin.security.violation_occurred"),
    PLAYER_BLOCKED("assassin.security.player_blocked"),
    SUSPICIOUS_ACTIVITY_DETECTED("assassin.security.suspicious_activity"),
    LOCATION_SPOOFING_DETECTED("assassin.security.location_spoofing"),
    
    // Payment Events
    PAYMENT_INITIATED("assassin.payment.initiated"),
    PAYMENT_COMPLETED("assassin.payment.completed"),
    PAYMENT_FAILED("assassin.payment.failed"),
    SUBSCRIPTION_CREATED("assassin.subscription.created"),
    SUBSCRIPTION_UPDATED("assassin.subscription.updated"),
    SUBSCRIPTION_CANCELLED("assassin.subscription.cancelled"),
    SUBSCRIPTION_RENEWED("assassin.subscription.renewed"),
    
    // Notification Events
    NOTIFICATION_SENT("assassin.notification.sent"),
    NOTIFICATION_DELIVERED("assassin.notification.delivered"),
    NOTIFICATION_FAILED("assassin.notification.failed"),
    
    // Analytics Events
    ANALYTICS_EVENT_CAPTURED("assassin.analytics.event_captured"),
    PERFORMANCE_METRIC_RECORDED("assassin.analytics.performance_metric"),
    USER_ENGAGEMENT_TRACKED("assassin.analytics.user_engagement"),
    
    // System Events
    SYSTEM_HEALTH_CHECK("assassin.system.health_check"),
    SYSTEM_ERROR_OCCURRED("assassin.system.error_occurred"),
    RATE_LIMIT_EXCEEDED("assassin.system.rate_limit_exceeded"),
    DATA_EXPORT_COMPLETED("assassin.system.data_export_completed"),
    
    // WebSocket Events
    WEBSOCKET_CONNECTION_ESTABLISHED("assassin.websocket.connection_established"),
    WEBSOCKET_CONNECTION_CLOSED("assassin.websocket.connection_closed"),
    WEBSOCKET_MESSAGE_SENT("assassin.websocket.message_sent"),
    REAL_TIME_UPDATE_BROADCAST("assassin.websocket.real_time_update");

    private final String eventName;

    EventType(String eventName) {
        this.eventName = eventName;
    }

    public String getEventName() {
        return eventName;
    }

    /**
     * Get event category from event name (first part before the dot)
     */
    public String getCategory() {
        String[] parts = eventName.split("\\.");
        return parts.length > 1 ? parts[1] : "unknown";
    }

    /**
     * Get event action from event name (last part after the dot)
     */
    public String getAction() {
        String[] parts = eventName.split("\\.");
        return parts.length > 2 ? parts[2] : "unknown";
    }

    /**
     * Check if this is a critical event that requires immediate processing
     */
    public boolean isCritical() {
        return this == SECURITY_THREAT_DETECTED ||
               this == SECURITY_VIOLATION_OCCURRED ||
               this == SYSTEM_ERROR_OCCURRED ||
               this == PAYMENT_FAILED ||
               this == PLAYER_ELIMINATED ||
               this == GAME_ENDED;
    }

    /**
     * Check if this event should be persisted for audit purposes
     */
    public boolean requiresAuditLog() {
        return getCategory().equals("security") ||
               getCategory().equals("payment") ||
               this == PLAYER_ELIMINATED ||
               this == KILL_VERIFIED ||
               this == GAME_ENDED;
    }

    /**
     * Get event type from event name string
     */
    public static EventType fromEventName(String eventName) {
        for (EventType type : values()) {
            if (type.eventName.equals(eventName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown event type: " + eventName);
    }
}