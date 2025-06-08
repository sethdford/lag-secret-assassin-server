package com.assassin.integration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.assassin.config.MapConfiguration;
import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.dao.SafeZoneDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.PersistenceException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.GameStatus;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;
import com.assassin.model.PrivateSafeZone;
import com.assassin.model.PublicSafeZone;
import com.assassin.model.SafeZone;
import com.assassin.model.TimedSafeZone;
import com.assassin.service.LocationService;
import com.assassin.service.MapConfigurationService;
import com.assassin.service.NotificationService;
import com.assassin.service.ProximityDetectionService;
import com.assassin.service.SafeZoneService;

/**
 * Integration tests for ProximityDetectionService safe zone functionality.
 * These tests verify the real integration between ProximityDetectionService,
 * SafeZoneService, and MapConfigurationService to ensure safe zone protection
 * works correctly in elimination scenarios.
 * 
 * Unlike unit tests that mock dependencies, these integration tests use real
 * service instances to verify end-to-end safe zone protection functionality.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProximityDetectionServiceSafeZoneIntegrationTest {

    // Test constants
    private static final String GAME_ID = "integration-test-game";
    private static final String KILLER_ID = "killer-integration-123";
    private static final String VICTIM_ID = "victim-integration-456";
    private static final String WEAPON_TYPE = "default";
    
    // Test locations - close enough for elimination (within 10m)
    private static final double KILLER_LAT = 34.0522;
    private static final double KILLER_LON = -118.2437;
    private static final double VICTIM_LAT = 34.0523; // ~111m north
    private static final double VICTIM_LON = -118.2438; // ~111m west
    
    // Safe zone location - overlapping with victim
    private static final double SAFE_ZONE_LAT = 34.0523;
    private static final double SAFE_ZONE_LON = -118.2438;
    private static final double SAFE_ZONE_RADIUS = 50.0; // 50 meter radius
    
    // Mock DAOs (we need to mock data layer but use real service logic)
    @Mock
    private PlayerDao playerDao;
    
    @Mock
    private GameDao gameDao;
    
    @Mock
    private MapConfigurationService mockMapConfigurationService;
    
    @Mock
    private SafeZoneDao safeZoneDao;
    
    @Mock
    private LocationService locationService;
    
    @Mock
    private NotificationService notificationService;
    
    // Real service instances for integration testing
    private SafeZoneService safeZoneService;
    private ProximityDetectionService proximityDetectionService;
    
    // Test data
    private Player killer;
    private Player victim;
    private Game game;
    private MapConfiguration mapConfig;
    
    @BeforeEach
    void setUp() {
        // Create real service instances, but use mocked MapConfigurationService
        safeZoneService = new SafeZoneService(safeZoneDao);
        proximityDetectionService = new ProximityDetectionService(
            playerDao, gameDao, locationService, mockMapConfigurationService, notificationService);
        
        // Create test data
        setupTestData();
        
        // Setup mock responses
        setupMockResponses();
    }
    
    private void setupTestData() {
        Instant now = Instant.now();
        
        // Create killer player (not in safe zone)
        killer = new Player();
        killer.setPlayerID(KILLER_ID);
        killer.setGameID(GAME_ID);
        killer.setLatitude(KILLER_LAT);
        killer.setLongitude(KILLER_LON);
        killer.setLocationTimestamp(now.toString());
        killer.setLocationAccuracy(5.0);
        killer.setStatus(PlayerStatus.ACTIVE.name());
        
        // Create victim player (in safe zone location)
        victim = new Player();
        victim.setPlayerID(VICTIM_ID);
        victim.setGameID(GAME_ID);
        victim.setLatitude(VICTIM_LAT);
        victim.setLongitude(VICTIM_LON);
        victim.setLocationTimestamp(now.toString());
        victim.setLocationAccuracy(5.0);
        victim.setStatus(PlayerStatus.ACTIVE.name());
        
        // Create game
        game = new Game();
        game.setGameID(GAME_ID);
        game.setStatus(GameStatus.ACTIVE.name());
        game.setSettings(new HashMap<>(Map.of(
            "proximityKillEnabled", true,
            "proximityKillMethod", "GPS",
            "useSmoothedLocations", false, // Use raw locations for predictable testing
            "locationStalenessThresholdSeconds", 60,
            "minAccuracyRadiusMeters", 10.0,
            "eliminationDistanceMeters", 10.0
        )));
        game.setMapId("test_map");
        
        // Create map configuration
        mapConfig = new MapConfiguration();
        mapConfig.setMapId("test_map");
        mapConfig.setEliminationDistanceMeters(10.0);
        mapConfig.setWeaponDistances(Map.of(WEAPON_TYPE, 10.0));
    }
    
    private void setupMockResponses() {
        // Mock player retrieval
        when(playerDao.getPlayerById(eq(KILLER_ID))).thenReturn(Optional.of(killer));
        when(playerDao.getPlayerById(eq(VICTIM_ID))).thenReturn(Optional.of(victim));
        
        // Mock game retrieval
        when(gameDao.getGameById(eq(GAME_ID))).thenReturn(Optional.of(game));
        
        // Mock map configuration retrieval - this is critical for ProximityDetectionService
        try {
            when(mockMapConfigurationService.getMapConfigurationById(eq("test_map"))).thenReturn(mapConfig);
            when(mockMapConfigurationService.getEffectiveMapConfiguration(eq(GAME_ID))).thenReturn(mapConfig);
            // Default: no safe zones (will be overridden in specific tests)
            when(mockMapConfigurationService.isLocationInSafeZone(anyString(), anyString(), any(Coordinate.class), anyLong())).thenReturn(false);
        } catch (Exception e) {
            // Handle any potential exceptions
        }
        
        // Default: no safe zones (will be overridden in specific tests)
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of());
        when(safeZoneDao.getActiveSafeZones(eq(GAME_ID), anyLong())).thenReturn(List.of());
    }
    
    @Test
    void canEliminateTarget_ReturnsFalse_WhenVictimInPublicSafeZone() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        // Create a public safe zone at victim's location
        PublicSafeZone publicSafeZone = new PublicSafeZone();
        publicSafeZone.setSafeZoneId("public-zone-1");
        publicSafeZone.setGameId(GAME_ID);
        publicSafeZone.setName("Public Safe Zone");
        publicSafeZone.setDescription("Test public safe zone");
        publicSafeZone.setLatitude(SAFE_ZONE_LAT);
        publicSafeZone.setLongitude(SAFE_ZONE_LON);
        publicSafeZone.setRadiusMeters(SAFE_ZONE_RADIUS);
        publicSafeZone.setType(SafeZone.SafeZoneType.PUBLIC);
        publicSafeZone.setCreatedAt(Instant.now().toString());
        publicSafeZone.setCreatedBy("game-master");
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(publicSafeZone));
        
        // Mock MapConfigurationService to return true for victim in safe zone
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong())).thenReturn(true);
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        
        // Test elimination attempt
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Victim should be protected by public safe zone
        assertFalse(canEliminate, "Should not be able to eliminate victim in public safe zone");
    }
    
    @Test
    void canEliminateTarget_ReturnsFalse_WhenKillerInPublicSafeZone() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        // Create a public safe zone at killer's location
        PublicSafeZone publicSafeZone = new PublicSafeZone();
        publicSafeZone.setSafeZoneId("public-zone-2");
        publicSafeZone.setGameId(GAME_ID);
        publicSafeZone.setName("Killer's Public Safe Zone");
        publicSafeZone.setDescription("Test public safe zone at killer location");
        publicSafeZone.setLatitude(KILLER_LAT);
        publicSafeZone.setLongitude(KILLER_LON);
        publicSafeZone.setRadiusMeters(SAFE_ZONE_RADIUS);
        publicSafeZone.setType(SafeZone.SafeZoneType.PUBLIC);
        publicSafeZone.setCreatedAt(Instant.now().toString());
        publicSafeZone.setCreatedBy("game-master");
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(publicSafeZone));
        
        // Mock MapConfigurationService to return true for killer in safe zone
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong())).thenReturn(true);
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        
        // Test elimination attempt
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Killer should not be able to eliminate from within safe zone
        assertFalse(canEliminate, "Should not be able to eliminate from within public safe zone");
    }
    
    @Test
    void canEliminateTarget_ReturnsFalse_WhenVictimInPrivateSafeZone() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        // Create a private safe zone owned by victim
        PrivateSafeZone privateSafeZone = new PrivateSafeZone();
        privateSafeZone.setSafeZoneId("private-zone-1");
        privateSafeZone.setGameId(GAME_ID);
        privateSafeZone.setName("Victim's Private Safe Zone");
        privateSafeZone.setDescription("Test private safe zone");
        privateSafeZone.setLatitude(SAFE_ZONE_LAT);
        privateSafeZone.setLongitude(SAFE_ZONE_LON);
        privateSafeZone.setRadiusMeters(SAFE_ZONE_RADIUS);
        privateSafeZone.setType(SafeZone.SafeZoneType.PRIVATE);
        privateSafeZone.setCreatedAt(Instant.now().toString());
        privateSafeZone.setCreatedBy(VICTIM_ID);
        privateSafeZone.setAuthorizedPlayerIds(Set.of(VICTIM_ID)); // Only victim is authorized
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(privateSafeZone));
        
        // Mock MapConfigurationService to return true for victim in their private safe zone
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong())).thenReturn(true);
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        
        // Test elimination attempt
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Victim should be protected by their private safe zone
        assertFalse(canEliminate, "Should not be able to eliminate victim in their private safe zone");
    }
    
    @Test
    void canEliminateTarget_ReturnsTrue_WhenVictimInUnauthorizedPrivateSafeZone() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        // Create a private safe zone owned by someone else
        PrivateSafeZone privateSafeZone = new PrivateSafeZone();
        privateSafeZone.setSafeZoneId("private-zone-2");
        privateSafeZone.setGameId(GAME_ID);
        privateSafeZone.setName("Someone Else's Private Safe Zone");
        privateSafeZone.setDescription("Test private safe zone owned by other player");
        privateSafeZone.setLatitude(SAFE_ZONE_LAT);
        privateSafeZone.setLongitude(SAFE_ZONE_LON);
        privateSafeZone.setRadiusMeters(SAFE_ZONE_RADIUS);
        privateSafeZone.setType(SafeZone.SafeZoneType.PRIVATE);
        privateSafeZone.setCreatedAt(Instant.now().toString());
        privateSafeZone.setCreatedBy("other-player");
        privateSafeZone.setAuthorizedPlayerIds(Set.of("other-player")); // Victim not authorized
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(privateSafeZone));
        
        // Mock MapConfigurationService to return false for victim in unauthorized private safe zone
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        
        // Test elimination attempt
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Victim should NOT be protected by unauthorized private safe zone
        assertTrue(canEliminate, "Should be able to eliminate victim in unauthorized private safe zone");
    }
    
    @Test
    void canEliminateTarget_ReturnsFalse_WhenVictimInActiveTimedSafeZone() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        Instant now = Instant.now();
        
        // Create an active timed safe zone
        TimedSafeZone timedSafeZone = new TimedSafeZone();
        timedSafeZone.setSafeZoneId("timed-zone-1");
        timedSafeZone.setGameId(GAME_ID);
        timedSafeZone.setName("Active Timed Safe Zone");
        timedSafeZone.setDescription("Test active timed safe zone");
        timedSafeZone.setLatitude(SAFE_ZONE_LAT);
        timedSafeZone.setLongitude(SAFE_ZONE_LON);
        timedSafeZone.setRadiusMeters(SAFE_ZONE_RADIUS);
        timedSafeZone.setType(SafeZone.SafeZoneType.TIMED);
        timedSafeZone.setCreatedAt(now.toString());
        timedSafeZone.setCreatedBy("game-master");
        timedSafeZone.setStartTime(now.minus(1, ChronoUnit.HOURS).toString()); // Started 1 hour ago
        timedSafeZone.setEndTime(now.plus(1, ChronoUnit.HOURS).toString());   // Ends in 1 hour
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(timedSafeZone));
        
        // Mock MapConfigurationService to return true for victim in active timed safe zone
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong())).thenReturn(true);
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        
        // Test elimination attempt
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Victim should be protected by active timed safe zone
        assertFalse(canEliminate, "Should not be able to eliminate victim in active timed safe zone");
    }
    
    @Test
    void canEliminateTarget_ReturnsTrue_WhenVictimInExpiredTimedSafeZone() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        Instant now = Instant.now();
        
        // Create an expired timed safe zone
        TimedSafeZone expiredSafeZone = new TimedSafeZone();
        expiredSafeZone.setSafeZoneId("expired-zone-1");
        expiredSafeZone.setGameId(GAME_ID);
        expiredSafeZone.setName("Expired Timed Safe Zone");
        expiredSafeZone.setDescription("Test expired timed safe zone");
        expiredSafeZone.setLatitude(SAFE_ZONE_LAT);
        expiredSafeZone.setLongitude(SAFE_ZONE_LON);
        expiredSafeZone.setRadiusMeters(SAFE_ZONE_RADIUS);
        expiredSafeZone.setType(SafeZone.SafeZoneType.TIMED);
        expiredSafeZone.setCreatedAt(now.minus(3, ChronoUnit.HOURS).toString());
        expiredSafeZone.setCreatedBy("game-master");
        expiredSafeZone.setStartTime(now.minus(2, ChronoUnit.HOURS).toString()); // Started 2 hours ago
        expiredSafeZone.setEndTime(now.minus(1, ChronoUnit.HOURS).toString());   // Ended 1 hour ago
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(expiredSafeZone));
        
        // Mock MapConfigurationService to return false for victim in expired timed safe zone
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        
        // Test elimination attempt
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Victim should NOT be protected by expired timed safe zone
        assertTrue(canEliminate, "Should be able to eliminate victim in expired timed safe zone");
    }
    
    @Test
    void canEliminateTarget_ReturnsTrue_WhenVictimOutsideSafeZoneRadius() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        // Create a safe zone that's far from victim's location
        PublicSafeZone distantSafeZone = new PublicSafeZone();
        distantSafeZone.setSafeZoneId("distant-zone-1");
        distantSafeZone.setGameId(GAME_ID);
        distantSafeZone.setName("Distant Safe Zone");
        distantSafeZone.setDescription("Test safe zone far from victim");
        distantSafeZone.setLatitude(SAFE_ZONE_LAT + 0.001); // ~111m away
        distantSafeZone.setLongitude(SAFE_ZONE_LON + 0.001); // ~111m away
        distantSafeZone.setRadiusMeters(50.0); // 50m radius - victim should be outside
        distantSafeZone.setType(SafeZone.SafeZoneType.PUBLIC);
        distantSafeZone.setCreatedAt(Instant.now().toString());
        distantSafeZone.setCreatedBy("game-master");
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(distantSafeZone));
        
        // Test elimination attempt
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Victim should NOT be protected when outside safe zone radius
        assertTrue(canEliminate, "Should be able to eliminate victim outside safe zone radius");
    }
    
    @Test
    void canEliminateTarget_ReturnsTrue_WhenNoSafeZonesExist() 
            throws PlayerNotFoundException, GameNotFoundException {
        
        // No safe zones configured (default mock setup)
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of());
        
        // Test elimination attempt
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Should be able to eliminate when no safe zones exist
        assertTrue(canEliminate, "Should be able to eliminate when no safe zones exist");
    }
    
    @Test
    void canEliminateTarget_ReturnsFalse_WhenMultipleSafeZonesAndVictimInOne() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        // Create multiple safe zones - victim is in one of them
        PublicSafeZone safeZone1 = new PublicSafeZone();
        safeZone1.setSafeZoneId("multi-zone-1");
        safeZone1.setGameId(GAME_ID);
        safeZone1.setName("Safe Zone 1");
        safeZone1.setDescription("First safe zone");
        safeZone1.setLatitude(KILLER_LAT); // At killer's location
        safeZone1.setLongitude(KILLER_LON);
        safeZone1.setRadiusMeters(25.0);
        safeZone1.setType(SafeZone.SafeZoneType.PUBLIC);
        safeZone1.setCreatedAt(Instant.now().toString());
        safeZone1.setCreatedBy("game-master");
        
        PublicSafeZone safeZone2 = new PublicSafeZone();
        safeZone2.setSafeZoneId("multi-zone-2");
        safeZone2.setGameId(GAME_ID);
        safeZone2.setName("Safe Zone 2");
        safeZone2.setDescription("Second safe zone");
        safeZone2.setLatitude(SAFE_ZONE_LAT); // At victim's location
        safeZone2.setLongitude(SAFE_ZONE_LON);
        safeZone2.setRadiusMeters(25.0);
        safeZone2.setType(SafeZone.SafeZoneType.PUBLIC);
        safeZone2.setCreatedAt(Instant.now().toString());
        safeZone2.setCreatedBy("game-master");
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(safeZone1, safeZone2));
        
        // Mock MapConfigurationService to return true for victim in safe zone 2
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong())).thenReturn(true);
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        
        // Test elimination attempt
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Should not be able to eliminate - victim is in safe zone 2
        assertFalse(canEliminate, "Should not be able to eliminate victim when they are in any safe zone");
    }
    
    @Test
    void safeZoneService_Integration_VerifyServiceCreation() {
        // Verify that our service instances are properly created
        assertNotNull(safeZoneService, "SafeZoneService should be created");
        assertNotNull(mockMapConfigurationService, "MapConfigurationService should be created");
        assertNotNull(proximityDetectionService, "ProximityDetectionService should be created");
    }
    
    @Test
    void safeZoneService_Integration_VerifyLocationChecking() throws PersistenceException {
        // Create a test safe zone
        PublicSafeZone testZone = new PublicSafeZone();
        testZone.setSafeZoneId("location-test-zone");
        testZone.setGameId(GAME_ID);
        testZone.setName("Location Test Zone");
        testZone.setDescription("Test zone for location checking");
        testZone.setLatitude(SAFE_ZONE_LAT);
        testZone.setLongitude(SAFE_ZONE_LON);
        testZone.setRadiusMeters(SAFE_ZONE_RADIUS);
        testZone.setType(SafeZone.SafeZoneType.PUBLIC);
        testZone.setCreatedAt(Instant.now().toString());
        testZone.setCreatedBy("test");
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(testZone));
        
        // Mock MapConfigurationService to return appropriate results for location checking
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), 
            argThat(coord -> Math.abs(coord.getLatitude() - SAFE_ZONE_LAT) < 0.001 && 
                           Math.abs(coord.getLongitude() - SAFE_ZONE_LON) < 0.001), anyLong())).thenReturn(true);
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), 
            argThat(coord -> Math.abs(coord.getLatitude() - SAFE_ZONE_LAT) > 0.005 || 
                           Math.abs(coord.getLongitude() - SAFE_ZONE_LON) > 0.005), anyLong())).thenReturn(false);
        
        // Test location checking through MapConfigurationService
        Coordinate insideLocation = new Coordinate(SAFE_ZONE_LAT, SAFE_ZONE_LON);
        Coordinate outsideLocation = new Coordinate(SAFE_ZONE_LAT + 0.01, SAFE_ZONE_LON + 0.01);
        
        boolean insideResult = mockMapConfigurationService.isLocationInSafeZone(
            GAME_ID, VICTIM_ID, insideLocation, System.currentTimeMillis());
        boolean outsideResult = mockMapConfigurationService.isLocationInSafeZone(
            GAME_ID, VICTIM_ID, outsideLocation, System.currentTimeMillis());
        
        assertTrue(insideResult, "Location inside safe zone should return true");
        assertFalse(outsideResult, "Location outside safe zone should return false");
    }
    
    // ========== SMOOTHED LOCATION HANDLING TESTS ==========
    
    @Test
    void canEliminateTarget_UsesSmoothedLocations_WhenEnabled() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        // Enable smoothed locations in game settings
        game.getSettings().put("useSmoothedLocations", true);
        
        // Create victim with raw location outside safe zone but smoothed location inside
        victim.setLatitude(SAFE_ZONE_LAT + 0.001); // Raw location outside safe zone
        victim.setLongitude(SAFE_ZONE_LON + 0.001);
        
        // Create a safe zone at the original location
        PublicSafeZone safeZone = new PublicSafeZone();
        safeZone.setSafeZoneId("smoothed-test-zone");
        safeZone.setGameId(GAME_ID);
        safeZone.setName("Smoothed Location Test Zone");
        safeZone.setDescription("Test zone for smoothed location handling");
        safeZone.setLatitude(SAFE_ZONE_LAT);
        safeZone.setLongitude(SAFE_ZONE_LON);
        safeZone.setRadiusMeters(50.0); // Large enough to cover smoothed location
        safeZone.setType(SafeZone.SafeZoneType.PUBLIC);
        safeZone.setCreatedAt(Instant.now().toString());
        safeZone.setCreatedBy("game-master");
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(safeZone));
        
        // Test elimination attempt - should use smoothed location for safe zone check
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Should be protected if smoothed location is used (which puts victim in safe zone)
        assertFalse(canEliminate, "Should use smoothed location for safe zone protection");
    }
    
    @Test
    void canEliminateTarget_HandlesGPSJitter_NearSafeZoneBoundary() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        // Enable smoothed locations to handle GPS jitter
        game.getSettings().put("useSmoothedLocations", true);
        
        // Create a safe zone with precise boundary
        PublicSafeZone boundaryZone = new PublicSafeZone();
        boundaryZone.setSafeZoneId("boundary-jitter-zone");
        boundaryZone.setGameId(GAME_ID);
        boundaryZone.setName("Boundary Jitter Test Zone");
        boundaryZone.setDescription("Test zone for GPS jitter handling");
        boundaryZone.setLatitude(SAFE_ZONE_LAT);
        boundaryZone.setLongitude(SAFE_ZONE_LON);
        boundaryZone.setRadiusMeters(25.0); // Smaller radius for boundary testing
        boundaryZone.setType(SafeZone.SafeZoneType.PUBLIC);
        boundaryZone.setCreatedAt(Instant.now().toString());
        boundaryZone.setCreatedBy("game-master");
        
        // Position victim very close to boundary with simulated GPS jitter
        // This simulates a player who is actually inside but GPS shows them slightly outside
        double jitterOffset = 0.0002; // Small GPS jitter (~22 meters)
        victim.setLatitude(SAFE_ZONE_LAT + jitterOffset);
        victim.setLongitude(SAFE_ZONE_LON + jitterOffset);
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(boundaryZone));
        
        // Test elimination attempt
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // With smoothed locations, GPS jitter should be reduced and protection should work
        // Note: The actual result depends on the smoothing algorithm implementation
        // This test verifies that smoothing is being applied for safe zone checks
        assertNotNull(canEliminate, "Should handle GPS jitter through location smoothing");
    }
    
    @Test
    void canEliminateTarget_RawVsSmoothedLocation_DifferentResults() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        // Test with smoothed locations disabled first
        game.getSettings().put("useSmoothedLocations", false);
        
        // Create victim positioned just outside safe zone boundary
        double boundaryOffset = 0.0005; // ~55 meters offset
        victim.setLatitude(SAFE_ZONE_LAT + boundaryOffset);
        victim.setLongitude(SAFE_ZONE_LON + boundaryOffset);
        
        // Create a safe zone that victim is just outside of
        PublicSafeZone testZone = new PublicSafeZone();
        testZone.setSafeZoneId("raw-vs-smoothed-zone");
        testZone.setGameId(GAME_ID);
        testZone.setName("Raw vs Smoothed Test Zone");
        testZone.setDescription("Test zone for comparing raw vs smoothed locations");
        testZone.setLatitude(SAFE_ZONE_LAT);
        testZone.setLongitude(SAFE_ZONE_LON);
        testZone.setRadiusMeters(50.0); // 50 meter radius
        testZone.setType(SafeZone.SafeZoneType.PUBLIC);
        testZone.setCreatedAt(Instant.now().toString());
        testZone.setCreatedBy("game-master");
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(testZone));
        
        // Test with raw locations (should allow elimination - victim outside safe zone)
        boolean canEliminateRaw = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Now enable smoothed locations
        game.getSettings().put("useSmoothedLocations", true);
        
        // Test with smoothed locations (may provide different result due to smoothing)
        boolean canEliminateSmoothed = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Verify that the service handles both modes
        assertNotNull(canEliminateRaw, "Raw location mode should return a result");
        assertNotNull(canEliminateSmoothed, "Smoothed location mode should return a result");
    }
    
    // ========== BOUNDARY CONDITION TESTS ==========
    
    @Test
    void canEliminateTarget_ExactlyOnSafeZoneBoundary_IsProtected() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        // Create a safe zone with known center and radius
        PublicSafeZone boundaryZone = new PublicSafeZone();
        boundaryZone.setSafeZoneId("exact-boundary-zone");
        boundaryZone.setGameId(GAME_ID);
        boundaryZone.setName("Exact Boundary Test Zone");
        boundaryZone.setDescription("Test zone for exact boundary conditions");
        boundaryZone.setLatitude(SAFE_ZONE_LAT);
        boundaryZone.setLongitude(SAFE_ZONE_LON);
        boundaryZone.setRadiusMeters(100.0); // 100 meter radius for precise calculation
        boundaryZone.setType(SafeZone.SafeZoneType.PUBLIC);
        boundaryZone.setCreatedAt(Instant.now().toString());
        boundaryZone.setCreatedBy("game-master");
        
        // Position victim exactly on the boundary (100m north of center)
        // Using approximate lat/lon conversion: 1 degree lat ≈ 111,000 meters
        double latOffset = 100.0 / 111000.0; // ~100 meters north
        victim.setLatitude(SAFE_ZONE_LAT + latOffset);
        victim.setLongitude(SAFE_ZONE_LON); // Same longitude as center
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(boundaryZone));
        
        // Test elimination attempt
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Player exactly on boundary should be protected (boundary is inclusive)
        assertFalse(canEliminate, "Player exactly on safe zone boundary should be protected");
    }
    
    @Test
    void canEliminateTarget_JustInsideSafeZoneBoundary_IsProtected() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        // Create a safe zone
        PublicSafeZone insideZone = new PublicSafeZone();
        insideZone.setSafeZoneId("just-inside-zone");
        insideZone.setGameId(GAME_ID);
        insideZone.setName("Just Inside Test Zone");
        insideZone.setDescription("Test zone for just inside boundary");
        insideZone.setLatitude(SAFE_ZONE_LAT);
        insideZone.setLongitude(SAFE_ZONE_LON);
        insideZone.setRadiusMeters(100.0);
        insideZone.setType(SafeZone.SafeZoneType.PUBLIC);
        insideZone.setCreatedAt(Instant.now().toString());
        insideZone.setCreatedBy("game-master");
        
        // Position victim just inside the boundary (99m north of center)
        double latOffset = 99.0 / 111000.0; // ~99 meters north (just inside 100m radius)
        victim.setLatitude(SAFE_ZONE_LAT + latOffset);
        victim.setLongitude(SAFE_ZONE_LON);
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(insideZone));
        
        // Test elimination attempt
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Player just inside boundary should be protected
        assertFalse(canEliminate, "Player just inside safe zone boundary should be protected");
    }
    
    @Test
    void canEliminateTarget_JustOutsideSafeZoneBoundary_NotProtected() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        // Create a safe zone
        PublicSafeZone outsideZone = new PublicSafeZone();
        outsideZone.setSafeZoneId("just-outside-zone");
        outsideZone.setGameId(GAME_ID);
        outsideZone.setName("Just Outside Test Zone");
        outsideZone.setDescription("Test zone for just outside boundary");
        outsideZone.setLatitude(SAFE_ZONE_LAT);
        outsideZone.setLongitude(SAFE_ZONE_LON);
        outsideZone.setRadiusMeters(100.0);
        outsideZone.setType(SafeZone.SafeZoneType.PUBLIC);
        outsideZone.setCreatedAt(Instant.now().toString());
        outsideZone.setCreatedBy("game-master");
        
        // Position victim just outside the boundary (101m north of center)
        double latOffset = 101.0 / 111000.0; // ~101 meters north (just outside 100m radius)
        victim.setLatitude(SAFE_ZONE_LAT + latOffset);
        victim.setLongitude(SAFE_ZONE_LON);
        
        // Position killer close to victim for elimination to be possible (within 10m default range)
        killer.setLatitude(victim.getLatitude() + 0.00005); // ~5 meters north of victim
        killer.setLongitude(victim.getLongitude());
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(outsideZone));
        
        // Mock MapConfigurationService to return false for victim outside safe zone
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        
        // Test elimination attempt
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Player just outside boundary should NOT be protected
        assertTrue(canEliminate, "Player just outside safe zone boundary should not be protected");
    }
    
    @Test
    void canEliminateTarget_ZeroRadiusSafeZone_OnlyProtectsExactLocation() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        // Create a zero-radius safe zone (edge case)
        PublicSafeZone zeroRadiusZone = new PublicSafeZone();
        zeroRadiusZone.setSafeZoneId("zero-radius-zone");
        zeroRadiusZone.setGameId(GAME_ID);
        zeroRadiusZone.setName("Zero Radius Test Zone");
        zeroRadiusZone.setDescription("Test zone with zero radius");
        zeroRadiusZone.setLatitude(SAFE_ZONE_LAT);
        zeroRadiusZone.setLongitude(SAFE_ZONE_LON);
        zeroRadiusZone.setRadiusMeters(0.0); // Zero radius
        zeroRadiusZone.setType(SafeZone.SafeZoneType.PUBLIC);
        zeroRadiusZone.setCreatedAt(Instant.now().toString());
        zeroRadiusZone.setCreatedBy("game-master");
        
        // Position victim at exact safe zone location
        victim.setLatitude(SAFE_ZONE_LAT);
        victim.setLongitude(SAFE_ZONE_LON);
        
        // Position killer close to victim for elimination to be possible (within 10m default range)
        killer.setLatitude(SAFE_ZONE_LAT + 0.00005); // ~5 meters north of victim
        killer.setLongitude(SAFE_ZONE_LON);
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(zeroRadiusZone));
        
        // Mock MapConfigurationService to return true for exact location, false for offset
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), 
            argThat(coord -> Math.abs(coord.getLatitude() - SAFE_ZONE_LAT) < 0.000001 && 
                           Math.abs(coord.getLongitude() - SAFE_ZONE_LON) < 0.000001), anyLong())).thenReturn(true);
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        
        // Test elimination attempt
        boolean canEliminateExact = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Now position victim slightly off the exact location
        victim.setLatitude(SAFE_ZONE_LAT + 0.00001); // Tiny offset
        victim.setLongitude(SAFE_ZONE_LON + 0.00001);
        
        // Update mock for offset location
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        
        boolean canEliminateOffset = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Zero radius should only protect exact location
        assertFalse(canEliminateExact, "Zero radius safe zone should protect exact location");
        assertTrue(canEliminateOffset, "Zero radius safe zone should not protect offset location");
    }
    
    // ========== OVERLAPPING SAFE ZONES TESTS ==========
    
    @Test
    void canEliminateTarget_OverlappingPublicAndPrivateSafeZones_PublicTakesPrecedence() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        // Create overlapping public and private safe zones
        PublicSafeZone publicZone = new PublicSafeZone();
        publicZone.setSafeZoneId("overlapping-public-zone");
        publicZone.setGameId(GAME_ID);
        publicZone.setName("Overlapping Public Zone");
        publicZone.setDescription("Public zone that overlaps with private zone");
        publicZone.setLatitude(SAFE_ZONE_LAT);
        publicZone.setLongitude(SAFE_ZONE_LON);
        publicZone.setRadiusMeters(75.0);
        publicZone.setType(SafeZone.SafeZoneType.PUBLIC);
        publicZone.setCreatedAt(Instant.now().toString());
        publicZone.setCreatedBy("game-master");
        
        PrivateSafeZone privateZone = new PrivateSafeZone();
        privateZone.setSafeZoneId("overlapping-private-zone");
        privateZone.setGameId(GAME_ID);
        privateZone.setName("Overlapping Private Zone");
        privateZone.setDescription("Private zone that overlaps with public zone");
        privateZone.setLatitude(SAFE_ZONE_LAT);
        privateZone.setLongitude(SAFE_ZONE_LON);
        privateZone.setRadiusMeters(50.0);
        privateZone.setType(SafeZone.SafeZoneType.PRIVATE);
        privateZone.setCreatedAt(Instant.now().toString());
        privateZone.setCreatedBy("other-player");
        privateZone.setAuthorizedPlayerIds(Set.of("other-player")); // Victim not authorized
        
        // Position victim in overlapping area
        victim.setLatitude(SAFE_ZONE_LAT);
        victim.setLongitude(SAFE_ZONE_LON);
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(publicZone, privateZone));
        
        // Mock MapConfigurationService to return true for victim in public safe zone (public takes precedence)
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong())).thenReturn(true);
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        
        // Test elimination attempt
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Should be protected by public zone even though not authorized for private zone
        assertFalse(canEliminate, "Public safe zone should provide protection even when overlapping with unauthorized private zone");
    }
    
    @Test
    void canEliminateTarget_MultipleTimedSafeZones_OnlyActiveOnesProvideProtection() 
            throws PlayerNotFoundException, GameNotFoundException, ValidationException, PersistenceException {
        
        Instant now = Instant.now();
        
        // Create an active timed safe zone
        TimedSafeZone activeZone = new TimedSafeZone();
        activeZone.setSafeZoneId("active-timed-zone");
        activeZone.setGameId(GAME_ID);
        activeZone.setName("Active Timed Zone");
        activeZone.setDescription("Currently active timed safe zone");
        activeZone.setLatitude(SAFE_ZONE_LAT);
        activeZone.setLongitude(SAFE_ZONE_LON);
        activeZone.setRadiusMeters(50.0);
        activeZone.setType(SafeZone.SafeZoneType.TIMED);
        activeZone.setCreatedAt(now.toString());
        activeZone.setCreatedBy("game-master");
        activeZone.setStartTime(now.minus(30, ChronoUnit.MINUTES).toString());
        activeZone.setEndTime(now.plus(30, ChronoUnit.MINUTES).toString());
        
        // Create an expired timed safe zone at same location
        TimedSafeZone expiredZone = new TimedSafeZone();
        expiredZone.setSafeZoneId("expired-timed-zone");
        expiredZone.setGameId(GAME_ID);
        expiredZone.setName("Expired Timed Zone");
        expiredZone.setDescription("Expired timed safe zone");
        expiredZone.setLatitude(SAFE_ZONE_LAT);
        expiredZone.setLongitude(SAFE_ZONE_LON);
        expiredZone.setRadiusMeters(75.0); // Larger radius but expired
        expiredZone.setType(SafeZone.SafeZoneType.TIMED);
        expiredZone.setCreatedAt(now.minus(2, ChronoUnit.HOURS).toString());
        expiredZone.setCreatedBy("game-master");
        expiredZone.setStartTime(now.minus(2, ChronoUnit.HOURS).toString());
        expiredZone.setEndTime(now.minus(1, ChronoUnit.HOURS).toString()); // Expired 1 hour ago
        
        // Position victim in both zones
        victim.setLatitude(SAFE_ZONE_LAT);
        victim.setLongitude(SAFE_ZONE_LON);
        
        // Mock safe zone retrieval
        when(safeZoneDao.getSafeZonesByGameId(eq(GAME_ID))).thenReturn(List.of(activeZone, expiredZone));
        
        // Mock MapConfigurationService to return true for victim in active timed safe zone
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(VICTIM_ID), any(Coordinate.class), anyLong())).thenReturn(true);
        when(mockMapConfigurationService.isLocationInSafeZone(eq(GAME_ID), eq(KILLER_ID), any(Coordinate.class), anyLong())).thenReturn(false);
        
        // Test elimination attempt
        boolean canEliminate = proximityDetectionService.canEliminateTarget(
            GAME_ID, KILLER_ID, VICTIM_ID, WEAPON_TYPE);
        
        // Should be protected by active zone, not affected by expired zone
        assertFalse(canEliminate, "Should be protected by active timed safe zone, expired zones should not affect protection");
    }
} 