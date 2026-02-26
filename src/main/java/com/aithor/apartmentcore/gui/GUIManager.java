package com.aithor.apartmentcore.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.gui.interfaces.GUI;
import com.aithor.apartmentcore.gui.menus.ApartmentBrowserGUI;
import com.aithor.apartmentcore.gui.menus.ApartmentDetailsGUI;
import com.aithor.apartmentcore.gui.menus.ApartmentShopGUI;
import com.aithor.apartmentcore.gui.menus.ApartmentStatisticsGUI;
import com.aithor.apartmentcore.gui.menus.AuctionHouseGUI;
import com.aithor.apartmentcore.gui.menus.GuestbookGUI;
import com.aithor.apartmentcore.gui.menus.MainMenuGUI;
import com.aithor.apartmentcore.gui.menus.MyApartmentsGUI;
import com.aithor.apartmentcore.gui.menus.ResearchGUI;
import com.aithor.apartmentcore.gui.menus.StatisticsGUI;
import com.aithor.apartmentcore.gui.menus.TaxManagementGUI;
import com.aithor.apartmentcore.gui.utils.GUIUtils;

/**
 * Central manager for all GUI operations
 */
public class GUIManager implements Listener {
    
    private final ApartmentCore plugin;
    private final Map<UUID, GUI> openGUIs;
    private final Map<Inventory, GUI> inventoryToGUI;
    private int refreshTaskId = -1;
    
    public GUIManager(ApartmentCore plugin) {
        this.plugin = plugin;
        this.openGUIs = new HashMap<>();
        this.inventoryToGUI = new HashMap<>();
        
        // Register event listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Auto-refresh open GUIs according to config gui.refresh-interval (seconds)
        scheduleRefreshTask();
    }

