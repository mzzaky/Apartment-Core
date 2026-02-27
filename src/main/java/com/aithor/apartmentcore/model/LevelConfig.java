package com.aithor.apartmentcore.model;

/**
 * Public level configuration model used across the plugin
 */
public class LevelConfig {
    public final double minIncome;
    public final double maxIncome;
    public final double upgradeCost;
    public final double incomeCapacity;
    public final long upgradeDuration; // in ticks; 0 = instant

    public LevelConfig(double minIncome, double maxIncome, double upgradeCost, double incomeCapacity,
            long upgradeDuration) {
        this.minIncome = minIncome;
        this.maxIncome = maxIncome;
        this.upgradeCost = upgradeCost;
        this.incomeCapacity = incomeCapacity;
        this.upgradeDuration = upgradeDuration;
    }
}