package com.assassin.config;

import java.util.ArrayList; // Ensure Coordinate model exists and path is correct
import java.util.HashMap; // Added this import
import java.util.List;   // Added for weaponDistances
import java.util.Map; // Added for weaponDistances initialization
import java.util.Objects;

import com.assassin.model.Coordinate;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Represents a map configuration for a game, including boundaries and zone settings.
 */
@DynamoDbBean
public class MapConfiguration {

    private String mapId; // Unique identifier for the map configuration
    private String mapName; // Display name for the map
    private List<Coordinate> gameBoundary; // List of coordinates defining the playable area polygon
    private Boolean shrinkingZoneEnabled; // Flag indicating if the shrinking zone mechanic is active
    private Double initialZoneRadiusMeters; // Starting radius of the safe zone
    private Coordinate initialZoneCenter; // Center of the initial zone
    private Double zoneDamagePerSecond; // Damage dealt to players outside the safe zone
    private List<ZonePhase> zonePhases; // Ordered list defining the shrinking phases
    private ShrinkingZoneConfig shrinkingZoneConfig; // Added this field

    // New fields for proximity detection
    private Double eliminationDistanceMeters; // Max distance for elimination
    private Double proximityAwarenessDistanceMeters; // Distance at which proximity alerts trigger
    private Map<String, Double> weaponDistances; // Weapon type -> Elimination distance (meters)

    /**
     * Default constructor
     */
    public MapConfiguration() {
        this.zonePhases = new ArrayList<>();
        this.weaponDistances = new HashMap<>(); // Initialize the map
    }

    /**
     * Gets the map ID
     * 
     * @return The map ID
     */
    public String getMapId() {
        return mapId;
    }

    /**
     * Sets the map ID
     * 
     * @param mapId The map ID to set
     */
    public void setMapId(String mapId) {
        this.mapId = mapId;
    }

    /**
     * Gets the map name
     * 
     * @return The map name
     */
    public String getMapName() {
        return mapName;
    }

    /**
     * Sets the map name
     * 
     * @param mapName The map name to set
     */
    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    /**
     * Gets the game boundary coordinates
     * 
     * @return List of coordinates defining the game boundary
     */
    public List<Coordinate> getGameBoundary() {
        return gameBoundary;
    }

    /**
     * Sets the game boundary coordinates
     * 
     * @param gameBoundary List of coordinates defining the game boundary
     */
    public void setGameBoundary(List<Coordinate> gameBoundary) {
        this.gameBoundary = gameBoundary;
    }

    /**
     * Checks if shrinking zone is enabled
     * 
     * @return True if shrinking zone is enabled, false otherwise
     */
    public Boolean getShrinkingZoneEnabled() {
        return shrinkingZoneEnabled;
    }

    /**
     * Sets whether shrinking zone is enabled
     * 
     * @param shrinkingZoneEnabled True to enable shrinking zone, false otherwise
     */
    public void setShrinkingZoneEnabled(Boolean shrinkingZoneEnabled) {
        this.shrinkingZoneEnabled = shrinkingZoneEnabled;
    }

    /**
     * Gets the initial zone radius in meters
     * 
     * @return The initial zone radius in meters
     */
    public Double getInitialZoneRadiusMeters() {
        return initialZoneRadiusMeters;
    }

    /**
     * Sets the initial zone radius in meters
     * 
     * @param initialZoneRadiusMeters The initial zone radius to set
     */
    public void setInitialZoneRadiusMeters(Double initialZoneRadiusMeters) {
        this.initialZoneRadiusMeters = initialZoneRadiusMeters;
    }

    /**
     * Gets the initial zone center coordinate
     * 
     * @return The initial zone center coordinate
     */
    public Coordinate getInitialZoneCenter() {
        return initialZoneCenter;
    }

    /**
     * Sets the initial zone center coordinate
     * 
     * @param initialZoneCenter The initial zone center to set
     */
    public void setInitialZoneCenter(Coordinate initialZoneCenter) {
        this.initialZoneCenter = initialZoneCenter;
    }

    /**
     * Gets the zone damage per second
     * 
     * @return The zone damage per second
     */
    public Double getZoneDamagePerSecond() {
        return zoneDamagePerSecond;
    }

