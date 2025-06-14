package com.assassin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for publishing custom security metrics to CloudWatch
 * These metrics are used for security monitoring and alerting
 */
public class SecurityMetricsPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityMetricsPublisher.class);
    
    private final CloudWatchClient cloudWatchClient;
    private static final String NAMESPACE = "AssassinGame/Security";
    
    public SecurityMetricsPublisher() {
        this.cloudWatchClient = CloudWatchClient.builder().build();
    }
    
    public SecurityMetricsPublisher(CloudWatchClient cloudWatchClient) {
        this.cloudWatchClient = cloudWatchClient;
    }
    
    /**
     * Publish a security event count metric
     */
    public void publishSecurityEventsCount(int count) {
        publishMetric("SecurityEventsCount", count, StandardUnit.COUNT);
    }
    
    /**
     * Publish blocked entities count metric
     */
    public void publishBlockedEntitiesCount(int count) {
        publishMetric("BlockedEntitiesCount", count, StandardUnit.COUNT);
    }
    
    /**
     * Publish the maximum threat score detected
     */
    public void publishMaxThreatScore(int maxScore) {
        publishMetric("ThreatScoreMax", maxScore, StandardUnit.NONE);
    }
    
    /**
     * Publish anti-cheat violation metric
     */
    public void publishAntiCheatViolation(String cheatType, int severity) {
        logger.info("Publishing anti-cheat violation metric: {} (severity: {})", cheatType, severity);
        
        try {
            MetricDatum datum = MetricDatum.builder()
                .metricName("AntiCheatViolation")
                .value((double) severity)
                .unit(StandardUnit.NONE)
                .timestamp(Instant.now())
                .dimensions(
                    Dimension.builder()
                        .name("CheatType")
                        .value(cheatType)
                        .build()
                )
                .build();
            
            PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(NAMESPACE)
                .metricData(datum)
                .build();
            
            cloudWatchClient.putMetricData(request);
            
        } catch (Exception e) {
            logger.error("Failed to publish anti-cheat violation metric: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Publish suspicious kill metric
     */
    public void publishSuspiciousKill(String killerPlayerId, String targetPlayerId, String cheatType) {
        logger.info("Publishing suspicious kill metric: {} -> {} ({})", killerPlayerId, targetPlayerId, cheatType);
        
        try {
            MetricDatum datum = MetricDatum.builder()
                .metricName("SuspiciousKill")
                .value(1.0)
                .unit(StandardUnit.COUNT)
                .timestamp(Instant.now())
                .dimensions(
                    Dimension.builder()
                        .name("CheatType")
                        .value(cheatType)
                        .build()
                )
                .build();
            
            PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(NAMESPACE)
                .metricData(datum)
                .build();
            
            cloudWatchClient.putMetricData(request);
            
        } catch (Exception e) {
            logger.error("Failed to publish suspicious kill metric: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Publish automated response metric
     */
    public void publishAutomatedResponse(String playerId, String cheatType, int severity) {
        logger.info("Publishing automated response metric for player: {} ({}, severity: {})", 
                   playerId, cheatType, severity);
        
        try {
            MetricDatum datum = MetricDatum.builder()
                .metricName("AutomatedResponse")
                .value((double) severity)
                .unit(StandardUnit.NONE)
                .timestamp(Instant.now())
                .dimensions(
                    Dimension.builder()
                        .name("CheatType")
                        .value(cheatType)
                        .build(),
                    Dimension.builder()
                        .name("ResponseType")
                        .value(getResponseType(severity))
                        .build()
                )
                .build();
            
            PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(NAMESPACE)
                .metricData(datum)
                .build();
            
            cloudWatchClient.putMetricData(request);
            
        } catch (Exception e) {
            logger.error("Failed to publish automated response metric: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Publish player suspended metric
     */
    public void publishPlayerSuspended(String playerId, String reason) {
        logger.info("Publishing player suspended metric for: {} (reason: {})", playerId, reason);
        
        try {
            MetricDatum datum = MetricDatum.builder()
                .metricName("PlayerSuspended")
                .value(1.0)
                .unit(StandardUnit.COUNT)
                .timestamp(Instant.now())
                .dimensions(
                    Dimension.builder()
                        .name("SuspensionType")
                        .value("AUTOMATED")
                        .build()
                )
                .build();
            
            PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(NAMESPACE)
                .metricData(datum)
                .build();
            
            cloudWatchClient.putMetricData(request);
            
        } catch (Exception e) {
            logger.error("Failed to publish player suspended metric: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Publish player flagged for review metric
     */
    public void publishPlayerFlaggedForReview(String playerId, String reason) {
        logger.info("Publishing player flagged for review metric for: {} (reason: {})", playerId, reason);
        
        try {
            MetricDatum datum = MetricDatum.builder()
                .metricName("PlayerFlaggedForReview")
                .value(1.0)
                .unit(StandardUnit.COUNT)
                .timestamp(Instant.now())
                .build();
            
            PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(NAMESPACE)
                .metricData(datum)
                .build();
            
            cloudWatchClient.putMetricData(request);
            
        } catch (Exception e) {
            logger.error("Failed to publish player flagged for review metric: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Publish monitoring level increased metric
     */
    public void publishMonitoringLevelIncreased(String playerId) {
        logger.info("Publishing monitoring level increased metric for player: {}", playerId);
        
        try {
            MetricDatum datum = MetricDatum.builder()
                .metricName("MonitoringLevelIncreased")
                .value(1.0)
                .unit(StandardUnit.COUNT)
                .timestamp(Instant.now())
                .build();
            
            PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(NAMESPACE)
                .metricData(datum)
                .build();
            
            cloudWatchClient.putMetricData(request);
            
        } catch (Exception e) {
            logger.error("Failed to publish monitoring level increased metric: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Determines response type based on severity
     */
    private String getResponseType(int severity) {
        if (severity >= 9) return "SUSPENSION";
        if (severity >= 7) return "FLAG_FOR_REVIEW";
        if (severity >= 4) return "INCREASED_MONITORING";
        return "WARNING";
    }
    
    /**
     * Publish the number of alerts generated
     */
    public void publishAlertsGenerated(int count) {
        publishMetric("AlertsGenerated", count, StandardUnit.COUNT);
    }
    
    /**
     * Publish suspicious activity rate (events per minute)
     */
    public void publishSuspiciousActivityRate(double rate) {
        publishMetric("SuspiciousActivityRate", rate, StandardUnit.COUNT);
    }
    
    /**
     * Publish authentication failure rate
     */
    public void publishAuthFailureRate(double rate) {
        publishMetric("AuthFailureRate", rate, StandardUnit.COUNT);
    }
    
    /**
     * Publish rate limiting violations count
     */
    public void publishRateLimitViolations(int count) {
        publishMetric("RateLimitViolations", count, StandardUnit.COUNT);
    }
    
    /**
     * Publish IP-specific threat score
     */
    public void publishIPThreatScore(String ipAddress, int threatScore) {
        publishMetricWithDimension("IPThreatScore", threatScore, StandardUnit.NONE, 
            Map.of("IPAddress", ipAddress));
    }
    
    /**
     * Publish security event by type
     */
    public void publishSecurityEventByType(String eventType, int count) {
        publishMetricWithDimension("SecurityEventsByType", count, StandardUnit.COUNT,
            Map.of("EventType", eventType));
    }
    
    /**
     * Publish API endpoint abuse metrics
     */
    public void publishEndpointAbuseMetric(String endpoint, int abuseCount) {
        publishMetricWithDimension("EndpointAbuse", abuseCount, StandardUnit.COUNT,
            Map.of("Endpoint", endpoint));
    }
    
    /**
     * Publish geographic anomaly detection
     */
    public void publishGeographicAnomaly(String country, int anomalyCount) {
        publishMetricWithDimension("GeographicAnomalies", anomalyCount, StandardUnit.COUNT,
            Map.of("Country", country));
    }
    
    /**
     * Publish batch security metrics for efficiency
     */
    public void publishBatchMetrics(List<SecurityMetric> metrics) {
        try {
            List<MetricDatum> metricData = metrics.stream()
                .map(this::convertToMetricDatum)
                .toList();
            
            // CloudWatch has a limit of 20 metrics per request
            int batchSize = 20;
            for (int i = 0; i < metricData.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, metricData.size());
                List<MetricDatum> batch = metricData.subList(i, endIndex);
                
                PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(NAMESPACE)
                    .metricData(batch)
                    .build();
                
                cloudWatchClient.putMetricData(request);
                logger.debug("Published batch of {} security metrics to CloudWatch", batch.size());
            }
            
            logger.info("Successfully published {} security metrics to CloudWatch", metrics.size());
            
        } catch (RuntimeException e) {
            logger.error("Failed to publish batch security metrics to CloudWatch", e);
            throw new RuntimeException("Failed to publish security metrics", e);
        }
    }
    
    /**
     * Publish a single metric with optional dimensions
     */
    private void publishMetric(String metricName, double value, StandardUnit unit) {
        publishMetricWithDimension(metricName, value, unit, Map.of());
    }
    
    /**
     * Publish a metric with dimensions
     */
    private void publishMetricWithDimension(String metricName, double value, StandardUnit unit, 
                                          Map<String, String> dimensions) {
        try {
            List<Dimension> cloudWatchDimensions = dimensions.entrySet().stream()
                .map(entry -> Dimension.builder()
                    .name(entry.getKey())
                    .value(entry.getValue())
                    .build())
                .toList();
            
            MetricDatum metricDatum = MetricDatum.builder()
                .metricName(metricName)
                .value(value)
                .unit(unit)
                .timestamp(Instant.now())
                .dimensions(cloudWatchDimensions)
                .build();
            
            PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(NAMESPACE)
                .metricData(metricDatum)
                .build();
            
            cloudWatchClient.putMetricData(request);
            logger.debug("Published security metric: {} = {} to CloudWatch", metricName, value);
            
        } catch (RuntimeException e) {
            logger.error("Failed to publish security metric {} = {} to CloudWatch", metricName, value, e);
            throw new RuntimeException("Failed to publish security metric: " + metricName, e);
        }
    }
    
    /**
     * Convert SecurityMetric to CloudWatch MetricDatum
     */
    private MetricDatum convertToMetricDatum(SecurityMetric metric) {
        List<Dimension> dimensions = metric.getDimensions().entrySet().stream()
            .map(entry -> Dimension.builder()
                .name(entry.getKey())
                .value(entry.getValue())
                .build())
            .toList();
        
        return MetricDatum.builder()
            .metricName(metric.getName())
            .value(metric.getValue())
            .unit(StandardUnit.valueOf(metric.getUnit()))
            .timestamp(metric.getTimestamp())
            .dimensions(dimensions)
            .build();
    }
    
    /**
     * Data class for batch metric publishing
     */
    public static class SecurityMetric {
        private final String name;
        private final double value;
        private final String unit;
        private final Instant timestamp;
        private final Map<String, String> dimensions;
        
        public SecurityMetric(String name, double value, String unit, Map<String, String> dimensions) {
            this.name = name;
            this.value = value;
            this.unit = unit;
            this.timestamp = Instant.now();
            this.dimensions = dimensions != null ? dimensions : Map.of();
        }
        
        public SecurityMetric(String name, double value, String unit) {
            this(name, value, unit, Map.of());
        }
        
        // Getters
        public String getName() { return name; }
        public double getValue() { return value; }
        public String getUnit() { return unit; }
        public Instant getTimestamp() { return timestamp; }
        public Map<String, String> getDimensions() { return dimensions; }
    }
} 