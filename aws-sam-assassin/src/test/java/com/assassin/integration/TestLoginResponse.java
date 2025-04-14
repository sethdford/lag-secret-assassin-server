package com.assassin.integration;

/**
 * Custom auth response class for testing.
 */
public class TestLoginResponse {
    private String playerID;
    private String token;
    
    public String getPlayerID() { return playerID; }
    public void setPlayerID(String playerID) { this.playerID = playerID; }
    
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
} 