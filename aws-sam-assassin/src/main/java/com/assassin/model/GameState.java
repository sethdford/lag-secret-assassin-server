package com.assassin.model;

/**
 * Represents the possible states of a game.
 */
public enum GameState {
    PENDING,    // Game has been created but not yet started
    ACTIVE,     // Game is currently active with players participating
    COMPLETED,  // Game has been completed with a winner
    CANCELLED   // Game was cancelled before completion
} 