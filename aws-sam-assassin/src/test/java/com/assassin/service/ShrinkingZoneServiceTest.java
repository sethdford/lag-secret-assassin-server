package com.assassin.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import org.mockito.Mockito;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.assassin.config.MapConfiguration;
import com.assassin.config.ZonePhase;
import com.assassin.dao.GameDao;
import com.assassin.dao.GameZoneStateDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GameStateException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.GameStatus;
import com.assassin.model.GameZoneState;
import com.assassin.model.ShrinkingZoneStage;
import com.assassin.util.GeoUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // Apply lenient strictness globally
class ShrinkingZoneServiceTest {

    @Mock
    private GameDao gameDao;
    @Mock
    private GameZoneStateDao gameZoneStateDao;
    @Mock
    private PlayerDao playerDao;
    @Mock
    private MapConfigurationService mapConfigService;
    @Mock
    private GeoUtils geoUtils;
    @Mock
    private Clock mockClock;

    private ShrinkingZoneService shrinkingZoneService;

    private Game testGame;
    private List<ShrinkingZoneStage> testConfig;
    private String gameId = "sz-test-game-1";
    private Coordinate boundaryCentroid = new Coordinate(10.0, 10.0);
    private MapConfiguration testMapConfig;
    private GameZoneState testZoneState;
    private GameZoneState initialZoneState;

    private static final String TEST_GAME_ID = "test-game-123";
    private static final String TEST_MAP_ID = "test-map-config-001";
    private static final Coordinate INITIAL_CENTER = new Coordinate(40.7128, -74.0060);

