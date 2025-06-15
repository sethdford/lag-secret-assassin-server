# WebSocket API Documentation

## Overview

The Assassin Game WebSocket API provides real-time communication for live game events, location tracking, kill notifications, and system updates. The WebSocket connection enables instant bidirectional communication between the client and server for an engaging real-time gaming experience.

## Connection

### WebSocket URL
```
wss://api.assassingame.com/websocket?token={jwt_token}
```

### Local Development URL
```
ws://localhost:3002/websocket?token={jwt_token}
```

### Authentication

WebSocket connections require JWT authentication via query parameter:
- **Parameter**: `token`
- **Value**: Valid JWT token obtained from `/auth/signin`
- **Example**: `wss://api.assassingame.com/websocket?token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`

### Connection Lifecycle

1. **Establish Connection**: Client connects with valid JWT token
2. **Authentication**: Server validates token and associates connection with player
3. **Game Association**: Player's active game is determined for event filtering
4. **Event Subscription**: Connection is subscribed to relevant game events
5. **Heartbeat**: Regular ping/pong messages maintain connection health
6. **Disconnection**: Graceful or ungraceful connection termination

## Message Format

All WebSocket messages follow a consistent JSON format:

```json
{
  "type": "message_type",
  "payload": {
    // Message-specific data
  },
  "timestamp": 1640995200000,
  "requestId": "optional-request-id"
}
```

### Message Fields

- **`type`** (string, required): Message type identifier
- **`payload`** (object, required): Message-specific data payload
- **`timestamp`** (number, required): Unix timestamp in milliseconds
- **`requestId`** (string, optional): Client-provided request ID for tracking responses

## Message Types

### Client-to-Server Messages

#### 1. Player Location Update

**Type**: `player_update`

**Description**: Update player's current location

**Payload**:
```json
{
  "gameId": "123e4567-e89b-12d3-a456-426614174000",
  "latitude": 40.7128,
  "longitude": -74.0060,
  "accuracy": 5.0
}
```

**Example**:
```json
{
  "type": "player_update",
  "payload": {
    "gameId": "123e4567-e89b-12d3-a456-426614174000",
    "latitude": 40.7128,
    "longitude": -74.0060,
    "accuracy": 5.0
  },
  "timestamp": 1640995200000,
  "requestId": "loc-update-001"
}
```

#### 2. Ping

**Type**: `ping`

**Description**: Health check message to maintain connection

**Payload**:
```json
{
  "message": "ping"
}
```

#### 3. Kill Report

**Type**: `kill_report`

**Description**: Report an elimination (may also be handled via REST API)

**Payload**:
```json
{
  "gameId": "123e4567-e89b-12d3-a456-426614174000",
  "targetPlayerId": "456e7890-e89b-12d3-a456-426614174000",
  "verificationType": "gps",
  "location": {
    "latitude": 40.7128,
    "longitude": -74.0060,
    "accuracy": 3.0
  },
  "verificationData": {
    "photoUrl": "https://example.com/kill-photo.jpg"
  }
}
```

### Server-to-Client Messages

#### 1. Connection Established

**Type**: `connection_established`

**Description**: Confirms successful connection and authentication

**Payload**:
```json
{
  "playerId": "123e4567-e89b-12d3-a456-426614174000",
  "connectionId": "conn_abc123",
  "gameId": "456e7890-e89b-12d3-a456-426614174000",
  "message": "Connected successfully"
}
```

#### 2. Game State Update

**Type**: `game_update`

**Description**: Notification of game state changes

**Payload**:
```json
{
  "gameId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "active",
  "playerCount": 42,
  "event": "game_started",
  "message": "The hunt begins!"
}
```

#### 3. Kill Notification

**Type**: `kill_reported`

**Description**: Notification when a kill is reported

