package com.assassin.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbGameZoneStateDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GameStateException;
import com.assassin.exception.PlayerPersistenceException;
import com.assassin.exception.UnauthorizedException; // Assuming this exists for permissions
import com.assassin.exception.ValidationException;
import com.assassin.model.Coordinate; // Import Coordinate
import com.assassin.model.Game;
import com.assassin.model.GameStatus;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus; // Assuming PlayerStatus enum exists

/**
 * Service layer for managing game logic, such as starting games and assigning targets.
 */
public class GameService {

    private static final Logger logger = LoggerFactory.getLogger(GameService.class);
    private final GameDao gameDao;
    private final PlayerDao playerDao;
    private final ShrinkingZoneService shrinkingZoneService;

    // Default constructor
    public GameService() {
        this(new DynamoDbGameDao(), new DynamoDbPlayerDao(), 
             new ShrinkingZoneService(new DynamoDbGameDao(), new DynamoDbGameZoneStateDao(), new DynamoDbPlayerDao()));
    }

    // Constructor for dependency injection (testing)
    public GameService(GameDao gameDao, PlayerDao playerDao) {
        this(gameDao, playerDao, 
             new ShrinkingZoneService(gameDao, new DynamoDbGameZoneStateDao(), playerDao));
    }
    
    // Full constructor for dependency injection
    public GameService(GameDao gameDao, PlayerDao playerDao, ShrinkingZoneService shrinkingZoneService) {
        this.gameDao = Objects.requireNonNull(gameDao, "gameDao cannot be null");
        this.playerDao = Objects.requireNonNull(playerDao, "playerDao cannot be null");
        this.shrinkingZoneService = Objects.requireNonNull(shrinkingZoneService, "shrinkingZoneService cannot be null");
    }

