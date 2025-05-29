package Paris.optimization.lockfree;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Lock-free game statistics tracking using atomic operations.
 * Provides high-performance counters for game metrics without synchronization overhead.
 */

public class LockFreeGameStats {
    
    private static final Logger logger = Logger.getLogger(LockFreeGameStats.class.getName());
    
    // Game statistics
    private final AtomicLong gamesPlayed = new AtomicLong(0);
    private final AtomicLong totalCollisions = new AtomicLong(0);
    private final AtomicLong trailBlocksGenerated = new AtomicLong(0);
    private final AtomicLong packetsGenerated = new AtomicLong(0);
    private final AtomicLong packetsBatched = new AtomicLong(0);
    
    // Performance metrics
    private final AtomicLong totalGameTime = new AtomicLong(0);
    private final AtomicLong shortestGame = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong longestGame = new AtomicLong(0);
    private final AtomicInteger currentPlayers = new AtomicInteger(0);
    private final AtomicInteger peakPlayers = new AtomicInteger(0);
    
    // Memory and performance tracking
    private final AtomicLong memoryAllocations = new AtomicLong(0);
    private final AtomicLong objectPoolHits = new AtomicLong(0);
    private final AtomicLong objectPoolMisses = new AtomicLong(0);
    private final AtomicLong gcCollections = new AtomicLong(0);
    
    // Timing statistics
    private final AtomicReference<TimingStats> collisionCheckTiming = new AtomicReference<>(new TimingStats());
    private final AtomicReference<TimingStats> trailGenerationTiming = new AtomicReference<>(new TimingStats());
    private final AtomicReference<TimingStats> packetBatchTiming = new AtomicReference<>(new TimingStats());
    
    // Timing data structure
    public static class TimingStats {
        public final long totalTime;
        public final long operationCount;
        public final long minTime;
        public final long maxTime;
        
        public TimingStats() {
            this(0, 0, Long.MAX_VALUE, 0);
        }
        
        public TimingStats(long totalTime, long operationCount, long minTime, long maxTime) {
            this.totalTime = totalTime;
            this.operationCount = operationCount;
            this.minTime = minTime;
            this.maxTime = maxTime;
        }
        
        public TimingStats addTiming(long duration) {
            return new TimingStats(
                totalTime + duration,
                operationCount + 1,
                Math.min(minTime, duration),
                Math.max(maxTime, duration)
            );
        }
        
        public double getAverageTime() {
            return operationCount > 0 ? (double) totalTime / operationCount : 0.0;
        }
    }
    
    public LockFreeGameStats() {
        logger.info("[LockFreeGameStats] Initialized lock-free game statistics tracking");
    }
    
    // Game event tracking
    public void recordGameStart() {
        gamesPlayed.incrementAndGet();
        logger.fine("[LockFreeGameStats] Game started - total games: " + gamesPlayed.get());
    }
    
    public void recordGameEnd(long gameDurationMs) {
        totalGameTime.addAndGet(gameDurationMs);
        
        // Update shortest game time
        long currentShortest = shortestGame.get();
        while (gameDurationMs < currentShortest && 
               !shortestGame.compareAndSet(currentShortest, gameDurationMs)) {
            currentShortest = shortestGame.get();
        }
        
        // Update longest game time
        long currentLongest = longestGame.get();
        while (gameDurationMs > currentLongest && 
               !longestGame.compareAndSet(currentLongest, gameDurationMs)) {
            currentLongest = longestGame.get();
        }
        
        logger.fine("[LockFreeGameStats] Game ended - duration: " + gameDurationMs + "ms");
    }
    
    public void recordCollision() {
        totalCollisions.incrementAndGet();
    }
    
    public void recordTrailBlock() {
        trailBlocksGenerated.incrementAndGet();
    }
    
    public void recordPacketGenerated() {
        packetsGenerated.incrementAndGet();
    }
    
    public void recordPacketBatched() {
        packetsBatched.incrementAndGet();
    }
    
    // Player tracking
    public void setCurrentPlayers(int count) {
        currentPlayers.set(count);
        
        // Update peak players
        int currentPeak = peakPlayers.get();
        while (count > currentPeak && !peakPlayers.compareAndSet(currentPeak, count)) {
            currentPeak = peakPlayers.get();
        }
    }
    
    public void incrementCurrentPlayers() {
        int newCount = currentPlayers.incrementAndGet();
        
        // Update peak players
        int currentPeak = peakPlayers.get();
        while (newCount > currentPeak && !peakPlayers.compareAndSet(currentPeak, newCount)) {
            currentPeak = peakPlayers.get();
        }
    }
    
    public void decrementCurrentPlayers() {
        currentPlayers.decrementAndGet();
    }
    
    // Memory tracking
    public void recordMemoryAllocation() {
        memoryAllocations.incrementAndGet();
    }
    
    public void recordObjectPoolHit() {
        objectPoolHits.incrementAndGet();
    }
    
    public void recordObjectPoolMiss() {
        objectPoolMisses.incrementAndGet();
    }
    
    public void recordGCCollection() {
        gcCollections.incrementAndGet();
    }
    
    // Timing tracking with lock-free updates
    public void recordCollisionCheckTime(long durationNanos) {
        updateTimingStats(collisionCheckTiming, durationNanos);
    }
    
