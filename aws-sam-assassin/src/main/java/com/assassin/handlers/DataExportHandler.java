package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbKillDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.GameDao;
import com.assassin.dao.KillDao;
import com.assassin.dao.PlayerDao;
import com.assassin.model.export.GameStatisticsExport;
import com.assassin.model.export.LocationHeatmapData;
import com.assassin.model.export.PlayerPerformanceExport;
import com.assassin.service.DataExportService;
import com.assassin.util.DataExportFormatter;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Lambda handler for data export API endpoints.
 * Provides RESTful APIs for exporting game data in JSON and CSV formats.
 */
public class DataExportHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(DataExportHandler.class);
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 10000;
    
    private final DataExportService dataExportService;
    private final Gson gson = new Gson();
    
    public DataExportHandler() {
        // Initialize DAOs
        PlayerDao playerDao = new DynamoDbPlayerDao();
        KillDao killDao = new DynamoDbKillDao();
        GameDao gameDao = new DynamoDbGameDao();
        
        this.dataExportService = new DataExportService(gameDao, killDao, playerDao);
    }
    
    // Constructor for dependency injection/testing
    public DataExportHandler(DataExportService dataExportService) {
        this.dataExportService = dataExportService;
    }
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        String httpMethod = input.getHttpMethod();
        String path = input.getPath();
        logger.info("Received {} request for path: {}", httpMethod, path);
        
        try {
            if ("GET".equalsIgnoreCase(httpMethod)) {
                return handleGetRequest(input, path);
            } else {
                logger.warn("Unsupported HTTP method: {}", httpMethod);
                return HandlerUtils.createErrorResponse(405, "Method Not Allowed");
            }
        } catch (RuntimeException e) {
            logger.error("Error processing request: {} {}", httpMethod, path, e);
            return HandlerUtils.createErrorResponse(500, "Internal Server Error: " + e.getMessage());
        }
    }
    
    private APIGatewayProxyResponseEvent handleGetRequest(APIGatewayProxyRequestEvent input, String path) {
        // Route based on path
        if (path.endsWith("/export/games")) {
            return exportGameStatistics(input);
        } else if (path.endsWith("/export/players")) {
            return exportPlayerPerformance(input);
        } else if (path.endsWith("/export/locations")) {
            return exportLocationHeatmap(input);
        } else if (path.endsWith("/export/statistics")) {
            return exportAggregatedStatistics(input);
        } else {
            logger.warn("Unsupported route: {}", path);
            return HandlerUtils.createErrorResponse(404, "Not Found");
        }
    }
    
    /**
     * Handle GET /export/games - Export game statistics
     */
    private APIGatewayProxyResponseEvent exportGameStatistics(APIGatewayProxyRequestEvent input) {
        try {
            Map<String, String> queryParams = input.getQueryStringParameters();
            if (queryParams == null) queryParams = new HashMap<>();
            
            // Parse query parameters
            String startDate = queryParams.get("start_date");
            String endDate = queryParams.get("end_date");
            String gameStatus = queryParams.get("status");
            String format = queryParams.getOrDefault("format", "json").toLowerCase();
            int limit = parseLimit(queryParams.get("limit"));
            
            logger.info("Exporting game statistics: startDate={}, endDate={}, status={}, format={}, limit={}", 
                       startDate, endDate, gameStatus, format, limit);
            
            // Get data from service
            List<GameStatisticsExport> data = dataExportService.exportGameStatistics(
                startDate, endDate, gameStatus, limit);
            
            // Format response based on requested format
            String responseBody;
            String contentType;
            
            if ("csv".equals(format)) {
                responseBody = DataExportFormatter.formatGameStatisticsAsCsv(data);
                contentType = "text/csv";
            } else {
                Map<String, Object> metadata = createMetadata(queryParams);
                responseBody = DataExportFormatter.formatGameStatisticsAsJson(data, metadata);
                contentType = "application/json";
            }
            
            return createExportResponse(responseBody, contentType, format, "game_statistics");
            
        } catch (RuntimeException e) {
            logger.error("Error exporting game statistics", e);
            return HandlerUtils.createErrorResponse(500, "Failed to export game statistics: " + e.getMessage());
        }
    }
    
    /**
     * Handle GET /export/players - Export player performance metrics
     */
    private APIGatewayProxyResponseEvent exportPlayerPerformance(APIGatewayProxyRequestEvent input) {
        try {
            Map<String, String> queryParams = input.getQueryStringParameters();
            if (queryParams == null) queryParams = new HashMap<>();
            
            // Parse query parameters
            String startDate = queryParams.get("start_date");
            String endDate = queryParams.get("end_date");
            String playerIdsParam = queryParams.get("player_ids");
            String format = queryParams.getOrDefault("format", "json").toLowerCase();
            int limit = parseLimit(queryParams.get("limit"));
            
            List<String> playerIds = null;
            if (playerIdsParam != null && !playerIdsParam.trim().isEmpty()) {
                playerIds = Arrays.asList(playerIdsParam.split(","));
            }
            
            logger.info("Exporting player performance: startDate={}, endDate={}, playerIds={}, format={}, limit={}", 
                       startDate, endDate, playerIds != null ? playerIds.size() : "all", format, limit);
            
            // Get data from service
            List<PlayerPerformanceExport> data = dataExportService.exportPlayerPerformance(
                startDate, endDate, playerIds, limit);
            
            // Format response based on requested format
            String responseBody;
            String contentType;
            
            if ("csv".equals(format)) {
                responseBody = DataExportFormatter.formatPlayerPerformanceAsCsv(data);
                contentType = "text/csv";
            } else {
                Map<String, Object> metadata = createMetadata(queryParams);
                responseBody = DataExportFormatter.formatPlayerPerformanceAsJson(data, metadata);
                contentType = "application/json";
            }
            
            return createExportResponse(responseBody, contentType, format, "player_performance");
            
        } catch (RuntimeException e) {
            logger.error("Error exporting player performance", e);
            return HandlerUtils.createErrorResponse(500, "Failed to export player performance: " + e.getMessage());
        }
    }
    
    /**
     * Handle GET /export/locations - Export location heatmap data
     */
    private APIGatewayProxyResponseEvent exportLocationHeatmap(APIGatewayProxyRequestEvent input) {
        try {
            Map<String, String> queryParams = input.getQueryStringParameters();
            if (queryParams == null) queryParams = new HashMap<>();
            
            // Parse query parameters
            String startDate = queryParams.get("start_date");
            String endDate = queryParams.get("end_date");
            String gameId = queryParams.get("game_id");
            String eventType = queryParams.getOrDefault("event_type", "all");
            String format = queryParams.getOrDefault("format", "json").toLowerCase();
            int limit = parseLimit(queryParams.get("limit"));
            
            logger.info("Exporting location heatmap: startDate={}, endDate={}, gameId={}, eventType={}, format={}, limit={}", 
                       startDate, endDate, gameId, eventType, format, limit);
            
            // Get data from service
            List<LocationHeatmapData> data = dataExportService.exportLocationHeatmapData(
                startDate, endDate, gameId, eventType, limit);
            
            // Format response based on requested format
            String responseBody;
            String contentType;
            
            if ("csv".equals(format)) {
                responseBody = DataExportFormatter.formatLocationHeatmapAsCsv(data);
                contentType = "text/csv";
            } else {
                Map<String, Object> metadata = createMetadata(queryParams);
                responseBody = DataExportFormatter.formatLocationHeatmapAsJson(data, metadata);
                contentType = "application/json";
            }
            
            return createExportResponse(responseBody, contentType, format, "location_heatmap");
            
        } catch (RuntimeException e) {
            logger.error("Error exporting location heatmap", e);
            return HandlerUtils.createErrorResponse(500, "Failed to export location heatmap: " + e.getMessage());
        }
    }
    
    /**
     * Handle GET /export/statistics - Export aggregated statistics
     */
    private APIGatewayProxyResponseEvent exportAggregatedStatistics(APIGatewayProxyRequestEvent input) {
        try {
            Map<String, String> queryParams = input.getQueryStringParameters();
            if (queryParams == null) queryParams = new HashMap<>();
            
            // Parse query parameters
            String startDate = queryParams.get("start_date");
            String endDate = queryParams.get("end_date");
            
            logger.info("Exporting aggregated statistics: startDate={}, endDate={}", startDate, endDate);
            
            // Get data from service
            Map<String, Object> statistics = dataExportService.getAggregatedStatistics(startDate, endDate);
            
            // Format response as JSON (aggregated stats are always JSON)
            String responseBody = DataExportFormatter.formatAggregatedStatisticsAsJson(statistics);
            
            return createExportResponse(responseBody, "application/json", "json", "aggregated_statistics");
            
        } catch (RuntimeException e) {
            logger.error("Error exporting aggregated statistics", e);
            return HandlerUtils.createErrorResponse(500, "Failed to export aggregated statistics: " + e.getMessage());
        }
    }
    
    // Helper methods
    
    private int parseLimit(String limitParam) {
        if (limitParam == null || limitParam.trim().isEmpty()) {
            return DEFAULT_LIMIT;
        }
        
        try {
            int limit = Integer.parseInt(limitParam.trim());
            if (limit <= 0) {
                logger.warn("Invalid limit parameter: {}. Using default.", limitParam);
                return DEFAULT_LIMIT;
            }
            if (limit > MAX_LIMIT) {
                logger.warn("Limit parameter {} exceeds maximum {}. Using maximum.", limitParam, MAX_LIMIT);
                return MAX_LIMIT;
            }
            return limit;
        } catch (NumberFormatException e) {
            logger.warn("Invalid number format for limit parameter: {}. Using default.", limitParam);
            return DEFAULT_LIMIT;
        }
    }
    
    private Map<String, Object> createMetadata(Map<String, String> queryParams) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Add query parameters to metadata for reference
        if (queryParams.get("start_date") != null) {
            metadata.put("start_date", queryParams.get("start_date"));
        }
        if (queryParams.get("end_date") != null) {
            metadata.put("end_date", queryParams.get("end_date"));
        }
        if (queryParams.get("status") != null) {
            metadata.put("status_filter", queryParams.get("status"));
        }
        if (queryParams.get("game_id") != null) {
            metadata.put("game_id_filter", queryParams.get("game_id"));
        }
        if (queryParams.get("event_type") != null) {
            metadata.put("event_type_filter", queryParams.get("event_type"));
        }
        if (queryParams.get("limit") != null) {
            metadata.put("limit", queryParams.get("limit"));
        }
        
        return metadata;
    }
    
    private APIGatewayProxyResponseEvent createExportResponse(String body, String contentType, 
                                                            String format, String exportType) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(200);
        response.setBody(body);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", contentType);
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        // Add content disposition header for CSV downloads
        if ("csv".equals(format)) {
            String filename = exportType + "_" + System.currentTimeMillis() + ".csv";
            headers.put("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        }
        
        response.setHeaders(headers);
        return response;
    }
} 