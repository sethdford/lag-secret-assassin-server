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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataExportServiceTest {

    @Mock
    private GameDao gameDao;

    @Mock
    private KillDao killDao;

    @Mock
    private PlayerDao playerDao;

    private DataExportService dataExportService;

    @BeforeEach
    void setUp() {
        dataExportService = new DataExportService(gameDao, killDao, playerDao);
    }

    @Test
    void testExportGameStatistics_AllGames() throws Exception {
        // Arrange
        List<Game> mockGames = Arrays.asList(
            createMockGame("game1", "Test Game 1", "ACTIVE"),
            createMockGame("game2", "Test Game 2", "COMPLETED")
        );
        
        when(gameDao.getAllGames()).thenReturn(mockGames);
        when(killDao.findKillsByGameId("game1")).thenReturn(Arrays.asList(createMockKill()));
        when(killDao.findKillsByGameId("game2")).thenReturn(Arrays.asList(createMockKill(), createMockKill()));

        // Act
        List<GameStatisticsExport> result = dataExportService.exportGameStatistics(null, null, null, 10);

        // Assert
        assertEquals(2, result.size());
        assertEquals("game1", result.get(0).getGameId());
        assertEquals("Test Game 1", result.get(0).getGameName());
        assertEquals("ACTIVE", result.get(0).getStatus());
        assertEquals(1, result.get(0).getTotalKills());
        
        verify(gameDao).getAllGames();
        verify(killDao, times(2)).findKillsByGameId(anyString());
    }

    @Test
    void testExportGameStatistics_FilterByStatus() throws Exception {
        // Arrange
        List<Game> mockGames = Arrays.asList(
            createMockGame("game1", "Active Game", "ACTIVE")
        );
        
        when(gameDao.listGamesByStatus("ACTIVE")).thenReturn(mockGames);
        when(killDao.findKillsByGameId("game1")).thenReturn(Arrays.asList());

        // Act
        List<GameStatisticsExport> result = dataExportService.exportGameStatistics(null, null, "ACTIVE", 10);

        // Assert
        assertEquals(1, result.size());
        assertEquals("ACTIVE", result.get(0).getStatus());
        
        verify(gameDao).listGamesByStatus("ACTIVE");
        verify(gameDao, never()).getAllGames();
    }

    @Test
    void testExportGameStatistics_WithLimit() throws Exception {
        // Arrange
        List<Game> mockGames = Arrays.asList(
            createMockGame("game1", "Game 1", "ACTIVE"),
            createMockGame("game2", "Game 2", "ACTIVE"),
            createMockGame("game3", "Game 3", "ACTIVE")
        );
        
        when(gameDao.getAllGames()).thenReturn(mockGames);
        when(killDao.findKillsByGameId(anyString())).thenReturn(Arrays.asList());

        // Act
        List<GameStatisticsExport> result = dataExportService.exportGameStatistics(null, null, null, 2);

        // Assert
        assertEquals(2, result.size());
        assertEquals("game1", result.get(0).getGameId());
        assertEquals("game2", result.get(1).getGameId());
    }

    @Test
    void testExportPlayerPerformance_AllPlayers() {
        // Arrange
        List<Player> mockPlayers = Arrays.asList(
            createMockPlayer("player1", "ACTIVE"),
            createMockPlayer("player2", "INACTIVE")
        );
        
        when(playerDao.getAllPlayers()).thenReturn(mockPlayers);
        when(gameDao.countGamesPlayedByPlayer("player1")).thenReturn(5);
        when(gameDao.countGamesPlayedByPlayer("player2")).thenReturn(3);
        when(gameDao.countWinsByPlayer("player1")).thenReturn(2);
        when(gameDao.countWinsByPlayer("player2")).thenReturn(1);

        // Act
        List<PlayerPerformanceExport> result = dataExportService.exportPlayerPerformance(null, null, null, 10);

        // Assert
        assertEquals(2, result.size());
        
        PlayerPerformanceExport player1Export = result.get(0);
        assertNotNull(player1Export.getPlayerId());
        assertTrue(player1Export.getPlayerId().startsWith("player_"));
        assertEquals("ACTIVE", player1Export.getStatus());
        assertEquals(10, player1Export.getTotalKills());
        assertEquals(0, player1Export.getTotalDeaths()); // Player model doesn't track deaths directly
        assertEquals(Double.MAX_VALUE, player1Export.getKillDeathRatio()); // 10/0 = MAX_VALUE
        assertEquals(5, player1Export.getGamesPlayed());
        
        verify(playerDao).getAllPlayers();
    }

    @Test
    void testExportPlayerPerformance_SpecificPlayers() {
        // Arrange
        Player mockPlayer = createMockPlayer("player1", "ACTIVE");
        
        when(playerDao.getPlayerById("player1")).thenReturn(Optional.of(mockPlayer));
        when(playerDao.getPlayerById("player2")).thenReturn(Optional.empty());
        when(gameDao.countGamesPlayedByPlayer("player1")).thenReturn(5);
        when(gameDao.countWinsByPlayer("player1")).thenReturn(2);

        // Act
        List<PlayerPerformanceExport> result = dataExportService.exportPlayerPerformance(
            null, null, Arrays.asList("player1", "player2"), 10);

        // Assert
        assertEquals(1, result.size()); // Only player1 found
        assertEquals("ACTIVE", result.get(0).getStatus());
        
        verify(playerDao).getPlayerById("player1");
        verify(playerDao).getPlayerById("player2");
        verify(playerDao, never()).getAllPlayers();
    }

    @Test
    void testExportLocationHeatmapData_AllKills() {
        // Arrange
        List<Kill> mockKills = Arrays.asList(
            createMockKillWithLocation("kill1", "game1", 40.7128, -74.0060),
            createMockKillWithLocation("kill2", "game1", 40.7589, -73.9851)
        );
        
        when(killDao.getAllKills()).thenReturn(mockKills);

        // Act
        List<LocationHeatmapData> result = dataExportService.exportLocationHeatmapData(
            null, null, null, "all", 10);

        // Assert
        assertEquals(2, result.size());
        
        LocationHeatmapData location1 = result.get(0);
        assertEquals(40.7128, location1.getLatitude());
        assertEquals(-74.0060, location1.getLongitude());
        assertEquals("game1", location1.getGameId());
        assertEquals("kill", location1.getEventType());
        assertEquals(1.0, location1.getIntensity());
        
        verify(killDao).getAllKills();
    }

    @Test
    void testExportLocationHeatmapData_FilterByGame() {
        // Arrange
        List<Kill> mockKills = Arrays.asList(
            createMockKillWithLocation("kill1", "game123", 40.7128, -74.0060)
        );
        
        when(killDao.findKillsByGameId("game123")).thenReturn(mockKills);

        // Act
        List<LocationHeatmapData> result = dataExportService.exportLocationHeatmapData(
            null, null, "game123", "kill", 10);

        // Assert
        assertEquals(1, result.size());
        assertEquals("game123", result.get(0).getGameId());
        
        verify(killDao).findKillsByGameId("game123");
        verify(killDao, never()).getAllKills();
    }

    @Test
    void testGetAggregatedStatistics() throws Exception {
        // Arrange
        List<Game> mockGames = Arrays.asList(
            createMockGame("game1", "Game 1", "ACTIVE"),
            createMockGame("game2", "Game 2", "COMPLETED")
        );
        
        List<Player> mockPlayers = Arrays.asList(
            createMockPlayer("player1", "ACTIVE"),
            createMockPlayer("player2", "INACTIVE")
        );
        
        List<Kill> mockKills = Arrays.asList(
            createMockKillWithLocation("kill1", "game1", 40.7128, -74.0060),
            createMockKillWithLocation("kill2", "game1", 40.7128, -74.0060),
            createMockKillWithLocation("kill3", "game2", 40.7589, -73.9851)
        );
        
        when(gameDao.getAllGames()).thenReturn(mockGames);
        when(playerDao.getAllPlayers()).thenReturn(mockPlayers);
        when(killDao.getAllKills()).thenReturn(mockKills);

        // Act
        Map<String, Object> result = dataExportService.getAggregatedStatistics(null, null);

        // Assert
        assertEquals(2, result.get("total_games"));
        assertEquals(2, result.get("total_players"));
        assertEquals(1, result.get("active_players"));
        assertEquals(3, result.get("total_kills"));
        assertEquals(1.5, result.get("average_kills_per_game"));
        
        assertNotNull(result.get("games_by_status"));
        assertNotNull(result.get("most_active_locations"));
        assertNotNull(result.get("generated_at"));
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> gamesByStatus = (Map<String, Integer>) result.get("games_by_status");
        assertEquals(1, gamesByStatus.get("ACTIVE"));
        assertEquals(1, gamesByStatus.get("COMPLETED"));
    }

    @Test
    void testKillDeathRatioCalculation_NoDeaths() {
        // Arrange
        Player mockPlayer = createMockPlayerWithStats("player1", "ACTIVE", 10, 0);
        
        when(playerDao.getAllPlayers()).thenReturn(Arrays.asList(mockPlayer));
        when(gameDao.countGamesPlayedByPlayer("player1")).thenReturn(5);
        when(gameDao.countWinsByPlayer("player1")).thenReturn(2);

        // Act
        List<PlayerPerformanceExport> result = dataExportService.exportPlayerPerformance(null, null, null, 10);

        // Assert
        assertEquals(1, result.size());
        assertEquals(Double.MAX_VALUE, result.get(0).getKillDeathRatio());
    }

    @Test
    void testKillDeathRatioCalculation_NoKills() {
        // Arrange
        Player mockPlayer = createMockPlayerWithStats("player1", "ACTIVE", 0, 0);
        
        when(playerDao.getAllPlayers()).thenReturn(Arrays.asList(mockPlayer));
        when(gameDao.countGamesPlayedByPlayer("player1")).thenReturn(5);
        when(gameDao.countWinsByPlayer("player1")).thenReturn(0);

        // Act
        List<PlayerPerformanceExport> result = dataExportService.exportPlayerPerformance(null, null, null, 10);

        // Assert
        assertEquals(1, result.size());
        assertEquals(0.0, result.get(0).getKillDeathRatio()); // 0 kills = 0.0 ratio
    }

    @Test
    void testServiceException_GameDaoFailure() throws Exception {
        // Arrange
        when(gameDao.getAllGames()).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            dataExportService.exportGameStatistics(null, null, null, 10);
        });
        
        assertEquals("Failed to export game statistics", exception.getMessage());
    }

    @Test
    void testServiceException_PlayerDaoFailure() {
        // Arrange
        when(playerDao.getAllPlayers()).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            dataExportService.exportPlayerPerformance(null, null, null, 10);
        });
        
        assertEquals("Failed to export player performance", exception.getMessage());
    }

    // Helper methods

    private Game createMockGame(String gameId, String gameName, String status) {
        Game game = new Game();
        game.setGameID(gameId);
        game.setGameName(gameName);
        game.setStatus(status);
        game.setCreatedAt("2024-01-01T00:00:00Z");
        game.setStartTimeEpochMillis(1704067200000L); // 2024-01-01T01:00:00Z
        game.setAdminPlayerID("admin123");
        game.setMapId("map1");
        game.setShrinkingZoneEnabled(true);
        game.setPlayerIDs(Arrays.asList("player1", "player2", "player3"));
        return game;
    }

    private Player createMockPlayer(String playerId, String status) {
        return createMockPlayerWithStats(playerId, status, 10, 2);
    }

    private Player createMockPlayerWithStats(String playerId, String status, int kills, int deaths) {
        Player player = new Player();
        player.setPlayerID(playerId);
        player.setStatus(status);
        player.setKillCount(kills); // Player has killCount directly, not through stats
        
        return player;
    }

    private Kill createMockKill() {
        Kill kill = new Kill();
        kill.setKillerID("killer1");
        kill.setVictimID("victim1");
        kill.setTime("2024-01-01T12:00:00Z");
        kill.setGameId("game1");
        kill.setVerificationStatus("VERIFIED");
        return kill;
    }

    private Kill createMockKillWithLocation(String killId, String gameId, double lat, double lng) {
        Kill kill = createMockKill();
        kill.setGameId(gameId);
        kill.setLatitude(lat);
        kill.setLongitude(lng);
        return kill;
    }
} 