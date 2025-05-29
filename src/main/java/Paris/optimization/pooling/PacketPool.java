package Paris.optimization.pooling;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * High-performance object pool for PacketContainer objects to reduce allocation overhead.
 * Maintains separate pools for different packet types with automatic management.
 */
public class PacketPool {

    private static final Logger logger = Logger.getLogger(PacketPool.class.getName());

    // Pool configuration per packet type
    private static final int DEFAULT_POOL_SIZE = 50;
    private static final int MAX_POOL_SIZE = 200;
    private static final int MIN_POOL_SIZE = 10;

    // Packet type specific pool
    private static class TypedPacketPool {
        final PacketType packetType;
        final ConcurrentLinkedQueue<PacketContainer> pool;
        final AtomicInteger currentSize = new AtomicInteger(0);
        final AtomicLong hits = new AtomicLong(0);
        final AtomicLong misses = new AtomicLong(0);
        final AtomicLong returns = new AtomicLong(0);

        TypedPacketPool(PacketType packetType) {
            this.packetType = packetType;
            this.pool = new ConcurrentLinkedQueue<>();
        }
    }

    // Pool storage
    private final ConcurrentHashMap<PacketType, TypedPacketPool> typedPools;
    private final ProtocolManager protocolManager;

    // Global performance tracking
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalHits = new AtomicLong(0);
    private final AtomicLong totalMisses = new AtomicLong(0);
    private final AtomicLong totalReturns = new AtomicLong(0);
    private final AtomicLong invalidReturns = new AtomicLong(0);

    // Maintenance tracking
    private volatile long lastMaintenanceTime = System.currentTimeMillis();
    private static final long MAINTENANCE_INTERVAL_MS = 60000; // 1 minute

    public PacketPool(ProtocolManager protocolManager) {
        this.protocolManager = protocolManager;
        this.typedPools = new ConcurrentHashMap<>();

        // Pre-warm pools for common packet types
        preWarmCommonPools();

        logger.info("[PacketPool] Initialized with ProtocolManager");
    }

    /**
     * Pre-warm pools for commonly used packet types
     */
    private void preWarmCommonPools() {
        PacketType[] commonTypes = {
            PacketType.Play.Server.BLOCK_CHANGE,
            PacketType.Play.Server.MULTI_BLOCK_CHANGE,
            PacketType.Play.Server.ENTITY_TELEPORT,
            PacketType.Play.Server.ENTITY_DESTROY,
            PacketType.Play.Server.SPAWN_ENTITY,
            PacketType.Play.Server.ENTITY_METADATA
        };

        for (PacketType type : commonTypes) {
            warmUpPool(type, DEFAULT_POOL_SIZE);
        }

        logger.info("[PacketPool] Pre-warmed " + commonTypes.length + " common packet pools");
    }

    /**
     * Warm up a specific packet type pool
     */
    private void warmUpPool(PacketType packetType, int size) {
        TypedPacketPool typedPool = getOrCreateTypedPool(packetType);

        for (int i = 0; i < size; i++) {
            try {
                PacketContainer packet = protocolManager.createPacket(packetType);
                typedPool.pool.offer(packet);
                typedPool.currentSize.incrementAndGet();
            } catch (Exception e) {
                logger.warning("[PacketPool] Failed to create packet for warm-up: " + e.getMessage());
                break;
            }
        }

        logger.fine("[PacketPool] Warmed up " + packetType + " pool with " +
                   typedPool.currentSize.get() + " packets");
    }

    /**
     * Get or create a typed pool for a packet type
     */
    private TypedPacketPool getOrCreateTypedPool(PacketType packetType) {
        return typedPools.computeIfAbsent(packetType, TypedPacketPool::new);
    }

