package com.assassin.dao;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.exception.KillNotFoundException;
import com.assassin.exception.KillPersistenceException;
import com.assassin.model.Kill;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;
import com.assassin.util.DynamoDbClientProvider;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;

/**
 * DynamoDB implementation of the KillDao interface using AWS SDK v2 Enhanced Client.
 */
public class DynamoDbKillDao implements KillDao {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbKillDao.class);
    private static final String KILLS_TABLE_NAME_ENV_VAR = "KILLS_TABLE_NAME";
    private static final String VICTIM_ID_TIME_INDEX = "VictimID-Time-index"; // GSI name
    private static final String GAME_ID_TIME_INDEX_NAME = "GameID-Time-index"; // New GSI name
    private static final String STATUS_TIME_INDEX_NAME = "StatusTimeIndex"; // GSI for recent kills

    private final DynamoDbTable<Kill> killTable;
    private final DynamoDbIndex<Kill> victimIndex;
    private final DynamoDbIndex<Kill> gameIndex; // Index for game-specific kills
    private final DynamoDbIndex<Kill> statusTimeIndex; // Index for recent kills
    private final String tableName;
    private final DynamoDbEnhancedClient enhancedClient;
    private final PlayerDao playerDao;

    /**
     * Constructor that initializes the DynamoDB Enhanced Client and Kill table/index.
     */
    public DynamoDbKillDao() {
        // Use the client provider
        enhancedClient = DynamoDbClientProvider.getDynamoDbEnhancedClient();
        this.playerDao = new DynamoDbPlayerDao();

        this.tableName = getTableName();
        this.killTable = enhancedClient.table(this.tableName, TableSchema.fromBean(Kill.class));
        this.victimIndex = this.killTable.index(VICTIM_ID_TIME_INDEX);
        this.gameIndex = this.killTable.index(GAME_ID_TIME_INDEX_NAME);
        this.statusTimeIndex = this.killTable.index(STATUS_TIME_INDEX_NAME);
        logger.info("Initialized KillDao for table: {} with indexes: {}, {}, {}",
                    this.tableName, VICTIM_ID_TIME_INDEX, GAME_ID_TIME_INDEX_NAME, STATUS_TIME_INDEX_NAME);
    }

    /**
     * Saves a kill record to the database.
     *
     * @param kill The Kill object to save.
     * @throws KillPersistenceException if the save operation fails.
     */
    @Override
    public void saveKill(Kill kill) throws KillPersistenceException {
        try {
            logger.debug("Attempting to save kill record for killer: {}, victim: {}", kill.getKillerID(), kill.getVictimID());
            // Set the partition key for the StatusTimeIndex GSI
            if (kill.getVerificationStatus() != null) {
                // Using status directly. Consider sharding if one status dominates (e.g., adding _v1)
                kill.setKillStatusPartition(kill.getVerificationStatus()); 
            } else {
                // Fallback or default partition if status is null (shouldn't happen ideally)
                kill.setKillStatusPartition("UNKNOWN"); 
                logger.warn("Kill object missing verificationStatus, setting KillStatusPartition to UNKNOWN for Killer={}, Time={}",
                            kill.getKillerID(), kill.getTime());
            }
            
            killTable.putItem(kill);
            logger.info("Successfully saved kill record for killer: {}, victim: {}", kill.getKillerID(), kill.getVictimID());
        } catch (DynamoDbException e) {
            logger.error("DynamoDbException saving kill record: {}", e.getMessage(), e);
            throw new KillPersistenceException("Failed to save kill record: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.error("Unexpected runtime error saving kill record: {}", e.getMessage(), e);
            throw new KillPersistenceException("Unexpected runtime error saving kill record: " + e.getMessage(), e);
        }
    }

    /**
     * Finds all kills performed by a specific killer, ordered by time descending.
     *
     * @param killerID The ID of the killer.
     * @return A list of Kill objects.
     * @throws KillNotFoundException if no kills are found for the specified killer.
     * @throws KillPersistenceException if the query operation fails.
     */
    @Override
    public List<Kill> findKillsByKiller(String killerID) throws KillNotFoundException, KillPersistenceException {
        try {
            logger.debug("Finding kills by killer ID: {}", killerID);
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(killerID).build());
            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                                                                 .queryConditional(queryConditional)
                                                                 .scanIndexForward(false) // Sort by Time descending
                                                                 .build();

            List<Kill> kills = killTable.query(request).items().stream().collect(Collectors.toList());
            logger.debug("Found {} kills for killer {}", kills.size(), killerID);
            
            if (kills.isEmpty()) {
                throw new KillNotFoundException("No kills found for killer: " + killerID);
            }
            
            return kills;
        } catch (KillNotFoundException e) {
            // Re-throw KillNotFoundException as-is
            throw e;
        } catch (DynamoDbException e) {
            logger.error("DynamoDbException finding kills by killer {}: {}", killerID, e.getMessage(), e);
            throw new KillPersistenceException("Database error finding kills by killer: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.error("Unexpected runtime error finding kills by killer {}: {}", killerID, e.getMessage(), e);
            throw new KillPersistenceException("Unexpected runtime error finding kills by killer: " + e.getMessage(), e);
        }
    }

    /**
     * Finds all kills where a specific player was the victim, ordered by time descending.
     * Requires the VictimID-Time-index GSI.
     *
     * @param victimID The ID of the victim.
     * @return A list of Kill objects.
     * @throws KillNotFoundException if no kills are found for the specified victim.
     * @throws KillPersistenceException if the query operation fails.
     */
    @Override
    public List<Kill> findKillsByVictim(String victimID) throws KillNotFoundException, KillPersistenceException {
        try {
            logger.debug("Finding kills by victim ID: {} using index: {}", victimID, VICTIM_ID_TIME_INDEX);
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(victimID).build());
            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                                                                 .queryConditional(queryConditional)
                                                                 .scanIndexForward(false) // Sort by Time descending
                                                                 // Cannot use consistent reads on GSIs - not supported by DynamoDB
                                                                 .build();

            // Query the GSI
            List<Kill> kills = victimIndex.query(request).stream()
                                            .flatMap(page -> page.items().stream())
                                            .collect(Collectors.toList());
            logger.debug("Found {} kills for victim {} using index", kills.size(), victimID);
            
            if (kills.isEmpty()) {
                throw new KillNotFoundException("No kills found for victim: " + victimID);
            }
            
            return kills;
        } catch (KillNotFoundException e) {
            // Re-throw KillNotFoundException as-is
            throw e;
        } catch (DynamoDbException e) {
            logger.error("DynamoDbException finding kills by victim {}: {}", victimID, e.getMessage(), e);
            throw new KillPersistenceException("Database error finding kills by victim: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.error("Unexpected runtime error finding kills by victim {}: {}", victimID, e.getMessage(), e);
            throw new KillPersistenceException("Unexpected runtime error finding kills by victim: " + e.getMessage(), e);
        }
    }
    
    /**
     * Finds the most recent N kills, ordered by time descending.
     * Uses the StatusTimeIndex GSI, querying the 'VERIFIED' partition.
     *
     * @param limit The maximum number of kills to return.
     * @return A list of Kill objects.
     * @throws KillNotFoundException if no verified kills are found.
     * @throws KillPersistenceException if the query operation fails.
     */
    @Override
    public List<Kill> findRecentKills(int limit) throws KillNotFoundException, KillPersistenceException {
        try {
            // Query the StatusTimeIndex GSI for VERIFIED kills, newest first
            String verifiedPartitionKey = "VERIFIED"; // Query only verified kills for public feed
            logger.debug("Finding most recent {} kills using index '{}' with partition key '{}'",
                         limit, STATUS_TIME_INDEX_NAME, verifiedPartitionKey);
            
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder()
                                                                                .partitionValue(verifiedPartitionKey)
                                                                                .build());
            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                                                                 .queryConditional(queryConditional)
                                                                 .scanIndexForward(false) // Sort by Time descending
                                                                 .limit(limit)
                                                                 .build();

            // Query the GSI
            List<Kill> recentKills = statusTimeIndex.query(request).stream()
                                                    .flatMap(page -> page.items().stream())
                                                    .limit(limit) // Ensure limit is applied across pages
                                                    .collect(Collectors.toList());
            
            logger.debug("Found {} recent verified kills using index", recentKills.size());
            
            if (recentKills.isEmpty()) {
                throw new KillNotFoundException("No recent verified kills found");
            }
            
            return recentKills;
        } catch (KillNotFoundException e) {
            // Re-throw KillNotFoundException as-is
            throw e;
        } catch (DynamoDbException e) {
            logger.error("DynamoDbException finding recent kills using index {}: {}", STATUS_TIME_INDEX_NAME, e.getMessage(), e);
            throw new KillPersistenceException("Database error finding recent kills: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.error("Unexpected runtime error finding recent kills using index {}: {}", STATUS_TIME_INDEX_NAME, e.getMessage(), e);
            throw new KillPersistenceException("Unexpected runtime error finding recent kills: " + e.getMessage(), e);
        }
    }

    /**
     * Counts the number of times a player has been killed (appeared as victim).
     * Requires the VictimID-Time-index GSI.
     *
     * @param victimId The ID of the player (victim).
     * @return The total count of deaths for the player.
     * @throws KillPersistenceException if the query operation fails.
     */
    @Override
    public int getPlayerDeathCount(String victimId) throws KillPersistenceException {
        try {
            logger.debug("Counting deaths for victim ID: {} using index: {}", victimId, VICTIM_ID_TIME_INDEX);
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(victimId).build());
            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                                                                 .queryConditional(queryConditional)
                                                                 // Project only the partition key to minimize data transfer/cost
                                                                 .attributesToProject("VictimID") 
                                                                 .build();

            // Query the GSI and iterate through pages to count items
            int deathCount = 0;
            for (var page : victimIndex.query(request)) {
                 deathCount += page.items().size(); // Add count from current page
            }
                                                
            logger.debug("Found {} deaths for victim {}", deathCount, victimId);
            return deathCount;
        } catch (DynamoDbException e) {
            logger.error("DynamoDbException counting deaths for victim {}: {}", victimId, e.getMessage(), e);
            throw new KillPersistenceException("Database error counting deaths: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.error("Unexpected runtime error counting deaths for victim {}: {}", victimId, e.getMessage(), e);
            throw new KillPersistenceException("Unexpected runtime error counting deaths: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all kills from the database.
     * WARNING: This performs a table scan and can be inefficient for large tables.
     *
     * @return A list of all Kill objects.
     * @throws KillPersistenceException if the query operation fails.
     */
    @Override
    public List<Kill> getAllKills() throws KillPersistenceException {
        logger.warn("Executing getAllKills - performing full table scan on {}. Consider alternatives for large tables.", tableName);
        try {
            ScanEnhancedRequest request = ScanEnhancedRequest.builder().build();
            return killTable.scan(request).items().stream().collect(Collectors.toList());
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error scanning kills table: {}", e.getMessage(), e);
            throw new KillPersistenceException("Error retrieving all kills from DynamoDB", e);
        }
    }

    /**
     * Retrieves a specific kill by its composite key (killer ID and time).
     *
     * @param killerId The ID of the killer (partition key).
     * @param time The timestamp of the kill (sort key).
     * @return An Optional containing the Kill if found, otherwise empty.
     * @throws KillPersistenceException if the query operation fails.
     */
    @Override
    public Optional<Kill> getKill(String killerId, String time) throws KillPersistenceException {
        logger.debug("Getting kill by composite key: KillerID={}, Time={}", killerId, time);
        try {
            Key key = Key.builder()
                         .partitionValue(killerId)
                         .sortValue(time)
                         .build();
            return Optional.ofNullable(killTable.getItem(key));
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error getting kill {}/{}: {}", killerId, time, e.getMessage(), e);
            throw new KillPersistenceException("Error retrieving kill from DynamoDB", e);
        }
    }

    /**
     * Gets the approximate total number of kills in the table using DescribeTable.
     *
     * @return The approximate count of kills.
     * @throws KillPersistenceException If there's an error communicating with DynamoDB.
     */
    @Override
    public long getKillCount() throws KillPersistenceException {
        logger.debug("Getting approximate kill count for table: {}", this.tableName);
        try {
            DynamoDbClient ddbClient = DynamoDbClientProvider.getClient(); // Corrected method name
            DescribeTableRequest request = DescribeTableRequest.builder()
                                                               .tableName(this.tableName)
                                                               .build();
            DescribeTableResponse response = ddbClient.describeTable(request);
            long count = response.table().itemCount();
            logger.info("Approximate kill count for table {}: {}", this.tableName, count);
            return count;
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error describing kill table {}: {}", this.tableName, e.getMessage(), e);
            throw new KillPersistenceException("Failed to describe table to get kill count", e);
        } catch (RuntimeException e) {
            logger.error("Unexpected runtime error getting kill count for table {}: {}", this.tableName, e.getMessage(), e);
            throw new KillPersistenceException("Unexpected runtime error getting kill count", e);
        }
    }

    /**
     * Counts the total number of times a player has been killed (i.e., was the victim)
     * by querying the VictimID-Time-index using a COUNT query.
     *
     * @param victimId The ID of the player.
     * @return The total number of deaths for the player.
     * @throws KillPersistenceException If there's an error counting deaths.
     */
    @Override
    public int countDeathsByVictim(String victimId) throws KillPersistenceException {
        logger.debug("Counting deaths for victim: {}", victimId);
        try {
            // Use the low-level client for an efficient COUNT query
            DynamoDbClient ddbClient = DynamoDbClientProvider.getClient();
            QueryRequest lowLevelQuery = QueryRequest.builder()
                .tableName(this.tableName)
                .indexName(VICTIM_ID_TIME_INDEX)
                .keyConditionExpression("VictimID = :v_id")
                .expressionAttributeValues(Map.of(":v_id", AttributeValue.builder().s(victimId).build()))
                .select(Select.COUNT) // Only get the count
                .build();
                
            QueryResponse response = ddbClient.query(lowLevelQuery);
            int count = response.count();

            logger.debug("Found {} deaths for victim {}", count, victimId);
            return count;
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error counting deaths for victim {}: {}", victimId, e.awsErrorDetails().errorMessage(), e);
            throw new KillPersistenceException("Failed to count deaths by victim", e);
        } catch (RuntimeException e) {
            logger.error("Unexpected runtime error counting deaths for victim {}: {}", victimId, e.getMessage(), e);
            throw new KillPersistenceException("Unexpected runtime error counting deaths by victim", e);
        }
    }

    /**
     * Checks if a player is currently considered "alive" in a specific game.
     * This implementation checks the player's status in the Player table.
     * 
     * @param playerId The ID of the player.
     * @param gameId The ID of the game context.
     * @return true if the player is found, belongs to the game, and has an "ACTIVE" status, false otherwise.
     * @throws KillPersistenceException if there is an error accessing player data.
     */
    @Override
    public boolean isPlayerAlive(String playerId, String gameId) throws KillPersistenceException {
        try {
            logger.debug("Checking if player {} is alive in game {}", playerId, gameId);
            Optional<Player> playerOpt = playerDao.getPlayerById(playerId);

            if (playerOpt.isPresent()) {
                Player player = playerOpt.get();
                // Check if player belongs to the correct game and is ACTIVE
                boolean alive = gameId.equals(player.getGameID()) && PlayerStatus.ACTIVE.name().equals(player.getStatus());
                logger.debug("Player {} in game {} found. Game Match: {}, Status: {}, Alive: {}", 
                             playerId, gameId, gameId.equals(player.getGameID()), player.getStatus(), alive);
                return alive;
            } else {
                logger.warn("Player {} not found when checking alive status for game {}.{}", playerId, gameId,
                            playerId, gameId);
                return false; // Player not found, definitely not alive in this context
            }
        } catch (RuntimeException e) {
            // Catching general exception from playerDao interaction
            logger.error("Error checking if player {} is alive in game {}: {}", playerId, gameId, e.getMessage(), e);
            // Wrap in KillPersistenceException as this DAO method declares it
            throw new KillPersistenceException("Failed to check player status: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a list of recent kills across all players.
     *
     * @param limit The maximum number of recent kills to retrieve.
     * @return A list of the most recent Kill objects.
     * @throws KillPersistenceException If there's an error retrieving recent kills.
     */
    @Override
    public List<Kill> getRecentKills(int limit) throws KillPersistenceException {
        try {
            logger.debug("Getting {} most recent kills", limit);
            ScanEnhancedRequest request = ScanEnhancedRequest.builder()
                .limit(limit)
                .build();
                
            List<Kill> kills = killTable.scan(request)
                .items()
                .stream()
                .sorted(Comparator.comparing(Kill::getTime).reversed())
                .limit(limit)
                .collect(Collectors.toList());
                
            logger.debug("Found {} recent kills", kills.size());
            return kills;
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error retrieving recent kills: {}", e.getMessage(), e);
            throw new KillPersistenceException("Error retrieving recent kills from DynamoDB", e);
        }
    }

    /**
     * Finds all kill records associated with a specific game ID using the GSI.
     *
     * @param gameId The ID of the game.
     * @return A list of Kill objects for the specified game, potentially empty.
     */
    @Override
    public List<Kill> findKillsByGameId(String gameId) {
        logger.debug("Finding kills for gameID: {}", gameId);
        try {
            DynamoDbIndex<Kill> gameIndex = enhancedClient.table(getTableName(), TableSchema.fromBean(Kill.class))
                                                    .index(GAME_ID_TIME_INDEX_NAME);
                                                    
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder()
                .partitionValue(gameId)
                .build());
                
            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(true) // Optional: Sort by time ascending for timeline
                .build();

            var results = gameIndex.query(queryRequest);
            
            List<Kill> kills = results.stream()
                                    .flatMap(page -> page.items().stream())
                                    .collect(Collectors.toList());
                                            
            logger.info("Found {} kills for gameID: {}", kills.size(), gameId);
            return kills;
            
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error querying GameID-Time-index for game {}: {}", gameId, e.getMessage(), e);
            throw new KillPersistenceException("Error finding kills by game ID", e);
        }
    }

    /**
     * Finds the kill record for a given victim within a specific game context.
     * Queries the VictimID-Time-index GSI for the most recent kill of the victim,
     * then filters by game ID.
     *
     * @param victimId The ID of the victim.
     * @param gameId The ID of the game to scope the search.
     * @return An Optional containing the Kill record if found, otherwise empty.
     * @throws KillPersistenceException if the query operation fails.
     */
    @Override
    public Optional<Kill> findKillRecordByVictimAndGame(String victimId, String gameId) throws KillPersistenceException {
        logger.debug("Finding kill record for victim {} in game {} using index {}", victimId, gameId, VICTIM_ID_TIME_INDEX);
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(victimId).build());
            
            // Build the query request
            QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                                                                                .queryConditional(queryConditional)
                                                                                .scanIndexForward(false) // Get the most recent kill first
                                                                                .limit(1); // Only need the latest kill where this player was victim
            
            // Query the GSI
            List<Kill> kills = victimIndex.query(requestBuilder.build()).stream()
                                            .flatMap(page -> page.items().stream())
                                            .collect(Collectors.toList());
            
            if (!kills.isEmpty()) {
                // Filter for the specific game ID
                Optional<Kill> killInGameOpt = kills.stream()
                                                 .filter(k -> gameId.equals(k.getGameId()))
                                                 .findFirst(); 
                
                if (killInGameOpt.isPresent()) {
                    logger.debug("Found kill record for victim {} in game {}.", victimId, gameId);
                    return killInGameOpt;
                } else {
                    logger.debug("Victim {} was killed, but not found within game {}.", victimId, gameId);
                    return Optional.empty();
                }
            } else {
                logger.debug("No kill records found for victim {}", victimId);
                return Optional.empty(); // Victim not found in kill records
            }
        } catch (DynamoDbException e) {
            logger.error("DynamoDbException finding kill record for victim {}: {}", victimId, e.getMessage(), e);
            throw new KillPersistenceException("Database error finding kill record for victim: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.error("Unexpected runtime error finding kill record for victim {}: {}", victimId, e.getMessage(), e);
            throw new KillPersistenceException("Unexpected runtime error finding kill record for victim: " + e.getMessage(), e);
        }
    }

    private String getTableName() {
        // Prioritize system property (for testing) over environment variable
        String systemPropTableName = System.getProperty(KILLS_TABLE_NAME_ENV_VAR);
        if (systemPropTableName != null && !systemPropTableName.isEmpty()) {
            logger.info("Using table name from system property: {}", systemPropTableName);
            return systemPropTableName;
        }

        String envTableName = System.getenv(KILLS_TABLE_NAME_ENV_VAR); // Match env var name in template.yaml
        if (envTableName == null || envTableName.isEmpty()) {
            logger.warn("{} system property or environment variable not set, using default 'local-assassin-kills'",
                        KILLS_TABLE_NAME_ENV_VAR);
            return "local-assassin-kills"; // Use the same name as in our setup script
        }
        logger.info("Using table name from environment variable: {}", envTableName);
        return envTableName;
    }
} 