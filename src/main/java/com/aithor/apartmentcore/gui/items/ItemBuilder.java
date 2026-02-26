package com.aithor.apartmentcore.gui.items;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating ItemStacks with ease
 */
public class ItemBuilder {
    
    private final ItemStack itemStack;
    private final ItemMeta itemMeta;
    
    public ItemBuilder(Material material) {
        this.itemStack = new ItemStack(material);
        this.itemMeta = itemStack.getItemMeta();
    }
    
    public ItemBuilder(Material material, int amount) {
        this.itemStack = new ItemStack(material, amount);
        this.itemMeta = itemStack.getItemMeta();
    }
    
    public ItemBuilder(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
        this.itemMeta = this.itemStack.getItemMeta();
    }
    
    /**
     * Set the display name of the item
     * @param name The display name (supports color codes)
     * @return This builder
     */
    public ItemBuilder name(String name) {
        if (itemMeta != null) {
            itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }
        return this;
    }
    
    /**
     * Set the lore of the item
     * @param lore The lore lines (supports color codes)
     * @return This builder
     */
    public ItemBuilder lore(String... lore) {
        if (itemMeta != null) {
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            itemMeta.setLore(loreList);
        }
        return this;
    }
    
    /**
     * Set the lore of the item
     * @param lore The lore lines list
     * @return This builder
     */
    public ItemBuilder lore(List<String> lore) {
        if (itemMeta != null) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            itemMeta.setLore(coloredLore);
        }
        return this;
    }
    
    /**
     * Add lines to existing lore
     * @param lines The lines to add
     * @return This builder
     */
    public ItemBuilder addLore(String... lines) {
        if (itemMeta != null) {
            List<String> lore = itemMeta.getLore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            
            for (String line : lines) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            
            itemMeta.setLore(lore);
        }
        return this;
    }
    
    /**
     * Set the amount of the item
     * @param amount The amount
     * @return This builder
     */
    public ItemBuilder amount(int amount) {
        itemStack.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }
    
    /**
     * Add an enchantment to the item
     * @param enchantment The enchantment
     * @param level The level
     * @return This builder
     */
    public ItemBuilder enchant(Enchantment enchantment, int level) {
        if (itemMeta != null) {
            itemMeta.addEnchant(enchantment, level, true);
        }
        return this;
    }
    
    /**
     * Add a glow effect (fake enchantment) to the item
     * @return This builder
     */
    public ItemBuilder glow() {
        if (itemMeta != null) {
            // Use a safe enchant across versions (hidden to simulate glow)
            itemMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }
    
    /**
     * Add item flags to hide certain attributes
     * @param flags The flags to add
     * @return This builder
     */
    public ItemBuilder flags(ItemFlag... flags) {
        if (itemMeta != null) {
            itemMeta.addItemFlags(flags);
        }
        return this;
    }
    
    /**
     * Hide all attributes
     * @return This builder
     */
    public ItemBuilder hideAll() {
        if (itemMeta != null) {
            itemMeta.addItemFlags(ItemFlag.values());
        }
        return this;
    }
    
    /**
     * Set the skull owner (for player heads)
     * @param owner The player name
     * @return This builder
     */
    public ItemBuilder skull(String owner) {
        if (itemMeta instanceof SkullMeta meta) {
            // Prefer owning player API (avoids deprecated setOwner)
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
        }
        return this;
    }
    
    /**
     * Set custom model data
     * @param data The custom model data
     * @return This builder
     */
    public ItemBuilder modelData(int data) {
        if (itemMeta != null) {
            itemMeta.setCustomModelData(data);
        }
        return this;
    }
    
    /**
     * Set the item as unbreakable
     * @return This builder
     */
    public ItemBuilder unbreakable() {
        if (itemMeta != null) {
            itemMeta.setUnbreakable(true);
            itemMeta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        return this;
    }
    
    /**
     * Build the final ItemStack
     * @return The built ItemStack
     */
    public ItemStack build() {
        if (itemMeta != null) {
            itemStack.setItemMeta(itemMeta);
        }
        return itemStack;
    }
    
    /**
     * Quick method to create a simple item
     * @param material The material
     * @param name The display name
     * @param lore The lore lines
     * @return The created ItemStack
     */
    public static ItemStack create(Material material, String name, String... lore) {
        return new ItemBuilder(material)
                .name(name)
                .lore(lore)
                .build();
    }
    
    /**
     * Create a GUI filler item
     * @param material The material
     * @return The filler ItemStack
     */
    public static ItemStack filler(Material material) {
        return new ItemBuilder(material)
                .name(" ")
                .hideAll()
                .build();
    }
}