# SC803 自动热点 APK：需求说明与验证记录

> 用途：把本文档直接交给 Claude / 其他编码 Agent，在 GitHub 仓库中实现
> Android APK，并通过 GitHub Actions 生成可下载的 APK。\
> 目标设备：腾讯/阅文「口袋阅2代」SC803\
> 固件：`SC803 V29.202005281043`\
> Android：`8.1.0`，API 27\
> 原则：**不 Root**；优先使用普通 APK +
> AccessibilityService；不要重复已经完成的 ADB 验证。

------------------------------------------------------------------------

## 1. 项目最终目标

SC803 内有 SIM 卡和 4G/LTE。希望把它作为 iPad mini 的自动随身热点。

日常待机状态希望是：

``` text
飞行模式 ON
蓝牙 ON
Wi-Fi/热点 OFF
蜂窝网络 OFF
```

当目标设备（最终是 iPad mini；开发阶段可以先用 Mac）通过蓝牙与 SC803
建立连接时：

``` text
收到目标蓝牙设备 CONNECTED
        ↓
退出飞行模式
        ↓
确保蓝牙保持/恢复 ON
        ↓
等待移动数据/LTE恢复
        ↓
开启 WLAN 热点
```

当目标蓝牙设备断开时：

``` text
收到目标设备 DISCONNECTED
        ↓
等待约 60 秒（防止短暂断线）
        ↓
若目标设备没有重新连接
        ↓
关闭 WLAN 热点
        ↓
开启飞行模式
        ↓
重新打开蓝牙
        ↓
回到待机状态：
飞行模式 ON + 蓝牙 ON + 热点 OFF
```

60 秒延迟应做成可配置参数，第一版默认 60 秒。

------------------------------------------------------------------------

## 2. 非目标 / 不要走的路线

### 2.1 不要求 Root

当前方案明确以 no-root 为目标。

### 2.2 不要假定普通 APK 可以直接调用系统 Tethering API

设备实测：

``` text
android.permission.TETHER_PRIVILEGED
protectionLevel = signature|privileged
```

普通安装 APK 无法通过 `pm grant` 获得该权限。

系统手动开启热点时 logcat 显示：

``` text
I/ConnectivityManager: startTethering caller:com.android.settings
I/WifiService: startSoftAp uid=1000
```

因此真正有权限启动 tethering 的是系统 Settings（UID 1000）。

### 2.3 不要依赖 `adb shell cmd wifi`

SC803 上执行相关命令已经确认被拒绝：

``` text
Security exception: Uid 2000 does not have access to wifi commands
WifiShellCommand.checkRootPermission
```

### 2.4 不要把 `settings put global airplane_mode_on` 当成完整飞行模式切换

已实测：

``` bash
adb shell settings put global airplane_mode_on 1
```

可以修改数据库值，但：

``` bash
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true
```

返回：

``` text
java.lang.SecurityException:
Permission Denial: not allowed to send broadcast
android.intent.action.AIRPLANE_MODE
from uid=2000
```

因此只修改 `airplane_mode_on`
会产生"数据库值改变，但无线电状态没有真正同步"的假状态。

飞行模式应通过系统 Settings UI + AccessibilityService 操作。

------------------------------------------------------------------------

## 3. 已经完成的设备验证 ------ 不要重复

以下均已在真实 SC803 上完成。

### 3.1 ADB 环境

Mac 可以正常连接：

``` text
adb devices
S803MCKC10002086    device
```

ADB shell UID 为 2000。

### 3.2 热点本身工作正常

手动打开 SC803 的 WLAN 热点后：

``` text
tetherableWifiRegexs: [wlan0]
tetherableBluetoothRegexs: [bt-pan]

Tether state:
    wlan0 - TetheredState - lastError = 0

LinkAddresses: [192.168.43.1/24]

SET master tether settings: ON
```

LTE 可以作为上游，热点网关为 `192.168.43.1/24`。

相关日志包括：

``` text
D/SoftApManager: mUseSprdCustomSoftap = true; max_num_sta = 10
D/SoftApManager: Soft AP is started
D/SoftApManager: SoftAp is ready for use
D/Tethering: Tethering wlan0
D/TetherInterfaceSM: Tethered wlan0
D/TetherController: Setting IP forward enable = 1
D/TetherController: Starting tethering services
```

