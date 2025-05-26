# Technical Architecture

This document outlines the technical architecture, key components, infrastructure design, and integration patterns for the Assassin Game application.

## System Architecture Overview

The Assassin Game application is built using a serverless architecture on AWS, utilizing various AWS managed services to create a scalable, cost-effective, and maintainable solution. The architecture follows cloud-native design principles with a focus on high availability, fault tolerance, and security.

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│                 │     │                 │     │                 │
│  Client App     │━━━━▶│  API Gateway    │━━━━▶│  Lambda         │
│                 │     │                 │     │                 │
└─────────────────┘     └─────────────────┘     └───────┬─────────┘
                                                        │
                                                        ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│                 │     │                 │     │                 │
│   CloudWatch    │◀━━━━│  DynamoDB       │◀━━━━│  Service Layer  │
│                 │     │                 │     │                 │
└─────────────────┘     └─────────────────┘     └───────┬─────────┘
                                                        │
                                                        ▼
                               ┌─────────────────┐     ┌─────────────────┐
                               │                 │     │                 │
                               │  S3 Bucket      │◀━━━━│  Cognito        │
                               │                 │     │                 │
                               └─────────────────┘     └─────────────────┘
```

## Core Components

### 1. API Layer

**Amazon API Gateway** serves as the entry point for all client requests, providing:

- RESTful API endpoints for game operations
- Request validation and throttling
- API key management for client authentication
- CORS support for client applications
- SSL/TLS termination

The API is organized into the following resource paths:
- `/games` - Game management operations
- `/players` - Player management operations
- `/kills` - Kill reporting and verification
- `/zones` - Safe zone and shrinking zone management
- `/admin` - Administrative operations

### 2. Compute Layer

**AWS Lambda** functions implement the business logic, with separate functions for different operations:

- `CreateGameFunction` - Creates new game instances
- `JoinGameFunction` - Handles player registration to games
- `ReportKillFunction` - Processes kill reports
- `VerifyKillFunction` - Handles kill verification
- `GetGameStateFunction` - Returns the current state of a game
- `UpdatePlayerLocationFunction` - Updates player location data
- `CreateSafeZoneFunction` - Creates safe zones
- `ShrinkZoneFunction` - Manages shrinking zone operations

Lambda functions are organized using a handler pattern that delegates to service classes for business logic implementation.

### 3. Data Layer

**Amazon DynamoDB** serves as the primary data store with the following table design:

- `Players` table - Stores player information
  - Partition key: `playerId`
  - GSI1: `gameId-status` for querying players by game and status
  - GSI2: `email` for looking up players by email

- `Games` table - Stores game information
  - Partition key: `gameId`
  - GSI1: `ownerId` for querying games by owner
  - GSI2: `status` for querying games by status

- `Kills` table - Records kill information
  - Partition key: `killId`
  - Sort key: `gameId`
  - GSI1: `killerId` for querying kills by killer
  - GSI2: `victimId` for querying kills by victim
  - GSI3: `gameId-timestamp` for querying recent kills in a game

- `SafeZones` table - Stores safe zone configurations
  - Partition key: `zoneId`
  - Sort key: `gameId`
  - GSI1: `gameId-status` for querying active safe zones

- `ShrinkingZones` table - Manages shrinking zone states
  - Partition key: `gameId`
  - GSI1: `status` for querying active shrinking zones

Data access is implemented using the DynamoDB Enhanced Client for Java to provide an object mapper for domain entities.

### 4. Authentication & Authorization

**Amazon Cognito** handles user authentication and authorization:

- User Pool manages player accounts and authentication
- Identity Pool provides temporary AWS credentials for authenticated players
- Integration with social identity providers (optional)
- JWT tokens secure API access
- Custom authorization rules enforce game-specific permissions

### 5. Storage Layer

**Amazon S3** provides object storage for:

- Profile pictures
- Game media (kill confirmation photos)
- Application static assets
- Backup data

### 6. Monitoring & Observability

**AWS CloudWatch** provides comprehensive monitoring:

- Metrics for Lambda function performance
- Logs for application diagnostics
- Alarms for critical operational thresholds
- Dashboards for operational visibility

**AWS X-Ray** enables distributed tracing:

- End-to-end request tracking
- Performance bottleneck identification
- Service dependency mapping

## Key Integration Patterns

### Event-Driven Architecture

The system leverages event-driven patterns for asynchronous operations:

1. **DynamoDB Streams** capture data changes and trigger Lambda functions:
   - Player status changes trigger target reassignment
   - Game state changes trigger notifications
   - Kill verifications trigger game state updates

2. **EventBridge** manages scheduled events:
   - Safe zone activation/deactivation
   - Shrinking zone updates
   - Player inactivity checks
   - Game state transitions

### Geospatial Processing

Location-based features are implemented using:

1. **DynamoDB Geospatial Library** for:
   - Storing and querying player locations
   - Determining proximity between players
   - Checking if players are within game boundaries

2. **Custom geometric calculations** for:
   - Safe zone containment checks
   - Shrinking zone boundary determinations
   - Distance and bearing calculations

### Caching Strategy

Performance optimization through strategic caching:

1. **DynamoDB DAX** for high-throughput read operations on:
   - Game state queries
   - Player status checks
   - Zone boundary validations

2. **Application-level caching** for:
   - Frequently accessed configuration data
   - Game boundary definitions
   - Active safe zone calculations

## Deployment & Infrastructure

The infrastructure is defined and deployed using **AWS SAM (Serverless Application Model)**:

- `template.yaml` defines all resources in a declarative manner
- CI/CD pipeline automates deployments
- Environment-specific configurations for dev, test, and production
- Infrastructure as Code (IaC) ensures consistency and repeatability

Resource organization follows AWS best practices:

- Lambda functions are grouped by domain capability
- IAM roles follow least privilege principle
- Resources are tagged for cost allocation
- Regional deployment with multi-region capability

## Security Implementation

Security is implemented at multiple layers:

1. **Network Security**:
   - API Gateway with WAF integration
   - VPC-enabled Lambda functions for secure database access (if needed)
   - Private API endpoints for administrative functions

2. **Identity Security**:
   - Cognito authentication for all user operations
   - Fine-grained IAM roles for Lambda execution
   - STS for temporary credential management

3. **Data Security**:
   - DynamoDB encryption at rest
   - S3 bucket encryption
   - HTTPS for all data in transit
   - Sensitive data handling with Parameter Store

4. **Application Security**:
   - Input validation on all API endpoints
   - Request throttling to prevent abuse
   - Comprehensive logging for security audit
   - Regular dependency updates

## Scalability Considerations

The architecture is designed for automatic scaling:

1. **Compute Scaling**:
   - Lambda concurrency scales with request volume
   - Provisioned concurrency for performance-critical functions

2. **Database Scaling**:
   - DynamoDB on-demand capacity for variable workloads
   - Auto-scaling policies for predictable patterns

3. **API Layer Scaling**:
   - API Gateway handles high volumes automatically
   - Stage-specific throttling limits

4. **Cost Management**:
   - Pay-per-use model aligns costs with usage
   - Reserved capacity for baseline workloads
   - CloudWatch Budgets for cost monitoring

## Resilience & Fault Tolerance

Resilience is built into the design:

1. **Error Handling**:
   - Comprehensive exception handling in all Lambda functions
   - Retry mechanisms for transient failures
   - Dead-letter queues for failed operations

2. **Circuit Breakers**:
   - Custom protection for external service dependencies
   - Graceful degradation for non-critical features

3. **Data Integrity**:
   - DynamoDB transactions for multi-item operations
   - Consistent read operations where required
   - Backup and recovery mechanisms

## Development & Testing Approach

The development process is structured for quality and maintainability:

1. **Local Development**:
   - SAM CLI for local Lambda testing
   - DynamoDB Local for database development
   - Mock services for external dependencies

2. **Testing Strategy**:
   - Unit tests for service and handler classes
   - Integration tests with test containers
   - End-to-end tests with deployed resources
   - Performance tests for critical operations

3. **CI/CD Pipeline**:
   - Automated builds and deployments
   - Test automation at multiple stages
   - Environment promotion workflow
   - Rollback capabilities

## System Interfaces

The application interfaces with:

1. **Client Applications**:
   - REST API for game operations
   - WebSocket API for real-time updates (future enhancement)
   - Push notification integration for alerts

2. **Administrative Portal**:
   - Secure admin API endpoints
   - Dashboard for game monitoring
   - Reporting and analytics interfaces

3. **External Services**:
   - Mapping services for geospatial visualization
   - Email/SMS providers for notifications
   - Analytics platforms for game statistics 