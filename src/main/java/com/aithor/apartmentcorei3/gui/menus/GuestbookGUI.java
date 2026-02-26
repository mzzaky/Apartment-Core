package com.aithor.apartmentcorei3.gui.menus;

import com.aithor.apartmentcorei3.ApartmentCorei3;
import com.aithor.apartmentcorei3.model.Apartment;
import com.aithor.apartmentcorei3.model.GuestBookEntry;
import com.aithor.apartmentcorei3.gui.GUIManager;
import com.aithor.apartmentcorei3.gui.interfaces.PaginatedGUI;
import com.aithor.apartmentcorei3.gui.items.GUIItem;
import com.aithor.apartmentcorei3.gui.items.ItemBuilder;
import com.aithor.apartmentcorei3.gui.utils.GUIUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GUI for viewing and managing apartment guestbooks
 */
public class GuestbookGUI extends PaginatedGUI {
    
    private final ApartmentCorei3 plugin;
    private final GUIManager guiManager;
    private final String apartmentId;
    
    // Action slots
    private static final int BACK_SLOT = 0;
    private static final int LEAVE_MESSAGE_SLOT = 1;
    private static final int CLEAR_ALL_SLOT = 2;
    private static final int GUESTBOOK_INFO_SLOT = 7;
    
    public GuestbookGUI(Player player, ApartmentCorei3 plugin, GUIManager guiManager, String apartmentId) {
        super(player, ChatColor.DARK_AQUA + "Guestbook", 54, 28);
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.apartmentId = apartmentId;
    }
    
    @Override
    protected List<GUIItem> loadItems() {
        List<GuestBookEntry> entries = plugin.getApartmentManager().getGuestBooks().get(apartmentId);
        
        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Sort by timestamp (newest first)
        List<GuestBookEntry> sortedEntries = entries.stream()
                .sorted(Comparator.comparingLong((GuestBookEntry e) -> e.timestamp).reversed())
                .collect(Collectors.toList());
        
        List<GUIItem> items = new ArrayList<>();
        for (int i = 0; i < sortedEntries.size(); i++) {
            GuestBookEntry entry = sortedEntries.get(i);
            items.add(createMessageItem(entry, i + 1));
        }
        
        return items;
    }
    
    @Override
    protected void setupInventory() {
        super.setupInventory();
        addActionButtons();
    }
    
