package com.assassin.exception;

public class GamePersistenceException extends PersistenceException {
    public GamePersistenceException(String message) {
        super(message);
    }

    public GamePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
} 