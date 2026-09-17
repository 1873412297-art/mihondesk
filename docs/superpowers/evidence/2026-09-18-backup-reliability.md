# 0.2.18 基线上的备份可靠性迭代

## 基线与范围

按用户要求重新核对：本地 `main@b02fc9e0b` 的 `desktop-version.txt` 为 **0.2.18**。本轮执行 `git fetch origin main` 后，远端仍是 `90662a9f7` / 0.2.13。工作分支 `codex/backup-reliability` 已快进到本地最新 main，保留其下载身份、偏好并发保存、恢复体验和 ANGLE 渲染改进。

生产修复提交：`26dcb3faf2f0694376cbee656d7f1a171f3d4a4e`。本轮不发布 GitHub、不覆盖已安装版本；开发应用镜像仍使用 0.2.18，并以构建提交区分。

## 复现与改动

1. 自动备份内部记录完成时间后，外层又以开始时间覆盖它。导出持续超过间隔时，下次检查会立即重复备份。确定性时钟回归先在旧基线失败，再在 0.2.18 失败。现在只记录一次完成时间，调度及结果使用相同值，继续通过 `updatePreferences` 保留其他并发设置。
2. 备份路径原先每输入一个字符就持久化，后台任务可能读取半成品路径。实际 Compose 设置页回归先失败，改为草稿、选择文件夹、异步检查和明确保存；恢复默认同样须保存。显示已保存位置及下一次备份生效的说明。现有备份不搬迁。
3. 无效自定义路径原先静默回退默认目录并报告成功。回归先失败，现在对非空无效/相对路径明确失败，保留成功时间及原恢复点。旧配置中的相对路径须在设置页重新保存为绝对路径；空值仍选默认目录。

复用现有目录校验，不改变下载路径行为。新编辑区支持英语、简体和繁体中文，按钮可换行。

## 源码验证

Corretto 23，Gradle `--max-workers=1 -Pkotlin.compiler.execution.strategy=in-process`。

- 完整 `desktop-library-data:test`：118 项，0 失败、0 错误、0 跳过。
- 受影响桌面备份、设置、导入、CLI、偏好与更新调度用例：62 项，0 失败、0 错误、0 跳过。
- 两个模块 `spotlessCheck` 及 `git diff --check` 通过。
- 验证慢导出间隔、8 个并发调度检查只发布一份备份、手动备份不推进自动时间、导出抛出 IO/取消异常后旧备份可解码且仍可重试，以及原子发布失败不残留临时文件。
- 设置回归验证未保存草稿不落盘、非法路径不落盘、中文/空格目录可保存、默认重置需确认保存、并发通知设置和后台成功时间不丢失。
- 六个渲染用例覆盖三种语言、480/1024 像素宽及浅色/纯黑主题。检查了窄窗按钮换行、文字和控件可见性。

日志：`build/backup-reliability-0218-red.log`、`backup-path-red.log`、`backup-reliability-regression.log`；XML 快照在 `build/backup-reliability-evidence/regression/`。测试扩展曾错误比较协程重建异常的对象身份，已改为检查异常类型/信息；这是测试断言修正，不是生产缺陷。

## 应用镜像与真实 EXE

`createDistributable` 和 `verifyCleanDistribution` 通过。无用户配置、书库或已装扩展混入镜像。

路径：`desktop-app/build/compose/binaries/main/app/mihondesk/mihondesk.exe`。

| 项目 | 结果 |
| --- | --- |
| 内嵌版本 | 0.2.18 |
| 内嵌提交 | 26dcb3faf2f0694376cbee656d7f1a171f3d4a4e |
| dirty | false |
| EXE SHA-256 | 329b002304fa801d368a14c3875b92780c37bf81d3c101efaa5ff8a8287aef88 |
| 应用 JAR SHA-256 | 1d14fe2e6652d092da6951161b4e16a50f243435c4d66051f7df50adeb5bb2b3 |

使用此 EXE 和内置运行时，在独立中文/空格路径资料目录执行 10 次命令：

- 导入非空合成备份、导出、恢复至另一空库：退出 0；6 张业务表逐行一致，SQLite integrity_check 和 foreign_key_check 通过。包含 2 条漫画（1 条未收藏）、2 章节、分类及关联、历史、跟踪；书签、已读和页码、笔记保留。
- 再次导入：退出 0，漫画/章节新增均为 0，业务数据不变。
- 截断 gzip：退出 2 / CORRUPT_GZIP，业务数据不变。
- Windows 文件句柄禁止替换目标备份：导出退出 2 / INPUT_IO，旧文件逐字节不变，临时文件清理。
- 保存自定义目录后自动备份成功；立即再次运行得到 SKIPPED；自动备份恢复到第三个空库，6 张表仍一致。
- 非法相对路径：退出 1，不在默认目录生成备份，不损坏自定义目录中的旧备份。

运行记录：`build/backup-reliability-evidence/packaged-20260918-023949/result.json`，旁边保存每次 stdout/stderr。复现脚本为同目录上一级 `verify_packaged_backup.py`（Python 标准库；仅用于验证，非应用运行依赖）。初次脚本错误断言默认目录不存在；Runtime 本就初始化空目录，改为核对未生成备份后复跑全部命令通过。

应用镜像 JAR 优先的 classpath 再运行 27 项备份和设置测试：全部通过，无跳过。六个渲染用例断言 UI 生产类来自上述镜像 JAR，截图位于 `build/backup-reliability-evidence/packaged-renders/`。这是应用镜像类的 Compose 渲染验证，不冒充原生窗口点击。

## 后续门槛

本轮完成两项已确认缺陷修复及独立验证，不代表整个端口完成。真实 Android/Suwayomi 应用导出的非空备份往返、长时间导入的进度与主动取消、升级前一致性快照、Win10/Win11 干净环境及完整发行包验收仍待推进。已有取消测试只验证导出抛出取消异常时的保护，不声称实现了同步导出过程的即时取消。

本轮只新建 app-image；未生成新 MSI/EXE 安装器/portable ZIP，未变更用户资料或当前安装。
