package com.aithor.apartmentcorei3;

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
    private final ApartmentCorei3 plugin;
    private final ConfigManager configManager;
    
    private FileConfiguration dataConfig;
    private File dataFile;
    private File backupFolder;

    public DataManager(ApartmentCorei3 plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        setupBackupSystem();
    }

    /**
     * Load data file for apartment storage
     */
    public void loadDataFile() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            dataFile = new File(plugin.getDataFolder(), "apartments.yml");
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
     * Setup backup system
     */
    private void setupBackupSystem() {
        backupFolder = new File(plugin.getDataFolder(), "backups");
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

    public File getDataFile() {
        return dataFile;
    }

    public File getBackupFolder() {
        return backupFolder;
    }
}