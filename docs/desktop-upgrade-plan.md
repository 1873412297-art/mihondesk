# mihondesk 升级计划书（对标 Mihon Android）

> 基线：main @ `d3384d4d5`（v0.2.21）。调研方式：三路并行代码审查（功能完整度 / UX·UI / 健壮性），证据均带 file:line。
> 本文是长期路线图；执行按 Phase 分批，每批单独交付、测试、发布。

## 〇、现状总评

桌面端**底子比想象的好**：

- 阅读器是桌面原生水准（全键盘映射、滚轮策略、侧键、沉浸边框唤出、双页、裁剪、章节抽屉），部分超过 Android；
- 书架有四种布局、三态过滤、7 种排序、批量操作条、≥1100dp 双栏详情；
- 扩展是 Windows 沙箱隔离运行（AppContainer + JobObject），Android 没有；
- 备份编解码与 Mihon/Suwayomi 双向兼容且经真实往返验证；
- 207 个桌面测试文件，testTag 覆盖率高。

**主要差距**集中在：① 健壮性（同步服务器安全边界、日志与崩溃可诊断性、迁移快照堆积）；② 桌面交互基建（无真后退栈、无全局快捷键、无 Snackbar/Undo、无右键菜单与 Tooltip）；③ 对标 Android 的功能细节（迁移、每漫画设置、更新流、下载队列交互等）；④ i18n 半成品（39 处 `recoveryText` 内联三语文本 + 散落硬编码英文）。

---

## Phase 0 — 健壮性与数据安全（最高优先级，先行交付）

> 原则：先堵数据丢失和安全边界，再谈体验。全部为低风险小改动。

| # | 事项 | 证据 | 交付 |
|---|---|---|---|
| 0.1 | 同步服务器请求体无上限，认证对等端可 OOM 桌面端 | `sync-server/.../SyncServer.kt:93-94` | `POST /v1/changesets` 加请求体大小上限 + 单条 changeset 载荷上限，超限 413 |
| 0.2 | 同步服务器 deviceId 取自客户端载荷，持 token 者可冒写他人游标造成同步数据丢失 | `SyncServer.kt:105-121`, `SqliteChangesetStore.kt:39-52` | deviceId 与配对身份绑定（注册/所有权校验），拒绝冒写 |
| 0.3 | 主程序无运行日志：`logs` 目录存在但无人写入，打包用户无法提供诊断信息 | `DiagnosticBundleService.kt:40-44`, `desktop-app/build.gradle.kts:78`（slf4j-nop） | 引入滚动文件日志（logs/ 下），接 `DiagnosticBundleService` |
| 0.4 | 无全局未捕获异常兜底：appScope 无 CoroutineExceptionHandler，后台协程静默死 | `DesktopRuntime.kt:324` | `Thread.setDefaultUncaughtExceptionHandler` + appScope handler，记录到日志并尽量恢复 |
| 0.5 | 迁移快照 `migration-backups/` 只增不减，无限占盘 | `desktop-library-data/.../DatabaseMigrationSnapshot.kt:14-27` | 保留最近 N 个/按天龄修剪；在诊断页显示快照大小与位置 |
| 0.6 | 书库 DB 单连接驱动在调用线程跑 I/O，UI 线程可被慢查询冻结；主库未开 WAL/busy_timeout | `DesktopLibraryDatabaseFactory.kt:195-269` | 开 WAL + busy_timeout；DB 访问收敛到专用 dispatcher |
| 0.7 | 空 catch 吞错：页缓存写失败、缓存清理失败、Tracker 冲刷失败无日志无退避 | `OnlineChapterSource.kt:144`, `DownloadCacheCleaner.kt:118-123`, `TrackOnReadSyncService.kt:83,130` | 至少记日志；跟踪冲刷加退避 |
| 0.8 | 同步服务器：token 比较非常量时间；cursor 过滤在 Java 侧全表扫描 | `SyncServer.kt:88,135,175`, `SqliteChangesetStore.kt:59-93` | 常量时间比较；`cursor > ?` 下推 SQL |
| 0.9 | 补测试：`DesktopProfileLock`、`WindowsCredentialStore`、`DesktopCategoryService`、`DesktopHistoryService`（当前 0 覆盖）；同步服务器滥用用例（冒写 deviceId、超限载荷）；下载器取消竞态回归 | 测试盘点见调研报告 | 新增对应测试类 |

## Phase 1 — 桌面交互基建（UX 骨架）

