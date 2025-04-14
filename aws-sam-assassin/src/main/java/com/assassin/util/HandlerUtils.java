package com.assassin.util;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

/**
 * Utility class for common Lambda handler tasks.
 */
public class HandlerUtils {

    private static final Logger logger = LoggerFactory.getLogger(HandlerUtils.class);

    // Define standard CORS headers
    private static final Map<String, String> CORS_HEADERS = Map.of(
        "Access-Control-Allow-Origin", "*", // Restrict in production!
        "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS",
        "Access-Control-Allow-Headers", "Content-Type, Authorization, X-Amz-Date, X-Api-Key, X-Amz-Security-Token"
    );

    // Define headers specifically for OPTIONS preflight responses
    private static final Map<String, String> PREFLIGHT_CORS_HEADERS = Map.of(
        "Access-Control-Allow-Origin", "*", // Restrict in production!
        "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS",
        "Access-Control-Allow-Headers", "Content-Type, Authorization, X-Amz-Date, X-Api-Key, X-Amz-Security-Token",
        "Access-Control-Max-Age", "86400" // Cache preflight response for 1 day
    );

    // Private constructor to prevent instantiation
    private HandlerUtils() {}

    /**
     * Returns the standard CORS headers for API responses.
     */
    public static Map<String, String> getResponseHeaders() {
        return CORS_HEADERS;
    }

    /**
     * Returns the CORS headers specifically for preflight (OPTIONS) responses.
     */
    public static Map<String, String> getPreflightResponseHeaders() {
        return PREFLIGHT_CORS_HEADERS;
    }

    /**
     * Checks if the given request is an HTTP OPTIONS preflight request.
     */
    public static boolean isPreflightRequest(APIGatewayProxyRequestEvent request) {
        return "OPTIONS".equalsIgnoreCase(request.getHttpMethod());
    }

    /**
     * Creates a standard API Gateway response with CORS headers.
     *
     * @param statusCode HTTP status code
     * @param body Response body (can be null)
     * @return APIGatewayProxyResponseEvent
     */
    public static APIGatewayProxyResponseEvent createApiResponse(int statusCode, String body) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
            .withStatusCode(statusCode)
            .withHeaders(getResponseHeaders()); // Use the getter method
        if (body != null) {
            response.withBody(body);
        }
        return response;
    }

    /**
     * Creates an error response with CORS headers.
     *
     * @param statusCode HTTP status code
     * @param errorMessage Error message
     * @return APIGatewayProxyResponseEvent
     */
    public static APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String errorMessage) {
        // Using a simple JSON structure for the error message
        String errorBody = String.format("{\"error\": \"%s\"}", escapeJson(errorMessage));
        return createApiResponse(statusCode, errorBody);
    }

    /**
     * Extracts the Player ID (subject) from the Cognito authorizer claims in the request context.
     *
     * @param request The API Gateway request event.
     * @return An Optional containing the Player ID if found, otherwise empty.
     */
    public static Optional<String> getPlayerIdFromRequest(APIGatewayProxyRequestEvent request) {
        try {
            return Optional.ofNullable(request)
                    .map(APIGatewayProxyRequestEvent::getRequestContext)
                    .map(APIGatewayProxyRequestEvent.ProxyRequestContext::getAuthorizer)
                    .map(auth -> auth.get("claims"))
                    .filter(claims -> claims instanceof Map)
                    .map(claims -> (Map<String, Object>) claims)
                    .map(claims -> claims.get("sub"))
                    .map(Object::toString);
        } catch (Exception e) {
            logger.error("Error extracting player ID from request context", e);
            return Optional.empty();
        }
    }

    /**
     * Basic JSON string escaping.
     * Replace with a proper library function (like from Gson or Jackson) if available.
     */
    private static String escapeJson(String input) {
        if (input == null) return "";
        // Basic escaping for quotes and backslashes
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Inner class for structured error response (optional, but good practice)
    // Ensure you have a JSON library like Gson or Jackson to serialize this properly
    // if you intend to return this object directly in the response body.
    /*
    public static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }
    }
    */

    // Add other common utility methods here as needed, e.g.:
    // - parseRequestBody(String body, Class<T> clazz, Gson gson)
} 