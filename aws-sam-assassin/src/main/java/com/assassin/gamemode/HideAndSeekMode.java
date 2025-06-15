package com.assassin.gamemode;

import com.assassin.models.gamemode.GameMode;
import com.assassin.model.*;
import com.assassin.util.GeoUtils;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Hide and Seek game mode implementation.
 * Hiders have time to hide, then seekers hunt them down using proximity alerts.
 */
public class HideAndSeekMode implements GameMode {
    
    private static final Gson GSON = new Gson();
    private static final int HIDING_PHASE_SECONDS = 120; // 2 minutes to hide
    private static final int CATCH_RADIUS = 5; // meters to catch someone
    private static final int PROXIMITY_ALERT_RADIUS = 50; // meters for "warmer/colder"
    private static final int POWER_UP_DURATION = 30; // seconds
    
    private final Map<String, HideAndSeekState> gameStates = new ConcurrentHashMap<>();
    
    @Override
    public String getModeId() {
        return "hide_and_seek";
    }
    
    @Override
    public String getModeName() {
        return "Hide & Seek";
    }
    
    @Override
    public String getDescription() {
        return "Hiders get 2 minutes to hide, then seekers hunt them down! " +
               "Use power-ups like invisibility and speed boosts to survive. " +
               "Last hider standing wins!";
    }
    
    @Override
    public int getMinPlayers() {
        return 4; // At least 1 seeker and 3 hiders
    }
    
    @Override
    public int getMaxPlayers() {
        return 30;
    }
    
    @Override
    public void initializeGame(Game game) {
        HideAndSeekState state = new HideAndSeekState();
        state.gameId = game.getGameID();
        state.gamePhase = GamePhase.WAITING;
        state.phaseStartTime = Instant.now();
        
        // Initialize power-up spawn points based on game area
        // Initialize power-ups based on game area
        initializePowerUpLocations(state, game.getBoundary());
        
        gameStates.put(game.getGameID(), state);
    }
    
    @Override
    public void onPlayerJoin(Game game, Player player) {
        HideAndSeekState state = gameStates.get(game.getGameID());
        if (state == null) return;
        
        // Assign roles - 25% seekers, 75% hiders
        int totalPlayers = state.hiders.size() + state.seekers.size() + 1;
        int desiredSeekers = Math.max(1, totalPlayers / 4);
        
        if (state.seekers.size() < desiredSeekers) {
            state.seekers.add(player.getPlayerID());
            state.playerRoles.put(player.getPlayerID(), PlayerRole.SEEKER);
        } else {
            state.hiders.add(player.getPlayerID());
            state.playerRoles.put(player.getPlayerID(), PlayerRole.HIDER);
        }
        
        // If this is the first seeker and we have enough hiders, start hiding phase
        if (state.gamePhase == GamePhase.WAITING && 
            state.seekers.size() >= 1 && 
            state.hiders.size() >= 3) {
            startHidingPhase(state);
        }
    }
    
    @Override
    public void onPlayerLeave(Game game, Player player) {
        HideAndSeekState state = gameStates.get(game.getGameID());
        if (state == null) return;
        
        state.hiders.remove(player.getPlayerID());
        state.seekers.remove(player.getPlayerID());
        state.caught.remove(player.getPlayerID());
        state.playerRoles.remove(player.getPlayerID());
        state.playerPowerUps.remove(player.getPlayerID());
    }
    
    @Override
    public void onLocationUpdate(Game game, Player player, Coordinate newLocation) {
        HideAndSeekState state = gameStates.get(game.getGameID());
        if (state == null) return;
        
        // Store location
        state.playerLocations.put(player.getPlayerID(), newLocation);
        
        // Check phase transitions
        checkPhaseTransition(state);
        
        PlayerRole role = state.playerRoles.get(player.getPlayerID());
        if (role == null) return;
        
        if (state.gamePhase == GamePhase.SEEKING) {
            if (role == PlayerRole.SEEKER) {
                // Check if seeker caught any hiders
                for (String hiderId : state.hiders) {
                    if (state.caught.contains(hiderId)) continue;
                    
                    Coordinate hiderLocation = state.playerLocations.get(hiderId);
                    if (hiderLocation == null) continue;
                    
                    // Check if hider is invisible
                    PowerUp invisibility = state.playerPowerUps.get(hiderId);
                    if (invisibility != null && 
                        invisibility.type == PowerUpType.INVISIBILITY &&
                        invisibility.expiresAt.isAfter(Instant.now())) {
                        continue; // Can't catch invisible players
                    }
                    
                    double distance = GeoUtils.calculateDistance(newLocation, hiderLocation);
                    if (distance <= CATCH_RADIUS) {
                        // Caught!
                        state.caught.add(hiderId);
                        state.catchTimes.put(hiderId, Instant.now());
                        broadcastEvent(game, "PLAYER_CAUGHT", Map.of(
                            "seeker", player.getPlayerID(),
                            "hider", hiderId,
                            "remainingHiders", state.hiders.size() - state.caught.size()
                        ));
                    }
                }
            } else if (role == PlayerRole.HIDER && !state.caught.contains(player.getPlayerID())) {
                // Check for power-up collection
                checkPowerUpCollection(state, player.getPlayerID(), newLocation);
            }
        }
    }
    
