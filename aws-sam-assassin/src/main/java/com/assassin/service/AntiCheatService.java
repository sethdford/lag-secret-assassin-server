package com.assassin.service;

import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.DynamoDbSecurityEventDao;
import com.assassin.dao.PlayerDao;
import com.assassin.dao.SecurityEventDao;
import com.assassin.exception.AntiCheatViolationException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.model.Coordinate;
import com.assassin.model.Player;
import com.assassin.model.SecurityEvent;
import com.assassin.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive anti-cheat system for detecting and preventing various forms of cheating
 * in the Assassin Game. Includes location spoofing detection, speed validation, 
 * device fingerprinting, behavioral analysis, and automated response mechanisms.
 */
public class AntiCheatService {
    
    private static final Logger logger = LoggerFactory.getLogger(AntiCheatService.class);
    
    private final PlayerDao playerDao;
    private final SecurityEventDao securityEventDao;
    private final SecurityService securityService;
    private final SecurityMetricsPublisher metricsPublisher;
    
    // In-memory caches for performance (in production, consider Redis)
    private final Map<String, List<LocationCheck>> playerLocationHistory = new ConcurrentHashMap<>();
    private final Map<String, DeviceFingerprint> playerDeviceFingerprints = new ConcurrentHashMap<>();
    private final Map<String, BehaviorProfile> playerBehaviorProfiles = new ConcurrentHashMap<>();
    private final Map<String, Instant> playerLastValidationTime = new ConcurrentHashMap<>();
    
    // Anti-cheat configuration constants
    private static final double MAX_REALISTIC_SPEED_MPS = 83.33; // 300 km/h (airplane speed)
    private static final double SUSPICIOUS_SPEED_MPS = 55.56; // 200 km/h (very fast car)
    private static final double WALKING_SPEED_MPS = 2.0; // 7.2 km/h (fast walking)
    private static final double RUNNING_SPEED_MPS = 6.0; // 21.6 km/h (fast running)
    
    private static final int MAX_LOCATION_HISTORY = 20;
    private static final int MIN_TIME_BETWEEN_UPDATES_MS = 1000; // 1 second
    private static final int MAX_DEVICE_SWITCHES_PER_HOUR = 3;
    private static final double MAX_GPS_ACCURACY_METERS = 100.0;
    private static final double SUSPICIOUS_GPS_ACCURACY_METERS = 5.0; // Too accurate might be spoofed
    
    // Behavioral analysis thresholds
    private static final int SUSPICIOUS_KILL_RATE_PER_HOUR = 10;
    private static final double MIN_HUNT_TIME_MINUTES = 2.0; // Minimum realistic time to find and eliminate target
    private static final int MAX_CONSECUTIVE_PERFECT_KILLS = 5; // Perfect GPS proximity kills
    
    public AntiCheatService() {
        this.playerDao = new DynamoDbPlayerDao();
        this.securityEventDao = new DynamoDbSecurityEventDao();
        this.securityService = new SecurityService();
        this.metricsPublisher = new SecurityMetricsPublisher();
    }
    
    public AntiCheatService(PlayerDao playerDao, SecurityEventDao securityEventDao, 
                           SecurityService securityService) {
        this.playerDao = playerDao;
        this.securityEventDao = securityEventDao;
        this.securityService = securityService;
        this.metricsPublisher = new SecurityMetricsPublisher();
    }
    
