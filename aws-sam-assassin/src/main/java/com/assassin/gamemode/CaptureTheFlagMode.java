package com.assassin.gamemode;

import com.assassin.models.gamemode.GameMode;
import com.assassin.model.*;
import com.assassin.util.GeoUtils;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Capture The Flag game mode implementation.
 * Two teams compete to capture each other's flags and return them to their base.
 */
public class CaptureTheFlagMode implements GameMode {
    
    private static final Gson GSON = new Gson();
    private static final int FLAG_CAPTURE_RADIUS = 10; // meters
    private static final int BASE_RADIUS = 30; // meters
    private static final int FLAG_RESET_TIME = 60; // seconds after drop
    
    // Game state storage (in production, this would be in DynamoDB)
    private final Map<String, CTFGameState> gameStates = new ConcurrentHashMap<>();
    
    @Override
    public String getModeId() {
        return "capture_the_flag";
    }
    
    @Override
    public String getModeName() {
        return "Capture The Flag";
    }
    
    @Override
    public String getDescription() {
        return "Two teams compete to capture the enemy flag and return it to their base. " +
               "First team to 3 captures wins!";
    }
    
    @Override
    public int getMinPlayers() {
        return 6; // 3v3 minimum
    }
    
    @Override
    public int getMaxPlayers() {
        return 50; // 25v25 maximum
    }
    
    @Override
    public void initializeGame(Game game) {
        CTFGameState state = new CTFGameState();
        
        // Initialize teams
        state.redTeam = new Team("red", "Red Team");
        state.blueTeam = new Team("blue", "Blue Team");
        
        // Set up bases and flags based on game boundaries
        // Get first coordinate as center (simplified - in production would calculate actual center)
        Coordinate center = game.getBoundary().get(0);
        double radius = 1000; // 1km radius for now
        
        // Red base in the north, blue base in the south
        state.redBase = new Base(
            "red_base",
            new Coordinate(center.getLatitude() + (radius / 111320.0), center.getLongitude())
        );
        state.blueBase = new Base(
            "blue_base",
            new Coordinate(center.getLatitude() - (radius / 111320.0), center.getLongitude())
        );
        
        // Flags start at bases
        state.redFlag = new Flag("red_flag", state.redBase.location, state.redBase.location);
        state.blueFlag = new Flag("blue_flag", state.blueBase.location, state.blueBase.location);
        
        state.gameId = game.getGameID();
        state.scoreLimit = 3;
        
        gameStates.put(game.getGameID(), state);
    }
    
    @Override
    public void onPlayerJoin(Game game, Player player) {
        CTFGameState state = gameStates.get(game.getGameID());
        if (state == null) return;
        
        // Auto-balance teams
        if (state.redTeam.players.size() <= state.blueTeam.players.size()) {
            state.redTeam.players.add(player.getPlayerID());
            state.playerTeams.put(player.getPlayerID(), "red");
        } else {
            state.blueTeam.players.add(player.getPlayerID());
            state.playerTeams.put(player.getPlayerID(), "blue");
        }
        
        // Spawn player at their base
        Coordinate spawnPoint = state.playerTeams.get(player.getPlayerID()).equals("red") 
            ? state.redBase.location 
            : state.blueBase.location;
        
        // In real implementation, would update player location
    }
    
    @Override
    public void onPlayerLeave(Game game, Player player) {
        CTFGameState state = gameStates.get(game.getGameID());
        if (state == null) return;
        
        // Remove from team
        String team = state.playerTeams.get(player.getPlayerID());
        if ("red".equals(team)) {
            state.redTeam.players.remove(player.getPlayerID());
        } else {
            state.blueTeam.players.remove(player.getPlayerID());
        }
        state.playerTeams.remove(player.getPlayerID());
        
        // Drop flag if carrying
        if (player.getPlayerID().equals(state.redFlag.carriedBy)) {
            // Get player's last known location
            Coordinate lastLocation = new Coordinate(
                player.getLatitude() != null ? player.getLatitude() : 0.0,
                player.getLongitude() != null ? player.getLongitude() : 0.0
            );
            state.redFlag.drop(lastLocation);
        } else if (player.getPlayerID().equals(state.blueFlag.carriedBy)) {
            Coordinate lastLocation = new Coordinate(
                player.getLatitude() != null ? player.getLatitude() : 0.0,
                player.getLongitude() != null ? player.getLongitude() : 0.0
            );
            state.blueFlag.drop(lastLocation);
        }
    }
    