    /**
     * Starts a game, assigns targets to players in a circular chain, and updates game status.
     *
     * @param gameId The ID of the game to start.
     * @throws GameNotFoundException If the game with the given ID does not exist.
     * @throws GameStateException If the game is not in a state that allows starting (e.g., already active or too few players).
     * @throws PlayerPersistenceException If there's an error updating player records.
     */
    public void startGameAndAssignTargets(String gameId) throws GameNotFoundException, GameStateException, PlayerPersistenceException {
        logger.info("Attempting to start game and assign targets for game ID: {}", gameId);

        // 1. Fetch the game
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found with ID: " + gameId));

        // 2. Validate game state
        if (!GameStatus.PENDING.name().equalsIgnoreCase(game.getStatus())) {
            throw new GameStateException("Game " + gameId + " cannot be started. Current status: " + game.getStatus());
        }

        // 3. Fetch player IDs from the game object
        List<String> playerIdsInGame = game.getPlayerIDs();
        if (playerIdsInGame == null) {
            playerIdsInGame = new ArrayList<>(); // Handle null list
        }

        if (playerIdsInGame.size() < 2) {
            throw new GameStateException("Game " + gameId + " requires at least 2 players to start. Found: " + playerIdsInGame.size());
        }

        // 4. Fetch Player objects and filter for ACTIVE status
        // Consider optimizing this for large games (e.g., batch get or GSI query)
        List<Player> activePlayers = new ArrayList<>();
        for (String playerId : playerIdsInGame) {
            Player player = playerDao.getPlayerById(playerId).orElse(null);
            if (player != null && PlayerStatus.ACTIVE.name().equalsIgnoreCase(player.getStatus())) {
                activePlayers.add(player);
            }
        }

        if (activePlayers.size() < 2) {
            throw new GameStateException("Game " + gameId + " requires at least 2 *active* players to start. Found: " + activePlayers.size());
        }

        logger.info("Found {} active players for game {}. Shuffling and assigning targets.", activePlayers.size(), gameId);

        // 5. Shuffle players for random assignment
        Collections.shuffle(activePlayers);

        // 6. Assign targets in a circular chain
        int numPlayers = activePlayers.size();
        for (int i = 0; i < numPlayers; i++) {
            Player currentPlayer = activePlayers.get(i);
            Player targetPlayer = activePlayers.get((i + 1) % numPlayers); // Next player in the shuffled list (wraps around)

            currentPlayer.setTargetID(targetPlayer.getPlayerID());
            currentPlayer.setTargetName(targetPlayer.getPlayerName()); // Optionally set target name for convenience

            // Persist changes for each player
            try {
                playerDao.savePlayer(currentPlayer);
                logger.debug("Assigned target {} ({}) to player {} ({})", 
                           targetPlayer.getPlayerID(), targetPlayer.getPlayerName(), 
                           currentPlayer.getPlayerID(), currentPlayer.getPlayerName());
            } catch (PlayerPersistenceException e) {
                logger.error("Failed to save target assignment for player {}: {}\n{}", currentPlayer.getPlayerID(), e.getMessage(), e);
                // Decide on error handling: continue? rollback? For now, rethrow.
                throw new PlayerPersistenceException("Failed to save target assignment for player " + currentPlayer.getPlayerID(), e);
            }
        }

        // 7. Update game status to ACTIVE
        game.setStatus(GameStatus.ACTIVE.name());
        // Optionally update other game fields like startTime
        // game.setSettings(...); // Example if storing start time in settings
        try {
            gameDao.saveGame(game);
            logger.info("Successfully started game {} and assigned targets.", gameId);
        } catch (Exception e) { // Catch potential DAO exceptions
            logger.error("Failed to update game status to ACTIVE for game {}: {}", gameId, e.getMessage(), e);
            // Consider rollback mechanisms for player updates if game status update fails
            throw new RuntimeException("Failed to update game status after assigning targets.", e);
        }
        
        // 8. Initialize shrinking zone state if enabled for this game
        try {
            shrinkingZoneService.initializeZoneState(game);
            logger.info("Shrinking zone initialization completed for game {}", gameId);
        } catch (GameStateException e) {
            // Log but don't fail the game start if zone initialization fails
            logger.warn("Failed to initialize shrinking zone for game {}: {}", gameId, e.getMessage());
            // Game can still proceed without shrinking zone if configuration is missing
        } catch (Exception e) {
            // Unexpected errors during zone initialization
            logger.error("Unexpected error during shrinking zone initialization for game {}: {}", gameId, e.getMessage(), e);
            // Don't fail the game start for zone initialization errors
        }
    }

    /**
     * Updates the boundary coordinates for a given game.
     *
     * @param gameId The ID of the game to update.
     * @param boundary A list of Coordinate objects defining the new boundary polygon.
     * @param requestingPlayerId The ID of the player requesting the update.
     * @return The updated Game object.
     * @throws GameNotFoundException If the game is not found.
     * @throws ValidationException If the boundary data is invalid or the game cannot be updated.
     * @throws UnauthorizedException If the requesting player is not authorized (e.g., not the admin).
     */
    public Game updateGameBoundary(String gameId, List<Coordinate> boundary, String requestingPlayerId)
            throws GameNotFoundException, ValidationException, UnauthorizedException {

        logger.info("Attempting to update boundary for game ID: {} by player {}", gameId, requestingPlayerId);

        // 1. Fetch the game
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found with ID: " + gameId));

        // 2. Authorization Check: Ensure the requester is the admin of the game
        if (!Objects.equals(game.getAdminPlayerID(), requestingPlayerId)) {
            logger.warn("Unauthorized attempt by player {} to update boundary for game {} (Admin: {})",
                    requestingPlayerId, gameId, game.getAdminPlayerID());
            throw new UnauthorizedException("Only the game admin can update the boundary.");
        }

        // 3. Validation (basic validation already done in handler, add more if needed)
        // E.g., check if game status allows boundary updates (e.g., only PENDING?)
         if (!GameStatus.PENDING.name().equalsIgnoreCase(game.getStatus())) {
             logger.warn("Attempted to update boundary for game {} which is not in PENDING status (Status: {})", gameId, game.getStatus());
             throw new GameStateException("Game boundary can only be updated when the game status is PENDING.");
         }

        // 4. Update the boundary field
        game.setBoundary(boundary);

        // 5. Save the updated game
        try {
            gameDao.saveGame(game);
            logger.info("Successfully updated boundary for game {}.", gameId);
            return game;
        } catch (Exception e) {
            logger.error("Failed to save updated boundary for game {}: {}", gameId, e.getMessage(), e);
            // Consider specific exceptions from DAO
            throw new RuntimeException("Failed to save game with updated boundary.", e);
        }
    }

