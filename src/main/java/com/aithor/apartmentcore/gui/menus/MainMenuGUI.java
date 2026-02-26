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
 * Main menu GUI - Central hub for all apartment functions
 */
public class MainMenuGUI implements GUI {

    private final Player player;
    private final ApartmentCore plugin;
    private final GUIManager guiManager;
    private final String title;
    private final Inventory inventory;

    // Slot positions
    private static final int MY_APARTMENTS_SLOT = 11;
    private static final int BROWSE_BUY_SLOT = 13;
    private static final int TAX_MANAGEMENT_SLOT = 15;
    private static final int AUCTION_HOUSE_SLOT = 20;
    private static final int STATISTICS_SLOT = 22;
    private static final int HELP_INFO_SLOT = 24;

    public MainMenuGUI(Player player, ApartmentCore plugin, GUIManager guiManager) {
        this.player = player;
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.title = ChatColor.translateAlternateColorCodes('&', "&2ApartmentCore Main Menu");
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

        // Add main menu items
        addMyApartments();
        addBrowseAndBuy();
        addTaxManagement();
        addAuctionHouse();
        addStatistics();
        addHelpInfo();

        // Add player info
        addPlayerInfo();
    }

    private void addBorder() {
        ItemStack borderItem = ItemBuilder.filler(Material.GRAY_STAINED_GLASS_PANE);
        int rows = Math.max(1, inventory.getSize() / 9);

        // Top border (first row)
        for (int i = 0; i < 9 && i < inventory.getSize(); i++) {
            inventory.setItem(i, borderItem);
        }

        // Bottom border (last row)
        int bottomStart = (rows - 1) * 9;
        for (int i = bottomStart; i < bottomStart + 9 && i < inventory.getSize(); i++) {
            inventory.setItem(i, borderItem);
        }

        // Side borders for intermediate rows
        for (int r = 1; r < rows - 1; r++) {
            int leftIndex = r * 9;
            int rightIndex = r * 9 + 8;
            if (leftIndex < inventory.getSize())
                inventory.setItem(leftIndex, borderItem);
            if (rightIndex < inventory.getSize())
                inventory.setItem(rightIndex, borderItem);
        }
    }

    private void addMyApartments() {
        long ownedCount = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .count();

        double totalPendingIncome = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToDouble(a -> a.pendingIncome)
                .sum();

        ItemStack item = new ItemBuilder(Material.DARK_OAK_DOOR)
                .name("&6🏠 My Apartments")
                .lore(
                        "&7Manage your owned apartments",
                        "",
                        "&e📊 Statistics:",
                        "&7• Owned: &f" + ownedCount + "&7/&f"
                                + plugin.getConfig().getInt("settings.max-apartments-per-player", 5),
                        "&7• Pending Income: &a" + plugin.getConfigManager().formatMoney(totalPendingIncome),
                        "",
                        "&a▶ Click to open")
                .glow()
                .build();

        inventory.setItem(MY_APARTMENTS_SLOT, item);
    }

    private void addBrowseAndBuy() {
        long availableCount = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> a.owner == null)
                .count();

