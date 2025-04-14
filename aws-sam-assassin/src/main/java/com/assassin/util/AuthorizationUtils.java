package com.assassin.util;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for handling authorization, primarily JWT validation.
 */
public class AuthorizationUtils {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationUtils.class);

    private final String awsRegion;
    private final String userPoolId;
    private final String audience; // Typically the Cognito App Client ID
    private final JwkProvider jwkProvider;
    private final String issuer;

    /**
     * Constructor initializes the utility with necessary Cognito configuration.
     * Expects environment variables: AWS_REGION, COGNITO_USER_POOL_ID, COGNITO_CLIENT_ID
     */
    public AuthorizationUtils() {
        this.awsRegion = System.getenv("AWS_REGION"); // Ensure this is set in your Lambda environment
        this.userPoolId = System.getenv("COGNITO_USER_POOL_ID");
        this.audience = System.getenv("COGNITO_CLIENT_ID");

        if (awsRegion == null || userPoolId == null || audience == null) {
            logger.error("Missing required environment variables for JWT validation: AWS_REGION, COGNITO_USER_POOL_ID, COGNITO_CLIENT_ID");
            throw new IllegalStateException("Missing required Cognito configuration for JWT validation.");
        }

        this.issuer = String.format("https://cognito-idp.%s.amazonaws.com/%s", awsRegion, userPoolId);
        this.jwkProvider = buildCachingJwkProvider(this.issuer);
        logger.info("AuthorizationUtils initialized for issuer: {} and audience: {}", this.issuer, this.audience);
    }

    /**
     * Constructor for explicit configuration (useful for testing).
     * @param awsRegion AWS Region
     * @param userPoolId Cognito User Pool ID
     * @param audience Cognito App Client ID
     */
    public AuthorizationUtils(String awsRegion, String userPoolId, String audience) {
        this.awsRegion = awsRegion;
        this.userPoolId = userPoolId;
        this.audience = audience;

        if (awsRegion == null || userPoolId == null || audience == null) {
             throw new IllegalArgumentException("Region, User Pool ID, and Audience cannot be null.");
        }

        this.issuer = String.format("https://cognito-idp.%s.amazonaws.com/%s", awsRegion, userPoolId);
        this.jwkProvider = buildCachingJwkProvider(this.issuer);
         logger.info("AuthorizationUtils initialized for issuer: {} and audience: {}", this.issuer, this.audience);
    }

    private JwkProvider buildCachingJwkProvider(String issuerUrl) {
        try {
            URL jwksUrl = new URL(issuerUrl + "/.well-known/jwks.json");
            // Build a provider that caches JWKs for 10 hours and limits rate
            return new JwkProviderBuilder(jwksUrl)
                    .cached(10, 24, TimeUnit.HOURS) // Cache up to 10 JWKs for 24 hours
                    .rateLimited(10, 1, TimeUnit.MINUTES) // Allow max 10 requests per minute (overall)
                    .build();
        } catch (MalformedURLException e) {
            logger.error("Invalid JWKS URL derived from issuer: {}", issuerUrl, e);
            throw new IllegalStateException("Could not build JWK provider due to invalid URL", e);
        }
    }

    /**
     * Validates a JWT token (typically an IdToken or AccessToken from Cognito)
     * and returns the decoded claims if validation is successful.
     *
     * Checks signature, expiration, issuer, and audience.
     *
     * @param token The JWT token string.
     * @return DecodedJWT containing the token's claims.
     * @throws JWTVerificationException If the token is invalid (bad signature, expired, wrong issuer/audience, etc.)
     * @throws JwkException If the public key cannot be retrieved.
     */
    public DecodedJWT validateAndDecodeToken(String token) throws JwkException, JWTVerificationException {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty.");
        }

        // Decode without verification first to get the Key ID (kid)
        DecodedJWT jwt = JWT.decode(token);
        String keyId = jwt.getKeyId();
        if (keyId == null) {
             throw new JWTVerificationException("Token does not contain kid header");
        }

        // Get the JWK (public key) from the provider using the kid
        Jwk jwk = jwkProvider.get(keyId);

        // Build the RSA Algorithm using the public key
        Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null); // Assuming RSA keys

        // Build the verifier
        JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer(this.issuer)
                .withAudience(this.audience) // Verify 'aud' claim matches the App Client ID
                // .acceptLeeway(1) // Optional: Account for clock skew (in seconds)
                .build();

        // Verify the token's signature and claims (exp, iss, aud)
        DecodedJWT verifiedJwt = verifier.verify(token);
        logger.debug("Successfully validated token for user: {}", verifiedJwt.getSubject());
        return verifiedJwt;
    }

    /**
     * Extracts the user ID (subject) from a validated JWT.
     *
     * @param validatedToken A DecodedJWT that has already been successfully validated.
     * @return The user ID (subject claim).
     * @throws NullPointerException if the validatedToken is null.
     * @throws IllegalArgumentException if the subject claim is missing.
     */
    public String getUserIdFromToken(DecodedJWT validatedToken) {
         if (validatedToken == null) {
            throw new NullPointerException("Validated token cannot be null.");
        }
        String userId = validatedToken.getSubject();
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("Validated token is missing subject (sub) claim.");
        }
        return userId;
    }

    /**
     * Extracts the Cognito groups from a validated JWT (typically IdToken).
     *
     * @param validatedToken A DecodedJWT that has already been successfully validated.
     * @return A list of group names the user belongs to, or an empty list if not found.
     */
    public List<String> getUserGroups(DecodedJWT validatedToken) {
        if (validatedToken == null) {
            logger.warn("Cannot extract groups from a null token.");
            return Collections.emptyList();
        }
        try {
            // Retrieve the claim. It might be null if the user has no groups or the claim isn't in the token.
            List<String> groups = validatedToken.getClaim("cognito:groups").asList(String.class);
            return groups != null ? groups : Collections.emptyList();
        } catch (Exception e) {
            // Handle cases where the claim exists but is not a list of strings
            logger.error("Error parsing 'cognito:groups' claim from token for subject {}: {}", validatedToken.getSubject(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Checks if the user associated with the validated token belongs to the specified required role (Cognito group).
     *
     * @param validatedToken A DecodedJWT that has already been successfully validated.
     * @param requiredRole The name of the Cognito group required for access.
     * @return true if the user belongs to the required group, false otherwise.
     */
    public boolean hasRequiredRole(DecodedJWT validatedToken, String requiredRole) {
        if (requiredRole == null || requiredRole.trim().isEmpty()) {
            logger.warn("Required role cannot be null or empty for check.");
            return false; // Or throw IllegalArgumentException based on policy
        }
        List<String> userGroups = getUserGroups(validatedToken);
        boolean hasRole = userGroups.contains(requiredRole);
        if (hasRole) {
            logger.debug("User {} has required role: {}", validatedToken.getSubject(), requiredRole);
        } else {
             logger.debug("User {} does NOT have required role: {}. User groups: {}", validatedToken.getSubject(), requiredRole, userGroups);
        }
        return hasRole;
    }
} 