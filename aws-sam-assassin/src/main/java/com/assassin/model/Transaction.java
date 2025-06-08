package com.assassin.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

import java.time.Instant;
import java.util.Map;

@DynamoDbBean
public class Transaction {

    public enum TransactionType {
        ENTRY_FEE,      // Fee to join a game
        IAP_ITEM,       // In-app purchase of a consumable or entitlement
        IAP_CURRENCY,   // In-app purchase of virtual currency
        SUBSCRIPTION_NEW, // New subscription activation
        SUBSCRIPTION_RENEWAL, // Subscription renewal
        REFUND,         // Refund for a previous transaction
        PAYOUT          // Payout to a player (e.g., winnings)
    }

    public enum TransactionStatus {
        PENDING,        // Transaction initiated but not yet confirmed by payment gateway
        PROCESSING,     // Payment gateway is processing the transaction
        COMPLETED,      // Transaction successfully completed
        FAILED,         // Transaction failed (e.g., card declined, fraud)
        REFUNDED,       // Transaction has been fully or partially refunded
        PARTIALLY_REFUNDED, // Transaction has been partially refunded
        CANCELLED,      // Transaction was cancelled before completion
        REQUIRES_ACTION // Transaction requires further action from the user (e.g. 3DS authentication)
    }

    private String transactionId;          // (PK)
    private String playerId;               // (GSI_PlayerTransactions_PK)
    private String createdAt;              // (GSI_PlayerTransactions_SK, GSI_GameTransactions_SK, etc.)
    private String gameId;                 // (Optional, GSI_GameTransactions_PK) For entry fees or game-related purchases
    private String itemId;                 // (Optional) For IAP, references an item ID
    private TransactionType transactionType;
    private Long amount;                   // Amount in smallest currency unit (e.g., cents)
    private String currency;               // ISO 4217 currency code (e.g., "USD", "EUR")
    private String paymentGateway;         // e.g., "STRIPE", "PAYPAL"
    private String gatewayTransactionId;   // ID from the payment gateway
    private String gatewayCustomerId;      // Customer ID from the payment gateway (e.g. Stripe Customer ID)
    private String paymentMethodDetails;   // e.g., "Visa **** 4242", "PayPal user@example.com" (store minimal, non-sensitive info)
    private TransactionStatus status;
    private String updatedAt;
    private String description;            // Optional user-facing description
    private Map<String, String> metadata;  // For additional details (promo codes, gateway response excerpts, etc.)

    public Transaction() {
        // Default constructor for DynamoDB
    }

    // Getters and Setters

    @DynamoDbPartitionKey
    @DynamoDbAttribute("TransactionID")
    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"PlayerTransactionsIndex"})
    @DynamoDbAttribute("PlayerID")
    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    @DynamoDbSecondarySortKey(indexNames = {"PlayerTransactionsIndex", "GameTransactionsIndex"})
    @DynamoDbAttribute("CreatedAt")
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"GameTransactionsIndex"})
    @DynamoDbAttribute("GameID")
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    @DynamoDbAttribute("ItemID")
    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    @DynamoDbAttribute("TransactionType")
    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    @DynamoDbAttribute("Amount")
    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    @DynamoDbAttribute("Currency")
    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    @DynamoDbAttribute("PaymentGateway")
    public String getPaymentGateway() {
        return paymentGateway;
    }

    public void setPaymentGateway(String paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    @DynamoDbAttribute("GatewayTransactionID")
    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public void setGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }
    
    @DynamoDbAttribute("GatewayCustomerID")
    public String getGatewayCustomerId() {
        return gatewayCustomerId;
    }

    public void setGatewayCustomerId(String gatewayCustomerId) {
        this.gatewayCustomerId = gatewayCustomerId;
    }

    @DynamoDbAttribute("PaymentMethodDetails")
    public String getPaymentMethodDetails() {
        return paymentMethodDetails;
    }

    public void setPaymentMethodDetails(String paymentMethodDetails) {
        this.paymentMethodDetails = paymentMethodDetails;
    }

    @DynamoDbAttribute("Status")
    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    @DynamoDbAttribute("UpdatedAt")
    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @DynamoDbAttribute("Description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @DynamoDbAttribute("Metadata")
    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    /**
     * Helper method to add a single key-value pair to the metadata map.
     * Initializes the map if it's null.
     * @param key The metadata key.
     * @param value The metadata value.
     */
    public void putMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new java.util.HashMap<>();
        }
        this.metadata.put(key, value);
    }

    // Helper to set default timestamps
    public void initializeTimestamps() {
        String now = Instant.now().toString();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }
} 