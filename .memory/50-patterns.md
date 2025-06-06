# System Patterns & Best Practices: Assassin Game API

## Critical Quality Patterns

### Definition of Done Verification Pattern
**Date**: 2024-12-19
**Pattern**: Mandatory verification checklist before marking any task "done"
**CRITICAL IMPORTANCE**: NO exceptions - all tasks must pass complete verification

**Pre-Completion Verification Steps:**
```bash
# 1. Verify Implementation Exists
find src/main/java -name "*[ComponentName]*.java"

# 2. Locate Test Files  
find src/test/java -name "*[ComponentName]*Test.java"

# 3. Run Specific Tests
mvn test -Dtest=*[ComponentName]*Test

# 4. Generate Coverage Report
mvn jacoco:report
open target/site/jacoco/index.html

# 5. Run Static Analysis
mvn verify

# 6. Check for TODOs/FIXMEs
grep -r "TODO\|FIXME" src/main/java/path/to/component/
```

**Quality Gates (ALL must pass):**
- ✅ Implementation complete (no TODOs/placeholders)
- ✅ Tests exist for all public methods
- ✅ Tests pass (0 failures, 0 errors)  
- ✅ Coverage >80% for modified code
- ✅ Static analysis clean (0 violations)
- ✅ Integration tests verify component interactions
- ✅ Documentation updated (Javadoc for public APIs)

**Failure Response Pattern:**
1. Change task status to "in-progress" immediately
2. Document specific failures in task details
3. Complete missing implementations/tests
4. Re-run full verification process
5. Only mark "done" after 100% compliance

### Test-Driven Development Pattern
**Pattern**: Write tests before implementation
**Benefits**: Ensures testability, catches edge cases early
**Implementation**:
1. Write failing test for required functionality
2. Implement minimal code to make test pass
3. Refactor while keeping tests green
4. Add edge case tests
5. Complete documentation

### Comprehensive Test Coverage Pattern
**Pattern**: Multi-layer testing strategy
**Components**:
- **Unit Tests**: Individual method testing with mocks
- **Integration Tests**: Component interaction testing  
- **E2E Tests**: Complete workflow testing
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
**Date**: [YYYY-MM-DD]
**Pattern**: Mode-based development workflow
**Usage**: 
- **Architect Mode**: Design, initialization, high-level planning
- **Code Mode**: Active development, implementation, testing
- **Debug Mode**: Issue investigation, troubleshooting

### Quality Verification Integration Pattern
**Pattern**: Memory Bank tracking of verification results
**Implementation**:
- Document verification results in 40-active.md
- Track quality metrics in 50-progress.md
- Record Definition of Done failures and resolutions
- Update patterns based on verification learnings

## Code Architecture Patterns

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

### AWS Lambda Handler Pattern
**Pattern**: Clean separation of concerns in Lambda handlers
**Structure**:
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

## Development Workflow Patterns

### Task Implementation Workflow Pattern
**Pattern**: Structured approach to task completion
**Workflow**:
1. **Understand**: Read task details thoroughly
2. **Plan**: Design classes, methods, interfaces needed
3. **Test First**: Write failing tests (TDD approach)
4. **Implement**: Make tests pass with quality code
5. **Verify**: Run static analysis, review code
6. **Document**: Add/update Javadoc and docs
7. **Quality Gate**: Complete Definition of Done checklist
8. **Complete**: Mark task "done" only after verification

### Git Workflow Pattern
**Pattern**: Feature branch workflow with quality gates
**Process**:
1. Create feature branch for task
2. Implement with tests
3. Run full verification locally
4. Create pull request
5. CI/CD pipeline validation
6. Code review
7. Merge to main

### Testing Strategy Pattern
**Pattern**: Comprehensive testing approach
**Layers**:
- **Unit**: Fast, isolated, mocked dependencies
- **Integration**: Component interactions, real dependencies
- **E2E**: Complete user workflows
- **Performance**: Basic timing and resource assertions

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

## Quality Assurance Patterns

### Static Analysis Integration Pattern
**Pattern**: Automated code quality enforcement
**Tools**:
- **Checkstyle**: Code style enforcement
- **SpotBugs**: Bug pattern detection
- **PMD**: Code quality metrics
- **JaCoCo**: Test coverage reporting

**Maven Integration:**
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Continuous Quality Pattern
**Pattern**: Quality validation at every step
**Implementation**:
- Pre-commit hooks for basic validation
- CI/CD pipeline quality gates
- Automated test execution
- Coverage threshold enforcement
- Static analysis violation blocking

## Performance Patterns

### Efficient DynamoDB Access Pattern
**Pattern**: Optimized database access patterns
**Techniques**:
- Batch operations where possible
- Proper GSI usage
- Consistent read/write patterns
- Connection pooling

### Lambda Cold Start Optimization Pattern
**Pattern**: Minimize Lambda cold start impact
**Techniques**:
- Dependency injection container reuse
- Connection pooling
- Lazy initialization of expensive resources
- Proper memory allocation

## Security Patterns

### Input Validation Pattern
**Pattern**: Comprehensive input sanitization
**Implementation**:
- Request object validation
- Parameter sanitization
- SQL injection prevention
- XSS protection

### Authentication/Authorization Pattern
**Pattern**: Consistent auth handling across endpoints
**Components**:
- JWT token validation
- Role-based access control
- Resource ownership verification
- Session management

## Implementation Notes

**Last Updated**: 2024-12-19
**Critical Addition**: Definition of Done verification pattern (MANDATORY)
**Quality Standard**: No task completion without full verification
**Enforcement**: Memory Bank integration tracks compliance 