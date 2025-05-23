package com.assassin.dao;

import com.assassin.model.Report;
import com.assassin.exception.PersistenceException;

import java.util.List;
import java.util.Optional;

public interface ReportDao {

    String GAME_REPORTS_INDEX = "GameReportsIndex";
    String REPORTING_PLAYER_REPORTS_INDEX = "ReportingPlayerReportsIndex";
    String REPORTED_PLAYER_REPORTS_INDEX = "ReportedPlayerReportsIndex";

    /**
     * Saves a report record.
     *
     * @param report The report to save.
     * @throws PersistenceException if the save operation fails.
     */
    void saveReport(Report report) throws PersistenceException;

    /**
     * Retrieves a report by its unique ID.
     *
     * @param reportId The ID of the report.
     * @return An Optional containing the Report if found, otherwise empty.
     * @throws PersistenceException if the retrieval operation fails.
     */
    Optional<Report> getReportById(String reportId) throws PersistenceException;

    /**
     * Retrieves all reports associated with a specific game ID.
     *
     * @param gameId The ID of the game.
     * @return A list of reports for the given game.
     * @throws PersistenceException if the retrieval operation fails.
     */
    List<Report> getReportsByGameId(String gameId) throws PersistenceException;

    /**
     * Retrieves all reports submitted by a specific player.
     *
     * @param reportingPlayerId The ID of the player who submitted the reports.
     * @return A list of reports submitted by the player.
     * @throws PersistenceException if the retrieval operation fails.
     */
    List<Report> getReportsByReportingPlayerId(String reportingPlayerId) throws PersistenceException;

    /**
     * Retrieves all reports filed against a specific player.
     *
     * @param reportedPlayerId The ID of the player who was reported.
     * @return A list of reports filed against the player.
     * @throws PersistenceException if the retrieval operation fails.
     */
    List<Report> getReportsByReportedPlayerId(String reportedPlayerId) throws PersistenceException;
    
    /**
     * Retrieves reports based on their status for a given game.
     * @param gameId The ID of the game.
     * @param status The status to filter by.
     * @return A list of reports matching the status.
     * @throws PersistenceException if an error occurs.
     */
    List<Report> getReportsByGameIdAndStatus(String gameId, Report.ReportStatus status) throws PersistenceException;

} 