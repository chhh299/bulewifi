package com.bulewifi.sc803;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DevicePreferences {
    private static final String PREF_NAME = "sc803_hotspot_prefs";

    private static final String KEY_TARGET_MACS = "target_macs";
    private static final String KEY_TARGET_NAMES = "target_names_summary";
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

    public synchronized Set<String> getTargetMacs() {
        Set<String> set = mPrefs.getStringSet(KEY_TARGET_MACS, null);
        if (set == null) {
            return new HashSet<>();
        }
        return new HashSet<>(set);
    }

    public synchronized void setTargetDevices(Set<String> macs, String namesSummary) {
        Set<String> upperSet = new HashSet<>();
        if (macs != null) {
            for (String mac : macs) {
                if (mac != null && !mac.trim().isEmpty()) {
                    upperSet.add(mac.trim().toUpperCase());
                }
            }
        }

        mPrefs.edit()
                .putStringSet(KEY_TARGET_MACS, upperSet)
                .putString(KEY_TARGET_NAMES, namesSummary != null ? namesSummary.trim() : "")
                .apply();
    }

    public String getTargetNamesSummary() {
        String names = mPrefs.getString(KEY_TARGET_NAMES, "");
        if (names.isEmpty()) {
            Set<String> macs = getTargetMacs();
            if (macs.isEmpty() || macs.contains("ALL")) {
                return "任意设备均触发 (未绑定)";
            }
            return "已绑定 " + macs.size() + " 台设备";
        }
        return names;
    }

    public boolean isTargetDevice(String address) {
        if (address == null) return false;
        Set<String> targetMacs = getTargetMacs();
        if (targetMacs.isEmpty() || targetMacs.contains("ALL")) {
            return true; // Match all devices if not restricted
        }
        return targetMacs.contains(address.trim().toUpperCase());
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
