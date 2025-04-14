package com.assassin.e2e;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbKillDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.GameDao;
import com.assassin.dao.KillDao;
import com.assassin.dao.PlayerDao;
import com.assassin.handlers.KillHandler;
import com.assassin.handlers.PlayerHandler;
import com.assassin.integration.TestContext;
import com.assassin.model.Game;
import com.assassin.model.Kill;
import com.assassin.model.Player;
import com.assassin.service.KillService;
import com.assassin.service.NotificationService;
import com.assassin.service.verification.VerificationManager;
import com.assassin.util.DynamoDbClientProvider;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

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
 * End-to-end test that simulates a complete game flow from player creation
 * to recording kills, verifying the entire system works together.
 */
@Tag("e2e")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
public class GameFlowEndToEndTest {

    private static final Logger logger = LoggerFactory.getLogger(GameFlowEndToEndTest.class);
    private static final String PLAYERS_TABLE_NAME = "e2e-test-players";
    private static final String KILLS_TABLE_NAME = "e2e-test-kills";
    private static final String GAMES_TABLE_NAME = "e2e-test-games";
    private static final String E2E_SAFE_ZONES_TABLE_NAME = "e2e-test-safezones";
    private static final String E2E_NOTIFICATIONS_TABLE_NAME = "e2e-test-notifications";
    
    // Test data
    private static final String PLAYER_1_ID = "e2e-player-1-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String PLAYER_2_ID = "e2e-player-2-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String PLAYER_3_ID = "e2e-player-3-" + UUID.randomUUID().toString().substring(0, 8);
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.5.0"))
            .withServices(DYNAMODB);
    
    private DynamoDbClient ddbClient;
    private DynamoDbEnhancedClient enhancedClient;
    private DynamoDbTable<Player> playerTable;
    private DynamoDbTable<Kill> killTable;
    private PlayerDao playerDao;
    private KillDao killDao;
    private GameDao gameDao;
    private PlayerHandler playerHandler;
    private KillHandler killHandler;
    private KillService killService;
    private Gson gson;
    private Context mockContext;
    
    @BeforeAll
    void setup() {
        System.setProperty("PLAYERS_TABLE_NAME", PLAYERS_TABLE_NAME);
        System.setProperty("KILLS_TABLE_NAME", KILLS_TABLE_NAME);
        System.setProperty("GAMES_TABLE_NAME", "e2e-test-games");
        System.setProperty("ASSASSIN_TEST_MODE", "true");
        System.setProperty("SAFE_ZONES_TABLE_NAME", E2E_SAFE_ZONES_TABLE_NAME);
        System.setProperty("NOTIFICATIONS_TABLE_NAME", E2E_NOTIFICATIONS_TABLE_NAME);
        
        // Set up DynamoDB client pointing to LocalStack
        ddbClient = DynamoDbClient.builder()
                .endpointOverride(localstack.getEndpointOverride(DYNAMODB))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .build();
        
        // Override the client provider for testing
        DynamoDbClientProvider.overrideClient(ddbClient);
        
        // Create the test tables
        createPlayersTable();
        createKillsTable();
        createGamesTable();
        createSafeZonesTable();
        createNotificationsTable();
        
        // Add a small delay to allow LocalStack GSI to potentially become ready
        try {
            logger.info("Waiting briefly for LocalStack GSI initialization...");
            Thread.sleep(1000); // 1 second delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for LocalStack setup.");
        }
        
        // Set up enhanced client and tables (after potential delay)
        enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(ddbClient)
                .build();
        
        playerTable = enhancedClient.table(PLAYERS_TABLE_NAME, TableSchema.fromBean(Player.class));
        killTable = enhancedClient.table(KILLS_TABLE_NAME, TableSchema.fromBean(Kill.class));
        
        // Initialize DAOs, Services, and Handlers
        playerDao = new DynamoDbPlayerDao();
        killDao = new DynamoDbKillDao();
        gameDao = new DynamoDbGameDao();
        NotificationService notificationService = new NotificationService();
        VerificationManager verificationManager = new VerificationManager(playerDao, gameDao); // Use DAOs created above
        // Instantiate KillService using the constructor that accepts the enhancedClient
        killService = new KillService(killDao, playerDao, gameDao, notificationService, verificationManager, enhancedClient);
        
        playerHandler = new PlayerHandler(playerDao);
        killHandler = new KillHandler(killService);
        
        // Initialize Gson and context
        gson = new GsonBuilder().setPrettyPrinting().create();
        mockContext = new TestContext();
        
        logger.info("E2E test environment setup complete");
    }
    
    @AfterAll
    void cleanup() {
        DynamoDbClientProvider.resetClient();
        System.clearProperty("PLAYERS_TABLE_NAME");
        System.clearProperty("KILLS_TABLE_NAME");
        System.clearProperty("GAMES_TABLE_NAME");
        System.clearProperty("ASSASSIN_TEST_MODE");
        System.clearProperty("SAFE_ZONES_TABLE_NAME");
        System.clearProperty("NOTIFICATIONS_TABLE_NAME");
        
        if (ddbClient != null) {
            ddbClient.close();
        }
    }
    
    private void createPlayersTable() {
        try {
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
            
            CreateTableRequest createTableRequest = CreateTableRequest.builder()
                    .tableName(PLAYERS_TABLE_NAME)
                    .keySchema(playerIdKey)
                    .attributeDefinitions(playerIdDef, gameIdDef) // Add gameIdDef
                    .globalSecondaryIndexes(gameIdIndex) // Add the GSI
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build();
            
            ddbClient.createTable(createTableRequest);
            ddbClient.waiter().waitUntilTableExists(builder -> builder.tableName(PLAYERS_TABLE_NAME));
            logger.info("Created players table for E2E test");
        } catch (ResourceInUseException e) {
            logger.info("Players table for E2E test already exists, continuing");
        }
    }
    
