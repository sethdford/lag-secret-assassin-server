package com.assassin.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a WebSocket connection in the WebSocketConnections DynamoDB table.
 */
@DynamoDbBean
public class WebSocketConnection {
    private String connectionId; // Partition Key
    private String playerId;
    private String gameId;
    private Instant connectedAt;
    private Instant lastActivity;
    private String sourceIp;
    private String userAgent;
    private Long ttl; // Time to live for automatic cleanup

    @DynamoDbPartitionKey
    @DynamoDbAttribute("connectionId")
    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "PlayerIdIndex")
    @DynamoDbAttribute("playerId")
    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    @DynamoDbAttribute("gameId")
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    @DynamoDbAttribute("connectedAt")
    public Instant getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }

    @DynamoDbAttribute("lastActivity")
    public Instant getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(Instant lastActivity) {
        this.lastActivity = lastActivity;
    }

    @DynamoDbAttribute("sourceIp")
    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    @DynamoDbAttribute("userAgent")
    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    @DynamoDbAttribute("ttl")
    public Long getTtl() {
        return ttl;
    }

    public void setTtl(Long ttl) {
        this.ttl = ttl;
    }

    /**
     * Check if this connection is considered stale
     */
    public boolean isStale(int maxAgeMinutes) {
        if (lastActivity == null) {
            return true;
        }
        
        Instant threshold = Instant.now().minusSeconds(maxAgeMinutes * 60L);
        return lastActivity.isBefore(threshold);
    }

    /**
     * Get connection age in minutes
     */
    public long getAgeMinutes() {
        if (connectedAt == null) {
            return 0;
        }
        
        return (Instant.now().getEpochSecond() - connectedAt.getEpochSecond()) / 60;
    }

    /**
     * Get time since last activity in minutes
     */
    public long getIdleMinutes() {
        if (lastActivity == null) {
            return getAgeMinutes();
        }
        
        return (Instant.now().getEpochSecond() - lastActivity.getEpochSecond()) / 60;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebSocketConnection that = (WebSocketConnection) o;
        return Objects.equals(connectionId, that.connectionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId);
    }

    @Override
    public String toString() {
        return "WebSocketConnection{" +
               "connectionId='" + connectionId + '\'' +
               ", playerId='" + playerId + '\'' +
               ", gameId='" + gameId + '\'' +
               ", connectedAt=" + connectedAt +
               ", lastActivity=" + lastActivity +
               ", ageMinutes=" + getAgeMinutes() +
               ", idleMinutes=" + getIdleMinutes() +
               '}';
    }
} 