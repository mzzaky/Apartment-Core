package com.aithor.apartmentcore.manager;

import com.aithor.apartmentcore.ApartmentCore;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/**
 * Manages data file operations and backups
 */
public class DataManager {
    private final ApartmentCore plugin;
    private final ConfigManager configManager;

    private FileConfiguration dataConfig;
    private File dataFile;
    private File backupFolder;
    private FileConfiguration guestBookConfig;
    private File guestBookFile;
    private FileConfiguration statsConfig;
    private File statsFile;


    public DataManager(ApartmentCore plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        setupBackupSystem();
        loadGuestBookFile(); // Initialize guestbook file
        loadStatsFile(); // Initialize stats file
    }

    /**
     * Load data file for apartment storage
     */
    public void loadDataFile() {
        try {
            File dataDir = new File(plugin.getDataFolder(), "data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            dataFile = new File(dataDir, "apartments.yml");
            if (!dataFile.exists()) {
                dataFile.createNewFile();
                plugin.debug("Created new apartments.yml file");
            }

            dataConfig = YamlConfiguration.loadConfiguration(dataFile);

            // Load last tax check day and rent claim time
            long lastMinecraftDay = dataConfig.getLong("last-minecraft-day", 0);
            long lastRentClaimTime = dataConfig.getLong("last-rent-claim-time", System.currentTimeMillis());

            plugin.setLastMinecraftDay(lastMinecraftDay);
            plugin.setLastRentClaimTime(lastRentClaimTime);

            plugin.debug("Data file loaded successfully");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not create apartments.yml: " + e.getMessage());
            dataConfig = new YamlConfiguration(); // Create empty config to prevent null
        }
    }
    
    /**
     * Load the guestbook data file.
     */
    public void loadGuestBookFile() {
        try {
            File dataDir = new File(plugin.getDataFolder(), "data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            guestBookFile = new File(dataDir, "guestbook.yml");
            if (!guestBookFile.exists()) {
                guestBookFile.createNewFile();
                plugin.debug("Created new guestbook.yml file");
            }
            guestBookConfig = YamlConfiguration.loadConfiguration(guestBookFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not create guestbook.yml: " + e.getMessage());
            guestBookConfig = new YamlConfiguration();
        }
    }

    /**
     * Load the stats data file.
     */
    public void loadStatsFile() {
        try {
            File dataDir = new File(plugin.getDataFolder(), "data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            statsFile = new File(dataDir, "apartments-stats.yml");
            if (!statsFile.exists()) {
                statsFile.createNewFile();
                plugin.debug("Created new apartments-stats.yml file");
            }
            statsConfig = YamlConfiguration.loadConfiguration(statsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not create apartments-stats.yml: " + e.getMessage());
            statsConfig = new YamlConfiguration();
        }
    }


    /**
     * Save data file
     */
    public void saveDataFile() {
        if (dataConfig == null || dataFile == null) {
            plugin.debug("Cannot save data - not initialized");
            return;
        }

        try {
            dataConfig.save(dataFile);
            plugin.debug("Data file saved successfully");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save apartments: " + e.getMessage());
        }
    }
    
    /**
     * Save the guestbook data file.
     */
    public void saveGuestBookFile() {
        if (guestBookConfig == null || guestBookFile == null) {
            plugin.debug("Cannot save guestbook data - not initialized");
            return;
        }
        try {
            guestBookConfig.save(guestBookFile);
            plugin.debug("Guestbook file saved successfully.");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save guestbook.yml: " + e.getMessage());
        }
    }

    /**
     * Save the stats data file.
     */
    public void saveStatsFile() {
        if (statsConfig == null || statsFile == null) {
            plugin.debug("Cannot save stats data - not initialized");
            return;
        }
        try {
            statsConfig.save(statsFile);
            plugin.debug("Stats file saved successfully.");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save apartments-stats.yml: " + e.getMessage());
        }
    }

    /**
     * Setup backup system
     */
    private void setupBackupSystem() {
        File dataDir = new File(plugin.getDataFolder(), "data");
        backupFolder = new File(dataDir, "backups");
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }

        // Clean old backups on startup
        cleanOldBackups();
    }

    /**
     * Create backup of apartment data
     */
    public void createBackup(String type) {
        if (!configManager.isBackupEnabled()) return;

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String timestamp = sdf.format(new Date());
            String backupName = String.format("apartments_%s_%s.yml", type, timestamp);

            File backupFile = new File(backupFolder, backupName);

            if (dataFile != null && dataFile.exists()) {
                Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.debug("Created backup: " + backupName);
            }

            // Clean old backups after creating new one
            cleanOldBackups();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create backup: " + e.getMessage());
        }
    }

    /**
     * Clean old backup files
     */
    private void cleanOldBackups() {
        File[] backups = backupFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (backups != null && backups.length > configManager.getMaxBackups()) {
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));

            for (int i = 0; i < backups.length - configManager.getMaxBackups(); i++) {
                if (backups[i].delete()) {
                    plugin.debug("Deleted old backup: " + backups[i].getName());
                }
            }
        }
    }

    /**
     * Restore from backup
     */
    public boolean restoreBackup(String backupName) {
        File backupFile = new File(backupFolder, backupName);
        if (!backupFile.exists()) {
            return false;
        }

        try {
            // Create current backup before restore
            createBackup("pre-restore");

            // Restore from backup
            Files.copy(backupFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Reload data
            loadDataFile();

            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to restore backup: " + e.getMessage());
            return false;
        }
    }

    // Getters
    public FileConfiguration getDataConfig() {
        return dataConfig;
    }
    
    public FileConfiguration getGuestBookConfig() {
        return guestBookConfig;
    }

    public FileConfiguration getStatsConfig() {
        return statsConfig;
    }


    public File getDataFile() {
        return dataFile;
    }

    public File getBackupFolder() {
        return backupFolder;
    }
}