    @BeforeEach
    void setUp() {
        testGame = new Game();
        testGame.setGameID(gameId);
        testGame.setStatus("ACTIVE");
        testGame.setShrinkingZoneEnabled(true);
        
        List<Coordinate> boundary = List.of(
            new Coordinate(9.9, 9.9),  
            new Coordinate(10.1, 9.9),
            new Coordinate(10.1, 10.1),
            new Coordinate(9.9, 10.1)
        );
        testGame.setBoundary(boundary);

        testConfig = new ArrayList<>();
        ShrinkingZoneStage stage0 = new ShrinkingZoneStage();
        stage0.setStageIndex(0);
        stage0.setWaitTimeSeconds(60);
        stage0.setTransitionTimeSeconds(120);
        stage0.setEndRadiusMeters(500.0);
        testConfig.add(stage0);
        
        ShrinkingZoneStage stage1 = new ShrinkingZoneStage();
        stage1.setStageIndex(1);
        stage1.setWaitTimeSeconds(30);
        stage1.setTransitionTimeSeconds(60);
        stage1.setEndRadiusMeters(100.0);
        testConfig.add(stage1);
        
        ShrinkingZoneStage stage2 = new ShrinkingZoneStage();
        stage2.setStageIndex(2);
        stage2.setWaitTimeSeconds(15);
        stage2.setTransitionTimeSeconds(0);
        stage2.setEndRadiusMeters(0.0);
        testConfig.add(stage2);

        testGame.setSettings(Map.of("shrinkingZoneConfig", testConfig));

        shrinkingZoneService = Mockito.spy(new ShrinkingZoneService(gameDao, gameZoneStateDao, playerDao));
        
        // Set up common mocks with lenient()
        lenient().when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        testMapConfig = new MapConfiguration();
        testMapConfig.setMapId("defaultMap");
        testMapConfig.setInitialZoneRadiusMeters(5000.0);
        testMapConfig.setShrinkingZoneEnabled(true);
        testMapConfig.setZoneDamagePerSecond(1.0);

        List<ZonePhase> zonePhases = new ArrayList<>();
        zonePhases.add(new ZonePhase(1, 60, 120, 1000.0, 1.0));
        zonePhases.add(new ZonePhase(2, 30, 60, 500.0, 2.0));
        zonePhases.add(new ZonePhase(3, 15, 0, 0.0, 5.0));
        testMapConfig.setZonePhases(zonePhases);

        testZoneState = new GameZoneState();
        testZoneState.setGameId("game123");
        testZoneState.setCurrentStageIndex(1);
        testZoneState.setCurrentPhase(GameZoneState.ZonePhase.WAITING.name());
        testZoneState.setCurrentCenter(new Coordinate(5, 5));
        testZoneState.setCurrentRadiusMeters(5000.0);
        testZoneState.setPhaseEndTime(Instant.now().plusSeconds(120).toString());
        testZoneState.setLastUpdated(Instant.now().toString());

        lenient().when(gameZoneStateDao.getGameZoneState(gameId)).thenReturn(Optional.of(testZoneState));

        lenient().when(mapConfigService.getEffectiveMapConfiguration(any(String.class))).thenReturn(testMapConfig);
        lenient().when(mapConfigService.getGameBoundaryCenter(any(String.class))).thenReturn(boundaryCentroid);

        // Create a test Game for the new MapConfiguration/ZonePhase tests
        Game testGameForZoneTest = new Game();
        testGameForZoneTest.setGameID(TEST_GAME_ID);
        testGameForZoneTest.setMapId(TEST_MAP_ID);
        testGameForZoneTest.setStatus(GameStatus.ACTIVE.name());
        testGameForZoneTest.setStartTimeEpochMillis(Instant.now().toEpochMilli() - 5 * 60 * 1000);
        testGameForZoneTest.setShrinkingZoneEnabled(true);

        // Create initial GameZoneState based on MapConfig
        initialZoneState = new GameZoneState();
        initialZoneState.setGameId(TEST_GAME_ID);
        initialZoneState.setCurrentStageIndex(0);
        initialZoneState.setCurrentRadiusMeters(testMapConfig.getInitialZoneRadiusMeters());
        initialZoneState.setNextRadiusMeters(testMapConfig.getInitialZoneRadiusMeters());
        initialZoneState.setTargetCenterLatitude(INITIAL_CENTER.getLatitude());
        initialZoneState.setTargetCenterLongitude(INITIAL_CENTER.getLongitude());
        initialZoneState.setStageStartTimeEpochMillis(testGameForZoneTest.getStartTimeEpochMillis());
        long phase1WaitTimeMillis = zonePhases.get(0).getWaitTimeSeconds() * 1000L;
        initialZoneState.setNextShrinkTimeEpochMillis(testGameForZoneTest.getStartTimeEpochMillis() + phase1WaitTimeMillis);
        initialZoneState.setLastUpdateTimeEpochMillis(testGameForZoneTest.getStartTimeEpochMillis());

        lenient().when(gameDao.getGameById(TEST_GAME_ID)).thenReturn(Optional.of(testGameForZoneTest));
        lenient().when(gameZoneStateDao.getGameZoneState(TEST_GAME_ID)).thenReturn(Optional.of(initialZoneState));
    }

    @Test
    void initializeZoneState_ShouldCreateInitialWaitingState() throws GameStateException {
        // Arrange
        ArgumentCaptor<GameZoneState> captor = ArgumentCaptor.forClass(GameZoneState.class);
        when(gameZoneStateDao.getGameZoneState(gameId)).thenReturn(Optional.empty()); // Simulate no existing state

        // Act
        shrinkingZoneService.initializeZoneState(testGame);

        // Assert
        verify(gameZoneStateDao).saveGameZoneState(captor.capture());
        GameZoneState savedState = captor.getValue();

        assertNotNull(savedState);
        assertEquals(gameId, savedState.getGameId());
        assertEquals(0, savedState.getCurrentStageIndex());
        assertEquals(GameZoneState.ZonePhase.WAITING, savedState.getCurrentPhaseAsEnum());
        assertEquals(testConfig.get(0).getEndRadiusMeters(), savedState.getCurrentRadiusMeters());
        assertEquals(boundaryCentroid.getLatitude(), savedState.getCurrentCenter().getLatitude(), 0.0001);
        assertEquals(boundaryCentroid.getLongitude(), savedState.getCurrentCenter().getLongitude(), 0.0001);

        Instant expectedEndTime = Instant.now().plusSeconds(testConfig.get(0).getWaitTimeSeconds());
        Instant actualEndTime = Instant.parse(savedState.getPhaseEndTime());
        assertTrue(Duration.between(expectedEndTime, actualEndTime).abs().toSeconds() < 1);
    }
    
