package com.aithor.apartmentcore.gui.menus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.achievement.AchievementManager;
import com.aithor.apartmentcore.achievement.AchievementType;
import com.aithor.apartmentcore.achievement.PlayerAchievementData;
import com.aithor.apartmentcore.gui.GUIManager;
import com.aithor.apartmentcore.gui.interfaces.GUI;
import com.aithor.apartmentcore.gui.items.ItemBuilder;
import com.aithor.apartmentcore.gui.utils.GUIUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Achievement GUI - Displays all achievements with real-time progress tracking
 */
public class AchievementGUI implements GUI {

    private final Player player;
    private final ApartmentCore plugin;
    private final GUIManager guiManager;
    private final String title;
    private final Inventory inventory;

    // Slot positions for achievements
    private static final int INCOME_MILLIONAIRE_SLOT = 11;
    private static final int TAX_CONTRIBUTOR_SLOT = 13;
    private static final int SALES_TYCOON_SLOT = 15;
    private static final int RESEARCH_MASTER_SLOT = 29;
    private static final int MAX_LEVEL_OWNER_SLOT = 33;

    // Navigation
    private static final int BACK_SLOT = 45;
    private static final int SUMMARY_SLOT = 4;

    public AchievementGUI(Player player, ApartmentCore plugin, GUIManager guiManager) {
        this.player = player;
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.title = ChatColor.translateAlternateColorCodes('&', "&6Achievements");
        this.inventory = Bukkit.createInventory(null, 54, this.title);
    }

    @Override
    public void open(Player player) {
        setupInventory();
        player.openInventory(inventory);
    }

    private void setupInventory() {
        inventory.clear();

        addBorder();
        addSummary();
        addAchievementItem(AchievementType.INCOME_MILLIONAIRE, INCOME_MILLIONAIRE_SLOT);
        addAchievementItem(AchievementType.TAX_CONTRIBUTOR, TAX_CONTRIBUTOR_SLOT);
        addAchievementItem(AchievementType.SALES_TYCOON, SALES_TYCOON_SLOT);
        addAchievementItem(AchievementType.RESEARCH_MASTER, RESEARCH_MASTER_SLOT);
        addAchievementItem(AchievementType.MAX_LEVEL_OWNER, MAX_LEVEL_OWNER_SLOT);
        addBackButton();
    }

