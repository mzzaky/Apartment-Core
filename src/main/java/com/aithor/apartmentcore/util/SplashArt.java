package com.aithor.apartmentcore.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Renders a stylised ASCII splash banner to the server console on plugin load.
 * All output is piped through the plugin's own {@link Logger} so log-level
 * filtering / formatting remains consistent with the rest of the plugin.
 */
public final class SplashArt {

    // ─────────────────────────────────────────────
    // ANSI colour codes (supported by most modern
    // server terminals; silently ignored otherwise)
    // ─────────────────────────────────────────────
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[36m";
    private static final String BLUE = "\u001B[34m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String WHITE = "\u001B[97m";
    private static final String DIM = "\u001B[2m";

    private SplashArt() {
        /* utility class – no instances */ }

    /**
     * Prints the splash banner to the console using the plugin's logger.
     *
     * @param plugin the owning plugin instance (used for version & author metadata)
     */
    public static void print(JavaPlugin plugin) {
        Logger log = plugin.getLogger();

        String version = plugin.getDescription().getVersion();
        String author = plugin.getDescription().getAuthors().isEmpty()
                ? "Aithor"
                : plugin.getDescription().getAuthors().get(0);

        // ── Banner lines ──────────────────────────────────────────────────────
        String[] banner = {
                CYAN + BOLD + "  ___                 _                          _    " + RESET,
                CYAN + BOLD + " / _ \\  _ __    __ _ | |_  _ __ ___    ___  _ __ | |_ " + RESET,
                CYAN + BOLD + "| | | || '_ \\  / _` || __|| '_ ` _ \\  / _ \\| '_ \\| __|" + RESET,
                CYAN + BOLD + "| |_| || |_) || (_| || |_ | | | | | ||  __/| | | | |_ " + RESET,
                CYAN + BOLD + " \\___/ | .__/  \\__,_| \\__||_| |_| |_| \\___||_| |_|\\__|" + RESET,
                CYAN + BOLD + "       |_|                                              " + RESET,
                BLUE + BOLD + "  ____                                                  " + RESET,
                BLUE + BOLD + " / ___|  ___   _ __  ___                                " + RESET,
                BLUE + BOLD + "| |     / _ \\ | '__|/ _ \\                               " + RESET,
                BLUE + BOLD + "| |___ | (_) || |  |  __/                               " + RESET,
                BLUE + BOLD + " \\____| \\___/ |_|   \\___|                               " + RESET,
        };

        String sep = DIM + "  " + "─".repeat(54) + RESET;
        String sep2 = DIM + "  " + "═".repeat(54) + RESET;

        log.info(sep2);
        for (String line : banner) {
            log.info(line);
        }
        log.info(sep);

        // ── Metadata block ────────────────────────────────────────────────────
        log.info(WHITE + BOLD + "  » Version  " + RESET + GREEN + BOLD + "v" + version + RESET);
        log.info(WHITE + BOLD + "  » Author   " + RESET + YELLOW + BOLD + author + RESET);
        log.info(WHITE + BOLD + "  » API      " + RESET + WHITE + "Spigot 1.21+");
        log.info(WHITE + BOLD + "  » Status   " + RESET + GREEN + BOLD + "● ONLINE" + RESET);

        log.info(sep);

        // ── Feature summary ───────────────────────────────────────────────────
        log.info(DIM + "  Modules loaded:" + RESET);
        log.info("    " + GREEN + "✔" + RESET + " Apartment Management System");
        log.info("    " + GREEN + "✔" + RESET + " Economy Integration  (Vault)");
        log.info("    " + GREEN + "✔" + RESET + " Tax & Invoice Engine");
        log.info("    " + GREEN + "✔" + RESET + " Auction House");
        log.info("    " + GREEN + "✔" + RESET + " Research & Upgrade System");
        log.info("    " + GREEN + "✔" + RESET + " Achievement Engine");
        log.info("    " + GREEN + "✔" + RESET + " GUI Framework");
        log.info("    " + GREEN + "✔" + RESET + " PlaceholderAPI Support");

        log.info(sep2);
        log.info(DIM + "  ApartmentCore is ready. Thank you for using this plugin!" + RESET);
        log.info(sep2);
    }
}
