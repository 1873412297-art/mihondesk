# Mihon 手机端 ↔ 桌面端书架同步计划文档

> 版本：v1.0（草案）
> 日期：2026-09-25
> 范围：`app`（Android 手机端）、`desktop-app` / `desktop-library-data`（Windows 桌面端）

---

## 1. 背景与目标

### 1.1 背景

本仓库是 Mihon 的 Fork，同时维护 Android 手机端（`app`）和 Windows 桌面端（`desktop-app`）。两端各自维护独立的本地书架：

- 手机端：Room 数据库（`data` / `domain` 模块）
- 桌面端：SQLDelight SQLite（`desktop-library-data`）

目前两端之间**没有任何自动同步通道**，只能通过「手动导出 / 导入 `.tachibk` 备份」进行一次性迁移。用户在手机上收藏漫画、标记已读，回到电脑端后书架和阅读进度对不上，这是当前体验上最大的断点。

### 1.2 目标

实现手机端与桌面端之间的**书架双向自动同步**，按优先级覆盖：

| 优先级 | 同步内容 | 说明 |
| --- | --- | --- |
| P0 | 收藏状态（favorite） | 收藏 / 取消收藏双向同步 |
| P0 | 章节已读状态（read） | 手机读完 → 电脑端显示已读，反之亦然 |
| P0 | 分类（category） | 分类的增删改与漫画-分类关联 |
| P1 | 阅读进度（lastPageRead / history） | 读到第几页、上次阅读时间 |
| P1 | 书架（漫画条目本体） | 元数据、封面 URL、简介等 |
| P2 | 书签（bookmark）、漫画备注（notes） | 可合并的附加信息 |
| P2 | 追踪器记录（tracking） | AniList / MAL 等绑定与进度 |
| 不做 | 下载的章节文件、阅读设置、扩展本体 | 文件体积大且与平台强相关；设置通过现有备份渠道手动迁移 |

### 1.3 非目标（明确排除）

- 不同步漫画图片缓存与下载文件（两端各自下载，见 §7.4）
- 不做多端实时在线（WebSocket 推送），准实时（分钟级）轮询即可
- 不引入账号系统（除非走自建服务器方案且后续需要）
- 不改变上游 Mihon 的备份格式兼容性

---

## 2. 现状盘点（可复用资产）

同步功能不需要从零造轮子，仓库中已有大量可直接复用的基础设施：

### 2.1 数据模型高度对齐

桌面端 `desktop-library-data/src/main/sqldelight/.../Library.sq` 的表结构（`manga` / `chapter` / `category` / `manga_category` / `history` / `tracking`）与手机端 Room 实体一一对应，且**已经包含同步所需的元数据字段**：

- `manga.last_modified_at`、`manga.favorite_modified_at`、`manga.version`
- `chapter.last_modified_at`、`chapter.version`
- `manga.memo_json` / `chapter.memo_json`（扩展用草稿字段）

这些字段手机端同样存在（`domain/.../manga/model/Manga.kt` 中的 `lastModifiedAt` 等），说明数据层已经为同步预留了 LWW（last-write-wins）依据。

### 2.2 已有跨端备份互通格式

`desktop-library-data/.../backup/` 已实现与 Android `.tachibk` protobuf 备份格式的完整双向转换：

- `AndroidBackupImporter.kt` / `AndroidBackupExporter.kt`：导入 / 导出
- `AndroidBackupCodec.kt` / `AndroidBackupDtos.kt`：编解码与 DTO（`AndroidBackupManga` / `AndroidBackupChapter` / `AndroidBackupCategory` / `AndroidBackupHistory` / `AndroidBackupTracking`）
- `AndroidBackupValidator.kt` / `BackupLimits.kt`：校验与限额
- 测试：`AndroidBackupRoundTripTest`、`SuwayomiBackupCompatibilityTest`、`LibraryImportIntegrationTest`

**同步协议的数据格式可以直接建立在这套 DTO 之上**，两端都能无歧义地序列化 / 反序列化同一批实体。

### 2.3 已有合并语义

`BackupMergePolicy.kt` 已经定义了完整的实体级合并规则，且这套规则本身就是"同步冲突解决"的规则：

