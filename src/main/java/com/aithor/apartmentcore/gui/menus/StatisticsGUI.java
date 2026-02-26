package com.aithor.apartmentcore.gui.menus;

import com.aithor.apartmentcore.model.Apartment;
import com.aithor.apartmentcore.ApartmentCorei3;
import com.aithor.apartmentcore.model.ApartmentRating;
import com.aithor.apartmentcore.model.ApartmentStats;
import com.aithor.apartmentcore.model.TaxInvoice;
import com.aithor.apartmentcore.model.TaxStatus;
import com.aithor.apartmentcore.gui.GUIManager;
import com.aithor.apartmentcore.gui.interfaces.PaginatedGUI;
import com.aithor.apartmentcore.gui.items.GUIItem;
import com.aithor.apartmentcore.gui.items.ItemBuilder;
import com.aithor.apartmentcore.gui.utils.GUIUtils;
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
 * Player statistics overview GUI
 */
public class StatisticsGUI extends PaginatedGUI {

    private final ApartmentCorei3 plugin;
    private final GUIManager guiManager;

    // Header/action slots (top border row)
    private static final int BACK_SLOT = 0;
    private static final int CLAIM_ALL_SLOT = 1;
    private static final int PAY_ALL_TAXES_SLOT = 2;
    private static final int SUMMARY_SLOT = 4;

