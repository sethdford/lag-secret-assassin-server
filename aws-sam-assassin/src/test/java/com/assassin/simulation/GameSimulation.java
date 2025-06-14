package com.assassin.simulation;

import com.assassin.dao.*;
import com.assassin.exception.PlayerPersistenceException;
import com.assassin.model.*;
import com.assassin.service.*;
import com.assassin.util.GeoUtils;
import com.assassin.websocket.GameWebSocketServer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Real-world synthetic simulation of an Assassin game
 * Simulates players moving around a campus, hunting targets, and eliminating each other
 */
public class GameSimulation {
    private static final Logger logger = LoggerFactory.getLogger(GameSimulation.class);
    
    // Services
    private final GameService gameService;
    private final PlayerService playerService;
    private final LocationService locationService;
    private final KillService killService;
    private final SafeZoneService safeZoneService;
    private final TargetAssignmentService targetAssignmentService;
    private final NotificationService notificationService;
    private final ProximityDetectionService proximityDetectionService;
    // private final GameWebSocketServer webSocketServer; // Removed for testing without Cognito
    
    // Simulation parameters
    private static final int NUM_PLAYERS = 20;
    private static final double CAMPUS_CENTER_LAT = 40.7486;
    private static final double CAMPUS_CENTER_LON = -73.9864; // Example: Columbia University
    private static final double CAMPUS_RADIUS_KM = 1.0;
    private static final int SIMULATION_DURATION_MINUTES = 30;
    private static final int LOCATION_UPDATE_INTERVAL_SECONDS = 5;
    
    // Game state
    private Game game;
    private List<SimulatedPlayer> simulatedPlayers;
    private Map<String, Player> players;
    private Map<String, TargetAssignment> activeAssignments;
    private ScheduledExecutorService executorService;
    private boolean gameActive = true;
    
    public GameSimulation() {
        // Initialize services
        this.gameService = new GameService();
        this.playerService = new PlayerService();
        this.locationService = new LocationService();
        this.killService = new KillService();
        this.safeZoneService = new SafeZoneService();
        this.targetAssignmentService = new TargetAssignmentService();
        this.notificationService = new NotificationService();
        this.proximityDetectionService = new ProximityDetectionService();
        // this.webSocketServer = new GameWebSocketServer(); // Removed for testing without Cognito
        
        this.simulatedPlayers = new ArrayList<>();
        this.players = new HashMap<>();
        this.activeAssignments = new HashMap<>();
        this.executorService = Executors.newScheduledThreadPool(NUM_PLAYERS + 5);
    }
    
    public static void main(String[] args) {
        GameSimulation simulation = new GameSimulation();
        simulation.runSimulation();
    }
    
    @Test
    public void testGameSimulation() {
        runSimulation();
    }
    
    public void runSimulation() {
        try {
            logger.info("🎮 Starting Assassin Game Simulation");
            logger.info("📍 Location: Columbia University Campus");
            logger.info("👥 Players: {}", NUM_PLAYERS);
            logger.info("⏱️  Duration: {} minutes", SIMULATION_DURATION_MINUTES);
            
            // Setup
            createGame();
            createPlayers();
            setupSafeZones();
            
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
        
        // Define campus boundary
        List<Coordinate> boundary = createCampusBoundary();
        
        // Create game configuration
        Map<String, Object> config = new HashMap<>();
        config.put("eliminationMethods", Arrays.asList("GPS", "QR_CODE", "PHOTO"));
        config.put("safeZoneEnabled", true);
        config.put("shrinkingZoneEnabled", true);
        config.put("proximityRequiredMeters", 10.0);
        config.put("respawnEnabled", false);
        
        // Create basic game first - since createGame is not implemented, create manually
        game = new Game();
        game.setGameID(UUID.randomUUID().toString());
        game.setGameName("Columbia Campus Assassin Championship");
        game.setAdminPlayerID("admin-user");
        game.setCreatedAt(Instant.now().toString());
        game.setPlayerIDs(new ArrayList<>());
        
        // Set additional properties
        game.setBoundary(boundary);
        game.setSettings(config);
        game.setShrinkingZoneEnabled(true);
        game.setStatus(GameStatus.PENDING.name());
        
        // Save the game to database
        try {
            new DynamoDbGameDao().saveGame(game);
        } catch (Exception e) {
            logger.error("Failed to save game: {}", e.getMessage());
        }
        
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
            player.setStatus(PlayerStatus.ACTIVE.name());
            player.setActive(true);
            
            // Save player
            try {
                new DynamoDbPlayerDao().savePlayer(player);
            } catch (PlayerPersistenceException e) {
                logger.error("Failed to save player: {}", e.getMessage());
            }
            players.put(player.getPlayerID(), player);
            
            // Join game - add player to game's player list
            game.getPlayerIDs().add(player.getPlayerID());
            
            // Create simulated player with behavior
            SimulatedPlayer simPlayer = new SimulatedPlayer(player, getRandomStartLocation());
            simPlayer.setBehavior(getRandomBehavior());
            simulatedPlayers.add(simPlayer);
            
            logger.debug("Created player: {} at {}", player.getPlayerName(), simPlayer.getCurrentLocation());
        }
        
        logger.info("✅ All players created and joined the game");
    }
    
