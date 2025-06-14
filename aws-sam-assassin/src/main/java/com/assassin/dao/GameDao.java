package com.assassin.dao;

import java.util.List;
import java.util.Optional;

import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.GamePersistenceException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;

/**
 * Data Access Object interface for Game entities.
 */
public interface GameDao {

    /**
     * Saves or updates a game record in the database.
     *
     * @param game The Game object to save.
     */
    void saveGame(Game game);

    /**
     * Retrieves a specific game by its ID.
     *
     * @param gameId The ID of the game to retrieve.
     * @return An Optional containing the Game if found, otherwise empty.
     */
    Optional<Game> getGameById(String gameId);

    /**
     * Lists games based on their status, typically ordered by creation date.
     * This might utilize the StatusCreatedAtIndex GSI.
     *
     * @param status The status to filter games by (e.g., "PENDING", "ACTIVE", "COMPLETED").
     * @return A list of Game objects matching the status.
     */
    List<Game> listGamesByStatus(String status);

    /**
     * Counts the total number of games a player has participated in.
     * Requires knowing how participation is tracked (e.g., a player list in Game model).
     *
     * @param playerId The ID of the player.
     * @return The total number of games played.
     * @throws GamePersistenceException If there's an error counting games.
     */
    int countGamesPlayedByPlayer(String playerId) throws GamePersistenceException;

    /**
     * Counts the total number of games a player has won.
     * Requires knowing how wins are tracked (e.g., a winnerId field in Game model).
     *
     * @param playerId The ID of the player.
     * @return The total number of games won.
     * @throws GamePersistenceException If there's an error counting wins.
     */
    int countWinsByPlayer(String playerId) throws GamePersistenceException;

    /**
     * Updates the boundary coordinates for a specific game.
     *
     * @param gameId The ID of the game to update.
     * @param boundary The new list of coordinates representing the boundary.
     * @throws GameNotFoundException If the game with the specified ID is not found.
     * @throws GamePersistenceException If the update fails.
     */
    void updateGameBoundary(String gameId, List<Coordinate> boundary) 
        throws GameNotFoundException, GamePersistenceException;

    /**
     * Deletes a game by its ID.
     *
     * @param gameId The ID of the game to delete.
     * @throws GameNotFoundException If the game with the specified ID is not found.
     * @throws GamePersistenceException If the deletion fails.
     */
    void deleteGame(String gameId) throws GameNotFoundException, GamePersistenceException;

    /**
     * Retrieves all games from the database.
     * Use with caution as this performs a scan operation which can be expensive.
     *
     * @return A list of all Game objects.
     * @throws GamePersistenceException If there's an error retrieving games.
     */
    List<Game> getAllGames() throws GamePersistenceException;

} 