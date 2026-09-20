# 3C 上游同步演练：分析与合并完成（2026-09-20）

日期：2026-09-20 · 依据：`docs/upstream/SYNC.md` · upstream remote 已配置并完成首次 fetch

## 现状

| 项 | 值 |
| --- | --- |
| 合并基点（merge-base） | `7f342ca07`（2026-08-29，PR #3874）— 与 SYNC.md 记录一致 |
| 上游 main 顶点 | `424bbc53b` |
| 待合入提交 | **55 个** |
| 新上游 tag | 无（最新仍为 `v0.20.4`）→ 按 SYNC.md §3 以 **merge commit** 方式合入 |
| 落后时长 | ~22 天 > 14 天阈值 → **已触发同步条件** |
| 改动规模 | 229 文件，+3497/−2292；集中在 `app/`（129）与 `i18n/`（79） |
| ExtensionLoader `SUPPORTED_LIB_VERSIONS` | 两侧均为 `listOf(1.4, 1.6)` — **无差异**，桌面侧三处等价物无需适配 ✅ |

## 与本地工作树的重叠

- 唯一重叠文件：`app/build.gradle.kts`（本地曾未提交：测试属性转发 3 行，现已提交 a16ffe450；上游亦有改动）。合并前需处理（提交或 stash）。
- 上游不感知 `desktop-*`/`extension-host`/`extension-sdk` 模块，理论上无直接冲突源。

## 需要人工评审的上游变更（对 fork 有外溢影响）

1. **#3951 Replace extension NSFW flag with content warning app-wide** — 扩展模型/加载器语义变更；需检查桌面侧 `extension-host` shim、`TachiyomiExtensionConverter`、`ExtensionStoreService` 的 NSFW 字段处理是否要对齐。
2. **#3953 / #3952 / #3954 扩展加载失败暴露与设置联动重载** — Android 侧扩展管理行为变更；桌面扩展管理建议评估功能对等（属功能差距记录，非合并阻塞）。
3. **#3965 Resolve Injekt dependencies from the Metro graph** — Android DI 重构；桌面 Metro 图若镜像了相关绑定需跟随检查。
4. **#3828 Tracker profile refresh button** — tracker 新能力；桌面 tracker 可选跟进（功能差距记录）。
5. **#3970 / #3967 Local source Year/Month/Day + ComicInfo** — `source-local` 变更；桌面本地源导入了相同模块，随合并自动获得，需跑 1F 回归确认。

## 执行计划（待用户授权后执行）

前置：用户审阅并提交当前工作树（12+ 个未提交文件，含产品修复 Filter.Select、新测试、脚本、文档、3B 工作流），或授权 stash 方案。

```bash
git merge --no-ff upstream/main -m "Merge commit '424bbc53b'"
# 预期：仅 app/build.gradle.kts 可能需手工对齐（双方追加性改动，可共存）
```

合并后按 SYNC.md §4 强制检查：
1. 1F 回归：`:app:testDebugUnitTest :app:assembleDebug` 全绿（含本次新增 DesktopExportDecodeContractTest）
2. 桌面模块关键测试：desktop-app / desktop-library-data / extension-host / reader-core
3. libVersion 检查：已预检无差异，合并后复核一次
4. 更新 `docs/upstream/MIHON_CHANGELOG.md` 副本，记录新合并基点 `424bbc53b`
5. NSFW→content-warning 变更的对齐评审（见上表第 1 项）

## 备注

- 3B 工作流若已上线，本次状态会被判为 OVERDUE 并自动开 issue —— 符合预期行为。
- 演练的首个实证：fetch、基点比对、libVersion 预检、重叠分析均按 SYNC.md 流程执行。

## 合并执行结果（2026-09-20 当日）

- **合并提交**：`bcc495a2a` `Merge commit '424bbc53b'`（--no-ff，符合 SYNC.md §3）。
- **冲突与解决（3 处，均按"Android 侧跟随上游、fork 改动保留"原则）**：
  | 文件 | 解决 |
  | --- | --- |
  | `.github/workflows/update_website.yml` | 保留 fork 的 `github.repository == 'mihonapp/mihon'` 守卫 + 接受上游 `ubuntu-26.04` |
  | `gradle/libs.versions.toml` | coil 3.6.0→3.6.3、composeGrid 1.2.2→2.8.0 接受上游；**commonsCompress 保留**（desktop-app/reader-core 在用，上游已删）；conscrypt 2.6.3→2.7.0 接受上游 |
  | `CHANGELOG.md` | fork 的 0.2.x 条目在上 + 上游 [Unreleased] 段在下 |
  | `app/build.gradle.kts` | 自动合并成功（此前预估的唯一重叠） |
- **SYNC.md §4 强制检查**：
  1. 1F Android 单测 + assembleDebug + 桌面四模块测试（desktop-app/reader-core/desktop-library-data/extension-host）：合并后全绿，BUILD SUCCESSFUL in 12m 19s（356 任务，提交 bcc495a2a）✅
  2. libVersion 合并后复核：仍为 `listOf(1.4, 1.6)`，桌面侧无需适配 ✅
  3. `docs/upstream/MIHON_CHANGELOG.md` 副本已更新为当前上游 Unreleased 并记录合并基点 ✅
- **#3951 NSFW→content-warning 影响评审**：桌面 `AxmlManifestParser` 已同时识别 `tachiyomi.extension.nsfw` 与 `tachiyomix.contentWarning`（2/3 → isNsfw），布尔模型功能等价；上游新增的 `ContentWarning` 枚举属 Android UI 层丰富化，记为可选对等跟进项，不阻塞。
- **遗留**：合并后尚未重建发行包——soak/矩阵/帧时间证据仍属合并前包身份；按"新包新身份"原则，下次发版（Phase 2 门禁）时在合并后构建上重跑关键验收。依赖升级（coil 3.6.3、composeGrid 2.8.0、conscrypt 2.7.0）已随编译与测试通过初步验证。