    @Test
    void initializeZoneState_WhenNoConfig_ShouldThrowException() {
        // Arrange
        String testGameId = "sz-test-game-no-config";
        Game testGameNoConfig = new Game();
        testGameNoConfig.setGameID(testGameId);
        testGameNoConfig.setShrinkingZoneEnabled(true); // Need to enable it to get past the first check
        testGameNoConfig.setSettings(Map.of()); // No config
        
        // Reset mock specifically for this test if needed, though Lenient should handle it.
        // Mockito.reset(gameDao); 
        
        when(gameDao.getGameById(testGameId)).thenReturn(Optional.of(testGameNoConfig));
        when(gameZoneStateDao.getGameZoneState(testGameId)).thenReturn(Optional.empty());
        // Mock boundary center - shouldn't be reached, but for completeness
        lenient().when(mapConfigService.getGameBoundaryCenter(testGameId)).thenReturn(new Coordinate(0.0, 0.0)); 

        // Act & Assert
        assertThrows(GameStateException.class, () -> {
            shrinkingZoneService.initializeZoneState(testGameNoConfig);
        }, "Should throw GameStateException when map configuration is missing");

        verify(gameZoneStateDao, never()).saveGameZoneState(any());
    }
    
    // --- Tests for advanceZoneState --- 

    @Test
    void advanceZoneState_WhenInWaitingPhaseBeforeEnd_ShouldReturnCurrentState() throws Exception {
        // Arrange: State is WAITING, time is before phase end
        GameZoneState currentState = new GameZoneState();
        currentState.setGameId(gameId);
        currentState.setCurrentStageIndex(0);
        currentState.setCurrentPhase(GameZoneState.ZonePhase.WAITING.name());
        currentState.setCurrentRadiusMeters(500.0);
        currentState.setCurrentCenter(boundaryCentroid);
        currentState.setPhaseEndTime(Instant.now().plusSeconds(30).toString()); // Ends in 30s
        when(gameZoneStateDao.getGameZoneState(gameId)).thenReturn(Optional.of(currentState));

        // Act
        Optional<GameZoneState> nextStateOpt = shrinkingZoneService.advanceZoneState(gameId);

        // Assert
        assertTrue(nextStateOpt.isPresent());
        assertEquals(currentState, nextStateOpt.get()); // Should return the same state object
        verify(gameZoneStateDao, never()).saveGameZoneState(any(GameZoneState.class)); // No save needed
    }
    
    @Test
    void advanceZoneState_WhenWaitingPhaseEnds_ShouldTransitionToShrinking() throws Exception {
        // Arrange: State is WAITING, time is *after* phase end
        Instant phaseEndTime = Instant.now().minusSeconds(1); // Ended 1 second ago
        GameZoneState currentState = new GameZoneState();
        currentState.setGameId(gameId);
        currentState.setCurrentStageIndex(0);
        currentState.setCurrentPhase(GameZoneState.ZonePhase.WAITING.name());
        currentState.setCurrentRadiusMeters(500.0);
        currentState.setCurrentCenter(boundaryCentroid);
        currentState.setPhaseEndTime(phaseEndTime.toString()); 
        when(gameZoneStateDao.getGameZoneState(gameId)).thenReturn(Optional.of(currentState));
        ArgumentCaptor<GameZoneState> captor = ArgumentCaptor.forClass(GameZoneState.class);

        // Act
        Optional<GameZoneState> nextStateOpt = shrinkingZoneService.advanceZoneState(gameId);

        // Assert
        assertTrue(nextStateOpt.isPresent());
        verify(gameZoneStateDao).saveGameZoneState(captor.capture());
        GameZoneState savedState = captor.getValue();
        
        assertEquals(0, savedState.getCurrentStageIndex());
        assertEquals(GameZoneState.ZonePhase.SHRINKING, savedState.getCurrentPhaseAsEnum());
        assertEquals(500.0, savedState.getCurrentRadiusMeters()); // Starts shrinking from current radius
        assertEquals(boundaryCentroid, savedState.getCurrentCenter());
        // End time should be now + transition time
        Instant expectedEndTime = Instant.now().plusSeconds(testConfig.get(0).getTransitionTimeSeconds());
        Instant actualEndTime = Instant.parse(savedState.getPhaseEndTime());
        assertTrue(Duration.between(expectedEndTime, actualEndTime).abs().toSeconds() < 1);
    }
    
