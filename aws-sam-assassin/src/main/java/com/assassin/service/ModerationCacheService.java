package com.assassin.service;

import com.assassin.model.ModerationFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory cache service for moderation results to avoid redundant API calls
 */
public class ModerationCacheService {
    
    private static final Logger LOG = LoggerFactory.getLogger(ModerationCacheService.class);
    
    private final Map<String, CacheEntry<List<ModerationFlag>>> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    
    public ModerationCacheService() {
        // Schedule cache cleanup every hour
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredEntries, 1, 1, TimeUnit.HOURS);
        LOG.info("ModerationCacheService initialized with cleanup scheduled every hour");
    }
    
    /**
     * Get cached moderation flags for a given key
     * 
     * @param key Cache key (typically a hash of the content)
     * @return Optional containing the cached flags if present and not expired
     */
    public Optional<List<ModerationFlag>> getFlags(String key) {
        if (key == null || key.trim().isEmpty()) {
            return Optional.empty();
        }
        
        CacheEntry<List<ModerationFlag>> entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            LOG.debug("Cache hit for key: {}", key);
            return Optional.of(entry.getValue());
        }
        
        if (entry != null && entry.isExpired()) {
            cache.remove(key); // Remove expired entry
            LOG.debug("Removed expired cache entry for key: {}", key);
        }
        
        LOG.debug("Cache miss for key: {}", key);
        return Optional.empty();
    }
    
    /**
     * Store moderation flags in cache with TTL
     * 
     * @param key Cache key
     * @param flags Moderation flags to cache
     * @param ttl Time to live for the cache entry
     */
    public void putFlags(String key, List<ModerationFlag> flags, Duration ttl) {
        if (key == null || key.trim().isEmpty()) {
            LOG.warn("Attempted to cache with null or empty key");
            return;
        }
        
        if (flags == null) {
            LOG.warn("Attempted to cache null flags for key: {}", key);
            return;
        }
        
        Instant expiryTime = Instant.now().plus(ttl);
        cache.put(key, new CacheEntry<>(flags, expiryTime));
        LOG.debug("Cached {} flags for key: {} with expiry: {}", flags.size(), key, expiryTime);
    }
    
    /**
     * Clear all cached entries
     */
    public void clearCache() {
        int size = cache.size();
        cache.clear();
        LOG.info("Cleared {} entries from moderation cache", size);
    }
    
    /**
     * Get current cache size
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        int totalEntries = cache.size();
        long expiredEntries = cache.values().stream()
                .mapToLong(entry -> entry.isExpired() ? 1 : 0)
                .sum();
        
        return new CacheStats(totalEntries, expiredEntries);
    }
    
    /**
     * Clean up expired entries from cache
     */
    private void cleanupExpiredEntries() {
        int initialSize = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int finalSize = cache.size();
        
        if (initialSize != finalSize) {
            LOG.info("Cache cleanup removed {} expired entries, {} entries remaining", 
                    initialSize - finalSize, finalSize);
        }
    }
    
    /**
     * Shutdown the cache service and cleanup executor
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOG.info("ModerationCacheService shutdown complete");
    }
    
    /**
     * Cache entry wrapper with expiration time
     */
    private static class CacheEntry<T> {
        private final T value;
        private final Instant expiryTime;
        
        public CacheEntry(T value, Instant expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiryTime);
        }
        
        public T getValue() {
            return value;
        }
        
        public Instant getExpiryTime() {
            return expiryTime;
        }
    }
    
    /**
     * Cache statistics container
     */
    public static class CacheStats {
        private final int totalEntries;
        private final long expiredEntries;
        
        public CacheStats(int totalEntries, long expiredEntries) {
            this.totalEntries = totalEntries;
            this.expiredEntries = expiredEntries;
        }
        
        public int getTotalEntries() {
            return totalEntries;
        }
        
        public long getExpiredEntries() {
            return expiredEntries;
        }
        
        public long getValidEntries() {
            return totalEntries - expiredEntries;
        }
        
        @Override
        public String toString() {
            return "CacheStats{" +
                    "totalEntries=" + totalEntries +
                    ", expiredEntries=" + expiredEntries +
                    ", validEntries=" + getValidEntries() +
                    '}';
        }
    }
} 