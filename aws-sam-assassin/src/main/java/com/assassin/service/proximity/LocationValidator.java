package com.assassin.service.proximity;

import com.assassin.model.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Validates location data for accuracy and freshness.
 * Ensures location-based calculations use reliable data.
 */
public class LocationValidator {
    private static final Logger logger = LoggerFactory.getLogger(LocationValidator.class);
    
    // Maximum age of location data to be considered valid (milliseconds)
    private static final long DEFAULT_LOCATION_STALENESS_THRESHOLD_MS = 60000; // 60 seconds
    
    // Minimum accuracy required for location data (meters)
    private static final double DEFAULT_MIN_ACCURACY = 50.0;
    
    // Maximum reasonable speed for a player (m/s) - helps detect teleportation cheats
    private static final double MAX_REASONABLE_SPEED_MS = 50.0; // ~180 km/h
    
    private final long locationStalenessThresholdMs;
    private final double minAccuracy;
    
    public LocationValidator() {
        this(DEFAULT_LOCATION_STALENESS_THRESHOLD_MS, DEFAULT_MIN_ACCURACY);
    }
    
    public LocationValidator(long locationStalenessThresholdMs, double minAccuracy) {
        this.locationStalenessThresholdMs = locationStalenessThresholdMs;
        this.minAccuracy = minAccuracy;
    }
    
    /**
     * Validates if a player's location data is fresh and accurate enough for use.
     * 
     * @param player Player whose location to validate
     * @return true if location is valid
     */
    public boolean isLocationValid(Player player) {
        if (player == null) {
            logger.warn("Cannot validate null player");
            return false;
        }
        
        // Check if location exists
        if (player.getLatitude() == null || player.getLongitude() == null) {
            logger.debug("Player {} has no location data", player.getPlayerID());
            return false;
        }
        
        // Check location freshness
        if (!isLocationFresh(player)) {
            logger.debug("Player {} location is stale", player.getPlayerID());
            return false;
        }
        
        // Check location accuracy if available
        if (player.getLocationAccuracy() != null && player.getLocationAccuracy() > minAccuracy) {
            logger.debug("Player {} location accuracy {}m exceeds threshold {}m", 
                player.getPlayerID(), player.getLocationAccuracy(), minAccuracy);
            return false;
        }
        
        // Validate coordinate ranges
        if (!isValidCoordinate(player.getLatitude(), player.getLongitude())) {
            logger.warn("Player {} has invalid coordinates: {}, {}", 
                player.getPlayerID(), player.getLatitude(), player.getLongitude());
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks if a player's location is fresh (recently updated).
     * 
     * @param player Player to check
     * @return true if location is fresh
     */
    public boolean isLocationFresh(Player player) {
        if (player.getLocationTimestamp() == null) {
            return false;
        }
        
        try {
            Instant lastUpdate = Instant.parse(player.getLocationTimestamp());
            long ageMs = Instant.now().toEpochMilli() - lastUpdate.toEpochMilli();
            
            return ageMs <= locationStalenessThresholdMs;
        } catch (DateTimeParseException e) {
            logger.error("Invalid timestamp format for player {}: {}", 
                player.getPlayerID(), player.getLocationTimestamp());
            return false;
        }
    }
    
    /**
     * Validates if coordinates are within valid ranges.
     * 
     * @param latitude Latitude to validate
     * @param longitude Longitude to validate
     * @return true if coordinates are valid
     */
    public boolean isValidCoordinate(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        
        // Latitude must be between -90 and 90
        if (latitude < -90 || latitude > 90) {
            return false;
        }
        
        // Longitude must be between -180 and 180
        if (longitude < -180 || longitude > 180) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Calculates the age of location data in milliseconds.
     * 
     * @param player Player whose location age to calculate
     * @return Age in milliseconds, or -1 if cannot be determined
     */
    public long getLocationAge(Player player) {
        if (player.getLocationTimestamp() == null) {
            return -1;
        }
        
        try {
            Instant lastUpdate = Instant.parse(player.getLocationTimestamp());
            return Instant.now().toEpochMilli() - lastUpdate.toEpochMilli();
        } catch (DateTimeParseException e) {
            logger.error("Invalid timestamp format for player {}: {}", 
                player.getPlayerID(), player.getLocationTimestamp());
            return -1;
        }
    }
    
    /**
     * Validates if a location update represents reasonable movement.
     * Helps detect teleportation or GPS spoofing.
     * 
     * @param oldLat Previous latitude
     * @param oldLon Previous longitude
     * @param newLat New latitude
     * @param newLon New longitude
     * @param timeDeltaMs Time between updates in milliseconds
     * @return true if movement is reasonable
     */
    public boolean isMovementReasonable(double oldLat, double oldLon, 
                                      double newLat, double newLon, 
                                      long timeDeltaMs) {
        if (timeDeltaMs <= 0) {
            return false;
        }
        
        // Calculate distance using Haversine formula
        double distance = calculateDistance(oldLat, oldLon, newLat, newLon);
        
        // Calculate speed in m/s
        double speed = distance / (timeDeltaMs / 1000.0);
        
        if (speed > MAX_REASONABLE_SPEED_MS) {
            logger.warn("Unreasonable movement detected: {}m in {}ms = {:.2f}m/s", 
                distance, timeDeltaMs, speed);
            return false;
        }
        
        return true;
    }
    
    /**
     * Simple distance calculation for movement validation.
     * 
     * @param lat1 First latitude
     * @param lon1 First longitude
     * @param lat2 Second latitude
     * @param lon2 Second longitude
     * @return Distance in meters
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth's radius in meters
        
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                  Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                  Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    /**
     * Gets a validation summary for a player's location.
     * 
     * @param player Player to validate
     * @return Human-readable validation summary
     */
    public String getValidationSummary(Player player) {
        StringBuilder summary = new StringBuilder();
        summary.append("Location validation for player ").append(player.getPlayerID()).append(": ");
        
        if (!isLocationValid(player)) {
            if (player.getLatitude() == null || player.getLongitude() == null) {
                summary.append("No location data");
            } else if (!isLocationFresh(player)) {
                long age = getLocationAge(player);
                summary.append("Stale location (age: ").append(age / 1000).append("s)");
            } else if (player.getLocationAccuracy() != null && player.getLocationAccuracy() > minAccuracy) {
                summary.append("Poor accuracy (").append(player.getLocationAccuracy()).append("m)");
            } else {
                summary.append("Invalid coordinates");
            }
        } else {
            summary.append("Valid");
        }
        
        return summary.toString();
    }
}