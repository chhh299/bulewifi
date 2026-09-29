package com.bulewifi.sc803;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class BluetoothProbeService extends Service {
    private static final String CHANNEL_ID = "sc803_bt_probe_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static volatile boolean sIsRunning = false;
    private BluetoothEventReceiver mReceiver;

    public static boolean isRunning() {
        return sIsRunning;
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, BluetoothProbeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, BluetoothProbeService.class);
        context.stopService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sIsRunning = true;
        LogManager.getInstance().addInfoLog("BluetoothProbeService onCreate");

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        registerBluetoothReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogManager.getInstance().addInfoLog("BluetoothProbeService onStartCommand");
        return START_STICKY;
    }

    private void registerBluetoothReceiver() {
        if (mReceiver == null) {
            mReceiver = new BluetoothEventReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mReceiver, filter);
            LogManager.getInstance().addInfoLog("Bluetooth ACL BroadcastReceiver 动态注册成功");
        }
    }

    private void unregisterBluetoothReceiver() {
        if (mReceiver != null) {
            try {
                unregisterReceiver(mReceiver);
                LogManager.getInstance().addInfoLog("Bluetooth ACL BroadcastReceiver 已注销");
            } catch (Exception e) {
                LogManager.getInstance().addErrorLog("注销 Receiver 异常: " + e.getMessage());
            }
            mReceiver = null;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SC803 蓝牙探测服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持前台常驻以监听蓝牙连接与断开广播");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SC803 蓝牙探测服务运行中")
                .setContentText("正在监听目标蓝牙 ACL CONNECTED / DISCONNECTED")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterBluetoothReceiver();
        sIsRunning = false;
        LogManager.getInstance().addInfoLog("BluetoothProbeService 已停止");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
