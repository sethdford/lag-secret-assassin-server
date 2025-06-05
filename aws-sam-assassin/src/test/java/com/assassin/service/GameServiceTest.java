package com.assassin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GameStateException;
import com.assassin.exception.UnauthorizedException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.GameStatus;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameDao gameDao;

    @Mock
    private PlayerDao playerDao; // Mock PlayerDao even if not directly used in updateBoundary

    @Mock
    private ShrinkingZoneService shrinkingZoneService;

    @InjectMocks
    private GameService gameService;

    private Game testGame;
    private List<Coordinate> validBoundary;
    private String gameId = "test-game-123";
    private String adminPlayerId = "admin-player-abc";
    private String nonAdminPlayerId = "non-admin-player-xyz";

    @BeforeEach
    void setUp() {
        testGame = new Game();
        testGame.setGameID(gameId);
        testGame.setAdminPlayerID(adminPlayerId);
        testGame.setStatus(GameStatus.PENDING.name());

        validBoundary = List.of(
            new Coordinate(10.0, 10.0),
            new Coordinate(10.0, 20.0),
            new Coordinate(20.0, 20.0),
            new Coordinate(20.0, 10.0)
        );
    }

    // Test Case 1: Success Case
    @Test
    void testUpdateGameBoundary_Success() {
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        // No exception expected from saveGame in mock

        Game updatedGame = gameService.updateGameBoundary(gameId, validBoundary, adminPlayerId);

        assertNotNull(updatedGame);
        assertEquals(validBoundary, updatedGame.getBoundary());
        verify(gameDao).getGameById(gameId); // Verify game was fetched
        verify(gameDao).saveGame(testGame); // Verify game was saved with updated boundary
    }

    // Test Case 2: Game Not Found
    @Test
    void testUpdateGameBoundary_GameNotFound() {
        when(gameDao.getGameById(gameId)).thenReturn(Optional.empty());

        assertThrows(GameNotFoundException.class, () -> {
            gameService.updateGameBoundary(gameId, validBoundary, adminPlayerId);
        });

        verify(gameDao).getGameById(gameId);
        verify(gameDao, never()).saveGame(any(Game.class)); // Ensure save was not called
    }

    // Test Case 3: Not Admin
    @Test
    void testUpdateGameBoundary_NotAdmin() {
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        assertThrows(UnauthorizedException.class, () -> {
            gameService.updateGameBoundary(gameId, validBoundary, nonAdminPlayerId);
        });

        verify(gameDao).getGameById(gameId);
        verify(gameDao, never()).saveGame(any(Game.class));
    }

    // Test Case 4: Incorrect Game State (e.g., ACTIVE)
    @Test
    void testUpdateGameBoundary_WrongGameState() {
        testGame.setStatus(GameStatus.ACTIVE.name());
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        assertThrows(GameStateException.class, () -> {
            gameService.updateGameBoundary(gameId, validBoundary, adminPlayerId);
        });

        verify(gameDao).getGameById(gameId);
        verify(gameDao, never()).saveGame(any(Game.class));
    }

    // Test Case 5: DAO Save Error
    @Test
    void testUpdateGameBoundary_DaoSaveError() {
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        // Simulate DAO throwing an exception during save
        doThrow(new RuntimeException("DAO Save Failed")).when(gameDao).saveGame(any(Game.class));

        // Expect the service to wrap the DAO exception
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            gameService.updateGameBoundary(gameId, validBoundary, adminPlayerId);
        });
        
        assertTrue(thrown.getMessage().contains("Failed to save game with updated boundary."));
        assertEquals("DAO Save Failed", thrown.getCause().getMessage());

        verify(gameDao).getGameById(gameId);
        verify(gameDao).saveGame(testGame);
    }
    
    // Add tests for startGameAndAssignTargets if they don't exist
    // ...
} 