package com.assassin.service;

import com.assassin.dao.ItemDao;
import com.assassin.dao.PlayerInventoryDao;
import com.assassin.dao.TransactionDao;
import com.assassin.dao.PlayerDao;
import com.assassin.dao.DynamoDbItemDao;
import com.assassin.dao.DynamoDbPlayerInventoryDao;
import com.assassin.dao.DynamoDbTransactionDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.model.Item;
import com.assassin.model.PlayerInventoryItem;
import com.assassin.model.Transaction;
import com.assassin.model.Player;
import com.assassin.exception.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for item-related operations including inventory management and purchases.
 */
public class ItemService {

    private static final Logger logger = LoggerFactory.getLogger(ItemService.class);
    private final ItemDao itemDao;
    private final PlayerInventoryDao playerInventoryDao;
    private final TransactionDao transactionDao;
    private final PlayerDao playerDao;

    /**
     * Default constructor.
     */
    public ItemService() {
        this.itemDao = new DynamoDbItemDao();
        this.playerInventoryDao = new DynamoDbPlayerInventoryDao();
        this.transactionDao = new DynamoDbTransactionDao();
        this.playerDao = new DynamoDbPlayerDao();
    }

    /**
     * Constructor with dependency injection for testability.
     *
     * @param itemDao The item DAO
     * @param playerInventoryDao The player inventory DAO
     * @param transactionDao The transaction DAO
     * @param playerDao The player DAO
     */
    public ItemService(ItemDao itemDao, PlayerInventoryDao playerInventoryDao, 
                      TransactionDao transactionDao, PlayerDao playerDao) {
        this.itemDao = itemDao;
        this.playerInventoryDao = playerInventoryDao;
        this.transactionDao = transactionDao;
        this.playerDao = playerDao;
    }