    private void createKillsTable() {
        try {
            // Define primary key attributes
            AttributeDefinition killerIdDef = AttributeDefinition.builder()
                    .attributeName("KillerID")
                    .attributeType(ScalarAttributeType.S)
                    .build();
            
            AttributeDefinition timeDef = AttributeDefinition.builder()
                    .attributeName("Time")
                    .attributeType(ScalarAttributeType.S)
                    .build();
            
            // Define GSI attributes
            AttributeDefinition victimIdDef = AttributeDefinition.builder()
                    .attributeName("VictimID")
                    .attributeType(ScalarAttributeType.S)
                    .build();
            
            // Define GameID attribute for the GameID-Time-index
            AttributeDefinition gameIdDef = AttributeDefinition.builder()
                    .attributeName("GameID")
                    .attributeType(ScalarAttributeType.S)
                    .build();
            
            // Define attributes for the StatusTimeIndex GSI
            AttributeDefinition statusPartitionDef = AttributeDefinition.builder()
                    .attributeName("KillStatusPartition") // Matches Kill model field
                    .attributeType(ScalarAttributeType.S)
                    .build();
            
            // Define primary key schema
            KeySchemaElement killerIdKey = KeySchemaElement.builder()
                    .attributeName("KillerID")
                    .keyType(KeyType.HASH)
                    .build();
            
            KeySchemaElement timeKey = KeySchemaElement.builder()
                    .attributeName("Time")
                    .keyType(KeyType.RANGE)
                    .build();
            
            // Define GSI key schema
            KeySchemaElement victimIdKey = KeySchemaElement.builder()
                    .attributeName("VictimID")
                    .keyType(KeyType.HASH)
                    .build();
            
            KeySchemaElement gsiTimeKey = KeySchemaElement.builder()
                    .attributeName("Time")
                    .keyType(KeyType.RANGE)
                    .build();
            
            // Define Game GSI key schema
            KeySchemaElement gameIdKey = KeySchemaElement.builder()
                    .attributeName("GameID")
                    .keyType(KeyType.HASH)
                    .build();
            
            KeySchemaElement gameTimeKey = KeySchemaElement.builder()
                    .attributeName("Time")
                    .keyType(KeyType.RANGE)
                    .build();
            
            // Define StatusTimeIndex GSI key schema
            KeySchemaElement statusPartitionKey = KeySchemaElement.builder()
                    .attributeName("KillStatusPartition")
                    .keyType(KeyType.HASH)
                    .build();
            KeySchemaElement statusTimeKey = KeySchemaElement.builder()
                    .attributeName("Time")
                    .keyType(KeyType.RANGE)
                    .build();
            
            // Define GSI projection (what attributes to include)
            Projection projection = Projection.builder()
                    .projectionType(ProjectionType.ALL) // Include all attributes
                    .build();
            
            // Define the Victim GSI
            GlobalSecondaryIndex victimIndex = GlobalSecondaryIndex.builder()
                    .indexName("VictimID-Time-index")
                    .keySchema(victimIdKey, gsiTimeKey)
                    .projection(projection)
                    .build();
            
            // Define the Game GSI
            GlobalSecondaryIndex gameIndex = GlobalSecondaryIndex.builder()
                    .indexName("GameID-Time-index")
                    .keySchema(gameIdKey, gameTimeKey)
                    .projection(projection)
                    .build();
            
            // Define the StatusTimeIndex GSI
            GlobalSecondaryIndex statusTimeIndex = GlobalSecondaryIndex.builder()
                    .indexName("StatusTimeIndex") // Name expected by the DAO
                    .keySchema(statusPartitionKey, statusTimeKey)
                    .projection(projection) 
                    .build();
            
            // Build the create table request
            CreateTableRequest createTableRequest = CreateTableRequest.builder()
                    .tableName(KILLS_TABLE_NAME)
                    .keySchema(killerIdKey, timeKey)
                    .attributeDefinitions(killerIdDef, timeDef, victimIdDef, gameIdDef, statusPartitionDef) // Added statusPartitionDef
                    .globalSecondaryIndexes(victimIndex, gameIndex, statusTimeIndex) // Added statusTimeIndex
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build();
            
            ddbClient.createTable(createTableRequest);
            ddbClient.waiter().waitUntilTableExists(builder -> builder.tableName(KILLS_TABLE_NAME));
            logger.info("Created kills table for E2E test with GSIs: VictimID-Time-index, GameID-Time-index, StatusTimeIndex");
        } catch (ResourceInUseException e) {
            logger.info("Kills table for E2E test already exists, continuing");
        }
    }
    
    private void createGamesTable() {
        try {
            AttributeDefinition gameIdDef = AttributeDefinition.builder()
                    .attributeName("GameID") // Matches Game model @DynamoDbPartitionKey
                    .attributeType(ScalarAttributeType.S)
                    .build();
            
            KeySchemaElement gameIdKey = KeySchemaElement.builder()
                    .attributeName("GameID")
                    .keyType(KeyType.HASH)
                    .build();
            
            // Define attributes for the GSI (StatusCreatedAtIndex)
            AttributeDefinition statusDef = AttributeDefinition.builder()
                    .attributeName("Status") // Matches Game model @DynamoDbSecondaryPartitionKey
                    .attributeType(ScalarAttributeType.S)
                    .build();
            
            AttributeDefinition createdAtDef = AttributeDefinition.builder()
                    .attributeName("CreatedAt") // Matches Game model @DynamoDbSecondarySortKey
                    .attributeType(ScalarAttributeType.S) 
                    .build();
            
            // Define GSI key schema
            KeySchemaElement statusKey = KeySchemaElement.builder()
                    .attributeName("Status")
                    .keyType(KeyType.HASH)
                    .build();
            KeySchemaElement createdAtKey = KeySchemaElement.builder()
                    .attributeName("CreatedAt")
                    .keyType(KeyType.RANGE)
                    .build();
                    
            // Define the GSI projection
            Projection projection = Projection.builder()
                    .projectionType(ProjectionType.ALL)
                    .build();
                    
            // Define the GSI
            GlobalSecondaryIndex statusIndex = GlobalSecondaryIndex.builder()
                    .indexName("StatusCreatedAtIndex") // Matches constant in GameDao
                    .keySchema(statusKey, createdAtKey)
                    .projection(projection)
                    .build();

            String tableName = "e2e-test-games"; // Use a consistent name 
            
            CreateTableRequest createTableRequest = CreateTableRequest.builder()
                    .tableName(tableName)
                    .keySchema(gameIdKey)
                    .attributeDefinitions(gameIdDef, statusDef, createdAtDef) // Include all key attributes
                    .globalSecondaryIndexes(statusIndex)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build();
            
            ddbClient.createTable(createTableRequest);
            ddbClient.waiter().waitUntilTableExists(builder -> builder.tableName(tableName));
            logger.info("Created games table for E2E test: {}", tableName);
        } catch (ResourceInUseException e) {
            logger.info("Games table for E2E test already exists, continuing");
        }
    }
    
    private void createSafeZonesTable() {
        try {
            AttributeDefinition safeZoneIdDef = AttributeDefinition.builder()
                    .attributeName("SafeZoneID") // Matches SafeZone model @DynamoDbPartitionKey
                    .attributeType(ScalarAttributeType.S)
                    .build();
                    
            // Define attribute for the GameIdIndex GSI
            AttributeDefinition gameIdDef = AttributeDefinition.builder()
                    .attributeName("GameID") // Matches the attribute used in the index
                    .attributeType(ScalarAttributeType.S)
                    .build();
            
            KeySchemaElement safeZoneIdKey = KeySchemaElement.builder()
                    .attributeName("SafeZoneID")
                    .keyType(KeyType.HASH)
                    .build();
            
            // Define GSI key schema
            KeySchemaElement gameIdKey = KeySchemaElement.builder()
                    .attributeName("GameID")
                    .keyType(KeyType.HASH)
                    .build();
                    
            // Define the GSI projection (include all attributes)
            Projection projection = Projection.builder()
                    .projectionType(ProjectionType.ALL)
                    .build();
                    
            // Define the GSI
            GlobalSecondaryIndex gameIdIndex = GlobalSecondaryIndex.builder()
                    .indexName("GameIdIndex") // Name expected by the DAO
                    .keySchema(gameIdKey)
                    .projection(projection)
                    .build();
                    
            CreateTableRequest createTableRequest = CreateTableRequest.builder()
                    .tableName(E2E_SAFE_ZONES_TABLE_NAME)
                    .keySchema(safeZoneIdKey)
                    .attributeDefinitions(safeZoneIdDef, gameIdDef) // Include both attribute definitions
                    .globalSecondaryIndexes(gameIdIndex) // Add the GSI to the request
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build();
            
            ddbClient.createTable(createTableRequest);
            ddbClient.waiter().waitUntilTableExists(builder -> builder.tableName(E2E_SAFE_ZONES_TABLE_NAME));
            logger.info("Created safe zones table for E2E test: {}", E2E_SAFE_ZONES_TABLE_NAME);
        } catch (ResourceInUseException e) {
            logger.info("Safe zones table for E2E test already exists, continuing");
        }
    }
    
