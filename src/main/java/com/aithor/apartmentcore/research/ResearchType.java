package com.aithor.apartmentcore.research;

import org.bukkit.Material;

/**
 * Enum representing the different research types available to players.
 * Each research provides a permanent buff when completed.
 */
public enum ResearchType {

    REVENUE_ACCELERATION("revenue_acceleration", "Revenue Acceleration",
            "Reduces income generation interval by 5% per tier",
            Material.CLOCK, 5),

    CAPITAL_GROWTH("capital_growth", "Capital Growth Strategy",
            "Increases income generation amount by 5% per tier",
            Material.GOLD_BLOCK, 5),

    TAX_EFFICIENCY("tax_efficiency", "Tax Efficiency Strategy",
            "Reduces final tax amount by 5% per tier",
            Material.EMERALD_BLOCK, 5),

    EXPANSION_PLAN("expansion_plan", "Expansion Plan",
            "Increases apartment ownership limit by +1 per tier",
            Material.BEACON, 5),

    CAPACITY_EXPANSION("capacity_expansion", "Vault Expansion",
            "Increases income capacity by 5% per tier",
            Material.CHEST, 5),

    AUCTION_EFFICIENCY("auction_efficiency", "Auction Efficiency",
            "Reduces auction fee by 5% and commission by 1% per tier",
            Material.SUNFLOWER, 5);

    private final String configKey;
    private final String displayName;
    private final String description;
    private final Material icon;
    private final int maxTier;

    ResearchType(String configKey, String displayName, String description, Material icon, int maxTier) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.maxTier = maxTier;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }

    public int getMaxTier() {
        return maxTier;
    }

    /**
     * Get a ResearchType by its config key.
     */
    public static ResearchType fromConfigKey(String key) {
        for (ResearchType type : values()) {
            if (type.configKey.equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
}
