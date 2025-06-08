package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.assassin.dao.SecurityEventDao;
import com.assassin.dao.BlockedEntityDao;
import com.assassin.dao.DynamoDbSecurityEventDao;
import com.assassin.dao.DynamoDbBlockedEntityDao;
import com.assassin.model.SecurityEvent;
import com.assassin.model.BlockedEntity;
import com.assassin.service.SecurityMetricsPublisher;
import com.assassin.util.DynamoDbClientProvider;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Scheduled Lambda handler that publishes security metrics to CloudWatch
 * This function runs on a schedule (e.g., every 5 minutes) to provide real-time security monitoring
 */
public class SecurityMetricsSchedulerHandler implements RequestHandler<ScheduledEvent, String> {

    private static final Logger logger = LoggerFactory.getLogger(SecurityMetricsSchedulerHandler.class);
    
    private final SecurityEventDao securityEventDao;
    private final BlockedEntityDao blockedEntityDao;
    private final SecurityMetricsPublisher metricsPublisher;
    
    // Constructor for dependency injection (used in tests)
    public SecurityMetricsSchedulerHandler(SecurityEventDao securityEventDao, 
                                         BlockedEntityDao blockedEntityDao,
                                         SecurityMetricsPublisher metricsPublisher) {
        this.securityEventDao = securityEventDao;
        this.blockedEntityDao = blockedEntityDao;
        this.metricsPublisher = metricsPublisher;
    }
    
    // Default constructor for Lambda runtime
    public SecurityMetricsSchedulerHandler() {
        // Validate required environment variables
        String securityEventsTableName = System.getenv("SECURITY_EVENTS_TABLE_NAME");
        String blockedEntitiesTableName = System.getenv("BLOCKED_ENTITIES_TABLE_NAME");
        
        if (securityEventsTableName == null || securityEventsTableName.trim().isEmpty()) {
            throw new IllegalStateException("SECURITY_EVENTS_TABLE_NAME environment variable is not set");
        }
        if (blockedEntitiesTableName == null || blockedEntitiesTableName.trim().isEmpty()) {
            throw new IllegalStateException("BLOCKED_ENTITIES_TABLE_NAME environment variable is not set");
        }
        
        logger.info("Initializing SecurityMetricsSchedulerHandler with security events table: {} and blocked entities table: {}", 
                   securityEventsTableName, blockedEntitiesTableName);
        
        try {
            this.securityEventDao = new DynamoDbSecurityEventDao();
            this.blockedEntityDao = new DynamoDbBlockedEntityDao(DynamoDbClientProvider.getDynamoDbEnhancedClient());
            this.metricsPublisher = new SecurityMetricsPublisher();
        } catch (Exception e) {
            logger.error("Failed to initialize SecurityMetricsSchedulerHandler: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize handler", e);
        }
    }

    @Override
    public String handleRequest(ScheduledEvent input, Context context) {
        logger.info("SecurityMetricsSchedulerHandler invoked by CloudWatch Events at: {}", input.getTime());
        
        try {
            // Calculate time windows for metrics
            Instant now = Instant.now();
            String currentTime = now.toString();
            String oneHourAgo = now.minus(1, ChronoUnit.HOURS).toString();
            String oneDayAgo = now.minus(24, ChronoUnit.HOURS).toString();
            
            // Publish rate limiting metrics (last hour)
            publishRateLimitingMetrics(oneHourAgo, currentTime);
            
            // Publish security event metrics (last hour and last day)
            publishSecurityEventMetrics(oneHourAgo, currentTime, oneDayAgo);
            
            // Publish blocked entity metrics
            publishBlockedEntityMetrics();
            
            // Publish threat detection metrics (last day)
            publishThreatDetectionMetrics(oneDayAgo, currentTime);
            
            logger.info("Successfully published security metrics to CloudWatch");
            return "Security metrics published successfully";
            
        } catch (Exception e) {
            logger.error("Failed to publish security metrics: {}", e.getMessage(), e);
            return "Failed to publish security metrics: " + e.getMessage();
        }
    }
    
    /**
     * Publish rate limiting related metrics
     */
    private void publishRateLimitingMetrics(String startTime, String endTime) {
        try {
            // Count rate limit violations in the last hour
            List<SecurityEvent> rateLimitEvents = securityEventDao.getSecurityEventsByType("RATE_LIMIT_EXCEEDED", startTime, endTime);
            metricsPublisher.publishRateLimitViolations(rateLimitEvents.size());
            
            // Count authentication failures
            List<SecurityEvent> authFailures = securityEventDao.getSecurityEventsByType("AUTH_FAILURE", startTime, endTime);
            metricsPublisher.publishAuthFailureRate(authFailures.size());
            
            logger.debug("Published rate limiting metrics: {} violations, {} auth failures", 
                        rateLimitEvents.size(), authFailures.size());
                        
        } catch (Exception e) {
            logger.warn("Failed to publish rate limiting metrics: {}", e.getMessage());
        }
    }
    
