package com.assassin.model;

import java.time.Instant;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Represents a timed safe zone that is only active during a specific time period.
 * The zone automatically activates at startTime and deactivates at endTime.
 */
@DynamoDbBean
public class TimedSafeZone extends SafeZone {
    
    // Note: startTime and endTime are inherited from SafeZone base class
    
    /**
     * Creates a new timed safe zone with default properties.
     */
    public TimedSafeZone() {
        super();
        setType(SafeZoneType.TIMED);
        setIsActive(true); // Initialize to true by default
    }
    
    /**
     * Creates a new timed safe zone with the specified properties.
     * 
     * @param gameId The ID of the game this safe zone belongs to
     * @param name The name of the safe zone
     * @param description The description of the safe zone
     * @param latitude The latitude of the safe zone center
     * @param longitude The longitude of the safe zone center
     * @param radiusMeters The radius of the safe zone in meters
     * @param createdBy The ID of the player who created the safe zone
     * @param startTime The ISO-8601 timestamp when the zone becomes active
     * @param endTime The ISO-8601 timestamp when the zone becomes inactive
     */
    public TimedSafeZone(String gameId, String name, String description, 
                         Double latitude, Double longitude, Double radiusMeters, 
                         String createdBy, String startTime, String endTime) {
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
        setType(SafeZoneType.TIMED);
        setCreatedAt(Instant.now().toString());
        setLastModifiedAt(getCreatedAt());
        setIsActive(true); // Initialize to true by default
        setStartTime(startTime);
        setEndTime(endTime);
    }
    
    /**
     * Checks if this timed safe zone is active at the given timestamp.
     * A timed zone is active if the current time is between startTime and endTime.
     * 
     * @param timestamp The time to check (Unix timestamp in milliseconds)
     * @return true if the zone is active at the given time, false otherwise
     */
    @Override
    public boolean isActiveAt(long timestamp) {
        // First check the base isActive flag - default to true if null
        Boolean isActive = getIsActive();
        if (isActive != null && !isActive) {
            return false;
        }
        
        // If either time bound is missing, rely on the base isActive flag
        String startTime = getStartTime();
        String endTime = getEndTime();
        if (startTime == null || endTime == null) {
            return isActive != null ? isActive : true; // Default to true if null
        }
        
        try {
            // Parse the ISO-8601 timestamps to milliseconds
            long startTimeMs = Instant.parse(startTime).toEpochMilli();
            long endTimeMs = Instant.parse(endTime).toEpochMilli();
            
            // Check if the provided timestamp is within the active period
            return timestamp >= startTimeMs && timestamp <= endTimeMs;
        } catch (RuntimeException e) {
            // If there's any error parsing the timestamps, fall back to the general isActive status
            // Consider logging this exception
            return isActive != null ? isActive : true; // Default to true if null
        }
    }
    
    /**
     * Checks if this timed safe zone has expired.
     * 
     * @return true if the end time has passed, false otherwise
     */
    public boolean hasExpired() {
        String endTime = getEndTime();
        if (endTime == null) {
            return false;
        }
        
        try {
            long endTimeMs = Instant.parse(endTime).toEpochMilli();
            long currentTimeMs = System.currentTimeMillis();
            return currentTimeMs > endTimeMs;
        } catch (RuntimeException e) {
            return false;
        }
    }
    
    // Note: getStartTime() and getEndTime() are inherited from SafeZone base class
    // No need to redefine them here
} 