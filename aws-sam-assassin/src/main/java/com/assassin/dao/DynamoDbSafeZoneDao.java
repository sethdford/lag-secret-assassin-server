package com.assassin.dao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.exception.PersistenceException;
import com.assassin.model.SafeZone;
import com.assassin.util.DynamoDbClientProvider;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

public class DynamoDbSafeZoneDao implements SafeZoneDao {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbSafeZoneDao.class);
    private static final String SAFE_ZONES_TABLE_ENV_VAR = "SAFE_ZONES_TABLE_NAME";
    private static final String GAME_ID_INDEX_NAME = "GameIdIndex"; // Example GSI name

    private final DynamoDbTable<SafeZone> safeZoneTable;
    private final DynamoDbIndex<SafeZone> gameIdIndex; // GSI for querying by gameId
    private final DynamoDbEnhancedClient enhancedClient;
    private final String tableName;

    // It might be beneficial to add another GSI on GameID and Type, or GameID and CreatedBy
    // if these queries become frequent and performance-critical.
    // For now, filtering will be done client-side after querying by GameID.

    // Constructor accepting the enhanced client for dependency injection
    public DynamoDbSafeZoneDao(DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
        this.tableName = getTableName(); // Table name logic remains the same
        if (this.tableName == null || this.tableName.isEmpty()) {
            throw new IllegalStateException("Could not determine SafeZones table name from System Property or Environment Variable '" + SAFE_ZONES_TABLE_ENV_VAR + "'");
        }
        logger.info("Initializing DynamoDbSafeZoneDao with table: {} using provided client", this.tableName);
        this.safeZoneTable = this.enhancedClient.table(this.tableName, TableSchema.fromBean(SafeZone.class));
        try {
            // Initialize GSI using the provided client's table reference
            this.gameIdIndex = this.safeZoneTable.index(GAME_ID_INDEX_NAME);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to initialize GSI '{}' for table '{}'. Ensure the index exists and is correctly defined.", 
                         GAME_ID_INDEX_NAME, this.tableName, e);
            throw e; // Re-throw to fail initialization if index is critical
        }
    }

    // Keep the default constructor but have it delegate to the new one
    public DynamoDbSafeZoneDao() {
        this(DynamoDbClientProvider.getDynamoDbEnhancedClient());
    }

    @Override
    public Optional<SafeZone> getSafeZoneById(String safeZoneId) {
        logger.debug("Getting safe zone by ID: {}", safeZoneId);
        try {
            Key key = Key.builder().partitionValue(safeZoneId).build();
            return Optional.ofNullable(safeZoneTable.getItem(key));
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error getting safe zone {}: {}", safeZoneId, e.getMessage(), e);
            throw new PersistenceException("Error retrieving safe zone from DynamoDB", e);
        }
    }

    @Override
    public List<SafeZone> getSafeZonesByGameId(String gameId) {
        logger.debug("Getting safe zones by game ID: {}", gameId);
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(gameId).build());
            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .build();

            var results = gameIdIndex.query(queryRequest);
            List<SafeZone> safeZones = results.stream()
                    .flatMap(page -> page.items().stream())
                    .collect(Collectors.toList());
            logger.debug("Found {} safe zones for game ID {}", safeZones.size(), gameId);
            return safeZones;
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error querying safe zones by game ID {}: {}", gameId, e.getMessage(), e);
            throw new PersistenceException("Error retrieving safe zones by game ID from DynamoDB", e);
        }
    }

    @Override
    public void saveSafeZone(SafeZone safeZone) {
        logger.debug("Saving safe zone with ID: {} of type: {}", safeZone.getSafeZoneId(), safeZone.getType());
        
        // Validate zone before saving
        try {
            safeZone.validate();
        } catch (IllegalArgumentException e) {
            logger.error("Validation failed for safe zone {}: {}", safeZone.getSafeZoneId(), e.getMessage());
            throw new PersistenceException("SafeZone validation failed: " + e.getMessage(), e);
        }
        
        // Set timestamps if not already set
        String now = Instant.now().toString();
        if (safeZone.getCreatedAt() == null) {
            safeZone.setCreatedAt(now);
        }
        if (safeZone.getLastModifiedAt() == null) {
            safeZone.setLastModifiedAt(now);
        }
        
        try {
            safeZoneTable.putItem(safeZone);
            logger.info("Successfully saved safe zone: {} of type: {}", safeZone.getSafeZoneId(), safeZone.getType());
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error saving safe zone {}: {}", safeZone.getSafeZoneId(), e.getMessage(), e);
            throw new PersistenceException("Error saving safe zone to DynamoDB", e);
        }
    }

    @Override
    public void deleteSafeZone(String safeZoneId) {
        logger.info("Deleting safe zone with ID: {}", safeZoneId);
        try {
            Key key = Key.builder().partitionValue(safeZoneId).build();
            DeleteItemEnhancedRequest deleteRequest = DeleteItemEnhancedRequest.builder().key(key).build();
            safeZoneTable.deleteItem(deleteRequest);
            logger.info("Successfully deleted safe zone: {}", safeZoneId);
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error deleting safe zone {}: {}", safeZoneId, e.getMessage(), e);
            throw new PersistenceException("Error deleting safe zone from DynamoDB", e);
        }
    }

    @Override
    public List<SafeZone> getSafeZonesByType(String gameId, SafeZone.SafeZoneType type) {
        logger.debug("Getting safe zones by game ID: {} and type: {}", gameId, type);
        List<SafeZone> gameZones = getSafeZonesByGameId(gameId);
        List<SafeZone> filteredZones = gameZones.stream()
                .filter(zone -> zone.getType() == type)
                .collect(Collectors.toList());
        logger.debug("Found {} safe zones of type {} for game ID {}", filteredZones.size(), type, gameId);
        return filteredZones;
    }

    @Override
    public List<SafeZone> getSafeZonesByOwner(String gameId, String ownerPlayerId) {
        logger.debug("Getting safe zones by game ID: {} and owner: {}", gameId, ownerPlayerId);
        List<SafeZone> gameZones = getSafeZonesByGameId(gameId);
        List<SafeZone> ownedZones = gameZones.stream()
                .filter(zone -> ownerPlayerId.equals(zone.getCreatedBy()))
                .collect(Collectors.toList());
        logger.debug("Found {} safe zones owned by player {} for game ID {}", ownedZones.size(), ownerPlayerId, gameId);
        return ownedZones;
    }

    @Override
    public void updateRelocatableSafeZone(String safeZoneId, double newLatitude, double newLongitude, String newLastRelocationTime, int newRelocationCount) {
        logger.info("Updating relocatable safe zone with ID: {} to new location: {}, {}", safeZoneId, newLatitude, newLongitude);
        try {
            SafeZone zone = getSafeZoneById(safeZoneId)
                    .orElseThrow(() -> new PersistenceException("SafeZone with ID " + safeZoneId + " not found for update.", null));

            if (zone.getType() != SafeZone.SafeZoneType.RELOCATABLE) {
                throw new IllegalArgumentException("Cannot update non-relocatable safe zone with ID: " + safeZoneId + " (type: " + zone.getType() + ")");
            }

            zone.setLatitude(newLatitude);
            zone.setLongitude(newLongitude);
            zone.setLastRelocationTime(newLastRelocationTime);
            zone.setRelocationCount(newRelocationCount);
            zone.setLastModifiedAt(Instant.now().toString());

            safeZoneTable.updateItem(zone);
            logger.info("Successfully updated relocatable safe zone: {} to location: {}, {} (relocation #{}", 
                       safeZoneId, newLatitude, newLongitude, newRelocationCount);
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error updating relocatable safe zone {}: {}", safeZoneId, e.getMessage(), e);
            throw new PersistenceException("Error updating relocatable safe zone in DynamoDB", e);
        }
    }

    @Override
    public List<SafeZone> getActiveSafeZones(String gameId, long currentTimestamp) {
        logger.debug("Getting active safe zones for game ID: {} at timestamp: {}", gameId, currentTimestamp);
        List<SafeZone> gameZones = getSafeZonesByGameId(gameId);
        List<SafeZone> activeZones = gameZones.stream()
                .filter(zone -> zone.isActiveAt(currentTimestamp))
                .collect(Collectors.toList());
        logger.debug("Found {} active safe zones for game ID {} at timestamp {}", activeZones.size(), gameId, currentTimestamp);
        return activeZones;
    }

    // Additional enhanced methods for better safe zone management

    /**
     * Get expired safe zones for cleanup purposes.
     * This is useful for maintenance tasks to remove or deactivate expired zones.
     */
    public List<SafeZone> getExpiredSafeZones(String gameId, long currentTimestamp) {
        logger.debug("Getting expired safe zones for game ID: {} at timestamp: {}", gameId, currentTimestamp);
        List<SafeZone> gameZones = getSafeZonesByGameId(gameId);
        List<SafeZone> expiredZones = gameZones.stream()
                .filter(zone -> !zone.isActiveAt(currentTimestamp))
                .filter(zone -> zone.getType() == SafeZone.SafeZoneType.TIMED) // Only timed zones can expire
                .collect(Collectors.toList());
        logger.debug("Found {} expired safe zones for game ID {} at timestamp {}", expiredZones.size(), gameId, currentTimestamp);
        return expiredZones;
    }

    /**
     * Get relocatable safe zones that are eligible for relocation (based on cooldown periods if any).
     * This method helps identify zones that can be moved by their owners.
     */
    public List<SafeZone> getRelocatableSafeZones(String gameId, String ownerPlayerId) {
        logger.debug("Getting relocatable safe zones for game ID: {} and owner: {}", gameId, ownerPlayerId);
        List<SafeZone> ownedZones = getSafeZonesByOwner(gameId, ownerPlayerId);
        List<SafeZone> relocatableZones = ownedZones.stream()
                .filter(zone -> zone.getType() == SafeZone.SafeZoneType.RELOCATABLE)
                .collect(Collectors.toList());
        logger.debug("Found {} relocatable safe zones for owner {} in game {}", relocatableZones.size(), ownerPlayerId, gameId);
        return relocatableZones;
    }

    /**
     * Batch save multiple safe zones for efficiency.
     * Useful when creating multiple zones at once during game setup.
     */
    public void saveSafeZones(List<SafeZone> safeZones) {
        logger.info("Batch saving {} safe zones", safeZones.size());
        try {
            for (SafeZone zone : safeZones) {
                // Validate each zone
                try {
                    zone.validate();
                } catch (IllegalArgumentException e) {
                    logger.error("Validation failed for safe zone {}: {}", zone.getSafeZoneId(), e.getMessage());
                    throw new PersistenceException("SafeZone validation failed for zone " + zone.getSafeZoneId() + ": " + e.getMessage(), e);
                }
                
                // Set timestamps if not already set
                String now = Instant.now().toString();
                if (zone.getCreatedAt() == null) {
                    zone.setCreatedAt(now);
                }
                if (zone.getLastModifiedAt() == null) {
                    zone.setLastModifiedAt(now);
                }
            }

            // Use batch write for efficiency
            var writeRequestBuilder = software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch.builder(SafeZone.class);
            for (SafeZone zone : safeZones) {
                writeRequestBuilder.addPutItem(zone);
            }
            
            enhancedClient.batchWriteItem(r -> r.addWriteBatch(writeRequestBuilder.build()));
            logger.info("Successfully batch saved {} safe zones", safeZones.size());
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error batch saving safe zones: {}", e.getMessage(), e);
            throw new PersistenceException("Error batch saving safe zones to DynamoDB", e);
        }
    }

    /**
     * Update safe zone status (activate/deactivate).
     * This is useful for game masters to control zone availability.
     */
    public void updateSafeZoneStatus(String safeZoneId, boolean isActive) {
        logger.info("Updating safe zone {} status to: {}", safeZoneId, isActive ? "active" : "inactive");
        try {
            SafeZone zone = getSafeZoneById(safeZoneId)
                    .orElseThrow(() -> new PersistenceException("SafeZone with ID " + safeZoneId + " not found for status update.", null));

            zone.setIsActive(isActive);
            zone.setLastModifiedAt(Instant.now().toString());

            UpdateItemEnhancedRequest<SafeZone> updateRequest = UpdateItemEnhancedRequest.builder(SafeZone.class)
                    .item(zone)
                    .build();
            
            safeZoneTable.updateItem(updateRequest);
            logger.info("Successfully updated safe zone {} status to: {}", safeZoneId, isActive ? "active" : "inactive");
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error updating safe zone {} status: {}", safeZoneId, e.getMessage(), e);
            throw new PersistenceException("Error updating safe zone status in DynamoDB", e);
        }
    }

    // Helper method to get table name from environment variable or system property
    private String getTableName() {
        String tableNameFromEnv = System.getenv(SAFE_ZONES_TABLE_ENV_VAR);
        String tableNameFromProp = System.getProperty(SAFE_ZONES_TABLE_ENV_VAR);

        if (tableNameFromProp != null && !tableNameFromProp.isEmpty()) {
            logger.info("Using SafeZones table name from System Property: {}", tableNameFromProp);
            return tableNameFromProp;
        }
        if (tableNameFromEnv != null && !tableNameFromEnv.isEmpty()) {
             logger.info("Using SafeZones table name from Environment Variable: {}", tableNameFromEnv);
            return tableNameFromEnv;
        }
        logger.error("SafeZones table name not found in System Property or Environment Variable ({})", SAFE_ZONES_TABLE_ENV_VAR);
        return null; // Or throw an exception
    }
} 