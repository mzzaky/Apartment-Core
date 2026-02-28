package com.aithor.apartmentcore.gui.menus;

import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.model.Apartment;
import com.aithor.apartmentcore.model.ApartmentRating;
import com.aithor.apartmentcore.model.ApartmentStats;
import com.aithor.apartmentcore.model.LevelConfig;
import com.aithor.apartmentcore.model.TaxStatus;
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
import java.util.concurrent.TimeUnit;

/**
 * Detailed view and management GUI for a specific apartment
 */
public class ApartmentDetailsGUI implements GUI {

    private final Player player;
    private final ApartmentCore plugin;
    private final GUIManager guiManager;
    private final String apartmentId;
    private final String title;
    private final Inventory inventory;

    // Slot positions
    private static final int BACK_SLOT = 0;
    private static final int APARTMENT_INFO_SLOT = 4;
    private static final int TELEPORT_SLOT = 10;
    private static final int CLAIM_INCOME_SLOT = 12;
    private static final int UPGRADE_SLOT = 14;
    private static final int BUY_SLOT = 16;
    private static final int SET_NAME_SLOT = 19;
    private static final int SET_WELCOME_SLOT = 21;
    private static final int SET_TELEPORT_SLOT = 23;
    private static final int QUICK_SELL_SLOT = 24;
    private static final int MARKET_SELL_SLOT = 25;
    private static final int GUESTBOOK_SLOT = 28;
    private static final int STATISTICS_SLOT = 30;
    private static final int TAX_INFO_SLOT = 32;
    private static final int RATE_SLOT = 34;

    public ApartmentDetailsGUI(Player player, ApartmentCore plugin, GUIManager guiManager, String apartmentId) {
        this.player = player;
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.apartmentId = apartmentId;
        this.title = ChatColor.DARK_PURPLE + "Apartment Details";
        this.inventory = Bukkit.createInventory(null, 45, this.title);
    }

    @Override
    public void open(Player player) {
        setupInventory();
        player.openInventory(inventory);
    }

    private void setupInventory() {
        inventory.clear();

        Apartment apartment = plugin.getApartmentManager().getApartment(apartmentId);
        if (apartment == null) {
            // Apartment not found
            ItemStack errorItem = new ItemBuilder(Material.BARRIER)
                    .name("&cApartment Not Found")
                    .lore("&7The requested apartment could not be found")
                    .build();
            inventory.setItem(22, errorItem);
            return;
        }

        addBorder();
        addApartmentInfo(apartment);
        addActionButtons(apartment);
    }

    private void addBorder() {
        ItemStack borderItem = ItemBuilder.filler(Material.GRAY_STAINED_GLASS_PANE);

        // Add border around the GUI
        int[] borderSlots = { 0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44 };
        for (int slot : borderSlots) {
            inventory.setItem(slot, borderItem);
        }
    }

