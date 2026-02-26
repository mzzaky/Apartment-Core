package com.aithor.apartmentcorei3.gui.utils;

import com.aithor.apartmentcorei3.gui.items.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for GUI-related functions
 */
public class GUIUtils {
    
    // Common GUI materials
    public static final Material BORDER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    public static final Material SUCCESS_MATERIAL = Material.LIME_STAINED_GLASS_PANE;
    public static final Material WARNING_MATERIAL = Material.YELLOW_STAINED_GLASS_PANE;
    public static final Material ERROR_MATERIAL = Material.RED_STAINED_GLASS_PANE;
    public static final Material INFO_MATERIAL = Material.BLUE_STAINED_GLASS_PANE;
    
    // Common sounds
    public static final Sound CLICK_SOUND = Sound.UI_BUTTON_CLICK;
    public static final Sound SUCCESS_SOUND = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    public static final Sound ERROR_SOUND = Sound.ENTITY_VILLAGER_NO;
    public static final Sound PAGE_TURN_SOUND = Sound.ITEM_BOOK_PAGE_TURN;
    
    /**
     * Create a simple ItemStack with name and lore
     * @param material The material
     * @param name The display name
     * @param lore The lore lines
     * @return The created ItemStack
     */
    public static ItemStack createItem(Material material, String name, String... lore) {
        return new ItemBuilder(material)
                .name(name)
                .lore(lore)
                .build();
    }
    
    /**
     * Create a clickable item with glow effect
     * @param material The material
     * @param name The display name
     * @param lore The lore lines
     * @return The created ItemStack
     */
    public static ItemStack createClickableItem(Material material, String name, String... lore) {
        return new ItemBuilder(material)
                .name(name)
                .lore(lore)
                .glow()
                .build();
    }
    
    /**
     * Create a status indicator item
     * @param status The status (ACTIVE, WARNING, ERROR, etc.)
     * @param name The display name
     * @param lore The lore lines
     * @return The created ItemStack
     */
    public static ItemStack createStatusItem(StatusType status, String name, String... lore) {
        Material material;
        switch (status) {
            case SUCCESS:
                material = SUCCESS_MATERIAL;
                break;
            case WARNING:
                material = WARNING_MATERIAL;
                break;
            case ERROR:
                material = ERROR_MATERIAL;
                break;
            case INFO:
                material = INFO_MATERIAL;
                break;
            default:
                material = BORDER_MATERIAL;
        }
        
        return createItem(material, name, lore);
    }
    
    /**
     * Create a player head item
     * @param playerName The player name
     * @param displayName The display name
     * @param lore The lore lines
     * @return The created ItemStack
     */
    public static ItemStack createPlayerHead(String playerName, String displayName, String... lore) {
        return new ItemBuilder(Material.PLAYER_HEAD)
                .name(displayName)
                .lore(lore)
                .skull(playerName)
                .build();
    }
    
    /**
     * Create a number display item
     * @param number The number to display
     * @param name The display name
     * @param lore The lore lines
     * @return The created ItemStack
     */
    public static ItemStack createNumberItem(int number, String name, String... lore) {
        Material material = getNumberMaterial(number);
        return createItem(material, name, lore);
    }
    
    /**
     * Get material representing a number
     * @param number The number (0-9)
     * @return The corresponding material
     */
    private static Material getNumberMaterial(int number) {
        switch (number % 10) {
            case 1: return Material.REDSTONE;
            case 2: return Material.GOLD_INGOT;
            case 3: return Material.IRON_INGOT;
            case 4: return Material.DIAMOND;
            case 5: return Material.EMERALD;
            case 6: return Material.LAPIS_LAZULI;
            case 7: return Material.COAL;
            case 8: return Material.COPPER_INGOT;
            case 9: return Material.NETHERITE_INGOT;
            default: return Material.STONE;
        }
    }
    
    /**
     * Format money amount for display
     * @param amount The amount
     * @return Formatted string
     */
    public static String formatMoney(double amount) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.US);
        return formatter.format(amount);
    }
    
    /**
     * Format large numbers with suffixes (K, M, B)
     * @param number The number
     * @return Formatted string
     */
    public static String formatNumber(long number) {
        if (number < 1000) {
            return String.valueOf(number);
        } else if (number < 1000000) {
            return String.format("%.1fK", number / 1000.0);
        } else if (number < 1000000000) {
            return String.format("%.1fM", number / 1000000.0);
        } else {
            return String.format("%.1fB", number / 1000000000.0);
        }
    }
    
    /**
     * Format time duration
     * @param millis Time in milliseconds
     * @return Formatted time string
     */
    public static String formatTime(long millis) {
        if (millis < 0) return "0s";
        
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");
        
        return sb.toString().trim();
    }
    
    /**
     * Create a progress bar string
     * @param current Current value
     * @param max Maximum value
     * @param length Length of the progress bar
     * @return Progress bar string
     */
    public static String createProgressBar(double current, double max, int length) {
        if (max <= 0) return ChatColor.GRAY + "■".repeat(length);
        
        double percentage = Math.min(1.0, current / max);
        int filledBars = (int) (percentage * length);
        int emptyBars = length - filledBars;
        
        ChatColor color = getProgressColor(percentage);
        
        return color + "■".repeat(filledBars) + ChatColor.GRAY + "■".repeat(emptyBars);
    }
    
    /**
     * Get color based on progress percentage
     * @param percentage The percentage (0.0 - 1.0)
     * @return The appropriate color
     */
    private static ChatColor getProgressColor(double percentage) {
        if (percentage >= 0.8) return ChatColor.GREEN;
        if (percentage >= 0.6) return ChatColor.YELLOW;
        if (percentage >= 0.4) return ChatColor.GOLD;
        if (percentage >= 0.2) return ChatColor.RED;
        return ChatColor.DARK_RED;
    }
    
    /**
     * Play a sound to a player
     * @param player The player
     * @param sound The sound to play
     */
    public static void playSound(Player player, Sound sound) {
        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
    }
    
    /**
     * Send a formatted message to a player
     * @param player The player
     * @param message The message
     */
    public static void sendMessage(Player player, String message) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
    
    /**
     * Wrap text to fit in lore lines
     * @param text The text to wrap
     * @param maxWidth Maximum characters per line
     * @return List of wrapped lines
     */
    public static List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }
            }
            
            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }
        
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        
        return lines;
    }
    
    /**
     * Status types for GUI elements
     */
    public enum StatusType {
        SUCCESS, WARNING, ERROR, INFO, NEUTRAL
    }
}