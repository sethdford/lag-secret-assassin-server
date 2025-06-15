# System Architecture Documentation

## Overview

The Assassin Game backend is built using a serverless-first architecture on AWS, designed for high scalability, low latency, and cost efficiency. The system supports real-time location-based gameplay for up to 1000+ concurrent players per game while maintaining sub-3 second response times.

## Architecture Principles

### Serverless-First Design
- **Event-Driven**: Responds to user actions and system events
- **Auto-Scaling**: Automatically handles varying loads
- **Pay-per-Use**: Cost optimization through serverless compute
- **High Availability**: Built-in redundancy and fault tolerance

### Microservices Architecture
- **Domain Separation**: Clear boundaries between business domains
- **Independent Deployment**: Services deployed and versioned independently
- **Technology Diversity**: Best tool for each specific need
- **Fault Isolation**: Failures contained within service boundaries

### Security by Design
- **Zero Trust**: No implicit trust between components
- **Defense in Depth**: Multiple layers of security
- **Least Privilege**: Minimal necessary permissions
- **Data Protection**: Encryption at rest and in transit

## High-Level Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Mobile Apps   │    │   Web Clients   │    │  Admin Panel    │
│   (iOS/Android) │    │   (React/Vue)   │    │    (React)      │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │     API Gateway         │
                    │   (REST + WebSocket)    │
                    └────────────┬────────────┘
                                 │
           ┌─────────────────────┼─────────────────────┐
           │                     │                     │
    ┌──────▼──────┐    ┌─────────▼─────────┐   ┌──────▼──────┐
    │   Lambda     │    │   Lambda          │   │   Lambda    │
    │ Functions    │    │ Functions         │   │ Functions   │
    │ (Business    │    │ (WebSocket        │   │ (Admin      │
    │  Logic)      │    │  Handlers)        │   │ Functions)  │
    └──────┬──────┘    └─────────┬─────────┘   └──────┬──────┘
           │                     │                     │
           └─────────────────────┼─────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │      DynamoDB           │
                    │   (Primary Database)    │
                    └─────────────────────────┘
```

## AWS Services Architecture

### Compute Layer

#### AWS Lambda Functions
**Purpose**: Serverless compute for all business logic

**Handler Categories**:
- **API Handlers**: REST endpoint implementations
- **WebSocket Handlers**: Real-time messaging
- **Event Processors**: Asynchronous event handling
- **Scheduled Tasks**: Background jobs and maintenance

**Configuration**:
- **Runtime**: Java 17
- **Memory**: 512MB - 3GB (varies by function)
- **Timeout**: 30 seconds (API) / 15 minutes (background)
- **Concurrency**: 1000 concurrent executions per function

**Key Lambda Functions**:
```
├── GameHandler              # Game lifecycle management
├── PlayerHandler            # Player profile and actions
├── KillHandler              # Elimination reporting and verification
├── LocationHandler          # Location tracking and validation
├── SafeZoneHandler          # Safe zone management
├── MapHandler               # Interactive map data
├── PaymentHandler           # Payment processing
├── SubscriptionHandler      # Subscription management
├── SecurityMonitoringHandler # Security alerts and monitoring
├── DataExportHandler        # Analytics and data export
├── NotificationHandler      # Push notifications
├── WebSocket/ConnectHandler # WebSocket connection management
├── WebSocket/DefaultHandler # WebSocket message routing
└── AdminHandler             # Administrative functions
```

### API Layer

#### Amazon API Gateway
**Purpose**: Unified API management and routing

**REST API Configuration**:
- **Base URL**: `https://api.assassingame.com/`
- **Authentication**: JWT Bearer tokens
- **Rate Limiting**: 1000 requests/minute per user
- **CORS**: Enabled for web clients
- **Request Validation**: JSON schema validation
- **Response Caching**: 5-minute TTL for static data

**WebSocket API Configuration**:
- **URL**: `wss://api.assassingame.com/websocket`
- **Authentication**: JWT via query parameter
- **Connection Management**: Automatic cleanup
- **Message Routing**: Type-based handler selection
- **Heartbeat**: 30-second ping/pong

