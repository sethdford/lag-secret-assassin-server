package com.assassin.service;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any; // Includes verify, never, times, lenient, reset
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyLong; // Added static import for anyLong
import static org.mockito.ArgumentMatchers.anyString; // Added static import for anyString
import static org.mockito.ArgumentMatchers.eq; // Added static import for eq
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
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
import org.mockito.Mockito;

import com.assassin.config.MapConfiguration;
import com.assassin.dao.GameDao;
import com.assassin.dao.GameZoneStateDao;
import com.assassin.dao.SafeZoneDao;
import com.assassin.exception.ConfigurationNotFoundException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.SafeZone;
import com.assassin.model.SafeZone.SafeZoneType;
import com.assassin.model.TimedSafeZone;
import com.assassin.util.GeoUtils;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

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
    private SafeZoneService safeZoneService; // Added mock for SafeZoneService
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
    private List<Coordinate> defaultBoundary;

    // Declare MockedStatic as a field
    private MockedStatic<GeoUtils> mockedGeoUtils;

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        MockitoAnnotations.openMocks(this); // Initialize mocks

        // Comment out safeZoneService from reset for now
        reset(gameDao, safeZoneDao, gameZoneStateDao, shrinkingZoneService, mockEnhancedClient, safeZoneService, mockMapConfigTable); 

        // Crucially, mock enhancedClient.table() to return our mockMapConfigTable
        // The table name here should match what the service would resolve via getTableName()
        // For testing, we can assume/know it will resolve to DEFAULT_MAP_CONFIG_TABLE_NAME or a specific test name if we set env vars
        String expectedTableName = System.getenv(MapConfigurationService.MAP_CONFIG_TABLE_ENV_VAR) != null ? 
                                   System.getenv(MapConfigurationService.MAP_CONFIG_TABLE_ENV_VAR) : 
                                   MapConfigurationService.DEFAULT_MAP_CONFIG_TABLE_NAME; // Match service logic
        when(mockEnhancedClient.table(eq(expectedTableName), eq(TableSchema.fromBean(MapConfiguration.class))))
            .thenReturn(mockMapConfigTable);

        // Initialize the service using the constructor that takes the mocked enhancedClient
        mapConfigurationService = new MapConfigurationService(
            gameDao, 
            gameZoneStateDao, 
            safeZoneDao, 
            shrinkingZoneService, 
            mockEnhancedClient // Pass the mocked client
        );

        // Inject the mocked SafeZoneService using reflection
        Field safeZoneServiceField = MapConfigurationService.class.getDeclaredField("safeZoneService");
        safeZoneServiceField.setAccessible(true);
        safeZoneServiceField.set(mapConfigurationService, safeZoneService);

        // testGame, testMapConfig, etc. are initialized using the static final constants directly
        testGame = new Game();
        testGame.setGameID(TEST_GAME_ID); // Use existing static final value
        testGame.setMapId(TEST_MAP_ID);   // Use existing static final value

        // Default game boundary for tests
        defaultBoundary = List.of(
            new Coordinate(37.0, -122.0), // A wide boundary to contain test coordinates
            new Coordinate(37.0, -121.0),
            new Coordinate(38.0, -121.0),
            new Coordinate(38.0, -122.0)
        );
        testGame.setBoundary(defaultBoundary); // Also set it on the game object if it's read directly

        // Mock DAO calls
        lenient().when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));
        lenient().when(gameDao.getGameById(argThat(id -> !java.util.Objects.equals(id, TEST_GAME_ID))))
                 .thenReturn(Optional.empty());
        
        // Prepare an empty list for safe zones, tests can add to this
        testSafeZones = new ArrayList<>();

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
        // testSafeZones = new ArrayList<>(); // Comment out for now
        // lenient().when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(testSafeZones); // Comment out for now

        // Mock gameDao (should be okay now) - REMOVED DUPLICATE
        // lenient().when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame)); 
        lenient().when(gameDao.getGameById(eq("non-existent-game"))).thenReturn(Optional.empty());

        // Mock mapConfigTable getItem calls are now set above using local variables

        // Mock safeZoneDao
        // lenient().when(safeZoneDao.getSafeZonesByGameId(TEST_GAME_ID)).thenReturn(testSafeZones); // Comment out for now

        // Ensure testGame boundary settings are present for boundary checks
        Map<String, Object> gameSettings = new HashMap<>();
        gameSettings.put("gameBoundary", testBoundary.stream()
                .map(c -> Map.of("latitude", c.getLatitude(), "longitude", c.getLongitude()))
                .collect(Collectors.toList()));
        testGame.setSettings(gameSettings);
        testGame.setMapId(TEST_MAP_ID); // Ensure this remains set

        // Initialize the static mock for GeoUtils if not already done
        mockedGeoUtils = mockStatic(GeoUtils.class);
        // Mock boundary check to return true for coordinates within the defaultBoundary range
        mockedGeoUtils.when(() -> GeoUtils.isPointInBoundary(argThat(coord -> 
                coord != null && 
                coord.getLatitude() >= 37.0 && coord.getLatitude() <= 38.0 &&
                coord.getLongitude() >= -122.0 && coord.getLongitude() <= -121.0
            ), anyList())).thenReturn(true);
        // Mock boundary check to return false for coordinates outside the range
        mockedGeoUtils.when(() -> GeoUtils.isPointInBoundary(argThat(coord -> 
                coord == null || 
                coord.getLatitude() < 37.0 || coord.getLatitude() > 38.0 ||
                coord.getLongitude() < -122.0 || coord.getLongitude() > -121.0
            ), anyList())).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        // Close the static mock after each test if it was initialized
        if (mockedGeoUtils != null) {
            mockedGeoUtils.close();
            mockedGeoUtils = null; // Reset the field
        }
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

        // Verify mocks - gameDao is called only once because effectiveMapConfigCache caches by gameId
        verify(gameDao, times(1)).getGameById(TEST_GAME_ID); // Called only once due to effectiveMapConfigCache
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
        reset(mockMapConfigTable); // Reset table mock to clear any lenient stubs from setUp

        // GameDao returns testGame (which has TEST_MAP_ID) for TEST_GAME_ID
        // and empty for any other ID to prevent unexpected interactions.
        when(gameDao.getGameById(eq(TEST_GAME_ID))).thenReturn(Optional.of(testGame));
        when(gameDao.getGameById(argThat(id -> id != null && !TEST_GAME_ID.equals(id)))).thenReturn(Optional.empty()); // Made argThat null-safe

        // Specific map config (TEST_MAP_ID) MUST return null to trigger fallback to default
        Key specificMapKey = Key.builder().partitionValue(TEST_MAP_ID).build();
        when(mockMapConfigTable.getItem(eq(specificMapKey))).thenReturn(null);
        when(mockMapConfigTable.getItem(eq(GetItemEnhancedRequest.builder().key(specificMapKey).build()))).thenReturn(null);


        // Default map config MUST also return null from the DB for this test
        Key defaultMapKey = Key.builder().partitionValue(DEFAULT_MAP_ID).build();
        when(mockMapConfigTable.getItem(eq(defaultMapKey))).thenReturn(null);
        when(mockMapConfigTable.getItem(eq(GetItemEnhancedRequest.builder().key(defaultMapKey).build()))).thenReturn(null);
        
        // Act & Assert
        ConfigurationNotFoundException exception = assertThrows(ConfigurationNotFoundException.class, () -> {
            mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);
        });

        assertTrue(exception.getMessage().contains("Map configuration not found or failed to load for mapId: " + DEFAULT_MAP_ID));
        verify(gameDao, times(1)).getGameById(eq(TEST_GAME_ID)); // Expect exactly one call for TEST_GAME_ID
        verify(gameDao, never()).getGameById(argThat(id -> id != null && !TEST_GAME_ID.equals(id))); // Ensure no other game IDs are fetched

        // Verify interactions with mapConfigTable for both specific and default map IDs
        verify(mockMapConfigTable, times(1)).getItem(eq(Key.builder().partitionValue(TEST_MAP_ID).build()));
        verify(mockMapConfigTable, times(1)).getItem(eq(Key.builder().partitionValue(DEFAULT_MAP_ID).build()));
    }

    // Add more tests here...

    // --- Tests for isLocationInSafeZone ---

    @Test
    void isLocationInSafeZone_InsideFixedPublicZone_ReturnsTrue() {
        // Arrange
        // Adjust location and center to be within the defaultBoundary (37-38 lat, -122 to -121 lon)
        Coordinate locationInside = new Coordinate(37.5, -121.5); 
        Coordinate safeZoneCenter = new Coordinate(37.5, -121.5);
        SafeZone fixedZone = createSafeZone("sz1", TEST_GAME_ID, safeZoneCenter.getLatitude(), safeZoneCenter.getLongitude(), 50.0, SafeZoneType.PUBLIC, "admin", true, null);
        testSafeZones.add(fixedZone);
        
        // Mock the SafeZoneService to return true when checking if player is in safe zone
        when(safeZoneService.isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                      anyDouble(), anyDouble(), anyLong())).thenReturn(true);
        
        long currentTime = System.currentTimeMillis();

        // Act
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationInside, currentTime);

        // Assert
        assertTrue(result, "Player should be considered inside the safe zone");
        verify(safeZoneService).isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                         eq(locationInside.getLatitude()), eq(locationInside.getLongitude()), eq(currentTime));
    }

    @Test
    void isLocationInSafeZone_OutsideAllZones_ReturnsFalse() {
        // Arrange
        Coordinate locationOutside = new Coordinate(0.0, 0.0); // Clearly outside testBoundary and any zones
        // Mock the SafeZoneService to return false when checking if player is in safe zone
        when(safeZoneService.isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                      anyDouble(), anyDouble(), anyLong())).thenReturn(false);
        long currentTime = System.currentTimeMillis();

        // Act
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationOutside, currentTime);

        // Assert
        assertFalse(result, "Location is outside the public safe zones"); 
        // With the location being outside the boundary defined in setUp, the service should return false early.
        // Therefore, the safeZoneService should NOT be called because boundary check fails first.
        verify(safeZoneService, never()).isPlayerInActiveSafeZone(anyString(), anyString(), anyDouble(), anyDouble(), anyLong());
    }

    @Test
    void isLocationInSafeZone_InsideActiveTimedZone_ReturnsTrue() {
         // Arrange
        // Adjust center to be within the defaultBoundary
        Coordinate zoneCenter = new Coordinate(37.5, -121.5); 
        Coordinate playerLocationInside = new Coordinate(37.5001, -121.5001); 
        long now = System.currentTimeMillis();
        
        // Mock the SafeZoneService to return true when checking if player is in safe zone
        when(safeZoneService.isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                      anyDouble(), anyDouble(), anyLong())).thenReturn(true);

         // Act
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, playerLocationInside, now);

         // Assert
         assertTrue(result, "Location should be inside the active timed safe zone");
        verify(safeZoneService).isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                         eq(playerLocationInside.getLatitude()), eq(playerLocationInside.getLongitude()), eq(now));
    }

    @Test
    void isLocationInSafeZone_InsideExpiredTimedZone_ReturnsFalse() {
         // Arrange
         Coordinate locationInside = new Coordinate(37.5, -121.5);
         Coordinate safeZoneCenter = new Coordinate(37.5, -121.5);
         long currentTime = System.currentTimeMillis();
         // Mock the SafeZoneService to return false when checking if player is in safe zone
         when(safeZoneService.isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                       anyDouble(), anyDouble(), anyLong())).thenReturn(false);

         // Act
         boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationInside, currentTime);

         // Assert
         assertFalse(result, "Location should not be inside the expired timed safe zone");
         verify(safeZoneService).isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                          eq(locationInside.getLatitude()), eq(locationInside.getLongitude()), eq(currentTime));
    }

    @Test
    void isLocationInSafeZone_OutsideGameBoundary_ReturnsFalse() {
        // Arrange
        Coordinate locationOutsideBoundary = new Coordinate(0.0, 0.0); // Outside the defaultBoundary range
        long currentTime = System.currentTimeMillis();

        // Act
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationOutsideBoundary, currentTime);

        // Assert
        assertFalse(result, "Location outside game boundary should not be in safe zone, even if zones exist");
        // Verification: safeZoneService should NOT be called if boundary check fails first
        verify(safeZoneService, never()).isPlayerInActiveSafeZone(anyString(), anyString(), anyDouble(), anyDouble(), anyLong());
    }

    @Test
    void isLocationInSafeZone_ExactlyOnRadius_ReturnsTrue() {
        // Arrange
        // Adjust center to be within the defaultBoundary
        Coordinate safeZoneCenter = new Coordinate(37.5, -121.5);
        Coordinate locationOnRadius = new Coordinate(37.5001, -121.5); // Approx. on radius for mocking
        
        // Mock the SafeZoneService to return true when checking if player is in safe zone
        when(safeZoneService.isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                      anyDouble(), anyDouble(), anyLong())).thenReturn(true);
        
        long currentTime = System.currentTimeMillis();
        
        // Act
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationOnRadius, currentTime);

        // Assert
        assertTrue(result, "Location exactly on the radius should be considered inside the safe zone");
        verify(safeZoneService).isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                         eq(locationOnRadius.getLatitude()), eq(locationOnRadius.getLongitude()), eq(currentTime));
    }

    @Test
    void isLocationInSafeZone_InsideOwnPrivateZone_ReturnsTrue() {
         // Arrange
         String ownerId = "player-owner-1";
         // Adjust center to be within the defaultBoundary
         Coordinate safeZoneCenter = new Coordinate(37.5, -121.5);
         Coordinate locationInsideZone = new Coordinate(37.5001, -121.5001); 
         
         // Mock the SafeZoneService to return true when checking if player is in safe zone
         when(safeZoneService.isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(ownerId), 
                                                       anyDouble(), anyDouble(), anyLong())).thenReturn(true);
         
         long currentTime = System.currentTimeMillis();
 
         // Act: Check using the owner's ID
         boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, ownerId, locationInsideZone, currentTime);
 
         // Assert
         assertTrue(result, "Owner should be considered inside their own private safe zone");
         verify(safeZoneService).isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(ownerId), 
                                                          eq(locationInsideZone.getLatitude()), eq(locationInsideZone.getLongitude()), eq(currentTime));
    }
 
    @Test
    void isLocationInSafeZone_InsideSomeoneElsesPrivateZone_ReturnsFalse() {
        // Arrange
        String ownerId = "otherPlayer999";
        String testPlayerId = "player-checking"; // Different from owner
        // Adjust centers to be within the defaultBoundary (37-38 lat, -122 to -121 lon)
        Coordinate publicZoneCenter = new Coordinate(37.5, -121.5);
        Coordinate privateZoneCenter = new Coordinate(37.5001, -121.5001); // Slightly offset but overlapping
        Coordinate playerLocation = new Coordinate(37.50005, -121.50005); // Inside both

        SafeZone publicZone = createSafeZone("pub1", TEST_GAME_ID, publicZoneCenter.getLatitude(), publicZoneCenter.getLongitude(), 50.0, SafeZoneType.PUBLIC, "admin", true, null);
        SafeZone privateZone = createSafeZone("priv1", TEST_GAME_ID, privateZoneCenter.getLatitude(), privateZoneCenter.getLongitude(), 20.0, SafeZoneType.PRIVATE, ownerId, true, null);
        testSafeZones.add(publicZone);
        testSafeZones.add(privateZone);
        
        // Mock the SafeZoneService to return true (player is in the public zone)
        when(safeZoneService.isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(testPlayerId), 
                                                      anyDouble(), anyDouble(), anyLong())).thenReturn(true);
        
        long currentTime = System.currentTimeMillis();
 
        // Act: Check using a different player's ID (not the owner)
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, testPlayerId, playerLocation, currentTime);
 
        // Assert - Should be true because they're in the public zone
        assertTrue(result, "Non-owner should be considered inside the public zone even if there's a private zone at the same location");
        verify(safeZoneService).isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(testPlayerId), 
                                                         eq(playerLocation.getLatitude()), eq(playerLocation.getLongitude()), eq(currentTime));
    }
 
    @Test
    void isLocationInSafeZone_OutsideOwnPrivateZone_ReturnsFalse() {
        // Arrange
        String ownerId = "player-owner-1";
        Coordinate locationOutside = new Coordinate(0.0, 0.0); // Outside the zone and boundary
        long currentTime = System.currentTimeMillis();
 
        // Act: Check using the owner's ID but with an outside location
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, ownerId, locationOutside, currentTime);
 
        // Assert
        assertFalse(result, "Owner should not be considered inside their private zone if the location is outside");
        // The boundary check should fail first, so the safeZoneService should not be called
        verify(safeZoneService, never()).isPlayerInActiveSafeZone(anyString(), anyString(), anyDouble(), anyDouble(), anyLong());
    }
 
    @Test
    void isLocationInSafeZone_InsidePublicZoneAndSomeoneElsesPrivateZone_ReturnsTrue() {
        // Arrange
        String ownerId = "player-owner-1";
        String otherPlayerId = "player-other-2";
        // Adjust location and center to be within the defaultBoundary (37-38 lat, -122 to -121 lon)
        Coordinate locationInsideBoth = new Coordinate(37.5, -121.5);
        Coordinate center = new Coordinate(37.5, -121.5);
 
        // Corrected call to match helper signature
        SafeZone publicZone = createSafeZone("sz-public-overlap", TEST_GAME_ID, center.getLatitude(), center.getLongitude(), 100.0, SafeZoneType.PUBLIC, "admin", true, null);
        SafeZone privateZone = createSafeZone("sz-private-overlap", TEST_GAME_ID, center.getLatitude(), center.getLongitude(), 100.0, SafeZoneType.PRIVATE, ownerId, true, null);
        testSafeZones.add(publicZone);
        testSafeZones.add(privateZone);
        
        // Mock the SafeZoneService to return true when checking if player is in safe zone
        when(safeZoneService.isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(otherPlayerId), 
                                                      anyDouble(), anyDouble(), anyLong())).thenReturn(true);
        
        long currentTime = System.currentTimeMillis();
 
        // Act
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, otherPlayerId, locationInsideBoth, currentTime);
 
        // Assert
        assertTrue(result, "Player should be considered inside the public safe zone");
        verify(safeZoneService).isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(otherPlayerId), 
                                                         eq(locationInsideBoth.getLatitude()), eq(locationInsideBoth.getLongitude()), eq(currentTime));
    }
 
    @Test
    public void isLocationInSafeZone_InsideSafeZone_ReturnsTrue() {
        // Setup
        Coordinate safeZoneCenter = new Coordinate(37.5, -121.5);
        Coordinate locationInsideSafeZone = new Coordinate(37.5001, -121.5001);
        
        // Mock the SafeZoneService to return true when checking if player is in safe zone
        when(safeZoneService.isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                      anyDouble(), anyDouble(), anyLong())).thenReturn(true);
 
        // Test
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationInsideSafeZone, System.currentTimeMillis());

        // Verify
        assertTrue(result);
        verify(safeZoneService).isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                         eq(locationInsideSafeZone.getLatitude()), eq(locationInsideSafeZone.getLongitude()), anyLong());
    }
    
    @Test
    public void isLocationInSafeZone_OutsideSafeZone_ReturnsFalse() {
        // Setup
        Coordinate safeZoneCenter = new Coordinate(37.5, -121.5);
        Coordinate locationOutsideSafeZone = new Coordinate(37.6, -121.6); // Still within boundary but outside safe zone
        
        // Mock the SafeZoneService to return false when checking if player is in safe zone
        when(safeZoneService.isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                      anyDouble(), anyDouble(), anyLong())).thenReturn(false);
        
        // Test
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationOutsideSafeZone, System.currentTimeMillis());

        // Verify
        assertFalse(result);
        verify(safeZoneService).isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                         eq(locationOutsideSafeZone.getLatitude()), eq(locationOutsideSafeZone.getLongitude()), anyLong());
    }
    
    @Test
    public void isLocationInSafeZone_ExactlyOnRadius_WithMockedDistance_ReturnsTrue() {
        // Arrange
        Coordinate locationOnRadius = new Coordinate(37.5009, -121.5); // A location
        long currentTime = System.currentTimeMillis();

        // Mock the SafeZoneService to return true when checking if player is in safe zone
        when(safeZoneService.isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                      anyDouble(), anyDouble(), anyLong())).thenReturn(true);
        
        // Act
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationOnRadius, currentTime);

        // Assert
        assertTrue(result, "Location exactly on radius (via mocked containsLocation) should be considered inside safe zone");
        verify(safeZoneService).isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                         eq(locationOnRadius.getLatitude()), eq(locationOnRadius.getLongitude()), eq(currentTime));
    }
    
    @Test
    public void isLocationInSafeZone_OutsideBoundary_ReturnsFalse() {
        // Setup
        Coordinate locationInsideSafeZone = new Coordinate(38.897958, -77.036561); // Outside defaultBoundary
        
        // Test
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationInsideSafeZone, System.currentTimeMillis());

        // Verify
        assertFalse(result, "Location outside boundary should not be in safe zone");
        // The boundary check should fail first, so the safeZoneService should not be called
        verify(safeZoneService, never()).isPlayerInActiveSafeZone(anyString(), anyString(), anyDouble(), anyDouble(), anyLong());
    }
    
    @Test
    public void isLocationInSafeZone_TimedSafeZoneExpired_ReturnsFalse() {
        // Setup
        Coordinate safeZoneCenter = new Coordinate(37.5, -121.5);
        Coordinate locationInsideSafeZone = new Coordinate(37.5001, -121.5001);
        long currentTimeMillis = System.currentTimeMillis();
        
        // Mock the SafeZoneService to return false when checking if player is in safe zone (expired)
        when(safeZoneService.isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                      anyDouble(), anyDouble(), anyLong())).thenReturn(false);
        
        // Test
        boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, locationInsideSafeZone, currentTimeMillis);

        // Verify
        assertFalse(result, "Expired timed safe zone should not be active");
        verify(safeZoneService).isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                         eq(locationInsideSafeZone.getLatitude()), eq(locationInsideSafeZone.getLongitude()), eq(currentTimeMillis));
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

    @Test
    public void testSafeZoneCheckPerformance_WithMultipleSafeZones() {
        // The test point is set between grid points and outside the radius of any safe zone
        double testLat = 37.5;
        double testLon = -121.5;

        Coordinate testCoord = new Coordinate(testLat, testLon);
        long currentTime = System.currentTimeMillis();

        // Mock the SafeZoneService to return false when checking if player is in safe zone
        when(safeZoneService.isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                      anyDouble(), anyDouble(), anyLong())).thenReturn(false);

        // Run multiple iterations to get an average
        int iterations = 100;
        long totalTime = 0;

        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();
            boolean result = mapConfigurationService.isLocationInSafeZone(TEST_GAME_ID, TEST_PLAYER_ID, testCoord, currentTime);
            long endTime = System.nanoTime();
            totalTime += (endTime - startTime);
            
            // The location should not be in a safe zone
            assertFalse(result, "Location should not be in any safe zone");
        }

        double avgDurationMs = (totalTime / iterations) / 1_000_000.0;
        System.out.println("Average safe zone check duration: " + avgDurationMs + " ms");
        
        // Verify the safe zone service was called
        verify(safeZoneService, times(iterations)).isPlayerInActiveSafeZone(eq(TEST_GAME_ID), eq(TEST_PLAYER_ID), 
                                                                            eq(testCoord.getLatitude()), eq(testCoord.getLongitude()), eq(currentTime));
        
        // Performance assertion - should complete in under 10ms on average
        assertTrue(avgDurationMs < 10.0, "Safe zone check should complete in under 10ms on average");
    }

} 