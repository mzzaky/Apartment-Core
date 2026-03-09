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
import com.aithor.apartmentcore.gui.config.MainMenuConfig;
import com.aithor.apartmentcore.gui.interfaces.GUI;
import com.aithor.apartmentcore.gui.items.ItemBuilder;
import com.aithor.apartmentcore.gui.utils.GUIUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main menu GUI - Central hub for all apartment functions.
 * Layout, titles, materials, names, and lore are driven by
 * {@code custom_gui/main_menu.yml}. Every value falls back to a
 * hardcoded default when the config key is absent.
 */
public class MainMenuGUI implements GUI {

    private final Player player;
    private final ApartmentCore plugin;
    private final GUIManager guiManager;
    private final MainMenuConfig menuConfig;
    private final String title;
    private final Inventory inventory;

    public MainMenuGUI(Player player, ApartmentCore plugin, GUIManager guiManager) {
        this.player = player;
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.menuConfig = plugin.getMainMenuConfig();
        this.title = ChatColor.translateAlternateColorCodes('&', menuConfig.getTitle());
        this.inventory = Bukkit.createInventory(null, menuConfig.getSize(), this.title);
    }

    @Override
    public void open(Player player) {
        setupInventory();
        player.openInventory(inventory);
    }

    // ── Inventory setup ──────────────────────────────────────────

    private void setupInventory() {
        inventory.clear();

        if (menuConfig.isBorderEnabled()) {
            addBorder();
        }

        // Gather all dynamic placeholders once
        Map<String, String> placeholders = buildPlaceholders();

        addConfigItem("my_apartments", placeholders, false);
        addConfigItem("browse_buy", placeholders, false);
        addTaxManagementItem(placeholders);
        addAuctionHouseItem(placeholders);
        addResearchItem(placeholders);
        addConfigItem("statistics", placeholders, false);
        addAchievementItem(placeholders);
        addConfigItem("help_info", placeholders, false);

        if (menuConfig.isPlayerInfoEnabled()) {
            addPlayerInfo(placeholders);
        }
    }

    // ── Border ───────────────────────────────────────────────────

    private void addBorder() {
        ItemStack borderItem = ItemBuilder.filler(menuConfig.getBorderMaterial());
        int rows = Math.max(1, inventory.getSize() / 9);

        for (int i = 0; i < 9 && i < inventory.getSize(); i++) {
            inventory.setItem(i, borderItem);
        }

        int bottomStart = (rows - 1) * 9;
        for (int i = bottomStart; i < bottomStart + 9 && i < inventory.getSize(); i++) {
            inventory.setItem(i, borderItem);
        }

        for (int r = 1; r < rows - 1; r++) {
            int leftIndex = r * 9;
            int rightIndex = r * 9 + 8;
            if (leftIndex < inventory.getSize())
                inventory.setItem(leftIndex, borderItem);
            if (rightIndex < inventory.getSize())
                inventory.setItem(rightIndex, borderItem);
        }
    }

    // ── Generic config-driven item builder ───────────────────────

    private void addConfigItem(String key, Map<String, String> placeholders, boolean disabled) {
        Material material;
        String name;
        List<String> lore;

        if (disabled) {
            material = menuConfig.getDisabledMaterial(key);
            name = menuConfig.getDisabledName(key);
            lore = menuConfig.getDisabledLore(key);
        } else {
            material = menuConfig.getItemMaterial(key);
            name = menuConfig.getItemName(key);
            lore = menuConfig.getItemLore(key);
        }

        int slot = menuConfig.getItemSlot(key);
        int customModelData = menuConfig.getItemCustomModelData(key);
        boolean glow = !disabled && menuConfig.getItemGlow(key);

        // Resolve placeholders in name and lore
        name = replacePlaceholders(name, placeholders);
        List<String> resolvedLore = new ArrayList<>();
        for (String line : lore) {
            resolvedLore.add(replacePlaceholders(line, placeholders));
        }

        ItemBuilder builder = new ItemBuilder(material)
                .name(name)
                .lore(resolvedLore);

        if (customModelData > 0) {
            builder.modelData(customModelData);
        }
        if (glow) {
            builder.glow();
        }

        inventory.setItem(slot, builder.build());
    }

    // ── Items that require conditional logic ─────────────────────

