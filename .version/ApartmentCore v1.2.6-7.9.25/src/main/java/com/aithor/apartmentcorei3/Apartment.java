package com.aithor.apartmentcorei3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import net.milkbowl.vault.economy.Economy;

/**
 * Apartment data model class
 */
public class Apartment {
    public String id;
    public String regionName;
    public String worldName;
    public UUID owner;
    public double price;
    public double tax;
    public int taxDays;
    public int level;
    public long lastTaxPayment;
    public double pendingIncome;
    public boolean inactive;
    public double penalty;
    public long inactiveSince;
    public String displayName;
    public String welcomeMessage;
    private long lastTaxCheckDay;

    // New fields for custom teleport location
    public String teleportWorld;
    public double teleportX, teleportY, teleportZ;
    public float teleportYaw, teleportPitch;
    public boolean hasCustomTeleport;

    public Apartment(String id, String regionName, String worldName, UUID owner, double price,
                     double tax, int taxDays, int level, long lastTaxPayment, double pendingIncome,
                     boolean inactive, double penalty, long inactiveSince, String displayName, String welcomeMessage) {
        this.id = id;
        this.regionName = regionName;
        this.worldName = worldName;
        this.owner = owner;
        this.price = price;
        this.tax = tax;
        this.taxDays = taxDays;
        this.level = level;
        this.lastTaxPayment = lastTaxPayment;
        this.pendingIncome = pendingIncome;
        this.inactive = inactive;
        this.penalty = penalty;
        this.inactiveSince = inactiveSince;
        this.displayName = displayName != null ? displayName : id;
        this.welcomeMessage = welcomeMessage != null ? welcomeMessage : "";
        this.lastTaxCheckDay = 0;
        this.hasCustomTeleport = false; // Default to false
    }

    /**
     * Set a custom teleport location.
     * @param location The new location to set.
     */
    public void setCustomTeleportLocation(Location location) {
        if (location == null) {
            this.hasCustomTeleport = false;
            return;
        }
        this.teleportWorld = location.getWorld().getName();
        this.teleportX = location.getX();
        this.teleportY = location.getY();
        this.teleportZ = location.getZ();
        this.teleportYaw = location.getYaw();
        this.teleportPitch = location.getPitch();
        this.hasCustomTeleport = true;
    }

    /**
     * Get the custom teleport location if it exists.
     * @return The Location object, or null if not set.
     */
    public Location getCustomTeleportLocation() {
        if (!hasCustomTeleport) {
            return null;
        }
        World world = Bukkit.getWorld(teleportWorld);
        if (world == null) {
            return null;
        }
        return new Location(world, teleportX, teleportY, teleportZ, teleportYaw, teleportPitch);
    }

    /**
     * Get hourly income based on level
     */
    public double getHourlyIncome(ConfigManager configManager) {
        LevelConfig config = configManager.getLevelConfig(level);
        if (config == null) {
            return 10; // Default fallback
        }
        return config.minIncome + Math.random() * (config.maxIncome - config.minIncome);
    }

    /**
     * Process daily tax
     */
    public void processDailyTax(Economy econ, ApartmentCorei3 plugin,
                               ConfigManager configManager, ApartmentManager apartmentManager) {
        if (owner == null) return;

        // Check if tax period has passed
        if (lastTaxCheckDay == 0) {
            lastTaxCheckDay = plugin.getLastMinecraftDay();
        }

        long daysSinceLastCheck = plugin.getLastMinecraftDay() - lastTaxCheckDay;

        if (daysSinceLastCheck >= taxDays) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(owner);

            if (player.isOnline() && player.getPlayer().hasPermission("apartmentcore.bypass.tax")) {
                lastTaxCheckDay = plugin.getLastMinecraftDay();
                plugin.debug("Player " + player.getName() + " bypassed tax for apartment " + id);
                return;
            }

            if (econ.has(player, tax)) {
                econ.withdrawPlayer(player, tax);
                lastTaxCheckDay = plugin.getLastMinecraftDay();
                lastTaxPayment = System.currentTimeMillis(); // Update real-time timestamp
                inactive = false;
                penalty = 0;
                inactiveSince = 0;
                plugin.debug("Auto-collected tax from " + player.getName() + " for apartment " + id);

                // Update stats
                ApartmentStats stats = apartmentManager.getStats(id);
                stats.totalTaxPaid += tax;

                if (player.isOnline()) {
                    player.getPlayer().sendMessage(ChatColor.GREEN +
                            "Tax of " + configManager.formatMoney(tax) + " collected for " + displayName);
                }
            } else {
                if (!inactive) {
                    inactive = true;
                    inactiveSince = plugin.getLastMinecraftDay() * 24000; // Convert to ticks
                    plugin.debug("Apartment " + id + " is now inactive due to unpaid taxes");

                    if (player.isOnline()) {
                        player.getPlayer().sendMessage(ChatColor.RED +
                                "Your apartment " + displayName + " is now INACTIVE due to unpaid taxes!");
                    }
                }

                // Add daily penalty
                penalty += price * configManager.getPenaltyPercentage();
                plugin.debug("Added penalty of " + configManager.formatMoney(price * configManager.getPenaltyPercentage()) +
                           " to apartment " + id);

                // Check if grace period expired
                long inactiveDays = (plugin.getLastMinecraftDay() * 24000 - inactiveSince) / 24000;
                if (inactiveDays >= configManager.getInactiveGracePeriod()) {
                    // Remove ownership
                    if (player.isOnline()) {
                        player.getPlayer().sendMessage(ChatColor.DARK_RED +
                                "You have lost apartment " + displayName + " due to extended non-payment!");
                    }

                    owner = null;
                    inactive = false;
                    penalty = 0;
                    pendingIncome = 0;
                    inactiveSince = 0;
                    lastTaxCheckDay = 0;

                    // Reset ratings and stats
                    apartmentManager.getApartmentRatings().remove(id);
                    apartmentManager.removeStats(id);

                    plugin.debug("Apartment " + id + " ownership removed due to extended non-payment");
                }
            }
        }
    }
}

/**
 * Apartment rating data class
 */
class ApartmentRating {
    public double totalRating = 0;
    public int ratingCount = 0;
    public Map<UUID, Double> raters = new java.util.HashMap<>();

    public double getAverageRating() {
        return ratingCount > 0 ? totalRating / ratingCount : 0;
    }
}

/**
 * Guestbook entry data class
 */
class GuestBookEntry {
    public final UUID senderUuid;
    public final String senderName;
    public final String message;
    public final long timestamp;

    public GuestBookEntry(UUID senderUuid, String senderName, String message, long timestamp) {
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.message = message;
        this.timestamp = timestamp;
    }
}


/**
 * Level configuration class
 */
class LevelConfig {
    public final double minIncome;
    public final double maxIncome;
    public final double upgradeCost;

    public LevelConfig(double minIncome, double maxIncome, double upgradeCost) {
        this.minIncome = minIncome;
        this.maxIncome = maxIncome;
        this.upgradeCost = upgradeCost;
    }
}

/**
 * Confirmation action class
 */
class ConfirmationAction {
    public final String type;
    public final String data;
    public final long timestamp;
    public final Map<String, Object> extraData; // For more complex actions like guestbook clear

    public ConfirmationAction(String type, String data, long timestamp) {
        this.type = type;
        this.data = data;
        this.timestamp = timestamp;
        this.extraData = new HashMap<>();
    }

    public ConfirmationAction(String type, String data, long timestamp, Map<String, Object> extraData) {
        this.type = type;
        this.data = data;
        this.timestamp = timestamp;
        this.extraData = extraData;
    }
}