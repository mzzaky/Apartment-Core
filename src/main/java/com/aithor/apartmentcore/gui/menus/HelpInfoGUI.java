package com.aithor.apartmentcore.gui.menus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;

import com.aithor.apartmentcore.ApartmentCorei3;
import com.aithor.apartmentcore.gui.GUIManager;
import com.aithor.apartmentcore.gui.interfaces.GUI;
import com.aithor.apartmentcore.gui.items.ItemBuilder;
import com.aithor.apartmentcore.gui.utils.GUIUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Help & Information GUI - Provides helpful information about the plugin
 */
public class HelpInfoGUI implements GUI {

    private final Player player;
    private final ApartmentCorei3 plugin;
    private final GUIManager guiManager;
    private String title;
    private Inventory inventory;

    // YAML menu section and placeholder context
    private ConfigurationSection menuSection;
    private final Map<String, String> context = new HashMap<>();

    // Default slot positions (can be overridden by apartment_gui.yml)
    private static final int DEFAULT_COMMANDS_SLOT = 11;
    private static final int DEFAULT_FAQ_SLOT = 13;
    private static final int DEFAULT_FEATURES_SLOT = 15;
    private static final int DEFAULT_SUPPORT_SLOT = 29;
    private static final int DEFAULT_VERSION_SLOT = 31;
    private static final int DEFAULT_BACK_SLOT = 40;

    // Resolved per-instance slots (read from apartment_gui.yml when present)
    private int commandsSlot = DEFAULT_COMMANDS_SLOT;
    private int faqSlot = DEFAULT_FAQ_SLOT;
    private int featuresSlot = DEFAULT_FEATURES_SLOT;
    private int supportSlot = DEFAULT_SUPPORT_SLOT;
    private int versionSlot = DEFAULT_VERSION_SLOT;
    private int backSlot = DEFAULT_BACK_SLOT;

    public HelpInfoGUI(Player player, ApartmentCorei3 plugin, GUIManager guiManager) {
        this.player = player;
        this.plugin = plugin;
        this.guiManager = guiManager;

        // Load menu overrides from external GUI config (apartment_gui.yml)
        this.menuSection = plugin.getConfigManager().getGuiMenuSection("help-info");

        // Resolve title and size from YAML if available
        String title = ChatColor.DARK_GREEN + "Help & Information";
        int size = 45;

        if (menuSection != null) {
            title = ChatColor.translateAlternateColorCodes('&',
                    menuSection.getString("title", title));
            size = menuSection.getInt("size", size);
            size = Math.max(9, Math.min(54, size));
            if (size % 9 != 0) size = ((size / 9) + 1) * 9;
        }

        this.title = title;
        this.inventory = Bukkit.createInventory(null, size, this.title);

        // Resolve slot positions from config if provided
        if (menuSection != null) {
            ConfigurationSection items = menuSection.getConfigurationSection("items");
            if (items != null) {
                this.commandsSlot = items.getInt("commands.slot", DEFAULT_COMMANDS_SLOT);
                this.faqSlot = items.getInt("faq.slot", DEFAULT_FAQ_SLOT);
                this.featuresSlot = items.getInt("features.slot", DEFAULT_FEATURES_SLOT);
                this.supportSlot = items.getInt("support.slot", DEFAULT_SUPPORT_SLOT);
                this.versionSlot = items.getInt("version.slot", DEFAULT_VERSION_SLOT);
                this.backSlot = items.getInt("back.slot", DEFAULT_BACK_SLOT);
            }
        }
    }

    @Override
    public void open(Player player) {
        setupInventory();
        player.openInventory(inventory);
    }

    private void setupInventory() {
        inventory.clear();

        // Add decorative border
        addBorder();

        // Add help sections
        addCommands();
        addFAQ();
        addFeatures();
        addSupport();
        addVersion();
        addBackButton();

        // Add title item
        addTitleItem();
    }

