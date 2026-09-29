package com.bulewifi.sc803.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogItem {
    private static long sIdCounter = 0;

    private final long id;
    private final String timestamp;
    private final String eventType;
    private final String deviceName;
    private final String deviceAddress;
    private final String details;

    public LogItem(String eventType, String deviceName, String deviceAddress, String details) {
        this.id = ++sIdCounter;
        this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        this.eventType = eventType != null ? eventType : "UNKNOWN";
        this.deviceName = (deviceName != null && !deviceName.isEmpty()) ? deviceName : "(Unknown Name)";
        this.deviceAddress = (deviceAddress != null && !deviceAddress.isEmpty()) ? deviceAddress : "--";
        this.details = details != null ? details : "";
    }

    public long getId() {
        return id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    public String getDetails() {
        return details;
    }

    public boolean isConnected() {
        return "CONNECTED".equalsIgnoreCase(eventType) || "ACL_CONNECTED".equalsIgnoreCase(eventType);
    }

    public boolean isDisconnected() {
        return "DISCONNECTED".equalsIgnoreCase(eventType) || "ACL_DISCONNECTED".equalsIgnoreCase(eventType);
    }

    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestamp).append("] ");
        sb.append(eventType).append("\n");
        sb.append("  Device: ").append(deviceName).append(" (").append(deviceAddress).append(")\n");
        if (!details.isEmpty()) {
            sb.append("  Detail: ").append(details).append("\n");
        }
        return sb.toString();
    }
}
