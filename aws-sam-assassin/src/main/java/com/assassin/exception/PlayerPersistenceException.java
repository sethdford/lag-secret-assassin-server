package com.assassin.exception;

public class PlayerPersistenceException extends PersistenceException {
    public PlayerPersistenceException(String message) {
        super(message);
    }

    public PlayerPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
} 