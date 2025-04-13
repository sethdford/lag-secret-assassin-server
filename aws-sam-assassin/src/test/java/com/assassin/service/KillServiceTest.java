package com.assassin.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assassin.dao.GameDao;
import com.assassin.dao.KillDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.ValidationException;
import com.assassin.model.Game;
import com.assassin.model.GameState;
import com.assassin.model.Kill;
import com.assassin.model.Notification;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;
import com.assassin.service.verification.VerificationManager;
import com.assassin.service.verification.VerificationResult;

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

    @Mock
    private VerificationManager verificationManager;

    @Mock
    private SafeZoneService safeZoneService;
    
    @Mock
    private PlayerStatusService playerStatusService;

    @InjectMocks
    private KillService killService;

    private Kill testKill;
    private Player testKiller;
    private Player testVictim;
    private Game testGame;
    private final String gameId = "game-test-1";
    private final String killerId = "killer-test-1";
    private final String victimId = "victim-test-1";
    private final Instant killTime = Instant.parse("2023-10-27T10:15:30.00Z");
    private final String killTimeString = killTime.toString(); // String representation for API

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Using constructor with all dependencies to match service implementation
        killService = new KillService(killDao, playerDao, gameDao, notificationService, verificationManager, safeZoneService);

        // Basic valid game and players setup
        testGame = new Game();
        testGame.setGameID(gameId);
        testGame.setStatus(GameState.ACTIVE.name());
        testGame.setCreatedAt(Instant.now().minusSeconds(3600).toString());
        Map<String, Object> settings = new HashMap<>();
        settings.put("KILL_VERIFICATION_METHODS", List.of("GPS", "PHOTO"));
        settings.put("GPS_PROXIMITY_THRESHOLD_METERS", 100.0);
        testGame.setSettings(settings);

        testKiller = new Player();
        testKiller.setPlayerID(killerId);
        testKiller.setGameID(gameId);
        testKiller.setStatus(PlayerStatus.ACTIVE.name());
        testKiller.setTargetID(victimId);

        testVictim = new Player();
        testVictim.setPlayerID(victimId);
        testVictim.setGameID(gameId);
        testVictim.setStatus(PlayerStatus.ACTIVE.name());
        testVictim.setTargetID("another-player");

        testKill = new Kill();
        testKill.setGameId(gameId);
        testKill.setKillerID(killerId);
        testKill.setVictimID(victimId);
        testKill.setTime(killTimeString); // Use string for time
        testKill.setLatitude(40.7128);
        testKill.setLongitude(-74.0060);
        testKill.setVerificationMethod("GPS");
        testKill.setVerificationStatus("PENDING"); // Use string directly

        // Removed general mock setups from here. They will be added to individual tests.
        // when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        // when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(testKiller));
        // when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(testVictim));
        // when(killDao.getKill(killerId, killTimeString)).thenReturn(Optional.of(testKill));
        // when(verificationManager.verifyKill(any(Kill.class), anyMap(), anyString()))
        //         .thenReturn(VerificationResult.verified("Default verification success"));
    }

    @Test
    void verifyKill_Gps_Success_WithinThreshold() throws Exception {
        // Arrange: Specific mocks for this test
        when(killDao.getKill(killerId, killTimeString)).thenReturn(Optional.of(testKill));
        // Mock the verification manager outcome for this scenario
        when(verificationManager.verifyKill(any(Kill.class), anyMap(), anyString()))
                .thenReturn(VerificationResult.verified("Verified via GPS proximity (within threshold)"));

        Map<String, String> verificationInput = new HashMap<>();
        verificationInput.put("victimLatitude", "40.71285");
        verificationInput.put("victimLongitude", "-74.00605");

        // Act
        Kill result = killService.verifyKill(killerId, killTimeString, "verifier-1", verificationInput);

        // Assert
        assertEquals("VERIFIED", result.getVerificationStatus());
        assertTrue(result.getVerificationNotes().contains("via GPS proximity"));
        verify(killDao).saveKill(result);
        verify(notificationService).sendNotification(any(Notification.class));
    }

    @Test
    void verifyKill_Gps_Failure_OutsideThreshold() throws Exception {
        // Arrange: Specific mocks for this test
        when(killDao.getKill(killerId, killTimeString)).thenReturn(Optional.of(testKill));
        // Mock the verification manager outcome for this scenario
        when(verificationManager.verifyKill(any(Kill.class), anyMap(), anyString()))
                .thenReturn(VerificationResult.rejected("Rejected via GPS proximity (outside threshold)"));

        Map<String, String> verificationInput = new HashMap<>();
        verificationInput.put("victimLatitude", "40.7135");
        verificationInput.put("victimLongitude", "-74.0070");

        // Act
        Kill result = killService.verifyKill(killerId, killTimeString, "verifier-1", verificationInput);

        // Assert
        assertEquals("REJECTED", result.getVerificationStatus());
        assertTrue(result.getVerificationNotes().contains("via GPS proximity"));
        verify(killDao).saveKill(result);
        verify(notificationService, never()).sendNotification(any(Notification.class));
    }

    @Test
    void verifyKill_Gps_Failure_MissingVictimLocation() throws Exception {
        // Arrange: Specific mocks for this test
        when(killDao.getKill(killerId, killTimeString)).thenReturn(Optional.of(testKill));
        // Mock the verification manager outcome for this scenario
        when(verificationManager.verifyKill(any(Kill.class), anyMap(), anyString()))
                .thenReturn(VerificationResult.rejected("Rejected via GPS proximity (missing victim location)"));

        Map<String, String> verificationInput = new HashMap<>(); // Empty input, simulating missing coords

        // Act
        Kill result = killService.verifyKill(killerId, killTimeString, "verifier-1", verificationInput);

        // Assert
        assertEquals("REJECTED", result.getVerificationStatus());
        assertTrue(result.getVerificationNotes().contains("missing victim location"));
        verify(killDao).saveKill(result);
        verify(notificationService, never()).sendNotification(any(Notification.class));
    }

    @Test
    void verifyKill_Gps_Failure_MissingKillLocation() throws Exception {
        // Arrange: Modify testKill for this scenario
        testKill.setLatitude(null);
        // Arrange: Specific mocks for this test
        when(killDao.getKill(killerId, killTimeString)).thenReturn(Optional.of(testKill));
        // Mock the verification manager outcome for this scenario
        when(verificationManager.verifyKill(any(Kill.class), anyMap(), anyString()))
                .thenReturn(VerificationResult.rejected("Rejected via GPS proximity (missing kill location)"));

        Map<String, String> verificationInput = new HashMap<>();
        verificationInput.put("victimLatitude", "40.71285");
        verificationInput.put("victimLongitude", "-74.00605");

        // Act
        Kill result = killService.verifyKill(killerId, killTimeString, "verifier-1", verificationInput);

        // Assert
        assertEquals("REJECTED", result.getVerificationStatus());
        assertTrue(result.getVerificationNotes().contains("missing kill location"));
        verify(killDao).saveKill(result);
        verify(notificationService, never()).sendNotification(any(Notification.class));
    }

    @Test
    void verifyKill_Nfc_Failure_MissingInputTag() throws Exception {
        // Arrange: Modify testKill for this scenario
        testKill.setVerificationMethod("NFC");
        // Arrange: Specific mocks for this test
        when(killDao.getKill(killerId, killTimeString)).thenReturn(Optional.of(testKill));
        // Mock the verification manager outcome for this scenario
        when(verificationManager.verifyKill(any(Kill.class), anyMap(), anyString()))
               .thenReturn(VerificationResult.rejected("Required NFC verification data (scannedNfcTagId) is missing"));

        Map<String, String> verificationInput = new HashMap<>(); // Empty map, simulating missing tag

        // Act
        Kill result = killService.verifyKill(killerId, killTimeString, "verifier-1", verificationInput);

        // Assert
        assertEquals("REJECTED", result.getVerificationStatus());
        assertTrue(result.getVerificationNotes().contains("Required NFC verification data (scannedNfcTagId) is missing"));
        verify(killDao).saveKill(result);
        verify(notificationService, never()).sendNotification(any(Notification.class));
    }

    @Test
    void verifyKill_Photo_Submission_Success() throws Exception {
        // Arrange: Modify testKill for this scenario
        testKill.setVerificationMethod("PHOTO");
        // Arrange: Specific mocks for this test
        when(killDao.getKill(killerId, killTimeString)).thenReturn(Optional.of(testKill));
        // Mock the verification manager outcome for this scenario
        when(verificationManager.verifyKill(any(Kill.class), anyMap(), anyString()))
               .thenReturn(VerificationResult.pendingReview("Photo submitted for review"));

        String photoUrl = "https://example.com/kill.jpg";
        Map<String, String> verificationInput = new HashMap<>();
        verificationInput.put("photoUrl", photoUrl);

        // Act
        Kill result = killService.verifyKill(killerId, killTimeString, "verifier-1", verificationInput);

        // Assert
        assertEquals("PENDING_REVIEW", result.getVerificationStatus());
        assertTrue(result.getVerificationNotes().contains("Photo submitted for review"));
        verify(killDao).saveKill(result);
        verify(notificationService, never()).sendNotification(any(Notification.class));
    }

    @Test
    void verifyKill_Photo_Submission_Failure_MissingUrl() throws Exception {
        // Arrange: Modify testKill for this scenario
        testKill.setVerificationMethod("PHOTO");
        // Arrange: Specific mocks for this test
        when(killDao.getKill(killerId, killTimeString)).thenReturn(Optional.of(testKill));
        // Mock the verification manager outcome for this scenario
        when(verificationManager.verifyKill(any(Kill.class), anyMap(), anyString()))
               .thenReturn(VerificationResult.rejected("Required photo verification data (photoUrl) is missing"));

        Map<String, String> verificationInput = new HashMap<>(); // Empty map, simulating missing URL

        // Act
        Kill result = killService.verifyKill(killerId, killTimeString, "verifier-1", verificationInput);

        // Assert
        assertEquals("REJECTED", result.getVerificationStatus());
        assertTrue(result.getVerificationNotes().contains("Required photo verification data (photoUrl) is missing"));
        verify(killDao).saveKill(result);
        verify(notificationService, never()).sendNotification(any(Notification.class));
    }

    @Test
    void reportKill_Success_PendingVerification() {
        // Arrange: Specific mocks for this test
        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(testKiller));
        when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(testVictim));
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        when(safeZoneService.isLocationInSafeZone(eq(gameId), any())).thenReturn(false);
        
        // Act
        Kill result = killService.reportKill(killerId, victimId, 40.7128, -74.0060, "GPS", new HashMap<>());

        // Assert
        assertNotNull(result);
        assertEquals(killerId, result.getKillerID());
        assertEquals(victimId, result.getVictimID());
        assertEquals("PENDING", result.getVerificationStatus());
        assertEquals("PENDING", result.getKillStatusPartition()); // Verify partition key is set
        assertEquals("GPS", result.getVerificationMethod());
        
        // Verify interactions
        verify(killDao).saveKill(result); // Verify the kill object itself
        verify(playerDao).savePlayer(testVictim); // Victim status updated
        verify(playerDao).savePlayer(testKiller); // Killer target updated
        // Ensure increment count is called (assuming DynamoDbPlayerDao specific method)
        // verify(((DynamoDbPlayerDao)playerDao)).incrementPlayerKillCount(killerId);
        // Note: Verifying calls on concrete types requires casting or a different mock setup
    }

    @Test
    void reportKill_Failure_KillerNotFound() {
        // Arrange: Specific mocks for this test
        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.empty()); // Killer not found
        // The victim check happens after the killer check, so this mock isn't needed for this specific test path
        // when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(testVictim));

        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            killService.reportKill(killerId, victimId, 40.7128, -74.0060, "GPS", new HashMap<>());
        });
        assertTrue(exception.getMessage().contains("Killer with ID " + killerId + " not found"));
        
        verify(killDao, never()).saveKill(any(Kill.class));
        verify(playerDao, never()).savePlayer(any(Player.class));
    }

    @Test
    void reportKill_Failure_VictimNotFound() {
        // Arrange: Specific mocks for this test
        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(testKiller));
        when(playerDao.getPlayerById(victimId)).thenReturn(Optional.empty()); // Victim not found
        
        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            killService.reportKill(killerId, victimId, 40.7128, -74.0060, "GPS", new HashMap<>());
        });
        assertTrue(exception.getMessage().contains("Victim with ID " + victimId + " not found"));
        
        verify(killDao, never()).saveKill(any(Kill.class));
        verify(playerDao, never()).savePlayer(any(Player.class));
    }
    // Add more reportKill failure tests: wrong target, game not active, in safe zone etc.
} 