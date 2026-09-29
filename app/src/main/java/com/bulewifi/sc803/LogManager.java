package com.bulewifi.sc803;

import android.util.Log;

public class LogManager {
    public static final String TAG = "SC803AutoHotspot";

    private static volatile LogManager sInstance;

    private LogManager() {}

    public static LogManager getInstance() {
        if (sInstance == null) {
            synchronized (LogManager.class) {
                if (sInstance == null) {
                    sInstance = new LogManager();
                }
            }
        }
        return sInstance;
    }

    public void addLog(String eventType, String deviceName, String address, String details) {
        String logcatMsg = String.format("[%s] device=%s, addr=%s, detail=%s",
                eventType, deviceName, address, details);
        Log.i(TAG, logcatMsg);
    }

    public void addInfoLog(String message) {
        addLog("INFO", "System", "--", message);
    }

    public void addErrorLog(String error) {
        addLog("ERROR", "System", "--", error);
    }
}
