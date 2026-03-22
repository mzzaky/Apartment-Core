package com.aithor.apartmentcore.gui.menus;

import com.aithor.apartmentcore.model.Apartment;
import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.model.ApartmentRating;
import com.aithor.apartmentcore.model.ApartmentStats;
import com.aithor.apartmentcore.model.LevelConfig;
import com.aithor.apartmentcore.model.TaxInvoice;
import com.aithor.apartmentcore.model.TaxStatus;
import com.aithor.apartmentcore.research.ResearchManager;
import com.aithor.apartmentcore.shop.ApartmentShopData;
import com.aithor.apartmentcore.shop.ShopItem;
import com.aithor.apartmentcore.manager.ConfigManager;
import com.aithor.apartmentcore.gui.GUIManager;
import com.aithor.apartmentcore.gui.interfaces.GUI;
import com.aithor.apartmentcore.gui.items.ItemBuilder;
import com.aithor.apartmentcore.gui.utils.GUIUtils;
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
 * Per-apartment detailed statistics GUI — displays all stat panels:
 * base, shop buffs, research buffs, final math, and misc stats.
 */
public class ApartmentStatisticsGUI implements GUI {

    private final Player player;
    private final ApartmentCore plugin;
    private final GUIManager guiManager;
    private final String apartmentId;
    private final String title;
    private final Inventory inventory;

    // Navigation slots (top row)
    private static final int BACK_SLOT = 0;
    private static final int OPEN_DETAILS_SLOT = 8;

    // Section slots — 6-row inventory (54 slots)
    private static final int BASE_STATS_SLOT = 20; // Section 1
    private static final int SHOP_BUFF_SLOT = 22; // Section 2
    private static final int RESEARCH_BUFF_SLOT = 24; // Section 3
    private static final int FINAL_MATH_SLOT = 30; // Section 4
    private static final int OTHER_STATS_SLOT = 32; // Section 5

    // Status / meta slots (bottom area)
    private static final int STATUS_SLOT = 48;
    private static final int RATING_SLOT = 49;
    private static final int GUESTBOOK_SLOT = 50;

    public ApartmentStatisticsGUI(Player player, ApartmentCore plugin, GUIManager guiManager, String apartmentId) {
        this.player = player;
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.apartmentId = apartmentId;
        this.title = ChatColor.DARK_AQUA + "Apartment Statistics";
        this.inventory = Bukkit.createInventory(null, 54, this.title);
    }

    // =====================================================================
    // GUI Lifecycle
    // =====================================================================

    @Override
    public void open(Player player) {
        setupInventory();
        player.openInventory(inventory);
    }

    private void setupInventory() {
        inventory.clear();

        Apartment apt = plugin.getApartmentManager().getApartment(apartmentId);
        if (apt == null) {
            ItemStack errorItem = new ItemBuilder(Material.BARRIER)
                    .name("&cApartment Not Found")
                    .lore("&7The requested apartment could not be found")
                    .build();
            inventory.setItem(22, errorItem);
            return;
        }

        addBorder();
        addNavigation();
        addBaseStats(apt);
        addShopBuffStats(apt);
        addResearchBuffStats(apt);
        addFinalMathStats(apt);
        addOtherStats(apt);
        addStatusAndMeta(apt);
    }

    // =====================================================================
    // Border & Navigation
    // =====================================================================

    private void addBorder() {
        ItemStack border = ItemBuilder.filler(Material.GRAY_STAINED_GLASS_PANE);
        // Top row
        for (int i = 0; i <= 8; i++)
            inventory.setItem(i, border);
        // Bottom row
        for (int i = 45; i <= 53; i++)
            inventory.setItem(i, border);
        // Left & right columns
        for (int i = 0; i < 6; i++) {
            inventory.setItem(i * 9, border);
            inventory.setItem(i * 9 + 8, border);
        }
    }

    private void addNavigation() {
        // Back button
        ItemStack back = new ItemBuilder(Material.ARROW)
                .name("&c◀ Back to Statistics Overview")
                .lore("&7Return to the global statistics")
                .build();
        inventory.setItem(BACK_SLOT, back);

        // Shortcut to Apartment Details
        ItemStack details = new ItemBuilder(Material.NETHER_STAR)
                .name("&a📋 Open Apartment Details")
                .lore("&7View management & actions for this apartment", "", "&a▶ Click to open")
                .glow()
                .build();
        inventory.setItem(OPEN_DETAILS_SLOT, details);
    }