**Payload**:
```json
{
  "killId": "789e0123-e89b-12d3-a456-426614174000",
  "gameId": "123e4567-e89b-12d3-a456-426614174000",
  "killerPlayerId": "456e7890-e89b-12d3-a456-426614174000",
  "targetPlayerId": "789e0123-e89b-12d3-a456-426614174000",
  "status": "pending",
  "location": {
    "latitude": 40.7128,
    "longitude": -74.0060
  },
  "verificationDeadline": "2024-01-15T12:00:00Z"
}
```

#### 4. Kill Verification

**Type**: `kill_verified`

**Description**: Notification when a kill is verified or rejected

**Payload**:
```json
{
  "killId": "789e0123-e89b-12d3-a456-426614174000",
  "gameId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "verified",
  "newTargetPlayerId": "012e3456-e89b-12d3-a456-426614174000",
  "eliminatedPlayerId": "789e0123-e89b-12d3-a456-426614174000",
  "message": "Kill verified! New target assigned."
}
```

#### 5. Shrinking Zone Update

**Type**: `zone_update`

**Description**: Updates about shrinking zone progression

**Payload**:
```json
{
  "gameId": "123e4567-e89b-12d3-a456-426614174000",
  "currentStage": 2,
  "currentRadiusMeters": 500,
  "centerLatitude": 40.7128,
  "centerLongitude": -74.0060,
  "nextShrinkTime": "2024-01-15T12:30:00Z",
  "damagePerSecond": 10,
  "event": "zone_shrinking",
  "message": "The zone is shrinking! Get to safety!"
}
```

#### 6. Safe Zone Update

**Type**: `safe_zone_update`

**Description**: Notifications about safe zone changes

**Payload**:
```json
{
  "gameId": "123e4567-e89b-12d3-a456-426614174000",
  "safeZoneId": "345e6789-e89b-12d3-a456-426614174000",
  "event": "safe_zone_activated",
  "location": {
    "latitude": 40.7128,
    "longitude": -74.0060
  },
  "radiusMeters": 50,
  "duration": 300,
  "message": "Temporary safe zone activated!"
}
```

#### 7. Player Events

**Type**: `player_joined`

**Description**: Notification when a player joins the game

**Payload**:
```json
{
  "gameId": "123e4567-e89b-12d3-a456-426614174000",
  "playerId": "567e8901-e89b-12d3-a456-426614174000",
  "playerName": "ShadowHunter",
  "playerCount": 43,
  "message": "ShadowHunter has joined the hunt!"
}
```

**Type**: `player_left`

**Description**: Notification when a player leaves the game

**Payload**:
```json
{
  "gameId": "123e4567-e89b-12d3-a456-426614174000",
  "playerId": "567e8901-e89b-12d3-a456-426614174000",
  "playerName": "ShadowHunter",
  "playerCount": 42,
  "reason": "disconnected",
  "message": "ShadowHunter has left the hunt"
}
```

#### 8. General Notifications

**Type**: `notification`

**Description**: General game notifications and announcements

**Payload**:
```json
{
  "gameId": "123e4567-e89b-12d3-a456-426614174000",
  "title": "Game Event",
  "message": "Only 10 players remain!",
  "priority": "high",
  "category": "game_progress",
  "actionRequired": false
}
```

#### 9. Error Messages

**Type**: `error`

**Description**: Error notifications for failed operations

**Payload**:
```json
{
  "code": "INVALID_LOCATION",
  "message": "Location update failed: coordinates out of game boundary",
  "details": {
    "requestId": "loc-update-001",
    "reason": "boundary_violation"
  }
}
```

#### 10. Pong Response

**Type**: `pong`

**Description**: Response to client ping

**Payload**:
```json
{
  "message": "pong",
  "serverTime": 1640995200000
}
```

## Connection Management

### Connection States

1. **Connecting**: Initial connection attempt
2. **Open**: Connection established and authenticated
3. **Closing**: Connection termination initiated
4. **Closed**: Connection terminated

### Heartbeat Protocol

- Client sends `ping` every 30 seconds
- Server responds with `pong` 
- If no pong received within 45 seconds, client should reconnect
- Server may send `ping` to check client responsiveness

