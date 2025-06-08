package com.assassin.service;

import com.assassin.dao.SecurityEventDao;
import com.assassin.dao.DynamoDbSecurityEventDao;
import com.assassin.dao.BlockedEntityDao;
import com.assassin.dao.DynamoDbBlockedEntityDao;
import com.assassin.model.SecurityEvent;
import com.assassin.model.BlockedEntity;
import com.assassin.util.DynamoDbClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for handling security-related operations including rate limiting,
 * abuse detection, and security event tracking.
 */
public class SecurityService {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityService.class);
    
    private final SecurityEventDao securityEventDao;
    private final BlockedEntityDao blockedEntityDao;
    private final SecurityMetricsPublisher metricsPublisher;
    
    // Rate limiting thresholds
    private static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 60;
    private static final int DEFAULT_RATE_LIMIT_PER_HOUR = 1000;
    private static final int SUSPICIOUS_RATE_LIMIT_PER_MINUTE = 100;
    private static final int ABUSE_RATE_LIMIT_PER_MINUTE = 200;
    
    // Location spoofing detection thresholds
    private static final double MAX_SPEED_KMH = 300.0; // Maximum reasonable speed (e.g., airplane)
    private static final double SUSPICIOUS_SPEED_KMH = 100.0; // Speed that triggers investigation
    
    public SecurityService() {
        this.securityEventDao = new DynamoDbSecurityEventDao();
        this.blockedEntityDao = new DynamoDbBlockedEntityDao(DynamoDbClientProvider.getDynamoDbEnhancedClient());
        this.metricsPublisher = new SecurityMetricsPublisher();
    }
    
    public SecurityService(SecurityEventDao securityEventDao) {
        this.securityEventDao = securityEventDao;
        this.blockedEntityDao = new DynamoDbBlockedEntityDao(DynamoDbClientProvider.getDynamoDbEnhancedClient());
        this.metricsPublisher = new SecurityMetricsPublisher();
    }
    
    public SecurityService(SecurityEventDao securityEventDao, BlockedEntityDao blockedEntityDao) {
        this.securityEventDao = securityEventDao;
        this.blockedEntityDao = blockedEntityDao;
        this.metricsPublisher = new SecurityMetricsPublisher();
    }
    
    public SecurityService(SecurityEventDao securityEventDao, BlockedEntityDao blockedEntityDao, SecurityMetricsPublisher metricsPublisher) {
        this.securityEventDao = securityEventDao;
        this.blockedEntityDao = blockedEntityDao;
        this.metricsPublisher = metricsPublisher;
    }
    
    /**
     * Check if an IP address is rate limited.
     * 
     * @param sourceIP The IP address to check
     * @param endpoint The endpoint being accessed
     * @return RateLimitResult indicating if the request should be allowed
     */
    public RateLimitResult checkRateLimit(String sourceIP, String endpoint) {
        try {
            Instant now = Instant.now();
            String currentTime = now.toString();
            String oneMinuteAgo = now.minus(1, ChronoUnit.MINUTES).toString();
            String oneHourAgo = now.minus(1, ChronoUnit.HOURS).toString();
            
            // Count requests in the last minute
            long requestsLastMinute = securityEventDao.countSecurityEventsByIP(sourceIP, oneMinuteAgo, currentTime);
            
            // Count requests in the last hour
            long requestsLastHour = securityEventDao.countSecurityEventsByIP(sourceIP, oneHourAgo, currentTime);
            
            // Determine rate limits based on endpoint
            int minuteLimit = getMinuteLimitForEndpoint(endpoint);
            int hourLimit = getHourLimitForEndpoint(endpoint);
            
            logger.debug("Rate limit check for IP {}: {} requests/minute (limit: {}), {} requests/hour (limit: {})",
                        sourceIP, requestsLastMinute, minuteLimit, requestsLastHour, hourLimit);
            
            if (requestsLastMinute >= minuteLimit || requestsLastHour >= hourLimit) {
                // Log rate limit exceeded event
                SecurityEvent event = new SecurityEvent(sourceIP, SecurityEvent.EventType.RATE_LIMIT_EXCEEDED.getValue());
                event.setEndpoint(endpoint);
                event.setStatusCode(429);
                Map<String, String> metadata = new HashMap<>();
                metadata.put("requestsLastMinute", String.valueOf(requestsLastMinute));
                metadata.put("requestsLastHour", String.valueOf(requestsLastHour));
                metadata.put("minuteLimit", String.valueOf(minuteLimit));
                metadata.put("hourLimit", String.valueOf(hourLimit));
                event.setMetadata(metadata);
                
                securityEventDao.saveSecurityEvent(event);
                
                // Publish rate limiting metrics
                try {
                    metricsPublisher.publishRateLimitViolations(1);
                    metricsPublisher.publishSecurityEventByType("RATE_LIMIT_EXCEEDED", 1);
                    metricsPublisher.publishIPThreatScore(sourceIP, calculateThreatScore(sourceIP));
                    metricsPublisher.publishEndpointAbuseMetric(endpoint, 1);
                } catch (Exception e) {
                    logger.warn("Failed to publish rate limit metrics for IP {}: {}", sourceIP, e.getMessage());
                }
                
                return new RateLimitResult(false, "Rate limit exceeded", 
                                         Math.max(60 - (int)(requestsLastMinute - minuteLimit), 60));
            }
            
            return new RateLimitResult(true, "Request allowed", 0);
            
        } catch (Exception e) {
            logger.error("Error checking rate limit for IP {}: {}", sourceIP, e.getMessage(), e);
            // In case of error, allow the request but log the issue
            return new RateLimitResult(true, "Rate limit check failed - allowing request", 0);
        }
    }
    
    /**
     * Detect potential abuse patterns for a user or IP.
     * 
     * @param sourceIP The IP address
     * @param userID The user ID (if authenticated)
     * @param endpoint The endpoint being accessed
     * @param userAgent The user agent string
     * @return AbuseDetectionResult indicating if abuse is detected
     */
    public AbuseDetectionResult detectAbuse(String sourceIP, String userID, String endpoint, String userAgent) {
        try {
            Instant now = Instant.now();
            String currentTime = now.toString();
            String oneHourAgo = now.minus(1, ChronoUnit.HOURS).toString();
            
            // Check for suspicious patterns
            boolean isSuspicious = false;
            String reason = "";
            
            // 1. Check for excessive requests
            long requestsLastHour = securityEventDao.countSecurityEventsByIP(sourceIP, oneHourAgo, currentTime);
            if (requestsLastHour > SUSPICIOUS_RATE_LIMIT_PER_MINUTE * 60) {
                isSuspicious = true;
                reason += "Excessive requests (" + requestsLastHour + "/hour); ";
            }
            
            // 2. Check for bot-like user agents
            if (userAgent != null && isBot(userAgent)) {
                isSuspicious = true;
                reason += "Bot-like user agent; ";
            }
            
            // 3. Check for rapid endpoint switching (potential scanning)
            List<SecurityEvent> recentEvents = securityEventDao.getSecurityEventsByIP(sourceIP, 
                now.minus(10, ChronoUnit.MINUTES).toString(), currentTime);
            
            long uniqueEndpoints = recentEvents.stream()
                .map(SecurityEvent::getEndpoint)
                .distinct()
                .count();
            
            if (uniqueEndpoints > 10) {
                isSuspicious = true;
                reason += "Rapid endpoint switching (" + uniqueEndpoints + " endpoints); ";
            }
            
            // 4. Check for previous abuse events
            long abuseEvents = securityEventDao.countSecurityEventsByIPAndType(sourceIP, 
                SecurityEvent.EventType.ABUSE_DETECTED.getValue(), oneHourAgo, currentTime);
            
            if (abuseEvents > 0) {
                isSuspicious = true;
                reason += "Previous abuse detected; ";
            }
            
            if (isSuspicious) {
                // Log abuse detection event
                SecurityEvent event = new SecurityEvent(sourceIP, SecurityEvent.EventType.ABUSE_DETECTED.getValue());
                event.setUserID(userID);
                event.setEndpoint(endpoint);
                event.setUserAgent(userAgent);
                Map<String, String> metadata = new HashMap<>();
                metadata.put("reason", reason);
                metadata.put("requestsLastHour", String.valueOf(requestsLastHour));
                metadata.put("uniqueEndpoints", String.valueOf(uniqueEndpoints));
                metadata.put("abuseEvents", String.valueOf(abuseEvents));
                event.setMetadata(metadata);
                
                securityEventDao.saveSecurityEvent(event);
                
                // Publish abuse detection metrics
                try {
                    metricsPublisher.publishSuspiciousActivityRate(1);
                    metricsPublisher.publishSecurityEventByType("ABUSE_DETECTED", 1);
                    metricsPublisher.publishIPThreatScore(sourceIP, calculateThreatScore(sourceIP));
                } catch (Exception e) {
                    logger.warn("Failed to publish abuse detection metrics for IP {}: {}", sourceIP, e.getMessage());
                }
                
                return new AbuseDetectionResult(true, reason.trim(), 
                    requestsLastHour > ABUSE_RATE_LIMIT_PER_MINUTE * 60);
            }
            
            return new AbuseDetectionResult(false, "No abuse detected", false);
            
        } catch (Exception e) {
            logger.error("Error detecting abuse for IP {}: {}", sourceIP, e.getMessage(), e);
            return new AbuseDetectionResult(false, "Abuse detection failed", false);
        }
    }
    
    /**
     * Detect potential location spoofing based on movement speed.
     * 
     * @param userID The user ID
     * @param newLatitude New latitude
     * @param newLongitude New longitude
     * @param timestamp Timestamp of the new location
     * @return LocationSpoofingResult indicating if spoofing is detected
     */
    public LocationSpoofingResult detectLocationSpoofing(String userID, double newLatitude, 
                                                        double newLongitude, String timestamp) {
        try {
            // Get the user's most recent location update
            List<SecurityEvent> recentEvents = securityEventDao.getSecurityEventsByUser(userID, 
                Instant.now().minus(1, ChronoUnit.HOURS).toString(), Instant.now().toString());
            
            // Find the most recent location update
            SecurityEvent lastLocationEvent = recentEvents.stream()
                .filter(event -> event.getMetadata() != null && 
                        event.getMetadata().containsKey("latitude") && 
                        event.getMetadata().containsKey("longitude"))
                .findFirst()
                .orElse(null);
            
            if (lastLocationEvent == null) {
                // No previous location data, can't detect spoofing
                return new LocationSpoofingResult(false, "No previous location data", 0.0);
            }
            
            // Calculate distance and time difference
            double lastLat = Double.parseDouble(lastLocationEvent.getMetadata().get("latitude"));
            double lastLon = Double.parseDouble(lastLocationEvent.getMetadata().get("longitude"));
            
            double distance = calculateDistance(lastLat, lastLon, newLatitude, newLongitude);
            
            Instant lastTime = Instant.parse(lastLocationEvent.getTimestamp());
            Instant currentTime = Instant.parse(timestamp);
            long timeDiffSeconds = ChronoUnit.SECONDS.between(lastTime, currentTime);
            
            if (timeDiffSeconds <= 0) {
                return new LocationSpoofingResult(false, "Invalid time sequence", 0.0);
            }
            
            // Calculate speed in km/h
            double speedKmh = (distance / 1000.0) / (timeDiffSeconds / 3600.0);
            
            logger.debug("Location spoofing check for user {}: distance={}m, time={}s, speed={}km/h",
                        userID, distance, timeDiffSeconds, speedKmh);
            
            boolean isSpoofing = speedKmh > MAX_SPEED_KMH;
            boolean isSuspicious = speedKmh > SUSPICIOUS_SPEED_KMH;
            
            if (isSpoofing || isSuspicious) {
                // Log suspicious location event
                SecurityEvent event = new SecurityEvent("unknown", SecurityEvent.EventType.SUSPICIOUS_LOCATION.getValue());
                event.setUserID(userID);
                Map<String, String> metadata = new HashMap<>();
                metadata.put("latitude", String.valueOf(newLatitude));
                metadata.put("longitude", String.valueOf(newLongitude));
                metadata.put("previousLatitude", String.valueOf(lastLat));
                metadata.put("previousLongitude", String.valueOf(lastLon));
                metadata.put("distance", String.valueOf(distance));
                metadata.put("timeDiff", String.valueOf(timeDiffSeconds));
                metadata.put("speed", String.valueOf(speedKmh));
                metadata.put("spoofing", String.valueOf(isSpoofing));
                event.setMetadata(metadata);
                
                securityEventDao.saveSecurityEvent(event);
            }
            
            return new LocationSpoofingResult(isSpoofing, 
                isSpoofing ? "Speed too high: " + String.format("%.1f", speedKmh) + " km/h" :
                isSuspicious ? "Suspicious speed: " + String.format("%.1f", speedKmh) + " km/h" : "Normal movement",
                speedKmh);
            
        } catch (Exception e) {
            logger.error("Error detecting location spoofing for user {}: {}", userID, e.getMessage(), e);
            return new LocationSpoofingResult(false, "Location spoofing detection failed", 0.0);
        }
    }
    
    /**
     * Check if an entity (IP or user) is currently blocked.
     * 
     * @param entityId The entity ID (IP address or user ID)
     * @return true if the entity is currently blocked
     */
    public boolean isEntityBlocked(String entityId) {
        try {
            return blockedEntityDao.isEntityBlocked(entityId);
        } catch (Exception e) {
            logger.error("Error checking if entity {} is blocked: {}", entityId, e.getMessage(), e);
            return false; // Default to not blocked on error
        }
    }
    
    /**
     * Automatically block an entity based on abuse detection.
     * 
     * @param entityId The entity ID (IP address or user ID)
     * @param entityType The entity type ("ip" or "user")
     * @param reason The reason for blocking
     * @param durationHours Duration of the block in hours (null for permanent)
     * @return true if the entity was successfully blocked
     */
    public boolean blockEntity(String entityId, String entityType, String reason, Integer durationHours) {
        try {
            BlockedEntity blockedEntity = new BlockedEntity();
            blockedEntity.setEntityId(entityId);
            blockedEntity.setEntityType(entityType);
            blockedEntity.setBlockReason(reason);
            blockedEntity.setBlockedBy("SYSTEM");
            blockedEntity.setActive(true);
            blockedEntity.setViolationCount(1);
            
            if (durationHours != null) {
                Instant expiration = Instant.now().plus(durationHours, ChronoUnit.HOURS);
                blockedEntity.setExpiresAt(expiration.toString());
            }
            
            Map<String, String> metadata = new HashMap<>();
            metadata.put("autoBlocked", "true");
            metadata.put("detectionTime", Instant.now().toString());
            if (durationHours != null) {
                metadata.put("durationHours", String.valueOf(durationHours));
            }
            blockedEntity.setMetadata(metadata);
            
            blockedEntityDao.saveBlockedEntity(blockedEntity);
            
            logger.warn("Automatically blocked {} {}: {} (duration: {} hours)", 
                       entityType, entityId, reason, durationHours != null ? durationHours : "permanent");
            
            return true;
        } catch (Exception e) {
            logger.error("Error blocking entity {} ({}): {}", entityId, entityType, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check for automatic blocking conditions and block entities if necessary.
     * 
     * @param sourceIP The source IP address
     * @param userID The user ID (if authenticated)
     * @param abuseResult The abuse detection result
     * @return true if the entity was automatically blocked
     */
    public boolean checkAndApplyAutomaticBlocking(String sourceIP, String userID, AbuseDetectionResult abuseResult) {
        try {
            if (!abuseResult.shouldBlock()) {
                return false;
            }
            
            boolean blocked = false;
            
            // Block IP if severe abuse is detected
            if (sourceIP != null && !isEntityBlocked(sourceIP)) {
                blocked = blockEntity(sourceIP, "ip", "Automatic blocking: " + abuseResult.getReason(), 24);
            }
            
            // Block user if authenticated and severe abuse is detected
            if (userID != null && !isEntityBlocked(userID)) {
                blocked = blockEntity(userID, "user", "Automatic blocking: " + abuseResult.getReason(), 12) || blocked;
            }
            
            return blocked;
        } catch (Exception e) {
            logger.error("Error applying automatic blocking for IP {} / User {}: {}", sourceIP, userID, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Log a security event.
     * 
     * @param sourceIP Source IP address
     * @param userID User ID (if authenticated)
     * @param eventType Type of security event
     * @param endpoint Endpoint accessed
     * @param statusCode HTTP status code
     * @param userAgent User agent string
     * @param metadata Additional metadata
     */
    public void logSecurityEvent(String sourceIP, String userID, String eventType, String endpoint,
                                Integer statusCode, String userAgent, Map<String, String> metadata) {
        try {
            SecurityEvent event = new SecurityEvent(sourceIP, eventType);
            event.setUserID(userID);
            event.setEndpoint(endpoint);
            event.setStatusCode(statusCode);
            event.setUserAgent(userAgent);
            event.setMetadata(metadata);
            
            securityEventDao.saveSecurityEvent(event);
            
            logger.debug("Logged security event: {} for IP: {}", eventType, sourceIP);
        } catch (Exception e) {
            logger.error("Error logging security event: {}", e.getMessage(), e);
        }
    }
    
    private int getMinuteLimitForEndpoint(String endpoint) {
        if (endpoint == null) return DEFAULT_RATE_LIMIT_PER_MINUTE;
        
        // Higher limits for location updates (real-time requirement)
        if (endpoint.contains("/location")) {
            return 100;
        }
        
        // Lower limits for sensitive operations
        if (endpoint.contains("/games") && endpoint.contains("POST")) {
            return 10;
        }
        
        if (endpoint.contains("/eliminations")) {
            return 15;
        }
        
        // Very restrictive for admin operations
        if (endpoint.contains("/admin")) {
            return 5;
        }
        
        return DEFAULT_RATE_LIMIT_PER_MINUTE;
    }
    
    private int getHourLimitForEndpoint(String endpoint) {
        return getMinuteLimitForEndpoint(endpoint) * 60;
    }
    
    private boolean isBot(String userAgent) {
        if (userAgent == null) return false;
        
        String lowerUserAgent = userAgent.toLowerCase();
        return lowerUserAgent.contains("bot") || 
               lowerUserAgent.contains("crawler") || 
               lowerUserAgent.contains("spider") ||
               lowerUserAgent.contains("scraper") ||
               lowerUserAgent.length() < 10; // Very short user agents are suspicious
    }
    
    /**
     * Calculate distance between two points using Haversine formula.
     * 
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Distance in meters
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth's radius in meters
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    /**
     * Calculate a threat score for an IP address based on recent security events
     * 
     * @param sourceIP The IP address to calculate the threat score for
     * @return Threat score from 0-100 (higher is more threatening)
     */
    private int calculateThreatScore(String sourceIP) {
        try {
            Instant now = Instant.now();
            String currentTime = now.toString();
            String oneHourAgo = now.minus(1, ChronoUnit.HOURS).toString();
            String oneDayAgo = now.minus(24, ChronoUnit.HOURS).toString();
            
            int threatScore = 0;
            
            // Base score from rate limit violations (last hour)
            long rateLimitEvents = securityEventDao.countSecurityEventsByIPAndType(sourceIP, 
                SecurityEvent.EventType.RATE_LIMIT_EXCEEDED.getValue(), oneHourAgo, currentTime);
            threatScore += Math.min(rateLimitEvents * 5, 25); // Max 25 points for rate limiting
            
            // Score from abuse detection (last hour)
            long abuseEvents = securityEventDao.countSecurityEventsByIPAndType(sourceIP, 
                SecurityEvent.EventType.ABUSE_DETECTED.getValue(), oneHourAgo, currentTime);
            threatScore += Math.min(abuseEvents * 10, 30); // Max 30 points for abuse
            
            // Score from failed authentication attempts (last hour)
            long authFailures = securityEventDao.countSecurityEventsByIPAndType(sourceIP, 
                SecurityEvent.EventType.AUTHENTICATION_FAILURE.getValue(), oneHourAgo, currentTime);
            threatScore += Math.min(authFailures * 3, 15); // Max 15 points for auth failures
            
            // Score from location spoofing (last 24 hours)
            long spoofingEvents = securityEventDao.countSecurityEventsByIPAndType(sourceIP, 
                SecurityEvent.EventType.SUSPICIOUS_LOCATION.getValue(), oneDayAgo, currentTime);
            threatScore += Math.min(spoofingEvents * 15, 30); // Max 30 points for spoofing
            
            // Check if IP is currently blocked
            if (isEntityBlocked(sourceIP)) {
                threatScore = Math.max(threatScore, 80); // Blocked IPs get minimum 80 score
            }
            
            return Math.min(threatScore, 100); // Cap at 100
            
        } catch (Exception e) {
            logger.warn("Failed to calculate threat score for IP {}: {}", sourceIP, e.getMessage());
            return 0; // Default to no threat if calculation fails
        }
    }
    
    // Result classes
    public static class RateLimitResult {
        private final boolean allowed;
        private final String message;
        private final int retryAfterSeconds;
        
        public RateLimitResult(boolean allowed, String message, int retryAfterSeconds) {
            this.allowed = allowed;
            this.message = message;
            this.retryAfterSeconds = retryAfterSeconds;
        }
        
        public boolean isAllowed() { return allowed; }
        public String getMessage() { return message; }
        public int getRetryAfterSeconds() { return retryAfterSeconds; }
    }
    
    public static class AbuseDetectionResult {
        private final boolean abuseDetected;
        private final String reason;
        private final boolean shouldBlock;
        
        public AbuseDetectionResult(boolean abuseDetected, String reason, boolean shouldBlock) {
            this.abuseDetected = abuseDetected;
            this.reason = reason;
            this.shouldBlock = shouldBlock;
        }
        
        public boolean isAbuseDetected() { return abuseDetected; }
        public String getReason() { return reason; }
        public boolean shouldBlock() { return shouldBlock; }
    }
    
    public static class LocationSpoofingResult {
        private final boolean spoofingDetected;
        private final String message;
        private final double speedKmh;
        
        public LocationSpoofingResult(boolean spoofingDetected, String message, double speedKmh) {
            this.spoofingDetected = spoofingDetected;
            this.message = message;
            this.speedKmh = speedKmh;
        }
        
        public boolean isSpoofingDetected() { return spoofingDetected; }
        public String getMessage() { return message; }
        public double getSpeedKmh() { return speedKmh; }
    }
} 