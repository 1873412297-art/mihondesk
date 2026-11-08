package mihon.desktop.i18n

import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderClickAction
import mihon.desktop.reader.ReaderColorFilter
import mihon.desktop.reader.ReaderPageTransition
import mihon.desktop.reader.ReaderWheelBehavior
import mihon.desktop.track.TrackStatus
import mihon.desktop.ui.browse.SourceListingMode
import mihon.reader.model.ReaderErrorCode
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.session.ReaderSessionError
import mihon.reader.source.ReaderFailure

internal class SimplifiedChineseAdvancedStrings(private val core: CoreStrings) : AdvancedStrings, CoreStrings by core {
    // Tracker & Tracking
    override fun trackerConnectTitle(name: String) = "连接到 $name"
    override fun trackerAuthTitle(name: String) = "在 $name 认证"
    override fun trackerLoginTitle(name: String) = "登录到 $name"
    override val trackerServerUrl = "服务器地址 (例如 https://komga.example.com)"
    override val trackerUsername = "用户名"
    override val trackerPasswordOrApiKey = "密码或 API 密钥"
    override fun trackerAuthUrl(url: String) = "授权网址: $url"
    override val trackerToken = "API 令牌 / 访问令牌"
    override val trackerAccountName = "账户名称 (选填)"
    override val trackerPassword = "密码"
    override val trackerConnect = "连接"
    override val trackerLogin = "登录"
    override val trackerLoginFailed = "登录失败，请检查账户、凭证和服务器地址后重试。"
    override val trackerServerAuthHelp = "使用 API 密钥时留空用户名；使用密码时填写用户名和密码。"
    override val trackingRequestFailed = "追踪请求失败，请检查网络和登录状态后重试。"
    override fun trackingTitle(mangaTitle: String) = "进度记录 - $mangaTitle"
    override val trackingNotTracking = "未记录"
    override val trackingNotLoggedIn = "未登录"
    override val trackingTrack = "记录"
    override val trackingEdit = "编辑"
    override val trackingRemove = "解绑"
    override fun trackingSearchTitle(name: String) = "搜索 $name"
    override val trackingSearch = "搜索"
    override fun trackingTotalChapters(count: Long) = "$count 话"
    override fun trackingEditTitle(title: String) = "编辑记录 - $title"
    override val trackingChaptersRead = "已读话数"
    override val trackingScore = "评分"
    override fun trackStatusLabel(status: TrackStatus) = when (status) {
        TrackStatus.READING -> "阅读中"
        TrackStatus.COMPLETED -> "已读完"
        TrackStatus.ON_HOLD -> "搁置"
        TrackStatus.DROPPED -> "弃坑"
        TrackStatus.PLAN_TO_READ -> "计划阅读"
        TrackStatus.REREADING -> "重读中"
    }

