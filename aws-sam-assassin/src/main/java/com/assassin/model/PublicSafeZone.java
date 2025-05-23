package com.assassin.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Represents a public safe zone where all players are protected.
 * Public safe zones are accessible to all players in the game.
 */
@DynamoDbBean
public class PublicSafeZone extends SafeZone {
    
    /**
     * Creates a new public safe zone with the specified properties.
     */
    public PublicSafeZone() {
        super();
        setType(SafeZoneType.PUBLIC);
    }
    
    /**
     * Creates a new public safe zone with the specified properties.
     * 
     * @param gameId The ID of the game this safe zone belongs to
     * @param name The name of the safe zone
     * @param description The description of the safe zone
     * @param latitude The latitude of the safe zone center
     * @param longitude The longitude of the safe zone center
     * @param radiusMeters The radius of the safe zone in meters
     * @param createdBy The ID of the player who created the safe zone
     */
    public PublicSafeZone(String gameId, String name, String description, 
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
        setType(SafeZoneType.PUBLIC);
    }
    
    /**
     * All players are authorized in a public safe zone.
     */
    @Override
    public boolean isPlayerAuthorized(String playerId) {
        return true;
    }
} 