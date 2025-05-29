package Paris.utils;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;

/**
 * Utility class for particle effects in the Tron minigame
 */
public class ParticleUtils {


    /**
     * Spawn elimination particles when a player is eliminated
     */
    public static void spawnEliminationParticles(Location location, Material trailMaterial) {
        if (location.getWorld() == null) return;

        Color color = ColorUtils.getLeatherColorForMaterial(trailMaterial);
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 2.0f);

        // Large explosion of colored particles
        location.getWorld().spawnParticle(
            Particle.DUST,
            location,
            50,
            2.0,
            2.0,
            2.0,
            0.1,
            dustOptions
        );

        // Add explosion effect
        location.getWorld().spawnParticle(
            Particle.EXPLOSION,
            location,
            3,
            1.0,
            1.0,
            1.0,
            0.0
        );

        // Add some firework-like particles
        location.getWorld().spawnParticle(
            Particle.FIREWORK,
            location,
            20,
            2.0,
            2.0,
            2.0,
            0.1
        );
    }

    /**
     * Spawn victory particles for the winner
     */
    public static void spawnVictoryParticles(Location location, Material trailMaterial) {
        if (location.getWorld() == null) return;

        Color color = ColorUtils.getLeatherColorForMaterial(trailMaterial);
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.5f);

        // Continuous victory particles
        for (int i = 0; i < 5; i++) {
            double angle = (2 * Math.PI * i) / 5;
            double x = Math.cos(angle) * 2;
            double z = Math.sin(angle) * 2;

            Location particleLocation = location.clone().add(x, 2, z);

            location.getWorld().spawnParticle(
                Particle.DUST,
                particleLocation,
                10,
                0.5,
                0.5,
                0.5,
                0.0,
                dustOptions
            );
        }

        // Add golden particles for victory
        location.getWorld().spawnParticle(
            Particle.HAPPY_VILLAGER,
            location.clone().add(0, 2, 0),
            15,
            1.5,
            1.5,
            1.5,
            0.0
        );

        // Add totem effect
        location.getWorld().spawnParticle(
            Particle.TOTEM_OF_UNDYING,
            location.clone().add(0, 1, 0),
            10,
            1.0,
            2.0,
            1.0,
            0.1
        );
    }

    /**
     * Spawn pig movement particles
     */
    public static void spawnPigMovementParticles(Location location, Material trailMaterial) {
        if (location.getWorld() == null) return;

        Color color = ColorUtils.getLeatherColorForMaterial(trailMaterial);
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 0.8f);

        // Small trail particles behind the pig
        location.getWorld().spawnParticle(
            Particle.DUST,
            location.clone().add(0, 0.5, 0),
            3,
            0.2,
            0.1,
            0.2,
            0.0,
            dustOptions
        );

        // Speed lines effect
        location.getWorld().spawnParticle(
            Particle.CRIT,
            location.clone().add(0, 0.8, 0),
            2,
            0.3,
            0.1,
            0.3,
            0.0
        );
    }

    /**
     * Spawn countdown particles
     */
    public static void spawnCountdownParticles(Location location, int secondsLeft) {
        if (location.getWorld() == null) return;

        // Different colors based on countdown
        Color color;
        if (secondsLeft <= 3) {
            color = Color.RED;
        } else if (secondsLeft <= 5) {
            color = Color.YELLOW;
        } else {
            color = Color.GREEN;
        }

        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 2.0f);

        // Ring of particles
        for (int i = 0; i < secondsLeft * 4; i++) {
            double angle = (2 * Math.PI * i) / (secondsLeft * 4);
            double radius = 3.0;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location particleLocation = location.clone().add(x, 2, z);

            location.getWorld().spawnParticle(
                Particle.DUST,
                particleLocation,
                1,
                0.0,
                0.0,
                0.0,
                0.0,
                dustOptions
            );
        }
    }

    /**
     * Spawn arena border particles
     */
    public static void spawnBorderParticles(Location center, int size) {
        if (center.getWorld() == null) return;

        World world = center.getWorld();
        int halfSize = size / 2;

        // Spawn particles along the border
        for (int i = 0; i < 20; i++) {
            // North border
            world.spawnParticle(
                Particle.BLOCK,
                center.clone().add(halfSize, 1, (Math.random() - 0.5) * size),
                1
            );

            // South border
            world.spawnParticle(
                Particle.BLOCK,
                center.clone().add(-halfSize, 1, (Math.random() - 0.5) * size),
                1
            );

            // East border
            world.spawnParticle(
                Particle.BLOCK,
                center.clone().add((Math.random() - 0.5) * size, 1, halfSize),
                1
            );

            // West border
            world.spawnParticle(
                Particle.BLOCK,
                center.clone().add((Math.random() - 0.5) * size, 1, -halfSize),
                1
            );
        }
    }

    /**
     * Create a particle line between two points
     */
    public static void createParticleLine(Location start, Location end, Particle particle, int density) {
        if (start.getWorld() == null || end.getWorld() == null) return;
        if (!start.getWorld().equals(end.getWorld())) return;

        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        direction.normalize();

        for (int i = 0; i < density; i++) {
            double progress = (double) i / density;
            Location particleLocation = start.clone().add(direction.clone().multiply(distance * progress));
            start.getWorld().spawnParticle(particle, particleLocation, 1);
        }
    }

    /**
     * Spawn particles for all players in a collection
     */
    public static void spawnParticlesForPlayers(Collection<Player> players, Location location,
                                               Particle particle, int count, double offsetX,
                                               double offsetY, double offsetZ, double extra) {
        for (Player player : players) {
            player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
        }
    }
}