    // Reader Chrome, Settings & Messages
    override val readerFullscreen = "全屏"
    override val readerBorderless = "无边框"
    override val readerShortcutsTitle = "快捷键速查"
    override val readerShortcutsHelp = "快捷键"
    override fun readerCropToggle(active: Boolean) = if (active) "裁切白边: 开" else "裁切白边: 关"
    override fun readerCoverOffsetToggle(active: Boolean) = if (active) "封面偏移: 开" else "封面偏移: 关"
    override val readerModeDualLtr = "双页拼合（从左到右）"
    override val readerModeDualRtl = "双页拼合（从右到左）"
    override val readerModeVertical = "连续垂直"
    override fun readerModeLabel(mode: ReadingMode) = when (mode) {
        ReadingMode.SINGLE_LTR -> readerModeSingleLtr
        ReadingMode.SINGLE_RTL -> readerModeSingleRtl
        ReadingMode.DUAL_LTR -> readerModeDualLtr
        ReadingMode.DUAL_RTL -> readerModeDualRtl
        ReadingMode.VERTICAL -> readerModeVertical
        ReadingMode.WEBTOON -> readerModeWebtoon
    }
    override fun readerScaleLabel(scale: ScaleMode) = when (scale) {
        ScaleMode.ORIGINAL -> readerScaleOriginal
        ScaleMode.FIT_WIDTH -> readerScaleFitWidth
        ScaleMode.FIT_HEIGHT -> readerScaleFitHeight
    }
    override fun readerFilterLabel(filter: ReaderColorFilter) = when (filter) {
        ReaderColorFilter.NONE -> readerFilterNone
        ReaderColorFilter.INVERT -> readerFilterInvert
        ReaderColorFilter.GRAYSCALE -> readerFilterGrayscale
        ReaderColorFilter.INVERT_GRAYSCALE -> readerFilterInvertGrayscale
        ReaderColorFilter.SEPIA -> readerFilterSepia
        ReaderColorFilter.NIGHT -> readerFilterNight
        ReaderColorFilter.CUSTOM -> readerFilterCustom
    }
    override fun readerBackgroundLabel(bg: ReaderBackgroundColor) = when (bg) {
        ReaderBackgroundColor.DARK_GRAY -> readerBgDarkGray
        ReaderBackgroundColor.BLACK -> readerBgBlack
        ReaderBackgroundColor.WHITE -> readerBgWhite
        ReaderBackgroundColor.WARM_CREAM -> readerBgWarmCream
    }
    override val readerPageTransitions = "翻页过渡"
    override fun readerPageTransitionLabel(transition: ReaderPageTransition) = when (transition) {
        ReaderPageTransition.NONE -> "无"
        ReaderPageTransition.FADE -> "淡入淡出"
        ReaderPageTransition.SLIDE -> "滑动"
    }
    override val readerDualPageSplit = "跨页拆分"
    override fun readerDualPageSplitLabel(split: mihon.reader.layout.DualPageSplit) = when (split) {
        mihon.reader.layout.DualPageSplit.NEVER -> "从不"
        mihon.reader.layout.DualPageSplit.ALWAYS -> "总是"
        mihon.reader.layout.DualPageSplit.WIDE -> "仅宽图"
    }
    override val readerDualPageRotateToFit = "旋转宽图以适应"
    override val readerSettingsDialogTitle = "阅读器设置"
    override val readerSettingsTabReading = "阅读模式"
    override val readerSettingsTabGeneral = "常规"
    override val readerSettingsTabFilter = "色彩滤镜"
    override val readerChapterTransitions = "章节切换"
    override val readerAlwaysShowChapterTransition = "始终显示章节切换页"
    override val readerSkipReadChapters = "跳过已读章节"
    override val readerSkipFilteredChapters = "跳过被筛选章节"
    override val readerSkipDuplicateChapters = "跳过重复章节"
    override val readerClickRegions = "点击翻页区域"
    override val readerRegionLeft = "左侧"
    override val readerRegionCenter = "中间"
    override val readerRegionRight = "右侧"
    override fun readerActionLabel(action: ReaderClickAction) = when (action) {
        ReaderClickAction.PREVIOUS -> "上一页"
        ReaderClickAction.NEXT -> "下一页"
        ReaderClickAction.TOGGLE_CHROME -> "切换菜单"
        ReaderClickAction.NONE -> "无操作"
    }
    override fun readerLeftBoundary(percent: Int) = "左侧边界 $percent%"
    override fun readerCenterBoundary(percent: Int) = "中间边界 $percent%"
    override fun readerWheelLabel(behavior: ReaderWheelBehavior) = when (behavior) {
        ReaderWheelBehavior.SCROLL -> "滚轮: 滚动页面"
        ReaderWheelBehavior.PAGE_NAVIGATION -> "滚轮: 翻页"
    }
    override val readerReserveCover = "双页模式下保留封面独立单页"
    override val readerCropBordersPaged = "智能裁切白边 (单页/双页)"
    override val readerWebtoonLayout = "条漫排版"
    override val readerWebtoonWidthFull = "最大宽度: 全屏"
    override fun readerWebtoonWidthDp(width: Int) = "最大宽度: ${width}dp"
    override fun readerWebtoonSidePaddingPercent(padding: Int) = "侧边距: $padding%"
    override val readerLoadingChapter = "正在加载章节…"
    override val readerClosed = "阅读器已关闭"
    override fun readerErrorMessage(error: ReaderSessionError): String = when (error.cause) {
        is ReaderFailure.PageNotFound -> "无法找到此页面，可能已被移动或删除。"
        is ReaderFailure.UnsupportedFormat -> "不支持此章节格式。"
        is ReaderFailure.RemoteImage -> "页面下载失败。\n${error.cause?.cause?.message ?: error.cause?.message.orEmpty()}"
        is ReaderFailure.EncryptedContainer -> "不支持加密的章节压缩包。"
        is ReaderFailure.UnsafePath,
        is ReaderFailure.ResourceChanged,
        -> "本地章节已不可用，请重新导入或检查文件位置。"
        is ReaderFailure.CorruptContainer,
        is ReaderFailure.CorruptImage,
        -> "页面文件损坏或无法读取。"
        is ReaderFailure.UnsupportedImage,
        is ReaderFailure.RegionUnavailable,
        -> "不支持此图片格式。"
        is ReaderFailure.LimitExceeded,
        is ReaderFailure.TooManyEntries,
        -> "章节内容超出安全阅读限制。"
        is ReaderFailure.EmptyChapter -> "此章节不包含任何可读取的页面。"
        else -> when (error.code) {
            ReaderErrorCode.EMPTY_CHAPTER -> "此章节不包含任何可读取的页面。"
            ReaderErrorCode.SOURCE_UNAVAILABLE -> "图源或本地章节不可用，请定位或重新导入。"
            ReaderErrorCode.PAGE_NOT_FOUND -> "未找到此页面。"
            ReaderErrorCode.PAGE_DECODE_FAILED -> "页面解码失败。"
            ReaderErrorCode.MEMORY_LIMIT_REACHED -> "阅读器已达到内存占用上限。"
            ReaderErrorCode.INVALID_PROGRESS -> "保存的阅读进度无效。"
        }
    }

