package com.assassin.service.proximity;

import com.assassin.model.Coordinate;
import com.assassin.model.Player;
import com.assassin.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core proximity calculation engine for the assassin game.
 * Handles distance calculations between players with GPS accuracy compensation.
 */
public class ProximityCalculator {
    private static final Logger logger = LoggerFactory.getLogger(ProximityCalculator.class);
    
    // GPS accuracy compensation in meters
    private static final double GPS_ACCURACY_BUFFER = 5.0;
    
    // Default elimination distance if not specified
    private static final double DEFAULT_ELIMINATION_DISTANCE = 10.0;
    
    /**
     * Calculates the distance between two players.
     * 
     * @param player1 First player
     * @param player2 Second player
     * @return Distance in meters
     */
    public double calculateDistance(Player player1, Player player2) {
        if (player1 == null || player2 == null) {
            throw new IllegalArgumentException("Players cannot be null");
        }
        
        Coordinate coord1 = new Coordinate(player1.getLatitude(), player1.getLongitude());
        Coordinate coord2 = new Coordinate(player2.getLatitude(), player2.getLongitude());
        
        return calculateDistance(coord1, coord2);
    }
    
    /**
     * Calculates the distance between two coordinates.
     * 
     * @param coord1 First coordinate
     * @param coord2 Second coordinate
     * @return Distance in meters
     */
    public double calculateDistance(Coordinate coord1, Coordinate coord2) {
        if (coord1 == null || coord2 == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }
        
        return GeoUtils.calculateDistance(
            coord1.getLatitude(), coord1.getLongitude(),
            coord2.getLatitude(), coord2.getLongitude()
        );
    }
    
    /**
     * Checks if two players are within elimination range.
     * 
     * @param player1 First player
     * @param player2 Second player
     * @param eliminationDistance Maximum distance for elimination in meters
     * @return true if players are within elimination range
     */
    public boolean isWithinEliminationRange(Player player1, Player player2, double eliminationDistance) {
        double distance = calculateDistance(player1, player2);
        double effectiveDistance = eliminationDistance + GPS_ACCURACY_BUFFER;
        
        boolean inRange = distance <= effectiveDistance;
        
        if (logger.isDebugEnabled()) {
            logger.debug("Proximity check: {} to {} = {}m (threshold: {}m) - {}",
                player1.getPlayerID(), player2.getPlayerID(), 
                String.format("%.2f", distance),
                effectiveDistance,
                inRange ? "IN RANGE" : "OUT OF RANGE"
            );
        }
        
        return inRange;
    }
    
    /**
     * Checks if two players are within elimination range using default distance.
     * 
     * @param player1 First player
     * @param player2 Second player
     * @return true if players are within default elimination range
     */
    public boolean isWithinEliminationRange(Player player1, Player player2) {
        return isWithinEliminationRange(player1, player2, DEFAULT_ELIMINATION_DISTANCE);
    }
    
    /**
     * Calculates the bearing (direction) from one player to another.
     * 
     * @param from Origin player
     * @param to Target player
     * @return Bearing in degrees (0-360, where 0 is north)
     */
    public double calculateBearing(Player from, Player to) {
        return GeoUtils.calculateBearing(
            from.getLatitude(), from.getLongitude(),
            to.getLatitude(), to.getLongitude()
        );
    }
    
    /**
     * Gets the compass direction from bearing.
     * 
     * @param bearing Bearing in degrees
     * @return Compass direction (N, NE, E, SE, S, SW, W, NW)
     */
    public String getCompassDirection(double bearing) {
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = (int) ((bearing + 22.5) / 45.0) % 8;
        return directions[index];
    }
    
    /**
     * Rounds distance for display purposes.
     * 
     * @param distance Distance in meters
     * @return Rounded distance suitable for display
     */
    public int roundDistanceForDisplay(double distance) {
        if (distance < 10) {
            return (int) Math.round(distance);
        } else if (distance < 50) {
            return ((int) Math.round(distance / 5.0)) * 5;
        } else if (distance < 100) {
            return ((int) Math.round(distance / 10.0)) * 10;
        } else {
            return ((int) Math.round(distance / 50.0)) * 50;
        }
    }
}