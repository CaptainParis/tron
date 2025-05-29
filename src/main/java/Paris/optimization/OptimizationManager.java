package Paris.optimization;

import Paris.optimization.lockfree.*;
import Paris.optimization.batching.*;
import Paris.optimization.pooling.*;
import Paris.optimization.primitives.*;

import com.comphenix.protocol.ProtocolManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Central optimization manager that coordinates all performance optimization systems.
 * Provides unified interface for lock-free operations, packet batching, object pooling,
 * and primitive collections with comprehensive monitoring and fallback mechanisms.
 */
public class OptimizationManager {

    private static final Logger logger = Logger.getLogger(OptimizationManager.class.getName());

    // Core optimization components
    private final LockFreePlayerManager lockFreePlayerManager;
    private final LockFreeGameStats lockFreeGameStats;
    private final LockFreeEventQueue<Object> eventQueue;
    private final LockFreeSpatialIndex spatialIndex;

    private final SmartPacketBatcher packetBatcher;
    private final LocationPool locationPool;
    private final PacketPool packetPool;

    // Configuration
    private final Plugin plugin;
    private final ProtocolManager protocolManager;
    private final OptimizationConfig config;

    // State management
    private final AtomicBoolean isEnabled = new AtomicBoolean(false);
    private final AtomicBoolean isHealthy = new AtomicBoolean(true);
    private final AtomicLong startTime = new AtomicLong(0);

    // Monitoring and maintenance
    private BukkitTask monitoringTask;
    private BukkitTask maintenanceTask;

    // Performance tracking
    private final AtomicLong totalOptimizationsSaved = new AtomicLong(0);
    private final AtomicLong totalMemoryReduced = new AtomicLong(0);

    public static class OptimizationConfig {
        public final boolean enableLockFree;
        public final boolean enablePacketBatching;
        public final boolean enableObjectPooling;
        public final boolean enablePrimitiveCollections;
        public final boolean enableFallbacks;
        public final int monitoringIntervalTicks;
        public final int maintenanceIntervalTicks;

        public OptimizationConfig(boolean enableLockFree, boolean enablePacketBatching,
                                boolean enableObjectPooling, boolean enablePrimitiveCollections,
                                boolean enableFallbacks, int monitoringIntervalTicks,
                                int maintenanceIntervalTicks) {
            this.enableLockFree = enableLockFree;
            this.enablePacketBatching = enablePacketBatching;
            this.enableObjectPooling = enableObjectPooling;
            this.enablePrimitiveCollections = enablePrimitiveCollections;
            this.enableFallbacks = enableFallbacks;
            this.monitoringIntervalTicks = monitoringIntervalTicks;
            this.maintenanceIntervalTicks = maintenanceIntervalTicks;
        }

        public static OptimizationConfig createDefault() {
            return new OptimizationConfig(
                true,  // enableLockFree
                true,  // enablePacketBatching
                true,  // enableObjectPooling
                true,  // enablePrimitiveCollections
                true,  // enableFallbacks
                100,   // monitoringIntervalTicks (5 seconds)
                1200   // maintenanceIntervalTicks (1 minute)
            );
        }

        public static OptimizationConfig createConservative() {
            return new OptimizationConfig(
                false, // enableLockFree
                true,  // enablePacketBatching
                true,  // enableObjectPooling
                false, // enablePrimitiveCollections
                true,  // enableFallbacks
                200,   // monitoringIntervalTicks (10 seconds)
                2400   // maintenanceIntervalTicks (2 minutes)
            );
        }
    }

    public OptimizationManager(Plugin plugin, ProtocolManager protocolManager, OptimizationConfig config) {
        this.plugin = plugin;
        this.protocolManager = protocolManager;
        this.config = config;

        // Initialize lock-free components
        if (config.enableLockFree) {
            this.lockFreePlayerManager = new LockFreePlayerManager();
            this.lockFreeGameStats = new LockFreeGameStats();
            this.eventQueue = new LockFreeEventQueue<>(1024); // 1024 events capacity
            this.spatialIndex = new LockFreeSpatialIndex(1000, 30000); // 1000 entries per player, 30s timeout
        } else {
            this.lockFreePlayerManager = null;
            this.lockFreeGameStats = null;
            this.eventQueue = null;
            this.spatialIndex = null;
        }

        // Initialize packet batching
        if (config.enablePacketBatching) {
            this.packetBatcher = new SmartPacketBatcher(plugin, protocolManager);
        } else {
            this.packetBatcher = null;
        }

        // Initialize object pooling
        if (config.enableObjectPooling) {
            this.locationPool = new LocationPool();
            this.packetPool = new PacketPool(protocolManager);
        } else {
            this.locationPool = null;
            this.packetPool = null;
        }

        logger.info("[OptimizationManager] Initialized with config: " + getConfigSummary());
    }

