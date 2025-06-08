package com.assassin.integration;

import com.assassin.dao.*;
import com.assassin.model.*;
import com.assassin.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;

@Testcontainers
public class TargetAssignmentFlowIntegrationTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
            .withServices(DYNAMODB);

    private DynamoDbClient dynamoDbClient;
    private DynamoDbEnhancedClient enhancedClient;
    private GameDao gameDao;
    private PlayerDao playerDao;
    private TargetAssignmentDao targetAssignmentDao;
    private PlayerService playerService;
    private GameService gameService;

    @BeforeEach
    public void setUp() {
        // Configure DynamoDB client
        dynamoDbClient = DynamoDbClient.builder()
                .endpointOverride(localstack.getEndpointOverride(DYNAMODB))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .build();

        enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();

        // Create tables with proper wait
        createTablesWithProperWait();

        // Create tables
        DynamoDbTable<Game> gameTable = enhancedClient.table("dev-Games", TableSchema.fromBean(Game.class));
        DynamoDbTable<Player> playerTable = enhancedClient.table("Players", TableSchema.fromBean(Player.class));
        DynamoDbTable<TargetAssignment> targetAssignmentTable = enhancedClient.table("TargetAssignments", TableSchema.fromBean(TargetAssignment.class));

        // Init DAOs
        gameDao = new DynamoDbGameDao(enhancedClient);
        playerDao = new DynamoDbPlayerDao(enhancedClient);
        targetAssignmentDao = new DynamoDbTargetAssignmentDao(targetAssignmentTable);

        // Init Services - use correct constructors
        playerService = new PlayerService(playerDao);  // PlayerService(PlayerDao)
        gameService = new GameService(gameDao, playerDao);  // GameService(GameDao, PlayerDao)
    }

    private void createTablesWithProperWait() {
        try {
            System.out.println("Creating DynamoDB tables...");
            
            // Delete existing tables first to ensure clean state
            try {
                dynamoDbClient.deleteTable(DeleteTableRequest.builder().tableName("dev-Games").build());
                System.out.println("Deleted existing dev-Games table");
            } catch (Exception e) {
                // Table might not exist, ignore
            }
            
            try {
                dynamoDbClient.deleteTable(DeleteTableRequest.builder().tableName("Players").build());
                System.out.println("Deleted existing Players table");
            } catch (Exception e) {
                // Table might not exist, ignore
            }
            
            try {
                dynamoDbClient.deleteTable(DeleteTableRequest.builder().tableName("TargetAssignments").build());
                System.out.println("Deleted existing TargetAssignments table");
            } catch (Exception e) {
                // Table might not exist, ignore
            }
            
            // Wait a bit for tables to be deleted
            Thread.sleep(2000);
            
            // Create Games table (dev-Games is the default) with GSI
            try {
                CreateTableRequest gamesTableRequest = CreateTableRequest.builder()
                        .tableName("dev-Games")
                        .keySchema(KeySchemaElement.builder()
                                .attributeName("GameID")
                                .keyType(KeyType.HASH)
                                .build())
                        .attributeDefinitions(
                                AttributeDefinition.builder()
                                        .attributeName("GameID")
                                        .attributeType(ScalarAttributeType.S)
                                        .build(),
                                AttributeDefinition.builder()
                                        .attributeName("status")
                                        .attributeType(ScalarAttributeType.S)
                                        .build(),
                                AttributeDefinition.builder()
                                        .attributeName("createdAt")
                                        .attributeType(ScalarAttributeType.S)
                                        .build())
                        .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                                .indexName("StatusCreatedAtIndex")
                                .keySchema(
                                        KeySchemaElement.builder()
                                                .attributeName("status")
                                                .keyType(KeyType.HASH)
                                                .build(),
                                        KeySchemaElement.builder()
                                                .attributeName("createdAt")
                                                .keyType(KeyType.RANGE)
                                                .build())
                                .projection(Projection.builder()
                                        .projectionType(ProjectionType.ALL)
                                        .build())
                                .provisionedThroughput(ProvisionedThroughput.builder()
                                        .readCapacityUnits(5L)
                                        .writeCapacityUnits(5L)
                                        .build())
                                .build())
                        .provisionedThroughput(ProvisionedThroughput.builder()
                                .readCapacityUnits(5L)
                                .writeCapacityUnits(5L)
                                .build())
                        .build();
                
                dynamoDbClient.createTable(gamesTableRequest);
                System.out.println("Games table created successfully");
            } catch (Exception e) {
                System.out.println("Games table might already exist: " + e.getMessage());
            }

            // Create Players table (Players is the default)
            try {
                CreateTableRequest playersTableRequest = CreateTableRequest.builder()
                        .tableName("Players")  // Match what PlayerDao expects
                        .keySchema(KeySchemaElement.builder()
                                .attributeName("PlayerID")  // Match the @DynamoDbAttribute annotation
                                .keyType(KeyType.HASH)
                                .build())
                        .attributeDefinitions(AttributeDefinition.builder()
                                .attributeName("PlayerID")
                                .attributeType(ScalarAttributeType.S)
                                .build())
                        .provisionedThroughput(ProvisionedThroughput.builder()
                                .readCapacityUnits(5L)
                                .writeCapacityUnits(5L)
                                .build())
                        .build();
                dynamoDbClient.createTable(playersTableRequest);
                System.out.println("Players table created successfully");
            } catch (Exception e) {
                System.out.println("Players table creation failed or already exists: " + e.getMessage());
            }

            // Create TargetAssignments table (TargetAssignments is the default)
            try {
                CreateTableRequest targetAssignmentsTableRequest = CreateTableRequest.builder()
                        .tableName("TargetAssignments")  // Match what TargetAssignmentDao expects
                        .keySchema(KeySchemaElement.builder()
                                .attributeName("AssignmentId")  // Match the @DynamoDbAttribute annotation
                                .keyType(KeyType.HASH)
                                .build())
                        .attributeDefinitions(
                                AttributeDefinition.builder()
                                        .attributeName("AssignmentId")
                                        .attributeType(ScalarAttributeType.S)
                                        .build(),
                                AttributeDefinition.builder()
                                        .attributeName("GameId")
                                        .attributeType(ScalarAttributeType.S)
                                        .build(),
                                AttributeDefinition.builder()
                                        .attributeName("AssignerId")
                                        .attributeType(ScalarAttributeType.S)
                                        .build(),
                                AttributeDefinition.builder()
                                        .attributeName("TargetId")
                                        .attributeType(ScalarAttributeType.S)
                                        .build(),
                                AttributeDefinition.builder()
                                        .attributeName("Status")
                                        .attributeType(ScalarAttributeType.S)
                                        .build())
                        .globalSecondaryIndexes(
                                // GameAssignerIndex: GameId (partition) + AssignerId (sort)
                                GlobalSecondaryIndex.builder()
                                        .indexName("GameAssignerIndex")
                                        .keySchema(
                                                KeySchemaElement.builder()
                                                        .attributeName("GameId")
                                                        .keyType(KeyType.HASH)
                                                        .build(),
                                                KeySchemaElement.builder()
                                                        .attributeName("AssignerId")
                                                        .keyType(KeyType.RANGE)
                                                        .build())
                                        .projection(Projection.builder()
                                                .projectionType(ProjectionType.ALL)
                                                .build())
                                        .provisionedThroughput(ProvisionedThroughput.builder()
                                                .readCapacityUnits(5L)
                                                .writeCapacityUnits(5L)
                                                .build())
                                        .build(),
                                // GameTargetIndex: GameId (partition) + TargetId (sort)
                                GlobalSecondaryIndex.builder()
                                        .indexName("GameTargetIndex")
                                        .keySchema(
                                                KeySchemaElement.builder()
                                                        .attributeName("GameId")
                                                        .keyType(KeyType.HASH)
                                                        .build(),
                                                KeySchemaElement.builder()
                                                        .attributeName("TargetId")
                                                        .keyType(KeyType.RANGE)
                                                        .build())
                                        .projection(Projection.builder()
                                                .projectionType(ProjectionType.ALL)
                                                .build())
                                        .provisionedThroughput(ProvisionedThroughput.builder()
                                                .readCapacityUnits(5L)
                                                .writeCapacityUnits(5L)
                                                .build())
                                        .build(),
                                // GameStatusIndex: GameId (partition) + Status (sort)
                                GlobalSecondaryIndex.builder()
                                        .indexName("GameStatusIndex")
                                        .keySchema(
                                                KeySchemaElement.builder()
                                                        .attributeName("GameId")
                                                        .keyType(KeyType.HASH)
                                                        .build(),
                                                KeySchemaElement.builder()
                                                        .attributeName("Status")
                                                        .keyType(KeyType.RANGE)
                                                        .build())
                                        .projection(Projection.builder()
                                                .projectionType(ProjectionType.ALL)
                                                .build())
                                        .provisionedThroughput(ProvisionedThroughput.builder()
                                                .readCapacityUnits(5L)
                                                .writeCapacityUnits(5L)
                                                .build())
                                        .build())
                        .provisionedThroughput(ProvisionedThroughput.builder()
                                .readCapacityUnits(5L)
                                .writeCapacityUnits(5L)
                                .build())
                        .build();
                dynamoDbClient.createTable(targetAssignmentsTableRequest);
                System.out.println("TargetAssignments table created successfully");
            } catch (Exception e) {
                System.out.println("TargetAssignments table creation failed or already exists: " + e.getMessage());
            }

            // Wait for tables to be ready
            System.out.println("Waiting for tables to be ready...");
            Thread.sleep(2000); // Give LocalStack time to create tables

            // Verify tables exist
            ListTablesResponse listTablesResponse = dynamoDbClient.listTables();
            System.out.println("Available tables: " + listTablesResponse.tableNames());

        } catch (Exception e) {
            System.err.println("Error creating tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    public void testTargetAssignmentBasicFlow() {
        assertNotNull(targetAssignmentDao);
        // Basic test to ensure setup is working
    }

    @Test
    public void testCreateAndRetrieveTargetAssignment() {
        // Create a game
        Game game = new Game();
        game.setGameID("test-game-1");
        game.setGameName("Test Game 1");
        game.setStatus("ACTIVE");
        game.setCreatedAt(java.time.Instant.now().toString());
        game.setAdminPlayerID("admin-1");
        
        // Debug: Print all the fields we're setting
        System.out.println("DEBUG: Game object before save:");
        System.out.println("  gameID: " + game.getGameID());
        System.out.println("  gameName: " + game.getGameName());
        System.out.println("  status: " + game.getStatus());
        System.out.println("  createdAt: " + game.getCreatedAt());
        System.out.println("  adminPlayerID: " + game.getAdminPlayerID());
        
        gameDao.saveGame(game);

        // Create players
        Player hunter = new Player();
        hunter.setPlayerID("hunter-1");
        hunter.setPlayerName("hunter1");
        hunter.setEmail("hunter1@test.com");
        playerDao.savePlayer(hunter);

        Player target = new Player();
        target.setPlayerID("target-1");
        target.setPlayerName("target1");
        target.setEmail("target1@test.com");
        playerDao.savePlayer(target);

        // Create target assignment
        TargetAssignment assignment = new TargetAssignment();
        assignment.setAssignmentId("assignment-1");
        assignment.setGameId("test-game-1");
        assignment.setAssignerId("hunter-1");
        assignment.setTargetId("target-1");
        assignment.setStatus("ACTIVE");
        assignment.setAssignmentDate(java.time.Instant.now().toString());

        // Save assignment
        targetAssignmentDao.saveAssignment(assignment);

        // Retrieve assignment
        Optional<TargetAssignment> retrieved = targetAssignmentDao.getAssignmentById("assignment-1");
        assertTrue(retrieved.isPresent());
        assertEquals("test-game-1", retrieved.get().getGameId());
        assertEquals("hunter-1", retrieved.get().getAssignerId());
        assertEquals("target-1", retrieved.get().getTargetId());
        assertEquals("ACTIVE", retrieved.get().getStatus());
    }

    @Test
    public void testGetCurrentAssignmentForPlayer() {
        // Create a game
        Game game = new Game();
        game.setGameID("test-game-2");
        game.setGameName("Test Game 2");
        game.setStatus("ACTIVE");
        game.setCreatedAt(java.time.Instant.now().toString());
        game.setAdminPlayerID("admin-2");
        gameDao.saveGame(game);

        // Create players
        Player hunter = new Player();
        hunter.setPlayerID("hunter-2");
        hunter.setPlayerName("hunter2");
        hunter.setEmail("hunter2@test.com");
        playerDao.savePlayer(hunter);

        Player target = new Player();
        target.setPlayerID("target-2");
        target.setPlayerName("target2");
        target.setEmail("target2@test.com");
        playerDao.savePlayer(target);

        // Create target assignment
        TargetAssignment assignment = new TargetAssignment();
        assignment.setAssignmentId("assignment-2");
        assignment.setGameId("test-game-2");
        assignment.setAssignerId("hunter-2");
        assignment.setTargetId("target-2");
        assignment.setStatus("ACTIVE");
        assignment.setAssignmentDate(java.time.Instant.now().toString());

        // Save assignment
        targetAssignmentDao.saveAssignment(assignment);

        // Test getCurrentAssignmentForPlayer
        Optional<TargetAssignment> currentAssignment = targetAssignmentDao.getCurrentAssignmentForPlayer("test-game-2", "hunter-2");
        assertTrue(currentAssignment.isPresent());
        assertEquals("assignment-2", currentAssignment.get().getAssignmentId());
        assertEquals("hunter-2", currentAssignment.get().getAssignerId());
    }

    @Test
    public void testGetActiveAssignmentsInGame() {
        // Create a game
        Game game = new Game();
        game.setGameID("test-game-3");
        game.setGameName("Test Game 3");
        game.setStatus("ACTIVE");
        game.setCreatedAt(java.time.Instant.now().toString());
        game.setAdminPlayerID("admin-3");
        gameDao.saveGame(game);

        // Create multiple assignments
        TargetAssignment assignment1 = new TargetAssignment();
        assignment1.setAssignmentId("assignment-3-1");
        assignment1.setGameId("test-game-3");
        assignment1.setAssignerId("hunter-3-1");
        assignment1.setTargetId("target-3-1");
        assignment1.setStatus("ACTIVE");
        assignment1.setAssignmentDate(java.time.Instant.now().toString());
        targetAssignmentDao.saveAssignment(assignment1);

        TargetAssignment assignment2 = new TargetAssignment();
        assignment2.setAssignmentId("assignment-3-2");
        assignment2.setGameId("test-game-3");
        assignment2.setAssignerId("hunter-3-2");
        assignment2.setTargetId("target-3-2");
        assignment2.setStatus("ACTIVE");
        assignment2.setAssignmentDate(java.time.Instant.now().toString());
        targetAssignmentDao.saveAssignment(assignment2);

        // Test getActiveAssignmentsForGame
        List<TargetAssignment> activeAssignments = targetAssignmentDao.getActiveAssignmentsForGame("test-game-3");
        assertEquals(2, activeAssignments.size());
    }

    @Test
    public void testUpdateAssignmentStatus() {
        // Create a game
        Game game = new Game();
        game.setGameID("test-game-4");
        game.setGameName("Test Game 4");
        game.setStatus("ACTIVE");
        game.setCreatedAt(java.time.Instant.now().toString());
        game.setAdminPlayerID("admin-4");
        gameDao.saveGame(game);

        // Create target assignment
        TargetAssignment assignment = new TargetAssignment();
        assignment.setAssignmentId("assignment-4");
        assignment.setGameId("test-game-4");
        assignment.setAssignerId("hunter-4");
        assignment.setTargetId("target-4");
        assignment.setStatus("ACTIVE");
        assignment.setAssignmentDate(java.time.Instant.now().toString());
        targetAssignmentDao.saveAssignment(assignment);

        // Update status to COMPLETED
        assignment.setStatus("COMPLETED");
        assignment.setCompletedDate(java.time.Instant.now().toString());
        targetAssignmentDao.saveAssignment(assignment);

        // Verify update
        Optional<TargetAssignment> updated = targetAssignmentDao.getAssignmentById("assignment-4");
        assertTrue(updated.isPresent());
        assertEquals("COMPLETED", updated.get().getStatus());
        assertNotNull(updated.get().getCompletedDate());
    }

    @Test
    public void testGetAssignmentHistory() {
        // Create a game
        Game game = new Game();
        game.setGameID("test-game-5");
        game.setGameName("Test Game 5");
        game.setStatus("ACTIVE");
        game.setCreatedAt(java.time.Instant.now().toString());
        game.setAdminPlayerID("admin-5");
        gameDao.saveGame(game);

        // Create multiple assignments for the same player
        TargetAssignment assignment1 = new TargetAssignment();
        assignment1.setAssignmentId("assignment-5-1");
        assignment1.setGameId("test-game-5");
        assignment1.setAssignerId("hunter-5");
        assignment1.setTargetId("target-5-1");
        assignment1.setStatus("COMPLETED");
        assignment1.setAssignmentDate(java.time.Instant.now().toString());
        assignment1.setCompletedDate(java.time.Instant.now().toString());
        targetAssignmentDao.saveAssignment(assignment1);

        TargetAssignment assignment2 = new TargetAssignment();
        assignment2.setAssignmentId("assignment-5-2");
        assignment2.setGameId("test-game-5");
        assignment2.setAssignerId("hunter-5");
        assignment2.setTargetId("target-5-2");
        assignment2.setStatus("ACTIVE");
        assignment2.setAssignmentDate(java.time.Instant.now().toString());
        targetAssignmentDao.saveAssignment(assignment2);

        // Test getAssignmentHistoryForPlayer
        List<TargetAssignment> history = targetAssignmentDao.getAssignmentHistoryForPlayer("test-game-5", "hunter-5");
        assertEquals(2, history.size());
    }
}
