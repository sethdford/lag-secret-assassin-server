package com.assassin.util;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;

/**
 * Utility class for handling authorization aspects.
 */
public class AuthorizationUtils {

    /**
     * Extracts the user ID from the request context (e.g., Cognito claims).
     * TODO: Implement actual logic based on the authorizer setup.
     *
     * @param request The incoming API Gateway request.
     * @return The authenticated user ID, or null if not found.
     */
    public static String getUserId(APIGatewayProxyRequestEvent request) {
        if (request.getRequestContext() != null && request.getRequestContext().getAuthorizer() != null) {
            Map<String, Object> claims = (Map<String, Object>) request.getRequestContext().getAuthorizer().get("claims");
            if (claims != null && claims.containsKey("sub")) {
                return (String) claims.get("sub");
            }
        }
        return null; // Or throw an exception if user ID is always expected
    }
    
    // TODO: Add methods for checking roles/permissions if needed
} 