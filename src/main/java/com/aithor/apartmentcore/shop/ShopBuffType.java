package com.aithor.apartmentcore.shop;

/**
 * Enum representing different types of buffs that can be applied to apartments
 */
public enum ShopBuffType {
    /**
     * Increases income by a percentage (multiplicative bonus)
     */
    INCOME_BONUS("Income Bonus", "%"),
    
    /**
     * Adds flat income amount (additive bonus)
     */
    BASE_INCOME("Base Income", "$"),
    
    /**
     * Reduces tax payments by percentage
     */
    TAX_REDUCTION("Tax Reduction", "%"),
    
    /**
     * Reduces income generation tick intervals (makes income generate faster)
     */
    INCOME_SPEED("Income Speed", "ticks"),
    
    /**
     * Increases maximum guestbook messages
     */
    MAX_MESSAGES("Max Messages", "msgs");

    private final String displayName;
    private final String unit;

    ShopBuffType(String displayName, String unit) {
        this.displayName = displayName;
        this.unit = unit;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUnit() {
        return unit;
    }
}