    @Override
    public GameEndResult checkEndConditions(Game game) {
        HideAndSeekState state = gameStates.get(game.getGameID());
        if (state == null) {
            return new GameEndResult(false, Collections.emptyList(), Collections.emptyMap(), null);
        }
        
        // Check if all hiders are caught
        if (state.gamePhase == GamePhase.SEEKING && 
            state.caught.size() >= state.hiders.size()) {
            
            // Seekers win!
            Map<String, Integer> scores = calculateScores(state);
            return new GameEndResult(
                true, 
                new ArrayList<>(state.seekers),
                scores,
                "All hiders caught! Seekers win!"
            );
        }
        
        // Check if time limit reached (e.g., 10 minutes of seeking)
        if (state.gamePhase == GamePhase.SEEKING) {
            long seekingDuration = Instant.now().getEpochSecond() - state.phaseStartTime.getEpochSecond();
            if (seekingDuration > 600) { // 10 minutes
                // Remaining hiders win!
                List<String> winners = state.hiders.stream()
                    .filter(h -> !state.caught.contains(h))
                    .collect(Collectors.toList());
                    
                Map<String, Integer> scores = calculateScores(state);
                return new GameEndResult(
                    true,
                    winners,
                    scores,
                    "Time's up! Hiders win!"
                );
            }
        }
        
        return new GameEndResult(false, Collections.emptyList(), calculateScores(state), null);
    }
    
    @Override
    public Map<String, Integer> getScores(Game game) {
        HideAndSeekState state = gameStates.get(game.getGameID());
        if (state == null) return Collections.emptyMap();
        
        return calculateScores(state);
    }
    
    @Override
    public Map<String, Object> getGameState(Game game) {
        HideAndSeekState state = gameStates.get(game.getGameID());
        if (state == null) return Collections.emptyMap();
        
        Map<String, Object> gameState = new HashMap<>();
        gameState.put("phase", state.gamePhase);
        gameState.put("phaseTimeRemaining", getPhaseTimeRemaining(state));
        gameState.put("seekers", state.seekers);
        gameState.put("hidersAlive", state.hiders.size() - state.caught.size());
        gameState.put("hidersCaught", state.caught.size());
        
        // Don't reveal hider locations during hiding phase or to seekers
        if (state.gamePhase == GamePhase.SEEKING) {
            gameState.put("powerUps", state.availablePowerUps.stream()
                .map(p -> Map.of("type", p.type, "location", p.location))
                .collect(Collectors.toList()));
        }
        
        return gameState;
    }
    
    @Override
    public APIGatewayProxyResponseEvent handlePlayerAction(Game game, Player player, 
                                                          String action, APIGatewayProxyRequestEvent request) {
        HideAndSeekState state = gameStates.get(game.getGameID());
        if (state == null) {
            return createErrorResponse(404, "Game not found");
        }
        
        switch (action) {
            case "use_powerup":
                PowerUp powerUp = state.playerPowerUps.get(player.getPlayerID());
                if (powerUp == null) {
                    return createErrorResponse(400, "No power-up available");
                }
                
                // Power-up effects are automatic, just acknowledge
                return createSuccessResponse("Power-up active: " + powerUp.type);
                
            case "proximity_check":
                // Seekers can check proximity to nearest hider
                if (state.playerRoles.get(player.getPlayerID()) != PlayerRole.SEEKER) {
                    return createErrorResponse(403, "Only seekers can check proximity");
                }
                
                if (state.gamePhase != GamePhase.SEEKING) {
                    return createErrorResponse(400, "Can only check during seeking phase");
                }
                
                Coordinate seekerLoc = state.playerLocations.get(player.getPlayerID());
                if (seekerLoc == null) {
                    return createErrorResponse(400, "Location not found");
                }
                
                // Find nearest uncaught hider
                double nearestDistance = Double.MAX_VALUE;
                for (String hiderId : state.hiders) {
                    if (state.caught.contains(hiderId)) continue;
                    
                    Coordinate hiderLoc = state.playerLocations.get(hiderId);
                    if (hiderLoc == null) continue;
                    
                    double distance = GeoUtils.calculateDistance(seekerLoc, hiderLoc);
                    nearestDistance = Math.min(nearestDistance, distance);
                }
                
                String proximity;
                if (nearestDistance < 20) {
                    proximity = "VERY HOT! 🔥";
                } else if (nearestDistance < 50) {
                    proximity = "Hot! 🌡️";
                } else if (nearestDistance < 100) {
                    proximity = "Warm 🌤️";
                } else if (nearestDistance < 200) {
                    proximity = "Cold ❄️";
                } else {
                    proximity = "Freezing! 🧊";
                }
                
                return createSuccessResponse(proximity);
                
            default:
                return createErrorResponse(400, "Unknown action: " + action);
        }
    }
    
