package com.assassin.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.dao.TargetAssignmentDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GameStateException;
import com.assassin.exception.PersistenceException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.PlayerPersistenceException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Game;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;
import com.assassin.model.TargetAssignment;

/**
 * Service responsible for managing target assignments in assassin games.
 * Handles initial target assignment, reassignment after eliminations, and target retrieval.
 * 
 * Implements a circular chain assignment algorithm where each player targets the next player
 * in a shuffled list, with the last player targeting the first (creating a closed loop).
 * 
 * Enhanced with comprehensive assignment tracking using TargetAssignmentDao for audit trails
 * and advanced querying capabilities.
 */
public class TargetAssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(TargetAssignmentService.class);
    
    private final PlayerDao playerDao;
    private final GameDao gameDao;
    private final TargetAssignmentDao targetAssignmentDao;

    /**
     * Default constructor for dependency injection frameworks.
     */
    public TargetAssignmentService() {
        this.playerDao = null;
        this.gameDao = null;
        this.targetAssignmentDao = null;
    }

    /**
     * Constructor with dependencies.
     *
     * @param playerDao DAO for player operations
     * @param gameDao DAO for game operations
     */
    public TargetAssignmentService(PlayerDao playerDao, GameDao gameDao) {
        this.playerDao = playerDao;
        this.gameDao = gameDao;
        this.targetAssignmentDao = null;
    }

    /**
     * Enhanced constructor with full dependency injection including TargetAssignmentDao.
     *
     * @param playerDao DAO for player operations
     * @param gameDao DAO for game operations
     * @param targetAssignmentDao DAO for target assignment tracking and audit trails
     */
    public TargetAssignmentService(PlayerDao playerDao, GameDao gameDao, TargetAssignmentDao targetAssignmentDao) {
        this.playerDao = playerDao;
        this.gameDao = gameDao;
        this.targetAssignmentDao = targetAssignmentDao;
    }

    /**
     * Performs initial target assignment for all active players in a game.
     * Uses a circular chain algorithm where players are shuffled and each targets the next.
     * Creates comprehensive assignment records for tracking and audit purposes.
     *
     * @param gameId The ID of the game to assign targets for
     * @throws GameNotFoundException if the game is not found
     * @throws GameStateException if the game is not in a valid state for target assignment
     * @throws PlayerPersistenceException if there's an error saving player target assignments
     */
    public void assignInitialTargets(String gameId) throws GameNotFoundException, GameStateException, PlayerPersistenceException {
        logger.info("Assigning initial targets for game ID: {}", gameId);

        // 1. Fetch the game
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found with ID: " + gameId));

        // 2. Get active players for the game
        List<Player> activePlayers = getActivePlayersForGame(game);

        // 3. Validate minimum player count
        if (activePlayers.size() < 2) {
            throw new GameStateException("Game " + gameId + " requires at least 2 active players for target assignment. Found: " + activePlayers.size());
        }

        // 4. Perform circular chain assignment with enhanced tracking
        assignTargetsInCircularChainWithTracking(gameId, activePlayers);

        logger.info("Successfully assigned initial targets for {} players in game {}", activePlayers.size(), gameId);
    }

    /**
     * Reassigns targets after a player elimination.
     * When a player is eliminated, their assassin inherits their target.
     * Creates comprehensive assignment records for tracking and audit purposes.
     *
     * @param gameId The ID of the game
     * @param eliminatedPlayerId The ID of the eliminated player
     * @param assassinPlayerId The ID of the player who made the elimination
     * @throws GameNotFoundException if the game is not found
     * @throws PlayerNotFoundException if either player is not found
     * @throws PlayerPersistenceException if there's an error saving the reassignment
     */
    public void reassignTargetAfterElimination(String gameId, String eliminatedPlayerId, String assassinPlayerId) 
            throws GameNotFoundException, PlayerNotFoundException, PlayerPersistenceException {
        logger.info("Reassigning targets after elimination in game {}: {} eliminated by {}", 
                   gameId, eliminatedPlayerId, assassinPlayerId);

        // 1. Validate game exists
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found with ID: " + gameId));

        // 2. Fetch the eliminated player to get their target
        Player eliminatedPlayer = playerDao.getPlayerById(eliminatedPlayerId)
                .orElseThrow(() -> new PlayerNotFoundException("Eliminated player not found: " + eliminatedPlayerId));

        // 3. Fetch the assassin player
        Player assassinPlayer = playerDao.getPlayerById(assassinPlayerId)
                .orElseThrow(() -> new PlayerNotFoundException("Assassin player not found: " + assassinPlayerId));

        // 4. Complete the assassin's current assignment if tracking is enabled
        if (targetAssignmentDao != null) {
            try {
                Optional<TargetAssignment> currentAssignment = targetAssignmentDao.getCurrentAssignmentForPlayer(gameId, assassinPlayerId);
                if (currentAssignment.isPresent()) {
                    currentAssignment.get().markCompleted();
                    targetAssignmentDao.saveAssignment(currentAssignment.get());
                    logger.debug("Marked assignment {} as completed", currentAssignment.get().getAssignmentId());
                }
            } catch (PersistenceException e) {
                logger.warn("Failed to update assignment tracking for completed assignment: {}", e.getMessage());
                // Don't fail the reassignment for tracking issues
            }
        }

        // 5. Get the eliminated player's target
        String newTargetId = eliminatedPlayer.getTargetID();
        String newTargetName = eliminatedPlayer.getTargetName();
        String previousAssignmentId = null;

        // 6. Handle edge case: if the eliminated player was targeting their assassin
        if (assassinPlayerId.equals(newTargetId)) {
            logger.info("Edge case detected: eliminated player {} was targeting their assassin {}. Checking for remaining players.", 
                       eliminatedPlayerId, assassinPlayerId);
            
            // Find a new target for the assassin from remaining active players
            newTargetId = findAlternativeTarget(gameId, assassinPlayerId, eliminatedPlayerId);
            if (newTargetId != null) {
                Player newTargetPlayer = playerDao.getPlayerById(newTargetId).orElse(null);
                newTargetName = newTargetPlayer != null ? newTargetPlayer.getPlayerName() : null;
            }
        }

        // 7. Update the assassin's target
        if (newTargetId != null) {
            // Store previous assignment ID for tracking
            if (targetAssignmentDao != null) {
                try {
                    Optional<TargetAssignment> prevAssignment = targetAssignmentDao.getCurrentAssignmentForPlayer(gameId, assassinPlayerId);
                    previousAssignmentId = prevAssignment.map(TargetAssignment::getAssignmentId).orElse(null);
                } catch (PersistenceException e) {
                    logger.warn("Failed to retrieve previous assignment for tracking: {}", e.getMessage());
                }
            }

            assassinPlayer.setTargetID(newTargetId);
            assassinPlayer.setTargetName(newTargetName);
            
            try {
                playerDao.savePlayer(assassinPlayer);
                logger.info("Successfully reassigned target for assassin {}: new target is {}", 
                           assassinPlayerId, newTargetId);

                // Create new assignment record for tracking
                if (targetAssignmentDao != null) {
                    try {
                        TargetAssignment newAssignment = TargetAssignment.createReassignment(
                            gameId, assassinPlayerId, newTargetId, previousAssignmentId);
                        targetAssignmentDao.saveAssignment(newAssignment);
                        logger.debug("Created reassignment tracking record: {}", newAssignment.getAssignmentId());
                    } catch (PersistenceException e) {
                        logger.warn("Failed to create assignment tracking record: {}", e.getMessage());
                        // Don't fail the reassignment for tracking issues
                    }
                }
            } catch (PlayerPersistenceException e) {
                logger.error("Failed to save target reassignment for assassin {}: {}", assassinPlayerId, e.getMessage(), e);
                throw e;
            }
        } else {
            logger.info("No target reassignment needed - game may be ending with assassin {} as potential winner", assassinPlayerId);
        }

        // 8. Mark eliminated player's assignment as cancelled and clear their target
        if (targetAssignmentDao != null) {
            try {
                Optional<TargetAssignment> eliminatedAssignment = targetAssignmentDao.getCurrentAssignmentForPlayer(gameId, eliminatedPlayerId);
                if (eliminatedAssignment.isPresent()) {
                    eliminatedAssignment.get().markCancelled();
                    targetAssignmentDao.saveAssignment(eliminatedAssignment.get());
                    logger.debug("Marked eliminated player's assignment {} as cancelled", eliminatedAssignment.get().getAssignmentId());
                }
            } catch (PersistenceException e) {
                logger.warn("Failed to update assignment tracking for eliminated player: {}", e.getMessage());
            }
        }

        eliminatedPlayer.setTargetID(null);
        eliminatedPlayer.setTargetName(null);
        
        try {
            playerDao.savePlayer(eliminatedPlayer);
            logger.debug("Cleared target assignment for eliminated player {}", eliminatedPlayerId);
        } catch (PlayerPersistenceException e) {
            logger.warn("Failed to clear target for eliminated player {}: {}", eliminatedPlayerId, e.getMessage());
            // Don't throw - this is cleanup and shouldn't fail the reassignment
        }
    }

    /**
     * Retrieves the current target for a specific player.
     * Enhanced with assignment tracking information when available.
     *
     * @param playerId The ID of the player
     * @return The target player information, or empty if no target assigned
     * @throws PlayerNotFoundException if the player is not found
     */
    public Optional<TargetInfo> getCurrentTarget(String playerId) throws PlayerNotFoundException {
        logger.debug("Retrieving current target for player {}", playerId);

        Player player = playerDao.getPlayerById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + playerId));

        String targetId = player.getTargetID();
        if (targetId == null || targetId.trim().isEmpty()) {
            logger.debug("No target assigned to player {}", playerId);
            return Optional.empty();
        }

        // Fetch target player details
        Optional<Player> targetPlayer = playerDao.getPlayerById(targetId);
        if (targetPlayer.isEmpty()) {
            logger.warn("Target player {} not found for player {}", targetId, playerId);
            return Optional.empty();
        }

        // Enhanced target info with assignment tracking if available
        String assignmentId = null;
        String assignmentDate = null;
        String assignmentType = null;

        if (targetAssignmentDao != null) {
            try {
                Optional<TargetAssignment> assignment = targetAssignmentDao.getCurrentAssignmentForPlayer(player.getGameID(), playerId);
                if (assignment.isPresent()) {
                    assignmentId = assignment.get().getAssignmentId();
                    assignmentDate = assignment.get().getAssignmentDate();
                    assignmentType = assignment.get().getAssignmentType();
                }
            } catch (PersistenceException e) {
                logger.warn("Failed to retrieve assignment tracking info for player {}: {}", playerId, e.getMessage());
            }
        }

        TargetInfo targetInfo = new TargetInfo(
            targetPlayer.get().getPlayerID(),
            targetPlayer.get().getPlayerName(),
            targetPlayer.get().getStatus(),
            assignmentId,
            assignmentDate,
            assignmentType
        );

        logger.debug("Found target for player {}: {}", playerId, targetInfo.getTargetId());
        return Optional.of(targetInfo);
    }

    /**
     * Retrieves assignment history for a player in a specific game.
     * Requires TargetAssignmentDao to be configured.
     *
     * @param gameId The ID of the game
     * @param playerId The ID of the player
     * @return List of assignment history, empty if tracking not enabled
     */
    public List<TargetAssignment> getAssignmentHistory(String gameId, String playerId) {
        if (targetAssignmentDao == null) {
            logger.debug("Assignment tracking not enabled - returning empty history");
            return new ArrayList<>();
        }

        try {
            return targetAssignmentDao.getAssignmentHistoryForPlayer(gameId, playerId);
        } catch (PersistenceException e) {
            logger.error("Failed to retrieve assignment history for player {} in game {}: {}", playerId, gameId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Retrieves all active assignments for a game.
     * Requires TargetAssignmentDao to be configured.
     *
     * @param gameId The ID of the game
     * @return List of active assignments, empty if tracking not enabled
     */
    public List<TargetAssignment> getActiveAssignments(String gameId) {
        if (targetAssignmentDao == null) {
            logger.debug("Assignment tracking not enabled - returning empty list");
            return new ArrayList<>();
        }

        try {
            return targetAssignmentDao.getActiveAssignmentsForGame(gameId);
        } catch (PersistenceException e) {
            logger.error("Failed to retrieve active assignments for game {}: {}", gameId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Validates that target assignments form a proper circular chain.
     * Enhanced with assignment tracking validation when available.
     *
     * @param gameId The ID of the game to validate
     * @return ValidationResult containing validation status and any issues found
     * @throws GameNotFoundException if the game is not found
     */
    public ValidationResult validateTargetChain(String gameId) throws GameNotFoundException {
        logger.debug("Validating target chain for game {}", gameId);

        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found with ID: " + gameId));

        List<Player> activePlayers = getActivePlayersForGame(game);
        List<String> issues = new ArrayList<>();

        // Validate player-based target chain
        validateCircularChainIntegrity(activePlayers, issues);

        // Enhanced validation with assignment tracking if available
        if (targetAssignmentDao != null) {
            try {
                List<String> trackingIssues = targetAssignmentDao.validateAssignmentIntegrity(gameId);
                issues.addAll(trackingIssues);
            } catch (PersistenceException e) {
                issues.add("Failed to validate assignment tracking integrity: " + e.getMessage());
            }
        }

        boolean isValid = issues.isEmpty();
        ValidationResult result = new ValidationResult(isValid, issues);
        
        logger.debug("Target chain validation for game {} completed. Valid: {}, Issues: {}", 
                    gameId, isValid, issues.size());
        
        return result;
    }

    // --- Private Helper Methods ---

    private List<Player> getActivePlayersForGame(Game game) {
        List<String> playerIds = game.getPlayerIDs();
        if (playerIds == null || playerIds.isEmpty()) {
            logger.warn("No players found for game {}", game.getGameID());
            return new ArrayList<>();
        }

        List<Player> activePlayers = new ArrayList<>();
        for (String playerId : playerIds) {
            try {
                Optional<Player> player = playerDao.getPlayerById(playerId);
                if (player.isPresent() && PlayerStatus.ACTIVE.name().equalsIgnoreCase(player.get().getStatus())) {
                    activePlayers.add(player.get());
                }
            } catch (RuntimeException e) {
                logger.warn("Error fetching player {}: {}", playerId, e.getMessage());
            }
        }

        logger.debug("Found {} active players for game {}", activePlayers.size(), game.getGameID());
        return activePlayers;
    }

    private void assignTargetsInCircularChain(List<Player> players) throws PlayerPersistenceException {
        // Shuffle players for randomness
        List<Player> shuffledPlayers = new ArrayList<>(players);
        Collections.shuffle(shuffledPlayers);

        logger.debug("Assigning targets in circular chain for {} players", shuffledPlayers.size());

        // Assign each player to target the next player in the shuffled list
        for (int i = 0; i < shuffledPlayers.size(); i++) {
            Player assassin = shuffledPlayers.get(i);
            Player target = shuffledPlayers.get((i + 1) % shuffledPlayers.size()); // Circular: last targets first

            assassin.setTargetID(target.getPlayerID());
            assassin.setTargetName(target.getPlayerName());

            try {
                playerDao.savePlayer(assassin);
                logger.debug("Assigned target {} to player {}", target.getPlayerID(), assassin.getPlayerID());
            } catch (PlayerPersistenceException e) {
                logger.error("Failed to save target assignment for player {}: {}", assassin.getPlayerID(), e.getMessage(), e);
                throw e;
            }
        }
    }

    /**
     * Enhanced version of assignTargetsInCircularChain that also creates assignment tracking records.
     */
    private void assignTargetsInCircularChainWithTracking(String gameId, List<Player> players) throws PlayerPersistenceException {
        // First, assign targets using the standard method
        assignTargetsInCircularChain(players);

        // Then, create assignment tracking records if tracking is enabled
        if (targetAssignmentDao != null) {
            logger.debug("Creating assignment tracking records for {} players", players.size());
            
            for (Player player : players) {
                try {
                    TargetAssignment assignment = TargetAssignment.createInitialAssignment(
                        gameId, player.getPlayerID(), player.getTargetID());
                    targetAssignmentDao.saveAssignment(assignment);
                    logger.debug("Created initial assignment tracking record: {}", assignment.getAssignmentId());
                } catch (PersistenceException e) {
                    logger.warn("Failed to create assignment tracking record for player {}: {}", 
                               player.getPlayerID(), e.getMessage());
                    // Don't fail the assignment for tracking issues
                }
            }
        }
    }

    private String findAlternativeTarget(String gameId, String assassinPlayerId, String eliminatedPlayerId) {
        try {
            Game game = gameDao.getGameById(gameId).orElse(null);
            if (game == null) return null;

            List<Player> activePlayers = getActivePlayersForGame(game);
            
            // Find a player who is not the assassin and not the eliminated player
            for (Player player : activePlayers) {
                String playerId = player.getPlayerID();
                if (!playerId.equals(assassinPlayerId) && !playerId.equals(eliminatedPlayerId)) {
                    logger.debug("Found alternative target {} for assassin {}", playerId, assassinPlayerId);
                    return playerId;
                }
            }
            
            logger.debug("No alternative target found for assassin {}", assassinPlayerId);
            return null;
        } catch (RuntimeException e) {
            logger.error("Error finding alternative target for assassin {}: {}", assassinPlayerId, e.getMessage(), e);
            return null;
        }
    }

    private void validateCircularChainIntegrity(List<Player> players, List<String> issues) {
        if (players.size() < 2) {
            issues.add("Insufficient players for circular chain validation: " + players.size());
            return;
        }

        // Check that each player has a target and that targets form a complete chain
        for (Player player : players) {
            String targetId = player.getTargetID();
            if (targetId == null || targetId.trim().isEmpty()) {
                issues.add("Player " + player.getPlayerID() + " has no target assigned");
                continue;
            }

            // Verify target exists in the active player list
            boolean targetFound = players.stream()
                    .anyMatch(p -> p.getPlayerID().equals(targetId));
            
            if (!targetFound) {
                issues.add("Player " + player.getPlayerID() + " targets non-existent or inactive player: " + targetId);
            }
        }

        // Additional validation could include checking for circular chain completeness
        // (i.e., following the chain should eventually return to the starting player)
    }

    // --- Enhanced Data Classes ---

    /**
     * Enhanced TargetInfo class with assignment tracking information.
     */
    public static class TargetInfo {
        private final String targetId;
        private final String targetName;
        private final String targetStatus;
        private final String assignmentId;
        private final String assignmentDate;
        private final String assignmentType;

        public TargetInfo(String targetId, String targetName, String targetStatus) {
            this(targetId, targetName, targetStatus, null, null, null);
        }

        public TargetInfo(String targetId, String targetName, String targetStatus, 
                         String assignmentId, String assignmentDate, String assignmentType) {
            this.targetId = targetId;
            this.targetName = targetName;
            this.targetStatus = targetStatus;
            this.assignmentId = assignmentId;
            this.assignmentDate = assignmentDate;
            this.assignmentType = assignmentType;
        }

        public String getTargetId() { return targetId; }
        public String getTargetName() { return targetName; }
        public String getTargetStatus() { return targetStatus; }
        public String getAssignmentId() { return assignmentId; }
        public String getAssignmentDate() { return assignmentDate; }
        public String getAssignmentType() { return assignmentType; }

        @Override
        public String toString() {
            return String.format("TargetInfo{targetId='%s', targetName='%s', targetStatus='%s', assignmentId='%s', assignmentDate='%s', assignmentType='%s'}", 
                               targetId, targetName, targetStatus, assignmentId, assignmentDate, assignmentType);
        }
    }

    /**
     * Validation result class for target chain integrity checks.
     */
    public static class ValidationResult {
        private final boolean isValid;
        private final List<String> issues;

        public ValidationResult(boolean isValid, List<String> issues) {
            this.isValid = isValid;
            this.issues = new ArrayList<>(issues);
        }

        public boolean isValid() { return isValid; }
        public List<String> getIssues() { return new ArrayList<>(issues); }

        @Override
        public String toString() {
            return String.format("ValidationResult{isValid=%s, issues=%s}", isValid, issues);
        }
    }
} 