    /**
     * Start all optimization systems
     */
    public boolean start() {
        if (isEnabled.get()) {
            logger.warning("[OptimizationManager] Already started");
            return false;
        }

        try {
            startTime.set(System.currentTimeMillis());

            // Start monitoring and maintenance tasks
            startMonitoringTask();
            startMaintenanceTask();

            isEnabled.set(true);
            isHealthy.set(true);

            logger.info("[OptimizationManager] All optimization systems started successfully");
            return true;

        } catch (Exception e) {
            logger.severe("[OptimizationManager] Failed to start optimization systems: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Stop all optimization systems
     */
    public void stop() {
        if (!isEnabled.get()) {
            return;
        }

        isEnabled.set(false);

        // Stop tasks
        if (monitoringTask != null) {
            monitoringTask.cancel();
        }
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
        }

        // Shutdown packet batcher
        if (packetBatcher != null) {
            packetBatcher.shutdown();
        }

        // Clear pools
        if (locationPool != null) {
            locationPool.clear();
        }
        if (packetPool != null) {
            packetPool.clearAllPools();
        }

        // Clear lock-free structures
        if (lockFreePlayerManager != null) {
            lockFreePlayerManager.clearAllPlayers();
        }
        if (eventQueue != null) {
            eventQueue.clear();
        }
        if (spatialIndex != null) {
            spatialIndex.clear();
        }

        logger.info("[OptimizationManager] All optimization systems stopped");
    }

    /**
     * Start monitoring task
     */
    private void startMonitoringTask() {
        monitoringTask = new BukkitRunnable() {
            @Override
            public void run() {
                performHealthCheck();
                updatePerformanceMetrics();
            }
        }.runTaskTimer(plugin, config.monitoringIntervalTicks, config.monitoringIntervalTicks);

        logger.fine("[OptimizationManager] Started monitoring task");
    }

    /**
     * Start maintenance task
     */
    private void startMaintenanceTask() {
        maintenanceTask = new BukkitRunnable() {
            @Override
            public void run() {
                performMaintenance();
            }
        }.runTaskTimer(plugin, config.maintenanceIntervalTicks, config.maintenanceIntervalTicks);

        logger.fine("[OptimizationManager] Started maintenance task");
    }

    /**
     * Perform health check on all systems
     */
    private void performHealthCheck() {
        boolean healthy = true;

        try {
            // Check lock-free systems
            if (config.enableLockFree) {
                if (eventQueue != null && !eventQueue.isHealthy()) {
                    logger.warning("[OptimizationManager] Event queue unhealthy: " + eventQueue.getPerformanceStats());
                    healthy = false;
                }
            }

            // Check object pools
            if (config.enableObjectPooling) {
                if (locationPool != null && !locationPool.isHealthy()) {
                    logger.warning("[OptimizationManager] Location pool unhealthy: " + locationPool.getPerformanceStats());
                    healthy = false;
                }
                if (packetPool != null && !packetPool.isHealthy()) {
                    logger.warning("[OptimizationManager] Packet pool unhealthy: " + packetPool.getPerformanceStats());
                    healthy = false;
                }
            }

            isHealthy.set(healthy);

        } catch (Exception e) {
            logger.severe("[OptimizationManager] Health check failed: " + e.getMessage());
            isHealthy.set(false);
        }
    }

    /**
     * Update performance metrics
     */
    private void updatePerformanceMetrics() {
        try {
            long optimizationsSaved = 0;
            long memoryReduced = 0;

            // Calculate optimizations from packet batching
            if (packetBatcher != null) {
                optimizationsSaved += packetBatcher.getBatchesSaved();
            }

            // Calculate memory reduction from object pooling
            if (locationPool != null) {
                memoryReduced += locationPool.getPoolHits() * 80L; // ~80 bytes per Location object
            }
            if (packetPool != null) {
                memoryReduced += packetPool.getPoolHits() * 200L; // ~200 bytes per packet object
            }

            totalOptimizationsSaved.set(optimizationsSaved);
            totalMemoryReduced.set(memoryReduced);

        } catch (Exception e) {
            logger.warning("[OptimizationManager] Failed to update performance metrics: " + e.getMessage());
        }
    }

    /**
     * Perform maintenance on all systems
     */
    private void performMaintenance() {
        try {
            // Maintain object pools
            if (config.enableObjectPooling) {
                if (locationPool != null) {
                    locationPool.performMaintenance();
                }
                if (packetPool != null) {
                    packetPool.performMaintenance();
                }
            }

            // Cleanup spatial index
            if (config.enableLockFree && spatialIndex != null) {
                int cleaned = spatialIndex.cleanupExpiredEntries();
                if (cleaned > 0) {
                    logger.fine("[OptimizationManager] Cleaned up " + cleaned + " expired spatial entries");
                }
            }

            logger.finest("[OptimizationManager] Maintenance completed");

        } catch (Exception e) {
            logger.warning("[OptimizationManager] Maintenance failed: " + e.getMessage());
        }
    }

    // Getters for optimization components
    public LockFreePlayerManager getLockFreePlayerManager() { return lockFreePlayerManager; }
    public LockFreeGameStats getLockFreeGameStats() { return lockFreeGameStats; }
    public LockFreeEventQueue<Object> getEventQueue() { return eventQueue; }
    public LockFreeSpatialIndex getSpatialIndex() { return spatialIndex; }
    public SmartPacketBatcher getPacketBatcher() { return packetBatcher; }
    public LocationPool getLocationPool() { return locationPool; }
    public PacketPool getPacketPool() { return packetPool; }

    // Status getters
    public boolean isEnabled() { return isEnabled.get(); }
    public boolean isHealthy() { return isHealthy.get(); }
    public long getUptime() { return System.currentTimeMillis() - startTime.get(); }

    /**
     * Get configuration summary
     */
    private String getConfigSummary() {
        return String.format(
            "LockFree=%s, PacketBatching=%s, ObjectPooling=%s, PrimitiveCollections=%s, Fallbacks=%s",
            config.enableLockFree, config.enablePacketBatching, config.enableObjectPooling,
            config.enablePrimitiveCollections, config.enableFallbacks
        );
    }

    /**
     * Get comprehensive performance report
     */
    public String getPerformanceReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Optimization Manager Performance Report ===\n");
        report.append(String.format("Status: %s (Healthy: %s)\n",
                     isEnabled.get() ? "ENABLED" : "DISABLED", isHealthy.get()));
        report.append(String.format("Uptime: %.1f minutes\n", getUptime() / 60000.0));
        report.append(String.format("Configuration: %s\n", getConfigSummary()));
        report.append(String.format("Total Optimizations Saved: %d\n", totalOptimizationsSaved.get()));
        report.append(String.format("Total Memory Reduced: %d bytes (%.1f KB)\n",
                     totalMemoryReduced.get(), totalMemoryReduced.get() / 1024.0));

        if (config.enableLockFree) {
            report.append("\n--- Lock-Free Systems ---\n");
            if (lockFreePlayerManager != null) {
                report.append(lockFreePlayerManager.getPerformanceStats()).append("\n");
            }
            if (lockFreeGameStats != null) {
                report.append("Game Stats: ").append(lockFreeGameStats.getPerformanceReport()).append("\n");
            }
            if (eventQueue != null) {
                report.append(eventQueue.getPerformanceStats()).append("\n");
            }
            if (spatialIndex != null) {
                report.append(spatialIndex.getPerformanceStats()).append("\n");
            }
        }

        if (config.enablePacketBatching && packetBatcher != null) {
            report.append("\n--- Packet Batching ---\n");
            report.append(packetBatcher.getPerformanceStats()).append("\n");
        }

        if (config.enableObjectPooling) {
            report.append("\n--- Object Pooling ---\n");
            if (locationPool != null) {
                report.append(locationPool.getPerformanceStats()).append("\n");
            }
            if (packetPool != null) {
                report.append(packetPool.getPerformanceStats()).append("\n");
            }
        }

        if (config.enablePrimitiveCollections) {
            report.append("\n--- Primitive Collections ---\n");
            report.append(PrimitiveCollections.getPerformanceStats()).append("\n");
        }

        return report.toString();
    }
}
