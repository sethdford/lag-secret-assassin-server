package com.assassin.dao;

import com.assassin.model.Item;
import com.assassin.exception.PersistenceException;
import com.assassin.util.DynamoDbClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation of ItemDao.
 */
public class DynamoDbItemDao implements ItemDao {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbItemDao.class);
    private static final String ITEMS_TABLE_NAME_ENV_VAR = "ITEMS_TABLE_NAME";
    private final DynamoDbTable<Item> itemTable;
    private final String tableName;

    public DynamoDbItemDao() {
        DynamoDbEnhancedClient enhancedClient = DynamoDbClientProvider.getDynamoDbEnhancedClient();
        this.tableName = getTableNameFromEnv();
        this.itemTable = enhancedClient.table(this.tableName, TableSchema.fromBean(Item.class));
        logger.info("Initialized ItemDao for table: {}", this.tableName);
    }

    public DynamoDbItemDao(DynamoDbEnhancedClient enhancedClient) {
        this.tableName = getTableNameFromEnv();
        this.itemTable = enhancedClient.table(this.tableName, TableSchema.fromBean(Item.class));
        logger.info("Initialized ItemDao with provided enhanced client for table: {}", this.tableName);
    }

    private String getTableNameFromEnv() {
        String tableNameFromEnv = System.getenv(ITEMS_TABLE_NAME_ENV_VAR);
        if (tableNameFromEnv == null || tableNameFromEnv.isEmpty()) {
            logger.error("Items table name environment variable '{}' is not set.", ITEMS_TABLE_NAME_ENV_VAR);
            throw new IllegalStateException("Items table name not configured.");
        }
        return tableNameFromEnv;
    }

    @Override
    public void saveItem(Item item) throws PersistenceException {
        try {
            if (item.getItemId() == null || item.getItemId().trim().isEmpty()) {
                throw new IllegalArgumentException("Item ID cannot be null or empty");
            }
            
            item.initializeTimestamps(); // Ensure createdAt and updatedAt are set
            logger.debug("Saving item: {}", item.getItemId());
            itemTable.putItem(item);
            logger.info("Saved item: {}", item.getItemId());
        } catch (DynamoDbException e) {
            logger.error("Error saving item {}: {}", item.getItemId(), e.getMessage(), e);
            throw new PersistenceException("Failed to save item", e);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid item data: {}", e.getMessage());
            throw new PersistenceException("Invalid item data", e);
        }
    }

    @Override
    public Optional<Item> getItemById(String itemId) throws PersistenceException {
        try {
            if (itemId == null || itemId.trim().isEmpty()) {
                return Optional.empty();
            }
            
            logger.debug("Getting item by ID: {}", itemId);
            Item item = itemTable.getItem(Key.builder().partitionValue(itemId).build());
            return Optional.ofNullable(item);
        } catch (DynamoDbException e) {
            logger.error("Error getting item by ID {}: {}", itemId, e.getMessage(), e);
            throw new PersistenceException("Failed to get item by ID", e);
        }
    }

    @Override
    public List<Item> getAllActiveItems() throws PersistenceException {
        try {
            logger.debug("Getting all active items");
            
            Expression filterExpression = Expression.builder()
                .expression("IsActive = :isActive")
                .putExpressionValue(":isActive", AttributeValue.builder().bool(true).build())
                .build();
            
            ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(filterExpression)
                .build();
                
            return itemTable.scan(scanRequest).stream()
                .flatMap(page -> page.items().stream())
                .filter(Item::isAvailable) // Additional client-side filter as safety
                .collect(Collectors.toList());
        } catch (DynamoDbException e) {
            logger.error("Error getting all active items: {}", e.getMessage(), e);
            throw new PersistenceException("Failed to get all active items", e);
        }
    }

    @Override
    public List<Item> getAllItems() throws PersistenceException {
        try {
            logger.debug("Getting all items");
            return itemTable.scan().stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
        } catch (DynamoDbException e) {
            logger.error("Error getting all items: {}", e.getMessage(), e);
            throw new PersistenceException("Failed to get all items", e);
        }
    }

    @Override
    public List<Item> getItemsByType(Item.ItemType itemType) throws PersistenceException {
        try {
            if (itemType == null) {
                return List.of();
            }
            
            logger.debug("Getting items by type: {}", itemType);
            
            Expression filterExpression = Expression.builder()
                .expression("ItemType = :itemType")
                .putExpressionValue(":itemType", AttributeValue.builder().s(itemType.name()).build())
                .build();
            
            ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(filterExpression)
                .build();
                
            return itemTable.scan(scanRequest).stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
        } catch (DynamoDbException e) {
            logger.error("Error getting items by type {}: {}", itemType, e.getMessage(), e);
            throw new PersistenceException("Failed to get items by type", e);
        }
    }

    @Override
    public Optional<Item> updateItem(Item item) throws PersistenceException {
        try {
            if (item.getItemId() == null || item.getItemId().trim().isEmpty()) {
                throw new IllegalArgumentException("Item ID cannot be null or empty");
            }
            
            // Check if item exists first
            Optional<Item> existingItem = getItemById(item.getItemId());
            if (existingItem.isEmpty()) {
                logger.warn("Item not found for update: {}", item.getItemId());
                return Optional.empty();
            }
            
            item.updateTimestamp(); // Update the updatedAt timestamp
            logger.debug("Updating item: {}", item.getItemId());
            
            UpdateItemEnhancedRequest<Item> request = UpdateItemEnhancedRequest.builder(Item.class)
                .item(item)
                .build();
                
            Item updatedItem = itemTable.updateItem(request);
            logger.info("Updated item: {}", item.getItemId());
            return Optional.of(updatedItem);
        } catch (DynamoDbException e) {
            logger.error("Error updating item {}: {}", item.getItemId(), e.getMessage(), e);
            throw new PersistenceException("Failed to update item", e);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid item data for update: {}", e.getMessage());
            throw new PersistenceException("Invalid item data", e);
        }
    }

    @Override
    public Optional<Item> updateItemActiveStatus(String itemId, boolean isActive) throws PersistenceException {
        try {
            if (itemId == null || itemId.trim().isEmpty()) {
                throw new IllegalArgumentException("Item ID cannot be null or empty");
            }
            
            logger.debug("Updating active status for item ID: {} to {}", itemId, isActive);
            
            Optional<Item> existingItem = getItemById(itemId);
            if (existingItem.isEmpty()) {
                logger.warn("Item not found for active status update: {}", itemId);
                return Optional.empty();
            }
            
            Item item = existingItem.get();
            item.setIsActive(isActive);
            item.updateTimestamp();
            
            UpdateItemEnhancedRequest<Item> request = UpdateItemEnhancedRequest.builder(Item.class)
                .item(item)
                .build();
                
            Item updatedItem = itemTable.updateItem(request);
            logger.info("Updated active status for item: {} to {}", itemId, isActive);
            return Optional.of(updatedItem);
        } catch (DynamoDbException e) {
            logger.error("Error updating active status for item {}: {}", itemId, e.getMessage(), e);
            throw new PersistenceException("Failed to update item active status", e);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid data for active status update: {}", e.getMessage());
            throw new PersistenceException("Invalid data", e);
        }
    }

    @Override
    public Optional<Item> updateItemPrice(String itemId, Long newPrice) throws PersistenceException {
        try {
            if (itemId == null || itemId.trim().isEmpty()) {
                throw new IllegalArgumentException("Item ID cannot be null or empty");
            }
            if (newPrice == null || newPrice < 0) {
                throw new IllegalArgumentException("Price must be non-negative");
            }
            
            logger.debug("Updating price for item ID: {} to {}", itemId, newPrice);
            
            Optional<Item> existingItem = getItemById(itemId);
            if (existingItem.isEmpty()) {
                logger.warn("Item not found for price update: {}", itemId);
                return Optional.empty();
            }
            
            Item item = existingItem.get();
            item.setPrice(newPrice);
            item.updateTimestamp();
            
            UpdateItemEnhancedRequest<Item> request = UpdateItemEnhancedRequest.builder(Item.class)
                .item(item)
                .build();
                
            Item updatedItem = itemTable.updateItem(request);
            logger.info("Updated price for item: {} to {}", itemId, newPrice);
            return Optional.of(updatedItem);
        } catch (DynamoDbException e) {
            logger.error("Error updating price for item {}: {}", itemId, e.getMessage(), e);
            throw new PersistenceException("Failed to update item price", e);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid data for price update: {}", e.getMessage());
            throw new PersistenceException("Invalid data", e);
        }
    }

    @Override
    public boolean deleteItem(String itemId) throws PersistenceException {
        try {
            if (itemId == null || itemId.trim().isEmpty()) {
                return false;
            }
            
            logger.debug("Deleting item: {}", itemId);
            
            // Check if item exists first
            Optional<Item> existingItem = getItemById(itemId);
            if (existingItem.isEmpty()) {
                logger.warn("Item not found for deletion: {}", itemId);
                return false;
            }
            
            Key key = Key.builder().partitionValue(itemId).build();
            itemTable.deleteItem(key);
            logger.info("Deleted item: {}", itemId);
            return true;
        } catch (DynamoDbException e) {
            logger.error("Error deleting item {}: {}", itemId, e.getMessage(), e);
            throw new PersistenceException("Failed to delete item", e);
        }
    }

    @Override
    public List<Item> getItemsByPriceRange(Long minPrice, Long maxPrice) throws PersistenceException {
        try {
            if (minPrice == null || maxPrice == null || minPrice < 0 || maxPrice < 0 || minPrice > maxPrice) {
                throw new IllegalArgumentException("Invalid price range");
            }
            
            logger.debug("Getting items by price range: {} - {}", minPrice, maxPrice);
            
            // Since we don't have a GSI on price, we need to scan and filter
            Expression filterExpression = Expression.builder()
                .expression("Price >= :minPrice AND Price <= :maxPrice")
                .putExpressionValue(":minPrice", AttributeValue.builder().n(minPrice.toString()).build())
                .putExpressionValue(":maxPrice", AttributeValue.builder().n(maxPrice.toString()).build())
                .build();
            
            ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(filterExpression)
                .build();
                
            return itemTable.scan(scanRequest).stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
        } catch (DynamoDbException e) {
            logger.error("Error getting items by price range {}-{}: {}", minPrice, maxPrice, e.getMessage(), e);
            throw new PersistenceException("Failed to get items by price range", e);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid price range: {}", e.getMessage());
            throw new PersistenceException("Invalid price range", e);
        }
    }
} 