package com.assassin.model;

/**
 * Represents the possible statuses of a game.
 */
public enum GameStatus {
    PENDING, // Game created, waiting for players or start
    ACTIVE,  // Game in progress
    COMPLETED, // Game finished normally (winner declared)
    CANCELLED // Game stopped prematurely
} 