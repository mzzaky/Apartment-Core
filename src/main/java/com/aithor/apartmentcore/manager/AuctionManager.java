package com.aithor.apartmentcore.manager;

import com.aithor.apartmentcore.ApartmentCore;
import com.aithor.apartmentcore.model.Apartment;
import com.aithor.apartmentcore.model.ApartmentAuction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

/**
 * Manages apartment auctions
 */
public class AuctionManager {
    private final ApartmentCore plugin;
    private final ApartmentManager apartmentManager;
    private final Economy economy;
    private final ConfigManager configManager;
    private final DataManager dataManager;

    private final Map<String, ApartmentAuction> activeAuctions;
    private final Map<UUID, Long> auctionCooldowns;

    public AuctionManager(ApartmentCore plugin, ApartmentManager apartmentManager,
                          Economy economy, ConfigManager configManager, DataManager dataManager) {
        this.plugin = plugin;
        this.apartmentManager = apartmentManager;
        this.economy = economy;
        this.configManager = configManager;
        this.dataManager = dataManager;
        this.activeAuctions = new ConcurrentHashMap<>();
        this.auctionCooldowns = new ConcurrentHashMap<>();
    }

    /**
     * Load auctions from storage (saved into data.yml under "auctions")
     */
    public void loadAuctions() {
        if (dataManager.getDataConfig() == null) return;

        ConfigurationSection section = dataManager.getDataConfig().getConfigurationSection("auctions");
        if (section == null) return;

        for (String apartmentId : section.getKeys(false)) {
            try {
                ConfigurationSection auctionSection = section.getConfigurationSection(apartmentId);
                if (auctionSection == null) continue;

                Map<String, Object> data = auctionSection.getValues(false);
                ApartmentAuction auction = ApartmentAuction.deserialize(data);

                // Only load active auctions
                if (auction.isActive()) {
                    activeAuctions.put(apartmentId, auction);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load auction for apartment " + apartmentId + ": " + e.getMessage());
            }
        }

        plugin.debug("Loaded " + activeAuctions.size() + " active auctions");
    }

    /**
     * Save auctions to storage
     */
    public void saveAuctions() {
        if (dataManager.getDataConfig() == null) return;

        dataManager.getDataConfig().set("auctions", null);

        for (Map.Entry<String, ApartmentAuction> entry : activeAuctions.entrySet()) {
            String apartmentId = entry.getKey();
            ApartmentAuction auction = entry.getValue();

            dataManager.getDataConfig().set("auctions." + apartmentId, auction.serialize());
        }

        dataManager.saveDataFile();
        plugin.debug("Saved " + activeAuctions.size() + " auctions");
    }

    /**
     * Create a new auction
     */
    public boolean createAuction(Player player, String apartmentId, double startingBid, int durationHours) {
        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) {
            player.sendMessage(ChatColor.RED + "Apartment not found!");
            return false;
        }

        if (apt.owner == null || !apt.owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't own this apartment!");
            return false;
        }

        if (activeAuctions.containsKey(apartmentId)) {
            player.sendMessage(ChatColor.RED + "This apartment is already being auctioned!");
            return false;
        }

        // Check if apartment has unpaid taxes
        if (apt.getTotalUnpaid() > 0) {
            player.sendMessage(ChatColor.RED + "Cannot auction apartments with unpaid taxes!");
            return false;
        }

        // Check cooldown
        UUID playerId = player.getUniqueId();
        long cooldown = configManager.getAuctionCooldown() * 1000L;
        long lastAuction = auctionCooldowns.getOrDefault(playerId, 0L);
        if (System.currentTimeMillis() - lastAuction < cooldown) {
            player.sendMessage(ChatColor.RED + "You must wait before creating another auction!");
            return false;
        }

        // Validate starting bid
        double minBid = configManager.getAuctionMinStartingBid();
        double maxBid = configManager.getAuctionMaxStartingBid();
        if (startingBid < minBid || startingBid > maxBid) {
            player.sendMessage(ChatColor.RED + "Starting bid must be between " +
                configManager.formatMoney(minBid) + " and " + configManager.formatMoney(maxBid));
            return false;
        }

        // Validate duration
        int minDuration = configManager.getAuctionMinDuration();
        int maxDuration = configManager.getAuctionMaxDuration();
        if (durationHours < minDuration || durationHours > maxDuration) {
            player.sendMessage(ChatColor.RED + "Duration must be between " + minDuration + " and " + maxDuration + " hours!");
            return false;
        }

        // Check auction fee (apply Auction Efficiency research reduction)
        double auctionFee = configManager.getAuctionCreationFee();
        if (plugin.getResearchManager() != null) {
            double feeReduction = plugin.getResearchManager().getAuctionFeeReduction(player.getUniqueId());
            if (feeReduction > 0) {
                auctionFee *= (1.0 - feeReduction / 100.0);
                auctionFee = Math.max(0, auctionFee);
            }
        }
        if (auctionFee > 0) {
            if (!economy.has(player, auctionFee)) {
                player.sendMessage(ChatColor.RED + "You need " + configManager.formatMoney(auctionFee) + " to create an auction!");
                return false;
            }
            EconomyResponse feeResp = economy.withdrawPlayer(player, auctionFee);
            if (feeResp == null || !feeResp.transactionSuccess()) {
                player.sendMessage(ChatColor.RED + "Payment failed while creating auction" +
                        (feeResp != null && feeResp.errorMessage != null ? ": " + feeResp.errorMessage : "."));
                return false;
            }
        }

        // Create auction
        ApartmentAuction auction = new ApartmentAuction(apartmentId, player.getUniqueId(),
            player.getName(), startingBid, durationHours);
        activeAuctions.put(apartmentId, auction);
        auctionCooldowns.put(playerId, System.currentTimeMillis());

        saveAuctions();

        player.sendMessage(ChatColor.GREEN + "Auction created for " + apt.displayName +
            " with starting bid " + configManager.formatMoney(startingBid) +
            " for " + durationHours + " hours!");

        // Broadcast to online players
        if (configManager.isAuctionBroadcast()) {
            String message = ChatColor.GOLD + "[Auction] " + ChatColor.YELLOW +
                apt.displayName + " is now being auctioned! Starting bid: " +
                configManager.formatMoney(startingBid);
            Bukkit.broadcastMessage(message);
        }

        plugin.logTransaction(player.getName() + " created auction for apartment " + apartmentId);
        return true;
    }

