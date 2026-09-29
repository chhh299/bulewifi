package com.bulewifi.sc803;

import android.accessibilityservice.AccessibilityService;
import android.bluetooth.BluetoothAdapter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

public class HotspotAccessibilityService extends AccessibilityService {

    public interface ActionListener {
        void onSuccess(String action, boolean desiredState);
        void onFailure(String action, String error);
    }

    private static volatile HotspotAccessibilityService sInstance;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private PendingAction mPendingAction;

    private static class PendingAction {
        final String actionType; // "AIRPLANE", "HOTSPOT", "BLUETOOTH"
        final boolean desiredState;
        final long startTime;
        final long timeoutMs;
        final boolean returnToApp;
        final ActionListener listener;
        boolean clickTriggered = false;
        boolean dumped = false;

        PendingAction(String actionType, boolean desiredState, long timeoutMs, boolean returnToApp, ActionListener listener) {
            this.actionType = actionType;
            this.desiredState = desiredState;
            this.startTime = SystemClock.uptimeMillis();
            this.timeoutMs = timeoutMs;
            this.returnToApp = returnToApp;
            this.listener = listener;
        }

        boolean isTimedOut() {
            return (SystemClock.uptimeMillis() - startTime) > timeoutMs;
        }
    }

    public static boolean isServiceConnected() {
        return sInstance != null;
    }

    public static HotspotAccessibilityService getInstance() {
        return sInstance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        LogManager.getInstance().addInfoLog("AccessibilityService 已成功连接与绑定");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        LogManager.getInstance().addInfoLog("AccessibilityService 已断开");
    }

    @Override
    public void onInterrupt() {
        LogManager.getInstance().addInfoLog("AccessibilityService onInterrupt");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (mPendingAction != null) {
            mHandler.post(this::processPendingAction);
        }
    }

    public synchronized void setAirplaneMode(boolean enable, boolean returnToApp, ActionListener listener) {
        ScreenHelper.wakeUpScreen(this);
        LogManager.getInstance().addLog("REQUEST", "AirplaneMode", "--",
                "Request desired=" + enable + ", returnToApp=" + returnToApp);

        mPendingAction = new PendingAction("AIRPLANE", enable, 8000, returnToApp, listener);
        SettingsHelper.openAirplaneModeSettings(this);

        schedulePeriodicCheck(0);
    }

    public synchronized void setHotspot(boolean enable, boolean returnToApp, ActionListener listener) {
        ScreenHelper.wakeUpScreen(this);
        LogManager.getInstance().addLog("REQUEST", "WLAN_Hotspot", "--",
                "Request desired=" + enable + ", returnToApp=" + returnToApp);

        // Pre-condition: Hotspot hardware is completely disabled when Airplane Mode is ON
        if (enable && SettingsHelper.isAirplaneModeOn(this)) {
            String errorMsg = "当前处于飞行模式，WLAN热点已被系统底层禁用！必须先退出飞行模式。";
            LogManager.getInstance().addErrorLog(errorMsg);
            if (listener != null) {
                listener.onFailure("HOTSPOT", errorMsg);
            }
            return;
        }

        if (!enable && SettingsHelper.isAirplaneModeOn(this)) {
            LogManager.getInstance().addLog("VERIFY", "Hotspot", "--", "处于飞行模式，热点已被系统关闭 (OK)");
            if (listener != null) {
                listener.onSuccess("HOTSPOT", false);
            }
            return;
        }

        mPendingAction = new PendingAction("HOTSPOT", enable, 8000, returnToApp, listener);
        SettingsHelper.openTetherSettings(this);

        schedulePeriodicCheck(0);
    }

    public synchronized void setBluetooth(boolean enable, boolean returnToApp, ActionListener listener) {
        ScreenHelper.wakeUpScreen(this);
        LogManager.getInstance().addLog("REQUEST", "Bluetooth", "--",
                "Request desired=" + enable);

        // Try direct BluetoothAdapter first
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            if (enable && adapter.isEnabled()) {
                LogManager.getInstance().addLog("VERIFY", "Bluetooth", "--", "Bluetooth already ON");
                if (listener != null) listener.onSuccess("BLUETOOTH", true);
                return;
            }
            if (enable) {
                try {
                    boolean directSuccess = adapter.enable();
                    LogManager.getInstance().addLog("ACTION", "Bluetooth", "--", "Direct adapter.enable() returned " + directSuccess);
                } catch (Exception e) {
                    LogManager.getInstance().addErrorLog("adapter.enable() exception: " + e.getMessage());
                }
            }
        }

