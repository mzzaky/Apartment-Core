package com.aithor.apartmentcorei3.gui;

import com.aithor.apartmentcorei3.ApartmentCorei3;
import com.aithor.apartmentcorei3.gui.interfaces.GUI;
import com.aithor.apartmentcorei3.gui.menus.*;
import com.aithor.apartmentcorei3.gui.utils.GUIUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central manager for all GUI operations
 */
public class GUIManager implements Listener {
    
    private final ApartmentCorei3 plugin;
    private final Map<UUID, GUI> openGUIs;
    private final Map<Inventory, GUI> inventoryToGUI;
    
    public GUIManager(ApartmentCorei3 plugin) {
        this.plugin = plugin;
        this.openGUIs = new HashMap<>();
        this.inventoryToGUI = new HashMap<>();
        
        // Register event listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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

                // Play sound
                GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);

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