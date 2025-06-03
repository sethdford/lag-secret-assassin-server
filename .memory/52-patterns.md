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

## Code Architecture Patterns

### Hexagonal Architecture Implementation  
**Pattern**: Ports and Adapters architecture for AWS Lambda functions
**Components**:
- **Domain Layer**: Core business logic (`service` package)
- **Application Layer**: Use case orchestration (`handlers` package)  
- **Infrastructure Layer**: External integrations (`dao`, `config` packages)
- **Ports**: Interfaces defining contracts between layers

### AWS Service Integration Patterns
**Pattern**: Singleton AWS clients with thread-safe initialization
**Implementation**:
- Static initialization blocks for service clients
- Reuse connections across Lambda invocations
- Environment-based configuration injection
- Consistent error handling across all AWS integrations

### DynamoDB Access Patterns
**Pattern**: Single-table design with strategic use of GSIs
**Key Patterns**:
- Primary Key: Entity type + unique identifier
- Sort Keys: Hierarchical data organization
- GSI1: Query patterns for game-related entities
- GSI2: Location-based queries for proximity detection

## Testing Patterns

### Test Structure Organization
**Pattern**: Mirrored test package structure with specialized test types
**Organization**:
- **Unit Tests**: Isolated component testing
- **Integration Tests**: DynamoDB Local + AWS SDK testing
- **E2E Tests**: Full Lambda handler execution testing
- **Performance Tests**: Load testing for critical paths

### Mock Strategy Pattern
**Pattern**: Selective mocking based on test boundaries
**Strategy**:
- Mock external AWS services in unit tests
- Use DynamoDB Local for integration tests
- Real AWS services only in E2E tests
- Mockito for service layer dependency injection

## Development Workflow Patterns

### TaskMaster Integration Pattern
**Pattern**: AI-powered task breakdown and progress tracking
**Workflow**:
1. High-level task definition in tasks.json
2. AI-powered subtask expansion for complex features
3. Dependency-driven development order
4. Regular status updates and progress tracking

### Code Quality Assurance Pattern  
**Pattern**: Multi-layer quality enforcement
**Layers**:
- Static analysis (Checkstyle, SpotBugs, PMD)
- Unit test coverage requirements
- Integration test validation
- Manual code review checkpoints
