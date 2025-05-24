package com.assassin.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * PlayerInventoryItem model representing items owned by players in their inventories.
 * Tracks quantities, acquisition time, expiration, and usage details.
 */
@DynamoDbBean
public class PlayerInventoryItem {

    /**
     * Status of the inventory item
     */
    public enum InventoryItemStatus {
        ACTIVE,         // Item is available for use
        USED,           // Item has been consumed/used
        EXPIRED,        // Item has expired and cannot be used
        LOCKED          // Item is temporarily locked (e.g., during cooldown)
    }

    // Fields
    private String playerId;                    // Partition Key
    private String inventoryItemId;             // Sort Key (unique per player)
    private String itemId;                      // Foreign key to Item table
    private Integer quantity;                   // Number of this item the player owns
    private String acquiredAt;                  // When the item was acquired
    private String expiresAt;                   // When the item expires (null if no expiration)
    private String lastUsedAt;                  // When the item was last used (null if never used)
    private String gameId;                      // Optional: Game context where item was acquired
    private InventoryItemStatus status;         // Current status of the item
    private Integer usageCount;                 // How many times this item has been used
    private String cooldownExpiresAt;           // When the item cooldown expires (null if no cooldown)
    private Map<String, String> metadata;       // Additional properties (purchase source, effects, etc.)

    public PlayerInventoryItem() {
        // Default constructor for DynamoDB
        this.quantity = 1;
        this.usageCount = 0;
        this.status = InventoryItemStatus.ACTIVE;
        this.metadata = new HashMap<>();
    }

    // Partition Key
    @DynamoDbPartitionKey
    @DynamoDbAttribute("PlayerID")
    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    // Sort Key
    @DynamoDbSortKey
    @DynamoDbAttribute("InventoryItemID")
    public String getInventoryItemId() {
        return inventoryItemId;
    }

    public void setInventoryItemId(String inventoryItemId) {
        this.inventoryItemId = inventoryItemId;
    }

    // GSI for querying by ItemID
    @DynamoDbSecondaryPartitionKey(indexNames = {"ItemIdIndex"})
    @DynamoDbAttribute("ItemID")
    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    @DynamoDbAttribute("Quantity")
    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    @DynamoDbSecondarySortKey(indexNames = {"ItemIdIndex"})
    @DynamoDbAttribute("AcquiredAt")
    public String getAcquiredAt() {
        return acquiredAt;
    }

    public void setAcquiredAt(String acquiredAt) {
        this.acquiredAt = acquiredAt;
    }

    @DynamoDbAttribute("ExpiresAt")
    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    @DynamoDbAttribute("LastUsedAt")
    public String getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(String lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    @DynamoDbAttribute("GameID")
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    @DynamoDbAttribute("Status")
    public InventoryItemStatus getStatus() {
        return status;
    }

    public void setStatus(InventoryItemStatus status) {
        this.status = status;
    }

    @DynamoDbAttribute("UsageCount")
    public Integer getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(Integer usageCount) {
        this.usageCount = usageCount;
    }

    @DynamoDbAttribute("CooldownExpiresAt")
    public String getCooldownExpiresAt() {
        return cooldownExpiresAt;
    }

    public void setCooldownExpiresAt(String cooldownExpiresAt) {
        this.cooldownExpiresAt = cooldownExpiresAt;
    }

    @DynamoDbAttribute("Metadata")
    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    // Utility methods
    public void putMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    public void initializeAcquisition() {
        String now = Instant.now().toString();
        this.acquiredAt = now;
    }

    public void markAsUsed() {
        this.lastUsedAt = Instant.now().toString();
        this.usageCount = this.usageCount != null ? this.usageCount + 1 : 1;
    }

    public void decrementQuantity() {
        if (this.quantity != null && this.quantity > 0) {
            this.quantity--;
            if (this.quantity == 0) {
                this.status = InventoryItemStatus.USED;
            }
        }
    }

    public void incrementQuantity(int amount) {
        this.quantity = this.quantity != null ? this.quantity + amount : amount;
        if (this.status == InventoryItemStatus.USED && this.quantity > 0) {
            this.status = InventoryItemStatus.ACTIVE;
        }
    }

    // Convenience methods for checking item state
    public boolean isActive() {
        return InventoryItemStatus.ACTIVE.equals(status);
    }

    public boolean isExpired() {
        if (InventoryItemStatus.EXPIRED.equals(status)) {
            return true;
        }
        if (expiresAt != null) {
            try {
                Instant expiration = Instant.parse(expiresAt);
                return Instant.now().isAfter(expiration);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public boolean isOnCooldown() {
        if (cooldownExpiresAt != null) {
            try {
                Instant cooldownExpiration = Instant.parse(cooldownExpiresAt);
                return Instant.now().isBefore(cooldownExpiration);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public boolean canUse() {
        return isActive() && !isExpired() && !isOnCooldown() && 
               quantity != null && quantity > 0;
    }

    public boolean hasQuantity() {
        return quantity != null && quantity > 0;
    }

    // Auto-update status based on expiration
    public void updateStatusIfExpired() {
        if (isExpired() && !InventoryItemStatus.EXPIRED.equals(status)) {
            this.status = InventoryItemStatus.EXPIRED;
        }
    }

    // Set expiration based on duration
    public void setExpirationFromDuration(Integer durationSeconds) {
        if (durationSeconds != null && durationSeconds > 0) {
            Instant expiration = Instant.now().plusSeconds(durationSeconds);
            this.expiresAt = expiration.toString();
        }
    }

    // Set cooldown based on duration
    public void setCooldownFromDuration(Integer cooldownSeconds) {
        if (cooldownSeconds != null && cooldownSeconds > 0) {
            Instant cooldownEnd = Instant.now().plusSeconds(cooldownSeconds);
            this.cooldownExpiresAt = cooldownEnd.toString();
            this.status = InventoryItemStatus.LOCKED;
        }
    }

    @Override
    public String toString() {
        return "PlayerInventoryItem{" +
               "playerId='" + playerId + '\'' +
               ", inventoryItemId='" + inventoryItemId + '\'' +
               ", itemId='" + itemId + '\'' +
               ", quantity=" + quantity +
               ", acquiredAt='" + acquiredAt + '\'' +
               ", expiresAt='" + expiresAt + '\'' +
               ", status=" + status +
               ", usageCount=" + usageCount +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PlayerInventoryItem that = (PlayerInventoryItem) o;

        if (playerId != null ? !playerId.equals(that.playerId) : that.playerId != null) return false;
        return inventoryItemId != null ? inventoryItemId.equals(that.inventoryItemId) : that.inventoryItemId == null;
    }

    @Override
    public int hashCode() {
        int result = playerId != null ? playerId.hashCode() : 0;
        result = 31 * result + (inventoryItemId != null ? inventoryItemId.hashCode() : 0);
        return result;
    }
} 