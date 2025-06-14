package com.assassin.model.export;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data transfer object for exporting player performance metrics.
 * Contains anonymized player data suitable for external analysis.
 */
public class PlayerPerformanceExport {
    
    @JsonProperty("player_id")
    private String playerId; // Anonymized player ID
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("total_kills")
    private Integer totalKills;
    
    @JsonProperty("total_deaths")
    private Integer totalDeaths;
    
    @JsonProperty("kill_death_ratio")
    private Double killDeathRatio;
    
    @JsonProperty("games_played")
    private Integer gamesPlayed;
    
    @JsonProperty("average_kills_per_game")
    private Double averageKillsPerGame;
    
    @JsonProperty("recent_kills")
    private Integer recentKills;
    
    @JsonProperty("accuracy_rate")
    private Double accuracyRate;
    
    @JsonProperty("average_response_time_minutes")
    private Double averageResponseTimeMinutes;
    
    @JsonProperty("success_rate")
    private Double successRate;
    
    // Default constructor
    public PlayerPerformanceExport() {}
    
    // Getters and setters
    public String getPlayerId() {
        return playerId;
    }
    
    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Integer getTotalKills() {
        return totalKills;
    }
    
    public void setTotalKills(Integer totalKills) {
        this.totalKills = totalKills;
    }
    
    public Integer getTotalDeaths() {
        return totalDeaths;
    }
    
    public void setTotalDeaths(Integer totalDeaths) {
        this.totalDeaths = totalDeaths;
    }
    
    public Double getKillDeathRatio() {
        return killDeathRatio;
    }
    
    public void setKillDeathRatio(Double killDeathRatio) {
        this.killDeathRatio = killDeathRatio;
    }
    
    public Integer getGamesPlayed() {
        return gamesPlayed;
    }
    
    public void setGamesPlayed(Integer gamesPlayed) {
        this.gamesPlayed = gamesPlayed;
    }
    
    public Double getAverageKillsPerGame() {
        return averageKillsPerGame;
    }
    
    public void setAverageKillsPerGame(Double averageKillsPerGame) {
        this.averageKillsPerGame = averageKillsPerGame;
    }
    
    public Integer getRecentKills() {
        return recentKills;
    }
    
    public void setRecentKills(Integer recentKills) {
        this.recentKills = recentKills;
    }
    
    public Double getAccuracyRate() {
        return accuracyRate;
    }
    
    public void setAccuracyRate(Double accuracyRate) {
        this.accuracyRate = accuracyRate;
    }
    
    public Double getAverageResponseTimeMinutes() {
        return averageResponseTimeMinutes;
    }
    
    public void setAverageResponseTimeMinutes(Double averageResponseTimeMinutes) {
        this.averageResponseTimeMinutes = averageResponseTimeMinutes;
    }
    
    public Double getSuccessRate() {
        return successRate;
    }
    
    public void setSuccessRate(Double successRate) {
        this.successRate = successRate;
    }
    
    @Override
    public String toString() {
        return "PlayerPerformanceExport{" +
                "playerId='" + playerId + '\'' +
                ", status='" + status + '\'' +
                ", totalKills=" + totalKills +
                ", totalDeaths=" + totalDeaths +
                ", killDeathRatio=" + killDeathRatio +
                ", gamesPlayed=" + gamesPlayed +
                ", averageKillsPerGame=" + averageKillsPerGame +
                ", recentKills=" + recentKills +
                ", accuracyRate=" + accuracyRate +
                ", averageResponseTimeMinutes=" + averageResponseTimeMinutes +
                ", successRate=" + successRate +
                '}';
    }
} 