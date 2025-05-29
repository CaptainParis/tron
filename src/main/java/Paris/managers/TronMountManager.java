package Paris.managers;

import Paris.Tron;
import Paris.data.PlayerData;
import Paris.optimization.movement.OptimizedMovementManager;
import Paris.optimization.pooling.LocationPool;
import Paris.utils.ColorUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pig;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages Tron-style pig mounting using ArmorStand + Pig approach
 * Optimized with object pooling and efficient movement calculations
 */
public class TronMountManager {
    
    private final Tron plugin;
    private final Map<UUID, ArmorStand> riderMap = new HashMap<>();
    private final Map<UUID, Pig> pigMap = new HashMap<>();
    private final Map<UUID, BukkitTask> movementTasks = new HashMap<>();
    
    // Optimization components
    private OptimizedMovementManager optimizedMovementManager;
    private LocationPool locationPool;
    private boolean useOptimizedMovement = false;
    
    public TronMountManager(Tron plugin) {
        this.plugin = plugin;
        initializeOptimizations();
    }
    
    /**
     * Initialize optimization systems if available
     */
    private void initializeOptimizations() {
        try {
            // Try to get optimization components from the plugin
            if (plugin.getOptimizationManager() != null) {
                this.locationPool = plugin.getOptimizationManager().getLocationPool();
                this.optimizedMovementManager = new OptimizedMovementManager(plugin, locationPool);
                this.useOptimizedMovement = true;
                plugin.getLogger().info("[TronMountManager] Optimized movement system enabled");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[TronMountManager] Failed to initialize optimizations, using standard movement: " + e.getMessage());
            this.useOptimizedMovement = false;
        }
    }
    
    /**
     * Mount a player on a Tron pig system
     */
    public void mountPlayer(Player player, PlayerData playerData) {
        World world = player.getWorld();
        Location spawnLoc = player.getLocation().add(0, 0.1, 0);
        
        // Spawn invisible armor stand (the actual vehicle)
        ArmorStand stand = world.spawn(spawnLoc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setSilent(true);
            as.setInvulnerable(true);
            as.setCollidable(false);
            as.setCustomName("TronMount_" + player.getName());
        });
        
        // Spawn visible pig model to follow
        Pig pig = world.spawn(spawnLoc, Pig.class, p -> {
            p.setCustomName(ColorUtils.getColoredPlayerName(player.getName(), playerData.getTrailMaterial()));
            p.setCustomNameVisible(true);
            p.setAI(false);
            p.setSilent(true);
            p.setInvulnerable(true);
            p.setCollidable(false);
            p.setSaddle(true);
        });
        
        // Let player ride the armor stand
        stand.addPassenger(player);
        
        // Track them
        riderMap.put(player.getUniqueId(), stand);
        pigMap.put(player.getUniqueId(), pig);
        
        // Store references in PlayerData
        playerData.setPig(pig);
        
        // Start movement task (optimized or standard)
        if (useOptimizedMovement && optimizedMovementManager != null) {
            optimizedMovementManager.startMoveTask(player, stand, pig, playerData);
        } else {
            startStandardMoveTask(player, stand, pig, playerData);
        }
        
        plugin.getLogger().info("Mounted player " + player.getName() + " on Tron system" + 
                               (useOptimizedMovement ? " (optimized)" : " (standard)"));
    }
    
    /**
     * Start the standard movement task for a player (fallback)
     */
    private void startStandardMoveTask(Player player, ArmorStand stand, Pig pig, PlayerData playerData) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // Check if entities are still valid
                if (!stand.isValid() || !pig.isValid() || !player.isOnline() || 
                    !player.isInsideVehicle() || player.getVehicle() != stand) {
                    this.cancel();
                    movementTasks.remove(player.getUniqueId());
                    return;
                }
                
                // Get movement direction from player view
                Vector direction = player.getLocation().getDirection().setY(0).normalize();
                
                // Base speed
                double speed = 0.4; // Base speed
                if (playerData.isBoostActive()) {
                    speed = 0.4 * Math.min(3.0, plugin.getConfig().getDouble("game.boost-multiplier", 3.0));
                }
                
                // Calculate new location
                Vector movement = direction.multiply(speed);
                Location newLoc = stand.getLocation().add(movement);
                
                // Teleport the armor stand (the actual mover)
                stand.teleport(newLoc);
                
                // Move the pig to follow armor stand (visual only)
                Location pigLoc = newLoc.clone().add(0, -0.1, 0); // Slightly lower
                pigLoc.setYaw(newLoc.getYaw());
                pig.teleport(pigLoc);
                
                // Update player data with current location for trail generation
                playerData.setLastLocation(newLoc.clone());
            }
        }.runTaskTimer(plugin, 0, 1); // Every tick for smooth movement
        
        movementTasks.put(player.getUniqueId(), task);
    }
    
    /**
     * Unmount a player from the Tron system
     */
    public void unmountPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Stop optimized movement if using it
        if (useOptimizedMovement && optimizedMovementManager != null) {
            optimizedMovementManager.stopMoveTask(playerId);
        }
        
        // Cancel standard movement task
        BukkitTask task = movementTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        
        // Remove armor stand
        ArmorStand stand = riderMap.remove(playerId);
        if (stand != null && stand.isValid()) {
            // Remove passenger first
            if (!stand.getPassengers().isEmpty()) {
                stand.getPassengers().forEach(stand::removePassenger);
            }
            stand.remove();
        }
        
        // Remove pig
        Pig pig = pigMap.remove(playerId);
        if (pig != null && pig.isValid()) {
            pig.remove();
        }
        
        plugin.getLogger().info("Unmounted player " + player.getName() + " from Tron system");
    }
    
    /**
     * Get the ArmorStand for a player
     */
    public ArmorStand getArmorStand(UUID playerId) {
        return riderMap.get(playerId);
    }
    
    /**
     * Get the Pig for a player
     */
    public Pig getPig(UUID playerId) {
        return pigMap.get(playerId);
    }
    
    /**
     * Get the current location of a player's mount
     */
    public Location getMountLocation(UUID playerId) {
        ArmorStand stand = riderMap.get(playerId);
        return stand != null && stand.isValid() ? stand.getLocation() : null;
    }
    
    /**
     * Check if a player is mounted
     */
    public boolean isMounted(UUID playerId) {
        ArmorStand stand = riderMap.get(playerId);
        return stand != null && stand.isValid() && !stand.getPassengers().isEmpty();
    }
    
    /**
     * Get performance statistics
     */
    public String getPerformanceStats() {
        if (useOptimizedMovement && optimizedMovementManager != null) {
            return optimizedMovementManager.getPerformanceStats();
        }
        return "TronMountManager: StandardMode, ActiveMounts=" + riderMap.size();
    }
    
    /**
     * Cleanup all mounts
     */
    public void cleanup() {
        // Stop optimized movement manager
        if (useOptimizedMovement && optimizedMovementManager != null) {
            optimizedMovementManager.cleanup();
        }
        
        // Cancel all movement tasks
        movementTasks.values().forEach(BukkitTask::cancel);
        movementTasks.clear();
        
        // Remove all armor stands
        riderMap.values().forEach(stand -> {
            if (stand != null && stand.isValid()) {
                stand.getPassengers().forEach(stand::removePassenger);
                stand.remove();
            }
        });
        riderMap.clear();
        
        // Remove all pigs
        pigMap.values().forEach(pig -> {
            if (pig != null && pig.isValid()) {
                pig.remove();
            }
        });
        pigMap.clear();
        
        plugin.getLogger().info("Cleaned up all Tron mounts");
    }
}
