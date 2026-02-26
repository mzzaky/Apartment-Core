package com.aithor.apartmentcorei3;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;

public class ApartmentCorei3 extends JavaPlugin {
    private Economy economy;
    private ConfigManager configManager;
    private DataManager dataManager;
    private ApartmentManager apartmentManager;
    private TaskManager taskManager;
    private CommandHandler commandHandler;
    private ApartmentPlaceholder placeholder;
    private MessageManager messageManager;
    private LoggerManager loggerManager;

    private final Map<UUID, Long> commandCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, ConfirmationAction> pendingConfirmations = new ConcurrentHashMap<>();

    private long lastMinecraftDay = 0L;
    private long lastRentClaimTime = System.currentTimeMillis();
    private long lastIncomeGenerationTime = System.currentTimeMillis();

    @Override
    public void onEnable() {
        // Ensure default config exists and load configuration
        saveDefaultConfig();
        this.messageManager = new MessageManager(this);

        this.configManager = new ConfigManager(this);
        this.configManager.loadConfiguration();

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
        if (apartmentManager != null) {
            apartmentManager.saveApartments();
            apartmentManager.saveRatings();
            apartmentManager.saveGuestBooks();
            apartmentManager.saveStats();
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
}