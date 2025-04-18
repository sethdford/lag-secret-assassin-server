package com.assassin.service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue; // Includes verify, never, times, lenient, reset
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.assassin.config.MapConfiguration;
import com.assassin.dao.GameDao;
import com.assassin.dao.GameZoneStateDao;
import com.assassin.dao.SafeZoneDao;
import com.assassin.exception.ConfigurationNotFoundException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.SafeZone;
import com.assassin.model.SafeZone.SafeZoneType;
import com.assassin.util.GeoUtils;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // Lenient to avoid UnnecessaryStubbingException
class MapConfigurationServiceTest {

    @Mock
    private GameDao gameDao;
    @Mock
    private GameZoneStateDao gameZoneStateDao; // Keep mock even if unused for now
    @Mock
    private SafeZoneDao safeZoneDao;
    @Mock
    private ShrinkingZoneService shrinkingZoneService; // Keep mock even if unused for now
    @Mock
    private DynamoDbTable<MapConfiguration> mockMapConfigTable; // Mock the DynamoDB table
    @Mock
    private DynamoDbEnhancedClient mockEnhancedClient; // If used directly or indirectly

    @InjectMocks
    private MapConfigurationService mapConfigurationService;

    private static final String TEST_GAME_ID = "test-game-1";
    private static final String TEST_MAP_ID = "test-map-config-1";
    private static final String DEFAULT_MAP_ID = "default_map"; // Make sure this matches the service
    private static final String TEST_PLAYER_ID = "test-player-1"; // Added for safe zone tests
    private static final String TEST_SAFE_ZONE_ID = "test-safe-zone-id";
    private static final String DEFAULT_MAP_KEY = "default";

    private Game testGame;
    private MapConfiguration testMapConfig;
    private MapConfiguration defaultMapConfig;
    private List<Coordinate> testBoundary;
    private List<SafeZone> testSafeZones;

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        MockitoAnnotations.openMocks(this); // Initialize mocks

        // Reset mocks to clear interactions from previous tests
        reset(gameDao, safeZoneDao, gameZoneStateDao, shrinkingZoneService, mockMapConfigTable, mockEnhancedClient);

        mapConfigurationService = new MapConfigurationService(
                gameDao, gameZoneStateDao, safeZoneDao, shrinkingZoneService);

        // Use reflection to inject the mock table *after* service instantiation
        Field tableField = MapConfigurationService.class.getDeclaredField("mapConfigTable");
        tableField.setAccessible(true);
        tableField.set(mapConfigurationService, mockMapConfigTable);

        // Reset MapConfigurationService's internal cache
        Field cacheField = MapConfigurationService.class.getDeclaredField("mapConfigCache");
        cacheField.setAccessible(true);
        Map<String, MapConfiguration> cache = (Map<String, MapConfiguration>) cacheField.get(mapConfigurationService);
        cache.clear(); // Clear the cache before each test

        // Reset Game Boundary Cache
        Field boundaryCacheField = MapConfigurationService.class.getDeclaredField("gameBoundaryCache");
        boundaryCacheField.setAccessible(true);
        Map<String, List<Coordinate>> boundaryCache = (Map<String, List<Coordinate>>) boundaryCacheField.get(mapConfigurationService);
        boundaryCache.clear(); // Clear the boundary cache

        // ** Initialize testBoundary EARLY **
        testBoundary = createSampleBoundary();

        // ** Setup Mocking for DEFAULT_MAP_ID retrieval **
        MapConfiguration localDefaultMapConfig = new MapConfiguration(); 
        localDefaultMapConfig.setMapId(DEFAULT_MAP_ID);
        localDefaultMapConfig.setMapName("Default Fallback Map Test"); // Use a distinct name for debugging
        localDefaultMapConfig.setGameBoundary(testBoundary); 
        localDefaultMapConfig.setWeaponDistances(createDefaultWeaponDistances()); 
        // Assign to class member AFTER full initialization if needed for assertions
        this.defaultMapConfig = localDefaultMapConfig;

