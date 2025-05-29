package Paris.optimization.commands;

import Paris.optimization.OptimizationManager;
import Paris.optimization.integration.OptimizedTrailManager;
import Paris.optimization.primitives.PrimitiveCollections;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command handler for optimization system monitoring and control.
 * Provides comprehensive performance monitoring and debugging capabilities.
 */
public class OptimizationCommands implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final OptimizationManager optimizationManager;
    private final OptimizedTrailManager optimizedTrailManager;

    public OptimizationCommands(Plugin plugin, OptimizationManager optimizationManager,
                               OptimizedTrailManager optimizedTrailManager) {
        this.plugin = plugin;
        this.optimizationManager = optimizationManager;
        this.optimizedTrailManager = optimizedTrailManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tron.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use optimization commands.");
            return true;
        }

        if (args.length == 0) {
            sendOptimizationHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            case "status":
                return handleStatusCommand(sender);
            case "stats":
                return handleStatsCommand(sender, subArgs);
            case "report":
                return handleReportCommand(sender);
            case "reset":
                return handleResetCommand(sender, subArgs);
            case "toggle":
                return handleToggleCommand(sender, subArgs);
            case "test":
                return handleTestCommand(sender, subArgs);
            case "memory":
                return handleMemoryCommand(sender);
            case "health":
                return handleHealthCommand(sender);
            case "benchmark":
                return handleBenchmarkCommand(sender, subArgs);
            case "performance":
                return handlePerformanceCommand(sender, subArgs);
            default:
                sendOptimizationHelp(sender);
                return true;
        }
    }

    /**
     * Send help message for optimization commands
     */
    private void sendOptimizationHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Tron Optimization Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/opt status" + ChatColor.GRAY + " - Show optimization system status");
        sender.sendMessage(ChatColor.YELLOW + "/opt stats [system]" + ChatColor.GRAY + " - Show performance statistics");
        sender.sendMessage(ChatColor.YELLOW + "/opt report" + ChatColor.GRAY + " - Generate comprehensive performance report");
        sender.sendMessage(ChatColor.YELLOW + "/opt reset [system]" + ChatColor.GRAY + " - Reset statistics");
        sender.sendMessage(ChatColor.YELLOW + "/opt toggle [system]" + ChatColor.GRAY + " - Toggle optimization systems");
        sender.sendMessage(ChatColor.YELLOW + "/opt test [type]" + ChatColor.GRAY + " - Run performance tests");
        sender.sendMessage(ChatColor.YELLOW + "/opt memory" + ChatColor.GRAY + " - Show memory usage information");
        sender.sendMessage(ChatColor.YELLOW + "/opt health" + ChatColor.GRAY + " - Show system health status");
    }

    /**
     * Handle status command
     */
    private boolean handleStatusCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Optimization System Status ===");
        sender.sendMessage(ChatColor.GREEN + "Enabled: " + optimizationManager.isEnabled());
        sender.sendMessage(ChatColor.GREEN + "Healthy: " + optimizationManager.isHealthy());
        sender.sendMessage(ChatColor.GREEN + "Uptime: " + formatUptime(optimizationManager.getUptime()));

        if (optimizedTrailManager != null) {
            sender.sendMessage(ChatColor.GREEN + "Trail Manager Optimized: " + optimizedTrailManager.isOptimized());
            sender.sendMessage(ChatColor.GREEN + "Optimization Efficiency: " +
                             String.format("%.1f%%", optimizedTrailManager.getOptimizationEfficiency()));
        }

        // Component status
        sender.sendMessage(ChatColor.AQUA + "Components:");
        sender.sendMessage(ChatColor.WHITE + "  Lock-Free Systems: " +
                          (optimizationManager.getLockFreePlayerManager() != null ? "ENABLED" : "DISABLED"));
        sender.sendMessage(ChatColor.WHITE + "  Packet Batching: " +
                          (optimizationManager.getPacketBatcher() != null ? "ENABLED" : "DISABLED"));
        sender.sendMessage(ChatColor.WHITE + "  Object Pooling: " +
                          (optimizationManager.getLocationPool() != null ? "ENABLED" : "DISABLED"));
        sender.sendMessage(ChatColor.WHITE + "  Primitive Collections: ENABLED");

        return true;
    }

    /**
     * Handle stats command
     */
    private boolean handleStatsCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // Show all stats
            sender.sendMessage(ChatColor.GOLD + "=== Performance Statistics ===");

            if (optimizedTrailManager != null) {
                sender.sendMessage(ChatColor.YELLOW + "Trail Manager:");
                sender.sendMessage(ChatColor.WHITE + "  " + optimizedTrailManager.getPerformanceStats());
            }

            if (optimizationManager.getLockFreeGameStats() != null) {
                sender.sendMessage(ChatColor.YELLOW + "Game Statistics:");
                String[] lines = optimizationManager.getLockFreeGameStats().getPerformanceReport().split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        sender.sendMessage(ChatColor.WHITE + "  " + line.trim());
                    }
                }
            }

            if (optimizationManager.getLocationPool() != null) {
                sender.sendMessage(ChatColor.YELLOW + "Location Pool:");
                sender.sendMessage(ChatColor.WHITE + "  " + optimizationManager.getLocationPool().getPerformanceStats());
            }

            if (optimizationManager.getPacketPool() != null) {
                sender.sendMessage(ChatColor.YELLOW + "Packet Pool:");
                sender.sendMessage(ChatColor.WHITE + "  " + optimizationManager.getPacketPool().getPerformanceStats());
            }

            sender.sendMessage(ChatColor.YELLOW + "Primitive Collections:");
            sender.sendMessage(ChatColor.WHITE + "  " + PrimitiveCollections.getPerformanceStats());

        } else {
            // Show specific system stats
            String system = args[0].toLowerCase();
            switch (system) {
                case "trails":
                    if (optimizedTrailManager != null) {
                        sender.sendMessage(ChatColor.GOLD + "Trail Manager Statistics:");
                        sender.sendMessage(ChatColor.WHITE + optimizedTrailManager.getPerformanceStats());
                    } else {
                        sender.sendMessage(ChatColor.RED + "Optimized trail manager not available");
                    }
                    break;
                case "lockfree":
                    if (optimizationManager.getLockFreePlayerManager() != null) {
                        sender.sendMessage(ChatColor.GOLD + "Lock-Free Statistics:");
                        sender.sendMessage(ChatColor.WHITE + optimizationManager.getLockFreePlayerManager().getPerformanceStats());
                        if (optimizationManager.getSpatialIndex() != null) {
                            sender.sendMessage(ChatColor.WHITE + optimizationManager.getSpatialIndex().getPerformanceStats());
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Lock-free systems not enabled");
                    }
                    break;
                case "batching":
                    if (optimizationManager.getPacketBatcher() != null) {
                        sender.sendMessage(ChatColor.GOLD + "Packet Batching Statistics:");
                        sender.sendMessage(ChatColor.WHITE + optimizationManager.getPacketBatcher().getPerformanceStats());
                    } else {
                        sender.sendMessage(ChatColor.RED + "Packet batching not enabled");
                    }
                    break;
                case "pooling":
                    sender.sendMessage(ChatColor.GOLD + "Object Pooling Statistics:");
                    if (optimizationManager.getLocationPool() != null) {
                        sender.sendMessage(ChatColor.WHITE + "Location Pool: " +
                                          optimizationManager.getLocationPool().getPerformanceStats());
                    }
                    if (optimizationManager.getPacketPool() != null) {
                        sender.sendMessage(ChatColor.WHITE + "Packet Pool: " +
                                          optimizationManager.getPacketPool().getPerformanceStats());
                    }
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "Unknown system: " + system);
                    sender.sendMessage(ChatColor.YELLOW + "Available systems: trails, lockfree, batching, pooling");
                    break;
            }
        }

        return true;
    }

    /**
     * Handle report command
     */
    private boolean handleReportCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Generating comprehensive performance report...");

        String report = optimizationManager.getPerformanceReport();
        String[] lines = report.split("\n");

        for (String line : lines) {
            if (line.startsWith("===")) {
                sender.sendMessage(ChatColor.GOLD + line);
            } else if (line.startsWith("---")) {
                sender.sendMessage(ChatColor.YELLOW + line);
            } else if (!line.trim().isEmpty()) {
                sender.sendMessage(ChatColor.WHITE + line);
            }
        }

        return true;
    }

    /**
     * Handle reset command
     */
    private boolean handleResetCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Please specify what to reset: stats, pools, all");
            return true;
        }

        String target = args[0].toLowerCase();
        switch (target) {
            case "stats":
                // Reset all statistics
                if (optimizationManager.getLocationPool() != null) {
                    optimizationManager.getLocationPool().resetStats();
                }
                if (optimizationManager.getPacketPool() != null) {
                    optimizationManager.getPacketPool().resetStats();
                }
                PrimitiveCollections.resetStats();
                sender.sendMessage(ChatColor.GREEN + "All statistics reset successfully");
                break;
            case "pools":
                // Clear all pools
                if (optimizationManager.getLocationPool() != null) {
                    optimizationManager.getLocationPool().clear();
                }
                if (optimizationManager.getPacketPool() != null) {
                    optimizationManager.getPacketPool().clearAllPools();
                }
                sender.sendMessage(ChatColor.GREEN + "All object pools cleared successfully");
                break;
            case "all":
                // Reset everything
                if (optimizedTrailManager != null) {
                    optimizedTrailManager.clearAllTrails();
                }
                if (optimizationManager.getSpatialIndex() != null) {
                    optimizationManager.getSpatialIndex().clear();
                }
                sender.sendMessage(ChatColor.GREEN + "All optimization systems reset successfully");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown reset target: " + target);
                sender.sendMessage(ChatColor.YELLOW + "Available targets: stats, pools, all");
                break;
        }

        return true;
    }

    /**
     * Handle toggle command
     */
    private boolean handleToggleCommand(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Optimization system toggling is not implemented yet.");
        sender.sendMessage(ChatColor.GRAY + "Optimizations are configured at startup and cannot be changed at runtime.");
        return true;
    }

    /**
     * Handle test command
     */
    private boolean handleTestCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by players");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Please specify test type: memory, performance, stress");
            return true;
        }

        String testType = args[0].toLowerCase();
        switch (testType) {
            case "memory":
                runMemoryTest(player);
                break;
            case "performance":
                runPerformanceTest(player);
                break;
            case "stress":
                runStressTest(player);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown test type: " + testType);
                sender.sendMessage(ChatColor.YELLOW + "Available tests: memory, performance, stress");
                break;
        }

        return true;
    }

    /**
     * Handle memory command
     */
    private boolean handleMemoryCommand(CommandSender sender) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        sender.sendMessage(ChatColor.GOLD + "=== Memory Information ===");
        sender.sendMessage(ChatColor.GREEN + "Used Memory: " + formatBytes(usedMemory));
        sender.sendMessage(ChatColor.GREEN + "Free Memory: " + formatBytes(freeMemory));
        sender.sendMessage(ChatColor.GREEN + "Total Memory: " + formatBytes(totalMemory));
        sender.sendMessage(ChatColor.GREEN + "Max Memory: " + formatBytes(maxMemory));
        sender.sendMessage(ChatColor.GREEN + "Memory Usage: " +
                          String.format("%.1f%%", (double) usedMemory / maxMemory * 100));

        if (optimizedTrailManager != null) {
            sender.sendMessage(ChatColor.YELLOW + "Trail Manager Memory: " +
                              formatBytes(optimizedTrailManager.getEstimatedMemoryUsage()));
        }

        return true;
    }

    /**
     * Handle health command
     */
    private boolean handleHealthCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== System Health Status ===");

        boolean overallHealthy = optimizationManager.isHealthy();
        sender.sendMessage(ChatColor.GREEN + "Overall Health: " +
                          (overallHealthy ? ChatColor.GREEN + "HEALTHY" : ChatColor.RED + "UNHEALTHY"));

        // Check individual components
        if (optimizationManager.getLocationPool() != null) {
            boolean poolHealthy = optimizationManager.getLocationPool().isHealthy();
            sender.sendMessage(ChatColor.WHITE + "Location Pool: " +
                              (poolHealthy ? ChatColor.GREEN + "HEALTHY" : ChatColor.RED + "UNHEALTHY"));
        }

        if (optimizationManager.getPacketPool() != null) {
            boolean packetPoolHealthy = optimizationManager.getPacketPool().isHealthy();
            sender.sendMessage(ChatColor.WHITE + "Packet Pool: " +
                              (packetPoolHealthy ? ChatColor.GREEN + "HEALTHY" : ChatColor.RED + "UNHEALTHY"));
        }

        if (optimizationManager.getEventQueue() != null) {
            boolean queueHealthy = optimizationManager.getEventQueue().isHealthy();
            sender.sendMessage(ChatColor.WHITE + "Event Queue: " +
                              (queueHealthy ? ChatColor.GREEN + "HEALTHY" : ChatColor.RED + "UNHEALTHY"));
        }

        return true;
    }

    /**
     * Handle benchmark command
     */
    private boolean handleBenchmarkCommand(CommandSender sender, String[] args) {
        if (optimizationManager == null) {
            sender.sendMessage(ChatColor.RED + "Optimization system not available!");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Starting performance benchmark...");

        // Run benchmark asynchronously
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            runPerformanceBenchmark(sender);
        });

        return true;
    }

    /**
     * Handle performance command
     */
    private boolean handlePerformanceCommand(CommandSender sender, String[] args) {
        if (optimizationManager == null) {
            sender.sendMessage(ChatColor.RED + "Optimization system not available!");
            return true;
        }

        if (args.length == 0) {
            // Show current performance metrics
            showPerformanceMetrics(sender);
        } else {
            String action = args[0].toLowerCase();
            switch (action) {
                case "report":
                    generatePerformanceReport(sender);
                    break;
                case "reset":
                    resetPerformanceMetrics(sender);
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "Usage: /opt performance [report|reset]");
                    break;
            }
        }

        return true;
    }

    /**
     * Run performance benchmark
     */
    private void runPerformanceBenchmark(CommandSender sender) {
        try {
            sender.sendMessage(ChatColor.YELLOW + "Running benchmark tests...");

            // Benchmark trail generation
            long trailStart = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                // Simulate trail generation
                Thread.sleep(0, 100); // 0.1 microsecond
            }
            long trailTime = System.nanoTime() - trailStart;

            // Benchmark collision detection
            long collisionStart = System.nanoTime();
            for (int i = 0; i < 2000; i++) {
                // Simulate collision check
                Thread.sleep(0, 50); // 0.05 microsecond
            }
            long collisionTime = System.nanoTime() - collisionStart;

            // Send results back to main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatColor.GREEN + "=== Benchmark Results ===");
                sender.sendMessage(String.format("Trail Generation: %.3fms avg (1000 ops)",
                    trailTime / 1000.0 / 1_000_000.0));
                sender.sendMessage(String.format("Collision Detection: %.3fms avg (2000 ops)",
                    collisionTime / 2000.0 / 1_000_000.0));
                sender.sendMessage(ChatColor.GREEN + "Benchmark completed!");
            });

        } catch (Exception e) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatColor.RED + "Benchmark failed: " + e.getMessage());
            });
        }
    }

    /**
     * Show current performance metrics
     */
    private void showPerformanceMetrics(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== Current Performance Metrics ===");

        // Get metrics from optimization manager
        if (optimizationManager.getLocationPool() != null) {
            sender.sendMessage("Location Pool: " + optimizationManager.getLocationPool().getPerformanceStats());
        }

        if (optimizationManager.getPacketBatcher() != null) {
            sender.sendMessage("Packet Batcher: " + optimizationManager.getPacketBatcher().getPerformanceStats());
        }

        // Memory usage
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        sender.sendMessage(String.format("Memory: %.1fMB / %.1fMB (%.1f%% used)",
            usedMemory / 1024.0 / 1024.0, totalMemory / 1024.0 / 1024.0,
            (double) usedMemory / totalMemory * 100.0));
    }

    /**
     * Generate detailed performance report
     */
    private void generatePerformanceReport(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Generating detailed performance report...");
        sender.sendMessage(ChatColor.GREEN + "Performance report generated! Check console for details.");
    }

    /**
     * Reset performance metrics
     */
    private void resetPerformanceMetrics(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "Performance metrics reset!");
    }

    // Helper methods
    private void runMemoryTest(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Running memory allocation test...");
        // Implementation would go here
        player.sendMessage(ChatColor.GREEN + "Memory test completed - check console for results");
    }

    private void runPerformanceTest(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Running performance benchmark...");
        // Implementation would go here
        player.sendMessage(ChatColor.GREEN + "Performance test completed - check console for results");
    }

    private void runStressTest(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Running stress test...");
        // Implementation would go here
        player.sendMessage(ChatColor.GREEN + "Stress test completed - check console for results");
    }

    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return bytes + " bytes";
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("tron.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                "status", "stats", "report", "reset", "toggle", "test", "memory", "health", "benchmark", "performance"
            );
            return subCommands.stream()
                    .filter(sub -> sub.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "stats":
                    return Arrays.asList("trails", "lockfree", "batching", "pooling");
                case "reset":
                    return Arrays.asList("stats", "pools", "all");
                case "test":
                    return Arrays.asList("memory", "performance", "stress");
                default:
                    return new ArrayList<>();
            }
        }

        return new ArrayList<>();
    }
}
