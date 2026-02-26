package com.aithor.apartmentcorei3.manager;

import com.aithor.apartmentcorei3.ApartmentCorei3;

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
        startDailyUpdateTask();
        startConfirmationCleanupTask();

        if (configManager.isAutoSaveEnabled()) {
            startAutoSaveTask();
        }

        // Backups are now part of the auto-save task for simplicity in some setups.
        // If you want a separate backup schedule, you can re-add startBackupTask() here.
    }

    /**
     * Start income generation task with shop buff consideration
     * Respects:
     * - features.income-generation
     * - performance.use-async
     * - time.ticks-per-hour
     */
    private void startIncomeTask() {
        if (!configManager.isFeatureIncomeGeneration()) {
            plugin.debug("Income generation disabled via features.income-generation");
            return;
        }
        long period = Math.max(1L, configManager.getTicksPerHour()); // ticks
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.setLastIncomeGenerationTime(System.currentTimeMillis());
                apartmentManager.generateIncome();
            }
        };
        if (configManager.isPerformanceUseAsync()) {
            task.runTaskTimerAsynchronously(plugin, period, period);
            plugin.debug("Income task scheduled ASYNC every " + period + " ticks");
        } else {
            task.runTaskTimer(plugin, period, period);
            plugin.debug("Income task scheduled SYNC every " + period + " ticks");
        }
    }

    /**
     * Start tax collection and daily update task
     * Respects:
     * - features.tax-system
     * - time.ticks-per-day
     */
    private void startDailyUpdateTask() {
        // Check every 100 ticks (5 seconds) for day changes
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!configManager.isFeatureTaxSystem()) {
                    return; // tax system disabled
                }
                World mainWorld = plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().get(0);
                if (mainWorld == null) return;

                long ticksPerDay = Math.max(1, configManager.getTicksPerDay());
                long currentDay = mainWorld.getFullTime() / ticksPerDay;
                long lastMinecraftDay = plugin.getLastMinecraftDay();

                if (currentDay > lastMinecraftDay) {
                    plugin.debug("Minecraft day changed (ticksPerDay=" + ticksPerDay + "). Processing daily updates...");

                    apartmentManager.processDailyUpdates();

                    plugin.setLastMinecraftDay(currentDay);
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
                long timeout = plugin.getConfig().getLong("security.confirmation-timeout", 30) * 1000;
                plugin.getPendingConfirmations().entrySet().removeIf(entry ->
                        now - entry.getValue().timestamp > timeout);
            }
        }.runTaskTimer(plugin, 600L, 600L); // Every 30 seconds
    }

    /**
     * Start auto-save task, which now also handles backups.
     */
    private void startAutoSaveTask() {
        long interval = configManager.getAutoSaveInterval() * 60 * 20L; // Convert minutes to ticks

        new BukkitRunnable() {
            private int runs = 0;
            private int backupFrequency = 6; // Run backup every 6th save (e.g., every 60 mins if save is 10 mins)

            @Override
            public void run() {
                apartmentManager.saveApartments();
                apartmentManager.saveRatings();
                apartmentManager.saveGuestBooks();
                apartmentManager.saveStats();
                // Also persist auctions for consistency snapshots
                if (plugin.getAuctionManager() != null) {
                    try { plugin.getAuctionManager().saveAuctions(); } catch (Throwable ignored) {}
                }
                plugin.log("Auto-saved all data.");
 
                runs++;
                if (configManager.isBackupEnabled() && runs >= backupFrequency) {
                    plugin.getDataManager().createBackup("auto");
                    runs = 0; // Reset counter
                }
            }
        }.runTaskTimer(plugin, interval, interval); // Run on main thread for Bukkit API thread-safety
    }

    /**
     * Cancel all tasks
     */
    public void cancelAllTasks() {
        plugin.getServer().getScheduler().cancelTasks(plugin);
    }
}