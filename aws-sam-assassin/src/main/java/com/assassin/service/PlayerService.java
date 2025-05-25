package com.assassin.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.PlayerPersistenceException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Player;

/**
 * Service for player-related operations.
 */
public class PlayerService {

    private static final Logger logger = LoggerFactory.getLogger(PlayerService.class);
    private final PlayerDao playerDao;
    private static final Duration LOCATION_PAUSE_COOLDOWN = Duration.ofMinutes(5);

    // Define a simple record for sensitive areas
    private record SensitiveArea(String name, double latitude, double longitude, double radiusMeters) {}

    // Predefined list of sensitive areas (replace with dynamic loading in a real app)
    private static final List<SensitiveArea> SENSITIVE_AREAS = List.of(
        new SensitiveArea("Hospital A", 34.052235, -118.243683, 200), // Example coords LA
        new SensitiveArea("School B", 34.072235, -118.263683, 300)
    );

    /**
     * Default constructor.
     */
    public PlayerService() {
        this.playerDao = new DynamoDbPlayerDao();
    }

    /**
     * Constructor with dependency injection for testability.
     *
     * @param playerDao The player DAO
     */
    public PlayerService(PlayerDao playerDao) {
        this.playerDao = playerDao;
    }

    /**
     * Syncs a federated user's profile data to the Player table.
     * If the player already exists (based on the Cognito user ID), updates the profile.
     * If the player doesn't exist, creates a new player record.
     *
     * @param userId The Cognito user ID (sub claim)
     * @param email The user's email address
     * @param name The user's display name
     * @param provider The identity provider name (e.g., "Google", "Facebook", "Apple")
     * @param userAttributes Additional user attributes from Cognito
     * @return The synced Player object
     * @throws PlayerPersistenceException if there's an error persisting the player
     */
    public Player syncFederatedUserToPlayer(String userId, String email, String name, String provider, 
                                           Map<String, String> userAttributes) 
            throws PlayerPersistenceException {
        
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be empty");
        }
        
