package com.assassin.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.config.MapConfiguration;
import com.assassin.dao.GameDao;
import com.assassin.dao.GameZoneStateDao;
import com.assassin.dao.SafeZoneDao;
import com.assassin.exception.ConfigurationNotFoundException;
import com.assassin.exception.GameNotFoundException;
import com.assassin.exception.PersistenceException;
import com.assassin.exception.GameStateException;
import com.assassin.model.Coordinate;
import com.assassin.model.Game;
import com.assassin.util.DynamoDbClientProvider;
import com.assassin.util.GeoUtils;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/**
 * Service for managing map configurations and game boundaries.
 * Provides methods for retrieving and validating map configurations for different game modes.
 */
public class MapConfigurationService {
    private static final Logger logger = LoggerFactory.getLogger(MapConfigurationService.class);
    public static final String MAP_CONFIG_TABLE_ENV_VAR = "MAP_CONFIG_TABLE_NAME";
    public static final String DEFAULT_MAP_CONFIG_TABLE_NAME = "dev-MapConfigurations";
    public static final String DEFAULT_MAP_ID = "default_map";

    private final GameDao gameDao;
    private final GameZoneStateDao gameZoneStateDao;
    private final SafeZoneDao safeZoneDao;
    private final SafeZoneService safeZoneService;
    private final ShrinkingZoneService shrinkingZoneService;
    private final DynamoDbTable<MapConfiguration> mapConfigTable;

    private final Map<String, MapConfiguration> mapConfigCache;
    private final Map<String, List<Coordinate>> gameBoundaryCache;
    private final Map<String, MapConfiguration> effectiveMapConfigCache;

    private static final List<Coordinate> DEFAULT_GAME_BOUNDARY;

    private static final double MAX_MAP_SIZE_METERS = 5000.0;

    static {
        List<Coordinate> defaultBoundary = new ArrayList<>();
        defaultBoundary.add(new Coordinate(37.808, -122.409));
        defaultBoundary.add(new Coordinate(37.808, -122.347));
        defaultBoundary.add(new Coordinate(37.735, -122.347));
        defaultBoundary.add(new Coordinate(37.735, -122.409));
        DEFAULT_GAME_BOUNDARY = Collections.unmodifiableList(defaultBoundary);
    }

    // Constructor for testing with mocked enhanced client
    public MapConfigurationService(
            GameDao gameDao,
            GameZoneStateDao gameZoneStateDao,
            SafeZoneDao safeZoneDao,
            ShrinkingZoneService shrinkingZoneService,
            DynamoDbEnhancedClient enhancedClient) {
        this.gameDao = gameDao;
        this.gameZoneStateDao = gameZoneStateDao;
        this.safeZoneDao = safeZoneDao;
        this.shrinkingZoneService = shrinkingZoneService;
        this.gameBoundaryCache = new ConcurrentHashMap<>();
        this.mapConfigCache = new ConcurrentHashMap<>();
        this.effectiveMapConfigCache = new ConcurrentHashMap<>();
        this.safeZoneService = new SafeZoneService(safeZoneDao); // Or mock if preferred for pure unit test

        String mapConfigTableName = getTableName(MAP_CONFIG_TABLE_ENV_VAR, DEFAULT_MAP_CONFIG_TABLE_NAME);
        this.mapConfigTable = enhancedClient.table(mapConfigTableName, TableSchema.fromBean(MapConfiguration.class));
        logger.info("Initialized MapConfigurationService with (potentially mocked) MapConfiguration table: {}", mapConfigTableName);
    }