    private void addActionButtons() {
        Apartment apartment = plugin.getApartmentManager().getApartment(apartmentId);
        if (apartment == null) {
            return;
        }
        
        boolean isOwner = apartment.owner != null && apartment.owner.equals(player.getUniqueId());
        
        // Back button
        ItemStack backItem = new ItemBuilder(Material.ARROW)
                .name("&c◀ Back")
                .lore("&7Return to apartment details")
                .build();
        inventory.setItem(BACK_SLOT, backItem);
        
        // Leave Message button
        boolean canLeaveMessage = !isOwner; // Usually visitors leave messages
        long cooldown = getCooldownRemaining();
        boolean onCooldown = cooldown > 0;
        
        Material leaveMaterial = canLeaveMessage && !onCooldown ? Material.WRITABLE_BOOK : Material.GRAY_CONCRETE;
        List<String> leaveLore = new ArrayList<>();
        leaveLore.add("&7Leave a message in this guestbook");
        leaveLore.add("");
        
        if (onCooldown) {
            leaveLore.add("&cCooldown: " + GUIUtils.formatTime(cooldown));
        } else if (canLeaveMessage) {
            leaveLore.add("&7Max Length: &f" + plugin.getConfigManager().getGuestBookMaxMessageLength() + " characters");
            leaveLore.add("&7Cooldown: &f" + plugin.getConfigManager().getGuestBookLeaveCooldown() + " seconds");
            leaveLore.add("");
            leaveLore.add("&a▶ Click to leave message");
        } else {
            leaveLore.add("&7Owners typically don't leave messages");
            leaveLore.add("&7in their own guestbooks");
        }
        
        ItemStack leaveItem = new ItemBuilder(leaveMaterial)
                .name("&a✏️ Leave Message")
                .lore(leaveLore)
                .build();
        inventory.setItem(LEAVE_MESSAGE_SLOT, leaveItem);
        
        // Clear All button (owners only)
        if (isOwner) {
            List<GuestBookEntry> entries = plugin.getApartmentManager().getGuestBooks().get(apartmentId);
            int messageCount = entries != null ? entries.size() : 0;
            
            Material clearMaterial = messageCount > 0 ? Material.RED_CONCRETE : Material.GRAY_CONCRETE;
            ItemStack clearItem = new ItemBuilder(clearMaterial)
                    .name("&c🗑️ Clear All Messages")
                    .lore(
                        "&7Clear all messages from the guestbook",
                        "",
                        "&7Current Messages: &f" + messageCount,
                        "",
                        messageCount > 0 ? "&c▶ Click to clear (requires confirmation)" : "&7No messages to clear"
                    )
                    .build();
            inventory.setItem(CLEAR_ALL_SLOT, clearItem);
        }
        
        // Guestbook Info
        List<GuestBookEntry> entries = plugin.getApartmentManager().getGuestBooks().get(apartmentId);
        int currentMessages = entries != null ? entries.size() : 0;
        int maxMessages = plugin.getConfigManager().getGuestBookMaxMessages();
        
        // Calculate usage percentage
        double usagePercent = (double) currentMessages / maxMessages * 100;
        String usageBar = GUIUtils.createProgressBar(currentMessages, maxMessages, 10);
        
        ItemStack infoItem = new ItemBuilder(Material.BOOK)
                .name("&6📊 Guestbook Information")
                .lore(
                    "&7Information about this guestbook",
                    "",
                    "&e📖 Usage Statistics:",
                    "&7• Messages: &f" + currentMessages + "&7/&f" + maxMessages,
                    "&7• Usage: " + usageBar + " &f" + String.format("%.1f%%", usagePercent),
                    "",
                    "&e⚙️ Settings:",
                    "&7• Max Message Length: &f" + plugin.getConfigManager().getGuestBookMaxMessageLength() + " chars",
                    "&7• Leave Cooldown: &f" + plugin.getConfigManager().getGuestBookLeaveCooldown() + " seconds",
                    "",
                    "&7When full, oldest messages are automatically",
                    "&7removed to make room for new ones."
                )
                .build();
        inventory.setItem(GUESTBOOK_INFO_SLOT, infoItem);
    }
    
    private GUIItem createMessageItem(GuestBookEntry entry, int messageNumber) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String formattedDate = dateFormat.format(new Date(entry.timestamp));
        
        // Calculate how long ago this message was posted
        long timeSince = System.currentTimeMillis() - entry.timestamp;
        String timeAgo = getTimeAgoString(timeSince);
        
        // Wrap long messages for better display
        List<String> wrappedMessage = GUIUtils.wrapText(entry.message, 30);
        
        // Choose material based on message age
        Material material;
        if (timeSince < 86400000) { // Less than 1 day
            material = Material.LIME_STAINED_GLASS;
        } else if (timeSince < 604800000) { // Less than 1 week
            material = Material.YELLOW_STAINED_GLASS;
        } else {
            material = Material.GRAY_STAINED_GLASS;
        }
        
        List<String> lore = new ArrayList<>();
        lore.add("&7Author: &f" + entry.senderName);
        lore.add("&7Posted: &f" + formattedDate);
        lore.add("&7Time Ago: &f" + timeAgo);
        lore.add("");
        lore.add("&e💬 Message:");
        
        // Add wrapped message lines
        for (String line : wrappedMessage) {
            lore.add("&f\"" + line + "\"");
        }
        
        // Add action hints for owners
        Apartment apartment = plugin.getApartmentManager().getApartment(apartmentId);
        if (apartment != null && apartment.owner != null && apartment.owner.equals(player.getUniqueId())) {
            lore.add("");
            lore.add("&7As the owner, you can:");
            lore.add("&7• View all messages");
            lore.add("&7• Clear individual or all messages");
        }
        
        ItemStack item = new ItemBuilder(material)
                .name("&6📝 Message #" + messageNumber)
                .lore(lore)
                .build();
        
