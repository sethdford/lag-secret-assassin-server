package com.assassin.model;

/**
 * Defines the different types of notifications that can be sent.
 */
public enum NotificationType {
    GAME_START,
    GAME_END,
    TARGET_ASSIGNED,
    ELIMINATION_CONFIRMED,
    ELIMINATION_VERIFICATION_NEEDED,
    PROXIMITY_ALERT, // Alert for nearby target or hunter
    ZONE_WARNING, // Warning about shrinking zone
    PLAYER_JOINED,
    PLAYER_LEFT,
    ADMIN_MESSAGE,
    OTHER
} 