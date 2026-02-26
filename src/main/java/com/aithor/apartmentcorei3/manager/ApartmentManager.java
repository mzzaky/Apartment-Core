package com.aithor.apartmentcorei3.manager;

import com.aithor.apartmentcorei3.ApartmentCorei3;
import com.aithor.apartmentcorei3.model.Apartment;
import com.aithor.apartmentcorei3.model.ApartmentRating;
import com.aithor.apartmentcorei3.model.ApartmentStats;
import com.aithor.apartmentcorei3.model.GuestBookEntry;
import com.aithor.apartmentcorei3.model.TaxInvoice;
import com.aithor.apartmentcorei3.model.TaxStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import net.milkbowl.vault.economy.Economy;

public class ApartmentManager {
    private final ApartmentCorei3 plugin;
    private final Economy economy;
    private final ConfigManager configManager;
    private final DataManager dataManager;
    private final WorldGuardPlugin worldGuard;

    private final Map<String, Apartment> apartments;
    private final Map<String, ApartmentRating> apartmentRatings;
    private final Map<UUID, Map<String, Long>> playerRatingCooldowns;
    private final Map<String, List<GuestBookEntry>> guestBooks;
    private final Map<String, ApartmentStats> apartmentStats;

    // Cached lists for performance
    private final java.util.concurrent.ConcurrentHashMap<String, CachedList> listCache;

    private static class CachedList {
        final java.util.List<Apartment> list;
        final long timestamp;
        CachedList(java.util.List<Apartment> list, long timestamp) {
            this.list = list;
            this.timestamp = timestamp;
        }
    }


