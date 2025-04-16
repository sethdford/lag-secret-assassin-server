package com.assassin.service;

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

import com.assassin.config.MapConfiguration;
import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.GameStatus;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProximityDetectionServiceTest {

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

        Map<String, Double> weaponDistances = new HashMap<>();
        weaponDistances.put("SNIPER", 100.0);
        weaponDistances.put("MELEE", 5.0);
        testMapConfig.setWeaponDistances(weaponDistances);

        lenient().when(playerDao.getPlayerById(eq("assassin1"))).thenReturn(Optional.of(assassin));
        lenient().when(playerDao.getPlayerById(eq("target1"))).thenReturn(Optional.of(target));
        lenient().when(gameDao.getGameById(eq("game1"))).thenReturn(Optional.of(testGame));
        lenient().when(mapConfigService.getEffectiveMapConfiguration(eq("game1"))).thenReturn(testMapConfig);

        lenient().when(playerDao.getPlayerById(eq("target1"))).thenAnswer(inv -> Optional.of(target));
        lenient().when(playerDao.getPlayerById(eq("assassin1"))).thenAnswer(inv -> Optional.of(assassin));

        assassin.setStatus(PlayerStatus.ACTIVE.name());
        target.setStatus(PlayerStatus.ACTIVE.name());
    }

    private static final double DEFAULT_EFFECTIVE_DIST = 10.0 + 5.0;
    private static final double SNIPER_EFFECTIVE_DIST = 100.0 + 5.0;
    private static final double MELEE_EFFECTIVE_DIST = 5.0 + 5.0;

    @Test
    void canEliminateTarget_WhenPlayersAreCloseEnough_DefaultDistance_ShouldReturnTrue() {
        target.setLatitude(targetCoordClose.getLatitude());
        target.setLongitude(targetCoordClose.getLongitude());
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", null);
        assertTrue(canEliminate, "Should be true as target is ~5m away and effective required distance is 15m");
        verify(mapConfigService).getEffectiveMapConfiguration("game1");
        verify(playerDao, times(1)).getPlayerById("assassin1");
        verify(playerDao, times(1)).getPlayerById("target1");
    }
    
    @Test
    void canEliminateTarget_WhenPlayersAreTooFar_DefaultDistance_ShouldReturnFalse() {
        target.setLatitude(targetCoordFar.getLatitude());
        target.setLongitude(targetCoordFar.getLongitude());
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", null);
        assertFalse(canEliminate);
        verify(mapConfigService).getEffectiveMapConfiguration("game1");
        verify(playerDao, times(1)).getPlayerById("assassin1");
        verify(playerDao, times(1)).getPlayerById("target1");
    }
    
    @Test
    void canEliminateTarget_WhenAssassinNotFound_ShouldThrowException() {
        when(playerDao.getPlayerById(eq("assassin1"))).thenReturn(Optional.empty());
        assertThrows(PlayerNotFoundException.class, () -> {
            proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", null);
        });
        verify(playerDao, times(1)).getPlayerById("assassin1");
        verify(playerDao, never()).getPlayerById("target1");
        verify(gameDao, never()).getGameById(anyString());
    }
    
    @Test
    void canEliminateTarget_WhenTargetNotFound_ShouldThrowException() {
        when(playerDao.getPlayerById(eq("target1"))).thenReturn(Optional.empty());
        assertThrows(PlayerNotFoundException.class, () -> {
            proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", null);
        });
        verify(playerDao, times(1)).getPlayerById("assassin1");
        verify(playerDao, times(1)).getPlayerById("target1");
        verify(gameDao, never()).getGameById(anyString());
    }
    
    @Test
    void canEliminateTarget_WhenGameNotFound_ShouldThrowException() {
        when(gameDao.getGameById(eq("game1"))).thenReturn(Optional.empty());
        assertThrows(GameNotFoundException.class, () -> {
            proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", null);
        });
        verify(playerDao, times(1)).getPlayerById("assassin1");
        verify(playerDao, times(1)).getPlayerById("target1");
        verify(gameDao).getGameById(eq("game1"));
    }
    
    @Test
    void canEliminateTarget_WhenAssassinHasNoLocation_ShouldReturnFalse() {
        assassin.setLatitude(null);
        assassin.setLongitude(null);
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", null);
        assertFalse(canEliminate);
        verify(playerDao, times(1)).getPlayerById("assassin1");
        verify(playerDao, times(1)).getPlayerById("target1");
        verify(gameDao, never()).getGameById(anyString());
    }
    
    @Test
    void canEliminateTarget_WhenTargetHasNoLocation_ShouldReturnFalse() {
        target.setLatitude(null);
        target.setLongitude(null);
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", null);
        assertFalse(canEliminate);
        verify(playerDao, times(1)).getPlayerById("assassin1");
        verify(playerDao, times(1)).getPlayerById("target1");
        verify(gameDao, never()).getGameById(anyString());
    }

    @Test
    void canEliminateTarget_WhenKillerNotActive_ShouldReturnFalse() {
        assassin.setStatus(PlayerStatus.DEAD.name());
        target.setLatitude(targetCoordClose.getLatitude());
        target.setLongitude(targetCoordClose.getLongitude());
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", null);
        assertFalse(canEliminate, "Cannot eliminate if killer is not ACTIVE");
        verify(playerDao, times(1)).getPlayerById("assassin1");
        verify(playerDao, times(1)).getPlayerById("target1");
        verify(gameDao, never()).getGameById(anyString());
    }

    @Test
    void canEliminateTarget_WhenVictimNotActive_ShouldReturnFalse() {
        target.setStatus(PlayerStatus.PENDING.name());
        target.setLatitude(targetCoordClose.getLatitude());
        target.setLongitude(targetCoordClose.getLongitude());
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", null);
        assertFalse(canEliminate, "Cannot eliminate if victim is not ACTIVE");
        verify(playerDao, times(1)).getPlayerById("assassin1");
        verify(playerDao, times(1)).getPlayerById("target1");
        verify(gameDao, never()).getGameById(anyString());
    }

    @Test
    void canEliminateTarget_AttemptSelfElimination_ShouldReturnFalse() {
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "assassin1", null);
        assertFalse(canEliminate, "Player cannot eliminate themselves");
        verify(playerDao, never()).getPlayerById(anyString());
    }

    @Test
    void canEliminateTarget_WithValidMeleeWeapon_CloseEnough_ShouldReturnTrue() {
        target.setLatitude(targetCoordClose.getLatitude());
        target.setLongitude(targetCoordClose.getLongitude());
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "MELEE");
        assertTrue(canEliminate, "Should be true as target is ~5m away and effective required distance for MELEE is 10m");
    }

    @Test
    void canEliminateTarget_WithValidMeleeWeapon_TooFar_ShouldReturnFalse() {
        target.setLatitude(targetCoordMid.getLatitude());
        target.setLongitude(targetCoordMid.getLongitude());
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "MELEE");
        assertFalse(canEliminate, "Should be false as target is ~35m away and effective required distance for MELEE is 10m");
    }

    @Test
    void canEliminateTarget_WithValidSniperWeapon_CloseEnough_ShouldReturnTrue() {
        target.setLatitude(targetCoordMid.getLatitude());
        target.setLongitude(targetCoordMid.getLongitude());
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "SNIPER");
        assertTrue(canEliminate, "Should be true as target is ~35m away and effective required distance for SNIPER is 105m");
    }

    @Test
    void canEliminateTarget_WithUnknownWeapon_ShouldUseMapDefaultDistance() {
        target.setLatitude(targetCoordClose.getLatitude());
        target.setLongitude(targetCoordClose.getLongitude());
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "LASER_POINTER");
        assertTrue(canEliminate, "Should be true as target is ~5m away and effective required distance is 15m (map default)");
    }
    
    @Test
    void canEliminateTarget_WithUnknownWeaponAndMissingMapDefault_ShouldUseGlobalDefault() {
        testMapConfig.setEliminationDistanceMeters(null);
        when(mapConfigService.getEffectiveMapConfiguration(eq("game1"))).thenReturn(testMapConfig);
        
        target.setLatitude(targetCoordClose.getLatitude());
        target.setLongitude(targetCoordClose.getLongitude());
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "PEASHOOTER");
        assertTrue(canEliminate, "Should be true as target is ~5m away and effective required distance is 15m (global default)");
    }
    
    @Test
    void canEliminateTarget_WithWeaponTypeCaseDifference_ShouldMatch() {
        target.setLatitude(targetCoordClose.getLatitude());
        target.setLongitude(targetCoordClose.getLongitude());
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "melee");
        assertTrue(canEliminate, "Should match weapon type case-insensitively and use 10m effective distance");
    }
    
    @Test
    void canEliminateTarget_WhenKillerInSafeZone_ShouldReturnFalse() {
        // Setup
        target.setLatitude(targetCoordClose.getLatitude());
        target.setLongitude(targetCoordClose.getLongitude());
        
        // Mock: Killer is in a safe zone
        when(mapConfigService.isLocationInSafeZone(
                eq("game1"), 
                eq(new Coordinate(assassinCoord.getLatitude(), assassinCoord.getLongitude())), 
                org.mockito.ArgumentMatchers.anyLong())
        ).thenReturn(true);
        
        // Execute and verify
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "melee");
        
        assertFalse(canEliminate, "Should return false when killer is in a safe zone");
        verify(mapConfigService).isLocationInSafeZone(
                eq("game1"), 
                eq(new Coordinate(assassinCoord.getLatitude(), assassinCoord.getLongitude())), 
                org.mockito.ArgumentMatchers.anyLong());
    }
    
    @Test
    void canEliminateTarget_WhenTargetInSafeZone_ShouldReturnFalse() {
        // Setup
        target.setLatitude(targetCoordClose.getLatitude());
        target.setLongitude(targetCoordClose.getLongitude());
        
        // Mock: Killer is NOT in a safe zone, but target is
        when(mapConfigService.isLocationInSafeZone(
                eq("game1"), 
                eq(new Coordinate(assassinCoord.getLatitude(), assassinCoord.getLongitude())), 
                org.mockito.ArgumentMatchers.anyLong())
        ).thenReturn(false);
        
        when(mapConfigService.isLocationInSafeZone(
                eq("game1"), 
                eq(new Coordinate(targetCoordClose.getLatitude(), targetCoordClose.getLongitude())), 
                org.mockito.ArgumentMatchers.anyLong())
        ).thenReturn(true);
        
        // Execute and verify
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "melee");
        
        assertFalse(canEliminate, "Should return false when target is in a safe zone");
        verify(mapConfigService).isLocationInSafeZone(
                eq("game1"), 
                eq(new Coordinate(assassinCoord.getLatitude(), assassinCoord.getLongitude())), 
                org.mockito.ArgumentMatchers.anyLong());
        verify(mapConfigService).isLocationInSafeZone(
                eq("game1"), 
                eq(new Coordinate(targetCoordClose.getLatitude(), targetCoordClose.getLongitude())), 
                org.mockito.ArgumentMatchers.anyLong());
    }
    
    @Test
    void canEliminateTarget_WhenBothPlayersNotInSafeZone_ShouldCheckProximity() {
        // Setup
        target.setLatitude(targetCoordClose.getLatitude());
        target.setLongitude(targetCoordClose.getLongitude());
        
        // Mock: Neither killer nor target is in a safe zone
        when(mapConfigService.isLocationInSafeZone(
                eq("game1"), 
                eq(new Coordinate(assassinCoord.getLatitude(), assassinCoord.getLongitude())), 
                org.mockito.ArgumentMatchers.anyLong())
        ).thenReturn(false);
        
        when(mapConfigService.isLocationInSafeZone(
                eq("game1"), 
                eq(new Coordinate(targetCoordClose.getLatitude(), targetCoordClose.getLongitude())), 
                org.mockito.ArgumentMatchers.anyLong())
        ).thenReturn(false);
        
        // Execute and verify
        boolean canEliminate = proximityDetectionService.canEliminateTarget("game1", "assassin1", "target1", "melee");
        
        // They are close enough with the melee weapon, so should return true
        assertTrue(canEliminate, "Should check proximity when neither player is in a safe zone");
        verify(mapConfigService).isLocationInSafeZone(
                eq("game1"), 
                eq(new Coordinate(assassinCoord.getLatitude(), assassinCoord.getLongitude())), 
                org.mockito.ArgumentMatchers.anyLong());
        verify(mapConfigService).isLocationInSafeZone(
                eq("game1"), 
                eq(new Coordinate(targetCoordClose.getLatitude(), targetCoordClose.getLongitude())), 
                org.mockito.ArgumentMatchers.anyLong());
    }
    
    // TODO: Add tests for proximity cache behavior
    // TODO: Add tests for checkProximityAlerts method

} 