- 标量字段：`lastModifiedAt` 大者胜（LWW），空值不覆盖非空值
- 单调字段取 max：`lastPageRead`、`dateFetch`、`dateUpload`、`readDuration`、`lastChapterRead`
- 布尔取 OR：`favorite`、`read`、`bookmark`
- 集合取并集：`genre`、`excludedScanlators`
- 历史记录：`lastRead` 取 max

**该模块应从 `desktop-library-data` 上移到共享模块（如 `core` 或新建 `sync-core`），让 Android 端直接复用同一套合并逻辑**，避免两端规则漂移。

### 2.4 已有定时任务基建

桌面端 `DesktopBackupScheduler.kt` 已实现"定时导出备份到本地目录"的调度器（分钟级检查、互斥锁、结果记录）。手机端有 `LibraryUpdateJob` / `BackupRestoreJob`（WorkManager）和 `MetadataUpdateJob`。

### 2.5 源 ID 一致性（需要验证的关键假设）

漫画的全局身份键是 `(source_id, url)`。Tachiyomi/Mihon 生态中在线源的 `source_id` 由扩展的 `name + lang` 确定性生成，桌面端通过 IPC 运行同一批 Android 扩展 APK，因此**理论上两端 source_id 一致**。同步方案成立与否强依赖此假设，Phase 0 必须用自动化测试证实（见 §8 Phase 0）。

### 2.6 缺口

| 缺口 | 说明 |
| --- | --- |
| 无传输层 | 两端都没有网络同步客户端 / 服务器（已确认桌面端无 Ktor / HTTP server 依赖） |
| 无增量协议 | 现有备份是"全量快照"，无游标 / 变更集概念 |
| 无删除语义 | 取消收藏可表达，但"彻底删除漫画 + 章节"没有 tombstone，无法安全地跨端传播删除 |
| 无设备身份 | 没有 device_id 概念，无法区分"谁最后改的"以外的信息 |
| 合并逻辑未共享 | `BackupMergePolicy` 只在桌面端模块内 |

---

## 3. 方案选型

### 3.1 候选方案对比

| 方案 | 原理 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- | --- |
| **A. 快照文件同步** | 复用现有 `AndroidBackupExporter/Importer`，定时把 `.tachibk` 快照放进云盘 / WebDAV / 局域网共享目录，两端各自拉取并合并 | 开发量最小（预估 1~2 周）；无服务器成本；传输层可插拔 | 准实时性受同步周期限制；冲突粒度粗（快照级）；依赖第三方存储 | **Phase 1 落地** |
| **B. 轻量同步服务器** | 自建一个小型同步服务（REST + 游标增量），两端作为客户端做双向同步 | 体验最好（秒级增量）；冲突粒度细（实体级）；可扩展账号 / 分享 | 需要维护服务器（或有 VPS）；开发量最大 | **目标架构，Phase 3** |
| **C. 局域网直连** | 桌面端起 HTTP 服务，手机端在同一 Wi-Fi 下配对直连同步 | 无云依赖、速度快 | 使用场景受限；移动端后台发现 / 配对复杂 | 作为 B 的局域网加速选项，不单独立项 |
| **D. 借道追踪器** | 通过 AniList / MAL 等追踪服务间接同步已读进度 | 零自建基础设施 | 只覆盖已读进度，不覆盖收藏/分类/书签；强依赖第三方 | 不采用（已有 TrackOnReadSyncService 覆盖该场景） |

### 3.2 推荐路线：A → B 平滑演进

核心决策：**先建协议，再选传输**。

1. 把"导出变更集 → 合并 → 导入"抽象成与传输层无关的同步引擎（`sync-core`），方案 A 的"文件即传输层"只是引擎的一个 Transport 实现。
2. Phase 1 用文件 Transport（云盘/WebDAV 目录）快速交付可用功能。
3. Phase 2/3 增加服务器 Transport，同一套引擎换传输层即可，协议与合并逻辑零改动。

这样避免了两条路线重复造协议，也控制了初期风险。

---

## 4. 总体架构设计

### 4.1 模块划分