    @Test
    void advanceZoneState_WhenAlreadyFinished_ShouldDoNothing() throws Exception {
        // Arrange: State is already FINISHED
        Instant phaseEndTime = Instant.now().minusSeconds(60); 
        GameZoneState currentState = new GameZoneState();
        currentState.setGameId(gameId);
        currentState.setCurrentStageIndex(2); 
        currentState.setCurrentPhase(GameZoneState.ZonePhase.FINISHED.name());
        currentState.setCurrentRadiusMeters(0.0);
        currentState.setCurrentCenter(boundaryCentroid);
        currentState.setPhaseEndTime(phaseEndTime.toString()); 
        when(gameZoneStateDao.getGameZoneState(gameId)).thenReturn(Optional.of(currentState));
        
        // Act
        Optional<GameZoneState> nextStateOpt = shrinkingZoneService.advanceZoneState(gameId);

        // Assert
        assertTrue(nextStateOpt.isPresent());
        assertEquals(currentState, nextStateOpt.get());
        verify(gameZoneStateDao, never()).saveGameZoneState(any(GameZoneState.class));
    }

    @Test
    void testIsShrinkingZoneEnabled_GameNotFound() {
        when(gameDao.getGameById("unknownGame")).thenReturn(Optional.empty());
        assertThrows(GameNotFoundException.class, () -> shrinkingZoneService.isShrinkingZoneEnabled("unknownGame"));
    }
    
    @Test
    void testIsShrinkingZoneEnabled_EnabledTrue() {
        Game localTestGame = new Game();
        localTestGame.setGameID("test-enabled-true");
        localTestGame.setShrinkingZoneEnabled(true);
        
        when(gameDao.getGameById("test-enabled-true")).thenReturn(Optional.of(localTestGame));
        assertTrue(shrinkingZoneService.isShrinkingZoneEnabled("test-enabled-true"), "Should return true when game setting is true");
    }
    
    @Test
    void testIsShrinkingZoneEnabled_EnabledFalse() {
        Game localTestGame = new Game();
        localTestGame.setGameID("test-enabled-false");
        localTestGame.setShrinkingZoneEnabled(false);
        
        when(gameDao.getGameById("test-enabled-false")).thenReturn(Optional.of(localTestGame));
        assertFalse(shrinkingZoneService.isShrinkingZoneEnabled("test-enabled-false"), "Should return false when game setting is false");
    }
    
    @Test
    void testIsShrinkingZoneEnabled_SettingNotPresent() {
        Game localTestGame = new Game();
        localTestGame.setGameID("test-enabled-null");
        localTestGame.setShrinkingZoneEnabled(null);
        
        when(gameDao.getGameById("test-enabled-null")).thenReturn(Optional.of(localTestGame));
        assertFalse(shrinkingZoneService.isShrinkingZoneEnabled("test-enabled-null"), "Should return false when game setting is not present (null)");
    }

    @Test
    void initializeZoneState_WhenAlreadyExists_ShouldDoNothing() {
        // Arrange
        String localGameId = "test-already-exists";
        Game game = new Game();
        game.setGameID(localGameId);
        
        GameZoneState existingState = new GameZoneState();
        existingState.setGameId(localGameId);
        
        when(gameDao.getGameById(localGameId)).thenReturn(Optional.of(game));
        when(gameZoneStateDao.getGameZoneState(localGameId)).thenReturn(Optional.of(existingState));

        // Act
        shrinkingZoneService.initializeZoneState(game);

        // Assert
        verify(gameZoneStateDao, never()).saveGameZoneState(any(GameZoneState.class));
    }