        Player player;
        try {
            // Try to find the player by ID (which is the Cognito user ID)
            player = playerDao.findPlayerById(userId);
            if (player != null) {
                logger.info("Found existing player with ID: {}", userId);
                // Update existing player with latest profile data
                updatePlayerProfile(player, email, name, provider, userAttributes);
            } else {
                // Player doesn't exist, create a new one
                logger.info("Creating new player for federated user ID: {}", userId);
                player = createPlayerFromFederatedUser(userId, email, name, provider, userAttributes);
            }
            
            // Save updated or new player
            playerDao.savePlayer(player);
            logger.info("Successfully synced federated user profile for user: {}, provider: {}", userId, provider);
            
            return player;
        } catch (Exception e) {
            logger.error("Error syncing federated user to player: {}", e.getMessage(), e);
            throw new PlayerPersistenceException("Failed to sync federated user profile: " + e.getMessage(), e);
        }
    }
    
    /**
     * Updates an existing player's profile with data from a federated login.
     */
    private void updatePlayerProfile(Player player, String email, String name, String provider, 
                                     Map<String, String> userAttributes) {
        // Only update email if it changed and isn't empty
        if (email != null && !email.trim().isEmpty() && !email.equals(player.getEmail())) {
            player.setEmail(email);
        }
        
        // Only update name if it changed and isn't empty
        if (name != null && !name.trim().isEmpty() && !name.equals(player.getPlayerName())) {
            player.setPlayerName(name);
        }
        
        // Set or update social provider info if needed
        // We could store this in an attribute or as a note in the player record
        
        // If the player has no status yet, default to PENDING
        if (player.getStatus() == null || player.getStatus().isEmpty()) {
            player.setStatus("PENDING");
        }
        
        // Set player as active
        player.setActive(true);
    }
    
    /**
     * Creates a new Player object for a federated user.
     */
    private Player createPlayerFromFederatedUser(String userId, String email, String name, String provider, 
                                                Map<String, String> userAttributes) {
        Player player = new Player();
        player.setPlayerID(userId); // Use the Cognito user ID as the player ID
        
        // Set required fields
        player.setEmail(email != null ? email : "");
        player.setPlayerName(name != null ? name : "Player " + userId.substring(0, 8));
        
        // Set default values
        player.setStatus("PENDING"); // New players start as pending until joined to a game
        player.setKillCount(0);
        player.setActive(true);
        
        // Generate a default secret if needed
        player.setSecret(generateSecret());
        
        return player;
    }
    
    /**
     * Generates a random secret for a player.
     */
    private String generateSecret() {
        // Simple random word generation - in production, consider a better word generation approach
        String[] adjectives = {"quick", "lazy", "clever", "brave", "silent", "dark", "bright", "hidden"};
        String[] nouns = {"fox", "dog", "shadow", "assassin", "hunter", "ghost", "warrior", "ninja"};
        
        String adjective = adjectives[(int) (Math.random() * adjectives.length)];
        String noun = nouns[(int) (Math.random() * nouns.length)];
        String randomDigits = String.format("%03d", (int) (Math.random() * 1000));
        
        return adjective + "-" + noun + "-" + randomDigits;
    }

    /**
     * Retrieves the location visibility settings for a player.
     *
     * @param playerId The ID of the player.
     * @return An Optional containing the player's LocationVisibility, or empty if player not found.
     * @throws PlayerPersistenceException if there is an issue accessing the database.
     */
    public Optional<Player.LocationVisibility> getLocationVisibilitySettings(String playerId) throws PlayerPersistenceException {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Player ID cannot be null or empty.");
        }
        try {
            Player player = playerDao.findPlayerById(playerId);
            if (player == null) {
                logger.warn("Player not found with ID: {}", playerId);
                return Optional.empty();
            }
            logger.info("Retrieved location visibility settings for player {}: {}", playerId, player.getLocationVisibility());
            return Optional.ofNullable(player.getLocationVisibility()); // Default is set in Player model
        } catch (Exception e) {
            logger.error("Error retrieving location visibility settings for player {}: {}", playerId, e.getMessage(), e);
            throw new PlayerPersistenceException("Failed to retrieve location visibility settings: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the location visibility settings for a player.
     *
     * @param playerId The ID of the player.
     * @param newVisibility The new location visibility setting.
     * @return The updated Player object.
     * @throws PlayerNotFoundException if the player with the given ID is not found.
     * @throws PlayerPersistenceException if there is an issue persisting the changes.
     * @throws IllegalArgumentException if playerId or newVisibility is null.
     */
    public Player updateLocationVisibilitySettings(String playerId, Player.LocationVisibility newVisibility) 
            throws PlayerNotFoundException, PlayerPersistenceException {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Player ID cannot be null or empty.");
        }
        if (newVisibility == null) {
            throw new IllegalArgumentException("Location visibility setting cannot be null.");
        }

        Player player = findPlayerByIdOrElseThrow(playerId);
        Player.LocationVisibility oldVisibility = player.getLocationVisibility();
        player.setLocationVisibility(newVisibility);
        try {
            playerDao.savePlayer(player);
            logger.info("AUDIT - PlayerID: {}, Action: UpdateLocationVisibility, OldValue: {}, NewValue: {}", 
                        playerId, oldVisibility, newVisibility);
            return player;
        } catch (PlayerPersistenceException e) {
            logger.error("Failed to update location visibility for player {}: {}", playerId, e.getMessage());
            throw e;
        }
    }

    /**
     * Pauses location sharing for a player.
     *
     * @param playerId The ID of the player.
     * @return The updated Player object.
     * @throws PlayerNotFoundException if the player is not found.
     * @throws PlayerPersistenceException if there's an issue saving the player.
     * @throws ValidationException if location sharing is currently on cooldown.
     */
    public Player pauseLocationSharing(String playerId) 
            throws PlayerNotFoundException, PlayerPersistenceException, ValidationException {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Player ID cannot be null or empty.");
        }
        try {
            Player player = playerDao.findPlayerById(playerId);
            if (player == null) {
                logger.warn("Attempted to pause location sharing for non-existent player ID: {}", playerId);
                throw new PlayerNotFoundException("Player not found with ID: " + playerId);
            }

            if (player.isLocationSharingPaused()) {
                logger.warn("Player {} location sharing is already paused.", playerId);
                return player;
            }

            if (player.getLocationPauseCooldownUntil() != null) {
                ZonedDateTime cooldownEnd = ZonedDateTime.parse(player.getLocationPauseCooldownUntil());
                if (ZonedDateTime.now(ZoneOffset.UTC).isBefore(cooldownEnd)) {
                    logger.warn("Player {} tried to pause location sharing before cooldown ended ({}).", 
                                playerId, player.getLocationPauseCooldownUntil());
                    throw new ValidationException("Location sharing pause is on cooldown until " + player.getLocationPauseCooldownUntil());
                }
            }

            boolean oldPauseState = player.isLocationSharingPaused();
            player.setLocationSharingPaused(true);
            ZonedDateTime newCooldownUntilZoned = ZonedDateTime.now(ZoneOffset.UTC).plus(LOCATION_PAUSE_COOLDOWN);
            player.setLocationPauseCooldownUntilFromDateTime(newCooldownUntilZoned.toLocalDateTime());

            try {
                playerDao.savePlayer(player);
                logger.info("AUDIT - PlayerID: {}, Action: PauseLocationSharing, OldValue: {}, NewValue: {}, CooldownUntil: {}", 
                            playerId, oldPauseState, player.isLocationSharingPaused(), player.getLocationPauseCooldownUntil());
                return player;
            } catch (PlayerPersistenceException e) {
                logger.error("Failed to pause location sharing for player {}: {}", playerId, e.getMessage());
                throw e;
            }
        } catch (PlayerNotFoundException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error pausing location sharing for player {}: {}", playerId, e.getMessage(), e);
            throw new PlayerPersistenceException("Failed to pause location sharing: " + e.getMessage(), e);
        }
    }

    /**
     * Resumes location sharing for a player.
     *
     * @param playerId The ID of the player.
     * @return The updated Player object.
     * @throws PlayerNotFoundException if the player is not found.
     * @throws PlayerPersistenceException if there's an issue saving the player.
     */
    public Player resumeLocationSharing(String playerId) 
            throws PlayerNotFoundException, PlayerPersistenceException {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Player ID cannot be null or empty.");
        }
        try {
            Player player = playerDao.findPlayerById(playerId);
            if (player == null) {
                logger.warn("Attempted to resume location sharing for non-existent player ID: {}", playerId);
                throw new PlayerNotFoundException("Player not found with ID: " + playerId);
            }

            if (!player.isLocationSharingPaused()) {
                logger.warn("Player {} location sharing is not currently paused.", playerId);
                // Potentially throw an exception or return a specific status if not paused and trying to resume
                // For now, just return the player state
                return player;
            }
            boolean oldPauseState = player.isLocationSharingPaused();
            player.setLocationSharingPaused(false);
            // player.setLocationPauseCooldownUntil(null); // Cooldown is for re-pausing, not for resuming.

            try {
                playerDao.savePlayer(player);
                logger.info("AUDIT - PlayerID: {}, Action: ResumeLocationSharing, OldValue: {}, NewValue: {}", 
                            playerId, oldPauseState, player.isLocationSharingPaused());
                return player;
            } catch (PlayerPersistenceException e) {
                logger.error("Failed to resume location sharing for player {}: {}", playerId, e.getMessage());
                throw e;
            }
        } catch (PlayerNotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error resuming location sharing for player {}: {}", playerId, e.getMessage(), e);
            throw new PlayerPersistenceException("Failed to resume location sharing: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the location precision settings for a player.
     *
     * @param playerId The ID of the player.
     * @return An Optional containing the player's LocationPrecision, or empty if player not found.
     * @throws PlayerPersistenceException if there is an issue accessing the database.
     */
    public Optional<Player.LocationPrecision> getLocationPrecisionSettings(String playerId) throws PlayerPersistenceException {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Player ID cannot be null or empty.");
        }
        try {
            Player player = playerDao.findPlayerById(playerId);
            if (player == null) {
                logger.warn("Player not found with ID for precision settings: {}", playerId);
                return Optional.empty();
            }
            logger.info("Retrieved location precision for player {}: {}", playerId, player.getLocationPrecision());
            return Optional.ofNullable(player.getLocationPrecision()); // Default is set in Player model
        } catch (Exception e) {
            logger.error("Error retrieving location precision for player {}: {}", playerId, e.getMessage(), e);
            throw new PlayerPersistenceException("Failed to retrieve location precision settings: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the location precision settings for a player.
     *
     * @param playerId The ID of the player.
     * @param newPrecision The new location precision setting.
     * @return The updated Player object.
     * @throws PlayerNotFoundException if the player with the given ID is not found.
     * @throws PlayerPersistenceException if there is an issue persisting the changes.
     * @throws IllegalArgumentException if playerId or newPrecision is null.
     */
    public Player updateLocationPrecisionSettings(String playerId, Player.LocationPrecision newPrecision) 
            throws PlayerNotFoundException, PlayerPersistenceException {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Player ID cannot be null or empty.");
        }
        if (newPrecision == null) {
            throw new IllegalArgumentException("Location precision setting cannot be null.");
        }

        Player player = findPlayerByIdOrElseThrow(playerId);
        Player.LocationPrecision oldPrecision = player.getLocationPrecision();
        player.setLocationPrecision(newPrecision);
        try {
            playerDao.savePlayer(player);
            logger.info("AUDIT - PlayerID: {}, Action: UpdateLocationPrecision, OldValue: {}, NewValue: {}", 
                        playerId, oldPrecision, newPrecision);
            return player;
        } catch (PlayerPersistenceException e) {
            logger.error("Failed to update location precision for player {}: {}", playerId, e.getMessage());
            throw e;
        }
    }

    /**
     * Gets the effective (potentially fuzzed) location for a player.
     * This method should be called by any service needing to display or use a player's location.
     * It respects the player's visibility, pause, and precision settings.
     *
     * @param playerId The ID of the player whose location is requested.
     * @param requestingPlayerId The ID of the player making the request (can be null for system requests).
     * @return An Optional containing the Player.LocationData (a new inner class/record for fuzzed location), 
     *         or empty if location should not be shared or player not found.
     */
    public Optional<PlayerLocationData> getEffectivePlayerLocation(String playerId, String requestingPlayerId) {
        // This is a conceptual outline. Implementation details will be more complex.
        try {
            Player player = playerDao.findPlayerById(playerId);
            if (player == null || player.getLatitude() == null || player.getLongitude() == null) {
                return Optional.empty(); // No player or no base location to fuzz
            }

            // 1. Check if location sharing is paused (manual or system)
            if (player.isLocationSharingPaused() || player.isSystemLocationPauseActive()) {
                logger.debug("Location sharing is paused for player {} (manual: {}, system: {})", 
                             playerId, player.isLocationSharingPaused(), player.isSystemLocationPauseActive());
                return Optional.empty();
            }

            // 2. Check visibility settings (simplified for this example)
            // In a real scenario, this would involve checking game state, target/hunter relationship, friends, etc.
            // For now, if HIDDEN, don't share. VISIBLE_TO_HUNTER_TARGET would need more context.
            if (player.getLocationVisibility() == Player.LocationVisibility.HIDDEN) {
                 logger.debug("Location visibility is HIDDEN for player {}", playerId);
                return Optional.empty();
            }
            // TODO: Implement more sophisticated visibility checks based on requestingPlayerId and game context.

            // 3. Apply fuzzing based on precision settings
            double lat = player.getLatitude();
            double lon = player.getLongitude();
            Player.LocationPrecision precision = player.getLocationPrecision() != null ? player.getLocationPrecision() : Player.LocationPrecision.PRECISE;

            switch (precision) {
                case REDUCED_100M:
                    // Approximate by truncating decimal places (0.001 degree ~ 111m at equator)
                    lat = Math.floor(lat * 1000) / 1000;
                    lon = Math.floor(lon * 1000) / 1000;
                    break;
                case REDUCED_500M:
                    // Approximate by truncating decimal places (0.005 degree ~ 555m at equator)
                    lat = Math.floor(lat * 200) / 200; // Roughly 1/200th of a degree
                    lon = Math.floor(lon * 200) / 200;
                    break;
                case NOISE_ADDED_LOW:
                    // Add small random offset (e.g., +/- 0.0001 to 0.0005 degrees, ~10m to ~50m)
                    lat += (new SecureRandom().nextDouble() - 0.5) * 0.001; // Max offset ~0.0005
                    lon += (new SecureRandom().nextDouble() - 0.5) * 0.001;
                    break;
                case NOISE_ADDED_MED:
                    // Add medium random offset (e.g., +/- 0.0005 to 0.0025 degrees, ~50m to ~250m)
                    lat += (new SecureRandom().nextDouble() - 0.5) * 0.005; // Max offset ~0.0025
                    lon += (new SecureRandom().nextDouble() - 0.5) * 0.005;
                    break;
                case PRECISE:
                default:
                    // No changes
                    break;
            }
            logger.debug("Effective location for player {}: lat={}, lon={}, precision={}", playerId, lat, lon, precision);
            return Optional.of(new PlayerLocationData(lat, lon, player.getLocationTimestamp(), player.getLocationAccuracy(), precision));

        } catch (Exception e) {
            logger.error("Error getting effective player location for {}: {}", playerId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    // Inner record/class to return fuzzed location data along with applied precision
    public record PlayerLocationData(Double latitude, Double longitude, String timestamp, Double accuracy, Player.LocationPrecision appliedPrecision) {}

    /**
     * Checks if the player is inside any sensitive area and updates their system-initiated pause status.
     *
     * @param player The player whose location is being checked.
     * @return true if the player's systemLocationPauseActive status was changed, false otherwise.
     */
    public boolean checkAndApplySensitiveAreaRules(Player player) {
        if (player == null || player.getLatitude() == null || player.getLongitude() == null) {
            return false; // Cannot check without location
        }

        boolean currentlyInSensitiveArea = false;
        for (SensitiveArea area : SENSITIVE_AREAS) {
            if (isLocationInsideArea(player.getLatitude(), player.getLongitude(), area)) {
                currentlyInSensitiveArea = true;
                break;
            }
        }

        boolean statusChanged = false;
        if (player.isSystemLocationPauseActive() != currentlyInSensitiveArea) {
            player.setSystemLocationPauseActive(currentlyInSensitiveArea);
            // No need to savePlayer here, this method is a helper.
            // The calling method (e.g., updateLocation) should handle saving.
            statusChanged = true;
            logger.info("Player {} system location pause status changed to {} due to sensitive area rules.", 
                        player.getPlayerID(), currentlyInSensitiveArea);
        }
        return statusChanged;
    }

    /**
     * Helper method to calculate distance between two lat/lon points in meters (Haversine formula).
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters
        return distance;
    }

    private boolean isLocationInsideArea(double playerLat, double playerLon, SensitiveArea area) {
        double distance = calculateDistance(playerLat, playerLon, area.latitude(), area.longitude());
        return distance <= area.radiusMeters();
    }

    private Player findPlayerByIdOrElseThrow(String playerId) throws PlayerNotFoundException {
        Player player = playerDao.findPlayerById(playerId);
        if (player == null) {
            throw new PlayerNotFoundException("Player not found with ID: " + playerId);
        }
        return player;
    }
} 