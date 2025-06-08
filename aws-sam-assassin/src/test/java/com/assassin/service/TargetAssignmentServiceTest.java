package com.assassin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.dao.TargetAssignmentDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GameStateException;
import com.assassin.exception.PersistenceException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.PlayerPersistenceException;
import com.assassin.model.Game;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;
import com.assassin.model.TargetAssignment;
import com.assassin.service.TargetAssignmentService.TargetInfo;
import com.assassin.service.TargetAssignmentService.ValidationResult;

@ExtendWith(MockitoExtension.class)
class TargetAssignmentServiceTest {

    @Mock
    private PlayerDao playerDao;

    @Mock
    private GameDao gameDao;

    @Mock
    private TargetAssignmentDao targetAssignmentDao;

    private TargetAssignmentService service;
    private TargetAssignmentService serviceWithTracking;

    @BeforeEach
    void setUp() {
        service = new TargetAssignmentService(playerDao, gameDao);
        serviceWithTracking = new TargetAssignmentService(playerDao, gameDao, targetAssignmentDao);
    }

    @Test
    void testAssignInitialTargets_Success() throws Exception {
        // Arrange
        String gameId = "game-1";
        Game game = createTestGame(gameId, Arrays.asList("player1", "player2", "player3"));
        List<Player> players = createTestPlayers(Arrays.asList("player1", "player2", "player3"));

        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(game));
        when(playerDao.getPlayerById("player1")).thenReturn(Optional.of(players.get(0)));
        when(playerDao.getPlayerById("player2")).thenReturn(Optional.of(players.get(1)));
        when(playerDao.getPlayerById("player3")).thenReturn(Optional.of(players.get(2)));

        // Act
        service.assignInitialTargets(gameId);

        // Assert
        verify(playerDao, times(3)).savePlayer(any(Player.class));
        
