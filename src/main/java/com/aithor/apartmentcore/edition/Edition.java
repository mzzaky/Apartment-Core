package com.aithor.apartmentcore.edition;

/**
 * Represents the plugin edition compiled via Maven profiles.
 * The active edition is injected at build time through resource filtering
 * in plugin.yml ({@code edition: FREE} or {@code edition: PRO}).
 */
public enum Edition {

    FREE("Free"),
    PRO("Pro");

    private final String label;

    Edition(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isFree() {
        return this == FREE;
    }

    public boolean isPro() {
        return this == PRO;
    }

    /**
     * Parse an edition string from plugin.yml.
     * Defaults to FREE if the value is unrecognised.
     */
    public static Edition fromString(String value) {
        if (value == null) return FREE;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FREE;
        }
    }
}
