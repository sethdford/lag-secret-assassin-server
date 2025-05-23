package com.assassin.integration.e2e;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import com.assassin.integration.AssassinApiIntegrationTestBase;
import static com.assassin.integration.TestConstants.DEFAULT_EMAIL_DOMAIN;
import static com.assassin.integration.TestConstants.DEFAULT_GAME_DESCRIPTION;
import static com.assassin.integration.TestConstants.DEFAULT_MAX_PLAYERS;
import static com.assassin.integration.TestConstants.DEFAULT_PLAYER_NAME;
import static com.assassin.integration.TestConstants.DEFAULT_WEAPON_DISTANCE;
import static com.assassin.integration.TestConstants.TEST_BOUNDARY;
import static com.assassin.integration.TestConstants.TEST_LATITUDE;
import static com.assassin.integration.TestConstants.TEST_LONGITUDE;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * End-to-end tests for Assassin API.
 * These tests simulate an entire game flow from creation to completion.
 */
public class AssassinApiE2ETest extends AssassinApiIntegrationTestBase {

    @Test
    @DisplayName("Complete Game Flow - Create Game, Add Players, Assign Targets, Report Kill")
    public void testCompleteGameFlow(TestInfo testInfo) throws IOException, InterruptedException {
        String testName = createUniqueTestName(testInfo.getDisplayName());
        
        // Step 1: Create a new game
        String gameId = createGame(testName);
        assertNotNull(gameId, "Game ID should not be null");
        
        // Step 2: Add boundary to the game
        addBoundaryToGame(gameId);
        
        // Step 3: Create and add players to the game
        List<Map<String, Object>> players = createAndAddPlayers(gameId, 4);
        assertEquals(4, players.size(), "Should have created 4 players");
        
        // Step 4: Start the game
        startGame(gameId);
        
        // Step 5: Verify targets are assigned
        verifyTargetAssignments(gameId, players);
        
        // Step 6: Report a kill
        String killerId = (String) players.get(0).get("playerId");
        String targetId = (String) players.get(0).get("targetId");
        reportKill(gameId, killerId, targetId);
        
        // Step 7: Verify kill was recorded and new target assigned
        verifyKillRecorded(gameId, killerId, targetId);
        verifyNewTargetAssigned(gameId, killerId);
        
        // Step 8: End the game
        endGame(gameId);
        
        // Step 9: Verify game is completed
        verifyGameCompleted(gameId);
    }
    
    /**
     * Creates a new game for testing
     */
    private String createGame(String testName) throws IOException {
        Map<String, Object> gameRequest = new HashMap<>();
        gameRequest.put("name", testName);
        gameRequest.put("description", DEFAULT_GAME_DESCRIPTION);
        gameRequest.put("weaponDistance", DEFAULT_WEAPON_DISTANCE);
        gameRequest.put("maxPlayers", DEFAULT_MAX_PLAYERS);
        
        JsonObject response = callApi("POST", "/games", gameRequest, JsonObject.class);
        assertTrue(response.has("gameId"), "Response should contain gameId");
        return response.get("gameId").getAsString();
    }
    
    /**
     * Adds a boundary to the game
     */
    private void addBoundaryToGame(String gameId) throws IOException {
        callApi("PUT", "/games/" + gameId + "/boundary", TEST_BOUNDARY, null);
    }
    
    /**
     * Creates and adds players to the game
     */
    private List<Map<String, Object>> createAndAddPlayers(String gameId, int count) throws IOException {
        List<Map<String, Object>> players = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            String email = "player" + i + "-" + UUID.randomUUID() + "@" + DEFAULT_EMAIL_DOMAIN;
            String name = DEFAULT_PLAYER_NAME + " " + i;
            
            Map<String, Object> playerRequest = new HashMap<>();
            playerRequest.put("name", name);
            playerRequest.put("email", email);
            
            JsonObject response = callApi("POST", "/games/" + gameId + "/players", playerRequest, JsonObject.class);
            assertTrue(response.has("playerId"), "Response should contain playerId");
            
            Map<String, Object> player = new HashMap<>();
            player.put("playerId", response.get("playerId").getAsString());
            player.put("name", name);
            player.put("email", email);
            players.add(player);
            
            // Add a starting location for the player
            addPlayerLocation(gameId, (String) player.get("playerId"));
        }
        
