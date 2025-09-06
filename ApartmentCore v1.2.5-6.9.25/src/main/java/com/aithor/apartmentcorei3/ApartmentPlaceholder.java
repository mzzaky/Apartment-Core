package com.aithor.apartmentcorei3;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

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
        return "1.2.5";
    }

    @Override
    public boolean persist() {
        return true;
    }

    private String formatTime(long millis) {
        if (millis < 0) return "0s";
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString().trim();
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

        // Handle apartment-specific placeholders
        String[] parts = params.split("_");
        if (parts.length < 2) return "";

        String apartmentId = parts[0];
        String infoType = String.join("_", Arrays.copyOfRange(parts, 1, parts.length));


        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) return "N/A";

        return switch (infoType) {
            case "owner" -> apt.owner != null ? Bukkit.getOfflinePlayer(apt.owner).getName() : "For Sale";
            case "displayname" -> apt.displayName;
            case "price" -> configManager.formatMoney(apt.price);
            case "tax" -> configManager.formatMoney(apt.tax);
            case "level" -> String.valueOf(apt.level);
            case "income" -> configManager.formatMoney(apt.pendingIncome);
            case "status" -> apt.inactive ? "Inactive" : "Active";
            case "rating" -> {
                ApartmentRating rating = apartmentManager.getRating(apartmentId);
                yield rating != null && rating.ratingCount > 0 ?
                        String.format("%.1f", rating.getAverageRating()) : "N/A";
            }
            case "welcome" -> apt.welcomeMessage;
            case "tax_due_in" -> {
                if (apt.owner == null || apt.inactive) yield "N/A";
                long nextTaxMillis = apt.lastTaxPayment + (apt.taxDays * 20L * 60L * 1000L);
                long taxTimeRemaining = nextTaxMillis - System.currentTimeMillis();
                yield taxTimeRemaining > 0 ? formatTime(taxTimeRemaining) : "Overdue";
            }
            case "income_in" -> {
                if (apt.owner == null || apt.inactive) yield "N/A";
                long nextIncomeMillis = plugin.getLastIncomeGenerationTime() + 50000;
                long incomeTimeRemaining = nextIncomeMillis - System.currentTimeMillis();
                yield incomeTimeRemaining > 0 ? formatTime(incomeTimeRemaining) : "Now";
            }
            default -> "";
        };
    }
}