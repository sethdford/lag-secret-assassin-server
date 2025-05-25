package com.assassin.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assassin.dao.PlayerDao;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.PlayerPersistenceException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Player;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerDao mockPlayerDao;

    @InjectMocks
    private PlayerService playerService;

    private Player testPlayer;
    private String testPlayerId = "testPlayer123";
    private String testEmail = "test@example.com";
    private String testName = "Test Player";

    @BeforeEach
    void setUp() {
        testPlayer = new Player();
        testPlayer.setPlayerID(testPlayerId);
        testPlayer.setEmail(testEmail);
        testPlayer.setPlayerName(testName);
        testPlayer.setStatus("ACTIVE");
        // Default visibility is VISIBLE_TO_HUNTER_TARGET
    }

    @Test
    void syncFederatedUserToPlayer_existingPlayer_updatesAndSaves() throws PlayerPersistenceException {
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);
        
        Map<String, String> attributes = new HashMap<>();
        Player result = playerService.syncFederatedUserToPlayer(testPlayerId, "new-" + testEmail, "New Name", "Google", attributes);

        assertNotNull(result);
        assertEquals("New Name", result.getPlayerName());
        assertEquals("new-" + testEmail, result.getEmail());
        verify(mockPlayerDao).savePlayer(testPlayer);
    }

    @Test
    void syncFederatedUserToPlayer_newPlayer_createsAndSaves() throws PlayerPersistenceException {
        when(mockPlayerDao.findPlayerById("newPlayerId")).thenReturn(null);
        
        Map<String, String> attributes = new HashMap<>();
        Player result = playerService.syncFederatedUserToPlayer("newPlayerId", testEmail, testName, "Google", attributes);

        assertNotNull(result);
        assertEquals("newPlayerId", result.getPlayerID());
        assertEquals(testName, result.getPlayerName());
        assertEquals(testEmail, result.getEmail());
        assertEquals("PENDING", result.getStatus()); // New players are PENDING
        verify(mockPlayerDao).savePlayer(any(Player.class));
    }

    @Test
    void getLocationVisibilitySettings_playerExists_returnsVisibility() throws PlayerPersistenceException {
        testPlayer.setLocationVisibility(Player.LocationVisibility.HIDDEN);
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);

        Optional<Player.LocationVisibility> result = playerService.getLocationVisibilitySettings(testPlayerId);

        assertTrue(result.isPresent());
        assertEquals(Player.LocationVisibility.HIDDEN, result.get());
    }

    @Test
    void getLocationVisibilitySettings_playerDoesNotExist_returnsEmpty() throws PlayerPersistenceException {
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(null);

        Optional<Player.LocationVisibility> result = playerService.getLocationVisibilitySettings(testPlayerId);

        assertFalse(result.isPresent());
    }
    
    @Test
    void getLocationVisibilitySettings_daoThrowsException_propagatesException() {
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenThrow(new RuntimeException("DAO Error"));

        PlayerPersistenceException exception = assertThrows(PlayerPersistenceException.class, () -> {
            playerService.getLocationVisibilitySettings(testPlayerId);
        });
        assertTrue(exception.getMessage().contains("Failed to retrieve location visibility settings"));
    }

    @Test
    void updateLocationVisibilitySettings_playerExists_updatesAndReturnsPlayer() throws Exception {
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);
        doNothing().when(mockPlayerDao).savePlayer(any(Player.class));

        Player result = playerService.updateLocationVisibilitySettings(testPlayerId, Player.LocationVisibility.VISIBLE_TO_ALL_IN_GAME);

        assertNotNull(result);
        assertEquals(Player.LocationVisibility.VISIBLE_TO_ALL_IN_GAME, result.getLocationVisibility());
        verify(mockPlayerDao).savePlayer(testPlayer);
        assertEquals(Player.LocationVisibility.VISIBLE_TO_ALL_IN_GAME, testPlayer.getLocationVisibility()); // Check side-effect on passed in object
    }

    @Test
    void updateLocationVisibilitySettings_playerDoesNotExist_throwsPlayerNotFound() {
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(null);

        assertThrows(PlayerNotFoundException.class, () -> {
            playerService.updateLocationVisibilitySettings(testPlayerId, Player.LocationVisibility.HIDDEN);
        });
        verify(mockPlayerDao, never()).savePlayer(any());
    }

    @Test
    void updateLocationVisibilitySettings_nullVisibility_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            playerService.updateLocationVisibilitySettings(testPlayerId, null);
        });
        verify(mockPlayerDao, never()).savePlayer(any());
    }

    @Test
    void updateLocationVisibilitySettings_daoSaveThrowsException_propagatesException() {
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);
        // Throw PlayerPersistenceException, which the service method is expected to propagate
        doThrow(new PlayerPersistenceException("DAO Save Error")).when(mockPlayerDao).savePlayer(any(Player.class));

        assertThrows(PlayerPersistenceException.class, () -> {
            playerService.updateLocationVisibilitySettings(testPlayerId, Player.LocationVisibility.HIDDEN);
        });
        verify(mockPlayerDao).savePlayer(any(Player.class));
    }
    
    @Test
    void syncFederatedUserToPlayer_nullUserId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            playerService.syncFederatedUserToPlayer(null, testEmail, testName, "Google", new HashMap<>());
        });
    }

    @Test
    void getLocationVisibilitySettings_nullPlayerId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            playerService.getLocationVisibilitySettings(null);
        });
    }

    @Test
    void updateLocationVisibilitySettings_nullPlayerId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            playerService.updateLocationVisibilitySettings(null, Player.LocationVisibility.HIDDEN);
        });
    }

    // Tests for pauseLocationSharing
    @Test
    void pauseLocationSharing_playerExists_notOnCooldown_pausesAndSetsCooldown() throws Exception {
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);
        doNothing().when(mockPlayerDao).savePlayer(any(Player.class));
        testPlayer.setLocationPauseCooldownUntilFromDateTime(null); // Ensure no cooldown

        LocalDateTime beforePause = LocalDateTime.now();
        Player result = playerService.pauseLocationSharing(testPlayerId);

        assertTrue(result.isLocationSharingPaused());
        assertNotNull(result.getLocationPauseCooldownUntilAsDateTime());
        assertTrue(result.getLocationPauseCooldownUntilAsDateTime().isAfter(beforePause.plus(Duration.ofMinutes(4)))); // Check cooldown is set roughly correctly
        verify(mockPlayerDao).savePlayer(testPlayer);
    }

    @Test
    void pauseLocationSharing_playerExists_onCooldown_throwsValidationException() {
        // Arrange
        testPlayer.setLocationSharingPaused(false);
        // Set cooldown to be in the future using ISO_INSTANT format
        String futureCooldown = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(30).format(DateTimeFormatter.ISO_INSTANT);
        testPlayer.setLocationPauseCooldownUntil(futureCooldown); 

        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);

        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            playerService.pauseLocationSharing(testPlayerId);
        });
        assertTrue(exception.getMessage().contains("Location sharing pause is on cooldown until"));
        verify(mockPlayerDao, never()).savePlayer(any(Player.class));
    }

    @Test
    void pauseLocationSharing_playerNotFound_throwsPlayerNotFoundException() {
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(null);
        assertThrows(PlayerNotFoundException.class, () -> playerService.pauseLocationSharing(testPlayerId));
    }

    // Tests for resumeLocationSharing
    @Test
    void resumeLocationSharing_playerExists_resumes() throws Exception {
        testPlayer.setLocationSharingPaused(true); // Start with paused state
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);
        doNothing().when(mockPlayerDao).savePlayer(any(Player.class));

        Player result = playerService.resumeLocationSharing(testPlayerId);

        assertFalse(result.isLocationSharingPaused());
        verify(mockPlayerDao).savePlayer(testPlayer);
    }

    @Test
    void resumeLocationSharing_playerNotFound_throwsPlayerNotFoundException() {
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(null);
        assertThrows(PlayerNotFoundException.class, () -> playerService.resumeLocationSharing(testPlayerId));
    }

    // --- Tests for Location Precision ---
    @Test
    void getLocationPrecisionSettings_playerExists_returnsPrecision() throws PlayerPersistenceException {
        testPlayer.setLocationPrecision(Player.LocationPrecision.REDUCED_100M);
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);

        Optional<Player.LocationPrecision> result = playerService.getLocationPrecisionSettings(testPlayerId);

        assertTrue(result.isPresent());
        assertEquals(Player.LocationPrecision.REDUCED_100M, result.get());
    }

    @Test
    void getLocationPrecisionSettings_playerDoesNotExist_returnsEmpty() throws PlayerPersistenceException {
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(null);
        Optional<Player.LocationPrecision> result = playerService.getLocationPrecisionSettings(testPlayerId);
        assertFalse(result.isPresent());
    }

    @Test
    void updateLocationPrecisionSettings_playerExists_updatesAndReturnsPlayer() throws Exception {
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);
        doNothing().when(mockPlayerDao).savePlayer(any(Player.class));

        Player result = playerService.updateLocationPrecisionSettings(testPlayerId, Player.LocationPrecision.NOISE_ADDED_MED);

        assertNotNull(result);
        assertEquals(Player.LocationPrecision.NOISE_ADDED_MED, result.getLocationPrecision());
        verify(mockPlayerDao).savePlayer(testPlayer);
    }

    @Test
    void updateLocationPrecisionSettings_playerDoesNotExist_throwsPlayerNotFound() {
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(null);
        assertThrows(PlayerNotFoundException.class, () -> {
            playerService.updateLocationPrecisionSettings(testPlayerId, Player.LocationPrecision.PRECISE);
        });
    }

    @Test
    void updateLocationPrecisionSettings_nullPrecision_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            playerService.updateLocationPrecisionSettings(testPlayerId, null);
        });
    }
    
    // --- Tests for getEffectivePlayerLocation (conceptual) ---
    @Test
    void getEffectivePlayerLocation_playerExists_precise_returnsOriginal() {
        testPlayer.setLatitude(10.0);
        testPlayer.setLongitude(20.0);
        testPlayer.setLocationPrecision(Player.LocationPrecision.PRECISE);
        testPlayer.setLocationSharingPaused(false);
        testPlayer.setLocationVisibility(Player.LocationVisibility.VISIBLE_TO_ALL_IN_GAME); // Ensure visible
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);

        Optional<PlayerService.PlayerLocationData> result = playerService.getEffectivePlayerLocation(testPlayerId, "otherPlayerId");

        assertTrue(result.isPresent());
        assertEquals(10.0, result.get().latitude());
        assertEquals(20.0, result.get().longitude());
        assertEquals(Player.LocationPrecision.PRECISE, result.get().appliedPrecision());
    }

    @Test
    void getEffectivePlayerLocation_playerExists_reduced100m_returnsFuzzed() {
        testPlayer.setLatitude(10.12345);
        testPlayer.setLongitude(20.54321);
        testPlayer.setLocationPrecision(Player.LocationPrecision.REDUCED_100M);
        testPlayer.setLocationSharingPaused(false);
        testPlayer.setLocationVisibility(Player.LocationVisibility.VISIBLE_TO_ALL_IN_GAME);
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);

        Optional<PlayerService.PlayerLocationData> result = playerService.getEffectivePlayerLocation(testPlayerId, "otherPlayerId");

        assertTrue(result.isPresent());
        assertEquals(10.123, result.get().latitude(), 0.00001); // Lat fuzzed to 3 decimal places
        assertEquals(20.543, result.get().longitude(), 0.00001); // Lon fuzzed to 3 decimal places
        assertEquals(Player.LocationPrecision.REDUCED_100M, result.get().appliedPrecision());
    }

    @Test
    void getEffectivePlayerLocation_playerExists_noiseAdded_returnsDifferentThanOriginal() {
        testPlayer.setLatitude(10.0);
        testPlayer.setLongitude(20.0);
        testPlayer.setLocationPrecision(Player.LocationPrecision.NOISE_ADDED_LOW);
        testPlayer.setLocationSharingPaused(false);
        testPlayer.setLocationVisibility(Player.LocationVisibility.VISIBLE_TO_ALL_IN_GAME);
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);

        Optional<PlayerService.PlayerLocationData> result = playerService.getEffectivePlayerLocation(testPlayerId, "otherPlayerId");

        assertTrue(result.isPresent());
        // Check that location is not exactly the original, indicating noise was added.
        // Exact values depend on SecureRandom, so we check for *change*.
        assertTrue(result.get().latitude() != 10.0 || result.get().longitude() != 20.0);
        assertEquals(Player.LocationPrecision.NOISE_ADDED_LOW, result.get().appliedPrecision());
    }

    @Test
    void getEffectivePlayerLocation_locationSharingPaused_returnsEmpty() {
        testPlayer.setLatitude(10.0);
        testPlayer.setLongitude(20.0);
        testPlayer.setLocationSharingPaused(true);
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);

        Optional<PlayerService.PlayerLocationData> result = playerService.getEffectivePlayerLocation(testPlayerId, "otherPlayerId");

        assertFalse(result.isPresent());
    }

    @Test
    void getEffectivePlayerLocation_locationHidden_returnsEmpty() {
        testPlayer.setLatitude(10.0);
        testPlayer.setLongitude(20.0);
        testPlayer.setLocationVisibility(Player.LocationVisibility.HIDDEN);
        testPlayer.setLocationSharingPaused(false);
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);

        Optional<PlayerService.PlayerLocationData> result = playerService.getEffectivePlayerLocation(testPlayerId, "otherPlayerId");

        assertFalse(result.isPresent());
    }

    @Test
    void getEffectivePlayerLocation_playerNotFound_returnsEmpty() {
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(null);
        Optional<PlayerService.PlayerLocationData> result = playerService.getEffectivePlayerLocation(testPlayerId, "otherPlayerId");
        assertFalse(result.isPresent());
    }
    
    @Test
    void getEffectivePlayerLocation_noBaseLocation_returnsEmpty() {
        testPlayer.setLatitude(null);
        testPlayer.setLongitude(null);
        testPlayer.setLocationSharingPaused(false);
        testPlayer.setLocationVisibility(Player.LocationVisibility.VISIBLE_TO_ALL_IN_GAME);
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);

        Optional<PlayerService.PlayerLocationData> result = playerService.getEffectivePlayerLocation(testPlayerId, "otherPlayerId");

        assertFalse(result.isPresent());
    }

    // --- Tests for checkAndApplySensitiveAreaRules ---
    // Note: SENSITIVE_AREAS in PlayerService is private static final.
    // For these tests, we assume the predefined areas:
    // new SensitiveArea("Hospital A", 34.052235, -118.243683, 200)
    // new SensitiveArea("School B", 34.072235, -118.263683, 300)

    @Test
    void checkAndApplySensitiveAreaRules_playerOutsideAllAreas_noChange() {
        testPlayer.setLatitude(35.0); // Far from LA examples
        testPlayer.setLongitude(-119.0);
        testPlayer.setSystemLocationPauseActive(false);

        boolean changed = playerService.checkAndApplySensitiveAreaRules(testPlayer);

        assertFalse(changed);
        assertFalse(testPlayer.isSystemLocationPauseActive());
    }

    @Test
    void checkAndApplySensitiveAreaRules_playerInsideHospitalA_pauseActivated() {
        testPlayer.setLatitude(34.052230); // Inside Hospital A
        testPlayer.setLongitude(-118.243680);
        testPlayer.setSystemLocationPauseActive(false);

        boolean changed = playerService.checkAndApplySensitiveAreaRules(testPlayer);

        assertTrue(changed);
        assertTrue(testPlayer.isSystemLocationPauseActive());
    }

    @Test
    void checkAndApplySensitiveAreaRules_playerInsideSchoolB_pauseActivated() {
        testPlayer.setLatitude(34.072230); // Inside School B
        testPlayer.setLongitude(-118.263680);
        testPlayer.setSystemLocationPauseActive(false);

        boolean changed = playerService.checkAndApplySensitiveAreaRules(testPlayer);

        assertTrue(changed);
        assertTrue(testPlayer.isSystemLocationPauseActive());
    }

    @Test
    void checkAndApplySensitiveAreaRules_playerAlreadyPausedInArea_noChange() {
        testPlayer.setLatitude(34.052230); // Inside Hospital A
        testPlayer.setLongitude(-118.243680);
        testPlayer.setSystemLocationPauseActive(true); // Already paused

        boolean changed = playerService.checkAndApplySensitiveAreaRules(testPlayer);

        assertFalse(changed);
        assertTrue(testPlayer.isSystemLocationPauseActive());
    }

    @Test
    void checkAndApplySensitiveAreaRules_playerMovesFromInsideToOutside_pauseDeactivated() {
        testPlayer.setLatitude(34.052230); // Inside Hospital A initially
        testPlayer.setLongitude(-118.243680);
        playerService.checkAndApplySensitiveAreaRules(testPlayer); // Should activate pause
        assertTrue(testPlayer.isSystemLocationPauseActive());

        // Move player outside
        testPlayer.setLatitude(35.0);
        testPlayer.setLongitude(-119.0);

        boolean changed = playerService.checkAndApplySensitiveAreaRules(testPlayer);

        assertTrue(changed);
        assertFalse(testPlayer.isSystemLocationPauseActive());
    }

    @Test
    void checkAndApplySensitiveAreaRules_playerMovesFromOutsideToInside_pauseActivated() {
        testPlayer.setLatitude(35.0); // Outside initially
        testPlayer.setLongitude(-119.0);
        testPlayer.setSystemLocationPauseActive(false);

        // Move player inside Hospital A
        testPlayer.setLatitude(34.052230);
        testPlayer.setLongitude(-118.243680);

        boolean changed = playerService.checkAndApplySensitiveAreaRules(testPlayer);

        assertTrue(changed);
        assertTrue(testPlayer.isSystemLocationPauseActive());
    }

    @Test
    void checkAndApplySensitiveAreaRules_playerNullLocation_noChange() {
        testPlayer.setLatitude(null);
        testPlayer.setLongitude(null);
        testPlayer.setSystemLocationPauseActive(false);

        boolean changed = playerService.checkAndApplySensitiveAreaRules(testPlayer);
        assertFalse(changed);
        assertFalse(testPlayer.isSystemLocationPauseActive());
    }

    @Test
    void getEffectivePlayerLocation_systemLocationPauseActive_returnsEmpty() {
        testPlayer.setLatitude(10.0);
        testPlayer.setLongitude(20.0);
        testPlayer.setSystemLocationPauseActive(true); // System pause is active
        testPlayer.setLocationSharingPaused(false); // Manual pause is not
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);

        Optional<PlayerService.PlayerLocationData> result = playerService.getEffectivePlayerLocation(testPlayerId, "otherPlayerId");

        assertFalse(result.isPresent());
    }

    @Test
    void getEffectivePlayerLocation_bothSystemAndManualPauseActive_returnsEmpty() {
        testPlayer.setLatitude(10.0);
        testPlayer.setLongitude(20.0);
        testPlayer.setSystemLocationPauseActive(true); 
        testPlayer.setLocationSharingPaused(true); 
        when(mockPlayerDao.findPlayerById(testPlayerId)).thenReturn(testPlayer);

        Optional<PlayerService.PlayerLocationData> result = playerService.getEffectivePlayerLocation(testPlayerId, "otherPlayerId");

        assertFalse(result.isPresent());
    }
} 