        return players;
    }
    
    /**
     * Adds a location for a player
     */
    private void addPlayerLocation(String gameId, String playerId) throws IOException {
        Map<String, Object> locationRequest = new HashMap<>();
        locationRequest.put("latitude", TEST_LATITUDE);
        locationRequest.put("longitude", TEST_LONGITUDE);
        locationRequest.put("accuracy", 10.0);
        locationRequest.put("timestamp", Instant.now().toEpochMilli());
        
        callApi("POST", "/games/" + gameId + "/players/" + playerId + "/location", locationRequest, null);
    }
    
    /**
     * Starts the game
     */
    private void startGame(String gameId) throws IOException {
        Map<String, Object> startRequest = new HashMap<>();
        startRequest.put("status", "active");
        
        callApi("PUT", "/games/" + gameId + "/status", startRequest, null);
    }
    
    /**
     * Verifies target assignments for all players
     */
    private void verifyTargetAssignments(String gameId, List<Map<String, Object>> players) throws IOException {
        JsonObject response = callApi("GET", "/games/" + gameId, null, JsonObject.class);
        assertTrue(response.has("status"), "Response should contain status");
        assertEquals("active", response.get("status").getAsString(), "Game should be active");
        
        // For each player, get their assigned target
        for (Map<String, Object> player : players) {
            String playerId = (String) player.get("playerId");
            JsonObject playerResponse = callApi("GET", "/games/" + gameId + "/players/" + playerId, null, JsonObject.class);
            
            assertTrue(playerResponse.has("targetId"), "Player should have a target assigned");
            String targetId = playerResponse.get("targetId").getAsString();
            assertNotEquals(playerId, targetId, "Player should not be their own target");
            
            // Store the targetId for later use
            player.put("targetId", targetId);
        }
    }
    
    /**
     * Reports a kill
     */
    private void reportKill(String gameId, String killerId, String targetId) throws IOException {
        Map<String, Object> killRequest = new HashMap<>();
        killRequest.put("targetId", targetId);
        killRequest.put("latitude", TEST_LATITUDE);
        killRequest.put("longitude", TEST_LONGITUDE);
        
        callApi("POST", "/games/" + gameId + "/players/" + killerId + "/kills", killRequest, null);
    }
    
    /**
     * Verifies a kill was recorded
     */
    private void verifyKillRecorded(String gameId, String killerId, String targetId) throws IOException {
        JsonObject response = callApi("GET", "/games/" + gameId + "/players/" + killerId + "/kills", null, JsonObject.class);
        assertTrue(response.has("kills"), "Response should contain kills");
        
        JsonArray kills = response.getAsJsonArray("kills");
        assertTrue(kills.size() > 0, "Player should have at least one kill");
        
        boolean found = false;
        for (int i = 0; i < kills.size(); i++) {
            JsonObject kill = kills.get(i).getAsJsonObject();
            if (kill.has("targetId") && kill.get("targetId").getAsString().equals(targetId)) {
                found = true;
                break;
            }
        }
        
        assertTrue(found, "Kill record for target should exist");
    }
    
    /**
     * Verifies a new target was assigned after a kill
     */
    private void verifyNewTargetAssigned(String gameId, String playerId) throws IOException {
        JsonObject playerResponse = callApi("GET", "/games/" + gameId + "/players/" + playerId, null, JsonObject.class);
        assertTrue(playerResponse.has("targetId"), "Player should have a new target assigned");
    }
    
    /**
     * Ends the game
     */
    private void endGame(String gameId) throws IOException {
        Map<String, Object> endRequest = new HashMap<>();
        endRequest.put("status", "completed");
        
        callApi("PUT", "/games/" + gameId + "/status", endRequest, null);
    }
    
    /**
     * Verifies the game is completed
     */
    private void verifyGameCompleted(String gameId) throws IOException {
        JsonObject response = callApi("GET", "/games/" + gameId, null, JsonObject.class);
        assertTrue(response.has("status"), "Response should contain status");
        assertEquals("completed", response.get("status").getAsString(), "Game should be completed");
    }
} 