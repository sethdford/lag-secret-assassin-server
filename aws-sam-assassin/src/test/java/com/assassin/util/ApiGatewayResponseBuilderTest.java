package com.assassin.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApiGatewayResponseBuilderTest {

    @Test
    void shouldIncludeSecurityHeadersInBasicResponse() {
        APIGatewayProxyResponseEvent response = ApiGatewayResponseBuilder.buildResponse(200, "{\"message\":\"success\"}");
        
        assertEquals(200, response.getStatusCode());
        assertEquals("{\"message\":\"success\"}", response.getBody());
        
        Map<String, String> headers = response.getHeaders();
        assertNotNull(headers);
        
        // Verify CORS headers
        assertEquals("*", headers.get("Access-Control-Allow-Origin"));
        assertEquals("GET,POST,PUT,DELETE,OPTIONS", headers.get("Access-Control-Allow-Methods"));
        assertEquals("Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token,Accept,X-Request-ID", 
                     headers.get("Access-Control-Allow-Headers"));
        assertEquals("true", headers.get("Access-Control-Allow-Credentials"));
        assertEquals("86400", headers.get("Access-Control-Max-Age"));
        
        // Verify security headers
        assertEquals("application/json", headers.get("Content-Type"));
        assertEquals("nosniff", headers.get("X-Content-Type-Options"));
        assertEquals("DENY", headers.get("X-Frame-Options"));
        assertEquals("1; mode=block", headers.get("X-XSS-Protection"));
        assertEquals("max-age=31536000; includeSubDomains; preload", headers.get("Strict-Transport-Security"));
        assertEquals("strict-origin-when-cross-origin", headers.get("Referrer-Policy"));
        assertEquals("geolocation=(self), camera=(), microphone=(), payment=()", headers.get("Permissions-Policy"));
        
        // Verify CSP header
        String csp = headers.get("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("default-src 'self'"));
        assertTrue(csp.contains("script-src 'self' 'unsafe-inline' https://js.stripe.com"));
        assertTrue(csp.contains("object-src 'none'"));
        
        // Verify cache control headers
        assertEquals("no-cache, no-store, must-revalidate", headers.get("Cache-Control"));
        assertEquals("no-cache", headers.get("Pragma"));
        assertEquals("0", headers.get("Expires"));
    }

    @Test
    void shouldIncludeSecurityHeadersInErrorResponse() {
        APIGatewayProxyResponseEvent response = ApiGatewayResponseBuilder.buildErrorResponse(400, "Bad request");
        
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("\"error\":\"Bad request\""));
        
        Map<String, String> headers = response.getHeaders();
        assertNotNull(headers);
        
        // Verify key security headers are present
        assertEquals("nosniff", headers.get("X-Content-Type-Options"));
        assertEquals("DENY", headers.get("X-Frame-Options"));
        assertEquals("1; mode=block", headers.get("X-XSS-Protection"));
        assertEquals("max-age=31536000; includeSubDomains; preload", headers.get("Strict-Transport-Security"));
    }

    @Test
    void shouldAllowCustomHeadersWhileKeepingSecurityDefaults() {
        Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("X-Custom-Header", "custom-value");
        customHeaders.put("Content-Type", "application/xml"); // Override default
        
        APIGatewayProxyResponseEvent response = ApiGatewayResponseBuilder.buildResponse(
            200, "<xml>test</xml>", customHeaders);
        
        assertEquals(200, response.getStatusCode());
        assertEquals("<xml>test</xml>", response.getBody());
        
        Map<String, String> headers = response.getHeaders();
        
        // Custom headers should be present
        assertEquals("custom-value", headers.get("X-Custom-Header"));
        assertEquals("application/xml", headers.get("Content-Type")); // Should be overridden
        
        // Security headers should still be present
        assertEquals("nosniff", headers.get("X-Content-Type-Options"));
        assertEquals("DENY", headers.get("X-Frame-Options"));
        assertEquals("1; mode=block", headers.get("X-XSS-Protection"));
    }

    @Test
    void shouldBuildOptionsResponseForPreflightRequests() {
        APIGatewayProxyResponseEvent response = ApiGatewayResponseBuilder.buildOptionsResponse();
        
        assertEquals(200, response.getStatusCode());
        assertEquals("", response.getBody());
        
        Map<String, String> headers = response.getHeaders();
        assertNotNull(headers);
        
        // Should include all CORS headers for preflight
        assertEquals("*", headers.get("Access-Control-Allow-Origin"));
        assertEquals("GET,POST,PUT,DELETE,OPTIONS", headers.get("Access-Control-Allow-Methods"));
        assertEquals("Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token,Accept,X-Request-ID", 
                     headers.get("Access-Control-Allow-Headers"));
        assertEquals("true", headers.get("Access-Control-Allow-Credentials"));
        assertEquals("86400", headers.get("Access-Control-Max-Age"));
        
        // Should include security headers
        assertEquals("nosniff", headers.get("X-Content-Type-Options"));
        assertEquals("DENY", headers.get("X-Frame-Options"));
    }

    @Test
    void shouldBuildRateLimitResponseWithRetryAfterHeader() {
        APIGatewayProxyResponseEvent response = ApiGatewayResponseBuilder.buildRateLimitResponse(60);
        
        assertEquals(429, response.getStatusCode());
        
        Map<String, String> headers = response.getHeaders();
        assertNotNull(headers);
        
        // Should include Retry-After header
        assertEquals("60", headers.get("Retry-After"));
        
        // Should include security headers
        assertEquals("nosniff", headers.get("X-Content-Type-Options"));
        assertEquals("DENY", headers.get("X-Frame-Options"));
        assertEquals("1; mode=block", headers.get("X-XSS-Protection"));
        
        // Verify response body structure
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("\"error\":\"Rate limit exceeded\""));
        assertTrue(body.contains("\"retryAfter\":60"));
    }

    @Test
    void shouldHandleNullCustomHeaders() {
        APIGatewayProxyResponseEvent response = ApiGatewayResponseBuilder.buildResponse(
            200, "{\"test\":\"data\"}", null);
        
        assertEquals(200, response.getStatusCode());
        assertEquals("{\"test\":\"data\"}", response.getBody());
        
        Map<String, String> headers = response.getHeaders();
        assertNotNull(headers);
        
        // Should still include all default security headers
        assertEquals("application/json", headers.get("Content-Type"));
        assertEquals("nosniff", headers.get("X-Content-Type-Options"));
        assertEquals("DENY", headers.get("X-Frame-Options"));
    }

    @Test
    void shouldIncludeContentSecurityPolicyWithStripeSupport() {
        APIGatewayProxyResponseEvent response = ApiGatewayResponseBuilder.buildResponse(200, "{}");
        
        String csp = response.getHeaders().get("Content-Security-Policy");
        assertNotNull(csp);
        
        // Verify CSP allows Stripe integration
        assertTrue(csp.contains("script-src 'self' 'unsafe-inline' https://js.stripe.com"));
        assertTrue(csp.contains("connect-src 'self' https://api.stripe.com https://*.amazonaws.com"));
        assertTrue(csp.contains("frame-src https://js.stripe.com"));
        
        // Verify restrictive defaults
        assertTrue(csp.contains("default-src 'self'"));
        assertTrue(csp.contains("object-src 'none'"));
        assertTrue(csp.contains("base-uri 'self'"));
        assertTrue(csp.contains("form-action 'self'"));
    }

    @Test
    void shouldIncludePermissionsPolicyForPrivacyProtection() {
        APIGatewayProxyResponseEvent response = ApiGatewayResponseBuilder.buildResponse(200, "{}");
        
        String permissionsPolicy = response.getHeaders().get("Permissions-Policy");
        assertNotNull(permissionsPolicy);
        
        // Verify geolocation is allowed for self (needed for game)
        assertTrue(permissionsPolicy.contains("geolocation=(self)"));
        
        // Verify other permissions are restricted
        assertTrue(permissionsPolicy.contains("camera=()"));
        assertTrue(permissionsPolicy.contains("microphone=()"));
        assertTrue(permissionsPolicy.contains("payment=()"));
    }

    @Test
    void shouldIncludeCacheControlHeadersForApiResponses() {
        APIGatewayProxyResponseEvent response = ApiGatewayResponseBuilder.buildResponse(200, "{}");
        
        Map<String, String> headers = response.getHeaders();
        
        // Verify cache control headers prevent caching of API responses
        assertEquals("no-cache, no-store, must-revalidate", headers.get("Cache-Control"));
        assertEquals("no-cache", headers.get("Pragma"));
        assertEquals("0", headers.get("Expires"));
    }
} 