package com.assassin.service;

import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.PersistenceException;
import com.assassin.model.SafeZone;
import com.assassin.model.Player;
import com.assassin.model.Notification;
import com.assassin.service.SafeZoneViolationDetector.ViolationType;
import com.assassin.service.SafeZoneViolationDetector.ViolationCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SafeZoneViolationDetectorTest {

    @Mock
    private SafeZoneService mockSafeZoneService;
    
    @Mock
    private PlayerDao mockPlayerDao;
    
    @Mock
    private GameDao mockGameDao;
    
    @Mock
    private NotificationService mockNotificationService;

    private SafeZoneViolationDetector violationDetector;
    
    private String testGameId = "game-123";
    private String testPlayerId = "player-456";
    private String testAttackerId = "attacker-789";
    private String testVictimId = "victim-123";
    
    // Fixed timestamp to avoid timing issues
    private long fixedTimestamp = 1000000000L;
    
    // Test coordinates
    private double testLatitude = 40.7128;
    private double testLongitude = -74.0060;
    private double nearbyLatitude = 40.7129; // Very close, within 100m radius
    private double nearbyLongitude = -74.0061;
    private double farLatitude = 40.8000; // Far away, outside radius
    private double farLongitude = -74.1000;

    @BeforeEach
    void setUp() {
        violationDetector = new SafeZoneViolationDetector(
            mockSafeZoneService, mockPlayerDao, mockGameDao, mockNotificationService
        );
    }

    @Test
    void testCheckPlayerPosition_PlayerInAuthorizedPublicZone() {
        // Arrange
        SafeZone publicZone = SafeZone.createPublicZone(testGameId, "Library", "Public safe zone", 40.7128, -74.0060, 100.0, "creator-123");
        when(mockSafeZoneService.getActiveZonesForGame(eq(testGameId), anyLong())).thenReturn(Arrays.asList(publicZone));

        // Act
        ViolationCheckResult result = violationDetector.checkPlayerPosition(testGameId, testPlayerId, 40.7129, -74.0061, fixedTimestamp);

        // Assert
        assertFalse(result.isViolation());
        assertTrue(result.isPlayerProtected());
        verify(mockSafeZoneService).getActiveZonesForGame(eq(testGameId), anyLong());
    }

    @Test
    void testCheckPlayerPosition_PlayerInUnauthorizedPrivateZone() {
        // Arrange - Create mutable set for authorized players
        Set<String> authorizedPlayers = new HashSet<>();
        authorizedPlayers.add("authorized-player");
        SafeZone privateZone = SafeZone.createPrivateZone(testGameId, "Private Zone", "Private zone", 40.7128, -74.0060, 100.0, "owner-456", authorizedPlayers);
        when(mockSafeZoneService.getActiveZonesForGame(eq(testGameId), anyLong())).thenReturn(Arrays.asList(privateZone));

        // Act  
        ViolationCheckResult result = violationDetector.checkPlayerPosition(testGameId, testPlayerId, 40.7129, -74.0061, fixedTimestamp);

        // Assert
        assertTrue(result.isViolation());
        assertEquals(ViolationType.UNAUTHORIZED_ZONE_ACCESS, result.getViolationType());
        assertFalse(result.isPlayerProtected());
        assertEquals(privateZone, result.getInvolvedZone());
        verify(mockSafeZoneService).getActiveZonesForGame(eq(testGameId), anyLong());
    }

    @Test
    void testCheckPlayerPosition_PlayerOutsideAllZones() {
        // Arrange
        when(mockSafeZoneService.getActiveZonesForGame(eq(testGameId), anyLong())).thenReturn(Collections.emptyList());

        // Act
        ViolationCheckResult result = violationDetector.checkPlayerPosition(testGameId, testPlayerId, 40.7500, -74.0100, fixedTimestamp);

        // Assert
        assertFalse(result.isViolation());
        assertFalse(result.isPlayerProtected());
        verify(mockSafeZoneService).getActiveZonesForGame(eq(testGameId), anyLong());
    }

    @Test
    void testCheckPlayerPosition_NoActiveZones() {
        // Arrange
        when(mockSafeZoneService.getActiveZonesForGame(eq(testGameId), anyLong())).thenReturn(Collections.emptyList());

        // Act
        ViolationCheckResult result = violationDetector.checkPlayerPosition(testGameId, testPlayerId, 40.7128, -74.0060, fixedTimestamp);

        // Assert
        assertFalse(result.isViolation());
        assertFalse(result.isPlayerProtected());
        verify(mockSafeZoneService).getActiveZonesForGame(eq(testGameId), anyLong());
    }

    @Test
    void testCheckEliminationAttempt_VictimProtected() {
        // Arrange
        SafeZone protectedZone = SafeZone.createPublicZone(testGameId, "Library", "Public safe zone", 40.7128, -74.0060, 100.0, "creator-123");
        when(mockSafeZoneService.getActiveZonesForGame(eq(testGameId), anyLong())).thenReturn(Arrays.asList(protectedZone));

        // Act
        ViolationCheckResult result = violationDetector.checkEliminationAttempt(testGameId, testPlayerId, "victim-456", 40.7500, -74.0100, 40.7129, -74.0061, fixedTimestamp);

        // Assert
        assertTrue(result.isViolation());
        assertEquals(ViolationType.ELIMINATION_ATTEMPT_IN_SAFE_ZONE, result.getViolationType());
        assertEquals(protectedZone, result.getInvolvedZone());
        verify(mockSafeZoneService, atLeastOnce()).getActiveZonesForGame(eq(testGameId), anyLong());
    }

    @Test
    void testCheckEliminationAttempt_VictimNotProtected() {
        // Arrange
        when(mockSafeZoneService.getActiveZonesForGame(eq(testGameId), anyLong())).thenReturn(Collections.emptyList());

        // Act
        ViolationCheckResult result = violationDetector.checkEliminationAttempt(testGameId, testPlayerId, "victim-456", 40.7500, -74.0100, 40.7500, -74.0100, fixedTimestamp);

        // Assert
        assertFalse(result.isViolation());
        assertFalse(result.isPlayerProtected());
        verify(mockSafeZoneService, atLeastOnce()).getActiveZonesForGame(eq(testGameId), anyLong());
    }

    @Test
    void testCheckEliminationAttempt_AttackerInSafeZone() {
        // Arrange
        SafeZone attackerZone = SafeZone.createPublicZone(testGameId, "Attacker Zone", "Safe zone", 40.7128, -74.0060, 100.0, "creator-123");
        when(mockSafeZoneService.getActiveZonesForGame(eq(testGameId), anyLong())).thenReturn(Arrays.asList(attackerZone));

        // Act - attacker is in zone, victim is outside
        ViolationCheckResult result = violationDetector.checkEliminationAttempt(testGameId, testPlayerId, "victim-456", 40.7129, -74.0061, 40.7500, -74.0100, fixedTimestamp);

        // Assert - currently allows attacking from safe zones 
        assertFalse(result.isViolation());
        assertFalse(result.isPlayerProtected());
        verify(mockSafeZoneService, atLeastOnce()).getActiveZonesForGame(eq(testGameId), anyLong());
    }

    @Test
    void testHandleExpiredSafeZones_Success() throws Exception {
        // Arrange
        int expectedExpiredCount = 2;
        when(mockSafeZoneService.cleanupExpiredTimedZones(testGameId)).thenReturn(expectedExpiredCount);

        // Act
        int actualExpiredCount = violationDetector.handleExpiredSafeZones(testGameId);

        // Assert
        assertEquals(expectedExpiredCount, actualExpiredCount);
        verify(mockSafeZoneService).cleanupExpiredTimedZones(testGameId);
    }

    @Test
    void testHandleExpiredSafeZones_NoExpiredZones() throws Exception {
        // Arrange
        when(mockSafeZoneService.cleanupExpiredTimedZones(testGameId)).thenReturn(0);

        // Act
        int actualExpiredCount = violationDetector.handleExpiredSafeZones(testGameId);

        // Assert
        assertEquals(0, actualExpiredCount);
        verify(mockSafeZoneService).cleanupExpiredTimedZones(testGameId);
    }

    @Test
    void testViolationCheckResult_StaticFactoryMethods() {
        // Test noViolation factory method
        ViolationCheckResult noViolation = ViolationCheckResult.noViolation(true);
        assertFalse(noViolation.isViolation());
        assertTrue(noViolation.isPlayerProtected());
        assertNull(noViolation.getViolationType());
        assertEquals("No violation detected", noViolation.getMessage());

        // Test violation factory method
        SafeZone testZone = SafeZone.createPublicZone(testGameId, "Test", "Test", 0.0, 0.0, 100.0, "admin");
        ViolationCheckResult violation = ViolationCheckResult.violation(
                ViolationType.UNAUTHORIZED_ZONE_ACCESS, "Test violation", testZone, false);
        
        assertTrue(violation.isViolation());
        assertFalse(violation.isPlayerProtected());
        assertEquals(ViolationType.UNAUTHORIZED_ZONE_ACCESS, violation.getViolationType());
        assertEquals("Test violation", violation.getMessage());
        assertEquals(testZone, violation.getInvolvedZone());
    }

    @Test
    void testCheckPlayerPosition_ExceptionHandling() {
        // Arrange
        when(mockSafeZoneService.getActiveZonesForGame(eq(testGameId), anyLong()))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(PersistenceException.class, () -> {
            violationDetector.checkPlayerPosition(testGameId, testPlayerId, 40.7128, -74.0060, fixedTimestamp);
        });
        
        verify(mockSafeZoneService).getActiveZonesForGame(eq(testGameId), anyLong());
    }

    @Test
    void testNotificationFailure_DoesNotThrowException() {
        // Arrange
        SafeZone publicZone = SafeZone.createPublicZone(testGameId, "Library", "Public safe zone", 40.7128, -74.0060, 100.0, "creator-123");
        when(mockSafeZoneService.getActiveZonesForGame(eq(testGameId), anyLong())).thenReturn(Arrays.asList(publicZone));
        
        // Make notification service throw an exception
        doThrow(new RuntimeException("Notification failed")).when(mockNotificationService).sendNotification(any());

        // Act - should not throw despite notification failure
        assertDoesNotThrow(() -> {
            ViolationCheckResult result = violationDetector.checkPlayerPosition(testGameId, testPlayerId, 40.7129, -74.0061, fixedTimestamp);
            assertFalse(result.isViolation());
            assertTrue(result.isPlayerProtected());
        });
        
        verify(mockSafeZoneService).getActiveZonesForGame(eq(testGameId), anyLong());
        verify(mockNotificationService).sendNotification(any());
    }
} 