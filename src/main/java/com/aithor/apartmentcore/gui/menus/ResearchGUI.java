package com.aithor.apartmentcore.gui.menus;

import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.gui.GUIManager;
import com.aithor.apartmentcore.gui.interfaces.GUI;
import com.aithor.apartmentcore.gui.items.ItemBuilder;
import com.aithor.apartmentcore.gui.utils.GUIUtils;
import com.aithor.apartmentcore.research.PlayerResearchData;
import com.aithor.apartmentcore.research.ResearchManager;
import com.aithor.apartmentcore.research.ResearchType;

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
 * GUI for the Research Center - allows players to start and track researches
 * that provide permanent buffs.
 */
public class ResearchGUI implements GUI {

    private final Player player;
    private final ApartmentCore plugin;
    private final GUIManager guiManager;
    private final String title;
    private final Inventory inventory;

    // Slot positions for research items (row 2 and row 3)
    private static final int REVENUE_ACCELERATION_SLOT = 10;
    private static final int CAPITAL_GROWTH_SLOT = 12;
    private static final int TAX_EFFICIENCY_SLOT = 14;
    private static final int EXPANSION_PLAN_SLOT = 16;
    private static final int AUCTION_EFFICIENCY_SLOT = 22;

    // Other slots
    private static final int INFO_SLOT = 4;
    private static final int ACTIVE_RESEARCH_SLOT = 31;
    private static final int BACK_SLOT = 49;

    public ResearchGUI(Player player, ApartmentCore plugin, GUIManager guiManager) {
        this.player = player;
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.title = ChatColor.translateAlternateColorCodes('&', "&8Research Center");
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
        addInfoItem();
        addResearchItems();
        addActiveResearchDisplay();
        addBackButton();
    }

