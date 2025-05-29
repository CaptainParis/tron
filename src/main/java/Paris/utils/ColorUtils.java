package Paris.utils;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * Utility class for handling colors in the Tron minigame
 */
public class ColorUtils {

    // Available trail materials with their corresponding colors (30 different colors)
    public static final List<Material> TRAIL_MATERIALS = Arrays.asList(
        Material.RED_STAINED_GLASS,
        Material.BLUE_STAINED_GLASS,
        Material.GREEN_STAINED_GLASS,
        Material.YELLOW_STAINED_GLASS,
        Material.PURPLE_STAINED_GLASS,
        Material.ORANGE_STAINED_GLASS,
        Material.PINK_STAINED_GLASS,
        Material.CYAN_STAINED_GLASS,
        Material.LIME_STAINED_GLASS,
        Material.MAGENTA_STAINED_GLASS,
        Material.LIGHT_BLUE_STAINED_GLASS,
        Material.BROWN_STAINED_GLASS,
        Material.GRAY_STAINED_GLASS,
        Material.LIGHT_GRAY_STAINED_GLASS,
        Material.BLACK_STAINED_GLASS,
        Material.WHITE_STAINED_GLASS,
        // Additional colors using glass panes for better visuals
        Material.RED_STAINED_GLASS_PANE,
        Material.BLUE_STAINED_GLASS_PANE,
        Material.GREEN_STAINED_GLASS_PANE,
        Material.YELLOW_STAINED_GLASS_PANE,
        Material.PURPLE_STAINED_GLASS_PANE,
        Material.ORANGE_STAINED_GLASS_PANE,
        Material.PINK_STAINED_GLASS_PANE,
        Material.CYAN_STAINED_GLASS_PANE,
        Material.LIME_STAINED_GLASS_PANE,
        Material.MAGENTA_STAINED_GLASS_PANE,
        Material.LIGHT_BLUE_STAINED_GLASS_PANE,
        Material.BROWN_STAINED_GLASS_PANE,
        Material.GRAY_STAINED_GLASS_PANE,
        Material.LIGHT_GRAY_STAINED_GLASS_PANE
    );

    // Corresponding chat colors for each material (30 colors)
    public static final List<ChatColor> CHAT_COLORS = Arrays.asList(
        // Glass colors
        ChatColor.RED,
        ChatColor.BLUE,
        ChatColor.GREEN,
        ChatColor.YELLOW,
        ChatColor.DARK_PURPLE,
        ChatColor.GOLD,
        ChatColor.LIGHT_PURPLE,
        ChatColor.DARK_AQUA,
        ChatColor.GREEN,
        ChatColor.LIGHT_PURPLE,
        ChatColor.AQUA,
        ChatColor.DARK_RED,
        ChatColor.DARK_GRAY,
        ChatColor.GRAY,
        ChatColor.BLACK,
        ChatColor.WHITE,
        // Glass pane colors (using different shades)
        ChatColor.DARK_RED,
        ChatColor.DARK_BLUE,
        ChatColor.DARK_GREEN,
        ChatColor.GOLD,
        ChatColor.DARK_PURPLE,
        ChatColor.RED,
        ChatColor.LIGHT_PURPLE,
        ChatColor.AQUA,
        ChatColor.GREEN,
        ChatColor.LIGHT_PURPLE,
        ChatColor.BLUE,
        ChatColor.DARK_RED,
        ChatColor.DARK_GRAY,
        ChatColor.GRAY
    );

    // Corresponding leather armor colors (30 colors with RGB values)
    public static final List<Color> LEATHER_COLORS = Arrays.asList(
        // Glass colors
        Color.RED,
        Color.BLUE,
        Color.GREEN,
        Color.YELLOW,
        Color.PURPLE,
        Color.ORANGE,
        Color.FUCHSIA,
        Color.AQUA,
        Color.LIME,
        Color.PURPLE,
        Color.BLUE,
        Color.MAROON,
        Color.GRAY,
        Color.SILVER,
        Color.BLACK,
        Color.WHITE,
        // Additional custom colors for glass panes
        Color.fromRGB(139, 0, 0),    // Dark Red
        Color.fromRGB(0, 0, 139),    // Dark Blue
        Color.fromRGB(0, 100, 0),    // Dark Green
        Color.fromRGB(255, 215, 0),  // Gold
        Color.fromRGB(75, 0, 130),   // Indigo
        Color.fromRGB(255, 69, 0),   // Red Orange
        Color.fromRGB(255, 20, 147), // Deep Pink
        Color.fromRGB(0, 255, 255),  // Cyan
        Color.fromRGB(50, 205, 50),  // Lime Green
        Color.fromRGB(186, 85, 211), // Medium Orchid
        Color.fromRGB(30, 144, 255), // Dodger Blue
        Color.fromRGB(165, 42, 42),  // Brown
        Color.fromRGB(105, 105, 105), // Dim Gray
        Color.fromRGB(192, 192, 192)  // Silver
    );

    /**
     * Get a trail material by index
     */
    public static Material getTrailMaterial(int index) {
        if (index < 0 || index >= TRAIL_MATERIALS.size()) {
            return TRAIL_MATERIALS.get(0); // Default to red
        }
        return TRAIL_MATERIALS.get(index);
    }

    /**
     * Get a chat color by index
     */
    public static ChatColor getChatColor(int index) {
        if (index < 0 || index >= CHAT_COLORS.size()) {
            return CHAT_COLORS.get(0); // Default to red
        }
        return CHAT_COLORS.get(index);
    }

