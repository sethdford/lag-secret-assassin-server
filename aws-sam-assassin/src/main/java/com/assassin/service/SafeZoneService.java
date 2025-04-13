package com.assassin.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.DynamoDbSafeZoneDao;
import com.assassin.dao.SafeZoneDao;
import com.assassin.exception.PersistenceException;
import com.assassin.exception.SafeZoneNotFoundException;
import com.assassin.exception.UnauthorizedException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Coordinate;
import com.assassin.model.SafeZone;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

/**
 * Service layer for handling Safe Zone logic.
 */
public class SafeZoneService {

    private static final Logger logger = LoggerFactory.getLogger(SafeZoneService.class);
    private final SafeZoneDao safeZoneDao;
    // Add other DAOs as needed (e.g., GameDao to validate gameId)

    // Default constructor using the default DAO constructor
    public SafeZoneService() {
        this(new DynamoDbSafeZoneDao());
    }

    // Constructor for dependency injection with DAO
    public SafeZoneService(SafeZoneDao safeZoneDao) {
        this.safeZoneDao = safeZoneDao;
    }
    
    // Constructor for dependency injection with Enhanced Client
    public SafeZoneService(DynamoDbEnhancedClient enhancedClient) {
        this(new DynamoDbSafeZoneDao(enhancedClient));
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
        if (safeZone.getCenter() == null) {
            throw new ValidationException("Center coordinate is required.");
        }
        if (safeZone.getRadiusMeters() == null || safeZone.getRadiusMeters() <= 0) {
            throw new ValidationException("Valid radius (in meters) is required.");
        }
        if (safeZone.getType() == null || safeZone.getType().isEmpty()) {
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
     * Calculates whether a given location is within any safe zone of a game.
     *
     * @param gameId    the ID of the game to check safe zones for
     * @param location  the coordinate to check
     * @return true if the location is within any safe zone, false otherwise
     */
    public boolean isLocationInSafeZone(String gameId, Coordinate location) {
        if (gameId == null || gameId.isEmpty() || location == null) {
            return false;
        }

        // Get all safe zones for the game
        List<SafeZone> safeZones = getSafeZonesForGame(gameId);
        
        // Check if the location is within any safe zone
        for (SafeZone safeZone : safeZones) {
            if (isWithinRadius(location, safeZone.getCenter(), safeZone.getRadiusMeters())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Determines if a location is within the radius of a center point.
     * Uses the Haversine formula to calculate the distance between two coordinates.
     *
     * @param location      the location to check
     * @param center        the center of the circle
     * @param radiusMeters  the radius in meters
     * @return true if the location is within the radius, false otherwise
     */
    private boolean isWithinRadius(Coordinate location, Coordinate center, Double radiusMeters) {
        if (location == null || center == null || radiusMeters == null) {
            return false;
        }
        
        // Earth's radius in meters
        final double EARTH_RADIUS_METERS = 6371000;
        
        // Convert to radians
        double lat1 = Math.toRadians(location.getLatitude());
        double lon1 = Math.toRadians(location.getLongitude());
        double lat2 = Math.toRadians(center.getLatitude());
        double lon2 = Math.toRadians(center.getLongitude());
        
        // Haversine formula
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(lat1) * Math.cos(lat2) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double distance = EARTH_RADIUS_METERS * c;
        
        return distance <= radiusMeters;
    }

    /**
     * Updates an existing safe zone.
     *
     * @param safeZoneId The ID of the safe zone to update.
     * @param updates The SafeZone object containing updates.
     * @param requestingPlayerId ID of player making the request (for authorization).
     * @return The updated SafeZone.
     * @throws SafeZoneNotFoundException if the safe zone doesn't exist.
     * @throws ValidationException if updates are invalid.
     * @throws UnauthorizedException if player is not authorized to update.
     * @throws PersistenceException if update fails.
     */
    public SafeZone updateSafeZone(String safeZoneId, SafeZone updates, String requestingPlayerId) 
            throws SafeZoneNotFoundException, ValidationException, UnauthorizedException, PersistenceException {
        
        if (safeZoneId == null || safeZoneId.isEmpty()) {
            throw new ValidationException("SafeZone ID cannot be null or empty");
        }
        
        if (updates == null) {
            throw new ValidationException("Updates cannot be null");
        }
        
        // Find the existing safe zone
        Optional<SafeZone> existingZoneOpt = safeZoneDao.getSafeZoneById(safeZoneId);
        if (existingZoneOpt.isEmpty()) {
            logger.warn("Safe zone not found for update: {}", safeZoneId);
            throw new SafeZoneNotFoundException("Safe zone not found with ID: " + safeZoneId);
        }
        
        SafeZone existingZone = existingZoneOpt.get();
        
        // TODO: Implement authorization check here
        // e.g., check if requestingPlayerId is a game admin
        // For now, we'll skip this and focus on the update logic
        
        // Apply updates, preserving immutable fields
        if (updates.getName() != null && !updates.getName().isEmpty()) {
            existingZone.setName(updates.getName());
        }
        
        if (updates.getCenter() != null) {
            existingZone.setCenter(updates.getCenter());
        }
        
        if (updates.getRadiusMeters() != null && updates.getRadiusMeters() > 0) {
            existingZone.setRadiusMeters(updates.getRadiusMeters());
        }
        
        if (updates.getType() != null && !updates.getType().isEmpty()) {
            existingZone.setType(updates.getType());
        }
        
        if (updates.getExpiresAt() != null) {
            existingZone.setExpiresAt(updates.getExpiresAt());
        }
        
        // Never update these fields from client input
        // - safeZoneId (primary key)
        // - gameId (partition key)
        // - createdAt (immutable timestamp)
        
        logger.info("Updating safe zone: {} for game: {}", existingZone.getSafeZoneId(), existingZone.getGameId());
        safeZoneDao.saveSafeZone(existingZone);
        return existingZone;
    }

    // Add methods for updating safe zones, checking player proximity to zones, etc.

} 