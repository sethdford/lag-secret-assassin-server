package com.assassin.dao;

import java.util.List;
import java.util.Optional;

import com.assassin.exception.PersistenceException;
import com.assassin.model.TargetAssignment;

/**
 * Data Access Object interface for TargetAssignment operations.
 * Provides methods for creating, updating, retrieving, and deleting target assignments.
 */
public interface TargetAssignmentDao {

    /**
     * Saves a target assignment to the database.
     * Creates a new assignment if it doesn't exist, updates if it does.
     *
     * @param assignment The target assignment to save
     * @throws PersistenceException if there's an error saving the assignment
     */
    void saveAssignment(TargetAssignment assignment) throws PersistenceException;

    /**
     * Retrieves a target assignment by its ID.
     *
     * @param assignmentId The unique ID of the assignment
     * @return Optional containing the assignment if found, empty otherwise
     * @throws PersistenceException if there's an error retrieving the assignment
     */
    Optional<TargetAssignment> getAssignmentById(String assignmentId) throws PersistenceException;

    /**
     * Retrieves the current active assignment for a specific player in a game.
     *
     * @param gameId The ID of the game
     * @param assignerId The ID of the player (assassin)
     * @return Optional containing the active assignment if found, empty otherwise
     * @throws PersistenceException if there's an error retrieving the assignment
     */
    Optional<TargetAssignment> getCurrentAssignmentForPlayer(String gameId, String assignerId) throws PersistenceException;

    /**
     * Retrieves all assignments for a specific game.
     *
     * @param gameId The ID of the game
     * @return List of all assignments for the game
     * @throws PersistenceException if there's an error retrieving assignments
     */
    List<TargetAssignment> getAssignmentsForGame(String gameId) throws PersistenceException;

    /**
     * Retrieves all active assignments for a specific game.
     *
     * @param gameId The ID of the game
     * @return List of active assignments for the game
     * @throws PersistenceException if there's an error retrieving assignments
     */
    List<TargetAssignment> getActiveAssignmentsForGame(String gameId) throws PersistenceException;

    /**
     * Retrieves all assignments where a specific player is the target.
     *
     * @param gameId The ID of the game
     * @param targetId The ID of the target player
     * @return List of assignments targeting the specified player
     * @throws PersistenceException if there's an error retrieving assignments
     */
    List<TargetAssignment> getAssignmentsTargetingPlayer(String gameId, String targetId) throws PersistenceException;

    /**
     * Retrieves assignment history for a specific player (all assignments they've had).
     *
     * @param gameId The ID of the game
     * @param assignerId The ID of the player
     * @return List of all assignments for the player, ordered by date
     * @throws PersistenceException if there's an error retrieving assignments
     */
    List<TargetAssignment> getAssignmentHistoryForPlayer(String gameId, String assignerId) throws PersistenceException;

    /**
     * Updates the status of a target assignment.
     *
     * @param assignmentId The ID of the assignment to update
     * @param newStatus The new status to set
     * @throws PersistenceException if there's an error updating the assignment
     */
    void updateAssignmentStatus(String assignmentId, String newStatus) throws PersistenceException;

    /**
     * Marks an assignment as completed and sets the completion date.
     *
     * @param assignmentId The ID of the assignment to complete
     * @param completionDate The date/time when the assignment was completed (ISO 8601)
     * @throws PersistenceException if there's an error updating the assignment
     */
    void completeAssignment(String assignmentId, String completionDate) throws PersistenceException;

    /**
     * Cancels all active assignments for a game (typically when game ends).
     *
     * @param gameId The ID of the game
     * @throws PersistenceException if there's an error updating assignments
     */
    void cancelAllAssignmentsForGame(String gameId) throws PersistenceException;

    /**
     * Deletes a target assignment from the database.
     * Note: This should be used carefully as it removes audit trail.
     *
     * @param assignmentId The ID of the assignment to delete
     * @throws PersistenceException if there's an error deleting the assignment
     */
    void deleteAssignment(String assignmentId) throws PersistenceException;

    /**
     * Counts the number of assignments for a game by status.
     *
     * @param gameId The ID of the game
     * @param status The status to count (null for all statuses)
     * @return The number of assignments matching the criteria
     * @throws PersistenceException if there's an error counting assignments
     */
    long countAssignmentsByStatus(String gameId, String status) throws PersistenceException;

    /**
     * Validates the integrity of target assignments for a game.
     * Checks for orphaned assignments, circular references, etc.
     *
     * @param gameId The ID of the game to validate
     * @return List of validation issues found (empty if no issues)
     * @throws PersistenceException if there's an error during validation
     */
    List<String> validateAssignmentIntegrity(String gameId) throws PersistenceException;
} 