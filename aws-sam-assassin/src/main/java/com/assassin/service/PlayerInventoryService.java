package com.assassin.service;

import com.assassin.dao.PlayerInventoryDao;
import com.assassin.dao.ItemDao;
import com.assassin.dao.PlayerDao;
import com.assassin.dao.DynamoDbPlayerInventoryDao;
import com.assassin.dao.DynamoDbItemDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.model.PlayerInventoryItem;
import com.assassin.model.Item;
import com.assassin.model.Player;
import com.assassin.exception.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

/**
 * Service for player inventory operations including item usage and effect application.
 */
public class PlayerInventoryService {

    private static final Logger logger = LoggerFactory.getLogger(PlayerInventoryService.class);
    private final PlayerInventoryDao playerInventoryDao;
    private final ItemDao itemDao;
    private final PlayerDao playerDao;

    /**
     * Default constructor.
     */
    public PlayerInventoryService() {
        this.playerInventoryDao = new DynamoDbPlayerInventoryDao();
        this.itemDao = new DynamoDbItemDao();
        this.playerDao = new DynamoDbPlayerDao();
    }

    /**
     * Constructor with dependency injection for testability.
     *
     * @param playerInventoryDao The player inventory DAO
     * @param itemDao The item DAO
     * @param playerDao The player DAO
     */
    public PlayerInventoryService(PlayerInventoryDao playerInventoryDao, ItemDao itemDao, PlayerDao playerDao) {
        this.playerInventoryDao = playerInventoryDao;
        this.itemDao = itemDao;
        this.playerDao = playerDao;
    }

