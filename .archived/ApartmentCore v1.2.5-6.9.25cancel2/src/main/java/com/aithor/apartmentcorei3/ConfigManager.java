package com.aithor.apartmentcorei3;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages all configuration values and settings
 */
public class ConfigManager {
    private final ApartmentCorei3 plugin;

    // Configuration values
    private boolean debugMode;
    private boolean useMySQL;
    private String currencySymbol;
    private boolean autoSaveEnabled;
    private int autoSaveInterval;
    private double sellPercentage;
    private double penaltyPercentage;
    private int inactiveGracePeriod;
    private long commandCooldown;
    private boolean backupEnabled;
    private int maxBackups;
    private Map<Integer, LevelConfig> levelConfigs;

    // New GuestBook settings
    private int guestBookMaxMessages;
    private int guestBookMaxMessageLength;
    private int guestBookLeaveCooldown;


    public ConfigManager(ApartmentCorei3 plugin) {
        this.plugin = plugin;
        this.levelConfigs = new HashMap<>();
    }

    /**
     * Load configuration values from config.yml
     */
    public void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();
        plugin.saveDefaultConfig(); // Ensure new values are added
        plugin.reloadConfig(); // Reload to get them
        config = plugin.getConfig(); // Re-assign

        debugMode = config.getBoolean("debug", false);
        useMySQL = config.getBoolean("database.use-mysql", false);
        currencySymbol = config.getString("economy.currency-symbol", "$");
        autoSaveEnabled = config.getBoolean("auto-save.enabled", true);
        autoSaveInterval = config.getInt("auto-save.interval-minutes", 10);
        sellPercentage = config.getDouble("economy.sell-percentage", 70) / 100.0;
        penaltyPercentage = config.getDouble("economy.penalty-percentage", 25) / 100.0;
        inactiveGracePeriod = config.getInt("time.inactive-grace-period", 3);
        commandCooldown = config.getLong("security.command-cooldown", 1000);
        backupEnabled = config.getBoolean("backup.enabled", true);
        maxBackups = config.getInt("backup.max-backups", 10);

        // Load GuestBook settings
        guestBookMaxMessages = config.getInt("guestbook.max-messages", 50);
        guestBookMaxMessageLength = config.getInt("guestbook.max-message-length", 100);
        guestBookLeaveCooldown = config.getInt("guestbook.leave-cooldown", 60);

        // Load level configurations
        levelConfigs.clear();
        ConfigurationSection levelsSection = config.getConfigurationSection("apartment-levels");
        if (levelsSection != null) {
            for (String key : levelsSection.getKeys(false)) {
                int level = Integer.parseInt(key.replace("level-", ""));
                ConfigurationSection levelSection = levelsSection.getConfigurationSection(key);
                if (levelSection != null) {
                    levelConfigs.put(level, new LevelConfig(
                            levelSection.getDouble("min-income"),
                            levelSection.getDouble("max-income"),
                            levelSection.getDouble("upgrade-cost")
                    ));
                }
            }
        }

        plugin.debug("Configuration loaded successfully");
    }

    /**
     * Format money display
     */
    public String formatMoney(double amount) {
        return currencySymbol + String.format("%.2f", amount);
    }

    /**
     * Get level configuration for a specific level
     */
    public LevelConfig getLevelConfig(int level) {
        return levelConfigs.get(level);
    }

    // Getters
    public boolean isDebugMode() { return debugMode; }
    public boolean isUseMySQL() { return useMySQL; }
    public String getCurrencySymbol() { return currencySymbol; }
    public boolean isAutoSaveEnabled() { return autoSaveEnabled; }
    public int getAutoSaveInterval() { return autoSaveInterval; }
    public double getSellPercentage() { return sellPercentage; }
    public double getPenaltyPercentage() { return penaltyPercentage; }
    public int getInactiveGracePeriod() { return inactiveGracePeriod; }
    public long getCommandCooldown() { return commandCooldown; }
    public boolean isBackupEnabled() { return backupEnabled; }
    public int getMaxBackups() { return maxBackups; }
    public Map<Integer, LevelConfig> getLevelConfigs() { return levelConfigs; }

    // GuestBook getters
    public int getGuestBookMaxMessages() { return guestBookMaxMessages; }
    public int getGuestBookMaxMessageLength() { return guestBookMaxMessageLength; }
    public int getGuestBookLeaveCooldown() { return guestBookLeaveCooldown; }
}