    // Original constructor for production use
    public MapConfigurationService(
            GameDao gameDao,
            GameZoneStateDao gameZoneStateDao,
            SafeZoneDao safeZoneDao,
            ShrinkingZoneService shrinkingZoneService) {
        this.gameDao = gameDao;
        this.gameZoneStateDao = gameZoneStateDao;
        this.safeZoneDao = safeZoneDao;
        this.shrinkingZoneService = shrinkingZoneService;
        this.gameBoundaryCache = new ConcurrentHashMap<>();
        this.mapConfigCache = new ConcurrentHashMap<>();
        this.effectiveMapConfigCache = new ConcurrentHashMap<>();

        this.safeZoneService = new SafeZoneService(safeZoneDao);

        DynamoDbEnhancedClient enhancedClient = DynamoDbClientProvider.getDynamoDbEnhancedClient();
        String mapConfigTableName = getTableName(MAP_CONFIG_TABLE_ENV_VAR, DEFAULT_MAP_CONFIG_TABLE_NAME);
        this.mapConfigTable = enhancedClient.table(mapConfigTableName, TableSchema.fromBean(MapConfiguration.class));
        logger.info("Initialized MapConfigurationService with MapConfiguration table: {}", mapConfigTableName);
    }

    private String getTableName(String envVarName, String defaultName) {
        String tableName = System.getProperty(envVarName);
        if (tableName == null || tableName.isEmpty()) {
            tableName = System.getenv(envVarName);
        }
        if (tableName == null || tableName.isEmpty()) {
            logger.warn("'{}' system property or environment variable not set, using default '{}'", envVarName, defaultName);
            tableName = defaultName;
        } else {
            logger.info("Using table name '{}' from system/environment variable '{}'", tableName, envVarName);
        }
        return tableName;
    }

    public List<Coordinate> getGameBoundary(String gameId) {
        try {
            MapConfiguration mapConfig = getEffectiveMapConfiguration(gameId);
            List<Coordinate> boundary = mapConfig.getGameBoundary();
            if (boundary != null && boundary.size() >= 3) {
                return boundary;
            } else {
                logger.warn("Map configuration '{}' for game '{}' has invalid boundary, using default.", mapConfig.getMapId(), gameId);
                return DEFAULT_GAME_BOUNDARY;
            }
        } catch (ConfigurationNotFoundException e) {
            logger.error("Could not retrieve map configuration for game {}: {}. Using default boundary.", gameId, e.getMessage());
            return DEFAULT_GAME_BOUNDARY;
        } catch (Exception e) {
            logger.error("Unexpected error retrieving game boundary for game {}: {}. Using default boundary.", gameId, e.getMessage(), e);
            return DEFAULT_GAME_BOUNDARY;
        }
    }

    public boolean isCoordinateInGameBoundary(String gameId, Coordinate coordinate) {
        if (coordinate == null) {
            return false;
        }
        
        List<Coordinate> boundary = getGameBoundary(gameId);
        return GeoUtils.isPointInBoundary(coordinate, boundary);
    }

    private boolean isCoordinateInActiveZone(String gameId, Coordinate coordinate) {
        try {
            Optional<Coordinate> centerOpt = shrinkingZoneService.getCurrentZoneCenter(gameId);
            Optional<Double> radiusOpt = shrinkingZoneService.getCurrentZoneRadius(gameId);
            
            if (centerOpt.isEmpty() || radiusOpt.isEmpty()) {
                logger.debug("No active zone found for game {}", gameId);
                return false;
            }
            
            Coordinate center = centerOpt.get();
            double radiusMeters = radiusOpt.get();
            
            double distanceMeters = GeoUtils.calculateDistance(
                coordinate.getLatitude(), coordinate.getLongitude(),
                center.getLatitude(), center.getLongitude());
                
            boolean inZone = distanceMeters <= radiusMeters;
            if (!inZone) {
                logger.debug("Coordinate is outside active zone for game {}. Distance: {}, Zone radius: {}", 
                    gameId, distanceMeters, radiusMeters);
            }
            
            return inZone;
        } catch (GameNotFoundException | GameStateException e) {
            logger.error("Error checking if coordinate is in active zone for game {}: {}", gameId, e.getMessage());
            return false;
        }
    }

