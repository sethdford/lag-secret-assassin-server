package com.assassin.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Map;

public class RequestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(RequestUtils.class);
    private static final Gson GSON = new Gson();

    /**
     * Extracts the player ID (sub) from the Cognito authorizer claims in the API Gateway request.
     * @param request The APIGatewayProxyRequestEvent.
     * @return The player ID (cognito:username or sub) if present, otherwise null.
     */
    public static String getPlayerIdFromRequest(APIGatewayProxyRequestEvent request) {
        try {
            Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
            if (authorizer != null && authorizer.containsKey("claims")) {
                @SuppressWarnings("unchecked")
                Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
                if (claims != null) {
                    // "sub" is the standard claim for user ID in JWT from Cognito
                    String sub = claims.get("sub"); 
                    if (sub != null && !sub.isEmpty()) return sub;
                    
                    // Fallback to cognito:username if sub is not present for some reason
                    String cognitoUsername = claims.get("cognito:username");
                    if (cognitoUsername != null && !cognitoUsername.isEmpty()) return cognitoUsername;
                    
                    LOG.warn("Neither 'sub' nor 'cognito:username' claim found in authorizer claims.");
                    return null;
                }
            }
            LOG.warn("Authorizer claims not found in request context.");
            return null;
        } catch (RuntimeException e) {
            LOG.error("Error extracting player ID from request: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Parses the request body as a JsonObject.
     * @param request The APIGatewayProxyRequestEvent.
     * @return JsonObject representing the request body, or null if parsing fails or body is empty.
     */
    public static JsonObject getBodyAsJsonObject(APIGatewayProxyRequestEvent request) {
        if (request.getBody() == null || request.getBody().trim().isEmpty()) {
            return null;
        }
        try {
            return GSON.fromJson(request.getBody(), JsonObject.class);
        } catch (RuntimeException e) {
            LOG.error("Error parsing request body to JsonObject: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts a specific path parameter from the request.
     * @param request The APIGatewayProxyRequestEvent.
     * @param paramName The name of the path parameter.
     * @return The value of the path parameter, or null if not found.
     */
    public static String getPathParameter(APIGatewayProxyRequestEvent request, String paramName) {
        if (request.getPathParameters() != null) {
            return request.getPathParameters().get(paramName);
        }
        return null;
    }

    /**
     * Extracts a specific query string parameter from the request.
     * @param request The APIGatewayProxyRequestEvent.
     * @param paramName The name of the query string parameter.
     * @return The value of the query string parameter, or null if not found.
     */
    public static String getQueryStringParameter(APIGatewayProxyRequestEvent request, String paramName) {
        if (request.getQueryStringParameters() != null) {
            return request.getQueryStringParameters().get(paramName);
        }
        return null;
    }
} 