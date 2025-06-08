package com.assassin.dao;

import com.assassin.model.Transaction;
import com.assassin.exception.PersistenceException;
import com.assassin.util.DynamoDbClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DynamoDbTransactionDao implements TransactionDao {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbTransactionDao.class);
    private static final String TRANSACTIONS_TABLE_NAME_ENV_VAR = "TRANSACTIONS_TABLE_NAME";
    private final DynamoDbTable<Transaction> transactionTable;
    private final DynamoDbIndex<Transaction> playerTransactionsIndex;
    private final DynamoDbIndex<Transaction> gameTransactionsIndex;
    private final String tableName;

    public DynamoDbTransactionDao() {
        DynamoDbEnhancedClient enhancedClient = DynamoDbClientProvider.getDynamoDbEnhancedClient();
        this.tableName = getTableNameFromEnv();
        this.transactionTable = enhancedClient.table(this.tableName, TableSchema.fromBean(Transaction.class));
        this.playerTransactionsIndex = transactionTable.index(PLAYER_TRANSACTIONS_INDEX_NAME);
        this.gameTransactionsIndex = transactionTable.index(GAME_TRANSACTIONS_INDEX_NAME);
        logger.info("Initialized TransactionDao for table: {}", this.tableName);
    }

    public DynamoDbTransactionDao(DynamoDbEnhancedClient enhancedClient) {
        this.tableName = getTableNameFromEnv();
        this.transactionTable = enhancedClient.table(this.tableName, TableSchema.fromBean(Transaction.class));
        this.playerTransactionsIndex = transactionTable.index(PLAYER_TRANSACTIONS_INDEX_NAME);
        this.gameTransactionsIndex = transactionTable.index(GAME_TRANSACTIONS_INDEX_NAME);
        logger.info("Initialized TransactionDao with provided enhanced client for table: {}", this.tableName);
    }

    private String getTableNameFromEnv() {
        String tableNameFromEnv = System.getenv(TRANSACTIONS_TABLE_NAME_ENV_VAR);
        if (tableNameFromEnv == null || tableNameFromEnv.isEmpty()) {
            // Fallback to system property for testing
            tableNameFromEnv = System.getProperty(TRANSACTIONS_TABLE_NAME_ENV_VAR);
        }
        if (tableNameFromEnv == null || tableNameFromEnv.isEmpty()) {
            logger.error("Transactions table name environment variable '{}' is not set.", TRANSACTIONS_TABLE_NAME_ENV_VAR);
            throw new IllegalStateException("Transactions table name not configured.");
        }
        return tableNameFromEnv;
    }

    @Override
    public void saveTransaction(Transaction transaction) throws PersistenceException {
        try {
            transaction.initializeTimestamps(); // Ensure createdAt and updatedAt are set
            logger.debug("Saving transaction: {}", transaction.getTransactionId());
            transactionTable.putItem(transaction);
            logger.info("Saved transaction: {}", transaction.getTransactionId());
        } catch (DynamoDbException e) {
            logger.error("Error saving transaction {}: {}", transaction.getTransactionId(), e.getMessage(), e);
            throw new PersistenceException("Failed to save transaction", e);
        }
    }

    @Override
    public Optional<Transaction> getTransactionById(String transactionId) throws PersistenceException {
        try {
            logger.debug("Getting transaction by ID: {}", transactionId);
            return Optional.ofNullable(transactionTable.getItem(Key.builder().partitionValue(transactionId).build()));
        } catch (DynamoDbException e) {
            logger.error("Error getting transaction by ID {}: {}", transactionId, e.getMessage(), e);
            throw new PersistenceException("Failed to get transaction by ID", e);
        }
    }

    private List<Transaction> queryIndex(DynamoDbIndex<Transaction> index, QueryConditional queryConditional, int limit, String exclusiveStartTransactionId, boolean scanIndexForward) {
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(scanIndexForward);

        if (limit > 0) {
            requestBuilder.limit(limit);
        }
        if (exclusiveStartTransactionId != null && !exclusiveStartTransactionId.isEmpty()) {
            // We need the full key of the item to start after. For GSIs using CreatedAt as sort key,
            // we might need the PK of the GSI and the sort key value (CreatedAt) from the last item.
            // This simplified version assumes exclusiveStartKey is the primary key of the GSI's item if sort key isn't createdAt.
            // For GSIs PlayerTransactionsIndex & GameTransactionsIndex, SK is CreatedAt.
            // Let's assume exclusiveStartTransactionId helps to derive the lastSeenKey.
            // This part needs careful implementation based on actual GSI structure.
            // For simplicity, if 'exclusiveStartTransactionId' is the transaction ID, we'd fetch that transaction
            // and use its GSI keys for the exclusiveStartKey. This is inefficient.
            // A better approach is to return the lastEvaluatedKey from the previous query.
            // For now, this example will be basic and might not paginate GSIs perfectly without lastEvaluatedKey.
            
            // This is a placeholder. Real pagination for GSIs needs the actual GSI key values.
            // Map<String, AttributeValue> exclusiveStartKeyMap = ...; 
            // requestBuilder.exclusiveStartKey(exclusiveStartKeyMap);
            logger.warn("Pagination with exclusiveStartKey on GSI is not fully implemented in this basic DAO example without returning lastEvaluatedKey.");
        }

        return index.query(requestBuilder.build()).stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }


    @Override
    public List<Transaction> getTransactionsByPlayerId(String playerId, int limit, String exclusiveStartKey) throws PersistenceException {
        try {
            logger.debug("Getting transactions for player ID: {}, limit: {}", playerId, limit);
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(playerId).build());
             // Query GSI: PlayerId is PK, CreatedAt is SK. Scan forward false for newest first.
            return queryIndex(playerTransactionsIndex, queryConditional, limit, exclusiveStartKey, false);
        } catch (DynamoDbException e) {
            logger.error("Error getting transactions for player {}: {}", playerId, e.getMessage(), e);
            throw new PersistenceException("Failed to get transactions by player ID", e);
        }
    }

    @Override
    public List<Transaction> getTransactionsByGameId(String gameId, int limit, String exclusiveStartKey) throws PersistenceException {
        try {
            logger.debug("Getting transactions for game ID: {}, limit: {}", gameId, limit);
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(gameId).build());
            // Query GSI: GameId is PK, CreatedAt is SK. Scan forward false for newest first.
            return queryIndex(gameTransactionsIndex, queryConditional, limit, exclusiveStartKey, false);
        } catch (DynamoDbException e) {
            logger.error("Error getting transactions for game {}: {}", gameId, e.getMessage(), e);
            throw new PersistenceException("Failed to get transactions by game ID", e);
        }
    }

    @Override
    public List<Transaction> getTransactionsByPlayerAndGameId(String playerId, String gameId, int limit, String exclusiveStartKey) throws PersistenceException {
         try {
            logger.debug("Getting transactions for player ID: {} and game ID: {}, limit: {}", playerId, gameId, limit);
            // This query assumes a GSI PlayerGameTransactionsIndex with PK=PlayerID and SK=GameID
            // If SK is GameID#CreatedAt, then this key needs adjustment.
            // For simplicity, assuming PlayerGameTransactionsIndex: PK=PlayerID, SK=GameID.
            // Then one would further filter by CreatedAt client-side or adjust the GSI.
            // If PlayerGameTransactionsIndex is PK=PlayerID, SK=GameID then filter by CreatedAt client-side. This isn't ideal.
            // A GSI like: PK=`PlayerID#GameID`, SK=`CreatedAt` would be better, or PK=PlayerID, SK=`GameID#CreatedAt`

            // Given current model annotations: playerGameTransactionsIndex has PK=PlayerID and PK=GameID simultaneously on different fields,
            // and SK=CreatedAt. This is not how DynamoDB GSIs work directly.
            // Let's assume for this GSI: PK=playerId, SK starts with gameId for querying.
            // This requires a composite sort key or client-side filtering.

            // Simplest approach with current model using playerTransactionsIndex and client-side filtering by gameId:
            List<Transaction> playerTransactions = getTransactionsByPlayerId(playerId, 0, null); // Get all for player
            return playerTransactions.stream()
                    .filter(t -> gameId.equals(t.getGameId()))
                    // .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt())) // Ensure reverse chrono
                    .limit(limit > 0 ? limit : Long.MAX_VALUE)
                    .collect(Collectors.toList());
            // This is inefficient for large datasets. A dedicated GSI is better.
            // Example: GSI `PlayerGameDateIndex` with PK `PlayerID` and SK `GameID#CreatedAt`
            // Then query PK `PlayerID` and SK begins_with `GameID#`

        } catch (DynamoDbException e) {
            logger.error("Error getting transactions for player {} and game {}: {}", playerId, gameId, e.getMessage(), e);
            throw new PersistenceException("Failed to get transactions by player and game ID", e);
        }
    }

    @Override
    public Optional<Transaction> updateTransactionStatus(String transactionId, Transaction.TransactionStatus newStatus, 
                                                         String gatewayTransactionId, String paymentMethodDetails) throws PersistenceException {
        try {
            logger.debug("Updating status for transaction ID: {} to {}", transactionId, newStatus);
            
            Transaction transaction = transactionTable.getItem(Key.builder().partitionValue(transactionId).build());
            if (transaction == null) {
                logger.warn("Transaction not found for update: {}", transactionId);
                return Optional.empty();
            }

            transaction.setStatus(newStatus);
            transaction.setUpdatedAt(Instant.now().toString());
            if (gatewayTransactionId != null && !gatewayTransactionId.isEmpty()) {
                transaction.setGatewayTransactionId(gatewayTransactionId);
            }
            if (paymentMethodDetails != null && !paymentMethodDetails.isEmpty()) {
                transaction.setPaymentMethodDetails(paymentMethodDetails);
            }

            UpdateItemEnhancedRequest<Transaction> request = UpdateItemEnhancedRequest.builder(Transaction.class)
                .item(transaction)
                .build();

            Transaction updatedTransaction = transactionTable.updateItem(request);
            logger.info("Successfully updated status for transaction ID: {}", transactionId);
            return Optional.ofNullable(updatedTransaction);

        } catch (DynamoDbException e) {
            logger.error("Error updating status for transaction {}: {}", transactionId, e.getMessage(), e);
            throw new PersistenceException("Failed to update transaction status", e);
        }
    }
} 