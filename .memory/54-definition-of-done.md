# Development Standards & Definition of Done

## Critical Definition of Done

**NO TASK CAN BE MARKED "DONE" WITHOUT MEETING ALL CRITERIA:**

### ✅ Definition of Done Checklist

**Before setting any task status to "done", verify:**

1. **Code Implementation Exists**
   - All required classes, methods, and functionality are implemented
   - Code follows Java 17+ standards and project conventions
   - No placeholder/TODO comments remain

2. **Comprehensive Tests Written**
   - Unit tests cover all public methods and business logic
   - Integration tests verify component interactions  
   - Test coverage is >80% for the implemented code
   - Edge cases and error conditions are tested

3. **All Tests Pass**
   - Run `mvn test` and verify 0 failures, 0 errors
   - Integration tests pass with real/mocked dependencies
   - No flaky or skipped tests

4. **Code Quality Verified**
   - Static analysis passes (`mvn verify`)
   - No Checkstyle, SpotBugs, or PMD violations
   - Code review standards met

5. **Documentation Updated**
   - Javadoc comments for public APIs
   - README or relevant docs updated if needed
   - API documentation reflects changes

### ⚠️ Task Verification Process

**For Every Task Completion:**

1. **Locate Implementation**: Find the actual Java classes/methods
2. **Find Test Files**: Locate corresponding test classes  
3. **Run Tests**: Execute `mvn test -Dtest=*[ClassName]*Test` 
4. **Verify Coverage**: Check test coverage reports
5. **Manual Testing**: Test functionality if integration required
6. **Only Then**: Mark task as "done"

### 🚫 Common Verification Failures

**Do NOT mark done if:**
- Tests don't exist for the implemented code
- Tests exist but are failing or skipped
- Coverage is below 80% for new/modified code
- Static analysis reports violations
- Code has TODO/FIXME comments
- Integration points aren't tested

### 📊 Quality Gates

**Minimum Requirements:**
- **Test Coverage**: >80% line coverage for new code
- **Test Success Rate**: 100% (0 failures, 0 errors)
- **Static Analysis**: 0 violations in new/modified code
- **Performance**: Integration tests complete in <30s
- **Documentation**: All public APIs documented

### 🔄 Re-verification Process

**If a "done" task fails verification:**
1. Immediately change status to "in-progress"
2. Document what's missing in task details
3. Complete missing implementations/tests
4. Re-run full verification process
5. Only then mark "done" again

### 💡 Testing Best Practices

**Unit Tests:**
- Test all public methods
- Mock external dependencies
- Test happy path + edge cases + error conditions
- Use JUnit 5 + Mockito patterns

**Integration Tests:**
- Test component interactions
- Use Testcontainers for database testing
- Test end-to-end API workflows
- Verify business logic in realistic scenarios

**Performance Tests:**
- Include basic performance assertions
- Verify no memory leaks in long-running operations
- Test concurrent access patterns where relevant

## Development Workflow Standards

### Task Implementation Process

1. **Understand Requirements**: Read task details thoroughly
2. **Identify Dependencies**: Verify all prerequisite tasks are truly complete
3. **Design Implementation**: Plan classes, methods, interfaces needed
4. **Write Failing Tests**: TDD approach - tests first
5. **Implement Code**: Make tests pass with quality implementation
6. **Verify Quality**: Run static analysis, review code
7. **Update Documentation**: Add/update Javadoc and docs
8. **Final Verification**: Complete Definition of Done checklist
9. **Mark Complete**: Only after all criteria met

### Code Standards

**Java 17+ Features:**
- Use records for data classes where appropriate
- Leverage pattern matching and switch expressions
- Use var for local variables when type is obvious
- Apply sealed classes for controlled hierarchies

**Architecture Patterns:**
- Follow SOLID principles
- Use dependency injection patterns
- Implement proper error handling with custom exceptions
- Apply builder patterns for complex object creation

**Testing Patterns:**
- Given/When/Then test structure
- Test naming: `should_[expected]_when_[condition]()`
- Use @Nested classes for grouping related tests
- Apply proper test data builders/factories

### Memory Bank Integration

**Session Updates:**
- Document verification results in 40-active.md
- Track quality metrics in 50-progress.md
- Record any Definition of Done failures and resolutions
- Update patterns/decisions based on verification learnings

**Quality Tracking:**
- Maintain test coverage metrics
- Track static analysis violation trends
- Document any quality gate adjustments
- Record lessons learned from verification failures

## Implementation Notes

**Last Updated**: [Current Session]
**Verification Standard**: Mandatory for all task completions
**Quality Gate**: >80% coverage, 0 test failures, 0 static analysis violations
**Review Process**: Self-verification required before marking any task "done" 