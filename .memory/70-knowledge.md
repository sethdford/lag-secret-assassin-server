# Domain Knowledge: Assassin Game

This document captures key domain concepts and game mechanics for the Assassin Game application.

## Core Game Concepts

### Game
- A single instance of an Assassin game
- Has defined geographic boundaries
- Contains a set of players
- Has configurable settings (e.g., verification distance, shrinking zone settings)
- Has a lifecycle (SETUP, ACTIVE, COMPLETED)
- May have multiple administrators

### Player
- A participant in a game
- Has a unique identifier within the game
- Has a real-world location (latitude/longitude)
- Has a status (ALIVE, DEAD, ADMIN)
- May have targets assigned to them
- May have achievements
- Has statistics (kills, time alive, etc.)

### Kill
- Represents the elimination of one player by another
- Has a timestamp
- Has a location
- Has a verification status (PENDING, VERIFIED, REJECTED)
- May have a verification method (AUTOMATIC, MANUAL, SELF_REPORT)

### Safe Zone
- A geographic area where players cannot be eliminated
- Can be static (fixed throughout the game)
- Can be dynamic (shrinking over time)
- Defined by geometric boundaries (usually a polygon)

## Game Mechanics

### Target Assignment
- Players are assigned target(s) to eliminate
- Assignments can be:
  - Chain-based (A targets B, B targets C, etc.)
  - Random assignment
  - Team-based assignment
- Assignments may change when players are eliminated

### Kill Verification
- Process to confirm a kill is legitimate
- Automatic verification checks:
  - Proximity of killer to target
  - Whether location is outside safe zones
  - Whether game is active
- Manual verification allows admins to override for edge cases

### Shrinking Zone Mechanics
- Safe zone reduces in size over time
- Forces players to move closer together
- Implemented via time-based zone updates
- Controlled by configuration parameters:
  - Shrink interval (how often the zone shrinks)
  - Shrink percentage (how much the zone shrinks each time)
  - Minimum zone size (when shrinking stops)

### Location Updates
- Players report their location to the system
- Updates restricted to prevent excessive server load
- Recent locations used for proximity-based game mechanics
- Privacy considerations limit location data retention

## Player Experience Flow

### Registration
1. Player discovers a game (via code, link, etc.)
2. Player creates an account/profile
3. Player joins a specific game
4. Player provides essential information
5. Game admin approves player (if required)

### Gameplay
1. Player receives target assignment
2. Player locates target in the real world
3. Player attempts to eliminate target
4. Kill is verified (automatically or manually)
5. Player receives new target (if applicable)
6. Process continues until game concludes

### End Game
1. Game concludes when:
   - One player remains
   - Time limit is reached
   - Admin manually ends the game
2. Final statistics and achievements are calculated
3. Winners are announced
4. Post-game analysis is available

## Technical Domain Concepts

### Geospatial Calculations
- Distance calculations use the Haversine formula for accuracy
- Geographic boundaries represented as GeoJSON polygons
- Point-in-polygon algorithms determine if players are in safe zones
- Coordinates stored and processed in the WGS84 coordinate system

### Time-Based Events
- Game has defined start and end times
- Shrinking zone updates occur at regular intervals
- Kill times are recorded and used for game mechanics
- Player location timestamps prevent replay attacks

### Security Concepts
- Players can only view limited information about other players
- Location data is protected and minimized
- Game administrators have elevated permissions
- Authentication required for all player actions 