    // Browse, Sources, Extensions, Migration, Filters, Global Search
    override fun browseSourceLanguage(lang: String) = "图源语言: " + lang.uppercase()
    override val browseSourcePopular = "热门"
    override val browseSourceLatest = "最新"
    override val browseSearchTitlesPlaceholder = "搜索漫画标题..."
    override val browseSearchButton = "搜索"
    override fun browseFiltersButton(count: Int) = if (count > 0) "筛选 ($count)" else "筛选"
    override val browseNoMangaFound = "未找到任何漫画"
    override fun browseSourceUnsupportedOperation(mode: SourceListingMode) = when (mode) {
        SourceListingMode.Popular -> "该图源不支持浏览，请使用搜索"
        SourceListingMode.Latest -> "该图源不支持最新，请使用搜索"
        SourceListingMode.Search -> "该图源不支持搜索"
    }
    override val browseInLibraryBadge = "已在书架"
    override val browsePrevPage = "上一页"
    override fun browsePageNumber(page: Int) = "第 $page 页"
    override val browseNextPage = "下一页"
    override fun browseInstallExtensionTitle(name: String) = "安装扩展: $name"
    override fun browsePackageLabel(pkg: String) = "包名: $pkg"
    override fun browseVersionLabel(version: String) = "版本: $version"
    override fun browseLanguageLabel(lang: String) = "语言: $lang"
    override val browseNetworkPermissionNotice = "安装扩展前请确认其网络声明权限。"
    override val browseTrustAndInstall = "信任并安装"
    override val browseAddRepository = "添加"
    override val browseConfiguredRepositories = "已配置的扩展仓库:"
    override val browseRemoveRepository = "移除"
    override val browseSourceBrowse = "浏览"
    override val browsePin = "☆ 固定"
    override val browseMoreActions = "更多操作"
    override val browsePinnedSection = "已固定"
    override val browseUnpin = "★ 取消固定"
    override val browseUpdate = "更新"
    override val browseDisable = "停用"
    override val browseEnable = "启用"
    override fun onlineDetailSource(name: String, lang: String) = "图源: $name (${lang.uppercase()})"
    override fun onlineDetailAuthor(author: String) = "作者: $author"
    override fun onlineDetailArtist(artist: String) = "画师: $artist"
    override fun onlineDetailGenres(genres: String) = "标签: $genres"
    override fun onlineDetailChapters(count: Int) = "章节 ($count)"
    override fun onlineDetailScanlator(scanlator: String) = "翻译组/发布方: $scanlator"
    override val migrateSelectSourceHeader = "选择要迁出的图源："
    override val migrateNoMangaInLibrary = "书架中暂无来自在线图源的漫画。"
    override fun migrateMangaCount(count: Int) = "书架中有 $count 部漫画"
    override val migrateViewManga = "查看漫画"
    override val migrateBackToSources = "返回图源列表"
    override val migrateSelectMangaHeader = "选择要迁移到其他图源的漫画"
    override val migrateAction = "迁移"
    override fun migrateDialogTitle(title: String) = "迁移: $title"
    override val migrateDialogSubtitle = "在目标图源中搜索对应作品，以完整转移阅读进度、书签和分类。"
    override val migrateNoOtherSources = "没有可用于迁移的其他图源。"
    override val migrateTargetSourceLabel = "目标图源:"
    override val migrateSearchPlaceholder = "搜索标题..."
    override val migrateSelectedBadge = "✓ 已选择"
    override val migrateConfirm = "确认迁移"
    override val migrateAllButton = "全部迁移"
    override val migrateAllDialogTitle = "批量迁移漫画"
    override fun migrateAllDialogSubtitle(count: Int, sourceName: String) =
        "在目标图源中自动匹配并迁移来自「$sourceName」的全部 $count 部漫画。"
    override val migrateAutoMatchButton = "自动匹配"
    override val migrateAutoMatching = "正在自动匹配..."
    override val migrateExactMatchBadge = "精确匹配"
    override val migrateHighConfidenceBadge = "高置信匹配"
    override val migrateBatchProgressCardTitle = "批量迁移进行中"
    override fun migrateBatchProgress(current: Int, total: Int, title: String) =
        "正在迁移 ($current/$total)：$title"
    override fun migrateBatchCounts(success: Int, failed: Int) =
        "成功: $success | 失败/跳过: $failed"
    override val migrateBatchCancel = "取消迁移"
    override val migrateBatchReportTitle = "批量迁移报告"
    override fun migrateBatchReportSummary(success: Int, failed: Int) =
        "批量迁移完成：$success 部成功，$failed 部失败或跳过。"
    override fun migrateBatchAllSuccess(count: Int, targetSource: String) =
        "已成功将全部 $count 部漫画迁移至 $targetSource。"
    override fun migrateBatchCancelled(success: Int, total: Int) =
        "迁移已取消。已成功迁移 $success / $total 部漫画。"
    override fun migrateSuccessSnackbar(title: String, targetSource: String) =
        "已成功将《$title》迁移至 $targetSource。"
    override fun migrateFailedSnackbar(title: String, error: String) =
        "《$title》迁移失败：$error"
    override val migrateFailureNoMatch = "目标图源未找到匹配漫画"
    override val migrateFailureAmbiguous = "找到多个候选，需手动选择"
    override fun migrateFailureError(error: String) = "错误：$error"
    override val migrateBatchFailuresListHeader = "未成功迁移的漫画："
    override val migrateManualMigrate = "手动迁移"
    override val filterDialogTitle = "图源筛选"
    override val filterReset = "重置"
    override val filterNoAvailable = "该图源没有可用的筛选器。"
    override val filterApply = "应用筛选"
    override val filterAscending = "▲ 升序"
    override val filterDescending = "▼ 降序"
    override val globalSearchEnterQuery = "输入搜索关键词以在所有已安装及内置图源中查找。"
    override val globalSearchNoSources = "没有可用图源或尚未开始搜索。"
    override val globalSearchViewAll = "查看全部"
    override fun globalSearchError(error: String) = "出错了: $error"
    override val globalSearchNoResults = "该图源中无匹配结果"

