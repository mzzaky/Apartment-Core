package com.aithor.apartmentcorei3.gui.menus;

import com.aithor.apartmentcorei3.*;
import com.aithor.apartmentcorei3.gui.GUIManager;
import com.aithor.apartmentcorei3.gui.interfaces.PaginatedGUI;
import com.aithor.apartmentcorei3.gui.items.GUIItem;
import com.aithor.apartmentcorei3.gui.items.ItemBuilder;
import com.aithor.apartmentcorei3.gui.utils.GUIUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GUI for browsing and managing auctions
 */
public class AuctionHouseGUI extends PaginatedGUI {
    
    private final ApartmentCorei3 plugin;
    private final GUIManager guiManager;
    private final AuctionManager auctionManager;
    
    // Filter states
    private AuctionFilter currentFilter = AuctionFilter.ALL;
    
    // Action slots
    private static final int BACK_SLOT = 0;
    private static final int CREATE_AUCTION_SLOT = 1;
    private static final int MY_AUCTIONS_SLOT = 2;
    private static final int FILTER_ALL_SLOT = 3;
    private static final int FILTER_ENDING_SLOT = 4;
    private static final int FILTER_NO_BIDS_SLOT = 5;
    private static final int REFRESH_SLOT = 7;
    
    public AuctionHouseGUI(Player player, ApartmentCorei3 plugin, GUIManager guiManager) {
        super(player, ChatColor.DARK_PURPLE + "Auction House", 54, 28);
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.auctionManager = plugin.getAuctionManager();
    }
    
    @Override
    protected List<GUIItem> loadItems() {
        if (auctionManager == null) {
            return new ArrayList<>();
        }
        
        List<ApartmentAuction> auctions = auctionManager.getActiveAuctions().values().stream()
                .filter(auction -> auction.isActive())
                .collect(Collectors.toList());
        
        // Apply filter
        auctions = applyFilter(auctions);
        
        // Sort by ending time (soonest first)
        auctions = auctions.stream()
                .sorted(Comparator.comparingLong(ApartmentAuction::getRemainingTime))
                .collect(Collectors.toList());
        
        List<GUIItem> items = new ArrayList<>();
        for (ApartmentAuction auction : auctions) {
            items.add(createAuctionItem(auction));
        }
        
        return items;
    }
    
    @Override
    protected void setupInventory() {
        super.setupInventory();
        addActionButtons();
    }
    
    private void addActionButtons() {
        // Back button
        ItemStack backItem = new ItemBuilder(Material.ARROW)
                .name("&c◀ Back to Main Menu")
                .lore("&7Return to the main menu")
                .build();
        inventory.setItem(BACK_SLOT, backItem);
        
        // Create Auction
        boolean canCreate = player.hasPermission("apartmentcore.auction.create");
        long cooldown = auctionManager != null ? auctionManager.getAuctionCooldown(player.getUniqueId()) : 0;
        boolean onCooldown = cooldown > 0;
        
        Material createMaterial = canCreate && !onCooldown ? Material.DIAMOND_AXE : Material.GRAY_CONCRETE;
        List<String> createLore = new ArrayList<>();
        createLore.add("&7Create a new auction for your apartment");
        createLore.add("");
        if (onCooldown) {
            createLore.add("&cCooldown: " + GUIUtils.formatTime(cooldown));
        } else if (canCreate) {
            createLore.add("&7Fee: &e" + plugin.getConfigManager().formatMoney(plugin.getConfigManager().getAuctionCreationFee()));
            createLore.add("&7Commission: &e" + String.format("%.1f%%", plugin.getConfigManager().getAuctionCommission() * 100));
            createLore.add("");
            createLore.add("&a▶ Click to create auction");
        } else {
            createLore.add("&cNo permission to create auctions");
        }
        
        ItemStack createItem = new ItemBuilder(createMaterial)
                .name("&a🔨 Create Auction")
                .lore(createLore)
                .build();
        inventory.setItem(CREATE_AUCTION_SLOT, createItem);
        
        // My Auctions
        int myAuctionCount = 0;
        if (auctionManager != null) {
            myAuctionCount = (int) auctionManager.getActiveAuctions().values().stream()
                    .filter(a -> a.ownerId.equals(player.getUniqueId()))
                    .count();
        }
        
        ItemStack myAuctionsItem = new ItemBuilder(Material.PLAYER_HEAD)
                .name("&a👤 My Auctions")
                .lore(
                    "&7View and manage your auctions",
                    "",
                    "&7Active Auctions: &f" + myAuctionCount,
                    "",
                    "&a▶ Click to view"
                )
                .skull(player.getName())
                .build();
        inventory.setItem(MY_AUCTIONS_SLOT, myAuctionsItem);
        
        // Filter buttons
        addFilterButtons();
        
        // Refresh button
        ItemStack refreshItem = new ItemBuilder(Material.CLOCK)
                .name("&a🔄 Refresh")
                .lore(
                    "&7Refresh auction listings",
                    "",
                    "&a▶ Click to refresh"
                )
                .build();
        inventory.setItem(REFRESH_SLOT, refreshItem);
    }
    
