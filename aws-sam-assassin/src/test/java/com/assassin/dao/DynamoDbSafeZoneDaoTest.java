package com.assassin.dao;

import com.assassin.exception.PersistenceException;
import com.assassin.model.SafeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamoDbSafeZoneDaoTest {

    @Mock
    private DynamoDbEnhancedClient mockClient;

    @Mock
    private DynamoDbTable<SafeZone> mockTable;

    @Mock
    private DynamoDbIndex<SafeZone> mockIndex;

    @Mock
    private PageIterable<SafeZone> mockPageIterable;

    @Mock
    private Page<SafeZone> mockPage;

    private DynamoDbSafeZoneDao safeZoneDao;
    private SafeZone testSafeZone;
    private String testGameId = "test-game-123";
    private String testSafeZoneId = "test-zone-456";
    private String testPlayerId = "test-player-789";

    @BeforeEach
    void setUp() {
        // Set up environment variable for table name
        System.setProperty("SAFE_ZONES_TABLE_NAME", "test-safe-zones");
        
        // Create test safe zone
        testSafeZone = SafeZone.createPublicZone(
            testGameId,
            "Test Safe Zone",
            "A test safe zone for unit testing",
            40.7128, // NYC latitude
            -74.0060, // NYC longitude
            100.0, // 100 meter radius
            testPlayerId
        );
        testSafeZone.setSafeZoneId(testSafeZoneId);

        // Mock the table and index initialization
        when(mockClient.table(anyString(), any(TableSchema.class))).thenReturn(mockTable);
        when(mockTable.index(anyString())).thenReturn(mockIndex);

        // Initialize DAO with mocked client
        safeZoneDao = new DynamoDbSafeZoneDao(mockClient);
    }

    @Test
    void testGetSafeZoneById_Success() {
        // Arrange
        when(mockTable.getItem(any(Key.class))).thenReturn(testSafeZone);

        // Act
        Optional<SafeZone> result = safeZoneDao.getSafeZoneById(testSafeZoneId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testSafeZone.getSafeZoneId(), result.get().getSafeZoneId());
        verify(mockTable).getItem(any(Key.class));
    }

    @Test
    void testGetSafeZoneById_NotFound() {
        // Arrange
        when(mockTable.getItem(any(Key.class))).thenReturn(null);

        // Act
        Optional<SafeZone> result = safeZoneDao.getSafeZoneById("non-existent");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetSafeZoneById_DynamoDbException() {
        // Arrange
        when(mockTable.getItem(any(Key.class))).thenThrow(DynamoDbException.builder().message("Test exception").build());

        // Act & Assert
        assertThrows(PersistenceException.class, () -> safeZoneDao.getSafeZoneById(testSafeZoneId));
    }

    @Test
    void testGetSafeZonesByGameId_Success() {
        // Arrange
        List<SafeZone> expectedZones = Arrays.asList(testSafeZone);
        when(mockIndex.query(any(QueryEnhancedRequest.class))).thenReturn(mockPageIterable);
        when(mockPageIterable.stream()).thenReturn(Stream.of(mockPage));
        when(mockPage.items()).thenReturn(expectedZones);

        // Act
        List<SafeZone> result = safeZoneDao.getSafeZonesByGameId(testGameId);

        // Assert
        assertEquals(1, result.size());
        assertEquals(testSafeZone.getSafeZoneId(), result.get(0).getSafeZoneId());
        verify(mockIndex).query(any(QueryEnhancedRequest.class));
    }

    @Test
    void testSaveSafeZone_Success() {
        // Arrange
        doNothing().when(mockTable).putItem(any(SafeZone.class));

        // Act
        assertDoesNotThrow(() -> safeZoneDao.saveSafeZone(testSafeZone));

        // Assert
        verify(mockTable).putItem(testSafeZone);
        assertNotNull(testSafeZone.getCreatedAt());
        assertNotNull(testSafeZone.getLastModifiedAt());
    }

    @Test
    void testSaveSafeZone_ValidationFailure() {
        // Arrange
        SafeZone invalidZone = new SafeZone();
        invalidZone.setSafeZoneId("invalid");
        // Missing required fields to trigger validation failure

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> safeZoneDao.saveSafeZone(invalidZone));
        verify(mockTable, never()).putItem(any(SafeZone.class));
    }

    @Test
    void testDeleteSafeZone_Success() {
        // Arrange - mock deleteItem to accept DeleteItemEnhancedRequest
        when(mockTable.deleteItem(any(software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest.class))).thenReturn(null);

        // Act
        assertDoesNotThrow(() -> safeZoneDao.deleteSafeZone(testSafeZoneId));

        // Assert
        verify(mockTable, times(1)).deleteItem(any(software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest.class));
    }

    @Test
    void testGetSafeZonesByType_Success() {
        // Arrange
        SafeZone privateZone = SafeZone.createPrivateZone(
            testGameId, "Private Zone", "Test private zone",
            40.7500, -74.0000, 50.0, testPlayerId, new HashSet<>()
        );
        
        List<SafeZone> allZones = Arrays.asList(testSafeZone, privateZone);
        when(mockIndex.query(any(QueryEnhancedRequest.class))).thenReturn(mockPageIterable);
        when(mockPageIterable.stream()).thenReturn(Stream.of(mockPage));
        when(mockPage.items()).thenReturn(allZones);

        // Act
        List<SafeZone> publicZones = safeZoneDao.getSafeZonesByType(testGameId, SafeZone.SafeZoneType.PUBLIC);

        // Assert
        assertEquals(1, publicZones.size());
        assertEquals(SafeZone.SafeZoneType.PUBLIC, publicZones.get(0).getType());
    }

    @Test
    void testGetSafeZonesByOwner_Success() {
        // Arrange
        SafeZone ownedZone = SafeZone.createPrivateZone(
            testGameId, "Owned Zone", "Test owned zone",
            40.7500, -74.0000, 50.0, testPlayerId, new HashSet<>()
        );
        SafeZone notOwnedZone = SafeZone.createPublicZone(
            testGameId, "Not Owned", "Not owned zone",
            40.7600, -74.0100, 75.0, "other-player"
        );
        
        List<SafeZone> allZones = Arrays.asList(ownedZone, notOwnedZone);
        when(mockIndex.query(any(QueryEnhancedRequest.class))).thenReturn(mockPageIterable);
        when(mockPageIterable.stream()).thenReturn(Stream.of(mockPage));
        when(mockPage.items()).thenReturn(allZones);

        // Act
        List<SafeZone> ownedZones = safeZoneDao.getSafeZonesByOwner(testGameId, testPlayerId);

        // Assert
        assertEquals(1, ownedZones.size());
        assertEquals(testPlayerId, ownedZones.get(0).getCreatedBy());
    }

    @Test
    void testUpdateRelocatableSafeZone_Success() {
        // Arrange
        SafeZone relocatableZone = SafeZone.createRelocatableZone(
            testGameId, "Relocatable Zone", "Test relocatable zone",
            40.7128, -74.0060, 100.0, testPlayerId
        );
        relocatableZone.setSafeZoneId(testSafeZoneId);
        
        when(mockTable.getItem(any(Key.class))).thenReturn(relocatableZone);
        when(mockTable.updateItem(any(SafeZone.class))).thenReturn(null);

        double newLat = 40.7500;
        double newLon = -74.0100;
        String newTime = Instant.now().toString();
        int newCount = 1;

        // Act
        assertDoesNotThrow(() -> safeZoneDao.updateRelocatableSafeZone(testSafeZoneId, newLat, newLon, newTime, newCount));

        // Assert
        verify(mockTable).updateItem(any(SafeZone.class));
        assertEquals(newLat, relocatableZone.getLatitude());
        assertEquals(newLon, relocatableZone.getLongitude());
        assertEquals(newTime, relocatableZone.getLastRelocationTime());
        assertEquals(newCount, relocatableZone.getRelocationCount());
    }

    @Test
    void testUpdateRelocatableSafeZone_NotRelocatable() {
        // Arrange
        when(mockTable.getItem(any(Key.class))).thenReturn(testSafeZone); // PUBLIC zone

        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> safeZoneDao.updateRelocatableSafeZone(testSafeZoneId, 40.7500, -74.0100, Instant.now().toString(), 1));
    }

    @Test
    void testGetActiveSafeZones_Success() {
        // Arrange
        long currentTime = System.currentTimeMillis();
        SafeZone activeZone = testSafeZone;
        SafeZone inactiveZone = SafeZone.createPublicZone(testGameId, "Inactive", "Inactive zone", 40.7500, -74.0100, 50.0, testPlayerId);
        inactiveZone.setIsActive(false);

        List<SafeZone> allZones = Arrays.asList(activeZone, inactiveZone);
        when(mockIndex.query(any(QueryEnhancedRequest.class))).thenReturn(mockPageIterable);
        when(mockPageIterable.stream()).thenReturn(Stream.of(mockPage));
        when(mockPage.items()).thenReturn(allZones);

        // Act
        List<SafeZone> activeZones = safeZoneDao.getActiveSafeZones(testGameId, currentTime);

        // Assert
        assertEquals(1, activeZones.size());
        assertTrue(activeZones.get(0).getIsActive());
    }

    @Test
    void testGetExpiredSafeZones_Success() {
        // Arrange
        long currentTime = System.currentTimeMillis();
        SafeZone activeZone = testSafeZone;
        
        // Create a timed zone that has expired
        SafeZone expiredZone = SafeZone.createTimedZone(
            testGameId, "Expired Zone", "Expired timed zone",
            40.7500, -74.0100, 50.0, testPlayerId,
            Instant.ofEpochMilli(currentTime - 3600000).toString(), // Started 1 hour ago
            Instant.ofEpochMilli(currentTime - 1800000).toString()  // Ended 30 minutes ago
        );

        List<SafeZone> allZones = Arrays.asList(activeZone, expiredZone);
        when(mockIndex.query(any(QueryEnhancedRequest.class))).thenReturn(mockPageIterable);
        when(mockPageIterable.stream()).thenReturn(Stream.of(mockPage));
        when(mockPage.items()).thenReturn(allZones);

        // Act
        List<SafeZone> expiredZones = safeZoneDao.getExpiredSafeZones(testGameId, currentTime);

        // Assert
        assertEquals(1, expiredZones.size());
        assertEquals(SafeZone.SafeZoneType.TIMED, expiredZones.get(0).getType());
    }

    @Test
    void testGetRelocatableSafeZones_Success() {
        // Arrange
        SafeZone relocatableZone = SafeZone.createRelocatableZone(
            testGameId, "Relocatable", "Relocatable zone", 40.7500, -74.0100, 50.0, testPlayerId
        );
        SafeZone publicZone = testSafeZone;

        List<SafeZone> ownedZones = Arrays.asList(relocatableZone, publicZone);
        when(mockIndex.query(any(QueryEnhancedRequest.class))).thenReturn(mockPageIterable);
        when(mockPageIterable.stream()).thenReturn(Stream.of(mockPage));
        when(mockPage.items()).thenReturn(ownedZones);

        // Act
        List<SafeZone> relocatableZones = safeZoneDao.getRelocatableSafeZones(testGameId, testPlayerId);

        // Assert
        assertEquals(1, relocatableZones.size());
        assertEquals(SafeZone.SafeZoneType.RELOCATABLE, relocatableZones.get(0).getType());
    }

    // Note: Enhanced methods like saveSafeZones and updateSafeZoneStatus 
    // are tested via integration tests to avoid complex mocking
} 