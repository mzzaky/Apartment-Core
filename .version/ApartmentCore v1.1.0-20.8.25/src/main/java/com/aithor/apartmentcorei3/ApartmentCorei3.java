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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Main plugin class for ApartmentCore
 * Manages apartments in Minecraft with WorldGuard regions
 * @author Aithor
 * @version 1.1.0
 */
public class ApartmentCorei3 extends JavaPlugin {

    private Economy economy;
    private WorldGuardPlugin worldGuard;
    private Map<String, Apartment> apartments;
    private Map<UUID, Long> commandCooldowns;
    private Map<UUID, ConfirmationAction> pendingConfirmations;
    private FileConfiguration dataConfig;
    private File dataFile;
    private long lastMinecraftDay = 0;

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
    private Map<Integer, LevelConfig> levelConfigs;

    @Override
    public void onEnable() {
        // Initialize collections
        apartments = new ConcurrentHashMap<>();
        commandCooldowns = new ConcurrentHashMap<>();
        pendingConfirmations = new ConcurrentHashMap<>();
        levelConfigs = new HashMap<>();

        // Save default config
        saveDefaultConfig();
        loadConfiguration();

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

        // Start tasks
        startIncomeTask();
        startTaxTask();
        startConfirmationCleanupTask();
        if (autoSaveEnabled) {
            startAutoSaveTask();
        }

        log("ApartmentCore v1.1.0 enabled successfully!");
        log("Loaded " + apartments.size() + " apartments");
    }

