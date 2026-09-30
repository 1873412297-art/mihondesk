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

## 2026-09-29 执行记录（Phase 7–9）

- Phase 7：已接入设置搜索、按来源清理非书库数据、存储占用明细、非更新周期估算过滤，以及自动下载的未读和分类条件。7.5 的 CBZ 保存及长图拆分尚未实现；这两项需要同时调整下载落盘、断点恢复、离线资源注册和阅读器读取路径，不能只添加开关。
- Phase 8.1：每周上游检查在没有 merge-base 时读取并校验 `docs/upstream/MIHON_CHANGELOG.md` 的基线；本地用 `upstream/main` 验证了报告生成。
- Phase 8.2：桌面端适配了备份恢复保留已有跟踪远端条目、同章节多份历史的阅读时长合并，以及被 scanlator 过滤的直达章节。上游跟踪误判重复、来源名搜索跳转和 stub source 修复位于 Android 特有路径，桌面端当前没有对应的判重查询、该来源名跳转或持久化 stub map；Weblate 提交只修改 Android 资源。
- Phase 8.3：审查了桌面扩展安装器的签名校验，并让商店候选先按已安装签名筛选，再选版本。上游扩展列表的商店归属及安装错误展示已在下方 2026-09-29 记录中补齐（商店名/仓库标签与安装失败分类）。
- Phase 8.4：上游的 DB 重构涉及 Android 的 `mangas.sq`、`chapters.sq`、迁移 `16.sqm` 与仓库的桌面 SQLDelight/sync 表结构不兼容；暂不 cherry-pick 整组，需单独设计桌面迁移和数据往返验证。
- Phase 8.5：本地 `upstream/main` 的 Apollo GraphQL 提交 `ec614bb4c` 尚未包含于任何 tag，按计划等待上游 tag。
- Phase 9：已把部分书库快照读移至 IO、下载 claim 的队列落盘移出锁、同步接口加入每令牌速率限制，并为 extension-host 增加未捕获异常日志。其余 `LibraryPresenter` 直调快照位置仍需迁移。

本记录是当前工作树的实施状态，不代表 Phase 7–9 全部完成或已发布。

## 2026-09-29 执行记录（Phase 7.5 · CBZ 保存）

- 7.5 的 **CBZ 保存已实现**，作为可选项 `download.save_as_cbz`（`DesktopPreferences.saveChapterAsCbz`），**桌面端默认关闭**；Android 的 `save_chapter_as_cbz` 默认开启，此处按最小风险取舍，改默认值只需一行。设置项位于「下载」设置面板，并已登记进设置搜索。
- 开启后章节发布为单个 `<mangaDir>/chapter-<id>.cbz`：先写 `<...>.cbz_tmp` 暂存文件，再原子改名到目标；若目标位置已有目录（此前以文件夹模式下载过）或旧 cbz，先移到 `.previous`，注册失败时按反向顺序回滚并把暂存文件删除，页目录 `chapter-<id>_tmp` 始终保留以保证可重试。注册 `relativePath = chapter-<id>.cbz`、`assetKind = ARCHIVE`。
- `Library.sq` 的 `isLocalChapterAssetRegistered` 不再硬编码 `asset_kind = 'DIRECTORY'`，改为按值比较；查询变更不涉及 schema，`verifySqlDelightMigration` 通过。
- 阅读与删除路径**未改读取实现**：`LocalChapterSourceFactory` 早已按 zip magic 走 `ZipChapterSource`，`DesktopReaderCatalog.hasReadableContent()` 也已接受文件形态；`deleteChapter`、`deleteReadChapters`、启动恢复与重新注册改为按「目录或 .cbz」解析。两种布局可并存、可读。
- 长图拆分（`split_tall_images`）在本批次**尚未实现**，已按独立批次在下方记录中完成。
- 验证：`./gradlew spotlessCheck test verifySqlDelightMigration` 全绿；新增 `ArchiveChapterDownloadTest`（6 例）与 `ArchiveChapterReaderTest`（1 例，下载后断网仍能经 `DesktopReaderFactory` 解出 2 页）。

## 2026-09-29 执行记录（Phase 9 收尾 · LibraryPresenter 库读/库写移出 UI 线程）

