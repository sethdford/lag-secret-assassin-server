package com.assassin.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail; // Includes verify, never, times, lenient, reset
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

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

    @InjectMocks
    private MapConfigurationService mapConfigurationService;

    private static final String TEST_GAME_ID = "test-game-1";
    private static final String TEST_MAP_ID = "test-map-config-1";
    private static final String DEFAULT_MAP_ID = "default_map"; // Match the default used in service

    private Game testGame;
    private MapConfiguration testMapConfig;
    private MapConfiguration defaultMapConfig;
    private List<Coordinate> testBoundary;

    @BeforeEach
    void setUp() {
        // Create a sample valid boundary
        testBoundary = new ArrayList<>();
        testBoundary.add(new Coordinate(10.0, 10.0));
        testBoundary.add(new Coordinate(10.1, 10.0));
        testBoundary.add(new Coordinate(10.1, 10.1));
        testBoundary.add(new Coordinate(10.0, 10.1));

        // Create a test game
        testGame = new Game();
        testGame.setGameID(TEST_GAME_ID);
        testGame.setMapId(TEST_MAP_ID); // Game uses the specific map

        // Create a specific map configuration
        testMapConfig = new MapConfiguration();
        testMapConfig.setMapId(TEST_MAP_ID);
        testMapConfig.setMapName("Test Map");
        testMapConfig.setGameBoundary(testBoundary);
        testMapConfig.setShrinkingZoneEnabled(false);
        testMapConfig.setEliminationDistanceMeters(10.0);
        // Add other necessary defaults

        // Create a default map configuration
        defaultMapConfig = new MapConfiguration();
        defaultMapConfig.setMapId(DEFAULT_MAP_ID);
        defaultMapConfig.setMapName("Default Fallback Map");
        defaultMapConfig.setGameBoundary(testBoundary); // Default can also have a boundary
        defaultMapConfig.setShrinkingZoneEnabled(false);
        defaultMapConfig.setEliminationDistanceMeters(15.0); // Different value for distinction

        // --- Mock Setup ---
        // Mock gameDao to return the test game
        lenient().when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));

        // Mock mapConfigTable interactions - will be refined in specific tests
        // Example: Mock finding the specific config
        Key testMapKey = Key.builder().partitionValue(TEST_MAP_ID).build();
        lenient().when(mockMapConfigTable.getItem(eq(testMapKey))).thenReturn(testMapConfig);

        // Example: Mock finding the default config
        Key defaultMapKey = Key.builder().partitionValue(DEFAULT_MAP_ID).build();
        lenient().when(mockMapConfigTable.getItem(eq(defaultMapKey))).thenReturn(defaultMapConfig);

        // Need to manually inject the mock table because @InjectMocks doesn't handle generics well
        // This requires reflection or a manual constructor/setter. Let's try reflection for now.
        try {
            java.lang.reflect.Field tableField = MapConfigurationService.class.getDeclaredField("mapConfigTable");
            tableField.setAccessible(true);
            tableField.set(mapConfigurationService, mockMapConfigTable);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to inject mock mapConfigTable into MapConfigurationService via reflection", e);
        }
    }

    // --- Tests for getEffectiveMapConfiguration ---

    @Test
    void getEffectiveMapConfiguration_GameFound_MapIdPresent_ConfigFound_ReturnsSpecificConfig() {
        // Arrange (mocks already set up in @BeforeEach)
        // GameDao returns testGame with TEST_MAP_ID
        // mockMapConfigTable returns testMapConfig for TEST_MAP_ID

        // Act
        MapConfiguration result = mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_MAP_ID, result.getMapId(), "Should return the specific map config");
        assertEquals(testMapConfig, result);

        // Verify mocks
        verify(gameDao).getGameById(TEST_GAME_ID);
        // Verify table was called for the specific map ID (cache miss on first call)
        Key testMapKey = Key.builder().partitionValue(TEST_MAP_ID).build();
        verify(mockMapConfigTable).getItem(eq(testMapKey));
        // Verify default config was NOT fetched
        Key defaultMapKey = Key.builder().partitionValue(DEFAULT_MAP_ID).build();
        verify(mockMapConfigTable, never()).getItem(eq(defaultMapKey));
    }

    @Test
    void getEffectiveMapConfiguration_CacheHit_ReturnsCachedConfigWithoutDbCall() {
        // Arrange: Call once to populate the cache
        mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);
        // Reset interaction tracking for the mock table after the first call
        reset(mockMapConfigTable);
        // GameDao mock is still active from @BeforeEach

        // Act: Call the method again
        MapConfiguration result = mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_MAP_ID, result.getMapId(), "Should return the specific map config from cache");
        assertEquals(testMapConfig, result);

        // Verify mocks
        verify(gameDao, times(2)).getGameById(TEST_GAME_ID); // GameDao is called each time
        // Verify mapConfigTable was NOT called this time
        verify(mockMapConfigTable, never()).getItem(any(Key.class));
    }

    @Test
    void getEffectiveMapConfiguration_SpecificConfigNotFound_ReturnsDefaultConfig() {
        // Arrange:
        // GameDao returns testGame with TEST_MAP_ID
        // Make the specific map config return null from the DB
        Key testMapKey = Key.builder().partitionValue(TEST_MAP_ID).build();
        when(mockMapConfigTable.getItem(eq(testMapKey))).thenReturn(null);
        // Default config mock is still active from @BeforeEach

        // Act
        MapConfiguration result = mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);

        // Assert
        assertNotNull(result);
        assertEquals(DEFAULT_MAP_ID, result.getMapId(), "Should return the default map config");
        assertEquals(defaultMapConfig, result);

        // Verify mocks
        verify(gameDao).getGameById(TEST_GAME_ID);
        // Verify table was called for the specific map ID (which returned null)
        verify(mockMapConfigTable).getItem(eq(testMapKey));
        // Verify table was ALSO called for the default map ID (cache miss for default)
        Key defaultMapKey = Key.builder().partitionValue(DEFAULT_MAP_ID).build();
        verify(mockMapConfigTable).getItem(eq(defaultMapKey));
    }

    @Test
    void getEffectiveMapConfiguration_GameHasNullMapId_ReturnsDefaultConfig() {
        // Arrange:
        testGame.setMapId(null); // Set mapId to null
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));
        // Default config mock is still active from @BeforeEach

        // Act
        MapConfiguration result = mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);

        // Assert
        assertNotNull(result);
        assertEquals(DEFAULT_MAP_ID, result.getMapId(), "Should return the default map config");
        assertEquals(defaultMapConfig, result);

        // Verify mocks
        verify(gameDao).getGameById(TEST_GAME_ID);
        // Verify table was NOT called for the specific (null) map ID
        verify(mockMapConfigTable, never()).getItem(eq(Key.builder().partitionValue(TEST_MAP_ID).build()));
        // Verify table was called for the default map ID
        Key defaultMapKey = Key.builder().partitionValue(DEFAULT_MAP_ID).build();
        verify(mockMapConfigTable).getItem(eq(defaultMapKey));
    }

    @Test
    void getEffectiveMapConfiguration_GameHasEmptyMapId_ReturnsDefaultConfig() {
        // Arrange:
        testGame.setMapId(""); // Set mapId to empty string
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGame));
        // Default config mock is still active from @BeforeEach

        // Act
        MapConfiguration result = mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);

        // Assert
        assertNotNull(result);
        assertEquals(DEFAULT_MAP_ID, result.getMapId(), "Should return the default map config");
        assertEquals(defaultMapConfig, result);

        // Verify mocks
        verify(gameDao).getGameById(TEST_GAME_ID);
        // Verify table was NOT called for the specific (empty) map ID
        verify(mockMapConfigTable, never()).getItem(eq(Key.builder().partitionValue(TEST_MAP_ID).build()));
        verify(mockMapConfigTable, never()).getItem(eq(Key.builder().partitionValue("").build()));
        // Verify table was called for the default map ID
        Key defaultMapKey = Key.builder().partitionValue(DEFAULT_MAP_ID).build();
        verify(mockMapConfigTable).getItem(eq(defaultMapKey));
    }

    @Test
    void getEffectiveMapConfiguration_GameNotFound_ReturnsDefaultConfig() {
        // Arrange:
        when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.empty());
        // Default config mock is still active from @BeforeEach

        // Act
        MapConfiguration result = mapConfigurationService.getEffectiveMapConfiguration(TEST_GAME_ID);

        // Assert
        assertNotNull(result);
        assertEquals(DEFAULT_MAP_ID, result.getMapId(), "Should return the default map config when game not found");
        assertEquals(defaultMapConfig, result);

        // Verify mocks
        verify(gameDao).getGameById(TEST_GAME_ID);
        // Verify table was called for the default map ID
        Key defaultMapKey = Key.builder().partitionValue(DEFAULT_MAP_ID).build();
        verify(mockMapConfigTable).getItem(eq(defaultMapKey));
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

} 