    private void addTaxManagementItem(Map<String, String> placeholders) {
        // Dynamic material based on tax status
        double totalUnpaid = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToDouble(a -> a.getTotalUnpaid())
                .sum();

        // Override material from config default if taxes are due
        String configMat = menuConfig.getItemMaterial("tax_management").name();
        if (totalUnpaid > 0 && configMat.equals("GREEN_CONCRETE")) {
            // Switch to RED_CONCRETE when taxes are due (only if using default material)
            Material material = Material.RED_CONCRETE;
            int slot = menuConfig.getItemSlot("tax_management");
            int customModelData = menuConfig.getItemCustomModelData("tax_management");
            boolean glow = menuConfig.getItemGlow("tax_management");

            String name = replacePlaceholders(menuConfig.getItemName("tax_management"), placeholders);
            List<String> resolvedLore = new ArrayList<>();
            for (String line : menuConfig.getItemLore("tax_management")) {
                resolvedLore.add(replacePlaceholders(line, placeholders));
            }

            ItemBuilder builder = new ItemBuilder(material).name(name).lore(resolvedLore);
            if (customModelData > 0) builder.modelData(customModelData);
            if (glow) builder.glow();
            inventory.setItem(slot, builder.build());
        } else {
            addConfigItem("tax_management", placeholders, false);
        }
    }

    private void addAuctionHouseItem(Map<String, String> placeholders) {
        boolean disabled = plugin.getAuctionManager() == null || !plugin.getConfigManager().isAuctionEnabled();
        addConfigItem("auction_house", placeholders, disabled);
    }

    private void addResearchItem(Map<String, String> placeholders) {
        boolean disabled = plugin.getResearchManager() == null || !plugin.getResearchManager().isEnabled();
        addConfigItem("research", placeholders, disabled);
    }

    private void addAchievementItem(Map<String, String> placeholders) {
        boolean disabled = plugin.getAchievementManager() == null || !plugin.getAchievementManager().isEnabled();
        addConfigItem("achievements", placeholders, disabled);
    }

    private void addPlayerInfo(Map<String, String> placeholders) {
        int slot = menuConfig.getPlayerInfoSlot();
        String name = replacePlaceholders(menuConfig.getPlayerInfoName(), placeholders);
        List<String> resolvedLore = new ArrayList<>();
        for (String line : menuConfig.getPlayerInfoLore()) {
            resolvedLore.add(replacePlaceholders(line, placeholders));
        }

        ItemBuilder builder = new ItemBuilder(menuConfig.getPlayerInfoMaterial())
                .name(name)
                .lore(resolvedLore);

        // Apply skull if material is PLAYER_HEAD
        if (menuConfig.getPlayerInfoMaterial() == Material.PLAYER_HEAD) {
            builder.skull(player.getName());
        }

        inventory.setItem(slot, builder.build());
    }

    // ── Placeholder resolution ───────────────────────────────────

    private Map<String, String> buildPlaceholders() {
        Map<String, String> map = new HashMap<>();

        // Apartment counts
        long ownedCount = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .count();
        int maxApartments = plugin.getConfig().getInt("settings.max-apartments-per-player", 5);

        double totalPendingIncome = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToDouble(a -> a.pendingIncome)
                .sum();

        long availableCount = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> a.owner == null)
                .count();

