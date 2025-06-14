package com.assassin.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.assassin.model.Coordinate;
import com.assassin.util.GeoUtils;

/**
 * High-performance spatial index implementation using a hierarchical grid structure.
 * Provides efficient spatial queries for point-in-polygon, radius searches, and K-nearest neighbor operations.
 * Thread-safe and optimized for read-heavy workloads typical in gaming applications.
 */
public class SpatialIndex<T extends SpatialIndex.SpatialElement> {
    
    private static final Logger logger = LoggerFactory.getLogger(SpatialIndex.class);
    
    // Configuration constants
    private static final int DEFAULT_MAX_ELEMENTS_PER_CELL = 50;
    private static final int DEFAULT_MAX_DEPTH = 8;
    private static final double DEFAULT_MIN_CELL_SIZE = 0.0001; // ~11 meters
    
    private final int maxElementsPerCell;
    private final int maxDepth;
    private final double minCellSize;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Root node of the spatial index
    private SpatialNode root;
    private BoundingBox bounds;
    
    // Cache for frequently accessed regions
    private final ConcurrentHashMap<String, CachedRegion> regionCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;
    private static final long CACHE_TTL_MS = 30000; // 30 seconds
    
    /**
     * Creates a spatial index with default configuration.
     */
    public SpatialIndex(BoundingBox bounds) {
        this(bounds, DEFAULT_MAX_ELEMENTS_PER_CELL, DEFAULT_MAX_DEPTH, DEFAULT_MIN_CELL_SIZE);
    }
    
    /**
     * Creates a spatial index with custom configuration.
     */
    public SpatialIndex(BoundingBox bounds, int maxElementsPerCell, int maxDepth, double minCellSize) {
        this.bounds = bounds;
        this.maxElementsPerCell = maxElementsPerCell;
        this.maxDepth = maxDepth;
        this.minCellSize = minCellSize;
        this.root = new SpatialNode(bounds, 0);
        
        logger.info("Created spatial index with bounds: {}, maxElements: {}, maxDepth: {}, minCellSize: {}", 
                   bounds, maxElementsPerCell, maxDepth, minCellSize);
    }
    
