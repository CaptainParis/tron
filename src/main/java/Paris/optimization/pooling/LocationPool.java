package Paris.optimization.pooling;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * High-performance object pool for Location objects to reduce garbage collection pressure.
 * Thread-safe implementation with automatic pool management and performance monitoring.
 */
public class LocationPool {

    private static final Logger logger = Logger.getLogger(LocationPool.class.getName());

    // Pool configuration
    private static final int DEFAULT_INITIAL_SIZE = 100;
    private static final int DEFAULT_MAX_SIZE = 1000;
    private static final int DEFAULT_MIN_SIZE = 50;
    private static final long CLEANUP_INTERVAL_MS = 30000; // 30 seconds

    // Thread-safe pool storage
    private final ConcurrentLinkedQueue<Location> pool;

    // Pool configuration
    private final int maxSize;
    private final int minSize;
    private final int initialSize;

    // Performance tracking
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong poolHits = new AtomicLong(0);
    private final AtomicLong poolMisses = new AtomicLong(0);
    private final AtomicLong totalReturns = new AtomicLong(0);
    private final AtomicLong invalidReturns = new AtomicLong(0);
    private final AtomicInteger currentSize = new AtomicInteger(0);
    private final AtomicInteger peakSize = new AtomicInteger(0);

    // Pool management
    private volatile long lastCleanupTime = System.currentTimeMillis();
    private volatile boolean isWarmedUp = false;

    public LocationPool() {
        this(DEFAULT_INITIAL_SIZE, DEFAULT_MAX_SIZE, DEFAULT_MIN_SIZE);
    }

    public LocationPool(int initialSize, int maxSize, int minSize) {
        this.initialSize = initialSize;
        this.maxSize = maxSize;
        this.minSize = minSize;
        this.pool = new ConcurrentLinkedQueue<>();

        warmUpPool();
        logger.info("[LocationPool] Initialized with initial=" + initialSize +
                   ", max=" + maxSize + ", min=" + minSize);
    }

    /**
     * Get a Location from the pool or create a new one
     */
    public Location acquire() {
        totalRequests.incrementAndGet();

        Location location = pool.poll();
        if (location != null) {
            poolHits.incrementAndGet();
            currentSize.decrementAndGet();

            // Reset the location to default values
            resetLocation(location);

            logger.finest("[LocationPool] Pool hit - acquired location (pool size: " +
                         currentSize.get() + ")");
            return location;
        } else {
            poolMisses.incrementAndGet();

            // Create new location when pool is empty
            location = new Location(null, 0, 0, 0);

            logger.finest("[LocationPool] Pool miss - created new location (pool size: " +
                         currentSize.get() + ")");
            return location;
        }
    }

    /**
     * Get a Location with specific coordinates
     */
    public Location acquire(World world, double x, double y, double z) {
        Location location = acquire();
        location.setWorld(world);
        location.setX(x);
        location.setY(y);
        location.setZ(z);
        return location;
    }

    /**
     * Get a Location with specific coordinates and rotation
     */
    public Location acquire(World world, double x, double y, double z, float yaw, float pitch) {
        Location location = acquire(world, x, y, z);
        location.setYaw(yaw);
        location.setPitch(pitch);
        return location;
    }

    /**
     * Return a Location to the pool
     */
    public boolean release(Location location) {
        if (location == null) {
            invalidReturns.incrementAndGet();
            return false;
        }

        totalReturns.incrementAndGet();

        // Check if pool is at capacity
        if (currentSize.get() >= maxSize) {
            logger.finest("[LocationPool] Pool at capacity - discarding location");
            return false;
        }

        // Validate and clean the location before returning to pool
        if (validateLocation(location)) {
            cleanLocation(location);
            pool.offer(location);

            int newSize = currentSize.incrementAndGet();
            updatePeakSize(newSize);

            logger.finest("[LocationPool] Released location to pool (pool size: " + newSize + ")");
            return true;
        } else {
            invalidReturns.incrementAndGet();
            logger.finest("[LocationPool] Invalid location rejected from pool");
            return false;
        }
    }

    /**
     * Reset location to default values
     */
    private void resetLocation(Location location) {
        location.setWorld(null);
        location.setX(0);
        location.setY(0);
        location.setZ(0);
        location.setYaw(0);
        location.setPitch(0);
    }

    /**
     * Clean location before returning to pool
     */
    private void cleanLocation(Location location) {
        // Clear any references that might cause memory leaks
        location.setWorld(null);
        location.setX(0);
        location.setY(0);
        location.setZ(0);
        location.setYaw(0);
        location.setPitch(0);
    }