    // =====================================================================
    // Section 1 — Base Apartment Statistics
    // =====================================================================

    private void addBaseStats(Apartment apt) {
        LevelConfig cfg = plugin.getConfigManager().getLevelConfig(apt.level);

        List<String> lore = new ArrayList<>();
        lore.add("&8▸ Base apartment attributes from level config");
        lore.add("");
        lore.add("&e📋 Identity:");
        lore.add("&7• Apartment ID: &f" + apt.id);
        lore.add("&7• Display Name: &f" + apt.displayName);
        lore.add("&7• Level: &f" + apt.level + " &7/ 5");
        lore.add("&7• Purchase Price: &f" + plugin.getConfigManager().formatMoney(apt.price));
        lore.add("&7• Floor: &f" + apt.floor);
        lore.add("&7• Height: &f" + apt.height);
        lore.add("");
        lore.add("&e💰 Base Income (per cycle):");
        if (cfg != null) {
            lore.add("&7• Min Income: &a" + plugin.getConfigManager().formatMoney(apt.getMinIncome(plugin.getConfigManager(), apt.level)));
            lore.add("&7• Max Income: &a" + plugin.getConfigManager().formatMoney(apt.getMaxIncome(plugin.getConfigManager(), apt.level)));
            lore.add("&7• Income Capacity: &b" + plugin.getConfigManager().formatMoney(cfg.incomeCapacity));
        } else {
            lore.add("&c• Level config not found (level: " + apt.level + ")");
        }
        lore.add("");
        lore.add("&e🧾 Base Tax:");
        if (cfg != null) {
            lore.add("&7• Tax Rate: &c" + String.format("%.1f%%", cfg.taxPercentage));
            double baseTaxAmt = apt.price * (cfg.taxPercentage / 100.0);
            lore.add("&7• Tax per Cycle: &c" + plugin.getConfigManager().formatMoney(baseTaxAmt));
        } else {
            lore.add("&c• Level config not found");
        }
        lore.add("");
        lore.add("&7These are raw values before any buffs are applied.");

        ItemStack item = new ItemBuilder(Material.BOOK)
                .name("&b📊 Section 1: Base Apartment Stats")
                .lore(lore)
                .build();
        inventory.setItem(BASE_STATS_SLOT, item);
    }

    // =====================================================================
    // Section 2 — Shop Buff Statistics
    // =====================================================================

    private void addShopBuffStats(Apartment apt) {
        ApartmentShopData shopData = plugin.getShopManager() != null
                ? plugin.getShopManager().getShopData(apt.id)
                : null;

        List<String> lore = new ArrayList<>();
        lore.add("&8▸ Buffs applied via the apartment shop");
        lore.add("");

        if (shopData == null || plugin.getShopManager() == null) {
            lore.add("&cShop system is unavailable.");
        } else {
            lore.add("&e🛒 Shop Upgrades:");

            for (ShopItem shopItem : ShopItem.values()) {
                int tier = shopData.getTier(shopItem);
                String tierText = tier == 0 ? "&8Not purchased" : "&aT" + tier + " &7/ &aT" + shopItem.getMaxTier();
                String buffText = tier == 0 ? "" : " &7(" + shopItem.getBuffDescription(tier) + ")";
                lore.add("&7• " + shopItem.getDisplayName() + ": " + tierText + buffText);
            }

            lore.add("");
            lore.add("&e📈 Total Shop Buffs:");

            double incomeBonus = plugin.getShopManager().getIncomeBonusPercentage(apt.id);
            double baseBonus = plugin.getShopManager().getBaseIncomeBonus(apt.id);
            double taxReduct = plugin.getShopManager().getTaxReductionPercentage(apt.id);
            double incomeSpeed = plugin.getShopManager().getIncomeSpeedBonus(apt.id);
            int maxMsgs = plugin.getShopManager().getMaxMessagesBonus(apt.id);
            double capacityBonus = plugin.getShopManager().getIncomeCapacityBonusPercentage(apt.id);

            lore.add("&7• Income % Bonus: "
                    + (incomeBonus > 0 ? "&a+" + String.format("%.1f%%", incomeBonus) : "&8None"));
            lore.add("&7• Flat Income Bonus: "
                    + (baseBonus > 0 ? "&a+" + plugin.getConfigManager().formatMoney(baseBonus) : "&8None"));
            lore.add("&7• Tax Reduction: " + (taxReduct > 0 ? "&a-" + String.format("%.1f%%", taxReduct) : "&8None"));
            lore.add("&7• Income Speed Bonus: " + (incomeSpeed > 0 ? "&a-" + (int) incomeSpeed + " ticks" : "&8None"));
            lore.add("&7• Extra Guestbook Msgs: " + (maxMsgs > 0 ? "&a+" + maxMsgs : "&8None"));
            lore.add("&7• Income Capacity: "
                    + (capacityBonus > 0 ? "&a+" + String.format("%.1f%%", capacityBonus) : "&8None"));
            lore.add("&7• Total Invested: &e" + plugin.getConfigManager().formatMoney(shopData.getTotalMoneySpent()));
        }

        ItemStack item = new ItemBuilder(Material.EMERALD)
                .name("&a🛒 Section 2: Shop Buff Stats")
                .lore(lore)
                .build();
        inventory.setItem(SHOP_BUFF_SLOT, item);
    }