    private void addApartmentInfo(Apartment apartment) {
        long now = System.currentTimeMillis();
        TaxStatus taxStatus = apartment.computeTaxStatus(now);
        ApartmentRating rating = plugin.getApartmentManager().getRating(apartmentId);
        ApartmentStats stats = plugin.getApartmentManager().getStats(apartmentId);

        // Main apartment display
        String ownerName = apartment.owner != null ? Bukkit.getOfflinePlayer(apartment.owner).getName()
                : "Available for Purchase";

        String ratingDisplay = rating != null && rating.ratingCount > 0
                ? String.format("%.1f⭐ (%d reviews)", rating.getAverageRating(), rating.ratingCount)
                : "No ratings yet";

        // Build comprehensive lore
        List<String> lore = new ArrayList<>();
        lore.add("&7ID: &f" + apartmentId);
        lore.add("&7Owner: &f" + ownerName);
        lore.add("&7Location: &f" + apartment.worldName);
        lore.add("");

        // Financial info
        lore.add("&e💰 Financial Information:");
        lore.add("&7• Price: &a" + plugin.getConfigManager().formatMoney(apartment.price));
        if (apartment.owner != null) {
            lore.add("&7• Pending Income: &a" + plugin.getConfigManager().formatMoney(apartment.pendingIncome));
            lore.add("&7• Tax Arrears: &c" + plugin.getConfigManager().formatMoney(apartment.getTotalUnpaid()));
        }
        lore.add("");

        // Level and income info
        LevelConfig levelConfig = plugin.getConfigManager().getLevelConfig(apartment.level);
        if (levelConfig != null) {
            lore.add("&e📊 Level Information:");
            lore.add("&7• Current Level: &f" + apartment.level + "/5");
            lore.add("&7• Hourly Income: &a" + plugin.getConfigManager().formatMoney(levelConfig.minIncome) +
                    " &7- &a" + plugin.getConfigManager().formatMoney(levelConfig.maxIncome));
            lore.add("&7• Vault Capacity: &a" + plugin.getConfigManager().formatMoney(levelConfig.incomeCapacity));

            if (apartment.level < 5) {
                LevelConfig nextLevel = plugin.getConfigManager().getLevelConfig(apartment.level + 1);
                if (nextLevel != null) {
                    lore.add("&7• Upgrade Cost: &e" + plugin.getConfigManager().formatMoney(nextLevel.upgradeCost));
                }
            }
        }
        lore.add("");

        // Status info
        lore.add("&e🏠 Status Information:");
        String statusDisplay = getStatusDisplay(taxStatus);
        lore.add("&7• Tax Status: " + statusDisplay);
        lore.add("&7• Can Generate Income: " + (apartment.canGenerateIncome(now) ? "&aYes" : "&cNo"));
        if (apartment.owner != null) {
            lore.add("&7• Auto-pay Taxes: " + (apartment.autoTaxPayment ? "&aEnabled" : "&cDisabled"));
        }
        lore.add("");

        // Rating and stats
        lore.add("&e⭐ Community Information:");
        lore.add("&7• Rating: &f" + ratingDisplay);
        if (stats != null) {
            lore.add("&7• Ownership Age: &f" + stats.ownershipAgeDays + " days");
            lore.add("&7• Total Income Generated: &a"
                    + plugin.getConfigManager().formatMoney(stats.totalIncomeGenerated));
        }

        // Determine material based on status
        Material material = getMaterialForStatus(taxStatus, apartment.owner != null);

        ItemStack infoItem = new ItemBuilder(material)
                .name("&6🏠 " + apartment.displayName)
                .lore(lore)
                .build();

        inventory.setItem(APARTMENT_INFO_SLOT, infoItem);

        // Back button
        ItemStack backItem = new ItemBuilder(Material.ARROW)
                .name("&c◀ Back")
                .lore("&7Return to previous menu")
                .build();
        inventory.setItem(BACK_SLOT, backItem);
    }

