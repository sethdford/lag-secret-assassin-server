package com.assassin.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbGameZoneStateDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.DynamoDbSafeZoneDao;
import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.InvalidLocationException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.PlayerPersistenceException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.GameZoneState;
import com.assassin.model.Player;
import com.assassin.service.GeofenceManager.GeofenceEvent;
import com.assassin.service.GeofenceManager.GeofenceEventType;
import com.assassin.util.GeoUtils;

/**
 * Service responsible for handling player location updates and boundary checks.
 * Provides functionality for location tracking, geofencing, and shrinking zone management.
 */
public class LocationService {

    private static final Logger logger = LoggerFactory.getLogger(LocationService.class);
    private final PlayerDao playerDao;
    private final GameDao gameDao;
    private final MapConfigurationService mapConfigService;
    private final GeofenceManager geofenceManager;
    private final ShrinkingZoneService shrinkingZoneService;
    
    // Constants for location validation
    private static final double DEFAULT_SPEED_LIMIT_METERS_PER_SECOND = 30.0; // ~108 km/h or ~67 mph
    private static final int MAX_LOCATION_HISTORY_SIZE = 10; // Number of recent locations to keep

    // Default constructor
    public LocationService() {
        ShrinkingZoneService shrinkingZone = new ShrinkingZoneService(
            new DynamoDbGameDao(),
            new DynamoDbGameZoneStateDao(),
            new DynamoDbPlayerDao()
        );
        
        MapConfigurationService mapConfig = new MapConfigurationService(
            new DynamoDbGameDao(), 
            new DynamoDbGameZoneStateDao(),
            new DynamoDbSafeZoneDao(),
            shrinkingZone
        );
        
        this.playerDao = new DynamoDbPlayerDao();
        this.gameDao = new DynamoDbGameDao();
        this.mapConfigService = mapConfig;
        this.geofenceManager = new GeofenceManager(mapConfig);
        this.shrinkingZoneService = shrinkingZone;
    }

    // Constructor for dependency injection (testing)
    public LocationService(PlayerDao playerDao, GameDao gameDao) {
        // This constructor might need to be adjusted or removed depending on testing strategy,
        // as MapConfigurationService now has required dependencies.
        // For now, initialize MapConfigurationService with default DAOs/Services.
        ShrinkingZoneService shrinkingZone = new ShrinkingZoneService(
            new DynamoDbGameDao(),
            new DynamoDbGameZoneStateDao(),
            new DynamoDbPlayerDao()
        );
        
        MapConfigurationService mapConfig = new MapConfigurationService(
            new DynamoDbGameDao(), 
            new DynamoDbGameZoneStateDao(),
            new DynamoDbSafeZoneDao(),
            shrinkingZone
        );
        
        this.playerDao = Objects.requireNonNull(playerDao, "playerDao cannot be null");
        this.gameDao = Objects.requireNonNull(gameDao, "gameDao cannot be null");
        this.mapConfigService = mapConfig;
        this.geofenceManager = new GeofenceManager(mapConfig);
        this.shrinkingZoneService = shrinkingZone;
    }
    
    // Full constructor for all dependencies
    public LocationService(PlayerDao playerDao, GameDao gameDao, 
                          MapConfigurationService mapConfigService,
                          GeofenceManager geofenceManager,
                          ShrinkingZoneService shrinkingZoneService) {
        this.playerDao = Objects.requireNonNull(playerDao, "playerDao cannot be null");
        this.gameDao = Objects.requireNonNull(gameDao, "gameDao cannot be null");
        this.mapConfigService = Objects.requireNonNull(mapConfigService, "mapConfigService cannot be null");
        this.geofenceManager = Objects.requireNonNull(geofenceManager, "geofenceManager cannot be null");
        this.shrinkingZoneService = Objects.requireNonNull(shrinkingZoneService, "shrinkingZoneService cannot be null");
    }

