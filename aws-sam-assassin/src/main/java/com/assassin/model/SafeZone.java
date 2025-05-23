package com.assassin.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Base class for all safe zones in the game.
 * Safe zones are areas where players cannot be eliminated.
 */
@DynamoDbBean
public class SafeZone {
    
    public enum SafeZoneType {
        PUBLIC,     // Zones where all players are safe
        PRIVATE,    // Zones where only specific players are safe (e.g., owner)
        TIMED,      // Zones that are active only during certain time periods
        RELOCATABLE // Zones that can be moved by their owner
    }
    
    private String safeZoneId;
    private String gameId;
    private String name;
    private String description;
    private Double latitude;
    private Double longitude;
    private Double radiusMeters;
    private String createdBy;
    private String createdAt;
    private String lastModifiedAt;
    private SafeZoneType type;
    private Boolean isActive;
    private Set<String> authorizedPlayerIds; // For PRIVATE zones
    private String startTime; // For TIMED zones (ISO 8601 format)
    private String endTime;   // For TIMED zones (ISO 8601 format)
    private Integer relocationCount; // For RELOCATABLE zones
    private String lastRelocationTime; // For RELOCATABLE zones (ISO 8601 format)
    
    // Private constructor to be used by factory methods
    private SafeZone(SafeZoneType type) {
        this.safeZoneId = UUID.randomUUID().toString();
        this.createdAt = Instant.now().toString();
        this.lastModifiedAt = this.createdAt;
        this.isActive = true;
        this.authorizedPlayerIds = new HashSet<>();
        this.type = type;
        if (type == SafeZoneType.RELOCATABLE) {
            this.relocationCount = 0;
        }
    }

    // Default constructor for DynamoDB marshalling - should not be used directly for creation
    public SafeZone() {
        // Fields will be populated by DynamoDB Unmarshaller
    }

    // Factory methods
    public static SafeZone createPublicZone(String gameId, String name, String description, 
                                            Double latitude, Double longitude, Double radiusMeters, String createdBy) {
        SafeZone zone = new SafeZone(SafeZoneType.PUBLIC);
        zone.setGameId(gameId);
        zone.setName(name);
        zone.setDescription(description);
        zone.setLatitude(latitude);
        zone.setLongitude(longitude);
        zone.setRadiusMeters(radiusMeters);
        zone.setCreatedBy(createdBy);
        return zone;
    }

    public static SafeZone createPrivateZone(String gameId, String name, String description, 
                                             Double latitude, Double longitude, Double radiusMeters, String createdByPlayerId,
                                             Set<String> authorizedIds) {
        SafeZone zone = new SafeZone(SafeZoneType.PRIVATE);
        zone.setGameId(gameId);
        zone.setName(name);
        zone.setDescription(description);
        zone.setLatitude(latitude);
        zone.setLongitude(longitude);
        zone.setRadiusMeters(radiusMeters);
        zone.setCreatedBy(createdByPlayerId); // Owner of the private zone
        
        // Convert to mutable set if needed
        if (authorizedIds != null) {
            zone.setAuthorizedPlayerIds(new HashSet<>(authorizedIds));
        } else {
            zone.setAuthorizedPlayerIds(new HashSet<>());
        }
        
        // Ensure owner is always authorized if not in the set
        zone.addAuthorizedPlayer(createdByPlayerId);
        return zone;
    }