    /**
     * Validates a location update for potential cheating indicators
     */
    public LocationValidationResult validateLocationUpdate(String playerId, Coordinate newLocation, 
                                                          String deviceFingerprint, Map<String, Object> deviceMetadata) {
        logger.debug("Validating location update for player: {} at coordinates: {}", playerId, newLocation);
        
        LocationValidationResult result = new LocationValidationResult();
        result.setValid(true);
        result.setPlayerId(playerId);
        result.setLocation(newLocation);
        result.setValidationTime(Instant.now());
        
        try {
            // 1. Speed validation
            validateMovementSpeed(playerId, newLocation, result);
            
            // 2. Device fingerprinting
            validateDeviceFingerprint(playerId, deviceFingerprint, deviceMetadata, result);
            
            // 3. GPS accuracy analysis
            validateGpsAccuracy(newLocation, result);
            
            // 4. Temporal validation
            validateUpdateTiming(playerId, result);
            
            // 5. Location pattern analysis
            validateLocationPatterns(playerId, newLocation, result);
            
            // 6. Geospatial anomaly detection
            validateGeospatialAnomalies(playerId, newLocation, result);
            
            // Update location history if validation passed
            if (result.isValid()) {
                updateLocationHistory(playerId, newLocation);
            }
            
            // Log security events for suspicious activity
            if (!result.getViolations().isEmpty()) {
                logSecurityEvents(playerId, result);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error validating location for player {}: {}", playerId, e.getMessage(), e);
            result.setValid(false);
            result.addViolation(CheatType.SYSTEM_ERROR, "Validation system error", 10);
            return result;
        }
    }
    
    /**
     * Validates kill report for cheating indicators
     */
    public KillValidationResult validateKillReport(String killerPlayerId, String targetPlayerId, 
                                                   Coordinate killLocation, String verificationType,
                                                   Map<String, Object> verificationData) {
        logger.debug("Validating kill report: {} -> {} at {}", killerPlayerId, targetPlayerId, killLocation);
        
        KillValidationResult result = new KillValidationResult();
        result.setValid(true);
        result.setKillerPlayerId(killerPlayerId);
        result.setTargetPlayerId(targetPlayerId);
        result.setKillLocation(killLocation);
        result.setVerificationType(verificationType);
        result.setValidationTime(Instant.now());
        
        try {
            // 1. Validate killer behavior patterns
            validateKillBehaviorPatterns(killerPlayerId, result);
            
            // 2. Validate proximity requirements
            validateKillProximity(killerPlayerId, targetPlayerId, killLocation, result);
            
            // 3. Validate verification method authenticity
            validateVerificationMethod(verificationType, verificationData, result);
            
            // 4. Validate kill timing patterns
            validateKillTiming(killerPlayerId, result);
            
            // 5. Cross-reference with location history
            validateKillLocationConsistency(killerPlayerId, killLocation, result);
            
            // Update behavior profile
            if (result.isValid()) {
                updateBehaviorProfile(killerPlayerId, "KILL_SUCCESSFUL");
            }
            
            // Log suspicious kill patterns
            if (!result.getViolations().isEmpty()) {
                logKillSecurityEvents(killerPlayerId, targetPlayerId, result);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error validating kill for players {} -> {}: {}", 
                        killerPlayerId, targetPlayerId, e.getMessage(), e);
            result.setValid(false);
            result.addViolation(CheatType.SYSTEM_ERROR, "Kill validation system error", 10);
            return result;
        }
    }
    
    /**
     * Validates movement speed between location updates
     */
    private void validateMovementSpeed(String playerId, Coordinate newLocation, LocationValidationResult result) {
        List<LocationCheck> history = playerLocationHistory.get(playerId);
        if (history == null || history.isEmpty()) {
            return; // First location update
        }
        
        LocationCheck lastLocation = history.get(history.size() - 1);
        long timeDiffMs = ChronoUnit.MILLIS.between(lastLocation.getTimestamp(), Instant.now());
        
        if (timeDiffMs <= 0) {
            result.addViolation(CheatType.TEMPORAL_ANOMALY, "Invalid timestamp sequence", 8);
            return;
        }
        
        double distance = GeoUtils.calculateDistance(lastLocation.getLocation(), newLocation);
        double speed = distance / (timeDiffMs / 1000.0); // meters per second
        
        if (speed > MAX_REALISTIC_SPEED_MPS) {
            result.addViolation(CheatType.IMPOSSIBLE_SPEED, 
                String.format("Speed %.2f m/s exceeds maximum realistic speed", speed), 10);
            result.setValid(false);
        } else if (speed > SUSPICIOUS_SPEED_MPS) {
            result.addViolation(CheatType.SUSPICIOUS_SPEED, 
                String.format("Speed %.2f m/s is unusually fast", speed), 6);
        }
        
        // Additional context-based speed validation
        validateContextualSpeed(speed, timeDiffMs, result);
    }
    
    /**
     * Validates device fingerprint consistency
     */
    private void validateDeviceFingerprint(String playerId, String deviceFingerprint, 
                                         Map<String, Object> deviceMetadata, LocationValidationResult result) {
        DeviceFingerprint existingFingerprint = playerDeviceFingerprints.get(playerId);
        
        if (existingFingerprint == null) {
            // First time seeing this player, store fingerprint
            DeviceFingerprint newFingerprint = new DeviceFingerprint(deviceFingerprint, deviceMetadata);
            playerDeviceFingerprints.put(playerId, newFingerprint);
            return;
        }
        
        // Check for device switching patterns
        if (!existingFingerprint.getFingerprint().equals(deviceFingerprint)) {
            long hoursSinceLastSwitch = ChronoUnit.HOURS.between(
                existingFingerprint.getLastSeen(), Instant.now());
            
            existingFingerprint.incrementSwitchCount();
            
            if (existingFingerprint.getSwitchCount() > MAX_DEVICE_SWITCHES_PER_HOUR && hoursSinceLastSwitch < 1) {
                result.addViolation(CheatType.DEVICE_SWITCHING, 
                    "Too many device switches in short period", 7);
            }
            
            // Update to new device
            existingFingerprint.setFingerprint(deviceFingerprint);
            existingFingerprint.setMetadata(deviceMetadata);
        }
        
        existingFingerprint.setLastSeen(Instant.now());
    }
    
    /**
     * Validates GPS accuracy for spoofing indicators
     */
    private void validateGpsAccuracy(Coordinate location, LocationValidationResult result) {
        Double accuracy = location.getAccuracy();
        if (accuracy == null) {
            result.addViolation(CheatType.MISSING_GPS_DATA, "Missing GPS accuracy data", 3);
            return;
        }
        
        if (accuracy > MAX_GPS_ACCURACY_METERS) {
            result.addViolation(CheatType.POOR_GPS_ACCURACY, 
                String.format("GPS accuracy %.1fm is too poor", accuracy), 4);
        } else if (accuracy < SUSPICIOUS_GPS_ACCURACY_METERS) {
            result.addViolation(CheatType.SUSPICIOUS_GPS_ACCURACY, 
                String.format("GPS accuracy %.1fm is suspiciously precise", accuracy), 5);
        }
    }
    
    /**
     * Validates timing between location updates
     */
    private void validateUpdateTiming(String playerId, LocationValidationResult result) {
        Instant lastValidation = playerLastValidationTime.get(playerId);
        Instant now = Instant.now();
        
        if (lastValidation != null) {
            long timeSinceLastMs = ChronoUnit.MILLIS.between(lastValidation, now);
            
            if (timeSinceLastMs < MIN_TIME_BETWEEN_UPDATES_MS) {
                result.addViolation(CheatType.HIGH_FREQUENCY_UPDATES, 
                    "Location updates too frequent", 6);
            }
        }
        
        playerLastValidationTime.put(playerId, now);
    }
    
    /**
     * Analyzes location patterns for anomalies
     */
    private void validateLocationPatterns(String playerId, Coordinate newLocation, LocationValidationResult result) {
        List<LocationCheck> history = playerLocationHistory.get(playerId);
        if (history == null || history.size() < 3) {
            return; // Need at least 3 points for pattern analysis
        }
        
        // Check for teleportation patterns
        detectTeleportation(history, newLocation, result);
        
        // Check for repetitive patterns
        detectRepetitivePatterns(history, newLocation, result);
        
        // Check for impossible turns/stops
        detectImpossibleManeuvers(history, newLocation, result);
    }
    
    /**
     * Detects sudden location jumps (teleportation)
     */
    private void detectTeleportation(List<LocationCheck> history, Coordinate newLocation, 
                                   LocationValidationResult result) {
        if (history.size() < 2) return;
        
        LocationCheck previous = history.get(history.size() - 1);
        LocationCheck beforePrevious = history.get(history.size() - 2);
        
        double dist1 = GeoUtils.calculateDistance(beforePrevious.getLocation(), previous.getLocation());
        double dist2 = GeoUtils.calculateDistance(previous.getLocation(), newLocation);
        
        // If current movement is dramatically different from previous movement
        if (dist2 > dist1 * 10 && dist2 > 1000) { // 10x increase and >1km
            result.addViolation(CheatType.TELEPORTATION, 
                String.format("Sudden jump of %.0fm detected", dist2), 9);
        }
    }
    
    /**
     * Detects repetitive location patterns
     */
    private void detectRepetitivePatterns(List<LocationCheck> history, Coordinate newLocation, 
                                        LocationValidationResult result) {
        // Check if new location exactly matches a recent location (copy-paste cheating)
        for (int i = Math.max(0, history.size() - 5); i < history.size(); i++) {
            Coordinate historicLocation = history.get(i).getLocation();
            if (GeoUtils.calculateDistance(historicLocation, newLocation) < 1.0) { // Within 1 meter
                result.addViolation(CheatType.REPETITIVE_PATTERN, 
                    "Exact location repetition detected", 7);
                break;
            }
        }
    }
    
    /**
     * Detects impossible maneuvers (sharp turns at high speed)
     */
    private void detectImpossibleManeuvers(List<LocationCheck> history, Coordinate newLocation, 
                                         LocationValidationResult result) {
        if (history.size() < 2) return;
        
        LocationCheck p1 = history.get(history.size() - 2);
        LocationCheck p2 = history.get(history.size() - 1);
        
        // Calculate bearing change
        double bearing1 = GeoUtils.calculateBearing(
            p1.getLocation().getLatitude(), p1.getLocation().getLongitude(),
            p2.getLocation().getLatitude(), p2.getLocation().getLongitude());
        double bearing2 = GeoUtils.calculateBearing(
            p2.getLocation().getLatitude(), p2.getLocation().getLongitude(),
            newLocation.getLatitude(), newLocation.getLongitude());
        double bearingChange = Math.abs(bearing2 - bearing1);
        if (bearingChange > 180) bearingChange = 360 - bearingChange;
        
        // Calculate speed
        long timeDiff = ChronoUnit.MILLIS.between(p2.getTimestamp(), Instant.now());
        double distance = GeoUtils.calculateDistance(p2.getLocation(), newLocation);
        double speed = distance / (timeDiff / 1000.0);
        
        // Sharp turn at high speed is suspicious
        if (bearingChange > 90 && speed > RUNNING_SPEED_MPS) {
            result.addViolation(CheatType.IMPOSSIBLE_MANEUVER, 
                String.format("Sharp turn (%.0f°) at high speed (%.1f m/s)", bearingChange, speed), 6);
        }
    }
    
    /**
     * Validates geospatial anomalies
     */
    private void validateGeospatialAnomalies(String playerId, Coordinate newLocation, 
                                           LocationValidationResult result) {
        // Check for impossible locations (middle of ocean, restricted areas, etc.)
        if (isImpossibleLocation(newLocation)) {
            result.addViolation(CheatType.IMPOSSIBLE_LOCATION, 
                "Location in impossible area", 8);
        }
        
        // Check altitude changes (if available)
        validateAltitudeChanges(playerId, newLocation, result);
    }
    
    /**
     * Checks if location is in an impossible area
     */
    private boolean isImpossibleLocation(Coordinate location) {
        // Basic checks for obviously fake locations
        // This could be expanded with more sophisticated geospatial databases
        
        // Check for null island (0,0)
        if (Math.abs(location.getLatitude()) < 0.1 && Math.abs(location.getLongitude()) < 0.1) {
            return true;
        }
        
        // Check for locations outside valid GPS ranges
        if (Math.abs(location.getLatitude()) > 90 || Math.abs(location.getLongitude()) > 180) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Validates altitude changes for reasonableness
     */
    private void validateAltitudeChanges(String playerId, Coordinate newLocation, 
                                       LocationValidationResult result) {
        List<LocationCheck> history = playerLocationHistory.get(playerId);
        if (history == null || history.isEmpty()) return;
        
        LocationCheck lastLocation = history.get(history.size() - 1);
        Double lastAltitude = lastLocation.getLocation().getAltitude();
        Double newAltitude = newLocation.getAltitude();
        
        if (lastAltitude != null && newAltitude != null) {
            double altitudeChange = Math.abs(newAltitude - lastAltitude);
            long timeDiff = ChronoUnit.MILLIS.between(lastLocation.getTimestamp(), Instant.now());
            
            // Vertical speed in m/s
            double verticalSpeed = altitudeChange / (timeDiff / 1000.0);
            
            // Maximum reasonable vertical speed (fast elevator)
            if (verticalSpeed > 10.0) {
                result.addViolation(CheatType.IMPOSSIBLE_ALTITUDE_CHANGE, 
                    String.format("Impossible altitude change: %.1f m/s", verticalSpeed), 7);
            }
        }
    }
    
    /**
     * Validates contextual speed based on movement patterns
     */
    private void validateContextualSpeed(double speed, long timeDiffMs, LocationValidationResult result) {
        // For very short time periods, allow higher speeds due to GPS accuracy issues
        if (timeDiffMs < 5000) { // Less than 5 seconds
            return;
        }
        
        // For longer periods, be more strict about walking/running speeds
        if (timeDiffMs > 30000) { // More than 30 seconds
            if (speed > WALKING_SPEED_MPS * 3) { // 3x walking speed sustained
                result.addViolation(CheatType.SUSTAINED_HIGH_SPEED, 
                    String.format("Sustained speed %.2f m/s over %d seconds", speed, timeDiffMs/1000), 5);
            }
        }
    }
    
    /**
     * Validates kill behavior patterns
     */
    private void validateKillBehaviorPatterns(String killerPlayerId, KillValidationResult result) {
        BehaviorProfile profile = playerBehaviorProfiles.computeIfAbsent(killerPlayerId, 
            k -> new BehaviorProfile());
        
        // Check kill frequency
        long recentKills = profile.getRecentKillCount(ChronoUnit.HOURS.between(
            Instant.now().minus(1, ChronoUnit.HOURS), Instant.now()));
        
        if (recentKills > SUSPICIOUS_KILL_RATE_PER_HOUR) {
            result.addViolation(CheatType.HIGH_KILL_FREQUENCY, 
                String.format("Too many kills in past hour: %d", recentKills), 7);
        }
        
        // Check for consecutive perfect kills
        if (profile.getConsecutivePerfectKills() > MAX_CONSECUTIVE_PERFECT_KILLS) {
            result.addViolation(CheatType.PERFECT_KILL_PATTERN, 
                "Too many consecutive perfect proximity kills", 8);
        }
    }
    
    /**
     * Validates kill proximity requirements
     */
    private void validateKillProximity(String killerPlayerId, String targetPlayerId, 
                                     Coordinate killLocation, KillValidationResult result) {
        try {
            // Get recent locations for both players
            List<LocationCheck> killerHistory = playerLocationHistory.get(killerPlayerId);
            List<LocationCheck> targetHistory = playerLocationHistory.get(targetPlayerId);
            
            if (killerHistory == null || targetHistory == null) {
                result.addViolation(CheatType.MISSING_LOCATION_DATA, 
                    "Missing location history for proximity validation", 6);
                return;
            }
            
            // Check if both players were actually in proximity
            boolean proximityConfirmed = false;
            Instant killTime = Instant.now();
            
            for (LocationCheck killerLoc : killerHistory) {
                for (LocationCheck targetLoc : targetHistory) {
                    // Check locations within 5 minutes of kill time
                    if (Math.abs(ChronoUnit.MINUTES.between(killerLoc.getTimestamp(), killTime)) <= 5 &&
                        Math.abs(ChronoUnit.MINUTES.between(targetLoc.getTimestamp(), killTime)) <= 5) {
                        
                        double distance = GeoUtils.calculateDistance(
                            killerLoc.getLocation(), targetLoc.getLocation());
                        
                        if (distance <= 50) { // Within 50 meters
                            proximityConfirmed = true;
                            break;
                        }
                    }
                }
                if (proximityConfirmed) break;
            }
            
            if (!proximityConfirmed) {
                result.addViolation(CheatType.IMPOSSIBLE_PROXIMITY, 
                    "No proximity evidence found for kill", 9);
            }
            
        } catch (Exception e) {
            logger.warn("Error validating kill proximity: {}", e.getMessage());
            result.addViolation(CheatType.PROXIMITY_VALIDATION_ERROR, 
                "Unable to validate proximity", 4);
        }
    }
    
    /**
     * Validates verification method authenticity
     */
    private void validateVerificationMethod(String verificationType, Map<String, Object> verificationData, 
                                          KillValidationResult result) {
        switch (verificationType.toLowerCase()) {
            case "photo":
                validatePhotoVerification(verificationData, result);
                break;
            case "qr_code":
                validateQrCodeVerification(verificationData, result);
                break;
            case "nfc":
                validateNfcVerification(verificationData, result);
                break;
            case "gps":
                validateGpsVerification(verificationData, result);
                break;
            default:
                result.addViolation(CheatType.INVALID_VERIFICATION_METHOD, 
                    "Unknown verification method", 5);
        }
    }
    
    /**
     * Validates photo verification data
     */
    private void validatePhotoVerification(Map<String, Object> verificationData, KillValidationResult result) {
        if (verificationData == null || !verificationData.containsKey("photoUrl")) {
            result.addViolation(CheatType.MISSING_VERIFICATION_DATA, 
                "Missing photo URL for photo verification", 6);
            return;
        }
        
        // Additional photo validation could include:
        // - EXIF data analysis
        // - Reverse image search
        // - Face recognition (if applicable)
        // - Timestamp verification
        
        String photoUrl = (String) verificationData.get("photoUrl");
        if (photoUrl == null || photoUrl.trim().isEmpty()) {
            result.addViolation(CheatType.INVALID_VERIFICATION_DATA, 
                "Invalid photo URL", 6);
        }
    }
    
    /**
     * Validates QR code verification data
     */
    private void validateQrCodeVerification(Map<String, Object> verificationData, KillValidationResult result) {
        if (verificationData == null || !verificationData.containsKey("qrCode")) {
            result.addViolation(CheatType.MISSING_VERIFICATION_DATA, 
                "Missing QR code data", 7);
            return;
        }
        
        String qrCode = (String) verificationData.get("qrCode");
        if (qrCode == null || qrCode.trim().isEmpty()) {
            result.addViolation(CheatType.INVALID_VERIFICATION_DATA, 
                "Invalid QR code", 7);
            return;
        }
        
        // Validate QR code format and freshness
        if (!isValidQrCodeFormat(qrCode)) {
            result.addViolation(CheatType.INVALID_QR_CODE, 
                "QR code format invalid", 8);
        }
    }
    
    /**
     * Validates NFC verification data
     */
    private void validateNfcVerification(Map<String, Object> verificationData, KillValidationResult result) {
        if (verificationData == null || !verificationData.containsKey("nfcTag")) {
            result.addViolation(CheatType.MISSING_VERIFICATION_DATA, 
                "Missing NFC tag data", 7);
            return;
        }
        
        // NFC verification is generally more secure due to proximity requirements
        String nfcTag = (String) verificationData.get("nfcTag");
        if (nfcTag == null || nfcTag.trim().isEmpty()) {
            result.addViolation(CheatType.INVALID_VERIFICATION_DATA, 
                "Invalid NFC tag", 7);
        }
    }
    
    /**
     * Validates GPS verification data
     */
    private void validateGpsVerification(Map<String, Object> verificationData, KillValidationResult result) {
        // GPS verification relies on location proximity validation
        // which is handled in validateKillProximity method
        
        if (verificationData != null && verificationData.containsKey("accuracy")) {
            Double accuracy = (Double) verificationData.get("accuracy");
            if (accuracy != null && accuracy > 20.0) {
                result.addViolation(CheatType.POOR_GPS_ACCURACY, 
                    String.format("GPS accuracy too poor for verification: %.1fm", accuracy), 4);
            }
        }
    }
    
    /**
     * Validates kill timing patterns
     */
    private void validateKillTiming(String killerPlayerId, KillValidationResult result) {
        BehaviorProfile profile = playerBehaviorProfiles.get(killerPlayerId);
        if (profile == null) return;
        
        Instant lastKillTime = profile.getLastKillTime();
        if (lastKillTime != null) {
            long minutesSinceLastKill = ChronoUnit.MINUTES.between(lastKillTime, Instant.now());
            
            if (minutesSinceLastKill < MIN_HUNT_TIME_MINUTES) {
                result.addViolation(CheatType.RAPID_SUCCESSIVE_KILLS, 
                    String.format("Kill too soon after previous (%.1f minutes)", 
                                 minutesSinceLastKill), 7);
            }
        }
    }
    
    /**
     * Validates kill location consistency with player movement
     */
    private void validateKillLocationConsistency(String killerPlayerId, Coordinate killLocation, 
                                               KillValidationResult result) {
        List<LocationCheck> history = playerLocationHistory.get(killerPlayerId);
        if (history == null || history.isEmpty()) {
            result.addViolation(CheatType.MISSING_LOCATION_DATA, 
                "No location history for consistency check", 5);
            return;
        }
        
        // Find the most recent location
        LocationCheck recentLocation = history.get(history.size() - 1);
        double distance = GeoUtils.calculateDistance(recentLocation.getLocation(), killLocation);
        
        // Kill location should be reasonably close to last known location
        if (distance > 100) { // More than 100 meters
            result.addViolation(CheatType.INCONSISTENT_KILL_LOCATION, 
                String.format("Kill location %.0fm from last known position", distance), 6);
        }
    }
    
    /**
     * Updates location history for a player
     */
    private void updateLocationHistory(String playerId, Coordinate location) {
        List<LocationCheck> history = playerLocationHistory.computeIfAbsent(
            playerId, k -> new ArrayList<>());
        
        history.add(new LocationCheck(location, Instant.now()));
        
        // Maintain history size limit
        if (history.size() > MAX_LOCATION_HISTORY) {
            history.remove(0);
        }
    }
    
    /**
     * Updates behavior profile for a player
     */
    private void updateBehaviorProfile(String playerId, String event) {
        BehaviorProfile profile = playerBehaviorProfiles.computeIfAbsent(
            playerId, k -> new BehaviorProfile());
        
        profile.recordEvent(event, Instant.now());
    }
    
    /**
     * Logs security events for location violations
     */
    private void logSecurityEvents(String playerId, LocationValidationResult result) {
        for (CheatViolation violation : result.getViolations()) {
            SecurityEvent event = new SecurityEvent("system", "ANTI_CHEAT_VIOLATION");
            event.setUserID(playerId);
            event.setTimestamp(Instant.now().toString());
            
            Map<String, String> metadata = new HashMap<>();
            metadata.put("cheatType", violation.getCheatType().toString());
            metadata.put("severity", String.valueOf(violation.getSeverity()));
            metadata.put("severityLevel", mapSeverityLevel(violation.getSeverity()));
            metadata.put("description", String.format("%s: %s", 
                violation.getCheatType(), violation.getDescription()));
            metadata.put("locationLat", String.valueOf(result.getLocation().getLatitude()));
            metadata.put("locationLon", String.valueOf(result.getLocation().getLongitude()));
            event.setMetadata(metadata);
            
            try {
                securityEventDao.saveSecurityEvent(event);
                metricsPublisher.publishAntiCheatViolation(violation.getCheatType().toString(), 
                                                         violation.getSeverity());
            } catch (Exception e) {
                logger.error("Failed to log security event: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Logs security events for kill violations
     */
    private void logKillSecurityEvents(String killerPlayerId, String targetPlayerId, 
                                     KillValidationResult result) {
        for (CheatViolation violation : result.getViolations()) {
            SecurityEvent event = new SecurityEvent("system", "SUSPICIOUS_KILL");
            event.setUserID(killerPlayerId);
            event.setTimestamp(Instant.now().toString());
            
            Map<String, String> metadata = new HashMap<>();
            metadata.put("cheatType", violation.getCheatType().toString());
            metadata.put("severity", String.valueOf(violation.getSeverity()));
            metadata.put("severityLevel", mapSeverityLevel(violation.getSeverity()));
            metadata.put("description", String.format("Kill violation: %s: %s", 
                violation.getCheatType(), violation.getDescription()));
            metadata.put("targetPlayerId", targetPlayerId);
            metadata.put("killLocationLat", String.valueOf(result.getKillLocation().getLatitude()));
            metadata.put("killLocationLon", String.valueOf(result.getKillLocation().getLongitude()));
            metadata.put("verificationType", result.getVerificationType());
            event.setMetadata(metadata);
            
            try {
                securityEventDao.saveSecurityEvent(event);
                metricsPublisher.publishSuspiciousKill(killerPlayerId, targetPlayerId, 
                                                     violation.getCheatType().toString());
            } catch (Exception e) {
                logger.error("Failed to log kill security event: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Maps severity score to severity level
     */
    private String mapSeverityLevel(int severity) {
        if (severity >= 9) return "CRITICAL";
        if (severity >= 7) return "HIGH";
        if (severity >= 4) return "MEDIUM";
        return "LOW";
    }
    
    /**
     * Validates QR code format
     */
    private boolean isValidQrCodeFormat(String qrCode) {
        // Implement QR code format validation
        // This would include checking format, expiration, signature, etc.
        return qrCode != null && qrCode.length() > 10 && qrCode.startsWith("ASG_");
    }
    
    /**
     * Gets anti-cheat summary for a player
     */
    public AntiCheatSummary getPlayerAntiCheatSummary(String playerId) {
        AntiCheatSummary summary = new AntiCheatSummary();
        summary.setPlayerId(playerId);
        
        // Location validation stats
        List<LocationCheck> locationHistory = playerLocationHistory.get(playerId);
        summary.setLocationValidationCount(locationHistory != null ? locationHistory.size() : 0);
        
        // Behavior profile stats
        BehaviorProfile behaviorProfile = playerBehaviorProfiles.get(playerId);
        if (behaviorProfile != null) {
            summary.setTotalKills(behaviorProfile.getTotalEvents("KILL_SUCCESSFUL"));
            summary.setRecentKillRate(behaviorProfile.getRecentKillCount(1)); // Last hour
            summary.setConsecutivePerfectKills(behaviorProfile.getConsecutivePerfectKills());
        }
        
        // Device fingerprint stats
        DeviceFingerprint deviceFingerprint = playerDeviceFingerprints.get(playerId);
        if (deviceFingerprint != null) {
            summary.setDeviceSwitchCount(deviceFingerprint.getSwitchCount());
            summary.setLastDeviceSwitch(deviceFingerprint.getLastSeen());
        }
        
        return summary;
    }
    
    /**
     * Triggers automated response to anti-cheat violations
     */
    public void triggerAutomatedResponse(String playerId, CheatType cheatType, int severity) {
        logger.warn("Triggering automated response for player {} - {} (severity: {})", 
                   playerId, cheatType, severity);
        
        try {
            if (severity >= 9) {
                // Critical violations - immediate action
                securityService.suspendPlayer(playerId, "Automatic suspension: critical anti-cheat violation", 
                                             24 * 60); // 24 hours
                logger.error("Player {} automatically suspended for critical violation: {}", 
                           playerId, cheatType);
            } else if (severity >= 7) {
                // High severity - warning and monitoring
                securityService.flagPlayerForReview(playerId, "High severity anti-cheat violation: " + cheatType);
                logger.warn("Player {} flagged for review due to high severity violation: {}", 
                          playerId, cheatType);
            } else if (severity >= 4) {
                // Medium severity - increased monitoring
                securityService.increaseMonitoringLevel(playerId);
                logger.info("Increased monitoring for player {} due to medium severity violation: {}", 
                          playerId, cheatType);
            }
            
            // Log the automated response
            metricsPublisher.publishAutomatedResponse(playerId, cheatType.toString(), severity);
            
        } catch (Exception e) {
            logger.error("Failed to trigger automated response for player {}: {}", 
                        playerId, e.getMessage(), e);
        }
    }
    
    // Inner classes for data structures
    
    public static class LocationValidationResult {
        private boolean valid;
        private String playerId;
        private Coordinate location;
        private Instant validationTime;
        private List<CheatViolation> violations = new ArrayList<>();
        
        // Getters and setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        
        public Coordinate getLocation() { return location; }
        public void setLocation(Coordinate location) { this.location = location; }
        
        public Instant getValidationTime() { return validationTime; }
        public void setValidationTime(Instant validationTime) { this.validationTime = validationTime; }
        
        public List<CheatViolation> getViolations() { return violations; }
        public void setViolations(List<CheatViolation> violations) { this.violations = violations; }
        
        public void addViolation(CheatType cheatType, String description, int severity) {
            violations.add(new CheatViolation(cheatType, description, severity));
            if (severity >= 8) {
                this.valid = false;
            }
        }
    }
    
    public static class KillValidationResult {
        private boolean valid;
        private String killerPlayerId;
        private String targetPlayerId;
        private Coordinate killLocation;
        private String verificationType;
        private Instant validationTime;
        private List<CheatViolation> violations = new ArrayList<>();
        
        // Getters and setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public String getKillerPlayerId() { return killerPlayerId; }
        public void setKillerPlayerId(String killerPlayerId) { this.killerPlayerId = killerPlayerId; }
        
        public String getTargetPlayerId() { return targetPlayerId; }
        public void setTargetPlayerId(String targetPlayerId) { this.targetPlayerId = targetPlayerId; }
        
        public Coordinate getKillLocation() { return killLocation; }
        public void setKillLocation(Coordinate killLocation) { this.killLocation = killLocation; }
        
        public String getVerificationType() { return verificationType; }
        public void setVerificationType(String verificationType) { this.verificationType = verificationType; }
        
        public Instant getValidationTime() { return validationTime; }
        public void setValidationTime(Instant validationTime) { this.validationTime = validationTime; }
        
        public List<CheatViolation> getViolations() { return violations; }
        public void setViolations(List<CheatViolation> violations) { this.violations = violations; }
        
        public void addViolation(CheatType cheatType, String description, int severity) {
            violations.add(new CheatViolation(cheatType, description, severity));
            if (severity >= 8) {
                this.valid = false;
            }
        }
    }
    
    public static class CheatViolation {
        private CheatType cheatType;
        private String description;
        private int severity;
        private Instant timestamp;
        
        public CheatViolation(CheatType cheatType, String description, int severity) {
            this.cheatType = cheatType;
            this.description = description;
            this.severity = severity;
            this.timestamp = Instant.now();
        }
        
        // Getters
        public CheatType getCheatType() { return cheatType; }
        public String getDescription() { return description; }
        public int getSeverity() { return severity; }
        public Instant getTimestamp() { return timestamp; }
    }
    
    public enum CheatType {
        // Location-based cheats
        IMPOSSIBLE_SPEED,
        SUSPICIOUS_SPEED,
        SUSTAINED_HIGH_SPEED,
        TELEPORTATION,
        IMPOSSIBLE_LOCATION,
        IMPOSSIBLE_ALTITUDE_CHANGE,
        IMPOSSIBLE_MANEUVER,
        REPETITIVE_PATTERN,
        
        // Timing-based cheats
        TEMPORAL_ANOMALY,
        HIGH_FREQUENCY_UPDATES,
        RAPID_SUCCESSIVE_KILLS,
        
        // Device-based cheats
        DEVICE_SWITCHING,
        MISSING_GPS_DATA,
        POOR_GPS_ACCURACY,
        SUSPICIOUS_GPS_ACCURACY,
        
        // Behavior-based cheats
        HIGH_KILL_FREQUENCY,
        PERFECT_KILL_PATTERN,
        IMPOSSIBLE_PROXIMITY,
        INCONSISTENT_KILL_LOCATION,
        
        // Verification-based cheats
        INVALID_VERIFICATION_METHOD,
        MISSING_VERIFICATION_DATA,
        INVALID_VERIFICATION_DATA,
        INVALID_QR_CODE,
        
        // System errors
        SYSTEM_ERROR,
        MISSING_LOCATION_DATA,
        PROXIMITY_VALIDATION_ERROR
    }
    
    private static class LocationCheck {
        private Coordinate location;
        private Instant timestamp;
        
        public LocationCheck(Coordinate location, Instant timestamp) {
            this.location = location;
            this.timestamp = timestamp;
        }
        
        public Coordinate getLocation() { return location; }
        public Instant getTimestamp() { return timestamp; }
    }
    
    private static class DeviceFingerprint {
        private String fingerprint;
        private Map<String, Object> metadata;
        private Instant lastSeen;
        private int switchCount;
        
        public DeviceFingerprint(String fingerprint, Map<String, Object> metadata) {
            this.fingerprint = fingerprint;
            this.metadata = metadata;
            this.lastSeen = Instant.now();
            this.switchCount = 0;
        }
        
        // Getters and setters
        public String getFingerprint() { return fingerprint; }
        public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        public Instant getLastSeen() { return lastSeen; }
        public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
        
        public int getSwitchCount() { return switchCount; }
        public void incrementSwitchCount() { this.switchCount++; }
    }
    
    private static class BehaviorProfile {
        private Map<String, List<Instant>> eventHistory = new HashMap<>();
        private int consecutivePerfectKills = 0;
        private Instant lastKillTime;
        
        public void recordEvent(String eventType, Instant timestamp) {
            eventHistory.computeIfAbsent(eventType, k -> new ArrayList<>()).add(timestamp);
            
            if ("KILL_SUCCESSFUL".equals(eventType)) {
                lastKillTime = timestamp;
                // Logic to determine if it's a "perfect" kill could be added here
            }
        }
        
        public long getRecentKillCount(long hours) {
            List<Instant> kills = eventHistory.get("KILL_SUCCESSFUL");
            if (kills == null) return 0;
            
            Instant cutoff = Instant.now().minus(hours, ChronoUnit.HOURS);
            return kills.stream()
                       .filter(timestamp -> timestamp.isAfter(cutoff))
                       .count();
        }
        
        public int getTotalEvents(String eventType) {
            List<Instant> events = eventHistory.get(eventType);
            return events != null ? events.size() : 0;
        }
        
        public int getConsecutivePerfectKills() { return consecutivePerfectKills; }
        public Instant getLastKillTime() { return lastKillTime; }
    }
    
    public static class AntiCheatSummary {
        private String playerId;
        private int locationValidationCount;
        private int totalKills;
        private long recentKillRate;
        private int consecutivePerfectKills;
        private int deviceSwitchCount;
        private Instant lastDeviceSwitch;
        
        // Getters and setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        
        public int getLocationValidationCount() { return locationValidationCount; }
        public void setLocationValidationCount(int locationValidationCount) { 
            this.locationValidationCount = locationValidationCount; 
        }
        
        public int getTotalKills() { return totalKills; }
        public void setTotalKills(int totalKills) { this.totalKills = totalKills; }
        
        public long getRecentKillRate() { return recentKillRate; }
        public void setRecentKillRate(long recentKillRate) { this.recentKillRate = recentKillRate; }
        
        public int getConsecutivePerfectKills() { return consecutivePerfectKills; }
        public void setConsecutivePerfectKills(int consecutivePerfectKills) { 
            this.consecutivePerfectKills = consecutivePerfectKills; 
        }
        
        public int getDeviceSwitchCount() { return deviceSwitchCount; }
        public void setDeviceSwitchCount(int deviceSwitchCount) { this.deviceSwitchCount = deviceSwitchCount; }
        
        public Instant getLastDeviceSwitch() { return lastDeviceSwitch; }
        public void setLastDeviceSwitch(Instant lastDeviceSwitch) { this.lastDeviceSwitch = lastDeviceSwitch; }
    }
}