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
import com.assassin.model.Kill;
import com.assassin.model.Player;
import com.assassin.service.KillService;
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
        System.setProperty("ASSASSIN_TEST_MODE", "true");
        
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
        
        // Set up enhanced client and tables
        enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(ddbClient)
                .build();
        
        playerTable = enhancedClient.table(PLAYERS_TABLE_NAME, TableSchema.fromBean(Player.class));
        killTable = enhancedClient.table(KILLS_TABLE_NAME, TableSchema.fromBean(Kill.class));
        
        // Initialize DAOs, Services, and Handlers
        playerDao = new DynamoDbPlayerDao();
        killDao = new DynamoDbKillDao();
        gameDao = new DynamoDbGameDao();
        killService = new KillService(killDao, playerDao, gameDao);
        
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
        System.clearProperty("ASSASSIN_TEST_MODE");
        
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
            
            CreateTableRequest createTableRequest = CreateTableRequest.builder()
                    .tableName(PLAYERS_TABLE_NAME)
                    .keySchema(playerIdKey)
                    .attributeDefinitions(playerIdDef)
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
            
            // Define GSI projection (what attributes to include)
            Projection projection = Projection.builder()
                    .projectionType(ProjectionType.ALL) // Include all attributes
                    .build();
            
            // Define the GSI
            GlobalSecondaryIndex victimIndex = GlobalSecondaryIndex.builder()
                    .indexName("VictimID-Time-index")
                    .keySchema(victimIdKey, gsiTimeKey)
                    .projection(projection)
                    .build();
            
            // Build the create table request
            CreateTableRequest createTableRequest = CreateTableRequest.builder()
                    .tableName(KILLS_TABLE_NAME)
                    .keySchema(killerIdKey, timeKey)
                    .attributeDefinitions(killerIdDef, timeDef, victimIdDef)
                    .globalSecondaryIndexes(victimIndex)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build();
            
            ddbClient.createTable(createTableRequest);
            ddbClient.waiter().waitUntilTableExists(builder -> builder.tableName(KILLS_TABLE_NAME));
            logger.info("Created kills table for E2E test with GSI: VictimID-Time-index");
        } catch (ResourceInUseException e) {
            logger.info("Kills table for E2E test already exists, continuing");
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
        Kill kill = new Kill();
        kill.setKillerID(PLAYER_1_ID);
        kill.setVictimID(PLAYER_2_ID);
        kill.setTime(Instant.now().toString());
        kill.setLatitude(40.7128);
        kill.setLongitude(-74.0060);
        
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
                .withBody(gson.toJson(kill));
        
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
        
        List<Kill> kills = gson.fromJson(response.getBody(), new TypeToken<List<Kill>>(){}.getType());
        assertEquals(1, kills.size());
        assertEquals(PLAYER_1_ID, kills.get(0).getKillerID());
        assertEquals(PLAYER_2_ID, kills.get(0).getVictimID());
        
        logger.info("Successfully retrieved kills by killer");
    }
    
    @Test
    @Order(5)
    void testRecordAnotherKill() {
        logger.info("E2E Test Step 5: Recording another kill");
        
        // Player 1 kills Player 3
        Kill kill = new Kill();
        kill.setKillerID(PLAYER_1_ID);
        kill.setVictimID(PLAYER_3_ID);
        kill.setTime(Instant.now().toString());
        kill.setLatitude(40.7128);
        kill.setLongitude(-74.0060);
        
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
                .withBody(gson.toJson(kill));
        
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
        
        List<Kill> kills = gson.fromJson(response.getBody(), new TypeToken<List<Kill>>(){}.getType());
        assertEquals(1, kills.size());
        assertEquals(PLAYER_1_ID, kills.get(0).getKillerID());
        assertEquals(PLAYER_3_ID, kills.get(0).getVictimID());
        
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
        
        List<Kill> kills = gson.fromJson(response.getBody(), new TypeToken<List<Kill>>(){}.getType());
        assertEquals(2, kills.size());
        
        logger.info("Verified that Player 1 has the expected number of kills: {}", kills.size());
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
} 