    // --- Stubs for other Game Management Methods --- 

    public Game createGame(String gameName, String adminPlayerId) throws ValidationException {
        logger.warn("createGame is not fully implemented yet.");
        // TODO: Implement game creation logic (save to DAO)
        throw new UnsupportedOperationException("Game creation not implemented.");
    }

    public List<Game> listGames(String status) {
        logger.warn("listGames is not fully implemented yet.");
        // TODO: Implement game listing logic (query DAO)
        throw new UnsupportedOperationException("Game listing not implemented.");
    }

    public Game getGame(String gameId) throws GameNotFoundException {
        logger.warn("getGame is not fully implemented yet.");
        // TODO: Implement game retrieval logic (call DAO)
        return gameDao.getGameById(gameId)
               .orElseThrow(() -> new GameNotFoundException("Game not found stub: " + gameId));
        // throw new UnsupportedOperationException("Game retrieval not implemented.");
    }

    public Game joinGame(String gameId, String playerId) throws GameNotFoundException, ValidationException {
        logger.warn("joinGame is not fully implemented yet.");
        // TODO: Implement logic to add player ID to game's player list
        throw new UnsupportedOperationException("Joining game not implemented.");
    }

    /**
     * Forces a game to end, updating its status and cleaning up resources.
     * Only the game admin can force end a game.
     * 
     * @param gameId The ID of the game to end
     * @param requestingPlayerId The ID of the player requesting to end the game
     * @return The updated Game object
     * @throws GameNotFoundException if the game is not found
     * @throws ValidationException if the request is invalid
     * @throws UnauthorizedException if the requesting player is not the admin
     */
    public Game forceEndGame(String gameId, String requestingPlayerId) throws GameNotFoundException, ValidationException, UnauthorizedException {
        logger.info("Attempting to force end game {} by player {}", gameId, requestingPlayerId);
        
        // 1. Fetch the game
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found with ID: " + gameId));
        
        // 2. Authorization Check: Ensure the requester is the admin of the game
        if (!Objects.equals(game.getAdminPlayerID(), requestingPlayerId)) {
            logger.warn("Unauthorized attempt by player {} to force end game {} (Admin: {})",
                    requestingPlayerId, gameId, game.getAdminPlayerID());
            throw new UnauthorizedException("Only the game admin can force end a game.");
        }
        
        // 3. Validation: Check if game can be ended
        String currentStatus = game.getStatus();
        if (GameStatus.COMPLETED.name().equalsIgnoreCase(currentStatus) || 
            GameStatus.CANCELLED.name().equalsIgnoreCase(currentStatus)) {
            logger.warn("Attempted to force end game {} which is already ended (Status: {})", gameId, currentStatus);
            throw new ValidationException("Game is already ended with status: " + currentStatus);
        }
        
        // 4. Update game status to CANCELLED
        game.setStatus(GameStatus.CANCELLED.name());
        
        // 5. Save the updated game
        try {
            gameDao.saveGame(game);
            logger.info("Successfully force ended game {}. Status changed to CANCELLED.", gameId);
        } catch (Exception e) {
            logger.error("Failed to save force ended game {}: {}", gameId, e.getMessage(), e);
            throw new RuntimeException("Failed to save game with updated status.", e);
        }
        
        // 6. Cleanup shrinking zone state if it exists
        try {
            shrinkingZoneService.cleanupZoneState(gameId);
            logger.info("Shrinking zone cleanup completed for force ended game {}", gameId);
        } catch (GameNotFoundException e) {
            // Game not found during cleanup (shouldn't happen since we just fetched it)
            logger.warn("Game not found during zone cleanup for game {}: {}", gameId, e.getMessage());
        } catch (Exception e) {
            // Log cleanup errors but don't fail the game ending
            logger.error("Error during shrinking zone cleanup for game {}: {}", gameId, e.getMessage(), e);
            // Game is still successfully ended even if cleanup fails
        }
        
        return game;
    }
    