- 把 `LibraryPresenter` 中仍在调用线程上做库读/库写的入口移到 `Dispatchers.IO`：`migrateDuplicateTo`（会重写源漫画的全部章节）、`toggleChapterBookmark`、`toggleChapterRead`、`updateMangaInfo`、`resetMangaInfo`、`setDetailFavorite`。
- `setDetailFavorite` 的 `Boolean` 返回值改为回调 `onResult(Boolean)`（默认空实现）；回调经 presenter 自身 dispatcher 派发，调用方可安全更新 UI 状态。`MihonDesktopApp` 两处调用点（详情页加入/移出书库、组织动作确认框）改为回调式，撤销提示链路不变，并补上「加入书库失败」的错误提示（此前该分支忽略返回值、静默失败）。
- 章节设置的持久化改到后台并**串行化**：这类写是「读 manga 行 → 覆盖整组 settings 字段」，并发执行会丢掉较新的选择，因此用单消费者 `Channel` 按调用顺序逐个落盘（`persistChapterSettingsInBackground`）。`updateChapterSettings` / `resetChapterSettingsToDefault` 仍同步更新内存覆盖值，保证 UI 即时反馈。
- 仍未覆盖：`currentChapterSettings` 的兜底分支在没有内存覆盖且详情漫画不匹配时仍会读库（只由 `updateChapterSettings` 触发，属罕见路径）。
- 测试相应更新：`ChapterSettingsTest` 两处在读回持久化行之前先等待写入；`EditMangaInfoTest` 等待 mutation port 收到记录；`LibraryPresenterTest` 用 `MutableStateFlow` 接住回调结果。
- 验证：`./gradlew spotlessCheck test verifySqlDelightMigration` 全绿（`desktop-app` 1004 例 0 失败）。

## 2026-09-29 执行记录（Phase 7.5 · 长图拆分）

- 7.5 的**长图拆分已实现**，作为可选项 `download.split_tall_images`（`DesktopPreferences.splitTallImages`），**桌面端默认关闭**；Android 的 `split_tall_images` 默认开启，改默认值只需一行。设置项位于「下载」设置面板，并已登记进设置搜索。
- 拆分规则对齐 Android `ImageUtil.splitTallImage`：`height > width * 3` 且 `partCount = (height - 1) / optimalHeight + 1 > 1`；`partHeight = height / partCount`（整除），第 k 段覆盖 `[k * partHeight, (k + 1) * partHeight)`，**最后一段**吃掉余数（到 `height`）；每段全宽，JPEG 质量 1.0。Android 的目标高度是 `2 * max(屏幕高, 屏幕宽)`，桌面下载路径没有屏幕指标，改用常量 `OPTIMAL_SPLIT_HEIGHT = 4096`（约 2×4K 屏高）。段名 `%03d__%03d.jpg`（页号__段号），与 Android `splitImageName` 一致。
- 拆分用 `java.desktop` 的 `ImageIO`（新增 `TallImageSplitter`，直接持有 `ImageIoPageDecoder` 并按 `decodeRegion` 逐段解码），**不依赖 ImageMagick 子进程、不新增依赖**。分段先写 `<段名>.part` 再原子改名，全部段落通过既有 `isValidPage` 校验后才删除原图；任何异常都会删掉已产出的段落、保留原图、记 WARN 日志，并把该页视为「未拆分」——拆分永不导致下载失败。
- 命中以下情况不拆：动图（`ImageMetadata.frameCount > 1`）、ImageIO 无法解码的格式、`shouldSplit` 为假。
- 分页模型改为「页 → 文件列表」：新增 `pageFiles` / `pageMetadata` / `validPageMetadata`，`finalizeChapter`、`inspectChapterAt`、断点复用扫描与「从已发布目录回填」全部页级感知；段落缺失即视为该页未下载（manifest 的 `partCount` 已知时要求段落齐全，未知时要求从第 1 段起连续无缺口）。
- manifest 新增默认值字段 `partCount`（默认 1，`encodeDefaults` 关闭 → 未拆分章节的 manifest 字节与改动前完全一致，不产生 churn）；分段页的 `sizeBytes` 为各段之和、`modifiedAtMillis` 取各段最大值。
- CBZ 侧修正归档页序：`ArchiveImage.ORDER` 改为依次比较「页号、段号、名字」，`001__001.jpg` 现在紧跟 `001.jpg`（非页码名仍按 `Int.MAX_VALUE` 排在最后，行为不变）；`inspectArchivePayload` 按 manifest 的 `partCount` 逐页消费若干张图，页大小为其图片之和。
- 「拆分中途被强杀」可能留下与整页并存的孤立段落（原图在最后才删，所以页面本身是完整的）：`finalizeChapter` 发布前会清理掉整页仍然存在时的 `%03d__%03d.jpg`，避免阅读器把同一页显示两次。
- 顺带修了一处与本功能无关的 Windows 健壮性问题（可独立丢弃）：`deleteChapter` 在另一线程仍持有该章节页面文件时会因文件共享冲突返回 `false`，删除静默失败——典型触发是「下载器刚构造、启动恢复正在校验该章节」与用户删除同时发生。`DownloadDiskProvider` 现在对目录树与归档删除做短暂重试（最多 4 次、25ms 递增退避，共约 150ms），失败语义不变（仍会抛出/返回 false）。复现证据：把重试关到 1 次后，`--tests "mihon.desktop.download.*"` 连跑 5 次有 1 次挂在 `DesktopDownloaderTest > enqueue redownloads a completed chapter removed after startup`（`deleteChapter(...) shouldBe true`）；打开重试后同配置连跑 6 次全绿。另有报告称同一线程并发解码时 `deleteChapter` 失败率由 500 次中 121 次降到 2 次。
- 另有两条**既有**、与本批次无关的负载敏感 flake（都是 `ui/browse` 下的 Compose UI 测试，本批次未改动这两个文件）：`UnifiedOnlineMangaDetailTest`（`ComposeTimeoutException`）与 `SourcePaginationRecoveryTest`（`UncompletedCoroutinesError`）。单独重跑分别 10/10、6/6 全绿，只在全量套件负载下偶发。
- **未改**：阅读器图源（`DirectoryChapterSource`/`ZipChapterSource` 的自然排序已让各段各自成页、顺序正确）、`DownloadCacheCleaner`、队列持久化格式。
- 验证：仓库级 `./gradlew spotlessCheck test verifySqlDelightMigration` 全绿；新增 `TallImageSplitTest`（12 例）与 `SplitTallImagesDownloadTest`（5 例，含断网后经 `DesktopReaderFactory` 读出 6 页、CBZ 与目录布局页数一致、编码失败仍正常完成）。

