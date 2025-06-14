package com.assassin.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

import java.time.Instant;
import java.util.Map;

/**
 * SecurityEvent model for tracking security-related events and rate limiting data.
 * Used for abuse detection, rate limiting, and security monitoring.
 */
@DynamoDbBean
public class SecurityEvent {
    
    private String sourceIP;
    private String timestamp;
    private String userID;
    private String eventType;
    private String endpoint;
    private String userAgent;
    private String requestId;
    private Integer statusCode;
    private Long responseTime;
    private String errorMessage;
    private Map<String, String> metadata;
    private Long ttl; // TTL for automatic cleanup
    
    public SecurityEvent() {
        this.timestamp = Instant.now().toString();
        // Set TTL to 30 days from now
        this.ttl = Instant.now().plusSeconds(30 * 24 * 60 * 60).getEpochSecond();
    }
    
    public SecurityEvent(String sourceIP, String eventType) {
        this();
        this.sourceIP = sourceIP;
        this.eventType = eventType;
    }
    
    @DynamoDbPartitionKey
    @DynamoDbAttribute("SourceIP")
    public String getSourceIP() {
        return sourceIP;
    }
    
    public void setSourceIP(String sourceIP) {
        this.sourceIP = sourceIP;
    }
    
    @DynamoDbSortKey
    @DynamoDbSecondarySortKey(indexNames = {"UserSecurityIndex", "EventTypeIndex"})
    @DynamoDbAttribute("Timestamp")
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = {"UserSecurityIndex"})
    @DynamoDbAttribute("UserID")
    public String getUserID() {
        return userID;
    }
    
    public void setUserID(String userID) {
        this.userID = userID;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = {"EventTypeIndex"})
    @DynamoDbAttribute("EventType")
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    @DynamoDbAttribute("Endpoint")
    public String getEndpoint() {
        return endpoint;
    }
    
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
    
    @DynamoDbAttribute("UserAgent")
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    @DynamoDbAttribute("RequestId")
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    @DynamoDbAttribute("StatusCode")
    public Integer getStatusCode() {
        return statusCode;
    }
    
    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }
    
    @DynamoDbAttribute("ResponseTime")
    public Long getResponseTime() {
        return responseTime;
    }
    
    public void setResponseTime(Long responseTime) {
        this.responseTime = responseTime;
    }
    
    @DynamoDbAttribute("ErrorMessage")
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    @DynamoDbAttribute("Metadata")
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    @DynamoDbAttribute("TTL")
    public Long getTtl() {
        return ttl;
    }
    
    public void setTtl(Long ttl) {
        this.ttl = ttl;
    }
    
    // Enum for common event types
    public enum EventType {
        RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED"),
        SUSPICIOUS_LOCATION("SUSPICIOUS_LOCATION"),
        INVALID_REQUEST("INVALID_REQUEST"),
        AUTHENTICATION_FAILURE("AUTHENTICATION_FAILURE"),
        ABUSE_DETECTED("ABUSE_DETECTED"),
        API_ERROR("API_ERROR"),
        BLOCKED_REQUEST("BLOCKED_REQUEST"),
        ENTITY_BLOCKED("ENTITY_BLOCKED"),
        ENTITY_UNBLOCKED("ENTITY_UNBLOCKED"),
        CLEANUP_EXPIRED_BLOCKS("CLEANUP_EXPIRED_BLOCKS"),
        PLAYER_SUSPENDED("PLAYER_SUSPENDED"),
        PLAYER_FLAGGED_FOR_REVIEW("PLAYER_FLAGGED_FOR_REVIEW"),
        MONITORING_LEVEL_INCREASED("MONITORING_LEVEL_INCREASED"),
        ANTI_CHEAT_VIOLATION("ANTI_CHEAT_VIOLATION"),
        SUSPICIOUS_KILL("SUSPICIOUS_KILL");
        
        private final String value;
        
        EventType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    @Override
    public String toString() {
        return "SecurityEvent{" +
                "sourceIP='" + sourceIP + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", userID='" + userID + '\'' +
                ", eventType='" + eventType + '\'' +
                ", endpoint='" + endpoint + '\'' +
                ", statusCode=" + statusCode +
                ", responseTime=" + responseTime +
                '}';
    }
} 