package Paris.managers;

import Paris.Tron;
import Paris.data.GameData;
import Paris.utils.ColorUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Manages arena creation, reset, and spawn locations for the Tron minigame
 */
public class ArenaManager {

    private final Tron plugin;
    private final GameData gameData;

    // Configuration
    private String worldName;
    private int arenaSize;
    private int arenaHeight;
    private Material borderMaterial;
    private Material floorMaterial;
    private boolean resetBetweenGames;

    // Arena data
    private World arenaWorld;
    private Location arenaCenter;
    private final Set<Location> originalBlocks;

    // World border system
    private WorldBorder worldBorder;
    private BukkitTask borderTask;
    private double initialBorderSize;
    private double finalBorderSize;
    private long borderCloseTime;

    public ArenaManager(Tron plugin, GameData gameData) {
        this.plugin = plugin;
        this.gameData = gameData;
        this.originalBlocks = new HashSet<>();

        loadConfiguration();
        initializeArena();
    }

    private void loadConfiguration() {
        this.worldName = plugin.getConfig().getString("arena.world-name", "tron_arena");
        this.arenaSize = plugin.getConfig().getInt("arena.size", 50);
        this.arenaHeight = plugin.getConfig().getInt("arena.height", 10);
        this.borderMaterial = Material.valueOf(
            plugin.getConfig().getString("arena.border-material", "BARRIER"));
        this.floorMaterial = Material.valueOf(
            plugin.getConfig().getString("arena.floor-material", "QUARTZ_BLOCK"));
        this.resetBetweenGames = plugin.getConfig().getBoolean("arena.reset-between-games", true);

        // World border configuration
        this.initialBorderSize = plugin.getConfig().getDouble("arena.world-border.initial-size", arenaSize * 1.2);
        this.finalBorderSize = plugin.getConfig().getDouble("arena.world-border.final-size", 10.0);
        this.borderCloseTime = plugin.getConfig().getLong("arena.world-border.close-time-seconds", 300) * 20L; // Convert to ticks
    }

    /**
     * Initialize the arena world
     */
    private void initializeArena() {
        // Check if world exists
        arenaWorld = Bukkit.getWorld(worldName);

        if (arenaWorld == null) {
            // Create new void world
            createArenaWorld();
        }

        if (arenaWorld != null) {
            // Set world properties
            arenaWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            arenaWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            arenaWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            arenaWorld.setGameRule(GameRule.KEEP_INVENTORY, true);
            arenaWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            arenaWorld.setTime(6000); // Noon

            // Set arena center
            arenaCenter = new Location(arenaWorld, 0, 64, 0);
            gameData.setArenaWorld(arenaWorld);
            gameData.setArenaCenter(arenaCenter);
            gameData.setArenaSize(arenaSize);

            // Generate arena structure
            generateArenaStructure();

            // Initialize world border
            initializeWorldBorder();
        }
    }

    /**
     * Create the arena world
     */
    private void createArenaWorld() {
        try {
            WorldCreator creator = new WorldCreator(worldName);
            creator.generator(new VoidGenerator());
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);

            arenaWorld = creator.createWorld();

            if (arenaWorld != null) {
                plugin.getLogger().info("Created arena world: " + worldName);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create arena world: " + e.getMessage());
            // Fallback to main world
            arenaWorld = Bukkit.getWorlds().get(0);
            arenaCenter = arenaWorld.getSpawnLocation();
        }
    }

    /**
     * Generate the arena structure
     */
    private void generateArenaStructure() {
        if (arenaWorld == null || arenaCenter == null) return;

        int halfSize = arenaSize / 2;
        int centerX = arenaCenter.getBlockX();
        int centerY = arenaCenter.getBlockY();
        int centerZ = arenaCenter.getBlockZ();

        // Generate floor
        for (int x = -halfSize; x <= halfSize; x++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                Location floorLocation = new Location(arenaWorld, centerX + x, centerY - 1, centerZ + z);
                setBlock(floorLocation, floorMaterial);
            }
        }

        // Generate borders
        for (int y = 0; y < arenaHeight; y++) {
            // North and South borders
            for (int x = -halfSize; x <= halfSize; x++) {
                setBlock(new Location(arenaWorld, centerX + x, centerY + y, centerZ - halfSize), borderMaterial);
                setBlock(new Location(arenaWorld, centerX + x, centerY + y, centerZ + halfSize), borderMaterial);
            }

            // East and West borders
            for (int z = -halfSize + 1; z < halfSize; z++) {
                setBlock(new Location(arenaWorld, centerX - halfSize, centerY + y, centerZ + z), borderMaterial);
                setBlock(new Location(arenaWorld, centerX + halfSize, centerY + y, centerZ + z), borderMaterial);
            }
        }

