package com.aithor.apartmentcorei3.shop;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents shop purchases and buffs for a specific apartment
 */
public class ApartmentShopData {
    private final String apartmentId;
    private final Map<ShopItem, Integer> purchasedTiers; // ShopItem -> tier level (1-5, 0 = not purchased)
    private double totalMoneySpent; // total amount spent on this apartment's shop items

    public ApartmentShopData(String apartmentId) {
        this.apartmentId = apartmentId;
        this.purchasedTiers = new HashMap<>();
        this.totalMoneySpent = 0.0;
        
        // Initialize all shop items with tier 0 (not purchased)
        for (ShopItem item : ShopItem.values()) {
            purchasedTiers.put(item, 0);
        }
    }

    /**
     * Get the current tier of a shop item (0 = not purchased, 1-5 = tier level)
     */
    public int getTier(ShopItem item) {
        return purchasedTiers.getOrDefault(item, 0);
    }

    /**
     * Upgrade a shop item to the next tier
     * @param item The shop item to upgrade
     * @return true if upgrade was successful, false if already at max tier
     */
    public boolean upgradeTier(ShopItem item) {
        int currentTier = getTier(item);
        if (currentTier >= item.getMaxTier()) {
            return false; // already at max tier
        }
        
        int nextTier = currentTier + 1;
        purchasedTiers.put(item, nextTier);
        totalMoneySpent += item.getTierCost(nextTier);
        return true;
    }

    /**
     * Set a specific tier for a shop item (used for loading from data)
     */
    public void setTier(ShopItem item, int tier) {
        if (tier < 0 || tier > item.getMaxTier()) {
            tier = 0;
        }
        purchasedTiers.put(item, tier);
    }

    /**
     * Get the current buff value for a specific shop item
     */
    public double getBuffValue(ShopItem item) {
        int tier = getTier(item);
        if (tier == 0) return 0.0;
        return item.getBuffValue(tier);
    }

    /**
     * Check if an item can be upgraded to the next tier
     */
    public boolean canUpgrade(ShopItem item) {
        return getTier(item) < item.getMaxTier();
    }

    /**
     * Get the cost to upgrade to the next tier
     */
    public double getUpgradeCost(ShopItem item) {
        int currentTier = getTier(item);
        if (currentTier >= item.getMaxTier()) {
            return 0.0; // cannot upgrade
        }
        return item.getTierCost(currentTier + 1);
    }

    /**
     * Get total money spent on this apartment's shop items
     */
    public double getTotalMoneySpent() {
        return totalMoneySpent;
    }

    /**
     * Set total money spent (used for loading from data)
     */
    public void setTotalMoneySpent(double amount) {
        this.totalMoneySpent = amount;
    }

    /**
     * Add to total money spent
     */
    public void addMoneySpent(double amount) {
        this.totalMoneySpent += amount;
    }

    /**
     * Get 50% refund value when apartment is sold
     */
    public double getRefundAmount() {
        return totalMoneySpent * 0.5;
    }

    /**
     * Reset all shop data (used when apartment is sold)
     */
    public void reset() {
        for (ShopItem item : ShopItem.values()) {
            purchasedTiers.put(item, 0);
        }
        totalMoneySpent = 0.0;
    }

    /**
     * Get apartment ID
     */
    public String getApartmentId() {
        return apartmentId;
    }

    /**
     * Get all purchased tiers as a map
     */
    public Map<ShopItem, Integer> getPurchasedTiers() {
        return new HashMap<>(purchasedTiers);
    }

    /**
     * Check if any shop items have been purchased
     */
    public boolean hasAnyPurchases() {
        return purchasedTiers.values().stream().anyMatch(tier -> tier > 0);
    }

    /**
     * Get total buff value for a specific buff type across all items
     */
    public double getTotalBuffValue(ShopBuffType buffType) {
        double total = 0.0;
        for (ShopItem item : ShopItem.values()) {
            if (item.getBuffType() == buffType) {
                total += getBuffValue(item);
            }
        }
        return total;
    }
}