    /**
     * Sets the zone damage per second
     * 
     * @param zoneDamagePerSecond The zone damage per second to set
     */
    public void setZoneDamagePerSecond(Double zoneDamagePerSecond) {
        this.zoneDamagePerSecond = zoneDamagePerSecond;
    }

    /**
     * Gets the list of zone phases
     * 
     * @return List of zone phases
     */
    public List<ZonePhase> getZonePhases() {
        return zonePhases;
    }

    /**
     * Sets the list of zone phases
     * 
     * @param zonePhases List of zone phases to set
     */
    public void setZonePhases(List<ZonePhase> zonePhases) {
        this.zonePhases = zonePhases;
    }

    public void setShrinkingZoneConfig(ShrinkingZoneConfig shrinkingZoneConfig) {
        this.shrinkingZoneConfig = shrinkingZoneConfig;
    }
    
    public ShrinkingZoneConfig getShrinkingZoneConfig() {
        return shrinkingZoneConfig;
    }

    // Getters and Setters for new proximity fields
    public Double getEliminationDistanceMeters() {
        return eliminationDistanceMeters;
    }

    public void setEliminationDistanceMeters(Double eliminationDistanceMeters) {
        this.eliminationDistanceMeters = eliminationDistanceMeters;
    }

    public Double getProximityAwarenessDistanceMeters() {
        return proximityAwarenessDistanceMeters;
    }

    public void setProximityAwarenessDistanceMeters(Double proximityAwarenessDistanceMeters) {
        this.proximityAwarenessDistanceMeters = proximityAwarenessDistanceMeters;
    }

    /**
     * Gets the map of weapon types to their specific elimination distances.
     *
     * @return Map where keys are weapon types (String) and values are distances (Double).
     */
    public Map<String, Double> getWeaponDistances() {
        return weaponDistances;
    }

    /**
     * Sets the map of weapon types to their specific elimination distances.
     *
     * @param weaponDistances Map where keys are weapon types (String) and values are distances (Double).
     */
    public void setWeaponDistances(Map<String, Double> weaponDistances) {
        this.weaponDistances = weaponDistances;
    }

    // Standard equals, hashCode, and toString methods
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapConfiguration that = (MapConfiguration) o;
        return Objects.equals(mapId, that.mapId) &&
                Objects.equals(mapName, that.mapName) &&
                Objects.equals(gameBoundary, that.gameBoundary) &&
                Objects.equals(shrinkingZoneEnabled, that.shrinkingZoneEnabled) &&
                Objects.equals(initialZoneRadiusMeters, that.initialZoneRadiusMeters) &&
                Objects.equals(initialZoneCenter, that.initialZoneCenter) &&
                Objects.equals(zoneDamagePerSecond, that.zoneDamagePerSecond) &&
                Objects.equals(zonePhases, that.zonePhases) &&
                Objects.equals(shrinkingZoneConfig, that.shrinkingZoneConfig) &&
                Objects.equals(eliminationDistanceMeters, that.eliminationDistanceMeters) &&
                Objects.equals(proximityAwarenessDistanceMeters, that.proximityAwarenessDistanceMeters) &&
                Objects.equals(weaponDistances, that.weaponDistances);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapId, mapName, gameBoundary, shrinkingZoneEnabled, 
                initialZoneRadiusMeters, initialZoneCenter, zoneDamagePerSecond, zonePhases,
                shrinkingZoneConfig,
                eliminationDistanceMeters, proximityAwarenessDistanceMeters,
                weaponDistances);
    }

    @Override
    public String toString() {
        return "MapConfiguration{" +
                "mapId='" + mapId + '\'' +
                ", mapName='" + mapName + '\'' +
                ", gameBoundary=" + gameBoundary +
                ", shrinkingZoneEnabled=" + shrinkingZoneEnabled +
                ", initialZoneRadiusMeters=" + initialZoneRadiusMeters +
                ", initialZoneCenter=" + initialZoneCenter +
                ", zoneDamagePerSecond=" + zoneDamagePerSecond +
                ", zonePhases=" + zonePhases +
                ", shrinkingZoneConfig=" + shrinkingZoneConfig +
                ", eliminationDistanceMeters=" + eliminationDistanceMeters +
                ", proximityAwarenessDistanceMeters=" + proximityAwarenessDistanceMeters +
                ", weaponDistances=" + weaponDistances +
                '}';
    }
} 