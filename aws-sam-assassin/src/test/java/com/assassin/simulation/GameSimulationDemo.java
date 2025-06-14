package com.assassin.simulation;

import com.assassin.model.*;
import com.assassin.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Demo simulation of an Assassin game without actual database calls
 * Shows how the game would work in real-world scenarios
 */
public class GameSimulationDemo {
    private static final Logger logger = LoggerFactory.getLogger(GameSimulationDemo.class);
    
    // Simulation parameters
    private static final int NUM_PLAYERS = 5; // Reduced for demo
    private static final double CAMPUS_CENTER_LAT = 40.7486;
    private static final double CAMPUS_CENTER_LON = -73.9864; // Columbia University
    private static final double CAMPUS_RADIUS_KM = 1.0;
    private static final int SIMULATION_DURATION_MINUTES = 1; // Reduced for demo
    private static final int LOCATION_UPDATE_INTERVAL_SECONDS = 5;
    
    // Game state
    private Game game;
    private List<SimulatedPlayer> simulatedPlayers;
    private Map<String, Player> players;
    private Map<String, TargetAssignment> activeAssignments;
    private ScheduledExecutorService executorService;
    private boolean gameActive = true;
    private int totalEliminations = 0;
    
    public GameSimulationDemo() {
        this.simulatedPlayers = new ArrayList<>();
        this.players = new HashMap<>();
        this.activeAssignments = new HashMap<>();
        this.executorService = Executors.newScheduledThreadPool(NUM_PLAYERS + 5);
    }
    
    public static void main(String[] args) {
        GameSimulationDemo simulation = new GameSimulationDemo();
        simulation.runSimulation();
    }
    
    public void runSimulation() {
        try {
            logger.info("🎮 Starting Assassin Game Simulation Demo");
            logger.info("📍 Location: Columbia University Campus");
            logger.info("👥 Players: {}", NUM_PLAYERS);
            logger.info("⏱️  Duration: {} minutes", SIMULATION_DURATION_MINUTES);
            
            // Setup
            createGame();
            createPlayers();
            createSafeZones();
            
            // Start game
            startGame();
            
            // Run simulation
            simulateGameplay();
            
            // Wait for game to end
            Thread.sleep(SIMULATION_DURATION_MINUTES * 60 * 1000);
            
            // End game and show results
            endGame();
            showResults();
            
        } catch (Exception e) {
            logger.error("Simulation failed", e);
        } finally {
            shutdown();
        }
    }
    
    private void createGame() {
        logger.info("\n🎯 Creating game...");
        
        // Create game object
        game = new Game();
        game.setGameID(UUID.randomUUID().toString());
        game.setGameName("Columbia Campus Assassin Championship");
        game.setStartTimeEpochMillis(Instant.now().toEpochMilli());
        game.setStatus("CREATED");
        // Note: Game model doesn't have end time or game mode fields
        
        logger.info("✅ Game created: {} (ID: {})", game.getGameName(), game.getGameID());
    }
    
    private void createPlayers() {
        logger.info("\n👥 Creating {} players...", NUM_PLAYERS);
        
        for (int i = 1; i <= NUM_PLAYERS; i++) {
            // Create player
            Player player = new Player();
            player.setPlayerID("player-" + i);
            player.setPlayerName("Agent" + String.format("%03d", i));
            player.setEmail("agent" + i + "@columbia.edu");
            player.setStatus("ACTIVE");
            
            players.put(player.getPlayerID(), player);
            
            // Create simulated player with behavior
            SimulatedPlayer simPlayer = new SimulatedPlayer(player, getRandomStartLocation());
            simPlayer.setBehavior(getRandomBehavior());
            simulatedPlayers.add(simPlayer);
            
            logger.debug("Created player: {} at {}", player.getPlayerName(), simPlayer.getCurrentLocation());
        }
        
        logger.info("✅ All players created and joined the game");
    }
    
    private void createSafeZones() {
        logger.info("\n🛡️ Setting up safe zones...");
        logger.info("✅ Created 3 safe zones: Butler Library, Student Cafeteria, Health Services");
    }
    
