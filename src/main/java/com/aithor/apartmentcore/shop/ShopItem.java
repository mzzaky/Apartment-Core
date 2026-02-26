package com.aithor.apartmentcore.shop;

import org.bukkit.Material;
import com.aithor.apartmentcore.shop.ShopBuffType;

/**
 * Represents a shop item that can be purchased to buff apartments
 */
public enum ShopItem {
    PREMIUM_KITCHEN("Premium Kitchen", Material.FURNACE, ShopBuffType.INCOME_BONUS, 
        new double[]{10.0, 15.0, 20.0, 25.0, 30.0}, // percentage bonus per tier
        new double[]{5000, 10000, 18000, 30000, 50000}), // cost per tier
    
    LUXURY_FURNITURE("Luxury Furniture", Material.ITEM_FRAME, ShopBuffType.BASE_INCOME, 
        new double[]{50.0, 100.0, 175.0, 275.0, 400.0}, // flat income bonus per tier
        new double[]{3000, 8000, 15000, 25000, 40000}), // cost per tier
    
    SOLAR_PANEL_SYSTEM("Solar Panel System", Material.DAYLIGHT_DETECTOR, ShopBuffType.TAX_REDUCTION,
        new double[]{5.0, 10.0, 15.0, 20.0, 25.0}, // percentage tax reduction per tier
        new double[]{7000, 12000, 20000, 32000, 50000}), // cost per tier
    
    HIGH_SPEED_INTERNET("High Speed Internet", Material.REDSTONE, ShopBuffType.INCOME_SPEED,
        new double[]{1.0, 2.0, 3.0, 4.0, 5.0}, // tick reduction per tier
        new double[]{4000, 9000, 16000, 28000, 45000}), // cost per tier
    
    EXTRA_LIVING_ROOM("Extra Living Room", Material.BOOKSHELF, ShopBuffType.MAX_MESSAGES,
        new double[]{10.0, 20.0, 30.0, 40.0, 50.0}, // extra messages per tier
        new double[]{2000, 5000, 10000, 18000, 30000}); // cost per tier

    private final String displayName;
    private final Material icon;
    private final ShopBuffType buffType;
    private final double[] buffValues; // values for tier 1-5
    private final double[] tierCosts; // costs for tier 1-5

    ShopItem(String displayName, Material icon, ShopBuffType buffType, double[] buffValues, double[] tierCosts) {
        this.displayName = displayName;
        this.icon = icon;
        this.buffType = buffType;
        this.buffValues = buffValues;
        this.tierCosts = tierCosts;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public ShopBuffType getBuffType() {
        return buffType;
    }

    public double getBuffValue(int tier) {
        if (tier < 1 || tier > 5) return 0.0;
        return buffValues[tier - 1];
    }

    public double getTierCost(int tier) {
        if (tier < 1 || tier > 5) return 0.0;
        return tierCosts[tier - 1];
    }

    public double getTotalCostUpToTier(int tier) {
        if (tier < 1 || tier > 5) return 0.0;
        double total = 0.0;
        for (int i = 0; i < tier; i++) {
            total += tierCosts[i];
        }
        return total;
    }

    public int getMaxTier() {
        return 5;
    }

    public String getDescription() {
        return switch (this) {
            case PREMIUM_KITCHEN -> "Increases your apartment's income by a percentage bonus";
            case LUXURY_FURNITURE -> "Adds flat income bonus to your apartment";
            case SOLAR_PANEL_SYSTEM -> "Reduces your apartment's tax payments by percentage";
            case HIGH_SPEED_INTERNET -> "Speeds up income generation by reducing tick intervals";
            case EXTRA_LIVING_ROOM -> "Increases maximum guestbook messages for your apartment";
        };
    }

    public String getBuffDescription(int tier) {
        if (tier < 1 || tier > 5) return "Invalid tier";
        double value = getBuffValue(tier);
        return switch (buffType) {
            case INCOME_BONUS -> String.format("+%.1f%% income bonus", value);
            case BASE_INCOME -> String.format("+$%.0f base income", value);
            case TAX_REDUCTION -> String.format("-%.1f%% tax reduction", value);
            case INCOME_SPEED -> String.format("-%d tick faster income", (int)value);
            case MAX_MESSAGES -> String.format("+%d max guestbook messages", (int)value);
        };
    }
}