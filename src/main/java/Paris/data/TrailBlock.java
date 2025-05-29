package Paris.data;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a trail block with metadata for spatial partitioning and collision detection.
 * Optimized for performance with pre-calculated coordinates and keys.
 */
public class TrailBlock {

    private final Location location;
    private final UUID ownerId;
    private final Material material;
    private final long timestamp;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private final String chunkKey;
    private final String blockKey;

    public TrailBlock(Location location, UUID ownerId, Material material) {
        this.location = location.clone();
        this.ownerId = ownerId;
        this.material = material;
        this.timestamp = System.currentTimeMillis();

        // Pre-calculate coordinates for performance
        this.blockX = location.getBlockX();
        this.blockY = location.getBlockY();
        this.blockZ = location.getBlockZ();

        // Pre-calculate keys for spatial partitioning
        this.chunkKey = generateChunkKey(blockX, blockZ);
        this.blockKey = generateBlockKey(blockX, blockY, blockZ);
    }

    /**
     * Generate chunk key for spatial partitioning.
     * Uses bit shifting for efficient chunk coordinate calculation.
     */
    public static String generateChunkKey(int blockX, int blockZ) {
        return (blockX >> 4) + "," + (blockZ >> 4);
    }

    /**
     * Generate block key for spatial partitioning.
     * Pre-formatted string for efficient lookups.
     */
    public static String generateBlockKey(int blockX, int blockY, int blockZ) {
        return blockX + "," + blockY + "," + blockZ;
    }

    /**
     * Generate block key from location
     */
    public static String generateBlockKey(Location location) {
        return generateBlockKey(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Generate chunk key from location
     */
    public static String generateChunkKey(Location location) {
        return generateChunkKey(location.getBlockX(), location.getBlockZ());
    }

    // Getters
    public Location getLocation() {
        return location.clone();
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Material getMaterial() {
        return material;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getBlockX() {
        return blockX;
    }

    public int getBlockY() {
        return blockY;
    }

    public int getBlockZ() {
        return blockZ;
    }

    public String getChunkKey() {
        return chunkKey;
    }

    public String getBlockKey() {
        return blockKey;
    }

    /**
     * Get age of this trail block in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * Check if this trail block is within grace period for its owner
     */
    public boolean isWithinGracePeriod(UUID playerId, long gracePeriodMs) {
        return ownerId.equals(playerId) && getAge() <= gracePeriodMs;
    }

    /**
     * Check if this trail block should cause collision for the given player
     */
    public boolean shouldCauseCollision(UUID playerId, long gracePeriodMs) {
        // Always collision with other players' trails
        if (!ownerId.equals(playerId)) {
            return true;
        }

        // Collision with own trail only after grace period
        return getAge() > gracePeriodMs;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        TrailBlock that = (TrailBlock) obj;
        return blockX == that.blockX &&
               blockY == that.blockY &&
               blockZ == that.blockZ &&
               Objects.equals(location.getWorld(), that.location.getWorld());
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockX, blockY, blockZ, location.getWorld());
    }

    @Override
    public String toString() {
        return "TrailBlock{" +
                "location=" + blockX + "," + blockY + "," + blockZ +
                ", owner=" + ownerId +
                ", material=" + material +
                ", age=" + getAge() + "ms" +
                '}';
    }
}
