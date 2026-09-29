package com.bulewifi.sc803;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

public class NetworkMonitor {

    public interface CellularCallback {
        void onCellularReady();
        void onCellularTimeout();
    }

    public static boolean isCellularConnected(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            NetworkInfo activeInfo = cm.getActiveNetworkInfo();
            if (activeInfo != null && activeInfo.isConnected() && activeInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                return true;
            }

            NetworkInfo mobileInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            return mobileInfo != null && mobileInfo.isConnected();
        } catch (Exception e) {
            LogManager.getInstance().addErrorLog("检测移动网络异常: " + e.getMessage());
            return false;
        }
    }

    public static void waitForCellular(Context context, int timeoutSeconds, CellularCallback callback) {
        Handler handler = new Handler(Looper.getMainLooper());
        long startTime = SystemClock.uptimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        LogManager.getInstance().addLog("WAIT", "Cellular", "--",
                "等待移动数据/4G网络恢复 (超时限制: " + timeoutSeconds + "秒)...");

        Runnable checker = new Runnable() {
            @Override
            public void run() {
                if (isCellularConnected(context)) {
                    LogManager.getInstance().addLog("READY", "Cellular", "--", "移动网络/LTE 已就绪！");
                    if (callback != null) {
                        callback.onCellularReady();
                    }
                    return;
                }

                if (SystemClock.uptimeMillis() - startTime >= timeoutMs) {
                    LogManager.getInstance().addLog("WARN", "Cellular", "--",
                            "等待移动网络超时 (" + timeoutSeconds + "秒)，继续尝试启动热点...");
                    if (callback != null) {
                        callback.onCellularTimeout();
                    }
                    return;
                }

                handler.postDelayed(this, 1000);
            }
        };

        handler.post(checker);
    }
}