    @Override
    public void onLocationUpdate(Game game, Player player, Coordinate newLocation) {
        CTFGameState state = gameStates.get(game.getGameID());
        if (state == null) return;
        
        String playerTeam = state.playerTeams.get(player.getPlayerID());
        if (playerTeam == null) return;
        
        // Check flag interactions
        if (playerTeam.equals("red")) {
            // Red player can capture blue flag
            if (state.blueFlag.carriedBy == null && 
                GeoUtils.calculateDistance(newLocation, state.blueFlag.currentLocation) <= FLAG_CAPTURE_RADIUS) {
                state.blueFlag.pickup(player.getPlayerID());
                broadcastEvent(game, "FLAG_CAPTURED", Map.of(
                    "player", player.getPlayerID(),
                    "flag", "blue",
                    "team", "red"
                ));
            }
            
            // Check if carrying blue flag back to base
            if (player.getPlayerID().equals(state.blueFlag.carriedBy) &&
                GeoUtils.calculateDistance(newLocation, state.redBase.location) <= BASE_RADIUS) {
                // Score!
                state.redTeam.score++;
                state.blueFlag.reset();
                broadcastEvent(game, "FLAG_SCORED", Map.of(
                    "team", "red",
                    "score", state.redTeam.score,
                    "scorer", player.getPlayerID()
                ));
            }
        } else {
            // Blue player can capture red flag
            if (state.redFlag.carriedBy == null && 
                GeoUtils.calculateDistance(newLocation, state.redFlag.currentLocation) <= FLAG_CAPTURE_RADIUS) {
                state.redFlag.pickup(player.getPlayerID());
                broadcastEvent(game, "FLAG_CAPTURED", Map.of(
                    "player", player.getPlayerID(),
                    "flag", "red",
                    "team", "blue"
                ));
            }
            
            // Check if carrying red flag back to base
            if (player.getPlayerID().equals(state.redFlag.carriedBy) &&
                GeoUtils.calculateDistance(newLocation, state.blueBase.location) <= BASE_RADIUS) {
                // Score!
                state.blueTeam.score++;
                state.redFlag.reset();
                broadcastEvent(game, "FLAG_SCORED", Map.of(
                    "team", "blue",
                    "score", state.blueTeam.score,
                    "scorer", player.getPlayerID()
                ));
            }
        }
        
        // Update flag location if carrying
        if (player.getPlayerID().equals(state.redFlag.carriedBy)) {
            state.redFlag.currentLocation = newLocation;
        } else if (player.getPlayerID().equals(state.blueFlag.carriedBy)) {
            state.blueFlag.currentLocation = newLocation;
        }
    }
    
    @Override
    public GameEndResult checkEndConditions(Game game) {
        CTFGameState state = gameStates.get(game.getGameID());
        if (state == null) {
            return new GameEndResult(false, Collections.emptyList(), Collections.emptyMap(), null);
        }
        
        Map<String, Integer> scores = new HashMap<>();
        scores.put("red", state.redTeam.score);
        scores.put("blue", state.blueTeam.score);
        
        if (state.redTeam.score >= state.scoreLimit) {
            return new GameEndResult(true, state.redTeam.players, scores, "Red team reached score limit");
        } else if (state.blueTeam.score >= state.scoreLimit) {
            return new GameEndResult(true, state.blueTeam.players, scores, "Blue team reached score limit");
        }
        
        return new GameEndResult(false, Collections.emptyList(), scores, null);
    }
    
