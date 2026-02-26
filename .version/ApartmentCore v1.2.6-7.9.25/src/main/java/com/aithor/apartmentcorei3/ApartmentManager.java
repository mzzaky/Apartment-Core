package com.aithor.apartmentcorei3;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ApartmentManager {
    private final ApartmentCorei3 plugin;
    private final Economy economy;
    private final ConfigManager configManager;
    private final DataManager dataManager;
    private final WorldGuardPlugin worldGuard;

    private Map<String, Apartment> apartments;
    private Map<String, ApartmentRating> apartmentRatings;
    private Map<UUID, Map<String, Long>> playerRatingCooldowns;
    private Map<String, List<GuestBookEntry>> guestBooks;
    private Map<String, ApartmentStats> apartmentStats;


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

                Apartment apt = new Apartment(
                        id,
                        aptSection.getString("region"),
                        aptSection.getString("world"),
                        aptSection.getString("owner") != null ? UUID.fromString(aptSection.getString("owner")) : null,
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
                    apt.teleportWorld = tpSection.getString("world");
                    apt.teleportX = tpSection.getDouble("x");
                    apt.teleportY = tpSection.getDouble("y");
                    apt.teleportZ = tpSection.getDouble("z");
                    apt.teleportYaw = (float) tpSection.getDouble("yaw");
                    apt.teleportPitch = (float) tpSection.getDouble("pitch");
                    apt.hasCustomTeleport = true;
                }


                apartments.put(id, apt);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load apartment " + id + ": " + e.getMessage());
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
                    rating.raters.put(UUID.fromString(uuid), ratersSection.getDouble(uuid));
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
                    plugin.getLogger().warning("Failed to load a guestbook entry for " + apartmentId + ": " + e.getMessage());
                }
            }
            guestBooks.put(apartmentId, entries);
        }
        plugin.debug("Loaded " + guestBooks.size() + " guestbooks.");
    }

    /**
     * Load apartment stats from storage
     */
    public void loadStats() {
        ConfigurationSection section = dataManager.getStatsConfig().getConfigurationSection("stats");
        if (section == null) return;

        for (String apartmentId : section.getKeys(false)) {
            try {
                ConfigurationSection statsSection = section.getConfigurationSection(apartmentId);
                if (statsSection != null) {
                    ApartmentStats stats = ApartmentStats.deserialize(statsSection.getValues(false));
                    apartmentStats.put(apartmentId, stats);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load stats for apartment " + apartmentId + ": " + e.getMessage());
            }
        }
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
     * Generate income for all apartments
     */
    public void generateIncome() {
        for (Apartment apt : apartments.values()) {
            if (apt.owner != null && !apt.inactive) {
                double income = apt.getHourlyIncome(configManager);
                apt.pendingIncome += income;
                plugin.debug("Generated " + configManager.formatMoney(income) + " income for apartment " + apt.id);
            }
        }
    }

    /**
     * Process daily updates for all apartments (e.g., taxes, age)
     */
    public void processDailyUpdates() {
        for (Apartment apt : apartments.values()) {
            if (apt.owner != null) {
                // Process taxes
                apt.processDailyTax(economy, plugin, configManager, this);

                // Increment age
                ApartmentStats stats = getStats(apt.id);
                stats.ownershipAgeDays++;
            }
        }
        saveApartments();
        saveStats(); // Save stats after daily processing
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

        if (!isAdmin && (apt.owner == null || !apt.owner.equals(player.getUniqueId()))) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
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
            displayList = apartments.values().stream()
                    .filter(a -> a.owner != null && !a.inactive)
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
}