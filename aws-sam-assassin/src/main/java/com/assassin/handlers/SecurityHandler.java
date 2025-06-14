package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.service.SecurityService;
import com.assassin.util.ApiGatewayResponseBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Lambda handler for security-related operations including rate limiting,
 * abuse detection, and security monitoring.
 */
public class SecurityHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityHandler.class);
    private final SecurityService securityService;
    private final Gson gson;
    
    public SecurityHandler() {
        this.securityService = new SecurityService();
        this.gson = new Gson();
    }
    
    public SecurityHandler(SecurityService securityService) {
        this.securityService = securityService;
        this.gson = new Gson();
    }
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Security handler invoked for path: {}", request.getPath());
        
        try {
            String path = request.getPath();
            String httpMethod = request.getHttpMethod();
            
            // Route to appropriate security operation
            if (path.equals("/security/rate-limit") && "POST".equals(httpMethod)) {
                return handleRateLimitCheck(request);
            } else if (path.equals("/security/abuse-detection") && "POST".equals(httpMethod)) {
                return handleAbuseDetection(request);
            } else if (path.equals("/security/location-spoofing") && "POST".equals(httpMethod)) {
                return handleLocationSpoofingCheck(request);
            } else if (path.equals("/security/log-event") && "POST".equals(httpMethod)) {
                return handleLogSecurityEvent(request);
            } else if (path.equals("/security/health") && "GET".equals(httpMethod)) {
                return handleHealthCheck();
            } else {
                return ApiGatewayResponseBuilder.buildErrorResponse(404, "Security endpoint not found");
            }
            
        } catch (RuntimeException e) {
            logger.error("Error in security handler: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Internal security service error");
        }
    }
    
    /**
     * Handle rate limit checking requests.
     */
    private APIGatewayProxyResponseEvent handleRateLimitCheck(APIGatewayProxyRequestEvent request) {
        try {
            String sourceIP = getSourceIP(request);
            JsonObject requestBody = gson.fromJson(request.getBody(), JsonObject.class);
            
            String endpoint = requestBody.has("endpoint") ? requestBody.get("endpoint").getAsString() : null;
            
            if (endpoint == null) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Missing required field: endpoint");
            }
            
            SecurityService.RateLimitResult result = securityService.checkRateLimit(sourceIP, endpoint);
            
            JsonObject response = new JsonObject();
            response.addProperty("allowed", result.isAllowed());
            response.addProperty("message", result.getMessage());
            response.addProperty("retryAfterSeconds", result.getRetryAfterSeconds());
            response.addProperty("sourceIP", sourceIP);
            response.addProperty("endpoint", endpoint);
            
            if (!result.isAllowed()) {
                // Return 429 for rate limit exceeded
                Map<String, String> headers = new HashMap<>();
                headers.put("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
                return ApiGatewayResponseBuilder.buildResponse(429, gson.toJson(response), headers);
            }
            
            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));
            
        } catch (RuntimeException e) {
            logger.error("Error checking rate limit: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Rate limit check failed");
        }
    }
    
    /**
     * Handle abuse detection requests.
     */
    private APIGatewayProxyResponseEvent handleAbuseDetection(APIGatewayProxyRequestEvent request) {
        try {
            String sourceIP = getSourceIP(request);
            JsonObject requestBody = gson.fromJson(request.getBody(), JsonObject.class);
            
            String userID = requestBody.has("userID") ? requestBody.get("userID").getAsString() : null;
            String endpoint = requestBody.has("endpoint") ? requestBody.get("endpoint").getAsString() : null;
            String userAgent = getUserAgent(request);
            
            SecurityService.AbuseDetectionResult result = securityService.detectAbuse(sourceIP, userID, endpoint, userAgent);
            
            JsonObject response = new JsonObject();
            response.addProperty("abuseDetected", result.isAbuseDetected());
            response.addProperty("reason", result.getReason());
            response.addProperty("shouldBlock", result.shouldBlock());
            response.addProperty("sourceIP", sourceIP);
            response.addProperty("userID", userID);
            response.addProperty("endpoint", endpoint);
            
            if (result.shouldBlock()) {
                // Return 403 for blocked requests
                return ApiGatewayResponseBuilder.buildResponse(403, gson.toJson(response));
            }
            
            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));
            
        } catch (RuntimeException e) {
            logger.error("Error detecting abuse: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Abuse detection failed");
        }
    }
    
    /**
     * Handle location spoofing detection requests.
     */
    private APIGatewayProxyResponseEvent handleLocationSpoofingCheck(APIGatewayProxyRequestEvent request) {
        try {
            JsonObject requestBody = gson.fromJson(request.getBody(), JsonObject.class);
            
            String userID = requestBody.has("userID") ? requestBody.get("userID").getAsString() : null;
            Double latitude = requestBody.has("latitude") ? requestBody.get("latitude").getAsDouble() : null;
            Double longitude = requestBody.has("longitude") ? requestBody.get("longitude").getAsDouble() : null;
            String timestamp = requestBody.has("timestamp") ? requestBody.get("timestamp").getAsString() : null;
            
            if (userID == null || latitude == null || longitude == null || timestamp == null) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, 
                    "Missing required fields: userID, latitude, longitude, timestamp");
            }
            
            SecurityService.LocationSpoofingResult result = securityService.detectLocationSpoofing(
                userID, latitude, longitude, timestamp);
            
            JsonObject response = new JsonObject();
            response.addProperty("spoofingDetected", result.isSpoofingDetected());
            response.addProperty("message", result.getMessage());
            response.addProperty("speedKmh", result.getSpeedKmh());
            response.addProperty("userID", userID);
            response.addProperty("latitude", latitude);
            response.addProperty("longitude", longitude);
            
            if (result.isSpoofingDetected()) {
                // Return 422 for invalid location data
                return ApiGatewayResponseBuilder.buildResponse(422, gson.toJson(response));
            }
            
            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));
            
        } catch (RuntimeException e) {
            logger.error("Error checking location spoofing: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Location spoofing check failed");
        }
    }
    
    /**
     * Handle security event logging requests.
     */
    private APIGatewayProxyResponseEvent handleLogSecurityEvent(APIGatewayProxyRequestEvent request) {
        try {
            String sourceIP = getSourceIP(request);
            JsonObject requestBody = gson.fromJson(request.getBody(), JsonObject.class);
            
            String userID = requestBody.has("userID") ? requestBody.get("userID").getAsString() : null;
            String eventType = requestBody.has("eventType") ? requestBody.get("eventType").getAsString() : null;
            String endpoint = requestBody.has("endpoint") ? requestBody.get("endpoint").getAsString() : null;
            Integer statusCode = requestBody.has("statusCode") ? requestBody.get("statusCode").getAsInt() : null;
            String userAgent = getUserAgent(request);
            
            if (eventType == null) {
                return ApiGatewayResponseBuilder.buildErrorResponse(400, "Missing required field: eventType");
            }
            
            // Parse metadata if provided
            Map<String, String> metadata = new HashMap<>();
            if (requestBody.has("metadata") && requestBody.get("metadata").isJsonObject()) {
                JsonObject metadataObj = requestBody.getAsJsonObject("metadata");
                for (String key : metadataObj.keySet()) {
                    metadata.put(key, metadataObj.get(key).getAsString());
                }
            }
            
            securityService.logSecurityEvent(sourceIP, userID, eventType, endpoint, statusCode, userAgent, metadata);
            
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "Security event logged successfully");
            response.addProperty("eventType", eventType);
            response.addProperty("sourceIP", sourceIP);
            
            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));
            
        } catch (RuntimeException e) {
            logger.error("Error logging security event: {}", e.getMessage(), e);
            return ApiGatewayResponseBuilder.buildErrorResponse(500, "Security event logging failed");
        }
    }
    
    /**
     * Handle health check requests.
     */
    private APIGatewayProxyResponseEvent handleHealthCheck() {
        JsonObject response = new JsonObject();
        response.addProperty("status", "healthy");
        response.addProperty("service", "security-handler");
        response.addProperty("timestamp", java.time.Instant.now().toString());
        
        return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));
    }
    
    /**
     * Extract source IP from the request.
     */
    private String getSourceIP(APIGatewayProxyRequestEvent request) {
        // Check for X-Forwarded-For header first (common in load balancers)
        Map<String, String> headers = request.getHeaders();
        if (headers != null) {
            String xForwardedFor = headers.get("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                // X-Forwarded-For can contain multiple IPs, take the first one
                return xForwardedFor.split(",")[0].trim();
            }
            
            String xRealIP = headers.get("X-Real-IP");
            if (xRealIP != null && !xRealIP.isEmpty()) {
                return xRealIP;
            }
        }
        
        // Fall back to request context source IP
        if (request.getRequestContext() != null && request.getRequestContext().getIdentity() != null) {
            return request.getRequestContext().getIdentity().getSourceIp();
        }
        
        return "unknown";
    }
    
    /**
     * Extract user agent from the request.
     */
    private String getUserAgent(APIGatewayProxyRequestEvent request) {
        Map<String, String> headers = request.getHeaders();
        if (headers != null) {
            return headers.get("User-Agent");
        }
        return null;
    }
} 