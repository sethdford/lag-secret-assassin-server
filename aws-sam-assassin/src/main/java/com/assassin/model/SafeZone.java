package com.assassin.model;

import java.util.Objects;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

/**
 * Represents a safe zone within a game.
 */
@DynamoDbBean
public class SafeZone {

    private String safeZoneId;    // Partition key
    private String gameId;        // Identifier for the game this safe zone belongs to (Potentially GSI PK)
    private String name;          // User-friendly name for the safe zone
    private Coordinate center;      // Center coordinates of the safe zone (requires Coordinate to be @DynamoDbBean)
    private Double radiusMeters;  // Radius of the safe zone in meters
    private String type;          // Type of safe zone (e.g., "PUBLIC", "PRIVATE", "TIMED") - Consider Enum
    private String createdAt;     // ISO 8601 timestamp of creation
    private String expiresAt;     // Optional ISO 8601 timestamp for timed zones
    // Add other fields as needed, e.g., ownerId for private zones, rules, etc.

    @DynamoDbPartitionKey
    @DynamoDbAttribute("SafeZoneID")
    public String getSafeZoneId() {
        return safeZoneId;
    }

    public void setSafeZoneId(String safeZoneId) {
        this.safeZoneId = safeZoneId;
    }

    // Add explicit GSI annotation
    @DynamoDbSecondaryPartitionKey(indexNames = "GameIdIndex") 
    @DynamoDbAttribute("GameID")
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    @DynamoDbAttribute("Name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // Requires Coordinate class to be annotated with @DynamoDbBean
    // or use a custom AttributeConverter
    @DynamoDbAttribute("Center")
    public Coordinate getCenter() {
        return center;
    }

    public void setCenter(Coordinate center) {
        this.center = center;
    }

    @DynamoDbAttribute("RadiusMeters")
    public Double getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(Double radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    @DynamoDbAttribute("Type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @DynamoDbAttribute("CreatedAt")
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("ExpiresAt")
    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SafeZone safeZone = (SafeZone) o;
        return Objects.equals(safeZoneId, safeZone.safeZoneId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(safeZoneId);
    }

    @Override
    public String toString() {
        return "SafeZone{" +
               "safeZoneId='" + safeZoneId + '\'' +
               ", gameId='" + gameId + '\'' +
               ", name='" + name + '\'' +
               ", center=" + center +
               ", radiusMeters=" + radiusMeters +
               ", type='" + type + '\'' +
               ", createdAt='" + createdAt + '\'' +
               ", expiresAt='" + expiresAt + '\'' +
               '}';
    }
} 