    private void addBorder() {
        ItemStack borderItem = ItemBuilder.filler(Material.BLUE_STAINED_GLASS_PANE);

        // Top and bottom border
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, borderItem);
            inventory.setItem(i + 36, borderItem);
        }

        // Side borders
        for (int i = 9; i < 36; i += 9) {
            inventory.setItem(i, borderItem);
            inventory.setItem(i + 8, borderItem);
        }
    }

    private void addTitleItem() {
        ItemStack titleItem = new ItemBuilder(Material.ENCHANTED_BOOK)
                .name("&6&lApartmentCore Help Center")
                .lore(
                    "&7Welcome to the help center!",
                    "&7Click on any section below to learn more.",
                    "",
                    "&e💡 Tip: &7Use &f/apartmentcore help &7for command help!"
                )
                .build();

        inventory.setItem(4, titleItem);
    }

    private void addCommands() {
        ItemStack item = new ItemBuilder(Material.COMMAND_BLOCK)
                .name("&6📋 Command Reference")
                .lore(
                    "&7View all available commands",
                    "",
                    "&e📝 Main Commands:",
                    "&7• &f/apartmentcore help &7- Show help",
                    "&7• &f/apartmentcore menu &7- Open main menu",
                    "&7• &f/apartmentcore create <name> &7- Create apartment",
                    "&7• &f/apartmentcore delete <id> &7- Delete apartment",
                    "&7• &f/apartmentcore info <id> &7- View apartment info",
                    "&7• &f/apartmentcore list &7- List your apartments",
                    "&7• &f/apartmentcore browse &7- Browse available apartments",
                    "&7• &f/apartmentcore tax <id> &7- Manage taxes",
                    "&7• &f/apartmentcore guestbook <id> &7- View guestbook",
                    "&7• &f/apartmentcore shop <id> &7- Open apartment shop",
                    "",
                    "&a▶ Click to view more commands"
                )
                .glow()
                .build();

        item = applyItemOverrides("commands", item);
        if (item != null) {
            inventory.setItem(commandsSlot, item);
        }
    }

    private void addFAQ() {
        ItemStack item = new ItemBuilder(Material.BOOK)
                .name("&6❓ FAQ & Guides")
                .lore(
                    "&7Frequently asked questions",
                    "",
                    "&e❓ Common Questions:",
                    "&7• How do I buy an apartment?",
                    "&7  Use &f/apartmentcore browse &7to find available apartments",
                    "",
                    "&7• How do I earn money?",
                    "&7  Apartments generate income over time automatically",
                    "",
                    "&7• What are taxes?",
                    "&7  You must pay taxes periodically to keep your apartments",
                    "",
                    "&7• How do I upgrade my apartment?",
                    "&7  Use the shop in the main menu to buy upgrades",
                    "",
                    "&7• What happens if I don't pay taxes?",
                    "&7  Your apartment may be seized and sold at auction",
                    "",
                    "&a▶ Click for more help"
                )
                .glow()
                .build();

        item = applyItemOverrides("faq", item);
        if (item != null) {
            inventory.setItem(faqSlot, item);
        }
    }

    private void addFeatures() {
        ItemStack item = new ItemBuilder(Material.DIAMOND)
                .name("&6⭐ Plugin Features")
                .lore(
                    "&7What you can do with ApartmentCore",
                    "",
                    "&e🏠 Apartment Management:",
                    "&7• Buy, sell, and manage multiple apartments",
                    "&7• Earn passive income from your properties",
                    "&7• Pay taxes to maintain ownership",
                    "&7• Upgrade apartments with various buffs",
                    "",
                    "&e💰 Economy System:",
                    "&7• Dynamic pricing based on location and upgrades",
                    "&7• Auction house for buying/selling apartments",
                    "&7• Shop system for apartment improvements",
                    "&7• Guestbook system for visitor messages",
                    "",
                    "&e📊 Statistics & Tracking:",
                    "&7• Detailed statistics for each apartment",
                    "&7• Income and tax payment tracking",
                    "&7• Rating system based on various factors",
                    "",
                    "&a▶ Click to learn more"
                )
                .glow()
                .build();

        item = applyItemOverrides("features", item);
        if (item != null) {
            inventory.setItem(featuresSlot, item);
        }
    }

    private void addSupport() {
        ItemStack item = new ItemBuilder(Material.OAK_SIGN)
                .name("&6🆘 Support & Contact")
                .lore(
                    "&7Need help? Here's how to get support",
                    "",
                    "&e📞 Getting Help:",
                    "&7• Use this help GUI for common questions",
                    "&7• Check the command reference for commands",
                    "&7• Visit our wiki for detailed guides",
                    "",
                    "&e🐛 Reporting Issues:",
                    "&7• Contact a server administrator",
                    "&7• Provide detailed information about the problem",
                    "&7• Include any error messages you see",
                    "",
                    "&e💬 Suggestions:",
                    "&7• Server admins can forward suggestions",
                    "&7• Be specific about what you'd like to see",
                    "",
                    "&a▶ Click for contact information"
                )
                .glow()
                .build();

        item = applyItemOverrides("support", item);
        if (item != null) {
            inventory.setItem(supportSlot, item);
        }
    }

    private void addVersion() {
        ItemStack item = new ItemBuilder(Material.NAME_TAG)
                .name("&6📋 Version Information")
                .lore(
                    "&7Plugin version and information",
                    "",
                    "&e🔖 Current Version:",
                    "&7• Version: &f" + plugin.getDescription().getVersion(),
                    "&7• Build: &fApartmentCore i3",
                    "&7• Author: &fAithor",
                    "",
                    "&e📊 System Info:",
                    "&7• Economy: &f" + plugin.getEconomy().getName(),
                    "&7• Language: &fJava (Bukkit/Spigot)",
                    "",
                    "&e🔄 Updates:",
                    "&7• Check for updates regularly",
                    "&7• Backup your data before updating",
                    "&7• See changelog for new features",
                    "",
                    "&a▶ Click for more details"
                )
                .glow()
                .build();

        item = applyItemOverrides("version", item);
        if (item != null) {
            inventory.setItem(versionSlot, item);
        }
    }

    private void addBackButton() {
        ItemStack item = new ItemBuilder(Material.ARROW)
                .name("&6⬅️ Back to Main Menu")
                .lore(
                    "&7Return to the main menu",
                    "",
                    "&a▶ Click to go back"
                )
                .build();

        item = applyItemOverrides("back", item);
        if (item != null) {
            inventory.setItem(backSlot, item);
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);

        if (slot == commandsSlot) {
            showCommandsPage();
            return;
        }

        if (slot == faqSlot) {
            showFAQPage();
            return;
        }

        if (slot == featuresSlot) {
            showFeaturesPage();
            return;
        }

        if (slot == supportSlot) {
            showSupportPage();
            return;
        }

        if (slot == versionSlot) {
            GUIUtils.sendMessage(player, "&6📋 ApartmentCore v" + plugin.getDescription().getVersion() + " - Made with ❤️ by Aithor");
            return;
        }

        if (slot == backSlot) {
            plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openMainMenu(player));
            return;
        }
    }

    private void showCommandsPage() {
        GUIUtils.sendMessage(player, "&6📋 Additional Commands:");
        GUIUtils.sendMessage(player, "&7• &f/apartmentcore reload &7- Reload configuration");
        GUIUtils.sendMessage(player, "&7• &f/apartmentcore save &7- Force save all data");
        GUIUtils.sendMessage(player, "&7• &f/apartmentcore stats &7- View global statistics");
        GUIUtils.sendMessage(player, "&7• &f/apartmentcore admin &7- Admin commands (if you have permission)");
        GUIUtils.playSound(player, GUIUtils.SUCCESS_SOUND);
    }

    private void showFAQPage() {
        GUIUtils.sendMessage(player, "&6❓ More FAQ:");
        GUIUtils.sendMessage(player, "&7• Q: How often do apartments generate income?");
        GUIUtils.sendMessage(player, "&7  A: Every " + plugin.getConfig().getInt("income.interval-minutes", 30) + " minutes");
        GUIUtils.sendMessage(player, "&7• Q: Can I own multiple apartments?");
        GUIUtils.sendMessage(player, "&7  A: Yes, up to " + plugin.getConfig().getInt("limits.max-apartments-per-player", 5) + " apartments");
        GUIUtils.sendMessage(player, "&7• Q: What affects apartment ratings?");
        GUIUtils.sendMessage(player, "&7  A: Location, upgrades, maintenance, and guestbook activity");
        GUIUtils.playSound(player, GUIUtils.SUCCESS_SOUND);
    }

    private void showFeaturesPage() {
        GUIUtils.sendMessage(player, "&6⭐ More Features:");
        GUIUtils.sendMessage(player, "&7• Real-time apartment statistics");
        GUIUtils.sendMessage(player, "&7• Comprehensive tax management system");
        GUIUtils.sendMessage(player, "&7• Interactive guestbook for visitor feedback");
        GUIUtils.sendMessage(player, "&7• Advanced shop system with tiered upgrades");
        GUIUtils.sendMessage(player, "&7• Auction house for player-to-player trading");
        GUIUtils.sendMessage(player, "&7• Detailed logging and backup systems");
        GUIUtils.playSound(player, GUIUtils.SUCCESS_SOUND);
    }

    private void showSupportPage() {
        GUIUtils.sendMessage(player, "&6🆘 Support Information:");
        GUIUtils.sendMessage(player, "&7• For technical issues, contact server administrators");
        GUIUtils.sendMessage(player, "&7• Check console logs for detailed error information");
        GUIUtils.sendMessage(player, "&7• Make sure you have all required permissions");
        GUIUtils.playSound(player, GUIUtils.SUCCESS_SOUND);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public int getSize() {
        return inventory.getSize();
    }

    @Override
    public void refresh() {
        setupInventory();
    }

    @Override
    public boolean isThisInventory(Inventory inventory) {
        return this.inventory.equals(inventory);
    }

    @Override
    public void onClose(Player player) {
        // Nothing special needed on close
    }

    // ======================
    // Helpers for YAML config
    // ======================
    private void buildContext() {
        context.clear();
        context.put("%version%", plugin.getDescription().getVersion());
        context.put("%economy%", plugin.getEconomy() != null ? plugin.getEconomy().getName() : "N/A");
    }

    private ItemStack applyItemOverrides(String key, ItemStack defaultItem) {
        if (menuSection == null) return defaultItem;
        ConfigurationSection items = menuSection.getConfigurationSection("items");
        if (items == null) return defaultItem;
        ConfigurationSection sec = items.getConfigurationSection(key);
        if (sec == null) return defaultItem;

        boolean enabled = sec.getBoolean("enabled", true);
        if (!enabled) return null;

        String materialName = sec.getString("material", null);
        Material mat = materialName != null ? parseMaterial(materialName, defaultItem.getType()) : defaultItem.getType();

        String name = sec.getString("name", null);
        List<String> lore = sec.isList("lore") ? sec.getStringList("lore") : null;
        boolean glow = sec.getBoolean("glow", false);
        int customModelData = sec.getInt("custom-model-data", 0);

        // Allow partial overrides - if name/lore not provided, use default but still apply other properties
        ItemBuilder builder = new ItemBuilder(mat);

        if (name != null) {
            builder.name(colorize(replacePlaceholders(name)));
        } else {
            // Keep default name - we can't easily extract it from defaultItem, so return as-is
            return defaultItem;
        }

        if (lore != null) {
            List<String> colored = new ArrayList<>();
            for (String line : lore) {
                colored.add(colorize(replacePlaceholders(line)));
            }
            builder.lore(colored.toArray(new String[0]));
        } else {
            // Keep default lore - same limitation as name
            return defaultItem;
        }

        if (customModelData > 0) {
            builder.modelData(customModelData);
        }
        List<String> colored = new ArrayList<>();
        for (String line : lore) {
            colored.add(colorize(replacePlaceholders(line)));
        }
        builder.lore(colored.toArray(new String[0]));
        if (glow) builder.glow();
        return builder.build();
    }

    private String replacePlaceholders(String s) {
        if (s == null) return null;
        String out = s;
        for (Map.Entry<String, String> e : context.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    private String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}