```
┌─────────────────────────────────────────────────────────┐
│  app (Android)                                          │
│  ┌──────────────┐   ┌──────────────┐   ┌────────────┐  │
│  │ Room DB      │◄─►│ SyncEngine   │◄─►│ Transport  │  │
│  │ (data层)     │   │ (sync-core)  │   │ (File/HTTP)│  │
│  └──────────────┘   └──────┬───────┘   └────────────┘  │
└────────────────────────────┼────────────────────────────┘
                             │ 共享：DTO / MergePolicy / Protocol
┌────────────────────────────┼────────────────────────────┐
│  desktop-app (Windows)     │                            │
│  ┌──────────────┐   ┌──────┴───────┐   ┌────────────┐  │
│  │ SQLDelight   │◄─►│ SyncEngine   │◄─►│ Transport  │  │
│  │ (library-db) │   │ (sync-core)  │   │ (File/HTTP)│  │
│  └──────────────┘   └──────────────┘   └────────────┘  │
└─────────────────────────────────────────────────────────┘
```

新增 / 调整的 Gradle 模块：

| 模块 | 职责 | 依赖 |
| --- | --- | --- |
| `sync-core`（新建，KMP 纯 Kotlin） | 同步协议 DTO、变更集（changeset）模型、合并策略（从 `BackupMergePolicy` 迁入并扩展）、身份键解析、tombstone 模型 | kotlinx-serialization；被两端共用 |
| `sync-transport-api`（新建） | `SyncTransport` 接口：`push(changeset)` / `pull(sinceCursor): Changeset` / `headCursor()` | `sync-core` |
| `sync-transport-file`（新建） | 方案 A 实现：监控本地同步目录（云盘/WebDAV 挂载目录），读写字端命名变更集文件 | `sync-transport-api` |
| `sync-transport-http`（Phase 3） | 方案 B 客户端实现 | `sync-transport-api` + Ktor client |
| `sync-server`（Phase 3，独立部署） | 接收变更集、按用户存储、提供游标拉取 | Ktor server + SQLite/Postgres |
| Android 集成 | `app` 内新增 `data/sync/`：变更捕获（Repository 写路径挂钩）、WorkManager 周期任务、设置页 UI | `sync-core`、Room |
| 桌面端集成 | `desktop-app` 内新增 `sync/`：变更捕获（`SqlDelightLibraryRepository` 写路径挂钩）、调度器接入 `DesktopRuntime`、设置页 UI | `sync-core`、SQLDelight |

### 4.2 身份与键设计

| 实体 | 同步身份键（全局稳定） | 说明 |
| --- | --- | --- |
| Manga | `(sourceId, url)` | 与两端现有 `UNIQUE(source_id, url)` 约束一致；`LocalSource`（source_id = 0）用 `local://<存储路径哈希>` 派生键，见 §7.4 |
| Chapter | `(mangaKey, chapter.url)` | 章节 url 在同一 manga 下稳定 |
| Category | `name`（NOCASE 归一） | 与现有 `insertCategory ON CONFLICT(name)` 语义一致 |
| History | `(mangaKey, chapter.url)` | 挂在章节键下 |
| Tracking | `(mangaKey, trackerId)` | 与现有 `UNIQUE(manga_id, tracker_id)` 一致 |

所有 DTO 中**不携带本地自增主键 id**（现有 `AndroidBackupDtos` 已基本满足，导入端按身份键重映射）。

### 4.3 变更集（Changeset）模型

```kotlin
// sync-core
data class Changeset(
    val deviceId: String,          // UUID，首次启用同步时生成并持久化
    val baseSchema: Int,           // 协议版本，向后兼容的依据
    val cursor: Long,              // 本端数据水位（单调递增，见下）
    val producedAt: Long,          // 生成时间（仅用于展示，不参与冲突判定）
    val upserts: EntityDelta,      // 各实体的 upsert 列表（携带 lastModifiedAt/version）
    val tombstones: List<Tombstone>, // 删除标记
)

data class Tombstone(
    val entityType: EntityType,    // MANGA / CHAPTER / CATEGORY / ...
    val entityKey: String,         // 序列化后的全局身份键
    val deletedAt: Long,           // 逻辑时钟水位
)
```

**游标（cursor）设计**：每端维护一个本地单调递增的 `sync_clock`（存于 `library_metadata` / Android 端 `syncState` 表）。任何写操作在事务内将受影响实体的 `last_modified_at` 设为 `max(当前值, ++sync_clock)`。导出变更集 = 拉取 `last_modified_at > 上次导出水位` 的所有行。