## 2026-09-29 Phase 8.4 评估结论（上游 DB 重构链）

> 这一节就是 8.4 要求的「整组评估」结论。判定依据与可复现命令都在下面。

**链条本身**（基线 `424bbc53b..upstream/main`，`git log --oneline 424bbc53b..upstream/main -- data/src/main/sqldelight` 得 10 个提交，其中只有 2 个带迁移）：
`c67a33f3d`（上游 `15.sqm`：删 `version`/`is_syncing`/`last_modified_at` 与 5 个触发器）→ `a4abb6292`（拆通用更新查询）→ `f8fff318b`（`PartialUpdate` + 按字段追踪）→ `986f46c09`（上游 `16.sqm`：`favorite` + `date_added` + `favorite_modified_at` → 可空 `favorite_at`，重建 3 个视图）。另有 5 个纯查询提交（`532575e29`、`8bfa495f3`、`9a77baedc`、`b74e010bf`、`ed17ece4e`）可独立取用；`fc5592ff7` 无关。

**真正的冲突不在桌面 schema**（桌面根本不编译 `data/`、`domain/`），而在两处：
1. **本仓库自己的 Android 迁移与上游同号反义**：`data/.../migrations/15.sqm`、`16.sqm`（来自 `fb92c2da2`，建 `sync_state`/`sync_peer_state` 并改写触发器）与上游在同一起点（最高 `14.sqm`）各自新增的 `15.sqm`/`16.sqm` 意图相反；而本仓库的 Android 同步（`app/.../data/sync/*`，约 1534 行）恰好依赖上游要删掉的列。
2. **桌面侧是「schema + 协议」耦合单元**：`Library.sq` 的 `favorite`/`date_added`/`favorite_modified_at`/`last_modified_at` 既被增量游标查询用（`:693-703`），又同步到 `sync-core/.../AndroidBackupDtos.kt`（字段 13/100/106/107/109），合并策略是 `SyncMergePolicy.kt:69-85` 对 `favoriteModifiedAt` 做 LWW——所以 `favorite_at` 是**线上协议**变更，不是本地 schema 变更。