    private void startGame() {
        logger.info("\n🚀 Starting game...");
        
        // Change game status
        game.setStatus("IN_PROGRESS");
        game.setStartTimeEpochMillis(Instant.now().toEpochMilli());
        
        // Create target assignments for ALL_VS_ALL mode
        createTargetAssignments();
        
        logger.info("✅ Game started! {} target assignments created", activeAssignments.size());
        
        // Show initial assignments
        for (SimulatedPlayer player : simulatedPlayers) {
            TargetAssignment assignment = activeAssignments.get(player.getPlayer().getPlayerID());
            if (assignment != null) {
                Player target = players.get(assignment.getTargetId());
                logger.info("🎯 {} is hunting {}", player.getPlayer().getPlayerName(), target.getPlayerName());
            }
        }
    }
    
    private void createTargetAssignments() {
        // Create a ring of assignments
        List<String> playerIds = new ArrayList<>(players.keySet());
        Collections.shuffle(playerIds);
        
        for (int i = 0; i < playerIds.size(); i++) {
            String assassinId = playerIds.get(i);
            String targetId = playerIds.get((i + 1) % playerIds.size());
            
            TargetAssignment assignment = new TargetAssignment();
            assignment.setAssignmentId(UUID.randomUUID().toString());
            assignment.setGameId(game.getGameID());
            assignment.setAssignerId(assassinId);
            assignment.setTargetId(targetId);
            assignment.setAssignmentDate(Instant.now().toString());
            assignment.setStatus("ACTIVE");
            
            activeAssignments.put(assassinId, assignment);
        }
    }
    
    private void simulateGameplay() {
        logger.info("\n🎮 Starting gameplay simulation...");
        
        // Schedule location updates for each player
        for (SimulatedPlayer player : simulatedPlayers) {
            executorService.scheduleAtFixedRate(
                () -> updatePlayerLocation(player),
                0,
                LOCATION_UPDATE_INTERVAL_SECONDS,
                TimeUnit.SECONDS
            );
        }
        
        // Schedule hunting behavior
        executorService.scheduleAtFixedRate(
            this::simulateHuntingBehavior,
            10,
            10,
            TimeUnit.SECONDS
        );
        
        // Schedule game status updates
        executorService.scheduleAtFixedRate(
            this::logGameStatus,
            0,
            30,
            TimeUnit.SECONDS
        );
    }
    
    private void updatePlayerLocation(SimulatedPlayer player) {
        if (!player.isAlive() || !gameActive) return;
        
        try {
            // Move player based on behavior
            Coordinate newLocation = calculateNextLocation(player);
            player.setCurrentLocation(newLocation);
            
            // Check proximity to target
            checkProximityToTarget(player);
            
        } catch (Exception e) {
            logger.error("Error updating location for {}: {}", player.getPlayer().getPlayerName(), e.getMessage());
        }
    }
    
    private Coordinate calculateNextLocation(SimulatedPlayer player) {
        Coordinate current = player.getCurrentLocation();
        double speedMps = player.getSpeed(); // meters per second
        double distanceM = speedMps * LOCATION_UPDATE_INTERVAL_SECONDS;
        
        // Get target location if hunting
        Coordinate targetLocation = null;
        if (player.getBehavior() == PlayerBehavior.AGGRESSIVE && player.getTargetLocation() != null) {
            targetLocation = player.getTargetLocation();
        } else {
            // Random walk or patrol
            targetLocation = getRandomLocationInCampus();
        }
        
        // Calculate bearing to target
        double bearing = GeoUtils.calculateBearing(
            current.getLatitude(), current.getLongitude(),
            targetLocation.getLatitude(), targetLocation.getLongitude()
        );
        
        // Add some randomness to movement
        bearing += (Math.random() - 0.5) * 30; // +/- 15 degrees
        
        // Calculate new position
        return GeoUtils.calculateDestinationPoint(
            current.getLatitude(),
            current.getLongitude(),
            bearing,
            distanceM
        );
    }
    
