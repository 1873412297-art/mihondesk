# 桌面端：同步 IP 记忆 + 退出后台运行 实施计划

> 版本：v1.0
> 日期：2026-09-26
> 范围：仅 `desktop-app` 模块（含 `DesktopPreferenceStore`、`SettingsScreen`、`MihonDesktopApp`、新增托盘辅助类）
> 读者：实现工程师（agy）。前置：扫码/发现配对功能已交付（`docs/library-sync-discovery-plan.md`）。

---

## 1. 功能 A：同步服务器 IP 选择记忆

### 1.1 现状与问题

`SettingsScreen.kt` 同步卡片中的 IP 下拉选择（约 L2072）：

```kotlin
var selectedIp by remember { mutableStateOf(localIps.firstOrNull() ?: "127.0.0.1") }
```

`selectedIp` 是 Composable 内的临时状态，**重启应用后丢失**。多网卡机器（VPN/虚拟网卡会排到前面）每次启动都默认选中错误 IP，用户要重新选，配对码/二维码也随之错误。

### 1.2 方案

- `DesktopPreferences` 新增字段 `syncServerSelectedIp: String = ""`；`DesktopPreferenceStore.load()/save()` 增加 properties 键 `sync.server_selected_ip`。
- `SettingsScreen` 初始化逻辑改为：
  1. 读 `preferences.syncServerSelectedIp`；
  2. 若**非空且在 `localIps` 中** → 作为初始选中项；
  3. 否则（空、或上次记忆的 IP 当前不在列表，如换了网络）→ 回退 `localIps.firstOrNull() ?: "127.0.0.1"`。
- 下拉菜单 `onClick` 选中时同步持久化：`preferenceStore.updatePreferences { it.copy(syncServerSelectedIp = ip) }` + `onPreferencesChanged?.invoke(updated)`（与端口/Token 的持久化模式一致）。
- 配对 URI 与二维码本就由 `selectedIp` 派生，无需其他改动。

### 1.3 验收

- 选中某 IP → 重启应用 → 该 IP 仍为默认选中，配对码/二维码一致。
- 记忆的 IP 不在当前网卡列表时，回退到列表第一项，不崩溃、不留死值。
- 偏好文件 `desktop.properties`（或现有存储）中出现 `sync.server_selected_ip`。

---

## 2. 功能 B：退出后在后台运行（系统托盘）

### 2.1 现状与问题

- 点窗口关闭 → `MihonDesktopApp.closeApplication()`（L179-186）保存窗口位置后直接 `exitApplication()`，进程结束。
- 已存在的 `backgroundTasksEnabled` 是**Windows 计划任务**（登录触发/定时跑更新备份，`WindowsBackgroundScheduler`），与本功能无关，不要复用其名。
- 用户诉求：开启内置同步服务器后，关掉窗口同步服务还活着（手机随时能同步），需要真正的"最小化到系统托盘"。

### 2.2 方案

**新增偏好**：`runInBackgroundOnClose: Boolean = false`，properties 键 `background.run_in_background_on_close`。

**新增托盘辅助类** `desktop-app/.../ui/DesktopTray.kt`（或 `platform/`）：

```kotlin
class DesktopTray(
    private val appIcon: java.awt.Image,
    private val onShow: () -> Unit,
    private val onQuit: () -> Unit,
) {
    val isSupported: Boolean      // SystemTray.isSupported
    fun ensureInstalled()         // 幂等：创建 TrayIcon（tooltip "mihondesk"，弹出菜单：显示 mihondesk / 退出）
    fun showNotification(title, message)  // 首次隐藏到托盘时提示用户（可选，用 TrayIcon.displayMessage）
    fun dispose()
}
```

- 托盘图标：复用启动时已加载的 `icon.png` 字节转 `java.awt.Image`（`ImageIO.read`）。
- 菜单项文案走 `DesktopStrings`（三语实现都要加）：`trayShow`("显示 mihondesk")、`trayQuit`("退出")、`trayHiddenTitle/Hint`（首次入托盘的气泡提示）。
- **注意 Swing/AWT 线程**：TrayIcon 的创建/销毁在 `EventQueue.invokeLater` 中执行。

**`MihonDesktopApp.closeApplication()` 改造**：

```kotlin
@Volatile private var forceQuit = false

fun closeApplication() {
    if (!forceQuit && preferences.runInBackgroundOnClose && tray.isSupported && composeWindow != null) {
        tray.ensureInstalled()
        composeWindow!!.isVisible = false
        tray.showNotificationOnce(...)   // "mihondesk 仍在后台运行，点击托盘图标显示"
        return
    }
    tray.dispose()
    composeWindow?.let { /* 保存窗口位置（现有逻辑）*/ }
    exitApplication()
}
```

