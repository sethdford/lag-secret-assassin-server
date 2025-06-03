# Development Standards & Coding Conventions

This document outlines the development standards, coding conventions, and best practices for the Assassin Game project, with a focus on Java development for AWS Lambda and associated AWS services.

## Java Coding Standards

### Code Style & Formatting

1. **Naming Conventions**
   - Classes/Interfaces: `UpperCamelCase` (e.g., `PlayerService`, `DynamoDbClientProvider`)
   - Methods/Variables: `lowerCamelCase` (e.g., `getPlayerById`, `gameStartTime`)
   - Constants: `UPPER_SNAKE_CASE` (e.g., `MAX_PLAYERS`, `DEFAULT_TIMEOUT_SECONDS`)
   - Packages: lowercase, domain-reversed (e.g., `com.assassin.service`, `com.assassin.dao`)

2. **Indentation & Spacing**
   - Use 4 spaces for indentation (no tabs)
   - One statement per line
   - Maximum line length of 120 characters
   - Use blank lines to separate logical blocks of code

3. **Comments & Documentation**
   - Use JavaDoc for all public classes and methods
   - Include `@param`, `@return`, and `@throws` tags where applicable
   - Document complex algorithms and business logic
   - Add TODO comments for incomplete work with JIRA ticket references

4. **Import Statements**
   - No wildcard imports (`import java.util.*`)
   - Group imports by domain (java, javax, com.amazonaws, com.assassin)
   - Remove unused imports

### Code Organization

1. **Package Structure**
   ```
   com.assassin
   ├── config        // Configuration classes
   ├── dao           // Data access objects
   ├── exception     // Custom exceptions
   ├── handler       // Lambda handlers
   ├── model         // Domain model objects
   ├── service       // Business logic services
   ├── util          // Utility classes
   └── validation    // Input validation
   ```

2. **Class Structure**
   - Order class elements consistently:
     1. Static fields
     2. Instance fields
     3. Constructors
     4. Public methods
     5. Protected methods
     6. Private methods
     7. Inner classes/interfaces

3. **Field Declaration**
   - Declare fields at the top of the class
   - Group fields by access level (public, protected, private)
   - Initialize fields at declaration when possible
   - Make fields `final` when applicable

4. **Method Design**
   - Keep methods focused on a single responsibility
   - Limit method length (< 50 lines as guideline)
   - Favor readability over cleverness
   - Return defensive copies of mutable objects

### Java Best Practices

1. **Null Handling**
   - Use `java.util.Optional` for methods that might not return a value
   - Perform null checks on all method parameters
   - Document nullable parameters and return values
   - Use `Objects.requireNonNull()` for mandatory parameters

2. **Exception Handling**
   - Create custom exceptions for domain-specific errors
   - Use checked exceptions for recoverable errors
   - Use unchecked exceptions for programming errors
   - Always clean up resources in a `finally` block or use try-with-resources
   - Log exceptions with appropriate context

3. **Immutability**
   - Make domain objects immutable when possible
   - Use builder pattern for complex object construction
   - Avoid setters on model classes where updates are rare
   - Return defensive copies of mutable fields

4. **Generics and Collections**
   - Specify generic types fully (no raw types)
   - Use interface types for variables (e.g., `List` instead of `ArrayList`)
   - Choose appropriate collection types based on access patterns
   - Consider immutable collections where applicable
   - Use streams for collection processing when it improves readability

## AWS Lambda Development Standards

### Lambda Handler Design

1. **Handler Structure**
   - Implement `RequestHandler<TInput, TOutput>` interface
   - Keep handlers thin, delegating business logic to services
   - Handle JSON parsing/formatting within the handler
   - Use dependency injection for services

   ```java
   public class GetPlayerHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
       private final PlayerService playerService;
       private final ObjectMapper objectMapper;
       
       public GetPlayerHandler() {
           this(new PlayerService(), new ObjectMapper());
       }
       
       // Constructor for dependency injection and testing
       public GetPlayerHandler(PlayerService playerService, ObjectMapper objectMapper) {
           this.playerService = playerService;
           this.objectMapper = objectMapper;
       }
       
       @Override
       public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
           try {
               // Extract parameters
               String playerId = input.getPathParameters().get("playerId");
               
               // Invoke service
               Player player = playerService.getPlayerById(playerId);
               
               // Format response
               return ApiGatewayResponse.builder()
                   .withStatusCode(200)
                   .withBody(objectMapper.writeValueAsString(player))
                   .build();
           } catch (PlayerNotFoundException e) {
               return ApiGatewayResponse.builder()
                   .withStatusCode(404)
                   .withBody("{\"message\":\"Player not found\"}")
                   .build();
           } catch (Exception e) {
               // Log the error
               context.getLogger().log("Error: " + e.getMessage());
               return ApiGatewayResponse.builder()
                   .withStatusCode(500)
                   .withBody("{\"message\":\"Internal server error\"}")
                   .build();
           }
       }
   }
   ```