- 手机端风险：现有 `MangaRepositoryImpl` 等写路径目前**不一定**都维护 `lastModifiedAt`（上游 Mihon 只在部分路径写入）。需要审计并在同步开启后统一由 `SyncMutationHook` 在写事务中补打时间戳（见 §6 Phase 0 任务）。
- 桌面端风险：SQLDelight 的 `updateReaderChapterProgress` 等语句目前不更新 `last_modified_at`，同样需要补。

**时钟问题**：不用墙钟做冲突判定。LWW 比较的是**逻辑时钟水位**（cursor），墙钟仅作展示。两端各自水位单调递增；合并时取 max。这避免了手机改系统时间导致的数据错乱。

### 4.4 同步循环（以文件 Transport 为例）

```
每端独立执行：
1. pull：扫描同步目录中所有远端 changeset（按 cursor 过滤，跳过本 deviceId）
2. merge：对每个远端实体按 BackupMergePolicy 语义合并进本地库（含 tombstone 应用）
3. push：导出本地水位之后的新变更集，写入 `<deviceId>.<cursor>.pb` 到同步目录
4. 清理：目录中超过 N 天且 cursor 已被所有已知设备拉走的文件可归档/删除
```

冲突安全性的关键不变量：**合并操作幂等且满足交换律**（commutative）。现有 `BackupMergePolicy` 的规则（max / OR / union / LWW-by-watermark）天然满足，需要补充的只有：

- LWW 平局（水位相等）：按 `deviceId` 字典序裁决，保证两端收敛到同一结果
- 删除 vs 修改：tombstone 的 `deletedAt` 水位 ≥ 实体 `lastModifiedAt` 时删除胜，否则保留修改并"复活"（清除 tombstone）。首期可直接规定"删除永远胜"简化，见 §7.3

### 4.5 同步范围设置

设置页提供粒度选项（两端各自配置，仅影响 push 范围）：

- 开关：总开关
- 内容范围：收藏 / 已读进度 / 分类 / 书签 / 备注 / tracking（复选，默认全开）
- 传输方式：本地目录（WebDAV/云盘路径）→ 后续加"自建服务器"
- 频率：仅手动 / 每 15 分钟 / 每 1 小时 / 每天（手机端走 WorkManager 周期任务，受系统省电策略约束）

---

## 5. 与现有备份功能的关系

- **不替换**手动备份/恢复。`.tachibk` 全量备份仍是"换机迁移"的正式渠道；同步是日常状态收敛渠道。
- 复用关系：同步引擎的 DTO 复用 `AndroidBackupDtos` 的字段定义（或让 `AndroidBackupManga` 直接实现 `sync-core` 的实体接口），避免两套序列化定义漂移。建议将 DTO 定义上移到 `sync-core`，备份模块改为依赖它。
- 手动"立即同步"按钮与自动周期任务共用同一个 `SyncEngine.syncNow()` 入口。

---

## 6. 分阶段实施计划

### Phase 0：地基（审计 + 共享化 + 验证）— 约 1 周

| # | 任务 | 产出 / 验收 |
| --- | --- | --- |
| 0.1 | **source_id 一致性验证**：写集成测试——同一扩展 APK 在手机端与桌面端 IPC 宿主中枚举的 source id 逐一比对；用 `desktop-library-data` 现有 fixture（`suwayomifixture`）补充跨端 id 断言 | 测试通过；若不一致，方案增加 `source_id 映射表`（按 `name+lang` 键映射），并在本文档 §7.1 展开 |
| 0.2 | **写路径审计**：审计手机端（`data` 模块各 RepositoryImpl）与桌面端（`SqlDelightLibraryRepository` + `.sq` 写语句）所有写路径，列出未维护 `last_modified_at` / `version` 的清单 | 审计清单文档；明确每个写路径的修改点 |
| 0.3 | **新建 `sync-core` 模块**：迁移 `AndroidBackupDtos` 与 `BackupMergePolicy`；补充设备身份键解析、tombstone、changeset 模型；添加平局裁决规则 | `sync-core` 单测覆盖所有合并规则（含交换律/幂等性性质测试） |
| 0.4 | **补打时间戳**：两端在写事务内统一通过 hook 更新 `last_modified_at`（手机端 Room 端可用 `@Update` 拦截或 Repository 层包装；桌面端修改 `.sq` 写语句） | 审计清单中所有写路径闭合；现有测试全绿 |
| 0.5 | 协议版本号 `baseSchema = 1` 落地 | — |

