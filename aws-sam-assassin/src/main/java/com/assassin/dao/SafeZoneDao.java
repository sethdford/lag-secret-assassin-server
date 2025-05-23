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

    /**
     * Retrieves all safe zones of a specific type for a given game.
     *
     * @param gameId The ID of the game.
     * @param type The type of safe zone to retrieve.
     * @return A list of SafeZones matching the type for the given game.
     */
    List<SafeZone> getSafeZonesByType(String gameId, SafeZone.SafeZoneType type);

    /**
     * Retrieves all safe zones owned by a specific player in a given game.
     * This typically applies to PRIVATE or RELOCATABLE zones where 'createdBy' is the owner.
     *
     * @param gameId The ID of the game.
     * @param ownerPlayerId The ID of the player who owns the safe zones.
     * @return A list of SafeZones owned by the player in the game.
     */
    List<SafeZone> getSafeZonesByOwner(String gameId, String ownerPlayerId);

    /**
     * Updates the location and potentially other attributes of a relocatable safe zone.
     *
     * @param safeZoneId The ID of the safe zone to update.
     * @param newLatitude The new latitude.
     * @param newLongitude The new longitude.
     * @param newLastRelocationTime The timestamp of this relocation.
     * @param newRelocationCount The updated relocation count.
     */
    void updateRelocatableSafeZone(String safeZoneId, double newLatitude, double newLongitude, String newLastRelocationTime, int newRelocationCount);

    /**
     * Retrieves all safe zones for a given game that are currently active based on the provided timestamp.
     * This considers the base isActive flag and type-specific logic (e.g., start/end times for TIMED zones).
     *
     * @param gameId The ID of the game.
     * @param currentTimestamp The current time (Unix timestamp in milliseconds) to check against.
     * @return A list of active SafeZones for the given game at the current time.
     */
    List<SafeZone> getActiveSafeZones(String gameId, long currentTimestamp);

    // Enhanced methods for better safe zone management

    /**
     * Get expired safe zones for cleanup purposes.
     * This is useful for maintenance tasks to remove or deactivate expired zones.
     *
     * @param gameId The ID of the game.
     * @param currentTimestamp The current time (Unix timestamp in milliseconds) to check against.
     * @return A list of expired SafeZones for the given game.
     */
    List<SafeZone> getExpiredSafeZones(String gameId, long currentTimestamp);

    /**
     * Get relocatable safe zones that are eligible for relocation (based on cooldown periods if any).
     * This method helps identify zones that can be moved by their owners.
     *
     * @param gameId The ID of the game.
     * @param ownerPlayerId The ID of the player who owns the zones.
     * @return A list of relocatable SafeZones owned by the player.
     */
    List<SafeZone> getRelocatableSafeZones(String gameId, String ownerPlayerId);

    /**
     * Batch save multiple safe zones for efficiency.
     * Useful when creating multiple zones at once during game setup.
     *
     * @param safeZones The list of SafeZone objects to save.
     */
    void saveSafeZones(List<SafeZone> safeZones);

    /**
     * Update safe zone status (activate/deactivate).
     * This is useful for game masters to control zone availability.
     *
     * @param safeZoneId The ID of the safe zone to update.
     * @param isActive The new active status.
     */
    void updateSafeZoneStatus(String safeZoneId, boolean isActive);

    // No explicit getExpiredSafeZones is added; active check implies non-expired for timed.
    // Expiration cleanup can be a service-level task that uses getSafeZonesByGameId and filters.
} 