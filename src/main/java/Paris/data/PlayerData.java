package Paris.data;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pig;
import org.bukkit.Location;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

/**
 * Represents a player's data in the Tron minigame
 */
public class PlayerData {
    private final UUID playerId;
    private final String playerName;
    private Player player;
    private Pig pig;
    private Material trailMaterial;
    private boolean isAlive;
    private boolean isAFK;
    private Location lastLocation;
    private Location previousLocation; // For turn angle calculation and gap prevention
    private Set<Location> trailBlocks;

    // Boost system
    private boolean hasBoost;
    private boolean isBoosting;
    private long boostEndTime;

    // Statistics
    private int gamesPlayed;
    private int gamesWon;
    private int eliminations;

    public PlayerData(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.isAlive = false;
        this.isAFK = false;
        this.trailBlocks = new HashSet<>();
        this.gamesPlayed = 0;
        this.gamesWon = 0;
        this.eliminations = 0;
        this.hasBoost = true; // Start with boost available
        this.isBoosting = false;
        this.boostEndTime = 0;
    }

    // Getters and Setters
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }

    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }

    public Pig getPig() { return pig; }
    public void setPig(Pig pig) { this.pig = pig; }

    public Material getTrailMaterial() { return trailMaterial; }
    public void setTrailMaterial(Material trailMaterial) { this.trailMaterial = trailMaterial; }

    public boolean isAlive() { return isAlive; }
    public void setAlive(boolean alive) { this.isAlive = alive; }

    public boolean isAFK() { return isAFK; }
    public void setAFK(boolean afk) { this.isAFK = afk; }

    public Location getLastLocation() { return lastLocation; }
    public void setLastLocation(Location lastLocation) { this.lastLocation = lastLocation; }

    public Location getPreviousLocation() { return previousLocation; }
    public void setPreviousLocation(Location previousLocation) { this.previousLocation = previousLocation; }

    public Set<Location> getTrailBlocks() { return trailBlocks; }
    public void addTrailBlock(Location location) { this.trailBlocks.add(location); }
    public void clearTrailBlocks() { this.trailBlocks.clear(); }

    // Statistics
    public int getGamesPlayed() { return gamesPlayed; }
    public void setGamesPlayed(int gamesPlayed) { this.gamesPlayed = gamesPlayed; }
    public void incrementGamesPlayed() { this.gamesPlayed++; }

    public int getGamesWon() { return gamesWon; }
    public void setGamesWon(int gamesWon) { this.gamesWon = gamesWon; }
    public void incrementGamesWon() { this.gamesWon++; }

    public int getEliminations() { return eliminations; }
    public void setEliminations(int eliminations) { this.eliminations = eliminations; }
    public void incrementEliminations() { this.eliminations++; }



    // Boost system methods
    public boolean hasBoost() { return hasBoost; }
    public void setHasBoost(boolean hasBoost) { this.hasBoost = hasBoost; }

    public boolean isBoosting() { return isBoosting; }
    public void setBoosting(boolean boosting) { this.isBoosting = boosting; }

    public long getBoostEndTime() { return boostEndTime; }
    public void setBoostEndTime(long boostEndTime) { this.boostEndTime = boostEndTime; }

    public boolean isBoostActive() {
        return isBoosting && System.currentTimeMillis() < boostEndTime;
    }

    public void activateBoost(int durationSeconds) {
        if (hasBoost) {
            this.hasBoost = false; // Use up the boost
            this.isBoosting = true;
            this.boostEndTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        }
    }

    /**
     * Reset player data for a new game
     */
    public void resetForGame() {
        this.isAlive = true;
        this.pig = null;
        this.lastLocation = null;
        this.previousLocation = null; // Reset for gap prevention system
        this.trailBlocks.clear();
        this.hasBoost = true; // Reset boost for new game
        this.isBoosting = false;
        this.boostEndTime = 0;
    }

    /**
     * Calculate win rate as a percentage
     */
    public double getWinRate() {
        if (gamesPlayed == 0) return 0.0;
        return (double) gamesWon / gamesPlayed * 100.0;
    }

    /**
     * Calculate eliminations per game
     */
    public double getEliminationsPerGame() {
        if (gamesPlayed == 0) return 0.0;
        return (double) eliminations / gamesPlayed;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PlayerData that = (PlayerData) obj;
        return playerId.equals(that.playerId);
    }

    @Override
    public int hashCode() {
        return playerId.hashCode();
    }
}
