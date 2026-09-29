package com.bulewifi.sc803;

import android.app.Activity;
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

    public static void openDeviceAdminSettings(Activity activity, int requestCode) {
        try {
            ComponentName adminComponent = new ComponentName(activity, ScreenAdminReceiver.class);
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "开启后，自动化控制热点完成后可自动熄灭屏幕，节省墨水屏电量。");
            activity.startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            LogManager.getInstance().addErrorLog("打开设备管理器异常: " + e.getMessage());
        }
    }

    public static boolean turnScreenOff(Context context, long delayMs) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(context, ScreenAdminReceiver.class);
            if (dpm != null && dpm.isAdminActive(adminComponent)) {
                if (delayMs <= 0) {
                    LogManager.getInstance().addLog("SLEEP", "Screen", "--", "立即调用 lockNow() 熄灭屏幕");
                    dpm.lockNow();
                } else {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            LogManager.getInstance().addLog("SLEEP", "Screen", "--", "延时完成，调用 lockNow() 熄灭屏幕");
                            dpm.lockNow();
                        } catch (Exception e) {
                            LogManager.getInstance().addErrorLog("lockNow() 执行失败: " + e.getMessage());
                        }
                    }, delayMs);
                }
                return true;
            } else {
                LogManager.getInstance().addLog("SLEEP", "Screen", "--", "设备管理器未激活，跳过 lockNow 关屏");
                return false;
            }
        } catch (Exception e) {
            LogManager.getInstance().addErrorLog("turnScreenOff 异常: " + e.getMessage());
            return false;
        }
    }
}
