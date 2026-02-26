package com.aithor.apartmentcorei3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

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

    // Legacy tax fields (kept for backward-compat in storage; no longer used for logic)
    public double tax;
    public int taxDays;

    public int level;
    public long lastTaxPayment;
    public double pendingIncome;

    // Legacy inactive/penalty fields (kept for backward-compat)
    public boolean inactive;
    public double penalty;
    public long inactiveSince;

    public String displayName;
    public String welcomeMessage;
    private long lastTaxCheckDay; // legacy checkpoint

    // Custom teleport location
    public String teleportWorld;
    public double teleportX, teleportY, teleportZ;
    public float teleportYaw, teleportPitch;
    public boolean hasCustomTeleport;

    // New tax system state
    public List<TaxInvoice> taxInvoices; // active and paid invoices
    public boolean autoTaxPayment;       // per-apartment auto payment flag
    public long lastInvoiceAt;           // last invoice creation timestamp (epoch millis)

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

        // New tax system defaults
        this.taxInvoices = new ArrayList<>();
        this.autoTaxPayment = false;
        this.lastInvoiceAt = 0L;
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

    // =========================
    // New Invoice-based Tax API
    // =========================

    /**
     * Base tax percentage based on level.
     * Requirement: 2.5% per level (Level 3 = 7.5%).
     */
    public double getBaseTaxPercent() {
        return 0.025 * Math.max(1, level);
    }

    /**
     * Base tax amount for a new invoice before multipliers.
     */
    public double computeBaseTaxAmount() {
        return price * getBaseTaxPercent();
    }

    /**
     * Compute current total unpaid amount (sum of all unpaid invoices).
     */
    public double getTotalUnpaid() {
        double sum = 0.0;
        if (taxInvoices != null) {
            for (TaxInvoice inv : taxInvoices) {
                if (!inv.isPaid()) sum += inv.amount;
            }
        }
        return sum;
    }

    /**
     * Compute current tax status from oldest unpaid invoice.
     */
    public TaxStatus computeTaxStatus(long now) {
        if (owner == null) return TaxStatus.ACTIVE;
        if (taxInvoices == null || taxInvoices.isEmpty()) {
            return TaxStatus.ACTIVE;
        }
        long oldestCreatedAt = Long.MAX_VALUE;
        boolean hasUnpaid = false;
        for (TaxInvoice inv : taxInvoices) {
            if (!inv.isPaid()) {
                hasUnpaid = true;
                if (inv.createdAt < oldestCreatedAt) {
                    oldestCreatedAt = inv.createdAt;
                }
            }
        }
        if (!hasUnpaid) return TaxStatus.ACTIVE;

        long days = Math.max(0L, (now - oldestCreatedAt) / 86_400_000L); // real days since oldest unpaid
        if (days >= 7) return TaxStatus.REPOSSESSION;
        if (days >= 5) return TaxStatus.INACTIVE;
        if (days >= 3) return TaxStatus.OVERDUE;
        return TaxStatus.ACTIVE;
    }

    /**
     * Whether this apartment can currently generate income.
     * - Active: yes
     * - Overdue/Inactive/Repossession: no
     */
    public boolean canGenerateIncome(long now) {
        return computeTaxStatus(now) == TaxStatus.ACTIVE;
    }

    /**
     * Main tick to maintain invoice system.
     * - Generate new invoice every 24h real time (can stack).
     * - Auto-pay if enabled and player has funds.
     * - Send layered notifications.
     * - Apply status transitions and repossession at day 7.
     */
    public void tickTaxInvoices(Economy econ, ApartmentCorei3 plugin,
                                ConfigManager configManager, ApartmentManager apartmentManager) {
        if (owner == null) return;

        long now = System.currentTimeMillis();
        if (lastInvoiceAt == 0L) {
            // Seed with lastTaxPayment for backward compatibility
            lastInvoiceAt = Math.max(0L, lastTaxPayment);
            if (lastInvoiceAt == 0L) lastInvoiceAt = now;
        }

        // 1) Generate new invoices for each full day passed since lastInvoiceAt
        final long dayMs = 86_400_000L;
        while (now - lastInvoiceAt >= dayMs) {
            long newCreatedAt = lastInvoiceAt + dayMs;

            // Determine current status BEFORE creating this invoice (for multiplier)
            TaxStatus statusBefore = computeTaxStatus(now);
            int multiplier = 1;
            if (statusBefore == TaxStatus.OVERDUE) multiplier = 2;
            else if (statusBefore == TaxStatus.INACTIVE) multiplier = 3;
            // Repossession handled below; if reached, owner will be null and loop breaks next tick.

            double base = computeBaseTaxAmount();
            double amount = base * multiplier;

            // Due at day 3 since creation
            TaxInvoice invoice = new TaxInvoice(amount, newCreatedAt, newCreatedAt + 3 * dayMs);
            if (taxInvoices == null) taxInvoices = new ArrayList<>();
            taxInvoices.add(invoice);
            lastInvoiceAt = newCreatedAt;

            OfflinePlayer player = Bukkit.getOfflinePlayer(owner);
            // Send "new bill" notification once for this invoice
            if (!invoice.notifNewSent) {
                if (player.isOnline()) {
                    player.getPlayer().sendMessage("📢 A new tax bill of " + configManager.formatMoney(amount) + " has appeared.");
                }
                invoice.notifNewSent = true;
            }

            // Attempt auto-payment immediately if enabled and funds available
            if (autoTaxPayment && player != null) {
                if (econ.has(player, invoice.amount)) {
                    econ.withdrawPlayer(player, invoice.amount);
                    invoice.paidAt = System.currentTimeMillis();
                    lastTaxPayment = invoice.paidAt; // maintain legacy field
                    // Update stats
                    ApartmentStats stats = apartmentManager.getStats(id);
                    stats.totalTaxPaid += invoice.amount;
                    if (player.isOnline()) {
                        player.getPlayer().sendMessage(ChatColor.GREEN + "Auto-payment successful: " +
                                configManager.formatMoney(invoice.amount) + " for " + displayName);
                    }
                }
            }
        }

        // 2) Notifications for existing unpaid invoices and status effects
        OfflinePlayer player = Bukkit.getOfflinePlayer(owner);

        // Try auto-paying existing unpaid invoices if enabled and balance allows (oldest first)
        if (autoTaxPayment && player != null) {
            java.util.List<TaxInvoice> unpaid = new java.util.ArrayList<>();
            if (taxInvoices != null) {
                for (TaxInvoice i : taxInvoices) {
                    if (!i.isPaid()) unpaid.add(i);
                }
            }
            unpaid.sort(java.util.Comparator.comparingLong(i -> i.createdAt));
            for (TaxInvoice i : unpaid) {
                if (econ.has(player, i.amount)) {
                    econ.withdrawPlayer(player, i.amount);
                    i.paidAt = now;
                    lastTaxPayment = now;
                    // Update stats
                    ApartmentStats stats2 = apartmentManager.getStats(id);
                    stats2.totalTaxPaid += i.amount;
                } else {
                    break; // stop at first unaffordable invoice
                }
            }
        }

        double totalUnpaid = getTotalUnpaid();
        TaxStatus status = computeTaxStatus(now);

        // Iterate invoices for reminders
        if (taxInvoices != null) {
            for (TaxInvoice inv : taxInvoices) {
                if (inv.isPaid()) continue;
                long days = inv.daysSinceCreated(now);

                if (days >= 2 && !inv.notifDay2Sent) {
                    if (player.isOnline()) {
                        player.getPlayer().sendMessage("⏳ Remember! A tax of " +
                                configManager.formatMoney(inv.amount) + " is due tomorrow.");
                    }
                    inv.notifDay2Sent = true;
                }
                if (days >= 3 && !inv.notifDay3Sent) {
                    if (player.isOnline()) {
                        player.getPlayer().sendMessage("⚠ Your taxes are overdue! Total arrears are now " +
                                configManager.formatMoney(getTotalUnpaid()) + ".");
                    }
                    inv.notifDay3Sent = true;
                }
                if (days >= 5 && !inv.notifDay5Sent) {
                    // Mark apartment inactive from day 5
                    if (!inactive) {
                        inactive = true;
                        inactiveSince = now;
                    }
                    if (player.isOnline()) {
                        player.getPlayer().sendMessage("⛔ Your apartment is now Inactive! Total arrears are now " +
                                configManager.formatMoney(getTotalUnpaid()) + ".");
                    }
                    inv.notifDay5Sent = true;
                }
            }
        }

        // 3) Apply status effects
        switch (status) {
            case ACTIVE:
                // Ensure legacy inactive flag is off
                if (inactive) {
                    inactive = false;
                    inactiveSince = 0L;
                }
                break;
            case OVERDUE:
                // Income stops, but not fully inactive (leave inactive=false for UX)
                // We'll keep legacy flag untouched unless previously set.
                break;
            case INACTIVE:
                // Ensure inactive=true (already set above)
                if (!inactive) {
                    inactive = true;
                    inactiveSince = now;
                }
                break;
            case REPOSSESSION:
                // 4) Repossess at day 7
                if (player.isOnline()) {
                    player.getPlayer().sendMessage("❌ Your apartment has been repossessed by the server for failure to pay taxes.");
                }

                // Remove ownership permanently
                java.util.UUID prevOwner = owner;
                // Remove owner from region (even if offline)
                try {
                    apartmentManager.removeOwnerUuidFromRegion(this, prevOwner);
                } catch (Throwable ignored) {}
                owner = null;
                inactive = false;
                penalty = 0;
                pendingIncome = 0;
                inactiveSince = 0;
                lastTaxCheckDay = 0;
                lastInvoiceAt = 0;
                taxInvoices.clear();
                setCustomTeleportLocation(null); // clear custom teleport

                // Reset ratings and stats
                apartmentManager.getApartmentRatings().remove(id);
                apartmentManager.removeStats(id);

                plugin.debug("Apartment " + id + " repossessed due to unpaid taxes.");
                break;
        }
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