**一个会直接写坏数据的坑**：上游 `16.sqm` 的回填写成 `coalesce(nullif(date_added,0), favorite_modified_at * 1000, 0)`，即假定该值是**秒**；而桌面写的是毫秒（`LibraryPresenter.kt` 用 `System.currentTimeMillis()`），本仓库的 Android 触发器也已改成毫秒。照抄会让 `date_added` 为 0 的收藏得到 1000 倍的时间戳。

**结论：4 个带 schema 的提交继续搁置，但把门槛写清楚**（不再是「范围未知」）：
1. 上游先把含 `986f46c09` 的版本**打 tag**（本地 clone 未抓 upstream tag，需 `git fetch upstream --tags` 确认）；
2. 本仓库就用书面定下同步线上格式（`sync_*` 是否采用 `favoriteAt`；Android 那套 sync 脚手架保留还是删除）；
3. 先做一次专门的提交把 Android 迁移重编号为 `17/18`，之后上游合入才是机械动作。

**可以现在就做的**（纯查询、零 schema 影响，桌面侧按概念移植而非 cherry-pick）：`8bfa495f3`+`ed17ece4e`（分类顺序在 SQL 里算）与 `9a77baedc`（tracking insert→upsert，避免重复插入撞唯一约束）——**这两项已于 2026-09-30 采纳**，见下方「环境修复」一节末段；`b74e010bf`（排序下推到视图）**不做**：桌面有 7 种用户可选排序且都在 Kotlin 侧，下推收益有限。

**真要动手时的验证资产**（已有，可复用）：`DesktopLibraryDatabaseMigrationTest.kt` 的 `createVersionOneFixture`/`createVersionTwoDownloadFixture` 模式（补一个 v3 fixture）、`AndroidBackupRoundTripTest`、`SqlDelightSyncLocalRepositoryTest`、`SyncMergePolicyTest`；需要新增的是 v3→v4 迁移用例、毫秒回填用例、以及旧对端↔新对端的同步收敛用例。规模估计：桌面约 21 个主源码文件 + `Library.sq` + 新 `3.sqm` + 快照，Android 侧 2 个重编号迁移 + 同步水位线二选一。

## 2026-09-30 环境修复（磁盘残留与误判更正）

**先更正一条错误结论**：先前报告「测试泄漏 4 个 `mihondesk.exe` 进程」是**误判**。用 `taskkill` 终止时系统明确回报它们是 **正在运行的应用进程（winpid 24924，`%LOCALAPPDATA%\mihondesk\mihondesk.exe`）的子进程**，即应用自己的扩展宿主沙箱进程。实测也证明测试不泄漏：跑隔离测试与全量套件前后，`mihonw-sandbox-runtime-*` 目录 1 → 1、`mihondesk.exe` 2 → 2、无残留测试 JVM。同时提醒：`WindowsExtensionIsolationTest` 的 AppContainer 用例在本机 **跳过**（`isSupported()` 为 false，因为临时目录对 `ALL APPLICATION PACKAGES` 可读）。

**清理（本次释放约 10.9 GB）**：
- `%TEMP%\mihonw-sandbox-runtime-*`：10 个陈旧副本（**每个约 976 MB**，合计约 10.7 GB），保留仍在使用的那个（属于当前应用会话）。
- `%TEMP%\mihon-runtime-test*`：**1945 个 / 202 MB**，由 `DesktopRuntime.forTesting` 每次调用建一个且从不删除，每次全量套件约 +14。

**根因与修复（沙箱运行时副本）**：`WindowsAppContainerLauncher` 把宿主运行时整份复制到临时目录并按引用计数管理，副本在 `close()` 里删除；但删除遍历是 `close()` 的最后一步且未加保护，**单个删不掉的文件就会中断遍历**；更糟的是抛点位于 `runtimeUsers.remove(key)` 之后、`runtimeLease = null` 之前，于是下一次 release 会抛 `NoSuchElementException`，并经 `WindowsExtensionProcessManager.closeInternal()` 传播出去，中断 close 的剩余步骤与后续 shutdown。修复：抽出 `SandboxRuntimeStaging`（租约计数、`runCatching` 保护的删除、失败不改状态、拷贝失败回滚半成品）、把单个 `runtimeLease` 改成租约列表（一个 launcher 暂存多个来源也能全部释放）、`close()` 主体包进 `try/finally` 确保释放，并加入 **24 小时年龄守卫的陈旧副本清扫**（跳过本进程租用的与有活动进程镜像命中的目录；2 秒预算；每进程只跑一次；永不抛错）。新增 `SandboxRuntimeStagingTest`（6 例）。

