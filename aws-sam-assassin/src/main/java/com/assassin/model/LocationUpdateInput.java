package com.assassin.model;

import java.util.Objects;

/**
 * Represents the expected input structure for player location updates.
 * Used for deserializing the request body in LocationHandler.
 */
public class LocationUpdateInput {

    private Double latitude;
    private Double longitude;
    private String timestamp; // Expecting ISO 8601 format string
    private Double accuracy; // Optional, in meters

    // Getters are needed for Gson deserialization and handler logic
    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    // Setters are generally good practice for DTOs, although Gson might not require them
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }

    // Optional: toString, equals, hashCode for debugging or collections
    @Override
    public String toString() {
        return "LocationUpdateInput{" +
               "latitude=" + latitude +
               ", longitude=" + longitude +
               ", timestamp='" + timestamp + '\'' +
               ", accuracy=" + accuracy +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocationUpdateInput that = (LocationUpdateInput) o;
        return Objects.equals(latitude, that.latitude) &&
               Objects.equals(longitude, that.longitude) &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(accuracy, that.accuracy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, timestamp, accuracy);
    }
} 