package com.assassin.exception;

/**
 * Custom exception for errors related to invalid player locations,
 * such as being outside game boundaries.
 */
public class InvalidLocationException extends RuntimeException {

    public InvalidLocationException(String message) {
        super(message);
    }

    public InvalidLocationException(String message, Throwable cause) {
        super(message, cause);
    }
} 