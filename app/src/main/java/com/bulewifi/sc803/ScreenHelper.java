package com.bulewifi.sc803;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class ScreenHelper {

    public static void wakeUpScreen(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) {
                PowerManager.WakeLock wl = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "bulewifi:screen_wakeup"
                );
                wl.acquire(3000);
                wl.release();
                LogManager.getInstance().addLog("WAKE", "Screen", "--", "息屏中检测到任务，已唤醒点亮屏幕");
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

    public static void requestDeviceAdmin(Activity activity, int requestCode) {
        try {
            ComponentName adminComponent = new ComponentName(activity, ScreenAdminReceiver.class);
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "允许在热点开启和待机完成后自动熄灭屏幕锁屏，节省墨水屏电量。");
            activity.startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            LogManager.getInstance().addErrorLog("申请设备管理器异常: " + e.getMessage());
        }
    }

    public static boolean turnScreenOff(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(context, ScreenAdminReceiver.class);
            if (dpm == null) {
                LogManager.getInstance().addErrorLog("DevicePolicyManager 为 null");
                return false;
            }
            if (dpm.isAdminActive(adminComponent)) {
                LogManager.getInstance().addLog("SLEEP", "Screen", "--", "正在调用 dpm.lockNow() 触发硬件熄屏锁屏");
                dpm.lockNow();
                return true;
            } else {
                LogManager.getInstance().addLog("SLEEP", "Screen", "--", "设备管理器未激活，跳过 lockNow");
                return false;
            }
        } catch (SecurityException se) {
            LogManager.getInstance().addErrorLog("lockNow() 权限被系统拒绝: " + se.getMessage());
            return false;
        } catch (Exception e) {
            LogManager.getInstance().addErrorLog("lockNow() 执行异常: " + e.getMessage());
            return false;
        }
    }
}
