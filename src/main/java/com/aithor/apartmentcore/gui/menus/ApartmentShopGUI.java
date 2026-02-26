package com.aithor.apartmentcore.gui.menus;

import com.aithor.apartmentcore.model.Apartment;
import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.gui.GUIManager;
import com.aithor.apartmentcore.gui.interfaces.GUI;
import com.aithor.apartmentcore.gui.items.ItemBuilder;
import com.aithor.apartmentcore.gui.utils.GUIUtils;
import com.aithor.apartmentcore.shop.ApartmentShopData;
import com.aithor.apartmentcore.shop.ApartmentShopManager;
import com.aithor.apartmentcore.shop.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for apartment shop system - allows players to purchase upgrades for their apartments
 */
public class ApartmentShopGUI implements GUI {
    
    private final Player player;
    private final ApartmentCore plugin;
    private final GUIManager guiManager;
    private final String apartmentId;
    private final String title;
    private final Inventory inventory;
    
    // Slot positions for shop items
    private static final int PREMIUM_KITCHEN_SLOT = 10;
    private static final int LUXURY_FURNITURE_SLOT = 12;
    private static final int SOLAR_PANEL_SLOT = 14;
    private static final int HIGH_SPEED_INTERNET_SLOT = 16;
    private static final int EXTRA_LIVING_ROOM_SLOT = 28;
    
    // Other slots
    private static final int BACK_SLOT = 40;
    private static final int INFO_SLOT = 4;
    private static final int STATS_SLOT = 22;
    
    public ApartmentShopGUI(Player player, ApartmentCore plugin, GUIManager guiManager, String apartmentId) {
        this.player = player;
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.apartmentId = apartmentId;
        
        Apartment apartment = plugin.getApartmentManager().getApartment(apartmentId);
        String apartmentName = apartment != null ? apartment.displayName : apartmentId;
        
        this.title = ChatColor.GOLD + "🛍️ " + apartmentName + " - Shop";
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
        
        // Add apartment info
        addApartmentInfo();
        
        // Add shop items
        addShopItems();
        
        // Add stats and navigation
        addStats();
        addBackButton();
    }
    
