package com.assassin.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.util.AuthorizationUtils;
import com.assassin.util.CognitoFederationHelper;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;

// imports for calculateSecretHash if uncommented
// import javax.crypto.Mac;
// import javax.crypto.spec.SecretKeySpec;
// import java.nio.charset.StandardCharsets;
// import java.util.Base64;

/**
 * Service for authentication operations using Amazon Cognito.
 * Supports both standard username/password authentication and federated OAuth 2.0 logins.
 */
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    
    private final CognitoIdentityProviderClient cognitoClient;
    private final String userPoolId;
    private final String clientId;
    private final String clientSecret;
    private final AuthorizationUtils authorizationUtils;
    private final CognitoFederationHelper federationHelper;

    /**
     * Default constructor.
     */
    public AuthService() {
        this.cognitoClient = CognitoIdentityProviderClient.builder().build();
        // These would typically come from environment variables
        this.userPoolId = System.getenv("COGNITO_USER_POOL_ID");
        this.clientId = System.getenv("COGNITO_CLIENT_ID");
        this.clientSecret = System.getenv("COGNITO_CLIENT_SECRET");
        
        this.authorizationUtils = new AuthorizationUtils();
        this.federationHelper = new CognitoFederationHelper(this.cognitoClient);
    }

    /**
     * Constructor with dependency injection for testability.
     * 
     * @param cognitoClient The Cognito client
     * @param userPoolId The Cognito user pool ID
     * @param clientId The Cognito client ID
     * @param clientSecret The Cognito client secret
     */
    public AuthService(CognitoIdentityProviderClient cognitoClient, String userPoolId, String clientId, String clientSecret) {
        this.cognitoClient = cognitoClient;
        this.userPoolId = userPoolId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        
        this.authorizationUtils = new AuthorizationUtils(
            System.getenv("AWS_REGION") != null ? System.getenv("AWS_REGION") : "us-east-1",
            userPoolId,
            clientId
        );
        this.federationHelper = new CognitoFederationHelper(this.cognitoClient);
    }

    /**
     * Authenticates a user with Cognito using the Initiate Auth flow, typically with SRP.
     * This is the standard flow for client-side authentication where the password is not sent directly.
     *
     * @param email The user's email address (username).
     * @param password The user's password.
     * @return AuthenticationResultType containing JWT tokens if successful.
     * @throws CognitoIdentityProviderException if authentication fails (e.g., incorrect password, user not found, challenge required).
     * @throws IllegalArgumentException if email or password are empty.
     */
    public AuthenticationResultType loginUser(String email, String password) throws CognitoIdentityProviderException {
        if (email == null || email.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Email and password cannot be empty.");
        }

        Map<String, String> authParameters = new HashMap<>();
        authParameters.put("USERNAME", email);
        authParameters.put("PASSWORD", password); // For USER_SRP_AUTH, Cognito SDK handles the SRP calculations
        // Note: SECRET_HASH is generally not needed for USER_SRP_AUTH unless the client is configured with a secret
        // and the specific Cognito setup requires it. Check Cognito App Client settings.

        InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.USER_SRP_AUTH) // Use Secure Remote Password protocol
                .clientId(this.clientId) 
                // userPoolId is implicitly handled by the client configuration unless overridden
                .authParameters(authParameters)
                .build();

        try {
            logger.info("Attempting USER_SRP_AUTH authentication for user with email: {}", email);
            // Use initiateAuth instead of adminInitiateAuth
            InitiateAuthResponse response = cognitoClient.initiateAuth(authRequest);
            
            // Check for challenges (e.g., MFA, new password required) - Simplified for now
            if (response.challengeName() != null) {
                 logger.warn("Authentication for {} requires challenge: {}. Challenge handling not fully implemented.", email, response.challengeNameAsString());
                 // Production code would need to handle challenges like NEW_PASSWORD_REQUIRED, MFA_SETUP, etc.
                 // This might involve returning challenge info to the client or handling specific challenges server-side.
                 // For simplicity here, we'll throw an exception indicating a challenge is pending.
                 throw CognitoIdentityProviderException.builder()
                     .message("Authentication challenge required: " + response.challengeNameAsString())
                     .build();
            }

            AuthenticationResultType authResult = response.authenticationResult();
            if (authResult != null) {
                logger.info("Successfully authenticated user: {}", email);
                logger.debug("Received tokens: IdToken={}, AccessToken={}, RefreshToken={}",
                    authResult.idToken() != null, authResult.accessToken() != null, authResult.refreshToken() != null);
            } else {
                // In USER_SRP_AUTH, a null authResult without a challenge usually indicates an issue.
                logger.error("Authentication completed for {} using USER_SRP_AUTH but no auth result or challenge returned. This indicates a potential configuration issue or unexpected Cognito response.", email);
                throw CognitoIdentityProviderException.builder()
                    .message("Authentication failed: No authentication result or challenge returned from Cognito.")
                    .build();
            }
            return authResult; 
        } catch (CognitoIdentityProviderException e) {
            logger.error("Cognito USER_SRP_AUTH login failed for email {}: {}", email, e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage(), e);
            // Map common exceptions if needed
            throw e;
        }
    }
    
    /**
     * Registers a new user in Cognito.
     *
     * @param email User's email address (will be used as username)
     * @param password User's password
     * @param name User's full name
     * @param additionalAttributes Optional additional attributes to store with the user
     * @return The user sub (unique identifier) from Cognito
     * @throws CognitoIdentityProviderException if registration fails
     */
    public String registerUser(String email, String password, String name, Map<String, String> additionalAttributes) 
            throws CognitoIdentityProviderException {
        if (email == null || email.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Email and password cannot be empty.");
        }

        List<AttributeType> attributes = new ArrayList<>();
        attributes.add(AttributeType.builder().name("email").value(email).build());
        attributes.add(AttributeType.builder().name("email_verified").value("true").build());
        attributes.add(AttributeType.builder().name("name").value(name).build());
        
        // Add any additional attributes
        if (additionalAttributes != null) {
            for (Map.Entry<String, String> entry : additionalAttributes.entrySet()) {
                attributes.add(AttributeType.builder().name(entry.getKey()).value(entry.getValue()).build());
            }
        }

        SignUpRequest signUpRequest = SignUpRequest.builder()
                .clientId(clientId)
                .username(email)
                .password(password)
                .userAttributes(attributes)
                .build();

        try {
            logger.info("Attempting to register new user with email: {}", email);
            SignUpResponse response = cognitoClient.signUp(signUpRequest);
            logger.info("Successfully registered user with sub: {}, confirmed: {}", 
                    response.userSub(), response.userConfirmed());
            return response.userSub();
        } catch (CognitoIdentityProviderException e) {
            logger.error("User registration failed for email {}: {}", email, e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Confirms a user's registration using the confirmation code sent to their email.
     *
     * @param email User's email address
     * @param confirmationCode Confirmation code sent to the user's email
     * @return true if confirmation was successful
     * @throws CognitoIdentityProviderException if confirmation fails
     */
    public boolean confirmUserRegistration(String email, String confirmationCode) 
            throws CognitoIdentityProviderException {
        if (email == null || email.trim().isEmpty() || confirmationCode == null || confirmationCode.isEmpty()) {
            throw new IllegalArgumentException("Email and confirmation code cannot be empty.");
        }

        ConfirmSignUpRequest confirmRequest = ConfirmSignUpRequest.builder()
                .clientId(clientId)
                .username(email)
                .confirmationCode(confirmationCode)
                .build();

        try {
            logger.info("Attempting to confirm registration for user: {}", email);
            ConfirmSignUpResponse response = cognitoClient.confirmSignUp(confirmRequest);
            logger.info("Successfully confirmed user registration: {}", email);
            return true;
        } catch (CognitoIdentityProviderException e) {
            logger.error("User confirmation failed for email {}: {}", email, e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Initiates authentication with a token received from a social identity provider.
     * This method handles the token exchange with Cognito when a user logs in via a federated provider.
     *
     * @param providerName The name of the identity provider (e.g., "Google", "Facebook", "Apple")
     * @param idToken The ID token received from the identity provider
     * @return Authentication result containing Cognito tokens
     * @throws CognitoIdentityProviderException if authentication fails
     */
    public AuthenticationResultType federatedLogin(String providerName, String idToken) 
            throws CognitoIdentityProviderException {
        if (providerName == null || providerName.trim().isEmpty() || idToken == null || idToken.isEmpty()) {
            throw new IllegalArgumentException("Provider name and ID token cannot be empty.");
        }

        Map<String, String> authParams = new HashMap<>();
        authParams.put("TOKEN", idToken);
        authParams.put("PROVIDER_ID", providerName);

        InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.CUSTOM_AUTH)
                .clientId(clientId)
                .authParameters(authParams)
                .build();

        try {
            logger.info("Attempting federated authentication with provider: {}", providerName);
            InitiateAuthResponse response = cognitoClient.initiateAuth(authRequest);
            AuthenticationResultType authResult = response.authenticationResult();
            
            if (authResult != null) {
                logger.info("Successfully authenticated user via federation with provider: {}", providerName);
            } else {
                logger.warn("Federated authentication completed but no auth result returned");
            }
            
            return authResult;
        } catch (CognitoIdentityProviderException e) {
            logger.error("Federated authentication failed with provider {}: {}", 
                    providerName, e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Gets user information from Cognito using an access token.
     * Works for both directly authenticated and federated users.
     *
     * @param accessToken The access token from the user's session
     * @return Map of user attributes
     * @throws CognitoIdentityProviderException if retrieval fails
     */
    public Map<String, String> getUserInfo(String accessToken) throws CognitoIdentityProviderException {
        return federationHelper.getUserAttributes(accessToken);
    }
    
    /**
     * Determines if a user authenticated through a federated provider.
     *
     * @param userAttributes User attributes retrieved from Cognito
     * @return true if the user authenticated via federation, false otherwise
     */
    public boolean isFederatedUser(Map<String, String> userAttributes) {
        return federationHelper.isUserFederated(userAttributes);
    }
    
    /**
     * Gets the identity provider for a federated user.
     *
     * @param userAttributes User attributes retrieved from Cognito
     * @return Optional containing the provider name if federated, empty otherwise
     */
    public Optional<String> getIdentityProvider(Map<String, String> userAttributes) {
        return federationHelper.getIdentityProvider(userAttributes);
    }

    /**
     * Exchanges an authorization code for tokens using Cognito's AUTHORIZATION_CODE flow.
     * This is used after a user is redirected back from an OAuth provider's login page.
     *
     * @param authorizationCode The authorization code from the OAuth callback
     * @return Authentication result containing Cognito tokens
     * @throws CognitoIdentityProviderException if token exchange fails
     */
    public AuthenticationResultType exchangeCodeForTokens(String authorizationCode) 
            throws CognitoIdentityProviderException {
        if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Authorization code cannot be empty.");
        }

        Map<String, String> authParams = new HashMap<>();
        authParams.put("GRANT_TYPE", "authorization_code");
        authParams.put("CODE", authorizationCode);
        // Redirecting URI is required for AUTHORIZATION_CODE flow but is matched against the one configured in Cognito
        authParams.put("REDIRECT_URI", System.getenv("COGNITO_REDIRECT_URI"));

        InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH) // Use USER_PASSWORD_AUTH for authorization code flow
                .clientId(clientId)
                .authParameters(authParams)
                .build();

        try {
            logger.info("Exchanging authorization code for tokens");
            InitiateAuthResponse response = cognitoClient.initiateAuth(authRequest);
            AuthenticationResultType authResult = response.authenticationResult();
            
            if (authResult != null) {
                logger.info("Successfully exchanged authorization code for tokens");
            } else {
                logger.warn("Authorization code exchange completed but no auth result returned");
            }
            
            return authResult;
        } catch (CognitoIdentityProviderException e) {
            logger.error("Authorization code exchange failed: {}", e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Generates a URL for redirecting users to the appropriate OAuth provider login page.
     * The URL is constructed based on the Cognito Hosted UI and the specified provider.
     *
     * @param providerName The identity provider name (e.g., "Google", "Facebook", "Apple")
     * @param redirectUri The URI to redirect to after authentication (must be configured in Cognito)
     * @param state Optional state parameter for CSRF protection
     * @return The login URL for the specified provider
     * @throws IllegalArgumentException if providerName is invalid
     */
    public String getProviderLoginUrl(String providerName, String redirectUri, String state) {
        if (providerName == null || providerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider name cannot be empty.");
        }
        
        // Validate provider name
        String normalizedProvider = providerName.toLowerCase();
        if (!normalizedProvider.equals("google") && 
            !normalizedProvider.equals("facebook") && 
            !normalizedProvider.equals("apple")) {
            throw new IllegalArgumentException("Unsupported provider: " + providerName);
        }
        
        // Fallback to configured redirect URI if none provided
        String effectiveRedirectUri = (redirectUri != null && !redirectUri.isEmpty()) 
                ? redirectUri 
                : System.getenv("COGNITO_REDIRECT_URI");
        
        if (effectiveRedirectUri == null || effectiveRedirectUri.isEmpty()) {
            throw new IllegalArgumentException("Redirect URI not configured.");
        }

        // Domain name from environment variable or construct from user pool ID
        String domain = System.getenv("COGNITO_DOMAIN");
        if (domain == null || domain.isEmpty()) {
            String region = System.getenv("AWS_REGION") != null ? System.getenv("AWS_REGION") : "us-east-1";
            domain = String.format("%s.auth.%s.amazoncognito.com", userPoolId, region);
        }
        
        // Build the login URL for the Cognito hosted UI with the specified identity provider
        StringBuilder urlBuilder = new StringBuilder()
                .append("https://").append(domain)
                .append("/oauth2/authorize?")
                .append("identity_provider=").append(normalizedProvider.substring(0, 1).toUpperCase())
                .append(normalizedProvider.substring(1))  // Capitalize first letter (e.g., "google" -> "Google")
                .append("&response_type=code")
                .append("&client_id=").append(clientId)
                .append("&redirect_uri=").append(encode(effectiveRedirectUri))
                .append("&scope=openid+email+profile");
        
        // Add state parameter if provided (for CSRF protection)
        if (state != null && !state.isEmpty()) {
            urlBuilder.append("&state=").append(encode(state));
        }
        
        String loginUrl = urlBuilder.toString();
        logger.debug("Generated login URL for provider {}: {}", providerName, loginUrl);
        return loginUrl;
    }
    
    /**
     * URL encodes a string for use in URL parameters.
     */
    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is always supported
            return value;
        }
    }

    // TODO: Implement secret hash calculation if Cognito client has a secret
    // ... existing calculateSecretHash method ...
} 