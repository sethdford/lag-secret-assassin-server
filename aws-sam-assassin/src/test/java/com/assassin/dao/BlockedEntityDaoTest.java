package com.assassin.dao;

import com.assassin.model.BlockedEntity;
import com.assassin.util.DynamoDbClientProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockedEntityDaoTest {

    @Mock
    private DynamoDbEnhancedClient mockEnhancedClient;
    
    @Mock
    private DynamoDbTable<BlockedEntity> mockTable;
    
    @Mock
    private DynamoDbIndex<BlockedEntity> entityTypeIndex;
    
    @Mock
    private DynamoDbIndex<BlockedEntity> expirationIndex;
    
    @Mock
    private PageIterable<BlockedEntity> pageIterable;
    
    @Mock
    private Page<BlockedEntity> page;

    private DynamoDbBlockedEntityDao blockedEntityDao;
    private MockedStatic<DynamoDbClientProvider> mockClientProvider;
    private MockedStatic<TableSchema> mockTableSchema;

    @BeforeEach
    void setUp() {
        // Mock the static method call to get the enhanced client
        mockClientProvider = Mockito.mockStatic(DynamoDbClientProvider.class);
        mockClientProvider.when(DynamoDbClientProvider::getDynamoDbEnhancedClient).thenReturn(mockEnhancedClient);

        // Create the actual schema
        var actualSchema = TableSchema.fromBean(BlockedEntity.class);

        // Mock the static method TableSchema.fromBean
        mockTableSchema = Mockito.mockStatic(TableSchema.class);
        lenient().when(TableSchema.fromBean(BlockedEntity.class)).thenReturn(actualSchema);

        // Mock the enhanced client returning the mocked table
        lenient().when(mockEnhancedClient.table(anyString(), any(TableSchema.class))).thenReturn(mockTable);
        when(mockTable.index("EntityTypeIndex")).thenReturn(entityTypeIndex);
        when(mockTable.index("ExpirationIndex")).thenReturn(expirationIndex);
        
        blockedEntityDao = new DynamoDbBlockedEntityDao(mockEnhancedClient);
    }

    @AfterEach
    void tearDown() {
        mockClientProvider.close();
        mockTableSchema.close();
    }

    @Test
    void shouldSaveBlockedEntity() {
        // Given
        BlockedEntity blockedEntity = createTestBlockedEntity("192.168.1.1", "ip");
        doNothing().when(mockTable).putItem(any(BlockedEntity.class));

        // When
        BlockedEntity result = blockedEntityDao.saveBlockedEntity(blockedEntity);

        // Then
        assertNotNull(result);
        assertEquals("192.168.1.1", result.getEntityId());
        assertEquals("ip", result.getEntityType());
        verify(mockTable).putItem(blockedEntity);
    }

    @Test
    void shouldGetBlockedEntityById() {
        // Given
        String entityId = "192.168.1.1";
        BlockedEntity blockedEntity = createTestBlockedEntity(entityId, "ip");
        when(mockTable.getItem(any(Key.class))).thenReturn(blockedEntity);

        // When
        Optional<BlockedEntity> result = blockedEntityDao.getBlockedEntityById(entityId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(entityId, result.get().getEntityId());
        verify(mockTable).getItem(Key.builder().partitionValue(entityId).build());
    }

    @Test
    void shouldReturnEmptyWhenEntityNotFound() {
        // Given
        String entityId = "nonexistent";
        when(mockTable.getItem(any(Key.class))).thenReturn(null);

        // When
        Optional<BlockedEntity> result = blockedEntityDao.getBlockedEntityById(entityId);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void shouldCheckIfEntityIsBlocked() {
        // Given
        String entityId = "192.168.1.1";
        BlockedEntity blockedEntity = createTestBlockedEntity(entityId, "ip");
        blockedEntity.setActive(true);
        when(mockTable.getItem(any(Key.class))).thenReturn(blockedEntity);

        // When
        boolean result = blockedEntityDao.isEntityBlocked(entityId);

        // Then
        assertTrue(result);
    }

    @Test
    void shouldReturnFalseForInactiveBlock() {
        // Given
        String entityId = "192.168.1.1";
        BlockedEntity blockedEntity = createTestBlockedEntity(entityId, "ip");
        blockedEntity.setActive(false);
        when(mockTable.getItem(any(Key.class))).thenReturn(blockedEntity);

        // When
        boolean result = blockedEntityDao.isEntityBlocked(entityId);

        // Then
        assertFalse(result);
    }

    @Test
    void shouldReturnFalseForExpiredBlock() {
        // Given
        String entityId = "192.168.1.1";
        BlockedEntity blockedEntity = createTestBlockedEntity(entityId, "ip");
        blockedEntity.setActive(true);
        blockedEntity.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS).toString());
        when(mockTable.getItem(any(Key.class))).thenReturn(blockedEntity);

        // When
        boolean result = blockedEntityDao.isEntityBlocked(entityId);

        // Then
        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenEntityNotFound() {
        // Given
        String entityId = "nonexistent";
        when(mockTable.getItem(any(Key.class))).thenReturn(null);

        // When
        boolean result = blockedEntityDao.isEntityBlocked(entityId);

        // Then
        assertFalse(result);
    }

    @Test
    void shouldGetBlockedEntitiesByType() {
        // Given
        String entityType = "ip";
        List<BlockedEntity> entities = Arrays.asList(
            createTestBlockedEntity("192.168.1.1", entityType),
            createTestBlockedEntity("192.168.1.2", entityType)
        );
        
        when(page.items()).thenReturn(entities);
        when(pageIterable.stream()).thenReturn(Stream.of(page));
        when(entityTypeIndex.query(any(QueryConditional.class))).thenReturn(pageIterable);

        // When
        List<BlockedEntity> result = blockedEntityDao.getBlockedEntitiesByType(entityType);

        // Then
        assertEquals(2, result.size());
        assertEquals("192.168.1.1", result.get(0).getEntityId());
        assertEquals("192.168.1.2", result.get(1).getEntityId());
    }

    @Test
    void shouldDeactivateBlockedEntity() {
        // Given
        String entityId = "192.168.1.1";
        BlockedEntity blockedEntity = createTestBlockedEntity(entityId, "ip");
        blockedEntity.setActive(true);
        when(mockTable.getItem(any(Key.class))).thenReturn(blockedEntity);
        doNothing().when(mockTable).putItem(any(BlockedEntity.class));

        // When
        boolean result = blockedEntityDao.deactivateBlockedEntity(entityId);

        // Then
        assertTrue(result);
        verify(mockTable).putItem(any(BlockedEntity.class));
    }

    @Test
    void shouldReturnFalseWhenDeactivatingNonexistentEntity() {
        // Given
        String entityId = "nonexistent";
        when(mockTable.getItem(any(Key.class))).thenReturn(null);

        // When
        boolean result = blockedEntityDao.deactivateBlockedEntity(entityId);

        // Then
        assertFalse(result);
        verify(mockTable, never()).putItem(any(BlockedEntity.class));
    }

    @Test
    void shouldHandleExceptionInSaveBlockedEntity() {
        // Given
        BlockedEntity blockedEntity = createTestBlockedEntity("192.168.1.1", "ip");
        doThrow(new RuntimeException("DynamoDB error")).when(mockTable).putItem(any(BlockedEntity.class));

        // When
        BlockedEntity result = blockedEntityDao.saveBlockedEntity(blockedEntity);

        // Then
        assertNull(result);
    }

    @Test
    void shouldHandleExceptionInIsEntityBlocked() {
        // Given
        String entityId = "192.168.1.1";
        when(mockTable.getItem(any(Key.class))).thenThrow(new RuntimeException("DynamoDB error"));

        // When
        boolean result = blockedEntityDao.isEntityBlocked(entityId);

        // Then
        assertFalse(result); // Should default to false on error
    }

    private BlockedEntity createTestBlockedEntity(String entityId, String entityType) {
        BlockedEntity entity = new BlockedEntity();
        entity.setEntityId(entityId);
        entity.setEntityType(entityType);
        entity.setBlockReason("Test blocking");
        entity.setBlockedBy("SYSTEM");
        entity.setActive(true);
        entity.setViolationCount(1);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("testData", "true");
        entity.setMetadata(metadata);
        
        return entity;
    }
} 