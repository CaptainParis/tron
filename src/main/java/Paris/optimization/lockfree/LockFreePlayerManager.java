package Paris.optimization.lockfree;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Lock-free player state management using atomic references and copy-on-write pattern.
 * Provides thread-safe operations without blocking for high-performance concurrent access.
 */
public class LockFreePlayerManager {
    
    private static final Logger logger = Logger.getLogger(LockFreePlayerManager.class.getName());
    
    // Player state data structure
    public static class PlayerState {
        public final UUID playerId;
        public final Location lastLocation;
        public final Material trailMaterial;
        public final boolean isActive;
        public final boolean isAlive;
        public final long lastUpdate;
        public final int trailCount;
        
        public PlayerState(UUID playerId, Location lastLocation, Material trailMaterial, 
                          boolean isActive, boolean isAlive, long lastUpdate, int trailCount) {
            this.playerId = playerId;
            this.lastLocation = lastLocation;
            this.trailMaterial = trailMaterial;
            this.isActive = isActive;
            this.isAlive = isAlive;
            this.lastUpdate = lastUpdate;
            this.trailCount = trailCount;
        }
        
        public PlayerState withLocation(Location newLocation) {
            return new PlayerState(playerId, newLocation, trailMaterial, isActive, isAlive, 
                                 System.currentTimeMillis(), trailCount);
        }
        
        public PlayerState withTrailMaterial(Material newMaterial) {
            return new PlayerState(playerId, lastLocation, newMaterial, isActive, isAlive, 
                                 lastUpdate, trailCount);
        }
        
        public PlayerState withActiveStatus(boolean newActive) {
            return new PlayerState(playerId, lastLocation, trailMaterial, newActive, isAlive, 
                                 lastUpdate, trailCount);
        }
        
        public PlayerState withAliveStatus(boolean newAlive) {
            return new PlayerState(playerId, lastLocation, trailMaterial, isActive, newAlive, 
                                 lastUpdate, trailCount);
        }
        
        public PlayerState withIncrementedTrailCount() {
            return new PlayerState(playerId, lastLocation, trailMaterial, isActive, isAlive, 
                                 lastUpdate, trailCount + 1);
        }
    }
    
    // Lock-free state storage using atomic reference with copy-on-write
    private final AtomicReference<Map<UUID, PlayerState>> playerStates;
    
    // Performance metrics
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong casRetries = new AtomicLong(0);
    private final AtomicLong maxRetries = new AtomicLong(0);
    
    public LockFreePlayerManager() {
        this.playerStates = new AtomicReference<>(new HashMap<>());
        logger.info("[LockFreePlayerManager] Initialized with lock-free player state management");
    }
    
