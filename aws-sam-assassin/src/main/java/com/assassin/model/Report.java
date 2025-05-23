package com.assassin.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@DynamoDbBean
public class Report {

    public enum ReportType {
        INAPPROPRIATE_BEHAVIOR, // General inappropriate behavior
        CHEATING,              // Suspected cheating or game manipulation
        HARASSMENT,            // Harassment or bullying
        SPAM,                  // Spam messages or behavior
        FAKE_LOCATION,         // Suspected fake GPS/location spoofing
        OFFENSIVE_CONTENT,     // Offensive profile content, names, etc.
        OTHER                  // Other types of reports
    }

    public enum ReportStatus {
        PENDING,        // Report submitted, awaiting review
        UNDER_REVIEW,   // Report is being actively investigated
        RESOLVED,       // Report has been resolved (action taken)
        DISMISSED,      // Report was reviewed but dismissed (no action needed)
        ESCALATED       // Report has been escalated to higher-level review
    }

    private String reportId;           // (PK) Unique report ID
    private String reporterId;         // (GSI ReporterIndex) ID of the player making the report
    private String reportedPlayerId;   // (GSI ReportedPlayerIndex) ID of the player being reported
    private String gameId;             // (GSI GameReportsIndex) Game where the incident occurred
    private ReportType reportType;     // Type of report
    private String description;        // Detailed description of the incident
    private String createdAt;          // (GSI sort keys) When the report was created
    private String updatedAt;          // When the report was last updated
    private ReportStatus status;       // Current status of the report
    private String reviewedBy;         // (Optional) ID of moderator/admin who reviewed
    private String reviewNotes;        // (Optional) Notes from the review
    private List<String> evidenceUrls; // (Optional) URLs to screenshots/media evidence
    private Map<String, String> metadata; // Additional metadata (location, app version, etc.)

    public Report() {
        // Default constructor for DynamoDB
    }

    // Convenience constructor
    public Report(String reporterId, String reportedPlayerId, String gameId, 
                  ReportType reportType, String description) {
        this.reporterId = reporterId;
        this.reportedPlayerId = reportedPlayerId;
        this.gameId = gameId;
        this.reportType = reportType;
        this.description = description;
        this.status = ReportStatus.PENDING;
        initializeTimestamps();
    }

    // Getters and Setters

    @DynamoDbPartitionKey
    @DynamoDbAttribute("ReportID")
    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"ReporterIndex"})
    @DynamoDbAttribute("ReporterID")
    public String getReporterId() {
        return reporterId;
    }

    public void setReporterId(String reporterId) {
        this.reporterId = reporterId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"ReportedPlayerIndex"})
    @DynamoDbAttribute("ReportedPlayerID")
    public String getReportedPlayerId() {
        return reportedPlayerId;
    }

    public void setReportedPlayerId(String reportedPlayerId) {
        this.reportedPlayerId = reportedPlayerId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"GameReportsIndex"})
    @DynamoDbAttribute("GameID")
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    @DynamoDbAttribute("ReportType")
    public ReportType getReportType() {
        return reportType;
    }

    public void setReportType(ReportType reportType) {
        this.reportType = reportType;
    }

    @DynamoDbAttribute("Description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @DynamoDbSecondarySortKey(indexNames = {"ReporterIndex", "ReportedPlayerIndex", "GameReportsIndex"})
    @DynamoDbAttribute("CreatedAt")
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("UpdatedAt")
    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @DynamoDbAttribute("Status")
    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    @DynamoDbAttribute("ReviewedBy")
    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    @DynamoDbAttribute("ReviewNotes")
    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }

    @DynamoDbAttribute("EvidenceUrls")
    public List<String> getEvidenceUrls() {
        return evidenceUrls;
    }

    public void setEvidenceUrls(List<String> evidenceUrls) {
        this.evidenceUrls = evidenceUrls;
    }

    @DynamoDbAttribute("Metadata")
    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    // Utility methods

    /**
     * Adds a metadata entry.
     *
     * @param key   The metadata key
     * @param value The metadata value
     */
    public void putMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new java.util.HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * Initialize timestamps for a new report.
     */
    public void initializeTimestamps() {
        String timestamp = Instant.now().toString();
        this.createdAt = timestamp;
        this.updatedAt = timestamp;
    }

    /**
     * Update the updatedAt timestamp.
     */
    public void updateTimestamp() {
        this.updatedAt = Instant.now().toString();
    }

    /**
     * Add evidence URL to the list.
     *
     * @param evidenceUrl URL to evidence (screenshot, video, etc.)
     */
    public void addEvidenceUrl(String evidenceUrl) {
        if (this.evidenceUrls == null) {
            this.evidenceUrls = new java.util.ArrayList<>();
        }
        this.evidenceUrls.add(evidenceUrl);
    }

    /**
     * Helper method to update the 'updatedAt' timestamp to the current time.
     */
    public void touch() {
        this.updatedAt = Instant.now().toString();
    }

    @Override
    public String toString() {
        return "Report{" +
                "reportId='" + reportId + '\'' +
                ", reporterId='" + reporterId + '\'' +
                ", reportedPlayerId='" + reportedPlayerId + '\'' +
                ", gameId='" + gameId + '\'' +
                ", reportType=" + reportType +
                ", status=" + status +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                '}';
    }
} 