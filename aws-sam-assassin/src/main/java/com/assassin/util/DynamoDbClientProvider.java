package com.assassin.util;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Provides singleton instances of DynamoDB clients.
 * Handles basic configuration like region and endpoint override for local testing.
 */
public class DynamoDbClientProvider {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbClientProvider.class);
    private static final String DYNAMODB_ENDPOINT_OVERRIDE = System.getenv("DYNAMODB_ENDPOINT_OVERRIDE");
    private static final String AWS_REGION = System.getenv("AWS_REGION"); // SAM injects this by default

    private static volatile DynamoDbClient dynamoDbClientInstance;
    private static volatile DynamoDbEnhancedClient dynamoDbEnhancedClientInstance;
    private static volatile DynamoDbClient overrideClient; // Added for testing
    private static volatile DynamoDbEnhancedClient overriddenEnhancedClient;

    // Private constructor to prevent instantiation
    private DynamoDbClientProvider() {}

    /**
     * Gets the singleton DynamoDbClient instance.
     * This is the standard V2 client.
     *
     * @return DynamoDbClient instance.
     */
    public static DynamoDbClient getClient() { // Renamed from getDynamoDbClient for clarity
        if (overrideClient != null) { // Return override if set
            logger.debug("Returning overridden DynamoDbClient instance.");
            return overrideClient;
        }
        if (dynamoDbClientInstance == null) {
            synchronized (DynamoDbClientProvider.class) {
                if (dynamoDbClientInstance == null) {
                    logger.info("Initializing standard DynamoDbClient instance.");
                    dynamoDbClientInstance = buildDynamoDbClient();
                }
            }
        }
        return dynamoDbClientInstance;
    }

    /**
     * Gets the singleton DynamoDbEnhancedClient instance.
     *
     * @return DynamoDbEnhancedClient instance.
     */
    public static DynamoDbEnhancedClient getDynamoDbEnhancedClient() {
        if (overriddenEnhancedClient != null) {
            return overriddenEnhancedClient;
        }

        if (dynamoDbEnhancedClientInstance == null) {
            synchronized (DynamoDbClientProvider.class) {
                if (dynamoDbEnhancedClientInstance == null) {
                    logger.info("Initializing DynamoDbEnhancedClient instance.");
                    dynamoDbEnhancedClientInstance = DynamoDbEnhancedClient.builder()
                            .dynamoDbClient(getClient()) // Use getClient() to ensure initialization
                            .build();
                }
            }
        }
        return dynamoDbEnhancedClientInstance;
    }

    /**
     * Alias for getDynamoDbEnhancedClient() to maintain compatibility with both naming conventions
     * @return the DynamoDbEnhancedClient instance
     */
    public static DynamoDbEnhancedClient getEnhancedClient() {
        return getDynamoDbEnhancedClient();
    }

    private static DynamoDbClient buildDynamoDbClient() {
        var clientBuilder = DynamoDbClient.builder()
                .httpClient(UrlConnectionHttpClient.builder().build())
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create());

        if (AWS_REGION != null && !AWS_REGION.isEmpty()) {
            clientBuilder.region(Region.of(AWS_REGION));
            logger.info("Configuring DynamoDB client for region: {}", AWS_REGION);
        } else {
             logger.warn("AWS_REGION environment variable not set. Using SDK default region resolution.");
        }

        if (DYNAMODB_ENDPOINT_OVERRIDE != null && !DYNAMODB_ENDPOINT_OVERRIDE.isEmpty()) {
            logger.warn("Overriding DynamoDB endpoint: {}", DYNAMODB_ENDPOINT_OVERRIDE);
            clientBuilder.endpointOverride(URI.create(DYNAMODB_ENDPOINT_OVERRIDE));
        }

        return clientBuilder.build();
    }

    /**
     * Overrides the singleton client instance for testing purposes.
     * USE WITH CAUTION in tests only.
     *
     * @param client The mock or test client to use.
     */
    public static void overrideClient(DynamoDbClient client) {
        logger.warn("!!! Overriding DynamoDbClientProvider with a test client !!!");
        overrideClient = client;
        dynamoDbClientInstance = null;
        dynamoDbEnhancedClientInstance = null;
    }

    /**
     * Resets the client provider, removing any test override.
     * Call this in @AfterAll or test teardown.
     */
    public static void resetClient() {
         logger.warn("!!! Resetting DynamoDbClientProvider !!!");
        overrideClient = null;
        dynamoDbClientInstance = null;
        dynamoDbEnhancedClientInstance = null;
    }
} 