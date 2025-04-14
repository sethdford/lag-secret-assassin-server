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

    // Default constructor
    public GameService() {
        this(new DynamoDbGameDao(), new DynamoDbPlayerDao());
    }

    // Constructor for dependency injection (testing)
    public GameService(GameDao gameDao, PlayerDao playerDao) {
        this.gameDao = Objects.requireNonNull(gameDao, "gameDao cannot be null");
        this.playerDao = Objects.requireNonNull(playerDao, "playerDao cannot be null");
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

    public Game forceEndGame(String gameId, String requestingPlayerId) throws GameNotFoundException, ValidationException {
         logger.warn("forceEndGame is not fully implemented yet.");
         // TODO: Implement logic to force game status to COMPLETED/CANCELLED (check admin)
         throw new UnsupportedOperationException("Forcing game end not implemented.");
    }
    
    public Game removePlayerFromGame(String gameId, String playerIdToRemove, String requestingPlayerId) throws GameNotFoundException, ValidationException {
        logger.warn("removePlayerFromGame is not fully implemented yet.");
        // TODO: Implement logic to remove player ID from game's player list (check admin, game status)
        throw new UnsupportedOperationException("Removing player from game not implemented.");
    }

    // Other game-related methods can go here (e.g., processGameEnd, etc.)
} 