    private void addBorder() {
        ItemStack borderItem = ItemBuilder.filler(Material.CYAN_STAINED_GLASS_PANE);
        
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
    
    private void addApartmentInfo() {
        Apartment apartment = plugin.getApartmentManager().getApartment(apartmentId);
        if (apartment == null) return;
        
        ApartmentShopData shopData = plugin.getShopManager().getShopData(apartmentId);
        
        ItemStack item = new ItemBuilder(Material.DIAMOND)
                .name("&6🏠 " + apartment.displayName)
                .lore(
                    "&7Apartment Shop System",
                    "&7Upgrade your apartment with premium amenities!",
                    "",
                    "&e💰 Shop Statistics:",
                    "&7• Total Spent: &a" + plugin.getConfigManager().formatMoney(shopData.getTotalMoneySpent()),
                    "&7• Active Upgrades: &f" + getActiveUpgradeCount(shopData) + "&7/&f5",
                    "",
                    "&a✨ Each upgrade provides unique buffs to your apartment!"
                )
                .glow()
                .build();
        
        inventory.setItem(INFO_SLOT, item);
    }
    
    private void addShopItems() {
        ApartmentShopData shopData = plugin.getShopManager().getShopData(apartmentId);
        
        // Premium Kitchen
        addShopItem(ShopItem.PREMIUM_KITCHEN, PREMIUM_KITCHEN_SLOT, shopData);
        
        // Luxury Furniture
        addShopItem(ShopItem.LUXURY_FURNITURE, LUXURY_FURNITURE_SLOT, shopData);
        
        // Solar Panel System
        addShopItem(ShopItem.SOLAR_PANEL_SYSTEM, SOLAR_PANEL_SLOT, shopData);
        
        // High Speed Internet
        addShopItem(ShopItem.HIGH_SPEED_INTERNET, HIGH_SPEED_INTERNET_SLOT, shopData);
        
        // Extra Living Room
        addShopItem(ShopItem.EXTRA_LIVING_ROOM, EXTRA_LIVING_ROOM_SLOT, shopData);
    }
    
    private void addShopItem(ShopItem item, int slot, ApartmentShopData shopData) {
        int currentTier = shopData.getTier(item);
        boolean canUpgrade = shopData.canUpgrade(item);
        double upgradeCost = canUpgrade ? shopData.getUpgradeCost(item) : 0.0;
        boolean canAfford = plugin.getEconomy().has(player, upgradeCost);
        
        List<String> lore = new ArrayList<>();
        lore.add("&7" + item.getDescription());
        lore.add("");
        
        // Current status
        if (currentTier == 0) {
            lore.add("&c❌ Not Purchased");
        } else {
            lore.add("&a✅ Current Tier: &f" + currentTier + "&7/&f" + item.getMaxTier());
            lore.add("&e⭐ Current Buff: &f" + item.getBuffDescription(currentTier));
        }
        
        lore.add("");
        
        // Upgrade info
        if (canUpgrade) {
            int nextTier = currentTier + 1;
            lore.add("&6🔧 Next Upgrade (Tier " + nextTier + "):");
            lore.add("&e⭐ New Buff: &f" + item.getBuffDescription(nextTier));
            lore.add("&e💰 Cost: &f" + plugin.getConfigManager().formatMoney(upgradeCost));
            lore.add("");
            
            if (canAfford) {
                lore.add("&a▶ Click to upgrade!");
            } else {
                lore.add("&c❌ Insufficient funds!");
                lore.add("&7Need: &c" + plugin.getConfigManager().formatMoney(upgradeCost - plugin.getEconomy().getBalance(player)) + " &7more");
            }
        } else {
            lore.add("&a🌟 MAX TIER REACHED!");
            lore.add("&7This item cannot be upgraded further.");
        }
        
        // Determine material and effects
        Material material = item.getIcon();
        boolean glow = currentTier > 0;
        
        // Add tier indicators with different colors
        if (currentTier > 0) {
            switch (currentTier) {
                case 1 -> material = Material.IRON_INGOT;
                case 2 -> material = Material.GOLD_INGOT;
                case 3 -> material = Material.EMERALD;
                case 4 -> material = Material.DIAMOND;
                case 5 -> material = Material.NETHERITE_INGOT;
                default -> material = item.getIcon();
            }
        }
        
        ItemBuilder builder = new ItemBuilder(material)
                .name("&6" + item.getDisplayName())
                .lore(lore);
        
        if (glow) {
            builder.glow();
        }
        
        inventory.setItem(slot, builder.build());
    }
    
    private void addStats() {
        ApartmentShopData shopData = plugin.getShopManager().getShopData(apartmentId);
        
        List<String> lore = new ArrayList<>();
        lore.add("&7Current apartment buffs from shop items:");
        lore.add("");
        
        // Show all active buffs
        double incomeBonus = plugin.getShopManager().getIncomeBonusPercentage(apartmentId);
        if (incomeBonus > 0) {
            lore.add("&e📈 Income Bonus: &a+" + String.format("%.1f%%", incomeBonus));
        }
        
        double baseIncome = plugin.getShopManager().getBaseIncomeBonus(apartmentId);
        if (baseIncome > 0) {
            lore.add("&e💵 Base Income: &a+" + plugin.getConfigManager().formatMoney(baseIncome));
        }
        
        double taxReduction = plugin.getShopManager().getTaxReductionPercentage(apartmentId);
        if (taxReduction > 0) {
            lore.add("&e🌞 Tax Reduction: &a-" + String.format("%.1f%%", taxReduction));
        }
        
        double incomeSpeed = plugin.getShopManager().getIncomeSpeedBonus(apartmentId);
        if (incomeSpeed > 0) {
            lore.add("&e⚡ Income Speed: &a+" + String.format("%.0f", incomeSpeed) + " ticks faster");
        }
        
        int maxMessages = plugin.getShopManager().getMaxMessagesBonus(apartmentId);
        if (maxMessages > 0) {
            lore.add("&e📖 Extra Messages: &a+" + maxMessages + " max messages");
        }
        
        if (incomeBonus == 0 && baseIncome == 0 && taxReduction == 0 && incomeSpeed == 0 && maxMessages == 0) {
            lore.add("&7No active buffs. Purchase items to get buffs!");
        }
        
        lore.add("");
        lore.add("&e💰 Total Investment: &a" + plugin.getConfigManager().formatMoney(shopData.getTotalMoneySpent()));
        lore.add("&e💵 Refund Value: &a" + plugin.getConfigManager().formatMoney(shopData.getRefundAmount()));
        lore.add("&7(50% refund when apartment is sold)");
        
        ItemStack item = new ItemBuilder(Material.BOOK)
                .name("&6📊 Current Buffs")
                .lore(lore)
                .glow()
                .build();
        
        inventory.setItem(STATS_SLOT, item);
    }
    
    private void addBackButton() {
        ItemStack item = new ItemBuilder(Material.ARROW)
                .name("&c⬅ Back")
                .lore("&7Click to go back")
                .build();
        
        inventory.setItem(BACK_SLOT, item);
    }
    
    private int getActiveUpgradeCount(ApartmentShopData shopData) {
        int count = 0;
        for (ShopItem item : ShopItem.values()) {
            if (shopData.getTier(item) > 0) {
                count++;
            }
        }
        return count;
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();
        
        GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
        
        // Handle back button
        if (slot == BACK_SLOT) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                guiManager.openMyApartments(player);
            });
            return;
        }
        
        // Handle shop item clicks
        ShopItem clickedItem = getShopItemFromSlot(slot);
        if (clickedItem != null) {
            handleShopItemClick(clickedItem);
            return;
        }
    }
    
    private ShopItem getShopItemFromSlot(int slot) {
        return switch (slot) {
            case PREMIUM_KITCHEN_SLOT -> ShopItem.PREMIUM_KITCHEN;
            case LUXURY_FURNITURE_SLOT -> ShopItem.LUXURY_FURNITURE;
            case SOLAR_PANEL_SLOT -> ShopItem.SOLAR_PANEL_SYSTEM;
            case HIGH_SPEED_INTERNET_SLOT -> ShopItem.HIGH_SPEED_INTERNET;
            case EXTRA_LIVING_ROOM_SLOT -> ShopItem.EXTRA_LIVING_ROOM;
            default -> null;
        };
    }
    
    private void handleShopItemClick(ShopItem item) {
        // Check permissions
        if (!player.hasPermission("apartmentcore.shop.buy")) {
            GUIUtils.sendMessage(player, "&cYou don't have permission to purchase shop items!");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }
        
        // Validate apartment ownership
        Apartment apartment = plugin.getApartmentManager().getApartment(apartmentId);
        if (apartment == null || !player.getUniqueId().equals(apartment.owner)) {
            GUIUtils.sendMessage(player, "&cYou don't own this apartment!");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }
        
        // Process purchase
        ApartmentShopManager.PurchaseResult result = plugin.getShopManager().purchaseUpgrade(player, apartmentId, item);
        
        if (result.isSuccess()) {
            GUIUtils.sendMessage(player, "&a" + result.getMessage());
            GUIUtils.playSound(player, GUIUtils.SUCCESS_SOUND);
            
            // Refresh GUI to show updated tiers
            plugin.getServer().getScheduler().runTask(plugin, this::refresh);
        } else {
            GUIUtils.sendMessage(player, "&c" + result.getMessage());
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
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