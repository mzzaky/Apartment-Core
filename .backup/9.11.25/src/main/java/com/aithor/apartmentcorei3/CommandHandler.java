
package com.aithor.apartmentcorei3;

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
    private final ApartmentCorei3 plugin;
    private final ApartmentManager apartmentManager;
    private final Economy economy;
    private final ConfigManager configManager;
    private final Map<UUID, Long> guestBookCooldowns = new HashMap<>();
    private final ApartmentCommandService commandService;

    public CommandHandler(ApartmentCorei3 plugin, ApartmentManager apartmentManager, Economy economy, ConfigManager configManager) {
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
            sender.sendMessage(ChatColor.GOLD + "=== ApartmentCore v1.2.6 ===");
            sender.sendMessage(ChatColor.YELLOW + "Author: " + ChatColor.WHITE + "Aithor");
            sender.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/apartmentcore help" + ChatColor.YELLOW + " for commands");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "version":
                sender.sendMessage(ChatColor.GREEN + "ApartmentCore version 1.2.8");
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
                return commandService.handleBuyCommand((Player) sender, args[1]);

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
                return commandService.handleSellCommand((Player) sender, args[1]);

            case "setname":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
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
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
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
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore setteleport <apartment_id>");
                    return true;
                }
                return commandService.handleSetTeleportCommand((Player) sender, args[1]);

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
                    return commandService.handleRateCommand((Player) sender, args[1], rating);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid rating! Must be a number between 0 and 10");
                    return true;
                }

            case "confirm":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                return commandService.handleConfirmCommand((Player) sender);

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
                
            case "guestbook":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore guestbook <leave|read|clear> ...");
                    return true;
                }
                return commandService.handleGuestBookCommand(sender, Arrays.copyOfRange(args, 1, args.length));

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
                return commandService.handleRentCommand((Player) sender, args[1], args[2]);

            case "tax":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (!sender.hasPermission("apartmentcore.tax")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to manage taxes!");
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
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /apartmentcore upgrade <apartment_id>");
                    return true;
                }
                return commandService.handleUpgradeCommand((Player) sender, args[1]);

            case "list":
                return commandService.handleListCommand(sender, args.length > 1 ? args[1] : null);

            case "admin":
                if (!sender.hasPermission("apartmentcore.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use admin commands!");
                    return true;
                }
                if (args.length < 2) {
                    commandService.sendAdminHelp(sender);
                    return true;
                }
                return commandService.handleAdminCommand(sender, Arrays.copyOfRange(args, 1, args.length));

            case "help":
                commandService.sendHelp(sender);
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown command. Use /apartmentcore help");
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
                    "help", "version", "info", "buy", "sell", "teleport",
                    "rent", "tax", "upgrade", "list", "confirm", "rate",
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
                case "admin":
                    if (sender.hasPermission("apartmentcore.admin")) {
                        Arrays.asList("create", "remove", "set", "teleport", "apartment_list", "reload", "backup", "restore")
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
                            apartmentManager.getApartments().keySet().stream()
                                    .filter(id -> id.toLowerCase().startsWith(partial))
                                    .forEach(completions::add);
                            break;
                        case "set":
                            Arrays.asList("owner", "price", "tax", "tax_days", "level", "rate").stream()
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
            }
            // Admin create (ID argument) - no strong suggestions available; leave free-form.
        } else if (args.length == 5) {
            if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
                java.util.Arrays.asList("1000", "2500", "5000", "10000").stream()
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


   