    /**
     * Updates a player's location after validation and boundary checks.
     * Performs enhanced validation including coordinate range checking and movement speed validation.
     * Also notifies the GeofenceManager to monitor for boundary crossings.
     *
     * @param playerId The ID of the player.
     * @param latitude The reported latitude.
     * @param longitude The reported longitude.
     * @param accuracy The reported accuracy in meters.
     * @return Optional GeofenceEvent if a boundary event occurred, empty otherwise
     * @throws PlayerNotFoundException If the player doesn't exist.
     * @throws GameNotFoundException If the player's game doesn't exist.
     * @throws InvalidLocationException If the location is invalid (outside boundaries, impossible movement).
     * @throws PlayerPersistenceException If the database update fails.
     * @throws IllegalArgumentException If input parameters are invalid.
     */
    public Optional<GeofenceEvent> updatePlayerLocation(String playerId, Double latitude, Double longitude, Double accuracy)
            throws PlayerNotFoundException, GameNotFoundException, InvalidLocationException, PlayerPersistenceException {
        
        logger.debug("Attempting to update location for player: {}", playerId);
        Optional<GeofenceEvent> geofenceEvent = Optional.empty();
        
        // 1. Basic Validation
        if (playerId == null || playerId.isEmpty()) {
            throw new IllegalArgumentException("Player ID cannot be null or empty");
        }
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Latitude and Longitude cannot be null");
        }
        
        // Use the private method to validate coordinate ranges
        if (!validateCoordinates(latitude, longitude)) {
            throw new InvalidLocationException(
                    String.format("Invalid coordinate values: latitude=%f, longitude=%f", latitude, longitude));
        }
        
