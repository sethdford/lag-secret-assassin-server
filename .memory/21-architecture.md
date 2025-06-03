# Architecture & Component Design

This document outlines the architecture and component design of the Assassin Game application, including the AWS services used, component relationships, and operational characteristics.

## System Architecture Overview

The Assassin Game is built using a serverless architecture on AWS, with the primary components implemented as AWS Lambda functions exposed through Amazon API Gateway. The system follows a microservices-inspired approach, with clear separation of concerns between different functional areas.

```
┌───────────────────┐     ┌───────────────────┐     ┌───────────────────┐
│                   │     │                   │     │                   │
│  Mobile Client    │     │  Web Client       │     │  Admin Client     │
│                   │     │                   │     │                   │
└─────────┬─────────┘     └─────────┬─────────┘     └─────────┬─────────┘
          │                         │                         │
          └─────────────┬───────────┴─────────────┬───────────┘
                        │                         │
                        ▼                         ▼
┌──────────────────────────────────┐   ┌──────────────────────────────────┐
│                                  │   │                                  │
│        Amazon CloudFront         │   │      Amazon Cognito              │
│                                  │   │                                  │
└──────────────┬───────────────────┘   └────────────────┬─────────────────┘
               │                                         │
               ▼                                         │
┌──────────────────────────────────┐                     │
│                                  │                     │
│        Amazon API Gateway        │◄────────────────────┘
│                                  │    (Authorization)
└─────────────┬────────────────────┘
              │
┌─────────────┼─────────────────────────────────────────────────────────────┐
│ ┌───────────▼────────────┐  ┌─────────────────────┐  ┌──────────────────┐ │
│ │                        │  │                     │  │                  │ │
│ │  Game API Lambda       │  │  Player API Lambda  │  │  Kill API Lambda │ │
│ │                        │  │                     │  │                  │ │
│ └────────────┬───────────┘  └─────────┬───────────┘  └─────────┬────────┘ │
│              │                        │                        │          │
│              ▼                        ▼                        ▼          │
│ ┌─────────────────────────────────────────────────────────────────────┐  │
│ │                             Service Layer                            │  │
│ │                                                                      │  │
│ │  ┌────────────────┐  ┌──────────────┐  ┌──────────────────────────┐ │  │
│ │  │ Game Service   │  │ Kill Service │  │ Player/Target Service    │ │  │
│ │  └────────────────┘  └──────────────┘  └──────────────────────────┘ │  │
│ │                                                                      │  │
│ │  ┌────────────────┐  ┌──────────────┐  ┌──────────────────────────┐ │  │
│ │  │ Auth Service   │  │ Map Service  │  │ Verification Service     │ │  │
│ │  └────────────────┘  └──────────────┘  └──────────────────────────┘ │  │
│ └────────────┬────────────────┬────────────────────┬─────────────────┘  │
│              │                │                    │                     │
│              ▼                ▼                    ▼                     │
│ ┌─────────────────────────────────────────────────────────────────────┐  │
│ │                             Data Access Layer                        │  │
│ │                                                                      │  │
│ │  ┌────────────────┐  ┌──────────────┐  ┌──────────────────────────┐ │  │
│ │  │ Game DAO       │  │ Kill DAO     │  │ Player DAO               │ │  │
│ │  └────────────────┘  └──────────────┘  └──────────────────────────┘ │  │
│ │                                                                      │  │
│ │  ┌────────────────┐  ┌──────────────┐  ┌──────────────────────────┐ │  │
│ │  │ SafeZone DAO   │  │ Location DAO │  │ ShrinkingZone DAO        │ │  │
│ │  └────────────────┘  └──────────────┘  └──────────────────────────┘ │  │
│ └─────────────────────────────────────────────────────────────────────┘  │
│                               AWS Lambda Functions                        │
└─────────────────────┬─────────────────────┬─────────────────────────────┘
                      │                     │
┌─────────────────────▼─────┐   ┌───────────▼─────────────┐   ┌───────────────────┐
│                           │   │                         │   │                   │
│     Amazon DynamoDB       │   │    Amazon S3            │   │   Amazon SQS      │
│                           │   │                         │   │                   │
└───────────────────────────┘   └─────────────────────────┘   └───────────────────┘
```