    private void createNotificationsTable() {
        try {
            AttributeDefinition recipientIdDef = AttributeDefinition.builder()
                    .attributeName("RecipientPlayerID") // Matches Notification model @DynamoDbAttribute
                    .attributeType(ScalarAttributeType.S)
                    .build();
            AttributeDefinition timestampDef = AttributeDefinition.builder()
                    .attributeName("Timestamp") // Matches Notification model @DynamoDbAttribute
                    .attributeType(ScalarAttributeType.S)
                    .build();
            
            KeySchemaElement recipientIdKey = KeySchemaElement.builder()
                    .attributeName("RecipientPlayerID")
                    .keyType(KeyType.HASH)
                    .build();
            KeySchemaElement timestampKey = KeySchemaElement.builder()
                    .attributeName("Timestamp")
                    .keyType(KeyType.RANGE)
                    .build();
                    
            CreateTableRequest createTableRequest = CreateTableRequest.builder()
                    .tableName(E2E_NOTIFICATIONS_TABLE_NAME)
                    .keySchema(recipientIdKey, timestampKey)
                    .attributeDefinitions(recipientIdDef, timestampDef)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build();
            
            ddbClient.createTable(createTableRequest);
            ddbClient.waiter().waitUntilTableExists(builder -> builder.tableName(E2E_NOTIFICATIONS_TABLE_NAME));
            logger.info("Created notifications table for E2E test: {}", E2E_NOTIFICATIONS_TABLE_NAME);
        } catch (ResourceInUseException e) {
            logger.info("Notifications table for E2E test already exists, continuing");
        }
    }
    
    @Test
    @Order(1)
    void testCreatePlayers() {
        logger.info("E2E Test Step 1: Creating players");
        
        // Create Player 1
        Player player1 = new Player();
        player1.setPlayerID(PLAYER_1_ID);
        player1.setPlayerName("E2E Test Player 1");
        player1.setEmail("e2e-player1@example.com");
        
        APIGatewayProxyRequestEvent request1 = new APIGatewayProxyRequestEvent()
                .withPath("/players")
                .withHttpMethod("POST")
                .withBody(gson.toJson(player1));
        
        APIGatewayProxyResponseEvent response1 = playerHandler.handleRequest(request1, mockContext);
        assertEquals(201, response1.getStatusCode());
        
        // Create Player 2
        Player player2 = new Player();
        player2.setPlayerID(PLAYER_2_ID);
        player2.setPlayerName("E2E Test Player 2");
        player2.setEmail("e2e-player2@example.com");
        
        APIGatewayProxyRequestEvent request2 = new APIGatewayProxyRequestEvent()
                .withPath("/players")
                .withHttpMethod("POST")
                .withBody(gson.toJson(player2));
        
        APIGatewayProxyResponseEvent response2 = playerHandler.handleRequest(request2, mockContext);
        assertEquals(201, response2.getStatusCode());
        
        // Create Player 3
        Player player3 = new Player();
        player3.setPlayerID(PLAYER_3_ID);
        player3.setPlayerName("E2E Test Player 3");
        player3.setEmail("e2e-player3@example.com");
        
        APIGatewayProxyRequestEvent request3 = new APIGatewayProxyRequestEvent()
                .withPath("/players")
                .withHttpMethod("POST")
                .withBody(gson.toJson(player3));
        
        APIGatewayProxyResponseEvent response3 = playerHandler.handleRequest(request3, mockContext);
        assertEquals(201, response3.getStatusCode());
        
        // Verify all players were created by getting all players
        APIGatewayProxyRequestEvent getAllRequest = new APIGatewayProxyRequestEvent()
                .withPath("/players")
                .withHttpMethod("GET");
        
        APIGatewayProxyResponseEvent getAllResponse = playerHandler.handleRequest(getAllRequest, mockContext);
        assertEquals(200, getAllResponse.getStatusCode());
        
        List<Player> allPlayers = gson.fromJson(getAllResponse.getBody(), 
                new TypeToken<List<Player>>(){}.getType());
        
        assertTrue(allPlayers.size() >= 3);
        assertTrue(allPlayers.stream().anyMatch(p -> p.getPlayerID().equals(PLAYER_1_ID)));
        assertTrue(allPlayers.stream().anyMatch(p -> p.getPlayerID().equals(PLAYER_2_ID)));
        assertTrue(allPlayers.stream().anyMatch(p -> p.getPlayerID().equals(PLAYER_3_ID)));
        
        logger.info("Successfully created and verified 3 test players");
    }
    
    @Test
    @Order(2)
    void testGetIndividualPlayers() {
        logger.info("E2E Test Step 2: Retrieving individual players");
        
        // Get Player 1
        APIGatewayProxyRequestEvent request1 = new APIGatewayProxyRequestEvent()
                .withPath("/players/" + PLAYER_1_ID)
                .withHttpMethod("GET")
                .withPathParameters(java.util.Map.of("playerID", PLAYER_1_ID));
        
        APIGatewayProxyResponseEvent response1 = playerHandler.handleRequest(request1, mockContext);
        assertEquals(200, response1.getStatusCode());
        
        Player player1 = gson.fromJson(response1.getBody(), Player.class);
        assertEquals(PLAYER_1_ID, player1.getPlayerID());
        assertEquals("E2E Test Player 1", player1.getPlayerName());
        
        // Get Player 2
        APIGatewayProxyRequestEvent request2 = new APIGatewayProxyRequestEvent()
                .withPath("/players/" + PLAYER_2_ID)
                .withHttpMethod("GET")
                .withPathParameters(java.util.Map.of("playerID", PLAYER_2_ID));
        
        APIGatewayProxyResponseEvent response2 = playerHandler.handleRequest(request2, mockContext);
        assertEquals(200, response2.getStatusCode());
        
        Player player2 = gson.fromJson(response2.getBody(), Player.class);
        assertEquals(PLAYER_2_ID, player2.getPlayerID());
        assertEquals("E2E Test Player 2", player2.getPlayerName());
        
        logger.info("Successfully retrieved individual players");
    }
    