    /**
     * Place a bid on auction
     */
    public boolean placeBid(Player player, String apartmentId, double bidAmount) {
        ApartmentAuction auction = activeAuctions.get(apartmentId);
        if (auction == null) {
            player.sendMessage(ChatColor.RED + "No active auction found for this apartment!");
            return false;
        }

        if (!auction.isActive()) {
            player.sendMessage(ChatColor.RED + "This auction has ended!");
            return false;
        }

        if (auction.ownerId.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You cannot bid on your own auction!");
            return false;
        }

        // Check minimum bid or increment
        double minIncrement = configManager.getAuctionMinBidIncrement();
        double requiredBid = (auction.totalBids == 0 || auction.currentBidderId == null)
                ? Math.max(auction.startingBid, auction.currentBid)
                : auction.currentBid + minIncrement;
        if (bidAmount < requiredBid) {
            player.sendMessage(ChatColor.RED + "Bid must be at least " + configManager.formatMoney(requiredBid));
            return false;
        }

        if (!economy.has(player, bidAmount)) {
            player.sendMessage(ChatColor.RED + "You don't have enough money! Need: " + configManager.formatMoney(bidAmount));
            return false;
        }

        // Withdraw new bid amount first to avoid losing the current state if it fails
        EconomyResponse withResp = economy.withdrawPlayer(player, bidAmount);
        if (withResp == null || !withResp.transactionSuccess()) {
            player.sendMessage(ChatColor.RED + "Payment failed" +
                    (withResp != null && withResp.errorMessage != null ? ": " + withResp.errorMessage : "."));
            return false;
        }

        // Refund previous bidder (if any)
        if (auction.currentBidderId != null) {
            OfflinePlayer previousBidder = Bukkit.getOfflinePlayer(auction.currentBidderId);
            EconomyResponse depResp = economy.depositPlayer(previousBidder, auction.currentBid);
            if (depResp == null || !depResp.transactionSuccess()) {
                plugin.getLogger().warning("Failed to refund previous bidder " + previousBidder.getName() +
                        " for apartment " + apartmentId + ": " + (depResp != null ? depResp.errorMessage : "unknown"));
                if (previousBidder.isOnline()) {
                    previousBidder.getPlayer().sendMessage(ChatColor.RED +
                            "System failed to refund your previous bid. Please contact an admin.");
                }
            } else if (previousBidder.isOnline()) {
                Apartment apt = apartmentManager.getApartment(apartmentId);
                String aptName = apt != null ? apt.displayName : apartmentId;
                previousBidder.getPlayer().sendMessage(ChatColor.YELLOW +
                        "You have been outbid on " + aptName +
                        "! Your bid of " + configManager.formatMoney(auction.currentBid) + " has been refunded.");
            }
        }

        // Update auction
        auction.placeBid(player.getUniqueId(), player.getName(), bidAmount);
        saveAuctions();

        player.sendMessage(ChatColor.GREEN + "Bid placed successfully! Your bid: " +
            configManager.formatMoney(bidAmount));

        // Notify owner
        OfflinePlayer owner = Bukkit.getOfflinePlayer(auction.ownerId);
        if (owner.isOnline()) {
            owner.getPlayer().sendMessage(ChatColor.GREEN + "New bid on your auction: " +
                configManager.formatMoney(bidAmount) + " by " + player.getName());
        }

        // Extend auction if bid placed in last minutes
        long remainingTime = auction.getRemainingTime();
        long extendThreshold = configManager.getAuctionExtendThreshold() * 60L * 1000L; // minutes to ms
        if (remainingTime < extendThreshold) {
            long extension = configManager.getAuctionExtendTime() * 60L * 1000L; // minutes to ms
            long maxDurationMs = configManager.getAuctionMaxDuration() * 60L * 60L * 1000L;
            auction.endTime = Math.min(auction.endTime + extension, auction.startTime + maxDurationMs);

            player.sendMessage(ChatColor.YELLOW + "Auction extended due to late bid!");
        }

        plugin.logTransaction(player.getName() + " bid " + bidAmount + " on apartment " + apartmentId);
        return true;
    }