    public boolean validateCoordinate(String gameId, Coordinate coordinate) {
        if (coordinate == null) {
            return false;
        }
        
        if (!GeoUtils.isValidCoordinate(coordinate.getLatitude(), coordinate.getLongitude())) {
            logger.warn("Invalid coordinate values for game {}: {}", gameId, coordinate);
            return false;
        }

        boolean inGameBoundary = isCoordinateInGameBoundary(gameId, coordinate);
        
        try {
            Optional<Game> gameOpt = gameDao.getGameById(gameId);
            if (gameOpt.isPresent() && "ACTIVE".equals(gameOpt.get().getStatus())) {
                Game game = gameOpt.get();
                
                if (Boolean.TRUE.equals(game.getShrinkingZoneEnabled())) {
                    return isCoordinateInActiveZone(gameId, coordinate);
                }
            }
        } catch (Exception e) {
            logger.error("Error validating coordinate for game {}: {}", gameId, e.getMessage());
        }
        
        return inGameBoundary;
    }

    public void clearBoundaryCache(String gameId) {
        if (gameId == null) {
            gameBoundaryCache.clear();
            logger.debug("Cleared all game boundary caches");
        } else {
            gameBoundaryCache.remove(gameId);
            logger.debug("Cleared boundary cache for game {}", gameId);
        }
        clearMapConfigurationCache(gameId);
    }

    public void clearMapConfigurationCache(String mapId) {
        if (mapId == null) {
            mapConfigCache.clear();
            effectiveMapConfigCache.clear();
            logger.debug("Cleared all map configuration caches and effective map configuration caches");
        } else {
            mapConfigCache.remove(mapId);
            logger.debug("Cleared map configuration cache for mapId {}", mapId);
        }
    }

    public double getMaxMapSizeMeters() {
        return MAX_MAP_SIZE_METERS;
    }

    public Coordinate getGameBoundaryCenter(String gameId) {
        List<Coordinate> boundary = getGameBoundary(gameId);
        if (boundary == null || boundary.size() < 3) {
            logger.warn("Cannot calculate center for invalid boundary for game {}", gameId);
            return null;
        }
        return GeoUtils.calculateCentroid(boundary);
    }

    public boolean isLocationInSafeZone(String gameId, String playerId, Coordinate location, long currentTimeMillis) {
        if (gameId == null || playerId == null || location == null) {
            logger.warn("Cannot check safe zone: null gameId, playerId, or location");
            return false;
        }

        // Boundary check can remain here if it's a general pre-condition
        // before more specific safe zone logic.
        List<Coordinate> boundary = getGameBoundary(gameId);
        if (!GeoUtils.isPointInBoundary(location, boundary)) {
            // Optional buffer check
            double buffer = 0.00001; // Small latitude/longitude buffer
            Coordinate bufferedLocN = new Coordinate(location.getLatitude() + buffer, location.getLongitude());
            Coordinate bufferedLocS = new Coordinate(location.getLatitude() - buffer, location.getLongitude());
            Coordinate bufferedLocE = new Coordinate(location.getLatitude(), location.getLongitude() + buffer);
            Coordinate bufferedLocW = new Coordinate(location.getLatitude(), location.getLongitude() - buffer);

            if (!(GeoUtils.isPointInBoundary(bufferedLocN, boundary) ||
                  GeoUtils.isPointInBoundary(bufferedLocS, boundary) ||
                  GeoUtils.isPointInBoundary(bufferedLocE, boundary) ||
                  GeoUtils.isPointInBoundary(bufferedLocW, boundary) ||
                  GeoUtils.isPointInBoundary(location, boundary)))
            {
               logger.debug("Location is outside game boundary (checked with buffer), not in safe zone: {}", location);
               return false;
            }
        }

        // Delegate to SafeZoneService
        try {
            return safeZoneService.isPlayerInActiveSafeZone(gameId, playerId, location.getLatitude(), location.getLongitude(), currentTimeMillis);
        } catch (PersistenceException e) {
            logger.error("PersistenceException while checking safe zone status for player {} in game {}: {}", playerId, gameId, e.getMessage(), e);
            // Depending on desired behavior, you might re-throw, or return true/false (e.g., fail-safe or fail-open)
            // For now, let's assume if we can't check, the player is NOT in a safe zone (fail-open for kill attempts)
            return false; 
        }
    }

