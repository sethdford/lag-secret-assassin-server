package com.assassin.dao;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.localstack.LocalStackContainer;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.assassin.exception.KillNotFoundException;
import com.assassin.model.Kill;
import com.assassin.util.DynamoDbClientProvider;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.IndexStatus;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Integration tests for the DynamoDbKillDao class.
 * Uses Testcontainers with LocalStack to provide a real DynamoDB instance for testing.
 * Tests cover CRUD operations and querying via both the primary key and GSI.
 */
@Testcontainers
@ExtendWith(MockitoExtension.class)
class DynamoDbKillDaoTest {

    private static final String TEST_TABLE_NAME = "test-kills";
    private static final String TEST_INDEX_NAME = "VictimID-Time-index";

    /**
     * LocalStack container that provides a DynamoDB service for testing.
     * This eliminates the need for an actual AWS account during testing.
     */
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.5.0"))
            .withServices(DYNAMODB);

    private static DynamoDbEnhancedClient enhancedClient;
    private static DynamoDbTable<Kill> killTable;
    private static DynamoDbKillDao killDao;
    private static DynamoDbClient ddbClient;

    /**
     * Set up the test environment before any tests run.
     * Creates the DynamoDB table with the necessary GSI and waits for both to become active.
     * This ensures the test environment is properly initialized.
     */
    @BeforeAll
    static void beforeAll() {
        // Set system property for table name to be used by the DAO
        System.setProperty("KILLS_TABLE_NAME", TEST_TABLE_NAME);
        
        // Create and configure the DynamoDB client to use LocalStack
        ddbClient = DynamoDbClient.builder()
                .endpointOverride(localstack.getEndpointOverride(DYNAMODB))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .build();

        // Override the provider with our test client
        DynamoDbClientProvider.overrideClient(ddbClient);

        // Create table schema from Kill class
        TableSchema<Kill> killSchema = TableSchema.fromBean(Kill.class);

        // Create the table and GSI needed for the tests using low-level client
        try {
             // Define the attribute definitions needed for the table and GSIs
             AttributeDefinition killerIdDef = AttributeDefinition.builder()
                .attributeName("KillerID").attributeType(ScalarAttributeType.S).build();
             AttributeDefinition timeDef = AttributeDefinition.builder() // Primary Sort Key & GSI Sort Key
                .attributeName("Time").attributeType(ScalarAttributeType.S).build();
             AttributeDefinition victimIdDef = AttributeDefinition.builder() // GSI Hash Key
                .attributeName("VictimID").attributeType(ScalarAttributeType.S).build();
             AttributeDefinition gameIdDef = AttributeDefinition.builder() // GSI Hash Key
                .attributeName("GameID").attributeType(ScalarAttributeType.S).build();
            // Corrected: Use KillStatusPartition for the GSI attribute definition
             AttributeDefinition statusPartitionDef = AttributeDefinition.builder() // GSI Hash Key
                .attributeName("KillStatusPartition").attributeType(ScalarAttributeType.S).build();

             // Define the primary key schema (hash + range)
             KeySchemaElement pkHash = KeySchemaElement.builder()
                .attributeName("KillerID").keyType(KeyType.HASH).build();
             KeySchemaElement pkRange = KeySchemaElement.builder()
                .attributeName("Time").keyType(KeyType.RANGE).build();

             // Define the VictimID-Time-index GSI key schema
             KeySchemaElement victimGsiHash = KeySchemaElement.builder()
                .attributeName("VictimID").keyType(KeyType.HASH).build();
             KeySchemaElement victimGsiRange = KeySchemaElement.builder()
                .attributeName("Time").keyType(KeyType.RANGE).build();

             // Define GameID-Time-index GSI schema
             KeySchemaElement gameIdKey = KeySchemaElement.builder()
                .attributeName("GameID").keyType(KeyType.HASH).build();
             KeySchemaElement gameTimeKey = KeySchemaElement.builder()
                .attributeName("Time").keyType(KeyType.RANGE).build();

            // Corrected: Define StatusTimeIndex GSI schema with KillStatusPartition (HASH) and Time (RANGE)
             KeySchemaElement statusPartitionKey = KeySchemaElement.builder()
                .attributeName("KillStatusPartition").keyType(KeyType.HASH).build();
             KeySchemaElement statusTimeKey = KeySchemaElement.builder()
                .attributeName("Time").keyType(KeyType.RANGE).build();

             // Build the CreateTableRequest with the table and GSI configuration
            software.amazon.awssdk.services.dynamodb.model.CreateTableRequest createTableRequest =
                software.amazon.awssdk.services.dynamodb.model.CreateTableRequest.builder()
                    .tableName(TEST_TABLE_NAME)
                    // Ensure all necessary attributes are defined
                    .attributeDefinitions(killerIdDef, timeDef, victimIdDef, gameIdDef, statusPartitionDef)
                    .keySchema(pkHash, pkRange)
                    .globalSecondaryIndexes(
                        software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex.builder()
                            .indexName(TEST_INDEX_NAME) // VictimID-Time-index
                            .keySchema(victimGsiHash, victimGsiRange)
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            // Use BillingMode PAY_PER_REQUEST for LocalStack consistency if possible
                            // .provisionedThroughput(...) // Removed for PAY_PER_REQUEST
                            .build(),
                        software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex.builder()
                            .indexName("GameIdIndex")
                            .keySchema(gameIdKey, gameTimeKey)
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            // .provisionedThroughput(...) // Removed for PAY_PER_REQUEST
                            .build(),
                        software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex.builder()
                            .indexName("StatusTimeIndex")
                            .keySchema(statusPartitionKey, statusTimeKey) // Corrected key schema
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            // .provisionedThroughput(...) // Removed for PAY_PER_REQUEST
                            .build()
                    )
                     // Use BillingMode PAY_PER_REQUEST for the table
                     .billingMode(software.amazon.awssdk.services.dynamodb.model.BillingMode.PAY_PER_REQUEST)
                     // .provisionedThroughput(...) // Removed for PAY_PER_REQUEST
                    .build();

            System.out.println("Attempting to create table: " + TEST_TABLE_NAME);
            ddbClient.createTable(createTableRequest);
            System.out.println("Table creation request sent. Waiting for table to become active...");

            // Wait until the table is active using the SDK's built-in waiter
            ddbClient.waiter().waitUntilTableExists(b -> b.tableName(TEST_TABLE_NAME));
            System.out.println("Table '" + TEST_TABLE_NAME + "' is active. Now waiting for GSI '" + TEST_INDEX_NAME + "'...");

            // Wait for the GSI to become active by polling the describeTable API
            // This is necessary because the SDK doesn't provide a waiter for GSI activation
            boolean gsiActive = false;
            long startTime = System.currentTimeMillis();
            long timeoutMillis = 60000; // 1 minute timeout for GSI activation

            while (!gsiActive && (System.currentTimeMillis() - startTime < timeoutMillis)) {
                DescribeTableRequest describeRequest = DescribeTableRequest.builder()
                    .tableName(TEST_TABLE_NAME)
                    .build();
                DescribeTableResponse describeResponse = ddbClient.describeTable(describeRequest);

                List<GlobalSecondaryIndexDescription> gsis = describeResponse.table().globalSecondaryIndexes();
                if (gsis != null) {
                    for (GlobalSecondaryIndexDescription gsiDesc : gsis) {
                        if (gsiDesc.indexName().equals(TEST_INDEX_NAME)) {
                            if (gsiDesc.indexStatus() == IndexStatus.ACTIVE) {
                                gsiActive = true;
                                System.out.println("GSI '" + TEST_INDEX_NAME + "' is now active.");
                                break; // Exit the inner loop
                            } else {
                                System.out.println("GSI '" + TEST_INDEX_NAME + "' status: " + gsiDesc.indexStatus() + ". Waiting...");
                            }
                        }
                    }
                }

                if (!gsiActive) {
                    try {
                        TimeUnit.SECONDS.sleep(2); // Wait 2 seconds before polling again
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for GSI activation", ie);
                    }
                }
            } // End while loop

             if (!gsiActive) {
                 throw new RuntimeException("Timed out waiting for GSI '" + TEST_INDEX_NAME + "' to become active.");
             }

        } catch (ResourceInUseException e) {
             // Handle the case where the table already exists (from a previous test run)
             System.out.println("Table " + TEST_TABLE_NAME + " already exists. Ensuring it's active...");
             
             // If the table exists, still wait for it and the GSI to be active
             try {
                 ddbClient.waiter().waitUntilTableExists(b -> b.tableName(TEST_TABLE_NAME));
                  System.out.println("Existing table '" + TEST_TABLE_NAME + "' is active. Checking GSI...");
                  
                  // Add GSI active check for the existing table as well
                   boolean gsiActive = false;
                    long startTime = System.currentTimeMillis();
                    long timeoutMillis = 60000; // 1 minute timeout

                    while (!gsiActive && (System.currentTimeMillis() - startTime < timeoutMillis)) {
                         DescribeTableRequest describeRequest = DescribeTableRequest.builder().tableName(TEST_TABLE_NAME).build();
                         DescribeTableResponse describeResponse = ddbClient.describeTable(describeRequest);
                         List<GlobalSecondaryIndexDescription> gsis = describeResponse.table().globalSecondaryIndexes();
                         if (gsis != null) {
                             for (GlobalSecondaryIndexDescription gsiDesc : gsis) {
                                 if (gsiDesc.indexName().equals(TEST_INDEX_NAME)) {
                                     if (gsiDesc.indexStatus() == IndexStatus.ACTIVE) {
                                         gsiActive = true;
                                         System.out.println("GSI '" + TEST_INDEX_NAME + "' on existing table is active.");
                                         break;
                                     } else {
                                          System.out.println("GSI '" + TEST_INDEX_NAME + "' on existing table status: " + gsiDesc.indexStatus() + ". Waiting...");
                                     }
                                 }
                             }
                         }
                         if (!gsiActive) {
                             try { TimeUnit.SECONDS.sleep(2); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new RuntimeException("Interrupted waiting for GSI", ie); }
                         }
                     }
                     if (!gsiActive) {
                        throw new RuntimeException("Timed out waiting for GSI '" + TEST_INDEX_NAME + "' on existing table to become active.");
                    }

             } catch (Exception waitEx) {
                 System.err.println("Error waiting for existing table or its GSI: " + waitEx.getMessage());
                 throw new RuntimeException("Failed to ensure DynamoDB table/GSI readiness for test", waitEx);
             }
        } catch (Exception e) {
            System.err.println("Error creating table or waiting: " + e.getMessage());
            e.printStackTrace(); // Print stack trace for detailed debugging
            throw new RuntimeException("Failed to set up DynamoDB table for test", e);
        }

        // Once the table and GSI are active, create the enhanced client and table reference
        enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(ddbClient)
                .build();
        killTable = enhancedClient.table(TEST_TABLE_NAME, killSchema);
        System.out.println("DynamoDB setup complete for tests.");
    }