    /**
     * Get a leather armor color by index
     */
    public static Color getLeatherColor(int index) {
        if (index < 0 || index >= LEATHER_COLORS.size()) {
            return LEATHER_COLORS.get(0); // Default to red
        }
        return LEATHER_COLORS.get(index);
    }

    /**
     * Get the chat color for a specific trail material
     */
    public static ChatColor getChatColorForMaterial(Material material) {
        int index = TRAIL_MATERIALS.indexOf(material);
        return getChatColor(index);
    }

    /**
     * Get the leather color for a specific trail material
     */
    public static Color getLeatherColorForMaterial(Material material) {
        int index = TRAIL_MATERIALS.indexOf(material);
        return getLeatherColor(index);
    }

    /**
     * Create colored leather armor piece
     */
    public static ItemStack createColoredLeatherArmor(Material armorType, Color color) {
        if (!isLeatherArmor(armorType)) {
            throw new IllegalArgumentException("Material must be leather armor");
        }

        ItemStack armor = new ItemStack(armorType);
        LeatherArmorMeta meta = (LeatherArmorMeta) armor.getItemMeta();
        if (meta != null) {
            meta.setColor(color);
            armor.setItemMeta(meta);
        }
        return armor;
    }

    /**
     * Check if a material is leather armor
     */
    public static boolean isLeatherArmor(Material material) {
        return material == Material.LEATHER_HELMET ||
               material == Material.LEATHER_CHESTPLATE ||
               material == Material.LEATHER_LEGGINGS ||
               material == Material.LEATHER_BOOTS;
    }

    /**
     * Get DyeColor from Material
     */
    public static DyeColor getDyeColorFromMaterial(Material material) {
        switch (material) {
            // Glass materials
            case RED_STAINED_GLASS: return DyeColor.RED;
            case BLUE_STAINED_GLASS: return DyeColor.BLUE;
            case GREEN_STAINED_GLASS: return DyeColor.GREEN;
            case YELLOW_STAINED_GLASS: return DyeColor.YELLOW;
            case PURPLE_STAINED_GLASS: return DyeColor.PURPLE;
            case ORANGE_STAINED_GLASS: return DyeColor.ORANGE;
            case PINK_STAINED_GLASS: return DyeColor.PINK;
            case CYAN_STAINED_GLASS: return DyeColor.CYAN;
            case LIME_STAINED_GLASS: return DyeColor.LIME;
            case MAGENTA_STAINED_GLASS: return DyeColor.MAGENTA;
            case LIGHT_BLUE_STAINED_GLASS: return DyeColor.LIGHT_BLUE;
            case BROWN_STAINED_GLASS: return DyeColor.BROWN;
            case GRAY_STAINED_GLASS: return DyeColor.GRAY;
            case LIGHT_GRAY_STAINED_GLASS: return DyeColor.LIGHT_GRAY;
            case BLACK_STAINED_GLASS: return DyeColor.BLACK;
            case WHITE_STAINED_GLASS: return DyeColor.WHITE;
            // Glass pane materials (map to same colors)
            case RED_STAINED_GLASS_PANE: return DyeColor.RED;
            case BLUE_STAINED_GLASS_PANE: return DyeColor.BLUE;
            case GREEN_STAINED_GLASS_PANE: return DyeColor.GREEN;
            case YELLOW_STAINED_GLASS_PANE: return DyeColor.YELLOW;
            case PURPLE_STAINED_GLASS_PANE: return DyeColor.PURPLE;
            case ORANGE_STAINED_GLASS_PANE: return DyeColor.ORANGE;
            case PINK_STAINED_GLASS_PANE: return DyeColor.PINK;
            case CYAN_STAINED_GLASS_PANE: return DyeColor.CYAN;
            case LIME_STAINED_GLASS_PANE: return DyeColor.LIME;
            case MAGENTA_STAINED_GLASS_PANE: return DyeColor.MAGENTA;
            case LIGHT_BLUE_STAINED_GLASS_PANE: return DyeColor.LIGHT_BLUE;
            case BROWN_STAINED_GLASS_PANE: return DyeColor.BROWN;
            case GRAY_STAINED_GLASS_PANE: return DyeColor.GRAY;
            case LIGHT_GRAY_STAINED_GLASS_PANE: return DyeColor.LIGHT_GRAY;
            default: return DyeColor.RED;
        }
    }

    /**
     * Format a message with color codes
     */
    public static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Get a player's display name with their assigned color
     */
    public static String getColoredPlayerName(String playerName, Material trailMaterial) {
        ChatColor color = getChatColorForMaterial(trailMaterial);
        return color + playerName + ChatColor.RESET;
    }

    /**
     * Get the maximum number of available colors
     */
    public static int getMaxColors() {
        return TRAIL_MATERIALS.size();
    }

    /**
     * Get a randomized list of trail materials for up to 30 players
     */
    public static List<Material> getRandomizedTrailMaterials() {
        List<Material> shuffled = new ArrayList<>(TRAIL_MATERIALS);
        Collections.shuffle(shuffled, new Random());
        return shuffled;
    }

    /**
     * Get a randomized list of trail materials with a specific seed for consistent randomization
     */
    public static List<Material> getRandomizedTrailMaterials(long seed) {
        List<Material> shuffled = new ArrayList<>(TRAIL_MATERIALS);
        Collections.shuffle(shuffled, new Random(seed));
        return shuffled;
    }

    /**
     * Get a trail material for a player index with randomization
     */
    public static Material getRandomizedTrailMaterial(int playerIndex, long gameSeed) {
        List<Material> randomized = getRandomizedTrailMaterials(gameSeed);
        return randomized.get(playerIndex % randomized.size());
    }
}