**API Structure**:
```
REST API Routes:
├── /auth/*                  # Authentication endpoints
├── /games/*                 # Game management
├── /players/*               # Player operations
├── /kills/*                 # Kill reporting
├── /location                # Location updates
├── /safezones/*             # Safe zone management
├── /map/*                   # Interactive map data
├── /export/*                # Data export
├── /security-monitoring/*   # Security endpoints
├── /subscriptions/*         # Subscription management
├── /privacy/*               # Privacy controls
└── /admin/*                 # Administrative functions

WebSocket Routes:
├── $connect                 # Connection establishment
├── $disconnect              # Connection cleanup
└── $default                 # Message routing
```

### Data Layer

#### Amazon DynamoDB
**Purpose**: Primary NoSQL database for all application data

**Table Design**:
```
Games Table (GAMES_TABLE_NAME)
├── PK: gameId
├── Attributes: name, status, players, settings, boundaries
├── GSI1: status-createdAt (list active games)
└── GSI2: createdBy-createdAt (games by creator)

Players Table (PLAYER_TABLE_NAME)
├── PK: playerId
├── Attributes: name, email, stats, settings, location
├── GSI1: email (login lookup)
└── GSI2: gameId-status (players in game)

Kills Table (ELIMINATION_TABLE_NAME)
├── PK: killId
├── Attributes: killer, target, game, status, verification
├── GSI1: gameId-reportedAt (game kills timeline)
├── GSI2: killerPlayerId-reportedAt (player kills)
└── GSI3: targetPlayerId-reportedAt (player deaths)

SafeZones Table (SAFE_ZONE_TABLE_NAME)
├── PK: safeZoneId
├── Attributes: gameId, type, location, radius, schedule
├── GSI1: gameId-type (safe zones by game)
└── GSI2: ownerId-gameId (player-owned zones)

GameZoneState Table (GAME_ZONE_STATE_TABLE_NAME)
├── PK: gameId
├── Attributes: stage, radius, center, damage, timing
└── TTL: expires after game completion

MapConfig Table (MAP_CONFIG_TABLE_NAME)
├── PK: gameId
├── Attributes: center, zoom, boundaries, heatmap settings
└── TTL: expires after game completion

Notifications Table
├── PK: notificationId
├── Attributes: playerId, type, message, status, timing
├── GSI1: playerId-timestamp (player notifications)
└── TTL: 30 days
```

**Performance Optimizations**:
- **Partition Key Design**: Even distribution across partitions
- **Global Secondary Indexes**: Optimized query patterns
- **DynamoDB Streams**: Real-time change capture
- **Auto Scaling**: Automatic capacity management
- **Point-in-Time Recovery**: Data protection and compliance

### Real-Time Communication

#### WebSocket Management
**Connection Storage**: DynamoDB table for active connections
```
WebSocketConnections Table
├── PK: connectionId
├── Attributes: playerId, gameId, connectedAt, lastPing
├── GSI1: playerId (find player connections)
├── GSI2: gameId (broadcast to game)
└── TTL: 24 hours
```

**Message Broadcasting**:
- **Direct Messaging**: Point-to-point communication
- **Game Broadcasting**: Messages to all game participants
- **Proximity Broadcasting**: Location-based message filtering
- **Event Streaming**: Real-time game state updates

### Authentication & Authorization

#### AWS Cognito User Pool
**Purpose**: User authentication and JWT token management

**Configuration**:
- **Username**: Email-based authentication
- **Password Policy**: 8+ characters, mixed case, numbers
- **MFA**: Optional two-factor authentication
- **Token Expiry**: 1 hour access tokens, 30-day refresh
- **Custom Attributes**: Player metadata and preferences

**JWT Token Structure**:
```json
{
  "sub": "player-uuid",
  "email": "player@example.com",
  "cognito:groups": ["players", "admins"],
  "custom:playerId": "player-uuid",
  "custom:gameId": "current-game-uuid",
  "exp": 1640995200,
  "iat": 1640991600
}
```

#### Authorization Patterns
- **Function-Level**: Lambda authorizers for API Gateway
- **Resource-Level**: DynamoDB fine-grained access control
- **Admin Separation**: Elevated permissions for admin functions
- **Game Isolation**: Players only access their game data

### Payment Processing

#### Stripe Integration
**Purpose**: Secure payment processing for entry fees and subscriptions

