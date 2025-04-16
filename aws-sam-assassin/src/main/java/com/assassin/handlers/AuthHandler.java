package com.assassin.handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.service.AuthService;
import com.assassin.service.PlayerService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;

/**
 * Lambda handler for authentication-related requests.
 */
public class AuthHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(AuthHandler.class);
    private static final Gson gson = new GsonBuilder().create();
    
    private final AuthService authService;
    private final PlayerService playerService;

    /**
     * Default constructor.
     */
    public AuthHandler() {
        this.authService = new AuthService();
        this.playerService = new PlayerService();
    }

    /**
     * Constructor with dependency injection for testability.
     * 
     * @param authService The authentication service
     * @param playerService The player service for syncing profiles
     */
    public AuthHandler(AuthService authService, PlayerService playerService) {
        this.authService = authService;
        this.playerService = playerService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String path = request.getPath();
        String method = request.getHttpMethod();
        logger.info("Processing {} request to {}", method, path);
        
        try {
            if ("POST".equals(method) && "/auth/signup".equals(path)) {
                return handleSignUp(request);
            } else if ("POST".equals(method) && "/auth/signin".equals(path)) {
                return handleSignIn(request);
            } else if ("GET".equals(method) && "/auth/oauth/callback".equals(path)) {
                return handleOAuthCallback(request);
            } else if ("POST".equals(method) && "/auth/oauth/token".equals(path)) {
                return handleOAuthToken(request);
            } else if ("GET".equals(method) && "/auth/oauth/url".equals(path)) {
                return getOAuthUrl(request);
            } else {
                logger.warn("Unhandled auth route: {} {}", method, path);
                return createErrorResponse(404, "Not found");
            }
        } catch (Exception e) {
            logger.error("Error processing authentication request", e);
            return createErrorResponse(500, "Internal server error: " + e.getMessage());
        }
    }
    
    /**
     * Handles user registration (signup) requests.
     */
    private APIGatewayProxyResponseEvent handleSignUp(APIGatewayProxyRequestEvent request) {
        try {
            JsonObject requestBody = JsonParser.parseString(request.getBody()).getAsJsonObject();
            
            String email = getRequiredField(requestBody, "email");
            String password = getRequiredField(requestBody, "password");
            String name = getRequiredField(requestBody, "name");
            
            // Optional additional attributes
            Map<String, String> additionalAttributes = new HashMap<>();
            if (requestBody.has("phoneNumber")) {
                additionalAttributes.put("phone_number", requestBody.get("phoneNumber").getAsString());
            }
            
            String userSub = authService.registerUser(email, password, name, additionalAttributes);
            
            Map<String, String> response = new HashMap<>();
            response.put("userId", userSub);
            response.put("status", "success");
            response.put("message", "User registered successfully. Please check your email for verification code.");
            
            return createSuccessResponse(response);
        } catch (IllegalArgumentException | JsonSyntaxException e) {
            logger.warn("Invalid signup request: {}", e.getMessage());
            return createErrorResponse(400, e.getMessage());
        } catch (CognitoIdentityProviderException e) {
            logger.error("Cognito error during signup: {}", e.getMessage());
            return createErrorResponse(400, "Registration failed: " + e.getMessage());
        }
    }
    
    /**
     * Handles user authentication (signin) requests.
     */
    private APIGatewayProxyResponseEvent handleSignIn(APIGatewayProxyRequestEvent request) {
        try {
            JsonObject requestBody = JsonParser.parseString(request.getBody()).getAsJsonObject();
            
            String email = getRequiredField(requestBody, "email");
            String password = getRequiredField(requestBody, "password");
            
            AuthenticationResultType authResult = authService.loginUser(email, password);
            
            Map<String, Object> response = new HashMap<>();
            response.put("idToken", authResult.idToken());
            response.put("accessToken", authResult.accessToken());
            response.put("refreshToken", authResult.refreshToken());
            response.put("expiresIn", authResult.expiresIn());
            response.put("tokenType", authResult.tokenType());
            
            return createSuccessResponse(response);
        } catch (IllegalArgumentException | JsonSyntaxException e) {
            logger.warn("Invalid signin request: {}", e.getMessage());
            return createErrorResponse(400, e.getMessage());
        } catch (CognitoIdentityProviderException e) {
            logger.error("Cognito error during signin: {}", e.getMessage());
            return createErrorResponse(401, "Authentication failed: " + e.getMessage());
        }
    }
    
    /**
     * Handles the OAuth callback from social identity providers.
     * This is called when a user is redirected back from a social login provider.
     */
    private APIGatewayProxyResponseEvent handleOAuthCallback(APIGatewayProxyRequestEvent request) {
        Map<String, String> queryParams = request.getQueryStringParameters();
        if (queryParams == null || !queryParams.containsKey("code")) {
            return createErrorResponse(400, "Missing authorization code");
        }
        
        String code = queryParams.get("code");
        String state = queryParams.getOrDefault("state", "");
        
        try {
            // Exchange authorization code for tokens
            AuthenticationResultType authResult = authService.exchangeCodeForTokens(code);
            
            // Get user information from the token
            Map<String, String> userAttributes = authService.getUserInfo(authResult.accessToken());
            
            // Check if this is a federated user
            if (authService.isFederatedUser(userAttributes)) {
                // Sync the federated user's profile data to our Player table
                String userId = userAttributes.getOrDefault("sub", "");
                String email = userAttributes.getOrDefault("email", "");
                String name = userAttributes.getOrDefault("name", "");
                
                Optional<String> providerOpt = authService.getIdentityProvider(userAttributes);
                String provider = providerOpt.orElse("Unknown");
                
                // Call PlayerService to create or update player record with social profile data
                playerService.syncFederatedUserToPlayer(userId, email, name, provider, userAttributes);
                
                logger.info("Synced federated user profile from {} for user ID {}", provider, userId);
            }
            
            // Build a success response with tokens to be used by the frontend
            // In a real application, you would probably redirect to a frontend URL with the tokens
            Map<String, Object> response = new HashMap<>();
            response.put("idToken", authResult.idToken());
            response.put("accessToken", authResult.accessToken());
            response.put("refreshToken", authResult.refreshToken());
            response.put("expiresIn", authResult.expiresIn());
            response.put("tokenType", authResult.tokenType());
            response.put("state", state); // Return the state value for the client to verify
            
            return createSuccessResponse(response);
        } catch (CognitoIdentityProviderException e) {
            logger.error("Error exchanging OAuth code for tokens: {}", e.getMessage());
            return createErrorResponse(400, "OAuth authentication failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during OAuth callback handling", e);
            return createErrorResponse(500, "Internal server error during OAuth processing");
        }
    }
    
    /**
     * Handles direct token exchange for OAuth providers.
     * Used when a client has already obtained a token from a provider (e.g., Google, Facebook).
     */
    private APIGatewayProxyResponseEvent handleOAuthToken(APIGatewayProxyRequestEvent request) {
        try {
            JsonObject requestBody = JsonParser.parseString(request.getBody()).getAsJsonObject();
            
            String providerName = getRequiredField(requestBody, "provider");
            String idToken = getRequiredField(requestBody, "token");
            
            // Call the federated login method
            AuthenticationResultType authResult = authService.federatedLogin(providerName, idToken);
            
            // Get user information from the token
            Map<String, String> userAttributes = authService.getUserInfo(authResult.accessToken());
            
            // Sync the federated user's profile data to our Player table
            String userId = userAttributes.getOrDefault("sub", "");
            String email = userAttributes.getOrDefault("email", "");
            String name = userAttributes.getOrDefault("name", "");
            
            // Call PlayerService to create or update player record with social profile data
            playerService.syncFederatedUserToPlayer(userId, email, name, providerName, userAttributes);
            
            logger.info("Synced federated user profile from {} for user ID {}", providerName, userId);
            
            // Return tokens to the client
            Map<String, Object> response = new HashMap<>();
            response.put("idToken", authResult.idToken());
            response.put("accessToken", authResult.accessToken());
            response.put("refreshToken", authResult.refreshToken());
            response.put("expiresIn", authResult.expiresIn());
            response.put("tokenType", authResult.tokenType());
            
            return createSuccessResponse(response);
        } catch (IllegalArgumentException | JsonSyntaxException e) {
            logger.warn("Invalid OAuth token request: {}", e.getMessage());
            return createErrorResponse(400, e.getMessage());
        } catch (CognitoIdentityProviderException e) {
            logger.error("Cognito error during OAuth token exchange: {}", e.getMessage());
            return createErrorResponse(401, "Authentication failed: " + e.getMessage());
        }
    }
    
    /**
     * Returns the login URL for a specific OAuth provider.
     */
    private APIGatewayProxyResponseEvent getOAuthUrl(APIGatewayProxyRequestEvent request) {
        Map<String, String> queryParams = request.getQueryStringParameters();
        if (queryParams == null || !queryParams.containsKey("provider")) {
            return createErrorResponse(400, "Missing provider parameter");
        }
        
        String provider = queryParams.get("provider");
        String redirectUri = queryParams.getOrDefault("redirect_uri", "");
        String state = queryParams.getOrDefault("state", "");
        
        try {
            String loginUrl = authService.getProviderLoginUrl(provider, redirectUri, state);
            
            Map<String, String> response = new HashMap<>();
            response.put("loginUrl", loginUrl);
            
            return createSuccessResponse(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid OAuth URL request: {}", e.getMessage());
            return createErrorResponse(400, e.getMessage());
        } catch (Exception e) {
            logger.error("Error generating OAuth URL: {}", e.getMessage());
            return createErrorResponse(500, "Error generating OAuth URL: " + e.getMessage());
        }
    }
    
    /**
     * Creates a successful response with the given body.
     */
    private APIGatewayProxyResponseEvent createSuccessResponse(Object body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*"); // Should be restricted in production
        headers.put("Access-Control-Allow-Credentials", "true");
        
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(headers)
                .withBody(gson.toJson(body));
    }
    
    /**
     * Creates an error response with CORS headers.
     */
    private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String message) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*"); // Should be restricted in production
        headers.put("Access-Control-Allow-Credentials", "true");
        
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("message", message);
        
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(gson.toJson(errorBody));
    }
    
    /**
     * Extracts a required field from a JSON object.
     */
    private String getRequiredField(JsonObject json, String fieldName) {
        if (!json.has(fieldName) || json.get(fieldName).isJsonNull()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return json.get(fieldName).getAsString();
    }
} 