    public ApartmentManager(ApartmentCorei3 plugin, Economy economy, ConfigManager configManager, DataManager dataManager) {
        this.plugin = plugin;
        this.economy = economy;
        this.configManager = configManager;
        this.dataManager = dataManager;
        this.worldGuard = (WorldGuardPlugin) plugin.getServer().getPluginManager().getPlugin("WorldGuard");

        this.apartments = new ConcurrentHashMap<>();
        this.apartmentRatings = new ConcurrentHashMap<>();
        this.playerRatingCooldowns = new ConcurrentHashMap<>();
        this.guestBooks = new ConcurrentHashMap<>();
        this.apartmentStats = new ConcurrentHashMap<>();
        this.listCache = new ConcurrentHashMap<>();

        if (worldGuard == null) {
            plugin.getLogger().severe("WorldGuard not found! Disabling plugin...");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    /**
     * Load all apartments from storage
     */
    public void loadApartments() {
        ConfigurationSection section = dataManager.getDataConfig().getConfigurationSection("apartments");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            try {
                ConfigurationSection aptSection = section.getConfigurationSection(id);
                if (aptSection == null) continue;

                String ownerStr = aptSection.getString("owner");
                UUID owner = (ownerStr != null && !ownerStr.isEmpty()) ? UUID.fromString(ownerStr) : null;

                Apartment apt = new Apartment(
                        id,
                        aptSection.getString("region"),
                        aptSection.getString("world"),
                        owner,
                        aptSection.getDouble("price"),
                        aptSection.getDouble("tax"),
                        aptSection.getInt("tax-days"),
                        aptSection.getInt("level", 1),
                        aptSection.getLong("last-tax-payment", System.currentTimeMillis()),
                        aptSection.getDouble("pending-income", 0),
                        aptSection.getBoolean("inactive", false),
                        aptSection.getDouble("penalty", 0),
                        aptSection.getLong("inactive-since", 0),
                        aptSection.getString("display-name", id),
                        aptSection.getString("welcome-message", "")
                );

                // Load custom teleport location
                if (aptSection.isConfigurationSection("teleport-location")) {
                    ConfigurationSection tpSection = aptSection.getConfigurationSection("teleport-location");
                    if (tpSection != null) {
                        apt.teleportWorld = tpSection.getString("world");
                        apt.teleportX = tpSection.getDouble("x");
                        apt.teleportY = tpSection.getDouble("y");
                        apt.teleportZ = tpSection.getDouble("z");
                        apt.teleportYaw = (float) tpSection.getDouble("yaw");
                        apt.teleportPitch = (float) tpSection.getDouble("pitch");
                        apt.hasCustomTeleport = true;
                    }
                }

                // Load new tax system data (optional for backward-compatibility)
                try {
                    apt.autoTaxPayment = aptSection.getBoolean("auto-tax-payment", false);
                    apt.lastInvoiceAt = aptSection.getLong("last-invoice-at", 0L);
                    apt.taxInvoices = new ArrayList<>();
                    if (aptSection.isList("tax-invoices")) {
                        java.util.List<java.util.Map<?, ?>> invList = aptSection.getMapList("tax-invoices");
                        for (java.util.Map<?, ?> raw : invList) {
                            try {
                                java.util.Map<String, Object> map = new java.util.HashMap<>();
                                for (java.util.Map.Entry<?, ?> e : raw.entrySet()) {
                                    if (e.getKey() != null) map.put(String.valueOf(e.getKey()), e.getValue());
                                }
                                apt.taxInvoices.add(TaxInvoice.deserialize(map));
                            } catch (Exception ex) {
                                plugin.getLogger().warning(String.format("Failed to load invoice for %s: %s", id, ex.getMessage()));
                            }
                        }
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning(String.format("Failed reading tax invoices for %s: %s", id, t.getMessage()));
                }


                apartments.put(id, apt);
            } catch (Exception e) {
                plugin.getLogger().warning(String.format("Failed to load apartment %s: %s", id, e.getMessage()));
            }
        }

        plugin.debug("Loaded " + apartments.size() + " apartments from storage");
    }

    /**
     * Load apartment ratings
     */
    public void loadRatings() {
        ConfigurationSection section = dataManager.getDataConfig().getConfigurationSection("ratings");
        if (section == null) return;

        for (String apartmentId : section.getKeys(false)) {
            ConfigurationSection ratingSection = section.getConfigurationSection(apartmentId);
            if (ratingSection == null) continue;

            ApartmentRating rating = new ApartmentRating();
            rating.totalRating = ratingSection.getDouble("total", 0);
            rating.ratingCount = ratingSection.getInt("count", 0);

            ConfigurationSection ratersSection = ratingSection.getConfigurationSection("raters");
            if (ratersSection != null) {
                for (String uuid : ratersSection.getKeys(false)) {
                    try {
                        rating.raters.put(UUID.fromString(uuid), ratersSection.getDouble(uuid));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning(String.format("Invalid UUID in ratings for apartment %s: %s", apartmentId, uuid));
                    }
                }
            }

            apartmentRatings.put(apartmentId, rating);
        }
    }
    
    /**
     * Load guest books from storage
     */
    public void loadGuestBooks() {
        ConfigurationSection section = dataManager.getGuestBookConfig().getConfigurationSection("guestbooks");
        if (section == null) return;

        for (String apartmentId : section.getKeys(false)) {
            List<Map<?, ?>> messagesData = section.getMapList(apartmentId);
            List<GuestBookEntry> entries = new ArrayList<>();

            for (Map<?, ?> msgData : messagesData) {
                try {
                    UUID senderUuid = UUID.fromString((String) msgData.get("uuid"));
                    String senderName = (String) msgData.get("name");
                    String message = (String) msgData.get("message");
                    long timestamp = (long) msgData.get("timestamp");
                    entries.add(new GuestBookEntry(senderUuid, senderName, message, timestamp));
                } catch (Exception e) {
                    plugin.getLogger().warning(String.format("Failed to load a guestbook entry for %s: %s", apartmentId, e.getMessage()));
                }
            }
            guestBooks.put(apartmentId, entries);
        }
        plugin.debug("Loaded " + guestBooks.size() + " guestbooks.");
    }

    /**
     * Load apartment stats from storage
     * Also ensures every existing apartment has a stats entry so the file is not empty.
     */
    public void loadStats() {
        ConfigurationSection section = dataManager.getStatsConfig().getConfigurationSection("stats");
        apartmentStats.clear();
 
        if (section != null) {
            for (String apartmentId : section.getKeys(false)) {
                try {
                    ConfigurationSection statsSection = section.getConfigurationSection(apartmentId);
                    if (statsSection != null) {
                        ApartmentStats stats = ApartmentStats.deserialize(statsSection.getValues(false));
                        apartmentStats.put(apartmentId, stats);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning(String.format("Failed to load stats for apartment %s: %s", apartmentId, e.getMessage()));
                }
            }
        }
 
        // Ensure every apartment has a stats object so apartments-stats.yml is populated
        for (String apartmentId : apartments.keySet()) {
            apartmentStats.computeIfAbsent(apartmentId, k -> new ApartmentStats());
        }
 
        // Persist to disk to guarantee file content exists at startup
        saveStats();
        plugin.debug("Loaded " + apartmentStats.size() + " apartment stats entries.");
    }

    /**
     * Save all apartments to storage
     */
    public void saveApartments() {
        if (dataManager.getDataConfig() == null || apartments == null) {
            plugin.debug("Cannot save apartments - data not initialized");
            return;
        }

        dataManager.getDataConfig().set("apartments", null);
        dataManager.getDataConfig().set("last-minecraft-day", plugin.getLastMinecraftDay());
        dataManager.getDataConfig().set("last-rent-claim-time", plugin.getLastRentClaimTime());

        for (Apartment apt : apartments.values()) {
            String path = "apartments." + apt.id + ".";
            dataManager.getDataConfig().set(path + "region", apt.regionName);
            dataManager.getDataConfig().set(path + "world", apt.worldName);
            dataManager.getDataConfig().set(path + "owner", apt.owner != null ? apt.owner.toString() : null);
            dataManager.getDataConfig().set(path + "price", apt.price);
            dataManager.getDataConfig().set(path + "tax", apt.tax);
            dataManager.getDataConfig().set(path + "tax-days", apt.taxDays);
            dataManager.getDataConfig().set(path + "level", apt.level);
            dataManager.getDataConfig().set(path + "last-tax-payment", apt.lastTaxPayment);
            dataManager.getDataConfig().set(path + "pending-income", apt.pendingIncome);
            dataManager.getDataConfig().set(path + "inactive", apt.inactive);
            dataManager.getDataConfig().set(path + "penalty", apt.penalty);
            dataManager.getDataConfig().set(path + "inactive-since", apt.inactiveSince);
            dataManager.getDataConfig().set(path + "display-name", apt.displayName);
            dataManager.getDataConfig().set(path + "welcome-message", apt.welcomeMessage);

            // Save new tax system data
            dataManager.getDataConfig().set(path + "auto-tax-payment", apt.autoTaxPayment);
            dataManager.getDataConfig().set(path + "last-invoice-at", apt.lastInvoiceAt);
            java.util.List<java.util.Map<String, Object>> invoices = new java.util.ArrayList<>();
            if (apt.taxInvoices != null) {
                for (TaxInvoice inv : apt.taxInvoices) {
                    invoices.add(inv.serialize());
                }
            }
            dataManager.getDataConfig().set(path + "tax-invoices", invoices);

            // Save custom teleport location
            if (apt.hasCustomTeleport) {
                String tpPath = path + "teleport-location.";
                dataManager.getDataConfig().set(tpPath + "world", apt.teleportWorld);
                dataManager.getDataConfig().set(tpPath + "x", apt.teleportX);
                dataManager.getDataConfig().set(tpPath + "y", apt.teleportY);
                dataManager.getDataConfig().set(tpPath + "z", apt.teleportZ);
                dataManager.getDataConfig().set(tpPath + "yaw", apt.teleportYaw);
                dataManager.getDataConfig().set(tpPath + "pitch", apt.teleportPitch);
            } else {
                dataManager.getDataConfig().set(path + "teleport-location", null);
            }
        }

        dataManager.saveDataFile();
        plugin.debug("Saved " + apartments.size() + " apartments to storage");
    }

    /**
     * Save apartment ratings
     */
    public void saveRatings() {
        if (dataManager.getDataConfig() == null) return;

        dataManager.getDataConfig().set("ratings", null);

        for (Map.Entry<String, ApartmentRating> entry : apartmentRatings.entrySet()) {
            String path = "ratings." + entry.getKey() + ".";
            ApartmentRating rating = entry.getValue();

            dataManager.getDataConfig().set(path + "total", rating.totalRating);
            dataManager.getDataConfig().set(path + "count", rating.ratingCount);

            for (Map.Entry<UUID, Double> rater : rating.raters.entrySet()) {
                dataManager.getDataConfig().set(path + "raters." + rater.getKey().toString(), rater.getValue());
            }
        }

        dataManager.saveDataFile();
    }
    
    /**
     * Save all guest books to storage.
     */
    public void saveGuestBooks() {
        if (dataManager.getGuestBookConfig() == null) return;

        dataManager.getGuestBookConfig().set("guestbooks", null); // Clear old data

        for (Map.Entry<String, List<GuestBookEntry>> entry : guestBooks.entrySet()) {
            String apartmentId = entry.getKey();
            List<GuestBookEntry> messages = entry.getValue();
            List<Map<String, Object>> messagesData = new ArrayList<>();

            for (GuestBookEntry msg : messages) {
                Map<String, Object> msgData = new HashMap<>();
                msgData.put("uuid", msg.senderUuid.toString());
                msgData.put("name", msg.senderName);
                msgData.put("message", msg.message);
                msgData.put("timestamp", msg.timestamp);
                messagesData.add(msgData);
            }
            dataManager.getGuestBookConfig().set("guestbooks." + apartmentId, messagesData);
        }
        dataManager.saveGuestBookFile();
        plugin.debug("Saved " + guestBooks.size() + " guestbooks.");
    }

    /**
     * Save all apartment stats to storage.
     */
    public void saveStats() {
        if (dataManager.getStatsConfig() == null) return;

        dataManager.getStatsConfig().set("stats", null); // Clear old data

        for (Map.Entry<String, ApartmentStats> entry : apartmentStats.entrySet()) {
            dataManager.getStatsConfig().set("stats." + entry.getKey(), entry.getValue().serialize());
        }
        dataManager.saveStatsFile();
        plugin.debug("Saved " + apartmentStats.size() + " apartment stats entries.");
    }


    /**
     * Generate income for all apartments with shop buffs applied
     */
    public void generateIncome() {
        long now = System.currentTimeMillis();
        for (Apartment apt : apartments.values()) {
            if (apt.owner != null && apt.canGenerateIncome(now)) {
                // Calculate base income for buff comparison
                double baseIncome = apt.getHourlyIncome(configManager);
                // Use shop-buffed income calculation
                double income = apt.getHourlyIncomeWithShopBuffs(configManager, plugin);
                apt.pendingIncome += income;

                // Update stats
                ApartmentStats stats = getStats(apt.id);
                stats.totalIncomeGenerated += income;

                // Send notification to player if online
                org.bukkit.OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(apt.owner);
                if (offlinePlayer.isOnline()) {
                    org.bukkit.entity.Player player = offlinePlayer.getPlayer();
                    if (player != null) {
                        String message;
                        // Check if apartment actually has active income buffs
                        boolean hasIncomeBuffs = plugin.getShopManager().hasActiveIncomeBuffs(apt.id);

                        if (hasIncomeBuffs) {
                            // Get actual shop buff amounts instead of random difference
                            double flatBuff = plugin.getShopManager().getTotalFlatIncomeBonus(apt.id);
                            double percentageBuff = plugin.getShopManager().getTotalPercentageIncomeBonus(apt.id);

                            message = plugin.getMessageManager().getMessage("notifications.rent_generated_with_buff")
                                    .replace("%amount%", configManager.formatMoney(income))
                                    .replace("%apartment%", apt.displayName)
                                    .replace("%flat_buff%", configManager.formatMoney(flatBuff))
                                    .replace("%percentage_buff%", String.format("%.1f", percentageBuff));
                        } else { // No shop buff
                            message = plugin.getMessageManager().getMessage("notifications.rent_generated")
                                    .replace("%amount%", configManager.formatMoney(income))
                                    .replace("%apartment%", apt.displayName);
                        }
                        player.sendMessage(message);
                    }
                }

                plugin.debug("Generated " + configManager.formatMoney(income) + " income for apartment " + apt.id +
                    " (with shop buffs applied)");
            }
        }
    }

    /**
     * Process daily updates for all apartments (e.g., taxes, age)
     */
    public void processDailyUpdates() {
        for (Apartment apt : apartments.values()) {
            if (apt.owner != null) {
                // Process taxes (new invoice-based system; safe to call daily as it is time-driven)
                apt.tickTaxInvoices(economy, plugin, configManager, this);

                // Increment age
                ApartmentStats stats = getStats(apt.id);
                stats.ownershipAgeDays++;
            }
        }
        // The main auto-save task is already running asynchronously. Calling a synchronous save here could cause lag. Data will be saved on the next auto-save cycle.
        // saveApartments();
        // saveStats();
    }

    /**
     * Teleport player to apartment
     */
    public boolean teleportToApartment(Player player, String apartmentId, boolean isAdmin) {
        Apartment apt = apartments.get(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return false;
        }

        if (!isAdmin && !configManager.isFeatureTeleportation()) {
            player.sendMessage(ChatColor.RED + "Teleportation is disabled.");
            return false;
        }

        if (!isAdmin && (apt.owner == null || !apt.owner.equals(player.getUniqueId()))) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return false;
        }

        // Block usage if apartment is inactive (cannot be used at all)
        if (!isAdmin && apt.inactive) {
            player.sendMessage(ChatColor.RED + "Apartment Inactive. All functions are frozen until the tax is paid.");
            return false;
        }
        
        // Prioritize custom teleport location
        Location customLoc = apt.getCustomTeleportLocation();
        if (customLoc != null) {
            player.teleport(customLoc);
            player.sendMessage(ChatColor.GREEN + "Teleported to " + apt.displayName);
            if (!apt.welcomeMessage.isEmpty()) {
                player.sendMessage(ChatColor.AQUA + apt.welcomeMessage);
            }
            return true;
        }

        // Fallback to region center
        World world = Bukkit.getWorld(apt.worldName);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "World not found!");
            return false;
        }

        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (regionManager != null) {
            ProtectedRegion region = regionManager.getRegion(apt.regionName);
            if (region != null) {
                BlockVector3 min = region.getMinimumPoint();
                BlockVector3 max = region.getMaximumPoint();

                double x = (min.getX() + max.getX() + 1) / 2.0;
                double z = (min.getZ() + max.getZ() + 1) / 2.0;

                Location loc = new Location(world, x, min.getY(), z);
                
                // Try to find a safe spot
                loc = findSafeLocation(loc);

                player.teleport(loc);
                player.sendMessage(ChatColor.GREEN + "Teleported to " + apt.displayName);

                // Show welcome message
                if (!apt.welcomeMessage.isEmpty()) {
                    player.sendMessage(ChatColor.AQUA + apt.welcomeMessage);
                }
                return true;
            } else {
                player.sendMessage(ChatColor.RED + "Region not found!");
            }
        }
        return false;
    }
    
