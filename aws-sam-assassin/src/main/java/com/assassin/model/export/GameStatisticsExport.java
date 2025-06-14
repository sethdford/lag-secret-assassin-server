package com.assassin.model.export;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data transfer object for exporting game statistics.
 * Contains aggregated game data suitable for external analysis.
 */
public class GameStatisticsExport {
    
    @JsonProperty("game_id")
    private String gameId;
    
    @JsonProperty("game_name")
    private String gameName;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("created_at")
    private String createdAt;
    
    @JsonProperty("start_time")
    private String startTime;
    
    @JsonProperty("duration_minutes")
    private Double durationMinutes;
    
    @JsonProperty("player_count")
    private Integer playerCount;
    
    @JsonProperty("admin_player_id")
    private String adminPlayerId;
    
    @JsonProperty("map_id")
    private String mapId;
    
    @JsonProperty("shrinking_zone_enabled")
    private Boolean shrinkingZoneEnabled;
    
    @JsonProperty("total_kills")
    private Integer totalKills;
    
    @JsonProperty("completion_rate")
    private Double completionRate;
    
    // Default constructor
    public GameStatisticsExport() {}
    
    // Getters and setters
    public String getGameId() {
        return gameId;
    }
    
    public void setGameId(String gameId) {
        this.gameId = gameId;
    }
    
    public String getGameName() {
        return gameName;
    }
    
    public void setGameName(String gameName) {
        this.gameName = gameName;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getStartTime() {
        return startTime;
    }
    
    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }
    
    public Double getDurationMinutes() {
        return durationMinutes;
    }
    
    public void setDurationMinutes(Double durationMinutes) {
        this.durationMinutes = durationMinutes;
    }
    
    public Integer getPlayerCount() {
        return playerCount;
    }
    
    public void setPlayerCount(Integer playerCount) {
        this.playerCount = playerCount;
    }
    
    public String getAdminPlayerId() {
        return adminPlayerId;
    }
    
    public void setAdminPlayerId(String adminPlayerId) {
        this.adminPlayerId = adminPlayerId;
    }
    
    public String getMapId() {
        return mapId;
    }
    
    public void setMapId(String mapId) {
        this.mapId = mapId;
    }
    
    public Boolean getShrinkingZoneEnabled() {
        return shrinkingZoneEnabled;
    }
    
    public void setShrinkingZoneEnabled(Boolean shrinkingZoneEnabled) {
        this.shrinkingZoneEnabled = shrinkingZoneEnabled;
    }
    
    public Integer getTotalKills() {
        return totalKills;
    }
    
    public void setTotalKills(Integer totalKills) {
        this.totalKills = totalKills;
    }
    
    public Double getCompletionRate() {
        return completionRate;
    }
    
    public void setCompletionRate(Double completionRate) {
        this.completionRate = completionRate;
    }
    
    @Override
    public String toString() {
        return "GameStatisticsExport{" +
                "gameId='" + gameId + '\'' +
                ", gameName='" + gameName + '\'' +
                ", status='" + status + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", startTime='" + startTime + '\'' +
                ", durationMinutes=" + durationMinutes +
                ", playerCount=" + playerCount +
                ", adminPlayerId='" + adminPlayerId + '\'' +
                ", mapId='" + mapId + '\'' +
                ", shrinkingZoneEnabled=" + shrinkingZoneEnabled +
                ", totalKills=" + totalKills +
                ", completionRate=" + completionRate +
                '}';
    }
} 