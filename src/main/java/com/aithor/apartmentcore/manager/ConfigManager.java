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
/**
 * Manages all configuration values and settings
 */
public class ConfigManager {
    private final ApartmentCore plugin;

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
    private boolean guiAnimations;

    // Performance settings
    private boolean performanceUseAsync;
    private boolean performanceUseCache;
    private int performanceCacheExpiry;

    // WorldGuard settings
    private boolean wgAutoAddOwner;
    private boolean wgAutoRemoveOwner;
    private boolean wgCheckFlags;
    private java.util.List<String> wgRequiredFlags;

    // Messages settings
    private String messagesPrefix;
    private boolean messagesUseColors;
    private String messagesLanguage;

    // Feature toggles
    private boolean featureIncomeGeneration;
    private boolean featureTaxSystem;
    private boolean featureLevelSystem;
    private boolean featureTeleportation;

    // Time tick settings
    private int ticksPerHour;
    private int ticksPerDay;

    // PlaceholderAPI
    private int placeholderUpdateInterval;

    // Security extras
    private boolean securityRequireConfirmation;
    private boolean securityLogSuspicious;


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
        guiAnimations = config.getBoolean("gui.animations", true);

        // Load Performance settings
        performanceUseAsync = config.getBoolean("performance.use-async", true);
        performanceUseCache = config.getBoolean("performance.use-cache", true);
        performanceCacheExpiry = config.getInt("performance.cache-expiry", 300);

        // Load WorldGuard settings
        wgAutoAddOwner = config.getBoolean("worldguard.auto-add-owner", true);
        wgAutoRemoveOwner = config.getBoolean("worldguard.auto-remove-owner", true);
        wgCheckFlags = config.getBoolean("worldguard.check-flags", false);
        wgRequiredFlags = config.getStringList("worldguard.required-flags");

        // Load Messages settings
        messagesPrefix = config.getString("messages.prefix", "&6[ApartmentCore] &r");
        messagesUseColors = config.getBoolean("messages.use-colors", true);
        messagesLanguage = config.getString("messages.language", "en_US");

        // Load Feature toggles
        featureIncomeGeneration = config.getBoolean("features.income-generation", true);
        featureTaxSystem = config.getBoolean("features.tax-system", true);
        featureLevelSystem = config.getBoolean("features.level-system", true);
        featureTeleportation = config.getBoolean("features.teleportation", true);

        // Load time tick settings
        ticksPerHour = config.getInt("time.ticks-per-hour", 1000);
        ticksPerDay = config.getInt("time.ticks-per-day", 24000);

        // Placeholder API settings
        placeholderUpdateInterval = config.getInt("placeholderapi.update-interval", 60);

        // Security extras
        securityRequireConfirmation = config.getBoolean("security.require-confirmation", true);
        securityLogSuspicious = config.getBoolean("security.log-suspicious", true);
 
        plugin.debug("Configuration loaded successfully");
 
        // Load external GUI configuration (apartment_gui.yml) so GUIs can use custom menus/items
        try {
            loadGuiConfig();
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to load external GUI config: " + t.getMessage());
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
    public boolean isGuiAnimations() { return guiAnimations; }

    // Performance getters
    public boolean isPerformanceUseAsync() { return performanceUseAsync; }
    public boolean isPerformanceUseCache() { return performanceUseCache; }
    public int getPerformanceCacheExpiry() { return performanceCacheExpiry; }

    // WorldGuard getters
    public boolean isWgAutoAddOwner() { return wgAutoAddOwner; }
    public boolean isWgAutoRemoveOwner() { return wgAutoRemoveOwner; }
    public boolean isWgCheckFlags() { return wgCheckFlags; }
    public java.util.List<String> getWgRequiredFlags() { return wgRequiredFlags; }

    // Messages getters
    public String getMessagesPrefix() { return messagesPrefix; }
    public boolean isMessagesUseColors() { return messagesUseColors; }
    public String getMessagesLanguage() { return messagesLanguage; }

    // Feature getters
    public boolean isFeatureIncomeGeneration() { return featureIncomeGeneration; }
    public boolean isFeatureTaxSystem() { return featureTaxSystem; }
    public boolean isFeatureLevelSystem() { return featureLevelSystem; }
    public boolean isFeatureTeleportation() { return featureTeleportation; }

    // Time getters
    public int getTicksPerHour() { return ticksPerHour; }
    public int getTicksPerDay() { return ticksPerDay; }

    // PlaceholderAPI
    public int getPlaceholderUpdateInterval() { return placeholderUpdateInterval; }
 
    // Security extras
    public boolean isSecurityRequireConfirmation() { return securityRequireConfirmation; }
    public boolean isSecurityLogSuspicious() { return securityLogSuspicious; }
 
    // External GUI configuration file (apartment_gui.yml)
    private File guiConfigFile;
    private FileConfiguration guiConfig;
 
    /**
     * Load or reload the external GUI configuration file (apartment_gui.yml).
     * If the file doesn't exist in plugin data folder, attempt to copy bundled resource.
     */
    public void loadGuiConfig() {
        try {
            if (!plugin.getDataFolder().exists()) {
                if (!plugin.getDataFolder().mkdirs()) {
                    plugin.getLogger().warning("Could not create plugin data folder to store apartment_gui.yml");
                }
            }
 
            this.guiConfigFile = new File(plugin.getDataFolder(), "apartment_gui.yml");
            if (!this.guiConfigFile.exists()) {
                // Try to copy bundled resource if available
                try (InputStream in = plugin.getResource("apartment_gui.yml")) {
                    if (in != null) {
                        Files.copy(in, this.guiConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        // create an empty file so the server admin can edit it
                        this.guiConfigFile.createNewFile();
                    }
                } catch (IOException ioe) {
                    plugin.getLogger().warning("Failed to create/copy apartment_gui.yml: " + ioe.getMessage());
                }
            }
 
            this.guiConfig = YamlConfiguration.loadConfiguration(this.guiConfigFile);
            plugin.debug("GUI configuration loaded from " + this.guiConfigFile.getName());
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to load GUI config: " + t.getMessage());
        }
    }
 
    /**
     * Get the raw GUI FileConfiguration. Loads it lazily if needed.
     */
    public FileConfiguration getGuiConfig() {
        if (this.guiConfig == null) {
            loadGuiConfig();
        }
        return this.guiConfig;
    }
 
    /**
     * Get a ConfigurationSection for a named menu under 'menus.<menuId>' in apartment_gui.yml
     * Example: getGuiMenuSection("main-menu") => configuration section at menus.main-menu
     */
    public ConfigurationSection getGuiMenuSection(String menuId) {
        FileConfiguration cfg = getGuiConfig();
        if (cfg == null) return null;
        return cfg.getConfigurationSection("menus." + menuId);
    }
 
}