package com.assassin.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

@DynamoDbBean
public class WebSocketConnection {
    private String connectionId;
    private String playerId; // Associated player ID

    @DynamoDbPartitionKey
    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    // Optional: If using PlayerIdIndex GSI
    @DynamoDbSecondaryPartitionKey(indexNames = "PlayerIdIndex") 
    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    @Override
    public String toString() {
        return "WebSocketConnection{" +
               "connectionId='" + connectionId + '\'' +
               ", playerId='" + playerId + '\'' +
               '}';
    }
} 