2. **Request Validation**
   - Validate all input parameters before processing
   - Return appropriate HTTP status codes for validation errors
   - Use custom validators or validation libraries when appropriate
   - Document validation rules in method JavaDoc

3. **Response Formatting**
   - Use consistent response structure
   - Include appropriate HTTP headers (CORS, content type)
   - Format error responses with clear messages and status codes
   - Consider pagination for responses with potentially large result sets

### AWS SDK Usage

1. **Client Management**
   - Use DynamoDbClientProvider for efficient client reuse
   - Initialize clients outside the handler method
   - Configure clients appropriately (retries, timeouts)
   - Use specific AWS SDK v2 service clients

   ```java
   public class DynamoDbClientProvider {
       private static volatile DynamoDbClient dynamoDbClient;
       private static volatile DynamoDbEnhancedClient enhancedClient;
       private static final Logger logger = LoggerFactory.getLogger(DynamoDbClientProvider.class);
       
       public static DynamoDbClient getDynamoDbClient() {
           if (dynamoDbClient == null) {
               synchronized (DynamoDbClientProvider.class) {
                   if (dynamoDbClient == null) {
                       logger.info("Initializing DynamoDB client");
                       dynamoDbClient = DynamoDbClient.builder()
                           .region(Region.of(System.getenv("AWS_REGION")))
                           .build();
                   }
               }
           }
           return dynamoDbClient;
       }
       
       public static DynamoDbEnhancedClient getEnhancedClient() {
           if (enhancedClient == null) {
               synchronized (DynamoDbClientProvider.class) {
                   if (enhancedClient == null) {
                       logger.info("Initializing DynamoDB enhanced client");
                       enhancedClient = DynamoDbEnhancedClient.builder()
                           .dynamoDbClient(getDynamoDbClient())
                           .build();
                   }
               }
           }
           return enhancedClient;
       }
   }
   ```

2. **DynamoDB Interaction**
   - Use the Enhanced Client API for model mapping
   - Use explicit attribute converters for complex types
   - Leverage batch operations for efficiency
   - Design queries to avoid table scans
   - Use appropriate key conditions and filter expressions

3. **Error Handling**
   - Handle AWS SDK exceptions appropriately
   - Implement retry logic for transient failures
   - Log relevant details for troubleshooting
   - Map SDK exceptions to appropriate API responses

### Lambda Optimization

1. **Cold Start Mitigation**
   - Initialize heavy resources outside the handler method
   - Keep dependencies minimal
   - Use Provisioned Concurrency for critical functions
   - Consider Java 17 features for faster startup

2. **Memory Configuration**
   - Balance memory allocation for cost and performance
   - Benchmark functions with different memory settings
   - Consider CPU-bound vs. IO-bound operations
   - Document memory allocation decisions

3. **Timeout Settings**
   - Set appropriate timeouts based on function complexity
   - Handle long-running operations asynchronously
   - Implement circuit breakers for external dependencies
   - Monitor execution times and adjust settings

4. **Environment Variables**
   - Use environment variables for configuration
   - Never hardcode credentials or sensitive values
   - Use parameter naming conventions
   - Document required environment variables

## Testing Standards

### Unit Testing

1. **Test Coverage**
   - Minimum 80% code coverage for all business logic
   - 100% coverage for critical components
   - Test both success and failure paths
   - Cover edge cases and boundary conditions

2. **Test Structure**
   - Use descriptive method names (`should_ReturnPlayer_When_ValidIdProvided`)
   - Structure tests in Arrange-Act-Assert pattern
   - Group related tests in nested classes
   - Use appropriate assertions with meaningful messages

3. **Mocking Strategy**
   - Use Mockito for dependency mocking
   - Mock external services and DAOs
   - Use `verify()` to ensure methods are called correctly
   - Reset mocks between tests if necessary

   ```java
   @Test
   public void should_ReturnPlayer_When_ValidIdProvided() {
       // Arrange
       String playerId = "player123";
       Player expectedPlayer = new Player();
       expectedPlayer.setPlayerId(playerId);
       expectedPlayer.setName("Test Player");
       
       when(playerDao.getPlayerById(playerId)).thenReturn(expectedPlayer);
       
       // Act
       Player result = playerService.getPlayerById(playerId);
       
       // Assert
       assertNotNull(result);
       assertEquals(playerId, result.getPlayerId());
       assertEquals("Test Player", result.getName());
       verify(playerDao, times(1)).getPlayerById(playerId);
   }
   ```

4. **Test Data Management**
   - Use factory methods for test data creation
   - Avoid duplicate test data setup
   - Use parameterized tests for multiple data variations
   - Reset test state between test runs

