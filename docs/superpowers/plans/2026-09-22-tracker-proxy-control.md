# 产品改进规划：Tracker 网络代理控制（1B 联调遗留项）

日期：2026-09-22 · 来源：1B 生产联调证据（Clash 类代理系统性掐断 okhttp tracker 流量，Bangumi/AniList 均复现）

## 问题

`defaultTrackerHttpClient()` 创建的 okhttp 客户端未接代理配置，永远走 JVM 系统代理。用户开启 Clash/mihomo 类系统代理后，tracker 请求被代理核心掐断（`unexpected end of stream`），登录/搜索/同步全挂，且应用内无任何开关（现有"图源网络"设置只作用于图源 broker，不覆盖 tracker）。

## 方案

复用现有 `DesktopNetworkPolicy`（`mihon/desktop/extension/DesktopNetworkPolicy.kt`，持久化在 `network.*` preferences），让 tracker 客户端按同一策略选择代理：

1. **动态 ProxySelector**（新增，放 `TrackerApiSupport.kt` 或同包新文件）：每连接调用 `select()` 时实时读策略——
   - `SYSTEM`：委托给 `ProxySelector.getDefault()`（= 今日行为，零变化）
   - `DIRECT`：`Proxy.NO_PROXY`
   - `HTTP/SOCKS`：构造对应 `java.net.Proxy`（host/port 来自策略）
   - 优点：策略运行时修改立即生效，无需重建 client（对齐 DesktopNetworkHelper 的语义但更简）。
2. **`defaultTrackerHttpClient(policyProvider)`** 加可选参数（默认 `{ DesktopNetworkPolicy() }`，测试零影响），通过 `.proxySelector(...)` 接入。
3. **注入点**：`DesktopRuntime` 构建 `DesktopTrackerManager` 时，用 `defaultTrackerHttpClient { DesktopNetworkSettingsStore(preferences).load() }` 构建 `defaultTrackers(httpClient)`（`defaultTrackers` 加可选 httpClient 参数透传给各 tracker 构造函数——各 tracker 已有 `httpClient` 构造参数）。
4. **UI 文案**：`NetworkSettingsCard` 标题/说明从"图源网络"改为覆盖 tracker（如"图源与跟踪器网络"），说明代理设置同时作用于图源请求与 tracker（AniList/Bangumi 等）同步流量。
5. **不做**：policy 的超时与自定义 UA 不应用于 tracker（tracker 有自己的 15s callTimeout 与 MihonW UA 约定），避免行为面扩大。

## 测试

- 新增 `TrackerProxySelectorTest`：SYSTEM 委托默认 selector、DIRECT 返回 NO_PROXY、HTTP/SOCKS 构造正确 InetSocketAddress、策略变化即时反映（同一 selector 实例两次 select 返回不同结果）。
- 回归：`:desktop-app:test --tests "*Tracker*"` 全绿。

## 验收（GUI，主线执行）

系统代理 ON（Clash）+ 应用网络设置切"直连"→ AniList/Bangumi 登录成功（不经系统代理）。这是该改进的端到端判据。

## 执行记录（2026-09-22，agy staffer 实施 + 主线复核）

- 实现：agy staffer（staffer-mubk68ki），主线逐行复核 diff 后执行构建测试
- 单测：TrackerProxySelectorTest 4 项（SYSTEM 委托 / DIRECT / HTTP+SOCKS / 策略热切换）+ tracker 回归 40 项全绿
- **验收（实活 API，系统代理 ON + Clash 活动）**：`DIRECT` 策略下 BangumiTracker 登录+搜索成功——即此前 100% 失败的代码路径；`SYSTEM` 模式行为不变（委托 JVM 默认 selector）
- 提交：7a53df952
