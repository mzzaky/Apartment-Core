package com.aithor.apartmentcore.gui.menus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.gui.GUIManager;
import com.aithor.apartmentcore.gui.interfaces.GUI;
import com.aithor.apartmentcore.gui.items.ItemBuilder;
import com.aithor.apartmentcore.gui.utils.GUIUtils;

/**
 * Help & Information GUI - Provides helpful information about the plugin
 */
public class HelpInfoGUI implements GUI {

    private final Player player;
    private final ApartmentCore plugin;
    private final GUIManager guiManager;
    private final String title;
    private final Inventory inventory;

    // Slot positions
    private static final int COMMANDS_SLOT = 11;
    private static final int FAQ_SLOT = 13;
    private static final int FEATURES_SLOT = 15;
    private static final int SUPPORT_SLOT = 29;
    private static final int VERSION_SLOT = 31;
    private static final int BACK_SLOT = 40;

    public HelpInfoGUI(Player player, ApartmentCore plugin, GUIManager guiManager) {
        this.player = player;
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.title = ChatColor.DARK_GREEN + "Help & Information";
        this.inventory = Bukkit.createInventory(null, 45, this.title);
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

        inventory.setItem(COMMANDS_SLOT, item);
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

        inventory.setItem(FAQ_SLOT, item);
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

        inventory.setItem(FEATURES_SLOT, item);
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

        inventory.setItem(SUPPORT_SLOT, item);
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

        inventory.setItem(VERSION_SLOT, item);
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

        inventory.setItem(BACK_SLOT, item);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);

        if (slot == COMMANDS_SLOT) {
            showCommandsPage();
            return;
        }

        if (slot == FAQ_SLOT) {
            showFAQPage();
            return;
        }

        if (slot == FEATURES_SLOT) {
            showFeaturesPage();
            return;
        }

        if (slot == SUPPORT_SLOT) {
            showSupportPage();
            return;
        }

        if (slot == VERSION_SLOT) {
            GUIUtils.sendMessage(player, "&6📋 ApartmentCore v" + plugin.getDescription().getVersion() + " - Made with ❤️ by Aithor");
            return;
        }

        if (slot == BACK_SLOT) {
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
        GUIUtils.sendMessage(player, "&7  A: Yes, up to " + plugin.getConfig().getInt("settings.max-apartments-per-player", 5) + " apartments");
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

}