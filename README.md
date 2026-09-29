# SC803 自动热点 (BuleWifi) - Milestone 1: Bluetooth Event Probe

本项目旨在为腾讯/阅文**「口袋阅2代」SC803**（展讯平台，Android 8.1.0 / API 27，免 Root）开发一款自动热点伴侣应用，作为 iPad mini / Mac 的随身 4G 热点。

---

## 阶段规划概览

- [x] **Milestone 1（当前版本）**：蓝牙 ACL 连接与断开事件探测（Bluetooth Event Probe），验证普通安装 APK 在 Android 8.1 上接收系统蓝牙广播的稳定性。
- [ ] **Milestone 2**：无障碍服务（AccessibilityService）模拟点击系统设置，验证免 Root 切换飞行模式、蓝牙与 WLAN 热点。
- [ ] **Milestone 3**：目标设备白名单过滤（绑定指定 MAC 地址，防止无关设备触发）。
- [ ] **Milestone 4**：串行自动化状态机串联（含 4G 蜂窝网络恢复轮询与 60 秒断开宽限期防抖）。
- [ ] **Milestone 5**：前台保活、开机自启与异常容错优化。

---

## Milestone 1 核心功能

1. **动态注册蓝牙事件监听**：
   - `BluetoothDevice.ACTION_ACL_CONNECTED`
   - `BluetoothDevice.ACTION_ACL_DISCONNECTED`
   - `BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED`
   - `BluetoothAdapter.ACTION_STATE_CHANGED`
2. **前台保活探测服务（BluetoothProbeService）**：
   - 带有常驻通知栏提示，防止息屏或切到后台时被 Android 8.1 后台限制休眠。
3. **墨水屏专属高对比度 UI**：
   - 纯黑白界面设计，无灰阶阴影，无多余动画，适配 SC803 电子墨水屏。
   - 实时展示本机蓝牙状态、已配对设备列表、服务运行状态。
   - 提供「复制全部日志」、「清空日志」、「注入测试」、「刷新状态」快捷按钮。
4. **统一 Logcat 输出**：
   - 统一 Tag：`SC803AutoHotspot`

---

## 实机测试验证步骤

### 1. 获取与安装 APK
- **GitHub Actions 构建**：每次 Push 或手动触发 `workflow_dispatch` 后，在 GitHub 仓库的 **Actions** 页面下载 `SC803-AutoHotspot-debug` 产物。
- **ADB 安装**：
  ```bash
  adb install -r app-debug.apk
  ```

### 2. 验证流程（先使用 Mac 测试，再使用 iPad mini）
1. **SC803 准备状态**：
   - 开启飞行模式：`飞行模式 ON`
   - 重新开启蓝牙：`蓝牙 ON`
2. **启动应用**：
   - 打开「SC803 蓝牙探测」App，确认前台服务已显示「运行中」。
3. **建立连接**：
   - 用 Mac 与 SC803 建立蓝牙连接。
   - **观察**：App 界面与 Logcat 是否成功弹出 `CONNECTED` 记录，记录设备名与 MAC 地址。
4. **主动断开**：
   - 用 Mac 主动断开蓝牙连接。
   - **观察**：App 界面与 Logcat 是否成功弹出 `DISCONNECTED` 记录。
5. **回传日志**：
   - 点击 App 内的「复制全部日志」，或通过 ADB 抓取：
     ```bash
     adb logcat -v time -s SC803AutoHotspot
     ```

---

## 项目环境规范
- **开发语言**：Java (1.8)
- **编译工具**：Gradle 8.5, Android Gradle Plugin 8.2.2, JDK 17
- **SDK 版本**：`minSdkVersion 23`, `targetSdkVersion 27`, `compileSdkVersion 34`
