package Paris.managers;

import Paris.Tron;
import Paris.data.GameData;
import Paris.data.PlayerData;
import Paris.utils.ColorUtils;
import Paris.utils.ParticleUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pig;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Main game manager that handles game flow, states, and coordination
 */
public class GameManager {

    private final Tron plugin;
    private final GameData gameData;
    private final PlayerManager playerManager;
    private final TrailManager trailManager;
    private final ArenaManager arenaManager;

    // Configuration
    private int minPlayers;
    private int maxPlayers;
    private int countdownTime;
    private int maxGameTime;
    private boolean soundsEnabled;
    private double boostMultiplier;
    private int boostDuration;
    private double trailDelayDistance;

    // Debug counter
    private int debugCounter = 0;

    // Game tasks
    private BukkitTask countdownTask;
    private BukkitTask gameTask;
    private BukkitTask movementTask;
    private BukkitTask pigMovementTask;

    // UI Elements
    private BossBar countdownBar;
    private BossBar gameBar;

    public GameManager(Tron plugin, GameData gameData, PlayerManager playerManager,
                      TrailManager trailManager, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.gameData = gameData;
        this.playerManager = playerManager;
        this.trailManager = trailManager;
        this.arenaManager = arenaManager;

        loadConfiguration();
        createBossBars();
        startAutoQueue();
    }

    private void loadConfiguration() {
        this.minPlayers = plugin.getConfig().getInt("game.min-players", 2);
        this.maxPlayers = plugin.getConfig().getInt("game.max-players", 8);
        this.countdownTime = plugin.getConfig().getInt("game.countdown-time", 5);
        this.maxGameTime = plugin.getConfig().getInt("game.max-game-time", 300);
        this.soundsEnabled = plugin.getConfig().getBoolean("game.sounds", true);
        this.boostMultiplier = plugin.getConfig().getDouble("game.boost-multiplier", 7.0);
        this.boostDuration = plugin.getConfig().getInt("game.boost-duration", 3);
        this.trailDelayDistance = plugin.getConfig().getDouble("trails.delay-distance", 2.0);

        plugin.getLogger().info("[GameManager] Trail delay distance set to " + trailDelayDistance + " blocks");
    }

    private void createBossBars() {
        this.countdownBar = Bukkit.createBossBar(
            "Game starting...",
            BarColor.GREEN,
            BarStyle.SOLID
        );

        this.gameBar = Bukkit.createBossBar(
            "Tron Game",
            BarColor.BLUE,
            BarStyle.SOLID
        );
    }

