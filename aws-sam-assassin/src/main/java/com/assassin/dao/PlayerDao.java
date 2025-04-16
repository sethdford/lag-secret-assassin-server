package com.assassin.dao;

import java.util.List;
import java.util.Optional;

import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.PlayerPersistenceException;
import com.assassin.model.Player;

/**
 * Data Access Object interface for Player data.
 */
public interface PlayerDao {

    /**
     * Retrieves a player by their ID.
     *
     * @param playerID The ID of the player to retrieve.
     * @return An Optional containing the Player if found, otherwise empty.
     */
    Optional<Player> getPlayerById(String playerID);

    /**
     * Finds a player by their ID.
     * 
     * @param playerID The ID of the player to find.
     * @return The Player if found, otherwise null.
     */
    Player findPlayerById(String playerID);

    /**
     * Finds a player by their email address.
     * 
     * @param email The email address of the player to find.
     * @return The Player if found, otherwise null.
     */
    Player findPlayerByEmail(String email);

    /**
     * Saves or updates a player item in the database.
     *
     * @param player The Player object to save.
     */
    void savePlayer(Player player);

    /**
     * Retrieves all players from the database.
     * WARNING: This typically performs a full table scan, which can be inefficient
     * and costly on large tables. Consider alternatives like pagination or specific queries if possible.
     *
     * @return A list of all Player objects.
     */
    List<Player> getAllPlayers();

    /**
     * @return A list of top players sorted by kill count descending.
     */
    List<Player> getLeaderboardByKillCount(String statusPartitionKey, int limit);

    /**
     * Gets the approximate total number of players in the table.
     *
     * @return The approximate count of players.
     * @throws PlayerPersistenceException If there's an error communicating with DynamoDB.
     */
    long getPlayerCount() throws PlayerPersistenceException;

    /**
     * Deletes a player by their ID.
     *
     * @param playerId the ID of the player to delete.
     */
    void deletePlayer(String playerId);

    /**
     * Increments the kill count for a player atomically.
     *
     * @param playerId The ID of the player whose kill count should be incremented.
     * @return The updated kill count.
     * @throws PlayerPersistenceException if the update operation fails.
     * @throws PlayerNotFoundException if the player does not exist.
     */
    int incrementPlayerKillCount(String playerId) throws PlayerPersistenceException, PlayerNotFoundException;

    /**
     * Updates the location details for a specific player.
     *
     * @param playerId The ID of the player to update.
     * @param latitude The new latitude.
     * @param longitude The new longitude.
     * @param timestamp The timestamp of the location update (ISO 8601 format).
     * @param accuracy The accuracy of the location in meters.
     * @throws PlayerPersistenceException If the update fails.
     * @throws PlayerNotFoundException If the player does not exist (optional, depending on implementation).
     */
    void updatePlayerLocation(String playerId, Double latitude, Double longitude, String timestamp, Double accuracy)
            throws PlayerPersistenceException, PlayerNotFoundException;

    /**
     * Retrieves all players associated with a specific game ID.
     * Requires a GSI on the GameID attribute.
     *
     * @param gameId The ID of the game.
     * @return A list of Player objects in the specified game.
     * @throws PlayerPersistenceException if there is an error querying the index.
     */
    List<Player> getPlayersByGameId(String gameId) throws PlayerPersistenceException;

    /**
     * Retrieves all players targeting a specific player within a game.
     *
     * @param targetId The ID of the player being targeted.
     * @param gameId The ID of the game.
     * @return A list of players (hunters) targeting the specified player.
     */
    List<Player> getPlayersTargeting(String targetId, String gameId);

    // Potentially add methods for finding player by targetID, listing alive players, etc.
    // Player findByTargetId(String targetId); // Requires GSI
    // List<Player> findAllAlivePlayers(); // Requires Scan or GSI
}