    private void addActionButtons(Apartment apartment) {
        boolean isOwner = apartment.owner != null && apartment.owner.equals(player.getUniqueId());
        boolean isAvailable = apartment.owner == null;

        // Teleport (available to owners or for preview if has permission)
        if (isOwner || player.hasPermission("apartmentcore.preview")) {
            ItemStack teleportItem = new ItemBuilder(Material.ENDER_PEARL)
                    .name("&a🚪 Teleport")
                    .lore(
                            isOwner ? "&7Teleport to your apartment" : "&7Preview this apartment",
                            "",
                            "&a▶ Click to teleport")
                    .glow()
                    .build();
            inventory.setItem(TELEPORT_SLOT, teleportItem);
        }

        // Claim Income (owners only)
        if (isOwner) {
            boolean hasIncome = apartment.pendingIncome > 0;
            ItemBuilder claimItemBuilder = new ItemBuilder(hasIncome ? Material.EMERALD : Material.GRAY_DYE)
                    .name("&a💰 Claim Income")
                    .lore(
                            "&7Claim pending rental income",
                            "",
                            "&7Amount: &a" + plugin.getConfigManager().formatMoney(apartment.pendingIncome),
                            "",
                            hasIncome ? "&a▶ Click to claim" : "&7No income to claim");

            if (hasIncome) {
                claimItemBuilder.glow();
            }

            inventory.setItem(CLAIM_INCOME_SLOT, claimItemBuilder.build());
        }

        // Buy (non-owners, if available - government sale)
        if (isAvailable) {
            boolean canAfford = plugin.getEconomy().has(player, apartment.price);
            Material buyMaterial = canAfford ? Material.GOLD_BLOCK : Material.RED_CONCRETE;

            ItemStack buyItem = new ItemBuilder(buyMaterial)
                    .name("&a🛒 Purchase Apartment")
                    .lore(
                            "&7Buy this apartment",
                            "",
                            "&7Price: &a" + plugin.getConfigManager().formatMoney(apartment.price),
                            "&7Your Balance: &f"
                                    + plugin.getConfigManager().formatMoney(plugin.getEconomy().getBalance(player)),
                            "",
                            canAfford ? "&a▶ Click to purchase" : "&cInsufficient funds")
                    .glow()
                    .build();
            inventory.setItem(BUY_SLOT, buyItem);
        }

        // Market Buy (non-owners, if listed on market)
        if (!isOwner && !isAvailable && apartment.marketListing) {
            boolean canAfford = plugin.getEconomy().has(player, apartment.marketPrice);
            String sellerName = apartment.owner != null
                    ? Bukkit.getOfflinePlayer(apartment.owner).getName()
                    : "Unknown";
            Material buyMaterial = canAfford ? Material.GOLD_BLOCK : Material.RED_CONCRETE;

            ItemStack marketBuyItem = new ItemBuilder(buyMaterial)
                    .name("&a🛒 Buy from Market")
                    .lore(
                            "&7Purchase this apartment from",
                            "&7the current owner",
                            "",
                            "&7Seller: &f" + sellerName,
                            "&7Price: &a" + plugin.getConfigManager().formatMoney(apartment.marketPrice),
                            "&7Your Balance: &f"
                                    + plugin.getConfigManager().formatMoney(plugin.getEconomy().getBalance(player)),
                            "",
                            canAfford ? "&a▶ Click to purchase" : "&cInsufficient funds")
                    .glow()
                    .build();
            inventory.setItem(BUY_SLOT, marketBuyItem);
        }

        // Upgrade (owners only, if not max level)
        if (isOwner && apartment.level < 5) {
            LevelConfig nextLevel = plugin.getConfigManager().getLevelConfig(apartment.level + 1);
            if (nextLevel != null) {
                if (apartment.upgradeInProgress) {
                    long remainingMillis = apartment.upgradeCompleteAt - System.currentTimeMillis();
                    String timeStr = remainingMillis > 0 ? formatTime(remainingMillis) : "Completing...";

                    ItemStack upgradingItem = new ItemBuilder(Material.CLOCK)
                            .name("&e⏳ Upgrading...")
                            .lore(
                                    "&7Level " + apartment.level + " ➔ " + (apartment.level + 1),
                                    "",
                                    "&7Time Remaining: &f" + timeStr,
                                    "",
                                    "&c✘ Please wait")
                            .glow()
                            .build();
                    inventory.setItem(UPGRADE_SLOT, upgradingItem);
                } else {
                    boolean canAffordUpgrade = plugin.getEconomy().has(player, nextLevel.upgradeCost);
                    Material upgradeMaterial = canAffordUpgrade ? Material.DIAMOND : Material.RED_CONCRETE;

                    ItemStack upgradeItem = new ItemBuilder(upgradeMaterial)
                            .name("&a🔨 Upgrade Apartment")
                            .lore(
                                    "&7Upgrade to level " + (apartment.level + 1),
                                    "",
                                    "&7Cost: &e" + plugin.getConfigManager().formatMoney(nextLevel.upgradeCost),
                                    "&7New Income: &a" + plugin.getConfigManager().formatMoney(nextLevel.minIncome) +
                                            " &7- &a" + plugin.getConfigManager().formatMoney(nextLevel.maxIncome),
                                    "",
                                    canAffordUpgrade ? "&a▶ Click to upgrade" : "&cInsufficient funds")
                            .glow()
                            .build();
                    inventory.setItem(UPGRADE_SLOT, upgradeItem);
                }
            }
        }

        // Management options (owners only)
        if (isOwner) {
            // Set Name
            ItemStack setNameItem = new ItemBuilder(Material.NAME_TAG)
                    .name("&a📝 Set Display Name")
                    .lore(
                            "&7Change the apartment's display name",
                            "",
                            "&7Current: &f" + apartment.displayName,
                            "",
                            "&a▶ Click to change")
                    .build();
            inventory.setItem(SET_NAME_SLOT, setNameItem);

            // Set Welcome Message
            String welcomePreview = apartment.welcomeMessage.length() > 30
                    ? apartment.welcomeMessage.substring(0, 27) + "..."
                    : apartment.welcomeMessage;

            ItemStack setWelcomeItem = new ItemBuilder(Material.PAPER)
                    .name("&a💬 Set Welcome Message")
                    .lore(
                            "&7Set a welcome message for visitors",
                            "",
                            "&7Current: &f" + (apartment.welcomeMessage.isEmpty() ? "None" : welcomePreview),
                            "",
                            "&a▶ Click to change")
                    .build();
            inventory.setItem(SET_WELCOME_SLOT, setWelcomeItem);

            // Set Teleport Location
            ItemStack setTeleportItem = new ItemBuilder(Material.COMPASS)
                    .name("&a📍 Set Teleport Location")
                    .lore(
                            "&7Set custom teleport location",
                            "",
                            "&7Status: "
                                    + (apartment.hasCustomTeleport ? "&aCustom location set" : "&7Using region center"),
                            "",
                            "&a▶ Click to set at current location")
                    .build();
            inventory.setItem(SET_TELEPORT_SLOT, setTeleportItem);

            // Quick Sell
            double sellPrice = apartment.price * plugin.getConfigManager().getSellPercentage();
            boolean canSell = apartment.getTotalUnpaid() <= 0; // Can't sell with unpaid taxes

            ItemStack quickSellItem = new ItemBuilder(canSell ? Material.RED_CONCRETE : Material.BARRIER)
                    .name("&c⚡ Quick Sell")
                    .lore(
                            "&7Sell apartment back to the government",
                            "&7(instant sale at reduced price)",
                            "",
                            "&7You will receive: &a" + plugin.getConfigManager().formatMoney(sellPrice),
                            "&7(" + String.format("%.0f%%", plugin.getConfigManager().getSellPercentage() * 100)
                                    + " of purchase price)",
                            "",
                            canSell ? "&c▶ Click to quick sell" : "&cCannot sell with unpaid taxes")
                    .build();
            inventory.setItem(QUICK_SELL_SLOT, quickSellItem);

            // Market Sell
            boolean canMarketSell = canSell && !apartment.upgradeInProgress;
            if (apartment.marketListing) {
                // Already listed - show cancel option
                ItemStack cancelListingItem = new ItemBuilder(Material.ORANGE_CONCRETE)
                        .name("&6📢 Listed on Market")
                        .lore(
                                "&7Your apartment is listed on the market",
                                "",
                                "&7Listed Price: &a" + plugin.getConfigManager().formatMoney(apartment.marketPrice),
                                "",
                                "&c▶ Click to cancel listing")
                        .glow()
                        .build();
                inventory.setItem(MARKET_SELL_SLOT, cancelListingItem);
            } else {
                ItemStack marketSellItem = new ItemBuilder(canMarketSell ? Material.YELLOW_CONCRETE : Material.BARRIER)
                        .name("&e📢 Market Sell")
                        .lore(
                                "&7List apartment on the market",
                                "&7for other players to buy",
                                "",
                                "&7Listing Price: &a" + plugin.getConfigManager().formatMoney(apartment.price),
                                "&7(ownership transfer on purchase)",
                                "",
                                canMarketSell ? "&e▶ Click to list on market"
                                        : (!canSell ? "&cCannot sell with unpaid taxes"
                                                : "&cCannot list while upgrading"))
                        .build();
                inventory.setItem(MARKET_SELL_SLOT, marketSellItem);
            }
        }

        // Guestbook (everyone can view, owners can manage)
        var guestbook = plugin.getApartmentManager().getGuestBooks().get(apartmentId);
        int messageCount = guestbook != null ? guestbook.size() : 0;

        ItemStack guestbookItem = new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&a📖 Guestbook")
                .lore(
                        "&7View or manage the guestbook",
                        "",
                        "&7Messages: &f" + messageCount + "&7/" + plugin.getConfigManager().getGuestBookMaxMessages(),
                        "",
                        "&a▶ Click to open")
                .build();
        inventory.setItem(GUESTBOOK_SLOT, guestbookItem);

        // Statistics
        ItemStack statsItem = new ItemBuilder(Material.BOOK)
                .name("&a📊 Statistics")
                .lore(
                        "&7View detailed statistics",
                        "",
                        "&a▶ Click to view")
                .build();
        inventory.setItem(STATISTICS_SLOT, statsItem);

        // Tax Information (owners only)
        if (isOwner) {
            long activeInvoices = apartment.taxInvoices != null
                    ? apartment.taxInvoices.stream().filter(inv -> !inv.isPaid()).count()
                    : 0;

            ItemStack taxItem = new ItemBuilder(Material.GOLD_INGOT)
                    .name("&a💰 Tax Information")
                    .lore(
                            "&7View tax details and payment options",
                            "",
                            "&7Active Invoices: &f" + activeInvoices,
                            "&7Total Due: &c" + plugin.getConfigManager().formatMoney(apartment.getTotalUnpaid()),
                            "",
                            "&a▶ Click for details")
                    .build();
            inventory.setItem(TAX_INFO_SLOT, taxItem);
        }

        // Rate Apartment (non-owners only)
        if (!isOwner && apartment.owner != null) {
            ItemStack rateItem = new ItemBuilder(Material.NETHER_STAR)
                    .name("&a⭐ Rate Apartment")
                    .lore(
                            "&7Rate this apartment (0-10)",
                            "",
                            "&a▶ Click to rate")
                    .glow()
                    .build();
            inventory.setItem(RATE_SLOT, rateItem);
        }
    }

