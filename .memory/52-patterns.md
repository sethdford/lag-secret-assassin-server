# System Patterns & Best Practices: Assassin Game API

## Critical Quality Patterns

### Definition of Done Verification Pattern
**Date**: 2024-12-19 (Successfully Implemented)
**Pattern**: Mandatory verification checklist before marking any task "done"
**Status**: ✅ ACTIVE - Successfully enforced, quality crisis resolved

**Quality Gates (ALL must pass):**
- ✅ Implementation complete (no TODOs/placeholders)
- ✅ Tests exist for all public methods
- ✅ Tests pass (0 failures, 0 errors)  
- ✅ Coverage >80% for modified code
- ✅ Static analysis clean (0 violations)
- ✅ Integration tests verify component interactions
- ✅ Documentation updated (Javadoc for public APIs)

**Success Pattern**: Java 17 + Mockito compatibility resolved testing crisis, achieving 338+ tests passing

### Test-Driven Development Pattern
**Pattern**: Write tests before implementation (enforced in Definition of Done)
**Benefits**: Ensures testability, catches edge cases early, prevents quality regressions
**Implementation**:
1. Write failing test for required functionality
2. Implement minimal code to make test pass
3. Refactor while keeping tests green
4. Add edge case tests
5. Complete documentation

### Comprehensive Test Coverage Pattern
**Pattern**: Multi-layer testing strategy (successfully implemented)
**Components**:
- **Unit Tests**: Individual method testing with mocks (338+ tests)
- **Integration Tests**: Component interaction testing  
- **E2E Tests**: Complete workflow testing (17 tests)
- **Performance Tests**: Basic performance assertions

**Test Naming Convention:**
```java
@Test
void should_[expected_behavior]_when_[specific_condition]() {
    // Given
    // When  
    // Then
}
```

## Memory Bank Workflow Patterns

### Custom Mode Usage Pattern
**Date**: 2025-01-08
**Pattern**: Mode-based development workflow using Cursor custom modes
**Usage**: 
- **Architect Mode**: Design, initialization, high-level planning
- **Code Mode**: Active development, implementation, testing
- **Debug Mode**: Issue investigation, troubleshooting, optimization
- **Update Mode**: Memory bank synchronization, progress tracking

### Memory Bank ↔ TaskMaster Synchronization Pattern
**Date**: 2025-01-08
**Pattern**: Automated synchronization between Memory Bank context and TaskMaster project state
**Implementation**:
1. **Pre-Development Sync**: `task-master next` → Update .memory/40-active.md
2. **During Development**: `task-master update-subtask` for progress tracking
3. **Post-Development Sync**: `task-master set-status` → Update memory files
4. **Weekly Reconciliation**: Compare Memory Bank with TaskMaster state

**Key Files**: `.memory/40-active.md`, `.memory/53-progress.md`
**Tools**: TaskMaster MCP tools, manual Memory Bank updates

## Code Architecture Patterns

### Hexagonal Architecture Implementation  
**Pattern**: Ports and Adapters architecture for AWS Lambda functions
**Components**:
- **Domain Layer**: Core business logic (`service` package)
- **Application Layer**: Use case orchestration (`handlers` package)  
- **Infrastructure Layer**: External integrations (`dao` package)
- **Model Layer**: Data transfer objects (`model` package)

**Example Structure**:
```
com.assassin/
├── handlers/     # Lambda entry points (Application Layer)
├── service/      # Business logic (Domain Layer)  
├── dao/          # Data access (Infrastructure Layer)
├── model/        # DTOs and entities (Model Layer)
└── exception/    # Custom exceptions
```

### Service Layer Pattern
**Pattern**: Business logic encapsulation with dependency injection
**Components**:
- **Service Classes**: Business logic implementation
- **DAO Layer**: Data access abstraction
- **Handler Layer**: AWS Lambda entry points
- **Model Layer**: Data transfer objects and entities

**Dependency Injection Pattern:**
```java
@Service
public class GameService {
    private final GameDAO gameDAO;
    private final NotificationService notificationService;
    
    public GameService(GameDAO gameDAO, NotificationService notificationService) {
        this.gameDAO = gameDAO;
        this.notificationService = notificationService;
    }
}
```

### AWS Lambda Handler Pattern
**Pattern**: Consistent request/response handling for API Gateway integration
**Components**:
- **Request/Response DTOs**: Typed input/output objects
- **Error Handling**: Standardized exception responses  
- **Dependency Injection**: Service layer integration
- **Authentication**: JWT token validation

