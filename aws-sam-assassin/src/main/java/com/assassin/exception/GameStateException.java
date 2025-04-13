package com.assassin.exception;

/**
 * Custom exception for errors related to invalid game states or operations
 * attempted on a game in an inappropriate state.
 */
public class GameStateException extends RuntimeException {

    public GameStateException(String message) {
        super(message);
    }

    public GameStateException(String message, Throwable cause) {
        super(message, cause);
    }
} 