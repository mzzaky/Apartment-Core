package com.aithor.apartmentcore.manager;

import com.aithor.apartmentcore.ApartmentCorei3;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages custom file logging with rotation and cleanup
 */
public class LoggerManager {
    private final ApartmentCorei3 plugin;
    private final ConfigManager config;
    private final SimpleDateFormat timestampFormat;
    private File logFile;
    private File logsDir;

    public LoggerManager(ApartmentCorei3 plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        initializeLogging();
    }

    /**
     * Initialize logging directory and file
     */
    private void initializeLogging() {
        try {
            // Create logs directory if it doesn't exist
            logsDir = new File(plugin.getDataFolder(), "logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
                plugin.getLogger().info("Created logs directory: " + logsDir.getAbsolutePath());
            }

            // Set log file path
            logFile = new File(plugin.getDataFolder(), config.getLogFile());
            if (!logFile.getParentFile().exists()) {
                logFile.getParentFile().mkdirs();
            }

            // Log initialization
            log("INFO", "LoggerManager initialized. Log file: " + logFile.getAbsolutePath());

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize logging", e);
        }
    }

    /**
     * Log a general message
     */
    public void log(String message) {
        log("INFO", message);
    }

    /**
     * Log a message with specific level
     */
    public void log(String level, String message) {
        try {
            String timestamp = timestampFormat.format(new Date());
            String logEntry = String.format("[%s] [%s] %s%n", timestamp, level, message);

            // Check if rotation is needed
            checkRotation();

            // Write to file
            try (FileWriter fw = new FileWriter(logFile, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {
                out.print(logEntry);
            }

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write to log file", e);
        }
    }

    /**
     * Log a transaction if enabled
     */
    public void logTransaction(String message) {
        if (config.isLogTransactions()) {
            log("TRANSACTION", message);
        }
    }

    /**
     * Log an admin action if enabled
     */
    public void logAdminAction(String message) {
        if (config.isLogAdminActions()) {
            log("ADMIN", message);
        }
    }

    /**
     * Check if log rotation is needed and perform it
     */
    private void checkRotation() {
        if (!logFile.exists()) return;

        long fileSizeMB = logFile.length() / (1024 * 1024);
        if (fileSizeMB >= config.getMaxLogSize()) {
            rotateLogFile();
        }
    }

    /**
     * Rotate the log file
     */
    private void rotateLogFile() {
        try {
            // Create backup filename with timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            File backupFile = new File(logsDir, "apartmentcore_" + timestamp + ".log");

            // Move current log to backup
            Files.move(logFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Clean up old logs if needed
            if (config.isKeepOldLogs()) {
                cleanupOldLogs();
            }

            log("INFO", "Log file rotated to: " + backupFile.getName());

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to rotate log file", e);
        }
    }

    /**
     * Clean up old log files, keeping only the specified maximum number
     */
    private void cleanupOldLogs() {
        try {
            File[] logFiles = logsDir.listFiles((dir, name) -> name.startsWith("apartmentcore_") && name.endsWith(".log"));
            if (logFiles == null || logFiles.length <= config.getMaxOldLogs()) {
                return;
            }

            // Sort by last modified (oldest first)
            Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified));

            // Delete oldest files
            int filesToDelete = logFiles.length - config.getMaxOldLogs();
            for (int i = 0; i < filesToDelete; i++) {
                if (logFiles[i].delete()) {
                    plugin.getLogger().info("Deleted old log file: " + logFiles[i].getName());
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to cleanup old log files", e);
        }
    }

    /**
     * Get current log file
     */
    public File getLogFile() {
        return logFile;
    }

    /**
     * Get logs directory
     */
    public File getLogsDir() {
        return logsDir;
    }
}