package com.assassin.dao;

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
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

public class DynamoDbSafeZoneDao implements SafeZoneDao {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbSafeZoneDao.class);
    private static final String SAFE_ZONES_TABLE_ENV_VAR = "SAFE_ZONES_TABLE_NAME";
    private static final String GAME_ID_INDEX_NAME = "GameIdIndex"; // Example GSI name

    private final DynamoDbTable<SafeZone> safeZoneTable;
    private final DynamoDbIndex<SafeZone> gameIdIndex; // GSI for querying by gameId
    private final DynamoDbEnhancedClient enhancedClient;
    private final String tableName;

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
        logger.debug("Saving safe zone with ID: {}", safeZone.getSafeZoneId());
        try {
            safeZoneTable.putItem(safeZone);
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
            safeZoneTable.deleteItem(deleteRequest); // Consider returning the deleted item if needed
            logger.info("Successfully deleted safe zone: {}", safeZoneId);
        } catch (DynamoDbException e) {
            // Consider adding conditional delete or checking existence first if needed
            logger.error("DynamoDB error deleting safe zone {}: {}", safeZoneId, e.getMessage(), e);
            throw new PersistenceException("Error deleting safe zone from DynamoDB", e);
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