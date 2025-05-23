package com.assassin.service;

import com.assassin.model.ModerationFlag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ModerationCacheServiceTest {

    private ModerationCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new ModerationCacheService();
    }

    @Test
    void testPutAndGetFlags_Success() {
        // Arrange
        String key = "test-key";
        ModerationFlag flag = new ModerationFlag("TEST", 90.0, "TestSource", Collections.emptyMap());
        List<ModerationFlag> flags = List.of(flag);
        Duration ttl = Duration.ofMinutes(5);

        // Act
        cacheService.putFlags(key, flags, ttl);
        Optional<List<ModerationFlag>> result = cacheService.getFlags(key);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
        assertEquals("TEST", result.get().get(0).getFlagType());
        assertEquals(90.0, result.get().get(0).getConfidence());
    }

    @Test
    void testGetFlags_WithNullKey_ReturnsEmpty() {
        // Act
        Optional<List<ModerationFlag>> result = cacheService.getFlags(null);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetFlags_WithEmptyKey_ReturnsEmpty() {
        // Act
        Optional<List<ModerationFlag>> result = cacheService.getFlags("");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetFlags_NonExistentKey_ReturnsEmpty() {
        // Act
        Optional<List<ModerationFlag>> result = cacheService.getFlags("non-existent");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testPutFlags_WithNullKey_DoesNotThrow() {
        // Arrange
        ModerationFlag flag = new ModerationFlag("TEST", 90.0, "TestSource", Collections.emptyMap());
        List<ModerationFlag> flags = List.of(flag);

        // Act & Assert
        assertDoesNotThrow(() -> cacheService.putFlags(null, flags, Duration.ofMinutes(5)));
    }

    @Test
    void testPutFlags_WithNullFlags_DoesNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() -> cacheService.putFlags("test-key", null, Duration.ofMinutes(5)));
    }

    @Test
    void testClearCache() {
        // Arrange
        String key = "test-key";
        ModerationFlag flag = new ModerationFlag("TEST", 90.0, "TestSource", Collections.emptyMap());
        List<ModerationFlag> flags = List.of(flag);
        cacheService.putFlags(key, flags, Duration.ofMinutes(5));

        // Verify entry exists
        assertTrue(cacheService.getFlags(key).isPresent());

        // Act
        cacheService.clearCache();

        // Assert
        assertFalse(cacheService.getFlags(key).isPresent());
        assertEquals(0, cacheService.getCacheSize());
    }

    @Test
    void testGetCacheSize() {
        // Initial size should be 0
        assertEquals(0, cacheService.getCacheSize());

        // Add entries
        ModerationFlag flag = new ModerationFlag("TEST", 90.0, "TestSource", Collections.emptyMap());
        List<ModerationFlag> flags = List.of(flag);
        
        cacheService.putFlags("key1", flags, Duration.ofMinutes(5));
        assertEquals(1, cacheService.getCacheSize());

        cacheService.putFlags("key2", flags, Duration.ofMinutes(5));
        assertEquals(2, cacheService.getCacheSize());
    }

    @Test
    void testGetStats() {
        // Arrange
        ModerationFlag flag = new ModerationFlag("TEST", 90.0, "TestSource", Collections.emptyMap());
        List<ModerationFlag> flags = List.of(flag);
        
        cacheService.putFlags("key1", flags, Duration.ofMinutes(5));
        cacheService.putFlags("key2", flags, Duration.ofMinutes(5));

        // Act
        ModerationCacheService.CacheStats stats = cacheService.getStats();

        // Assert
        assertEquals(2, stats.getTotalEntries());
        assertEquals(0, stats.getExpiredEntries());
        assertEquals(2, stats.getValidEntries());
    }

    @Test
    void testCacheStatsToString() {
        ModerationCacheService.CacheStats stats = new ModerationCacheService.CacheStats(5, 2);
        String toString = stats.toString();
        
        assertTrue(toString.contains("totalEntries=5"));
        assertTrue(toString.contains("expiredEntries=2"));
        assertTrue(toString.contains("validEntries=3"));
    }

    @Test
    void testShutdown_DoesNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() -> cacheService.shutdown());
    }
} 