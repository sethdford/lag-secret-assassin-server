package com.assassin.model.export;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data transfer object for exporting location data suitable for heatmap generation.
 * Contains anonymized location event data for visualization and analysis.
 */
public class LocationHeatmapData {
    
    @JsonProperty("latitude")
    private Double latitude;
    
    @JsonProperty("longitude")
    private Double longitude;
    
    @JsonProperty("timestamp")
    private String timestamp;
    
    @JsonProperty("game_id")
    private String gameId;
    
    @JsonProperty("event_type")
    private String eventType; // e.g., "kill", "death", "location_update"
    
    @JsonProperty("intensity")
    private Double intensity; // Weight for heatmap visualization
    
    @JsonProperty("killer_id")
    private String killerId; // Anonymized killer ID
    
    @JsonProperty("victim_id")
    private String victimId; // Anonymized victim ID
    
    @JsonProperty("verification_status")
    private String verificationStatus;
    
    @JsonProperty("zone_type")
    private String zoneType; // e.g., "safe_zone", "shrinking_zone", "normal"
    
    // Default constructor
    public LocationHeatmapData() {}
    
    // Getters and setters
    public Double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
    
    public Double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getGameId() {
        return gameId;
    }
    
    public void setGameId(String gameId) {
        this.gameId = gameId;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public Double getIntensity() {
        return intensity;
    }
    
    public void setIntensity(Double intensity) {
        this.intensity = intensity;
    }
    
    public String getKillerId() {
        return killerId;
    }
    
    public void setKillerId(String killerId) {
        this.killerId = killerId;
    }
    
    public String getVictimId() {
        return victimId;
    }
    
    public void setVictimId(String victimId) {
        this.victimId = victimId;
    }
    
    public String getVerificationStatus() {
        return verificationStatus;
    }
    
    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }
    
    public String getZoneType() {
        return zoneType;
    }
    
    public void setZoneType(String zoneType) {
        this.zoneType = zoneType;
    }
    
    @Override
    public String toString() {
        return "LocationHeatmapData{" +
                "latitude=" + latitude +
                ", longitude=" + longitude +
                ", timestamp='" + timestamp + '\'' +
                ", gameId='" + gameId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", intensity=" + intensity +
                ", killerId='" + killerId + '\'' +
                ", victimId='" + victimId + '\'' +
                ", verificationStatus='" + verificationStatus + '\'' +
                ", zoneType='" + zoneType + '\'' +
                '}';
    }
} 