# Implementation Details

This document outlines the key classes, interfaces, and implementation patterns used in the Assassin Game application.

## Core Domain Models

### Game
- **Purpose**: Represents a game instance with all its configuration and state
- **Key Fields**: 
  - `id`: Unique game identifier
  - `name`: Display name
  - `createdBy`: Admin user ID
  - `gameStatus`: Enum (CREATED, ACTIVE, ENDED)
  - `gameStartTime`/`gameEndTime`: Time boundaries
  - `boundary`: GeoJSON polygon defining play area
  - `settings`: Map of game configuration options
- **Notable Methods**: 
  - Accessors/mutators for all fields
  - `getShrinkingZoneEnabled()`: Determines if shrinking zone mechanic is active
- **DynamoDB Annotations**: Uses enhanced client annotations for persistence

### Player
- **Purpose**: Represents a participant in a game
- **Key Fields**:
  - `id`: Unique player identifier (UUID)
  - `gameId`: Associated game
  - `playerStatus`: Enum (ALIVE, DEAD, etc.)
  - `userId`: Cognito user ID
  - `name`: Display name
  - `targetId`: Current assigned target
  - `lastKnownLocation`: Geographic coordinates
  - `lastUpdated`: Timestamp of last activity
- **Notable Methods**:
  - Status management methods
  - Location update logic
- **DynamoDB Annotations**: Uses enhanced client annotations for persistence

### Kill
- **Purpose**: Represents a kill event within a game
- **Key Fields**:
  - `id`: Unique kill identifier
  - `gameId`: Associated game
  - `killerId`: Player who made the kill
  - `victimId`: Player who was killed
  - `time`: Timestamp of the kill
  - `location`: Geographic coordinates where kill occurred
  - `verificationStatus`: Enum (PENDING, VERIFIED, REJECTED)
  - `verificationMethod`: Enum (MANUAL, AUTOMATIC, SELF_REPORT)
- **Notable Methods**:
  - Verification status management
  - Time formatting and parsing
- **DynamoDB Annotations**: Uses enhanced client GSI for verification status queries

### SafeZone
- **Purpose**: Represents a designated safe area in a game
- **Key Fields**:
  - `id`: Unique identifier
  - `gameId`: Associated game
  - `name`: Display name
  - `boundary`: GeoJSON representing the zone area
  - `type`: Type of safe zone (PERMANENT, TEMPORARY)
  - `startTime`/`endTime`: For temporary zones
- **DynamoDB Annotations**: Uses enhanced client annotations for persistence

## Data Access Objects (DAOs)

### DynamoDbPlayerDao
- **Purpose**: Handles persistence operations for Player entities
- **Dependencies**: DynamoDbEnhancedClient
- **Key Methods**:
  - `savePlayer(Player)`: Create or update a player
  - `getPlayerById(String)`: Retrieve by ID
  - `getPlayersByGameId(String)`: Get all players in a game
  - `getPlayersByGameIdAndStatus(String, PlayerStatus)`: Filtered query
  - `deletePlayer(String)`: Remove a player

### DynamoDbGameDao
- **Purpose**: Handles persistence operations for Game entities
- **Dependencies**: DynamoDbEnhancedClient
- **Key Methods**:
  - `saveGame(Game)`: Create or update a game
  - `getGameById(String)`: Retrieve by ID
  - `getGamesByStatus(GameStatus)`: Find games by status
  - `getActiveGames()`: Find all active games
  - `deleteGame(String)`: Remove a game

### DynamoDbKillDao
- **Purpose**: Handles persistence operations for Kill entities
- **Dependencies**: DynamoDbEnhancedClient
- **Key Methods**:
  - `saveKill(Kill)`: Create or update a kill record
  - `getKillById(String)`: Retrieve by ID
  - `getKillsByGameId(String)`: Get all kills in a game
  - `findRecentKills(int)`: Get recent verified kills using GSI
  - `getKillsByVictimId(String)`: Get kills where a player was the victim

### DynamoDbSafeZoneDao
- **Purpose**: Handles persistence operations for SafeZone entities
- **Dependencies**: DynamoDbEnhancedClient
- **Key Methods**:
  - `saveSafeZone(SafeZone)`: Create or update a safe zone
  - `getSafeZoneById(String)`: Retrieve by ID
  - `getSafeZonesByGameId(String)`: Get all safe zones in a game
  - `getActiveSafeZones(String, Instant)`: Get currently active safe zones

## Service Layer

### GameService
- **Purpose**: Business logic for game management
- **Dependencies**: DynamoDbGameDao, PlayerService
- **Key Methods**:
  - `createGame(Game)`: Initialize a new game
  - `startGame(String)`: Activate a game and assign targets
  - `endGame(String)`: Finalize a game
  - `updateGameSettings(String, Map)`: Modify game configuration

