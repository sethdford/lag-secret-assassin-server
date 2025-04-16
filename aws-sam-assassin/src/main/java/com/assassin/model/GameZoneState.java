package com.assassin.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Represents the dynamic state of the shrinking zone for an active game.
 */
@DynamoDbBean
public class GameZoneState {

    private String gameId; // Partition Key - Links to the Game
    private Integer currentStageIndex;
    private String currentPhase; // e.g., "WAITING", "SHRINKING"
    private String phaseEndTime; // ISO 8601 timestamp when the current phase ends
    private Double currentRadiusMeters;
    private Coordinate currentCenter;
    // Additional fields for testing
    private Double nextRadiusMeters;
    private Double targetCenterLatitude;
    private Double targetCenterLongitude;
    private Long stageStartTimeEpochMillis;
    private Long nextShrinkTimeEpochMillis;
    private Long lastUpdateTimeEpochMillis;
    private String lastUpdated; // ISO 8601 timestamp of the last update
    
    // Default constructor
    public GameZoneState() {
    }
    
    // Copy constructor for test convenience
    public GameZoneState(GameZoneState other) {
        if (other != null) {
            this.gameId = other.gameId;
            this.currentStageIndex = other.currentStageIndex;
            this.currentPhase = other.currentPhase;
            this.phaseEndTime = other.phaseEndTime;
            this.currentRadiusMeters = other.currentRadiusMeters;
            this.currentCenter = other.currentCenter;
            this.nextRadiusMeters = other.nextRadiusMeters;
            this.targetCenterLatitude = other.targetCenterLatitude;
            this.targetCenterLongitude = other.targetCenterLongitude;
            this.stageStartTimeEpochMillis = other.stageStartTimeEpochMillis;
            this.nextShrinkTimeEpochMillis = other.nextShrinkTimeEpochMillis;
            this.lastUpdateTimeEpochMillis = other.lastUpdateTimeEpochMillis;
            this.lastUpdated = other.lastUpdated;
        }
    }

    // Enum for phase status
    public enum ZonePhase {
        WAITING, // Waiting before the next shrink starts
        SHRINKING, // Zone is actively shrinking
        FINISHED // All stages completed
        // Consider IDLE or INACTIVE if needed before game start?
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("GameID")
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public Integer getCurrentStageIndex() {
        return currentStageIndex;
    }

    public void setCurrentStageIndex(Integer currentStageIndex) {
        this.currentStageIndex = currentStageIndex;
    }

    public String getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(String currentPhase) {
        this.currentPhase = currentPhase;
    }

    // Helper to set phase using enum
    public void setCurrentPhase(ZonePhase phase) {
        this.currentPhase = (phase == null) ? null : phase.name();
    }

    // Helper to get phase as enum
    public ZonePhase getCurrentPhaseAsEnum() {
        try {
            return (this.currentPhase == null) ? null : ZonePhase.valueOf(this.currentPhase);
        } catch (IllegalArgumentException e) {
            return null; // Or throw an error
        }
    }

    public String getPhaseEndTime() {
        return phaseEndTime;
    }

    public void setPhaseEndTime(String phaseEndTime) {
        this.phaseEndTime = phaseEndTime;
    }

    public Double getCurrentRadiusMeters() {
        return currentRadiusMeters;
    }

    public void setCurrentRadiusMeters(Double currentRadiusMeters) {
        this.currentRadiusMeters = currentRadiusMeters;
    }

    // We need a converter for Coordinate if it's not directly supported
    // Assuming Coordinate can be stored as a Map {latitude: N, longitude: N}
    // Or potentially use a custom converter like in Game.java for settings
    public Coordinate getCurrentCenter() {
        return currentCenter;
    }

    public void setCurrentCenter(Coordinate currentCenter) {
        this.currentCenter = currentCenter;
    }
    
    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    // Additional getters and setters for testing
    public Double getNextRadiusMeters() {
        return nextRadiusMeters;
    }

    public void setNextRadiusMeters(Double nextRadiusMeters) {
        this.nextRadiusMeters = nextRadiusMeters;
    }

    public Double getTargetCenterLatitude() {
        return targetCenterLatitude;
    }

    public void setTargetCenterLatitude(Double targetCenterLatitude) {
        this.targetCenterLatitude = targetCenterLatitude;
    }

    public Double getTargetCenterLongitude() {
        return targetCenterLongitude;
    }

    public void setTargetCenterLongitude(Double targetCenterLongitude) {
        this.targetCenterLongitude = targetCenterLongitude;
    }

    public Long getStageStartTimeEpochMillis() {
        return stageStartTimeEpochMillis;
    }

    public void setStageStartTimeEpochMillis(Long stageStartTimeEpochMillis) {
        this.stageStartTimeEpochMillis = stageStartTimeEpochMillis;
    }

    public Long getNextShrinkTimeEpochMillis() {
        return nextShrinkTimeEpochMillis;
    }

    public void setNextShrinkTimeEpochMillis(Long nextShrinkTimeEpochMillis) {
        this.nextShrinkTimeEpochMillis = nextShrinkTimeEpochMillis;
    }

    public Long getLastUpdateTimeEpochMillis() {
        return lastUpdateTimeEpochMillis;
    }

    public void setLastUpdateTimeEpochMillis(Long lastUpdateTimeEpochMillis) {
        this.lastUpdateTimeEpochMillis = lastUpdateTimeEpochMillis;
    }

    // toString, equals, hashCode 
    // ... (implement if needed)
} 