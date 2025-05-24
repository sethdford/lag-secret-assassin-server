package com.assassin.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GameStateException;
import com.assassin.exception.UnauthorizedException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Game;
import com.assassin.model.GameStatus;
import com.assassin.model.Notification;
import com.assassin.model.Player;

/**
 * Service for handling emergency game operations such as pausing and resuming games.
 * Provides safety mechanisms to immediately stop game activity when needed.
 */
public class EmergencyService {

    private static final Logger logger = LoggerFactory.getLogger(EmergencyService.class);
    private final GameDao gameDao;
    private final PlayerDao playerDao;
    private final NotificationService notificationService;

    // Default constructor
    public EmergencyService() {
        this(new DynamoDbGameDao(), new DynamoDbPlayerDao(), new NotificationService());
    }

    // Constructor for dependency injection (testing)
    public EmergencyService(GameDao gameDao, PlayerDao playerDao, NotificationService notificationService) {
        this.gameDao = Objects.requireNonNull(gameDao, "gameDao cannot be null");
        this.playerDao = Objects.requireNonNull(playerDao, "playerDao cannot be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService cannot be null");
    }

    /**
     * Pauses a game immediately with an emergency flag.
     * Only game admins and platform administrators can trigger emergency pauses.
     *
     * @param gameId The ID of the game to pause
     * @param reason The reason for the emergency pause
     * @param requestingPlayerId The ID of the player requesting the pause
     * @return The updated Game object with emergency pause status
     * @throws GameNotFoundException If the game is not found
     * @throws UnauthorizedException If the requesting player is not authorized
     * @throws GameStateException If the game is already paused or in an invalid state
     * @throws ValidationException If the input parameters are invalid
     */
    public Game pauseGame(String gameId, String reason, String requestingPlayerId) 
            throws GameNotFoundException, UnauthorizedException, GameStateException, ValidationException {
        
        logger.info("Emergency pause requested for game {} by player {} with reason: {}", 
                   gameId, requestingPlayerId, reason);

        // Validate input parameters
        if (gameId == null || gameId.trim().isEmpty()) {
            throw new ValidationException("Game ID cannot be null or empty");
        }
        if (requestingPlayerId == null || requestingPlayerId.trim().isEmpty()) {
            throw new ValidationException("Requesting player ID cannot be null or empty");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Emergency reason cannot be null or empty");
        }

        // Fetch the game
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found with ID: " + gameId));

        // Authorization check: only game admin can pause (could extend to platform admins)
        if (!Objects.equals(game.getAdminPlayerID(), requestingPlayerId)) {
            logger.warn("Unauthorized emergency pause attempt by player {} for game {} (Admin: {})",
                       requestingPlayerId, gameId, game.getAdminPlayerID());
            throw new UnauthorizedException("Only game administrators can trigger emergency pauses");
        }

        // Check if game is already in emergency pause
        if (Boolean.TRUE.equals(game.getEmergencyPause())) {
            throw new GameStateException("Game " + gameId + " is already in emergency pause mode");
        }

        // Check if game is in a pauseable state
        String currentStatus = game.getStatus();
        if (!GameStatus.ACTIVE.name().equalsIgnoreCase(currentStatus) && 
            !GameStatus.PENDING.name().equalsIgnoreCase(currentStatus)) {
            throw new GameStateException("Game " + gameId + " cannot be paused. Current status: " + currentStatus);
        }

        // Set emergency pause fields
        String timestamp = Instant.now().toString();
        game.setEmergencyPause(true);
        game.setEmergencyReason(reason.trim());
        game.setEmergencyTimestamp(timestamp);
        game.setEmergencyTriggeredBy(requestingPlayerId);

        // Save the updated game
        try {
            gameDao.saveGame(game);
            logger.info("Successfully paused game {} in emergency mode. Reason: {}", gameId, reason);
        } catch (Exception e) {
            logger.error("Failed to save emergency pause for game {}: {}", gameId, e.getMessage(), e);
            throw new RuntimeException("Failed to save emergency pause state", e);
        }

        // Send notifications to all game participants
        notifyAllParticipants(game, "EMERGENCY_PAUSE", 
                             "Game has been paused due to an emergency: " + reason);

        return game;
    }