    @Test
    @Order(3)
    void testRecordKill() {
        logger.info("E2E Test Step 3: Recording a kill");
        
        // Player 1 kills Player 2
        // Construct the request body, including verification details
        // For this test, let's assume GPS verification is used.
        Map<String, Object> killRequestBody = new HashMap<>();
        killRequestBody.put("killerID", PLAYER_1_ID);
        killRequestBody.put("victimID", PLAYER_2_ID);
        // killRequestBody.put("time", Instant.now().toString()); // Time is set by service
        killRequestBody.put("latitude", 40.7128);
        killRequestBody.put("longitude", -74.0060);
        killRequestBody.put("verificationMethod", "GPS");
        // Include initial data for verification (e.g., killer's coords at time of report)
        Map<String, String> verificationData = new HashMap<>();
        verificationData.put("killerLatitude", "40.7129"); // Slightly different coords
        verificationData.put("killerLongitude", "-74.0061");
        killRequestBody.put("verificationData", verificationData);
        
        // Create a mock request context with authorizer
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", PLAYER_1_ID);
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/die")  // Match the path in KillHandler
                .withHttpMethod("POST")
                .withRequestContext(requestContext)
                .withBody(gson.toJson(killRequestBody)); // Use the Map for the body
        
        APIGatewayProxyResponseEvent response = killHandler.handleRequest(request, mockContext);
        assertEquals(201, response.getStatusCode());
        
        Kill savedKill = gson.fromJson(response.getBody(), Kill.class);
        assertNotNull(savedKill.getKillerID());
        assertNotNull(savedKill.getVictimID());
        assertNotNull(savedKill.getTime());
        
        logger.info("Successfully recorded a kill: {} killed {}", PLAYER_1_ID, PLAYER_2_ID);
    }
    
    @Test
    @Order(4)
    void testGetKillsByKiller() {
        logger.info("E2E Test Step 4: Retrieving kills by killer");
        
        // Create a mock request context with authorizer
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", PLAYER_1_ID);
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/kills/killer/" + PLAYER_1_ID)
                .withHttpMethod("GET")
                .withRequestContext(requestContext)
                .withPathParameters(java.util.Map.of("killerID", PLAYER_1_ID));
        
        APIGatewayProxyResponseEvent response = killHandler.handleRequest(request, mockContext);
        assertEquals(200, response.getStatusCode());

        // Refactored Deserialization
        List<Map<String, Object>> killsData = gson.fromJson(response.getBody(),
                new TypeToken<List<Map<String, Object>>>(){}.getType());
        assertEquals(1, killsData.size());
        assertEquals(PLAYER_1_ID, killsData.get(0).get("killerID"));
        assertEquals(PLAYER_2_ID, killsData.get(0).get("victimID"));

        logger.info("Successfully retrieved kills by killer");
    }
    
    @Test
    @Order(5)
    void testRecordAnotherKill() {
        logger.info("E2E Test Step 5: Recording another kill");
        
        // Player 1 kills Player 3
        // Use NFC verification this time
        Map<String, Object> killRequestBody = new HashMap<>();
        killRequestBody.put("killerID", PLAYER_1_ID);
        killRequestBody.put("victimID", PLAYER_3_ID);
        killRequestBody.put("latitude", 40.7128);
        killRequestBody.put("longitude", -74.0060);
        killRequestBody.put("verificationMethod", "NFC");
        // For NFC, we might not provide verificationData initially
        // It would come during the verifyKill step
        killRequestBody.put("verificationData", new HashMap<>()); // Empty map
        
        // Create a mock request context with authorizer
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", PLAYER_1_ID);
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/die")  // Match the path in KillHandler
                .withHttpMethod("POST")
                .withRequestContext(requestContext)
                .withBody(gson.toJson(killRequestBody)); // Use map for body
        
        APIGatewayProxyResponseEvent response = killHandler.handleRequest(request, mockContext);
        assertEquals(201, response.getStatusCode());
        
        Kill savedKill = gson.fromJson(response.getBody(), Kill.class);
        assertNotNull(savedKill.getKillerID());
        assertNotNull(savedKill.getVictimID());
        assertNotNull(savedKill.getTime());
        
        logger.info("Successfully recorded another kill: {} killed {}", PLAYER_1_ID, PLAYER_3_ID);
    }
    
    @Test
    @Order(6)
    void testGetKillsByVictim() {
        logger.info("E2E Test Step 6: Retrieving kills by victim");
        
        // Create a mock request context with authorizer
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", PLAYER_1_ID);
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/kills/victim/" + PLAYER_3_ID)
                .withHttpMethod("GET")
                .withRequestContext(requestContext)
                .withPathParameters(java.util.Map.of("victimID", PLAYER_3_ID));
        
        APIGatewayProxyResponseEvent response = killHandler.handleRequest(request, mockContext);
        assertEquals(200, response.getStatusCode());

        // Refactored Deserialization
        List<Map<String, Object>> killsData = gson.fromJson(response.getBody(),
             new TypeToken<List<Map<String, Object>>>(){}.getType());
        assertEquals(1, killsData.size());
        assertEquals(PLAYER_1_ID, killsData.get(0).get("killerID"));
        assertEquals(PLAYER_3_ID, killsData.get(0).get("victimID"));

        logger.info("Successfully retrieved kills by victim");
    }
    
    @Test
    @Order(7)
    void testKillerStats() {
        logger.info("E2E Test Step 7: Checking killer stats");
        
        // Create a mock request context with authorizer
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", PLAYER_1_ID);
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        
        // At this point, Player 1 should have 2 kills
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/kills/killer/" + PLAYER_1_ID)
                .withHttpMethod("GET")
                .withRequestContext(requestContext)
                .withPathParameters(java.util.Map.of("killerID", PLAYER_1_ID));
        
        APIGatewayProxyResponseEvent response = killHandler.handleRequest(request, mockContext);
        assertEquals(200, response.getStatusCode());

        // Refactored Deserialization
        List<Map<String, Object>> killsData = gson.fromJson(response.getBody(),
             new TypeToken<List<Map<String, Object>>>(){}.getType());
        assertEquals(2, killsData.size());

        logger.info("Verified that Player 1 has the expected number of kills: {}", killsData.size());
    }
    
    @Test
    @Order(8)
    void testUpdatePlayer() {
        logger.info("E2E Test Step 8: Updating a player");
        
        // Get Player 1 first
        APIGatewayProxyRequestEvent getRequest = new APIGatewayProxyRequestEvent()
                .withPath("/players/" + PLAYER_1_ID)
                .withHttpMethod("GET")
                .withPathParameters(java.util.Map.of("playerID", PLAYER_1_ID));
        
        APIGatewayProxyResponseEvent getResponse = playerHandler.handleRequest(getRequest, mockContext);
        assertEquals(200, getResponse.getStatusCode());
        
        Player player = gson.fromJson(getResponse.getBody(), Player.class);
        
        // Update the player
        player.setPlayerName("Updated E2E Test Player 1");
        
        APIGatewayProxyRequestEvent updateRequest = new APIGatewayProxyRequestEvent()
                .withPath("/players/" + PLAYER_1_ID)
                .withHttpMethod("PUT")
                .withPathParameters(java.util.Map.of("playerID", PLAYER_1_ID))
                .withBody(gson.toJson(player));
        
        APIGatewayProxyResponseEvent updateResponse = playerHandler.handleRequest(updateRequest, mockContext);
        assertEquals(200, updateResponse.getStatusCode());
        
        // Verify the update
        APIGatewayProxyRequestEvent verifyRequest = new APIGatewayProxyRequestEvent()
                .withPath("/players/" + PLAYER_1_ID)
                .withHttpMethod("GET")
                .withPathParameters(java.util.Map.of("playerID", PLAYER_1_ID));
        
        APIGatewayProxyResponseEvent verifyResponse = playerHandler.handleRequest(verifyRequest, mockContext);
        assertEquals(200, verifyResponse.getStatusCode());
        
        Player updatedPlayer = gson.fromJson(verifyResponse.getBody(), Player.class);
        assertEquals("Updated E2E Test Player 1", updatedPlayer.getPlayerName());
        
        logger.info("Successfully updated and verified player information");
    }

