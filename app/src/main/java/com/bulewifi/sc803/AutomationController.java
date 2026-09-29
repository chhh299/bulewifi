package com.bulewifi.sc803;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AutomationController {

    public enum State {
        IDLE("待机中 (飞行ON+蓝牙ON)"),
        EXITING_AIRPLANE("正在退出飞行模式..."),
        WAITING_CELLULAR("等待4G蜂窝网络恢复..."),
        ENABLING_HOTSPOT("正在开启WLAN热点..."),
        ONLINE("热点已开启 (正常在线)"),
        DISCONNECT_GRACE("目标断开，宽限倒计时"),
        DISABLING_HOTSPOT("正在关闭WLAN热点..."),
        ENTERING_AIRPLANE("正在开启飞行模式..."),
        RESTORING_BLUETOOTH("正在恢复蓝牙待机..."),
        ERROR("自动化异常");

        private final String description;

        State(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public interface StateChangeListener {
        void onStateChanged(State newState, String detail);
        void onGraceCountdown(int remainingSeconds);
    }

    private static volatile AutomationController sInstance;

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<StateChangeListener> mListeners = new CopyOnWriteArrayList<>();
    private final java.util.Set<String> mActiveConnectedTargetDevices = new java.util.HashSet<>();

    private State mCurrentState = State.IDLE;
    private Runnable mGraceRunnable;
    private int mRemainingGraceSeconds = 0;

    private AutomationController(Context context) {
        mContext = context.getApplicationContext();
    }

    public static AutomationController getInstance(Context context) {
        if (sInstance == null) {
            synchronized (AutomationController.class) {
                if (sInstance == null) {
                    sInstance = new AutomationController(context);
                }
            }
        }
        return sInstance;
    }

    public State getCurrentState() {
        return mCurrentState;
    }

    public int getRemainingGraceSeconds() {
        return mRemainingGraceSeconds;
    }

    public void registerListener(StateChangeListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void unregisterListener(StateChangeListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    private synchronized void transitionTo(State newState, String detail) {
        mCurrentState = newState;
        LogManager.getInstance().addLog("STATE", "Automation", "--",
                "State -> " + newState.name() + " (" + newState.getDescription() + ") | " + detail);

        mHandler.post(() -> {
            for (StateChangeListener l : mListeners) {
                l.onStateChanged(newState, detail);
            }
        });
    }

    public synchronized void onBluetoothConnected(String address, String name) {
        DevicePreferences prefs = DevicePreferences.getInstance(mContext);

        if (!prefs.isAutoModeEnabled()) {
            LogManager.getInstance().addLog("AUTO", "Ignore", address,
                    "已连接但自动控制未开启: " + name + " (" + address + ")");
            return;
        }

        if (!prefs.isTargetDevice(address)) {
            LogManager.getInstance().addLog("AUTO", "Filtered", address,
                    "非目标设备连接，忽略: " + name + " (" + address + ")");
            return;
        }

        mActiveConnectedTargetDevices.add(address.trim().toUpperCase());
        int activeCount = mActiveConnectedTargetDevices.size();

        LogManager.getInstance().addLog("AUTO", "TargetConnected", address,
                "目标设备连接成功: " + name + " (" + address + ") [当前在线目标: " + activeCount + "台]");

        // If in disconnect grace countdown, cancel shutdown and return to ONLINE
        if (mCurrentState == State.DISCONNECT_GRACE) {
            cancelGraceCountdown();
            transitionTo(State.ONLINE, "目标设备在宽限期内重新连接，取消关机并保持热点 (在线目标: " + activeCount + "台)");
            return;
        }

        if (mCurrentState == State.ONLINE) {
            LogManager.getInstance().addLog("AUTO", "AlreadyOnline", address,
                    "热点已处于在线状态，新目标设备接入: " + name + " (在线目标: " + activeCount + "台)");
            return;
        }

        // Cancel any pending countdown
        cancelGraceCountdown();

        // Check Accessibility service readiness
        if (!HotspotAccessibilityService.isServiceConnected()) {
            transitionTo(State.ERROR, "无法执行自动化：无障碍服务未连接！");
            return;
        }

        // Wake up screen if asleep so UI can render
        ScreenHelper.wakeUpScreen(mContext);

        // Start connection pipeline:
        // 1. Exit Airplane Mode
        transitionTo(State.EXITING_AIRPLANE, "收到蓝牙连接，开始退出飞行模式");
        HotspotAccessibilityService.getInstance().setAirplaneMode(false, false, new HotspotAccessibilityService.ActionListener() {
            @Override
            public void onSuccess(String action, boolean desiredState) {
                // 2. Wait for cellular mobile network
                transitionTo(State.WAITING_CELLULAR, "飞行模式已退出，等待4G/LTE移动数据就绪");
                NetworkMonitor.waitForCellular(mContext, 25, new NetworkMonitor.CellularCallback() {
                    @Override
                    public void onCellularReady() {
                        startEnablingHotspot();
                    }

                    @Override
                    public void onCellularTimeout() {
                        LogManager.getInstance().addLog("AUTO", "CellularTimeout", "--", "移动网络超时，仍然尝试开启热点");
                        startEnablingHotspot();
                    }
                });
            }

            @Override
            public void onFailure(String action, String error) {
                transitionTo(State.ERROR, "退出飞行模式失败: " + error);
            }
        });
    }

    private void startEnablingHotspot() {
        if (!HotspotAccessibilityService.isServiceConnected()) {
            transitionTo(State.ERROR, "无障碍服务异常断开");
            return;
        }

        transitionTo(State.ENABLING_HOTSPOT, "移动数据已就绪，正在通过无障碍开启WLAN热点");
        HotspotAccessibilityService.getInstance().setHotspot(true, true, new HotspotAccessibilityService.ActionListener() {
            @Override
            public void onSuccess(String action, boolean desiredState) {
                transitionTo(State.ONLINE, "WLAN 热点已成功开启，iPad/目标设备可正常上网！");
                SettingsHelper.goToHomeScreen(mContext);
            }

            @Override
            public void onFailure(String action, String error) {
                transitionTo(State.ERROR, "开启 WLAN 热点失败: " + error);
            }
        });
    }

    public synchronized void onBluetoothDisconnected(String address, String name) {
        DevicePreferences prefs = DevicePreferences.getInstance(mContext);

        if (!prefs.isAutoModeEnabled()) {
            return;
        }

        if (!prefs.isTargetDevice(address)) {
            return;
        }

        mActiveConnectedTargetDevices.remove(address.trim().toUpperCase());
        int remaining = mActiveConnectedTargetDevices.size();

        LogManager.getInstance().addLog("AUTO", "TargetDisconnected", address,
                "目标设备断开: " + name + " (" + address + ") [剩余在线目标: " + remaining + "台]");

        if (remaining > 0) {
            LogManager.getInstance().addLog("AUTO", "KeepOnline", address,
                    "仍有 " + remaining + " 台目标设备保持连接，热点继续保持在线！");
            return;
        }

        if (mCurrentState == State.IDLE || mCurrentState == State.ENTERING_AIRPLANE || mCurrentState == State.RESTORING_BLUETOOTH) {
            LogManager.getInstance().addLog("AUTO", "IdleState", "--", "当前已处于待机或关机流程，无需重复处理");
            return;
        }

        startGraceCountdown();
    }

    private synchronized void startGraceCountdown() {
        cancelGraceCountdown();

        DevicePreferences prefs = DevicePreferences.getInstance(mContext);
        mRemainingGraceSeconds = prefs.getGracePeriodSeconds();

        transitionTo(State.DISCONNECT_GRACE, "启动 " + mRemainingGraceSeconds + " 秒断开宽限倒计时");

        mGraceRunnable = new Runnable() {
            @Override
            public void run() {
                mRemainingGraceSeconds--;

                for (StateChangeListener l : mListeners) {
                    l.onGraceCountdown(mRemainingGraceSeconds);
                }

                if (mRemainingGraceSeconds <= 0) {
                    LogManager.getInstance().addLog("AUTO", "GraceExpired", "--", "宽限期结束，未重新连入，执行热点关闭与待机流程");
                    executeShutdownPipeline();
                } else {
                    mHandler.postDelayed(this, 1000);
                }
            }
        };

        mHandler.postDelayed(mGraceRunnable, 1000);
    }

    private synchronized void cancelGraceCountdown() {
        if (mGraceRunnable != null) {
            mHandler.removeCallbacks(mGraceRunnable);
            mGraceRunnable = null;
        }
        mRemainingGraceSeconds = 0;
    }

    private void executeShutdownPipeline() {
        if (!HotspotAccessibilityService.isServiceConnected()) {
            transitionTo(State.ERROR, "无障碍服务未连接，无法执行关机流程");
            return;
        }

        // Wake screen so Settings UI can render
        ScreenHelper.wakeUpScreen(mContext);

        // Step 1: Disable WLAN Hotspot
        transitionTo(State.DISABLING_HOTSPOT, "宽限期超时，开始关闭WLAN热点");
        HotspotAccessibilityService.getInstance().setHotspot(false, false, new HotspotAccessibilityService.ActionListener() {
            @Override
            public void onSuccess(String action, boolean desiredState) {
                // Step 2: Enable Airplane Mode
                transitionTo(State.ENTERING_AIRPLANE, "热点已关闭，开始开启飞行模式");
                HotspotAccessibilityService.getInstance().setAirplaneMode(true, false, new HotspotAccessibilityService.ActionListener() {
                    @Override
                    public void onSuccess(String action, boolean desiredState) {
                        // Step 3: Restore Bluetooth
                        transitionTo(State.RESTORING_BLUETOOTH, "飞行模式已开启，开始单独恢复开启蓝牙");
                        mHandler.postDelayed(() -> {
                            HotspotAccessibilityService.getInstance().setBluetooth(true, true, new HotspotAccessibilityService.ActionListener() {
                                @Override
                                public void onSuccess(String action, boolean desiredState) {
                                    mActiveConnectedTargetDevices.clear();
                                    transitionTo(State.IDLE, "待机状态已达成: 飞行模式 ON + 蓝牙 ON + 热点 OFF (极度省电)");
                                    SettingsHelper.goToHomeScreen(mContext);
                                }

                                @Override
                                public void onFailure(String action, String error) {
                                    transitionTo(State.ERROR, "恢复蓝牙失败: " + error);
                                }
                            });
                        }, 500);
                    }

                    @Override
                    public void onFailure(String action, String error) {
                        transitionTo(State.ERROR, "开启飞行模式失败: " + error);
                    }
                });
            }

            @Override
            public void onFailure(String action, String error) {
                transitionTo(State.ERROR, "关闭热点失败: " + error);
            }
        });
    }

    public synchronized void forceState(State state) {
        cancelGraceCountdown();
        transitionTo(state, "用户手动重置状态");
    }
}
