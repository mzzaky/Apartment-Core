package com.aithor.apartmentcore.achievement;

import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.manager.ConfigManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the achievement system: configuration, player data, tracking,
 * persistence, and reward distribution.
 */
public class AchievementManager {

    private final ApartmentCore plugin;
    private final Economy economy;
    private final ConfigManager configManager;

    // Achievement config (from achievements.yml)
    private FileConfiguration achievementConfig;
    private File achievementConfigFile;

    // Player data (UUID -> data)
    private final Map<UUID, PlayerAchievementData> playerData;

    // Data file for persistence
    private File dataFile;
    private FileConfiguration dataConfig;

    // Cached config per achievement type
    private final Map<AchievementType, Boolean> enabledMap = new ConcurrentHashMap<>();
    private final Map<AchievementType, String> nameMap = new ConcurrentHashMap<>();
    private final Map<AchievementType, String> descriptionMap = new ConcurrentHashMap<>();
    private final Map<AchievementType, Material> iconMap = new ConcurrentHashMap<>();
    private final Map<AchievementType, Double> targetMap = new ConcurrentHashMap<>();
    private final Map<AchievementType, Double> rewardMap = new ConcurrentHashMap<>();
    private final Map<AchievementType, Boolean> broadcastMap = new ConcurrentHashMap<>();

    private boolean enabled;

    public AchievementManager(ApartmentCore plugin, Economy economy, ConfigManager configManager) {
        this.plugin = plugin;
        this.economy = economy;
        this.configManager = configManager;
        this.playerData = new ConcurrentHashMap<>();

        loadAchievementConfig();
        loadPlayerData();
    }

    // ===========================
    // Configuration
    // ===========================