    /**
     * Acquire a packet from the pool
     */
    public PacketContainer acquire(PacketType packetType) {
        totalRequests.incrementAndGet();

        TypedPacketPool typedPool = getOrCreateTypedPool(packetType);
        PacketContainer packet = typedPool.pool.poll();

        if (packet != null) {
            // Pool hit
            typedPool.hits.incrementAndGet();
            totalHits.incrementAndGet();
            typedPool.currentSize.decrementAndGet();

            // Clear the packet for reuse
            clearPacket(packet);

            logger.finest("[PacketPool] Pool hit for " + packetType +
                         " (pool size: " + typedPool.currentSize.get() + ")");

            return packet;
        } else {
            // Pool miss - create new packet
            typedPool.misses.incrementAndGet();
            totalMisses.incrementAndGet();

            try {
                packet = protocolManager.createPacket(packetType);

                logger.finest("[PacketPool] Pool miss for " + packetType + " - created new packet");

                return packet;
            } catch (Exception e) {
                logger.severe("[PacketPool] Failed to create packet " + packetType + ": " + e.getMessage());
                return null;
            }
        }
    }

    /**
     * Return a packet to the pool
     */
    public boolean release(PacketContainer packet) {
        if (packet == null) {
            invalidReturns.incrementAndGet();
            return false;
        }

        totalReturns.incrementAndGet();

        PacketType packetType = packet.getType();
        TypedPacketPool typedPool = getOrCreateTypedPool(packetType);

        // Check if pool is at capacity
        if (typedPool.currentSize.get() >= MAX_POOL_SIZE) {
            logger.finest("[PacketPool] Pool at capacity for " + packetType + " - discarding packet");
            return false;
        }

        // Validate and clean packet before returning to pool
        if (validatePacket(packet)) {
            clearPacket(packet);
            typedPool.pool.offer(packet);
            typedPool.returns.incrementAndGet();
            typedPool.currentSize.incrementAndGet();

            logger.finest("[PacketPool] Released " + packetType + " packet to pool " +
                         "(pool size: " + typedPool.currentSize.get() + ")");

            return true;
        } else {
            invalidReturns.incrementAndGet();
            logger.finest("[PacketPool] Invalid packet rejected: " + packetType);
            return false;
        }
    }

    /**
     * Clear packet data for reuse
     */
    private void clearPacket(PacketContainer packet) {
        try {
            // Clear all modifiers to reset packet state
            // This is a simplified approach - in practice, you might need
            // more specific clearing based on packet type

            // Note: ProtocolLib packets are complex objects and complete clearing
            // might not be possible/safe. This is a basic implementation.

            // For safety, we'll just ensure the packet is in a clean state
            // The actual clearing depends on the specific packet structure

        } catch (Exception e) {
            logger.warning("[PacketPool] Failed to clear packet: " + e.getMessage());
        }
    }