    @Test
    @Order(9)
    void testGpsKillVerification_Success() throws Exception {
        logger.info("E2E Test Step 9: Verifying a kill via GPS (Success Case)");

        // --- Setup: Create a Game with GPS threshold setting --- 
        Game testGame = new Game();
        testGame.setGameID("e2e-gps-game-1");
        testGame.setGameName("E2E GPS Test Game");
        testGame.setStatus("ACTIVE"); // Assume game is active for verification
        Map<String, Object> settings = new HashMap<>();
        settings.put("gpsVerificationThresholdMeters", 50.0); // Set threshold
        testGame.setSettings(settings);
        gameDao.saveGame(testGame); 
        
        // Ensure player 2 (victim) is associated with this game
        Player victim = playerDao.getPlayerById(PLAYER_2_ID).orElseThrow();
        victim.setGameID(testGame.getGameID());
        playerDao.savePlayer(victim);
        
        // --- Find the kill to verify (Player 1 killed Player 2) ---
        // We need the exact time from the kill record
        List<Kill> killsByKiller = killDao.findKillsByKiller(PLAYER_1_ID);
        Kill killToVerify = killsByKiller.stream()
                                       .filter(k -> k.getVictimID().equals(PLAYER_2_ID))
                                       .findFirst()
                                       .orElseThrow(() -> new AssertionError("Kill record not found!"));
        
        // Ensure the kill is pending verification (it should be by default from reportKill)
        assertEquals("PENDING", killToVerify.getVerificationStatus());

        // --- Prepare Verification Request --- 
        String killerId = killToVerify.getKillerID();
        String killTime = killToVerify.getTime();
        String verifierId = PLAYER_1_ID; // Assume killer verifies, or could be moderator

        Map<String, String> verificationInput = new HashMap<>();
        // Victim coords *within* threshold (e.g., 10m away from kill loc 40.7128, -74.0060)
        verificationInput.put("victimLatitude", "40.71285"); 
        verificationInput.put("victimLongitude", "-74.00605"); 

        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", verifierId); // Authenticate as the verifier
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        
        APIGatewayProxyRequestEvent verifyRequest = new APIGatewayProxyRequestEvent()
                .withPath("/kills/" + killerId + "/" + killTime + "/verify")
                .withHttpMethod("POST")
                .withRequestContext(requestContext)
                .withPathParameters(Map.of("killerId", killerId, "killTime", killTime))
                .withBody(gson.toJson(verificationInput));

        // --- Execute Verification --- 
        APIGatewayProxyResponseEvent verifyResponse = killHandler.handleRequest(verifyRequest, mockContext);
        
        // --- Assertions --- 
        assertEquals(200, verifyResponse.getStatusCode());
        Kill verifiedKill = gson.fromJson(verifyResponse.getBody(), Kill.class);
        
        assertNotNull(verifiedKill);
        assertEquals("VERIFIED", verifiedKill.getVerificationStatus());
        assertEquals("GPS", verifiedKill.getVerificationMethod());
        assertNotNull(verifiedKill.getVerificationNotes());
        
        logger.info("Successfully verified kill via GPS (within threshold)");
    }
    
    @Test
    @Order(10)
    void testGpsKillVerification_Failure() throws Exception {
        logger.info("E2E Test Step 10: Verifying a kill via GPS (Failure Case)");

        // --- Find the same kill again (it should now be VERIFIED from previous test) ---
        // We'll reset its status to PENDING for this test case
        List<Kill> killsByKiller = killDao.findKillsByKiller(PLAYER_1_ID);
        Kill killToVerify = killsByKiller.stream()
                                       .filter(k -> k.getVictimID().equals(PLAYER_2_ID))
                                       .findFirst()
                                       .orElseThrow(() -> new AssertionError("Kill record not found!"));
                                       
        killToVerify.setVerificationStatus("PENDING"); // Reset for test
        killDao.saveKill(killToVerify); 
        
        assertEquals("PENDING", killDao.getKill(killToVerify.getKillerID(), killToVerify.getTime()).get().getVerificationStatus()); // Verify reset
        
        // --- Prepare Verification Request --- 
        String killerId = killToVerify.getKillerID();
        String killTime = killToVerify.getTime();
        String verifierId = PLAYER_1_ID; 

        Map<String, String> verificationInput = new HashMap<>();
        // Victim coords *outside* threshold (e.g., 100m away from kill loc 40.7128, -74.0060)
        verificationInput.put("victimLatitude", "40.7135"); 
        verificationInput.put("victimLongitude", "-74.0070"); 

        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", verifierId);
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        
        APIGatewayProxyRequestEvent verifyRequest = new APIGatewayProxyRequestEvent()
                .withPath("/kills/" + killerId + "/" + killTime + "/verify")
                .withHttpMethod("POST")
                .withRequestContext(requestContext)
                .withPathParameters(Map.of("killerId", killerId, "killTime", killTime))
                .withBody(gson.toJson(verificationInput));

        // --- Execute Verification --- 
        APIGatewayProxyResponseEvent verifyResponse = killHandler.handleRequest(verifyRequest, mockContext);
        
        // --- Assertions --- 
        assertEquals(200, verifyResponse.getStatusCode()); // Still 200 OK, but status inside is REJECTED
        Kill rejectedKill = gson.fromJson(verifyResponse.getBody(), Kill.class);
        
        assertNotNull(rejectedKill);
        assertEquals("REJECTED", rejectedKill.getVerificationStatus());
        assertEquals("GPS", rejectedKill.getVerificationMethod());
        assertNotNull(rejectedKill.getVerificationNotes());
        
        logger.info("Successfully rejected kill via GPS (outside threshold)");
    }

    @Test
    @Order(11)
    void testNfcKillVerification_Success() throws Exception {
        logger.info("E2E Test Step 11: Verifying a kill via NFC (Success Case)");

        // --- Find the kill to verify (Player 1 killed Player 3, which used NFC) ---
        List<Kill> killsByKiller = killDao.findKillsByKiller(PLAYER_1_ID);
        Kill killToVerify = killsByKiller.stream()
                                       .filter(k -> k.getVictimID().equals(PLAYER_3_ID))
                                       .findFirst()
                                       .orElseThrow(() -> new AssertionError("Kill record not found!"));
        
        // Reset verification status for test
        killToVerify.setVerificationStatus("PENDING");
        killDao.saveKill(killToVerify);
        
        // --- Update victim with an NFC tag ID ---
        Player victim = playerDao.getPlayerById(PLAYER_3_ID).orElseThrow();
        String expectedNfcTagId = "nfc-tag-" + UUID.randomUUID().toString().substring(0, 8);
        victim.setNfcTagId(expectedNfcTagId);
        playerDao.savePlayer(victim);
        
        // --- Prepare Verification Request --- 
        String killerId = killToVerify.getKillerID();
        String killTime = killToVerify.getTime();
        String verifierId = PLAYER_1_ID; // Killer verifies

        Map<String, String> verificationInput = new HashMap<>();
        verificationInput.put("scannedNfcTagId", expectedNfcTagId); // Matching NFC tag ID

        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", verifierId);
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        
        APIGatewayProxyRequestEvent verifyRequest = new APIGatewayProxyRequestEvent()
                .withPath("/kills/" + killerId + "/" + killTime + "/verify")
                .withHttpMethod("POST")
                .withRequestContext(requestContext)
                .withPathParameters(Map.of("killerId", killerId, "killTime", killTime))
                .withBody(gson.toJson(verificationInput));

        // --- Execute Verification --- 
        APIGatewayProxyResponseEvent verifyResponse = killHandler.handleRequest(verifyRequest, mockContext);
        
        // --- Assertions --- 
        assertEquals(200, verifyResponse.getStatusCode());
        Kill verifiedKill = gson.fromJson(verifyResponse.getBody(), Kill.class);
        
        assertNotNull(verifiedKill);
        assertEquals("VERIFIED", verifiedKill.getVerificationStatus());
        assertEquals("NFC", verifiedKill.getVerificationMethod());
        assertNotNull(verifiedKill.getVerificationNotes());
        
        logger.info("Successfully verified kill via NFC (matching tag ID)");
    }
    
