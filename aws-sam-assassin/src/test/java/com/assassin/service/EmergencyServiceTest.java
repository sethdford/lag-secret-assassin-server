package com.assassin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GameStateException;
import com.assassin.exception.UnauthorizedException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Game;
import com.assassin.model.GameStatus;
import com.assassin.model.Notification;

/**
 * Unit tests for EmergencyService.
 * Tests emergency state transitions, authorization rules, game mechanics pausing,
 * and notification dispatch to all participants.
 */
@ExtendWith(MockitoExtension.class)
class EmergencyServiceTest {

    @Mock
    private GameDao gameDao;

    @Mock
    private PlayerDao playerDao;

    @Mock
    private NotificationService notificationService;

    private EmergencyService emergencyService;

    private Game testGame;
    private final String gameId = "test-game-123";
    private final String adminPlayerId = "admin-player-456";
    private final String nonAdminPlayerId = "other-player-789";
    private final String emergencyReason = "Safety concern reported";

    @BeforeEach
    void setUp() {
        emergencyService = new EmergencyService(gameDao, playerDao, notificationService);

        // Setup test game
        testGame = new Game();
        testGame.setGameID(gameId);
        testGame.setAdminPlayerID(adminPlayerId);
        testGame.setStatus(GameStatus.ACTIVE.name());
        testGame.setPlayerIDs(Arrays.asList(adminPlayerId, nonAdminPlayerId, "player-3"));
        testGame.setEmergencyPause(false);
        testGame.setEmergencyReason(null);
        testGame.setEmergencyTimestamp(null);
        testGame.setEmergencyTriggeredBy(null);
    }

    // ==================== pauseGame Tests ====================

    @Test
    void pauseGame_Success_ValidAdminRequest() throws Exception {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act
        Game result = emergencyService.pauseGame(gameId, emergencyReason, adminPlayerId);

        // Assert
        assertTrue(result.getEmergencyPause());
        assertEquals(emergencyReason, result.getEmergencyReason());
        assertEquals(adminPlayerId, result.getEmergencyTriggeredBy());
        assertNotNull(result.getEmergencyTimestamp());

        verify(gameDao).saveGame(result);
        verify(notificationService, times(3)).sendNotification(any(Notification.class));
    }

    @Test
    void pauseGame_FailsValidation_NullGameId() {
        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () ->
            emergencyService.pauseGame(null, emergencyReason, adminPlayerId));
        
