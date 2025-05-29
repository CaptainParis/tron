package Paris.optimization.monitoring;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Comprehensive performance monitoring system for the Tron plugin
 * Tracks all optimization metrics and provides detailed performance reports
 */
public class PerformanceMonitor {
    
    private final Plugin plugin;
    private final Logger logger;
    
    // Performance metrics
    private final AtomicLong trailGenerationTime = new AtomicLong();
    private final AtomicLong collisionCheckTime = new AtomicLong();
    private final AtomicLong movementUpdateTime = new AtomicLong();
    private final AtomicLong memoryAllocations = new AtomicLong();
    private final AtomicLong gcPressure = new AtomicLong();
    
    // Operation counters
    private final AtomicLong trailOperations = new AtomicLong();
    private final AtomicLong collisionChecks = new AtomicLong();
    private final AtomicLong movementUpdates = new AtomicLong();
    private final AtomicLong objectsPooled = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    
    // Monitoring task
    private BukkitTask monitoringTask;
    private final long reportIntervalMs = 30000; // 30 seconds
    
    // Performance targets (in nanoseconds)
    private static final long TARGET_TRAIL_GENERATION_NS = 100_000; // 0.1ms
    private static final long TARGET_COLLISION_CHECK_NS = 50_000;   // 0.05ms
    private static final long TARGET_MOVEMENT_UPDATE_NS = 10_000;   // 0.01ms
    
    public PerformanceMonitor(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        
        startMonitoring();
        logger.info("[PerformanceMonitor] Initialized with performance tracking");
    }
    
    /**
     * Start performance monitoring task
     */
    private void startMonitoring() {
        monitoringTask = new BukkitRunnable() {
            @Override
            public void run() {
                generatePerformanceReport();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 30, 20L * 30); // Every 30 seconds
    }
    
    /**
     * Record trail generation performance
     */
    public void recordTrailGeneration(long nanos) {
        trailGenerationTime.addAndGet(nanos);
        trailOperations.incrementAndGet();
    }
    
    /**
     * Record collision check performance
     */
    public void recordCollisionCheck(long nanos) {
        collisionCheckTime.addAndGet(nanos);
        collisionChecks.incrementAndGet();
    }
    
    /**
     * Record movement update performance
     */
    public void recordMovementUpdate(long nanos) {
        movementUpdateTime.addAndGet(nanos);
        movementUpdates.incrementAndGet();
    }
    
    /**
     * Record memory allocation
     */
    public void recordMemoryAllocation(long bytes) {
        memoryAllocations.addAndGet(bytes);
    }
    
    /**
     * Record GC pressure
     */
    public void recordGcPressure(long pressure) {
        gcPressure.addAndGet(pressure);
    }
    
    /**
     * Record object pooling usage
     */
    public void recordObjectPooled() {
        objectsPooled.incrementAndGet();
    }
    
    /**
     * Record cache hit
     */
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }
    