    /**
     * Cancel an auction
     */
    public boolean cancelAuction(Player player, String apartmentId) {
        ApartmentAuction auction = activeAuctions.get(apartmentId);
        if (auction == null) {
            player.sendMessage(ChatColor.RED + "No active auction found for this apartment!");
            return false;
        }

        if (!auction.ownerId.equals(player.getUniqueId()) && !player.hasPermission("apartmentcore.admin")) {
            player.sendMessage(ChatColor.RED + "You can only cancel your own auctions!");
            return false;
        }

        if (auction.totalBids > 0 && !player.hasPermission("apartmentcore.admin")) {
            player.sendMessage(ChatColor.RED + "Cannot cancel auction with existing bids!");
            return false;
        }

        // Refund current bidder if any
        if (auction.currentBidderId != null) {
            OfflinePlayer currentBidder = Bukkit.getOfflinePlayer(auction.currentBidderId);
            EconomyResponse depResp = economy.depositPlayer(currentBidder, auction.currentBid);
            if (currentBidder.isOnline()) {
                Apartment apt = apartmentManager.getApartment(apartmentId);
                String aptName = apt != null ? apt.displayName : apartmentId;
                if (depResp == null || !depResp.transactionSuccess()) {
                    currentBidder.getPlayer().sendMessage(ChatColor.RED +
                            "Refund failed for cancelled auction of " + aptName + ". Please contact an admin.");
                } else {
                    currentBidder.getPlayer().sendMessage(ChatColor.YELLOW +
                            "Auction for " + aptName +
                            " has been cancelled. Your bid has been refunded.");
                }
            }
        }

        activeAuctions.remove(apartmentId);
        saveAuctions();

        player.sendMessage(ChatColor.GREEN + "Auction cancelled successfully!");
        plugin.logAdminAction(player.getName() + " cancelled auction for apartment " + apartmentId);
        return true;
    }

