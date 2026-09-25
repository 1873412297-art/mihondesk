# 内置同步工具（Builtin Sync）实施计划

> 版本：v1.0
> 日期：2026-09-25
> 前置：文件 Transport 书架同步已交付（commit `26ca5c8f3`），本文档是其 Phase 3 的详细设计。
> 目标读者：实现工程师（人或 agy）。实施时请对照 `docs/library-sync-plan.md` 的协议章节。

---

## 1. 背景与目标

### 1.1 问题

当前书架同步（已交付）依赖第三方文件同步工具（Syncthing / 云盘 / WebDAV 挂载）搬运变更集文件。用户反馈门槛高："手机和电脑怎么通过同一个目录同步？"——本质是需要一个**软件内置的同步通道**，开箱即用。

### 1.2 目标

- 桌面端（mihondesk）**内置同步服务器**：设置里一键开启，生成配对码。
- 手机端输入/扫描配对码即完成配对，之后同步全自动，零第三方工具。
- 保留现有文件 Transport 作为备选通道（两者共用同一 `SyncTransport` 接口与同步引擎，互不干扰）。
- 架构上支持未来扩展"独立部署的同步服务器"（跨网络场景），但**本期不做**。

### 1.3 非目标

- 不做账号系统 / 云托管服务。
- 不做跨网络直连（NAT 穿透、中继）——局域网场景由内置服务器覆盖；跨网络用户继续用文件 Transport 或自托管服务器（后续阶段）。
- 不做 HTTPS / 端到端加密（局域网明文 + token，风险见 §6）。
- 不改动 SyncEngine / SyncMergePolicy / changeset 协议（baseSchema 仍为 1）。

---

## 2. 方案选型

| 方案 | 原理 | 结论 |
| --- | --- | --- |
| **A. 桌面内置服务器 + 手机直连（本期）** | 桌面端内嵌 Ktor 服务器；手机作为 HTTP 客户端，经局域网直连推拉 changeset | **本期实现**。桌面常开、性能最强、零依赖 |
| B. 手机内置服务器 + 电脑连手机 | 手机做服务端 | 不做：手机 IP 漂移、后台限制，体验差 |
| C. 独立部署服务器（VPS/NAS） | sync-server 独立进程，两端均为客户端 | 下期：本期在模块设计上预留（server 独立成 module，不耦合桌面代码） |
| D. 二维码自动发现（mDNS/NSD） | 免输入配对 | 下期增强：本期用手动配对码，格式预留为 URI 便于将来扫码 |

---

## 3. 总体架构

```
┌────────────────────────────────────────────────────┐
│ desktop-app (Windows)                              │
│  ┌─────────────┐   ┌────────────────────────────┐  │
│  │ DesktopSync │◄─►│ SyncServer (内嵌, Ktor)     │  │
│  │ Scheduler   │   │  · REST API + Bearer token │  │
│  └──────┬──────┘   │  · SQLite 存储 changesets  │  │
│         │          └────────────────────────────┘  │
│  ┌──────┴───────┐    同时作为客户端：               │
│  │ SyncEngine   │◄── HttpTransport ──► 对端服务器  │
│  └──────────────┘                                  │
└────────────────────────────────────────────────────┘
           ▲ 局域网 HTTP（protobuf over HTTP）
┌──────────┴─────────────────────────────────────────┐
│ app (Android)                                      │
│  ┌─────────────┐   ┌────────────────────────────┐  │
│  │ SyncWorker  │◄─►│ HttpTransport (客户端)      │  │
│  └──────┬──────┘   └────────────────────────────┘  │
│  ┌──────┴──────┐   （可选同时保留 FileTransport）  │
│  │ SyncEngine  │                                    │
│  └─────────────┘                                    │
└────────────────────────────────────────────────────┘
```

新增模块：

| 模块 | 职责 | 关键依赖 |
| --- | --- | --- |
| `sync-server`（新，纯 JVM） | 同步服务器：REST API、token 鉴权、SQLite 存储、清理策略 | Ktor server (CIO)、sqlite-jdbc 或 SQLDelight、sync-core |
| `sync-transport-http`（新，纯 JVM） | `SyncTransport` 的 HTTP 客户端实现 | Ktor client (CIO/OkHttp)、sync-core、sync-transport-api |

改动模块：`desktop-app`（内嵌服务器 + 设置 UI）、`app`（传输方式选择 + 配对输入 UI）。

**SyncEngine / SyncMergePolicy / changeset 协议零改动**——只新增一个 Transport 实现。

---

## 4. 协议设计（HTTP API）

复用现有 changeset protobuf 序列化（`ProtoBuf.encodeToByteArray(Changeset.serializer(), ...)`，与 FileTransport 一致）。Base path：`/v1`。

