package Paris.listeners;

import Paris.Tron;
import Paris.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PigRideListener implements Listener {

    private static final double PIG_SPEED = 0.35; // Slightly faster than original but not too fast
    private static final int TASK_INTERVAL_TICKS = 1; // Every tick for maximum responsiveness
    private static final int DEBUG_INTERVAL_TICKS = 400; // Less frequent debug to reduce console spam

    private final Plugin plugin;
    private BukkitTask pigControlTask;
    private final Map<UUID, Pig> ridingPlayers = new HashMap<>();
    private final Map<UUID, Float> lastPlayerYaw = new HashMap<>(); // Track last yaw to avoid unnecessary rotations

    // Chunk-based trail tracking for bottom layer
    private final Map<String, Map<String, UUID>> chunkTrailMap = new ConcurrentHashMap<>(); // chunk -> blockKey -> playerId

    private int debugCounter = 0;

    public PigRideListener(Plugin plugin) {
        this.plugin = plugin;
        startPigControlTask();
    }

    private void startPigControlTask() {
        pigControlTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (ridingPlayers.isEmpty()) return;

                debugCounter++;

                ridingPlayers.entrySet().removeIf(entry -> {
                    UUID playerId = entry.getKey();
                    Pig pig = entry.getValue();
                    Player player = Bukkit.getPlayer(playerId);

                    if (player == null || !player.isOnline() || player.getVehicle() != pig) {
                        // Clean up tracking data
                        lastPlayerYaw.remove(playerId);
                        return true;
                    }

                    float playerYaw = player.getLocation().getYaw();
                    double radians = Math.toRadians(playerYaw);

                    // Calculate speed based on boost status
                    double currentSpeed = PIG_SPEED;
                    if (plugin instanceof Tron) {
                        Tron tronPlugin = (Tron) plugin;
                        PlayerData playerData = tronPlugin.getPlayerManager().getPlayerData(playerId);
                        if (playerData != null && playerData.isBoostActive()) {
                            double boostMultiplier = tronPlugin.getConfig().getDouble("game.boost-multiplier", 3.0);
                            currentSpeed = PIG_SPEED * boostMultiplier;
                        }
                    }

                    double velocityX = -Math.sin(radians) * currentSpeed;
                    double velocityZ = Math.cos(radians) * currentSpeed;

                    // Apply velocity for movement (AI processes this) but keep Y at 0 to stay on ground
                    pig.setVelocity(new Vector(velocityX, 0, velocityZ));

                    // Ensure pig stays at ground level (arena floor)
                    Location pigLoc = pig.getLocation();
                    pigLoc.setY(pigLoc.getBlockY()); // Keep pig directly on arena floor blocks
                    pig.teleport(pigLoc);

                    // Smart rotation - only rotate if player yaw changed significantly
                    Float lastYaw = lastPlayerYaw.get(playerId);
                    if (lastYaw == null || Math.abs(playerYaw - lastYaw) > 1.0f) {
                        pig.setRotation(playerYaw, 0);
                        lastPlayerYaw.put(playerId, playerYaw);
                    }

                    // Check for collision with trail blocks (using existing TrailManager)
                    checkCollisionWithTrails(pig, player, playerYaw);

                    // Debug logging disabled to prevent console spam
                    // Uncomment below for debugging pig movement issues
                    /*
                    if (debugCounter % 400 == 0) { // Much less frequent
                        Vector currentVel = pig.getVelocity();
                        float actualPigYaw = pig.getLocation().getYaw();
                        Bukkit.getLogger().info("[PigRide DEBUG] Player: " + player.getName() +
                            " | PlayerYaw: " + String.format("%.1f", playerYaw) + "° | PigYaw: " + String.format("%.1f", actualPigYaw) + "°" +
                            " | YawDiff: " + String.format("%.1f", Math.abs(playerYaw - actualPigYaw)) + "°" +
                            " | Applied: (" + String.format("%.3f", velocityX) + ", 0, " + String.format("%.3f", velocityZ) + ")" +
                            " | Actual: (" + String.format("%.3f", currentVel.getX()) + ", " + String.format("%.3f", currentVel.getY()) + ", " + String.format("%.3f", currentVel.getZ()) + ")" +
                            " | AI: " + pig.hasAI());
                    }
                    */

                    return false;
                });

                if (debugCounter % DEBUG_INTERVAL_TICKS == 0 && !ridingPlayers.isEmpty()) {
                    Bukkit.getLogger().info("[PigRide] Controlling " + ridingPlayers.size() + " pig(s)");
                }
            }
        }.runTaskTimer(plugin, 0L, TASK_INTERVAL_TICKS);

        Bukkit.getLogger().info("[PigRide] Pig control task started");
    }

    /**
     * Check for collision with trail blocks and barriers using existing TrailManager
     */
    private void checkCollisionWithTrails(Pig pig, Player player, float yaw) {
        if (!(plugin instanceof Tron)) return;

        Tron tronPlugin = (Tron) plugin;
        Location currentLoc = pig.getLocation();

        // Calculate direction vector from yaw
        double radians = Math.toRadians(yaw);
        double dirX = -Math.sin(radians);
        double dirZ = Math.cos(radians);

        // Check the block the pig is moving towards (1 block ahead)
        Location nextLoc = currentLoc.clone().add(dirX, 0, dirZ);

        // Check for barrier blocks first (immediate elimination)
        if (checkBarrierCollision(nextLoc, player.getUniqueId())) {
            eliminatePlayer(player.getUniqueId(), "hit barrier wall");
            return;
        }

        // Check current location for barrier collision
        if (checkBarrierCollision(currentLoc, player.getUniqueId())) {
            eliminatePlayer(player.getUniqueId(), "hit barrier wall");
            return;
        }

        // Use TrailManager's collision detection for trails
        if (tronPlugin.getTrailManager().checkCollision(nextLoc, player.getUniqueId())) {
            eliminatePlayer(player.getUniqueId(), "hit trail");
            return;
        }

        // Also check current location for immediate trail collision
        if (tronPlugin.getTrailManager().checkCollision(currentLoc, player.getUniqueId())) {
            eliminatePlayer(player.getUniqueId(), "hit trail");
        }
    }

    /**
     * Check for collision with barrier blocks (arena walls/borders)
     */
    private boolean checkBarrierCollision(Location location, UUID playerId) {
        if (location.getWorld() == null) return false;

        Block block = location.getBlock();
        Material blockType = block.getType();

        // Check for barrier blocks
        if (blockType == Material.BARRIER) {
            Bukkit.getLogger().info("[PigRide] Player " + playerId + " hit barrier at " +
                location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
            return true;
        }

        // Also check one block above for tall barriers
        Block aboveBlock = location.clone().add(0, 1, 0).getBlock();
        if (aboveBlock.getType() == Material.BARRIER) {
            Bukkit.getLogger().info("[PigRide] Player " + playerId + " hit barrier above at " +
                location.getBlockX() + "," + (location.getBlockY() + 1) + "," + location.getBlockZ());
            return true;
        }

        return false;
    }

    /**
     * Eliminate a player (delegate to game manager)
     */
    private void eliminatePlayer(UUID playerId, String reason) {
        if (plugin instanceof Tron) {
            Tron tronPlugin = (Tron) plugin;
            if (tronPlugin.getGameManager() != null) {
                tronPlugin.getGameManager().eliminatePlayer(playerId, reason);
            }
        }
        Bukkit.getLogger().info("[PigRide] Player " + playerId + " eliminated: " + reason);
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player && event.getVehicle().getType() == EntityType.PIG) {
            Player player = (Player) event.getEntered();
            Pig pig = (Pig) event.getVehicle();

            // Keep AI enabled for movement, we'll handle rotation conflicts differently
            pig.setAI(true);

            ridingPlayers.put(player.getUniqueId(), pig);
            Bukkit.getLogger().info("[PigRide] " + player.getName() + " mounted pig - AI enabled, manual steering active");
        }
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player && event.getVehicle().getType() == EntityType.PIG) {
            Player player = (Player) event.getExited();
            Pig pig = (Pig) event.getVehicle();

            // Re-enable AI so pig behaves normally after dismount
            pig.setAI(true);

            UUID playerId = player.getUniqueId();
            ridingPlayers.remove(playerId);
            lastPlayerYaw.remove(playerId); // Clean up tracking data
            Bukkit.getLogger().info("[PigRide] " + player.getName() + " dismounted pig - AI re-enabled");
        }
    }

    /**
     * Clean up tracking data for a specific player
     */
    public void cleanupPlayer(UUID playerId) {
        ridingPlayers.remove(playerId);
        lastPlayerYaw.remove(playerId);
        Bukkit.getLogger().info("[PigRide] Cleaned up tracking data for player " + playerId);
    }

    public void shutdown() {
        if (pigControlTask != null) {
            pigControlTask.cancel();
        }
        ridingPlayers.clear();
        lastPlayerYaw.clear();
        chunkTrailMap.clear();
    }

    /**
     * Add a trail block to chunk tracking (no longer used - kept for compatibility)
     */
    public void addTrailBlock(Location location, UUID playerId) {
        // No longer needed - TrailManager handles trail tracking
    }

    /**
     * Remove a trail block from chunk tracking (no longer used - kept for compatibility)
     */
    public void removeTrailBlock(Location location) {
        // No longer needed - TrailManager handles trail tracking
    }

    /**
     * Clear all trail tracking data (no longer used - kept for compatibility)
     */
    public void clearTrailTracking() {
        // No longer needed - TrailManager handles trail tracking
    }
}