    // =====================================================================
    // Section 3 — Research Buff Statistics
    // =====================================================================

    private void addResearchBuffStats(Apartment apt) {
        List<String> lore = new ArrayList<>();
        lore.add("&8▸ Permanent player-level research buffs");
        lore.add("");

        if (apt.owner == null || plugin.getResearchManager() == null) {
            lore.add("&7• No owner or research system unavailable.");
        } else {
            ResearchManager rm = plugin.getResearchManager();
            java.util.UUID ownerId = apt.owner;

            double capitalGrowth = rm.getIncomeAmountBonus(ownerId);
            double revenueAccel = rm.getIncomeIntervalReduction(ownerId);
            double taxEfficiency = rm.getTaxReduction(ownerId);
            int expansionSlots = rm.getExtraOwnershipSlots(ownerId);
            double vaultExpansion = rm.getIncomeCapacityBonus(ownerId);
            double auctionFee = rm.getAuctionFeeReduction(ownerId);
            double auctionComm = rm.getAuctionCommissionReduction(ownerId);

            lore.add("&e🔬 Research Buffs (owner-level):");
            lore.add("&7• Capital Growth Strategy: " + formatResearchBuff(capitalGrowth, "%", true));
            lore.add("  &8Income amount +" + (capitalGrowth > 0 ? String.format("%.0f%%", capitalGrowth) : "0%")
                    + " per cycle");
            lore.add("&7• Revenue Acceleration: " + formatResearchBuff(revenueAccel, "%", true));
            lore.add("  &8Income interval -" + (revenueAccel > 0 ? String.format("%.0f%%", revenueAccel) : "0%")
                    + " (faster income)");
            lore.add("&7• Tax Efficiency Strategy: " + formatResearchBuff(taxEfficiency, "%", true));
            lore.add("  &8Tax amount -" + (taxEfficiency > 0 ? String.format("%.0f%%", taxEfficiency) : "0%"));
            lore.add("&7• Expansion Plan: "
                    + (expansionSlots > 0 ? "&a+" + expansionSlots + " ownership slot(s)" : "&8Not researched"));
            lore.add("&7• Vault Expansion: " + formatResearchBuff(vaultExpansion, "%", true));
            lore.add("  &8Income capacity +" + (vaultExpansion > 0 ? String.format("%.0f%%", vaultExpansion) : "0%"));
            lore.add("&7• Auction Efficiency: " + (auctionFee > 0
                    ? "&a-" + String.format("%.0f%%", auctionFee) + " fee, -" + String.format("%.0f%%", auctionComm)
                            + " commission"
                    : "&8Not researched"));
        }

        ItemStack item = new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name("&d🔬 Section 3: Research Buff Stats")
                .lore(lore)
                .build();
        inventory.setItem(RESEARCH_BUFF_SLOT, item);
    }

    // =====================================================================
    // Section 4 — Final Calculation (after all buffs)
    // =====================================================================