### Integration Testing

1. **DynamoDB Local Testing**
   - Use DynamoDB Local for DAO integration tests
   - Create tables programmatically before tests
   - Clean up data after tests
   - Test actual query patterns

2. **AWS SDK Testing**
   - Use AWS SDK mocking for integration tests
   - Test actual SDK client calls where appropriate
   - Verify correct AWS resource interaction
   - Use localstack for extended AWS service testing

3. **Lambda Function Testing**
   - Test handlers with simulated API Gateway events
   - Create realistic Context objects
   - Test full request-response cycles
   - Verify correct error handling and status codes

### Test Automation

1. **CI/CD Pipeline Integration**
   - Run all tests on pull requests
   - Enforce code coverage thresholds
   - Generate test reports for review
   - Block merges if tests fail

2. **Test Categories**
   - Separate fast unit tests from slower integration tests
   - Use JUnit categories or tags to organize tests
   - Configure test suites for different purposes
   - Document test execution requirements

## Logging & Monitoring Standards

### Logging

1. **Log Configuration**
   - Use SLF4J as the logging facade
   - Configure appropriate log levels by environment
   - Use structured JSON logging format
   - Include request IDs for correlation

2. **Log Content**
   - Log request/response details (excluding sensitive data)
   - Include contextual information (user ID, game ID)
   - Use appropriate log levels (ERROR, WARN, INFO, DEBUG)
   - Log performance metrics for critical operations

   ```java
   public Player getPlayerById(String playerId) {
       MDC.put("playerId", playerId);
       logger.info("Retrieving player by ID");
       
       try {
           Player player = playerDao.getPlayerById(playerId);
           if (player == null) {
               logger.warn("Player not found");
               throw new PlayerNotFoundException("Player not found with ID: " + playerId);
           }
           logger.debug("Player retrieved successfully: {}", player.getName());
           return player;
       } catch (Exception e) {
           logger.error("Error retrieving player", e);
           throw e;
       } finally {
           MDC.remove("playerId");
       }
   }
   ```

3. **Log Management**
   - Configure log retention periods
   - Implement log aggregation
   - Set up log-based alerts for critical errors
   - Document logging conventions

### Monitoring

1. **CloudWatch Metrics**
   - Track custom business metrics
   - Monitor Lambda execution metrics
   - Track DynamoDB throughput and latency
   - Create dashboards for key metrics

2. **Alarming**
   - Set up alarms for error thresholds
   - Monitor Lambda throttling and errors
   - Alert on unusual patterns
   - Define escalation procedures

3. **Tracing**
   - Configure X-Ray tracing
   - Add custom annotations for business events
   - Monitor service dependencies
   - Analyze performance bottlenecks

## Security Standards

### Authentication & Authorization

1. **Cognito Integration**
   - Validate JWT tokens for all protected endpoints
   - Extract and verify user claims
   - Implement role-based access control
   - Use custom authorizers for fine-grained permissions

2. **Input Validation**
   - Validate and sanitize all user input
   - Protect against injection attacks
   - Use parameterized queries for DynamoDB
   - Implement request throttling

### Data Protection

1. **Sensitive Data Handling**
   - Never log sensitive information
   - Use encryption for sensitive data at rest
   - Use HTTPS for all API communications
   - Implement data masking where appropriate

2. **IAM Permissions**
   - Follow least privilege principle
   - Use specific resource ARNs in policies
   - Review permissions regularly
   - Document IAM role requirements

## Deployment & Release Standards

### Infrastructure as Code

1. **AWS SAM Templates**
   - Define all resources in SAM template
   - Use parameters for environment-specific values
   - Document resource configurations
   - Include comments for complex resources

2. **Environment Management**
   - Maintain separate environments (dev, test, prod)
   - Use consistent naming conventions
   - Document environment differences
   - Control access to production environments

### Release Process

1. **Versioning**
   - Follow semantic versioning (MAJOR.MINOR.PATCH)
   - Document release contents
   - Tag releases in Git
   - Maintain a changelog

2. **Deployment Strategy**
   - Use automated deployments
   - Implement staged rollouts
   - Have rollback procedures
   - Verify deployments with smoke tests

## Code Review Standards

### Review Process

1. **Pre-Submission Checklist**
   - Run all tests locally
   - Check code formatting
   - Review for security issues
   - Ensure documentation is updated

2. **Review Criteria**
   - Code correctness and reliability
   - Security considerations
   - Performance implications
   - Adherence to standards
   - Test coverage

3. **Review Etiquette**
   - Provide constructive feedback
   - Focus on code, not the author
   - Explain the reasoning behind suggestions
   - Be timely with reviews

### Continuous Improvement

1. **Technical Debt Management**
   - Track technical debt in JIRA
   - Allocate time for refactoring
   - Document architectural decisions
   - Review and update standards regularly 