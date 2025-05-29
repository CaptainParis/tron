package Paris.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.*;

/**
 * Represents the current game state and data
 * Optimized with primitive collections for better performance
 */
public class GameData {

    public enum GameState {
        WAITING,    // Waiting for players
        STARTING,   // Countdown phase
        ACTIVE,     // Game in progress
        ENDING      // Game ending/cleanup
    }

    private GameState gameState;
    private final Set<UUID> queuedPlayers;
    private final Set<UUID> activePlayers;
    private final Set<UUID> alivePlayers;

    // Optimized trail storage using bit-packed coordinates
    private final Long2ObjectOpenHashMap<UUID> trailBlocks; // Maps packed coordinates to player UUIDs
    private final Map<Location, UUID> locationTrailBlocks; // Fallback for Location-based access
    private World arenaWorld;
    private Location arenaCenter;
    private int arenaSize;
    private long gameStartTime;
    private long gameEndTime;
    private int countdownTime;
    private UUID winner;

    // Performance tracking
    private final Set<Location> pendingBlockUpdates;
    private int totalTrailBlocks;

    public GameData() {
        this.gameState = GameState.WAITING;
        this.queuedPlayers = new HashSet<>();
        this.activePlayers = new HashSet<>();
        this.alivePlayers = new HashSet<>();
        this.trailBlocks = new Long2ObjectOpenHashMap<>();
        this.locationTrailBlocks = new HashMap<>();
        this.pendingBlockUpdates = new HashSet<>();
        this.countdownTime = 10; // Default countdown
        this.totalTrailBlocks = 0;
    }

    /**
     * Pack coordinates into a long for efficient storage
     */
    private long packCoordinates(int x, int y, int z) {
        return ((long) (x + 30000000) << 32) | ((long) (y + 2048) << 16) | (long) (z + 30000000);
    }

    /**
     * Unpack coordinates from a long
     */
    private int[] unpackCoordinates(long packed) {
        int x = (int) ((packed >> 32) & 0xFFFFFFF) - 30000000;
        int y = (int) ((packed >> 16) & 0xFFF) - 2048;
        int z = (int) (packed & 0xFFFFFFF) - 30000000;
        return new int[]{x, y, z};
    }

    // Game State Management
    public GameState getGameState() { return gameState; }
    public void setGameState(GameState gameState) { this.gameState = gameState; }

    public boolean isWaiting() { return gameState == GameState.WAITING; }
    public boolean isStarting() { return gameState == GameState.STARTING; }
    public boolean isActive() { return gameState == GameState.ACTIVE; }
    public boolean isEnding() { return gameState == GameState.ENDING; }

    // Player Management
    public Set<UUID> getQueuedPlayers() { return new HashSet<>(queuedPlayers); }
    public Set<UUID> getActivePlayers() { return new HashSet<>(activePlayers); }
    public Set<UUID> getAlivePlayers() { return new HashSet<>(alivePlayers); }

    public void addToQueue(UUID playerId) { queuedPlayers.add(playerId); }
    public void removeFromQueue(UUID playerId) { queuedPlayers.remove(playerId); }
    public boolean isInQueue(UUID playerId) { return queuedPlayers.contains(playerId); }

    public void addActivePlayer(UUID playerId) {
        activePlayers.add(playerId);
        alivePlayers.add(playerId);
    }
    public void removeActivePlayer(UUID playerId) {
        activePlayers.remove(playerId);
        alivePlayers.remove(playerId);
    }
    public boolean isActivePlayer(UUID playerId) { return activePlayers.contains(playerId); }

    public void eliminatePlayer(UUID playerId) { alivePlayers.remove(playerId); }
    public boolean isPlayerAlive(UUID playerId) { return alivePlayers.contains(playerId); }

    public int getQueueSize() { return queuedPlayers.size(); }
    public int getActivePlayerCount() { return activePlayers.size(); }
    public int getAlivePlayerCount() { return alivePlayers.size(); }

