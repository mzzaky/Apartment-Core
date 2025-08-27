package com.aithor.apartmentcorei3;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * PlaceholderAPI Expansion for ApartmentCore
 */
public class ApartmentPlaceholder extends PlaceholderExpansion {
    private final ApartmentCorei3 plugin;
    private final ApartmentManager apartmentManager;

    public ApartmentPlaceholder(ApartmentCorei3 plugin, ApartmentManager apartmentManager) {
        this.plugin = plugin;
        this.apartmentManager = apartmentManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "apartmentcore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Aithor";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.2.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        ConfigManager configManager = plugin.getConfigManager();

        // Handle player-specific placeholders
        if (params.equals("owned_count")) {
            return String.valueOf(apartmentManager.getApartments().values().stream()
                    .filter(a -> player.getUniqueId().equals(a.owner))
                    .count());
        }

        if (params.equals("total_income")) {
            return configManager.formatMoney(apartmentManager.getApartments().values().stream()
                    .filter(a -> player.getUniqueId().equals(a.owner))
                    .mapToDouble(a -> a.pendingIncome)
                    .sum());
        }

        if (params.equals("last_rent_claim")) {
            long timeSince = System.currentTimeMillis() - plugin.getLastRentClaimTime();
            long minutes = timeSince / 60000;
            return minutes < 1 ? "Just now" : minutes + " minutes ago";
        }

        // Handle apartment-specific placeholders
        String[] parts = params.split("_");
        if (parts.length < 2) return "";

        String apartmentId = parts[0];
        String info = parts[1];

        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) return "N/A";

        switch (info) {
            case "owner":
                return apt.owner != null ? Bukkit.getOfflinePlayer(apt.owner).getName() : "For Sale";
            case "displayname":
                return apt.displayName;
            case "price":
                return configManager.formatMoney(apt.price);
            case "tax":
                return configManager.formatMoney(apt.tax);
            case "level":
                return String.valueOf(apt.level);
            case "income":
                return configManager.formatMoney(apt.pendingIncome);
            case "status":
                return apt.inactive ? "Inactive" : "Active";
            case "rating":
                ApartmentRating rating = apartmentManager.getRating(apartmentId);
                return rating != null && rating.ratingCount > 0 ?
                        String.format("%.1f", rating.getAverageRating()) : "N/A";
            case "welcome":
                return apt.welcomeMessage;
            default:
                return "";
        }
    }
}