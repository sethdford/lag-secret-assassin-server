# System Architecture

This document outlines the architectural design of the Assassin Game application, focusing on the AWS SAM implementation.

## High-Level Architecture

The Assassin Game is implemented as a serverless application using AWS Lambda with AWS SAM (Serverless Application Model). The application follows a hexagonal architecture pattern with well-defined domains and separation of concerns.

```
┌───────────────┐         ┌───────────────┐         ┌───────────────┐
│   API Layer   │         │ Business Layer│         │   Data Layer  │
│  (Controllers)│ ───────►│   (Services)  │ ───────►│     (DAOs)    │
└───────────────┘         └───────────────┘         └───────────────┘
        ▲                        ▲                         ▲
        │                        │                         │
        ▼                        ▼                         ▼
┌───────────────┐         ┌───────────────┐         ┌───────────────┐
│  API Gateway  │         │ Domain Models │         │   DynamoDB    │
│               │         │               │         │               │
└───────────────┘         └───────────────┘         └───────────────┘
```

## AWS Services Used

- **AWS Lambda**: Hosts the serverless functions that implement the game logic
- **Amazon API Gateway**: Provides the REST API interface to clients
- **Amazon DynamoDB**: Primary data store for game state, player data, etc.
- **Amazon Cognito**: Handles authentication and authorization
- **Amazon CloudWatch**: Monitoring, logging, and metrics
- **AWS X-Ray**: Distributed tracing for performance monitoring
- **Amazon S3**: Static asset storage (if applicable)
- **AWS SAM**: Infrastructure as code for deployment and management

## Key Components

### Lambda Handlers (API Layer)

Lambda handlers are organized by resource type and provide the entry points for API requests:

- `GameHandler`: Manages game lifecycle operations
- `PlayerHandler`: Handles player registration and updates
- `KillHandler`: Processes kill reports and verifications
- `LocationHandler`: Manages player location updates
- `AdminHandler`: Provides game administration capabilities
- `ConnectHandler`: Manages websocket connections (if applicable)

### Services (Business Layer)

Services implement the core business logic of the application:

- `GameService`: Game management, configuration, and lifecycle
- `PlayerService`: Player registration, profile management, and targeting
- `KillService`: Kill reporting, verification, and processing
- `LocationService`: Location updates, history, and geospatial calculations
- `AuthService`: Authentication and authorization logic
- `VerificationService`: Implements verification strategies for kills
- `MapConfigurationService`: Manages game boundaries and safe zones
- `ShrinkingZoneService`: Implements the shrinking zone game mechanic

### Data Access Objects (Data Layer)

DAOs provide an abstraction over DynamoDB tables:

- `DynamoDbGameDao`: CRUD operations for Game entities
- `DynamoDbPlayerDao`: CRUD operations for Player entities
- `DynamoDbKillDao`: CRUD operations for Kill entities
- `DynamoDbSafeZoneDao`: CRUD operations for SafeZone entities
- `DynamoDbLocationDao`: CRUD operations for Location entities

### Domain Models

Core domain models represent the primary entities in the system:

- `Game`: Represents a game instance with configuration settings
- `Player`: Represents a player within a game
- `Kill`: Represents a kill event with verification status
- `SafeZone`: Represents a geographic area where kills are prohibited
- `Location`: Represents a player's location at a point in time

## Data Persistence Design

### DynamoDB Single-Table Design

The application uses a single-table design pattern for DynamoDB with the following key structure:

- **Partition Key**: `PK` (composite key with entity type prefix)
- **Sort Key**: `SK` (composite key with additional identifiers)
- **Global Secondary Indexes (GSIs)**:
  - `GSI1`: For querying players by game
  - `GSI2`: For querying kills by status and timestamp
  - `GSI3`: For querying locations by player and timestamp

### Data Access Patterns

- **Get Game by ID**: `PK = "GAME#<gameId>", SK = "METADATA"`
- **Get Player by ID**: `PK = "PLAYER#<playerId>", SK = "METADATA"`
- **Get Players by Game**: Use GSI1 with `PK_GSI1 = "GAME#<gameId>", SK_GSI1 begins_with "PLAYER#"`
- **Get Recent Kills**: Use GSI2 with `PK_GSI2 = "KILL#VERIFIED", SK_GSI2 = "<timestamp>" (sorted descending)`
- **Get Player Locations**: Use GSI3 with `PK_GSI3 = "PLAYER#<playerId>", SK_GSI3 begins_with "LOCATION#"`

## Authentication & Authorization

The system uses Amazon Cognito for authentication with three levels of authorization:

1. **Unauthenticated**: Limited access for joining games
2. **Player**: Standard access for participating in games
3. **Admin**: Extended access for game administration

JWT tokens from Cognito are validated and processed by API Gateway authorizers and custom validation logic in the Lambda handlers.

## API Design

The application provides a RESTful API with the following groups:

### Game Management
- `POST /games`: Create a new game
- `GET /games/{gameId}`: Get game details
- `PUT /games/{gameId}`: Update game settings
- `DELETE /games/{gameId}`: Delete a game

### Player Management
- `POST /games/{gameId}/players`: Register a player
- `GET /games/{gameId}/players`: List players in a game
- `GET /games/{gameId}/players/{playerId}`: Get player details
- `PUT /games/{gameId}/players/{playerId}`: Update player status

### Kill Processing
- `POST /games/{gameId}/kills`: Report a kill
- `GET /games/{gameId}/kills`: List kills in a game
- `PUT /games/{gameId}/kills/{killId}/verify`: Verify a kill

### Location Management
- `POST /games/{gameId}/players/{playerId}/location`: Update player location
- `GET /games/{gameId}/players/{playerId}/location`: Get player's current location

## Deployment Architecture

The application is deployed using AWS SAM with the following components:

- **SAM Template**: Defines all AWS resources and their configurations
- **CloudFormation Stack**: Created by SAM for resource management
- **CI/CD Pipeline**: Automated deployment process (if implemented)
- **Environment Configuration**: Development, testing, production environments

## Monitoring & Observability

- **Structured Logging**: JSON-formatted logs with correlation IDs
- **CloudWatch Metrics**: Custom metrics for key business operations
- **X-Ray Tracing**: End-to-end tracing for performance analysis
- **CloudWatch Alarms**: Automated alerts for critical issues
- **CloudWatch Dashboards**: Visual monitoring of application health 