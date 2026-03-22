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
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CommandHandler implements TabCompleter {
    private final ApartmentCore plugin;
    private final ApartmentManager apartmentManager;
    private final Economy economy;
    private final ConfigManager configManager;
    private final ApartmentCommandService commandService;

    public CommandHandler(ApartmentCore plugin, ApartmentManager apartmentManager, Economy economy,
            ConfigManager configManager) {
        this.plugin = plugin;
        this.apartmentManager = apartmentManager;
        this.economy = economy;
        this.configManager = configManager;
        this.commandService = new ApartmentCommandService(plugin, apartmentManager, economy, configManager);
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
            player.sendMessage(plugin.getMessageManager().getMessage("general.cooldown"));
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
            sender.sendMessage(plugin.getMessageManager().getMessage("general.version_header").replace("%version%",
                    plugin.getDescription().getVersion()));
            
            // Add Edition and License Status in English
            String editionStr = plugin.getEditionManager().getEdition().name();
            sender.sendMessage(ChatColor.GOLD + "Edition: " + ChatColor.WHITE + editionStr);
            
            String licenseStatus = "N/A";
            if (plugin.getEditionManager().isPro()) {
                if (plugin.getLicenseManager() != null) {
                    licenseStatus = plugin.getLicenseManager().getStatus().name();
                } else {
                    licenseStatus = "ERROR";
                }
            }
            sender.sendMessage(ChatColor.GOLD + "License Status: " + ChatColor.WHITE + licenseStatus);

            sender.sendMessage(plugin.getMessageManager().getMessage("general.about_author"));
            sender.sendMessage(plugin.getMessageManager().getMessage("general.about_help_hint"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "version":
                String ver = plugin.getDescription().getVersion();
                sender.sendMessage(ChatColor.GREEN + "ApartmentCore " + ChatColor.WHITE + "v" + ver);
                
                // Add Edition and License Status in English
                String edition = plugin.getEditionManager().getEdition().name();
                sender.sendMessage(ChatColor.YELLOW + "Edition: " + ChatColor.WHITE + edition);
                
                String status = "N/A";
                if (plugin.getEditionManager().isPro()) {
                    if (plugin.getLicenseManager() != null) {
                        status = plugin.getLicenseManager().getStatus().name();
                    } else {
                        status = "ERROR";
                    }
                }
                sender.sendMessage(ChatColor.YELLOW + "License Status: " + ChatColor.WHITE + status);

                // Show update checker status
                com.aithor.apartmentcore.util.UpdateChecker uc = plugin.getUpdateChecker();
                if (uc != null) {
                    if (uc.isUpdateAvailable()) {
                        sender.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Update Available! "
                                + ChatColor.RESET + ChatColor.YELLOW + "v" + uc.getLatestVersion()
                                + ChatColor.GRAY + " (current: v" + uc.getCurrentVersion() + ")");
                        if (uc.getDownloadUrl() != null) {
                            sender.sendMessage(ChatColor.AQUA + "Download: " + ChatColor.WHITE + uc.getDownloadUrl());
                        }
                    } else if (uc.getLatestVersion() != null) {
                        sender.sendMessage(ChatColor.GREEN + "Up-to-date " + ChatColor.GRAY
                                + "(latest: v" + uc.getLatestVersion() + ")");
                    } else {
                        sender.sendMessage(ChatColor.GRAY + "Update status: checking...");
                    }
                }
                return true;

            case "info":
                if (args.length == 1) {
                    sender.sendMessage(ChatColor.GOLD + "=== ApartmentCore Info ===");
                    sender.sendMessage(ChatColor.YELLOW + "Total Apartments: " + ChatColor.WHITE
                            + apartmentManager.getApartmentCount());
                    sender.sendMessage(ChatColor.YELLOW + "Owned Apartments: " + ChatColor.WHITE +
                            apartmentManager.getApartments().values().stream().filter(a -> a.owner != null).count());
                    sender.sendMessage(ChatColor.YELLOW + "For Sale: " + ChatColor.WHITE +
                            apartmentManager.getApartments().values().stream().filter(a -> a.owner == null).count());
                    sender.sendMessage(ChatColor.YELLOW + "Economy: " + ChatColor.WHITE + economy.getName());
                    return true;
                } else if (args.length == 2) {
                    return commandService.handleInfoCommand(sender, args[1]);
                }
                break;

            case "buy":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.buy")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission")
                            .replace("%action%", "buy apartments"));
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore buy <apartment_id>");
                    return true;
                }
                return commandService.handleBuyCommand((Player) sender, args[1]);

            case "sell":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.sell")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission")
                            .replace("%action%", "sell apartments"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(
                            ChatColor.RED + "Usage: /apartmentcore sell <quick|market|cancel> <apartment_id> [price]");
                    return true;
                }
                String sellType = args[1].toLowerCase();
                switch (sellType) {
                    case "quick":
                        return commandService.handleSellCommand((Player) sender, args[2]);
                    case "market":
                        double price = -1;
                        if (args.length >= 4) {
                            try {
                                price = Double.parseDouble(args[3]);
                            } catch (NumberFormatException e) {
                                sender.sendMessage(
                                        ChatColor.RED + "Invalid price format! Please enter a valid number.");
                                return true;
                            }
                        }
                        return commandService.handleMarketSellCommand((Player) sender, args[2], price);
                    case "cancel":
                        return commandService.handleCancelMarketCommand((Player) sender, args[2]);
                    default:
                        sender.sendMessage(ChatColor.RED
                                + "Usage: /apartmentcore sell <quick|market|cancel> <apartment_id> [price]");
                        return true;
                }

            case "marketbuy":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.buy")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission")
                            .replace("%action%", "buy apartments"));
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore marketbuy <apartment_id>");
                    return true;
                }
                return commandService.handleMarketBuyCommand((Player) sender, args[1]);

            case "setname":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore setname <apartment_id> <display_name>");
                    return true;
                }
                return commandService.handleSetNameCommand((Player) sender, args[1],
                        String.join(" ", Arrays.copyOfRange(args, 2, args.length)));

            case "setwelcome":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore setwelcome <apartment_id> <message>");
                    return true;
                }
                return commandService.handleSetWelcomeCommand((Player) sender, args[1],
                        String.join(" ", Arrays.copyOfRange(args, 2, args.length)));

            case "setteleport":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore setteleport <apartment_id>");
                    return true;
                }
                return commandService.handleSetTeleportCommand((Player) sender, args[1]);

            case "rate":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                    return true;
                }
                if (args.length != 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore rate <apartment_id> <rating>");
                    return true;
                }
                try {
                    double rating = Double.parseDouble(args[2]);
                    return commandService.handleRateCommand((Player) sender, args[1], rating);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid rating! Must be a number between 0 and 10");
                    return true;
                }

            case "confirm":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                    return true;
                }
                return commandService.handleConfirmCommand((Player) sender);

            case "teleport":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.teleport")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission")
                            .replace("%action%", "teleport"));
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore teleport <apartment_id>");
                    return true;
                }
                return apartmentManager.teleportToApartment((Player) sender, args[1], false);

            case "guestbook":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore guestbook <leave|read|clear> ...");
                    return true;
                }
                return commandService.handleGuestBookCommand(sender, Arrays.copyOfRange(args, 1, args.length));

            case "rent":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.rent")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission")
                            .replace("%action%", "manage rent"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore rent <claim|info> <apartment_id>");
                    return true;
                }
                return commandService.handleRentCommand((Player) sender, args[1], args[2]);

            case "tax":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.tax")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission")
                            .replace("%action%", "manage taxes"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore tax <info|pay|auto> [on/off]");
                    return true;
                }
                String taxSub = args[1].toLowerCase();
                switch (taxSub) {
                    case "info":
                        return commandService.handleTaxInfo((Player) sender);
                    case "pay":
                        return commandService.handleTaxPay((Player) sender);
                    case "auto":
                        if (args.length != 3) {
                            sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore tax auto <on|off>");
                            return true;
                        }
                        return commandService.handleTaxAuto((Player) sender, args[2]);
                    default:
                        sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore tax <info|pay|auto> [on/off]");
                        return true;
                }

            case "upgrade":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                    return true;
                }
                if (args.length < 2 || args.length > 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore upgrade <apartment_id> [confirm]");
                    return true;
                }
                boolean confirmed = args.length == 3 && args[2].equalsIgnoreCase("confirm");
                return commandService.handleUpgradeCommand((Player) sender, args[1], confirmed);

            case "list":
                return commandService.handleListCommand(sender, args.length > 1 ? args[1] : null);

            case "auction":
                if (!configManager.isAuctionEnabled() || plugin.getAuctionManager() == null) {
                    sender.sendMessage(ChatColor.RED + "Auction system is disabled.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /apartmentcore auction <create|bid|list|cancel> ...");
                    return true;
                }
                String aAct = args[1].toLowerCase();
                AuctionManager am = plugin.getAuctionManager();
                switch (aAct) {
                    case "create":
                        if (!(sender instanceof Player)) {
                            sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                            return true;
                        }
                        if (!sender.hasPermission("apartmentcore.auction.create")) {
                            sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission")
                                    .replace("%action%", "create auctions"));
                            return true;
                        }
                        if (args.length != 5) {
                            sender.sendMessage(ChatColor.RED
                                    + "Usage: /apartmentcore auction create <apartment_id> <starting_bid> <duration_hours>");
                            return true;
                        }
                        try {
                            String aptId = args[2];
                            double startBid = Double.parseDouble(args[3]);
                            int hours = Integer.parseInt(args[4]);
                            return am.createAuction((Player) sender, aptId, startBid, hours);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(
                                    ChatColor.RED + "Invalid number format for starting_bid or duration_hours.");
                            return true;
                        }
                    case "bid":
                        if (!(sender instanceof Player)) {
                            sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                            return true;
                        }
                        if (!sender.hasPermission("apartmentcore.auction.bid")) {
                            sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission")
                                    .replace("%action%", "bid"));
                            return true;
                        }
                        if (args.length != 4) {
                            sender.sendMessage(
                                    ChatColor.RED + "Usage: /apartmentcore auction bid <apartment_id> <amount>");
                            return true;
                        }
                        try {
                            String aptId = args[2];
                            double amount = Double.parseDouble(args[3]);
                            return am.placeBid((Player) sender, aptId, amount);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ChatColor.RED + "Invalid number format for amount.");
                            return true;
                        }
                    case "cancel":
                        if (!(sender instanceof Player)) {
                            sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                            return true;
                        }
                        if (!sender.hasPermission("apartmentcore.auction.cancel")) {
                            sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission")
                                    .replace("%action%", "cancel auctions"));
                            return true;
                        }
                        if (args.length != 3) {
                            sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore auction cancel <apartment_id>");
                            return true;
                        }
                        return am.cancelAuction((Player) sender, args[2]);
                    case "list": {
                        if (!sender.hasPermission("apartmentcore.auction.list")) {
                            sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission")
                                    .replace("%action%", "view auctions"));
                            return true;
                        }
                        String filter = args.length > 2 ? args[2].toLowerCase() : "all";
                        UUID puid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
                        java.util.List<ApartmentAuction> auctions = am.getAuctionList(filter, puid);
                        if (auctions.isEmpty()) {
                            sender.sendMessage(ChatColor.YELLOW + "No auctions found for filter: " + filter);
                            return true;
                        }
                        sender.sendMessage(ChatColor.GOLD + "=== Auctions (" + auctions.size() + ") ===");
                        for (ApartmentAuction a : auctions) {
                            Apartment apt = apartmentManager.getApartment(a.apartmentId);
                            String name = apt != null ? apt.displayName : a.apartmentId;
                            String time = formatTime(a.getRemainingTime());
                            String bidder = (a.currentBidderName != null && !a.currentBidderName.isEmpty())
                                    ? a.currentBidderName
                                    : "-";
                            sender.sendMessage(ChatColor.YELLOW + name + ChatColor.WHITE + " [" + a.apartmentId + "] " +
                                    "Seller: " + a.ownerName + ", Current: " + configManager.formatMoney(a.currentBid) +
                                    ", Bids: " + a.totalBids + ", Bidder: " + bidder + ", Ends in: " + time);
                        }
                        return true;
                    }
                    default:
                        sender.sendMessage(
                                ChatColor.YELLOW + "Usage: /apartmentcore auction <create|bid|list|cancel> ...");
                        return true;
                }

            case "admin":
                if (!sender.hasPermission("apartmentcore.admin")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission")
                            .replace("%action%", "use admin commands"));
                    return true;
                }
                if (args.length < 2) {
                    commandService.sendAdminHelp(sender);
                    return true;
                }
                return commandService.handleAdminCommand(sender, Arrays.copyOfRange(args, 1, args.length));

            case "gui":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.gui")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission")
                            .replace("%action%", "use GUI"));
                    return true;
                }
                plugin.getGUIManager().openMainMenu((Player) sender);
                return true;

            case "help":
                commandService.sendHelp(sender);
                return true;

            default:
                sender.sendMessage(plugin.getMessageManager().getMessage("general.unknown_command"));
                return true;

        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String partial = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            List<String> commands = new ArrayList<>(Arrays.asList(
                    "help", "version", "info", "buy", "marketbuy", "sell", "teleport", "gui",
                    "rent", "tax", "upgrade", "list", "auction", "confirm", "rate",
                    "setname", "setwelcome", "setteleport", "guestbook"));
            if (sender.hasPermission("apartmentcore.admin")) {
                commands.add("admin");
            }
            commands.stream().filter(cmd -> cmd.startsWith(partial)).forEach(completions::add);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "info":
                case "buy":
                case "marketbuy":
                case "teleport":
                case "upgrade":
                case "setname":
                case "setwelcome":
                case "setteleport":
                case "rate":
                    apartmentManager.getApartments().keySet().stream()
                            .filter(id -> id.toLowerCase().startsWith(partial))
                            .forEach(completions::add);
                    break;
                case "sell":
                    Arrays.asList("quick", "market", "cancel").stream()
                            .filter(s -> s.startsWith(partial))
                            .forEach(completions::add);
                    break;
                case "guestbook":
                    Arrays.asList("leave", "read", "clear").stream()
                            .filter(s -> s.startsWith(partial))
                            .forEach(completions::add);
                    break;
                case "rent":
                    Arrays.asList("claim", "info").stream()
                            .filter(s -> s.startsWith(partial))
                            .forEach(completions::add);
                    break;
                case "tax":
                    Arrays.asList("pay", "info", "auto").stream()
                            .filter(s -> s.startsWith(partial))
                            .forEach(completions::add);
                    break;
                case "list":
                    Arrays.asList("all", "sale", "mine", "top").stream()
                            .filter(s -> s.startsWith(partial))
                            .forEach(completions::add);
                    break;
                case "auction":
                    Arrays.asList("create", "bid", "list", "cancel").stream()
                            .filter(s -> s.startsWith(partial))
                            .forEach(completions::add);
                    break;
                case "admin":
                    if (sender.hasPermission("apartmentcore.admin")) {
                        Arrays.asList("create", "remove", "set", "status", "invoice", "teleport", "apartment_list",
                                "reload", "backup")
                                .stream().filter(cmd -> cmd.startsWith(partial)).forEach(completions::add);
                    }
                    break;
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "sell":
                    if ("quick".equalsIgnoreCase(args[1]) || "market".equalsIgnoreCase(args[1])
                            || "cancel".equalsIgnoreCase(args[1])) {
                        apartmentManager.getApartments().keySet().stream()
                                .filter(id -> id.toLowerCase().startsWith(partial))
                                .forEach(completions::add);
                    }
                    break;
                case "rent":
                    if (args.length >= 2 && ("claim".equalsIgnoreCase(args[1]) || "info".equalsIgnoreCase(args[1]))) {
                        apartmentManager.getApartments().keySet().stream()
                                .filter(id -> id.toLowerCase().startsWith(partial))
                                .forEach(completions::add);
                    }
                    break;
                case "tax":
                    if (args.length >= 2 && "auto".equalsIgnoreCase(args[1])) {
                        Arrays.asList("on", "off").stream()
                                .filter(s -> s.startsWith(partial))
                                .forEach(completions::add);
                    }
                    break;
                case "rate":
                    Arrays.asList("10", "9", "8", "7", "6", "5", "4", "3", "2", "1", "0").stream()
                            .filter(s -> s.startsWith(partial))
                            .forEach(completions::add);
                    break;
                case "guestbook":
                    switch (args[1].toLowerCase()) {
                        case "leave":
                        case "read":
                        case "clear":
                            apartmentManager.getApartments().keySet().stream()
                                    .filter(id -> id.toLowerCase().startsWith(partial))
                                    .forEach(completions::add);
                            break;
                    }
                    break;
                case "auction":
                    if (args.length >= 2) {
                        String act = args[1].toLowerCase();
                        switch (act) {
                            case "create":
                            case "bid":
                            case "cancel":
                                apartmentManager.getApartments().keySet().stream()
                                        .filter(id -> id.toLowerCase().startsWith(partial))
                                        .forEach(completions::add);
                                break;
                            case "list":
                                Arrays.asList("all", "mine", "ending", "nobids").stream()
                                        .filter(s -> s.startsWith(partial))
                                        .forEach(completions::add);
                                break;
                        }
                    }
                    break;
                case "admin":
                    switch (args[1].toLowerCase()) {
                        case "create": {
                            org.bukkit.World world = sender instanceof org.bukkit.entity.Player
                                    ? ((org.bukkit.entity.Player) sender).getWorld()
                                    : (org.bukkit.Bukkit.getWorlds().isEmpty() ? null
                                            : org.bukkit.Bukkit.getWorlds().get(0));
                            if (world != null) {
                                com.sk89q.worldguard.protection.managers.RegionManager regionManager = com.sk89q.worldguard.WorldGuard
                                        .getInstance().getPlatform()
                                        .getRegionContainer()
                                        .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
                                if (regionManager != null) {
                                    regionManager.getRegions().keySet().stream()
                                            .filter(r -> r.toLowerCase().startsWith(partial))
                                            .forEach(completions::add);
                                }
                            }
                            break;
                        }
                        case "remove":
                        case "teleport":
                        case "status":
                            apartmentManager.getApartments().keySet().stream()
                                    .filter(id -> id.toLowerCase().startsWith(partial))
                                    .forEach(completions::add);
                            break;
                        case "set":
                            Arrays.asList("owner", "price", "level", "rate", "research", "teleport").stream()
                                    .filter(s -> s.startsWith(partial))
                                    .forEach(completions::add);
                            break;
                        case "invoice":
                            Arrays.asList("add", "remove").stream()
                                    .filter(s -> s.startsWith(partial))
                                    .forEach(completions::add);
                            break;
                        case "backup":
                            Arrays.asList("create", "list", "restore").stream()
                                    .filter(s -> s.startsWith(partial))
                                    .forEach(completions::add);
                            break;
                    }
                    break;
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("set")) {
                apartmentManager.getApartments().keySet().stream()
                        .filter(id -> id.toLowerCase().startsWith(partial))
                        .forEach(completions::add);
            } else if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("status")) {
                Arrays.asList("active", "inactive").stream()
                        .filter(s -> s.startsWith(partial))
                        .forEach(completions::add);
            } else if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("invoice")) {
                // /apartmentcore admin invoice <add|remove> <apartment_id> ...
                apartmentManager.getApartments().keySet().stream()
                        .filter(id -> id.toLowerCase().startsWith(partial))
                        .forEach(completions::add);
            } else if (args[0].equalsIgnoreCase("auction")) {
                String act = args[1].toLowerCase();
                if ("create".equals(act) || "bid".equals(act)) {
                    Arrays.asList("1000", "2500", "5000", "10000").stream()
                            .filter(s -> s.startsWith(partial))
                            .forEach(completions::add);
                }
            }
            // Admin create (ID argument) - no strong suggestions available; leave
            // free-form.
        } else if (args.length == 5) {
            if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("set")
                    && args[2].equalsIgnoreCase("research")) {
                Arrays.stream(com.aithor.apartmentcore.research.ResearchType.values())
                        .map(com.aithor.apartmentcore.research.ResearchType::getConfigKey)
                        .filter(key -> key.toLowerCase().startsWith(partial))
                        .forEach(completions::add);
            } else if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("invoice")) {
                if ("add".equalsIgnoreCase(args[2])) {
                    Arrays.asList("100", "500", "1000", "2500", "5000").stream()
                            .filter(s -> s.startsWith(partial))
                            .forEach(completions::add);
                } else if ("remove".equalsIgnoreCase(args[2])) {
                    Apartment apt = apartmentManager.getApartment(args[3]);
                    if (apt != null && apt.taxInvoices != null) {
                        for (TaxInvoice inv : apt.taxInvoices) {
                            if (!inv.isPaid() && inv.id != null && inv.id.toLowerCase().startsWith(partial)) {
                                completions.add(inv.id);
                            }
                        }
                    }
                }
            } else if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
                java.util.Arrays.asList("1000", "2500", "5000", "10000").stream()
                        .filter(s -> s.startsWith(partial))
                        .forEach(completions::add);
            } else if (args[0].equalsIgnoreCase("auction") && args[1].equalsIgnoreCase("create")) {
                Arrays.asList("1", "12", "24", "72", "168").stream()
                        .filter(s -> s.startsWith(partial))
                        .forEach(completions::add);
            }
        } else if (args.length == 6) {
            if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("set")
                    && args[2].equalsIgnoreCase("research")) {
                com.aithor.apartmentcore.research.ResearchType rt = com.aithor.apartmentcore.research.ResearchType
                        .fromConfigKey(args[4]);
                if (rt != null) {
                    for (int i = 0; i <= rt.getMaxTier(); i++) {
                        String tierStr = String.valueOf(i);
                        if (tierStr.startsWith(partial)) {
                            completions.add(tierStr);
                        }
                    }
                } else {
                    Arrays.asList("0", "1", "2", "3", "4", "5").stream()
                            .filter(s -> s.startsWith(partial))
                            .forEach(completions::add);
                }
            }
        }

        return completions;
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
}
