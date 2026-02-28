package com.aithor.apartmentcore.model;

import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.manager.ApartmentManager;
import com.aithor.apartmentcore.manager.ConfigManager;

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

    // Legacy tax fields (kept for backward-compat in storage; no longer used for
    // logic)
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
    public boolean autoTaxPayment; // per-apartment auto payment flag
    public long lastInvoiceAt; // last invoice creation timestamp (epoch millis)

    // Upgrade-in-progress state
    public boolean upgradeInProgress; // true while upgrade construction is active
    public long upgradeCompleteAt; // epoch millis when upgrade finishes (0 = not upgrading)

    // Market sell state
    public boolean marketListing; // true when apartment is listed on the market
    public double marketPrice; // price set by owner for market sale
    public long marketListedAt; // epoch millis when listed on market

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

        // Upgrade-in-progress defaults
        this.upgradeInProgress = false;
        this.upgradeCompleteAt = 0L;

        // Market sell defaults
        this.marketListing = false;
        this.marketPrice = 0.0;
        this.marketListedAt = 0L;
    }

    /**
     * Set a custom teleport location.
     * 
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
     * 
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
     * Get hourly income based on level with shop buffs applied
     */
    public double getHourlyIncome(ConfigManager configManager) {
        LevelConfig config = configManager.getLevelConfig(level);
        if (config == null) {
            return 10; // Default fallback
        }

        // Base income calculation
        double baseIncome = config.minIncome + Math.random() * (config.maxIncome - config.minIncome);

        // Apply shop buffs if available
        return applyShopBuffsToIncome(baseIncome, configManager);
    }

    /**
     * Apply shop buffs to base income amount
     */
    private double applyShopBuffsToIncome(double baseIncome, ConfigManager configManager) {
        // We need the plugin instance to get shop manager, but it's not available here
        // This will be handled in the TaskManager where the plugin instance is
        // available
        return baseIncome;
    }

    /**
     * Get hourly income with shop buffs and research buffs applied (version with
     * plugin access)
     */
    public double getHourlyIncomeWithShopBuffs(ConfigManager configManager, ApartmentCore plugin) {
        LevelConfig config = configManager.getLevelConfig(level);
        if (config == null) {
            return 10; // Default fallback
        }

        // Base income calculation
        double baseIncome = config.minIncome + Math.random() * (config.maxIncome - config.minIncome);

        // Apply shop buffs
        if (plugin != null && plugin.getShopManager() != null) {
            var shopManager = plugin.getShopManager();

            // Add flat base income bonus
            double baseIncomeBonus = shopManager.getBaseIncomeBonus(id);
            baseIncome += baseIncomeBonus;

            // Apply percentage income bonus
            double incomeBonus = shopManager.getIncomeBonusPercentage(id);
            if (incomeBonus > 0) {
                baseIncome *= (1.0 + incomeBonus / 100.0);
            }
        }

        // Apply research buffs (player-level permanent bonuses)
        if (owner != null && plugin != null && plugin.getResearchManager() != null) {
            var rm = plugin.getResearchManager();

            // Capital Growth Strategy: +5% income per tier
            double capitalGrowthBonus = rm.getIncomeAmountBonus(owner);
            if (capitalGrowthBonus > 0) {
                baseIncome *= (1.0 + capitalGrowthBonus / 100.0);
            }

            // Revenue Acceleration: -5% generation interval per tier
            // Since the global interval is shared, we compensate by proportionally boosting
            // income.
            // A 5% interval reduction means income arrives 1/(1-0.05) = ~5.26% more
            // frequently,
            // which is equivalent to multiplying income by 1/(1 - reduction/100).
            double intervalReduction = rm.getIncomeIntervalReduction(owner);
            if (intervalReduction > 0 && intervalReduction < 100) {
                baseIncome *= (1.0 / (1.0 - intervalReduction / 100.0));
            }
        }

        return baseIncome;
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
     * Base tax amount with shop buffs and research buffs applied (tax reduction)
     */
    public double computeBaseTaxAmountWithShopBuffs(ApartmentCore plugin) {
        double baseTax = computeBaseTaxAmount();

        // Apply shop tax reduction buff
        if (plugin != null && plugin.getShopManager() != null) {
            double taxReduction = plugin.getShopManager().getTaxReductionPercentage(id);
            if (taxReduction > 0) {
                baseTax *= (1.0 - taxReduction / 100.0);
            }
        }

        // Apply research Tax Efficiency Strategy: -5% final tax per tier
        if (owner != null && plugin != null && plugin.getResearchManager() != null) {
            double researchTaxReduction = plugin.getResearchManager().getTaxReduction(owner);
            if (researchTaxReduction > 0) {
                baseTax *= (1.0 - researchTaxReduction / 100.0);
            }
        }

        return Math.max(0, baseTax); // Ensure tax never goes negative
    }

    /**
     * Compute current total unpaid amount (sum of all unpaid invoices).
     */
    public double getTotalUnpaid() {
        double sum = 0.0;
        if (taxInvoices != null) {
            for (TaxInvoice inv : taxInvoices) {
                if (!inv.isPaid())
                    sum += inv.amount;
            }
        }
        return sum;
    }

    /**
     * Compute current tax status from oldest unpaid invoice.
     */
    public TaxStatus computeTaxStatus(long now) {
        if (owner == null)
            return TaxStatus.ACTIVE;
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
        if (!hasUnpaid)
            return TaxStatus.ACTIVE;

        ApartmentCore plugin_instance = (ApartmentCore) org.bukkit.Bukkit.getPluginManager().getPlugin("ApartmentCore");
        long dayMs = plugin_instance != null && plugin_instance.getConfigManager() != null
                ? plugin_instance.getConfigManager().getTaxGenerationInterval() * 50L
                : 86_400_000L;
        if (dayMs <= 0)
            dayMs = 86_400_000L;

        long days = Math.max(0L, (now - oldestCreatedAt) / dayMs); // config days since oldest unpaid
        if (days >= 7)
            return TaxStatus.REPOSSESSION;
        if (days >= 5)
            return TaxStatus.INACTIVE;
        if (days >= 3)
            return TaxStatus.OVERDUE;
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
    public void tickTaxInvoices(Economy econ, ApartmentCore plugin,
            ConfigManager configManager, ApartmentManager apartmentManager) {
        if (owner == null)
            return;

        long now = System.currentTimeMillis();
        if (lastInvoiceAt == 0L) {
            // Seed with lastTaxPayment for backward compatibility
            lastInvoiceAt = Math.max(0L, lastTaxPayment);
            if (lastInvoiceAt == 0L)
                lastInvoiceAt = now;
        }

        // 1) Generate new invoices for each full day passed since lastInvoiceAt
        final long dayMs = Math.max(1000L, configManager.getTaxGenerationInterval() * 50L);
        while (now - lastInvoiceAt >= dayMs) {
            long newCreatedAt = lastInvoiceAt + dayMs;

            // Determine current status BEFORE creating this invoice (for multiplier)
            TaxStatus statusBefore = computeTaxStatus(now);
            int multiplier = 1;
            if (statusBefore == TaxStatus.OVERDUE)
                multiplier = 2;
            else if (statusBefore == TaxStatus.INACTIVE)
                multiplier = 3;
            // Repossession handled below; if reached, owner will be null and loop breaks
            // next tick.

            // Use shop-buffed tax calculation
            double base = computeBaseTaxAmountWithShopBuffs(plugin);
            double amount = base * multiplier;

            // Due at day 3 since creation
            TaxInvoice invoice = new TaxInvoice(amount, newCreatedAt, newCreatedAt + 3 * dayMs);
            if (taxInvoices == null)
                taxInvoices = new ArrayList<>();
            taxInvoices.add(invoice);
            lastInvoiceAt = newCreatedAt;

            OfflinePlayer player = Bukkit.getOfflinePlayer(owner);
            // Send "new bill" notification once for this invoice
            if (!invoice.notifNewSent) {
                if (player.isOnline()) {
                    String msg = plugin.getMessageManager().getMessage("notifications.bill_new")
                            .replace("%amount%", configManager.formatMoney(amount))
                            .replace("%apartment%", displayName);
                    player.getPlayer().sendMessage(msg);
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
                    // Track tax achievement
                    if (plugin.getAchievementManager() != null) {
                        double totalTax = 0;
                        for (Apartment a : apartmentManager.getApartments().values()) {
                            if (owner.equals(a.owner)) {
                                ApartmentStats s = apartmentManager.getStats(a.id);
                                if (s != null) totalTax += s.totalTaxPaid;
                            }
                        }
                        plugin.getAchievementManager().setProgress(owner,
                                com.aithor.apartmentcore.achievement.AchievementType.TAX_CONTRIBUTOR, totalTax);
                    }
                    if (player.isOnline()) {
                        String paid = plugin.getMessageManager().getMessage("notifications.auto_paid")
                                .replace("%amount%", configManager.formatMoney(invoice.amount))
                                .replace("%apartment%", displayName);
                        player.getPlayer().sendMessage(paid);
                    }
                }
            }
        }

        // 2) Notifications for existing unpaid invoices and status effects
        OfflinePlayer player = Bukkit.getOfflinePlayer(owner);

        // Try auto-paying existing unpaid invoices if enabled and balance allows
        // (oldest first)
        if (autoTaxPayment && player != null) {
            java.util.List<TaxInvoice> unpaid = new java.util.ArrayList<>();
            if (taxInvoices != null) {
                for (TaxInvoice i : taxInvoices) {
                    if (!i.isPaid())
                        unpaid.add(i);
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
                    // Track tax achievement
                    if (plugin.getAchievementManager() != null) {
                        double totalTax = 0;
                        for (Apartment a : apartmentManager.getApartments().values()) {
                            if (owner.equals(a.owner)) {
                                ApartmentStats s = apartmentManager.getStats(a.id);
                                if (s != null) totalTax += s.totalTaxPaid;
                            }
                        }
                        plugin.getAchievementManager().setProgress(owner,
                                com.aithor.apartmentcore.achievement.AchievementType.TAX_CONTRIBUTOR, totalTax);
                    }
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
                if (inv.isPaid())
                    continue;
                long days = inv.daysSinceCreated(now);

                if (days >= 2 && !inv.notifDay2Sent) {
                    if (player.isOnline()) {
                        String msg = plugin.getMessageManager().getMessage("notifications.bill_reminder_day2")
                                .replace("%amount%", configManager.formatMoney(inv.amount))
                                .replace("%apartment%", displayName);
                        player.getPlayer().sendMessage(msg);
                    }
                    inv.notifDay2Sent = true;
                }
                if (days >= 3 && !inv.notifDay3Sent) {
                    if (player.isOnline()) {
                        String msg = plugin.getMessageManager().getMessage("notifications.bill_overdue_day3")
                                .replace("%total%", configManager.formatMoney(getTotalUnpaid()))
                                .replace("%apartment%", displayName);
                        player.getPlayer().sendMessage(msg);
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
                        String msg = plugin.getMessageManager().getMessage("notifications.apartment_inactive_day5")
                                .replace("%total%", configManager.formatMoney(getTotalUnpaid()))
                                .replace("%apartment%", displayName);
                        player.getPlayer().sendMessage(msg);
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
                    String msg = plugin.getMessageManager().getMessage("notifications.apartment_repossessed_day7")
                            .replace("%apartment%", displayName);
                    player.getPlayer().sendMessage(msg);
                }

                // Remove ownership permanently
                java.util.UUID prevOwner = owner;
                // Remove owner from region (even if offline)
                try {
                    apartmentManager.removeOwnerUuidFromRegion(this, prevOwner);
                } catch (Throwable ignored) {
                }
                owner = null;
                inactive = false;
                penalty = 0;
                pendingIncome = 0;
                inactiveSince = 0;
                lastTaxCheckDay = 0;
                lastInvoiceAt = 0;
                taxInvoices.clear();
                marketListing = false;
                marketPrice = 0;
                marketListedAt = 0;
                setCustomTeleportLocation(null); // clear custom teleport

                // Reset ratings and stats
                apartmentManager.getApartmentRatings().remove(id);
                apartmentManager.removeStats(id);

                plugin.debug("Apartment " + id + " repossessed due to unpaid taxes.");
                break;
        }
    }
}
