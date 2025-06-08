package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.service.SecurityMonitoringService;
import com.assassin.util.ApiGatewayResponseBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lambda handler for security monitoring and alerting endpoints.
 */
public class SecurityMonitoringHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityMonitoringHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Gson gson = new Gson();
    private final SecurityMonitoringService securityMonitoringService;
    
    public SecurityMonitoringHandler() {
        this.securityMonitoringService = new SecurityMonitoringService();
    }
    
    public SecurityMonitoringHandler(SecurityMonitoringService securityMonitoringService) {
        this.securityMonitoringService = securityMonitoringService;
    }
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String path = request.getPath();
            String httpMethod = request.getHttpMethod();
            
            logger.info("Processing security monitoring request: {} {}", httpMethod, path);
            
            switch (path) {
                case "/security-monitoring/report":
                    return handleSecurityReport(request);
                case "/security-monitoring/alerts":
                    return handleSecurityAlerts(request);
                case "/security-monitoring/metrics":
                    return handleSecurityMetrics(request);
                case "/security-monitoring/threats":
                    return handleTopThreats(request);
                case "/security-monitoring/response":
                    return handleAutomatedResponse(request);
                case "/security-monitoring/health":
                    return handleHealthCheck();
                default:
                    return ApiGatewayResponseBuilder.buildResponse(404, 
                        gson.toJson(Map.of("error", "Endpoint not found: " + path)));
            }
        } catch (Exception e) {
            logger.error("Error processing security monitoring request", e);
            return ApiGatewayResponseBuilder.buildResponse(500, 
                gson.toJson(Map.of("error", "Internal server error: " + e.getMessage())));
        }
    }
    
    /**
     * Generate comprehensive security monitoring report.
     * GET /security-monitoring/report?hours=24
     */
    private APIGatewayProxyResponseEvent handleSecurityReport(APIGatewayProxyRequestEvent request) {
        try {
            int hoursBack = getHoursParameter(request, 24);
            
            SecurityMonitoringService.SecurityMonitoringReport report = 
                securityMonitoringService.generateSecurityReport(hoursBack);
            
            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(report));
        } catch (Exception e) {
            logger.error("Error generating security report", e);
            return ApiGatewayResponseBuilder.buildResponse(500, 
                gson.toJson(Map.of("error", "Failed to generate security report: " + e.getMessage())));
        }
    }
    
    /**
     * Check for security alerts.
     * GET /security-monitoring/alerts?hours=1
     */
    private APIGatewayProxyResponseEvent handleSecurityAlerts(APIGatewayProxyRequestEvent request) {
        try {
            int hoursBack = getHoursParameter(request, 1);
            
            List<SecurityMonitoringService.SecurityAlert> alerts = 
                securityMonitoringService.checkSecurityAlerts(hoursBack);
            
            Map<String, Object> response = new HashMap<>();
            response.put("alerts", alerts);
            response.put("alertCount", alerts.size());
            response.put("timeRange", hoursBack + " hours");
            
            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));
        } catch (Exception e) {
            logger.error("Error checking security alerts", e);
            return ApiGatewayResponseBuilder.buildResponse(500, 
                gson.toJson(Map.of("error", "Failed to check security alerts: " + e.getMessage())));
        }
    }
    
    /**
     * Get security metrics for dashboard.
     * GET /security-monitoring/metrics?hours=24
     */
    private APIGatewayProxyResponseEvent handleSecurityMetrics(APIGatewayProxyRequestEvent request) {
        try {
            int hoursBack = getHoursParameter(request, 24);
            
            SecurityMonitoringService.SecurityMetrics metrics = 
                securityMonitoringService.getSecurityMetrics(hoursBack);
            
            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(metrics));
        } catch (Exception e) {
            logger.error("Error getting security metrics", e);
            return ApiGatewayResponseBuilder.buildResponse(500, 
                gson.toJson(Map.of("error", "Failed to get security metrics: " + e.getMessage())));
        }
    }
    
    /**
     * Get top security threats by IP.
     * GET /security-monitoring/threats?hours=24&limit=10
     */
    private APIGatewayProxyResponseEvent handleTopThreats(APIGatewayProxyRequestEvent request) {
        try {
            int hoursBack = getHoursParameter(request, 24);
            int limit = getLimitParameter(request, 10);
            
            List<SecurityMonitoringService.ThreatIP> threats = 
                securityMonitoringService.getTopThreatIPs(hoursBack, limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("threats", threats);
            response.put("threatCount", threats.size());
            response.put("timeRange", hoursBack + " hours");
            response.put("limit", limit);
            
            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));
        } catch (Exception e) {
            logger.error("Error getting top threats", e);
            return ApiGatewayResponseBuilder.buildResponse(500, 
                gson.toJson(Map.of("error", "Failed to get top threats: " + e.getMessage())));
        }
    }
    
    /**
     * Perform automated security response for an IP.
     * POST /security-monitoring/response
     * Body: {"ip": "192.168.1.1", "hours": 1}
     */
    private APIGatewayProxyResponseEvent handleAutomatedResponse(APIGatewayProxyRequestEvent request) {
        try {
            if (!"POST".equals(request.getHttpMethod())) {
                return ApiGatewayResponseBuilder.buildResponse(405, 
                    gson.toJson(Map.of("error", "Method not allowed. Use POST.")));
            }
            
            String body = request.getBody();
            if (body == null || body.trim().isEmpty()) {
                return ApiGatewayResponseBuilder.buildResponse(400, 
                    gson.toJson(Map.of("error", "Request body is required")));
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> requestData = objectMapper.readValue(body, Map.class);
            
            String ip = (String) requestData.get("ip");
            if (ip == null || ip.trim().isEmpty()) {
                return ApiGatewayResponseBuilder.buildResponse(400, 
                    gson.toJson(Map.of("error", "IP address is required")));
            }
            
            int hoursBack = requestData.containsKey("hours") ? 
                ((Number) requestData.get("hours")).intValue() : 1;
            
            SecurityMonitoringService.SecurityResponse response = 
                securityMonitoringService.performAutomatedResponse(ip, hoursBack);
            
            return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));
        } catch (Exception e) {
            logger.error("Error performing automated response", e);
            return ApiGatewayResponseBuilder.buildResponse(500, 
                gson.toJson(Map.of("error", "Failed to perform automated response: " + e.getMessage())));
        }
    }
    
    /**
     * Health check endpoint.
     * GET /security-monitoring/health
     */
    private APIGatewayProxyResponseEvent handleHealthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "SecurityMonitoringHandler");
        response.put("timestamp", java.time.Instant.now().toString());
        response.put("version", "1.0.0");
        
        return ApiGatewayResponseBuilder.buildResponse(200, gson.toJson(response));
    }
    
    // Helper methods
    
    private int getHoursParameter(APIGatewayProxyRequestEvent request, int defaultValue) {
        try {
            Map<String, String> queryParams = request.getQueryStringParameters();
            if (queryParams != null && queryParams.containsKey("hours")) {
                return Integer.parseInt(queryParams.get("hours"));
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid hours parameter, using default: " + defaultValue);
        }
        return defaultValue;
    }
    
    private int getLimitParameter(APIGatewayProxyRequestEvent request, int defaultValue) {
        try {
            Map<String, String> queryParams = request.getQueryStringParameters();
            if (queryParams != null && queryParams.containsKey("limit")) {
                return Integer.parseInt(queryParams.get("limit"));
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid limit parameter, using default: " + defaultValue);
        }
        return defaultValue;
    }
} 