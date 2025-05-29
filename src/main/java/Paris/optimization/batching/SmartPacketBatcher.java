package Paris.optimization.batching;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Smart packet batching system with adaptive sizing and time-based triggers.
 * Optimizes network performance by batching multiple packets into single transmissions.
 */
public class SmartPacketBatcher {

    private static final Logger logger = Logger.getLogger(SmartPacketBatcher.class.getName());

    // Batching configuration
    private static final int DEFAULT_TRAIL_BATCH_SIZE = 25;
    private static final int DEFAULT_BULK_BATCH_SIZE = 50;
    private static final long DEFAULT_MAX_DELAY_MS = 50; // 1 tick
    private static final int DEFAULT_PRIORITY_THRESHOLD = 10;

    // Packet batch data structure
    public static class PacketBatch {
        public final List<PacketContainer> packets;
        public final long creationTime;
        public final PacketType primaryType;
        public final int priority;

        public PacketBatch(PacketType primaryType, int priority) {
            this.packets = new ArrayList<>();
            this.creationTime = System.currentTimeMillis();
            this.primaryType = primaryType;
            this.priority = priority;
        }

        public void addPacket(PacketContainer packet) {
            packets.add(packet);
        }

        public int size() {
            return packets.size();
        }

        public long getAge() {
            return System.currentTimeMillis() - creationTime;
        }

        public boolean isReady(int maxSize, long maxDelay) {
            return packets.size() >= maxSize || getAge() >= maxDelay;
        }
    }

    // Per-player packet queues
    private final Map<UUID, Map<PacketType, PacketBatch>> playerBatches;
    private final Map<UUID, Queue<PacketBatch>> priorityQueues;

    // Global batching queues for broadcast packets
    private final Map<PacketType, PacketBatch> globalBatches;
    private final Queue<PacketBatch> globalPriorityQueue;

    // Configuration
    private final Plugin plugin;
    private final ProtocolManager protocolManager;
    private final Map<PacketType, Integer> batchSizes;
    private final long maxDelayMs;
    private final int priorityThreshold;

    // Performance tracking
    private final AtomicLong totalPacketsQueued = new AtomicLong(0);
    private final AtomicLong totalPacketsSent = new AtomicLong(0);
    private final AtomicLong totalBatchesSent = new AtomicLong(0);
    private final AtomicLong totalBytesReduced = new AtomicLong(0);
    private final AtomicInteger currentQueueSize = new AtomicInteger(0);

    // Flush task
    private BukkitTask flushTask;

    public SmartPacketBatcher(Plugin plugin, ProtocolManager protocolManager) {
        this.plugin = plugin;
        this.protocolManager = protocolManager;
        this.playerBatches = new ConcurrentHashMap<>();
        this.priorityQueues = new ConcurrentHashMap<>();
        this.globalBatches = new ConcurrentHashMap<>();
        this.globalPriorityQueue = new ConcurrentLinkedQueue<>();
        this.maxDelayMs = DEFAULT_MAX_DELAY_MS;
        this.priorityThreshold = DEFAULT_PRIORITY_THRESHOLD;

        // Initialize batch sizes for different packet types
        this.batchSizes = new HashMap<>();
        batchSizes.put(PacketType.Play.Server.BLOCK_CHANGE, DEFAULT_TRAIL_BATCH_SIZE);
        batchSizes.put(PacketType.Play.Server.MULTI_BLOCK_CHANGE, DEFAULT_BULK_BATCH_SIZE);
        batchSizes.put(PacketType.Play.Server.ENTITY_TELEPORT, 15);
        batchSizes.put(PacketType.Play.Server.ENTITY_DESTROY, 30);

        startFlushTask();
        logger.info("[SmartPacketBatcher] Initialized with maxDelay=" + maxDelayMs + "ms");
    }

    /**
     * Queue a packet for batching
     */
    public boolean queuePacket(Player player, PacketContainer packet, int priority) {
        if (player == null || packet == null) {
            return false;
        }

        totalPacketsQueued.incrementAndGet();
        currentQueueSize.incrementAndGet();

        PacketType packetType = packet.getType();
        UUID playerId = player.getUniqueId();

        // Handle high-priority packets immediately
        if (priority >= priorityThreshold) {
            sendPacketImmediately(player, packet);
            return true;
        }

        // Get or create batch for this player and packet type
        Map<PacketType, PacketBatch> playerTypeBatches = playerBatches.computeIfAbsent(
            playerId, k -> new ConcurrentHashMap<>()
        );

        PacketBatch batch = playerTypeBatches.computeIfAbsent(
            packetType, k -> new PacketBatch(packetType, priority)
        );

        synchronized (batch) {
            batch.addPacket(packet);

            // Check if batch is ready to send
            int maxSize = batchSizes.getOrDefault(packetType, DEFAULT_BULK_BATCH_SIZE);
            if (batch.isReady(maxSize, maxDelayMs)) {
                // Move to priority queue for immediate sending
                Queue<PacketBatch> playerQueue = priorityQueues.computeIfAbsent(
                    playerId, k -> new ConcurrentLinkedQueue<>()
                );
                playerQueue.offer(batch);
                playerTypeBatches.remove(packetType);

                // Send immediately if high priority
                if (priority > 0) {
                    flushPlayerQueue(player);
                }
            }
        }

        logger.finest("[SmartPacketBatcher] Queued " + packetType + " packet for player " +
                     player.getName() + " (batch size: " + batch.size() + ")");

        return true;
    }

