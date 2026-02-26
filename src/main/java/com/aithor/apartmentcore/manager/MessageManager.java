package com.aithor.apartmentcore.manager;

import com.aithor.apartmentcore.ApartmentCorei3;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class MessageManager {

    private final ApartmentCorei3 plugin;
    private final ConfigManager configManager;
    private FileConfiguration messageConfig;
    private File messagesFile;

    public MessageManager(ApartmentCorei3 plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        saveDefaultMessages();
    }

    public void reloadMessages() {
        // Decide language file
        String lang = "en_US";
        try {
            if (configManager != null) {
                lang = configManager.getMessagesLanguage();
            } else if (plugin.getConfig() != null) {
                lang = plugin.getConfig().getString("messages.language", "en_US");
            }
        } catch (Throwable ignored) {}

        // Determine candidate file path
        String langFileName = lang.equalsIgnoreCase("en_US") ? "messages.yml" : ("messages_" + lang + ".yml");
        File candidate = new File(plugin.getDataFolder(), langFileName);

        // If not present in data folder, try to copy from resources
        if (!candidate.exists()) {
            try (InputStream in = plugin.getResource(langFileName)) {
                if (in != null) {
                    plugin.saveResource(langFileName, false);
                } else {
                    // Fallback to default messages.yml
                    candidate = new File(plugin.getDataFolder(), "messages.yml");
                    if (!candidate.exists()) {
                        plugin.saveResource("messages.yml", false);
                    }
                }
            } catch (Throwable ignored) {
                // Fallback safety
                candidate = new File(plugin.getDataFolder(), "messages.yml");
                if (!candidate.exists()) {
                    try { plugin.saveResource("messages.yml", false); } catch (Throwable t) {}
                }
            }
        }

        messagesFile = candidate;
        messageConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Load defaults from the corresponding resource if any
        try (InputStream defMessageStream = plugin.getResource(langFileName) != null
                ? plugin.getResource(langFileName)
                : plugin.getResource("messages.yml")) {
            if (defMessageStream != null) {
                YamlConfiguration defMessages = YamlConfiguration.loadConfiguration(new InputStreamReader(defMessageStream));
                messageConfig.setDefaults(defMessages);
                messageConfig.options().copyDefaults(true);
            }
        } catch (Throwable ignored) {}
    }

    public FileConfiguration getMessageConfig() {
        if (messageConfig == null) {
            reloadMessages();
        }
        return messageConfig;
    }

    public void saveDefaultMessages() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
    }

    public String getMessage(String path) {
        String message = getMessageConfig().getString(path);
        if (message == null) {
            return ChatColor.RED + "Missing message: " + path;
        }

        // Prefix from config.yml overrides any messages.yml format prefix
        String prefix = "&6[ApartmentCore]&r ";
        boolean useColors = true;
        try {
            if (configManager != null) {
                prefix = configManager.getMessagesPrefix();
                useColors = configManager.isMessagesUseColors();
            } else if (plugin.getConfig() != null) {
                prefix = plugin.getConfig().getString("messages.prefix", "&6[ApartmentCore]&r ");
                useColors = plugin.getConfig().getBoolean("messages.use-colors", true);
            }
        } catch (Throwable ignored) {}

        String out = message.replace("{prefix}", prefix);

        if (useColors) {
            return ChatColor.translateAlternateColorCodes('&', out);
        } else {
            // Strip common color codes if colors disabled
            return out.replaceAll("&[0-9A-FK-ORa-fk-or]", "");
        }
    }
}