设备存在 Spreadtrum/展讯定制，但 Settings 的上层流程仍是 Android 8.1
的标准 tethering/SoftAP 流程。

### 3.3 热点 Settings UI 已确认可被 Accessibility 识别

"热点和网络共享"页面中存在：

``` text
text="WLAN 热点"
resource-id="android:id/title"
```

其开关：

``` text
resource-id="android:id/switch_widget"
class="android.widget.Switch"
checked="false"   # 关闭状态示例
```

"WLAN 热点"所在整行父节点：

``` text
class="android.widget.LinearLayout"
clickable="true"
bounds="[0,357][720,553]"
```

因此最终实现应：

1.  找到 `text="WLAN 热点"`；
2.  找到其所属 Preference / 可点击父节点；
3.  找到同一项内的 `android:id/switch_widget`；
4.  根据 `checked` 判断当前状态；
5.  只有目标状态与当前状态不同时才 `ACTION_CLICK`。

**不要使用固定坐标作为正式实现。**

### 3.4 飞行模式 Settings 页面可以启动

执行：

``` bash
adb shell am start -a android.settings.AIRPLANE_MODE_SETTINGS
```

SC803 会打开"网络和互联网"页面。

该页面 UI 树中：

``` text
text="飞行模式"
resource-id="android:id/title"
```

对应开关：

``` text
resource-id="android:id/switch_widget"
class="android.widget.Switch"
checked="true"    # 飞行模式开启时
```

其整行：

``` text
class="android.widget.LinearLayout"
clickable="true"
bounds="[0,793][720,908]"
```

真实设备已经用点击测试两个方向：

``` text
飞行模式 ON → 点击 Settings 中该项 → airplane_mode_on: 1 → 0
```

手机实际退出飞行模式、蜂窝恢复。

反方向：

``` text
飞行模式 OFF → 点击 Settings 中该项 → airplane_mode_on: 0 → 1
```

手机实际进入飞行模式。

因此：

**飞行模式 ON/OFF 通过系统 Settings UI 是已验证可行的。**

### 3.5 飞行模式的无线电配置

设备返回：

``` text
airplane_mode_radios:
cell,bluetooth,wifi,nfc,wimax

airplane_mode_toggleable_radios:
bluetooth,wifi,nfc
```

实际行为：

-   开启飞行模式时，蓝牙会一起被关闭；
-   进入飞行模式后，可以再单独开启蓝牙。

这正是最终状态机需要处理的行为。

### 3.6 飞行模式 ON 时，蓝牙可以重新打开

在"蓝牙"详情页，UI 树：

``` text
resource-id="com.android.settings:id/switch_bar"
class="android.widget.Switch"
checked="false"
clickable="true"
bounds="[0,161][720,273]
```

内部开关：

``` text
resource-id="com.android.settings:id/switch_widget"
class="android.widget.Switch"
checked="false"
clickable="true"
```

真实设备在飞行模式 ON 时点击该开关后，再次 dump UI：

``` text
resource-id="com.android.settings:id/switch_bar"
checked="true"
clickable="true"
```

手机蓝牙实际成功开启。

因此已确认：

``` text
飞行模式 ON + 蓝牙 ON
```

可以稳定共存。

### 3.7 蓝牙系统层连接/断开事件已确认

测试设备先使用 Mac 与 SC803 蓝牙连接。

`dumpsys bluetooth_manager` 出现：

``` text
Connection Events:
09-29 13:17:54.434 CONNECTED    cc:08:fa:6e:b9:5a
09-29 13:17:54.434 CONNECTED    cc:08:fa:6e:b9:5a
```

Mac 主动断开后：

``` text
09-29 13:19:11.233 DISCONNECTED cc:08:fa:6e:b9:5a reason=19
09-29 13:19:11.233 DISCONNECTED cc:08:fa:6e:b9:5a reason=19
```

说明 SC803 的 Bluetooth stack 确实能感知 ACL 连接和断开。

