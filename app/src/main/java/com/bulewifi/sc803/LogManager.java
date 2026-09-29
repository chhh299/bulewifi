package com.bulewifi.sc803;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.bulewifi.sc803.model.LogItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class LogManager {
    public static final String TAG = "SC803AutoHotspot";
    private static final int MAX_LOG_SIZE = 500;

    private static volatile LogManager sInstance;

    private final List<LogItem> mLogs = new ArrayList<>();
    private final List<OnLogUpdateListener> mListeners = new CopyOnWriteArrayList<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public interface OnLogUpdateListener {
        void onLogAdded(LogItem item);
        void onLogsCleared();
    }

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

    public synchronized void addLog(String eventType, String deviceName, String deviceAddress, String details) {
        LogItem item = new LogItem(eventType, deviceName, deviceAddress, details);

        // Keep maximum MAX_LOG_SIZE items
        while (mLogs.size() >= MAX_LOG_SIZE) {
            mLogs.remove(0);
        }
        mLogs.add(item);

        // Print to logcat with unified tag
        String logcatMsg = String.format("[%s] device=%s, addr=%s, detail=%s",
                eventType, deviceName, deviceAddress, details);
        Log.i(TAG, logcatMsg);

        // Notify listeners on main thread
        mMainHandler.post(() -> {
            for (OnLogUpdateListener listener : mListeners) {
                listener.onLogAdded(item);
            }
        });
    }

    public void addInfoLog(String message) {
        addLog("INFO", "System", "--", message);
    }

    public void addErrorLog(String error) {
        addLog("ERROR", "System", "--", error);
    }

    public synchronized List<LogItem> getLogsSnapshot() {
        return new ArrayList<>(mLogs);
    }

    public synchronized void clearLogs() {
        mLogs.clear();
        Log.i(TAG, "Logs cleared by user");
        mMainHandler.post(() -> {
            for (OnLogUpdateListener listener : mListeners) {
                listener.onLogsCleared();
            }
        });
    }

    public synchronized String getAllLogsAsText() {
        if (mLogs.isEmpty()) {
            return "(暂无日志记录)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== SC803 AutoHotspot Bluetooth Probe Logs ===\n");
        sb.append("Total entries: ").append(mLogs.size()).append("\n\n");
        for (LogItem item : mLogs) {
            sb.append(item.toFormattedString()).append("\n");
        }
        return sb.toString();
    }

    public void registerListener(OnLogUpdateListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void unregisterListener(OnLogUpdateListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }
}
