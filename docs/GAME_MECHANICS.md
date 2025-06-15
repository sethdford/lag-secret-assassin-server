# Game Mechanics Documentation

## Overview

The Assassin Game is a location-based elimination game that combines real-world movement with strategic gameplay. Players hunt assigned targets while avoiding their own pursuers in a thrilling game of survival that can span hours, days, or weeks.

## Core Game Concepts

### Game Lifecycle

1. **Game Creation**: Admin creates a game with specific parameters
2. **Player Registration**: Players join the game during registration period
3. **Game Start**: Targets are assigned and the hunt begins
4. **Active Gameplay**: Players hunt targets while avoiding elimination
5. **Game End**: Game concludes when one player remains or time expires

### Player States

- **Alive**: Actively hunting with an assigned target
- **Pending Death**: Kill reported but not yet verified
- **Eliminated**: Confirmed elimination, out of the game
- **Winner**: Last player standing or highest score at game end

## Target Assignment System

### Circular Assignment Chain

The game uses a circular target assignment system where:

- Each player is assigned exactly one target to hunt
- Each player is also being hunted by exactly one other player
- The assignments form a closed loop (A hunts B, B hunts C, C hunts A)
- This ensures balanced gameplay and prevents targeting imbalances

### Assignment Algorithm

```
1. Shuffle player list randomly
2. Create circular chain: P1 → P2 → P3 → ... → Pn → P1
3. Each player receives their target assignment
4. Assignments remain secret to maintain suspense
```

### Target Reassignment

When a player is eliminated:

1. **Immediate**: Killer inherits the eliminated player's target
2. **Chain Repair**: The targeting chain is automatically repaired
3. **Notification**: New target assignment sent to the killer
4. **Update**: Game state updated for all participants

**Example**:
- Original: A → B → C → D → A
- B eliminates C: A → B → D → A
- B now hunts D instead of C

## Elimination Mechanics

### Kill Reporting

Players can report eliminations using multiple verification methods:

#### 1. GPS Proximity Verification
- **Range**: 10-50 meters (configurable)
- **Accuracy**: Requires GPS accuracy < 20 meters
- **Validation**: Both players must be within range
- **Auto-verify**: Kills within proximity range auto-verify after 5 minutes

#### 2. QR Code Verification
- **Generation**: Each player has a unique QR code
- **Scanning**: Killer scans target's QR code
- **Instant**: Immediate verification upon successful scan
- **Security**: QR codes rotate every 24 hours

#### 3. Photo Verification
- **Capture**: Killer takes photo of target
- **Review**: Manual admin review required
- **Timeline**: 24-hour verification window
- **Appeals**: Target can dispute photo evidence

#### 4. NFC Verification
- **Tap**: Physical NFC tag interaction
- **Instant**: Immediate verification
- **Range**: < 5cm proximity required
- **Security**: Encrypted NFC tokens

#### 5. Admin Override
- **Manual**: Admin can verify/reject any kill
- **Appeals**: Used for dispute resolution
- **Emergency**: For technical issues or special circumstances

### Verification Flow

```
1. Kill Reported
   ├── Verification Method Applied
   ├── Status: PENDING (5-60 minutes depending on method)
   └── Awaiting Verification

2. Verification Process
   ├── Auto-Verify (GPS/QR/NFC)
   ├── Manual Review (Photo)
   └── Admin Decision (Appeals)

3. Verification Result
   ├── VERIFIED → Target Eliminated, New Target Assigned
   ├── REJECTED → Kill Invalid, No Changes
   └── PENDING_DEATH → Extended Review Period
```

### Delayed Target Reassignment

For enhanced gameplay integrity:

1. **Grace Period**: 30-minute window for target verification
2. **Status Change**: Target marked as "PENDING_DEATH"
3. **Limited Actions**: Pending players cannot make kills
4. **Resolution**: Verification or rejection determines final state
5. **Appeals Process**: 24-hour window for disputes

## Safe Zone System

### Safe Zone Types

#### 1. Public Safe Zones
- **Access**: Available to all players
- **Locations**: Libraries, dining halls, dormitories
- **Duration**: Permanent or scheduled hours
- **Protection**: No eliminations allowed within zone

#### 2. Private Safe Zones
- **Access**: Player-created personal safe zones
- **Limit**: 1 per player per game
- **Duration**: 2-hour activation period
- **Cooldown**: 24-hour reuse cooldown

#### 3. Timed Safe Zones
- **Schedule**: Activate at specific times
- **Examples**: Dining hours, class periods, sleep hours
- **Automatic**: System-controlled activation/deactivation
- **Notification**: 10-minute advance warning

#### 4. Relocatable Safe Zones
- **Mobility**: Can be moved during the game
- **Uses**: Limited number of relocations (typically 3)
- **Strategy**: Tactical positioning for endgame
- **Cooldown**: 6-hour delay between moves

### Safe Zone Rules

