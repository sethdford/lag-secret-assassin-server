# Assassin Game API Codebase Structure

## Repository Structure

The project follows a standard AWS SAM project structure with Java as the primary language.

```
assassin/
├── aws-sam-assassin/                  # Main AWS SAM project directory
│   ├── src/                           # Source code
│   │   ├── main/
│   │   │   ├── java/com/assassin/     # Java source files
│   │   │   │   ├── handler/           # Lambda function handlers
│   │   │   │   ├── dao/               # Data Access Objects
│   │   │   │   ├── model/             # Domain models
│   │   │   │   ├── service/           # Business logic services
│   │   │   │   ├── util/              # Utility classes
│   │   │   │   └── exception/         # Custom exceptions
│   │   │   └── resources/             # Configuration files
│   │   └── test/                      # Test code
│   │       └── java/com/assassin/     # Test classes matching main structure
│   ├── pom.xml                        # Maven project configuration
│   └── template.yaml                  # AWS SAM template
├── tasks/                             # Task management
│   └── tasks.json                     # Project tasks file
├── scripts/                           # Utility scripts
│   └── prd.txt                        # Project Requirements Document
└── .memory/                           # Memory bank for project context
```

## Key Components

### Lambda Function Handlers

Lambda handlers are the entry points for AWS Lambda functions, processing API Gateway requests:

```java
public class ConnectHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbPlayerDao playerDao;
    private final GameService gameService;
    
    public ConnectHandler() {
        this.playerDao = new DynamoDbPlayerDao();
        this.gameService = new GameService();
    }
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        // Request processing logic
    }
}
```

### Data Access Objects (DAOs)

DAOs handle database operations using the DynamoDB Enhanced Client for Java:

```java
public class DynamoDbPlayerDao implements PlayerDao {
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Player> playerTable;
    
    public DynamoDbPlayerDao() {
        this.enhancedClient = DynamoDbClientProvider.enhancedClient();
        this.playerTable = enhancedClient.table("Players", TableSchema.fromBean(Player.class));
    }
    
    @Override
    public Player getPlayerById(String playerId) {
        // Database access logic
    }
}
```

### Models

Domain models represent the data structures in the application:

```java
@DynamoDbBean
public class Player {
    private String playerId;
    private String gameId;
    private String targetId;
    private PlayerStatus status;
    
    @DynamoDbPartitionKey
    public String getPlayerId() {
        return playerId;
    }
    
    // Getters and setters
}
```

### Services

Services implement the business logic of the application:

```java
public class KillService {
    private final KillDao killDao;
    private final PlayerDao playerDao;
    private final NotificationService notificationService;
    
    public KillService() {
        this.killDao = new DynamoDbKillDao();
        this.playerDao = new DynamoDbPlayerDao();
        this.notificationService = new NotificationService();
    }
    
    public Kill reportKill(String killerId, String victimId, String gameId, double latitude, double longitude) {
        // Business logic for processing kill reports
    }
}
```

## Database Schema

### Players Table

```
Table: Players
- playerId (Partition Key): String
- gameId: String
- name: String
- email: String
- targetId: String
- status: String (ENUM: ACTIVE, ELIMINATED, WINNER)
- killCount: Number
- lastLocationUpdate: String (ISO timestamp)
- lastLatitude: Number
- lastLongitude: Number

GSI1:
- PK: gameId
- SK: status
```

### Games Table

```
Table: Games
- gameId (Partition Key): String
- name: String
- adminPlayerId: String
- status: String (ENUM: PENDING, ACTIVE, COMPLETED)
- startTime: String (ISO timestamp)
- endTime: String (ISO timestamp)
- settings: Map (Various game settings)
- boundary: List (Geospatial boundary coordinates)

GSI1:
- PK: adminPlayerId
- SK: status
```

### Kills Table

```
Table: Kills
- killId (Partition Key): String
- gameId: String
- killerId: String
- victimId: String
- time: String (ISO timestamp)
- latitude: Number
- longitude: Number
- verificationStatus: String (ENUM: PENDING, VERIFIED, REJECTED)
- killStatusPartition: String (For GSI)

GSI1 (StatusTimeIndex):
- PK: killStatusPartition
- SK: time
```

