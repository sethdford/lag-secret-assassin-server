package com.assassin.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

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
import com.assassin.model.Player;
import com.assassin.util.GeoUtils;
import com.assassin.model.Notification;
import com.assassin.model.NotificationType;

/**
 * Service responsible for detecting proximity between players for elimination mechanics.
 * Provides optimized methods for checking when players are close enough for elimination attempts.
 */
public class ProximityDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(ProximityDetectionService.class);
    
    // Default threshold distances for different elimination modes in meters
    private static final double DEFAULT_ELIMINATION_DISTANCE = 10.0;  // Default for close-range eliminations
    private static final double LONG_RANGE_ELIMINATION_DISTANCE = 50.0; // For "long-range" weapons
    private static final double EXTREME_RANGE_ELIMINATION_DISTANCE = 100.0; // For "extreme-range" weapons
    
    // GPS accuracy compensation (meters)
    private static final double GPS_ACCURACY_BUFFER = 5.0;
    
    // Cache of recent proximity checks to reduce unnecessary recalculations
    private final Map<String, ProximityResult> proximityCache;
    
    // Cache expiration time in milliseconds
    private static final long CACHE_EXPIRATION_MS = 10000; // 10 seconds
    
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
     * Takes into account game rules, weapon types, and adds a buffer for GPS inaccuracy.
     * 
     * @param gameId ID of the game
     * @param playerId ID of the player attempting elimination
     * @param targetId ID of the target player
     * @param weaponType Optional weapon type affecting elimination distance
     * @return true if players are within required proximity, false otherwise
     * @throws PlayerNotFoundException If either player cannot be found
     * @throws GameNotFoundException If the game cannot be found
     */
    public boolean canEliminateTarget(String gameId, String playerId, String targetId, String weaponType) 
            throws PlayerNotFoundException, GameNotFoundException {
        
        // Validate inputs
        if (gameId == null || playerId == null || targetId == null) {
            logger.warn("Null parameters in canEliminateTarget: gameId={}, playerId={}, targetId={}", 
                      gameId, playerId, targetId);
            return false;
        }
        
        // Get the game to check game-specific settings
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found: " + gameId));
        
        // Get map configuration for proximity settings
        MapConfiguration mapConfig = mapConfigService.getEffectiveMapConfiguration(gameId);

        // Determine elimination distance based on map config and weapon type
        double eliminationDistance = getEliminationDistance(mapConfig, weaponType);
        
        // Add buffer for GPS inaccuracy
        double effectiveDistance = eliminationDistance + GPS_ACCURACY_BUFFER;
        
        logger.debug("Checking proximity for elimination in game {}. Players: {} -> {}. Required distance: {}m ({}m base + {}m GPS buffer)",
                   gameId, playerId, targetId, effectiveDistance, eliminationDistance, GPS_ACCURACY_BUFFER);
        
        // Use LocationService to check if players are nearby
        boolean inRange = locationService.arePlayersNearby(playerId, targetId, effectiveDistance);
        
        // If we need the actual distance for the cache, we need to get player locations and calculate
        double distance = calculateDistanceBetweenPlayers(playerId, targetId);
        
        // Cache the result
        proximityCache.put(generateCacheKey(gameId, playerId, targetId), new ProximityResult(playerId, targetId, distance, inRange));
        
        return inRange;
    }
    
    /**
     * Calculate the actual distance between two players.
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

        Double lat1 = player1.getLatitude();
        Double lon1 = player1.getLongitude();
        Double lat2 = player2.getLatitude();
        Double lon2 = player2.getLongitude();

        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            logger.warn("Cannot calculate distance between {} and {}: one or both players have unknown locations", 
                      player1Id, player2Id);
            return Double.MAX_VALUE;
        }

        return GeoUtils.calculateDistance(
            new Coordinate(lat1, lon1), 
            new Coordinate(lat2, lon2)
        );
    }
    
    /**
     * Determine the elimination distance based on game settings and weapon type.
     * 
     * @param mapConfig Map configuration for game
     * @param weaponType Type of weapon being used
     * @return Distance in meters required for elimination
     */
    private double getEliminationDistance(MapConfiguration mapConfig, String weaponType) {
        // Get default from map config, fallback to constant if null
        double distance = mapConfig.getEliminationDistanceMeters() != null 
                            ? mapConfig.getEliminationDistanceMeters() 
                            : DEFAULT_ELIMINATION_DISTANCE;
        
        // TODO: Implement logic for weapon-specific distances based on mapConfig or game settings if needed.
        // For now, we just use the base elimination distance from mapConfig.
        if (weaponType != null) {
             logger.warn("Weapon-specific distance logic not yet implemented in getEliminationDistance, using base: {}", distance);
            // Example placeholder logic if weapon distances were stored in mapConfig:
            // Map<String, Double> weaponDistances = mapConfig.getWeaponDistances(); 
            // if (weaponDistances != null && weaponDistances.containsKey(weaponType)) {
            //     distance = weaponDistances.get(weaponType);
            // }
        }
        
        return distance;
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
     * @return Distance in meters.
     */
    private double calculateDistanceBetweenPlayersInternal(Player player1, Player player2) {
        return GeoUtils.calculateDistance(
            new Coordinate(player1.getLatitude(), player1.getLongitude()),
            new Coordinate(player2.getLatitude(), player2.getLongitude())
        );
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