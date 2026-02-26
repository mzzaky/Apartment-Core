package com.aithor.apartmentcorei3;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;

public class MessageManager {

    private final ApartmentCorei3 plugin;
    private FileConfiguration messageConfig;
    private File messagesFile;

    public MessageManager(ApartmentCorei3 plugin) {
        this.plugin = plugin;
        saveDefaultMessages();
    }

    public void reloadMessages() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }
        messageConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Look for defaults in the jar
        InputStream defMessageStream = plugin.getResource("messages.yml");
        if (defMessageStream != null) {
            YamlConfiguration defMessages = YamlConfiguration.loadConfiguration(new InputStreamReader(defMessageStream));
            messageConfig.setDefaults(defMessages);
            messageConfig.options().copyDefaults(true);
        }
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
        String prefix = getMessageConfig().getString("format.prefix", "&6[ApartmentCore]&r ");
        message = message.replace("{prefix}", prefix);
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}