    public void recordTrailGenerationTime(long durationNanos) {
        updateTimingStats(trailGenerationTiming, durationNanos);
    }
    
    public void recordPacketBatchTime(long durationNanos) {
        updateTimingStats(packetBatchTiming, durationNanos);
    }
    
    private void updateTimingStats(AtomicReference<TimingStats> statsRef, long duration) {
        while (true) {
            TimingStats current = statsRef.get();
            TimingStats updated = current.addTiming(duration);
            
            if (statsRef.compareAndSet(current, updated)) {
                break;
            }
            // Retry on CAS failure
        }
    }
    
    // Getters for statistics
    public long getGamesPlayed() { return gamesPlayed.get(); }
    public long getTotalCollisions() { return totalCollisions.get(); }
    public long getTrailBlocksGenerated() { return trailBlocksGenerated.get(); }
    public long getPacketsGenerated() { return packetsGenerated.get(); }
    public long getPacketsBatched() { return packetsBatched.get(); }
    public long getTotalGameTime() { return totalGameTime.get(); }
    public long getShortestGame() { 
        long shortest = shortestGame.get();
        return shortest == Long.MAX_VALUE ? 0 : shortest;
    }
    public long getLongestGame() { return longestGame.get(); }
    public int getCurrentPlayers() { return currentPlayers.get(); }
    public int getPeakPlayers() { return peakPlayers.get(); }
    public long getMemoryAllocations() { return memoryAllocations.get(); }
    public long getObjectPoolHits() { return objectPoolHits.get(); }
    public long getObjectPoolMisses() { return objectPoolMisses.get(); }
    public long getGCCollections() { return gcCollections.get(); }
    
    // Calculated metrics
    public double getAverageGameTime() {
        long games = gamesPlayed.get();
        return games > 0 ? (double) totalGameTime.get() / games : 0.0;
    }
    
    public double getPacketBatchingEfficiency() {
        long generated = packetsGenerated.get();
        long batched = packetsBatched.get();
        return generated > 0 ? (double) batched / generated * 100.0 : 0.0;
    }
    
    public double getObjectPoolHitRate() {
        long hits = objectPoolHits.get();
        long misses = objectPoolMisses.get();
        long total = hits + misses;
        return total > 0 ? (double) hits / total * 100.0 : 0.0;
    }
    
    public TimingStats getCollisionCheckTiming() { return collisionCheckTiming.get(); }
    public TimingStats getTrailGenerationTiming() { return trailGenerationTiming.get(); }
    public TimingStats getPacketBatchTiming() { return packetBatchTiming.get(); }
    
    /**
     * Reset all statistics
     */
    public void reset() {
        gamesPlayed.set(0);
        totalCollisions.set(0);
        trailBlocksGenerated.set(0);
        packetsGenerated.set(0);
        packetsBatched.set(0);
        totalGameTime.set(0);
        shortestGame.set(Long.MAX_VALUE);
        longestGame.set(0);
        currentPlayers.set(0);
        peakPlayers.set(0);
        memoryAllocations.set(0);
        objectPoolHits.set(0);
        objectPoolMisses.set(0);
        gcCollections.set(0);
        collisionCheckTiming.set(new TimingStats());
        trailGenerationTiming.set(new TimingStats());
        packetBatchTiming.set(new TimingStats());
        
        logger.info("[LockFreeGameStats] All statistics reset");
    }
    
    /**
     * Get comprehensive performance report
     */
    public String getPerformanceReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Lock-Free Game Statistics ===\n");
        report.append(String.format("Games Played: %d\n", getGamesPlayed()));
        report.append(String.format("Total Collisions: %d\n", getTotalCollisions()));
        report.append(String.format("Trail Blocks Generated: %d\n", getTrailBlocksGenerated()));
        report.append(String.format("Packets Generated: %d\n", getPacketsGenerated()));
        report.append(String.format("Packets Batched: %d (%.1f%% efficiency)\n", 
                     getPacketsBatched(), getPacketBatchingEfficiency()));
        report.append(String.format("Current Players: %d (Peak: %d)\n", 
                     getCurrentPlayers(), getPeakPlayers()));
        report.append(String.format("Average Game Time: %.1f seconds\n", getAverageGameTime() / 1000.0));
        report.append(String.format("Shortest Game: %.1f seconds\n", getShortestGame() / 1000.0));
        report.append(String.format("Longest Game: %.1f seconds\n", getLongestGame() / 1000.0));
        report.append(String.format("Object Pool Hit Rate: %.1f%%\n", getObjectPoolHitRate()));
        report.append(String.format("Memory Allocations: %d\n", getMemoryAllocations()));
        report.append(String.format("GC Collections: %d\n", getGCCollections()));
        
        TimingStats collisionTiming = getCollisionCheckTiming();
        if (collisionTiming.operationCount > 0) {
            report.append(String.format("Collision Check Timing: %.2fμs avg (%.2f-%.2fμs)\n",
                         collisionTiming.getAverageTime() / 1000.0,
                         collisionTiming.minTime / 1000.0,
                         collisionTiming.maxTime / 1000.0));
        }
        
        return report.toString();
    }
}
