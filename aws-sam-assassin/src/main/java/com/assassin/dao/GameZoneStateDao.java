package com.assassin.dao;

import com.assassin.model.GameZoneState;
import java.util.Optional;

/**
 * Data Access Object interface for GameZoneState entities.
 */
public interface GameZoneStateDao {

    /**
     * Saves or updates the game zone state.
     * @param gameZoneState The state object to save.
     */
    void saveGameZoneState(GameZoneState gameZoneState);

    /**
     * Retrieves the game zone state for a specific game.
     * @param gameId The ID of the game.
     * @return An Optional containing the GameZoneState if found.
     */
    Optional<GameZoneState> getGameZoneState(String gameId);

    /**
     * Deletes the game zone state for a specific game.
     * @param gameId The ID of the game whose state to delete.
     */
    void deleteGameZoneState(String gameId);
} 