    private void loadAchievementConfig() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            this.achievementConfigFile = new File(plugin.getDataFolder(), "achievements.yml");
            if (!achievementConfigFile.exists()) {
                try (InputStream in = plugin.getResource("achievements.yml")) {
                    if (in != null) {
                        Files.copy(in, achievementConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        plugin.getLogger().info("achievements.yml created from default resource.");
                    } else {
                        achievementConfigFile.createNewFile();
                        plugin.getLogger().warning("achievements.yml resource not found in jar! Created empty file.");
                    }
                }
            }

            this.achievementConfig = YamlConfiguration.loadConfiguration(achievementConfigFile);
            this.enabled = achievementConfig.getBoolean("achievements.enabled", true);

            // Parse per-achievement config
            ConfigurationSection definitions = achievementConfig.getConfigurationSection("definitions");
            if (definitions != null) {
                for (AchievementType type : AchievementType.values()) {
                    ConfigurationSection sec = definitions.getConfigurationSection(type.getConfigKey());
                    if (sec != null) {
                        enabledMap.put(type, sec.getBoolean("enabled", true));
                        nameMap.put(type, sec.getString("name", type.getDefaultName()));
                        descriptionMap.put(type, sec.getString("description", type.getDefaultDescription()));
                        targetMap.put(type, sec.getDouble("target", 1000000));
                        rewardMap.put(type, sec.getDouble("reward", 0));
                        broadcastMap.put(type, sec.getBoolean("broadcast", true));

                        // Parse icon material
                        String iconStr = sec.getString("icon", "GOLD_BLOCK");
                        try {
                            iconMap.put(type, Material.valueOf(iconStr.toUpperCase()));
                        } catch (IllegalArgumentException e) {
                            iconMap.put(type, Material.GOLD_BLOCK);
                        }
                    } else {
                        // Defaults
                        enabledMap.put(type, true);
                        nameMap.put(type, type.getDefaultName());
                        descriptionMap.put(type, type.getDefaultDescription());
                        targetMap.put(type, 1000000.0);
                        rewardMap.put(type, 0.0);
                        broadcastMap.put(type, true);
                        iconMap.put(type, Material.GOLD_BLOCK);
                    }
                }
            }

            plugin.debug("Achievement configuration loaded successfully.");
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to load achievement config: " + t.getMessage());
        }
    }

    public void reloadConfig() {
        loadAchievementConfig();
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ===========================
    // Config Getters
    // ===========================

    public boolean isAchievementEnabled(AchievementType type) {
        return enabledMap.getOrDefault(type, true);
    }

    public String getAchievementName(AchievementType type) {
        return nameMap.getOrDefault(type, type.getDefaultName());
    }

    public String getAchievementDescription(AchievementType type) {
        return descriptionMap.getOrDefault(type, type.getDefaultDescription());
    }

    public Material getAchievementIcon(AchievementType type) {
        return iconMap.getOrDefault(type, Material.GOLD_BLOCK);
    }

    public double getAchievementTarget(AchievementType type) {
        return targetMap.getOrDefault(type, 1000000.0);
    }

    public double getAchievementReward(AchievementType type) {
        return rewardMap.getOrDefault(type, 0.0);
    }

    public boolean isAchievementBroadcast(AchievementType type) {
        return broadcastMap.getOrDefault(type, true);
    }

    // ===========================
    // Progress Tracking
    // ===========================

    /**
     * Record progress for an achievement. Automatically checks for completion.
     *
     * @param playerId The player UUID
     * @param type     The achievement type
     * @param amount   The amount to add to progress
     */
    public void recordProgress(UUID playerId, AchievementType type, double amount) {
        if (!enabled || !isAchievementEnabled(type)) return;

        PlayerAchievementData data = getPlayerData(playerId);
        if (data.isCompleted(type)) return;

        data.addProgress(type, amount);
        checkCompletion(playerId, data, type);
    }

    /**
     * Set absolute progress for an achievement (used for snapshot-based achievements
     * like max level or max tier research).
     *
     * @param playerId The player UUID
     * @param type     The achievement type
     * @param value    The absolute progress value
     */
    public void setProgress(UUID playerId, AchievementType type, double value) {
        if (!enabled || !isAchievementEnabled(type)) return;

        PlayerAchievementData data = getPlayerData(playerId);
        if (data.isCompleted(type)) return;

        data.setProgress(type, value);
        checkCompletion(playerId, data, type);
    }

    /**
     * Check if a player has met the target for an achievement and grant it if so.
     */
    private void checkCompletion(UUID playerId, PlayerAchievementData data, AchievementType type) {
        if (data.isCompleted(type)) return;

        double target = getAchievementTarget(type);
        if (data.getProgress(type) >= target) {
            completeAchievement(playerId, data, type);
        }
    }

    /**
     * Mark an achievement as completed and grant rewards.
     */
    private void completeAchievement(UUID playerId, PlayerAchievementData data, AchievementType type) {
        data.setCompleted(type, true);
        savePlayerData();

        Player player = Bukkit.getPlayer(playerId);
        String achName = getAchievementName(type);

        // Grant reward
        double reward = getAchievementReward(type);
        if (reward > 0 && player != null) {
            economy.depositPlayer(player, reward);
        }

        // Notify player
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&6&l[Achievement Unlocked] &e" + achName + "&7 - " + getAchievementDescription(type)));
            if (reward > 0) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&6&l[Reward] &a" + configManager.formatMoney(reward)));
            }

            // Play sound and title
            try {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                player.sendTitle(
                        ChatColor.translateAlternateColorCodes('&', "&6&lAchievement Unlocked!"),
                        ChatColor.translateAlternateColorCodes('&', "&e" + achName),
                        10, 70, 20);
            } catch (Throwable ignored) {
            }
        }

        // Broadcast if enabled
        if (isAchievementBroadcast(type)) {
            String playerName = player != null ? player.getName() : Bukkit.getOfflinePlayer(playerId).getName();
            String broadcast = ChatColor.translateAlternateColorCodes('&',
                    "&6[ApartmentCore] &e" + playerName + " &7has unlocked the achievement &6" + achName + "&7!");
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(broadcast);
            }
        }

        plugin.log("Achievement unlocked: " + achName + " for player " + playerId);
    }

    // ===========================
    // Player Data Access
    // ===========================

    public PlayerAchievementData getPlayerData(UUID playerId) {
        return playerData.computeIfAbsent(playerId, PlayerAchievementData::new);
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

            this.dataFile = new File(dataDir, "achievement_data.yml");
            if (!dataFile.exists()) {
                dataFile.createNewFile();
                plugin.debug("Created new achievement_data.yml file");
            }

            this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);

            ConfigurationSection players = dataConfig.getConfigurationSection("players");
            if (players == null) return;

            for (String uuidStr : players.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    PlayerAchievementData data = new PlayerAchievementData(uuid);
                    ConfigurationSection playerSec = players.getConfigurationSection(uuidStr);
                    if (playerSec == null) continue;

                    for (AchievementType type : AchievementType.values()) {
                        ConfigurationSection achSec = playerSec.getConfigurationSection(type.getConfigKey());
                        if (achSec != null) {
                            data.setProgress(type, achSec.getDouble("progress", 0));
                            data.setCompleted(type, achSec.getBoolean("completed", false));
                            data.setCompletedAt(type, achSec.getLong("completed-at", 0L));
                        }
                    }

                    playerData.put(uuid, data);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in achievement data: " + uuidStr);
                }
            }

            plugin.debug("Loaded achievement data for " + playerData.size() + " players.");
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to load achievement data: " + t.getMessage());
        }
    }

    public void savePlayerData() {
        try {
            YamlConfiguration config = new YamlConfiguration();

            for (Map.Entry<UUID, PlayerAchievementData> entry : playerData.entrySet()) {
                String uuid = entry.getKey().toString();
                PlayerAchievementData data = entry.getValue();

                for (AchievementType type : AchievementType.values()) {
                    String path = "players." + uuid + "." + type.getConfigKey() + ".";
                    config.set(path + "progress", data.getProgress(type));
                    config.set(path + "completed", data.isCompleted(type));
                    config.set(path + "completed-at", data.getCompletedAt(type));
                }
            }

            config.save(dataFile);
            plugin.debug("Saved achievement data for " + playerData.size() + " players.");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save achievement data: " + e.getMessage());
        }
    }

    // ===========================
    // Shutdown
    // ===========================

    public void shutdown() {
        savePlayerData();
    }
}