    private String getStatusDisplay(TaxStatus status) {
        switch (status) {
            case ACTIVE:
                return "&a✅ Active";
            case OVERDUE:
                return "&6⚠️ Overdue";
            case INACTIVE:
                return "&c❌ Inactive";
            case REPOSSESSION:
                return "&4💀 Repossession";
            default:
                return "&7Unknown";
        }
    }

    private Material getMaterialForStatus(TaxStatus status, boolean isOwned) {
        if (!isOwned)
            return Material.GOLD_BLOCK; // For sale

        switch (status) {
            case ACTIVE:
                return Material.EMERALD_BLOCK;
            case OVERDUE:
                return Material.GOLD_BLOCK;
            case INACTIVE:
                return Material.RED_CONCRETE;
            case REPOSSESSION:
                return Material.BARRIER;
            default:
                return Material.STONE;
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        Apartment apartment = plugin.getApartmentManager().getApartment(apartmentId);
        if (apartment == null) {
            GUIUtils.sendMessage(player, "&cApartment not found!");
            player.closeInventory();
            return;
        }

        GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);

        switch (slot) {
            case BACK_SLOT:
                // Return to appropriate previous menu
                if (apartment.owner != null && apartment.owner.equals(player.getUniqueId())) {
                    guiManager.openMyApartments(player);
                } else {
                    guiManager.openApartmentBrowser(player);
                }
                break;

            case TELEPORT_SLOT:
                handleTeleport(apartment);
                break;

            case CLAIM_INCOME_SLOT:
                handleClaimIncome(apartment);
                break;

            case BUY_SLOT:
                handleBuy(apartment);
                break;

            case UPGRADE_SLOT:
                handleUpgrade(apartment);
                break;

            case SET_NAME_SLOT:
                handleSetName();
                break;

            case SET_WELCOME_SLOT:
                handleSetWelcome();
                break;

            case SET_TELEPORT_SLOT:
                handleSetTeleport(apartment);
                break;

            case QUICK_SELL_SLOT:
                handleQuickSell(apartment);
                break;

            case MARKET_SELL_SLOT:
                handleMarketSell(apartment);
                break;

            case GUESTBOOK_SLOT:
                guiManager.openGuestbook(player, apartmentId);
                break;

            case STATISTICS_SLOT:
                handleStatistics();
                break;

            case TAX_INFO_SLOT:
                guiManager.openTaxManagement(player);
                break;

            case RATE_SLOT:
                handleRate();
                break;
        }
    }