    private void addFinalMathStats(Apartment apt) {
        List<String> lore = new ArrayList<>();
        lore.add("&8▸ Final income & tax after applying all buffs");
        lore.add("");

        LevelConfig cfg = plugin.getConfigManager().getLevelConfig(apt.level);
        if (cfg == null) {
            lore.add("&cLevel config not found — cannot calculate final stats.");
        } else {
            // --- Income calculation ---
            double baseMin = apt.getMinIncome(plugin.getConfigManager(), apt.level);
            double baseMax = apt.getMaxIncome(plugin.getConfigManager(), apt.level);
            double baseAvg = (baseMin + baseMax) / 2.0;

            double shopFlatBonus = plugin.getShopManager() != null ? plugin.getShopManager().getBaseIncomeBonus(apt.id)
                    : 0;
            double shopPctBonus = plugin.getShopManager() != null
                    ? plugin.getShopManager().getIncomeBonusPercentage(apt.id)
                    : 0;
            double shopSpeedTickDiff = plugin.getShopManager() != null
                    ? plugin.getShopManager().getIncomeSpeedBonus(apt.id)
                    : 0;

            double capitalGrowth = (apt.owner != null && plugin.getResearchManager() != null)
                    ? plugin.getResearchManager().getIncomeAmountBonus(apt.owner)
                    : 0;
            double revenueAccel = (apt.owner != null && plugin.getResearchManager() != null)
                    ? plugin.getResearchManager().getIncomeIntervalReduction(apt.owner)
                    : 0;

            // Replicate same formula as Apartment#getHourlyIncomeWithShopBuffs
            double incomeAfterFlat = baseAvg + shopFlatBonus;
            double incomeAfterShopPct = shopPctBonus > 0 ? incomeAfterFlat * (1.0 + shopPctBonus / 100.0)
                    : incomeAfterFlat;

            double incomeAfterShopSpeed = incomeAfterShopPct;
            long baseInterval = plugin.getConfigManager().getIncomeGenerationInterval();
            if (shopSpeedTickDiff > 0 && shopSpeedTickDiff < baseInterval) {
                incomeAfterShopSpeed *= ((double) baseInterval / (baseInterval - shopSpeedTickDiff));
            }

            double incomeAfterCapGrowth = capitalGrowth > 0 ? incomeAfterShopSpeed * (1.0 + capitalGrowth / 100.0)
                    : incomeAfterShopSpeed;
            double incomeAfterRevAccel = (revenueAccel > 0 && revenueAccel < 100)
                    ? incomeAfterCapGrowth * (1.0 / (1.0 - revenueAccel / 100.0))
                    : incomeAfterCapGrowth;
            double finalIncome = incomeAfterRevAccel;

            // Income capacity
            double baseCapacity = cfg.incomeCapacity;
            double vaultPct = (apt.owner != null && plugin.getResearchManager() != null)
                    ? plugin.getResearchManager().getIncomeCapacityBonus(apt.owner)
                    : 0;
            double shopVaultPct = plugin.getShopManager() != null
                    ? plugin.getShopManager().getIncomeCapacityBonusPercentage(apt.id)
                    : 0;
            double finalCapacity = (vaultPct > 0 || shopVaultPct > 0)
                    ? baseCapacity * (1.0 + ((vaultPct + shopVaultPct) / 100.0))
                    : baseCapacity;

            // --- Tax calculation ---
            // Mirror Apartment#computeBaseTaxAmount: respect the active tax-calculation-method.
            ConfigManager.TaxCalculationMethod taxMethod =
                    plugin.getConfigManager().getTaxCalculationMethod();

            double baseTaxRate = cfg.taxPercentage / 100.0;

            // Determine which base value is used and build a human-readable label for the GUI
            boolean usingIncomeBased = taxMethod == ConfigManager.TaxCalculationMethod.INCOME_BASED
                    && apt.lastGeneratedIncome > 0;
            boolean incomeFallback = taxMethod == ConfigManager.TaxCalculationMethod.INCOME_BASED
                    && apt.lastGeneratedIncome <= 0;

            double taxBase;
            String taxBaseLabel;
            if (usingIncomeBased) {
                taxBase = apt.lastGeneratedIncome;
                taxBaseLabel = "&7  Base: &flast income ("
                        + plugin.getConfigManager().formatMoney(taxBase) + ")";
            } else {
                // price-based (or income-based fallback when no income recorded yet)
                taxBase = apt.price;
                taxBaseLabel = "&7  Base: &fprice ("
                        + plugin.getConfigManager().formatMoney(taxBase) + ")"
                        + (incomeFallback ? " &e(income-based fallback)" : "");
            }

            double baseTaxAmt = taxBase * baseTaxRate;
            double shopTaxReduct = plugin.getShopManager() != null
                    ? plugin.getShopManager().getTaxReductionPercentage(apt.id)
                    : 0;
            double resTaxReduct = (apt.owner != null && plugin.getResearchManager() != null)
                    ? plugin.getResearchManager().getTaxReduction(apt.owner)
                    : 0;
            double taxAfterShop = shopTaxReduct > 0 ? baseTaxAmt * (1.0 - shopTaxReduct / 100.0) : baseTaxAmt;
            double finalTax = resTaxReduct > 0 ? taxAfterShop * (1.0 - resTaxReduct / 100.0) : taxAfterShop;
            finalTax = Math.max(0, finalTax);

            // --- Display ---
            lore.add("&e💰 Income (avg per cycle):");
            lore.add("&7  Base (avg): &f" + plugin.getConfigManager().formatMoney(baseAvg));
            if (shopFlatBonus > 0)
                lore.add("&7  + Shop Flat Bonus: &a+" + plugin.getConfigManager().formatMoney(shopFlatBonus)
                        + " &7→ &f" + plugin.getConfigManager().formatMoney(incomeAfterFlat));
            if (shopPctBonus > 0)
                lore.add("&7  × Shop % Bonus (" + String.format("+%.1f%%", shopPctBonus) + "): &a→ &f"
                        + plugin.getConfigManager().formatMoney(incomeAfterShopPct));
            if (shopSpeedTickDiff > 0)
                lore.add("&7  × High Speed Internet (-" + (long) shopSpeedTickDiff + " ticks): &a→ &f"
                        + plugin.getConfigManager().formatMoney(incomeAfterShopSpeed));
            if (capitalGrowth > 0)
                lore.add("&7  × Capital Growth (" + String.format("+%.0f%%", capitalGrowth) + "): &a→ &f"
                        + plugin.getConfigManager().formatMoney(incomeAfterCapGrowth));
            if (revenueAccel > 0)
                lore.add("&7  × Rev. Acceleration (" + String.format("-%.0f%%", revenueAccel) + " int.): &a→ &f"
                        + plugin.getConfigManager().formatMoney(incomeAfterRevAccel));
            lore.add("&a  ▶ Final Income/Cycle: &f&l" + plugin.getConfigManager().formatMoney(finalIncome));
            lore.add("");
            lore.add("&e📦 Income Capacity:");
            lore.add("&7  Base Capacity: &f" + plugin.getConfigManager().formatMoney(baseCapacity));
            if (vaultPct > 0)
                lore.add("&7  + Research Expansion (" + String.format("+%.0f%%", vaultPct) + "): &b→ &f"
                        + plugin.getConfigManager().formatMoney(baseCapacity * (1.0 + vaultPct / 100.0)));
            if (shopVaultPct > 0)
                lore.add("&7  + Shop Vault Expansion (" + String.format("+%.1f%%", shopVaultPct) + "): &b→ &f"
                        + plugin.getConfigManager().formatMoney(finalCapacity));
            lore.add("&b  ▶ Final Capacity: &f&l" + plugin.getConfigManager().formatMoney(finalCapacity));
            lore.add("");

            // Tax method header — shows which method is active
            String methodTag = usingIncomeBased ? "&7[&bincome-based&7]"
                    : (incomeFallback ? "&7[&eincome-based → price fallback&7]" : "&7[&7price-based&7]");
            lore.add("&e🧾 Tax (per cycle): " + methodTag);
            lore.add(taxBaseLabel);
            lore.add("&7  × Tax Rate: &f" + String.format("%.2f%%", cfg.taxPercentage) + " &7→ &f"
                    + plugin.getConfigManager().formatMoney(baseTaxAmt));
            if (shopTaxReduct > 0)
                lore.add("&7  - Shop Tax Reduction (" + String.format("-%.1f%%", shopTaxReduct) + "): &a→ &f"
                        + plugin.getConfigManager().formatMoney(taxAfterShop));
            if (resTaxReduct > 0)
                lore.add("&7  - Research Tax Efficiency (" + String.format("-%.0f%%", resTaxReduct) + "): &a→ &f"
                        + plugin.getConfigManager().formatMoney(finalTax));
            lore.add("&c  ▶ Final Tax/Cycle: &f&l" + plugin.getConfigManager().formatMoney(finalTax));
            lore.add("");
            lore.add("&e📊 Net (per cycle):");
            double net = finalIncome - finalTax;
            String netColor = net >= 0 ? "&a" : "&c";
            lore.add(netColor + "  ▶ Net Income: &f&l" + plugin.getConfigManager().formatMoney(net));
        }

        ItemStack item = new ItemBuilder(Material.GOLD_INGOT)
                .name("&6📐 Section 4: Final Calculation")
                .lore(lore)
                .build();
        inventory.setItem(FINAL_MATH_SLOT, item);
    }


