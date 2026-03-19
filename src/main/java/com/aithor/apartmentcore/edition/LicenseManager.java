package com.aithor.apartmentcore.edition;

import com.aithor.apartmentcore.ApartmentCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages license validation for the Pro edition.
 * <p>
 * On startup the manager reads the license key from {@code config.yml}
 * ({@code license.key}) and validates it against a remote Cloudflare Worker
 * endpoint. A cached validation result is stored locally so the plugin
 * can start even when the server has no outbound connectivity (grace period).
 * <p>
 * This class is only active when the compiled edition is {@link Edition#PRO}.
 * The Free edition skips license validation entirely.
 */
public class LicenseManager {

    // ── Configuration ───────────────────────────────────────────────────────
    /**
     * The remote validation endpoint (Cloudflare Worker).
     * Replace this URL with your own deployed worker.
     */
    private static final String VALIDATE_URL = "https://apartmentcore-license.your-worker.workers.dev/api/validate";

    /**
     * How many days the plugin may run with a cached (offline) validation
     * before it forces a re-check.
     */
    private static final int GRACE_PERIOD_DAYS = 7;

    // ── State ───────────────────────────────────────────────────────────────
    private final ApartmentCore plugin;
    private String licenseKey;
    private LicenseStatus status = LicenseStatus.UNCHECKED;
    private String statusMessage = "";
    private long lastValidatedAt = 0L;

    public enum LicenseStatus {
        UNCHECKED,
        VALID,
        INVALID,
        EXPIRED,
        GRACE_PERIOD,
        ERROR
    }

    public LicenseManager(ApartmentCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Run the full license check sequence.
     * Should be called asynchronously during {@code onEnable}.
     */
    public void initialize() {
        this.licenseKey = plugin.getConfig().getString("license.key", "").trim();

        if (licenseKey.isEmpty()) {
            status = LicenseStatus.INVALID;
            statusMessage = "No license key configured. Add 'license.key' to config.yml.";
            printLicenseStatus();
            return;
        }

        // Try to load cached validation
        loadCache();

        // Validate asynchronously so we don't block the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                validateRemote();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "License validation failed: " + t.getMessage());
                handleOfflineFallback();
            }
            // Print result on main thread
            Bukkit.getScheduler().runTask(plugin, this::printLicenseStatus);
        });
    }

    /**
     * Attempt remote validation against the Cloudflare Worker API.
     */
    private void validateRemote() throws IOException {
        String serverIp = Bukkit.getIp().isEmpty() ? "0.0.0.0" : Bukkit.getIp();
        int serverPort = Bukkit.getPort();
        String serverId = generateServerId();

        String jsonBody = "{"
                + "\"license_key\":\"" + escapeJson(licenseKey) + "\","
                + "\"server_ip\":\"" + escapeJson(serverIp) + "\","
                + "\"server_port\":" + serverPort + ","
                + "\"server_id\":\"" + escapeJson(serverId) + "\","
                + "\"plugin_version\":\"" + escapeJson(plugin.getDescription().getVersion()) + "\""
                + "}";

        HttpURLConnection conn = (HttpURLConnection) new URL(VALIDATE_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "ApartmentCore/" + plugin.getDescription().getVersion());
        conn.setDoOutput(true);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        String body = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());

        if (code == 200) {
            if (body.contains("\"valid\":true") || body.contains("\"status\":\"active\"")) {
                status = LicenseStatus.VALID;
                statusMessage = "License validated successfully.";
                lastValidatedAt = System.currentTimeMillis();
                saveCache();
            } else if (body.contains("\"expired\":true") || body.contains("\"status\":\"expired\"")) {
                status = LicenseStatus.EXPIRED;
                statusMessage = "License has expired. Please renew your license.";
            } else {
                status = LicenseStatus.INVALID;
                statusMessage = "License key is not valid.";
            }
        } else if (code == 403 || code == 401) {
            status = LicenseStatus.INVALID;
            statusMessage = "License rejected by server (HTTP " + code + ").";
        } else {
            throw new IOException("Unexpected HTTP " + code + ": " + body);
        }
    }

    /**
     * When the remote check fails (network error), check if we have a recent
     * cached validation to fall back on.
     */
    private void handleOfflineFallback() {
        if (lastValidatedAt > 0) {
            long daysSince = (System.currentTimeMillis() - lastValidatedAt) / (1000L * 60 * 60 * 24);
            if (daysSince <= GRACE_PERIOD_DAYS) {
                status = LicenseStatus.GRACE_PERIOD;
                statusMessage = "Offline mode – cached validation (" + daysSince + "d ago). "
                        + "Re-validation required within " + (GRACE_PERIOD_DAYS - daysSince) + " days.";
            } else {
                status = LicenseStatus.ERROR;
                statusMessage = "Cached validation expired. Please connect to the internet to re-validate.";
            }
        } else {
            status = LicenseStatus.ERROR;
            statusMessage = "Cannot reach license server and no cached validation found.";
        }
    }

    // ── Cache persistence ───────────────────────────────────────────────────

    private File getCacheFile() {
        return new File(plugin.getDataFolder(), ".license_cache");
    }

    private void saveCache() {
        try {
            File f = getCacheFile();
            if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
            try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                pw.println(lastValidatedAt);
                pw.println(hashKey(licenseKey));
            }
        } catch (IOException ignored) {
        }
    }

    private void loadCache() {
        File f = getCacheFile();
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            lastValidatedAt = Long.parseLong(br.readLine().trim());
            String cachedHash = br.readLine().trim();
            if (!cachedHash.equals(hashKey(licenseKey))) {
                // Key changed – invalidate cache
                lastValidatedAt = 0;
            }
        } catch (Throwable ignored) {
            lastValidatedAt = 0;
        }
    }

    // ── Console output ──────────────────────────────────────────────────────

    private void printLicenseStatus() {
        switch (status) {
            case VALID:
                plugin.getLogger().info("License: VALID - " + statusMessage);
                break;
            case GRACE_PERIOD:
                plugin.getLogger().warning("License: GRACE PERIOD - " + statusMessage);
                break;
            case INVALID:
                plugin.getLogger().severe("License: INVALID - " + statusMessage);
                plugin.getLogger().severe("Pro features are DISABLED. The plugin will run in Free mode.");
                break;
            case EXPIRED:
                plugin.getLogger().severe("License: EXPIRED - " + statusMessage);
                plugin.getLogger().severe("Pro features are DISABLED. The plugin will run in Free mode.");
                break;
            case ERROR:
                plugin.getLogger().severe("License: ERROR - " + statusMessage);
                plugin.getLogger().severe("Pro features are DISABLED. The plugin will run in Free mode.");
                break;
            default:
                plugin.getLogger().warning("License: UNCHECKED");
                break;
        }
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public LicenseStatus getStatus() {
        return status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    /**
     * Whether the license allows Pro features to be active.
     * Only VALID and GRACE_PERIOD statuses grant Pro access.
     */
    public boolean isLicenseActive() {
        return status == LicenseStatus.VALID || status == LicenseStatus.GRACE_PERIOD;
    }

    public String getLicenseKey() {
        return licenseKey;
    }

    // ── Utility ─────────────────────────────────────────────────────────────

    private String generateServerId() {
        try {
            String raw = Bukkit.getIp() + ":" + Bukkit.getPort() + ":" + Bukkit.getMotd();
            return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static String hashKey(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            return key;
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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
