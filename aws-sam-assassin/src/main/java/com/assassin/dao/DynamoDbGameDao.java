package com.assassin.dao;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.exception.GamePersistenceException;
import com.assassin.model.Game;
import com.assassin.util.DynamoDbClientProvider;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

/**
 * DynamoDB implementation of the GameDao interface.
 */
public class DynamoDbGameDao implements GameDao {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbGameDao.class);
    private static final String GAMES_TABLE_NAME_ENV_VAR = "GAMES_TABLE_NAME";
    private static final String STATUS_CREATED_AT_INDEX = "StatusCreatedAtIndex";

    private final DynamoDbTable<Game> gameTable;
    private final DynamoDbIndex<Game> statusIndex;
    private final String tableName;

    public DynamoDbGameDao() {
        DynamoDbEnhancedClient enhancedClient = DynamoDbClientProvider.getDynamoDbEnhancedClient();
        this.tableName = getTableName();
        if (this.tableName == null || this.tableName.isEmpty()) {
            throw new IllegalStateException("Could not determine Games table name from System Property or Environment Variable '" + GAMES_TABLE_NAME_ENV_VAR + "'");
        }
        this.gameTable = enhancedClient.table(this.tableName, TableSchema.fromBean(Game.class));
        this.statusIndex = enhancedClient.table(this.tableName, TableSchema.fromBean(Game.class))
                                         .index(STATUS_CREATED_AT_INDEX);
        logger.info("Initialized GameDao for table: {}", this.tableName);
    }

    @Override
    public void saveGame(Game game) {
        try {
            logger.debug("Attempting to save game: {}", game.getGameID());
            gameTable.putItem(game);
            logger.info("Successfully saved game: {}", game.getGameID());
        } catch (ConditionalCheckFailedException e) {
            logger.warn("Conditional check failed while saving game {}: {}", game.getGameID(), e.getMessage());
            // This might be expected if using conditional writes (e.g., for optimistic locking)
            // Re-throw or wrap in a custom exception if needed by the service layer
            throw new RuntimeException("Optimistic lock failed or condition not met saving game " + game.getGameID(), e);
        } catch (DynamoDbException e) {
            logger.error("DynamoDbException saving game {}: {}", game.getGameID(), e.getMessage(), e);
            throw new RuntimeException("Failed to save game " + game.getGameID(), e);
        } catch (Exception e) {
            logger.error("Unexpected error saving game {}: {}", game.getGameID(), e.getMessage(), e);
            throw new RuntimeException("Unexpected error saving game " + game.getGameID(), e);
        }
    }

    @Override
    public Optional<Game> getGameById(String gameId) {
        try {
            logger.debug("Attempting to get game by ID: {}", gameId);
            Game game = gameTable.getItem(Key.builder().partitionValue(gameId).build());
            logger.debug("Retrieved game by ID: {}. Found: {}", gameId, game != null);
            return Optional.ofNullable(game);
        } catch (DynamoDbException e) {
            logger.error("DynamoDbException getting game by ID {}: {}", gameId, e.getMessage(), e);
            return Optional.empty(); // Return empty on DB error, service layer can decide how to handle
        } catch (Exception e) {
            logger.error("Unexpected error getting game by ID {}: {}", gameId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public List<Game> listGamesByStatus(String status) {
        try {
            logger.debug("Listing games with status: {} using index: {}", status, STATUS_CREATED_AT_INDEX);
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(status).build());
            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                                                                 .queryConditional(queryConditional)
                                                                 .scanIndexForward(false) // Sort by CreatedAt descending
                                                                 .build();

            List<Game> games = statusIndex.query(request).stream()
                                           .flatMap(page -> page.items().stream())
                                           .collect(Collectors.toList());
            logger.debug("Found {} games with status: {}", games.size(), status);
            return games;
        } catch (DynamoDbException e) {
            logger.error("DynamoDbException listing games by status {}: {}", status, e.getMessage(), e);
            return Collections.emptyList();
        } catch (Exception e) {
            logger.error("Unexpected error listing games by status {}: {}", status, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public int countGamesPlayedByPlayer(String playerId) throws GamePersistenceException {
        logger.warn("Counting games played by player {} using scan - inefficient!", playerId);
        // Assumption: Game model has a List<String> playerIds attribute.
        // This requires scanning the entire table.
        try {
            Expression filterExpression = Expression.builder()
                .expression("contains(playerIds, :pId)")
                .putExpressionValue(":pId", AttributeValue.builder().s(playerId).build())
                .build();
                
            ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(filterExpression)
                .build();
                
            // Count items across all pages of the scan result.
            long count = gameTable.scan(scanRequest).items().stream().count();
            logger.debug("Found {} games played by player {}", count, playerId);
            return (int) count;
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error counting games for player {}: {}", playerId, e.awsErrorDetails().errorMessage(), e);
            throw new GamePersistenceException("Failed to count games played", e);
        } catch (Exception e) {
             logger.error("Unexpected error counting games for player {}: {}", playerId, e.getMessage(), e);
             throw new GamePersistenceException("Unexpected error counting games played", e);
        }
    }

    @Override
    public int countWinsByPlayer(String playerId) throws GamePersistenceException {
        logger.warn("Counting wins by player {} using scan - inefficient!", playerId);
        // Assumption: Game model has a String winnerId attribute.
        // This requires scanning the entire table.
        try {
            Expression filterExpression = Expression.builder()
                .expression("winnerId = :pId")
                .putExpressionValue(":pId", AttributeValue.builder().s(playerId).build())
                .build();
                
            ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(filterExpression)
                .build();
                
            long count = gameTable.scan(scanRequest).items().stream().count();
             logger.debug("Found {} wins for player {}", count, playerId);
            return (int) count;
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error counting wins for player {}: {}", playerId, e.awsErrorDetails().errorMessage(), e);
            throw new GamePersistenceException("Failed to count wins", e);
         } catch (Exception e) {
             logger.error("Unexpected error counting wins for player {}: {}", playerId, e.getMessage(), e);
             throw new GamePersistenceException("Unexpected error counting wins", e);
        }
    }

    private String getTableName() {
        String systemPropTableName = System.getProperty(GAMES_TABLE_NAME_ENV_VAR);
        if (systemPropTableName != null && !systemPropTableName.isEmpty()) {
            logger.info("Using games table name from system property: {}", systemPropTableName);
            return systemPropTableName;
        }

        String envTableName = System.getenv(GAMES_TABLE_NAME_ENV_VAR);
        if (envTableName != null && !envTableName.isEmpty()) {
            logger.info("Using games table name from environment variable: {}", envTableName);
            return envTableName;
        }
        
        String defaultTable = "dev-Games";
        logger.warn("'{}' system property or environment variable not set, using default '{}'", 
                    GAMES_TABLE_NAME_ENV_VAR, defaultTable);
        return defaultTable;
    }
} 