    // =====================================================================
    // Section 5 — Other / Lifetime Statistics
    // =====================================================================

    private void addOtherStats(Apartment apt) {
        long now = System.currentTimeMillis();
        ApartmentStats stats = plugin.getApartmentManager().getStats(apt.id);
        if (stats == null)
            stats = new ApartmentStats();

        double totalUnpaid = apt.getTotalUnpaid();
        long unpaidCount = apt.taxInvoices == null ? 0 : apt.taxInvoices.stream().filter(inv -> !inv.isPaid()).count();
        long oldestDays = 0;
        if (apt.taxInvoices != null) {
            oldestDays = apt.taxInvoices.stream().filter(inv -> !inv.isPaid())
                    .mapToLong(inv -> inv.daysSinceCreated(now))
                    .max().orElse(0);
        }

        // Pending income vs capacity
        LevelConfig cfg = plugin.getConfigManager().getLevelConfig(apt.level);
        double capacity = cfg != null ? cfg.incomeCapacity : 0;
        double vaultPct = (apt.owner != null && plugin.getResearchManager() != null)
                ? plugin.getResearchManager().getIncomeCapacityBonus(apt.owner)
                : 0;
        double shopVaultPct = plugin.getShopManager() != null
                ? plugin.getShopManager().getIncomeCapacityBonusPercentage(apt.id)
                : 0;
        double finalCap = (vaultPct > 0 || shopVaultPct > 0) ? capacity * (1.0 + ((vaultPct + shopVaultPct) / 100.0))
                : capacity;
        double fillPct = finalCap > 0 ? (apt.pendingIncome / finalCap) * 100.0 : 0;

        List<String> lore = new ArrayList<>();
        lore.add("&8▸ Lifetime & current financial statistics");
        lore.add("");
        lore.add("&e📈 Lifetime Earnings:");
        lore.add("&7• Total Income Generated: &a" + plugin.getConfigManager().formatMoney(stats.totalIncomeGenerated));
        lore.add("&7• Total Tax Paid: &c" + plugin.getConfigManager().formatMoney(stats.totalTaxPaid));
        lore.add("&7• Ownership Age: &f" + stats.ownershipAgeDays + " day(s)");
        lore.add("");
        lore.add("&e💰 Current Financial:");
        lore.add("&7• Pending Income: &a" + plugin.getConfigManager().formatMoney(apt.pendingIncome)
                + " &7(" + String.format("%.1f%%", Math.min(fillPct, 100.0)) + " full)");
        lore.add("&7• Income Capacity: &b" + plugin.getConfigManager().formatMoney(finalCap));
        lore.add("&7• Outstanding Taxes: &c" + plugin.getConfigManager().formatMoney(totalUnpaid));
        lore.add("&7• Unpaid Bills: &f" + unpaidCount
                + (unpaidCount > 0 ? " &7(Oldest: &f" + oldestDays + "d&7)" : ""));
        lore.add("");
        lore.add("&e🏠 Apartment Status:");
        lore.add("&7• Auto-Pay Taxes: " + (apt.autoTaxPayment ? "&aEnabled" : "&cDisabled"));
        lore.add("&7• Market Listed: "
                + (apt.marketListing ? "&aYes — &f" + plugin.getConfigManager().formatMoney(apt.marketPrice) : "&7No"));
        lore.add("&7• Upgrade In Progress: " + (apt.upgradeInProgress ? "&eYes" : "&7No"));
        lore.add("");
        ApartmentRating rating = plugin.getApartmentManager().getRating(apartmentId);
        if (rating != null && rating.ratingCount > 0) {
            lore.add("&7• Community Rating: &f"
                    + String.format("%.1f⭐ (%d reviews)", rating.getAverageRating(), rating.ratingCount));
        } else {
            lore.add("&7• Community Rating: &8No ratings yet");
        }

        ItemStack item = new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&e📋 Section 5: Other Statistics")
                .lore(lore)
                .build();
        inventory.setItem(OTHER_STATS_SLOT, item);
    }

