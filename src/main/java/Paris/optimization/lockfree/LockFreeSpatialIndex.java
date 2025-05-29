package Paris.optimization.lockfree;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Lock-free spatial indexing system for high-performance collision detection.
 * Uses chunk-based partitioning with atomic operations for thread-safe access.
 */
public class LockFreeSpatialIndex {
    
    private static final Logger logger = Logger.getLogger(LockFreeSpatialIndex.class.getName());
    
    // Spatial data structure
    public static class SpatialEntry {
        public final Location location;
        public final UUID playerId;
        public final Material material;
        public final long timestamp;
        public final String chunkKey;
        public final String blockKey;
        
        public SpatialEntry(Location location, UUID playerId, Material material) {
            this.location = location.clone();
            this.playerId = playerId;
            this.material = material;
            this.timestamp = System.currentTimeMillis();
            this.chunkKey = generateChunkKey(location);
            this.blockKey = generateBlockKey(location);
        }
        
        public static String generateChunkKey(Location location) {
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            return chunkX + "," + chunkZ;
        }
        
        public static String generateBlockKey(Location location) {
            return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof SpatialEntry)) return false;
            SpatialEntry other = (SpatialEntry) obj;
            return blockKey.equals(other.blockKey);
        }
        
        @Override
        public int hashCode() {
            return blockKey.hashCode();
        }
    }
    
    // Lock-free spatial index using atomic references
    private final AtomicReference<Map<String, Map<String, SpatialEntry>>> spatialIndex;
    private final AtomicReference<Map<UUID, Set<SpatialEntry>>> playerIndex;
    
    // Performance tracking
    private final AtomicLong totalEntries = new AtomicLong(0);
    private final AtomicLong totalLookups = new AtomicLong(0);
    private final AtomicLong totalInserts = new AtomicLong(0);
    private final AtomicLong totalRemovals = new AtomicLong(0);
    private final AtomicLong casRetries = new AtomicLong(0);
    
    // Configuration
    private final int maxEntriesPerPlayer;
    private final long entryTimeoutMs;
    
    public LockFreeSpatialIndex(int maxEntriesPerPlayer, long entryTimeoutMs) {
        this.spatialIndex = new AtomicReference<>(new HashMap<>());
        this.playerIndex = new AtomicReference<>(new HashMap<>());
        this.maxEntriesPerPlayer = maxEntriesPerPlayer;
        this.entryTimeoutMs = entryTimeoutMs;
        
        logger.info("[LockFreeSpatialIndex] Initialized with maxEntriesPerPlayer=" + 
                   maxEntriesPerPlayer + ", entryTimeoutMs=" + entryTimeoutMs);
    }
    
    /**
     * Add a spatial entry atomically
     */
    public boolean addEntry(Location location, UUID playerId, Material material) {
        SpatialEntry entry = new SpatialEntry(location, playerId, material);
        totalInserts.incrementAndGet();
        
        // Update spatial index
        if (!updateSpatialIndex(entry, true)) {
            return false;
        }
        
        // Update player index
        if (!updatePlayerIndex(entry, true)) {
            // Rollback spatial index update
            updateSpatialIndex(entry, false);
            return false;
        }
        
        totalEntries.incrementAndGet();
        logger.fine("[LockFreeSpatialIndex] Added entry for player " + playerId + 
                   " at " + entry.blockKey + " in chunk " + entry.chunkKey);
        
        // Enforce player limits
        enforcePlayerLimits(playerId);
        
        return true;
    }
    
    /**
     * Remove a spatial entry atomically
     */
    public boolean removeEntry(Location location, UUID playerId) {
        String chunkKey = SpatialEntry.generateChunkKey(location);
        String blockKey = SpatialEntry.generateBlockKey(location);
        totalRemovals.incrementAndGet();
        
        // Find the entry first
        SpatialEntry entryToRemove = null;
        Map<String, Map<String, SpatialEntry>> currentSpatial = spatialIndex.get();
        Map<String, SpatialEntry> chunkEntries = currentSpatial.get(chunkKey);
        if (chunkEntries != null) {
            entryToRemove = chunkEntries.get(blockKey);
        }
        
        if (entryToRemove == null || !entryToRemove.playerId.equals(playerId)) {
            return false; // Entry not found or wrong player
        }
        
        // Remove from spatial index
        if (!updateSpatialIndex(entryToRemove, false)) {
            return false;
        }
        
        // Remove from player index
        if (!updatePlayerIndex(entryToRemove, false)) {
            // Rollback spatial index update
            updateSpatialIndex(entryToRemove, true);
            return false;
        }
        
        totalEntries.decrementAndGet();
        logger.fine("[LockFreeSpatialIndex] Removed entry for player " + playerId + 
                   " at " + blockKey + " in chunk " + chunkKey);
        
        return true;
    }
    
    /**
     * Check for collision at location (O(1) lookup)
     */
    public SpatialEntry checkCollision(Location location, UUID excludePlayerId) {
        totalLookups.incrementAndGet();
        
        String chunkKey = SpatialEntry.generateChunkKey(location);
        String blockKey = SpatialEntry.generateBlockKey(location);
        
        Map<String, Map<String, SpatialEntry>> currentSpatial = spatialIndex.get();
        Map<String, SpatialEntry> chunkEntries = currentSpatial.get(chunkKey);
        
        if (chunkEntries == null) {
            return null; // No entries in this chunk
        }
        
        SpatialEntry entry = chunkEntries.get(blockKey);
        if (entry == null) {
            return null; // No entry at this location
        }
        
        // Check if it's the same player (no collision with own trail)
        if (entry.playerId.equals(excludePlayerId)) {
            return null;
        }
        
        // Check entry timeout
        if (System.currentTimeMillis() - entry.timestamp > entryTimeoutMs) {
            // Entry is too old, remove it asynchronously
            removeEntry(location, entry.playerId);
            return null;
        }
        
        logger.fine("[LockFreeSpatialIndex] Collision detected at " + blockKey + 
                   " between player " + excludePlayerId + " and " + entry.playerId);
        
        return entry;
    }
    
    /**
     * Get all entries for a player
     */
    public Set<SpatialEntry> getPlayerEntries(UUID playerId) {
        Map<UUID, Set<SpatialEntry>> currentPlayer = playerIndex.get();
        Set<SpatialEntry> entries = currentPlayer.get(playerId);
        return entries != null ? new HashSet<>(entries) : new HashSet<>();
    }
    
    /**
     * Remove all entries for a player
     */
    public boolean removePlayerEntries(UUID playerId) {
        Set<SpatialEntry> playerEntries = getPlayerEntries(playerId);
        
        for (SpatialEntry entry : playerEntries) {
            if (!removeEntry(entry.location, playerId)) {
                logger.warning("[LockFreeSpatialIndex] Failed to remove entry for player " + playerId);
                return false;
            }
        }
        
        logger.info("[LockFreeSpatialIndex] Removed " + playerEntries.size() + 
                   " entries for player " + playerId);
        return true;
    }
    
    /**
     * Update spatial index atomically
     */
    private boolean updateSpatialIndex(SpatialEntry entry, boolean add) {
        int retryCount = 0;
        
        while (retryCount < 100) {
            Map<String, Map<String, SpatialEntry>> currentSpatial = spatialIndex.get();
            Map<String, Map<String, SpatialEntry>> newSpatial = new HashMap<>(currentSpatial);
            
            if (add) {
                newSpatial.computeIfAbsent(entry.chunkKey, k -> new HashMap<>())
                         .put(entry.blockKey, entry);
            } else {
                Map<String, SpatialEntry> chunkEntries = newSpatial.get(entry.chunkKey);
                if (chunkEntries != null) {
                    chunkEntries.remove(entry.blockKey);
                    if (chunkEntries.isEmpty()) {
                        newSpatial.remove(entry.chunkKey);
                    }
                }
            }
            
            if (spatialIndex.compareAndSet(currentSpatial, newSpatial)) {
                return true;
            }
            
            retryCount++;
            casRetries.incrementAndGet();
        }
        
        logger.warning("[LockFreeSpatialIndex] Failed to update spatial index after 100 retries");
        return false;
    }
    
    /**
     * Update player index atomically
     */
    private boolean updatePlayerIndex(SpatialEntry entry, boolean add) {
        int retryCount = 0;
        
        while (retryCount < 100) {
            Map<UUID, Set<SpatialEntry>> currentPlayer = playerIndex.get();
            Map<UUID, Set<SpatialEntry>> newPlayer = new HashMap<>(currentPlayer);
            
            if (add) {
                newPlayer.computeIfAbsent(entry.playerId, k -> new HashSet<>())
                        .add(entry);
            } else {
                Set<SpatialEntry> playerEntries = newPlayer.get(entry.playerId);
                if (playerEntries != null) {
                    playerEntries.remove(entry);
                    if (playerEntries.isEmpty()) {
                        newPlayer.remove(entry.playerId);
                    }
                }
            }
            
            if (playerIndex.compareAndSet(currentPlayer, newPlayer)) {
                return true;
            }
            
            retryCount++;
            casRetries.incrementAndGet();
        }
        
        logger.warning("[LockFreeSpatialIndex] Failed to update player index after 100 retries");
        return false;
    }
    
    /**
     * Enforce per-player entry limits
     */
    private void enforcePlayerLimits(UUID playerId) {
        Set<SpatialEntry> playerEntries = getPlayerEntries(playerId);
        
        if (playerEntries.size() > maxEntriesPerPlayer) {
            // Remove oldest entries
            List<SpatialEntry> sortedEntries = new ArrayList<>(playerEntries);
            sortedEntries.sort(Comparator.comparingLong(e -> e.timestamp));
            
            int toRemove = playerEntries.size() - maxEntriesPerPlayer;
            for (int i = 0; i < toRemove; i++) {
                SpatialEntry oldEntry = sortedEntries.get(i);
                removeEntry(oldEntry.location, playerId);
            }
            
            logger.fine("[LockFreeSpatialIndex] Removed " + toRemove + 
                       " old entries for player " + playerId);
        }
    }
    
    /**
     * Cleanup expired entries
     */
    public int cleanupExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;
        
        Map<UUID, Set<SpatialEntry>> currentPlayer = playerIndex.get();
        for (Map.Entry<UUID, Set<SpatialEntry>> playerEntry : currentPlayer.entrySet()) {
            UUID playerId = playerEntry.getKey();
            Set<SpatialEntry> entries = new HashSet<>(playerEntry.getValue());
            
            for (SpatialEntry entry : entries) {
                if (currentTime - entry.timestamp > entryTimeoutMs) {
                    if (removeEntry(entry.location, playerId)) {
                        removedCount++;
                    }
                }
            }
        }
        
        if (removedCount > 0) {
            logger.info("[LockFreeSpatialIndex] Cleaned up " + removedCount + " expired entries");
        }
        
        return removedCount;
    }
    
    /**
     * Clear all entries
     */
    public void clear() {
        spatialIndex.set(new HashMap<>());
        playerIndex.set(new HashMap<>());
        totalEntries.set(0);
        logger.info("[LockFreeSpatialIndex] All entries cleared");
    }
    
    /**
     * Get performance statistics
     */
    public String getPerformanceStats() {
        return String.format(
            "LockFreeSpatialIndex Stats: Entries=%d, Lookups=%d, Inserts=%d, Removals=%d, CAS Retries=%d",
            totalEntries.get(), totalLookups.get(), totalInserts.get(), 
            totalRemovals.get(), casRetries.get()
        );
    }
    
    // Getters
    public long getTotalEntries() { return totalEntries.get(); }
    public long getTotalLookups() { return totalLookups.get(); }
    public long getTotalInserts() { return totalInserts.get(); }
    public long getTotalRemovals() { return totalRemovals.get(); }
    public long getCasRetries() { return casRetries.get(); }
}
