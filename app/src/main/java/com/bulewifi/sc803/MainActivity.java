package com.bulewifi.sc803;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements AutomationController.StateChangeListener {

    private static final int REQ_PERMISSIONS = 2001;

    private TextView tvHeroStatus;
    private TextView tvHeroDetail;
    private Button btnMasterAuto;

    private TextView tvTargetDevices;
    private Button btnEditDevices;

    private Button btnAccAction;
    private Button btnAdminAction;
    private TextView tvHwState;

    private TextView tvToggleAdvanced;
    private LinearLayout layoutAdvancedControls;
    private Button btnTestAirplaneOff;
    private Button btnTestAirplaneOn;
    private Button btnTestHotspotOn;
    private Button btnTestHotspotOff;
    private Button btnTestBtOn;

    private BluetoothAdapter mBluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Wake screen and dismiss lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }

        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        checkAndRequestPermissions();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Register automation listener
        AutomationController.getInstance(this).registerListener(this);

        // Auto-start probe background service
        if (!BluetoothProbeService.isRunning()) {
            BluetoothProbeService.start(this);
        }

        updateUiStatus();
    }

    private void initViews() {
        tvHeroStatus = findViewById(R.id.tv_hero_status);
        tvHeroDetail = findViewById(R.id.tv_hero_detail);
        btnMasterAuto = findViewById(R.id.btn_master_auto);

        tvTargetDevices = findViewById(R.id.tv_target_devices);
        btnEditDevices = findViewById(R.id.btn_edit_devices);

        btnAccAction = findViewById(R.id.btn_acc_action);
        btnAdminAction = findViewById(R.id.btn_admin_action);
        tvHwState = findViewById(R.id.tv_hw_state);

        tvToggleAdvanced = findViewById(R.id.tv_toggle_advanced);
        layoutAdvancedControls = findViewById(R.id.layout_advanced_controls);
        btnTestAirplaneOff = findViewById(R.id.btn_test_airplane_off);
        btnTestAirplaneOn = findViewById(R.id.btn_test_airplane_on);
        btnTestHotspotOn = findViewById(R.id.btn_test_hotspot_on);
        btnTestHotspotOff = findViewById(R.id.btn_test_hotspot_off);
        btnTestBtOn = findViewById(R.id.btn_test_bt_on);
    }

    private void setupListeners() {
        // Master Auto Mode Toggle
        btnMasterAuto.setOnClickListener(v -> {
            DevicePreferences prefs = DevicePreferences.getInstance(this);
            boolean newEnabled = !prefs.isAutoModeEnabled();
            if (newEnabled && !HotspotAccessibilityService.isServiceConnected()) {
                Toast.makeText(this, "⚠️ 请先开启无障碍服务才能启用自动控制！", Toast.LENGTH_LONG).show();
                SettingsHelper.openAccessibilitySettings(this);
                return;
            }
            prefs.setAutoModeEnabled(newEnabled);
            Toast.makeText(this, newEnabled ? "✅ 自动热点控制已开启" : "⏸ 自动热点控制已停止", Toast.LENGTH_SHORT).show();
            updateUiStatus();
        });

        // Edit Target Devices
        btnEditDevices.setOnClickListener(v -> showDeviceSelectDialog());

        // Accessibility Permission Action
        btnAccAction.setOnClickListener(v -> {
            if (HotspotAccessibilityService.isServiceConnected()) {
                Toast.makeText(this, "无障碍辅助服务已就绪！", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "请在列表中找到并开启「SC803 自动热点辅助服务」", Toast.LENGTH_LONG).show();
                SettingsHelper.openAccessibilitySettings(this);
            }
        });

        // Device Admin Permission Action
        btnAdminAction.setOnClickListener(v -> {
            if (ScreenHelper.isDeviceAdminActive(this)) {
                Toast.makeText(this, "自动熄屏权限已就绪！", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "请点击「激活」以启用自动化完成自动熄屏", Toast.LENGTH_LONG).show();
                ScreenHelper.openDeviceAdminSettings(this);
            }
        });

        // Toggle Advanced Controls
        tvToggleAdvanced.setOnClickListener(v -> {
            if (layoutAdvancedControls.getVisibility() == View.VISIBLE) {
                layoutAdvancedControls.setVisibility(View.GONE);
                tvToggleAdvanced.setText("▼ 高级手动测试 (排查时展开)");
            } else {
                layoutAdvancedControls.setVisibility(View.VISIBLE);
                tvToggleAdvanced.setText("▲ 收起高级测试选项");
            }
        });

        // Advanced Manual Test Buttons
        btnTestAirplaneOff.setOnClickListener(v -> executeWithAccessibility("退出飞行模式", service ->
                service.setAirplaneMode(false, true, createCallback("退出飞行模式"))));

        btnTestAirplaneOn.setOnClickListener(v -> executeWithAccessibility("进入飞行模式", service ->
                service.setAirplaneMode(true, true, createCallback("进入飞行模式"))));

        btnTestBtOn.setOnClickListener(v -> executeWithAccessibility("开启蓝牙", service ->
                service.setBluetooth(true, true, createCallback("开启蓝牙"))));

        btnTestHotspotOn.setOnClickListener(v -> {
            if (SettingsHelper.isAirplaneModeOn(this)) {
                Toast.makeText(this, "⚠️ 当前处于飞行模式，WLAN热点已被系统底层禁用！请先点击「退出飞行模式」。", Toast.LENGTH_LONG).show();
                return;
            }
            executeWithAccessibility("开启 WLAN 热点", service ->
                    service.setHotspot(true, true, createCallback("开启 WLAN 热点")));
        });

        btnTestHotspotOff.setOnClickListener(v -> executeWithAccessibility("关闭 WLAN 热点", service ->
                service.setHotspot(false, true, createCallback("关闭 WLAN 热点"))));
    }

    private void showDeviceSelectDialog() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "请先开启蓝牙后选择设备", Toast.LENGTH_SHORT).show();
            return;
        }

        DevicePreferences prefs = DevicePreferences.getInstance(this);
        Set<String> savedMacs = prefs.getTargetMacs();
        boolean isAllSelected = savedMacs.isEmpty() || savedMacs.contains("ALL");

        Set<BluetoothDevice> bonded = mBluetoothAdapter.getBondedDevices();
        List<String> displayItems = new ArrayList<>();
        List<String> macList = new ArrayList<>();
        List<String> nameList = new ArrayList<>();

        displayItems.add("⚡ 监听任意蓝牙设备 (调试模式)");
        macList.add("ALL");
        nameList.add("任意设备");

        if (bonded != null) {
            for (BluetoothDevice dev : bonded) {
                String devName = dev.getName() != null ? dev.getName() : "(Unknown)";
                String devMac = dev.getAddress();
                displayItems.add("📱 " + devName + " (" + devMac + ")");
                macList.add(devMac);
                nameList.add(devName);
            }
        }

        int count = displayItems.size();
        CharSequence[] itemArray = displayItems.toArray(new CharSequence[0]);
        boolean[] checkedItems = new boolean[count];

        if (isAllSelected) {
            checkedItems[0] = true;
        } else {
            for (int i = 1; i < count; i++) {
                if (savedMacs.contains(macList.get(i).toUpperCase())) {
                    checkedItems[i] = true;
                }
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择目标蓝牙设备 (可勾选多个)");
        builder.setMultiChoiceItems(itemArray, checkedItems, (dialog, which, isChecked) -> {
            checkedItems[which] = isChecked;
            if (which == 0 && isChecked) {
                for (int i = 1; i < count; i++) {
                    checkedItems[i] = false;
                    ((AlertDialog) dialog).getListView().setItemChecked(i, false);
                }
            } else if (which != 0 && isChecked) {
                checkedItems[0] = false;
                ((AlertDialog) dialog).getListView().setItemChecked(0, false);
            }
        });

        builder.setPositiveButton("确定保存", (dialog, which) -> {
            Set<String> selectedMacs = new HashSet<>();
            List<String> selectedNames = new ArrayList<>();

            if (checkedItems[0]) {
                selectedMacs.add("ALL");
                selectedNames.add("任意设备");
            } else {
                for (int i = 1; i < count; i++) {
                    if (checkedItems[i]) {
                        selectedMacs.add(macList.get(i));
                        selectedNames.add(nameList.get(i));
                    }
                }
            }

            if (selectedMacs.isEmpty()) {
                selectedMacs.add("ALL");
                selectedNames.add("任意设备");
            }

            String summary = TextUtils.join(", ", selectedNames);
            prefs.setTargetDevices(selectedMacs, summary);
            Toast.makeText(this, "已保存目标设备: " + summary, Toast.LENGTH_SHORT).show();
            updateUiStatus();
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private interface ServiceAction {
        void run(HotspotAccessibilityService service);
    }

    private void executeWithAccessibility(String actionName, ServiceAction action) {
        if (!HotspotAccessibilityService.isServiceConnected()) {
            Toast.makeText(this, "请先开启系统无障碍服务！", Toast.LENGTH_LONG).show();
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
        DevicePreferences prefs = DevicePreferences.getInstance(this);
        AutomationController controller = AutomationController.getInstance(this);

        // 1. Hero State Card
        AutomationController.State state = controller.getCurrentState();
        int remainingGrace = controller.getRemainingGraceSeconds();
        if (state == AutomationController.State.DISCONNECT_GRACE && remainingGrace > 0) {
            tvHeroStatus.setText("断开倒计时: " + remainingGrace + " 秒");
            tvHeroDetail.setText("目标设备已断开，若未重连将在 " + remainingGrace + " 秒后关闭热点省电");
        } else {
            tvHeroStatus.setText(state.getDescription());
            if (state == AutomationController.State.IDLE) {
                tvHeroDetail.setText("飞行模式 ON · 蓝牙待命 · 热点关闭 (极度省电)");
            } else if (state == AutomationController.State.ONLINE) {
                tvHeroDetail.setText("4G 数据已连通 · 热点正常发射中");
            } else {
                tvHeroDetail.setText("自动化流水线调度中...");
            }
        }

        // 2. Master Auto Button
        boolean autoEnabled = prefs.isAutoModeEnabled();
        if (autoEnabled) {
            btnMasterAuto.setText("● 自动控制运行中 (点击暂停)");
            btnMasterAuto.setBackgroundResource(R.drawable.btn_black);
            btnMasterAuto.setTextColor(Color.WHITE);
        } else {
            btnMasterAuto.setText("▶ 开启全自动热点控制");
            btnMasterAuto.setBackgroundResource(R.drawable.btn_border);
            btnMasterAuto.setTextColor(Color.BLACK);
        }

        // 3. Target Devices
        String summary = prefs.getTargetNamesSummary();
        tvTargetDevices.setText(summary);

        // 4. Accessibility Service state
        boolean isAccConnected = HotspotAccessibilityService.isServiceConnected();
        boolean isAccSysEnabled = SettingsHelper.isAccessibilityServiceEnabled(this, HotspotAccessibilityService.class);
        if (isAccConnected) {
            btnAccAction.setText("● 已就绪");
            btnAccAction.setTextColor(Color.BLACK);
        } else if (isAccSysEnabled) {
            btnAccAction.setText("连接中...");
            btnAccAction.setTextColor(Color.DKGRAY);
        } else {
            btnAccAction.setText("点击授权");
            btnAccAction.setTextColor(Color.BLACK);
        }

        // 5. Device Admin state
        boolean isAdmin = ScreenHelper.isDeviceAdminActive(this);
        if (isAdmin) {
            btnAdminAction.setText("● 已激活");
            btnAdminAction.setTextColor(Color.BLACK);
        } else {
            btnAdminAction.setText("点击激活");
            btnAdminAction.setTextColor(Color.BLACK);
        }

        // 6. Hardware status
        boolean btEnabled = mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
        boolean isAirplane = SettingsHelper.isAirplaneModeOn(this);
        tvHwState.setText(String.format("蓝牙硬件: %s | 飞行模式: %s",
                btEnabled ? "ON" : "OFF",
                isAirplane ? "ON" : "OFF"));
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
            updateUiStatus();
        }
    }

    @Override
    public void onStateChanged(AutomationController.State newState, String detail) {
        runOnUiThread(this::updateUiStatus);
    }

    @Override
    public void onGraceCountdown(int remainingSeconds) {
        runOnUiThread(this::updateUiStatus);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUiStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AutomationController.getInstance(this).unregisterListener(this);
    }
}
