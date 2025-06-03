# System Patterns & Best Practices: Assassin Game API

## Memory Bank Workflow Patterns

### Custom Mode Usage Pattern
**Date**: [2025-01-08]
**Pattern**: Mode-based development workflow using Cursor custom modes
**Usage**: 
- **Architect Mode**: Design, initialization, high-level planning
- **Code Mode**: Active development, implementation, testing
- **Debug Mode**: Issue investigation, troubleshooting, optimization
- **Update Mode**: Memory bank synchronization, progress tracking

### Memory Bank ↔ TaskMaster Synchronization Pattern
**Date**: [2025-01-08]
**Pattern**: Automated synchronization between Memory Bank context and TaskMaster project state
**Implementation**:
1. **Pre-Development Sync**:
   ```bash
   task-master next  # Get current priority task
   # Update .memory/40-active.md with current task info
   ```

2. **During Development**:
   ```bash
   task-master update-subtask --id=X.Y --prompt="Implementation progress notes"
   # Real-time progress tracking in TaskMaster
   ```

3. **Post-Development Sync**:
   ```bash
   task-master set-status --id=X.Y --status=done
   # Update .memory/40-active.md with completion
   ```

4. **Weekly Reconciliation**:
   ```bash
   task-master list --status=done  # Check completed tasks
   task-master get-tasks           # Full project status
   # Compare with Memory Bank and resolve discrepancies
   ```

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

### AWS Lambda Handler Pattern
**Pattern**: Consistent request/response handling for API Gateway integration
**Components**:
- **Request/Response DTOs**: Typed input/output objects
- **Error Handling**: Standardized exception responses  
- **Dependency Injection**: Service layer integration
- **Authentication**: JWT token validation

**Example Implementation**:
```java
public class GameHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final GameService gameService = new GameService();
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(
        APIGatewayProxyRequestEvent input, 
        Context context
    ) {
        // Standard pattern implementation
    }
}
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

### Testing Strategy Pattern
**Pattern**: Comprehensive testing approach for AWS Lambda functions
**Components**:
- **Unit Tests**: Service layer business logic testing
- **Integration Tests**: DAO layer with DynamoDB testing
- **Handler Tests**: Lambda function request/response testing
- **End-to-End Tests**: Full API workflow validation

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
