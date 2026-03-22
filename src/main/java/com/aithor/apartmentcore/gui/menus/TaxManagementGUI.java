package com.aithor.apartmentcore.gui.menus;

import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.model.Apartment;
import com.aithor.apartmentcore.model.ApartmentStats;
import com.aithor.apartmentcore.model.TaxInvoice;
import com.aithor.apartmentcore.model.TaxStatus;
import com.aithor.apartmentcore.gui.GUIManager;
import com.aithor.apartmentcore.gui.interfaces.PaginatedGUI;
import com.aithor.apartmentcore.gui.items.GUIItem;
import com.aithor.apartmentcore.gui.items.ItemBuilder;
import com.aithor.apartmentcore.gui.utils.GUIUtils;
import com.aithor.apartmentcore.manager.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GUI for managing tax payments and viewing tax information
 */
public class TaxManagementGUI extends PaginatedGUI {

    private final ApartmentCore plugin;
    private final GUIManager guiManager;

    // Action slots
    private static final int BACK_SLOT = 0;
    private static final int PAY_ALL_SLOT = 1;
    private static final int AUTO_PAY_TOGGLE_SLOT = 2;
    private static final int TAX_INFO_SLOT = 7;

    public TaxManagementGUI(Player player, ApartmentCore plugin, GUIManager guiManager) {
        super(player, ChatColor.DARK_RED + "Tax Management", 54, 28);
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @Override
    protected List<GUIItem> loadItems() {
        List<Apartment> apartments = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .filter(a -> a.getTotalUnpaid() > 0) // Only show apartments with unpaid taxes
                .sorted(Comparator.comparing((Apartment a) -> a.computeTaxStatus(System.currentTimeMillis()).ordinal())
                        .reversed()) // Most urgent first
                .collect(Collectors.toList());

        List<GUIItem> items = new ArrayList<>();

        // Add apartments with unpaid taxes
        for (Apartment apartment : apartments) {
            items.add(createApartmentTaxItem(apartment));
        }

        // Add individual tax invoices for detailed view
        for (Apartment apartment : apartments) {
            if (apartment.taxInvoices != null) {
                for (TaxInvoice invoice : apartment.taxInvoices) {
                    if (!invoice.isPaid()) {
                        items.add(createInvoiceItem(apartment, invoice));
                    }
                }
            }
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

        // Calculate totals
        double totalUnpaid = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToDouble(a -> a.getTotalUnpaid())
                .sum();

        boolean canAfford = plugin.getEconomy().has(player, totalUnpaid);

        // Pay All button
        Material payAllMaterial = totalUnpaid > 0 ? (canAfford ? Material.EMERALD_BLOCK : Material.RED_CONCRETE)
                : Material.GRAY_CONCRETE;
        ItemStack payAllItem = new ItemBuilder(payAllMaterial)
                .name("&a💳 Pay All Taxes")
                .lore(
                        "&7Pay all outstanding tax bills",
                        "",
                        "&7Total Due: &c" + plugin.getConfigManager().formatMoney(totalUnpaid),
                        "&7Your Balance: &a"
                                + plugin.getConfigManager().formatMoney(plugin.getEconomy().getBalance(player)),
                        "",
                        totalUnpaid > 0 ? (canAfford ? "&a▶ Click to pay all" : "&cInsufficient funds")
                                : "&7No taxes due")
                .glow()
                .build();
        inventory.setItem(PAY_ALL_SLOT, payAllItem);

        // Auto-pay toggle
        boolean isProActive = plugin.getEditionManager().isProActive();
        boolean isFeatureEnabled = plugin.getEditionManager().isAutoTaxPaymentEnabled();
        boolean hasAutoPayEnabled = isFeatureEnabled && plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .anyMatch(a -> a.autoTaxPayment);

        long apartmentCount = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .count();

        ItemStack autoPayItem;
        if (!isProActive) {
            // Free edition — show locked button
            autoPayItem = new ItemBuilder(Material.GRAY_CONCRETE)
                    .name("&7⚙️ Auto-Pay Settings &8[PRO]")
                    .lore(
                            "&7Automatically pay tax invoices",
                            "&7from your balance when they are due.",
                            "",
                            "&c🔒 This feature is only available",
                            "&cin &6ApartmentCore Pro&c.",
                            "",
                            "&7Get Pro at: &bhttps://pasman.io/apartmentcore")
                    .build();
        } else if (!isFeatureEnabled) {
            // Disabled by admin config
            autoPayItem = new ItemBuilder(Material.BARRIER)
                    .name("&c⚙️ Auto-Pay Settings &8[DISABLED]")
                    .lore(
                            "&7Automatically pay tax invoices",
                            "&7from your balance when they are due.",
                            "",
                            "&c🔒 This feature has been disabled",
                            "&cby the server administrator.")
                    .build();
        } else {
            Material autoPayMaterial = hasAutoPayEnabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
            autoPayItem = new ItemBuilder(autoPayMaterial)
                    .name("&6⚙️ Auto-Pay Settings")
                    .lore(
                            "&7Toggle automatic tax payment",
                            "",
                            "&7Current Status: " + (hasAutoPayEnabled ? "&aEnabled" : "&cDisabled"),
                            "&7Apartments: &f" + apartmentCount,
                            "",
                            "&7Auto-pay attempts to pay taxes",
                            "&7automatically when due if you",
                            "&7have sufficient balance.",
                            "",
                            "&a▶ Click to toggle all apartments")
                    .build();
        }
        inventory.setItem(AUTO_PAY_TOGGLE_SLOT, autoPayItem);

        // Tax Information
        long overdueCount = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .filter(a -> a.computeTaxStatus(System.currentTimeMillis()).ordinal() >= 1) // OVERDUE or worse
                .count();

        long totalInvoices = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .filter(a -> a.taxInvoices != null)
                .mapToLong(a -> a.taxInvoices.stream().filter(inv -> !inv.isPaid()).count())
                .sum();

        // Get tax efficiency research bonus for overview
        double taxEfficiencyBonus = 0.0;
        if (plugin.getResearchManager() != null) {
            taxEfficiencyBonus = plugin.getResearchManager().getTaxReduction(player.getUniqueId());
        }

        // Get average shop tax reduction bonus for overview
        double totalShopTaxBuff = 0.0;
        int apartmentsWithShopBuff = 0;
        for (Apartment app : plugin.getApartmentManager().getApartments().values()) {
            if (player.getUniqueId().equals(app.owner)) {
                if (plugin.getShopManager() != null) {
                    double buff = plugin.getShopManager().getTaxReductionPercentage(app.id);
                    if (buff > 0) {
                        totalShopTaxBuff += buff;
                        apartmentsWithShopBuff++;
                    }
                }
            }
        }
        double avgShopTaxBuff = apartmentsWithShopBuff > 0 ? totalShopTaxBuff / apartmentsWithShopBuff : 0.0;

        List<String> taxInfoLore = new ArrayList<>();
        taxInfoLore.add("&7Summary of your tax situation");
        taxInfoLore.add("");
        taxInfoLore.add("&e📋 Statistics:");
        taxInfoLore.add("&7• Total Apartments: &f" + apartmentCount);
        taxInfoLore.add("&7• Overdue Apartments: &c" + overdueCount);
        taxInfoLore.add("&7• Active Invoices: &f" + totalInvoices);
        taxInfoLore.add("&7• Total Amount Due: &c" + plugin.getConfigManager().formatMoney(totalUnpaid));
        if (taxEfficiencyBonus > 0) {
            taxInfoLore.add("&7• Tax Efficiency: &a-" + String.format("%.0f%%", taxEfficiencyBonus) + " &7(Research)");
        }
        if (avgShopTaxBuff > 0) {
            taxInfoLore.add("&7• Avg Shop Buff: &a-" + String.format("%.0f%%", avgShopTaxBuff) + " &7(Solar Panel)");
        }
        taxInfoLore.add("");
        
        ConfigManager.TaxCalculationMethod taxMethod = plugin.getConfigManager().getTaxCalculationMethod();
        if (taxMethod == ConfigManager.TaxCalculationMethod.INCOME_BASED) {
            taxInfoLore.add("&7Taxes are calculated based on a percentage");
            taxInfoLore.add("&7of the apartment's &elast generated income&7.");
            taxInfoLore.add("&7(Falls back to price-based if no income yet)");
        } else {
            taxInfoLore.add("&7Taxes are calculated based on a percentage");
            taxInfoLore.add("&7of the apartment's &epurchase price&7.");
        }

        ItemStack taxInfoItem = new ItemBuilder(Material.BOOK)
                .name("&6📊 Tax Overview")
                .lore(taxInfoLore)
                .build();
        inventory.setItem(TAX_INFO_SLOT, taxInfoItem);
    }

    private GUIItem createApartmentTaxItem(Apartment apartment) {
        long now = System.currentTimeMillis();
        TaxStatus status = apartment.computeTaxStatus(now);
        double totalUnpaid = apartment.getTotalUnpaid();

        // Count active invoices
        long activeInvoices = apartment.taxInvoices != null
                ? apartment.taxInvoices.stream().filter(inv -> !inv.isPaid()).count()
                : 0;

        // Find oldest unpaid invoice
        long oldestDays = 0;
        if (apartment.taxInvoices != null) {
            oldestDays = apartment.taxInvoices.stream()
                    .filter(inv -> !inv.isPaid())
                    .mapToLong(inv -> inv.daysSinceCreated(now))
                    .max()
                    .orElse(0);
        }

        // Get tax efficiency research bonus
        double taxEfficiencyBonus = 0.0;
        if (plugin.getResearchManager() != null && apartment.owner != null) {
            taxEfficiencyBonus = plugin.getResearchManager().getTaxReduction(apartment.owner);
        }

        // Get shop tax buff
        double shopTaxBuff = 0.0;
        if (plugin.getShopManager() != null) {
            shopTaxBuff = plugin.getShopManager().getTaxReductionPercentage(apartment.id);
        }

        // Determine display based on status
        Material material;
        String statusDisplay;
        List<String> statusLore = new ArrayList<>();

        switch (status) {
            case OVERDUE:
                material = Material.YELLOW_CONCRETE;
                statusDisplay = "&6⚠️ Overdue";
                statusLore.add("&7Income generation has stopped");
                statusLore.add("&7Pay taxes to restore functionality");
                break;
            case INACTIVE:
                material = Material.RED_CONCRETE;
                statusDisplay = "&c❌ Inactive";
                statusLore.add("&7Apartment is completely disabled");
                statusLore.add("&7All functions are frozen");
                break;
            case REPOSSESSION:
                material = Material.BARRIER;
                statusDisplay = "&4💀 Repossession Risk";
                statusLore.add("&4WARNING: This apartment will be");
                statusLore.add("&4repossessed if taxes aren't paid soon!");
                break;
            default:
                material = Material.ORANGE_CONCRETE;
                statusDisplay = "&e📋 Pending Payment";
                break;
        }

        List<String> lore = new ArrayList<>();
        lore.add("&7Apartment: &f" + apartment.displayName);
        lore.add("&7Status: " + statusDisplay);
        lore.add("");
        lore.add("&e💰 Tax Information:");
        lore.add("&7• Total Due: &c" + plugin.getConfigManager().formatMoney(totalUnpaid));
        if (taxEfficiencyBonus > 0) {
            lore.add("&7• Tax Efficiency: &a-" + String.format("%.0f%%", taxEfficiencyBonus) + " &7(Research)");
        }
        if (shopTaxBuff > 0) {
            lore.add("&7• Shop Buff: &a-" + String.format("%.0f%%", shopTaxBuff) + " &7(Solar Panel)");
        }
        lore.add("&7• Active Bills: &f" + activeInvoices);
        lore.add("&7• Oldest Bill: &f" + oldestDays + " days old");
        boolean isFeatureEnabled = plugin.getEditionManager().isAutoTaxPaymentEnabled();
        boolean isProActive = plugin.getEditionManager().isProActive();
        if (!isProActive) {
            lore.add("&7• Auto-pay: &8[PRO Only]");
        } else if (!isFeatureEnabled) {
            lore.add("&7• Auto-pay: &c[Disabled by Admin]");
        } else {
            lore.add("&7• Auto-pay: " + (apartment.autoTaxPayment ? "&aEnabled" : "&cDisabled"));
        }
        lore.add("");
        lore.addAll(statusLore);
        lore.add("");
        lore.add("&a▶ Click to view apartment details");

        ItemStack item = new ItemBuilder(material)
                .name("&c🏠 " + apartment.displayName + " - Tax Due")
                .lore(lore)
                .build();

        return new GUIItem(item, "apartment_" + apartment.id, apartment);
    }

    private GUIItem createInvoiceItem(Apartment apartment, TaxInvoice invoice) {
        long now = System.currentTimeMillis();
        long daysSinceCreated = invoice.daysSinceCreated(now);
        boolean isOverdue = daysSinceCreated >= 3;

        // Determine urgency color
        ChatColor urgencyColor;
        String urgencyText;
        if (daysSinceCreated >= 5) {
            urgencyColor = ChatColor.DARK_RED;
            urgencyText = "CRITICAL";
        } else if (daysSinceCreated >= 3) {
            urgencyColor = ChatColor.RED;
            urgencyText = "OVERDUE";
        } else if (daysSinceCreated >= 2) {
            urgencyColor = ChatColor.GOLD;
            urgencyText = "DUE SOON";
        } else {
            urgencyColor = ChatColor.YELLOW;
            urgencyText = "CURRENT";
        }

        Material material = isOverdue ? Material.RED_STAINED_GLASS : Material.YELLOW_STAINED_GLASS;

        // Get tax efficiency research bonus
        double taxEfficiencyBonus = 0.0;
        if (plugin.getResearchManager() != null && apartment.owner != null) {
            taxEfficiencyBonus = plugin.getResearchManager().getTaxReduction(apartment.owner);
        }

        // Get shop tax buff
        double shopTaxBuff = 0.0;
        if (plugin.getShopManager() != null) {
            shopTaxBuff = plugin.getShopManager().getTaxReductionPercentage(apartment.id);
        }

        List<String> lore = new ArrayList<>();
        lore.add("&7Apartment: &f" + apartment.displayName);
        lore.add("&7Invoice ID: &f" + invoice.id.substring(0, 8) + "...");
        lore.add("");
        lore.add("&e💰 Bill Details:");
        lore.add("&7• Amount: &c" + plugin.getConfigManager().formatMoney(invoice.amount));
        if (taxEfficiencyBonus > 0 || shopTaxBuff > 0) {
            double totalReduction = taxEfficiencyBonus + shopTaxBuff;
            double originalAmount = invoice.amount / (1.0 - (totalReduction / 100.0));
            lore.add("&7• Original: &c" + plugin.getConfigManager().formatMoney(originalAmount));
            if (taxEfficiencyBonus > 0) {
                lore.add("&7• Tax Efficiency: &a-" + String.format("%.0f%%", taxEfficiencyBonus) + " &7(Research)");
            }
            if (shopTaxBuff > 0) {
                lore.add("&7• Shop Buff: &a-" + String.format("%.0f%%", shopTaxBuff) + " &7(Solar Panel)");
            }
        }
        lore.add("&7• Age: &f" + daysSinceCreated + " days");
        lore.add("&7• Status: " + urgencyColor + urgencyText);
        lore.add("&7• Due Date: " + (isOverdue ? "&cPASSED" : "&a" + (3 - daysSinceCreated) + " days left"));
        lore.add("");

        if (isOverdue) {
            lore.add("&c⚠️ This bill is overdue!");
            lore.add("&7Pay immediately to avoid penalties");
        } else {
            lore.add("&7This bill will be due in " + (3 - daysSinceCreated) + " days");
        }

        lore.add("");
        lore.add("&a▶ Click to pay this bill only");

        ItemStack item = new ItemBuilder(material)
                .name("&c📄 Tax Bill #" + (daysSinceCreated + 1))
                .lore(lore)
                .build();

        return new GUIItem(item, "invoice_" + invoice.id, new InvoiceData(apartment, invoice));
    }

    @Override
    protected void handleItemClick(GUIItem item, InventoryClickEvent event) {
        String itemId = item.getId();

        if (itemId.startsWith("apartment_")) {
            // Apartment item clicked - open details
            Apartment apartment = item.getData(Apartment.class);
            if (apartment != null) {
                guiManager.openApartmentDetails(player, apartment.id);
            }
        } else if (itemId.startsWith("invoice_")) {
            // Individual invoice clicked - pay single bill
            InvoiceData invoiceData = item.getData(InvoiceData.class);
            if (invoiceData != null) {
                handlePaySingleInvoice(invoiceData.apartment, invoiceData.invoice);
            }
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == BACK_SLOT) {
            guiManager.openMainMenu(player);
            return;
        }

        if (slot == PAY_ALL_SLOT) {
            handlePayAll();
            return;
        }

        if (slot == AUTO_PAY_TOGGLE_SLOT) {
            handleAutoPayToggle();
            return;
        }

        // Handle pagination and items
        super.handleClick(event);
    }

    private void handlePayAll() {
        player.closeInventory();
        plugin.getServer().dispatchCommand(player, "apartmentcore tax pay");
    }

    private void handleAutoPayToggle() {
        // Check Pro edition gate
        if (!plugin.getEditionManager().isProActive()) {
            plugin.getEditionManager().sendProOnlyMessage(player, "Auto Tax Payment");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }

        // Check if disabled by Admin
        if (!plugin.getEditionManager().isAutoTaxPaymentEnabled()) {
            GUIUtils.sendMessage(player, "&cThis feature has been disabled by the administrator.");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }

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

    private void handlePaySingleInvoice(Apartment apartment, TaxInvoice invoice) {
        if (invoice.isPaid()) {
            GUIUtils.sendMessage(player, "&cThis invoice has already been paid!");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }

        if (!plugin.getEconomy().has(player, invoice.amount)) {
            GUIUtils.sendMessage(player,
                    "&cInsufficient funds! Need: " + plugin.getConfigManager().formatMoney(invoice.amount));
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }

        // Pay the invoice
        plugin.getEconomy().withdrawPlayer(player, invoice.amount);
        invoice.paidAt = System.currentTimeMillis();
        apartment.lastTaxPayment = System.currentTimeMillis();

        // Update stats
        ApartmentStats stats = plugin.getApartmentManager().getStats(apartment.id);
        stats.totalTaxPaid += invoice.amount;

        // Track tax achievement
        if (plugin.getAchievementManager() != null) {
            double totalTax = 0;
            for (com.aithor.apartmentcore.model.Apartment a : plugin.getApartmentManager().getApartments().values()) {
                if (player.getUniqueId().equals(a.owner)) {
                    ApartmentStats s = plugin.getApartmentManager().getStats(a.id);
                    if (s != null) totalTax += s.totalTaxPaid;
                }
            }
            plugin.getAchievementManager().setProgress(player.getUniqueId(),
                    com.aithor.apartmentcore.achievement.AchievementType.TAX_CONTRIBUTOR, totalTax);
        }

        // Clear inactive flags if this was the last unpaid invoice
        if (apartment.getTotalUnpaid() <= 0) {
            apartment.inactive = false;
            apartment.inactiveSince = 0L;
        }

        plugin.getApartmentManager().saveApartments();
        plugin.getApartmentManager().saveStats();

        GUIUtils.sendMessage(player, "&aPaid tax bill: &f" + plugin.getConfigManager().formatMoney(invoice.amount) +
                " &afor &f" + apartment.displayName);
        GUIUtils.playSound(player, GUIUtils.SUCCESS_SOUND);

        refresh(); // Refresh to show updated status
    }

    /**
     * Helper class to hold apartment and invoice data together
     */
    private static class InvoiceData {
        final Apartment apartment;
        final TaxInvoice invoice;

        InvoiceData(Apartment apartment, TaxInvoice invoice) {
            this.apartment = apartment;
            this.invoice = invoice;
        }
    }
}