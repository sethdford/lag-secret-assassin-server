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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for security monitoring, alerting, and analytics.
 */
public class SecurityMonitoringService {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityMonitoringService.class);
    
    private final SecurityEventDao securityEventDao;
    private final BlockedEntityDao blockedEntityDao;
    
    // Alert thresholds
    private static final int HIGH_RATE_LIMIT_THRESHOLD = 100; // Events per hour
    private static final int CRITICAL_RATE_LIMIT_THRESHOLD = 500; // Events per hour
    private static final int SUSPICIOUS_LOCATION_THRESHOLD = 10; // Events per hour
    private static final int AUTHENTICATION_FAILURE_THRESHOLD = 20; // Events per hour
    private static final int ABUSE_DETECTION_THRESHOLD = 5; // Events per hour
    
    public SecurityMonitoringService() {
        this.securityEventDao = new DynamoDbSecurityEventDao();
        this.blockedEntityDao = new DynamoDbBlockedEntityDao(DynamoDbClientProvider.getDynamoDbEnhancedClient());
    }
    
    public SecurityMonitoringService(SecurityEventDao securityEventDao, BlockedEntityDao blockedEntityDao) {
        this.securityEventDao = securityEventDao;
        this.blockedEntityDao = blockedEntityDao;
    }
    
    /**
     * Generate a comprehensive security monitoring report.
     * 
     * @param hoursBack Number of hours to look back for events
     * @return Security monitoring report
     */
    public SecurityMonitoringReport generateSecurityReport(int hoursBack) {
        Instant startTime = Instant.now().minus(hoursBack, ChronoUnit.HOURS);
        Instant endTime = Instant.now();
        
        try {
            // Since we don't have getSecurityEventsByTimeRange, we'll need to get events by type
            // and combine them. For now, let's create a simplified version
            List<SecurityEvent> events = new ArrayList<>();
            
            // Get events by different types within the time range
            for (SecurityEvent.EventType eventType : SecurityEvent.EventType.values()) {
                List<SecurityEvent> typeEvents = securityEventDao.getSecurityEventsByType(
                    eventType.getValue(), startTime.toString(), endTime.toString());
                events.addAll(typeEvents);
            }
            
            return analyzeSecurityEvents(events, hoursBack);
        } catch (RuntimeException e) {
            logger.error("Error generating security report", e);
            return new SecurityMonitoringReport();
        }
    }
    
    /**
     * Check for security alerts based on recent events.
     * 
     * @param hoursBack Number of hours to analyze
     * @return List of security alerts
     */
    public List<SecurityAlert> checkSecurityAlerts(int hoursBack) {
        List<SecurityAlert> alerts = new ArrayList<>();
        Instant startTime = Instant.now().minus(hoursBack, ChronoUnit.HOURS);
        Instant endTime = Instant.now();
        
        try {
            // Get events by different types within the time range
            List<SecurityEvent> events = new ArrayList<>();
            for (SecurityEvent.EventType eventType : SecurityEvent.EventType.values()) {
                List<SecurityEvent> typeEvents = securityEventDao.getSecurityEventsByType(
                    eventType.getValue(), startTime.toString(), endTime.toString());
                events.addAll(typeEvents);
            }
            
            // Group events by type and IP
            Map<String, List<SecurityEvent>> eventsByType = events.stream()
                .collect(Collectors.groupingBy(SecurityEvent::getEventType));
            
            Map<String, List<SecurityEvent>> eventsByIP = events.stream()
                .collect(Collectors.groupingBy(SecurityEvent::getSourceIP));
            
            // Check for rate limiting alerts
            checkRateLimitingAlerts(eventsByType, alerts);
            
            // Check for suspicious IP activity
            checkSuspiciousIPActivity(eventsByIP, alerts);
            
            // Check for authentication failures
            checkAuthenticationFailures(eventsByType, alerts);
            
            // Check for abuse detection
            checkAbuseDetection(eventsByType, alerts);
            
        } catch (RuntimeException e) {
            logger.error("Error checking security alerts", e);
        }
        
        return alerts;
    }
    
    /**
     * Get security metrics for dashboard display.
     * 
     * @param hoursBack Number of hours to analyze
     * @return Security metrics
     */
    public SecurityMetrics getSecurityMetrics(int hoursBack) {
        Instant startTime = Instant.now().minus(hoursBack, ChronoUnit.HOURS);
        Instant endTime = Instant.now();
        
        try {
            // Get events by different types within the time range
            List<SecurityEvent> events = new ArrayList<>();
            for (SecurityEvent.EventType eventType : SecurityEvent.EventType.values()) {
                List<SecurityEvent> typeEvents = securityEventDao.getSecurityEventsByType(
                    eventType.getValue(), startTime.toString(), endTime.toString());
                events.addAll(typeEvents);
            }
            
            List<BlockedEntity> activeBlocks = blockedEntityDao.getActiveBlockedEntities();
            
            return calculateSecurityMetrics(events, activeBlocks, hoursBack);
        } catch (RuntimeException e) {
            logger.error("Error getting security metrics", e);
            return new SecurityMetrics();
        }
    }
    
    /**
     * Get top security threats by IP address.
     * 
     * @param hoursBack Number of hours to analyze
     * @param limit Maximum number of results
     * @return List of top threat IPs
     */
    public List<ThreatIP> getTopThreatIPs(int hoursBack, int limit) {
        Instant startTime = Instant.now().minus(hoursBack, ChronoUnit.HOURS);
        Instant endTime = Instant.now();
        
        try {
            // Get events by different types within the time range
            List<SecurityEvent> events = new ArrayList<>();
            for (SecurityEvent.EventType eventType : SecurityEvent.EventType.values()) {
                List<SecurityEvent> typeEvents = securityEventDao.getSecurityEventsByType(
                    eventType.getValue(), startTime.toString(), endTime.toString());
                events.addAll(typeEvents);
            }
            
            Map<String, List<SecurityEvent>> eventsByIP = events.stream()
                .collect(Collectors.groupingBy(SecurityEvent::getSourceIP));
            
            return eventsByIP.entrySet().stream()
                .map(entry -> {
                    String ip = entry.getKey();
                    List<SecurityEvent> ipEvents = entry.getValue();
                    
                    int threatScore = calculateThreatScore(ipEvents);
                    Map<String, Long> eventTypeCounts = ipEvents.stream()
                        .collect(Collectors.groupingBy(SecurityEvent::getEventType, Collectors.counting()));
                    
                    boolean isBlocked = blockedEntityDao.isEntityBlocked(ip);
                    
                    return new ThreatIP(ip, threatScore, ipEvents.size(), eventTypeCounts, isBlocked);
                })
                .sorted((a, b) -> Integer.compare(b.getThreatScore(), a.getThreatScore()))
                .limit(limit)
                .collect(Collectors.toList());
        } catch (RuntimeException e) {
            logger.error("Error getting top threat IPs", e);
            return List.of();
        }
    }
    
    /**
     * Perform automated security response based on threat level.
     * 
     * @param ip IP address to analyze
     * @param hoursBack Number of hours to analyze
     * @return Response action taken
     */
    public SecurityResponse performAutomatedResponse(String ip, int hoursBack) {
        try {
            Instant startTime = Instant.now().minus(hoursBack, ChronoUnit.HOURS);
            Instant endTime = Instant.now();
            
            List<SecurityEvent> ipEvents = securityEventDao.getSecurityEventsByIP(ip, startTime.toString(), endTime.toString());
            int threatScore = calculateThreatScore(ipEvents);
            
            if (threatScore >= 80) {
                // High threat - block immediately
                BlockedEntity block = createBlockedEntity(ip, "ip", "Automated block - high threat score: " + threatScore, 
                    Instant.now().plus(24, ChronoUnit.HOURS).toString());
                blockedEntityDao.saveBlockedEntity(block);
                
                return new SecurityResponse("BLOCKED", "IP blocked for 24 hours due to high threat score: " + threatScore);
            } else if (threatScore >= 50) {
                // Medium threat - temporary block
                BlockedEntity block = createBlockedEntity(ip, "ip", "Automated block - medium threat score: " + threatScore, 
                    Instant.now().plus(1, ChronoUnit.HOURS).toString());
                blockedEntityDao.saveBlockedEntity(block);
                
                return new SecurityResponse("TEMP_BLOCKED", "IP temporarily blocked for 1 hour due to medium threat score: " + threatScore);
            } else if (threatScore >= 30) {
                // Low threat - rate limit
                return new SecurityResponse("RATE_LIMITED", "IP rate limited due to suspicious activity. Threat score: " + threatScore);
            } else {
                return new SecurityResponse("MONITORED", "IP under monitoring. Threat score: " + threatScore);
            }
        } catch (RuntimeException e) {
            logger.error("Error performing automated response for IP: " + ip, e);
            return new SecurityResponse("ERROR", "Failed to perform automated response");
        }
    }
    
    // Helper methods
    
    private SecurityMonitoringReport analyzeSecurityEvents(List<SecurityEvent> events, int hoursBack) {
        SecurityMonitoringReport report = new SecurityMonitoringReport();
        report.setTimeRange(hoursBack + " hours");
        report.setTotalEvents(events.size());
        report.setGeneratedAt(Instant.now().toString());
        
        // Event type breakdown
        Map<String, Long> eventTypeCounts = events.stream()
            .collect(Collectors.groupingBy(SecurityEvent::getEventType, Collectors.counting()));
        report.setEventTypeBreakdown(eventTypeCounts);
        
        // Since SecurityEvent doesn't have severity, we'll skip severity breakdown
        Map<String, Long> severityCounts = new HashMap<>();
        report.setSeverityBreakdown(severityCounts);
        
        // Top IPs
        Map<String, Long> ipCounts = events.stream()
            .collect(Collectors.groupingBy(SecurityEvent::getSourceIP, Collectors.counting()));
        List<Map.Entry<String, Long>> topIPs = ipCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toList());
        report.setTopSourceIPs(topIPs);
        
        // Active blocks
        List<BlockedEntity> activeBlocks = blockedEntityDao.getActiveBlockedEntities();
        report.setActiveBlocks(activeBlocks.size());
        
        return report;
    }
    
    private void checkRateLimitingAlerts(Map<String, List<SecurityEvent>> eventsByType, List<SecurityAlert> alerts) {
        List<SecurityEvent> rateLimitEvents = eventsByType.getOrDefault("RATE_LIMIT_EXCEEDED", List.of());
        
        if (rateLimitEvents.size() >= CRITICAL_RATE_LIMIT_THRESHOLD) {
            alerts.add(new SecurityAlert("CRITICAL", "Rate Limiting", 
                "Critical rate limiting activity detected: " + rateLimitEvents.size() + " events in the last hour"));
        } else if (rateLimitEvents.size() >= HIGH_RATE_LIMIT_THRESHOLD) {
            alerts.add(new SecurityAlert("HIGH", "Rate Limiting", 
                "High rate limiting activity detected: " + rateLimitEvents.size() + " events in the last hour"));
        }
    }
    
    private void checkSuspiciousIPActivity(Map<String, List<SecurityEvent>> eventsByIP, List<SecurityAlert> alerts) {
        for (Map.Entry<String, List<SecurityEvent>> entry : eventsByIP.entrySet()) {
            String ip = entry.getKey();
            List<SecurityEvent> ipEvents = entry.getValue();
            
            if (ipEvents.size() >= SUSPICIOUS_LOCATION_THRESHOLD) {
                int threatScore = calculateThreatScore(ipEvents);
                if (threatScore >= 50) {
                    alerts.add(new SecurityAlert("HIGH", "Suspicious IP Activity", 
                        "IP " + ip + " has " + ipEvents.size() + " events with threat score " + threatScore));
                }
            }
        }
    }
    
    private void checkAuthenticationFailures(Map<String, List<SecurityEvent>> eventsByType, List<SecurityAlert> alerts) {
        List<SecurityEvent> authFailures = eventsByType.getOrDefault("AUTHENTICATION_FAILURE", List.of());
        
        if (authFailures.size() >= AUTHENTICATION_FAILURE_THRESHOLD) {
            alerts.add(new SecurityAlert("MEDIUM", "Authentication Failures", 
                "High number of authentication failures: " + authFailures.size() + " in the last hour"));
        }
    }
    
    private void checkAbuseDetection(Map<String, List<SecurityEvent>> eventsByType, List<SecurityAlert> alerts) {
        List<SecurityEvent> abuseEvents = eventsByType.getOrDefault("ABUSE_DETECTED", List.of());
        
        if (abuseEvents.size() >= ABUSE_DETECTION_THRESHOLD) {
            alerts.add(new SecurityAlert("HIGH", "Abuse Detection", 
                "Multiple abuse detection events: " + abuseEvents.size() + " in the last hour"));
        }
    }
    
    private SecurityMetrics calculateSecurityMetrics(List<SecurityEvent> events, List<BlockedEntity> activeBlocks, int hoursBack) {
        SecurityMetrics metrics = new SecurityMetrics();
        metrics.setTimeRange(hoursBack + " hours");
        metrics.setTotalEvents(events.size());
        metrics.setActiveBlocks(activeBlocks.size());
        
        // Calculate events per hour
        metrics.setEventsPerHour(hoursBack > 0 ? events.size() / hoursBack : 0);
        
        // Count unique IPs
        Set<String> uniqueIPs = events.stream()
            .map(SecurityEvent::getSourceIP)
            .collect(Collectors.toSet());
        metrics.setUniqueSourceIPs(uniqueIPs.size());
        
        // Since SecurityEvent doesn't have severity, we'll count critical event types instead
        long criticalEvents = events.stream()
            .filter(event -> "ABUSE_DETECTED".equals(event.getEventType()) || 
                           "SUSPICIOUS_LOCATION".equals(event.getEventType()) ||
                           "AUTHENTICATION_FAILURE".equals(event.getEventType()))
            .count();
        metrics.setHighSeverityEvents((int) criticalEvents);
        
        return metrics;
    }
    
    private int calculateThreatScore(List<SecurityEvent> events) {
        int score = 0;
        
        for (SecurityEvent event : events) {
            switch (event.getEventType()) {
                case "RATE_LIMIT_EXCEEDED":
                    score += 5;
                    break;
                case "SUSPICIOUS_LOCATION":
                    score += 15;
                    break;
                case "AUTHENTICATION_FAILURE":
                    score += 10;
                    break;
                case "ABUSE_DETECTED":
                    score += 25;
                    break;
                case "INVALID_REQUEST":
                    score += 3;
                    break;
                default:
                    score += 1;
            }
            
            // Since SecurityEvent doesn't have severity, we'll use event type for scoring
            // No additional multiplier needed as the base scores already reflect severity
        }
        
        return Math.min(score, 100); // Cap at 100
    }
    
    private BlockedEntity createBlockedEntity(String entityId, String entityType, String reason, String expiresAt) {
        BlockedEntity entity = new BlockedEntity();
        entity.setEntityId(entityId);
        entity.setEntityType(entityType);
        entity.setBlockReason(reason);
        entity.setBlockedBy("SYSTEM");
        entity.setBlockedAt(Instant.now().toString());
        entity.setExpiresAt(expiresAt);
        entity.setActive(true);
        entity.setViolationCount(1);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("automatedResponse", "true");
        metadata.put("timestamp", Instant.now().toString());
        entity.setMetadata(metadata);
        
        return entity;
    }
    
    // Data classes for responses
    
    public static class SecurityMonitoringReport {
        private String timeRange;
        private int totalEvents;
        private String generatedAt;
        private Map<String, Long> eventTypeBreakdown = new HashMap<>();
        private Map<String, Long> severityBreakdown = new HashMap<>();
        private List<Map.Entry<String, Long>> topSourceIPs = new ArrayList<>();
        private int activeBlocks;
        
        // Getters and setters
        public String getTimeRange() { return timeRange; }
        public void setTimeRange(String timeRange) { this.timeRange = timeRange; }
        
        public int getTotalEvents() { return totalEvents; }
        public void setTotalEvents(int totalEvents) { this.totalEvents = totalEvents; }
        
        public String getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
        
        public Map<String, Long> getEventTypeBreakdown() { return eventTypeBreakdown; }
        public void setEventTypeBreakdown(Map<String, Long> eventTypeBreakdown) { this.eventTypeBreakdown = eventTypeBreakdown; }
        
        public Map<String, Long> getSeverityBreakdown() { return severityBreakdown; }
        public void setSeverityBreakdown(Map<String, Long> severityBreakdown) { this.severityBreakdown = severityBreakdown; }
        
        public List<Map.Entry<String, Long>> getTopSourceIPs() { return topSourceIPs; }
        public void setTopSourceIPs(List<Map.Entry<String, Long>> topSourceIPs) { this.topSourceIPs = topSourceIPs; }
        
        public int getActiveBlocks() { return activeBlocks; }
        public void setActiveBlocks(int activeBlocks) { this.activeBlocks = activeBlocks; }
    }
    
    public static class SecurityAlert {
        private String severity;
        private String category;
        private String message;
        private String timestamp;
        
        public SecurityAlert(String severity, String category, String message) {
            this.severity = severity;
            this.category = category;
            this.message = message;
            this.timestamp = Instant.now().toString();
        }
        
        // Getters and setters
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }
    
    public static class SecurityMetrics {
        private String timeRange;
        private int totalEvents;
        private int eventsPerHour;
        private int uniqueSourceIPs;
        private int highSeverityEvents;
        private int activeBlocks;
        
        // Getters and setters
        public String getTimeRange() { return timeRange; }
        public void setTimeRange(String timeRange) { this.timeRange = timeRange; }
        
        public int getTotalEvents() { return totalEvents; }
        public void setTotalEvents(int totalEvents) { this.totalEvents = totalEvents; }
        
        public int getEventsPerHour() { return eventsPerHour; }
        public void setEventsPerHour(int eventsPerHour) { this.eventsPerHour = eventsPerHour; }
        
        public int getUniqueSourceIPs() { return uniqueSourceIPs; }
        public void setUniqueSourceIPs(int uniqueSourceIPs) { this.uniqueSourceIPs = uniqueSourceIPs; }
        
        public int getHighSeverityEvents() { return highSeverityEvents; }
        public void setHighSeverityEvents(int highSeverityEvents) { this.highSeverityEvents = highSeverityEvents; }
        
        public int getActiveBlocks() { return activeBlocks; }
        public void setActiveBlocks(int activeBlocks) { this.activeBlocks = activeBlocks; }
    }
    
    public static class ThreatIP {
        private String ipAddress;
        private int threatScore;
        private int eventCount;
        private Map<String, Long> eventTypeCounts;
        private boolean isBlocked;
        
        public ThreatIP(String ipAddress, int threatScore, int eventCount, Map<String, Long> eventTypeCounts, boolean isBlocked) {
            this.ipAddress = ipAddress;
            this.threatScore = threatScore;
            this.eventCount = eventCount;
            this.eventTypeCounts = eventTypeCounts;
            this.isBlocked = isBlocked;
        }
        
        // Getters and setters
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        
        public int getThreatScore() { return threatScore; }
        public void setThreatScore(int threatScore) { this.threatScore = threatScore; }
        
        public int getEventCount() { return eventCount; }
        public void setEventCount(int eventCount) { this.eventCount = eventCount; }
        
        public Map<String, Long> getEventTypeCounts() { return eventTypeCounts; }
        public void setEventTypeCounts(Map<String, Long> eventTypeCounts) { this.eventTypeCounts = eventTypeCounts; }
        
        public boolean isBlocked() { return isBlocked; }
        public void setBlocked(boolean blocked) { isBlocked = blocked; }
    }
    
    public static class SecurityResponse {
        private String action;
        private String message;
        private String timestamp;
        
        public SecurityResponse(String action, String message) {
            this.action = action;
            this.message = message;
            this.timestamp = Instant.now().toString();
        }
        
        // Getters and setters
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }
} 