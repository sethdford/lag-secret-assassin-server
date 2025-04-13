package com.assassin.service.verification;

import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.model.Game;
import com.assassin.model.Kill;
import com.assassin.model.Player;
import com.assassin.model.VerificationMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GpsVerificationMethodTest {

    @Mock
    private PlayerDao mockPlayerDao;
    @Mock
    private GameDao mockGameDao;

    @InjectMocks
    private GpsVerificationMethod gpsVerificationMethod;

    private Kill testKill;
    private Player testVictim;
    private Game testGame;
    private String verifierId = "test-verifier";
    private static final String VICTIM_ID = "victim-gps-1";
    private static final String GAME_ID = "game-gps-1";
    private static final double DEFAULT_THRESHOLD = 50.0;
    private static final double CUSTOM_THRESHOLD = 25.0;

    // Sample coordinates (around Central Park, NYC)
    private static final double KILL_LAT = 40.7829;
    private static final double KILL_LON = -73.9654;
    // Victim location ~20m away (within CUSTOM_THRESHOLD)
    private static final double VICTIM_LAT_CLOSE = 40.7830;
    private static final double VICTIM_LON_CLOSE = -73.9655;
    // Victim location ~40m away (outside CUSTOM_THRESHOLD, within DEFAULT_THRESHOLD)
    private static final double VICTIM_LAT_MID = 40.7832;
    private static final double VICTIM_LON_MID = -73.9656;
    // Victim location ~100m away (outside both thresholds)
    private static final double VICTIM_LAT_FAR = 40.7838;
    private static final double VICTIM_LON_FAR = -73.9658;

    @BeforeEach
    void setUp() {
        // @InjectMocks handles instantiation

        testKill = new Kill();
        testKill.setKillerID("killer-gps-1");
        testKill.setVictimID(VICTIM_ID);
        testKill.setTime("2024-01-01T12:00:00Z");
        testKill.setLatitude(KILL_LAT);
        testKill.setLongitude(KILL_LON);
        testKill.setVerificationMethod(VerificationMethod.GPS.name());

        testVictim = new Player();
        testVictim.setPlayerID(VICTIM_ID);
        testVictim.setGameID(GAME_ID);

        testGame = new Game();
        testGame.setGameID(GAME_ID);
        testGame.setSettings(new HashMap<>()); // Initialize settings map
    }

    // --- Helper to mock standard DAO interactions --- 
    private void setupStandardMocks(Double customThresholdValue) {
        when(mockPlayerDao.getPlayerById(VICTIM_ID)).thenReturn(Optional.of(testVictim));
        if (customThresholdValue != null) {
            testGame.getSettings().put("gpsVerificationThresholdMeters", customThresholdValue);
        } else {
             testGame.getSettings().remove("gpsVerificationThresholdMeters"); // Ensure not set
        }
        when(mockGameDao.getGameById(GAME_ID)).thenReturn(Optional.of(testGame));
    }

     private void setupStandardMocksWithDefaultThreshold() {
        setupStandardMocks(null); // Pass null to indicate no custom setting
    }

    @Test
    void constructor_shouldThrowExceptionWhenPlayerDaoIsNull() {
        assertThrows(IllegalArgumentException.class,
                     () -> new GpsVerificationMethod(null, mockGameDao));
    }

    @Test
    void constructor_shouldThrowExceptionWhenGameDaoIsNull() {
        assertThrows(IllegalArgumentException.class,
                     () -> new GpsVerificationMethod(mockPlayerDao, null));
    }

    @Test
    void getMethodType_shouldReturnGps() {
        assertEquals(VerificationMethod.GPS, gpsVerificationMethod.getMethodType());
    }

    @Test
    void hasRequiredData_shouldReturnTrueWhenDataPresent() {
        Map<String, String> input = new HashMap<>();
        input.put("victimLatitude", String.valueOf(VICTIM_LAT_CLOSE));
        input.put("victimLongitude", String.valueOf(VICTIM_LON_CLOSE));
        assertTrue(gpsVerificationMethod.hasRequiredData(input));
    }

    @Test
    void hasRequiredData_shouldReturnFalseWhenLatitudeMissing() {
        Map<String, String> input = new HashMap<>();
        input.put("victimLongitude", String.valueOf(VICTIM_LON_CLOSE));
        assertFalse(gpsVerificationMethod.hasRequiredData(input));
    }

    @Test
    void hasRequiredData_shouldReturnFalseWhenLongitudeMissing() {
        Map<String, String> input = new HashMap<>();
        input.put("victimLatitude", String.valueOf(VICTIM_LAT_CLOSE));
        assertFalse(gpsVerificationMethod.hasRequiredData(input));
    }

    @Test
    void hasRequiredData_shouldReturnFalseWhenInputNull() {
        assertFalse(gpsVerificationMethod.hasRequiredData(null));
    }

    @Test
    void verify_shouldUseDefaultThresholdAndVerifyWhenClose() {
        // Arrange
        setupStandardMocksWithDefaultThreshold();
        Map<String, String> input = new HashMap<>();
        input.put("victimLatitude", String.valueOf(VICTIM_LAT_MID)); // ~40m away
        input.put("victimLongitude", String.valueOf(VICTIM_LON_MID));

        // Act
        VerificationResult result = gpsVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.VERIFIED, result.getStatus());
        assertTrue(result.getNotes().contains("Threshold=%.1f".formatted(DEFAULT_THRESHOLD)), "Notes should contain default threshold");
        verify(mockPlayerDao).getPlayerById(VICTIM_ID);
        verify(mockGameDao).getGameById(GAME_ID);
    }

    @Test
    void verify_shouldUseDefaultThresholdAndRejectWhenFar() {
        // Arrange
        setupStandardMocksWithDefaultThreshold();
        Map<String, String> input = new HashMap<>();
        input.put("victimLatitude", String.valueOf(VICTIM_LAT_FAR)); // ~100m away
        input.put("victimLongitude", String.valueOf(VICTIM_LON_FAR));

        // Act
        VerificationResult result = gpsVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.REJECTED, result.getStatus());
        assertTrue(result.getNotes().contains("Threshold=%.1f".formatted(DEFAULT_THRESHOLD)), "Notes should contain default threshold");
        assertTrue(result.getNotes().contains("Distance exceeds threshold"));
        verify(mockPlayerDao).getPlayerById(VICTIM_ID);
        verify(mockGameDao).getGameById(GAME_ID);
    }

    @Test
    void verify_shouldUseCustomThresholdAndVerifyWhenClose() {
        // Arrange
        setupStandardMocks(CUSTOM_THRESHOLD);
        Map<String, String> input = new HashMap<>();
        input.put("victimLatitude", String.valueOf(VICTIM_LAT_CLOSE)); // ~20m away
        input.put("victimLongitude", String.valueOf(VICTIM_LON_CLOSE));

        // Act
        VerificationResult result = gpsVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.VERIFIED, result.getStatus());
        assertTrue(result.getNotes().contains("Threshold=%.1f".formatted(CUSTOM_THRESHOLD)), "Notes should contain custom threshold");
        verify(mockPlayerDao).getPlayerById(VICTIM_ID);
        verify(mockGameDao).getGameById(GAME_ID);
    }

    @Test
    void verify_shouldUseCustomThresholdAndRejectWhenMidDistance() {
        // Arrange
        setupStandardMocks(CUSTOM_THRESHOLD);
        Map<String, String> input = new HashMap<>();
        input.put("victimLatitude", String.valueOf(VICTIM_LAT_MID)); // ~40m away
        input.put("victimLongitude", String.valueOf(VICTIM_LON_MID));

        // Act
        VerificationResult result = gpsVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.REJECTED, result.getStatus());
        assertTrue(result.getNotes().contains("Threshold=%.1f".formatted(CUSTOM_THRESHOLD)), "Notes should contain custom threshold");
        assertTrue(result.getNotes().contains("Distance exceeds threshold"));
        verify(mockPlayerDao).getPlayerById(VICTIM_ID);
        verify(mockGameDao).getGameById(GAME_ID);
    }

    @Test
    void verify_shouldUseDefaultThresholdWhenSettingInvalidType() {
        // Arrange
        when(mockPlayerDao.getPlayerById(VICTIM_ID)).thenReturn(Optional.of(testVictim));
        testGame.getSettings().put("gpsVerificationThresholdMeters", "not-a-number"); // Invalid type
        when(mockGameDao.getGameById(GAME_ID)).thenReturn(Optional.of(testGame));

        Map<String, String> input = new HashMap<>();
        input.put("victimLatitude", String.valueOf(VICTIM_LAT_MID)); // ~40m away
        input.put("victimLongitude", String.valueOf(VICTIM_LON_MID));

        // Act
        VerificationResult result = gpsVerificationMethod.verify(testKill, input, verifierId);

        // Assert (Should verify because 40m < 50m default)
        assertNotNull(result);
        assertEquals(VerificationResult.Status.VERIFIED, result.getStatus());
        assertTrue(result.getNotes().contains("Threshold=%.1f".formatted(DEFAULT_THRESHOLD)), "Notes should contain default threshold");
    }

     @Test
    void verify_shouldUseDefaultThresholdWhenVictimNotFound() {
        // Arrange
        when(mockPlayerDao.getPlayerById(VICTIM_ID)).thenReturn(Optional.empty());
        Map<String, String> input = new HashMap<>();
        input.put("victimLatitude", String.valueOf(VICTIM_LAT_MID)); // ~40m away
        input.put("victimLongitude", String.valueOf(VICTIM_LON_MID));

        // Act
        VerificationResult result = gpsVerificationMethod.verify(testKill, input, verifierId);

        // Assert (Should verify because 40m < 50m default)
        assertNotNull(result);
        assertEquals(VerificationResult.Status.VERIFIED, result.getStatus());
        assertTrue(result.getNotes().contains("Threshold=%.1f".formatted(DEFAULT_THRESHOLD)), "Notes should contain default threshold");
        verify(mockGameDao, never()).getGameById(anyString()); // GameDao shouldn't be called
    }

    @Test
    void verify_shouldUseDefaultThresholdWhenVictimHasNoGameId() {
        // Arrange
        testVictim.setGameID(null); // Remove game ID
        when(mockPlayerDao.getPlayerById(VICTIM_ID)).thenReturn(Optional.of(testVictim));
        Map<String, String> input = new HashMap<>();
        input.put("victimLatitude", String.valueOf(VICTIM_LAT_MID)); // ~40m away
        input.put("victimLongitude", String.valueOf(VICTIM_LON_MID));

        // Act
        VerificationResult result = gpsVerificationMethod.verify(testKill, input, verifierId);

        // Assert (Should verify because 40m < 50m default)
        assertNotNull(result);
        assertEquals(VerificationResult.Status.VERIFIED, result.getStatus());
        assertTrue(result.getNotes().contains("Threshold=%.1f".formatted(DEFAULT_THRESHOLD)), "Notes should contain default threshold");
        verify(mockGameDao, never()).getGameById(anyString()); // GameDao shouldn't be called
    }

    @Test
    void verify_shouldUseDefaultThresholdWhenGameNotFound() {
        // Arrange
        when(mockPlayerDao.getPlayerById(VICTIM_ID)).thenReturn(Optional.of(testVictim));
        when(mockGameDao.getGameById(GAME_ID)).thenReturn(Optional.empty()); // Game not found
        Map<String, String> input = new HashMap<>();
        input.put("victimLatitude", String.valueOf(VICTIM_LAT_MID)); // ~40m away
        input.put("victimLongitude", String.valueOf(VICTIM_LON_MID));

        // Act
        VerificationResult result = gpsVerificationMethod.verify(testKill, input, verifierId);

        // Assert (Should verify because 40m < 50m default)
        assertNotNull(result);
        assertEquals(VerificationResult.Status.VERIFIED, result.getStatus());
        assertTrue(result.getNotes().contains("Threshold=%.1f".formatted(DEFAULT_THRESHOLD)), "Notes should contain default threshold");
    }

    @Test
    void verify_shouldUseDefaultThresholdWhenGameDaoThrowsException() {
        // Arrange
        when(mockPlayerDao.getPlayerById(VICTIM_ID)).thenReturn(Optional.of(testVictim));
        when(mockGameDao.getGameById(GAME_ID)).thenThrow(new RuntimeException("DAO Error")); // Simulate error
        Map<String, String> input = new HashMap<>();
        input.put("victimLatitude", String.valueOf(VICTIM_LAT_MID)); // ~40m away
        input.put("victimLongitude", String.valueOf(VICTIM_LON_MID));

        // Act
        VerificationResult result = gpsVerificationMethod.verify(testKill, input, verifierId);

        // Assert (Should verify because 40m < 50m default)
        assertNotNull(result);
        assertEquals(VerificationResult.Status.VERIFIED, result.getStatus());
        assertTrue(result.getNotes().contains("Threshold=%.1f".formatted(DEFAULT_THRESHOLD)), "Notes should contain default threshold");
    }

    // --- Tests inherited from previous version, adjusted for mocks --- 

    @Test
    void verify_shouldReturnInvalidInputWhenVictimDataMissing() {
        // Arrange
        Map<String, String> input = new HashMap<>(); // Missing victim coords
        // No DAO calls expected

        // Act
        VerificationResult result = gpsVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.INVALID_INPUT, result.getStatus());
        assertTrue(result.getNotes().contains("Required GPS verification data"));
        verifyNoInteractions(mockPlayerDao, mockGameDao);
    }

    @Test
    void verify_shouldReturnInvalidInputWhenKillLatitudeMissing() {
        // Arrange
        testKill.setLatitude(null);
        Map<String, String> input = new HashMap<>();
        input.put("victimLatitude", String.valueOf(VICTIM_LAT_CLOSE));
        input.put("victimLongitude", String.valueOf(VICTIM_LON_CLOSE));
        // No DAO calls expected

        // Act
        VerificationResult result = gpsVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.INVALID_INPUT, result.getStatus());
        assertTrue(result.getNotes().contains("Kill location data is missing"));
        verifyNoInteractions(mockPlayerDao, mockGameDao);
    }

    @Test
    void verify_shouldReturnInvalidInputWhenKillLongitudeMissing() {
        // Arrange
        testKill.setLongitude(null);
        Map<String, String> input = new HashMap<>();
        input.put("victimLatitude", String.valueOf(VICTIM_LAT_CLOSE));
        input.put("victimLongitude", String.valueOf(VICTIM_LON_CLOSE));
        // No DAO calls expected

        // Act
        VerificationResult result = gpsVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.INVALID_INPUT, result.getStatus());
        assertTrue(result.getNotes().contains("Kill location data is missing"));
        verifyNoInteractions(mockPlayerDao, mockGameDao);
    }

     @Test
    void verify_shouldReturnInvalidInputWhenVictimDataInvalidFormat() {
        // Arrange
        Map<String, String> input = new HashMap<>();
        input.put("victimLatitude", "not-a-number");
        input.put("victimLongitude", String.valueOf(VICTIM_LON_CLOSE));
        // No DAO calls expected

        // Act
        VerificationResult result = gpsVerificationMethod.verify(testKill, input, verifierId);

        // Assert
        assertNotNull(result);
        assertEquals(VerificationResult.Status.INVALID_INPUT, result.getStatus());
        assertTrue(result.getNotes().contains("Invalid format for victim GPS coordinates"));
        verifyNoInteractions(mockPlayerDao, mockGameDao);
    }
} 