        assertEquals("Game ID cannot be null or empty", exception.getMessage());
        verify(gameDao, never()).getGameById(any());
        verify(gameDao, never()).saveGame(any());
    }

    @Test
    void pauseGame_FailsValidation_EmptyGameId() {
        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () ->
            emergencyService.pauseGame("", emergencyReason, adminPlayerId));
        
        assertEquals("Game ID cannot be null or empty", exception.getMessage());
        verify(gameDao, never()).getGameById(any());
        verify(gameDao, never()).saveGame(any());
    }

    @Test
    void pauseGame_FailsValidation_NullRequestingPlayerId() {
        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () ->
            emergencyService.pauseGame(gameId, emergencyReason, null));
        
        assertEquals("Requesting player ID cannot be null or empty", exception.getMessage());
        verify(gameDao, never()).getGameById(any());
        verify(gameDao, never()).saveGame(any());
    }

    @Test
    void pauseGame_FailsValidation_EmptyReason() {
        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () ->
            emergencyService.pauseGame(gameId, "", adminPlayerId));
        
        assertEquals("Emergency reason cannot be null or empty", exception.getMessage());
        verify(gameDao, never()).getGameById(any());
        verify(gameDao, never()).saveGame(any());
    }

    @Test
    void pauseGame_FailsNotFound_GameDoesNotExist() {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.empty());

        // Act & Assert
        GameNotFoundException exception = assertThrows(GameNotFoundException.class, () ->
            emergencyService.pauseGame(gameId, emergencyReason, adminPlayerId));
        
        assertEquals("Game not found with ID: " + gameId, exception.getMessage());
        verify(gameDao, never()).saveGame(any());
    }

    @Test
    void pauseGame_FailsAuthorization_NonAdminPlayer() {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act & Assert
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
            emergencyService.pauseGame(gameId, emergencyReason, nonAdminPlayerId));
        
        assertEquals("Only game administrators can trigger emergency pauses", exception.getMessage());
        verify(gameDao, never()).saveGame(any());
    }

    @Test
    void pauseGame_FailsGameState_AlreadyInEmergencyPause() {
        // Arrange
        testGame.setEmergencyPause(true);
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act & Assert
        GameStateException exception = assertThrows(GameStateException.class, () ->
            emergencyService.pauseGame(gameId, emergencyReason, adminPlayerId));
        
        assertEquals("Game " + gameId + " is already in emergency pause mode", exception.getMessage());
        verify(gameDao, never()).saveGame(any());
    }

    @Test
    void pauseGame_FailsGameState_InvalidGameStatus() {
        // Arrange
        testGame.setStatus(GameStatus.COMPLETED.name());
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act & Assert
        GameStateException exception = assertThrows(GameStateException.class, () ->
            emergencyService.pauseGame(gameId, emergencyReason, adminPlayerId));
        
        assertTrue(exception.getMessage().contains("cannot be paused"));
        verify(gameDao, never()).saveGame(any());
    }

    @Test
    void pauseGame_AllowsPending_GameStatus() throws Exception {
        // Arrange
        testGame.setStatus(GameStatus.PENDING.name());
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act
        Game result = emergencyService.pauseGame(gameId, emergencyReason, adminPlayerId);

        // Assert
        assertTrue(result.getEmergencyPause());
        verify(gameDao).saveGame(result);
    }

    @Test
    void pauseGame_SendsNotificationToAllPlayers() throws Exception {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act
        emergencyService.pauseGame(gameId, emergencyReason, adminPlayerId);

        // Assert - Verify notifications sent to all 3 players
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, times(3)).sendNotification(notificationCaptor.capture());

        for (Notification notification : notificationCaptor.getAllValues()) {
            assertEquals("EMERGENCY_PAUSE", notification.getType());
            assertEquals("Game has been paused due to an emergency: " + emergencyReason, notification.getMessage());
            assertEquals(gameId, notification.getGameId());
            assertFalse(notification.isRead());
            assertNotNull(notification.getTimestamp());
            assertTrue(testGame.getPlayerIDs().contains(notification.getRecipientPlayerId()));
        }
    }

    @Test
    void pauseGame_HandlesDaoException() {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        doThrow(new RuntimeException("Database error")).when(gameDao).saveGame(any());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            emergencyService.pauseGame(gameId, emergencyReason, adminPlayerId));
        
        assertEquals("Failed to save emergency pause state", exception.getMessage());
    }

    // ==================== resumeGame Tests ====================

    @Test
    void resumeGame_Success_ValidAdminRequest() throws Exception {
        // Arrange
        testGame.setEmergencyPause(true);
        testGame.setEmergencyReason(emergencyReason);
        testGame.setEmergencyTimestamp(Instant.now().toString());
        testGame.setEmergencyTriggeredBy(adminPlayerId);
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act
        Game result = emergencyService.resumeGame(gameId, adminPlayerId);

        // Assert
        assertFalse(result.getEmergencyPause());
        assertNull(result.getEmergencyReason());
        assertNull(result.getEmergencyTimestamp());
        assertNull(result.getEmergencyTriggeredBy());

        verify(gameDao).saveGame(result);
        verify(notificationService, times(3)).sendNotification(any(Notification.class));
    }

    @Test
    void resumeGame_FailsValidation_NullGameId() {
        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () ->
            emergencyService.resumeGame(null, adminPlayerId));
        
        assertEquals("Game ID cannot be null or empty", exception.getMessage());
    }

    @Test
    void resumeGame_FailsValidation_EmptyRequestingPlayerId() {
        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () ->
            emergencyService.resumeGame(gameId, ""));
        
        assertEquals("Requesting player ID cannot be null or empty", exception.getMessage());
    }

    @Test
    void resumeGame_FailsNotFound_GameDoesNotExist() {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.empty());

        // Act & Assert
        GameNotFoundException exception = assertThrows(GameNotFoundException.class, () ->
            emergencyService.resumeGame(gameId, adminPlayerId));
        
        assertEquals("Game not found with ID: " + gameId, exception.getMessage());
    }

    @Test
    void resumeGame_FailsAuthorization_NonAdminPlayer() {
        // Arrange
        testGame.setEmergencyPause(true);
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act & Assert
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
            emergencyService.resumeGame(gameId, nonAdminPlayerId));
        
        assertEquals("Only game administrators can resume emergency paused games", exception.getMessage());
    }

    @Test
    void resumeGame_FailsGameState_NotInEmergencyPause() {
        // Arrange
        testGame.setEmergencyPause(false);
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act & Assert
        GameStateException exception = assertThrows(GameStateException.class, () ->
            emergencyService.resumeGame(gameId, adminPlayerId));
        
        assertEquals("Game " + gameId + " is not currently in emergency pause mode", exception.getMessage());
    }

    @Test
    void resumeGame_SendsNotificationToAllPlayers() throws Exception {
        // Arrange
        testGame.setEmergencyPause(true);
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act
        emergencyService.resumeGame(gameId, adminPlayerId);

        // Assert - Verify notifications sent to all 3 players
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, times(3)).sendNotification(notificationCaptor.capture());

        for (Notification notification : notificationCaptor.getAllValues()) {
            assertEquals("EMERGENCY_RESUME", notification.getType());
            assertEquals("Game has been resumed from emergency pause", notification.getMessage());
            assertEquals(gameId, notification.getGameId());
            assertFalse(notification.isRead());
            assertNotNull(notification.getTimestamp());
            assertTrue(testGame.getPlayerIDs().contains(notification.getRecipientPlayerId()));
        }
    }

    // ==================== getEmergencyStatus Tests ====================

    @Test
    void getEmergencyStatus_Success_GameInEmergencyPause() throws Exception {
        // Arrange
        String timestamp = Instant.now().toString();
        testGame.setEmergencyPause(true);
        testGame.setEmergencyReason(emergencyReason);
        testGame.setEmergencyTimestamp(timestamp);
        testGame.setEmergencyTriggeredBy(adminPlayerId);
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act
        EmergencyService.EmergencyStatus status = emergencyService.getEmergencyStatus(gameId);

        // Assert
        assertEquals(gameId, status.getGameId());
        assertTrue(status.isInEmergencyPause());
        assertEquals(emergencyReason, status.getReason());
        assertEquals(timestamp, status.getTimestamp());
        assertEquals(adminPlayerId, status.getTriggeredBy());
    }

    @Test
    void getEmergencyStatus_Success_GameNotInEmergencyPause() throws Exception {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act
        EmergencyService.EmergencyStatus status = emergencyService.getEmergencyStatus(gameId);

        // Assert
        assertEquals(gameId, status.getGameId());
        assertFalse(status.isInEmergencyPause());
        assertNull(status.getReason());
        assertNull(status.getTimestamp());
        assertNull(status.getTriggeredBy());
    }

    @Test
    void getEmergencyStatus_FailsValidation_NullGameId() {
        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class, () ->
            emergencyService.getEmergencyStatus(null));
        
        assertEquals("Game ID cannot be null or empty", exception.getMessage());
    }

    @Test
    void getEmergencyStatus_FailsNotFound_GameDoesNotExist() {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.empty());

        // Act & Assert
        GameNotFoundException exception = assertThrows(GameNotFoundException.class, () ->
            emergencyService.getEmergencyStatus(gameId));
        
        assertEquals("Game not found with ID: " + gameId, exception.getMessage());
    }

    // ==================== isGameInEmergencyPause Tests ====================

    @Test
    void isGameInEmergencyPause_ReturnsTrue_WhenGameInEmergencyPause() {
        // Arrange
        testGame.setEmergencyPause(true);
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act & Assert
        assertTrue(emergencyService.isGameInEmergencyPause(gameId));
    }

    @Test
    void isGameInEmergencyPause_ReturnsFalse_WhenGameNotInEmergencyPause() {
        // Arrange
        testGame.setEmergencyPause(false);
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act & Assert
        assertFalse(emergencyService.isGameInEmergencyPause(gameId));
    }

    @Test
    void isGameInEmergencyPause_ReturnsFalse_WhenGameNotFound() {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.empty());

        // Act & Assert
        assertFalse(emergencyService.isGameInEmergencyPause(gameId));
    }

    @Test
    void isGameInEmergencyPause_ReturnsFalse_WhenExceptionOccurs() {
        // Arrange
        when(gameDao.getGameById(gameId)).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertFalse(emergencyService.isGameInEmergencyPause(gameId));
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    void pauseGame_HandlesNotificationFailureGracefully() throws Exception {
        // Arrange
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));
        doThrow(new RuntimeException("Notification failed")).when(notificationService).sendNotification(any());

        // Act - Should not throw exception despite notification failure
        Game result = emergencyService.pauseGame(gameId, emergencyReason, adminPlayerId);

        // Assert
        assertTrue(result.getEmergencyPause());
        verify(gameDao).saveGame(result);
        verify(notificationService, times(3)).sendNotification(any(Notification.class));
    }

    @Test
    void pauseGame_HandlesEmptyPlayerList() throws Exception {
        // Arrange
        testGame.setPlayerIDs(null);
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act
        Game result = emergencyService.pauseGame(gameId, emergencyReason, adminPlayerId);

        // Assert
        assertTrue(result.getEmergencyPause());
        verify(gameDao).saveGame(result);
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void pauseGame_TrimsWhitespaceFromReason() throws Exception {
        // Arrange
        String reasonWithWhitespace = "  " + emergencyReason + "  ";
        when(gameDao.getGameById(gameId)).thenReturn(Optional.of(testGame));

        // Act
        Game result = emergencyService.pauseGame(gameId, reasonWithWhitespace, adminPlayerId);

        // Assert
        assertEquals(emergencyReason, result.getEmergencyReason());
    }

    // ==================== Constructor Tests ====================

    @Test
    void constructor_DefaultConstructor_InitializesSuccessfully() {
        // Note: We cannot test the default constructor directly because it requires
        // environment variables for DynamoDB table names. This test would require
        // integration test setup.
        // Act & Assert - Test the constructor with mocked dependencies instead
        EmergencyService service = new EmergencyService(gameDao, playerDao, notificationService);
        assertNotNull(service);
    }

    @Test
    void constructor_ThrowsException_WhenGameDaoIsNull() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
            new EmergencyService(null, playerDao, notificationService));
    }

    @Test
    void constructor_ThrowsException_WhenPlayerDaoIsNull() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
            new EmergencyService(gameDao, null, notificationService));
    }

    @Test
    void constructor_ThrowsException_WhenNotificationServiceIsNull() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
            new EmergencyService(gameDao, playerDao, null));
    }

    // ==================== EmergencyStatus Inner Class Tests ====================

    @Test
    void emergencyStatus_ToStringMethod_ReturnsExpectedFormat() {
        // Arrange
        String timestamp = Instant.now().toString();
        EmergencyService.EmergencyStatus status = new EmergencyService.EmergencyStatus(
            gameId, true, emergencyReason, timestamp, adminPlayerId);

        // Act
        String result = status.toString();

        // Assert
        assertTrue(result.contains(gameId));
        assertTrue(result.contains("true"));
        assertTrue(result.contains(emergencyReason));
        assertTrue(result.contains(timestamp));
        assertTrue(result.contains(adminPlayerId));
    }
} 