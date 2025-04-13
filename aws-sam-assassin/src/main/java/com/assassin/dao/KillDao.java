package com.assassin.dao;

import java.util.List;
import java.util.Optional;

import com.assassin.exception.KillNotFoundException;
import com.assassin.exception.KillPersistenceException;
import com.assassin.model.Kill;
import com.assassin.model.Player;

/**
 * Data Access Object interface for Kill data.
 */
public interface KillDao {

    /**
     * Saves a kill record to the database.
     *
     * @param kill The Kill object to save.
     * @throws KillPersistenceException if the save operation fails.
     */
    void saveKill(Kill kill) throws KillPersistenceException;

    /**
     * Finds all kills performed by a specific killer, ordered by time descending.
     *
     * @param killerID The ID of the killer.
     * @return A list of Kill objects.
     * @throws KillNotFoundException if no kills are found for the specified killer.
     * @throws KillPersistenceException if the query operation fails.
     */
    List<Kill> findKillsByKiller(String killerID) throws KillNotFoundException, KillPersistenceException;

    /**
     * Finds all kills where a specific player was the victim, ordered by time descending.
     * Requires the VictimID-Time-index GSI.
     *
     * @param victimID The ID of the victim.
     * @return A list of Kill objects.
     * @throws KillNotFoundException if no kills are found for the specified victim.
     * @throws KillPersistenceException if the query operation fails.
     */
    List<Kill> findKillsByVictim(String victimID) throws KillNotFoundException, KillPersistenceException;

     /**
     * Finds the most recent N kills, ordered by time descending.
     * Uses a Scan operation with a limit.
     *
     * @param limit The maximum number of kills to return.
     * @return A list of Kill objects.
     * @throws KillNotFoundException if no kills are found.
     * @throws KillPersistenceException if the query operation fails.
     */
    List<Kill> findRecentKills(int limit) throws KillNotFoundException, KillPersistenceException;

    /**
     * Counts the number of times a player has been killed (appeared as victim).
     * Requires the VictimID-Time-index GSI.
     *
     * @param victimId The ID of the player (victim).
     * @return The total count of deaths for the player.
     * @throws KillPersistenceException if the query operation fails.
     */
    int getPlayerDeathCount(String victimId) throws KillPersistenceException;

    /**
     * Gets the approximate total number of kills in the table.
     *
     * @return The approximate count of kills.
     * @throws KillPersistenceException If there's an error communicating with DynamoDB.
     */
    long getKillCount() throws KillPersistenceException;

    /**
     * Counts the total number of times a player has been killed (i.e., was the victim).
     *
     * @param victimId The ID of the player.
     * @return The total number of deaths for the player.
     * @throws KillPersistenceException If there's an error counting deaths.
     */
    int countDeathsByVictim(String victimId) throws KillPersistenceException;

    /**
     * Retrieves a list of recent kills across all players.
     *
     * @param limit The maximum number of recent kills to retrieve.
     * @return A list of the most recent Kill objects.
     * @throws KillPersistenceException If there's an error retrieving recent kills.
     */
    List<Kill> getRecentKills(int limit) throws KillPersistenceException;

    /**
     * Checks if a player is still alive in a given game context 
     * (i.e., they have not been recorded as a victim).
     *
     * @param playerId The ID of the player to check.
     * @param gameId The context of the game (may influence which kills are relevant).
     * @return true if the player is considered alive, false otherwise.
     * @throws KillPersistenceException If there's an error checking the player's status.
     */
    boolean isPlayerAlive(String playerId, String gameId) throws KillPersistenceException;

    /**
     * Retrieves the Kill record where a specific victim was killed in a given game.
     * This typically finds the most recent kill record for the victim within the game context.
     *
     * @param victimId The ID of the victim.
     * @param gameId The context of the game.
     * @return An Optional containing the Kill record if found, otherwise empty.
     * @throws KillPersistenceException If there's an error retrieving the kill record.
     */
    Optional<Kill> findKillRecordByVictimAndGame(String victimId, String gameId) throws KillPersistenceException;

    /**
     * Retrieves all kills from the database.
     * WARNING: This performs a table scan and can be inefficient for large tables.
     *
     * @return A list of all Kill objects.
     * @throws KillPersistenceException if the query operation fails.
     */
    List<Kill> getAllKills() throws KillPersistenceException;

    /**
     * Retrieves a specific kill by its composite key (killer ID and time).
     *
     * @param killerId The ID of the killer (partition key).
     * @param time The timestamp of the kill (sort key).
     * @return An Optional containing the Kill if found, otherwise empty.
     * @throws KillPersistenceException if the query operation fails.
     */
    Optional<Kill> getKill(String killerId, String time) throws KillPersistenceException;

    /**
     * Finds all kill records associated with a specific game ID.
     *
     * @param gameId The ID of the game.
     * @return A list of Kill objects for the specified game, potentially empty.
     */
    List<Kill> findKillsByGameId(String gameId);

} 