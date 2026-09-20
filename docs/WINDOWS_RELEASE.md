# mihondesk — Windows 发行与升级

mihondesk 是 AI 辅助开发的 Windows 漫画阅读器，基于 Mihon。当前发行版本由 `desktop-version.txt` 管理。

## 发行文件

| 文件 | 用途 |
| --- | --- |
| `mihondesk-<版本>.exe` | Windows 安装向导 |
| `mihondesk-<版本>.msi` | MSI 安装包 |
| `mihondesk-<版本>-windows-x64-portable.zip` | 便携版，解压后运行 `mihondesk/mihondesk.exe` |
| `mihon-build-info.properties` | 版本、源码提交和工作树状态 |
| `SHA256SUMS.txt` | 文件摘要 |

下载入口：[GitHub Releases](https://github.com/1873412297-art/mihondesk/releases/latest)。发行包内置 Java 17；网页辅助进程使用独立 Java 21/JCEF 运行时。

## 数据与升级兼容

- 安装版的默认程序目录为 `%LOCALAPPDATA%\mihondesk`。
- 安装版继续使用 `%APPDATA%\MihonW` 数据目录，以便从 0.2.4 及以前版本升级时保留书架、图源、设置和登录数据。
- Windows 安装升级识别码保持 `07E02BEA-9179-4E54-A1AF-CFC185C91398`。
- 凭据存储、文件关联 ProgID 与后台任务的内部标识保持兼容，程序显示名和新启动文件为 `mihondesk` / `mihondesk.exe`。
- 便携版数据位于启动文件同目录的 `data` 文件夹。升级时保留该文件夹。

## 当前限制与验证状态

mihondesk 的 Windows 发行产物（EXE、MSI、便携 ZIP）已实现功能可用，但以下事项尚未完成生产级验证，会在后续版本逐步收敛：

- **图源与扩展**：扩展安装和本地加载已验证，部分图源的完整阅读链（搜索→详情→章节→图片）仍在按矩阵实测；需要验证码或登录的站点可能无法自动通过。
- **Tracker**：11 个服务已有实现和本地契约测试，但生产账户的登录→绑定→进度写入→刷新→退出全流程尚未全部验证；新增服务的 OAuth 自动登录配置也未完成。
- **备份**：桌面备份格式与 Mihon 兼容。2026-09-20 已完成与真实 Android mihon 应用的双向往返验证（codec 两方向 + 模拟器应用恢复/再导出），六类字段无丢失；Suwayomi 方向互导仍待验证；下载图片、密钥、SyncYomi uid、Suwayomi 私有设置不随备份迁移。
- **安装/升级/卸载**：功能已实现，干净 Windows 10 22H2 / Windows 11 系统上的完整验收矩阵（安装、旧版升级、卸载、回滚）尚未全部完成；便携版升级使用两次目录重命名的事务保护，但非原子交换。
- **性能**：长阅读内存平台期、帧率 P95、冷启动 P95 仍在测量与优化中。

详细验证状态与证据索引见 [功能覆盖快照](superpowers/evidence/suwayomi-feature-coverage.md)。

## 干净发行规则

发行包不预装图源或扩展仓库，也不包含账号或阅读数据。首次使用请自行添加仓库或安装扩展。

EXE、MSI 和便携 ZIP 都依赖 `verifyCleanDistribution`，打包前执行 `scripts/verify-release-clean.ps1`。检查会拒绝个人数据目录、已安装扩展包、偏好文件、Cookie 和数据库。用于验证的独立数据目录、日志与升级备份放在发行目录以外。

## 发版检查清单（每次发版强制执行，缺一不发）

以下各项必须使用**当版构建**留证，禁止沿用历史版本的验证结果（新包新身份原则）：

- [ ] **2A MSI 门禁**：`scripts/verify-msi-package.ps1` 全断言通过（WiX 升级码、钩子、组件清单）；在 VM 快照上完成 N-1→N 升级与卸载两格实测，日志随版本归档。
- [ ] **2B 便携交接失败注入**：更新进程被杀、目标目录被占用、交接中断电、回滚脚本缺失四类故障注入下事务回滚与启动保护有效；`scripts/tests/portable-updater.tests.ps1` 全绿。
- [ ] **2C 应用内更新真实通道**：对真实已发布 release 资产完成 检查→下载→清单/SHA 校验→交接→启动 端到端一次；断点续传与校验失败路径各一次。
- [ ] **1D 快速回归**：干净 VM 上干净安装 + 启动 + 备份导出冒烟通过。
- [ ] **1F CI 绿**：同一提交上 Android 单测 + assembleDebug + 桌面关键测试全绿（CI run 链接附 release 说明）。

历史事故区（`msi-upgrade-rollback`、便携交接、应用内更新反复出现 hotfix 的版本）必须在 release 说明中引用当版本项证据链接。

## 构建与验证

在已配置本仓库构建环境的 Windows PowerShell 中：

```powershell
.\gradlew.bat :desktop-app:assembleWindowsRelease
```

统一产物位于 `desktop-app/build/releases/<版本>/`，其中 `app-image/mihondesk` 为应用目录。发布前核对构建身份、包内容、安装升级和 SHA-256，并运行相关验证：

```powershell
.\scripts\verify-release-clean.ps1 -ImagePath '<应用目录>'
.\scripts\verify-msi-package.ps1 -MsiPath '<MSI 路径>'
.\scripts\verify-desktop-clean-machine.ps1 -PortableZip '<便携 ZIP 路径>' -SkipBuild
```

最后一个脚本验证独立目录中的便携运行、版本、帮助和备份导出。Windows 10 / 11 的全新系统验收需另外执行。

推送代码分支不会发布 Windows 新版本。仓库保留的上游 Release 工作流只为上游 Android 仓库运行。Windows 发布时还需更新首页与更新日志，将验证后的 EXE、MSI、便携 ZIP、版本与构建信息、SHA-256 清单上传到对应版本的 GitHub Release，核对远程附件摘要后设为最新正式版。清单应只列出随 Release 上传的文件。

## 更新

“关于”页面提供主动检查更新、版本说明和更新包下载。它查询 `1873412297-art/mihondesk` 最新正式 Release，只选择同版本的 Windows 安装包或 x64 便携包。下载必须具有官方 Release 的 `SHA256SUMS.txt` 唯一匹配条目，并通过文件大小及 SHA-256 校验才会保存到用户选择的位置；下载或校验失败、在保存前取消，均不会替换已有目标文件。Windows 临时占用目标文件时会短暂重试，持续占用则保留原文件并提示失败。切换页面不会取消下载，主动取消会等待网络读取退出及临时文件清理，网络读取最长等待 30 秒。

默认 `data` 目录的便携版下载完成后可选择“退出并更新”：应用使用随自身打包的更新器，先校验归档和试启动新程序，收到准备就绪信号后才正常退出。更新器核对原进程身份、等待安全退出并复制配置；成功后启动新程序，失败且旧目录可用时重新打开旧程序。“关于”显示上次更新结果与日志。准备阶段可取消；程序目录上级的 `.mihon-handoff-<标识>` 保留此次日志和交接记录。

安装版、开发环境或使用外部配置目录的便携版暂不显示自动更新按钮，仍可打开下载位置和发布页面。安装版请关闭程序后运行安装包，便携版也可按下文使用手动更新器。没有匹配附件或有效校验清单时，不能从应用内下载更新。

便携包包含 `mihondesk-updater.ps1`。新更新器会锁住便携版配置、校验暂存 ZIP 的 SHA-256、检查归档路径并验证新程序，再复制完整 `data` 目录。它保留完整旧程序和旧数据，并在新程序成功打开复制后的数据库后确认更新。后续验证失败则恢复旧目录，同时保留失败的新目录供检查。不能把带个人数据、链接或不安全路径的 ZIP 用作更新包。

将**新包中的更新脚本复制到程序目录外**，关闭程序及其后台任务，按 Release 的摘要清单验证下载包，再运行：

```powershell
& '<程序目录外的脚本路径>\mihondesk-updater.ps1' -ZipPath '<新便携 ZIP 的绝对路径>' -TargetDir '<现有便携程序目录>' -ExpectedSha256 '<清单中的 64 位 SHA-256>'
```

`-NoRestart` 可禁止完成后自动启动。`-CallerPid` 仅接受该程序目录中的进程，会等待它退出；不指定时也必须关闭使用该配置的程序。旧目录中的启动文件可以仍叫 `MihonW.exe`，新包默认使用 `mihondesk/mihondesk.exe`。`-ExecutableName` 指定的是新包的启动文件名称。新包必须支持便携更新启动保护；过旧的包会在替换前被拒绝。

替换使用同一磁盘卷上的两次目录重命名，并非两个目录名称的单次原子交换。程序上级目录中的 `.mihon-update-<标识>.json` 记录未完成的事务；新程序遇到更新保护标记时拒绝普通界面/后台启动，只有更新器的版本验证进程可以继续。更新器异常退出后，请先确认更新器、验证进程和应用都已退出，保留恢复记录，再用同一个脚本恢复：

```powershell
& '<程序目录外的脚本路径>\mihondesk-updater.ps1' -TargetDir '<原便携程序目录>' -Recover
```

即使原程序目录在中断时暂时不存在，也使用原路径。恢复未确认完成的替换时会还原旧目录；已记录验证完成的事务只完成清理。重新执行正常更新命令也会先处理未完成的事务。`.mihon-rollback-<标识>` 保留成功更新前的程序与数据，`.mihon-failed-<标识>` 保留失败的新目录；确认数据无误前不要删除它们，也不要同时运行其中的副本。更新与恢复需要足够空间保存旧版、新版、完整数据副本及暂存 ZIP。

配置中存在目录联接/符号链接时，更新器会拒绝自动复制，避免沿链接读写其他位置。这一流程只保护程序目录内的便携 `data`，不复制外部 `--data-dir`、安装版数据或 MSI 安装状态；数据库自身仍有独立的[迁移前快照](database-recovery.md)。它不承诺所有文件系统或故障硬件下的断电持久性。

后台任务卸载清理仅删除指向当前安装程序的任务；升级后启用的后台任务由程序重新绑定到新的启动文件。
