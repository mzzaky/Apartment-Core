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
    // rewards is now a list of strings like "[money] 5000", "[exp] 100", "[console]
    // give %player% diamond 1"
    private final Map<AchievementType, java.util.List<String>> rewardsMap = new ConcurrentHashMap<>();
    private final Map<AchievementType, Boolean> broadcastMap = new ConcurrentHashMap<>();

    private boolean enabled;

    // Effect config fields
    private boolean effectSoundEnabled;
    private String effectSoundName;
    private float effectSoundVolume;
    private float effectSoundPitch;

    private boolean effectTitleEnabled;
    private String effectTitleHeader;
    private String effectTitleSubheader;
    private int effectTitleFadeIn;
    private int effectTitleStay;
    private int effectTitleFadeOut;

    private boolean effectActionBarEnabled;
    private String effectActionBarFormat;

    private boolean effectBossBarEnabled;
    private String effectBossBarTitle;
    private String effectBossBarColor;
    private String effectBossBarStyle;
    private int effectBossBarDuration;

    // GUI lore format fields (per achievement)
    private final Map<AchievementType, java.util.List<String>> formatCompletedMap = new ConcurrentHashMap<>();
    private final Map<AchievementType, java.util.List<String>> formatInProgressMap = new ConcurrentHashMap<>();
    private final Map<AchievementType, java.util.List<String>> formatDisabledMap = new ConcurrentHashMap<>();

    // Global format texts for rewards
    private String formatRewardMoney;
    private String formatRewardExp;
    private String formatRewardConsole;

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

            // Free edition: load from bundled resource only (don't export to disk)
            boolean isFree = plugin.getEditionManager() != null && plugin.getEditionManager().isFree();
            if (isFree) {
                try (InputStream in = plugin.getResource("achievements.yml")) {
                    if (in != null) {
                        this.achievementConfig = YamlConfiguration.loadConfiguration(
                                new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                    } else {
                        this.achievementConfig = new YamlConfiguration();
                    }
                }
            } else {
                // Pro edition: export to disk for editing
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
            }
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
                        broadcastMap.put(type, sec.getBoolean("broadcast", true));

                        // Parse multi-reward list (new format)
                        // Falls back to legacy scalar "reward" for backward compatibility
                        java.util.List<String> rewards;
                        if (sec.isList("rewards")) {
                            rewards = sec.getStringList("rewards");
                        } else {
                            // Legacy: convert single money reward
                            double legacy = sec.getDouble("reward", 0);
                            rewards = new java.util.ArrayList<>();
                            if (legacy > 0)
                                rewards.add("[money] " + (long) legacy);
                        }
                        rewardsMap.put(type, rewards);

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
                        rewardsMap.put(type, new java.util.ArrayList<>());
                        broadcastMap.put(type, true);
                        iconMap.put(type, Material.GOLD_BLOCK);
                        // Formats are populated later along with the custom format checking.
                    }
                }
            }

            // Load Global Object Effects
            effectSoundEnabled = achievementConfig.getBoolean("achievements.effects.sound.enabled", true);
            effectSoundName = achievementConfig.getString("achievements.effects.sound.sound",
                    "UI_TOAST_CHALLENGE_COMPLETE");
            effectSoundVolume = (float) achievementConfig.getDouble("achievements.effects.sound.volume", 1.0);
            effectSoundPitch = (float) achievementConfig.getDouble("achievements.effects.sound.pitch", 1.0);

            effectTitleEnabled = achievementConfig.getBoolean("achievements.effects.title.enabled", true);
            effectTitleHeader = achievementConfig.getString("achievements.effects.title.header",
                    "&6&lAchievement Unlocked!");
            effectTitleSubheader = achievementConfig.getString("achievements.effects.title.subheader",
                    "&e{achievement}");
            effectTitleFadeIn = achievementConfig.getInt("achievements.effects.title.fade-in", 10);
            effectTitleStay = achievementConfig.getInt("achievements.effects.title.stay", 70);
            effectTitleFadeOut = achievementConfig.getInt("achievements.effects.title.fade-out", 20);

            effectActionBarEnabled = achievementConfig.getBoolean("achievements.effects.action-bar.enabled", true);
            effectActionBarFormat = achievementConfig.getString("achievements.effects.action-bar.format",
                    "&a\u2728 &e{achievement} &a\u2728");

            effectBossBarEnabled = achievementConfig.getBoolean("achievements.effects.bossbar.enabled", true);
            effectBossBarTitle = achievementConfig.getString("achievements.effects.bossbar.title",
                    "&6&lAchievement Unlocked: &e{achievement}");
            effectBossBarColor = achievementConfig.getString("achievements.effects.bossbar.color", "YELLOW");
            effectBossBarStyle = achievementConfig.getString("achievements.effects.bossbar.style", "SOLID");
            effectBossBarDuration = achievementConfig.getInt("achievements.effects.bossbar.duration-ticks", 80);

            // Load GUI layout formats (Global defaults)
            java.util.List<String> globalFormatCompleted = achievementConfig
                    .getStringList("achievements.format.completed");
            if (globalFormatCompleted == null || globalFormatCompleted.isEmpty()) {
                globalFormatCompleted = java.util.Arrays.asList(
                        "&7{description}", "", "&a&lCOMPLETED", "",
                        "&eProgress: &a{progress_formatted} &7/ &f{target_formatted}",
                        "&7{progress_bar} &a100%", "", "&7Unlocked: &f{completed_date}", "",
                        "&6Rewards: &7(Claimed)", "{rewards}");
            }
            java.util.List<String> globalFormatInProgress = achievementConfig
                    .getStringList("achievements.format.in-progress");
            if (globalFormatInProgress == null || globalFormatInProgress.isEmpty()) {
                globalFormatInProgress = java.util.Arrays.asList(
                        "&7{description}", "", "&eProgress: &f{progress_formatted} &7/ &f{target_formatted}",
                        "&7{progress_bar} &f{percentage}%", "", "&7Remaining: &c{remaining_formatted}", "",
                        "&6Rewards:", "{rewards}");
            }
            java.util.List<String> globalFormatDisabled = achievementConfig
                    .getStringList("achievements.format.disabled");
            if (globalFormatDisabled == null || globalFormatDisabled.isEmpty()) {
                globalFormatDisabled = java.util.Arrays.asList(
                        "&7{description}", "", "&c&lDISABLED");
            }

            // Apply formats per achievement (Check local definition, fallback to global)
            if (definitions != null) {
                for (AchievementType type : AchievementType.values()) {
                    ConfigurationSection sec = definitions.getConfigurationSection(type.getConfigKey());
                    if (sec != null && sec.isList("format.completed")) {
                        formatCompletedMap.put(type, sec.getStringList("format.completed"));
                    } else {
                        formatCompletedMap.put(type, globalFormatCompleted);
                    }

                    if (sec != null && sec.isList("format.in-progress")) {
                        formatInProgressMap.put(type, sec.getStringList("format.in-progress"));
                    } else {
                        formatInProgressMap.put(type, globalFormatInProgress);
                    }

                    if (sec != null && sec.isList("format.disabled")) {
                        formatDisabledMap.put(type, sec.getStringList("format.disabled"));
                    } else {
                        formatDisabledMap.put(type, globalFormatDisabled);
                    }
                }
            } else {
                for (AchievementType type : AchievementType.values()) {
                    formatCompletedMap.put(type, globalFormatCompleted);
                    formatInProgressMap.put(type, globalFormatInProgress);
                    formatDisabledMap.put(type, globalFormatDisabled);
                }
            }

            formatRewardMoney = achievementConfig.getString("achievements.format.reward-money", "&6  💰 {amount}");
            formatRewardExp = achievementConfig.getString("achievements.format.reward-exp", "&b  ✨ {amount} EXP");
            formatRewardConsole = achievementConfig.getString("achievements.format.reward-console",
                    "&d  🎁 Special Reward");

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

    /**
     * Returns the raw rewards list for a given achievement type.
     */
    public java.util.List<String> getAchievementRewards(AchievementType type) {
        return rewardsMap.getOrDefault(type, new java.util.ArrayList<>());
    }

    /**
     * Convenience getter: returns total money reward for display purposes.
     * Sums all [money] entries in the rewards list.
     */
    public double getAchievementReward(AchievementType type) {
        double total = 0;
        for (String entry : getAchievementRewards(type)) {
            String trimmed = entry.trim();
            if (trimmed.toLowerCase().startsWith("[money]")) {
                try {
                    total += Double.parseDouble(trimmed.substring(7).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return total;
    }

    public boolean isAchievementBroadcast(AchievementType type) {
        return broadcastMap.getOrDefault(type, true);
    }

    public java.util.List<String> getFormatCompleted(AchievementType type) {
        return formatCompletedMap.getOrDefault(type, new java.util.ArrayList<>());
    }

    public java.util.List<String> getFormatInProgress(AchievementType type) {
        return formatInProgressMap.getOrDefault(type, new java.util.ArrayList<>());
    }

    public java.util.List<String> getFormatDisabled(AchievementType type) {
        return formatDisabledMap.getOrDefault(type, new java.util.ArrayList<>());
    }

    public String getFormatRewardMoney() {
        return formatRewardMoney;
    }

    public String getFormatRewardExp() {
        return formatRewardExp;
    }

    public String getFormatRewardConsole() {
        return formatRewardConsole;
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
        if (!enabled || !isAchievementEnabled(type))
            return;

        PlayerAchievementData data = getPlayerData(playerId);
        if (data.isCompleted(type))
            return;

        data.addProgress(type, amount);
        checkCompletion(playerId, data, type);
    }

    /**
     * Set absolute progress for an achievement (used for snapshot-based
     * achievements
     * like max level or max tier research).
     *
     * @param playerId The player UUID
     * @param type     The achievement type
     * @param value    The absolute progress value
     */
    public void setProgress(UUID playerId, AchievementType type, double value) {
        if (!enabled || !isAchievementEnabled(type))
            return;

        PlayerAchievementData data = getPlayerData(playerId);
        if (data.isCompleted(type))
            return;

        data.setProgress(type, value);
        checkCompletion(playerId, data, type);
    }

    /**
     * Check if a player has met the target for an achievement and grant it if so.
     */
    private void checkCompletion(UUID playerId, PlayerAchievementData data, AchievementType type) {
        if (data.isCompleted(type))
            return;

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

        // Grant rewards
        parseAndGrantRewards(type, player);

        // Build reward summary for notification
        java.util.List<String> rewardLines = buildRewardSummary(type);

        // Notify player
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&6&l[Achievement Unlocked] &e" + achName + "&7 - " + getAchievementDescription(type)));
            for (String line : rewardLines) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6&l[Reward] &a" + line));
            }

            // Play sound, title, toast (action bar), and animated bossbar
            try {
                // --- Sound Effect ---
                if (effectSoundEnabled) {
                    try {
                        Sound sound = Sound.valueOf(effectSoundName.toUpperCase());
                        player.playSound(player.getLocation(), sound, effectSoundVolume, effectSoundPitch);
                    } catch (IllegalArgumentException ignored) {
                        plugin.getLogger().warning("[Achievement] Invalid sound name: " + effectSoundName);
                    }
                }

                // --- Title Message ---
                if (effectTitleEnabled) {
                    String header = ChatColor.translateAlternateColorCodes('&',
                            effectTitleHeader.replace("{achievement}", achName));
                    String subheader = ChatColor.translateAlternateColorCodes('&',
                            effectTitleSubheader.replace("{achievement}", achName));
                    player.sendTitle(header, subheader, effectTitleFadeIn, effectTitleStay, effectTitleFadeOut);
                }

                // --- Action Bar (Toast) ---
                if (effectActionBarEnabled) {
                    String abText = ChatColor.translateAlternateColorCodes('&',
                            effectActionBarFormat.replace("{achievement}", achName));
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent(abText));
                }

                // --- Animated BossBar ---
                if (effectBossBarEnabled) {
                    String bbTitle = ChatColor.translateAlternateColorCodes('&',
                            effectBossBarTitle.replace("{achievement}", achName));
                    org.bukkit.boss.BarColor bbColor;
                    org.bukkit.boss.BarStyle bbStyle;
                    try {
                        bbColor = org.bukkit.boss.BarColor.valueOf(effectBossBarColor.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        bbColor = org.bukkit.boss.BarColor.YELLOW;
                    }
                    try {
                        bbStyle = org.bukkit.boss.BarStyle.valueOf(effectBossBarStyle.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        bbStyle = org.bukkit.boss.BarStyle.SOLID;
                    }

                    org.bukkit.boss.BossBar bossBar = Bukkit.createBossBar(bbTitle, bbColor, bbStyle);
                    bossBar.addPlayer(player);

                    final int totalTicks = Math.max(effectBossBarDuration, 2);
                    final double step = 1.0 / (totalTicks / 2.0);

                    new org.bukkit.scheduler.BukkitRunnable() {
                        double progress = 1.0;

                        @Override
                        public void run() {
                            progress -= step;
                            if (progress <= 0 || !player.isOnline()) {
                                bossBar.removePlayer(player);
                                this.cancel();
                            } else {
                                bossBar.setProgress(progress);
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 2L);
                }
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

    /**
     * Parse and execute all rewards from the rewards list for the given
     * achievement.
     * Supported tags: [money], [exp], [console]
     */
    private void parseAndGrantRewards(AchievementType type, Player player) {
        for (String entry : getAchievementRewards(type)) {
            String trimmed = entry.trim();
            try {
                if (trimmed.toLowerCase().startsWith("[money]")) {
                    double amount = Double.parseDouble(trimmed.substring(7).trim());
                    if (amount > 0 && player != null) {
                        economy.depositPlayer(player, amount);
                    }
                } else if (trimmed.toLowerCase().startsWith("[exp]")) {
                    int exp = Integer.parseInt(trimmed.substring(5).trim());
                    if (exp > 0 && player != null) {
                        // Run on main thread (XP must be given on main thread)
                        final int xp = exp;
                        Bukkit.getScheduler().runTask(plugin, () -> player.giveExp(xp));
                    }
                } else if (trimmed.toLowerCase().startsWith("[console]")) {
                    String cmd = trimmed.substring(9).trim();
                    if (player != null) {
                        cmd = cmd.replace("%player%", player.getName());
                    }
                    final String finalCmd = cmd;
                    Bukkit.getScheduler().runTask(plugin,
                            () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
                } else {
                    plugin.getLogger().warning("[Achievement] Unknown reward type: " + trimmed);
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("[Achievement] Invalid reward value: " + trimmed);
            }
        }
    }

    /**
     * Build a human-readable list of reward descriptions for in-game notifications.
     */
    private java.util.List<String> buildRewardSummary(AchievementType type) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String entry : getAchievementRewards(type)) {
            String trimmed = entry.trim();
            try {
                if (trimmed.toLowerCase().startsWith("[money]")) {
                    double amount = Double.parseDouble(trimmed.substring(7).trim());
                    lines.add(configManager.formatMoney(amount));
                } else if (trimmed.toLowerCase().startsWith("[exp]")) {
                    int exp = Integer.parseInt(trimmed.substring(5).trim());
                    lines.add(exp + " EXP");
                } else if (trimmed.toLowerCase().startsWith("[console]")) {
                    // Don't expose raw console commands in chat
                    lines.add("Special Reward");
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return lines;
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
            if (players == null)
                return;

            for (String uuidStr : players.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    PlayerAchievementData data = new PlayerAchievementData(uuid);
                    ConfigurationSection playerSec = players.getConfigurationSection(uuidStr);
                    if (playerSec == null)
                        continue;

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