    private String formatTime(long millis) {
        if (millis < 0)
            return "0s";
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0)
            sb.append(days).append("d ");
        if (hours > 0)
            sb.append(hours).append("h ");
        if (minutes > 0)
            sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    private void handleTeleport(Apartment apartment) {
        player.closeInventory();
        plugin.getApartmentManager().teleportToApartment(player, apartmentId,
                !apartment.owner.equals(player.getUniqueId())); // Admin mode if not owner (for preview)
    }

    private void handleClaimIncome(Apartment apartment) {
        if (apartment.pendingIncome <= 0) {
            GUIUtils.sendMessage(player, "&cNo income to claim!");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }
        player.closeInventory();
        plugin.getServer().dispatchCommand(player, "apartmentcore rent claim " + apartmentId);
    }

    private void handleBuy(Apartment apartment) {
        player.closeInventory();
        if (apartment.marketListing && apartment.owner != null) {
            // Market buy - ownership transfer
            plugin.getServer().dispatchCommand(player, "apartmentcore marketbuy " + apartmentId);
        } else {
            // Regular buy from government
            plugin.getServer().dispatchCommand(player, "apartmentcore buy " + apartmentId);
        }
    }

    private void handleUpgrade(Apartment apartment) {
        if (apartment.upgradeInProgress) {
            GUIUtils.sendMessage(player, "&cAn upgrade is already in progress!");
            player.closeInventory();
            return;
        }
        guiManager.openUpgradeConfirm(player, apartmentId);
    }