| 端点 | 方法 | 请求 | 响应 | 说明 |
| --- | --- | --- | --- | --- |
| `/v1/changesets` | POST | body = Changeset protobuf bytes；`Authorization: Bearer <token>` | 204；`X-Cursor` 响应头 = 服务端为该设备分配的确认游标 | push。服务端按 `(device_id, cursor)` 主键存储，重复 push 幂等（同键覆盖） |
| `/v1/changesets` | GET | query：`exclude=<deviceId>`、`since=<deviceId>:<cursor>[,...]`（每 peer 一个条目，缺省该 peer 从 0 拉） | 200，body = 变长protobuf流：连续编码的 Changeset 消息（或 `application/x-ndjson` 风格的 `base64` 行，二选一，实现时取更简单者并在报告中说明）；无变更返回空 body | pull。按 `(device_id, cursor)` 升序返回 |
| `/v1/head` | GET | query：`exclude=<deviceId>` | 200，text/plain 数字 = 远端最大游标 | headCursor 实现 |
| `/v1/health` | GET | 无鉴权 | 200 `ok` | 连通性探测（设置页"测试连接"） |

鉴权：除 `/v1/health` 外全部要求 `Authorization: Bearer <token>`，否则 401。

### 4.1 配对码格式

```
mihonsync://<host>:<port>#<token>
```

- 桌面端设置页显示该字符串（可复制），并显示本机局域网 IP 供核对。
- 手机端输入框粘贴（或将来扫码）。解析出 host/port/token 存入同步设置。
- token：服务器首次启动生成 256-bit 随机 hex，持久化在服务器数据目录；设置页提供"重新生成"（使所有已配对设备失效，需重新配对）。

### 4.2 服务器存储

表（SQLite，WAL 模式）：

```sql
CREATE TABLE changeset(
  device_id TEXT NOT NULL,
  cursor INTEGER NOT NULL,
  produced_at INTEGER NOT NULL,
  payload BLOB NOT NULL,           -- protobuf Changeset bytes
  received_at INTEGER NOT NULL,
  PRIMARY KEY(device_id, cursor)
);
CREATE INDEX idx_changeset_received ON changeset(received_at);
```

- push 幂等：`INSERT OR REPLACE`（同 `(device_id, cursor)`）。
- 清理：每次 push 后惰性检查——删除 `received_at` 早于 N 天（默认 30，可配）的条目；或总量超 M 条（默认 5000，可配）时删最旧。**注意**：清理依赖客户端各自维护 pull 水位（已实现：sync_peer_state / library_metadata），服务端不追踪客户端进度；文档中说明"清理窗口内未同步的设备会丢变更"，默认窗口 30 天对正常使用足够。
- 多用户隔离：本期 token 即用户，单 token 命名空间；表结构预留 `user_id` 列（默认 'default'），将来多 token 直接扩展。

### 4.3 端口与发现

- 默认端口 `45831`，设置页可改。端口被占用时启动失败并在 UI 明确报错（不做静默随机端口，避免防火墙规则失效）。
- 绑定 `0.0.0.0`（局域网可路由）；设置页明示"同一局域网内的设备可凭配对码访问"。Windows 首次启动会弹防火墙授权框，文档和 UI 文案提示用户允许"专用网络"。

---

## 5. 各端改动明细

### 5.1 `sync-server`（新模块）

- `SyncServer` 类：`start(host, port, token, storage)/stop()`，Ktor CIO 引擎。
- `ChangesetStore` 接口 + SQLite 实现（表见 §4.2；用 sqlite-jdbc 即可，不必上 SQLDelight codegen）。
- 路由 + 鉴权拦截器 + 异常到 HTTP 状态码的映射（坏 protobuf → 400，未授权 → 401）。
- 单测：Ktor `testApplication`，覆盖 push/pull/head/鉴权/幂等重复 push/清理策略。

### 5.2 `sync-transport-http`（新模块）

- `HttpTransport(baseUrl, token, httpClient)` 实现 `SyncTransport`：
  - `push` → POST body protobuf，带 Bearer。
  - `pull(sinceCursors, excludeDeviceId)` → GET，解析响应流为 `List<Changeset>`。
  - `headCursor` → GET /v1/head。
- 错误分类：401 → 抛出"配对失效"专用异常（UI 提示重新配对）；连接失败/超时 → 可重试网络异常（SyncWorker 现有重试路径处理）。
- 单测：Ktor MockEngine 或内嵌 testApplication 服务器，行为与 FileTransport 契约对齐（乱序、按 peer 过滤、跳过自身）。

### 5.3 桌面端（desktop-app）