    /**
     * Gets all active items available for purchase.
     *
     * @return List of all active items
     * @throws PersistenceException if there's an error accessing the data store
     */
    public List<Item> getAllItems() throws PersistenceException {
        try {
            logger.debug("Getting all active items");
            List<Item> items = itemDao.getAllActiveItems();
            logger.info("Retrieved {} active items", items.size());
            return items;
        } catch (Exception e) {
            logger.error("Error getting all items: {}", e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve items", e);
        }
    }

    /**
     * Gets all items of a specific type.
     *
     * @param itemType The type of items to retrieve
     * @return List of items of the specified type
     * @throws PersistenceException if there's an error accessing the data store
     */
    public List<Item> getItemsByType(Item.ItemType itemType) throws PersistenceException {
        try {
            if (itemType == null) {
                throw new IllegalArgumentException("Item type cannot be null");
            }
            
            logger.debug("Getting items by type: {}", itemType);
            List<Item> items = itemDao.getItemsByType(itemType);
            logger.info("Retrieved {} items of type {}", items.size(), itemType);
            return items;
        } catch (Exception e) {
            logger.error("Error getting items by type {}: {}", itemType, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve items by type", e);
        }
    }

    /**
     * Gets an item by its ID.
     *
     * @param itemId The ID of the item to retrieve
     * @return Optional containing the item if found, empty otherwise
     * @throws PersistenceException if there's an error accessing the data store
     */
    public Optional<Item> getItemById(String itemId) throws PersistenceException {
        if (itemId == null || itemId.trim().isEmpty()) {
            logger.warn("getItemById called with null or empty itemId");
            throw new IllegalArgumentException("Item ID cannot be null or empty");
        }
        try {
            logger.debug("Getting item by ID: {}", itemId);
            Optional<Item> item = itemDao.getItemById(itemId);
            
            if (item.isPresent()) {
                logger.info("Found item: {}", itemId);
            } else {
                logger.info("Item not found: {}", itemId);
            }
            
            return item;
        } catch (Exception e) {
            logger.error("Error getting item by ID {}: {}", itemId, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve item", e);
        }
    }

    /**
     * Gets a player's complete inventory.
     *
     * @param playerId The ID of the player
     * @return List of inventory items for the player
     * @throws PersistenceException if there's an error accessing the data store
     */
    public List<PlayerInventoryItem> getPlayerInventory(String playerId) throws PersistenceException {
        if (playerId == null || playerId.trim().isEmpty()) {
            logger.warn("getPlayerInventory called with null or empty playerId");
            throw new IllegalArgumentException("Player ID cannot be null or empty");
        }
        Player player = playerDao.findPlayerById(playerId);
        if (player == null) {
            logger.warn("Player not found for getPlayerInventory: {}", playerId);
            throw new IllegalArgumentException("Player not found: " + playerId);
        }

        try {
            logger.debug("Getting inventory for player: {}", playerId);
            List<PlayerInventoryItem> inventory = playerInventoryDao.getPlayerInventory(playerId);
            logger.info("Retrieved {} inventory items for player {}", inventory.size(), playerId);
            return inventory;
        } catch (Exception e) {
            logger.error("Error getting player inventory for {}: {}", playerId, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve player inventory", e);
        }
    }

    /**
     * Gets a player's active (usable) inventory items only.
     *
     * @param playerId The ID of the player
     * @return List of active inventory items for the player
     * @throws PersistenceException if there's an error accessing the data store
     */
    public List<PlayerInventoryItem> getActivePlayerInventory(String playerId) throws PersistenceException {
        try {
            if (playerId == null || playerId.trim().isEmpty()) {
                throw new IllegalArgumentException("Player ID cannot be null or empty");
            }
            
            logger.debug("Getting active inventory for player: {}", playerId);
            List<PlayerInventoryItem> inventory = playerInventoryDao.getActivePlayerInventory(playerId);
            logger.info("Retrieved {} active inventory items for player {}", inventory.size(), playerId);
            return inventory;
        } catch (Exception e) {
            logger.error("Error getting active player inventory for {}: {}", playerId, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve active player inventory", e);
        }
    }

    /**
     * Purchases an item for a player using a payment method.
     * This creates a transaction record and adds the item to the player's inventory.
     *
     * @param playerId The ID of the player making the purchase
     * @param itemId The ID of the item to purchase
     * @param paymentMethodId The Stripe payment method ID
     * @return The transaction record of the purchase
     * @throws PersistenceException if there's an error processing the purchase
     */
    public Transaction purchaseItem(String playerId, String itemId, String paymentMethodId) 
            throws PersistenceException {
        return purchaseItem(playerId, itemId, paymentMethodId, 1, null);
    }

    /**
     * Purchases an item for a player with specified quantity and game context.
     *
     * @param playerId The ID of the player making the purchase
     * @param itemId The ID of the item to purchase
     * @param paymentMethodId The Stripe payment method ID
     * @param quantity The quantity to purchase
     * @param gameId The game context (optional)
     * @return The transaction record of the purchase
     * @throws PersistenceException if there's an error processing the purchase
     */
    public Transaction purchaseItem(String playerId, String itemId, String paymentMethodId, 
                                   int quantity, String gameId) throws PersistenceException {
        try {
            // Validate inputs
            if (playerId == null || playerId.trim().isEmpty()) {
                throw new IllegalArgumentException("Player ID cannot be null or empty");
            }
            if (itemId == null || itemId.trim().isEmpty()) {
                throw new IllegalArgumentException("Item ID cannot be null or empty");
            }
            if (paymentMethodId == null || paymentMethodId.trim().isEmpty()) {
                throw new IllegalArgumentException("Payment method ID cannot be null or empty");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }

            logger.info("Processing item purchase: player={}, item={}, quantity={}, gameId={}", 
                playerId, itemId, quantity, gameId);

            // 1. Verify the player exists
            Player player = playerDao.findPlayerById(playerId);
            if (player == null) {
                throw new IllegalArgumentException("Player not found: " + playerId);
            }

            // 2. Verify the item exists and is available
            Optional<Item> itemOpt = itemDao.getItemById(itemId);
            if (itemOpt.isEmpty()) {
                throw new IllegalArgumentException("Item not found: " + itemId);
            }
            
            Item item = itemOpt.get();
            if (!item.isAvailable()) {
                throw new IllegalArgumentException("Item is not available for purchase: " + itemId);
            }

            // 3. Calculate total cost
            long totalCostCents = item.getPrice() * quantity;
            logger.debug("Calculated total cost: {} cents for {} x {}", totalCostCents, quantity, item.getName());

            // 4. Create transaction record - this will be PENDING until payment processes
            Transaction transaction = new Transaction();
            transaction.setTransactionId(UUID.randomUUID().toString());
            transaction.setPlayerId(playerId);
            transaction.setGameId(gameId);
            transaction.setItemId(itemId);
            transaction.setTransactionType(Transaction.TransactionType.IAP_ITEM);
            transaction.setAmount(totalCostCents);
            transaction.setCurrency("USD");
            transaction.setPaymentGateway("Stripe");
            transaction.setPaymentMethodDetails(paymentMethodId);
            transaction.setDescription(String.format("Purchase of %dx %s", quantity, item.getName()));
            transaction.setStatus(Transaction.TransactionStatus.PENDING);
            transaction.putMetadata("quantity", String.valueOf(quantity));
            transaction.putMetadata("itemName", item.getName());
            transaction.putMetadata("itemType", item.getItemType().name());
            transaction.initializeTimestamps();

            // 5. Save the transaction
            transactionDao.saveTransaction(transaction);
            logger.info("Created transaction {} for item purchase", transaction.getTransactionId());

            // Note: The actual payment processing would happen in the PaymentHandler
            // For now, we're just creating the transaction record as PENDING
            // The PaymentHandler would update the transaction status and grant the item
            // upon successful payment

            return transaction;

        } catch (Exception e) {
            logger.error("Error processing item purchase for player {}, item {}: {}", 
                playerId, itemId, e.getMessage(), e);
            throw new PersistenceException("Failed to process item purchase", e);
        }
    }

    /**
     * Grants an item directly to a player's inventory (for admin use, rewards, etc.).
     * This bypasses the payment system.
     *
     * @param playerId The ID of the player to grant the item to
     * @param itemId The ID of the item to grant
     * @param quantity The quantity to grant
     * @param gameId The game context (optional)
     * @return The inventory item that was created or updated
     * @throws PersistenceException if there's an error granting the item
     */
    public PlayerInventoryItem grantItemToPlayer(String playerId, String itemId, int quantity, String gameId) 
            throws PersistenceException {
        try {
            // Validate inputs
            if (playerId == null || playerId.trim().isEmpty()) {
                throw new IllegalArgumentException("Player ID cannot be null or empty");
            }
            if (itemId == null || itemId.trim().isEmpty()) {
                throw new IllegalArgumentException("Item ID cannot be null or empty");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }

            logger.info("Granting item to player: player={}, item={}, quantity={}, gameId={}", 
                playerId, itemId, quantity, gameId);

            // 1. Verify the player exists
            Player player = playerDao.findPlayerById(playerId);
            if (player == null) {
                throw new IllegalArgumentException("Player not found: " + playerId);
            }

            // 2. Verify the item exists
            Optional<Item> itemOpt = itemDao.getItemById(itemId);
            if (itemOpt.isEmpty()) {
                throw new IllegalArgumentException("Item not found: " + itemId);
            }

            // 3. Grant the item to the player's inventory
            PlayerInventoryItem inventoryItem = playerInventoryDao.grantItemToPlayer(playerId, itemId, quantity, gameId);
            logger.info("Successfully granted {}x {} to player {}", quantity, itemOpt.get().getName(), playerId);

            return inventoryItem;

        } catch (Exception e) {
            logger.error("Error granting item to player {}, item {}: {}", playerId, itemId, e.getMessage(), e);
            throw new PersistenceException("Failed to grant item to player", e);
        }
    }

    /**
     * Uses an inventory item (consumes it and applies its effect).
     *
     * @param playerId The ID of the player
     * @param inventoryItemId The ID of the inventory item to use
     * @return The updated inventory item if successful, empty if item cannot be used
     * @throws PersistenceException if there's an error using the item
     */
    public Optional<PlayerInventoryItem> useInventoryItem(String playerId, String inventoryItemId) 
            throws PersistenceException {
        try {
            if (playerId == null || playerId.trim().isEmpty()) {
                throw new IllegalArgumentException("Player ID cannot be null or empty");
            }
            if (inventoryItemId == null || inventoryItemId.trim().isEmpty()) {
                throw new IllegalArgumentException("Inventory item ID cannot be null or empty");
            }

            logger.debug("Using inventory item: player={}, inventoryItemId={}", playerId, inventoryItemId);

            // 1. Verify the player exists
            Player player = playerDao.findPlayerById(playerId);
            if (player == null) {
                throw new IllegalArgumentException("Player not found: " + playerId);
            }

            // 2. Use the item through the DAO
            Optional<PlayerInventoryItem> result = playerInventoryDao.useInventoryItem(playerId, inventoryItemId);
            
            if (result.isPresent()) {
                logger.info("Successfully used inventory item {} for player {}", inventoryItemId, playerId);
            } else {
                logger.warn("Could not use inventory item {} for player {} - item not found or cannot be used", 
                    inventoryItemId, playerId);
            }

            return result;

        } catch (Exception e) {
            logger.error("Error using inventory item {} for player {}: {}", inventoryItemId, playerId, e.getMessage(), e);
            throw new PersistenceException("Failed to use inventory item", e);
        }
    }

    /**
     * Gets the total quantity of a specific item type that a player owns.
     *
     * @param playerId The ID of the player
     * @param itemId The ID of the item type
     * @return The total quantity of the item the player owns
     * @throws PersistenceException if there's an error accessing the data store
     */
    public int getTotalItemQuantityForPlayer(String playerId, String itemId) throws PersistenceException {
        if (playerId == null || playerId.trim().isEmpty()) {
            logger.warn("getTotalItemQuantityForPlayer called with null or empty playerId");
            throw new IllegalArgumentException("Player ID cannot be null or empty");
        }
        if (itemId == null || itemId.trim().isEmpty()) {
            logger.warn("getTotalItemQuantityForPlayer called with null or empty itemId");
            throw new IllegalArgumentException("Item ID cannot be null or empty");
        }
        Player player = playerDao.findPlayerById(playerId);
        if (player == null) {
            logger.warn("Player not found for getTotalItemQuantityForPlayer: {}", playerId);
            throw new IllegalArgumentException("Player not found: " + playerId);
        }

        try {
            logger.debug("Getting total item quantity for player: {}, item: {}", playerId, itemId);
            int quantity = playerInventoryDao.getTotalItemQuantityForPlayer(playerId, itemId);
            logger.debug("Player {} has {} of item {}", playerId, quantity, itemId);
            return quantity;

        } catch (Exception e) {
            logger.error("Error getting total item quantity for player {}, item {}: {}", 
                playerId, itemId, e.getMessage(), e);
            throw new PersistenceException("Failed to get total item quantity", e);
        }
    }

    /**
     * Gets items within a price range.
     *
     * @param minPrice Minimum price in cents
     * @param maxPrice Maximum price in cents
     * @return List of items within the price range
     * @throws PersistenceException if there's an error accessing the data store
     */
    public List<Item> getItemsByPriceRange(Long minPrice, Long maxPrice) throws PersistenceException {
        try {
            if (minPrice == null || maxPrice == null || minPrice < 0 || maxPrice < 0 || minPrice > maxPrice) {
                throw new IllegalArgumentException("Invalid price range");
            }

            logger.debug("Getting items by price range: {} - {}", minPrice, maxPrice);
            List<Item> items = itemDao.getItemsByPriceRange(minPrice, maxPrice);
            logger.info("Retrieved {} items in price range {} - {}", items.size(), minPrice, maxPrice);
            return items;

        } catch (Exception e) {
            logger.error("Error getting items by price range {}-{}: {}", minPrice, maxPrice, e.getMessage(), e);
            throw new PersistenceException("Failed to get items by price range", e);
        }
    }

    /**
     * Updates an item's availability status.
     *
     * @param itemId The ID of the item
     * @param isActive Whether the item should be active/available
     * @return The updated item if successful, empty if item not found
     * @throws PersistenceException if there's an error updating the item
     */
    public Optional<Item> updateItemAvailability(String itemId, boolean isActive) throws PersistenceException {
        try {
            if (itemId == null || itemId.trim().isEmpty()) {
                throw new IllegalArgumentException("Item ID cannot be null or empty");
            }

            logger.debug("Updating item {} availability to {}", itemId, isActive);
            Optional<Item> result = itemDao.updateItemActiveStatus(itemId, isActive);
            
            if (result.isPresent()) {
                logger.info("Updated item {} availability to {}", itemId, isActive);
            } else {
                logger.warn("Could not update item {} availability - item not found", itemId);
            }

            return result;

        } catch (Exception e) {
            logger.error("Error updating item {} availability: {}", itemId, e.getMessage(), e);
            throw new PersistenceException("Failed to update item availability", e);
        }
    }
} 