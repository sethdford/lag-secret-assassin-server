package com.assassin.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Represents a game entity in the DynamoDB Games table.
 */
@DynamoDbBean
public class Game {

    private static final Logger logger = LoggerFactory.getLogger(Game.class);

    private String gameID; // Partition key
    private String gameName;
    private String status; // GSI Partition Key (StatusCreatedAtIndex)
    private String createdAt; // GSI Sort Key (StatusCreatedAtIndex)
    private List<String> playerIDs; // List of player IDs participating in the game
    private String adminPlayerID; // Player who created/administers the game
    private Map<String, Object> settings; // Flexible map for game settings

    // Constants for GSI
    private static final String STATUS_CREATED_AT_INDEX = "StatusCreatedAtIndex";

    @DynamoDbPartitionKey
    @DynamoDbAttribute("GameID")
    public String getGameID() {
        return gameID;
    }

    public void setGameID(String gameID) {
        this.gameID = gameID;
    }

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {STATUS_CREATED_AT_INDEX})
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @DynamoDbSecondarySortKey(indexNames = {STATUS_CREATED_AT_INDEX})
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public List<String> getPlayerIDs() {
        return playerIDs;
    }

    public void setPlayerIDs(List<String> playerIDs) {
        this.playerIDs = playerIDs;
    }

    public String getAdminPlayerID() {
        return adminPlayerID;
    }

    public void setAdminPlayerID(String adminPlayerID) {
        this.adminPlayerID = adminPlayerID;
    }

    // Explicitly define converter for Map<String, Object>
    public static class GenericMapConverter implements AttributeConverter<Map<String, Object>> {
        @Override
        public AttributeValue transformFrom(Map<String, Object> input) {
            // This simple implementation assumes input values are already suitable for AttributeValue.from*
            // More robust implementation might inspect types and convert accordingly.
            Map<String, AttributeValue> attributeValueMap = new HashMap<>();
            if (input != null) {
                input.forEach((key, value) -> {
                    if (value instanceof String) {
                        attributeValueMap.put(key, AttributeValue.builder().s((String) value).build());
                    } else if (value instanceof Number) {
                         attributeValueMap.put(key, AttributeValue.builder().n(value.toString()).build());
                    } else if (value instanceof Boolean) {
                         attributeValueMap.put(key, AttributeValue.builder().bool((Boolean) value).build());
                    } // Add other types as needed (List, Map, etc.)
                     else {
                         // Fallback or throw error for unsupported types
                         logger.warn("Unsupported type in settings map for key '{}': {}", key, value.getClass().getName());
                         // attributeValueMap.put(key, AttributeValue.builder().nul(true).build()); // Or store as null
                    }
                });
            }
            return AttributeValue.builder().m(attributeValueMap).build();
        }

        @Override
        public Map<String, Object> transformTo(AttributeValue input) {
            Map<String, Object> resultMap = new HashMap<>();
            if (input != null && input.hasM()) {
                input.m().forEach((key, av) -> {
                    resultMap.put(key, convertAttributeValueToObject(av));
                });
            }
            return resultMap;
        }

        // Helper method to convert a single AttributeValue to a Java Object
        private Object convertAttributeValueToObject(AttributeValue av) {
            if (av.s() != null) return av.s();
            if (av.n() != null) {
                try {
                    if (av.n().contains(".")) return Double.parseDouble(av.n());
                    return Long.parseLong(av.n());
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse number string '{}', returning as string.", av.n());
                    return av.n(); // Fallback to string
                }
            }
            if (av.bool() != null) return av.bool();
            if (av.nul() != null && av.nul()) return null;
            if (av.hasL()) {
                // Recursively convert list elements
                List<Object> list = new ArrayList<>();
                av.l().forEach(item -> list.add(convertAttributeValueToObject(item)));
                return list;
            }
            if (av.hasM()) {
                // Recursively convert map elements
                Map<String, Object> map = new HashMap<>();
                av.m().forEach((k, v) -> map.put(k, convertAttributeValueToObject(v)));
                return map;
            }
            // Handle other types like BS, SS, NS, etc. if needed
            logger.warn("Unsupported AttributeValue type encountered during conversion: {}", av.type());
            return null; // Or throw an exception
        }

        @Override
        public EnhancedType<Map<String, Object>> type() {
            // This is tricky because Object isn't specific. We tell it it's a Map.
            return EnhancedType.mapOf(String.class, Object.class);
        }

        @Override
        public AttributeValueType attributeValueType() {
            return AttributeValueType.M; // Represents a DynamoDB Map
        }
    }

    @DynamoDbAttribute("Settings")
    @DynamoDbConvertedBy(GenericMapConverter.class) // Apply the custom converter
    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Game game = (Game) o;
        return Objects.equals(gameID, game.gameID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameID);
    }

    @Override
    public String toString() {
        return "Game{" +
               "gameID='" + gameID + '\'' +
               ", gameName='" + gameName + '\'' +
               ", status='" + status + '\'' +
               ", createdAt='" + createdAt + '\'' +
               ", playerIDs=" + playerIDs +
               ", adminPlayerID='" + adminPlayerID + '\'' +
               ", settings=" + settings +
               '}';
    }
} 