- `DesktopRuntime` 装配：设置开启"内置服务器"时启动 `SyncServer`，关闭/退出时停止。
- 设置页"书架同步"卡片扩展：
  - 传输方式：`文件目录（云盘/Syncthing）` / `内置服务器（局域网）`。
  - 选内置服务器时显示：运行状态、配对码（可复制）、端口配置、重新生成 token、"在防火墙中允许"提示。
  - 桌面作为客户端去连别的服务器：本期**不做**（桌面-桌面同步用文件 Transport；如需双桌面，后续加"连接到远程服务器"子项）。
- 偏好存储：`DesktopPreferenceStore` 增加 serverEnabled/port/token 项。

### 5.4 Android 端（app）

- `SyncPreferences` 增加 transport 类型（file / http）与 http 配置（host/port/token）。
- `SyncWorker` 装配处按类型选择 `FileTransport` 或 `HttpTransport`。
- `SettingsLibrarySyncScreen` 扩展：传输方式选择；选 http 时显示配对码输入框（粘贴 `mihonsync://...` 自动解析 host/port/token）、"测试连接"按钮（打 /v1/health）、配对失效错误提示。
- 本期手机仍保留文件 Transport 选项。

---

## 6. 安全说明（需写进 UI 文案与 README）

- 局域网明文 HTTP：任何能接入该局域网且拿到配对码的人可读写该用户的同步数据（漫画书架、阅读记录，属半敏感）。配对码只应在可信网络分享。
- token 即全部凭证，无用户体系；服务器不暴露到公网是用户责任（文档明确警告：不要将端口映射到公网）。
- 后续阶段（不做承诺）：HTTPS（自签或 LAN 证书）、token 轮换审计、多用户。

---

## 7. 分阶段实施

### Phase A（本期交付）：核心链路

1. `sync-server` 模块 + 单测
2. `sync-transport-http` 模块 + 单测
3. 桌面端内嵌服务器 + 设置 UI（配对码显示/复制、端口、token 管理）
4. Android 端 http 选项 + 配对码输入 + 测试连接
5. 端到端测试：两个 SyncEngine 实例经 `HttpTransport` ↔ `sync-server` 完成 pull-merge-push 收敛（JVM 集成测试，参照现有 SqlDelightSyncLocalRepositoryTest 模式）
6. 全量回归：sync 相关全部测试 + spotless

验收：同一 Wi-Fi 下，手机不配任何第三方工具，粘贴配对码即可完成用例 1–4（见冒烟文档）。

### Phase B（后续增强）

- ✅ 扫码配对（Android CameraX + zxing；桌面端 zxing MultiFormatWriter 生成二维码）**已交付**
- ✅ mDNS/NSD 自动发现（`SyncServiceAdvertiser` JmDNS 广播；Android `LanSyncDiscovery` 发现 + 一键配对）**已交付**
- 服务器变更集清理的可配置 UI

### Phase C（后续形态）

- 独立服务器部署（单 jar / Docker），两端都支持"连接到远程服务器"
- 手机端内嵌服务器（可选，热点/固定 IP 场景）

---

## 8. 测试计划

| 层级 | 内容 |
| --- | --- |
| `sync-server` 单测 | push/pull/head 全端点、401/400 路径、重复 push 幂等、按 peer since 过滤、清理策略边界（30 天窗口、5000 条上限） |
| `sync-transport-http` 单测 | 契约对齐 FileTransport：乱序 pull、excludeDeviceId、headCursor、401 → 配对失效异常分类 |
| 集成测试 | SyncEngine A ↔ server ↔ HttpTransport B 双端收敛；断线重推幂等 |
| 回归 | 现有全部 sync 测试 + 备份兼容性 + spotless |
| 手动冒烟 | 更新 `docs/library-sync-smoke-test.md`：新增"内置服务器"通道的用例 0（配对）后沿用用例 1–6 |

---

## 9. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| Ktor 依赖增大安装包体积（桌面 +Android 合计约 2–4 MB） | 仅 CIO 引擎，不引 netty；在报告中给出实测增量 |
| Windows 防火墙拦截 | UI 明确提示"专用网络允许"；文档 FAQ |
| 手机与桌面不在同一网段（AP 隔离、访客网络） | 健康检查按钮快速定位；文档引导用文件 Transport 兜底 |
| 服务器长期运行内存膨胀 | 清理策略（§4.2）+ 单测覆盖 |
| 端口冲突 | 启动失败显式报错，用户可改端口 |
| token 泄露 | 重新生成按钮；README 安全说明 |

---

## 10. 工作量估算

| 项 | 预估 |
| --- | --- |
| sync-server + 单测 | 3–4 人日 |
| sync-transport-http + 单测 | 2–3 人日 |
| 桌面端集成 + UI | 2–3 人日 |
| Android 集成 + UI | 2–3 人日 |
| 集成测试 + 回归 | 2 人日 |
| 合计 | **约 11–15 人日** |
