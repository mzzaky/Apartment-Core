package com.aithor.apartmentcorei3.gui.menus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;

import com.aithor.apartmentcorei3.ApartmentCorei3;
import com.aithor.apartmentcorei3.gui.GUIManager;
import com.aithor.apartmentcorei3.gui.interfaces.GUI;
import com.aithor.apartmentcorei3.gui.items.ItemBuilder;
import com.aithor.apartmentcorei3.gui.utils.GUIUtils;
import com.aithor.apartmentcorei3.gui.menus.HelpInfoGUI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Main menu GUI - Central hub for all apartment functions
 */
public class MainMenuGUI implements GUI {
    
    private final Player player;
    private final ApartmentCorei3 plugin;
    private final GUIManager guiManager;
    private String title;
    private Inventory inventory;

    // YAML menu section and placeholder context
    private ConfigurationSection menuSection;
    private final Map<String, String> context = new HashMap<>();
    
    // Default slot positions (can be overridden by apartment_gui.yml)
    private static final int DEFAULT_MY_APARTMENTS_SLOT = 11;
    private static final int DEFAULT_BROWSE_BUY_SLOT = 13;
    private static final int DEFAULT_TAX_MANAGEMENT_SLOT = 15;
    private static final int DEFAULT_AUCTION_HOUSE_SLOT = 29;
    private static final int DEFAULT_GUESTBOOKS_SLOT = 31;
    private static final int DEFAULT_SETTINGS_SLOT = 33;
    private static final int DEFAULT_STATISTICS_SLOT = 20;
    private static final int DEFAULT_APARTMENT_SHOP_SLOT = 22;
    private static final int DEFAULT_HELP_INFO_SLOT = 24;

    // Resolved per-instance slots (read from apartment_gui.yml when present)
    private int myApartmentsSlot = DEFAULT_MY_APARTMENTS_SLOT;
    private int browseBuySlot = DEFAULT_BROWSE_BUY_SLOT;
    private int taxManagementSlot = DEFAULT_TAX_MANAGEMENT_SLOT;
    private int auctionHouseSlot = DEFAULT_AUCTION_HOUSE_SLOT;
    private int guestbooksSlot = DEFAULT_GUESTBOOKS_SLOT;
    private int settingsSlot = DEFAULT_SETTINGS_SLOT;
    private int statisticsSlot = DEFAULT_STATISTICS_SLOT;
    private int apartmentShopSlot = DEFAULT_APARTMENT_SHOP_SLOT;
    private int helpInfoSlot = DEFAULT_HELP_INFO_SLOT;
    
    public MainMenuGUI(Player player, ApartmentCorei3 plugin, GUIManager guiManager) {
        this.player = player;
        this.plugin = plugin;
        this.guiManager = guiManager;
        
        // Load menu overrides from external GUI config (apartment_gui.yml)
        this.menuSection = plugin.getConfigManager().getGuiMenuSection("main-menu");
        this.title = ChatColor.translateAlternateColorCodes('&',
                menuSection != null ? menuSection.getString("title", "&2ApartmentCore Main Menu") : "&2ApartmentCore Main Menu");
        int size = menuSection != null ? menuSection.getInt("size", 45) : 45;
        // Ensure size is a multiple of 9 and within reasonable bounds
        size = Math.max(9, Math.min(54, size));
        if (size % 9 != 0) size = ((size / 9) + 1) * 9;
        if (size > 54) size = 54;

        this.inventory = Bukkit.createInventory(null, size, this.title);

        // Resolve per-item slots from config if provided
        ConfigurationSection items = (menuSection != null) ? menuSection.getConfigurationSection("items") : null;
        if (items != null) {
            this.myApartmentsSlot = items.getInt("my-apartments.slot", DEFAULT_MY_APARTMENTS_SLOT);
            this.browseBuySlot = items.getInt("browse-buy.slot", DEFAULT_BROWSE_BUY_SLOT);
            this.taxManagementSlot = items.getInt("tax-management.slot", DEFAULT_TAX_MANAGEMENT_SLOT);
            this.auctionHouseSlot = items.getInt("auction-house.slot", DEFAULT_AUCTION_HOUSE_SLOT);
            this.guestbooksSlot = items.getInt("guestbooks.slot", DEFAULT_GUESTBOOKS_SLOT);
            this.settingsSlot = items.getInt("settings.slot", DEFAULT_SETTINGS_SLOT);
            this.statisticsSlot = items.getInt("statistics.slot", DEFAULT_STATISTICS_SLOT);
            this.apartmentShopSlot = items.getInt("apartment-shop.slot", DEFAULT_APARTMENT_SHOP_SLOT);
            this.helpInfoSlot = items.getInt("help-info.slot", DEFAULT_HELP_INFO_SLOT);
        }
    }
    