重复两条事件暂时不要过度解读，应用层需要做幂等/去重。

**尚未验证：普通第三方 APK 是否能稳定收到 `ACTION_ACL_CONNECTED` /
`ACTION_ACL_DISCONNECTED`。这是第一版 APK 的首要验证目标。**

------------------------------------------------------------------------

## 4. 第一阶段 APK：Bluetooth Event Probe

不要第一版就把全部自动化逻辑混在一起。

第一版 APK 的唯一核心任务：

> 验证普通安装 APK 在 SC803 Android 8.1 上能否稳定收到目标设备的蓝牙 ACL
> CONNECTED / DISCONNECTED 广播。

### 4.1 技术建议

建议 Java，实现兼容 Android 8.1。

建议：

``` text
minSdk: 23（或更低，只要不增加复杂度）
targetSdk: 27
compileSdk: 可使用 GitHub Actions 当前方便安装的较新 SDK
```

不要因为 compileSdk 较新就把 targetSdk
提升到现代版本；本项目目标机固定为 Android 8.1/API 27。

Manifest 至少考虑：

``` xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
```

Android 8.1 不需要 Android 12 的 `BLUETOOTH_CONNECT` 才能按旧模型工作。

### 4.2 监听事件

监听：

``` java
BluetoothDevice.ACTION_ACL_CONNECTED
BluetoothDevice.ACTION_ACL_DISCONNECTED
```

第一版优先使用运行中的 Activity/Service 动态注册 Receiver，避免一开始被
manifest implicit broadcast 等行为干扰。

界面显示完整事件日志：

``` text
2026-xx-xx xx:xx:xx
CONNECTED
name: ...
address: ...

2026-xx-xx xx:xx:xx
DISCONNECTED
name: ...
address: ...
```

同时写 Logcat，例如统一 tag：

``` text
SC803AutoHotspot
```

### 4.3 第一阶段验收

SC803：

``` text
飞行模式 ON
蓝牙 ON
```

然后：

1.  Mac 与 SC803 建立蓝牙连接；
2.  APK 必须显示 `CONNECTED`；
3.  Mac 断开；
4.  APK 必须显示 `DISCONNECTED`。

如果 App 层收不到广播，先调查广播注册方式、后台限制和 ROM
行为，不要直接开始写最终状态机。

通过 Mac 后，再用最终目标 iPad mini 测试。

重要：

**"已配对"不等于"保持 ACL 已连接"。**

iPad 是否能像 Mac 一样形成稳定、可检测的 ACL
连接仍需实机验证。不要在代码里假定 iPad 一定可以。

------------------------------------------------------------------------

## 5. 第二阶段 APK：Accessibility 控制验证

第一阶段蓝牙广播验证成功后，再加入 AccessibilityService。

### 5.1 AccessibilityService 基本要求

需要：

``` xml
android.permission.BIND_ACCESSIBILITY_SERVICE
```

以及对应 accessibility-service XML 配置。

用户第一次安装后手动到系统设置开启该无障碍服务。

服务主要观察：

``` text
packageName = com.android.settings
```

### 5.2 飞行模式控制

打开：

``` java
Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
```

在 SC803 上该 Intent 已确认会进入"网络和互联网"。

算法不要依赖坐标：

``` text
查找 text="飞行模式"
→ 找到所属 Preference
→ 找到同一项的 android:id/switch_widget
→ 读取 checked
→ 若 current != desired：
       对 clickable 父节点 ACTION_CLICK
→ 等待 UI 更新
→ 再次读取 checked 验证
```

必须实现超时和失败日志。

### 5.3 蓝牙开启

可以优先尝试 Android 8.1 普通 BluetoothAdapter API；如果
ROM/权限/状态导致不可靠，则使用已经验证过的 Settings UI。

UI fallback：

进入蓝牙 Settings 页面后查找：

``` text
resource-id="com.android.settings:id/switch_bar"
```

该控件本身：

``` text
class=android.widget.Switch
clickable=true
checked=true/false
```

根据 `checked` 决定是否 `ACTION_CLICK`。

### 5.4 WLAN 热点控制

进入系统的"热点和网络共享"页面。