    /**
     * Ends a game naturally when it reaches completion (e.g., winner declared).
     * Updates game status to COMPLETED and cleans up resources.
     * 
     * @param gameId The ID of the game to complete
     * @param winnerId The ID of the winning player (optional, can be null for tie games)
     * @return The updated Game object
     * @throws GameNotFoundException if the game is not found
     * @throws GameStateException if the game is not in a state that allows completion
     */
    public Game completeGame(String gameId, String winnerId) throws GameNotFoundException, GameStateException {
        logger.info("Attempting to complete game {} with winner {}", gameId, winnerId != null ? winnerId : "none");
        
        // 1. Fetch the game
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found with ID: " + gameId));
        
        // 2. Validation: Check if game can be completed
        String currentStatus = game.getStatus();
        if (!GameStatus.ACTIVE.name().equalsIgnoreCase(currentStatus)) {
            throw new GameStateException("Game " + gameId + " cannot be completed. Current status: " + currentStatus + ". Only ACTIVE games can be completed.");
        }
        
        // 3. Update game status to COMPLETED
        game.setStatus(GameStatus.COMPLETED.name());
        
        // 4. Optionally set winner information in game settings
        // This would depend on how winner information is stored in the Game model
        // For now, we'll log it
        if (winnerId != null) {
            logger.info("Game {} completed with winner: {}", gameId, winnerId);
            // TODO: Store winner information in game record if field exists
            // game.setWinnerId(winnerId);
        } else {
            logger.info("Game {} completed with no winner (tie/draw)", gameId);
        }
        
        // 5. Save the updated game
        try {
            gameDao.saveGame(game);
            logger.info("Successfully completed game {}. Status changed to COMPLETED.", gameId);
        } catch (Exception e) {
            logger.error("Failed to save completed game {}: {}", gameId, e.getMessage(), e);
            throw new RuntimeException("Failed to save game with updated status.", e);
        }
        
        // 6. Cleanup shrinking zone state if it exists
        try {
            shrinkingZoneService.cleanupZoneState(gameId);
            logger.info("Shrinking zone cleanup completed for completed game {}", gameId);
        } catch (GameNotFoundException e) {
            // Game not found during cleanup (shouldn't happen since we just fetched it)
            logger.warn("Game not found during zone cleanup for game {}: {}", gameId, e.getMessage());
        } catch (Exception e) {
            // Log cleanup errors but don't fail the game completion
            logger.error("Error during shrinking zone cleanup for game {}: {}", gameId, e.getMessage(), e);
            // Game is still successfully completed even if cleanup fails
        }
        
        return game;
    }
    
    public Game removePlayerFromGame(String gameId, String playerIdToRemove, String requestingPlayerId) throws GameNotFoundException, ValidationException {
        logger.warn("removePlayerFromGame is not fully implemented yet.");
        // TODO: Implement logic to remove player ID from game's player list (check admin, game status)
        throw new UnsupportedOperationException("Removing player from game not implemented.");
    }

    // Other game-related methods can go here (e.g., processGameEnd, etc.)
} 