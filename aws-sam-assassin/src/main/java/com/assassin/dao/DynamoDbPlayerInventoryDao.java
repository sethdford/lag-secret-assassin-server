package com.assassin.dao;

import com.assassin.model.PlayerInventoryItem;
import com.assassin.exception.PersistenceException;
import com.assassin.util.DynamoDbClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation of PlayerInventoryDao.
 */
public class DynamoDbPlayerInventoryDao implements PlayerInventoryDao {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbPlayerInventoryDao.class);
    private static final String INVENTORY_TABLE_NAME_ENV_VAR = "PLAYER_INVENTORY_TABLE_NAME";
    private final DynamoDbTable<PlayerInventoryItem> inventoryTable;
    private final DynamoDbIndex<PlayerInventoryItem> itemIdIndex;
    private final String tableName;

    public DynamoDbPlayerInventoryDao() {
        DynamoDbEnhancedClient enhancedClient = DynamoDbClientProvider.getDynamoDbEnhancedClient();
        this.tableName = getTableNameFromEnv();
        this.inventoryTable = enhancedClient.table(this.tableName, TableSchema.fromBean(PlayerInventoryItem.class));
        this.itemIdIndex = inventoryTable.index(ITEM_ID_INDEX_NAME);
        logger.info("Initialized PlayerInventoryDao for table: {}", this.tableName);
    }

    public DynamoDbPlayerInventoryDao(DynamoDbEnhancedClient enhancedClient) {
        this.tableName = getTableNameFromEnv();
        this.inventoryTable = enhancedClient.table(this.tableName, TableSchema.fromBean(PlayerInventoryItem.class));
        this.itemIdIndex = inventoryTable.index(ITEM_ID_INDEX_NAME);
        logger.info("Initialized PlayerInventoryDao with provided enhanced client for table: {}", this.tableName);
    }

    private String getTableNameFromEnv() {
        String tableNameFromEnv = System.getenv(INVENTORY_TABLE_NAME_ENV_VAR);
        if (tableNameFromEnv == null || tableNameFromEnv.isEmpty()) {
            logger.error("Player inventory table name environment variable '{}' is not set.", INVENTORY_TABLE_NAME_ENV_VAR);
            throw new IllegalStateException("Player inventory table name not configured.");
        }
        return tableNameFromEnv;
    }

    @Override
    public void saveInventoryItem(PlayerInventoryItem inventoryItem) throws PersistenceException {
        try {
            if (inventoryItem.getPlayerId() == null || inventoryItem.getPlayerId().trim().isEmpty()) {
                throw new IllegalArgumentException("Player ID cannot be null or empty");
            }
            if (inventoryItem.getInventoryItemId() == null || inventoryItem.getInventoryItemId().trim().isEmpty()) {
                throw new IllegalArgumentException("Inventory Item ID cannot be null or empty");
            }
            
            if (inventoryItem.getAcquiredAt() == null) {
                inventoryItem.initializeAcquisition();
            }
            inventoryItem.updateStatusIfExpired(); // Auto-update status if expired
            
            logger.debug("Saving inventory item: {}/{}", inventoryItem.getPlayerId(), inventoryItem.getInventoryItemId());
            inventoryTable.putItem(inventoryItem);
            logger.info("Saved inventory item: {}/{}", inventoryItem.getPlayerId(), inventoryItem.getInventoryItemId());
        } catch (DynamoDbException e) {
            logger.error("Error saving inventory item {}/{}: {}", 
                inventoryItem.getPlayerId(), inventoryItem.getInventoryItemId(), e.getMessage(), e);
            throw new PersistenceException("Failed to save inventory item", e);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid inventory item data: {}", e.getMessage());
            throw new PersistenceException("Invalid inventory item data", e);
        }
    }

    @Override
    public Optional<PlayerInventoryItem> getInventoryItem(String playerId, String inventoryItemId) throws PersistenceException {
        try {
            if (playerId == null || playerId.trim().isEmpty() || 
                inventoryItemId == null || inventoryItemId.trim().isEmpty()) {
                return Optional.empty();
            }
            
            logger.debug("Getting inventory item: {}/{}", playerId, inventoryItemId);
            Key key = Key.builder()
                .partitionValue(playerId)
                .sortValue(inventoryItemId)
                .build();
            PlayerInventoryItem item = inventoryTable.getItem(key);
            
            if (item != null) {
                item.updateStatusIfExpired(); // Auto-update status if expired
            }
            
            return Optional.ofNullable(item);
        } catch (DynamoDbException e) {
            logger.error("Error getting inventory item {}/{}: {}", playerId, inventoryItemId, e.getMessage(), e);
            throw new PersistenceException("Failed to get inventory item", e);
        }
    }

    @Override
    public List<PlayerInventoryItem> getPlayerInventory(String playerId) throws PersistenceException {
        try {
            if (playerId == null || playerId.trim().isEmpty()) {
                return List.of();
            }
            
            logger.debug("Getting player inventory for: {}", playerId);
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(playerId).build());
            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // Most recent first (by acquisitionTime)
                .build();
                
            List<PlayerInventoryItem> items = inventoryTable.query(request).stream()
                .flatMap(page -> page.items().stream())
                .peek(PlayerInventoryItem::updateStatusIfExpired) // Auto-update status if expired
                .collect(Collectors.toList());
                
            logger.debug("Found {} inventory items for player: {}", items.size(), playerId);
            return items;
        } catch (DynamoDbException e) {
            logger.error("Error getting player inventory for {}: {}", playerId, e.getMessage(), e);
            throw new PersistenceException("Failed to get player inventory", e);
        }
    }

    @Override
    public List<PlayerInventoryItem> getActivePlayerInventory(String playerId) throws PersistenceException {
        try {
            if (playerId == null || playerId.trim().isEmpty()) {
                return List.of();
            }
            
            logger.debug("Getting active inventory for player: {}", playerId);
            
            Expression filterExpression = Expression.builder()
                .expression("#status = :activeStatus AND attribute_not_exists(ExpiresAt) OR ExpiresAt > :now")
                .putExpressionName("#status", "Status")
                .putExpressionValue(":activeStatus", AttributeValue.builder().s("ACTIVE").build())
                .putExpressionValue(":now", AttributeValue.builder().s(Instant.now().toString()).build())
                .build();
                
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(playerId).build());
            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .scanIndexForward(false)
                .build();
                
            List<PlayerInventoryItem> items = inventoryTable.query(request).stream()
                .flatMap(page -> page.items().stream())
                .filter(item -> {
                    item.updateStatusIfExpired();
                    return item.canUse();
                })
                .collect(Collectors.toList());
                
            logger.debug("Found {} active inventory items for player: {}", items.size(), playerId);
            return items;
        } catch (DynamoDbException e) {
            logger.error("Error getting active inventory for {}: {}", playerId, e.getMessage(), e);
            throw new PersistenceException("Failed to get active player inventory", e);
        }
    }

    @Override
    public List<PlayerInventoryItem> getPlayerInventoryByItemId(String playerId, String itemId) throws PersistenceException {
        try {
            if (playerId == null || playerId.trim().isEmpty() || itemId == null || itemId.trim().isEmpty()) {
                return List.of();
            }
            
            logger.debug("Getting inventory items for player {} and item ID: {}", playerId, itemId);
            
            Expression filterExpression = Expression.builder()
                .expression("ItemID = :itemId")
                .putExpressionValue(":itemId", AttributeValue.builder().s(itemId).build())
                .build();
                
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(playerId).build());
            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .scanIndexForward(false)
                .build();
                
            List<PlayerInventoryItem> items = inventoryTable.query(request).stream()
                .flatMap(page -> page.items().stream())
                .peek(PlayerInventoryItem::updateStatusIfExpired)
                .collect(Collectors.toList());
                
            logger.debug("Found {} inventory items for player {} and item ID {}", items.size(), playerId, itemId);
            return items;
        } catch (DynamoDbException e) {
            logger.error("Error getting inventory by item ID for player {}: {}", playerId, e.getMessage(), e);
            throw new PersistenceException("Failed to get player inventory by item ID", e);
        }
    }

    @Override
    public List<PlayerInventoryItem> getPlayersWithItem(String itemId, int limit, String exclusiveStartKey) throws PersistenceException {
        try {
            if (itemId == null || itemId.trim().isEmpty()) {
                return List.of();
            }
            
            logger.debug("Getting players with item ID: {}", itemId);
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(itemId).build());
            QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false);
                
            if (limit > 0) {
                requestBuilder.limit(limit);
            }
            
            // Note: exclusiveStartKey implementation would need more complex handling for GSI pagination
            if (exclusiveStartKey != null && !exclusiveStartKey.trim().isEmpty()) {
                logger.warn("ExclusiveStartKey pagination not fully implemented for GSI queries");
            }
            
            List<PlayerInventoryItem> items = itemIdIndex.query(requestBuilder.build()).stream()
                .flatMap(page -> page.items().stream())
                .peek(PlayerInventoryItem::updateStatusIfExpired)
                .collect(Collectors.toList());
                
            logger.debug("Found {} players with item ID: {}", items.size(), itemId);
            return items;
        } catch (DynamoDbException e) {
            logger.error("Error getting players with item ID {}: {}", itemId, e.getMessage(), e);
            throw new PersistenceException("Failed to get players with item", e);
        }
    }

    @Override
    public Optional<PlayerInventoryItem> updateInventoryItem(PlayerInventoryItem inventoryItem) throws PersistenceException {
        try {
            if (inventoryItem.getPlayerId() == null || inventoryItem.getPlayerId().trim().isEmpty() ||
                inventoryItem.getInventoryItemId() == null || inventoryItem.getInventoryItemId().trim().isEmpty()) {
                throw new IllegalArgumentException("Player ID and Inventory Item ID cannot be null or empty");
            }
            
            // Check if item exists first
            Optional<PlayerInventoryItem> existingItem = getInventoryItem(
                inventoryItem.getPlayerId(), inventoryItem.getInventoryItemId());
            if (existingItem.isEmpty()) {
                logger.warn("Inventory item not found for update: {}/{}", 
                    inventoryItem.getPlayerId(), inventoryItem.getInventoryItemId());
                return Optional.empty();
            }
            
            inventoryItem.updateStatusIfExpired(); // Auto-update status if expired
            logger.debug("Updating inventory item: {}/{}", 
                inventoryItem.getPlayerId(), inventoryItem.getInventoryItemId());
            
            UpdateItemEnhancedRequest<PlayerInventoryItem> request = UpdateItemEnhancedRequest.builder(PlayerInventoryItem.class)
                .item(inventoryItem)
                .build();
                
            PlayerInventoryItem updatedItem = inventoryTable.updateItem(request);
            logger.info("Updated inventory item: {}/{}", 
                inventoryItem.getPlayerId(), inventoryItem.getInventoryItemId());
            return Optional.of(updatedItem);
        } catch (DynamoDbException e) {
            logger.error("Error updating inventory item {}/{}: {}", 
                inventoryItem.getPlayerId(), inventoryItem.getInventoryItemId(), e.getMessage(), e);
            throw new PersistenceException("Failed to update inventory item", e);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid inventory item data for update: {}", e.getMessage());
            throw new PersistenceException("Invalid inventory item data", e);
        }
    }

    @Override
    public Optional<PlayerInventoryItem> updateInventoryItemQuantity(String playerId, String inventoryItemId, int newQuantity) throws PersistenceException {
        try {
            Optional<PlayerInventoryItem> existingItem = getInventoryItem(playerId, inventoryItemId);
            if (existingItem.isEmpty()) {
                logger.warn("Inventory item not found for quantity update: {}/{}", playerId, inventoryItemId);
                return Optional.empty();
            }
            
            PlayerInventoryItem item = existingItem.get();
            item.setQuantity(newQuantity);
            if (newQuantity <= 0) {
                item.setStatus(PlayerInventoryItem.InventoryItemStatus.USED);
            } else if (item.getStatus() == PlayerInventoryItem.InventoryItemStatus.USED) {
                item.setStatus(PlayerInventoryItem.InventoryItemStatus.ACTIVE);
            }
            
            return updateInventoryItem(item);
        } catch (Exception e) {
            logger.error("Error updating inventory item quantity for {}/{}: {}", playerId, inventoryItemId, e.getMessage(), e);
            throw new PersistenceException("Failed to update inventory item quantity", e);
        }
    }

    @Override
    public Optional<PlayerInventoryItem> updateInventoryItemStatus(String playerId, String inventoryItemId, 
                                                                 PlayerInventoryItem.InventoryItemStatus newStatus) throws PersistenceException {
        try {
            Optional<PlayerInventoryItem> existingItem = getInventoryItem(playerId, inventoryItemId);
            if (existingItem.isEmpty()) {
                logger.warn("Inventory item not found for status update: {}/{}", playerId, inventoryItemId);
                return Optional.empty();
            }
            
            PlayerInventoryItem item = existingItem.get();
            item.setStatus(newStatus);
            
            return updateInventoryItem(item);
        } catch (Exception e) {
            logger.error("Error updating inventory item status for {}/{}: {}", playerId, inventoryItemId, e.getMessage(), e);
            throw new PersistenceException("Failed to update inventory item status", e);
        }
    }

    @Override
    public Optional<PlayerInventoryItem> useInventoryItem(String playerId, String inventoryItemId) throws PersistenceException {
        try {
            Optional<PlayerInventoryItem> existingItem = getInventoryItem(playerId, inventoryItemId);
            if (existingItem.isEmpty()) {
                logger.warn("Inventory item not found for use: {}/{}", playerId, inventoryItemId);
                return Optional.empty();
            }
            
            PlayerInventoryItem item = existingItem.get();
            
            // Check if item can be used
            if (!item.canUse()) {
                logger.warn("Inventory item cannot be used: {}/{}, status: {}, quantity: {}", 
                    playerId, inventoryItemId, item.getStatus(), item.getQuantity());
                return Optional.empty();
            }
            
            // Mark as used and decrement quantity
            item.markAsUsed();
            item.decrementQuantity();
            
            return updateInventoryItem(item);
        } catch (Exception e) {
            logger.error("Error using inventory item {}/{}: {}", playerId, inventoryItemId, e.getMessage(), e);
            throw new PersistenceException("Failed to use inventory item", e);
        }
    }

    @Override
    public PlayerInventoryItem grantItemToPlayer(String playerId, String itemId, int quantity, String gameId) throws PersistenceException {
        try {
            if (playerId == null || playerId.trim().isEmpty() || itemId == null || itemId.trim().isEmpty()) {
                throw new IllegalArgumentException("Player ID and Item ID cannot be null or empty");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
            
            logger.debug("Granting {} of item {} to player {} in game {}", quantity, itemId, playerId, gameId);
            
            // Check if player already has this item type
            List<PlayerInventoryItem> existingItems = getPlayerInventoryByItemId(playerId, itemId);
            
            // Find the first active item of this type to add quantity to
            Optional<PlayerInventoryItem> activeItem = existingItems.stream()
                .filter(PlayerInventoryItem::canUse)
                .findFirst();
                
            if (activeItem.isPresent()) {
                // Add to existing item
                PlayerInventoryItem item = activeItem.get();
                item.incrementQuantity(quantity);
                updateInventoryItem(item);
                logger.info("Added {} quantity to existing item for player {}", quantity, playerId);
                return item;
            } else {
                // Create new inventory item
                PlayerInventoryItem newItem = new PlayerInventoryItem();
                newItem.setPlayerId(playerId);
                newItem.setInventoryItemId(UUID.randomUUID().toString());
                newItem.setItemId(itemId);
                newItem.setQuantity(quantity);
                newItem.setGameId(gameId);
                newItem.setStatus(PlayerInventoryItem.InventoryItemStatus.ACTIVE);
                newItem.initializeAcquisition();
                
                saveInventoryItem(newItem);
                logger.info("Created new inventory item for player {} with {} quantity", playerId, quantity);
                return newItem;
            }
        } catch (Exception e) {
            logger.error("Error granting item {} to player {}: {}", itemId, playerId, e.getMessage(), e);
            throw new PersistenceException("Failed to grant item to player", e);
        }
    }

    @Override
    public boolean deleteInventoryItem(String playerId, String inventoryItemId) throws PersistenceException {
        try {
            if (playerId == null || playerId.trim().isEmpty() || 
                inventoryItemId == null || inventoryItemId.trim().isEmpty()) {
                return false;
            }
            
            logger.debug("Deleting inventory item: {}/{}", playerId, inventoryItemId);
            
            // Check if item exists first
            Optional<PlayerInventoryItem> existingItem = getInventoryItem(playerId, inventoryItemId);
            if (existingItem.isEmpty()) {
                logger.warn("Inventory item not found for deletion: {}/{}", playerId, inventoryItemId);
                return false;
            }
            
            Key key = Key.builder()
                .partitionValue(playerId)
                .sortValue(inventoryItemId)
                .build();
            inventoryTable.deleteItem(key);
            logger.info("Deleted inventory item: {}/{}", playerId, inventoryItemId);
            return true;
        } catch (DynamoDbException e) {
            logger.error("Error deleting inventory item {}/{}: {}", playerId, inventoryItemId, e.getMessage(), e);
            throw new PersistenceException("Failed to delete inventory item", e);
        }
    }

    @Override
    public List<PlayerInventoryItem> getExpiredInventoryItems(int limit) throws PersistenceException {
        try {
            logger.debug("Getting expired inventory items with limit: {}", limit);
            
            Expression filterExpression = Expression.builder()
                .expression("attribute_exists(ExpiresAt) AND ExpiresAt < :now")
                .putExpressionValue(":now", AttributeValue.builder().s(Instant.now().toString()).build())
                .build();
                
            ScanEnhancedRequest.Builder requestBuilder = ScanEnhancedRequest.builder()
                .filterExpression(filterExpression);
                
            if (limit > 0) {
                requestBuilder.limit(limit);
            }
            
            List<PlayerInventoryItem> expiredItems = inventoryTable.scan(requestBuilder.build()).stream()
                .flatMap(page -> page.items().stream())
                .limit(limit > 0 ? limit : Integer.MAX_VALUE)
                .collect(Collectors.toList());
                
            logger.debug("Found {} expired inventory items", expiredItems.size());
            return expiredItems;
        } catch (DynamoDbException e) {
            logger.error("Error getting expired inventory items: {}", e.getMessage(), e);
            throw new PersistenceException("Failed to get expired inventory items", e);
        }
    }

    @Override
    public List<PlayerInventoryItem> getPlayerInventoryForGame(String playerId, String gameId) throws PersistenceException {
        try {
            if (playerId == null || playerId.trim().isEmpty() || gameId == null || gameId.trim().isEmpty()) {
                return List.of();
            }
            
            logger.debug("Getting inventory for player {} in game {}", playerId, gameId);
            
            Expression filterExpression = Expression.builder()
                .expression("GameID = :gameId OR attribute_not_exists(GameID)")
                .putExpressionValue(":gameId", AttributeValue.builder().s(gameId).build())
                .build();
                
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(playerId).build());
            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .scanIndexForward(false)
                .build();
                
            List<PlayerInventoryItem> items = inventoryTable.query(request).stream()
                .flatMap(page -> page.items().stream())
                .peek(PlayerInventoryItem::updateStatusIfExpired)
                .collect(Collectors.toList());
                
            logger.debug("Found {} inventory items for player {} in game {}", items.size(), playerId, gameId);
            return items;
        } catch (DynamoDbException e) {
            logger.error("Error getting inventory for player {} in game {}: {}", playerId, gameId, e.getMessage(), e);
            throw new PersistenceException("Failed to get player inventory for game", e);
        }
    }

    @Override
    public int getTotalItemQuantityForPlayer(String playerId, String itemId) throws PersistenceException {
        try {
            if (playerId == null || playerId.trim().isEmpty() || itemId == null || itemId.trim().isEmpty()) {
                return 0;
            }
            
            logger.debug("Getting total quantity of item {} for player {}", itemId, playerId);
            
            List<PlayerInventoryItem> items = getPlayerInventoryByItemId(playerId, itemId);
            int totalQuantity = items.stream()
                .filter(item -> item.getStatus() == PlayerInventoryItem.InventoryItemStatus.ACTIVE)
                .filter(item -> !item.isExpired())
                .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                .sum();
                
            logger.debug("Player {} has total quantity {} of item {}", playerId, totalQuantity, itemId);
            return totalQuantity;
        } catch (Exception e) {
            logger.error("Error getting total item quantity for player {}: {}", playerId, e.getMessage(), e);
            throw new PersistenceException("Failed to get total item quantity", e);
        }
    }
} 