不要调用需要 `TETHER_PRIVILEGED` 的 tethering API 作为正式方案。

查找：

``` text
text="WLAN 热点"
```

再读取同一 Preference 中：

``` text
resource-id="android:id/switch_widget"
```

依据 `checked` 幂等地切换。

------------------------------------------------------------------------

## 6. 最终状态机设计

建议集中实现一个
`AutomationController`，所有事件进入同一个串行状态机，避免蓝牙广播、Accessibility
回调和延时任务互相打架。

建议状态：

``` text
IDLE
EXITING_AIRPLANE
WAITING_CELLULAR
ENABLING_HOTSPOT
ONLINE
DISCONNECT_GRACE
DISABLING_HOTSPOT
ENTERING_AIRPLANE
RESTORING_BLUETOOTH
ERROR
```

### 6.1 CONNECTED

仅目标设备触发。

``` text
CONNECTED(target)
    ↓
取消 pending shutdown
    ↓
若已经 ONLINE：不重复执行
    ↓
确保飞行模式 OFF
    ↓
等待系统实际退出飞行模式
    ↓
确保 Bluetooth ON
    ↓
等待移动网络可用
    ↓
开启 WLAN 热点
    ↓
确认热点 checked=true
    ↓
ONLINE
```

### 6.2 DISCONNECTED

``` text
DISCONNECTED(target)
    ↓
启动 60 秒 grace period
    ↓
若期间重新 CONNECTED：
    取消关机流程
    保持热点
    ↓
60 秒后仍未连接：
    关闭热点
    ↓
确认热点 OFF
    ↓
开启飞行模式
    ↓
确认飞行模式 ON
    ↓
由于 SC803 开飞行模式会关闭蓝牙：
    再次开启蓝牙
    ↓
确认 Bluetooth ON
    ↓
IDLE
```

### 6.3 幂等要求

所有操作必须是：

``` text
setAirplaneMode(desired)
setBluetooth(desired)
setHotspot(desired)
```

而不是：

``` text
toggleAirplaneMode()
toggleBluetooth()
toggleHotspot()
```

每次操作前必须读取当前状态。

重复 CONNECTED / DISCONNECTED 不得造成反复切换。

------------------------------------------------------------------------

## 7. 移动网络等待逻辑

退出飞行模式后不能简单固定 `sleep(2s)` 就开热点。

优先监听 Android 网络状态，判断 cellular/mobile 网络是否已经可用。

Android 8.1 可考虑 `ConnectivityManager` / `NetworkCallback`（按 API 27
可用能力实现）。

需要：

-   检测 mobile/cellular transport；
-   最好确认网络具有 Internet 能力；
-   设置合理超时，例如 30 秒；
-   超时时记录日志，不要无限等待。

由于 SC803 是旧 ROM，实际实现应允许 fallback：

``` text
退出飞行模式
→ 轮询移动网络状态
→ 成功则继续
→ 30 秒超时则报告 ERROR / 可选继续尝试热点
```

不要假定 LTE 恢复时间恒定。

------------------------------------------------------------------------

## 8. 目标蓝牙设备选择

最终不要对"任何蓝牙设备连接"都开启热点。

App 应提供：

``` text
已配对设备列表
→ 用户选择目标设备
→ 保存 MAC address（首选）
→ 同时保存显示名称用于 UI
```

之后只有匹配目标 MAC 的：

``` text
ACTION_ACL_CONNECTED
ACTION_ACL_DISCONNECTED
```

才进入自动化状态机。

开发阶段可以使用已测试的 Mac，后续再切换到 iPad mini。

------------------------------------------------------------------------

## 9. 后台运行

目标机 Android 8.1，有后台执行限制，同时国产/定制 ROM 可能有额外限制。

最终版本建议：

-   使用 Foreground Service 保持自动化监听；
-   常驻通知显示：
    -   当前目标设备；
    -   当前状态（待机/联网/等待断开/错误）；
-   Receiver 可以在 Foreground Service 内动态注册；
-   开机后是否自动恢复服务可作为后续功能；
-   如需要 `BOOT_COMPLETED`，单独加入并实机验证。

