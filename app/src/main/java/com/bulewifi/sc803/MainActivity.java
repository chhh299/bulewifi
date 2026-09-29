package com.bulewifi.sc803;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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

import java.util.Set;

public class MainActivity extends AppCompatActivity implements LogManager.OnLogUpdateListener {

    private static final int REQ_PERMISSIONS = 2001;

    private TextView tvBtStatus;
    private TextView tvServiceStatus;
    private TextView tvBondedDevices;
    private TextView tvLogSummary;
    private Button btnToggleService;
    private Button btnCopyLogs;
    private Button btnClearLogs;
    private Button btnTestEvent;
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
        tvServiceStatus = findViewById(R.id.tv_service_status);
        tvBondedDevices = findViewById(R.id.tv_bonded_devices);
        tvLogSummary = findViewById(R.id.tv_log_summary);
        btnToggleService = findViewById(R.id.btn_toggle_service);
        btnCopyLogs = findViewById(R.id.btn_copy_logs);
        btnClearLogs = findViewById(R.id.btn_clear_logs);
        btnTestEvent = findViewById(R.id.btn_test_event);
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
        btnToggleService.setOnClickListener(v -> {
            if (BluetoothProbeService.isRunning()) {
                BluetoothProbeService.stop(this);
                Toast.makeText(this, "正在停止探测服务...", Toast.LENGTH_SHORT).show();
            } else {
                BluetoothProbeService.start(this);
                Toast.makeText(this, "正在启动探测服务...", Toast.LENGTH_SHORT).show();
            }
            v.postDelayed(this::updateUiStatus, 300);
        });

        btnCopyLogs.setOnClickListener(v -> copyLogsToClipboard());

        btnClearLogs.setOnClickListener(v -> {
            LogManager.getInstance().clearLogs();
            Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show();
        });

        btnTestEvent.setOnClickListener(v -> {
            LogManager.getInstance().addLog("TEST_MOCK", "Test-Device-Mac", "CC:08:FA:6E:B9:5A", "Mock ACL Test Entry");
            Toast.makeText(this, "已注入测试事件", Toast.LENGTH_SHORT).show();
        });

        btnRefresh.setOnClickListener(v -> {
            updateUiStatus();
            Toast.makeText(this, "状态已刷新", Toast.LENGTH_SHORT).show();
        });
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
            tvBtStatus.setText(String.format("蓝牙: %s (State: %s)\n本机: %s (%s)",
                    enabled ? "已开启" : "已关闭",
                    BluetoothEventReceiver.getBtStateString(state),
                    name != null ? name : "SC803",
                    address != null ? address : "--"));
        }

        // Service state
        boolean isRunning = BluetoothProbeService.isRunning();
        tvServiceStatus.setText(String.format("前台探测服务: %s", isRunning ? "运行中 (ACTIVE)" : "已停止 (STOPPED)"));
        btnToggleService.setText(isRunning ? "停止服务" : "启动服务");

        // Bonded devices list
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            try {
                Set<BluetoothDevice> bonded = mBluetoothAdapter.getBondedDevices();
                if (bonded == null || bonded.isEmpty()) {
                    tvBondedDevices.setText("已配对设备: (暂无配对设备)");
                } else {
                    StringBuilder sb = new StringBuilder("已配对设备:\n");
                    for (BluetoothDevice dev : bonded) {
                        sb.append(" • ").append(dev.getName()).append(" [").append(dev.getAddress()).append("]\n");
                    }
                    tvBondedDevices.setText(sb.toString().trim());
                }
            } catch (SecurityException e) {
                tvBondedDevices.setText("已配对设备: 获取被系统权限拒绝");
            }
        } else {
            tvBondedDevices.setText("已配对设备: 需开启蓝牙后查看");
        }

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