    /**
     * Inserts an element into the spatial index.
     */
    public void insert(T element) {
        lock.writeLock().lock();
        try {
            root.insert(element);
            invalidateCache();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Removes an element from the spatial index.
     */
    public boolean remove(T element) {
        lock.writeLock().lock();
        try {
            boolean removed = root.remove(element);
            if (removed) {
                invalidateCache();
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Updates an element's position in the spatial index.
     */
    public void update(T element) {
        lock.writeLock().lock();
        try {
            root.remove(element);
            root.insert(element);
            invalidateCache();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Finds all elements within a specified radius of a point.
     */
    public List<T> findWithinRadius(Coordinate center, double radiusMeters) {
        String cacheKey = String.format("radius_%.6f_%.6f_%.0f", 
                                       center.getLatitude(), center.getLongitude(), radiusMeters);
        
        CachedRegion cached = regionCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            @SuppressWarnings("unchecked")
            List<T> cachedElements = (List<T>) cached.elements;
            return new ArrayList<>(cachedElements);
        }
        
        lock.readLock().lock();
        try {
            // Calculate bounding box for the radius
            double[] bbox = GeoUtils.calculateBoundingBox(
                center.getLatitude(), center.getLongitude(), radiusMeters);
            BoundingBox searchBounds = new BoundingBox(
                new Coordinate(bbox[0], bbox[1]),
                new Coordinate(bbox[2], bbox[3])
            );
            
            List<T> candidates = new ArrayList<>();
            root.query(searchBounds, candidates);
            
            // Filter candidates by exact distance
            List<T> results = new ArrayList<>();
            for (T element : candidates) {
                double distance = GeoUtils.calculateDistance(center, element.getLocation());
                if (distance <= radiusMeters) {
                    results.add(element);
                }
            }
            
            // Cache the result
            if (regionCache.size() < MAX_CACHE_SIZE) {
                regionCache.put(cacheKey, new CachedRegion(results));
            }
            
            return results;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Finds all elements within a bounding box.
     */
    public List<T> findWithinBounds(BoundingBox bounds) {
        String cacheKey = String.format("bounds_%.6f_%.6f_%.6f_%.6f", 
                                       bounds.getSouthWest().getLatitude(), bounds.getSouthWest().getLongitude(),
                                       bounds.getNorthEast().getLatitude(), bounds.getNorthEast().getLongitude());
        
        CachedRegion cached = regionCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            @SuppressWarnings("unchecked")
            List<T> cachedElements = (List<T>) cached.elements;
            return new ArrayList<>(cachedElements);
        }
        
        lock.readLock().lock();
        try {
            List<T> results = new ArrayList<>();
            root.query(bounds, results);
            
            // Cache the result
            if (regionCache.size() < MAX_CACHE_SIZE) {
                regionCache.put(cacheKey, new CachedRegion(results));
            }
            
            return results;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Finds the K nearest elements to a point.
     */
    public List<ElementDistancePair<T>> findKNearest(Coordinate center, int k) {
        lock.readLock().lock();
        try {
            KNearestCollector<T> collector = new KNearestCollector<>(center, k);
            root.collectKNearest(center, collector);
            return collector.getResults();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Finds all elements within a polygon.
     */
    public List<T> findWithinPolygon(List<Coordinate> polygon) {
        lock.readLock().lock();
        try {
            // Calculate bounding box of the polygon
            BoundingBox polygonBounds = calculatePolygonBounds(polygon);
            
            // Get candidates within bounding box
            List<T> candidates = new ArrayList<>();
            root.query(polygonBounds, candidates);
            
            // Filter candidates by point-in-polygon test
            List<T> results = new ArrayList<>();
            for (T element : candidates) {
                if (GeoUtils.isPointInBoundary(element.getLocation(), polygon)) {
                    results.add(element);
                }
            }
            
            return results;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets statistics about the spatial index.
     */
    public IndexStatistics getStatistics() {
        lock.readLock().lock();
        try {
            IndexStatistics stats = new IndexStatistics();
            root.collectStatistics(stats);
            stats.cacheSize = regionCache.size();
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Clears all elements from the spatial index.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            root = new SpatialNode(bounds, 0);
            invalidateCache();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void invalidateCache() {
        regionCache.clear();
    }
    
    private BoundingBox calculatePolygonBounds(List<Coordinate> polygon) {
        if (polygon.isEmpty()) {
            throw new IllegalArgumentException("Polygon cannot be empty");
        }
        
        double minLat = polygon.get(0).getLatitude();
        double maxLat = polygon.get(0).getLatitude();
        double minLon = polygon.get(0).getLongitude();
        double maxLon = polygon.get(0).getLongitude();
        
        for (Coordinate coord : polygon) {
            minLat = Math.min(minLat, coord.getLatitude());
            maxLat = Math.max(maxLat, coord.getLatitude());
            minLon = Math.min(minLon, coord.getLongitude());
            maxLon = Math.max(maxLon, coord.getLongitude());
        }
        
        return new BoundingBox(new Coordinate(minLat, minLon), new Coordinate(maxLat, maxLon));
    }
    
    /**
     * Internal node structure for the spatial index.
     */
    private class SpatialNode {
        private final BoundingBox bounds;
        private final int depth;
        private final List<T> elements = new ArrayList<>();
        private SpatialNode[] children = null;
        
        public SpatialNode(BoundingBox bounds, int depth) {
            this.bounds = bounds;
            this.depth = depth;
        }
        
        public void insert(T element) {
            if (!isInBounds(element.getLocation())) {
                return; // Element is outside this node's bounds
            }
            
            if (children == null) {
                elements.add(element);
                
                // Check if we need to subdivide
                if (elements.size() > maxElementsPerCell && depth < maxDepth && canSubdivide()) {
                    subdivide();
                }
            } else {
                // Insert into appropriate child
                for (SpatialNode child : children) {
                    if (child.isInBounds(element.getLocation())) {
                        child.insert(element);
                        break;
                    }
                }
            }
        }
        
        public boolean remove(T element) {
            if (children == null) {
                return elements.remove(element);
            } else {
                for (SpatialNode child : children) {
                    if (child.remove(element)) {
                        return true;
                    }
                }
                return false;
            }
        }
        
        public void query(BoundingBox queryBounds, List<T> results) {
            if (!bounds.intersects(queryBounds)) {
                return;
            }
            
            if (children == null) {
                // Leaf node - check all elements
                for (T element : elements) {
                    if (queryBounds.contains(element.getLocation())) {
                        results.add(element);
                    }
                }
            } else {
                // Internal node - recurse to children
                for (SpatialNode child : children) {
                    child.query(queryBounds, results);
                }
            }
        }
        
        public void collectKNearest(Coordinate center, KNearestCollector<T> collector) {
            if (children == null) {
                // Leaf node - check all elements
                for (T element : elements) {
                    double distance = GeoUtils.calculateDistance(center, element.getLocation());
                    collector.consider(element, distance);
                }
            } else {
                // Internal node - recurse to children in order of distance
                for (SpatialNode child : children) {
                    double minDistance = child.bounds.minDistanceTo(center);
                    if (collector.shouldConsider(minDistance)) {
                        child.collectKNearest(center, collector);
                    }
                }
            }
        }
        
        public void collectStatistics(IndexStatistics stats) {
            if (children == null) {
                stats.leafNodes++;
                stats.totalElements += elements.size();
                stats.maxElementsInNode = Math.max(stats.maxElementsInNode, elements.size());
                stats.maxDepth = Math.max(stats.maxDepth, depth);
            } else {
                stats.internalNodes++;
                for (SpatialNode child : children) {
                    child.collectStatistics(stats);
                }
            }
        }
        
        private boolean isInBounds(Coordinate location) {
            return bounds.contains(location);
        }
        
        private boolean canSubdivide() {
            double latSize = bounds.getNorthEast().getLatitude() - bounds.getSouthWest().getLatitude();
            double lonSize = bounds.getNorthEast().getLongitude() - bounds.getSouthWest().getLongitude();
            return latSize > minCellSize && lonSize > minCellSize;
        }
        
        private void subdivide() {
            double midLat = (bounds.getSouthWest().getLatitude() + bounds.getNorthEast().getLatitude()) / 2;
            double midLon = (bounds.getSouthWest().getLongitude() + bounds.getNorthEast().getLongitude()) / 2;
            
            @SuppressWarnings("unchecked")
            SpatialNode[] childArray = (SpatialNode[]) new SpatialIndex<?>.SpatialNode[4];
            children = childArray;
            children[0] = new SpatialNode(new BoundingBox(
                bounds.getSouthWest(),
                new Coordinate(midLat, midLon)
            ), depth + 1);
            children[1] = new SpatialNode(new BoundingBox(
                new Coordinate(bounds.getSouthWest().getLatitude(), midLon),
                new Coordinate(midLat, bounds.getNorthEast().getLongitude())
            ), depth + 1);
            children[2] = new SpatialNode(new BoundingBox(
                new Coordinate(midLat, bounds.getSouthWest().getLongitude()),
                new Coordinate(bounds.getNorthEast().getLatitude(), midLon)
            ), depth + 1);
            children[3] = new SpatialNode(new BoundingBox(
                new Coordinate(midLat, midLon),
                bounds.getNorthEast()
            ), depth + 1);
            
            // Redistribute elements to children
            for (T element : elements) {
                for (SpatialNode child : children) {
                    if (child.isInBounds(element.getLocation())) {
                        child.insert(element);
                        break;
                    }
                }
            }
            
            elements.clear();
        }
    }
    
    /**
     * Interface for elements that can be stored in the spatial index.
     */
    public interface SpatialElement {
        Coordinate getLocation();
        String getId();
    }
    
    /**
     * Bounding box implementation with intersection and containment methods.
     */
    public static class BoundingBox {
        private final Coordinate southWest;
        private final Coordinate northEast;
        
        public BoundingBox(Coordinate southWest, Coordinate northEast) {
            this.southWest = southWest;
            this.northEast = northEast;
        }
        
        public boolean contains(Coordinate point) {
            return point.getLatitude() >= southWest.getLatitude() &&
                   point.getLatitude() <= northEast.getLatitude() &&
                   point.getLongitude() >= southWest.getLongitude() &&
                   point.getLongitude() <= northEast.getLongitude();
        }
        
        public boolean intersects(BoundingBox other) {
            return !(other.northEast.getLatitude() < southWest.getLatitude() ||
                     other.southWest.getLatitude() > northEast.getLatitude() ||
                     other.northEast.getLongitude() < southWest.getLongitude() ||
                     other.southWest.getLongitude() > northEast.getLongitude());
        }
        
        public double minDistanceTo(Coordinate point) {
            double lat = point.getLatitude();
            double lon = point.getLongitude();
            
            double closestLat = Math.max(southWest.getLatitude(), 
                                       Math.min(lat, northEast.getLatitude()));
            double closestLon = Math.max(southWest.getLongitude(), 
                                       Math.min(lon, northEast.getLongitude()));
            
            return GeoUtils.calculateDistance(point, new Coordinate(closestLat, closestLon));
        }
        
        public Coordinate getSouthWest() { return southWest; }
        public Coordinate getNorthEast() { return northEast; }
        
        @Override
        public String toString() {
            return String.format("BoundingBox[SW=%s, NE=%s]", southWest, northEast);
        }
    }
    
    /**
     * Collector for K-nearest neighbor queries.
     */
    private static class KNearestCollector<T extends SpatialElement> {
        private final Coordinate center;
        private final int k;
        private final List<ElementDistancePair<T>> results = new ArrayList<>();
        
        public KNearestCollector(Coordinate center, int k) {
            this.center = center;
            this.k = k;
        }
        
        public void consider(T element, double distance) {
            results.add(new ElementDistancePair<>(element, distance));
            results.sort((a, b) -> Double.compare(a.distance, b.distance));
            
            if (results.size() > k) {
                results.remove(results.size() - 1);
            }
        }
        
        public boolean shouldConsider(double minDistance) {
            return results.size() < k || minDistance < results.get(results.size() - 1).distance;
        }
        
        public List<ElementDistancePair<T>> getResults() {
            return new ArrayList<>(results);
        }
    }
    
    /**
     * Pair of element and its distance from a query point.
     */
    public static class ElementDistancePair<T extends SpatialElement> {
        public final T element;
        public final double distance;
        
        public ElementDistancePair(T element, double distance) {
            this.element = element;
            this.distance = distance;
        }
        
        public T getElement() { return element; }
        public double getDistance() { return distance; }
    }
    
    /**
     * Cached region for performance optimization.
     */
    private static class CachedRegion {
        private final List<? extends SpatialElement> elements;
        private final long timestamp;
        
        public CachedRegion(List<? extends SpatialElement> elements) {
            this.elements = new ArrayList<>(elements);
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
    
    /**
     * Statistics about the spatial index.
     */
    public static class IndexStatistics {
        public int leafNodes = 0;
        public int internalNodes = 0;
        public int totalElements = 0;
        public int maxElementsInNode = 0;
        public int maxDepth = 0;
        public int cacheSize = 0;
        
        @Override
        public String toString() {
            return String.format("IndexStatistics[leafNodes=%d, internalNodes=%d, totalElements=%d, " +
                               "maxElementsInNode=%d, maxDepth=%d, cacheSize=%d]",
                               leafNodes, internalNodes, totalElements, maxElementsInNode, maxDepth, cacheSize);
        }
    }
} 