        // Verify each player has a target assigned
        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerDao, times(3)).savePlayer(playerCaptor.capture());
        
        List<Player> savedPlayers = playerCaptor.getAllValues();
        for (Player player : savedPlayers) {
            assertNotNull(player.getTargetID());
            assertNotNull(player.getTargetName());
        }
    }

    @Test
    void testAssignInitialTargetsWithTracking_Success() throws Exception {
        // Arrange
        String gameId = "game-1";
        Game game = createTestGame(gameId, Arrays.asList("player1", "player2", "player3"));
        List<Player> players = createTestPlayers(Arrays.asList("player1", "player2", "player3"));

        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(game));
        when(playerDao.getPlayerById("player1")).thenReturn(Optional.of(players.get(0)));
        when(playerDao.getPlayerById("player2")).thenReturn(Optional.of(players.get(1)));
        when(playerDao.getPlayerById("player3")).thenReturn(Optional.of(players.get(2)));

        // Act
        serviceWithTracking.assignInitialTargets(gameId);

        // Assert
        verify(playerDao, times(3)).savePlayer(any(Player.class));
        verify(targetAssignmentDao, times(3)).saveAssignment(any(TargetAssignment.class));
        
        // Verify assignment tracking records are created
        ArgumentCaptor<TargetAssignment> assignmentCaptor = ArgumentCaptor.forClass(TargetAssignment.class);
        verify(targetAssignmentDao, times(3)).saveAssignment(assignmentCaptor.capture());
        
        List<TargetAssignment> savedAssignments = assignmentCaptor.getAllValues();
        for (TargetAssignment assignment : savedAssignments) {
            assertEquals(gameId, assignment.getGameId());
            assertEquals(TargetAssignment.AssignmentStatus.ACTIVE.name(), assignment.getStatus());
            assertEquals(TargetAssignment.AssignmentType.INITIAL.name(), assignment.getAssignmentType());
        }
    }

    @Test
    void testAssignInitialTargets_InsufficientPlayers() throws Exception {
        // Arrange
        String gameId = "game-1";
        Game game = createTestGame(gameId, Arrays.asList("player1"));
        Player player = createTestPlayer("player1");

        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(game));
        when(playerDao.getPlayerById("player1")).thenReturn(Optional.of(player));

        // Act & Assert
        GameStateException exception = assertThrows(GameStateException.class, 
            () -> service.assignInitialTargets(gameId));
        
        assertTrue(exception.getMessage().contains("requires at least 2 active players"));
        verify(playerDao, never()).savePlayer(any(Player.class));
    }

    @Test
    void testAssignInitialTargets_GameNotFound() throws Exception {
        // Arrange
        String gameId = "nonexistent-game";
        when(gameDao.getGameById(gameId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(GameNotFoundException.class, () -> service.assignInitialTargets(gameId));
        verify(playerDao, never()).savePlayer(any(Player.class));
    }

    @Test
    void testReassignTargetAfterElimination_Success() throws Exception {
        // Arrange
        String gameId = "game-1";
        String eliminatedPlayerId = "player2";
        String assassinPlayerId = "player1";
        
        Game game = createTestGame(gameId, Arrays.asList("player1", "player2", "player3"));
        Player eliminatedPlayer = createTestPlayer("player2");
        eliminatedPlayer.setTargetID("player3");
        eliminatedPlayer.setTargetName("Player 3");
        
        Player assassinPlayer = createTestPlayer("player1");
        assassinPlayer.setTargetID("player2");
        
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(game));
        when(playerDao.getPlayerById(eliminatedPlayerId)).thenReturn(Optional.of(eliminatedPlayer));
        when(playerDao.getPlayerById(assassinPlayerId)).thenReturn(Optional.of(assassinPlayer));

        // Act
        service.reassignTargetAfterElimination(gameId, eliminatedPlayerId, assassinPlayerId);

        // Assert
        verify(playerDao, times(2)).savePlayer(any(Player.class));
        
        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerDao, times(2)).savePlayer(playerCaptor.capture());
        
        List<Player> savedPlayers = playerCaptor.getAllValues();
        
        // Find the assassin player in saved players
        Player savedAssassin = savedPlayers.stream()
            .filter(p -> p.getPlayerID().equals(assassinPlayerId))
            .findFirst()
            .orElse(null);
        assertNotNull(savedAssassin);
        assertEquals("player3", savedAssassin.getTargetID());
        assertEquals("Player 3", savedAssassin.getTargetName());
        
        // Find the eliminated player in saved players
        Player savedEliminated = savedPlayers.stream()
            .filter(p -> p.getPlayerID().equals(eliminatedPlayerId))
            .findFirst()
            .orElse(null);
        assertNotNull(savedEliminated);
        assertNull(savedEliminated.getTargetID());
        assertNull(savedEliminated.getTargetName());
    }

    @Test
    void testReassignTargetAfterEliminationWithTracking_Success() throws Exception {
        // Arrange
        String gameId = "game-1";
        String eliminatedPlayerId = "player2";
        String assassinPlayerId = "player1";
        
        Game game = createTestGame(gameId, Arrays.asList("player1", "player2", "player3"));
        Player eliminatedPlayer = createTestPlayer("player2");
        eliminatedPlayer.setTargetID("player3");
        eliminatedPlayer.setTargetName("Player 3");
        
        Player assassinPlayer = createTestPlayer("player1");
        assassinPlayer.setTargetID("player2");
        
        TargetAssignment currentAssignment = new TargetAssignment();
        currentAssignment.setAssignmentId("assignment-1");
        currentAssignment.setGameId(gameId);
        currentAssignment.setAssignerId(assassinPlayerId);
        currentAssignment.setTargetId(eliminatedPlayerId);
        currentAssignment.setStatus(TargetAssignment.AssignmentStatus.ACTIVE.name());
        
        TargetAssignment eliminatedAssignment = new TargetAssignment();
        eliminatedAssignment.setAssignmentId("assignment-2");
        eliminatedAssignment.setGameId(gameId);
        eliminatedAssignment.setAssignerId(eliminatedPlayerId);
        eliminatedAssignment.setTargetId("player3");
        eliminatedAssignment.setStatus(TargetAssignment.AssignmentStatus.ACTIVE.name());

        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(game));
        when(playerDao.getPlayerById(eliminatedPlayerId)).thenReturn(Optional.of(eliminatedPlayer));
        when(playerDao.getPlayerById(assassinPlayerId)).thenReturn(Optional.of(assassinPlayer));
        when(targetAssignmentDao.getCurrentAssignmentForPlayer(gameId, assassinPlayerId))
            .thenReturn(Optional.of(currentAssignment));
        when(targetAssignmentDao.getCurrentAssignmentForPlayer(gameId, eliminatedPlayerId))
            .thenReturn(Optional.of(eliminatedAssignment));

        // Act
        serviceWithTracking.reassignTargetAfterElimination(gameId, eliminatedPlayerId, assassinPlayerId);

        // Assert
        verify(playerDao, times(2)).savePlayer(any(Player.class));
        verify(targetAssignmentDao, times(3)).saveAssignment(any(TargetAssignment.class));
        
        // Verify assignment tracking updates
        ArgumentCaptor<TargetAssignment> assignmentCaptor = ArgumentCaptor.forClass(TargetAssignment.class);
        verify(targetAssignmentDao, times(3)).saveAssignment(assignmentCaptor.capture());
        
        List<TargetAssignment> savedAssignments = assignmentCaptor.getAllValues();
        
        // Should have: completed assignment, cancelled assignment, new reassignment
        long completedCount = savedAssignments.stream()
            .filter(a -> TargetAssignment.AssignmentStatus.COMPLETED.name().equals(a.getStatus()))
            .count();
        long cancelledCount = savedAssignments.stream()
            .filter(a -> TargetAssignment.AssignmentStatus.CANCELLED.name().equals(a.getStatus()))
            .count();
        long activeCount = savedAssignments.stream()
            .filter(a -> TargetAssignment.AssignmentStatus.ACTIVE.name().equals(a.getStatus()))
            .count();
            
        assertEquals(1, completedCount);
        assertEquals(1, cancelledCount);
        assertEquals(1, activeCount);
    }

    @Test
    void testGetCurrentTarget_Success() throws Exception {
        // Arrange
        String playerId = "player1";
        String targetId = "player2";
        
        Player player = createTestPlayer(playerId);
        player.setTargetID(targetId);
        player.setTargetName("Player 2");
        player.setGameID("game-1");
        
        Player targetPlayer = createTestPlayer(targetId);
        targetPlayer.setPlayerName("Player 2");
        targetPlayer.setStatus(PlayerStatus.ACTIVE.name());

        when(playerDao.getPlayerById(playerId)).thenReturn(Optional.of(player));
        when(playerDao.getPlayerById(targetId)).thenReturn(Optional.of(targetPlayer));

        // Act
        Optional<TargetInfo> result = service.getCurrentTarget(playerId);

        // Assert
        assertTrue(result.isPresent());
        TargetInfo targetInfo = result.get();
        assertEquals(targetId, targetInfo.getTargetId());
        assertEquals("Player 2", targetInfo.getTargetName());
        assertEquals(PlayerStatus.ACTIVE.name(), targetInfo.getTargetStatus());
        assertNull(targetInfo.getAssignmentId()); // No tracking in basic service
    }

    @Test
    void testGetCurrentTargetWithTracking_Success() throws Exception {
        // Arrange
        String playerId = "player1";
        String targetId = "player2";
        String gameId = "game-1";
        
        Player player = createTestPlayer(playerId);
        player.setTargetID(targetId);
        player.setTargetName("Player 2");
        player.setGameID(gameId);
        
        Player targetPlayer = createTestPlayer(targetId);
        targetPlayer.setPlayerName("Player 2");
        targetPlayer.setStatus(PlayerStatus.ACTIVE.name());
        
        TargetAssignment assignment = new TargetAssignment();
        assignment.setAssignmentId("assignment-1");
        assignment.setAssignmentDate("2023-01-01T10:00:00Z");
        assignment.setAssignmentType(TargetAssignment.AssignmentType.INITIAL.name());

        when(playerDao.getPlayerById(playerId)).thenReturn(Optional.of(player));
        when(playerDao.getPlayerById(targetId)).thenReturn(Optional.of(targetPlayer));
        when(targetAssignmentDao.getCurrentAssignmentForPlayer(gameId, playerId))
            .thenReturn(Optional.of(assignment));

        // Act
        Optional<TargetInfo> result = serviceWithTracking.getCurrentTarget(playerId);

        // Assert
        assertTrue(result.isPresent());
        TargetInfo targetInfo = result.get();
        assertEquals(targetId, targetInfo.getTargetId());
        assertEquals("Player 2", targetInfo.getTargetName());
        assertEquals(PlayerStatus.ACTIVE.name(), targetInfo.getTargetStatus());
        assertEquals("assignment-1", targetInfo.getAssignmentId());
        assertEquals("2023-01-01T10:00:00Z", targetInfo.getAssignmentDate());
        assertEquals(TargetAssignment.AssignmentType.INITIAL.name(), targetInfo.getAssignmentType());
    }

    @Test
    void testGetCurrentTarget_NoTarget() throws Exception {
        // Arrange
        String playerId = "player1";
        Player player = createTestPlayer(playerId);
        player.setTargetID(null);

        when(playerDao.getPlayerById(playerId)).thenReturn(Optional.of(player));

        // Act
        Optional<TargetInfo> result = service.getCurrentTarget(playerId);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetCurrentTarget_PlayerNotFound() throws Exception {
        // Arrange
        String playerId = "nonexistent-player";
        when(playerDao.getPlayerById(playerId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(PlayerNotFoundException.class, () -> service.getCurrentTarget(playerId));
    }

    @Test
    void testGetAssignmentHistory_WithTracking() throws Exception {
        // Arrange
        String gameId = "game-1";
        String playerId = "player1";
        List<TargetAssignment> history = Arrays.asList(
            createTestAssignment("assignment-1", gameId, playerId, "player2"),
            createTestAssignment("assignment-2", gameId, playerId, "player3")
        );

        when(targetAssignmentDao.getAssignmentHistoryForPlayer(gameId, playerId)).thenReturn(history);

        // Act
        List<TargetAssignment> result = serviceWithTracking.getAssignmentHistory(gameId, playerId);

        // Assert
        assertEquals(2, result.size());
        assertEquals(history, result);
    }

    @Test
    void testGetAssignmentHistory_WithoutTracking() throws Exception {
        // Arrange
        String gameId = "game-1";
        String playerId = "player1";

        // Act
        List<TargetAssignment> result = service.getAssignmentHistory(gameId, playerId);

        // Assert
        assertTrue(result.isEmpty());
        verifyNoInteractions(targetAssignmentDao);
    }

    @Test
    void testGetActiveAssignments_WithTracking() throws Exception {
        // Arrange
        String gameId = "game-1";
        List<TargetAssignment> activeAssignments = Arrays.asList(
            createTestAssignment("assignment-1", gameId, "player1", "player2"),
            createTestAssignment("assignment-2", gameId, "player2", "player3")
        );

        when(targetAssignmentDao.getActiveAssignmentsForGame(gameId)).thenReturn(activeAssignments);

        // Act
        List<TargetAssignment> result = serviceWithTracking.getActiveAssignments(gameId);

        // Assert
        assertEquals(2, result.size());
        assertEquals(activeAssignments, result);
    }

    @Test
    void testGetActiveAssignments_WithoutTracking() throws Exception {
        // Arrange
        String gameId = "game-1";

        // Act
        List<TargetAssignment> result = service.getActiveAssignments(gameId);

        // Assert
        assertTrue(result.isEmpty());
        verifyNoInteractions(targetAssignmentDao);
    }

    @Test
    void testValidateTargetChain_Valid() throws Exception {
        // Arrange
        String gameId = "game-1";
        Game game = createTestGame(gameId, Arrays.asList("player1", "player2", "player3"));
        List<Player> players = createTestPlayers(Arrays.asList("player1", "player2", "player3"));
        
        // Set up circular chain: player1 -> player2 -> player3 -> player1
        players.get(0).setTargetID("player2");
        players.get(1).setTargetID("player3");
        players.get(2).setTargetID("player1");

        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(game));
        when(playerDao.getPlayerById("player1")).thenReturn(Optional.of(players.get(0)));
        when(playerDao.getPlayerById("player2")).thenReturn(Optional.of(players.get(1)));
        when(playerDao.getPlayerById("player3")).thenReturn(Optional.of(players.get(2)));

        // Act
        ValidationResult result = service.validateTargetChain(gameId);

        // Assert
        assertTrue(result.isValid());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void testValidateTargetChainWithTracking_Valid() throws Exception {
        // Arrange
        String gameId = "game-1";
        Game game = createTestGame(gameId, Arrays.asList("player1", "player2", "player3"));
        List<Player> players = createTestPlayers(Arrays.asList("player1", "player2", "player3"));
        
        // Set up circular chain: player1 -> player2 -> player3 -> player1
        players.get(0).setTargetID("player2");
        players.get(1).setTargetID("player3");
        players.get(2).setTargetID("player1");

        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(game));
        when(playerDao.getPlayerById("player1")).thenReturn(Optional.of(players.get(0)));
        when(playerDao.getPlayerById("player2")).thenReturn(Optional.of(players.get(1)));
        when(playerDao.getPlayerById("player3")).thenReturn(Optional.of(players.get(2)));
        when(targetAssignmentDao.validateAssignmentIntegrity(gameId)).thenReturn(new ArrayList<>());

        // Act
        ValidationResult result = serviceWithTracking.validateTargetChain(gameId);

        // Assert
        assertTrue(result.isValid());
        assertTrue(result.getIssues().isEmpty());
        verify(targetAssignmentDao).validateAssignmentIntegrity(gameId);
    }

    @Test
    void testValidateTargetChain_Invalid() throws Exception {
        // Arrange
        String gameId = "game-1";
        Game game = createTestGame(gameId, Arrays.asList("player1", "player2"));
        List<Player> players = createTestPlayers(Arrays.asList("player1", "player2"));
        
        // Set up broken chain: player1 has no target, player2 targets nonexistent player
        players.get(0).setTargetID(null);
        players.get(1).setTargetID("nonexistent-player");

        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(game));
        when(playerDao.getPlayerById("player1")).thenReturn(Optional.of(players.get(0)));
        when(playerDao.getPlayerById("player2")).thenReturn(Optional.of(players.get(1)));

        // Act
        ValidationResult result = service.validateTargetChain(gameId);

        // Assert
        assertFalse(result.isValid());
        assertEquals(2, result.getIssues().size());
        assertTrue(result.getIssues().stream().anyMatch(issue -> issue.contains("has no target assigned")));
        assertTrue(result.getIssues().stream().anyMatch(issue -> issue.contains("targets non-existent")));
    }

    // --- Helper Methods ---

    private Game createTestGame(String gameId, List<String> playerIds) {
        Game game = new Game();
        game.setGameID(gameId);
        game.setGameName("Test Game");
        game.setPlayerIDs(playerIds);
        game.setStatus("ACTIVE");
        return game;
    }

    private Player createTestPlayer(String playerId) {
        Player player = new Player();
        player.setPlayerID(playerId);
        player.setPlayerName("Player " + playerId.substring(playerId.length() - 1));
        player.setStatus(PlayerStatus.ACTIVE.name());
        return player;
    }

    private List<Player> createTestPlayers(List<String> playerIds) {
        List<Player> players = new ArrayList<>();
        for (String playerId : playerIds) {
            players.add(createTestPlayer(playerId));
        }
        return players;
    }

    private TargetAssignment createTestAssignment(String assignmentId, String gameId, String assignerId, String targetId) {
        TargetAssignment assignment = new TargetAssignment();
        assignment.setAssignmentId(assignmentId);
        assignment.setGameId(gameId);
        assignment.setAssignerId(assignerId);
        assignment.setTargetId(targetId);
        assignment.setStatus(TargetAssignment.AssignmentStatus.ACTIVE.name());
        assignment.setAssignmentType(TargetAssignment.AssignmentType.INITIAL.name());
        return assignment;
    }
} 