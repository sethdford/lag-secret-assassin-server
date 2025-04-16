package com.assassin.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        game.setStatus(GameStatus.PENDING.name()); // Use enum name
        game.setCreatedAt(Instant.now().toString());
        game.setPlayerIDs(new ArrayList<>(List.of("player1", "player2")));
        game.setAdminPlayerID("adminPlayer");
        // Initialize settings map, but don't put shrinkingZoneEnabled here anymore
        game.setSettings(new HashMap<>());
        game.setBoundary(List.of(new Coordinate(0, 0), new Coordinate(0, 10), new Coordinate(10, 10), new Coordinate(10, 0)));
        // Keep shrinkingZoneEnabled null initially for most tests unless overridden
        game.setShrinkingZoneEnabled(null);
    }
    
    @Test
    public void testGetShrinkingZoneEnabled_WithoutSettings() {
        // Arrange
        game.setSettings(null); // Settings map is null
        
        // Act
        Boolean result = game.getShrinkingZoneEnabled();
        
        // Assert
        assertFalse(result, "Should return false when settings are null");
    }
    
    @Test
    public void testGetShrinkingZoneEnabled_WithEmptySettings() {
        // Arrange
        game.setSettings(new HashMap<>()); // Settings map exists but is empty
        
        // Act
        Boolean result = game.getShrinkingZoneEnabled();
        
        // Assert
        assertFalse(result, "Should return false when settings doesn't contain shrinkingZoneEnabled");
    }
    
    @Test
    public void testGetShrinkingZoneEnabled_WithValidBoolean() {
        // Set the direct field
        game.setShrinkingZoneEnabled(Boolean.TRUE);
        
        assertTrue(game.getShrinkingZoneEnabled(), 
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
        // Arrange
        Map<String, Object> settings = new HashMap<>();
        settings.put("shrinkingZoneEnabled", "not-a-boolean"); // Invalid type
        game.setSettings(settings);
        // Also directly set the field for this test case, assuming direct setting if not using settings map
        game.setShrinkingZoneEnabled(null); // Simulate the case where the direct field isn't set or is null
        
        // Act
        Boolean result = game.getShrinkingZoneEnabled(); 
        
        // Assert
        assertFalse(result, "Should return false when shrinkingZoneEnabled field is null");
    }

    @Test
    void testGetShrinkingZoneEnabled_WhenNull_ShouldReturnFalse() {
        // Explicitly ensure it's null for this test (redundant given setUp, but clear)
        game.setShrinkingZoneEnabled(null);
        assertFalse(game.getShrinkingZoneEnabled(), "Getter should return false when field is null");
    }

    @Test
    void testGetShrinkingZoneEnabled_WhenFalse_ShouldReturnFalse() {
        game.setShrinkingZoneEnabled(Boolean.FALSE);
        assertFalse(game.getShrinkingZoneEnabled(), "Getter should return false when field is false");
    }

    @Test
    void testGetShrinkingZoneEnabled_WhenTrue_ShouldReturnTrue() {
        // Correctly set the direct field now
        game.setShrinkingZoneEnabled(Boolean.TRUE);
        assertTrue(game.getShrinkingZoneEnabled(), "Getter should return true when field is true");
    }

    @Test
    void testSettingsMap_PutAndGet() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("difficulty", "hard");
        settings.put("maxPlayers", 10);
        game.setSettings(settings);

        Map<String, Object> retrievedSettings = game.getSettings();
        assertNotNull(retrievedSettings);
        assertEquals("hard", retrievedSettings.get("difficulty"));
        // Note: DynamoDB Enhanced Client might store numbers as BigDecimal,
        // handle potential type differences if testing after DB interaction.
        // For direct object testing, Integer should be fine.
        assertEquals(10, retrievedSettings.get("maxPlayers"));
    }

    @Test
    void testSettingsMap_NullValue() {
         Map<String, Object> settings = new HashMap<>();
         settings.put("optionalFeature", null);
         game.setSettings(settings);
         // Depending on converter, null might be stored or omitted. Test get returns null.
         assertNull(game.getSettings().get("optionalFeature"));
    }

    @Test
    void testSettingsGetterSetterPresence() {
        // Set settings to null explicitly
        game.setSettings(null); 
        assertNull(game.getSettings(), "Setting settings to null should result in null");
        
        // Test setting a non-null map
        Map<String, Object> newSettings = new HashMap<>();
        newSettings.put("key", "value");
        game.setSettings(newSettings);
        assertEquals(newSettings, game.getSettings(), "Settings map should be retrievable");
    }

    @Test
    void testSettingsMap_CanStoreBoolean() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("someOtherBooleanSetting", true);
        game.setSettings(settings);

        assertTrue((Boolean) game.getSettings().get("someOtherBooleanSetting"),
                   "Boolean value stored in settings map should be retrieved as true");

        // Verify this does NOT affect the direct shrinkingZoneEnabled field
        assertFalse(game.getShrinkingZoneEnabled(),
                    "Setting a boolean in the map should not affect the direct shrinkingZoneEnabled field");
    }
} 