    @Override
    public List<PlayerAction> getAvailableActions(Game game, Player player) {
        HideAndSeekState state = gameStates.get(game.getGameID());
        if (state == null) return Collections.emptyList();
        
        List<PlayerAction> actions = new ArrayList<>();
        PlayerRole role = state.playerRoles.get(player.getPlayerID());
        
        if (role == PlayerRole.SEEKER && state.gamePhase == GamePhase.SEEKING) {
            actions.add(new PlayerAction(
                "proximity_check",
                "Check Proximity",
                "Get a hot/cold indicator to nearest hider",
                false,
                false
            ));
        }
        
        if (state.playerPowerUps.containsKey(player.getPlayerID())) {
            actions.add(new PlayerAction(
                "use_powerup",
                "Use Power-up",
                "Activate your collected power-up",
                false,
                false
            ));
        }
        
        return actions;
    }
    
    @Override
    public MapConfiguration getMapConfiguration(Game game) {
        HideAndSeekState state = gameStates.get(game.getGameID());
        if (state == null) return null;
        
        Map<String, MapElement> elements = new HashMap<>();
        
        // Add power-ups to map (visible to hiders only)
        int elementIndex = 0;
        for (PowerUp powerUp : state.availablePowerUps) {
            elements.put("powerup_" + elementIndex++, new MapElement(
                powerUp.id,
                "powerup",
                powerUp.location,
                Map.of("type", powerUp.type.toString())
            ));
        }
        
        // Different visibility rules for different phases and roles
        boolean showAllPlayers = false;
        int visibilityRadius = -1;
        
        if (state.gamePhase == GamePhase.HIDING) {
            // During hiding phase, seekers can't see anyone
            showAllPlayers = false;
            visibilityRadius = 0;
        } else if (state.gamePhase == GamePhase.SEEKING) {
            // During seeking, show limited info
            showAllPlayers = false;
            visibilityRadius = PROXIMITY_ALERT_RADIUS;
        }
        
        return new MapConfiguration(
            showAllPlayers,
            false, // No trails in hide and seek
            visibilityRadius,
            true,  // Show objectives (power-ups)
            false, // No safe zones
            elements
        );
    }
    
    @Override
    public boolean validateGameConfiguration(Map<String, Object> config) {
        return config.containsKey("gameBoundary");
    }
    
    // Helper methods
    private void startHidingPhase(HideAndSeekState state) {
        state.gamePhase = GamePhase.HIDING;
        state.phaseStartTime = Instant.now();
        broadcastEvent(null, "HIDING_PHASE_START", Map.of(
            "duration", HIDING_PHASE_SECONDS,
            "seekers", state.seekers,
            "hiders", state.hiders
        ));
    }
    
    private void checkPhaseTransition(HideAndSeekState state) {
        if (state.gamePhase == GamePhase.HIDING) {
            long elapsed = Instant.now().getEpochSecond() - state.phaseStartTime.getEpochSecond();
            if (elapsed >= HIDING_PHASE_SECONDS) {
                state.gamePhase = GamePhase.SEEKING;
                state.phaseStartTime = Instant.now();
                broadcastEvent(null, "SEEKING_PHASE_START", Map.of(
                    "seekers", state.seekers,
                    "hidersToFind", state.hiders.size()
                ));
            }
        }
    }
    