    @Test
    @Order(12)
    void testNfcKillVerification_Failure() throws Exception {
        logger.info("E2E Test Step 12: Verifying a kill via NFC (Failure Case)");

        // --- Find the kill again ---
        List<Kill> killsByKiller = killDao.findKillsByKiller(PLAYER_1_ID);
        Kill killToVerify = killsByKiller.stream()
                                       .filter(k -> k.getVictimID().equals(PLAYER_3_ID))
                                       .findFirst()
                                       .orElseThrow(() -> new AssertionError("Kill record not found!"));
        
        // Reset verification status for test
        killToVerify.setVerificationStatus("PENDING");
        killDao.saveKill(killToVerify);
        
        // --- Prepare Verification Request with incorrect NFC tag --- 
        String killerId = killToVerify.getKillerID();
        String killTime = killToVerify.getTime();
        String verifierId = PLAYER_1_ID;

        Map<String, String> verificationInput = new HashMap<>();
        verificationInput.put("scannedNfcTagId", "incorrect-nfc-tag-" + UUID.randomUUID()); // Wrong tag ID

        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", verifierId);
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        
        APIGatewayProxyRequestEvent verifyRequest = new APIGatewayProxyRequestEvent()
                .withPath("/kills/" + killerId + "/" + killTime + "/verify")
                .withHttpMethod("POST")
                .withRequestContext(requestContext)
                .withPathParameters(Map.of("killerId", killerId, "killTime", killTime))
                .withBody(gson.toJson(verificationInput));

        // --- Execute Verification --- 
        APIGatewayProxyResponseEvent verifyResponse = killHandler.handleRequest(verifyRequest, mockContext);
        
        // --- Assertions --- 
        assertEquals(200, verifyResponse.getStatusCode()); // Still 200 OK, but REJECTED inside
        Kill rejectedKill = gson.fromJson(verifyResponse.getBody(), Kill.class);
        
        assertNotNull(rejectedKill);
        assertEquals("REJECTED", rejectedKill.getVerificationStatus());
        assertEquals("NFC", rejectedKill.getVerificationMethod());
        assertNotNull(rejectedKill.getVerificationNotes());
        
        logger.info("Successfully rejected kill via NFC (non-matching tag ID)");
    }

    @Test
    @Order(13)
    void testPhotoKillVerification_Submission() {
        logger.info("E2E Test Step 13: Testing photo evidence verification submission");

        // For this test, let's create a new kill with PHOTO verification
        Map<String, Object> killRequestBody = new HashMap<>();
        killRequestBody.put("killerID", PLAYER_1_ID);
        killRequestBody.put("victimID", PLAYER_2_ID); // Reuse Player 2 (we reset state in each test)
        killRequestBody.put("latitude", 40.7128);
        killRequestBody.put("longitude", -74.0060);
        killRequestBody.put("verificationMethod", "PHOTO");
        Map<String, String> initialVerificationData = new HashMap<>();
        killRequestBody.put("verificationData", initialVerificationData);
        
        // Create request context with authorizer
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", PLAYER_1_ID);
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        
        // Create kill with PHOTO verification
        APIGatewayProxyRequestEvent createRequest = new APIGatewayProxyRequestEvent()
                .withPath("/die")
                .withHttpMethod("POST")
                .withRequestContext(requestContext)
                .withBody(gson.toJson(killRequestBody));
        
        APIGatewayProxyResponseEvent createResponse = killHandler.handleRequest(createRequest, mockContext);
        assertEquals(201, createResponse.getStatusCode());
        Kill photoKill = gson.fromJson(createResponse.getBody(), Kill.class);
        assertEquals("PHOTO", photoKill.getVerificationMethod());
        assertEquals("PENDING", photoKill.getVerificationStatus());
        
        // Now submit photo evidence for verification
        String photoUrl = "https://example.com/kill-photos/" + UUID.randomUUID() + ".jpg";
        Map<String, String> verificationInput = new HashMap<>();
        verificationInput.put("photoUrl", photoUrl);
        verificationInput.put("timestamp", Instant.now().toString());
        verificationInput.put("description", "Photo of the assassination scene");
        
        APIGatewayProxyRequestEvent verifyRequest = new APIGatewayProxyRequestEvent()
                .withPath("/kills/" + photoKill.getKillerID() + "/" + photoKill.getTime() + "/verify")
                .withHttpMethod("POST")
                .withRequestContext(requestContext)
                .withPathParameters(Map.of(
                    "killerId", photoKill.getKillerID(), 
                    "killTime", photoKill.getTime()))
                .withBody(gson.toJson(verificationInput));
        
        APIGatewayProxyResponseEvent verifyResponse = killHandler.handleRequest(verifyRequest, mockContext);
        
        // Assertions - should be PENDING_REVIEW status after photo submission
        assertEquals(200, verifyResponse.getStatusCode());
        Kill pendingReviewKill = gson.fromJson(verifyResponse.getBody(), Kill.class);
        assertEquals("PENDING_REVIEW", pendingReviewKill.getVerificationStatus());
        assertNotNull(pendingReviewKill.getVerificationNotes());
        
        logger.info("Successfully tested photo evidence submission, status now PENDING_REVIEW");
    }
    