    private void setupSafeZones() {
        logger.info("\n🛡️ Setting up safe zones...");
        
        // Create main safe zones
        PublicSafeZone libraryZone = new PublicSafeZone();
        libraryZone.setGameId(game.getGameID());
        libraryZone.setName("Butler Library");
        libraryZone.setLatitude(40.7484);
        libraryZone.setLongitude(-73.9857);
        libraryZone.setRadiusMeters(50.0);
        libraryZone.setIsActive(true);
        safeZoneService.createSafeZone(libraryZone);
        
        PublicSafeZone cafeteriaZone = new PublicSafeZone();
        cafeteriaZone.setGameId(game.getGameID());
        cafeteriaZone.setName("Student Cafeteria");
        cafeteriaZone.setLatitude(40.7490);
        cafeteriaZone.setLongitude(-73.9870);
        cafeteriaZone.setRadiusMeters(30.0);
        cafeteriaZone.setIsActive(true);
        safeZoneService.createSafeZone(cafeteriaZone);
        
        PublicSafeZone medicalZone = new PublicSafeZone();
        medicalZone.setGameId(game.getGameID());
        medicalZone.setName("Health Services");
        medicalZone.setLatitude(40.7478);
        medicalZone.setLongitude(-73.9850);
        medicalZone.setRadiusMeters(20.0);
        medicalZone.setIsActive(true);
        safeZoneService.createSafeZone(medicalZone);
        
        logger.info("✅ Created {} safe zones", 3);
    }
    