    @Override
    public Map<String, Integer> getScores(Game game) {
        CTFGameState state = gameStates.get(game.getGameID());
        if (state == null) return Collections.emptyMap();
        
        Map<String, Integer> scores = new HashMap<>();
        scores.put("red", state.redTeam.score);
        scores.put("blue", state.blueTeam.score);
        return scores;
    }
    
    @Override
    public Map<String, Object> getGameState(Game game) {
        CTFGameState state = gameStates.get(game.getGameID());
        if (state == null) return Collections.emptyMap();
        
        Map<String, Object> gameState = new HashMap<>();
        gameState.put("teams", Map.of(
            "red", state.redTeam,
            "blue", state.blueTeam
        ));
        gameState.put("flags", Map.of(
            "red", state.redFlag,
            "blue", state.blueFlag
        ));
        gameState.put("bases", Map.of(
            "red", state.redBase,
            "blue", state.blueBase
        ));
        gameState.put("scoreLimit", state.scoreLimit);
        
        return gameState;
    }
    
    @Override
    public APIGatewayProxyResponseEvent handlePlayerAction(Game game, Player player, 
                                                          String action, APIGatewayProxyRequestEvent request) {
        CTFGameState state = gameStates.get(game.getGameID());
        if (state == null) {
            return createErrorResponse(404, "Game not found");
        }
        
        switch (action) {
            case "drop_flag":
                // Allow player to drop flag voluntarily
                if (player.getPlayerID().equals(state.redFlag.carriedBy)) {
                    Coordinate currentLoc = new Coordinate(
                        player.getLatitude() != null ? player.getLatitude() : 0.0,
                        player.getLongitude() != null ? player.getLongitude() : 0.0
                    );
                    state.redFlag.drop(currentLoc);
                    return createSuccessResponse("Flag dropped");
                } else if (player.getPlayerID().equals(state.blueFlag.carriedBy)) {
                    Coordinate currentLoc2 = new Coordinate(
                        player.getLatitude() != null ? player.getLatitude() : 0.0,
                        player.getLongitude() != null ? player.getLongitude() : 0.0
                    );
                    state.blueFlag.drop(currentLoc2);
                    return createSuccessResponse("Flag dropped");
                }
                return createErrorResponse(400, "Not carrying a flag");
                
            case "tag_enemy":
                // Tag enemy carrying your flag to make them drop it
                Map<String, String> body = GSON.fromJson(request.getBody(), Map.class);
                String targetId = body.get("targetPlayerId");
                
                // Verify target is enemy and in range
                String playerTeam = state.playerTeams.get(player.getPlayerID());
                String targetTeam = state.playerTeams.get(targetId);
                
                if (playerTeam.equals(targetTeam)) {
                    return createErrorResponse(400, "Cannot tag teammate");
                }
                
                // In real implementation, check proximity
                // For now, just handle the tag
                if (targetId.equals(state.redFlag.carriedBy) && playerTeam.equals("red")) {
                    state.redFlag.drop(state.redFlag.currentLocation);
                    return createSuccessResponse("Tagged enemy, flag dropped!");
                } else if (targetId.equals(state.blueFlag.carriedBy) && playerTeam.equals("blue")) {
                    state.blueFlag.drop(state.blueFlag.currentLocation);
                    return createSuccessResponse("Tagged enemy, flag dropped!");
                }
                
                return createErrorResponse(400, "Target not carrying flag");
                
            default:
                return createErrorResponse(400, "Unknown action: " + action);
        }
    }
    
    @Override
    public List<PlayerAction> getAvailableActions(Game game, Player player) {
        CTFGameState state = gameStates.get(game.getGameID());
        if (state == null) return Collections.emptyList();
        
        List<PlayerAction> actions = new ArrayList<>();
        
        // Check if carrying flag
        if (player.getPlayerID().equals(state.redFlag.carriedBy) || 
            player.getPlayerID().equals(state.blueFlag.carriedBy)) {
            actions.add(new PlayerAction(
                "drop_flag", 
                "Drop Flag", 
                "Drop the flag you're carrying",
                false, 
                false
            ));
        }
        
        // Can always try to tag enemies
        actions.add(new PlayerAction(
            "tag_enemy",
            "Tag Enemy",
            "Tag an enemy to make them drop the flag",
            true,
            false
        ));
        
        return actions;
    }
    
