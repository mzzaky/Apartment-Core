package com.aithor.apartmentcore.research;

import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.manager.ConfigManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the research system: configuration, player data, tick processing,
 * persistence, and buff calculations.
 */
public class ResearchManager {

    private final ApartmentCore plugin;
    private final Economy economy;
    private final ConfigManager configManager;

    // Research config (from research.yml)
    private FileConfiguration researchConfig;
    private File researchConfigFile;

    // Player data (UUID -> data)
    private final Map<UUID, PlayerResearchData> playerData;

    // Data file
    private File dataFile;
    private FileConfiguration dataConfig;

    // Tick task
    private BukkitTask tickTask;

    // Cached config values per research type
    private final Map<ResearchType, Double> costBase = new ConcurrentHashMap<>();
    private final Map<ResearchType, Double> costMultiplier = new ConcurrentHashMap<>();
    private final Map<ResearchType, Long> durationBase = new ConcurrentHashMap<>();
    private final Map<ResearchType, Double> durationMultiplier = new ConcurrentHashMap<>();

    private boolean enabled;

    public ResearchManager(ApartmentCore plugin, Economy economy, ConfigManager configManager) {
        this.plugin = plugin;
        this.economy = economy;
        this.configManager = configManager;
        this.playerData = new ConcurrentHashMap<>();

        loadResearchConfig();
        loadPlayerData();
        startTickTask();
    }

    // ===========================
    // Configuration
    // ===========================

