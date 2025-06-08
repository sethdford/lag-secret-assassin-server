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
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue; // Needed for DescribeTable
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException; // Import AttributeValue

public class DynamoDbPlayerDao implements PlayerDao {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbPlayerDao.class);
    private static final String PLAYER_TABLE_ENV_VAR = "PLAYERS_TABLE_NAME"; // Store the env var *name*
    private static final String EMAIL_INDEX_NAME = "EmailIndex";
    private static final String KILL_COUNT_INDEX_NAME = "KillCountIndex";
    private static final String GAME_ID_INDEX_NAME = "GameIdIndex"; // Name of the new index
    private static final String TARGET_ID_INDEX_NAME = "TargetIdIndex"; // Name for the TargetID index

    private final DynamoDbTable<Player> playerTable;
    private final DynamoDbIndex<Player> emailIndex;
    private final DynamoDbIndex<Player> killCountIndex;
    private final DynamoDbIndex<Player> gameIdIndex; // Add index reference
    private final DynamoDbIndex<Player> targetIdIndex; // Add index reference for TargetID
    private final DynamoDbEnhancedClient enhancedClient;
    private final String tableName; // Store table name for DescribeTable

    public DynamoDbPlayerDao() {
        this.enhancedClient = DynamoDbClientProvider.getDynamoDbEnhancedClient();
        this.tableName = getTableNameFromSysPropsOrEnv(); // Get table name once using the helper
        if (this.tableName == null || this.tableName.isEmpty()) {
            // Throw exception earlier if table name is missing
            throw new IllegalStateException("Could not determine Players table name from System Property or Environment Variable '" + PLAYER_TABLE_ENV_VAR + "'");
        }
        logger.info("Initializing DynamoDbPlayerDao with table: {}", this.tableName);
        this.playerTable = enhancedClient.table(this.tableName, TableSchema.fromBean(Player.class));
        this.emailIndex = playerTable.index(EMAIL_INDEX_NAME);
        this.killCountIndex = playerTable.index(KILL_COUNT_INDEX_NAME);
        this.gameIdIndex = playerTable.index(GAME_ID_INDEX_NAME); // Initialize the index
        this.targetIdIndex = playerTable.index(TARGET_ID_INDEX_NAME); // Initialize the TargetID index
    }

    // Constructor for dependency injection (e.g., for testing)
    public DynamoDbPlayerDao(DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
        this.tableName = getTableName(); // You'll need a helper for this
        if (this.tableName == null || this.tableName.isEmpty()) {
            throw new IllegalStateException("Could not determine Players table name from environment.");
        }
        this.playerTable = enhancedClient.table(this.tableName, TableSchema.fromBean(Player.class));
        this.emailIndex = playerTable.index(EMAIL_INDEX_NAME);
        this.killCountIndex = playerTable.index(KILL_COUNT_INDEX_NAME);
        this.gameIdIndex = playerTable.index(GAME_ID_INDEX_NAME);
        this.targetIdIndex = playerTable.index(TARGET_ID_INDEX_NAME);
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

    /**
     * Updates the location details for a specific player.
     * Uses DynamoDB updateItem for efficiency, only modifying location attributes.
     *
     * @param playerId The ID of the player to update.
     * @param latitude The new latitude.
     * @param longitude The new longitude.
     * @param timestamp The timestamp of the location update (ISO 8601 format).
     * @param accuracy The accuracy of the location in meters.
     * @throws PlayerPersistenceException If the update fails.
     * @throws PlayerNotFoundException If the player does not exist (based on update condition).
     */
    @Override
    public void updatePlayerLocation(String playerId, Double latitude, Double longitude, String timestamp, Double accuracy)
            throws PlayerPersistenceException, PlayerNotFoundException {
        logger.debug("Updating location for player ID: {} - Lat={}, Lon={}, Timestamp={}, Accuracy={}", 
                   playerId, latitude, longitude, timestamp, accuracy);
        
        if (playerId == null || playerId.isEmpty()) {
            throw new IllegalArgumentException("Player ID cannot be null or empty");
        }
        // Add null checks for required location fields if necessary, or allow partial updates
        
        try {
            // Create a partial Player object containing only the primary key and the fields to update
            Player playerUpdate = new Player();
            playerUpdate.setPlayerID(playerId); // Essential: Set the partition key
            if (latitude != null) {
                playerUpdate.setLatitude(latitude); // Use renamed setter
            }
            if (longitude != null) {
                playerUpdate.setLongitude(longitude); // Use renamed setter
            }
            if (timestamp != null) {
                playerUpdate.setLocationTimestamp(timestamp);
            }
            if (accuracy != null) {
                playerUpdate.setLocationAccuracy(accuracy);
            }
            
            // Use updateItem with ignoreNulls(true) to only update provided fields.
            // This also avoids overwriting other player attributes.
            // Add a condition expression to ensure the player exists before updating.
            playerTable.updateItem(r -> r.item(playerUpdate)
                                          .ignoreNulls(true)
                                          // Condition: Ensure PlayerID exists.
                                          // If it doesn't, update fails and we might throw PlayerNotFoundException.
                                          .conditionExpression(Expression.builder()
                                                                       .expression("attribute_exists(PlayerID)")
                                                                       .build()));
            
            logger.info("Successfully updated location for player ID: {}", playerId);
            
        } catch (software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException e) {
             logger.warn("Update location failed because player not found: {}", playerId);
            throw new PlayerNotFoundException("Player not found with ID: " + playerId + " during location update.", e);
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error updating location for player {}: {}", playerId, e.getMessage(), e);
            throw new PlayerPersistenceException("Error updating player location", e);
        } catch (Exception e) {
            logger.error("Unexpected error updating location for player {}: {}", playerId, e.getMessage(), e);
            throw new PlayerPersistenceException("Unexpected error updating player location", e);
        }
    }

    @Override
    public List<Player> getPlayersByGameId(String gameId) throws PlayerPersistenceException {
        logger.debug("Getting players by game ID: {} using index: {}", gameId, GAME_ID_INDEX_NAME);
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(gameId).build());
            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                                                               .queryConditional(queryConditional)
                                                               .build();

            // Query the GSI
            List<Player> players = gameIdIndex.query(request).stream()
                                             .flatMap(page -> page.items().stream())
                                             .collect(Collectors.toList());
                                             
            logger.debug("Found {} players for game ID: {}", players.size(), gameId);
            return players;
        } catch (DynamoDbException e) {
            String errorMessage = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
            logger.error("Error querying GameIdIndex for game {}: {}", gameId, errorMessage, e);
            throw new PlayerPersistenceException("Error finding players by game ID", e);
        } catch (Exception e) { // Catch broader exceptions
            logger.error("Unexpected error getting players for game {}: {}", gameId, e.getMessage(), e);
            throw new PlayerPersistenceException("Unexpected error getting players by game ID", e);
        }
    }

    /**
     * Retrieves all players targeting a specific player within a game.
     * Uses the TargetIdIndex GSI.
     *
     * @param targetId The ID of the player being targeted.
     * @param gameId The ID of the game.
     * @return A list of players (hunters) targeting the specified player.
     * @throws PlayerPersistenceException if there is an error querying the index.
     */
    @Override
    public List<Player> getPlayersTargeting(String targetId, String gameId) throws PlayerPersistenceException {
        logger.debug("Getting players targeting player ID: {} in game ID: {} using index: {}", 
                   targetId, gameId, TARGET_ID_INDEX_NAME);
        if (targetId == null || targetId.isEmpty() || gameId == null || gameId.isEmpty()) {
             logger.warn("targetId and gameId cannot be null or empty for getPlayersTargeting");
             return List.of(); // Return empty list for invalid input
        }

        try {
            // Query based on the targetId (partition key of the GSI)
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(targetId).build());

            // Filter expression to match the gameId (assuming gameId is not part of the GSI key)
            // Adjust the attribute name "GameID" if it's different in your Player model mapping.
            Expression filterExpression = Expression.builder()
                                                    .expression("GameID = :gid") // Use mapped attribute name
                                                    .putExpressionValue(":gid", AttributeValue.builder().s(gameId).build())
                                                    .build();

            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                                                               .queryConditional(queryConditional)
                                                               .filterExpression(filterExpression) // Apply the filter
                                                               .build();

            // Query the GSI
            List<Player> players = targetIdIndex.query(request).stream()
                                                .flatMap(page -> page.items().stream())
                                                .collect(Collectors.toList());

            logger.debug("Found {} players targeting player ID: {} in game ID: {}", 
                       players.size(), targetId, gameId);
            return players;

        } catch (DynamoDbException e) {
            String errorMessage = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
            logger.error("Error querying TargetIdIndex for target {} in game {}: {}", 
                       targetId, gameId, errorMessage, e);
            throw new PlayerPersistenceException("Error finding players by target ID", e);
        } catch (Exception e) { // Catch broader exceptions
            logger.error("Unexpected error getting players targeting {} in game {}: {}", 
                       targetId, gameId, e.getMessage(), e);
            throw new PlayerPersistenceException("Unexpected error finding players by target ID", e);
        }
    }

    private String getTableName() {
        String tableName = System.getenv(PLAYER_TABLE_ENV_VAR);
        if (tableName == null || tableName.isEmpty()) {
            // In a test environment, we might not have the env var set.
            // You could also use a system property or a default.
            logger.warn("Players table name environment variable '{}' not set, using default 'Players'", PLAYER_TABLE_ENV_VAR);
            return "Players"; // Default table name for tests
        }
        return tableName;
    }

    private String getTableNameFromSysPropsOrEnv() {
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

        String defaultTable = "dev-Players";
        logger.warn("'{}' system property or environment variable not set, using default '{}'",
                PLAYER_TABLE_ENV_VAR, defaultTable);
        return defaultTable;
    }
} 