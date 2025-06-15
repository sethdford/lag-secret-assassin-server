package com.assassin.service.proximity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages caching of proximity calculations to reduce redundant computations.
 * Thread-safe implementation for high-concurrency environments.
 */
public class ProximityCacheManager {
    private static final Logger logger = LoggerFactory.getLogger(ProximityCacheManager.class);
    
    // Cache expiration time in milliseconds
    private static final long DEFAULT_CACHE_EXPIRATION_MS = 10000; // 10 seconds
    
    // Maximum cache size to prevent memory issues
    private static final int MAX_CACHE_SIZE = 10000;
    
    private final Map<String, ProximityResult> proximityCache;
    private final long cacheExpirationMs;
    
    public ProximityCacheManager() {
        this(DEFAULT_CACHE_EXPIRATION_MS);
    }
    
    public ProximityCacheManager(long cacheExpirationMs) {
        this.proximityCache = new ConcurrentHashMap<>();
        this.cacheExpirationMs = cacheExpirationMs;
    }
    
    /**
     * Represents a cached proximity calculation result.
     */
    public static class ProximityResult {
        private final double distance;
        private final boolean withinRange;
        private final long calculatedAt;
        private final String player1Id;
        private final String player2Id;
        
        public ProximityResult(String player1Id, String player2Id, double distance, boolean withinRange) {
            this.player1Id = player1Id;
            this.player2Id = player2Id;
            this.distance = distance;
            this.withinRange = withinRange;
            this.calculatedAt = System.currentTimeMillis();
        }
        
        public boolean isExpired(long expirationMs) {
            return System.currentTimeMillis() - calculatedAt > expirationMs;
        }
        
        // Getters
        public double getDistance() { return distance; }
        public boolean isWithinRange() { return withinRange; }
        public long getCalculatedAt() { return calculatedAt; }
        public String getPlayer1Id() { return player1Id; }
        public String getPlayer2Id() { return player2Id; }
    }
    
    /**
     * Gets a cached proximity result if available and not expired.
     * 
     * @param player1Id First player ID
     * @param player2Id Second player ID
     * @return Cached result or null if not found/expired
     */
    public ProximityResult getCachedResult(String player1Id, String player2Id) {
        String cacheKey = generateCacheKey(player1Id, player2Id);
        ProximityResult result = proximityCache.get(cacheKey);
        
        if (result != null) {
            if (result.isExpired(cacheExpirationMs)) {
                proximityCache.remove(cacheKey);
                logger.debug("Cache expired for proximity: {} <-> {}", player1Id, player2Id);
                return null;
            }
            logger.debug("Cache hit for proximity: {} <-> {} = {}m", 
                player1Id, player2Id, result.getDistance());
            return result;
        }
        
        logger.debug("Cache miss for proximity: {} <-> {}", player1Id, player2Id);
        return null;
    }
    
    /**
     * Caches a proximity calculation result.
     * 
     * @param player1Id First player ID
     * @param player2Id Second player ID
     * @param distance Calculated distance
     * @param withinRange Whether players are within elimination range
     */
    public void cacheResult(String player1Id, String player2Id, double distance, boolean withinRange) {
        // Prevent cache from growing too large
        if (proximityCache.size() >= MAX_CACHE_SIZE) {
            cleanExpiredEntries();
            
            // If still too large, clear oldest 10%
            if (proximityCache.size() >= MAX_CACHE_SIZE) {
                clearOldestEntries(MAX_CACHE_SIZE / 10);
            }
        }
        
        String cacheKey = generateCacheKey(player1Id, player2Id);
        ProximityResult result = new ProximityResult(player1Id, player2Id, distance, withinRange);
        proximityCache.put(cacheKey, result);
        
        logger.debug("Cached proximity result: {} <-> {} = {}m", player1Id, player2Id, distance);
    }
    
    /**
     * Invalidates cache entries for a specific player.
     * Called when player location is updated.
     * 
     * @param playerId Player whose cache entries should be invalidated
     */
    public void invalidatePlayerCache(String playerId) {
        final int[] removed = {0};
        proximityCache.entrySet().removeIf(entry -> {
            ProximityResult result = entry.getValue();
            boolean shouldRemove = result.getPlayer1Id().equals(playerId) || 
                                 result.getPlayer2Id().equals(playerId);
            if (shouldRemove) {
                removed[0]++;
            }
            return shouldRemove;
        });
        
        if (removed[0] > 0) {
            logger.debug("Invalidated {} cache entries for player: {}", removed[0], playerId);
        }
    }
    
    /**
     * Clears all expired entries from the cache.
     */
    public void cleanExpiredEntries() {
        int removed = proximityCache.entrySet().removeIf(
            entry -> entry.getValue().isExpired(cacheExpirationMs)
        ) ? 1 : 0;
        
        if (removed > 0) {
            logger.debug("Cleaned {} expired entries from proximity cache", removed);
        }
    }
    
    /**
     * Clears the oldest entries from the cache.
     * 
     * @param count Number of entries to remove
     */
    private void clearOldestEntries(int count) {
        proximityCache.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(
                e1.getValue().getCalculatedAt(), 
                e2.getValue().getCalculatedAt()
            ))
            .limit(count)
            .map(Map.Entry::getKey)
            .forEach(proximityCache::remove);
            
        logger.debug("Cleared {} oldest entries from proximity cache", count);
    }
    
    /**
     * Generates a cache key for two players.
     * Key is symmetric (same for player1->player2 and player2->player1).
     * 
     * @param player1Id First player ID
     * @param player2Id Second player ID
     * @return Cache key
     */
    private String generateCacheKey(String player1Id, String player2Id) {
        // Sort IDs to ensure consistent key regardless of order
        if (player1Id.compareTo(player2Id) < 0) {
            return player1Id + ":" + player2Id;
        } else {
            return player2Id + ":" + player1Id;
        }
    }
    
    /**
     * Gets current cache size.
     * 
     * @return Number of entries in cache
     */
    public int getCacheSize() {
        return proximityCache.size();
    }
    
    /**
     * Clears the entire cache.
     */
    public void clearCache() {
        proximityCache.clear();
        logger.info("Proximity cache cleared");
    }
}