    private void addBorder() {
        ItemStack borderItem = ItemBuilder.filler(Material.PURPLE_STAINED_GLASS_PANE);
        int rows = inventory.getSize() / 9;

        // Top border
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, borderItem);
        }
        // Bottom border
        int bottomStart = (rows - 1) * 9;
        for (int i = bottomStart; i < bottomStart + 9; i++) {
            inventory.setItem(i, borderItem);
        }
        // Side borders
        for (int r = 1; r < rows - 1; r++) {
            inventory.setItem(r * 9, borderItem);
            inventory.setItem(r * 9 + 8, borderItem);
        }
    }

    private void addInfoItem() {
        ResearchManager rm = plugin.getResearchManager();
        PlayerResearchData data = rm.getPlayerData(player.getUniqueId());

        int totalCompleted = 0;
        int totalMax = 0;
        for (ResearchType type : ResearchType.values()) {
            totalCompleted += data.getCompletedTier(type);
            totalMax += type.getMaxTier();
        }

        List<String> lore = new ArrayList<>();
        lore.add("&7Conduct research to unlock permanent buffs");
        lore.add("&7that apply to all your apartments!");
        lore.add("");
        lore.add("&e Progress:");
        lore.add("&7 Overall: &f" + totalCompleted + "&7/&f" + totalMax + " tiers completed");
        lore.add("");

        // Show active buffs summary
        double intervalReduction = rm.getIncomeIntervalReduction(player.getUniqueId());
        double incomeBonus = rm.getIncomeAmountBonus(player.getUniqueId());
        double taxReduction = rm.getTaxReduction(player.getUniqueId());
        int extraSlots = rm.getExtraOwnershipSlots(player.getUniqueId());
        double auctionFeeReduction = rm.getAuctionFeeReduction(player.getUniqueId());
        double commissionReduction = rm.getAuctionCommissionReduction(player.getUniqueId());

        boolean hasBuffs = intervalReduction > 0 || incomeBonus > 0 || taxReduction > 0
                || extraSlots > 0 || auctionFeeReduction > 0;

        if (hasBuffs) {
            lore.add("&a Active Buffs:");
            if (intervalReduction > 0)
                lore.add("&7 Income Speed: &a-" + String.format("%.0f%%", intervalReduction));
            if (incomeBonus > 0)
                lore.add("&7 Income Bonus: &a+" + String.format("%.0f%%", incomeBonus));
            if (taxReduction > 0)
                lore.add("&7 Tax Reduction: &a-" + String.format("%.0f%%", taxReduction));
            if (extraSlots > 0)
                lore.add("&7 Extra Slots: &a+" + extraSlots);
            if (auctionFeeReduction > 0)
                lore.add("&7 Auction Fee: &a-" + String.format("%.0f%%", auctionFeeReduction)
                        + " &7/ Commission: &a-" + String.format("%.0f%%", commissionReduction));
        } else {
            lore.add("&7 No active buffs yet. Start researching!");
        }

        ItemStack item = new ItemBuilder(Material.ENCHANTING_TABLE)
                .name("&d Research Center")
                .lore(lore)
                .glow()
                .build();

        inventory.setItem(INFO_SLOT, item);
    }

    private void addResearchItems() {
        ResearchManager rm = plugin.getResearchManager();
        PlayerResearchData data = rm.getPlayerData(player.getUniqueId());

        addResearchItem(ResearchType.REVENUE_ACCELERATION, REVENUE_ACCELERATION_SLOT, data, rm);
        addResearchItem(ResearchType.CAPITAL_GROWTH, CAPITAL_GROWTH_SLOT, data, rm);
        addResearchItem(ResearchType.TAX_EFFICIENCY, TAX_EFFICIENCY_SLOT, data, rm);
        addResearchItem(ResearchType.EXPANSION_PLAN, EXPANSION_PLAN_SLOT, data, rm);
        addResearchItem(ResearchType.AUCTION_EFFICIENCY, AUCTION_EFFICIENCY_SLOT, data, rm);
    }

    private void addResearchItem(ResearchType type, int slot, PlayerResearchData data, ResearchManager rm) {
        int completedTier = data.getCompletedTier(type);
        boolean isMaxTier = data.isMaxTier(type);
        boolean isActive = data.hasActiveResearch() && data.getActiveResearch() == type;

        List<String> lore = new ArrayList<>();
        lore.add("&7" + type.getDescription());
        lore.add("");

        // Tier progress bar
        StringBuilder tierBar = new StringBuilder("&7Tier: ");
        for (int i = 1; i <= type.getMaxTier(); i++) {
            if (i <= completedTier) {
                tierBar.append("&a\u2588 "); // completed (full block)
            } else if (isActive && i == data.getActiveTier()) {
                tierBar.append("&e\u2592 "); // in progress (medium shade)
            } else {
                tierBar.append("&8\u2591 "); // not started (light shade)
            }
        }
        lore.add(tierBar.toString().trim());
        lore.add("&7Completed: &f" + ResearchManager.toRoman(completedTier)
                + "&7/&f" + ResearchManager.toRoman(type.getMaxTier()));
        lore.add("");

        // Current buff
        if (completedTier > 0) {
            lore.add("&a Current Buff:");
            lore.add("&7 " + getBuffDescription(type, completedTier));
            lore.add("");
        }

        // Next tier info
        if (isActive) {
            long remaining = data.getRemainingTime();
            lore.add("&e Researching Tier " + ResearchManager.toRoman(data.getActiveTier()) + "...");
            lore.add("&7 Time Remaining: &f" + GUIUtils.formatTime(remaining));
            lore.add("");
            lore.add("&7 Please wait for completion.");
        } else if (isMaxTier) {
            lore.add("&a MAX TIER REACHED!");
            lore.add("&7 This research cannot be upgraded further.");
        } else if (data.hasActiveResearch()) {
            // Player is researching something else
            int nextTier = completedTier + 1;
            double cost = rm.getResearchCost(type, nextTier);
            long duration = rm.getResearchDurationSeconds(type, nextTier);
            lore.add("&6 Next Tier " + ResearchManager.toRoman(nextTier) + ":");
            lore.add("&7 Buff: &f" + getBuffDescription(type, nextTier));
            lore.add("&7 Cost: &f" + plugin.getConfigManager().formatMoney(cost));
            lore.add("&7 Duration: &f" + GUIUtils.formatTime(duration * 1000L));
            lore.add("");
            lore.add("&c Another research is in progress!");
        } else {
            int nextTier = completedTier + 1;
            double cost = rm.getResearchCost(type, nextTier);
            long duration = rm.getResearchDurationSeconds(type, nextTier);
            boolean canAfford = plugin.getEconomy().has(player, cost);

            lore.add("&6 Next Tier " + ResearchManager.toRoman(nextTier) + ":");
            lore.add("&7 Buff: &f" + getBuffDescription(type, nextTier));
            lore.add("&7 Cost: &f" + plugin.getConfigManager().formatMoney(cost));
            lore.add("&7 Duration: &f" + GUIUtils.formatTime(duration * 1000L));
            lore.add("");

            if (canAfford) {
                lore.add("&a Click to start research!");
            } else {
                lore.add("&c Insufficient funds!");
                lore.add("&7 Need: &c" + plugin.getConfigManager().formatMoney(
                        cost - plugin.getEconomy().getBalance(player)) + " &7more");
            }
        }

        // Determine material based on tier
        Material material = type.getIcon();
        if (completedTier > 0) {
            material = switch (completedTier) {
                case 1 -> Material.IRON_BLOCK;
                case 2 -> Material.GOLD_BLOCK;
                case 3 -> Material.EMERALD_BLOCK;
                case 4 -> Material.DIAMOND_BLOCK;
                case 5 -> Material.NETHERITE_BLOCK;
                default -> type.getIcon();
            };
        }

        ItemBuilder builder = new ItemBuilder(material)
                .name("&d" + type.getDisplayName())
                .lore(lore);

        if (completedTier > 0 || isActive) {
            builder.glow();
        }

        inventory.setItem(slot, builder.build());
    }

    private void addActiveResearchDisplay() {
        ResearchManager rm = plugin.getResearchManager();
        PlayerResearchData data = rm.getPlayerData(player.getUniqueId());

        if (data.hasActiveResearch()) {
            ResearchType type = data.getActiveResearch();
            long remaining = data.getRemainingTime();
            long total = data.getResearchDuration();
            double progress = total > 0 ? 1.0 - ((double) remaining / total) : 1.0;

            List<String> lore = new ArrayList<>();
            lore.add("&7Currently researching:");
            lore.add("");
            lore.add("&e " + type.getDisplayName() + " Tier " + ResearchManager.toRoman(data.getActiveTier()));
            lore.add("");
            lore.add("&7 Progress: " + GUIUtils.createProgressBar(progress * 100, 100, 20));
            lore.add("&7 " + String.format("%.1f%%", progress * 100) + " complete");
            lore.add("");
            lore.add("&7 Time Remaining: &f" + GUIUtils.formatTime(remaining));
            lore.add("&7 Total Duration: &f" + GUIUtils.formatTime(total));

            ItemStack item = new ItemBuilder(Material.BREWING_STAND)
                    .name("&e Active Research")
                    .lore(lore)
                    .glow()
                    .build();

            inventory.setItem(ACTIVE_RESEARCH_SLOT, item);
        } else {
            ItemStack item = new ItemBuilder(Material.GLASS_BOTTLE)
                    .name("&7 No Active Research")
                    .lore(
                            "&7You are not researching anything.",
                            "",
                            "&7Click a research above to start!")
                    .build();

            inventory.setItem(ACTIVE_RESEARCH_SLOT, item);
        }
    }

    private void addBackButton() {
        ItemStack item = new ItemBuilder(Material.ARROW)
                .name("&c Back to Main Menu")
                .lore("&7Click to go back")
                .build();

        inventory.setItem(BACK_SLOT, item);
    }

    private String getBuffDescription(ResearchType type, int tier) {
        return switch (type) {
            case REVENUE_ACCELERATION -> "-" + (tier * 5) + "% income generation interval";
            case CAPITAL_GROWTH -> "+" + (tier * 5) + "% income amount";
            case TAX_EFFICIENCY -> "-" + (tier * 5) + "% tax amount";
            case EXPANSION_PLAN -> "+" + tier + " apartment ownership slot" + (tier > 1 ? "s" : "");
            case AUCTION_EFFICIENCY -> "-" + (tier * 5) + "% auction fee, -" + tier + "% commission rate";
        };
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);

        // Handle back button
        if (slot == BACK_SLOT) {
            plugin.getServer().getScheduler().runTask(plugin, () -> guiManager.openMainMenu(player));
            return;
        }

        // Handle research item clicks
        ResearchType clickedType = getResearchTypeFromSlot(slot);
        if (clickedType != null) {
            handleResearchClick(clickedType);
            return;
        }
    }

    private ResearchType getResearchTypeFromSlot(int slot) {
        return switch (slot) {
            case REVENUE_ACCELERATION_SLOT -> ResearchType.REVENUE_ACCELERATION;
            case CAPITAL_GROWTH_SLOT -> ResearchType.CAPITAL_GROWTH;
            case TAX_EFFICIENCY_SLOT -> ResearchType.TAX_EFFICIENCY;
            case EXPANSION_PLAN_SLOT -> ResearchType.EXPANSION_PLAN;
            case AUCTION_EFFICIENCY_SLOT -> ResearchType.AUCTION_EFFICIENCY;
            default -> null;
        };
    }

    private void handleResearchClick(ResearchType type) {
        if (!player.hasPermission("apartmentcore.research")) {
            GUIUtils.sendMessage(player, "&cYou don't have permission to use the research system!");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }

        ResearchManager rm = plugin.getResearchManager();
        ResearchManager.StartResult result = rm.startResearch(player, type);

        if (result.isSuccess()) {
            GUIUtils.sendMessage(player, "&a" + result.getMessage());
            GUIUtils.playSound(player, GUIUtils.SUCCESS_SOUND);
        } else {
            GUIUtils.sendMessage(player, "&c" + result.getMessage());
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
        }

        // Refresh GUI to show updated state
        plugin.getServer().getScheduler().runTask(plugin, this::refresh);
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
