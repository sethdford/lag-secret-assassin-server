package com.assassin.dao;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.PlayerPersistenceException;
import com.assassin.model.Player;
import com.assassin.util.DynamoDbClientProvider;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest; // Needed for DescribeTable
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
public class DynamoDbPlayerDao implements PlayerDao {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbPlayerDao.class);
    private static final String PLAYER_TABLE_ENV_VAR = "PLAYERS_TABLE_NAME"; // Store the env var *name*
    private static final String EMAIL_INDEX_NAME = "EmailIndex";
    private static final String KILL_COUNT_INDEX_NAME = "KillCountIndex";

    private final DynamoDbTable<Player> playerTable;
    private final DynamoDbIndex<Player> emailIndex;
    private final DynamoDbIndex<Player> killCountIndex;
    private final DynamoDbEnhancedClient enhancedClient;
    private final String tableName; // Store table name for DescribeTable

    public DynamoDbPlayerDao() {
        this.enhancedClient = DynamoDbClientProvider.getDynamoDbEnhancedClient();
        this.tableName = getTableName(); // Get table name once using the helper
        if (this.tableName == null || this.tableName.isEmpty()) {
            // Throw exception earlier if table name is missing
            throw new IllegalStateException("Could not determine Players table name from System Property or Environment Variable '" + PLAYER_TABLE_ENV_VAR + "'");
        }
        logger.info("Initializing DynamoDbPlayerDao with table: {}", this.tableName);
        this.playerTable = enhancedClient.table(this.tableName, TableSchema.fromBean(Player.class));
        this.emailIndex = playerTable.index(EMAIL_INDEX_NAME);
        this.killCountIndex = playerTable.index(KILL_COUNT_INDEX_NAME);
    }

    @Override
    public Optional<Player> getPlayerById(String playerId) {
        logger.debug("Getting player by ID: {}", playerId);
        try {
            Key key = Key.builder().partitionValue(playerId).build();
            return Optional.ofNullable(playerTable.getItem(key));
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error getting player {}: {}", playerId, e.getMessage(), e);
            throw new PlayerPersistenceException("Error retrieving player from DynamoDB", e);
        }
    }
    
    @Override
    public Player findPlayerById(String playerID) {
         return getPlayerById(playerID).orElse(null);
    }
    
    @Override
    public Player findPlayerByEmail(String email) {
        logger.debug("Attempting to find player by email: {}", email);
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(email).build());
            var results = emailIndex.query(QueryEnhancedRequest.builder().queryConditional(queryConditional).limit(1).build());
            
            List<Player> players = results.stream().flatMap(page -> page.items().stream()).collect(Collectors.toList());
            
            if (!players.isEmpty()) {
                logger.debug("Found player by email: {}", players.get(0));
                return players.get(0);
            }
            logger.debug("No player found for email: {}", email);
            return null;
        } catch (DynamoDbException e) {
            String errorMessage = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
            logger.error("Error querying EmailIndex for email {}: {}", email, errorMessage, e);
            // Propagate as unchecked exception or custom persistence exception
            throw new PlayerPersistenceException("Error finding player by email", e);
        }
    }

    @Override
    public void savePlayer(Player player) {
        logger.debug("Saving player with ID: {}", player.getPlayerID());
        try {
            playerTable.putItem(player);
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error saving player {}: {}", player.getPlayerID(), e.getMessage(), e);
            throw new PlayerPersistenceException("Error saving player to DynamoDB", e);
        }
    }

    @Override
    public List<Player> getAllPlayers() {
        logger.debug("Getting all players from table: {}", tableName);
        try {
            ScanEnhancedRequest request = ScanEnhancedRequest.builder().build();
            return playerTable.scan(request).items().stream().toList();
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error scanning players: {}", e.getMessage(), e);
            throw new PlayerPersistenceException("Error retrieving all players from DynamoDB", e);
        }
    }

    @Override
    public void deletePlayer(String playerId) {
        logger.info("Deleting player with ID: {}", playerId);
        try {
            Key key = Key.builder().partitionValue(playerId).build();
            
            Optional<Player> existingPlayer = getPlayerById(playerId);
            if (existingPlayer.isEmpty()) {
                logger.warn("Attempted to delete non-existent player: {}", playerId);
                throw new PlayerNotFoundException("Player not found with ID: " + playerId);
            }
            
            DeleteItemEnhancedRequest deleteRequest = DeleteItemEnhancedRequest.builder()
                .key(key)
                .build();
            playerTable.deleteItem(deleteRequest);
            logger.info("Successfully deleted player: {}", playerId);
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error deleting player {}: {}", playerId, e.getMessage(), e);
            throw new PlayerPersistenceException("Error deleting player from DynamoDB", e);
        }
    }

    @Override
    public long getPlayerCount() throws PlayerPersistenceException {
        logger.debug("Getting approximate player count for table: {}", this.tableName);
        try {
            DynamoDbClient ddbClient = DynamoDbClientProvider.getClient(); // Get the standard client
            DescribeTableRequest request = DescribeTableRequest.builder()
                                                               .tableName(this.tableName)
                                                               .build();
            DescribeTableResponse response = ddbClient.describeTable(request);
            long count = response.table().itemCount();
            logger.info("Approximate player count for table {}: {}", this.tableName, count);
            return count;
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error describing table {}: {}", this.tableName, e.getMessage(), e);
            throw new PlayerPersistenceException("Failed to describe table to get player count", e);
        } catch (Exception e) { // Catch broader exceptions for unexpected errors
             logger.error("Unexpected error getting player count for table {}: {}", this.tableName, e.getMessage(), e);
            throw new PlayerPersistenceException("Unexpected error getting player count", e);
        }
    }

    @Override
    public List<Player> getLeaderboardByKillCount(String statusPartitionKey, int limit) {
        logger.debug("Querying KillCountIndex with partitionKey='{}' and limit={}", statusPartitionKey, limit);
        try {
             QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder()
                .partitionValue(statusPartitionKey)
                .build());
                
            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // Sort descending (highest kill count first)
                .limit(limit)
                .build();

            var results = killCountIndex.query(queryRequest);
            
            List<Player> topPlayers = results.stream()
                                            .flatMap(page -> page.items().stream())
                                            .collect(Collectors.toList());
                                            
            logger.debug("Found {} players on KillCountIndex leaderboard", topPlayers.size());
            return topPlayers;
        } catch (DynamoDbException e) {
            String errorMessage = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
            logger.error("Error querying KillCountIndex for partitionKey {}: {}", statusPartitionKey, errorMessage, e);
            // Propagate as unchecked exception or custom persistence exception
            throw new PlayerPersistenceException("Error getting leaderboard", e);
        }
    }

    /**
     * Increments the kill count for a player atomically.
     * Matches the signature in PlayerDao.
     *
     * @param playerId The ID of the player whose kill count should be incremented.
     * @return The updated kill count.
     * @throws PlayerPersistenceException if the update operation fails.
     * @throws PlayerNotFoundException if the player does not exist.
     */
    @Override
    public int incrementPlayerKillCount(String playerId) throws PlayerPersistenceException, PlayerNotFoundException {
        logger.debug("Attempting to increment kill count for player ID: {}", playerId);
        try {
            // First check if the player exists
            Optional<Player> existingPlayer = getPlayerById(playerId);
            if (existingPlayer.isEmpty()) {
                throw new PlayerNotFoundException("Player not found with ID: " + playerId);
            }
            
            // Get the current player and update the kill count
            Player player = existingPlayer.get();
            int currentKillCount = player.getKillCount();
            player.setKillCount(currentKillCount + 1);
            
            // Save the updated player
            playerTable.updateItem(player);
            
            logger.info("Successfully incremented kill count for player ID {} to {}", playerId, player.getKillCount());
            return player.getKillCount();
        } catch (PlayerNotFoundException e) {
            throw e;
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error incrementing kill count for player {}: {}", playerId, e.getMessage(), e);
            throw new PlayerPersistenceException("Error incrementing kill count", e);
        } catch (Exception e) {
            logger.error("Unexpected error incrementing kill count for player {}: {}", playerId, e.getMessage(), e);
            throw new PlayerPersistenceException("Unexpected error incrementing kill count", e);
        }
    }

    // Helper method to get table name from system property or environment or default
    private String getTableName() {
        String systemPropTableName = System.getProperty(PLAYER_TABLE_ENV_VAR);
        if (systemPropTableName != null && !systemPropTableName.isEmpty()) {
            logger.info("Using players table name from system property: {}", systemPropTableName);
            return systemPropTableName;
        }

        String envTableName = System.getenv(PLAYER_TABLE_ENV_VAR);
        if (envTableName != null && !envTableName.isEmpty()) {
             logger.info("Using players table name from environment variable: {}", envTableName);
             return envTableName;
        }
        
        String defaultTable = "dev-Players"; // Default if not set
        logger.warn("'{}' system property or environment variable not set, using default '{}'", 
                    PLAYER_TABLE_ENV_VAR, defaultTable);
        return defaultTable;
    }
} 