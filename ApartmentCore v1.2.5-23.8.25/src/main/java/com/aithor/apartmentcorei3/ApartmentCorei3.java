package com.aithor.apartmentcorei3;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Main plugin class for ApartmentCore
 * Manages apartments in Minecraft with WorldGuard regions
 * @author Aithor
 * @version 1.2.5
 */
public class ApartmentCorei3 extends JavaPlugin implements TabCompleter {

    private Economy economy;
    private WorldGuardPlugin worldGuard;
    private Map<String, Apartment> apartments;
    private Map<UUID, Long> commandCooldowns;
    private Map<UUID, ConfirmationAction> pendingConfirmations;
    private Map<String, ApartmentRating> apartmentRatings;
    private Map<UUID, Map<String, Long>> playerRatingCooldowns;
    private Map<String, GuestBook> guestBooks;
    private Map<String, ApartmentStatistics> apartmentStats;
    private Map<UUID, VirtualTour> activeTours;
    private FileConfiguration dataConfig;
    private File dataFile;
    private File backupFolder;
    private long lastMinecraftDay = 0;
    private long lastRentClaimTime = 0;
    private long serverStartTime;

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
    private int maxGuestBookMessages;
    private Map<Integer, LevelConfig> levelConfigs;

    @Override
    public void onEnable() {
        // Initialize collections
        apartments = new ConcurrentHashMap<>();
        commandCooldowns = new ConcurrentHashMap<>();
        pendingConfirmations = new ConcurrentHashMap<>();
        apartmentRatings = new ConcurrentHashMap<>();
        playerRatingCooldowns = new ConcurrentHashMap<>();
        guestBooks = new ConcurrentHashMap<>();
        apartmentStats = new ConcurrentHashMap<>();
        activeTours = new ConcurrentHashMap<>();
        levelConfigs = new HashMap<>();
        serverStartTime = System.currentTimeMillis();

        // Save default config
        saveDefaultConfig();
        loadConfiguration();

        // Setup backup folder
        setupBackupSystem();

        // Initialize data file first (before dependency checks)
        loadDataFile();

        // Setup dependencies
        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!setupWorldGuard()) {
            getLogger().severe("WorldGuard not found! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Load apartments data
        loadApartments();
        loadRatings();
        loadGuestBooks();
        loadStatistics();

        // Initialize Minecraft day tracking
        World mainWorld = getServer().getWorlds().get(0);
        if (mainWorld != null) {
            lastMinecraftDay = mainWorld.getFullTime() / 24000;
        }

        // Register PlaceholderAPI expansion with delay
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    new ApartmentPlaceholder().register();
                    log("PlaceholderAPI expansion registered successfully");
                }
            }.runTaskLater(this, 20L);
        }

        // Register tab completer
        getCommand("apartmentcore").setTabCompleter(this);

        // Start tasks
        startIncomeTask();
        startTaxTask();
        startConfirmationCleanupTask();
        startRentClaimTracker();
        startVirtualTourTask();
        if (autoSaveEnabled) {
            startAutoSaveTask();
        }
        if (backupEnabled) {
            startBackupTask();
        }

        log("ApartmentCore v1.2.5 enabled successfully!");
        log("Loaded " + apartments.size() + " apartments");
    }

    @Override
    public void onDisable() {
        // Cancel all tasks
        getServer().getScheduler().cancelTasks(this);

        // End all virtual tours
        for (VirtualTour tour : activeTours.values()) {
            endVirtualTour(tour.player);
        }

        // Create backup on shutdown
        if (backupEnabled) {
            createBackup("shutdown");
        }

        // Only save if data is properly initialized
        if (dataConfig != null && apartments != null) {
            saveApartments();
            saveRatings();
            saveGuestBooks();
            saveStatistics();
            log("ApartmentCore disabled. All data saved.");
        } else {
            log("ApartmentCore disabled without saving (not initialized properly).");
        }
    }

    /**
     * Load configuration values from config.yml
     */
    private void loadConfiguration() {
        FileConfiguration config = getConfig();
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
        maxGuestBookMessages = config.getInt("guestbook.max-messages", 50);

        // Load level configurations
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
    }

    /**
     * Setup backup system
     */
    private void setupBackupSystem() {
        backupFolder = new File(getDataFolder(), "backups");
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }

        // Clean old backups
        cleanOldBackups();
    }

    /**
     * Create backup of apartment data
     */
    private void createBackup(String type) {
        if (!backupEnabled) return;

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String timestamp = sdf.format(new Date());
            String backupName = String.format("apartments_%s_%s.yml", type, timestamp);

            File backupFile = new File(backupFolder, backupName);

            if (dataFile.exists()) {
                Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                debug("Created backup: " + backupName);
            }

            // Clean old backups
            cleanOldBackups();
        } catch (IOException e) {
            getLogger().warning("Failed to create backup: " + e.getMessage());
        }
    }

    /**
     * Clean old backup files
     */
    private void cleanOldBackups() {
        File[] backups = backupFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (backups != null && backups.length > maxBackups) {
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));

            for (int i = 0; i < backups.length - maxBackups; i++) {
                if (backups[i].delete()) {
                    debug("Deleted old backup: " + backups[i].getName());
                }
            }
        }
    }

    /**
     * Restore from backup
     */
    private boolean restoreBackup(String backupName) {
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
            loadApartments();
            loadRatings();
            loadGuestBooks();
            loadStatistics();

            return true;
        } catch (IOException e) {
            getLogger().severe("Failed to restore backup: " + e.getMessage());
            return false;
        }
    }

    /**
     * Setup Vault economy
     */
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    /**
     * Setup WorldGuard integration
     */
    private boolean setupWorldGuard() {
        worldGuard = (WorldGuardPlugin) getServer().getPluginManager().getPlugin("WorldGuard");
        return worldGuard != null;
    }

    /**
     * Load data file for apartment storage
     */
    private void loadDataFile() {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            dataFile = new File(getDataFolder(), "apartments.yml");
            if (!dataFile.exists()) {
                dataFile.createNewFile();
                debug("Created new apartments.yml file");
            }

            dataConfig = YamlConfiguration.loadConfiguration(dataFile);

            // Load last tax check day
            lastMinecraftDay = dataConfig.getLong("last-minecraft-day", 0);
            lastRentClaimTime = dataConfig.getLong("last-rent-claim-time", System.currentTimeMillis());

            debug("Data file loaded successfully");
        } catch (IOException e) {
            getLogger().severe("Could not create apartments.yml: " + e.getMessage());
            dataConfig = new YamlConfiguration(); // Create empty config to prevent null
        }
    }

    /**
     * Load all apartments from storage
     */
    private void loadApartments() {
        ConfigurationSection section = dataConfig.getConfigurationSection("apartments");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            try {
                ConfigurationSection aptSection = section.getConfigurationSection(id);
                if (aptSection == null) continue;

                // Load custom spawn location
                Location customSpawn = null;
                if (aptSection.contains("custom-spawn")) {
                    customSpawn = new Location(
                            Bukkit.getWorld(aptSection.getString("custom-spawn.world")),
                            aptSection.getDouble("custom-spawn.x"),
                            aptSection.getDouble("custom-spawn.y"),
                            aptSection.getDouble("custom-spawn.z"),
                            (float) aptSection.getDouble("custom-spawn.yaw"),
                            (float) aptSection.getDouble("custom-spawn.pitch")
                    );
                }

                // Load preview location
                Location previewLocation = null;
                if (aptSection.contains("preview-location")) {
                    previewLocation = new Location(
                            Bukkit.getWorld(aptSection.getString("preview-location.world")),
                            aptSection.getDouble("preview-location.x"),
                            aptSection.getDouble("preview-location.y"),
                            aptSection.getDouble("preview-location.z"),
                            (float) aptSection.getDouble("preview-location.yaw"),
                            (float) aptSection.getDouble("preview-location.pitch")
                    );
                }

                Apartment apt = new Apartment(
                        id,
                        aptSection.getString("region"),
                        aptSection.getString("world"),
                        aptSection.getString("owner") != null ? UUID.fromString(aptSection.getString("owner")) : null,
                        aptSection.getDouble("price"),
                        aptSection.getDouble("tax"),
                        aptSection.getInt("tax-days"),
                        aptSection.getInt("level", 1),
                        aptSection.getLong("last-tax-payment", System.currentTimeMillis()),
                        aptSection.getDouble("pending-income", 0),
                        aptSection.getBoolean("inactive", false),
                        aptSection.getDouble("penalty", 0),
                        aptSection.getLong("inactive-since", 0),
                        aptSection.getString("display-name", id),
                        aptSection.getString("welcome-message", ""),
                        customSpawn,
                        previewLocation
                );

                apartments.put(id, apt);
            } catch (Exception e) {
                getLogger().warning("Failed to load apartment " + id + ": " + e.getMessage());
            }
        }

        debug("Loaded " + apartments.size() + " apartments from storage");
    }

    /**
     * Load apartment ratings
     */
    private void loadRatings() {
        ConfigurationSection section = dataConfig.getConfigurationSection("ratings");
        if (section == null) return;

        for (String apartmentId : section.getKeys(false)) {
            ConfigurationSection ratingSection = section.getConfigurationSection(apartmentId);
            if (ratingSection == null) continue;

            ApartmentRating rating = new ApartmentRating();
            rating.totalRating = ratingSection.getDouble("total", 0);
            rating.ratingCount = ratingSection.getInt("count", 0);

            ConfigurationSection ratersSection = ratingSection.getConfigurationSection("raters");
            if (ratersSection != null) {
                for (String uuid : ratersSection.getKeys(false)) {
                    rating.raters.put(UUID.fromString(uuid), ratersSection.getDouble(uuid));
                }
            }

            apartmentRatings.put(apartmentId, rating);
        }
    }

    /**
     * Load guest books
     */
    private void loadGuestBooks() {
        ConfigurationSection section = dataConfig.getConfigurationSection("guestbooks");
        if (section == null) return;

        for (String apartmentId : section.getKeys(false)) {
            GuestBook guestBook = new GuestBook();
            ConfigurationSection gbSection = section.getConfigurationSection(apartmentId);
            if (gbSection == null) continue;

            ConfigurationSection messagesSection = gbSection.getConfigurationSection("messages");
            if (messagesSection != null) {
                for (String key : messagesSection.getKeys(false)) {
                    ConfigurationSection msgSection = messagesSection.getConfigurationSection(key);
                    if (msgSection == null) continue;

                    GuestMessage message = new GuestMessage(
                            UUID.fromString(msgSection.getString("sender")),
                            msgSection.getString("message"),
                            msgSection.getLong("timestamp")
                    );
                    guestBook.messages.add(message);
                }
            }

            ConfigurationSection giftsSection = gbSection.getConfigurationSection("gifts");
            if (giftsSection != null) {
                for (String key : giftsSection.getKeys(false)) {
                    ConfigurationSection giftSection = giftsSection.getConfigurationSection(key);
                    if (giftSection == null) continue;

                    try {
                        ItemStack item = itemFromBase64(giftSection.getString("item"));
                        GuestGift gift = new GuestGift(
                                UUID.fromString(giftSection.getString("sender")),
                                item,
                                giftSection.getString("message"),
                                giftSection.getLong("timestamp")
                        );
                        guestBook.gifts.add(gift);
                    } catch (Exception e) {
                        debug("Failed to load gift: " + e.getMessage());
                    }
                }
            }

            guestBooks.put(apartmentId, guestBook);
        }
    }

    /**
     * Load apartment statistics
     */
    private void loadStatistics() {
        ConfigurationSection section = dataConfig.getConfigurationSection("statistics");
        if (section == null) return;

        for (String apartmentId : section.getKeys(false)) {
            ConfigurationSection statSection = section.getConfigurationSection(apartmentId);
            if (statSection == null) continue;

            ApartmentStatistics stats = new ApartmentStatistics();
            stats.totalVisitors = statSection.getInt("total-visitors", 0);
            stats.totalIncome = statSection.getDouble("total-income", 0);
            stats.totalTaxPaid = statSection.getDouble("total-tax-paid", 0);
            stats.timesUpgraded = statSection.getInt("times-upgraded", 0);
            stats.timesSold = statSection.getInt("times-sold", 0);
            stats.createdDate = statSection.getLong("created-date", System.currentTimeMillis());
            stats.lastVisitDate = statSection.getLong("last-visit", System.currentTimeMillis());

            // Load unique visitors
            List<String> visitors = statSection.getStringList("unique-visitors");
            for (String uuid : visitors) {
                stats.uniqueVisitors.add(UUID.fromString(uuid));
            }

            apartmentStats.put(apartmentId, stats);
        }
    }

    /**
     * Save all apartments to storage
     */
    private void saveApartments() {
        if (dataConfig == null || apartments == null) {
            debug("Cannot save apartments - data not initialized");
            return;
        }

        dataConfig.set("apartments", null);
        dataConfig.set("last-minecraft-day", lastMinecraftDay);
        dataConfig.set("last-rent-claim-time", lastRentClaimTime);

        for (Apartment apt : apartments.values()) {
            String path = "apartments." + apt.id + ".";
            dataConfig.set(path + "region", apt.regionName);
            dataConfig.set(path + "world", apt.worldName);
            dataConfig.set(path + "owner", apt.owner != null ? apt.owner.toString() : null);
            dataConfig.set(path + "price", apt.price);
            dataConfig.set(path + "tax", apt.tax);
            dataConfig.set(path + "tax-days", apt.taxDays);
            dataConfig.set(path + "level", apt.level);
            dataConfig.set(path + "last-tax-payment", apt.lastTaxPayment);
            dataConfig.set(path + "pending-income", apt.pendingIncome);
            dataConfig.set(path + "inactive", apt.inactive);
            dataConfig.set(path + "penalty", apt.penalty);
            dataConfig.set(path + "inactive-since", apt.inactiveSince);
            dataConfig.set(path + "display-name", apt.displayName);
            dataConfig.set(path + "welcome-message", apt.welcomeMessage);

            // Save custom spawn location
            if (apt.customSpawn != null) {
                dataConfig.set(path + "custom-spawn.world", apt.customSpawn.getWorld().getName());
                dataConfig.set(path + "custom-spawn.x", apt.customSpawn.getX());
                dataConfig.set(path + "custom-spawn.y", apt.customSpawn.getY());
                dataConfig.set(path + "custom-spawn.z", apt.customSpawn.getZ());
                dataConfig.set(path + "custom-spawn.yaw", apt.customSpawn.getYaw());
                dataConfig.set(path + "custom-spawn.pitch", apt.customSpawn.getPitch());
            }

            // Save preview location
            if (apt.previewLocation != null) {
                dataConfig.set(path + "preview-location.world", apt.previewLocation.getWorld().getName());
                dataConfig.set(path + "preview-location.x", apt.previewLocation.getX());
                dataConfig.set(path + "preview-location.y", apt.previewLocation.getY());
                dataConfig.set(path + "preview-location.z", apt.previewLocation.getZ());
                dataConfig.set(path + "preview-location.yaw", apt.previewLocation.getYaw());
                dataConfig.set(path + "preview-location.pitch", apt.previewLocation.getPitch());
            }
        }

        try {
            dataConfig.save(dataFile);
            debug("Saved " + apartments.size() + " apartments to storage");
        } catch (IOException e) {
            getLogger().severe("Could not save apartments: " + e.getMessage());
        }
    }

    /**
     * Save apartment ratings
     */
    private void saveRatings() {
        if (dataConfig == null) return;

        dataConfig.set("ratings", null);

        for (Map.Entry<String, ApartmentRating> entry : apartmentRatings.entrySet()) {
            String path = "ratings." + entry.getKey() + ".";
            ApartmentRating rating = entry.getValue();

            dataConfig.set(path + "total", rating.totalRating);
            dataConfig.set(path + "count", rating.ratingCount);

            for (Map.Entry<UUID, Double> rater : rating.raters.entrySet()) {
                dataConfig.set(path + "raters." + rater.getKey().toString(), rater.getValue());
            }
        }
    }

    /**
     * Save guest books
     */
    private void saveGuestBooks() {
        if (dataConfig == null) return;

        dataConfig.set("guestbooks", null);

        for (Map.Entry<String, GuestBook> entry : guestBooks.entrySet()) {
            String path = "guestbooks." + entry.getKey() + ".";
            GuestBook guestBook = entry.getValue();

            // Save messages
            int msgIndex = 0;
            for (GuestMessage msg : guestBook.messages) {
                String msgPath = path + "messages." + msgIndex + ".";
                dataConfig.set(msgPath + "sender", msg.sender.toString());
                dataConfig.set(msgPath + "message", msg.message);
                dataConfig.set(msgPath + "timestamp", msg.timestamp);
                msgIndex++;
            }

            // Save gifts
            int giftIndex = 0;
            for (GuestGift gift : guestBook.gifts) {
                String giftPath = path + "gifts." + giftIndex + ".";
                dataConfig.set(giftPath + "sender", gift.sender.toString());
                dataConfig.set(giftPath + "item", itemToBase64(gift.item));
                dataConfig.set(giftPath + "message", gift.message);
                dataConfig.set(giftPath + "timestamp", gift.timestamp);
                giftIndex++;
            }
        }
    }

    /**
     * Save apartment statistics
     */
    private void saveStatistics() {
        if (dataConfig == null) return;

        dataConfig.set("statistics", null);

        for (Map.Entry<String, ApartmentStatistics> entry : apartmentStats.entrySet()) {
            String path = "statistics." + entry.getKey() + ".";
            ApartmentStatistics stats = entry.getValue();

            dataConfig.set(path + "total-visitors", stats.totalVisitors);
            dataConfig.set(path + "total-income", stats.totalIncome);
            dataConfig.set(path + "total-tax-paid", stats.totalTaxPaid);
            dataConfig.set(path + "times-upgraded", stats.timesUpgraded);
            dataConfig.set(path + "times-sold", stats.timesSold);
            dataConfig.set(path + "created-date", stats.createdDate);
            dataConfig.set(path + "last-visit", stats.lastVisitDate);

            // Save unique visitors
            List<String> visitors = new ArrayList<>();
            for (UUID uuid : stats.uniqueVisitors) {
                visitors.add(uuid.toString());
            }
            dataConfig.set(path + "unique-visitors", visitors);
        }
    }

    /**
     * Convert ItemStack to Base64
     */
    private String itemToBase64(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convert Base64 to ItemStack
     */
    private ItemStack itemFromBase64(String data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();
        return item;
    }

    /**
     * Start income generation task
     */
    private void startIncomeTask() {
        // Run every Minecraft hour (1000 ticks = 50 seconds real time)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Apartment apt : apartments.values()) {
                    if (apt.owner != null && !apt.inactive) {
                        double income = apt.getHourlyIncome();
                        apt.pendingIncome += income;

                        // Update statistics
                        ApartmentStatistics stats = apartmentStats.computeIfAbsent(apt.id,
                                k -> new ApartmentStatistics());
                        stats.totalIncome += income;

                        debug("Generated " + formatMoney(income) + " income for apartment " + apt.id);
                    }
                }
            }
        }.runTaskTimer(this, 1000L, 1000L);
    }

    /**
     * Start rent claim time tracker
     */
    private void startRentClaimTracker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                lastRentClaimTime = System.currentTimeMillis();
            }
        }.runTaskTimer(this, 20L, 20L); // Update every second
    }

    /**
     * Start tax collection task
     */
    private void startTaxTask() {
        // Check every 100 ticks (5 seconds) for day changes
        new BukkitRunnable() {
            @Override
            public void run() {
                World mainWorld = getServer().getWorlds().get(0);
                if (mainWorld == null) return;

                long currentDay = mainWorld.getFullTime() / 24000;

                if (currentDay > lastMinecraftDay) {
                    long daysPassed = currentDay - lastMinecraftDay;
                    debug("Minecraft day changed. Days passed: " + daysPassed);

                    for (Apartment apt : apartments.values()) {
                        if (apt.owner != null) {
                            apt.processDailyTax(economy, daysPassed);
                        }
                    }

                    lastMinecraftDay = currentDay;
                    saveApartments();
                }
            }
        }.runTaskTimer(this, 100L, 100L);
    }

    /**
     * Start virtual tour cleanup task
     */
    private void startVirtualTourTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, VirtualTour>> it = activeTours.entrySet().iterator();

                while (it.hasNext()) {
                    Map.Entry<UUID, VirtualTour> entry = it.next();
                    VirtualTour tour = entry.getValue();

                    // End tour after 60 seconds
                    if (now - tour.startTime > 60000) {
                        endVirtualTour(tour.player);
                        it.remove();
                    }
                }
            }
        }.runTaskTimer(this, 100L, 100L); // Check every 5 seconds
    }

    /**
     * Start confirmation cleanup task
     */
    private void startConfirmationCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                pendingConfirmations.entrySet().removeIf(entry ->
                        now - entry.getValue().timestamp > 30000); // 30 seconds timeout
            }
        }.runTaskTimer(this, 600L, 600L); // Every 30 seconds
    }

    /**
     * Start auto-save task
     */
    private void startAutoSaveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveApartments();
                saveRatings();
                saveGuestBooks();
                saveStatistics();
                log("Auto-saved apartment data");
            }
        }.runTaskTimer(this, autoSaveInterval * 60 * 20L, autoSaveInterval * 60 * 20L);
    }

    /**
     * Start backup task
     */
    private void startBackupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                createBackup("auto");
            }
        }.runTaskTimer(this, 72000L, 72000L); // Every hour
    }

    /**
     * Check command cooldown
     */
    private boolean checkCooldown(Player player) {
        if (player.hasPermission("apartmentcore.bypass.cooldown")) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        Long lastUse = commandCooldowns.get(uuid);
        long now = System.currentTimeMillis();

        if (lastUse != null && now - lastUse < commandCooldown) {
            player.sendMessage(ChatColor.RED + "Please wait before using another command!");
            return false;
        }

        commandCooldowns.put(uuid, now);
        return true;
    }

    /**
     * Start virtual tour
     */
    private void startVirtualTour(Player player, Apartment apartment) {
        // Save current state
        VirtualTour tour = new VirtualTour(player, apartment.id, player.getLocation(),
                player.getGameMode(), System.currentTimeMillis());
        activeTours.put(player.getUniqueId(), tour);

        // Set spectator mode
        player.setGameMode(GameMode.SPECTATOR);

        // Teleport to preview location
        Location previewLoc = apartment.previewLocation != null ?
                apartment.previewLocation : getApartmentDefaultLocation(apartment);

        if (previewLoc != null) {
            player.teleport(previewLoc);
            player.sendMessage(ChatColor.GREEN + "Virtual tour started! You have 60 seconds to explore.");
            player.sendMessage(ChatColor.YELLOW + "Type /apartmentcore tour end to exit early.");
        }
    }

    /**
     * End virtual tour
     */
    private void endVirtualTour(Player player) {
        VirtualTour tour = activeTours.remove(player.getUniqueId());
        if (tour != null) {
            // Restore original state
            player.setGameMode(tour.originalGameMode);
            player.teleport(tour.originalLocation);
            player.sendMessage(ChatColor.GREEN + "Virtual tour ended!");
        }
    }

    /**
     * Get default apartment location
     */
    private Location getApartmentDefaultLocation(Apartment apt) {
        World world = Bukkit.getWorld(apt.worldName);
        if (world == null) return null;

        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (regionManager != null) {
            ProtectedRegion region = regionManager.getRegion(apt.regionName);
            if (region != null) {
                BlockVector3 min = region.getMinimumPoint();
                BlockVector3 max = region.getMaximumPoint();

                double x = (min.getX() + max.getX()) / 2.0;
                double z = (min.getZ() + max.getZ()) / 2.0;
                double y = min.getY();

                Location loc = new Location(world, x, y, z);
                return world.getHighestBlockAt((int)x, (int)z).getLocation().add(0, 1, 0);
            }
        }
        return null;
    }

    /**
     * Get time until next income
     */
    private String getTimeUntilNextIncome() {
        World world = getServer().getWorlds().get(0);
        if (world == null) return "N/A";

        long currentTick = world.getFullTime() % 1000; // Current tick within the hour
        long ticksUntilNextHour = 1000 - currentTick;
        long secondsUntilNext = ticksUntilNextHour / 20; // Convert ticks to seconds

        long minutes = secondsUntilNext / 60;
        long seconds = secondsUntilNext % 60;

        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Get time until next tax for apartment
     */
    private String getTimeUntilNextTax(Apartment apt) {
        World world = getServer().getWorlds().get(0);
        if (world == null) return "N/A";

        long currentDay = world.getFullTime() / 24000;
        long lastTaxDay = apt.lastTaxPayment / (24000 * 50); // Convert to MC days
        long daysSinceTax = currentDay - lastTaxDay;
        long daysUntilTax = apt.taxDays - daysSinceTax;

        if (daysUntilTax <= 0) return "Due Now";

        long currentTimeInDay = world.getFullTime() % 24000;
        long ticksUntilNextDay = 24000 - currentTimeInDay;
        long totalTicksUntilTax = (daysUntilTax - 1) * 24000 + ticksUntilNextDay;

        long totalSeconds = totalTicksUntilTax / 20;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("apartmentcore")) {
            return false;
        }

        // Check cooldown for players
        if (sender instanceof Player && !checkCooldown((Player) sender)) {
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "=== ApartmentCore v1.2.5 ===");
            sender.sendMessage(ChatColor.YELLOW + "Author: " + ChatColor.WHITE + "Aithor");
            sender.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/apartmentcore help" + ChatColor.YELLOW + " for commands");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "version":
                sender.sendMessage(ChatColor.GREEN + "ApartmentCore version 1.2.5");
                return true;

            case "info":
                if (args.length == 1) {
                    sender.sendMessage(ChatColor.GOLD + "=== ApartmentCore Info ===");
                    sender.sendMessage(ChatColor.YELLOW + "Total Apartments: " + ChatColor.WHITE + apartments.size());
                    sender.sendMessage(ChatColor.YELLOW + "Owned Apartments: " + ChatColor.WHITE +
                            apartments.values().stream().filter(a -> a.owner != null).count());
                    sender.sendMessage(ChatColor.YELLOW + "For Sale: " + ChatColor.WHITE +
                            apartments.values().stream().filter(a -> a.owner == null).count());
                    sender.sendMessage(ChatColor.YELLOW + "Economy: " + ChatColor.WHITE + economy.getName());
                    return true;
                } else if (args.length == 2) {
                    return handleInfoCommand(sender, args[1]);
                }
                break;

            case "stats":
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore stats <apartment_id>");
                    return true;
                }
                return handleStatsCommand(sender, args[1]);

            case "setspawn":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore setspawn <apartment_id>");
                    return true;
                }
                return handleSetSpawnCommand((Player) sender, args[1]);

            case "setpreview":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore setpreview <apartment_id>");
                    return true;
                }
                return handleSetPreviewCommand((Player) sender, args[1]);

            case "tour":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore tour <start|end> [apartment_id]");
                    return true;
                }
                return handleTourCommand((Player) sender, args);

            case "guestbook":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore guestbook <view|sign|gift|claim> <apartment_id> [message]");
                    return true;
                }
                return handleGuestBookCommand((Player) sender, Arrays.copyOfRange(args, 1, args.length));

            case "buy":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.buy")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to buy apartments!");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore buy <apartment_id>");
                    return true;
                }
                return handleBuyCommand((Player) sender, args[1]);

            case "sell":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.sell")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to sell apartments!");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore sell <apartment_id>");
                    return true;
                }
                return handleSellCommand((Player) sender, args[1]);

            case "setname":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore setname <apartment_id> <display_name>");
                    return true;
                }
                return handleSetNameCommand((Player) sender, args[1],
                        String.join(" ", Arrays.copyOfRange(args, 2, args.length)));

            case "setwelcome":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore setwelcome <apartment_id> <message>");
                    return true;
                }
                return handleSetWelcomeCommand((Player) sender, args[1],
                        String.join(" ", Arrays.copyOfRange(args, 2, args.length)));

            case "rate":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length != 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore rate <apartment_id> <rating>");
                    return true;
                }
                try {
                    double rating = Double.parseDouble(args[2]);
                    return handleRateCommand((Player) sender, args[1], rating);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid rating! Must be a number between 0 and 10");
                    return true;
                }

            case "confirm":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                return handleConfirmCommand((Player) sender);

            case "teleport":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.teleport")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to teleport!");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore teleport <apartment_id>");
                    return true;
                }
                return handleTeleportCommand((Player) sender, args[1], false);

            case "rent":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.rent")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to manage rent!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore rent <claim|info> <apartment_id>");
                    return true;
                }
                return handleRentCommand((Player) sender, args[1], args[2]);

            case "tax":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.tax")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to manage taxes!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore tax <pay|info> <apartment_id>");
                    return true;
                }
                return handleTaxCommand((Player) sender, args[1], args[2]);

            case "upgrade":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore upgrade <apartment_id>");
                    return true;
                }
                return handleUpgradeCommand((Player) sender, args[1]);

            case "list":
                return handleListCommand(sender, args.length > 1 ? args[1] : null);

            case "admin":
                if (!sender.hasPermission("apartmentcore.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use admin commands!");
                    return true;
                }
                if (args.length < 2) {
                    sendAdminHelp(sender);
                    return true;
                }
                return handleAdminCommand(sender, Arrays.copyOfRange(args, 1, args.length));

            case "help":
                sendHelp(sender);
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown command. Use /apartmentcore help");
                return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("apartmentcore")) {
            return null;
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Main commands
            List<String> commands = Arrays.asList(
                    "help", "version", "info", "buy", "sell", "teleport",
                    "rent", "tax", "upgrade", "list", "confirm", "rate",
                    "setname", "setwelcome", "stats", "setspawn", "setpreview",
                    "tour", "guestbook"
            );

            if (sender.hasPermission("apartmentcore.admin")) {
                commands = new ArrayList<>(commands);
                commands.add("admin");
            }

            String partial = args[0].toLowerCase();
            for (String cmd : commands) {
                if (cmd.startsWith(partial)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "buy":
                case "sell":
                case "teleport":
                case "upgrade":
                case "setname":
                case "setwelcome":
                case "rate":
                case "stats":
                case "setspawn":
                case "setpreview":
                    // Suggest apartment IDs
                    String partial = args[1].toLowerCase();
                    for (String id : apartments.keySet()) {
                        if (id.toLowerCase().startsWith(partial)) {
                            completions.add(id);
                        }
                    }
                    break;

                case "tour":
                    completions.add("start");
                    completions.add("end");
                    break;

                case "guestbook":
                    completions.add("view");
                    completions.add("sign");
                    completions.add("gift");
                    completions.add("claim");
                    break;

                case "rent":
                case "tax":
                    completions.add("claim");
                    completions.add("info");
                    break;

                case "list":
                    completions.add("all");
                    completions.add("sale");
                    completions.add("mine");
                    completions.add("top");
                    break;

                case "admin":
                    if (sender.hasPermission("apartmentcore.admin")) {
                        List<String> adminCmds = Arrays.asList(
                                "create", "remove", "set", "teleport",
                                "apartment_list", "reload", "backup", "restore"
                        );
                        String partialAdmin = args[1].toLowerCase();
                        for (String cmd : adminCmds) {
                            if (cmd.startsWith(partialAdmin)) {
                                completions.add(cmd);
                            }
                        }
                    }
                    break;

                case "info":
                    // Suggest apartment IDs for info
                    String partialInfo = args[1].toLowerCase();
                    for (String id : apartments.keySet()) {
                        if (id.toLowerCase().startsWith(partialInfo)) {
                            completions.add(id);
                        }
                    }
                    break;
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("tour") && args[1].equals("start")) {
                // Suggest apartment IDs for tour
                String partial = args[2].toLowerCase();
                for (String id : apartments.keySet()) {
                    if (id.toLowerCase().startsWith(partial)) {
                        completions.add(id);
                    }
                }
            } else if (subCommand.equals("guestbook")) {
                // Suggest apartment IDs
                String partial = args[2].toLowerCase();
                for (String id : apartments.keySet()) {
                    if (id.toLowerCase().startsWith(partial)) {
                        completions.add(id);
                    }
                }
            } else if (subCommand.equals("rent") || subCommand.equals("tax")) {
                String action = args[1].toLowerCase();
                if (action.equals("claim") || action.equals("info")) {
                    // Suggest apartment IDs
                    String partial = args[2].toLowerCase();
                    for (String id : apartments.keySet()) {
                        if (id.toLowerCase().startsWith(partial)) {
                            completions.add(id);
                        }
                    }
                }
            } else if (subCommand.equals("admin")) {
                String adminCmd = args[1].toLowerCase();

                switch (adminCmd) {
                    case "set":
                        completions.addAll(Arrays.asList("owner", "price", "tax", "tax_time", "level"));
                        break;
                    case "remove":
                    case "teleport":
                        // Suggest apartment IDs
                        String partial = args[2].toLowerCase();
                        for (String id : apartments.keySet()) {
                            if (id.toLowerCase().startsWith(partial)) {
                                completions.add(id);
                            }
                        }
                        break;
                    case "backup":
                        completions.add("create");
                        completions.add("list");
                        break;
                }
            } else if (subCommand.equals("rate")) {
                // Suggest rating values
                completions.addAll(Arrays.asList("10", "9", "8", "7", "6", "5", "4", "3", "2", "1", "0"));
            }
        } else if (args.length == 4) {
            if (args[0].toLowerCase().equals("admin") && args[1].toLowerCase().equals("set")) {
                // Suggest apartment IDs for admin set
                String partial = args[3].toLowerCase();
                for (String id : apartments.keySet()) {
                    if (id.toLowerCase().startsWith(partial)) {
                        completions.add(id);
                    }
                }
            }
        }

        return completions;
    }

    private boolean handleInfoCommand(CommandSender sender, String apartmentId) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            sender.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Apartment Info: " + apt.displayName + " ===");
        sender.sendMessage(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + apartmentId);
        sender.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE +
                (apt.owner != null ? Bukkit.getOfflinePlayer(apt.owner).getName() : "For Sale"));
        sender.sendMessage(ChatColor.YELLOW + "Price: " + ChatColor.WHITE + formatMoney(apt.price));
        sender.sendMessage(ChatColor.YELLOW + "Tax: " + ChatColor.WHITE + formatMoney(apt.tax) + " every " + apt.taxDays + " days");
        sender.sendMessage(ChatColor.YELLOW + "Level: " + ChatColor.WHITE + apt.level + "/5");
        sender.sendMessage(ChatColor.YELLOW + "Hourly Income: " + ChatColor.WHITE +
                formatMoney(levelConfigs.get(apt.level).minIncome) + " - " +
                formatMoney(levelConfigs.get(apt.level).maxIncome));

        // Show rating
        ApartmentRating rating = apartmentRatings.get(apartmentId);
        if (rating != null && rating.ratingCount > 0) {
            double avgRating = rating.getAverageRating();
            sender.sendMessage(ChatColor.YELLOW + "Rating: " + ChatColor.WHITE +
                    String.format("%.1f/10.0", avgRating) + " (" + rating.ratingCount + " reviews)");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Rating: " + ChatColor.GRAY + "Not rated yet");
        }

        sender.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.WHITE +
                (apt.inactive ? ChatColor.RED + "Inactive" : ChatColor.GREEN + "Active"));

        // Show timers
        sender.sendMessage(ChatColor.YELLOW + "Next Income: " + ChatColor.WHITE + getTimeUntilNextIncome());
        sender.sendMessage(ChatColor.YELLOW + "Next Tax Due: " + ChatColor.WHITE + getTimeUntilNextTax(apt));

        if (apt.owner != null && sender instanceof Player &&
                apt.owner.equals(((Player) sender).getUniqueId())) {
            sender.sendMessage(ChatColor.YELLOW + "Pending Income: " + ChatColor.WHITE + formatMoney(apt.pendingIncome));
            if (apt.penalty > 0) {
                sender.sendMessage(ChatColor.YELLOW + "Penalty: " + ChatColor.RED + formatMoney(apt.penalty));
            }
            if (apt.level < 5) {
                double upgradeCost = levelConfigs.get(apt.level + 1).upgradeCost;
                sender.sendMessage(ChatColor.YELLOW + "Upgrade Cost: " + ChatColor.WHITE + formatMoney(upgradeCost));
            }

            // Show guest book info
            GuestBook gb = guestBooks.get(apartmentId);
            if (gb != null) {
                int unreadMessages = gb.messages.size();
                int unclaimedGifts = gb.gifts.size();
                if (unreadMessages > 0 || unclaimedGifts > 0) {
                    sender.sendMessage(ChatColor.AQUA + "Guest Book: " +
                            unreadMessages + " messages, " + unclaimedGifts + " gifts waiting!");
                }
            }
        }

        return true;
    }

    private boolean handleStatsCommand(CommandSender sender, String apartmentId) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            sender.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        ApartmentStatistics stats = apartmentStats.computeIfAbsent(apartmentId,
                k -> new ApartmentStatistics());

        sender.sendMessage(ChatColor.GOLD + "=== Statistics: " + apt.displayName + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Total Visitors: " + ChatColor.WHITE + stats.totalVisitors);
        sender.sendMessage(ChatColor.YELLOW + "Unique Visitors: " + ChatColor.WHITE + stats.uniqueVisitors.size());
        sender.sendMessage(ChatColor.YELLOW + "Total Income Generated: " + ChatColor.WHITE + formatMoney(stats.totalIncome));
        sender.sendMessage(ChatColor.YELLOW + "Total Tax Paid: " + ChatColor.WHITE + formatMoney(stats.totalTaxPaid));
        sender.sendMessage(ChatColor.YELLOW + "Times Upgraded: " + ChatColor.WHITE + stats.timesUpgraded);
        sender.sendMessage(ChatColor.YELLOW + "Times Sold: " + ChatColor.WHITE + stats.timesSold);

        // Calculate age
        long ageMillis = System.currentTimeMillis() - stats.createdDate;
        long ageDays = ageMillis / (1000 * 60 * 60 * 24);
        sender.sendMessage(ChatColor.YELLOW + "Age: " + ChatColor.WHITE + ageDays + " days");

        // Last visit
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        sender.sendMessage(ChatColor.YELLOW + "Last Visit: " + ChatColor.WHITE +
                sdf.format(new Date(stats.lastVisitDate)));

        // Average income per day
        if (ageDays > 0) {
            double avgDaily = stats.totalIncome / ageDays;
            sender.sendMessage(ChatColor.YELLOW + "Avg Daily Income: " + ChatColor.WHITE + formatMoney(avgDaily));
        }

        return true;
    }

    private boolean handleSetSpawnCommand(Player player, String apartmentId) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return true;
        }

        apt.customSpawn = player.getLocation();
        saveApartments();

        player.sendMessage(ChatColor.GREEN + "Custom spawn location set for " + apt.displayName);
        player.sendMessage(ChatColor.YELLOW + "Players will now teleport to this exact location!");

        return true;
    }

    private boolean handleSetPreviewCommand(Player player, String apartmentId) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return true;
        }

        apt.previewLocation = player.getLocation();
        saveApartments();

        player.sendMessage(ChatColor.GREEN + "Preview location set for " + apt.displayName);
        player.sendMessage(ChatColor.YELLOW + "Virtual tours will start from this location!");

        return true;
    }

    private boolean handleTourCommand(Player player, String[] args) {
        String action = args[0];

        if (action.equals("end")) {
            if (activeTours.containsKey(player.getUniqueId())) {
                endVirtualTour(player);
                return true;
            } else {
                player.sendMessage(ChatColor.RED + "You are not in a virtual tour!");
                return true;
            }
        } else if (action.equals("start")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /apartmentcore tour start <apartment_id>");
                return true;
            }

            String apartmentId = args[1];
            Apartment apt = apartments.get(apartmentId);
            if (apt == null) {
                player.sendMessage(ChatColor.RED + "Apartment not found!");
                return true;
            }

            if (apt.owner != null) {
                player.sendMessage(ChatColor.RED + "This apartment is already owned! Tours are only for apartments for sale.");
                return true;
            }

            if (activeTours.containsKey(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You are already in a virtual tour! End it first.");
                return true;
            }

            startVirtualTour(player, apt);

            // Update statistics
            ApartmentStatistics stats = apartmentStats.computeIfAbsent(apartmentId,
                    k -> new ApartmentStatistics());
            stats.totalVisitors++;
            if (!stats.uniqueVisitors.contains(player.getUniqueId())) {
                stats.uniqueVisitors.add(player.getUniqueId());
            }
            stats.lastVisitDate = System.currentTimeMillis();
        }

        return true;
    }

    private boolean handleGuestBookCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /apartmentcore guestbook <view|sign|gift|claim> <apartment_id> [message]");
            return true;
        }

        String action = args[0];
        String apartmentId = args[1];

        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        GuestBook guestBook = guestBooks.computeIfAbsent(apartmentId, k -> new GuestBook());

        switch (action) {
            case "view":
                if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Only the owner can view the guest book!");
                    return true;
                }

                player.sendMessage(ChatColor.GOLD + "=== Guest Book: " + apt.displayName + " ===");

                if (guestBook.messages.isEmpty() && guestBook.gifts.isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "No messages or gifts yet!");
                } else {
                    // Show messages
                    if (!guestBook.messages.isEmpty()) {
                        player.sendMessage(ChatColor.YELLOW + "Messages:");
                        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
                        for (GuestMessage msg : guestBook.messages) {
                            String senderName = Bukkit.getOfflinePlayer(msg.sender).getName();
                            String date = sdf.format(new Date(msg.timestamp));
                            player.sendMessage(ChatColor.GRAY + "[" + date + "] " +
                                    ChatColor.WHITE + senderName + ": " + ChatColor.AQUA + msg.message);
                        }
                    }

                    // Show gifts
                    if (!guestBook.gifts.isEmpty()) {
                        player.sendMessage(ChatColor.YELLOW + "Gifts (use /apartmentcore guestbook claim <id> to claim):");
                        for (GuestGift gift : guestBook.gifts) {
                            String senderName = Bukkit.getOfflinePlayer(gift.sender).getName();
                            player.sendMessage(ChatColor.GREEN + "- From " + senderName + ": " +
                                    gift.item.getType() + " x" + gift.item.getAmount());
                            if (!gift.message.isEmpty()) {
                                player.sendMessage(ChatColor.GRAY + "  Message: " + gift.message);
                            }
                        }
                    }
                }
                break;

            case "sign":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /apartmentcore guestbook sign <apartment_id> <message>");
                    return true;
                }

                if (apt.owner != null && apt.owner.equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "You can't sign your own guest book!");
                    return true;
                }

                String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                if (message.length() > 100) {
                    player.sendMessage(ChatColor.RED + "Message too long! Maximum 100 characters.");
                    return true;
                }

                // Check if guest book is full
                if (guestBook.messages.size() >= maxGuestBookMessages) {
                    // Remove oldest message
                    guestBook.messages.remove(0);
                }

                guestBook.messages.add(new GuestMessage(player.getUniqueId(), message, System.currentTimeMillis()));
                saveGuestBooks();

                player.sendMessage(ChatColor.GREEN + "Message left in guest book!");

                // Notify owner if online
                if (apt.owner != null) {
                    Player owner = Bukkit.getPlayer(apt.owner);
                    if (owner != null && owner.isOnline()) {
                        owner.sendMessage(ChatColor.AQUA + player.getName() + " left a message in your apartment " +
                                apt.displayName + "'s guest book!");
                    }
                }
                break;

            case "gift":
                if (apt.owner != null && apt.owner.equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "You can't send gifts to yourself!");
                    return true;
                }

                ItemStack item = player.getInventory().getItemInMainHand();
                if (item == null || item.getType() == Material.AIR) {
                    player.sendMessage(ChatColor.RED + "You must hold an item to gift!");
                    return true;
                }

                String giftMessage = "";
                if (args.length > 2) {
                    giftMessage = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                }

                // Clone item and remove from player
                ItemStack giftItem = item.clone();
                player.getInventory().setItemInMainHand(null);

                guestBook.gifts.add(new GuestGift(player.getUniqueId(), giftItem, giftMessage, System.currentTimeMillis()));
                saveGuestBooks();

                player.sendMessage(ChatColor.GREEN + "Gift sent to apartment " + apt.displayName + "!");

                // Notify owner if online
                if (apt.owner != null) {
                    Player owner = Bukkit.getPlayer(apt.owner);
                    if (owner != null && owner.isOnline()) {
                        owner.sendMessage(ChatColor.GREEN + player.getName() + " sent you a gift at " +
                                apt.displayName + "!");
                    }
                }
                break;

            case "claim":
                if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Only the owner can claim gifts!");
                    return true;
                }

                if (guestBook.gifts.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "No gifts to claim!");
                    return true;
                }

                // Claim all gifts
                int claimed = 0;
                Iterator<GuestGift> it = guestBook.gifts.iterator();
                while (it.hasNext()) {
                    GuestGift gift = it.next();
                    if (player.getInventory().addItem(gift.item).isEmpty()) {
                        it.remove();
                        claimed++;
                    } else {
                        player.sendMessage(ChatColor.RED + "Inventory full! Could not claim all gifts.");
                        break;
                    }
                }

                if (claimed > 0) {
                    saveGuestBooks();
                    player.sendMessage(ChatColor.GREEN + "Claimed " + claimed + " gift(s)!");
                }
                break;

            default:
                player.sendMessage(ChatColor.RED + "Usage: /apartmentcore guestbook <view|sign|gift|claim> <apartment_id>");
                break;
        }

        return true;
    }

    // Continue with other handler methods...
    // [Previous handler methods remain the same: handleBuyCommand, handleSellCommand, etc.]

    private boolean handleBuyCommand(Player player, String apartmentId) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner != null) {
            player.sendMessage(ChatColor.RED + "This apartment is already owned!");
            return true;
        }

        if (!economy.has(player, apt.price)) {
            player.sendMessage(ChatColor.RED + "You don't have enough money! Need: " + formatMoney(apt.price));
            return true;
        }

        // Check apartment limit
        if (!player.hasPermission("apartmentcore.bypass.limit")) {
            int maxApartments = getConfig().getInt("limits.max-apartments-per-player", 5);
            if (maxApartments > 0) {
                long owned = apartments.values().stream()
                        .filter(a -> player.getUniqueId().equals(a.owner))
                        .count();
                if (owned >= maxApartments) {
                    player.sendMessage(ChatColor.RED + "You have reached the maximum number of apartments (" + maxApartments + ")!");
                    return true;
                }
            }
        }

        economy.withdrawPlayer(player, apt.price);
        apt.owner = player.getUniqueId();
        apt.lastTaxPayment = System.currentTimeMillis();
        apt.inactive = false;
        apt.penalty = 0;
        apt.inactiveSince = 0;

        // Update statistics
        ApartmentStatistics stats = apartmentStats.computeIfAbsent(apartmentId,
                k -> new ApartmentStatistics());
        stats.timesSold++;

        // Add player to WorldGuard region
        addPlayerToRegion(player, apt);

        saveApartments();
        saveStatistics();
        player.sendMessage(ChatColor.GREEN + "Successfully purchased apartment " + apt.displayName + " for " + formatMoney(apt.price));

        // Show welcome message if exists
        if (!apt.welcomeMessage.isEmpty()) {
            player.sendMessage(ChatColor.AQUA + apt.welcomeMessage);
        }

        log(player.getName() + " purchased apartment " + apartmentId);

        return true;
    }

    private boolean handleSellCommand(Player player, String apartmentId) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return true;
        }

        // Check for pending confirmation
        ConfirmationAction pending = pendingConfirmations.get(player.getUniqueId());
        if (pending == null || !pending.type.equals("sell") || !pending.data.equals(apartmentId)) {
            double sellPrice = apt.price * sellPercentage;
            player.sendMessage(ChatColor.YELLOW + "You will receive " + formatMoney(sellPrice) +
                    " for selling " + apt.displayName);
            player.sendMessage(ChatColor.YELLOW + "Type " + ChatColor.WHITE + "/apartmentcore confirm" +
                    ChatColor.YELLOW + " to confirm the sale.");

            pendingConfirmations.put(player.getUniqueId(),
                    new ConfirmationAction("sell", apartmentId, System.currentTimeMillis()));
            return true;
        }

        return true;
    }

    private boolean handleSetNameCommand(Player player, String apartmentId, String displayName) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return true;
        }

        // Validate display name length
        if (displayName.length() > 32) {
            player.sendMessage(ChatColor.RED + "Display name cannot be longer than 32 characters!");
            return true;
        }

        apt.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        saveApartments();

        player.sendMessage(ChatColor.GREEN + "Apartment display name set to: " + apt.displayName);

        return true;
    }

    private boolean handleSetWelcomeCommand(Player player, String apartmentId, String message) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return true;
        }

        // Validate message length
        if (message.length() > 100) {
            player.sendMessage(ChatColor.RED + "Welcome message cannot be longer than 100 characters!");
            return true;
        }

        if (message.equalsIgnoreCase("none") || message.equalsIgnoreCase("clear")) {
            apt.welcomeMessage = "";
            player.sendMessage(ChatColor.GREEN + "Welcome message cleared!");
        } else {
            apt.welcomeMessage = ChatColor.translateAlternateColorCodes('&', message);
            player.sendMessage(ChatColor.GREEN + "Welcome message set to: " + apt.welcomeMessage);
        }

        saveApartments();

        return true;
    }

    private boolean handleRateCommand(Player player, String apartmentId, double rating) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        // Check if apartment is active
        if (apt.owner == null || apt.inactive) {
            player.sendMessage(ChatColor.RED + "You can only rate active apartments!");
            return true;
        }

        // Validate rating range
        if (rating < 0 || rating > 10) {
            player.sendMessage(ChatColor.RED + "Rating must be between 0 and 10!");
            return true;
        }

        // Check cooldown (24 hours)
        UUID playerUuid = player.getUniqueId();
        Map<String, Long> playerCooldowns = playerRatingCooldowns.computeIfAbsent(playerUuid, k -> new HashMap<>());
        Long lastRating = playerCooldowns.get(apartmentId);

        if (lastRating != null) {
            long timeSinceLastRating = System.currentTimeMillis() - lastRating;
            if (timeSinceLastRating < 86400000) { // 24 hours in milliseconds
                long hoursLeft = (86400000 - timeSinceLastRating) / 3600000;
                player.sendMessage(ChatColor.RED + "You can rate this apartment again in " + hoursLeft + " hours!");
                return true;
            }
        }

        // Get or create rating entry
        ApartmentRating aptRating = apartmentRatings.computeIfAbsent(apartmentId, k -> new ApartmentRating());

        // Check if player has rated before
        Double oldRating = aptRating.raters.get(playerUuid);
        if (oldRating != null) {
            // Update existing rating
            aptRating.totalRating = aptRating.totalRating - oldRating + rating;
        } else {
            // New rating
            aptRating.totalRating += rating;
            aptRating.ratingCount++;
        }

        aptRating.raters.put(playerUuid, rating);
        playerCooldowns.put(apartmentId, System.currentTimeMillis());

        saveRatings();

        player.sendMessage(ChatColor.GREEN + "You rated " + apt.displayName + " " +
                String.format("%.1f", rating) + "/10.0!");
        player.sendMessage(ChatColor.YELLOW + "New average rating: " +
                String.format("%.1f", aptRating.getAverageRating()) + "/10.0");

        return true;
    }

    private boolean handleConfirmCommand(Player player) {
        ConfirmationAction action = pendingConfirmations.remove(player.getUniqueId());
        if (action == null) {
            player.sendMessage(ChatColor.RED + "You have no pending actions to confirm!");
            return true;
        }

        if (action.type.equals("sell")) {
            Apartment apt = apartments.get(action.data);
            if (apt == null || !player.getUniqueId().equals(apt.owner)) {
                player.sendMessage(ChatColor.RED + "Cannot complete the sale!");
                return true;
            }

            double sellPrice = apt.price * sellPercentage;
            economy.depositPlayer(player, sellPrice);

            // Remove player from WorldGuard region
            removePlayerFromRegion(player, apt);

            // Reset apartment
            apt.owner = null;
            apt.pendingIncome = 0;
            apt.inactive = false;
            apt.penalty = 0;
            apt.inactiveSince = 0;
            apt.customSpawn = null;
            apt.previewLocation = null;

            // Reset ratings and guest book
            apartmentRatings.remove(apt.id);
            guestBooks.remove(apt.id);

            // Update statistics
            ApartmentStatistics stats = apartmentStats.get(apt.id);
            if (stats != null) {
                stats.timesSold++;
            }

            saveApartments();
            saveRatings();
            saveGuestBooks();
            saveStatistics();
            player.sendMessage(ChatColor.GREEN + "Successfully sold " + apt.displayName + " for " + formatMoney(sellPrice));
            log(player.getName() + " sold apartment " + apt.id);
        }

        return true;
    }

    private boolean handleTeleportCommand(Player player, String apartmentId, boolean isAdmin) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (!isAdmin && (apt.owner == null || !apt.owner.equals(player.getUniqueId()))) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return true;
        }

        // Use custom spawn if available
        Location teleportLoc = apt.customSpawn != null ? apt.customSpawn : getApartmentDefaultLocation(apt);

        if (teleportLoc != null) {
            player.teleport(teleportLoc);
            player.sendMessage(ChatColor.GREEN + "Teleported to " + apt.displayName);

            // Show welcome message
            if (!apt.welcomeMessage.isEmpty()) {
                player.sendMessage(ChatColor.AQUA + apt.welcomeMessage);
            }

            // Update statistics
            ApartmentStatistics stats = apartmentStats.computeIfAbsent(apartmentId,
                    k -> new ApartmentStatistics());
            stats.totalVisitors++;
            stats.lastVisitDate = System.currentTimeMillis();
            if (!stats.uniqueVisitors.contains(player.getUniqueId())) {
                stats.uniqueVisitors.add(player.getUniqueId());
            }
        } else {
            player.sendMessage(ChatColor.RED + "Could not find teleport location!");
        }

        return true;
    }

    private boolean handleRentCommand(Player player, String action, String apartmentId) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return true;
        }

        switch (action.toLowerCase()) {
            case "claim":
                if (apt.pendingIncome <= 0) {
                    player.sendMessage(ChatColor.RED + "No income to claim!");
                    return true;
                }
                economy.depositPlayer(player, apt.pendingIncome);
                player.sendMessage(ChatColor.GREEN + "Claimed " + formatMoney(apt.pendingIncome) + " from " + apt.displayName);
                apt.pendingIncome = 0;
                lastRentClaimTime = System.currentTimeMillis();
                saveApartments();
                break;

            case "info":
                player.sendMessage(ChatColor.GOLD + "=== Rent Info: " + apt.displayName + " ===");
                player.sendMessage(ChatColor.YELLOW + "Pending Income: " + ChatColor.WHITE + formatMoney(apt.pendingIncome));
                player.sendMessage(ChatColor.YELLOW + "Hourly Income Range: " + ChatColor.WHITE +
                        formatMoney(levelConfigs.get(apt.level).minIncome) + " - " +
                        formatMoney(levelConfigs.get(apt.level).maxIncome));
                player.sendMessage(ChatColor.YELLOW + "Level: " + ChatColor.WHITE + apt.level + "/5");

                // Show timer
                player.sendMessage(ChatColor.YELLOW + "Next Income In: " + ChatColor.WHITE + getTimeUntilNextIncome());

                // Show time since last claim
                long timeSinceLastClaim = System.currentTimeMillis() - lastRentClaimTime;
                long minutesSince = timeSinceLastClaim / 60000;
                player.sendMessage(ChatColor.YELLOW + "Last claim: " + ChatColor.WHITE +
                        (minutesSince < 1 ? "Just now" : minutesSince + " minutes ago"));

                player.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.WHITE +
                        (apt.inactive ? ChatColor.RED + "Inactive (no income)" : ChatColor.GREEN + "Active"));
                break;

            default:
                player.sendMessage(ChatColor.RED + "Usage: /apartmentcore rent <claim|info> <apartment_id>");
        }

        return true;
    }

    private boolean handleTaxCommand(Player player, String action, String apartmentId) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return true;
        }

        switch (action.toLowerCase()) {
            case "pay":
                double totalDue = apt.tax + apt.penalty;
                if (totalDue <= 0) {
                    player.sendMessage(ChatColor.YELLOW + "No taxes due at this time!");
                    return true;
                }

                if (!economy.has(player, totalDue)) {
                    player.sendMessage(ChatColor.RED + "You don't have enough money! Need: " + formatMoney(totalDue));
                    return true;
                }

                economy.withdrawPlayer(player, totalDue);
                apt.lastTaxPayment = System.currentTimeMillis();
                apt.inactive = false;
                apt.penalty = 0;
                apt.inactiveSince = 0;

                // Update statistics
                ApartmentStatistics stats = apartmentStats.computeIfAbsent(apartmentId,
                        k -> new ApartmentStatistics());
                stats.totalTaxPaid += totalDue;

                saveApartments();
                saveStatistics();
                player.sendMessage(ChatColor.GREEN + "Paid " + formatMoney(totalDue) + " in taxes for " + apt.displayName);
                break;

            case "info":
                World world = getServer().getWorlds().get(0);
                long currentDay = world != null ? world.getFullTime() / 24000 : 0;
                long lastPaymentDay = apt.lastTaxPayment / (24000 * 50); // Convert to MC days
                long daysSincePayment = currentDay - lastPaymentDay;

                player.sendMessage(ChatColor.GOLD + "=== Tax Info: " + apt.displayName + " ===");
                player.sendMessage(ChatColor.YELLOW + "Tax Amount: " + ChatColor.WHITE + formatMoney(apt.tax));
                player.sendMessage(ChatColor.YELLOW + "Tax Period: " + ChatColor.WHITE + apt.taxDays + " days");
                player.sendMessage(ChatColor.YELLOW + "Days Since Payment: " + ChatColor.WHITE + daysSincePayment);
                player.sendMessage(ChatColor.YELLOW + "Next Tax Due: " + ChatColor.WHITE + getTimeUntilNextTax(apt));
                player.sendMessage(ChatColor.YELLOW + "Penalty: " + ChatColor.WHITE +
                        (apt.penalty > 0 ? ChatColor.RED + formatMoney(apt.penalty) : ChatColor.GREEN + "None"));
                player.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.WHITE +
                        (apt.inactive ? ChatColor.RED + "Inactive" : ChatColor.GREEN + "Active"));

                if (apt.inactive && apt.inactiveSince > 0) {
                    long inactiveDays = (currentDay * 24000 - apt.inactiveSince) / 24000;
                    player.sendMessage(ChatColor.YELLOW + "Inactive for: " + ChatColor.RED + inactiveDays + " days");
                    player.sendMessage(ChatColor.RED + "Warning: Apartment will be lost after " +
                            (inactiveGracePeriod - inactiveDays) + " more days!");
                }
                break;

            default:
                player.sendMessage(ChatColor.RED + "Usage: /apartmentcore tax <pay|info> <apartment_id>");
        }

        return true;
    }

    private boolean handleUpgradeCommand(Player player, String apartmentId) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return true;
        }

        if (apt.level >= 5) {
            player.sendMessage(ChatColor.RED + "This apartment is already at maximum level!");
            return true;
        }

        double upgradeCost = levelConfigs.get(apt.level + 1).upgradeCost;

        if (!economy.has(player, upgradeCost)) {
            player.sendMessage(ChatColor.RED + "You don't have enough money! Need: " + formatMoney(upgradeCost));
            return true;
        }

        economy.withdrawPlayer(player, upgradeCost);
        apt.level++;

        // Update statistics
        ApartmentStatistics stats = apartmentStats.computeIfAbsent(apartmentId,
                k -> new ApartmentStatistics());
        stats.timesUpgraded++;

        saveApartments();
        saveStatistics();

        player.sendMessage(ChatColor.GREEN + "Successfully upgraded " + apt.displayName + " to level " + apt.level);
        player.sendMessage(ChatColor.YELLOW + "New income range: " +
                formatMoney(levelConfigs.get(apt.level).minIncome) + " - " +
                formatMoney(levelConfigs.get(apt.level).maxIncome) + " per hour");

        return true;
    }

    private boolean handleListCommand(CommandSender sender, String filter) {
        List<Apartment> displayList;
        String title;

        if (filter == null || filter.equals("all")) {
            displayList = new ArrayList<>(apartments.values());
            title = "All Apartments";
        } else if (filter.equals("sale")) {
            displayList = apartments.values().stream()
                    .filter(a -> a.owner == null)
                    .collect(Collectors.toList());
            title = "Apartments For Sale";
        } else if (filter.equals("mine") && sender instanceof Player) {
            UUID playerUuid = ((Player) sender).getUniqueId();
            displayList = apartments.values().stream()
                    .filter(a -> playerUuid.equals(a.owner))
                    .collect(Collectors.toList());
            title = "Your Apartments";
        } else if (filter.equals("top")) {
            // Show top rated apartments
            displayList = apartments.values().stream()
                    .filter(a -> a.owner != null && !a.inactive)
                    .sorted((a1, a2) -> {
                        ApartmentRating r1 = apartmentRatings.get(a1.id);
                        ApartmentRating r2 = apartmentRatings.get(a2.id);
                        double rating1 = r1 != null ? r1.getAverageRating() : 0;
                        double rating2 = r2 != null ? r2.getAverageRating() : 0;
                        return Double.compare(rating2, rating1);
                    })
                    .limit(10)
                    .collect(Collectors.toList());
            title = "Top Rated Apartments";
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore list [all|sale|mine|top]");
            return true;
        }

        if (displayList.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No apartments found for filter: " + filter);
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== " + title + " ===");
        for (Apartment apt : displayList) {
            String owner = apt.owner != null ? Bukkit.getOfflinePlayer(apt.owner).getName() : "For Sale";
            String status = apt.inactive ? ChatColor.RED + "[INACTIVE]" : "";

            // Get rating
            ApartmentRating rating = apartmentRatings.get(apt.id);
            String ratingStr = rating != null && rating.ratingCount > 0 ?
                    String.format(" %.1f⭐", rating.getAverageRating()) : "";

            sender.sendMessage(ChatColor.YELLOW + apt.displayName + " (" + apt.id + "): " + ChatColor.WHITE +
                    "Owner: " + owner + ", Price: " + formatMoney(apt.price) +
                    ", L" + apt.level + ratingStr + " " + status);
        }

        return true;
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        String adminCmd = args[0].toLowerCase();

        switch (adminCmd) {
            case "create":
                if (args.length != 6) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin create <region> <id> <price> <tax> <tax_days>");
                    return true;
                }
                try {
                    return createApartment(sender, args[1], args[2],
                            Double.parseDouble(args[3]),
                            Double.parseDouble(args[4]),
                            Integer.parseInt(args[5]));
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid number format!");
                    return true;
                }

            case "remove":
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin remove <apartment_id>");
                    return true;
                }
                return removeApartment(sender, args[1]);

            case "set":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin set <property> <apartment_id> <value>");
                    return true;
                }
                return setApartmentProperty(sender, args[1], args[2], args[3]);

            case "teleport":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin teleport <apartment_id>");
                    return true;
                }
                return handleTeleportCommand((Player) sender, args[1], true);

            case "apartment_list":
                listApartments(sender);
                return true;

            case "backup":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin backup <create|list|restore>");
                    return true;
                }
                return handleBackupCommand(sender, args[1], args.length > 2 ? args[2] : null);

            case "reload":
                reloadConfig();
                loadConfiguration();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded!");
                return true;

            default:
                sendAdminHelp(sender);
                return true;
        }
    }

    private boolean handleBackupCommand(CommandSender sender, String action, String backupName) {
        switch (action.toLowerCase()) {
            case "create":
                createBackup("manual");
                sender.sendMessage(ChatColor.GREEN + "Backup created successfully!");
                return true;

            case "list":
                File[] backups = backupFolder.listFiles((dir, name) -> name.endsWith(".yml"));
                if (backups == null || backups.length == 0) {
                    sender.sendMessage(ChatColor.YELLOW + "No backups found!");
                    return true;
                }

                sender.sendMessage(ChatColor.GOLD + "=== Available Backups ===");
                Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());
                for (File backup : backups) {
                    long size = backup.length() / 1024;
                    Date modified = new Date(backup.lastModified());
                    sender.sendMessage(ChatColor.YELLOW + backup.getName() + ChatColor.WHITE +
                            " (" + size + "KB, " + modified + ")");
                }
                return true;

            case "restore":
                if (backupName == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin backup restore <backup_name>");
                    return true;
                }

                if (restoreBackup(backupName)) {
                    sender.sendMessage(ChatColor.GREEN + "Successfully restored from backup: " + backupName);
                    log("Admin " + sender.getName() + " restored backup: " + backupName);
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to restore backup! Check console for errors.");
                }
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin backup <create|list|restore>");
                return true;
        }
    }

    private boolean createApartment(CommandSender sender, String region, String id, double price, double tax, int taxDays) {
        if (apartments.containsKey(id)) {
            sender.sendMessage(ChatColor.RED + "Apartment with this ID already exists!");
            return true;
        }

        // Validate price and tax
        double maxPrice = getConfig().getDouble("limits.max-apartment-price", 1000000);
        double minPrice = getConfig().getDouble("limits.min-apartment-price", 100);

        if (price < minPrice || price > maxPrice) {
            sender.sendMessage(ChatColor.RED + "Price must be between " + formatMoney(minPrice) +
                    " and " + formatMoney(maxPrice));
            return true;
        }

        Player player = sender instanceof Player ? (Player) sender : null;
        String worldName = player != null ? player.getWorld().getName() : "world";

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "World not found!");
            return true;
        }

        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (regionManager == null || regionManager.getRegion(region) == null) {
            sender.sendMessage(ChatColor.RED + "Region not found!");
            return true;
        }

        Apartment apt = new Apartment(id, region, worldName, null, price, tax, taxDays, 1,
                System.currentTimeMillis(), 0, false, 0, 0, id, "", null, null);
        apartments.put(id, apt);

        // Initialize statistics
        ApartmentStatistics stats = new ApartmentStatistics();
        stats.createdDate = System.currentTimeMillis();
        apartmentStats.put(id, stats);

        saveApartments();
        saveStatistics();

        sender.sendMessage(ChatColor.GREEN + "Successfully created apartment " + id);
        log("Admin " + sender.getName() + " created apartment " + id);

        return true;
    }

    private boolean removeApartment(CommandSender sender, String apartmentId) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            sender.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        // Refund owner if exists
        if (apt.owner != null) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(apt.owner);
            double refund = apt.price * 0.5; // 50% refund
            economy.depositPlayer(owner, refund);

            if (owner.isOnline()) {
                owner.getPlayer().sendMessage(ChatColor.YELLOW + "Your apartment " + apt.displayName +
                        " was removed. You received a refund of " + formatMoney(refund));
            }
        }

        apartments.remove(apartmentId);
        apartmentRatings.remove(apartmentId);
        guestBooks.remove(apartmentId);
        apartmentStats.remove(apartmentId);
        saveApartments();
        saveRatings();
        saveGuestBooks();
        saveStatistics();

        sender.sendMessage(ChatColor.GREEN + "Successfully removed apartment " + apartmentId);
        log("Admin " + sender.getName() + " removed apartment " + apartmentId);

        return true;
    }

    private boolean setApartmentProperty(CommandSender sender, String property, String apartmentId, String value) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            sender.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        try {
            switch (property.toLowerCase()) {
                case "owner":
                    if (value.equals("none")) {
                        apt.owner = null;
                        sender.sendMessage(ChatColor.GREEN + "Removed owner from apartment");
                    } else {
                        Player newOwner = Bukkit.getPlayer(value);
                        if (newOwner == null) {
                            sender.sendMessage(ChatColor.RED + "Player not found!");
                            return true;
                        }
                        apt.owner = newOwner.getUniqueId();
                        sender.sendMessage(ChatColor.GREEN + "Set owner to " + value);
                    }
                    break;

                case "price":
                    apt.price = Double.parseDouble(value);
                    sender.sendMessage(ChatColor.GREEN + "Set price to " + formatMoney(apt.price));
                    break;

                case "tax":
                    apt.tax = Double.parseDouble(value);
                    sender.sendMessage(ChatColor.GREEN + "Set tax to " + formatMoney(apt.tax));
                    break;

                case "tax_time":
                    apt.taxDays = Integer.parseInt(value);
                    sender.sendMessage(ChatColor.GREEN + "Set tax time to " + value + " days");
                    break;

                case "type":
                case "level":
                    int level = Integer.parseInt(value);
                    if (level < 1 || level > 5) {
                        sender.sendMessage(ChatColor.RED + "Level must be between 1 and 5!");
                        return true;
                    }
                    apt.level = level;
                    sender.sendMessage(ChatColor.GREEN + "Set level to " + value);
                    break;

                default:
                    sender.sendMessage(ChatColor.RED + "Unknown property: " + property);
                    return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid value format!");
            return true;
        }

        saveApartments();
        return true;
    }

    private void listApartments(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== All Apartments ===");
        sender.sendMessage(ChatColor.GRAY + "Total: " + apartments.size());

        for (Apartment apt : apartments.values()) {
            String owner = apt.owner != null ? Bukkit.getOfflinePlayer(apt.owner).getName() : "For Sale";
            String status = apt.inactive ? ChatColor.RED + " [INACTIVE]" : ChatColor.GREEN + " [ACTIVE]";

            ApartmentRating rating = apartmentRatings.get(apt.id);
            String ratingStr = rating != null && rating.ratingCount > 0 ?
                    String.format(" (%.1f⭐)", rating.getAverageRating()) : "";

            sender.sendMessage(ChatColor.YELLOW + apt.displayName + " (" + apt.id + "): " + ChatColor.WHITE +
                    "Owner: " + owner + ", Price: " + formatMoney(apt.price) +
                    ", Tax: " + formatMoney(apt.tax) + "/" + apt.taxDays + "d" +
                    ", L" + apt.level + ratingStr + status);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== ApartmentCore Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore info [id]" + ChatColor.WHITE + " - View info");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore buy <id>" + ChatColor.WHITE + " - Buy apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore sell <id>" + ChatColor.WHITE + " - Sell apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore teleport <id>" + ChatColor.WHITE + " - Teleport to apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore rent <claim|info> <id>" + ChatColor.WHITE + " - Manage income");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore tax <pay|info> <id>" + ChatColor.WHITE + " - Manage taxes");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore upgrade <id>" + ChatColor.WHITE + " - Upgrade apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore setname <id> <name>" + ChatColor.WHITE + " - Set display name");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore setwelcome <id> <msg>" + ChatColor.WHITE + " - Set welcome message");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore setspawn <id>" + ChatColor.WHITE + " - Set custom spawn");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore setpreview <id>" + ChatColor.WHITE + " - Set preview location");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore rate <id> <0-10>" + ChatColor.WHITE + " - Rate apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore stats <id>" + ChatColor.WHITE + " - View statistics");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore tour <start|end> [id]" + ChatColor.WHITE + " - Virtual tour");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore guestbook <action> <id>" + ChatColor.WHITE + " - Guest book");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore list [all|sale|mine|top]" + ChatColor.WHITE + " - List apartments");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore confirm" + ChatColor.WHITE + " - Confirm action");
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Admin Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin create" + ChatColor.WHITE + " - Create apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin remove" + ChatColor.WHITE + " - Remove apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin set" + ChatColor.WHITE + " - Set apartment property");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin apartment_list" + ChatColor.WHITE + " - List all apartments");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin teleport" + ChatColor.WHITE + " - Teleport to any apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin backup <create|list|restore>" + ChatColor.WHITE + " - Manage backups");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin reload" + ChatColor.WHITE + " - Reload config");
    }

    /**
     * Add player to WorldGuard region
     */
    private void addPlayerToRegion(Player player, Apartment apt) {
        World world = Bukkit.getWorld(apt.worldName);
        if (world != null) {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(world));
            if (regionManager != null) {
                ProtectedRegion region = regionManager.getRegion(apt.regionName);
                if (region != null) {
                    region.getOwners().addPlayer(player.getUniqueId());
                }
            }
        }
    }

    /**
     * Remove player from WorldGuard region
     */
    private void removePlayerFromRegion(Player player, Apartment apt) {
        World world = Bukkit.getWorld(apt.worldName);
        if (world != null) {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(world));
            if (regionManager != null) {
                ProtectedRegion region = regionManager.getRegion(apt.regionName);
                if (region != null) {
                    region.getOwners().removePlayer(player.getUniqueId());
                }
            }
        }
    }

    /**
     * Format money display
     */
    private String formatMoney(double amount) {
        return currencySymbol + String.format("%.2f", amount);
    }

    /**
     * Log message with prefix
     */
    private void log(String message) {
        getLogger().info(message);
    }

    /**
     * Debug message (only if debug mode enabled)
     */
    private void debug(String message) {
        if (debugMode) {
            getLogger().log(Level.INFO, "[DEBUG] " + message);
        }
    }

    /**
     * Apartment data class
     */
    private class Apartment {
        String id;
        String regionName;
        String worldName;
        UUID owner;
        double price;
        double tax;
        int taxDays;
        int level;
        long lastTaxPayment;
        double pendingIncome;
        boolean inactive;
        double penalty;
        long inactiveSince;
        String displayName;
        String welcomeMessage;
        Location customSpawn;
        Location previewLocation;
        private long lastTaxCheckDay;

        Apartment(String id, String regionName, String worldName, UUID owner, double price,
                  double tax, int taxDays, int level, long lastTaxPayment, double pendingIncome,
                  boolean inactive, double penalty, long inactiveSince, String displayName,
                  String welcomeMessage, Location customSpawn, Location previewLocation) {
            this.id = id;
            this.regionName = regionName;
            this.worldName = worldName;
            this.owner = owner;
            this.price = price;
            this.tax = tax;
            this.taxDays = taxDays;
            this.level = level;
            this.lastTaxPayment = lastTaxPayment;
            this.pendingIncome = pendingIncome;
            this.inactive = inactive;
            this.penalty = penalty;
            this.inactiveSince = inactiveSince;
            this.displayName = displayName != null ? displayName : id;
            this.welcomeMessage = welcomeMessage != null ? welcomeMessage : "";
            this.customSpawn = customSpawn;
            this.previewLocation = previewLocation;
            this.lastTaxCheckDay = 0;
        }

        /**
         * Get hourly income based on level
         */
        double getHourlyIncome() {
            LevelConfig config = levelConfigs.get(level);
            if (config == null) {
                return 10; // Default fallback
            }
            return config.minIncome + Math.random() * (config.maxIncome - config.minIncome);
        }

        /**
         * Process daily tax
         */
        void processDailyTax(Economy econ, long daysPassed) {
            if (owner == null) return;

            // Check if tax period has passed
            if (lastTaxCheckDay == 0) {
                lastTaxCheckDay = lastMinecraftDay;
            }

            long daysSinceLastCheck = lastMinecraftDay - lastTaxCheckDay;

            if (daysSinceLastCheck >= taxDays) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(owner);

                if (player.isOnline() && player.getPlayer().hasPermission("apartmentcore.bypass.tax")) {
                    lastTaxCheckDay = lastMinecraftDay;
                    debug("Player " + player.getName() + " bypassed tax for apartment " + id);
                    return;
                }

                if (econ.has(player, tax)) {
                    econ.withdrawPlayer(player, tax);
                    lastTaxCheckDay = lastMinecraftDay;
                    inactive = false;
                    penalty = 0;
                    inactiveSince = 0;

                    // Update statistics
                    ApartmentStatistics stats = apartmentStats.get(id);
                    if (stats != null) {
                        stats.totalTaxPaid += tax;
                    }

                    debug("Auto-collected tax from " + player.getName() + " for apartment " + id);

                    if (player.isOnline()) {
                        player.getPlayer().sendMessage(ChatColor.GREEN +
                                "Tax of " + formatMoney(tax) + " collected for " + displayName);
                    }
                } else {
                    if (!inactive) {
                        inactive = true;
                        inactiveSince = lastMinecraftDay * 24000; // Convert to ticks
                        debug("Apartment " + id + " is now inactive due to unpaid taxes");

                        if (player.isOnline()) {
                            player.getPlayer().sendMessage(ChatColor.RED +
                                    "Your apartment " + displayName + " is now INACTIVE due to unpaid taxes!");
                        }
                    }

                    // Add daily penalty
                    penalty += price * penaltyPercentage;
                    debug("Added penalty of " + formatMoney(price * penaltyPercentage) + " to apartment " + id);

                    // Check if grace period expired
                    long inactiveDays = (lastMinecraftDay * 24000 - inactiveSince) / 24000;
                    if (inactiveDays >= inactiveGracePeriod) {
                        // Remove ownership
                        if (player.isOnline()) {
                            player.getPlayer().sendMessage(ChatColor.DARK_RED +
                                    "You have lost apartment " + displayName + " due to extended non-payment!");
                        }

                        owner = null;
                        inactive = false;
                        penalty = 0;
                        pendingIncome = 0;
                        inactiveSince = 0;
                        lastTaxCheckDay = 0;
                        customSpawn = null;
                        previewLocation = null;

                        // Reset ratings and guest book
                        apartmentRatings.remove(id);
                        guestBooks.remove(id);

                        debug("Apartment " + id + " ownership removed due to extended non-payment");
                    }
                }
            }
        }
    }

    /**
     * Guest book class
     */
    private static class GuestBook {
        List<GuestMessage> messages = new ArrayList<>();
        List<GuestGift> gifts = new ArrayList<>();
    }

    /**
     * Guest message class
     */
    private static class GuestMessage {
        UUID sender;
        String message;
        long timestamp;

        GuestMessage(UUID sender, String message, long timestamp) {
            this.sender = sender;
            this.message = message;
            this.timestamp = timestamp;
        }
    }

    /**
     * Guest gift class
     */
    private static class GuestGift {
        UUID sender;
        ItemStack item;
        String message;
        long timestamp;

        GuestGift(UUID sender, ItemStack item, String message, long timestamp) {
            this.sender = sender;
            this.item = item;
            this.message = message;
            this.timestamp = timestamp;
        }
    }

    /**
     * Apartment statistics class
     */
    private static class ApartmentStatistics {
        int totalVisitors = 0;
        Set<UUID> uniqueVisitors = new HashSet<>();
        double totalIncome = 0;
        double totalTaxPaid = 0;
        int timesUpgraded = 0;
        int timesSold = 0;
        long createdDate = System.currentTimeMillis();
        long lastVisitDate = System.currentTimeMillis();
    }

    /**
     * Virtual tour class
     */
    private static class VirtualTour {
        Player player;
        String apartmentId;
        Location originalLocation;
        GameMode originalGameMode;
        long startTime;

        VirtualTour(Player player, String apartmentId, Location originalLocation,
                    GameMode originalGameMode, long startTime) {
            this.player = player;
            this.apartmentId = apartmentId;
            this.originalLocation = originalLocation;
            this.originalGameMode = originalGameMode;
            this.startTime = startTime;
        }
    }

    /**
     * Apartment rating class
     */
    private static class ApartmentRating {
        double totalRating = 0;
        int ratingCount = 0;
        Map<UUID, Double> raters = new HashMap<>();

        double getAverageRating() {
            return ratingCount > 0 ? totalRating / ratingCount : 0;
        }
    }

    /**
     * Level configuration class
     */
    private static class LevelConfig {
        final double minIncome;
        final double maxIncome;
        final double upgradeCost;

        LevelConfig(double minIncome, double maxIncome, double upgradeCost) {
            this.minIncome = minIncome;
            this.maxIncome = maxIncome;
            this.upgradeCost = upgradeCost;
        }
    }

    /**
     * Confirmation action class
     */
    private static class ConfirmationAction {
        final String type;
        final String data;
        final long timestamp;

        ConfirmationAction(String type, String data, long timestamp) {
            this.type = type;
            this.data = data;
            this.timestamp = timestamp;
        }
    }

    /**
     * PlaceholderAPI Expansion
     */
    private class ApartmentPlaceholder extends PlaceholderExpansion {
        @Override
        public @NotNull String getIdentifier() {
            return "apartmentcore";
        }

        @Override
        public @NotNull String getAuthor() {
            return "Aithor";
        }

        @Override
        public @NotNull String getVersion() {
            return "1.2.5";
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, @NotNull String params) {
            if (player == null) return "";

            // Handle player-specific placeholders
            if (params.equals("owned_count")) {
                return String.valueOf(apartments.values().stream()
                        .filter(a -> player.getUniqueId().equals(a.owner))
                        .count());
            }

            if (params.equals("total_income")) {
                return formatMoney(apartments.values().stream()
                        .filter(a -> player.getUniqueId().equals(a.owner))
                        .mapToDouble(a -> a.pendingIncome)
                        .sum());
            }

            if (params.equals("last_rent_claim")) {
                long timeSince = System.currentTimeMillis() - lastRentClaimTime;
                long minutes = timeSince / 60000;
                return minutes < 1 ? "Just now" : minutes + " minutes ago";
            }

            if (params.equals("next_income_time")) {
                return getTimeUntilNextIncome();
            }

            if (params.equals("server_uptime")) {
                long uptime = System.currentTimeMillis() - serverStartTime;
                long hours = uptime / 3600000;
                long minutes = (uptime % 3600000) / 60000;
                return hours + "h " + minutes + "m";
            }

            // Handle apartment-specific placeholders
            String[] parts = params.split("_");
            if (parts.length < 2) return "";

            String apartmentId = parts[0];
            String info = parts[1];

            Apartment apt = apartments.get(apartmentId);
            if (apt == null) return "N/A";

            switch (info) {
                case "owner":
                    return apt.owner != null ? Bukkit.getOfflinePlayer(apt.owner).getName() : "For Sale";
                case "displayname":
                    return apt.displayName;
                case "price":
                    return formatMoney(apt.price);
                case "tax":
                    return formatMoney(apt.tax);
                case "level":
                    return String.valueOf(apt.level);
                case "income":
                    return formatMoney(apt.pendingIncome);
                case "status":
                    return apt.inactive ? "Inactive" : "Active";
                case "rating":
                    ApartmentRating rating = apartmentRatings.get(apartmentId);
                    return rating != null && rating.ratingCount > 0 ?
                            String.format("%.1f", rating.getAverageRating()) : "N/A";
                case "welcome":
                    return apt.welcomeMessage;
                case "nexttax":
                    return getTimeUntilNextTax(apt);
                case "visitors":
                    ApartmentStatistics stats = apartmentStats.get(apartmentId);
                    return stats != null ? String.valueOf(stats.totalVisitors) : "0";
                case "uniquevisitors":
                    ApartmentStatistics stats2 = apartmentStats.get(apartmentId);
                    return stats2 != null ? String.valueOf(stats2.uniqueVisitors.size()) : "0";
                case "totalincome":
                    ApartmentStatistics stats3 = apartmentStats.get(apartmentId);
                    return stats3 != null ? formatMoney(stats3.totalIncome) : "$0";
                case "totaltax":
                    ApartmentStatistics stats4 = apartmentStats.get(apartmentId);
                    return stats4 != null ? formatMoney(stats4.totalTaxPaid) : "$0";
                case "upgrades":
                    ApartmentStatistics stats5 = apartmentStats.get(apartmentId);
                    return stats5 != null ? String.valueOf(stats5.timesUpgraded) : "0";
                case "sold":
                    ApartmentStatistics stats6 = apartmentStats.get(apartmentId);
                    return stats6 != null ? String.valueOf(stats6.timesSold) : "0";
                case "guestmessages":
                    GuestBook gb = guestBooks.get(apartmentId);
                    return gb != null ? String.valueOf(gb.messages.size()) : "0";
                case "guestgifts":
                    GuestBook gb2 = guestBooks.get(apartmentId);
                    return gb2 != null ? String.valueOf(gb2.gifts.size()) : "0";
                default:
                    return "";
            }
        }
    }
}