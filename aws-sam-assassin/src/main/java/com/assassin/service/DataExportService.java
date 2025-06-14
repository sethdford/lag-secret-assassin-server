package com.assassin.service;

import com.assassin.dao.GameDao;
import com.assassin.dao.KillDao;
import com.assassin.dao.PlayerDao;
import com.assassin.model.Game;
import com.assassin.model.Kill;
import com.assassin.model.Player;
import com.assassin.model.PlayerStats;
import com.assassin.model.export.GameStatisticsExport;
import com.assassin.model.export.LocationHeatmapData;
import com.assassin.model.export.PlayerPerformanceExport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for exporting game data in various formats.
 * Handles data aggregation, anonymization, and formatting for external analysis.
 */
public class DataExportService {
    
    private static final Logger logger = LoggerFactory.getLogger(DataExportService.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    
    private final GameDao gameDao;
    private final KillDao killDao;
    private final PlayerDao playerDao;
    
    public DataExportService(GameDao gameDao, KillDao killDao, PlayerDao playerDao) {
        this.gameDao = gameDao;
        this.killDao = killDao;
        this.playerDao = playerDao;
    }
    
    /**
     * Export game statistics with optional filtering.
     */
    public List<GameStatisticsExport> exportGameStatistics(String startDate, String endDate, 
                                                          String gameStatus, int limit) {
        logger.info("Exporting game statistics: startDate={}, endDate={}, status={}, limit={}", 
                   startDate, endDate, gameStatus, limit);
        
        try {
            List<Game> games;
            
            // Get games based on status filter
            if (gameStatus != null && !gameStatus.trim().isEmpty()) {
                games = gameDao.listGamesByStatus(gameStatus.toUpperCase());
            } else {
                games = gameDao.getAllGames();
            }
            
            // Filter by date range if provided
            if (startDate != null || endDate != null) {
                games = filterGamesByDateRange(games, startDate, endDate);
            }
            
            // Apply limit
            if (limit > 0 && games.size() > limit) {
                games = games.subList(0, limit);
            }
            
            // Convert to export format
            List<GameStatisticsExport> exports = new ArrayList<>();
            for (Game game : games) {
                GameStatisticsExport export = convertGameToExport(game);
                exports.add(export);
            }
            
            logger.info("Exported {} game statistics records", exports.size());
            return exports;
            
        } catch (RuntimeException e) {
            logger.error("Error exporting game statistics", e);
            throw new RuntimeException("Failed to export game statistics", e);
        }
    }
    
    /**
     * Export player performance metrics with optional filtering.
     */
    public List<PlayerPerformanceExport> exportPlayerPerformance(String startDate, String endDate, 
                                                                List<String> playerIds, int limit) {
        logger.info("Exporting player performance: startDate={}, endDate={}, playerIds={}, limit={}", 
                   startDate, endDate, playerIds != null ? playerIds.size() : "all", limit);
        
        try {
            List<Player> players;
            
            // Get players based on filter
            if (playerIds != null && !playerIds.isEmpty()) {
                players = new ArrayList<>();
                for (String playerId : playerIds) {
                    Optional<Player> player = playerDao.getPlayerById(playerId);
                    player.ifPresent(players::add);
                }
            } else {
                players = playerDao.getAllPlayers();
            }
            
            // Apply limit
            if (limit > 0 && players.size() > limit) {
                players = players.subList(0, limit);
            }
            
            // Convert to export format with performance calculations
            List<PlayerPerformanceExport> exports = new ArrayList<>();
            for (Player player : players) {
                PlayerPerformanceExport export = convertPlayerToExport(player, startDate, endDate);
                exports.add(export);
            }
            
            logger.info("Exported {} player performance records", exports.size());
            return exports;
            
        } catch (RuntimeException e) {
            logger.error("Error exporting player performance", e);
            throw new RuntimeException("Failed to export player performance", e);
        }
    }
    
    /**
     * Export location heatmap data for visualization.
     */
    public List<LocationHeatmapData> exportLocationHeatmapData(String startDate, String endDate, 
                                                              String gameId, String eventType, int limit) {
        logger.info("Exporting location heatmap: startDate={}, endDate={}, gameId={}, eventType={}, limit={}", 
                   startDate, endDate, gameId, eventType, limit);
        
        try {
            List<Kill> kills;
            
            // Get kills based on game filter
            if (gameId != null && !gameId.trim().isEmpty()) {
                kills = killDao.findKillsByGameId(gameId);
            } else {
                kills = killDao.getAllKills();
            }
            
            // Filter by date range if provided
            if (startDate != null || endDate != null) {
                kills = filterKillsByDateRange(kills, startDate, endDate);
            }
            
            // Apply limit
            if (limit > 0 && kills.size() > limit) {
                kills = kills.subList(0, limit);
            }
            
            // Convert to location heatmap data
            List<LocationHeatmapData> heatmapData = new ArrayList<>();
            for (Kill kill : kills) {
                LocationHeatmapData data = convertKillToLocationData(kill, eventType);
                if (data != null) {
                    heatmapData.add(data);
                }
            }
            
            logger.info("Exported {} location heatmap records", heatmapData.size());
            return heatmapData;
            
        } catch (RuntimeException e) {
            logger.error("Error exporting location heatmap data", e);
            throw new RuntimeException("Failed to export location heatmap data", e);
        }
    }
    
    /**
     * Get aggregated statistics across all data.
     */
    public Map<String, Object> getAggregatedStatistics(String startDate, String endDate) {
        logger.info("Generating aggregated statistics: startDate={}, endDate={}", startDate, endDate);
        
        try {
            Map<String, Object> statistics = new HashMap<>();
            
            // Game statistics
            List<Game> allGames = gameDao.getAllGames();
            if (startDate != null || endDate != null) {
                allGames = filterGamesByDateRange(allGames, startDate, endDate);
            }
            
            statistics.put("total_games", allGames.size());
            statistics.put("games_by_status", getGamesByStatus(allGames));
            statistics.put("average_game_duration", calculateAverageGameDuration(allGames));
            
            // Player statistics
            List<Player> allPlayers = playerDao.getAllPlayers();
            statistics.put("total_players", allPlayers.size());
            statistics.put("active_players", countActivePlayers(allPlayers));
            
            // Kill statistics
            List<Kill> allKills = killDao.getAllKills();
            if (startDate != null || endDate != null) {
                allKills = filterKillsByDateRange(allKills, startDate, endDate);
            }
            
            statistics.put("total_kills", allKills.size());
            statistics.put("average_kills_per_game", calculateAverageKillsPerGame(allKills, allGames));
            statistics.put("most_active_locations", getMostActiveLocations(allKills));
            
            // Metadata
            statistics.put("generated_at", Instant.now().toString());
            statistics.put("date_range", Map.of(
                "start_date", startDate != null ? startDate : "all_time",
                "end_date", endDate != null ? endDate : "all_time"
            ));
            
            logger.info("Generated aggregated statistics with {} metrics", statistics.size());
            return statistics;
            
        } catch (RuntimeException e) {
            logger.error("Error generating aggregated statistics", e);
            throw new RuntimeException("Failed to generate aggregated statistics", e);
        }
    }
    
    // Helper methods for data conversion
    
    private GameStatisticsExport convertGameToExport(Game game) {
        GameStatisticsExport export = new GameStatisticsExport();
        
        export.setGameId(game.getGameID());
        export.setGameName(game.getGameName());
        export.setStatus(game.getStatus());
        export.setCreatedAt(game.getCreatedAt());
        export.setStartTime(game.getStartTimeEpochMillis() != null ? 
            Instant.ofEpochMilli(game.getStartTimeEpochMillis()).toString() : null);
        export.setAdminPlayerId(anonymizePlayerId(game.getAdminPlayerID()));
        export.setMapId(game.getMapId());
        export.setShrinkingZoneEnabled(game.getShrinkingZoneEnabled());
        
        // Calculate derived metrics
        export.setDurationMinutes(calculateGameDuration(game));
        export.setPlayerCount(game.getPlayerIDs() != null ? game.getPlayerIDs().size() : 0);
        export.setTotalKills(calculateTotalKillsForGame(game.getGameID()));
        export.setCompletionRate(calculateCompletionRate(game));
        
        return export;
    }
    
    private PlayerPerformanceExport convertPlayerToExport(Player player, String startDate, String endDate) {
        PlayerPerformanceExport export = new PlayerPerformanceExport();
        
        export.setPlayerId(anonymizePlayerId(player.getPlayerID()));
        export.setStatus(player.getStatus());
        
        // Get player stats from Player model directly
        Integer totalKills = player.getKillCount();
        Integer totalDeaths = 0; // Player model doesn't track deaths directly
        
        export.setTotalKills(totalKills != null ? totalKills : 0);
        export.setTotalDeaths(totalDeaths);
        export.setRecentKills(totalKills != null ? totalKills : 0); // Use total kills as recent kills
        export.setGamesPlayed(calculateGamesPlayed(player.getPlayerID()));
        
        // Calculate derived metrics
        export.setKillDeathRatio(calculateKillDeathRatio(totalKills, totalDeaths));
        export.setAverageKillsPerGame(calculateAverageKillsPerGame(totalKills, export.getGamesPlayed()));
        export.setAccuracyRate(calculateAccuracyRate(player.getPlayerID()));
        export.setAverageResponseTimeMinutes(calculateAverageResponseTime(player.getPlayerID()));
        export.setSuccessRate(calculateSuccessRate(player.getPlayerID()));
        
        return export;
    }
    
    private LocationHeatmapData convertKillToLocationData(Kill kill, String eventTypeFilter) {
        // Skip if event type filter doesn't match
        if (!"all".equals(eventTypeFilter) && !"kill".equals(eventTypeFilter)) {
            return null;
        }
        
        LocationHeatmapData data = new LocationHeatmapData();
        
        data.setLatitude(kill.getLatitude());
        data.setLongitude(kill.getLongitude());
        data.setTimestamp(kill.getTime());
        data.setGameId(kill.getGameId());
        data.setEventType("kill");
        data.setIntensity(1.0); // Base intensity for kills
        data.setKillerId(anonymizePlayerId(kill.getKillerID()));
        data.setVictimId(anonymizePlayerId(kill.getVictimID()));
        data.setVerificationStatus(kill.getVerificationStatus());
        data.setZoneType("normal"); // Default zone type
        
        return data;
    }
    
    // Helper methods for calculations
    
    private String anonymizePlayerId(String playerId) {
        if (playerId == null) return null;
        // Simple anonymization - hash the player ID
        return "player_" + Math.abs(playerId.hashCode());
    }
    
    private Double calculateGameDuration(Game game) {
        if (game.getStartTimeEpochMillis() == null || game.getCreatedAt() == null) {
            return null;
        }
        
        try {
            Long startTimeMillis = game.getStartTimeEpochMillis();
            Instant created = Instant.parse(game.getCreatedAt());
            return (double) (startTimeMillis - created.toEpochMilli()) / (1000.0 * 60.0); // minutes
        } catch (RuntimeException e) {
            logger.warn("Error calculating game duration for game {}", game.getGameID(), e);
            return null;
        }
    }
    
    private Integer calculateTotalKillsForGame(String gameId) {
        try {
            List<Kill> gameKills = killDao.findKillsByGameId(gameId);
            return gameKills.size();
        } catch (RuntimeException e) {
            logger.warn("Error calculating total kills for game {}", gameId, e);
            return 0;
        }
    }
    
    private Double calculateCompletionRate(Game game) {
        if (!"COMPLETED".equals(game.getStatus())) {
            return 0.0;
        }
        return 100.0; // Completed games have 100% completion rate
    }
    
    private Integer calculateGamesPlayed(String playerId) {
        try {
            return gameDao.countGamesPlayedByPlayer(playerId);
        } catch (RuntimeException e) {
            logger.warn("Error calculating games played for player {}", playerId, e);
            return 0;
        }
    }
    
    private Double calculateKillDeathRatio(Integer kills, Integer deaths) {
        if (deaths == null || deaths == 0) {
            return kills != null && kills > 0 ? Double.MAX_VALUE : 0.0;
        }
        if (kills == null) {
            return 0.0;
        }
        return (double) kills / deaths;
    }
    
    private Double calculateAverageKillsPerGame(Integer totalKills, Integer gamesPlayed) {
        if (gamesPlayed == null || gamesPlayed == 0) {
            return 0.0;
        }
        if (totalKills == null) {
            return 0.0;
        }
        return (double) totalKills / gamesPlayed;
    }
    
    private Double calculateAccuracyRate(String playerId) {
        // Placeholder - would need additional data to calculate actual accuracy
        return 85.0; // Default accuracy rate
    }
    
    private Double calculateAverageResponseTime(String playerId) {
        // Placeholder - would need kill timing data to calculate actual response time
        return 15.0; // Default response time in minutes
    }
    
    private Double calculateSuccessRate(String playerId) {
        try {
            int gamesPlayed = gameDao.countGamesPlayedByPlayer(playerId);
            int wins = gameDao.countWinsByPlayer(playerId);
            
            if (gamesPlayed == 0) {
                return 0.0;
            }
            
            return ((double) wins / gamesPlayed) * 100.0;
        } catch (RuntimeException e) {
            logger.warn("Error calculating success rate for player {}", playerId, e);
            return 0.0;
        }
    }
    
    // Helper methods for filtering
    
    private List<Game> filterGamesByDateRange(List<Game> games, String startDate, String endDate) {
        return games.stream()
                   .filter(game -> isWithinDateRange(game.getCreatedAt(), startDate, endDate))
                   .collect(Collectors.toList());
    }
    
    private List<Kill> filterKillsByDateRange(List<Kill> kills, String startDate, String endDate) {
        return kills.stream()
                   .filter(kill -> isWithinDateRange(kill.getTime(), startDate, endDate))
                   .collect(Collectors.toList());
    }
    
    private boolean isWithinDateRange(String timestamp, String startDate, String endDate) {
        if (timestamp == null) return false;
        
        try {
            Instant instant = Instant.parse(timestamp);
            
            if (startDate != null) {
                Instant start = Instant.parse(startDate);
                if (instant.isBefore(start)) {
                    return false;
                }
            }
            
            if (endDate != null) {
                Instant end = Instant.parse(endDate);
                if (instant.isAfter(end)) {
                    return false;
                }
            }
            
            return true;
        } catch (RuntimeException e) {
            logger.warn("Error parsing timestamp for date range filter: {}", timestamp, e);
            return false;
        }
    }
    
    // Helper methods for aggregated statistics
    
    private Map<String, Integer> getGamesByStatus(List<Game> games) {
        return games.stream()
                   .collect(Collectors.groupingBy(
                       Game::getStatus,
                       Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                   ));
    }
    
    private Double calculateAverageGameDuration(List<Game> games) {
        List<Double> durations = games.stream()
                                     .map(this::calculateGameDuration)
                                     .filter(Objects::nonNull)
                                     .collect(Collectors.toList());
        
        if (durations.isEmpty()) {
            return 0.0;
        }
        
        return durations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
    
    private Integer countActivePlayers(List<Player> players) {
        return (int) players.stream()
                           .filter(player -> "ACTIVE".equals(player.getStatus()))
                           .count();
    }
    
    private Double calculateAverageKillsPerGame(List<Kill> kills, List<Game> games) {
        if (games.isEmpty()) {
            return 0.0;
        }
        return (double) kills.size() / games.size();
    }
    
    private List<Map<String, Object>> getMostActiveLocations(List<Kill> kills) {
        // Group kills by approximate location (rounded to 3 decimal places)
        Map<String, Long> locationCounts = kills.stream()
                .filter(kill -> kill.getLatitude() != null && kill.getLongitude() != null)
                .collect(Collectors.groupingBy(
                    kill -> String.format("%.3f,%.3f", kill.getLatitude(), kill.getLongitude()),
                    Collectors.counting()
                ));
        
        // Return top 10 most active locations
        return locationCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    String[] coords = entry.getKey().split(",");
                    Map<String, Object> location = new HashMap<>();
                    location.put("latitude", Double.parseDouble(coords[0]));
                    location.put("longitude", Double.parseDouble(coords[1]));
                    location.put("kill_count", entry.getValue());
                    return location;
                })
                .collect(Collectors.toList());
    }
} 