    @Override
    public void onDisable() {
        // Cancel all tasks
        getServer().getScheduler().cancelTasks(this);

        // Only save if data is properly initialized
        if (dataConfig != null && apartments != null) {
            saveApartments();
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
                        aptSection.getLong("inactive-since", 0)
                );

                apartments.put(id, apt);
            } catch (Exception e) {
                getLogger().warning("Failed to load apartment " + id + ": " + e.getMessage());
            }
        }

        debug("Loaded " + apartments.size() + " apartments from storage");
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
        }

        try {
            dataConfig.save(dataFile);
            debug("Saved " + apartments.size() + " apartments to storage");
        } catch (IOException e) {
            getLogger().severe("Could not save apartments: " + e.getMessage());
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
                for (Apartment apt : apartments.values()) {
                    if (apt.owner != null && !apt.inactive) {
                        double income = apt.getHourlyIncome();
                        apt.pendingIncome += income;
                        debug("Generated " + formatMoney(income) + " income for apartment " + apt.id);
                    }
                }
            }
        }.runTaskTimer(this, 1000L, 1000L);
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
                log("Auto-saved apartment data");
            }
        }.runTaskTimer(this, autoSaveInterval * 60 * 20L, autoSaveInterval * 60 * 20L);
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
            sender.sendMessage(ChatColor.GOLD + "=== ApartmentCore v1.1.0 ===");
            sender.sendMessage(ChatColor.YELLOW + "Author: " + ChatColor.WHITE + "Aithor");
            sender.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/apartmentcore help" + ChatColor.YELLOW + " for commands");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "version":
                sender.sendMessage(ChatColor.GREEN + "ApartmentCore version 1.1.0");
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

    private boolean handleInfoCommand(CommandSender sender, String apartmentId) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            sender.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Apartment Info: " + apartmentId + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE +
                (apt.owner != null ? Bukkit.getOfflinePlayer(apt.owner).getName() : "For Sale"));
        sender.sendMessage(ChatColor.YELLOW + "Price: " + ChatColor.WHITE + formatMoney(apt.price));
        sender.sendMessage(ChatColor.YELLOW + "Tax: " + ChatColor.WHITE + formatMoney(apt.tax) + " every " + apt.taxDays + " days");
        sender.sendMessage(ChatColor.YELLOW + "Level: " + ChatColor.WHITE + apt.level + "/5");
        sender.sendMessage(ChatColor.YELLOW + "Hourly Income: " + ChatColor.WHITE +
                formatMoney(levelConfigs.get(apt.level).minIncome) + " - " +
                formatMoney(levelConfigs.get(apt.level).maxIncome));
        sender.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.WHITE +
                (apt.inactive ? ChatColor.RED + "Inactive" : ChatColor.GREEN + "Active"));

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
        }

        return true;
    }

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

        // Add player to WorldGuard region
        addPlayerToRegion(player, apt);

        saveApartments();
        player.sendMessage(ChatColor.GREEN + "Successfully purchased apartment " + apartmentId + " for " + formatMoney(apt.price));
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
                    " for selling this apartment.");
            player.sendMessage(ChatColor.YELLOW + "Type " + ChatColor.WHITE + "/apartmentcore confirm" +
                    ChatColor.YELLOW + " to confirm the sale.");

            pendingConfirmations.put(player.getUniqueId(),
                    new ConfirmationAction("sell", apartmentId, System.currentTimeMillis()));
            return true;
        }

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

            apt.owner = null;
            apt.pendingIncome = 0;
            apt.inactive = false;
            apt.penalty = 0;
            apt.inactiveSince = 0;

            saveApartments();
            player.sendMessage(ChatColor.GREEN + "Successfully sold apartment " + apt.id + " for " + formatMoney(sellPrice));
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

        World world = Bukkit.getWorld(apt.worldName);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "World not found!");
            return true;
        }

        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (regionManager != null) {
            ProtectedRegion region = regionManager.getRegion(apt.regionName);
            if (region != null) {
                BlockVector3 min = region.getMinimumPoint();
                BlockVector3 max = region.getMaximumPoint();

                // Calculate center of region
                double x = (min.getX() + max.getX()) / 2.0;
                double z = (min.getZ() + max.getZ()) / 2.0;
                double y = min.getY();

                // Find safe location
                Location loc = new Location(world, x, y, z);
                loc = world.getHighestBlockAt((int)x, (int)z).getLocation().add(0, 1, 0);

                player.teleport(loc);
                player.sendMessage(ChatColor.GREEN + "Teleported to apartment " + apartmentId);
            } else {
                player.sendMessage(ChatColor.RED + "Region not found!");
            }
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
                player.sendMessage(ChatColor.GREEN + "Claimed " + formatMoney(apt.pendingIncome) + " from apartment " + apartmentId);
                apt.pendingIncome = 0;
                saveApartments();
                break;

            case "info":
                player.sendMessage(ChatColor.GOLD + "=== Rent Info: " + apartmentId + " ===");
                player.sendMessage(ChatColor.YELLOW + "Pending Income: " + ChatColor.WHITE + formatMoney(apt.pendingIncome));
                player.sendMessage(ChatColor.YELLOW + "Hourly Income Range: " + ChatColor.WHITE +
                        formatMoney(levelConfigs.get(apt.level).minIncome) + " - " +
                        formatMoney(levelConfigs.get(apt.level).maxIncome));
                player.sendMessage(ChatColor.YELLOW + "Level: " + ChatColor.WHITE + apt.level + "/5");
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
                saveApartments();
                player.sendMessage(ChatColor.GREEN + "Paid " + formatMoney(totalDue) + " in taxes for apartment " + apartmentId);
                break;

            case "info":
                World world = getServer().getWorlds().get(0);
                long currentDay = world != null ? world.getFullTime() / 24000 : 0;
                long lastPaymentDay = apt.lastTaxPayment / (24000 * 50); // Convert to MC days
                long daysSincePayment = currentDay - lastPaymentDay;

                player.sendMessage(ChatColor.GOLD + "=== Tax Info: " + apartmentId + " ===");
                player.sendMessage(ChatColor.YELLOW + "Tax Amount: " + ChatColor.WHITE + formatMoney(apt.tax));
                player.sendMessage(ChatColor.YELLOW + "Tax Period: " + ChatColor.WHITE + apt.taxDays + " days");
                player.sendMessage(ChatColor.YELLOW + "Days Since Payment: " + ChatColor.WHITE + daysSincePayment);
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
        saveApartments();

        player.sendMessage(ChatColor.GREEN + "Successfully upgraded apartment " + apartmentId + " to level " + apt.level);
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
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore list [all|sale|mine]");
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
            sender.sendMessage(ChatColor.YELLOW + apt.id + ": " + ChatColor.WHITE +
                    "Owner: " + owner + ", Price: " + formatMoney(apt.price) +
                    ", Level: " + apt.level + " " + status);
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
                System.currentTimeMillis(), 0, false, 0, 0);
        apartments.put(id, apt);
        saveApartments();

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
                owner.getPlayer().sendMessage(ChatColor.YELLOW + "Your apartment " + apartmentId +
                        " was removed. You received a refund of " + formatMoney(refund));
            }
        }

        apartments.remove(apartmentId);
        saveApartments();

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
            sender.sendMessage(ChatColor.YELLOW + apt.id + ": " + ChatColor.WHITE +
                    "Owner: " + owner + ", Price: " + formatMoney(apt.price) +
                    ", Tax: " + formatMoney(apt.tax) + "/" + apt.taxDays + "d" +
                    ", Level: " + apt.level + status);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== ApartmentCore Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore info [id]" + ChatColor.WHITE + " - View info");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore buy <id>" + ChatColor.WHITE + " - Buy apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore sell <id>" + ChatColor.WHITE + " - Sell apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore teleport <id>" + ChatColor.WHITE + " - Teleport to apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore rent claim <id>" + ChatColor.WHITE + " - Claim income");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore rent info <id>" + ChatColor.WHITE + " - View rent info");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore tax pay <id>" + ChatColor.WHITE + " - Pay taxes");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore tax info <id>" + ChatColor.WHITE + " - View tax info");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore upgrade <id>" + ChatColor.WHITE + " - Upgrade apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore list [all|sale|mine]" + ChatColor.WHITE + " - List apartments");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore confirm" + ChatColor.WHITE + " - Confirm action");
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Admin Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin create" + ChatColor.WHITE + " - Create apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin remove" + ChatColor.WHITE + " - Remove apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin set" + ChatColor.WHITE + " - Set apartment property");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin apartment_list" + ChatColor.WHITE + " - List all apartments");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin teleport" + ChatColor.WHITE + " - Teleport to any apartment");
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
        private long lastTaxCheckDay;

        Apartment(String id, String regionName, String worldName, UUID owner, double price,
                  double tax, int taxDays, int level, long lastTaxPayment, double pendingIncome,
                  boolean inactive, double penalty, long inactiveSince) {
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
                    debug("Auto-collected tax from " + player.getName() + " for apartment " + id);

                    if (player.isOnline()) {
                        player.getPlayer().sendMessage(ChatColor.GREEN +
                                "Tax of " + formatMoney(tax) + " collected for apartment " + id);
                    }
                } else {
                    if (!inactive) {
                        inactive = true;
                        inactiveSince = lastMinecraftDay * 24000; // Convert to ticks
                        debug("Apartment " + id + " is now inactive due to unpaid taxes");

                        if (player.isOnline()) {
                            player.getPlayer().sendMessage(ChatColor.RED +
                                    "Your apartment " + id + " is now INACTIVE due to unpaid taxes!");
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
                                    "You have lost apartment " + id + " due to extended non-payment!");
                        }

                        owner = null;
                        inactive = false;
                        penalty = 0;
                        pendingIncome = 0;
                        inactiveSince = 0;
                        lastTaxCheckDay = 0;
                        debug("Apartment " + id + " ownership removed due to extended non-payment");
                    }
                }
            }
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
            return "1.1.0";
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
                default:
                    return "";
            }
        }
    }
}