    // Settings & Diagnostics & Reports
    override val settingsDownloadCustomPath = "自定义下载路径"
    override val settingsDownloadCustomPathPlaceholder = "留空则使用默认路径 (media/downloads)"
    override val settingsDownloadChooseFolder = "选择文件夹"
    override val settingsDownloadUseDefault = "恢复默认"
    override fun settingsDownloadActivePath(path: String) = "当前使用：$path"
    override val settingsDownloadRestartRequired = "路径更改会在重启 MihonW 后生效。"
    override val settingsDownloadAheadTitle = "阅读时自动预下载"
    override val settingsDownloadAheadDesc = "在阅读当前章节时，自动在后台下载后续未读章节。"
    override val settingsDownloadAheadDisabled = "已禁用"
    override fun settingsDownloadAheadChapters(count: Int) = "后 $count 话"
    override val settingsDeleteReadChaptersTitle = "自动删除已读章节"
    override val settingsDeleteReadChaptersDesc = "章节被标记为已读后，自动删除本地已下载的文件以节省存储空间。"
    override val settingsSaveChapterAsCbzTitle = "下载章节保存为 CBZ"
    override val settingsSaveChapterAsCbzDesc = "下载完成后，每章保存为单个 .cbz 压缩包，而不是文件夹。"
    override val settingsSplitTallImagesTitle = "下载时拆分长图"
    override val settingsSplitTallImagesDesc = "把下载到的超长页面切成若干屏幕高度的片段，阅读时加载更快。"
    override fun settingsTrackerLoggedInAs(user: String, server: String?) =
        "已登录为 $user" + if (!server.isNullOrBlank()) " ($server)" else ""
    override val settingsTrackerLogout = "退出登录"
    override fun settingsLibraryUpdateResult(checked: Int, newChapters: Int) =
        "已检查 $checked 部漫画，发现 $newChapters 个新章节"
    override val settingsLibraryUpdateCompleted = "书架更新已完成"
    override fun settingsLibraryUpdateFailed(msg: String) = "更新失败: $msg"
    override val readerUnavailable = "当前运行环境中阅读器不可用。"
    override val readerOpeningChapter = "正在打开章节…"
    override fun importReportTitle(id: Long) = "导入报告 $id"
    override fun importReportManga(inserted: Long, merged: Long) = "漫画：新增 $inserted 部，合并 $merged 部"
    override fun importReportChapters(inserted: Long, merged: Long) = "章节：新增 $inserted 话，合并 $merged 话"
    override fun importReportCategories(count: Long) = "关联分类：$count 个"
    override fun importReportPreferences(imported: Long, skipped: Long) = "偏好设置：导入 $imported 项，跳过 $skipped 项"
    override fun importReportSkipCategories(categories: String) = "跳过分类：$categories"
    override fun importReportCategory(category: String) = "分类：$category"
    override val mangaDetailBackToLibrary = "返回书架"