    @Override
    public MapConfiguration getMapConfiguration(Game game) {
        CTFGameState state = gameStates.get(game.getGameID());
        if (state == null) return null;
        
        Map<String, MapElement> elements = new HashMap<>();
        
        // Add bases
        elements.put("red_base", new MapElement(
            "red_base", "base", state.redBase.location,
            Map.of("team", "red", "radius", BASE_RADIUS)
        ));
        elements.put("blue_base", new MapElement(
            "blue_base", "base", state.blueBase.location,
            Map.of("team", "blue", "radius", BASE_RADIUS)
        ));
        
        // Add flags
        elements.put("red_flag", new MapElement(
            "red_flag", "flag", state.redFlag.currentLocation,
            Map.of("team", "red", "carriedBy", state.redFlag.carriedBy)
        ));
        elements.put("blue_flag", new MapElement(
            "blue_flag", "flag", state.blueFlag.currentLocation,
            Map.of("team", "blue", "carriedBy", state.blueFlag.carriedBy)
        ));
        
        return new MapConfiguration(
            true,  // Show all players
            true,  // Show player trails
            -1,    // Unlimited visibility
            true,  // Show objectives (flags/bases)
            false, // No safe zones in CTF
            elements
        );
    }
    
    @Override
    public boolean validateGameConfiguration(Map<String, Object> config) {
        // Validate CTF-specific configuration
        // For now, just ensure we have valid boundaries
        return config.containsKey("gameBoundary");
    }
    
    // Helper methods
    private void broadcastEvent(Game game, String eventType, Map<String, Object> data) {
        // In real implementation, send via WebSocket/SNS
        System.out.println("Event: " + eventType + " - " + GSON.toJson(data));
    }
    
    private APIGatewayProxyResponseEvent createSuccessResponse(String message) {
        return new APIGatewayProxyResponseEvent()
            .withStatusCode(200)
            .withBody(GSON.toJson(Map.of("message", message)));
    }
    
    private APIGatewayProxyResponseEvent createErrorResponse(int status, String error) {
        return new APIGatewayProxyResponseEvent()
            .withStatusCode(status)
            .withBody(GSON.toJson(Map.of("error", error)));
    }
    
    // Inner classes for CTF game state
    private static class CTFGameState {
        String gameId;
        Team redTeam;
        Team blueTeam;
        Flag redFlag;
        Flag blueFlag;
        Base redBase;
        Base blueBase;
        Map<String, String> playerTeams = new ConcurrentHashMap<>();
        int scoreLimit;
    }
    
    private static class Team {
        String id;
        String name;
        List<String> players = new ArrayList<>();
        int score = 0;
        
        Team(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
    
    private static class Flag {
        String id;
        Coordinate homeLocation;
        Coordinate currentLocation;
        String carriedBy;
        long droppedAt;
        
        Flag(String id, Coordinate homeLocation, Coordinate currentLocation) {
            this.id = id;
            this.homeLocation = homeLocation;
            this.currentLocation = currentLocation;
        }
        
        void pickup(String playerId) {
            this.carriedBy = playerId;
            this.droppedAt = 0;
        }
        
        void drop(Coordinate location) {
            this.carriedBy = null;
            this.currentLocation = location;
            this.droppedAt = System.currentTimeMillis();
        }
        
        void reset() {
            this.carriedBy = null;
            this.currentLocation = homeLocation;
            this.droppedAt = 0;
        }
    }
    
    private static class Base {
        String id;
        Coordinate location;
        
        Base(String id, Coordinate location) {
            this.id = id;
            this.location = location;
        }
    }
}