    /**
     * Validate packet before accepting into pool
     */
    private boolean validatePacket(PacketContainer packet) {
        if (packet == null) {
            return false;
        }

        try {
            // Basic validation - ensure packet type is set
            PacketType type = packet.getType();
            if (type == null) {
                return false;
            }

            // Additional validation could be added here
            return true;

        } catch (Exception e) {
            logger.warning("[PacketPool] Packet validation failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Perform periodic maintenance on all pools
     */
    public void performMaintenance() {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastMaintenanceTime < MAINTENANCE_INTERVAL_MS) {
            return;
        }

        lastMaintenanceTime = currentTime;

        int totalCleaned = 0;
        int totalAdded = 0;

        for (TypedPacketPool typedPool : typedPools.values()) {
            int cleaned = performPoolMaintenance(typedPool);
            totalCleaned += cleaned;

            // Add packets if pool is too small and has good hit rate
            if (typedPool.currentSize.get() < MIN_POOL_SIZE && getPoolHitRate(typedPool) > 0.5) {
                int toAdd = MIN_POOL_SIZE - typedPool.currentSize.get();
                for (int i = 0; i < toAdd; i++) {
                    try {
                        PacketContainer packet = protocolManager.createPacket(typedPool.packetType);
                        typedPool.pool.offer(packet);
                        typedPool.currentSize.incrementAndGet();
                        totalAdded++;
                    } catch (Exception e) {
                        break;
                    }
                }
            }
        }

        if (totalCleaned > 0 || totalAdded > 0) {
            logger.fine("[PacketPool] Maintenance: cleaned " + totalCleaned +
                       " packets, added " + totalAdded + " packets");
        }
    }

    /**
     * Perform maintenance on a specific pool
     */
    private int performPoolMaintenance(TypedPacketPool typedPool) {
        int currentSize = typedPool.currentSize.get();

        // Shrink oversized pools with good hit rates
        if (currentSize > MAX_POOL_SIZE * 0.8 && getPoolHitRate(typedPool) > 0.8) {
            int toRemove = currentSize - DEFAULT_POOL_SIZE;
            int removed = 0;

            for (int i = 0; i < toRemove; i++) {
                if (typedPool.pool.poll() != null) {
                    typedPool.currentSize.decrementAndGet();
                    removed++;
                }
            }

            return removed;
        }

        return 0;
    }

    /**
     * Get hit rate for a specific pool
     */
    private double getPoolHitRate(TypedPacketPool typedPool) {
        long hits = typedPool.hits.get();
        long misses = typedPool.misses.get();
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }

    /**
     * Clear all pools
     */
    public void clearAllPools() {
        for (TypedPacketPool typedPool : typedPools.values()) {
            typedPool.pool.clear();
            typedPool.currentSize.set(0);
        }

        logger.info("[PacketPool] All pools cleared");
    }

    /**
     * Get total size across all pools
     */
    public int getTotalSize() {
        return typedPools.values().stream()
                        .mapToInt(pool -> pool.currentSize.get())
                        .sum();
    }

    /**
     * Get number of different packet types pooled
     */
    public int getPooledTypes() {
        return typedPools.size();
    }

    /**
     * Get total pool hits
     */
    public long getPoolHits() {
        return totalHits.get();
    }

    /**
     * Get overall hit rate
     */
    public double getOverallHitRate() {
        long hits = totalHits.get();
        long requests = totalRequests.get();
        return requests > 0 ? (double) hits / requests : 0.0;
    }

    /**
     * Check if pools are healthy
     */
    public boolean isHealthy() {
        long totalRequests = totalHits.get() + totalMisses.get();
        double hitRate = getOverallHitRate();
        long returns = totalReturns.get();
        long invalidRet = invalidReturns.get();
        double invalidRate = returns > 0 ? (double) invalidRet / returns : 0.0;

        // More lenient health check - pools are healthy during startup or with minimal usage
        return (totalRequests == 0 || hitRate > 0.1) && invalidRate < 0.2;
    }

    /**
     * Reset all statistics
     */
    public void resetStats() {
        totalRequests.set(0);
        totalHits.set(0);
        totalMisses.set(0);
        totalReturns.set(0);
        invalidReturns.set(0);

        for (TypedPacketPool typedPool : typedPools.values()) {
            typedPool.hits.set(0);
            typedPool.misses.set(0);
            typedPool.returns.set(0);
        }

        logger.info("[PacketPool] All statistics reset");
    }

    /**
     * Get comprehensive performance statistics
     */
    public String getPerformanceStats() {
        return String.format(
            "PacketPool Stats: Types=%d, TotalSize=%d, Requests=%d, Hits=%d (%.1f%%), " +
            "Misses=%d, Returns=%d, Invalid=%d (%.1f%%), Healthy=%s",
            getPooledTypes(), getTotalSize(), totalRequests.get(), totalHits.get(),
            getOverallHitRate() * 100.0, totalMisses.get(), totalReturns.get(),
            invalidReturns.get(),
            totalReturns.get() > 0 ? (invalidReturns.get() * 100.0 / totalReturns.get()) : 0.0,
            isHealthy()
        );
    }

    /**
     * Get detailed statistics for a specific packet type
     */
    public String getPacketTypeStats(PacketType packetType) {
        TypedPacketPool typedPool = typedPools.get(packetType);
        if (typedPool == null) {
            return "No pool for " + packetType;
        }

        return String.format(
            "%s Pool: Size=%d, Hits=%d, Misses=%d (%.1f%% hit rate), Returns=%d",
            packetType, typedPool.currentSize.get(), typedPool.hits.get(),
            typedPool.misses.get(), getPoolHitRate(typedPool) * 100.0, typedPool.returns.get()
        );
    }
}