        return new GUIItem(item, "message_" + entry.timestamp, entry);
    }
    
    private long getCooldownRemaining() {
        // This would need to be implemented similar to the existing cooldown system
        // For now, return 0 (no cooldown)
        return 0;
    }
    
    private String getTimeAgoString(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        long months = days / 30;
        long years = days / 365;
        
        if (years > 0) {
            return years + " year" + (years == 1 ? "" : "s") + " ago";
        } else if (months > 0) {
            return months + " month" + (months == 1 ? "" : "s") + " ago";
        } else if (weeks > 0) {
            return weeks + " week" + (weeks == 1 ? "" : "s") + " ago";
        } else if (days > 0) {
            return days + " day" + (days == 1 ? "" : "s") + " ago";
        } else if (hours > 0) {
            return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
        } else {
            return "Just now";
        }
    }
    
    @Override
    protected void handleItemClick(GUIItem item, InventoryClickEvent event) {
        // For now, just show message details
        // Could be expanded to allow individual message management
        GuestBookEntry entry = item.getData(GuestBookEntry.class);
        if (entry != null) {
            GUIUtils.sendMessage(player, "&eMessage from &f" + entry.senderName + "&e:");
            GUIUtils.sendMessage(player, "&f\"" + entry.message + "\"");
            
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            GUIUtils.sendMessage(player, "&7Posted on: " + dateFormat.format(new Date(entry.timestamp)));
        }
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        
        if (slot == BACK_SLOT) {
            guiManager.openApartmentDetails(player, apartmentId);
            return;
        }
        
        if (slot == LEAVE_MESSAGE_SLOT) {
            handleLeaveMessage();
            return;
        }
        
        if (slot == CLEAR_ALL_SLOT) {
            handleClearAll();
            return;
        }
        
        // Handle pagination and items
        super.handleClick(event);
    }
    
    private void handleLeaveMessage() {
        player.closeInventory();
        
        Apartment apartment = plugin.getApartmentManager().getApartment(apartmentId);
        if (apartment == null) {
            GUIUtils.sendMessage(player, "&cApartment not found!");
            return;
        }
        
        // Check cooldown
        long cooldown = getCooldownRemaining();
        if (cooldown > 0) {
            GUIUtils.sendMessage(player, "&cYou must wait " + GUIUtils.formatTime(cooldown) + " before leaving another message!");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }
        
        // Check if guestbook is full
        List<GuestBookEntry> entries = plugin.getApartmentManager().getGuestBooks().get(apartmentId);
        int currentMessages = entries != null ? entries.size() : 0;
        int maxMessages = plugin.getConfigManager().getGuestBookMaxMessages();
        
        if (currentMessages >= maxMessages) {
            GUIUtils.sendMessage(player, "&eGuestbook is full! Your message will replace the oldest one.");
        }
        
        GUIUtils.sendMessage(player, "&eUse command: &f/apartmentcore guestbook leave " + apartmentId + " <message>");
        GUIUtils.sendMessage(player, "&7Maximum length: &f" + plugin.getConfigManager().getGuestBookMaxMessageLength() + " characters");
    }
    
    private void handleClearAll() {
        Apartment apartment = plugin.getApartmentManager().getApartment(apartmentId);
        if (apartment == null) {
            GUIUtils.sendMessage(player, "&cApartment not found!");
            return;
        }
        
        if (apartment.owner == null || !apartment.owner.equals(player.getUniqueId())) {
            GUIUtils.sendMessage(player, "&cOnly the apartment owner can clear the guestbook!");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }
        
        List<GuestBookEntry> entries = plugin.getApartmentManager().getGuestBooks().get(apartmentId);
        if (entries == null || entries.isEmpty()) {
            GUIUtils.sendMessage(player, "&cGuestbook is already empty!");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
            return;
        }
        
        player.closeInventory();
        
        // Use existing confirmation system
        plugin.getServer().dispatchCommand(player, "apartmentcore guestbook clear " + apartmentId);
    }
    
    @Override
    public String getTitle() {
        Apartment apartment = plugin.getApartmentManager().getApartment(apartmentId);
        String apartmentName = apartment != null ? apartment.displayName : apartmentId;
        return ChatColor.DARK_AQUA + "Guestbook - " + apartmentName;
    }
}