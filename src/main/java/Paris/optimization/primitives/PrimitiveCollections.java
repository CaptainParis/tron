package Paris.optimization.primitives;

import it.unimi.dsi.fastutil.objects.*;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.*;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * High-performance primitive collections for Tron game data structures.
 * Uses FastUtil library to reduce memory usage and improve performance.
 */
public class PrimitiveCollections {
    
    private static final Logger logger = Logger.getLogger(PrimitiveCollections.class.getName());
    
    // Performance tracking
    private static final AtomicLong memoryReductionBytes = new AtomicLong(0);
    private static final AtomicLong operationCount = new AtomicLong(0);
    
    /**
     * High-performance player score tracking using primitive collections
     */
    public static class PlayerScoreMap {
        private final Object2IntOpenHashMap<UUID> scores;
        
        public PlayerScoreMap() {
            this.scores = new Object2IntOpenHashMap<>();
            this.scores.defaultReturnValue(0); // Return 0 for missing players
            
            logger.fine("[PlayerScoreMap] Initialized with primitive int values");
        }
        
        public void setScore(UUID playerId, int score) {
            scores.put(playerId, score);
            operationCount.incrementAndGet();
        }
        
        public int getScore(UUID playerId) {
            operationCount.incrementAndGet();
            return scores.getInt(playerId);
        }
        
        public void incrementScore(UUID playerId) {
            scores.put(playerId, scores.getInt(playerId) + 1);
            operationCount.incrementAndGet();
        }
        
        public void addToScore(UUID playerId, int points) {
            scores.put(playerId, scores.getInt(playerId) + points);
            operationCount.incrementAndGet();
        }
        
        public boolean hasPlayer(UUID playerId) {
            return scores.containsKey(playerId);
        }
        
        public void removePlayer(UUID playerId) {
            scores.removeInt(playerId);
            operationCount.incrementAndGet();
        }
        
        public Set<UUID> getPlayers() {
            return scores.keySet();
        }
        
        public int size() {
            return scores.size();
        }
        
        public void clear() {
            scores.clear();
        }
        
        // Get top players sorted by score
        public List<Map.Entry<UUID, Integer>> getTopPlayers(int limit) {
            List<Map.Entry<UUID, Integer>> entries = new ArrayList<>();
            
            for (Object2IntMap.Entry<UUID> entry : scores.object2IntEntrySet()) {
                entries.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getIntValue()));
            }
            
            entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            
            return entries.subList(0, Math.min(limit, entries.size()));
        }
        
        public long getEstimatedMemoryUsage() {
            // Estimate: UUID (16 bytes) + int (4 bytes) + overhead (8 bytes) = 28 bytes per entry
            // vs standard HashMap: UUID (16) + Integer object (16) + Entry overhead (24) = 56 bytes
            // Savings: 28 bytes per entry
            long entriesCount = scores.size();
            memoryReductionBytes.addAndGet(entriesCount * 28);
            return entriesCount * 20; // Actual usage estimate
        }
    }
    
    /**
     * Coordinate-based trail tracking using bit-packed coordinates
     */
    public static class CoordinateTrailMap {
        private final Long2ObjectOpenHashMap<TrailData> trails;
        
        public static class TrailData {
            public final UUID playerId;
            public final Material material;
            public final long timestamp;
            
            public TrailData(UUID playerId, Material material, long timestamp) {
                this.playerId = playerId;
                this.material = material;
                this.timestamp = timestamp;
            }
        }
        
        public CoordinateTrailMap() {
            this.trails = new Long2ObjectOpenHashMap<>();
            logger.fine("[CoordinateTrailMap] Initialized with bit-packed coordinates");
        }
        
        /**
         * Pack coordinates into a single long value for efficient storage
         */
        public static long packCoordinates(int x, int y, int z) {
            // Pack coordinates: x (21 bits) | y (12 bits) | z (21 bits) = 54 bits total
            // Supports coordinates from -1M to +1M for x,z and -2048 to +2047 for y
            long packed = 0L;
            packed |= ((long) (x + 1048576) & 0x1FFFFF) << 33; // x: bits 33-53
            packed |= ((long) (y + 2048) & 0xFFF) << 21;       // y: bits 21-32
            packed |= ((long) (z + 1048576) & 0x1FFFFF);       // z: bits 0-20
            return packed;
        }
        
        /**
         * Unpack coordinates from long value
         */
        public static int[] unpackCoordinates(long packed) {
            int x = (int) ((packed >> 33) & 0x1FFFFF) - 1048576;
            int y = (int) ((packed >> 21) & 0xFFF) - 2048;
            int z = (int) (packed & 0x1FFFFF) - 1048576;
            return new int[]{x, y, z};
        }
        
        public void addTrail(Location location, UUID playerId, Material material) {
            long packed = packCoordinates(
                location.getBlockX(), 
                location.getBlockY(), 
                location.getBlockZ()
            );
            
            TrailData data = new TrailData(playerId, material, System.currentTimeMillis());
            trails.put(packed, data);
            operationCount.incrementAndGet();
        }
        
        public TrailData getTrail(Location location) {
            long packed = packCoordinates(
                location.getBlockX(), 
                location.getBlockY(), 
                location.getBlockZ()
            );
            
            operationCount.incrementAndGet();
            return trails.get(packed);
        }
        
        public boolean hasTrail(Location location) {
            long packed = packCoordinates(
                location.getBlockX(), 
                location.getBlockY(), 
                location.getBlockZ()
            );
            
            return trails.containsKey(packed);
        }
        
        public TrailData removeTrail(Location location) {
            long packed = packCoordinates(
                location.getBlockX(), 
                location.getBlockY(), 
                location.getBlockZ()
            );
            
            operationCount.incrementAndGet();
            return trails.remove(packed);
        }
        
        public void removePlayerTrails(UUID playerId) {
            LongIterator iterator = trails.keySet().iterator();
            while (iterator.hasNext()) {
                long packed = iterator.nextLong();
                TrailData data = trails.get(packed);
                if (data != null && data.playerId.equals(playerId)) {
                    iterator.remove();
                }
            }
            operationCount.incrementAndGet();
        }
        
        public int size() {
            return trails.size();
        }
        
        public void clear() {
            trails.clear();
        }
        
        // Get all trails for a player
        public List<Location> getPlayerTrails(UUID playerId) {
            List<Location> playerTrails = new ArrayList<>();
            
            for (Long2ObjectMap.Entry<TrailData> entry : trails.long2ObjectEntrySet()) {
                TrailData data = entry.getValue();
                if (data.playerId.equals(playerId)) {
                    int[] coords = unpackCoordinates(entry.getLongKey());
                    // Note: World is not stored in packed coordinates, would need to be handled separately
                    playerTrails.add(new Location(null, coords[0], coords[1], coords[2]));
                }
            }
            
            return playerTrails;
        }
        
        public long getEstimatedMemoryUsage() {
            // Estimate: long key (8 bytes) + TrailData object (~40 bytes) = 48 bytes per entry
            // vs standard HashMap: Location object (~80 bytes) + TrailData (~40 bytes) + overhead (~24 bytes) = 144 bytes
            // Savings: 96 bytes per entry
            long entriesCount = trails.size();
            memoryReductionBytes.addAndGet(entriesCount * 96);
            return entriesCount * 48;
        }
    }
    
    /**
     * Efficient integer list for coordinate arrays
     */
    public static class CoordinateList {
        private final IntList coordinates;
        
        public CoordinateList() {
            this.coordinates = new IntArrayList();
            logger.fine("[CoordinateList] Initialized primitive int list");
        }
        
        public CoordinateList(int initialCapacity) {
            this.coordinates = new IntArrayList(initialCapacity);
        }
        
        public void addCoordinate(int value) {
            coordinates.add(value);
            operationCount.incrementAndGet();
        }
        
        public void addCoordinates(int x, int y, int z) {
            coordinates.add(x);
            coordinates.add(y);
            coordinates.add(z);
            operationCount.incrementAndGet();
        }
        
        public int getCoordinate(int index) {
            operationCount.incrementAndGet();
            return coordinates.getInt(index);
        }
        
        public void setCoordinate(int index, int value) {
            coordinates.set(index, value);
            operationCount.incrementAndGet();
        }
        
        public int size() {
            return coordinates.size();
        }
        
        public void clear() {
            coordinates.clear();
        }
        
        public int[] toArray() {
            return coordinates.toIntArray();
        }
        
        // Get coordinates as triplets (x, y, z)
        public List<int[]> getCoordinateTriplets() {
            List<int[]> triplets = new ArrayList<>();
            
            for (int i = 0; i < coordinates.size(); i += 3) {
                if (i + 2 < coordinates.size()) {
                    triplets.add(new int[]{
                        coordinates.getInt(i),
                        coordinates.getInt(i + 1),
                        coordinates.getInt(i + 2)
                    });
                }
            }
            
            return triplets;
        }
        
        public long getEstimatedMemoryUsage() {
            // Estimate: int (4 bytes) per coordinate
            // vs List<Integer>: Integer object (16 bytes) + list overhead (8 bytes) = 24 bytes
            // Savings: 20 bytes per coordinate
            long coordinateCount = coordinates.size();
            memoryReductionBytes.addAndGet(coordinateCount * 20);
            return coordinateCount * 4;
        }
    }
    
    /**
     * Conversion utilities for backward compatibility
     */
    public static class ConversionUtils {
        
        /**
         * Convert standard Map<UUID, Integer> to primitive PlayerScoreMap
         */
        public static PlayerScoreMap convertToPlayerScoreMap(Map<UUID, Integer> standardMap) {
            PlayerScoreMap primitiveMap = new PlayerScoreMap();
            
            for (Map.Entry<UUID, Integer> entry : standardMap.entrySet()) {
                primitiveMap.setScore(entry.getKey(), entry.getValue());
            }
            
            logger.fine("[ConversionUtils] Converted " + standardMap.size() + 
                       " entries to PlayerScoreMap");
            
            return primitiveMap;
        }
        
        /**
         * Convert PlayerScoreMap to standard Map<UUID, Integer>
         */
        public static Map<UUID, Integer> convertFromPlayerScoreMap(PlayerScoreMap primitiveMap) {
            Map<UUID, Integer> standardMap = new HashMap<>();
            
            for (UUID playerId : primitiveMap.getPlayers()) {
                standardMap.put(playerId, primitiveMap.getScore(playerId));
            }
            
            return standardMap;
        }
        
        /**
         * Convert List<Integer> to primitive CoordinateList
         */
        public static CoordinateList convertToCoordinateList(List<Integer> standardList) {
            CoordinateList primitiveList = new CoordinateList(standardList.size());
            
            for (Integer value : standardList) {
                primitiveList.addCoordinate(value);
            }
            
            logger.fine("[ConversionUtils] Converted " + standardList.size() + 
                       " coordinates to CoordinateList");
            
            return primitiveList;
        }
    }
    
    /**
     * Get overall performance statistics
     */
    public static String getPerformanceStats() {
        return String.format(
            "PrimitiveCollections Stats: Operations=%d, EstimatedMemoryReduction=%d bytes (%.1f KB)",
            operationCount.get(), memoryReductionBytes.get(), memoryReductionBytes.get() / 1024.0
        );
    }
    
    /**
     * Reset performance statistics
     */
    public static void resetStats() {
        operationCount.set(0);
        memoryReductionBytes.set(0);
        logger.info("[PrimitiveCollections] Performance statistics reset");
    }
    
    /**
     * Get estimated memory reduction percentage
     */
    public static double getMemoryReductionPercentage() {
        // Rough estimate based on typical usage patterns
        long operations = operationCount.get();
        if (operations == 0) return 0.0;
        
        // Assume average 50% memory reduction with primitive collections
        return 50.0;
    }
}
