package com.assassin.util;

import com.assassin.model.export.GameStatisticsExport;
import com.assassin.model.export.LocationHeatmapData;
import com.assassin.model.export.PlayerPerformanceExport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Utility class for formatting exported data into various formats (JSON, CSV).
 * Provides standardized formatting with proper metadata and structure.
 */
public class DataExportFormatter {
    
    private static final Logger logger = LoggerFactory.getLogger(DataExportFormatter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    
    /**
     * Format game statistics data as JSON with metadata.
     */
    public static String formatGameStatisticsAsJson(List<GameStatisticsExport> data, 
                                                   Map<String, Object> metadata) {
        try {
            ExportWrapper<GameStatisticsExport> wrapper = new ExportWrapper<>();
            wrapper.setData(data);
            wrapper.setMetadata(addStandardMetadata(metadata, "game_statistics", data.size()));
            
            return objectMapper.writeValueAsString(wrapper);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("JSON processing error formatting game statistics", e);
            throw new RuntimeException("Failed to format game statistics as JSON", e);
        } catch (RuntimeException e) {
            logger.error("Error formatting game statistics as JSON", e);
            throw new RuntimeException("Failed to format game statistics as JSON", e);
        }
    }
    
    /**
     * Format player performance data as JSON with metadata.
     */
    public static String formatPlayerPerformanceAsJson(List<PlayerPerformanceExport> data, 
                                                      Map<String, Object> metadata) {
        try {
            ExportWrapper<PlayerPerformanceExport> wrapper = new ExportWrapper<>();
            wrapper.setData(data);
            wrapper.setMetadata(addStandardMetadata(metadata, "player_performance", data.size()));
            
            return objectMapper.writeValueAsString(wrapper);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("JSON processing error formatting player performance", e);
            throw new RuntimeException("Failed to format player performance as JSON", e);
        } catch (RuntimeException e) {
            logger.error("Error formatting player performance as JSON", e);
            throw new RuntimeException("Failed to format player performance as JSON", e);
        }
    }
    
    /**
     * Format location heatmap data as JSON with metadata.
     */
    public static String formatLocationHeatmapAsJson(List<LocationHeatmapData> data, 
                                                    Map<String, Object> metadata) {
        try {
            ExportWrapper<LocationHeatmapData> wrapper = new ExportWrapper<>();
            wrapper.setData(data);
            wrapper.setMetadata(addStandardMetadata(metadata, "location_heatmap", data.size()));
            
            return objectMapper.writeValueAsString(wrapper);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("JSON processing error formatting location heatmap", e);
            throw new RuntimeException("Failed to format location heatmap as JSON", e);
        } catch (RuntimeException e) {
            logger.error("Error formatting location heatmap as JSON", e);
            throw new RuntimeException("Failed to format location heatmap as JSON", e);
        }
    }
    
    /**
     * Format aggregated statistics as JSON.
     */
    public static String formatAggregatedStatisticsAsJson(Map<String, Object> statistics) {
        try {
            return objectMapper.writeValueAsString(statistics);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("JSON processing error formatting aggregated statistics", e);
            throw new RuntimeException("Failed to format aggregated statistics as JSON", e);
        } catch (RuntimeException e) {
            logger.error("Error formatting aggregated statistics as JSON", e);
            throw new RuntimeException("Failed to format aggregated statistics as JSON", e);
        }
    }
    
    /**
     * Format game statistics data as CSV.
     */
    public static String formatGameStatisticsAsCsv(List<GameStatisticsExport> data) {
        try {
            StringWriter writer = new StringWriter();
            
            // Write CSV header
            writer.append("game_id,game_name,status,created_at,start_time,duration_minutes,")
                  .append("player_count,admin_player_id,map_id,shrinking_zone_enabled,")
                  .append("total_kills,completion_rate\n");
            
            // Write data rows
            for (GameStatisticsExport game : data) {
                writer.append(escapeCsvValue(game.getGameId())).append(",")
                      .append(escapeCsvValue(game.getGameName())).append(",")
                      .append(escapeCsvValue(game.getStatus())).append(",")
                      .append(escapeCsvValue(game.getCreatedAt())).append(",")
                      .append(escapeCsvValue(game.getStartTime())).append(",")
                      .append(formatNumber(game.getDurationMinutes())).append(",")
                      .append(formatNumber(game.getPlayerCount())).append(",")
                      .append(escapeCsvValue(game.getAdminPlayerId())).append(",")
                      .append(escapeCsvValue(game.getMapId())).append(",")
                      .append(formatBoolean(game.getShrinkingZoneEnabled())).append(",")
                      .append(formatNumber(game.getTotalKills())).append(",")
                      .append(formatNumber(game.getCompletionRate())).append("\n");
            }
            
            return writer.toString();
        } catch (RuntimeException e) {
            logger.error("Error formatting game statistics as CSV", e);
            throw new RuntimeException("Failed to format game statistics as CSV", e);
        }
    }
    
    /**
     * Format player performance data as CSV.
     */
    public static String formatPlayerPerformanceAsCsv(List<PlayerPerformanceExport> data) {
        try {
            StringWriter writer = new StringWriter();
            
            // Write CSV header
            writer.append("player_id,status,total_kills,total_deaths,kill_death_ratio,")
                  .append("games_played,average_kills_per_game,recent_kills,accuracy_rate,")
                  .append("average_response_time_minutes,success_rate\n");
            
            // Write data rows
            for (PlayerPerformanceExport player : data) {
                writer.append(escapeCsvValue(player.getPlayerId())).append(",")
                      .append(escapeCsvValue(player.getStatus())).append(",")
                      .append(formatNumber(player.getTotalKills())).append(",")
                      .append(formatNumber(player.getTotalDeaths())).append(",")
                      .append(formatNumber(player.getKillDeathRatio())).append(",")
                      .append(formatNumber(player.getGamesPlayed())).append(",")
                      .append(formatNumber(player.getAverageKillsPerGame())).append(",")
                      .append(formatNumber(player.getRecentKills())).append(",")
                      .append(formatNumber(player.getAccuracyRate())).append(",")
                      .append(formatNumber(player.getAverageResponseTimeMinutes())).append(",")
                      .append(formatNumber(player.getSuccessRate())).append("\n");
            }
            
            return writer.toString();
        } catch (RuntimeException e) {
            logger.error("Error formatting player performance as CSV", e);
            throw new RuntimeException("Failed to format player performance as CSV", e);
        }
    }
    
    /**
     * Format location heatmap data as CSV.
     */
    public static String formatLocationHeatmapAsCsv(List<LocationHeatmapData> data) {
        try {
            StringWriter writer = new StringWriter();
            
            // Write CSV header
            writer.append("latitude,longitude,timestamp,game_id,event_type,intensity,")
                  .append("killer_id,victim_id,verification_status,zone_type\n");
            
            // Write data rows
            for (LocationHeatmapData location : data) {
                writer.append(formatNumber(location.getLatitude())).append(",")
                      .append(formatNumber(location.getLongitude())).append(",")
                      .append(escapeCsvValue(location.getTimestamp())).append(",")
                      .append(escapeCsvValue(location.getGameId())).append(",")
                      .append(escapeCsvValue(location.getEventType())).append(",")
                      .append(formatNumber(location.getIntensity())).append(",")
                      .append(escapeCsvValue(location.getKillerId())).append(",")
                      .append(escapeCsvValue(location.getVictimId())).append(",")
                      .append(escapeCsvValue(location.getVerificationStatus())).append(",")
                      .append(escapeCsvValue(location.getZoneType())).append("\n");
            }
            
            return writer.toString();
        } catch (RuntimeException e) {
            logger.error("Error formatting location heatmap as CSV", e);
            throw new RuntimeException("Failed to format location heatmap as CSV", e);
        }
    }
    
    // Helper methods
    
    private static Map<String, Object> addStandardMetadata(Map<String, Object> metadata, 
                                                          String exportType, int recordCount) {
        if (metadata == null) {
            metadata = new java.util.HashMap<>();
        }
        
        metadata.put("export_type", exportType);
        metadata.put("record_count", recordCount);
        metadata.put("export_timestamp", Instant.now().toString());
        metadata.put("format_version", "1.0");
        
        return metadata;
    }
    
    private static String escapeCsvValue(String value) {
        if (value == null) {
            return "";
        }
        
        // Escape quotes and wrap in quotes if contains comma, quote, or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        
        return value;
    }
    
    private static String formatNumber(Number number) {
        if (number == null) {
            return "";
        }
        
        // Handle special cases for Double
        if (number instanceof Double) {
            Double d = (Double) number;
            if (d.equals(Double.MAX_VALUE)) {
                return "Infinity";
            }
            if (d.isNaN()) {
                return "";
            }
        }
        
        return number.toString();
    }
    
    private static String formatBoolean(Boolean value) {
        if (value == null) {
            return "";
        }
        return value.toString();
    }
    
    /**
     * Wrapper class for JSON exports with metadata.
     */
    public static class ExportWrapper<T> {
        private List<T> data;
        private Map<String, Object> metadata;
        
        public List<T> getData() {
            return data;
        }
        
        public void setData(List<T> data) {
            this.data = data;
        }
        
        public Map<String, Object> getMetadata() {
            return metadata;
        }
        
        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }
} 