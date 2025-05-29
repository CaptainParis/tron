package Paris.managers;

import Paris.Tron;
import Paris.data.GameData;
import Paris.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simplified trail manager for the Tron minigame
 */
public class TrailManager {

    private final Tron plugin;
    private final GameData gameData;
    private final PlayerManager playerManager;

    // Essential tracking
    private final Set<Location> processedLocations;
    private final Map<Location, Long> blockPlacementTimes;

    // Configuration
    private final int maxTrailBlocks = 1000;

    public TrailManager(Tron plugin, GameData gameData, PlayerManager playerManager) {
        this.plugin = plugin;
        this.gameData = gameData;
        this.playerManager = playerManager;

        // Initialize essential tracking
        this.processedLocations = ConcurrentHashMap.newKeySet();
        this.blockPlacementTimes = new ConcurrentHashMap<>();

        plugin.getLogger().info("TrailManager initialized with simplified real block system");
    }

    /**
     * Generate a trail at the specified location for a player
     */
    public void generateTrail(Location location, UUID playerId) {
        if (!gameData.isActive() || !gameData.isPlayerAlive(playerId)) return;

        PlayerData playerData = playerManager.getPlayerData(playerId);
        if (playerData == null || playerData.getTrailMaterial() == null) return;

        // Normalize location to block coordinates
        Location groundLocation = location.getBlock().getLocation();

        // Skip if already processed
        if (processedLocations.contains(groundLocation)) return;

        // Check trail limit per player
        if (playerData.getTrailBlocks().size() >= maxTrailBlocks) {
            removeOldestTrailBlock(playerData);
        }

        // For glass panes, check if we need to add diagonal corner connections
        if (playerData.getTrailMaterial().name().contains("GLASS_PANE")) {
            generateGlassPaneTrailWithDiagonalSupport(groundLocation, playerId, playerData);
        } else {
            // Generate normal 2-block tall trail for non-glass materials
            for (int y = 0; y < 2; y++) {
                Location trailLocation = groundLocation.clone().add(0, y, 0);
                if (isValidTrailLocation(trailLocation)) {
                    placeTrailBlock(trailLocation, playerId, playerData.getTrailMaterial());
                }
            }
        }

        processedLocations.add(groundLocation);
    }

    /**
     * Generate glass pane trail with diagonal corner support
     */
    private void generateGlassPaneTrailWithDiagonalSupport(Location groundLocation, UUID playerId, PlayerData playerData) {
        Material material = playerData.getTrailMaterial();

        // Generate the main 2-block tall trail
        for (int y = 0; y < 2; y++) {
            Location trailLocation = groundLocation.clone().add(0, y, 0);
            if (isValidTrailLocation(trailLocation)) {
                placeTrailBlock(trailLocation, playerId, material);
            }
        }

        // Check for diagonal gaps and add corner blocks if needed
        addDiagonalCornerBlocks(groundLocation, playerId, material);
    }

    /**
     * Add corner blocks to connect diagonal glass pane movements
     * Only add corners when absolutely necessary to maintain single-line trails
     */
    private void addDiagonalCornerBlocks(Location currentLocation, UUID playerId, Material material) {
        // Only check for traditional diagonal patterns - no aggressive gap filling
        addTraditionalDiagonalCorners(currentLocation, playerId, material);
    }



