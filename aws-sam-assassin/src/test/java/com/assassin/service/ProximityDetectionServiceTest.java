package com.assassin.service;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assassin.config.MapConfiguration;
import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.ConfigurationNotFoundException;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.GameStatus;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;
import com.assassin.util.GeoUtils;

@ExtendWith(MockitoExtension.class)
public class ProximityDetectionServiceTest {

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

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        Field field = ProximityDetectionService.class.getDeclaredField("locationHistoryManager");
        field.setAccessible(true);
        historyManager = field.get(proximityDetectionService);
        assertNotNull(historyManager, "LocationHistoryManager instance should be present in service");
    }

    private Player createPlayerWithLocation(String playerId, double lat, double lon, String timestamp) {
        Player player = new Player();
        player.setPlayerID(playerId);
        player.setLatitude(lat);
        player.setLongitude(lon);
        player.setLocationTimestamp(timestamp);
        player.setGameID("test-game-id");
        player.setStatus(PlayerStatus.ACTIVE.name());
        return player;
    }

    private Player createPlayerWithLocation(String playerId, double lat, double lon, Instant timestamp) {
        Player player = new Player();
        player.setPlayerID(playerId);
        player.setLatitude(lat);
        player.setLongitude(lon);
        player.setLocationTimestamp(timestamp.toString());
        player.setGameID("test-game-id");
        player.setStatus(PlayerStatus.ACTIVE.name());
        return player;
    }

    private Game createGame(String gameId, GameStatus status) {
        Game game = new Game();
        game.setGameID(gameId);
        game.setStatus(status.name());
        game.setSettings(new HashMap<>(Map.of(
            "proximityKillEnabled", true,
            "proximityKillMethod", "GPS",
            "useSmoothedLocations", false,
            "locationStalenessThresholdSeconds", 60,
            "minAccuracyRadiusMeters", 50.0,
            "eliminationDistanceMeters", 10.0
        )));
        game.setMapId("default_map");
        return game;
    }

    private MapConfiguration createMapConfiguration() {
        MapConfiguration config = new MapConfiguration();
        config.setMapId("default_map");
        config.setWeaponDistances(Map.of("default", 10.0));
        return config;
    }

    @Test
    void canEliminateTarget_False_WhenKillerEliminated() throws GameNotFoundException, PlayerNotFoundException {
        Player killer = createPlayerWithLocation("killerId", 34.0522, -118.2437, Instant.now());
        killer.setStatus(PlayerStatus.DEAD.name());
        String weaponId = "default";

        when(playerDao.getPlayerById("killerId")).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById("targetId")).thenReturn(Optional.of(createPlayerWithLocation("targetId", 0, 0, Instant.now())));

        assertFalse(proximityDetectionService.canEliminateTarget("gameId", "killerId", "targetId", weaponId));
        verify(mapConfigurationService, never()).getEffectiveMapConfiguration(anyString());
    }

    @Test
    void canEliminateTarget_False_WhenTargetEliminated() throws GameNotFoundException, PlayerNotFoundException {
        Player killer = createPlayerWithLocation("killerId", 34.0522, -118.2437, Instant.now());
        Player target = createPlayerWithLocation("targetId", 34.0523, -118.2438, Instant.now());
        target.setStatus(PlayerStatus.DEAD.name());
        String weaponId = "default";

        when(playerDao.getPlayerById("killerId")).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById("targetId")).thenReturn(Optional.of(target));

        assertFalse(proximityDetectionService.canEliminateTarget("gameId", "killerId", "targetId", weaponId));
        verify(mapConfigurationService, never()).getEffectiveMapConfiguration(anyString());
    }
    
    @Test
    void canEliminateTarget_False_WhenKillerLocationMissing() throws GameNotFoundException, PlayerNotFoundException {
        Player killer = createPlayerWithLocation("killerId", 34.0522, -118.2437, Instant.now());
        killer.setLatitude(null);
        killer.setLongitude(null);
        String weaponId = "default";

        when(playerDao.getPlayerById("killerId")).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById("targetId")).thenReturn(Optional.of(createPlayerWithLocation("targetId", 34.0523, -118.2438, Instant.now())));

        assertFalse(proximityDetectionService.canEliminateTarget("gameId", "killerId", "targetId", weaponId));
    }

    @Test
    void canEliminateTarget_False_WhenTargetLocationMissing() throws GameNotFoundException, PlayerNotFoundException {
        Player killer = createPlayerWithLocation("killerId", 34.0522, -118.2437, Instant.now());
        Player target = createPlayerWithLocation("targetId", 34.0523, -118.2438, Instant.now());
        target.setLatitude(null);
        target.setLongitude(null);
        String weaponId = "default";

        when(playerDao.getPlayerById("killerId")).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById("targetId")).thenReturn(Optional.of(target));

        assertFalse(proximityDetectionService.canEliminateTarget("gameId", "killerId", "targetId", weaponId));
    }

    @Test
    void canEliminateTarget_False_WhenKillerLocationStale() throws GameNotFoundException, PlayerNotFoundException {
        Instant staleTime = Instant.now().minus(Duration.ofMinutes(5));
        Instant now = Instant.now();
        Player killer = createPlayerWithLocation("killerId", 34.0522, -118.2437, staleTime);
        Player target = createPlayerWithLocation("targetId", 34.0523, -118.2438, now);
        Game game = createGame("gameId", GameStatus.ACTIVE);
        game.getSettings().put("locationStalenessThresholdSeconds", 60);
        String weaponId = "default";

        when(playerDao.getPlayerById("killerId")).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById("targetId")).thenReturn(Optional.of(target));

        assertFalse(proximityDetectionService.canEliminateTarget("gameId", "killerId", "targetId", weaponId));
    }

    @Test
    void canEliminateTarget_False_WhenTargetLocationStale() throws GameNotFoundException, PlayerNotFoundException {
        Instant now = Instant.now();
        Instant staleTime = now.minus(Duration.ofMinutes(5));
        Player killer = createPlayerWithLocation("killerId", 34.0522, -118.2437, now);
        Player target = createPlayerWithLocation("targetId", 34.0523, -118.2438, staleTime);
        Game game = createGame("gameId", GameStatus.ACTIVE);
        game.getSettings().put("locationStalenessThresholdSeconds", 60);
        String weaponId = "default";

        when(playerDao.getPlayerById("killerId")).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById("targetId")).thenReturn(Optional.of(target));

        assertFalse(proximityDetectionService.canEliminateTarget("gameId", "killerId", "targetId", weaponId));
    }

    @Test
    void canEliminateTarget_True_WhenCloseEnough_RawLocation() throws GameNotFoundException, PlayerNotFoundException, ConfigurationNotFoundException {
        Instant now = Instant.now();
        Player killer = createPlayerWithLocation("killerId", 37.77490, -122.41940, now);
        Player target = createPlayerWithLocation("targetId", 37.77495, -122.41940, now);
        Game game = createGame("gameId", GameStatus.ACTIVE);
        game.getSettings().put("eliminationDistanceMeters", 10.0);
        game.getSettings().put("useSmoothedLocations", false);
        String weaponId = "default";

        MapConfiguration mapConfig = createMapConfiguration();
        mapConfig.setWeaponDistances(Map.of(weaponId, 10.0));

        when(playerDao.getPlayerById("killerId")).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById("targetId")).thenReturn(Optional.of(target));
        when(gameDao.getGameById("gameId")).thenReturn(Optional.of(game));
        when(mapConfigurationService.getEffectiveMapConfiguration("gameId")).thenReturn(mapConfig);

        assertTrue(proximityDetectionService.canEliminateTarget("gameId", "killerId", "targetId", weaponId));
    }

    @Test
    void canEliminateTarget_False_WhenTooFar_RawLocation() throws GameNotFoundException, PlayerNotFoundException, ConfigurationNotFoundException {
        Instant now = Instant.now();
        Player killer = createPlayerWithLocation("killerId", 37.7749, -122.4194, now);
        Player target = createPlayerWithLocation("targetId", 37.7760, -122.4195, now);
        Game game = createGame("gameId", GameStatus.ACTIVE);
        game.getSettings().put("eliminationDistanceMeters", 50.0);
        game.getSettings().put("useSmoothedLocations", false);
        String weaponId = "default";

        MapConfiguration mapConfig = createMapConfiguration();
        mapConfig.setWeaponDistances(Map.of(weaponId, 50.0));

        when(playerDao.getPlayerById("killerId")).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById("targetId")).thenReturn(Optional.of(target));
        when(gameDao.getGameById("gameId")).thenReturn(Optional.of(game));
        when(mapConfigurationService.getEffectiveMapConfiguration("gameId")).thenReturn(mapConfig);

        assertFalse(proximityDetectionService.canEliminateTarget("gameId", "killerId", "targetId", weaponId));
    }

    @Test
    void canEliminateTarget_True_WhenCloseEnough_SmoothedLocationUsed() throws Exception {
        // Arrange
        String gameId = "gameSmooth";
        String killerId = "killerSmooth";
        String targetId = "targetSmooth";
        String weaponId = "default";
        double rangeMeters = 10.0;
        MapConfiguration mapConfig = createMapConfiguration();
        mapConfig.setWeaponDistances(Map.of(weaponId, rangeMeters));
        Instant now = Instant.now();

        Player killer = createPlayerWithLocation(killerId, 34.05220, -118.24370, now.minusSeconds(15)); // Older points
        Player target = createPlayerWithLocation(targetId, 34.05230, -118.24380, now.minusSeconds(15)); 

        // Add history to ensure smoothing happens
        TestUtils.addLocationHistoryViaReflection(historyManager, killerId, 
            new Coordinate(34.05220, -118.24370), // Start
            new Coordinate(34.05221, -118.24371), // Move slightly
            new Coordinate(34.05222, -118.24372)  // Closer to target's path
        );
         TestUtils.addLocationHistoryViaReflection(historyManager, targetId, 
            new Coordinate(34.05230, -118.24380), // Start
            new Coordinate(34.05229, -118.24379), // Move slightly
            new Coordinate(34.05228, -118.24378)  // Closer to killer's path
        );
        // Update player objects with the latest location from history addition
        killer.setLatitude(34.05222); killer.setLongitude(-118.24372); killer.setLocationTimestamp(Instant.now().toString());
        target.setLatitude(34.05228); target.setLongitude(-118.24378); target.setLocationTimestamp(Instant.now().toString());
        
        // Assuming the above history makes smoothed locations < 10m apart
        Coordinate smoothedKillerCoord = TestUtils.getSmoothedLocationViaReflection(historyManager, killerId, killer.getLatitude(), killer.getLongitude());
        Coordinate smoothedTargetCoord = TestUtils.getSmoothedLocationViaReflection(historyManager, targetId, target.getLatitude(), target.getLongitude());
        assertTrue(GeoUtils.calculateDistance(smoothedKillerCoord, smoothedTargetCoord) < rangeMeters, "Smoothed distance should be < range");

        Game game = createGame(gameId, GameStatus.ACTIVE);
        game.setMapId(mapConfig.getMapId());
        game.getSettings().put("useSmoothedLocations", true); 
        game.getSettings().put("locationStalenessThresholdSeconds", 60);

        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById(targetId)).thenReturn(Optional.of(target));
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(game));
        when(mapConfigurationService.getEffectiveMapConfiguration(gameId)).thenReturn(mapConfig);

        // Act
        boolean canEliminate = proximityDetectionService.canEliminateTarget(gameId, killerId, targetId, weaponId);

        // Assert: Now that we provide history ensuring smoothed is close, this should pass
        assertTrue(canEliminate, "Should be able to eliminate when smoothed locations are within range");
    }

    @Test
    void canEliminateTarget_False_WhenSmoothedLocationOutsideRange_RawInside() throws Exception {
        // Arrange: Inverse of the above test
        String gameId = "gameSmoothFail";
        String killerId = "killerSmoothFail";
        String targetId = "targetSmoothFail";
        String weaponId = "default";
        double rangeMeters = 15.0; // Increased range
        MapConfiguration mapConfig = createMapConfiguration();
        mapConfig.setWeaponDistances(Map.of(weaponId, rangeMeters));
        Instant now = Instant.now();

        // Raw locations are INSIDE the 15m range (~12.2m apart)
        Player killer = createPlayerWithLocation(killerId, 34.05220, -118.24370, now.minusSeconds(15));
        Player target = createPlayerWithLocation(targetId, 34.05230, -118.24380, now.minusSeconds(15));
        assertTrue(GeoUtils.calculateDistance(new Coordinate(killer.getLatitude(), killer.getLongitude()), new Coordinate(target.getLatitude(), target.getLongitude())) < rangeMeters, "Raw distance should be < range");

        // Add history to ensure smoothing pushes them FAR apart
         TestUtils.addLocationHistoryViaReflection(historyManager, killerId, 
            new Coordinate(34.05220, -118.24370), // Start
            new Coordinate(34.05200, -118.24300), // Move far away
            new Coordinate(34.05180, -118.24230) 
        );
         TestUtils.addLocationHistoryViaReflection(historyManager, targetId, 
            new Coordinate(34.05230, -118.24380), // Start
            new Coordinate(34.05250, -118.24450), // Move far away other direction
            new Coordinate(34.05270, -118.24520) 
        );
        // Update player objects with the latest location from history addition
        killer.setLatitude(34.05180); killer.setLongitude(-118.24230); killer.setLocationTimestamp(Instant.now().toString());
        target.setLatitude(34.05270); target.setLongitude(-118.24520); target.setLocationTimestamp(Instant.now().toString());
        
        // Verify smoothed locations are now > rangeMeters apart
        Coordinate smoothedKillerCoord = TestUtils.getSmoothedLocationViaReflection(historyManager, killerId, killer.getLatitude(), killer.getLongitude());
        Coordinate smoothedTargetCoord = TestUtils.getSmoothedLocationViaReflection(historyManager, targetId, target.getLatitude(), target.getLongitude());
        assertTrue(GeoUtils.calculateDistance(smoothedKillerCoord, smoothedTargetCoord) > rangeMeters, "Smoothed distance should be > range");

        Game game = createGame(gameId, GameStatus.ACTIVE);
        game.setMapId(mapConfig.getMapId());
        game.getSettings().put("useSmoothedLocations", true);
        game.getSettings().put("locationStalenessThresholdSeconds", 60);

        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById(targetId)).thenReturn(Optional.of(target));
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(game));
        when(mapConfigurationService.getEffectiveMapConfiguration(gameId)).thenReturn(mapConfig);

        // Act
        boolean canEliminate = proximityDetectionService.canEliminateTarget(gameId, killerId, targetId, weaponId);

        // Assert: Now that we provide history ensuring smoothed is far, this should pass
        assertFalse(canEliminate, "Should NOT be able to eliminate when smoothed locations are outside range");
    }

    @Test
    void canEliminateTarget_FailDueToSmoothedLocation_WhenRawIsInsideRange() throws Exception {
        Instant now = Instant.now();
        String killerId = "killerSmoothedOutOfRange";
        String targetId = "targetSmoothedOutOfRange";
        String weaponId = "default";
        String gameId = "gameSmoothedFailTest";
        double rangeMeters = 10.0;

        // Raw locations ~9m apart (just inside 10m range)
        Player killer = createPlayerWithLocation(killerId, 37.77490, -122.41940, now.minusSeconds(15));
        Player target = createPlayerWithLocation(targetId, 37.77498, -122.41940, now.minusSeconds(15));
        assertTrue(GeoUtils.calculateDistance(new Coordinate(killer.getLatitude(), killer.getLongitude()), new Coordinate(target.getLatitude(), target.getLongitude())) < rangeMeters, "Raw distance should be < range");

        // Add history indicating movement away from each other, ensuring smoothed locations > 10m apart
        // Add more points to make smoothing more pronounced
         TestUtils.addLocationHistoryViaReflection(historyManager, killerId, 
            new Coordinate(37.77490, -122.41940), // Start closer
            new Coordinate(37.77500, -122.41940), // Move North
            new Coordinate(37.77510, -122.41940)  // Further North 
        );
         TestUtils.addLocationHistoryViaReflection(historyManager, targetId, 
            new Coordinate(37.77498, -122.41940), // Start closer
            new Coordinate(37.77488, -122.41940), // Move South
            new Coordinate(37.77478, -122.41940)  // Further South
        );
        // Update player objects with the latest location from history addition
        killer.setLatitude(37.77510); killer.setLongitude(-122.41940); killer.setLocationTimestamp(Instant.now().toString());
        target.setLatitude(37.77478); target.setLongitude(-122.41940); target.setLocationTimestamp(Instant.now().toString());

        // Invoke getSmoothedLocation via reflection
        Coordinate smoothedKillerLoc = TestUtils.getSmoothedLocationViaReflection(historyManager, killerId, killer.getLatitude(), killer.getLongitude());
        Coordinate smoothedTargetLoc = TestUtils.getSmoothedLocationViaReflection(historyManager, targetId, target.getLatitude(), target.getLongitude());

        assertNotNull(smoothedKillerLoc, "Smoothed killer location is null");
        assertNotNull(smoothedTargetLoc, "Smoothed target location is null");
        // Verify the smoothed distance IS actually > 10m with this history
        double smoothedDistance = GeoUtils.calculateDistance(smoothedKillerLoc, smoothedTargetLoc);
        System.out.println("DEBUG: Smoothed distance in test 'FailDueToSmoothed': " + smoothedDistance);
        assertTrue(smoothedDistance > rangeMeters,
                   "Smoothed distance (" + smoothedDistance + "m) should be greater than " + rangeMeters + "m based on history");

        Game game = createGame(gameId, GameStatus.ACTIVE);
        game.getSettings().put("eliminationDistanceMeters", rangeMeters);
        game.getSettings().put("useSmoothedLocations", true); // Enable smoothed locations

        MapConfiguration mapConfig = createMapConfiguration();
        mapConfig.setWeaponDistances(Map.of(weaponId, rangeMeters));

        when(playerDao.getPlayerById(killerId)).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById(targetId)).thenReturn(Optional.of(target));
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(game));
        when(mapConfigurationService.getEffectiveMapConfiguration(gameId)).thenReturn(mapConfig);

        // Assert that elimination is NOT possible due to smoothed locations being too far
        assertFalse(proximityDetectionService.canEliminateTarget(gameId, killerId, targetId, weaponId),
                    "Elimination should NOT be possible based on smoothed locations being out of range");
    }

    // Helper nested class for Reflection - Consider moving to a test utility package
    private static class TestUtils {
        static void addLocationHistoryViaReflection(Object historyManagerInstance, String playerId, Coordinate... coords) throws Exception {
            Class<?> historyManagerClass = findInnerClass(ProximityDetectionService.class, "LocationHistoryManager");
            java.lang.reflect.Method addLocationMethod = historyManagerClass.getDeclaredMethod("addLocation", String.class, double.class, double.class);
            addLocationMethod.setAccessible(true);
            long time = System.currentTimeMillis() - (coords.length * 1000); // Simulate time passing
            for (Coordinate coord : coords) {
                 addLocationMethod.invoke(historyManagerInstance, playerId, coord.getLatitude(), coord.getLongitude());
                 // We might need to add timestamps too if the real addLocation uses them
                 // Find addLocation method that takes timestamp if necessary
                time += 1000;
            }
        }

        static Coordinate getSmoothedLocationViaReflection(Object historyManagerInstance, String playerId, Double currentLat, Double currentLon) throws Exception {
             Class<?> historyManagerClass = findInnerClass(ProximityDetectionService.class, "LocationHistoryManager");
             java.lang.reflect.Method getSmoothedLocationMethod = historyManagerClass.getDeclaredMethod("getSmoothedLocation", String.class, Double.class, Double.class);
             getSmoothedLocationMethod.setAccessible(true);
             // The real method returns Coordinate
             return (Coordinate) getSmoothedLocationMethod.invoke(historyManagerInstance, playerId, currentLat, currentLon);
        }
        
        static Class<?> findInnerClass(Class<?> outerClass, String innerClassName) throws ClassNotFoundException {
            for (Class<?> cls : outerClass.getDeclaredClasses()) {
                if (cls.getSimpleName().equals(innerClassName)) {
                    return cls;
                }
            }
            throw new ClassNotFoundException("Could not find inner class " + innerClassName + " in " + outerClass.getName());
        }
    }
} 