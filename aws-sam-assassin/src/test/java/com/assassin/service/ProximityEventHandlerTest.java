package com.assassin.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.model.Game;
import com.assassin.model.GameStatus;
import com.assassin.model.Notification;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;

@ExtendWith(MockitoExtension.class)
public class ProximityEventHandlerTest {

    @Mock
    private PlayerDao playerDao;
    
    @Mock
    private GameDao gameDao;
    
    @Mock
    private NotificationService notificationService;
    
    @Mock
    private ProximityDetectionService proximityDetectionService;
    
    private ProximityEventHandler proximityEventHandler;
    
    private Game testGame;
    private Player testPlayer;
    private Player testTarget;
    
    @BeforeEach
    void setUp() {
        proximityEventHandler = new ProximityEventHandler(
            playerDao, 
            gameDao, 
            proximityDetectionService,
            notificationService
        );
        
        // Setup test data
        testGame = new Game();
        testGame.setGameID("game123");
        testGame.setGameName("Test Game");
        testGame.setStatus(GameStatus.ACTIVE.name());
        
        testPlayer = new Player();
        testPlayer.setPlayerID("player123");
        testPlayer.setGameID("game123");
        testPlayer.setPlayerName("Hunter");
        testPlayer.setStatus(PlayerStatus.ACTIVE.name());
        testPlayer.setTargetID("target123");
        testPlayer.setKillCount(0);
        
        testTarget = new Player();
        testTarget.setPlayerID("target123");
        testTarget.setGameID("game123");
        testTarget.setPlayerName("Target");
        testTarget.setStatus(PlayerStatus.ACTIVE.name());
        testTarget.setTargetID("nextTarget123");
    }
    
