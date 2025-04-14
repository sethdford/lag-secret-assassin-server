package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.service.AuthService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Lambda handler for authentication-related requests.
 */
public class AuthHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(AuthHandler.class);
    private static final Gson gson = new GsonBuilder().create();
    
    private final AuthService authService;

    /**
     * Default constructor.
     */
    public AuthHandler() {
        this.authService = new AuthService();
    }

    /**
     * Constructor with dependency injection for testability.
     * 
     * @param authService The authentication service
     */
    public AuthHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Processing {} request to {}", request.getHttpMethod(), request.getPath());
        
        try {
            // TODO: Implement authentication request handling (login, registration, etc.)
            
            // Return a default response for now
            return createErrorResponse(501, "Not implemented");
        } catch (Exception e) {
            logger.error("Error processing authentication request", e);
            return createErrorResponse(500, "Internal server error");
        }
    }
    
    private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String message) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("message", message);
        
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(gson.toJson(errorBody));
    }
} 