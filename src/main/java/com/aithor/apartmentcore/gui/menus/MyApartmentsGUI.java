package com.aithor.apartmentcore.gui.menus;

import com.aithor.apartmentcore.model.Apartment;
import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.model.ApartmentRating;
import com.aithor.apartmentcore.model.TaxStatus;
import com.aithor.apartmentcore.gui.GUIManager;
import com.aithor.apartmentcore.gui.interfaces.PaginatedGUI;
import com.aithor.apartmentcore.gui.items.GUIItem;
import com.aithor.apartmentcore.gui.items.ItemBuilder;
import com.aithor.apartmentcore.gui.utils.GUIUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GUI for managing player's owned apartments
 */
public class MyApartmentsGUI extends PaginatedGUI {

    private final ApartmentCore plugin;
    private final GUIManager guiManager;

    // Action slots
    private static final int BACK_SLOT = 0;
    private static final int CLAIM_ALL_SLOT = 1;
    private static final int PAY_ALL_TAXES_SLOT = 2;
    private static final int TOGGLE_AUTO_PAY_SLOT = 7;

    public MyApartmentsGUI(Player player, ApartmentCore plugin, GUIManager guiManager) {
        super(player, ChatColor.DARK_GREEN + "My Apartments", 54, 28);
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @Override
    protected List<GUIItem> loadItems() {
        List<Apartment> apartments = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .sorted(Comparator.comparing(a -> a.displayName))
                .collect(Collectors.toList());

        List<GUIItem> items = new ArrayList<>();
        for (Apartment apartment : apartments) {
            items.add(createApartmentItem(apartment));
        }

        return items;
    }

    @Override
    protected void setupInventory() {
        super.setupInventory();
        addActionButtons();
    }

    private void addActionButtons() {
        // Back button
        ItemStack backItem = new ItemBuilder(Material.ARROW)
                .name("&c◀ Back to Main Menu")
                .lore("&7Return to the main menu")
                .build();
        inventory.setItem(BACK_SLOT, backItem);

        // Claim all income button
        double totalPendingIncome = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToDouble(a -> a.pendingIncome)
                .sum();

        Material claimMaterial = totalPendingIncome > 0 ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE;
        ItemStack claimAllItem = new ItemBuilder(claimMaterial)
                .name("&a💰 Claim All Income")
                .lore(
                        "&7Claim income from all apartments",
                        "",
                        "&7Total Pending: &a" + plugin.getConfigManager().formatMoney(totalPendingIncome),
                        "",
                        totalPendingIncome > 0 ? "&a▶ Click to claim all" : "&7No income to claim")
                .build();
        inventory.setItem(CLAIM_ALL_SLOT, claimAllItem);

        // Pay all taxes button
        double totalUnpaidTaxes = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToDouble(a -> a.getTotalUnpaid())
                .sum();

        Material taxMaterial = totalUnpaidTaxes > 0 ? Material.RED_CONCRETE : Material.GREEN_CONCRETE;
        ItemStack payAllTaxesItem = new ItemBuilder(taxMaterial)
                .name("&c📋 Pay All Taxes")
                .lore(
                        "&7Pay all outstanding tax bills",
                        "",
                        "&7Total Due: &c" + plugin.getConfigManager().formatMoney(totalUnpaidTaxes),
                        "&7Your Balance: &a"
                                + plugin.getConfigManager().formatMoney(plugin.getEconomy().getBalance(player)),
                        "",
                        totalUnpaidTaxes > 0 ? "&a▶ Click to pay all" : "&7No taxes due")
                .build();
        inventory.setItem(PAY_ALL_TAXES_SLOT, payAllTaxesItem);

        // Auto-pay toggle
        boolean hasAutoPayEnabled = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .anyMatch(a -> a.autoTaxPayment);

        Material autoPayMaterial = hasAutoPayEnabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        ItemStack autoPayItem = new ItemBuilder(autoPayMaterial)
                .name("&6⚙️ Auto-Pay Taxes")
                .lore(
                        "&7Toggle auto-payment for all apartments",
                        "",
                        "&7Status: " + (hasAutoPayEnabled ? "&aEnabled" : "&cDisabled"),
                        "&7Auto-pay will attempt to pay taxes",
                        "&7automatically when they are due",
                        "",
                        "&a▶ Click to toggle")
                .build();
        inventory.setItem(TOGGLE_AUTO_PAY_SLOT, autoPayItem);
    }

    private GUIItem createApartmentItem(Apartment apartment) {
        long now = System.currentTimeMillis();
        TaxStatus taxStatus = apartment.computeTaxStatus(now);
        double totalUnpaid = apartment.getTotalUnpaid();

        // Determine status display
        String statusDisplay;
        Material statusMaterial;
        List<String> statusLore = new ArrayList<>();

        switch (taxStatus) {
            case ACTIVE:
                statusDisplay = "&a✅ Active";
                statusMaterial = Material.DIAMOND;
                statusLore.add("&7This apartment is fully functional");
                break;
            case OVERDUE:
                statusDisplay = "&6⚠️ Tax Due";
                statusMaterial = Material.GOLD_BLOCK;
                statusLore.add("&7Taxes are overdue - income stopped");
                statusLore.add("&7Due amount: &c" + plugin.getConfigManager().formatMoney(totalUnpaid));
                break;
            case INACTIVE:
                statusDisplay = "&c❌ Inactive";
                statusMaterial = Material.RED_CONCRETE;
                statusLore.add("&7Apartment is inactive - all functions disabled");
                statusLore.add("&7Due amount: &c" + plugin.getConfigManager().formatMoney(totalUnpaid));
                break;
            case REPOSSESSION:
                statusDisplay = "&4💀 Repossession";
                statusMaterial = Material.BARRIER;
                statusLore.add("&7This apartment will be repossessed soon!");
                statusLore.add("&7Due amount: &c" + plugin.getConfigManager().formatMoney(totalUnpaid));
                break;
            default:
                statusDisplay = "&7Unknown";
                statusMaterial = Material.STONE;
                break;
        }

        // Check for custom icon
        if (apartment.icon != null && !apartment.icon.isEmpty()) {
            try {
                Material customMaterial = Material.valueOf(apartment.icon.toUpperCase());
                statusMaterial = customMaterial;
            } catch (IllegalArgumentException e) {
                // Invalid material, keep default status material
            }
        }

        // Get rating info
        ApartmentRating rating = plugin.getApartmentManager().getRating(apartment.id);
        String ratingDisplay = rating != null && rating.ratingCount > 0
                ? String.format("%.1f⭐ (%d reviews)", rating.getAverageRating(), rating.ratingCount)
                : "No ratings yet";

        // Get shop info
        var shopData = plugin.getShopManager().getShopData(apartment.id);
        int activeUpgrades = 0;
        for (com.aithor.apartmentcore.shop.ShopItem item : com.aithor.apartmentcore.shop.ShopItem.values()) {
            if (shopData.getTier(item) > 0) {
                activeUpgrades++;
            }
        }

        long intervalMs = Math.max(1000L, plugin.getConfigManager().getIncomeGenerationInterval() * 50L);
        long lastGen = plugin.getLastIncomeGenerationTime();
        boolean canGenerateIncome = apartment.canGenerateIncome(now);
        String nextIncomeDisplay;
        if (!canGenerateIncome) {
            // Income is blocked due to tax status — don't show a countdown
            nextIncomeDisplay = null;
        } else if (lastGen <= 0) {
            // Task hasn't fired yet since startup — show full interval
            nextIncomeDisplay = GUIUtils.formatTime(intervalMs);
        } else {
            long nextIncomeMs = lastGen + intervalMs;
            long remainingMs = nextIncomeMs - now;
            nextIncomeDisplay = remainingMs > 0 ? GUIUtils.formatTime(remainingMs) : "Soon...";
        }

        // Tax countdown — use real-time milliseconds, consistent with tickTaxInvoices()
        long taxIntervalMs = Math.max(1000L, plugin.getConfigManager().getTaxGenerationInterval() * 50L);
        String nextTaxDisplay;
        if (apartment.lastInvoiceAt <= 0) {
            // No invoice issued yet — show full interval
            nextTaxDisplay = GUIUtils.formatTime(taxIntervalMs);
        } else {
            long nextTaxMs = apartment.lastInvoiceAt + taxIntervalMs;
            long remainingTaxMs = nextTaxMs - now;
            nextTaxDisplay = remainingTaxMs > 0 ? GUIUtils.formatTime(remainingTaxMs) : "Soon...";
        }

        // Get config and capacity
        double baseCapacity = plugin.getConfigManager().getIncomeCapacity(apartment.level);
        double researchBonusPercentage = 0.0;
        if (plugin.getResearchManager() != null && apartment.owner != null) {
            researchBonusPercentage = plugin.getResearchManager().getIncomeCapacityBonus(apartment.owner);
        }
        double shopBonusPercentage = 0.0;
        if (plugin.getShopManager() != null) {
            shopBonusPercentage = plugin.getShopManager().getIncomeCapacityBonusPercentage(apartment.id);
        }
        double totalBonusPercentage = researchBonusPercentage + shopBonusPercentage;
        double capacity = baseCapacity * (1.0 + (totalBonusPercentage / 100.0));

        // Get research buffs for display
        double capitalGrowthBonus = 0.0;
        double revenueAccelerationBonus = 0.0;
        if (plugin.getResearchManager() != null && apartment.owner != null) {
            capitalGrowthBonus = plugin.getResearchManager().getIncomeAmountBonus(apartment.owner);
            revenueAccelerationBonus = plugin.getResearchManager().getIncomeIntervalReduction(apartment.owner);
        }

        // Build item lore
        List<String> lore = new ArrayList<>();
        lore.add("&7ID: &f" + apartment.id);
        lore.add("&7Level: &f" + apartment.level + "/5");
        lore.add("&7Floor: &f" + apartment.floor);
        lore.add("&7Height: &f" + apartment.height);
        lore.add("");
        lore.add("&e💰 Financial Info:");
        lore.add("&7• Pending Income: &a" + plugin.getConfigManager().formatMoney(apartment.pendingIncome) +
                " &7/ &a" + plugin.getConfigManager().formatMoney(capacity));
        if (!canGenerateIncome) {
            // Income is blocked — show status-specific warning message
            String blockedReason;
            switch (taxStatus) {
                case OVERDUE:
                    blockedReason = "&c⚠ Income halted (taxes overdue)";
                    break;
                case INACTIVE:
                    blockedReason = "&4✖ Income halted (apartment inactive)";
                    break;
                case REPOSSESSION:
                    blockedReason = "&4💀 Income halted (repossession imminent)";
                    break;
                default:
                    blockedReason = "&cIncome halted";
                    break;
            }
            lore.add("&7• Next Income In: " + blockedReason);
        } else {
            String nextIncomeLine = "&7• Next Income In: &a" + nextIncomeDisplay;
            if (capitalGrowthBonus > 0 || revenueAccelerationBonus > 0) {
                nextIncomeLine += " &7(";
                if (capitalGrowthBonus > 0) {
                    nextIncomeLine += "&a+" + String.format("%.0f%%", capitalGrowthBonus) + " &7CG";
                }
                if (capitalGrowthBonus > 0 && revenueAccelerationBonus > 0) {
                    nextIncomeLine += "&7, ";
                }
                if (revenueAccelerationBonus > 0) {
                    nextIncomeLine += "&a-" + String.format("%.0f%%", revenueAccelerationBonus) + " &7RA";
                }
                nextIncomeLine += "&7)";
            }
            lore.add(nextIncomeLine);
        }
        lore.add("&7• Next Tax In: &c" + nextTaxDisplay);
        lore.add("&7• Outstanding Taxes: &c" + plugin.getConfigManager().formatMoney(totalUnpaid));
        lore.add("&7• Auto-pay: " + (apartment.autoTaxPayment ? "&aEnabled" : "&cDisabled"));
        lore.add("");
        lore.add("&e🛍️ Shop Info:");
        lore.add("&7• Active Upgrades: &f" + activeUpgrades + "&7/&f5");
        lore.add("&7• Total Investment: &a" + plugin.getConfigManager().formatMoney(shopData.getTotalMoneySpent()));
        lore.add("");
        lore.add("&e📊 Status: " + statusDisplay);
        lore.addAll(statusLore);
        lore.add("");
        lore.add("&e⭐ Rating: &f" + ratingDisplay);
        lore.add("");
        lore.add("&a▶ Left-click for details");
        lore.add("&a▶ Right-click for shop");
        lore.add("&a▶ Shift+click to teleport");

        ItemStack item = new ItemBuilder(statusMaterial)
                .name("&6🏠 " + apartment.displayName)
                .lore(lore)
                .build();

        return new GUIItem(item, apartment.id, apartment);
    }

    @Override
    protected void handleItemClick(GUIItem item, InventoryClickEvent event) {
        Apartment apartment = item.getData(Apartment.class);
        if (apartment == null)
            return;

        if (event.isShiftClick()) {
            // Teleport to apartment
            handleTeleport(apartment);
        } else if (event.isRightClick()) {
            // Open shop for this apartment
            handleShopAccess(apartment);
        } else {
            // View details (open next tick to avoid inventory modification during click
            // processing)
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> guiManager.openApartmentDetails(player, apartment.id));
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == BACK_SLOT) {
            guiManager.openMainMenu(player);
            return;
        }

        if (slot == CLAIM_ALL_SLOT) {
            handleClaimAll();
            return;
        }

        if (slot == PAY_ALL_TAXES_SLOT) {
            handlePayAllTaxes();
            return;
        }

        if (slot == TOGGLE_AUTO_PAY_SLOT) {
            handleToggleAutoPay();
            return;
        }

        // Handle pagination and items
        super.handleClick(event);
    }

