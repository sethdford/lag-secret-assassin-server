package com.assassin.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assassin.dao.GameDao;
import com.assassin.dao.KillDao;
import com.assassin.dao.PlayerDao;
import com.assassin.model.Game;
import com.assassin.model.Kill;
import com.assassin.model.Notification;
import com.assassin.model.Player;

@ExtendWith(MockitoExtension.class)
class KillServiceTest {

    @Mock
    private KillDao killDao;

    @Mock
    private PlayerDao playerDao;

    @Mock
    private GameDao gameDao;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private KillService killService;

    private Kill testKill;
    private Player testVictim;
    private Game testGame;

    @BeforeEach
    void setUp() {
        // Basic setup for common test objects
        testKill = new Kill();
        testKill.setKillerID("killer-1");
        testKill.setVictimID("victim-1");
        testKill.setTime("2025-01-01T12:00:00Z");
        testKill.setLatitude(40.7128);
        testKill.setLongitude(-74.0060);
        testKill.setVerificationMethod("GPS");
        testKill.setVerificationStatus("PENDING");

        testVictim = new Player();
        testVictim.setPlayerID("victim-1");
        testVictim.setGameID("game-1");

        testGame = new Game();
        testGame.setGameID("game-1");
        Map<String, Object> settings = new HashMap<>();
        settings.put("gpsVerificationThresholdMeters", 50.0);
        testGame.setSettings(settings);
    }

    // --- Tests for verifyGpsProximity (via verifyKill) ---

    @Test
    void verifyKill_Gps_Success_WithinThreshold() throws Exception {
        // Arrange
        when(killDao.getKill(testKill.getKillerID(), testKill.getTime())).thenReturn(Optional.of(testKill));
        when(playerDao.getPlayerById("victim-1")).thenReturn(Optional.of(testVictim));
        when(gameDao.getGameById("game-1")).thenReturn(Optional.of(testGame));

        Map<String, String> verificationInput = new HashMap<>();
        verificationInput.put("victimLatitude", "40.71285"); // Approx 10m away
        verificationInput.put("victimLongitude", "-74.00605");

        // Act
        Kill result = killService.verifyKill(testKill.getKillerID(), testKill.getTime(), "verifier-1", verificationInput);

        // Assert
        assertEquals("VERIFIED", result.getVerificationStatus());
        assertTrue(result.getVerificationNotes().contains("via GPS proximity"));
        verify(killDao).saveKill(result); // Verify save was called
        verify(notificationService).sendNotification(any(Notification.class)); // Verify notification was sent
    }

    @Test
    void verifyKill_Gps_Failure_OutsideThreshold() throws Exception {
        // Arrange
        when(killDao.getKill(testKill.getKillerID(), testKill.getTime())).thenReturn(Optional.of(testKill));
        when(playerDao.getPlayerById("victim-1")).thenReturn(Optional.of(testVictim));
        when(gameDao.getGameById("game-1")).thenReturn(Optional.of(testGame));

        Map<String, String> verificationInput = new HashMap<>();
        verificationInput.put("victimLatitude", "40.7135"); // Approx 100m away
        verificationInput.put("victimLongitude", "-74.0070");

        // Act
        Kill result = killService.verifyKill(testKill.getKillerID(), testKill.getTime(), "verifier-1", verificationInput);

        // Assert
        assertEquals("REJECTED", result.getVerificationStatus());
        assertTrue(result.getVerificationNotes().contains("via GPS proximity"));
        verify(killDao).saveKill(result);
        // Notification should NOT be sent on rejection
        verify(notificationService, org.mockito.Mockito.never()).sendNotification(any(Notification.class));
    }

    @Test
    void verifyKill_Gps_Failure_MissingVictimLocation() throws Exception {
        // Arrange
        when(killDao.getKill(testKill.getKillerID(), testKill.getTime())).thenReturn(Optional.of(testKill));
        // when(playerDao.getPlayerById("victim-1")).thenReturn(Optional.of(testVictim)); // Unnecessary
        // when(gameDao.getGameById("game-1")).thenReturn(Optional.of(testGame)); // Unnecessary

        Map<String, String> verificationInput = new HashMap<>(); // Missing coords

        // Act
        Kill result = killService.verifyKill(testKill.getKillerID(), testKill.getTime(), "verifier-1", verificationInput);

        // Assert
        assertEquals("REJECTED", result.getVerificationStatus());
        verify(killDao).saveKill(result);
        verify(notificationService, org.mockito.Mockito.never()).sendNotification(any(Notification.class));
    }

    @Test
    void verifyKill_Gps_Failure_MissingKillLocation() throws Exception {
        // Arrange
        testKill.setLatitude(null); // Remove kill location
        when(killDao.getKill(testKill.getKillerID(), testKill.getTime())).thenReturn(Optional.of(testKill));
        // when(playerDao.getPlayerById("victim-1")).thenReturn(Optional.of(testVictim)); // Unnecessary
        // when(gameDao.getGameById("game-1")).thenReturn(Optional.of(testGame)); // Unnecessary

        Map<String, String> verificationInput = new HashMap<>();
        verificationInput.put("victimLatitude", "40.71285");
        verificationInput.put("victimLongitude", "-74.00605");

        // Act
        Kill result = killService.verifyKill(testKill.getKillerID(), testKill.getTime(), "verifier-1", verificationInput);

        // Assert
        assertEquals("REJECTED", result.getVerificationStatus());
        verify(killDao).saveKill(result);
        verify(notificationService, org.mockito.Mockito.never()).sendNotification(any(Notification.class));
    }

    @Test
    void verifyKill_Gps_Failure_GameNotFound() throws Exception {
        // Arrange
        when(killDao.getKill(testKill.getKillerID(), testKill.getTime())).thenReturn(Optional.of(testKill));
        when(playerDao.getPlayerById("victim-1")).thenReturn(Optional.of(testVictim));
        when(gameDao.getGameById("game-1")).thenReturn(Optional.empty()); // Game not found

        Map<String, String> verificationInput = new HashMap<>();
        verificationInput.put("victimLatitude", "40.71285"); 
        verificationInput.put("victimLongitude", "-74.00605");

        // Act
        // Exception should be logged, but verification defaults to threshold check
        Kill result = killService.verifyKill(testKill.getKillerID(), testKill.getTime(), "verifier-1", verificationInput);

        // Assert
        // Since game settings weren't found, it uses the default 50m threshold, which passes
        assertEquals("VERIFIED", result.getVerificationStatus()); 
        verify(killDao).saveKill(result);
        verify(notificationService).sendNotification(any(Notification.class)); // Verify notification was sent
    }

    // TODO: Add tests for verifyNfc (including notification verification)
    // TODO: Add tests for verifyPhoto
    // TODO: Add tests for reportKill
    // TODO: Add tests for confirmDeath

} 