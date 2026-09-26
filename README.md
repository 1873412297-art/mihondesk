<div align="center">

<img src=".github/assets/banner.png" alt="mihondesk — Windows 漫画阅读器，本地阅读、书架管理与离线下载" width="100%" />

# mihondesk

**从书架到每一页，在 Windows 上阅读漫画。**

AI 辅助开发的开源项目 · 基于 Mihon · Kotlin / Compose Desktop

![AI 辅助开发项目](https://img.shields.io/badge/AI-assisted%20development-1739B8)

[![最新版本](https://img.shields.io/github/v/release/1873412297-art/mihondesk?label=Release&color=0057d9)](https://github.com/1873412297-art/mihondesk/releases/latest)
[![下载量](https://img.shields.io/github/downloads/1873412297-art/mihondesk/total?label=Downloads&color=0057d9)](https://github.com/1873412297-art/mihondesk/releases)
![Windows x64](https://img.shields.io/badge/Windows-x64-0078d4)
[![Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-64748b)](LICENSE)

**[下载 Windows 版](https://github.com/1873412297-art/mihondesk/releases/latest)** · [更新日志](CHANGELOG.md) · [使用说明](docs/WINDOWS_RELEASE.md) · [反馈问题](https://github.com/1873412297-art/mihondesk/issues/new/choose)

</div>

**AI 项目说明**：本仓库的 Windows 移植、功能迭代、问题修复和文档维护使用 AI 编程助手协助完成。上游 Mihon、Suwayomi 及其他第三方项目的贡献与许可归属见文末。

## 下载与安装

当前版本：**0.2.21**。面向 **Windows 10 22H2 / Windows 11，x64**，发行包已内置运行环境，无需另装 Java。

程序、安装包和快捷方式现已统一命名为 **mihondesk**。安装版继续使用原有数据目录，支持从 MihonW 升级。

| 下载 | 适用方式 |
| --- | --- |
| **[EXE 安装包](https://github.com/1873412297-art/mihondesk/releases/download/v0.2.21/mihondesk-0.2.21.exe)** | 推荐使用，下载后按向导安装 |
| [MSI 安装包](https://github.com/1873412297-art/mihondesk/releases/download/v0.2.21/mihondesk-0.2.21.msi) | 需要 MSI 安装方式时使用 |
| [便携 ZIP](https://github.com/1873412297-art/mihondesk/releases/download/v0.2.21/mihondesk-0.2.21-windows-x64-portable.zip) | 完整解压到可写目录，运行其中的 `mihondesk.exe` |
| [SHA-256 校验文件](https://github.com/1873412297-art/mihondesk/releases/download/v0.2.21/SHA256SUMS.txt) | 校验下载文件的完整性 |

**首次使用**：发行包不预装在线图源或扩展仓库，请自行添加仓库或安装扩展。本地文件阅读无需安装图源。升级会保留已有书架、设置和阅读数据。

安装版默认将数据保存在 `%APPDATA%\MihonW`；便携版保存在 `mihondesk.exe` 同目录下的 `data` 文件夹。迁移便携版时请一并保留该文件夹。

## 可以做什么

| 功能 | 说明 |
| --- | --- |
| 本地阅读 | 导入图片目录，以及 ZIP / CBZ、RAR / CBR、7z / CB7、TAR 和图片型 EPUB 等文件 |
| 桌面阅读器 | 单页、双页、纵向和条漫模式，阅读方向切换、缩放、键盘与鼠标操作；阅读中直达书籍详情 |
| 书架管理 | 分类、搜索、阅读进度和历史；在线漫画通过明确的加入书架操作收藏 |
| 图源浏览 | 安装扩展，浏览、搜索漫画并查看章节；支持部分 Mihon / Tachiyomi 扩展的转换与兼容运行 |
| 离线与更新 | 章节下载、下载队列恢复、从下载页直接阅读已完成内容，以及书库章节更新 |
| 备份与恢复 | 导入、导出 Mihon 兼容备份；具体字段支持范围见下方兼容说明 |
| 手机书架同步 | 桌面端开启内置同步服务器后，手机扫码 / 局域网发现 / 配对码完成配对，收藏、已读、分类与阅读进度双向自动同步（0.2.20 起） |

## 开始使用

1. **安装或解压程序**，运行 `mihondesk.exe`。
2. **阅读本地文件**：使用本地导入入口选择漫画文件或图片目录。
3. **阅读在线内容**：在浏览页安装扩展包，再选择图源。安装入口支持 `.mext`，也可尝试转换受支持的扩展 `.apk`。
4. **保留阅读数据**：使用备份功能定期导出。备份不包含已下载的漫画图片。

## 0.2.21 更新

- **图源修复**：扩展不支持「热门/最新」时显示「请使用搜索」的引导页而非报错；修复 vomic 等扩展因缺少 Android `http.agent` 系统属性导致的裸 NullPointerException；哔哩漫画等防盗链图源封面恢复显示（封面请求自动附带 Referer）；扩展包文件丢失时启动自动清理脏记录并提示重装。
- **性能优化**：偏好与扩展列表缓存（网络请求/下载通知不再全量读盘）、下载进度合并落盘、启动不再同步扫描下载文件、IPC 每请求只解析一次 JSON、封面加载去除双重重试并恢复懒列表重组跳过、书架滚动零磁盘探测。
- **设置页流畅度**：进入「备份与还原」不再卡顿（网卡枚举与二维码生成移出 UI 线程）。

详见[本版发布说明](https://github.com/1873412297-art/mihondesk/releases/tag/v0.2.21)和[更新日志](CHANGELOG.md)。

## 0.2.20 更新

- **手机 ↔ 桌面书架同步（内置服务器）**：桌面端设置里一键开启同步服务器，手机通过**扫码**（桌面显示配对二维码）、**局域网自动发现**（mDNS，桌面开启"允许免输入配对"后点一下设备即配对）或**手动配对码**三种方式完成配对；之后收藏、已读、分类、阅读进度在两端自动双向同步，无需任何第三方同步工具。
- **关闭窗口后在后台运行**（新开关，默认关）：窗口关闭后隐藏到系统托盘，同步服务器保持运行，托盘菜单可显示或彻底退出；应用更新重启不受影响。
- **同步 IP 选择记忆**：多网卡/VPN 机器上记住上次选定的同步 IP，重启后自动恢复，失效时自动回退。
- 扫码界面重做：扫描框 + 动画扫描线 + 无效码提示 + 手动输入兜底；外部扫码器扫二维码可经 deep-link 直达配对确认。

详见[本版发布说明](https://github.com/1873412297-art/mihondesk/releases/tag/v0.2.20)和[更新日志](CHANGELOG.md)。

## 0.2.19 更新

- **网络代理统一管辖**：图源请求、Tracker 同步、扩展仓库索引、扩展包下载与封面加载现在统一遵循「设置 → 高级与诊断 → 网络代理」（系统代理 / 直连 / HTTP / SOCKS）。此前各处客户端各自走系统代理，在 Clash 类代理下会静默失败；现在遇到加载异常可直接切换代理模式定位。
- **Tracker 生产可用性**：修复 okhttp 默认 User-Agent 被站点反爬拦截导致登录失败，以及请求体被自动追加字符集导致写入被拒（HTTP 415）；修复跟踪编辑对话框无法打开、单本（非正章节号）阅读进度永不同步。Bangumi 与 AniList 已完成生产全链验证。
- **扩展管理**：修复扩展仓库条目无法移除、新增仓库因单个仓库失败而整体报错的问题，历史遗留条目会自动迁移。
- **图源网页验证崩溃修复**：补全打包运行时缺失的 JDK 模块，修复「打开网页」必定崩溃；打包流程新增运行时模块校验，防止同类问题再次进入发行包。
- **封面加载稳定性**：为封面请求加入并发限制与退避重试，修复书架首屏并发过高导致的封面大面积空白；失效封面（404/410）自动重取，并提供一键批量修复入口。
- **缺失图源引导**：导入备份后若书籍所属图源未安装，详情页显示提示横幅、导入完成弹出汇总对话框，可一键安装对应扩展。
- **界面与可读性**：主导航补全文字标签，设置与进度记录页分区卡片化，书架与详情页操作区主次分明；Cloudflare 拦截等网络错误改为含处置建议的本地化提示；修复阅读器错误页鼠标光标隐藏后无法恢复、导致重试按钮点不到的问题。

详见[本版发布说明](https://github.com/1873412297-art/mihondesk/releases/tag/v0.2.19)和[更新日志](CHANGELOG.md)。

## 当前限制

mihondesk 仍在持续开发，桌面端尚未覆盖 Mihon 的全部功能。当前版本号为独立序列（`0.2.x`），与上游 mihon `0.20.x` 无对应关系。以下限制基于[功能覆盖快照](docs/superpowers/evidence/suwayomi-feature-coverage.md)与原设计完成标准，会随验证进展持续收敛：

- **扩展与图源兼容性**：扩展可安装并加载，不代表该图源的浏览→详情→章节→图片全链可用。部分站点需要验证码或登录。2026-09-20 端到端矩阵实测：MangaDex / NHentai 全链通过，BiliManga 除搜索外全链通过（搜索需登录），MangaFire 被 Cloudflare 封锁（需人工过验证），MangaPlus 因地区封锁不可用（其 API 对本网络返回错误信封；兼容层 Filter.Select 缺陷当日已修复，授权地区网络下有望通过）。详见[扩展兼容矩阵](docs/suwayomi-extension-compatibility.md)。
- **Tracker 账户**：11 个服务均有实现与本地契约测试。2026-09-22 生产实测：**Bangumi** 与 **AniList** 已完成登录→搜索绑定→进度写入→重进保持→解绑全链验证；其余服务（MyAnimeList、Kitsu、Shikimori、MangaUpdates、Hikka、MangaBaka 及自托管 Komga / Kavita / Suwayomi）尚待账户或实例就绪后继续联调，新增服务的 OAuth 自动登录配置也未完成。
- **备份互通**：桌面端可导入/导出 Mihon 兼容备份。2026-09-20 已完成双向往返验证：真实 Android 编码器备份→桌面导入（字段级对照）、桌面导出→Android 解码器契约测试、桌面导出→Android 应用恢复→应用再导出→桌面再导入，六类字段无丢失；下载图片、密钥、SyncYomi uid、Suwayomi 私有设置不随备份迁移。详见[备份往返证据](docs/superpowers/evidence/2026-09-20-backup-roundtrip.md)。
- **安装与升级**：MSI / EXE / 便携三种形态已在干净 Windows 11 虚拟机上完成实测（14/14 验收格）：三种形态的干净安装、0.2.10→0.2.18 向导覆盖升级、卸载（保留数据 / 删除数据）与损坏回滚。其中 EXE 安装器的 `/S` 静默安装不受支持，请在图形向导中安装。三种发行形态均**不注册控制面板卸载项**，卸载请使用原安装包或便携目录删除。Win10 22H2 干净机矩阵因宿主为异构大小核平台、Windows 10 客户机内核无法完成初始化而未完成（Win11 正常），详见[干净机矩阵证据](docs/superpowers/evidence/2026-09-21-clean-machine-matrix.md)。
- **性能与稳定性**：六种阅读模式、万部库搜索等已可用。2026-09-20 四项 SLO 实测：冷启动 P95 1.24 s（目标 ≤5 s）、空闲内存中位 463 MiB（目标 <500 MB）、30 分钟阅读内存两次 1800 秒 soak 均有界、帧时间 P95 10.22 ms（真实阅读窗口负载，目标 ≤33 ms）。详见[性能证据](docs/superpowers/evidence/2026-09-20-performance-slo.md)。
- **故障恢复**：数据库迁移前快照、备份恢复事务回滚等已实现；安装过程中断已用断电注入方式实测——断电后重启系统与应用数据均完好，可重新安装。满盘、拔盘等其余场景尚未实测。
- **Android 回归**：本仓库保留 Android 模块用于格式兼容，但共享改动后的 Android 全量单测 + APK 构建尚未作为桌面端发版的强制回归门槛。

**旧版本升级**：0.2.4 及更早版本的内置更新检查指向旧仓库，首次升级请从[本仓库 Releases](https://github.com/1873412297-art/mihondesk/releases/latest)手动下载。

遇到图源、tracker 或备份问题请在 Issues 反馈，附上 mihondesk 版本、Windows 版本、扩展名称/版本、复现步骤和错误信息。

## 反馈与开发

遇到问题请在[本仓库 Issues](https://github.com/1873412297-art/mihondesk/issues)反馈，附上 mihondesk 版本、Windows 版本、复现步骤和错误信息。图源问题请同时提供扩展名称及版本。

欢迎提交改进，参见[贡献指南](CONTRIBUTING.md)。开发资料：

- [扩展兼容矩阵](docs/suwayomi-extension-compatibility.md)
- [功能覆盖与验证范围](docs/superpowers/evidence/suwayomi-feature-coverage.md)
- [Windows 发行与升级说明](docs/WINDOWS_RELEASE.md)
- [开发环境与构建](docs/windows-development.md)
- [图标与品牌资源](docs/BRANDING.md)

## 上游与许可

本项目是 [Mihon](https://github.com/mihonapp/mihon) 的独立 Windows 移植项目，基于其阅读与备份相关实现，并参考 [Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server) 的扩展兼容与后台服务实现。感谢上游项目及所有贡献者。Android 原版请访问 [Mihon 官网](https://mihon.app)。

项目采用 [Apache License 2.0](LICENSE)。桌面端第三方组件及许可见 [THIRD-PARTY-DESKTOP.txt](THIRD-PARTY-DESKTOP.txt)。程序本身不提供或托管漫画内容。

Copyright © 2015 Javier Tomás

Copyright © 2024 Mihon Open Source Project
