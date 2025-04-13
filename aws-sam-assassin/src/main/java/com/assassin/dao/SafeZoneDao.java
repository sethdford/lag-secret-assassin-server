package com.assassin.dao;

import com.assassin.model.SafeZone;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for SafeZone entities.
 */
public interface SafeZoneDao {

    /**
     * Retrieves a safe zone by its unique ID.
     *
     * @param safeZoneId The ID of the safe zone.
     * @return An Optional containing the SafeZone if found, otherwise empty.
     */
    Optional<SafeZone> getSafeZoneById(String safeZoneId);

    /**
     * Retrieves all safe zones associated with a specific game.
     * Requires a GSI on gameId.
     *
     * @param gameId The ID of the game.
     * @return A list of SafeZones for the given game.
     */
    List<SafeZone> getSafeZonesByGameId(String gameId);

    /**
     * Saves a new safe zone or updates an existing one.
     *
     * @param safeZone The SafeZone object to save.
     */
    void saveSafeZone(SafeZone safeZone);

    /**
     * Deletes a safe zone by its ID.
     *
     * @param safeZoneId The ID of the safe zone to delete.
     */
    void deleteSafeZone(String safeZoneId);

    // Add other methods as needed, e.g., findActiveSafeZones, findByType, etc.
} 