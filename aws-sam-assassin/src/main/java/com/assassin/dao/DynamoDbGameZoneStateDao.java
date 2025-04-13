package com.assassin.dao;

import com.assassin.exception.PersistenceException;
import com.assassin.model.GameZoneState;
import com.assassin.util.DynamoDbClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.Optional;

public class DynamoDbGameZoneStateDao implements GameZoneStateDao {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbGameZoneStateDao.class);
    private final DynamoDbTable<GameZoneState> gameZoneStateTable;

    // Constants
    private static final String TABLE_NAME_ENV_VAR = "GAME_ZONE_STATE_TABLE_NAME"; // Need to define this
    private static final String DEFAULT_TABLE_NAME = "dev-GameZoneState"; // Sensible default

    public DynamoDbGameZoneStateDao() {
        DynamoDbEnhancedClient enhancedClient = DynamoDbClientProvider.getEnhancedClient();
        String tableName = Optional.ofNullable(System.getenv(TABLE_NAME_ENV_VAR)).orElse(DEFAULT_TABLE_NAME);
        this.gameZoneStateTable = enhancedClient.table(tableName, TableSchema.fromBean(GameZoneState.class));
    }

    @Override
    public void saveGameZoneState(GameZoneState gameZoneState) {
        if (gameZoneState == null || gameZoneState.getGameId() == null) {
            logger.error("Attempted to save null GameZoneState or state with null gameId");
            throw new IllegalArgumentException("GameZoneState and its gameId cannot be null");
        }
        try {
            logger.debug("Saving game zone state for game ID: {}", gameZoneState.getGameId());
            gameZoneStateTable.putItem(gameZoneState);
            logger.info("Successfully saved game zone state for game ID: {}", gameZoneState.getGameId());
        } catch (DynamoDbException e) {
            logger.error("Error saving GameZoneState for game {}: {}", gameZoneState.getGameId(), e.getMessage(), e);
            throw new PersistenceException("Failed to save game zone state for game: " + gameZoneState.getGameId(), e);
        }
    }

    @Override
    public Optional<GameZoneState> getGameZoneState(String gameId) {
        if (gameId == null || gameId.isEmpty()) {
            logger.warn("Attempted to get GameZoneState with null or empty gameId");
            return Optional.empty();
        }
        try {
            logger.debug("Getting game zone state for game ID: {}", gameId);
            Key key = Key.builder().partitionValue(gameId).build();
            GameZoneState state = gameZoneStateTable.getItem(key);
            return Optional.ofNullable(state);
        } catch (DynamoDbException e) {
            logger.error("Error getting GameZoneState for game {}: {}", gameId, e.getMessage(), e);
            throw new PersistenceException("Failed to get game zone state for game: " + gameId, e);
        }
    }

    @Override
    public void deleteGameZoneState(String gameId) {
        if (gameId == null || gameId.isEmpty()) {
            logger.warn("Attempted to delete GameZoneState with null or empty gameId");
            return; // Or throw IllegalArgumentException?
        }
        try {
            logger.debug("Deleting game zone state for game ID: {}", gameId);
            Key key = Key.builder().partitionValue(gameId).build();
            gameZoneStateTable.deleteItem(key);
            logger.info("Successfully deleted game zone state for game ID: {}", gameId);
        } catch (DynamoDbException e) {
            logger.error("Error deleting GameZoneState for game {}: {}", gameId, e.getMessage(), e);
            throw new PersistenceException("Failed to delete game zone state for game: " + gameId, e);
        }
    }
} 