1. **Entry Protection**: Players are safe once inside the zone
2. **Exit Vulnerability**: No protection upon leaving safe zone
3. **No Camping**: Maximum safe zone occupancy time limits
4. **Boundary Enforcement**: GPS monitoring with 10-meter buffer
5. **Fair Play**: Cannot be used to indefinitely avoid elimination

## Shrinking Zone System

### Battle Royale Mechanics

The shrinking zone adds urgency and forces player movement:

#### Zone Progression Stages

1. **Stage 1**: Full game area available (60 minutes)
2. **Stage 2**: Zone shrinks to 75% of original size (45 minutes)
3. **Stage 3**: Zone shrinks to 50% of original size (30 minutes)
4. **Stage 4**: Zone shrinks to 25% of original size (15 minutes)
5. **Final Stage**: Small central area (10 minutes)

#### Zone Damage System

- **Warning Period**: 5-minute countdown before shrinking
- **Damage**: Players outside zone take damage over time
- **Escalation**: Damage increases with each stage
- **Elimination**: Players can be eliminated by zone damage
- **Healing**: Safe zones provide protection and healing

#### Zone Configuration

```json
{
  "stages": [
    {
      "stageIndex": 1,
      "waitTimeSeconds": 3600,
      "shrinkTimeSeconds": 300,
      "radiusMultiplier": 0.75,
      "damagePerSecond": 5
    },
    {
      "stageIndex": 2,
      "waitTimeSeconds": 2700,
      "shrinkTimeSeconds": 300,
      "radiusMultiplier": 0.67,
      "damagePerSecond": 10
    }
  ]
}
```

## Location Tracking and Privacy

### Location Requirements

- **GPS Accuracy**: < 20 meters for verification
- **Update Frequency**: Every 30 seconds during active gameplay
- **Battery Optimization**: Adaptive frequency based on movement
- **Offline Mode**: Cache updates when connection unavailable

### Privacy Controls

#### Location Sharing Settings

1. **Public**: Location visible to all game participants
2. **Friends**: Location visible only to friends list
3. **Private**: Location hidden from other players
4. **Proximity Only**: Location shared only when near other players

#### Data Protection

- **Encryption**: All location data encrypted in transit and at rest
- **Retention**: Location history deleted after game completion
- **Anonymization**: Historical data anonymized for analytics
- **User Control**: Players can delete location history at any time

### Anti-Cheat Location Validation

- **Speed Checks**: Impossible movement speeds flagged
- **Pattern Analysis**: Teleportation and spoofing detection
- **Device Verification**: Multiple device usage monitoring
- **Crowd Validation**: Cross-reference with other player locations

## Scoring and Statistics

### Individual Player Metrics

#### Kill Statistics
- **Total Kills**: Number of successful eliminations
- **Kill/Death Ratio**: Performance efficiency metric
- **Verification Success Rate**: Percentage of verified kills
- **Average Kill Time**: Time to eliminate each target

#### Survival Metrics
- **Survival Time**: Total time alive in each game
- **Distance Traveled**: Total movement during gameplay
- **Safe Zone Usage**: Time spent in protected areas
- **Close Calls**: Number of near-elimination events

#### Strategic Metrics
- **Target Tracking Efficiency**: Time to locate assigned targets
- **Evasion Score**: Success at avoiding elimination attempts
- **Zone Positioning**: Effectiveness at staying within shrinking zones
- **Resource Management**: Safe zone and power-up usage efficiency

### Game-Level Statistics

#### Population Dynamics
- **Elimination Rate**: Players eliminated per hour
- **Average Game Duration**: Typical game completion time
- **Peak Activity Hours**: Most active gameplay periods
- **Geographic Spread**: Player distribution across game area

#### Engagement Metrics
- **Active Player Percentage**: Players currently hunting vs. hiding
- **Location Update Frequency**: Real-time engagement level
- **Feature Usage**: Safe zones, verification methods, social features
- **Retention Rate**: Players completing vs. abandoning games

## Game Modes and Variations

### Standard Mode
- **Duration**: 1-7 days
- **Players**: 10-1000 participants
- **Verification**: All methods available
- **Safe Zones**: Full safe zone system active

### Blitz Mode
- **Duration**: 2-6 hours
- **Players**: 20-100 participants
- **Pace**: Accelerated elimination timeline
- **Features**: Limited safe zones, faster shrinking zone

### Campus Mode
- **Duration**: 1 semester (ongoing)
- **Players**: University-wide participation
- **Integration**: Class schedules, dining hours, events
- **Seasons**: Multiple games throughout academic year

### Tournament Mode
- **Structure**: Bracket-style elimination
- **Duration**: Multiple rounds over weeks
- **Advancement**: Winners progress to next round
- **Prizes**: Competitive rewards and recognition

## Power-Ups and Special Items

### Temporary Abilities

#### Ghost Mode
- **Effect**: Hidden from other players for 30 minutes
- **Limitation**: Cannot make kills while active
- **Acquisition**: Purchase with in-game currency
- **Cooldown**: 6-hour reuse delay

