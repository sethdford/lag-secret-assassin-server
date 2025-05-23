package com.assassin.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Add imports
import com.assassin.integration.AssassinApiIntegrationTestBase;
import com.assassin.integration.TestConstants;

/**
 * Integration tests for Game API endpoints.
 * Demonstrates how to use the AssassinApiIntegrationTestBase class.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GameApiTest extends AssassinApiIntegrationTestBase {

    private String testUserId;
    private String testGameId;
    private String testName;

    @BeforeAll
    public void setUp() {
        // Test setup beyond the base setup
        testName = createUniqueTestName("IntegrationTest");
        // Create a test user
        createTestUser();
    }

    @AfterAll
    public void tearDown() {
        // Clean up test resources
        if (testGameId != null) {
            try {
                // Delete the test game
                callApi("DELETE", "/games/" + testGameId, null, null);
            } catch (Exception e) {
                System.err.println("Failed to clean up test game: " + e.getMessage());
            }
        }
    }

    private void createTestUser() {
        try {
            // Create a user for testing
            Map<String, Object> userRequest = new HashMap<>();
            userRequest.put("username", testName + "-user");
            userRequest.put("email", testName + "@example.com");
            userRequest.put("password", "Password123!");

            Map<String, Object> response = callApi("POST", "/users", userRequest, Map.class);
            testUserId = (String) response.get("userId");
            
            // Could also register the user, login, etc.
            
            assertNotNull(testUserId, "Test user creation failed");
        } catch (Exception e) {
            fail("Failed to create test user: " + e.getMessage());
        }
    }

    @Test
    @Order(1)
    public void testCreateGame() throws IOException {
        // Create a game for testing
        Map<String, Object> gameRequest = new HashMap<>();
        gameRequest.put("name", testName + "-game");
        gameRequest.put("description", "Test game created by integration test");
        gameRequest.put("hostId", testUserId);
        gameRequest.put("maxPlayers", 10);
        gameRequest.put("boundary", TestConstants.createUNCCBoundary());

        Map<String, Object> response = callApi("POST", "/games", gameRequest, Map.class);
        
        testGameId = (String) response.get("gameId");
        
        assertNotNull(testGameId, "Game creation should return a gameId");
        assertEquals(gameRequest.get("name"), response.get("name"), "Game name should match request");
        assertEquals(gameRequest.get("description"), response.get("description"), "Game description should match request");
    }

    @Test
    @Order(2)
    public void testGetGame() throws IOException {
        // Verify we can get the game we created
        assertNotNull(testGameId, "Game ID should be set before running this test");
        
        Map<String, Object> response = callApi("GET", "/games/" + testGameId, null, Map.class);
        
        assertEquals(testGameId, response.get("gameId"), "Returned game should have correct ID");
        assertNotNull(response.get("boundary"), "Game should have a boundary");
    }

    @Test
    @Order(3)
    public void testUpdateGameBoundary() throws IOException {
        // Update the game boundary
        assertNotNull(testGameId, "Game ID should be set before running this test");
        
        // Create a larger boundary
        List<Map<String, Object>> newBoundary = TestConstants.createSquareBoundary(
                TestConstants.UNCC_CAMPUS, 0.02);  // Twice the size of the original
        
        Map<String, Object> response = callApi("PUT", "/games/" + testGameId + "/boundary", 
                newBoundary, Map.class);
        
        assertTrue((Boolean) response.get("success"), "Boundary update should succeed");
    }
} 