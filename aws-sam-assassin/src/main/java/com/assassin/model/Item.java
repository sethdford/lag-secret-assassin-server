package com.assassin.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * Item model representing the master list of available items in the game.
 * Includes various types of purchasable items with different effects and properties.
 */
@DynamoDbBean
public class Item {

    /**
     * Types of items available in the game.
     */
    public enum ItemType {
        // Intelligence items
        REVEAL_TARGET("Reveals your current target's identity and location"),
        REVEAL_HUNTER("Reveals who is hunting you"),
        PROXIMITY_ALERT("Alerts when your hunter is nearby"),
        
        // Protection items
        TEMPORARY_SAFE_ZONE("Creates a temporary safe zone around your location"),
        CLOAKING_DEVICE("Hides your location from hunters for a duration"),
        SHIELD("Protects from one elimination attempt"),
        
        // Utility items
        RADAR_SCAN("Shows all players within a certain radius"),
        SPEED_BOOST("Increases your movement tracking for stealth"),
        DECOY_LOCATION("Sends false location to your hunter"),
        
        // Strategic items
        TARGET_SWAP("Forces a new target assignment"),
        HUNTER_CONFUSION("Temporarily assigns your hunter a different target"),
        FAKE_ELIMINATION("Creates a false elimination report to confuse others"),
        
        // Premium items
        ELITE_INTELLIGENCE("Full intelligence package for premium subscribers"),
        ZONE_IMMUNITY("Immunity to shrinking zone damage for a period"),
        RESURRECTION_TOKEN("One-time protection from elimination");

        private final String description;

        ItemType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // Fields
    private String itemId;                    // Partition Key
    private String name;
    private String description;
    private ItemType itemType;
    private Long price;                       // Price in cents (USD)
    private Map<String, String> effects;      // Effect parameters (range, duration, strength, etc.)
    private Integer durationSeconds;          // Duration for temporary effects (null for instant effects)
    private Boolean isUsable;                 // Whether the item can be actively used by players
    private Boolean isStackable;              // Whether multiple quantities can be held
    private Boolean isActive;                 // Whether the item is currently available for purchase
    private String createdAt;
    private String updatedAt;
    private Map<String, String> metadata;     // Additional item properties

    public Item() {
        // Default constructor for DynamoDB
        this.effects = new HashMap<>();
        this.metadata = new HashMap<>();
        this.isUsable = true;
        this.isStackable = true;
        this.isActive = true;
    }

    // Partition Key
    @DynamoDbPartitionKey
    @DynamoDbAttribute("ItemID")
    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    @DynamoDbAttribute("Name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @DynamoDbAttribute("Description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @DynamoDbAttribute("ItemType")
    public ItemType getItemType() {
        return itemType;
    }

    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
    }

    @DynamoDbAttribute("Price")
    public Long getPrice() {
        return price;
    }

    public void setPrice(Long price) {
        this.price = price;
    }

    @DynamoDbAttribute("Effects")
    public Map<String, String> getEffects() {
        return effects;
    }

    public void setEffects(Map<String, String> effects) {
        this.effects = effects != null ? effects : new HashMap<>();
    }

    @DynamoDbAttribute("DurationSeconds")
    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    @DynamoDbAttribute("IsUsable")
    public Boolean getIsUsable() {
        return isUsable;
    }

    public void setIsUsable(Boolean isUsable) {
        this.isUsable = isUsable;
    }

    @DynamoDbAttribute("IsStackable")
    public Boolean getIsStackable() {
        return isStackable;
    }

    public void setIsStackable(Boolean isStackable) {
        this.isStackable = isStackable;
    }

    @DynamoDbAttribute("IsActive")
    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    @DynamoDbAttribute("CreatedAt")
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("UpdatedAt")
    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @DynamoDbAttribute("Metadata")
    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    // Utility methods
    public void putEffect(String key, String value) {
        if (this.effects == null) {
            this.effects = new HashMap<>();
        }
        this.effects.put(key, value);
    }

    public void putMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    public void initializeTimestamps() {
        String now = Instant.now().toString();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateTimestamp() {
        this.updatedAt = Instant.now().toString();
    }

    // Convenience methods for checking item properties
    public boolean isTemporary() {
        return durationSeconds != null && durationSeconds > 0;
    }

    public boolean isInstant() {
        return durationSeconds == null || durationSeconds == 0;
    }

    public boolean canStack() {
        return Boolean.TRUE.equals(isStackable);
    }

    public boolean canUse() {
        return Boolean.TRUE.equals(isUsable);
    }

    public boolean isAvailable() {
        return Boolean.TRUE.equals(isActive);
    }

    // Helper method to get price in dollars for display
    public double getPriceInDollars() {
        return price != null ? price / 100.0 : 0.0;
    }

    @Override
    public String toString() {
        return "Item{" +
               "itemId='" + itemId + '\'' +
               ", name='" + name + '\'' +
               ", itemType=" + itemType +
               ", price=" + price +
               ", durationSeconds=" + durationSeconds +
               ", isUsable=" + isUsable +
               ", isStackable=" + isStackable +
               ", isActive=" + isActive +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Item item = (Item) o;
        return itemId != null ? itemId.equals(item.itemId) : item.itemId == null;
    }

    @Override
    public int hashCode() {
        return itemId != null ? itemId.hashCode() : 0;
    }
} 