    /**
     * Clean up resources after all tests have completed.
     * Resets the DynamoDbClientProvider and clears system properties.
     */
     @AfterAll
    static void afterAll() {
        DynamoDbClientProvider.resetClient(); // Clean up override
        System.clearProperty("KILLS_TABLE_NAME");
    }

    /**
     * Set up before each test method.
     * Creates a new instance of DynamoDbKillDao to ensure tests start with a clean state.
     */
    @BeforeEach
    void setUp() {
        // Instantiate DAO before each test using the configured client
        killDao = new DynamoDbKillDao();
        // Note: We're intentionally not cleaning the table before each test
        // to avoid potential race conditions with GSI consistency.
        // Individual tests that need a clean state should handle it themselves.
    }

    /**
     * Helper method to create a Kill object with the specified killer, victim, and time.
     * 
     * @param killerId The ID of the killer
     * @param victimId The ID of the victim
     * @param time The time of the kill in ISO-8601 format
     * @return A populated Kill object
     */
    private Kill createSampleKill(String killerId, String victimId, String time) {
        Kill kill = new Kill();
        kill.setKillerID(killerId);
        kill.setVictimID(victimId);
        kill.setTime(time);
        kill.setLatitude(10.0);
        kill.setLongitude(20.0);
        return kill;
    }