**根因与修复（测试运行时目录）**：新增 `TestRuntimeRoots`，`forTesting` 创建的每个根都被登记，由一个惰性注册的守护 shutdown hook 在 JVM 退出时统一删除（逐项 `runCatching` + 记日志），并在首次创建前对 `mihon-runtime-test*` 做一次同规格的 24 小时年龄守卫清扫（跳过本 JVM 登记的目录）。删除只发生在 JVM 退出与清扫路径，不在 `shutdown()` 里提前删，避免破坏「shutdown 后仍读该目录」的既有用例。新增 `TestRuntimeRootsTest`（7 例）。实测：一次全量 `:desktop-app:test` 前后目录数 **0 → 0**。

**顺带采纳的两个上游纯查询改动**（8.4 结论里的「可以现在就做的」）：
- 分类位置改为在 SQL 里算（`insertCategory` 用 `coalesce(max(sort_order), -1) + 1`），消除 `DesktopCategoryService` 里「先读最大序再写」的竞态；冲突语义与显示排序不变。
- `insertTracking` 改为 `ON CONFLICT(manga_id, tracker_id) DO UPDATE`，重复/并发插入不再可能撞唯一约束。
- 一处行为位移（已确认并记录）：**空书库的第一个分类位置现在是 0 而不是 1**（与上游公式一致；`sort_order` 只用于排序，没有其它语义）。**还原备份仍逐字保留备份里的分类绝对序号**——导入器在外层插入（位置由 SQL 计算）之后，显式把备份的 `order` 写回该行。
- 这里踩到并修掉了一个**真实回归**：改动初版按「插入时重排位置」实现，导致 Android 模块的契约测试 `app/src/test/java/eu/kanade/tachiyomi/data/backup/DesktopBackupImportContractTest`（断言导入后的库与源备份投影完全相等）失败。**这是仓库级门禁发现的**——子代理只跑了 `:desktop-app`/`:desktop-library-data` 两个模块所以没暴露。修法是给导入器补一次显式 `updateCategoryOrder`，**不是**放宽那份契约测试。
- 未做的第三个上游改动（`b74e010bf` 把排序下推视图）在桌面端价值有限：桌面有 7 种用户可选排序且都在 Kotlin 侧，故不做。

## 2026-09-30 收尾（测试预算补齐 + 关闭路径加固）

- **测试预算补齐**：`ui/reader/ReaderScreenActionsTest`（19 处）、`ui/settings/SettingsScreenTest`（8 处）、`ui/settings/AppUpdateNavigationTest`（2 处）、`ui/reader/ReaderScreenTest`（2 处）原先用的是 `waitUntil` 默认 **1000ms** 预算，是全模块最紧的一档。现按「等待对象是否跨 dispatcher」分档补齐：跨线程（`withContext(Dispatchers.Default/IO)` 之后再断言）给 30s，纯协程 launch + 重组给 15s，同步分支（其实是断言）给 5s。**没有提升任何已有预算，没有改动任何断言**；全模块已无无预算的 `waitUntil` 调用点。
- **关闭路径加固**：`WindowsExtensionProcessManager.closeInternal()` 里 `sandboxLauncher?.close()`（原 `:617`）以及 `packageHosts.values.forEach { it.close() }`（原 `:592`）都没有守卫——任一子进程或沙箱启动器的释放抛错都会中断整个关闭流程与后续 `DesktopRuntime` shutdown，把其它扩展/句柄留在打开状态。现在两处都按「逐个 host 捕获 + `DesktopLogger.warn`（带堆栈）+ 继续清理」处理，`WindowsAppContainerLauncher.close()` 自身未改。新增 `WindowsExtensionProcessManagerTest` 用例（注入两个会抛错的 host + 一个正常 host，断言三个都被关闭、遗留文件仍被删）；并做了反向对照——把 `packageHosts` 守卫还原后该用例确实失败。
- 未覆盖：`sandboxLauncher?.close()` 这处守卫没有直接用例（字段私有且类型为 final 的 `WindowsAppContainerLauncher`，构造真实实例后无法让其 `close()` 抛错；要直接覆盖需要把该类改成 `open`，属于为测试改生产代码，故停在用同形状的 package-host 环路覆盖）。