    /**
     * Validate location before accepting into pool
     */
    private boolean validateLocation(Location location) {
        // Check for null and basic validity
        if (location == null) {
            return false;
        }

        // Check for reasonable coordinate values (prevent memory issues)
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) ||
            Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
            return false;
        }

        // Check for reasonable bounds (within Minecraft world limits)
        if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000 ||
            y < -2048 || y > 2048) {
            return false;
        }

        return true;
    }

    /**
     * Update peak size tracking
     */
    private void updatePeakSize(int newSize) {
        int currentPeak = peakSize.get();
        while (newSize > currentPeak && !peakSize.compareAndSet(currentPeak, newSize)) {
            currentPeak = peakSize.get();
        }
    }

    /**
     * Warm up the pool with initial objects
     */
    private void warmUpPool() {
        logger.info("[LocationPool] Warming up pool with " + initialSize + " locations...");

        for (int i = 0; i < initialSize; i++) {
            Location location = new Location(null, 0, 0, 0);
            pool.offer(location);
            currentSize.incrementAndGet();
        }

        isWarmedUp = true;
        logger.info("[LocationPool] Pool warmed up successfully");
    }

    /**
     * Perform periodic cleanup and maintenance
     */
    public void performMaintenance() {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastCleanupTime < CLEANUP_INTERVAL_MS) {
            return; // Too soon for cleanup
        }

        lastCleanupTime = currentTime;

        int currentPoolSize = currentSize.get();

        // Shrink pool if it's too large and hit rate is good
        if (currentPoolSize > minSize * 2 && getHitRate() > 0.8) {
            int toRemove = Math.min(currentPoolSize - minSize, 50);

            for (int i = 0; i < toRemove; i++) {
                if (pool.poll() != null) {
                    currentSize.decrementAndGet();
                }
            }

            logger.fine("[LocationPool] Maintenance: removed " + toRemove +
                       " locations (new size: " + currentSize.get() + ")");
        }

        // Expand pool if hit rate is low
        else if (getHitRate() < 0.5 && currentPoolSize < maxSize) {
            int toAdd = Math.min(maxSize - currentPoolSize, 25);

            for (int i = 0; i < toAdd; i++) {
                Location location = new Location(null, 0, 0, 0);
                pool.offer(location);
                currentSize.incrementAndGet();
            }

            logger.fine("[LocationPool] Maintenance: added " + toAdd +
                       " locations (new size: " + currentSize.get() + ")");
        }
    }

    /**
     * Clear the entire pool
     */
    public void clear() {
        pool.clear();
        currentSize.set(0);
        logger.info("[LocationPool] Pool cleared");
    }

    /**
     * Get current pool size
     */
    public int size() {
        return currentSize.get();
    }

    /**
     * Check if pool is empty
     */
    public boolean isEmpty() {
        return pool.isEmpty();
    }

    /**
     * Get hit rate (percentage of requests served from pool)
     */
    public double getHitRate() {
        long requests = totalRequests.get();
        long hits = poolHits.get();
        return requests > 0 ? (double) hits / requests : 0.0;
    }

    /**
     * Get pool utilization (current size vs max size)
     */
    public double getUtilization() {
        return (double) currentSize.get() / maxSize;
    }

    /**
     * Check if pool is healthy
     */
    public boolean isHealthy() {
        // Pool is healthy if:
        // 1. No requests yet (startup phase) OR hit rate is reasonable (> 10%)
        // 2. Not too many invalid returns (< 20%)
        // 3. Pool size is within reasonable bounds

        long totalRequests = poolHits.get() + poolMisses.get();
        double hitRate = getHitRate();
        long returns = totalReturns.get();
        long invalidRet = invalidReturns.get();
        double invalidRate = returns > 0 ? (double) invalidRet / returns : 0.0;

        // More lenient health check - pools are healthy during startup or with minimal usage
        return (totalRequests == 0 || hitRate > 0.1) && invalidRate < 0.2 && currentSize.get() >= minSize;
    }

    /**
     * Reset all statistics
     */
    public void resetStats() {
        totalRequests.set(0);
        poolHits.set(0);
        poolMisses.set(0);
        totalReturns.set(0);
        invalidReturns.set(0);
        peakSize.set(currentSize.get());

        logger.info("[LocationPool] Statistics reset");
    }

    /**
     * Get comprehensive performance statistics
     */
    public String getPerformanceStats() {
        long requests = totalRequests.get();
        long hits = poolHits.get();
        long misses = poolMisses.get();
        long returns = totalReturns.get();
        long invalidRet = invalidReturns.get();

        return String.format(
            "LocationPool Stats: Size=%d/%d (Peak=%d), Requests=%d, Hits=%d (%.1f%%), " +
            "Misses=%d, Returns=%d, Invalid=%d (%.1f%%), Healthy=%s",
            currentSize.get(), maxSize, peakSize.get(), requests, hits,
            getHitRate() * 100.0, misses, returns, invalidRet,
            returns > 0 ? (invalidRet * 100.0 / returns) : 0.0, isHealthy()
        );
    }

    // Getters for individual statistics
    public long getTotalRequests() { return totalRequests.get(); }
    public long getPoolHits() { return poolHits.get(); }
    public long getPoolMisses() { return poolMisses.get(); }
    public long getTotalReturns() { return totalReturns.get(); }
    public long getInvalidReturns() { return invalidReturns.get(); }
    public int getPeakSize() { return peakSize.get(); }
    public boolean isWarmedUp() { return isWarmedUp; }
}
