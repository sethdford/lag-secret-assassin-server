package com.assassin.model;

/**
 * Represents the configuration for a single stage of a shrinking safe zone.
 */
public class ShrinkingZoneStage {
    
    private Integer stageIndex;
    private Integer waitTimeSeconds;
    private Integer transitionTimeSeconds;
    private Double endRadiusMeters; // Radius at the *end* of this stage's transition
    private Double damagePerSecond; // Damage dealt outside the zone during this stage
    // Optional: Add fields for center movement rules if needed

    // Getters and Setters
    public Integer getStageIndex() {
        return stageIndex;
    }

    public void setStageIndex(Integer stageIndex) {
        this.stageIndex = stageIndex;
    }

    public Integer getWaitTimeSeconds() {
        return waitTimeSeconds;
    }

    public void setWaitTimeSeconds(Integer waitTimeSeconds) {
        this.waitTimeSeconds = waitTimeSeconds;
    }

    public Integer getTransitionTimeSeconds() {
        return transitionTimeSeconds;
    }

    public void setTransitionTimeSeconds(Integer transitionTimeSeconds) {
        this.transitionTimeSeconds = transitionTimeSeconds;
    }

    public Double getEndRadiusMeters() {
        return endRadiusMeters;
    }

    public void setEndRadiusMeters(Double endRadiusMeters) {
        this.endRadiusMeters = endRadiusMeters;
    }

    public Double getDamagePerSecond() {
        return damagePerSecond;
    }

    public void setDamagePerSecond(Double damagePerSecond) {
        this.damagePerSecond = damagePerSecond;
    }

    // toString, equals, hashCode (optional but recommended)
    @Override
    public String toString() {
        return "ShrinkingZoneStage{" +
               "stageIndex=" + stageIndex +
               ", waitTimeSeconds=" + waitTimeSeconds +
               ", transitionTimeSeconds=" + transitionTimeSeconds +
               ", endRadiusMeters=" + endRadiusMeters +
               ", damagePerSecond=" + damagePerSecond +
               '}';
    }

    // Add equals() and hashCode() if needed for comparisons or use in collections
} 