package com.assassin.model;

import java.util.HashSet;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Represents a private safe zone where only authorized players are protected.
 * Authorized players are defined by the authorizedPlayerIds list.
 */
@DynamoDbBean
public class PrivateSafeZone extends SafeZone {
    
    /**
     * Creates a new private safe zone with default properties.
     */
    public PrivateSafeZone() {
        super();
        setType(SafeZoneType.PRIVATE);
    }
    
    /**
     * Creates a new private safe zone with the specified properties.
     * 
     * @param gameId The ID of the game this safe zone belongs to
     * @param name The name of the safe zone
     * @param description The description of the safe zone
     * @param latitude The latitude of the safe zone center
     * @param longitude The longitude of the safe zone center
     * @param radiusMeters The radius of the safe zone in meters
     * @param createdBy The ID of the player who created the safe zone
     */
    public PrivateSafeZone(String gameId, String name, String description, 
                         Double latitude, Double longitude, Double radiusMeters, 
                         String createdBy) {
        super();
        setGameId(gameId);
        setName(name);
        setDescription(description);
        setLatitude(latitude);
        setLongitude(longitude);
        setRadiusMeters(radiusMeters);
        setCreatedBy(createdBy);
        setType(SafeZoneType.PRIVATE);
        
        // By default, the creator is authorized in their private safe zone
        addAuthorizedPlayer(createdBy);
    }
    
    /**
     * Checks if a player is authorized in this private safe zone.
     * 
     * @param playerId The ID of the player to check
     * @return true if the player is in the authorized players list, false otherwise
     */
    @Override
    public boolean isPlayerAuthorized(String playerId) {
        return getAuthorizedPlayerIds() != null && getAuthorizedPlayerIds().contains(playerId);
    }
    
    /**
     * Adds a player to the authorized players list.
     * 
     * @param playerId The ID of the player to authorize
     */
    public void addAuthorizedPlayer(String playerId) {
        if (getAuthorizedPlayerIds() == null) {
            setAuthorizedPlayerIds(new HashSet<>());
        }
        
        if (!getAuthorizedPlayerIds().contains(playerId)) {
            getAuthorizedPlayerIds().add(playerId);
        }
    }
    
    /**
     * Removes a player from the authorized players list.
     * 
     * @param playerId The ID of the player to remove authorization for
     */
    @Override
    public void removeAuthorizedPlayer(String playerId) {
        if (getAuthorizedPlayerIds() != null) {
            getAuthorizedPlayerIds().remove(playerId);
        }
    }
} 