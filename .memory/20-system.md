# Assassin Game API System Architecture

## Overview
The Assassin Game API is a serverless application built on AWS services, utilizing AWS SAM for infrastructure as code. The architecture follows cloud-native design principles to ensure scalability, reliability, and cost-effectiveness.

## Key Components

### Amazon API Gateway
- Serves as the entry point for all API requests
- Manages API versioning and stages (dev, test, prod)
- Handles request validation and authorization
- Implements rate limiting and throttling
- Provides WebSocket support for real-time updates

### AWS Lambda Functions
- **AuthHandler**: Manages user authentication and token validation
- **GameHandler**: Handles game creation, configuration, and lifecycle
- **PlayerHandler**: Manages player registration, profiles, and status
- **LocationHandler**: Processes location updates and proximity calculations
- **KillHandler**: Manages kill reporting and verification
- **SafeZoneHandler**: Handles safe zone creation and validation
- **NotificationHandler**: Manages push notifications and alerts
- **AdminHandler**: Provides administrative functions for game management

### Amazon DynamoDB
- **UsersTable**: Stores user authentication data
- **GamesTable**: Stores game configuration and state
- **PlayersTable**: Stores player data and game associations
- **LocationsTable**: Tracks player location history with TTL
- **KillsTable**: Records kill reports and verification status
- **SafeZonesTable**: Stores safe zone definitions and metadata
- Uses GSIs (Global Secondary Indexes) for efficient querying
- On-demand capacity mode for cost optimization

### Amazon Cognito
- User authentication and identity management
- Token-based authentication with JWT
- User pools for managing user accounts
- Identity pools for granting access to AWS resources
- Integration with social identity providers

### Amazon SNS
- Push notification delivery to mobile devices
- Topic-based notification for game events
- Event-driven architecture for asynchronous processing
- Real-time alerts for critical game events

## Data Flow

### Player Registration
1. User registers through client app
2. Request routed through API Gateway to Lambda function
3. Cognito creates new user entry
4. Lambda creates player profile in DynamoDB
5. Confirmation message sent via SNS

### Game Creation
1. Admin creates game through API
2. Request validated and routed to GameHandler Lambda
3. Game details stored in DynamoDB
4. Game boundaries and settings validated
5. Notification sent to subscribed players

### Location Updates
1. Player app sends location updates at configurable intervals
2. LocationHandler Lambda receives updates via API Gateway
3. Updates stored in DynamoDB with TTL for privacy
4. Proximity calculations performed for nearby players
5. Game state updated based on proximity rules

### Kill Reporting
1. Player reports kill through app
2. Request routed to KillHandler Lambda
3. Kill event validated based on game rules and proximity
4. If valid, player statuses updated in DynamoDB
5. Notifications sent to affected players
6. Game state updated accordingly

## System Properties

### Scalability
- Serverless architecture automatically scales with user load
- DynamoDB auto-scaling for read/write capacity
- API Gateway and Lambda scale to handle thousands of concurrent requests
- Event-driven design for asynchronous processing

### Reliability
- Multi-AZ deployment for high availability
- Dead letter queues for failed Lambda executions
- Retry mechanisms for transient errors
- Monitoring and alerting for system issues

### Security
- Cognito authentication and authorization
- API Gateway request validation
- IAM roles with least privilege principle
- DynamoDB encryption at rest
- HTTPS for all communications
- Input validation on all API endpoints

### Performance
- DynamoDB DAX for caching frequent queries
- Lambda function optimization (memory, timeout settings)
- Efficient database queries with appropriate indexes
- Batched writes for high-throughput operations

### Cost Optimization
- Pay-per-use model with serverless components
- DynamoDB TTL for automatic data expiration
- Lambda concurrency limits to prevent runaway costs
- CloudWatch Alarms for cost monitoring

## Integration Points

### Client Applications
- REST APIs for standard operations
- WebSocket connections for real-time updates
- Push notifications via SNS
- Authentication via Cognito SDK

### Admin Dashboard
- Dedicated APIs for administrative functions
- Real-time monitoring of game state
- Player management capabilities
- Game configuration tools

### Analytics and Reporting
- CloudWatch Metrics for system monitoring
- Custom metrics for game statistics
- Event logging for audit purposes
- Reporting APIs for game outcomes

## Deployment Strategy
- AWS SAM templates for infrastructure as code
- CI/CD pipeline for automated deployments
- Staged rollouts (dev, test, prod)
- Blue/green deployment for zero-downtime updates 