    /**
     * Add traditional diagonal corner blocks - CONSERVATIVE approach for single lines
     */
    private void addTraditionalDiagonalCorners(Location currentLocation, UUID playerId, Material material) {
        PlayerData playerData = playerManager.getPlayerData(playerId);
        if (playerData == null) return;

        Location lastLocation = playerData.getLastLocation();
        if (lastLocation == null) return;

        // Normalize to block coordinates
        Location lastBlock = lastLocation.getBlock().getLocation();
        Location currentBlock = currentLocation.getBlock().getLocation();

        // Calculate movement vector
        int deltaX = currentBlock.getBlockX() - lastBlock.getBlockX();
        int deltaZ = currentBlock.getBlockZ() - lastBlock.getBlockZ();

        // Only add corner for EXACT 1-block diagonal movements to maintain single lines
        if (Math.abs(deltaX) == 1 && Math.abs(deltaZ) == 1) {
            plugin.getLogger().info("[TrailManager] Single-block diagonal movement detected for player " + playerId +
                                   " from " + lastBlock.getBlockX() + "," + lastBlock.getBlockZ() +
                                   " to " + currentBlock.getBlockX() + "," + currentBlock.getBlockZ());

            // Add only ONE corner block to maintain single line - choose the better option
            Location corner1 = new Location(currentLocation.getWorld(), lastBlock.getBlockX(), lastBlock.getBlockY(), currentBlock.getBlockZ());
            Location corner2 = new Location(currentLocation.getWorld(), currentBlock.getBlockX(), lastBlock.getBlockY(), lastBlock.getBlockZ());

            // Choose the corner that would create better connections (but only add ONE)
            if (wouldCreateBetterConnection(corner1, corner2)) {
                addSingleCornerBlockIfNeeded(corner1, playerId, material);
            } else {
                addSingleCornerBlockIfNeeded(corner2, playerId, material);
            }
        }
    }

    /**
     * Determine which corner would create a better connection
     */
    private boolean wouldCreateBetterConnection(Location corner1, Location corner2) {
        int corner1Connections = countAdjacentGlassPanes(corner1);
        int corner2Connections = countAdjacentGlassPanes(corner2);

        // Choose corner1 if it has more connections, or if equal, default to corner1
        return corner1Connections >= corner2Connections;
    }

    /**
     * Count adjacent glass panes for connection scoring
     */
    private int countAdjacentGlassPanes(Location location) {
        int count = 0;
        Location[] adjacent = {
            location.clone().add(0, 0, -1), // North
            location.clone().add(0, 0, 1),  // South
            location.clone().add(1, 0, 0),  // East
            location.clone().add(-1, 0, 0)  // West
        };

        for (Location adj : adjacent) {
            if (isGlassPaneAt(adj)) {
                count++;
            }
        }
        return count;
    }



    /**
     * Add a single corner block only when it would create a proper connection
     * CONSERVATIVE approach to maintain single-line trails
     */
    private void addSingleCornerBlockIfNeeded(Location cornerLocation, UUID playerId, Material material) {
        // Check if this corner location is valid and not already occupied
        if (!isValidTrailLocation(cornerLocation)) return;

        // Only add corner if it would connect exactly 2 glass panes (creating a proper corner)
        if (wouldCreateProperCorner(cornerLocation)) {
            plugin.getLogger().info("[TrailManager] Adding single diagonal corner block at " +
                                   cornerLocation.getBlockX() + "," + cornerLocation.getBlockZ() +
                                   " for player " + playerId);

            // Place 2-block tall corner
            for (int y = 0; y < 2; y++) {
                Location cornerTrailLocation = cornerLocation.clone().add(0, y, 0);
                if (isValidTrailLocation(cornerTrailLocation)) {
                    placeTrailBlock(cornerTrailLocation, playerId, material);
                }
            }
        }
    }

    /**
     * Check if placing a glass pane would create a proper corner (not thick trails)
     */
    private boolean wouldCreateProperCorner(Location location) {
        int adjacentPanes = countAdjacentGlassPanes(location);

        // Only create corner if it connects exactly 2 adjacent panes
        // This prevents creating thick trails while ensuring proper corners
        return adjacentPanes == 2;
    }



    /**
     * Place a trail block and track it
     */
    private void placeTrailBlock(Location location, UUID playerId, Material material) {
        // Place the block
        if (material.name().contains("GLASS_PANE")) {
            placeGlassPane(location, material);
        } else {
            location.getBlock().setType(material, false);
        }

        // Track the block
        gameData.addTrailBlock(location, playerId);
        blockPlacementTimes.put(location, System.currentTimeMillis());

        PlayerData playerData = playerManager.getPlayerData(playerId);
        if (playerData != null) {
            playerData.addTrailBlock(location);
        }
    }