    /**
     * Process ended auctions
     */
    public void processEndedAuctions() {
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, ApartmentAuction> entry : activeAuctions.entrySet()) {
            String apartmentId = entry.getKey();
            ApartmentAuction auction = entry.getValue();

            if (auction.hasEnded() && !auction.ended) {
                processAuctionEnd(apartmentId, auction);
                toRemove.add(apartmentId);
            }
        }

        // Remove ended auctions
        for (String apartmentId : toRemove) {
            activeAuctions.remove(apartmentId);
        }

        if (!toRemove.isEmpty()) {
            saveAuctions();
        }
    }

    /**
     * Process the end of a single auction
     */
    private void processAuctionEnd(String apartmentId, ApartmentAuction auction) {
        auction.endAuction();
        Apartment apt = apartmentManager.getApartment(apartmentId);
        if (apt == null) return;

        OfflinePlayer owner = Bukkit.getOfflinePlayer(auction.ownerId);

        if (auction.currentBidderId != null) {
            // Auction successful - transfer ownership
            OfflinePlayer winner = Bukkit.getOfflinePlayer(auction.currentBidderId);

            // Transfer ownership
            try {
                apartmentManager.removeOwnerUuidFromRegion(apt, auction.ownerId);
            } catch (Throwable ignored) {}

            apt.owner = auction.currentBidderId;
            apt.lastTaxPayment = System.currentTimeMillis();
            apt.inactive = false;
            apt.penalty = 0;
            apt.inactiveSince = 0;

            if (winner.isOnline()) {
                apartmentManager.addPlayerToRegion(winner.getPlayer(), apt);
            } else {
                try {
                    apartmentManager.addOwnerUuidToRegion(apt, auction.currentBidderId);
                } catch (Throwable ignored) {}
            }

            // Pay seller (minus commission, apply Auction Efficiency research reduction)
            double commissionRate = configManager.getAuctionCommission();
            if (plugin.getResearchManager() != null) {
                double commReduction = plugin.getResearchManager().getAuctionCommissionReduction(auction.ownerId);
                if (commReduction > 0) {
                    commissionRate = Math.max(0, commissionRate - (commReduction / 100.0));
                }
            }
            double commission = auction.currentBid * commissionRate;
            double sellerAmount = auction.currentBid - commission;
            EconomyResponse sellerResp = economy.depositPlayer(owner, sellerAmount);
            if (sellerResp == null || !sellerResp.transactionSuccess()) {
                plugin.getLogger().severe("Failed to pay seller for auction " + apartmentId + ": " +
                        (sellerResp != null ? sellerResp.errorMessage : "unknown"));
            }

            // Reset apartment ratings and stats for new owner
            apartmentManager.getApartmentRatings().remove(apartmentId);
            apartmentManager.getGuestBooks().remove(apartmentId);
            apartmentManager.removeStats(apartmentId);

            apartmentManager.saveApartments();

            // Notify participants
            if (winner.isOnline()) {
                winner.getPlayer().sendMessage(ChatColor.GREEN +
                    "Congratulations! You won the auction for " + apt.displayName +
                    " with a bid of " + configManager.formatMoney(auction.currentBid) + "!");
            }

            if (owner.isOnline()) {
                owner.getPlayer().sendMessage(ChatColor.GREEN +
                    "Your auction for " + apt.displayName + " has ended! You received " +
                    configManager.formatMoney(sellerAmount) +
                    " (commission: " + configManager.formatMoney(commission) + ")");
            }

            // Broadcast result
            if (configManager.isAuctionBroadcast()) {
                Bukkit.broadcastMessage(ChatColor.GOLD + "[Auction] " + ChatColor.GREEN +
                    auction.currentBidderName + " won the auction for " + apt.displayName +
                    " with a bid of " + configManager.formatMoney(auction.currentBid) + "!");
            }

            plugin.logTransaction("Auction ended: " + apartmentId + " sold to " +
                auction.currentBidderName + " for " + auction.currentBid);

        } else {
            // No bids - return to owner
            if (owner.isOnline()) {
                owner.getPlayer().sendMessage(ChatColor.YELLOW +
                    "Your auction for " + apt.displayName + " has ended with no bids.");
            }

            plugin.debug("Auction ended with no bids: " + apartmentId);
        }
    }

    /**
     * Get auction by apartment ID
     */
    public ApartmentAuction getAuction(String apartmentId) {
        return activeAuctions.get(apartmentId);
    }

    /**
     * Get all active auctions
     */
    public Map<String, ApartmentAuction> getActiveAuctions() {
        return new HashMap<>(activeAuctions);
    }

    /**
     * Get auctions by filter
     */
    public List<ApartmentAuction> getAuctionList(String filter, UUID playerUuid) {
        return activeAuctions.values().stream()
            .filter(auction -> {
                if (filter == null) return auction.isActive();
                switch (filter.toLowerCase()) {
                    case "mine":
                        return playerUuid != null && auction.ownerId.equals(playerUuid);
                    case "ending":
                        return auction.getRemainingTime() < 3_600_000L; // Less than 1 hour
                    case "nobids":
                        return auction.totalBids == 0;
                    default:
                        return auction.isActive();
                }
            })
            .sorted(Comparator.comparingLong(ApartmentAuction::getRemainingTime))
            .collect(Collectors.toList());
    }

    /**
     * Check remaining cooldown for a player in milliseconds
     */
    public long getAuctionCooldown(UUID playerId) {
        long cooldown = configManager.getAuctionCooldown() * 1000L;
        long lastAuction = auctionCooldowns.getOrDefault(playerId, 0L);
        return Math.max(0, cooldown - (System.currentTimeMillis() - lastAuction));
    }

    /**
     * Admin-cancel an auction without a Player context.
     * Refunds current highest bidder if present and removes the auction entry.
     * Returns true if an active auction existed and was cancelled.
     */
    public boolean cancelAuctionAdmin(String apartmentId) {
        ApartmentAuction auction = activeAuctions.get(apartmentId);
        if (auction == null) {
            return false;
        }

        // Refund current bidder if any
        if (auction.currentBidderId != null) {
            OfflinePlayer currentBidder = Bukkit.getOfflinePlayer(auction.currentBidderId);
            economy.depositPlayer(currentBidder, auction.currentBid);

            if (currentBidder.isOnline()) {
                Apartment apt = apartmentManager.getApartment(apartmentId);
                String aptName = apt != null ? apt.displayName : apartmentId;
                currentBidder.getPlayer().sendMessage(ChatColor.YELLOW +
                        "Auction for " + aptName +
                        " has been cancelled by an admin. Your bid has been refunded.");
            }
        }

        // Notify owner if online
        OfflinePlayer owner = Bukkit.getOfflinePlayer(auction.ownerId);
        if (owner.isOnline()) {
            Apartment apt = apartmentManager.getApartment(apartmentId);
            String aptName = apt != null ? apt.displayName : apartmentId;
            owner.getPlayer().sendMessage(ChatColor.YELLOW +
                    "Your auction for " + aptName + " has been cancelled by an admin.");
        }

        activeAuctions.remove(apartmentId);
        saveAuctions();
        plugin.logAdminAction("Admin cancelled auction for apartment " + apartmentId);
        return true;
    }

    /**
     * Force-end an auction immediately, transferring ownership if there is a winner.
     * Returns true if an active auction existed and was force-ended.
     */
    public boolean forceEndAuction(String apartmentId) {
        ApartmentAuction auction = activeAuctions.get(apartmentId);
        if (auction == null) {
            return false;
        }

        try {
            processAuctionEnd(apartmentId, auction);
        } catch (Throwable t) {
            plugin.getLogger().warning("Error while force-ending auction for " + apartmentId + ": " + t.getMessage());
        } finally {
            activeAuctions.remove(apartmentId);
            saveAuctions();
        }

        plugin.logAdminAction("Admin force-ended auction for apartment " + apartmentId);
        return true;
    }
}