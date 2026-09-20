# 1C 备份实机往返闭环证据（codec + 真实 Android 应用）

日期：2026-09-20 · 环境：Android 模拟器 mihon-test（android-35 google_apis x86_64，本机 handcrafted AVD）· 桌面端：0.2.18 app image（`329B0023…F88`）· Android 端：`app-x86_64-debug.apk`（当前提交构建，包名 app.mihon.dev）

## 链路一：Android 编码器 → 桌面导入（字段级）

1. 启用 `DesktopBackupFixtureWriterTest`（真实 Android `Backup` protobuf 编码器）生成 `app/build/plan2-fixtures/android-generated.tachibk`（499 B，含校验和）。
2. 桌面 CLI `--import-backup` 导入隔离数据目录：SUCCEEDED（1 漫画 / 1 章节 / 1 分类 / 3 偏好导入 / 3 跳过）。
3. SQLite 字段对照（`build/1c-roundtrip-20260920/desktop-import/database/library.db`）：

| 字段类 | Android 原值 | 桌面导入值 | 结果 |
| --- | --- | --- | --- |
| 漫画身份/元数据 | source=42, url=/cross-platform, title=跨平台备份, author=Windows 迁移验证 | 全部一致 | ✅ |
| 章节进度 | read=true, lastPageRead=7 | read=1, last_page_read=7 | ✅ |
| 分类+顺序 | Android 收藏, order=7 | sort_order=7，关联正确 | ✅ |
| 历史 | lastRead=1700000000000, duration=90 | 完全一致 | ✅ |
| Tracker | syncId=1, mediaId=420, libraryId=2, lastChapterRead=1 | 完全一致 | ✅ |
| 偏好 | 3 个可迁移 + __PRIVATE_/__APP_STATE_/unrecognized 各 1 | 3 导入（类型/值正确，分类 id 700→1 合理重映射）；3 跳过且报告原因 PRIVATE/APP_STATE/UNKNOWN | ✅ |

## 链路二：桌面导出 → Android 解码（契约测试）

- 桌面 `--export-backup` 生成 `desktop-export.tachibk`（389 B）。
- 新增 `DesktopExportDecodeContractTest`（app 模块，真实 Android `Backup` protobuf 解码器）解码并断言六类字段：1/1 通过，无失败无跳过（绝对路径传入，gradle 转发 `mihon.plan2.desktopExport`）。

## 链路三：桌面导出 → Android 应用恢复（UI 实测）

- 模拟器安装 debug APK → 完成引导（存储目录 Documents）→ Settings → Restore backup → SAF 选择 `desktop-export.tachibk` → 确认对话框正确列出 "Missing sources: Android Fixture Source" 与 "Trackers not logged into: MyAnimeList"（证明应用完整解析了备份元数据）→ Restore。
- 验证：Library 出现分类 "Android 收藏" 与漫画 "跨平台备份"；详情页显示作者、In library、1 chapter（第 1 话）、Tracking 区块。
- 备注：引导完成后系统还处理了早期 `am start` 遗留的 `file://` intent 并报 EACCES（file:// 无授权属预期），与 SAF 流程无关。

## 链路四：Android 应用再导出 → 桌面再导入

- 应用内 Create backup 生成 `app.mihon.dev_2026-09-20_03-57.tachibk`（350 B），拉取后桌面 `--import-backup`：SUCCEEDED，漫画/章节/分类回归，章节已读状态保持（unreadCount=0）。

## 结论与边界

- **双向往返闭环成立**：codec 层（两方向）+ 真实 Android 应用层（恢复+再导出）全部通过，六类字段无 P0 差异。
- 样本为合成但由**真实 Android 编码器/应用**产生，非 mock；数据形状覆盖设计的不保留项（私有/应用状态/未知键）。
- 边界：未测 Suwayomi 方向（docker 起 Suwayomi-Server 互导，留待后续）；未测大库（≥1000 部）与下载图片不迁移（设计如此）；单一 Android 版本（API 35）。
- 交付物：`DesktopExportDecodeContractTest.kt` + `app/build.gradle.kts` 属性转发（已提交 a16ffe450）。
