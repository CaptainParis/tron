package Paris.optimization.movement;

import org.bukkit.util.Vector;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache for direction calculations to avoid redundant trigonometric operations
 * Reduces CPU usage for movement calculations by caching common directions
 */
public class DirectionCache {
    
    private final ConcurrentHashMap<Integer, Vector> directionCache;
    private final int maxCacheSize;
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    
    // Cache precision - directions are rounded to nearest degree
    private static final int PRECISION = 1;
    
    public DirectionCache(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
        this.directionCache = new ConcurrentHashMap<>();
        
        // Pre-cache common directions (0-359 degrees)
        preWarmCache();
    }
    
    /**
     * Pre-warm cache with common directions
     */
    private void preWarmCache() {
        for (int yaw = 0; yaw < 360; yaw += 5) { // Every 5 degrees
            Vector direction = calculateDirection(yaw);
            directionCache.put(yaw, direction);
        }
    }
    
    /**
     * Get cached direction or calculate and cache it
     */
    public Vector getDirection(float yaw) {
        // Round yaw to nearest degree for caching
        int roundedYaw = Math.round(yaw) % 360;
        if (roundedYaw < 0) roundedYaw += 360;
        
        Vector cached = directionCache.get(roundedYaw);
        if (cached != null) {
            hits.incrementAndGet();
            return cached.clone(); // Return clone to avoid modification
        }
        
        misses.incrementAndGet();
        
        // Calculate and cache if space available
        Vector direction = calculateDirection(roundedYaw);
        if (directionCache.size() < maxCacheSize) {
            directionCache.put(roundedYaw, direction.clone());
        }
        
        return direction;
    }
    
    /**
     * Calculate direction vector from yaw angle
     */
    private Vector calculateDirection(float yaw) {
        double radians = Math.toRadians(yaw);
        double x = -Math.sin(radians);
        double z = Math.cos(radians);
        return new Vector(x, 0, z).normalize();
    }
    
    /**
     * Get cache statistics
     */
    public String getStats() {
        long totalRequests = hits.get() + misses.get();
        double hitRate = totalRequests > 0 ? (double) hits.get() / totalRequests * 100.0 : 0.0;
        
        return String.format("DirectionCache: Size=%d/%d, Hits=%d, Misses=%d (%.1f%% hit rate)",
            directionCache.size(), maxCacheSize, hits.get(), misses.get(), hitRate);
    }
    
    /**
     * Get cache hit rate
     */
    public double getHitRate() {
        long totalRequests = hits.get() + misses.get();
        return totalRequests > 0 ? (double) hits.get() / totalRequests : 0.0;
    }
    
    /**
     * Clear the cache
     */
    public void cleanup() {
        directionCache.clear();
        hits.set(0);
        misses.set(0);
    }
    
    /**
     * Perform cache maintenance
     */
    public void performMaintenance() {
        // Remove least recently used entries if cache is full
        if (directionCache.size() > maxCacheSize * 0.9) {
            // Simple cleanup - remove some entries
            int toRemove = directionCache.size() - (maxCacheSize * 3 / 4);
            directionCache.entrySet().removeIf(entry -> toRemove > 0);
        }
    }
}