## 2026-09-29 执行记录（Phase 8.3 收尾 · 商店归属与安装错误展示）

- **商店归属（A）**：`ExtensionStoreItem` 新增 `storeName`（protobuf/JSON 商店索引用 `name`，缺省用 `badgeLabel`；legacy 仓库从 `repo.json` 的 `meta.name`/`meta.shortName` 读取，沿用原先只取签名密钥的那次请求），`repoUrl` 保持原样。新增纯函数 `extensionStoreLabel(item)` / `extensionStoreLabel(storeName, repoUrl)`：优先用商店声明的名字，否则把仓库 URL 收敛成可识别的标签（GitHub 取 `owner/repo`，其他取 `host/path`）。
- 归属**跟随被展示的那个候选**：`selectAvailableStoreItems` 未改（仍先按已安装签名筛选、再取最高 versionCode），候选自身带着 `repoUrl`/`storeName`，因此同一 `pkg` 出现在多个商店时标签描述的是胜出候选的来源；`ExtensionStoreSelectionTest` 新增两例（同包多商店、签名过滤后胜者变化）钉住该语义。UI 位置：扩展列表行与安装确认框（`Store: X`），扩展详情页的 `extension-details-repo` 由原始 URL 改为商店标签（优先取更新候选的来源，回退到已安装扩展记录的仓库），原始 URL 仍保留在「复制调试信息」中。
- **安装错误展示（B）**：安装状态新增类型化字段 `BrowseUiState.installFailure`（`DownloadFailed` / `SignatureRejected` / `StoreUnavailable` / `Unknown`），由 `classifyInstallFailure` 按异常类型判定；`ExtensionInstallStatus` 在安装进度下方按分类显示本地化标题+建议，通用错误横幅同时保留原始诊断（`ErrorDetails`），并避免再对安装错误套用下载队列的文案。新增两类异常以区分步骤：`ExtensionDownloadException`（下载阶段的 HTTP/网络失败，`ExtensionValidationException` 子类）与 `ExtensionStoreUnavailableException`（已配置商店不再提供该包/版本）。
- 商店可用性检查（`isStoreCandidateInstallable`）：仅对带 `repoUrl` 的商店候选生效——下载地址为空、其仓库已不在配置中、或当前商店列表里已没有该包时，直接报 `StoreUnavailable`，不再尝试下载（原先只会抛 `Extension download URL is missing` 或下载后 404）。
- **未移植**：上游把失败挂在条目上并提供「重试 + 错误详情」行内入口；桌面端仍只有顶部状态与错误横幅，详情对话框保留为横幅里的「错误详情」。缺源批量安装（`MihonDesktopApp` 直调 `downloadAndInstall`）与 `ExtensionUpdateChecker` 通知不在本次改动范围，仍走原有 snackbar。
- 验证：`./gradlew :desktop-app:test spotlessCheck` 全绿（desktop-app 1049 例、0 失败、21 跳过——跳过项为此前即有的环境门控测试，如真实扩展包样例、Windows 隔离/进程、联网冒烟等）。新增 `ExtensionStoreLabelTest`（5）、`ExtensionStoreAvailabilityTest`（6）、`ExtensionInstallFailureTest`（5）、`ExtensionInstallFailurePresenterTest`（4，用本地 HttpServer 分别断言下载失败/签名拒绝/商店缺包/仓库被移除四类消息），并扩充 `ExtensionStoreSelectionTest`（+2）、`DesktopStringsTest`（+1）、`BrowseScreenTest`（+1）、`ExtensionInstallStatusTest`（+3）。

## 2026-09-30 环境修复（残留的系统托盘图标登记）

