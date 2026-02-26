package com.aithor.apartmentcore.shop;

import com.aithor.apartmentcore.model.Apartment;
import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.manager.ApartmentManager;
import com.aithor.apartmentcore.manager.ConfigManager;
import com.aithor.apartmentcore.manager.DataManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the apartment shop system - purchases, upgrades, and buff
 * applications
 */
public class ApartmentShopManager {
    private final ApartmentCore plugin;
    private final ApartmentManager apartmentManager;
    private final Economy economy;
    private final ConfigManager configManager;
    private final DataManager dataManager;

    // Shop data for each apartment (apartmentId -> shop data)
    private final Map<String, ApartmentShopData> shopData;
    private final File shopDataFile;

    public ApartmentShopManager(ApartmentCore plugin, ApartmentManager apartmentManager,
            Economy economy, ConfigManager configManager, DataManager dataManager) {
        this.plugin = plugin;
        this.apartmentManager = apartmentManager;
        this.economy = economy;
        this.configManager = configManager;
        this.dataManager = dataManager;
        this.shopData = new HashMap<>();
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        this.shopDataFile = new File(dataDir, "shop_data.yml");

        loadShopData();
    }

    /**
     * Get shop data for an apartment, creating if it doesn't exist
     */
    public ApartmentShopData getShopData(String apartmentId) {
        return shopData.computeIfAbsent(apartmentId, id -> new ApartmentShopData(id));
    }

    /**
     * Purchase/upgrade a shop item for an apartment
     */
    public PurchaseResult purchaseUpgrade(Player player, String apartmentId, ShopItem item) {
        // Validate player owns the apartment
        Apartment apartment = apartmentManager.getApartment(apartmentId);
        if (apartment == null) {
            return new PurchaseResult(false, "Apartment not found!");
        }

        if (!player.getUniqueId().equals(apartment.owner)) {
            return new PurchaseResult(false, "You don't own this apartment!");
        }

        // Get shop data for apartment
        ApartmentShopData data = getShopData(apartmentId);

        // Check if item can be upgraded
        if (!data.canUpgrade(item)) {
            return new PurchaseResult(false, "This item is already at maximum tier!");
        }

        // Check cost
        double cost = data.getUpgradeCost(item);
        if (!economy.has(player, cost)) {
            return new PurchaseResult(false, "Insufficient funds! Need " +
                    configManager.formatMoney(cost) + " but you have " +
                    configManager.formatMoney(economy.getBalance(player)));
        }

        // Process purchase
        economy.withdrawPlayer(player, cost);
        boolean success = data.upgradeTier(item);

        if (success) {
            // Log transaction
            plugin.logTransaction(player.getName() + " purchased " + item.getDisplayName() +
                    " tier " + data.getTier(item) + " for apartment " + apartmentId +
                    " for " + configManager.formatMoney(cost));

            // Save shop data
            saveShopData();

            return new PurchaseResult(true, "Successfully purchased " + item.getDisplayName() +
                    " tier " + data.getTier(item) + " for " + configManager.formatMoney(cost));
        } else {
            // Refund on failure
            economy.depositPlayer(player, cost);
            return new PurchaseResult(false, "Failed to upgrade item. Money refunded.");
        }
    }

    /**
     * Handle apartment sale - give 50% refund and reset shop data
     */
    public double handleApartmentSale(String apartmentId, UUID previousOwner) {
        ApartmentShopData data = getShopData(apartmentId);
        double refund = data.getRefundAmount();

        if (refund > 0 && previousOwner != null) {
            // Give refund to previous owner
            org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(previousOwner);
            if (offlinePlayer != null) {
                economy.depositPlayer(offlinePlayer, refund);

                // Notify if online
                if (offlinePlayer.isOnline()) {
                    offlinePlayer.getPlayer().sendMessage(plugin.getMessageManager().getMessage("shop.refund")
                            .replace("%amount%", configManager.formatMoney(refund))
                            .replace("%apartment%", apartmentId));
                }

                plugin.logTransaction("Shop refund of " + configManager.formatMoney(refund) +
                        " given to " + offlinePlayer.getName() + " for apartment " + apartmentId);
            }
        }

        // Reset shop data for the apartment
        data.reset();
        saveShopData();

        return refund;
    }

    /**
     * Get total income bonus percentage from all shop items for an apartment
     */
    public double getIncomeBonusPercentage(String apartmentId) {
        ApartmentShopData data = getShopData(apartmentId);
        return data.getTotalBuffValue(ShopBuffType.INCOME_BONUS);
    }

    /**
     * Get total base income bonus from all shop items for an apartment
     */
    public double getBaseIncomeBonus(String apartmentId) {
        ApartmentShopData data = getShopData(apartmentId);
        return data.getTotalBuffValue(ShopBuffType.BASE_INCOME);
    }

