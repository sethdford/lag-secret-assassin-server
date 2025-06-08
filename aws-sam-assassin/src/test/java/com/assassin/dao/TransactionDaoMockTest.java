package com.assassin.dao;

import com.assassin.model.Transaction;
import com.assassin.exception.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TransactionDao Mock Tests")
class TransactionDaoMockTest {

    @Mock
    private TransactionDao transactionDao;

    private Transaction testTransaction;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Create test transaction
        testTransaction = createTestTransaction();
    }

    private Transaction createTestTransaction() {
        Transaction transaction = new Transaction();
        transaction.setTransactionId("txn_123456789");
        transaction.setPlayerId("player_123");
        transaction.setGameId("game_456");
        transaction.setItemId("item_789");
        transaction.setTransactionType(Transaction.TransactionType.ENTRY_FEE);
        transaction.setAmount(1000L); // $10.00 in cents
        transaction.setCurrency("USD");
        transaction.setPaymentGateway("STRIPE");
        transaction.setGatewayTransactionId("stripe_txn_123");
        transaction.setGatewayCustomerId("cus_123");
        transaction.setPaymentMethodDetails("Visa **** 4242");
        transaction.setStatus(Transaction.TransactionStatus.PENDING);
        transaction.setDescription("Entry fee for game 456");
        transaction.initializeTimestamps();
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("promo_code", "WELCOME10");
        metadata.put("source", "mobile_app");
        transaction.setMetadata(metadata);
        
        return transaction;
    }

    @Test
    @DisplayName("Should save transaction successfully")
    void shouldSaveTransactionSuccessfully() {
        // Given
        doNothing().when(transactionDao).saveTransaction(any(Transaction.class));

        // When & Then
        assertDoesNotThrow(() -> transactionDao.saveTransaction(testTransaction));
        verify(transactionDao).saveTransaction(testTransaction);
    }

    @Test
    @DisplayName("Should throw PersistenceException when save fails")
    void shouldThrowPersistenceExceptionWhenSaveFails() {
        // Given
        doThrow(new PersistenceException("Failed to save transaction"))
            .when(transactionDao).saveTransaction(any(Transaction.class));

        // When & Then
        PersistenceException exception = assertThrows(PersistenceException.class,
            () -> transactionDao.saveTransaction(testTransaction));
        
        assertTrue(exception.getMessage().contains("Failed to save transaction"));
        verify(transactionDao).saveTransaction(testTransaction);
    }

    @Test
    @DisplayName("Should return transaction when found by ID")
    void shouldReturnTransactionWhenFoundById() {
        // Given
        String transactionId = "txn_123456789";
        when(transactionDao.getTransactionById(transactionId)).thenReturn(Optional.of(testTransaction));

        // When
        Optional<Transaction> result = transactionDao.getTransactionById(transactionId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(testTransaction, result.get());
        verify(transactionDao).getTransactionById(transactionId);
    }

    @Test
    @DisplayName("Should return empty when transaction not found")
    void shouldReturnEmptyWhenTransactionNotFound() {
        // Given
        String transactionId = "txn_nonexistent";
        when(transactionDao.getTransactionById(transactionId)).thenReturn(Optional.empty());

        // When
        Optional<Transaction> result = transactionDao.getTransactionById(transactionId);

        // Then
        assertFalse(result.isPresent());
        verify(transactionDao).getTransactionById(transactionId);
    }

    @Test
    @DisplayName("Should return transactions for player")
    void shouldReturnTransactionsForPlayer() {
        // Given
        String playerId = "player_123";
        List<Transaction> transactions = Arrays.asList(testTransaction);
        when(transactionDao.getTransactionsByPlayerId(playerId, 10, null)).thenReturn(transactions);

        // When
        List<Transaction> result = transactionDao.getTransactionsByPlayerId(playerId, 10, null);

        // Then
        assertEquals(1, result.size());
        assertEquals(testTransaction, result.get(0));
        verify(transactionDao).getTransactionsByPlayerId(playerId, 10, null);
    }

    @Test
    @DisplayName("Should return transactions for game")
    void shouldReturnTransactionsForGame() {
        // Given
        String gameId = "game_456";
        List<Transaction> transactions = Arrays.asList(testTransaction);
        when(transactionDao.getTransactionsByGameId(gameId, 10, null)).thenReturn(transactions);

        // When
        List<Transaction> result = transactionDao.getTransactionsByGameId(gameId, 10, null);

        // Then
        assertEquals(1, result.size());
        assertEquals(testTransaction, result.get(0));
        verify(transactionDao).getTransactionsByGameId(gameId, 10, null);
    }

    @Test
    @DisplayName("Should update transaction status successfully")
    void shouldUpdateTransactionStatusSuccessfully() {
        // Given
        String transactionId = "txn_123456789";
        Transaction.TransactionStatus newStatus = Transaction.TransactionStatus.COMPLETED;
        String gatewayTransactionId = "stripe_txn_updated";
        String paymentMethodDetails = "Visa **** 1234";
        
        Transaction updatedTransaction = createTestTransaction();
        updatedTransaction.setStatus(newStatus);
        updatedTransaction.setGatewayTransactionId(gatewayTransactionId);
        updatedTransaction.setPaymentMethodDetails(paymentMethodDetails);
        
        when(transactionDao.updateTransactionStatus(transactionId, newStatus, gatewayTransactionId, paymentMethodDetails))
            .thenReturn(Optional.of(updatedTransaction));

        // When
        Optional<Transaction> result = transactionDao.updateTransactionStatus(
            transactionId, newStatus, gatewayTransactionId, paymentMethodDetails);

        // Then
        assertTrue(result.isPresent());
        assertEquals(newStatus, result.get().getStatus());
        assertEquals(gatewayTransactionId, result.get().getGatewayTransactionId());
        assertEquals(paymentMethodDetails, result.get().getPaymentMethodDetails());
        verify(transactionDao).updateTransactionStatus(transactionId, newStatus, gatewayTransactionId, paymentMethodDetails);
    }

    @Test
    @DisplayName("Should handle all transaction types")
    void shouldHandleAllTransactionTypes() {
        // Given
        doNothing().when(transactionDao).saveTransaction(any(Transaction.class));

        // When & Then
        for (Transaction.TransactionType type : Transaction.TransactionType.values()) {
            Transaction transaction = createTestTransaction();
            transaction.setTransactionId("txn_" + type.name());
            transaction.setTransactionType(type);
            
            assertDoesNotThrow(() -> transactionDao.saveTransaction(transaction),
                "Should handle transaction type: " + type);
        }
        
        verify(transactionDao, times(Transaction.TransactionType.values().length)).saveTransaction(any(Transaction.class));
    }

    @Test
    @DisplayName("Should handle all transaction statuses")
    void shouldHandleAllTransactionStatuses() {
        // Given
        doNothing().when(transactionDao).saveTransaction(any(Transaction.class));

        // When & Then
        for (Transaction.TransactionStatus status : Transaction.TransactionStatus.values()) {
            Transaction transaction = createTestTransaction();
            transaction.setTransactionId("txn_" + status.name());
            transaction.setStatus(status);
            
            assertDoesNotThrow(() -> transactionDao.saveTransaction(transaction),
                "Should handle transaction status: " + status);
        }
        
        verify(transactionDao, times(Transaction.TransactionStatus.values().length)).saveTransaction(any(Transaction.class));
    }

    @Test
    @DisplayName("Should return empty list when no transactions found")
    void shouldReturnEmptyListWhenNoTransactionsFound() {
        // Given
        String playerId = "player_nonexistent";
        when(transactionDao.getTransactionsByPlayerId(playerId, 10, null)).thenReturn(Collections.emptyList());

        // When
        List<Transaction> result = transactionDao.getTransactionsByPlayerId(playerId, 10, null);

        // Then
        assertTrue(result.isEmpty());
        verify(transactionDao).getTransactionsByPlayerId(playerId, 10, null);
    }

    @Test
    @DisplayName("Should handle transactions with player and game ID")
    void shouldHandleTransactionsWithPlayerAndGameId() {
        // Given
        String playerId = "player_123";
        String gameId = "game_456";
        List<Transaction> transactions = Arrays.asList(testTransaction);
        when(transactionDao.getTransactionsByPlayerAndGameId(playerId, gameId, 10, null)).thenReturn(transactions);

        // When
        List<Transaction> result = transactionDao.getTransactionsByPlayerAndGameId(playerId, gameId, 10, null);

        // Then
        assertEquals(1, result.size());
        assertEquals(testTransaction, result.get(0));
        verify(transactionDao).getTransactionsByPlayerAndGameId(playerId, gameId, 10, null);
    }

    @Test
    @DisplayName("Should validate transaction data integrity")
    void shouldValidateTransactionDataIntegrity() {
        // Given
        Transaction transaction = createTestTransaction();
        
        // When & Then
        assertNotNull(transaction.getTransactionId());
        assertNotNull(transaction.getPlayerId());
        assertNotNull(transaction.getTransactionType());
        assertNotNull(transaction.getAmount());
        assertNotNull(transaction.getCurrency());
        assertNotNull(transaction.getStatus());
        assertNotNull(transaction.getCreatedAt());
        assertTrue(transaction.getAmount() >= 0);
        assertEquals("USD", transaction.getCurrency());
        assertEquals(Transaction.TransactionType.ENTRY_FEE, transaction.getTransactionType());
        assertEquals(Transaction.TransactionStatus.PENDING, transaction.getStatus());
    }

    @Test
    @DisplayName("Should handle metadata operations correctly")
    void shouldHandleMetadataOperationsCorrectly() {
        // Given
        Transaction transaction = new Transaction();
        
        // When
        transaction.putMetadata("key1", "value1");
        transaction.putMetadata("key2", "value2");
        
        // Then
        assertNotNull(transaction.getMetadata());
        assertEquals(2, transaction.getMetadata().size());
        assertEquals("value1", transaction.getMetadata().get("key1"));
        assertEquals("value2", transaction.getMetadata().get("key2"));
    }

    @Test
    @DisplayName("Should handle pagination correctly")
    void shouldHandlePaginationCorrectly() {
        // Given
        String playerId = "player_123";
        String exclusiveStartKey = "txn_start";
        List<Transaction> transactions = Arrays.asList(testTransaction);
        when(transactionDao.getTransactionsByPlayerId(playerId, 5, exclusiveStartKey)).thenReturn(transactions);

        // When
        List<Transaction> result = transactionDao.getTransactionsByPlayerId(playerId, 5, exclusiveStartKey);

        // Then
        assertEquals(1, result.size());
        verify(transactionDao).getTransactionsByPlayerId(playerId, 5, exclusiveStartKey);
    }

    @Test
    @DisplayName("Should handle large transaction amounts")
    void shouldHandleLargeTransactionAmounts() {
        // Given
        Transaction transaction = createTestTransaction();
        transaction.setAmount(Long.MAX_VALUE);
        
        doNothing().when(transactionDao).saveTransaction(any(Transaction.class));

        // When & Then
        assertDoesNotThrow(() -> transactionDao.saveTransaction(transaction));
        verify(transactionDao).saveTransaction(transaction);
    }

    @Test
    @DisplayName("Should handle zero amount transactions")
    void shouldHandleZeroAmountTransactions() {
        // Given
        Transaction transaction = createTestTransaction();
        transaction.setAmount(0L);
        
        doNothing().when(transactionDao).saveTransaction(any(Transaction.class));

        // When & Then
        assertDoesNotThrow(() -> transactionDao.saveTransaction(transaction));
        verify(transactionDao).saveTransaction(transaction);
    }

    @Test
    @DisplayName("Should return empty when transaction not found for update")
    void shouldReturnEmptyWhenTransactionNotFoundForUpdate() {
        // Given
        String transactionId = "txn_nonexistent";
        Transaction.TransactionStatus newStatus = Transaction.TransactionStatus.COMPLETED;
        
        when(transactionDao.updateTransactionStatus(transactionId, newStatus, null, null))
            .thenReturn(Optional.empty());

        // When
        Optional<Transaction> result = transactionDao.updateTransactionStatus(
            transactionId, newStatus, null, null);

        // Then
        assertFalse(result.isPresent());
        verify(transactionDao).updateTransactionStatus(transactionId, newStatus, null, null);
    }

    @Test
    @DisplayName("Should handle minimal required fields")
    void shouldHandleMinimalRequiredFields() {
        // Given
        Transaction minimalTransaction = new Transaction();
        minimalTransaction.setTransactionId("txn_minimal");
        minimalTransaction.setPlayerId("player_minimal");
        minimalTransaction.setTransactionType(Transaction.TransactionType.IAP_ITEM);
        minimalTransaction.setAmount(500L);
        minimalTransaction.setCurrency("USD");
        minimalTransaction.setStatus(Transaction.TransactionStatus.PENDING);
        minimalTransaction.initializeTimestamps();

        doNothing().when(transactionDao).saveTransaction(any(Transaction.class));

        // When & Then
        assertDoesNotThrow(() -> transactionDao.saveTransaction(minimalTransaction));
        verify(transactionDao).saveTransaction(minimalTransaction);
    }

    @Test
    @DisplayName("Should handle DAO exceptions gracefully")
    void shouldHandleDaoExceptionsGracefully() {
        // Given
        when(transactionDao.getTransactionById("txn_123"))
            .thenThrow(new PersistenceException("Failed to get transaction"));

        // When & Then
        PersistenceException exception = assertThrows(PersistenceException.class,
            () -> transactionDao.getTransactionById("txn_123"));
        
        assertTrue(exception.getMessage().contains("Failed to get transaction"));
        verify(transactionDao).getTransactionById("txn_123");
    }

    @Test
    @DisplayName("Should handle null validation correctly")
    void shouldHandleNullValidationCorrectly() {
        // Given
        doThrow(new PersistenceException("Transaction cannot be null"))
            .when(transactionDao).saveTransaction(null);
        doThrow(new PersistenceException("Transaction ID cannot be null"))
            .when(transactionDao).getTransactionById(null);
        doThrow(new PersistenceException("Player ID cannot be null"))
            .when(transactionDao).getTransactionsByPlayerId(null, 10, null);

        // When & Then
        assertThrows(PersistenceException.class, () -> transactionDao.saveTransaction(null));
        assertThrows(PersistenceException.class, () -> transactionDao.getTransactionById(null));
        assertThrows(PersistenceException.class, () -> transactionDao.getTransactionsByPlayerId(null, 10, null));
    }

    @Test
    @DisplayName("Should handle invalid parameters correctly")
    void shouldHandleInvalidParametersCorrectly() {
        // Given
        doThrow(new PersistenceException("Limit must be positive"))
            .when(transactionDao).getTransactionsByPlayerId("player_123", 0, null);

        // When & Then
        PersistenceException exception = assertThrows(PersistenceException.class,
            () -> transactionDao.getTransactionsByPlayerId("player_123", 0, null));
        
        assertTrue(exception.getMessage().contains("Limit must be positive"));
    }
} 