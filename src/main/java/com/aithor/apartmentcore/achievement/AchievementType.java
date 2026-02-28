package com.aithor.apartmentcore.achievement;

/**
 * Enum representing the different achievement types available to players.
 */
public enum AchievementType {

    INCOME_MILLIONAIRE("income_millionaire", "Income Millionaire",
            "Accumulate $1,000,000 in total apartment income"),

    TAX_CONTRIBUTOR("tax_contributor", "Tax Contributor",
            "Pay a total of $1,000,000 in apartment taxes"),

    SALES_TYCOON("sales_tycoon", "Sales Tycoon",
            "Earn $1,000,000 from apartment sales"),

    RESEARCH_MASTER("research_master", "Research Master",
            "Complete any research to its maximum tier"),

    MAX_LEVEL_OWNER("max_level_owner", "Max Level Owner",
            "Own an apartment at the maximum level");

    private final String configKey;
    private final String defaultName;
    private final String defaultDescription;

    AchievementType(String configKey, String defaultName, String defaultDescription) {
        this.configKey = configKey;
        this.defaultName = defaultName;
        this.defaultDescription = defaultDescription;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public String getDefaultDescription() {
        return defaultDescription;
    }

    /**
     * Get an AchievementType by its config key.
     */
    public static AchievementType fromConfigKey(String key) {
        for (AchievementType type : values()) {
            if (type.configKey.equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
}