不要第一阶段就过度实现后台保活；先验证 ACL 广播。

------------------------------------------------------------------------

## 10. UI 建议

最终主界面尽量简单：

``` text
SC803 Auto Hotspot

服务状态：运行中
无障碍服务：已开启

目标设备：
iPad mini
XX:XX:XX:XX:XX:XX

当前：
飞行模式：ON
蓝牙：ON
热点：OFF
目标蓝牙：未连接

断开延迟：60 秒

[选择目标蓝牙设备]
[开启无障碍设置]
[开始/停止自动控制]

最近日志：
...
```

开发/调试版本额外提供：

``` text
[测试退出飞行模式]
[测试进入飞行模式]
[测试开启蓝牙]
[测试开启热点]
[测试关闭热点]
[清空日志]
[复制日志]
```

这些按钮必须调用与自动状态机相同的 Controller，不要另写一套逻辑。

------------------------------------------------------------------------

## 11. 日志要求

这是老设备适配项目，日志非常重要。

统一 Logcat tag：

``` text
SC803AutoHotspot
```

每一步至少记录：

``` text
BT ACL CONNECTED: name=..., address=...
BT ACL DISCONNECTED: name=..., address=...

REQUEST airplane=false
CURRENT airplane=true
OPEN Settings.ACTION_AIRPLANE_MODE_SETTINGS
FOUND node 飞行模式
CLICK airplane preference
VERIFY airplane=false OK

WAIT cellular...
CELLULAR available

REQUEST hotspot=true
OPEN tether settings
FOUND WLAN 热点
CURRENT checked=false
CLICK
VERIFY hotspot=true OK
```

失败时记录：

``` text
ERROR: node not found
ERROR: settings activity timeout
ERROR: cellular timeout
ERROR: accessibility disabled
```

界面内保留最近若干百行日志，方便直接复制给开发者，不必每次接 ADB。

------------------------------------------------------------------------

## 12. GitHub 项目和自动构建要求

请直接建立可编译的 Android Gradle 项目，并加入 GitHub Actions。

目标：

-   push 到 GitHub 后自动构建 debug APK；
-   Actions 页面可以下载 APK artifact；
-   最好同时支持手动 `workflow_dispatch`；
-   不需要签正式 release key 才能进行第一阶段测试；
-   debug APK 即可 `adb install -r`。

建议 workflow 思路：

``` yaml
name: Build APK

on:
  push:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
      - run: chmod +x gradlew
      - run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: SC803-AutoHotspot-debug
          path: app/build/outputs/apk/debug/app-debug.apk
```

具体 JDK/AGP/Gradle 版本请由实现者选择一组彼此兼容、GitHub Actions
能稳定构建的版本。

**验收标准是 Actions 绿色通过并产出可安装 APK，不要只提交源码。**

------------------------------------------------------------------------

## 13. 开发顺序 ------ 严格按阶段做

### Milestone 1：Bluetooth Probe

只实现：

-   蓝牙 ACL CONNECTED / DISCONNECTED 监听；
-   UI 日志；
-   Logcat 日志；
-   GitHub Actions 构建 APK。

先在 SC803 + Mac 上验证。

### Milestone 2：Accessibility Manual Controls

加入：

-   AccessibilityService；
-   手动测试飞行模式 ON/OFF；
-   手动测试蓝牙 ON；
-   手动测试热点 ON/OFF；
-   所有操作读取状态并验证结果。

### Milestone 3：目标设备过滤

-   列出 bonded devices；
-   用户选择目标；
-   保存 MAC；
-   只响应目标设备。

### Milestone 4：自动状态机

串联：

``` text
CONNECTED
→ airplane OFF
→ wait cellular
→ hotspot ON

DISCONNECTED
→ 60s
→ hotspot OFF
→ airplane ON
→ bluetooth ON
```

### Milestone 5：可靠性

再处理：

-   重复广播去重；
-   60 秒内重连取消关闭；
-   Foreground Service；
-   Accessibility 超时/重试；
-   手机重启后的恢复；
-   Settings 页面跳转失败；
-   LTE 恢复慢；
-   用户手动改变状态；
-   iPad mini 实机连接行为。

