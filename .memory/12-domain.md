# Assassin Game Domain Terminology

## Core Game Concepts

### Player
A registered user participating in an Assassin game. Each player is both a hunter (seeking their assigned target) and a potential victim (being hunted by another player).

**Attributes:**
- Player ID: Unique identifier
- Name: Display name
- Game ID: The game they are participating in
- Status: Current player status (ACTIVE, ELIMINATED, WINNER)
- Target ID: The ID of their assigned target
- Kill Count: Number of successful eliminations
- Location: Last reported geographical coordinates

### Game
A single instance of an Assassin game with defined boundaries, rules, and participants.

**Attributes:**
- Game ID: Unique identifier
- Name: Display name
- Admin Player ID: Player who created and manages the game
- Status: Current game status (PENDING, ACTIVE, COMPLETED)
- Start Time: When the game began or will begin
- End Time: When the game ended or will end (optional)
- Settings: Configuration options and rules
- Boundary: Geographical area where the game takes place

### Kill
Represents an elimination event where one player (the killer) successfully eliminates their target (the victim).

**Attributes:**
- Kill ID: Unique identifier
- Game ID: The game this kill occurred in
- Killer ID: Player who performed the elimination
- Victim ID: Player who was eliminated
- Time: When the elimination occurred
- Location: Geographical coordinates where the elimination occurred
- Verification Status: Whether the kill has been verified (PENDING, VERIFIED, REJECTED)
- Verification Method: How the kill was verified (MANUAL, AUTOMATIC, SELF_REPORT)

### Safe Zone
A designated area within the game boundary where players are immune from elimination.

**Attributes:**
- Safe Zone ID: Unique identifier
- Game ID: The game this safe zone belongs to
- Name: Display name
- Type: Kind of safe zone (PERMANENT, TEMPORARY, SHRINKING)
- Center: Geographical center point
- Radius: Size of the safe zone in meters
- Start Time: When the safe zone becomes active
- End Time: When the safe zone becomes inactive
- Status: Current status (ACTIVE, INACTIVE)

## Game Mechanics

### Target Assignment
The process of assigning each player a target to eliminate. Typically forms a closed loop where each player is hunting exactly one other player and is hunted by exactly one player.

### Elimination
The act of a player successfully "assassinating" their target according to the game rules. This typically involves being within a certain proximity of the target and following game-specific interaction rules.

### Verification
The process of confirming that an elimination was legitimate according to game rules. May be:
- Manual: Game admin reviews and approves
- Automatic: System verifies based on location data
- Self-Report: Target confirms their own elimination

### Shrinking Zone
A dynamic game mechanic where the playable area decreases over time, forcing players into closer proximity. Similar to the "circle" or "storm" in battle royale games.

### Circle of Death
The chain of targets that forms a complete loop. When a player eliminates their target, they typically receive the target's target as their new target, maintaining the circle.

## Player Statuses

### Active
Player is currently participating in the game and has not been eliminated.

### Eliminated
Player has been successfully targeted by their hunter and is no longer actively hunting, though they may still participate in other game aspects.

### Winner
The last player remaining after all others have been eliminated.

## Game Statuses

### Pending
Game has been created but has not yet started. Players can join during this phase.

### Active
Game is currently in progress. Target assignments have been made and eliminations can occur.

### Completed
Game has ended, either because all but one player has been eliminated or because the scheduled end time has passed.

## Technical Concepts

### Location Update
The process of a player reporting their current geographical position to the system, used for verification and game mechanics.

### Proximity Detection
The system's ability to determine when players are within a certain distance of each other, used for verification and safe zone mechanics.

### Geofencing
The use of virtual boundaries (the game boundary and safe zones) to trigger game events when players enter or exit these areas.

### Notification
System-generated messages sent to players about game events, such as game start, new target assignments, nearby players, or elimination attempts.

## Game Administration

### Admin Player
The player responsible for creating and managing a game, with special privileges such as:
- Approving/rejecting elimination reports
- Creating and modifying safe zones
- Adjusting game boundaries
- Starting and ending the game
- Manually modifying player statuses or target assignments

### Game Creation
The process of setting up a new game instance, defining its boundaries, rules, and inviting players to participate.

### Game Joining
The process for players to enroll in an existing game, either through direct invitation or using a join code/link.

### Game Rules Configuration
Settings that define how the game operates, including:
- Verification method requirements
- Safe zone behaviors
- Target assignment algorithms
- Game duration settings
- Shrinking zone configuration 