        // No ceiling - open arena for better gameplay

        plugin.getLogger().info("Generated arena structure (" + arenaSize + "x" + arenaSize + ")");
    }

    /**
     * Initialize world border for the arena
     */
    private void initializeWorldBorder() {
        if (arenaWorld == null || arenaCenter == null) return;

        worldBorder = arenaWorld.getWorldBorder();
        worldBorder.setCenter(arenaCenter.getX(), arenaCenter.getZ());
        worldBorder.setSize(initialBorderSize);
        worldBorder.setWarningDistance(5);
        worldBorder.setWarningTime(10);
        worldBorder.setDamageAmount(1.0); // 1 heart per second
        worldBorder.setDamageBuffer(2.0); // 2 blocks outside border before damage

        plugin.getLogger().info("Initialized world border: center(" + arenaCenter.getX() + "," + arenaCenter.getZ() +
                               ") size=" + initialBorderSize);
    }

    /**
     * Start world border closing process
     */
    public void startWorldBorderClosing() {
        if (worldBorder == null) {
            plugin.getLogger().warning("Cannot start world border closing - border not initialized!");
            return;
        }

        // Set the border to close over time
        long closeTimeSeconds = borderCloseTime / 20L;
        worldBorder.setSize(finalBorderSize, closeTimeSeconds);

        plugin.getLogger().info("World border closing: " + initialBorderSize + " -> " + finalBorderSize +
                               " over " + closeTimeSeconds + " seconds");

        // Start monitoring task for player elimination
        startBorderMonitoring();
    }

    /**
     * Start monitoring players for world border elimination
     */
    private void startBorderMonitoring() {
        if (borderTask != null) {
            borderTask.cancel();
        }

        borderTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (arenaWorld == null) {
                    cancel();
                    return;
                }

                // Check all players in arena world
                for (Player player : arenaWorld.getPlayers()) {
                    if (gameData.isActivePlayer(player.getUniqueId()) && gameData.isPlayerAlive(player.getUniqueId())) {
                        Location playerLoc = player.getLocation();
                        double distanceFromCenter = playerLoc.distance(arenaCenter);
                        double currentBorderSize = worldBorder.getSize();

                        // Check if player is outside the border (with small buffer)
                        if (distanceFromCenter > (currentBorderSize / 2.0) + 1.0) {
                            // Eliminate player for being outside border
                            plugin.getGameManager().eliminatePlayer(player.getUniqueId(), "world border");
                            player.sendMessage(ColorUtils.colorize("&c&lYou were eliminated by the world border!"));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Check every second
    }

    /**
     * Stop world border closing and monitoring
     */
    public void stopWorldBorder() {
        if (borderTask != null) {
            borderTask.cancel();
            borderTask = null;
        }

        if (worldBorder != null) {
            // Reset border to initial size
            worldBorder.setSize(initialBorderSize);
            plugin.getLogger().info("World border reset to initial size: " + initialBorderSize);
        }
    }

    /**
     * Set a block and track original state
     */
    private void setBlock(Location location, Material material) {
        Block block = location.getBlock();
        if (block.getType() != material) {
            originalBlocks.add(location.clone());
            block.setType(material);
        }
    }

    /**
     * Prepare arena for a new game (reuses same arena, clears entities)
     */
    public void prepareArena() {
        plugin.getLogger().info("Preparing arena for new game...");

        // Clear all entities from arena first
        clearArenaEntities();

        // Clear old structures first (including barrier cubes from old arena size)
        clearOldArenaStructures();

        if (resetBetweenGames) {
            clearArenaFloor();
        }

        // Generate fresh arena structure
        generateArenaStructure();

        plugin.getLogger().info("Arena preparation completed!");
    }

    /**
     * Clear all entities from the arena world (except players)
     */
    private void clearArenaEntities() {
        if (arenaWorld == null) return;

        plugin.getLogger().info("Clearing arena entities...");

        int entityCount = 0;
        for (Entity entity : arenaWorld.getEntities()) {
            // Don't remove players - they'll be handled separately
            if (!(entity instanceof Player)) {
                entity.remove();
                entityCount++;
            }
        }

        plugin.getLogger().info("Cleared " + entityCount + " entities from arena");
    }

    /**
     * Clear the arena floor of any trails
     */
    private void clearArenaFloor() {
        if (arenaWorld == null || arenaCenter == null) return;

        int halfSize = arenaSize / 2;
        int centerX = arenaCenter.getBlockX();
        int centerY = arenaCenter.getBlockY();
        int centerZ = arenaCenter.getBlockZ();

        // Clear arena floor area
        for (int x = -halfSize + 1; x < halfSize; x++) {
            for (int z = -halfSize + 1; z < halfSize; z++) {
                for (int y = 0; y < arenaHeight - 1; y++) {
                    Location location = new Location(arenaWorld, centerX + x, centerY + y, centerZ + z);
                    Block block = location.getBlock();

                    // Remove any stained glass (trails)
                    if (block.getType().name().contains("STAINED_GLASS")) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    /**
     * Clear old arena structures (barriers, old floors) in a larger area
     */
    private void clearOldArenaStructures() {
        if (arenaWorld == null || arenaCenter == null) return;

        int clearSize = 150; // Clear larger area to remove old structures
        int halfClearSize = clearSize / 2;
        int centerX = arenaCenter.getBlockX();
        int centerY = arenaCenter.getBlockY();
        int centerZ = arenaCenter.getBlockZ();

        plugin.getLogger().info("Clearing old arena structures in " + clearSize + "x" + clearSize + " area...");

        // Clear old structures in a large area
        for (int x = -halfClearSize; x <= halfClearSize; x++) {
            for (int z = -halfClearSize; z <= halfClearSize; z++) {
                for (int y = -5; y <= arenaHeight + 5; y++) {
                    Location location = new Location(arenaWorld, centerX + x, centerY + y, centerZ + z);
                    Block block = location.getBlock();
                    Material blockType = block.getType();

                    // Remove old arena materials
                    if (blockType == Material.BARRIER ||
                        blockType == Material.QUARTZ_BLOCK ||
                        blockType.name().contains("STAINED_GLASS")) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }

        plugin.getLogger().info("Old arena structures cleared!");
    }

    /**
     * Get spawn locations for players
     */
    public List<Location> getSpawnLocations(int playerCount) {
        List<Location> spawnLocations = new ArrayList<>();

        if (arenaCenter == null) return spawnLocations;

        // Calculate spawn positions around the arena
        double radius = (arenaSize / 2.0) - 5; // 5 blocks from border
        double angleStep = (2 * Math.PI) / playerCount;

        for (int i = 0; i < playerCount; i++) {
            double angle = i * angleStep;
            double x = arenaCenter.getX() + (radius * Math.cos(angle));
            double z = arenaCenter.getZ() + (radius * Math.sin(angle));

            Location spawnLocation = new Location(
                arenaWorld,
                x,
                arenaCenter.getY() + 1,
                z,
                (float) Math.toDegrees(angle + Math.PI), // Face center
                0
            );

            spawnLocations.add(spawnLocation);
        }

        return spawnLocations;
    }

    /**
     * Check if a location is within arena bounds
     */
    public boolean isWithinArena(Location location) {
        if (arenaCenter == null || !location.getWorld().equals(arenaWorld)) {
            return false;
        }

        int halfSize = arenaSize / 2;
        int deltaX = Math.abs(location.getBlockX() - arenaCenter.getBlockX());
        int deltaZ = Math.abs(location.getBlockZ() - arenaCenter.getBlockZ());

        return deltaX <= halfSize && deltaZ <= halfSize;
    }

    /**
     * Get a safe location within the arena
     */
    public Location getSafeLocation() {
        if (arenaCenter == null) return null;

        return arenaCenter.clone().add(0, 1, 0);
    }

    /**
     * Reset arena to original state
     */
    public void resetArena() {
        if (!resetBetweenGames) return;

        // Clear all trails asynchronously
        new BukkitRunnable() {
            @Override
            public void run() {
                clearArenaFloor();
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Get arena center location
     */
    public Location getArenaCenter() {
        return arenaCenter != null ? arenaCenter.clone() : null;
    }

    /**
     * Get arena world
     */
    public World getArenaWorld() {
        return arenaWorld;
    }

    /**
     * Get arena size
     */
    public int getArenaSize() {
        return arenaSize;
    }

    /**
     * Teleport player to arena
     */
    public void teleportToArena(org.bukkit.entity.Player player) {
        Location safeLocation = getSafeLocation();
        if (safeLocation != null) {
            player.teleport(safeLocation);
        }
    }

    /**
     * Custom void world generator
     */
    private static class VoidGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            ChunkData chunkData = createChunkData(world);

            // Set all biomes to plains
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    for (int k = world.getMinHeight(); k < world.getMaxHeight(); k++) {
                        biome.setBiome(i, k, j, org.bukkit.block.Biome.PLAINS);
                    }
                }
            }

            return chunkData;
        }

        @Override
        public boolean canSpawn(World world, int x, int z) {
            return true;
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0, 64, 0);
        }
    }



    /**
     * Cleanup method
     */
    public void cleanup() {
        // Stop world border
        stopWorldBorder();

        if (arenaWorld != null && resetBetweenGames) {
            clearArenaFloor();
        }
    }
}