    public StatisticsGUI(Player player, ApartmentCorei3 plugin, GUIManager guiManager) {
        super(player, ChatColor.DARK_AQUA + "Statistics Overview", 54, 28);
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @Override
    protected List<GUIItem> loadItems() {
        // List all owned apartments, sorted by display name
        List<Apartment> apartments = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .sorted(Comparator.comparing(a -> a.displayName))
                .collect(Collectors.toList());

        List<GUIItem> items = new ArrayList<>();

        long now = System.currentTimeMillis();
        for (Apartment apt : apartments) {
            TaxStatus status = apt.computeTaxStatus(now);
            double unpaid = apt.getTotalUnpaid();
            ApartmentStats stats = plugin.getApartmentManager().getStats(apt.id);
            if (stats == null) stats = new ApartmentStats();
            ApartmentRating rating = plugin.getApartmentManager().getRating(apt.id);

            // Determine card material by status
            Material material;
            switch (status) {
                case ACTIVE:
                    material = Material.EMERALD_BLOCK;
                    break;
                case OVERDUE:
                    material = Material.GOLD_BLOCK;
                    break;
                case INACTIVE:
                    material = Material.RED_CONCRETE;
                    break;
                case REPOSSESSION:
                    material = Material.BARRIER;
                    break;
                default:
                    material = Material.STONE;
            }

            String ratingDisplay = rating != null && rating.ratingCount > 0
                    ? String.format("%.1f⭐ (%d reviews)", rating.getAverageRating(), rating.ratingCount)
                    : "No ratings yet";

            // Build lore lines
            List<String> lore = new ArrayList<>();
            lore.add("&7ID: &f" + apt.id);
            lore.add("&7Level: &f" + apt.level + "/5");
            lore.add("");
            lore.add("&e📈 Stats:");
            lore.add("&7• Ownership Age: &f" + stats.ownershipAgeDays + " days");
            lore.add("&7• Total Income: &a" + plugin.getConfigManager().formatMoney(stats.totalIncomeGenerated));
            lore.add("&7• Total Tax Paid: &c" + plugin.getConfigManager().formatMoney(stats.totalTaxPaid));
            lore.add("");
            lore.add("&e💰 Financial:");
            lore.add("&7• Pending Income: &a" + plugin.getConfigManager().formatMoney(apt.pendingIncome));
            lore.add("&7• Outstanding Taxes: &c" + plugin.getConfigManager().formatMoney(unpaid));
            lore.add("");
            lore.add("&e📊 Status: &f" + status.name());
            lore.add("&e⭐ Rating: &f" + ratingDisplay);
            lore.add("");
            lore.add("&a▶ Click to view apartment statistics");

            ItemStack item = new ItemBuilder(material)
                    .name("&6📊 " + apt.displayName)
                    .lore(lore)
                    .build();

            items.add(new GUIItem(item, "stats_apartment_" + apt.id, apt));
        }

        return items;
    }

    @Override
    protected void setupInventory() {
        super.setupInventory();
        addHeaderAndActions();
    }

    private void addHeaderAndActions() {
        // Back to Main Menu
        ItemStack backItem = new ItemBuilder(Material.ARROW)
                .name("&c◀ Back to Main Menu")
                .lore("&7Return to the main menu")
                .build();
        inventory.setItem(BACK_SLOT, backItem);

        // Aggregate calculations
        double totalPendingIncome = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToDouble(a -> a.pendingIncome)
                .sum();

        double totalUnpaidTaxes = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToDouble(Apartment::getTotalUnpaid)
                .sum();

        long ownedCount = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .count();

        // Sum stats
        double totalIncomeGenerated = 0.0;
        double totalTaxPaid = 0.0;
        for (Apartment a : plugin.getApartmentManager().getApartments().values()) {
            if (!player.getUniqueId().equals(a.owner)) continue;
            ApartmentStats st = plugin.getApartmentManager().getStats(a.id);
            if (st != null) {
                totalIncomeGenerated += st.totalIncomeGenerated;
                totalTaxPaid += st.totalTaxPaid;
            }
        }

        // Average rating
        double sumAvg = 0.0;
        int ratedCount = 0;
        for (Apartment a : plugin.getApartmentManager().getApartments().values()) {
            if (!player.getUniqueId().equals(a.owner)) continue;
            ApartmentRating r = plugin.getApartmentManager().getRating(a.id);
            if (r != null && r.ratingCount > 0) {
                sumAvg += r.getAverageRating();
                ratedCount++;
            }
        }
        String avgRatingStr = ratedCount > 0 ? String.format("%.1f⭐", (sumAvg / ratedCount)) : "N/A";

        // Summary card
        ItemStack summary = new ItemBuilder(Material.BOOK)
                .name("&6📊 Your Statistics")
                .lore(
                        "&7Version: &f" + plugin.getDescription().getVersion(),
                        "&7Economy: &f" + plugin.getEconomy().getName(),
                        "",
                        "&e📋 Overview:",
                        "&7• Owned Apartments: &f" + ownedCount,
                        "&7• Total Income: &a" + plugin.getConfigManager().formatMoney(totalIncomeGenerated),
                        "&7• Total Tax Paid: &c" + plugin.getConfigManager().formatMoney(totalTaxPaid),
                        "&7• Outstanding Taxes: &c" + plugin.getConfigManager().formatMoney(totalUnpaidTaxes),
                        "&7• Avg Rating: &f" + avgRatingStr
                )
                .build();
        inventory.setItem(SUMMARY_SLOT, summary);

        // Claim all income
        Material claimMaterial = totalPendingIncome > 0 ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE;
        ItemStack claimAllItem = new ItemBuilder(claimMaterial)
                .name("&a💰 Claim All Income")
                .lore(
                        "&7Claim income from all apartments",
                        "",
                        "&7Total Pending: &a" + plugin.getConfigManager().formatMoney(totalPendingIncome),
                        "",
                        totalPendingIncome > 0 ? "&a▶ Click to claim all" : "&7No income to claim"
                )
                .build();
        inventory.setItem(CLAIM_ALL_SLOT, claimAllItem);

        // Pay all taxes
        boolean canAfford = plugin.getEconomy().has(player, totalUnpaidTaxes);
        Material payMat = totalUnpaidTaxes > 0 ? (canAfford ? Material.GOLD_BLOCK : Material.RED_CONCRETE) : Material.GRAY_CONCRETE;
        ItemStack payAllTaxesItem = new ItemBuilder(payMat)
                .name("&a💳 Pay All Taxes")
                .lore(
                        "&7Pay all outstanding tax bills",
                        "",
                        "&7Total Due: &c" + plugin.getConfigManager().formatMoney(totalUnpaidTaxes),
                        "&7Your Balance: &a" + plugin.getConfigManager().formatMoney(plugin.getEconomy().getBalance(player)),
                        "",
                        totalUnpaidTaxes > 0 ? (canAfford ? "&a▶ Click to pay all" : "&cInsufficient funds") : "&7No taxes due"
                )
                .build();
        inventory.setItem(PAY_ALL_TAXES_SLOT, payAllTaxesItem);
    }

    @Override
    protected void handleItemClick(GUIItem item, InventoryClickEvent event) {
        Apartment apt = item.getData(Apartment.class);
        if (apt != null) {
            // Open detailed statistics for this apartment
            guiManager.openApartmentStatistics(player, apt.id);
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
            // Re-use existing flow
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

            int claimedCount = 0;
            for (Apartment apartment : plugin.getApartmentManager().getApartments().values()) {
                if (player.getUniqueId().equals(apartment.owner) && apartment.pendingIncome > 0) {
                    plugin.getEconomy().depositPlayer(player, apartment.pendingIncome);

                    // Update stats
                    ApartmentStats s = plugin.getApartmentManager().getStats(apartment.id);
                    s.totalIncomeGenerated += apartment.pendingIncome;

                    apartment.pendingIncome = 0;
                    claimedCount++;
                }
            }

            plugin.getApartmentManager().saveApartments();
            plugin.getApartmentManager().saveStats();

            GUIUtils.sendMessage(player, "&aClaimed &f" + plugin.getConfigManager().formatMoney(totalIncome) +
                    " &afrom &f" + claimedCount + " &aapartments!");
            GUIUtils.playSound(player, GUIUtils.SUCCESS_SOUND);
            return;
        }

        if (slot == PAY_ALL_TAXES_SLOT) {
            player.closeInventory();
            plugin.getServer().dispatchCommand(player, "apartmentcore tax pay");
            return;
        }

        // Delegate to pagination and item handling
        super.handleClick(event);
    }
}