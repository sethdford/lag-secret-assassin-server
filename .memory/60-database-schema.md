# Database Schema

This document outlines the DynamoDB tables, GSIs, attribute definitions, and data access patterns used in the Assassin Game application.

## Table Design Approach

The Assassin Game application uses Amazon DynamoDB as its primary data store. We've adopted a single-table design approach with Global Secondary Indexes (GSIs) to optimize for our access patterns while minimizing the number of tables required.

## Primary Table: AssassinGame

### Table Definition

```
TableName: AssassinGame-{environment}
BillingMode: PAY_PER_REQUEST
```

### Primary Key Structure

- **Partition Key**: `PK` (String)
- **Sort Key**: `SK` (String)

### Key Patterns

| Entity Type | PK Pattern | SK Pattern | Notes |
|-------------|------------|------------|-------|
| Game | `GAME#{gameId}` | `METADATA` | Game metadata |
| Player | `GAME#{gameId}` | `PLAYER#{playerId}` | Player in a game |
| Player Location | `PLAYER#{playerId}` | `LOCATION` | Player's last known location |
| Kill | `GAME#{gameId}` | `KILL#{killId}` | Kill record in a game |
| Safe Zone | `GAME#{gameId}` | `SAFEZONE#{safeZoneId}` | Safe zone in a game |
| Shrinking Zone | `GAME#{gameId}` | `SHRINKINGZONE#{level}` | Shrinking zone level for a game |
| User | `USER#{userId}` | `METADATA` | User account information |
| Game-User Mapping | `USER#{userId}` | `GAME#{gameId}` | Links users to games they've joined |

### Attributes

#### Common Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| `PK` | String | Partition key |
| `SK` | String | Sort key |
| `Type` | String | Entity type (GAME, PLAYER, KILL, etc.) |
| `CreatedAt` | String | ISO8601 timestamp of creation |
| `UpdatedAt` | String | ISO8601 timestamp of last update |

#### Game Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| `GameId` | String | Unique identifier for the game |
| `Name` | String | Game name |
| `CreatedBy` | String | User ID of game creator |
| `GameStatus` | String | Status (CREATED, ACTIVE, ENDED) |
| `GameStartTime` | String | ISO8601 timestamp of game start |
| `GameEndTime` | String | ISO8601 timestamp of game end |
| `Boundary` | Map | GeoJSON polygon of game boundaries |
| `Settings` | Map | Game configuration settings |

#### Player Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| `PlayerId` | String | Unique identifier for the player |
| `GameId` | String | Game the player belongs to |
| `UserId` | String | User account ID |
| `Name` | String | Player display name |
| `PlayerStatus` | String | Status (REGISTERED, ALIVE, DEAD) |
| `TargetId` | String | Current target player ID |
| `LastKnownLocation` | Map | Lat/long coordinates |
| `LastLocationUpdate` | String | ISO8601 timestamp of last location update |

#### Kill Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| `KillId` | String | Unique identifier for the kill |
| `GameId` | String | Game the kill belongs to |
| `KillerId` | String | Player ID of the killer |
| `VictimId` | String | Player ID of the victim |
| `Time` | String | ISO8601 timestamp of kill |
| `Location` | Map | Lat/long coordinates of kill |
| `VerificationStatus` | String | Status (PENDING, VERIFIED, REJECTED) |
| `VerificationMethod` | String | Method (MANUAL, AUTOMATIC, SELF_REPORT) |
| `VerifiedAt` | String | ISO8601 timestamp of verification |
| `KillStatusPartition` | String | GSI partition key based on verification status |

#### SafeZone Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| `SafeZoneId` | String | Unique identifier for the safe zone |
| `GameId` | String | Game the safe zone belongs to |
| `Name` | String | Safe zone name |
| `Type` | String | Type (PERMANENT, TEMPORARY) |
| `Boundary` | Map | GeoJSON polygon of safe zone |
| `StartTime` | String | ISO8601 timestamp of start |
| `EndTime` | String | ISO8601 timestamp of end |
| `IsActive` | Boolean | Whether safe zone is currently active |

#### ShrinkingZone Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| `GameId` | String | Game the shrinking zone belongs to |
| `Level` | Number | Current shrink level |
| `Boundary` | Map | GeoJSON polygon of zone boundaries |
| `ActiveFrom` | String | ISO8601 timestamp of activation |
| `ActiveTo` | String | ISO8601 timestamp of deactivation |
| `PercentageReduction` | Number | Reduction from previous level |

### Global Secondary Indexes (GSIs)

#### 1. UserGamesIndex

Enables retrieving all games a user has participated in.

- **Index Name**: `UserGamesIndex`
- **Partition Key**: `UserId` (String)
- **Sort Key**: `GameId` (String)
- **Projected Attributes**: `ALL`

#### 2. GamePlayersIndex

Enables retrieving all players in a game.

- **Index Name**: `GamePlayersIndex`
- **Partition Key**: `GameId` (String)
- **Sort Key**: `Type` (String)
- **Projected Attributes**: `ALL`

#### 3. PlayerUserIndex

Enables retrieving all players associated with a user.