    /**
     * Record cache miss
     */
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }
    
    /**
     * Get average trail generation time in milliseconds
     */
    public double getAverageTrailGenerationTime() {
        long operations = trailOperations.get();
        return operations > 0 ? trailGenerationTime.get() / (double) operations / 1_000_000.0 : 0.0;
    }
    
    /**
     * Get average collision check time in milliseconds
     */
    public double getAverageCollisionCheckTime() {
        long checks = collisionChecks.get();
        return checks > 0 ? collisionCheckTime.get() / (double) checks / 1_000_000.0 : 0.0;
    }
    
    /**
     * Get average movement update time in milliseconds
     */
    public double getAverageMovementUpdateTime() {
        long updates = movementUpdates.get();
        return updates > 0 ? movementUpdateTime.get() / (double) updates / 1_000_000.0 : 0.0;
    }
    
    /**
     * Get cache hit rate
     */
    public double getCacheHitRate() {
        long totalRequests = cacheHits.get() + cacheMisses.get();
        return totalRequests > 0 ? (double) cacheHits.get() / totalRequests * 100.0 : 0.0;
    }
    
    /**
     * Generate comprehensive performance report
     */
    public void generatePerformanceReport() {
        StringBuilder report = new StringBuilder();
        report.append("\n=== TRON PERFORMANCE REPORT ===\n");
        
        // Performance metrics
        report.append("Performance Metrics:\n");
        report.append(String.format("  Trail Generation: %.3fms avg (target: %.3fms) - %s\n",
            getAverageTrailGenerationTime(), TARGET_TRAIL_GENERATION_NS / 1_000_000.0,
            getAverageTrailGenerationTime() <= TARGET_TRAIL_GENERATION_NS / 1_000_000.0 ? "✓" : "⚠"));
        
        report.append(String.format("  Collision Check: %.3fms avg (target: %.3fms) - %s\n",
            getAverageCollisionCheckTime(), TARGET_COLLISION_CHECK_NS / 1_000_000.0,
            getAverageCollisionCheckTime() <= TARGET_COLLISION_CHECK_NS / 1_000_000.0 ? "✓" : "⚠"));
        
        report.append(String.format("  Movement Update: %.3fms avg (target: %.3fms) - %s\n",
            getAverageMovementUpdateTime(), TARGET_MOVEMENT_UPDATE_NS / 1_000_000.0,
            getAverageMovementUpdateTime() <= TARGET_MOVEMENT_UPDATE_NS / 1_000_000.0 ? "✓" : "⚠"));
        
        // Operation counters
        report.append("Operation Counters:\n");
        report.append(String.format("  Trail Operations: %d\n", trailOperations.get()));
        report.append(String.format("  Collision Checks: %d\n", collisionChecks.get()));
        report.append(String.format("  Movement Updates: %d\n", movementUpdates.get()));
        report.append(String.format("  Objects Pooled: %d\n", objectsPooled.get()));
        
        // Cache performance
        report.append("Cache Performance:\n");
        report.append(String.format("  Hit Rate: %.1f%% (%d hits, %d misses)\n",
            getCacheHitRate(), cacheHits.get(), cacheMisses.get()));
        
        // Memory metrics
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        report.append("Memory Usage:\n");
        report.append(String.format("  Used: %.1fMB / %.1fMB (%.1f%%)\n",
            usedMemory / 1024.0 / 1024.0, totalMemory / 1024.0 / 1024.0,
            (double) usedMemory / totalMemory * 100.0));
        
        // Performance assessment
        report.append("Performance Assessment:\n");
        boolean allTargetsMet = getAverageTrailGenerationTime() <= TARGET_TRAIL_GENERATION_NS / 1_000_000.0 &&
                               getAverageCollisionCheckTime() <= TARGET_COLLISION_CHECK_NS / 1_000_000.0 &&
                               getAverageMovementUpdateTime() <= TARGET_MOVEMENT_UPDATE_NS / 1_000_000.0;
        
        if (allTargetsMet) {
            report.append("  ✓ All performance targets met - Excellent performance!\n");
        } else {
            report.append("  ⚠ Some performance targets not met - Consider optimization\n");
        }
        
        report.append("================================\n");
        
        logger.info(report.toString());
    }
    
    /**
     * Get performance summary for commands
     */
    public String getPerformanceSummary() {
        return String.format(
            "Trail: %.3fms, Collision: %.3fms, Movement: %.3fms, Cache: %.1f%%, Pooled: %d",
            getAverageTrailGenerationTime(), getAverageCollisionCheckTime(),
            getAverageMovementUpdateTime(), getCacheHitRate(), objectsPooled.get()
        );
    }
    
    /**
     * Reset all performance metrics
     */
    public void resetMetrics() {
        trailGenerationTime.set(0);
        collisionCheckTime.set(0);
        movementUpdateTime.set(0);
        memoryAllocations.set(0);
        gcPressure.set(0);
        trailOperations.set(0);
        collisionChecks.set(0);
        movementUpdates.set(0);
        objectsPooled.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        
        logger.info("[PerformanceMonitor] All metrics reset");
    }
    
    /**
     * Stop performance monitoring
     */
    public void stop() {
        if (monitoringTask != null) {
            monitoringTask.cancel();
            monitoringTask = null;
        }
        
        // Generate final report
        generatePerformanceReport();
        logger.info("[PerformanceMonitor] Stopped performance monitoring");
    }
}