### Phase 1：文件同步 MVP（方案 A）— 约 2~3 周

**目标**：两端通过同一个云盘 / WebDAV 挂载目录实现收藏、已读、分类的分钟级同步。

| # | 任务 | 产出 / 验收 |
| --- | --- | --- |
| 1.1 | `sync-transport-api` + `sync-transport-file`：目录扫描、changeset 文件读写（protobuf 复用现有 codec）、水位记录 | 单测：多 changeset 乱序合并收敛 |
| 1.2 | 桌面端集成：变更捕获接入 `SqlDelightLibraryRepository` 写路径；调度器接入 `DesktopRuntime`；设置页（`NetworkSettingsCard` 同页新增"同步"卡片） | 桌面端手动同步按钮可用 |
| 1.3 | 手机端集成：新建 `data/sync/`（Room `syncState` 表：deviceId、push 水位、pull 水位）；变更捕获挂在 Repository 写路径；WorkManager 周期任务；设置页同步选项 | 手机端手动同步按钮可用 |
| 1.4 | 合并执行器：按 §4.4 循环实现 pull→merge→push；冲突平局裁决；同步结果通知（复用现有通知基建） | 端到端联调通过 |
| 1.5 | 端到端场景测试：手机收藏→电脑出现；电脑已读→手机已读；双端同时改同一漫画；离线 7 天后恢复同步 | 场景全部收敛一致 |

**Phase 1 验收标准**：双端各操作后，N 分钟内书架（收藏/已读/分类/进度）自动一致；全过程无需用户手动导出导入。

### Phase 2：完整数据面 + 删除语义 — 约 2 周

| # | 任务 | 产出 / 验收 |
| --- | --- | --- |
| 2.1 | 覆盖 bookmark、notes、tracking、history(readDuration) 的同步与合并 | 合并规则单测 + 端到端 |
| 2.2 | Tombstone 机制：两端"删除漫画/分类"产生 tombstone；合并时按 §4.4 规则处理；提供"同步删除"设置项（默认开） | 删除场景双端收敛 |
| 2.3 | 分类重命名 / 排序（sort_order）同步：重命名生成 tombstone(旧名) + upsert(新名) | 场景测试 |
| 2.4 | 冲突可视化：同步报告中列出被远端覆盖的本地修改（复用桌面端 `import_report` 表与手机端通知） | 用户可在两端查看"同步日志" |
| 2.5 | 变更集文件清理策略（§4.4 步骤 4） | 目录不无限膨胀 |

### Phase 3：服务器 Transport（方案 B）— 约 3~4 周（可选，视 Phase 1 反馈）

| # | 任务 | 产出 / 验收 |
| --- | --- | --- |
| 3.1 | `sync-server`：Ktor + SQLite，REST API（`POST /changeset`、`GET /changeset?since=`、`GET /head`），按 token 隔离用户 | 服务端集成测试 |
| 3.2 | `sync-transport-http` 客户端 | 与文件 Transport 行为对齐的契约测试 |
| 3.3 | 配对流程：手机扫桌面端二维码 / 输配对码换取 token | 配对流程可用 |
| 3.4 | 局域网直连（方案 C）：同一 Wi-Fi 下桌面端起本地服务，手机直连；服务器作为中继兜底 | 局域网场景测试 |
| 3.5 | （可选）多设备管理页：查看已配对设备、踢出设备 | — |

### Phase 4：打磨（持续）

- 同步失败重试与退避、电量/流量约束（手机端仅 Wi-Fi 同步选项）
- 首次全量对账（full reconcile）工具：比对两端身份键集合，输出差异报告
- 性能：大型书架（5000+ 章节）下变更集导出 < 2s

---

## 7. 关键难点与风险

### 7.1 source_id 跨端不一致（最高风险）

若两端扩展的 source id 生成存在差异（扩展版本不同、桌面端 IPC 宿主对 id 的二次加工），`(source_id, url)` 身份键直接失效，同步会把同一漫画识别成两本。

