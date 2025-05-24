package com.assassin.dao;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.model.SubscriptionTier;
import com.assassin.util.DynamoDbClientProvider;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

public class DynamoDbSubscriptionTierDao implements SubscriptionTierDao {

    private static final Logger LOG = LoggerFactory.getLogger(DynamoDbSubscriptionTierDao.class);
    private final DynamoDbTable<SubscriptionTier> subscriptionTierTable;
    public static final String SUBSCRIPTION_TIERS_TABLE_NAME_ENV_VAR = "SUBSCRIPTION_TIERS_TABLE_NAME";
    // GSI for active tiers, if we create one. For now, filter in code or add GSI later.
    // public static final String ACTIVE_TIERS_GSI_NAME = "ActiveTiersIndex"; 

    // Constructor for production use (reads from ENV var)
    public DynamoDbSubscriptionTierDao() {
        this(System.getenv(SUBSCRIPTION_TIERS_TABLE_NAME_ENV_VAR));
    }

    // Constructor for testing or specific table name injection
    public DynamoDbSubscriptionTierDao(String tableName) {
        this(tableName, DynamoDbClientProvider.getEnhancedClient());
    }
    
    // Internal constructor for maximal testability / DI
    DynamoDbSubscriptionTierDao(String tableName, DynamoDbEnhancedClient enhancedClient) {
        if (tableName == null || tableName.trim().isEmpty()) {
            LOG.error("Subscription Tiers table name provided to constructor is null or empty.");
            throw new IllegalStateException("Subscription Tiers table name not configured.");
        }
        if (enhancedClient == null) {
            LOG.error("DynamoDbEnhancedClient provided to constructor is null.");
            throw new IllegalStateException("DynamoDbEnhancedClient not provided.");
        }
        this.subscriptionTierTable = enhancedClient.table(tableName, TableSchema.fromBean(SubscriptionTier.class));
        LOG.info("DynamoDbSubscriptionTierDao initialized with table: {} using provided client.", tableName);
    }

    @Override
    public Optional<SubscriptionTier> getTierById(String tierId) {
        if (tierId == null || tierId.trim().isEmpty()) {
            LOG.warn("getTierById called with null or empty tierId.");
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(subscriptionTierTable.getItem(Key.builder().partitionValue(tierId).build()));
        } catch (DynamoDbException e) {
            LOG.error("Error fetching subscription tier by ID '{}': {}", tierId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public List<SubscriptionTier> getAllTiers() {
        try {
            return subscriptionTierTable.scan().items().stream().collect(Collectors.toList());
        } catch (DynamoDbException e) {
            LOG.error("Error scanning all subscription tiers: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<SubscriptionTier> getAllActiveTiersOrdered() {
        try {
            // If a GSI `isActive-displayOrder-index` exists, query it.
            // For now, scan and filter, then sort. This is less efficient for large tables.
            // Consider adding a GSI on `isActive` (as PK) and `displayOrder` (as SK) if performance is critical.
            Expression filter = Expression.builder()
                .expression("isActive = :val")
                .expressionValues(Map.of(":val", AttributeValue.builder().bool(true).build()))
                .build();

            return subscriptionTierTable.scan(req -> req.filterExpression(filter))
                    .items().stream()
                    .sorted(Comparator.comparing(SubscriptionTier::getDisplayOrder, Comparator.nullsLast(Integer::compareTo)))
                    .collect(Collectors.toList());
        } catch (DynamoDbException e) {
            LOG.error("Error scanning active and ordered subscription tiers: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public void saveTier(SubscriptionTier tier) {
        if (tier == null) {
            LOG.warn("saveTier called with null tier.");
            return;
        }
        try {
            subscriptionTierTable.putItem(tier);
            LOG.info("Subscription tier '{}' saved successfully.", tier.getTierId());
        } catch (DynamoDbException e) {
            LOG.error("Error saving subscription tier '{}': {}", tier.getTierId(), e.getMessage(), e);
            // Optionally rethrow as a custom DAO exception
        }
    }

    @Override
    public boolean deleteTier(String tierId) {
        if (tierId == null || tierId.trim().isEmpty()) {
            LOG.warn("deleteTier called with null or empty tierId.");
            return false;
        }
        try {
            subscriptionTierTable.deleteItem(Key.builder().partitionValue(tierId).build());
            LOG.info("Subscription tier '{}' deleted successfully.", tierId);
            return true;
        } catch (DynamoDbException e) {
            LOG.error("Error deleting subscription tier '{}': {}", tierId, e.getMessage(), e);
            return false;
        }
    }
} 