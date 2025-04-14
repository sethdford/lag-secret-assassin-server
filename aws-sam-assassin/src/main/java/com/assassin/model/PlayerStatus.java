package com.assassin.model;

/**
 * Represents the possible states of a player within a game.
 */
public enum PlayerStatus {
    PENDING,     // Player has registered but not yet active in a game
    ACTIVE,      // Player is currently playing in an active game
    DEAD,        // Player has been eliminated from the game
    WINNER,      // Player has won the game
    INVITED,     // Player has been invited but hasn't accepted
    DECLINED,    // Player has declined an invitation
    REMOVED      // Player was removed from the game by an admin
} 