- **机制**：**每一次**运行 `mihondesk.exe` 都会让 Windows 在 `HKCU\Control Panel\NotifyIconSettings\<哈希>` 里按可执行文件路径登记一条持久记录（`ExecutablePath`、`IconSnapshot`、`InitialTooltip`、`UID`）。触发点不是界面：打包运行时在构造通知服务时就装了托盘图标（`notification/DesktopNotificationService.kt` 与 `platform/DesktopNotificationService.kt` 都在 `init` 里 `SystemTray.getSystemTray().add(TrayIcon(...))`，`DesktopRuntime.kt` 对**任何**命令都会构造它们），所以 `mihondesk.exe --version` 这类无界面命令同样会登记（实测首个 `--version` 即产生记录；同一路径再次运行不再新增）。记录不随目录删除而消失，副本早已不存在，通知区域与「设置 → 个性化 → 任务栏 → 其他系统托盘图标」里仍会一直显示它——**只要记录还在，重启 explorer.exe 也没用**。
- **旧副本为何被当成另一个应用**：图标身份取自可执行文件路径（含启动文件的产品标识）。旧启动文件名为 **`MihonW.exe`**（产品名 `Mihon manga reader for Windows`），当前为 `mihondesk.exe`，因此工作树/soak 目录里的旧副本不会被归并到已安装版本，而是各算一个应用。
- **本次清理**：本机累计 39 条陈旧记录，其中 9 条路径匹配 `/mihon/i`——2 条属于真实安装（`%LOCALAPPDATA%\mihondesk\mihondesk.exe`），7 条属于 `.worktrees\suwayomi`、`.worktrees\reader-wheel-navigation-fix`、`.worktrees\desktop-reader-control-parity`、`.superpowers\sdd\reader-soak-20260915-01` 等目录下的旧副本。全部按 `ExecutablePath` 前缀逐条删除（只写 `HKCU`，无需提权）。
- **新增工具**：`scripts/clear-tray-icon-registrations.ps1 -PathPrefix <目录>` 按前缀（大小写不敏感、`[IO.Path]::GetFullPath` 归一化）逐条删除并打印路径与是否有 tooltip，支持 `-WhatIf`；**默认为安全模式**，跳过仍属于运行中进程的记录（`-KeepLiveProcesses` 只是把这一默认写明，`-IncludeLiveProcesses` 才连同运行中的一起删）。已接入两处：`scripts/verify-desktop-clean-machine.ps1`（临时沙箱）是**纠正性**的——它的无界面命令本身就会为 `mihon-sandbox-*` 里的 exe 留下一条记录，不扫就会每次运行攒一条；`scripts/tests/portable-updater.tests.ps1`（`build/portable-updater-tests-*`）是**预防性**的——那里的 `mihondesk.exe` 是 `csc.exe` 编译的纯控制台探针，实测不产生记录。**未接入** `scripts/mihondesk-updater.ps1`——它重启的是用户真实便携版，其托盘登记是合法的。

## 2026-09-30 收尾（无界面运行不再登记托盘图标；候选版本改号为 0.2.24）

- **根因修复**：上一节记录的登记来源在**产品代码**里——`DesktopRuntime` 对任何命令都构造通知服务，而它们在 `init` 里安装托盘图标。现在新增纯谓词 `DesktopCommand.needsNotificationTray`（穷举 `when`，新增命令会编译报错强制复核）：只有 `LaunchUi` / `BackgroundUpdate` / `BackgroundBackup` 安装图标，其余命令（`Version`、`Help`、导入导出、smoke、`VerifyReader`、清理后台任务…）完全不碰 shell。两个通知服务加 `trayIconEnabled`（默认 `true`，其他调用方零影响），在两个构造点由命令驱动。
- **端到端证据**：修前镜像（复制到全新目录）跑 `--version` → 该路径登记 `0 → 1`；修后重建镜像同样操作 → **`0 → 0`**；对照组 `--background-backup` → `0 → 1`（会通知的命令仍然安装图标）。测试：谓词表驱动覆盖全部 12 个命令；两个服务各一个「关掉后不加图标」用例（断言确实执行，非跳过）。
- **未覆盖**：`DesktopRuntimeFactory` 里的接线没有单测（需要完整 profile），由上面打包后的实测覆盖；`Version` / `Help` 仍会构造整个运行时（改其行为会影响锁获取与退出码，属另一件事，未动）。
- **版本号**：0.2.23 未发布、未打 tag，在其内容之上追加本次修复后统一以 **0.2.24** 发布（同版本号 MSI 会被 Windows Installer 以 1638 拒绝覆盖，递增版本号才是干净升级路径）。发布说明文件已改名更新，原 0.2.23 草稿不再作为发布文档。
