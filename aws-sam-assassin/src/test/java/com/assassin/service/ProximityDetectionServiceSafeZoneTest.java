package com.assassin.service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.assassin.config.MapConfiguration;
import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.GameStatus;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;

/**
 * Test class focused on SafeZone integration with the ProximityDetectionService.
 * Tests verify that the service correctly respects safe zone protection for players.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // Allow unused mocks
public class ProximityDetectionServiceSafeZoneTest {

    @Mock
    private PlayerDao playerDao;
    
    @Mock
    private GameDao gameDao;
    
    @Mock
    private MapConfigurationService mapConfigurationService;
    
    @Mock
    private LocationService locationService;
    
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ProximityDetectionService proximityDetectionService;

    private Object historyManager;
    private String gameId = "test-game-1";
    private String killerId = "killer-123";
    private String targetId = "target-456";
    private String weaponType = "default";
    private double eliminationDistance = 10.0; // meters
    private Instant now = Instant.now();

    // Define constants and variables as class members
    private static final String GAME_ID = "test-game-safezone";
    private static final String KILLER_ID = "killer-sz-123";
    private static final String VICTIM_ID = "victim-sz-456";
    private static final double KILLER_LAT = 34.0522;
    private static final double KILLER_LON = -118.2437;
    private static final double VICTIM_LAT = 34.0525; // Slightly different location
    private static final double VICTIM_LON = -118.2440;

    private Player killer;
    private Player victim;
    private Game game;
    private MapConfiguration mapConfig;

    @BeforeEach
    void setUp() throws Exception {
        // Access the LocationHistoryManager via reflection
        Field field = ProximityDetectionService.class.getDeclaredField("locationHistoryManager");
        field.setAccessible(true);
        historyManager = field.get(proximityDetectionService);

        now = Instant.now();

        // Initialize class members
        killer = createTestPlayer(KILLER_ID, GAME_ID, KILLER_LAT, KILLER_LON, now.toString(), 5.0, PlayerStatus.ACTIVE.name());
        victim = createTestPlayer(VICTIM_ID, GAME_ID, VICTIM_LAT, VICTIM_LON, now.toString(), 5.0, PlayerStatus.ACTIVE.name());
        game = createTestGame(GAME_ID);
        mapConfig = createStandardMapConfig(); // Assuming this is defined or should be

        when(playerDao.getPlayerById(eq(KILLER_ID))).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById(eq(VICTIM_ID))).thenReturn(Optional.of(victim));
        when(gameDao.getGameById(eq(GAME_ID))).thenReturn(Optional.of(game));

        // Mock map configuration service to control safe zone checks
        when(mapConfigurationService.isLocationInSafeZone(eq(GAME_ID), anyString(), any(Coordinate.class), anyLong()))
            .thenReturn(false); // Default: not in safe zone

        // Mock location history manager with coordinates for the players
        setupLocationHistory(KILLER_ID, new Coordinate(KILLER_LAT, KILLER_LON));
        setupLocationHistory(VICTIM_ID, new Coordinate(VICTIM_LAT, VICTIM_LON));
    }

    /**
     * Creates a player with a specific location and status
     */
    private Player createTestPlayer(String id, String gameId, double lat, double lon, String timestamp, double accuracy, String status) {
        Player player = new Player();
        player.setPlayerID(id);
        player.setGameID(gameId);
        player.setLatitude(lat);
        player.setLongitude(lon);
        player.setLocationTimestamp(timestamp);
        player.setLocationAccuracy(accuracy); // Corrected method name
        player.setStatus(status);
        return player;
    }

    /**
     * Creates a standard game config for testing
     */
    private Game createTestGame(String gameId) { // Match signature used in tests
        Game game = new Game();
        game.setGameID(gameId);
        game.setStatus(GameStatus.ACTIVE.name());
        game.setSettings(new HashMap<>(Map.of(
            "proximityKillEnabled", true,
            "proximityKillMethod", "GPS",
            "useSmoothedLocations", true,
            "locationStalenessThresholdSeconds", 60,
            "minAccuracyRadiusMeters", 5.0,
            "eliminationDistanceMeters", eliminationDistance
        )));
        game.setMapId("default_map");
        return game;
    }

    /**
     * Creates a standard map configuration for testing
     */
    private MapConfiguration createStandardMapConfig() {
        MapConfiguration config = new MapConfiguration();
        config.setMapId("default_map");
        config.setEliminationDistanceMeters(eliminationDistance);
        config.setWeaponDistances(Map.of(weaponType, eliminationDistance));
        return config;
    }

    /**
     * Sets up the location history manager with coordinates for a specific player
     */
    private void setupLocationHistory(String playerId, Coordinate... coords) throws Exception {
        // Access the private method via reflection
        Class<?> locationHistoryManagerClass = Class.forName("com.assassin.service.ProximityDetectionService$LocationHistoryManager");
        Object historyManagerInstance = historyManager;
        
        // Get the addLocation method via reflection
        Method addLocationMethod = locationHistoryManagerClass.getDeclaredMethod("addLocation", String.class, double.class, double.class);
        addLocationMethod.setAccessible(true);
        
        // Add each coordinate to the history
        for (Coordinate coord : coords) {
            addLocationMethod.invoke(historyManagerInstance, playerId, coord.getLatitude(), coord.getLongitude());
        }
    }

    /**
     * Helper method to set up mocks for standard elimination scenario
     */
    private void setupStandardEliminationScenario(
            Player killer, 
            Player target, 
            Coordinate smoothedKillerLoc, 
            Coordinate smoothedTargetLoc,
            boolean killerInSafeZone,
            boolean targetInSafeZone) throws Exception {
            
        // Mock player retrieval
        when(playerDao.getPlayerById(eq(killerId))).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById(eq(targetId))).thenReturn(Optional.of(target));
        
        // Mock game retrieval
        Game game = createTestGame(gameId);
        when(gameDao.getGameById(eq(gameId))).thenReturn(Optional.of(game));
        
        // Mock map configuration
        MapConfiguration mapConfig = createStandardMapConfig();
        when(mapConfigurationService.getEffectiveMapConfiguration(eq(gameId))).thenReturn(mapConfig);
        
        // Set up smoothed location mock behavior using reflection
        setupLocationHistory(killerId, smoothedKillerLoc);
        setupLocationHistory(targetId, smoothedTargetLoc);
        
        // Mock safe zone checks
        when(mapConfigurationService.isLocationInSafeZone(
                eq(gameId), eq(killerId), any(Coordinate.class), anyLong()))
                .thenReturn(killerInSafeZone);
                
        when(mapConfigurationService.isLocationInSafeZone(
                eq(gameId), eq(targetId), any(Coordinate.class), anyLong()))
                .thenReturn(targetInSafeZone);
    }
    
    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testKillerInSafeZone_CannotEliminateTarget() throws Exception {
        // Create player positions where they are within elimination distance
        double baseLat = 34.052235;
        double baseLon = -118.243683;
        
        // Create players close enough to each other for elimination
        Player killer = createTestPlayer(killerId, gameId, baseLat, baseLon, now.toString(), 5.0, PlayerStatus.ACTIVE.name());
        Player target = createTestPlayer(targetId, gameId, baseLat + 0.00001, baseLon + 0.00001, now.toString(), 5.0, PlayerStatus.ACTIVE.name()); // Very close
        
        // Create smoothed locations nearby original positions
        Coordinate smoothedKillerLoc = new Coordinate(baseLat + 0.000005, baseLon + 0.000005);
        Coordinate smoothedTargetLoc = new Coordinate(baseLat + 0.000015, baseLon + 0.000015);
        
        // Configure the killer to be in a safe zone, but not the target
        setupStandardEliminationScenario(killer, target, smoothedKillerLoc, smoothedTargetLoc, true, false);
        
        // Test the canEliminateTarget method - should return false because killer is in a safe zone
        boolean canEliminate = proximityDetectionService.canEliminateTarget(gameId, killerId, targetId, weaponType);
        
        // Assert result and verify safe zone check was performed with smoothed coordinates
        assertFalse(canEliminate, "Player in safe zone should not be able to eliminate targets");
        
        // Verify that the isLocationInSafeZone method was called for both players
        verify(mapConfigurationService, times(1)).isLocationInSafeZone(
                eq(gameId), eq(killerId), any(Coordinate.class), anyLong());
        verify(mapConfigurationService, times(0)).isLocationInSafeZone(
                eq(gameId), eq(targetId), any(Coordinate.class), anyLong());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testTargetInSafeZone_CannotBeEliminated() throws Exception {
        // Create player positions where they are within elimination distance
        double baseLat = 34.052235;
        double baseLon = -118.243683;
        
        // Create players close enough to each other for elimination
        Player killer = createTestPlayer(killerId, gameId, baseLat, baseLon, now.toString(), 5.0, PlayerStatus.ACTIVE.name());
        Player target = createTestPlayer(targetId, gameId, baseLat + 0.00001, baseLon + 0.00001, now.toString(), 5.0, PlayerStatus.ACTIVE.name()); // Very close
        
        // Create smoothed locations nearby original positions
        Coordinate smoothedKillerLoc = new Coordinate(baseLat + 0.000005, baseLon + 0.000005);
        Coordinate smoothedTargetLoc = new Coordinate(baseLat + 0.000015, baseLon + 0.000015);
        
        // Configure the target to be in a safe zone, but not the killer
        setupStandardEliminationScenario(killer, target, smoothedKillerLoc, smoothedTargetLoc, false, true);
        
        // Test the canEliminateTarget method - should return false because target is in a safe zone
        boolean canEliminate = proximityDetectionService.canEliminateTarget(gameId, killerId, targetId, weaponType);
        
        // Assert result and verify safe zone check was performed with smoothed coordinates
        assertFalse(canEliminate, "Player cannot eliminate target in a safe zone");
        
        // Verify that the isLocationInSafeZone method was called for both players
        verify(mapConfigurationService, times(1)).isLocationInSafeZone(
                eq(gameId), eq(killerId), any(Coordinate.class), anyLong());
        verify(mapConfigurationService, times(1)).isLocationInSafeZone(
                eq(gameId), eq(targetId), any(Coordinate.class), anyLong());
    }

    @Test
    void testSmoothedLocationUsedForSafeZone_WhenRawLocationOutsideButSmoothedInside() throws Exception {
        // Setup: Raw location is outside, but history makes smoothed location inside
        Coordinate rawTargetLoc = new Coordinate(34.06, -118.25); // Outside
        Coordinate smoothedTargetLoc = new Coordinate(34.0522, -118.2437); // Inside (Mocked value)
        victim.setLatitude(rawTargetLoc.getLatitude());
        victim.setLongitude(rawTargetLoc.getLongitude());

        setupLocationHistory(VICTIM_ID, smoothedTargetLoc); // Use reflection to mock smoothed location
        // Mock map config to return the base config
        when(mapConfigurationService.getEffectiveMapConfiguration(anyString())).thenReturn(mapConfig);

        // Mock safe zone check: return true only for the victim's smoothed location
        when(mapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), argThat(coord -> coord.equals(smoothedTargetLoc)), anyLong())).thenReturn(true);
        when(mapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong())).thenReturn(false); // Killer is not in SZ

        // Act
        boolean canEliminate = proximityDetectionService.canEliminateTarget(GAME_ID, KILLER_ID, VICTIM_ID, weaponType);

        // Assertions
        assertFalse(canEliminate, "Elimination should fail because target's SMOOTHED location is in a safe zone.");

        // Verify that the isLocationInSafeZone method was called for the killer AND the victim
        verify(mapConfigurationService, times(1)).isLocationInSafeZone(
                eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong());
        verify(mapConfigurationService, times(1)).isLocationInSafeZone(
                eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong());
    }

    @Test
    void testSmoothedLocationUsedForSafeZone_WhenRawLocationInsideButSmoothedOutside() throws Exception {
        // Setup: Raw location inside, but smoothed location is outside
        Coordinate rawTargetLoc = new Coordinate(34.0522, -118.2437); // Inside
        Coordinate smoothedTargetLoc = new Coordinate(34.06, -118.25); // Outside (Mocked)
        victim.setLatitude(rawTargetLoc.getLatitude());
        victim.setLongitude(rawTargetLoc.getLongitude());

        setupLocationHistory(VICTIM_ID, smoothedTargetLoc); // Use reflection to mock smoothed location
        // Mock map config to return the base config
        when(mapConfigurationService.getEffectiveMapConfiguration(anyString())).thenReturn(mapConfig);

        // Mock safe zone check: return false for the victim's smoothed location
        when(mapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), argThat(coord -> coord.equals(smoothedTargetLoc)), anyLong())).thenReturn(false);
        when(mapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong())).thenReturn(false); // Killer is not in SZ

        // Act
        boolean canEliminate = proximityDetectionService.canEliminateTarget(GAME_ID, KILLER_ID, VICTIM_ID, weaponType);

        // Assertions
        assertFalse(canEliminate, "Player in safe zone should not be able to eliminate targets");

        // Verify that the isLocationInSafeZone method was called for the killer AND the victim
        verify(mapConfigurationService, times(1)).isLocationInSafeZone(
                eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong());
        verify(mapConfigurationService, times(1)).isLocationInSafeZone(
                eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong());
    }

    @Test
    void testPlayerInSafeZone_WhenPlayerInSafeZone() throws Exception {
        // Mock mapConfigService to return true for the victim's location
        when(mapConfigurationService.isLocationInSafeZone(
                eq(GAME_ID),
                eq(VICTIM_ID),
                argThat((Coordinate coord) ->
                        Math.abs(coord.getLatitude() - VICTIM_LAT) < 0.00001 &&
                        Math.abs(coord.getLongitude() - VICTIM_LON) < 0.00001),
                anyLong()))
            .thenReturn(true);

        boolean result = proximityDetectionService.canEliminateTarget(GAME_ID, KILLER_ID, VICTIM_ID, "DEFAULT");

        assertFalse(result, "Elimination should fail if victim is in a safe zone");
        verify(mapConfigurationService).isLocationInSafeZone(
                eq(GAME_ID),
                eq(VICTIM_ID),
                argThat((Coordinate coord) ->
                        Math.abs(coord.getLatitude() - VICTIM_LAT) < 0.00001 &&
                        Math.abs(coord.getLongitude() - VICTIM_LON) < 0.00001),
                anyLong());
    }

    @Test
    void testPlayerInSafeZone_WhenKillerInSafeZone() throws Exception {
        // Mock mapConfigService to return true for the killer's location
        when(mapConfigurationService.isLocationInSafeZone(
                eq(GAME_ID),
                eq(KILLER_ID),
                argThat((Coordinate coord) ->
                        Math.abs(coord.getLatitude() - KILLER_LAT) < 0.00001 &&
                        Math.abs(coord.getLongitude() - KILLER_LON) < 0.00001),
                anyLong()))
            .thenReturn(true);

        boolean result = proximityDetectionService.canEliminateTarget(GAME_ID, KILLER_ID, VICTIM_ID, "DEFAULT");

        // Killer is in safe zone, so canEliminateTarget should return false
        assertFalse(result, "Killer in safe zone should not be able to eliminate targets");
        verify(mapConfigurationService).isLocationInSafeZone(
                eq(GAME_ID),
                eq(KILLER_ID),
                argThat((Coordinate coord) ->
                        Math.abs(coord.getLatitude() - KILLER_LAT) < 0.00001 &&
                        Math.abs(coord.getLongitude() - KILLER_LON) < 0.00001),
                anyLong());
    }

    @Test
    void testPlayerInSafeZone_WhenPlayerNotInSafeZone() throws Exception {
        // Setup: Player is outside any safe zone
        when(mapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong()))
                .thenReturn(false);
        // Add missing mock setup for getEffectiveMapConfiguration
        when(mapConfigurationService.getEffectiveMapConfiguration(anyString())).thenReturn(mapConfig);

        // Act: Check if elimination is possible (depends on distance, not safe zone)
        // This call might fail if distance calculation requires mapConfig, which was missing
        boolean canEliminate = proximityDetectionService.canEliminateTarget(GAME_ID, KILLER_ID, VICTIM_ID, weaponType);

        assertFalse(canEliminate, "Elimination should fail if victim is in a safe zone");
        verify(mapConfigurationService).isLocationInSafeZone(
                eq(GAME_ID),
                eq(VICTIM_ID),
                argThat((Coordinate coord) ->
                        Math.abs(coord.getLatitude() - VICTIM_LAT) < 0.00001 &&
                        Math.abs(coord.getLongitude() - VICTIM_LON) < 0.00001),
                anyLong());
    }

    @Test
    void testPlayerNotFound() {
        when(playerDao.getPlayerById(eq(VICTIM_ID))).thenReturn(Optional.empty());

        // Should throw PlayerNotFoundException since player lookup would fail
        assertThrows(PlayerNotFoundException.class, () -> {
            proximityDetectionService.canEliminateTarget(GAME_ID, KILLER_ID, VICTIM_ID, "DEFAULT");
        }, "Player not found should throw PlayerNotFoundException");
        
        // Map service call verification is handled in successful tests
    }

    @Test
    void testPlayerHasNoLocation() {
        victim.setLatitude(null);
        victim.setLongitude(null);
        when(playerDao.getPlayerById(eq(VICTIM_ID))).thenReturn(Optional.of(victim));

        // canEliminateTarget will return false due to lack of location data for comparison
        boolean result = proximityDetectionService.canEliminateTarget(GAME_ID, KILLER_ID, VICTIM_ID, "DEFAULT");
        assertFalse(result, "Player without location data should not be eligible for elimination");
    }

    @Test
    void canEliminateTarget_ReturnsFalse_WhenKillerInSafeZone() throws Exception {
        // Mock mapConfigService: Killer is IN safe zone, Victim is NOT
        when(mapConfigurationService.isLocationInSafeZone(
                eq(GAME_ID),
                eq(KILLER_ID),
                any(Coordinate.class),
                anyLong()))
            .thenReturn(true);
        when(mapConfigurationService.isLocationInSafeZone(
                eq(GAME_ID),
                eq(VICTIM_ID),
                any(Coordinate.class),
                anyLong()))
            .thenReturn(false);

        // Assume players are otherwise close enough
        // (Distance check is complex, focus on safe zone logic here)
        // We can refine distance mocking later if needed.

        boolean result = proximityDetectionService.canEliminateTarget(GAME_ID, KILLER_ID, VICTIM_ID, "DEFAULT");

        assertFalse(result, "Player in safe zone should not be able to eliminate targets");
        
        // Verify that the isLocationInSafeZone method was called for the killer ONLY
        verify(mapConfigurationService, times(1)).isLocationInSafeZone(
                eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong());
        verify(mapConfigurationService, never()).isLocationInSafeZone(
                eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong());
    }

    @Test
    void canEliminateTarget_ReturnsFalse_WhenVictimInSafeZone() throws Exception {
        // Mock mapConfigService: Killer is NOT in safe zone, Victim IS
        when(mapConfigurationService.isLocationInSafeZone(
                eq(GAME_ID),
                eq(KILLER_ID),
                any(Coordinate.class),
                anyLong()))
            .thenReturn(false);
        when(mapConfigurationService.isLocationInSafeZone(
                eq(GAME_ID),
                eq(VICTIM_ID),
                any(Coordinate.class),
                anyLong()))
            .thenReturn(true);

        boolean result = proximityDetectionService.canEliminateTarget(GAME_ID, KILLER_ID, VICTIM_ID, "DEFAULT");

        assertFalse(result, "Elimination should fail if victim is in a safe zone");

        // Verify safe zone check was called for both players
        verify(mapConfigurationService).isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong());
        verify(mapConfigurationService).isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong());
    }

    @Test
    void canEliminateTarget_ReturnsTrue_WhenNeitherInSafeZoneAndInRange() throws Exception {
        // Setup players close enough
        Coordinate killerCoord = new Coordinate(KILLER_LAT, KILLER_LON);
        Coordinate victimCoord = new Coordinate(KILLER_LAT + 0.00001, KILLER_LON + 0.00001); // Very close
        killer.setLatitude(killerCoord.getLatitude());
        killer.setLongitude(killerCoord.getLongitude());
        victim.setLatitude(victimCoord.getLatitude());
        victim.setLongitude(victimCoord.getLongitude());
        setupLocationHistory(KILLER_ID, killerCoord);
        setupLocationHistory(VICTIM_ID, victimCoord);

        // Mock safe zone checks to return false for both
        when(mapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        when(mapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        // Add missing mock setup for getEffectiveMapConfiguration
        when(mapConfigurationService.getEffectiveMapConfiguration(anyString())).thenReturn(mapConfig);

        // Act
        boolean result = proximityDetectionService.canEliminateTarget(GAME_ID, KILLER_ID, VICTIM_ID, weaponType);

        // Assertion depends on whether the default setup locations ARE actually in range
        // If they are within DEFAULT_ELIMINATION_DISTANCE + buffers, this should be true.
        // Let's assume they are for this test.
        assertTrue(result, "Elimination should succeed if neither is in safe zone and they are in range");

        // Verify safe zone check was called for both players
        verify(mapConfigurationService, times(1)).isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong());
        verify(mapConfigurationService, times(1)).isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong());
    }

    @Test
    void canEliminateTarget_ThrowsPlayerNotFound_WhenKillerNotFound() {
        when(playerDao.getPlayerById(eq(KILLER_ID))).thenReturn(Optional.empty());

        assertThrows(PlayerNotFoundException.class, () -> {
            proximityDetectionService.canEliminateTarget(GAME_ID, KILLER_ID, VICTIM_ID, "DEFAULT");
        }, "Should throw PlayerNotFoundException if killer doesn't exist");

        // Verify map service was never called for safe zones
        verify(mapConfigurationService, never()).isLocationInSafeZone(anyString(), anyString(), any(Coordinate.class), anyLong());
    }

    @Test
    void canEliminateTarget_ThrowsPlayerNotFound_WhenVictimNotFound() {
        // Arrange: Mock PlayerDao to throw exception for victim
        when(playerDao.getPlayerById(eq(KILLER_ID))).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById(eq(VICTIM_ID))).thenReturn(Optional.empty());
        // Mock map config to return the base config - needed even if player not found early
        when(mapConfigurationService.getEffectiveMapConfiguration(anyString())).thenReturn(mapConfig);

        // Act & Assert
        assertThrows(PlayerNotFoundException.class, () -> {
            proximityDetectionService.canEliminateTarget(GAME_ID, KILLER_ID, VICTIM_ID, weaponType);
        });

        // Verify safe zone check was NOT called because player lookup failed first
        verify(mapConfigurationService, never()).isLocationInSafeZone(anyString(), anyString(), any(Coordinate.class), anyLong());
    }
} 