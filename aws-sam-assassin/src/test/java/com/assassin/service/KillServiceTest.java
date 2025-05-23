package com.assassin.service;

import java.time.Instant;
import java.util.Collections;
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
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyDouble;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doReturn;

import com.assassin.dao.GameDao;
import com.assassin.dao.KillDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.SafeZoneException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.GameState;
import com.assassin.model.Kill;
import com.assassin.model.Notification;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;
import com.assassin.service.SafeZoneViolationDetector.ViolationCheckResult;
import com.assassin.service.verification.VerificationManager;
import com.assassin.service.verification.VerificationResult;

import java.lang.reflect.Field;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    private MapConfigurationService mapConfigurationService;
    
    @Mock
    private PlayerStatusService playerStatusService;
    
    @Mock
    private SafeZoneViolationDetector violationDetector;

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

    private static final Logger logger = LoggerFactory.getLogger(KillServiceTest.class);

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        // Create the KillService with mocked dependencies
        killService = new KillService(killDao, playerDao, gameDao, notificationService, verificationManager, mapConfigurationService);
        
        // Use reflection to inject the mocked violationDetector
        Field violationDetectorField = KillService.class.getDeclaredField("violationDetector");
        violationDetectorField.setAccessible(true);
        violationDetectorField.set(killService, violationDetector);

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

        // Default mocking for dependencies
        lenient().when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(testKiller));
        lenient().when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(testVictim));
        lenient().when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        lenient().when(killDao.getKill(killerId, killTimeString)).thenReturn(Optional.of(testKill));
        lenient().when(verificationManager.verifyKill(any(Kill.class), anyMap(), anyString()))
                .thenReturn(VerificationResult.verified("Verified via GPS proximity (within threshold)"));

        // Default behavior for MapConfigurationService:
        // Assume location is within game boundaries by default for most tests
        lenient().when(mapConfigurationService.isCoordinateInGameBoundary(eq(gameId), any(Coordinate.class))).thenReturn(true);
        // Assume no one is in a safe zone by default for most tests
        lenient().when(mapConfigurationService.isLocationInSafeZone(eq(gameId), anyString(), any(Coordinate.class), anyLong())).thenReturn(false);
        
        // Default behavior for SafeZoneViolationDetector - no violations by default
        lenient().when(violationDetector.checkEliminationAttempt(anyString(), anyString(), anyString(), 
                                                                 anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyLong()))
                .thenReturn(ViolationCheckResult.noViolation(false));
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
                .thenReturn(VerificationResult.rejected("Rejected via NFC (missing tag ID in input)"));

        Map<String, String> verificationInput = new HashMap<>(); // Missing 'nfcTagId'

        // Act
        Kill result = killService.verifyKill(killerId, killTimeString, "verifier-1", verificationInput);

        // Assert
        assertEquals("REJECTED", result.getVerificationStatus());
        assertTrue(result.getVerificationNotes().contains("missing tag ID"));
        verify(killDao).saveKill(result);
        verify(notificationService, never()).sendNotification(any(Notification.class));
    }

    @Test
    void verifyKill_Photo_Submission_Success() throws Exception {
        logger.info("Starting test verifyKill_Photo_Submission_Success...");
        String killId = killerId + "_" + Instant.now().toString(); // Example killId format
        String verifierId = "moderator123"; // Assuming a moderator verifies
        String photoUrl = "s3://bucket/photo.jpg";
        Map<String, String> verificationInput = Map.of("photoUrl", photoUrl);

        Kill pendingKill = new Kill();
        pendingKill.setKillerID(killerId);
        pendingKill.setTime(Instant.now().toString());
        pendingKill.setVictimID(victimId);
        pendingKill.setVerificationMethod("PHOTO");
        pendingKill.setVerificationStatus("PENDING");

        // Mock DAO to return the pending kill
        when(killDao.getKill(pendingKill.getKillerID(), pendingKill.getTime()))
                .thenReturn(Optional.of(pendingKill));

        // Mock VerificationManager to return PENDING_REVIEW for PHOTO
        VerificationResult expectedResult = VerificationResult.pendingReview("Photo submitted for review");
        when(verificationManager.verifyKill(any(Kill.class), eq(verificationInput), eq(verifierId)))
                .thenReturn(expectedResult);

        // --- Execute ---
        Kill result = killService.verifyKill(pendingKill.getKillerID(), pendingKill.getTime(), verifierId, verificationInput);

        // --- Assert ---
        assertNotNull(result);
        // Check the VerificationResult status enum, not the String in the Kill object directly yet
        // The service should eventually update the Kill object's string status based on this result.
        // For now, let's assert the *result* of the verificationManager call was PENDING_REVIEW
        // We'll adjust if the test should assert the final kill object status after saving.
        // ** Correction: Assert the *final* status set on the Kill object after service logic **
        assertEquals("PENDING_REVIEW", result.getVerificationStatus(), "Kill status should be pending review after photo submission");

        // Verify DAO save was called with the updated status
        ArgumentCaptor<Kill> killCaptor = ArgumentCaptor.forClass(Kill.class);
        verify(killDao).saveKill(killCaptor.capture());
        assertEquals("PENDING_REVIEW", killCaptor.getValue().getVerificationStatus());
        assertEquals("Photo submitted for review", killCaptor.getValue().getVerificationNotes());

        logger.info("Test verifyKill_Photo_Submission_Success completed successfully.");
    }

    @Test
    void verifyKill_Photo_Submission_Failure_MissingUrl() throws Exception {
        // Arrange: Modify testKill for this scenario
        testKill.setVerificationMethod("PHOTO");
        // Arrange: Specific mocks for this test
        when(killDao.getKill(killerId, killTimeString)).thenReturn(Optional.of(testKill));
        // Mock the verification manager outcome for PHOTO submission failure
        when(verificationManager.verifyKill(any(Kill.class), anyMap(), anyString()))
                .thenReturn(VerificationResult.rejected("Rejected photo submission (missing photo URL)"));

        Map<String, String> verificationInput = new HashMap<>(); // Missing 'photoUrl'

        // Act
        Kill result = killService.verifyKill(killerId, killTimeString, "verifier-1", verificationInput);

        // Assert
        assertEquals("REJECTED", result.getVerificationStatus());
        assertTrue(result.getVerificationNotes().contains("missing photo URL"));
        verify(killDao).saveKill(result);
        verify(notificationService, never()).sendNotification(any(Notification.class));
    }

    @Test
    void reportKill_Success_PendingVerification() throws Exception {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(testKiller));
        when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(testVictim));
        // Mock boundary check to pass
        when(mapConfigurationService.isCoordinateInGameBoundary(eq(gameId), any(Coordinate.class))).thenReturn(true);
        // Mock violation detector to return no violation
        when(violationDetector.checkEliminationAttempt(eq(gameId), eq(killerId), eq(victimId), 
                                                       anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyLong()))
            .thenReturn(ViolationCheckResult.noViolation(false));

        // Act
        Kill result = killService.reportKill(killerId, victimId, testKill.getLatitude(), testKill.getLongitude(), "GPS", Collections.emptyMap());

        // Assert
        assertNotNull(result);
        assertEquals(killerId, result.getKillerID());
        assertEquals(victimId, result.getVictimID());
        assertNotNull(result.getTime());
        assertEquals(testKill.getLatitude(), result.getLatitude());
        assertEquals(testKill.getLongitude(), result.getLongitude());
        assertEquals("GPS", result.getVerificationMethod());
        assertEquals("PENDING", result.getVerificationStatus());
        verify(killDao).saveKill(result);
        verify(violationDetector, times(1)).checkEliminationAttempt(eq(gameId), eq(killerId), eq(victimId), 
                                                                    anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyLong());
    }

    @Test
    void reportKill_Failure_KillerNotFound() throws Exception {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.empty());
        when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(testVictim));

        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            killService.reportKill(killerId, victimId, 40.7128, -74.0060, "GPS", Collections.emptyMap());
        });
        assertEquals("Killer with ID " + killerId + " not found.", exception.getMessage());
        verify(killDao, never()).saveKill(any(Kill.class));
    }

    @Test
    void reportKill_Failure_VictimNotFound() throws Exception {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(testKiller));
        when(playerDao.getPlayerById(victimId)).thenReturn(Optional.empty());

        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            killService.reportKill(killerId, victimId, 40.7128, -74.0060, "GPS", Collections.emptyMap());
        });
        assertEquals("Victim with ID " + victimId + " not found.", exception.getMessage());
        verify(killDao, never()).saveKill(any(Kill.class));
    }

    @Test
    void reportKill_Failure_GameNotFound() throws Exception {
        // Arrange
        // Mock gameDao to return empty when the specific gameId is requested
        when(gameDao.getGameById(gameId)).thenReturn(Optional.empty()); 
        // Mock killer being found (so the code can get the gameId)
        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(testKiller)); 
        // IMPORTANT: Ensure victim is also found initially, otherwise earlier validation fails.
        // The GameNotFoundException happens AFTER player checks.
        when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(testVictim)); 

        // Act & Assert
        // We expect GameNotFoundException because the game associated with the killer doesn't exist.
        GameNotFoundException exception = assertThrows(GameNotFoundException.class, () -> {
            killService.reportKill(killerId, victimId, 40.7128, -74.0060, "GPS", Collections.emptyMap());
        });
        assertEquals("Game with ID " + gameId + " not found.", exception.getMessage());
        verify(killDao, never()).saveKill(any(Kill.class));
    }

    @Test
    void reportKill_Failure_KillerNotInGame() throws Exception {
        // Arrange
        testKiller.setGameID("another-game");
        when(gameDao.getGameById("another-game")).thenReturn(Optional.of(testGame));
        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(testKiller));
        when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(testVictim));
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            killService.reportKill(killerId, victimId, 40.7128, -74.0060, "GPS", Collections.emptyMap());
        });
        assertEquals("Killer and victim are not in the same game.", exception.getMessage());
        verify(killDao, never()).saveKill(any(Kill.class));
    }

    @Test
    void reportKill_Failure_VictimNotInGame() throws Exception {
        // Arrange
        testVictim.setGameID("another-game");
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(testKiller));
        when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(testVictim));

        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            killService.reportKill(killerId, victimId, 40.7128, -74.0060, "GPS", Collections.emptyMap());
        });
        assertEquals("Killer and victim are not in the same game.", exception.getMessage());
        verify(killDao, never()).saveKill(any(Kill.class));
    }

    @Test
    void reportKill_Failure_KillerIsNotActive() throws Exception {
        // Arrange
        testKiller.setStatus(PlayerStatus.DEAD.name());
        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(testKiller));
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(testVictim));

        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            killService.reportKill(killerId, victimId, 40.7128, -74.0060, "GPS", Collections.emptyMap());
        });
        assertEquals("Killer " + killerId + " is not active in the game.", exception.getMessage());
        verify(killDao, never()).saveKill(any(Kill.class));
    }

    @Test
    void reportKill_Failure_VictimIsNotActive() throws Exception {
        // Arrange
        testVictim.setStatus(PlayerStatus.DEAD.name());
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(testKiller));
        when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(testVictim));

        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            killService.reportKill(killerId, victimId, 40.7128, -74.0060, "GPS", Collections.emptyMap());
        });
        assertEquals("Victim " + victimId + " is not active in the game.", exception.getMessage());
        verify(killDao, never()).saveKill(any(Kill.class));
    }

    @Test
    void reportKill_Failure_KillerTargetMismatch() throws Exception {
        // Arrange
        testKiller.setTargetID("some-other-player");
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(testKiller));
        when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(testVictim));

        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            killService.reportKill(killerId, victimId, 40.7128, -74.0060, "GPS", Collections.emptyMap());
        });
        assertEquals("Reported victim " + victimId + " is not the killer's current target (some-other-player).", exception.getMessage());
        verify(killDao, never()).saveKill(any(Kill.class));
    }

    @Test
    void reportKill_Failure_KillerInSafeZone() throws Exception {
        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(testKiller));
        when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(testVictim));
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        when(mapConfigurationService.isCoordinateInGameBoundary(eq(gameId), any(Coordinate.class))).thenReturn(true);
        
        // Mock the violation detector to return a violation
        ViolationCheckResult violationResult = ViolationCheckResult.violation(
            SafeZoneViolationDetector.ViolationType.ELIMINATION_ATTEMPT_IN_SAFE_ZONE,
            "Elimination attempt blocked: killer killer-test-1 is protected in safe zone Test Zone", 
            null, 
            false
        );
        when(violationDetector.checkEliminationAttempt(eq(gameId), eq(killerId), eq(victimId), 
                                                       anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyLong()))
            .thenReturn(violationResult);

        assertThrows(SafeZoneException.class, () -> {
            killService.reportKill(killerId, victimId, testKill.getLatitude(), testKill.getLongitude(), "GPS", Collections.emptyMap());
        }, "Should throw SafeZoneException when killer is in a safe zone");

        verify(killDao, never()).saveKill(any(Kill.class));
        verify(playerDao, never()).savePlayer(any(Player.class));
    }

    @Test
    void reportKill_Failure_NullLocation() throws Exception {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(testKiller));
        when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(testVictim));

        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            killService.reportKill(killerId, victimId, null, null, "GPS", Collections.emptyMap());
        });
        assertTrue(exception.getMessage().contains("Latitude and Longitude are required"));
        verify(killDao, never()).saveKill(any(Kill.class));
        verify(mapConfigurationService, never()).isLocationInSafeZone(anyString(), anyString(), any(Coordinate.class), anyLong());
    }

    // ================== handleVerifiedKill Tests ==================

    // ... potentially add tests for handleVerifiedKill later if needed ...
} 