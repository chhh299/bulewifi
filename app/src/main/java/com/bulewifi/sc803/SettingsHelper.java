package com.bulewifi.sc803;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

public class SettingsHelper {

    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        ComponentName expectedComponentName = new ComponentName(context, serviceClass);
        String enabledServicesSetting = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        if (enabledServicesSetting == null) {
            return false;
        }

        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServicesSetting);

        while (colonSplitter.hasNext()) {
            String componentNameString = colonSplitter.next();
            ComponentName enabledComponent = ComponentName.unflattenFromString(componentNameString);
            if (enabledComponent != null && enabledComponent.equals(expectedComponentName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAirplaneModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    public static void openAccessibilitySettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void openAirplaneModeSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    public static void openTetherSettings(Context context) {
        LogManager.getInstance().addLog("LAUNCH", "TetherSettings", "--", "Attempting to open Tether Settings...");

        // 1. Try standard Android 8.1 AOSP Settings$TetherSettingsActivity
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$TetherSettingsActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
            LogManager.getInstance().addLog("LAUNCH", "TetherSettings", "--", "Launched Settings$TetherSettingsActivity");
            return;
        } catch (Exception e) {
            LogManager.getInstance().addLog("LAUNCH", "TetherSettings", "--", "Settings$TetherSettingsActivity failed: " + e.getMessage());
        }

        // 2. Try action "android.settings.TETHER_SETTINGS"
        try {
            Intent actionIntent = new Intent("android.settings.TETHER_SETTINGS");
            actionIntent.addCategory(Intent.CATEGORY_DEFAULT);
            actionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(actionIntent);
            LogManager.getInstance().addLog("LAUNCH", "TetherSettings", "--", "Launched android.settings.TETHER_SETTINGS");
            return;
        } catch (Exception e) {
            LogManager.getInstance().addLog("LAUNCH", "TetherSettings", "--", "TETHER_SETTINGS action failed: " + e.getMessage());
        }

        // 3. Fallback: Open "网络和互联网" (Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        // This is 100% verified to work on SC803 and opens the Network & Internet dashboard
        try {
            Intent netIntent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            netIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(netIntent);
            LogManager.getInstance().addLog("LAUNCH", "TetherSettings", "--", "Launched AIRPLANE_MODE_SETTINGS as dashboard fallback");
        } catch (Exception e) {
            LogManager.getInstance().addErrorLog("打开网络和互联网页面失败: " + e.getMessage());
        }
    }

    public static void openBluetoothSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void bringAppToFront(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }
}
