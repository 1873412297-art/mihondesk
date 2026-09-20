# agy（Gemini 3.8 Flash High）评审工作树改动与修复记录

日期：2026-09-20 · 评审工具：agy-staff reviewer（gemini-3.8-flash-high，job `review-mu97nnww-916cf7a8`）

## 背景

按用户要求接入 Google Antigravity CLI（agy 1.2.1 已预装并认证），克隆 agy-staff 到 `~/tools/agy-staff`，用其 reviewer 角色评审 2026-09-20 的未提交工作树改动（计划文档、README/WINDOWS_RELEASE 重写、SYNC.md、1A runner 脚本）。

## 评审结果摘要

评审报告（10 项发现，完整原文：`.agy-staff/jobs/review-mu97nnww-916cf7a8.result.md`）：

| # | 严重度 | 发现 | 处置 |
| --- | --- | --- | --- |
| 1 | P0 | runner 未实现 `IpcCallbackRequest`（broker_http）协议，所有真实网络步必崩 | **已修复**：重写 HostClient 为读线程 + pending-future 架构，内联实现 broker_http（urllib、重定向镜像 OkHttp 语义、failureKind 分类镜像 `DesktopNetworkHelper`、大 body 走 `broker-<uuid>.bin` 文件协议） |
| 2 | P0 | WINDOWS_RELEASE.md 相对链接 404（`docs/...` 应为 `superpowers/...`） | 已修复 |
| 3 | P1 | README 称平台期"仍在调查"与同日 1800s 证据矛盾 | 已修复（按 1800s 证据改写，注明单次运行） |
| 4 | P1 | SYNC.md 称基线为"2026-08-29 的上游 tag"，实为未打 tag 的 main 提交 | 已修复（区分 tag/commit 两种 merge 流程） |
| 5 | P1 | `--no-images` 把图片步记为 pass 造成假阳性 | 已修复（新增 `skipped` 状态，跳过即最高"受限通过"） |
| 6 | P2 | `fileName` 为 None 时 TypeError | 已修复（显式检查并报可读错误） |
| 7 | P2 | 3A 勾选但 upstream remote 未添加 | 已修复（`git remote add upstream` 已执行并核实） |
| 8 | P2 | README 引用尚不存在的"支持等级" | 已修复（改为说明矩阵当前覆盖范围与更新计划） |
| 9 | P3 | 临时目录泄漏 | 已修复（`shutil.rmtree`） |
| 10 | P3 | 单线程 executor 超时后失步 | 已修复（读线程架构从结构上消除该问题） |

## 验证

- `python -m py_compile` 通过。
- 真实环境跑 NHentai 扩展（本地 `%APPDATA%\MihonW\extensions` 唯一已装扩展）验证 broker_http 链路，结果见 `build/source-e2e-matrix-nhentai-20260920.json/.md`。

**实测结果（2026-09-20，修复后）**：install ✅ → search ✅（"touhou" 命中）→ detail ✅ → chapters ✅（66 页）→ image ✅（197 KB webp，magic bytes `RIFF....WEBP` 真实图片）。broker_http 双向 IPC 全链验证通过。仅 browse（get_popular）一步因瞬时 TLS `UNEXPECTED_EOF` 失败（归类 `environment`），判 `unsupported` 属 runner 语义（任何一步 fail 即不通过），与修复无关；7 天复测和归因仲裁步骤会处理此类瞬时故障。后续可考虑给 broker 层瞬时失败加一次重试。

## 备注

- 评审还指出：样本清单中其余 4 源（MangaDex/MangaPlus/MangaFire/BiliManga）本地无 `.mext`，跑全样本前需先准备扩展包；Keiyoushi 官方仓库 index 已是 stub（见 1A 状态）。
- agy-staff 插件原生面向 Claude Code/Codex/Pi；本环境以 companion 脚本直调方式使用（Kimi Code 无插件集成点）。
