package com.aithor.apartmentcore.gui.menus;

import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.gui.GUIManager;
import com.aithor.apartmentcore.gui.interfaces.GUI;
import com.aithor.apartmentcore.gui.items.ItemBuilder;
import com.aithor.apartmentcore.gui.utils.GUIUtils;
import com.aithor.apartmentcore.model.Apartment;
import com.aithor.apartmentcore.model.LevelConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class UpgradeConfirmGUI implements GUI {
    private final Player player;
    private final ApartmentCore plugin;
    private final GUIManager guiManager;
    private final String apartmentId;
    private final String title;
    private final Inventory inventory;

    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;
    private static final int INFO_SLOT = 13;

    public UpgradeConfirmGUI(Player player, ApartmentCore plugin, GUIManager guiManager, String apartmentId) {
        this.player = player;
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.apartmentId = apartmentId;
        this.title = ChatColor.GOLD + "Confirm Upgrade";
        this.inventory = Bukkit.createInventory(null, 27, this.title);
    }

    @Override
    public void open(Player player) {
        setupInventory();
        player.openInventory(inventory);
    }

    private void setupInventory() {
        inventory.clear();

        Apartment apt = plugin.getApartmentManager().getApartment(apartmentId);
        if (apt == null || apt.level >= 5) {
            player.closeInventory();
            return;
        }

        LevelConfig levelConfig = plugin.getConfigManager().getLevelConfig(apt.level + 1);
        if (levelConfig == null)
            return;

        long duration = levelConfig.upgradeDuration; // in ticks
        String durationStr = duration <= 0 ? "Instant" : formatTicks(duration);

        // Fill background
        ItemStack filler = ItemBuilder.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        // Info item
        ItemStack infoItem = new ItemBuilder(Material.DIAMOND)
                .name("&bUpgrade Information")
                .lore(
                        "&7Apartment: &f" + apt.displayName,
                        "&7Current Level: &f" + apt.level,
                        "&7Target Level: &f" + (apt.level + 1),
                        "",
                        "&7Cost: &e" + plugin.getConfigManager().formatMoney(levelConfig.upgradeCost),
                        "&7Duration: &6" + durationStr,
                        "",
                        "&7New Income: &a" + plugin.getConfigManager().formatMoney(levelConfig.minIncome) +
                                " &7- &a" + plugin.getConfigManager().formatMoney(levelConfig.maxIncome))
                .glow()
                .build();
        inventory.setItem(INFO_SLOT, infoItem);

        // Confirm
        ItemStack confirmItem = new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a&lCONFIRM")
                .lore("&7Click to start the upgrade process.")
                .build();
        inventory.setItem(CONFIRM_SLOT, confirmItem);

        // Cancel
        ItemStack cancelItem = new ItemBuilder(Material.REDSTONE_BLOCK)
                .name("&c&lCANCEL")
                .lore("&7Click to return to apartment details.")
                .build();
        inventory.setItem(CANCEL_SLOT, cancelItem);
    }

    private String formatTicks(long ticks) {
        long seconds = ticks / 20;
        if (seconds < 60)
            return seconds + " seconds";
        long minutes = seconds / 60;
        if (minutes < 60)
            return minutes + " minutes " + (seconds % 60) + " seconds";
        long hours = minutes / 60;
        return hours + " hours " + (minutes % 60) + " minutes";
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == CANCEL_SLOT) {
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            guiManager.openApartmentDetails(player, apartmentId);
        } else if (slot == CONFIRM_SLOT) {
            GUIUtils.playSound(player, GUIUtils.SUCCESS_SOUND);
            player.closeInventory();
            plugin.getServer().dispatchCommand(player, "apartmentcore upgrade " + apartmentId + " confirm");
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
    }
}
