package com.aithor.apartmentcore.gui.config;

import com.aithor.apartmentcore.ApartmentCore;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and provides access to the main_menu.yml configuration.
 * Every getter falls back to a hardcoded default when the YAML key
 * is missing or contains an invalid value, so the menu always works
 * even without the file.
 */
public class MainMenuConfig {

    private final ApartmentCore plugin;
    private YamlConfiguration config;
    private File configFile;

    // ── Hardcoded defaults ────────────────────────────────────────
    private static final String DEF_TITLE = "&2ApartmentCore Main Menu";
    private static final int DEF_SIZE = 54;
    private static final boolean DEF_BORDER_ENABLED = true;
    private static final String DEF_BORDER_MATERIAL = "GRAY_STAINED_GLASS_PANE";

    // Player info defaults
    private static final boolean DEF_PLAYER_INFO_ENABLED = true;
    private static final int DEF_PLAYER_INFO_SLOT = 4;
    private static final String DEF_PLAYER_INFO_NAME = "&6{player_name}";
    private static final List<String> DEF_PLAYER_INFO_LORE = Arrays.asList(
            "&7ApartmentCore v{plugin_version}",
            "&7Economy: &f{economy_name}",
            "",
            "&7Balance: &a{player_balance}");

    // Default item definitions (key → defaults)
    private static final Map<String, ItemDefaults> DEFAULT_ITEMS = new LinkedHashMap<>();

    static {
        DEFAULT_ITEMS.put("my_apartments", new ItemDefaults(
                11, "DARK_OAK_DOOR", 0,
                "&6\u1F3E0 My Apartments",
                Arrays.asList(
                        "&7Manage your owned apartments", "",
                        "&e📊 Statistics:",
                        "&7• Owned: &f{owned_count}&7/&f{max_apartments}",
                        "&7• Pending Income: &a{pending_income}", "",
                        "&a▶ Click to open"),
                true, null, null, null));

        DEFAULT_ITEMS.put("browse_buy", new ItemDefaults(
                13, "GOLD_INGOT", 0,
                "&6🛒 Browse & Buy",
                Arrays.asList(
                        "&7Browse available apartments", "",
                        "&e📊 Market Info:",
                        "&7• Available: &f{available_count} &7apartments",
                        "&7• Starting from: &a{cheapest_price}", "",
                        "&a▶ Click to browse"),
                true, null, null, null));

        DEFAULT_ITEMS.put("tax_management", new ItemDefaults(
                15, "GREEN_CONCRETE", 0,
                "&6💰 Tax Management",
                Arrays.asList(
                        "&7Manage your tax payments", "",
                        "&e📊 Tax Status:",
                        "&7• Status: {tax_status_color}{tax_status}",
                        "&7• Total Due: &f{total_unpaid}",
                        "&7• Overdue: &f{overdue_count} &7apartments", "",
                        "&a▶ Click to manage"),
                true, null, null, null));

        DEFAULT_ITEMS.put("auction_house", new ItemDefaults(
                20, "SUNFLOWER", 0,
                "&6🔨 Auction House",
                Arrays.asList(
                        "&7Buy and sell apartments via auction", "",
                        "&e📊 Auction Info:",
                        "&7• Active Auctions: &f{active_auctions}",
                        "&7• Commission: &f{auction_commission}", "",
                        "&a▶ Click to open"),
                true,
                "BARRIER", "&c🔨 Auction House",
                Arrays.asList("&7Auction system is disabled", "", "&c✗ Not available")));

        DEFAULT_ITEMS.put("research", new ItemDefaults(
                22, "ENCHANTING_TABLE", 0,
                "&d Research Center",
                Arrays.asList(
                        "&7Conduct research for permanent buffs", "",
                        "&e Research Progress:",
                        "&7 Completed: &f{research_completed}&7/&f{research_max} &7tiers",
                        "{research_status}", "",
                        "&a Click to open"),
                true,
                "BARRIER", "&c Research Center",
                Arrays.asList("&7Research system is disabled", "", "&c Not available")));

        DEFAULT_ITEMS.put("statistics", new ItemDefaults(
                24, "BOOK", 0,
                "&6📊 Statistics",
                Arrays.asList(
                        "&7Your overall performance", "",
                        "&e📋 Overview:",
                        "&7• Owned: &f{owned_count}",
                        "&7• Lifetime Income: &a{lifetime_income}",
                        "&7• Total Tax Paid: &c{total_tax_paid}",
                        "&7• Pending Income: &a{pending_income}",
                        "&7• Outstanding Taxes: &c{outstanding_taxes}", "",
                        "&a▶ Click to open"),
                true, null, null, null));

        DEFAULT_ITEMS.put("achievements", new ItemDefaults(
                30, "NETHER_STAR", 0,
                "&6 Achievements",
                Arrays.asList(
                        "&7Track your milestones and earn rewards", "",
                        "&e Achievement Progress:",
                        "&7 Completed: &f{achievement_completed}&7/&f{achievement_total} &7({achievement_percent})",
                        "&7 {achievement_bar}", "",
                        "&a Click to view"),
                true,
                "BARRIER", "&c Achievements",
                Arrays.asList("&7Achievement system is disabled", "", "&c Not available")));

        DEFAULT_ITEMS.put("help_info", new ItemDefaults(
                32, "ENCHANTED_BOOK", 0,
                "&6❓ Help & Info",
                Arrays.asList(
                        "&7Get help and information", "",
                        "&e📚 Available Help:",
                        "&7• Command reference",
                        "&7• FAQ and guides",
                        "&7• Contact support", "",
                        "&a▶ Click for help"),
                true, null, null, null));
    }

