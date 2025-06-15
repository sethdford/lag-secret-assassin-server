package com.assassin.models.gamemode;

import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.Player;
import com.assassin.model.GameStatus;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.List;
import java.util.Map;

/**
 * Abstract interface for all game modes in the LAG platform.
 * This allows for multiple game types beyond just Assassin.
 */
public interface GameMode {
    
    /**
     * Unique identifier for this game mode
     */
    String getModeId();
    
    /**
     * Display name for this game mode
     */
    String getModeName();
    
    /**
     * Description of how to play this game mode
     */
    String getDescription();
    
    /**
     * Minimum number of players required
     */
    int getMinPlayers();
    
    /**
     * Maximum number of players allowed
     */
    int getMaxPlayers();
    
    /**
     * Initialize a new game with this mode
     */
    void initializeGame(Game game);
    
    /**
     * Handle a player joining the game
     */
    void onPlayerJoin(Game game, Player player);
    
    /**
     * Handle a player leaving the game
     */
    void onPlayerLeave(Game game, Player player);
    
    /**
     * Process a player location update
     */
    void onLocationUpdate(Game game, Player player, Coordinate newLocation);
    
    /**
     * Check if the game has ended and determine winner(s)
     */
    GameEndResult checkEndConditions(Game game);
    
    /**
     * Get current scores/standings for all players/teams
     */
    Map<String, Integer> getScores(Game game);
    
    /**
     * Get mode-specific game state data
     */
    Map<String, Object> getGameState(Game game);
    
    /**
     * Handle mode-specific player actions
     */
    APIGatewayProxyResponseEvent handlePlayerAction(
        Game game, 
        Player player, 
        String action, 
        APIGatewayProxyRequestEvent request
    );
    
    /**
     * Get list of available actions for a player
     */
    List<PlayerAction> getAvailableActions(Game game, Player player);
    
    /**
     * Get map configuration for this mode
     */
    MapConfiguration getMapConfiguration(Game game);
    
    /**
     * Validate if a game configuration is valid for this mode
     */
    boolean validateGameConfiguration(Map<String, Object> config);
    
    /**
     * Result of end condition check
     */
    class GameEndResult {
        private final boolean gameEnded;
        private final List<String> winnerIds;
        private final Map<String, Integer> finalScores;
        private final String endReason;
        
        public GameEndResult(boolean gameEnded, List<String> winnerIds, 
                           Map<String, Integer> finalScores, String endReason) {
            this.gameEnded = gameEnded;
            this.winnerIds = winnerIds;
            this.finalScores = finalScores;
            this.endReason = endReason;
        }
        
        // Getters
        public boolean isGameEnded() { return gameEnded; }
        public List<String> getWinnerIds() { return winnerIds; }
        public Map<String, Integer> getFinalScores() { return finalScores; }
        public String getEndReason() { return endReason; }
    }
    
    /**
     * Available player action
     */
    class PlayerAction {
        private final String actionId;
        private final String displayName;
        private final String description;
        private final boolean requiresTarget;
        private final boolean requiresLocation;
        
        public PlayerAction(String actionId, String displayName, String description,
                          boolean requiresTarget, boolean requiresLocation) {
            this.actionId = actionId;
            this.displayName = displayName;
            this.description = description;
            this.requiresTarget = requiresTarget;
            this.requiresLocation = requiresLocation;
        }
        
        // Getters
        public String getActionId() { return actionId; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public boolean isRequiresTarget() { return requiresTarget; }
        public boolean isRequiresLocation() { return requiresLocation; }
    }
    
    /**
     * Map configuration for game mode
     */
    class MapConfiguration {
        private final boolean showAllPlayers;
        private final boolean showPlayerTrails;
        private final int visibilityRadius; // meters, -1 for unlimited
        private final boolean showObjectives;
        private final boolean showSafeZones;
        private final Map<String, MapElement> dynamicElements;
        
        public MapConfiguration(boolean showAllPlayers, boolean showPlayerTrails,
                              int visibilityRadius, boolean showObjectives,
                              boolean showSafeZones, Map<String, MapElement> dynamicElements) {
            this.showAllPlayers = showAllPlayers;
            this.showPlayerTrails = showPlayerTrails;
            this.visibilityRadius = visibilityRadius;
            this.showObjectives = showObjectives;
            this.showSafeZones = showSafeZones;
            this.dynamicElements = dynamicElements;
        }
        
        // Getters
        public boolean isShowAllPlayers() { return showAllPlayers; }
        public boolean isShowPlayerTrails() { return showPlayerTrails; }
        public int getVisibilityRadius() { return visibilityRadius; }
        public boolean isShowObjectives() { return showObjectives; }
        public boolean isShowSafeZones() { return showSafeZones; }
        public Map<String, MapElement> getDynamicElements() { return dynamicElements; }
    }
    
    /**
     * Dynamic map element
     */
    class MapElement {
        private final String elementId;
        private final String type;
        private final Coordinate location;
        private final Map<String, Object> properties;
        
        public MapElement(String elementId, String type, Coordinate location,
                         Map<String, Object> properties) {
            this.elementId = elementId;
            this.type = type;
            this.location = location;
            this.properties = properties;
        }
        
        // Getters
        public String getElementId() { return elementId; }
        public String getType() { return type; }
        public Coordinate getLocation() { return location; }
        public Map<String, Object> getProperties() { return properties; }
    }
}