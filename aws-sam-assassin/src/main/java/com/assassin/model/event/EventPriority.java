package com.assassin.model.event;

/**
 * Priority levels for event processing.
 * Higher priority events are processed first and may have different retry policies.
 */
public enum EventPriority {
    LOW(1),
    NORMAL(2),
    HIGH(3),
    CRITICAL(4);

    private final int level;

    EventPriority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    /**
     * Get retry count based on priority
     */
    public int getRetryCount() {
        switch (this) {
            case CRITICAL:
                return 5;
            case HIGH:
                return 3;
            case NORMAL:
                return 2;
            case LOW:
            default:
                return 1;
        }
    }

    /**
     * Get processing timeout in seconds based on priority
     */
    public int getProcessingTimeoutSeconds() {
        switch (this) {
            case CRITICAL:
                return 10;
            case HIGH:
                return 30;
            case NORMAL:
                return 60;
            case LOW:
            default:
                return 120;
        }
    }

    /**
     * Check if this priority level should trigger immediate processing
     */
    public boolean requiresImmediateProcessing() {
        return this == CRITICAL || this == HIGH;
    }
}