package com.aithor.apartmentcorei3.gui.menus;

import com.aithor.apartmentcorei3.Apartment;
import com.aithor.apartmentcorei3.ApartmentCorei3;
import com.aithor.apartmentcorei3.ApartmentRating;
import com.aithor.apartmentcorei3.ApartmentStats;
import com.aithor.apartmentcorei3.TaxInvoice;
import com.aithor.apartmentcorei3.TaxStatus;
import com.aithor.apartmentcorei3.gui.GUIManager;
import com.aithor.apartmentcorei3.gui.interfaces.GUI;
import com.aithor.apartmentcorei3.gui.items.ItemBuilder;
import com.aithor.apartmentcorei3.gui.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Per-apartment detailed statistics GUI
 */
public class ApartmentStatisticsGUI implements GUI {

    private final Player player;
    private final ApartmentCorei3 plugin;
    private final GUIManager guiManager;
    private final String apartmentId;
    private final String title;
    private final Inventory inventory;

    // Slots
    private static final int BACK_OVERVIEW_SLOT = 0;
    private static final int OPEN_DETAILS_SLOT = 8;

    private static final int SUMMARY_SLOT = 4;

    private static final int CLAIM_INCOME_SLOT = 20;
    private static final int TAX_MGMT_SLOT = 22;
    private static final int TOGGLE_AUTOPAY_SLOT = 24;

    private static final int STATUS_SLOT = 29;
    private static final int RATING_SLOT = 31;
    private static final int GUESTBOOK_SLOT = 33;

