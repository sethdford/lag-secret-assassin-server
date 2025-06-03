package com.assassin.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.DynamoDbSafeZoneDao;
import com.assassin.dao.SafeZoneDao;
import com.assassin.exception.PersistenceException;
import com.assassin.exception.SafeZoneNotFoundException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Coordinate;
import com.assassin.model.GameZoneState;
import com.assassin.model.PrivateSafeZone;
import com.assassin.model.PublicSafeZone;
import com.assassin.model.RelocatableSafeZone;
import com.assassin.model.SafeZone;
import com.assassin.model.TimedSafeZone;
import com.assassin.util.GeoUtils;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

/**
 * Service layer for handling Safe Zone logic.
 */
public class SafeZoneService {

    private static final Logger logger = LoggerFactory.getLogger(SafeZoneService.class);
    private final SafeZoneDao safeZoneDao;
    private final ShrinkingZoneService shrinkingZoneService;
    // Add other DAOs as needed (e.g., GameDao to validate gameId)

    // Default constructor using the default DAO constructor
    public SafeZoneService() {
        this(new DynamoDbSafeZoneDao(), null);
    }

    // Constructor for dependency injection with DAO
    public SafeZoneService(SafeZoneDao safeZoneDao) {
        this(safeZoneDao, null);
    }
    
    // Constructor for dependency injection with Enhanced Client
    public SafeZoneService(DynamoDbEnhancedClient enhancedClient) {
        this(new DynamoDbSafeZoneDao(enhancedClient), null);
    }
    
    // Constructor for dependency injection with both DAO and ShrinkingZoneService
    public SafeZoneService(SafeZoneDao safeZoneDao, ShrinkingZoneService shrinkingZoneService) {
        this.safeZoneDao = safeZoneDao;
        this.shrinkingZoneService = shrinkingZoneService;
    }

    /**
     * Creates a new safe zone using the provided SafeZone object.
     * This method is flexible but requires the caller to construct the SafeZone object correctly.
     * Prefer using type-specific creation methods for clarity and validation.
     *
     * @param safeZone The SafeZone object to create.
     * @return The created SafeZone object.
     * @throws ValidationException if input is invalid.
     * @throws PersistenceException if saving fails.
     */
    public SafeZone createGeneralSafeZone(SafeZone safeZone) throws ValidationException, PersistenceException {
        if (safeZone == null) {
            throw new ValidationException("SafeZone object cannot be null.");
        }

        // Ensure essential IDs and timestamps are set if not already
        if (safeZone.getSafeZoneId() == null || safeZone.getSafeZoneId().isEmpty()) {
            safeZone.setSafeZoneId(UUID.randomUUID().toString());
        }
        if (safeZone.getCreatedAt() == null || safeZone.getCreatedAt().isEmpty()) {
            safeZone.setCreatedAt(Instant.now().toString());
        }
        safeZone.setLastModifiedAt(safeZone.getCreatedAt()); // lastModified is same as created at creation

        // Validate the constructed safe zone
        try {
            safeZone.validate(); // Use the validate method from the SafeZone model
        } catch (IllegalStateException e) {
            throw new ValidationException("Invalid SafeZone configuration: " + e.getMessage(), e);
        }

        logger.info("Creating safe zone with ID: {} for game: {} of type: {}", 
                    safeZone.getSafeZoneId(), safeZone.getGameId(), safeZone.getType());
        safeZoneDao.saveSafeZone(safeZone);
        return safeZone;
    }

    /**
     * Creates a new safe zone.
     *
     * @param safeZone The SafeZone object to create (safeZoneId can be null, will be generated).
     * @return The created SafeZone object with its generated ID.
     * @throws ValidationException if input is invalid.
     * @throws PersistenceException if saving fails.
     */
    public SafeZone createSafeZone(SafeZone safeZone) throws ValidationException, PersistenceException {
        if (safeZone == null) {
            throw new ValidationException("SafeZone object cannot be null.");
        }
        // Basic validation (more can be added)
        if (safeZone.getGameId() == null || safeZone.getGameId().isEmpty()) {
            throw new ValidationException("GameID is required for a safe zone.");
        }
        if (safeZone.getLatitude() == null || safeZone.getLongitude() == null) {
            throw new ValidationException("Latitude and Longitude are required for a safe zone.");
        }
        if (safeZone.getRadiusMeters() == null || safeZone.getRadiusMeters() <= 0) {
            throw new ValidationException("Valid radius (in meters) is required.");
        }
        if (safeZone.getType() == null) {
            throw new ValidationException("Safe zone type is required.");
        }

        // Generate ID if not provided
        if (safeZone.getSafeZoneId() == null || safeZone.getSafeZoneId().isEmpty()) {
            safeZone.setSafeZoneId(UUID.randomUUID().toString());
        }

        // Set creation timestamp if not provided
        if (safeZone.getCreatedAt() == null || safeZone.getCreatedAt().isEmpty()) {
            safeZone.setCreatedAt(Instant.now().toString());
        }

        // Type-specific validation
        if (safeZone instanceof TimedSafeZone) {
            validateTimedSafeZone((TimedSafeZone) safeZone);
        } else if (safeZone instanceof RelocatableSafeZone) {
            validateRelocatableSafeZone((RelocatableSafeZone) safeZone);
        }

        logger.info("Creating safe zone with ID: {} for game: {}", safeZone.getSafeZoneId(), safeZone.getGameId());
        safeZoneDao.saveSafeZone(safeZone);
        return safeZone;
    }

