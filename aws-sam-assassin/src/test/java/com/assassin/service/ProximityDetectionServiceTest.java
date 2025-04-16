package com.assassin.service;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.config.MapConfiguration;
import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.GameStatus;
import com.assassin.model.Player;
import com.assassin.util.GeoUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProximityDetectionServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(ProximityDetectionServiceTest.class);

    @Mock
    private PlayerDao playerDao;

    @Mock
    private GameDao gameDao;

    @Mock
    private LocationService locationService;

    @Mock
    private MapConfigurationService mapConfigService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ProximityDetectionService proximityDetectionService;

    private Player assassin;
    private Player target;
    private Game testGame;
    private MapConfiguration testMapConfig;
    private Coordinate assassinCoord = new Coordinate(37.7749, -122.4194);
    private Coordinate targetCoordClose = new Coordinate(37.77492, -122.41935);
    private Coordinate targetCoordFar = new Coordinate(37.8000, -122.4000);
    private Coordinate targetCoordMid = new Coordinate(37.7750, -122.4190);

    @BeforeEach
    void setUp() {
        assassin = new Player();
        assassin.setPlayerID("assassin1");
        assassin.setGameID("game1");
        assassin.setTargetID("target1");
        assassin.setLatitude(assassinCoord.getLatitude());
        assassin.setLongitude(assassinCoord.getLongitude());
        assassin.setLocationTimestamp(Instant.now().toString());

        target = new Player();
        target.setPlayerID("target1");
        target.setGameID("game1");
        target.setTargetID("someOtherTarget");
        target.setLatitude(targetCoordMid.getLatitude());
        target.setLongitude(targetCoordMid.getLongitude());
        target.setLocationTimestamp(Instant.now().toString());

        testGame = new Game();
        testGame.setGameID("game1");
        testGame.setStatus(GameStatus.ACTIVE.name());

        testMapConfig = new MapConfiguration();
        testMapConfig.setMapId("testMap");
        testMapConfig.setEliminationDistanceMeters(10.0);
        testMapConfig.setProximityAwarenessDistanceMeters(50.0);

        lenient().when(playerDao.getPlayerById(eq("assassin1"))).thenReturn(Optional.of(assassin));
        lenient().when(playerDao.getPlayerById(eq("target1"))).thenReturn(Optional.of(target));
        lenient().when(gameDao.getGameById(eq("game1"))).thenReturn(Optional.of(testGame));
        lenient().when(mapConfigService.getEffectiveMapConfiguration(eq("game1"))).thenReturn(testMapConfig);

        lenient().when(locationService.arePlayersNearby(eq("assassin1"), eq("target1"), anyDouble()))
                 .thenAnswer(invocation -> {
                    double requiredEffectiveDistance = invocation.getArgument(2);
                    double actualDistance = Double.MAX_VALUE;
                    if (target.getLatitude() != null && target.getLongitude() != null) {
                        actualDistance = GeoUtils.calculateDistance(assassinCoord, new Coordinate(target.getLatitude(), target.getLongitude()));
                    }
                    logger.debug("Mock arePlayersNearby: Required Effective Distance = {}, Actual Distance = {}", requiredEffectiveDistance, actualDistance);
                    return actualDistance <= requiredEffectiveDistance;
                 });

        lenient().when(playerDao.getPlayerById(eq("target1"))).thenAnswer(inv -> Optional.of(target));
        lenient().when(playerDao.getPlayerById(eq("assassin1"))).thenAnswer(inv -> Optional.of(assassin));
    }

    private static final double BASE_EFFECTIVE_DIST = 10.0 + 5.0;
    private static final double MODIFIED_EFFECTIVE_DIST = 5.0 + 5.0;

    @Test
    void canEliminateTarget_WhenPlayersAreCloseEnough_ShouldReturnTrue() {
        target.setLatitude(targetCoordClose.getLatitude());
        target.setLongitude(targetCoordClose.getLongitude());
        when(playerDao.getPlayerById(eq("target1"))).thenReturn(Optional.of(target));

        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "melee");

        assertTrue(canEliminate, "Should be true as target is ~5m away and effective required distance is 15m");
        verify(locationService).arePlayersNearby(eq("assassin1"), eq("target1"), eq(BASE_EFFECTIVE_DIST));
        verify(mapConfigService).getEffectiveMapConfiguration("game1");
        verify(playerDao, times(2)).getPlayerById(anyString());
    }
    
    @Test
    void canEliminateTarget_WhenPlayersAreTooFar_ShouldReturnFalse() {
        target.setLatitude(targetCoordFar.getLatitude());
        target.setLongitude(targetCoordFar.getLongitude());
        when(playerDao.getPlayerById(eq("target1"))).thenReturn(Optional.of(target));

        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "melee");

        assertFalse(canEliminate);
        verify(locationService).arePlayersNearby(eq("assassin1"), eq("target1"), eq(BASE_EFFECTIVE_DIST));
        verify(mapConfigService).getEffectiveMapConfiguration("game1");
        verify(playerDao, times(2)).getPlayerById(anyString());
    }
    
    @Test
    void canEliminateTarget_WhenAssassinNotFound_ShouldThrowException() {
        when(playerDao.getPlayerById(eq("assassin1"))).thenReturn(Optional.empty());

        assertThrows(PlayerNotFoundException.class, () -> {
            proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "melee");
        });
        verify(gameDao).getGameById(eq("game1"));
    }
    
    @Test
    void canEliminateTarget_WhenTargetNotFound_ShouldThrowException() {
        when(playerDao.getPlayerById(eq("target1"))).thenReturn(Optional.empty());

        assertThrows(PlayerNotFoundException.class, () -> {
            proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "melee");
        });
        verify(gameDao).getGameById(eq("game1"));
        verify(playerDao).getPlayerById(eq("assassin1"));
        verify(playerDao).getPlayerById(eq("target1"));
    }
    
    @Test
    void canEliminateTarget_WhenGameNotFound_ShouldThrowException() {
        when(gameDao.getGameById(eq("game1"))).thenReturn(Optional.empty());
        
        assertThrows(GameNotFoundException.class, () -> {
            proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "melee");
        });
        verify(playerDao, never()).getPlayerById(anyString());
        verify(locationService, never()).arePlayersNearby(anyString(), anyString(), anyDouble());
    }
    
    @Test
    void canEliminateTarget_WhenAssassinHasNoLocation_ShouldReturnFalse() {
        assassin.setLatitude(null);
        assassin.setLongitude(null);
        when(playerDao.getPlayerById(eq("assassin1"))).thenReturn(Optional.of(assassin));
        
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "melee");
        
        assertFalse(canEliminate);
        verify(locationService).arePlayersNearby(eq("assassin1"), eq("target1"), eq(BASE_EFFECTIVE_DIST));
        verify(playerDao, times(2)).getPlayerById(anyString());
        verify(gameDao).getGameById(eq("game1"));
    }
    
    @Test
    void canEliminateTarget_WhenTargetHasNoLocation_ShouldReturnFalse() {
        target.setLatitude(null);
        target.setLongitude(null);
        when(playerDao.getPlayerById(eq("target1"))).thenReturn(Optional.of(target));
        
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "melee");
        
        assertFalse(canEliminate);
        verify(locationService).arePlayersNearby(eq("assassin1"), eq("target1"), eq(BASE_EFFECTIVE_DIST));
        verify(playerDao, times(2)).getPlayerById(anyString());
        verify(gameDao).getGameById(eq("game1"));
    }

    @Test
    void canEliminateTarget_WhenEliminationDistanceIsDifferentInConfig_ShouldUseConfigValue() {
        testMapConfig.setEliminationDistanceMeters(5.0);
        when(mapConfigService.getEffectiveMapConfiguration(eq("game1"))).thenReturn(testMapConfig);
        
        target.setLatitude(targetCoordClose.getLatitude());
        target.setLongitude(targetCoordClose.getLongitude());
        when(playerDao.getPlayerById(eq("target1"))).thenReturn(Optional.of(target));

        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "melee");

        assertTrue(canEliminate, "Should be true as target is ~5m away and effective required distance is 10m");
        verify(locationService).arePlayersNearby(eq("assassin1"), eq("target1"), eq(MODIFIED_EFFECTIVE_DIST));
        verify(mapConfigService).getEffectiveMapConfiguration("game1");
        verify(playerDao, times(2)).getPlayerById(anyString());
    }
    
    // TODO: Add tests for weapon-specific distances once implemented in getEliminationDistance
    // TODO: Add tests for proximity cache behavior
    // TODO: Add tests for checkProximityAlerts method (needs implementation first)

} 