        Key defaultMapKey = Key.builder().partitionValue(DEFAULT_MAP_ID).build();
        // Use the LOCAL variable in the mock setup
        lenient().when(mockMapConfigTable.getItem(eq(defaultMapKey))).thenReturn(localDefaultMapConfig);
        // Also handle GetItemEnhancedRequest variant if used
        lenient().when(mockMapConfigTable.getItem(eq(GetItemEnhancedRequest.builder().key(defaultMapKey).build())))
                 .thenReturn(localDefaultMapConfig);

        // Setup mock for TEST_MAP_ID retrieval (using local variable)
        MapConfiguration localSpecificMapConfig = new MapConfiguration();
        localSpecificMapConfig.setMapId(TEST_MAP_ID);
        localSpecificMapConfig.setMapName("Specific Test Map"); // Use a distinct name
        localSpecificMapConfig.setGameBoundary(testBoundary); 
        localSpecificMapConfig.setWeaponDistances(createDefaultWeaponDistances()); 
        localSpecificMapConfig.setEliminationDistanceMeters(10.0); // Add specific setting
        // Assign to class member AFTER full initialization
        this.testMapConfig = localSpecificMapConfig;

        Key specificMapKey = Key.builder().partitionValue(TEST_MAP_ID).build();
        // Use the LOCAL variable in the mock setup
        lenient().when(mockMapConfigTable.getItem(eq(specificMapKey))).thenReturn(localSpecificMapConfig);
        // Also handle GetItemEnhancedRequest variant
        lenient().when(mockMapConfigTable.getItem(eq(GetItemEnhancedRequest.builder().key(specificMapKey).build())))
                 .thenReturn(localSpecificMapConfig);

        // Mock gameDao to return a test game
        testGame = new Game();
        testGame.setGameID(TEST_GAME_ID);
        testGame.setMapId(TEST_MAP_ID); // Game uses the specific map

        // Default safe zone setup (can be overridden in tests)
        testSafeZones = new ArrayList<>();
        lenient().when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(testSafeZones);