    // =====================================================================
    // Status & Meta (bottom row)
    // =====================================================================

    private void addStatusAndMeta(Apartment apt) {
        long now = System.currentTimeMillis();
        TaxStatus status = apt.computeTaxStatus(now);

        // Status card
        Material statusMat;
        String statusTitle;
        List<String> statusLore = new ArrayList<>();
        switch (status) {
            case ACTIVE:
                statusMat = Material.EMERALD_BLOCK;
                statusTitle = "&a✅ Active";
                statusLore.add("&7Apartment is fully functional.");
                statusLore.add("&7Income is being generated normally.");
                break;
            case OVERDUE:
                statusMat = Material.GOLD_BLOCK;
                statusTitle = "&6⚠️ Overdue";
                statusLore.add("&7Income generation has stopped.");
                statusLore.add("&7Pay taxes to restore functionality.");
                break;
            case INACTIVE:
                statusMat = Material.RED_CONCRETE;
                statusTitle = "&c❌ Inactive";
                statusLore.add("&cApartment functions are frozen.");
                break;
            case REPOSSESSION:
                statusMat = Material.BARRIER;
                statusTitle = "&4💀 Repossession Risk";
                statusLore.add("&4Unpaid bills may cause repossession!");
                break;
            default:
                statusMat = Material.STONE;
                statusTitle = "&7Unknown";
        }
        inventory.setItem(STATUS_SLOT, new ItemBuilder(statusMat)
                .name("&6🏠 Status: " + statusTitle)
                .lore(statusLore)
                .build());

        // Rating card
        ApartmentRating rating = plugin.getApartmentManager().getRating(apartmentId);
        String ratingDisplay = (rating != null && rating.ratingCount > 0)
                ? String.format("&f%.1f⭐ &7(%d reviews)", rating.getAverageRating(), rating.ratingCount)
                : "&7No ratings yet";
        inventory.setItem(RATING_SLOT, new ItemBuilder(Material.NETHER_STAR)
                .name("&6⭐ Community Rating")
                .lore(
                        ratingDisplay,
                        "",
                        "&7Rate via command:",
                        "&f/apartmentcore rate " + apartmentId + " <0-10>")
                .build());

        // Guestbook card
        var guestbook = plugin.getApartmentManager().getGuestBooks().get(apartmentId);
        int messageCount = guestbook != null ? guestbook.size() : 0;
        inventory.setItem(GUESTBOOK_SLOT, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&6📖 Guestbook")
                .lore(
                        "&7View or manage the guestbook",
                        "",
                        "&7Messages: &f" + messageCount + "&7/" + plugin.getConfigManager().getGuestBookMaxMessages(),
                        "",
                        "&a▶ Click to open")
                .build());
    }

