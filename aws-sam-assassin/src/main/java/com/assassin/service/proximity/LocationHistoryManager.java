package com.assassin.service.proximity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages location history for players to enable smoothing and jitter reduction.
 * Supports multiple smoothing algorithms for different use cases.
 */
public class LocationHistoryManager {
    private static final Logger logger = LoggerFactory.getLogger(LocationHistoryManager.class);
    
    // Default configuration
    private static final int DEFAULT_HISTORY_SIZE = 3;
    private static final long MAX_LOCATION_AGE_MS = 300000; // 5 minutes
    
    // Storage for location history
    private final Map<String, List<LocationEntry>> playerLocationHistory;
    private final int historySize;
    
    // Default smoothing algorithm
    private SmoothingAlgorithm defaultAlgorithm = SmoothingAlgorithm.LINEAR_WEIGHTED;
    
    public LocationHistoryManager() {
        this(DEFAULT_HISTORY_SIZE);
    }
    
    public LocationHistoryManager(int historySize) {
        this.historySize = historySize;
        this.playerLocationHistory = new ConcurrentHashMap<>();
    }
    
    /**
     * Represents a single location entry in the history.
     */
    public static class LocationEntry {
        private final double latitude;
        private final double longitude;
        private final long timestamp;
        private final Double accuracy; // GPS accuracy in meters (optional)
        
        public LocationEntry(double latitude, double longitude, long timestamp, Double accuracy) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
            this.accuracy = accuracy;
        }
        
        // Getters
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public long getTimestamp() { return timestamp; }
        public Double getAccuracy() { return accuracy; }
        