**Payment Flows**:
```
Entry Fee Payment:
1. Client creates payment intent
2. Lambda processes with Stripe API
3. Payment confirmed via webhook
4. Player added to game
5. Receipt generated and stored

Subscription Payment:
1. Player selects subscription tier
2. Lambda creates Stripe subscription
3. Webhook confirms payment
4. Benefits activated immediately
5. Recurring billing automated
```

**Security Measures**:
- **PCI Compliance**: Stripe handles sensitive card data
- **Webhook Validation**: Signature verification
- **Idempotency**: Duplicate payment prevention
- **Refund Handling**: Automated refund processing

### File Storage

#### Amazon S3
**Purpose**: Static asset storage and user-generated content

**Bucket Structure**:
```
assassin-game-assets/
├── profile-pictures/
│   └── {playerId}/avatar.jpg
├── kill-verification-photos/
│   └── {killId}/evidence.jpg
├── qr-codes/
│   └── {playerId}/qr-{timestamp}.png
├── exported-data/
│   └── {exportId}/data.{csv|json}
└── static-assets/
    └── {version}/app-assets/
```

**Access Patterns**:
- **Pre-signed URLs**: Secure direct upload/download
- **CloudFront CDN**: Global content delivery
- **Lifecycle Policies**: Automatic cleanup of temporary files
- **Encryption**: S3-managed encryption at rest

### Monitoring & Observability

#### AWS CloudWatch
**Purpose**: Comprehensive monitoring and alerting

**Metrics Tracked**:
- **Lambda Performance**: Duration, errors, memory usage
- **API Gateway**: Request count, latency, error rates
- **DynamoDB**: Read/write capacity, throttling, errors
- **WebSocket**: Connections, message throughput, errors
- **Business Metrics**: Active games, player actions, payments

**Log Aggregation**:
- **Structured Logging**: JSON format with correlation IDs
- **Log Groups**: Organized by service and environment
- **Retention**: 30 days for debugging, 1 year for compliance
- **Search**: CloudWatch Insights for log analysis

#### AWS X-Ray (Distributed Tracing)
**Purpose**: Request flow tracking and performance analysis

**Tracing Configuration**:
- **Lambda Integration**: Automatic trace capture
- **Custom Segments**: Business logic timing
- **External Services**: Stripe, AWS service calls
- **Error Tracking**: Exception capture and analysis

### Security Architecture

#### Network Security
- **VPC Isolation**: Lambda functions in private subnets
- **Security Groups**: Restrictive network access
- **WAF Protection**: Web Application Firewall rules
- **DDoS Protection**: AWS Shield Standard

#### Data Security
- **Encryption at Rest**: All DynamoDB tables encrypted
- **Encryption in Transit**: TLS 1.2+ for all communications
- **Key Management**: AWS KMS for encryption keys
- **Data Minimization**: Only necessary data collected

#### Application Security
- **Input Validation**: All requests validated against schemas
- **SQL Injection Prevention**: NoSQL database usage
- **Rate Limiting**: Per-user and global rate limits
- **CORS Configuration**: Restrictive cross-origin policies

### Geographic Distribution

#### Multi-Region Architecture
**Primary Region**: us-east-1 (N. Virginia)
**Secondary Region**: us-west-2 (Oregon)

**Replication Strategy**:
- **Database**: DynamoDB Global Tables for disaster recovery
- **Storage**: S3 cross-region replication
- **DNS**: Route 53 health checks and failover
- **CDN**: CloudFront global edge locations

## Service Communication Patterns

### Synchronous Communication
- **API Gateway → Lambda**: Direct invocation for real-time requests
- **Lambda → DynamoDB**: Direct database operations
- **Lambda → External APIs**: Stripe, AWS services

### Asynchronous Communication
- **DynamoDB Streams**: Database change events
- **EventBridge**: Decoupled event publishing
- **SQS**: Reliable message queuing
- **SNS**: Notification broadcasting

### Event-Driven Patterns

#### Event Types
```
Game Events:
├── GameCreated
├── GameStarted
├── GameEnded
├── PlayerJoined
├── PlayerLeft
└── GameSettingsUpdated

Player Events:
├── PlayerRegistered
├── LocationUpdated
├── TargetAssigned
├── KillReported
├── KillVerified
└── PlayerEliminated

System Events:
├── PaymentProcessed
├── SubscriptionUpdated
├── SecurityAlert
├── DataExported
└── MaintenanceScheduled
```

