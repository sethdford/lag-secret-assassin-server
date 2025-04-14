package com.assassin.handlers;

/**
 * Simple POJO to represent the request body for adding a player to a game.
 */
public class AddPlayerRequest {
    private String playerId;

    // Getters and setters are needed for Gson deserialization
    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }
} 