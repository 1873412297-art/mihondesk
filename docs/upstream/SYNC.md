# 上游同步规程（SYNC）

本文档定义 mihon-w（Mihon Windows 桌面移植 fork）与上游 [mihonapp/mihon](https://github.com/mihonapp/mihon) 的同步机制。配套监测工作流见 3B（GitHub Actions weekly）；首轮演练要求见 3C。

## 1. Upstream Remote 配置

```bash
git remote add upstream https://github.com/mihonapp/mihon
git fetch upstream --tags
```

> 上述 remote 已于 2026-09-20 由维护者添加并核实（`git remote -v` 可见 upstream）；不要推送到 upstream。

## 2. 同步节奏

- 触发条件：**落后上游最新 tag（或 main 分支新提交）超过 2 周**即触发一次合入；宁可小步多次，不要攒半年一次大合并（冲突面随落后时长复利增长）。
- 监测：weekly Actions 工作流比对上游最新 tag 与本仓库合并基点，超期自动开 issue（3B）。
- 当前基线：上游 main 提交 `7f342ca07`（2026-08-29，PR #3874，**尚未打 tag**；最近的上游 tag 是 `v0.20.4`，2026-08-05）。详见 `docs/upstream/MIHON_CHANGELOG.md` 副本与 3C 演练记录。

## 3. 合并方式

- 优先 **merge 上游 tag**，不 rebase——与本仓库历史一致（如 `Merge tag 'v0.19.7'`）：

```bash
git fetch upstream --tags
git merge --no-ff <tag>          # 例：git merge --no-ff v0.20.5
```

- 若两次 tag 之间间隔过长，允许 **merge 上游 main 的指定提交**（如当前基线 `7f342ca07` 这类未打 tag 的提交），合并消息格式相应改为 `Merge commit '<sha>'`（例：`Merge commit '7f342ca07'`）。
- merge commit 消息格式：`Merge tag '<tag>'` / `Merge commit '<sha>'`，必要时附一段本 fork 侧冲突处理摘要。

## 4. 每次同步后的强制检查清单

合入上游 tag 后、推送/发版前，以下检查必须全部通过并留证（证据归档到 `docs/superpowers/evidence/<日期>-upstream-sync.md`）：

| # | 检查项 | 说明 |
| --- | --- | --- |
| 1 | **1F Android 全量回归** | Android 模块全部单测 + `assembleDebug` 构建（CI 门槛，见 1F 定义） |
| 2 | **桌面模块关键测试** | `desktop-app`、`desktop-library-data`、`desktop-webview-host`、`extension-host`、`reader-core` 关键测试套件通过 |
| 3 | **Extension API `libVersion` 变动检查** | 比对上游 `ExtensionLoader.SUPPORTED_LIB_VERSIONS`（`app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt:51`）与桌面侧等价物（`desktop-app/.../ExtensionStoreService.kt`、`desktop-app/.../compat/TachiyomiExtensionConverter.kt`、`extension-sdk/.../ExtensionPackageValidator.kt`）；新增支持的 libVersion 必须在三处同时落地，否则扩展会加载失败 |
| 4 | **更新 changelog 副本** | 将上游自上次基线以来的变更追加到 `docs/upstream/MIHON_CHANGELOG.md` 副本，并记录本次合并基点 tag |

## 5. 冲突处理原则

- **Android 侧尽量跟随上游**：`app/`、`data/`、`domain/`、`source-api/`、i18n 等共享模块冲突时，默认接受上游版本，本 fork 的改动以最小 diff 形式重新施加。
- **桌面侧 shim 按需适配**：冲突或破坏性 API 变更涉及本 fork 新增/重写的 shim 层时，以适配上游为准，不做反向修改。关键 shim 位置：
  - `extension-host/src/main/kotlin/android/*`（Android API 的 JVM 替身层）
  - 扩展转换器：`desktop-app/src/main/kotlin/mihon/desktop/extension/compat/TachiyomiExtensionConverter.kt`
  - WebView host：`desktop-webview-host/`
  - 桌面扩展商店：`desktop-app/src/main/kotlin/mihon/desktop/extension/ExtensionStoreService.kt`
- 单次合并冲突文件 >50 时，按小步多次原则拆 tag 分段合入，不要硬啃。
- 冲突解决结果必须在 merge commit 消息或配套 issue 中记录：哪些文件跟随上游、哪些 shim 做了适配。

## 6. 相关文档

- `docs/upstream/README.md` — 本目录说明
- `docs/upstream/MIHON_CHANGELOG.md` — 上游 changelog 副本（每次同步必更新）
- `docs/upstream/suwayomi-reference.md` — Suwayomi 参考基线
- `docs/superpowers/plans/2026-09-20-production-readiness-continuation.md` §4 — Phase 3 任务与 DoD
