package com.assassin.config;

import java.util.Objects;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;

/**
 * Represents a phase in the shrinking safe zone mechanic.
 * Each phase defines how long players wait before the zone shrinks,
 * how long the shrinking takes, what the target radius is, and
 * how much damage players take outside the zone during this phase.
 */
@DynamoDBDocument
public class ZonePhase {
    
    private Integer phaseIndex;
    private Integer waitTimeSeconds;
    private Integer shrinkTimeSeconds;
    private Double targetRadiusMeters;
    private Double damagePerSecond;
    
    // Default constructor
    public ZonePhase() {
    }
    
    // Constructor with parameters
    public ZonePhase(Integer phaseIndex, Integer waitTimeSeconds, Integer shrinkTimeSeconds, 
                     Double targetRadiusMeters, Double damagePerSecond) {
        this.phaseIndex = phaseIndex;
        this.waitTimeSeconds = waitTimeSeconds;
        this.shrinkTimeSeconds = shrinkTimeSeconds;
        this.targetRadiusMeters = targetRadiusMeters;
        this.damagePerSecond = damagePerSecond;
    }
    
    // Getters and setters
    public Integer getPhaseIndex() {
        return phaseIndex;
    }
    
    public void setPhaseIndex(Integer phaseIndex) {
        this.phaseIndex = phaseIndex;
    }
    
    public Integer getWaitTimeSeconds() {
        return waitTimeSeconds;
    }
    
    public void setWaitTimeSeconds(Integer waitTimeSeconds) {
        this.waitTimeSeconds = waitTimeSeconds;
    }
    
    public Integer getShrinkTimeSeconds() {
        return shrinkTimeSeconds;
    }
    
    public void setShrinkTimeSeconds(Integer shrinkTimeSeconds) {
        this.shrinkTimeSeconds = shrinkTimeSeconds;
    }
    
    public Double getTargetRadiusMeters() {
        return targetRadiusMeters;
    }
    
    public void setTargetRadiusMeters(Double targetRadiusMeters) {
        this.targetRadiusMeters = targetRadiusMeters;
    }
    
    public Double getDamagePerSecond() {
        return damagePerSecond;
    }
    
    public void setDamagePerSecond(Double damagePerSecond) {
        this.damagePerSecond = damagePerSecond;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZonePhase zonePhase = (ZonePhase) o;
        return Objects.equals(phaseIndex, zonePhase.phaseIndex) &&
               Objects.equals(waitTimeSeconds, zonePhase.waitTimeSeconds) &&
               Objects.equals(shrinkTimeSeconds, zonePhase.shrinkTimeSeconds) &&
               Objects.equals(targetRadiusMeters, zonePhase.targetRadiusMeters) &&
               Objects.equals(damagePerSecond, zonePhase.damagePerSecond);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(phaseIndex, waitTimeSeconds, shrinkTimeSeconds, 
                           targetRadiusMeters, damagePerSecond);
    }
    
    @Override
    public String toString() {
        return "ZonePhase{" +
               "phaseIndex=" + phaseIndex +
               ", waitTimeSeconds=" + waitTimeSeconds +
               ", shrinkTimeSeconds=" + shrinkTimeSeconds +
               ", targetRadiusMeters=" + targetRadiusMeters +
               ", damagePerSecond=" + damagePerSecond +
               '}';
    }
} 