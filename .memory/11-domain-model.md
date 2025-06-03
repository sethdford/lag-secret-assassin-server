# Domain Model & Business Logic

This document outlines the core domain model, key entities, relationships, and business rules for the Assassin Game application.

## Core Entities

### Player

The `Player` entity represents a user participating in a game of Assassin.

**Key Attributes:**
- `playerId`: Unique identifier for the player, generated during registration
- `name`: Display name of the player
- `email`: Email address for communications and notifications
- `status`: Current player status (ALIVE, DEAD, SPECTATOR)
- `kills`: Number of successful kills
- `lastKnownLocation`: Last recorded GPS coordinates of the player
- `targetId`: Reference to the player's current target
- `gameId`: Reference to the game the player is participating in
- `lastActiveTimestamp`: When the player was last active in the game

**Business Rules:**
- A player can only be assigned to one game at a time
- A player can only have one target at a time
- A player's status changes to DEAD upon being killed
- A player's location is only visible to the game administrator

### Game

The `Game` entity represents a single instance of an Assassin game.

**Key Attributes:**
- `gameId`: Unique identifier for the game
- `name`: Name of the game
- `status`: Current game status (SETUP, ACTIVE, PAUSED, COMPLETED)
- `startTime`: When the game started or will start
- `endTime`: When the game ended or will end
- `ownerId`: Reference to the player who created/owns the game
- `settings`: Configuration options for the game
- `boundary`: Geographic boundary within which the game takes place
- `inviteCode`: Code for players to join the game

**Business Rules:**
- A game can only be started by its owner
- A game must have at least 3 players to start
- Games have a defined geographic boundary
- Game rules and settings are configured during setup and cannot be changed once started
- Games can be paused and resumed by the owner

### Kill

The `Kill` entity represents a successful elimination of one player by another.

**Key Attributes:**
- `killId`: Unique identifier for the kill
- `gameId`: Reference to the game in which the kill occurred
- `killerId`: Reference to the player who made the kill
- `victimId`: Reference to the player who was killed
- `timestamp`: When the kill occurred
- `location`: GPS coordinates where the kill took place
- `verificationStatus`: Status of the kill verification (PENDING, VERIFIED, REJECTED)
- `verificationMethod`: Method used to verify the kill (MANUAL, AUTOMATIC, SELF_REPORT)

**Business Rules:**
- A kill must be verified before it's considered official
- Verification can be manual (admin), automatic (proximity-based), or self-reported
- A player can only kill their assigned target
- Once a kill is verified, the killer is assigned the victim's target
- Kill timestamps and locations are recorded for game history

### SafeZone

The `SafeZone` entity represents a geographic area where players cannot be killed.

**Key Attributes:**
- `zoneId`: Unique identifier for the safe zone
- `gameId`: Reference to the game to which the safe zone belongs
- `name`: Descriptive name for the safe zone
- `center`: GPS coordinates of the center of the safe zone
- `radius`: Radius of the safe zone in meters
- `startTime`: When the safe zone becomes active
- `endTime`: When the safe zone expires

**Business Rules:**
- Safe zones can be time-limited or permanent
- Kill attempts within safe zones are automatically rejected
- Safe zones can be added or removed by game administrators
- Safe zones can overlap with each other

### ShrinkingZone

The `ShrinkingZone` entity represents a dynamic game boundary that shrinks over time.

**Key Attributes:**
- `zoneId`: Unique identifier for the shrinking zone
- `gameId`: Reference to the game to which the zone belongs
- `initialCenter`: Starting GPS coordinates of the center
- `initialRadius`: Starting radius in meters
- `currentCenter`: Current GPS coordinates of the center
- `currentRadius`: Current radius in meters
- `finalRadius`: Final target radius in meters
- `shrinkStartTime`: When the zone begins shrinking
- `shrinkEndTime`: When the zone stops shrinking
- `status`: Current status of the zone (PLANNED, ACTIVE, COMPLETED)

