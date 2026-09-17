# 严重使用问题审计

目标：检查当前 mihondesk 是否存在会严重影响使用的缺陷，优先处理数据丢失、
启动/运行崩溃、下载文件损坏或丢失、阅读无法退出、扩展失联等问题。
此记录跟踪审计和证据，不承诺软件不存在任何未知缺陷。

基线：`e0a1d9c88`，版本 0.2.15；工作分支 `codex/audit-critical-stability`。
真实数据在安装前做一致性备份并核对，升级后验证记录完整；破坏性/故障注入场景使用独立临时目录。

## 检查范围与验收证据

| 范围 | 必须验证的行为 | 当前证据/状态 |
| --- | --- | --- |
| 启动、退出、数据持久化 | 配置与数据库可恢复，多窗口/后台写入不丢配置，启动失败有可用反馈 | A5；回归通过，真实资料迁移核对通过 |
| 书库、导入和备份 | 备份恢复事务完整，删除和清理仅影响目标数据，导出失败保留旧备份 | 事务及原子导出回归，A1/A2/A3 修复 |
| 下载与离线阅读 | 并发/暂停/重试/取消一致，文件身份不会碰撞，损坏/中断可恢复 | A1–A4；下载到断网阅读回归通过 |
| 阅读器 | 进入/翻页/章节切换/退出可用，取消释放资源，阅读进度正确持久化 | 回归及安装 EXE 三次独立启动的续读/完成验证通过 |
| 扩展与网络 | 进程退出/重启不移除新宿主，超时/取消不挂死，严格保持来源权限边界 | 回归及安装 EXE 握手/重启、2 扩展 5 图源注册通过 |
| 更新、任务与桌面界面 | 任务失败不拖垮 UI，更新路径和本地文件操作安全，重要操作反馈可见 | 设置、调度、通知回归通过 |

六个 Windows 运行时模块基线命令已完成，日志 `build/critical-stability-audit/baseline.log`：
共 1024 项，1006 通过、1 失败、17 跳过。分模块：desktop-app 658，
desktop-library-data 115，reader-core 180，extension-sdk 23，extension-host 47，
desktop-webview-host 1。部分未改动模块使用 Gradle 的有效缓存，不能称全部重新执行。
XML 快照在 `build/critical-stability-audit/baseline/`。

唯一基线失败是 LibraryUpdateSchedulerTest：测试用手动时钟主动调用检查，同时默认
启动真实后台检查，后台会抢先完成任务，使手动调用返回 null。测试已明确关闭自动
启动；实际自动调度行为另由 Recovery 测试覆盖，未据此改动生产调度逻辑。
测试过程两次线程快照证明宿主测试持续推进，并非遇到同一处死锁。

## 已确认缺陷

### A1：章节名可使删除越过目标目录（严重）

安装版 0.2.15 的 `DownloadDiskProvider.sanitizeFileName` 保留 `..`。
在独立临时目录调用删除 `Manga/..`，确实删除了 `Other Manga/Chapter 1/keep.txt`。
该探针直接加载安装 JAR，结果为 `dot_chapter_deleted_neighbor=true`。
探针限制最终路径位于自身临时目录；没有操作真实用户下载。

代码已过滤控制字符、Windows 保留设备名及末尾点/空格，并在递归删除前确认
规范化路径位于下载根下至少三层。对应两个回归先失败，修复后通过。

### A2：不同章节 ID 的同名章节互相覆盖（严重）

安装版 0.2.15 探针先发布 chapterId=11，再发布同名 chapterId=12，返回相同路径，
删除了第一章的标记文件。旧版同样不能区分重名书籍和 Windows 文件名替换造成的碰撞。

新下载统一使用 sourceId/manga-{mangaId}/chapter-{chapterId}，临时目录也按 ID 隔离。
已保存的旧队列仅在身份无歧义时关联旧目录；旧数据库已登记的章节按登记位置查找，
无需整体搬动用户文件。下载删除优先匹配章节 ID，修正旧条件 OR 名称匹配会选错项的问题。
新回归同时下载同名章节、同名书籍及名称替换碰撞书籍，验证各图片内容、清空队列、
改名、指定 ID 删除、旧自定义目录恢复，以及不认领身份冲突的旧目录。
已被旧版实际覆盖的内容不能凭空恢复，需要重新下载。

