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
    private final Map<UUID, Long> guestBookCooldowns = new HashMap<>();
    private final ApartmentCommandService commandService;

    public CommandHandler(ApartmentCore plugin, ApartmentManager apartmentManager, Economy economy, ConfigManager configManager) {
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
            sender.sendMessage(plugin.getMessageManager().getMessage("general.version_header").replace("%version%", plugin.getDescription().getVersion()));
            sender.sendMessage(plugin.getMessageManager().getMessage("general.about_author"));
            sender.sendMessage(plugin.getMessageManager().getMessage("general.about_help_hint"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "version":
                String ver = plugin.getDescription().getVersion();
                sender.sendMessage(ChatColor.GREEN + "ApartmentCore " + ver);
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
                    return commandService.handleInfoCommand(sender, args[1]);
                }
                break;

            case "buy":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.buy")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission").replace("%action%", "buy apartments"));
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
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission").replace("%action%", "sell apartments"));
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore sell <apartment_id>");
                    return true;
                }
                return commandService.handleSellCommand((Player) sender, args[1]);

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
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission").replace("%action%", "teleport"));
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
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission").replace("%action%", "manage rent"));
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
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission").replace("%action%", "manage taxes"));
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
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore upgrade <apartment_id>");
                    return true;
                }
                return commandService.handleUpgradeCommand((Player) sender, args[1]);
            
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
                            sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission").replace("%action%", "create auctions"));
                            return true;
                        }
                        if (args.length != 5) {
                            sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore auction create <apartment_id> <starting_bid> <duration_hours>");
                            return true;
                        }
                        try {
                            String aptId = args[2];
                            double startBid = Double.parseDouble(args[3]);
                            int hours = Integer.parseInt(args[4]);
                            return am.createAuction((Player) sender, aptId, startBid, hours);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ChatColor.RED + "Invalid number format for starting_bid or duration_hours.");
                            return true;
                        }
                    case "bid":
                        if (!(sender instanceof Player)) {
                            sender.sendMessage(plugin.getMessageManager().getMessage("general.player_only"));
                            return true;
                        }
                        if (!sender.hasPermission("apartmentcore.auction.bid")) {
                            sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission").replace("%action%", "bid"));
                            return true;
                        }
                        if (args.length != 4) {
                            sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore auction bid <apartment_id> <amount>");
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
                            sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission").replace("%action%", "cancel auctions"));
                            return true;
                        }
                        if (args.length != 3) {
                            sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore auction cancel <apartment_id>");
                            return true;
                        }
                        return am.cancelAuction((Player) sender, args[2]);
                    case "list": {
                        if (!sender.hasPermission("apartmentcore.auction.list")) {
                            sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission").replace("%action%", "view auctions"));
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
                            String bidder = (a.currentBidderName != null && !a.currentBidderName.isEmpty()) ? a.currentBidderName : "-";
                            sender.sendMessage(ChatColor.YELLOW + name + ChatColor.WHITE + " [" + a.apartmentId + "] " +
                                    "Seller: " + a.ownerName + ", Current: " + configManager.formatMoney(a.currentBid) +
                                    ", Bids: " + a.totalBids + ", Bidder: " + bidder + ", Ends in: " + time);
                        }
                        return true;
                    }
                    default:
                        sender.sendMessage(ChatColor.YELLOW + "Usage: /apartmentcore auction <create|bid|list|cancel> ...");
                        return true;
                }
            
            case "admin":
                if (!sender.hasPermission("apartmentcore.admin")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission").replace("%action%", "use admin commands"));
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
                    sender.sendMessage(plugin.getMessageManager().getMessage("general.no_permission").replace("%action%", "use GUI"));
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
                    "help", "version", "info", "buy", "sell", "teleport", "gui",
                    "rent", "tax", "upgrade", "list", "auction", "confirm", "rate",
                    "setname", "setwelcome", "setteleport", "guestbook"
            ));
            if (sender.hasPermission("apartmentcore.admin")) {
                commands.add("admin");
            }
            commands.stream().filter(cmd -> cmd.startsWith(partial)).forEach(completions::add);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "info":
                case "buy":
                case "sell":
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
                        Arrays.asList("create", "remove", "set", "status", "invoice", "teleport", "apartment_list", "reload", "backup")
                                .stream().filter(cmd -> cmd.startsWith(partial)).forEach(completions::add);
                    }
                    break;
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
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
                                    : (org.bukkit.Bukkit.getWorlds().isEmpty() ? null : org.bukkit.Bukkit.getWorlds().get(0));
                            if (world != null) {
                                com.sk89q.worldguard.protection.managers.RegionManager regionManager =
                                        com.sk89q.worldguard.WorldGuard.getInstance().getPlatform()
                                                .getRegionContainer().get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
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
                            Arrays.asList("owner", "price", "level", "rate").stream()
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
            // Admin create (ID argument) - no strong suggestions available; leave free-form.
        } else if (args.length == 5) {
            if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("invoice")) {
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
        }

        return completions;
    }

    private boolean handleGuestBookCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
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
                    player.sendMessage(ChatColor.RED + "You must wait before leaving another message.");
                    return true;
                }

                String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                if (message.length() > configManager.getGuestBookMaxMessageLength()) {
                    player.sendMessage(ChatColor.RED + "Your message is too long! Max " + configManager.getGuestBookMaxMessageLength() + " characters.");
                    return true;
                }

                List<GuestBookEntry> entries = apartmentManager.getGuestBooks().computeIfAbsent(apartmentId, k -> new ArrayList<>());
                if (entries.size() >= configManager.getGuestBookMaxMessages()) {
                    entries.remove(0); // Remove oldest message if full
                }
                entries.add(new GuestBookEntry(player.getUniqueId(), player.getName(), message, System.currentTimeMillis()));
                apartmentManager.saveGuestBooks();
                guestBookCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                player.sendMessage(ChatColor.GREEN + "You left a message in " + apt.displayName + "'s guestbook.");
                break;

            case "read":
                List<GuestBookEntry> book = apartmentManager.getGuestBooks().get(apartmentId);
                if (book == null || book.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "The guestbook for " + apt.displayName + " is empty.");
                    return true;
                }
                player.sendMessage(ChatColor.GOLD + "--- Guestbook for " + apt.displayName + " ---");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                for (GuestBookEntry entry : book) {
                    player.sendMessage(ChatColor.GRAY + "[" + sdf.format(new Date(entry.timestamp)) + "] " +
                            ChatColor.AQUA + entry.senderName + ": " + ChatColor.WHITE + entry.message);
                }
                break;

            case "clear":
                if (!player.getUniqueId().equals(apt.owner)) {
                    player.sendMessage(ChatColor.RED + "Only the owner can clear the guestbook.");
                    return true;
                }
                ConfirmationAction pending = plugin.getPendingConfirmations().get(player.getUniqueId());
                if (pending == null || !pending.type.equals("guestbook_clear") || !pending.data.equals(apartmentId)) {
                    player.sendMessage(ChatColor.YELLOW + "Are you sure you want to clear the guestbook for " + apt.displayName + "?");
                    player.sendMessage(ChatColor.YELLOW + "Type " + ChatColor.WHITE + "/apartmentcore confirm" + ChatColor.YELLOW + " to confirm.");
                    plugin.getPendingConfirmations().put(player.getUniqueId(), new ConfirmationAction("guestbook_clear", apartmentId, System.currentTimeMillis()));
                }
                break;

            default:
                player.sendMessage(ChatColor.RED + "Usage: /apt guestbook <leave|read|clear> <apartment_id> ...");
                break;
        }
        return true;
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
            int maxApartments = plugin.getConfig().getInt("settings.max-apartments-per-player", 5);
            // Apply Expansion Plan research bonus
            if (plugin.getResearchManager() != null) {
                maxApartments += plugin.getResearchManager().getExtraOwnershipSlots(player.getUniqueId());
            }
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

        // Block selling if there are unpaid tax invoices
        if (apt.getTotalUnpaid() > 0) {
            player.sendMessage(ChatColor.RED + "Apartments with tax arrears cannot be sold. Pay all taxes first.");
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
    
     private boolean handleSetTeleportCommand(Player player, String apartmentId) {
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
        ApplicableRegionSet regionSet = regionManager.getApplicableRegions(BlockVector3.at(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ()));

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

    private boolean handleConfirmCommand(Player player) {
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
    // New TAX commands
    // ================

    private boolean handleTaxInfo(Player player) {
        UUID uid = player.getUniqueId();
        java.util.List<Apartment> owned = apartmentManager.getApartments().values().stream()
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

    private boolean handleTaxPay(Player player) {
        UUID uid = player.getUniqueId();
        java.util.List<Apartment> owned = apartmentManager.getApartments().values().stream()
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
            java.util.List<TaxInvoice> unpaid = new java.util.ArrayList<>();
            if (apt.taxInvoices != null) {
                for (TaxInvoice inv : apt.taxInvoices) {
                    if (!inv.isPaid()) {
                        unpaid.add(inv);
                    }
                }
            }
            unpaid.sort(new java.util.Comparator<TaxInvoice>() {
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

    private boolean handleTaxAuto(Player player, String toggle) {
        boolean enable;
        if ("on".equalsIgnoreCase(toggle)) enable = true;
        else if ("off".equalsIgnoreCase(toggle)) enable = false;
        else {
            player.sendMessage(ChatColor.RED + "Usage: /apartmentcore tax auto <on|off>");
            return true;
        }

        UUID uid = player.getUniqueId();
        java.util.List<Apartment> owned = apartmentManager.getApartments().values().stream()
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

    private boolean handleListCommand(CommandSender sender, String filter) {
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
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessage("general.help_header"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.about_help_hint"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.buy"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.sell"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.teleport"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.rent"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.tax_auto")); // tax usage maps to tax_auto or tax, choose closest
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.upgrade"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.setname"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.setwelcome"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.setteleport"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.rate"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.list"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.guestbook"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.confirm"));
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessage("general.admin_help_header"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.admin_create"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.admin_remove"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.admin_set"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.list_all"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.teleport"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.admin_backup"));
        sender.sendMessage(plugin.getMessageManager().getMessage("general.usage.admin_reload"));
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
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

            case "reload":
                plugin.reloadConfig();
                configManager.loadConfiguration();
                plugin.getMessageManager().reloadMessages();
                sender.sendMessage(plugin.getMessageManager().getMessage("general.reloaded"));
                return true;

            default:
                sendAdminHelp(sender);
                return true;
        }
    }

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
        ProtectedRegion region = regionManager != null ? regionManager.getRegion(regionName) : null;
        if (regionManager == null || region == null) {
            sender.sendMessage(ChatColor.RED + "Region '" + regionName + "' not found in world '" + worldName + "'!");
            return true;
        }
        // Optional WorldGuard flag checks from config
        if (configManager.isWgCheckFlags() && !apartmentManager.checkRegionRequiredFlags(worldName, regionName)) {
            sender.sendMessage(ChatColor.RED + "Region '" + regionName + "' does not satisfy required WorldGuard flags.");
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
                        UUID targetUuid = null;
                        if (online != null) {
                            targetUuid = online.getUniqueId();
                        } else {
                            try {
                                targetUuid = UUID.fromString(value);
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