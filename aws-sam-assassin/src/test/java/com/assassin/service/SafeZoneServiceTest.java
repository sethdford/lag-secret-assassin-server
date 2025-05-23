package com.assassin.service;

import com.assassin.dao.SafeZoneDao;
import com.assassin.exception.PersistenceException;
import com.assassin.exception.SafeZoneNotFoundException;
import com.assassin.exception.ValidationException;
import com.assassin.model.SafeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SafeZoneServiceTest {

    @Mock
    private SafeZoneDao mockSafeZoneDao;

    private SafeZoneService safeZoneService;

    private String testGameId = "test-game-123";
    private String testPlayerId = "test-player-456";
    private String testSafeZoneId = "test-zone-789";

    @BeforeEach
    void setUp() {
        safeZoneService = new SafeZoneService(mockSafeZoneDao);
    }

    @Test
    void testCreatePublicSafeZone_Success() throws Exception {
        // Arrange
        String name = "Test Public Zone";
        String description = "A test public safe zone";
        double latitude = 40.7128;
        double longitude = -74.0060;
        double radius = 100.0;

        doNothing().when(mockSafeZoneDao).saveSafeZone(any(SafeZone.class));

        // Act
        SafeZone result = safeZoneService.createPublicSafeZone(testGameId, name, description, latitude, longitude, radius, testPlayerId);

        // Assert
        assertNotNull(result);
        assertEquals(testGameId, result.getGameId());
        assertEquals(name, result.getName());
        assertEquals(SafeZone.SafeZoneType.PUBLIC, result.getType());
        assertEquals(testPlayerId, result.getCreatedBy());
        verify(mockSafeZoneDao).saveSafeZone(any(SafeZone.class));
    }

    @Test
    void testCreatePrivateSafeZone_Success() throws Exception {
        // Arrange
        String name = "Test Private Zone";
        String description = "A test private safe zone";
        double latitude = 40.7128;
        double longitude = -74.0060;
        double radius = 50.0;
        Set<String> authorizedIds = Set.of(testPlayerId, "player2");

        doNothing().when(mockSafeZoneDao).saveSafeZone(any(SafeZone.class));

        // Act
        SafeZone result = safeZoneService.createPrivateSafeZone(testGameId, name, description, latitude, longitude, radius, testPlayerId, authorizedIds);

        // Assert
        assertNotNull(result);
        assertEquals(testGameId, result.getGameId());
        assertEquals(SafeZone.SafeZoneType.PRIVATE, result.getType());
        assertTrue(result.getAuthorizedPlayerIds().contains(testPlayerId));
        verify(mockSafeZoneDao).saveSafeZone(any(SafeZone.class));
    }

    @Test
    void testCreateTimedSafeZone_Success() throws Exception {
        // Arrange
        String name = "Test Timed Zone";
        String description = "A test timed safe zone";
        double latitude = 40.7128;
        double longitude = -74.0060;
        double radius = 75.0;
        String startTime = Instant.now().toString();
        String endTime = Instant.now().plusSeconds(3600).toString(); // 1 hour later

        doNothing().when(mockSafeZoneDao).saveSafeZone(any(SafeZone.class));

        // Act
        SafeZone result = safeZoneService.createTimedSafeZone(testGameId, name, description, latitude, longitude, radius, testPlayerId, startTime, endTime);

        // Assert
        assertNotNull(result);
        assertEquals(testGameId, result.getGameId());
        assertEquals(SafeZone.SafeZoneType.TIMED, result.getType());
        assertEquals(startTime, result.getStartTime());
        assertEquals(endTime, result.getEndTime());
        verify(mockSafeZoneDao).saveSafeZone(any(SafeZone.class));
    }

    @Test
    void testCreateRelocatableSafeZone_Success() throws Exception {
        // Arrange
        String name = "Test Relocatable Zone";
        String description = "A test relocatable safe zone";
        double latitude = 40.7128;
        double longitude = -74.0060;
        double radius = 25.0;

        doNothing().when(mockSafeZoneDao).saveSafeZone(any(SafeZone.class));

        // Act
        SafeZone result = safeZoneService.createRelocatableSafeZone(testGameId, name, description, latitude, longitude, radius, testPlayerId);

        // Assert
        assertNotNull(result);
        assertEquals(testGameId, result.getGameId());
        assertEquals(SafeZone.SafeZoneType.RELOCATABLE, result.getType());
        assertEquals(testPlayerId, result.getCreatedBy());
        verify(mockSafeZoneDao).saveSafeZone(any(SafeZone.class));
    }

    @Test
    void testPurchasePrivateZone_Success() throws Exception {
        // Arrange
        String name = "Purchased Private Zone";
        String description = "A purchased private safe zone";
        double latitude = 40.7128;
        double longitude = -74.0060;
        double radius = 100.0;
        String paymentMethodId = "pm_test_123"; // Valid payment method (doesn't start with "invalid")
        Long priceInCents = 1000L; // $10.00

        when(mockSafeZoneDao.getSafeZonesByOwner(testGameId, testPlayerId)).thenReturn(Collections.emptyList());
        doNothing().when(mockSafeZoneDao).saveSafeZone(any(SafeZone.class));

        // Act
        SafeZone result = safeZoneService.purchasePrivateZone(testGameId, name, description, latitude, longitude, 
                                                             radius, testPlayerId, null, paymentMethodId, priceInCents);

        // Assert
        assertNotNull(result);
        assertEquals(testGameId, result.getGameId());
        assertEquals(SafeZone.SafeZoneType.PRIVATE, result.getType());
        assertEquals(testPlayerId, result.getCreatedBy());
        assertTrue(result.getAuthorizedPlayerIds().contains(testPlayerId));
        verify(mockSafeZoneDao).saveSafeZone(any(SafeZone.class));
    }

    @Test
    void testPurchasePrivateZone_ExceedsMaximumLimit() {
        // Arrange
        List<SafeZone> existingPrivateZones = Arrays.asList(
            createMockPrivateZone("zone1"),
            createMockPrivateZone("zone2"),
            createMockPrivateZone("zone3")
        );

        when(mockSafeZoneDao.getSafeZonesByOwner(testGameId, testPlayerId)).thenReturn(existingPrivateZones);

        // Act & Assert
        assertThrows(ValidationException.class, () -> 
            safeZoneService.purchasePrivateZone(testGameId, "New Zone", "Description", 40.7128, -74.0060, 
                                               100.0, testPlayerId, null, "pm_test_123", 1000L));
    }

    @Test
    void testPurchasePrivateZone_InvalidPaymentMethod() {
        // Arrange
        when(mockSafeZoneDao.getSafeZonesByOwner(testGameId, testPlayerId)).thenReturn(Collections.emptyList());

        // Act & Assert
        // Payment method starting with "invalid" will fail in the mock payment processor
        assertThrows(RuntimeException.class, () -> 
            safeZoneService.purchasePrivateZone(testGameId, "New Zone", "Description", 40.7128, -74.0060, 
                                               100.0, testPlayerId, null, "invalid_pm_123", 1000L));
    }

    @Test
    void testCreatePublicZoneWithAuth_Success() throws Exception {
        // Arrange
        doNothing().when(mockSafeZoneDao).saveSafeZone(any(SafeZone.class));

        // Act
        SafeZone result = safeZoneService.createPublicZoneWithAuth(testGameId, "Public Zone", "Description", 
                                                                  40.7128, -74.0060, 500.0, testPlayerId, true);

        // Assert
        assertNotNull(result);
        assertEquals(SafeZone.SafeZoneType.PUBLIC, result.getType());
        verify(mockSafeZoneDao).saveSafeZone(any(SafeZone.class));
    }

    @Test
    void testCreatePublicZoneWithAuth_NotAuthorized() {
        // Act & Assert
        assertThrows(ValidationException.class, () -> 
            safeZoneService.createPublicZoneWithAuth(testGameId, "Public Zone", "Description", 
                                                    40.7128, -74.0060, 500.0, testPlayerId, false));
    }

    @Test
    void testRelocateZone_Success() throws Exception {
        // Arrange
        SafeZone relocatableZone = SafeZone.createRelocatableZone(testGameId, "Relocatable Zone", "Description", 
                                                                 40.7128, -74.0060, 100.0, testPlayerId);
        relocatableZone.setSafeZoneId(testSafeZoneId);

        when(mockSafeZoneDao.getSafeZoneById(testSafeZoneId)).thenReturn(Optional.of(relocatableZone));
        doNothing().when(mockSafeZoneDao).updateRelocatableSafeZone(anyString(), anyDouble(), anyDouble(), anyString(), anyInt());

        // Mock return after update
        SafeZone updatedZone = SafeZone.createRelocatableZone(testGameId, "Relocatable Zone", "Description", 
                                                             40.7500, -74.0100, 100.0, testPlayerId);
        updatedZone.setSafeZoneId(testSafeZoneId);
        when(mockSafeZoneDao.getSafeZoneById(testSafeZoneId)).thenReturn(Optional.of(updatedZone));

        // Act
        SafeZone result = safeZoneService.relocateZone(testSafeZoneId, testPlayerId, 40.7500, -74.0100);

        // Assert
        assertNotNull(result);
        verify(mockSafeZoneDao).updateRelocatableSafeZone(eq(testSafeZoneId), eq(40.7500), eq(-74.0100), 
                                                         anyString(), anyInt());
    }

    @Test
    void testRelocateZone_NotRelocatable() {
        // Arrange
        SafeZone publicZone = SafeZone.createPublicZone(testGameId, "Public Zone", "Description", 
                                                       40.7128, -74.0060, 100.0, testPlayerId);
        publicZone.setSafeZoneId(testSafeZoneId);

        when(mockSafeZoneDao.getSafeZoneById(testSafeZoneId)).thenReturn(Optional.of(publicZone));

        // Act & Assert
        assertThrows(ValidationException.class, () -> 
            safeZoneService.relocateZone(testSafeZoneId, testPlayerId, 40.7500, -74.0100));
    }

    @Test
    void testRelocateZone_NotOwner() {
        // Arrange
        SafeZone relocatableZone = SafeZone.createRelocatableZone(testGameId, "Relocatable Zone", "Description", 
                                                                 40.7128, -74.0060, 100.0, "other-player");
        relocatableZone.setSafeZoneId(testSafeZoneId);

        when(mockSafeZoneDao.getSafeZoneById(testSafeZoneId)).thenReturn(Optional.of(relocatableZone));

        // Act & Assert
        assertThrows(ValidationException.class, () -> 
            safeZoneService.relocateZone(testSafeZoneId, testPlayerId, 40.7500, -74.0100));
    }

    @Test
    void testGetSafeZone_Found() {
        // Arrange
        SafeZone mockZone = SafeZone.createPublicZone(testGameId, "Test Zone", "Description", 
                                                     40.7128, -74.0060, 100.0, testPlayerId);
        when(mockSafeZoneDao.getSafeZoneById(testSafeZoneId)).thenReturn(Optional.of(mockZone));

        // Act
        Optional<SafeZone> result = safeZoneService.getSafeZone(testSafeZoneId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(mockZone, result.get());
    }

    @Test
    void testGetSafeZone_NotFound() {
        // Arrange
        when(mockSafeZoneDao.getSafeZoneById(testSafeZoneId)).thenReturn(Optional.empty());

        // Act
        Optional<SafeZone> result = safeZoneService.getSafeZone(testSafeZoneId);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testIsPlayerInActiveSafeZone_Protected() throws Exception {
        // Arrange
        SafeZone activeZone = SafeZone.createPublicZone(testGameId, "Test Zone", "Description", 
                                                       40.7128, -74.0060, 100.0, testPlayerId);
        activeZone.setIsActive(true);

        when(mockSafeZoneDao.getSafeZonesByGameId(testGameId)).thenReturn(Arrays.asList(activeZone));

        // Act - player is within the zone
        boolean result = safeZoneService.isPlayerInActiveSafeZone(testGameId, testPlayerId, 40.7128, -74.0060, System.currentTimeMillis());

        // Assert
        assertTrue(result);
    }

    @Test
    void testIsPlayerInActiveSafeZone_NotProtected() throws Exception {
        // Arrange
        SafeZone activeZone = SafeZone.createPublicZone(testGameId, "Test Zone", "Description", 
                                                       40.7128, -74.0060, 100.0, testPlayerId);
        activeZone.setIsActive(true);

        when(mockSafeZoneDao.getSafeZonesByGameId(testGameId)).thenReturn(Arrays.asList(activeZone));

        // Act - player is far from the zone
        boolean result = safeZoneService.isPlayerInActiveSafeZone(testGameId, testPlayerId, 40.8000, -74.1000, System.currentTimeMillis());

        // Assert
        assertFalse(result);
    }

    @Test
    void testGetActiveZonesForGame() {
        // Arrange
        long currentTime = System.currentTimeMillis();
        List<SafeZone> activeZones = Arrays.asList(
            SafeZone.createPublicZone(testGameId, "Zone 1", "Description", 40.7128, -74.0060, 100.0, testPlayerId),
            SafeZone.createPublicZone(testGameId, "Zone 2", "Description", 40.7500, -74.0100, 150.0, testPlayerId)
        );

        when(mockSafeZoneDao.getActiveSafeZones(testGameId, currentTime)).thenReturn(activeZones);

        // Act
        List<SafeZone> result = safeZoneService.getActiveZonesForGame(testGameId, currentTime);

        // Assert
        assertEquals(2, result.size());
        verify(mockSafeZoneDao).getActiveSafeZones(testGameId, currentTime);
    }

    @Test
    void testCleanupExpiredTimedZones() throws Exception {
        // Arrange
        // Create expired timed zone
        String pastTime = Instant.now().minusSeconds(3600).toString(); // 1 hour ago
        SafeZone expiredZone = SafeZone.createTimedZone(testGameId, "Expired Zone", "Description", 
                                                       40.7128, -74.0060, 100.0, testPlayerId, 
                                                       Instant.now().minusSeconds(7200).toString(), pastTime);
        expiredZone.setIsActive(true);

        // Create active timed zone
        SafeZone activeZone = SafeZone.createTimedZone(testGameId, "Active Zone", "Description", 
                                                      40.7500, -74.0100, 150.0, testPlayerId,
                                                      Instant.now().minusSeconds(1800).toString(), 
                                                      Instant.now().plusSeconds(1800).toString());
        activeZone.setIsActive(true);

        when(mockSafeZoneDao.getSafeZonesByGameId(testGameId)).thenReturn(Arrays.asList(expiredZone, activeZone));
        doNothing().when(mockSafeZoneDao).saveSafeZone(any(SafeZone.class));

        // Act
        int cleanedUp = safeZoneService.cleanupExpiredTimedZones(testGameId);

        // Assert
        assertEquals(1, cleanedUp);
        verify(mockSafeZoneDao).saveSafeZone(expiredZone);
        assertFalse(expiredZone.getIsActive());
    }

    // Helper method to create mock private zones
    private SafeZone createMockPrivateZone(String id) {
        SafeZone zone = SafeZone.createPrivateZone(testGameId, "Private Zone " + id, "Description", 
                                                  40.7128, -74.0060, 50.0, testPlayerId, Set.of(testPlayerId));
        zone.setSafeZoneId(id);
        return zone;
    }
} 