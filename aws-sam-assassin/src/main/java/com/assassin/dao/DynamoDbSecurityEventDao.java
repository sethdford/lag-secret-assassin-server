package com.assassin.dao;

import com.assassin.util.DynamoDbClientProvider;
import com.assassin.model.SecurityEvent;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DynamoDB implementation of SecurityEventDao.
 * Provides methods for tracking security events, rate limiting, and abuse detection.
 */
public class DynamoDbSecurityEventDao implements SecurityEventDao {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamoDbSecurityEventDao.class);
    
    private final DynamoDbTable<SecurityEvent> securityEventTable;
    private final DynamoDbIndex<SecurityEvent> userSecurityIndex;
    private final DynamoDbIndex<SecurityEvent> eventTypeIndex;
    private final String tableName;
    
    public DynamoDbSecurityEventDao() {
        DynamoDbEnhancedClient enhancedClient = DynamoDbClientProvider.getDynamoDbEnhancedClient();
        this.tableName = getTableNameFromEnv();
        this.securityEventTable = enhancedClient.table(this.tableName, TableSchema.fromBean(SecurityEvent.class));
        this.userSecurityIndex = securityEventTable.index("UserSecurityIndex");
        this.eventTypeIndex = securityEventTable.index("EventTypeIndex");
        logger.info("DynamoDbSecurityEventDao initialized with table: {}", this.tableName);
    }
    
    private String getTableNameFromEnv() {
        String tableName = System.getenv("SECURITY_EVENTS_TABLE_NAME");
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalStateException("SECURITY_EVENTS_TABLE_NAME environment variable is not set");
        }
        return tableName;
    }
    
    @Override
    public SecurityEvent saveSecurityEvent(SecurityEvent securityEvent) {
        try {
            logger.debug("Saving security event: {}", securityEvent);
            securityEventTable.putItem(securityEvent);
            logger.info("Successfully saved security event for IP: {} with type: {}", 
                       securityEvent.getSourceIP(), securityEvent.getEventType());
            return securityEvent;
        } catch (RuntimeException e) {
            logger.error("Error saving security event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save security event", e);
        }
    }
    
    @Override
    public List<SecurityEvent> getSecurityEventsByIP(String sourceIP, String startTime, String endTime) {
        try {
            logger.debug("Querying security events for IP: {} between {} and {}", sourceIP, startTime, endTime);
            
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(sourceIP).build()
            );
            
            QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional);
            
            // Add time range filter if provided
            if (startTime != null && endTime != null) {
                requestBuilder.filterExpression(
                    software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                        .expression("#timestamp BETWEEN :startTime AND :endTime")
                        .putExpressionName("#timestamp", "Timestamp")
                        .putExpressionValue(":startTime", AttributeValue.builder().s(startTime).build())
                        .putExpressionValue(":endTime", AttributeValue.builder().s(endTime).build())
                        .build()
                );
            }
            
            List<SecurityEvent> events = securityEventTable.query(requestBuilder.build())
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
            
            logger.info("Found {} security events for IP: {}", events.size(), sourceIP);
            return events;
        } catch (RuntimeException e) {
            logger.error("Error querying security events by IP: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to query security events by IP", e);
        }
    }
    
    @Override
    public List<SecurityEvent> getSecurityEventsByUser(String userID, String startTime, String endTime) {
        try {
            logger.debug("Querying security events for user: {} between {} and {}", userID, startTime, endTime);
            
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(userID).build()
            );
            
            QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional);
            
            // Add time range filter if provided
            if (startTime != null && endTime != null) {
                requestBuilder.filterExpression(
                    software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                        .expression("#timestamp BETWEEN :startTime AND :endTime")
                        .putExpressionName("#timestamp", "Timestamp")
                        .putExpressionValue(":startTime", AttributeValue.builder().s(startTime).build())
                        .putExpressionValue(":endTime", AttributeValue.builder().s(endTime).build())
                        .build()
                );
            }
            
            List<SecurityEvent> events = userSecurityIndex.query(requestBuilder.build())
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
            
            logger.info("Found {} security events for user: {}", events.size(), userID);
            return events;
        } catch (RuntimeException e) {
            logger.error("Error querying security events by user: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to query security events by user", e);
        }
    }
    
    @Override
    public List<SecurityEvent> getSecurityEventsByType(String eventType, String startTime, String endTime) {
        try {
            logger.debug("Querying security events for type: {} between {} and {}", eventType, startTime, endTime);
            
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(eventType).build()
            );
            
            QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional);
            
            // Add time range filter if provided
            if (startTime != null && endTime != null) {
                requestBuilder.filterExpression(
                    software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                        .expression("#timestamp BETWEEN :startTime AND :endTime")
                        .putExpressionName("#timestamp", "Timestamp")
                        .putExpressionValue(":startTime", AttributeValue.builder().s(startTime).build())
                        .putExpressionValue(":endTime", AttributeValue.builder().s(endTime).build())
                        .build()
                );
            }
            
            List<SecurityEvent> events = eventTypeIndex.query(requestBuilder.build())
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
            
            logger.info("Found {} security events for type: {}", events.size(), eventType);
            return events;
        } catch (RuntimeException e) {
            logger.error("Error querying security events by type: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to query security events by type", e);
        }
    }
    
    @Override
    public long countSecurityEventsByIP(String sourceIP, String startTime, String endTime) {
        return getSecurityEventsByIP(sourceIP, startTime, endTime).size();
    }
    
    @Override
    public long countSecurityEventsByUser(String userID, String startTime, String endTime) {
        return getSecurityEventsByUser(userID, startTime, endTime).size();
    }
    
    @Override
    public long countSecurityEventsByIPAndType(String sourceIP, String eventType, String startTime, String endTime) {
        List<SecurityEvent> events = getSecurityEventsByIP(sourceIP, startTime, endTime);
        return events.stream()
            .filter(event -> eventType.equals(event.getEventType()))
            .count();
    }
    
    @Override
    public Optional<SecurityEvent> getLatestSecurityEventByIP(String sourceIP) {
        try {
            logger.debug("Getting latest security event for IP: {}", sourceIP);
            
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(sourceIP).build()
            );
            
            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // Descending order (latest first)
                .limit(1)
                .build();
            
            List<SecurityEvent> events = securityEventTable.query(request)
                .items()
                .stream()
                .collect(Collectors.toList());
            
            if (events.isEmpty()) {
                logger.debug("No security events found for IP: {}", sourceIP);
                return Optional.empty();
            }
            
            SecurityEvent latestEvent = events.get(0);
            logger.debug("Found latest security event for IP: {} at {}", sourceIP, latestEvent.getTimestamp());
            return Optional.of(latestEvent);
        } catch (RuntimeException e) {
            logger.error("Error getting latest security event by IP: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get latest security event by IP", e);
        }
    }
    
    @Override
    public int deleteOldSecurityEvents(String cutoffTime) {
        // Note: DynamoDB doesn't support efficient bulk delete operations
        // In practice, we rely on TTL for automatic cleanup
        // This method is provided for completeness but should be used sparingly
        logger.warn("deleteOldSecurityEvents called - consider using TTL for automatic cleanup instead");
        return 0;
    }
} 