package com.assassin.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import com.assassin.model.Player;
import com.assassin.util.GeoUtils;

/**
 * Service responsible for handling player location updates and boundary checks.
 * Provides functionality for location tracking and geofencing.
 */
public class LocationService {

    private static final Logger logger = LoggerFactory.getLogger(LocationService.class);
    private final PlayerDao playerDao;
    private final GameDao gameDao;
    private final MapConfigurationService mapConfigService;
    
    // Constants for location validation
    private static final double DEFAULT_SPEED_LIMIT_METERS_PER_SECOND = 30.0; // ~108 km/h or ~67 mph
    private static final int MAX_LOCATION_HISTORY_SIZE = 10; // Number of recent locations to keep

    // Default constructor
    public LocationService() {
        this(new DynamoDbPlayerDao(), 
             new DynamoDbGameDao(), 
             new MapConfigurationService(
                new DynamoDbGameDao(), 
                new DynamoDbGameZoneStateDao(),
                new DynamoDbSafeZoneDao(),
                new ShrinkingZoneService(
                    new DynamoDbGameDao(),
                    new DynamoDbGameZoneStateDao(),
                    new DynamoDbPlayerDao()
                )
             )
        );
    }

    // Constructor for dependency injection (testing)
    public LocationService(PlayerDao playerDao, GameDao gameDao) {
        // This constructor might need to be adjusted or removed depending on testing strategy,
        // as MapConfigurationService now has required dependencies.
        // For now, initialize MapConfigurationService with default DAOs/Services.
        this(playerDao, 
             gameDao, 
             new MapConfigurationService(
                new DynamoDbGameDao(), 
                new DynamoDbGameZoneStateDao(),
                new DynamoDbSafeZoneDao(),
                new ShrinkingZoneService(
                    new DynamoDbGameDao(),
                    new DynamoDbGameZoneStateDao(),
                    new DynamoDbPlayerDao()
                )
             )
        );
    }
    
    // Full constructor for all dependencies
    public LocationService(PlayerDao playerDao, GameDao gameDao, MapConfigurationService mapConfigService) {
        this.playerDao = Objects.requireNonNull(playerDao, "playerDao cannot be null");
        this.gameDao = Objects.requireNonNull(gameDao, "gameDao cannot be null");
        this.mapConfigService = Objects.requireNonNull(mapConfigService, "mapConfigService cannot be null");
    }

    /**
     * Updates a player's location after validation and boundary checks.
     * Performs enhanced validation including coordinate range checking and movement speed validation.
     *
     * @param playerId The ID of the player.
     * @param latitude The reported latitude.
     * @param longitude The reported longitude.
     * @param accuracy The reported accuracy in meters.
     * @throws PlayerNotFoundException If the player doesn't exist.
     * @throws GameNotFoundException If the player's game doesn't exist.
     * @throws InvalidLocationException If the location is invalid (outside boundaries, impossible movement).
     * @throws PlayerPersistenceException If the database update fails.
     * @throws IllegalArgumentException If input parameters are invalid.
     */
    public void updatePlayerLocation(String playerId, Double latitude, Double longitude, Double accuracy)
            throws PlayerNotFoundException, GameNotFoundException, InvalidLocationException, PlayerPersistenceException {
        
        logger.debug("Attempting to update location for player: {}", playerId);
        
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
            return;
        }
        
        Game game = gameDao.getGameById(player.getGameID())
                .orElseThrow(() -> new GameNotFoundException("Game not found for player: " + playerId + ", Game ID: " + player.getGameID()));
                
        // 3. Movement speed validation (if previous location exists)
        if (player.getLatitude() != null && player.getLongitude() != null && 
            player.getLocationTimestamp() != null) {
            
            validateMovementSpeed(
                player.getLatitude(), player.getLongitude(), player.getLocationTimestamp(),
                latitude, longitude, Instant.now().toString(),
                game);
        }
        
        // 4. Boundary Check (if boundaries are defined for the game)
        Coordinate location = new Coordinate(latitude, longitude);
        if (!isWithinBoundaries(location, game)) {
            logger.warn("Player {} reported location ({}, {}) outside game boundaries for game {}",
                        playerId, latitude, longitude, game.getGameID());
            throw new InvalidLocationException("Reported location is outside the defined game boundaries.");
        }
        