    /**
     * Start the auto-queue system
     */
    private void startAutoQueue() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Auto-queue online players who aren't AFK
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission("tron.play")) {
                        PlayerData playerData = playerManager.getPlayerData(player);
                        if (playerData != null && !playerData.isAFK() &&
                            !gameData.isInQueue(player.getUniqueId()) &&
                            !gameData.isActivePlayer(player.getUniqueId())) {
                            playerManager.addToQueue(player);
                        }
                    }
                }

                // Check if we can start a game
                checkGameStart();
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second
    }

    /**
     * Check if a game can be started
     */
    public void checkGameStart() {
        // Only start games when in WAITING state (not during active games)
        if (!gameData.isWaiting()) {
            // Removed logging to prevent console spam during active games
            return;
        }

        int queueSize = gameData.getQueueSize();
        if (queueSize >= minPlayers) {
            plugin.getLogger().info("Starting countdown with " + queueSize + " players");
            startCountdown();
        }
    }

    /**
     * Force start a game (admin command)
     */
    public boolean forceStart() {
        if (gameData.isActive() || gameData.isStarting()) {
            return false; // Game already running
        }

        if (gameData.getQueueSize() < minPlayers) {
            return false; // Need at least minimum players
        }

        startCountdown();
        return true;
    }

    /**
     * Start the countdown phase
     */
    private void startCountdown() {
        gameData.setGameState(GameData.GameState.STARTING);
        gameData.setCountdownTime(countdownTime);

        // Setup arena
        arenaManager.prepareArena();

        // Add queued players to game
        gameData.startGame();

        // Setup players
        List<Location> spawnLocations = arenaManager.getSpawnLocations(gameData.getActivePlayerCount());
        playerManager.setupPlayersForGame(spawnLocations);

        // Start countdown
        startCountdownTask();

        // Notify players
        broadcastMessage("game-starting", countdownTime);
    }

    /**
     * Start the countdown task
     */
    private void startCountdownTask() {
        final int[] timeLeft = {countdownTime};

        // Add players to countdown bar
        for (UUID playerId : gameData.getActivePlayers()) {
            PlayerData playerData = playerManager.getPlayerData(playerId);
            if (playerData != null && playerData.getPlayer() != null) {
                countdownBar.addPlayer(playerData.getPlayer());
            }
        }

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Check if we still have enough players to continue
                int activePlayerCount = gameData.getActivePlayerCount();
                if (activePlayerCount < minPlayers) {
                    plugin.getLogger().info("Not enough players remaining during countdown (" + activePlayerCount + "/" + minPlayers + ") - cancelling game");
                    cancelCountdown();
                    cancel();
                    return;
                }

                if (timeLeft[0] <= 0) {
                    startGame();
                    cancel();
                    return;
                }

                // Update boss bar with dramatic countdown
                String countdownTitle;
                if (timeLeft[0] <= 3) {
                    countdownTitle = "§c§l" + timeLeft[0] + " §r§cGET READY!";
                } else {
                    countdownTitle = "§e§lGame starting in §f§l" + timeLeft[0] + " §e§lseconds";
                }
                countdownBar.setTitle(countdownTitle);
                countdownBar.setProgress((double) timeLeft[0] / countdownTime);

                // Change boss bar color based on countdown
                if (timeLeft[0] <= 3) {
                    countdownBar.setColor(BarColor.RED);
                } else {
                    countdownBar.setColor(BarColor.YELLOW);
                }

                // Spawn countdown particles
                if (arenaManager.getArenaCenter() != null) {
                    ParticleUtils.spawnCountdownParticles(arenaManager.getArenaCenter(), timeLeft[0]);
                }

                // Enhanced sound and visual effects
                if (soundsEnabled) {
                    for (UUID playerId : gameData.getActivePlayers()) {
                        PlayerData playerData = playerManager.getPlayerData(playerId);
                        if (playerData != null && playerData.getPlayer() != null) {
                            Player player = playerData.getPlayer();

                            // Different sounds for different countdown phases
                            if (timeLeft[0] <= 3) {
                                // Final countdown - dramatic beep
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 2.0f, 2.0f);
                                player.sendTitle("§c§l" + timeLeft[0], "§7Get ready to ride!", 0, 20, 10);
                            } else {
                                // Regular countdown beep
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                                player.sendTitle("§e§l" + timeLeft[0], "§7Game starting soon...", 0, 20, 10);
                            }
                        }
                    }
                }

                timeLeft[0]--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Cancel countdown due to insufficient players
     */
    private void cancelCountdown() {
        gameData.setGameState(GameData.GameState.WAITING);

        // Clean up all players who were in the starting game
        for (UUID playerId : new HashSet<>(gameData.getActivePlayers())) {
            PlayerData playerData = playerManager.getPlayerData(playerId);
            if (playerData != null && playerData.getPlayer() != null) {
                Player player = playerData.getPlayer();

                // Clean up pig and reset player
                playerManager.cleanupPlayer(player);

                // Move back to queue if they're still online
                if (player.isOnline()) {
                    gameData.addToQueue(playerId);
                    player.sendMessage("§c§lCountdown cancelled - not enough players remaining!");
                }
            }
        }

        // Reset game data
        gameData.reset();

        // Remove countdown bar
        countdownBar.removeAll();

        plugin.getLogger().info("Countdown cancelled due to insufficient players");
    }

    /**
     * Start the actual game
     */
    private void startGame() {
        gameData.setGameState(GameData.GameState.ACTIVE);
        gameData.setGameStartTime(System.currentTimeMillis());

        // Remove countdown bar and add game bar
        countdownBar.removeAll();

        for (UUID playerId : gameData.getActivePlayers()) {
            PlayerData playerData = playerManager.getPlayerData(playerId);
            if (playerData != null && playerData.getPlayer() != null) {
                gameBar.addPlayer(playerData.getPlayer());
            }
        }

        // Start game tasks
        startGameTasks();

        // Notify players with dramatic start
        broadcastMessage("game-started");

        // Enhanced start effects
        if (soundsEnabled) {
            for (UUID playerId : gameData.getActivePlayers()) {
                PlayerData playerData = playerManager.getPlayerData(playerId);
                if (playerData != null && playerData.getPlayer() != null) {
                    Player player = playerData.getPlayer();

                    // Dramatic start sound
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);

                    // Start title
                    player.sendTitle("§a§lGO!", "§7Avoid the trails!", 0, 40, 20);

                    // Additional start sound
                    player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);
                }
            }
        }
    }

    /**
     * Start game monitoring tasks
     */
    private void startGameTasks() {
        startMainGameTask();
        startMovementAndTrailTask();

        // Start world border closing after a delay (30 seconds)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (gameData.getGameState() == GameData.GameState.ACTIVE) {
                    arenaManager.startWorldBorderClosing();

                    // Announce to players
                    for (UUID playerId : gameData.getActivePlayers()) {
                        PlayerData playerData = playerManager.getPlayerData(playerId);
                        if (playerData != null && playerData.getPlayer() != null) {
                            Player player = playerData.getPlayer();
                            player.sendMessage(ColorUtils.colorize("&c&l⚠ World border is now closing! ⚠"));
                            player.sendTitle(
                                ColorUtils.colorize("&c&lWORLD BORDER"),
                                ColorUtils.colorize("&eThe border is closing in!"),
                                10, 40, 10
                            );
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 600L); // 30 seconds delay
    }

    /**
     * Start the main game monitoring task
     */
    private void startMainGameTask() {
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateGame();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Start movement and trail generation task
     */
    private void startMovementAndTrailTask() {
        movementTask = new BukkitRunnable() {
            private int trailCounter = 0;
            private int gameTickCounter = 0;
            private final int trailInterval = plugin.getConfig().getInt("game.trail-interval", 1);
            private final int collisionGracePeriod = 20; // 1 second grace period
            private boolean collisionStarted = false;

            @Override
            public void run() {
                gameTickCounter++;

                if (gameTickCounter == collisionGracePeriod && !collisionStarted) {
                    collisionStarted = true;
                }

                // Arena boundary checks (PigRideListener handles primary collision detection)
                if (gameTickCounter > collisionGracePeriod) {
                    updatePlayerMovement();
                }

                // Generate trails after initial delay
                if (gameTickCounter > 10 && trailCounter % trailInterval == 0) {
                    updatePlayerTrails();
                }
                trailCounter++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Update game state
     */
    private void updateGame() {
        if (!gameData.isActive()) return;

        // Update boss bar
        int aliveCount = gameData.getAlivePlayerCount();
        gameBar.setTitle("Players alive: " + aliveCount);

        // Check for game end conditions
        if (gameData.shouldEnd()) {
            endGame();
            return;
        }

        // Check for timeout
        if (maxGameTime > 0 && gameData.getGameDuration() > maxGameTime * 1000L) {
            endGame("timeout");
        }
    }

    /**
     * Update player movement and check for collisions (runs every tick)
     * Note: Most collision detection is now handled by PigRideListener for better performance
     */
    private void updatePlayerMovement() {
        if (!gameData.isActive()) return;

        for (UUID playerId : gameData.getAlivePlayers()) {
            PlayerData playerData = playerManager.getPlayerData(playerId);
            if (playerData == null) continue;

            // Check if boost has expired and update pig speed
            if (playerData.isBoosting() && !playerData.isBoostActive()) {
                playerData.setBoosting(false);
                playerManager.updatePigSpeed(playerId); // Reset to normal speed

                Player player = playerData.getPlayer();
                if (player != null) {
                    player.sendMessage("§c⚡ Speed boost expired!");
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
                }
            }

            // Arena boundary check (backup - PigRideListener handles primary collision detection)
            if (playerData.getPig() != null) {
                Location currentLocation = playerData.getPig().getLocation();
                if (!arenaManager.isWithinArena(currentLocation)) {
                    eliminatePlayer(playerId, "hit arena boundary");
                }
            }
        }
    }

    /**
     * Update player trails (runs at trail interval)
     */
    private void updatePlayerTrails() {
        if (!gameData.isActive()) return;

        for (UUID playerId : gameData.getAlivePlayers()) {
            PlayerData playerData = playerManager.getPlayerData(playerId);
            if (playerData == null || playerData.getPig() == null) continue;

            Pig pig = playerData.getPig();
            Location currentLocation = pig.getLocation();
            Location lastLocation = playerData.getLastLocation();

            // Generate trail if pig moved to a different block
            if (lastLocation != null && !isSameBlock(currentLocation, lastLocation)) {
                plugin.getLogger().info("[GameManager] Player " + playerId + " moved from " +
                                       lastLocation.getBlockX() + "," + lastLocation.getBlockZ() + " to " +
                                       currentLocation.getBlockX() + "," + currentLocation.getBlockZ());

                // For glass panes, always use enhanced trail generation to prevent gaps
                if (playerData != null && playerData.getTrailMaterial() != null &&
                    playerData.getTrailMaterial().name().contains("GLASS_PANE")) {

                    double distance = lastLocation.distance(currentLocation);
                    plugin.getLogger().info("[GameManager] Glass pane movement detected (" +
                                           String.format("%.2f", distance) + " blocks), using enhanced trail generation");
                    generateContinuousGlassPaneTrail(lastLocation, currentLocation, playerId);
                } else {
                    // Normal trail generation for non-glass materials
                    generateSimpleTrail(lastLocation, currentLocation, playerId);
                }
            }

            // Update last location with pig's current location
            playerData.setLastLocation(currentLocation.clone());
        }
    }

    /**
     * Generate single block trail with smart diagonal handling
     */
    private void generateSimpleTrail(Location lastLocation, Location currentLocation, UUID playerId) {
        // Calculate movement vector
        double deltaX = currentLocation.getX() - lastLocation.getX();
        double deltaZ = currentLocation.getZ() - lastLocation.getZ();
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        if (distance < 0.2) {
            // Very small movement, skip to prevent over-generation
            return;
        }

        // For glass panes, use more sensitive detection to prevent gaps
        PlayerData playerData = playerManager.getPlayerData(playerId);
        if (playerData != null && playerData.getTrailMaterial() != null &&
            playerData.getTrailMaterial().name().contains("GLASS_PANE")) {

            // For glass panes, generate trails more frequently to prevent gaps
            if (distance > 2.0) {
                plugin.getLogger().info("[GameManager] Fast glass pane movement detected (" +
                                       String.format("%.2f", distance) + " blocks), ensuring gap filling");
            }
        }

        // Generate single trail block at the last location only
        // Use 1-block spacing for clean, single-width trails
        plugin.getLogger().info("[GameManager] Generating trail for player " + playerId +
                               " at " + lastLocation.getBlockX() + "," + lastLocation.getBlockZ() +
                               " (distance: " + String.format("%.2f", distance) + ")");
        trailManager.generateTrail(lastLocation, playerId);
    }

    /**
     * Generate continuous glass pane trail with comprehensive gap prevention
     */
    private void generateContinuousGlassPaneTrail(Location lastLocation, Location currentLocation, UUID playerId) {
        // Use Bresenham-like algorithm to ensure every block in the path is covered
        int x1 = lastLocation.getBlockX();
        int z1 = lastLocation.getBlockZ();
        int x2 = currentLocation.getBlockX();
        int z2 = currentLocation.getBlockZ();

        plugin.getLogger().info("[GameManager] Generating continuous glass pane trail from (" +
                               x1 + "," + z1 + ") to (" + x2 + "," + z2 + ")");

        // Get all blocks in the path using Bresenham line algorithm
        java.util.List<Location> pathBlocks = getBresenhamLine(lastLocation, x1, z1, x2, z2);

        // Generate trail at each block in the path
        for (Location pathBlock : pathBlocks) {
            trailManager.generateTrail(pathBlock, playerId);
            plugin.getLogger().fine("[GameManager] Generated glass pane trail at " +
                                   pathBlock.getBlockX() + "," + pathBlock.getBlockZ());
        }

        plugin.getLogger().info("[GameManager] Generated " + pathBlocks.size() + " glass pane trail blocks");
    }

    /**
     * Get all blocks in a line using Bresenham line algorithm
     */
    private java.util.List<Location> getBresenhamLine(Location baseLocation, int x1, int z1, int x2, int z2) {
        java.util.List<Location> blocks = new java.util.ArrayList<>();

        int dx = Math.abs(x2 - x1);
        int dz = Math.abs(z2 - z1);
        int x = x1;
        int z = z1;
        int n = 1 + dx + dz;
        int x_inc = (x2 > x1) ? 1 : -1;
        int z_inc = (z2 > z1) ? 1 : -1;
        int error = dx - dz;

        dx *= 2;
        dz *= 2;

        for (; n > 0; --n) {
            // Add current block to path
            Location blockLoc = new Location(baseLocation.getWorld(), x, baseLocation.getY(), z);
            blocks.add(blockLoc);

            if (error > 0) {
                x += x_inc;
                error -= dz;
            } else {
                z += z_inc;
                error += dx;
            }
        }

        return blocks;
    }





    /**
     * Check if two locations are in the same block
     */
    private boolean isSameBlock(Location loc1, Location loc2) {
        return loc1.getBlockX() == loc2.getBlockX() &&
               loc1.getBlockY() == loc2.getBlockY() &&
               loc1.getBlockZ() == loc2.getBlockZ();
    }

    /**
     * Eliminate a player
     */
    public void eliminatePlayer(UUID playerId, String reason) {
        PlayerData playerData = playerManager.getPlayerData(playerId);
        if (playerData == null) return;

        // Spawn elimination particles
        if (playerData.getPlayer() != null) {
            ParticleUtils.spawnEliminationParticles(
                playerData.getPlayer().getLocation(),
                playerData.getTrailMaterial()
            );
        }

        // Eliminate player
        playerManager.eliminatePlayer(playerId, reason);

        // Check if game should end
        if (gameData.shouldEnd()) {
            endGame();
        }
    }

    /**
     * End the game
     */
    public void endGame() {
        endGame(null);
    }

    public void endGame(String reason) {
        if (!gameData.isActive() && !gameData.isStarting()) return;

        gameData.setGameState(GameData.GameState.ENDING);
        gameData.setGameEndTime(System.currentTimeMillis());

        // Stop tasks
        stopTasks();

        // Determine winner
        UUID winnerId = gameData.determineWinner();
        if (winnerId != null) {
            PlayerData winner = playerManager.getPlayerData(winnerId);
            if (winner != null) {
                winner.incrementGamesWon();

                // Spawn victory particles
                if (winner.getPlayer() != null) {
                    ParticleUtils.spawnVictoryParticles(
                        winner.getPlayer().getLocation(),
                        winner.getTrailMaterial()
                    );
                }

                // Broadcast winner
                broadcastMessage("game-won", winner.getPlayerName());
            }
        }

        // Cleanup
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanup();
            }
        }.runTaskLater(plugin, 100L); // 5 seconds delay
    }

    /**
     * Cleanup after game
     */
    private void cleanup() {
        // Remove boss bars
        gameBar.removeAll();
        countdownBar.removeAll();

        // Cleanup players
        for (UUID playerId : gameData.getActivePlayers()) {
            PlayerData playerData = playerManager.getPlayerData(playerId);
            if (playerData != null && playerData.getPlayer() != null) {
                playerManager.cleanupPlayer(playerData.getPlayer());
            }
        }

        // Clear trails
        trailManager.clearAllTrails();

        // Reset arena and stop world border
        arenaManager.resetArena();
        arenaManager.stopWorldBorder();

        // Reset game data
        gameData.reset();
    }

    /**
     * Stop all tasks
     */
    private void stopTasks() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }

        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }

        if (pigMovementTask != null) {
            pigMovementTask.cancel();
            pigMovementTask = null;
        }
    }

    /**
     * Broadcast a message to all players
     */
    private void broadcastMessage(String key, Object... args) {
        String message = plugin.getConfig().getString("messages." + key, "");
        String prefix = plugin.getConfig().getString("messages.prefix", "&6[Tron] &r");

        for (Object arg : args) {
            message = message.replace("{" + Arrays.asList(args).indexOf(arg) + "}", arg.toString());
        }
        message = message.replace("{time}", String.valueOf(args.length > 0 ? args[0] : ""));
        message = message.replace("{player}", String.valueOf(args.length > 0 ? args[0] : ""));

        Bukkit.broadcastMessage(ColorUtils.colorize(prefix + message));
    }

    /**
     * Get current game state
     */
    public GameData.GameState getGameState() {
        return gameData.getGameState();
    }

    /**
     * Check if a player can join the queue
     */
    public boolean canJoinQueue(Player player) {
        // Allow joining queue even during active games (they'll be queued for next game)
        return gameData.getQueueSize() < maxPlayers &&
               !gameData.isInQueue(player.getUniqueId());
    }

    /**
     * Shutdown cleanup
     */
    public void shutdown() {
        stopTasks();
        gameBar.removeAll();
        countdownBar.removeAll();
        cleanup();
    }
}