### SafeZones Table

```
Table: SafeZones
- safeZoneId (Partition Key): String
- gameId: String
- name: String
- type: String (ENUM: PERMANENT, TEMPORARY, SHRINKING)
- center: Map (latitude, longitude)
- radius: Number
- startTime: String (ISO timestamp)
- endTime: String (ISO timestamp)
- status: String (ENUM: ACTIVE, INACTIVE)

GSI1:
- PK: gameId
- SK: status
```

## Key Utilities

### DynamoDbClientProvider

Singleton pattern for managing DynamoDB clients:

```java
public class DynamoDbClientProvider {
    private static DynamoDbClient client;
    private static DynamoDbEnhancedClient enhancedClient;
    
    public static synchronized DynamoDbClient client() {
        if (client == null) {
            client = DynamoDbClient.builder()
                    .region(Region.US_EAST_1)
                    .build();
        }
        return client;
    }
    
    public static synchronized DynamoDbEnhancedClient enhancedClient() {
        if (enhancedClient == null) {
            enhancedClient = DynamoDbEnhancedClient.builder()
                    .dynamoDbClient(client())
                    .build();
        }
        return enhancedClient;
    }
}
```

### MapConfigurationService

Manages game boundaries and safe zones:

```java
public class MapConfigurationService {
    private final GameDao gameDao;
    private final SafeZoneDao safeZoneDao;
    
    // Methods for checking if coordinates are within game boundaries
    public boolean isWithinGameBoundary(String gameId, double latitude, double longitude) {
        // Logic to check if coordinates are within game boundary
    }
    
    // Methods for checking if coordinates are within active safe zones
    public boolean isWithinActiveSafeZone(String gameId, double latitude, double longitude) {
        // Logic to check if coordinates are within any active safe zone
    }
}
```

## Testing Strategy

The project uses JUnit 5 for unit testing with Mockito for mocking dependencies:

```java
public class KillServiceTest {
    private KillService killService;
    private KillDao killDao;
    private PlayerDao playerDao;
    private NotificationService notificationService;
    private MapConfigurationService mapService;
    
    @BeforeEach
    void setUp() {
        killDao = mock(KillDao.class);
        playerDao = mock(PlayerDao.class);
        notificationService = mock(NotificationService.class);
        mapService = mock(MapConfigurationService.class);
        
        killService = new KillService(killDao, playerDao, notificationService, mapService);
    }
    
    @Test
    void testReportKill_ValidKill_ReturnsKill() {
        // Test logic
    }
}
```

## Deployment Configuration

The AWS SAM template defines all resources and their configurations:

```yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31
Description: Assassin Game API

Globals:
  Function:
    Timeout: 30
    MemorySize: 512
    Runtime: java11
    Tracing: Active

Resources:
  PlayersTable:
    Type: AWS::DynamoDB::Table
    Properties:
      BillingMode: PAY_PER_REQUEST
      AttributeDefinitions:
        - AttributeName: playerId
          AttributeType: S
        - AttributeName: gameId
          AttributeType: S
        - AttributeName: status
          AttributeType: S
      KeySchema:
        - AttributeName: playerId
          KeyType: HASH
      GlobalSecondaryIndexes:
        - IndexName: GameStatusIndex
          KeySchema:
            - AttributeName: gameId
              KeyType: HASH
            - AttributeName: status
              KeyType: RANGE
          Projection:
            ProjectionType: ALL

  ConnectFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: ./
      Handler: com.assassin.handler.ConnectHandler::handleRequest
      Policies:
        - DynamoDBCrudPolicy:
            TableName: !Ref PlayersTable
      Events:
        Connect:
          Type: Api
          Properties:
            Path: /connect
            Method: post
```

## Development Workflow

The development workflow involves these key steps:

1. Define new features or fixes as tasks in tasks.json
2. Implement changes in the appropriate components
3. Write comprehensive unit tests
4. Update AWS SAM template if new resources are needed
5. Test locally using AWS SAM CLI
6. Deploy to development environment
7. Run integration tests
8. Deploy to production 