### A3：更换下载根后，旧章节指向新目录（严重）

SQLDelight 原先仅在 local_manga_entry 保存一本书的根目录。新章节写入新位置时，
覆盖该根目录，导致旧章节的 ReaderChapterAsset 也解析到新位置。
独立数据库回归先复现路径指向错误，然后改用 local_chapter_storage 保存在线下载
每章的根目录。读取和前后章节查询优先使用该位置；改动一本书的根目录前保留旧章位置。
本地导入 sourceId=0 继续跟随原本的书库根目录规则。

新增 schema 2→3 迁移保存已有在线下载位置；迁移可重放且有故障注入回滚检查。
117 项数据层测试通过；17 项相关应用测试通过，包括 A1、A3、下载恢复、调度测试。
Spotless 和 git diff 检查通过。A2 的失败回归未包含在这 17 项中，不能称完整回归通过。
这是 A3 阶段的局部验证；最终安装状态见文末。

复现日志：`installed-storage-probe.log`、`storage-red.log`、`storage-root-red.log`；
后续检查：`path-safety-green.log`、`storage-root-regression.log`、`library-regression.log`，
均位于 `build/critical-stability-audit/`。最后的数据层报告修正了检查器返回值
`[ok]` 的测试断言；不是数据库损坏。

### A4：取消清理与立即重下竞争（高）

使用可控制的协程调度器和两个下载工人复现：旧下载尚未结束取消清理，另一个工人
已启动同章节的新下载，后续旧清理可能删除新临时文件。回归先失败后通过。
现按章节保存清理任务，新下载先等待其结束；领取任务与登记活动 Job、取消操作互斥，
避免取消发生在已领取但尚未登记 Job 的空隙。排队与进度更新也互斥，避免旧队列快照覆盖新状态。

### A5：页面旧配置覆盖其他设置和后台记录（高）

真实 Compose 设置界面回归复现：打开主题面板后，其他操作保存下载路径、筛选条件、
关闭桌面通知和备份时间，随后切换主题会将这些字段重置成面板打开时的旧值。
新增 updatePreferences，在同一锁内读取最新快照、修改本次字段并保存。
设置页、导航、窗口关闭、隐身模式、应用锁及备份/更新调度器已改用此入口。
回归通过，保留主题编辑以外的新配置和时间戳。应用锁凭据仍只存盐和散列。

## 检查记录

- 启动与持久化：DesktopProfileLock 在打开数据库前独占真实目录，关闭释放、不删除锁文件；
  Windows 二进程锁测试覆盖另一进程抢占。DesktopRuntime 先停止任务、关闭并刷新阅读会话，
  再关闭数据库；阻塞 close 禁止在 EDT 调用。配置丢失风险已按 A5 修复。
- 书库、导入、备份：AndroidBackupImporter 验证后进入完整事务，故障注入检查逐表回滚；
  Exporter 在事务内建立快照，Codec 每次独占临时文件后原子替换。LocalMangaImporter
  验证归属标记、限制导入目录，重复导入及异常清理有覆盖。A1/A2/A3 补上下载存储缺陷。
- 下载与离线：恢复、损坏页、失败页续传、旧队列、并发限额、公平调度、暂停/重试、
  A4 取消重下、数据库登记回滚及真正断网后通过 ReaderFactory 解码均已覆盖。
- 阅读器：ReaderScreen 加载期取消走 cancelWithoutFlush，就绪期 closeAndFlush；
  ReaderNavigationIntegration、章节切换、预取取消、缓存/内存释放、编解码进程取消、
  压缩包损坏/膨胀限制和多格式场景由现有回归覆盖。安装级还需运行同样的核心场景。
- 扩展/网络：WindowsExtensionProcessManager 仅当 process === proc 才由退出回调清理，
  不让旧进程回调覆盖新宿主；启动等待、异常退出、重启以及 IPC 超时路径有回归。
  DesktopNetworkHelper 保持按扩展来源的域名限制和调度许可释放，本次未放宽联网权限。
