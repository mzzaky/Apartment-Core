package com.aithor.apartmentcore.util;

import com.aithor.apartmentcore.ApartmentCore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listens for player join events and notifies administrators when a new
 * version of ApartmentCore is available.
 *
 * <p>The notification is only sent once per login session so it is not
 * repeated every time the player sends a command or opens a GUI.
 */
public class UpdateNotifyListener implements Listener {

    private final ApartmentCore plugin;
    private final UpdateChecker updateChecker;

    public UpdateNotifyListener(ApartmentCore plugin, UpdateChecker updateChecker) {
        this.plugin = plugin;
        this.updateChecker = updateChecker;
    }

    /**
     * Notify the joining player if they have admin permission and an update is available.
     *
     * <p>The notification is delayed by 40 ticks (2 seconds) so it appears after
     * the standard join messages and is clearly visible.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!updateChecker.isUpdateAvailable()) return;
        if (!event.getPlayer().hasPermission("apartmentcore.admin")) return;

        // Small delay so the message is not buried in join spam
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> {
                    if (event.getPlayer().isOnline()) {
                        updateChecker.notifyPlayer(event.getPlayer());
                    }
                }, 40L);
    }
}