### PlayerService
- **Purpose**: Business logic for player management
- **Dependencies**: DynamoDbPlayerDao, GameService
- **Key Methods**:
  - `registerPlayer(Player)`: Add a player to a game
  - `updatePlayerLocation(String, double, double)`: Update position
  - `updatePlayerStatus(String, PlayerStatus)`: Change player state
  - `assignTargets(String)`: Generate circular target assignments

### KillService
- **Purpose**: Business logic for kill processing
- **Dependencies**: DynamoDbKillDao, PlayerService, VerificationManager
- **Key Methods**:
  - `reportKill(String, String, double, double)`: Report a new kill
  - `verifyKill(String)`: Mark a kill as verified
  - `rejectKill(String)`: Mark a kill as rejected
  - `getRecentKills(int)`: Get kill feed data

### MapConfigurationService
- **Purpose**: Business logic for geospatial operations
- **Dependencies**: GameService, SafeZoneService
- **Key Methods**:
  - `isLocationInBounds(double, double, String)`: Check game boundary
  - `isLocationInSafeZone(double, double, String)`: Check safe zones
  - `calculateDistance(double, double, double, double)`: Haversine calc
  - `isPlayerNearTarget(String, String)`: Proximity check

### ShrinkingZoneService
- **Purpose**: Manages dynamic boundary reduction over time
- **Dependencies**: GameService, SafeZoneService
- **Key Methods**:
  - `calculateCurrentZone(String)`: Get current active boundary
  - `scheduleNextShrink(String)`: Plan boundary reduction
  - `getShrinkHistory(String)`: Get historical zone changes

### AuthService
- **Purpose**: Manages authentication and authorization
- **Dependencies**: CognitoIdentityProviderClient
- **Key Methods**:
  - `registerUser(String, String)`: Create new user
  - `authenticateUser(String, String)`: Login
  - `validateToken(String)`: Verify JWT token
  - `hasAdminPermission(String)`: Check admin rights

## Lambda Handlers

### CreateGameHandler
- **Purpose**: API endpoint for game creation
- **Dependencies**: GameService, AuthService
- **Input**: APIGatewayProxyRequestEvent with game details
- **Output**: APIGatewayProxyResponseEvent with game ID

### StartGameHandler
- **Purpose**: API endpoint to activate a game
- **Dependencies**: GameService, AuthService
- **Input**: APIGatewayProxyRequestEvent with game ID
- **Output**: APIGatewayProxyResponseEvent with success status

### RegisterPlayerHandler
- **Purpose**: API endpoint for player registration
- **Dependencies**: PlayerService, GameService, AuthService
- **Input**: APIGatewayProxyRequestEvent with player details
- **Output**: APIGatewayProxyResponseEvent with player ID

### ReportKillHandler
- **Purpose**: API endpoint for kill reporting
- **Dependencies**: KillService, AuthService
- **Input**: APIGatewayProxyRequestEvent with kill details
- **Output**: APIGatewayProxyResponseEvent with kill ID

### UpdateLocationHandler
- **Purpose**: API endpoint for location updates
- **Dependencies**: PlayerService, MapConfigurationService
- **Input**: APIGatewayProxyRequestEvent with location details
- **Output**: APIGatewayProxyResponseEvent with status info

## Utility Classes

### DynamoDbClientProvider
- **Purpose**: Singleton provider for DynamoDB clients
- **Pattern**: Thread-safe singleton with double-checked locking
- **Key Methods**:
  - `getDynamoDbClient()`: Get basic client
  - `getDynamoDbEnhancedClient()`: Get enhanced client
- **Features**: 
  - Region configuration
  - Local DynamoDB support for testing
  - Connection pooling for performance

### GeospatialUtils
- **Purpose**: Helper methods for geographic calculations
- **Pattern**: Static utility class
- **Key Methods**:
  - `calculateDistance()`: Haversine formula
  - `isPointInPolygon()`: Point-in-polygon algorithm
  - `parseGeoJson()`: GeoJSON processing

### TimeUtils
- **Purpose**: Helper methods for time operations
- **Pattern**: Static utility class
- **Key Methods**:
  - `formatTimestamp()`: Standard time formatting
  - `parseTimestamp()`: Parse time strings
  - `calculateTimeDifference()`: Duration between times

## Testing Strategy

### Unit Tests
- **Frameworks**: JUnit 5, Mockito
- **Coverage**: Core business logic in services
- **Patterns**: 
  - Mock DAOs and external dependencies
  - Test single units of functionality
  - Parameterized tests for edge cases

### Integration Tests
- **Frameworks**: JUnit 5, DynamoDB Local
- **Coverage**: DAO implementations, service interactions
- **Patterns**:
  - Use local DynamoDB for testing
  - Test full service methods end-to-end
  - Verify correct database operations

### Performance Tests
- **Frameworks**: Custom JUnit extensions
- **Coverage**: Key API operations under load
- **Patterns**:
  - Measure response times
  - Test concurrent operations
  - Verify scalability under load 