**Implementation Structure:**
```java
@Component
public class GameHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private final GameService gameService;
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            // 1. Input validation
            // 2. Business logic delegation
            // 3. Response formatting
        } catch (Exception e) {
            // 4. Error handling
        }
    }
}
```

### Error Handling Pattern
**Pattern**: Consistent exception handling across all layers
**Implementation**:
- Custom exception hierarchy
- Proper error logging with context
- Graceful degradation where possible
- Clear error messages for client

**Exception Hierarchy:**
```java
public class AssassinGameException extends RuntimeException
public class ValidationException extends AssassinGameException  
public class NotFoundException extends AssassinGameException
public class UnauthorizedException extends AssassinGameException
```

### DynamoDB Data Access Pattern
**Pattern**: DAO pattern for DynamoDB operations with error handling
**Components**:
- **DAO Interface**: Abstract data operations
- **DynamoDB Implementation**: Concrete AWS SDK integration
- **Error Mapping**: DynamoDB exceptions to domain exceptions
- **Query Optimization**: GSI usage and projection optimization

### Safe Zone Spatial Processing Pattern
**Pattern**: Efficient geospatial operations for location-based game mechanics
**Components**:
- **Geospatial Utils**: Distance calculations, boundary checks
- **Zone Management**: Safe zone creation, modification, detection
- **Performance Optimization**: Spatial indexing, batch operations
- **Integration Points**: LocationService, SafeZoneService coordination

## Development Workflow Patterns

### Task-Driven Development Workflow
**Pattern**: Development workflow driven by TaskMaster task definitions
**Workflow**:
1. **Task Selection**: Use `task-master next` for priority-based selection
2. **Context Gathering**: Review task details and implementation requirements
3. **Implementation**: Code following hexagonal architecture patterns
4. **Testing**: Unit and integration tests for new functionality
5. **Progress Tracking**: Update TaskMaster subtasks with implementation notes
6. **Completion**: Mark task complete and move to next priority

### Task Implementation Workflow Pattern
**Pattern**: Structured approach to task completion (Definition of Done enforced)
**Workflow**:
1. **Understand**: Read task details thoroughly
2. **Plan**: Design classes, methods, interfaces needed
3. **Test First**: Write failing tests (TDD approach)
4. **Implement**: Make tests pass with quality code
5. **Verify**: Run static analysis, review code
6. **Document**: Add/update Javadoc and docs
7. **Quality Gate**: Complete Definition of Done checklist
8. **Complete**: Mark task "done" only after verification

### Testing Strategy Pattern
**Pattern**: Comprehensive testing approach for AWS Lambda functions (successfully implemented)
**Components**:
- **Unit Tests**: Service layer business logic testing (majority of 338+ tests)
- **Integration Tests**: DAO layer with DynamoDB testing
- **Handler Tests**: Lambda function request/response testing
- **End-to-End Tests**: Full API workflow validation (17 tests)

**Test Data Management:**
```java
// Test Builders Pattern
public class GameTestBuilder {
    public static Game.Builder validGame() {
        return Game.builder()
            .gameId(UUID.randomUUID().toString())
            .organizerId("test-organizer")
            .status(GameStatus.PENDING)
            .createdAt(Instant.now());
    }
}
```

### Git Workflow Pattern
**Pattern**: Structured commit and branching strategy
**Components**:
- **Feature Branches**: Task-based feature development
- **Commit Messages**: Conventional commits with task references
- **Code Review**: PR-based review process
- **Integration**: Main branch protection with CI/CD

## Performance Optimization Patterns

### DynamoDB Performance Pattern
**Pattern**: Optimized data access for high-throughput gaming operations
**Components**:
- **Single Table Design**: Minimize cross-table operations
- **GSI Strategy**: Efficient query patterns for game data
- **Batch Operations**: Reduce API calls through batching
- **Consistent Reads**: Strategic use of strong consistency

### Geolocation Performance Pattern  
**Pattern**: Optimized location processing for real-time game mechanics
**Components**:
- **Spatial Indexing**: Efficient zone boundary calculations
- **Location Caching**: Reduce computational overhead
- **Batch Updates**: Grouped location processing
- **Performance Monitoring**: Metrics for optimization opportunities

## Multi-Game Platform Patterns (Future)

### Plugin Architecture Pattern
**Pattern**: Extensible game engine supporting multiple game types
**Components**:
- **Game Type Plugins**: Modular game-specific logic
- **Universal Player State**: Cross-game player management
- **Event-Driven Engine**: Decoupled game mechanics
- **Configuration System**: Game type setup and rules

**Planned Game Types**:
1. **Assassin** (Primary) - Location-based elimination
2. **Capture The Flag** - Team-based territory control  
3. **World Heist** - Cooperative treasure hunting
