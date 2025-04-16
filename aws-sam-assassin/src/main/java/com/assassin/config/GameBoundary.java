package com.assassin.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.assassin.model.Coordinate;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Represents the geographical boundary of a game.
 * This class encapsulates a list of coordinates that define the polygon
 * within which gameplay is valid.
 */
@DynamoDbBean
public class GameBoundary {
    
    private List<Coordinate> coordinates;
    
    /**
     * Default constructor required for DynamoDB Enhanced Client
     */
    public GameBoundary() {
        this.coordinates = new ArrayList<>();
    }
    
    /**
     * Constructor with initial coordinates
     * 
     * @param coordinates List of coordinate points defining the game boundary
     */
    public GameBoundary(List<Coordinate> coordinates) {
        this.coordinates = coordinates != null ? coordinates : new ArrayList<>();
    }
    
    /**
     * Get the list of coordinates defining the boundary
     * 
     * @return List of coordinate points
     */
    public List<Coordinate> getCoordinates() {
        return coordinates;
    }
    
    /**
     * Set the list of coordinates defining the boundary
     * 
     * @param coordinates List of coordinate points
     */
    public void setCoordinates(List<Coordinate> coordinates) {
        this.coordinates = coordinates != null ? coordinates : new ArrayList<>();
    }
    
    /**
     * Add a single coordinate to the boundary
     * 
     * @param coordinate Coordinate to add
     * @return true if coordinate was added successfully
     */
    public boolean addCoordinate(Coordinate coordinate) {
        if (coordinate == null) {
            return false;
        }
        return this.coordinates.add(coordinate);
    }
    
    /**
     * Remove a coordinate from the boundary
     * 
     * @param coordinate Coordinate to remove
     * @return true if coordinate was removed successfully
     */
    public boolean removeCoordinate(Coordinate coordinate) {
        return this.coordinates.remove(coordinate);
    }
    
    /**
     * Check if the boundary is valid (has at least 3 coordinates to form a polygon)
     * 
     * @return true if boundary is valid
     */
    public boolean isValid() {
        return this.coordinates != null && this.coordinates.size() >= 3;
    }
    
    /**
     * Get the number of coordinates in the boundary
     * 
     * @return number of coordinates
     */
    public int size() {
        return this.coordinates != null ? this.coordinates.size() : 0;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameBoundary that = (GameBoundary) o;
        return Objects.equals(coordinates, that.coordinates);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(coordinates);
    }
    
    @Override
    public String toString() {
        return "GameBoundary{" +
                "coordinates=" + coordinates +
                '}';
    }
} 