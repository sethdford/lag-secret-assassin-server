package com.assassin.dao;

import com.assassin.model.Transaction;
import com.assassin.exception.PersistenceException;

import java.util.List;
import java.util.Optional;

public interface TransactionDao {

    String PLAYER_TRANSACTIONS_INDEX_NAME = "PlayerTransactionsIndex";
    String GAME_TRANSACTIONS_INDEX_NAME = "GameTransactionsIndex"; 

    /**
     * Saves a transaction record.
     *
     * @param transaction The transaction to save.
     * @throws PersistenceException if the save operation fails.
     */
    void saveTransaction(Transaction transaction) throws PersistenceException;

    /**
     * Retrieves a transaction by its ID.
     *
     * @param transactionId The ID of the transaction.
     * @return An Optional containing the transaction if found, or empty otherwise.
     * @throws PersistenceException if the retrieval fails.
     */
    Optional<Transaction> getTransactionById(String transactionId) throws PersistenceException;

    /**
     * Retrieves all transactions for a specific player, ordered by creation date (newest first).
     *
     * @param playerId The ID of the player.
     * @param limit The maximum number of transactions to return.
     * @param exclusiveStartKey Optional. The transaction ID to start after for pagination.
     * @return A list of transactions for the player.
     * @throws PersistenceException if the retrieval fails.
     */
    List<Transaction> getTransactionsByPlayerId(String playerId, int limit, String exclusiveStartKey) throws PersistenceException;

    /**
     * Retrieves all transactions for a specific game, ordered by creation date (newest first).
     *
     * @param gameId The ID of the game.
     * @param limit The maximum number of transactions to return.
     * @param exclusiveStartKey Optional. The transaction ID to start after for pagination.
     * @return A list of transactions for the game.
     * @throws PersistenceException if the retrieval fails.
     */
    List<Transaction> getTransactionsByGameId(String gameId, int limit, String exclusiveStartKey) throws PersistenceException;

    /**
     * Retrieves transactions for a player within a specific game, ordered by creation date.
     *
     * @param playerId The ID of the player.
     * @param gameId The ID of the game.
     * @param limit The maximum number of transactions to return.
     * @param exclusiveStartKey Optional. The transaction ID to start after for pagination.
     * @return A list of transactions for the player in that game.
     * @throws PersistenceException if the retrieval fails.
     */
    List<Transaction> getTransactionsByPlayerAndGameId(String playerId, String gameId, int limit, String exclusiveStartKey) throws PersistenceException;

     /**
     * Updates the status of a transaction.
     *
     * @param transactionId The ID of the transaction to update.
     * @param newStatus The new status for the transaction.
     * @param gatewayTransactionId Optional. The gateway's transaction ID, if updated during this status change.
     * @param paymentMethodDetails Optional. Updated payment method details.
     * @return The updated Transaction object, or Optional.empty() if not found.
     * @throws PersistenceException if the update operation fails.
     */
    Optional<Transaction> updateTransactionStatus(String transactionId, Transaction.TransactionStatus newStatus, 
                                                String gatewayTransactionId, String paymentMethodDetails) throws PersistenceException;

    // Consider adding methods for more complex queries, e.g., by status, type, date range, etc.
    // List<Transaction> findTransactionsByStatus(Transaction.TransactionStatus status, int limit, String exclusiveStartKey);
    // List<Transaction> findTransactionsByType(Transaction.TransactionType type, int limit, String exclusiveStartKey);

} 