        public boolean isExpired(long maxAgeMs) {
            return System.currentTimeMillis() - timestamp > maxAgeMs;
        }
    }
    
    /**
     * Represents a smoothed location result.
     */
    public static class SmoothedLocation {
        private final double latitude;
        private final double longitude;
        private final double confidence; // 0.0 to 1.0
        private final int sampleCount;
        
        public SmoothedLocation(double latitude, double longitude, double confidence, int sampleCount) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.confidence = confidence;
            this.sampleCount = sampleCount;
        }
        
        // Getters
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public double getConfidence() { return confidence; }
        public int getSampleCount() { return sampleCount; }
    }
    
    /**
     * Smoothing algorithms for location data.
     */
    public enum SmoothingAlgorithm {
        LINEAR_WEIGHTED,      // Weight decreases linearly with age
        EXPONENTIAL_DECAY,    // Weight decreases exponentially with age
        SIMPLE_AVERAGE,       // Equal weights for all points
        PREDICTIVE           // Extrapolates based on velocity
    }
    
    /**
     * Adds a new location to a player's history.
     * 
     * @param playerId Player ID
     * @param latitude Latitude
     * @param longitude Longitude
     * @param accuracy GPS accuracy in meters (optional)
     */
    public void addLocation(String playerId, double latitude, double longitude, Double accuracy) {
        List<LocationEntry> history = playerLocationHistory.computeIfAbsent(
            playerId, k -> new ArrayList<>()
        );
        
        synchronized (history) {
            // Add new entry
            LocationEntry newEntry = new LocationEntry(
                latitude, longitude, System.currentTimeMillis(), accuracy
            );
            history.add(newEntry);
            
            // Remove old entries to maintain size limit
            while (history.size() > historySize) {
                history.remove(0);
            }
            
            // Remove expired entries
            history.removeIf(entry -> entry.isExpired(MAX_LOCATION_AGE_MS));
        }
        
        logger.debug("Added location for player {}: ({}, {}), history size: {}", 
            playerId, latitude, longitude, history.size());
    }
    
    /**
     * Gets the smoothed location for a player using the default algorithm.
     * 
     * @param playerId Player ID
     * @return Smoothed location or null if insufficient data
     */
    public SmoothedLocation getSmoothedLocation(String playerId) {
        return getSmoothedLocation(playerId, defaultAlgorithm);
    }
    
    /**
     * Gets the smoothed location for a player using a specific algorithm.
     * 
     * @param playerId Player ID
     * @param algorithm Smoothing algorithm to use
     * @return Smoothed location or null if insufficient data
     */
    public SmoothedLocation getSmoothedLocation(String playerId, SmoothingAlgorithm algorithm) {
        List<LocationEntry> history = playerLocationHistory.get(playerId);
        if (history == null || history.isEmpty()) {
            return null;
        }
        
        List<LocationEntry> validEntries;
        synchronized (history) {
            // Filter out expired entries
            validEntries = history.stream()
                .filter(entry -> !entry.isExpired(MAX_LOCATION_AGE_MS))
                .collect(Collectors.toList());
        }
        
        if (validEntries.isEmpty()) {
            return null;
        }
        
        switch (algorithm) {
            case LINEAR_WEIGHTED:
                return applyLinearWeightedSmoothing(validEntries);
            case EXPONENTIAL_DECAY:
                return applyExponentialDecaySmoothing(validEntries);
            case SIMPLE_AVERAGE:
                return applySimpleAverageSmoothing(validEntries);
            case PREDICTIVE:
                return applyPredictiveSmoothing(validEntries);
            default:
                return applyLinearWeightedSmoothing(validEntries);
        }
    }
    
    /**
     * Applies linear weighted smoothing where newer points have more weight.
     */
    private SmoothedLocation applyLinearWeightedSmoothing(List<LocationEntry> entries) {
        if (entries.isEmpty()) {
            return null;
        }
        
        double totalWeight = 0;
        double weightedLat = 0;
        double weightedLon = 0;
        
        long now = System.currentTimeMillis();
        long oldestTime = entries.stream()
            .mapToLong(LocationEntry::getTimestamp)
            .min()
            .orElse(now);
        
        for (LocationEntry entry : entries) {
            // Weight based on age (newer = higher weight)
            double ageRatio = (double)(entry.getTimestamp() - oldestTime) / 
                            (now - oldestTime + 1);
            double weight = 0.3 + 0.7 * ageRatio; // Weight range: 0.3 to 1.0
            
            // Adjust weight based on accuracy if available
            if (entry.getAccuracy() != null) {
                weight *= Math.max(0.5, 1.0 - entry.getAccuracy() / 100.0);
            }
            
            weightedLat += entry.getLatitude() * weight;
            weightedLon += entry.getLongitude() * weight;
            totalWeight += weight;
        }
        
        double smoothedLat = weightedLat / totalWeight;
        double smoothedLon = weightedLon / totalWeight;
        double confidence = Math.min(1.0, entries.size() / (double)historySize);
        
        return new SmoothedLocation(smoothedLat, smoothedLon, confidence, entries.size());
    }
    
    /**
     * Applies exponential decay smoothing.
     */
    private SmoothedLocation applyExponentialDecaySmoothing(List<LocationEntry> entries) {
        if (entries.isEmpty()) {
            return null;
        }
        
        double alpha = 0.3; // Smoothing factor
        double totalWeight = 0;
        double weightedLat = 0;
        double weightedLon = 0;
        
        long now = System.currentTimeMillis();
        
        for (LocationEntry entry : entries) {
            // Exponential decay based on age
            double ageSeconds = (now - entry.getTimestamp()) / 1000.0;
            double weight = Math.exp(-alpha * ageSeconds);
            
            weightedLat += entry.getLatitude() * weight;
            weightedLon += entry.getLongitude() * weight;
            totalWeight += weight;
        }
        
        double smoothedLat = weightedLat / totalWeight;
        double smoothedLon = weightedLon / totalWeight;
        double confidence = Math.min(1.0, entries.size() / (double)historySize);
        
        return new SmoothedLocation(smoothedLat, smoothedLon, confidence, entries.size());
    }
    
    /**
     * Applies simple average smoothing (equal weights).
     */
    private SmoothedLocation applySimpleAverageSmoothing(List<LocationEntry> entries) {
        if (entries.isEmpty()) {
            return null;
        }
        
        double avgLat = entries.stream()
            .mapToDouble(LocationEntry::getLatitude)
            .average()
            .orElse(0);
        
        double avgLon = entries.stream()
            .mapToDouble(LocationEntry::getLongitude)
            .average()
            .orElse(0);
        
        double confidence = Math.min(1.0, entries.size() / (double)historySize);
        
        return new SmoothedLocation(avgLat, avgLon, confidence, entries.size());
    }
    
    /**
     * Applies predictive smoothing based on velocity.
     */
    private SmoothedLocation applyPredictiveSmoothing(List<LocationEntry> entries) {
        if (entries.size() < 2) {
            // Not enough data for prediction, use latest location
            LocationEntry latest = entries.get(entries.size() - 1);
            return new SmoothedLocation(
                latest.getLatitude(), 
                latest.getLongitude(), 
                0.5, 
                entries.size()
            );
        }
        
        // Sort by timestamp
        entries.sort(Comparator.comparingLong(LocationEntry::getTimestamp));
        
        // Calculate velocity from last two points
        LocationEntry prev = entries.get(entries.size() - 2);
        LocationEntry latest = entries.get(entries.size() - 1);
        
        double timeDelta = (latest.getTimestamp() - prev.getTimestamp()) / 1000.0; // seconds
        if (timeDelta <= 0) {
            return new SmoothedLocation(
                latest.getLatitude(), 
                latest.getLongitude(), 
                0.7, 
                entries.size()
            );
        }
        
        // Calculate velocity components
        double latVelocity = (latest.getLatitude() - prev.getLatitude()) / timeDelta;
        double lonVelocity = (latest.getLongitude() - prev.getLongitude()) / timeDelta;
        
        // Predict current position
        double timeSinceLatest = (System.currentTimeMillis() - latest.getTimestamp()) / 1000.0;
        double predictedLat = latest.getLatitude() + latVelocity * timeSinceLatest;
        double predictedLon = latest.getLongitude() + lonVelocity * timeSinceLatest;
        
        // Confidence decreases with prediction time
        double confidence = Math.max(0.3, 1.0 - timeSinceLatest / 10.0);
        
        return new SmoothedLocation(predictedLat, predictedLon, confidence, entries.size());
    }
    
    /**
     * Cleans old entries from all player histories.
     */
    public void cleanOldEntries() {
        int totalRemoved = 0;
        
        for (Map.Entry<String, List<LocationEntry>> entry : playerLocationHistory.entrySet()) {
            List<LocationEntry> history = entry.getValue();
            synchronized (history) {
                int sizeBefore = history.size();
                history.removeIf(loc -> loc.isExpired(MAX_LOCATION_AGE_MS));
                totalRemoved += sizeBefore - history.size();
            }
        }
        
        // Remove players with no history
        playerLocationHistory.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        
        if (totalRemoved > 0) {
            logger.info("Cleaned {} old location entries", totalRemoved);
        }
    }
    
    /**
     * Gets the raw location history for a player.
     * 
     * @param playerId Player ID
     * @return List of location entries or empty list
     */
    public List<LocationEntry> getLocationHistory(String playerId) {
        List<LocationEntry> history = playerLocationHistory.get(playerId);
        if (history == null) {
            return Collections.emptyList();
        }
        
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }
    
    /**
     * Sets the default smoothing algorithm.
     * 
     * @param algorithm Smoothing algorithm
     */
    public void setDefaultAlgorithm(SmoothingAlgorithm algorithm) {
        this.defaultAlgorithm = algorithm;
    }
}