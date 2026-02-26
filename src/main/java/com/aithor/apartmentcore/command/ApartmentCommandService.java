package com.aithor.apartmentcore.command;

import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.manager.ApartmentManager;
import com.aithor.apartmentcore.manager.AuctionManager;
import com.aithor.apartmentcore.manager.ConfigManager;
import com.aithor.apartmentcore.manager.DataManager;
import com.aithor.apartmentcore.manager.MessageManager;
import com.aithor.apartmentcore.model.Apartment;
import com.aithor.apartmentcore.model.ApartmentAuction;
import com.aithor.apartmentcore.model.ApartmentRating;
import com.aithor.apartmentcore.model.ApartmentStats;
import com.aithor.apartmentcore.model.ConfirmationAction;
import com.aithor.apartmentcore.model.GuestBookEntry;
import com.aithor.apartmentcore.model.LevelConfig;
import com.aithor.apartmentcore.model.TaxInvoice;
import com.aithor.apartmentcore.model.TaxStatus;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ApartmentCommandService {
    private final ApartmentCore plugin;
    private final ApartmentManager apartmentManager;
    private final Economy economy;
    private final ConfigManager configManager;

    // Guestbook leave cooldowns (moved from CommandHandler)

    // Correct field (actual one used)
    private final Map<UUID, Long> guestBookCooldowns = new HashMap<>();

    public ApartmentCommandService(ApartmentCore plugin,
                                   ApartmentManager apartmentManager,
                                   Economy economy,
                                   ConfigManager configManager) {
        this.plugin = plugin;
        this.apartmentManager = apartmentManager;
        this.economy = economy;
        this.configManager = configManager;
    }

    // ==========================
    // Guestbook-related commands
    // ==========================
    public boolean handleGuestBookCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
            return true;
        }
        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /apt guestbook <leave|read|clear> <apartment_id> ...");
            return true;
        }

        String action = args[0].toLowerCase();
        String apartmentId = args[1];
        Apartment apt = apartmentManager.getApartment(apartmentId);

        if (apt == null) {
            sender.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        switch (action) {
            case "leave":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /apt guestbook leave <apartment_id> <message>");
                    return true;
                }
                // Cooldown check
                long cooldown = configManager.getGuestBookLeaveCooldown() * 1000L;
                long lastUsed = guestBookCooldowns.getOrDefault(player.getUniqueId(), 0L);
                if (System.currentTimeMillis() - lastUsed < cooldown) {
                    player.sendMessage(plugin.getMessageManager().getMessage("guestbook.leave.cooldown"));
                    return true;
                }

                String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                if (message.length() > configManager.getGuestBookMaxMessageLength()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("guestbook.leave.too_long").replace("%max_chars%", String.valueOf(configManager.getGuestBookMaxMessageLength())));
                    return true;
                }

                List<GuestBookEntry> entries = apartmentManager.getGuestBooks().computeIfAbsent(apartmentId, k -> new ArrayList<>());
                if (entries.size() >= configManager.getGuestBookMaxMessages()) {
                    entries.remove(0); // Remove oldest message if full
                }
                entries.add(new GuestBookEntry(player.getUniqueId(), player.getName(), message, System.currentTimeMillis()));
                apartmentManager.saveGuestBooks();
                guestBookCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                player.sendMessage(plugin.getMessageManager().getMessage("guestbook.leave.success").replace("%apartment%", apt.displayName));
                break;

            case "read":
                List<GuestBookEntry> book = apartmentManager.getGuestBooks().get(apartmentId);
                if (book == null || book.isEmpty()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("guestbook.read.empty").replace("%apartment%", apt.displayName));
                    return true;
                }
                player.sendMessage(plugin.getMessageManager().getMessage("guestbook.read.header").replace("%apartment%", apt.displayName));
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                for (GuestBookEntry entry : book) {
                    String line = plugin.getMessageManager().getMessage("guestbook.read.line_format")
                            .replace("%date%", sdf.format(new Date(entry.timestamp)))
                            .replace("%player%", entry.senderName)
                            .replace("%message%", entry.message);
                    player.sendMessage(line);
                }
                break;

            case "clear":
                if (!player.getUniqueId().equals(apt.owner)) {
                    player.sendMessage(plugin.getMessageManager().getMessage("guestbook.clear.not_owner"));
                    return true;
                }
                ConfirmationAction pending = plugin.getPendingConfirmations().get(player.getUniqueId());
                if (pending == null || !pending.type.equals("guestbook_clear") || !pending.data.equals(apartmentId)) {
                player.sendMessage(plugin.getMessageManager().getMessage("guestbook.clear.confirm_line1").replace("%apartment%", apt.displayName));
                player.sendMessage(plugin.getMessageManager().getMessage("guestbook.clear.confirm_line2"));
                plugin.getPendingConfirmations().put(player.getUniqueId(), new ConfirmationAction("guestbook_clear", apartmentId, System.currentTimeMillis()));
            }
                break;

            default:
                player.sendMessage(plugin.getMessageManager().getMessage("guestbook.usage"));
                break;
        }
        return true;
    }

    // ==========
    // Utilities
    // ==========
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

    // =================
    // Core user actions
    // =================
    public boolean handleInfoCommand(CommandSender sender, String apartmentId) {
        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) {
            sender.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Apartment Info: " + apt.displayName + " ===");
        sender.sendMessage(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + apartmentId);
        sender.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE +
                (apt.owner != null ? Bukkit.getOfflinePlayer(apt.owner).getName() : "For Sale"));
        sender.sendMessage(ChatColor.YELLOW + "Price: " + ChatColor.WHITE + configManager.formatMoney(apt.price));
        // New tax info (invoice-based)
        double basePercent = 0.025 * Math.max(1, apt.level);
        double baseAmount = apt.price * basePercent;
        long nowTs = System.currentTimeMillis();
        TaxStatus taxStatus = apt.computeTaxStatus(nowTs);
        long nextInvoiceInMs = Math.max(0L, (apt.lastInvoiceAt == 0L ? 0L : (apt.lastInvoiceAt + 86_400_000L) - nowTs));
        long unpaidCount = apt.taxInvoices == null ? 0 : apt.taxInvoices.stream().filter(inv -> !inv.isPaid()).count();
        double totalUnpaid = apt.getTotalUnpaid();

        sender.sendMessage(ChatColor.YELLOW + "Base Tax: " + ChatColor.WHITE + String.format("%.2f", basePercent * 100) + "% (" + configManager.formatMoney(baseAmount) + ")");
        sender.sendMessage(ChatColor.YELLOW + "Active Invoices: " + ChatColor.WHITE + unpaidCount + ", Total Arrears: " + configManager.formatMoney(totalUnpaid));
        sender.sendMessage(ChatColor.YELLOW + "Level: " + ChatColor.WHITE + apt.level + "/5");
        sender.sendMessage(ChatColor.YELLOW + "Hourly Income: " + ChatColor.WHITE +
                configManager.formatMoney(configManager.getLevelConfig(apt.level).minIncome) + " - " +
                configManager.formatMoney(configManager.getLevelConfig(apt.level).maxIncome));

        // Show rating
        ApartmentRating rating = apartmentManager.getRating(apartmentId);
        if (rating != null && rating.ratingCount > 0) {
            double avgRating = rating.getAverageRating();
            sender.sendMessage(ChatColor.YELLOW + "Rating: " + ChatColor.WHITE +
                    String.format("%.1f/10.0", avgRating) + " (" + rating.ratingCount + " reviews)");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Rating: " + ChatColor.GRAY + "Not rated yet");
        }

        sender.sendMessage(ChatColor.YELLOW + "Tax Status: " + ChatColor.WHITE + taxStatus.name());
        sender.sendMessage(ChatColor.YELLOW + "Can Generate Income: " + ChatColor.WHITE + (apt.canGenerateIncome(nowTs) ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

        // Countdown timers
        if (apt.owner != null) {
            // Next invoice countdown (24h real-world)
            sender.sendMessage(ChatColor.YELLOW + "New Invoice In: " + ChatColor.WHITE +
                    (nextInvoiceInMs > 0 ? formatTime(nextInvoiceInMs) : "Soon"));

            long nextIncomeMillis = plugin.getLastIncomeGenerationTime() + 50000L; // 50 seconds
            long incomeTimeRemaining = nextIncomeMillis - System.currentTimeMillis();
            sender.sendMessage(ChatColor.YELLOW + "Next Income In: " + ChatColor.WHITE +
                    (incomeTimeRemaining > 0 ? formatTime(incomeTimeRemaining) : "Now"));
        }

        if (apt.owner != null && sender instanceof Player &&
                apt.owner.equals(((Player) sender).getUniqueId())) {
            sender.sendMessage(ChatColor.YELLOW + "Pending Income: " + ChatColor.WHITE + configManager.formatMoney(apt.pendingIncome));
            if (apt.penalty > 0) {
                sender.sendMessage(ChatColor.YELLOW + "Penalty: " + ChatColor.RED + configManager.formatMoney(apt.penalty));
            }
            if (apt.level < 5) {
                double upgradeCost = configManager.getLevelConfig(apt.level + 1).upgradeCost;
                sender.sendMessage(ChatColor.YELLOW + "Upgrade Cost: " + ChatColor.WHITE + configManager.formatMoney(upgradeCost));
            }
        }

        return true;
    }

    public boolean handleBuyCommand(Player player, String apartmentId) {
        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner != null) {
            player.sendMessage(ChatColor.RED + "This apartment is already owned!");
            return true;
        }

        if (!economy.has(player, apt.price)) {
            player.sendMessage(ChatColor.RED + "You don't have enough money! Need: " + configManager.formatMoney(apt.price));
            return true;
        }

        // Check apartment limit
        if (!player.hasPermission("apartmentcore.bypass.limit")) {
            int maxApartments = plugin.getConfig().getInt("limits.max-apartments-per-player", 5);
            if (maxApartments > 0) {
                long owned = apartmentManager.getApartments().values().stream()
                        .filter(a -> player.getUniqueId().equals(a.owner))
                        .count();
                if (owned >= maxApartments) {
                    player.sendMessage(ChatColor.RED + "You have reached the maximum number of apartments (" + maxApartments + ")!");
                    return true;
                }
            }
        }

        economy.withdrawPlayer(player, apt.price);
        apt.owner = player.getUniqueId();
        apt.lastTaxPayment = System.currentTimeMillis();
        apt.inactive = false;
        apt.penalty = 0;
        apt.inactiveSince = 0;

        // Add player to WorldGuard region
        apartmentManager.addPlayerToRegion(player, apt);

        apartmentManager.saveApartments();
        player.sendMessage(ChatColor.GREEN + "Successfully purchased apartment " + apt.displayName + " for " + configManager.formatMoney(apt.price));

        // Show welcome message if exists
        if (!apt.welcomeMessage.isEmpty()) {
            player.sendMessage(ChatColor.AQUA + apt.welcomeMessage);
        }

        plugin.logTransaction(player.getName() + " purchased apartment " + apartmentId);

        return true;
    }

    public boolean handleSellCommand(Player player, String apartmentId) {
        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return true;
        }

        // Block selling if there are unpaid tax invoices
        if (apt.getTotalUnpaid() > 0) {
            player.sendMessage(ChatColor.RED + "Apartments with tax arrears cannot be sold. Pay all taxes first.");
            return true;
        }

        // Block selling if apartment is being auctioned
        AuctionManager am = plugin.getAuctionManager();
        if (am != null && am.getAuction(apartmentId) != null) {
            player.sendMessage(ChatColor.RED + "This apartment is currently being auctioned and cannot be sold.");
            return true;
        }

        // Check for pending confirmation
        ConfirmationAction pending = plugin.getPendingConfirmations().get(player.getUniqueId());
        if (pending == null || !pending.type.equals("sell") || !pending.data.equals(apartmentId)) {
            double sellPrice = apt.price * configManager.getSellPercentage();
            player.sendMessage(ChatColor.YELLOW + "You will receive " + configManager.formatMoney(sellPrice) +
                    " for selling " + apt.displayName);
            player.sendMessage(ChatColor.YELLOW + "Type " + ChatColor.WHITE + "/apartmentcore confirm" +
                    ChatColor.YELLOW + " to confirm the sale.");

            plugin.getPendingConfirmations().put(player.getUniqueId(),
                    new ConfirmationAction("sell", apartmentId, System.currentTimeMillis()));
            return true;
        }

        return true;
    }

    public boolean handleSetTeleportCommand(Player player, String apartmentId) {
        if (!player.hasPermission("apartmentcore.setteleport")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to set a teleport location.");
            return true;
        }

        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return true;
        }

        // Check if player is inside their own apartment region
        World world = Bukkit.getWorld(apt.worldName);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "Could not find the world for this apartment.");
            return true;
        }
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (regionManager == null) {
            player.sendMessage(ChatColor.RED + "WorldGuard RegionManager not found.");
            return true;
        }
        ApplicableRegionSet regionSet = regionManager.getApplicableRegions(BlockVector3.at(
                player.getLocation().getBlockX(),
                player.getLocation().getBlockY(),
                player.getLocation().getBlockZ()
        ));

        boolean inRegion = false;
        for (ProtectedRegion region : regionSet) {
            if (region.getId().equalsIgnoreCase(apt.regionName)) {
                inRegion = true;
                break;
            }
        }

        if (!inRegion) {
            player.sendMessage(ChatColor.RED + "You must be standing inside the apartment to set its teleport location.");
            return true;
        }

        apt.setCustomTeleportLocation(player.getLocation());
        apartmentManager.saveApartments();

        player.sendMessage(ChatColor.GREEN + "Teleport location for " + apt.displayName + " has been set to your current position.");
        return true;
    }

    public boolean handleSetNameCommand(Player player, String apartmentId, String displayName) {
        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return true;
        }

        // Validate display name length
        if (displayName.length() > 32) {
            player.sendMessage(ChatColor.RED + "Display name cannot be longer than 32 characters!");
            return true;
        }

        apt.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        apartmentManager.saveApartments();

        player.sendMessage(ChatColor.GREEN + "Apartment display name set to: " + apt.displayName);

        return true;
    }

    public boolean handleSetWelcomeCommand(Player player, String apartmentId, String message) {
        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return true;
        }

        // Validate message length
        if (message.length() > 100) {
            player.sendMessage(ChatColor.RED + "Welcome message cannot be longer than 100 characters!");
            return true;
        }

        if (message.equalsIgnoreCase("none") || message.equalsIgnoreCase("clear")) {
            apt.welcomeMessage = "";
            player.sendMessage(ChatColor.GREEN + "Welcome message cleared!");
        } else {
            apt.welcomeMessage = ChatColor.translateAlternateColorCodes('&', message);
            player.sendMessage(ChatColor.GREEN + "Welcome message set to: " + apt.welcomeMessage);
        }

        apartmentManager.saveApartments();

        return true;
    }

    public boolean handleRateCommand(Player player, String apartmentId, double rating) {
        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        // Check if apartment is active
        if (apt.owner == null || apt.inactive) {
            player.sendMessage(ChatColor.RED + "You can only rate active apartments!");
            return true;
        }

        // Validate rating range
        if (rating < 0 || rating > 10) {
            player.sendMessage(ChatColor.RED + "Rating must be between 0 and 10!");
            return true;
        }

        // Check cooldown (24 hours)
        UUID playerUuid = player.getUniqueId();
        Map<String, Long> playerCooldowns = apartmentManager.getPlayerRatingCooldowns().computeIfAbsent(playerUuid, k -> new HashMap<>());
        Long lastRating = playerCooldowns.get(apartmentId);

        if (lastRating != null) {
            long timeSinceLastRating = System.currentTimeMillis() - lastRating;
            if (timeSinceLastRating < 86400000) { // 24 hours in milliseconds
                long hoursLeft = TimeUnit.MILLISECONDS.toHours(86400000 - timeSinceLastRating);
                player.sendMessage(ChatColor.RED + "You can rate this apartment again in " + (hoursLeft + 1) + " hours!");
                return true;
            }
        }

        // Get or create rating entry
        ApartmentRating aptRating = apartmentManager.getApartmentRatings().computeIfAbsent(apartmentId, k -> new ApartmentRating());

        // Check if player has rated before
        Double oldRating = aptRating.raters.get(playerUuid);
        if (oldRating != null) {
            // Update existing rating
            aptRating.totalRating = aptRating.totalRating - oldRating + rating;
        } else {
            // New rating
            aptRating.totalRating += rating;
            aptRating.ratingCount++;
        }

        aptRating.raters.put(playerUuid, rating);
        playerCooldowns.put(apartmentId, System.currentTimeMillis());

        apartmentManager.saveRatings();

        player.sendMessage(ChatColor.GREEN + "You rated " + apt.displayName + " " +
                String.format("%.1f", rating) + "/10.0!");
        player.sendMessage(ChatColor.YELLOW + "New average rating: " +
                String.format("%.1f", aptRating.getAverageRating()) + "/10.0");

        return true;
    }

    public boolean handleConfirmCommand(Player player) {
        ConfirmationAction action = plugin.getPendingConfirmations().remove(player.getUniqueId());
        if (action == null) {
            player.sendMessage(ChatColor.RED + "You have no pending actions to confirm!");
            return true;
        }

        switch (action.type) {
            case "sell":
                Apartment aptToSell = apartmentManager.getApartment(action.data);
                if (aptToSell == null || !player.getUniqueId().equals(aptToSell.owner)) {
                    player.sendMessage(ChatColor.RED + "Cannot complete the sale!");
                    return true;
                }
                // Prevent sale if there are unpaid tax invoices
                if (aptToSell.getTotalUnpaid() > 0) {
                    player.sendMessage(ChatColor.RED + "Apartments with tax arrears cannot be sold. Pay the taxes first.");
                    return true;
                }

                // Prevent sale if the apartment has an active auction
                AuctionManager am = plugin.getAuctionManager();
                if (am != null && am.getAuction(action.data) != null) {
                    player.sendMessage(ChatColor.RED + "Cannot complete the sale while the apartment is being auctioned.");
                    return true;
                }
 
                double sellPrice = aptToSell.price * configManager.getSellPercentage();
                
                // Get shop refund before clearing data
                double shopRefund = 0.0;
                if (plugin.getShopManager() != null) {
                    shopRefund = plugin.getShopManager().handleApartmentSale(aptToSell.id, player.getUniqueId());
                }
                
                double totalRefund = sellPrice + shopRefund;
                economy.depositPlayer(player, totalRefund);

                apartmentManager.removePlayerFromRegion(player, aptToSell);

                // Reset apartment
                aptToSell.owner = null;
                aptToSell.pendingIncome = 0;
                aptToSell.inactive = false;
                aptToSell.penalty = 0;
                aptToSell.inactiveSince = 0;
                aptToSell.setCustomTeleportLocation(null); // Clear custom teleport

                // Reset ratings, guestbook, and stats
                apartmentManager.getApartmentRatings().remove(aptToSell.id);
                apartmentManager.getGuestBooks().remove(aptToSell.id);
                apartmentManager.removeStats(aptToSell.id);

                apartmentManager.saveApartments();
                apartmentManager.saveRatings();
                apartmentManager.saveGuestBooks();
                apartmentManager.saveStats();

                String message = "Successfully sold " + aptToSell.displayName + " for " + configManager.formatMoney(sellPrice);
                if (shopRefund > 0) {
                    message += " + " + configManager.formatMoney(shopRefund) + " shop refund";
                }
                message += " (Total: " + configManager.formatMoney(totalRefund) + ")";
                
                player.sendMessage(ChatColor.GREEN + message);
                plugin.logTransaction(player.getName() + " sold apartment " + aptToSell.id +
                    " for " + configManager.formatMoney(sellPrice) +
                    (shopRefund > 0 ? " + shop refund " + configManager.formatMoney(shopRefund) : ""));
                break;

            case "guestbook_clear":
                String apartmentId = action.data;
                Apartment aptToClear = apartmentManager.getApartment(apartmentId);
                if (aptToClear == null || !player.getUniqueId().equals(aptToClear.owner)) {
                    player.sendMessage(ChatColor.RED + "Cannot clear guestbook!");
                    return true;
                }
                apartmentManager.getGuestBooks().remove(apartmentId);
                apartmentManager.saveGuestBooks();
                player.sendMessage(ChatColor.GREEN + "Guestbook for " + aptToClear.displayName + " has been cleared.");
                break;
        }
        return true;
    }

    public boolean handleRentCommand(Player player, String action, String apartmentId) {
        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return true;
        }

        switch (action.toLowerCase()) {
            case "claim":
                if (apt.pendingIncome <= 0) {
                    player.sendMessage(ChatColor.RED + "No income to claim!");
                    return true;
                }
                double incomeToClaim = apt.pendingIncome;
                economy.depositPlayer(player, incomeToClaim);
                player.sendMessage(ChatColor.GREEN + "Claimed " + configManager.formatMoney(incomeToClaim) + " from " + apt.displayName);

                // Update stats
                ApartmentStats stats = apartmentManager.getStats(apartmentId);
                stats.totalIncomeGenerated += incomeToClaim;

                apt.pendingIncome = 0;
                plugin.setLastRentClaimTime(System.currentTimeMillis());
                apartmentManager.saveApartments();
                apartmentManager.saveStats();
                break;

            case "info":
                player.sendMessage(ChatColor.GOLD + "=== Rent Info: " + apt.displayName + " ===");
                player.sendMessage(ChatColor.YELLOW + "Pending Income: " + ChatColor.WHITE + configManager.formatMoney(apt.pendingIncome));
                player.sendMessage(ChatColor.YELLOW + "Hourly Income Range: " + ChatColor.WHITE +
                        configManager.formatMoney(configManager.getLevelConfig(apt.level).minIncome) + " - " +
                        configManager.formatMoney(configManager.getLevelConfig(apt.level).maxIncome));
                player.sendMessage(ChatColor.YELLOW + "Level: " + ChatColor.WHITE + apt.level + "/5");

                // Income countdown
                long nextIncomeMillis = plugin.getLastIncomeGenerationTime() + 50000L;
                long incomeTimeRemaining = nextIncomeMillis - System.currentTimeMillis();
                player.sendMessage(ChatColor.YELLOW + "Next Income In: " + ChatColor.WHITE +
                        (incomeTimeRemaining > 0 ? formatTime(incomeTimeRemaining) : "Now"));

                player.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.WHITE +
                        (apt.inactive ? ChatColor.RED + "Inactive (no income)" : ChatColor.GREEN + "Active"));
                break;

            default:
                player.sendMessage(ChatColor.RED + "Usage: /apartmentcore rent <claim|info> <apartment_id>");
        }

        return true;
    }

    // ================
    // TAX commands
    // ================
    public boolean handleTaxInfo(Player player) {
        UUID uid = player.getUniqueId();
        List<Apartment> owned = apartmentManager.getApartments().values().stream()
                .filter(a -> uid.equals(a.owner))
                .collect(java.util.stream.Collectors.toList());

        if (owned.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You don't have any apartments.");
            return true;
        }

        player.sendMessage(ChatColor.GOLD + "=== Tax information for all apartments ===");
        long now = System.currentTimeMillis();
        double grandTotal = 0.0;

        for (Apartment apt : owned) {
            TaxStatus status = apt.computeTaxStatus(now);
            double totalUnpaid = apt.getTotalUnpaid();
            grandTotal += totalUnpaid;

            long activeCount = 0;
            if (apt.taxInvoices != null) {
                for (TaxInvoice inv : apt.taxInvoices) {
                    if (!inv.isPaid()) activeCount++;
                }
            }

            player.sendMessage(ChatColor.YELLOW + "- " + apt.displayName + ChatColor.WHITE +
                    " [" + status.name() + "], active invoices: " + activeCount +
                    ", Total invoices: " + configManager.formatMoney(totalUnpaid));

            // List unpaid invoices
            int idx = 1;
            if (apt.taxInvoices != null) {
                for (TaxInvoice inv : apt.taxInvoices) {
                    if (inv.isPaid()) continue;
                    long days = inv.daysSinceCreated(now);
                    String dueStr = days >= 3 ? ChatColor.RED + "due date" : ChatColor.WHITE + "not due date";
                    player.sendMessage(ChatColor.GRAY + "  #" + idx + " " + configManager.formatMoney(inv.amount) +
                            " | created " + days + " days ago | " + dueStr);
                    idx++;
                }
            }
        }

        player.sendMessage(ChatColor.GOLD + "Total all invoices: " + ChatColor.WHITE + configManager.formatMoney(grandTotal));
        return true;
    }

    public boolean handleTaxPay(Player player) {
        UUID uid = player.getUniqueId();
        List<Apartment> owned = apartmentManager.getApartments().values().stream()
                .filter(a -> uid.equals(a.owner))
                .collect(java.util.stream.Collectors.toList());

        if (owned.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You don't have an apartment.");
            return true;
        }

        // Calculate total unpaid across all apartments
        double totalUnpaid = 0.0;
        for (Apartment apt : owned) {
            totalUnpaid += apt.getTotalUnpaid();
        }

        if (totalUnpaid <= 0) {
            player.sendMessage(ChatColor.YELLOW + "There are no active tax bills at this time..");
            return true;
        }

        if (!economy.has(player, totalUnpaid)) {
            player.sendMessage(ChatColor.RED + "Insufficient funds! Need: " + configManager.formatMoney(totalUnpaid));
            return true;
        }

        // Withdraw once and mark all invoices as paid (oldest first)
        economy.withdrawPlayer(player, totalUnpaid);
        long now = System.currentTimeMillis();
        for (Apartment apt : owned) {
            List<TaxInvoice> unpaid = new ArrayList<>();
            if (apt.taxInvoices != null) {
                for (TaxInvoice inv : apt.taxInvoices) {
                    if (!inv.isPaid()) {
                        unpaid.add(inv);
                    }
                }
            }
            unpaid.sort(new Comparator<TaxInvoice>() {
                @Override
                public int compare(TaxInvoice a, TaxInvoice b) {
                    return Long.compare(a.createdAt, b.createdAt);
                }
            });
            for (TaxInvoice inv : unpaid) {
                inv.paidAt = now;
                apt.lastTaxPayment = now;
                // Update stats
                ApartmentStats stats = apartmentManager.getStats(apt.id);
                stats.totalTaxPaid += inv.amount;
            }
            // Clear legacy inactive flags if any
            apt.inactive = false;
            apt.inactiveSince = 0L;
        }

        apartmentManager.saveApartments();
        apartmentManager.saveStats();

        player.sendMessage(ChatColor.GREEN + "All tax arrears have been paid: " + configManager.formatMoney(totalUnpaid));
        return true;
    }

    public boolean handleTaxAuto(Player player, String toggle) {
        boolean enable;
        if ("on".equalsIgnoreCase(toggle)) enable = true;
        else if ("off".equalsIgnoreCase(toggle)) enable = false;
        else {
            player.sendMessage(ChatColor.RED + "Usage: /apartmentcore tax auto <on|off>");
            return true;
        }

        UUID uid = player.getUniqueId();
        List<Apartment> owned = apartmentManager.getApartments().values().stream()
                .filter(a -> uid.equals(a.owner))
                .collect(java.util.stream.Collectors.toList());

        if (owned.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You don't have an apartment.");
            return true;
        }

        for (Apartment apt : owned) {
            apt.autoTaxPayment = enable;
        }
        apartmentManager.saveApartments();

        player.sendMessage(ChatColor.GREEN + "Auto-payment for taxes has been " + (enable ? "enabled" : "disabled") + " for all your apartments.");
        return true;
    }

    // =====================
    // Upgrades and listings
    // =====================
    public boolean handleUpgradeCommand(Player player, String apartmentId) {
        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return true;
        }

        if (apt.level >= 5) {
            player.sendMessage(ChatColor.RED + "This apartment is already at maximum level!");
            return true;
        }

        LevelConfig nextLevelConfig = configManager.getLevelConfig(apt.level + 1);
        if (nextLevelConfig == null) {
            player.sendMessage(ChatColor.RED + "This apartment cannot be upgraded further (missing config)!");
            plugin.log("Missing level config for level " + (apt.level + 1));
            return true;
        }
        double upgradeCost = nextLevelConfig.upgradeCost;

        if (!economy.has(player, upgradeCost)) {
            player.sendMessage(ChatColor.RED + "You don't have enough money! Need: " + configManager.formatMoney(upgradeCost));
            return true;
        }

        economy.withdrawPlayer(player, upgradeCost);
        apt.level++;
        apartmentManager.saveApartments();

        player.sendMessage(ChatColor.GREEN + "Successfully upgraded " + apt.displayName + " to level " + apt.level);
        player.sendMessage(ChatColor.YELLOW + "New income range: " +
                configManager.formatMoney(configManager.getLevelConfig(apt.level).minIncome) + " - " +
                configManager.formatMoney(configManager.getLevelConfig(apt.level).maxIncome) + " per hour");

        return true;
    }

    public boolean handleListCommand(CommandSender sender, String filter) {
        UUID playerUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        String finalFilter = filter != null ? filter : "all";
        List<Apartment> displayList = apartmentManager.getApartmentList(finalFilter, playerUuid);
        String title;

        switch(finalFilter) {
            case "sale": title = "Apartments For Sale"; break;
            case "mine": title = "Your Apartments"; break;
            case "top": title = "Top Rated Apartments"; break;
            default: title = "All Apartments"; break;
        }

        if (displayList.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No apartments found for filter: " + finalFilter);
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== " + title + " ===");
        for (Apartment apt : displayList) {
            String owner = apt.owner != null ? Bukkit.getOfflinePlayer(apt.owner).getName() : "For Sale";
            String status = apt.inactive ? ChatColor.RED + "[INACTIVE]" : "";

            // Get rating
            ApartmentRating rating = apartmentManager.getRating(apt.id);
            String ratingStr = rating != null && rating.ratingCount > 0 ?
                    String.format(" %.1f⭐", rating.getAverageRating()) : "";

            sender.sendMessage(ChatColor.YELLOW + apt.displayName + " (" + apt.id + "): " + ChatColor.WHITE +
                    "Owner: " + owner + ", Price: " + configManager.formatMoney(apt.price) +
                    ", L" + apt.level + ratingStr + " " + status);
        }

        return true;
    }

    // =================
    // Help and Admin UI
    // =================
public void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== ApartmentCore Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore gui" + ChatColor.WHITE + " - Open GUI interface");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore info [id]" + ChatColor.WHITE + " - View info");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore buy <id>" + ChatColor.WHITE + " - Buy apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore sell <id>" + ChatColor.WHITE + " - Sell apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore teleport <id>" + ChatColor.WHITE + " - Teleport to apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore rent <claim|info> <id>" + ChatColor.WHITE + " - Manage income");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore tax <info|pay|auto> [on/off]" + ChatColor.WHITE + " - Manage tax (global)");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore upgrade <id>" + ChatColor.WHITE + " - Upgrade apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore setname <id> <name>" + ChatColor.WHITE + " - Set display name");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore setwelcome <id> <msg>" + ChatColor.WHITE + " - Set welcome message");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore setteleport <id>" + ChatColor.WHITE + " - Set teleport location");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore rate <id> <0-10>" + ChatColor.WHITE + " - Rate apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore list [all|sale|mine|top]" + ChatColor.WHITE + " - List apartments");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore auction <create|bid|list|cancel> ..." + ChatColor.WHITE + " - Auction system");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore guestbook <leave|read|clear> <id>" + ChatColor.WHITE + " - Manage guestbook");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore confirm" + ChatColor.WHITE + " - Confirm action");
        sender.sendMessage(ChatColor.GREEN + "💡 Tip: Use " + ChatColor.WHITE + "/apartmentcore gui" + ChatColor.GREEN + " for an easy-to-use interface!");
    }

    public void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Admin Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin create <region> <id> <price>" + ChatColor.WHITE + " - Create apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin remove <id>" + ChatColor.WHITE + " - Remove apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin set <prop> <id> <val>" + ChatColor.WHITE + " - Set apartment property (owner, price, level, rate)");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin status <id> <active|inactive>" + ChatColor.WHITE + " - Manually set apartment status");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin invoice <add|remove> <id> <amount|invoice_id>" + ChatColor.WHITE + " - Manage invoices manually");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin apartment_list" + ChatColor.WHITE + " - List all apartments");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin teleport <id>" + ChatColor.WHITE + " - Teleport to any apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin backup <create|list|restore> [file]" + ChatColor.WHITE + " - Manage backups");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin auction <list|cancel|forceend> [id|filter]" + ChatColor.WHITE + " - Manage auctions");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin reload" + ChatColor.WHITE + " - Reload config");
    }

    public boolean handleAdminCommand(CommandSender sender, String[] args) {
        String adminCmd = args[0].toLowerCase();
 
        switch (adminCmd) {
            case "create":
                if (args.length != 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin create <region> <id> <price>");
                    return true;
                }
                try {
                    return createApartment(sender, args[1], args[2],
                            Double.parseDouble(args[3]));
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid number format!");
                    return true;
                }
 
            case "remove":
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin remove <apartment_id>");
                    return true;
                }
                return removeApartment(sender, args[1]);
 
            case "set":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin set <property> <apartment_id> <value>");
                    return true;
                }
                return setApartmentProperty(sender, args[1], args[2], String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
 
            case "status":
                if (args.length != 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin status <apartment_id> <active|inactive>");
                    return true;
                }
                {
                    String aptId = args[1];
                    String val = args[2].toLowerCase();
                    Apartment apt = apartmentManager.getApartment(aptId);
                    if (apt == null) {
                        sender.sendMessage(ChatColor.RED + "Apartment not found!");
                        return true;
                    }
                    if ("active".equals(val)) {
                        apt.inactive = false;
                        apt.inactiveSince = 0L;
                        sender.sendMessage(ChatColor.GREEN + "Set apartment " + apt.displayName + " to ACTIVE.");
                        plugin.logAdminAction("Admin " + sender.getName() + " set apartment " + aptId + " status ACTIVE");
                    } else if ("inactive".equals(val)) {
                        apt.inactive = true;
                        apt.inactiveSince = System.currentTimeMillis();
                        sender.sendMessage(ChatColor.GREEN + "Set apartment " + apt.displayName + " to INACTIVE.");
                        plugin.logAdminAction("Admin " + sender.getName() + " set apartment " + aptId + " status INACTIVE");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Invalid status. Use active or inactive.");
                        return true;
                    }
                    apartmentManager.saveApartments();
                    return true;
                }
 
            case "invoice": {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin invoice <add|remove> <apartment_id> <amount|invoice_id>");
                    return true;
                }
                String op = args[1].toLowerCase();
                if ("add".equals(op)) {
                    if (args.length != 4) {
                        sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin invoice add <apartment_id> <amount>");
                        return true;
                    }
                    String aptId = args[2];
                    Apartment apt = apartmentManager.getApartment(aptId);
                    if (apt == null) {
                        sender.sendMessage(ChatColor.RED + "Apartment not found!");
                        return true;
                    }
                    try {
                        double amount = Double.parseDouble(args[3]);
                        long now = System.currentTimeMillis();
                        long due = now + 3L * 86_400_000L; // 3 days
                        if (apt.taxInvoices == null) apt.taxInvoices = new ArrayList<>();
                        TaxInvoice inv = new TaxInvoice(amount, now, due);
                        apt.taxInvoices.add(inv);
                        apartmentManager.saveApartments();
                        sender.sendMessage(ChatColor.GREEN + "Added invoice " + inv.id + " (" + configManager.formatMoney(amount) + ") to " + apt.displayName);
                        plugin.logAdminAction("Admin " + sender.getName() + " added invoice " + inv.id + " to " + aptId + " amount " + amount);
                        return true;
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Invalid amount format.");
                        return true;
                    }
                } else if ("remove".equals(op)) {
                    if (args.length != 4) {
                        sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin invoice remove <apartment_id> <invoice_id>");
                        return true;
                    }
                    String aptId = args[2];
                    String invoiceId = args[3];
                    Apartment apt = apartmentManager.getApartment(aptId);
                    if (apt == null) {
                        sender.sendMessage(ChatColor.RED + "Apartment not found!");
                        return true;
                    }
                    if (apt.taxInvoices == null || apt.taxInvoices.isEmpty()) {
                        sender.sendMessage(ChatColor.RED + "No invoices found for this apartment.");
                        return true;
                    }
                    boolean removed = apt.taxInvoices.removeIf(inv -> invoiceId.equals(inv.id));
                    if (removed) {
                        apartmentManager.saveApartments();
                        sender.sendMessage(ChatColor.GREEN + "Removed invoice " + invoiceId + " from " + apt.displayName);
                        plugin.logAdminAction("Admin " + sender.getName() + " removed invoice " + invoiceId + " from " + aptId);
                    } else {
                        sender.sendMessage(ChatColor.RED + "Invoice ID not found for this apartment.");
                    }
                    return true;
                } else {
                    sender.sendMessage(ChatColor.RED + "Unknown invoice operation. Use add or remove.");
                    return true;
                }
            }
 
            case "teleport":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin teleport <apartment_id>");
                    return true;
                }
                return apartmentManager.teleportToApartment((Player) sender, args[1], true);
 
            case "apartment_list":
                listAllApartments(sender);
                return true;
 
            case "backup":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin backup <create|list|restore>");
                    return true;
                }
                return handleBackupCommand(sender, args[1], args.length > 2 ? args[2] : null);
 
            case "reload": {
                boolean wasEnabled = plugin.getAuctionManager() != null;
                plugin.reloadConfig();
                configManager.loadConfiguration();
                plugin.getMessageManager().reloadMessages();
                boolean nowEnabled = configManager.isAuctionEnabled();
 
                if (nowEnabled && !wasEnabled) {
                    plugin.initAuctionSystem();
                } else if (!nowEnabled && wasEnabled) {
                    plugin.shutdownAuctionSystem();
                }
 
                sender.sendMessage(plugin.getMessageManager().getMessage("general.reloaded"));
                return true;
            }
 
            case "auction": {
                AuctionManager am = plugin.getAuctionManager();
                if (am == null) {
                    sender.sendMessage(ChatColor.RED + "Auction system is disabled.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /apartmentcore admin auction <list|cancel|forceend> [id|filter]");
                    return true;
                }
                String op = args[1].toLowerCase();
                switch (op) {
                    case "list": {
                        String filter = args.length > 2 ? args[2].toLowerCase() : "all";
                        java.util.List<ApartmentAuction> auctions = am.getAuctionList(filter, null);
                        if (auctions.isEmpty()) {
                            sender.sendMessage(ChatColor.YELLOW + "No auctions found for filter: " + filter);
                            return true;
                        }
                        sender.sendMessage(ChatColor.GOLD + "=== Active Auctions (" + auctions.size() + ") ===");
                        for (ApartmentAuction a : auctions) {
                            Apartment apt = apartmentManager.getApartment(a.apartmentId);
                            String name = apt != null ? apt.displayName : a.apartmentId;
                            String time = formatTime(a.getRemainingTime());
                            String bidder = (a.currentBidderName != null && !a.currentBidderName.isEmpty()) ? a.currentBidderName : "-";
                            sender.sendMessage(ChatColor.YELLOW + name + ChatColor.WHITE + " [" + a.apartmentId + "] " +
                                    "Seller: " + a.ownerName + ", Current: " + configManager.formatMoney(a.currentBid) +
                                    ", Bids: " + a.totalBids + ", Bidder: " + bidder + ", Ends in: " + time);
                        }
                        return true;
                    }
                    case "cancel": {
                        if (args.length != 3) {
                            sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin auction cancel <apartment_id>");
                            return true;
                        }
                        boolean ok = am.cancelAuctionAdmin(args[2]);
                        sender.sendMessage(ok ? ChatColor.GREEN + "Auction cancelled for " + args[2] : ChatColor.RED + "No active auction for " + args[2]);
                        return true;
                    }
                    case "forceend": {
                        if (args.length != 3) {
                            sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin auction forceend <apartment_id>");
                            return true;
                        }
                        boolean ok = am.forceEndAuction(args[2]);
                        sender.sendMessage(ok ? ChatColor.GREEN + "Auction force-ended for " + args[2] : ChatColor.RED + "No active auction for " + args[2]);
                        return true;
                    }
                    default:
                        sender.sendMessage(ChatColor.YELLOW + "Usage: /apartmentcore admin auction <list|cancel|forceend> [id|filter]");
                        return true;
                }
            }
 
            default:
                sendAdminHelp(sender);
                return true;
        }
    }

    // ======================
    // Internal admin helpers
    // ======================
    private boolean createApartment(CommandSender sender, String regionName, String id, double price) {
        if (apartmentManager.getApartment(id) != null) {
            sender.sendMessage(ChatColor.RED + "Apartment with this ID already exists!");
            return true;
        }

        Player player = sender instanceof Player ? (Player) sender : null;
        String worldName = player != null ? player.getWorld().getName() : Bukkit.getWorlds().get(0).getName();

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "World '" + worldName + "' not found!");
            return true;
        }

        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (regionManager == null || regionManager.getRegion(regionName) == null) {
            sender.sendMessage(ChatColor.RED + "Region '" + regionName + "' not found in world '" + worldName + "'!");
            return true;
        }

        Apartment apt = new Apartment(id, regionName, worldName, null, price, 0.0, 0, 1,
                System.currentTimeMillis(), 0, false, 0, 0, id, "");
        // Set default teleport location to the admin's current position at creation time (if sender is a player)
        if (player != null) {
            apt.setCustomTeleportLocation(player.getLocation());
        }
        apartmentManager.getApartments().put(id, apt);
        apartmentManager.saveApartments();

        sender.sendMessage(ChatColor.GREEN + "Successfully created apartment " + id);
        plugin.logAdminAction("Admin " + sender.getName() + " created apartment " + id);
        return true;
    }

    private boolean removeApartment(CommandSender sender, String apartmentId) {
        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) {
            sender.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner != null) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(apt.owner);
            double refund = apt.price * 0.5; // 50% refund
            economy.depositPlayer(owner, refund);
            if (owner.isOnline()) {
                owner.getPlayer().sendMessage(ChatColor.YELLOW + "Your apartment " + apt.displayName +
                        " was removed by an admin. You received a refund of " + configManager.formatMoney(refund));
            }
        }