#### Radar Pulse
- **Effect**: Shows all players within 500-meter radius
- **Duration**: 10-second pulse
- **Usage**: One-time consumable item
- **Strategy**: Valuable for hunting or evasion

#### Shield
- **Effect**: Immunity to one elimination attempt
- **Duration**: 24 hours or until used
- **Visibility**: Other players can see shield status
- **Limitation**: Cannot be used in final 10% of players

#### Speed Boost
- **Effect**: Increased movement detection range
- **Duration**: 1 hour
- **Benefit**: Better proximity verification radius
- **Stacking**: Cannot combine with other movement items

### Strategic Items

#### Decoy Beacon
- **Effect**: Creates false location signal
- **Duration**: 2 hours
- **Deception**: Appears as player movement to others
- **Detection**: Sophisticated players may identify decoys

#### Zone Intel
- **Effect**: Preview of next shrinking zone location
- **Advantage**: 30-minute advance notice
- **Strategic Value**: Superior positioning for zone changes
- **Rarity**: Limited availability per game

## Social Features and Community

### Friend System
- **Connection**: Add friends within the game
- **Benefits**: Enhanced location sharing with friends
- **Communication**: Secure in-game messaging
- **Alliances**: Temporary partnerships (where allowed)

### Clans and Teams
- **Formation**: Create persistent groups across games
- **Benefits**: Shared statistics and achievements
- **Competition**: Clan vs. clan tournaments
- **Loyalty**: Exclusive clan-based rewards

### Communication Tools
- **Proximity Chat**: Voice/text within 100 meters
- **Encrypted Messaging**: Secure player-to-player communication
- **Public Channels**: Game-wide announcements and banter
- **Emergency Broadcast**: System-wide urgent communications

## Advanced Gameplay Elements

### Weather Integration
- **Real Weather**: Game difficulty adjusts to actual weather
- **Visibility**: Fog/rain affects detection ranges
- **Movement**: Snow/ice impacts travel speed calculations
- **Shelter**: Weather-based safe zone bonuses

### Time-Based Mechanics
- **Day/Night Cycles**: Different rules for different times
- **Schedule Integration**: Academic/work schedule awareness
- **Peak Hours**: Enhanced rewards during busy periods
- **Circadian Rhythm**: Gameplay adapts to player sleep patterns

### Event System
- **Random Events**: Surprise game modifiers
- **Scheduled Events**: Planned special circumstances
- **Community Events**: Player-driven activities
- **Seasonal Themes**: Holiday and special occasion modes

## Accessibility and Inclusivity

### Physical Accessibility
- **Mobility Assistance**: Modified rules for limited mobility
- **Alternative Verification**: Non-physical verification methods
- **Assistive Technology**: Screen reader and voice control support
- **Flexible Participation**: Partial participation options

### Cognitive Accessibility
- **Simplified Interfaces**: Reduced complexity options
- **Extended Timers**: Longer decision and reaction times
- **Clear Instructions**: Enhanced tutorial and help systems
- **Notification Options**: Customizable alert preferences

### Economic Accessibility
- **Free Participation**: Core game always free
- **Optional Purchases**: Cosmetic and convenience items only
- **Scholarship Programs**: Free premium features for students
- **Community Support**: Player-funded participation assistance

## Safety and Moderation

### Physical Safety
- **Emergency Protocols**: Instant contact with emergency services
- **Check-in System**: Regular safety confirmations
- **Dangerous Area Exclusion**: Automatic hazardous location filtering
- **Escort Services**: Coordination with campus safety

### Digital Safety
- **Privacy Protection**: Comprehensive data protection measures
- **Harassment Prevention**: Automated detection and reporting
- **Content Moderation**: Review of user-generated content
- **Appeal Process**: Fair dispute resolution procedures

### Behavioral Guidelines
- **Code of Conduct**: Clear expectations for player behavior
- **Reporting System**: Easy incident reporting mechanisms
- **Graduated Responses**: Warnings, suspensions, and bans
- **Rehabilitation**: Programs for returning suspended players

## Future Gameplay Innovations

### Augmented Reality
- **AR Overlays**: Enhanced visual target identification
- **Environmental Interaction**: Virtual objects in real spaces
- **Immersive Notifications**: 3D alerts and instructions
- **Mixed Reality**: Blend of virtual and physical gameplay elements

### Machine Learning
- **Dynamic Balancing**: AI-adjusted game parameters
- **Personalized Challenges**: Tailored difficulty and objectives
- **Behavior Prediction**: Anticipatory gameplay assistance
- **Cheat Detection**: Advanced pattern recognition for fair play

### IoT Integration
- **Smart Device Interaction**: Campus infrastructure integration
- **Environmental Sensors**: Real-time area monitoring
- **Wearable Support**: Smartwatch and fitness tracker integration
- **Automated Check-ins**: Proximity-based location verification

This comprehensive game mechanics documentation provides the foundation for understanding how the Assassin Game creates engaging, fair, and exciting gameplay experiences while maintaining safety, accessibility, and community standards.