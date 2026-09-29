package com.bulewifi.sc803;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bulewifi.sc803.model.LogItem;

public class MainActivity extends AppCompatActivity implements LogManager.OnLogUpdateListener {

    private static final int REQ_PERMISSIONS = 2001;

    private TextView tvBtStatus;
    private TextView tvAirplaneStatus;
    private TextView tvAccessibilityStatus;
    private TextView tvServiceStatus;
    private TextView tvLogSummary;

    private Button btnOpenAccessibility;
    private Button btnAirplaneOff;
    private Button btnAirplaneOn;
    private Button btnBluetoothOn;
    private Button btnHotspotOn;
    private Button btnHotspotOff;

    private Button btnCopyLogs;
    private Button btnClearLogs;
    private Button btnRefresh;
    private RecyclerView rvLogs;

    private LogAdapter mLogAdapter;
    private LinearLayoutManager mLayoutManager;
    private BluetoothAdapter mBluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupRecyclerView();
        setupListeners();
        checkAndRequestPermissions();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Register log listener
        LogManager.getInstance().registerListener(this);

        // Load existing logs
        mLogAdapter.setItems(LogManager.getInstance().getLogsSnapshot());

        // Auto-start probe service if not already running
        if (!BluetoothProbeService.isRunning()) {
            BluetoothProbeService.start(this);
        }

