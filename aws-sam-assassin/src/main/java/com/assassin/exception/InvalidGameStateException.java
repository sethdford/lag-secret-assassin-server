package com.assassin.exception;

/**
 * Exception thrown when an operation is attempted on a game in an invalid state.
 */
public class InvalidGameStateException extends RuntimeException {

    public InvalidGameStateException(String message) {
        super(message);
    }

    public InvalidGameStateException(String message, Throwable cause) {
        super(message, cause);
    }
} 