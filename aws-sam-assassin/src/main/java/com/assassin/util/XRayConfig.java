package com.assassin.util;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.entities.TraceID;
import com.amazonaws.xray.plugins.ECSPlugin;
import com.amazonaws.xray.plugins.EC2Plugin;
import com.amazonaws.xray.strategy.ContextMissingStrategy;
import com.amazonaws.xray.strategy.LogErrorContextMissingStrategy;
import com.amazonaws.xray.strategy.sampling.LocalizedSamplingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Map;

/**
 * Configuration class for AWS X-Ray tracing in the assassin game application.
 * Provides centralized configuration and utilities for distributed tracing.
 */
public class XRayConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(XRayConfig.class);
    private static final String SERVICE_NAME = "assassin-game-api";
    private static final String SERVICE_VERSION = "1.0.0";
    
    // Environment variables for configuration
    private static final String XRAY_DAEMON_ADDRESS = System.getenv("_X_AMZN_TRACE_ID");
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String STAGE = System.getenv("STAGE");
    
    private static AWSXRayRecorder recorder;
    private static boolean initialized = false;
    
    /**
     * Initialize X-Ray recorder with appropriate configuration for Lambda environment.
     */
    public static synchronized void initialize() {
        if (initialized) {
            logger.debug("X-Ray recorder already initialized");
            return;
        }
        
        try {
            logger.info("Initializing X-Ray recorder for service: {}", SERVICE_NAME);
            
            AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard()
                    .withPlugin(new ECSPlugin())
                    .withPlugin(new EC2Plugin())
                    .withContextMissingStrategy(new LogErrorContextMissingStrategy());
            
            // Configure sampling strategy
            configureSampling(builder);
            
            recorder = builder.build();
            AWSXRay.setGlobalRecorder(recorder);
            
            // Set service metadata
            AWSXRay.beginSegment(SERVICE_NAME);
            AWSXRay.getCurrentSegment().putMetadata("service", Map.of(
                "name", SERVICE_NAME,
                "version", SERVICE_VERSION,
                "stage", STAGE != null ? STAGE : "unknown",
                "region", AWS_REGION != null ? AWS_REGION : "unknown"
            ));
            AWSXRay.endSegment();
            
            initialized = true;
            logger.info("X-Ray recorder initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize X-Ray recorder: {}", e.getMessage(), e);
            // Don't throw exception to prevent application startup failure
            // Use a no-op recorder instead
            recorder = AWSXRayRecorderBuilder.standard()
                    .withContextMissingStrategy(new LogErrorContextMissingStrategy())
                    .build();
            AWSXRay.setGlobalRecorder(recorder);
            initialized = true;
        }
    }
    
    /**
     * Configure sampling strategy based on environment.
     */
    private static void configureSampling(AWSXRayRecorderBuilder builder) {
        try {
            // Try to load sampling rules from classpath
            URL samplingRulesUrl = XRayConfig.class.getClassLoader().getResource("sampling-rules.json");
            if (samplingRulesUrl != null) {
                logger.info("Loading sampling rules from: {}", samplingRulesUrl);
                builder.withSamplingStrategy(new LocalizedSamplingStrategy(samplingRulesUrl));
            } else {
                logger.info("No custom sampling rules found, using default sampling");
            }
        } catch (Exception e) {
            logger.warn("Failed to load custom sampling rules, using default: {}", e.getMessage());
        }
    }
    
    /**
     * Get the current X-Ray recorder instance.
     */
    public static AWSXRayRecorder getRecorder() {
        if (!initialized) {
            initialize();
        }
        return recorder;
    }
    
    /**
     * Check if X-Ray tracing is enabled and available.
     */
    public static boolean isTracingEnabled() {
        return initialized && recorder != null && XRAY_DAEMON_ADDRESS != null;
    }
    
    /**
     * Get current trace ID for correlation.
     */
    public static String getCurrentTraceId() {
        try {
            if (isTracingEnabled() && AWSXRay.getCurrentSegment() != null) {
                TraceID traceId = AWSXRay.getCurrentSegment().getTraceId();
                return traceId != null ? traceId.toString() : null;
            }
        } catch (Exception e) {
            logger.debug("Could not get current trace ID: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Add standard annotations to current segment.
     */
    public static void addStandardAnnotations(String operation, String resource) {
        try {
            if (isTracingEnabled() && AWSXRay.getCurrentSegment() != null) {
                AWSXRay.getCurrentSegment().putAnnotation("service", SERVICE_NAME);
                AWSXRay.getCurrentSegment().putAnnotation("operation", operation);
                AWSXRay.getCurrentSegment().putAnnotation("resource", resource);
                AWSXRay.getCurrentSegment().putAnnotation("version", SERVICE_VERSION);
                
                if (STAGE != null) {
                    AWSXRay.getCurrentSegment().putAnnotation("stage", STAGE);
                }
                if (AWS_REGION != null) {
                    AWSXRay.getCurrentSegment().putAnnotation("region", AWS_REGION);
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to add standard annotations: {}", e.getMessage());
        }
    }
    
    /**
     * Add metadata to current segment.
     */
    public static void addMetadata(String namespace, String key, Object value) {
        try {
            if (isTracingEnabled() && AWSXRay.getCurrentSegment() != null) {
                AWSXRay.getCurrentSegment().putMetadata(namespace, key, value);
            }
        } catch (Exception e) {
            logger.debug("Failed to add metadata: {}", e.getMessage());
        }
    }
    
    /**
     * Add annotation to current segment.
     */
    public static void addAnnotation(String key, String value) {
        try {
            if (isTracingEnabled() && AWSXRay.getCurrentSegment() != null) {
                AWSXRay.getCurrentSegment().putAnnotation(key, value);
            }
        } catch (Exception e) {
            logger.debug("Failed to add annotation: {}", e.getMessage());
        }
    }
    
    /**
     * Mark current segment as error.
     */
    public static void addError(Throwable error) {
        try {
            if (isTracingEnabled() && AWSXRay.getCurrentSegment() != null) {
                AWSXRay.getCurrentSegment().addException(error);
            }
        } catch (Exception e) {
            logger.debug("Failed to add error to segment: {}", e.getMessage());
        }
    }
    
    /**
     * Static initializer to automatically initialize X-Ray when class is loaded.
     */
    static {
        initialize();
    }
}