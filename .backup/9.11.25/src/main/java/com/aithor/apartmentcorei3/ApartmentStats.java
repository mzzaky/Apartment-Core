package com.aithor.apartmentcorei3;

import java.util.HashMap;
import java.util.Map;

public class ApartmentStats {
    public double totalTaxPaid;
    public double totalIncomeGenerated;
    public int ownershipAgeDays;

    public ApartmentStats() {
        this.totalTaxPaid = 0;
        this.totalIncomeGenerated = 0;
        this.ownershipAgeDays = 0;
    }

    public ApartmentStats(double totalTaxPaid, double totalIncomeGenerated, int ownershipAgeDays) {
        this.totalTaxPaid = totalTaxPaid;
        this.totalIncomeGenerated = totalIncomeGenerated;
        this.ownershipAgeDays = ownershipAgeDays;
    }

    /**
     * Converts the data into a format that can be saved in a YML file.
     * @return Map containing statistical data.
     */
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("total-tax-paid", totalTaxPaid);
        map.put("total-income-generated", totalIncomeGenerated);
        map.put("ownership-age-days", ownershipAgeDays);
        map.put("total-auctions", totalAuctions);
        map.put("highest-auction-price", highestAuctionPrice);
        map.put("average-auction-price", averageAuctionPrice);
        map.put("last-auction-date", lastAuctionDate);
        return map;
    }

    /**
     * Creates an ApartmentStats object from YML data.
     * @param map Data from the YML file.
     * @return New ApartmentStats object.
     */
    public static ApartmentStats deserialize(Map<String, Object> map) {
        if (map == null) {
            return new ApartmentStats();
        }
        ApartmentStats stats = new ApartmentStats(
            ((Number) map.getOrDefault("total-tax-paid", 0)).doubleValue(),
            ((Number) map.getOrDefault("total-income-generated", 0)).doubleValue(),
            ((Number) map.getOrDefault("ownership-age-days", 0)).intValue()
        );
        
        // Load auction stats
        stats.totalAuctions = ((Number) map.getOrDefault("total-auctions", 0)).intValue();
        stats.highestAuctionPrice = ((Number) map.getOrDefault("highest-auction-price", 0)).doubleValue();
        stats.averageAuctionPrice = ((Number) map.getOrDefault("average-auction-price", 0)).doubleValue();
        stats.lastAuctionDate = ((Number) map.getOrDefault("last-auction-date", 0L)).longValue();
        
        return stats;
    }
}