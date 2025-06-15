# LAG Secret Assassin - Game Simulation Guide

## Overview
This guide helps you run simulated games to validate that everything works correctly in your LAG Secret Assassin platform.

## Existing Simulations

### 1. 🎮 Real-World Game Simulation
**File**: `src/test/java/com/assassin/simulation/GameSimulation.java`

Simulates a complete 30-minute game with 20 players at Columbia University campus.

```bash
# Run the simulation
cd aws-sam-assassin
mvn test -Dtest=GameSimulation
```

**Features Tested**:
- Player movement and location updates
- Safe zone boundaries
- Target assignment chains
- Proximity detection
- Kill attempts and verification
- Winner determination

### 2. 🔄 End-to-End Game Flow Test
**File**: `src/test/java/com/assassin/e2e/GameFlowEndToEndTest.java`

Complete game lifecycle testing with LocalStack.

```bash
# Run end-to-end tests
mvn test -Dtest=GameFlowEndToEndTest
```

**Features Tested**:
- Player creation and authentication
- Kill recording (GPS, NFC, Photo)
- Moderator approval workflows
- Timeline and history tracking

### 3. 🌐 API Simulation Suite
**File**: `src/test/java/com/assassin/simulation/ApiTestSimulation.java`

Comprehensive API testing simulating Stanford University game.

```bash
# Run API simulation
mvn test -Dtest=ApiTestSimulation
```

**Features Tested**:
- All REST API endpoints
- Payment processing
- Real-time updates
- Map and statistics
- Data export

### 4. 🚀 WebSocket Load Test
**File**: `src/test/java/com/assassin/websocket/WebSocketLoadTest.java`

Performance testing with concurrent connections.

```bash
# Run load test
mvn test -Dtest=WebSocketLoadTest
```

## Running All Simulations

### Option 1: Local Testing with LocalStack

```bash
# Start LocalStack
docker-compose up -d

# Run all simulations
mvn test -Dtest="*Simulation,*EndToEnd*,*LoadTest"

# View results
cat target/surefire-reports/*.txt
```

### Option 2: Test Against Deployed AWS Environment

First, set up test environment variables:

```bash
export API_ENDPOINT="https://your-api-id.execute-api.us-east-1.amazonaws.com/dev"
export COGNITO_USER_POOL_ID="us-east-1_xxxxxxxxx"
export COGNITO_CLIENT_ID="xxxxxxxxxxxxxxxxxxxxxxxxxx"
```

Then run the integration tests:

```bash
# Run against live AWS
mvn test -Dtest="*IntegrationTest" \
  -Dapi.endpoint=$API_ENDPOINT \
  -Dcognito.userPoolId=$COGNITO_USER_POOL_ID \
  -Dcognito.clientId=$COGNITO_CLIENT_ID
```

## Custom Simulation Scripts

### 1. Quick Validation Script

Create `scripts/quick-validation.sh`:

```bash
#!/bin/bash

echo "🎯 LAG Secret Assassin - Quick Validation"
echo "========================================"

# Test 1: Health Check
echo -n "Testing API Health... "
curl -s $API_ENDPOINT/health | grep -q "healthy" && echo "✅ PASS" || echo "❌ FAIL"

# Test 2: Create Test Game
echo -n "Creating test game... "
GAME_ID=$(curl -s -X POST $API_ENDPOINT/games \
  -H "Authorization: Bearer $TEST_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Game",
    "organizerId": "test-org",
    "entryFee": 20.0,
    "maxPlayers": 10,
    "campus": "TEST_CAMPUS"
  }' | jq -r '.gameId')
[ ! -z "$GAME_ID" ] && echo "✅ PASS (ID: $GAME_ID)" || echo "❌ FAIL"

# Test 3: Join Game
echo -n "Joining game... "
curl -s -X POST $API_ENDPOINT/games/$GAME_ID/join \
  -H "Authorization: Bearer $TEST_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"playerId": "test-player-1"}' \
  | grep -q "success" && echo "✅ PASS" || echo "❌ FAIL"

echo "========================================"
```