## AWS Services Used

### Core Services

- **AWS Lambda**: Primary compute service for all application logic
- **Amazon API Gateway**: RESTful API interface for clients
- **Amazon DynamoDB**: Primary data store for game state
- **Amazon Cognito**: User authentication and authorization
- **Amazon S3**: Storage for static assets and game media
- **Amazon CloudFront**: Content delivery network for client applications
- **AWS Systems Manager Parameter Store**: Configuration management

### Supporting Services

- **Amazon CloudWatch**: Logging, monitoring, and alerting
- **AWS X-Ray**: Distributed tracing and performance analysis
- **Amazon SQS**: Message queuing for asynchronous processing
- **AWS SAM**: Infrastructure as Code for deployment
- **Amazon EventBridge**: Event-driven architecture components

## Component Architecture

### API Layer (Lambda Functions)

The API layer consists of Lambda functions that handle HTTP requests from API Gateway. Each function corresponds to a specific domain area:

1. **Game API Lambda**
   - Handles game creation, retrieval, and management
   - Endpoints: `/games`, `/games/{gameId}`, etc.

2. **Player API Lambda**
   - Manages player registration, status, and location updates
   - Endpoints: `/players`, `/players/{playerId}`, `/players/{playerId}/location`, etc.

3. **Kill API Lambda**
   - Processes kill reports, verification, and kill feeds
   - Endpoints: `/kills`, `/kills/{killId}`, `/kills/recent`, etc.

4. **SafeZone API Lambda**
   - Manages creation and retrieval of safe zones
   - Endpoints: `/safezones`, `/safezones/{safeZoneId}`, etc.

5. **ShrinkingZone API Lambda**
   - Handles shrinking zone updates and queries
   - Endpoints: `/shrinkingzones`, `/shrinkingzones/current`, etc.

6. **Admin API Lambda**
   - Administrative functions for game management
   - Endpoints: `/admin/games/{gameId}/verify`, `/admin/games/{gameId}/start`, etc.

### Service Layer

The service layer contains business logic and is organized by domain:

1. **GameService**
   - Game creation, retrieval, and lifecycle management
   - Game state transitions (created → active → ended)

2. **PlayerService**
   - Player registration and profile management
   - Target assignment logic

3. **KillService**
   - Kill reporting workflow
   - Chain reaction handling when a player is eliminated

4. **VerificationService**
   - Kill verification algorithms
   - Manual and automatic verification handling

5. **MapConfigurationService**
   - Game boundary and zone management
   - Coordinate validation and containment checks

6. **ShrinkingZoneService**
   - Zone calculation and scheduling
   - Zone transition management

7. **SafeZoneService**
   - Safe zone CRUD operations
   - Safe zone status management

8. **AuthService**
   - User authentication and authorization
   - Token validation and user profile management

### Data Access Layer

The data access layer abstracts database operations:

1. **DynamoDbGameDao**
   - CRUD operations for game entities

2. **DynamoDbPlayerDao**
   - CRUD operations for player entities
   - Target management

3. **DynamoDbKillDao**
   - CRUD operations for kill records
   - Kill feed queries

4. **DynamoDbSafeZoneDao**
   - CRUD operations for safe zone entities

5. **DynamoDbShrinkingZoneDao**
   - CRUD operations for shrinking zone entities

### Utility Components

1. **DynamoDbClientProvider**
   - Singleton pattern for DynamoDB client management
   - Connection pooling and configuration

2. **GeoUtils**
   - Geographic calculations and validations
   - Polygon containment algorithms

