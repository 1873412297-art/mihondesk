# 扫码与局域网自动发现配对（方案 D）实施计划

> 版本：v1.0
> 日期：2026-09-25
> 前置：内置同步服务器（`sync-server` + `sync-transport-http` + 手动配对码）已交付，配对码 URI 格式 `mihonsync://<host>:<port>#<token>` 已落地（`sync-transport-http/.../SyncPairingCode.kt`）。
> 本文档是 `docs/library-sync-builtin-plan.md` §2 方案 D 与 §7 Phase B 的详细设计，供实现工程师（人或 agy）对照实施。
> 目标读者：实现工程师。协议章节对照 `docs/library-sync-plan.md`。

---

## 1. 背景与目标

### 1.1 问题

内置同步服务器已可用，但配对流程是纯手动的：用户要在桌面端设置页复制 `mihonsync://192.168.x.x:45831#<64位hex>` 字符串，再到手机端粘贴。两个痛点：

- **长字符串手动搬运易错**：IP 选错（多网卡机器暴露一堆地址）、复制半截、端口输错。
- **免输入是刚需**：同 Wi-Fi 场景下，用户预期"手机点一下就连上电脑"，而不是搬运令牌。

### 1.2 目标（本期交付）

| # | 功能 | 验收标准 |
| --- | --- | --- |
| 1 | 桌面端展示**配对二维码**（编码完整配对 URI） | 手机用任意扫码器扫桌面二维码即可拿到 URI；手机 App 内扫码直接完成配对 |
| 2 | **mDNS/NSD 局域网自动发现**：桌面广播服务，手机列出附近可配对设备 | 同 Wi-Fi 下，手机"搜索局域网设备"能在数秒内列出电脑（设备名 + 地址） |
| 3 | **免输入一键配对**：桌面开启"允许免输入配对"后，手机点击发现到的设备即完成配对（无需扫码/粘贴） | 开启后点击设备 → 配对完成并可"测试连接"成功 |
| 4 | 手动配对码路径**完整保留**作为兜底 | 现有 `SyncPairingCodeTest`、设置项与端到端同步行为不变 |

### 1.3 非目标（明确排除）

- 不做 NAT 穿透 / 中继 / 跨网络发现（继续用文件 Transport 或后续独立服务器）。
- 不做端到端加密、HTTPS（沿用 §builtin-plan §6 的局域网明文 + token 模型）。
- 不做相册选图扫码（只做相机实时扫描；外部扫码器经 intent-filter 兜底已覆盖该场景）。
- 不改变配对码 URI 格式、changeset 协议、`SyncEngine` 行为。
- 不改 Windows 防火墙自动配置（继续用 UI 提示文案引导）。

---

## 2. 方案设计

### 2.1 配对 URI 格式（不变）

```
mihonsync://<host>:<port>#<token>
```

`SyncPairingCode.toUriString()` / `parseOrNull()` 已实现并被双端共用，本期**零改动**，二维码即编码该字符串。解析侧已兼容 `http(s)://` 前缀与缺省端口，扫码得到的任意变体都能解析。

### 2.2 桌面端：二维码展示（zxing-core）

- 新依赖 `com.google.zxing:core:3.5.3`（纯 JVM，~600KB，无原生组件），加入 `gradle/libs.versions.toml`。
- 在 `desktop-app` 新增 `ui/common/QrCodeImage.kt`：`rememberQrBitmap(content, sizePx)` —— `MultiFormatWriter` 生成 `BitMatrix`，转成 `ImageBitmap` 用 Compose `Image` 渲染；提供 `generateQrBitmap(content, sizePx): ImageBitmap?` 纯函数便于单测（非法内容/过长内容返回 null 或允许 QR 容错 L 降级）。
- 设置页"书架同步"卡片（`SettingsScreen.kt` 配对码区块，行号约 2052–2132）改造：
  - 配对码输入框上方显示二维码（约 200dp，带 `testTag("sync-server-qr")`），内容 = 当前选中 IP 的 `pairingUri`；token 为空时隐藏。
  - 二维码与下方文本框同源（同一个 `pairingUri`），IP 切换菜单切换后二维码同步刷新。
  - 屏幕上的配对码文本本就完整展示 token，二维码不引入新的信息暴露面。

### 2.3 桌面端：mDNS 服务广播（JmDNS）

- 新依赖 `org.jmdns:jmdns:3.5.9`（纯 JVM，~250KB），由 **`sync-server` 模块**依赖（保持"server 可独立部署"的 Phase C 形态，广告逻辑不落到 `desktop-app`）。
- `sync-server` 新增 `SyncServiceAdvertiser`：