    /**
     * Get total tax reduction percentage from all shop items for an apartment
     */
    public double getTaxReductionPercentage(String apartmentId) {
        ApartmentShopData data = getShopData(apartmentId);
        return data.getTotalBuffValue(ShopBuffType.TAX_REDUCTION);
    }

    /**
     * Get total income speed bonus (tick reduction) from all shop items for an
     * apartment
     */
    public double getIncomeSpeedBonus(String apartmentId) {
        ApartmentShopData data = getShopData(apartmentId);
        return data.getTotalBuffValue(ShopBuffType.INCOME_SPEED);
    }

    /**
     * Get total max messages bonus from all shop items for an apartment
     */
    public int getMaxMessagesBonus(String apartmentId) {
        ApartmentShopData data = getShopData(apartmentId);
        return (int) data.getTotalBuffValue(ShopBuffType.MAX_MESSAGES);
    }

    /**
     * Check if apartment has any active income-related buffs
     */
    public boolean hasActiveIncomeBuffs(String apartmentId) {
        ApartmentShopData data = getShopData(apartmentId);
        return data.hasAnyPurchases() &&
                (data.getTotalBuffValue(ShopBuffType.INCOME_BONUS) > 0 ||
                        data.getTotalBuffValue(ShopBuffType.BASE_INCOME) > 0);
    }

    /**
     * Get the total flat income bonus from shop buffs
     */
    public double getTotalFlatIncomeBonus(String apartmentId) {
        ApartmentShopData data = getShopData(apartmentId);
        return data.getTotalBuffValue(ShopBuffType.BASE_INCOME);
    }

    /**
     * Get the total percentage income bonus from shop buffs
     */
    public double getTotalPercentageIncomeBonus(String apartmentId) {
        ApartmentShopData data = getShopData(apartmentId);
        return data.getTotalBuffValue(ShopBuffType.INCOME_BONUS);
    }

    /**
     * Load shop data from file
     */
    private void loadShopData() {
        if (!shopDataFile.exists()) {
            plugin.debug("Shop data file does not exist, starting fresh.");
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(shopDataFile);

            for (String apartmentId : config.getKeys(false)) {
                ApartmentShopData data = new ApartmentShopData(apartmentId);

                // Load purchased tiers
                if (config.contains(apartmentId + ".tiers")) {
                    for (String itemName : config.getConfigurationSection(apartmentId + ".tiers").getKeys(false)) {
                        try {
                            ShopItem item = ShopItem.valueOf(itemName.toUpperCase());
                            int tier = config.getInt(apartmentId + ".tiers." + itemName, 0);
                            data.setTier(item, tier);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Unknown shop item in data: " + itemName);
                        }
                    }
                }

                // Load total money spent
                double totalSpent = config.getDouble(apartmentId + ".total_spent", 0.0);
                data.setTotalMoneySpent(totalSpent);

                shopData.put(apartmentId, data);
            }

            plugin.debug("Loaded shop data for " + shopData.size() + " apartments.");

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load shop data: " + e.getMessage());
        }
    }

    /**
     * Save shop data to file
     */
    public void saveShopData() {
        try {
            YamlConfiguration config = new YamlConfiguration();

            for (Map.Entry<String, ApartmentShopData> entry : shopData.entrySet()) {
                String apartmentId = entry.getKey();
                ApartmentShopData data = entry.getValue();

                // Save purchased tiers
                for (Map.Entry<ShopItem, Integer> tierEntry : data.getPurchasedTiers().entrySet()) {
                    ShopItem item = tierEntry.getKey();
                    int tier = tierEntry.getValue();
                    if (tier > 0) { // Only save purchased items
                        config.set(apartmentId + ".tiers." + item.name().toLowerCase(), tier);
                    }
                }

                // Save total money spent
                if (data.getTotalMoneySpent() > 0) {
                    config.set(apartmentId + ".total_spent", data.getTotalMoneySpent());
                }
            }

            config.save(shopDataFile);
            plugin.debug("Saved shop data for " + shopData.size() + " apartments.");

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save shop data: " + e.getMessage());
        }
    }

    /**
     * Clear shop data for an apartment (used when apartment is deleted)
     */
    public void clearShopData(String apartmentId) {
        shopData.remove(apartmentId);
        saveShopData();
    }

    /**
     * Get all shop data (for admin/debugging)
     */
    public Map<String, ApartmentShopData> getAllShopData() {
        return new HashMap<>(shopData);
    }

    /**
     * Result class for purchase operations
     */
    public static class PurchaseResult {
        private final boolean success;
        private final String message;

        public PurchaseResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}