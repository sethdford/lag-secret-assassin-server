package com.assassin.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

import java.time.Instant;
import java.util.Map;

/**
 * Model representing a blocked entity (IP address or user) in the security system.
 */
@DynamoDbBean
public class BlockedEntity {
    
    public enum EntityType {
        IP_ADDRESS("ip"),
        USER_ID("user");
        
        private final String value;
        
        EntityType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static EntityType fromValue(String value) {
            for (EntityType type : values()) {
                if (type.getValue().equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown entity type: " + value);
        }
    }
    
    public enum BlockReason {
        RATE_LIMIT_ABUSE("rate_limit_abuse"),
        LOCATION_SPOOFING("location_spoofing"),
        BOT_ACTIVITY("bot_activity"),
        MANUAL_BLOCK("manual_block"),
        SUSPICIOUS_BEHAVIOR("suspicious_behavior"),
        MULTIPLE_VIOLATIONS("multiple_violations");
        
        private final String value;
        
        BlockReason(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static BlockReason fromValue(String value) {
            for (BlockReason reason : values()) {
                if (reason.getValue().equals(value)) {
                    return reason;
                }
            }
            throw new IllegalArgumentException("Unknown block reason: " + value);
        }
    }
    
    private String entityId; // IP address or user ID
    private String entityType; // "ip" or "user"
    private String blockReason; // Reason for blocking
    private String blockedAt; // ISO timestamp when blocked
    private String expiresAt; // ISO timestamp when block expires (null for permanent)
    private String blockedBy; // User ID or system that created the block
    private String notes; // Additional notes about the block
    private Map<String, String> metadata; // Additional metadata
    private boolean isActive; // Whether the block is currently active
    private int violationCount; // Number of violations that led to this block
    
    // GSI for querying by entity type
    private String entityTypeGSI; // Same as entityType, for GSI
    
    // GSI for querying by expiration time
    private String expirationDate; // Date part of expiresAt for efficient querying
    
    public BlockedEntity() {
        this.isActive = true;
        this.violationCount = 1;
        this.blockedAt = Instant.now().toString();
    }
    
    public BlockedEntity(String entityId, EntityType entityType, BlockReason blockReason) {
        this();
        this.entityId = entityId;
        this.entityType = entityType.getValue();
        this.entityTypeGSI = entityType.getValue();
        this.blockReason = blockReason.getValue();
    }
    
    public BlockedEntity(String entityId, EntityType entityType, BlockReason blockReason, String expiresAt) {
        this(entityId, entityType, blockReason);
        this.expiresAt = expiresAt;
        if (expiresAt != null) {
            this.expirationDate = expiresAt.substring(0, 10); // Extract date part
        }
    }
    
    // Getters and setters
    @DynamoDbPartitionKey
    @DynamoDbAttribute("EntityID")
    public String getEntityId() {
        return entityId;
    }
    
    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }
    
    @DynamoDbAttribute("EntityType")
    public String getEntityType() {
        return entityType;
    }
    
    public void setEntityType(String entityType) {
        this.entityType = entityType;
        this.entityTypeGSI = entityType;
    }
    
    public EntityType getEntityTypeEnum() {
        return EntityType.fromValue(entityType);
    }
    
    public void setEntityTypeEnum(EntityType entityType) {
        this.entityType = entityType.getValue();
        this.entityTypeGSI = entityType.getValue();
    }
    
    @DynamoDbAttribute("BlockReason")
    public String getBlockReason() {
        return blockReason;
    }
    
    public void setBlockReason(String blockReason) {
        this.blockReason = blockReason;
    }
    
    public BlockReason getBlockReasonEnum() {
        return BlockReason.fromValue(blockReason);
    }
    
    public void setBlockReasonEnum(BlockReason blockReason) {
        this.blockReason = blockReason.getValue();
    }
    
    @DynamoDbAttribute("BlockedAt")
    public String getBlockedAt() {
        return blockedAt;
    }
    
    public void setBlockedAt(String blockedAt) {
        this.blockedAt = blockedAt;
    }
    
    @DynamoDbAttribute("ExpiresAt")
    public String getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
        if (expiresAt != null) {
            this.expirationDate = expiresAt.substring(0, 10);
        } else {
            this.expirationDate = null;
        }
    }
    
    @DynamoDbAttribute("BlockedBy")
    public String getBlockedBy() {
        return blockedBy;
    }
    
    public void setBlockedBy(String blockedBy) {
        this.blockedBy = blockedBy;
    }
    
    @DynamoDbAttribute("Notes")
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    @DynamoDbAttribute("Metadata")
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    @DynamoDbAttribute("IsActive")
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    @DynamoDbAttribute("ViolationCount")
    public int getViolationCount() {
        return violationCount;
    }
    
    public void setViolationCount(int violationCount) {
        this.violationCount = violationCount;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "EntityTypeIndex")
    @DynamoDbAttribute("EntityTypeGSI")
    public String getEntityTypeGSI() {
        return entityTypeGSI;
    }
    
    public void setEntityTypeGSI(String entityTypeGSI) {
        this.entityTypeGSI = entityTypeGSI;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "ExpirationIndex")
    @DynamoDbAttribute("ExpirationDate")
    public String getExpirationDate() {
        return expirationDate;
    }
    
    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }
    
    /**
     * Check if this block is currently expired.
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false; // Permanent blocks never expire
        }
        return Instant.now().isAfter(Instant.parse(expiresAt));
    }
    
    /**
     * Check if this block is currently effective (active and not expired).
     */
    public boolean isEffective() {
        return isActive && !isExpired();
    }
    
    @Override
    public String toString() {
        return "BlockedEntity{" +
                "entityId='" + entityId + '\'' +
                ", entityType='" + entityType + '\'' +
                ", blockReason='" + blockReason + '\'' +
                ", blockedAt='" + blockedAt + '\'' +
                ", expiresAt='" + expiresAt + '\'' +
                ", isActive=" + isActive +
                ", violationCount=" + violationCount +
                '}';
    }
} 