package com.bulewifi.sc803;

import android.content.Context;
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
}
