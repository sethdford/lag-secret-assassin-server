package com.assassin.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.config.MapConfiguration;
import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.Notification;
import com.assassin.model.NotificationType;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;
import com.assassin.util.GeoUtils;

/**
 * Service responsible for detecting proximity between players for elimination mechanics.
 * Provides optimized methods for checking when players are close enough for elimination attempts.
 */
public class ProximityDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(ProximityDetectionService.class);
    
    // Default threshold distance if not specified in map config
    private static final double DEFAULT_ELIMINATION_DISTANCE = 10.0;
    
    // GPS accuracy compensation (meters)
    private static final double GPS_ACCURACY_BUFFER = 5.0;
    
    // Cache of recent proximity checks to reduce unnecessary recalculations
    private final Map<String, ProximityResult> proximityCache;
    
    // Cache expiration time in milliseconds
    private static final long CACHE_EXPIRATION_MS = 10000; // 10 seconds
    
    // Maximum age of location data to be considered valid (milliseconds)
    private static final long LOCATION_STALENESS_THRESHOLD_MS = 60000; // 60 seconds
    
    // Cache for recent alerts sent to avoid spamming users
    private final Map<String, Long> alertCache;
    private static final long ALERT_COOLDOWN_MS = 60000; // 1 minute
    
    private final PlayerDao playerDao;
    private final GameDao gameDao;
    private final LocationService locationService;
    private final MapConfigurationService mapConfigService;
    private final NotificationService notificationService;
    
    /**
     * Represents the result of a proximity check between two players.
     */
    public static class ProximityResult {
        private final String player1Id;
        private final String player2Id;
        private final double distance;
        private final long timestamp;
        private final boolean isInRange;
        
        public ProximityResult(String player1Id, String player2Id, double distance, boolean isInRange) {
            this.player1Id = player1Id;
            this.player2Id = player2Id;
            this.distance = distance;
            this.timestamp = System.currentTimeMillis();
            this.isInRange = isInRange;
        }
        
        public String getPlayer1Id() {
            return player1Id;
        }
        
        public String getPlayer2Id() {
            return player2Id;
        }
        
        public double getDistance() {
            return distance;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public boolean isInRange() {
            return isInRange;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRATION_MS;
        }
    }
    
    /**
     * Default constructor that initializes dependencies.
     */
    public ProximityDetectionService() {
        this(new DynamoDbPlayerDao(), new DynamoDbGameDao(), new LocationService(), new MapConfigurationService(new DynamoDbGameDao(), null, null, null), new NotificationService());
    }
    
    /**
     * Constructor for dependency injection.
     * 
     * @param playerDao Data access for player information
     * @param gameDao Data access for game configuration
     * @param locationService Service for location-related operations
     * @param mapConfigService Service for retrieving map configuration
     * @param notificationService Service for sending notifications
     */
    public ProximityDetectionService(PlayerDao playerDao, GameDao gameDao, LocationService locationService, MapConfigurationService mapConfigService, NotificationService notificationService) {
        this.playerDao = Objects.requireNonNull(playerDao, "playerDao cannot be null");
        this.gameDao = Objects.requireNonNull(gameDao, "gameDao cannot be null");
        this.locationService = Objects.requireNonNull(locationService, "locationService cannot be null");
        this.mapConfigService = Objects.requireNonNull(mapConfigService, "mapConfigService cannot be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService cannot be null");
        this.proximityCache = new ConcurrentHashMap<>();
        this.alertCache = new ConcurrentHashMap<>();
    }
    
    /**
     * Checks if a player is close enough to their target for an elimination attempt.
     * Takes into account game rules, weapon types, player status, location validity, and GPS inaccuracy.
     * 
     * @param gameId ID of the game
     * @param playerId ID of the player attempting elimination (killer)
     * @param targetId ID of the target player (victim)
     * @param weaponType Optional weapon type affecting elimination distance (e.g., "MELEE", "SNIPER")
     * @return true if the killer can eliminate the victim based on proximity and status, false otherwise
     * @throws PlayerNotFoundException If either player cannot be found
     * @throws GameNotFoundException If the game cannot be found
     */
    public boolean canEliminateTarget(String gameId, String playerId, String targetId, String weaponType) 
            throws PlayerNotFoundException, GameNotFoundException {
        
        // Validate inputs
        if (gameId == null || playerId == null || targetId == null) {
            logger.warn("Null parameters provided to canEliminateTarget: gameId={}, playerId={}, targetId={}", 
                      gameId, playerId, targetId);
            return false; // Cannot proceed with null IDs
        }
        
        if (playerId.equals(targetId)) {
             logger.warn("Player {} attempted to eliminate themselves.", playerId);
             return false; // Cannot eliminate self
        }
        
        // Fetch player data first for status and location checks
        Player killer = playerDao.getPlayerById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("Killer not found: " + playerId));
        Player victim = playerDao.getPlayerById(targetId)
                .orElseThrow(() -> new PlayerNotFoundException("Victim not found: " + targetId));
        
        // Check Player Status: Both must be ACTIVE
        if (!PlayerStatus.ACTIVE.name().equals(killer.getStatus())) {
            logger.debug("Cannot eliminate: Killer {} is not ACTIVE (status: {})", playerId, killer.getStatus());
            return false;
        }
        if (!PlayerStatus.ACTIVE.name().equals(victim.getStatus())) {
            logger.debug("Cannot eliminate: Victim {} is not ACTIVE (status: {})", targetId, victim.getStatus());
            return false;
        }
        
        // Check Location Availability and Staleness
        if (killer.getLatitude() == null || killer.getLongitude() == null || killer.getLocationTimestamp() == null) {
            logger.warn("Cannot eliminate: Killer {} has no location data.", playerId);
            return false;
        }
        if (victim.getLatitude() == null || victim.getLongitude() == null || victim.getLocationTimestamp() == null) {
            logger.warn("Cannot eliminate: Victim {} has no location data.", targetId);
            return false;
        }
        
        // Check staleness
        long nowMillis = System.currentTimeMillis();
        try {
            Instant killerLocationInstant = Instant.parse(killer.getLocationTimestamp());
            if (nowMillis - killerLocationInstant.toEpochMilli() > LOCATION_STALENESS_THRESHOLD_MS) {
                logger.warn("Cannot eliminate: Killer {} location data is too old (timestamp: {}, Threshold: {}ms)", 
                          playerId, killer.getLocationTimestamp(), LOCATION_STALENESS_THRESHOLD_MS);
                return false;
            }

            Instant victimLocationInstant = Instant.parse(victim.getLocationTimestamp());
            if (nowMillis - victimLocationInstant.toEpochMilli() > LOCATION_STALENESS_THRESHOLD_MS) {
                logger.warn("Cannot eliminate: Victim {} location data is too old (timestamp: {}, Threshold: {}ms)", 
                          targetId, victim.getLocationTimestamp(), LOCATION_STALENESS_THRESHOLD_MS);
                return false;
            }
        } catch (DateTimeParseException e) {
            logger.error("Cannot eliminate: Failed to parse location timestamp for killer {} ({}) or victim {} ({}): {}", 
                         playerId, killer.getLocationTimestamp(), targetId, victim.getLocationTimestamp(), e.getMessage());
            return false; // Treat unparseable timestamps as stale/invalid
        }

        // Get the game to check game-specific settings (already checked players, less likely to throw here)
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found: " + gameId + " (referenced by players " + playerId + ", " + targetId + ")"));
        
        // Check if killer is in a safe zone
        Coordinate killerCoordinate = new Coordinate(killer.getLatitude(), killer.getLongitude());
        long currentTimeMillis = System.currentTimeMillis();
        if (mapConfigService.isLocationInSafeZone(gameId, killerCoordinate, currentTimeMillis)) {
            logger.debug("Cannot eliminate: Killer {} is in a safe zone", playerId);
            return false;
        }
        
        // Check if target is in a safe zone
        Coordinate targetCoordinate = new Coordinate(victim.getLatitude(), victim.getLongitude());
        if (mapConfigService.isLocationInSafeZone(gameId, targetCoordinate, currentTimeMillis)) {
            logger.debug("Cannot eliminate: Target {} is in a safe zone", targetId);
            return false;
        }
        
        // Get map configuration for proximity settings
        MapConfiguration mapConfig = mapConfigService.getEffectiveMapConfiguration(gameId);

        // Determine elimination distance based on map config and weapon type
        double eliminationDistance = getEliminationDistance(mapConfig, weaponType);
        
        // Add buffer for GPS inaccuracy
        double effectiveDistance = eliminationDistance + GPS_ACCURACY_BUFFER;
        
        logger.debug("Checking proximity for elimination in game {}. Killer: {} ({}), Victim: {} ({}). Weapon: {}. Required distance: {:.2f}m ({:.2f}m base + {:.2f}m buffer)",
                   gameId, playerId, killer.getStatus(), targetId, victim.getStatus(), weaponType, effectiveDistance, eliminationDistance, GPS_ACCURACY_BUFFER);
        
        // Calculate actual distance (now that we know locations exist)
        double actualDistance = calculateDistanceBetweenPlayersInternal(killer, victim);
        
        // Check if the actual distance is within the effective range
        boolean inRange = actualDistance <= effectiveDistance;
        
        logger.info("Elimination check result for {} -> {}: In Range = {} (Actual: {:.2f}m, Required: {:.2f}m)", 
                   playerId, targetId, inRange, actualDistance, effectiveDistance);

        // Cache the result (using actual distance)
        proximityCache.put(generateCacheKey(gameId, playerId, targetId), new ProximityResult(playerId, targetId, actualDistance, inRange));
        
        return inRange;
    }
    
    /**
     * Calculate the actual distance between two players.
     * Delegates to internal method after fetching players.
     * 
     * @param player1Id First player ID
     * @param player2Id Second player ID
     * @return Distance in meters, or Double.MAX_VALUE if locations are unknown
     * @throws PlayerNotFoundException If either player cannot be found
     */
    private double calculateDistanceBetweenPlayers(String player1Id, String player2Id) throws PlayerNotFoundException {
        Player player1 = playerDao.getPlayerById(player1Id)
                .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + player1Id));
        Player player2 = playerDao.getPlayerById(player2Id)
                .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + player2Id));

        return calculateDistanceBetweenPlayersInternal(player1, player2);
    }
    
    /**
     * Determine the elimination distance based on game settings and weapon type.
     * Prioritizes weapon-specific distance, then map default, then global default.
     * 
     * @param mapConfig Map configuration for game
     * @param weaponType Type of weapon being used (case-insensitive lookup)
     * @return Distance in meters required for elimination
     */
    private double getEliminationDistance(MapConfiguration mapConfig, String weaponType) {
        // 1. Check for weapon-specific distance in map config
        if (weaponType != null && mapConfig.getWeaponDistances() != null) {
            // Perform case-insensitive lookup if desired, or store keys consistently
            String lookupKey = weaponType.toUpperCase(); // Example: Store/lookup keys in uppercase
            Double weaponDistance = mapConfig.getWeaponDistances().get(lookupKey);
            if (weaponDistance != null) {
                 logger.debug("Using weapon-specific distance for '{}': {}m", weaponType, weaponDistance);
                 return weaponDistance;
            } else {
                 logger.debug("Weapon type '{}' not found in map config weaponDistances, checking default.", weaponType);
            }
        }

        // 2. Fall back to map default elimination distance
        Double mapDefaultDistance = mapConfig.getEliminationDistanceMeters();
        if (mapDefaultDistance != null) {
             logger.debug("Using map default elimination distance: {}m", mapDefaultDistance);
             return mapDefaultDistance;
        }

        // 3. Fall back to global default if nothing else is defined
        logger.warn("Elimination distance not specified in map config (mapId: {}), using global default: {}m", 
                    mapConfig.getMapId(), DEFAULT_ELIMINATION_DISTANCE);
        return DEFAULT_ELIMINATION_DISTANCE;
    }
    
    /**
     * Get recently cached proximity results for a player.
     * Useful for UI updates and notifications.
     * 
     * @param playerId ID of the player to get proximity results for
     * @return Map of target player IDs to proximity results
     */
    public Map<String, ProximityResult> getRecentProximityResults(String playerId) {
        Map<String, ProximityResult> results = new HashMap<>();
        
        // Clean expired results while collecting player's results
        proximityCache.entrySet().removeIf(entry -> {
            ProximityResult result = entry.getValue();
            
            // Check if result is expired
            if (result.isExpired()) {
                return true; // Remove expired entries
            }
            
            // If this entry involves the player, add it to results
            if (result.getPlayer1Id().equals(playerId) || result.getPlayer2Id().equals(playerId)) {
                String otherPlayerId = result.getPlayer1Id().equals(playerId) ? 
                                     result.getPlayer2Id() : result.getPlayer1Id();
                results.put(otherPlayerId, result);
            }
            
            return false; // Keep the entry
        });
        
        return results;
    }
    
    /**
     * Clears the proximity cache for a specific game.
     * Should be called when game state changes significantly.
     * 
     * @param gameId ID of the game to clear cache for
     */
    public void clearProximityCache(String gameId) {
        if (gameId == null) {
            return;
        }
        
        proximityCache.keySet().removeIf(key -> key.startsWith(gameId + ":"));
        logger.debug("Cleared proximity cache for game {}", gameId);
    }
    
    /**
     * Generate a cache key for proximity checks.
     * 
     * @param gameId Game ID
     * @param player1Id First player ID
     * @param player2Id Second player ID
     * @return Cache key string
     */
    private String generateCacheKey(String gameId, String player1Id, String player2Id) {
        // Ensure consistent ordering of player IDs for bidirectional caching
        if (player1Id.compareTo(player2Id) > 0) {
            String temp = player1Id;
            player1Id = player2Id;
            player2Id = temp;
        }
        
        return gameId + ":" + player1Id + ":" + player2Id;
    }

    /**
     * Checks proximity of a player to their target and any hunters, sending alerts if necessary.
     *
     * @param gameId ID of the game
     * @param playerId ID of the player to check alerts for
     */
    public void checkAndSendProximityAlerts(String gameId, String playerId) {
        logger.debug("Checking proximity alerts for player {} in game {}", playerId, gameId);
        Player player;
        Game game;
        MapConfiguration mapConfig;
        try {
            player = playerDao.getPlayerById(playerId)
                    .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + playerId));
            game = gameDao.getGameById(gameId)
                    .orElseThrow(() -> new GameNotFoundException("Game not found: " + gameId));
            mapConfig = mapConfigService.getEffectiveMapConfiguration(gameId);

            if (player.getLatitude() == null || player.getLongitude() == null) {
                logger.warn("Cannot check proximity alerts for player {}: location unknown.", playerId);
                return;
            }

            Double alertDistance = mapConfig.getProximityAwarenessDistanceMeters();
            if (alertDistance == null) {
                logger.warn("Proximity awareness distance not configured for map {} in game {}. Skipping alerts.",
                          mapConfig.getMapId(), gameId);
                return;
            }

            double effectiveAlertDistance = alertDistance + GPS_ACCURACY_BUFFER;

            // Check distance to target
            String targetId = player.getTargetID();
            if (targetId != null) {
                checkDistanceAndAlert(gameId, player, targetId, effectiveAlertDistance, "target");
            }

            // Check distance to hunters
            List<Player> hunters = playerDao.getPlayersTargeting(playerId, gameId);
            if (hunters != null) {
                for (Player hunter : hunters) {
                    checkDistanceAndAlert(gameId, player, hunter.getPlayerID(), effectiveAlertDistance, "hunter");
                }
            }

        } catch (PlayerNotFoundException | GameNotFoundException e) {
            logger.error("Error checking proximity alerts for player {}: {}", playerId, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error checking proximity alerts for player {}: {}", playerId, e.getMessage(), e);
        }
        // Clean up expired alert cache entries periodically
        cleanupAlertCache();
    }

    /**
     * Helper method to check distance between two players and send an alert if needed.
     *
     * @param gameId The game ID.
     * @param player The player receiving the potential alert.
     * @param subjectPlayerId The ID of the other player (target or hunter).
     * @param alertDistance The distance threshold for triggering an alert.
     * @param subjectType "target" or "hunter".
     */
    private void checkDistanceAndAlert(String gameId, Player player, String subjectPlayerId, double alertDistance, String subjectType) {
        try {
            Player subjectPlayer = playerDao.getPlayerById(subjectPlayerId)
                    .orElseThrow(() -> new PlayerNotFoundException(subjectType + " not found: " + subjectPlayerId));

            if (subjectPlayer.getLatitude() == null || subjectPlayer.getLongitude() == null) {
                logger.debug("Cannot check distance to {}: {} has no location.", subjectType, subjectPlayerId);
                return;
            }

            double distance = calculateDistanceBetweenPlayers(player.getPlayerID(), subjectPlayer.getPlayerID());

            if (distance <= alertDistance) {
                String alertCacheKey = generateAlertCacheKey(gameId, player.getPlayerID(), subjectPlayerId, subjectType);
                if (!isAlertOnCooldown(alertCacheKey)) {
                    String message = String.format("Your %s (%s) is nearby! (Approx. %.0fm)",
                            subjectType, subjectPlayer.getPlayerName(), distance);
                    logger.info("Sending proximity alert to {}: {}", player.getPlayerID(), message);

                    Notification notification = new Notification();
                    notification.setRecipientPlayerId(player.getPlayerID());
                    notification.setGameId(gameId);
                    notification.setType(NotificationType.PROXIMITY_ALERT.name());
                    notification.setTitle(subjectType.substring(0, 1).toUpperCase() + subjectType.substring(1) + " Nearby");
                    notification.setMessage(message);
                    notification.setTimestamp(String.valueOf(System.currentTimeMillis()));

                    notificationService.sendNotification(notification);

                    // Update alert cache
                    alertCache.put(alertCacheKey, System.currentTimeMillis());
                } else {
                    logger.debug("Proximity alert for {} to {} about {} is on cooldown.",
                               player.getPlayerID(), subjectPlayerId, subjectType);
                }
            } else {
                 // Optional: Consider removing the alert from cooldown if players move far apart again?
                 // String alertCacheKey = generateAlertCacheKey(gameId, player.getPlayerID(), subjectPlayerId, subjectType);
                 // alertCache.remove(alertCacheKey);
            }

        } catch (PlayerNotFoundException e) {
            logger.warn("Cannot check distance for alert: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Error checking distance for alert between {} and {}: {}",
                       player.getPlayerID(), subjectPlayerId, e.getMessage(), e);
        }
    }

    /**
     * Calculate the actual distance between two players using their Player objects.
     * Assumes locations are not null (should be checked before calling).
     *
     * @param player1 First player object
     * @param player2 Second player object
     * @return Distance in meters, or Double.MAX_VALUE if locations are unknown or calculation fails.
     */
    private double calculateDistanceBetweenPlayersInternal(Player player1, Player player2) {
        if (player1 == null || player2 == null || 
            player1.getLatitude() == null || player1.getLongitude() == null ||
            player2.getLatitude() == null || player2.getLongitude() == null) {
            
            logger.warn("Cannot calculate distance: one or both players/locations are null. P1: {}, P2: {}", 
                      player1 != null ? player1.getPlayerID() : "null", 
                      player2 != null ? player2.getPlayerID() : "null");
            return Double.MAX_VALUE; 
        }
        
        try {
            return GeoUtils.calculateDistance(
                new Coordinate(player1.getLatitude(), player1.getLongitude()),
                new Coordinate(player2.getLatitude(), player2.getLongitude())
            );
        } catch (Exception e) {
             logger.error("Error calculating distance between {} and {}: {}", 
                        player1.getPlayerID(), player2.getPlayerID(), e.getMessage(), e);
             return Double.MAX_VALUE; // Return max value on calculation error
        }
    }

    /**
     * Generate a cache key for proximity alerts.
     *
     * @param gameId Game ID
     * @param recipientPlayerId Player receiving the alert
     * @param subjectPlayerId Player the alert is about (target/hunter)
     * @param alertType Type of alert ("target" or "hunter")
     * @return Cache key string
     */
    private String generateAlertCacheKey(String gameId, String recipientPlayerId, String subjectPlayerId, String alertType) {
        return gameId + ":" + recipientPlayerId + ":" + subjectPlayerId + ":" + alertType;
    }

    /**
     * Check if a specific alert type is currently on cooldown for the player.
     *
     * @param alertCacheKey The generated key for the alert.
     * @return true if the alert is on cooldown, false otherwise.
     */
    private boolean isAlertOnCooldown(String alertCacheKey) {
        Long lastAlertTimestamp = alertCache.get(alertCacheKey);
        if (lastAlertTimestamp == null) {
            return false; // Not on cooldown if never sent
        }
        return (System.currentTimeMillis() - lastAlertTimestamp) < ALERT_COOLDOWN_MS;
    }

    /**
     * Cleans up expired entries from the alert cache.
     */
    private void cleanupAlertCache() {
        long now = System.currentTimeMillis();
        alertCache.entrySet().removeIf(entry -> (now - entry.getValue()) > ALERT_COOLDOWN_MS);
        // Optionally log cache size after cleanup
        // logger.debug("Alert cache size after cleanup: {}", alertCache.size());
    }
} 