    public MainMenuConfig(ApartmentCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Load or reload the configuration from disk.
     * Creates the default file from the JAR resource if it does not exist.
     */
    public void load() {
        File guiDir = new File(plugin.getDataFolder(), "custom_gui");
        if (!guiDir.exists()) {
            guiDir.mkdirs();
        }

        this.configFile = new File(guiDir, "main_menu.yml");
        if (!configFile.exists()) {
            plugin.saveResource("custom_gui/main_menu.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);
        plugin.debug("Main menu config loaded from " + configFile.getPath());
    }

    // ── Global properties ─────────────────────────────────────────

    public String getTitle() {
        return config != null ? config.getString("gui_title", DEF_TITLE) : DEF_TITLE;
    }

    public int getSize() {
        int size = config != null ? config.getInt("gui_size", DEF_SIZE) : DEF_SIZE;
        // Must be a multiple of 9, between 9 and 54
        if (size < 9 || size > 54 || size % 9 != 0) {
            return DEF_SIZE;
        }
        return size;
    }

    // ── Border ────────────────────────────────────────────────────

    public boolean isBorderEnabled() {
        return config != null ? config.getBoolean("border.enabled", DEF_BORDER_ENABLED) : DEF_BORDER_ENABLED;
    }

    public Material getBorderMaterial() {
        String mat = config != null ? config.getString("border.material", DEF_BORDER_MATERIAL) : DEF_BORDER_MATERIAL;
        return parseMaterial(mat, Material.GRAY_STAINED_GLASS_PANE);
    }

    // ── Player info ───────────────────────────────────────────────

    public boolean isPlayerInfoEnabled() {
        return config != null ? config.getBoolean("player_info.enabled", DEF_PLAYER_INFO_ENABLED) : DEF_PLAYER_INFO_ENABLED;
    }

    public int getPlayerInfoSlot() {
        return config != null ? config.getInt("player_info.slot", DEF_PLAYER_INFO_SLOT) : DEF_PLAYER_INFO_SLOT;
    }

    public String getPlayerInfoName() {
        return config != null ? config.getString("player_info.name", DEF_PLAYER_INFO_NAME) : DEF_PLAYER_INFO_NAME;
    }

    public List<String> getPlayerInfoLore() {
        if (config != null && config.isList("player_info.lore")) {
            return config.getStringList("player_info.lore");
        }
        return DEF_PLAYER_INFO_LORE;
    }

    public Material getPlayerInfoMaterial() {
        if (config != null && config.contains("player_info.material")) {
            return parseMaterial(config.getString("player_info.material"), Material.PLAYER_HEAD);
        }
        return Material.PLAYER_HEAD;
    }

    // ── Menu items ────────────────────────────────────────────────

    public int getItemSlot(String key) {
        ItemDefaults def = DEFAULT_ITEMS.get(key);
        int fallback = def != null ? def.slot : 0;
        if (config != null && config.contains("items." + key + ".slot")) {
            return config.getInt("items." + key + ".slot", fallback);
        }
        return fallback;
    }

    public Material getItemMaterial(String key) {
        ItemDefaults def = DEFAULT_ITEMS.get(key);
        Material fallback = def != null ? parseMaterial(def.material, Material.STONE) : Material.STONE;
        if (config != null && config.contains("items." + key + ".material")) {
            return parseMaterial(config.getString("items." + key + ".material"), fallback);
        }
        return fallback;
    }

    public int getItemCustomModelData(String key) {
        ItemDefaults def = DEFAULT_ITEMS.get(key);
        int fallback = def != null ? def.customModelData : 0;
        if (config != null && config.contains("items." + key + ".custom_model_data")) {
            return config.getInt("items." + key + ".custom_model_data", fallback);
        }
        return fallback;
    }

    public String getItemName(String key) {
        ItemDefaults def = DEFAULT_ITEMS.get(key);
        String fallback = def != null ? def.name : "&7Unknown";
        if (config != null && config.contains("items." + key + ".name")) {
            return config.getString("items." + key + ".name", fallback);
        }
        return fallback;
    }

    public List<String> getItemLore(String key) {
        ItemDefaults def = DEFAULT_ITEMS.get(key);
        List<String> fallback = def != null ? def.lore : Collections.emptyList();
        if (config != null && config.isList("items." + key + ".lore")) {
            return config.getStringList("items." + key + ".lore");
        }
        return fallback;
    }

    public boolean getItemGlow(String key) {
        ItemDefaults def = DEFAULT_ITEMS.get(key);
        boolean fallback = def != null && def.glow;
        if (config != null && config.contains("items." + key + ".glow")) {
            return config.getBoolean("items." + key + ".glow", fallback);
        }
        return fallback;
    }

    // ── Disabled state (for toggleable features) ──────────────────

    public Material getDisabledMaterial(String key) {
        ItemDefaults def = DEFAULT_ITEMS.get(key);
        Material fallback = def != null && def.disabledMaterial != null
                ? parseMaterial(def.disabledMaterial, Material.BARRIER) : Material.BARRIER;
        if (config != null && config.contains("items." + key + ".disabled_material")) {
            return parseMaterial(config.getString("items." + key + ".disabled_material"), fallback);
        }
        return fallback;
    }

    public String getDisabledName(String key) {
        ItemDefaults def = DEFAULT_ITEMS.get(key);
        String fallback = def != null && def.disabledName != null ? def.disabledName : "&cDisabled";
        if (config != null && config.contains("items." + key + ".disabled_name")) {
            return config.getString("items." + key + ".disabled_name", fallback);
        }
        return fallback;
    }

    public List<String> getDisabledLore(String key) {
        ItemDefaults def = DEFAULT_ITEMS.get(key);
        List<String> fallback = def != null && def.disabledLore != null
                ? def.disabledLore : Collections.singletonList("&7Feature is disabled");
        if (config != null && config.isList("items." + key + ".disabled_lore")) {
            return config.getStringList("items." + key + ".disabled_lore");
        }
        return fallback;
    }

    // ── Utilities ─────────────────────────────────────────────────

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[MainMenuConfig] Invalid material '" + name + "', using fallback: " + fallback.name());
            return fallback;
        }
    }

    /**
     * Simple data holder for hardcoded item defaults.
     */
    private static class ItemDefaults {
        final int slot;
        final String material;
        final int customModelData;
        final String name;
        final List<String> lore;
        final boolean glow;
        final String disabledMaterial;
        final String disabledName;
        final List<String> disabledLore;

        ItemDefaults(int slot, String material, int customModelData,
                     String name, List<String> lore, boolean glow,
                     String disabledMaterial, String disabledName, List<String> disabledLore) {
            this.slot = slot;
            this.material = material;
            this.customModelData = customModelData;
            this.name = name;
            this.lore = lore;
            this.glow = glow;
            this.disabledMaterial = disabledMaterial;
            this.disabledName = disabledName;
            this.disabledLore = disabledLore;
        }
    }
}
