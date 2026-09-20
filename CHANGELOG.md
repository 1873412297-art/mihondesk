# mihondesk 更新日志

这里记录 mihondesk Windows 版的更新。安装包与完整发布说明见 [GitHub Releases](https://github.com/1873412297-art/mihondesk/releases)。继承自 Android 上游的历史日志保留在 [Mihon 更新日志](docs/upstream/MIHON_CHANGELOG.md)。

## [0.2.18](https://github.com/1873412297-art/mihondesk/releases/tag/v0.2.18)

- **长章节与真实窗口渲染**：支持 256+ 页超长章节流畅阅读与平滑翻页，修复可见请求与排队预取任务重叠时的优先级提权死锁；优化 Windows 下编解码进程异步终止与临时目录文件句柄清理，消除目录占用异常。
- **连续阅读滚动定位**：修复连续阅读模式（纵向/条漫）在首帧组合后外部跳页或跨章时未能正确滚动至锚点位置的问题，并隔离用户手势与视口回传。
- **应用内自动更新与便携版无损升级**：新增在应用内检查版本、验证发布清单与断点校验下载；便携版支持平滑退出并交接更新脚本无损替换应用，具备事务回滚与启动保护。
- **备份恢复与进度可控性**：备份恢复支持实时进度、随时取消并提供原子回滚保证；数据库版本迁移前自动建立隔离快照备份。
- **扩展管理与图源稳定性**：支持扩展下载和安装过程的随时取消控制；修复图源分页失败重试时已选状态与已加载条目保留；图源筛选器草稿独立隔离，仅在明确点击应用时生效。
- **Windows 桌面体验与动效**：重构动画节奏与帧率平滑性，引入 ANGLE 加速；下载通知频率收敛，移除弹窗打扰；强化离线章节下载状态与并发标识保护。

## [0.2.13](https://github.com/1873412297-art/mihondesk/releases/tag/v0.2.13)

- 修复恢复下载时图源隔离进程路由丢失，以及下载队列、本地文件和离线记录不同步的问题。
- 已完成章节的本地文件缺失时，可以重新下载；清理缓存后同步修正下载状态。
- 支持选择下载目录，并展示当前生效路径。
- 兼容 NHentai.xxx 图片 CDN 的受限跳转，保留按扩展隔离的网络访问规则。
- 修复深色与纯黑主题下阅读器标题、图标、页码及更新日历文字不可见的问题。
- 补齐阅读菜单、图片操作、章节筛选、下载进度、应用锁、扩展管理和更新日历的中文；简体、繁体、英文切换会同步更新界面。
- 统一扩展、翻译组、Cookie 与网站设置等用词，日期按应用选择的语言显示。图源提供的书名、章节名和翻译组名称保留原文。

## [Unreleased]
### Added
- Add `id:` prefix search to remaining trackers (AniList, Bangumi, Kitsu, MangaUpdates, Shikimori, and Hikka) ([@MajorTanya](https://github.com/MajorTanya)) ([#3776](https://github.com/mihonapp/mihon/pull/3776))
  - Allow `id:` to search for slugs on Kitsu ([@MajorTanya](https://github.com/MajorTanya)) ([#3792](https://github.com/mihonapp/mihon/pull/3792))
- Add support for using the user's chosen rating system for Kitsu ([@MajorTanya](https://github.com/MajorTanya)) ([#3818](https://github.com/mihonapp/mihon/pull/3818))
- Add support for the Year, Month, and Day fields in ComicInfo.xml files for chapter dating ([@MajorTanya](https://github.com/MajorTanya)) ([#3967](https://github.com/mihonapp/mihon/pull/3967))
- Add refresh buttons to trackers in Settings to update displayed usernames (all trackers) & rating systems (where supported) ([@MajorTanya](https://github.com/MajorTanya)) ([#3828](https://github.com/mihonapp/mihon/pull/3828))

### Improved
- Show updates and upcoming filter icon as active for categories ([@Secozzi](https://github.com/Secozzi)) ([#3772](https://github.com/mihonapp/mihon/pull/3772))
- Show scores in MangaUpdates search results (and authors for `id:` prefix searches) ([@MajorTanya](https://github.com/MajorTanya)) ([#3795](https://github.com/mihonapp/mihon/pull/3795))
- Remove whitespace from MAL and MB `id:` prefix search inputs before searching ([@MajorTanya](https://github.com/MajorTanya)) ([#3793](https://github.com/mihonapp/mihon/pull/3793))
- Show a helpful error message for expired AniList credentials ([@MajorTanya](https://github.com/MajorTanya)) ([#3888](https://github.com/mihonapp/mihon/pull/3888))

### Fixed
- Fixed app and extension update check running again on configuration change ([@AntsyLich](https://github.com/AntsyLich)) ([#3708](https://github.com/mihonapp/mihon/pull/3708))
- Fixed MangaBaka user start/finish dates drifting in negative offset timezones ([@MajorTanya](https://github.com/MajorTanya)) ([#3711](https://github.com/mihonapp/mihon/pull/3711))
- Fixed MangaBaka scores being wrong when score step size was set to > 1 ([@MajorTanya](https://github.com/MajorTanya)) ([#3740](https://github.com/mihonapp/mihon/pull/3740))
- Fixed default category and manga sometimes not getting their category set when restoring a backup ([@Secozzi](https://github.com/Secozzi)) ([#3891](https://github.com/mihonapp/mihon/pull/3891))
- Fixed AniList rate limit ([@MajorTanya](https://github.com/MajorTanya)) ([#3942](https://github.com/mihonapp/mihon/pull/3942))

## [0.2.10](https://github.com/1873412297-art/mihondesk/releases/tag/v0.2.10)

- 更换为全新的展开书页与书签图标，统一窗口、任务栏、桌面快捷方式和安装包。
- 更新 GitHub 横幅、项目介绍、贡献指南和反馈模板，补充品牌资源与导出说明。
- 将 Windows 版更新日志与保留的 Mihon 上游历史分开，方便查阅对应版本。
- 更新 Windows 构建产物路径，并限制上游网站通知工作流只在上游仓库运行。

## [0.2.9](https://github.com/1873412297-art/mihondesk/releases/tag/v0.2.9)

- 阅读器顶部新增“书籍详情”，保存进度后打开当前书籍详情。
- 支持查看未收藏的下载书籍，并在详情、阅读器和原入口之间正确返回。
- 鼠标停留在阅读器工具栏时保持显示，避免按钮自动隐藏。
- 移除默认内置的 MangaDex，发行版不预装在线图源或扩展仓库。

## [0.2.8](https://github.com/1873412297-art/mihondesk/releases/tag/v0.2.8)

- 下载页可直接阅读已经完成下载的章节。
- 使用已下载文件离线打开内容，并保留阅读进度。

## 更早版本

程序与安装包已统一命名为 mihondesk，并保持旧版本的数据升级兼容。更早版本的具体改动请查看[历史 Releases](https://github.com/1873412297-art/mihondesk/releases)。
