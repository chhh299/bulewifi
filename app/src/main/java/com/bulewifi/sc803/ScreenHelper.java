package com.bulewifi.sc803;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

public class ScreenHelper {

    public static void wakeUpScreen(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) {
                PowerManager.WakeLock wl = pm.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                        "bulewifi:screen_wakeup"
                );
                wl.acquire(7000);
                wl.release();
                LogManager.getInstance().addLog("WAKE", "Screen", "--", "检测到黑屏息屏，已触发硬件点亮屏幕！");
            }
        } catch (Exception e) {
            LogManager.getInstance().addErrorLog("唤醒屏幕异常: " + e.getMessage());
        }
    }

    public static boolean isDeviceAdminActive(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(context, ScreenAdminReceiver.class);
            return dpm != null && dpm.isAdminActive(adminComponent);
        } catch (Exception e) {
            return false;
        }
    }

    public static void openDeviceAdminSettings(Context context) {
        try {
            ComponentName adminComponent = new ComponentName(context, ScreenAdminReceiver.class);
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "开启后，自动化控制热点完成后可自动熄灭屏幕，节省墨水屏电量。");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            LogManager.getInstance().addErrorLog("打开设备管理器异常: " + e.getMessage());
        }
    }

    public static void turnScreenOff(Context context, long delayMs) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                ComponentName adminComponent = new ComponentName(context, ScreenAdminReceiver.class);
                if (dpm != null && dpm.isAdminActive(adminComponent)) {
                    LogManager.getInstance().addLog("SLEEP", "Screen", "--", "自动化流程完成，已立即熄灭屏幕休眠");
                    dpm.lockNow();
                }
            } catch (Exception e) {
                LogManager.getInstance().addLog("SLEEP", "Screen", "--", "自动熄屏跳过: " + e.getMessage());
            }
        }, delayMs);
    }
}