    private void initializePowerUpLocations(HideAndSeekState state, List<Coordinate> boundary) {
        // Spawn random power-ups around the game area
        Random random = new Random();
        // Get center from first coordinate (simplified)
        Coordinate center = boundary.get(0);
        double radius = 1000; // 1km default
        
        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * radius * 0.8; // Stay within 80% of boundary
            
            double lat = center.getLatitude() + (distance * Math.cos(angle) / 111320.0);
            double lng = center.getLongitude() + (distance * Math.sin(angle) / (111320.0 * Math.cos(Math.toRadians(center.getLatitude()))));
            
            PowerUpType type = PowerUpType.values()[random.nextInt(PowerUpType.values().length)];
            state.availablePowerUps.add(new PowerUp(
                "powerup_" + i,
                type,
                new Coordinate(lat, lng),
                null
            ));
        }
    }
    
    private void checkPowerUpCollection(HideAndSeekState state, String playerId, Coordinate location) {
        Iterator<PowerUp> iterator = state.availablePowerUps.iterator();
        while (iterator.hasNext()) {
            PowerUp powerUp = iterator.next();
            if (GeoUtils.calculateDistance(location, powerUp.location) <= 10) {
                // Collected!
                iterator.remove();
                powerUp.expiresAt = Instant.now().plusSeconds(POWER_UP_DURATION);
                state.playerPowerUps.put(playerId, powerUp);
                
                broadcastEvent(null, "POWERUP_COLLECTED", Map.of(
                    "player", playerId,
                    "type", powerUp.type,
                    "duration", POWER_UP_DURATION
                ));
                break;
            }
        }
    }
    
    private long getPhaseTimeRemaining(HideAndSeekState state) {
        long elapsed = Instant.now().getEpochSecond() - state.phaseStartTime.getEpochSecond();
        
        if (state.gamePhase == GamePhase.HIDING) {
            return Math.max(0, HIDING_PHASE_SECONDS - elapsed);
        } else if (state.gamePhase == GamePhase.SEEKING) {
            return Math.max(0, 600 - elapsed); // 10 minute seeking phase
        }
        
        return 0;
    }
    
    private Map<String, Integer> calculateScores(HideAndSeekState state) {
        Map<String, Integer> scores = new HashMap<>();
        
        // Seekers get points for catches
        for (String seeker : state.seekers) {
            int catches = 0;
            for (Map.Entry<String, Instant> entry : state.catchTimes.entrySet()) {
                // Count catches (in full implementation, track who caught whom)
                catches++;
            }
            scores.put(seeker, catches * 100);
        }
        
        // Hiders get points for survival time
        for (String hider : state.hiders) {
            if (state.caught.contains(hider)) {
                // Points based on how long they survived
                long survivalTime = state.catchTimes.get(hider).getEpochSecond() - 
                                  state.phaseStartTime.getEpochSecond();
                scores.put(hider, (int)(survivalTime * 10));
            } else {
                // Max points for still being alive
                scores.put(hider, 1000);
            }
        }
        
        return scores;
    }
    
    private void broadcastEvent(Game game, String eventType, Map<String, Object> data) {
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
    
    // Inner classes
    private static class HideAndSeekState {
        String gameId;
        GamePhase gamePhase;
        Instant phaseStartTime;
        Set<String> seekers = ConcurrentHashMap.newKeySet();
        Set<String> hiders = ConcurrentHashMap.newKeySet();
        Set<String> caught = ConcurrentHashMap.newKeySet();
        Map<String, PlayerRole> playerRoles = new ConcurrentHashMap<>();
        Map<String, Coordinate> playerLocations = new ConcurrentHashMap<>();
        Map<String, Instant> catchTimes = new ConcurrentHashMap<>();
        List<PowerUp> availablePowerUps = new ArrayList<>();
        Map<String, PowerUp> playerPowerUps = new ConcurrentHashMap<>();
    }
    
    private enum GamePhase {
        WAITING,
        HIDING,
        SEEKING
    }
    
    private enum PlayerRole {
        SEEKER,
        HIDER
    }
    
    private enum PowerUpType {
        INVISIBILITY,    // Can't be caught for 30 seconds
        SPEED_BOOST,     // Move faster
        DECOY,          // Create false location
        TRACKER_JAMMER  // Block proximity checks
    }
    
    private static class PowerUp {
        String id;
        PowerUpType type;
        Coordinate location;
        Instant expiresAt;
        
        PowerUp(String id, PowerUpType type, Coordinate location, Instant expiresAt) {
            this.id = id;
            this.type = type;
            this.location = location;
            this.expiresAt = expiresAt;
        }
    }
}