    /**
     * Retrieves a safe zone by its ID.
     *
     * @param safeZoneId The ID of the safe zone.
     * @return An Optional containing the SafeZone if found.
     */
    public Optional<SafeZone> getSafeZone(String safeZoneId) {
        if (safeZoneId == null || safeZoneId.isEmpty()) {
            return Optional.empty();
        }
        logger.debug("Getting safe zone by ID: {}", safeZoneId);
        return safeZoneDao.getSafeZoneById(safeZoneId);
    }

    /**
     * Retrieves all safe zones for a specific game.
     *
     * @param gameId The ID of the game.
     * @return A list of safe zones for the game.
     */
    public List<SafeZone> getSafeZonesForGame(String gameId) {
        if (gameId == null || gameId.isEmpty()) {
            return List.of(); // Return empty list for invalid gameId
        }
        logger.debug("Getting safe zones for game ID: {}", gameId);
        return safeZoneDao.getSafeZonesByGameId(gameId);
    }

    /**
     * Retrieves all safe zones of a specific type for a given game.
     *
     * @param gameId The ID of the game.
     * @param type The type of safe zone to retrieve.
     * @return A list of SafeZones matching the type for the given game.
     */
    public List<SafeZone> getSafeZonesByType(String gameId, SafeZone.SafeZoneType type) {
        if (gameId == null || gameId.isEmpty() || type == null) {
            logger.warn("Attempted to get safe zones by type with null or empty gameId/type.");
            return List.of();
        }
        logger.debug("Getting safe zones for game ID: {} of type: {}", gameId, type);
        return safeZoneDao.getSafeZonesByType(gameId, type);
    }

    /**
     * Retrieves all safe zones owned by a specific player in a given game.
     *
     * @param gameId The ID of the game.
     * @param ownerPlayerId The ID of the player (owner).
     * @return A list of safe zones owned by the player in that game.
     */
    public List<SafeZone> getSafeZonesByOwner(String gameId, String ownerPlayerId) {
        if (gameId == null || gameId.isEmpty() || ownerPlayerId == null || ownerPlayerId.isEmpty()) {
            logger.warn("Attempted to get safe zones by owner with null or empty gameId/ownerPlayerId.");
            return List.of();
        }
        logger.debug("Getting safe zones for game ID: {} by owner: {}", gameId, ownerPlayerId);
        return safeZoneDao.getSafeZonesByOwner(gameId, ownerPlayerId);
    }

    /**
     * Deletes a safe zone by its ID.
     *
     * @param safeZoneId The ID of the safe zone to delete.
     * @throws PersistenceException if deletion fails.
     */
    public void deleteSafeZone(String safeZoneId) throws PersistenceException {
        if (safeZoneId == null || safeZoneId.isEmpty()) {
             logger.warn("Attempted to delete safe zone with null or empty ID.");
            return; // Or throw ValidationException
        }
        // Consider adding checks: does the zone exist? does the caller have permission?
        logger.info("Deleting safe zone with ID: {}", safeZoneId);
        safeZoneDao.deleteSafeZone(safeZoneId);
    }

    /**
     * Updates an existing safe zone.
     *
     * @param safeZoneId The ID of the safe zone to update.
     * @param updates The SafeZone object containing updates.
     * @return The updated SafeZone.
     * @throws SafeZoneNotFoundException if the safe zone doesn't exist.
     * @throws ValidationException if updates are invalid.
     * @throws PersistenceException if update fails.
     */
    public SafeZone updateSafeZone(String safeZoneId, SafeZone updates) throws SafeZoneNotFoundException, ValidationException, PersistenceException {
        logger.info("Updating safe zone with ID: {}", safeZoneId);

        SafeZone existingSafeZone = safeZoneDao.getSafeZoneById(safeZoneId)
                .orElseThrow(() -> new SafeZoneNotFoundException("Safe zone not found: " + safeZoneId));

        // Validate that we're not trying to change the zone type
        if (updates.getType() != null && !updates.getType().equals(existingSafeZone.getType())) {
            throw new ValidationException("Cannot change the type of an existing safe zone");
        }
        // Also ensure gameId is not being changed for an existing zone
        if (updates.getGameId() != null && !updates.getGameId().equals(existingSafeZone.getGameId())) {
            throw new ValidationException("Cannot change the GameID of an existing safe zone");
        }

        boolean updated = false;

        // Update standard fields
        if (updates.getName() != null && !updates.getName().equals(existingSafeZone.getName())) {
            existingSafeZone.setName(updates.getName());
            updated = true;
        }
        if (updates.getDescription() != null && !updates.getDescription().equals(existingSafeZone.getDescription())) {
            existingSafeZone.setDescription(updates.getDescription());
            updated = true;
        }
        // Update location if provided and different
        if (updates.getLatitude() != null && updates.getLongitude() != null && 
            (!updates.getLatitude().equals(existingSafeZone.getLatitude()) || 
             !updates.getLongitude().equals(existingSafeZone.getLongitude()))) 
        {
            existingSafeZone.setLatitude(updates.getLatitude());
            existingSafeZone.setLongitude(updates.getLongitude());
            updated = true;
        }
        if (updates.getRadiusMeters() != null && !updates.getRadiusMeters().equals(existingSafeZone.getRadiusMeters())) {
            existingSafeZone.setRadiusMeters(updates.getRadiusMeters());
            updated = true;
        }
        if (updates.getIsActive() != null && !updates.getIsActive().equals(existingSafeZone.getIsActive())) {
            existingSafeZone.setIsActive(updates.getIsActive());
            updated = true;
        }
        
        // Update type-specific fields
        if (existingSafeZone instanceof PrivateSafeZone && updates instanceof PrivateSafeZone) {
            updated = updatePrivateSafeZone((PrivateSafeZone) existingSafeZone, (PrivateSafeZone) updates) || updated;
        } else if (existingSafeZone instanceof TimedSafeZone && updates instanceof TimedSafeZone) {
            updated = updateTimedSafeZone((TimedSafeZone) existingSafeZone, (TimedSafeZone) updates) || updated;
        } else if (existingSafeZone instanceof RelocatableSafeZone && updates instanceof RelocatableSafeZone) {
            updated = updateRelocatableSafeZone((RelocatableSafeZone) existingSafeZone, (RelocatableSafeZone) updates) || updated;
        }

        if (updated) {
            existingSafeZone.setLastModifiedAt(Instant.now().toString());
            
            // Validate before saving
            try {
                existingSafeZone.validate();
            } catch (IllegalStateException e) {
                throw new ValidationException("Invalid SafeZone state after update: " + e.getMessage(), e);
            }

            safeZoneDao.saveSafeZone(existingSafeZone);
            logger.info("Successfully updated safe zone: {}", safeZoneId);
        } else {
            logger.info("No changes detected for safe zone: {}", safeZoneId);
        }
        return existingSafeZone;
    }