    private void addBorder() {
        ItemStack borderItem = ItemBuilder.filler(Material.GRAY_STAINED_GLASS_PANE);
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

    private void addSummary() {
        AchievementManager am = plugin.getAchievementManager();
        if (am == null) return;

        PlayerAchievementData data = am.getPlayerData(player.getUniqueId());
        int completed = data.getCompletedCount();
        int total = data.getTotalCount();

        String progressBar = GUIUtils.createProgressBar(completed, total, 20);
        String percentage = total > 0 ? String.format("%.0f%%", (completed * 100.0 / total)) : "0%";

        ItemStack item = new ItemBuilder(Material.NETHER_STAR)
                .name("&6&lAchievement Progress")
                .lore(
                        "&7Track your apartment milestones",
                        "",
                        "&eProgress: &f" + completed + "&7/&f" + total + " &7(" + percentage + ")",
                        "&7" + progressBar,
                        "",
                        "&7Complete achievements to earn",
                        "&7rewards and recognition!")
                .glow()
                .build();

        inventory.setItem(SUMMARY_SLOT, item);
    }

    private void addAchievementItem(AchievementType type, int slot) {
        AchievementManager am = plugin.getAchievementManager();
        if (am == null) return;

        PlayerAchievementData data = am.getPlayerData(player.getUniqueId());
        boolean isCompleted = data.isCompleted(type);
        boolean isEnabled = am.isAchievementEnabled(type);

        String name = am.getAchievementName(type);
        String description = am.getAchievementDescription(type);
        double target = am.getAchievementTarget(type);
        double progress = data.getProgress(type);
        double reward = am.getAchievementReward(type);
        Material icon = am.getAchievementIcon(type);

        // Compute real-time progress from live data
        double liveProgress = computeLiveProgress(type);
        if (!isCompleted && liveProgress > progress) {
            progress = liveProgress;
        }

        List<String> lore = new ArrayList<>();
        lore.add("&7" + description);
        lore.add("");

        if (!isEnabled) {
            // Disabled achievement
            Material disabledIcon = Material.BARRIER;
            ItemStack item = new ItemBuilder(disabledIcon)
                    .name("&c" + name)
                    .lore("&7" + description, "", "&c&lDISABLED")
                    .build();
            inventory.setItem(slot, item);
            return;
        }

        if (isCompleted) {
            lore.add("&a&lCOMPLETED");
            lore.add("");
            lore.add("&eProgress: &a" + formatProgressValue(type, target) + " &7/ &f" + formatProgressValue(type, target));
            String progressBar = GUIUtils.createProgressBar(1, 1, 20);
            lore.add("&7" + progressBar + " &a100%");

            if (data.getCompletedAt(type) > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm");
                lore.add("");
                lore.add("&7Completed: &f" + sdf.format(new Date(data.getCompletedAt(type))));
            }

            if (reward > 0) {
                lore.add("");
                lore.add("&6Reward: &a" + plugin.getConfigManager().formatMoney(reward) + " &7(Claimed)");
            }

            ItemStack item = new ItemBuilder(icon)
                    .name("&a&l" + name)
                    .lore(lore)
                    .glow()
                    .build();
            inventory.setItem(slot, item);
        } else {
            // In progress
            lore.add("&eProgress: &f" + formatProgressValue(type, progress) + " &7/ &f" + formatProgressValue(type, target));

            double pct = target > 0 ? Math.min(1.0, progress / target) : 0;
            String progressBar = GUIUtils.createProgressBar(progress, target, 20);
            lore.add("&7" + progressBar + " &f" + String.format("%.1f%%", pct * 100));

            double remaining = Math.max(0, target - progress);
            lore.add("");
            lore.add("&7Remaining: &c" + formatProgressValue(type, remaining));

            if (reward > 0) {
                lore.add("");
                lore.add("&6Reward: &e" + plugin.getConfigManager().formatMoney(reward));
            }

            Material progressIcon = Material.GRAY_DYE;
            if (pct >= 0.75) progressIcon = Material.LIME_DYE;
            else if (pct >= 0.50) progressIcon = Material.YELLOW_DYE;
            else if (pct >= 0.25) progressIcon = Material.ORANGE_DYE;

            ItemStack item = new ItemBuilder(progressIcon)
                    .name("&e" + name)
                    .lore(lore)
                    .build();
            inventory.setItem(slot, item);
        }
    }

    /**
     * Compute live progress from the current game state for real-time tracking.
     */
    private double computeLiveProgress(AchievementType type) {
        java.util.UUID uuid = player.getUniqueId();

        switch (type) {
            case INCOME_MILLIONAIRE: {
                double total = 0;
                for (com.aithor.apartmentcore.model.Apartment a : plugin.getApartmentManager().getApartments().values()) {
                    if (!uuid.equals(a.owner)) continue;
                    var st = plugin.getApartmentManager().getStats(a.id);
                    if (st != null) total += st.totalIncomeGenerated;
                }
                return total;
            }
            case TAX_CONTRIBUTOR: {
                double total = 0;
                for (com.aithor.apartmentcore.model.Apartment a : plugin.getApartmentManager().getApartments().values()) {
                    if (!uuid.equals(a.owner)) continue;
                    var st = plugin.getApartmentManager().getStats(a.id);
                    if (st != null) total += st.totalTaxPaid;
                }
                return total;
            }
            case SALES_TYCOON: {
                // Sales progress is tracked in achievement data only (cumulative from sell events)
                AchievementManager am = plugin.getAchievementManager();
                if (am != null) {
                    return am.getPlayerData(uuid).getProgress(AchievementType.SALES_TYCOON);
                }
                return 0;
            }
            case RESEARCH_MASTER: {
                if (plugin.getResearchManager() == null) return 0;
                com.aithor.apartmentcore.research.PlayerResearchData rd = plugin.getResearchManager().getPlayerData(uuid);
                int maxedCount = 0;
                for (com.aithor.apartmentcore.research.ResearchType rt : com.aithor.apartmentcore.research.ResearchType.values()) {
                    if (rd.isMaxTier(rt)) maxedCount++;
                }
                return maxedCount;
            }
            case MAX_LEVEL_OWNER: {
                int maxLevelCount = 0;
                int maxLevel = plugin.getConfigManager().getLevelConfigs().keySet().stream()
                        .mapToInt(Integer::intValue).max().orElse(5);
                for (com.aithor.apartmentcore.model.Apartment a : plugin.getApartmentManager().getApartments().values()) {
                    if (uuid.equals(a.owner) && a.level >= maxLevel) {
                        maxLevelCount++;
                    }
                }
                return maxLevelCount;
            }
            default:
                return 0;
        }
    }

    /**
     * Format progress values based on achievement type.
     * Currency-based achievements show as money, count-based show as integers.
     */
    private String formatProgressValue(AchievementType type, double value) {
        switch (type) {
            case INCOME_MILLIONAIRE:
            case TAX_CONTRIBUTOR:
            case SALES_TYCOON:
                return plugin.getConfigManager().formatMoney(value);
            case RESEARCH_MASTER:
            case MAX_LEVEL_OWNER:
                return String.valueOf((int) value);
            default:
                return String.format("%.0f", value);
        }
    }

    private void addBackButton() {
        ItemStack backItem = new ItemBuilder(Material.ARROW)
                .name("&c Back to Main Menu")
                .lore("&7Return to the main menu")
                .build();
        inventory.setItem(BACK_SLOT, backItem);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);

        if (slot == BACK_SLOT) {
            plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openMainMenu(player));
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
