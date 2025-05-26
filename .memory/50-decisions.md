# Technical Decisions

This document captures key technical decisions made during the development of the Assassin Game project, including their rationale and implications.

## Infrastructure Decisions

### AWS SAM for Infrastructure as Code
**Decision**: Use AWS SAM (Serverless Application Model) for defining and deploying infrastructure.

**Rationale**:
- Simplified syntax compared to raw CloudFormation
- Built-in support for common serverless patterns
- Local testing capabilities with SAM CLI
- Seamless integration with AWS CI/CD services

**Implications**:
- Requires learning SAM-specific constructs
- Some complex scenarios may require custom CloudFormation
- Team must standardize on SAM CLI for local development

### DynamoDB as Primary Database
**Decision**: Use Amazon DynamoDB as the primary data store.

**Rationale**:
- Serverless and fully managed
- Auto-scaling capabilities match Lambda's scaling model
- Millisecond response times for game operations
- No connection management overhead
- Support for geo-spatial indexing via libraries

**Implications**:
- Requires careful data modeling (single-table design)
- Query patterns must be known in advance
- Limited transaction capabilities compared to relational databases
- Different cost model based on reads/writes and storage

### Cognito for Authentication
**Decision**: Use Amazon Cognito for player authentication and identity management.

**Rationale**:
- Managed auth service eliminates need for custom implementation
- Supports multiple authentication providers
- Integrates with API Gateway for JWT validation
- Provides user pools and identity pools for authorization

**Implications**:
- Authentication flow complexity increases
- Token handling logic required in client applications

## Development Decisions

### Java for Lambda Implementation
**Decision**: Use Java 17 as the primary language for Lambda functions.

**Rationale**:
- Strong typing helps catch errors at compile time
- Mature ecosystem with robust libraries
- Team expertise in Java development
- Good support in AWS SDK and tooling
- LTS support from AWS Lambda

**Implications**:
- Higher cold start times compared to Node.js or Python
- Need to optimize Lambda memory settings
- Careful dependency management to control package size
- Need for efficient initialization patterns

### Single-Table Design Pattern
**Decision**: Implement a single-table design for DynamoDB where appropriate.

**Rationale**:
- Reduces overall provisioned throughput costs
- Enables complex transactions within a single table
- Simplifies backup and restore operations
- Optimizes for access patterns specific to the game

**Implications**:
- More complex data modeling
- Requires careful planning of partition/sort keys
- Potentially more verbose query code
- Need for specialized access patterns

### Custom Request Validation
**Decision**: Implement custom request validation in Lambda handlers rather than API Gateway request validators.

**Rationale**:
- More flexible validation rules
- Better error messages for clients
- Ability to apply business logic during validation
- Reusable validation components across handlers

**Implications**:
- Increased Lambda execution time for validation
- Validation code must be maintained alongside handler code
- Need for careful error handling patterns

## Architecture Decisions

### Hexagonal Architecture
**Decision**: Implement a hexagonal (ports and adapters) architecture for the application.

**Rationale**:
- Clear separation between domain logic and external systems
- Easier to test business logic in isolation
- Flexibility to change infrastructure components
- Better organization of codebase

**Implications**:
- More interfaces and abstractions
- Initial development overhead
- Need for dependency injection
- Learning curve for developers new to the pattern

### Singleton AWS Clients
**Decision**: Implement AWS service clients as thread-safe singletons.

**Rationale**:
- Reuse connections across Lambda invocations within the same container
- Reduce initialization overhead
- Consistent configuration across the application
- Avoid resource leaks

**Implications**:
- Need for thread-safe implementation
- Careful handling of client configuration
- Testing complexity due to static references

### Service-Oriented Domain Model
**Decision**: Organize business logic into service classes focused on specific domain areas.

**Rationale**:
- Clear separation of responsibilities
- Focused units of business logic
- Easier to test individual services
- Natural boundaries for transaction scopes

**Implications**:
- Need to manage service dependencies
- Potential for service classes to grow too large
- Interface design becomes critical

## Game Mechanics Decisions

### Shrinking Zone Implementation
**Decision**: Implement shrinking safe zone using time-based zone updates.

**Rationale**:
- Creates dynamic gameplay that forces player movement
- Increases encounter probability as game progresses
- Configurable shrinking rate based on game parameters
- Predictable system behavior for players

**Implications**:
- Need for efficient geospatial calculations
- Regular zone update processing
- Client applications must handle zone boundary changes
- Additional complexity in game rules

### Location Update Frequency
**Decision**: Limit player location updates to once per minute per player.

**Rationale**:
- Balances real-time gameplay needs with system load
- Reduces DynamoDB write capacity needs
- Minimizes battery impact on mobile devices
- Sufficient for gameplay mechanics

**Implications**:
- Game mechanics must account for potentially stale location data
- Client applications must handle location update throttling gracefully
- Need for client-side timestamps to validate update sequence

### Kill Verification System
**Decision**: Implement a dual verification system with both automatic and manual options.

**Rationale**:
- Automatic verification based on proximity reduces admin burden
- Manual verification provides fallback for technical issues
- Combination ensures game integrity
- Configurable verification rules per game

**Implications**:
- More complex state management for kills
- Need for admin interface for manual verification
- Additional database queries for verification status
- Potential for verification disputes

## Testing Decisions

### Local DynamoDB for Integration Tests
**Decision**: Use DynamoDB Local for integration testing.

