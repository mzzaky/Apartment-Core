package com.aithor.apartmentcore.gui.interfaces;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.aithor.apartmentcore.gui.items.GUIItem;
import com.aithor.apartmentcore.gui.items.ItemBuilder;

/**
 * Abstract class for paginated GUIs
 */
public abstract class PaginatedGUI implements GUI {
    
    protected final Player player;
    protected final String title;
    protected final int size;
    protected Inventory inventory;
    protected int currentPage;
    protected int itemsPerPage;
    protected List<GUIItem> items;
    
    // Navigation slots
    protected static final int PREVIOUS_SLOT = 45;
    protected static final int NEXT_SLOT = 53;
    protected static final int INFO_SLOT = 49;
    
    public PaginatedGUI(Player player, String title, int size, int itemsPerPage) {
        this.player = player;
        this.title = title;
        this.size = size;
        this.itemsPerPage = itemsPerPage;
        this.currentPage = 0;
        this.inventory = Bukkit.createInventory(null, size, title);
        // Defer loading items until setupInventory / open to avoid calling subclass
        // overrides before subclass fields are initialized.
        this.items = new java.util.ArrayList<>();
    }
    
    /**
     * Load items to display in this paginated GUI
     * @return List of GUI items
     */
    protected abstract List<GUIItem> loadItems();
    
    /**
     * Handle item click
     * @param item The clicked item
     * @param event The click event
     */
    protected abstract void handleItemClick(GUIItem item, InventoryClickEvent event);
    
    @Override
    public void open(Player player) {
        setupInventory();
        player.openInventory(inventory);
    }
    
    protected void setupInventory() {
        // Load items from subclass now that subclass constructor has completed
        this.items = loadItems();

        inventory.clear();
        
        // Add border
        addBorder();
        
        // Add items for current page
        addPageItems();
        
        // Add navigation
        addNavigation();
    }
    
    protected void addBorder() {
        ItemStack borderItem = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        
        // Top and bottom border
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, borderItem);
            inventory.setItem(i + (size - 9), borderItem);
        }
        
        // Side borders
        for (int i = 9; i < size - 9; i += 9) {
            inventory.setItem(i, borderItem);
            inventory.setItem(i + 8, borderItem);
        }
    }
    
    protected void addPageItems() {
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, items.size());
        
        int slot = 10; // Start after border
        for (int i = startIndex; i < endIndex; i++) {
            if (slot % 9 == 0 || slot % 9 == 8) { // Skip border slots
                slot += 2;
            }
            
            GUIItem item = items.get(i);
            inventory.setItem(slot, item.getItemStack());
            slot++;
        }
    }
    
    protected void addNavigation() {
        // Previous page
        if (currentPage > 0) {
            ItemStack prevItem = new ItemBuilder(Material.ARROW)
                    .name(ChatColor.GREEN + "◀ Previous Page")
                    .lore(ChatColor.GRAY + "Go to page " + currentPage)
                    .build();
            inventory.setItem(PREVIOUS_SLOT, prevItem);
        }
        
        // Next page
        if ((currentPage + 1) * itemsPerPage < items.size()) {
            ItemStack nextItem = new ItemBuilder(Material.ARROW)
                    .name(ChatColor.GREEN + "Next Page ▶")
                    .lore(ChatColor.GRAY + "Go to page " + (currentPage + 2))
                    .build();
            inventory.setItem(NEXT_SLOT, nextItem);
        }
        
        // Page info
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / itemsPerPage));
        ItemStack infoItem = new ItemBuilder(Material.BOOK)
                .name(ChatColor.YELLOW + "Page " + (currentPage + 1) + "/" + totalPages)
                .lore(ChatColor.GRAY + "Total items: " + items.size())
                .build();
        inventory.setItem(INFO_SLOT, infoItem);
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int slot = event.getSlot();
        
        // Handle navigation
        if (slot == PREVIOUS_SLOT && currentPage > 0) {
            currentPage--;
            refresh();
            return;
        }
        
        if (slot == NEXT_SLOT && (currentPage + 1) * itemsPerPage < items.size()) {
            currentPage++;
            refresh();
            return;
        }
        
        // Handle item clicks
        GUIItem clickedItem = getItemAtSlot(slot);
        if (clickedItem != null) {
            handleItemClick(clickedItem, event);
        }
    }
    
    protected GUIItem getItemAtSlot(int slot) {
        // Calculate which item this slot represents
        if (slot < 10 || slot % 9 == 0 || slot % 9 == 8) {
            return null; // Border or navigation slot
        }
        
        int itemIndex = calculateItemIndex(slot);
        if (itemIndex >= 0 && itemIndex < items.size()) {
            return items.get(itemIndex);
        }
        
        return null;
    }
    
    protected int calculateItemIndex(int slot) {
        // Convert slot to item index considering borders and pagination
        int row = slot / 9;
        int col = slot % 9;
        
        if (col == 0 || col == 8) return -1; // Border
        
        int localIndex = (row - 1) * 7 + (col - 1); // 7 items per row (excluding borders)
        return currentPage * itemsPerPage + localIndex;
    }
    
    @Override
    public void refresh() {
        this.items = loadItems(); // Reload items
        setupInventory();
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
        return size;
    }
    
    @Override
    public boolean isThisInventory(Inventory inventory) {
        return this.inventory.equals(inventory);
    }
    
    @Override
    public void onClose(Player player) {
        // Default implementation - override if needed
    }
}