    // Trail Management - Optimized with primitive collections
    public Map<Location, UUID> getTrailBlocks() { return new HashMap<>(locationTrailBlocks); }

    public void addTrailBlock(Location location, UUID playerId) {
        // Store in both optimized and fallback maps
        long packed = packCoordinates(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        trailBlocks.put(packed, playerId);
        locationTrailBlocks.put(location, playerId);
        totalTrailBlocks++;
    }

    public UUID getTrailOwner(Location location) {
        // Try optimized lookup first
        long packed = packCoordinates(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        UUID owner = trailBlocks.get(packed);
        return owner != null ? owner : locationTrailBlocks.get(location);
    }

    public boolean hasTrailAt(Location location) {
        // Try optimized lookup first
        long packed = packCoordinates(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return trailBlocks.containsKey(packed) || locationTrailBlocks.containsKey(location);
    }

    public void clearTrails() {
        trailBlocks.clear();
        locationTrailBlocks.clear();
        totalTrailBlocks = 0;
    }

    // Arena Management
    public World getArenaWorld() { return arenaWorld; }
    public void setArenaWorld(World arenaWorld) { this.arenaWorld = arenaWorld; }

    public Location getArenaCenter() { return arenaCenter; }
    public void setArenaCenter(Location arenaCenter) { this.arenaCenter = arenaCenter; }

    public int getArenaSize() { return arenaSize; }
    public void setArenaSize(int arenaSize) { this.arenaSize = arenaSize; }

    // Timing
    public long getGameStartTime() { return gameStartTime; }
    public void setGameStartTime(long gameStartTime) { this.gameStartTime = gameStartTime; }

    public long getGameEndTime() { return gameEndTime; }
    public void setGameEndTime(long gameEndTime) { this.gameEndTime = gameEndTime; }

    public long getGameDuration() {
        if (gameStartTime == 0) return 0;
        long endTime = gameEndTime == 0 ? System.currentTimeMillis() : gameEndTime;
        return endTime - gameStartTime;
    }

    public int getCountdownTime() { return countdownTime; }
    public void setCountdownTime(int countdownTime) { this.countdownTime = countdownTime; }

    // Winner
    public UUID getWinner() { return winner; }
    public void setWinner(UUID winner) { this.winner = winner; }

    // Performance tracking
    public Set<Location> getPendingBlockUpdates() { return new HashSet<>(pendingBlockUpdates); }
    public void addPendingBlockUpdate(Location location) { pendingBlockUpdates.add(location); }
    public void clearPendingBlockUpdates() { pendingBlockUpdates.clear(); }

    public int getTotalTrailBlocks() { return totalTrailBlocks; }

    /**
     * Reset all game data for a new game
     */
    public void reset() {
        this.gameState = GameState.WAITING;
        this.activePlayers.clear();
        this.alivePlayers.clear();
        this.trailBlocks.clear();
        this.locationTrailBlocks.clear();
        this.pendingBlockUpdates.clear();
        this.gameStartTime = 0;
        this.gameEndTime = 0;
        this.winner = null;
        this.totalTrailBlocks = 0;
        // Keep queued players for next game
    }

    /**
     * Start the game with current queued players
     */
    public void startGame() {
        this.activePlayers.addAll(queuedPlayers);
        this.alivePlayers.addAll(queuedPlayers);
        this.queuedPlayers.clear();
        this.gameState = GameState.STARTING;
        this.gameStartTime = System.currentTimeMillis();
    }

    /**
     * Check if there's a winner (only one player alive)
     */
    public boolean hasWinner() {
        return alivePlayers.size() == 1;
    }

    /**
     * Get the winner if there is one
     */
    public UUID determineWinner() {
        if (hasWinner()) {
            return alivePlayers.iterator().next();
        }
        return null;
    }

    /**
     * Check if the game should end (no players or one player left)
     */
    public boolean shouldEnd() {
        return alivePlayers.size() <= 1;
    }
}
