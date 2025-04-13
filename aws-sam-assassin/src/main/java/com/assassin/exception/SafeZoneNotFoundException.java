package com.assassin.exception;

/**
 * Exception thrown when a safe zone cannot be found.
 */
public class SafeZoneNotFoundException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    public SafeZoneNotFoundException(String message) {
        super(message);
    }
    
    public SafeZoneNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
} 