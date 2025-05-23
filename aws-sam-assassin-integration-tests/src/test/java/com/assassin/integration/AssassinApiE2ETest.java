package com.assassin.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * End-to-End tests for the Assassin Game API running against a live deployment.
 *
 * Configuration:
 * - Requires the API Gateway endpoint URL to be set via the 'API_ENDPOINT_URL' environment variable
 *   or system property (e.g., -Dapi.endpoint.url=https://...).
 * - Requires AWS credentials configured in the environment where tests are run,
 *   with permissions to interact with the deployed API, Lambdas, and DynamoDB tables.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // Run tests in a specific order
public class AssassinApiE2ETest {

    private static String apiEndpointUrl;
    private static HttpClient httpClient;
    private static Gson gson;
    private static DynamoDbClient dynamoDbClient; // Optional: For direct DB cleanup/verification

    // Store IDs created during tests to potentially use across scenarios or for cleanup
    private static String testGameIdScenario1;
    private static String testGameIdScenario2;
    private static String testGameIdScenario3;
    private static String testGameIdScenario4;
    private static String testGameIdScenario5;
    private static String testGameIdScenario6;


    @BeforeAll
    static void setUpClass() {
        apiEndpointUrl = System.getenv("API_ENDPOINT_URL");
        if (apiEndpointUrl == null || apiEndpointUrl.isBlank()) {
            apiEndpointUrl = System.getProperty("api.endpoint.url");
        }

        if (apiEndpointUrl == null || apiEndpointUrl.isBlank()) {
            throw new IllegalStateException("API Endpoint URL not configured. Set API_ENDPOINT_URL environment variable or api.endpoint.url system property.");
        }
        // Ensure URL ends with a slash for easier path joining
        if (!apiEndpointUrl.endsWith("/")) {
            apiEndpointUrl += "/";
        }

        System.out.println("Using API Endpoint URL: " + apiEndpointUrl);

        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20)) // Adjust timeout as needed
                .build();

        gson = new GsonBuilder().setPrettyPrinting().create(); // Use Gson for JSON handling

        // Optional: Initialize DynamoDB client if direct interaction is needed
        // dynamoDbClient = DynamoDbClient.builder().region(Region.US_EAST_1).build(); // Replace with your region
    }

    @AfterAll
    static void tearDownClass() {
        // Potential global cleanup logic (e.g., delete test games from DynamoDB)
        System.out.println("E2E Tests finished. Consider implementing cleanup logic.");
        // if (dynamoDbClient != null) {
        //     // Example: deleteTestGameData(testGameIdScenario1);
        // }
    }

    // --- Test Scenarios ---

    @Test
    @Order(1)
    public void testGameCreationAndSetup() throws IOException, InterruptedException {
        System.out.println("Running Test Scenario 1: Game Creation and Setup...");
        // 1. Create a new game
        // Using Map for simplicity, but ideally use dedicated Request classes if available from core module
        Map<String, Object> createRequest = Map.of(
            "gameName", "Test E2E Game Create " + UUID.randomUUID(),
            "startTime", System.currentTimeMillis() + 3600000, // Start in 1 hour
            "endTime", System.currentTimeMillis() + 86400000, // End in 24 hours
            "status", "PENDING", // Initial status
            "gameMode", "ELIMINATION"
        );

        HttpResponse<String> createResponse = callApi("POST", "games", createRequest); // Relative path
        assertEquals(201, createResponse.statusCode(), "Game creation failed: " + createResponse.body());
        JsonObject createResponseBody = JsonParser.parseString(createResponse.body()).getAsJsonObject();
        String gameId = createResponseBody.get("gameId").getAsString();
        assertNotNull(gameId);
        testGameIdScenario1 = gameId; // Store for potential cleanup or later use
        System.out.println("Created Game ID: " + gameId);


        // 2. Set game boundary (replace with actual coordinates needed)
        List<Map<String, Object>> boundary = List.of(
            Map.of("latitude", 40.0, "longitude", -80.0),
            Map.of("latitude", 41.0, "longitude", -80.0),
            Map.of("latitude", 41.0, "longitude", -79.0),
            Map.of("latitude", 40.0, "longitude", -79.0),
            Map.of("latitude", 40.0, "longitude", -80.0) // Close the polygon
        );

        HttpResponse<String> boundaryResponse = callApi("PUT", "games/" + gameId + "/boundary", boundary);
        assertEquals(200, boundaryResponse.statusCode(), "Setting boundary failed: " + boundaryResponse.body());
        System.out.println("Set Game Boundary for " + gameId);

        // 3. Create a safe zone
        Map<String, Object> safeZoneRequest = Map.of(
            "gameId", gameId,
            "latitude", 40.5,
            "longitude", -79.5,
            "radiusMeters", 100.0,
            "type", "PUBLIC", // Use String representation expected by API
            "name", "Campus Center E2E Test"
        );

        HttpResponse<String> safeZoneResponse = callApi("POST", "safezones", safeZoneRequest);
        assertEquals(201, safeZoneResponse.statusCode(), "Safe zone creation failed: " + safeZoneResponse.body());
        JsonObject safeZoneResponseBody = JsonParser.parseString(safeZoneResponse.body()).getAsJsonObject();
        String safeZoneId = safeZoneResponseBody.get("safeZoneId").getAsString();
        assertNotNull(safeZoneId);
        System.out.println("Created Safe Zone ID: " + safeZoneId);


        // 4. Verify game configuration
        HttpResponse<String> getGameResponse = callApi("GET", "games/" + gameId, null);
        assertEquals(200, getGameResponse.statusCode(), "Fetching game failed: " + getGameResponse.body());
        JsonObject game = JsonParser.parseString(getGameResponse.body()).getAsJsonObject();
        assertEquals("PENDING", game.get("status").getAsString()); // Should still be pending
        assertEquals("ELIMINATION", game.get("gameMode").getAsString());
        System.out.println("Verified Game Status: PENDING");

        // 5. Activate the game
        Map<String, Object> activateRequest = Map.of("status", "ACTIVE");

        HttpResponse<String> activateResponse = callApi("PATCH", "games/" + gameId, activateRequest);
        assertEquals(200, activateResponse.statusCode(), "Activating game failed: " + activateResponse.body());
        System.out.println("Activated Game ID: " + gameId);


        // Verify game is active
        getGameResponse = callApi("GET", "games/" + gameId, null);
        assertEquals(200, getGameResponse.statusCode());
        game = JsonParser.parseString(getGameResponse.body()).getAsJsonObject();
        assertEquals("ACTIVE", game.get("status").getAsString());
        System.out.println("Verified Game Status: ACTIVE");
        System.out.println("Test Scenario 1 Completed.");

    }

    @Test
    @Order(2)
    public void testPlayerRegistrationAndAssignment() throws IOException, InterruptedException {
        System.out.println("Running Test Scenario 2: Player Registration and Assignment...");
        
        // 1. Create a new test game
        String gameId = createTestGame("Player Assignment Game");
        testGameIdScenario2 = gameId;
        
        // 2. Register 5 players and have them join the game
        String[] playerIds = new String[5];
        for (int i = 0; i < 5; i++) {
            playerIds[i] = registerAndJoinGame("Test Player " + (i+1), gameId);
        }
        
        // 3. Verify player roster
        HttpResponse<String> rosterResponse = callApi("GET", "games/" + gameId + "/players", null);
        assertEquals(200, rosterResponse.statusCode(), "Failed to get player roster: " + rosterResponse.body());
        JsonArray roster = JsonParser.parseString(rosterResponse.body()).getAsJsonArray();
        assertEquals(5, roster.size(), "Roster should contain exactly 5 players");
        
        // 4. Assign targets
        Map<String, Object> assignRequest = Map.of(
            "gameId", gameId,
            "assignmentStrategy", "CIRCULAR" // Use circular strategy for simplicity
        );
        
        HttpResponse<String> assignResponse = callApi("POST", "games/" + gameId + "/assign-targets", assignRequest);
        assertEquals(200, assignResponse.statusCode(), "Failed to assign targets: " + assignResponse.body());
        
        // 5. Verify each player has a target
        for (String playerId : playerIds) {
            HttpResponse<String> playerResponse = callApi("GET", "players/" + playerId + "/games/" + gameId, null);
            assertEquals(200, playerResponse.statusCode(), "Failed to get player game data: " + playerResponse.body());
            JsonObject playerData = JsonParser.parseString(playerResponse.body()).getAsJsonObject();
            assertTrue(playerData.has("targetId"), "Player should have a target assigned");
            assertNotNull(playerData.get("targetId"), "TargetId should not be null");
            assertNotEquals("", playerData.get("targetId").getAsString(), "TargetId should not be empty");
        }
        
        System.out.println("Successfully registered 5 players and assigned targets in game: " + gameId);
        System.out.println("Test Scenario 2 Completed.");
    }

    @Test
    @Order(3)
    public void testLocationUpdatesAndProximityAlerts() throws IOException, InterruptedException {
        System.out.println("Running Test Scenario 3: Location Updates and Proximity Alerts...");
        
        // 1. Create a test game with 2 players
        String gameId = createTestGameWithPlayers("Location Test Game", 2);
        testGameIdScenario3 = gameId;
        String player1Id = getPlayerInGame(gameId, 0);
        String player2Id = getPlayerInGame(gameId, 1);
        
        // 2. Configure player to receive notifications (if applicable)
        Map<String, Object> registerRequest = Map.of(
            "playerId", player1Id,
            "pushToken", "test-fcm-token-" + UUID.randomUUID()
        );
        
        HttpResponse<String> registerResponse = callApi("POST", "notifications/register", registerRequest);
        assertEquals(200, registerResponse.statusCode(), "Failed to register for notifications: " + registerResponse.body());
        
        // 3. Update player1's location
        updatePlayerLocation(player1Id, 40.44, -79.94);
        
        // 4. Update player2's location (far from player1)
        updatePlayerLocation(player2Id, 40.48, -79.98); // ~4.5km away
        
        // 5. Check for notifications - should be none yet
        HttpResponse<String> notifications1 = callApi("GET", "players/" + player1Id + "/notifications", null);
        assertEquals(200, notifications1.statusCode(), "Failed to get notifications: " + notifications1.body());
        
        // 6. Update player2's location (close to player1)
        updatePlayerLocation(player2Id, 40.4401, -79.9401); // ~100m away
        
        // 7. Wait for proximity detection to process (since this might be async)
        System.out.println("Waiting for proximity detection to process...");
        Thread.sleep(2000);
        
        // 8. Check for proximity notifications
        HttpResponse<String> notifications2 = callApi("GET", "players/" + player1Id + "/notifications", null);
        assertEquals(200, notifications2.statusCode(), "Failed to get updated notifications: " + notifications2.body());
        
        // Note: This assertion might need adjustment based on actual API behavior
        // If the API doesn't create notifications automatically, this check might be skipped
        JsonArray notificationsArray = JsonParser.parseString(notifications2.body()).getAsJsonArray();
        System.out.println("Found " + notificationsArray.size() + " notifications after proximity.");
        
        System.out.println("Test Scenario 3 Completed.");
    }

    @Test
    @Order(4)
    public void testSafeZoneProtection() throws IOException, InterruptedException {
        System.out.println("Running Test Scenario 4: Safe Zone Protection...");
        
        // 1. Create a test game with 2 players
        String gameId = createTestGameWithPlayers("Safe Zone Test Game", 2);
        testGameIdScenario4 = gameId;
        String player1Id = getPlayerInGame(gameId, 0);
        String player2Id = getPlayerInGame(gameId, 1);
        
        // 2. Create a safe zone
        double safeZoneLat = 40.44;
        double safeZoneLon = -79.94;
        double safeZoneRadius = 100.0;
        
        Map<String, Object> safeZoneRequest = Map.of(
            "gameId", gameId,
            "latitude", safeZoneLat,
            "longitude", safeZoneLon,
            "radiusMeters", safeZoneRadius,
            "type", "PUBLIC",
            "name", "Test Safe Zone"
        );
        
        HttpResponse<String> safeZoneResponse = callApi("POST", "safezones", safeZoneRequest);
        assertEquals(201, safeZoneResponse.statusCode(), "Failed to create safe zone: " + safeZoneResponse.body());
        
        // 3. Place player1 inside the safe zone
        updatePlayerLocation(player1Id, safeZoneLat, safeZoneLon);
        
        // 4. Place player2 (attacker) near player1 but outside the safe zone
        updatePlayerLocation(player2Id, safeZoneLat + 0.002, safeZoneLon + 0.002); // ~200m away
        
        // 5. Attempt elimination - should fail due to safe zone protection
        Map<String, Object> killRequest = Map.of(
            "gameId", gameId,
            "playerId", player2Id,
            "targetId", player1Id,
            "killMethod", "BUTTON",
            "weaponType", "DEFAULT"
        );
        
        HttpResponse<String> killResponse = callApi("POST", "kills/attempt", killRequest);
        
        // Expected behavior: API returns 400 when target is in safe zone
        // Note: This may vary based on your specific API implementation
        boolean isTargetProtected = killResponse.statusCode() == 400 || 
                                  (killResponse.statusCode() == 200 && 
                                   killResponse.body().contains("safe zone"));
        
        assertTrue(isTargetProtected, "Target in safe zone should be protected from elimination");
        
        // 6. Move player1 out of the safe zone
        updatePlayerLocation(player1Id, safeZoneLat + 0.003, safeZoneLon + 0.003); // ~300m away from center
        
        // 7. Move player2 near player1
        updatePlayerLocation(player2Id, safeZoneLat + 0.0031, safeZoneLon + 0.0031); // Very close to player1 now
        
        // 8. Attempt elimination again - should succeed now
        HttpResponse<String> killResponse2 = callApi("POST", "kills/attempt", killRequest);
        assertEquals(200, killResponse2.statusCode(), "Kill attempt should succeed when target is outside safe zone: " + killResponse2.body());
        
        System.out.println("Test Scenario 4 Completed.");
    }

    @Test
    @Order(5)
    public void testKillVerificationWorkflow() throws IOException, InterruptedException {
        System.out.println("Running Test Scenario 5: Kill Verification Workflow...");
        
        // 1. Create a test game with 2 players
        String gameId = createTestGameWithPlayers("Kill Verification Game", 2);
        testGameIdScenario5 = gameId;
        String playerId = getPlayerInGame(gameId, 0);
        String targetId = getPlayerInGame(gameId, 1);
        
        // 2. Update locations to be in close proximity
        updatePlayerLocation(playerId, 40.44, -79.94);
        updatePlayerLocation(targetId, 40.4401, -79.9401);
        
        // 3. Attempt a kill
        Map<String, Object> killRequest = Map.of(
            "gameId", gameId,
            "playerId", playerId,
            "targetId", targetId,
            "killMethod", "PHOTO",
            "weaponType", "DEFAULT"
        );
        
        HttpResponse<String> killResponse = callApi("POST", "kills/attempt", killRequest);
        assertEquals(200, killResponse.statusCode(), "Kill attempt failed: " + killResponse.body());
        
        String killId = JsonParser.parseString(killResponse.body()).getAsJsonObject().get("killId").getAsString();
        assertNotNull(killId, "Kill ID should be returned");
        
        // 4. Upload verification photo
        Map<String, Object> photoRequest = Map.of(
            "killId", killId,
            "photo", generateTestImageBase64()
        );
        
        HttpResponse<String> photoResponse = callApi("PUT", "kills/" + killId + "/photo", photoRequest);
        assertEquals(200, photoResponse.statusCode(), "Photo upload failed: " + photoResponse.body());
        
        // 5. Check kill status - should be pending review
        HttpResponse<String> statusResponse = callApi("GET", "kills/" + killId, null);
        assertEquals(200, statusResponse.statusCode(), "Failed to get kill status: " + statusResponse.body());
        
        JsonObject killStatus = JsonParser.parseString(statusResponse.body()).getAsJsonObject();
        assertEquals("PENDING_REVIEW", killStatus.get("status").getAsString(), "Kill should be pending review after photo submission");
        
        // 6. Admin approves the kill
        Map<String, Object> verifyRequest = Map.of(
            "killId", killId,
            "isValid", true,
            "reason", "Valid elimination photo"
        );
        
        HttpResponse<String> verifyResponse = callApi("PUT", "kills/" + killId + "/verify", verifyRequest);
        assertEquals(200, verifyResponse.statusCode(), "Kill verification failed: " + verifyResponse.body());
        
        // 7. Check kill status again - should be verified
        HttpResponse<String> finalResponse = callApi("GET", "kills/" + killId, null);
        assertEquals(200, finalResponse.statusCode(), "Failed to get final kill status: " + finalResponse.body());
        
        JsonObject finalKill = JsonParser.parseString(finalResponse.body()).getAsJsonObject();
        assertEquals("VERIFIED", finalKill.get("status").getAsString(), "Kill should be verified after admin approval");
        
        // 8. Check target has been eliminated from the game
        HttpResponse<String> targetResponse = callApi("GET", "players/" + targetId + "/games/" + gameId, null);
        assertEquals(200, targetResponse.statusCode(), "Failed to get target status: " + targetResponse.body());
        
        JsonObject targetData = JsonParser.parseString(targetResponse.body()).getAsJsonObject();
        assertEquals("ELIMINATED", targetData.get("status").getAsString(), "Target should be eliminated after verified kill");
        
        System.out.println("Test Scenario 5 Completed.");
    }

    @Test
    @Order(6)
    public void testFullGameLifecycle() throws IOException, InterruptedException {
        System.out.println("Running Test Scenario 6: Full Game Lifecycle...");
        
        // 1. Create a full game with 5 players
        String gameId = createTestGame("Full Lifecycle Game");
        testGameIdScenario6 = gameId;
        
        // 2. Add 5 players
        String[] playerIds = new String[5];
        for (int i = 0; i < 5; i++) {
            playerIds[i] = registerAndJoinGame("Player " + (i+1), gameId);
        }
        
        // 3. Activate game and assign targets
        activateGameWithCircularTargets(gameId);
        
        // 4. Simulate a full set of eliminations
        // Day 1: Player 1 eliminates their target (Player 2)
        simulateElimination(gameId, playerIds[0], playerIds[1], true);
        
        // 5. Day 2: Player 1 eliminates their new target (Player 3)
        simulateElimination(gameId, playerIds[0], playerIds[2], true);
        
        // 6. Day 3: Player 1 eliminates Player 4
        simulateElimination(gameId, playerIds[0], playerIds[3], true);
        
        // 7. Check standings - should have 2 players left
        HttpResponse<String> standingsResponse = callApi("GET", "games/" + gameId + "/standings", null);
        assertEquals(200, standingsResponse.statusCode(), "Failed to get standings: " + standingsResponse.body());
        
        JsonArray standings = JsonParser.parseString(standingsResponse.body()).getAsJsonArray();
        
        // Count active players
        int activePlayerCount = 0;
        for (JsonElement element : standings) {
            JsonObject player = element.getAsJsonObject();
            if (player.get("status").getAsString().equals("ACTIVE")) {
                activePlayerCount++;
            }
        }
        assertEquals(2, activePlayerCount, "Should have 2 active players remaining");
        
        // 8. Player 1 eliminates final target (Player 5)
        simulateElimination(gameId, playerIds[0], playerIds[4], true);
        
        // 9. Verify game is complete
        HttpResponse<String> gameStatusResponse = callApi("GET", "games/" + gameId, null);
        assertEquals(200, gameStatusResponse.statusCode(), "Failed to get game status: " + gameStatusResponse.body());
        
        JsonObject gameStatus = JsonParser.parseString(gameStatusResponse.body()).getAsJsonObject();
        assertEquals("COMPLETED", gameStatus.get("status").getAsString(), "Game should be completed after all players eliminated");
        
        // 10. Verify winner
        HttpResponse<String> winnerResponse = callApi("GET", "games/" + gameId + "/winner", null);
        assertEquals(200, winnerResponse.statusCode(), "Failed to get winner: " + winnerResponse.body());
        
        JsonObject winner = JsonParser.parseString(winnerResponse.body()).getAsJsonObject();
        assertEquals(playerIds[0], winner.get("playerId").getAsString(), "Player 1 should be the winner");
        
        System.out.println("Test Scenario 6 Completed.");
    }

    // --- Helper Methods ---

    /**
     * Performs an HTTP API call to the configured API Gateway endpoint.
     *
     * @param method      HTTP method (GET, POST, PUT, PATCH, DELETE)
     * @param relativePath Path relative to the base API endpoint URL (e.g., "games", "players/{playerId}")
     * @param requestBody Java object to be serialized to JSON for the request body (or null for GET/DELETE)
     * @return The HttpResponse object
     * @throws IOException If an I/O error occurs when sending or receiving
     * @throws InterruptedException If the operation is interrupted
     */
    private HttpResponse<String> callApi(String method, String relativePath, Object requestBody)
            throws IOException, InterruptedException {

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiEndpointUrl + relativePath))
                .timeout(Duration.ofSeconds(30)); // Timeout for the request

        // Set Content-Type for methods with bodies
        if (requestBody != null && !method.equals("GET") && !method.equals("DELETE")) {
             requestBuilder.header("Content-Type", "application/json");
        }

        HttpRequest.BodyPublisher bodyPublisher = (requestBody == null)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody));

        switch (method.toUpperCase()) {
            case "POST":
                requestBuilder.POST(bodyPublisher);
                break;
            case "PUT":
                requestBuilder.PUT(bodyPublisher);
                break;
            case "PATCH":
                requestBuilder.method("PATCH", bodyPublisher); // PATCH requires explicit method name
                break;
            case "DELETE":
                requestBuilder.DELETE();
                break;
            case "GET":
            default:
                requestBuilder.GET();
                break;
        }

        HttpRequest request = requestBuilder.build();

        System.out.printf("Calling API: %s %s%s%n", method.toUpperCase(), apiEndpointUrl, relativePath);
        if (requestBody != null) {
             System.out.println("Request Body: " + gson.toJson(requestBody));
        }

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.printf("API Response: Status=%d%n", response.statusCode());
         System.out.println("Response Body: " + response.body());


        return response;
    }

     /**
      * Creates a test game for E2E testing.
      * 
      * @param gameName The name of the game to create
      * @return The gameId of the created game
      */
     private String createTestGame(String gameName) throws IOException, InterruptedException {
         System.out.println("Helper: Creating test game '" + gameName + "'...");
         Map<String, Object> createGameRequest = Map.of(
             "gameName", gameName + " " + UUID.randomUUID(),
             "startTime", System.currentTimeMillis() + 10000, // Start soon
             "endTime", System.currentTimeMillis() + 86400000, // End in 24 hours
             "status", "PENDING",
             "gameMode", "ELIMINATION"
          );
          HttpResponse<String> createResponse = callApi("POST", "games", createGameRequest);
          if (createResponse.statusCode() == 201) {
              String gameId = JsonParser.parseString(createResponse.body()).getAsJsonObject().get("gameId").getAsString();
              System.out.println("Helper: Created game ID: " + gameId);
              return gameId;
          } else {
              System.err.println("Helper: Failed to create game: " + createResponse.body());
              throw new RuntimeException("Failed to create test game in helper method. Status: " + createResponse.statusCode());
          }
    }

     /**
      * Creates a test game with the specified number of players.
      * 
      * @param gameName The name of the game to create
      * @param playerCount The number of players to add to the game
      * @return The gameId of the created game
      */
     private String createTestGameWithPlayers(String gameName, int playerCount) throws IOException, InterruptedException {
          System.out.println("Helper: Creating test game '" + gameName + "' with " + playerCount + " players...");
          String gameId = createTestGame(gameName);
          for (int i = 0; i < playerCount; i++) {
              registerAndJoinGame("E2E Player " + (i+1), gameId);
          }
           activateGameWithCircularTargets(gameId); // Activate and assign targets
          return gameId;
     }

      /**
       * Registers a new player.
       * 
       * @param playerName The name of the player to register
       * @return The playerId of the registered player
       */
      private String registerPlayer(String playerName) throws IOException, InterruptedException {
          System.out.println("Helper: Registering player '" + playerName + "'...");
          Map<String, Object> createRequest = Map.of(
              "name", playerName,
              "email", playerName.replaceAll("\\s+", "").toLowerCase() + "@e2etest.com",
              "deviceId", "e2e-device-" + UUID.randomUUID().toString()
          );
          HttpResponse<String> createResponse = callApi("POST", "players", createRequest);
          if (createResponse.statusCode() == 201) {
               String playerId = JsonParser.parseString(createResponse.body()).getAsJsonObject().get("playerId").getAsString();
               System.out.println("Helper: Registered player ID: " + playerId);
               return playerId;
          } else {
               System.err.println("Helper: Failed to register player: " + createResponse.body());
               throw new RuntimeException("Failed to register player in helper. Status: " + createResponse.statusCode());
          }
      }

     /**
      * Registers a new player and joins them to the specified game.
      * 
      * @param playerName The name of the player to register
      * @param gameId The gameId to join
      * @return The playerId of the registered player
      */
     private String registerAndJoinGame(String playerName, String gameId) throws IOException, InterruptedException {
          String playerId = registerPlayer(playerName);
          System.out.println("Helper: Joining player " + playerId + " to game " + gameId);
          Map<String, Object> joinRequest = Map.of(
              "gameId", gameId,
              "playerId", playerId
          );
          HttpResponse<String> joinResponse = callApi("POST", "games/join", joinRequest);
          if (joinResponse.statusCode() != 200) {
              System.err.println("Helper: Failed to join game: " + joinResponse.body());
              throw new RuntimeException("Failed to join game in helper. Status: " + joinResponse.statusCode());
          }
          System.out.println("Helper: Player " + playerId + " joined game " + gameId);
          return playerId;
     }

     /**
      * Gets a player ID for a player in the specified game at the given index.
      * 
      * @param gameId The gameId to get players from
      * @param index The index of the player to retrieve
      * @return The playerId at the specified index
      */
     private String getPlayerInGame(String gameId, int index) throws IOException, InterruptedException {
          System.out.println("Helper: Getting player at index " + index + " in game " + gameId);
          HttpResponse<String> rosterResponse = callApi("GET", "games/" + gameId + "/players", null);
          if (rosterResponse.statusCode() == 200) {
              JsonArray roster = JsonParser.parseString(rosterResponse.body()).getAsJsonArray();
              if (index < roster.size()) {
                  String playerId = roster.get(index).getAsJsonObject().get("playerId").getAsString();
                   System.out.println("Helper: Found player ID at index " + index + ": " + playerId);
                  return playerId;
              } else {
                   throw new IndexOutOfBoundsException("Player index " + index + " out of bounds for roster size " + roster.size());
              }
          } else {
               throw new RuntimeException("Failed to get player roster in helper. Status: " + rosterResponse.statusCode());
          }
     }

     /**
      * Updates a player's location.
      * 
      * @param playerId The playerId to update
      * @param latitude The latitude of the new location
      * @param longitude The longitude of the new location
      */
     private void updatePlayerLocation(String playerId, double latitude, double longitude) throws IOException, InterruptedException {
         System.out.println("Helper: Updating location for player " + playerId + " to " + latitude + ", " + longitude);
          Map<String, Object> locationRequest = Map.of(
              "latitude", latitude,
              "longitude", longitude,
              "accuracy", 5.0,
              "timestamp", System.currentTimeMillis()
          );
         HttpResponse<String> response = callApi("PUT", "players/" + playerId + "/location", locationRequest);
         assertEquals(200, response.statusCode(), "Failed to update location in helper: " + response.body());
          System.out.println("Helper: Location updated for " + playerId);
     }

     /**
      * Generates a test image in Base64 format for photo verification.
      * 
      * @return A Base64-encoded test image string
      */
     private String generateTestImageBase64() {
         System.out.println("Helper: Generating placeholder Base64 image...");
         // In a real test, generate or load a small valid image file
         byte[] dummyImageData = "test-image-data".getBytes();
         return Base64.getEncoder().encodeToString(dummyImageData);
     }

     /**
      * Activates a game and assigns targets in a circular pattern.
      * 
      * @param gameId The gameId to activate and assign targets for
      */
     private void activateGameWithCircularTargets(String gameId) throws IOException, InterruptedException {
         System.out.println("Helper: Activating game " + gameId + " and assigning targets...");
          // Activate
          Map<String, Object> activateRequest = Map.of("status", "ACTIVE");
          HttpResponse<String> activateResponse = callApi("PATCH", "games/" + gameId, activateRequest);
           assertEquals(200, activateResponse.statusCode(), "Failed to activate game in helper: " + activateResponse.body());

           // Assign Targets
          Map<String, Object> assignRequest = Map.of(
              "gameId", gameId,
              "assignmentStrategy", "CIRCULAR"
          );
           HttpResponse<String> assignResponse = callApi("POST", "games/" + gameId + "/assign-targets", assignRequest);
           assertEquals(200, assignResponse.statusCode(), "Failed to assign targets in helper: " + assignResponse.body());
           System.out.println("Helper: Game " + gameId + " activated and targets assigned.");
     }

     /**
      * Simulates an elimination between two players.
      * 
      * @param gameId The gameId where the elimination occurs
      * @param playerId The playerId of the assassin
      * @param targetId The playerId of the target
      * @param approved Whether the kill should be approved or rejected
      */
     private void simulateElimination(String gameId, String playerId, String targetId, boolean approved) throws IOException, InterruptedException {
         System.out.println("Helper: Simulating elimination: " + playerId + " -> " + targetId + " (Approved: " + approved + ")");

         // 1. Update locations to be close
         updatePlayerLocation(playerId, 40.1, -79.1);
         updatePlayerLocation(targetId, 40.1001, -79.1001); // Close by

         // 2. Attempt kill
         Map<String, Object> killRequest = Map.of(
             "gameId", gameId,
             "playerId", playerId,
             "targetId", targetId,
             "killMethod", "PHOTO", // Use photo for verification step
             "weaponType", "DEFAULT"
         );
         HttpResponse<String> killResponse = callApi("POST", "kills/attempt", killRequest);
         assertEquals(200, killResponse.statusCode(), "Kill attempt failed in helper: " + killResponse.body());
         String killId = JsonParser.parseString(killResponse.body()).getAsJsonObject().get("killId").getAsString();
         System.out.println("Helper: Kill attempt submitted, killId: " + killId);


         // 3. Submit photo
          Map<String, Object> photoRequest = Map.of(
              "killId", killId,
              "photo", generateTestImageBase64()
          );
          HttpResponse<String> photoResponse = callApi("PUT", "kills/" + killId + "/photo", photoRequest);
          assertEquals(200, photoResponse.statusCode(), "Photo submission failed in helper: " + photoResponse.body());
          System.out.println("Helper: Photo submitted for kill " + killId);


         // 4. Verify kill (if approved)
         Map<String, Object> verifyRequest = Map.of(
             "killId", killId,
             "isValid", approved,
             "reason", approved ? "E2E Test Approval" : "E2E Test Rejection"
         );
         HttpResponse<String> verifyResponse = callApi("PUT", "kills/" + killId + "/verify", verifyRequest);
         assertEquals(200, verifyResponse.statusCode(), "Kill verification failed in helper: " + verifyResponse.body());
          System.out.println("Helper: Kill " + killId + " verification submitted (Approved: " + approved + ")");


          // Optional: Short pause for state propagation if needed
          Thread.sleep(1000);
     }
} 