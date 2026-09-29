# 桌面版工作总结与发布说明（2026-09-30，v0.2.23）

本文件总结 v0.2.23 的交付内容（对标 Android Mihon 的升级计划 Phase 6–9 与 7.5，详见 `docs/desktop-upgrade-plan.md`）。0.2.22 覆盖的是 Phase 0–3，本版是其后的实质工作。

## 一、发布物

| 产物 | 说明 |
|---|---|
| `mihondesk-0.2.23.msi` | Windows 安装包（per-user，含开始菜单与快捷方式） |
| `mihondesk-0.2.23.exe` | Windows 安装器 |
| `mihondesk-0.2.23-windows-x64-portable.zip` | 便携版（解压即用，数据随包） |

发布包内容约定与 0.2.22 一致：不含任何图源扩展、用户配置、书架数据库、Cookie 或缓存；打包链 `verifyCleanDistribution` 与 `verifyRuntimeModules` 两道校验保持强制。

## 二、阅读器深化（Phase 6）

- **双页跨页拆分**：`DualPageSplit`（关闭 / 始终 / 仅宽图）与旋转适配（选择旋转时宽图不拆分而是旋转），对齐 Android `dualPageView` 语义；页分组与翻页步进改为按逻辑页组计算，不再是固定 ±2。
- **翻页过渡动画**：`NONE / FADE / SLIDE`，可在阅读设置中选择。
- **自定义色彩滤镜**：色相 / 亮度 / 对比度三个滑杆，叠加既有预设（反色、灰度、反色灰度、棕褐、夜间）与调光百分比。
- **次要偏好**：翻页闪屏开关、Webtoon 禁止缩小、Webtoon 双击缩放。

## 三、设置与数据管理（Phase 7 与 7.5）

- **设置内搜索**：设置项注册表 + 关键字匹配，可直达对应分节。
- **清除非书库数据**：按来源列出可清理条目，可勾选「保留有阅读记录的漫画」，二次确认后清理。
- **存储占用明细**：下载 / 图片缓存 / 数据库 / 扩展 / 封面 / 日志 / 备份分项统计。
- **智能更新的「非更新周期」过滤**：按最近章节的发布或抓取间隔估算下次发布窗口，跳过尚未进入窗口的条目（默认关闭）。
- **自动下载条件**：仅未读、包含 / 排除分类。
- **CBZ 保存（可选，默认关闭）**：开启后章节发布为单个 `chapter-<id>.cbz`——先写暂存文件再原子改名，注册为 `ARCHIVE`，注册失败按反向顺序回滚；阅读与删除路径自动兼容「目录或 .cbz」两种布局。
- **长图拆分（可选，默认关闭）**：超长页面按 Android `split_tall_images` 规则切成 `%03d__%03d.jpg` 段（段高用常量 4096px 顶替 Android 的屏幕指标），用 JDK `ImageIO` 实现、不依赖 ImageMagick 子进程与新增依赖；页模型改为「页 → 文件列表」，断点复用、manifest 与 CBZ 归档页序全部页级感知。**任何拆分失败都保留原图，不影响下载**。

> 两个新选项默认**关闭**（Android 默认开启），开启只影响之后的下载，已有章节不变。

## 四、上游同步（Phase 8）

- **8.1**：每周上游检查在取不到 merge-base 时改为读取并校验 `docs/upstream/MIHON_CHANGELOG.md` 的基线（此前是报错而不是报告）；顺带修正 `docs/upstream/SYNC.md` 中过期的基线行。
- **8.2**：桌面端适配——备份恢复保留已存在的跟踪远端条目、同章节多份历史按阅读时长合并、被 scanlator 过滤的章节仍可直达阅读。
- **8.3**：**扩展商店归属**（扩展列表行、安装确认框、详情页显示来源商店；同一包存在于多个商店时跟从被选中的那个候选），以及**安装错误分类展示**（下载失败 / 签名被拒 / 商店已不再提供 / 其他，四类各自有标题与处置建议，三语）；商店候选一律先按已安装签名筛选再比版本。
- **8.4**：完成计划书要求的「整组评估」——上游 DB 重构链（`c67a33f3d`→`a4abb6292`→`f8fff318b`→`986f46c09`）与本站的冲突点是本仓库自己的 Android 迁移 15/16 号同号反义、以及桌面侧同步线上格式与 schema 的耦合；**结论是 4 个带 schema 的提交继续搁置并写明门槛**，另有 3 个纯查询提交可独立取用。
- **8.5**：等待上游给 Apollo GraphQL tracker 相关提交打 tag。

## 五、结构性健壮性（Phase 9）

