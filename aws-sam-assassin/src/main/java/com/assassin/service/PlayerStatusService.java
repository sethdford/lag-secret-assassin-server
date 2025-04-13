package com.assassin.service;

import com.assassin.dao.PlayerDao;
import com.assassin.dao.GameDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GameStateException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.model.Coordinate;
import com.assassin.model.Player;
import com.assassin.model.Game;
import com.assassin.model.GameZoneState;
import com.assassin.model.ShrinkingZoneStage;
import com.assassin.util.GeoUtils; // Assuming we'll need this for distance calcs
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.Duration;

/**
 * Service responsible for managing and checking player status, 
 * particularly in relation to game boundaries and safe zones.
 */
public class PlayerStatusService {

    private static final Logger logger = LoggerFactory.getLogger(PlayerStatusService.class);
    private static final String ZONE_DAMAGE_INTERVAL_KEY = "zoneDamageIntervalSeconds";
    private static final String ZONE_ELIMINATION_THRESHOLD_KEY = "zoneEliminationThresholdSeconds";
    private static final int DEFAULT_ZONE_DAMAGE_INTERVAL = 1; // Default seconds

    private final PlayerDao playerDao;
    private final ShrinkingZoneService shrinkingZoneService;
    private final GameDao gameDao; // Add GameDao dependency

    // Constructor for dependency injection
    public PlayerStatusService(PlayerDao playerDao, ShrinkingZoneService shrinkingZoneService, GameDao gameDao) {
        this.playerDao = Objects.requireNonNull(playerDao, "playerDao cannot be null");
        this.shrinkingZoneService = Objects.requireNonNull(shrinkingZoneService, "shrinkingZoneService cannot be null");
        this.gameDao = Objects.requireNonNull(gameDao, "gameDao cannot be null"); // Initialize GameDao
    }