    public com.assassin.config.MapConfiguration getEffectiveMapConfiguration(String gameId)
            throws ConfigurationNotFoundException {
        if (gameId == null || gameId.isEmpty()) {
            logger.warn("getEffectiveMapConfiguration called with null or empty gameId. Returning default map configuration.");
            return getMapConfigurationById(DEFAULT_MAP_ID);
        }

        MapConfiguration cachedConfig = effectiveMapConfigCache.get(gameId);
        if (cachedConfig != null) {
            logger.debug("Returning effective map configuration from cache for gameId: {}", gameId);
            return cachedConfig;
        }

        Optional<Game> gameOptional = gameDao.getGameById(gameId);

        String mapIdToLoad;
        if (gameOptional.isPresent()) {
            Game game = gameOptional.get();
            if (game.getMapId() != null && !game.getMapId().trim().isEmpty()) {
                mapIdToLoad = game.getMapId();
            } else {
                logger.info("Game '{}' has no specific mapId, using default mapId: {}", gameId, DEFAULT_MAP_ID);
                mapIdToLoad = DEFAULT_MAP_ID;
            }
        } else {
            logger.info("Game '{}' not found, using default map configuration with mapId: {}", gameId, DEFAULT_MAP_ID);
            mapIdToLoad = DEFAULT_MAP_ID;
        }

        MapConfiguration resultingConfig;
        try {
            resultingConfig = getMapConfigurationById(mapIdToLoad);
        } catch (ConfigurationNotFoundException e) {
            if (!DEFAULT_MAP_ID.equals(mapIdToLoad)) {
                logger.warn("Specific map '{}' not found for game '{}'. Falling back to default map '{}'. Error: {}",
                            mapIdToLoad, gameId, DEFAULT_MAP_ID, e.getMessage());
                resultingConfig = getMapConfigurationById(DEFAULT_MAP_ID);
            } else {
                logger.error("Default map configuration '{}' not found directly or as fallback for game '{}'.", DEFAULT_MAP_ID, gameId);
                throw e;
            }
        }
        
        effectiveMapConfigCache.put(gameId, resultingConfig);
        return resultingConfig;
    }

    private MapConfiguration fetchMapConfigurationFromDb(String mapId) throws ConfigurationNotFoundException {
        if (mapId == null || mapId.isEmpty()) {
            throw new IllegalArgumentException("mapId cannot be null or empty for DB fetch");
        }
        logger.debug("Fetching MapConfiguration from DB for mapId: {}", mapId);
        try {
            Key key = Key.builder().partitionValue(mapId).build();
            MapConfiguration config = mapConfigTable.getItem(key);
            if (config == null) {
                throw new ConfigurationNotFoundException("MapConfiguration not found in DB for mapId: " + mapId);
            }
            logger.info("Successfully fetched MapConfiguration from DB for mapId: {}", mapId);
            return config;
        } catch (Exception e) {
            logger.error("DynamoDB error fetching MapConfiguration for mapId {}: {}", mapId, e.getMessage(), e);
            throw new ConfigurationNotFoundException("Error retrieving MapConfiguration from DB for mapId: " + mapId, e);
        }
    }

    public MapConfiguration getMapConfigurationById(String mapId) throws ConfigurationNotFoundException {
         if (mapId == null || mapId.isEmpty()) {
            throw new IllegalArgumentException("mapId cannot be null or empty");
        }
         MapConfiguration config = mapConfigCache.computeIfAbsent(mapId, id -> {
             logger.debug("Cache miss for specific MapConfiguration request: mapId={}. Attempting fetch.", id);
             try {
                 return fetchMapConfigurationFromDb(id);
             } catch (ConfigurationNotFoundException e) {
                 logger.error("Failed to fetch specific map configuration for mapId {}: {}", id, e.getMessage());
                 return null;
             }
         });

         if (config == null) {
             throw new ConfigurationNotFoundException("Map configuration not found or failed to load for mapId: " + mapId);
         }
         return config;
    }
} 