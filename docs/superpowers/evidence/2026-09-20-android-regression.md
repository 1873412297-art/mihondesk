# Android 回归门槛证据（1F 本地回归）

日期：2026-09-20 · 提交基线：`a7b11cffb`（工作树含未提交文档/脚本改动，代码未改）

## 环境

- Gradle runtime：Temurin JDK 21.0.12.1（项目本地 `.jdks/temurin-21`，因 Metro Gradle 插件要求 JVM ≥21）
- 主程序工具链仍为 Java 17（未变更）
- OS：Windows 11 Insider Preview Build 26220 · i9-13980HX · 32 GB

## 执行与结果

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 桌面模块单测 | `./gradlew.bat :desktop-app:test :reader-core:test :desktop-library-data:test :extension-host:test` | BUILD SUCCESSFUL（8m 52s） |
| Android 全量单测 | `./gradlew.bat :app:testDebugUnitTest` | BUILD SUCCESSFUL（2m 58s），仅 1 项跳过（`DesktopBackupFixtureWriterTest`，fixture 写出类测试，非失败） |
| Android APK 构建 | `./gradlew.bat :app:assembleDebug` | BUILD SUCCESSFUL |

关键测试输出摘录：`DesktopBackupImportContractTest` 2 项通过（真实桌面数据库导入当前 Android 编码器、legacy fallback 与偏好类型保留）；`MigratorTest` 6 项通过。打包提示 6 个 native 库未 strip（imagedecoder2/resize/sqliteJni/ssiv_crop/webgpu/zstd-kmp），为正常打包行为。

## 结论与边界

- 共享改动后 Android 单测 + debug APK 构建在当前提交上全绿，1F 本地回归门槛通过。
- `verifyCleanDistribution` 与 THIRD-PARTY-DESKTOP 许可比对属于发版流程，随下次 `assembleWindowsRelease` 执行。
- CI 工作流（build.yml）接入桌面测试步骤尚未提交，属 Phase 2 发版门禁工作。
- 未提交任何代码改动；本次仅验证。