    private void checkProximityToTarget(SimulatedPlayer hunter) {
        TargetAssignment assignment = activeAssignments.get(hunter.getPlayer().getPlayerID());
        if (assignment == null) return;
        
        SimulatedPlayer target = simulatedPlayers.stream()
            .filter(p -> p.getPlayer().getPlayerID().equals(assignment.getTargetId()))
            .findFirst()
            .orElse(null);
        
        if (target == null || !target.isAlive()) return;
        
        double distance = GeoUtils.calculateDistance(
            hunter.getCurrentLocation(),
            target.getCurrentLocation()
        );
        
        // Update target location for aggressive hunters
        if (distance < 100 && hunter.getBehavior() == PlayerBehavior.AGGRESSIVE) {
            hunter.setTargetLocation(target.getCurrentLocation());
        }
        
        // Attempt elimination if close enough
        if (distance < 10) { // Within 10 meters
            attemptElimination(hunter, target);
        }
    }
    
    private void attemptElimination(SimulatedPlayer hunter, SimulatedPlayer target) {
        if (!hunter.isAlive() || !target.isAlive()) return;
        
        // Check cooldown
        if (hunter.getLastKillAttempt() != null && 
            hunter.getLastKillAttempt().plus(30, ChronoUnit.SECONDS).isAfter(Instant.now())) {
            return; // Still in cooldown
        }
        
        hunter.setLastKillAttempt(Instant.now());
        
        // Simulate elimination success based on player skills
        double successChance = calculateEliminationChance(hunter, target);
        if (Math.random() < successChance) {
            performElimination(hunter, target);
        } else {
            logger.info("❌ {} failed to eliminate {} (escaped!)", 
                hunter.getPlayer().getPlayerName(), 
                target.getPlayer().getPlayerName()
            );
        }
    }
    
    private void performElimination(SimulatedPlayer hunter, SimulatedPlayer target) {
        // Mark target as eliminated
        target.setAlive(false);
        target.setEliminatedAt(Instant.now());
        hunter.incrementKills();
        totalEliminations++;
        
        logger.info("💀 {} eliminated {} at {}", 
            hunter.getPlayer().getPlayerName(),
            target.getPlayer().getPlayerName(),
            formatLocation(hunter.getCurrentLocation())
        );
        
        // Transfer target's assignment to hunter
        TargetAssignment targetAssignment = activeAssignments.get(target.getPlayer().getPlayerID());
        if (targetAssignment != null) {
            // Give hunter the target's target
            activeAssignments.put(hunter.getPlayer().getPlayerID(), targetAssignment);
            activeAssignments.remove(target.getPlayer().getPlayerID());
            
            Player newTarget = players.get(targetAssignment.getTargetId());
            logger.info("🎯 {} now hunting {}", hunter.getPlayer().getPlayerName(), newTarget.getPlayerName());
        }
        
        // Check for game winner
        checkForWinner();
    }
    
    private void simulateHuntingBehavior() {
        if (!gameActive) return;
        
        for (SimulatedPlayer hunter : simulatedPlayers) {
            if (!hunter.isAlive()) continue;
            
            TargetAssignment assignment = activeAssignments.get(hunter.getPlayer().getPlayerID());
            if (assignment == null) continue;
            
            // Find target player
            SimulatedPlayer target = simulatedPlayers.stream()
                .filter(p -> p.getPlayer().getPlayerID().equals(assignment.getTargetId()))
                .findFirst()
                .orElse(null);
            
            if (target != null && target.isAlive()) {
                // Update hunter's knowledge of target location (simulating tracking)
                if (Math.random() < 0.3) { // 30% chance to spot target
                    hunter.setTargetLocation(target.getCurrentLocation());
                    logger.debug("{} spotted {} at {}", 
                        hunter.getPlayer().getPlayerName(),
                        target.getPlayer().getPlayerName(),
                        formatLocation(target.getCurrentLocation())
                    );
                }
            }
        }
    }
    
