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
}