        // Find cheapest apartment
        double cheapestPrice = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> a.owner == null)
                .mapToDouble(a -> a.price)
                .min()
                .orElse(0);

        ItemStack item = new ItemBuilder(Material.GOLD_INGOT)
                .name("&6🛒 Browse & Buy")
                .lore(
                        "&7Browse available apartments",
                        "",
                        "&e📊 Market Info:",
                        "&7• Available: &f" + availableCount + " &7apartments",
                        "&7• Starting from: &a"
                                + (cheapestPrice > 0 ? plugin.getConfigManager().formatMoney(cheapestPrice) : "N/A"),
                        "",
                        "&a▶ Click to browse")
                .glow()
                .build();

        inventory.setItem(BROWSE_BUY_SLOT, item);
    }

    private void addTaxManagement() {
        // Calculate total unpaid taxes
        double totalUnpaid = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToDouble(a -> a.getTotalUnpaid())
                .sum();

        // Count overdue apartments
        long overdueCount = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .filter(a -> a.computeTaxStatus(System.currentTimeMillis()).ordinal() >= 1) // OVERDUE or worse
                .count();

        Material material = totalUnpaid > 0 ? Material.RED_CONCRETE : Material.GREEN_CONCRETE;
        String statusColor = totalUnpaid > 0 ? "&c" : "&a";
        String status = totalUnpaid > 0 ? "Taxes Due!" : "All Paid";

        ItemStack item = new ItemBuilder(material)
                .name("&6💰 Tax Management")
                .lore(
                        "&7Manage your tax payments",
                        "",
                        "&e📊 Tax Status:",
                        "&7• Status: " + statusColor + status,
                        "&7• Total Due: &f" + plugin.getConfigManager().formatMoney(totalUnpaid),
                        "&7• Overdue: &f" + overdueCount + " &7apartments",
                        "",
                        "&a▶ Click to manage")
                .glow()
                .build();

        inventory.setItem(TAX_MANAGEMENT_SLOT, item);
    }

    private void addAuctionHouse() {
        if (plugin.getAuctionManager() == null || !plugin.getConfigManager().isAuctionEnabled()) {
            ItemStack item = new ItemBuilder(Material.BARRIER)
                    .name("&c🔨 Auction House")
                    .lore(
                            "&7Auction system is disabled",
                            "",
                            "&c✗ Not available")
                    .build();
            inventory.setItem(AUCTION_HOUSE_SLOT, item);
            return;
        }

        int activeAuctions = plugin.getAuctionManager().getActiveAuctions().size();

        ItemStack item = new ItemBuilder(Material.SUNFLOWER)
                .name("&6🔨 Auction House")
                .lore(
                        "&7Buy and sell apartments via auction",
                        "",
                        "&e📊 Auction Info:",
                        "&7• Active Auctions: &f" + activeAuctions,
                        "&7• Commission: &f"
                                + String.format("%.1f%%", plugin.getConfigManager().getAuctionCommission() * 100),
                        "",
                        "&a▶ Click to open")
                .glow()
                .build();

        inventory.setItem(AUCTION_HOUSE_SLOT, item);
    }

    private void addStatistics() {
        // Aggregate player stats
        long ownedCount = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .count();

        double totalPendingIncome = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToDouble(a -> a.pendingIncome)
                .sum();

        double totalUnpaidTaxes = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToDouble(a -> a.getTotalUnpaid())
                .sum();

        double totalIncomeGenerated = 0.0;
        double totalTaxPaid = 0.0;
        for (com.aithor.apartmentcore.model.Apartment a : plugin.getApartmentManager().getApartments().values()) {
            if (!player.getUniqueId().equals(a.owner))
                continue;
            var st = plugin.getApartmentManager().getStats(a.id);
            if (st != null) {
                totalIncomeGenerated += st.totalIncomeGenerated;
                totalTaxPaid += st.totalTaxPaid;
            }
        }

        ItemStack item = new ItemBuilder(Material.BOOK)
                .name("&6📊 Statistics")
                .lore(
                        "&7Your overall performance",
                        "",
                        "&e📋 Overview:",
                        "&7• Owned: &f" + ownedCount,
                        "&7• Lifetime Income: &a" + plugin.getConfigManager().formatMoney(totalIncomeGenerated),
                        "&7• Total Tax Paid: &c" + plugin.getConfigManager().formatMoney(totalTaxPaid),
                        "&7• Pending Income: &a" + plugin.getConfigManager().formatMoney(totalPendingIncome),
                        "&7• Outstanding Taxes: &c" + plugin.getConfigManager().formatMoney(totalUnpaidTaxes),
                        "",
                        "&a▶ Click to open")
                .glow()
                .build();

        inventory.setItem(STATISTICS_SLOT, item);
    }

    private void addHelpInfo() {
        ItemStack item = new ItemBuilder(Material.ENCHANTED_BOOK)
                .name("&6❓ Help & Info")
                .lore(
                        "&7Get help and information",
                        "",
                        "&e📚 Available Help:",
                        "&7• Command reference",
                        "&7• FAQ and guides",
                        "&7• Contact support",
                        "",
                        "&a▶ Click for help")
                .glow()
                .build();

        inventory.setItem(HELP_INFO_SLOT, item);
    }

    private void addPlayerInfo() {
        // Add player head in corner
        ItemStack playerHead = new ItemBuilder(Material.PLAYER_HEAD)
                .name("&6" + player.getName())
                .lore(
                        "&7ApartmentCore v" + plugin.getDescription().getVersion(),
                        "&7Economy: &f" + plugin.getEconomy().getName(),
                        "",
                        "&7Balance: &a" + plugin.getConfigManager().formatMoney(plugin.getEconomy().getBalance(player)))
                .skull(player.getName())
                .build();

        inventory.setItem(4, playerHead);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);

        if (slot == MY_APARTMENTS_SLOT) {
            plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openMyApartments(player));
            return;
        }

        if (slot == BROWSE_BUY_SLOT) {
            plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openApartmentBrowser(player));
            return;
        }

        if (slot == TAX_MANAGEMENT_SLOT) {
            plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openTaxManagement(player));
            return;
        }

        if (slot == AUCTION_HOUSE_SLOT) {
            if (plugin.getAuctionManager() != null && plugin.getConfigManager().isAuctionEnabled()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openAuctionHouse(player));
            } else {
                GUIUtils.sendMessage(player, "&cAuction system is disabled!");
                GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            }
            return;
        }

        if (slot == STATISTICS_SLOT) {
            plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openStatistics(player));
            return;
        }

        if (slot == HELP_INFO_SLOT) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                HelpInfoGUI helpInfoGUI = new HelpInfoGUI(player, plugin, guiManager);
                guiManager.openGUI(player, helpInfoGUI);
            });
            return;
        }
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