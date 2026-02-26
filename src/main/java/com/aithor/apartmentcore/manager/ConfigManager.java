package com.aithor.apartmentcore.manager;

import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.model.LevelConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
// Note: the above imports are still needed for shop.yml loading
/**
 * Manages all configuration values and settings
 */
public class ConfigManager {
    private final ApartmentCore plugin;

    // Configuration values
    private boolean debugMode;
    private String currencySymbol;
    private boolean autoSaveEnabled;
    private int autoSaveInterval;
    private double sellPercentage;
    private int inactiveGracePeriod;
    private long commandCooldown;
    private boolean backupEnabled;
    private int maxBackups;
    private Map<Integer, LevelConfig> levelConfigs;

    // GuestBook settings
    private int guestBookMaxMessages;
    private int guestBookMaxMessageLength;
    private int guestBookLeaveCooldown;

    // Logging settings
    private boolean logTransactions;
    private boolean logAdminActions;
    private String logFile;
    private int maxLogSize;
    private boolean keepOldLogs;
    private int maxOldLogs;

    // Auction settings
    private boolean auctionEnabled;
    private double auctionMinStartingBid;
    private double auctionMaxStartingBid;
    private int auctionMinDuration;
    private int auctionMaxDuration;
    private double auctionMinBidIncrement;
    private double auctionCreationFee;
    // Fraction value, e.g. 0.05 for 5%
    private double auctionCommission;
    // Cooldown in seconds
    private int auctionCooldown;
    private boolean auctionBroadcast;
    // Minutes
    private int auctionExtendThreshold;
    private int auctionExtendTime;

    // GUI settings
    private boolean guiEnabled;
    private int guiRefreshInterval;
    private boolean guiSounds;

    // Performance settings
    private boolean performanceUseAsync;

    // WorldGuard settings
    private boolean wgAutoAddOwner;
    private boolean wgAutoRemoveOwner;
    private boolean wgCheckFlags;
    private java.util.List<String> wgRequiredFlags;

    // Feature toggles
    private boolean featureIncomeGeneration;
    private boolean featureTaxSystem;
    private boolean featureTeleportation;

    // Income & Tax settings
    private int taxGenerationInterval;

    // Income settings
    private int incomeGenerationInterval;


