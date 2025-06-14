package com.assassin.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.PlayerNotFoundException;
import com.assassin.exception.ValidationException;
import com.assassin.model.Coordinate;
import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbGameZoneStateDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.DynamoDbSafeZoneDao;
import com.assassin.service.LocationService;
import com.assassin.service.MapConfigurationService;
import com.assassin.service.GeospatialQueryService;
import com.assassin.service.ProximityDetectionService;
import com.assassin.service.ShrinkingZoneService;
import com.assassin.util.GsonUtil;
import com.assassin.util.HandlerUtils;
import com.google.gson.Gson;

/**
 * Handles API requests for interactive game map functionality including
 * zone overlays, proximity queries, and heatmap visualization.
 */
public class MapHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(MapHandler.class);
    private static final Gson gson = GsonUtil.getGson();
    
    private final LocationService locationService;
    private final MapConfigurationService mapConfigService;
    private final ProximityDetectionService proximityService;
    private final GeospatialQueryService geospatialQueryService;

    public MapHandler() {
        this.locationService = new LocationService();
        
        // Initialize dependencies for MapConfigurationService
        DynamoDbGameDao gameDao = new DynamoDbGameDao();
        DynamoDbGameZoneStateDao gameZoneStateDao = new DynamoDbGameZoneStateDao();
        DynamoDbSafeZoneDao safeZoneDao = new DynamoDbSafeZoneDao();
        ShrinkingZoneService shrinkingZoneService = new ShrinkingZoneService(gameDao, gameZoneStateDao, new DynamoDbPlayerDao());
        
        this.mapConfigService = new MapConfigurationService(gameDao, gameZoneStateDao, safeZoneDao, shrinkingZoneService);
        this.proximityService = new ProximityDetectionService();
        this.geospatialQueryService = new GeospatialQueryService();
    }

    // Constructor for dependency injection (testing)
    public MapHandler(LocationService locationService, 
                     MapConfigurationService mapConfigService,
                     ProximityDetectionService proximityService,
                     GeospatialQueryService geospatialQueryService) {
        this.locationService = locationService;
        this.mapConfigService = mapConfigService;
        this.proximityService = proximityService;
        this.geospatialQueryService = geospatialQueryService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(HandlerUtils.getResponseHeaders());

        logger.info("MapHandler received request: Method={}, Path={}", httpMethod, path);

        try {
            // Route requests based on path and method
            if ("GET".equals(httpMethod)) {
                if (path.matches("/games/[^/]+/map/config")) {
                    return getMapConfiguration(request, response);
                } else if (path.matches("/games/[^/]+/map/zones")) {
                    return getZoneOverlays(request, response);
                } else if (path.matches("/games/[^/]+/map/heatmap")) {
                    return getActivityHeatmap(request, response);
                } else if (path.matches("/games/[^/]+/map/proximity")) {
                    return getProximityData(request, response);
                } else if (path.matches("/games/[^/]+/map/boundary")) {
                    return getGameBoundary(request, response);
                }
            }
            
            logger.warn("Route not found in MapHandler: {} {}", httpMethod, path);
            return response
                    .withStatusCode(404)
                    .withBody(gson.toJson(Map.of("message", "Route not found")));
                    
        } catch (ValidationException | IllegalArgumentException e) {
            logger.warn("Invalid input processing map request: {}", e.getMessage());
            return response
                    .withStatusCode(400)
                    .withBody(gson.toJson(Map.of("message", "Invalid request data: " + e.getMessage())));
        } catch (PlayerNotFoundException | GameNotFoundException e) {
            logger.warn("Resource not found during map request: {}", e.getMessage());
            return response
                    .withStatusCode(404)
                    .withBody(gson.toJson(Map.of("message", e.getMessage())));
        } catch (RuntimeException e) {
            logger.error("Error processing map request: {}", e.getMessage(), e);
            return response
                    .withStatusCode(500)
                    .withBody(gson.toJson(Map.of("message", "Internal Server Error", "error", e.getClass().getSimpleName())));
        }
    }

    /**
     * GET /games/{gameId}/map/config
     * Returns map configuration including center point, zoom level, and map style settings.
     */
    private APIGatewayProxyResponseEvent getMapConfiguration(APIGatewayProxyRequestEvent request, 
                                                           APIGatewayProxyResponseEvent response) 
            throws GameNotFoundException {
        String gameId = extractGameIdFromPath(request.getPath());
        
        // Get client map configuration from LocationService
        Map<String, Object> mapConfig = locationService.getClientMapConfiguration(gameId);
        
        logger.info("Retrieved map configuration for game: {}", gameId);
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(mapConfig));
    }

    /**
     * GET /games/{gameId}/map/zones
     * Returns zone overlay data including safe zones, danger zones, and shrinking zones.
     */
    private APIGatewayProxyResponseEvent getZoneOverlays(APIGatewayProxyRequestEvent request, 
                                                        APIGatewayProxyResponseEvent response) 
            throws GameNotFoundException {
        String gameId = extractGameIdFromPath(request.getPath());
        
        Map<String, Object> zoneData = new HashMap<>();
        
        // Get game boundary
        List<Coordinate> boundary = mapConfigService.getGameBoundary(gameId);
        zoneData.put("gameBoundary", boundary);
        
        // Get boundary center for map centering
        Coordinate center = mapConfigService.getGameBoundaryCenter(gameId);
        zoneData.put("center", center);
        
        // Add zone type information for frontend styling
        Map<String, String> zoneStyles = new HashMap<>();
        zoneStyles.put("gameBoundary", "#00FF00"); // Green for game boundary
        zoneStyles.put("safeZone", "#0000FF");     // Blue for safe zones
        zoneStyles.put("dangerZone", "#FF0000");   // Red for danger zones
        zoneStyles.put("shrinkingZone", "#FFFF00"); // Yellow for shrinking zone
        zoneData.put("zoneStyles", zoneStyles);
        
        logger.info("Retrieved zone overlays for game: {}", gameId);
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(zoneData));
    }

    /**
     * GET /games/{gameId}/map/heatmap
     * Returns activity heatmap data showing player density without revealing exact positions.
     */
    private APIGatewayProxyResponseEvent getActivityHeatmap(APIGatewayProxyRequestEvent request, 
                                                          APIGatewayProxyResponseEvent response) 
            throws GameNotFoundException {
        String gameId = extractGameIdFromPath(request.getPath());
        
        // Get optional query parameters
        Map<String, String> queryParams = request.getQueryStringParameters();
        double cellSizeMeters = 50.0; // Default cell size
        
        if (queryParams != null && queryParams.containsKey("cellSize")) {
            try {
                cellSizeMeters = Double.parseDouble(queryParams.get("cellSize"));
                // Validate cell size range
                if (cellSizeMeters < 10.0 || cellSizeMeters > 500.0) {
                    throw new ValidationException("Cell size must be between 10 and 500 meters");
                }
            } catch (NumberFormatException e) {
                throw new ValidationException("Invalid cell size parameter");
            }
        }
        
        // Get game boundary for heatmap bounds
        List<Coordinate> boundary = mapConfigService.getGameBoundary(gameId);
        if (boundary.isEmpty()) {
            throw new GameNotFoundException("Game boundary not found for game: " + gameId);
        }
        
        // Calculate bounding box from game boundary
        GeospatialQueryService.BoundingBox bounds = calculateBoundingBox(boundary);
        
        // Calculate grid resolution based on cell size
        int gridResolution = calculateGridResolution(bounds, cellSizeMeters);
        
        // Generate heatmap data using GeospatialQueryService
        GeospatialQueryService.HeatmapData heatmapData = geospatialQueryService.generateActivityHeatmap(gameId, bounds, gridResolution);
        
        Map<String, Object> response_data = new HashMap<>();
        response_data.put("heatmapData", heatmapData.getData());
        response_data.put("bounds", Map.of(
            "southWest", Map.of("lat", bounds.getSouthWest().getLatitude(), "lng", bounds.getSouthWest().getLongitude()),
            "northEast", Map.of("lat", bounds.getNorthEast().getLatitude(), "lng", bounds.getNorthEast().getLongitude())
        ));
        response_data.put("resolution", gridResolution);
        response_data.put("cellSizeMeters", cellSizeMeters);
        response_data.put("timestamp", System.currentTimeMillis());
        
        logger.info("Generated activity heatmap for game: {} with cell size: {}", gameId, cellSizeMeters);
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(response_data));
    }

    /**
     * GET /games/{gameId}/map/proximity
     * Returns proximity data for players within specified radius.
     */
    private APIGatewayProxyResponseEvent getProximityData(APIGatewayProxyRequestEvent request, 
                                                        APIGatewayProxyResponseEvent response) 
            throws GameNotFoundException, PlayerNotFoundException {
        String gameId = extractGameIdFromPath(request.getPath());
        
        // Get authenticated player ID
        String playerId = HandlerUtils.getPlayerIdFromRequest(request)
                .orElseThrow(() -> new ValidationException("Player ID not found in request context"));
        
        // Get optional query parameters
        Map<String, String> queryParams = request.getQueryStringParameters();
        double radiusMeters = 100.0; // Default radius
        
        if (queryParams != null && queryParams.containsKey("radius")) {
            try {
                radiusMeters = Double.parseDouble(queryParams.get("radius"));
                // Validate radius range
                if (radiusMeters < 10.0 || radiusMeters > 1000.0) {
                    throw new ValidationException("Radius must be between 10 and 1000 meters");
                }
            } catch (NumberFormatException e) {
                throw new ValidationException("Invalid radius parameter");
            }
        }
        
        // Get recent proximity results for the player
        Map<String, ProximityDetectionService.ProximityResult> proximityResults = 
            proximityService.getRecentProximityResults(playerId);
        
        // Filter results by radius if needed
        Map<String, Object> filteredResults = new HashMap<>();
        for (Map.Entry<String, ProximityDetectionService.ProximityResult> entry : proximityResults.entrySet()) {
            ProximityDetectionService.ProximityResult result = entry.getValue();
            if (result.getDistance() <= radiusMeters) {
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("distance", result.getDistance());
                resultData.put("isInRange", result.isInRange());
                resultData.put("timestamp", result.getTimestamp());
                filteredResults.put(entry.getKey(), resultData);
            }
        }
        
        Map<String, Object> response_data = new HashMap<>();
        response_data.put("proximityData", filteredResults);
        response_data.put("radiusMeters", radiusMeters);
        response_data.put("playerId", playerId);
        response_data.put("timestamp", System.currentTimeMillis());
        
        logger.info("Retrieved proximity data for player: {} in game: {} with radius: {}", 
                   playerId, gameId, radiusMeters);
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(response_data));
    }

    /**
     * GET /games/{gameId}/map/boundary
     * Returns the game boundary coordinates for map visualization.
     */
    private APIGatewayProxyResponseEvent getGameBoundary(APIGatewayProxyRequestEvent request, 
                                                       APIGatewayProxyResponseEvent response) 
            throws GameNotFoundException {
        String gameId = extractGameIdFromPath(request.getPath());
        
        List<Coordinate> boundary = mapConfigService.getGameBoundary(gameId);
        Coordinate center = mapConfigService.getGameBoundaryCenter(gameId);
        
        Map<String, Object> boundaryData = new HashMap<>();
        boundaryData.put("boundary", boundary);
        boundaryData.put("center", center);
        boundaryData.put("gameId", gameId);
        
        logger.info("Retrieved game boundary for game: {}", gameId);
        return response
                .withStatusCode(200)
                .withBody(gson.toJson(boundaryData));
    }

    /**
     * Extracts game ID from path like "/games/{gameId}/map/..."
     */
    private String extractGameIdFromPath(String path) {
        String[] pathParts = path.split("/");
        if (pathParts.length >= 3 && "games".equals(pathParts[1])) {
            return pathParts[2];
        }
        throw new ValidationException("Invalid path format - game ID not found");
    }

    /**
     * Calculate bounding box from a list of boundary coordinates.
     */
    private GeospatialQueryService.BoundingBox calculateBoundingBox(List<Coordinate> boundary) {
        if (boundary.isEmpty()) {
            throw new ValidationException("Boundary coordinates cannot be empty");
        }
        
        double minLat = boundary.get(0).getLatitude();
        double maxLat = boundary.get(0).getLatitude();
        double minLng = boundary.get(0).getLongitude();
        double maxLng = boundary.get(0).getLongitude();
        
        for (Coordinate coord : boundary) {
            minLat = Math.min(minLat, coord.getLatitude());
            maxLat = Math.max(maxLat, coord.getLatitude());
            minLng = Math.min(minLng, coord.getLongitude());
            maxLng = Math.max(maxLng, coord.getLongitude());
        }
        
        return new GeospatialQueryService.BoundingBox(
            new Coordinate(minLat, minLng),
            new Coordinate(maxLat, maxLng)
        );
    }

    /**
     * Calculate grid resolution based on bounding box and desired cell size.
     */
    private int calculateGridResolution(GeospatialQueryService.BoundingBox bounds, double cellSizeMeters) {
        // Approximate conversion: 1 degree latitude ≈ 111,000 meters
        double latDegrees = bounds.getNorthEast().getLatitude() - bounds.getSouthWest().getLatitude();
        double lngDegrees = bounds.getNorthEast().getLongitude() - bounds.getSouthWest().getLongitude();
        
        // Calculate approximate distance in meters
        double latDistanceMeters = latDegrees * 111000;
        double lngDistanceMeters = lngDegrees * 111000 * Math.cos(Math.toRadians(
            (bounds.getNorthEast().getLatitude() + bounds.getSouthWest().getLatitude()) / 2));
        
        // Use the larger dimension to determine grid resolution
        double maxDistanceMeters = Math.max(latDistanceMeters, lngDistanceMeters);
        int resolution = (int) Math.ceil(maxDistanceMeters / cellSizeMeters);
        
        // Limit resolution to reasonable bounds
        return Math.max(10, Math.min(resolution, 100));
    }
} 