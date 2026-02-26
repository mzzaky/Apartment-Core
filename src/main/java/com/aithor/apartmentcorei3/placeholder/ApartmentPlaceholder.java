package com.aithor.apartmentcorei3.placeholder;

import com.aithor.apartmentcorei3.ApartmentCorei3;
import com.aithor.apartmentcorei3.manager.ApartmentManager;
import com.aithor.apartmentcorei3.manager.ConfigManager;
import com.aithor.apartmentcorei3.model.Apartment;
import com.aithor.apartmentcorei3.model.ApartmentRating;
import com.aithor.apartmentcorei3.model.ApartmentStats;
import com.aithor.apartmentcorei3.model.TaxStatus;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * PlaceholderAPI Expansion for ApartmentCore
 */
public class ApartmentPlaceholder extends PlaceholderExpansion {
    private final ApartmentCorei3 plugin;
    private final ApartmentManager apartmentManager;
    private final ConfigManager configManager;
    // Optional refresh heartbeat for future cache logic
    private volatile long lastRefreshAt = System.currentTimeMillis();

    public ApartmentPlaceholder(ApartmentCorei3 plugin, ApartmentManager apartmentManager) {
        this.plugin = plugin;
        this.apartmentManager = apartmentManager;
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public String getIdentifier() {
        return "apartmentcore";
    }

    @Override
    public String getAuthor() {
        return "Aithor";
    }

    @Override
    public String getVersion() {
        return "1.3.2";
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
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";

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

        // Handle apartment-specific and statistic placeholders
        String[] parts = params.split("_");
        if (parts.length < 2) return "";

        // New Statistic Placeholders: %apartmentcore_statistic_<id>_<name>%
        if (parts[0].equalsIgnoreCase("statistic")) {
            if (parts.length < 3) return "Invalid Stat Placeholder";
            String aptId = parts[1];
            String statName = String.join("_", Arrays.copyOfRange(parts, 2, parts.length));
            Apartment apt = apartmentManager.getApartment(aptId);
            if (apt == null) return "N/A";

            ApartmentStats stats = apartmentManager.getStats(aptId);
            if (stats == null) return "0"; // Default to 0 if no stats

            return switch (statName) {
                case "total_tax_paid" -> configManager.formatMoney(stats.totalTaxPaid);
                case "total_income_generated" -> configManager.formatMoney(stats.totalIncomeGenerated);
                case "ownership_age_days" -> String.valueOf(stats.ownershipAgeDays);
                default -> "Unknown Statistic";
            };
        }

        // Existing Apartment-specific Placeholders
        String apartmentId = parts[0];
        String infoType = String.join("_", Arrays.copyOfRange(parts, 1, parts.length));


        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) return "N/A";

        // Handle shop buff placeholders
        if (infoType.startsWith("shop_")) {
            return handleShopPlaceholder(apt, infoType);
        }

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
            case "next_invoice_in" -> { // Replaces tax_due_in for better accuracy
                if (apt.owner == null) yield "N/A";
                long now = System.currentTimeMillis();
                // Time until the next 24-hour cycle for a new bill
                long nextInvoiceInMs = Math.max(0L, (apt.lastInvoiceAt == 0L ? 0L : (apt.lastInvoiceAt + 86_400_000L) - now));
                yield formatTime(nextInvoiceInMs);
            }
            case "tax_status" -> {
                if (apt.owner == null) yield "For Sale";
                TaxStatus status = apt.computeTaxStatus(System.currentTimeMillis());
                yield status.name();
            }
            case "income_in" -> {
                if (apt.owner == null || apt.inactive) yield "N/A";
                long nextIncomeMillis = plugin.getLastIncomeGenerationTime() + 50000;
                long incomeTimeRemaining = nextIncomeMillis - System.currentTimeMillis();
                yield incomeTimeRemaining > 0 ? formatTime(incomeTimeRemaining) : "Now";
            }
            default -> "Invalid Placeholder";
        };
    }
    
    /**
     * Refresh placeholders cache/heartbeat (currently no caching; acts as a heartbeat).
     */
    public void refresh() {
        lastRefreshAt = System.currentTimeMillis();
    }
    
    /**
     * Handle shop-related placeholders
     * Patterns:
     * - shop_total_investment: Total money spent on shop items
     * - shop_income_bonus: Total income bonus percentage
     * - shop_base_income: Total base income bonus (flat amount)
     * - shop_tax_reduction: Total tax reduction percentage
     * - shop_income_speed: Total income speed bonus (tick reduction)
     * - shop_max_messages: Total max messages bonus
     * - shop_<item>_tier: Tier of specific shop item (premium_kitchen, luxury_furniture, etc.)
     * - shop_<item>_value: Buff value of specific shop item
     */
    private String handleShopPlaceholder(Apartment apt, String infoType) {
        if (plugin.getShopManager() == null) {
            return "0";
        }
        
        var shopManager = plugin.getShopManager();
        var shopData = shopManager.getShopData(apt.id);
        
        // Remove "shop_" prefix
        String shopParam = infoType.substring(5);
        
        return switch (shopParam) {
            case "total_investment" -> configManager.formatMoney(shopData.getTotalMoneySpent());
            case "income_bonus" -> String.format("%.1f", shopManager.getIncomeBonusPercentage(apt.id));
            case "base_income" -> configManager.formatMoney(shopManager.getBaseIncomeBonus(apt.id));
            case "tax_reduction" -> String.format("%.1f", shopManager.getTaxReductionPercentage(apt.id));
            case "income_speed" -> String.format("%.0f", shopManager.getIncomeSpeedBonus(apt.id));
            case "max_messages" -> String.valueOf(shopManager.getMaxMessagesBonus(apt.id));
            
            // Specific item placeholders
            case "premium_kitchen_tier" -> String.valueOf(shopData.getTier(com.aithor.apartmentcorei3.shop.ShopItem.PREMIUM_KITCHEN));
            case "premium_kitchen_value" -> String.format("%.1f%%", shopData.getBuffValue(com.aithor.apartmentcorei3.shop.ShopItem.PREMIUM_KITCHEN));
            
            case "luxury_furniture_tier" -> String.valueOf(shopData.getTier(com.aithor.apartmentcorei3.shop.ShopItem.LUXURY_FURNITURE));
            case "luxury_furniture_value" -> configManager.formatMoney(shopData.getBuffValue(com.aithor.apartmentcorei3.shop.ShopItem.LUXURY_FURNITURE));
            
            case "solar_panel_tier" -> String.valueOf(shopData.getTier(com.aithor.apartmentcorei3.shop.ShopItem.SOLAR_PANEL_SYSTEM));
            case "solar_panel_value" -> String.format("%.1f%%", shopData.getBuffValue(com.aithor.apartmentcorei3.shop.ShopItem.SOLAR_PANEL_SYSTEM));
            
            case "high_speed_internet_tier" -> String.valueOf(shopData.getTier(com.aithor.apartmentcorei3.shop.ShopItem.HIGH_SPEED_INTERNET));
            case "high_speed_internet_value" -> String.format("%.0f", shopData.getBuffValue(com.aithor.apartmentcorei3.shop.ShopItem.HIGH_SPEED_INTERNET));
            
            case "extra_living_room_tier" -> String.valueOf(shopData.getTier(com.aithor.apartmentcorei3.shop.ShopItem.EXTRA_LIVING_ROOM));
            case "extra_living_room_value" -> String.format("%.0f", shopData.getBuffValue(com.aithor.apartmentcorei3.shop.ShopItem.EXTRA_LIVING_ROOM));
            
            default -> "0";
        };
    }
}