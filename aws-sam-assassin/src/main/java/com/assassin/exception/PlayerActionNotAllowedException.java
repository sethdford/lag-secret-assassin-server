package com.assassin.exception;

/**
 * Exception thrown when a player attempts an action they are not allowed to perform
 * due to their current state or role in the game.
 */
public class PlayerActionNotAllowedException extends RuntimeException {

    public PlayerActionNotAllowedException(String message) {
        super(message);
    }

    public PlayerActionNotAllowedException(String message, Throwable cause) {
        super(message, cause);
    }
} 