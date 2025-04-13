package com.assassin.exception;

/**
 * Exception thrown when an operation is attempted in a safe zone where it's not allowed.
 */
public class SafeZoneException extends RuntimeException {

    /**
     * Constructs a new safe zone exception with the specified detail message.
     *
     * @param message the detail message
     */
    public SafeZoneException(String message) {
        super(message);
    }

    /**
     * Constructs a new safe zone exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public SafeZoneException(String message, Throwable cause) {
        super(message, cause);
    }
} 