    /**
     * Relocates a relocatable safe zone to a new position.
     * 
     * @param safeZoneId ID of the relocatable safe zone
     * @param newLatitude New latitude for the safe zone
     * @param newLongitude New longitude for the safe zone
     * @param playerId ID of the player attempting the relocation (must be owner/creator)
     * @return The updated safe zone if successful
     * @throws SafeZoneNotFoundException if the zone doesn't exist
     * @throws ValidationException if the zone is not relocatable, not owned by player, or on cooldown
     * @throws PersistenceException if saving fails
     */
    public SafeZone relocateZone(String safeZoneId, String playerId, Double newLatitude, Double newLongitude)
            throws SafeZoneNotFoundException, ValidationException, PersistenceException {
        
        SafeZone safeZone = safeZoneDao.getSafeZoneById(safeZoneId)
                .orElseThrow(() -> new SafeZoneNotFoundException("Safe zone not found: " + safeZoneId));
        
        if (safeZone.getType() != SafeZone.SafeZoneType.RELOCATABLE) {
            throw new ValidationException("Safe zone is not relocatable: " + safeZoneId);
        }
        
        if (!playerId.equals(safeZone.getCreatedBy())) {
            throw new ValidationException("Player " + playerId + " is not authorized to relocate safe zone " + safeZoneId);
        }

        if (newLatitude == null || newLongitude == null) {
            throw new ValidationException("New latitude and longitude are required for relocation.");
        }

        // Basic cooldown logic (example: 1 hour cooldown, can be configured elsewhere)
        // This is a simplified example. A more robust solution might involve game settings.
        final long RELOCATION_COOLDOWN_MS = 60 * 60 * 1000; // 1 hour
        if (safeZone.getLastRelocationTime() != null) {
            try {
                Instant lastRelo = Instant.parse(safeZone.getLastRelocationTime());
                if (Instant.now().isBefore(lastRelo.plusMillis(RELOCATION_COOLDOWN_MS))) {
                    long remainingCooldown = RELOCATION_COOLDOWN_MS - (Instant.now().toEpochMilli() - lastRelo.toEpochMilli());
                    throw new ValidationException("Safe zone relocation is on cooldown. Time remaining: " + formatCooldownTime(remainingCooldown / 1000));
        }
            } catch (Exception e) {
                logger.warn("Could not parse lastRelocationTime for zone {}: {}", safeZoneId, safeZone.getLastRelocationTime());
                // Decide if this should block relocation or proceed with caution
            }
        }
        
        // Perform the relocation via DAO
        String newLastRelocationTime = Instant.now().toString();
        int newRelocationCount = (safeZone.getRelocationCount() == null ? 0 : safeZone.getRelocationCount()) + 1;

        safeZoneDao.updateRelocatableSafeZone(safeZoneId, newLatitude, newLongitude, newLastRelocationTime, newRelocationCount);
        
        // Fetch the updated zone to return it
        return safeZoneDao.getSafeZoneById(safeZoneId)
                .orElseThrow(() -> new PersistenceException("Failed to retrieve safe zone after relocation update: " + safeZoneId, null));
    }

    /**
     * Creates a new public safe zone.
     * 
     * @param gameId The game ID
     * @param name The name of the safe zone
     * @param description The description
     * @param latitude The latitude of the center
     * @param longitude The longitude of the center
     * @param radiusMeters The radius in meters
     * @param createdBy The ID of the player creating the zone
     * @return The created public safe zone
     * @throws ValidationException if validation fails
     * @throws PersistenceException if saving fails
     */
    public SafeZone createPublicSafeZone(String gameId, String name, String description,
                                              Double latitude, Double longitude, Double radiusMeters,
                                              String createdBy) throws ValidationException, PersistenceException {
        SafeZone zone = SafeZone.createPublicZone(gameId, name, description, latitude, longitude, radiusMeters, createdBy);
        return createGeneralSafeZone(zone); // Use the general method for saving and common logic
    }