        // 2. Fetch Player and Game
        Player player = playerDao.getPlayerById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + playerId));
        
        if (player.getGameID() == null || player.getGameID().isEmpty()) {
            logger.warn("Player {} is not associated with any game. Location update skipped.", playerId);
            // If the player is not in a game, we'll still update their location but skip game-specific validations
            String timestamp = Instant.now().toString();
            playerDao.updatePlayerLocation(playerId, latitude, longitude, timestamp, accuracy);
            logger.info("Updated location for player {} not in a game: ({}, {})", 
                      playerId, latitude, longitude);
            return Optional.empty();
        }
        
        String gameId = player.getGameID();
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found for player: " + playerId + ", Game ID: " + gameId));
                
        // 3. Movement speed validation (if previous location exists)
        if (player.getLatitude() != null && player.getLongitude() != null && 
            player.getLocationTimestamp() != null) {
            
            validateMovementSpeed(
                player.getLatitude(), player.getLongitude(), player.getLocationTimestamp(),
                latitude, longitude, Instant.now().toString(),
                game);
        }
        
        // 4. Create coordinate object
        Coordinate location = new Coordinate(latitude, longitude);
        
        // 5. Boundary Check (using MapConfigurationService)
        if (!mapConfigService.isCoordinateInGameBoundary(gameId, location)) {
            logger.warn("Player {} reported location ({}, {}) outside game boundaries for game {}",
                        playerId, latitude, longitude, gameId);
            throw new InvalidLocationException("Reported location is outside the defined game boundaries.");
        }
        
        // 6. Check shrinking zone if enabled for this game
        ShrinkingZoneEvent shrinkingZoneEvent = checkShrinkingZoneBoundary(gameId, playerId, location);
        
        // 7. Update GeofenceManager to check for boundary events
        geofenceEvent = geofenceManager.updatePlayerLocation(gameId, playerId, location);
        
        // 8. Update Player Location in DAO
        String timestamp = Instant.now().toString();
        try {
            playerDao.updatePlayerLocation(playerId, latitude, longitude, timestamp, accuracy);
            logger.info("Successfully updated location for player: {}, Timestamp: {}", playerId, timestamp);
            
            // 8. Log any boundary events
            if (geofenceEvent.isPresent()) {
                GeofenceEvent event = geofenceEvent.get();
                if (event.getEventType() == GeofenceEventType.EXIT_BOUNDARY) {
                    logger.warn("Player {} has exited the game boundary for game {}", playerId, gameId);
                } else if (event.getEventType() == GeofenceEventType.ENTER_BOUNDARY) {
                    logger.info("Player {} has entered the game boundary for game {}", playerId, gameId);
                } else if (event.getEventType() == GeofenceEventType.APPROACHING_BOUNDARY) {
                    logger.debug("Player {} is approaching the boundary for game {}, distance: {}m", 
                                playerId, gameId, String.format("%.2f", event.getDistanceToBoundary()));
                }
            }
            
        } catch (PlayerNotFoundException pnfe) { 
            // Should ideally not happen if fetched above, but handle defensively
            logger.error("Consistency issue: Player {} found initially but not during update.", playerId, pnfe);
            throw pnfe; 
        } catch (PlayerPersistenceException ppe) {
            logger.error("Failed to persist location update for player {}: {}", playerId, ppe.getMessage(), ppe);
            throw ppe;
        }
        
        return geofenceEvent;
    }
    
    /**
     * Checks if a player is inside or outside the current shrinking zone and logs events.
     * 
     * @param gameId The game ID
     * @param playerId The player ID  
     * @param location The player's current location
     * @return ShrinkingZoneEvent if zone checking is applicable, null otherwise
     */
    private ShrinkingZoneEvent checkShrinkingZoneBoundary(String gameId, String playerId, Coordinate location) {
        try {
            // Check if shrinking zone is enabled for this game
            if (!shrinkingZoneService.isShrinkingZoneEnabled(gameId)) {
                logger.debug("Shrinking zone not enabled for game {}. Skipping zone check.", gameId);
                return null;
            }
            
            // Get current zone state
            Optional<GameZoneState> zoneStateOpt = shrinkingZoneService.advanceZoneState(gameId);
            if (zoneStateOpt.isEmpty()) {
                logger.debug("No zone state found for game {}. Skipping zone check.", gameId);
                return null;
            }
            
            GameZoneState zoneState = zoneStateOpt.get();
            
            // Calculate distance from player to zone center
            Coordinate zoneCenter = zoneState.getCurrentCenter();
            if (zoneCenter == null || zoneState.getCurrentRadiusMeters() == null) {
                logger.warn("Incomplete zone state for game {}. Missing center or radius.", gameId);
                return null;
            }
            
            double distanceToCenter = GeoUtils.calculateDistance(location, zoneCenter);
            double zoneRadius = zoneState.getCurrentRadiusMeters();
            boolean isInsideZone = distanceToCenter <= zoneRadius;
            
            // Create event for logging and potential processing
            ShrinkingZoneEvent event = new ShrinkingZoneEvent(
                gameId, playerId, location, isInsideZone, 
                distanceToCenter, zoneRadius, zoneState.getCurrentPhase()
            );
            
            if (!isInsideZone) {
                logger.warn("Player {} is OUTSIDE shrinking zone for game {}. Distance: {:.2f}m, Zone radius: {:.2f}m, Phase: {}", 
                           playerId, gameId, distanceToCenter, zoneRadius, zoneState.getCurrentPhase());
            } else {
                logger.debug("Player {} is inside shrinking zone for game {}. Distance: {:.2f}m, Zone radius: {:.2f}m", 
                            playerId, gameId, distanceToCenter, zoneRadius);
            }
            
            return event;
            
        } catch (Exception e) {
            logger.error("Error checking shrinking zone boundary for player {} in game {}: {}", 
                        playerId, gameId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Determines if a player is currently taking damage from being outside the shrinking zone.
     * 
     * @param gameId The game ID
     * @param playerId The player ID
     * @param location The player's current location
     * @return true if the player should be taking zone damage, false otherwise
     */
    public boolean isPlayerTakingZoneDamage(String gameId, String playerId, Coordinate location) {
        try {
            // Check if shrinking zone is enabled for this game
            if (!shrinkingZoneService.isShrinkingZoneEnabled(gameId)) {
                return false;
            }
            
            // Get current zone state
            Optional<GameZoneState> zoneStateOpt = shrinkingZoneService.advanceZoneState(gameId);
            if (zoneStateOpt.isEmpty()) {
                return false;
            }
            
            GameZoneState zoneState = zoneStateOpt.get();
            
            // Only apply damage during SHRINKING or active phases, not during WAITING
            if ("WAITING".equals(zoneState.getCurrentPhase())) {
                return false;
            }
            
            // Check if player is outside the current zone
            Coordinate zoneCenter = zoneState.getCurrentCenter();
            if (zoneCenter == null || zoneState.getCurrentRadiusMeters() == null) {
                return false;
            }
            
            double distanceToCenter = GeoUtils.calculateDistance(location, zoneCenter);
            double zoneRadius = zoneState.getCurrentRadiusMeters();
            boolean isOutsideZone = distanceToCenter > zoneRadius;
            
            if (isOutsideZone) {
                logger.info("Player {} is taking zone damage for game {}. Distance: {:.2f}m, Zone radius: {:.2f}m", 
                           playerId, gameId, distanceToCenter, zoneRadius);
            }
            
            return isOutsideZone;
            
        } catch (Exception e) {
            logger.error("Error checking zone damage for player {} in game {}: {}", 
                        playerId, gameId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Event data for shrinking zone boundary checking
     */
    public static class ShrinkingZoneEvent {
        private final String gameId;
        private final String playerId;
        private final Coordinate playerLocation;
        private final boolean isInsideZone;
        private final double distanceToCenter;
        private final double zoneRadius;
        private final String zonePhase;
        
        public ShrinkingZoneEvent(String gameId, String playerId, Coordinate playerLocation,
                                 boolean isInsideZone, double distanceToCenter, double zoneRadius, String zonePhase) {
            this.gameId = gameId;
            this.playerId = playerId;
            this.playerLocation = playerLocation;
            this.isInsideZone = isInsideZone;
            this.distanceToCenter = distanceToCenter;
            this.zoneRadius = zoneRadius;
            this.zonePhase = zonePhase;
        }
        
        public String getGameId() { return gameId; }
        public String getPlayerId() { return playerId; }
        public Coordinate getPlayerLocation() { return playerLocation; }
        public boolean isInsideZone() { return isInsideZone; }
        public double getDistanceToCenter() { return distanceToCenter; }
        public double getZoneRadius() { return zoneRadius; }
        public String getZonePhase() { return zonePhase; }
        
        public double getDistanceOutsideZone() {
            return Math.max(0, distanceToCenter - zoneRadius);
        }
    }

    /**
     * Validates that the movement speed between two location points is physically possible.
     * Guards against location spoofing or glitches.
     *
     * @param oldLat Previous latitude
     * @param oldLon Previous longitude
     * @param oldTimestamp Previous timestamp (ISO format)
     * @param newLat New latitude
     * @param newLon New longitude
     * @param newTimestamp New timestamp (ISO format)
     * @param game The game object (for game-specific speed limits)
     * @throws InvalidLocationException if movement speed is physically impossible
     */
    private void validateMovementSpeed(
            Double oldLat, Double oldLon, String oldTimestamp,
            Double newLat, Double newLon, String newTimestamp,
            Game game) throws InvalidLocationException {
            
        try {
            // Parse timestamps
            Instant oldTime = Instant.parse(oldTimestamp);
            Instant newTime = Instant.parse(newTimestamp);
            
            // Calculate time difference in seconds
            long secondsDiff = java.time.Duration.between(oldTime, newTime).getSeconds();
            if (secondsDiff <= 0) {
                // If timestamps are the same or out of order, skip validation
                return;
            }
            
            // Calculate distance between points
            double distance = GeoUtils.calculateDistance(
                new Coordinate(oldLat, oldLon), 
                new Coordinate(newLat, newLon)
            );
            
            // Calculate speed in meters per second
            double speed = distance / secondsDiff;
            
            // Get speed limit - could be customized per game in a full implementation
            double speedLimit = getSpeedLimit(game);
            
            if (speed > speedLimit) {
                logger.warn("Detected suspicious movement speed: {} m/s (limit: {} m/s) for player in game {}",
                    speed, speedLimit, game.getGameID());
                throw new InvalidLocationException(
                    String.format("Movement speed (%.2f m/s) exceeds the maximum allowed (%.2f m/s)", 
                        speed, speedLimit));
            }
        } catch (Exception e) {
            // If anything goes wrong with the calculation, log it but don't block the update
            // This is a safety check, not a critical validation
            if (!(e instanceof InvalidLocationException)) {
                logger.warn("Error checking movement speed: {}", e.getMessage());
            } else {
                throw (InvalidLocationException) e;
            }
        }
    }
    
    /**
     * Gets the speed limit for a specific game.
     * Could be customized based on game settings in a full implementation.
     *
     * @param game The game to get the speed limit for.
     * @return The maximum allowed speed in meters per second.
     */
    private double getSpeedLimit(Game game) {
        // In a real implementation, this could use game.getSettings() to get a custom speed limit
        // For now, return the default
        return DEFAULT_SPEED_LIMIT_METERS_PER_SECOND;
    }
    
    /**
     * Gets configuration parameters for the mapping service that can be passed to clients.
     *
     * @param gameId ID of the game to get configuration for.
     * @return Map of configuration parameters.
     * @throws GameNotFoundException If the specified game cannot be found.
     */
    public Map<String, Object> getClientMapConfiguration(String gameId) throws GameNotFoundException {
        Game game = gameDao.getGameById(gameId)
                         .orElseThrow(() -> new GameNotFoundException("Game not found: " + gameId));

        Map<String, Object> config = new HashMap<>();

        // Include game boundary
        config.put("gameBoundary", mapConfigService.getGameBoundary(gameId));
        
        // Include max allowed map size
        config.put("maxMapSizeMeters", mapConfigService.getMaxMapSizeMeters());

        // Include game status
        config.put("gameStatus", game.getStatus());
        
        // TODO: Add safe zone information if needed by the client
        // config.put("safeZones", mapConfigService.getActiveSafeZones(gameId));

        // TODO: Add shrinking zone status if applicable and needed by client
        /*
        if (Boolean.TRUE.equals(game.getShrinkingZoneEnabled())) {
            try {
                Optional<Coordinate> center = shrinkingZoneService.getCurrentZoneCenter(gameId);
                Optional<Double> radius = shrinkingZoneService.getCurrentZoneRadius(gameId);
                // Include current zone details if needed
            } catch (GameStateException e) {
                logger.warn("Could not retrieve shrinking zone state for client config: {}", e.getMessage());
            }
        }
        */

        return config;
    }
    
    /**
     * Checks if a player is near a specific target location.
     *
     * @param playerId The ID of the player.
     * @param targetLat Target latitude.
     * @param targetLon Target longitude.
     * @param radiusMeters The radius in meters to check within.
     * @return true if the player is within the radius, false otherwise or if location is unknown.
     * @throws PlayerNotFoundException If the player doesn't exist.
     */
    public boolean isPlayerNearLocation(String playerId, double targetLat, double targetLon, double radiusMeters) 
            throws PlayerNotFoundException {
        Player player = playerDao.getPlayerById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + playerId));

        // Use the correct getter methods from the Player object
        Double playerLat = player.getLatitude();
        Double playerLon = player.getLongitude();

        if (playerLat == null || playerLon == null) {
            logger.warn("Player {} has no known location, cannot check proximity.", playerId);
            return false; // Cannot determine proximity without location
        }

        // Use the Double values directly
        double distance = GeoUtils.calculateDistance(
                new Coordinate(playerLat, playerLon), 
                new Coordinate(targetLat, targetLon)
        );

        boolean isNear = distance <= radiusMeters;
        logger.debug("Player {} distance to ({}, {}): {} meters. Is within {}m radius? {}",
                     playerId, targetLat, targetLon, distance, radiusMeters, isNear);
        return isNear;
    }
    
    /**
     * Checks if two players are near each other based on their last known locations.
     *
     * @param player1Id ID of the first player.
     * @param player2Id ID of the second player.
     * @param radiusMeters The radius in meters to check within.
     * @return true if the players are within the radius of each other, false otherwise or if locations are unknown.
     * @throws PlayerNotFoundException If either player doesn't exist.
     */
    public boolean arePlayersNearby(String player1Id, String player2Id, double radiusMeters) 
            throws PlayerNotFoundException {
        Player player1 = playerDao.getPlayerById(player1Id)
                .orElseThrow(() -> new PlayerNotFoundException("Player 1 not found: " + player1Id));
        Player player2 = playerDao.getPlayerById(player2Id)
                .orElseThrow(() -> new PlayerNotFoundException("Player 2 not found: " + player2Id));

        // Use correct getters
        Double lat1 = player1.getLatitude();
        Double lon1 = player1.getLongitude();
        Double lat2 = player2.getLatitude();
        Double lon2 = player2.getLongitude();

        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
             logger.warn("Cannot check proximity between {} and {}: one or both players have unknown locations.", 
                         player1Id, player2Id);
            return false;
        }

        // Use Double values directly
        double distance = GeoUtils.calculateDistance(
            new Coordinate(lat1, lon1), 
            new Coordinate(lat2, lon2)
        );

        boolean areNear = distance <= radiusMeters;
        logger.debug("Distance between player {} and {}: {} meters. Are within {}m radius? {}",
                     player1Id, player2Id, distance, radiusMeters, areNear);
        return areNear;
    }

    /**
     * Validates if the given coordinates are within valid geographic ranges.
     * 
     * @param latitude Latitude in degrees
     * @param longitude Longitude in degrees
     * @return true if coordinates are valid, false otherwise
     */
    private boolean validateCoordinates(double latitude, double longitude) {
        return GeoUtils.isValidCoordinate(latitude, longitude);
    }
} 