    @Override
    public void open(Player player) {
        setupInventory();
        player.openInventory(inventory);
    }
    
    private void setupInventory() {
        inventory.clear();

        // Build placeholder context for this render
        buildContext();
        
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
        addApartmentShop();
        addHelpInfo();
        
        // Add player info
        addPlayerInfo();
    }
    
    private void addBorder() {
        Material borderMat = Material.GRAY_STAINED_GLASS_PANE;
        boolean borderEnabled = true;

        if (menuSection != null) {
            ConfigurationSection bsec = menuSection.getConfigurationSection("border");
            if (bsec != null) {
                borderEnabled = bsec.getBoolean("enabled", true);
                String mat = bsec.getString("material", "GRAY_STAINED_GLASS_PANE");
                borderMat = parseMaterial(mat, Material.GRAY_STAINED_GLASS_PANE);
            }
        }

        if (!borderEnabled) return;

        ItemStack borderItem = ItemBuilder.filler(borderMat);
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
            if (leftIndex < inventory.getSize()) inventory.setItem(leftIndex, borderItem);
            if (rightIndex < inventory.getSize()) inventory.setItem(rightIndex, borderItem);
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

        item = applyItemOverrides("my-apartments", item);
        if (item != null) {
            inventory.setItem(myApartmentsSlot, item);
        }
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

        item = applyItemOverrides("browse-buy", item);
        if (item != null) {
            inventory.setItem(browseBuySlot, item);
        }
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

        item = applyItemOverrides("tax-management", item);
        if (item != null) {
            inventory.setItem(taxManagementSlot, item);
        }
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
            // For disabled state, do not apply overrides to avoid opening disabled UI incorrectly
            inventory.setItem(auctionHouseSlot, item);
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

        item = applyItemOverrides("auction-house", item);
        if (item != null) {
            inventory.setItem(auctionHouseSlot, item);
        }
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

        item = applyItemOverrides("guestbooks", item);
        if (item != null) {
            inventory.setItem(guestbooksSlot, item);
        }
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

        item = applyItemOverrides("settings", item);
        if (item != null) {
            inventory.setItem(settingsSlot, item);
        }
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

        item = applyItemOverrides("statistics", item);
        if (item != null) {
            inventory.setItem(statisticsSlot, item);
        }
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

        item = applyItemOverrides("help-info", item);
        if (item != null) {
            inventory.setItem(helpInfoSlot, item);
        }
    }
    
    private void addApartmentShop() {
        long ownedCount = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .count();
        
        // Calculate total shop investments
        double totalInvestment = 0.0;
        int totalUpgrades = 0;
        
        for (com.aithor.apartmentcorei3.Apartment a : plugin.getApartmentManager().getApartments().values()) {
            if (!player.getUniqueId().equals(a.owner)) continue;
            var shopData = plugin.getShopManager().getShopData(a.id);
            totalInvestment += shopData.getTotalMoneySpent();
            for (com.aithor.apartmentcorei3.shop.ShopItem item : com.aithor.apartmentcorei3.shop.ShopItem.values()) {
                if (shopData.getTier(item) > 0) {
                    totalUpgrades++;
                }
            }
        }
        
        Material material = ownedCount > 0 ? Material.EMERALD : Material.BARRIER;
        boolean glow = totalInvestment > 0;
        
        ItemBuilder builder = new ItemBuilder(material)
                .name("&6🛍️ Apartment Shop")
                .lore(
                    "&7Upgrade your apartments with premium amenities!",
                    "",
                    "&e📊 Shop Statistics:",
                    "&7• Owned Apartments: &f" + ownedCount,
                    "&7• Total Investment: &a" + plugin.getConfigManager().formatMoney(totalInvestment),
                    "&7• Active Upgrades: &f" + totalUpgrades,
                    "",
                    ownedCount > 0 ? "&a▶ Click to access shop" : "&c❌ You need to own apartments first!"
                );

        if (glow) {
            builder.glow();
        }

        ItemStack item = builder.build();
        item = applyItemOverrides("apartment-shop", item);
        if (item != null) {
            inventory.setItem(apartmentShopSlot, item);
        }
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
        
        if (slot == myApartmentsSlot) {
            plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openMyApartments(player));
            return;
        }
        