    private void handleSetName() {
        player.closeInventory();
        GUIUtils.sendMessage(player, "&eUse command: &f/apartmentcore setname " + apartmentId + " <name>");
    }

    private void handleSetWelcome() {
        player.closeInventory();
        GUIUtils.sendMessage(player, "&eUse command: &f/apartmentcore setwelcome " + apartmentId + " <message>");
    }

    private void handleSetTeleport(Apartment apartment) {
        player.closeInventory();
        plugin.getServer().dispatchCommand(player, "apartmentcore setteleport " + apartmentId);
    }

    private void handleQuickSell(Apartment apartment) {
        player.closeInventory();
        plugin.getServer().dispatchCommand(player, "apartmentcore sell quick " + apartmentId);
    }

    private void handleMarketSell(Apartment apartment) {
        player.closeInventory();
        if (apartment.marketListing) {
            // Cancel listing
            plugin.getServer().dispatchCommand(player, "apartmentcore sell cancel " + apartmentId);
        } else {
            // List on market: request custom price from user
            guiManager.requestMarketPriceInput(player, apartmentId);
        }
    }

    private void handleStatistics() {
        // Open per-apartment statistics on next tick to avoid modifying inventories
        // mid-event
        plugin.getServer().getScheduler().runTask(plugin,
                () -> guiManager.openApartmentStatistics(player, apartmentId));
    }

    private void handleRate() {
        player.closeInventory();
        GUIUtils.sendMessage(player, "&eUse command: &f/apartmentcore rate " + apartmentId + " <0-10>");
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