- "显示"：窗口 `isVisible = true` + `toFront()`。
- 托盘"退出"：`forceQuit = true` → 调 `closeApplication()` 走真正退出路径（保存位置、dispose 托盘、`exitApplication()`）。`forceQuit` 也可用普通 boolean 局部于 remember（单 Window 场景）。
- 托盘菜单"退出"回调里的退出必须在主线程安全执行（Compose 的 `exitApplication` 本身是安全的；AWT 回调里调它即可，或用 `ComposeWindow` 派发）。
- `runInBackgroundOnClose` 关闭时：行为与现在完全一致（直接退出）。已入托盘时用户在设置里关不掉开关（窗口隐藏中）——无需处理边角。

**设置页 UI**（`SettingsScreen.kt`，"通用/行为"区域，跟随现有 Switch 控件模式）：

- 开关标题 `runInBackgroundTitle`：EN "Keep running in background after closing"、简中 "关闭窗口后在后台运行"、繁中 "關閉視窗後在背景執行"。
- 说明 `runInBackgroundSummary`：EN "Close button hides the window to the system tray; sync server stays alive"、简中 "点关闭后窗口隐藏到系统托盘，同步服务器保持运行，托盘菜单可彻底退出"、繁中对应。
- 变更时持久化（`updatePreferences` + `onPreferencesChanged`）。

### 2.3 兼容与安全

- `SystemTray.isSupported == false`（极少数环境）→ 开关开启也直接退出，不静默失败（首次点关闭时如托盘不可用可在标题栏/日志给提示；简单起见直接退回退出路径即可）。
- 阅读器无边框模式、`AppUpdatePresenter` 的 `::closeApplication` 回调（更新后重启路径）→ 更新重启必须**真退出**：把 presenter 的回调改为 force-quit 版本（或让 closeApplication 的 background 分支不适用于该路径）。**实现时检查 `closeApplication` 的全部调用点，更新重启/崩溃恢复路径不得留在托盘。**
- 进程已有退出钩子（`runtime.onShutdown` 等）不动。

### 2.4 测试

| 层级 | 内容 | 位置 |
| --- | --- | --- |
| 单测 | `DesktopPreferenceStore` round-trip：新增 `syncServerSelectedIp`、`runInBackgroundOnClose` 的 load/save（对照现有测试文件写法，通常用临时目录 properties） | `desktop-app/src/test/.../preferences/`（找现有测试） |
| 单测 | IP 选择回退逻辑的纯函数：从"记忆值 + 当前可用列表"算初始选中（把该逻辑抽成 `effectiveSelectedIp(remembered, localIps)` 纯函数放 companion，可测） | 同上 |
| 单测 | `closeApplication` 决策抽纯函数 `shouldStayInBackground(enabled, traySupported, windowAvailable)` | `MihonDesktopApp` companion 或托盘类 companion |
| 手动 | 开开关→关窗→托盘存在→点"显示"恢复→托盘"退出"进程结束；关开关→关窗→进程直接结束；记忆 IP 重启保留 | 冒烟 |
| 回归 | `:desktop-app:test`、`:desktop-app:spotlessCheck` 全绿 | CI |

---

## 3. 改动文件清单

| 文件 | 改动 |
| --- | --- |
| `desktop-app/.../preferences/DesktopPreferenceStore.kt` | 两个新偏好字段 + load/save |
| `desktop-app/.../ui/settings/SettingsScreen.kt` | IP 初始选中 + 选中持久化 + `effectiveSelectedIp` 纯函数；新开关（行为区） |
| `desktop-app/.../ui/DesktopTray.kt` | **新建**，AWT SystemTray 封装（ensureInstalled/dispose/通知，EDT 安全） |
| `desktop-app/.../ui/MihonDesktopApp.kt` | `closeApplication` 后台分支 + force-quit 路径 + 检查全部调用点（AppUpdatePresenter 等） |
| `desktop-app/.../i18n/DesktopStrings.kt` | 新增：runInBackgroundTitle/Summary、trayShow、trayQuit、trayHiddenTitle、trayHiddenHint（接口 + 英/简中/繁中三实现） |
| `desktop-app/src/test/...` | 偏好 round-trip、`effectiveSelectedIp`、`shouldStayInBackground` 测试 |

非目标：不改 Android 端、不改同步协议、不动 `backgroundTasksEnabled` 计划任务语义。

## 4. 验收标准（总）

1. IP 选择重启后保留；网络变化导致记忆 IP 失效时自动回退第一项。
2. 开关关闭时行为与现状 100% 一致。
3. 开关开启时：关窗 → 进程存活 + 托盘图标；托盘"显示"恢复窗口；"退出"彻底结束进程；内置同步服务器在整个后台期间保持可连接（可用手机"测试连接"验证）。
4. 应用内"更新后重启"等程序化退出路径不受开关影响，始终真退出。
5. `:desktop-app:test`、`:desktop-app:spotlessCheck` 全绿。