    public ConfigManager(ApartmentCore plugin) {
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
        currencySymbol = config.getString("economy.currency-symbol", "$");
        autoSaveEnabled = config.getBoolean("auto-save.enabled", true);
        autoSaveInterval = config.getInt("auto-save.interval-minutes", 10);
        sellPercentage = config.getDouble("economy.sell-percentage", 70) / 100.0;
        inactiveGracePeriod = config.getInt("settings.inactive-grace-period", 3);
        commandCooldown = config.getLong("security.command-cooldown", 1000);
        backupEnabled = config.getBoolean("backup.enabled", true);
        maxBackups = config.getInt("backup.max-backups", 10);

        // Load GuestBook settings
        guestBookMaxMessages = config.getInt("guestbook.max-messages", 50);
        guestBookMaxMessageLength = config.getInt("guestbook.max-message-length", 100);
        guestBookLeaveCooldown = config.getInt("guestbook.leave-cooldown", 60);

        // Load Logging settings
        logTransactions = config.getBoolean("logging.log-transactions", true);
        logAdminActions = config.getBoolean("logging.log-admin-actions", true);
        logFile = config.getString("logging.log-file", "logs/apartmentcore.log");
        maxLogSize = config.getInt("logging.max-log-size", 10);
        keepOldLogs = config.getBoolean("logging.keep-old-logs", true);
        maxOldLogs = config.getInt("logging.max-old-logs", 10);

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

        // Load auction settings (gracefully handle when missing)
        auctionEnabled = config.getBoolean("auction.enabled", false);
        auctionMinStartingBid = config.getDouble("auction.min-starting-bid", 1000D);
        auctionMaxStartingBid = config.getDouble("auction.max-starting-bid", 1_000_000D);
        auctionMinDuration = config.getInt("auction.min-duration-hours", 1);
        auctionMaxDuration = config.getInt("auction.max-duration-hours", 168);
        auctionMinBidIncrement = config.getDouble("auction.min-bid-increment", 100D);
        auctionCreationFee = config.getDouble("auction.creation-fee", 0D);
        auctionCommission = config.getDouble("auction.commission-percentage", 5D) / 100.0;
        auctionCooldown = config.getInt("auction.cooldown-seconds", 3600);
        auctionBroadcast = config.getBoolean("auction.broadcast-enabled", true);
        auctionExtendThreshold = config.getInt("auction.extend-threshold-minutes", 5);
        auctionExtendTime = config.getInt("auction.extend-time-minutes", 10);

        // Load GUI settings
        guiEnabled = config.getBoolean("gui.enabled", true);
        guiRefreshInterval = config.getInt("gui.refresh-interval", 30);
        guiSounds = config.getBoolean("gui.sounds", true);

        // Load Performance settings
        performanceUseAsync = config.getBoolean("performance.use-async", true);

        // Load WorldGuard settings
        wgAutoAddOwner = config.getBoolean("worldguard.auto-add-owner", true);
        wgAutoRemoveOwner = config.getBoolean("worldguard.auto-remove-owner", true);
        wgCheckFlags = config.getBoolean("worldguard.check-flags", false);
        wgRequiredFlags = config.getStringList("worldguard.required-flags");


        // Load Feature toggles
        featureIncomeGeneration = config.getBoolean("features.income-generation", true);
        featureTaxSystem = config.getBoolean("features.tax-system", true);
        featureTeleportation = config.getBoolean("features.teleportation", true);

        // Load income & tax settings
        taxGenerationInterval = config.getInt("settings.tax-generation-interval", 24000);

        // Load income settings
        incomeGenerationInterval = config.getInt("settings.income-generation-interval", 24000);

        plugin.debug("Configuration loaded successfully");

        // Load external Shop configuration (shop.yml)
        try {
            loadShopConfig();
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to load external Shop config: " + t.getMessage());
        }
 
        // Notify GUI manager (if present) so GUIs immediately reflect configuration changes
        try {
            if (plugin.getGUIManager() != null) {
                plugin.getGUIManager().onConfigReloaded();
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to notify GUI manager about config reload: " + t.getMessage());
        }
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
    public String getCurrencySymbol() { return currencySymbol; }
    public boolean isAutoSaveEnabled() { return autoSaveEnabled; }
    public int getAutoSaveInterval() { return autoSaveInterval; }
    public double getSellPercentage() { return sellPercentage; }
    public int getInactiveGracePeriod() { return inactiveGracePeriod; }
    public long getCommandCooldown() { return commandCooldown; }
    public boolean isBackupEnabled() { return backupEnabled; }
    public int getMaxBackups() { return maxBackups; }
    public Map<Integer, LevelConfig> getLevelConfigs() { return levelConfigs; }

    // GuestBook getters
    public int getGuestBookMaxMessages() { return guestBookMaxMessages; }
    public int getGuestBookMaxMessageLength() { return guestBookMaxMessageLength; }
    public int getGuestBookLeaveCooldown() { return guestBookLeaveCooldown; }

    // Logging getters
    public boolean isLogTransactions() { return logTransactions; }
    public boolean isLogAdminActions() { return logAdminActions; }
    public String getLogFile() { return logFile; }
    public int getMaxLogSize() { return maxLogSize; }
    public boolean isKeepOldLogs() { return keepOldLogs; }
    public int getMaxOldLogs() { return maxOldLogs; }

    // Auction getters
    public boolean isAuctionEnabled() { return auctionEnabled; }
    public double getAuctionMinStartingBid() { return auctionMinStartingBid; }
    public double getAuctionMaxStartingBid() { return auctionMaxStartingBid; }
    public int getAuctionMinDuration() { return auctionMinDuration; }
    public int getAuctionMaxDuration() { return auctionMaxDuration; }
    public double getAuctionMinBidIncrement() { return auctionMinBidIncrement; }
    public double getAuctionCreationFee() { return auctionCreationFee; }
    public double getAuctionCommission() { return auctionCommission; }
    public int getAuctionCooldown() { return auctionCooldown; }
    public boolean isAuctionBroadcast() { return auctionBroadcast; }
    public int getAuctionExtendThreshold() { return auctionExtendThreshold; }
    public int getAuctionExtendTime() { return auctionExtendTime; }

    // GUI getters
    public boolean isGuiEnabled() { return guiEnabled; }
    public int getGuiRefreshInterval() { return guiRefreshInterval; }
    public boolean isGuiSounds() { return guiSounds; }

    // Performance getters
    public boolean isPerformanceUseAsync() { return performanceUseAsync; }

    // WorldGuard getters
    public boolean isWgAutoAddOwner() { return wgAutoAddOwner; }
    public boolean isWgAutoRemoveOwner() { return wgAutoRemoveOwner; }
    public boolean isWgCheckFlags() { return wgCheckFlags; }
    public java.util.List<String> getWgRequiredFlags() { return wgRequiredFlags; }

    // Feature getters
    public boolean isFeatureIncomeGeneration() { return featureIncomeGeneration; }
    public boolean isFeatureTaxSystem() { return featureTaxSystem; }
    public boolean isFeatureTeleportation() { return featureTeleportation; }

    // Income & Tax getters
    public int getTaxGenerationInterval() { return taxGenerationInterval; }

    // Income getters
    public int getIncomeGenerationInterval() { return incomeGenerationInterval; }
 
    // External Shop configuration file (shop.yml)
    private File shopConfigFile;
    private FileConfiguration shopConfig;
 
    /**
     * Load or reload the external Shop configuration file (shop.yml).
     * If the file doesn't exist in plugin data folder, attempt to copy bundled resource.
     */
    public void loadShopConfig() {
        try {
            if (!plugin.getDataFolder().exists()) {
                if (!plugin.getDataFolder().mkdirs()) {
                    plugin.getLogger().warning("Could not create plugin data folder to store shop.yml");
                }
            }

            this.shopConfigFile = new File(plugin.getDataFolder(), "shop.yml");
            if (!this.shopConfigFile.exists()) {
                try (InputStream in = plugin.getResource("shop.yml")) {
                    if (in != null) {
                        Files.copy(in, this.shopConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        plugin.getLogger().info("shop.yml created from default resource.");
                    } else {
                        this.shopConfigFile.createNewFile();
                        plugin.getLogger().warning("shop.yml resource not found in jar! Created empty file.");
                    }
                } catch (IOException ioe) {
                    plugin.getLogger().warning("Failed to create/copy shop.yml: " + ioe.getMessage());
                }
            }

            this.shopConfig = YamlConfiguration.loadConfiguration(this.shopConfigFile);
            plugin.debug("Shop configuration loaded from " + this.shopConfigFile.getName());
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to load Shop config: " + t.getMessage());
        }
    }

    /**
     * Get the raw Shop FileConfiguration. Loads it lazily if needed.
     */
    public FileConfiguration getShopConfig() {
        if (this.shopConfig == null) {
            loadShopConfig();
        }
        return this.shopConfig;
    }

    /**
     * Get the shop.yml File instance.
     */
    public File getShopConfigFile() {
        return this.shopConfigFile;
    }

}
