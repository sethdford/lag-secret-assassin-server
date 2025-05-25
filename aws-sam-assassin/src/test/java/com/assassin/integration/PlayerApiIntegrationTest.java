package com.assassin.integration;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
import com.assassin.model.Player;
import com.assassin.service.PlayerService;
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
 * Integration test for the Player API, testing the flow from HTTP request through
 * the handler to the DAO and back.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "docker.available", matches = "true", disabledReason = "Docker not available")
public class PlayerApiIntegrationTest {

    private static final String TEST_TABLE_NAME = "test-players";
    
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
    
    // Mock Lambda context since we can't easily create a real one
    private Context mockContext = new TestContext();
    
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
        
        // Create the test table
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
        
        // Initialize Gson
        gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    @BeforeEach
    void clearTable() {
        // Clear all data from the table before each test
        playerTable.scan().items().forEach(playerTable::deleteItem);
    }
    
    @AfterAll
    void cleanup() {
        // Reset static client provider
        DynamoDbClientProvider.resetClient();
        System.clearProperty("PLAYERS_TABLE_NAME");
        
        // Close clients
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
            
            // Create table request
            CreateTableRequest createTableRequest = CreateTableRequest.builder()
                    .tableName(TEST_TABLE_NAME)
                    .keySchema(playerIdKey)
                    .attributeDefinitions(playerIdDef, gameIdDef)
                    .globalSecondaryIndexes(gameIdIndex)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build();
            
            // Create the table
            ddbClient.createTable(createTableRequest);
            
            // Wait for table to be ready
            ddbClient.waiter().waitUntilTableExists(builder -> builder.tableName(TEST_TABLE_NAME));
            
            System.out.println("Created test players table");
        } catch (ResourceInUseException e) {
            System.out.println("Table already exists, continuing...");
        }
    }
    
    @Test
    void testCreateAndGetPlayer() {
        // Create a player via the API
        Player newPlayer = new Player();
        newPlayer.setPlayerID("test-player-1");
        newPlayer.setPlayerName("Test Player");
        newPlayer.setEmail("test@example.com");
        
        // Create POST request
        APIGatewayProxyRequestEvent createRequest = new APIGatewayProxyRequestEvent()
                .withPath("/players")
                .withHttpMethod("POST")
                .withBody(gson.toJson(newPlayer));
        
        // Execute handler
        APIGatewayProxyResponseEvent createResponse = playerHandler.handleRequest(createRequest, mockContext);
        
        // Verify response
        assertEquals(201, createResponse.getStatusCode());
        Player createdPlayer = gson.fromJson(createResponse.getBody(), Player.class);
        assertEquals("test-player-1", createdPlayer.getPlayerID());
        assertEquals("Test Player", createdPlayer.getPlayerName());
        
        // Now try to get the player via GET API
        APIGatewayProxyRequestEvent getRequest = new APIGatewayProxyRequestEvent()
                .withPath("/players/test-player-1")
                .withHttpMethod("GET");
        
        APIGatewayProxyResponseEvent getResponse = playerHandler.handleRequest(getRequest, mockContext);
        
        // Verify GET response
        assertEquals(200, getResponse.getStatusCode());
        Player retrievedPlayer = gson.fromJson(getResponse.getBody(), Player.class);
        assertEquals("test-player-1", retrievedPlayer.getPlayerID());
        assertEquals("Test Player", retrievedPlayer.getPlayerName());
        assertEquals("test@example.com", retrievedPlayer.getEmail());
    }
    
    @Test
    void testGetAllPlayers() {
        // Set up multiple players in the database
        Player player1 = new Player();
        player1.setPlayerID("test-player-1");
        player1.setPlayerName("Test Player 1");
        player1.setEmail("test1@example.com");
        
        Player player2 = new Player();
        player2.setPlayerID("test-player-2");
        player2.setPlayerName("Test Player 2");
        player2.setEmail("test2@example.com");
        
        // Save directly via DAO
        playerDao.savePlayer(player1);
        playerDao.savePlayer(player2);
        
        // Create GET all request
        APIGatewayProxyRequestEvent getAllRequest = new APIGatewayProxyRequestEvent()
                .withPath("/players")
                .withHttpMethod("GET");
        
        // Execute handler
        APIGatewayProxyResponseEvent getAllResponse = playerHandler.handleRequest(getAllRequest, mockContext);
        
        // Verify response
        assertEquals(200, getAllResponse.getStatusCode());
        
        // Parse response body to get list of players
        java.lang.reflect.Type playerListType = new TypeToken<java.util.List<Player>>(){}.getType();
        java.util.List<Player> players = gson.fromJson(getAllResponse.getBody(), playerListType);
        
        // Verify returned players
        assertEquals(2, players.size());
        assertTrue(players.stream().anyMatch(p -> p.getPlayerID().equals("test-player-1")));
        assertTrue(players.stream().anyMatch(p -> p.getPlayerID().equals("test-player-2")));
    }
    
    @Test
    void testUpdatePlayer() {
        // First create a player
        Player player = new Player();
        player.setPlayerID("test-player-update");
        player.setPlayerName("Original Name");
        player.setEmail("original@example.com");
        
        // Save directly via DAO
        playerDao.savePlayer(player);
        
        // Now update via API
        Player updatedPlayer = new Player();
        updatedPlayer.setPlayerID("test-player-update");
        updatedPlayer.setPlayerName("Updated Name");
        updatedPlayer.setEmail("updated@example.com");
        
        // Create PUT request
        APIGatewayProxyRequestEvent updateRequest = new APIGatewayProxyRequestEvent()
                .withPath("/players/test-player-update")
                .withHttpMethod("PUT")
                .withBody(gson.toJson(updatedPlayer));
        
        // Execute handler
        APIGatewayProxyResponseEvent updateResponse = playerHandler.handleRequest(updateRequest, mockContext);
        
        // Verify response
        assertEquals(200, updateResponse.getStatusCode());
        
        // Get updated player directly from DAO to verify persistence
        Optional<Player> resultOpt = playerDao.getPlayerById("test-player-update");
        
        assertTrue(resultOpt.isPresent());
        Player result = resultOpt.get();
        assertEquals("Updated Name", result.getPlayerName());
        assertEquals("updated@example.com", result.getEmail());
    }
    
    @Test
    void testGetNonExistentPlayer() {
        // Create GET request for non-existent player
        APIGatewayProxyRequestEvent getRequest = new APIGatewayProxyRequestEvent()
                .withPath("/players/non-existent")
                .withHttpMethod("GET");
        
        // Execute handler
        APIGatewayProxyResponseEvent getResponse = playerHandler.handleRequest(getRequest, mockContext);
        
        // Verify response
        assertEquals(404, getResponse.getStatusCode());
        
        // Parse error message
        Map<String, String> errorResponse = gson.fromJson(getResponse.getBody(), 
                new TypeToken<Map<String, String>>(){}.getType());
        
        assertEquals("Player not found", errorResponse.get("message"));
    }
} 