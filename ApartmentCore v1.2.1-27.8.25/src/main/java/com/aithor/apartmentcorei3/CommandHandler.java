package com.aithor.apartmentcorei3;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class CommandHandler implements TabCompleter {
    private final ApartmentCorei3 plugin;
    private final ApartmentManager apartmentManager;
    private final Economy economy;
    private final ConfigManager configManager;

    public CommandHandler(ApartmentCorei3 plugin, ApartmentManager apartmentManager, Economy economy, ConfigManager configManager) {
        this.plugin = plugin;
        this.apartmentManager = apartmentManager;
        this.economy = economy;
        this.configManager = configManager;
    }

    /**
     * Check command cooldown
     */
    private boolean checkCooldown(Player player) {
        if (player.hasPermission("apartmentcore.bypass.cooldown")) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        Long lastUse = plugin.getCommandCooldowns().get(uuid);
        long now = System.currentTimeMillis();

        if (lastUse != null && now - lastUse < configManager.getCommandCooldown()) {
            player.sendMessage(ChatColor.RED + "Please wait before using another command!");
            return false;
        }

        plugin.getCommandCooldowns().put(uuid, now);
        return true;
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        // Check cooldown for players
        if (sender instanceof Player && !checkCooldown((Player) sender)) {
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "=== ApartmentCore v1.2.0 ===");
            sender.sendMessage(ChatColor.YELLOW + "Author: " + ChatColor.WHITE + "Aithor");
            sender.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/apartmentcore help" + ChatColor.YELLOW + " for commands");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "version":
                sender.sendMessage(ChatColor.GREEN + "ApartmentCore version 1.2.0");
                return true;

            case "info":
                if (args.length == 1) {
                    sender.sendMessage(ChatColor.GOLD + "=== ApartmentCore Info ===");
                    sender.sendMessage(ChatColor.YELLOW + "Total Apartments: " + ChatColor.WHITE + apartmentManager.getApartmentCount());
                    sender.sendMessage(ChatColor.YELLOW + "Owned Apartments: " + ChatColor.WHITE +
                            apartmentManager.getApartments().values().stream().filter(a -> a.owner != null).count());
                    sender.sendMessage(ChatColor.YELLOW + "For Sale: " + ChatColor.WHITE +
                            apartmentManager.getApartments().values().stream().filter(a -> a.owner == null).count());
                    sender.sendMessage(ChatColor.YELLOW + "Economy: " + ChatColor.WHITE + economy.getName());
                    return true;
                } else if (args.length == 2) {
                    return handleInfoCommand(sender, args[1]);
                }
                break;

            case "buy":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.buy")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to buy apartments!");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore buy <apartment_id>");
                    return true;
                }
                return handleBuyCommand((Player) sender, args[1]);

            case "sell":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.sell")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to sell apartments!");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore sell <apartment_id>");
                    return true;
                }
                return handleSellCommand((Player) sender, args[1]);

            case "setname":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore setname <apartment_id> <display_name>");
                    return true;
                }
                return handleSetNameCommand((Player) sender, args[1],
                        String.join(" ", Arrays.copyOfRange(args, 2, args.length)));

            case "setwelcome":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore setwelcome <apartment_id> <message>");
                    return true;
                }
                return handleSetWelcomeCommand((Player) sender, args[1],
                        String.join(" ", Arrays.copyOfRange(args, 2, args.length)));

            case "rate":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length != 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore rate <apartment_id> <rating>");
                    return true;
                }
                try {
                    double rating = Double.parseDouble(args[2]);
                    return handleRateCommand((Player) sender, args[1], rating);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid rating! Must be a number between 0 and 10");
                    return true;
                }

            case "confirm":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                return handleConfirmCommand((Player) sender);

            case "teleport":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.teleport")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to teleport!");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore teleport <apartment_id>");
                    return true;
                }
                return apartmentManager.teleportToApartment((Player) sender, args[1], false);

            case "rent":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.rent")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to manage rent!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore rent <claim|info> <apartment_id>");
                    return true;
                }
                return handleRentCommand((Player) sender, args[1], args[2]);

            case "tax":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.tax")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to manage taxes!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore tax <pay|info> <apartment_id>");
                    return true;
                }
                return handleTaxCommand((Player) sender, args[1], args[2]);

            case "upgrade":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore upgrade <apartment_id>");
                    return true;
                }
                return handleUpgradeCommand((Player) sender, args[1]);

            case "list":
                return handleListCommand(sender, args.length > 1 ? args[1] : null);

            case "admin":
                if (!sender.hasPermission("apartmentcore.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use admin commands!");
                    return true;
                }
                if (args.length < 2) {
                    sendAdminHelp(sender);
                    return true;
                }
                return handleAdminCommand(sender, Arrays.copyOfRange(args, 1, args.length));

            case "help":
                sendHelp(sender);
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown command. Use /apartmentcore help");
                return true;
        }

        return false;
    }

    private boolean handleInfoCommand(CommandSender sender, String apartmentId) {
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
        sender.sendMessage(ChatColor.YELLOW + "Tax: " + ChatColor.WHITE + configManager.formatMoney(apt.tax) + " every " + apt.taxDays + " days");
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

        sender.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.WHITE +
                (apt.inactive ? ChatColor.RED + "Inactive" : ChatColor.GREEN + "Active"));

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

    private boolean handleBuyCommand(Player player, String apartmentId) {
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

        plugin.log(player.getName() + " purchased apartment " + apartmentId);

        return true;
    }

    private boolean handleSellCommand(Player player, String apartmentId) {
        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
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

    private boolean handleSetNameCommand(Player player, String apartmentId, String displayName) {
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

    private boolean handleSetWelcomeCommand(Player player, String apartmentId, String message) {
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

    private boolean handleRateCommand(Player player, String apartmentId, double rating) {
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
                long hoursLeft = (86400000 - timeSinceLastRating) / 3600000;
                player.sendMessage(ChatColor.RED + "You can rate this apartment again in " + hoursLeft + " hours!");
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

    private boolean handleConfirmCommand(Player player) {
        ConfirmationAction action = plugin.getPendingConfirmations().remove(player.getUniqueId());
        if (action == null) {
            player.sendMessage(ChatColor.RED + "You have no pending actions to confirm!");
            return true;
        }

        if (action.type.equals("sell")) {
            Apartment apt = apartmentManager.getApartment(action.data);
            if (apt == null || !player.getUniqueId().equals(apt.owner)) {
                player.sendMessage(ChatColor.RED + "Cannot complete the sale!");
                return true;
            }

            double sellPrice = apt.price * configManager.getSellPercentage();
            economy.depositPlayer(player, sellPrice);

            // Remove player from WorldGuard region
            apartmentManager.removePlayerFromRegion(player, apt);

            // Reset apartment
            apt.owner = null;
            apt.pendingIncome = 0;
            apt.inactive = false;
            apt.penalty = 0;
            apt.inactiveSince = 0;

            // Reset ratings
            apartmentManager.getApartmentRatings().remove(apt.id);

            apartmentManager.saveApartments();
            apartmentManager.saveRatings();
            player.sendMessage(ChatColor.GREEN + "Successfully sold " + apt.displayName + " for " + configManager.formatMoney(sellPrice));
            plugin.log(player.getName() + " sold apartment " + apt.id);
        }

        return true;
    }

    private boolean handleRentCommand(Player player, String action, String apartmentId) {
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
                economy.depositPlayer(player, apt.pendingIncome);
                player.sendMessage(ChatColor.GREEN + "Claimed " + configManager.formatMoney(apt.pendingIncome) + " from " + apt.displayName);
                apt.pendingIncome = 0;
                plugin.setLastRentClaimTime(System.currentTimeMillis());
                apartmentManager.saveApartments();
                break;

            case "info":
                player.sendMessage(ChatColor.GOLD + "=== Rent Info: " + apt.displayName + " ===");
                player.sendMessage(ChatColor.YELLOW + "Pending Income: " + ChatColor.WHITE + configManager.formatMoney(apt.pendingIncome));
                player.sendMessage(ChatColor.YELLOW + "Hourly Income Range: " + ChatColor.WHITE +
                        configManager.formatMoney(configManager.getLevelConfig(apt.level).minIncome) + " - " +
                        configManager.formatMoney(configManager.getLevelConfig(apt.level).maxIncome));
                player.sendMessage(ChatColor.YELLOW + "Level: " + ChatColor.WHITE + apt.level + "/5");

                // Show time since last claim
                long timeSinceLastClaim = System.currentTimeMillis() - plugin.getLastRentClaimTime();
                long minutesSince = timeSinceLastClaim / 60000;
                player.sendMessage(ChatColor.YELLOW + "Last claim: " + ChatColor.WHITE +
                        (minutesSince < 1 ? "Just now" : minutesSince + " minutes ago"));

                player.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.WHITE +
                        (apt.inactive ? ChatColor.RED + "Inactive (no income)" : ChatColor.GREEN + "Active"));
                break;

            default:
                player.sendMessage(ChatColor.RED + "Usage: /apartmentcore rent <claim|info> <apartment_id>");
        }

        return true;
    }

    private boolean handleTaxCommand(Player player, String action, String apartmentId) {
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
            case "pay":
                double totalDue = apt.tax + apt.penalty;
                if (totalDue <= 0) {
                    player.sendMessage(ChatColor.YELLOW + "No taxes due at this time!");
                    return true;
                }

                if (!economy.has(player, totalDue)) {
                    player.sendMessage(ChatColor.RED + "You don't have enough money! Need: " + configManager.formatMoney(totalDue));
                    return true;
                }

                economy.withdrawPlayer(player, totalDue);
                apt.lastTaxPayment = System.currentTimeMillis();
                apt.inactive = false;
                apt.penalty = 0;
                apt.inactiveSince = 0;
                apartmentManager.saveApartments();
                player.sendMessage(ChatColor.GREEN + "Paid " + configManager.formatMoney(totalDue) + " in taxes for " + apt.displayName);
                break;

            case "info":
                World world = plugin.getServer().getWorlds().get(0);
                long currentDay = world != null ? world.getFullTime() / 24000 : 0;
                long lastPaymentDay = apt.lastTaxPayment / (24000 * 50); // Convert to MC days
                long daysSincePayment = currentDay - lastPaymentDay;

                player.sendMessage(ChatColor.GOLD + "=== Tax Info: " + apt.displayName + " ===");
                player.sendMessage(ChatColor.YELLOW + "Tax Amount: " + ChatColor.WHITE + configManager.formatMoney(apt.tax));
                player.sendMessage(ChatColor.YELLOW + "Tax Period: " + ChatColor.WHITE + apt.taxDays + " days");
                player.sendMessage(ChatColor.YELLOW + "Days Since Payment: " + ChatColor.WHITE + daysSincePayment);
                player.sendMessage(ChatColor.YELLOW + "Penalty: " + ChatColor.WHITE +
                        (apt.penalty > 0 ? ChatColor.RED + configManager.formatMoney(apt.penalty) : ChatColor.GREEN + "None"));
                player.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.WHITE +
                        (apt.inactive ? ChatColor.RED + "Inactive" : ChatColor.GREEN + "Active"));

                if (apt.inactive && apt.inactiveSince > 0) {
                    long inactiveDays = (currentDay * 24000 - apt.inactiveSince) / 24000;
                    player.sendMessage(ChatColor.YELLOW + "Inactive for: " + ChatColor.RED + inactiveDays + " days");
                    player.sendMessage(ChatColor.RED + "Warning: Apartment will be lost after " +
                            (configManager.getInactiveGracePeriod() - inactiveDays) + " more days!");
                }
                break;

            default:
                player.sendMessage(ChatColor.RED + "Usage: /apartmentcore tax <pay|info> <apartment_id>");
        }

        return true;
    }

    private boolean handleUpgradeCommand(Player player, String apartmentId) {
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

        double upgradeCost = configManager.getLevelConfig(apt.level + 1).upgradeCost;

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

    private boolean handleListCommand(CommandSender sender, String filter) {
        UUID playerUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        List<Apartment> displayList = apartmentManager.getApartmentList(filter, playerUuid);
        String title;

        if (filter == null || filter.equals("all")) {
            title = "All Apartments";
        } else if (filter.equals("sale")) {
            title = "Apartments For Sale";
        } else if (filter.equals("mine")) {
            title = "Your Apartments";
        } else if (filter.equals("top")) {
            title = "Top Rated Apartments";
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore list [all|sale|mine|top]");
            return true;
        }

        if (displayList.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No apartments found for filter: " + filter);
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

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        String adminCmd = args[0].toLowerCase();

        switch (adminCmd) {
            case "create":
                if (args.length != 6) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin create <region> <id> <price> <tax> <tax_days>");
                    return true;
                }
                try {
                    return createApartment(sender, args[1], args[2],
                            Double.parseDouble(args[3]),
                            Double.parseDouble(args[4]),
                            Integer.parseInt(args[5]));
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
                return setApartmentProperty(sender, args[1], args[2], args[3]);

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
                listApartments(sender);
                return true;

            case "backup":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin backup <create|list|restore>");
                    return true;
                }
                return handleBackupCommand(sender, args[1], args.length > 2 ? args[2] : null);

            case "reload":
                plugin.reloadConfig();
                configManager.loadConfiguration();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded!");
                return true;

            default:
                sendAdminHelp(sender);
                return true;
        }
    }

    private boolean handleBackupCommand(CommandSender sender, String action, String backupName) {
        switch (action.toLowerCase()) {
            case "create":
                plugin.getDataManager().createBackup("manual");
                sender.sendMessage(ChatColor.GREEN + "Backup created successfully!");
                return true;

            case "list":
                File backupFolder = plugin.getDataManager().getBackupFolder();
                File[] backups = backupFolder.listFiles((dir, name) -> name.endsWith(".yml"));
                if (backups == null || backups.length == 0) {
                    sender.sendMessage(ChatColor.YELLOW + "No backups found!");
                    return true;
                }

                sender.sendMessage(ChatColor.GOLD + "=== Available Backups ===");
                Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());
                for (File backup : backups) {
                    long size = backup.length() / 1024;
                    Date modified = new Date(backup.lastModified());
                    sender.sendMessage(ChatColor.YELLOW + backup.getName() + ChatColor.WHITE +
                            " (" + size + "KB, " + modified + ")");
                }
                return true;

            case "restore":
                if (backupName == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin backup restore <backup_name>");
                    return true;
                }

                if (plugin.getDataManager().restoreBackup(backupName)) {
                    // Reload data after restore
                    apartmentManager.loadApartments();
                    apartmentManager.loadRatings();
                    sender.sendMessage(ChatColor.GREEN + "Successfully restored from backup: " + backupName);
                    plugin.log("Admin " + sender.getName() + " restored backup: " + backupName);
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to restore backup! Check console for errors.");
                }
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore admin backup <create|list|restore>");
                return true;
        }
    }

    private boolean createApartment(CommandSender sender, String region, String id, double price, double tax, int taxDays) {
        if (apartmentManager.getApartments().containsKey(id)) {
            sender.sendMessage(ChatColor.RED + "Apartment with this ID already exists!");
            return true;
        }

        // Validate price and tax
        double maxPrice = plugin.getConfig().getDouble("limits.max-apartment-price", 1000000);
        double minPrice = plugin.getConfig().getDouble("limits.min-apartment-price", 100);

        if (price < minPrice || price > maxPrice) {
            sender.sendMessage(ChatColor.RED + "Price must be between " + configManager.formatMoney(minPrice) +
                    " and " + configManager.formatMoney(maxPrice));
            return true;
        }

        Player player = sender instanceof Player ? (Player) sender : null;
        String worldName = player != null ? player.getWorld().getName() : "world";

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "World not found!");
            return true;
        }

        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (regionManager == null || regionManager.getRegion(region) == null) {
            sender.sendMessage(ChatColor.RED + "Region not found!");
            return true;
        }

        Apartment apt = new Apartment(id, region, worldName, null, price, tax, taxDays, 1,
                System.currentTimeMillis(), 0, false, 0, 0, id, "");
        apartmentManager.getApartments().put(id, apt);
        apartmentManager.saveApartments();

        sender.sendMessage(ChatColor.GREEN + "Successfully created apartment " + id);
        plugin.log("Admin " + sender.getName() + " created apartment " + id);

        return true;
    }

    private boolean removeApartment(CommandSender sender, String apartmentId) {
        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) {
            sender.sendMessage(ChatColor.RED + "Apartment not found!");
            return true;
        }

        // Refund owner if exists
        if (apt.owner != null) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(apt.owner);
            double refund = apt.price * 0.5; // 50% refund
            economy.depositPlayer(owner, refund);

            if (owner.isOnline()) {
                owner.getPlayer().sendMessage(ChatColor.YELLOW + "Your apartment " + apt.displayName +
                        " was removed. You received a refund of " + configManager.formatMoney(refund));
            }
        }

        apartmentManager.getApartments().remove(apartmentId);
        apartmentManager.getApartmentRatings().remove(apartmentId);
        apartmentManager.saveApartments();
        apartmentManager.saveRatings();

        sender.sendMessage(ChatColor.GREEN + "Successfully removed apartment " + apartmentId);
        plugin.log("Admin " + sender.getName() + " removed apartment " + apartmentId);

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
                    if (value.equals("none")) {
                        apt.owner = null;
                        sender.sendMessage(ChatColor.GREEN + "Removed owner from apartment");
                    } else {
                        Player newOwner = Bukkit.getPlayer(value);
                        if (newOwner == null) {
                            sender.sendMessage(ChatColor.RED + "Player not found!");
                            return true;
                        }
                        apt.owner = newOwner.getUniqueId();
                        sender.sendMessage(ChatColor.GREEN + "Set owner to " + value);
                    }
                    break;

                case "price":
                    apt.price = Double.parseDouble(value);
                    sender.sendMessage(ChatColor.GREEN + "Set price to " + configManager.formatMoney(apt.price));
                    break;

                case "tax":
                    apt.tax = Double.parseDouble(value);
                    sender.sendMessage(ChatColor.GREEN + "Set tax to " + configManager.formatMoney(apt.tax));
                    break;

                case "tax_time":
                    apt.taxDays = Integer.parseInt(value);
                    sender.sendMessage(ChatColor.GREEN + "Set tax time to " + value + " days");
                    break;

                case "type":
                case "level":
                    int level = Integer.parseInt(value);
                    if (level < 1 || level > 5) {
                        sender.sendMessage(ChatColor.RED + "Level must be between 1 and 5!");
                        return true;
                    }
                    apt.level = level;
                    sender.sendMessage(ChatColor.GREEN + "Set level to " + value);
                    break;

                default:
                    sender.sendMessage(ChatColor.RED + "Unknown property: " + property);
                    return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid value format!");
            return true;
        }

        apartmentManager.saveApartments();
        return true;
    }

    private void listApartments(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== All Apartments ===");
        sender.sendMessage(ChatColor.GRAY + "Total: " + apartmentManager.getApartmentCount());

        for (Apartment apt : apartmentManager.getApartments().values()) {
            String owner = apt.owner != null ? Bukkit.getOfflinePlayer(apt.owner).getName() : "For Sale";
            String status = apt.inactive ? ChatColor.RED + " [INACTIVE]" : ChatColor.GREEN + " [ACTIVE]";

            ApartmentRating rating = apartmentManager.getRating(apt.id);
            String ratingStr = rating != null && rating.ratingCount > 0 ?
                    String.format(" (%.1f⭐)", rating.getAverageRating()) : "";

            sender.sendMessage(ChatColor.YELLOW + apt.displayName + " (" + apt.id + "): " + ChatColor.WHITE +
                    "Owner: " + owner + ", Price: " + configManager.formatMoney(apt.price) +
                    ", Tax: " + configManager.formatMoney(apt.tax) + "/" + apt.taxDays + "d" +
                    ", L" + apt.level + ratingStr + status);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    // ... kode tetap sama
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Main commands
            List<String> commands = Arrays.asList(
                    "help", "version", "info", "buy", "sell", "teleport",
                    "rent", "tax", "upgrade", "list", "confirm", "rate",
                    "setname", "setwelcome"
            );

            if (sender.hasPermission("apartmentcore.admin")) {
                commands = new ArrayList<>(commands);
                commands.add("admin");
            }

            String partial = args[0].toLowerCase();
            for (String cmd : commands) {
                if (cmd.startsWith(partial)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "buy":
                case "sell":
                case "teleport":
                case "upgrade":
                case "setname":
                case "setwelcome":
                case "rate":
                    // Suggest apartment IDs
                    String partial = args[1].toLowerCase();
                    for (String id : apartmentManager.getApartments().keySet()) {
                        if (id.toLowerCase().startsWith(partial)) {
                            completions.add(id);
                        }
                    }
                    break;

                case "rent":
                case "tax":
                    completions.add("claim");
                    completions.add("info");
                    break;

                case "list":
                    completions.add("all");
                    completions.add("sale");
                    completions.add("mine");
                    completions.add("top");
                    break;

                case "admin":
                    if (sender.hasPermission("apartmentcore.admin")) {
                        List<String> adminCmds = Arrays.asList(
                                "create", "remove", "set", "teleport",
                                "apartment_list", "reload", "backup", "restore"
                        );
                        String partialAdmin = args[1].toLowerCase();
                        for (String cmd : adminCmds) {
                            if (cmd.startsWith(partialAdmin)) {
                                completions.add(cmd);
                            }
                        }
                    }
                    break;

                case "info":
                    // Suggest apartment IDs for info
                    String partialInfo = args[1].toLowerCase();
                    for (String id : apartmentManager.getApartments().keySet()) {
                        if (id.toLowerCase().startsWith(partialInfo)) {
                            completions.add(id);
                        }
                    }
                    break;
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("rent") || subCommand.equals("tax")) {
                String action = args[1].toLowerCase();
                if (action.equals("claim") || action.equals("info")) {
                    // Suggest apartment IDs
                    String partial = args[2].toLowerCase();
                    for (String id : apartmentManager.getApartments().keySet()) {
                        if (id.toLowerCase().startsWith(partial)) {
                            completions.add(id);
                        }
                    }
                }
            } else if (subCommand.equals("admin")) {
                String adminCmd = args[1].toLowerCase();

                switch (adminCmd) {
                    case "set":
                        completions.addAll(Arrays.asList("owner", "price", "tax", "tax_time", "level"));
                        break;
                    case "remove":
                    case "teleport":
                        // Suggest apartment IDs
                        String partial = args[2].toLowerCase();
                        for (String id : apartmentManager.getApartments().keySet()) {
                            if (id.toLowerCase().startsWith(partial)) {
                                completions.add(id);
                            }
                        }
                        break;
                    case "backup":
                        completions.add("create");
                        completions.add("list");
                        break;
                }
            } else if (subCommand.equals("rate")) {
                // Suggest rating values
                completions.addAll(Arrays.asList("10", "9", "8", "7", "6", "5", "4", "3", "2", "1", "0"));
            }
        } else if (args.length == 4) {
            if (args[0].toLowerCase().equals("admin") && args[1].toLowerCase().equals("set")) {
                // Suggest apartment IDs for admin set
                String partial = args[3].toLowerCase();
                for (String id : apartmentManager.getApartments().keySet()) {
                    if (id.toLowerCase().startsWith(partial)) {
                        completions.add(id);
                    }
                }
            }
        }

        return completions;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== ApartmentCore Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore info [id]" + ChatColor.WHITE + " - View info");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore buy <id>" + ChatColor.WHITE + " - Buy apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore sell <id>" + ChatColor.WHITE + " - Sell apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore teleport <id>" + ChatColor.WHITE + " - Teleport to apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore rent <claim|info> <id>" + ChatColor.WHITE + " - Manage income");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore tax <pay|info> <id>" + ChatColor.WHITE + " - Manage taxes");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore upgrade <id>" + ChatColor.WHITE + " - Upgrade apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore setname <id> <name>" + ChatColor.WHITE + " - Set display name");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore setwelcome <id> <msg>" + ChatColor.WHITE + " - Set welcome message");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore rate <id> <0-10>" + ChatColor.WHITE + " - Rate apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore list [all|sale|mine|top]" + ChatColor.WHITE + " - List apartments");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore confirm" + ChatColor.WHITE + " - Confirm action");
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Admin Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin create" + ChatColor.WHITE + " - Create apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin remove" + ChatColor.WHITE + " - Remove apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin set" + ChatColor.WHITE + " - Set apartment property");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin apartment_list" + ChatColor.WHITE + " - List all apartments");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin teleport" + ChatColor.WHITE + " - Teleport to any apartment");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin backup <create|list|restore>" + ChatColor.WHITE + " - Manage backups");
        sender.sendMessage(ChatColor.YELLOW + "/apartmentcore admin reload" + ChatColor.WHITE + " - Reload config");
    }
}