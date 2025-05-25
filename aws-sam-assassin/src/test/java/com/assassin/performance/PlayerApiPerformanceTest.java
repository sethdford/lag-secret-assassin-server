package com.assassin.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.PlayerDao;
import com.assassin.handlers.PlayerHandler;
import com.assassin.integration.TestContext;
import com.assassin.model.Player;
import com.assassin.service.PlayerService;
import com.assassin.util.DynamoDbClientProvider;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Performance testing for Player API.
 * Simulates concurrent user load and measures response times.
 * 
 * Note: Tagged as "performance" so these tests can be excluded from regular CI runs
 * using Maven profiles or JUnit filtering.
 */
@Tag("performance")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "docker.available", matches = "true", disabledReason = "Docker not available")
public class PlayerApiPerformanceTest {

    private static final Logger logger = LoggerFactory.getLogger(PlayerApiPerformanceTest.class);
    private static final String TEST_TABLE_NAME = "perf-test-players";
    private static final int CONCURRENT_REQUESTS = 50;
    private static final int PERFORMANCE_THRESHOLD_MS = 1500; // Max acceptable average response time
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.5.0"))
            .withServices(DYNAMODB);
    
    private DynamoDbClient ddbClient;
    private DynamoDbEnhancedClient enhancedClient;
    private DynamoDbTable<Player> playerTable;
    private PlayerDao playerDao;
    private PlayerService playerService;
    private PlayerHandler playerHandler;
    private Gson gson;
    private Context mockContext;
    
    @BeforeAll
    void setup() {
        System.setProperty("PLAYERS_TABLE_NAME", TEST_TABLE_NAME);
        
        // Set up DynamoDB client pointing to LocalStack
        ddbClient = DynamoDbClient.builder()
                .endpointOverride(localstack.getEndpointOverride(DYNAMODB))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .build();
        
        // Override the client provider for testing
        DynamoDbClientProvider.overrideClient(ddbClient);
        
        // Create the test table with higher provisioned throughput for performance testing
        createPlayersTable();
        
        // Set up enhanced client and table
        enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(ddbClient)
                .build();
        
        playerTable = enhancedClient.table(TEST_TABLE_NAME, TableSchema.fromBean(Player.class));
        
        // Initialize DAO, Service and Handler
        playerDao = new DynamoDbPlayerDao();
        playerService = new PlayerService(playerDao);
        playerHandler = new PlayerHandler(playerDao, playerService);
        
        // Initialize Gson and context
        gson = new GsonBuilder().setPrettyPrinting().create();
        mockContext = new TestContext();
        
        // Pre-populate table with some data
        populateTestData(100);
    }
    
    @AfterAll
    void cleanup() {
        DynamoDbClientProvider.resetClient();
        System.clearProperty("PLAYERS_TABLE_NAME");
        
        if (ddbClient != null) {
            ddbClient.close();
        }
    }
    
    private void createPlayersTable() {
        try {
            // Define key attributes
            AttributeDefinition playerIdDef = AttributeDefinition.builder()
                    .attributeName("PlayerID")
                    .attributeType(ScalarAttributeType.S)
                    .build();
            
            KeySchemaElement playerIdKey = KeySchemaElement.builder()
                    .attributeName("PlayerID")
                    .keyType(KeyType.HASH)
                    .build();
            
            // Define attributes for GameIdIndex
            AttributeDefinition gameIdDef = AttributeDefinition.builder()
                    .attributeName("GameID")
                    .attributeType(ScalarAttributeType.S)
                    .build();

            KeySchemaElement gameIdKey = KeySchemaElement.builder()
                    .attributeName("GameID")
                    .keyType(KeyType.HASH)
                    .build();

            GlobalSecondaryIndex gameIdIndex = GlobalSecondaryIndex.builder()
                    .indexName("GameIdIndex")
                    .keySchema(gameIdKey)
                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                    .build();
            
            // Create table with higher throughput for performance testing
            CreateTableRequest createTableRequest = CreateTableRequest.builder()
                    .tableName(TEST_TABLE_NAME)
                    .keySchema(playerIdKey)
                    .attributeDefinitions(playerIdDef, gameIdDef) // Add gameIdDef
                    .globalSecondaryIndexes(gameIdIndex) // Add the GSI
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build();
            
            ddbClient.createTable(createTableRequest);
            ddbClient.waiter().waitUntilTableExists(builder -> builder.tableName(TEST_TABLE_NAME));
            logger.info("Created performance test table with high provisioned throughput");
        } catch (ResourceInUseException e) {
            logger.info("Performance test table already exists, continuing");
        }
    }
    