```kotlin
class SyncServiceAdvertiser(
    val serviceType: String = "_mihonsync._tcp.local.",
) : AutoCloseable {
    fun start(hostAddress: InetAddress, port: Int, deviceName: String, txt: Map<String, String>): Boolean
    fun stop()
    val isAdvertising: Boolean
}
```

  TXT record 键约定（大小写敏感，手机端按同键读取）：

| 键 | 取值 | 说明 |
| --- | --- | --- |
| `v` | `1` | 协议版本，手机端校验，不匹配则忽略该服务 |
| `name` | 设备显示名（桌面设置可改，默认主机名） | 手机设备列表展示 |
| `token` | 仅在"允许免输入配对"开启时出现 | **安全敏感键**，见 §2.5 |

- `DesktopSyncServerManager` 扩展：
  - `start(port, token)` 成功后按偏好启动 advertiser；`stop()` 先停 advertiser 再停服务器。
  - 新增 `updateAdvertisement(deviceName, quickPairEnabled)`：运行中开关免输入配对 / 改设备名时热更新 TXT（不重启服务器）。
  - 广告失败（如多播被禁）**不影响** HTTP 服务器本身，仅记录 `lastError` 供 UI 显示降级提示。
- 桌面偏好（`DesktopPreferenceStore` / preferences data class）新增：
  - `syncServerQuickPair: Boolean`（默认 **false**）
  - `syncServerDeviceName: String`（默认 `System.getProperty("user.name")` + `"的电脑"` 或主机名，截断 60 字符）
- 设置页同步卡片新增："允许免输入配对（在局域网中公布配对令牌）"开关 + 设备名输入框；开关变更调用 `updateAdvertisement`。

### 2.4 Android 端：NSD 自动发现

- **零新依赖**：用 framework `android.net.nsd.NsdManager`。
- 新增 `app/.../data/sync/LanSyncDiscovery.kt`：

```kotlin
data class DiscoveredSyncService(
    val serviceName: String,      // 广播名（去重键）
    val deviceName: String,       // TXT name 键
    val host: String,
    val port: Int,
    val token: String?,           // TXT token 键；无则 null
    val version: Int,             // TXT v 键；缺失按 1 处理
)

class LanSyncDiscovery(context: Context) : AutoCloseable {
    fun discoveries(): Flow<List<DiscoveredSyncService>>  // 发现/解析/消失持续更新
    fun refresh()      // 重新 startDiscovery（超时后由 UI 触发）
    override fun close()  // 注销 listener、stopDiscovery
}
```

  实现要点：
  - `NsdManager.discoverServices("_mihonsync._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)`。
  - 每个 `onServiceFound` 异步 `resolveService`（并发解析上限 4，其余排队；解析失败静默丢弃）。
  - **token 读取**：`NsdServiceInfo.getAttributes()` 自 API 21+ 可读（旧系统解析器可能返回空 map，属正常降级）。
  - `onServiceLost` 从列表移除；`v` 键存在且 ≠ 1 时忽略该服务（向前兼容）。
  - 内部用 `callbackFlow` 或 listener + `MutableStateFlow`；进程内单实例由 `appGraph` 提供（仿 `syncPreferences` 注入方式），`close` 幂等。
- `SettingsLibrarySyncScreen` 改造（`transportItems` 中、配对码输入项附近）：
  - 新增 `TextPreference` "搜索局域网设备"：点击展开全屏 dialog（或 push 一个简单 Screen）——顶部"正在搜索…/重新搜索"，列表展示 `DiscoveredSyncService`（设备名 + `host:port`）。
  - 点击设备：
    - `token != null` → `SyncPairingCode(host, port, token)` 直接 `updateFromPairingCode` + toast 配对成功 → 关闭 dialog。
    - `token == null` → 仅预填 `syncHttpHost`/`syncHttpPort` + toast 提示"该设备未开启免输入配对，请扫码或粘贴配对码"。
  - dialog 销毁时 `close()` discovery（或复用单例并停止发现）。
- 文案统一进 `SyncStrings`（与现有字符串同一风格，中文硬编码与现有一致——跟随 `SyncStrings.kt` 现状）。

### 2.5 Android 端：扫码配对（CameraX + zxing-core）

- 新依赖（仅 `app`）：
  - `com.google.zxing:core:3.5.3`（与桌面同一版本键）
  - CameraX：`androidx.camera:camera-core/camera-camera2/camera-lifecycle/camera-view`（版本与 BOM 对齐，写进 `libs.versions.toml`，camera 1.3.4）