| # | 事项 | 证据 | 交付 |
|---|---|---|---|
| 1.1 | **真后退栈**：当前导航器是单槽+两个记忆的返回目标，后退只在阅读器和详情页有效 | `navigation/DesktopNavigator.kt:7-44` | 改为后退栈列表，Esc/后退键在任意子页面可用；Upcoming 从布尔标志改为正式路由（`MihonDesktopApp.kt:286-291`） |
| 1.2 | **全局快捷键层**：无 Ctrl+1~8 导航 / Ctrl+F / F5 刷新 / Alt+← 后退 | `MihonDesktopApp.kt:464-467` | shell 级 `onPreviewKeyEvent` 分发器 + 快捷键速查对话框（复用阅读器 F1 模式） |
| 1.3 | **App 级 Snackbar + Undo**：现在导出完成/导入结果/删除等全是模态 AlertDialog；破坏性操作（删历史、移出书库、删下载）无撤销 | `MihonDesktopApp.kt:1152-1163,1303-1314` | Shell 挂 `SnackbarHost`；信息反馈改 Snackbar；破坏性操作给 Undo（对齐 Android） |
| 1.4 | **右键菜单 + Tooltip 大扫除**：全 UI 0 处 Tooltip；右键只在阅读器有 | `DesktopShell.kt:217-223` 等 | 书架卡片/章节行/图源行/历史条目/下载卡片五处右键菜单；图标按钮全部加 TooltipBox；修 `contentDescription = null` |
| 1.5 | 窗口细节：标题硬编码 `mihondesk`；无最小窗口尺寸 | `MihonDesktopApp.kt:461` | 标题随导航态变化；minimumSize 960×640 |
| 1.6 | `DesktopShell` ~95 参数的巨型组合函数拆分 | `DesktopShell.kt:46-189` | 抽状态/动作持有者对象（为后续 UI 工作降风险） |

## Phase 2 — 功能对齐 Android（按用户影响排序）

| # | 事项 | 现状 | 交付 |
|---|---|---|---|
| 2.1 | **迁移对齐**：无 SmartSearch 自动匹配、无批量迁移、迁移不搬分类与跟踪绑定 | `ui/browse/BrowsePresenter.kt` `performMigration` 只搬章节已读/书签/页码 | 自动匹配 + 「全部迁移」+ 分类/跟踪迁移 |
| 2.2 | **每漫画设置覆盖**（阅读模式/预载/自动下载按标题覆盖全局） | 只有全局设置 `DesktopReaderSettingsStore.kt:23` | manga 级覆盖存储 + 详情页入口 |
| 2.3 | **更新流对齐**：无日期分组、行内无已读/下载/标记操作 | `ui/updates/UpdatesScreen.kt:37` | 日期分组 + 行操作 |
| 2.4 | **浏览/搜索选项**：无仅搜固定图源、无过滤面板；无隐藏图源/语言过滤；无全局 NSFW 开关 | `GlobalSearchScreen.kt:61`, `BrowsePresenter.kt:82` | 对齐 Android `SettingsBrowseScreen` |
| 2.5 | **下载队列交互**：只有全部暂停/单项取消 | `ui/tasks/DownloadsScreen.kt:53-60` | 单项暂停/恢复 + 拖拽排序 |
| 2.6 | **备份粒度**：无创建选项勾选、无选择性恢复 | `SettingsScreen.kt:1760-1815` | 对齐 Android `SettingsDataScreen` 的选项集 |
| 2.7 | **书架展示选项**：只有未读角标；无已下载角标/新章节点/条目数/网格尺寸面板 | `LibraryScreen.kt:274` | 展示选项对话框 |
| 2.8 | **阅读器细节**：无预载页数设置、无自定义色彩矩阵滑杆、无 Hiatus/Cancelled 状态映射、翻页无瞬时页码提示 | `ReaderSettingsPanel.kt:129-163`, `MangaDetailScreen.kt:1134-1139` | 逐项补齐 |
| 2.9 | **首次运行引导（Onboarding）**：无 | Android 有 `OnboardingScreen` | 简版引导（仓库添加/备份位置/主题） |
| 2.10 | Tracker OAuth 生命周期补全（AniList/Bangumi 已验证，其余待生产矩阵） | 证据 E7 | 需真实账号矩阵，排期靠后 |

## Phase 3 — i18n 与文案

| # | 事项 | 证据 |
|---|---|---|
| 3.1 | 消灭 39 处 `recoveryText(en, zh, tw)` 内联三语，全部入 `DesktopStrings` | `ui/updates/UpdatesScreen.kt:87-178`, `NetworkSettingsCard.kt` 等 |
| 3.2 | 硬编码英文清扫：「Description」「Show more/less」「[Local]」「Queued」「missing chapter(s)」等 | `MangaDetailScreen.kt:1180-1193`, `BrowseScreen.kt:714` 等 |
| 3.3 | 日期时间格式本地化（`yyyy-MM-dd` 硬编码多处） | `MangaDetailScreen.kt:1450`, `UpdatesScreen.kt:286` 等 |
| 3.4 | 评估新增语言（当前 en/zh-CN/zh-TW；Android 40+） | `UiText.kt:493-497` |

## Phase 4 — 验证门禁与发布