    /**
     * Add or update a player state atomically
     */
    public boolean updatePlayerState(UUID playerId, PlayerState newState) {
        totalOperations.incrementAndGet();
        int retryCount = 0;
        
        while (true) {
            Map<UUID, PlayerState> currentStates = playerStates.get();
            Map<UUID, PlayerState> newStates = new HashMap<>(currentStates);
            newStates.put(playerId, newState);
            
            if (playerStates.compareAndSet(currentStates, newStates)) {
                logger.fine("[LockFreePlayerManager] Successfully updated player " + playerId + 
                           " after " + retryCount + " retries");
                updateRetryStats(retryCount);
                return true;
            }
            
            retryCount++;
            casRetries.incrementAndGet();
            
            // Prevent infinite loops with exponential backoff
            if (retryCount > 100) {
                logger.warning("[LockFreePlayerManager] Failed to update player " + playerId + 
                              " after " + retryCount + " retries - giving up");
                return false;
            }
            
            // Brief pause to reduce contention
            if (retryCount > 10) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }
    
    /**
     * Get player state (always returns current snapshot)
     */
    public PlayerState getPlayerState(UUID playerId) {
        return playerStates.get().get(playerId);
    }
    
    /**
     * Remove player atomically
     */
    public boolean removePlayer(UUID playerId) {
        totalOperations.incrementAndGet();
        int retryCount = 0;
        
        while (true) {
            Map<UUID, PlayerState> currentStates = playerStates.get();
            if (!currentStates.containsKey(playerId)) {
                return true; // Already removed
            }
            
            Map<UUID, PlayerState> newStates = new HashMap<>(currentStates);
            newStates.remove(playerId);
            
            if (playerStates.compareAndSet(currentStates, newStates)) {
                logger.fine("[LockFreePlayerManager] Successfully removed player " + playerId + 
                           " after " + retryCount + " retries");
                updateRetryStats(retryCount);
                return true;
            }
            
            retryCount++;
            casRetries.incrementAndGet();
            
            if (retryCount > 100) {
                logger.warning("[LockFreePlayerManager] Failed to remove player " + playerId + 
                              " after " + retryCount + " retries - giving up");
                return false;
            }
        }
    }
    
    /**
     * Get all active players (returns snapshot)
     */
    public Set<UUID> getActivePlayers() {
        Map<UUID, PlayerState> currentStates = playerStates.get();
        Set<UUID> activePlayers = new HashSet<>();
        
        for (Map.Entry<UUID, PlayerState> entry : currentStates.entrySet()) {
            if (entry.getValue().isActive) {
                activePlayers.add(entry.getKey());
            }
        }
        
        return activePlayers;
    }
    
    /**
     * Get all alive players (returns snapshot)
     */
    public Set<UUID> getAlivePlayers() {
        Map<UUID, PlayerState> currentStates = playerStates.get();
        Set<UUID> alivePlayers = new HashSet<>();
        
        for (Map.Entry<UUID, PlayerState> entry : currentStates.entrySet()) {
            if (entry.getValue().isAlive) {
                alivePlayers.add(entry.getKey());
            }
        }
        
        return alivePlayers;
    }
    
    /**
     * Batch update multiple players atomically
     */
    public boolean batchUpdatePlayers(Map<UUID, PlayerState> updates) {
        totalOperations.incrementAndGet();
        int retryCount = 0;
        
        while (true) {
            Map<UUID, PlayerState> currentStates = playerStates.get();
            Map<UUID, PlayerState> newStates = new HashMap<>(currentStates);
            newStates.putAll(updates);
            
            if (playerStates.compareAndSet(currentStates, newStates)) {
                logger.fine("[LockFreePlayerManager] Successfully batch updated " + updates.size() + 
                           " players after " + retryCount + " retries");
                updateRetryStats(retryCount);
                return true;
            }
            
            retryCount++;
            casRetries.incrementAndGet();
            
            if (retryCount > 100) {
                logger.warning("[LockFreePlayerManager] Failed to batch update " + updates.size() + 
                              " players after " + retryCount + " retries - giving up");
                return false;
            }
        }
    }
    
    /**
     * Clear all players atomically
     */
    public void clearAllPlayers() {
        playerStates.set(new HashMap<>());
        logger.info("[LockFreePlayerManager] Cleared all player states");
    }
    
    /**
     * Get current player count
     */
    public int getPlayerCount() {
        return playerStates.get().size();
    }
    
    /**
     * Update retry statistics
     */
    private void updateRetryStats(int retryCount) {
        if (retryCount > 0) {
            long currentMax = maxRetries.get();
            while (retryCount > currentMax && !maxRetries.compareAndSet(currentMax, retryCount)) {
                currentMax = maxRetries.get();
            }
        }
    }
    
    /**
     * Get performance statistics
     */
    public String getPerformanceStats() {
        long operations = totalOperations.get();
        long retries = casRetries.get();
        long maxRetry = maxRetries.get();
        
        return String.format(
            "LockFreePlayerManager Stats: Operations=%d, CAS Retries=%d (%.2f%%), Max Retries=%d, Players=%d",
            operations, retries, operations > 0 ? (retries * 100.0 / operations) : 0.0, maxRetry, getPlayerCount()
        );
    }
}
