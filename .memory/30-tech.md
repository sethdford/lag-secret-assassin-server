# Assassin Game API Development Guidelines

## Java Coding Standards

### Code Organization
1. **Package Structure**:
   - `com.assassin.handlers`: API Gateway event handlers (Lambda entry points)
   - `com.assassin.service`: Business logic services
   - `com.assassin.dao`: Data access objects
   - `com.assassin.model`: Domain models and DTOs
   - `com.assassin.util`: Utility classes
   - `com.assassin.config`: Configuration and constants

2. **Class Naming Conventions**:
   - Models: Noun (e.g., `Game`, `Player`, `Kill`)
   - Services: NounService (e.g., `GameService`, `KillService`)
   - DAOs: Interface NounDao, Implementation DynamoDbNounDao
   - Handlers: NounHandler (e.g., `GameHandler`, `KillHandler`)
   - Utilities: NounUtil (e.g., `ValidationUtil`, `LocationUtil`)

3. **File Structure**:
   - Start with package declaration
   - Followed by imports (organized by: java core, third-party, project)
   - Class javadoc
   - Class declaration
   - Constants
   - Fields
   - Constructors
   - Public methods
   - Protected/package-private methods
   - Private methods
   - Inner classes/interfaces

### Coding Style
1. **Indentation**: 4 spaces (no tabs)
2. **Line Length**: 100 characters maximum
3. **Naming**:
   - Classes/Interfaces: UpperCamelCase
   - Methods/Variables: lowerCamelCase
   - Constants: UPPER_SNAKE_CASE
4. **Comments**:
   - Use JavaDoc for all public methods and classes
   - Include `@param`, `@return`, and `@throws` tags
   - Use inline comments for complex logic
5. **Exception Handling**:
   - Create custom exceptions for domain-specific errors
   - Log exceptions with appropriate context
   - Avoid empty catch blocks
   - Use try-with-resources for closeable resources

## Lambda Development Patterns

### Handler Pattern
```java
public class GameHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final GameService gameService;
    private static final Logger logger = LoggerFactory.getLogger(GameHandler.class);
    
    public GameHandler() {
        // Initialize dependencies
        this.gameService = new GameService(new DynamoDbGameDao());
    }
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        logger.info("Processing request: {}", input.getResource());
        try {
            // Extract path parameters, query string parameters, and body
            // Validate input
            // Delegate to service layer
            // Return appropriate response
        } catch (Exception e) {
            // Log and handle exceptions
            // Return error response
        }
    }
}
```

### Dependency Injection
- Use constructor injection for required dependencies
- Initialize dependencies in handler constructor
- Consider static singleton pattern for expensive resources (AWS SDK clients)

```java
// Client provider example
public class DynamoDbClientProvider {
    private static DynamoDbClient dynamoDbClient;
    private static DynamoDbEnhancedClient enhancedClient;
    
    public static synchronized DynamoDbClient getDynamoDbClient() {
        if (dynamoDbClient == null) {
            dynamoDbClient = DynamoDbClient.builder().build();
        }
        return dynamoDbClient;
    }
    
    public static synchronized DynamoDbEnhancedClient getEnhancedClient() {
        if (enhancedClient == null) {
            enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(getDynamoDbClient())
                .build();
        }
        return enhancedClient;
    }
}
```

### DAO Implementation Pattern
```java
public class DynamoDbGameDao implements GameDao {
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Game> gameTable;
    private static final Logger logger = LoggerFactory.getLogger(DynamoDbGameDao.class);
    
    public DynamoDbGameDao() {
        this.enhancedClient = DynamoDbClientProvider.getEnhancedClient();
        this.gameTable = enhancedClient.table(System.getenv("GAMES_TABLE_NAME"), 
                                             TableSchema.fromBean(Game.class));
    }
    
    @Override
    public Game getGame(String gameId) {
        try {
            return gameTable.getItem(Key.builder().partitionValue(gameId).build());
        } catch (DynamoDbException e) {
            logger.error("Failed to get game with ID {}: {}", gameId, e.getMessage());
            throw new DataAccessException("Failed to retrieve game", e);
        }
    }
    
    // Additional methods...
}
```

