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

## 干净发行规则

发行包不预装图源或扩展仓库，也不包含账号或阅读数据。首次使用请自行添加仓库或安装扩展。

EXE、MSI 和便携 ZIP 都依赖 `verifyCleanDistribution`，打包前执行 `scripts/verify-release-clean.ps1`。检查会拒绝个人数据目录、已安装扩展包、偏好文件、Cookie 和数据库。用于验证的独立数据目录、日志与升级备份放在发行目录以外。

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

“关于”页面提供主动检查更新、版本说明和更新包下载。它查询 `1873412297-art/mihondesk` 最新正式 Release，只选择同版本的 Windows 安装包或 x64 便携包。下载必须具有官方 Release 的 `SHA256SUMS.txt` 唯一匹配条目，并通过文件大小及 SHA-256 校验才会保存到用户选择的位置；失败或取消不会替换已有目标文件。切换页面不会取消下载，主动取消会等待网络读取退出及临时文件清理，网络读取最长等待 30 秒。

下载完成可打开文件夹或发布页面；尚不自动运行安装器或便携更新器。安装版请关闭程序后运行安装包，便携版按下文使用更新器。没有匹配附件或有效校验清单时，不能从应用内下载更新。

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