    /**
     * Place a glass pane with enhanced connections and adjacent pane updates
     */
    private void placeGlassPane(Location location, Material material) {
        Block block = location.getBlock();
        block.setType(material, false);

        // Update connections for glass panes
        if (block.getBlockData() instanceof org.bukkit.block.data.type.GlassPane) {
            org.bukkit.block.data.type.GlassPane paneData = (org.bukkit.block.data.type.GlassPane) block.getBlockData();

            // Check adjacent blocks for connections
            boolean north = isGlassPaneAt(location.clone().add(0, 0, -1));
            boolean south = isGlassPaneAt(location.clone().add(0, 0, 1));
            boolean east = isGlassPaneAt(location.clone().add(1, 0, 0));
            boolean west = isGlassPaneAt(location.clone().add(-1, 0, 0));

            paneData.setFace(org.bukkit.block.BlockFace.NORTH, north);
            paneData.setFace(org.bukkit.block.BlockFace.SOUTH, south);
            paneData.setFace(org.bukkit.block.BlockFace.EAST, east);
            paneData.setFace(org.bukkit.block.BlockFace.WEST, west);

            block.setBlockData(paneData, false);

            // Update adjacent glass panes to connect to this new pane
            updateAdjacentGlassPanes(location);
        }
    }

    /**
     * Update adjacent glass panes to connect to the newly placed pane
     */
    private void updateAdjacentGlassPanes(Location centerLocation) {
        Location[] adjacentLocations = {
            centerLocation.clone().add(0, 0, -1), // North
            centerLocation.clone().add(0, 0, 1),  // South
            centerLocation.clone().add(1, 0, 0),  // East
            centerLocation.clone().add(-1, 0, 0)  // West
        };

        for (Location adjLocation : adjacentLocations) {
            if (adjLocation.getWorld() == null) continue;

            Block adjBlock = adjLocation.getBlock();
            if (adjBlock.getBlockData() instanceof org.bukkit.block.data.type.GlassPane) {
                org.bukkit.block.data.type.GlassPane adjPaneData = (org.bukkit.block.data.type.GlassPane) adjBlock.getBlockData();

                // Recalculate connections for this adjacent pane
                boolean north = isGlassPaneAt(adjLocation.clone().add(0, 0, -1));
                boolean south = isGlassPaneAt(adjLocation.clone().add(0, 0, 1));
                boolean east = isGlassPaneAt(adjLocation.clone().add(1, 0, 0));
                boolean west = isGlassPaneAt(adjLocation.clone().add(-1, 0, 0));

                // Update connections
                adjPaneData.setFace(org.bukkit.block.BlockFace.NORTH, north);
                adjPaneData.setFace(org.bukkit.block.BlockFace.SOUTH, south);
                adjPaneData.setFace(org.bukkit.block.BlockFace.EAST, east);
                adjPaneData.setFace(org.bukkit.block.BlockFace.WEST, west);

                // Apply the updated block data
                adjBlock.setBlockData(adjPaneData, false);
            }
        }
    }
    /**
     * Check if there's a glass pane at the specified location
     */
    private boolean isGlassPaneAt(Location location) {
        if (location.getWorld() == null) return false;
        return location.getBlock().getType().name().contains("GLASS_PANE");
    }


    /**
     * Check if a location is valid for trail placement
     */
    private boolean isValidTrailLocation(Location location) {
        if (location.getWorld() == null) return false;
        Block block = location.getBlock();
        Material blockType = block.getType();
        return blockType == Material.AIR ||
               blockType.name().contains("STAINED_GLASS") ||
               blockType.name().contains("GLASS_PANE");
    }

    /**
     * Check if there's a trail at the specified location
     */
    public boolean hasTrailAt(Location location) {
        return gameData.hasTrailAt(location);
    }