    /**
     * Creates a new private safe zone.
     * 
     * @param gameId The game ID
     * @param name The name of the safe zone
     * @param description The description
     * @param latitude The latitude of the center
     * @param longitude The longitude of the center
     * @param radiusMeters The radius in meters
     * @param createdBy The ID of the player creating the zone
     * @param authorizedPlayerIds Set of player IDs authorized to use this safe zone
     * @return The created private safe zone
     * @throws ValidationException if validation fails
     * @throws PersistenceException if saving fails
     */
    public SafeZone createPrivateSafeZone(String gameId, String name, String description,
                                              Double latitude, Double longitude, Double radiusMeters,
                                          String createdByPlayerId, Set<String> authorizedIds) 
            throws ValidationException, PersistenceException {
        SafeZone zone = SafeZone.createPrivateZone(gameId, name, description, latitude, longitude, radiusMeters, createdByPlayerId, authorizedIds);
        return createGeneralSafeZone(zone);
    }

    /**
     * Creates a new timed safe zone.
     * 
     * @param gameId The game ID
     * @param name The name of the safe zone
     * @param description The description
     * @param latitude The latitude of the center
     * @param longitude The longitude of the center
     * @param radiusMeters The radius in meters
     * @param createdBy The ID of the player creating the zone
     * @param startTime ISO-8601 timestamp when the zone starts being active
     * @param endTime ISO-8601 timestamp when the zone stops being active
     * @return The created timed safe zone
     * @throws ValidationException if validation fails
     * @throws PersistenceException if saving fails
     */
    public SafeZone createTimedSafeZone(String gameId, String name, String description,
                                            Double latitude, Double longitude, Double radiusMeters,
                                            String createdBy, String startTime, String endTime) 
            throws ValidationException, PersistenceException {
        SafeZone zone = SafeZone.createTimedZone(gameId, name, description, latitude, longitude, radiusMeters, createdBy, startTime, endTime);
        return createGeneralSafeZone(zone);
    }

    /**
     * Creates a new relocatable safe zone.
     * 
     * @param gameId The game ID
     * @param name The name of the safe zone
     * @param description The description
     * @param latitude The latitude of the center
     * @param longitude The longitude of the center
     * @param radiusMeters The radius in meters
     * @param createdBy The ID of the player creating the zone
     * @return The created relocatable safe zone
     * @throws ValidationException if validation fails
     * @throws PersistenceException if saving fails
     */
    public SafeZone createRelocatableSafeZone(String gameId, String name, String description,
                                              Double latitude, Double longitude, Double radiusMeters,
                                              String createdBy) 
            throws ValidationException, PersistenceException {
        // CooldownSeconds parameter removed as it was part of a RelocatableSafeZone class not being used.
        // Relocation logic will handle cooldowns based on relocationCount and lastRelocationTime.
        SafeZone zone = SafeZone.createRelocatableZone(gameId, name, description, latitude, longitude, radiusMeters, createdBy);
        return createGeneralSafeZone(zone);
    }

    // Note: The isLocationInSafeZone method has been removed to avoid duplication.
    // Use MapConfigurationService.isLocationInSafeZone() instead, which provides
    // more comprehensive checking including game boundary validation and timed zone expiration.
    
    // Helper methods for validation and updates
    
    /**
     * Validates a timed safe zone.
     * 
     * @param safeZone The timed safe zone to validate
     * @throws ValidationException if validation fails
     */
    private void validateTimedSafeZone(TimedSafeZone safeZone) throws ValidationException {
        if (safeZone.getStartTime() == null || safeZone.getStartTime().isEmpty()) {
            throw new ValidationException("Start time is required for timed safe zones");
        }
        
        if (safeZone.getEndTime() == null || safeZone.getEndTime().isEmpty()) {
            throw new ValidationException("End time is required for timed safe zones");
        }
        
        // Validate time format and logic
        try {
            Instant startTime = Instant.parse(safeZone.getStartTime());
            Instant endTime = Instant.parse(safeZone.getEndTime());
            
            if (endTime.isBefore(startTime)) {
                throw new ValidationException("End time cannot be before start time");
            }
        } catch (Exception e) {
            if (e instanceof ValidationException) {
                throw e;
            }
            throw new ValidationException("Invalid timestamp format. Use ISO-8601 format.");
        }
    }
    
    /**
     * Validates a relocatable safe zone.
     * 
     * @param safeZone The relocatable safe zone to validate
     * @throws ValidationException if validation fails
     */
    private void validateRelocatableSafeZone(RelocatableSafeZone safeZone) throws ValidationException {
        if (safeZone.getCooldownSeconds() != null && safeZone.getCooldownSeconds() < 0) {
            throw new ValidationException("Cooldown period cannot be negative");
        }
    }
    
    /**
     * Updates private safe zone specific attributes.
     * 
     * @param existingSafeZone The existing safe zone
     * @param updates The updates to apply
     * @return true if any changes were made, false otherwise
     */
    private boolean updatePrivateSafeZone(PrivateSafeZone existingSafeZone, PrivateSafeZone updates) {
        boolean updated = false;
        
        if (updates.getAuthorizedPlayerIds() != null && 
            !updates.getAuthorizedPlayerIds().equals(existingSafeZone.getAuthorizedPlayerIds())) {
            existingSafeZone.setAuthorizedPlayerIds(updates.getAuthorizedPlayerIds());
            updated = true;
        }
        
        return updated;
    }
    
