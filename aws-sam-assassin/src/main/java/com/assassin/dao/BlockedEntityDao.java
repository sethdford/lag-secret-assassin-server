package com.assassin.dao;

import com.assassin.model.BlockedEntity;

import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for BlockedEntity operations.
 */
public interface BlockedEntityDao {
    
    /**
     * Save a blocked entity to the database.
     * 
     * @param blockedEntity The blocked entity to save
     * @return The saved blocked entity
     */
    BlockedEntity saveBlockedEntity(BlockedEntity blockedEntity);
    
    /**
     * Get a blocked entity by its ID.
     * 
     * @param entityId The entity ID (IP address or user ID)
     * @return Optional containing the blocked entity if found
     */
    Optional<BlockedEntity> getBlockedEntityById(String entityId);
    
    /**
     * Check if an entity is currently blocked (active and not expired).
     * 
     * @param entityId The entity ID to check
     * @return true if the entity is currently blocked
     */
    boolean isEntityBlocked(String entityId);
    
    /**
     * Get all blocked entities of a specific type.
     * 
     * @param entityType The entity type ("ip" or "user")
     * @return List of blocked entities of the specified type
     */
    List<BlockedEntity> getBlockedEntitiesByType(String entityType);
    
    /**
     * Get all blocked entities that expire on a specific date.
     * 
     * @param expirationDate The expiration date (YYYY-MM-DD format)
     * @return List of blocked entities expiring on the specified date
     */
    List<BlockedEntity> getBlockedEntitiesByExpirationDate(String expirationDate);
    
    /**
     * Update a blocked entity.
     * 
     * @param blockedEntity The blocked entity to update
     * @return The updated blocked entity
     */
    BlockedEntity updateBlockedEntity(BlockedEntity blockedEntity);
    
    /**
     * Deactivate a blocked entity (set isActive to false).
     * 
     * @param entityId The entity ID to deactivate
     * @return true if the entity was successfully deactivated
     */
    boolean deactivateBlockedEntity(String entityId);
    
    /**
     * Delete a blocked entity from the database.
     * 
     * @param entityId The entity ID to delete
     * @return true if the entity was successfully deleted
     */
    boolean deleteBlockedEntity(String entityId);
    
    /**
     * Get all active (non-expired) blocked entities.
     * 
     * @return List of active blocked entities
     */
    List<BlockedEntity> getActiveBlockedEntities();
    
    /**
     * Clean up expired blocked entities by deactivating them.
     * 
     * @return Number of entities that were deactivated
     */
    int cleanupExpiredBlocks();
} 