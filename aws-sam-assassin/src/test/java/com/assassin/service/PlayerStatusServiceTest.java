package com.assassin.service;

import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GameStateException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.GameZoneState;
import com.assassin.model.Player;
import com.assassin.model.ShrinkingZoneStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.assassin.util.GeoUtils;

@ExtendWith(MockitoExtension.class)
class PlayerStatusServiceTest {

    @Mock
    private PlayerDao playerDao;
    @Mock
    private ShrinkingZoneService shrinkingZoneService;
    @Mock
    private GameDao gameDao;

    @InjectMocks
    private PlayerStatusService playerStatusService;

    private Player testPlayer;
    private Game testGame;
    private GameZoneState testZoneState;
    private ShrinkingZoneStage testStage;
    private Coordinate zoneCenter;
    private double zoneRadius;
    private String gameId = "test-game-1";
    private String playerId = "test-player-1";

    @BeforeEach
    void setUp() {
        zoneCenter = new Coordinate(10.0, 10.0);
        zoneRadius = 1000.0; // 1km

        testPlayer = new Player();
        testPlayer.setPlayerID(playerId);
        testPlayer.setGameID(gameId);
        testPlayer.setStatus("ACTIVE");
        // Location set per test case

        testGame = new Game();
        testGame.setGameID(gameId);
        testGame.setStatus("ACTIVE");

        testStage = new ShrinkingZoneStage();
        testStage.setStageIndex(0);
        testStage.setDamagePerSecond(10.0);

        testGame.setSettings(Map.of(
            "shrinkingZoneConfig", List.of(testStage),
            "zoneDamageIntervalSeconds", 1,
            "zoneEliminationThresholdSeconds", 5
        ));

        testZoneState = new GameZoneState();
        testZoneState.setGameId(gameId);
        testZoneState.setCurrentCenter(zoneCenter);
        testZoneState.setCurrentRadiusMeters(zoneRadius);
        testZoneState.setCurrentStageIndex(0);

        // Default mock behavior
        lenient().when(playerDao.getPlayerById(playerId)).thenReturn(Optional.of(testPlayer));
        lenient().when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        lenient().when(shrinkingZoneService.getCurrentZoneCenter(gameId)).thenReturn(Optional.of(zoneCenter));
        lenient().when(shrinkingZoneService.getCurrentZoneRadius(gameId)).thenReturn(Optional.of(zoneRadius));
        // Assume advanceZoneState returns the state for damage check
        lenient().when(shrinkingZoneService.advanceZoneState(gameId)).thenReturn(Optional.of(testZoneState)); 
    }

    // --- Tests for isPlayerOutsideZone --- 

    @Test
    void isPlayerOutsideZone_WhenPlayerInside_ShouldReturnFalse() throws Exception {
        // Arrange: Player location well within the radius
        testPlayer.setLatitude(10.001); // Approx 111m North
        testPlayer.setLongitude(10.0);

        // Act
        boolean result = playerStatusService.isPlayerOutsideZone(playerId);

        // Assert
        assertFalse(result);
        verify(playerDao).getPlayerById(playerId);
        verify(shrinkingZoneService).getCurrentZoneCenter(gameId);
        verify(shrinkingZoneService).getCurrentZoneRadius(gameId);
    }

    @Test
    void isPlayerOutsideZone_WhenPlayerOutside_ShouldReturnTrue() throws Exception {
        // Arrange: Player location clearly outside radius (e.g., 2km away)
        testPlayer.setLatitude(10.02); // Approx 2.2km North
        testPlayer.setLongitude(10.0);
        
        // Act
        boolean result = playerStatusService.isPlayerOutsideZone(playerId);

        // Assert
        assertTrue(result);
    }
    
    @Test
    void isPlayerOutsideZone_WhenPlayerOnEdge_ShouldReturnFalse() throws Exception {
        // Arrange: Position player very close to the edge (e.g., 999.9m away)
        // We know the center is 10.0, 10.0 and radius is 1000.0.
        // A point slightly less than 1000m north (latitude increase approx 0.009 degrees for 1km near equator)
        double slightOffset = 0.00899; // Slightly less than 1km latitude offset
        testPlayer.setLatitude(zoneCenter.getLatitude() + slightOffset);
        testPlayer.setLongitude(zoneCenter.getLongitude());

        // Verify distance is indeed just inside (optional sanity check)
        // double distance = GeoUtils.calculateDistance(new Coordinate(testPlayer.getLatitude(), testPlayer.getLongitude()), zoneCenter);
        // System.out.println("Edge Test Distance: " + distance);
        
        // Act
        boolean result = playerStatusService.isPlayerOutsideZone(playerId);

        // Assert
        assertFalse(result, "Player very close to the edge should be considered inside");
    }