    @Test
    void processEliminationAttempt_Success() throws GameNotFoundException, PlayerNotFoundException {
        // Arrange
        when(gameDao.getGameById("game123")).thenReturn(Optional.of(testGame));
        when(playerDao.getPlayerById("player123")).thenReturn(Optional.of(testPlayer));
        when(playerDao.getPlayerById("target123")).thenReturn(Optional.of(testTarget));
        when(proximityDetectionService.canEliminateTarget(eq("game123"), eq("player123"), eq("target123"), anyString()))
            .thenReturn(true);
        
        Player nextTarget = new Player();
        nextTarget.setPlayerID("nextTarget123");
        nextTarget.setPlayerName("NextTarget");
        nextTarget.setGameID("game123");
        nextTarget.setStatus(PlayerStatus.ACTIVE.name());
        
        when(playerDao.getPlayerById("nextTarget123")).thenReturn(Optional.of(nextTarget));
        
        // Simulate getting active players manually since getPlayersByGameIdAndStatus is not directly available
        Player otherActivePlayer = new Player(); 
        otherActivePlayer.setPlayerID("otherActivePlayer");
        otherActivePlayer.setGameID("game123");
        otherActivePlayer.setStatus(PlayerStatus.ACTIVE.name());
        // Note: In the test, the hunter is still ACTIVE after elimination
        when(playerDao.getPlayersByGameId("game123")).thenReturn(Arrays.asList(testPlayer, otherActivePlayer));
        
        // Act
        boolean result = proximityEventHandler.processEliminationAttempt("game123", "player123", "target123", "pistol");
        
        // Assert
        assertTrue(result, "Elimination should succeed");
        
        // Verify target was updated correctly
        // ArgumentCaptor<Player> targetCaptor = ArgumentCaptor.forClass(Player.class); // Captor moved below
        // verify(playerDao, times(1)).savePlayer(targetCaptor.capture()); // Verification changed below
        // Player updatedTarget = targetCaptor.getAllValues().get(0);
        // assertEquals(PlayerStatus.DEAD.name(), updatedTarget.getStatus(), "Target should be eliminated");
        
        // Verify player was updated with new target and target was updated
        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        // Verify savePlayer called 3 times: target (DEAD), player (new target, kill count++), next target (new hunter - currently only logged)
        verify(playerDao, times(3)).savePlayer(playerCaptor.capture()); 
        
        List<Player> savedPlayers = playerCaptor.getAllValues();
        
        Optional<Player> updatedTarget = savedPlayers.stream()
            .filter(p -> p.getPlayerID().equals("target123"))
            .findFirst();
        assertTrue(updatedTarget.isPresent(), "Target player should have been saved");
        assertEquals(PlayerStatus.DEAD.name(), updatedTarget.get().getStatus(), "Target should be eliminated");
            
        Optional<Player> updatedPlayer = savedPlayers.stream()
            .filter(p -> p.getPlayerID().equals("player123"))
            .findFirst();
        assertTrue(updatedPlayer.isPresent(), "Player should be updated");
        assertEquals("nextTarget123", updatedPlayer.get().getTargetID(), "Player should have new target");
        assertEquals(1, updatedPlayer.get().getKillCount(), "Player kill count should be incremented");
        
        // Verify Next Target was updated (hunter assignment - logged, not set)
        Optional<Player> updatedNextTarget = savedPlayers.stream()
            .filter(p -> p.getPlayerID().equals("nextTarget123"))
            .findFirst();
        assertTrue(updatedNextTarget.isPresent(), "Next target player should be updated (saved)");
        
        // Verify notifications were sent
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, times(3)).sendNotification(notificationCaptor.capture());
        List<Notification> sentNotifications = notificationCaptor.getAllValues();
        assertTrue(sentNotifications.stream().anyMatch(n -> n.getRecipientPlayerId().equals("player123") && n.getType().equals("ELIMINATION_SUCCESS")), "Hunter success notification missing");
        assertTrue(sentNotifications.stream().anyMatch(n -> n.getRecipientPlayerId().equals("target123") && n.getType().equals("ELIMINATED")), "Target eliminated notification missing");
        assertTrue(sentNotifications.stream().anyMatch(n -> n.getRecipientPlayerId().equals("nextTarget123") && n.getType().equals("NEW_HUNTER")), "Next target new hunter notification missing");
    }
    
    @Test
    void processEliminationAttempt_NotInProximity() throws GameNotFoundException, PlayerNotFoundException {
        // Arrange
        when(gameDao.getGameById("game123")).thenReturn(Optional.of(testGame));
        when(playerDao.getPlayerById("player123")).thenReturn(Optional.of(testPlayer));
        when(playerDao.getPlayerById("target123")).thenReturn(Optional.of(testTarget));
        
        // Players are NOT in proximity
        when(proximityDetectionService.canEliminateTarget(eq("game123"), eq("player123"), eq("target123"), anyString()))
            .thenReturn(false);
        
        // Act
        boolean result = proximityEventHandler.processEliminationAttempt("game123", "player123", "target123", "pistol");
        
        // Assert
        assertFalse(result, "Elimination should fail due to proximity");
        
        // Verify no player state updates were made (only the player lookup occurs)
        verify(playerDao, never()).savePlayer(any(Player.class));
        // verify(notificationService, never()).sendNotification(any(Notification.class)); // Removed incorrect verification - notification IS sent on failure
    }
    
    @Test
    void processEliminationAttempt_GameNotActive() throws GameNotFoundException, PlayerNotFoundException {
        // Arrange
        testGame.setStatus(GameStatus.COMPLETED.name());
        when(gameDao.getGameById("game123")).thenReturn(Optional.of(testGame));
        
        // Act
        boolean result = proximityEventHandler.processEliminationAttempt("game123", "player123", "target123", "pistol");
        
        // Assert
        assertFalse(result, "Elimination should fail in inactive game");
        verify(proximityDetectionService, never()).canEliminateTarget(anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void processEliminationAttempt_IllegalTarget() throws GameNotFoundException, PlayerNotFoundException {
        // Arrange
        // Change the hunter's target to make this an illegal elimination attempt
        testPlayer.setTargetID("someoneElseTarget456");
        
        when(gameDao.getGameById("game123")).thenReturn(Optional.of(testGame));
        when(playerDao.getPlayerById("player123")).thenReturn(Optional.of(testPlayer));
        
        // Act
        boolean result = proximityEventHandler.processEliminationAttempt("game123", "player123", "target123", "pistol");
        
        // Assert
        assertFalse(result, "Elimination should fail with illegal target");
        verify(proximityDetectionService, never()).canEliminateTarget(anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void processEliminationAttempt_LastPlayerRemaining() throws GameNotFoundException, PlayerNotFoundException {
        // Arrange
        when(gameDao.getGameById("game123")).thenReturn(Optional.of(testGame));
        when(playerDao.getPlayerById("player123")).thenReturn(Optional.of(testPlayer));
        when(playerDao.getPlayerById("target123")).thenReturn(Optional.of(testTarget));
        when(proximityDetectionService.canEliminateTarget(eq("game123"), eq("player123"), eq("target123"), anyString()))
            .thenReturn(true);
        
        // Mock getPlayersByGameId to return only the hunter as active after savePlayer calls
        // This is tricky because the state changes *during* the method call.
        // We'll verify the outcome (game completion) based on the logic, assuming DAO behaves.
        // The critical part is that the stream filter in checkGameCompletion should find only 1 ACTIVE player.
        when(playerDao.getPlayersByGameId("game123")).thenReturn(Arrays.asList(testPlayer, testTarget)); 
        // We expect testTarget status to be DEAD when checkGameCompletion runs.
        
        // Act
        boolean result = proximityEventHandler.processEliminationAttempt("game123", "player123", "target123", "pistol");
        
        // Assert
        assertTrue(result, "Elimination should succeed");
        
        // Verify game was completed
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameDao, times(1)).saveGame(gameCaptor.capture());
        Game updatedGame = gameCaptor.getValue();
        assertEquals(GameStatus.COMPLETED.name(), updatedGame.getStatus(), "Game should be completed");
        
        // Verify winner notification
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, atLeastOnce()).sendNotification(notificationCaptor.capture());
        assertTrue(notificationCaptor.getAllValues().stream()
                   .anyMatch(n -> n.getRecipientPlayerId().equals("player123") && n.getType().equals("GAME_WON")), 
                   "Winner notification missing");
    }
} 