    @Test
    void initializeZoneState_WhenShrinkingDisabled_ShouldDoNothing() {
        // Arrange
        String localGameId = "test-disabled";
        Game game = new Game();
        game.setGameID(localGameId);
        game.setShrinkingZoneEnabled(false);
        
        when(gameDao.getGameById(localGameId)).thenReturn(Optional.of(game));

        // Act
        shrinkingZoneService.initializeZoneState(game);

        // Assert
        verify(gameZoneStateDao, never()).getGameZoneState(anyString());
        verify(gameZoneStateDao, never()).saveGameZoneState(any());
    }

    @Test
    void initializeZoneState_WhenGameNotFound_ShouldThrowException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            shrinkingZoneService.initializeZoneState(null); // Pass null Game object
        }, "initializeZoneState should throw IllegalArgumentException for null Game input");
        
        verify(gameZoneStateDao, never()).saveGameZoneState(any(GameZoneState.class));
    }

    @Test
    void testBaseConstructor() {
         // Primarily to ensure the constructor runs without errors
         ShrinkingZoneService service = new ShrinkingZoneService(gameDao, gameZoneStateDao, playerDao);
         assertNotNull(service, "Service should be instantiated");
    }

    @Test
    void initializeZoneState_NewGame_CreatesCorrectState() {
        // Arrange
        String localGameId = "test-new-game";
        Game game = new Game();
        game.setGameID(localGameId);
        game.setMapId(TEST_MAP_ID);
        game.setStatus(GameStatus.ACTIVE.name());
        game.setShrinkingZoneEnabled(true);
        game.setSettings(Map.of("shrinkingZoneConfig", testConfig));
        long startTime = Instant.now().toEpochMilli();
        game.setStartTimeEpochMillis(startTime);
        
        List<Coordinate> boundary = List.of(
            new Coordinate(9.9, 9.9),  
            new Coordinate(10.1, 9.9),
            new Coordinate(10.1, 10.1),
            new Coordinate(9.9, 10.1)
        );
        game.setBoundary(boundary);

        when(gameDao.getGameById(localGameId)).thenReturn(Optional.of(game));
        when(gameZoneStateDao.getGameZoneState(localGameId)).thenReturn(Optional.empty()); // No existing state
        when(mapConfigService.getGameBoundaryCenter(localGameId)).thenReturn(boundaryCentroid);

        // Act
        shrinkingZoneService.initializeZoneState(game);

        // Assert
        ArgumentCaptor<GameZoneState> captor = ArgumentCaptor.forClass(GameZoneState.class);
        verify(gameZoneStateDao).saveGameZoneState(captor.capture());
        GameZoneState savedState = captor.getValue();

        assertEquals(localGameId, savedState.getGameId());
        assertEquals(0, savedState.getCurrentStageIndex());
        assertNotNull(savedState.getCurrentPhaseAsEnum());
        assertEquals(testConfig.get(0).getEndRadiusMeters(), savedState.getCurrentRadiusMeters());
        assertNotNull(savedState.getCurrentCenter());
    }

    @Test
    void advanceZoneState_WhenNoStateExists_ShouldInitialize() {
        // Arrange
        String localGameId = "test-no-state";
        Game game = new Game();
        game.setGameID(localGameId);
        game.setShrinkingZoneEnabled(true);
        game.setSettings(Map.of("shrinkingZoneConfig", testConfig));
        
        when(gameDao.getGameById(localGameId)).thenReturn(Optional.of(game));
        when(gameZoneStateDao.getGameZoneState(localGameId)).thenReturn(Optional.empty());
        when(mapConfigService.getGameBoundaryCenter(localGameId)).thenReturn(boundaryCentroid);

        // Act
        shrinkingZoneService.advanceZoneState(localGameId);

        // Assert
        verify(gameZoneStateDao).saveGameZoneState(any(GameZoneState.class));
    }
} 