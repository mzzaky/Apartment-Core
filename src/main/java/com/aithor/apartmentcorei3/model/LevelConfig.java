package com.aithor.apartmentcorei3.model;

/**
 * Public level configuration model used across the plugin
 */
public class LevelConfig {
    public final double minIncome;
    public final double maxIncome;
    public final double upgradeCost;

    public LevelConfig(double minIncome, double maxIncome, double upgradeCost) {
        this.minIncome = minIncome;
        this.maxIncome = maxIncome;
        this.upgradeCost = upgradeCost;
    }
}