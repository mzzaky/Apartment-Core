package com.aithor.apartmentcore.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Represents a single tax invoice for an apartment.
 * Invoices are generated every 24 hours real-world time and can stack.
 */
public class TaxInvoice {
    public String id; // UUID string
    public double amount; // Amount to pay
    public long createdAt; // Epoch millis when created
    public long dueAt; // Epoch millis when due (createdAt + 3 days)
    public long paidAt; // Epoch millis when paid (0 = unpaid)

    // Notification flags to prevent spamming
    public boolean notifNewSent;
    public boolean notifDay2Sent; // 1 day before due (day 2)
    public boolean notifDay3Sent; // Overdue (day 3)
    public boolean notifDay5Sent; // Inactive (day 5)

    public TaxInvoice() {
    }

    public TaxInvoice(double amount, long createdAt, long dueAt) {
        this.id = UUID.randomUUID().toString();
        this.amount = amount;
        this.createdAt = createdAt;
        this.dueAt = dueAt;
        this.paidAt = 0L;
        this.notifNewSent = false;
        this.notifDay2Sent = false;
        this.notifDay3Sent = false;
        this.notifDay5Sent = false;
    }

    public boolean isPaid() {
        return paidAt > 0;
    }

    public long ageMillis(long now) {
        return Math.max(0, now - createdAt);
    }

    public long daysSinceCreated(long now) {
        return ageMillis(now) / getDayMs();
    }

    public long daysOverdue(long now) {
        if (now < dueAt)
            return 0;
        return (now - dueAt) / getDayMs();
    }

    private static long getDayMs() {
        com.aithor.apartmentcore.ApartmentCore plugin = (com.aithor.apartmentcore.ApartmentCore) org.bukkit.Bukkit
                .getPluginManager().getPlugin("ApartmentCore");
        long dayMs = plugin != null && plugin.getConfigManager() != null
                ? plugin.getConfigManager().getTaxGenerationInterval() * 50L
                : 86_400_000L;
        return dayMs > 0 ? dayMs : 86_400_000L;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("amount", amount);
        map.put("createdAt", createdAt);
        map.put("dueAt", dueAt);
        map.put("paidAt", paidAt);
        map.put("notifNewSent", notifNewSent);
        map.put("notifDay2Sent", notifDay2Sent);
        map.put("notifDay3Sent", notifDay3Sent);
        map.put("notifDay5Sent", notifDay5Sent);
        return map;
    }

    public static TaxInvoice deserialize(Map<String, Object> data) {
        TaxInvoice inv = new TaxInvoice();
        inv.id = (String) data.getOrDefault("id", UUID.randomUUID().toString());
        inv.amount = ((Number) data.getOrDefault("amount", 0D)).doubleValue();
        inv.createdAt = ((Number) data.getOrDefault("createdAt", 0L)).longValue();
        inv.dueAt = ((Number) data.getOrDefault("dueAt", inv.createdAt + 3 * getDayMs())).longValue();
        inv.paidAt = ((Number) data.getOrDefault("paidAt", 0L)).longValue();
        inv.notifNewSent = (boolean) data.getOrDefault("notifNewSent", false);
        inv.notifDay2Sent = (boolean) data.getOrDefault("notifDay2Sent", false);
        inv.notifDay3Sent = (boolean) data.getOrDefault("notifDay3Sent", false);
        inv.notifDay5Sent = (boolean) data.getOrDefault("notifDay5Sent", false);
        return inv;
    }

    // Helper for reading from a ConfigurationSection (safety with Bukkit's typed
    // maps)
    @SuppressWarnings("unchecked")
    public static TaxInvoice fromSection(ConfigurationSection section) {
        Map<String, Object> map = new HashMap<>();
        for (String key : section.getKeys(false)) {
            map.put(key, section.get(key));
        }
        return deserialize(map);
    }
}