    private void populateTestData(int count) {
        logger.info("Populating test data with {} players", count);
        
        for (int i = 0; i < count; i++) {
            Player player = new Player();
            player.setPlayerID("perf-player-" + i);
            player.setPlayerName("Performance Test Player " + i);
            player.setEmail("perf-test-" + i + "@example.com");
            
            playerDao.savePlayer(player);
        }
        
        logger.info("Test data population complete");
    }
    
    @Test
    void testConcurrentGetAllPlayers() throws Exception {
        logger.info("Starting concurrent GET /players performance test with {} concurrent requests", CONCURRENT_REQUESTS);
        
        // Create executor service for concurrent requests
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        
        // Performance metrics
        List<Long> responseTimes = new ArrayList<>();
        
        // Create and submit tasks
        List<CompletableFuture<Long>> futures = IntStream.range(0, CONCURRENT_REQUESTS)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    long startTime = System.currentTimeMillis();
                    
                    // Create request
                    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                            .withPath("/players")
                            .withHttpMethod("GET");
                    
                    // Execute handler
                    APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
                    
                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;
                    
                    logger.debug("Request {} completed in {} ms with status {}", 
                            i, duration, response.getStatusCode());
                    
                    return duration;
                }, executor))
                .collect(Collectors.toList());
        
        // Wait for all futures to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Collect response times
        for (CompletableFuture<Long> future : futures) {
            responseTimes.add(future.get());
        }
        
        // Calculate metrics
        double averageResponseTime = responseTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        
        long maxResponseTime = responseTimes.stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);
        
        long minResponseTime = responseTimes.stream()
                .mapToLong(Long::longValue)
                .min()
                .orElse(0);
        
        logger.info("Performance test results:");
        logger.info("Average response time: {} ms", averageResponseTime);
        logger.info("Min response time: {} ms", minResponseTime);
        logger.info("Max response time: {} ms", maxResponseTime);
        
        // Assert performance threshold
        assertTrue(averageResponseTime < PERFORMANCE_THRESHOLD_MS,
                String.format("Average response time (%.2f ms) exceeds threshold (%d ms)",
                        averageResponseTime, PERFORMANCE_THRESHOLD_MS));
        
        // Shut down executor
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
    }
    
    @Test
    void testConcurrentCreatePlayer() throws Exception {
        logger.info("Starting concurrent POST /players performance test with {} concurrent requests", CONCURRENT_REQUESTS);
        
        // Create executor service for concurrent requests
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        
        // Performance metrics
        List<Long> responseTimes = new ArrayList<>();
        
        // Create and submit tasks
        List<CompletableFuture<Long>> futures = IntStream.range(0, CONCURRENT_REQUESTS)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    long startTime = System.currentTimeMillis();
                    
                    // Create a unique player for each request
                    String uniqueId = UUID.randomUUID().toString();
                    Player player = new Player();
                    player.setPlayerID("perf-create-" + uniqueId);
                    player.setPlayerName("Create Performance Test " + i);
                    player.setEmail("create-perf-" + uniqueId + "@example.com");
                    
                    // Create request
                    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                            .withPath("/players")
                            .withHttpMethod("POST")
                            .withBody(gson.toJson(player));
                    
                    // Execute handler
                    APIGatewayProxyResponseEvent response = playerHandler.handleRequest(request, mockContext);
                    
                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;
                    
                    logger.debug("Request {} completed in {} ms with status {}", 
                            i, duration, response.getStatusCode());
                    
                    return duration;
                }, executor))
                .collect(Collectors.toList());
        
        // Wait for all futures to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Collect response times
        for (CompletableFuture<Long> future : futures) {
            responseTimes.add(future.get());
        }
        
        // Calculate metrics
        double averageResponseTime = responseTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        
        long maxResponseTime = responseTimes.stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);
        
        long minResponseTime = responseTimes.stream()
                .mapToLong(Long::longValue)
                .min()
                .orElse(0);
        
        logger.info("Performance test results:");
        logger.info("Average response time: {} ms", averageResponseTime);
        logger.info("Min response time: {} ms", minResponseTime);
        logger.info("Max response time: {} ms", maxResponseTime);
        
        // Assert performance threshold (allow slightly higher for writes)
        int writeThreshold = PERFORMANCE_THRESHOLD_MS * 2;
        assertTrue(averageResponseTime < writeThreshold,
                String.format("Average response time (%.2f ms) exceeds threshold (%d ms)",
                        averageResponseTime, writeThreshold));
        
        // Shut down executor
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
    }
} 