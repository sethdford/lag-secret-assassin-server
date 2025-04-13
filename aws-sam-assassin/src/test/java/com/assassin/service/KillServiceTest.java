package com.assassin.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

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

    @Test
    void verifyKill_Nfc_Failure_MissingInputTag() throws Exception {
        // Arrange
        testKill.setVerificationMethod("NFC");
        when(killDao.getKill(testKill.getKillerID(), testKill.getTime())).thenReturn(Optional.of(testKill));
        // No PlayerDao interaction expected

        Map<String, String> verificationInput = new HashMap<>(); // Missing scannedNfcTagId

        // Act
        Kill result = killService.verifyKill(testKill.getKillerID(), testKill.getTime(), "verifier-1", verificationInput);

        // Assert
        // Based on VerificationManager, missing required data results in REJECTED via INVALID_INPUT from the method
        assertEquals("REJECTED", result.getVerificationStatus()); 
        // assertTrue(result.getNotes().contains("Required NFC verification data (scannedNfcTagId) is missing")); // Incorrect assertion
        assertTrue(result.getVerificationNotes().contains("Required NFC verification data (scannedNfcTagId) is missing")); // Correct assertion using getVerificationNotes()
        verify(killDao).saveKill(result);
        verify(notificationService, org.mockito.Mockito.never()).sendNotification(any(Notification.class));
    }

    // --- Tests for verifyKill with PHOTO ---

    @Test
    void verifyKill_Photo_Submission_Success() throws Exception {
        // Arrange
        testKill.setVerificationMethod("PHOTO");
        when(killDao.getKill(testKill.getKillerID(), testKill.getTime())).thenReturn(Optional.of(testKill));
        // No PlayerDao/GameDao needed for initial photo submission check

        String photoUrl = "https://example.com/kill.jpg";
        Map<String, String> verificationInput = new HashMap<>();
        verificationInput.put("photoUrl", photoUrl);

        // Act
        Kill result = killService.verifyKill(testKill.getKillerID(), testKill.getTime(), "verifier-1", verificationInput);

        // Assert
        assertEquals("PENDING_REVIEW", result.getVerificationStatus());
        assertTrue(result.getVerificationNotes().contains("Photo submitted for review"));
        assertTrue(result.getVerificationNotes().contains(photoUrl));
        verify(killDao).saveKill(result);
        verify(notificationService, org.mockito.Mockito.never()).sendNotification(any(Notification.class));
    }

    @Test
    void verifyKill_Photo_Submission_Failure_MissingUrl() throws Exception {
        // Arrange
        testKill.setVerificationMethod("PHOTO");
        when(killDao.getKill(testKill.getKillerID(), testKill.getTime())).thenReturn(Optional.of(testKill));

        Map<String, String> verificationInput = new HashMap<>(); // Missing photoUrl

        // Act
        Kill result = killService.verifyKill(testKill.getKillerID(), testKill.getTime(), "verifier-1", verificationInput);

        // Assert
        // Missing required data results in REJECTED via INVALID_INPUT from the method
        assertEquals("REJECTED", result.getVerificationStatus());
        assertTrue(result.getVerificationNotes().contains("Required photo verification data (photoUrl) is missing"));
        verify(killDao).saveKill(result);
        verify(notificationService, org.mockito.Mockito.never()).sendNotification(any(Notification.class));
    }

    @Test
    void verifyKill_Photo_ModeratorApproval_Success() throws Exception {
        // Arrange
        testKill.setVerificationMethod("PHOTO");
        testKill.setVerificationStatus("PENDING_REVIEW"); // Kill is awaiting review
        when(killDao.getKill(testKill.getKillerID(), testKill.getTime())).thenReturn(Optional.of(testKill));

        Map<String, String> verificationInput = new HashMap<>();
        verificationInput.put("moderatorAction", "APPROVE");
        verificationInput.put("moderatorNotes", "Clear photo");

        // Act
        Kill result = killService.verifyKill(testKill.getKillerID(), testKill.getTime(), "moderator-1", verificationInput);

        // Assert
        assertEquals("VERIFIED", result.getVerificationStatus());
        assertTrue(result.getVerificationNotes().contains("Approved by moderator"));
        assertTrue(result.getVerificationNotes().contains("Clear photo"));
        verify(killDao).saveKill(result);
        verify(notificationService).sendNotification(any(Notification.class)); // Notification sent on approval
    }

    @Test
    void verifyKill_Photo_ModeratorRejection_Success() throws Exception {
        // Arrange
        testKill.setVerificationMethod("PHOTO");
        testKill.setVerificationStatus("PENDING_REVIEW");
        when(killDao.getKill(testKill.getKillerID(), testKill.getTime())).thenReturn(Optional.of(testKill));

        Map<String, String> verificationInput = new HashMap<>();
        verificationInput.put("moderatorAction", "REJECT");
        verificationInput.put("moderatorNotes", "Blurry photo");

        // Act
        Kill result = killService.verifyKill(testKill.getKillerID(), testKill.getTime(), "moderator-1", verificationInput);

        // Assert
        assertEquals("REJECTED", result.getVerificationStatus());
        assertTrue(result.getVerificationNotes().contains("Rejected by moderator"));
        assertTrue(result.getVerificationNotes().contains("Blurry photo"));
        verify(killDao).saveKill(result);
        verify(notificationService, org.mockito.Mockito.never()).sendNotification(any(Notification.class)); // No notification on rejection
    }

    // --- Tests for reportKill --- 

    @Test
    void reportKill_Success_ValidPlayersAndTarget() throws Exception {
        // Arrange
        String killerId = "killer-active-1";
        String victimId = "victim-active-1";
        String victimTargetId = "victim-target-1"; // The target killer will inherit

        Player killer = new Player();
        killer.setPlayerID(killerId);
        killer.setStatus("ACTIVE");
        killer.setTargetID(victimId); // Killer's target is the victim
        killer.setKillCount(0); // Start with 0 kills

        Player victim = new Player();
        victim.setPlayerID(victimId);
        victim.setStatus("ACTIVE");
        victim.setTargetID(victimTargetId); // Victim's target

        // Mock DAO calls
        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(victim));
        // We don't need to mock saveKill, just verify it's called with the right data
        // We need to capture the arguments passed to savePlayer
        org.mockito.ArgumentCaptor<Player> playerCaptor = org.mockito.ArgumentCaptor.forClass(Player.class);

        // Act
        Kill reportedKill = killService.reportKill(killerId, victimId, 10.0, 20.0, "GPS", null);

        // Assert
        assertNotNull(reportedKill);
        assertEquals(killerId, reportedKill.getKillerID());
        assertEquals(victimId, reportedKill.getVictimID());
        assertEquals("PENDING", reportedKill.getVerificationStatus()); // Initial status
        assertNotNull(reportedKill.getTime());

        // Verify saveKill was called once
        verify(killDao).saveKill(any(Kill.class));

        // Verify savePlayer was called twice (once for victim, once for killer update + kill count increment)
        verify(playerDao, times(2)).savePlayer(playerCaptor.capture());
        
        // Check the saved players
        List<Player> savedPlayers = playerCaptor.getAllValues();
        Player savedVictim = savedPlayers.stream().filter(p -> p.getPlayerID().equals(victimId)).findFirst().orElseThrow();
        Player savedKiller = savedPlayers.stream().filter(p -> p.getPlayerID().equals(killerId)).findFirst().orElseThrow();

        // Assert victim updates
        assertEquals("DEAD", savedVictim.getStatus());
        assertNull(savedVictim.getTargetID()); // Dead players lose target

        // Assert killer updates
        assertEquals(victimTargetId, savedKiller.getTargetID()); // Killer gets victim's target
        assertEquals(1, savedKiller.getKillCount()); // Kill count incremented
    }

    @Test
    void reportKill_Failure_KillerNotFound() {
        // Arrange
        when(playerDao.getPlayerById("nonexistent-killer")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(com.assassin.exception.ValidationException.class, () -> {
            killService.reportKill("nonexistent-killer", "victim-1", 10.0, 20.0, "GPS", null);
        }, "Should throw ValidationException if killer not found");

        verify(killDao, never()).saveKill(any());
        verify(playerDao, never()).savePlayer(any());
    }

    @Test
    void reportKill_Failure_KillerNotActive() {
        // Arrange
        Player inactiveKiller = new Player();
        inactiveKiller.setPlayerID("inactive-killer");
        inactiveKiller.setStatus("DEAD"); // Not active
        when(playerDao.getPlayerById("inactive-killer")).thenReturn(Optional.of(inactiveKiller));

        // Act & Assert
        assertThrows(com.assassin.exception.ValidationException.class, () -> {
            killService.reportKill("inactive-killer", "victim-1", 10.0, 20.0, "GPS", null);
        }, "Should throw ValidationException if killer is not active");
    }

    @Test
    void reportKill_Failure_VictimNotFound() {
        // Arrange
        Player killer = new Player();
        killer.setPlayerID("killer-1");
        killer.setStatus("ACTIVE");
        when(playerDao.getPlayerById("killer-1")).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById("nonexistent-victim")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(com.assassin.exception.ValidationException.class, () -> {
            killService.reportKill("killer-1", "nonexistent-victim", 10.0, 20.0, "GPS", null);
        }, "Should throw ValidationException if victim not found");
    }

     @Test
    void reportKill_Failure_VictimNotActive() {
        // Arrange
        Player killer = new Player();
        killer.setPlayerID("killer-1");
        killer.setStatus("ACTIVE");
        killer.setTargetID("inactive-victim");

        Player inactiveVictim = new Player();
        inactiveVictim.setPlayerID("inactive-victim");
        inactiveVictim.setStatus("DEAD"); // Not active

        when(playerDao.getPlayerById("killer-1")).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById("inactive-victim")).thenReturn(Optional.of(inactiveVictim));

        // Act & Assert
        assertThrows(com.assassin.exception.ValidationException.class, () -> {
            killService.reportKill("killer-1", "inactive-victim", 10.0, 20.0, "GPS", null);
        }, "Should throw ValidationException if victim is not active");
    }

    @Test
    void reportKill_Failure_VictimNotTarget() {
        // Arrange
        Player killer = new Player();
        killer.setPlayerID("killer-1");
        killer.setStatus("ACTIVE");
        killer.setTargetID("actual-target"); // Killer's target is someone else

        Player wrongVictim = new Player();
        wrongVictim.setPlayerID("wrong-victim");
        wrongVictim.setStatus("ACTIVE");

        when(playerDao.getPlayerById("killer-1")).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById("wrong-victim")).thenReturn(Optional.of(wrongVictim));

        // Act & Assert
        assertThrows(com.assassin.exception.ValidationException.class, () -> {
            killService.reportKill("killer-1", "wrong-victim", 10.0, 20.0, "GPS", null);
        }, "Should throw ValidationException if victim is not the killer's target");
    }

    // --- Tests for confirmDeath --- 

    @Test
    void confirmDeath_Success() throws Exception {
        // Arrange
        String gameId = "game-confirm-1";
        String victimId = "victim-confirm-1";
        String killerId = "killer-confirm-1";
        String lastWill = "Tell my cat I love her.";

        Game activeGame = new Game();
        activeGame.setGameID(gameId);
        activeGame.setStatus("ACTIVE");

        Player victimPlayer = new Player();
        victimPlayer.setPlayerID(victimId);
        victimPlayer.setGameID(gameId);
        victimPlayer.setStatus("DEAD"); // Victim must already be marked DEAD

        Kill killRecord = new Kill();
        killRecord.setKillerID(killerId);
        killRecord.setVictimID(victimId);
        killRecord.setTime("2024-02-01T10:00:00Z");
        killRecord.setGameId(gameId);
        killRecord.setDeathConfirmed(false); // Not yet confirmed

        // Mock DAO calls
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(activeGame));
        when(playerDao.getPlayerById(victimId)).thenReturn(Optional.of(victimPlayer));
        when(killDao.findKillRecordByVictimAndGame(victimId, gameId)).thenReturn(Optional.of(killRecord)); 
        // Capture the saved Kill object
        org.mockito.ArgumentCaptor<Kill> killCaptor = org.mockito.ArgumentCaptor.forClass(Kill.class);

        // Act
        Kill confirmedKill = killService.confirmDeath(gameId, victimId, lastWill);

        // Assert
        assertNotNull(confirmedKill);
        assertTrue(confirmedKill.isDeathConfirmed());
        assertEquals(lastWill, confirmedKill.getLastWill());
        
        // Verify killDao.saveKill was called with the updated kill
        verify(killDao).saveKill(killCaptor.capture());
        Kill savedKill = killCaptor.getValue();
        assertTrue(savedKill.isDeathConfirmed());
        assertEquals(lastWill, savedKill.getLastWill());
    }

    @Test
    void confirmDeath_Failure_GameNotFound() {
        // Arrange
        when(gameDao.getGameById("nonexistent-game")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(com.assassin.exception.GameNotFoundException.class, () -> {
            killService.confirmDeath("nonexistent-game", "victim-1", "Will");
        });
        verify(killDao, never()).saveKill(any());
    }

    @Test
    void confirmDeath_Failure_GameNotActive() {
        // Arrange
        Game inactiveGame = new Game();
        inactiveGame.setGameID("inactive-game");
        inactiveGame.setStatus("FINISHED"); // Not active
        when(gameDao.getGameById("inactive-game")).thenReturn(Optional.of(inactiveGame));

        // Act & Assert
        assertThrows(com.assassin.exception.InvalidGameStateException.class, () -> {
            killService.confirmDeath("inactive-game", "victim-1", "Will");
        });
    }

    @Test
    void confirmDeath_Failure_VictimNotFound() {
        // Arrange
        Game activeGame = new Game();
        activeGame.setGameID("game-1");
        activeGame.setStatus("ACTIVE");
        when(gameDao.getGameById("game-1")).thenReturn(Optional.of(activeGame));
        when(playerDao.getPlayerById("nonexistent-victim")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(com.assassin.exception.PlayerNotFoundException.class, () -> {
            killService.confirmDeath("game-1", "nonexistent-victim", "Will");
        });
    }

    @Test
    void confirmDeath_Failure_VictimNotDead() {
        // Arrange
        Game activeGame = new Game();
        activeGame.setGameID("game-1");
        activeGame.setStatus("ACTIVE");

        Player aliveVictim = new Player();
        aliveVictim.setPlayerID("alive-victim");
        aliveVictim.setGameID("game-1");
        aliveVictim.setStatus("ACTIVE"); // Still alive

        when(gameDao.getGameById("game-1")).thenReturn(Optional.of(activeGame));
        when(playerDao.getPlayerById("alive-victim")).thenReturn(Optional.of(aliveVictim));

        // Act & Assert
        assertThrows(com.assassin.exception.PlayerActionNotAllowedException.class, () -> {
            killService.confirmDeath("game-1", "alive-victim", "Will");
        }, "Should throw exception if victim is not DEAD");
    }

    @Test
    void confirmDeath_Failure_KillRecordNotFound() {
        // Arrange
        Game activeGame = new Game();
        activeGame.setGameID("game-1");
        activeGame.setStatus("ACTIVE");

        Player deadVictim = new Player();
        deadVictim.setPlayerID("dead-victim");
        deadVictim.setGameID("game-1");
        deadVictim.setStatus("DEAD");

        when(gameDao.getGameById("game-1")).thenReturn(Optional.of(activeGame));
        when(playerDao.getPlayerById("dead-victim")).thenReturn(Optional.of(deadVictim));
        when(killDao.findKillRecordByVictimAndGame("dead-victim", "game-1")).thenReturn(Optional.empty()); // Kill not found

        // Act & Assert
        assertThrows(com.assassin.exception.KillNotFoundException.class, () -> {
            killService.confirmDeath("game-1", "dead-victim", "Will");
        }, "Should throw exception if kill record not found for victim");
    }

    @Test
    void confirmDeath_Failure_AlreadyConfirmed() {
        // Arrange
        Game activeGame = new Game();
        activeGame.setGameID("game-1");
        activeGame.setStatus("ACTIVE");

        Player deadVictim = new Player();
        deadVictim.setPlayerID("dead-victim");
        deadVictim.setGameID("game-1");
        deadVictim.setStatus("DEAD");

        Kill alreadyConfirmedKill = new Kill();
        alreadyConfirmedKill.setVictimID("dead-victim");
        alreadyConfirmedKill.setGameId("game-1");
        alreadyConfirmedKill.setDeathConfirmed(true); // Already confirmed

        when(gameDao.getGameById("game-1")).thenReturn(Optional.of(activeGame));
        when(playerDao.getPlayerById("dead-victim")).thenReturn(Optional.of(deadVictim));
        when(killDao.findKillRecordByVictimAndGame("dead-victim", "game-1")).thenReturn(Optional.of(alreadyConfirmedKill)); 

        // Act & Assert
        assertThrows(com.assassin.exception.PlayerActionNotAllowedException.class, () -> {
            killService.confirmDeath("game-1", "dead-victim", "Will");
        }, "Should throw exception if death already confirmed");
    }

} 