    // =====================================================================
    // Click Handler
    // =====================================================================

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);

        Apartment apt = plugin.getApartmentManager().getApartment(apartmentId);
        if (apt == null) {
            GUIUtils.sendMessage(player, "&cApartment not found!");
            player.closeInventory();
            return;
        }

        switch (slot) {
            case BACK_SLOT:
                guiManager.openStatistics(player);
                break;

            case OPEN_DETAILS_SLOT:
                guiManager.openApartmentDetails(player, apartmentId);
                break;

            case GUESTBOOK_SLOT:
                guiManager.openGuestbook(player, apartmentId);
                break;
        }
    }

    // =====================================================================
    // GUI Interface Methods
    // =====================================================================

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
        // Nothing special
    }

    // =====================================================================
    // Utility
    // =====================================================================

    /**
     * Format a research buff value for display.
     * Returns colored "+X%" or "&8Not researched" when zero.
     */
    private String formatResearchBuff(double value, String unit, boolean positive) {
        if (value <= 0)
            return "&8Not researched";
        String prefix = positive ? "+" : "-";
        if (unit.equals("%")) {
            return "&a" + prefix + String.format("%.0f%%", value) + " &7(active)";
        }
        return "&a" + prefix + String.format("%.0f", value) + unit + " &7(active)";
    }
}