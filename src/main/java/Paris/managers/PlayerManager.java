package Paris.managers;

import Paris.Tron;
import Paris.data.GameData;
import Paris.data.PlayerData;
import Paris.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player data, pig mounting, and player states for the Tron minigame
 */
public class PlayerManager {

    private final Tron plugin;
    private final GameData gameData;
    private final Map<UUID, PlayerData> playerDataMap;
    private final Map<UUID, Location> playerSpawnLocations;

    // Configuration
    private double pigSpeed;
    private boolean soundsEnabled;

    public PlayerManager(Tron plugin, GameData gameData) {
        this.plugin = plugin;
        this.gameData = gameData;
        this.playerDataMap = new ConcurrentHashMap<>();
        this.playerSpawnLocations = new HashMap<>();

        loadConfiguration();
    }

    private void loadConfiguration() {
        this.pigSpeed = plugin.getConfig().getDouble("game.pig-speed", 2.5);
        this.soundsEnabled = plugin.getConfig().getBoolean("game.sounds", true);
    }

    /**
     * Get or create player data
     */
    public PlayerData getPlayerData(UUID playerId) {
        return playerDataMap.get(playerId);
    }

    /**
     * Get or create player data by player object
     */
    public PlayerData getPlayerData(Player player) {
        return getOrCreatePlayerData(player.getUniqueId(), player.getName());
    }

    /**
     * Get or create player data
     */
    public PlayerData getOrCreatePlayerData(UUID playerId, String playerName) {
        return playerDataMap.computeIfAbsent(playerId, id -> new PlayerData(id, playerName));
    }

    /**
     * Get all player data
     */
    public Collection<PlayerData> getAllPlayerData() {
        return playerDataMap.values();
    }