- 新增 `presentation/more/settings/screen/ScanSyncPairingScreen.kt`（`Screen()`，Voyager）：
  - 布局：`AndroidView { PreviewView }` 全屏预览 + 顶部标题栏 + 中部半透明扫描框 + 底部提示文案。
  - 权限：进入时检查 `Manifest.permission.CAMERA`，未授予用 `rememberLauncherForActivityResult(RequestPermission)` 请求；永久拒绝时显示"去设置开启相机权限"引导文案。
  - 分析管线：`ProcessCameraProvider`（`LocalContext.current` + `ContextCompat.getMainExecutor`）绑定 `ImageAnalysis`（`STRATEGY_KEEP_ONLY_LATEST`）；Analyzer 内取 `ImageProxy` YUV buffer 构造 `com.google.zxing.PlanarYUVLuminanceSource` + `HybridBinarizer` + `MultiFormatReader.decode`，只认 `BarcodeFormat.QR_CODE`；成功后 `imageProxy.close()` 并回到主线程处理。
  - 解码结果 → `SyncPairingCode.parseOrNull(result.text)`：
    - 成功 → `syncPreferences.updateFromPairingCode(parsed)` + toast 成功 → `navigator.pop()`。
    - 失败 → 底部 toast "不是有效的 Mihon 同步二维码"并继续扫（间隔节流 ≥1.5s 防刷屏）。
  - 生命周期：`DisposableEffect` 解绑 camera；`onDispose` 释放 executor。
- `AndroidManifest.xml`：
  - `<uses-permission android:name="android.permission.CAMERA" />`
  - `<uses-feature android:name="android.hardware.camera" android:required="false" />`
  - ScanScreen Activity 的 intent-filter：`action VIEW` + `category DEFAULT/BROWSABLE` + `data scheme "mihonsync"` —— 用户用任意外部扫码器扫桌面二维码点"打开链接"也能直达配对页（在 Activity `onCreate`/`onNewIntent` 解析 `intent.dataString` 走同一 `parseOrNull` 路径）。该 Activity 的 intent 处理若涉及既有 Activity（如 `MainActivity`），走最小改动：在 ScanScreen 所在宿主读取启动 intent，忽略非本屏直达的场景不算失败。
- `SettingsLibrarySyncScreen` 新增 `TextPreference` "扫码配对"（与"搜索局域网设备"并列），`LocalNavigator.currentOrThrow.push(ScanSyncPairingScreen)`。

### 2.6 免输入配对的安全模型（必须照做）

token 通过 mDNS TXT **被动广播**给整个局域网，意味着"能连该局域网的人即可静默读写同步数据"。因此：

- 默认 **关闭**（`syncServerQuickPair` 默认 false）：TXT 只含 `v` + `name`。手机发现设备后仍需扫码或粘贴拿 token。
- 开启是用户显式选择：开关旁必须带说明文案（"同一 Wi-Fi 下的任何设备将无需扫码即可连接你的书架同步"）。
- 桌面"重新生成 token"后 advertiser 同步更新 TXT（走 `updateAdvertisement`）。
- 设置页与 `docs/library-sync-smoke-test.md` 的安全说明保持一致的口径（明文 + token = 局域网信任边界）。

### 2.7 双端配对流程总览（交付后）

```
桌面：开启内置服务器 ──► 设置页显示配对码文本 + 二维码 ──►（可选）开启"允许免输入配对"
                                        │
手机（同 Wi-Fi）                         │
 ├─ 路径 A：设置 → 扫码配对 → 扫桌面二维码 ─────► 完成配对
 ├─ 路径 B：设置 → 搜索局域网设备 → 点设备 ─────► token 公布? 是→完成配对 / 否→预填地址
 └─ 路径 C（兜底）：手动粘贴 mihonsync://...（现有路径不变）
```

---

## 3. 各模块改动清单

| 模块 | 改动 | 文件（现有锚点） |
| --- | --- | --- |
| `gradle/libs.versions.toml` | 新增 `zxing-core = "3.5.3"`、`jmdns = "3.5.9"`、CameraX 4 件套 1.3.4 | — |
| `sync-server` | 新增 `SyncServiceAdvertiser`（JmDNS）+ 集成测试 | `SyncServer.kt` 同包 |
| `sync-transport-http` | 无改动（`SyncPairingCode` 复用） | — |
| `desktop-app` | 依赖 +zxing；`QrCodeImage.kt` 新文件；`DesktopSyncServerManager` 加广告生命周期；偏好加 `syncServerQuickPair`/`syncServerDeviceName`；`SettingsScreen.kt` 同步卡片加二维码/开关/设备名 | `SettingsScreen.kt:2052` 配对码区块 |
| `app` | 依赖 +zxing +CameraX；`LanSyncDiscovery.kt` 新文件；`ScanSyncPairingScreen.kt` 新文件；`SettingsLibrarySyncScreen.kt` 加两个入口；`AndroidManifest.xml` 权限与 intent-filter；`SyncStrings.kt` 新文案 | `SettingsLibrarySyncScreen.kt:80` 配对码项 |
| `docs` | 更新 `library-sync-builtin-plan.md` §7 Phase B 状态（扫码配对、NSD 两项标记已交付）；`library-sync-smoke-test.md` 增加"免输入配对"用例 | — |

