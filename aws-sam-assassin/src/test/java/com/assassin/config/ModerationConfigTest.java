package com.assassin.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ModerationConfigTest {

    @AfterEach
    void tearDown() {
        // Clean up environment variables
        System.clearProperty("MODERATION_IMAGE_THRESHOLD");
        System.clearProperty("MODERATION_TEXT_THRESHOLD");
        System.clearProperty("MODERATION_MANUAL_REVIEW_THRESHOLD");
        System.clearProperty("MODERATION_CACHE_ENABLED");
        System.clearProperty("MODERATION_CACHE_HOURS");
    }

    @Test
    void testDefaultConfig() {
        ModerationConfig config = ModerationConfig.defaultConfig();
        
        assertEquals(80.0, config.getImageModerationThreshold());
        assertEquals(0.7, config.getTextModerationThreshold());
        assertEquals(50.0, config.getManualReviewThreshold());
        assertTrue(config.isCacheEnabled());
        assertEquals(Duration.ofHours(24), config.getCacheExpiration());
    }

    @Test
    void testFromEnvironment_WithDefaults() {
        ModerationConfig config = ModerationConfig.fromEnvironment();
        
        assertEquals(80.0, config.getImageModerationThreshold());
        assertEquals(0.7, config.getTextModerationThreshold());
        assertEquals(50.0, config.getManualReviewThreshold());
        assertTrue(config.isCacheEnabled());
        assertEquals(Duration.ofHours(24), config.getCacheExpiration());
    }

    @Test
    void testBuilder() {
        ModerationConfig config = new ModerationConfig.Builder()
                .imageModerationThreshold(75.0)
                .textModerationThreshold(0.8)
                .manualReviewThreshold(40.0)
                .cacheEnabled(false)
                .cacheExpiration(Duration.ofHours(12))
                .build();
        
        assertEquals(75.0, config.getImageModerationThreshold());
        assertEquals(0.8, config.getTextModerationThreshold());
        assertEquals(40.0, config.getManualReviewThreshold());
        assertFalse(config.isCacheEnabled());
        assertEquals(Duration.ofHours(12), config.getCacheExpiration());
    }

    @Test
    void testToString() {
        ModerationConfig config = ModerationConfig.defaultConfig();
        String toString = config.toString();
        
        assertTrue(toString.contains("imageModerationThreshold=80.0"));
        assertTrue(toString.contains("textModerationThreshold=0.7"));
        assertTrue(toString.contains("cacheEnabled=true"));
    }
} 