package com.assassin.util;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utility class for X-Ray tracing operations in the assassin game application.
 * Provides convenient methods for creating segments, subsegments, and handling tracing operations.
 */
public class XRayTraceUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(XRayTraceUtils.class);
    
    /**
     * Execute a function within a traced subsegment.
     * 
     * @param segmentName Name of the subsegment
     * @param operation The operation to execute
     * @param <T> Return type of the operation
     * @return Result of the operation
     */
    public static <T> T traceFunction(String segmentName, Supplier<T> operation) {
        return traceFunction(segmentName, null, null, operation);
    }
    
    /**
     * Execute a function within a traced subsegment with annotations.
     * 
     * @param segmentName Name of the subsegment
     * @param annotations Annotations to add to the segment
     * @param operation The operation to execute
     * @param <T> Return type of the operation
     * @return Result of the operation
     */
    public static <T> T traceFunction(String segmentName, Map<String, String> annotations, Supplier<T> operation) {
        return traceFunction(segmentName, annotations, null, operation);
    }
    
    /**
     * Execute a function within a traced subsegment with annotations and metadata.
     * 
     * @param segmentName Name of the subsegment
     * @param annotations Annotations to add to the segment
     * @param metadata Metadata to add to the segment
     * @param operation The operation to execute
     * @param <T> Return type of the operation
     * @return Result of the operation
     */
    public static <T> T traceFunction(String segmentName, Map<String, String> annotations, 
                                    Map<String, Object> metadata, Supplier<T> operation) {
        if (!XRayConfig.isTracingEnabled()) {
            return operation.get();
        }
        
        Subsegment subsegment = AWSXRay.beginSubsegment(segmentName);
        try {
            // Add annotations
            if (annotations != null) {
                annotations.forEach(subsegment::putAnnotation);
            }
            
            // Add metadata
            if (metadata != null) {
                metadata.forEach((key, value) -> subsegment.putMetadata("operation", key, value));
            }
            
            // Add standard annotations
            XRayConfig.addStandardAnnotations(segmentName, "function");
            
            T result = operation.get();
            
            // Add success metadata
            subsegment.putMetadata("operation", "success", true);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error in traced operation {}: {}", segmentName, e.getMessage(), e);
            subsegment.addException(e);
            subsegment.putMetadata("operation", "success", false);
            subsegment.putMetadata("operation", "error", e.getMessage());
            throw e;
        } finally {
            AWSXRay.endSubsegment();
        }
    }
    
    /**
     * Execute a void operation within a traced subsegment.
     * 
     * @param segmentName Name of the subsegment
     * @param operation The operation to execute
     */
    public static void traceVoidFunction(String segmentName, Runnable operation) {
        traceVoidFunction(segmentName, null, null, operation);
    }
    
    /**
     * Execute a void operation within a traced subsegment with annotations.
     * 
     * @param segmentName Name of the subsegment
     * @param annotations Annotations to add to the segment
     * @param operation The operation to execute
     */
    public static void traceVoidFunction(String segmentName, Map<String, String> annotations, Runnable operation) {
        traceVoidFunction(segmentName, annotations, null, operation);
    }
    
    /**
     * Execute a void operation within a traced subsegment with annotations and metadata.
     * 
     * @param segmentName Name of the subsegment
     * @param annotations Annotations to add to the segment
     * @param metadata Metadata to add to the segment
     * @param operation The operation to execute
     */
    public static void traceVoidFunction(String segmentName, Map<String, String> annotations, 
                                       Map<String, Object> metadata, Runnable operation) {
        traceFunction(segmentName, annotations, metadata, () -> {
            operation.run();
            return null;
        });
    }
    
    /**
     * Create a database operation subsegment.
     * 
     * @param operation Database operation name (e.g., "query", "put", "delete")
     * @param tableName Table name being accessed
     * @param keyInfo Key information for the operation
     * @param function The database operation to execute
     * @param <T> Return type of the operation
     * @return Result of the operation
     */
    public static <T> T traceDatabaseOperation(String operation, String tableName, String keyInfo, Supplier<T> function) {
        return traceFunction(
            "DynamoDB." + operation,
            Map.of(
                "aws.operation", operation,
                "aws.table_name", tableName,
                "aws.region", System.getenv("AWS_REGION") != null ? System.getenv("AWS_REGION") : "unknown"
            ),
            Map.of(
                "table_name", tableName,
                "operation", operation,
                "key_info", keyInfo != null ? keyInfo : "unknown"
            ),
            function
        );
    }
    
    /**
     * Create an external service call subsegment.
     * 
     * @param serviceName Name of the external service
     * @param operation Operation being performed
     * @param endpoint Service endpoint or identifier
     * @param function The service call to execute
     * @param <T> Return type of the operation
     * @return Result of the operation
     */
    public static <T> T traceExternalService(String serviceName, String operation, String endpoint, Supplier<T> function) {
        return traceFunction(
            serviceName + "." + operation,
            Map.of(
                "service.name", serviceName,
                "service.operation", operation,
                "service.type", "external"
            ),
            Map.of(
                "endpoint", endpoint != null ? endpoint : "unknown",
                "service_name", serviceName,
                "operation", operation
            ),
            function
        );
    }
    
    /**
     * Create a business logic operation subsegment.
     * 
     * @param businessFunction Name of the business function
     * @param entityType Type of entity being processed
     * @param entityId ID of the entity (can be null)
     * @param function The business logic to execute
     * @param <T> Return type of the operation
     * @return Result of the operation
     */
    public static <T> T traceBusinessLogic(String businessFunction, String entityType, String entityId, Supplier<T> function) {
        Map<String, String> annotations = Map.of(
            "business.function", businessFunction,
            "business.entity_type", entityType
        );
        
        Map<String, Object> metadata = Map.of(
            "business_function", businessFunction,
            "entity_type", entityType,
            "entity_id", entityId != null ? entityId : "unknown"
        );
        
        return traceFunction("Business." + businessFunction, annotations, metadata, function);
    }
    
    /**
     * Add custom performance metrics to the current segment.
     * 
     * @param metricName Name of the performance metric
     * @param value Metric value
     * @param unit Unit of measurement
     */
    public static void addPerformanceMetric(String metricName, double value, String unit) {
        try {
            if (XRayConfig.isTracingEnabled() && AWSXRay.getCurrentSegment() != null) {
                XRayConfig.addMetadata("performance", metricName, Map.of(
                    "value", value,
                    "unit", unit,
                    "timestamp", System.currentTimeMillis()
                ));
            }
        } catch (Exception e) {
            logger.debug("Failed to add performance metric: {}", e.getMessage());
        }
    }
    
    /**
     * Add request context information to the current segment.
     * 
     * @param userId User ID making the request
     * @param requestId Request ID for correlation
     * @param httpMethod HTTP method
     * @param path Request path
     */
    public static void addRequestContext(String userId, String requestId, String httpMethod, String path) {
        try {
            if (XRayConfig.isTracingEnabled()) {
                if (userId != null) {
                    XRayConfig.addAnnotation("user.id", userId);
                }
                if (requestId != null) {
                    XRayConfig.addAnnotation("request.id", requestId);
                }
                if (httpMethod != null) {
                    XRayConfig.addAnnotation("http.method", httpMethod);
                }
                if (path != null) {
                    XRayConfig.addAnnotation("http.path", path);
                }
                
                XRayConfig.addMetadata("request", "context", Map.of(
                    "user_id", userId != null ? userId : "anonymous",
                    "request_id", requestId != null ? requestId : "unknown",
                    "http_method", httpMethod != null ? httpMethod : "unknown",
                    "path", path != null ? path : "unknown",
                    "timestamp", System.currentTimeMillis()
                ));
            }
        } catch (Exception e) {
            logger.debug("Failed to add request context: {}", e.getMessage());
        }
    }
    
    /**
     * Add error details to the current segment.
     * 
     * @param errorType Type of error
     * @param errorMessage Error message
     * @param errorCode Error code (optional)
     */
    public static void addErrorDetails(String errorType, String errorMessage, String errorCode) {
        try {
            if (XRayConfig.isTracingEnabled() && AWSXRay.getCurrentSegment() != null) {
                XRayConfig.addAnnotation("error.type", errorType);
                if (errorCode != null) {
                    XRayConfig.addAnnotation("error.code", errorCode);
                }
                
                XRayConfig.addMetadata("error", "details", Map.of(
                    "type", errorType,
                    "message", errorMessage != null ? errorMessage : "unknown",
                    "code", errorCode != null ? errorCode : "unknown",
                    "timestamp", System.currentTimeMillis()
                ));
            }
        } catch (Exception e) {
            logger.debug("Failed to add error details: {}", e.getMessage());
        }
    }
}