        updateUiStatus();
    }

    private void initViews() {
        tvBtStatus = findViewById(R.id.tv_bt_status);
        tvAirplaneStatus = findViewById(R.id.tv_airplane_status);
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status);
        tvServiceStatus = findViewById(R.id.tv_service_status);
        tvLogSummary = findViewById(R.id.tv_log_summary);

        btnOpenAccessibility = findViewById(R.id.btn_open_accessibility);
        btnAirplaneOff = findViewById(R.id.btn_airplane_off);
        btnAirplaneOn = findViewById(R.id.btn_airplane_on);
        btnBluetoothOn = findViewById(R.id.btn_bluetooth_on);
        btnHotspotOn = findViewById(R.id.btn_hotspot_on);
        btnHotspotOff = findViewById(R.id.btn_hotspot_off);

        btnCopyLogs = findViewById(R.id.btn_copy_logs);
        btnClearLogs = findViewById(R.id.btn_clear_logs);
        btnRefresh = findViewById(R.id.btn_refresh);
        rvLogs = findViewById(R.id.rv_logs);
    }

    private void setupRecyclerView() {
        mLayoutManager = new LinearLayoutManager(this);
        mLayoutManager.setStackFromEnd(true); // Latest logs at bottom
        rvLogs.setLayoutManager(mLayoutManager);

        mLogAdapter = new LogAdapter();
        rvLogs.setAdapter(mLogAdapter);

        DividerItemDecoration divider = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        rvLogs.addItemDecoration(divider);
    }

    private void setupListeners() {
        btnOpenAccessibility.setOnClickListener(v -> {
            Toast.makeText(this, "请在列表中找到并开启「SC803 自动热点辅助服务」", Toast.LENGTH_LONG).show();
            SettingsHelper.openAccessibilitySettings(this);
        });

        btnAirplaneOff.setOnClickListener(v -> executeWithAccessibility("退出飞行模式", service ->
                service.setAirplaneMode(false, true, createCallback("退出飞行模式"))));

        btnAirplaneOn.setOnClickListener(v -> executeWithAccessibility("进入飞行模式", service ->
                service.setAirplaneMode(true, true, createCallback("进入飞行模式"))));

        btnBluetoothOn.setOnClickListener(v -> executeWithAccessibility("开启蓝牙", service ->
                service.setBluetooth(true, true, createCallback("开启蓝牙"))));

        btnHotspotOn.setOnClickListener(v -> {
            if (SettingsHelper.isAirplaneModeOn(this)) {
                Toast.makeText(this, "⚠️ 当前处于飞行模式，WLAN热点已被系统底层禁用！请先点击「退出飞行模式」。", Toast.LENGTH_LONG).show();
                return;
            }
            executeWithAccessibility("开启 WLAN 热点", service ->
                    service.setHotspot(true, true, createCallback("开启 WLAN 热点")));
        });

        btnHotspotOff.setOnClickListener(v -> executeWithAccessibility("关闭 WLAN 热点", service ->
                service.setHotspot(false, true, createCallback("关闭 WLAN 热点"))));

        btnCopyLogs.setOnClickListener(v -> copyLogsToClipboard());

        btnClearLogs.setOnClickListener(v -> {
            LogManager.getInstance().clearLogs();
            Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show();
        });

        btnRefresh.setOnClickListener(v -> {
            updateUiStatus();
            Toast.makeText(this, "状态已刷新", Toast.LENGTH_SHORT).show();
        });
    }

    private interface ServiceAction {
        void run(HotspotAccessibilityService service);
    }

    private void executeWithAccessibility(String actionName, ServiceAction action) {
        if (!HotspotAccessibilityService.isServiceConnected()) {
            boolean systemEnabled = SettingsHelper.isAccessibilityServiceEnabled(this, HotspotAccessibilityService.class);
            String tip = systemEnabled ? "服务已被系统挂起，请重新开关一次无障碍服务" : "请先开启系统无障碍服务！";
            Toast.makeText(this, tip, Toast.LENGTH_LONG).show();
            SettingsHelper.openAccessibilitySettings(this);
            return;
        }

        Toast.makeText(this, "正在执行: " + actionName, Toast.LENGTH_SHORT).show();
        action.run(HotspotAccessibilityService.getInstance());
    }

    private HotspotAccessibilityService.ActionListener createCallback(String actionName) {
        return new HotspotAccessibilityService.ActionListener() {
            @Override
            public void onSuccess(String action, boolean desiredState) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, actionName + " 执行成功！", Toast.LENGTH_SHORT).show();
                    updateUiStatus();
                });
            }

            @Override
            public void onFailure(String action, String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, actionName + " 失败: " + error, Toast.LENGTH_LONG).show();
                    updateUiStatus();
                });
            }
        };
    }

    private void updateUiStatus() {
        // Bluetooth adapter state
        if (mBluetoothAdapter == null) {
            tvBtStatus.setText("蓝牙硬件: 不可用 (null)");
        } else {
            boolean enabled = mBluetoothAdapter.isEnabled();
            int state = mBluetoothAdapter.getState();
            String name = mBluetoothAdapter.getName();
            String address = mBluetoothAdapter.getAddress();
            tvBtStatus.setText(String.format("蓝牙: %s (%s) | 本机: %s",
                    enabled ? "ON" : "OFF",
                    BluetoothEventReceiver.getBtStateString(state),
                    name != null ? name : "SC803"));
        }

        // Airplane mode state
        boolean isAirplane = SettingsHelper.isAirplaneModeOn(this);
        if (isAirplane) {
            tvAirplaneStatus.setText("飞行模式: ON (⚠️ 热点硬件已被系统禁用)");
        } else {
            tvAirplaneStatus.setText("飞行模式: OFF (蜂窝网络与热点就绪)");
        }

        // Accessibility state
        boolean isConnected = HotspotAccessibilityService.isServiceConnected();
        boolean isSysEnabled = SettingsHelper.isAccessibilityServiceEnabled(this, HotspotAccessibilityService.class);

        if (isConnected) {
            tvAccessibilityStatus.setText("无障碍服务: 已连接就绪 (ACTIVE)");
            btnOpenAccessibility.setVisibility(View.GONE);
        } else if (isSysEnabled) {
            tvAccessibilityStatus.setText("无障碍服务: 系统已开 (等待绑定连接)");
            btnOpenAccessibility.setVisibility(View.VISIBLE);
            btnOpenAccessibility.setText("无障碍服务连接中... (若无响应点击重开)");
        } else {
            tvAccessibilityStatus.setText("无障碍服务: 未开启 (免Root必需)");
            btnOpenAccessibility.setVisibility(View.VISIBLE);
            btnOpenAccessibility.setText("⚠️ 点击跳转开启系统无障碍服务");
        }

        // Probe Service state
        boolean isRunning = BluetoothProbeService.isRunning();
        tvServiceStatus.setText(String.format("前台保活服务: %s", isRunning ? "运行中 (ACTIVE)" : "已停止"));

        tvLogSummary.setText(String.format("事件日志 (共 %d 条):", mLogAdapter.getItemCount()));
    }

    private void copyLogsToClipboard() {
        String allLogs = LogManager.getInstance().getAllLogsAsText();
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            ClipData clip = ClipData.newPlainText("SC803_BT_Logs", allLogs);
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "日志已复制到剪贴板！", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
            boolean needed = false;
            for (String p : permissions) {
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    needed = true;
                    break;
                }
            }
            if (needed) {
                ActivityCompat.requestPermissions(this, permissions, REQ_PERMISSIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            LogManager.getInstance().addInfoLog("权限申请结果返回");
            updateUiStatus();
        }
    }

    @Override
    public void onLogAdded(LogItem item) {
        mLogAdapter.addItem(item);
        rvLogs.smoothScrollToPosition(mLogAdapter.getItemCount() - 1);
        tvLogSummary.setText(String.format("事件日志 (共 %d 条):", mLogAdapter.getItemCount()));
    }

    @Override
    public void onLogsCleared() {
        mLogAdapter.clear();
        tvLogSummary.setText("事件日志 (共 0 条):");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUiStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogManager.getInstance().unregisterListener(this);
    }
}