// Cancel active auction if exists (admin action)
AuctionManager am = plugin.getAuctionManager();
if (am != null) {
    am.cancelAuctionAdmin(apartmentId);
}
        apartmentManager.getApartments().remove(apartmentId);
        apartmentManager.getApartmentRatings().remove(apartmentId);
        apartmentManager.getGuestBooks().remove(apartmentId);
        apartmentManager.removeStats(apartmentId);
        apartmentManager.saveApartments();
        apartmentManager.saveRatings();
        apartmentManager.saveGuestBooks();
        apartmentManager.saveStats();

        sender.sendMessage(ChatColor.GREEN + "Successfully removed apartment " + apartmentId);
        plugin.logAdminAction("Admin " + sender.getName() + " removed apartment " + apartmentId);
        return true;
    }

    private boolean setApartmentProperty(CommandSender sender, String property, String apartmentId, String value) {
        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) {
            sender.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }
 
        try {
            switch (property.toLowerCase()) {
                case "owner":
                    if (value.equalsIgnoreCase("none")) {
                        apt.owner = null;
                        sender.sendMessage(ChatColor.GREEN + "Removed owner from apartment " + apt.displayName);
                    } else {
                        Player online = Bukkit.getPlayerExact(value);
                        java.util.UUID targetUuid = null;
                        if (online != null) {
                            targetUuid = online.getUniqueId();
                        } else {
                            try {
                                targetUuid = java.util.UUID.fromString(value);
                            } catch (IllegalArgumentException ignored) {
                                // not a UUID string
                            }
                        }
                        if (targetUuid == null) {
                            sender.sendMessage(ChatColor.RED + "Player not found online and not a valid UUID. Use a UUID or have the player join once.");
                            return true;
                        }
                        OfflinePlayer newOwner = Bukkit.getOfflinePlayer(targetUuid);
                        if (newOwner == null || (!newOwner.hasPlayedBefore() && !newOwner.isOnline())) {
                            sender.sendMessage(ChatColor.RED + "Player not found or has never played!");
                            return true;
                        }
                        apt.owner = targetUuid;
                        sender.sendMessage(ChatColor.GREEN + "Set owner of " + apt.displayName + " to " + (online != null ? online.getName() : targetUuid.toString()));
                    }
                    break;
                case "price":
                    apt.price = Double.parseDouble(value);
                    sender.sendMessage(ChatColor.GREEN + "Set price for " + apt.displayName + " to " + configManager.formatMoney(apt.price));
                    break;
                case "level":
                    int level = Integer.parseInt(value);
                    if (level < 1 || level > configManager.getLevelConfigs().size()) {
                        sender.sendMessage(ChatColor.RED + "Invalid level. Must be between 1 and " + configManager.getLevelConfigs().size());
                        return true;
                    }
                    apt.level = level;
                    sender.sendMessage(ChatColor.GREEN + "Set level for " + apt.displayName + " to " + apt.level);
                    break;
                case "rate":
                    double newRating = Double.parseDouble(value);
                    if (newRating < 0 || newRating > 10) {
                        sender.sendMessage(ChatColor.RED + "Rating must be between 0 and 10.");
                        return true;
                    }
                    ApartmentRating aptRating = apartmentManager.getApartmentRatings().computeIfAbsent(apartmentId, k -> new ApartmentRating());
                    aptRating.totalRating = newRating;
                    aptRating.ratingCount = 1;
                    aptRating.raters.clear();
                    apartmentManager.saveRatings();
                    sender.sendMessage(ChatColor.GREEN + "Set rating for " + apt.displayName + " to " + String.format("%.1f", newRating));
                    return true;
                default:
                    sender.sendMessage(ChatColor.RED + "Unknown property. Use: owner, price, level, rate");
                    return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid number format for value!");
            return true;
        }
 
        apartmentManager.saveApartments();
        return true;
    }

    private void listAllApartments(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== All Apartments (" + apartmentManager.getApartmentCount() + ") ===");
        for (Apartment apt : apartmentManager.getApartments().values()) {
            String ownerName = apt.owner != null ? Bukkit.getOfflinePlayer(apt.owner).getName() : "For Sale";
            sender.sendMessage(ChatColor.YELLOW + apt.id + " (" + apt.displayName + "): " + ChatColor.WHITE + "Owner: " + ownerName);
        }
    }

    private boolean handleBackupCommand(CommandSender sender, String action, String backupName) {
        switch (action.toLowerCase()) {
            case "create":
                plugin.getDataManager().createBackup("manual");
                sender.sendMessage(ChatColor.GREEN + "Manual backup created successfully!");
                return true;
            case "list": {
                java.io.File folder = plugin.getDataManager().getBackupFolder();
                if (folder == null || !folder.exists()) {
                    sender.sendMessage(ChatColor.RED + "Backup folder not found.");
                    return true;
                }
                java.io.File[] backups = folder.listFiles((dir, name) -> name.endsWith(".yml"));
                if (backups == null || backups.length == 0) {
                    sender.sendMessage(ChatColor.YELLOW + "No backups found.");
                    return true;
                }
                java.util.Arrays.sort(backups, java.util.Comparator.comparingLong(java.io.File::lastModified).reversed());
                sender.sendMessage(ChatColor.GOLD + "=== Available Backups (" + backups.length + ") ===");
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                int shown = 0;
                for (java.io.File f : backups) {
                    String date = sdf.format(new java.util.Date(f.lastModified()));
                    long sizeKb = Math.max(1L, f.length() / 1024L);
                    sender.sendMessage(ChatColor.YELLOW + f.getName() + ChatColor.WHITE + " - " + sizeKb + "KB, " + date);
                    shown++;
                    if (shown >= 100) break; // avoid chat spam
                }
                sender.sendMessage(ChatColor.GRAY + "Use: /apartmentcore admin backup restore <filename.yml>");
                return true;
            }
            case "restore": {
                if (backupName == null || backupName.trim().isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin backup restore <filename.yml>");
                    return true;
                }
                String name = backupName.trim();
                if (name.contains("..") || name.contains("/") || name.contains("\\") || !name.endsWith(".yml")) {
                    sender.sendMessage(ChatColor.RED + "Invalid backup filename.");
                    return true;
                }
                boolean ok = plugin.getDataManager().restoreBackup(name);
                if (!ok) {
                    sender.sendMessage(ChatColor.RED + "Backup file not found or restore failed.");
                    return true;
                }
                // Reload in-memory data from restored file
                apartmentManager.getApartments().clear();
                apartmentManager.getApartmentRatings().clear();
                apartmentManager.getGuestBooks().clear();
                apartmentManager.getApartmentStats().clear();
                apartmentManager.loadApartments();
                apartmentManager.loadRatings();
                apartmentManager.loadGuestBooks();
                apartmentManager.loadStats();
                sender.sendMessage(ChatColor.GREEN + "Backup restored: " + name + ". All data reloaded.");
                return true;
            }
            default:
                sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin backup <create|list|restore> [filename]");
                return true;
        }
    }
}