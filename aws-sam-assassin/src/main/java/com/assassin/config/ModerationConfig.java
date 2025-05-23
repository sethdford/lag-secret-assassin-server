package com.assassin.config;

import java.time.Duration;

/**
 * Configuration class for content moderation settings
 */
public class ModerationConfig {
    private final double imageModerationThreshold;
    private final double textModerationThreshold;
    private final double manualReviewThreshold;
    private final boolean cacheEnabled;
    private final Duration cacheExpiration;

    private ModerationConfig(Builder builder) {
        this.imageModerationThreshold = builder.imageModerationThreshold;
        this.textModerationThreshold = builder.textModerationThreshold;
        this.manualReviewThreshold = builder.manualReviewThreshold;
        this.cacheEnabled = builder.cacheEnabled;
        this.cacheExpiration = builder.cacheExpiration;
    }

    /**
     * Create configuration from environment variables
     */
    public static ModerationConfig fromEnvironment() {
        return new Builder()
                .imageModerationThreshold(getEnvDouble("MODERATION_IMAGE_THRESHOLD", 80.0))
                .textModerationThreshold(getEnvDouble("MODERATION_TEXT_THRESHOLD", 0.7))
                .manualReviewThreshold(getEnvDouble("MODERATION_MANUAL_REVIEW_THRESHOLD", 50.0))
                .cacheEnabled(getEnvBoolean("MODERATION_CACHE_ENABLED", true))
                .cacheExpiration(Duration.ofHours(getEnvLong("MODERATION_CACHE_HOURS", 24)))
                .build();
    }

    /**
     * Create default configuration for testing
     */
    public static ModerationConfig defaultConfig() {
        return new Builder()
                .imageModerationThreshold(80.0)
                .textModerationThreshold(0.7)
                .manualReviewThreshold(50.0)
                .cacheEnabled(true)
                .cacheExpiration(Duration.ofHours(24))
                .build();
    }

    private static double getEnvDouble(String key, double defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean getEnvBoolean(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private static long getEnvLong(String key, long defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // Getters
    public double getImageModerationThreshold() {
        return imageModerationThreshold;
    }

    public double getTextModerationThreshold() {
        return textModerationThreshold;
    }

    public double getManualReviewThreshold() {
        return manualReviewThreshold;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public Duration getCacheExpiration() {
        return cacheExpiration;
    }

    /**
     * Builder class for ModerationConfig
     */
    public static class Builder {
        private double imageModerationThreshold = 80.0;
        private double textModerationThreshold = 0.7;
        private double manualReviewThreshold = 50.0;
        private boolean cacheEnabled = true;
        private Duration cacheExpiration = Duration.ofHours(24);

        public Builder imageModerationThreshold(double threshold) {
            this.imageModerationThreshold = threshold;
            return this;
        }

        public Builder textModerationThreshold(double threshold) {
            this.textModerationThreshold = threshold;
            return this;
        }

        public Builder manualReviewThreshold(double threshold) {
            this.manualReviewThreshold = threshold;
            return this;
        }

        public Builder cacheEnabled(boolean enabled) {
            this.cacheEnabled = enabled;
            return this;
        }

        public Builder cacheExpiration(Duration expiration) {
            this.cacheExpiration = expiration;
            return this;
        }

        public ModerationConfig build() {
            return new ModerationConfig(this);
        }
    }

    @Override
    public String toString() {
        return "ModerationConfig{" +
                "imageModerationThreshold=" + imageModerationThreshold +
                ", textModerationThreshold=" + textModerationThreshold +
                ", manualReviewThreshold=" + manualReviewThreshold +
                ", cacheEnabled=" + cacheEnabled +
                ", cacheExpiration=" + cacheExpiration +
                '}';
    }
} 