------------------------------------------------------------------------

## 14. 已知风险 / 仍需实机验证

### 风险 A：第三方 APK 是否能收到 ACL 广播

系统 Bluetooth stack 已确认有 CONNECTED/DISCONNECTED 事件。

**App 层尚未验证。**

这是 Milestone 1 要解决的问题。

### 风险 B：iPad mini 是否保持可用的蓝牙 ACL connection

Mac 已经验证连接/断开可以被 SC803 Bluetooth stack 识别。

但 iPad mini 的蓝牙行为可能不同。

不要把"已配对"当成"已连接"。

Milestone 1 通过 Mac 后必须用 iPad 再测。

### 风险 C：Accessibility 节点时序

UI 树已经确认目标节点存在，但 APK 自动打开 Settings 后需要等待页面加载。

实现必须：

-   等待 `TYPE_WINDOW_STATE_CHANGED` / `TYPE_WINDOW_CONTENT_CHANGED`；
-   有超时；
-   必要时短间隔重试查找节点；
-   不要固定 sleep 后盲点。

### 风险 D：系统设置页面被用户停留/切换

Accessibility Controller 应根据当前
package、页面文本和节点状态判断，不应假定每次从同一页面开始。

------------------------------------------------------------------------

## 15. 给编码 Agent 的明确要求

请不要重新讨论"理论上 Android 能不能控制热点"。

真实设备已经确认：

1.  普通 shell 无权直接使用 Wi-Fi shell command；
2.  `TETHER_PRIVILEGED` 是 signature/privileged；
3.  系统 Settings 可以正常启动 SoftAP；
4.  Settings 的"WLAN 热点"控件已经从 UI hierarchy 中确认；
5.  飞行模式 Settings 控件已经确认；
6.  飞行模式 ON/OFF 已实际点击验证；
7.  飞行模式下重新打开蓝牙已实际验证；
8.  Bluetooth stack 已实际记录 Mac 的 CONNECTED / DISCONNECTED。

因此实现应以：

``` text
Bluetooth events
        +
AccessibilityService 驱动系统 Settings
        +
串行状态机
```

作为主路线。

第一提交不要做"大而全"的最终版本。

**第一提交必须先交付可安装的 Bluetooth Probe APK，并通过 GitHub Actions
构建。**

------------------------------------------------------------------------

## 16. 第一版完成后需要用户回传的信息

第一版 APK 安装到 SC803 后，只测试：

``` text
飞行模式 ON
蓝牙 ON
```

Mac 连接 SC803：

``` text
APK 是否显示 ACTION_ACL_CONNECTED？
设备名？
MAC？
是否重复收到？
```

Mac 断开：

``` text
APK 是否显示 ACTION_ACL_DISCONNECTED？
是否重复收到？
```

然后再测试 iPad mini。

只有这一步通过后，继续 Milestone 2。

------------------------------------------------------------------------

## 17. 当前结论

截至本文档：

``` text
[已验证] SC803 热点功能正常
[已验证] LTE 可以作为热点上游
[已验证] 普通 shell 无法直接使用 Wi-Fi tethering shell command
[已验证] TETHER_PRIVILEGED 不适合普通 APK
[已验证] 飞行模式 Settings 页面可启动
[已验证] 飞行模式 UI 节点可识别
[已验证] Settings UI 可完整关闭飞行模式
[已验证] Settings UI 可完整开启飞行模式
[已验证] 飞行模式下可单独重新开启蓝牙
[已验证] SC803 Bluetooth stack 能记录 Mac CONNECTED
[已验证] SC803 Bluetooth stack 能记录 Mac DISCONNECTED

[待验证] 普通 APK 能否收到 ACL CONNECTED / DISCONNECTED
[待验证] iPad mini 是否能形成稳定可检测的蓝牙连接
[待验证] APK AccessibilityService 实际 ACTION_CLICK 控制飞行模式
[待验证] APK AccessibilityService 实际 ACTION_CLICK 控制热点
[待验证] 最终完整状态机长期运行稳定性
```

开发请从第一个"待验证"项目开始，不要重复前面的设备测试。