    /**
     * Updates timed safe zone specific attributes.
     * 
     * @param existingSafeZone The existing safe zone
     * @param updates The updates to apply
     * @return true if any changes were made, false otherwise
     * @throws ValidationException if the updates contain invalid values
     */
    private boolean updateTimedSafeZone(TimedSafeZone existingSafeZone, TimedSafeZone updates) throws ValidationException {
        boolean updated = false;
        
        if (updates.getStartTime() != null && !updates.getStartTime().equals(existingSafeZone.getStartTime())) {
            existingSafeZone.setStartTime(updates.getStartTime());
            updated = true;
        }
        
        if (updates.getEndTime() != null && !updates.getEndTime().equals(existingSafeZone.getEndTime())) {
            existingSafeZone.setEndTime(updates.getEndTime());
            updated = true;
        }
        
        // Revalidate if changes were made
        if (updated) {
            validateTimedSafeZone(existingSafeZone);
        }
        
        return updated;
    }
    
    /**
     * Updates relocatable safe zone specific attributes.
     * 
     * @param existingSafeZone The existing safe zone
     * @param updates The updates to apply
     * @return true if any changes were made, false otherwise
     */
    private boolean updateRelocatableSafeZone(RelocatableSafeZone existingSafeZone, RelocatableSafeZone updates) {
        boolean updated = false;
        
        if (updates.getCooldownSeconds() != null && 
            !updates.getCooldownSeconds().equals(existingSafeZone.getCooldownSeconds())) {
            existingSafeZone.setCooldownSeconds(updates.getCooldownSeconds());
            updated = true;
        }
        
        return updated;
    }
    
    /**
     * Formats cooldown time in a human-readable format.
     * 
     * @param seconds The number of seconds
     * @return Formatted string (e.g., "2 hours 15 minutes 30 seconds")
     */
    private String formatCooldownTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append(hours == 1 ? " hour " : " hours ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append(minutes == 1 ? " minute " : " minutes ");
        }
        sb.append(remainingSeconds).append(remainingSeconds == 1 ? " second" : " seconds");
        