    private Location findSafeLocation(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return loc.add(0.5, 0, 0.5);
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        // Start from the given Y and go up to find air blocks
        for (int i = y; i < world.getMaxHeight(); i++) {
            Location testLoc = new Location(world, x, i, z);
            Material blockType = testLoc.getBlock().getType();
            Material blockBelowType = testLoc.clone().subtract(0, 1, 0).getBlock().getType();
            Material blockAboveType = testLoc.clone().add(0, 1, 0).getBlock().getType();

            if (blockType.isAir() && blockAboveType.isAir() && blockBelowType.isSolid()) {
                return testLoc.add(0.5, 0, 0.5); // Center the location on the block
            }
        }
        // If no safe spot is found above, return the original (might be unsafe)
        return loc.add(0.5, 0, 0.5);
    }

    /**
     * Add player to WorldGuard region
     */
    public void addPlayerToRegion(Player player, Apartment apt) {
        if (!configManager.isWgAutoAddOwner()) return;
        World world = Bukkit.getWorld(apt.worldName);
        if (world != null) {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(world));
            if (regionManager != null) {
                ProtectedRegion region = regionManager.getRegion(apt.regionName);
                if (region != null) {
                    region.getOwners().addPlayer(player.getUniqueId());
                }
            }
        }
    }

    /**
     * Remove player from WorldGuard region
     */
    public void removePlayerFromRegion(Player player, Apartment apt) {
        if (!configManager.isWgAutoRemoveOwner()) return;
        World world = Bukkit.getWorld(apt.worldName);
        if (world != null) {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(world));
            if (regionManager != null) {
                ProtectedRegion region = regionManager.getRegion(apt.regionName);
                if (region != null) {
                    region.getOwners().removePlayer(player.getUniqueId());
                }
            }
        }
    }

    /**
     * Remove owner UUID from WorldGuard region (works even if player is offline)
     */
    public void removeOwnerUuidFromRegion(Apartment apt, java.util.UUID ownerUuid) {
        if (ownerUuid == null) return;
        if (!configManager.isWgAutoRemoveOwner()) return;
        World world = Bukkit.getWorld(apt.worldName);
        if (world != null) {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(world));
            if (regionManager != null) {
                ProtectedRegion region = regionManager.getRegion(apt.regionName);
                if (region != null) {
                    region.getOwners().removePlayer(ownerUuid);
                }
            }
        }
    }

    /**
     * Add owner UUID to WorldGuard region (works even if player is offline)
     */
    public void addOwnerUuidToRegion(Apartment apt, java.util.UUID ownerUuid) {
        if (ownerUuid == null) return;
        if (!configManager.isWgAutoAddOwner()) return;
        World world = Bukkit.getWorld(apt.worldName);
        if (world != null) {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(world));
            if (regionManager != null) {
                ProtectedRegion region = regionManager.getRegion(apt.regionName);
                if (region != null) {
                    region.getOwners().addPlayer(ownerUuid);
                }
            }
        }
    }

    /**
     * Get list of apartments with filter
     */
    public List<Apartment> getApartmentList(String filter, UUID playerUuid) {
        List<Apartment> displayList;

        if (filter == null || filter.equals("all")) {
            displayList = new ArrayList<>(apartments.values());
        } else if (filter.equals("sale")) {
            displayList = apartments.values().stream()
                    .filter(a -> a.owner == null)
                    .collect(Collectors.toList());
        } else if (filter.equals("mine") && playerUuid != null) {
            displayList = apartments.values().stream()
                    .filter(a -> playerUuid.equals(a.owner))
                    .collect(Collectors.toList());
        } else if (filter.equals("top")) {
            final long now = System.currentTimeMillis();
            displayList = apartments.values().stream()
                    .filter(a -> a.owner != null && a.canGenerateIncome(now))
                    .sorted((a1, a2) -> {
                        ApartmentRating r1 = apartmentRatings.get(a1.id);
                        ApartmentRating r2 = apartmentRatings.get(a2.id);
                        double rating1 = r1 != null ? r1.getAverageRating() : 0;
                        double rating2 = r2 != null ? r2.getAverageRating() : 0;
                        return Double.compare(rating2, rating1);
                    })
                    .limit(10)
                    .collect(Collectors.toList());
        } else {
            displayList = new ArrayList<>();
        }

        return displayList;
    }

    // Getters
    public Map<String, Apartment> getApartments() {
        return apartments;
    }

    public Map<String, ApartmentRating> getApartmentRatings() {
        return apartmentRatings;
    }

    public Map<UUID, Map<String, Long>> getPlayerRatingCooldowns() {
        return playerRatingCooldowns;
    }
    
    public Map<String, List<GuestBookEntry>> getGuestBooks() {
        return guestBooks;
    }
    
    public Map<String, ApartmentStats> getApartmentStats() {
        return apartmentStats;
    }

    public Apartment getApartment(String id) {
        return apartments.get(id);
    }

    public ApartmentRating getRating(String apartmentId) {
        return apartmentRatings.get(apartmentId);
    }
    
    public ApartmentStats getStats(String apartmentId) {
        return apartmentStats.computeIfAbsent(apartmentId, k -> new ApartmentStats());
    }

    public void removeStats(String apartmentId) {
        apartmentStats.remove(apartmentId);
    }



    public int getApartmentCount() {
        return apartments.size();
    }

    public WorldGuardPlugin getWorldGuard() {
        return worldGuard;
    }

    // Public API for other plugins
    public TaxStatus getTaxStatus(String apartmentId) {
        Apartment a = apartments.get(apartmentId);
        if (a == null) return TaxStatus.ACTIVE;
        return a.computeTaxStatus(System.currentTimeMillis());
    }

    public double getTotalUnpaid(String apartmentId) {
        Apartment a = apartments.get(apartmentId);
        if (a == null) return 0.0;
        return a.getTotalUnpaid();
    }

    public boolean canUseApartment(String apartmentId) {
        Apartment a = apartments.get(apartmentId);
        if (a == null) return false;
        return a.canGenerateIncome(System.currentTimeMillis());
    }
    
    /**
     * Validate WorldGuard flags on a region against config worldguard.required-flags.
     * Supported forms: "pvp: deny", "build: deny", boolean flags ("someflag: true/false").
     * Returns true when all requirements are satisfied or when checking is disabled.
     */
    public boolean checkRegionRequiredFlags(String worldName, String regionName) {
        if (!configManager.isWgCheckFlags()) return true;
        try {
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("WG check failed: world not found for " + worldName);
                return false;
            }
            com.sk89q.worldguard.protection.managers.RegionManager regionManager =
                    com.sk89q.worldguard.WorldGuard.getInstance().getPlatform()
                            .getRegionContainer().get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
            if (regionManager == null) {
                plugin.getLogger().warning("WG check failed: RegionManager is null for " + worldName);
                return false;
            }
            com.sk89q.worldguard.protection.regions.ProtectedRegion region = regionManager.getRegion(regionName);
            if (region == null) {
                plugin.getLogger().warning("WG check failed: region '" + regionName + "' not found in " + worldName);
                return false;
            }
            java.util.List<String> req = configManager.getWgRequiredFlags();
            if (req == null || req.isEmpty()) return true;

            com.sk89q.worldguard.protection.flags.registry.FlagRegistry reg =
                    com.sk89q.worldguard.WorldGuard.getInstance().getFlagRegistry();

            for (String entry : req) {
                if (entry == null || entry.trim().isEmpty()) continue;
                String[] parts = entry.split(":");
                String flagName = parts[0].trim();
                String expectedRaw = parts.length > 1 ? parts[1].trim() : "";
                com.sk89q.worldguard.protection.flags.Flag<?> flag = reg.get(flagName);
                if (flag == null) {
                    plugin.getLogger().warning("WG check: unknown flag '" + flagName + "'. Skipping strict check.");
                    continue; // don't hard fail on unknown flags
                }

                if (flag instanceof com.sk89q.worldguard.protection.flags.StateFlag) {
                    com.sk89q.worldguard.protection.flags.StateFlag sf =
                            (com.sk89q.worldguard.protection.flags.StateFlag) flag;
                    com.sk89q.worldguard.protection.flags.StateFlag.State expected =
                            "allow".equalsIgnoreCase(expectedRaw)
                                    ? com.sk89q.worldguard.protection.flags.StateFlag.State.ALLOW
                                    : com.sk89q.worldguard.protection.flags.StateFlag.State.DENY;
                    com.sk89q.worldguard.protection.flags.StateFlag.State actual = region.getFlag(sf);
                    if (actual == null || actual != expected) {
                        plugin.getLogger().warning("WG check failed: flag '" + flagName + "' expected '" + expectedRaw + "' but was '" + (actual == null ? "null" : actual.toString().toLowerCase()) + "'");
                        return false;
                    }
                } else if (flag instanceof com.sk89q.worldguard.protection.flags.BooleanFlag) {
                    com.sk89q.worldguard.protection.flags.BooleanFlag bf =
                            (com.sk89q.worldguard.protection.flags.BooleanFlag) flag;
                    boolean expected = Boolean.parseBoolean(expectedRaw);
                    Boolean actual = region.getFlag(bf);
                    if (actual == null || actual != expected) {
                        plugin.getLogger().warning("WG check failed: flag '" + flagName + "' expected '" + expected + "' but was '" + actual + "'");
                        return false;
                    }
                } else {
                    // Fallback string compare on map if we cannot strongly type the flag
                    String mapStr = String.valueOf(region.getFlags());
                    if (!mapStr.toLowerCase().contains((flagName + "=" + expectedRaw).toLowerCase())) {
                        plugin.getLogger().warning("WG check fallback mismatch for '" + flagName + ":" + expectedRaw + "'. Flags: " + mapStr);
                        return false;
                    }
                }
            }
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("WG check error: " + t.getMessage());
            return false;
        }
    }
}
