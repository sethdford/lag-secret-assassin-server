package com.assassin.dao;

import com.assassin.model.Item;
import com.assassin.exception.PersistenceException;

import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for managing Item entities.
 * Provides methods for CRUD operations on the Items table.
 */
public interface ItemDao {

    /**
     * Saves an item to the database.
     *
     * @param item The item to save.
     * @throws PersistenceException if the save operation fails.
     */
    void saveItem(Item item) throws PersistenceException;

    /**
     * Retrieves an item by its ID.
     *
     * @param itemId The ID of the item.
     * @return An Optional containing the item if found, or empty otherwise.
     * @throws PersistenceException if the retrieval fails.
     */
    Optional<Item> getItemById(String itemId) throws PersistenceException;

    /**
     * Retrieves all active items available for purchase.
     *
     * @return A list of all active items.
     * @throws PersistenceException if the retrieval fails.
     */
    List<Item> getAllActiveItems() throws PersistenceException;

    /**
     * Retrieves all items (including inactive ones).
     *
     * @return A list of all items.
     * @throws PersistenceException if the retrieval fails.
     */
    List<Item> getAllItems() throws PersistenceException;

    /**
     * Retrieves items by their type.
     *
     * @param itemType The type of items to retrieve.
     * @return A list of items of the specified type.
     * @throws PersistenceException if the retrieval fails.
     */
    List<Item> getItemsByType(Item.ItemType itemType) throws PersistenceException;

    /**
     * Updates an existing item.
     *
     * @param item The item with updated information.
     * @return The updated item, or Optional.empty() if not found.
     * @throws PersistenceException if the update operation fails.
     */
    Optional<Item> updateItem(Item item) throws PersistenceException;

    /**
     * Updates the active status of an item.
     *
     * @param itemId The ID of the item to update.
     * @param isActive The new active status.
     * @return The updated item, or Optional.empty() if not found.
     * @throws PersistenceException if the update operation fails.
     */
    Optional<Item> updateItemActiveStatus(String itemId, boolean isActive) throws PersistenceException;

    /**
     * Updates the price of an item.
     *
     * @param itemId The ID of the item to update.
     * @param newPrice The new price in cents.
     * @return The updated item, or Optional.empty() if not found.
     * @throws PersistenceException if the update operation fails.
     */
    Optional<Item> updateItemPrice(String itemId, Long newPrice) throws PersistenceException;

    /**
     * Deletes an item from the database.
     * Note: This is a hard delete. Consider using updateItemActiveStatus instead.
     *
     * @param itemId The ID of the item to delete.
     * @return true if the item was deleted, false if not found.
     * @throws PersistenceException if the delete operation fails.
     */
    boolean deleteItem(String itemId) throws PersistenceException;

    /**
     * Retrieves items within a price range.
     *
     * @param minPrice Minimum price in cents (inclusive).
     * @param maxPrice Maximum price in cents (inclusive).
     * @return A list of items within the specified price range.
     * @throws PersistenceException if the retrieval fails.
     */
    List<Item> getItemsByPriceRange(Long minPrice, Long maxPrice) throws PersistenceException;
} 