package com.assassin.exception;

/**
 * Exception thrown when a required configuration (e.g., MapConfiguration) cannot be found.
 */
public class ConfigurationNotFoundException extends RuntimeException {

    /**
     * Constructs a new ConfigurationNotFoundException with the specified detail message.
     *
     * @param message the detail message.
     */
    public ConfigurationNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new ConfigurationNotFoundException with the specified detail message and cause.
     *
     * @param message the detail message.
     * @param cause the cause (which is saved for later retrieval by the getCause() method).
     */
    public ConfigurationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
} 