    @Test
    void isPlayerOutsideZone_WhenPlayerNoLocation_ShouldReturnTrue() throws Exception {
        // Arrange: Player has null lat/lon
        testPlayer.setLatitude(null);
        testPlayer.setLongitude(null);
        
        // Act
        boolean result = playerStatusService.isPlayerOutsideZone(playerId);

        // Assert
        assertTrue(result, "Player with no location should be considered outside");
    }

    @Test
    void isPlayerOutsideZone_WhenPlayerNotInGame_ShouldReturnFalse() throws Exception {
        // Arrange: Player has no gameId
        testPlayer.setGameID(null);
        
        // Act
        boolean result = playerStatusService.isPlayerOutsideZone(playerId);

        // Assert
        assertFalse(result, "Player not in a game should be considered inside/safe");
        verify(shrinkingZoneService, never()).getCurrentZoneCenter(anyString());
        verify(shrinkingZoneService, never()).getCurrentZoneRadius(anyString());
    }

    @Test
    void isPlayerOutsideZone_WhenZoneStateNotFound_ShouldReturnFalse() throws Exception {
         // Arrange: Player has location, but zone service returns empty
        testPlayer.setLatitude(10.0); 
        testPlayer.setLongitude(10.0);
        when(shrinkingZoneService.getCurrentZoneCenter(gameId)).thenReturn(Optional.empty());
        when(shrinkingZoneService.getCurrentZoneRadius(gameId)).thenReturn(Optional.empty());
        
        // Act
        boolean result = playerStatusService.isPlayerOutsideZone(playerId);

        // Assert
        assertFalse(result, "Should be considered safe if zone state is unavailable");
    }
    
    // --- Tests for applyOutOfZoneDamage --- 

    @Test
    void applyOutOfZoneDamage_WhenPlayerInside_ShouldDoNothing() throws Exception {
        // Arrange: Player inside
        testPlayer.setLatitude(10.0);
        testPlayer.setLongitude(10.0);

        // Act
        boolean result = playerStatusService.applyOutOfZoneDamage(playerId);

        // Assert
        assertFalse(result);
        assertNull(testPlayer.getFirstEnteredOutOfZoneTimestamp()); // Ensure timestamp is cleared or remains null
        verify(playerDao, never()).savePlayer(any(Player.class)); // No save if player was already safe
    }

    @Test
    void applyOutOfZoneDamage_WhenPlayerInsideButWasOutside_ShouldClearTimestamp() throws Exception {
        // Arrange: Player inside now, but had an 'outside' timestamp set
        testPlayer.setLatitude(10.0);
        testPlayer.setLongitude(10.0);
        testPlayer.setFirstEnteredOutOfZoneTimestamp(Instant.now().minusSeconds(10).toString());

        // Act
        boolean result = playerStatusService.applyOutOfZoneDamage(playerId);

        // Assert
        assertFalse(result);
        assertNull(testPlayer.getFirstEnteredOutOfZoneTimestamp(), "Timestamp should be cleared on re-entry");
        verify(playerDao).savePlayer(testPlayer); // Should save the cleared timestamp
    }
    
    @Test
    void applyOutOfZoneDamage_WhenPlayerOutsideFirstTime_ShouldSetTimestampAndReturnTrue() throws Exception {
         // Arrange: Player outside, no previous damage/outside timestamps
        testPlayer.setLatitude(10.02); // Outside
        testPlayer.setLongitude(10.0);
        testPlayer.setLastZoneDamageTimestamp(null);
        testPlayer.setFirstEnteredOutOfZoneTimestamp(null);
        
        // Act
        boolean result = playerStatusService.applyOutOfZoneDamage(playerId);
        
        // Assert
        assertTrue(result);
        assertNotNull(testPlayer.getLastZoneDamageTimestamp());
        assertNotNull(testPlayer.getFirstEnteredOutOfZoneTimestamp());
        assertEquals("ACTIVE", testPlayer.getStatus()); // Not eliminated yet
        verify(playerDao).savePlayer(testPlayer);
    }

    @Test
    void applyOutOfZoneDamage_WhenPlayerOutsideBelowInterval_ShouldDoNothing() throws Exception {
        // Arrange: Player outside, but last damage was too recent (e.g., 0s ago)
        testPlayer.setLatitude(10.02); // Outside
        Instant now = Instant.now();
        testPlayer.setLastZoneDamageTimestamp(now.toString());
        testPlayer.setFirstEnteredOutOfZoneTimestamp(now.minusSeconds(10).toString()); // Was outside before
        String initialTimestamp = testPlayer.getLastZoneDamageTimestamp();
        
        // Act
        boolean result = playerStatusService.applyOutOfZoneDamage(playerId);
        
        // Assert
        assertFalse(result);
        assertEquals(initialTimestamp, testPlayer.getLastZoneDamageTimestamp()); // Timestamp unchanged
        assertEquals("ACTIVE", testPlayer.getStatus());
        verify(playerDao, never()).savePlayer(any(Player.class));
    }
    