    /**
     * Helper method to wait for DynamoDB consistency.
     * GSIs have eventual consistency, which requires a brief pause in tests
     * to ensure data has propagated to indexes before querying.
     * 
     * @throws RuntimeException if the thread is interrupted while waiting
     */
    private void waitForConsistency() {
        try {
            System.out.println("Waiting for DynamoDB consistency...");
            // Increase wait time for GSI eventual consistency 
            // GSIs typically need more time than base tables for consistency
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for DynamoDB consistency", e);
        }
    }

    /**
     * Tests saving kill records and retrieving them by killer ID.
     * Verifies that the primary table queries work correctly.
     */
    @Test
    void saveAndFindKillsByKiller() {
        // Create sample kill records
        Kill kill1 = createSampleKill("killer1", "victim1", "2023-01-01T10:00:00Z");
        Kill kill2 = createSampleKill("killer1", "victim2", "2023-01-01T11:00:00Z");
        Kill kill3 = createSampleKill("killer2", "victim1", "2023-01-01T12:00:00Z");

        // Save the kill records to the database
        killDao.saveKill(kill1);
        killDao.saveKill(kill2);
        killDao.saveKill(kill3);
        
        // Wait for consistency before querying
        waitForConsistency();

        // Test querying for kills by the first killer
        List<Kill> killer1Kills = killDao.findKillsByKiller("killer1");
        
        assertNotNull(killer1Kills);
        assertEquals(2, killer1Kills.size());
        // Verify results are sorted descending by time (Sort Key)
        assertEquals("victim2", killer1Kills.get(0).getVictimID());
        assertEquals("victim1", killer1Kills.get(1).getVictimID());

        // Test querying for kills by the second killer
        List<Kill> killer2Kills = killDao.findKillsByKiller("killer2");
        assertEquals(1, killer2Kills.size());
        assertEquals("victim1", killer2Kills.get(0).getVictimID());

        // Test querying for a non-existent killer
        // Should throw KillNotFoundException
        assertThrows(KillNotFoundException.class, () -> {
            killDao.findKillsByKiller("unknown");
        });
    }

