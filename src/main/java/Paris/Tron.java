package Paris;

import Paris.commands.TronCommands;
import Paris.data.GameData;
import Paris.listeners.GameListeners;
import Paris.listeners.PigRideListener;
import Paris.managers.*;
import Paris.optimization.OptimizationManager;
import Paris.optimization.integration.OptimizedTrailManager;
import Paris.optimization.commands.OptimizationCommands;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for the Tron Light Cycle minigame
 */
public final class Tron extends JavaPlugin {

    // Core components
    private GameData gameData;
    private PlayerManager playerManager;
    private TrailManager trailManager;
    private ArenaManager arenaManager;
    private GameManager gameManager;
    private PigRideListener pigRideListener;

    // Optimization system
    private OptimizationManager optimizationManager;
    private OptimizedTrailManager optimizedTrailManager;

    // Commands and listeners
    private TronCommands commandHandler;
    private OptimizationCommands optimizationCommands;
    private GameListeners gameListeners;

    @Override
    public void onLoad() {
        // PacketEvents no longer needed - using direct pig movement system
        getLogger().info("Tron plugin loading - using direct pig movement system");
    }

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize core components
        initializeComponents();

        // Initialize optimization system
        initializeOptimizations();

        // Register commands
        registerCommands();

        // Register listeners
        registerListeners();

        // Log successful startup
        getLogger().info("Tron minigame plugin has been enabled!");
        getLogger().info("Version: " + getDescription().getVersion());
        getLogger().info("Author: " + String.join(", ", getDescription().getAuthors()));
    }

    @Override
    public void onDisable() {
        // Stop optimization system
        if (optimizationManager != null) {
            optimizationManager.stop();
        }

        // Shutdown game manager
        if (gameManager != null) {
            gameManager.shutdown();
        }

        // Stop pig ride listener
        if (pigRideListener != null) {
            pigRideListener.shutdown();
        }

        // Stop spectator enforcement
        if (gameListeners != null) {
            gameListeners.stopSpectatorEnforcement();
        }

        // Cleanup managers
        if (playerManager != null) {
            playerManager.cleanup();
        }

        if (trailManager != null) {
            trailManager.cleanup();
        }

        if (arenaManager != null) {
            arenaManager.cleanup();
        }

        getLogger().info("Tron minigame plugin has been disabled!");
    }

    /**
     * Initialize all core components
     */
    private void initializeComponents() {
        getLogger().info("Initializing Tron components...");

        // Initialize data
        this.gameData = new GameData();

        // Initialize managers in dependency order
        this.playerManager = new PlayerManager(this, gameData);
        this.arenaManager = new ArenaManager(this, gameData);
        this.trailManager = new TrailManager(this, gameData, playerManager);
        this.gameManager = new GameManager(this, gameData, playerManager, trailManager, arenaManager);

        // Initialize pig ride listener for direct pig movement control
        this.pigRideListener = new PigRideListener(this);
        getLogger().info("PigRideListener initialized - direct pig movement control enabled!");

        getLogger().info("All components initialized successfully!");
    }

    /**
     * Initialize optimization system
     */
    private void initializeOptimizations() {
        try {
            // Check if ProtocolLib is available
            if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
                getLogger().warning("ProtocolLib not found - optimization system will use fallback mode");
                return;
            }

            // Get ProtocolManager safely
            ProtocolManager protocolManager = null;
            try {
                protocolManager = ProtocolLibrary.getProtocolManager();
                if (protocolManager == null) {
                    getLogger().warning("ProtocolManager is null - optimization system will use fallback mode");
                    return;
                }
            } catch (Exception e) {
                getLogger().warning("Failed to get ProtocolManager: " + e.getMessage());
                return;
            }

            // Initialize optimization systems with default configuration
            OptimizationManager.OptimizationConfig config = OptimizationManager.OptimizationConfig.createDefault();
            this.optimizationManager = new OptimizationManager(this, protocolManager, config);

            // Start optimization systems
            if (optimizationManager.start()) {
                getLogger().info("Optimization systems started successfully!");

                // Initialize optimized trail manager
                this.optimizedTrailManager = new OptimizedTrailManager(this, optimizationManager);
                getLogger().info("OptimizedTrailManager initialized - performance improvements active!");
            } else {
                getLogger().warning("Failed to start optimization systems - using standard mode");
            }

        } catch (Exception e) {
            getLogger().warning("Failed to initialize optimization system: " + e.getMessage());
            getLogger().info("Continuing with standard performance mode");
        }
    }

    /**
     * Register command handlers
     */
    private void registerCommands() {
        this.commandHandler = new TronCommands(this, gameManager, playerManager, gameData);

        // Register main commands
        getCommand("tron").setExecutor(commandHandler);
        getCommand("tron").setTabCompleter(commandHandler);
        getCommand("start").setExecutor(commandHandler);
        getCommand("afk").setExecutor(commandHandler);
        getCommand("stats").setExecutor(commandHandler);
        getCommand("forcegame").setExecutor(commandHandler);
        getCommand("endgame").setExecutor(commandHandler);

        // Register optimization commands if available
        if (optimizationManager != null) {
            this.optimizationCommands = new OptimizationCommands(this, optimizationManager, optimizedTrailManager);
            getCommand("opt").setExecutor(optimizationCommands);
            getCommand("opt").setTabCompleter(optimizationCommands);
            getLogger().info("Optimization commands registered successfully!");
        }

        getLogger().info("Commands registered successfully!");
    }

    /**
     * Register event listeners
     */
    private void registerListeners() {
        this.gameListeners = new GameListeners(this, gameData, gameManager, playerManager, arenaManager);

        Bukkit.getPluginManager().registerEvents(gameListeners, this);
        Bukkit.getPluginManager().registerEvents(pigRideListener, this);

        getLogger().info("Event listeners registered successfully!");
    }

    // Getters for accessing components from other classes
    public GameData getGameData() { return gameData; }
    public PlayerManager getPlayerManager() { return playerManager; }
    public TrailManager getTrailManager() { return trailManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public GameManager getGameManager() { return gameManager; }
    public PigRideListener getPigRideListener() { return pigRideListener; }
    public OptimizationManager getOptimizationManager() { return optimizationManager; }
    public OptimizedTrailManager getOptimizedTrailManager() { return optimizedTrailManager; }
}
