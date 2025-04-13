package com.assassin.exception;

/**
 * Custom exception for errors occurring during data persistence operations (e.g., DAO interactions).
 */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
} 