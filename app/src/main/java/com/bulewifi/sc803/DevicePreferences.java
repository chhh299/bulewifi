package com.bulewifi.sc803;

import android.content.Context;
import android.content.SharedPreferences;

public class DevicePreferences {
    private static final String PREF_NAME = "sc803_hotspot_prefs";

    private static final String KEY_TARGET_MAC = "target_mac";
    private static final String KEY_TARGET_NAME = "target_name";
    private static final String KEY_GRACE_PERIOD = "grace_period_seconds";
    private static final String KEY_AUTO_MODE = "auto_mode_enabled";

    private final SharedPreferences mPrefs;

    private static volatile DevicePreferences sInstance;

    private DevicePreferences(Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static DevicePreferences getInstance(Context context) {
        if (sInstance == null) {
            synchronized (DevicePreferences.class) {
                if (sInstance == null) {
                    sInstance = new DevicePreferences(context);
                }
            }
        }
        return sInstance;
    }

    public String getTargetMac() {
        return mPrefs.getString(KEY_TARGET_MAC, "");
    }

    public void setTargetDevice(String mac, String name) {
        mPrefs.edit()
                .putString(KEY_TARGET_MAC, mac != null ? mac.trim().toUpperCase() : "")
                .putString(KEY_TARGET_NAME, name != null ? name.trim() : "")
                .apply();
    }

    public String getTargetName() {
        return mPrefs.getString(KEY_TARGET_NAME, "");
    }

    public boolean isTargetDevice(String address) {
        String target = getTargetMac();
        if (target.isEmpty() || "ALL".equalsIgnoreCase(target)) {
            return true; // Any device
        }
        return address != null && target.equalsIgnoreCase(address.trim());
    }

    public int getGracePeriodSeconds() {
        return mPrefs.getInt(KEY_GRACE_PERIOD, 60);
    }

    public void setGracePeriodSeconds(int seconds) {
        mPrefs.edit().putInt(KEY_GRACE_PERIOD, seconds).apply();
    }

    public boolean isAutoModeEnabled() {
        return mPrefs.getBoolean(KEY_AUTO_MODE, false);
    }

    public void setAutoModeEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_AUTO_MODE, enabled).apply();
    }
}