### Reconnection Logic

```javascript
let reconnectAttempts = 0;
const maxReconnectAttempts = 5;
const reconnectDelay = 2000; // 2 seconds

function connect() {
  const ws = new WebSocket(`wss://api.assassingame.com/websocket?token=${jwt}`);
  
  ws.onopen = () => {
    console.log('Connected');
    reconnectAttempts = 0;
  };
  
  ws.onclose = () => {
    if (reconnectAttempts < maxReconnectAttempts) {
      setTimeout(() => {
        reconnectAttempts++;
        connect();
      }, reconnectDelay * reconnectAttempts);
    }
  };
  
  ws.onerror = (error) => {
    console.error('WebSocket error:', error);
  };
}
```

## Event Filtering

### Game-Based Filtering

- Players only receive events for their active game
- System events (maintenance, announcements) sent to all connected players
- Admin users may receive events for multiple games

### Privacy Filtering

- Location updates respect player privacy settings
- Kill notifications filtered based on game participation
- Sensitive player data excluded from broadcasts

### Performance Optimization

- High-frequency events (location updates) may be throttled
- Batch updates for multiple simultaneous events
- Client-side filtering for UI performance

## Error Handling

### Common Error Codes

| Code | Description | Action |
|------|-------------|---------|
| `INVALID_TOKEN` | JWT token invalid or expired | Refresh token and reconnect |
| `GAME_NOT_FOUND` | Referenced game doesn't exist | Verify game ID |
| `PLAYER_NOT_IN_GAME` | Player not participating in game | Join game first |
| `INVALID_LOCATION` | Location coordinates invalid | Check GPS/location services |
| `RATE_LIMITED` | Too many messages sent | Reduce message frequency |
| `CONNECTION_LIMIT` | Too many concurrent connections | Close duplicate connections |

### Error Response Format

```json
{
  "type": "error",
  "payload": {
    "code": "INVALID_TOKEN",
    "message": "JWT token has expired",
    "details": {
      "expiredAt": "2024-01-15T10:30:00Z",
      "shouldRefresh": true
    }
  },
  "timestamp": 1640995200000
}
```

## Security Considerations

### Authentication
- JWT tokens required for all connections
- Token validation on every message
- Automatic disconnection on token expiry

### Rate Limiting
- Maximum 60 messages per minute per connection
- Location updates limited to 1 per second
- Burst allowance for initial connection

### Data Validation
- All incoming messages validated against schemas
- Location coordinates validated for reasonableness
- Game state consistency checks

### Privacy Protection
- Player location shared only with game participants
- Sensitive data filtered from broadcasts
- Configurable privacy settings respected

## Client Implementation Examples

### JavaScript/Browser

```javascript
class AssassinGameWebSocket {
  constructor(token) {
    this.token = token;
    this.ws = null;
    this.handlers = new Map();
  }
  
  connect() {
    this.ws = new WebSocket(`wss://api.assassingame.com/websocket?token=${this.token}`);
    
    this.ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      const handler = this.handlers.get(message.type);
      if (handler) {
        handler(message.payload);
      }
    };
  }
  
  on(messageType, handler) {
    this.handlers.set(messageType, handler);
  }
  
  updateLocation(gameId, latitude, longitude, accuracy) {
    this.send('player_update', {
      gameId,
      latitude,
      longitude,
      accuracy
    });
  }
  
  send(type, payload) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({
        type,
        payload,
        timestamp: Date.now()
      }));
    }
  }
}

// Usage
const gameWS = new AssassinGameWebSocket(jwtToken);
gameWS.connect();

gameWS.on('game_update', (data) => {
  console.log('Game state changed:', data);
});

gameWS.on('kill_verified', (data) => {
  console.log('Kill verified:', data);
});
```

### Swift/iOS

```swift
import Foundation
import Starscream