    /**
     * Queue a packet for broadcast to all players
     */
    public boolean queueBroadcastPacket(PacketContainer packet, int priority) {
        if (packet == null) {
            return false;
        }

        totalPacketsQueued.incrementAndGet();
        PacketType packetType = packet.getType();

        // Handle high-priority packets immediately
        if (priority >= priorityThreshold) {
            broadcastPacketImmediately(packet);
            return true;
        }

        PacketBatch batch = globalBatches.computeIfAbsent(
            packetType, k -> new PacketBatch(packetType, priority)
        );

        synchronized (batch) {
            batch.addPacket(packet);

            // Check if batch is ready to send
            int maxSize = batchSizes.getOrDefault(packetType, DEFAULT_BULK_BATCH_SIZE);
            if (batch.isReady(maxSize, maxDelayMs)) {
                globalPriorityQueue.offer(batch);
                globalBatches.remove(packetType);

                // Send immediately if high priority
                if (priority > 0) {
                    flushGlobalQueue();
                }
            }
        }

        return true;
    }

    /**
     * Send packet immediately without batching
     */
    private void sendPacketImmediately(Player player, PacketContainer packet) {
        try {
            protocolManager.sendServerPacket(player, packet);
            totalPacketsSent.incrementAndGet();
            currentQueueSize.decrementAndGet();

            logger.finest("[SmartPacketBatcher] Sent immediate packet " + packet.getType() +
                         " to player " + player.getName());
        } catch (Exception e) {
            logger.warning("[SmartPacketBatcher] Failed to send immediate packet: " + e.getMessage());
        }
    }

