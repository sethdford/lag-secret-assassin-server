package com.assassin.dao;

import com.assassin.model.PlayerInventoryItem;
import com.assassin.exception.PersistenceException;

import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for managing PlayerInventoryItem entities.
 * Provides methods for CRUD operations on player inventories.
 */
public interface PlayerInventoryDao {

    String ITEM_ID_INDEX_NAME = "ItemIdIndex";

    /**
     * Saves a player inventory item to the database.
     *
     * @param inventoryItem The inventory item to save.
     * @throws PersistenceException if the save operation fails.
     */
    void saveInventoryItem(PlayerInventoryItem inventoryItem) throws PersistenceException;

    /**
     * Retrieves a specific inventory item by player ID and inventory item ID.
     *
     * @param playerId The ID of the player.
     * @param inventoryItemId The inventory item ID.
     * @return An Optional containing the inventory item if found, or empty otherwise.
     * @throws PersistenceException if the retrieval fails.
     */
    Optional<PlayerInventoryItem> getInventoryItem(String playerId, String inventoryItemId) throws PersistenceException;

    /**
     * Retrieves all inventory items for a specific player.
     *
     * @param playerId The ID of the player.
     * @return A list of all inventory items for the player.
     * @throws PersistenceException if the retrieval fails.
     */
    List<PlayerInventoryItem> getPlayerInventory(String playerId) throws PersistenceException;

    /**
     * Retrieves active inventory items for a specific player.
     *
     * @param playerId The ID of the player.
     * @return A list of active inventory items for the player.
     * @throws PersistenceException if the retrieval fails.
     */
    List<PlayerInventoryItem> getActivePlayerInventory(String playerId) throws PersistenceException;

    /**
     * Retrieves inventory items for a player filtered by item ID.
     *
     * @param playerId The ID of the player.
     * @param itemId The ID of the item type.
     * @return A list of inventory items of the specified item type for the player.
     * @throws PersistenceException if the retrieval fails.
     */
    List<PlayerInventoryItem> getPlayerInventoryByItemId(String playerId, String itemId) throws PersistenceException;

    /**
     * Retrieves all players who own a specific item.
     *
     * @param itemId The ID of the item.
     * @param limit Maximum number of results to return.
     * @param exclusiveStartKey Optional pagination key.
     * @return A list of inventory items for all players who own this item.
     * @throws PersistenceException if the retrieval fails.
     */
    List<PlayerInventoryItem> getPlayersWithItem(String itemId, int limit, String exclusiveStartKey) throws PersistenceException;

    /**
     * Updates an existing inventory item.
     *
     * @param inventoryItem The inventory item with updated information.
     * @return The updated inventory item, or Optional.empty() if not found.
     * @throws PersistenceException if the update operation fails.
     */
    Optional<PlayerInventoryItem> updateInventoryItem(PlayerInventoryItem inventoryItem) throws PersistenceException;

    /**
     * Updates the quantity of an inventory item.
     *
     * @param playerId The ID of the player.
     * @param inventoryItemId The inventory item ID.
     * @param newQuantity The new quantity.
     * @return The updated inventory item, or Optional.empty() if not found.
     * @throws PersistenceException if the update operation fails.
     */
    Optional<PlayerInventoryItem> updateInventoryItemQuantity(String playerId, String inventoryItemId, int newQuantity) throws PersistenceException;

    /**
     * Updates the status of an inventory item.
     *
     * @param playerId The ID of the player.
     * @param inventoryItemId The inventory item ID.
     * @param newStatus The new status.
     * @return The updated inventory item, or Optional.empty() if not found.
     * @throws PersistenceException if the update operation fails.
     */
    Optional<PlayerInventoryItem> updateInventoryItemStatus(String playerId, String inventoryItemId, 
                                                           PlayerInventoryItem.InventoryItemStatus newStatus) throws PersistenceException;

    /**
     * Marks an inventory item as used and decrements its quantity.
     *
     * @param playerId The ID of the player.
     * @param inventoryItemId The inventory item ID.
     * @return The updated inventory item, or Optional.empty() if not found or cannot be used.
     * @throws PersistenceException if the operation fails.
     */
    Optional<PlayerInventoryItem> useInventoryItem(String playerId, String inventoryItemId) throws PersistenceException;

    /**
     * Increments the quantity of an existing inventory item or creates a new one if it doesn't exist.
     *
     * @param playerId The ID of the player.
     * @param itemId The ID of the item.
     * @param quantity The quantity to add.
     * @param gameId Optional game context.
     * @return The updated or created inventory item.
     * @throws PersistenceException if the operation fails.
     */
    PlayerInventoryItem grantItemToPlayer(String playerId, String itemId, int quantity, String gameId) throws PersistenceException;

    /**
     * Deletes an inventory item from the database.
     *
     * @param playerId The ID of the player.
     * @param inventoryItemId The inventory item ID.
     * @return true if the item was deleted, false if not found.
     * @throws PersistenceException if the delete operation fails.
     */
    boolean deleteInventoryItem(String playerId, String inventoryItemId) throws PersistenceException;

    /**
     * Retrieves expired inventory items for cleanup.
     *
     * @param limit Maximum number of expired items to return.
     * @return A list of expired inventory items.
     * @throws PersistenceException if the retrieval fails.
     */
    List<PlayerInventoryItem> getExpiredInventoryItems(int limit) throws PersistenceException;

    /**
     * Retrieves inventory items for a player in a specific game context.
     *
     * @param playerId The ID of the player.
     * @param gameId The ID of the game.
     * @return A list of inventory items for the player in the specified game.
     * @throws PersistenceException if the retrieval fails.
     */
    List<PlayerInventoryItem> getPlayerInventoryForGame(String playerId, String gameId) throws PersistenceException;

    /**
     * Counts the total quantity of a specific item type owned by a player.
     *
     * @param playerId The ID of the player.
     * @param itemId The ID of the item.
     * @return The total quantity of the item owned by the player.
     * @throws PersistenceException if the operation fails.
     */
    int getTotalItemQuantityForPlayer(String playerId, String itemId) throws PersistenceException;
} 