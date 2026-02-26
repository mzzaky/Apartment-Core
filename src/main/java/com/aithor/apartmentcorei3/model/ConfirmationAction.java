package com.aithor.apartmentcorei3.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Confirmation action class
 */
public class ConfirmationAction {
    public final String type;
    public final String data;
    public final long timestamp;
    public final Map<String, Object> extraData; // For more complex actions like guestbook clear

    public ConfirmationAction(String type, String data, long timestamp) {
        this.type = type;
        this.data = data;
        this.timestamp = timestamp;
        this.extraData = new HashMap<>();
    }

    public ConfirmationAction(String type, String data, long timestamp, Map<String, Object> extraData) {
        this.type = type;
        this.data = data;
        this.timestamp = timestamp;
        this.extraData = extraData != null ? extraData : new HashMap<>();
    }
}