---

## 4. 测试计划

| 层级 | 内容 | 位置 |
| --- | --- | --- |
| 单测 | zxing 编解码往返：生成 `mihonsync://…` QR BitMatrix → 解码 → 与 `SyncPairingCode.parseOrNull` 断言一致 | `desktop-app/src/test` 或 `sync-transport-http/src/test` |
| 单测 | `DiscoveredSyncService` 从 TXT map 构造的纯函数逻辑（含缺键/版本不匹配/超长名） | `app/src/test/.../data/sync/` |
| 单测 | 桌面 `updateAdvertisement` 的 TXT 内容组装（quickPair 开/关、token 轮转、名称截断）——把 TXT 组装抽成纯函数再测 | `desktop-app/src/test` |
| 集成测试 | JmDNS 双实例：advertiser 发布 → 另一实例发现 → 断言 name/port/TXT（多播不可用的环境自动跳过，不 fail） | `sync-server/src/test` |
| 回归 | `:sync-server:test`、`:sync-transport-http:test`、`:desktop-app:test`、`:app:testDebugUnitTest`（sync 相关）、`:desktop-library-data:test`；spotless（可运行则 `:spotlessCheck`） | CI / 本地 |
| 手动冒烟 | 更新 `docs/library-sync-smoke-test.md`：新增用例 0a（扫码配对）、0b（发现+一键配对）、0c（发现但未开免输入→预填降级）、0d（AP 隔离下发现失败→手动兜底） | — |

Android 相机扫码无设备/robolectric 覆盖，验收靠手动冒烟 + 解码逻辑单测（zxing 对生成图解码可在 JVM 测）。

---

## 5. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| 路由器 AP 隔离/访客网络阻断多播 | 发现列表为空时 UI 提示"未找到设备，可扫码或手动输入"；手动路径不变 |
| 旧系统解析器不返回 TXT attributes | 降级为预填 host/port（§2.6 默认关闭 token 公布，正好一致）；免输入一键配对要求系统解析器支持 TXT 且桌面开开关 |
| JmDNS 与多网卡/VPN（Hyper-V/WSL 虚拟网卡） | advertiser 绑定与 HTTP 服务相同的真实 LAN IP 发布（优先 site-local 地址，复用 `getLocalIpAddresses()` 的排序）；JmDNS 构造指定 `InetAddress` |
| CameraX + zxing 体积增量 | 仅 core 引擎 + camera2 后端，预估 +2~3MB；报告中给出实测 |
| mDNS 在密集局域网中的命名冲突 | JmDNS 自动改名（`name (2)`），手机端以 `serviceName` 去重、以 TXT `name` 展示 |
| 二维码含 `#` 字符被外部扫码器/浏览器截断 | `#` 是合法 URI fragment 字符；主流扫码器原样回传；`parseOrNull` 已按 `#` 切分 token，勿改动该逻辑 |

---

## 6. 分阶段实施（建议派单顺序）

| 阶段 | 内容 | 预估 |
| --- | --- | --- |
| 1 | 依赖入库 + 桌面二维码展示 + zxing 单测 | 1 人日 |
| 2 | `SyncServiceAdvertiser` + 桌面广告生命周期 + 集成测试 + 设置开关 | 2 人日 |
| 3 | Android `LanSyncDiscovery` + 设置页发现入口 + 单测 | 2 人日 |
| 4 | Android 扫码屏 + 权限 + intent-filter | 2 人日 |
| 5 | 回归 + 冒烟文档更新 + 文档状态更新 | 1 人日 |
| 合计 | | **约 8 人日** |

---

## 7. 验收标准（总）

1. 桌面设置页在开启同步服务器后显示配对二维码，内容与复制按钮的 URI 完全一致。
2. 手机"扫码配对"扫该二维码 → 配对完成 → "测试连接"成功 → 书架同步端到端跑通（沿用现有同步冒烟用例 1–6）。
3. 手机"搜索局域网设备"在同 Wi-Fi 下 5 秒内列出电脑；桌面开启"允许免输入配对"后点击即配对成功；未开启时点击预填地址并给出引导。
4. 手动粘贴配对码路径与全部既有 sync 单测回归通过。
