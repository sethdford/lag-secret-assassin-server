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
    private static final String MAP_CONFIG_TABLE_ENV_VAR = "MAP_CONFIG_TABLE_NAME";
    private static final String DEFAULT_MAP_CONFIG_TABLE_NAME = "dev-MapConfigurations";
    private static final String DEFAULT_MAP_ID = "default_map";

    private final GameDao gameDao;
    private final GameZoneStateDao gameZoneStateDao;
    private final SafeZoneDao safeZoneDao;
    private final ShrinkingZoneService shrinkingZoneService;
    private final DynamoDbTable<MapConfiguration> mapConfigTable;

    private final Map<String, MapConfiguration> mapConfigCache;
    private final Map<String, List<Coordinate>> gameBoundaryCache;

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
            logger.debug("Cleared all map configuration caches");
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

    public boolean isLocationInSafeZone(String gameId, Coordinate location, long currentTimeMillis) {
        if (gameId == null || location == null) {
            logger.warn("Cannot check safe zone: null gameId or location");
            return false;
        }

        if (!isCoordinateInGameBoundary(gameId, location)) {
            logger.debug("Location is outside game boundary, not in safe zone: {}", location);
            return false;
        }

        List<com.assassin.model.SafeZone> safeZones = safeZoneDao.getSafeZonesByGameId(gameId);
        if (safeZones == null || safeZones.isEmpty()) {
            logger.debug("No safe zones found for game {}", gameId);
            return false;
        }

        for (com.assassin.model.SafeZone safeZone : safeZones) {
            if (safeZone.getExpiresAt() != null) {
                long expiresAtMillis;
                try {
                    expiresAtMillis = Long.parseLong(safeZone.getExpiresAt());
                    if (expiresAtMillis < currentTimeMillis) {
                        logger.debug("Safe zone {} has expired at {}, current time: {}", 
                                safeZone.getSafeZoneId(), safeZone.getExpiresAt(), currentTimeMillis);
                        continue;
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid expiresAt timestamp for safe zone {}: {}", 
                            safeZone.getSafeZoneId(), safeZone.getExpiresAt());
                    continue;
                }
            }

            Coordinate safeZoneCenter = safeZone.getCenter();
            if (safeZoneCenter != null && safeZone.getRadiusMeters() != null) {
                double distance = GeoUtils.calculateDistance(location, safeZoneCenter);
                if (distance <= safeZone.getRadiusMeters()) {
                    logger.debug("Location {} is within safe zone {} (center: {}, radius: {}m)", 
                            location, safeZone.getSafeZoneId(), safeZoneCenter, safeZone.getRadiusMeters());
                    return true;
                }
            } else {
                logger.warn("Safe zone {} has invalid center or radius", safeZone.getSafeZoneId());
            }
        }

        logger.debug("Location {} is not in any active safe zone for game {}", location, gameId);
        return false;
    }

    public com.assassin.config.MapConfiguration getEffectiveMapConfiguration(String gameId)
            throws ConfigurationNotFoundException {

        if (gameId == null || gameId.isEmpty()) {
            throw new IllegalArgumentException("gameId cannot be null or empty");
        }

        try {
            Game game = gameDao.getGameById(gameId)
                    .orElseThrow(() -> new GameNotFoundException("Game not found: " + gameId));

            String mapId = game.getMapId();
            if (mapId == null || mapId.isEmpty()) {
                logger.warn("Game {} does not have a mapId specified. Falling back to default mapId: {}", gameId, DEFAULT_MAP_ID);
                mapId = DEFAULT_MAP_ID;
            }

            final String effectiveMapId = mapId;
            MapConfiguration cachedConfig = mapConfigCache.computeIfAbsent(effectiveMapId, id -> {
                logger.debug("Cache miss for MapConfiguration with mapId: {}. Attempting fetch.", id);
                try {
                    return fetchMapConfigurationFromDb(id);
                } catch (ConfigurationNotFoundException e) {
                    logger.error("Failed to fetch map configuration for mapId {}: {}", id, e.getMessage());
                    return null;
                }
            });

            if (cachedConfig != null) {
                logger.debug("Returning map configuration for mapId: {} (from cache or fetch)", effectiveMapId);
                return cachedConfig;
            } else {
                logger.warn("Specific map configuration for mapId {} not found or failed to load for game {}. Attempting fallback to default mapId: {}",
                        effectiveMapId, gameId, DEFAULT_MAP_ID);

                if (DEFAULT_MAP_ID.equals(effectiveMapId)) {
                    throw new ConfigurationNotFoundException("Default map configuration (mapId: " + DEFAULT_MAP_ID + ") could not be loaded.");
                }

                MapConfiguration defaultConfig = mapConfigCache.computeIfAbsent(DEFAULT_MAP_ID, id -> {
                     logger.debug("Cache miss for DEFAULT MapConfiguration (mapId: {}). Attempting fetch.", id);
                     try {
                         return fetchMapConfigurationFromDb(id);
                     } catch (ConfigurationNotFoundException e) {
                         logger.error("CRITICAL: Failed to fetch DEFAULT map configuration for mapId {}: {}", id, e.getMessage());
                         return null;
                     }
                 });

                if (defaultConfig != null) {
                    return defaultConfig;
                } else {
                    throw new ConfigurationNotFoundException("Default map configuration (mapId: " + DEFAULT_MAP_ID + ") could not be loaded.");
                }
            }

        } catch (GameNotFoundException e) {
             logger.error("Cannot get effective map configuration because game {} was not found.", gameId);
             logger.warn("Falling back to default map configuration due to GameNotFoundException for game {}", gameId);
             try {
                 return getMapConfigurationById(DEFAULT_MAP_ID);
             } catch (ConfigurationNotFoundException ce) {
                  throw new ConfigurationNotFoundException("Failed to load default map configuration (mapId: " + DEFAULT_MAP_ID + ") after game " + gameId + " not found.", ce);
             }
        } catch (Exception e) {
             logger.error("Unexpected error retrieving effective map configuration for game {}: {}", gameId, e.getMessage(), e);
             logger.warn("Falling back to default map configuration due to unexpected error for game {}", gameId);
              try {
                 return getMapConfigurationById(DEFAULT_MAP_ID);
             } catch (ConfigurationNotFoundException ce) {
                  throw new ConfigurationNotFoundException("Failed to load default map configuration (mapId: " + DEFAULT_MAP_ID + ") after unexpected error for game " + gameId + ".", ce);
             }
        }
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