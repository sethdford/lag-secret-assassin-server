package com.assassin.exception;

public class KillPersistenceException extends PersistenceException {
    public KillPersistenceException(String message) {
        super(message);
    }

    public KillPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
} 