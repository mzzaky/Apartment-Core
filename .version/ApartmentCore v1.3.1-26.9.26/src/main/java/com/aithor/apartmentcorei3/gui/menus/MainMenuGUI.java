package com.aithor.apartmentcorei3.gui.menus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.aithor.apartmentcorei3.ApartmentCorei3;
import com.aithor.apartmentcorei3.gui.GUIManager;
import com.aithor.apartmentcorei3.gui.interfaces.GUI;
import com.aithor.apartmentcorei3.gui.items.ItemBuilder;
import com.aithor.apartmentcorei3.gui.utils.GUIUtils;

/**
 * Main menu GUI - Central hub for all apartment functions
 */
public class MainMenuGUI implements GUI {
    
    private final Player player;
    private final ApartmentCorei3 plugin;
    private final GUIManager guiManager;
    private final String title;
    private final Inventory inventory;
    
    // Slot positions
    private static final int MY_APARTMENTS_SLOT = 11;
    private static final int BROWSE_BUY_SLOT = 13;
    private static final int TAX_MANAGEMENT_SLOT = 15;
    private static final int AUCTION_HOUSE_SLOT = 29;
    private static final int GUESTBOOKS_SLOT = 31;
    private static final int SETTINGS_SLOT = 33;
    private static final int STATISTICS_SLOT = 20;
    private static final int HELP_INFO_SLOT = 24;
    
    public MainMenuGUI(Player player, ApartmentCorei3 plugin, GUIManager guiManager) {
        this.player = player;
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.title = ChatColor.DARK_GREEN + "ApartmentCore Main Menu";
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
        addGuestbooks();
        addSettings();
        addStatistics();
        addHelpInfo();
        
        // Add player info
        addPlayerInfo();
    }
    
    private void addBorder() {
        ItemStack borderItem = ItemBuilder.filler(Material.GRAY_STAINED_GLASS_PANE);
        
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
                    "&7• Owned: &f" + ownedCount + "&7/&f" + plugin.getConfig().getInt("limits.max-apartments-per-player", 5),
                    "&7• Pending Income: &a" + plugin.getConfigManager().formatMoney(totalPendingIncome),
                    "",
                    "&a▶ Click to open"
                )
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
                    "&7• Starting from: &a" + (cheapestPrice > 0 ? plugin.getConfigManager().formatMoney(cheapestPrice) : "N/A"),
                    "",
                    "&a▶ Click to browse"
                )
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
                    "&a▶ Click to manage"
                )
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
                        "&c✗ Not available"
                    )
                    .build();
            inventory.setItem(AUCTION_HOUSE_SLOT, item);
            return;
        }
        
        int activeAuctions = plugin.getAuctionManager().getActiveAuctions().size();
        
        ItemStack item = new ItemBuilder(Material.DIAMOND_AXE)
                .name("&6🔨 Auction House")
                .lore(
                    "&7Buy and sell apartments via auction",
                    "",
                    "&e📊 Auction Info:",
                    "&7• Active Auctions: &f" + activeAuctions,
                    "&7• Commission: &f" + String.format("%.1f%%", plugin.getConfigManager().getAuctionCommission() * 100),
                    "",
                    "&a▶ Click to open"
                )
                .glow()
                .build();
        
        inventory.setItem(AUCTION_HOUSE_SLOT, item);
    }
    
    private void addGuestbooks() {
        // Count total guestbook messages for player's apartments
        int totalMessages = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToInt(a -> {
                    var messages = plugin.getApartmentManager().getGuestBooks().get(a.id);
                    return messages != null ? messages.size() : 0;
                })
                .sum();
        
        ItemStack item = new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&6📖 Guestbooks")
                .lore(
                    "&7View apartment guestbooks",
                    "",
                    "&e📊 Message Info:",
                    "&7• Total Messages: &f" + totalMessages,
                    "&7• Max per Book: &f" + plugin.getConfigManager().getGuestBookMaxMessages(),
                    "",
                    "&a▶ Click to view"
                )
                .glow()
                .build();
        
        inventory.setItem(GUESTBOOKS_SLOT, item);
    }
    
    private void addSettings() {
        ItemStack item = new ItemBuilder(Material.COMPARATOR)
                .name("&6⚙️ Settings")
                .lore(
                    "&7Configure your preferences",
                    "",
                    "&e⚙️ Available Settings:",
                    "&7• Auto-pay taxes",
                    "&7• Notification preferences",
                    "&7• Display options",
                    "",
                    "&a▶ Click to configure"
                )
                .glow()
                .build();
        
        inventory.setItem(SETTINGS_SLOT, item);
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
        for (com.aithor.apartmentcorei3.Apartment a : plugin.getApartmentManager().getApartments().values()) {
            if (!player.getUniqueId().equals(a.owner)) continue;
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
                    "&a▶ Click to open"
                )
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
                    "&a▶ Click for help"
                )
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
                    "&7Balance: &a" + plugin.getConfigManager().formatMoney(plugin.getEconomy().getBalance(player))
                )
                .skull(player.getName())
                .build();
        
        inventory.setItem(4, playerHead);
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();
        
        GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
        
        switch (slot) {
            case MY_APARTMENTS_SLOT:
                // Schedule opening next tick to avoid modifying inventories while handling the current click
                plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openMyApartments(player));
                break;
                
            case BROWSE_BUY_SLOT:
                // Schedule opening next tick
                plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openApartmentBrowser(player));
                break;
                
            case TAX_MANAGEMENT_SLOT:
                // Schedule opening next tick
                plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openTaxManagement(player));
                break;
                
            case AUCTION_HOUSE_SLOT:
                if (plugin.getAuctionManager() != null && plugin.getConfigManager().isAuctionEnabled()) {
                    // Schedule opening next tick
                    plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openAuctionHouse(player));
                } else {
                    GUIUtils.sendMessage(player, "&cAuction system is disabled!");
                    GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
                }
                break;
                
            case GUESTBOOKS_SLOT:
                // Open an appropriate GUI so the player can access guestbooks:
                // - If the player owns apartments, open "My Apartments" so they can select one to manage/view guestbooks.
                // - Otherwise open the apartment browser so they can view apartments and access guestbooks from details.
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    boolean hasOwned = plugin.getApartmentManager().getApartments().values().stream()
                            .anyMatch(a -> player.getUniqueId().equals(a.owner));
                    if (hasOwned) {
                        guiManager.openMyApartments(player);
                    } else {
                        guiManager.openApartmentBrowser(player);
                    }
                });
                break;
                
            case SETTINGS_SLOT:
                GUIUtils.sendMessage(player, "&eSettings GUI coming soon! Use commands for now.");
                break;
                
            case STATISTICS_SLOT:
                // Schedule opening next tick
                plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openStatistics(player));
                break;
                
            case HELP_INFO_SLOT:
                GUIUtils.sendMessage(player, "&eHelp system coming soon! Use &f/apartmentcore help &efor now.");
                break;
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