    public ApartmentStatisticsGUI(Player player, ApartmentCorei3 plugin, GUIManager guiManager, String apartmentId) {
        this.player = player;
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.apartmentId = apartmentId;
        this.title = ChatColor.DARK_AQUA + "Apartment Statistics";
        this.inventory = Bukkit.createInventory(null, 45, this.title);
    }

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
        addHeaderButtons();
        addSummary(apt);
        addActions(apt);
        addStatusAndMeta(apt);
    }

    private void addBorder() {
        ItemStack borderItem = ItemBuilder.filler(Material.GRAY_STAINED_GLASS_PANE);

        // Add border around the GUI
        int[] borderSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44};
        for (int slot : borderSlots) {
            inventory.setItem(slot, borderItem);
        }
    }

    private void addHeaderButtons() {
        // Back to Stats Overview
        ItemStack backOverview = new ItemBuilder(Material.ARROW)
                .name("&c◀ Back to Statistics Overview")
                .lore("&7Return to the global statistics")
                .build();
        inventory.setItem(BACK_OVERVIEW_SLOT, backOverview);

        // Open Apartment Details
        ItemStack openDetails = new ItemBuilder(Material.NETHER_STAR)
                .name("&a📋 Open Apartment Details")
                .lore("&7View management and actions for this apartment", "", "&a▶ Click to open details")
                .glow()
                .build();
        inventory.setItem(OPEN_DETAILS_SLOT, openDetails);
    }

    private void addSummary(Apartment apt) {
        long now = System.currentTimeMillis();
        ApartmentStats stats = plugin.getApartmentManager().getStats(apt.id);
        if (stats == null) stats = new ApartmentStats();

        double totalUnpaid = apt.getTotalUnpaid();
        long unpaidCount = apt.taxInvoices == null ? 0 : apt.taxInvoices.stream().filter(inv -> !inv.isPaid()).count();
        long oldestDays = 0;
        if (apt.taxInvoices != null) {
            oldestDays = apt.taxInvoices.stream().filter(inv -> !inv.isPaid())
                    .mapToLong(inv -> inv.daysSinceCreated(now))
                    .max().orElse(0);
        }

        ItemStack summary = new ItemBuilder(Material.BOOK)
                .name("&6📊 " + apt.displayName)
                .lore(
                        "&7ID: &f" + apt.id,
                        "&7Level: &f" + apt.level + "/5",
                        "",
                        "&e📈 Lifetime Stats:",
                        "&7• Ownership Age: &f" + stats.ownershipAgeDays + " days",
                        "&7• Total Income: &a" + plugin.getConfigManager().formatMoney(stats.totalIncomeGenerated),
                        "&7• Total Tax Paid: &c" + plugin.getConfigManager().formatMoney(stats.totalTaxPaid),
                        "",
                        "&e💰 Current Financial:",
                        "&7• Pending Income: &a" + plugin.getConfigManager().formatMoney(apt.pendingIncome),
                        "&7• Outstanding Taxes: &c" + plugin.getConfigManager().formatMoney(totalUnpaid),
                        "&7• Active Bills: &f" + unpaidCount + (unpaidCount > 0 ? " &7(Oldest: &f" + oldestDays + "d&7)" : ""),
                        "",
                        "&7Version: &f" + plugin.getDescription().getVersion(),
                        "&7Economy: &f" + plugin.getEconomy().getName()
                )
                .build();
        inventory.setItem(SUMMARY_SLOT, summary);
    }

    private void addActions(Apartment apt) {
        // Claim income (if available)
        if (apt.owner != null && apt.owner.equals(player.getUniqueId())) {
            Material mat = apt.pendingIncome > 0 ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE;
            ItemStack claim = new ItemBuilder(mat)
                    .name("&a💰 Claim Income")
                    .lore(
                            "&7Claim pending income for this apartment",
                            "",
                            "&7Amount: &a" + plugin.getConfigManager().formatMoney(apt.pendingIncome),
                            "",
                            apt.pendingIncome > 0 ? "&a▶ Click to claim" : "&7No income to claim"
                    )
                    .build();
            inventory.setItem(CLAIM_INCOME_SLOT, claim);
        }

        // Open Tax Management
        ItemStack taxMgmt = new ItemBuilder(Material.GOLD_INGOT)
                .name("&a💳 Tax Management")
                .lore(
                        "&7Open the tax management interface",
                        "&7to review and pay bills (includes",
                        "&7single-bill payment options).",
                        "",
                        "&a▶ Click to open"
                )
                .build();
        inventory.setItem(TAX_MGMT_SLOT, taxMgmt);

        // Toggle auto-pay for this apartment (owners only)
        if (apt.owner != null && apt.owner.equals(player.getUniqueId())) {
            Material toggleMat = apt.autoTaxPayment ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
            ItemStack toggleAuto = new ItemBuilder(toggleMat)
                    .name("&6⚙️ Auto-Pay (This Apartment)")
                    .lore(
                            "&7Toggle automatic tax payment",
                            "",
                            "&7Status: " + (apt.autoTaxPayment ? "&aEnabled" : "&cDisabled"),
                            "",
                            "&a▶ Click to toggle"
                    )
                    .build();
            inventory.setItem(TOGGLE_AUTOPAY_SLOT, toggleAuto);
        }
    }

    private void addStatusAndMeta(Apartment apt) {
        long now = System.currentTimeMillis();
        TaxStatus status = apt.computeTaxStatus(now);

        // Status card
        Material mat;
        String statusTitle;
        List<String> statusLore = new ArrayList<>();
        switch (status) {
            case ACTIVE:
                mat = Material.EMERALD_BLOCK;
                statusTitle = "&a✅ Active";
                statusLore.add("&7This apartment is fully functional.");
                break;
            case OVERDUE:
                mat = Material.GOLD_BLOCK;
                statusTitle = "&6⚠️ Overdue";
                statusLore.add("&7Income generation has stopped.");
                statusLore.add("&7Pay taxes to restore functionality.");
                break;
            case INACTIVE:
                mat = Material.RED_CONCRETE;
                statusTitle = "&c❌ Inactive";
                statusLore.add("&7Apartment functions are frozen.");
                break;
            case REPOSSESSION:
                mat = Material.BARRIER;
                statusTitle = "&4💀 Repossession Risk";
                statusLore.add("&4Unpaid bills may cause repossession!");
                break;
            default:
                mat = Material.STONE;
                statusTitle = "&7Unknown";
        }
        ItemStack statusItem = new ItemBuilder(mat)
                .name("&6🏠 Status: " + statusTitle)
                .lore(statusLore)
                .build();
        inventory.setItem(STATUS_SLOT, statusItem);

        // Rating card
        ApartmentRating rating = plugin.getApartmentManager().getRating(apartmentId);
        String ratingDisplay = (rating != null && rating.ratingCount > 0)
                ? String.format("&f%.1f⭐ &7(%d reviews)", rating.getAverageRating(), rating.ratingCount)
                : "&7No ratings yet";
        ItemStack ratingItem = new ItemBuilder(Material.NETHER_STAR)
                .name("&6⭐ Community Rating")
                .lore(
                        ratingDisplay,
                        "",
                        "&7Rate via command:",
                        "&f/apartmentcore rate " + apartmentId + " <0-10>"
                )
                .build();
        inventory.setItem(RATING_SLOT, ratingItem);

        // Guestbook card
        var guestbook = plugin.getApartmentManager().getGuestBooks().get(apartmentId);
        int messageCount = guestbook != null ? guestbook.size() : 0;
        ItemStack guestbookItem = new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&6📖 Guestbook")
                .lore(
                        "&7View or manage the guestbook",
                        "",
                        "&7Messages: &f" + messageCount + "&7/" + plugin.getConfigManager().getGuestBookMaxMessages(),
                        "",
                        "&a▶ Click to open"
                )
                .build();
        inventory.setItem(GUESTBOOK_SLOT, guestbookItem);
    }

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
            case BACK_OVERVIEW_SLOT:
                guiManager.openStatistics(player);
                break;

            case OPEN_DETAILS_SLOT:
                guiManager.openApartmentDetails(player, apartmentId);
                break;

            case CLAIM_INCOME_SLOT:
                if (apt.owner != null && apt.owner.equals(player.getUniqueId()) && apt.pendingIncome > 0) {
                    player.closeInventory();
                    // Keep consistency with other flows by using the command
                    plugin.getServer().dispatchCommand(player, "apartmentcore rent claim " + apartmentId);
                } else {
                    GUIUtils.sendMessage(player, "&cNo income to claim for this apartment.");
                    GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
                }
                break;

            case TAX_MGMT_SLOT:
                guiManager.openTaxManagement(player);
                break;

            case TOGGLE_AUTOPAY_SLOT:
                if (apt.owner != null && apt.owner.equals(player.getUniqueId())) {
                    apt.autoTaxPayment = !apt.autoTaxPayment;
                    plugin.getApartmentManager().saveApartments();
                    GUIUtils.sendMessage(player, "&aAuto-pay is now " + (apt.autoTaxPayment ? "&fenabled" : "&fdisabled") + " &afor this apartment.");
                    GUIUtils.playSound(player, GUIUtils.SUCCESS_SOUND);
                    refresh();
                } else {
                    GUIUtils.sendMessage(player, "&cYou do not own this apartment.");
                    GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
                }
                break;

            case GUESTBOOK_SLOT:
                guiManager.openGuestbook(player, apartmentId);
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
        // Nothing special
    }
}