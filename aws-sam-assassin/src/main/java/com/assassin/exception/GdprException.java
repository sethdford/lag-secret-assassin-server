package com.assassin.exception;

/**
 * Exception thrown when GDPR compliance operations fail.
 * This includes data export, data deletion, and consent management operations.
 */
public class GdprException extends Exception {

    /**
     * Constructs a new GDPR exception with the specified detail message.
     *
     * @param message the detail message
     */
    public GdprException(String message) {
        super(message);
    }

    /**
     * Constructs a new GDPR exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public GdprException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new GDPR exception with the specified cause.
     *
     * @param cause the cause
     */
    public GdprException(Throwable cause) {
        super(cause);
    }
}