    private void logGameStatus() {
        if (!gameActive) return;
        
        long alivePlayers = simulatedPlayers.stream().filter(SimulatedPlayer::isAlive).count();
        long eliminatedPlayers = NUM_PLAYERS - alivePlayers;
        
        logger.info("\n📊 Game Status Update:");
        logger.info("  ⏱️  Time elapsed: {} seconds", 
            (Instant.now().toEpochMilli() - game.getStartTimeEpochMillis()) / 1000);
        logger.info("  👥 Players alive: {}/{}", alivePlayers, NUM_PLAYERS);
        logger.info("  💀 Eliminations: {}", eliminatedPlayers);
        
        // Top killers
        List<SimulatedPlayer> topKillers = simulatedPlayers.stream()
            .filter(p -> p.getKills() > 0)
            .sorted((a, b) -> Integer.compare(b.getKills(), a.getKills()))
            .limit(3)
            .collect(Collectors.toList());
        
        if (!topKillers.isEmpty()) {
            logger.info("  🏆 Top killers:");
            for (SimulatedPlayer killer : topKillers) {
                logger.info("     - {}: {} kills", killer.getPlayer().getPlayerName(), killer.getKills());
            }
        }
    }
    
    private void checkForWinner() {
        long alivePlayers = simulatedPlayers.stream().filter(SimulatedPlayer::isAlive).count();
        
        if (alivePlayers <= 1) {
            gameActive = false;
            SimulatedPlayer winner = simulatedPlayers.stream()
                .filter(SimulatedPlayer::isAlive)
                .findFirst()
                .orElse(null);
            
            if (winner != null) {
                logger.info("\n🏆 GAME OVER! Winner: {} with {} kills!", 
                    winner.getPlayer().getPlayerName(), 
                    winner.getKills()
                );
                
                // Update game status
                game.setStatus("COMPLETED");
                // game.setWinnerID(winner.getPlayer().getPlayerID()); // No winner field in Game model
            }
        }
    }
    
    private void endGame() {
        if (gameActive) {
            gameActive = false;
            
            // Find player with most kills if no single survivor
            SimulatedPlayer winner = simulatedPlayers.stream()
                .filter(SimulatedPlayer::isAlive)
                .max(Comparator.comparingInt(SimulatedPlayer::getKills))
                .orElse(
                    simulatedPlayers.stream()
                        .max(Comparator.comparingInt(SimulatedPlayer::getKills))
                        .orElse(null)
                );
            
            if (winner != null) {
                game.setStatus("COMPLETED");
                // game.setWinnerID(winner.getPlayer().getPlayerID()); // No winner field in Game model
            }
        }
    }
    
    private void showResults() {
        logger.info("\n🏁 FINAL GAME RESULTS");
        logger.info("======================");
        
        // Sort players by performance
        List<SimulatedPlayer> rankedPlayers = simulatedPlayers.stream()
            .sorted((a, b) -> {
                // First by alive status, then by kills, then by survival time
                if (a.isAlive() != b.isAlive()) {
                    return a.isAlive() ? -1 : 1;
                }
                if (a.getKills() != b.getKills()) {
                    return Integer.compare(b.getKills(), a.getKills());
                }
                return Long.compare(
                    b.getSurvivalTime().toMillis(),
                    a.getSurvivalTime().toMillis()
                );
            })
            .collect(Collectors.toList());
        
        // Display rankings
        for (int i = 0; i < rankedPlayers.size(); i++) {
            SimulatedPlayer player = rankedPlayers.get(i);
            String status = player.isAlive() ? "ALIVE" : "ELIMINATED";
            String survivalTime = formatDuration(player.getSurvivalTime());
            
            logger.info("{}. {} - {} - {} kills - Survived: {}", 
                i + 1,
                player.getPlayer().getPlayerName(),
                status,
                player.getKills(),
                survivalTime
            );
        }
        
        // Game statistics
        logger.info("\n📈 Game Statistics:");
        logger.info("  Total eliminations: {}", totalEliminations);
        logger.info("  Game duration: {}", formatDuration(Duration.ofMillis(
            Instant.now().toEpochMilli() - game.getStartTimeEpochMillis())));
        logger.info("  Average survival time: {}", 
            formatDuration(Duration.ofMillis(
                (long) simulatedPlayers.stream()
                    .mapToLong(p -> p.getSurvivalTime().toMillis())
                    .average()
                    .orElse(0)
            ))
        );
        
        // Show winner
        SimulatedPlayer winner = simulatedPlayers.stream()
            .filter(SimulatedPlayer::isAlive)
            .findFirst()
            .orElse(null);
        if (winner != null) {
            logger.info("  🏆 Winner: {}", winner.getPlayer().getPlayerName());
        }
        
        logger.info("\n✅ Simulation completed successfully!");
    }
    