    private void scheduleRefreshTask() {
        // Cancel any previous task before scheduling a new one
        cancelRefreshTask();
        try {
            int seconds = Math.max(0, plugin.getConfigManager().getGuiRefreshInterval());
            if (seconds > 0 && plugin.getConfigManager().isGuiEnabled()) {
                long period = seconds * 20L;
                refreshTaskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                    if (!plugin.getConfigManager().isGuiEnabled()) return;
                    for (GUI gui : openGUIs.values()) {
                        try { gui.refresh(); } catch (Throwable ignored) {}
                    }
                }, period, period);
                plugin.debug("GUI auto-refresh scheduled every " + seconds + "s");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to schedule GUI auto-refresh: " + t.getMessage());
        }
    }

    private void cancelRefreshTask() {
        if (refreshTaskId != -1) {
            try { plugin.getServer().getScheduler().cancelTask(refreshTaskId); } catch (Throwable ignored) {}
            refreshTaskId = -1;
        }
    }

    /**
     * Called when configuration is reloaded so GUIs apply changes immediately.
     * - Reschedules/cancels the auto-refresh task according to new config
     * - Closes all GUIs if GUI system was disabled
     * - Forces an immediate refresh of all open GUIs so new config is reflected
     */
    public void onConfigReloaded() {
        try {
            if (!plugin.getConfigManager().isGuiEnabled()) {
                // If GUI was turned off via config, close open GUIs and cancel the task
                cancelRefreshTask();
                try { closeAllGUIs(); } catch (Throwable ignored) {}
                plugin.debug("GUI system disabled by config; closed open GUIs.");
                return;
            }

            // Re-schedule refresh task per new configuration
            scheduleRefreshTask();

            // Refresh open GUIs immediately so they reflect updated config values
            for (GUI gui : openGUIs.values()) {
                try { gui.refresh(); } catch (Throwable ignored) {}
            }
            plugin.debug("GUI manager applied new configuration");
        } catch (Throwable t) {
            plugin.getLogger().warning("Error while applying GUI config reload: " + t.getMessage());
        }
    }
    
    /**
     * Open the main menu GUI for a player
     * @param player The player
     */
    public void openMainMenu(Player player) {
        MainMenuGUI gui = new MainMenuGUI(player, plugin, this);
        openGUI(player, gui);
    }
    
    /**
     * Open the apartment browser GUI
     * @param player The player
     */
    public void openApartmentBrowser(Player player) {
        ApartmentBrowserGUI gui = new ApartmentBrowserGUI(player, plugin, this);
        openGUI(player, gui);
    }
    
    /**
     * Open the my apartments GUI
     * @param player The player
     */
    public void openMyApartments(Player player) {
        MyApartmentsGUI gui = new MyApartmentsGUI(player, plugin, this);
        openGUI(player, gui);
    }
    
    /**
     * Open apartment details GUI
     * @param player The player
     * @param apartmentId The apartment ID
     */
    public void openApartmentDetails(Player player, String apartmentId) {
        ApartmentDetailsGUI gui = new ApartmentDetailsGUI(player, plugin, this, apartmentId);
        openGUI(player, gui);
    }
    
    /**
     * Open tax management GUI
     * @param player The player
     */
    public void openTaxManagement(Player player) {
        TaxManagementGUI gui = new TaxManagementGUI(player, plugin, this);
        openGUI(player, gui);
    }
    
    /**
     * Open auction house GUI
     * @param player The player
     */
    public void openAuctionHouse(Player player) {
        if (plugin.getAuctionManager() == null) {
            GUIUtils.sendMessage(player, "&cAuction system is disabled!");
            return;
        }
        AuctionHouseGUI gui = new AuctionHouseGUI(player, plugin, this);
        openGUI(player, gui);
    }
    
    /**
     * Open guestbook GUI
     * @param player The player
     * @param apartmentId The apartment ID
     */
    public void openGuestbook(Player player, String apartmentId) {
        GuestbookGUI gui = new GuestbookGUI(player, plugin, this, apartmentId);
        openGUI(player, gui);
    }

    /**
     * Open statistics overview GUI (player-wide)
     * @param player The player
     */
    public void openStatistics(Player player) {
        StatisticsGUI gui = new StatisticsGUI(player, plugin, this);
        openGUI(player, gui);
    }

    /**
     * Open per-apartment statistics GUI
     * @param player The player
     * @param apartmentId The apartment ID
     */
    public void openApartmentStatistics(Player player, String apartmentId) {
        ApartmentStatisticsGUI gui = new ApartmentStatisticsGUI(player, plugin, this, apartmentId);
        openGUI(player, gui);
    }
    
    /**
     * Open apartment shop GUI
     * @param player The player
     * @param apartmentId The apartment ID
     */
    public void openApartmentShop(Player player, String apartmentId) {
        ApartmentShopGUI gui = new ApartmentShopGUI(player, plugin, this, apartmentId);
        openGUI(player, gui);
    }
    
    /**
     * Open research center GUI
     * @param player The player
     */
    public void openResearch(Player player) {
        ResearchGUI gui = new ResearchGUI(player, plugin, this);
        openGUI(player, gui);
    }

    /**
     * Open any GUI for a player
     * @param player The player
     * @param gui The GUI to open
     */
    public void openGUI(Player player, GUI gui) {
        // Close any existing GUI synchronously (will remove mappings)
        closeGUI(player);

        // Register mapping immediately so event handlers can resolve this inventory
        openGUIs.put(player.getUniqueId(), gui);
        inventoryToGUI.put(gui.getInventory(), gui);

        // Defer the actual opening (inventory display) to the next tick to avoid concurrent inventory modifications
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                // Open the GUI
                gui.open(player);

                // Play sound if enabled in config
                try {
                    if (plugin.getConfigManager().isGuiSounds()) {
                        GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
                    }
                } catch (Throwable ignored) {}

                plugin.debug("Opened GUI '" + gui.getTitle() + "' for player " + player.getName());
            } catch (Throwable t) {
                plugin.getLogger().severe("Failed to open GUI for " + player.getName() + ": " + t.getMessage());
                t.printStackTrace();
                GUIUtils.sendMessage(player, "&cAn error occurred while opening the GUI. Check server console for details.");
                // Clean up mappings on failure
                openGUIs.remove(player.getUniqueId());
                inventoryToGUI.remove(gui.getInventory());
            }
        });
    }
    
    /**
     * Close GUI for a player
     * @param player The player
     */
    public void closeGUI(Player player) {
        GUI gui = openGUIs.remove(player.getUniqueId());
        if (gui != null) {
            inventoryToGUI.remove(gui.getInventory());
            gui.onClose(player);
            plugin.debug("Closed GUI '" + gui.getTitle() + "' for player " + player.getName());
        }
    }
    
    /**
     * Get the GUI that a player currently has open
     * @param player The player
     * @return The GUI or null if none open
     */
    public GUI getOpenGUI(Player player) {
        return openGUIs.get(player.getUniqueId());
    }
    
    /**
     * Get GUI by inventory
     * @param inventory The inventory
     * @return The GUI or null if not found
     */
    public GUI getGUIByInventory(Inventory inventory) {
        return inventoryToGUI.get(inventory);
    }
    
    /**
     * Check if a player has a GUI open
     * @param player The player
     * @return True if a GUI is open
     */
    public boolean hasGUIOpen(Player player) {
        return openGUIs.containsKey(player.getUniqueId());
    }
    
    /**
     * Refresh the GUI for a player if they have one open
     * @param player The player
     */
    public void refreshGUI(Player player) {
        GUI gui = getOpenGUI(player);
        if (gui != null) {
            gui.refresh();
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory topInventory = event.getView() != null ? event.getView().getTopInventory() : null;

        if (topInventory == null) {
            return;
        }

        GUI gui = getGUIByInventory(topInventory);
        if (gui != null) {
            // This is one of our GUIs; cancel original click and delegate
            event.setCancelled(true);

            try {
                // Delegate to GUI implementation
                gui.handleClick(event);
            } catch (Exception e) {
                plugin.getLogger().severe("Error handling GUI click for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                GUIUtils.sendMessage(player, "&cAn error occurred while processing your click!");
                GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        Inventory topInventory = event.getView() != null ? event.getView().getTopInventory() : null;
        GUI gui = topInventory != null ? getGUIByInventory(topInventory) : null;

        if (gui != null) {
            // Schedule cleanup for next tick to avoid concurrent modification
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (getOpenGUI(player) == gui) {
                    closeGUI(player);
                }
            });
        }
    }
    
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        Inventory topInventory = event.getView() != null ? event.getView().getTopInventory() : null;
        GUI gui = topInventory != null ? getGUIByInventory(topInventory) : null;

        if (gui != null) {
            plugin.debug("Player " + player.getName() + " opened GUI: " + gui.getTitle());
        }
    }
    
    /**
     * Close all open GUIs (used on plugin disable)
     */
    public void closeAllGUIs() {
        for (UUID playerId : openGUIs.keySet()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        openGUIs.clear();
        inventoryToGUI.clear();
    }
    
    /**
     * Get statistics about open GUIs
     * @return Map of GUI types to player counts
     */
    public Map<String, Integer> getGUIStats() {
        Map<String, Integer> stats = new HashMap<>();
        
        for (GUI gui : openGUIs.values()) {
            String type = gui.getClass().getSimpleName();
            stats.put(type, stats.getOrDefault(type, 0) + 1);
        }
        
        return stats;
    }
}