    private void addFilterButtons() {
        // Filter: All
        Material allMaterial = currentFilter == AuctionFilter.ALL ? Material.LIME_CONCRETE : Material.WHITE_CONCRETE;
        ItemStack allFilter = new ItemBuilder(allMaterial)
                .name("&6All Auctions")
                .lore(
                    "&7Show all active auctions",
                    "",
                    currentFilter == AuctionFilter.ALL ? "&a✓ Active filter" : "&7Click to activate"
                )
                .build();
        inventory.setItem(FILTER_ALL_SLOT, allFilter);
        
        // Filter: Ending Soon
        Material endingMaterial = currentFilter == AuctionFilter.ENDING_SOON ? Material.LIME_CONCRETE : Material.ORANGE_CONCRETE;
        ItemStack endingFilter = new ItemBuilder(endingMaterial)
                .name("&6Ending Soon")
                .lore(
                    "&7Show auctions ending within 1 hour",
                    "",
                    currentFilter == AuctionFilter.ENDING_SOON ? "&a✓ Active filter" : "&7Click to activate"
                )
                .build();
        inventory.setItem(FILTER_ENDING_SLOT, endingFilter);
        
        // Filter: No Bids
        Material noBidsMaterial = currentFilter == AuctionFilter.NO_BIDS ? Material.LIME_CONCRETE : Material.YELLOW_CONCRETE;
        ItemStack noBidsFilter = new ItemBuilder(noBidsMaterial)
                .name("&6No Bids Yet")
                .lore(
                    "&7Show auctions with no bids",
                    "",
                    currentFilter == AuctionFilter.NO_BIDS ? "&a✓ Active filter" : "&7Click to activate"
                )
                .build();
        inventory.setItem(FILTER_NO_BIDS_SLOT, noBidsFilter);
    }
    
    private List<ApartmentAuction> applyFilter(List<ApartmentAuction> auctions) {
        switch (currentFilter) {
            case ENDING_SOON:
                return auctions.stream()
                        .filter(a -> a.getRemainingTime() < 3600000) // Less than 1 hour
                        .collect(Collectors.toList());
            case NO_BIDS:
                return auctions.stream()
                        .filter(a -> a.totalBids == 0)
                        .collect(Collectors.toList());
            case MY_AUCTIONS:
                return auctions.stream()
                        .filter(a -> a.ownerId.equals(player.getUniqueId()))
                        .collect(Collectors.toList());
            default:
                return auctions;
        }
    }
    