### Service Layer Pattern
```java
public class GameService {
    private final GameDao gameDao;
    private final PlayerDao playerDao;
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);
    
    public GameService(GameDao gameDao, PlayerDao playerDao) {
        this.gameDao = gameDao;
        this.playerDao = playerDao;
    }
    
    public Game createGame(GameCreateRequest request, String adminPlayerId) {
        // Validate input
        // Business logic
        // Data access via DAOs
        // Return result
    }
    
    // Additional methods...
}
```

## AWS SDK Patterns

### DynamoDB Enhanced Client
- Use `@DynamoDbBean` for model classes
- Define partition and sort keys with appropriate annotations
- Use converters for complex types (`@DynamoDbConvertedBy`)
- Leverage secondary indexes for efficient queries

### Error Handling
- Catch specific AWS SDK exceptions (e.g., `DynamoDbException`, `SdkClientException`)
- Create domain-specific exceptions that wrap SDK exceptions
- Include context in exceptions (e.g., operation, resource ID)

### Environment Configuration
- Use environment variables for configuration (table names, feature flags)
- Access with `System.getenv("VAR_NAME")` with appropriate fallbacks
- Define constants for environment variable names

## API Structure

### Request/Response Format
- Use consistent JSON format for all API responses
- Include standard fields: success, message, data, errors
- Follow HTTP status code conventions (200, 201, 400, 404, 500)

```json
{
  "success": true,
  "message": "Game created successfully",
  "data": {
    "gameId": "abc123",
    "gameName": "Campus Assassin 2023"
  }
}
```

### Validation
- Validate all input at the handler level
- Provide clear error messages for validation failures
- Return appropriate HTTP status codes

## Testing Standards

### Unit Testing
- Use JUnit 5 for unit tests
- Use Mockito for mocking dependencies
- Test each layer independently (handler, service, DAO)
- Target 80%+ code coverage

```java
@Test
public void testCreateGame_ValidInput_ReturnsGame() {
    // Arrange
    GameCreateRequest request = new GameCreateRequest();
    request.setGameName("Test Game");
    // Set other required fields...
    
    when(gameDao.saveGame(any(Game.class))).thenReturn(new Game());
    
    // Act
    Game result = gameService.createGame(request, "admin123");
    
    // Assert
    assertNotNull(result);
    assertEquals("Test Game", result.getGameName());
    verify(gameDao).saveGame(any(Game.class));
}
```

### Integration Testing
- Test integration between layers
- Use DynamoDB Local for data access testing
- Test API Gateway event handling end-to-end

## Logging Guidelines

### Log Levels
- ERROR: Unexpected errors that affect functionality
- WARN: Unexpected conditions that don't affect core functionality
- INFO: Normal operation events (requests, responses, state changes)
- DEBUG: Detailed information for debugging

### Log Format
- Include context (request ID, user ID, operation)
- Use structured logging where possible
- Avoid logging sensitive information (PII, credentials)

```java
logger.info("Creating game. adminPlayerId={}, gameName={}", adminPlayerId, gameName);
```

## Security Guidelines

### Input Validation
- Validate all input parameters (type, range, format)
- Sanitize strings to prevent injection attacks
- Use frameworks like Jakarta Bean Validation (where appropriate)

### Authentication/Authorization
- Always verify user identity via Cognito tokens
- Check authorization for all operations
- Implement principle of least privilege in IAM policies

### Data Protection
- Minimize sensitive data storage
- Use encryption for sensitive fields
- Implement proper data retention policies

## Performance Considerations

### Lambda Cold Start Optimization
- Keep dependencies minimal
- Initialize heavy resources outside handler method
- Use appropriate memory settings

### DynamoDB Optimization
- Design keys for efficient access patterns
- Use sparse indexes where appropriate
- Consider using batch operations for multiple items

## Deployment Guidelines

### AWS SAM Template
- Define all resources in template.yaml
- Use parameters for environment-specific values
- Follow least privilege principle for IAM roles

### CI/CD Pipeline
- Run tests before deployment
- Use environment stages (dev, test, prod)
- Include rollback strategy

## Version Control Practices

### Branching Strategy
- main: Production-ready code
- develop: Integration branch
- feature/*: New features
- fix/*: Bug fixes
- release/*: Release candidates

### Commit Messages
- Follow conventional commits format
- Include task/issue reference
- Be descriptive but concise

Example: `feat(game): add support for time-limited games (#123)` 