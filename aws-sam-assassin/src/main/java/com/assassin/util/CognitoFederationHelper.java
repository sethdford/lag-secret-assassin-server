package com.assassin.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utility class for handling federated authentication through Cognito.
 * Supports social login providers like Google, Facebook, and Apple.
 */
public class CognitoFederationHelper {
    private static final Logger logger = LoggerFactory.getLogger(CognitoFederationHelper.class);
    
    private final CognitoIdentityProviderClient cognitoClient;
    
    /**
     * Constructor with Cognito client.
     * 
     * @param cognitoClient The Cognito Identity Provider client
     */
    public CognitoFederationHelper(CognitoIdentityProviderClient cognitoClient) {
        this.cognitoClient = cognitoClient;
    }
    
    /**
     * Retrieves user attributes from Cognito using an access token.
     * Works for both direct Cognito users and federated users.
     * 
     * @param accessToken The JWT access token from the user's session
     * @return Map of user attributes
     * @throws CognitoIdentityProviderException If there's an error with the Cognito API
     */
    public Map<String, String> getUserAttributes(String accessToken) {
        try {
            GetUserRequest request = GetUserRequest.builder()
                    .accessToken(accessToken)
                    .build();
            
            GetUserResponse response = cognitoClient.getUser(request);
            List<AttributeType> attributes = response.userAttributes();
            
            Map<String, String> attributeMap = new HashMap<>();
            for (AttributeType attribute : attributes) {
                attributeMap.put(attribute.name(), attribute.value());
            }
            
            // Add the username which is separate from the attributes
            attributeMap.put("username", response.username());
            
            // Log identity provider if present
            if (attributeMap.containsKey("identities")) {
                logger.info("User authenticated via federation: {}", attributeMap.get("identities"));
            }
            
            return attributeMap;
        } catch (CognitoIdentityProviderException e) {
            logger.error("Error retrieving user attributes: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Determines if the user authenticated through a federated provider.
     * 
     * @param userAttributes Map of user attributes
     * @return true if the user is federated, false otherwise
     */
    public boolean isUserFederated(Map<String, String> userAttributes) {
        return userAttributes.containsKey("identities");
    }
    
    /**
     * Extracts the identity provider name from user attributes.
     * 
     * @param userAttributes Map of user attributes
     * @return Optional containing the provider name if user is federated, empty otherwise
     */
    public Optional<String> getIdentityProvider(Map<String, String> userAttributes) {
        if (!isUserFederated(userAttributes)) {
            return Optional.empty();
        }
        
        String identities = userAttributes.get("identities");
        // Parse the identity information which is stored as a JSON string
        // This is a simplified approach - in production you would use a JSON parser
        if (identities.contains("\"providerName\":\"Google\"")) {
            return Optional.of("Google");
        } else if (identities.contains("\"providerName\":\"Facebook\"")) {
            return Optional.of("Facebook");
        } else if (identities.contains("\"providerName\":\"SignInWithApple\"")) {
            return Optional.of("Apple");
        } else {
            return Optional.of("Unknown");
        }
    }
    
    /**
     * Extracts the provider-specific user ID from the identities attribute.
     * 
     * @param userAttributes Map of user attributes
     * @return Optional containing the provider user ID if available, empty otherwise
     */
    public Optional<String> getProviderUserId(Map<String, String> userAttributes) {
        if (!isUserFederated(userAttributes)) {
            return Optional.empty();
        }
        
        // This is a simplified approach - in production you would use a JSON parser
        String identities = userAttributes.get("identities");
        int startIdx = identities.indexOf("\"userId\":\"") + 10;
        if (startIdx < 10) {
            return Optional.empty();
        }
        
        int endIdx = identities.indexOf("\"", startIdx);
        if (endIdx > startIdx) {
            return Optional.of(identities.substring(startIdx, endIdx));
        }
        
        return Optional.empty();
    }
} 