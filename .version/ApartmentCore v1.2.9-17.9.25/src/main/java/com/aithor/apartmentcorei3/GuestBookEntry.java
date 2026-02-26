package com.aithor.apartmentcorei3;

import java.util.UUID;

/**
 * Guestbook entry data class
 */
public class GuestBookEntry {
    public final UUID senderUuid;
    public final String senderName;
    public final String message;
    public final long timestamp;

    public GuestBookEntry(UUID senderUuid, String senderName, String message, long timestamp) {
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.message = message;
        this.timestamp = timestamp;
    }
}