### 2. Performance Validation Script

Create `scripts/performance-test.sh`:

```bash
#!/bin/bash

echo "🚀 Performance Validation Test"
echo "=============================="

# Concurrent player updates
echo "Testing 100 concurrent location updates..."
for i in {1..100}; do
  curl -X PUT $API_ENDPOINT/players/player-$i/location \
    -H "Authorization: Bearer $TEST_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"latitude\": 40.7128, \"longitude\": -74.0060}" &
done
wait
echo "✅ Concurrent updates completed"

# Measure response times
echo -e "\nMeasuring API response times:"
for endpoint in "games" "players" "kills"; do
  TIME=$(curl -o /dev/null -s -w '%{time_total}\n' $API_ENDPOINT/$endpoint)
  echo "$endpoint: ${TIME}s"
done
```

### 3. Full Game Simulation Script

Create `scripts/full-game-simulation.py`:

```python
#!/usr/bin/env python3
import requests
import time
import random
import json
from datetime import datetime

API_ENDPOINT = "YOUR_API_ENDPOINT"
AUTH_TOKEN = "YOUR_AUTH_TOKEN"

class GameSimulator:
    def __init__(self):
        self.headers = {
            "Authorization": f"Bearer {AUTH_TOKEN}",
            "Content-Type": "application/json"
        }
        self.game_id = None
        self.players = []
        
    def create_game(self):
        print("🎮 Creating new game...")
        response = requests.post(
            f"{API_ENDPOINT}/games",
            headers=self.headers,
            json={
                "name": f"Simulated Game {datetime.now()}",
                "organizerId": "simulator",
                "entryFee": 20.0,
                "maxPlayers": 20,
                "campus": "COLUMBIA"
            }
        )
        self.game_id = response.json()["gameId"]
        print(f"✅ Game created: {self.game_id}")
        
    def add_players(self, count=10):
        print(f"👥 Adding {count} players...")
        for i in range(count):
            player_id = f"sim-player-{i}"
            response = requests.post(
                f"{API_ENDPOINT}/players",
                headers=self.headers,
                json={
                    "playerId": player_id,
                    "username": f"Player{i}",
                    "email": f"player{i}@test.com"
                }
            )
            
            # Join game
            requests.post(
                f"{API_ENDPOINT}/games/{self.game_id}/join",
                headers=self.headers,
                json={"playerId": player_id}
            )
            
            self.players.append({
                "id": player_id,
                "lat": 40.8075 + random.uniform(-0.01, 0.01),
                "lng": -73.9626 + random.uniform(-0.01, 0.01),
                "alive": True
            })
        print("✅ Players added and joined game")
        
    def start_game(self):
        print("🚀 Starting game...")
        requests.put(
            f"{API_ENDPOINT}/games/{self.game_id}/start",
            headers=self.headers
        )
        print("✅ Game started")
        
    def simulate_gameplay(self, duration_minutes=5):
        print(f"🎯 Simulating {duration_minutes} minutes of gameplay...")
        start_time = time.time()
        updates = 0
        kills = 0
        
        while (time.time() - start_time) < (duration_minutes * 60):
            # Update player locations
            for player in self.players:
                if player["alive"]:
                    # Random movement
                    player["lat"] += random.uniform(-0.0001, 0.0001)
                    player["lng"] += random.uniform(-0.0001, 0.0001)
                    
                    # Update location
                    requests.put(
                        f"{API_ENDPOINT}/players/{player['id']}/location",
                        headers=self.headers,
                        json={
                            "latitude": player["lat"],
                            "longitude": player["lng"]
                        }
                    )
                    updates += 1
            
            # Simulate kills (10% chance each iteration)
            if random.random() < 0.1 and len([p for p in self.players if p["alive"]]) > 1:
                alive_players = [p for p in self.players if p["alive"]]
                killer = random.choice(alive_players)
                victim = random.choice([p for p in alive_players if p["id"] != killer["id"]])
                
                # Record kill
                response = requests.post(
                    f"{API_ENDPOINT}/kills",
                    headers=self.headers,
                    json={
                        "killerId": killer["id"],
                        "victimId": victim["id"],
                        "gameId": self.game_id,
                        "verificationType": "GPS",
                        "latitude": victim["lat"],
                        "longitude": victim["lng"]
                    }
                )
                
                if response.status_code == 200:
                    victim["alive"] = False
                    kills += 1
                    print(f"💀 {killer['id']} eliminated {victim['id']}")
            
            time.sleep(5)  # Update every 5 seconds
        
        print(f"✅ Simulation complete: {updates} location updates, {kills} eliminations")
        
    def get_results(self):
        print("📊 Getting game results...")
        response = requests.get(
            f"{API_ENDPOINT}/games/{self.game_id}",
            headers=self.headers
        )
        game = response.json()
        
        print(f"Game Status: {game['status']}")
        print(f"Winner: {game.get('winnerId', 'None yet')}")
        print(f"Active Players: {len([p for p in self.players if p['alive']])}")

# Run simulation
if __name__ == "__main__":
    sim = GameSimulator()
    sim.create_game()
    sim.add_players(10)
    sim.start_game()
    sim.simulate_gameplay(5)
    sim.get_results()
```