- 更新与桌面：后台调度的失败恢复、互斥、时间戳保存和通知服务均纳入回归；
  更新安装文件先写临时文件并校验（提供哈希时），下载失败不替换原目标。
  窗口导航、设置页面、阅读器对比度和下载动画使用现有 Compose 回归。

111 项聚焦应用回归：110 通过、1 真实图源探针跳过，日志 lifecycle-green.log。
完整六模块回归和安装验证已完成，数字与安装身份见下方。

跳过项的基线清单已核对：14 项应用测试要求真实扩展、指定安装目录、联网仓库、
系统计划任务或真实 WebView；extension-host 2 项要求提供实际扩展；reader-core 1 项
因系统未开放符号链接创建而跳过。不能以这些自动跳过项声称所有图源联网阅读已验证。
安装后已补做实际 EXE 宿主握手/重启、已装扩展注册以及安装 JAR 的存储、离线阅读和设置测试。

## 完成核对

本轮审计完成，5 类已确认缺陷均已修复、回归并安装验证；不等于对所有未知缺陷或所有图源可用性作保证。
日志与临时测试资料保存在 build/critical-stability-audit/（不随应用分发）。

完整六模块回归通过：1035 项，1018 通过、17 跳过、0 失败；8 分 46 秒。XML 已归档 final-regression/。


## 0.2.16 实际安装验收

- 代码提交：38660b6a3ef5b636fbafbf27bedc816215963de3，分支 codex/audit-critical-stability。
- packageMsi 和 verifyCleanDistribution 通过（3 分 20 秒）；MSI 版本、浏览器/许可及卸载钩子检查通过。
- 安装器退出码 0；安装前备份保存在 build/critical-stability-audit/profile-before-upgrade/。
  安装过程中 preferences.properties、downloads.json、database/library.db SHA-256 均未变。
- MSI SHA-256：`6BC3A3DF1F5B0C2D7931283723FBCFB791FC34D16D90D2C6B0F3849789281164`。
- 已安装核心 JAR SHA-256：`9595C514E682B6FCF29E5786820D7B72A870CED404ECBC0F27A503DA363DCB9C`，与本次应用映像一致。
  内嵌 version=0.2.16、revision=38660b6a3ef5b636fbafbf27bedc816215963de3、dirty=false。
- 对真实数据库的一致性副本先运行安装 EXE 的 --smoke-test，schema 2→3 成功。
  随后真实应用启动，真实数据库 integrity_check=ok、foreign_key_check 为空；8 张业务表的
  全部行内容摘要与升级前一致（6 本书、12 章节、4 历史、2 个已下载章节）。两个旧下载目录存在。
  preferences.properties 和 downloads.json 在启动后仍与备份字节一致。
- 38 项安装版回归全部通过、零跳过，日志 installed-regression.log，XML 在 installed-regression/。
  测试优先加载安装目录 JAR，并输出下载/数据库/通知/动画/阅读 UI 的类来源，覆盖本次存储修复、
  取消/恢复、设置并发保存、断网阅读、深色文字、通知与动画、宿主握手/重启。
  2 个实际已装扩展共 5 个图源注册并读取设置成功；这不是这些图源联网浏览全部通过的证明。
- 实际安装 EXE 对同一隔离资料目录连续执行 3 次阅读验证，分别得到 initial-open、reopen-continue、
  final-completion，均 SUCCEEDED。每次解码 38 块图像，覆盖 standalone/directory/CBZ/CBT/CB7/CBR/EPUB、
  6 种阅读模式和 GIF 帧；报告 installed-reader*.json。
- 新版主窗口已启动，进程路径为 C:/Users/18734/AppData/Local/mihondesk/mihondesk.exe，
  标题 mihondesk、Responding=true（installed-window.json）。升级前 Windows 自动化点击关闭报
  coordinate input geometry is unavailable，重新激活报 failed to activate captured window；
  确认无活跃下载并保留快照后，仅按精确安装路径停止该应用进程再升级。
  本轮原生 UI 证据为启动/响应检查；交互与像素验证来自加载安装 JAR 的 Compose 测试，未冒充人工点击验证。

公开 GitHub Release 未在本轮更新；修复提交、安装包和验证记录已保留在此旁支及本机。
