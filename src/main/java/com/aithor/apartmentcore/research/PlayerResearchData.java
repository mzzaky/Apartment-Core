package com.aithor.apartmentcore.research;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores research progress data for a single player.
 * Tracks completed tier levels and any currently active (in-progress) research.
 */
public class PlayerResearchData {

    private final UUID playerId;

    /** Completed tier per research type (0 = none completed, 1-5 = completed tier) */
    private final Map<ResearchType, Integer> completedTiers;

    /** Currently active research (null if none) */
    private ResearchType activeResearch;

    /** Tier being researched (the NEXT tier, i.e. completedTier + 1) */
    private int activeTier;

    /** Timestamp (epoch millis) when the active research started */
    private long researchStartTime;

    /** Duration in milliseconds required for the active research to complete */
    private long researchDuration;

    public PlayerResearchData(UUID playerId) {
        this.playerId = playerId;
        this.completedTiers = new HashMap<>();
        for (ResearchType type : ResearchType.values()) {
            completedTiers.put(type, 0);
        }
        this.activeResearch = null;
        this.activeTier = 0;
        this.researchStartTime = 0L;
        this.researchDuration = 0L;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    // --- Completed tiers ---

    public int getCompletedTier(ResearchType type) {
        return completedTiers.getOrDefault(type, 0);
    }

    public void setCompletedTier(ResearchType type, int tier) {
        completedTiers.put(type, Math.max(0, Math.min(type.getMaxTier(), tier)));
    }

    public boolean isMaxTier(ResearchType type) {
        return getCompletedTier(type) >= type.getMaxTier();
    }

    public Map<ResearchType, Integer> getCompletedTiers() {
        return new HashMap<>(completedTiers);
    }

    // --- Active research ---

    public boolean hasActiveResearch() {
        return activeResearch != null;
    }

    public ResearchType getActiveResearch() {
        return activeResearch;
    }

    public int getActiveTier() {
        return activeTier;
    }

    public long getResearchStartTime() {
        return researchStartTime;
    }

    public long getResearchDuration() {
        return researchDuration;
    }

    /**
     * Start researching a new tier.
     */
    public void startResearch(ResearchType type, int tier, long durationMillis) {
        this.activeResearch = type;
        this.activeTier = tier;
        this.researchStartTime = System.currentTimeMillis();
        this.researchDuration = durationMillis;
    }

    /**
     * Check if the active research has finished.
     */
    public boolean isResearchComplete() {
        if (activeResearch == null) return false;
        return System.currentTimeMillis() >= researchStartTime + researchDuration;
    }

    /**
     * Get remaining time in milliseconds for the active research.
     */
    public long getRemainingTime() {
        if (activeResearch == null) return 0L;
        long end = researchStartTime + researchDuration;
        return Math.max(0L, end - System.currentTimeMillis());
    }

    /**
     * Complete the active research: increment the completed tier and clear active state.
     */
    public void completeResearch() {
        if (activeResearch != null) {
            completedTiers.put(activeResearch, activeTier);
            activeResearch = null;
            activeTier = 0;
            researchStartTime = 0L;
            researchDuration = 0L;
        }
    }

    /**
     * Cancel the active research without completing it.
     */
    public void cancelResearch() {
        activeResearch = null;
        activeTier = 0;
        researchStartTime = 0L;
        researchDuration = 0L;
    }

    /**
     * Restore active research state from saved data (used during load).
     */
    public void restoreActiveResearch(ResearchType type, int tier, long startTime, long duration) {
        this.activeResearch = type;
        this.activeTier = tier;
        this.researchStartTime = startTime;
        this.researchDuration = duration;
    }
}
