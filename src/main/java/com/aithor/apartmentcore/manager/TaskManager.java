package com.aithor.apartmentcore.manager;

import com.aithor.apartmentcore.ApartmentCore;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * Manages all scheduled tasks for the plugin
 */
public class TaskManager {
    private final ApartmentCore plugin;
    private final ApartmentManager apartmentManager;
    private final ConfigManager configManager;

    public TaskManager(ApartmentCore plugin, ApartmentManager apartmentManager, ConfigManager configManager) {
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
        startUpgradeCheckTask();

        if (configManager.isAutoSaveEnabled()) {
            startAutoSaveTask();
        }

        // Backups are now part of the auto-save task for simplicity in some setups.
        // If you want a separate backup schedule, you can re-add startBackupTask()
        // here.
    }

    /**
     * Start income generation task with shop buff consideration
     * Respects:
     * - features.income-generation
     * - performance.use-async
     * - income.generation-interval
     */
    private void startIncomeTask() {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                long intervalMs = Math.max(1000L, configManager.getIncomeGenerationInterval() * 50L);
                long now = System.currentTimeMillis();
                long lastGen = plugin.getLastIncomeGenerationTime();

                if (lastGen <= 0) {
                    plugin.setLastIncomeGenerationTime(now);
                    return;
                }

                boolean changed = false;
                while (now - plugin.getLastIncomeGenerationTime() >= intervalMs) {
                    plugin.setLastIncomeGenerationTime(plugin.getLastIncomeGenerationTime() + intervalMs);
                    apartmentManager.generateIncome();
                    changed = true;
                }

                // If we generated income behind the scenes, we could save the timer here if
                // needed
            }
        };
        if (configManager.isPerformanceUseAsync()) {
            // Check every 100 ticks (5 seconds)
            task.runTaskTimerAsynchronously(plugin, 100L, 100L);
            plugin.debug("Income task scheduled ASYNC (checking every 5s)");
        } else {
            task.runTaskTimer(plugin, 100L, 100L);
            plugin.debug("Income task scheduled SYNC (checking every 5s)");
        }
    }

    /**
     * Start tax collection and daily update task
     * Respects:
     * - features.tax-system
     * - settings.tax-generation-interval
     *
     * Uses real-time milliseconds to track the tax cycle so that changes to
     * income-generation-interval do NOT affect when taxes are processed.
     */
    private void startDailyUpdateTask() {
        // Check every 600 ticks (30 seconds) whether a full tax interval has elapsed
        new BukkitRunnable() {
            @Override
            public void run() {

                long now = System.currentTimeMillis();

                // 1) Tick taxes for every apartment individually based on their specific
                // lastInvoiceAt timestamp
                for (com.aithor.apartmentcore.model.Apartment apt : apartmentManager.getApartments().values()) {
                    if (apt.owner != null) {
                        apt.tickTaxInvoices(plugin.getEconomy(), plugin, configManager, apartmentManager);
                    }
                }

                long taxIntervalMs = Math.max(1000L, configManager.getTaxGenerationInterval() * 50L);
                long lastMinecraftDay = plugin.getLastMinecraftDay();

                // On first startup it is 0, so we seed it to now (no immediate trigger).
                if (lastMinecraftDay <= 0) {
                    plugin.setLastMinecraftDay(now);
                    return;
                }

                // 2) Process global daily updates (like apartment age)
                if (now - lastMinecraftDay >= taxIntervalMs) {
                    plugin.debug("Global tax interval elapsed (taxIntervalMs=" + taxIntervalMs
                            + "ms). Processing global daily updates (age)...");

                    while (now - plugin.getLastMinecraftDay() >= taxIntervalMs) {
                        apartmentManager.processDailyUpdates();
                        plugin.setLastMinecraftDay(plugin.getLastMinecraftDay() + taxIntervalMs);
                    }
                }
            }
        }.runTaskTimer(plugin, 600L, 600L); // Check every 30 seconds
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
                plugin.getPendingConfirmations().entrySet()
                        .removeIf(entry -> now - entry.getValue().timestamp > timeout);
            }
        }.runTaskTimer(plugin, 600L, 600L); // Every 30 seconds
    }

    /**
     * Start periodic check for completed apartment upgrades
     */
    private void startUpgradeCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                boolean changed = false;

                for (com.aithor.apartmentcore.model.Apartment apt : apartmentManager.getApartments().values()) {
                    if (apt.upgradeInProgress && apt.upgradeCompleteAt > 0 && now >= apt.upgradeCompleteAt) {
                        // Upgrade completed!
                        apt.upgradeInProgress = false;
                        apt.upgradeCompleteAt = 0L;
                        apt.level++;
                        changed = true;

                        // Notify owner
                        if (apt.owner != null) {
                            org.bukkit.entity.Player ownerPlayer = plugin.getServer().getPlayer(apt.owner);
                            if (ownerPlayer != null && ownerPlayer.isOnline()) {
                                try {
                                    ownerPlayer.playSound(ownerPlayer.getLocation(),
                                            org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                                    String title = plugin.getMessageManager().getMessage("notifications.upgrade_title")
                                            .replace("%apartment%", apt.displayName)
                                            .replace("%level%", String.valueOf(apt.level));
                                    String subtitle = plugin.getMessageManager().getMessage("notifications.upgrade_subtitle")
                                            .replace("%apartment%", apt.displayName)
                                            .replace("%level%", String.valueOf(apt.level));
                                    String actionBar = plugin.getMessageManager().getMessage("notifications.upgrade_actionbar")
                                            .replace("%apartment%", apt.displayName)
                                            .replace("%level%", String.valueOf(apt.level));

                                    ownerPlayer.sendTitle(title, subtitle, 10, 70, 20);
                                    ownerPlayer.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                            new net.md_5.bungee.api.chat.TextComponent(actionBar));
                                } catch (Exception ignored) {
                                }
                            }

                            // Track max level achievement
                            if (plugin.getAchievementManager() != null) {
                                int maxLevel = configManager.getLevelConfigs().keySet().stream()
                                        .mapToInt(Integer::intValue).max().orElse(5);
                                if (apt.level >= maxLevel) {
                                    plugin.getAchievementManager().setProgress(apt.owner,
                                            com.aithor.apartmentcore.achievement.AchievementType.MAX_LEVEL_OWNER, 1);
                                }
                            }
                        }
                    }
                }

                if (changed) {
                    apartmentManager.saveApartments();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Check every second
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
                    try {
                        plugin.getAuctionManager().saveAuctions();
                    } catch (Throwable ignored) {
                    }
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