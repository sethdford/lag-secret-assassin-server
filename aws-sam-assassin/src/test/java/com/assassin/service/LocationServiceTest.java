package com.assassin.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.InvalidLocationException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.Player;

@ExtendWith(MockitoExtension.class)
public class LocationServiceTest {

    @Mock
    private PlayerDao playerDao;

    @Mock
    private GameDao gameDao;

    @Mock
    private MapConfigurationService mapConfigService;

    @InjectMocks
    private LocationService locationService;

    private Player testPlayer;
    private Game testGame;
    private final String playerId = "test-player-id";
    private final String gameId = "test-game-id";
    private final double validLat = 40.0;
    private final double validLon = -75.0;
    private final double invalidLat = 100.0;
    private final double invalidLon = 200.0;

    @BeforeEach
    void setUp() {
        testPlayer = new Player();
        testPlayer.setPlayerID(playerId);
        testPlayer.setGameID(gameId);
        testPlayer.setLatitude(validLat - 0.01); // Slightly different previous location
        testPlayer.setLongitude(validLon - 0.01);
        testPlayer.setLocationTimestamp(Instant.now().minusSeconds(60).toString()); // 1 min ago

        testGame = new Game();
        testGame.setGameID(gameId);
        // Define a sample boundary for testing
        List<Coordinate> boundary = List.of(
            new Coordinate(40.1, -75.1), 
            new Coordinate(40.1, -74.9), 
            new Coordinate(39.9, -74.9), 
            new Coordinate(39.9, -75.1)
        );
        testGame.setBoundary(boundary);
    }

    // --- Tests for updatePlayerLocation --- 

    @Test
    void updatePlayerLocation_Success() throws Exception {
        // Arrange
        when(playerDao.getPlayerById(playerId)).thenReturn(Optional.of(testPlayer));
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        // Mock getGameBoundary for boundary checks used within updatePlayerLocation
        when(mapConfigService.getGameBoundary(gameId)).thenReturn(testGame.getBoundary()); 
        // Assume GeoUtils.isPointInBoundary returns true for this setup
        
        // Act
        assertDoesNotThrow(() -> 
            locationService.updatePlayerLocation(playerId, validLat, validLon, 10.0)
        );
        
        // Assert
        verify(playerDao).updatePlayerLocation(eq(playerId), eq(validLat), eq(validLon), anyString(), eq(10.0));
    }

    @Test
    void updatePlayerLocation_PlayerNotFound() {
        // Arrange
        when(playerDao.getPlayerById(playerId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(PlayerNotFoundException.class, () -> {
            locationService.updatePlayerLocation(playerId, validLat, validLon, 10.0);
        });
        verify(playerDao, never()).updatePlayerLocation(any(), any(), any(), any(), any());
    }
    
    @Test
    void updatePlayerLocation_NullCoordinates() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            locationService.updatePlayerLocation(playerId, null, validLon, 10.0);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            locationService.updatePlayerLocation(playerId, validLat, null, 10.0);
        });
    }
    
    @Test
    void updatePlayerLocation_InvalidCoordinateRange() {
        // Act & Assert
        // Note: This relies on the private validateCoordinates method calling GeoUtils.isValidCoordinate correctly
        assertThrows(InvalidLocationException.class, () -> {
             locationService.updatePlayerLocation(playerId, invalidLat, validLon, 10.0);
        });
        assertThrows(InvalidLocationException.class, () -> {
             locationService.updatePlayerLocation(playerId, validLat, invalidLon, 10.0);
        });
    }

    @Test
    void updatePlayerLocation_GameNotFound() {
        // Arrange
        when(playerDao.getPlayerById(playerId)).thenReturn(Optional.of(testPlayer));
        when(gameDao.getGameById(gameId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(GameNotFoundException.class, () -> {
            locationService.updatePlayerLocation(playerId, validLat, validLon, 10.0);
        });
        verify(playerDao, never()).updatePlayerLocation(any(), any(), any(), any(), any());
    }

    @Test
    void updatePlayerLocation_OutsideBoundaries() {
        // Arrange
        double outsideLat = 41.0;
        double outsideLon = -76.0;
        when(playerDao.getPlayerById(playerId)).thenReturn(Optional.of(testPlayer));
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        when(mapConfigService.getGameBoundary(gameId)).thenReturn(testGame.getBoundary());
        // We test the boundary check logic by ensuring InvalidLocationException is thrown
        // This implicitly tests the internal call to isWithinBoundaries, which uses GeoUtils

        // Act & Assert
        assertThrows(InvalidLocationException.class, () -> {
            locationService.updatePlayerLocation(playerId, outsideLat, outsideLon, 10.0);
        }, "Should throw InvalidLocationException when outside boundaries");
        verify(playerDao, never()).updatePlayerLocation(any(), any(), any(), any(), any());
    }
    
    @Test
    void updatePlayerLocation_ImpossibleSpeed() {
        // Arrange
        testPlayer.setLatitude(0.0); // Set a very distant previous location
        testPlayer.setLongitude(0.0);
        testPlayer.setLocationTimestamp(Instant.now().minusSeconds(1).toString()); // 1 second ago
        when(playerDao.getPlayerById(playerId)).thenReturn(Optional.of(testPlayer));
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        when(mapConfigService.getGameBoundary(gameId)).thenReturn(testGame.getBoundary());
        // Implicitly tests validateMovementSpeed which uses GeoUtils.calculateDistance

        // Act & Assert
        assertThrows(InvalidLocationException.class, () -> {
            locationService.updatePlayerLocation(playerId, validLat, validLon, 10.0); // Current location is far
        }, "Should throw InvalidLocationException for impossible speed");
        verify(playerDao, never()).updatePlayerLocation(any(), any(), any(), any(), any());
    }
    
    @Test
    void updatePlayerLocation_PlayerNotInGame() throws Exception {
        // Arrange
        testPlayer.setGameID(null); // Player not associated with a game
        when(playerDao.getPlayerById(playerId)).thenReturn(Optional.of(testPlayer));
        
        // Act
        locationService.updatePlayerLocation(playerId, validLat, validLon, 10.0);
        
        // Assert
        // Should still update location, but skip game checks
        verify(playerDao).updatePlayerLocation(eq(playerId), eq(validLat), eq(validLon), anyString(), eq(10.0));
        verify(gameDao, never()).getGameById(any());
        verify(mapConfigService, never()).getGameBoundary(any());
    }
    
    // --- Tests for getClientMapConfiguration --- 
    
    @Test
    void getClientMapConfiguration_Success() throws Exception {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        when(mapConfigService.getGameBoundary(gameId)).thenReturn(testGame.getBoundary());
        when(mapConfigService.getMaxMapSizeMeters()).thenReturn(5000.0);
        testGame.setStatus("ACTIVE");

        // Act
        Map<String, Object> config = locationService.getClientMapConfiguration(gameId);

        // Assert
        assertNotNull(config);
        assertEquals(testGame.getBoundary(), config.get("gameBoundary"));
        assertEquals(5000.0, config.get("maxMapSizeMeters"));
        assertEquals("ACTIVE", config.get("gameStatus"));
    }
    
    @Test
    void getClientMapConfiguration_GameNotFound() {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(GameNotFoundException.class, () -> {
            locationService.getClientMapConfiguration(gameId);
        });
    }
} 