    /**
     * Checks if a player is currently outside the defined safe zone for their game.
     * Assumes the game uses a shrinking zone mechanism managed by ShrinkingZoneService.
     * 
     * @param playerId The ID of the player to check.
     * @return true if the player is outside the safe zone, false otherwise.
     * @throws PlayerNotFoundException If the player cannot be found.
     * @throws GameNotFoundException If the player's game or zone state cannot be found.
     * @throws GameStateException If the game configuration is invalid or missing.
     */
    public boolean isPlayerOutsideZone(String playerId) 
            throws PlayerNotFoundException, GameNotFoundException, GameStateException {
        
        logger.debug("Checking if player {} is outside the zone.", playerId);

        Player player = playerDao.getPlayerById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + playerId));
        
        String gameId = player.getGameID();
        if (gameId == null || gameId.isEmpty()) {
            logger.warn("Player {} is not associated with any game. Cannot check zone status.", playerId);
            // Or throw an exception? For now, let's assume they are "safe" if not in a game.
            return false; 
        }

        // Get current zone details (this implicitly advances state if needed)
        Optional<Coordinate> zoneCenterOpt = shrinkingZoneService.getCurrentZoneCenter(gameId);
        Optional<Double> zoneRadiusOpt = shrinkingZoneService.getCurrentZoneRadius(gameId);

        if (zoneCenterOpt.isEmpty() || zoneRadiusOpt.isEmpty()) {
            // This might happen if the game ended or state couldn't be calculated.
            // Treat as safe for now, but might need refinement.
            logger.warn("Could not retrieve zone center or radius for game {}. Assuming player {} is safe.", gameId, playerId);
            return false;
        }
        
        Coordinate zoneCenter = zoneCenterOpt.get();
        double zoneRadius = zoneRadiusOpt.get();

        // Get player's location fields
        Double playerLat = player.getLatitude();
        Double playerLon = player.getLongitude();

        if (playerLat == null || playerLon == null) {
            logger.warn("Player {} has incomplete location data (lat={}, lon={}). Assuming OUTSIDE zone.", 
                        playerId, playerLat, playerLon);
            return true; // If no location, assume outside
        }
        
        Coordinate playerLocation = new Coordinate(playerLat, playerLon);

        // Calculate distance between player and zone center using Coordinate objects
        double distanceMeters = GeoUtils.calculateDistance(playerLocation, zoneCenter);

        boolean isOutside = distanceMeters > zoneRadius;
        
        if (isOutside) {
            logger.info("Player {} (at {}, {}) is OUTSIDE the zone (center: {}, {}, radius: {:.2f}m, distance: {:.2f}m)",
                playerId, playerLocation.getLatitude(), playerLocation.getLongitude(), 
                zoneCenter.getLatitude(), zoneCenter.getLongitude(), zoneRadius, distanceMeters);
        } else {
            logger.debug("Player {} (at {}, {}) is INSIDE the zone (center: {}, {}, radius: {:.2f}m, distance: {:.2f}m)",
                playerId, playerLocation.getLatitude(), playerLocation.getLongitude(), 
                zoneCenter.getLatitude(), zoneCenter.getLongitude(), zoneRadius, distanceMeters);
        }

        return isOutside;
    }

    /**
     * Applies damage to a player if they are outside the safe zone and the damage interval has passed.
     * Currently, simplifies elimination logic.
     *
     * @param playerId The ID of the player to check and potentially apply damage to.
     * @return true if damage was applied or player was eliminated, false otherwise.
     * @throws PlayerNotFoundException If the player cannot be found.
     * @throws GameNotFoundException If the player's game or zone state cannot be found.
     * @throws GameStateException If the game configuration is invalid or missing.
     */
    public boolean applyOutOfZoneDamage(String playerId)
            throws PlayerNotFoundException, GameNotFoundException, GameStateException {

        boolean isOutside = isPlayerOutsideZone(playerId);
        Player player = playerDao.getPlayerById(playerId).orElseThrow(); // Get player early
        Instant now = Instant.now();

        if (!isOutside) {
            // Player is safe. Clear the timestamp tracking continuous time outside.
            if (player.getFirstEnteredOutOfZoneTimestamp() != null) {
                 logger.debug("Player {} re-entered the safe zone. Clearing out-of-zone timer.", playerId);
                 player.setFirstEnteredOutOfZoneTimestamp(null);
                 // Optionally clear lastZoneDamageTimestamp too?
                 // player.setLastZoneDamageTimestamp(null); 
                 playerDao.savePlayer(player);
            }
            return false;
        }

        // --- Player is confirmed OUTSIDE --- 
        String gameId = player.getGameID();
        if (gameId == null) { // Should have been caught by isPlayerOutsideZone, but double check
             return false;
        }

        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found for player: " + playerId));

        GameZoneState zoneState = shrinkingZoneService.advanceZoneState(gameId)
                .orElseThrow(() -> new GameStateException("Could not retrieve current zone state for game: " + gameId));

        List<ShrinkingZoneStage> zoneConfig = getShrinkingZoneConfigFromGame(game); // Use helper to get config
        ShrinkingZoneStage currentStageConfig = zoneConfig.get(zoneState.getCurrentStageIndex());
        double damagePerSecond = Optional.ofNullable(currentStageConfig.getDamagePerSecond()).orElse(0.0);

        // Get game-specific settings
        Map<String, Object> settings = game.getSettings();
        int damageIntervalSeconds = getIntSetting(settings, ZONE_DAMAGE_INTERVAL_KEY, DEFAULT_ZONE_DAMAGE_INTERVAL);
        int eliminationThresholdSeconds = getIntSetting(settings, ZONE_ELIMINATION_THRESHOLD_KEY, -1); // -1 means no threshold

        Instant lastDamageTime = Optional.ofNullable(player.getLastZoneDamageTimestamp())
                                         .map(Instant::parse)
                                         .orElse(Instant.MIN); // If never damaged, allow first hit

        Duration timeSinceLastDamage = Duration.between(lastDamageTime, now);

        if (timeSinceLastDamage.getSeconds() >= damageIntervalSeconds) {
            logger.info("Applying zone damage check to player {} (outside zone). Interval: {}s, Threshold: {}s, Damage/s: {}",
                playerId, damageIntervalSeconds, eliminationThresholdSeconds, damagePerSecond);

            boolean eliminated = false;
            String firstOutsideTimestampStr = player.getFirstEnteredOutOfZoneTimestamp();
            
            // Set timestamp if this is the first check interval they are outside
            if (firstOutsideTimestampStr == null) {
                logger.info("Player {} detected outside zone for the first time (or after re-entry). Starting timer.", playerId);
                player.setFirstEnteredOutOfZoneTimestamp(now.toString());
                firstOutsideTimestampStr = now.toString(); // Use immediately for threshold check below
            }

            // Refined Elimination Logic
            if (eliminationThresholdSeconds == 0 && damagePerSecond > 0) { 
                logger.warn("Player {} eliminated due to being outside zone (immediate elimination threshold).", playerId);
                player.setStatus("DEAD"); 
                eliminated = true;
            } else if (eliminationThresholdSeconds > 0) {
                Instant firstOutsideTime = Instant.parse(firstOutsideTimestampStr);
                Duration timeSpentOutside = Duration.between(firstOutsideTime, now);
                logger.debug("Player {} has been outside zone for {} seconds (Threshold: {}s).", 
                           playerId, timeSpentOutside.getSeconds(), eliminationThresholdSeconds);
                           
                if (timeSpentOutside.getSeconds() >= eliminationThresholdSeconds) {
                    logger.warn("Player {} eliminated after being outside zone for {} seconds (Threshold: {}s).", 
                              playerId, timeSpentOutside.getSeconds(), eliminationThresholdSeconds);
                    player.setStatus("DEAD");
                    eliminated = true;
                } else if (damagePerSecond > 0) {
                    // Threshold exists but not met yet, log potential damage
                    logger.info("Player {} would take {:.2f} zone damage (below elimination threshold).", 
                              playerId, damagePerSecond * damageIntervalSeconds);
                }
            } else { 
                 // No threshold (-1), just log potential damage
                 if (damagePerSecond > 0) {
                    logger.info("Player {} would take {:.2f} zone damage (no elimination threshold).", 
                              playerId, damagePerSecond * damageIntervalSeconds);
                 }
            }

            // Update last damage check timestamp and potentially first outside timestamp
            player.setLastZoneDamageTimestamp(now.toString());
            // FirstEnteredOutOfZoneTimestamp is set above if it was null
            
            playerDao.savePlayer(player); // Save changes (status, timestamps)
            
            return true; // Damage check occurred
        } else {
            logger.debug("Skipping zone damage for player {}. Time since last damage: {}s (Interval: {}s)",
                playerId, timeSinceLastDamage.getSeconds(), damageIntervalSeconds);
            return false;
        }
    }
    
    // Helper to safely get integer settings from the game map
    private int getIntSetting(Map<String, Object> settings, String key, int defaultValue) {
        Object value = settings.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer format for setting '{}': {}. Using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }
    
    // Helper to get and validate shrinking zone config (similar to ShrinkingZoneService)
    private List<ShrinkingZoneStage> getShrinkingZoneConfigFromGame(Game game) throws GameStateException {
        Map<String, Object> settings = game.getSettings();
        String configKey = "shrinkingZoneConfig"; // Reuse key name
        if (settings == null || !settings.containsKey(configKey)) {
            throw new GameStateException(String.format("Game %s is missing the '%s' setting.", game.getGameID(), configKey));
        }
        Object configObj = settings.get(configKey);
        if (!(configObj instanceof List)) {
            throw new GameStateException(String.format("Invalid '%s' format in game %s.", configKey, game.getGameID()));
        }
        try {
            @SuppressWarnings("unchecked")
            List<ShrinkingZoneStage> configList = (List<ShrinkingZoneStage>) configObj;
            if (configList.isEmpty()) {
                throw new GameStateException(String.format("Empty '%s' list in game %s.", configKey, game.getGameID()));
            }
            // TODO: Add deeper validation if needed
            return configList;
        } catch (ClassCastException e) {
            throw new GameStateException(String.format("Invalid '%s' list contents in game %s.", configKey, game.getGameID()), e);
        }
    }

    // TODO: Add method to periodically check all players in a game
    // public void checkAllPlayersInGame(String gameId) { ... }

} 