        // 5. Update Player Location in DAO
        String timestamp = Instant.now().toString();
        try {
            playerDao.updatePlayerLocation(playerId, latitude, longitude, timestamp, accuracy);
            logger.info("Successfully updated location for player: {}, Timestamp: {}", playerId, timestamp);
            
            // 6. Add to location history if needed
            // In a full implementation, we would store the location history
            // updateLocationHistory(playerId, latitude, longitude, timestamp);
            
        } catch (PlayerNotFoundException pnfe) { 
            // Should ideally not happen if fetched above, but handle defensively
            logger.error("Consistency issue: Player {} found initially but not during update.", playerId, pnfe);
            throw pnfe; 
        } catch (PlayerPersistenceException ppe) {
            logger.error("Failed to persist location update for player {}: {}", playerId, ppe.getMessage(), ppe);
            throw ppe;
        }
    }

    /**
     * Checks if the given coordinates are within the game's defined boundaries.
     * Uses the game's `boundary` field which is expected to be a List<Coordinate> defining a polygon.
     *
     * @param location The Coordinate to check.
     * @param game The game object containing the boundary field.
     * @return true if within boundaries or if no boundaries are defined, false otherwise.
     */
    public boolean isWithinBoundaries(Coordinate location, Game game) {
        if (location == null || game == null) {
            logger.warn("Cannot check boundaries with null location or game.");
            return false; // Or throw an exception, depending on desired behavior
        }

        List<Coordinate> boundary = game.getBoundary();

        if (boundary == null || boundary.isEmpty()) {
            logger.debug("No boundary defined or boundary is empty for game {}, assuming location is valid.", game.getGameID());
            return true; // No boundaries defined or empty, always considered inside
        }

        // Use GeoUtils for the point-in-polygon check
        boolean inside = GeoUtils.isPointInBoundary(location, boundary);

        logger.debug("Point ({}, {}) is {} polygon boundary for game {}",
                     location.getLatitude(), location.getLongitude(), inside ? "inside" : "outside", game.getGameID());
        return inside;
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
     * Checks if a player is near a specific coordinate.
     *
     * @param playerId ID of the player to check.
     * @param targetLat Latitude of the target location.
     * @param targetLon Longitude of the target location.
     * @param radiusMeters Radius in meters to consider "near".
     * @return true if the player is within the specified radius of the target, false otherwise.
     * @throws PlayerNotFoundException if the player doesn't exist.
     */
    public boolean isPlayerNearLocation(String playerId, double targetLat, double targetLon, double radiusMeters) 
            throws PlayerNotFoundException {
        
        Player player = playerDao.getPlayerById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + playerId));
                
        if (player.getLatitude() == null || player.getLongitude() == null) {
            logger.debug("Player {} has no location data", playerId);
            return false;
        }
        
        double distance = GeoUtils.calculateDistance(
            new Coordinate(player.getLatitude(), player.getLongitude()),
            new Coordinate(targetLat, targetLon)
        );
        
        boolean isNear = distance <= radiusMeters;
        logger.debug("Player {} is {}within {} meters of target location ({}, {}). Distance: {} meters",
            playerId, isNear ? "" : "not ", radiusMeters, targetLat, targetLon, distance);
            
        return isNear;
    }
    
    /**
     * Checks if two players are near each other.
     *
     * @param player1Id ID of the first player.
     * @param player2Id ID of the second player.
     * @param radiusMeters Radius in meters to consider "near".
     * @return true if the players are within the specified radius of each other, false otherwise.
     * @throws PlayerNotFoundException if either player doesn't exist.
     */
    public boolean arePlayersNearby(String player1Id, String player2Id, double radiusMeters) 
            throws PlayerNotFoundException {
        
        Player player1 = playerDao.getPlayerById(player1Id)
                .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + player1Id));
                
        Player player2 = playerDao.getPlayerById(player2Id)
                .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + player2Id));
                
        if (player1.getLatitude() == null || player1.getLongitude() == null ||
            player2.getLatitude() == null || player2.getLongitude() == null) {
            logger.debug("One or both players have no location data");
            return false;
        }
        
        double distance = GeoUtils.calculateDistance(
            new Coordinate(player1.getLatitude(), player1.getLongitude()),
            new Coordinate(player2.getLatitude(), player2.getLongitude())
        );
        
        boolean isNear = distance <= radiusMeters;
        logger.debug("Players {} and {} are {}within {} meters of each other. Distance: {} meters",
            player1Id, player2Id, isNear ? "" : "not ", radiusMeters, distance);
            
        return isNear;
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