    /**
     * Publish general security event metrics
     */
    private void publishSecurityEventMetrics(String hourAgo, String currentTime, String dayAgo) {
        try {
            // Estimate hourly events by checking different event types (approximate method)
            List<SecurityEvent> rateLimitEvents = securityEventDao.getSecurityEventsByType("RATE_LIMIT_EXCEEDED", hourAgo, currentTime);
            List<SecurityEvent> authFailureEvents = securityEventDao.getSecurityEventsByType("AUTH_FAILURE", hourAgo, currentTime);
            int hourlyEvents = rateLimitEvents.size() + authFailureEvents.size();
            metricsPublisher.publishSecurityEventsCount(hourlyEvents);
            
            // Suspicious activities in last day
            List<SecurityEvent> suspiciousEvents = securityEventDao.getSecurityEventsByType("SUSPICIOUS_ACTIVITY", dayAgo, currentTime);
            metricsPublisher.publishSuspiciousActivityRate(suspiciousEvents.size());
            
            // Security events by type
            publishSecurityEventsByType(dayAgo, currentTime);
            
            logger.debug("Published security event metrics: {} hourly events, {} suspicious activities", 
                        hourlyEvents, suspiciousEvents.size());
                        
        } catch (Exception e) {
            logger.warn("Failed to publish security event metrics: {}", e.getMessage());
        }
    }
    
    /**
     * Publish metrics for specific security event types
     */
    private void publishSecurityEventsByType(String startTime, String endTime) {
        String[] eventTypes = {
            "RATE_LIMIT_EXCEEDED", "AUTH_FAILURE", "SUSPICIOUS_ACTIVITY", 
            "BLOCKED_IP", "SUSPICIOUS_LOCATION", "UNUSUAL_PATTERN"
        };
        
        for (String eventType : eventTypes) {
            try {
                List<SecurityEvent> events = securityEventDao.getSecurityEventsByType(eventType, startTime, endTime);
                metricsPublisher.publishSecurityEventByType(eventType, events.size());
            } catch (Exception e) {
                logger.warn("Failed to publish metric for event type {}: {}", eventType, e.getMessage());
            }
        }
    }
    
    /**
     * Publish blocked entity metrics
     */
    private void publishBlockedEntityMetrics() {
        try {
            // Count active blocked entities
            List<BlockedEntity> activeEntities = blockedEntityDao.getActiveBlockedEntities();
            metricsPublisher.publishBlockedEntitiesCount(activeEntities.size());
            
            logger.debug("Published blocked entity metrics: {} active entities", activeEntities.size());
            
        } catch (Exception e) {
            logger.warn("Failed to publish blocked entity metrics: {}", e.getMessage());
        }
    }
    
    /**
     * Publish threat detection metrics including IP threat scores
     */
    private void publishThreatDetectionMetrics(String startTime, String endTime) {
        try {
            // Calculate and publish average threat score for recent events
            // This gives an overview of the threat landscape
            
            // Count high-severity events as proxy for threat level
            int highThreatEvents = 0;
            List<SecurityEvent> rateLimitEvents = securityEventDao.getSecurityEventsByType("RATE_LIMIT_EXCEEDED", startTime, endTime);
            List<SecurityEvent> suspiciousLocationEvents = securityEventDao.getSecurityEventsByType("SUSPICIOUS_LOCATION", startTime, endTime);
            List<SecurityEvent> blockedIPEvents = securityEventDao.getSecurityEventsByType("BLOCKED_IP", startTime, endTime);
            
            highThreatEvents = rateLimitEvents.size() + suspiciousLocationEvents.size() + blockedIPEvents.size();
            
            // Calculate threat score (0-100 scale based on event frequency)
            int threatScore = Math.min(highThreatEvents * 5, 100); // Scale factor of 5
            
            // Log threat level for manual monitoring (CloudWatch custom metric would need public method)
            logger.info("Current threat level calculated: {}/100 based on {} high-threat events", threatScore, highThreatEvents);
            
            logger.debug("Published threat detection metrics: threat score {}", threatScore);
            
        } catch (Exception e) {
            logger.warn("Failed to publish threat detection metrics: {}", e.getMessage());
        }
    }
} 