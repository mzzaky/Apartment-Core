package com.aithor.apartmentcore.edition;

import com.aithor.apartmentcore.ApartmentCore;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Central gate-keeper that enforces edition-specific limits.
 * <p>
 * All hard-coded limits for the FREE edition live here so they are easy to
 * audit and adjust without touching feature code scattered across the plugin.
 */
public class EditionManager {

    // ── Free edition hard limits ────────────────────────────────────────────
    public static final int FREE_MAX_APARTMENTS = 20;
    public static final int FREE_MAX_LEVEL = 5;

    private final ApartmentCore plugin;
    private final Edition edition;

    /**
     * Construct with an explicit edition (used after plugin.yml is parsed).
     */
    public EditionManager(ApartmentCore plugin, Edition edition) {
        this.plugin = plugin;
        this.edition = edition;
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    public Edition getEdition() {
        return edition;
    }

    public boolean isFree() {
        return edition.isFree();
    }

    public boolean isPro() {
        return edition.isPro();
    }

    // ── Limit helpers ───────────────────────────────────────────────────────

    /**
     * Maximum apartments that can be registered on the server.
     * Free = 20, Pro = unlimited (Integer.MAX_VALUE).
     */
    public int getMaxApartments() {
        return isFree() ? FREE_MAX_APARTMENTS : Integer.MAX_VALUE;
    }

    /**
     * Maximum apartment level players can upgrade to.
     * Free = 5, Pro = unlimited (reads all levels from config).
     */
    public int getMaxLevel() {
        if (isPro()) {
            return plugin.getConfigManager().getLevelConfigs().size();
        }
        return Math.min(FREE_MAX_LEVEL, plugin.getConfigManager().getLevelConfigs().size());
    }

    /**
     * Whether the research system configuration is editable (research.yml).
     */
    public boolean isResearchCustomisable() {
        return isPro();
    }

    /**
     * Whether the shop system tiers are customisable (shop.yml editable).
     */
    public boolean isShopCustomisable() {
        return isPro();
    }

    /**
     * Whether achievement configuration is editable (achievements.yml).
     */
    public boolean isAchievementCustomisable() {
        return isPro();
    }

    /**
     * Whether the custom GUI menu is editable (custom_gui/main_menu.yml).
     */
    public boolean isCustomGuiEditable() {
        return isPro();
    }

    /**
     * Whether the logging features are available.
     */
    public boolean isLoggingEnabled() {
        return isPro();
    }

    /**
     * Whether the backup features are available.
     */
    public boolean isBackupEnabled() {
        return isPro();
    }

    /**
     * Send a "Pro only" message to a player when they try to access a gated feature.
     */
    public void sendProOnlyMessage(Player player, String featureName) {
        player.sendMessage(ChatColor.RED + "[ApartmentCore] " + ChatColor.GRAY
                + "The " + ChatColor.GOLD + featureName + ChatColor.GRAY
                + " feature is only available in " + ChatColor.YELLOW + "ApartmentCore Pro"
                + ChatColor.GRAY + ".");
        player.sendMessage(ChatColor.GRAY + "Get Pro at: " + ChatColor.AQUA + "https://pasman.io/apartmentcore");
    }

    /**
     * Send a "limit reached" message for the Free edition.
     */
    public void sendLimitMessage(Player player, String limitDescription) {
        player.sendMessage(ChatColor.RED + "[ApartmentCore] " + ChatColor.GRAY
                + "Free edition limit reached: " + ChatColor.GOLD + limitDescription
                + ChatColor.GRAY + ".");
        player.sendMessage(ChatColor.GRAY + "Upgrade to " + ChatColor.YELLOW + "ApartmentCore Pro"
                + ChatColor.GRAY + " for unlimited access.");
    }
}