    @Test
    void applyOutOfZoneDamage_WhenPlayerOutsideAboveIntervalBelowThreshold_ShouldUpdateTimestamp() throws Exception {
        // Arrange: Player outside, interval passed, but below elimination threshold
        testPlayer.setLatitude(10.02); // Outside
        Instant lastDamage = Instant.now().minusSeconds(2); // Interval allows damage
        Instant firstOutside = Instant.now().minusSeconds(3); // Outside for 3s (threshold is 5s)
        testPlayer.setLastZoneDamageTimestamp(lastDamage.toString());
        testPlayer.setFirstEnteredOutOfZoneTimestamp(firstOutside.toString());
        String initialFirstOutside = testPlayer.getFirstEnteredOutOfZoneTimestamp();
        
        // Act
        boolean result = playerStatusService.applyOutOfZoneDamage(playerId);
        
        // Assert
        assertTrue(result);
        assertNotEquals(lastDamage.toString(), testPlayer.getLastZoneDamageTimestamp()); // Timestamp updated
        assertEquals(initialFirstOutside, testPlayer.getFirstEnteredOutOfZoneTimestamp()); // First outside time unchanged
        assertEquals("ACTIVE", testPlayer.getStatus()); // Not eliminated
        verify(playerDao).savePlayer(testPlayer);
    }
    
    @Test
    void applyOutOfZoneDamage_WhenPlayerOutsideAboveIntervalAndThreshold_ShouldEliminate() throws Exception {
        // Arrange: Player outside, interval passed, AND elimination threshold met
        testPlayer.setLatitude(10.02); // Outside
        Instant lastDamage = Instant.now().minusSeconds(2); // Interval allows damage
        Instant firstOutside = Instant.now().minusSeconds(6); // Outside for 6s (threshold is 5s)
        testPlayer.setLastZoneDamageTimestamp(lastDamage.toString());
        testPlayer.setFirstEnteredOutOfZoneTimestamp(firstOutside.toString());
        
        // Act
        boolean result = playerStatusService.applyOutOfZoneDamage(playerId);
        
        // Assert
        assertTrue(result);
        assertNotEquals(lastDamage.toString(), testPlayer.getLastZoneDamageTimestamp()); // Timestamp updated
        assertEquals("DEAD", testPlayer.getStatus()); // Player eliminated
        verify(playerDao).savePlayer(testPlayer);
    }
    
    @Test
    void applyOutOfZoneDamage_WhenImmediateEliminationThreshold_ShouldEliminateOnFirstHit() throws Exception {
        // Arrange: Player outside, interval passed, immediate elimination threshold (0)
        testGame.getSettings().put("zoneEliminationThresholdSeconds", 0);
        testPlayer.setLatitude(10.02); // Outside
        testPlayer.setLastZoneDamageTimestamp(Instant.now().minusSeconds(2).toString()); // Interval allows damage
        testPlayer.setFirstEnteredOutOfZoneTimestamp(null); // First time detected outside
        
        // Act
        boolean result = playerStatusService.applyOutOfZoneDamage(playerId);
        
        // Assert
        assertTrue(result);
        assertNotNull(testPlayer.getLastZoneDamageTimestamp());
        assertNotNull(testPlayer.getFirstEnteredOutOfZoneTimestamp()); // Gets set even on elimination
        assertEquals("DEAD", testPlayer.getStatus()); // Player eliminated
        verify(playerDao).savePlayer(testPlayer);
    }

    @Test
    void applyOutOfZoneDamage_WhenNoDamageInStage_ShouldNotEliminateImmediately() throws Exception {
        // Arrange: Player outside, interval passed, immediate elimination threshold (0), but stage damage is 0
        testStage.setDamagePerSecond(0.0);
        testGame.getSettings().put("zoneEliminationThresholdSeconds", 0); 
        testPlayer.setLatitude(10.02); // Outside
        testPlayer.setLastZoneDamageTimestamp(Instant.now().minusSeconds(2).toString());
        testPlayer.setFirstEnteredOutOfZoneTimestamp(null);
        
        // Act
        boolean result = playerStatusService.applyOutOfZoneDamage(playerId);
        
        // Assert
        assertTrue(result); // Check was performed
        assertNotNull(testPlayer.getLastZoneDamageTimestamp());
        assertNotNull(testPlayer.getFirstEnteredOutOfZoneTimestamp());
        assertEquals("ACTIVE", testPlayer.getStatus()); // NOT eliminated because damagePerSecond is 0
        verify(playerDao).savePlayer(testPlayer);
    }
    
    // Add tests for GameNotFoundException, GameStateException etc. if needed
    
} 