    /**
     * Check for collision at the specified location
     */
    public boolean checkCollision(Location location, UUID playerId) {
        if (!gameData.isActive()) return false;

        Location blockLocation = location.getBlock().getLocation();
        Block block = blockLocation.getBlock();
        Material blockType = block.getType();

        // Check for barrier blocks (arena walls)
        if (blockType == Material.BARRIER) {
            return true;
        }

        // Check for solid blocks (arena boundaries)
        if (blockType.isSolid() && blockType != Material.AIR &&
            !blockType.name().contains("STAINED_GLASS") && !blockType.name().contains("GLASS_PANE")) {
            return true;
        }

        // Check for trail blocks
        if (blockType.name().contains("GLASS_PANE") || blockType.name().contains("STAINED_GLASS")) {
            if (gameData.hasTrailAt(blockLocation)) {
                UUID trailOwner = gameData.getTrailOwner(blockLocation);
                if (trailOwner != null) {
                    // Collision with other player's trail
                    if (!trailOwner.equals(playerId)) {
                        return true;
                    }
                    // Collision with own trail after grace period
                    Long placementTime = blockPlacementTimes.get(blockLocation);
                    if (placementTime != null && (System.currentTimeMillis() - placementTime) > 1000) {
                        return true;
                    }
                }
            }
        }

        // Check one block above for 2-block tall trails
        Location aboveLocation = blockLocation.clone().add(0, 1, 0);
        Block aboveBlock = aboveLocation.getBlock();
        Material aboveBlockType = aboveBlock.getType();

        if (aboveBlockType.name().contains("GLASS_PANE") || aboveBlockType.name().contains("STAINED_GLASS")) {
            if (gameData.hasTrailAt(aboveLocation)) {
                UUID trailOwner = gameData.getTrailOwner(aboveLocation);
                if (trailOwner != null) {
                    if (!trailOwner.equals(playerId)) {
                        return true;
                    }
                    Long placementTime = blockPlacementTimes.get(aboveLocation);
                    if (placementTime != null && (System.currentTimeMillis() - placementTime) > 1000) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Remove the oldest trail block for a player to stay within limits
     */
    private void removeOldestTrailBlock(PlayerData playerData) {
        Set<Location> trailBlocks = playerData.getTrailBlocks();
        if (trailBlocks.isEmpty()) return;

        Location oldestLocation = null;
        long oldestTime = Long.MAX_VALUE;

        for (Location location : trailBlocks) {
            Long placementTime = blockPlacementTimes.get(location);
            if (placementTime != null && placementTime < oldestTime) {
                oldestTime = placementTime;
                oldestLocation = location;
            }
        }

        if (oldestLocation != null) {
            removeTrailBlock(oldestLocation, playerData.getPlayerId());
        }
    }

    /**
     * Remove a specific trail block
     */
    public void removeTrailBlock(Location location, UUID playerId) {
        if (location.getWorld() != null) {
            location.getBlock().setType(Material.AIR);
        }

        PlayerData playerData = playerManager.getPlayerData(playerId);
        if (playerData != null) {
            playerData.getTrailBlocks().remove(location);
        }

        gameData.getTrailBlocks().remove(location);
        processedLocations.remove(location);
        blockPlacementTimes.remove(location);
    }

    /**
     * Clear all trails
     */
    public void clearAllTrails() {
        plugin.getLogger().info("[TrailManager] Clearing all trail blocks...");

        // Clear real blocks in world
        int clearedBlocks = 0;
        for (Location location : gameData.getTrailBlocks().keySet()) {
            if (location.getWorld() != null) {
                Block block = location.getBlock();
                if (block.getType().name().contains("GLASS_PANE") ||
                    block.getType().name().contains("STAINED_GLASS")) {
                    block.setType(Material.AIR);
                    clearedBlocks++;
                }
            }
        }

        // Clear data structures
        gameData.clearTrails();
        processedLocations.clear();
        blockPlacementTimes.clear();

        // Clear player trail data
        for (PlayerData playerData : playerManager.getAllPlayerData()) {
            playerData.clearTrailBlocks();
        }

        plugin.getLogger().info("[TrailManager] Cleared " + clearedBlocks + " trail blocks");
    }

    /**
     * Cleanup method
     */
    public void cleanup() {
        clearAllTrails();
        plugin.getLogger().info("TrailManager cleanup completed");
    }
}
