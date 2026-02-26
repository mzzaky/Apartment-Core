package com.aithor.apartmentcore;

import com.aithor.apartmentcore.command.CommandHandler;
import com.aithor.apartmentcore.gui.GUIManager;
import com.aithor.apartmentcore.manager.ApartmentManager;
import com.aithor.apartmentcore.manager.AuctionManager;
import com.aithor.apartmentcore.manager.ConfigManager;
import com.aithor.apartmentcore.manager.DataManager;
import com.aithor.apartmentcore.manager.LoggerManager;
import com.aithor.apartmentcore.manager.MessageManager;
import com.aithor.apartmentcore.manager.TaskManager;
import com.aithor.apartmentcore.model.ConfirmationAction;
import com.aithor.apartmentcore.placeholder.ApartmentPlaceholder;
import com.aithor.apartmentcore.shop.ApartmentShopManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;

public class ApartmentCore extends JavaPlugin {
    private Economy economy;
    private ConfigManager configManager;
    private DataManager dataManager;
    private ApartmentManager apartmentManager;
    private TaskManager taskManager;
    private CommandHandler commandHandler;
    private ApartmentPlaceholder placeholder;
    private MessageManager messageManager;
    private LoggerManager loggerManager;
    private AuctionManager auctionManager;
    private GUIManager guiManager;
    private ApartmentShopManager shopManager;
    private BukkitTask auctionTask;

    private final Map<UUID, Long> commandCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, ConfirmationAction> pendingConfirmations = new ConcurrentHashMap<>();

    private long lastMinecraftDay = 0L;
    private long lastRentClaimTime = System.currentTimeMillis();
    private long lastIncomeGenerationTime = System.currentTimeMillis();

    @Override
    public void onEnable() {
        // Ensure default config exists and load configuration
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfiguration();
        this.messageManager = new MessageManager(this);

        // Initialize LoggerManager after config is loaded
        this.loggerManager = new LoggerManager(this, configManager);

        if (!setupEconomy()) {
            getLogger().severe("Vault not found or no economy provider! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Data and managers
        this.dataManager = new DataManager(this, configManager);
        this.dataManager.loadDataFile();
        this.dataManager.loadGuestBookFile();
        this.dataManager.loadStatsFile();

        this.apartmentManager = new ApartmentManager(this, economy, configManager, dataManager);
        this.apartmentManager.loadApartments();
        this.apartmentManager.loadRatings();
        this.apartmentManager.loadGuestBooks();
        this.apartmentManager.loadStats();

        this.taskManager = new TaskManager(this, apartmentManager, configManager);
        this.taskManager.startAllTasks();

        // Auction system
        if (configManager.isAuctionEnabled()) {
            initAuctionSystem();
        } else {
            debug("Auction system is disabled via config.");
        }

        // GUI system
        if (this.configManager.isGuiEnabled()) {
            this.guiManager = new GUIManager(this);
        } else {
            debug("GUI system is disabled via config.");
        }
        
        // Shop system
        this.shopManager = new ApartmentShopManager(this, apartmentManager, economy, configManager, dataManager);

        // Commands
        this.commandHandler = new CommandHandler(this, apartmentManager, economy, configManager);
        PluginCommand cmd = getCommand("apartmentcore");
        if (cmd != null) {
            cmd.setExecutor(new CommandExecutor() {
                @Override
                public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                    return commandHandler.handleCommand(sender, args);
                }
            });
            cmd.setTabCompleter(commandHandler);
        } else {
            getLogger().severe("Command 'apartmentcore' missing in plugin.yml!");
        }

        // PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null
                && getConfig().getBoolean("placeholderapi.enabled", true)) {
            try {
                this.placeholder = new ApartmentPlaceholder(this, apartmentManager);
                this.placeholder.register();
                debug("PlaceholderAPI expansion registered.");
            } catch (Throwable t) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
            }
        }

        log("ApartmentCore enabled.");
    }

    @Override
    public void onDisable() {
        if (taskManager != null) {
            taskManager.cancelAllTasks();
        }
        if (auctionTask != null) {
            try { auctionTask.cancel(); } catch (Throwable ignored) {}
            auctionTask = null;
        }
        if (apartmentManager != null) {
            apartmentManager.saveApartments();
            apartmentManager.saveRatings();
            apartmentManager.saveGuestBooks();
            apartmentManager.saveStats();
        }
        if (auctionManager != null) {
            auctionManager.saveAuctions();
            auctionManager = null;
        }
        if (guiManager != null) {
            try { guiManager.closeAllGUIs(); } catch (Throwable ignored) {}
            guiManager = null;
        }
        if (shopManager != null) {
            try { shopManager.saveShopData(); } catch (Throwable ignored) {}
            shopManager = null;
        }
        log("ApartmentCore disabled.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        this.economy = rsp.getProvider();
        return this.economy != null;
    }

    // Logging helpers
    public void debug(String msg) {
        if (configManager != null && configManager.isDebugMode()) {
            getLogger().info("[DEBUG] " + msg);
        }
    }

    public void log(String msg) {
        getLogger().info(msg);
        if (loggerManager != null) {
            loggerManager.log(msg);
        }
    }

    public void logTransaction(String msg) {
        if (loggerManager != null) {
            loggerManager.logTransaction(msg);
        }
    }

    public void logAdminAction(String msg) {
        if (loggerManager != null) {
            loggerManager.logAdminAction(msg);
        }
    }

    // Accessors for shared state
    public Economy getEconomy() {
        return economy;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public ApartmentManager getApartmentManager() {
        return apartmentManager;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public LoggerManager getLoggerManager() {
        return loggerManager;
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

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public GUIManager getGUIManager() {
        return guiManager;
    }
    
    public ApartmentShopManager getShopManager() {
        return shopManager;
    }

    /**
     * Initialize the auction system (manager + scheduler) if enabled.
     */
    public void initAuctionSystem() {
        if (auctionManager != null) {
            debug("Auction system already initialized.");
            return;
        }
        if (!configManager.isAuctionEnabled()) {
            debug("Auction system not initialized because it's disabled in config.");
            return;
        }
        try {
            this.auctionManager = new AuctionManager(this, apartmentManager, economy, configManager, dataManager);
            this.auctionManager.loadAuctions();
            // schedule periodic processing every 60 seconds
            this.auctionTask = getServer().getScheduler().runTaskTimer(this, () -> {
                try {
                    if (auctionManager != null) {
                        auctionManager.processEndedAuctions();
                    }
                } catch (Throwable ignored) {}
            }, 20L * 60L, 20L * 60L);
            debug("Auction system initialized.");
        } catch (Throwable t) {
            getLogger().warning("Failed to initialize Auction system: " + t.getMessage());
        }
    }

    /**
     * Shutdown the auction system (cancel scheduler and save).
     */
    public void shutdownAuctionSystem() {
        if (auctionTask != null) {
            try { auctionTask.cancel(); } catch (Throwable ignored) {}
            auctionTask = null;
        }
        if (auctionManager != null) {
            try { auctionManager.saveAuctions(); } catch (Throwable ignored) {}
            auctionManager = null;
        }
        debug("Auction system shut down.");
    }
}