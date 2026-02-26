package com.aithor.apartmentcorei3.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an apartment auction
 */
public class ApartmentAuction {
    public String apartmentId;
    public UUID ownerId;
    public String ownerName;
    public double startingBid;
    public double currentBid;
    public UUID currentBidderId;
    public String currentBidderName;
    public long startTime;
    public long endTime;
    public boolean active;
    public boolean ended;
    public int totalBids;

    public ApartmentAuction() {
    }

    public ApartmentAuction(String apartmentId, UUID ownerId, String ownerName,
                            double startingBid, long durationHours) {
        this.apartmentId = apartmentId;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.startingBid = startingBid;
        this.currentBid = startingBid;
        this.currentBidderId = null;
        this.currentBidderName = "";
        this.startTime = System.currentTimeMillis();
        this.endTime = startTime + (durationHours * 60 * 60 * 1000);
        this.active = true;
        this.ended = false;
        this.totalBids = 0;
    }

    /**
     * Check if auction is still active
     */
    public boolean isActive() {
        if (!active || ended) return false;
        return System.currentTimeMillis() < endTime;
    }

    /**
     * Check if auction has ended
     */
    public boolean hasEnded() {
        return ended || System.currentTimeMillis() >= endTime;
    }

    /**
     * Get remaining time in milliseconds
     */
    public long getRemainingTime() {
        if (hasEnded()) return 0;
        return Math.max(0, endTime - System.currentTimeMillis());
    }

    /**
     * Get auction duration in milliseconds
     */
    public long getDuration() {
        return endTime - startTime;
    }

    /**
     * Place a bid
     */
    public boolean placeBid(UUID bidderId, String bidderName, double amount) {
        if (!isActive() || amount <= currentBid) {
            return false;
        }

        this.currentBid = amount;
        this.currentBidderId = bidderId;
        this.currentBidderName = bidderName;
        this.totalBids++;
        return true;
    }

    /**
     * End the auction
     */
    public void endAuction() {
        this.active = false;
        this.ended = true;
    }

    /**
     * Cancel the auction
     */
    public void cancelAuction() {
        this.active = false;
        this.ended = true;
    }

    /**
     * Serialize auction data for storage
     */
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("apartment-id", apartmentId);
        map.put("owner-id", ownerId.toString());
        map.put("owner-name", ownerName);
        map.put("starting-bid", startingBid);
        map.put("current-bid", currentBid);
        map.put("current-bidder-id", currentBidderId != null ? currentBidderId.toString() : "");
        map.put("current-bidder-name", currentBidderName);
        map.put("start-time", startTime);
        map.put("end-time", endTime);
        map.put("active", active);
        map.put("ended", ended);
        map.put("total-bids", totalBids);
        return map;
    }

    /**
     * Deserialize auction data from storage
     */
    public static ApartmentAuction deserialize(Map<String, Object> map) {
        ApartmentAuction auction = new ApartmentAuction();
        auction.apartmentId = (String) map.get("apartment-id");
        auction.ownerId = UUID.fromString((String) map.get("owner-id"));
        auction.ownerName = (String) map.get("owner-name");
        auction.startingBid = ((Number) map.get("starting-bid")).doubleValue();
        auction.currentBid = ((Number) map.get("current-bid")).doubleValue();

        String bidderIdStr = (String) map.get("current-bidder-id");
        auction.currentBidderId = (bidderIdStr != null && !bidderIdStr.isEmpty())
            ? UUID.fromString(bidderIdStr) : null;
        auction.currentBidderName = (String) map.getOrDefault("current-bidder-name", "");

        auction.startTime = ((Number) map.get("start-time")).longValue();
        auction.endTime = ((Number) map.get("end-time")).longValue();
        auction.active = (boolean) map.getOrDefault("active", false);
        auction.ended = (boolean) map.getOrDefault("ended", false);
        auction.totalBids = ((Number) map.getOrDefault("total-bids", 0)).intValue();

        return auction;
    }
}