    @Test
    @Order(14)
    void testPhotoKillVerification_ModeratorApproval() throws Exception {
        logger.info("E2E Test Step 14: Testing moderator approval of photo evidence");
        
        // Find the kill with PENDING_REVIEW status
        List<Kill> killsByKiller = killDao.findKillsByKiller(PLAYER_1_ID);
        Kill photoKill = killsByKiller.stream()
                .filter(k -> "PHOTO".equals(k.getVerificationMethod()) && "PENDING_REVIEW".equals(k.getVerificationStatus()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Photo kill record with PENDING_REVIEW status not found!"));
        
        // Create moderator account for this test (simple approach for test)
        String moderatorId = "moderator-" + UUID.randomUUID().toString().substring(0, 8);
        Player moderator = new Player();
        moderator.setPlayerID(moderatorId);
        moderator.setPlayerName("Game Moderator");
        moderator.setEmail("moderator@example.com");
        playerDao.savePlayer(moderator);
        
        // Simulate moderator approving the photo
        Map<String, String> moderatorInput = new HashMap<>();
        moderatorInput.put("moderatorAction", "APPROVE");
        moderatorInput.put("moderatorNotes", "Photo clearly shows the assassination");
        
        // Create request context with moderator authorizer
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", moderatorId); 
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        
        APIGatewayProxyRequestEvent moderatorRequest = new APIGatewayProxyRequestEvent()
                .withPath("/kills/" + photoKill.getKillerID() + "/" + photoKill.getTime() + "/verify")
                .withHttpMethod("POST")
                .withRequestContext(requestContext)
                .withPathParameters(Map.of(
                    "killerId", photoKill.getKillerID(), 
                    "killTime", photoKill.getTime()))
                .withBody(gson.toJson(moderatorInput));
        
        APIGatewayProxyResponseEvent moderatorResponse = killHandler.handleRequest(moderatorRequest, mockContext);
        
        // Assertions - should be VERIFIED status after moderator approval
        assertEquals(200, moderatorResponse.getStatusCode());
        Kill verifiedKill = gson.fromJson(moderatorResponse.getBody(), Kill.class);
        assertEquals("VERIFIED", verifiedKill.getVerificationStatus());
        assertNotNull(verifiedKill.getVerificationNotes());
        
        logger.info("Successfully tested moderator approval of photo evidence");
    }
    
    @Test
    @Order(15)
    void testPhotoKillVerification_ModeratorRejection() {
        logger.info("E2E Test Step 15: Testing moderator rejection of photo evidence");
        
        // Create another kill with PHOTO verification
        Map<String, Object> killRequestBody = new HashMap<>();
        killRequestBody.put("killerID", PLAYER_1_ID);
        killRequestBody.put("victimID", PLAYER_3_ID); // Reuse Player 3
        killRequestBody.put("latitude", 40.7128);
        killRequestBody.put("longitude", -74.0060);
        killRequestBody.put("verificationMethod", "PHOTO");
        killRequestBody.put("verificationData", new HashMap<>());
        
        // Create kill request context
        APIGatewayProxyRequestEvent.ProxyRequestContext killerContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> killerAuthorizer = new HashMap<>();
        Map<String, Object> killerClaims = new HashMap<>();
        killerClaims.put("sub", PLAYER_1_ID);
        killerAuthorizer.put("claims", killerClaims);
        killerContext.setAuthorizer(killerAuthorizer);
        
        // Create kill
        APIGatewayProxyRequestEvent createRequest = new APIGatewayProxyRequestEvent()
                .withPath("/die")
                .withHttpMethod("POST")
                .withRequestContext(killerContext)
                .withBody(gson.toJson(killRequestBody));
        
        APIGatewayProxyResponseEvent createResponse = killHandler.handleRequest(createRequest, mockContext);
        assertEquals(201, createResponse.getStatusCode());
        Kill photoKill = gson.fromJson(createResponse.getBody(), Kill.class);
        
        // Submit photo for verification
        Map<String, String> photoInput = new HashMap<>();
        photoInput.put("photoUrl", "https://example.com/kill-photos/blurry-image-" + UUID.randomUUID() + ".jpg");
        
        APIGatewayProxyRequestEvent photoRequest = new APIGatewayProxyRequestEvent()
                .withPath("/kills/" + photoKill.getKillerID() + "/" + photoKill.getTime() + "/verify")
                .withHttpMethod("POST")
                .withRequestContext(killerContext)
                .withPathParameters(Map.of(
                    "killerId", photoKill.getKillerID(), 
                    "killTime", photoKill.getTime()))
                .withBody(gson.toJson(photoInput));
        
        APIGatewayProxyResponseEvent photoResponse = killHandler.handleRequest(photoRequest, mockContext);
        assertEquals(200, photoResponse.getStatusCode());
        
        // Get kill to verify it's in PENDING_REVIEW
        Kill pendingKill = gson.fromJson(photoResponse.getBody(), Kill.class);
        assertEquals("PENDING_REVIEW", pendingKill.getVerificationStatus());
        
        // Get moderator ID from previous test
        List<Player> allPlayers = playerDao.getAllPlayers();
        String moderatorId = allPlayers.stream()
                .filter(p -> p.getPlayerName() != null && p.getPlayerName().contains("Moderator"))
                .findFirst()
                .map(Player::getPlayerID)
                .orElse("moderator-" + UUID.randomUUID());
                
        // Simulate moderator rejecting this photo
        Map<String, String> moderatorInput = new HashMap<>();
        moderatorInput.put("moderatorAction", "REJECT");
        moderatorInput.put("moderatorNotes", "Photo is too blurry to verify the assassination");
        
        // Create moderator request context
        APIGatewayProxyRequestEvent.ProxyRequestContext modContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> modAuthorizer = new HashMap<>();
        Map<String, Object> modClaims = new HashMap<>();
        modClaims.put("sub", moderatorId);
        modAuthorizer.put("claims", modClaims);
        modContext.setAuthorizer(modAuthorizer);
        
        APIGatewayProxyRequestEvent rejectRequest = new APIGatewayProxyRequestEvent()
                .withPath("/kills/" + pendingKill.getKillerID() + "/" + pendingKill.getTime() + "/verify")
                .withHttpMethod("POST")
                .withRequestContext(modContext)
                .withPathParameters(Map.of(
                    "killerId", pendingKill.getKillerID(), 
                    "killTime", pendingKill.getTime()))
                .withBody(gson.toJson(moderatorInput));
        
        APIGatewayProxyResponseEvent rejectResponse = killHandler.handleRequest(rejectRequest, mockContext);
        
        // Assertions - should be REJECTED status after moderator rejection
        assertEquals(200, rejectResponse.getStatusCode());
        Kill rejectedKill = gson.fromJson(rejectResponse.getBody(), Kill.class);
        assertEquals("REJECTED", rejectedKill.getVerificationStatus());
        assertNotNull(rejectedKill.getVerificationNotes());
        
        logger.info("Successfully tested moderator rejection of photo evidence");
    }

    @Test
    @Order(16) 
    void testRecentKills() throws Exception {
        logger.info("E2E Test Step 16: Testing retrieval of recent kills for notifications");
        
        // Create a few more kills with different timestamps to test ordering
        // 1. A kill that happened "yesterday"
        Kill oldKill = new Kill();
        oldKill.setKillerID(PLAYER_3_ID); // Player 3 got a kill
        oldKill.setVictimID("extra-victim-" + UUID.randomUUID().toString().substring(0, 8));
        oldKill.setTime(Instant.now().minusSeconds(60 * 60 * 24).toString()); // 1 day ago
        oldKill.setLatitude(41.1234);
        oldKill.setLongitude(-73.4321);
        oldKill.setVerificationStatus("VERIFIED");
        oldKill.setVerificationMethod("GPS");
        oldKill.setKillStatusPartition("VERIFIED"); // Manually set partition key
        killDao.saveKill(oldKill);
        
        // 2. A kill that happened very recently (should be first in recent results)
        Kill veryRecentKill = new Kill();
        veryRecentKill.setKillerID(PLAYER_2_ID); // Player 2 got a kill (even though they're a victim in other tests)
        veryRecentKill.setVictimID("recent-victim-" + UUID.randomUUID().toString().substring(0, 8));
        veryRecentKill.setTime(Instant.now().toString()); // right now
        veryRecentKill.setLatitude(40.1234);
        veryRecentKill.setLongitude(-74.4321);
        veryRecentKill.setVerificationStatus("VERIFIED");
        veryRecentKill.setVerificationMethod("NFC");
        veryRecentKill.setKillStatusPartition("VERIFIED"); // Manually set partition key
        killDao.saveKill(veryRecentKill);
        
        // Create request context with auth
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", PLAYER_1_ID);
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        
        // Add query parameters to limit to 5 most recent kills
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("limit", "5");
        
        // Test the recent kills endpoint
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/kills/recent")
                .withHttpMethod("GET") 
                .withRequestContext(requestContext)
                .withQueryStringParameters(queryParams);
        
        APIGatewayProxyResponseEvent response = killHandler.handleRequest(request, mockContext);
        
        // Assertions
        assertEquals(200, response.getStatusCode());
        // Refactored Deserialization
        List<Map<String, Object>> recentKillsData = gson.fromJson(response.getBody(),
             new TypeToken<List<Map<String, Object>>>(){}.getType());

        // Should have at least 3 kills from all our tests
        assertTrue(recentKillsData.size() >= 3, "Expected at least 3 recent kills, found " + recentKillsData.size());

        // First kill should be the most recent
        assertEquals(veryRecentKill.getKillerID(), recentKillsData.get(0).get("killerID"));
        assertEquals(veryRecentKill.getVictimID(), recentKillsData.get(0).get("victimID"));

        // Verify chronological ordering (newest first)
        for (int i = 0; i < recentKillsData.size() - 1; i++) {
            // Extract time strings and parse them
            String currentTimeStr = (String) recentKillsData.get(i).get("time");
            String nextTimeStr = (String) recentKillsData.get(i+1).get("time");
            assertNotNull(currentTimeStr, "Kill time string should not be null");
            assertNotNull(nextTimeStr, "Next kill time string should not be null");

            Instant current = Instant.parse(currentTimeStr);
            Instant next = Instant.parse(nextTimeStr);
            assertTrue(current.isAfter(next) || current.equals(next),
                "Kills should be ordered by time (newest first)");
        }

        logger.info("Successfully tested retrieval of recent kills for notifications");
    }
    
    @Test
    @Order(17)
    void testKillsTimeline() throws Exception {
        logger.info("E2E Test Step 17: Testing game-specific kills timeline");
        
        // Create a game for timeline testing
        Game timelineGame = new Game();
        timelineGame.setGameID("timeline-game-" + UUID.randomUUID().toString().substring(0, 8));
        timelineGame.setGameName("Timeline Test Game");
        timelineGame.setStatus("ACTIVE");
        gameDao.saveGame(timelineGame);
        
        // Create a few players for this specific game
        Player gamePlayer1 = new Player();
        gamePlayer1.setPlayerID("timeline-player1-" + UUID.randomUUID().toString().substring(0, 8));
        gamePlayer1.setPlayerName("Timeline Player 1");
        gamePlayer1.setGameID(timelineGame.getGameID());
        playerDao.savePlayer(gamePlayer1);
        
        Player gamePlayer2 = new Player();
        gamePlayer2.setPlayerID("timeline-player2-" + UUID.randomUUID().toString().substring(0, 8)); 
        gamePlayer2.setPlayerName("Timeline Player 2");
        gamePlayer2.setGameID(timelineGame.getGameID());
        playerDao.savePlayer(gamePlayer2);
        
        Player gamePlayer3 = new Player();
        gamePlayer3.setPlayerID("timeline-player3-" + UUID.randomUUID().toString().substring(0, 8));
        gamePlayer3.setPlayerName("Timeline Player 3");
        gamePlayer3.setGameID(timelineGame.getGameID());
        playerDao.savePlayer(gamePlayer3);
        
        // Create some in-game kills with timestamps ascending order
        Kill kill1 = new Kill();
        kill1.setKillerID(gamePlayer1.getPlayerID());
        kill1.setVictimID(gamePlayer2.getPlayerID());
        kill1.setTime(Instant.now().minusSeconds(3600).toString()); // 1 hour ago
        kill1.setLatitude(40.1234);
        kill1.setLongitude(-74.4321);
        kill1.setVerificationStatus("VERIFIED");
        kill1.setVerificationMethod("GPS");
        kill1.setGameId(timelineGame.getGameID()); // Set the game ID
        killDao.saveKill(kill1);
        
        Kill kill2 = new Kill();
        kill2.setKillerID(gamePlayer3.getPlayerID());
        kill2.setVictimID(gamePlayer1.getPlayerID());
        kill2.setTime(Instant.now().minusSeconds(1800).toString()); // 30 minutes ago
        kill2.setLatitude(40.5678);
        kill2.setLongitude(-74.8765);
        kill2.setVerificationStatus("VERIFIED");
        kill2.setVerificationMethod("NFC");
        kill2.setGameId(timelineGame.getGameID()); // Set the game ID
        killDao.saveKill(kill2);
        
        Kill kill3 = new Kill();
        kill3.setKillerID(gamePlayer2.getPlayerID());
        kill3.setVictimID(gamePlayer3.getPlayerID()); 
        kill3.setTime(Instant.now().minusSeconds(600).toString()); // 10 minutes ago
        kill3.setLatitude(40.9876);
        kill3.setLongitude(-74.5432);
        kill3.setVerificationStatus("VERIFIED");
        kill3.setVerificationMethod("PHOTO");
        kill3.setGameId(timelineGame.getGameID()); // Set the game ID
        killDao.saveKill(kill3);
        
        // Create request context with auth (any player can view timeline)
        APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        Map<String, Object> authorizer = new HashMap<>();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", gamePlayer1.getPlayerID());
        authorizer.put("claims", claims);
        requestContext.setAuthorizer(authorizer);
        
        // Test the game timeline endpoint 
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPath("/games/" + timelineGame.getGameID() + "/timeline")
                .withHttpMethod("GET")
                .withPathParameters(Map.of("gameID", timelineGame.getGameID()))
                .withRequestContext(requestContext);
        
        APIGatewayProxyResponseEvent response = killHandler.handleRequest(request, mockContext);
        
        // Assertions
        assertEquals(200, response.getStatusCode());
        List<Map<String, Object>> timelineEvents = gson.fromJson(response.getBody(), 
                                        new TypeToken<List<Map<String, Object>>>(){}.getType());
        
        // Should have 3 events in chronological order (oldest first for timeline)
        assertEquals(3, timelineEvents.size());
        
        // Verify ordering (oldest first for the timeline feed)
        assertEquals("KILL", timelineEvents.get(0).get("eventType"));
        assertEquals(kill1.getKillerID(), timelineEvents.get(0).get("killerID"));
        assertEquals(kill1.getVictimID(), timelineEvents.get(0).get("victimID"));
        
        assertEquals("KILL", timelineEvents.get(1).get("eventType"));
        assertEquals(kill2.getKillerID(), timelineEvents.get(1).get("killerID"));
        assertEquals(kill2.getVictimID(), timelineEvents.get(1).get("victimID"));
        
        assertEquals("KILL", timelineEvents.get(2).get("eventType"));
        assertEquals(kill3.getKillerID(), timelineEvents.get(2).get("killerID"));
        assertEquals(kill3.getVictimID(), timelineEvents.get(2).get("victimID"));
        
        logger.info("Successfully tested game-specific kills timeline");
    }
} 