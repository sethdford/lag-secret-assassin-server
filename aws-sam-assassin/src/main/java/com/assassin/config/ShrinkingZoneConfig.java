package com.assassin.config;

import java.util.List;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Configuration for a shrinking zone in the game.
 * This class defines whether the shrinking zone is enabled and its phases.
 */
// Replace SDK v1 annotation with SDK v2 equivalent
@DynamoDbBean
public class ShrinkingZoneConfig {
    
    private Boolean enabled;
    private List<ZonePhase> phases;
    
    // Default constructor
    public ShrinkingZoneConfig() {
    }
    
    // Constructor with parameters
    public ShrinkingZoneConfig(Boolean enabled, List<ZonePhase> phases) {
        this.enabled = enabled;
        this.phases = phases;
    }
    
    // Getters and setters
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    public List<ZonePhase> getPhases() {
        return phases;
    }
    
    public void setPhases(List<ZonePhase> phases) {
        this.phases = phases;
    }
} 