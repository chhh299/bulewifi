package com.bulewifi.sc803;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

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

    public static void openAccessibilitySettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void openAirplaneModeSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void openTetherSettings(Context context) {
        // Standard AOSP TetherSettings action
        Intent intent = new Intent("android.settings.TETHER_SETTINGS");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (context.getPackageManager().resolveActivity(intent, 0) != null) {
            context.startActivity(intent);
            return;
        }

        // Direct component fallback for Android 8.1 / SC803
        try {
            Intent componentIntent = new Intent();
            componentIntent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
            componentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(componentIntent);
            return;
        } catch (Exception ignored) {
        }

        // Second fallback: Wireless & Network settings
        try {
            Intent wirelessIntent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            wirelessIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(wirelessIntent);
        } catch (Exception e) {
            LogManager.getInstance().addErrorLog("无法打开热点设置页面: " + e.getMessage());
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