    private void loadResearchConfig() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            this.researchConfigFile = new File(plugin.getDataFolder(), "research.yml");
            if (!researchConfigFile.exists()) {
                try (InputStream in = plugin.getResource("research.yml")) {
                    if (in != null) {
                        Files.copy(in, researchConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        plugin.getLogger().info("research.yml created from default resource.");
                    } else {
                        researchConfigFile.createNewFile();
                        plugin.getLogger().warning("research.yml resource not found in jar! Created empty file.");
                    }
                }
            }

            this.researchConfig = YamlConfiguration.loadConfiguration(researchConfigFile);
            this.enabled = researchConfig.getBoolean("research.enabled", true);

            // Parse per-research config
            ConfigurationSection researches = researchConfig.getConfigurationSection("researches");
            if (researches != null) {
                for (ResearchType type : ResearchType.values()) {
                    ConfigurationSection sec = researches.getConfigurationSection(type.getConfigKey());
                    if (sec != null) {
                        costBase.put(type, sec.getDouble("cost-base", 50000));
                        costMultiplier.put(type, sec.getDouble("cost-multiplier", 3.0));
                        durationBase.put(type, (long) sec.getDouble("duration-base", 3600));
                        durationMultiplier.put(type, sec.getDouble("duration-multiplier", 2.5));
                    }
                }
            }

            plugin.debug("Research configuration loaded successfully.");
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to load research config: " + t.getMessage());
        }
    }

    public void reloadConfig() {
        loadResearchConfig();
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ===========================
    // Cost & Duration Calculations
    // ===========================

    /**
     * Get the cost for a specific research at a specific tier.
     * Formula: base * multiplier^(tier-1)
     */
    public double getResearchCost(ResearchType type, int tier) {
        double base = costBase.getOrDefault(type, 50000.0);
        double mult = costMultiplier.getOrDefault(type, 3.0);
        return base * Math.pow(mult, tier - 1);
    }

    /**
     * Get the duration in seconds for a specific research at a specific tier.
     * Formula: base * multiplier^(tier-1)
     */
    public long getResearchDurationSeconds(ResearchType type, int tier) {
        long base = durationBase.getOrDefault(type, 3600L);
        double mult = durationMultiplier.getOrDefault(type, 2.5);
        return (long) (base * Math.pow(mult, tier - 1));
    }

    // ===========================
    // Research Actions
    // ===========================

    /**
     * Start a research for a player. Validates conditions and deducts cost.
     * 
     * @return Result message (success or error)
     */
    public StartResult startResearch(Player player, ResearchType type) {
        if (!enabled) {
            return new StartResult(false, "Research system is disabled!");
        }

        PlayerResearchData data = getPlayerData(player.getUniqueId());

        // Check if already researching
        if (data.hasActiveResearch()) {
            return new StartResult(false, "You already have an active research! Wait for it to complete.");
        }

        // Check if max tier reached
        if (data.isMaxTier(type)) {
            return new StartResult(false, "This research is already at maximum tier!");
        }

        int nextTier = data.getCompletedTier(type) + 1;
        double cost = getResearchCost(type, nextTier);

        // Check funds
        if (!economy.has(player, cost)) {
            return new StartResult(false, "Insufficient funds! Need " +
                    configManager.formatMoney(cost) + " but you have " +
                    configManager.formatMoney(economy.getBalance(player)) + ".");
        }

        // Deduct cost
        EconomyResponse resp = economy.withdrawPlayer(player, cost);
        if (resp == null || !resp.transactionSuccess()) {
            return new StartResult(false, "Payment failed" +
                    (resp != null && resp.errorMessage != null ? ": " + resp.errorMessage : "."));
        }

        // Start research
        long durationMs = getResearchDurationSeconds(type, nextTier) * 1000L;
        data.startResearch(type, nextTier, durationMs);

        savePlayerData();

        plugin.logTransaction(player.getName() + " started research " + type.getDisplayName() +
                " tier " + toRoman(nextTier) + " for " + configManager.formatMoney(cost));

        return new StartResult(true, "Research " + type.getDisplayName() +
                " Tier " + toRoman(nextTier) + " started! Cost: " + configManager.formatMoney(cost));
    }

    // ===========================
    // Tick Processing
    // ===========================

    private void startTickTask() {
        // Check every 20 ticks (1 second) for completed researches
        this.tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled)
                return;
            for (Map.Entry<UUID, PlayerResearchData> entry : playerData.entrySet()) {
                PlayerResearchData data = entry.getValue();
                if (data.hasActiveResearch() && data.isResearchComplete()) {
                    completeResearch(entry.getKey(), data);
                }
            }
        }, 20L, 20L);
    }

    private void completeResearch(UUID playerId, PlayerResearchData data) {
        ResearchType type = data.getActiveResearch();
        int tier = data.getActiveTier();
        data.completeResearch();
        savePlayerData();

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            String msg = plugin.getMessageManager().getMessage("research.completed")
                    .replace("%research%", type.getDisplayName())
                    .replace("%tier%", toRoman(tier));
            player.sendMessage(msg);

            // Play sound and title
            try {
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                player.sendTitle(
                        org.bukkit.ChatColor.translateAlternateColorCodes('&', "&6Research Complete!"),
                        org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                "&e" + type.getDisplayName() + " &fTier " + toRoman(tier)),
                        10, 60, 20);
            } catch (Throwable ignored) {
            }
        }

        plugin.log("Research completed: " + type.getDisplayName() + " tier " + toRoman(tier) +
                " for player " + playerId);
    }

    // ===========================
    // Buff Getters (for integration)
    // ===========================

    /**
     * Get the income interval reduction percentage for a player.
     * Revenue Acceleration: 5% per tier.
     */
    public double getIncomeIntervalReduction(UUID playerId) {
        PlayerResearchData data = playerData.get(playerId);
        if (data == null)
            return 0.0;
        return data.getCompletedTier(ResearchType.REVENUE_ACCELERATION) * 5.0;
    }

    /**
     * Get the income amount bonus percentage for a player.
     * Capital Growth Strategy: 5% per tier.
     */
    public double getIncomeAmountBonus(UUID playerId) {
        PlayerResearchData data = playerData.get(playerId);
        if (data == null)
            return 0.0;
        return data.getCompletedTier(ResearchType.CAPITAL_GROWTH) * 5.0;
    }

    /**
     * Get the tax reduction percentage for a player.
     * Tax Efficiency Strategy: 5% per tier (applied to final tax amount).
     */
    public double getTaxReduction(UUID playerId) {
        PlayerResearchData data = playerData.get(playerId);
        if (data == null)
            return 0.0;
        return data.getCompletedTier(ResearchType.TAX_EFFICIENCY) * 5.0;
    }

    /**
     * Get the extra apartment ownership slots for a player.
     * Expansion Plan: +1 per tier.
     */
    public int getExtraOwnershipSlots(UUID playerId) {
        PlayerResearchData data = playerData.get(playerId);
        if (data == null)
            return 0;
        return data.getCompletedTier(ResearchType.EXPANSION_PLAN);
    }

    /**
     * Get the income capacity bonus percentage for a player.
     * Vault Expansion: 5% per tier.
     */
    public double getIncomeCapacityBonus(UUID playerId) {
        PlayerResearchData data = playerData.get(playerId);
        if (data == null)
            return 0.0;
        return data.getCompletedTier(ResearchType.CAPACITY_EXPANSION) * 5.0;
    }

    /**
     * Get the auction fee reduction percentage for a player.
     * Auction Efficiency: 5% per tier.
     */
    public double getAuctionFeeReduction(UUID playerId) {
        PlayerResearchData data = playerData.get(playerId);
        if (data == null)
            return 0.0;
        return data.getCompletedTier(ResearchType.AUCTION_EFFICIENCY) * 5.0;
    }

    /**
     * Get the auction commission reduction (absolute percentage points) for a
     * player.
     * Auction Efficiency: 1% per tier.
     */
    public double getAuctionCommissionReduction(UUID playerId) {
        PlayerResearchData data = playerData.get(playerId);
        if (data == null)
            return 0.0;
        return data.getCompletedTier(ResearchType.AUCTION_EFFICIENCY) * 1.0;
    }

    // ===========================
    // Player Data Access
    // ===========================

    public PlayerResearchData getPlayerData(UUID playerId) {
        return playerData.computeIfAbsent(playerId, PlayerResearchData::new);
    }

    // ===========================
    // Persistence
    // ===========================

    private void loadPlayerData() {
        try {
            File dataDir = new File(plugin.getDataFolder(), "data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            this.dataFile = new File(dataDir, "research_data.yml");
            if (!dataFile.exists()) {
                dataFile.createNewFile();
                plugin.debug("Created new research_data.yml file");
            }

            this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);

            ConfigurationSection players = dataConfig.getConfigurationSection("players");
            if (players == null)
                return;

            for (String uuidStr : players.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    PlayerResearchData data = new PlayerResearchData(uuid);
                    ConfigurationSection playerSec = players.getConfigurationSection(uuidStr);
                    if (playerSec == null)
                        continue;

                    // Load completed tiers
                    ConfigurationSection completed = playerSec.getConfigurationSection("completed");
                    if (completed != null) {
                        for (String key : completed.getKeys(false)) {
                            ResearchType type = ResearchType.fromConfigKey(key);
                            if (type != null) {
                                data.setCompletedTier(type, completed.getInt(key, 0));
                            }
                        }
                    }

                    // Load active research
                    if (playerSec.contains("active")) {
                        ConfigurationSection active = playerSec.getConfigurationSection("active");
                        if (active != null) {
                            String typeKey = active.getString("type");
                            ResearchType type = typeKey != null ? ResearchType.fromConfigKey(typeKey) : null;
                            if (type != null) {
                                int tier = active.getInt("tier", 1);
                                long startTime = active.getLong("start-time", 0L);
                                long duration = active.getLong("duration", 0L);
                                data.restoreActiveResearch(type, tier, startTime, duration);
                            }
                        }
                    }

                    playerData.put(uuid, data);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in research data: " + uuidStr);
                }
            }

            plugin.debug("Loaded research data for " + playerData.size() + " players.");
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to load research data: " + t.getMessage());
        }
    }

    public void savePlayerData() {
        try {
            YamlConfiguration config = new YamlConfiguration();

            for (Map.Entry<UUID, PlayerResearchData> entry : playerData.entrySet()) {
                String uuid = entry.getKey().toString();
                PlayerResearchData data = entry.getValue();

                // Save completed tiers
                for (Map.Entry<ResearchType, Integer> tier : data.getCompletedTiers().entrySet()) {
                    config.set("players." + uuid + ".completed." + tier.getKey().getConfigKey(), tier.getValue());
                }

                // Save active research
                if (data.hasActiveResearch()) {
                    String activePath = "players." + uuid + ".active.";
                    config.set(activePath + "type", data.getActiveResearch().getConfigKey());
                    config.set(activePath + "tier", data.getActiveTier());
                    config.set(activePath + "start-time", data.getResearchStartTime());
                    config.set(activePath + "duration", data.getResearchDuration());
                } else {
                    config.set("players." + uuid + ".active", null);
                }
            }

            config.save(dataFile);
            plugin.debug("Saved research data for " + playerData.size() + " players.");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save research data: " + e.getMessage());
        }
    }

    // ===========================
    // Shutdown
    // ===========================

    public void shutdown() {
        if (tickTask != null) {
            try {
                tickTask.cancel();
            } catch (Throwable ignored) {
            }
            tickTask = null;
        }
        savePlayerData();
    }

    // ===========================
    // Utility
    // ===========================

    public static String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }

    public FileConfiguration getResearchConfig() {
        return researchConfig;
    }

    /**
     * Result class for start research operations.
     */
    public static class StartResult {
        private final boolean success;
        private final String message;

        public StartResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