        return sb.toString();
    }

    /**
     * Checks if a player is currently inside any active and authorized safe zone for a given game.
     *
     * @param gameId The ID of the game.
     * @param playerId The ID of the player.
     * @param playerLatitude The player's current latitude.
     * @param playerLongitude The player's current longitude.
     * @param timestamp The current time (Unix timestamp in milliseconds) to check against.
     * @return true if the player is in an active and authorized safe zone, false otherwise.
     * @throws PersistenceException if there's an error fetching safe zones.
     */
    public boolean isPlayerInActiveSafeZone(String gameId, String playerId, double playerLatitude, double playerLongitude, long timestamp) 
            throws PersistenceException {
        if (gameId == null || gameId.isEmpty()) {
            logger.warn("isPlayerInActiveSafeZone called with null or empty gameId.");
            return false;
        }
        if (playerId == null || playerId.isEmpty()) {
            logger.warn("isPlayerInActiveSafeZone called with null or empty playerId for gameId: {}", gameId);
            return false;
        }

        logger.debug("Checking if player {} in game {} is in an active safe zone at ({}, {}) at time {}.", 
            playerId, gameId, playerLatitude, playerLongitude, timestamp);

        List<SafeZone> gameSafeZones = safeZoneDao.getSafeZonesByGameId(gameId);

        if (gameSafeZones.isEmpty()) {
            logger.debug("No safe zones found for gameId: {}. Player {} is not in a safe zone.", gameId, playerId);
            return false;
        }

        for (SafeZone zone : gameSafeZones) {
            if (zone.isActiveAt(timestamp)) {
                logger.debug("Zone {} is active for game {}. Checking location and authorization for player {}.", 
                    zone.getSafeZoneId(), gameId, playerId);
                if (zone.containsLocation(playerLatitude, playerLongitude)) {
                    logger.debug("Player {} is within the boundaries of zone {}. Checking authorization.", 
                        playerId, zone.getSafeZoneId());
                    if (zone.isPlayerAuthorized(playerId)) {
                        logger.info("Player {} in game {} is PROTECTED by active safe zone {}.", 
                            playerId, gameId, zone.getSafeZoneId());
                        return true; // Player is in an active, authorized safe zone
                    }
                    logger.debug("Player {} is NOT authorized for zone {}.", playerId, zone.getSafeZoneId());
                } else {
                    logger.debug("Player {} is NOT within boundaries of zone {}.", playerId, zone.getSafeZoneId());
                }
            } else {
                logger.debug("Zone {} is NOT active for game {} at time {}.", zone.getSafeZoneId(), gameId, timestamp);
            }
        }

        logger.info("Player {} in game {} is NOT in any active safe zone.", playerId, gameId);
        return false; // Player is not in any active and authorized safe zone
    }

    // Method to get active zones using DAO
    public List<SafeZone> getActiveZonesForGame(String gameId, long currentTimestamp) {
        if (gameId == null || gameId.isEmpty()) {
            logger.warn("Attempted to get active safe zones with null or empty gameId.");
            return List.of();
        }
        return safeZoneDao.getActiveSafeZones(gameId, currentTimestamp);
    }

    // Method to check if a location is within any active safe zone for a game
    public boolean isLocationInActiveSafeZone(String gameId, double latitude, double longitude, long timestamp) 
            throws PersistenceException {
        if (gameId == null || gameId.isEmpty()) {
            logger.warn("isLocationInActiveSafeZone called with null or empty gameId.");
            return false; 
        }
        List<SafeZone> activeZones = getActiveZonesForGame(gameId, timestamp);
        for (SafeZone zone : activeZones) {
            if (zone.containsLocation(latitude, longitude)) {
                logger.debug("Location ({}, {}) is inside active safe zone: {}", latitude, longitude, zone.getSafeZoneId());
                return true;
            }
        }
        logger.debug("Location ({}, {}) is not inside any active safe zone for game {}", latitude, longitude, gameId);
        return false;
    }
    
    /**
     * Determines if a player is currently safe, considering both traditional safe zones 
     * and the shrinking zone mechanics. A player is safe if they are:
     * 1. Inside an active traditional safe zone (and authorized), OR
     * 2. Inside the current shrinking zone boundary (if shrinking zone is enabled)
     * 
     * @param gameId The game ID
     * @param playerId The player ID
     * @param playerLatitude The player's latitude
     * @param playerLongitude The player's longitude
     * @param timestamp The current timestamp
     * @return true if the player is safe from elimination, false otherwise
     * @throws PersistenceException if database access fails
     */
    public boolean isPlayerCurrentlySafe(String gameId, String playerId, double playerLatitude, double playerLongitude, long timestamp) 
            throws PersistenceException {
        
        if (gameId == null || gameId.isEmpty()) {
            logger.warn("isPlayerCurrentlySafe called with null or empty gameId.");
            return false;
        }
        if (playerId == null || playerId.isEmpty()) {
            logger.warn("isPlayerCurrentlySafe called with null or empty playerId for gameId: {}", gameId);
            return false;
        }
        
        logger.debug("Checking comprehensive safety for player {} in game {} at ({}, {}) at time {}.", 
            playerId, gameId, playerLatitude, playerLongitude, timestamp);
        
        // First, check traditional safe zones
        boolean inTraditionalSafeZone = isPlayerInActiveSafeZone(gameId, playerId, playerLatitude, playerLongitude, timestamp);
        if (inTraditionalSafeZone) {
            logger.info("Player {} in game {} is SAFE due to traditional safe zone protection.", playerId, gameId);
            return true;
        }
        
        // If not in a traditional safe zone, check shrinking zone status if available
        if (shrinkingZoneService != null) {
            try {
                // Check if shrinking zone is enabled for this game
                if (!shrinkingZoneService.isShrinkingZoneEnabled(gameId)) {
                    logger.debug("Shrinking zone not enabled for game {}. Player {} safety depends on traditional zones only.", 
                                gameId, playerId);
                    return false; // No shrinking zone protection available
                }
                
                // Get current zone state
                Optional<GameZoneState> zoneStateOpt = shrinkingZoneService.advanceZoneState(gameId);
                if (zoneStateOpt.isEmpty()) {
                    logger.debug("No shrinking zone state for game {}. Player {} safety depends on traditional zones only.", 
                                gameId, playerId);
                    return false;
                }
                
                GameZoneState zoneState = zoneStateOpt.get();
                
                // Check if player is inside the current shrinking zone
                Coordinate zoneCenter = zoneState.getCurrentCenter();
                if (zoneCenter == null || zoneState.getCurrentRadiusMeters() == null) {
                    logger.warn("Incomplete shrinking zone state for game {}. Cannot determine zone safety.", gameId);
                    return false;
                }
                
                Coordinate playerLocation = new Coordinate(playerLatitude, playerLongitude);
                double distanceToCenter = GeoUtils.calculateDistance(playerLocation, zoneCenter);
                double zoneRadius = zoneState.getCurrentRadiusMeters();
                boolean isInsideShrinkingZone = distanceToCenter <= zoneRadius;
                
                if (isInsideShrinkingZone) {
                    logger.info("Player {} in game {} is SAFE due to being inside shrinking zone. Distance: {:.2f}m, Zone radius: {:.2f}m", 
                               playerId, gameId, distanceToCenter, zoneRadius);
                    return true;
                } else {
                    logger.warn("Player {} in game {} is UNSAFE - outside shrinking zone. Distance: {:.2f}m, Zone radius: {:.2f}m", 
                               playerId, gameId, distanceToCenter, zoneRadius);
                    return false;
                }
                
            } catch (Exception e) {
                logger.error("Error checking shrinking zone safety for player {} in game {}: {}", 
                            playerId, gameId, e.getMessage(), e);
                // On error, default to unsafe to prevent exploitation
                return false;
            }
        }
        
        // No shrinking zone service available, and not in traditional safe zone
        logger.info("Player {} in game {} is NOT in any safe area.", playerId, gameId);
        return false;
    }
    
    /**
     * Checks if a location would be safe for a player, considering both traditional safe zones
     * and shrinking zone mechanics. This is useful for planning movements or verifying locations.
     * 
     * @param gameId The game ID
     * @param playerId The player ID (for authorization checking)
     * @param latitude The latitude to check
     * @param longitude The longitude to check
     * @param timestamp The timestamp to check against
     * @return true if the location would be safe, false otherwise
     * @throws PersistenceException if database access fails
     */
    public boolean isLocationSafeForPlayer(String gameId, String playerId, double latitude, double longitude, long timestamp) 
            throws PersistenceException {
        
        // Check traditional safe zones first
        List<SafeZone> activeZones = getActiveZonesForGame(gameId, timestamp);
        for (SafeZone zone : activeZones) {
            if (zone.containsLocation(latitude, longitude) && zone.isPlayerAuthorized(playerId)) {
                logger.debug("Location ({}, {}) is safe for player {} due to traditional safe zone: {}", 
                            latitude, longitude, playerId, zone.getSafeZoneId());
                return true;
            }
        }
        
        // Check shrinking zone if available
        if (shrinkingZoneService != null) {
            try {
                if (shrinkingZoneService.isShrinkingZoneEnabled(gameId)) {
                    Optional<GameZoneState> zoneStateOpt = shrinkingZoneService.advanceZoneState(gameId);
                    if (zoneStateOpt.isPresent()) {
                        GameZoneState zoneState = zoneStateOpt.get();
                        Coordinate zoneCenter = zoneState.getCurrentCenter();
                        if (zoneCenter != null && zoneState.getCurrentRadiusMeters() != null) {
                            Coordinate location = new Coordinate(latitude, longitude);
                            double distanceToCenter = GeoUtils.calculateDistance(location, zoneCenter);
                            double zoneRadius = zoneState.getCurrentRadiusMeters();
                            boolean isInsideShrinkingZone = distanceToCenter <= zoneRadius;
                            
                            if (isInsideShrinkingZone) {
                                logger.debug("Location ({}, {}) is safe for player {} due to shrinking zone. Distance: {:.2f}m, Zone radius: {:.2f}m", 
                                            latitude, longitude, playerId, distanceToCenter, zoneRadius);
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Error checking shrinking zone for location safety: {}", e.getMessage());
            }
        }
        
        return false;
    }

    /**
     * Marks expired timed safe zones as inactive.
     * This could be run periodically by a scheduled task.
     *
     * @param gameId The ID of the game to check for expired zones.
     * @return The number of zones marked as expired.
     */
    public int cleanupExpiredTimedZones(String gameId) throws PersistenceException {
        if (gameId == null || gameId.isEmpty()) {
            logger.warn("cleanupExpiredTimedZones called with null or empty gameId.");
            return 0;
        }
        long currentTimestamp = Instant.now().toEpochMilli();
        List<SafeZone> gameZones = safeZoneDao.getSafeZonesByGameId(gameId);
        int expiredCount = 0;

        for (SafeZone zone : gameZones) {
            if (zone.getType() == SafeZone.SafeZoneType.TIMED && zone.getIsActive()) {
                if (zone.getEndTime() != null) {
                    try {
                        Instant endTime = Instant.parse(zone.getEndTime());
                        if (endTime.isBefore(Instant.now())) {
                            zone.setIsActive(false);
                            zone.setLastModifiedAt(Instant.now().toString());
                            // Validate before saving (optional, as we are only changing isActive)
                            // try {
                            //     zone.validate(); 
                            // } catch (IllegalStateException e) {
                            //     logger.error("Error validating zone {} during expiration cleanup: {}", zone.getSafeZoneId(), e.getMessage());
                            //     continue; // Skip this zone if validation fails
                            // }
                            safeZoneDao.saveSafeZone(zone);
                            expiredCount++;
                            logger.info("Marked timed safe zone {} as inactive (expired).", zone.getSafeZoneId());
                        }
                    } catch (Exception e) {
                        logger.error("Error parsing endTime for timed safe zone {} during cleanup: {}", zone.getSafeZoneId(), e.getMessage());
                    }
                }
            }
        }
        if (expiredCount > 0) {
            logger.info("Cleaned up {} expired timed safe zones for game {}.", expiredCount, gameId);
        }
        return expiredCount;
    }

    /**
     * Purchases a private safe zone for a player with payment validation.
     * This method integrates with payment processing and validates player eligibility.
     * 
     * @param gameId The game ID
     * @param name The name of the safe zone
     * @param description The description
     * @param latitude The latitude of the center
     * @param longitude The longitude of the center
     * @param radiusMeters The radius in meters
     * @param playerId The ID of the player purchasing the zone
     * @param authorizedPlayerIds Set of player IDs authorized to use this safe zone (optional, defaults to just the owner)
     * @param paymentMethodId The payment method ID for processing payment
     * @param priceInCents The price in cents for purchasing the private zone
     * @return The created private safe zone
     * @throws ValidationException if validation fails or player is not eligible
     * @throws PersistenceException if saving fails
     * @throws RuntimeException if payment processing fails
     */
    public SafeZone purchasePrivateZone(String gameId, String name, String description,
                                        Double latitude, Double longitude, Double radiusMeters,
                                        String playerId, Set<String> authorizedPlayerIds,
                                        String paymentMethodId, Long priceInCents)
            throws ValidationException, PersistenceException {
        
        logger.info("Player {} attempting to purchase private safe zone for game {} at price {} cents", 
                   playerId, gameId, priceInCents);
        
        // Validate required parameters
        if (gameId == null || gameId.isEmpty()) {
            throw new ValidationException("Game ID is required for purchasing a private safe zone");
        }
        if (playerId == null || playerId.isEmpty()) {
            throw new ValidationException("Player ID is required for purchasing a private safe zone");
        }
        if (paymentMethodId == null || paymentMethodId.isEmpty()) {
            throw new ValidationException("Payment method is required for purchasing a private safe zone");
        }
        if (priceInCents == null || priceInCents <= 0) {
            throw new ValidationException("Valid price is required for purchasing a private safe zone");
        }
        
        // Basic business rule validation
        // Check if player already owns too many private zones in this game
        List<SafeZone> existingPrivateZones = getSafeZonesByOwner(gameId, playerId)
                .stream()
                .filter(zone -> zone.getType() == SafeZone.SafeZoneType.PRIVATE)
                .toList();
        
        final int MAX_PRIVATE_ZONES_PER_PLAYER = 3; // Business rule - configurable
        if (existingPrivateZones.size() >= MAX_PRIVATE_ZONES_PER_PLAYER) {
            throw new ValidationException("Player " + playerId + " already owns the maximum number of private safe zones (" + 
                                         MAX_PRIVATE_ZONES_PER_PLAYER + ") in game " + gameId);
        }
        
        // Validate coordinates and radius
        if (latitude == null || longitude == null) {
            throw new ValidationException("Latitude and longitude are required for the safe zone location");
        }
        if (radiusMeters == null || radiusMeters <= 0 || radiusMeters > 500) { // Max 500m radius for private zones
            throw new ValidationException("Private safe zone radius must be between 1 and 500 meters");
        }
        
        // TODO: In a real implementation, you would integrate with a payment service here
        // For now, we'll simulate payment processing
        boolean paymentSuccessful = processPayment(playerId, gameId, paymentMethodId, priceInCents);
        if (!paymentSuccessful) {
            throw new RuntimeException("Payment processing failed for private safe zone purchase");
        }
        
        logger.info("Payment successful for player {} purchasing private safe zone in game {}", playerId, gameId);
        
        // Set up authorized players (defaults to just the owner if not specified)
        Set<String> finalAuthorizedIds = authorizedPlayerIds != null ? authorizedPlayerIds : new java.util.HashSet<>();
        finalAuthorizedIds.add(playerId); // Ensure owner is always authorized
        
        // Create the private safe zone
        SafeZone privateZone = SafeZone.createPrivateZone(
            gameId, name, description, latitude, longitude, radiusMeters, playerId, finalAuthorizedIds
        );
        
        // Note: In a production system, purchase details would be stored in a separate Transaction table
        // For now, we'll log the purchase information
        logger.info("Private safe zone purchase details - Price: {} cents, Payment Method: {}, Date: {}", 
                   priceInCents, paymentMethodId, Instant.now().toString());
        
        SafeZone createdZone = createGeneralSafeZone(privateZone);
        
        logger.info("Successfully created purchased private safe zone {} for player {} in game {}", 
                   createdZone.getSafeZoneId(), playerId, gameId);
        
        return createdZone;
    }

    /**
     * Creates a new public safe zone with authorization checks.
     * Only game organizers/admins should be able to create public zones.
     * 
     * @param gameId The game ID
     * @param name The name of the safe zone
     * @param description The description
     * @param latitude The latitude of the center
     * @param longitude The longitude of the center
     * @param radiusMeters The radius in meters
     * @param createdBy The ID of the player creating the zone (must be authorized)
     * @param isGameOrganizer Whether the creator is a game organizer/admin
     * @return The created public safe zone
     * @throws ValidationException if validation fails or creator is not authorized
     * @throws PersistenceException if saving fails
     */
    public SafeZone createPublicZoneWithAuth(String gameId, String name, String description,
                                            Double latitude, Double longitude, Double radiusMeters,
                                            String createdBy, boolean isGameOrganizer) 
            throws ValidationException, PersistenceException {
        
        // Authorization check for public zone creation
        if (!isGameOrganizer) {
            throw new ValidationException("Only game organizers can create public safe zones");
        }
        
        logger.info("Game organizer {} creating public safe zone for game {}", createdBy, gameId);
        
        // Validate public zone specific rules
        if (radiusMeters != null && radiusMeters > 1000) { // Max 1km radius for public zones
            throw new ValidationException("Public safe zone radius cannot exceed 1000 meters");
        }
        
        return createPublicSafeZone(gameId, name, description, latitude, longitude, radiusMeters, createdBy);
    }

    /**
     * Simulates payment processing for private safe zone purchases.
     * In a real implementation, this would integrate with a payment service like Stripe.
     * 
     * @param playerId The player making the payment
     * @param gameId The game context
     * @param paymentMethodId The payment method identifier
     * @param priceInCents The amount to charge in cents
     * @return true if payment was successful, false otherwise
     */
    private boolean processPayment(String playerId, String gameId, String paymentMethodId, Long priceInCents) {
        logger.info("Processing payment for player {} in game {}: {} cents using payment method {}", 
                   playerId, gameId, priceInCents, paymentMethodId);
        
        try {
            // TODO: Integrate with actual payment service (e.g., Stripe, PayPal)
            // This is a simulation for now
            
            // Simulate payment validation
            if (paymentMethodId.startsWith("invalid")) {
                logger.error("Invalid payment method: {}", paymentMethodId);
                return false;
            }
            
            // Simulate network delay
            Thread.sleep(100);
            
            // Simulate 95% success rate
            boolean success = Math.random() < 0.95;
            
            if (success) {
                logger.info("Payment processed successfully for player {} - transaction simulated", playerId);
            } else {
                logger.error("Payment processing failed for player {} - simulation failure", playerId);
            }
            
            return success;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Payment processing interrupted for player {}: {}", playerId, e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Payment processing error for player {}: {}", playerId, e.getMessage(), e);
            return false;
        }
    }
} 