package com.assassin.model;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for the Game model.
 */
public class GameTest {
    
    private Game game;
    
    @BeforeEach
    public void setUp() {
        game = new Game();
        game.setGameID("test-game-id");
        game.setGameName("Test Game");
    }
    
    @Test
    public void testGetShrinkingZoneEnabled_WithoutSettings() {
        // No settings set
        assertNull(game.getShrinkingZoneEnabled(), "Should return null when settings are null");
    }
    
    @Test
    public void testGetShrinkingZoneEnabled_WithEmptySettings() {
        // Empty settings
        game.setSettings(new HashMap<>());
        assertNull(game.getShrinkingZoneEnabled(), "Should return null when settings doesn't contain shrinkingZoneEnabled");
    }
    
    @Test
    public void testGetShrinkingZoneEnabled_WithValidBoolean() {
        // Create settings with shrinkingZoneEnabled = true
        Map<String, Object> settings = new HashMap<>();
        settings.put("shrinkingZoneEnabled", true);
        game.setSettings(settings);
        
        assertEquals(Boolean.TRUE, game.getShrinkingZoneEnabled(), 
            "Should return true when shrinkingZoneEnabled is true");
    }
    
    @Test
    public void testGetShrinkingZoneEnabled_WithValidBooleanFalse() {
        // Create settings with shrinkingZoneEnabled = false
        Map<String, Object> settings = new HashMap<>();
        settings.put("shrinkingZoneEnabled", false);
        game.setSettings(settings);
        
        assertEquals(Boolean.FALSE, game.getShrinkingZoneEnabled(), 
            "Should return false when shrinkingZoneEnabled is false");
    }
    
    @Test
    public void testGetShrinkingZoneEnabled_WithInvalidType() {
        // Create settings with shrinkingZoneEnabled as non-boolean
        Map<String, Object> settings = new HashMap<>();
        settings.put("shrinkingZoneEnabled", "true"); // String, not boolean
        game.setSettings(settings);
        
        assertNull(game.getShrinkingZoneEnabled(), 
            "Should return null when shrinkingZoneEnabled is not a Boolean");
    }
} 