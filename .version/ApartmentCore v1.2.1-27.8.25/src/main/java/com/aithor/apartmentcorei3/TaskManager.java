package com.aithor.apartmentcorei3;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Manages all scheduled tasks for the plugin
 */
public class TaskManager {
    private final ApartmentCorei3 plugin;
    private final ApartmentManager apartmentManager;
    private final ConfigManager configManager;

    public TaskManager(ApartmentCorei3 plugin, ApartmentManager apartmentManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.apartmentManager = apartmentManager;
        this.configManager = configManager;
    }

    /**
     * Start all scheduled tasks
     */
    public void startAllTasks() {
        startIncomeTask();
        startTaxTask();
        startConfirmationCleanupTask();
        startRentClaimTracker();
        
        if (configManager.isAutoSaveEnabled()) {
            startAutoSaveTask();
        }
        
        if (configManager.isBackupEnabled()) {
            startBackupTask();
        }
    }

    /**
     * Start income generation task
     */
    private void startIncomeTask() {
        // Run every Minecraft hour (1000 ticks = 50 seconds real time)
        new BukkitRunnable() {
            @Override
            public void run() {
                apartmentManager.generateIncome();
            }
        }.runTaskTimer(plugin, 1000L, 1000L);
    }

    /**
     * Start rent claim time tracker
     */
    private void startRentClaimTracker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.setLastRentClaimTime(System.currentTimeMillis());
            }
        }.runTaskTimer(plugin, 20L, 20L); // Update every second
    }

    /**
     * Start tax collection task
     */
    private void startTaxTask() {
        // Check every 100 ticks (5 seconds) for day changes
        new BukkitRunnable() {
            @Override
            public void run() {
                World mainWorld = plugin.getServer().getWorlds().get(0);
                if (mainWorld == null) return;

                long currentDay = mainWorld.getFullTime() / 24000;
                long lastMinecraftDay = plugin.getLastMinecraftDay();

                if (currentDay > lastMinecraftDay) {
                    long daysPassed = currentDay - lastMinecraftDay;
                    plugin.debug("Minecraft day changed. Days passed: " + daysPassed);

                    apartmentManager.processDailyTaxes(daysPassed);

                    plugin.setLastMinecraftDay(currentDay);
                    apartmentManager.saveApartments();
                }
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    /**
     * Start confirmation cleanup task
     */
    private void startConfirmationCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                plugin.getPendingConfirmations().entrySet().removeIf(entry ->
                        now - entry.getValue().timestamp > 30000); // 30 seconds timeout
            }
        }.runTaskTimer(plugin, 600L, 600L); // Every 30 seconds
    }

    /**
     * Start auto-save task
     */
    private void startAutoSaveTask() {
        long interval = configManager.getAutoSaveInterval() * 60 * 20L; // Convert minutes to ticks
        
        new BukkitRunnable() {
            @Override
            public void run() {
                apartmentManager.saveApartments();
                apartmentManager.saveRatings();
                plugin.log("Auto-saved apartment data");
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    /**
     * Start backup task
     */
    private void startBackupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getDataManager().createBackup("auto");
            }
        }.runTaskTimer(plugin, 72000L, 72000L); // Every hour
    }

    /**
     * Cancel all tasks
     */
    public void cancelAllTasks() {
        plugin.getServer().getScheduler().cancelTasks(plugin);
    }
}