    private void handleTeleport(Apartment apartment) {
        if (apartment.inactive && !apartment.canGenerateIncome(System.currentTimeMillis())) {
            GUIUtils.sendMessage(player, "&cThis apartment is inactive and cannot be used!");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }

        player.closeInventory();
        plugin.getApartmentManager().teleportToApartment(player, apartment.id, false);
    }

    private void handleShopAccess(Apartment apartment) {
        // Check permissions
        if (!player.hasPermission("apartmentcore.shop.view")) {
            GUIUtils.sendMessage(player, "&cYou don't have permission to access the shop!");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }

        // Open shop GUI for this apartment
        plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openApartmentShop(player, apartment.id));
    }

    private void handleClaimAll() {
        player.closeInventory();

        // Calculate total income to claim
        double totalIncome = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToDouble(a -> a.pendingIncome)
                .sum();

        if (totalIncome <= 0) {
            GUIUtils.sendMessage(player, "&cNo income to claim!");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }

        // Claim income from all apartments
        int claimedCount = 0;
        for (Apartment apartment : plugin.getApartmentManager().getApartments().values()) {
            if (player.getUniqueId().equals(apartment.owner) && apartment.pendingIncome > 0) {
                plugin.getEconomy().depositPlayer(player, apartment.pendingIncome);
                apartment.pendingIncome = 0;
                claimedCount++;
            }
        }

        plugin.getApartmentManager().saveApartments();

        GUIUtils.sendMessage(player, "&aClaimed &f" + plugin.getConfigManager().formatMoney(totalIncome) +
                " &afrom &f" + claimedCount + " &aapartments!");
        GUIUtils.playSound(player, GUIUtils.SUCCESS_SOUND);
    }

    private void handlePayAllTaxes() {
        player.closeInventory();

        // Use existing tax pay command
        plugin.getServer().dispatchCommand(player, "apartmentcore tax pay");
    }

    private void handleToggleAutoPay() {
        // Toggle auto-pay for all apartments
        boolean newState = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .noneMatch(a -> a.autoTaxPayment); // If none have auto-pay, enable for all

        int changedCount = 0;
        for (Apartment apartment : plugin.getApartmentManager().getApartments().values()) {
            if (player.getUniqueId().equals(apartment.owner)) {
                apartment.autoTaxPayment = newState;
                changedCount++;
            }
        }

        plugin.getApartmentManager().saveApartments();

        String status = newState ? "enabled" : "disabled";
        GUIUtils.sendMessage(player, "&aAuto-pay has been &f" + status + " &afor &f" + changedCount + " &aapartments!");
        GUIUtils.playSound(player, GUIUtils.SUCCESS_SOUND);

        refresh(); // Refresh the GUI to show updated status
    }
}