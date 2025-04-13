# Assassin Game API Architecture

## Overview

The Assassin Game API follows a serverless architecture built on AWS services, using AWS SAM (Serverless Application Model) for infrastructure as code. The application employs a microservices approach with AWS Lambda functions handling specific domains of functionality, and Amazon DynamoDB as the persistent data store.

## Architecture Diagram

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│                 │     │                 │     │                 │
│  Mobile Clients │────▶│  API Gateway    │────▶│  Lambda         │
│  (iOS/Android)  │     │  (REST API)     │     │  Functions      │
│                 │◀────│                 │◀────│                 │
└─────────────────┘     └─────────────────┘     └────────┬────────┘
                                                         │
                        ┌─────────────────┐              │
                        │                 │              │
                        │  Amazon Cognito │◀─────────────┘
                        │  User Pools     │              │
                        │                 │              │
                        └─────────────────┘              │
                                                         │
┌─────────────────┐     ┌─────────────────┐     ┌────────▼────────┐
│                 │     │                 │     │                 │
│  Amazon SNS     │◀────│  Lambda         │◀────│  DynamoDB       │
│  Notifications  │     │  Functions      │     │  Tables         │
│                 │     │                 │     │                 │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

## Key Components

### API Layer

1. **Amazon API Gateway**
   - Serves as the entry point for all client requests
   - Provides RESTful API endpoints with proper resource paths
   - Implements request validation and API key management
   - Handles CORS for browser-based clients
   - Routes requests to appropriate Lambda functions

2. **Amazon Cognito User Pools**
   - Manages user authentication and authorization
   - Provides JWT tokens for API access
   - Supports user registration and profile management
   - Integrates with API Gateway for request authorization

### Compute Layer

3. **AWS Lambda Functions**
   - **PlayerManagementFunction**: Handle player registration, profile updates
   - **GameManagementFunction**: Create, update, delete, and query games
   - **KillReportingFunction**: Manage kill reports, confirmations, and verifications
   - **TargetAssignmentFunction**: Assign and reassign targets among players
   - **NotificationFunction**: Generate and send notifications to players
   - **LocationHandlerFunction**: Process location updates and proximity events
   - **SafeZoneHandlerFunction**: Manage and evaluate safe zone status and effects

### Data Layer

4. **Amazon DynamoDB Tables**
   - **PlayerTable**: Store player profiles and authentication details
   - **GameTable**: Store game configurations, rules, and status
   - **KillTable**: Track kill reports, confirmations, and related data
   - **NotificationTable**: Store notification data and delivery status
   - **PlayerGameStateTable**: Track player state within specific games
   - **GameZoneStateTable**: Store game boundary and safe zone information
   - **LocationHistoryTable**: Record player location history for verification

### Notification Layer

5. **Amazon SNS**
   - Deliver push notifications to mobile devices
   - Support for both iOS and Android platforms
   - Handle message formatting and delivery tracking

## Data Flow

### Player Registration and Authentication
1. Player registers through mobile app
2. Cognito creates user account and returns credentials
3. Player authenticates and receives JWT token
4. Token is used for subsequent API calls

### Game Creation
1. Authenticated admin player creates game via API
2. API Gateway validates request and routes to GameManagementFunction
3. Lambda function creates game record in GameTable
4. Confirmation response returned to client

### Player Joining Game
1. Player requests to join game
2. GameManagementFunction validates request
3. Player added to game's player list
4. PlayerGameStateTable updated with initial player state
5. Notification sent to player and game admin

### Location Update
1. Player app sends location update to API
2. LocationHandlerFunction processes update
3. Location stored in LocationHistoryTable
4. Proximity checks performed against other players
5. Safe zone status evaluated
6. State changes trigger appropriate game actions

### Kill Reporting
1. Player reports kill through app
2. KillReportingFunction validates kill report
3. Kill record created in KillTable
4. Verification process initiated (password, proximity, etc.)
5. Notifications sent to relevant players
6. Game state updated based on verification outcome

## System Properties

### Scalability
- Serverless architecture automatically scales based on request volume
- DynamoDB on-demand capacity mode handles variable workloads
- Distributed architecture allows independent scaling of components

### Reliability
- AWS managed services provide high availability
- Redundancy across multiple availability zones
- Stateless Lambda functions improve fault tolerance
- DynamoDB global tables for multi-region resilience

### Security
- Cognito authentication and authorization
- IAM roles for fine-grained access control
- API Gateway request validation
- Data encryption at rest and in transit
- Input sanitization and validation

### Monitoring and Observability
- CloudWatch Logs for centralized logging
- CloudWatch Metrics for performance monitoring
- X-Ray for distributed tracing
- CloudWatch Alarms for anomaly detection
- SNS for operational notifications

## Deployment and CI/CD

### AWS SAM
- Infrastructure as Code for consistent deployments
- Automated resource provisioning
- Environment-specific configurations

### CI/CD Pipeline
- GitHub Actions for automated builds and tests
- Multi-stage deployment process (dev, test, prod)
- Automated testing before deployment
- Rollback capabilities for failed deployments

## Database Design

### DynamoDB Access Patterns

**PlayerTable**
- Partition Key: `playerId` (UUID)
- GSI1-PK: `email` for email lookup

**GameTable**
- Partition Key: `gameId` (UUID)
- GSI1-PK: `adminPlayerId` for admin's games lookup
- GSI2-PK: `status` for active game queries

**KillTable**
- Partition Key: `killId` (UUID)
- GSI1-PK: `gameId`, GSI1-SK: `timestamp` for game's kills lookup
- GSI2-PK: `killerId` for killer's kills lookup
- GSI3-PK: `victimId` for victim's deaths lookup

**PlayerGameStateTable**
- Partition Key: `playerId`, Sort Key: `gameId` for player's game state
- GSI1-PK: `gameId`, GSI1-SK: `status` for game's player status queries

**GameZoneStateTable**
- Partition Key: `gameId` for game's boundary and safe zone data
- Contains boundary polygon and active safe zones

**SafeZoneTable**
- Partition Key: `safeZoneId` (UUID)
- GSI1-PK: `gameId` for game's safe zones lookup
- Types include permanent, temporary, and shrinking zones

## Integration Points

1. **Mobile Client Integration**
   - RESTful API with JSON payloads
   - Push notification delivery via FCM and APNS
   - Authentication flow with Cognito SDK

2. **Third-Party Services**
   - Maps API for geolocation data
   - Social media sharing integrations
   - Analytics services for usage tracking

## Performance Considerations

1. **DynamoDB Optimization**
   - Appropriate capacity mode selection (on-demand vs. provisioned)
   - Efficient access patterns to avoid scans
   - Careful selection of partition and sort keys
   - Use of TTL for ephemeral data (location history)

2. **Lambda Optimization**
   - Appropriate memory allocation
   - Code optimization to reduce cold starts
   - Connection pooling for database access
   - Resource caching where appropriate

3. **API Gateway**
   - Request validation to reduce invalid requests
   - Response caching where appropriate
   - Efficient payload size management

## Future Architectural Considerations

1. **Real-time Updates**
   - WebSocket API for real-time game state updates
   - Amazon DynamoDB Streams for event-driven processing

2. **Analytics Pipeline**
   - Amazon Kinesis for data streaming
   - Amazon Athena for data analysis
   - Amazon QuickSight for visualization

3. **Machine Learning Integration**
   - Cheating detection algorithms
   - Game balancing based on player behavior
   - Personalized game recommendations 