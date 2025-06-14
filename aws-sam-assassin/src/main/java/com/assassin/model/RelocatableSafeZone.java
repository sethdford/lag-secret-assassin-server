package com.assassin.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Represents a relocatable safe zone that can be moved by its owner.
 * Keeps track of movement history and enforces cooldown periods between moves.
 */
@DynamoDbBean
public class RelocatableSafeZone extends SafeZone {
    
    /**
     * Represents a single movement of the safe zone.
     */
    @DynamoDbBean
    public static class Movement {
        private Double latitude;
        private Double longitude;
        private String timestamp;
        
        public Movement() {
            // Default constructor required by DynamoDB Enhanced Client
        }
        
        public Movement(Double latitude, Double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = Instant.now().toString();
        }
        
        @DynamoDbAttribute("Latitude")
        public Double getLatitude() {
            return latitude;
        }
        
        public void setLatitude(Double latitude) {
            this.latitude = latitude;
        }
        
        @DynamoDbAttribute("Longitude")
        public Double getLongitude() {
            return longitude;
        }
        
        public void setLongitude(Double longitude) {
            this.longitude = longitude;
        }
        
        @DynamoDbAttribute("Timestamp")
        public String getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }
    
    private List<Movement> movementHistory;
    private Integer cooldownSeconds; // Cooldown period in seconds between relocations
    private String lastMovedAt; // ISO-8601 timestamp of last relocation
    
    /**
     * Creates a new relocatable safe zone with default properties.
     */
    public RelocatableSafeZone() {
        super();
        setType(SafeZoneType.RELOCATABLE);
        this.movementHistory = new ArrayList<>();
        this.cooldownSeconds = 3600; // Default: 1 hour cooldown
    }
    
    /**
     * Creates a new relocatable safe zone with the specified properties.
     * 
     * @param gameId The ID of the game this safe zone belongs to
     * @param name The name of the safe zone
     * @param description The description of the safe zone
     * @param latitude The latitude of the safe zone center
     * @param longitude The longitude of the safe zone center
     * @param radiusMeters The radius of the safe zone in meters
     * @param createdBy The ID of the player who created the safe zone
     * @param cooldownSeconds The cooldown period in seconds between relocations
     */
    public RelocatableSafeZone(String gameId, String name, String description, 
                              Double latitude, Double longitude, Double radiusMeters, 
                              String createdBy, Integer cooldownSeconds) {
        super();
        // Initialize base SafeZone fields
        setSafeZoneId(java.util.UUID.randomUUID().toString());
        setGameId(gameId);
        setName(name);
        setDescription(description);
        setLatitude(latitude);
        setLongitude(longitude);
        setRadiusMeters(radiusMeters);
        setCreatedBy(createdBy);
        setType(SafeZoneType.RELOCATABLE);
        setCreatedAt(Instant.now().toString());
        setLastModifiedAt(getCreatedAt());
        setIsActive(true);
        setRelocationCount(0); // Initialize relocation count to 0
        
        this.movementHistory = new ArrayList<>();
        // Record initial position in movement history
        this.movementHistory.add(new Movement(latitude, longitude));
        
        this.cooldownSeconds = (cooldownSeconds != null) ? cooldownSeconds : 3600;
        this.lastMovedAt = Instant.now().toString();
    }
    
    /**
     * Relocates this safe zone to a new position.
     * 
     * @param newLatitude The new latitude for the zone center
     * @param newLongitude The new longitude for the zone center
     * @return true if relocation was successful, false if on cooldown
     */
    public boolean relocate(Double newLatitude, Double newLongitude) {
        if (isOnCooldown()) {
            return false;
        }
        
        // Update the zone's location
        setLatitude(newLatitude);
        setLongitude(newLongitude);
        
        // Record the movement
        Movement movement = new Movement(newLatitude, newLongitude);
        movementHistory.add(movement);
        
        // Update the last moved timestamp
        lastMovedAt = Instant.now().toString();
        
        return true;
    }
    
    /**
     * Checks if this safe zone is currently on cooldown and cannot be moved.
     * 
     * @return true if the zone is on cooldown, false if it can be moved
     */
    public boolean isOnCooldown() {
        if (lastMovedAt == null || cooldownSeconds == null) {
            return false;
        }
        
        try {
            Instant lastMoved = Instant.parse(lastMovedAt);
            Instant cooldownEnd = lastMoved.plusSeconds(cooldownSeconds);
            Instant now = Instant.now();
            
            return now.isBefore(cooldownEnd);
        } catch (RuntimeException e) {
            // If there's any error parsing timestamps, assume not on cooldown
            return false;
        }
    }
    
    /**
     * Gets the time remaining on the cooldown in seconds.
     * 
     * @return The number of seconds until the zone can be moved again, or 0 if not on cooldown
     */
    public long getCooldownRemainingSeconds() {
        if (lastMovedAt == null || cooldownSeconds == null) {
            return 0;
        }
        
        try {
            Instant lastMoved = Instant.parse(lastMovedAt);
            Instant cooldownEnd = lastMoved.plusSeconds(cooldownSeconds);
            Instant now = Instant.now();
            
            if (now.isAfter(cooldownEnd)) {
                return 0;
            }
            
            return cooldownEnd.getEpochSecond() - now.getEpochSecond();
        } catch (RuntimeException e) {
            return 0;
        }
    }
    
    @DynamoDbAttribute("MovementHistory")
    public List<Movement> getMovementHistory() {
        return movementHistory;
    }
    
    public void setMovementHistory(List<Movement> movementHistory) {
        this.movementHistory = movementHistory;
    }
    
    @DynamoDbAttribute("CooldownSeconds")
    public Integer getCooldownSeconds() {
        return cooldownSeconds;
    }
    
    public void setCooldownSeconds(Integer cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }
    
    @DynamoDbAttribute("LastMovedAt")
    public String getLastMovedAt() {
        return lastMovedAt;
    }
    
    public void setLastMovedAt(String lastMovedAt) {
        this.lastMovedAt = lastMovedAt;
    }
} 