    private GUIItem createAuctionItem(ApartmentAuction auction) {
        Apartment apartment = plugin.getApartmentManager().getApartment(auction.apartmentId);
        String apartmentName = apartment != null ? apartment.displayName : auction.apartmentId;
        
        long remainingTime = auction.getRemainingTime();
        String timeDisplay = GUIUtils.formatTime(remainingTime);
        
        // Determine urgency color
        ChatColor timeColor;
        if (remainingTime < 600000) { // Less than 10 minutes
            timeColor = ChatColor.RED;
        } else if (remainingTime < 3600000) { // Less than 1 hour
            timeColor = ChatColor.GOLD;
        } else {
            timeColor = ChatColor.GREEN;
        }
        
        // Check if player can bid
        boolean canBid = !auction.ownerId.equals(player.getUniqueId()) && 
                        player.hasPermission("apartmentcore.auction.bid");
        
        // Calculate next bid amount
        double nextBidAmount = auction.totalBids == 0 ? 
                auction.startingBid : 
                auction.currentBid + plugin.getConfigManager().getAuctionMinBidIncrement();
        
        boolean canAfford = plugin.getEconomy().has(player, nextBidAmount);
        
        List<String> lore = new ArrayList<>();
        lore.add("&7Apartment: &f" + apartmentName);
        lore.add("&7Seller: &f" + auction.ownerName);
        lore.add("");
        lore.add("&e💰 Bid Information:");
        lore.add("&7• Starting Bid: &a" + plugin.getConfigManager().formatMoney(auction.startingBid));
        lore.add("&7• Current Bid: &a" + plugin.getConfigManager().formatMoney(auction.currentBid));
        lore.add("&7• Next Bid: &a" + plugin.getConfigManager().formatMoney(nextBidAmount));
        lore.add("&7• Total Bids: &f" + auction.totalBids);
        
        if (auction.currentBidderId != null) {
            boolean isCurrentBidder = auction.currentBidderId.equals(player.getUniqueId());
            lore.add("&7• Current Bidder: " + (isCurrentBidder ? "&a" + auction.currentBidderName + " (You!)" : "&f" + auction.currentBidderName));
        }
        
        lore.add("");
        lore.add("&e⏰ Time Information:");
        lore.add("&7• Time Remaining: " + timeColor + timeDisplay);
        
        if (apartment != null) {
            lore.add("");
            lore.add("&e🏠 Apartment Details:");
            lore.add("&7• Level: &f" + apartment.level + "/5");
            
            LevelConfig levelConfig = plugin.getConfigManager().getLevelConfig(apartment.level);
            if (levelConfig != null) {
                lore.add("&7• Income: &a" + plugin.getConfigManager().formatMoney(levelConfig.minIncome) + 
                         " &7- &a" + plugin.getConfigManager().formatMoney(levelConfig.maxIncome) + "/hour");
            }
            
            ApartmentRating rating = plugin.getApartmentManager().getRating(apartment.id);
            if (rating != null && rating.ratingCount > 0) {
                lore.add("&7• Rating: &f" + String.format("%.1f⭐ (%d reviews)", rating.getAverageRating(), rating.ratingCount));
            }
        }
        
        lore.add("");
        
        if (auction.ownerId.equals(player.getUniqueId())) {
            // Owner's auction
            lore.add("&6👑 This is your auction");
            lore.add("&a▶ Left-click to view details");
            lore.add("&c▶ Right-click to cancel");
        } else if (canBid) {
            // Can bid
            lore.add(canAfford ? "&a▶ Left-click to place bid" : "&c▶ Insufficient funds to bid");
            lore.add("&a▶ Right-click to view details");
        } else {
            // Cannot bid
            lore.add("&7▶ Click to view details");
        }
        
        // Choose material based on status
        Material material;
        if (auction.ownerId.equals(player.getUniqueId())) {
            material = Material.GOLD_BLOCK; // Owner's auction
        } else if (auction.currentBidderId != null && auction.currentBidderId.equals(player.getUniqueId())) {
            material = Material.EMERALD_BLOCK; // Player is winning
        } else if (remainingTime < 600000) {
            material = Material.RED_CONCRETE; // Ending soon
        } else {
            material = Material.DIAMOND_BLOCK; // Regular auction
        }
        
        ItemStack item = new ItemBuilder(material)
                .name("&6🔨 " + apartmentName)
                .lore(lore)
                .glow()
                .build();
        
        return new GUIItem(item, auction.apartmentId, auction);
    }
    
    @Override
    protected void handleItemClick(GUIItem item, InventoryClickEvent event) {
        ApartmentAuction auction = item.getData(ApartmentAuction.class);
        if (auction == null) return;
        
        ClickType clickType = event.getClick();
        boolean isOwner = auction.ownerId.equals(player.getUniqueId());
        
        if (isOwner && clickType == ClickType.RIGHT) {
            // Cancel auction
            handleCancelAuction(auction);
        } else if (!isOwner && clickType == ClickType.LEFT) {
            // Place bid
            handlePlaceBid(auction);
        } else {
            // View details
            handleViewAuctionDetails(auction);
        }
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        
        if (slot == BACK_SLOT) {
            guiManager.openMainMenu(player);
            return;
        }
        
        if (slot == CREATE_AUCTION_SLOT) {
            handleCreateAuction();
            return;
        }
        
        if (slot == MY_AUCTIONS_SLOT) {
            currentFilter = AuctionFilter.MY_AUCTIONS;
            refresh();
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            return;
        }
        
        if (slot == FILTER_ALL_SLOT) {
            currentFilter = AuctionFilter.ALL;
            refresh();
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            return;
        }
        
        if (slot == FILTER_ENDING_SLOT) {
            currentFilter = AuctionFilter.ENDING_SOON;
            refresh();
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            return;
        }
        
        if (slot == FILTER_NO_BIDS_SLOT) {
            currentFilter = AuctionFilter.NO_BIDS;
            refresh();
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            return;
        }
        
        if (slot == REFRESH_SLOT) {
            refresh();
            GUIUtils.playSound(player, GUIUtils.SUCCESS_SOUND);
            GUIUtils.sendMessage(player, "&aAuction listings refreshed!");
            return;
        }
        
        // Handle pagination and items
        super.handleClick(event);
    }
    