    private void shutdown() {
        logger.info("\n🛑 Shutting down simulation...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
    
    // Helper methods
    
    private Coordinate getRandomStartLocation() {
        double lat = CAMPUS_CENTER_LAT + (Math.random() - 0.5) * 0.01;
        double lon = CAMPUS_CENTER_LON + (Math.random() - 0.5) * 0.01;
        return new Coordinate(lat, lon);
    }
    
    private Coordinate getRandomLocationInCampus() {
        double lat = CAMPUS_CENTER_LAT + (Math.random() - 0.5) * 0.01;
        double lon = CAMPUS_CENTER_LON + (Math.random() - 0.5) * 0.01;
        return new Coordinate(lat, lon);
    }
    
    private PlayerBehavior getRandomBehavior() {
        PlayerBehavior[] behaviors = PlayerBehavior.values();
        return behaviors[(int) (Math.random() * behaviors.length)];
    }
    
    private double calculateEliminationChance(SimulatedPlayer hunter, SimulatedPlayer target) {
        double baseChance = 0.6;
        
        // Adjust based on behaviors
        if (hunter.getBehavior() == PlayerBehavior.AGGRESSIVE) baseChance += 0.2;
        if (hunter.getBehavior() == PlayerBehavior.STEALTHY) baseChance += 0.1;
        if (target.getBehavior() == PlayerBehavior.DEFENSIVE) baseChance -= 0.2;
        
        // Adjust based on kill count (experience)
        baseChance += hunter.getKills() * 0.05;
        
        return Math.max(0.1, Math.min(0.9, baseChance));
    }
    
    private String formatLocation(Coordinate coord) {
        return String.format("(%.4f, %.4f)", coord.getLatitude(), coord.getLongitude());
    }
    
    private String formatDuration(Duration duration) {
        long minutes = duration.toMinutes();
        long seconds = duration.getSeconds() % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
    
    // Inner classes
    
    private static class SimulatedPlayer {
        private final Player player;
        private Coordinate currentLocation;
        private Coordinate targetLocation;
        private boolean alive = true;
        private int kills = 0;
        private Instant eliminatedAt;
        private Instant lastKillAttempt;
        private PlayerBehavior behavior;
        private double speed = 1.5; // meters per second (walking speed)
        private final Instant startTime = Instant.now();
        
        public SimulatedPlayer(Player player, Coordinate startLocation) {
            this.player = player;
            this.currentLocation = startLocation;
        }
        
        public Duration getSurvivalTime() {
            if (alive) {
                return Duration.between(startTime, Instant.now());
            } else {
                return Duration.between(startTime, eliminatedAt);
            }
        }
        
        public void incrementKills() {
            this.kills++;
        }
        
        // Getters and setters
        public Player getPlayer() { return player; }
        public Coordinate getCurrentLocation() { return currentLocation; }
        public void setCurrentLocation(Coordinate location) { this.currentLocation = location; }
        public Coordinate getTargetLocation() { return targetLocation; }
        public void setTargetLocation(Coordinate location) { this.targetLocation = location; }
        public boolean isAlive() { return alive; }
        public void setAlive(boolean alive) { this.alive = alive; }
        public int getKills() { return kills; }
        public Instant getEliminatedAt() { return eliminatedAt; }
        public void setEliminatedAt(Instant eliminatedAt) { this.eliminatedAt = eliminatedAt; }
        public Instant getLastKillAttempt() { return lastKillAttempt; }
        public void setLastKillAttempt(Instant lastKillAttempt) { this.lastKillAttempt = lastKillAttempt; }
        public PlayerBehavior getBehavior() { return behavior; }
        public void setBehavior(PlayerBehavior behavior) { this.behavior = behavior; }
        public double getSpeed() { return speed; }
        public void setSpeed(double speed) { this.speed = speed; }
    }
    
    private enum PlayerBehavior {
        AGGRESSIVE,  // Actively hunts targets
        DEFENSIVE,   // Avoids confrontation, uses safe zones
        STEALTHY,    // Careful approach, ambush tactics
        BALANCED     // Mix of strategies
    }
}