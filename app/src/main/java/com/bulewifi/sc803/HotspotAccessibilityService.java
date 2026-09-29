package com.bulewifi.sc803;

import android.accessibilityservice.AccessibilityService;
import android.bluetooth.BluetoothAdapter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

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
        LogManager.getInstance().addLog("REQUEST", "AirplaneMode", "--",
                "Request desired=" + enable + ", returnToApp=" + returnToApp);

        mPendingAction = new PendingAction("AIRPLANE", enable, 8000, returnToApp, listener);
        SettingsHelper.openAirplaneModeSettings(this);

        // Schedule periodic check in case event is missed
        schedulePeriodicCheck(0);
    }

    public synchronized void setHotspot(boolean enable, boolean returnToApp, ActionListener listener) {
        LogManager.getInstance().addLog("REQUEST", "WLAN_Hotspot", "--",
                "Request desired=" + enable + ", returnToApp=" + returnToApp);

        mPendingAction = new PendingAction("HOTSPOT", enable, 8000, returnToApp, listener);
        SettingsHelper.openTetherSettings(this);

        schedulePeriodicCheck(0);
    }

    public synchronized void setBluetooth(boolean enable, boolean returnToApp, ActionListener listener) {
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
                    schedulePeriodicCheck(350);
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
            return; // Not on airplane mode page yet
        }

        // Find enclosing preference item
        AccessibilityNodeInfo preferenceItem = findPreferenceItem(titleNode);
        if (preferenceItem == null) {
            titleNode.recycle();
            return;
        }

        // Find switch_widget inside this preference
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
            // Success! State matches desired
            LogManager.getInstance().addLog("VERIFY", "AirplaneMode", "--",
                    "VERIFIED airplane=" + currentChecked + " OK (Desired: " + mPendingAction.desiredState + ")");
            completePendingAction(true);
        } else if (!mPendingAction.clickTriggered) {
            // Need to toggle
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
        // If on "网络和互联网" page and there is "热点和网络共享", click it to enter TetherSettings
        AccessibilityNodeInfo tetherEntry = findNodeByText(root, "热点和网络共享");
        if (tetherEntry != null) {
            AccessibilityNodeInfo clickTarget = findClickableParent(tetherEntry);
            if (clickTarget != null) {
                LogManager.getInstance().addLog("ACTION", "Hotspot", "--", "点击'热点和网络共享'进入子菜单");
                clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                clickTarget.recycle();
            }
            tetherEntry.recycle();
            return;
        }

        // Find text "WLAN 热点"
        AccessibilityNodeInfo titleNode = findNodeByText(root, "WLAN 热点");
        if (titleNode == null) {
            titleNode = findNodeByText(root, "WLAN热点");
        }
        if (titleNode == null) {
            titleNode = findNodeByText(root, "便携式 WLAN 热点");
        }
        if (titleNode == null) {
            return; // Not on TetherSettings page yet
        }

        AccessibilityNodeInfo preferenceItem = findPreferenceItem(titleNode);
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
            LogManager.getInstance().addLog("VERIFY", "Hotspot", "--",
                    "VERIFIED hotspot=" + currentChecked + " OK (Desired: " + mPendingAction.desiredState + ")");
            completePendingAction(true);
        } else if (!mPendingAction.clickTriggered) {
            LogManager.getInstance().addLog("ACTION", "Hotspot", "--",
                    "CURRENT checked=" + currentChecked + " -> CLICKING (Target=" + mPendingAction.desiredState + ")");

            boolean clicked = preferenceItem.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (!clicked) {
                clicked = switchWidget.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            LogManager.getInstance().addLog("ACTION", "Hotspot", "--", "Click result=" + clicked);
            mPendingAction.clickTriggered = true;
        }

        switchWidget.recycle();
        preferenceItem.recycle();
        titleNode.recycle();
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

    // Helper: walk up to find the Preference row (clickable LinearLayout or row containing switch)
    private AccessibilityNodeInfo findPreferenceItem(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node.getParent();
        while (current != null) {
            if (current.isClickable()) {
                return current;
            }
            // Check if this parent contains switch_widget
            List<AccessibilityNodeInfo> switches = current.findAccessibilityNodeInfosByViewId("android:id/switch_widget");
            if (switches != null && !switches.isEmpty()) {
                for (AccessibilityNodeInfo s : switches) s.recycle();
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