    public static TimedSafeZone createTimedZone(String gameId, String name, String description, 
                                           Double latitude, Double longitude, Double radiusMeters, String createdBy,
                                           String startTime, String endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time are required for timed safe zones.");
        }
        // Use the TimedSafeZone constructor directly
        TimedSafeZone zone = new TimedSafeZone(gameId, name, description, latitude, longitude, radiusMeters, createdBy, startTime, endTime);
        return zone;
    }

    public static RelocatableSafeZone createRelocatableZone(String gameId, String name, String description, 
                                               Double latitude, Double longitude, Double radiusMeters, String createdBy) {
        // Use the RelocatableSafeZone constructor, passing null for cooldownSeconds to use its default
        RelocatableSafeZone zone = new RelocatableSafeZone(gameId, name, description, latitude, longitude, radiusMeters, createdBy, null);
        return zone;
    }
    
    /**
     * Checks if a location is within this safe zone.
     * @param latitude The latitude to check
     * @param longitude The longitude to check
     * @return true if the location is within the safe zone, false otherwise
     */
    public boolean containsLocation(double latitude, double longitude) {
        if (this.latitude == null || this.longitude == null || this.radiusMeters == null) {
            return false;
        }
        
        // Calculate distance between safe zone center and the given location
        double distance = calculateDistance(this.latitude, this.longitude, latitude, longitude);
        
        // Location is within safe zone if distance is less than or equal to radius
        return distance <= this.radiusMeters;
    }
    
    /**
     * Checks if a player is authorized to be protected in this safe zone.
     * For PUBLIC zones, always returns true.
     * For PRIVATE zones, checks if the player is in the authorized list.
     * @param playerId The ID of the player to check
     * @return true if the player is authorized, false otherwise
     */
    public boolean isPlayerAuthorized(String playerId) {
        if (type == SafeZoneType.PUBLIC) {
            return true;
        }
        
        if (type == SafeZoneType.PRIVATE) {
            return authorizedPlayerIds.contains(playerId) || playerId.equals(createdBy);
        }
        
        return false;
    }
    
    /**
     * Checks if this safe zone is active at the given time.
     * Base implementation always returns the isActive flag value.
     * Subclasses like TimedSafeZone may override this.
     * @param timestamp The time to check (Unix timestamp in milliseconds)
     * @return true if the safe zone is active, false otherwise
     */
    public boolean isActiveAt(long timestamp) {
        return isActive;
    }
    
    /**
     * Calculates the distance between two coordinates in meters.
     * Uses the Haversine formula for great-circle distance.
     */
    protected double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth radius in meters
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
    
    // Getters and Setters
    
    @DynamoDbPartitionKey
    @DynamoDbAttribute("SafeZoneID")
    public String getSafeZoneId() {
        return safeZoneId;
    }
    
    public void setSafeZoneId(String safeZoneId) {
        this.safeZoneId = safeZoneId;
    }
    
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
    
    @DynamoDbAttribute("Description")
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
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
    
    @DynamoDbAttribute("RadiusMeters")
    public Double getRadiusMeters() {
        return radiusMeters;
    }
    
    public void setRadiusMeters(Double radiusMeters) {
        this.radiusMeters = radiusMeters;
    }
    
    @DynamoDbAttribute("CreatedBy")
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    @DynamoDbAttribute("CreatedAt")
    public String getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    
    @DynamoDbAttribute("LastModifiedAt")
    public String getLastModifiedAt() {
        return lastModifiedAt;
    }
    
    public void setLastModifiedAt(String lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
    }
    
    @DynamoDbAttribute("Type")
    public SafeZoneType getType() {
        return type;
    }
    
    public void setType(SafeZoneType type) {
        this.type = type;
    }
    
    @DynamoDbAttribute("IsActive")
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    @DynamoDbAttribute("AuthorizedPlayerIDs")
    public Set<String> getAuthorizedPlayerIds() {
        return authorizedPlayerIds;
    }
    
    public void setAuthorizedPlayerIds(Set<String> authorizedPlayerIds) {
        this.authorizedPlayerIds = authorizedPlayerIds;
    }
    
    public void addAuthorizedPlayer(String playerId) {
        if (this.authorizedPlayerIds == null) {
            this.authorizedPlayerIds = new HashSet<>();
        }
        this.authorizedPlayerIds.add(playerId);
    }
    
    public void removeAuthorizedPlayer(String playerId) {
        if (this.authorizedPlayerIds != null) {
            this.authorizedPlayerIds.remove(playerId);
        }
    }
    
    @DynamoDbAttribute("StartTime")
    public String getStartTime() {
        return startTime;
    }
    
    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }
    
    @DynamoDbAttribute("EndTime")
    public String getEndTime() {
        return endTime;
    }
    
    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
    
    @DynamoDbAttribute("RelocationCount")
    public Integer getRelocationCount() {
        return relocationCount;
    }
    
    public void setRelocationCount(Integer relocationCount) {
        this.relocationCount = relocationCount;
    }
    
    @DynamoDbAttribute("LastRelocationTime")
    public String getLastRelocationTime() {
        return lastRelocationTime;
    }
    
    public void setLastRelocationTime(String lastRelocationTime) {
        this.lastRelocationTime = lastRelocationTime;
    }

    /**
     * Validates the safe zone's state based on its type.
     * @throws IllegalStateException if the safe zone is invalid.
     */
    public void validate() throws IllegalStateException {
        if (type == null) {
            throw new IllegalStateException("Safe zone type cannot be null.");
        }
        if (gameId == null || gameId.isEmpty()) {
            throw new IllegalStateException("GameID cannot be null or empty.");
        }
        if (latitude == null || longitude == null || radiusMeters == null || radiusMeters <= 0) {
            throw new IllegalStateException("Latitude, longitude, and a positive radius are required.");
        }

        switch (type) {
            case TIMED:
                if (startTime == null || endTime == null) {
                    throw new IllegalStateException("Timed safe zones require a start and end time.");
                }
                // Optional: Add validation for startTime < endTime
                try {
                    Instant start = Instant.parse(startTime);
                    Instant end = Instant.parse(endTime);
                    if (start.isAfter(end)) {
                        throw new IllegalStateException("Start time must be before end time for timed safe zones.");
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Invalid start or end time format for timed safe zone.", e);
                }
                break;
            case PRIVATE:
                if (createdBy == null || createdBy.isEmpty()) {
                    throw new IllegalStateException("Private safe zones require a creator (owner).");
                }
                break;
            case RELOCATABLE:
                if (relocationCount == null || relocationCount < 0) {
                    throw new IllegalStateException("Relocatable safe zones require a non-negative relocation count.");
                }
                if (createdBy == null || createdBy.isEmpty()) {
                    throw new IllegalStateException("Relocatable safe zones require a creator.");
                }
                break;
            case PUBLIC:
                // No type-specific fields are strictly mandatory for PUBLIC beyond base fields.
                break;
            default:
                throw new IllegalStateException("Unknown or unsupported safe zone type: " + type);
        }
    }
} 