## Validation Checklist

After running simulations, verify:

### ✅ Core Functionality
- [ ] Games can be created and configured
- [ ] Players can register and join games
- [ ] Location updates work in real-time
- [ ] Proximity detection triggers correctly
- [ ] Kills are recorded and verified
- [ ] Target reassignment works after eliminations
- [ ] Winners are determined correctly

### ✅ Performance Metrics
- [ ] API response times < 500ms
- [ ] Location updates handle 100+ concurrent requests
- [ ] WebSocket connections remain stable
- [ ] No memory leaks during extended gameplay

### ✅ Security & Reliability
- [ ] Authentication works correctly
- [ ] Invalid requests are rejected
- [ ] Rate limiting prevents abuse
- [ ] Error handling works properly

## Monitoring During Simulation

### CloudWatch Metrics
```bash
# Watch Lambda invocations
aws cloudwatch get-metric-statistics \
  --namespace AWS/Lambda \
  --metric-name Invocations \
  --dimensions Name=FunctionName,Value=assassin-game-PlayerFunction \
  --statistics Sum \
  --start-time 2024-01-20T00:00:00Z \
  --end-time 2024-01-20T01:00:00Z \
  --period 300
```

### DynamoDB Activity
```bash
# Monitor table metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/DynamoDB \
  --metric-name ConsumedReadCapacityUnits \
  --dimensions Name=TableName,Value=assassin-game-Players \
  --statistics Sum \
  --start-time 2024-01-20T00:00:00Z \
  --end-time 2024-01-20T01:00:00Z \
  --period 300
```

## Troubleshooting

### Common Issues

1. **LocalStack Connection Failed**
   ```bash
   # Ensure LocalStack is running
   docker ps | grep localstack
   # Restart if needed
   docker-compose restart
   ```

2. **Authentication Errors**
   ```bash
   # Get a test token
   aws cognito-idp admin-initiate-auth \
     --user-pool-id $COGNITO_USER_POOL_ID \
     --client-id $COGNITO_CLIENT_ID \
     --auth-flow ADMIN_NO_SRP_AUTH \
     --auth-parameters USERNAME=testuser,PASSWORD=TestPassword123!
   ```

3. **Performance Issues**
   - Check Lambda cold starts
   - Verify DynamoDB capacity
   - Review CloudWatch logs

## Next Steps

1. Run the existing Java simulations to validate core functionality
2. Deploy to AWS and run integration tests
3. Use the custom scripts for specific scenario testing
4. Monitor CloudWatch metrics during simulations
5. Generate a performance report

The simulations will help ensure your platform can handle real-world usage patterns!