    /**
     * Resumes a game from emergency pause.
     * Only game admins and platform administrators can resume paused games.
     *
     * @param gameId The ID of the game to resume
     * @param requestingPlayerId The ID of the player requesting the resume
     * @return The updated Game object with emergency pause cleared
     * @throws GameNotFoundException If the game is not found
     * @throws UnauthorizedException If the requesting player is not authorized
     * @throws GameStateException If the game is not paused or in an invalid state
     * @throws ValidationException If the input parameters are invalid
     */
    public Game resumeGame(String gameId, String requestingPlayerId) 
            throws GameNotFoundException, UnauthorizedException, GameStateException, ValidationException {
        
        logger.info("Emergency resume requested for game {} by player {}", gameId, requestingPlayerId);

        // Validate input parameters
        if (gameId == null || gameId.trim().isEmpty()) {
            throw new ValidationException("Game ID cannot be null or empty");
        }
        if (requestingPlayerId == null || requestingPlayerId.trim().isEmpty()) {
            throw new ValidationException("Requesting player ID cannot be null or empty");
        }

        // Fetch the game
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found with ID: " + gameId));

        // Authorization check: only game admin can resume (could extend to platform admins)
        if (!Objects.equals(game.getAdminPlayerID(), requestingPlayerId)) {
            logger.warn("Unauthorized emergency resume attempt by player {} for game {} (Admin: {})",
                       requestingPlayerId, gameId, game.getAdminPlayerID());
            throw new UnauthorizedException("Only game administrators can resume emergency paused games");
        }

        // Check if game is actually in emergency pause
        if (!Boolean.TRUE.equals(game.getEmergencyPause())) {
            throw new GameStateException("Game " + gameId + " is not currently in emergency pause mode");
        }

        // Clear emergency pause fields
        game.setEmergencyPause(false);
        game.setEmergencyReason(null);
        game.setEmergencyTimestamp(null);
        game.setEmergencyTriggeredBy(null);

        // Save the updated game
        try {
            gameDao.saveGame(game);
            logger.info("Successfully resumed game {} from emergency pause", gameId);
        } catch (Exception e) {
            logger.error("Failed to save emergency resume for game {}: {}", gameId, e.getMessage(), e);
            throw new RuntimeException("Failed to save emergency resume state", e);
        }

        // Send notifications to all game participants
        notifyAllParticipants(game, "EMERGENCY_RESUME", 
                             "Game has been resumed from emergency pause");

        return game;
    }

    /**
     * Gets the current emergency status of a game.
     *
     * @param gameId The ID of the game to check
     * @return EmergencyStatus object containing current emergency state
     * @throws GameNotFoundException If the game is not found
     * @throws ValidationException If the game ID is invalid
     */
    public EmergencyStatus getEmergencyStatus(String gameId) 
            throws GameNotFoundException, ValidationException {
        
        logger.debug("Emergency status requested for game {}", gameId);

        // Validate input parameters
        if (gameId == null || gameId.trim().isEmpty()) {
            throw new ValidationException("Game ID cannot be null or empty");
        }

        // Fetch the game
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found with ID: " + gameId));

        return new EmergencyStatus(
            game.getGameID(),
            Boolean.TRUE.equals(game.getEmergencyPause()),
            game.getEmergencyReason(),
            game.getEmergencyTimestamp(),
            game.getEmergencyTriggeredBy()
        );
    }

    /**
     * Checks if a game is currently in emergency pause mode.
     * This is a convenience method for other services to quickly check emergency status.
     *
     * @param gameId The ID of the game to check
     * @return true if the game is in emergency pause, false otherwise
     */
    public boolean isGameInEmergencyPause(String gameId) {
        try {
            Game game = gameDao.getGameById(gameId).orElse(null);
            return game != null && Boolean.TRUE.equals(game.getEmergencyPause());
        } catch (Exception e) {
            logger.warn("Error checking emergency status for game {}: {}", gameId, e.getMessage());
            return false; // Assume not paused if we can't determine status
        }
    }

    /**
     * Sends notifications to all participants in a game.
     *
     * @param game The game whose participants should be notified
     * @param notificationType The type of notification (for categorization)
     * @param message The message to send to participants
     */
    private void notifyAllParticipants(Game game, String notificationType, String message) {
        if (game.getPlayerIDs() == null || game.getPlayerIDs().isEmpty()) {
            logger.warn("No players found to notify for game {}", game.getGameID());
            return;
        }

        logger.info("Sending {} notifications to {} players in game {}", 
                   notificationType, game.getPlayerIDs().size(), game.getGameID());

        for (String playerId : game.getPlayerIDs()) {
            try {
                Notification notification = new Notification();
                notification.setRecipientPlayerId(playerId);
                notification.setType(notificationType);
                notification.setMessage(message);
                notification.setGameId(game.getGameID());
                notification.setTimestamp(Instant.now().toString());
                notification.setRead(false);

                notificationService.sendNotification(notification);
                logger.debug("Sent {} notification to player {}", notificationType, playerId);
            } catch (Exception e) {
                logger.error("Failed to send {} notification to player {} for game {}: {}", 
                           notificationType, playerId, game.getGameID(), e.getMessage(), e);
                // Continue with other players even if one notification fails
            }
        }
    }

    /**
     * Data class representing the emergency status of a game.
     */
    public static class EmergencyStatus {
        private final String gameId;
        private final boolean isInEmergencyPause;
        private final String reason;
        private final String timestamp;
        private final String triggeredBy;

        public EmergencyStatus(String gameId, boolean isInEmergencyPause, String reason, 
                              String timestamp, String triggeredBy) {
            this.gameId = gameId;
            this.isInEmergencyPause = isInEmergencyPause;
            this.reason = reason;
            this.timestamp = timestamp;
            this.triggeredBy = triggeredBy;
        }

        public String getGameId() { return gameId; }
        public boolean isInEmergencyPause() { return isInEmergencyPause; }
        public String getReason() { return reason; }
        public String getTimestamp() { return timestamp; }
        public String getTriggeredBy() { return triggeredBy; }

        @Override
        public String toString() {
            return "EmergencyStatus{" +
                   "gameId='" + gameId + '\'' +
                   ", isInEmergencyPause=" + isInEmergencyPause +
                   ", reason='" + reason + '\'' +
                   ", timestamp='" + timestamp + '\'' +
                   ", triggeredBy='" + triggeredBy + '\'' +
                   '}';
        }
    }
} 