- **Index Name**: `PlayerUserIndex`
- **Partition Key**: `UserId` (String)
- **Sort Key**: `GameId` (String)
- **Projected Attributes**: `ALL`

#### 4. GameStatusIndex

Enables retrieving games by status.

- **Index Name**: `GameStatusIndex`
- **Partition Key**: `GameStatus` (String)
- **Sort Key**: `GameStartTime` (String)
- **Projected Attributes**: `ALL`

#### 5. StatusTimeIndex

Enables retrieving kills by verification status and time.

- **Index Name**: `StatusTimeIndex`
- **Partition Key**: `KillStatusPartition` (String)
- **Sort Key**: `Time` (String)
- **Projected Attributes**: `ALL`

#### 6. ActiveSafeZonesIndex

Enables retrieving active safe zones for a game.

- **Index Name**: `ActiveSafeZonesIndex`
- **Partition Key**: `GameId` (String)
- **Sort Key**: `IsActive` (Boolean)
- **Projected Attributes**: `ALL`

## Access Patterns

### Game Access Patterns

1. Create a new game
   - Operation: `PutItem`
   - Key: `PK=GAME#{gameId}, SK=METADATA`

2. Get game by ID
   - Operation: `GetItem`
   - Key: `PK=GAME#{gameId}, SK=METADATA`

3. List all games
   - Operation: `Scan`
   - Filter: `Type = GAME`

4. List games by status
   - Operation: `Query` on `GameStatusIndex`
   - Key: `GameStatus=ACTIVE`

5. Update game status
   - Operation: `UpdateItem`
   - Key: `PK=GAME#{gameId}, SK=METADATA`
   - Update: `GameStatus, GameStartTime, GameEndTime`

### Player Access Patterns

1. Register player for a game
   - Operation: `PutItem`
   - Key: `PK=GAME#{gameId}, SK=PLAYER#{playerId}`

2. Get player by ID
   - Operation: `GetItem`
   - Key: `PK=GAME#{gameId}, SK=PLAYER#{playerId}`

3. List all players in a game
   - Operation: `Query` on `GamePlayersIndex`
   - Key: `GameId=<gameId>, begins_with(Type, "PLAYER")`

4. Update player location
   - Operation: `UpdateItem`
   - Key: `PK=PLAYER#{playerId}, SK=LOCATION`
   - Update: `LastKnownLocation, LastLocationUpdate`

5. Get player target
   - Operation: Multi-step:
     1. `GetItem` to retrieve player's targetId
     2. `GetItem` to retrieve target player details

6. Get all players for a user
   - Operation: `Query` on `PlayerUserIndex`
   - Key: `UserId=<userId>`

### Kill Access Patterns

1. Report a kill
   - Operation: `PutItem`
   - Key: `PK=GAME#{gameId}, SK=KILL#{killId}`

2. Verify/reject a kill
   - Operation: `UpdateItem`
   - Key: `PK=GAME#{gameId}, SK=KILL#{killId}`
   - Update: `VerificationStatus, VerificationMethod, VerifiedAt, KillStatusPartition`

3. List all kills in a game
   - Operation: `Query`
   - Key: `PK=GAME#{gameId}, begins_with(SK, "KILL#")`

4. Get recent verified kills (feed)
   - Operation: `Query` on `StatusTimeIndex`
   - Key: `KillStatusPartition=VERIFIED`
   - Sort: `Time` descending

### Safe Zone Access Patterns

1. Create a safe zone
   - Operation: `PutItem`
   - Key: `PK=GAME#{gameId}, SK=SAFEZONE#{safeZoneId}`

2. List all safe zones in a game
   - Operation: `Query`
   - Key: `PK=GAME#{gameId}, begins_with(SK, "SAFEZONE#")`

3. Get active safe zones
   - Operation: `Query` on `ActiveSafeZonesIndex`
   - Key: `GameId=<gameId>, IsActive=true`

### Shrinking Zone Access Patterns

1. Update shrinking zone
   - Operation: `PutItem`
   - Key: `PK=GAME#{gameId}, SK=SHRINKINGZONE#{level}`

2. Get current shrinking zone
   - Operation: `Query`
   - Key: `PK=GAME#{gameId}, begins_with(SK, "SHRINKINGZONE#")`
   - Sort: `SK` descending
   - Limit: 1

3. Get shrinking zone history
   - Operation: `Query`
   - Key: `PK=GAME#{gameId}, begins_with(SK, "SHRINKINGZONE#")`
   - Sort: `SK` ascending

## Transaction Patterns

### Reporting a Kill

1. Begin transaction
2. Update the victim's status to DEAD
3. Add the kill record
4. Assign a new target to the killer
5. End transaction

### Starting a Game

1. Begin transaction
2. Update game status to ACTIVE
3. Assign targets to all players in a circular chain
4. Update all players to ALIVE status
5. End transaction

## Data Migration Strategy

For schema updates or changes, we use the AWS DynamoDB Data Migration process:

1. Deploy a migration Lambda function that reads all affected items
2. Transform the data according to the new schema
3. Write back the updated items
4. Update application code to work with the new schema

This approach ensures zero downtime during schema evolution. 