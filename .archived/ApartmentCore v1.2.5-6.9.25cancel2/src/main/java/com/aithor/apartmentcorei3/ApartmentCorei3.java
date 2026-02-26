package com.aithor.apartmentcorei3;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main plugin class for ApartmentCore
 * Manages apartments in Minecraft with WorldGuard regions
 * @author Aithor
 * @version 1.2.0
 */
public class ApartmentCorei3 extends JavaPlugin {

    private Economy economy;
    private ApartmentManager apartmentManager;
    private CommandHandler commandHandler;
    private TaskManager taskManager;
    private DataManager dataManager;
    private ConfigManager configManager;
    
    // Shared data maps
    private Map<UUID, Long> commandCooldowns;
    private Map<UUID, ConfirmationAction> pendingConfirmations;
    
    private long lastMinecraftDay = 0;
    private long lastRentClaimTime = 0;
    private long lastIncomeGenerationTime = 0;

    @Override
    public void onEnable() {
        // Initialize collections
        commandCooldowns = new ConcurrentHashMap<>();
        pendingConfirmations = new ConcurrentHashMap<>();

        // Save default config
        saveDefaultConfig();
        
        // Initialize managers
        configManager = new ConfigManager(this);
        configManager.loadConfiguration();
        
        // Setup economy
        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize managers
        dataManager = new DataManager(this, configManager);
        apartmentManager = new ApartmentManager(this, economy, configManager, dataManager);
        commandHandler = new CommandHandler(this, apartmentManager, economy, configManager);
        taskManager = new TaskManager(this, apartmentManager, configManager);
        
        // Load data
        dataManager.loadDataFile();
        apartmentManager.loadApartments();
        apartmentManager.loadRatings();
        
        // Initialize Minecraft day tracking
        World mainWorld = getServer().getWorlds().get(0);
        if (mainWorld != null) {
            lastMinecraftDay = mainWorld.getFullTime() / 24000;
        }

        // Register PlaceholderAPI expansion
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            getServer().getScheduler().runTaskLater(this, () -> {
                new ApartmentPlaceholder(this, apartmentManager).register();
                log("PlaceholderAPI expansion registered successfully");
            }, 20L);
        }

        // Register tab completer
        getCommand("apartmentcore").setTabCompleter(commandHandler);

        // Start all tasks
        taskManager.startAllTasks();

        log("ApartmentCore v1.2.0 enabled successfully!");
        log("Loaded " + apartmentManager.getApartmentCount() + " apartments");
    }

    @Override
    public void onDisable() {
        // Cancel all tasks
        getServer().getScheduler().cancelTasks(this);

        // Create backup on shutdown
        if (configManager.isBackupEnabled()) {
            dataManager.createBackup("shutdown");
        }

        // Save all data
        if (dataManager != null && apartmentManager != null) {
            apartmentManager.saveApartments();
            apartmentManager.saveRatings();
            log("ApartmentCore disabled. All data saved.");
        } else {
            log("ApartmentCore disabled without saving (not initialized properly).");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("apartmentcore")) {
            return false;
        }
        return commandHandler.handleCommand(sender, args);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("apartmentcore")) {
            return null;
        }
        return commandHandler.onTabComplete(sender, command, alias, args);
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
     * Log message with prefix
     */
    public void log(String message) {
        getLogger().info(message);
    }

    /**
     * Debug message (only if debug mode enabled)
     */
    public void debug(String message) {
        if (configManager.isDebugMode()) {
            getLogger().info(() -> "[DEBUG] " + message);
        }
    }

    // Getters
    public Economy getEconomy() {
        return economy;
    }
    
    public ApartmentManager getApartmentManager() {
        return apartmentManager;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }
    
    public CommandHandler getCommandHandler() {
        return commandHandler;
    }
    
    public TaskManager getTaskManager() {
        return taskManager;
    }
    
    public DataManager getDataManager() {
        return dataManager;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public Map<UUID, Long> getCommandCooldowns() {
        return commandCooldowns;
    }
    
    public Map<UUID, ConfirmationAction> getPendingConfirmations() {
        return pendingConfirmations;
    }
    
    public long getLastMinecraftDay() {
        return lastMinecraftDay;
    }
    
    public void setLastMinecraftDay(long lastMinecraftDay) {
        this.lastMinecraftDay = lastMinecraftDay;
    }
    
    public long getLastRentClaimTime() {
        return lastRentClaimTime;
    }
    
    public void setLastRentClaimTime(long lastRentClaimTime) {
        this.lastRentClaimTime = lastRentClaimTime;
    }

    public long getLastIncomeGenerationTime() {
        return lastIncomeGenerationTime;
    }

    public void setLastIncomeGenerationTime(long lastIncomeGenerationTime) {
        this.lastIncomeGenerationTime = lastIncomeGenerationTime;
    }
}