package com.assassin.dao;

import com.assassin.model.BlockedEntity;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation of BlockedEntityDao.
 */
public class DynamoDbBlockedEntityDao implements BlockedEntityDao {
    
    private final DynamoDbTable<BlockedEntity> table;
    private final DynamoDbIndex<BlockedEntity> entityTypeIndex;
    private final DynamoDbIndex<BlockedEntity> expirationIndex;
    
    public DynamoDbBlockedEntityDao(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("BlockedEntities", TableSchema.fromBean(BlockedEntity.class));
        this.entityTypeIndex = table.index("EntityTypeIndex");
        this.expirationIndex = table.index("ExpirationIndex");
    }
    
    @Override
    public BlockedEntity saveBlockedEntity(BlockedEntity blockedEntity) {
        try {
            if (blockedEntity.getBlockedAt() == null) {
                blockedEntity.setBlockedAt(Instant.now().toString());
            }
            if (blockedEntity.getExpirationDate() == null && blockedEntity.getExpiresAt() != null) {
                // Extract date from expiresAt timestamp for GSI
                try {
                    Instant expiration = Instant.parse(blockedEntity.getExpiresAt());
                    LocalDate expirationDate = LocalDate.ofInstant(expiration, java.time.ZoneOffset.UTC);
                    blockedEntity.setExpirationDate(expirationDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                } catch (RuntimeException e) {
                    // If parsing fails, set to today's date
                    blockedEntity.setExpirationDate(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
                }
            }
            
            table.putItem(blockedEntity);
            return blockedEntity;
        } catch (RuntimeException e) {
            return null;
        }
    }
    
    @Override
    public Optional<BlockedEntity> getBlockedEntityById(String entityId) {
        try {
            BlockedEntity item = table.getItem(Key.builder().partitionValue(entityId).build());
            return Optional.ofNullable(item);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public boolean isEntityBlocked(String entityId) {
        Optional<BlockedEntity> entity = getBlockedEntityById(entityId);
        if (entity.isEmpty() || !entity.get().isActive()) {
            return false;
        }
        
        // Check if the block has expired
        String expiresAt = entity.get().getExpiresAt();
        if (expiresAt != null) {
            try {
                Instant expiration = Instant.parse(expiresAt);
                if (Instant.now().isAfter(expiration)) {
                    // Block has expired, deactivate it
                    deactivateBlockedEntity(entityId);
                    return false;
                }
            } catch (RuntimeException e) {
                // If we can't parse the expiration, assume it's still active
            }
        }
        
        return true;
    }
    
    @Override
    public List<BlockedEntity> getBlockedEntitiesByType(String entityType) {
        try {
            return entityTypeIndex.query(QueryConditional.keyEqualTo(Key.builder()
                    .partitionValue(entityType)
                    .build()))
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            return List.of();
        }
    }
    
    @Override
    public List<BlockedEntity> getBlockedEntitiesByExpirationDate(String expirationDate) {
        try {
            return expirationIndex.query(QueryConditional.keyEqualTo(Key.builder()
                    .partitionValue(expirationDate)
                    .build()))
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            return List.of();
        }
    }
    
    @Override
    public BlockedEntity updateBlockedEntity(BlockedEntity blockedEntity) {
        return saveBlockedEntity(blockedEntity);
    }
    
    @Override
    public boolean deactivateBlockedEntity(String entityId) {
        try {
            Optional<BlockedEntity> entity = getBlockedEntityById(entityId);
            if (entity.isPresent()) {
                BlockedEntity blockedEntity = entity.get();
                blockedEntity.setActive(false);
                saveBlockedEntity(blockedEntity);
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }
    
    @Override
    public boolean deleteBlockedEntity(String entityId) {
        try {
            table.deleteItem(Key.builder().partitionValue(entityId).build());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
    
    @Override
    public List<BlockedEntity> getActiveBlockedEntities() {
        try {
            return table.scan(ScanEnhancedRequest.builder()
                    .filterExpression(software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                            .expression("IsActive = :active")
                            .putExpressionValue(":active", AttributeValue.builder().bool(true).build())
                            .build())
                    .build())
                    .items()
                    .stream()
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            return List.of();
        }
    }
    
    @Override
    public int cleanupExpiredBlocks() {
        int deactivatedCount = 0;
        String currentTime = Instant.now().toString();
        
        try {
            List<BlockedEntity> activeEntities = getActiveBlockedEntities();
            for (BlockedEntity entity : activeEntities) {
                String expiresAt = entity.getExpiresAt();
                if (expiresAt != null) {
                    try {
                        Instant expiration = Instant.parse(expiresAt);
                        if (Instant.now().isAfter(expiration)) {
                            if (deactivateBlockedEntity(entity.getEntityId())) {
                                deactivatedCount++;
                            }
                        }
                    } catch (RuntimeException e) {
                        // Skip entities with invalid expiration timestamps
                    }
                }
            }
        } catch (RuntimeException e) {
            // Log error but don't throw
        }
        
        return deactivatedCount;
    }
} 