    /**
     * Tests saving kill records and retrieving them by victim ID.
     * Verifies that the Global Secondary Index queries work correctly.
     */
    @Test
    void saveAndFindKillsByVictim() {
        // Create sample kill records
        Kill kill1 = createSampleKill("killerA", "victimX", "2023-02-01T10:00:00Z");
        Kill kill2 = createSampleKill("killerB", "victimX", "2023-02-01T11:00:00Z");
        Kill kill3 = createSampleKill("killerA", "victimY", "2023-02-01T12:00:00Z");

        // Save the kill records to the database
        killDao.saveKill(kill1);
        killDao.saveKill(kill2);
        killDao.saveKill(kill3);
        
        // Wait for GSI consistency before querying the index
        waitForConsistency();

        // Test querying for kills by the first victim
        List<Kill> victimXKills = killDao.findKillsByVictim("victimX");
        
        assertNotNull(victimXKills);
        assertEquals(2, victimXKills.size());
        // Verify results are sorted descending by time (Secondary Sort Key on GSI)
        assertEquals("killerB", victimXKills.get(0).getKillerID()); 
        assertEquals("killerA", victimXKills.get(1).getKillerID());

        // Test querying for kills by the second victim
        List<Kill> victimYKills = killDao.findKillsByVictim("victimY");
        assertEquals(1, victimYKills.size());
        assertEquals("killerA", victimYKills.get(0).getKillerID());

        // Test querying for a non-existent victim
        // Should throw KillNotFoundException
        assertThrows(KillNotFoundException.class, () -> {
            killDao.findKillsByVictim("unknown");
        });
    }
    