        mPendingAction = new PendingAction("BLUETOOTH", enable, 8000, returnToApp, listener);
        SettingsHelper.openBluetoothSettings(this);
        schedulePeriodicCheck(0);
    }

    private void schedulePeriodicCheck(int delayMs) {
        mHandler.postDelayed(() -> {
            if (mPendingAction != null) {
                processPendingAction();
                if (mPendingAction != null && !mPendingAction.isTimedOut()) {
                    schedulePeriodicCheck(250);
                }
            }
        }, delayMs);
    }

    private synchronized void processPendingAction() {
        if (mPendingAction == null) {
            return;
        }

        if (mPendingAction.isTimedOut()) {
            String errorMsg = "操作超时 (8秒未完成: " + mPendingAction.actionType + ")";
            LogManager.getInstance().addErrorLog(errorMsg);
            if (mPendingAction.listener != null) {
                mPendingAction.listener.onFailure(mPendingAction.actionType, errorMsg);
            }
            if (mPendingAction.returnToApp) {
                SettingsHelper.bringAppToFront(this);
            }
            mPendingAction = null;
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }

        // STRICT SAFETY CHECK: Only inspect and act on com.android.settings!
        // Never click buttons on our own app or launcher!
        CharSequence pkg = root.getPackageName();
        if (pkg == null || !"com.android.settings".contentEquals(pkg)) {
            root.recycle();
            return;
        }

        try {
            switch (mPendingAction.actionType) {
                case "AIRPLANE":
                    handleAirplaneStep(root);
                    break;
                case "HOTSPOT":
                    handleHotspotStep(root);
                    break;
                case "BLUETOOTH":
                    handleBluetoothStep(root);
                    break;
            }
        } finally {
            root.recycle();
        }
    }

    private void handleAirplaneStep(AccessibilityNodeInfo root) {
        // Find text "飞行模式"
        AccessibilityNodeInfo titleNode = findNodeByText(root, "飞行模式");
        if (titleNode == null) {
            titleNode = findNodeByText(root, "Airplane mode");
        }
        if (titleNode == null) {
            // If we are currently stuck in TetherSettings subpage, press system BACK to pop back to NetworkDashboard
            List<AccessibilityNodeInfo> subpageIndicators = root.findAccessibilityNodeInfosByText("USB网络共享");
            if (subpageIndicators == null || subpageIndicators.isEmpty()) {
                subpageIndicators = root.findAccessibilityNodeInfosByText("WLAN热点设置");
            }
            if (subpageIndicators != null && !subpageIndicators.isEmpty()) {
                recycleList(subpageIndicators);
                LogManager.getInstance().addLog("ACTION", "AirplaneMode", "--", "当前停留在热点子页面，执行系统 BACK 返回网络主菜单");
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
            return; // Wait for next tick
        }

        AccessibilityNodeInfo preferenceItem = findPreferenceRow(titleNode);
        if (preferenceItem == null) {
            titleNode.recycle();
            return;
        }

        AccessibilityNodeInfo switchWidget = findNodeById(preferenceItem, "android:id/switch_widget");
        if (switchWidget == null) {
            switchWidget = findNodeByClass(preferenceItem, "android.widget.Switch");
        }

        if (switchWidget == null) {
            preferenceItem.recycle();
            titleNode.recycle();
            return;
        }

        boolean currentChecked = switchWidget.isChecked();

        if (currentChecked == mPendingAction.desiredState) {
            LogManager.getInstance().addLog("VERIFY", "AirplaneMode", "--",
                    "VERIFIED airplane=" + currentChecked + " OK (Desired: " + mPendingAction.desiredState + ")");
            completePendingAction(true);
        } else if (!mPendingAction.clickTriggered) {
            LogManager.getInstance().addLog("ACTION", "AirplaneMode", "--",
                    "CURRENT checked=" + currentChecked + " -> CLICKING (Target=" + mPendingAction.desiredState + ")");

            boolean clicked = preferenceItem.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (!clicked) {
                clicked = switchWidget.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            LogManager.getInstance().addLog("ACTION", "AirplaneMode", "--", "Click result=" + clicked);
            mPendingAction.clickTriggered = true;
        }

        switchWidget.recycle();
        preferenceItem.recycle();
        titleNode.recycle();
    }

    private void handleHotspotStep(AccessibilityNodeInfo root) {
        // Step 1: Look specifically for "WLAN 热点" item on TetherSettings page
        AccessibilityNodeInfo wlanTitle = null;
        List<AccessibilityNodeInfo> candidates = root.findAccessibilityNodeInfosByText("WLAN 热点");
        if (candidates == null || candidates.isEmpty()) {
            candidates = root.findAccessibilityNodeInfosByText("WLAN热点");
        }
        if (candidates == null || candidates.isEmpty()) {
            candidates = root.findAccessibilityNodeInfosByText("便携式 WLAN 热点");
        }

        if (candidates != null && !candidates.isEmpty()) {
            wlanTitle = candidates.get(0);
            for (int i = 1; i < candidates.size(); i++) {
                candidates.get(i).recycle();
            }
        }

        if (wlanTitle != null) {
            AccessibilityNodeInfo row = findPreferenceRow(wlanTitle);
            if (row != null) {
                AccessibilityNodeInfo switchWidget = findNodeById(row, "android:id/switch_widget");
                if (switchWidget == null) {
                    switchWidget = findNodeByClass(row, "android.widget.Switch");
                }

                if (switchWidget != null) {
                    boolean currentChecked = switchWidget.isChecked();

                    if (currentChecked == mPendingAction.desiredState) {
                        LogManager.getInstance().addLog("VERIFY", "Hotspot", "--",
                                "VERIFIED WLAN 热点 checked=" + currentChecked + " OK (Desired: " + mPendingAction.desiredState + ")");
                        switchWidget.recycle();
                        row.recycle();
                        wlanTitle.recycle();
                        completePendingAction(true);
                        return;
                    } else if (!mPendingAction.clickTriggered) {
                        LogManager.getInstance().addLog("ACTION", "Hotspot", "--",
                                "WLAN 热点 CURRENT checked=" + currentChecked + " -> CLICKING (Target=" + mPendingAction.desiredState + ")");

                        // Try clicking the row first, then the switch widget if row click didn't toggle
                        boolean clicked = row.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        LogManager.getInstance().addLog("ACTION", "Hotspot", "--", "Row click result=" + clicked);
                        boolean switchClicked = switchWidget.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        LogManager.getInstance().addLog("ACTION", "Hotspot", "--", "Switch click result=" + switchClicked);
                        mPendingAction.clickTriggered = true;
                    }
                    switchWidget.recycle();
                }
                row.recycle();
            }
            wlanTitle.recycle();
            return;
        }

        // Step 2: If "WLAN 热点" is not found, check if we are on NetworkDashboard ("网络和互联网")
        // and need to click "热点和网络共享" to enter TetherSettings
        List<AccessibilityNodeInfo> tetherMenuNodes = root.findAccessibilityNodeInfosByText("热点和网络共享");
        if (tetherMenuNodes == null || tetherMenuNodes.isEmpty()) {
            tetherMenuNodes = root.findAccessibilityNodeInfosByText("热点与网络共享");
        }
        if (tetherMenuNodes != null && !tetherMenuNodes.isEmpty()) {
            if (!mPendingAction.clickTriggered) {
                AccessibilityNodeInfo targetNode = tetherMenuNodes.get(0);
                AccessibilityNodeInfo clickTarget = findClickableParent(targetNode);
                if (clickTarget != null) {
                    LogManager.getInstance().addLog("ACTION", "Hotspot", "--",
                            "点击进入热点子菜单: " + targetNode.getText());
                    boolean clicked = clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    LogManager.getInstance().addLog("ACTION", "Hotspot", "--", "点击结果=" + clicked);
                    clickTarget.recycle();
                }
            }
            recycleList(tetherMenuNodes);
            return;
        }

        // Diagnostic dump after 1.5 seconds if still searching
        if ((SystemClock.uptimeMillis() - mPendingAction.startTime > 1500) && !mPendingAction.dumped) {
            mPendingAction.dumped = true;
            List<String> visibleTexts = new ArrayList<>();
            collectVisibleTexts(root, visibleTexts);
            LogManager.getInstance().addLog("UI_DUMP", "ScreenTexts", "--",
                    "当前页面文本: " + visibleTexts.toString());
        }
    }

    private void handleBluetoothStep(AccessibilityNodeInfo root) {
        // Find switch bar in Bluetooth settings
        AccessibilityNodeInfo switchBar = findNodeById(root, "com.android.settings:id/switch_bar");
        if (switchBar == null) {
            switchBar = findNodeById(root, "com.android.settings:id/switch_widget");
        }

        if (switchBar != null) {
            boolean currentChecked = switchBar.isChecked();
            if (currentChecked == mPendingAction.desiredState) {
                LogManager.getInstance().addLog("VERIFY", "Bluetooth", "--",
                        "VERIFIED bluetooth=" + currentChecked + " OK");
                completePendingAction(true);
            } else if (!mPendingAction.clickTriggered) {
                LogManager.getInstance().addLog("ACTION", "Bluetooth", "--",
                        "Clicking Bluetooth switch_bar");
                boolean clicked = switchBar.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                LogManager.getInstance().addLog("ACTION", "Bluetooth", "--", "Click result=" + clicked);
                mPendingAction.clickTriggered = true;
            }
            switchBar.recycle();
        } else {
            // Check if adapter enabled already
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isEnabled() == mPendingAction.desiredState) {
                completePendingAction(true);
            }
        }
    }

    private void completePendingAction(boolean success) {
        if (mPendingAction != null) {
            PendingAction act = mPendingAction;
            mPendingAction = null;

            if (act.listener != null) {
                if (success) {
                    act.listener.onSuccess(act.actionType, act.desiredState);
                } else {
                    act.listener.onFailure(act.actionType, "验证未通过");
                }
            }

            if (act.returnToApp) {
                mHandler.postDelayed(() -> SettingsHelper.bringAppToFront(this), 600);
            }
        }
    }

    private void collectVisibleTexts(AccessibilityNodeInfo node, List<String> list) {
        if (node == null || list.size() > 30) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            list.add(text.toString());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectVisibleTexts(child, list);
                child.recycle();
            }
        }
    }

    private void recycleList(List<AccessibilityNodeInfo> list) {
        if (list != null) {
            for (AccessibilityNodeInfo n : list) {
                if (n != null) {
                    try {
                        n.recycle();
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    // Helper: walk up to find the Preference row containing switch_widget
    private AccessibilityNodeInfo findPreferenceRow(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node.getParent();
        while (current != null) {
            List<AccessibilityNodeInfo> switches = current.findAccessibilityNodeInfosByViewId("android:id/switch_widget");
            if (switches != null && !switches.isEmpty()) {
                recycleList(switches);
                return current;
            }
            AccessibilityNodeInfo next = current.getParent();
            current.recycle();
            current = next;
        }
        return null;
    }

    private AccessibilityNodeInfo findClickableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private AccessibilityNodeInfo findNodeByText(AccessibilityNodeInfo root, String text) {
        if (root == null || text == null) return null;
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(text);
        if (list != null && !list.isEmpty()) {
            AccessibilityNodeInfo target = list.get(0);
            for (int i = 1; i < list.size(); i++) {
                list.get(i).recycle();
            }
            return target;
        }
        return null;
    }

    private AccessibilityNodeInfo findNodeById(AccessibilityNodeInfo root, String viewId) {
        if (root == null || viewId == null) return null;
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByViewId(viewId);
        if (list != null && !list.isEmpty()) {
            AccessibilityNodeInfo target = list.get(0);
            for (int i = 1; i < list.size(); i++) {
                list.get(i).recycle();
            }
            return target;
        }
        return null;
    }

    private AccessibilityNodeInfo findNodeByClass(AccessibilityNodeInfo root, String className) {
        if (root == null || className == null) return null;
        if (className.equals(root.getClassName())) {
            return root;
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo res = findNodeByClass(child, className);
                if (res != null) {
                    if (res != child) child.recycle();
                    return res;
                }
                child.recycle();
            }
        }
        return null;
    }
}
