package Paris.listeners;

import Paris.Tron;
import Paris.data.GameData;
import Paris.data.PlayerData;
import Paris.managers.GameManager;
import Paris.managers.PlayerManager;

import Paris.managers.ArenaManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Pig;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Event listeners for the Tron minigame
 * Optimized with reverse lookup maps for O(1) entity-to-player mapping
 */
public class GameListeners implements Listener {

    private final Tron plugin;
    private final GameData gameData;
    private final GameManager gameManager;
    private final PlayerManager playerManager;
    private final ArenaManager arenaManager;
    private BukkitTask spectatorEnforcementTask;

    // Optimized reverse lookup maps for O(1) performance
    private final Map<Pig, UUID> pigToPlayerMap = new ConcurrentHashMap<>();
    private final Map<ArmorStand, UUID> armorStandToPlayerMap = new ConcurrentHashMap<>();

    public GameListeners(Tron plugin, GameData gameData, GameManager gameManager,
                        PlayerManager playerManager, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.gameData = gameData;
        this.gameManager = gameManager;
        this.playerManager = playerManager;
        this.arenaManager = arenaManager;

        // Start spectator mode enforcement task
        startSpectatorEnforcement();
    }

    /**
     * Start the spectator mode enforcement task
     */
    private void startSpectatorEnforcement() {
        spectatorEnforcementTask = new BukkitRunnable() {
            @Override
            public void run() {
                enforceSpectatorMode();
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every second
    }

    /**
     * Enforce spectator mode for all players not in active games
     */
    private void enforceSpectatorMode() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();

            // Skip players who are in active games OR during countdown
            if ((gameData.isActive() && gameData.isPlayerAlive(playerId)) ||
                (gameData.isStarting() && gameData.isActivePlayer(playerId))) {
                continue;
            }

            // Force spectator mode for all other players
            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SPECTATOR);
                plugin.getLogger().info("Enforced spectator mode for " + player.getName());
            }
        }
    }

    /**
     * Stop the spectator enforcement task
     */
    public void stopSpectatorEnforcement() {
        if (spectatorEnforcementTask != null) {
            spectatorEnforcementTask.cancel();
            spectatorEnforcementTask = null;
        }
    }

    /**
     * Register pig-to-player mapping for optimized lookups
     */
    public void registerPigMapping(Pig pig, UUID playerId) {
        pigToPlayerMap.put(pig, playerId);
    }

    /**
     * Register armor stand-to-player mapping for optimized lookups
     */
    public void registerArmorStandMapping(ArmorStand armorStand, UUID playerId) {
        armorStandToPlayerMap.put(armorStand, playerId);
    }

    /**
     * Remove pig mapping
     */
    public void removePigMapping(Pig pig) {
        pigToPlayerMap.remove(pig);
    }

    /**
     * Remove armor stand mapping
     */
    public void removeArmorStandMapping(ArmorStand armorStand) {
        armorStandToPlayerMap.remove(armorStand);
    }

    /**
     * Clear all entity mappings
     */
    public void clearEntityMappings() {
        pigToPlayerMap.clear();
        armorStandToPlayerMap.clear();
    }

    /**
     * Handle player join
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Create player data if needed
        playerManager.getOrCreatePlayerData(player.getUniqueId(), player.getName());

        // Immediately set to spectator mode
        player.setGameMode(GameMode.SPECTATOR);

        // Auto-queue if not AFK and has permission
        if (player.hasPermission("tron.play")) {
            PlayerData playerData = playerManager.getOrCreatePlayerData(player.getUniqueId(), player.getName());
            if (!playerData.isAFK() && gameData.isWaiting()) {
                playerManager.addToQueue(player);
            }
        }
    }

    /**
     * Handle player gamemode change
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Allow gamemode changes for players in active games OR during countdown
        if ((gameData.isActive() && gameData.isPlayerAlive(playerId)) ||
            (gameData.isStarting() && gameData.isActivePlayer(playerId))) {
            plugin.getLogger().info("Allowing gamemode change for " + player.getName() + " to " + event.getNewGameMode() + " (in game/countdown)");
            return;
        }

        // Allow admins to change gamemode
        if (player.hasPermission("tron.admin")) {
            return;
        }

        // Force spectator mode for all other players
        if (event.getNewGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(true);
            player.sendMessage("§cYou must be in spectator mode unless you're playing Tron!");
            plugin.getLogger().info("Cancelled gamemode change for " + player.getName() + " to " + event.getNewGameMode());
        }
    }

    /**
     * Handle player quit
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Player data is automatically saved (no economy system)

        // Handle if player is in game or starting
        if (gameData.isActivePlayer(playerId) || (gameData.isStarting() && gameData.isInQueue(playerId))) {
            if (gameData.isPlayerAlive(playerId)) {
                // Eliminate player for leaving
                gameManager.eliminatePlayer(playerId, "disconnected");
            }

            // Clean up player and pig references
            cleanupPlayerOnLeave(player);
        } else if (gameData.isInQueue(playerId)) {
            // Remove from queue
            playerManager.removeFromQueue(player);
        }
    }

    /**
     * Comprehensive cleanup when player leaves during game or countdown
     */
    private void cleanupPlayerOnLeave(Player player) {
        UUID playerId = player.getUniqueId();

        // Clean up through player manager
        playerManager.cleanupPlayer(player);

        // Clean up pig ride listener tracking
        if (plugin instanceof Paris.Tron) {
            Paris.Tron tronPlugin = (Paris.Tron) plugin;
            if (tronPlugin.getPigRideListener() != null) {
                tronPlugin.getPigRideListener().cleanupPlayer(playerId);
            }
        }

        // Remove from game data
        gameData.removeActivePlayer(playerId);
        gameData.removeFromQueue(playerId);

        plugin.getLogger().info("Comprehensive cleanup completed for " + player.getName() + " who left during game/countdown");
    }

    /**
     * Handle player movement (for immediate collision detection and boundary checking)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Only process if player is in active game
        if (!gameData.isActive() || !gameData.isPlayerAlive(playerId)) {
            return;
        }

        // Check if player moved to a different location (not just block)
        if (event.getFrom().distanceSquared(event.getTo()) < 0.01) {
            return; // Minimal movement, ignore
        }

        // Check arena boundaries first
        if (!arenaManager.isWithinArena(event.getTo())) {
            // Eliminate player for leaving arena
            gameManager.eliminatePlayer(playerId, "left arena");
            return;
        }

        // Immediate collision detection for responsive gameplay
        // This supplements the tick-based detection in GameManager
        if (event.getTo() != null) {
            // Use TrailManager's enhanced collision detection
            // Note: We can't directly access trailManager here, so we'll rely on the GameManager's tick-based system
            // The enhanced collision detection in TrailManager will handle this
        }
    }

    /**
     * Handle player teleport
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Prevent teleporting during active game (except admin teleports)
        if (gameData.isActive() && gameData.isPlayerAlive(playerId)) {
            if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN &&
                event.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) {

                if (!player.hasPermission("tron.admin")) {
                    event.setCancelled(true);
                    player.sendMessage("§cYou cannot teleport during a Tron game!");
                }
            }
        }
    }

    /**
     * Handle entity damage - Optimized with O(1) lookup
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        // Prevent damage to pigs in game - O(1) lookup instead of O(n)
        if (event.getEntity() instanceof Pig) {
            Pig pig = (Pig) event.getEntity();

            // Use optimized reverse lookup map
            UUID playerId = pigToPlayerMap.get(pig);
            if (playerId != null && gameData.isActivePlayer(playerId)) {
                event.setCancelled(true);
                return;
            }
        }

        // Prevent damage to players in game
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            UUID playerId = player.getUniqueId();

            if (gameData.isActivePlayer(playerId)) {
                // Cancel most damage types, but allow elimination through collision
                if (event.getCause() != EntityDamageEvent.DamageCause.CUSTOM) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * Handle entity death - Optimized with O(1) lookup
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        // Prevent pig death and drops - O(1) lookup instead of O(n)
        if (event.getEntity() instanceof Pig) {
            Pig pig = (Pig) event.getEntity();

            // Use optimized reverse lookup map
            UUID playerId = pigToPlayerMap.get(pig);
            if (playerId != null && gameData.isActivePlayer(playerId)) {
                event.getDrops().clear();
                event.setDroppedExp(0);

                // Eliminate the player
                gameManager.eliminatePlayer(playerId, "pig died");

                // Clean up mapping
                pigToPlayerMap.remove(pig);
                return;
            }
        }
    }

    /**
     * Handle vehicle exit (dismounting pig or armor stand)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player) {
            Player player = (Player) event.getExited();
            UUID playerId = player.getUniqueId();

            // Prevent dismounting during active game or starting sequence
            if ((gameData.isActive() || gameData.isStarting()) && gameData.isPlayerAlive(playerId)) {
                PlayerData playerData = playerManager.getPlayerData(playerId);
                if (playerData != null) {
                    // Check if dismounting from game pig
                    if (event.getVehicle() instanceof Pig && event.getVehicle().equals(playerData.getPig())) {
                        event.setCancelled(true);
                        if (gameData.isStarting()) {
                            player.sendMessage("§c§lYou cannot dismount during the countdown!");
                        } else {
                            player.sendMessage("§c§lYou cannot dismount during the game!");
                        }
                        return;
                    }

                    // Note: TronMountManager system removed - now using simple pig mounting
                }
            }
        }
    }

    /**
     * Handle player sneak (prevent dismounting via shift key)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Prevent sneaking to dismount during active game
        if (gameData.isActive() && gameData.isPlayerAlive(playerId) && event.isSneaking()) {
            if (player.getVehicle() != null) {
                // Player is trying to sneak while mounted - cancel to prevent dismounting
                event.setCancelled(true);
            }
        }
    }



    /**
     * Handle player command
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Restrict commands during active game
        if (gameData.isActive() && gameData.isPlayerAlive(playerId)) {
            String command = event.getMessage().toLowerCase();

            // Allow Tron-related commands and admin commands
            if (!command.startsWith("/tron") &&
                !command.startsWith("/tr") &&
                !command.startsWith("/afk") &&
                !command.startsWith("/stats") &&
                !player.hasPermission("tron.admin")) {

                event.setCancelled(true);
                player.sendMessage("§cYou can only use Tron commands during the game!");
            }
        }
    }

    /**
     * Handle player chat
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Add game status to chat format for active players
        if (gameData.isActivePlayer(playerId)) {
            PlayerData playerData = playerManager.getPlayerData(playerId);
            if (playerData != null) {
                String coloredName = playerData.getTrailMaterial() != null ?
                    Paris.utils.ColorUtils.getChatColorForMaterial(playerData.getTrailMaterial()) + player.getName() :
                    player.getName();

                event.setFormat("§7[§6Tron§7] " + coloredName + "§r: " + event.getMessage());
            }
        }
    }

    /**
     * Handle world load
     */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        // Set world rules for arena world
        if (event.getWorld().getName().equals(plugin.getConfig().getString("arena.world-name", "tron_arena"))) {
            event.getWorld().setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            event.getWorld().setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
            event.getWorld().setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            event.getWorld().setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
        }
    }

    /**
     * Handle player respawn
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // If player was in game, clean them up
        if (gameData.isActivePlayer(playerId)) {
            // Delay cleanup to avoid conflicts
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                playerManager.cleanupPlayer(player);
            }, 1L);
        }
    }

    /**
     * Handle player item drop
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Prevent item dropping during game
        if (gameData.isActivePlayer(playerId)) {
            event.setCancelled(true);
        }
    }

    /**
     * Handle player pickup item
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPickupItem(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            UUID playerId = player.getUniqueId();

            // Prevent item pickup during game
            if (gameData.isActivePlayer(playerId)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Handle pig mounting for easy mounting
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPigMount(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Pig pig) {
            Player player = event.getPlayer();
            UUID playerId = player.getUniqueId();

            // Only allow mounting during active game
            if (gameData.isActive() && gameData.isPlayerAlive(playerId)) {
                if (!pig.getPassengers().contains(player)) {
                    pig.setSaddle(true);
                    pig.addPassenger(player);
                }
            }
        }
    }

    /**
     * Handle boost item activation
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Only process if player is in active game
        if (!gameData.isActive() || !gameData.isPlayerAlive(playerId)) {
            return;
        }

        // Check if right-clicking with boost item
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (item != null && item.getType() == Material.FIREWORK_ROCKET) {
                PlayerData playerData = playerManager.getPlayerData(playerId);
                if (playerData != null && playerData.hasBoost()) {
                    // Get boost settings from config
                    int boostDuration = plugin.getConfig().getInt("game.boost-duration", 3);
                    double boostMultiplier = plugin.getConfig().getDouble("game.boost-multiplier", 7.0);

                    // Activate boost
                    playerData.activateBoost(boostDuration);

                    // Update pig speed to boost speed
                    playerManager.updatePigSpeed(playerId);

                    // Remove item from inventory
                    item.setAmount(item.getAmount() - 1);

                    // Effects
                    player.sendMessage("§a⚡ SPEED BOOST ACTIVATED! §e" + (int)boostMultiplier + "x speed for " + boostDuration + " seconds!");
                    player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 2.0f);
                    player.sendTitle("§c§l⚡ BOOST! ⚡", "§e" + (int)boostMultiplier + "x Speed!", 0, 60, 20);

                    // Cancel the event to prevent other interactions
                    event.setCancelled(true);
                } else {
                    player.sendMessage("§cYou have already used your boost!");
                    event.setCancelled(true);
                }
            }
        }
    }
}