- 延续 `docs/WINDOWS_RELEASE.md` 发版清单；VM 矩阵（2A/2B/2C/1D）补行；
- CI 保持绿；每 Phase 独立发版（0.2.22、0.2.23…）；
- 每个 Phase 结束时回归 `:desktop-app:test :extension-host:test :extension-sdk:test` + spotless。

---

# 第二轮调研追加（2026-09-28，对标上游 + 健壮性复查）

## Phase 5 — 健壮性 round 2 + 高价值小项

| # | 事项 | 证据 | 交付 |
|---|---|---|---|
| 5.1 | **Bug：Undo 恢复下载后队列不会重新启动**——`restoreDownloads()` 只入队不 `start()`，队列已排空时恢复项永远卡在 QUEUED | `DesktopDownloader.kt:673-690` | 末尾补 `start()` |
| 5.2 | **Bug：同步客户端游标 409 空洞后永久失败**——`nextCursor` 先落库再 push，崩溃后下次 push 永远 409 且无自愈 | `HttpTransport.kt:42-44`, `SyncEngine.kt:92-107` | 409 gap 时拉 `/v1/head` 重置本地游标后重试；补崩溃窗口测试 |
| 5.3 | 阅读器防睡眠（对齐 Android keepScreenOn）：Windows 无人调用 `SetThreadExecutionState`，全屏阅读会中途息屏 | `ReaderPreferences.kt:58`（Android） | 阅读器打开时 ES_DISPLAY_REQUIRED，退出恢复；设置开关 |
| 5.4 | `MangaReaderSettings.encode()` 对非 object 的合法 memo_json 静默清空 | `desktop-library-data/.../MangaReaderSettings.kt:78-79` | 非 object 时原样保留 |
| 5.5 | `DesktopNavigator.navigate(MangaDetails)` 命中已存在路由时不触发 `onDestinationChanged` | `DesktopNavigator.kt:117-126` | 补回调 |
| 5.6 | `BatchMigrationRunner.start()` 无重入守卫 | `BatchMigrationRunner.kt:35-59` | 活动任务存在时直接返回 |
| 5.7 | 小项打包：书架随机一本（shuffle）、详情页分享/复制链接、章节行「在 WebView 打开」、扩展自动更新检查（启动时一次+每日）、更新 tab 角标计数、书库更新「仅电源供电」门控 | 调研报告 §中低价值 | 逐项小改 |

## Phase 6 — 阅读器深化（中工作量项）

- 6.1 双页跨页拆分（宽图检测/拆分/旋转适配，对齐 `dualPageView` 语义）；
- 6.2 翻页过渡动画（FLIP/FADE 等子集 + 开关）；
- 6.3 自定义色彩滤镜滑杆（色相/亮度/对比度）+ 自定义亮度；
- 6.4 次要偏好：翻页闪屏开关、双击缩放速度/起始、webtoon 缩放开关。

## Phase 7 — 设置与数据管理补全

- 7.1 设置内搜索；7.2 清除数据库（非书库数据清理页）；7.3 存储占用明细；7.4 智能更新「非更新周期」过滤；7.5 下载设置补全（仅未读/分类选择/CBZ/长图拆分）。

## Phase 8 — 上游同步（基线 424bbc53b，落后 44 commits）

> 注意：历史已 squash，`git merge-base` 不可用；`upstream-sync-check.yml` 需改为读取 `docs/upstream/MIHON_CHANGELOG.md` 记录的基线（当前已坏，每周检查在报错而不是报告）。

- 8.1 修 `upstream-sync-check.yml` 基线回退；
- 8.2 cherry-pick 修复簇：阅读历史重复计数、跟踪条目误标重复、track 恢复保留远端、被排除 scanlator 章节可直接打开、书架搜索跳转修复、stub 图源重写修复、Weblate 翻译；
- 8.3 cherry-pick 扩展商店批次（含 `093841105` 签名密钥校验——需对照桌面端 `ExtensionStoreService` 审查）；
- 8.4 DB 重构链整组评估（与本仓库 sync 表冲突，要么整组要么不动，需手工迁移编号调和）；
- 8.5 Apollo GraphQL tracker 重写：等上游进 tag 再评估。

## Phase 9 — 结构性健壮性（排期靠后）

- 书库 DB 快照读移出 UI 线程（`LibraryPresenter` 等直调 `*Snapshot()` 的位置收敛到 IO）；
- `claimNextDownload` 持锁落盘解耦；
- 同步服务器速率限制；
- extension-host `Main` 未捕获异常处理器。

## 执行约定（agy 批次）

1. 每批只做一个 Phase 内的子集，最小 diff，不动 Android 端；
2. 遵循现有 Kotlin/Compose 写法与 `DesktopStrings` 三语模式；
3. 全部测试 + spotless 绿才交付；不自行 git 提交；
4. 每批交付后人工确认，再发版。
