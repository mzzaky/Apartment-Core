package com.aithor.apartmentcorei3;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
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

/**
 * Main plugin class for ApartmentCore
 * Manages apartments in Minecraft with WorldGuard regions
 * @author Aithor
 * @version 1.0.0
 */
public class ApartmentCorei3 extends JavaPlugin {

    private Economy economy;
    private WorldGuardPlugin worldGuard;
    private Map<String, Apartment> apartments;
    private Map<UUID, Double> pendingIncome;
    private FileConfiguration dataConfig;
    private File dataFile;
    private long lastTaxCheckTime = 0;

    // Configuration values
    private boolean debugMode;
    private boolean useMySQL;
    private String currencySymbol;
    private boolean autoSaveEnabled;
    private int autoSaveInterval;

    @Override
    public void onEnable() {
        // Initialize collections
        apartments = new ConcurrentHashMap<>();
        pendingIncome = new ConcurrentHashMap<>();

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

        // Register PlaceholderAPI expansion
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ApartmentPlaceholder().register();
            log("PlaceholderAPI expansion registered successfully");
        }

        // Start tasks
        startIncomeTask();
        startTaxTask();
        if (autoSaveEnabled) {
            startAutoSaveTask();
        }

        log("ApartmentCore v1.0.0 enabled successfully!");
    }

    @Override
    public void onDisable() {
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
                    aptSection.getDouble("penalty", 0)
            );

            apartments.put(id, apt);
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
                        debug("Generated " + income + " income for apartment " + apt.id);
                    }
                }
            }
        }.runTaskTimer(this, 1000L, 1000L);
    }

    /**
     * Start tax collection task
     */
    private void startTaxTask() {
        // Check every Minecraft day (24000 ticks = 20 minutes real time)
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = getServer().getWorld("world").getFullTime();
                long daysPassed = (currentTime - lastTaxCheckTime) / 24000;

                if (daysPassed >= 1) {
                    for (Apartment apt : apartments.values()) {
                        if (apt.owner != null) {
                            apt.checkTaxPayment(economy);
                        }
                    }
                    lastTaxCheckTime = currentTime;
                    saveApartments();
                }
            }
        }.runTaskTimer(this, 24000L, 24000L);
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

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("apartmentcore")) {
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "=== ApartmentCore v1.0.0 ===");
            sender.sendMessage(ChatColor.YELLOW + "Author: " + ChatColor.WHITE + "Aithor");
            sender.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/apartmentcore help" + ChatColor.YELLOW + " for commands");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "version":
                sender.sendMessage(ChatColor.GREEN + "ApartmentCore version 1.0.0");
                return true;

            case "info":
                if (args.length == 1) {
                    sender.sendMessage(ChatColor.GOLD + "=== ApartmentCore Info ===");
                    sender.sendMessage(ChatColor.YELLOW + "Total Apartments: " + ChatColor.WHITE + apartments.size());
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
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore sell <apartment_id>");
                    return true;
                }
                return handleSellCommand((Player) sender, args[1]);

            case "teleport":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
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
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore tax <pay|info> <apartment_id>");
                    return true;
                }
                return handleTaxCommand((Player) sender, args[1], args[2]);

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
        sender.sendMessage(ChatColor.YELLOW + "Price: " + ChatColor.WHITE + currencySymbol + apt.price);
        sender.sendMessage(ChatColor.YELLOW + "Tax: " + ChatColor.WHITE + currencySymbol + apt.tax + " every " + apt.taxDays + " days");
        sender.sendMessage(ChatColor.YELLOW + "Level: " + ChatColor.WHITE + apt.level);
        sender.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.WHITE + (apt.inactive ? "Inactive" : "Active"));
        if (apt.penalty > 0) {
            sender.sendMessage(ChatColor.YELLOW + "Penalty: " + ChatColor.RED + currencySymbol + apt.penalty);
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
            player.sendMessage(ChatColor.RED + "You don't have enough money! Need: " + currencySymbol + apt.price);
            return true;
        }

        economy.withdrawPlayer(player, apt.price);
        apt.owner = player.getUniqueId();
        apt.lastTaxPayment = System.currentTimeMillis();
        apt.inactive = false;
        apt.penalty = 0;

        // Add player to WorldGuard region
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

        saveApartments();
        player.sendMessage(ChatColor.GREEN + "Successfully purchased apartment " + apartmentId + " for " + currencySymbol + apt.price);
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

        double sellPrice = apt.price * 0.7; // 70% of original price
        economy.depositPlayer(player, sellPrice);

        // Remove player from WorldGuard region
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

        apt.owner = null;
        apt.pendingIncome = 0;
        apt.inactive = false;
        apt.penalty = 0;

        saveApartments();
        player.sendMessage(ChatColor.GREEN + "Successfully sold apartment " + apartmentId + " for " + currencySymbol + sellPrice);
        log(player.getName() + " sold apartment " + apartmentId);

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
                Location loc = new Location(world,
                        region.getMinimumPoint().getX(),
                        region.getMinimumPoint().getY(),
                        region.getMinimumPoint().getZ()
                );
                player.teleport(loc);
                player.sendMessage(ChatColor.GREEN + "Teleported to apartment " + apartmentId);
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
                player.sendMessage(ChatColor.GREEN + "Claimed " + currencySymbol + apt.pendingIncome + " from apartment " + apartmentId);
                apt.pendingIncome = 0;
                saveApartments();
                break;

            case "info":
                player.sendMessage(ChatColor.GOLD + "=== Rent Info: " + apartmentId + " ===");
                player.sendMessage(ChatColor.YELLOW + "Pending Income: " + ChatColor.WHITE + currencySymbol + apt.pendingIncome);
                player.sendMessage(ChatColor.YELLOW + "Hourly Income: " + ChatColor.WHITE + currencySymbol + apt.getHourlyIncome());
                player.sendMessage(ChatColor.YELLOW + "Level: " + ChatColor.WHITE + apt.level);
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
                if (!economy.has(player, totalDue)) {
                    player.sendMessage(ChatColor.RED + "You don't have enough money! Need: " + currencySymbol + totalDue);
                    return true;
                }

                economy.withdrawPlayer(player, totalDue);
                apt.lastTaxPayment = System.currentTimeMillis();
                apt.inactive = false;
                apt.penalty = 0;
                saveApartments();
                player.sendMessage(ChatColor.GREEN + "Paid tax of " + currencySymbol + totalDue + " for apartment " + apartmentId);
                break;

            case "info":
                player.sendMessage(ChatColor.GOLD + "=== Tax Info: " + apartmentId + " ===");
                player.sendMessage(ChatColor.YELLOW + "Tax Amount: " + ChatColor.WHITE + currencySymbol + apt.tax);
                player.sendMessage(ChatColor.YELLOW + "Tax Period: " + ChatColor.WHITE + apt.taxDays + " days");
                player.sendMessage(ChatColor.YELLOW + "Penalty: " + ChatColor.WHITE + currencySymbol + apt.penalty);
                player.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.WHITE + (apt.inactive ? "Inactive" : "Active"));
                break;

            default:
                player.sendMessage(ChatColor.RED + "Usage: /apartmentcore tax <pay|info> <apartment_id>");
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
                return createApartment(sender, args[1], args[2], Double.parseDouble(args[3]),
                        Double.parseDouble(args[4]), Integer.parseInt(args[5]));

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
                System.currentTimeMillis(), 0, false, 0);
        apartments.put(id, apt);
        saveApartments();

        sender.sendMessage(ChatColor.GREEN + "Successfully created apartment " + id);
        log("Admin " + sender.getName() + " created apartment " + id);

        return true;
    }

    private boolean removeApartment(CommandSender sender, String apartmentId) {
        if (!apartments.containsKey(apartmentId)) {
            sender.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
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

        switch (property.toLowerCase()) {
            case "owner":
                Player newOwner = Bukkit.getPlayer(value);
                if (newOwner == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found!");
                    return true;
                }
                apt.owner = newOwner.getUniqueId();
                sender.sendMessage(ChatColor.GREEN + "Set owner to " + value);
                break;

            case "price":
                apt.price = Double.parseDouble(value);
                sender.sendMessage(ChatColor.GREEN + "Set price to " + currencySymbol + value);
                break;

            case "tax":
                apt.tax = Double.parseDouble(value);
                sender.sendMessage(ChatColor.GREEN + "Set tax to " + currencySymbol + value);
                break;

            case "tax_time":
                apt.taxDays = Integer.parseInt(value);
                sender.sendMessage(ChatColor.GREEN + "Set tax time to " + value + " days");
                break;

            case "type":
            case "level":
                apt.level = Integer.parseInt(value);
                sender.sendMessage(ChatColor.GREEN + "Set level to " + value);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown property: " + property);
                return true;
        }

        saveApartments();
        return true;
    }

    private void listApartments(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Apartment List ===");
        for (Apartment apt : apartments.values()) {
            String owner = apt.owner != null ? Bukkit.getOfflinePlayer(apt.owner).getName() : "For Sale";
            sender.sendMessage(ChatColor.YELLOW + apt.id + ": " + ChatColor.WHITE +
                    "Owner: " + owner + ", Price: " + currencySymbol + apt.price +
                    ", Level: " + apt.level);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== ApartmentCore Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore info [id]" + ChatColor.WHITE + " - View info");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore buy <id>" + ChatColor.WHITE + " - Buy apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore sell <id>" + ChatColor.WHITE + " - Sell apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore teleport <id>" + ChatColor.WHITE + " - Teleport to apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore rent claim <id>" + ChatColor.WHITE + " - Claim income");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore tax pay <id>" + ChatColor.WHITE + " - Pay taxes");
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Admin Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin create" + ChatColor.WHITE + " - Create apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin remove" + ChatColor.WHITE + " - Remove apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin set" + ChatColor.WHITE + " - Set apartment property");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin apartment_list" + ChatColor.WHITE + " - List all apartments");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin reload" + ChatColor.WHITE + " - Reload config");
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

        Apartment(String id, String regionName, String worldName, UUID owner, double price,
                  double tax, int taxDays, int level, long lastTaxPayment, double pendingIncome,
                  boolean inactive, double penalty) {
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
        }

        /**
         * Get hourly income based on level
         */
        double getHourlyIncome() {
            switch (level) {
                case 1: return 10 + Math.random() * 90;  // 10-100
                case 2: return 100 + Math.random() * 100; // 100-200
                case 3: return 200 + Math.random() * 100; // 200-300
                case 4: return 300 + Math.random() * 100; // 300-400
                case 5: return 400 + Math.random() * 100; // 400-500
                default: return 10;
            }
        }

        /**
         * Check and process tax payment
         */
        void checkTaxPayment(Economy econ) {
            long currentTime = System.currentTimeMillis();
            long daysPassed = (currentTime - lastTaxPayment) / (24 * 60 * 60 * 1000);

            if (daysPassed >= taxDays) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(owner);
                if (econ.has(player, tax)) {
                    econ.withdrawPlayer(player, tax);
                    lastTaxPayment = currentTime;
                    debug("Auto-collected tax from " + player.getName() + " for apartment " + id);
                } else {
                    inactive = true;
                    penalty += price * 0.25 * daysPassed; // 25% penalty per day
                    debug("Apartment " + id + " is now inactive due to unpaid taxes");

                    // If inactive for 3+ days, remove ownership
                    if (daysPassed >= taxDays + 3) {
                        owner = null;
                        inactive = false;
                        penalty = 0;
                        pendingIncome = 0;
                        debug("Apartment " + id + " ownership removed due to extended non-payment");
                    }
                }
            }
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
            return "1.0.0";
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, @NotNull String params) {
            if (player == null) return "";

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
                    return String.valueOf(apt.price);
                case "tax":
                    return String.valueOf(apt.tax);
                case "level":
                    return String.valueOf(apt.level);
                case "income":
                    return String.valueOf(apt.pendingIncome);
                case "status":
                    return apt.inactive ? "Inactive" : "Active";
                default:
                    return "";
            }
        }
    }
}