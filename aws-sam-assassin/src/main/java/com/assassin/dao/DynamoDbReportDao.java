package com.assassin.dao;

import com.assassin.model.Report;
import com.assassin.exception.PersistenceException;
import com.assassin.util.DynamoDbClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class DynamoDbReportDao implements ReportDao {

    private static final Logger LOG = LoggerFactory.getLogger(DynamoDbReportDao.class);
    private static final String REPORTS_TABLE_NAME_ENV_VAR = "REPORTS_TABLE_NAME"; // Ensure this matches template.yaml
    private final DynamoDbTable<Report> reportTable;
    private final DynamoDbIndex<Report> gameReportsIndex;
    private final DynamoDbIndex<Report> reportingPlayerReportsIndex;
    private final DynamoDbIndex<Report> reportedPlayerReportsIndex;

    public DynamoDbReportDao() {
        DynamoDbEnhancedClient enhancedClient = DynamoDbClientProvider.getDynamoDbEnhancedClient();
        String tableName = System.getenv(REPORTS_TABLE_NAME_ENV_VAR);
        if (tableName == null || tableName.isEmpty()) {
            LOG.error("Reports table name environment variable {} is not set.", REPORTS_TABLE_NAME_ENV_VAR);
            throw new IllegalStateException("Reports table name not configured.");
        }
        this.reportTable = enhancedClient.table(tableName, TableSchema.fromBean(Report.class));
        this.gameReportsIndex = reportTable.index(GAME_REPORTS_INDEX);
        this.reportingPlayerReportsIndex = reportTable.index(REPORTING_PLAYER_REPORTS_INDEX);
        this.reportedPlayerReportsIndex = reportTable.index(REPORTED_PLAYER_REPORTS_INDEX);
        LOG.info("DynamoDbReportDao initialized for table: {}", tableName);
    }
    
    // Constructor for testing with mocked client
    public DynamoDbReportDao(DynamoDbEnhancedClient enhancedClient) {
        String tableName = System.getenv(REPORTS_TABLE_NAME_ENV_VAR);
        if (tableName == null) { 
            tableName = "test-reports"; // Fallback for tests if env var not set
            LOG.warn("Falling back to default table name for tests: {}", tableName);
        }
        this.reportTable = enhancedClient.table(tableName, TableSchema.fromBean(Report.class));
        this.gameReportsIndex = reportTable.index(GAME_REPORTS_INDEX);
        this.reportingPlayerReportsIndex = reportTable.index(REPORTING_PLAYER_REPORTS_INDEX);
        this.reportedPlayerReportsIndex = reportTable.index(REPORTED_PLAYER_REPORTS_INDEX);
    }

    @Override
    public void saveReport(Report report) throws PersistenceException {
        if (report.getReportId() == null || report.getReportId().isEmpty()) {
            report.setReportId(UUID.randomUUID().toString());
        }
        report.touch(); // Update 'updatedAt' timestamp
        try {
            reportTable.putItem(report);
            LOG.debug("Saved report with ID: {}", report.getReportId());
        } catch (DynamoDbException e) {
            LOG.error("Error saving report {}: {}", report.getReportId(), e.getMessage(), e);
            throw new PersistenceException("Failed to save report", e);
        }
    }

    @Override
    public Optional<Report> getReportById(String reportId) throws PersistenceException {
        try {
            Report report = reportTable.getItem(Key.builder().partitionValue(reportId).build());
            LOG.debug("Retrieved report by ID {}: {}", reportId, (report != null));
            return Optional.ofNullable(report);
        } catch (DynamoDbException e) {
            LOG.error("Error retrieving report by ID {}: {}", reportId, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve report by ID", e);
        }
    }

    @Override
    public List<Report> getReportsByGameId(String gameId) throws PersistenceException {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(gameId).build());
        try {
            List<Report> reports = gameReportsIndex.query(queryConditional).stream()
                    .flatMap(page -> page.items().stream())
                    .collect(Collectors.toList());
            LOG.debug("Retrieved {} reports for game ID: {}", reports.size(), gameId);
            return reports;
        } catch (DynamoDbException e) {
            LOG.error("Error retrieving reports for game ID {}: {}", gameId, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve reports by game ID", e);
        }
    }

    @Override
    public List<Report> getReportsByReportingPlayerId(String reportingPlayerId) throws PersistenceException {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(reportingPlayerId).build());
        try {
            List<Report> reports = reportingPlayerReportsIndex.query(queryConditional).stream()
                    .flatMap(page -> page.items().stream())
                    .collect(Collectors.toList());
            LOG.debug("Retrieved {} reports by reporting player ID: {}", reports.size(), reportingPlayerId);
            return reports;
        } catch (DynamoDbException e) {
            LOG.error("Error retrieving reports by reporting player ID {}: {}", reportingPlayerId, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve reports by reporting player ID", e);
        }
    }

    @Override
    public List<Report> getReportsByReportedPlayerId(String reportedPlayerId) throws PersistenceException {
         QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(reportedPlayerId).build());
        try {
            List<Report> reports = reportedPlayerReportsIndex.query(queryConditional).stream()
                    .flatMap(page -> page.items().stream())
                    .collect(Collectors.toList());
            LOG.debug("Retrieved {} reports for reported player ID: {}", reports.size(), reportedPlayerId);
            return reports;
        } catch (DynamoDbException e) {
            LOG.error("Error retrieving reports for reported player ID {}: {}", reportedPlayerId, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve reports by reported player ID", e);
        }
    }
    
    @Override
    public List<Report> getReportsByGameIdAndStatus(String gameId, Report.ReportStatus status) throws PersistenceException {
        LOG.debug("Querying reports for gameId {} and status {}", gameId, status);
        try {
            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(gameId).build()))
                .filterExpression(software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                    .expression("#status_attr = :status_val")
                    .putExpressionName("#status_attr", "Status") // 'Status' is the actual attribute name in DynamoDB
                    .putExpressionValue(":status_val", AttributeValue.builder().s(status.name()).build())
                    .build())
                .build();

            List<Report> reports = gameReportsIndex.query(queryRequest).stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
            LOG.debug("Retrieved {} reports for game ID {} and status {}: {}", reports.size(), gameId, status, reports);
            return reports;
        } catch (DynamoDbException e) {
            LOG.error("Error retrieving reports for game ID {} and status {}: {}", gameId, status, e.getMessage(), e);
            throw new PersistenceException("Failed to retrieve reports by game ID and status", e);
        }
    }
} 