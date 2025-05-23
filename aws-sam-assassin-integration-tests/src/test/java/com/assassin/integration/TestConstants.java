package com.assassin.integration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Constants for integration tests.
 * Contains common test values for coordinate pairs, boundaries, and other test data.
 */
public final class TestConstants {

    private TestConstants() {
        // Utility class, no instances
    }

    /**
     * Center point for many test operations - UNCC campus coordinates
     */
    public static final double[] UNCC_CAMPUS = {35.308250, -80.732260};
    
    /**
     * Test coordinate for downtown Charlotte
     */
    public static final double[] DOWNTOWN_CHARLOTTE = {35.227085, -80.843124};

    /**
     * Test coordinate for Carowinds amusement park
     */
    public static final double[] CAROWINDS = {35.104469, -80.941362};
    
    /**
     * Creates a coordinate point in the format expected by the API
     */
    public static Map<String, Object> createCoordinate(double latitude, double longitude) {
        Map<String, Object> coord = new HashMap<>();
        coord.put("latitude", latitude);
        coord.put("longitude", longitude);
        return coord;
    }
    
    /**
     * Creates a simple square boundary around the given center point with the specified size in degrees
     */
    public static List<Map<String, Object>> createSquareBoundary(double[] center, double size) {
        double halfSize = size / 2;
        
        return Arrays.asList(
            createCoordinate(center[0] - halfSize, center[1] - halfSize),
            createCoordinate(center[0] - halfSize, center[1] + halfSize),
            createCoordinate(center[0] + halfSize, center[1] + halfSize),
            createCoordinate(center[0] + halfSize, center[1] - halfSize)
        );
    }
    
    /**
     * Creates a boundary representing an approximately 1km square around UNCC campus
     */
    public static List<Map<String, Object>> createUNCCBoundary() {
        // ~0.01 degrees is approximately 1km
        return createSquareBoundary(UNCC_CAMPUS, 0.01);
    }
    
    /**
     * Creates a sample safe zone request
     */
    public static Map<String, Object> createSafeZoneRequest(String name, double[] center, double radiusMeters) {
        Map<String, Object> safeZone = new HashMap<>();
        safeZone.put("name", name);
        safeZone.put("center", createCoordinate(center[0], center[1]));
        safeZone.put("radiusInMeters", radiusMeters);
        return safeZone;
    }

    // Game constants
    public static final String DEFAULT_GAME_NAME = "Test Game";
    public static final String DEFAULT_GAME_DESCRIPTION = "A test game created for integration testing";
    public static final double DEFAULT_WEAPON_DISTANCE = 10.0;
    public static final int DEFAULT_MAX_PLAYERS = 20;
    
    // Player constants
    public static final String DEFAULT_PLAYER_NAME = "Test Player";
    public static final String DEFAULT_EMAIL_DOMAIN = "test.assassingame.com";
    
    // Location constants
    public static final double TEST_LATITUDE = 37.7749;  // San Francisco
    public static final double TEST_LONGITUDE = -122.4194;
    
    // Safe zone constants
    public static final String DEFAULT_SAFE_ZONE_NAME = "Test Safe Zone";
    public static final double DEFAULT_SAFE_ZONE_RADIUS = 100.0;  // meters
    
    // Game boundary (simple square around San Francisco)
    public static final List<Map<String, Double>> TEST_BOUNDARY = Arrays.asList(
        Map.of("latitude", 37.7649, "longitude", -122.4294),
        Map.of("latitude", 37.7649, "longitude", -122.4094),
        Map.of("latitude", 37.7849, "longitude", -122.4094),
        Map.of("latitude", 37.7849, "longitude", -122.4294)
    );
    
    // Authentication
    public static final String AUTH_HEADER_NAME = "Authorization";
    public static final String AUTH_HEADER_PREFIX = "Bearer ";
} 