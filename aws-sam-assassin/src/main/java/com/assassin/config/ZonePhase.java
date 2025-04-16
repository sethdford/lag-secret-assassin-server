package com.assassin.config;

import java.util.Objects;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Represents a phase in the shrinking zone mechanism.
 * Each phase defines parameters for the shrinking zone at a specific point in the game timeline.
 */
@DynamoDbBean
public class ZonePhase {
    
    private int phaseNumber;
    private long startTimeOffsetMillis;  // Offset from game start time
    private long shrinkDurationMillis;   // How long the shrinking takes
    private double endRadiusMeters;      // Target radius at the end of shrinking
    private double damagePerSecond;      // Damage applied to players outside the zone
    
    /**
     * Default constructor required for DynamoDB Enhanced Client
     */
    public ZonePhase() {
    }
    
    /**
     * Constructor with all phase parameters
     * 
     * @param phaseNumber           The sequential number of this phase
     * @param startTimeOffsetMillis Time offset from game start when this phase begins
     * @param shrinkDurationMillis  Duration over which the zone shrinks to target size
     * @param endRadiusMeters       Target radius at the end of this phase
     * @param damagePerSecond       Damage per second applied to players outside the zone
     */
    public ZonePhase(int phaseNumber, long startTimeOffsetMillis, long shrinkDurationMillis, 
                     double endRadiusMeters, double damagePerSecond) {
        this.phaseNumber = phaseNumber;
        this.startTimeOffsetMillis = startTimeOffsetMillis;
        this.shrinkDurationMillis = shrinkDurationMillis;
        this.endRadiusMeters = endRadiusMeters;
        this.damagePerSecond = damagePerSecond;
    }
    
    // Getters and Setters
    
    public int getPhaseNumber() {
        return phaseNumber;
    }
    
    public void setPhaseNumber(int phaseNumber) {
        this.phaseNumber = phaseNumber;
    }
    
    public long getStartTimeOffsetMillis() {
        return startTimeOffsetMillis;
    }
    
    public void setStartTimeOffsetMillis(long startTimeOffsetMillis) {
        this.startTimeOffsetMillis = startTimeOffsetMillis;
    }
    
    public long getShrinkDurationMillis() {
        return shrinkDurationMillis;
    }
    
    public void setShrinkDurationMillis(long shrinkDurationMillis) {
        this.shrinkDurationMillis = shrinkDurationMillis;
    }
    
    public double getEndRadiusMeters() {
        return endRadiusMeters;
    }
    
    public void setEndRadiusMeters(double endRadiusMeters) {
        this.endRadiusMeters = endRadiusMeters;
    }
    
    public double getDamagePerSecond() {
        return damagePerSecond;
    }
    
    public void setDamagePerSecond(double damagePerSecond) {
        this.damagePerSecond = damagePerSecond;
    }
    
    /**
     * Calculates the absolute start time of this phase in epoch millis
     * 
     * @param gameStartTimeEpochMillis The game start time in epoch millis
     * @return The absolute start time of this phase
     */
    public long getAbsoluteStartTimeMillis(long gameStartTimeEpochMillis) {
        return gameStartTimeEpochMillis + startTimeOffsetMillis;
    }
    
    /**
     * Calculates the absolute end time of this phase in epoch millis
     * 
     * @param gameStartTimeEpochMillis The game start time in epoch millis
     * @return The absolute end time of this phase
     */
    public long getAbsoluteEndTimeMillis(long gameStartTimeEpochMillis) {
        return getAbsoluteStartTimeMillis(gameStartTimeEpochMillis) + shrinkDurationMillis;
    }
    
    /**
     * Determines if this phase is active at the given time
     * 
     * @param currentTimeEpochMillis   The current time in epoch millis
     * @param gameStartTimeEpochMillis The game start time in epoch millis
     * @return True if this phase is active, false otherwise
     */
    public boolean isActiveAt(long currentTimeEpochMillis, long gameStartTimeEpochMillis) {
        long phaseStart = getAbsoluteStartTimeMillis(gameStartTimeEpochMillis);
        long phaseEnd = getAbsoluteEndTimeMillis(gameStartTimeEpochMillis);
        
        return currentTimeEpochMillis >= phaseStart && currentTimeEpochMillis <= phaseEnd;
    }
    
    /**
     * Calculates the shrinking progress (0.0 to 1.0) for this phase at the given time
     * 
     * @param currentTimeEpochMillis   The current time in epoch millis
     * @param gameStartTimeEpochMillis The game start time in epoch millis
     * @return The shrinking progress as a value between 0.0 and 1.0
     */
    public double getShrinkingProgress(long currentTimeEpochMillis, long gameStartTimeEpochMillis) {
        if (!isActiveAt(currentTimeEpochMillis, gameStartTimeEpochMillis) || shrinkDurationMillis <= 0) {
            return 0.0;
        }
        
        long phaseStart = getAbsoluteStartTimeMillis(gameStartTimeEpochMillis);
        long elapsed = currentTimeEpochMillis - phaseStart;
        
        return Math.min(1.0, Math.max(0.0, (double) elapsed / shrinkDurationMillis));
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZonePhase zonePhase = (ZonePhase) o;
        return phaseNumber == zonePhase.phaseNumber &&
                startTimeOffsetMillis == zonePhase.startTimeOffsetMillis &&
                shrinkDurationMillis == zonePhase.shrinkDurationMillis &&
                Double.compare(zonePhase.endRadiusMeters, endRadiusMeters) == 0 &&
                Double.compare(zonePhase.damagePerSecond, damagePerSecond) == 0;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(phaseNumber, startTimeOffsetMillis, shrinkDurationMillis, 
                endRadiusMeters, damagePerSecond);
    }
    
    @Override
    public String toString() {
        return "ZonePhase{" +
                "phaseNumber=" + phaseNumber +
                ", startTimeOffsetMillis=" + startTimeOffsetMillis +
                ", shrinkDurationMillis=" + shrinkDurationMillis +
                ", endRadiusMeters=" + endRadiusMeters +
                ", damagePerSecond=" + damagePerSecond +
                '}';
    }
} 