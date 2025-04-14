package com.assassin.exception;

public class KillNotFoundException extends RuntimeException {
    public KillNotFoundException(String message) {
        super(message);
    }

    public KillNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
} 