    private void handleCreateAuction() {
        player.closeInventory();
        
        // Show available apartments to auction
        List<Apartment> eligibleApartments = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> player.getUniqueId().equals(a.owner))
                .filter(a -> a.getTotalUnpaid() <= 0) // No unpaid taxes
                .filter(a -> auctionManager.getAuction(a.id) == null) // Not already being auctioned
                .collect(Collectors.toList());
        
        if (eligibleApartments.isEmpty()) {
            GUIUtils.sendMessage(player, "&cYou have no apartments eligible for auction!");
            GUIUtils.sendMessage(player, "&7Requirements: Own the apartment, no unpaid taxes, not already auctioned");
            return;
        }
        
        GUIUtils.sendMessage(player, "&eYour eligible apartments for auction:");
        for (Apartment apt : eligibleApartments) {
            GUIUtils.sendMessage(player, "&7• &f" + apt.displayName + " &7(ID: " + apt.id + ")");
        }
        GUIUtils.sendMessage(player, "&eUse: &f/apartmentcore auction create <apartment_id> <starting_bid> <duration_hours>");
    }
    
    private void handlePlaceBid(ApartmentAuction auction) {
        double nextBidAmount = auction.totalBids == 0 ? 
                auction.startingBid : 
                auction.currentBid + plugin.getConfigManager().getAuctionMinBidIncrement();
        
        player.closeInventory();
        
        if (!plugin.getEconomy().has(player, nextBidAmount)) {
            GUIUtils.sendMessage(player, "&cInsufficient funds! Need: " + plugin.getConfigManager().formatMoney(nextBidAmount));
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }
        
        // Place the bid
        boolean success = auctionManager.placeBid(player, auction.apartmentId, nextBidAmount);
        if (!success) {
            GUIUtils.sendMessage(player, "&cFailed to place bid! The auction may have ended or been outbid.");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
        }
    }
    
    private void handleCancelAuction(ApartmentAuction auction) {
        player.closeInventory();
        
        if (auction.totalBids > 0) {
            GUIUtils.sendMessage(player, "&cCannot cancel auction with existing bids!");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }
        
        boolean success = auctionManager.cancelAuction(player, auction.apartmentId);
        if (!success) {
            GUIUtils.sendMessage(player, "&cFailed to cancel auction!");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
        }
    }
    
    private void handleViewAuctionDetails(ApartmentAuction auction) {
        // For now, show detailed info in chat
        // Could be expanded to open a detailed GUI later
        player.closeInventory();
        
        Apartment apartment = plugin.getApartmentManager().getApartment(auction.apartmentId);
        String apartmentName = apartment != null ? apartment.displayName : auction.apartmentId;
        
        GUIUtils.sendMessage(player, "&6=== Auction Details: " + apartmentName + " ===");
        GUIUtils.sendMessage(player, "&7Seller: &f" + auction.ownerName);
        GUIUtils.sendMessage(player, "&7Starting Bid: &a" + plugin.getConfigManager().formatMoney(auction.startingBid));
        GUIUtils.sendMessage(player, "&7Current Bid: &a" + plugin.getConfigManager().formatMoney(auction.currentBid));
        GUIUtils.sendMessage(player, "&7Total Bids: &f" + auction.totalBids);
        GUIUtils.sendMessage(player, "&7Time Remaining: &e" + GUIUtils.formatTime(auction.getRemainingTime()));
        
        if (auction.currentBidderId != null) {
            GUIUtils.sendMessage(player, "&7Current Bidder: &f" + auction.currentBidderName);
        }
        
        if (apartment != null) {
            GUIUtils.sendMessage(player, "&7Level: &f" + apartment.level + "/5");
            LevelConfig levelConfig = plugin.getConfigManager().getLevelConfig(apartment.level);
            if (levelConfig != null) {
                GUIUtils.sendMessage(player, "&7Income: &a" + plugin.getConfigManager().formatMoney(levelConfig.minIncome) + 
                                   " &7- &a" + plugin.getConfigManager().formatMoney(levelConfig.maxIncome) + "/hour");
            }
        }
        
        if (!auction.ownerId.equals(player.getUniqueId()) && player.hasPermission("apartmentcore.auction.bid")) {
            double nextBid = auction.totalBids == 0 ? auction.startingBid : auction.currentBid + plugin.getConfigManager().getAuctionMinBidIncrement();
            GUIUtils.sendMessage(player, "&eUse: &f/apartmentcore auction bid " + auction.apartmentId + " " + String.format("%.2f", nextBid));
        }
    }
    
    private enum AuctionFilter {
        ALL, ENDING_SOON, NO_BIDS, MY_AUCTIONS
    }
}