class AssassinGameWebSocket: WebSocketDelegate {
    private var socket: WebSocket?
    private let token: String
    
    init(token: String) {
        self.token = token
    }
    
    func connect() {
        guard let url = URL(string: "wss://api.assassingame.com/websocket?token=\(token)") else {
            return
        }
        
        socket = WebSocket(request: URLRequest(url: url))
        socket?.delegate = self
        socket?.connect()
    }
    
    func updateLocation(gameId: String, latitude: Double, longitude: Double, accuracy: Double) {
        let message: [String: Any] = [
            "type": "player_update",
            "payload": [
                "gameId": gameId,
                "latitude": latitude,
                "longitude": longitude,
                "accuracy": accuracy
            ],
            "timestamp": Int64(Date().timeIntervalSince1970 * 1000)
        ]
        
        if let data = try? JSONSerialization.data(withJSONObject: message),
           let jsonString = String(data: data, encoding: .utf8) {
            socket?.write(string: jsonString)
        }
    }
    
    // WebSocketDelegate methods
    func didReceive(event: WebSocketEvent, client: WebSocket) {
        switch event {
        case .connected:
            print("WebSocket connected")
        case .text(let string):
            handleMessage(string)
        case .disconnected(let reason, let code):
            print("WebSocket disconnected: \(reason) with code: \(code)")
        default:
            break
        }
    }
    
    private func handleMessage(_ message: String) {
        // Parse and handle incoming messages
        if let data = message.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let type = json["type"] as? String,
           let payload = json["payload"] {
            
            switch type {
            case "game_update":
                handleGameUpdate(payload)
            case "kill_verified":
                handleKillVerified(payload)
            // Handle other message types
            default:
                break
            }
        }
    }
}
```

## Performance Considerations

### Message Frequency
- Location updates: Maximum 1 per second
- Heartbeat: Every 30 seconds
- Event notifications: Real-time (no throttling)

### Connection Limits
- Maximum 5 concurrent connections per player
- Automatic cleanup of stale connections
- Load balancing across multiple WebSocket servers

### Scalability
- Supports 1000+ concurrent connections per game
- Horizontal scaling with connection pools
- Redis pub/sub for multi-server event distribution

## Monitoring and Analytics

### Connection Metrics
- Active connection count
- Message throughput
- Connection duration
- Reconnection frequency

### Performance Metrics
- Message latency
- Processing time
- Error rates
- Bandwidth usage

### Business Metrics
- Player engagement (messages per session)
- Real-time interaction patterns
- Feature usage (location sharing, kill reporting)

## Testing

### Unit Tests
- Message parsing and validation
- Event handler registration
- Connection state management

### Integration Tests
- End-to-end message flow
- Multi-client scenarios
- Error condition handling

### Load Testing
- High concurrent connection count
- Message throughput under load
- Resource usage optimization

## Troubleshooting

### Common Issues

1. **Connection Fails**
   - Check JWT token validity
   - Verify WebSocket URL
   - Check network connectivity

2. **Messages Not Received**
   - Verify event handlers registered
   - Check message filtering
   - Confirm game participation

3. **Frequent Disconnections**
   - Check network stability
   - Verify heartbeat implementation
   - Review token expiration

4. **High Latency**
   - Check server load
   - Verify client-side processing
   - Review network conditions

### Debug Tools

```javascript
// Enable WebSocket debugging
websocket.debug = true;

// Log all messages
websocket.onmessage = (event) => {
  console.log('Received:', event.data);
  // Handle message normally
};

// Monitor connection state
websocket.onopen = () => console.log('Connected');
websocket.onclose = () => console.log('Disconnected');
websocket.onerror = (error) => console.error('Error:', error);
```

## Future Enhancements

### Planned Features
- Message compression for bandwidth optimization
- Binary message support for large payloads
- Client-side message caching
- Enhanced security with message signing

### API Evolution
- Versioned message formats
- Backward compatibility support
- Gradual migration strategies
- Feature flags for new capabilities