**Rationale**:
- No cost for test execution
- Faster test execution without network latency
- Isolated test environment
- No risk of affecting production data

**Implications**:
- Need to maintain consistency with AWS DynamoDB behavior
- Additional setup in CI/CD pipeline
- Potential for behavior differences between local and cloud

### Mockito for Unit Testing
**Decision**: Use Mockito for mocking dependencies in unit tests.

**Rationale**:
- Industry standard for Java mocking
- Rich feature set for verifying interactions
- Good integration with JUnit
- Familiar to the development team

**Implications**:
- Learning curve for advanced mocking scenarios
- Need for careful test design to avoid brittle tests
- Potential for mock objects to diverge from real implementations

## Security Decisions

### Least Privilege IAM Roles
**Decision**: Implement function-specific IAM roles with minimal permissions.

**Rationale**:
- Follows security best practice of least privilege
- Limits impact of potential security breaches
- Makes permission requirements explicit
- Enables fine-grained security auditing

**Implications**:
- More complex IAM configuration
- Need to carefully identify required permissions
- Potential for permission-related deployment issues

### API Authorization Levels
**Decision**: Implement three levels of API authorization: public, player, and admin.

**Rationale**:
- Public endpoints needed for game discovery and registration
- Player-specific endpoints for gameplay actions
- Admin endpoints for game management
- Clear security boundaries

**Implications**:
- More complex authorization logic
- Need for role-based access control
- Additional testing scenarios for security
- Careful JWT claims management

## Feature Implementation Decisions

### Privacy Controls (Task 13)
**Decision**: Implemented comprehensive privacy controls for player location data.

**Rationale**:
- Enhance player safety and control over their data.
- Comply with potential data privacy regulations.
- Provide players with flexible options for location sharing.

**Key Features Implemented**:
- **Location Visibility Settings**: Players can choose who sees their location (e.g., only hunter/target, all in game, friends, hidden).
- **Manual Location Sharing Pause**: Players can manually pause and resume location sharing, with a cooldown period to prevent abuse.
- **Location Precision/Fuzzing**: Players can choose to share their precise location or a fuzzed/approximated location (e.g., reduced to 100m, 500m, or with added noise).
- **Automatic Location Pausing for Sensitive Areas**: System automatically pauses location sharing if a player enters a predefined sensitive area (e.g., hospital, school). This is a system-level override.
- **Audit Logging**: All changes to privacy settings are logged for auditing purposes.

**Implications**:
- Increased complexity in location retrieval logic, as it must respect all active privacy settings.
- Additional API endpoints for managing these settings.
- Player model and service layer updated to store and manage these settings.
- Unit tests added to cover various privacy scenarios.
- Location-dependent game mechanics (e.g., kill verification, target proximity) must use the `getEffectivePlayerLocation` service method to ensure privacy settings are respected.

### Stripe SDK Version Downgrade
**Decision**: Downgraded Stripe Java SDK from `29.1.0` to `24.0.0`.

**Rationale**:
- Encountered persistent compilation errors with SDK version `29.1.0` related to `Subscription.getCurrentPeriodEnd()`, `Invoice.getSubscription()`, `Invoice.getSubscriptionItem()`, and `SubscriptionItem.getSubscription()` methods not being found, despite documentation suggesting they should exist.
- Multiple troubleshooting steps (clean verify, deleting local .m2 cache, explicit casting) did not resolve the issue with `29.1.0`.
- Downgrading to `24.0.0` resolved all compilation errors and allowed the build to succeed.

**Implications**:
- The project now uses an older version of the Stripe SDK. This might mean missing out on newer features or security updates present in later versions.
- Future Stripe-related development should be tested against `24.0.0`.
- A future task should be created to investigate upgrading the Stripe SDK again if the issues with newer versions are resolved or better understood.

## Operational Decisions

### CloudWatch Alarms for Critical Metrics
**Decision**: Implement CloudWatch alarms for key operational metrics.

**Rationale**:
- Proactive notification of system issues
- Visibility into performance bottlenecks
- Clear thresholds for intervention
- Integration with on-call notification systems

**Implications**:
- Need to identify meaningful alarm thresholds
- Cost implications for CloudWatch metrics and alarms
- Alert fatigue risk if not properly tuned
- Requirement for operational response procedures

### Structured JSON Logging
**Decision**: Implement structured JSON logging for all Lambda functions.

**Rationale**:
- Machine-parsable log format
- Easier log analysis in CloudWatch Logs Insights
- Consistent log structure across the application
- Ability to extract metrics from logs

**Implications**:
- More complex logging configuration
- Slightly increased log storage costs
- Need for log parsing utilities
- Learning curve for log query syntax

### Stripe SDK Version
**Decision**: Downgraded Stripe Java SDK from 29.1.0 to 24.0.0.
**Rationale**:
- Encountered "cannot find symbol" compilation errors with version 29.1.0 for methods like `Subscription.getCurrentPeriodEnd()` and `Invoice.getSubscription()`.
- These methods were confirmed to exist in the Stripe API documentation for recent versions.
- Attempts to resolve via classpath cleaning, forced dependency updates, and explicit casting were unsuccessful.
- Downgrading to 24.0.0 resolved all compilation errors related to these Stripe SDK calls.
**Implications**:
- The project will use Stripe SDK v24.0.0.
- Future upgrades to the Stripe SDK will require careful testing to ensure compatibility with existing method calls.
- May miss out on features or fixes available in versions between 24.0.0 and the latest (currently 29.x.x). 