**Business Rules:**
- The shrinking zone gradually reduces in size based on a schedule
- Players outside the shrinking zone are eliminated or penalized
- The center of the zone can shift during shrinking
- Zone updates are broadcast to all players

## Relationships

### Player-Game Relationship

- A player can be associated with one game at a time
- A game can have multiple players
- Players join games using invite codes
- Game owners have administrative privileges

### Player-Target Relationship

- Each living player has exactly one target
- A player can be the target of exactly one other player
- Targets are reassigned when a player makes a kill
- Target assignments form a circular chain

### Kill-Verification Relationship

- Kills must be verified through various methods
- Game admins can manually verify kills
- Proximity-based automatic verification uses GPS
- Self-reported kills may require confirmation by the victim

### Game-SafeZone Relationship

- A game can have multiple safe zones
- Safe zones are specific to a single game
- Safe zone configurations are part of game settings

## Key Workflows

### Game Creation Workflow

1. Game owner creates a new game instance
2. Owner configures game settings (boundaries, rules, etc.)
3. System generates a unique invite code
4. Owner shares invite code with potential players
5. Game remains in SETUP status until started

### Player Onboarding Workflow

1. New user registers with email and creates a profile
2. User joins a game using an invite code
3. User is assigned a target once the game starts
4. User receives notification of game start and target assignment

### Kill Reporting Workflow

1. Player locates and "kills" their target
2. Player reports the kill through the application
3. Kill is verified through configured verification method
4. Upon verification, player receives victim's target
5. Game statistics are updated to reflect the kill

### Game Progression Workflow

1. Game starts with initial target assignments
2. Players eliminate targets and receive new ones
3. The pool of active players gradually decreases
4. Safe zones may activate or deactivate during play
5. The game concludes when only one player remains or time expires

## Business Rules & Constraints

### Target Assignment Rules

- Targets are assigned randomly at game start
- When a player is killed, their target is assigned to their killer
- No player should be assigned themselves as a target
- Target reassignment happens immediately upon kill verification

### Geographic Constraints

- Players must be within the game boundary to participate
- Kill attempts are only valid within the game boundary
- Safe zones provide temporary protection within specific areas
- Shrinking zones force players into smaller areas over time

### Time Constraints

- Games can have fixed durations or continue until one player remains
- Safe zones can be time-limited
- Player inactivity (no location updates) for an extended period may result in elimination
- Time-based events (e.g., "The Purge" - temporary suspension of safe zones) can be scheduled

### Verification Rules

- Each game can configure its preferred verification methods
- Proximity verification requires players to be within a specific distance
- Manual verification by admins may require evidence (photos)
- Self-reported kills may require confirmation by the victim
- Disputed kills can be escalated to game administrators

### Player Status Rules

- Players begin as ALIVE status
- Players change to DEAD status when killed
- Players can optionally become SPECTATORS after death
- Only ALIVE players can make kills
- Only ALIVE players are assigned targets

## Configuration Options

### Game Settings

- **proximityThreshold**: Distance in meters required for kills
- **verificationMethod**: How kills are verified
- **safeZoneSettings**: Default rules for safe zones
- **shrinkingZoneEnabled**: Whether the game uses a shrinking boundary
- **autoAssignTargets**: Whether targets are automatically reassigned
- **timeLimit**: Maximum duration of the game
- **playerInactivityThreshold**: When to penalize inactive players

### Notification Settings

- Event-based notifications (game start, kill alerts, etc.)
- Proximity alerts (target nearby)
- Administrative announcements
- Game status updates
- Safe zone changes

## Domain Events

1. **GameCreated**: A new game has been created
2. **GameStarted**: A game has transitioned to ACTIVE status
3. **PlayerJoined**: A new player has joined a game
4. **TargetAssigned**: A player has been assigned a new target
5. **KillReported**: A kill has been reported but not yet verified
6. **KillVerified**: A kill has been verified and recorded
7. **SafeZoneActivated**: A new safe zone has become active
8. **SafeZoneDeactivated**: A safe zone has expired
9. **ZoneShrinking**: The game boundary is actively shrinking
10. **GameEnded**: A game has completed 