    /**
     * Tests retrieving the most recent kills using the findRecentKills method.
     * Verifies sorting by time and respecting the limit parameter.
     */
    @Test
    void findRecentKills() {
        // Clear any existing data to ensure consistent test results
        try {
            killTable.scan().items().forEach(killTable::deleteItem);
            waitForConsistency(); // Wait for consistency after deletion
        } catch (Exception e) {
            System.err.println("Error clearing table data: " + e.getMessage());
            // Continue with test even if clear fails
        }

        // Create kills with different timestamps in chronological order
        Kill kill1 = createSampleKill("killer1", "victim1", "2023-03-01T10:00:00Z"); // Oldest
        kill1.setVerificationStatus("VERIFIED"); // Set status for GSI
        kill1.setKillStatusPartition("VERIFIED"); // Set GSI partition key

        Kill kill2 = createSampleKill("killer2", "victim2", "2023-03-02T11:00:00Z");
        kill2.setVerificationStatus("VERIFIED"); // Set status for GSI
        kill2.setKillStatusPartition("VERIFIED"); // Set GSI partition key

        Kill kill3 = createSampleKill("killer3", "victim3", "2023-03-03T12:00:00Z");
        kill3.setVerificationStatus("VERIFIED"); // Set status for GSI
        kill3.setKillStatusPartition("VERIFIED"); // Set GSI partition key

        Kill kill4 = createSampleKill("killer4", "victim4", "2023-03-04T13:00:00Z");
        kill4.setVerificationStatus("VERIFIED"); // Set status for GSI
        kill4.setKillStatusPartition("VERIFIED"); // Set GSI partition key

        Kill kill5 = createSampleKill("killer5", "victim5", "2023-03-05T14:00:00Z"); // Newest
        kill5.setVerificationStatus("VERIFIED"); // Set status for GSI
        kill5.setKillStatusPartition("VERIFIED"); // Set GSI partition key

        // Save kills
        killDao.saveKill(kill1);
        killDao.saveKill(kill2);
        killDao.saveKill(kill3);
        killDao.saveKill(kill4);
        killDao.saveKill(kill5);
        
        // Wait for consistency
        waitForConsistency();

        // Test getting 3 most recent kills
        List<Kill> recentKills = killDao.findRecentKills(3);
        
        assertNotNull(recentKills);
        assertEquals(3, recentKills.size());
        
        // Verify kills are sorted by time descending (most recent first)
        assertEquals("killer5", recentKills.get(0).getKillerID()); // Newest
        assertEquals("killer4", recentKills.get(1).getKillerID());
        assertEquals("killer3", recentKills.get(2).getKillerID());
        
        // Test limit greater than available items
        List<Kill> allKills = killDao.findRecentKills(10);
        assertEquals(5, allKills.size()); // Should return all 5 items
        
        // Clean up test data
        try {
            killTable.scan().items().forEach(killTable::deleteItem);
        } catch (Exception e) {
            System.err.println("Error clearing table data after test: " + e.getMessage());
        }
    }
    
    /**
     * Tests the exception handling for cases when no kills are found.
     * Verifies that KillNotFoundException is thrown correctly for all query methods.
     */
    @Test
    void testNoKillsFound() {
        // Clear any existing data to ensure consistent test results
        try {
            killTable.scan().items().forEach(killTable::deleteItem);
            waitForConsistency(); // Wait for consistency after deletion
        } catch (Exception e) {
            System.err.println("Error clearing table data: " + e.getMessage());
            // Continue with test even if clear fails
        }
        
        // Test exception when no kills exist for findRecentKills
        assertThrows(KillNotFoundException.class, () -> {
            killDao.findRecentKills(5);
        });
        
        // Test exception when no kills exist for findKillsByKiller
        assertThrows(KillNotFoundException.class, () -> {
            killDao.findKillsByKiller("nonexistent");
        });
        
        // Test exception when no kills exist for findKillsByVictim
        assertThrows(KillNotFoundException.class, () -> {
            killDao.findKillsByVictim("nonexistent");
        });
    }
} 