        double cheapestPrice = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> a.owner == null)
                .mapToDouble(a -> a.price)
                .min()
                .orElse(0);

        // Tax info
        double totalUnpaid = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .mapToDouble(a -> a.getTotalUnpaid())
                .sum();

        long overdueCount = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .filter(a -> a.computeTaxStatus(System.currentTimeMillis()).ordinal() >= 1)
                .count();

        String taxStatusColor = totalUnpaid > 0 ? "&c" : "&a";
        String taxStatus = totalUnpaid > 0 ? "Taxes Due!" : "All Paid";

        // Statistics
        double totalIncomeGenerated = 0.0;
        double totalTaxPaid = 0.0;
        for (com.aithor.apartmentcore.model.Apartment a : plugin.getApartmentManager().getApartments().values()) {
            if (!player.getUniqueId().equals(a.owner)) continue;
            var st = plugin.getApartmentManager().getStats(a.id);
            if (st != null) {
                totalIncomeGenerated += st.totalIncomeGenerated;
                totalTaxPaid += st.totalTaxPaid;
            }
        }

        map.put("{owned_count}", String.valueOf(ownedCount));
        map.put("{max_apartments}", String.valueOf(maxApartments));
        map.put("{pending_income}", plugin.getConfigManager().formatMoney(totalPendingIncome));
        map.put("{available_count}", String.valueOf(availableCount));
        map.put("{cheapest_price}", cheapestPrice > 0 ? plugin.getConfigManager().formatMoney(cheapestPrice) : "N/A");
        map.put("{total_unpaid}", plugin.getConfigManager().formatMoney(totalUnpaid));
        map.put("{overdue_count}", String.valueOf(overdueCount));
        map.put("{tax_status_color}", taxStatusColor);
        map.put("{tax_status}", taxStatus);
        map.put("{lifetime_income}", plugin.getConfigManager().formatMoney(totalIncomeGenerated));
        map.put("{total_tax_paid}", plugin.getConfigManager().formatMoney(totalTaxPaid));
        map.put("{outstanding_taxes}", plugin.getConfigManager().formatMoney(totalUnpaid));

        // Auction
        if (plugin.getAuctionManager() != null && plugin.getConfigManager().isAuctionEnabled()) {
            map.put("{active_auctions}", String.valueOf(plugin.getAuctionManager().getActiveAuctions().size()));
            map.put("{auction_commission}",
                    String.format("%.1f%%", plugin.getConfigManager().getAuctionCommission() * 100));
        } else {
            map.put("{active_auctions}", "0");
            map.put("{auction_commission}", "N/A");
        }

        // Research
        if (plugin.getResearchManager() != null && plugin.getResearchManager().isEnabled()) {
            com.aithor.apartmentcore.research.ResearchManager rm = plugin.getResearchManager();
            com.aithor.apartmentcore.research.PlayerResearchData data = rm.getPlayerData(player.getUniqueId());
            int totalCompleted = 0;
            int totalMax = 0;
            for (com.aithor.apartmentcore.research.ResearchType type : com.aithor.apartmentcore.research.ResearchType.values()) {
                totalCompleted += data.getCompletedTier(type);
                totalMax += type.getMaxTier();
            }

            String statusLine;
            if (data.hasActiveResearch()) {
                statusLine = "&e Researching: &f" + data.getActiveResearch().getDisplayName();
            } else {
                statusLine = "&7 No active research";
            }

            map.put("{research_completed}", String.valueOf(totalCompleted));
            map.put("{research_max}", String.valueOf(totalMax));
            map.put("{research_status}", statusLine);
        } else {
            map.put("{research_completed}", "0");
            map.put("{research_max}", "0");
            map.put("{research_status}", "&7 No active research");
        }

        // Achievements
        if (plugin.getAchievementManager() != null && plugin.getAchievementManager().isEnabled()) {
            com.aithor.apartmentcore.achievement.AchievementManager am = plugin.getAchievementManager();
            com.aithor.apartmentcore.achievement.PlayerAchievementData adata = am.getPlayerData(player.getUniqueId());
            int completed = adata.getCompletedCount();
            int total = adata.getTotalCount();
            String percentage = total > 0 ? String.format("%.0f%%", (completed * 100.0 / total)) : "0%";
            String progressBar = GUIUtils.createProgressBar(completed, total, 15);

            map.put("{achievement_completed}", String.valueOf(completed));
            map.put("{achievement_total}", String.valueOf(total));
            map.put("{achievement_percent}", percentage);
            map.put("{achievement_bar}", progressBar);
        } else {
            map.put("{achievement_completed}", "0");
            map.put("{achievement_total}", "0");
            map.put("{achievement_percent}", "0%");
            map.put("{achievement_bar}", "");
        }

        // Player info
        map.put("{player_name}", player.getName());
        map.put("{plugin_version}", plugin.getDescription().getVersion());
        map.put("{economy_name}", plugin.getEconomy().getName());
        map.put("{player_balance}", plugin.getConfigManager().formatMoney(plugin.getEconomy().getBalance(player)));

        return map;
    }

    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (text == null) return "";
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return text;
    }

    // ── Click handling (uses config-driven slot positions) ───────

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);

        if (slot == menuConfig.getItemSlot("my_apartments")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openMyApartments(player));
            return;
        }

        if (slot == menuConfig.getItemSlot("browse_buy")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openApartmentBrowser(player));
            return;
        }

        if (slot == menuConfig.getItemSlot("tax_management")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openTaxManagement(player));
            return;
        }

        if (slot == menuConfig.getItemSlot("auction_house")) {
            if (plugin.getAuctionManager() != null && plugin.getConfigManager().isAuctionEnabled()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openAuctionHouse(player));
            } else {
                GUIUtils.sendMessage(player, "&cAuction system is disabled!");
                GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            }
            return;
        }

        if (slot == menuConfig.getItemSlot("research")) {
            if (plugin.getResearchManager() != null && plugin.getResearchManager().isEnabled()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openResearch(player));
            } else {
                GUIUtils.sendMessage(player, "&cResearch system is disabled!");
                GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            }
            return;
        }

        if (slot == menuConfig.getItemSlot("statistics")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openStatistics(player));
            return;
        }

        if (slot == menuConfig.getItemSlot("achievements")) {
            if (plugin.getAchievementManager() != null && plugin.getAchievementManager().isEnabled()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openAchievements(player));
            } else {
                GUIUtils.sendMessage(player, "&cAchievement system is disabled!");
                GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            }
            return;
        }

        if (slot == menuConfig.getItemSlot("help_info")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                HelpInfoGUI helpInfoGUI = new HelpInfoGUI(player, plugin, guiManager);
                guiManager.openGUI(player, helpInfoGUI);
            });
            return;
        }
    }

    // ── GUI interface methods ────────────────────────────────────

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
