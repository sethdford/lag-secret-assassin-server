package com.assassin.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.config.MapConfiguration;
import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.GameDao;
import com.assassin.dao.PlayerDao;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.model.Notification;
import com.assassin.model.NotificationType;
import com.assassin.model.Player;
import com.assassin.model.PlayerStatus;
import com.assassin.util.GeoUtils;

/**
 * Service responsible for detecting proximity between players for elimination mechanics.
 * Provides optimized methods for checking when players are close enough for elimination attempts.
 */
public class ProximityDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(ProximityDetectionService.class);
    
    // Default threshold distance if not specified in map config
    private static final double DEFAULT_ELIMINATION_DISTANCE = 10.0;
    
    // GPS accuracy compensation (meters)
    private static final double GPS_ACCURACY_BUFFER = 5.0;
    
    // Cache of recent proximity checks to reduce unnecessary recalculations
    private final Map<String, ProximityResult> proximityCache;
    
    // Cache expiration time in milliseconds
    private static final long CACHE_EXPIRATION_MS = 10000; // 10 seconds
    
    // Maximum age of location data to be considered valid (milliseconds)
    private static final long LOCATION_STALENESS_THRESHOLD_MS = 60000; // 60 seconds
    
    // Cache for recent alerts sent to avoid spamming users
    private final Map<String, Long> alertCache;
    private static final long ALERT_COOLDOWN_MS = 60000; // 1 minute
    
    // Location tracking for jitter reduction
    private final LocationHistoryManager locationHistoryManager;
    private static final int LOCATION_HISTORY_SIZE = 3; // Number of locations to track per player
    
    private final PlayerDao playerDao;
    private final GameDao gameDao;
    private final LocationService locationService;
    private final MapConfigurationService mapConfigService;
    private final NotificationService notificationService;
    
    /**
     * Tracks recent player locations to reduce GPS jitter and improve accuracy.
     * Stores a rolling window of recent locations and can provide smoothed coordinates.
     * Optimized for memory usage and concurrent access in a high-traffic environment.
     * Supports multiple smoothing algorithms for different use cases.
     */
    private static class LocationHistoryManager {
        // Define different smoothing algorithms and add PREDICTIVE option
        public enum SmoothingAlgorithm {
            /**
             * Linear weighted average where weight decreases linearly with age
             */
            LINEAR_WEIGHTED,
            
            /**
             * Exponential decay where weight decreases exponentially with age
             */
            EXPONENTIAL_DECAY,
            
            /**
             * Simple average (equal weights for all points)
             */
            SIMPLE_AVERAGE,
            
            /**
             * Predictive algorithm that extrapolates position based on velocity
             */
            PREDICTIVE
        }
        
        private final Map<String, List<LocationEntry>> playerLocationHistory;
        private final int historySize;
        
        // Default smoothing algorithm and parameters
        private SmoothingAlgorithm defaultAlgorithm = SmoothingAlgorithm.LINEAR_WEIGHTED;
        private double exponentialDecayFactor = 0.5; // Higher values weight recent points more heavily
        
        // Predictive movement parameters
        private int predictionTimeMs = 5000; // Default prediction time window (5 seconds)
        private int minPointsForPrediction = 3; // Minimum points needed for prediction
        private double velocityThresholdMetersPerSec = 0.5; // Minimum velocity to make predictions
        private double maxPredictionDistanceMeters = 30.0; // Maximum prediction distance
        
        // Cached prediction results
        private final Map<String, PredictedLocation> predictionsCache;
        private static final long PREDICTION_CACHE_EXPIRY_MS = 1000; // 1 second cache validity
        
        // Threshold for cleaning up player history that hasn't been updated
        private static final long HISTORY_CLEANUP_THRESHOLD_MS = 3600000; // 1 hour
        
        // Smoothed location cache to avoid recalculations within a short time period
        private final Map<String, CachedSmoothedLocation> smoothedLocationCache;
        private static final long SMOOTHED_CACHE_EXPIRY_MS = 2000; // 2 seconds
        
        // Last cleanup timestamp
        private long lastCleanupTime = System.currentTimeMillis();
        private static final long CLEANUP_INTERVAL_MS = 300000; // Cleanup every 5 minutes
        
        /**
         * Class representing a predicted future location based on movement history.
         */
        private static class PredictedLocation {
            final Coordinate coordinate;
            final long timestamp;
            final double confidenceScore; // 0.0-1.0 rating of prediction confidence
            final double velocityMetersPerSec; // Current velocity in m/s
            final double directionDegrees; // Direction in degrees (0-360, 0 = North)
            
            PredictedLocation(Coordinate coordinate, double confidenceScore, 
                             double velocityMetersPerSec, double directionDegrees) {
                this.coordinate = coordinate;
                this.timestamp = System.currentTimeMillis();
                this.confidenceScore = confidenceScore;
                this.velocityMetersPerSec = velocityMetersPerSec;
                this.directionDegrees = directionDegrees;
            }
            
            boolean isExpired() {
                return System.currentTimeMillis() - timestamp > PREDICTION_CACHE_EXPIRY_MS;
            }
        }
        
        private static class CachedSmoothedLocation {
            final Coordinate coordinate;
            final long timestamp;
            final SmoothingAlgorithm algorithm;
            
            CachedSmoothedLocation(Coordinate coordinate, SmoothingAlgorithm algorithm) {
                this.coordinate = coordinate;
                this.timestamp = System.currentTimeMillis();
                this.algorithm = algorithm;
            }
            
            boolean isExpired() {
                return System.currentTimeMillis() - timestamp > SMOOTHED_CACHE_EXPIRY_MS;
            }
        }
        
        private static class LocationEntry {
            final double latitude;
            final double longitude;
            final long timestamp;
            
            LocationEntry(double latitude, double longitude, long timestamp) {
                this.latitude = latitude;
                this.longitude = longitude;
                this.timestamp = timestamp;
            }
        }
        
        /**
         * Constructs a LocationHistoryManager with the specified history size.
         * 
         * @param historySize The number of location entries to maintain per player
         */
        public LocationHistoryManager(int historySize) {
            this.historySize = Math.max(historySize, 2); // Minimum of 2 entries for meaningful smoothing
            this.playerLocationHistory = new ConcurrentHashMap<>();
            this.smoothedLocationCache = new ConcurrentHashMap<>();
            this.predictionsCache = new ConcurrentHashMap<>();
        }
        
        /**
         * Sets the default smoothing algorithm to use.
         * 
         * @param algorithm The smoothing algorithm to use by default
         */
        public void setDefaultSmoothingAlgorithm(SmoothingAlgorithm algorithm) {
            if (algorithm != null) {
                this.defaultAlgorithm = algorithm;
                // Clear cache when algorithm changes
                this.smoothedLocationCache.clear();
            }
        }
        
        /**
         * Sets the exponential decay factor for the EXPONENTIAL_DECAY algorithm.
         * Higher values (closer to 1.0) give more weight to recent points.
         * 
         * @param factor Decay factor, between 0.0 and 1.0 (default 0.5)
         */
        public void setExponentialDecayFactor(double factor) {
            if (factor > 0.0 && factor <= 1.0) {
                this.exponentialDecayFactor = factor;
                // Clear cache when parameters change
                this.smoothedLocationCache.clear();
            }
        }
        
        /**
         * Configures the prediction parameters.
         * 
         * @param predictionTimeMs Time window for prediction in milliseconds
         * @param minPointsForPrediction Minimum points needed to make a prediction
         * @param velocityThreshold Minimum velocity (m/s) to make predictions 
         * @param maxDistance Maximum prediction distance in meters
         */
        public void configurePrediction(int predictionTimeMs, int minPointsForPrediction, 
                                       double velocityThreshold, double maxDistance) {
            if (predictionTimeMs > 0) {
                this.predictionTimeMs = predictionTimeMs;
            }
            if (minPointsForPrediction >= 2) {
                this.minPointsForPrediction = minPointsForPrediction;
            }
            if (velocityThreshold >= 0.0) {
                this.velocityThresholdMetersPerSec = velocityThreshold;
            }
            if (maxDistance > 0.0) {
                this.maxPredictionDistanceMeters = maxDistance;
            }
            // Clear predictions when parameters change
            this.predictionsCache.clear();
        }
        
        /**
         * Gets a smoothed location for a player based on recent history.
         * Uses the default smoothing algorithm.
         * 
         * @param playerId ID of the player
         * @param currentLat Current latitude (fallback if no history)
         * @param currentLon Current longitude (fallback if no history)
         * @return Coordinate object with smoothed location
         */
        public Coordinate getSmoothedLocation(String playerId, Double currentLat, Double currentLon) {
            return getSmoothedLocation(playerId, currentLat, currentLon, defaultAlgorithm);
        }
        
        /**
         * Gets a smoothed location for a player based on recent history.
         * Uses the specified smoothing algorithm.
         * 
         * @param playerId ID of the player
         * @param currentLat Current latitude (fallback if no history)
         * @param currentLon Current longitude (fallback if no history)
         * @param algorithm Smoothing algorithm to use
         * @return Coordinate object with smoothed location
         */
        public Coordinate getSmoothedLocation(String playerId, Double currentLat, Double currentLon, SmoothingAlgorithm algorithm) {
            if (playerId == null || currentLat == null || currentLon == null) {
                return currentLat != null && currentLon != null 
                    ? new Coordinate(currentLat, currentLon) 
                    : null;
            }
            
            // Default to LINEAR_WEIGHTED if null algorithm provided
            if (algorithm == null) {
                algorithm = SmoothingAlgorithm.LINEAR_WEIGHTED;
            }
            
            // Handle predictive algorithm specially
            if (algorithm == SmoothingAlgorithm.PREDICTIVE) {
                return getPredictedLocation(playerId, currentLat, currentLon);
            }
            
            // Check if we have a cached result that's still valid for this algorithm
            CachedSmoothedLocation cachedLocation = smoothedLocationCache.get(playerId);
            if (cachedLocation != null && !cachedLocation.isExpired() && cachedLocation.algorithm == algorithm) {
                return cachedLocation.coordinate;
            }
            
            List<LocationEntry> history = playerLocationHistory.get(playerId);
            if (history == null || history.isEmpty()) {
                // Add current location to history for future smoothing
                addLocation(playerId, currentLat, currentLon);
                Coordinate result = new Coordinate(currentLat, currentLon);
                smoothedLocationCache.put(playerId, new CachedSmoothedLocation(result, algorithm));
                return result;
            }
            
            // Add current location to history
            addLocation(playerId, currentLat, currentLon);
            
            // Get a thread-safe snapshot of the current history
            List<LocationEntry> historySnapshot = new ArrayList<>(playerLocationHistory.get(playerId));
            
            // Calculate smoothed location based on the selected algorithm
            Coordinate result;
            switch (algorithm) {
                case EXPONENTIAL_DECAY:
                    result = calculateExponentialDecayAverage(historySnapshot);
                    break;
                case SIMPLE_AVERAGE:
                    result = calculateSimpleAverage(historySnapshot);
                    break;
                case LINEAR_WEIGHTED:
                default:
                    result = calculateLinearWeightedAverage(historySnapshot);
                    break;
            }
            
            // Cache the result
            smoothedLocationCache.put(playerId, new CachedSmoothedLocation(result, algorithm));
            
            return result;
        }
        
        /**
         * Gets a predicted future location based on movement velocity and direction.
         * 
         * @param playerId ID of the player
         * @param currentLat Current latitude
         * @param currentLon Current longitude 
         * @return Predicted coordinate or current position if prediction not possible
         */
        public Coordinate getPredictedLocation(String playerId, Double currentLat, Double currentLon) {
            // Check for valid inputs
            if (playerId == null || currentLat == null || currentLon == null) {
                return currentLat != null && currentLon != null 
                    ? new Coordinate(currentLat, currentLon) 
                    : null;
            }
            
            // Check if we have a cached prediction
            PredictedLocation cachedPrediction = predictionsCache.get(playerId);
            if (cachedPrediction != null && !cachedPrediction.isExpired()) {
                return cachedPrediction.coordinate;
            }
            
            // Calculate the smoothed current position using LINEAR_WEIGHTED algorithm first
            Coordinate smoothedCurrentPosition = getSmoothedLocation(playerId, currentLat, currentLon, SmoothingAlgorithm.LINEAR_WEIGHTED);
            
            // Make sure there's enough history to make predictions
            List<LocationEntry> history = playerLocationHistory.get(playerId);
            if (history == null || history.size() < minPointsForPrediction) {
                // Not enough history, return smoothed current position
                addLocation(playerId, currentLat, currentLon); // Still update history
                return smoothedCurrentPosition;
            }
            
            // Take a snapshot of the history and sort by timestamp
            List<LocationEntry> historySnapshot = new ArrayList<>(history);
            historySnapshot.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
            
            // Analyze recent movement patterns to predict future position
            double[] velocityAndDirection = calculateVelocityAndDirection(historySnapshot);
            double velocityMetersPerSec = velocityAndDirection[0];
            double directionDegrees = velocityAndDirection[1];
            
            // Check if the player is moving fast enough to make predictions
            if (velocityMetersPerSec < velocityThresholdMetersPerSec) {
                // Too slow, use smoothed current position
                PredictedLocation lowConfidencePrediction = new PredictedLocation(
                    smoothedCurrentPosition, 0.1, velocityMetersPerSec, directionDegrees);
                predictionsCache.put(playerId, lowConfidencePrediction);
                return smoothedCurrentPosition;
            }
            
            // Calculate prediction time (how far into the future to predict)
            double predictionTimeSeconds = predictionTimeMs / 1000.0;
            
            // Calculate predicted movement distance in meters
            double distanceMeters = velocityMetersPerSec * predictionTimeSeconds;
            
            // Cap the maximum prediction distance
            distanceMeters = Math.min(distanceMeters, maxPredictionDistanceMeters);
            
            // Convert direction to radians for math calculations
            double directionRadians = Math.toRadians(directionDegrees);
            
            // Calculate the predicted position using the bearing formula
            // (simplified version for small distances)
            double latRad = Math.toRadians(smoothedCurrentPosition.getLatitude());
            double lonRad = Math.toRadians(smoothedCurrentPosition.getLongitude());
            
            // Earth radius in meters
            double earthRadius = 6371000;
            
            // Calculate new position
            double newLatRad = latRad + (distanceMeters / earthRadius) * Math.cos(directionRadians);
            double newLonRad = lonRad + (distanceMeters / earthRadius) * Math.sin(directionRadians) / Math.cos(latRad);
            
            // Convert back to degrees
            double newLat = Math.toDegrees(newLatRad);
            double newLon = Math.toDegrees(newLonRad);
            
            // Create the predicted coordinate
            Coordinate predictedCoordinate = new Coordinate(newLat, newLon);
            
            // Calculate confidence score (0.0-1.0) based on:
            // - consistency of velocity and direction
            // - number of points in history
            // - recency of points
            double consistencyScore = calculateMovementConsistency(historySnapshot);
            double pointsScore = Math.min(1.0, (history.size() / (double)historySize));
            double confidenceScore = consistencyScore * 0.7 + pointsScore * 0.3;
            
            // Create and cache the prediction
            PredictedLocation prediction = new PredictedLocation(
                predictedCoordinate, confidenceScore, velocityMetersPerSec, directionDegrees);
            predictionsCache.put(playerId, prediction);
            
            return predictedCoordinate;
        }
        
        /**
         * Calculates the current velocity (meters/second) and direction (degrees)
         * based on recent location history.
         * 
         * @param history List of location entries sorted by timestamp
         * @return Array with [velocityMetersPerSec, directionDegrees]
         */
        private double[] calculateVelocityAndDirection(List<LocationEntry> history) {
            // Need at least 2 points to calculate velocity and direction
            if (history.size() < 2) {
                return new double[] {0.0, 0.0}; // No movement
            }
            
            // Get the most recent entries
            LocationEntry newest = history.get(history.size() - 1);
            LocationEntry previous = history.get(history.size() - 2);
            
            // Calculate time difference in seconds
            double timeDiffSec = (newest.timestamp - previous.timestamp) / 1000.0;
            if (timeDiffSec <= 0.0) {
                return new double[] {0.0, 0.0}; // No time elapsed
            }
            
            // Create coordinates
            Coordinate newestCoord = new Coordinate(newest.latitude, newest.longitude);
            Coordinate previousCoord = new Coordinate(previous.latitude, previous.longitude);
            
            // Calculate distance in meters
            double distanceMeters;
            try {
                distanceMeters = GeoUtils.calculateDistance(previousCoord, newestCoord);
            } catch (Exception e) {
                // Handle calculation error
                return new double[] {0.0, 0.0};
            }
            
            // Calculate velocity in meters per second
            double velocityMetersPerSec = distanceMeters / timeDiffSec;
            
            // Calculate direction (bearing) in degrees
            double directionDegrees;
            try {
                directionDegrees = GeoUtils.calculateBearing(
                    previousCoord.getLatitude(), previousCoord.getLongitude(),
                    newestCoord.getLatitude(), newestCoord.getLongitude());
            } catch (Exception e) {
                directionDegrees = 0.0;
            }
            
            return new double[] {velocityMetersPerSec, directionDegrees};
        }
        
        /**
         * Calculates how consistent the recent movement has been.
         * Higher scores mean more consistent velocity and direction.
         * 
         * @param history List of location entries sorted by timestamp
         * @return Consistency score between 0.0 and 1.0
         */
        private double calculateMovementConsistency(List<LocationEntry> history) {
            if (history.size() < 3) {
                return 0.5; // Neutral score with limited data
            }
            
            double directionConsistency = 0.0;
            double velocityConsistency = 0.0;
            
            // Compare successive segments' directions and velocities
            List<Double> directions = new ArrayList<>();
            List<Double> velocities = new ArrayList<>();
            
            for (int i = 0; i < history.size() - 1; i++) {
                LocationEntry current = history.get(i);
                LocationEntry next = history.get(i + 1);
                
                Coordinate currentCoord = new Coordinate(current.latitude, current.longitude);
                Coordinate nextCoord = new Coordinate(next.latitude, next.longitude);
                
                // Skip entries with identical timestamps
                double timeDiffSec = (next.timestamp - current.timestamp) / 1000.0;
                if (timeDiffSec <= 0.0) continue;
                
                try {
                    // Calculate direction (bearing)
                    double bearing = GeoUtils.calculateBearing(
                        currentCoord.getLatitude(), currentCoord.getLongitude(),
                        nextCoord.getLatitude(), nextCoord.getLongitude());
                    directions.add(bearing);
                    
                    // Calculate velocity
                    double distance = GeoUtils.calculateDistance(currentCoord, nextCoord);
                    double velocity = distance / timeDiffSec;
                    velocities.add(velocity);
                } catch (Exception e) {
                    // Skip on calculation error
                    continue;
                }
            }
            
            // Calculate direction consistency (angle variation)
            if (directions.size() >= 2) {
                double sumAngleDiff = 0.0;
                for (int i = 0; i < directions.size() - 1; i++) {
                    double diff = Math.min(
                        Math.abs(directions.get(i + 1) - directions.get(i)),
                        360 - Math.abs(directions.get(i + 1) - directions.get(i))
                    );
                    sumAngleDiff += diff;
                }
                double avgAngleDiff = sumAngleDiff / (directions.size() - 1);
                // Convert to 0-1 score (0 degrees diff = 1.0, 180 degrees diff = 0.0)
                directionConsistency = 1.0 - (avgAngleDiff / 180.0);
            }
            
            // Calculate velocity consistency (coefficient of variation)
            if (velocities.size() >= 2) {
                double sum = 0.0;
                double sumSquared = 0.0;
                for (double v : velocities) {
                    sum += v;
                    sumSquared += v * v;
                }
                double mean = sum / velocities.size();
                double variance = (sumSquared / velocities.size()) - (mean * mean);
                double stdDev = Math.sqrt(variance);
                double cv = mean > 0 ? stdDev / mean : 1.0; // Coefficient of variation
                
                // Convert to 0-1 score (lower CV = higher consistency)
                velocityConsistency = Math.max(0.0, 1.0 - Math.min(1.0, cv));
            }
            
            // Return weighted combination
            return directionConsistency * 0.7 + velocityConsistency * 0.3;
        }
        
        /**
         * Adds a new location to the player's history.
         * Thread-safe implementation using ConcurrentHashMap compute method.
         * 
         * @param playerId ID of the player
         * @param latitude Current latitude
         * @param longitude Current longitude
         */
        public void addLocation(String playerId, double latitude, double longitude) {
            if (playerId == null || playerId.isEmpty()) {
                return;
            }
            
            long now = System.currentTimeMillis();
            LocationEntry entry = new LocationEntry(latitude, longitude, now);
            
            playerLocationHistory.compute(playerId, (key, existingHistory) -> {
                List<LocationEntry> updatedHistory = existingHistory;
                if (updatedHistory == null) {
                    updatedHistory = new ArrayList<>(historySize);
                }
                
                updatedHistory.add(entry);
                
                // Create a new list for removal to avoid concurrent modification issues
                final List<LocationEntry> historyToModify = updatedHistory;
                if (historyToModify.size() > historySize) {
                    // Remove oldest entry, keeping the latest ones
                    historyToModify.remove(0);
                }
                
                return historyToModify;
            });
            
            // Invalidate the cached smoothed location for this player
            smoothedLocationCache.remove(playerId);
            
            // Periodically clean up stale history entries
            if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
                cleanupStaleHistory();
                lastCleanupTime = now;
            }
        }
        
        /**
         * Calculates a linearly weighted average position based on recency.
         * 
         * @param history List of location entries
         * @return Smoothed coordinate
         */
        private Coordinate calculateLinearWeightedAverage(List<LocationEntry> history) {
            double totalWeight = 0;
            double[] weights = new double[history.size()];
            long now = System.currentTimeMillis();
            
            for (int i = 0; i < history.size(); i++) {
                LocationEntry entry = history.get(i);
                // Linear weight based on age: weight = 1.0 - (age/maxAge)
                // Newer entries get higher weights (closer to 1.0)
                double age = (now - entry.timestamp) / (double)LOCATION_STALENESS_THRESHOLD_MS;
                weights[i] = 1.0 - Math.min(age, 0.9); // Ensure some minimal weight
                totalWeight += weights[i];
            }
            
            // Calculate weighted average
            double latSum = 0;
            double lonSum = 0;
            
            for (int i = 0; i < history.size(); i++) {
                LocationEntry entry = history.get(i);
                double normalizedWeight = weights[i] / totalWeight;
                latSum += entry.latitude * normalizedWeight;
                lonSum += entry.longitude * normalizedWeight;
            }
            
            return new Coordinate(latSum, lonSum);
        }
        
        /**
         * Calculates an exponentially weighted average where weight decreases
         * exponentially with age (more rapidly than linear weighting).
         * 
         * @param history List of location entries
         * @return Smoothed coordinate
         */
        private Coordinate calculateExponentialDecayAverage(List<LocationEntry> history) {
            double totalWeight = 0;
            double[] weights = new double[history.size()];
            long now = System.currentTimeMillis();
            
            for (int i = 0; i < history.size(); i++) {
                LocationEntry entry = history.get(i);
                // Exponential decay: weight = e^(-decayFactor * age)
                double ageRatio = (now - entry.timestamp) / (double)LOCATION_STALENESS_THRESHOLD_MS;
                weights[i] = Math.exp(-exponentialDecayFactor * ageRatio * 10.0);
                totalWeight += weights[i];
            }
            
            // Calculate weighted average
            double latSum = 0;
            double lonSum = 0;
            
            for (int i = 0; i < history.size(); i++) {
                LocationEntry entry = history.get(i);
                double normalizedWeight = weights[i] / totalWeight;
                latSum += entry.latitude * normalizedWeight;
                lonSum += entry.longitude * normalizedWeight;
            }
            
            return new Coordinate(latSum, lonSum);
        }
        
        /**
         * Calculates a simple average of all points (equal weights).
         * 
         * @param history List of location entries
         * @return Smoothed coordinate
         */
        private Coordinate calculateSimpleAverage(List<LocationEntry> history) {
            double latSum = 0;
            double lonSum = 0;
            
            for (LocationEntry entry : history) {
                latSum += entry.latitude;
                lonSum += entry.longitude;
            }
            
            return new Coordinate(latSum / history.size(), lonSum / history.size());
        }
        
        /**
         * Cleans up location history for all players or a specific player.
         * 
         * @param playerId ID of player (null to clear all)
         */
        public void clearHistory(String playerId) {
            if (playerId == null) {
                playerLocationHistory.clear();
                smoothedLocationCache.clear();
                predictionsCache.clear();
            } else {
                playerLocationHistory.remove(playerId);
                smoothedLocationCache.remove(playerId);
                predictionsCache.remove(playerId);
            }
        }
        
        /**
         * Cleans up stale history entries that haven't been updated in a while.
         * Helps prevent memory leaks from players who are no longer active.
         */
        private void cleanupStaleHistory() {
            long now = System.currentTimeMillis();
            
            // Clean up stale history entries
            playerLocationHistory.entrySet().removeIf(entry -> {
                List<LocationEntry> history = entry.getValue();
                if (history.isEmpty()) {
                    return true; // Remove empty history
                }
                
                // Check timestamp of the most recent entry
                LocationEntry mostRecent = history.get(history.size() - 1);
                return (now - mostRecent.timestamp) > HISTORY_CLEANUP_THRESHOLD_MS;
            });
            
            // Clean up stale cached locations
            smoothedLocationCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            
            // Clean up stale predictions
            predictionsCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
        
        /**
         * Gets the current history size for a player.
         * Useful for diagnostics and testing.
         * 
         * @param playerId ID of the player
         * @return Number of location entries for the player, 0 if none
         */
        public int getHistorySize(String playerId) {
            List<LocationEntry> history = playerLocationHistory.get(playerId);
            return history != null ? history.size() : 0;
        }
        
        /**
         * Gets detailed motion analytics for a player including velocity, 
         * direction, and prediction confidence.
         * 
         * @param playerId ID of the player to analyze
         * @return Map containing motion analytics or null if insufficient data
         */
        public Map<String, Object> getPlayerMotionAnalytics(String playerId) {
            if (playerId == null) return null;
            
            PredictedLocation prediction = predictionsCache.get(playerId);
            if (prediction == null || prediction.isExpired()) {
                // Try generating a new prediction if we have current coordinates
                List<LocationEntry> history = playerLocationHistory.get(playerId);
                if (history != null && !history.isEmpty()) {
                    LocationEntry latest = history.get(history.size() - 1);
                    getPredictedLocation(playerId, latest.latitude, latest.longitude);
                    prediction = predictionsCache.get(playerId);
                }
            }
            
            if (prediction == null) return null;
            
            Map<String, Object> analytics = new HashMap<>();
            analytics.put("velocityMetersPerSec", prediction.velocityMetersPerSec);
            analytics.put("directionDegrees", prediction.directionDegrees);
            analytics.put("confidenceScore", prediction.confidenceScore);
            analytics.put("predictionAgeMs", System.currentTimeMillis() - prediction.timestamp);
            analytics.put("predictionCoordinate", prediction.coordinate);
            
            return analytics;
        }
    }
    
    /**
     * Represents the result of a proximity check between two players.
     */
    public static class ProximityResult {
        private final String player1Id;
        private final String player2Id;
        private final double distance;
        private final long timestamp;
        private final boolean isInRange;
        
        public ProximityResult(String player1Id, String player2Id, double distance, boolean isInRange) {
            this.player1Id = player1Id;
            this.player2Id = player2Id;
            this.distance = distance;
            this.timestamp = System.currentTimeMillis();
            this.isInRange = isInRange;
        }
        
        public String getPlayer1Id() {
            return player1Id;
        }
        
        public String getPlayer2Id() {
            return player2Id;
        }
        
        public double getDistance() {
            return distance;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public boolean isInRange() {
            return isInRange;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRATION_MS;
        }
    }
    
    /**
     * Default constructor that initializes dependencies.
     */
    public ProximityDetectionService() {
        this(new DynamoDbPlayerDao(), new DynamoDbGameDao(), new LocationService(), new MapConfigurationService(new DynamoDbGameDao(), null, null, null), new NotificationService());
    }
    
    /**
     * Constructor for dependency injection.
     * 
     * @param playerDao Data access for player information
     * @param gameDao Data access for game configuration
     * @param locationService Service for location-related operations
     * @param mapConfigService Service for retrieving map configuration
     * @param notificationService Service for sending notifications
     */
    public ProximityDetectionService(PlayerDao playerDao, GameDao gameDao, LocationService locationService, MapConfigurationService mapConfigService, NotificationService notificationService) {
        this.playerDao = Objects.requireNonNull(playerDao, "playerDao cannot be null");
        this.gameDao = Objects.requireNonNull(gameDao, "gameDao cannot be null");
        this.locationService = Objects.requireNonNull(locationService, "locationService cannot be null");
        this.mapConfigService = Objects.requireNonNull(mapConfigService, "mapConfigService cannot be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService cannot be null");
        this.proximityCache = new ConcurrentHashMap<>();
        this.alertCache = new ConcurrentHashMap<>();
        this.locationHistoryManager = new LocationHistoryManager(LOCATION_HISTORY_SIZE);
    }
    
    /**
     * Checks if a player is close enough to their target for an elimination attempt.
     * Takes into account game rules, weapon types, player status, location validity, and GPS inaccuracy.
     * Uses location history smoothing to reduce GPS jitter.
     * 
     * @param gameId ID of the game
     * @param playerId ID of the player attempting elimination (killer)
     * @param targetId ID of the target player (victim)
     * @param weaponType Optional weapon type affecting elimination distance (e.g., "MELEE", "SNIPER")
     * @return true if the killer can eliminate the victim based on proximity and status, false otherwise
     * @throws PlayerNotFoundException If either player cannot be found
     * @throws GameNotFoundException If the game cannot be found
     */
    public boolean canEliminateTarget(String gameId, String playerId, String targetId, String weaponType) 
            throws PlayerNotFoundException, GameNotFoundException {
        
        // Validate inputs
        if (gameId == null || playerId == null || targetId == null) {
            logger.warn("Null parameters provided to canEliminateTarget: gameId={}, playerId={}, targetId={}", 
                      gameId, playerId, targetId);
            return false; // Cannot proceed with null IDs
        }
        
        if (playerId.equals(targetId)) {
             logger.warn("Player {} attempted to eliminate themselves.", playerId);
             return false; // Cannot eliminate self
        }
        
        // Fetch player data first for status and location checks
        Player killer = playerDao.getPlayerById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("Killer not found: " + playerId));
        Player victim = playerDao.getPlayerById(targetId)
                .orElseThrow(() -> new PlayerNotFoundException("Victim not found: " + targetId));
        
        // Check Player Status: Killer must be ACTIVE, Victim can be ACTIVE or PENDING_DEATH
        if (!PlayerStatus.ACTIVE.name().equals(killer.getStatus())) {
            logger.debug("Cannot eliminate: Killer {} is not ACTIVE (status: {})", playerId, killer.getStatus());
            return false;
        }
        if (!PlayerStatus.ACTIVE.name().equals(victim.getStatus()) && 
            !PlayerStatus.PENDING_DEATH.name().equals(victim.getStatus())) {
            logger.debug("Cannot eliminate: Victim {} is not ACTIVE or PENDING_DEATH (status: {})", targetId, victim.getStatus());
            return false;
        }
        
        // Check Location Availability and Staleness
        if (killer.getLatitude() == null || killer.getLongitude() == null || killer.getLocationTimestamp() == null) {
            logger.warn("Cannot eliminate: Killer {} has no location data.", playerId);
            return false;
        }
        if (victim.getLatitude() == null || victim.getLongitude() == null || victim.getLocationTimestamp() == null) {
            logger.warn("Cannot eliminate: Victim {} has no location data.", targetId);
            return false;
        }
        
        // Check staleness
        long nowMillis = System.currentTimeMillis();
        try {
            Instant killerLocationInstant = Instant.parse(killer.getLocationTimestamp());
            if (nowMillis - killerLocationInstant.toEpochMilli() > LOCATION_STALENESS_THRESHOLD_MS) {
                logger.warn("Cannot eliminate: Killer {} location data is too old (timestamp: {}, Threshold: {}ms)", 
                          playerId, killer.getLocationTimestamp(), LOCATION_STALENESS_THRESHOLD_MS);
                return false;
            }

            Instant victimLocationInstant = Instant.parse(victim.getLocationTimestamp());
            if (nowMillis - victimLocationInstant.toEpochMilli() > LOCATION_STALENESS_THRESHOLD_MS) {
                logger.warn("Cannot eliminate: Victim {} location data is too old (timestamp: {}, Threshold: {}ms)", 
                          targetId, victim.getLocationTimestamp(), LOCATION_STALENESS_THRESHOLD_MS);
                return false;
            }
        } catch (DateTimeParseException e) {
            logger.error("Cannot eliminate: Failed to parse location timestamp for killer {} ({}) or victim {} ({}): {}", 
                         playerId, killer.getLocationTimestamp(), targetId, victim.getLocationTimestamp(), e.getMessage());
            return false; // Treat unparseable timestamps as stale/invalid
        }

        // Get the game to check game-specific settings (already checked players, less likely to throw here)
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found: " + gameId + " (referenced by players " + playerId + ", " + targetId + ")"));
        
        // Get smoothed locations for both players using location history
        // This helps reduce jitter and improve accuracy of distance calculation
        Coordinate smoothedKillerCoordinate = locationHistoryManager.getSmoothedLocation(
                killer.getPlayerID(), killer.getLatitude(), killer.getLongitude());
                
        Coordinate smoothedVictimCoordinate = locationHistoryManager.getSmoothedLocation(
                victim.getPlayerID(), victim.getLatitude(), victim.getLongitude());
        
        // Ensure we have valid smoothed coordinates to proceed
        if (smoothedKillerCoordinate == null || smoothedVictimCoordinate == null) {
             logger.warn("Cannot calculate elimination proximity: Failed to get smoothed location for killer {} or victim {}", playerId, targetId);
             return false;
        }
        
        // Check if killer is in a safe zone (using smoothed coordinates)
        long currentTimeMillis = System.currentTimeMillis();
        if (mapConfigService.isLocationInSafeZone(gameId, killer.getPlayerID(), smoothedKillerCoordinate, currentTimeMillis)) {
            logger.debug("Cannot eliminate: Killer {} is in a safe zone (smoothed location)", playerId);
            return false;
        }
        
        // Check if target is in a safe zone (using smoothed coordinates)
        if (mapConfigService.isLocationInSafeZone(gameId, victim.getPlayerID(), smoothedVictimCoordinate, currentTimeMillis)) {
            logger.debug("Cannot eliminate: Target {} is in a safe zone (smoothed location)", targetId);
            return false; // FIX: Add missing return statement
        }
        
        // Get map configuration for proximity settings
        MapConfiguration mapConfig = mapConfigService.getEffectiveMapConfiguration(gameId);

        // Determine elimination distance based on map config and weapon type
        double eliminationDistance = getEliminationDistance(mapConfig, weaponType);
        
        // Add buffer for GPS inaccuracy
        double effectiveDistance = eliminationDistance + GPS_ACCURACY_BUFFER;
        
        logger.debug("Checking proximity for elimination in game {}. Killer: {} ({}), Victim: {} ({}). Weapon: {}. Required distance: {:.2f}m ({:.2f}m base + {:.2f}m buffer) using smoothed locations.",
                   gameId, playerId, killer.getStatus(), targetId, victim.getStatus(), weaponType, effectiveDistance, eliminationDistance, GPS_ACCURACY_BUFFER);
        
        // Calculate actual distance using smoothed coordinates
        double actualDistance;
        try {
             actualDistance = GeoUtils.calculateDistance(smoothedKillerCoordinate, smoothedVictimCoordinate);
        } catch (Exception e) {
             logger.error("Error calculating distance between smoothed locations for {} and {}: {}", 
                        playerId, targetId, e.getMessage(), e);
             return false; // Cannot determine proximity if distance calculation fails
        }
        
        // Check if the actual distance is within the effective range
        boolean inRange = actualDistance <= effectiveDistance;
        
        logger.info("Elimination check result for {} -> {}: In Range = {} (Smoothed Actual: {:.2f}m, Required: {:.2f}m)", 
                   playerId, targetId, inRange, actualDistance, effectiveDistance);

        // Cache the result (using actual smoothed distance)
        proximityCache.put(generateCacheKey(gameId, playerId, targetId), new ProximityResult(playerId, targetId, actualDistance, inRange));
        
        return inRange;
    }
    
    /**
     * Calculate the actual distance between two players.
     * Uses smoothed locations from LocationHistoryManager to reduce GPS jitter.
     * 
     * @param player1Id First player ID
     * @param player2Id Second player ID
     * @return Distance in meters, or Double.MAX_VALUE if locations are unknown
     * @throws PlayerNotFoundException If either player cannot be found
     */
    private double calculateDistanceBetweenPlayers(String player1Id, String player2Id) throws PlayerNotFoundException {
        Player player1 = playerDao.getPlayerById(player1Id)
                .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + player1Id));
        Player player2 = playerDao.getPlayerById(player2Id)
                .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + player2Id));

        // Check if the players have valid location data
        if (player1.getLatitude() == null || player1.getLongitude() == null ||
            player2.getLatitude() == null || player2.getLongitude() == null) {
            
            logger.warn("Cannot calculate distance: one or both players have null location data. P1: {}, P2: {}", 
                      player1.getPlayerID(), player2.getPlayerID());
            return Double.MAX_VALUE;
        }
        
        // Use smoothed locations for both players
        Coordinate smoothedLocation1 = locationHistoryManager.getSmoothedLocation(
                player1.getPlayerID(), player1.getLatitude(), player1.getLongitude());
                
        Coordinate smoothedLocation2 = locationHistoryManager.getSmoothedLocation(
                player2.getPlayerID(), player2.getLatitude(), player2.getLongitude());
        
        // Check if we got valid smoothed coordinates
        if (smoothedLocation1 == null || smoothedLocation2 == null) {
            logger.warn("Failed to get smoothed locations for players {} and/or {}", player1Id, player2Id);
            // Fall back to direct calculation with raw coordinates if smoothing fails
            return calculateDistanceBetweenPlayersInternal(player1, player2);
        }
        
        try {
            double distance = GeoUtils.calculateDistance(smoothedLocation1, smoothedLocation2);
            logger.debug("Distance between {} and {} (smoothed): {:.2f}m", player1Id, player2Id, distance);
            return distance;
        } catch (Exception e) {
            logger.error("Error calculating distance between smoothed locations for {} and {}: {}", 
                       player1Id, player2Id, e.getMessage(), e);
            return Double.MAX_VALUE;
        }
    }
    
    /**
     * Determine the elimination distance based on game settings and weapon type.
     * Prioritizes weapon-specific distance, then map default, then global default.
     * 
     * @param mapConfig Map configuration for game
     * @param weaponType Type of weapon being used (case-insensitive lookup)
     * @return Distance in meters required for elimination
     */
    private double getEliminationDistance(MapConfiguration mapConfig, String weaponType) {
        // 1. Check for weapon-specific distance in map config
        if (weaponType != null && mapConfig.getWeaponDistances() != null) {
            // Perform case-insensitive lookup if desired, or store keys consistently
            String lookupKey = weaponType.toUpperCase(); // Example: Store/lookup keys in uppercase
            Double weaponDistance = mapConfig.getWeaponDistances().get(lookupKey);
            if (weaponDistance != null) {
                 logger.debug("Using weapon-specific distance for '{}': {}m", weaponType, weaponDistance);
                 return weaponDistance;
            } else {
                 logger.debug("Weapon type '{}' not found in map config weaponDistances, checking default.", weaponType);
            }
        }

        // 2. Fall back to map default elimination distance
        Double mapDefaultDistance = mapConfig.getEliminationDistanceMeters();
        if (mapDefaultDistance != null) {
             logger.debug("Using map default elimination distance: {}m", mapDefaultDistance);
             return mapDefaultDistance;
        }

        // 3. Fall back to global default if nothing else is defined
        logger.warn("Elimination distance not specified in map config (mapId: {}), using global default: {}m", 
                    mapConfig.getMapId(), DEFAULT_ELIMINATION_DISTANCE);
        return DEFAULT_ELIMINATION_DISTANCE;
    }
    
    /**
     * Get recently cached proximity results for a player.
     * Useful for UI updates and notifications.
     * 
     * @param playerId ID of the player to get proximity results for
     * @return Map of target player IDs to proximity results
     */
    public Map<String, ProximityResult> getRecentProximityResults(String playerId) {
        Map<String, ProximityResult> results = new HashMap<>();
        
        // Clean expired results while collecting player's results
        proximityCache.entrySet().removeIf(entry -> {
            ProximityResult result = entry.getValue();
            
            // Check if result is expired
            if (result.isExpired()) {
                return true; // Remove expired entries
            }
            
            // If this entry involves the player, add it to results
            if (result.getPlayer1Id().equals(playerId) || result.getPlayer2Id().equals(playerId)) {
                String otherPlayerId = result.getPlayer1Id().equals(playerId) ? 
                                     result.getPlayer2Id() : result.getPlayer1Id();
                results.put(otherPlayerId, result);
            }
            
            return false; // Keep the entry
        });
        
        return results;
    }
    
    /**
     * Clears the proximity cache for a specific game.
     * Should be called when game state changes significantly.
     * 
     * @param gameId ID of the game to clear cache for
     */
    public void clearProximityCache(String gameId) {
        if (gameId == null) {
            return;
        }
        
        proximityCache.keySet().removeIf(key -> key.startsWith(gameId + ":"));
        logger.debug("Cleared proximity cache for game {}", gameId);
    }
    
    /**
     * Generate a cache key for proximity checks.
     * 
     * @param gameId Game ID
     * @param player1Id First player ID
     * @param player2Id Second player ID
     * @return Cache key string
     */
    private String generateCacheKey(String gameId, String player1Id, String player2Id) {
        // Ensure consistent ordering of player IDs for bidirectional caching
        if (player1Id.compareTo(player2Id) > 0) {
            String temp = player1Id;
            player1Id = player2Id;
            player2Id = temp;
        }
        
        return gameId + ":" + player1Id + ":" + player2Id;
    }

    /**
     * Checks proximity of a player to their target and any hunters, sending alerts if necessary.
     * Uses smoothed location data to provide more consistent alerts and reduce GPS jitter effects.
     *
     * @param gameId ID of the game
     * @param playerId ID of the player to check alerts for
     */
    public void checkAndSendProximityAlerts(String gameId, String playerId) {
        logger.debug("Checking proximity alerts (using smoothed locations) for player {} in game {}", playerId, gameId);
        Player player;
        Game game;
        MapConfiguration mapConfig;
        try {
            player = playerDao.getPlayerById(playerId)
                    .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + playerId));
            game = gameDao.getGameById(gameId)
                    .orElseThrow(() -> new GameNotFoundException("Game not found: " + gameId));
            mapConfig = mapConfigService.getEffectiveMapConfiguration(gameId);

            if (player.getLatitude() == null || player.getLongitude() == null) {
                logger.warn("Cannot check proximity alerts for player {}: location unknown.", playerId);
                return;
            }

            Double alertDistance = mapConfig.getProximityAwarenessDistanceMeters();
            if (alertDistance == null) {
                logger.warn("Proximity awareness distance not configured for map {} in game {}. Skipping alerts.",
                          mapConfig.getMapId(), gameId);
                return;
            }

            double effectiveAlertDistance = alertDistance + GPS_ACCURACY_BUFFER;
            logger.debug("Using effective alert distance of {:.2f}m ({:.2f}m base + {:.2f}m buffer) for player {}",
                       effectiveAlertDistance, alertDistance, GPS_ACCURACY_BUFFER, playerId);

            // Check distance to target
            String targetId = player.getTargetID();
            if (targetId != null) {
                checkDistanceAndAlert(gameId, player, targetId, effectiveAlertDistance, "target");
            }

            // Check distance to hunters
            List<Player> hunters = playerDao.getPlayersTargeting(playerId, gameId);
            if (hunters != null) {
                for (Player hunter : hunters) {
                    checkDistanceAndAlert(gameId, player, hunter.getPlayerID(), effectiveAlertDistance, "hunter");
                }
            }

        } catch (PlayerNotFoundException | GameNotFoundException e) {
            logger.error("Error checking proximity alerts for player {}: {}", playerId, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error checking proximity alerts for player {}: {}", playerId, e.getMessage(), e);
        }
        // Clean up expired alert cache entries periodically
        cleanupAlertCache();
    }

    /**
     * Helper method to check distance between two players and send an alert if needed.
     * Uses smoothed locations to calculate distances for consistent user experience.
     *
     * @param gameId The game ID.
     * @param player The player receiving the potential alert.
     * @param subjectPlayerId The ID of the other player (target or hunter).
     * @param alertDistance The distance threshold for triggering an alert.
     * @param subjectType "target" or "hunter".
     */
    private void checkDistanceAndAlert(String gameId, Player player, String subjectPlayerId, double alertDistance, String subjectType) {
        // Skip if player doesn't have location or its stale
        if (player.getLatitude() == null || player.getLongitude() == null || player.getLocationTimestamp() == null) {
            logger.debug("Skipping proximity alert: Player {} has no location data", player.getPlayerID());
            return;
        }
        
        try {
            // Check player location staleness
            Instant locationTime = Instant.parse(player.getLocationTimestamp());
            if (System.currentTimeMillis() - locationTime.toEpochMilli() > LOCATION_STALENESS_THRESHOLD_MS) {
                logger.debug("Skipping proximity alert: Player {} location is stale", player.getPlayerID());
                return;
            }
        } catch (DateTimeParseException e) {
            logger.warn("Skipping proximity alert: Could not parse timestamp {} for player {}", 
                      player.getLocationTimestamp(), player.getPlayerID());
            return;
        }
        
        Player subjectPlayer;
        try {
            subjectPlayer = playerDao.getPlayerById(subjectPlayerId)
                    .orElseThrow(() -> new PlayerNotFoundException("Subject player " + subjectPlayerId + " not found"));
            
            // Skip if subject doesn't have location or its stale
            if (subjectPlayer.getLatitude() == null || subjectPlayer.getLongitude() == null || subjectPlayer.getLocationTimestamp() == null) {
                logger.debug("Skipping proximity alert: Subject player {} has no location data", subjectPlayerId);
                return;
            }
            
            try {
                // Check subject location staleness
                Instant subjectLocationTime = Instant.parse(subjectPlayer.getLocationTimestamp());
                if (System.currentTimeMillis() - subjectLocationTime.toEpochMilli() > LOCATION_STALENESS_THRESHOLD_MS) {
                    logger.debug("Skipping proximity alert: Subject player {} location is stale", subjectPlayerId);
                    return;
                }
            } catch (DateTimeParseException e) {
                logger.warn("Skipping proximity alert: Could not parse timestamp {} for subject player {}", 
                          subjectPlayer.getLocationTimestamp(), subjectPlayerId);
                return;
            }
            
            // Calculate distance using smoothed locations
            Coordinate playerCoord = locationHistoryManager.getSmoothedLocation(
                    player.getPlayerID(), player.getLatitude(), player.getLongitude());
            
            Coordinate subjectCoord = locationHistoryManager.getSmoothedLocation(
                    subjectPlayer.getPlayerID(), subjectPlayer.getLatitude(), subjectPlayer.getLongitude());
            
            if (playerCoord == null || subjectCoord == null) {
                logger.warn("Could not calculate smoothed locations for proximity alert between {} and {}", 
                          player.getPlayerID(), subjectPlayerId);
                return;
            }
            
            double distance = GeoUtils.calculateDistance(playerCoord, subjectCoord);
            
            // Send alert if in proximity and not on cooldown
            if (distance <= alertDistance) {
                String alertCacheKey = generateAlertCacheKey(
                        gameId, player.getPlayerID(), subjectPlayerId, subjectType + "_PROXIMITY");
                
                if (!isAlertOnCooldown(alertCacheKey)) {
                    // Create the alert
                    String subjectName = subjectPlayer.getPlayerName() != null ? 
                            subjectPlayer.getPlayerName() : "Unknown";
                    
                    String title = subjectType.substring(0, 1).toUpperCase() + subjectType.substring(1) + " Nearby";
                    String message = "Your " + subjectType.toLowerCase() + " (" + subjectName + ") is nearby! " +
                            "Distance: approximately " + (int)Math.round(distance) + " meters.";
                    
                    Notification notification = new Notification();
                    notification.setGameId(gameId);
                    notification.setRecipientPlayerId(player.getPlayerID());
                    notification.setType(NotificationType.PROXIMITY_ALERT.name());
                    notification.setTitle(title);
                    notification.setMessage(message);
                    notification.setTimestamp(Instant.now().toString());
                    
                    notificationService.sendNotification(notification);
                    
                    // Add to cooldown cache
                    alertCache.put(alertCacheKey, System.currentTimeMillis());
                    
                    logger.info("Sent {} proximity alert to player {} about {} (distance: {:.2f}m)", 
                               subjectType, player.getPlayerID(), subjectPlayerId, distance);
                } else {
                    logger.debug("{} proximity alert for player {} on cooldown", 
                                subjectType, player.getPlayerID());
                }
            }
        } catch (PlayerNotFoundException e) {
            logger.warn("Could not send proximity alert: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Error checking proximity between player {} and subject {}: {}", 
                       player.getPlayerID(), subjectPlayerId, e.getMessage(), e);
        }
    }

    /**
     * Calculate the actual distance between two players using their Player objects.
     * Assumes locations are not null (should be checked before calling).
     *
     * @param player1 First player object
     * @param player2 Second player object
     * @return Distance in meters, or Double.MAX_VALUE if locations are unknown or calculation fails.
     */
    private double calculateDistanceBetweenPlayersInternal(Player player1, Player player2) {
        if (player1 == null || player2 == null || 
            player1.getLatitude() == null || player1.getLongitude() == null ||
            player2.getLatitude() == null || player2.getLongitude() == null) {
            
            logger.warn("Cannot calculate distance: one or both players/locations are null. P1: {}, P2: {}", 
                      player1 != null ? player1.getPlayerID() : "null", 
                      player2 != null ? player2.getPlayerID() : "null");
            return Double.MAX_VALUE; 
        }
        
        try {
            return GeoUtils.calculateDistance(
                new Coordinate(player1.getLatitude(), player1.getLongitude()),
                new Coordinate(player2.getLatitude(), player2.getLongitude())
            );
        } catch (Exception e) {
             logger.error("Error calculating distance between {} and {}: {}", 
                        player1.getPlayerID(), player2.getPlayerID(), e.getMessage(), e);
             return Double.MAX_VALUE; // Return max value on calculation error
        }
    }

    /**
     * Generate a cache key for proximity alerts.
     *
     * @param gameId Game ID
     * @param recipientPlayerId Player receiving the alert
     * @param subjectPlayerId Player the alert is about (target/hunter)
     * @param alertType Type of alert ("target" or "hunter")
     * @return Cache key string
     */
    private String generateAlertCacheKey(String gameId, String recipientPlayerId, String subjectPlayerId, String alertType) {
        return gameId + ":" + recipientPlayerId + ":" + subjectPlayerId + ":" + alertType;
    }

    /**
     * Check if a specific alert type is currently on cooldown for the player.
     *
     * @param alertCacheKey The generated key for the alert.
     * @return true if the alert is on cooldown, false otherwise.
     */
    private boolean isAlertOnCooldown(String alertCacheKey) {
        Long lastAlertTimestamp = alertCache.get(alertCacheKey);
        if (lastAlertTimestamp == null) {
            return false; // Not on cooldown if never sent
        }
        return (System.currentTimeMillis() - lastAlertTimestamp) < ALERT_COOLDOWN_MS;
    }

    /**
     * Cleans up expired entries from the alert cache.
     */
    private void cleanupAlertCache() {
        long now = System.currentTimeMillis();
        alertCache.entrySet().removeIf(entry -> (now - entry.getValue()) > ALERT_COOLDOWN_MS);
        // Optionally log cache size after cleanup
        // logger.debug("Alert cache size after cleanup: {}", alertCache.size());
    }

    /**
     * Process proximity detection for large-scale games with many players.
     * Uses spatial partitioning to efficiently check proximity only between nearby players.
     * 
     * @param gameId ID of the game
     * @return A map of player IDs to lists of nearby players with their proximity status
     * @throws GameNotFoundException If the game is not found
     */
    public Map<String, List<ProximityResult>> processProximityForLargeGame(String gameId) throws GameNotFoundException {
        logger.info("Processing proximity detection for large game: {}", gameId);
        
        // Verify the game exists
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found during large-scale proximity processing: " + gameId));
        
        try {
            // Fetch all active players in the game (including PENDING_DEATH)
            List<Player> players = playerDao.getPlayersByGameId(gameId).stream()
                    .filter(p -> PlayerStatus.ACTIVE.name().equals(p.getStatus()) || 
                                PlayerStatus.PENDING_DEATH.name().equals(p.getStatus()))
                    .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                    .toList();
            
            logger.debug("Found {} active players with location data in game {}", players.size(), gameId);
            
            // Get game configuration
            MapConfiguration mapConfig = mapConfigService.getEffectiveMapConfiguration(gameId);
            double proximityDistance = (mapConfig.getProximityAwarenessDistanceMeters() != null) 
                    ? mapConfig.getProximityAwarenessDistanceMeters() 
                    : DEFAULT_ELIMINATION_DISTANCE;
            
            // Use spatial partitioning for optimization with large player counts
            Map<String, List<ProximityResult>> results = findPlayersInProximityOptimized(
                    players, proximityDistance + GPS_ACCURACY_BUFFER);
            
            logger.info("Processed proximity detection for {} players in game {}", players.size(), gameId);
            return results;
        } catch (Exception e) {
            logger.error("Error during large-scale proximity detection for game {}: {}", gameId, e.getMessage(), e);
            return Map.of(); // Return empty map on error
        }
    }
    
    /**
     * Find players in proximity to each other using an optimized grid-based spatial partitioning approach.
     * This method divides the map into grid cells and only checks proximity between players in the same or adjacent cells,
     * significantly reducing the number of comparisons needed for large player counts.
     * 
     * @param players List of all active players with location data
     * @param proximityThresholdMeters Maximum distance in meters to consider players in proximity
     * @return Map of player IDs to lists of proximity results with nearby players
     */
    private Map<String, List<ProximityResult>> findPlayersInProximityOptimized(
            List<Player> players, double proximityThresholdMeters) {
        
        // Calculate appropriate cell size based on proximity threshold
        // Using cell size slightly larger than proximity threshold ensures we only need to check adjacent cells
        double cellSizeMeters = proximityThresholdMeters * 1.2;
        
        // Create a grid to hold players
        // We use a sparse representation with keys as "x,y" grid coordinates
        Map<String, List<Player>> grid = new HashMap<>();
        
        // Track each player's grid cell for quick lookup
        Map<String, String> playerCells = new HashMap<>();
        
        // Place each player in the appropriate grid cell
        for (Player player : players) {
            // Convert lat/lon to approximate grid cell using rough approximation
            // ~111,000 meters per degree latitude, distance per degree longitude varies with latitude
            double metersPerDegreeLat = 111000;
            double metersPerDegreeLon = 111000 * Math.cos(Math.toRadians(player.getLatitude()));
            
            int cellX = (int) (player.getLongitude() * metersPerDegreeLon / cellSizeMeters);
            int cellY = (int) (player.getLatitude() * metersPerDegreeLat / cellSizeMeters);
            String cellKey = cellX + "," + cellY;
            
            // Add player to grid cell
            grid.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(player);
            playerCells.put(player.getPlayerID(), cellKey);
        }
        
        logger.debug("Spatial grid created with {} cells for {} players", grid.size(), players.size());
        
        // Results map to return
        Map<String, List<ProximityResult>> results = new HashMap<>();
        
        // For each player, check proximity with other players in the same and adjacent cells
        for (Player player : players) {
            String playerCell = playerCells.get(player.getPlayerID());
            int cellX = Integer.parseInt(playerCell.split(",")[0]);
            int cellY = Integer.parseInt(playerCell.split(",")[1]);
            
            List<ProximityResult> playerProximityResults = new ArrayList<>();
            Coordinate playerCoord = null;
            
            // Get player smoothed coordinate for distance calculation (lazy initialization)
            if (player.getLatitude() != null && player.getLongitude() != null) {
                playerCoord = locationHistoryManager.getSmoothedLocation(
                        player.getPlayerID(), player.getLatitude(), player.getLongitude());
            }
            
            // Skip this player if we couldn't get a valid coordinate
            if (playerCoord == null) continue;
            
            // Check adjacent cells (including the player's cell)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    String neighborCellKey = (cellX + dx) + "," + (cellY + dy);
                    List<Player> cellPlayers = grid.get(neighborCellKey);
                    
                    // Skip if no players in this cell
                    if (cellPlayers == null) continue;
                    
                    // Check each player in the cell
                    for (Player otherPlayer : cellPlayers) {
                        // Skip self-comparisons
                        if (otherPlayer.getPlayerID().equals(player.getPlayerID())) continue;
                        
                        // Skip if other player has no valid location
                        if (otherPlayer.getLatitude() == null || otherPlayer.getLongitude() == null) continue;
                        
                        // Get smoothed coordinates for the other player
                        Coordinate otherCoord = locationHistoryManager.getSmoothedLocation(
                                otherPlayer.getPlayerID(), otherPlayer.getLatitude(), otherPlayer.getLongitude());
                        
                        // Skip if couldn't get smoothed location
                        if (otherCoord == null) continue;
                        
                        // Calculate actual distance using smoothed coordinates
                        double distance = GeoUtils.calculateDistance(playerCoord, otherCoord);
                        
                        // Create proximity result if within threshold
                        if (distance <= proximityThresholdMeters) {
                            boolean inRange = distance <= proximityThresholdMeters;
                            ProximityResult proximityResult = new ProximityResult(
                                    player.getPlayerID(), otherPlayer.getPlayerID(), distance, inRange);
                            playerProximityResults.add(proximityResult);
                            
                            // Cache this result
                            String cacheKey = generateCacheKey(
                                    player.getGameID(), player.getPlayerID(), otherPlayer.getPlayerID());
                            proximityCache.put(cacheKey, proximityResult);
                        }
                    }
                }
            }
            
            // Add results to map if any found
            if (!playerProximityResults.isEmpty()) {
                results.put(player.getPlayerID(), playerProximityResults);
                int count = playerProximityResults.size();
                logger.debug("Player {} has {} players in proximity range ({}m)", 
                           player.getPlayerID(), count, proximityThresholdMeters);
            }
        }
        
        return results;
    }

    /**
     * Analyze and create a spatial heatmap of player activity and eliminations.
     * Useful for visualizing high-activity areas and planning game events.
     * 
     * @param gameId ID of the game
     * @param cellSizeMeters Size of each grid cell in meters
     * @return Grid cells with player and elimination density data
     * @throws GameNotFoundException If the game is not found
     */
    public Map<String, Map<String, Object>> generatePlayerActivityHeatmap(String gameId, double cellSizeMeters) 
            throws GameNotFoundException {
        
        logger.debug("Generating player activity heatmap for game {} with cell size {}m", gameId, cellSizeMeters);
        
        // Verify game exists
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found: " + gameId));
        
        try {
            // Get all active players
            List<Player> players = playerDao.getPlayersByGameId(gameId).stream()
                    .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                    .toList();
            
            // Grid to store player density data
            Map<String, Map<String, Object>> heatmapGrid = new HashMap<>();
            
            // Group players by grid cell
            for (Player player : players) {
                // Convert lat/lon to grid cell
                double metersPerDegreeLat = 111000;
                double metersPerDegreeLon = 111000 * Math.cos(Math.toRadians(player.getLatitude()));
                
                int cellX = (int) (player.getLongitude() * metersPerDegreeLon / cellSizeMeters);
                int cellY = (int) (player.getLatitude() * metersPerDegreeLat / cellSizeMeters);
                String cellKey = cellX + "," + cellY;
                
                // Initialize or update cell data
                Map<String, Object> cellData = heatmapGrid.computeIfAbsent(cellKey, k -> new HashMap<>());
                
                // Initialize the cell if it's new
                if (cellData.isEmpty()) {
                    cellData.put("playerCount", 0);
                    cellData.put("centerLat", 0.0);
                    cellData.put("centerLon", 0.0);
                    cellData.put("playerIDs", new ArrayList<String>());
                    cellData.put("lastUpdated", System.currentTimeMillis());
                }
                
                // Update player count and list
                int currentCount = (int) cellData.get("playerCount");
                cellData.put("playerCount", currentCount + 1);
                
                // Add player ID to the cell's list
                @SuppressWarnings("unchecked")
                List<String> playerIDs = (List<String>) cellData.get("playerIDs");
                playerIDs.add(player.getPlayerID());
                
                // Update cell center (average of all players in cell)
                double currentLat = (double) cellData.get("centerLat");
                double currentLon = (double) cellData.get("centerLon");
                
                if (currentCount == 0) {
                    cellData.put("centerLat", player.getLatitude());
                    cellData.put("centerLon", player.getLongitude());
                } else {
                    cellData.put("centerLat", (currentLat * currentCount + player.getLatitude()) / (currentCount + 1));
                    cellData.put("centerLon", (currentLon * currentCount + player.getLongitude()) / (currentCount + 1));
                }
                
                cellData.put("lastUpdated", System.currentTimeMillis());
            }
            
            logger.debug("Generated heatmap with {} occupied cells for game {}", heatmapGrid.size(), gameId);
            return heatmapGrid;
            
        } catch (Exception e) {
            logger.error("Error generating player activity heatmap for game {}: {}", gameId, e.getMessage(), e);
            return Map.of();
        }
    }

    /**
     * Batch process proximity notifications based on recent proximity data.
     * More efficient than sending individual notifications for large games.
     * 
     * @param gameId ID of the game to process notifications for
     * @param proximityResults Map of players and their proximity results
     * @param notificationType Type of notification to send (standard or premium)
     * @throws GameNotFoundException If game cannot be found
     */
    public void processBatchProximityNotifications(
            String gameId, 
            Map<String, List<ProximityResult>> proximityResults, 
            String notificationType) throws GameNotFoundException {
        
        logger.debug("Processing batch proximity notifications for game {}, type: {}", gameId, notificationType);
        
        // Verify the game exists
        Game game = gameDao.getGameById(gameId)
                .orElseThrow(() -> new GameNotFoundException("Game not found for batch notifications: " + gameId));
        
        // Get map configuration for proximity settings
        MapConfiguration mapConfig = mapConfigService.getEffectiveMapConfiguration(gameId);
        double alertDistance = (mapConfig.getProximityAwarenessDistanceMeters() != null) 
                ? mapConfig.getProximityAwarenessDistanceMeters() 
                : DEFAULT_ELIMINATION_DISTANCE * 2; // Default to double elimination distance
        
        List<Notification> batchNotifications = new ArrayList<>();
        int targetAlertCount = 0;
        int hunterAlertCount = 0;
        
        try {
            // Process each player and their proximity results
            for (Map.Entry<String, List<ProximityResult>> entry : proximityResults.entrySet()) {
                String playerId = entry.getKey();
                List<ProximityResult> playerResults = entry.getValue();
                
                Player player = playerDao.getPlayerById(playerId)
                        .orElse(null);
                
                if (player == null || (!PlayerStatus.ACTIVE.name().equals(player.getStatus()) && 
                                      !PlayerStatus.PENDING_DEATH.name().equals(player.getStatus()))) {
                    continue; // Skip inactive or not found players
                }
                
                // Check for target proximity
                if (player.getTargetID() != null) {
                    for (ProximityResult result : playerResults) {
                        // Check if this result is for the player's target
                        if (result.getPlayer2Id().equals(player.getTargetID()) && result.getDistance() <= alertDistance) {
                            // Check for notification cooldown
                            String alertCacheKey = generateAlertCacheKey(
                                    gameId, playerId, player.getTargetID(), "TARGET_PROXIMITY");
                            
                            if (!isAlertOnCooldown(alertCacheKey)) {
                                // Prepare a target proximity notification
                                Player targetPlayer = playerDao.getPlayerById(player.getTargetID()).orElse(null);
                                if (targetPlayer != null) {
                                    Notification notification = createProximityNotification(
                                            gameId, playerId, targetPlayer, result.getDistance(), "TARGET", notificationType);
                                    
                                    batchNotifications.add(notification);
                                    targetAlertCount++;
                                    
                                    // Add to alert cache to prevent repeated notifications
                                    alertCache.put(alertCacheKey, System.currentTimeMillis());
                                }
                            }
                        }
                    }
                }
                
                // Check for hunter proximity
                List<Player> hunters = playerDao.getPlayersTargeting(playerId, gameId);
                for (Player hunter : hunters) {
                    for (ProximityResult result : playerResults) {
                        // Check if this result is for a hunter
                        if (result.getPlayer2Id().equals(hunter.getPlayerID()) && result.getDistance() <= alertDistance) {
                            // Check for notification cooldown
                            String alertCacheKey = generateAlertCacheKey(
                                    gameId, playerId, hunter.getPlayerID(), "HUNTER_PROXIMITY");
                            
                            if (!isAlertOnCooldown(alertCacheKey)) {
                                // Prepare a hunter proximity notification
                                Notification notification = createProximityNotification(
                                        gameId, playerId, hunter, result.getDistance(), "HUNTER", notificationType);
                                
                                batchNotifications.add(notification);
                                hunterAlertCount++;
                                
                                // Add to alert cache to prevent repeated notifications
                                alertCache.put(alertCacheKey, System.currentTimeMillis());
                            }
                        }
                    }
                }
            }
            
            // Send all notifications in batch
            if (!batchNotifications.isEmpty()) {
                for (Notification notification : batchNotifications) {
                    notificationService.sendNotification(notification);
                }
                
                logger.info("Sent {} batch proximity notifications for game {} ({} target alerts, {} hunter alerts)", 
                        batchNotifications.size(), gameId, targetAlertCount, hunterAlertCount);
            } else {
                logger.debug("No proximity notifications to send for game {}", gameId);
            }
            
        } catch (Exception e) {
            logger.error("Error processing batch proximity notifications for game {}: {}", 
                    gameId, e.getMessage(), e);
        }
    }
    
    /**
     * Create a proximity notification with appropriate content based on the notification type.
     * 
     * @param gameId ID of the game
     * @param recipientPlayerId ID of the player receiving the notification
     * @param subjectPlayer The player (target or hunter) that triggered the notification
     * @param distanceMeters Distance in meters between the players
     * @param subjectType Type of subject ("TARGET" or "HUNTER")
     * @param notificationType Type of notification ("STANDARD" or "PREMIUM")
     * @return Configured notification object ready to send
     */
    private Notification createProximityNotification(
            String gameId, 
            String recipientPlayerId, 
            Player subjectPlayer, 
            double distanceMeters,
            String subjectType,
            String notificationType) {
        
        boolean isPremium = "PREMIUM".equalsIgnoreCase(notificationType);
        
        // Format distance for display (round to nearest meter)
        int roundedDistance = (int) Math.round(distanceMeters);
        
        String title;
        String message;
        
        if ("TARGET".equals(subjectType)) {
            title = "Target Nearby";
            
            if (isPremium) {
                // Premium notifications include exact distance and direction
                String playerName = subjectPlayer.getPlayerName() != null ? 
                        subjectPlayer.getPlayerName() : "Your target";
                
                message = String.format("%s is approximately %d meters away from your position!", 
                        playerName, roundedDistance);
            } else {
                // Standard notifications are more generic
                message = "Your target is nearby! Stay alert and prepare for an elimination opportunity.";
            }
        } else { // HUNTER
            title = "Hunter Nearby";
            
            if (isPremium) {
                // Premium notifications include exact distance and hunter name
                String hunterName = subjectPlayer.getPlayerName() != null ? 
                        subjectPlayer.getPlayerName() : "A hunter";
                
                message = String.format("%s is on your trail and only %d meters away! Take evasive action immediately!", 
                        hunterName, roundedDistance);
            } else {
                // Standard notifications are more generic
                message = "A hunter is nearby! Be cautious and consider moving to a new location.";
            }
        }
        
        Notification notification = new Notification();
        notification.setGameId(gameId);
        notification.setRecipientPlayerId(recipientPlayerId);
        notification.setType(NotificationType.PROXIMITY_ALERT.name());
        notification.setTitle(title);
        notification.setMessage(message);
        
        // Convert timestamp to ISO-8601 string format which is expected by Notification
        notification.setTimestamp(Instant.now().toString());
        
        // Add proximity data to the message body instead of using non-existent additionalData field
        // In a real app, you might modify the Notification class to support additional data
        StringBuilder enhancedMessage = new StringBuilder(message);
        if (isPremium) {
            enhancedMessage.append(" [Distance: ").append(roundedDistance).append("m]");
            
            // For a real implementation, additional data would be included in the notification
            // via a proper data structure in the Notification class or a serialized JSON string
            // in an existing field
            
            // This comment documents what we would have included in the additional data
            /*
            Map<String, Object> data = new HashMap<>();
            data.put("subjectType", subjectType);
            data.put("subjectId", subjectPlayer.getPlayerID());
            data.put("distance", roundedDistance);
            data.put("isPremium", isPremium);
            
            // Only include approximate location for premium notifications
            if (isPremium) {
                data.put("approximateLocationEnabled", true);
                
                // Deliberately add some fuzzing to the location for security
                double fuzzFactor = 0.0001; // Roughly 10m of randomization
                Random random = new Random();
                
                double fuzzedLat = subjectPlayer.getLatitude() + (random.nextDouble() * 2 - 1) * fuzzFactor;
                double fuzzedLon = subjectPlayer.getLongitude() + (random.nextDouble() * 2 - 1) * fuzzFactor;
                
                data.put("approximateLatitude", fuzzedLat);
                data.put("approximateLongitude", fuzzedLon);
            } else {
                data.put("approximateLocationEnabled", false);
            }
            */
        }
        
        notification.setMessage(enhancedMessage.toString());
        
        return notification;
    }
} 