        if (slot == browseBuySlot) {
            plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openApartmentBrowser(player));
            return;
        }
        
        if (slot == taxManagementSlot) {
            plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openTaxManagement(player));
            return;
        }
        
        if (slot == auctionHouseSlot) {
            if (plugin.getAuctionManager() != null && plugin.getConfigManager().isAuctionEnabled()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openAuctionHouse(player));
            } else {
                GUIUtils.sendMessage(player, "&cAuction system is disabled!");
                GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            }
            return;
        }
        
        if (slot == guestbooksSlot) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                boolean hasOwned = plugin.getApartmentManager().getApartments().values().stream()
                        .anyMatch(a -> player.getUniqueId().equals(a.owner));
                if (hasOwned) {
                    guiManager.openMyApartments(player);
                } else {
                    guiManager.openApartmentBrowser(player);
                }
            });
            return;
        }
        
        if (slot == settingsSlot) {
            GUIUtils.sendMessage(player, "&eSettings GUI coming soon! Use commands for now.");
            return;
        }
        
        if (slot == statisticsSlot) {
            plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openStatistics(player));
            return;
        }
        
        if (slot == apartmentShopSlot) {
            boolean hasApartments = plugin.getApartmentManager().getApartments().values().stream()
                    .anyMatch(a -> player.getUniqueId().equals(a.owner));
            
            if (!hasApartments) {
                GUIUtils.sendMessage(player, "&cYou need to own at least one apartment to access the shop!");
                GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openMyApartments(player));
            }
            return;
        }
        
        if (slot == helpInfoSlot) {
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

    // ======================
    // Helpers for YAML config
    // ======================
    private void buildContext() {
        // Owned apartments count
        long ownedCount = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .count();
        int maxApts = plugin.getConfig().getInt("limits.max-apartments-per-player", 5);
        double totalPendingIncome = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToDouble(a -> a.pendingIncome).sum();

        long availableCount = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> a.owner == null).count();
        double cheapestPrice = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> a.owner == null)
                .mapToDouble(a -> a.price).min().orElse(0);

        double totalUnpaid = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToDouble(a -> a.getTotalUnpaid()).sum();
        long overdueCount = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .filter(a -> a.computeTaxStatus(System.currentTimeMillis()).ordinal() >= 1).count();

        int activeAuctions = (plugin.getAuctionManager() != null) ? plugin.getAuctionManager().getActiveAuctions().size() : 0;
        double commissionPct = plugin.getConfigManager().getAuctionCommission() * 100.0;

        int totalMessages = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToInt(a -> {
                    var messages = plugin.getApartmentManager().getGuestBooks().get(a.id);
                    return messages != null ? messages.size() : 0;
                }).sum();
        int guestbookMax = plugin.getConfigManager().getGuestBookMaxMessages();

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

        double shopInvestment = 0.0;
        int shopUpgrades = 0;
        for (com.aithor.apartmentcorei3.Apartment a : plugin.getApartmentManager().getApartments().values()) {
            if (!player.getUniqueId().equals(a.owner)) continue;
            var shopData = plugin.getShopManager().getShopData(a.id);
            shopInvestment += shopData.getTotalMoneySpent();
            for (com.aithor.apartmentcorei3.shop.ShopItem item : com.aithor.apartmentcorei3.shop.ShopItem.values()) {
                if (shopData.getTier(item) > 0) {
                    shopUpgrades++;
                }
            }
        }

        String taxStatus = totalUnpaid > 0 ? "&cTaxes Due!" : "&aAll Paid";

        context.clear();
        context.put("%owned%", String.valueOf(ownedCount));
        context.put("%max%", String.valueOf(maxApts));
        context.put("%pending%", plugin.getConfigManager().formatMoney(totalPendingIncome));
        context.put("%available%", String.valueOf(availableCount));
        context.put("%cheapest%", cheapestPrice > 0 ? plugin.getConfigManager().formatMoney(cheapestPrice) : "N/A");
        context.put("%tax_status%", taxStatus);
        context.put("%tax_due%", plugin.getConfigManager().formatMoney(totalUnpaid));
        context.put("%overdue_count%", String.valueOf(overdueCount));
        context.put("%active_auctions%", String.valueOf(activeAuctions));
        context.put("%auction_commission%", String.format("%.1f", commissionPct));
        context.put("%guestbook_total%", String.valueOf(totalMessages));
        context.put("%guestbook_max%", String.valueOf(guestbookMax));
        context.put("%lifetime_income%", plugin.getConfigManager().formatMoney(totalIncomeGenerated));
        context.put("%total_tax_paid%", plugin.getConfigManager().formatMoney(totalTaxPaid));
        context.put("%shop_investment%", plugin.getConfigManager().formatMoney(shopInvestment));
        context.put("%shop_upgrades%", String.valueOf(shopUpgrades));
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
            // This is a limitation, but ensures we don't lose the original display name
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