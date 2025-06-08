package com.assassin.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ApiGatewayResponseBuilder {

    private static final Gson GSON = new Gson();

    /**
     * Get default security headers for all API responses
     */
    private static Map<String, String> getDefaultSecurityHeaders() {
        Map<String, String> headers = new HashMap<>();
        
        // CORS headers
        headers.put("Access-Control-Allow-Origin", "*"); // TODO: Be more specific in production
        headers.put("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token,Accept,X-Request-ID");
        headers.put("Access-Control-Allow-Credentials", "true");
        headers.put("Access-Control-Max-Age", "86400"); // 24 hours
        
        // Security headers
        headers.put("Content-Type", "application/json");
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("X-Frame-Options", "DENY");
        headers.put("X-XSS-Protection", "1; mode=block");
        headers.put("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        headers.put("Referrer-Policy", "strict-origin-when-cross-origin");
        headers.put("Permissions-Policy", "geolocation=(self), camera=(), microphone=(), payment=()");
        
        // Content Security Policy - restrictive but allows necessary functionality
        headers.put("Content-Security-Policy", 
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' https://js.stripe.com; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: https:; " +
            "connect-src 'self' https://api.stripe.com https://*.amazonaws.com; " +
            "font-src 'self'; " +
            "object-src 'none'; " +
            "media-src 'self'; " +
            "frame-src https://js.stripe.com; " +
            "base-uri 'self'; " +
            "form-action 'self'"
        );
        
        // Cache control for API responses
        headers.put("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.put("Pragma", "no-cache");
        headers.put("Expires", "0");
        
        return headers;
    }

    public static APIGatewayProxyResponseEvent buildResponse(int statusCode, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(getDefaultSecurityHeaders())
                .withBody(body);
    }

    public static APIGatewayProxyResponseEvent buildErrorResponse(int statusCode, String errorMessage) {
        Map<String, String> errorBody = Collections.singletonMap("error", errorMessage);
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(getDefaultSecurityHeaders())
                .withBody(GSON.toJson(errorBody));
    }
    
    public static APIGatewayProxyResponseEvent buildResponse(int statusCode, String body, Map<String, String> headers) {
        // Start with default security headers
        Map<String, String> effectiveHeaders = getDefaultSecurityHeaders();
        
        // Override with provided headers (allows customization while keeping security defaults)
        if (headers != null) {
            effectiveHeaders.putAll(headers);
        }
        
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(effectiveHeaders)
                .withBody(body);
    }

    /**
     * Build a response specifically for OPTIONS preflight requests
     */
    public static APIGatewayProxyResponseEvent buildOptionsResponse() {
        Map<String, String> headers = getDefaultSecurityHeaders();
        
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(headers)
                .withBody("");
    }

    /**
     * Build a response for rate limiting with appropriate headers
     */
    public static APIGatewayProxyResponseEvent buildRateLimitResponse(int retryAfterSeconds) {
        Map<String, String> headers = getDefaultSecurityHeaders();
        headers.put("Retry-After", String.valueOf(retryAfterSeconds));
        
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", "Rate limit exceeded");
        errorBody.put("message", "Too many requests. Please try again later.");
        errorBody.put("retryAfter", retryAfterSeconds);
        
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(429)
                .withHeaders(headers)
                .withBody(GSON.toJson(errorBody));
    }
} 