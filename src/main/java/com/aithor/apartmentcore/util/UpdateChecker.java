package com.aithor.apartmentcore.util;

import com.aithor.apartmentcore.ApartmentCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Checks for available plugin updates against the remote Cloudflare Worker
 * endpoint which fetches the latest version from the Supabase {@code plugin_metadata} table.
 *
 * <p>The check runs asynchronously on startup and then every {@link #CHECK_INTERVAL_TICKS} ticks
 * (default 12 hours). When a newer version is found, the result is cached so that
 * {@link UpdateNotifyListener} can notify admins who join the server.
 */
public class UpdateChecker {

    // ── Configuration ────────────────────────────────────────────────────────

    /** Cloudflare Worker endpoint that returns latest version info (JSON). */
    private static final String VERSION_CHECK_URL =
            "https://apartmentcore-license.apartmentcore-license.workers.dev/api/version";

    /** How often to re-check for updates. 864000 ticks = 12 hours. */
    private static final long CHECK_INTERVAL_TICKS = 864_000L;

    /** Connection / read timeout for the HTTP request (ms). */
    private static final int TIMEOUT_MS = 8_000;

    // ── State ────────────────────────────────────────────────────────────────

    private final ApartmentCore plugin;

    /** The version currently running (read from plugin.yml). */
    private final String currentVersion;

    /** Latest version string returned by the API, or {@code null} if unknown. */
    private volatile String latestVersion = null;

    /** Whether a newer version is available. */
    private volatile boolean updateAvailable = false;

    /** Download / changelog URL returned by the API, or {@code null}. */
    private volatile String downloadUrl = null;

    /** Human-readable changelog summary returned by the API, may be {@code null}. */
    private volatile String changelogSummary = null;

    // ── Constructor ──────────────────────────────────────────────────────────

    public UpdateChecker(ApartmentCore plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Start the update checker: performs an immediate check and then schedules
     * a repeating check every {@link #CHECK_INTERVAL_TICKS} ticks.
     */
    public void start() {
        // Immediate first check
        runCheckAsync();

        // Periodic re-check (e.g. every 12 hours for long-running servers)
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::runCheckAsync,
                CHECK_INTERVAL_TICKS,
                CHECK_INTERVAL_TICKS
        );

        plugin.debug("[UpdateChecker] Started. Checking every " + (CHECK_INTERVAL_TICKS / 72_000) + " hours.");
    }

    /** @return {@code true} if a newer version is available. */
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    /** @return The latest version string from the API, or {@code null} if unknown. */
    public String getLatestVersion() {
        return latestVersion;
    }

    /** @return The current (running) plugin version. */
    public String getCurrentVersion() {
        return currentVersion;
    }

    /** @return The download URL, or {@code null}. */
    public String getDownloadUrl() {
        return downloadUrl;
    }

    /** @return Short changelog summary, or {@code null}. */
    public String getChangelogSummary() {
        return changelogSummary;
    }

    /**
     * Send the update notification to a specific player (admin join notification).
     *
     * @param player The player to notify.
     */
    public void notifyPlayer(Player player) {
        if (!updateAvailable) return;

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "  [ApartmentCore] Update Available!");
        player.sendMessage(ChatColor.YELLOW + "  Current: " + ChatColor.WHITE + "v" + currentVersion
                + ChatColor.YELLOW + "  →  Latest: " + ChatColor.GREEN + "v" + latestVersion);
        if (changelogSummary != null && !changelogSummary.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  What's new: " + ChatColor.WHITE + changelogSummary);
        }
        if (downloadUrl != null && !downloadUrl.isEmpty()) {
            player.sendMessage(ChatColor.AQUA + "  Download: " + ChatColor.WHITE + downloadUrl);
        }
        player.sendMessage("");
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /**
     * Dispatch the version check on the current thread (must already be async).
     */
    private void runCheckAsync() {
        try {
            performCheck();
        } catch (Throwable t) {
            plugin.debug("[UpdateChecker] Check failed: " + t.getMessage());
        }
    }

    /**
     * Performs the HTTP request to the Cloudflare Worker and parses the JSON response.
     *
     * <p>Expected JSON shape (minimal):
     * <pre>
     * {
     *   "latest_version": "1.4.0",
     *   "download_url": "https://example.com/download",
     *   "changelog_summary": "Fixed auction bug, improved performance."
     * }
     * </pre>
     */
    private void performCheck() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(VERSION_CHECK_URL).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "ApartmentCore/" + currentVersion);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);

        int code = conn.getResponseCode();
        if (code != 200) {
            plugin.debug("[UpdateChecker] Server returned HTTP " + code + ". Skipping update check.");
            return;
        }

        String body = readStream(conn.getInputStream());
        plugin.debug("[UpdateChecker] Response: " + body);

        // Simple JSON field extraction (no external library needed)
        String newLatest = extractJsonString(body, "latest_version");
        if (newLatest == null || newLatest.isEmpty()) {
            plugin.debug("[UpdateChecker] 'latest_version' field missing in response.");
            return;
        }

        latestVersion = newLatest;
        downloadUrl = extractJsonString(body, "download_url");
        changelogSummary = extractJsonString(body, "changelog_summary");

        boolean newer = isNewerVersion(newLatest, currentVersion);
        updateAvailable = newer;

        // Log result on main thread so Bukkit logger is safe
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (newer) {
                plugin.getLogger().info(
                        "[UpdateChecker] A new version is available: v" + newLatest
                        + " (current: v" + currentVersion + ").");
                plugin.getLogger().info("[UpdateChecker] What's new: " + changelogSummary);
                if (downloadUrl != null) {
                    plugin.getLogger().info("[UpdateChecker] Download: " + downloadUrl);
                }
            } else {
                plugin.debug("[UpdateChecker] Plugin is up-to-date (v" + currentVersion + ").");
            }
        });
    }

    // ── Version comparison ───────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code remote} is strictly newer than {@code local}.
     * Handles standard Semantic Versioning (MAJOR.MINOR.PATCH).
     * Non-numeric parts are compared lexicographically as a fallback.
     */
    static boolean isNewerVersion(String remote, String local) {
        try {
            int[] r = parseVersion(remote);
            int[] l = parseVersion(local);
            int length = Math.max(r.length, l.length);
            for (int i = 0; i < length; i++) {
                int rv = i < r.length ? r[i] : 0;
                int lv = i < l.length ? l[i] : 0;
                if (rv > lv) return true;
                if (rv < lv) return false;
            }
            return false; // equal
        } catch (Throwable t) {
            // Lexicographic fallback
            return remote.compareTo(local) > 0;
        }
    }

    private static int[] parseVersion(String version) {
        // Strip any leading 'v'
        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }
        String[] parts = version.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            // Strip anything non-numeric after the number (e.g. "1.4.0-beta" -> 0)
            nums[i] = Integer.parseInt(parts[i].replaceAll("[^0-9].*", ""));
        }
        return nums;
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    /**
     * Naive JSON string field extractor — avoids pulling in an external JSON library.
     * Works for flat JSON objects with string values.
     */
    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        // Skip whitespace after the colon
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t'
                || json.charAt(start) == '\n' || json.charAt(start) == '\r')) {
            start++;
        }
        if (start >= json.length()) return null;
        if (json.charAt(start) == '"') {
            // String value
            int end = json.indexOf('"', start + 1);
            if (end < 0) return null;
            return json.substring(start + 1, end);
        }
        // Non-string value (number / boolean) – unlikely for version but handle gracefully
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        return json.substring(start, end).trim();
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