    /**
     * Gets a player's complete inventory.
     *
     * @param playerId The ID of the player
     * @return List of inventory items for the player
     * @throws PersistenceException if there's an error accessing the data store
     */
    public List<PlayerInventoryItem> getPlayerInventory(String playerId) throws PersistenceException {
        try {
            if (playerId == null || playerId.trim().isEmpty()) {
                throw new IllegalArgumentException("Player ID cannot be null or empty");
            }
            
            logger.debug("Getting inventory for player: {}", playerId);
            
            // Verify player exists
            Player player = playerDao.findPlayerById(playerId);
            if (player == null) {
                throw new IllegalArgumentException("Player not found: " + playerId);
            }
            
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
            
            // Verify player exists
            Player player = playerDao.findPlayerById(playerId);
            if (player == null) {
                throw new IllegalArgumentException("Player not found: " + playerId);
            }
            
            List<PlayerInventoryItem> inventory = playerInventoryDao.getActivePlayerInventory(playerId);
            logger.info("Retrieved {} active inventory items for player {}", inventory.size(), playerId);
            return inventory;
        } catch (Exception e) {
            logger.error("Error getting active player inventory for {}: {}", playerId, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve active player inventory", e);
        }
    }

    /**
     * Gets a player's inventory for a specific game.
     *
     * @param playerId The ID of the player
     * @param gameId The ID of the game
     * @return List of inventory items for the player in the game
     * @throws PersistenceException if there's an error accessing the data store
     */
    public List<PlayerInventoryItem> getPlayerInventoryForGame(String playerId, String gameId) throws PersistenceException {
        try {
            if (playerId == null || playerId.trim().isEmpty()) {
                throw new IllegalArgumentException("Player ID cannot be null or empty");
            }
            if (gameId == null || gameId.trim().isEmpty()) {
                throw new IllegalArgumentException("Game ID cannot be null or empty");
            }
            
            logger.debug("Getting inventory for player {} in game {}", playerId, gameId);
            
            // Verify player exists
            Player player = playerDao.findPlayerById(playerId);
            if (player == null) {
                throw new IllegalArgumentException("Player not found: " + playerId);
            }
            
            List<PlayerInventoryItem> inventory = playerInventoryDao.getPlayerInventoryForGame(playerId, gameId);
            logger.info("Retrieved {} inventory items for player {} in game {}", inventory.size(), playerId, gameId);
            return inventory;
        } catch (Exception e) {
            logger.error("Error getting player inventory for {} in game {}: {}", playerId, gameId, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve player inventory for game", e);
        }
    }

    /**
     * Grants an item directly to a player's inventory (for admin use, rewards, etc.).
     *
     * @param playerId The ID of the player to grant the item to
     * @param itemId The ID of the item to grant
     * @param quantity The quantity to grant
     * @return The inventory item that was created or updated
     * @throws PersistenceException if there's an error granting the item
     */
    public PlayerInventoryItem grantItemToPlayer(String playerId, String itemId, int quantity) 
            throws PersistenceException {
        return grantItemToPlayer(playerId, itemId, quantity, null);
    }

    /**
     * Grants an item directly to a player's inventory with game context.
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
     * Uses an inventory item and applies its effects based on the item type and context.
     *
     * @param playerId The ID of the player using the item
     * @param inventoryItemId The ID of the inventory item to use
     * @param context Context information for applying the item effect (game state, target info, etc.)
     * @return Map containing the results of using the item
     * @throws PersistenceException if there's an error using the item
     */
    public Map<String, Object> useItem(String playerId, String inventoryItemId, Map<String, Object> context) 
            throws PersistenceException {
        try {
            if (playerId == null || playerId.trim().isEmpty()) {
                throw new IllegalArgumentException("Player ID cannot be null or empty");
            }
            if (inventoryItemId == null || inventoryItemId.trim().isEmpty()) {
                throw new IllegalArgumentException("Inventory item ID cannot be null or empty");
            }
            if (context == null) {
                context = new HashMap<>();
            }

            logger.info("Using inventory item: player={}, inventoryItemId={}", playerId, inventoryItemId);

            // 1. Verify the player exists
            Player player = playerDao.findPlayerById(playerId);
            if (player == null) {
                throw new IllegalArgumentException("Player not found: " + playerId);
            }

            // 2. Get the inventory item
            Optional<PlayerInventoryItem> inventoryItemOpt = playerInventoryDao.getInventoryItem(playerId, inventoryItemId);
            if (inventoryItemOpt.isEmpty()) {
                throw new IllegalArgumentException("Inventory item not found: " + inventoryItemId);
            }

            PlayerInventoryItem inventoryItem = inventoryItemOpt.get();
            
            // 3. Check if item can be used
            if (!inventoryItem.canUse()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "Item cannot be used (expired, used up, or inactive)");
                result.put("reason", "ITEM_UNUSABLE");
                return result;
            }

            // 4. Get the item definition to understand its effects
            Optional<Item> itemOpt = itemDao.getItemById(inventoryItem.getItemId());
            if (itemOpt.isEmpty()) {
                throw new IllegalArgumentException("Item definition not found: " + inventoryItem.getItemId());
            }

            Item item = itemOpt.get();
            logger.debug("Using item type: {} - {}", item.getItemType(), item.getName());

            // 5. Apply the item effect based on its type
            Map<String, Object> effectResult = applyItemEffect(player, item, inventoryItem, context);

            // 6. Mark the item as used if the effect was successful
            boolean effectSuccessful = (boolean) effectResult.getOrDefault("success", false);
            if (effectSuccessful) {
                Optional<PlayerInventoryItem> usedItem = playerInventoryDao.useInventoryItem(playerId, inventoryItemId);
                if (usedItem.isPresent()) {
                    logger.info("Successfully used inventory item {} for player {}", inventoryItemId, playerId);
                    effectResult.put("itemUsed", true);
                    effectResult.put("remainingQuantity", usedItem.get().getQuantity());
                } else {
                    logger.warn("Item effect applied but failed to mark item as used: {}", inventoryItemId);
                    effectResult.put("itemUsed", false);
                }
            }

            return effectResult;

        } catch (Exception e) {
            logger.error("Error using inventory item {} for player {}: {}", inventoryItemId, playerId, e.getMessage(), e);
            throw new PersistenceException("Failed to use inventory item", e);
        }
    }

    /**
     * Applies the effect of using an item based on its type.
     *
     * @param player The player using the item
     * @param item The item definition
     * @param inventoryItem The inventory item being used
     * @param context Context information for applying the effect
     * @return Map containing the results of applying the effect
     */
    private Map<String, Object> applyItemEffect(Player player, Item item, PlayerInventoryItem inventoryItem, 
                                               Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            switch (item.getItemType()) {
                case REVEAL_TARGET:
                    result = applyRevealTargetEffect(player, item, context);
                    break;
                case REVEAL_HUNTER:
                    result = applyRevealHunterEffect(player, item, context);
                    break;
                case PROXIMITY_ALERT:
                    result = applyProximityAlertEffect(player, item, context);
                    break;
                case TEMPORARY_SAFE_ZONE:
                    result = applyTemporarySafeZoneEffect(player, item, context);
                    break;
                case CLOAKING_DEVICE:
                    result = applyCloakingDeviceEffect(player, item, context);
                    break;
                case SHIELD:
                    result = applyShieldEffect(player, item, context);
                    break;
                case RADAR_SCAN:
                    result = applyRadarScanEffect(player, item, context);
                    break;
                case SPEED_BOOST:
                    result = applySpeedBoostEffect(player, item, context);
                    break;
                case DECOY_LOCATION:
                    result = applyDecoyLocationEffect(player, item, context);
                    break;
                case TARGET_SWAP:
                    result = applyTargetSwapEffect(player, item, context);
                    break;
                case HUNTER_CONFUSION:
                    result = applyHunterConfusionEffect(player, item, context);
                    break;
                case FAKE_ELIMINATION:
                    result = applyFakeEliminationEffect(player, item, context);
                    break;
                case ELITE_INTELLIGENCE:
                    result = applyEliteIntelligenceEffect(player, item, context);
                    break;
                case ZONE_IMMUNITY:
                    result = applyZoneImmunityEffect(player, item, context);
                    break;
                case RESURRECTION_TOKEN:
                    result = applyResurrectionTokenEffect(player, item, context);
                    break;
                default:
                    result.put("success", false);
                    result.put("message", "Unknown item type: " + item.getItemType());
                    result.put("reason", "UNKNOWN_ITEM_TYPE");
                    break;
            }
            
            if ((boolean) result.getOrDefault("success", false)) {
                logger.info("Successfully applied effect for item type {} to player {}", 
                    item.getItemType(), player.getPlayerID());
            } else {
                logger.warn("Failed to apply effect for item type {} to player {}: {}", 
                    item.getItemType(), player.getPlayerID(), result.get("message"));
            }
            
        } catch (Exception e) {
            logger.error("Error applying item effect for {}: {}", item.getItemType(), e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Error applying item effect: " + e.getMessage());
            result.put("reason", "EFFECT_ERROR");
        }
        
        return result;
    }

    // Item effect implementation methods
    
    private Map<String, Object> applyRevealTargetEffect(Player player, Item item, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        // TODO: Implement reveal target logic - would need integration with game state
        result.put("success", true);
        result.put("message", "Target location revealed");
        result.put("effect", "REVEAL_TARGET");
        return result;
    }

    private Map<String, Object> applyRevealHunterEffect(Player player, Item item, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        // TODO: Implement reveal hunter logic
        result.put("success", true);
        result.put("message", "Hunter location revealed");
        result.put("effect", "REVEAL_HUNTER");
        return result;
    }

    private Map<String, Object> applyProximityAlertEffect(Player player, Item item, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        // TODO: Implement proximity alert logic - would need integration with location service
        result.put("success", true);
        result.put("message", "Proximity alert activated - will warn when hunter is nearby");
        result.put("effect", "PROXIMITY_ALERT");
        result.put("duration", item.getDurationSeconds() != null ? item.getDurationSeconds().toString() : "3600"); // 1 hour default
        return result;
    }

    private Map<String, Object> applyTemporarySafeZoneEffect(Player player, Item item, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        // TODO: Implement temporary safe zone creation logic - would need integration with safe zone service
        result.put("success", true);
        result.put("message", "Temporary safe zone created around your location");
        result.put("effect", "TEMPORARY_SAFE_ZONE");
        result.put("duration", item.getDurationSeconds() != null ? item.getDurationSeconds().toString() : "600"); // 10 minutes default
        return result;
    }

    private Map<String, Object> applyCloakingDeviceEffect(Player player, Item item, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        // TODO: Implement cloaking logic - hide location from hunters
        result.put("success", true);
        result.put("message", "Cloaking device activated - location hidden from hunters");
        result.put("effect", "CLOAKING_DEVICE");
        result.put("duration", item.getDurationSeconds() != null ? item.getDurationSeconds().toString() : "1800"); // 30 minutes default
        return result;
    }

    private Map<String, Object> applyShieldEffect(Player player, Item item, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        // TODO: Implement shield logic - protect from one elimination attempt
        result.put("success", true);
        result.put("message", "Shield activated - next elimination attempt will be blocked");
        result.put("effect", "SHIELD");
        result.put("usesRemaining", "1");
        return result;
    }

    private Map<String, Object> applyHunterConfusionEffect(Player player, Item item, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        // TODO: Implement hunter confusion logic
        result.put("success", true);
        result.put("message", "Hunter confused - false target info sent");
        result.put("effect", "HUNTER_CONFUSION");
        return result;
    }

    private Map<String, Object> applyRadarScanEffect(Player player, Item item, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        // TODO: Implement radar scan logic - show all players within radius
        result.put("success", true);
        result.put("message", "Radar scan activated - nearby players revealed");
        result.put("effect", "RADAR_SCAN");
        String radius = item.getEffects() != null ? item.getEffects().getOrDefault("radius", "500") : "500";
        result.put("radius", radius + " meters");
        return result;
    }

    private Map<String, Object> applyDecoyLocationEffect(Player player, Item item, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        // TODO: Implement decoy location logic - send false location to hunter
        result.put("success", true);
        result.put("message", "Decoy location activated - false location sent to hunter");
        result.put("effect", "DECOY_LOCATION");
        result.put("duration", item.getDurationSeconds() != null ? item.getDurationSeconds().toString() : "1800"); // 30 minutes default
        return result;
    }

    private Map<String, Object> applyTargetSwapEffect(Player player, Item item, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        // TODO: Implement target swap logic - force new target assignment
        result.put("success", true);
        result.put("message", "Target swap activated - you have been assigned a new target");
        result.put("effect", "TARGET_SWAP");
        return result;
    }

    private Map<String, Object> applySpeedBoostEffect(Player player, Item item, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        // TODO: Implement speed boost logic - would need player status tracking
        result.put("success", true);
        result.put("message", "Speed boost activated");
        result.put("effect", "SPEED_BOOST");
        result.put("duration", "600"); // 10 minutes default
        return result;
    }

    private Map<String, Object> applyFakeEliminationEffect(Player player, Item item, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        // TODO: Implement fake elimination logic - create false elimination report
        result.put("success", true);
        result.put("message", "Fake elimination report created to confuse other players");
        result.put("effect", "FAKE_ELIMINATION");
        return result;
    }

    private Map<String, Object> applyEliteIntelligenceEffect(Player player, Item item, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        // TODO: Implement elite intelligence logic - full intelligence package
        result.put("success", true);
        result.put("message", "Elite intelligence activated - comprehensive game information revealed");
        result.put("effect", "ELITE_INTELLIGENCE");
        result.put("duration", item.getDurationSeconds() != null ? item.getDurationSeconds().toString() : "3600"); // 1 hour default
        return result;
    }

    private Map<String, Object> applyZoneImmunityEffect(Player player, Item item, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        // TODO: Implement zone immunity logic - protect from shrinking zone damage
        result.put("success", true);
        result.put("message", "Zone immunity activated - protected from shrinking zone damage");
        result.put("effect", "ZONE_IMMUNITY");
        result.put("duration", item.getDurationSeconds() != null ? item.getDurationSeconds().toString() : "1800"); // 30 minutes default
        return result;
    }

    private Map<String, Object> applyResurrectionTokenEffect(Player player, Item item, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        // TODO: Implement resurrection logic - one-time protection from elimination
        result.put("success", true);
        result.put("message", "Resurrection token activated - next elimination will be negated");
        result.put("effect", "RESURRECTION_TOKEN");
        result.put("usesRemaining", "1");
        return result;
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
        try {
            if (playerId == null || playerId.trim().isEmpty()) {
                throw new IllegalArgumentException("Player ID cannot be null or empty");
            }
            if (itemId == null || itemId.trim().isEmpty()) {
                throw new IllegalArgumentException("Item ID cannot be null or empty");
            }

            logger.debug("Getting total quantity of item {} for player {}", itemId, playerId);
            
            // Verify player exists
            Player player = playerDao.findPlayerById(playerId);
            if (player == null) {
                throw new IllegalArgumentException("Player not found: " + playerId);
            }
            
            int quantity = playerInventoryDao.getTotalItemQuantityForPlayer(playerId, itemId);
            logger.debug("Player {} has {} of item {}", playerId, quantity, itemId);
            return quantity;

        } catch (Exception e) {
            logger.error("Error getting total item quantity for player {}, item {}: {}", 
                playerId, itemId, e.getMessage(), e);
            throw new PersistenceException("Failed to get total item quantity", e);
        }
    }
} 