        // Mock gameDao (should be okay now)
        lenient().when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame)); 
        lenient().when(gameDao.getGameById(eq("non-existent-game"))).thenReturn(Optional.empty());

        // Mock mapConfigTable getItem calls are now set above using local variables

        // Mock safeZoneDao
        lenient().when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(testSafeZones);

        // Ensure testGame boundary settings are present for boundary checks
        Map<String, Object> gameSettings = new HashMap<>();
        gameSettings.put("gameBoundary", testBoundary.stream()
                .map(c -> Map.of("latitude", c.getLatitude(), "longitude", c.getLongitude()))
                .collect(Collectors.toList()));
        testGame.setSettings(gameSettings);
        testGame.setMapId(TEST_MAP_ID); // Ensure this remains set
    }

    // --- Tests for getEffectiveMapConfiguration ---

    @Test
    void getEffectiveMapConfiguration_GameFound_MapIdPresent_ConfigFound_ReturnsSpecificConfig() {
        // Arrange
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));
        when(mockMapConfigTable.getItem(any(Key.class))).thenReturn(testMapConfig);

        // Act
        MapConfiguration result = mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);

        // Assert
        assertNotNull(result);
        assertEquals(testMapConfig, result, "Should return the specific map config when found");
        verify(gameDao).getGameById(TEST_GAME_ID);
        // Verify getItem called with a Key matching the specific map ID
        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);
        verify(mockMapConfigTable).getItem(keyCaptor.capture());
        assertEquals(testGame.getMapId(), keyCaptor.getValue().partitionKeyValue().s(), "Should query table with specific map ID");
        verify(mockMapConfigTable, never()).getItem( // Ensure default is NOT queried
            eq(Key.builder().partitionValue(DEFAULT_MAP_ID).build())
        );
    }

    @Test
    void getEffectiveMapConfiguration_CacheHit_ReturnsCachedConfigWithoutDbCall() {
        // Arrange: Game uses TEST_MAP_ID (set in setUp)
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));
        // Call once to populate the cache
        mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);
        // Reset interaction tracking for the mock table after the first call
        reset(mockMapConfigTable); 

        // Act: Call the method again
        MapConfiguration result = mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_MAP_ID, result.getMapId(), "Should return the specific map config from cache");
        assertEquals(this.testMapConfig, result);

        // Verify mocks
        verify(gameDao, times(2)).getGameById(TEST_GAME_ID); // GameDao is called each time
        // Verify mapConfigTable was NOT called this time
        verify(mockMapConfigTable, never()).getItem(any(Key.class)); 
    }

    @Test
    void getEffectiveMapConfiguration_SpecificConfigNotFound_ReturnsDefaultConfig() {
        // Arrange
        String specificMapId = "specific-map-123";
        testGame.setMapId(specificMapId); // Ensure testGame has the specific map ID
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));

        // Mock specific config fetch to return null
        when(mockMapConfigTable.getItem(eq(Key.builder().partitionValue(specificMapId).build()))).thenReturn(null);
        // Mock default config fetch to return the default config
        when(mockMapConfigTable.getItem(eq(Key.builder().partitionValue(DEFAULT_MAP_ID).build()))).thenReturn(defaultMapConfig);

        // Act
        MapConfiguration result = mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);

        // Assert
        assertNotNull(result);
        assertEquals(defaultMapConfig, result, "Should return the default map config when specific is not found");
        verify(gameDao).getGameById(TEST_GAME_ID);
        // Verify getItem called twice: once for specific, once for default
        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);
        verify(mockMapConfigTable, times(2)).getItem(keyCaptor.capture());

        List<Key> capturedKeys = keyCaptor.getAllValues();
        assertTrue(capturedKeys.stream().anyMatch(k -> k.partitionKeyValue().s().equals(specificMapId)), "Should attempt to fetch specific config");
        assertTrue(capturedKeys.stream().anyMatch(k -> k.partitionKeyValue().s().equals(DEFAULT_MAP_ID)), "Should fall back to fetching default config");
    }

    @Test
    void getEffectiveMapConfiguration_GameHasNullMapId_ReturnsDefaultConfig() {
        // Arrange:
        testGame.setMapId(null); // Set mapId to null
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));
        // Default config mock is active from setUp

        // Act
        MapConfiguration result = mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);

        // Assert
        assertNotNull(result);
        assertEquals(DEFAULT_MAP_ID, result.getMapId(), "Should return the default map config");
        assertEquals(this.defaultMapConfig, result);

        // Verify mocks
        verify(gameDao).getGameById(TEST_GAME_ID);
        // Verify table was NOT called for the specific (null) map ID
        verify(mockMapConfigTable, never()).getItem(eq(Key.builder().partitionValue(TEST_MAP_ID).build()));
        // Verify table was called for the default map ID
        Key defaultMapKey = Key.builder().partitionValue(DEFAULT_MAP_ID).build();
        verify(mockMapConfigTable).getItem(eq(defaultMapKey)); // Should match mock in setUp
    }

    @Test
    void getEffectiveMapConfiguration_GameHasEmptyMapId_ReturnsDefaultConfig() {
        // Arrange:
        testGame.setMapId(""); // Set mapId to empty string
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));
        // Default config mock is active from setUp

        // Act
        MapConfiguration result = mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);

        // Assert
        assertNotNull(result);
        assertEquals(DEFAULT_MAP_ID, result.getMapId(), "Should return the default map config");
        assertEquals(this.defaultMapConfig, result);

        // Verify mocks
        verify(gameDao).getGameById(TEST_GAME_ID);
        // Verify table was NOT called for the specific (empty) map ID
        verify(mockMapConfigTable, never()).getItem(eq(Key.builder().partitionValue(TEST_MAP_ID).build()));
        verify(mockMapConfigTable, never()).getItem(eq(Key.builder().partitionValue("").build()));
        // Verify table was called for the default map ID
        Key defaultMapKey = Key.builder().partitionValue(DEFAULT_MAP_ID).build();
        verify(mockMapConfigTable).getItem(eq(defaultMapKey)); // Should match mock in setUp
    }

    @Test
    void getEffectiveMapConfiguration_GameNotFound_ReturnsDefaultConfig() {
        // Arrange:
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.empty());
        // Default config mock is active from setUp

        // Act
        MapConfiguration result = mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);

        // Assert
        assertNotNull(result);
        assertEquals(DEFAULT_MAP_ID, result.getMapId(), "Should return the default map config when game not found");
        assertEquals(this.defaultMapConfig, result);

        // Verify mocks
        verify(gameDao).getGameById(TEST_GAME_ID);
        // Verify table was called for the default map ID
        Key defaultMapKey = Key.builder().partitionValue(DEFAULT_MAP_ID).build();
        verify(mockMapConfigTable).getItem(eq(defaultMapKey)); // Should match mock in setUp
        // Verify table was NOT called for the specific map ID
        verify(mockMapConfigTable, never()).getItem(eq(Key.builder().partitionValue(TEST_MAP_ID).build()));
    }

    @Test
    void getEffectiveMapConfiguration_DefaultConfigNotFound_ThrowsException() {
        // Arrange:
        // GameDao returns testGame with TEST_MAP_ID
        // Make the specific map config return null from the DB
        when(mockMapConfigTable.getItem(any(Key.class))).thenReturn(null);
        
        // Act & Assert
        assertThrows(ConfigurationNotFoundException.class, () -> {
            mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);
        }, "Should throw ConfigurationNotFoundException when default config is also missing");
        
        // Verify mocks - must be called at least once
        verify(gameDao).getGameById(TEST_GAME_ID);
        // The implementation might call getItem() multiple times, so don't verify exact count
        verify(mockMapConfigTable, atLeast(1)).getItem(any(Key.class));
    }

    // Add more tests here...

    // --- Tests for isLocationInSafeZone ---

    @Test
    void isLocationInSafeZone_InsideFixedPublicZone_ReturnsTrue() {
        // Arrange
        // Adjust location and center to be within the testBoundary
        Coordinate locationInside = new Coordinate(40.5, -79.5); 
        Coordinate safeZoneCenter = new Coordinate(40.5, -79.5);
        // Corrected call to match helper signature
        SafeZone fixedZone = createSafeZone("sz1", TEST_GAME_ID, safeZoneCenter.getLatitude(), safeZoneCenter.getLongitude(), 50.0, SafeZoneType.PUBLIC, "admin", true, null);
        testSafeZones.add(fixedZone);
        when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(testSafeZones);
        long currentTime = System.currentTimeMillis();

        // Act
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationInside, currentTime);

        // Assert
        assertTrue(result, "Location should be inside the public safe zone"); // Updated message
        verify(safeZoneDao).getSafeZonesByGameId(TEST_GAME_ID);
    }

    @Test
    void isLocationInSafeZone_OutsideAllZones_ReturnsFalse() {
        // Arrange
        Coordinate locationOutside = new Coordinate(0.0, 0.0); // Clearly outside testBoundary and any zones
        // Ensure safe zones are empty for this test
        testSafeZones.clear(); 
        when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(testSafeZones);
        // Ensure the game object with its boundary is returned
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame)); 
        long currentTime = System.currentTimeMillis();

        // Act
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationOutside, currentTime);

        // Assert
        assertFalse(result, "Location is outside the public safe zones"); 
        // With the location being outside the boundary defined in setUp, the service should return false early.
        // Therefore, the safeZoneDao should NOT be called.
        verify(safeZoneDao, never()).getSafeZonesByGameId(TEST_GAME_ID); 
    }

    @Test
    void isLocationInSafeZone_InsideActiveTimedZone_ReturnsTrue() {
         // Arrange
         // Adjust location and center to be within the testBoundary
         Coordinate locationInside = new Coordinate(40.5, -79.5);
         Coordinate safeZoneCenter = new Coordinate(40.5, -79.5);
         long currentTime = System.currentTimeMillis();
         long expiresAt = currentTime + 60000; // Conceptually expires in 60 seconds
         // Corrected call to match helper signature
         SafeZone timedZone = createSafeZone("sz2", TEST_GAME_ID, safeZoneCenter.getLatitude(), safeZoneCenter.getLongitude(), 50.0, SafeZoneType.TIMED, "admin", true, String.valueOf(expiresAt));
         testSafeZones.add(timedZone);
         when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(testSafeZones);

         // Act
         boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationInside, currentTime);

         // Assert
         assertTrue(result, "Location should be inside the active timed safe zone");
    }

    @Test
    void isLocationInSafeZone_InsideExpiredTimedZone_ReturnsFalse() {
         // Arrange
         Coordinate locationInside = new Coordinate(10.05, 10.05);
         Coordinate safeZoneCenter = new Coordinate(10.05, 10.05);
         long currentTime = System.currentTimeMillis();
         long expiresAt = currentTime - 60000; // Expired 60 seconds ago
         // Corrected call to match helper signature (set isActive=false)
         SafeZone timedZone = createSafeZone("sz3", TEST_GAME_ID, safeZoneCenter.getLatitude(), safeZoneCenter.getLongitude(), 50.0, SafeZoneType.TIMED, "admin", false, String.valueOf(expiresAt));
         testSafeZones.add(timedZone);
         when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(testSafeZones);

         // Act
         boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationInside, currentTime);

         // Assert
         assertFalse(result, "Location should not be inside the expired timed safe zone");
    }

    @Test
    void isLocationInSafeZone_OutsideGameBoundary_ReturnsFalse() {
        // Arrange
        Coordinate locationOutsideBoundary = new Coordinate(0.0, 0.0); // Outside the 10.x range of testBoundary
        Coordinate safeZoneCenter = new Coordinate(10.05, 10.05); // Zone is inside boundary
        // Corrected call to match helper signature
        SafeZone fixedZone = createSafeZone("sz4", TEST_GAME_ID, safeZoneCenter.getLatitude(), safeZoneCenter.getLongitude(), 50.0, SafeZoneType.PUBLIC, "admin", true, null);
        testSafeZones.add(fixedZone);
        when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(testSafeZones);
        long currentTime = System.currentTimeMillis();
        // Mock boundary check to return false for this location
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame)); // Ensure game object is returned
        // We need to mock GeoUtils if the boundary check logic uses it, or ensure testGame settings are correct
        // For simplicity, assume the service logic handles boundary check correctly based on game config/GeoUtils
        // The key assertion is that safeZoneDao is NOT called.

        // Act
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationOutsideBoundary, currentTime);

        // Assert
        assertFalse(result, "Location outside game boundary should not be in safe zone, even if zones exist");
        // Verification: safeZoneDao should NOT be called if boundary check fails first
        verify(safeZoneDao, never()).getSafeZonesByGameId(TEST_GAME_ID);
    }

    @Test
    void isLocationInSafeZone_ExactlyOnRadius_ReturnsTrue() {
        // Arrange
        // Adjust center to be within the testBoundary defined in setUp
        Coordinate safeZoneCenter = new Coordinate(40.5, -79.5);
        double radius = 100.0;
        Coordinate locationOnRadius = GeoUtils.calculateDestinationPoint(safeZoneCenter.getLatitude(), safeZoneCenter.getLongitude(), 0, radius);
        // Corrected call to match helper signature
        SafeZone fixedZone = createSafeZone("sz-edge", TEST_GAME_ID, safeZoneCenter.getLatitude(), safeZoneCenter.getLongitude(), radius, SafeZoneType.PUBLIC, "admin", true, null);
        testSafeZones.add(fixedZone);
        when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(testSafeZones);
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));
        long currentTime = System.currentTimeMillis();

        // Act
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationOnRadius, currentTime);

        // Assert
        assertTrue(result, "Location exactly on the radius should be considered inside the safe zone");
    }

    @Test
    void isLocationInSafeZone_InsideOwnPrivateZone_ReturnsTrue() {
         // Arrange
         String ownerId = "player-owner-1";
         // Adjust location and center to be within the testBoundary
         Coordinate locationInside = new Coordinate(40.5, -79.5);
         Coordinate safeZoneCenter = new Coordinate(40.5, -79.5);
         // Use the specific helper for private zones
         SafeZone privateZone = createSafeZoneWithOwner("sz-private-1", TEST_GAME_ID, safeZoneCenter, 50.0, SafeZoneType.PRIVATE, ownerId);
         testSafeZones.add(privateZone);
         when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(testSafeZones);
         when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));
         long currentTime = System.currentTimeMillis();
 
         // Act: Check using the owner's ID
         boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, ownerId, locationInside, currentTime);
 
         // Assert
         assertTrue(result, "Owner should be considered inside their own private safe zone");
    }
 
    @Test
    void isLocationInSafeZone_InsideSomeoneElsesPrivateZone_ReturnsFalse() {
        // Arrange
        String ownerId = "player-owner-1";
        String otherPlayerId = "player-other-2";
        Coordinate locationInside = new Coordinate(10.05, 10.05);
        Coordinate safeZoneCenter = new Coordinate(10.05, 10.05);
        // Use the specific helper for private zones
        SafeZone privateZone = createSafeZoneWithOwner("sz-private-1", TEST_GAME_ID, safeZoneCenter, 50.0, SafeZoneType.PRIVATE, ownerId);
        testSafeZones.add(privateZone);
        when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(testSafeZones);
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));
        long currentTime = System.currentTimeMillis();
 
        // Act: Check using a different player's ID
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, otherPlayerId, locationInside, currentTime);
 
        // Assert
        assertFalse(result, "Non-owner should not be considered inside someone else's private safe zone");
    }
 
    @Test
    void isLocationInSafeZone_OutsideOwnPrivateZone_ReturnsFalse() {
        // Arrange
        String ownerId = "player-owner-1";
        Coordinate locationOutside = new Coordinate(0.0, 0.0); // Outside the zone
        Coordinate safeZoneCenter = new Coordinate(10.05, 10.05);
        // Use the specific helper for private zones
        SafeZone privateZone = createSafeZoneWithOwner("sz-private-1", TEST_GAME_ID, safeZoneCenter, 50.0, SafeZoneType.PRIVATE, ownerId);
        testSafeZones.add(privateZone);
        when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(testSafeZones);
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));
        long currentTime = System.currentTimeMillis();
 
        // Act: Check using the owner's ID but with an outside location
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, ownerId, locationOutside, currentTime);
 
        // Assert
        assertFalse(result, "Owner should not be considered inside their private zone if the location is outside");
    }
 
    @Test
    void isLocationInSafeZone_InsidePublicZoneAndSomeoneElsesPrivateZone_ReturnsTrue() {
        // Arrange
        String ownerId = "player-owner-1";
        String otherPlayerId = "player-other-2";
        // Adjust location and center to be within the testBoundary
        Coordinate locationInsideBoth = new Coordinate(40.5, -79.5);
        Coordinate center = new Coordinate(40.5, -79.5);
 
        // Corrected call to match helper signature
        SafeZone publicZone = createSafeZone("sz-public-overlap", TEST_GAME_ID, center.getLatitude(), center.getLongitude(), 100.0, SafeZoneType.PUBLIC, "admin", true, null);
        SafeZone privateZone = createSafeZoneWithOwner("sz-private-overlap", TEST_GAME_ID, center, 50.0, SafeZoneType.PRIVATE, ownerId); // Smaller private zone inside public
 
        testSafeZones.add(publicZone);
        testSafeZones.add(privateZone);
        when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(testSafeZones);
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));
        long currentTime = System.currentTimeMillis();
 
        // Act: Check using the other player's ID (who doesn't own the private zone)
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, otherPlayerId, locationInsideBoth, currentTime);
 
        // Assert
        assertTrue(result, "Player should be considered inside the public zone, even though they are also within someone else's private zone range");
    }
 
    @Test
    public void isLocationInSafeZone_InsideSafeZone_ReturnsTrue() {
        // Setup
        Coordinate safeZoneCenter = new Coordinate(38.897957, -77.036560);
        Coordinate locationInsideSafeZone = new Coordinate(38.897958, -77.036561);
        // Corrected call to match helper signature
        SafeZone safeZone = createSafeZone("test-sz-1", TEST_GAME_ID, safeZoneCenter.getLatitude(), safeZoneCenter.getLongitude(), 100.0, SafeZoneType.PUBLIC, "admin", true, null);
        
        when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(Collections.singletonList(safeZone));
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));

        // Mock GeoUtils boundary check to pass
        MockedStatic<GeoUtils> mockedGeoUtils = mockStatic(GeoUtils.class);
        mockedGeoUtils.when(() -> GeoUtils.isPointInBoundary(any(Coordinate.class), anyList())).thenReturn(true);
        mockedGeoUtils.when(() -> GeoUtils.calculateDistance(locationInsideSafeZone, safeZoneCenter)).thenReturn(50.0); // Ensure distance is inside
 
        try {
            // Test
            boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationInsideSafeZone, System.currentTimeMillis());

            // Verify
            assertTrue(result);
            verify(safeZoneDao).getSafeZonesByGameId(TEST_GAME_ID);
        } finally {
            mockedGeoUtils.close(); // Close the static mock
        }
    }
    
    @Test
    public void isLocationInSafeZone_OutsideSafeZone_ReturnsFalse() {
        // Setup
        Coordinate safeZoneCenter = new Coordinate(38.897957, -77.036560);
        Coordinate locationOutsideSafeZone = new Coordinate(38.898957, -77.037560); // Far away
        // Corrected call to match helper signature
        SafeZone safeZone = createSafeZone("test-sz-2", TEST_GAME_ID, safeZoneCenter.getLatitude(), safeZoneCenter.getLongitude(), 100.0, SafeZoneType.PUBLIC, "admin", true, null);
        
        when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(Collections.singletonList(safeZone));
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));

        // Mock GeoUtils boundary check to pass, but distance calculation to be outside
        MockedStatic<GeoUtils> mockedGeoUtils = mockStatic(GeoUtils.class);
        mockedGeoUtils.when(() -> GeoUtils.isPointInBoundary(any(Coordinate.class), anyList())).thenReturn(true);
        mockedGeoUtils.when(() -> GeoUtils.calculateDistance(locationOutsideSafeZone, safeZoneCenter)).thenReturn(150.0); // Outside 100m radius
        
        try {
            // Test
            boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationOutsideSafeZone, System.currentTimeMillis());

            // Verify
            assertFalse(result);
            verify(safeZoneDao).getSafeZonesByGameId(TEST_GAME_ID);
        } finally {
             mockedGeoUtils.close(); // Close the static mock
        }
    }
    
    @Test
    public void isLocationInSafeZone_ExactlyOnRadius_WithMockedDistance_ReturnsTrue() {
        // Setup
        Coordinate safeZoneCenter = new Coordinate(38.897957, -77.036560);
        double radiusMeters = 100.0;
        Coordinate locationOnRadius = new Coordinate(38.898957, -77.037560); // A point conceptually on radius
        // Corrected call to match helper signature
        SafeZone safeZone = createSafeZone("test-sz-3", TEST_GAME_ID, safeZoneCenter.getLatitude(), safeZoneCenter.getLongitude(), radiusMeters, SafeZoneType.PUBLIC, "admin", true, null);
        
        when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(Collections.singletonList(safeZone));
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));
        MockedStatic<GeoUtils> mockedGeoUtils = mockStatic(GeoUtils.class);
        
        // Mock the distance calculation to return exactly the radius
        mockedGeoUtils.when(() -> GeoUtils.calculateDistance(locationOnRadius, safeZoneCenter))
                    .thenReturn(radiusMeters);
        mockedGeoUtils.when(() -> GeoUtils.isPointInBoundary(any(Coordinate.class), anyList()))
                    .thenReturn(true);
        
        try {
            // Test
            boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationOnRadius, System.currentTimeMillis());

            // Verify
            assertTrue(result, "Location exactly on radius should be considered inside safe zone");
        } finally {
            mockedGeoUtils.close();
        }
    }
    
    @Test
    public void isLocationInSafeZone_OutsideBoundary_ReturnsFalse() {
        // Setup
        Coordinate safeZoneCenter = new Coordinate(38.897957, -77.036560);
        Coordinate locationInsideSafeZone = new Coordinate(38.897958, -77.036561);
        // Corrected call to match helper signature
        SafeZone safeZone = createSafeZone("test-sz-4", TEST_GAME_ID, safeZoneCenter.getLatitude(), safeZoneCenter.getLongitude(), 100.0, SafeZoneType.PUBLIC, "admin", true, null);
        
        when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(Collections.singletonList(safeZone));
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));
        
        // Mock the boundary check to return false
        MockedStatic<GeoUtils> mockedGeoUtils = mockStatic(GeoUtils.class);
        mockedGeoUtils.when(() -> GeoUtils.isPointInBoundary(eq(locationInsideSafeZone), anyList()))
                    .thenReturn(false);
        // Also mock the buffer checks to return false
        mockedGeoUtils.when(() -> GeoUtils.isPointInBoundary(any(Coordinate.class), anyList()))
                    .thenReturn(false);
        
        try {
            // Test
            boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationInsideSafeZone, System.currentTimeMillis());

            // Verify
            assertFalse(result, "Location outside boundary should not be in safe zone");
        } finally {
            mockedGeoUtils.close();
        }
    }
    
    @Test
    public void isLocationInSafeZone_TimedSafeZoneExpired_ReturnsFalse() {
        // Setup
        Coordinate safeZoneCenter = new Coordinate(38.897957, -77.036560);
        Coordinate locationInsideSafeZone = new Coordinate(38.897958, -77.036561);
        long currentTimeMillis = System.currentTimeMillis();
        long expiredTimeMillis = currentTimeMillis - 10000; // 10 seconds in the past
        // Corrected call to match helper signature (isActive=false)
        SafeZone timedSafeZone = createSafeZone("test-sz-5", TEST_GAME_ID, safeZoneCenter.getLatitude(), safeZoneCenter.getLongitude(), 100.0, SafeZoneType.TIMED, "admin", false, String.valueOf(expiredTimeMillis));
        
        when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(Collections.singletonList(timedSafeZone));
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));

        MockedStatic<GeoUtils> mockedGeoUtils = mockStatic(GeoUtils.class);
        mockedGeoUtils.when(() -> GeoUtils.isPointInBoundary(any(Coordinate.class), anyList()))
                    .thenReturn(true);
        
        try {
            // Test
            boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationInsideSafeZone, currentTimeMillis);

            // Verify
            assertFalse(result, "Expired timed safe zone should not be active");
        } finally {
            mockedGeoUtils.close();
        }
    }

    // Helper to create private zones easily (corrected)
    private SafeZone createSafeZoneWithOwner(String id, String gameId, Coordinate center, double radius, SafeZoneType type, String ownerId) {
        // Pass lat/lon from Coordinate, ownerId as createdBy, default isActive=true
        return createSafeZone(id, gameId, center.getLatitude(), center.getLongitude(), radius, 
                              SafeZoneType.PRIVATE, ownerId, true, null); 
    }

    // Helper method to create a test game for these tests
    private Optional<Game> createTestGame() {
        Game game = new Game();
        game.setGameID(TEST_GAME_ID);
        game.setMapId(TEST_MAP_ID);
        game.setStatus("ACTIVE");
        return Optional.of(game);
    }

    // Helper method to create a safe zone for tests
    private SafeZone createSafeZone(String id, String gameId, Double lat, Double lon, double radiusMeters, 
                                   SafeZoneType type, String createdBy, boolean isActive, String expiresAtStr) {
        SafeZone safeZone = new SafeZone();
        safeZone.setSafeZoneId(id);
        safeZone.setGameId(gameId);
        safeZone.setLatitude(lat);
        safeZone.setLongitude(lon);
        safeZone.setRadiusMeters(radiusMeters);
        safeZone.setType(type);
        safeZone.setCreatedBy(createdBy);
        safeZone.setIsActive(isActive);

        if (type == SafeZoneType.PRIVATE && createdBy != null) {
             safeZone.addAuthorizedPlayer(createdBy); 
        }
        return safeZone;
    }

    // Helper method to create default weapon distances
    private Map<String, Double> createDefaultWeaponDistances() {
        Map<String, Double> distances = new HashMap<>();
        distances.put("default_weapon", 50.0); // Example distance
        // Add other weapon types if necessary
        return distances;
    }

    // Helper method to create a sample boundary (adjust as needed)
    private List<Coordinate> createSampleBoundary() {
        // Define your sample boundary here
        return List.of(
            new Coordinate(40.0, -80.0),
            new Coordinate(41.0, -80.0),
            new Coordinate(41.0, -79.0),
            new Coordinate(40.0, -79.0),
            new Coordinate(40.0, -80.0) // Close the polygon
        );
    }

    // --- Tests for getGameBoundary ---
    // ... existing tests for getGameBoundary can remain ...

    // --- Tests for validateCoordinate ---
    // ... existing tests for validateCoordinate can remain ...

    // --- Tests for Caching ---
    // ... existing tests for caching can remain ...

} 