    /**
     * Broadcast packet immediately to all online players
     */
    private void broadcastPacketImmediately(PacketContainer packet) {
        try {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                protocolManager.sendServerPacket(player, packet);
                totalPacketsSent.incrementAndGet();
            }

            logger.finest("[SmartPacketBatcher] Broadcast immediate packet " + packet.getType() +
                         " to " + plugin.getServer().getOnlinePlayers().size() + " players");
        } catch (Exception e) {
            logger.warning("[SmartPacketBatcher] Failed to broadcast immediate packet: " + e.getMessage());
        }
    }

    /**
     * Flush all batches for a specific player
     */
    public void flushPlayerQueue(Player player) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        Queue<PacketBatch> playerQueue = priorityQueues.get(playerId);

        if (playerQueue != null) {
            PacketBatch batch;
            while ((batch = playerQueue.poll()) != null) {
                sendBatch(player, batch);
            }
        }

        // Also flush any pending batches
        Map<PacketType, PacketBatch> playerTypeBatches = playerBatches.get(playerId);
        if (playerTypeBatches != null) {
            for (PacketBatch batch : new ArrayList<>(playerTypeBatches.values())) {
                if (batch.size() > 0) {
                    sendBatch(player, batch);
                }
            }
            playerTypeBatches.clear();
        }
    }

    /**
     * Flush global broadcast queue
     */
    public void flushGlobalQueue() {
        PacketBatch batch;
        while ((batch = globalPriorityQueue.poll()) != null) {
            broadcastBatch(batch);
        }

        // Also flush any pending global batches
        for (PacketBatch pendingBatch : new ArrayList<>(globalBatches.values())) {
            if (pendingBatch.size() > 0) {
                broadcastBatch(pendingBatch);
            }
        }
        globalBatches.clear();
    }

    /**
     * Send a batch to a specific player
     */
    private void sendBatch(Player player, PacketBatch batch) {
        try {
            for (PacketContainer packet : batch.packets) {
                protocolManager.sendServerPacket(player, packet);
            }

            totalPacketsSent.addAndGet(batch.size());
            totalBatchesSent.incrementAndGet();
            currentQueueSize.addAndGet(-batch.size());

            // Calculate bytes saved by batching
            long bytesSaved = estimateBytesSaved(batch);
            totalBytesReduced.addAndGet(bytesSaved);

            logger.fine("[SmartPacketBatcher] Sent batch of " + batch.size() + " " +
                       batch.primaryType + " packets to " + player.getName() +
                       " (age: " + batch.getAge() + "ms)");

        } catch (Exception e) {
            logger.warning("[SmartPacketBatcher] Failed to send batch to " + player.getName() +
                          ": " + e.getMessage());
        }
    }

    /**
     * Broadcast a batch to all online players
     */
    private void broadcastBatch(PacketBatch batch) {
        try {
            Collection<? extends Player> onlinePlayers = plugin.getServer().getOnlinePlayers();

            for (Player player : onlinePlayers) {
                for (PacketContainer packet : batch.packets) {
                    protocolManager.sendServerPacket(player, packet);
                }
            }

            long totalPackets = (long) batch.size() * onlinePlayers.size();
            totalPacketsSent.addAndGet(totalPackets);
            totalBatchesSent.incrementAndGet();

            logger.fine("[SmartPacketBatcher] Broadcast batch of " + batch.size() + " " +
                       batch.primaryType + " packets to " + onlinePlayers.size() + " players");

        } catch (Exception e) {
            logger.warning("[SmartPacketBatcher] Failed to broadcast batch: " + e.getMessage());
        }
    }

    /**
     * Estimate bytes saved by batching (rough calculation)
     */
    private long estimateBytesSaved(PacketBatch batch) {
        // Rough estimate: each packet has ~20 bytes of overhead
        // Batching reduces this to ~5 bytes per packet in batch
        return (batch.size() - 1) * 15;
    }

    /**
     * Start the automatic flush task
     */
    private void startFlushTask() {
        flushTask = new BukkitRunnable() {
            @Override
            public void run() {
                flushAllQueues();
            }
        }.runTaskTimer(plugin, 1L, 1L); // Every tick

        logger.info("[SmartPacketBatcher] Started automatic flush task");
    }

    /**
     * Flush all queues (called by timer)
     */
    private void flushAllQueues() {
        long currentTime = System.currentTimeMillis();

        // Flush player queues
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();

            // Check priority queue
            Queue<PacketBatch> playerQueue = priorityQueues.get(playerId);
            if (playerQueue != null && !playerQueue.isEmpty()) {
                flushPlayerQueue(player);
            }

            // Check for aged batches
            Map<PacketType, PacketBatch> playerTypeBatches = playerBatches.get(playerId);
            if (playerTypeBatches != null) {
                for (PacketBatch batch : new ArrayList<>(playerTypeBatches.values())) {
                    if (batch.getAge() >= maxDelayMs) {
                        sendBatch(player, batch);
                        playerTypeBatches.remove(batch.primaryType);
                    }
                }
            }
        }

        // Flush global queue
        if (!globalPriorityQueue.isEmpty()) {
            flushGlobalQueue();
        }

        // Check for aged global batches
        for (PacketBatch batch : new ArrayList<>(globalBatches.values())) {
            if (batch.getAge() >= maxDelayMs) {
                broadcastBatch(batch);
                globalBatches.remove(batch.primaryType);
            }
        }
    }

    /**
     * Cleanup resources for disconnected player
     */
    public void cleanupPlayer(UUID playerId) {
        playerBatches.remove(playerId);
        priorityQueues.remove(playerId);
        logger.fine("[SmartPacketBatcher] Cleaned up batches for player " + playerId);
    }

    /**
     * Shutdown the batcher
     */
    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
        }

        // Flush all remaining packets
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            flushPlayerQueue(player);
        }
        flushGlobalQueue();

        logger.info("[SmartPacketBatcher] Shutdown complete");
    }

    /**
     * Get batches saved count
     */
    public long getBatchesSaved() {
        long queued = totalPacketsQueued.get();
        long batches = totalBatchesSent.get();
        return Math.max(0, queued - batches);
    }

    /**
     * Get performance statistics
     */
    public String getPerformanceStats() {
        long queued = totalPacketsQueued.get();
        long sent = totalPacketsSent.get();
        long batches = totalBatchesSent.get();
        long bytesReduced = totalBytesReduced.get();

        double batchingEfficiency = queued > 0 ? (double) batches / queued * 100.0 : 0.0;
        double averageBatchSize = batches > 0 ? (double) sent / batches : 0.0;

        return String.format(
            "SmartPacketBatcher Stats: Queued=%d, Sent=%d, Batches=%d, Efficiency=%.1f%%, " +
            "AvgBatchSize=%.1f, BytesReduced=%d, QueueSize=%d",
            queued, sent, batches, batchingEfficiency, averageBatchSize, bytesReduced,
            currentQueueSize.get()
        );
    }
}