- 书库的库读 / 库写从 UI 线程移到 IO（`LibraryPresenter`：迁移、章节已读与书签、编辑信息、加入/移出书库）；`setDetailFavorite` 改为回调式并补上「加入书库失败」提示。
- 章节设置的持久化改到后台并**串行化**——这类写是「读整行再覆盖整组设置字段」，并发执行会丢掉较新的选择。
- 下载 claim 的队列落盘移出锁。
- 同步服务器加入每令牌速率限制（超限 429 + `Retry-After`）。
- extension-host 增加未捕获异常日志。
- 删除下载在 Windows 上会因文件共享冲突（阅读器解码、启动恢复校验仍在读）偶发失败，改为短暂重试，失败语义不变。

## 六、验证与质量

- 仓库级门禁 `./gradlew spotlessCheck test verifySqlDelightMigration` **全绿**：`desktop-app` 1062 例、`desktop-library-data` 144 例、`app`（Android 侧契约）35 例、`reader-core` 189 例、`sync-server` 16 例，0 失败。
- **Compose UI 测试预算加固**：Compose Multiplatform 的 `runComposeUiTest` 默认 `testTimeout = 60s`，且同一个值既充当协程预算（用尽报 `UncompletedCoroutinesError`）又充当框架 idle 等待阈值（用尽报 `ComposeTimeoutException`），重负载下会误报。现于 11 个文件、61 处显式传入 5 分钟预算（**仅测试改动，不动断言**）。
- 沙箱扩展运行时目录（`%TEMP%\mihonw-sandbox-runtime-*`）的清理加固：不再因单次删除失败而中断整个 `close()`，并加入按目录年龄守卫的陈旧目录清扫。

## 七、发版门禁状态

已在**当版本构建**（`ab71ec5b4`，`dirty=false`）上完成的本地项：

- [x] 提交并记录构建身份：`mihon-build-info.properties` 记录 `version=0.2.23`、`revision=ab71ec5b456604bc30eb895e8f58fe6121dbc22a`、`dirty=false`
- [x] 仓库级门禁 `spotlessCheck` + `test` + `verifySqlDelightMigration` 全绿（1446 例）
- [x] 发行包清洁校验 `verify-release-clean.ps1` → PASS（无个人配置、无已装扩展、无用户数据）
- [x] MSI 结构断言 `verify-msi-package.ps1` → PASS（WiX 升级码、许可、`RemoveFiles` 之前的延迟卸载钩子；**执行未测**）
- [x] 便携交接失败注入 `scripts/tests/portable-updater.tests.ps1` → **17 项全 PASS**（成功/取消/身份不符/失败退出/回滚/超时/非法提交/校验和拒绝/占用配置拒绝/替换保留旧数据/替换后校验失败回滚/归档不得夹带用户数据/路径穿越拒绝/目录联接拒绝/两处中断恢复）
- [x] 便携隔离目录验证 `verify-desktop-clean-machine.ps1 -SkipBuild` → `mihondesk 0.2.23 (Windows x64)`、`--help`、隔离 `data` 目录与备份导出全部通过

仍需在你环境完成（**缺一不发**）：

- [ ] **2A**：VM 快照上 N-1→N 升级与卸载两格实测（脚本部分已 PASS，执行部分必须实机）
- [ ] **2C**：对真实已发布 Release 资产完成 检查→下载→清单/SHA 校验→交接→启动 端到端（断点续传与校验失败路径各一次）——依赖 Release 已存在
- [ ] **1D**：干净 Windows 10 22H2 / Windows 11 上的安装冒烟（上面的隔离目录检查明确声明**不等同于**干净机器验收）
- [ ] `verify-release-assets.ps1 -Tag v0.2.23`（需先有 Release）
- [ ] 推送提交并打 tag，上传 EXE、MSI、便携 ZIP、`SHA256SUMS.txt`、`desktop-version.txt`、`mihon-build-info.properties`，核对远程附件摘要后设为最新正式版

本版跨越 Phase 6–9 与 7.5，差异面明显大于 0.2.22，建议按「新包新身份」原则把 VM 矩阵**全量重跑**，不要沿用 0.2.22 的结论。

## 八、已知限制

1. CBZ 与长图拆分两项默认关闭，与 Android 默认值不同；且只对开启之后下载的章节生效。
2. 长图拆分对 `ImageIO` 无法解码的格式（AVIF/HEIC 等）整页跳过；`OPTIMAL_SPLIT_HEIGHT = 4096` 是常量，不是真实屏幕指标，因此分段数可能与 Android 同机不同。
3. Phase 8.4 的上游 DB 重构链**未采纳**（门槛见计划书）；其中「上游回填假定秒、本站写毫秒」的陷阱已记录，将来照抄会写坏数据。
4. Tracker OAuth 生产矩阵（除 AniList/Bangumi 外）仍未验证，需要真实账号。
5. Compose UI 测试仍存在一种重负载下的**单步停顿**（某个 `waitUntil` 的标签在 60s 内始终不出现，而同类的兄弟调用 0.15s 即通过），预算无法修复；线索指向单测试 fork 内 Skiko 帧调度的共享状态。
6. 沙箱扩展的运行时目录清扫是**年龄守卫 + 尽力而为**，若进程被强杀且目录未满阈值，仍会残留到下次清扫。
