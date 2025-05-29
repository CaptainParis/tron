package Paris.commands;

import Paris.Tron;
import Paris.data.GameData;
import Paris.managers.GameManager;
import Paris.managers.PlayerManager;

import Paris.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.UUID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Command handler for the Tron minigame
 */
public class TronCommands implements CommandExecutor, TabCompleter {

    private final Tron plugin;
    private final GameManager gameManager;
    private final PlayerManager playerManager;
    private final GameData gameData;

    public TronCommands(Tron plugin, GameManager gameManager, PlayerManager playerManager, GameData gameData) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.playerManager = playerManager;
        this.gameData = gameData;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase();

        switch (commandName) {
            case "tron":
            case "tr":
                return handleTronCommand(sender, args);
            case "start":
                return handleStartCommand(sender);
            case "afk":
                return handleAFKCommand(sender);
            case "stats":
                return handleStatsCommand(sender, args);
            case "forcegame":
                return handleForceGameCommand(sender, args);
            case "endgame":
                return handleEndGameCommand(sender);
            case "forceleave":
                return handleForceLeaveCommand(sender, args);

            default:
                return false;
        }
    }

    /**
     * Handle main tron command
     */
    private boolean handleTronCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendTronHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            case "join":
                return handleJoinCommand(sender);
            case "leave":
                return handleLeaveCommand(sender);
            case "info":
                return handleInfoCommand(sender);
            case "leaderboard":
            case "top":
                return handleLeaderboardCommand(sender, subArgs);
            case "reload":
                return handleReloadCommand(sender);
            case "testglass":
                return handleTestGlassCommand(sender);
            case "testtrail":
                return handleTestTrailCommand(sender);
            case "forceleave":
                return handleForceLeaveCommand(sender, subArgs);
            default:
                sendTronHelp(sender);
                return true;
        }
    }

    /**
     * Send help message
     */
    private void sendTronHelp(CommandSender sender) {
        sender.sendMessage(ColorUtils.colorize(
            "&6=== Tron Commands ===\n" +
            "&e/tron join &7- Join the game queue\n" +
            "&e/tron leave &7- Leave the game queue\n" +
            "&e/tron info &7- Show game information\n" +
            "&e/tron top &7- Show leaderboard\n" +
            "&e/afk &7- Toggle AFK status\n" +
            "&e/stats [player] &7- Show statistics\n" +
            (sender.hasPermission("tron.admin") ?
                "&c/start &7- Force start game (admin)\n" +
                "&c/forcegame <player> &7- Force player into game (admin)\n" +
                "&c/endgame &7- Force end current game (admin)\n" +
                "&c/forceleave [player] &7- Force leave all plugin effects (admin)\n" +
                "&c/tron reload &7- Reload configuration (admin)" : "")
        ));
    }

    /**
     * Handle join command
     */
    private boolean handleJoinCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize("&cOnly players can use this command!"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("tron.play")) {
            player.sendMessage(ColorUtils.colorize("&cYou don't have permission to play Tron!"));
            return true;
        }

        if (!gameManager.canJoinQueue(player)) {
            if (gameData.isInQueue(player.getUniqueId())) {
                player.sendMessage(ColorUtils.colorize(getConfigMessage("already-in-queue")));
            } else {
                player.sendMessage(ColorUtils.colorize("&cCannot join queue at this time! (Queue may be full)"));
            }
            return true;
        }

        if (playerManager.addToQueue(player)) {
            player.sendMessage(ColorUtils.colorize(getConfigMessage("joined-queue")));
            gameManager.checkGameStart();
        } else {
            player.sendMessage(ColorUtils.colorize("&cFailed to join queue!"));
        }

        return true;
    }

    /**
     * Handle leave command
     */
    private boolean handleLeaveCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize("&cOnly players can use this command!"));
            return true;
        }

        Player player = (Player) sender;

        if (playerManager.removeFromQueue(player)) {
            player.sendMessage(ColorUtils.colorize(getConfigMessage("left-queue")));
        } else {
            player.sendMessage(ColorUtils.colorize(getConfigMessage("not-in-queue")));
        }

        return true;
    }

    /**
     * Handle info command
     */
    private boolean handleInfoCommand(CommandSender sender) {
        GameData.GameState state = gameManager.getGameState();
        int queueSize = gameData.getQueueSize();
        int activeCount = gameData.getActivePlayerCount();
        int aliveCount = gameData.getAlivePlayerCount();

        String stateString;
        switch (state) {
            case WAITING:
                stateString = "&aWaiting for players";
                break;
            case STARTING:
                stateString = "&eStarting...";
                break;
            case ACTIVE:
                stateString = "&cGame in progress";
                break;
            case ENDING:
                stateString = "&6Game ending";
                break;
            default:
                stateString = "&7Unknown";
        }

        sender.sendMessage(ColorUtils.colorize(
            "&6=== Tron Game Info ===\n" +
            "&eState: " + stateString + "\n" +
            "&eQueued Players: &f" + queueSize + "\n" +
            "&eActive Players: &f" + activeCount + "\n" +
            "&eAlive Players: &f" + aliveCount
        ));

        return true;
    }

    /**
     * Handle leaderboard command
     */
    private boolean handleLeaderboardCommand(CommandSender sender, String[] args) {
        int limit = 10;
        if (args.length > 0) {
            try {
                limit = Integer.parseInt(args[0]);
                limit = Math.max(1, Math.min(limit, 50)); // Clamp between 1 and 50
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.colorize("&cInvalid number: " + args[0]));
                return true;
            }
        }

        // Economy system removed - show simple message
        sender.sendMessage(ColorUtils.colorize("&cLeaderboard feature has been removed."));

        return true;
    }

    /**
     * Handle reload command
     */
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("tron.admin")) {
            sender.sendMessage(ColorUtils.colorize("&cYou don't have permission to use this command!"));
            return true;
        }

        plugin.reloadConfig();
        sender.sendMessage(ColorUtils.colorize("&aTron configuration reloaded!"));
        return true;
    }

    /**
     * Handle start command
     */
    private boolean handleStartCommand(CommandSender sender) {
        if (!sender.hasPermission("tron.admin")) {
            sender.sendMessage(ColorUtils.colorize("&cYou don't have permission to use this command!"));
            return true;
        }

        if (gameManager.forceStart()) {
            sender.sendMessage(ColorUtils.colorize("&aForce starting Tron game!"));
        } else {
            if (gameData.isActive() || gameData.isStarting()) {
                sender.sendMessage(ColorUtils.colorize(getConfigMessage("game-in-progress")));
            } else {
                sender.sendMessage(ColorUtils.colorize(getConfigMessage("insufficient-players")));
            }
        }

        return true;
    }

    /**
     * Handle AFK command
     */
    private boolean handleAFKCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize("&cOnly players can use this command!"));
            return true;
        }

        Player player = (Player) sender;
        boolean isAFK = playerManager.toggleAFK(player);

        if (isAFK) {
            player.sendMessage(ColorUtils.colorize(getConfigMessage("afk-enabled")));
        } else {
            player.sendMessage(ColorUtils.colorize(getConfigMessage("afk-disabled")));
        }

        return true;
    }

    /**
     * Handle stats command
     */
    private boolean handleStatsCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tron.stats")) {
            sender.sendMessage(ColorUtils.colorize("&cYou don't have permission to use this command!"));
            return true;
        }

        Player target;
        if (args.length > 0) {
            if (!sender.hasPermission("tron.admin")) {
                sender.sendMessage(ColorUtils.colorize("&cYou can only view your own statistics!"));
                return true;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ColorUtils.colorize("&cPlayer not found: " + args[0]));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtils.colorize("&cConsole must specify a player name!"));
                return true;
            }
            target = (Player) sender;
        }

        // Use PlayerManager for stats instead of economy system
        String stats = playerManager.getPlayerStats(target);
        sender.sendMessage(stats);

        return true;
    }

    /**
     * Handle force game command
     */
    private boolean handleForceGameCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tron.admin")) {
            sender.sendMessage(ColorUtils.colorize("&cYou don't have permission to use this command!"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ColorUtils.colorize("&cUsage: /forcegame <player>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ColorUtils.colorize("&cPlayer not found: " + args[0]));
            return true;
        }

        if (gameData.isActivePlayer(target.getUniqueId())) {
            sender.sendMessage(ColorUtils.colorize("&c" + target.getName() + " is already in the game!"));
            return true;
        }

        if (playerManager.addToQueue(target)) {
            sender.sendMessage(ColorUtils.colorize("&aAdded " + target.getName() + " to the game queue!"));
            target.sendMessage(ColorUtils.colorize("&aYou have been added to the Tron game queue by an admin!"));
        } else {
            sender.sendMessage(ColorUtils.colorize("&cFailed to add " + target.getName() + " to the queue!"));
        }

        return true;
    }

    /**
     * Handle end game command
     */
    private boolean handleEndGameCommand(CommandSender sender) {
        if (!sender.hasPermission("tron.admin")) {
            sender.sendMessage(ColorUtils.colorize("&cYou don't have permission to use this command!"));
            return true;
        }

        if (!gameData.isActive() && !gameData.isStarting()) {
            sender.sendMessage(ColorUtils.colorize("&cNo active game to end!"));
            return true;
        }

        // Force end the game
        gameManager.endGame("admin forced");
        sender.sendMessage(ColorUtils.colorize("&aGame has been force ended!"));

        return true;
    }

    /**
     * Handle test glass command to verify glass pane connections
     */
    private boolean handleTestGlassCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize("&cOnly players can test glass pane connections!"));
            return true;
        }

        if (!sender.hasPermission("tron.admin")) {
            sender.sendMessage(ColorUtils.colorize("&cYou don't have permission to test glass panes!"));
            return true;
        }

        Player player = (Player) sender;
        Location loc = player.getLocation().add(0, 1, 0); // Place above player

        // Test glass pane connections by placing a 3x3 grid
        org.bukkit.Material glassPaneMaterial = org.bukkit.Material.BLUE_STAINED_GLASS_PANE;

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Location testLoc = loc.clone().add(x, 0, z);

                org.bukkit.block.Block block = testLoc.getBlock();
                if (block.getType() == org.bukkit.Material.AIR) {
                    // Place with connections
                    block.setType(glassPaneMaterial, false);

                    if (block.getBlockData() instanceof org.bukkit.block.data.type.GlassPane) {
                        org.bukkit.block.data.type.GlassPane paneData = (org.bukkit.block.data.type.GlassPane) block.getBlockData();

                        // Check for adjacent glass panes and set connections
                        boolean north = isGlassPaneAt(testLoc.clone().add(0, 0, -1));
                        boolean south = isGlassPaneAt(testLoc.clone().add(0, 0, 1));
                        boolean east = isGlassPaneAt(testLoc.clone().add(1, 0, 0));
                        boolean west = isGlassPaneAt(testLoc.clone().add(-1, 0, 0));

                        // Set the connections
                        paneData.setFace(org.bukkit.block.BlockFace.NORTH, north);
                        paneData.setFace(org.bukkit.block.BlockFace.SOUTH, south);
                        paneData.setFace(org.bukkit.block.BlockFace.EAST, east);
                        paneData.setFace(org.bukkit.block.BlockFace.WEST, west);

                        // Apply the updated block data
                        block.setBlockData(paneData, false);
                    }
                }
            }
        }

        // Update all panes to connect properly
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Location testLoc = loc.clone().add(x, 0, z);
                updateAdjacentGlassPanes(testLoc);
            }
        }

        sender.sendMessage(ColorUtils.colorize("&aPlaced 3x3 glass pane test grid above you!"));
        sender.sendMessage(ColorUtils.colorize("&eCheck if the glass panes are properly connected."));

        return true;
    }

    /**
     * Check if there's a glass pane at the specified location (for test command)
     */
    private boolean isGlassPaneAt(Location location) {
        if (location.getWorld() == null) return false;

        org.bukkit.block.Block block = location.getBlock();
        org.bukkit.Material blockType = block.getType();

        return blockType.name().contains("GLASS_PANE");
    }

    /**
     * Update adjacent glass panes (for test command)
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

            org.bukkit.block.Block adjBlock = adjLocation.getBlock();
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
     * Handle test trail command to manually test trail generation
     */
    private boolean handleTestTrailCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.colorize("&cOnly players can test trail generation!"));
            return true;
        }

        if (!sender.hasPermission("tron.admin")) {
            sender.sendMessage(ColorUtils.colorize("&cYou don't have permission to test trails!"));
            return true;
        }

        Player player = (Player) sender;
        Location loc = player.getLocation();

        // Test trail generation directly
        if (plugin instanceof Paris.Tron) {
            Paris.Tron tronPlugin = (Paris.Tron) plugin;

            // Create a test trail at player's location
            sender.sendMessage(ColorUtils.colorize("&eGenerating test trail at your location..."));

            // Use the trail manager directly
            tronPlugin.getTrailManager().generateTrail(loc, player.getUniqueId());

            sender.sendMessage(ColorUtils.colorize("&aTest trail generation completed!"));
            sender.sendMessage(ColorUtils.colorize("&eCheck console for debug messages."));
        } else {
            sender.sendMessage(ColorUtils.colorize("&cTrail manager not available!"));
        }

        return true;
    }

    /**
     * Handle force leave command (admin only)
     */
    private boolean handleForceLeaveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tron.admin")) {
            sender.sendMessage(ColorUtils.colorize("&cYou don't have permission to use this command!"));
            return true;
        }

        Player targetPlayer = null;

        if (args.length == 0) {
            // Force leave self if sender is a player
            if (sender instanceof Player) {
                targetPlayer = (Player) sender;
            } else {
                sender.sendMessage(ColorUtils.colorize("&cYou must specify a player when using this command from console!"));
                sender.sendMessage(ColorUtils.colorize("&eUsage: /forceleave <player>"));
                return true;
            }
        } else {
            // Force leave specified player
            targetPlayer = Bukkit.getPlayer(args[0]);
            if (targetPlayer == null) {
                sender.sendMessage(ColorUtils.colorize("&cPlayer '" + args[0] + "' not found!"));
                return true;
            }
        }

        // Perform comprehensive cleanup
        forceLeaveAllEffects(targetPlayer);

        sender.sendMessage(ColorUtils.colorize("&aForced " + targetPlayer.getName() + " to leave all Tron plugin effects!"));
        targetPlayer.sendMessage(ColorUtils.colorize("&6[Tron] &cAn admin has forced you to leave all plugin effects!"));

        return true;
    }

    /**
     * Force a player to leave all plugin effects
     */
    private void forceLeaveAllEffects(Player player) {
        UUID playerId = player.getUniqueId();

        plugin.getLogger().info("Force leaving all plugin effects for " + player.getName());

        // Remove from all game states
        if (gameData.isActivePlayer(playerId)) {
            gameManager.eliminatePlayer(playerId, "force leave");
        }
        if (gameData.isInQueue(playerId)) {
            playerManager.removeFromQueue(player);
        }

        // Comprehensive cleanup
        playerManager.cleanupPlayer(player);

        // Clean up pig ride listener tracking
        if (plugin.getPigRideListener() != null) {
            plugin.getPigRideListener().cleanupPlayer(playerId);
        }

        // Force reset player state
        player.setGameMode(org.bukkit.GameMode.SURVIVAL); // Reset to survival
        player.getInventory().clear();
        player.getActivePotionEffects().forEach(effect ->
            player.removePotionEffect(effect.getType()));

        // Reset health and food
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20);

        // Remove from any vehicles
        if (player.getVehicle() != null) {
            player.getVehicle().removePassenger(player);
        }

        // Teleport to spawn if in arena world
        if (plugin.getArenaManager() != null &&
            plugin.getArenaManager().getArenaWorld() != null &&
            player.getWorld().equals(plugin.getArenaManager().getArenaWorld())) {

            // Teleport to main world spawn
            World mainWorld = Bukkit.getWorlds().get(0);
            player.teleport(mainWorld.getSpawnLocation());
        }

        // Clear any boss bars
        player.sendMessage(ColorUtils.colorize("&aAll Tron plugin effects have been cleared!"));
    }

    /**
     * Get a message from config
     */
    private String getConfigMessage(String key) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&6[Tron] &r");
        String message = plugin.getConfig().getString("messages." + key, "&cMessage not found: " + key);
        return prefix + message;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase();

        if (commandName.equals("tron") || commandName.equals("tr")) {
            if (args.length == 1) {
                List<String> subCommands = Arrays.asList("join", "leave", "info", "top", "leaderboard");
                if (sender.hasPermission("tron.admin")) {
                    subCommands = new ArrayList<>(subCommands);
                    subCommands.add("reload");
                    subCommands.add("testglass");
                    subCommands.add("testtrail");
                    subCommands.add("forceleave");
                }
                return subCommands.stream()
                    .filter(sub -> sub.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("top") || args[0].equalsIgnoreCase("leaderboard"))) {
                return Arrays.asList("5", "10", "15", "20");
            }
        } else if (commandName.equals("stats") && args.length == 1 && sender.hasPermission("tron.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        } else if (commandName.equals("forcegame") && args.length == 1 && sender.hasPermission("tron.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        } else if (commandName.equals("forceleave") && args.length == 1 && sender.hasPermission("tron.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
