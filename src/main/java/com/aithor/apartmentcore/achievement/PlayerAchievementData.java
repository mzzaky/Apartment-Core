package com.aithor.apartmentcore.achievement;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores achievement progress and completion data for a single player.
 */
public class PlayerAchievementData {

    private final UUID playerId;

    /** Current progress value per achievement type */
    private final Map<AchievementType, Double> progress;

    /** Whether the achievement has been completed (unlocked) */
    private final Map<AchievementType, Boolean> completed;

    /** Timestamp of when the achievement was completed (0 = not completed) */
    private final Map<AchievementType, Long> completedAt;

    public PlayerAchievementData(UUID playerId) {
        this.playerId = playerId;
        this.progress = new EnumMap<>(AchievementType.class);
        this.completed = new EnumMap<>(AchievementType.class);
        this.completedAt = new EnumMap<>(AchievementType.class);

        for (AchievementType type : AchievementType.values()) {
            progress.put(type, 0.0);
            completed.put(type, false);
            completedAt.put(type, 0L);
        }
    }

    public UUID getPlayerId() {
        return playerId;
    }

    // --- Progress ---

    public double getProgress(AchievementType type) {
        return progress.getOrDefault(type, 0.0);
    }

    public void setProgress(AchievementType type, double value) {
        progress.put(type, value);
    }

    public void addProgress(AchievementType type, double amount) {
        progress.put(type, getProgress(type) + amount);
    }

    // --- Completion ---

    public boolean isCompleted(AchievementType type) {
        return completed.getOrDefault(type, false);
    }

    public void setCompleted(AchievementType type, boolean value) {
        completed.put(type, value);
        if (value && getCompletedAt(type) == 0L) {
            completedAt.put(type, System.currentTimeMillis());
        }
    }

    public long getCompletedAt(AchievementType type) {
        return completedAt.getOrDefault(type, 0L);
    }

    public void setCompletedAt(AchievementType type, long timestamp) {
        completedAt.put(type, timestamp);
    }

    /**
     * Get the number of completed achievements.
     */
    public int getCompletedCount() {
        int count = 0;
        for (Boolean b : completed.values()) {
            if (b) count++;
        }
        return count;
    }

    /**
     * Get total number of achievements.
     */
    public int getTotalCount() {
        return AchievementType.values().length;
    }
}