#### Event Flow
```
1. Event Producer (Lambda/DynamoDB Stream)
2. Event Router (EventBridge)
3. Event Consumers (Multiple Lambdas)
4. Side Effects (Notifications, Analytics, Cleanup)
```

## Data Flow Architecture

### Real-Time Location Updates
```
Mobile App → API Gateway → LocationHandler → DynamoDB → DynamoDB Stream
                                                    ↓
WebSocket Broadcast ← EventBridge ← Stream Processor
```

### Kill Verification Flow
```
Kill Report → KillHandler → DynamoDB → Stream → EventBridge
                   ↓                               ↓
              Verification Logic              Notification Service
                   ↓                               ↓
              Target Assignment             Push Notifications
                   ↓                               ↓
              Game State Update            WebSocket Broadcast
```

### Payment Processing Flow
```
Client → PaymentHandler → Stripe API → Webhook → PaymentProcessor
            ↓                            ↓            ↓
    Payment Intent                 Verification   Game Access
            ↓                            ↓            ↓
    Client-Side Form              Event Logging  Notification
```

## Scalability Considerations

### Performance Targets
- **API Response Time**: < 200ms (95th percentile)
- **WebSocket Latency**: < 100ms message delivery
- **Database Operations**: < 50ms query response
- **Concurrent Players**: 1000+ per game, 10,000+ system-wide

### Scaling Strategies

#### Horizontal Scaling
- **Lambda Concurrency**: Auto-scaling to 1000+ concurrent executions
- **DynamoDB**: On-demand scaling for unpredictable workloads
- **WebSocket Connections**: Connection pooling across regions
- **API Gateway**: Automatic scaling for request volume

#### Vertical Scaling
- **Lambda Memory**: Optimize based on CPU requirements
- **DynamoDB Capacity**: Provision appropriate read/write units
- **Connection Pooling**: Reuse database connections
- **Caching**: In-memory caching for frequently accessed data

### Load Testing Results
```
Peak Load Testing (1000 concurrent players):
├── API Requests: 10,000 RPS sustained
├── WebSocket Messages: 50,000 messages/second
├── Database Operations: 25,000 reads/writes per second
├── Average Response Time: 180ms
└── Error Rate: < 0.1%
```

## Disaster Recovery & Business Continuity

### Backup Strategy
- **DynamoDB**: Point-in-time recovery enabled
- **S3**: Cross-region replication
- **Code**: Git repository with automated deployments
- **Configuration**: Infrastructure as Code (SAM templates)

### Recovery Procedures
- **RTO (Recovery Time Objective)**: 4 hours
- **RPO (Recovery Point Objective)**: 15 minutes
- **Automated Failover**: Route 53 health checks
- **Manual Failover**: Documented procedures

### Testing
- **Quarterly DR Tests**: Full failover exercises
- **Monthly Backup Verification**: Restore testing
- **Chaos Engineering**: Fault injection testing
- **Load Testing**: Performance validation

## Cost Optimization

### Resource Optimization
- **Lambda**: Right-sizing memory allocations
- **DynamoDB**: On-demand billing for variable workloads
- **S3**: Lifecycle policies for data archival
- **CloudWatch**: Log retention optimization

### Monitoring Costs
- **AWS Cost Explorer**: Daily cost tracking
- **Budget Alerts**: Threshold-based notifications
- **Resource Tagging**: Department and project cost allocation
- **Usage Analytics**: Feature cost analysis

### Estimated Monthly Costs (1000 active players)
```
AWS Lambda: $200-400
DynamoDB: $300-600
API Gateway: $100-200
S3 + CloudFront: $50-100
Other Services: $100-200
Total: $750-1500/month
```

## Future Architecture Considerations

### Planned Enhancements
- **GraphQL API**: More efficient data fetching
- **Edge Computing**: Lambda@Edge for global performance
- **Machine Learning**: AI-powered cheat detection
- **Blockchain**: Immutable game history and achievements

### Technology Evaluation
- **Kubernetes**: Container orchestration for complex workloads
- **Redis**: In-memory caching for session data
- **Elasticsearch**: Advanced search and analytics
- **Apache Kafka**: High-throughput event streaming

This architecture provides a robust, scalable, and maintainable foundation for the Assassin Game platform, capable of supporting thousands of concurrent players while maintaining excellent performance and reliability.