    // Phase 2d: Browse & Search Options
    override val browseGlobalSearchOnlyPinned = "仅固定图源"
    override val browseHideSource = "隐藏"
    override fun browseSourceHidden(name: String) = "已隐藏：$name"
    override val settingsResetHiddenSources = "恢复已隐藏图源"
    override val settingsResetHiddenSourcesDesc = "取消隐藏所有已手动隐藏的图源"
    override val settingsResetHiddenSourcesSuccess = "已恢复所有已隐藏图源"
    override val browseFilterLanguageAll = "全部"
    override val settingsShowNsfw = "显示 NSFW 图源与扩展"
    override val settingsShowNsfwDesc = "允许浏览和搜索成人 / 18+ 内容"

    // Phase 2d: Backup Granularity
    override val backupOptionsTitleExport = "备份导出选项"
    override val backupOptionsTitleRestore = "备份恢复选项"
    override val backupOptionsSubtitle = "选择要包含的数据项"
    override val backupOptionChapterState = "章节状态（已读与书签）"
    override val backupOptionCategories = "分类"
    override val backupOptionTracking = "跟踪"
    override val backupOptionHistory = "历史"
    override val backupOptionReadProgress = "阅读进度（页码）"
    override val backupOptionSettings = "设置与偏好"
    override val backupOptionsConfirmExport = "选择位置并导出"
    override val backupOptionsConfirmRestore = "恢复"

    // Phase 2d: Onboarding
    override val onboardingWelcomeTitle = "欢迎使用 Mihon 桌面端"
    override val onboardingWelcomeDesc = "让我们先完成几项基本设置，开启你的阅读之旅。"
    override val onboardingStepLanguageAndTheme = "语言与外观"
    override val onboardingStepRepo = "扩展仓库"
    override val onboardingStepRepoDesc = "添加第三方仓库以发现漫画扩展，或跳过以直接查看已有图源。"
    override val onboardingStepBackup = "备份与存储"
    override val onboardingStepBackupDesc = "配置文件备份位置与自动备份周期，保护你的书架数据安全。"
    override val onboardingRepoUrlPlaceholder = "https://example.com/index.min.json"
    override val onboardingRepoAdd = "添加仓库"
    override val onboardingBackupPathLabel = "备份存储目录"
    override val onboardingBackupPathBrowse = "浏览…"
    override val onboardingBackupIntervalLabel = "自动备份周期"
    override val onboardingBtnNext = "下一步"
    override val onboardingBtnPrev = "上一步"
    override val onboardingBtnSkip = "跳过"
    override val onboardingBtnFinish = "开始使用"
    override val onboardingGoToBrowse = "前往浏览页"
    override val settingsReshowOnboarding = "重新显示首次运行引导"
    override val settingsReshowOnboardingDesc = "重新打开初始设置向导"
}