- 缓解：Phase 0.1 先行验证；不一致时引入 `source_registry` 映射表（键 = `name + lang` 归一化），两端各自维护本地 source_id ↔ 全局键映射，changeset 中一律使用全局键。
- 注意：`LocalSource`（source_id = 0）天然没有跨端身份，**本地漫画不同步收藏条目**（见 §7.4）。

### 7.2 手机端写路径时间戳缺失

上游 Mihon 并非所有写路径都维护 `lastModifiedAt`（部分上游版本甚至不更新它）。漏打时间戳的后果是：该修改永远不会被增量导出，表现为"偶发丢同步"。

- 缓解：Phase 0.2 全量审计 + 0.4 统一 hook；并为同步功能加一个**兜底全量对账**（每次同步时抽样或周期性全量比对身份键 + 水位），作为增量协议的 safety net。

### 7.3 删除语义

- 取消收藏（favorite=false）已有 `favorite_modified_at`，可安全 LWW。
- 彻底删除（DB 行删除）无痕迹，必须 tombstone；tombstone 自身需要持久化并参与水位，否则一端删除后另一端"修改"会让它复活。
- 简化决策：Phase 2.2 前规定**同步只传播取消收藏，不传播物理删除**；物理删除仅本地生效。这符合"书架同步"语义且风险最低。

### 7.4 下载状态与本地漫画

- `downloadedCount` 等是派生状态（由 `local_chapter_asset` 等表 JOIN 得出），**不进入同步**；同步 only 同步逻辑状态（read/bookmark/lastPageRead）。下载文件两端各自管理，桌面端已有 `DownloadStore` / 下载恢复测试覆盖。
- 本地漫画（LocalSource）不同步：两端文件系统不同，且无稳定全局身份。设置页明示"本地漫画不参与同步"。

### 7.5 章节列表漂移

同一 manga 两端各自从源站拉章节，可能出现一端缺章 / url 微调。合并按 `(mangaKey, chapter.url)` 匹配，天然容忍；缺失章节在下次任一端的 `LibraryUpdate` 后补齐，不作为同步错误。

### 7.6 协议演进

`baseSchema` 版本号 + `ignoreUnknownKeys` 反序列化（现有 codec 已如此）；旧端收到高版本 changeset 时拒绝并提示升级，不做静默丢弃。

---

## 8. 测试计划

| 层级 | 内容 | 位置 |
| --- | --- | --- |
| 单测 | 合并规则全组合；交换律 / 幂等性性质测试；changeset 编解码 | `sync-core/src/test` |
| 单测 | 文件 Transport 乱序 / 重复 / 缺文件场景 | `sync-transport-file/src/test` |
| 集成测试 | 两端 DB fixture 经 sync-core 互相同步后状态一致 | 参照 `LibraryImportIntegrationTest` 模式 |
| 契约测试 | 手机端 ↔ 桌面端 source_id 一致性（Phase 0.1） | `desktop-library-data/src/test` + `app/src/test` |
| 端到端 | Phase 1.5 的五个场景脚本化 | 手动 + 后续自动化（Compose UI test / 桌面端 `webapp-testing`） |
| 回归 | 现有 `AndroidBackupRoundTripTest`、`SuwayomiBackupCompatibilityTest`、全部 Repository 测试保持绿色 | CI |

---

## 9. 工作量估算

| 阶段 | 内容 | 预估 |
| --- | --- | --- |
| Phase 0 | 审计 + sync-core + 时间戳补打 | 5 人日 |
| Phase 1 | 文件 Transport + 双端集成 + E2E | 10~15 人日 |
| Phase 2 | 完整数据面 + tombstone + 同步报告 | 8~10 人日 |
| Phase 3 | 服务器方案（可选） | 15~20 人日 |
| 合计（至 Phase 2 可用） | | **约 25~30 人日** |

---

## 10. 术语表

| 术语 | 含义 |
| --- | --- |
| 变更集（Changeset） | 一端在一段时间内的实体 upsert + tombstone 集合，同步的最小单位 |
| 水位（cursor） | 本地单调递增的逻辑时钟，用于增量导出与 LWW 判定 |
| 身份键 | 跨端稳定标识实体的键，如 manga 的 `(sourceId, url)` |
| Tombstone | 删除标记，使删除操作可跨端传播 |
| LWW | Last-Write-Wins，按水位裁决冲突的合并策略 |