3. **DateTimeUtils**
   - Timestamp management and conversions
   - Schedule calculations

4. **JsonUtils**
   - Serialization and deserialization utilities
   - Response formatting

## Request Flow Example

### Kill Reporting Flow

1. Mobile client sends a kill report to `/kills` endpoint
2. API Gateway authenticates the request using Cognito JWT token
3. Kill API Lambda receives the request and validates input
4. KillService processes the kill report:
   - Validates killer and victim are in the same game
   - Checks if killer/victim are active players
   - Verifies the kill location is valid (within game boundaries, not in safe zone)
   - Creates a new Kill record with PENDING status
5. VerificationService begins verification process:
   - For manual verification: Sets status to PENDING
   - For automatic verification: Runs verification algorithm and sets status
6. KillDao stores the kill record in DynamoDB
7. If verified automatically:
   - PlayerService updates victim status to DEAD
   - PlayerService reassigns the victim's target to the killer
8. Lambda returns success response to client

## Asynchronous Processing

For operations that don't require immediate response, we use SQS queues:

1. **Kill Verification Queue**
   - Processes kill verifications asynchronously when manual review is needed

2. **Notification Queue**
   - Handles push notifications to players about game events

3. **Zone Transition Queue**
   - Manages scheduled shrinking zone transitions

## Deployment Model

The application is deployed using AWS SAM (Serverless Application Model):

1. **Development Environment**
   - Isolated DynamoDB tables with dev- prefix
   - Reduced capacity and throughput settings

2. **Testing Environment**
   - Isolated resources with test- prefix
   - Simulated load testing capabilities

3. **Production Environment**
   - Fully scaled resources
   - Multiple AWS regions for resilience (future)

## Security Model

1. **Authentication**
   - Amazon Cognito user pools for identity management
   - JWT tokens for API authorization
   - Role-based access control for admin functions

2. **Authorization**
   - Lambda authorizers validate tokens and permissions
   - Resource-level access control in DynamoDB

3. **Data Protection**
   - Encryption at rest for all DynamoDB tables
   - HTTPS for all API endpoints
   - S3 bucket policies for secure asset access

## Monitoring and Observability

1. **Logging**
   - Structured JSON logs to CloudWatch Logs
   - Log correlation using request IDs

2. **Metrics**
   - Custom CloudWatch metrics for business KPIs
   - Lambda execution metrics (duration, errors, throttles)
   - DynamoDB throughput and latency metrics

3. **Tracing**
   - X-Ray tracing for request flow visualization
   - Service map for dependency visualization

4. **Alerting**
   - CloudWatch Alarms for critical metrics
   - Error rate thresholds for notification

## Scalability Considerations

1. **Lambda Concurrency**
   - Reserved concurrency for critical functions
   - Burst capacity management

2. **DynamoDB Capacity**
   - On-demand capacity for unpredictable workloads
   - GSI optimization for query patterns

3. **Read/Write Distribution**
   - Heavy read operations optimized with query patterns
   - Write operations distributed across partition keys

## Resilience Strategy

1. **Error Handling**
   - Comprehensive exception handling in all Lambda functions
   - Fallback mechanisms for dependency failures

2. **Retry Policies**
   - Exponential backoff for transient failures
   - Dead letter queues for unprocessable messages

3. **Circuit Breakers**
   - Fail fast pattern for dependency outages
   - Graceful degradation for non-critical features

## Future Architecture Enhancements

1. **Real-time Updates**
   - WebSocket API for live game status
   - Amazon API Gateway WebSocket support

2. **Spatial Data Indexing**
   - Amazon DynamoDB with spatial indexing extensions
   - Performance optimization for location-based queries

3. **Advanced Analytics**
   - Game telemetry processing with Amazon Kinesis
   - Machine learning for kill verification improvement

4. **Multi-region Deployment**
   - Global tables for data replication
   - Regional API endpoints for lower latency 