package com.aithor.apartmentcore.gui.interfaces;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * Base interface for all GUI implementations
 */
public interface GUI {
    
    /**
     * Open the GUI for a player
     * @param player The player to open the GUI for
     */
    void open(Player player);
    
    /**
     * Handle inventory click events
     * @param event The click event
     */
    void handleClick(InventoryClickEvent event);
    
    /**
     * Get the inventory associated with this GUI
     * @return The inventory
     */
    Inventory getInventory();
    
    /**
     * Get the title of this GUI
     * @return The GUI title
     */
    String getTitle();
    
    /**
     * Get the size of this GUI (number of slots)
     * @return The inventory size
     */
    int getSize();
    
    /**
     * Refresh the GUI content
     */
    void refresh();
    
    /**
     * Check if the inventory belongs to this GUI
     * @param inventory The inventory to check
     * @return True if it belongs to this GUI
     */
    boolean isThisInventory(Inventory inventory);
    
    /**
     * Called when the GUI is closed
     * @param player The player who closed the GUI
     */
    void onClose(Player player);
}