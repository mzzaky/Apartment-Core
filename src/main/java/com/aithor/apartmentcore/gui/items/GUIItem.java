package com.aithor.apartmentcore.gui.items;

import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * Wrapper class for GUI items with click actions
 */
public class GUIItem {
    
    private final ItemStack itemStack;
    private final String id;
    private final Object data;
    private final Consumer<GUIItem> clickAction;
    
    public GUIItem(ItemStack itemStack, String id) {
        this(itemStack, id, null, null);
    }
    
    public GUIItem(ItemStack itemStack, String id, Object data) {
        this(itemStack, id, data, null);
    }
    
    public GUIItem(ItemStack itemStack, String id, Object data, Consumer<GUIItem> clickAction) {
        this.itemStack = itemStack;
        this.id = id;
        this.data = data;
        this.clickAction = clickAction;
    }
    
    /**
     * Get the ItemStack for this GUI item
     * @return The ItemStack
     */
    public ItemStack getItemStack() {
        return itemStack;
    }
    
    /**
     * Get the unique ID of this item
     * @return The item ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * Get the data associated with this item
     * @return The item data
     */
    public Object getData() {
        return data;
    }
    
    /**
     * Get typed data
     * @param clazz The class to cast to
     * @param <T> The type
     * @return The casted data or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(Class<T> clazz) {
        if (data != null && clazz.isAssignableFrom(data.getClass())) {
            return (T) data;
        }
        return null;
    }
    
    /**
     * Execute the click action for this item
     */
    public void executeAction() {
        if (clickAction != null) {
            clickAction.accept(this);
        }
    }
    
    /**
     * Check if this item has a click action
     * @return True if it has an action
     */
    public boolean hasAction() {
        return clickAction != null;
    }
}