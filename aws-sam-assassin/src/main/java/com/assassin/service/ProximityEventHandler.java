package com.assassin.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.model.Game;
import com.assassin.model.GameStatus;
import com.assassin.model.Notification;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;

/**
 * Handles proximity-based events between players, including elimination attempts.
 */
public class ProximityEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(ProximityEventHandler.class);
    
    private final PlayerDao playerDao;
    private final GameDao gameDao;
    private final ProximityDetectionService proximityService;
    private final NotificationService notificationService;
    
    /**
     * Enum defining different types of proximity events
     */
    public enum ProximityEventType {
        /** Target player is within elimination range */
        TARGET_IN_RANGE,
        /** Hunter is within elimination range */
        HUNTER_IN_RANGE,
        /** Players are getting closer to each other */
        PLAYERS_APPROACHING,
        /** Players are moving away from each other */
        PLAYERS_SEPARATING
    }
    
    /**
     * Constructor with dependencies
     */
    public ProximityEventHandler(PlayerDao playerDao, GameDao gameDao, 
                                ProximityDetectionService proximityService,
                                NotificationService notificationService) {
        this.playerDao = playerDao;
        this.gameDao = gameDao;
        this.proximityService = proximityService;
        this.notificationService = notificationService;
    }
    
    /**
     * Default constructor for Lambda initialization
     */
    public ProximityEventHandler() {
        this.playerDao = new DynamoDbPlayerDao();
        this.gameDao = new DynamoDbGameDao();
        this.proximityService = new ProximityDetectionService();
        this.notificationService = new NotificationService();
    }
    
    /**
     * Process an elimination attempt from hunter to target
     * 
     * @param gameId Game identifier
     * @param hunterId Hunter player identifier
     * @param targetId Target player identifier
     * @param weaponType Type of weapon used
     * @return true if elimination was successful
     */
    public boolean processEliminationAttempt(String gameId, String hunterId, String targetId, String weaponType) {
        logger.info("Processing elimination attempt in game {} - hunter: {}, target: {}, weapon: {}", 
                gameId, hunterId, targetId, weaponType);
        
        // Check game status
        Optional<Game> gameOpt = gameDao.getGameById(gameId);
        if (!gameOpt.isPresent() || !GameStatus.ACTIVE.name().equalsIgnoreCase(gameOpt.get().getStatus())) {
            logger.warn("Elimination attempt failed: Game {} not found or not active", gameId);
            Notification failNotification = new Notification(hunterId, "ELIMINATION_FAILED", "The game is not active.", null);
            notificationService.sendNotification(failNotification);
            return false;
        }
        
        // Check hunter status
        Optional<Player> hunterOpt = playerDao.getPlayerById(hunterId);
        if (!hunterOpt.isPresent() || !PlayerStatus.ACTIVE.name().equalsIgnoreCase(hunterOpt.get().getStatus())) {
            logger.warn("Elimination attempt failed: Hunter {} not found or not active", hunterId);
            Notification failNotification = new Notification(hunterId, "ELIMINATION_FAILED", "Your player status is not active.", null);
            notificationService.sendNotification(failNotification);
            return false;
        }
        
        Player hunter = hunterOpt.get();
        
        // Validate the target is actually the hunter's assigned target
        if (!targetId.equals(hunter.getTargetID())) {
            logger.warn("Elimination attempt failed: Target {} is not the assigned target for hunter {}", 
                    targetId, hunterId);
            Notification failNotification = new Notification(hunterId, "ELIMINATION_FAILED", "This is not your assigned target.", null);
            notificationService.sendNotification(failNotification);
            return false;
        }
        
        // Check target status
        Optional<Player> targetOpt = playerDao.getPlayerById(targetId);
        if (!targetOpt.isPresent() || !PlayerStatus.ACTIVE.name().equalsIgnoreCase(targetOpt.get().getStatus())) {
            logger.warn("Elimination attempt failed: Target {} not found or not active", targetId);
            Notification failNotification = new Notification(hunterId, "ELIMINATION_FAILED", "The target is not active.", null);
            notificationService.sendNotification(failNotification);
            return false;
        }
        
        Player target = targetOpt.get();
        
        // Check proximity
        try {
            if (!proximityService.canEliminateTarget(gameId, hunterId, targetId, weaponType)) {
                logger.info("Elimination attempt failed: Hunter {} not in range of target {}", hunterId, targetId);
                Notification failNotification = new Notification(hunterId, "ELIMINATION_FAILED", "You are not close enough to your target.", null);
                notificationService.sendNotification(failNotification);
                return false;
            }
        } catch (PlayerNotFoundException | GameNotFoundException e) {
            logger.info("Elimination attempt failed: Hunter {} not in range of target {}", hunterId, targetId);
            Notification failNotification = new Notification(hunterId, "ELIMINATION_FAILED", "Could not check proximity: " + e.getMessage(), null);
            notificationService.sendNotification(failNotification);
            return false;
        }
        
        // Process elimination
        performElimination(gameId, hunter, target);
        return true;
    }
    
    /**
     * Update player statuses and assignments after a successful elimination
     */
    private void performElimination(String gameId, Player hunter, Player target) {
        logger.info("Performing elimination in game {} - hunter: {}, target: {}", 
                gameId, hunter.getPlayerID(), target.getPlayerID());
        
        String targetId = target.getPlayerID();
        String targetNextTargetId = target.getTargetID();
        
        // Mark target as eliminated
        target.setStatus(PlayerStatus.DEAD.name());
        logger.info("Marking player {} as eliminated by {}", targetId, hunter.getPlayerID());
        playerDao.savePlayer(target);
        
        // Update hunter's target to the target's target
        hunter.setTargetID(targetNextTargetId);
        hunter.setKillCount(hunter.getKillCount() + 1);
        playerDao.savePlayer(hunter);
        
        // Update the chain - target's target now has a new hunter
        if (targetNextTargetId != null) {
            Optional<Player> nextTargetOpt = playerDao.getPlayerById(targetNextTargetId);
            if (nextTargetOpt.isPresent()) {
                Player nextTarget = nextTargetOpt.get();
                logger.info("Player {} is now hunting player {}", hunter.getPlayerID(), nextTarget.getPlayerID());
                playerDao.savePlayer(nextTarget);
                
                // Notify the new target
                Notification newHunterNotification = new Notification(nextTarget.getPlayerID(), 
                        "NEW_HUNTER", 
                        "You have a new hunter! Your previous hunter was eliminated.", 
                        Map.of("newHunterId", hunter.getPlayerID()));
                notificationService.sendNotification(newHunterNotification);
            }
        }
        
        // Send notifications
        Notification hunterSuccessNotification = new Notification(hunter.getPlayerID(), 
                "ELIMINATION_SUCCESS", 
                "You've eliminated " + target.getPlayerName(),
                Map.of("eliminatedTargetId", targetId));
        notificationService.sendNotification(hunterSuccessNotification);
        
        Notification targetEliminatedNotification = new Notification(targetId, 
                "ELIMINATED", 
                "You were eliminated by " + hunter.getPlayerName(),
                Map.of("eliminatedById", hunter.getPlayerID()));
        notificationService.sendNotification(targetEliminatedNotification);
        
        // Check if the game is complete (only one player remaining)
        checkGameCompletion(gameId);
    }
    
    /**
     * Check if the game should be completed (only one player remaining)
     */
    private void checkGameCompletion(String gameId) {
        // Get all active players
        List<Player> allPlayersInGame = playerDao.getPlayersByGameId(gameId);
        List<Player> activePlayers = allPlayersInGame.stream()
                .filter(p -> PlayerStatus.ACTIVE.name().equalsIgnoreCase(p.getStatus()))
                .toList();
        
        // If only one player remains, they're the winner
        if (activePlayers.size() == 1) {
            Player winner = activePlayers.get(0);
            logger.info("Game {} completed. Winner: {}", gameId, winner.getPlayerID());
            
            // Update game status
            Optional<Game> gameOpt = gameDao.getGameById(gameId);
            if (gameOpt.isPresent()) {
                Game game = gameOpt.get();
                game.setStatus(GameStatus.COMPLETED.name());
                gameDao.saveGame(game);
                
                // Notify the winner
                Notification winnerNotification = new Notification(winner.getPlayerID(), 
                        "GAME_WON", 
                        "Congratulations! You are the last player standing! You've won the game!", 
                        null);
                notificationService.sendNotification(winnerNotification);
                
                // Notify all players about the game result
                for (Player player : allPlayersInGame) {
                    Notification gameEndNotification = new Notification(player.getPlayerID(), 
                            "GAME_COMPLETED", 
                            winner.getPlayerName() + " has won the game!",
                            Map.of("winnerId", winner.getPlayerID()));
                    notificationService.sendNotification(gameEndNotification);
                }
            }
        }
    }
} 