    /**
     * Add player to queue
     */
    public boolean addToQueue(Player player) {
        UUID playerId = player.getUniqueId();

        if (gameData.isInQueue(playerId)) {
            return false; // Already in queue
        }

        PlayerData playerData = getOrCreatePlayerData(playerId, player.getName());
        if (playerData.isAFK()) {
            return false; // Player is AFK
        }

        // If player is currently in an active game, eliminate them first
        if (gameData.isActivePlayer(playerId) && gameData.isPlayerAlive(playerId)) {
            plugin.getLogger().info(player.getName() + " joining queue while in active game - eliminating from current game");

            // Clean up their pig and eliminate them
            if (playerData.getPig() != null) {
                playerData.getPig().remove();
                playerData.setPig(null);
            }

            // Mark as eliminated
            gameData.eliminatePlayer(playerId);

            // Broadcast elimination message
            String message = plugin.getConfig().getString("messages.player-eliminated", "&c{player} &7has been eliminated!")
                .replace("{player}", player.getName())
                .replace("{reason}", "left game");

            Bukkit.broadcastMessage(ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix", "&6[Tron] &r") + message));

            // Check if game should end after elimination
            if (gameData.shouldEnd()) {
                plugin.getGameManager().endGame();
            }
        }

        gameData.addToQueue(playerId);
        playerData.setPlayer(player);

        // Set player to spectator mode while in queue
        player.setGameMode(GameMode.SPECTATOR);

        // Different message based on game state
        if (gameData.isActive() || gameData.isStarting()) {
            player.sendMessage("§eGame is currently in progress. You've been queued for the next game and put in spectator mode to watch.");
            plugin.getLogger().info(player.getName() + " joined mid-game - put in spectator mode and queued for next game");
        } else {
            player.sendMessage("§7You are now in spectator mode while waiting for the game to start.");
        }

        return true;
    }

    /**
     * Remove player from queue
     */
    public boolean removeFromQueue(Player player) {
        if (!gameData.isInQueue(player.getUniqueId())) {
            return false; // Not in queue
        }

        gameData.removeFromQueue(player.getUniqueId());
        return true;
    }

    /**
     * Toggle player AFK status
     */
    public boolean toggleAFK(Player player) {
        PlayerData playerData = getOrCreatePlayerData(player.getUniqueId(), player.getName());
        boolean newAFKStatus = !playerData.isAFK();
        playerData.setAFK(newAFKStatus);

        // Remove from queue if going AFK
        if (newAFKStatus && gameData.isInQueue(player.getUniqueId())) {
            removeFromQueue(player);
        }

        return newAFKStatus;
    }

    /**
     * Setup players for a new game
     */
    public void setupPlayersForGame(List<Location> spawnLocations) {
        Set<UUID> activePlayers = gameData.getActivePlayers();
        List<Material> trailMaterials = generateRandomizedTrailMaterials();

        plugin.getLogger().info("Setting up " + activePlayers.size() + " players with randomized colors (max 30 supported)");

        setupPlayersWithMaterials(activePlayers, spawnLocations, trailMaterials);
    }

    /**
     * Generate randomized trail materials for the game
     */
    private List<Material> generateRandomizedTrailMaterials() {
        long gameSeed = System.currentTimeMillis();
        return ColorUtils.getRandomizedTrailMaterials(gameSeed);
    }

    /**
     * Setup players with assigned materials and spawn locations
     */
    private void setupPlayersWithMaterials(Set<UUID> activePlayers, List<Location> spawnLocations, List<Material> trailMaterials) {
        int colorIndex = 0;
        int spawnIndex = 0;

        for (UUID playerId : activePlayers) {
            PlayerData playerData = getPlayerData(playerId);
            if (playerData == null) continue;

            Player player = playerData.getPlayer();
            if (player == null || !player.isOnline()) {
                // Remove offline players
                gameData.removeActivePlayer(playerId);
                continue;
            }

            // Reset player data for new game
            playerData.resetForGame();

            // Assign randomized color
            Material trailMaterial = trailMaterials.get(colorIndex % trailMaterials.size());
            playerData.setTrailMaterial(trailMaterial);
            plugin.getLogger().info("Assigned " + player.getName() + " color: " + trailMaterial.name());
            colorIndex++;

            // Teleport to spawn location
            if (spawnIndex < spawnLocations.size()) {
                Location spawnLocation = spawnLocations.get(spawnIndex);
                playerSpawnLocations.put(playerId, spawnLocation.clone());
                player.teleport(spawnLocation);
                spawnIndex++;
            }

            // Setup player
            setupPlayer(player, playerData);

            // Spawn and mount pig
            spawnPigForPlayer(player, playerData);
        }
    }

    /**
     * Setup a player for the game
     */
    private void setupPlayer(Player player, PlayerData playerData) {
        // Clear inventory and effects
        player.getInventory().clear();
        player.getActivePotionEffects().forEach(effect ->
            player.removePotionEffect(effect.getType()));

        // Set game mode
        player.setGameMode(GameMode.ADVENTURE);
        plugin.getLogger().info("Set " + player.getName() + " to ADVENTURE mode for game");

        // Set health and food
        player.setHealth(20.0); // Set to full health
        player.setFoodLevel(20);
        player.setSaturation(20);

        // Give colored leather armor
        Color leatherColor = ColorUtils.getLeatherColorForMaterial(playerData.getTrailMaterial());

        ItemStack helmet = ColorUtils.createColoredLeatherArmor(Material.LEATHER_HELMET, leatherColor);
        ItemStack chestplate = ColorUtils.createColoredLeatherArmor(Material.LEATHER_CHESTPLATE, leatherColor);
        ItemStack leggings = ColorUtils.createColoredLeatherArmor(Material.LEATHER_LEGGINGS, leatherColor);
        ItemStack boots = ColorUtils.createColoredLeatherArmor(Material.LEATHER_BOOTS, leatherColor);

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);

        // Give only boost item - no carrot needed
        ItemStack boostItem = new ItemStack(Material.FIREWORK_ROCKET);
        var boostMeta = boostItem.getItemMeta();
        if (boostMeta != null) {
            boostMeta.setDisplayName("§c§l⚡ SPEED BOOST ⚡");
            boostMeta.setLore(Arrays.asList(
                "§7Right-click to activate",
                "§e3x speed for 3 seconds!",
                "§c§lOne use only!"
            ));
            boostItem.setItemMeta(boostMeta);
        }
        player.getInventory().setItemInMainHand(boostItem);

        // Apply speed effect for faster movement
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));

        // Prevent fall damage
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 0, false, false));
    }

    /**
     * Spawn a pig for a player (PigRideListener controlled)
     */
    private void spawnPigForPlayer(Player player, PlayerData playerData) {
        Location spawnLocation = player.getLocation();

        // Spawn pig at arena floor level (reduce by 1 to place directly on floor)
        Location pigLocation = spawnLocation.clone();
        pigLocation.setY(spawnLocation.getBlockY() - 1); // Place pig directly on the arena floor

        Pig pig = (Pig) pigLocation.getWorld().spawnEntity(pigLocation, EntityType.PIG);

        // Configure pig for PigRideListener control
        pig.setCustomName(ColorUtils.getColoredPlayerName(player.getName(), playerData.getTrailMaterial()));
        pig.setCustomNameVisible(true);
        pig.setInvulnerable(true); // Prevent damage during game
        pig.setSilent(!soundsEnabled);
        pig.setAI(true); // Enable AI for movement - PigRideListener will control direction

        // Remove any existing potion effects
        pig.getActivePotionEffects().forEach(effect -> pig.removePotionEffect(effect.getType()));

        // Add saddle for rideable appearance
        pig.setSaddle(true);

        // Configure pig properties
        pig.setAdult(); // Ensure it's an adult pig
        pig.setBreed(false); // Disable breeding
        pig.setGravity(true); // Keep gravity for natural movement

        // Mount player on pig
        pig.addPassenger(player);
        plugin.getLogger().info("Mounted " + player.getName() + " on pig. Player gamemode: " + player.getGameMode());

        // Store pig reference
        playerData.setPig(pig);

        plugin.getLogger().info("Spawned pig for " + player.getName() + " - controlled by PigRideListener");
    }

    /**
     * Eliminate a player from the game
     */
    public void eliminatePlayer(UUID playerId, String reason) {
        PlayerData playerData = getPlayerData(playerId);
        if (playerData == null) return;

        Player player = playerData.getPlayer();
        if (player != null && player.isOnline()) {
            // Remove pig
            if (playerData.getPig() != null) {
                playerData.getPig().remove();
                playerData.setPig(null);
            }

            // Set spectator mode
            player.setGameMode(GameMode.SPECTATOR);

            // Clear effects
            player.getActivePotionEffects().forEach(effect ->
                player.removePotionEffect(effect.getType()));

            // Send elimination message
            String message = plugin.getConfig().getString("messages.player-eliminated", "&c{player} &7has been eliminated!")
                .replace("{player}", player.getName())
                .replace("{reason}", reason);

            Bukkit.broadcastMessage(ColorUtils.colorize(
                plugin.getConfig().getString("messages.prefix", "&6[Tron] &r") + message));
        }

        // Update game data
        gameData.eliminatePlayer(playerId);
        playerData.setAlive(false);
        playerData.incrementEliminations();
    }

    /**
     * Clean up player after game
     */
    public void cleanupPlayer(Player player) {
        PlayerData playerData = getPlayerData(player.getUniqueId());
        if (playerData != null) {
            // Remove pig
            if (playerData.getPig() != null) {
                playerData.getPig().remove();
                playerData.setPig(null);
            }

            // Increment games played
            playerData.incrementGamesPlayed();
        }

        // Reset player state - force spectator mode
        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().clear();
        player.getActivePotionEffects().forEach(effect ->
            player.removePotionEffect(effect.getType()));

        // Teleport to spawn location if available
        Location spawnLocation = playerSpawnLocations.get(player.getUniqueId());
        if (spawnLocation != null) {
            player.teleport(spawnLocation);
        }

        // Set health and food
        player.setHealth(20.0); // Set to full health
        player.setFoodLevel(20);
        player.setSaturation(20);
    }

    /**
     * Get player statistics as formatted string
     */
    public String getPlayerStats(Player player) {
        PlayerData playerData = getPlayerData(player.getUniqueId());
        if (playerData == null) {
            return ColorUtils.colorize("&cNo statistics found!");
        }

        return ColorUtils.colorize(String.format(
            "&6=== Tron Statistics for %s ===\n" +
            "&eGames Played: &f%d\n" +
            "&eGames Won: &f%d\n" +
            "&eWin Rate: &f%.1f%%\n" +
            "&eEliminations: &f%d",
            player.getName(),
            playerData.getGamesPlayed(),
            playerData.getGamesWon(),
            playerData.getWinRate(),
            playerData.getEliminations()
        ));
    }

    /**
     * Get pig location for a player
     */
    public Location getMountLocation(UUID playerId) {
        PlayerData playerData = getPlayerData(playerId);
        if (playerData != null && playerData.getPig() != null) {
            return playerData.getPig().getLocation();
        }
        return null;
    }



    /**
     * Update pig speed based on boost status
     * Note: Speed is now handled by PigRideListener directly
     */
    public void updatePigSpeed(UUID playerId) {
        // Speed is now handled by PigRideListener - this method kept for compatibility
        plugin.getLogger().fine("updatePigSpeed called for " + playerId + " - handled by PigRideListener");
    }





    /**
     * Cleanup all players
     */
    public void cleanup() {
        for (PlayerData playerData : playerDataMap.values()) {
            Player player = playerData.getPlayer();
            if (player != null && player.isOnline()) {
                cleanupPlayer(player);
            }
        }
        playerSpawnLocations.clear();
    }
}
