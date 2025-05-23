package com.assassin.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;

import java.util.Collections;
import java.util.Map;

public class ApiGatewayResponseBuilder {

    private static final Gson GSON = new Gson();

    public static APIGatewayProxyResponseEvent buildResponse(int statusCode, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Collections.singletonMap("Content-Type", "application/json"))
                .withBody(body);
    }

    public static APIGatewayProxyResponseEvent buildErrorResponse(int statusCode, String errorMessage) {
        Map<String, String> errorBody = Collections.singletonMap("error", errorMessage);
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Collections.singletonMap("Content-Type", "application/json"))
                .withBody(GSON.toJson(errorBody));
    }
    
    public static APIGatewayProxyResponseEvent buildResponse(int statusCode, Object bodyObject, Map<String, String> headers) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withBody(GSON.toJson(bodyObject));
        
        Map<String, String> effectiveHeaders = headers != null ? headers : Collections.emptyMap();
        effectiveHeaders.putIfAbsent("Content-Type", "application/json");
        // Add default CORS headers if not already present
        effectiveHeaders.putIfAbsent("Access-Control-Allow-Origin", "*"); // Be more specific in production
        effectiveHeaders.putIfAbsent("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        effectiveHeaders.putIfAbsent("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token,Accept,X-Request-ID");
        
        response.setHeaders(effectiveHeaders);
        return response;
    }
} 