    private void startGame() {
        logger.info("\n🚀 Starting game...");
        
        // Start the game
        gameService.startGameAndAssignTargets(game.getGameID());
        
        // Get target assignments
        List<TargetAssignment> assignments = targetAssignmentService.getActiveAssignments(game.getGameID());
        for (TargetAssignment assignment : assignments) {
            activeAssignments.put(assignment.getAssignerId(), assignment);
        }
        
        logger.info("✅ Game started! {} target assignments created", assignments.size());
        
        // Show initial assignments
        for (SimulatedPlayer player : simulatedPlayers) {
            TargetAssignment assignment = activeAssignments.get(player.getPlayer().getPlayerID());
            if (assignment != null) {
                Player target = players.get(assignment.getTargetId());
                logger.info("🎯 {} is hunting {}", player.getPlayer().getPlayerName(), target.getPlayerName());
            }
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
        
        // Schedule random events
        executorService.scheduleAtFixedRate(
            this::simulateRandomEvents,
            30,
            60,
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
            
            // Update location in service
            locationService.updatePlayerLocation(
                player.getPlayer().getPlayerID(),
                newLocation.getLatitude(),
                newLocation.getLongitude(),
                5.0 // GPS accuracy
            );
            
            // Check proximity to target
            checkProximityToTarget(player);
            
            // Broadcast location via WebSocket
            broadcastLocationUpdate(player);
            
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
        
        // Check if either player is in safe zone
        boolean hunterInSafeZone = safeZoneService.isPlayerInActiveSafeZone(
            game.getGameID(), hunter.getPlayer().getPlayerID(),
            hunter.getCurrentLocation().getLatitude(),
            hunter.getCurrentLocation().getLongitude(),
            System.currentTimeMillis()
        );
        boolean targetInSafeZone = safeZoneService.isPlayerInActiveSafeZone(
            game.getGameID(), target.getPlayer().getPlayerID(),
            target.getCurrentLocation().getLatitude(),
            target.getCurrentLocation().getLongitude(),
            System.currentTimeMillis()
        );
        
        if (hunterInSafeZone || targetInSafeZone) {
            logger.debug("Elimination blocked - player in safe zone");
            return;
        }
        
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
        try {
            // Report kill
            Kill kill = killService.reportKill(
                hunter.getPlayer().getPlayerID(),
                target.getPlayer().getPlayerID(),
                hunter.getCurrentLocation().getLatitude(),
                hunter.getCurrentLocation().getLongitude(),
                "GPS", // Verification method
                null   // No photo for simulation
            );
            
            // Verify kill (simulate verification)
            killService.verifyKill(
                kill.getKillerID(),
                kill.getTime(),
                target.getPlayer().getPlayerID(),
                null  // No additional verification data for simulation
            );
            
            // Mark target as eliminated
            target.setAlive(false);
            target.setEliminatedAt(Instant.now());
            hunter.incrementKills();
            
            logger.info("💀 {} eliminated {} at {}", 
                hunter.getPlayer().getPlayerName(),
                target.getPlayer().getPlayerName(),
                formatLocation(hunter.getCurrentLocation())
            );
            
            // Get new target assignment
            List<TargetAssignment> updatedAssignments = targetAssignmentService.getActiveAssignments(game.getGameID());
            for (TargetAssignment updatedAssignment : updatedAssignments) {
                if (updatedAssignment.getAssignerId().equals(hunter.getPlayer().getPlayerID())) {
                    activeAssignments.put(hunter.getPlayer().getPlayerID(), updatedAssignment);
                    Player newTarget = players.get(updatedAssignment.getTargetId());
                    logger.info("🎯 {} now hunting {}", hunter.getPlayer().getPlayerName(), newTarget.getPlayerName());
                    break;
                }
            }
            
            // Check for game winner
            checkForWinner();
            
        } catch (Exception e) {
            logger.error("Error performing elimination: {}", e.getMessage());
        }
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
    
    private void simulateRandomEvents() {
        if (!gameActive) return;
        
        // Random safe zone usage
        for (SimulatedPlayer player : simulatedPlayers) {
            if (!player.isAlive()) continue;
            
            if (Math.random() < 0.1) { // 10% chance to seek safe zone
                Coordinate safeZoneLocation = getSafeZoneLocation();
                player.setTargetLocation(safeZoneLocation);
                logger.debug("{} heading to safe zone", player.getPlayer().getPlayerName());
            }
        }
        
        // Random speed changes (running vs walking)
        for (SimulatedPlayer player : simulatedPlayers) {
            if (!player.isAlive()) continue;
            
            if (Math.random() < 0.2) { // 20% chance to change speed
                player.setSpeed(player.getSpeed() == 1.5 ? 4.0 : 1.5); // Walk vs run
            }
        }
    }
    
    private void logGameStatus() {
        if (!gameActive) return;
        
        long alivePlayers = simulatedPlayers.stream().filter(SimulatedPlayer::isAlive).count();
        long eliminatedPlayers = NUM_PLAYERS - alivePlayers;
        
        logger.info("\n📊 Game Status Update:");
        logger.info("  ⏱️  Time elapsed: {} minutes", 
            ChronoUnit.MINUTES.between(game.getStartTimeEpochMillis() != null ? Instant.ofEpochMilli(game.getStartTimeEpochMillis()) : Instant.now(), Instant.now()));
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
                try {
                    gameService.completeGame(game.getGameID(), winner.getPlayer().getPlayerID());
                } catch (Exception e) {
                    logger.error("Error completing game: {}", e.getMessage());
                }
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
                try {
                    gameService.completeGame(game.getGameID(), winner.getPlayer().getPlayerID());
                } catch (Exception e) {
                    logger.error("Error completing game: {}", e.getMessage());
                }
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
        logger.info("  Total eliminations: {}", NUM_PLAYERS - simulatedPlayers.stream().filter(SimulatedPlayer::isAlive).count());
        logger.info("  Game duration: {}", formatDuration(Duration.between(game.getStartTimeEpochMillis() != null ? Instant.ofEpochMilli(game.getStartTimeEpochMillis()) : Instant.now(), Instant.now())));
        logger.info("  Average survival time: {}", 
            formatDuration(Duration.ofMillis(
                (long) simulatedPlayers.stream()
                    .mapToLong(p -> p.getSurvivalTime().toMillis())
                    .average()
                    .orElse(0)
            ))
        );
    }
    
    private void broadcastLocationUpdate(SimulatedPlayer player) {
        // Simulate WebSocket broadcast
        Map<String, Object> message = new HashMap<>();
        message.put("type", "location_update");
        message.put("playerId", player.getPlayer().getPlayerID());
        message.put("location", Map.of(
            "latitude", player.getCurrentLocation().getLatitude(),
            "longitude", player.getCurrentLocation().getLongitude()
        ));
        message.put("timestamp", Instant.now().toString());
        
        // In real implementation, this would broadcast via WebSocket
        // webSocketServer.broadcast(game.getGameId(), message);
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
    
    private List<Coordinate> createCampusBoundary() {
        // Create a rectangular boundary around campus
        return Arrays.asList(
            new Coordinate(40.7520, -73.9900), // NW corner
            new Coordinate(40.7520, -73.9820), // NE corner
            new Coordinate(40.7450, -73.9820), // SE corner
            new Coordinate(40.7450, -73.9900), // SW corner
            new Coordinate(40.7520, -73.9900)  // Close polygon
        );
    }
    
    private List<Coordinate> createCircularBoundary(double centerLat, double centerLon, double radiusKm) {
        List<Coordinate> boundary = new ArrayList<>();
        int points = 16; // 16-point circle
        
        for (int i = 0; i < points; i++) {
            double angle = (360.0 / points) * i;
            Coordinate point = GeoUtils.calculateDestinationPoint(
                centerLat, centerLon, angle, radiusKm * 1000
            );
            boundary.add(point);
        }
        
        // Close the polygon
        boundary.add(boundary.get(0));
        
        return boundary;
    }
    
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
    
    private Coordinate getSafeZoneLocation() {
        // Return one of the predefined safe zone centers
        double[][] safeZones = {
            {40.7484, -73.9857}, // Library
            {40.7490, -73.9870}, // Cafeteria
            {40.7478, -73.9850}  // Medical
        };
        
        int index = (int) (Math.random() * safeZones.length);
        return new Coordinate(safeZones[index][0], safeZones[index][1]);
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