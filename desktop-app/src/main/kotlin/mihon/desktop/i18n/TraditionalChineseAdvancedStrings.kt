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

internal class TraditionalChineseAdvancedStrings(private val core: CoreStrings) : AdvancedStrings, CoreStrings by core {
    // Tracker & Tracking
    override fun trackerConnectTitle(name: String) = "連線到 $name"
    override fun trackerAuthTitle(name: String) = "在 $name 認證"
    override fun trackerLoginTitle(name: String) = "登入到 $name"
    override val trackerServerUrl = "伺服器位址 (例如 https://komga.example.com)"
    override val trackerUsername = "使用者名稱"
    override val trackerPasswordOrApiKey = "密碼或 API 金鑰"
    override fun trackerAuthUrl(url: String) = "授權網址: $url"
    override val trackerToken = "API 權杖 / 存取權杖"
    override val trackerAccountName = "帳戶名稱 (選填)"
    override val trackerPassword = "密碼"
    override val trackerConnect = "連線"
    override val trackerLogin = "登入"
    override val trackerLoginFailed = "登入失敗，請檢查帳戶、憑證和伺服器位址後重試。"
    override val trackerServerAuthHelp = "使用 API 金鑰時留空使用者名稱；使用密碼時填寫使用者名稱和密碼。"
    override val trackingRequestFailed = "追蹤請求失敗，請檢查網路和登入狀態後重試。"
    override fun trackingTitle(mangaTitle: String) = "進度記錄 - $mangaTitle"
    override val trackingNotTracking = "未記錄"
    override val trackingNotLoggedIn = "未登入"
    override val trackingTrack = "記錄"
    override val trackingEdit = "編輯"
    override val trackingRemove = "解除綁定"
    override fun trackingSearchTitle(name: String) = "搜尋 $name"
    override val trackingSearch = "搜尋"
    override fun trackingTotalChapters(count: Long) = "$count 話"
    override fun trackingEditTitle(title: String) = "編輯紀錄 - $title"
    override val trackingChaptersRead = "已讀話數"
    override val trackingScore = "評分"
    override fun trackStatusLabel(status: TrackStatus) = when (status) {
        TrackStatus.READING -> "閱讀中"
        TrackStatus.COMPLETED -> "已讀完"
        TrackStatus.ON_HOLD -> "擱置"
        TrackStatus.DROPPED -> "棄坑"
        TrackStatus.PLAN_TO_READ -> "計畫閱讀"
        TrackStatus.REREADING -> "重讀中"
    }

    // Reader Chrome, Settings & Messages
    override val readerFullscreen = "全螢幕"
    override val readerBorderless = "無邊框"
    override val readerShortcutsTitle = "快捷鍵速查"
    override val readerShortcutsHelp = "快捷鍵"
    override fun readerCropToggle(active: Boolean) = if (active) "裁切白邊: 開" else "裁切白邊: 關"
    override fun readerCoverOffsetToggle(active: Boolean) = if (active) "封面偏移: 開" else "封面偏移: 關"
    override val readerModeDualLtr = "雙頁拼合（從左到右）"
    override val readerModeDualRtl = "雙頁拼合（從右到左）"
    override val readerModeVertical = "連續垂直"
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
    override val readerPageTransitions = "翻頁過渡"
    override fun readerPageTransitionLabel(transition: ReaderPageTransition) = when (transition) {
        ReaderPageTransition.NONE -> "無"
        ReaderPageTransition.FADE -> "淡入淡出"
        ReaderPageTransition.SLIDE -> "滑動"
    }
    override val readerDualPageSplit = "跨頁分割"
    override fun readerDualPageSplitLabel(split: mihon.reader.layout.DualPageSplit) = when (split) {
        mihon.reader.layout.DualPageSplit.NEVER -> "從不"
        mihon.reader.layout.DualPageSplit.ALWAYS -> "總是"
        mihon.reader.layout.DualPageSplit.WIDE -> "僅寬頁"
    }
    override val readerDualPageRotateToFit = "旋轉寬頁以適應"
    override val readerSettingsDialogTitle = "閱讀器設定"
    override val readerSettingsTabReading = "閱讀模式"
    override val readerSettingsTabGeneral = "一般"
    override val readerSettingsTabFilter = "色彩濾鏡"
    override val readerChapterTransitions = "章節切換"
    override val readerAlwaysShowChapterTransition = "一律顯示章節切換頁"
    override val readerSkipReadChapters = "跳過已讀章節"
    override val readerSkipFilteredChapters = "跳過已篩選章節"
    override val readerSkipDuplicateChapters = "跳過重複章節"
    override val readerClickRegions = "點擊翻頁區域"
    override val readerRegionLeft = "左側"
    override val readerRegionCenter = "中間"
    override val readerRegionRight = "右側"
    override fun readerActionLabel(action: ReaderClickAction) = when (action) {
        ReaderClickAction.PREVIOUS -> "上一頁"
        ReaderClickAction.NEXT -> "下一頁"
        ReaderClickAction.TOGGLE_CHROME -> "切換選單"
        ReaderClickAction.NONE -> "無動作"
    }
    override fun readerLeftBoundary(percent: Int) = "左側邊界 $percent%"
    override fun readerCenterBoundary(percent: Int) = "中間邊界 $percent%"
    override fun readerWheelLabel(behavior: ReaderWheelBehavior) = when (behavior) {
        ReaderWheelBehavior.SCROLL -> "滾輪: 捲動頁面"
        ReaderWheelBehavior.PAGE_NAVIGATION -> "滾輪: 翻頁"
    }
    override val readerReserveCover = "雙頁模式下保留封面獨立單頁"
    override val readerCropBordersPaged = "智慧裁切白邊 (單頁/雙頁)"
    override val readerWebtoonLayout = "條漫排版"
    override val readerWebtoonWidthFull = "最大寬度: 全螢幕"
    override fun readerWebtoonWidthDp(width: Int) = "最大寬度: ${width}dp"
    override fun readerWebtoonSidePaddingPercent(padding: Int) = "側邊距: $padding%"
    override val readerLoadingChapter = "正在載入章節…"
    override val readerClosed = "閱讀器已關閉"
    override fun readerErrorMessage(error: ReaderSessionError): String = when (error.cause) {
        is ReaderFailure.PageNotFound -> "找不到此頁面，可能已被移動或刪除。"
        is ReaderFailure.UnsupportedFormat -> "不支援此章節格式。"
        is ReaderFailure.RemoteImage -> "頁面下載失敗。\n${error.cause?.cause?.message ?: error.cause?.message.orEmpty()}"
        is ReaderFailure.EncryptedContainer -> "不支援加密的章節壓縮包。"
        is ReaderFailure.UnsafePath,
        is ReaderFailure.ResourceChanged,
        -> "本機章節已不可用，請重新匯入或檢查檔案位置。"
        is ReaderFailure.CorruptContainer,
        is ReaderFailure.CorruptImage,
        -> "頁面檔案損毀或無法讀取。"
        is ReaderFailure.UnsupportedImage,
        is ReaderFailure.RegionUnavailable,
        -> "不支援此圖片格式。"
        is ReaderFailure.LimitExceeded,
        is ReaderFailure.TooManyEntries,
        -> "章節內容超出安全閱讀限制。"
        is ReaderFailure.EmptyChapter -> "此章節不包含任何可讀取的頁面。"
        else -> when (error.code) {
            ReaderErrorCode.EMPTY_CHAPTER -> "此章節不包含任何可讀取的頁面。"
            ReaderErrorCode.SOURCE_UNAVAILABLE -> "圖源或本機章節不可用，請定位或重新匯入。"
            ReaderErrorCode.PAGE_NOT_FOUND -> "找不到此頁面。"
            ReaderErrorCode.PAGE_DECODE_FAILED -> "頁面解碼失敗。"
            ReaderErrorCode.MEMORY_LIMIT_REACHED -> "閱讀器已達記憶體佔用上限。"
            ReaderErrorCode.INVALID_PROGRESS -> "儲存的閱讀進度無效。"
        }
    }

    // Browse, Sources, Extensions, Migration, Filters, Global Search
    override fun browseSourceLanguage(lang: String) = "圖源語言: " + lang.uppercase()
    override val browseSourcePopular = "熱門"
    override val browseSourceLatest = "最新"
    override val browseSearchTitlesPlaceholder = "搜尋漫畫標題..."
    override val browseSearchButton = "搜尋"
    override fun browseFiltersButton(count: Int) = if (count > 0) "篩選 ($count)" else "篩選"
    override val browseNoMangaFound = "找不到任何漫畫"
    override fun browseSourceUnsupportedOperation(mode: SourceListingMode) = when (mode) {
        SourceListingMode.Popular -> "該圖源不支援瀏覽，請使用搜尋"
        SourceListingMode.Latest -> "該圖源不支援最新，請使用搜尋"
        SourceListingMode.Search -> "該圖源不支援搜尋"
    }
    override val browseInLibraryBadge = "已在書架"
    override val browsePrevPage = "上一頁"
    override fun browsePageNumber(page: Int) = "第 $page 頁"
    override val browseNextPage = "下一頁"
    override fun browseInstallExtensionTitle(name: String) = "安裝擴充套件: $name"
    override fun browsePackageLabel(pkg: String) = "套件名稱: $pkg"
    override fun browseVersionLabel(version: String) = "版本: $version"
    override fun browseLanguageLabel(lang: String) = "語言: $lang"
    override val browseNetworkPermissionNotice = "安裝擴充套件前請確認其網路宣告權限。"
    override val browseTrustAndInstall = "信任並安裝"
    override val browseAddRepository = "新增"
    override val browseConfiguredRepositories = "已設定的套件庫:"
    override val browseRemoveRepository = "移除"
    override val browseSourceBrowse = "瀏覽"
    override val browsePin = "☆ 固定"
    override val browseMoreActions = "更多操作"
    override val browsePinnedSection = "已釘選"
    override val browseUnpin = "★ 取消固定"
    override val browseUpdate = "更新"
    override val browseDisable = "停用"
    override val browseEnable = "啟用"
    override fun onlineDetailSource(name: String, lang: String) = "圖源: $name (${lang.uppercase()})"
    override fun onlineDetailAuthor(author: String) = "作者: $author"
    override fun onlineDetailArtist(artist: String) = "畫師: $artist"
    override fun onlineDetailGenres(genres: String) = "標籤: $genres"
    override fun onlineDetailChapters(count: Int) = "章節 ($count)"
    override fun onlineDetailScanlator(scanlator: String) = "翻譯組/發布方: $scanlator"
    override val migrateSelectSourceHeader = "選擇要遷出的圖源："
    override val migrateNoMangaInLibrary = "書架中暫無來自線上圖源的漫畫。"
    override fun migrateMangaCount(count: Int) = "書架中有 $count 部漫畫"
    override val migrateViewManga = "查看漫畫"
    override val migrateBackToSources = "返回圖源清單"
    override val migrateSelectMangaHeader = "選擇要遷移到其他圖源的漫畫"
    override val migrateAction = "遷移"
    override fun migrateDialogTitle(title: String) = "遷移: $title"
    override val migrateDialogSubtitle = "在目標圖源中搜尋對應作品，以轉移閱讀進度、書籤和分類。"
    override val migrateNoOtherSources = "沒有可用於遷移的其他圖源。"
    override val migrateTargetSourceLabel = "目標圖源:"
    override val migrateSearchPlaceholder = "搜尋標題..."
    override val migrateSelectedBadge = "✓ 已選取"
    override val migrateConfirm = "確認遷移"
    override val migrateAllButton = "全部遷移"
    override val migrateAllDialogTitle = "批量遷移漫畫"
    override fun migrateAllDialogSubtitle(count: Int, sourceName: String) =
        "在目標圖源中自動匹配並遷移來自「$sourceName」的全部 $count 部漫畫。"
    override val migrateAutoMatchButton = "自動匹配"
    override val migrateAutoMatching = "正在自動匹配..."
    override val migrateExactMatchBadge = "精確匹配"
    override val migrateHighConfidenceBadge = "高置信匹配"
    override val migrateBatchProgressCardTitle = "批量遷移進行中"
    override fun migrateBatchProgress(current: Int, total: Int, title: String) =
        "正在遷移 ($current/$total)：$title"
    override fun migrateBatchCounts(success: Int, failed: Int) =
        "成功: $success | 失敗/略過: $failed"
    override val migrateBatchCancel = "取消遷移"
    override val migrateBatchReportTitle = "批量遷移報告"
    override fun migrateBatchReportSummary(success: Int, failed: Int) =
        "批量遷移完成：$success 部成功，$failed 部失敗或略過。"
    override fun migrateBatchAllSuccess(count: Int, targetSource: String) =
        "已成功將全部 $count 部漫畫遷移至 $targetSource。"
    override fun migrateBatchCancelled(success: Int, total: Int) =
        "遷移已取消。已成功遷移 $success / $total 部漫畫。"
    override fun migrateSuccessSnackbar(title: String, targetSource: String) =
        "已成功將《$title》遷移至 $targetSource。"
    override fun migrateFailedSnackbar(title: String, error: String) =
        "《$title》遷移失敗：$error"
    override val migrateFailureNoMatch = "目標圖源未找到匹配漫畫"
    override val migrateFailureAmbiguous = "找到多個候選，需手動選擇"
    override fun migrateFailureError(error: String) = "錯誤：$error"
    override val migrateBatchFailuresListHeader = "未成功遷移的漫畫："
    override val migrateManualMigrate = "手動遷移"
    override val filterDialogTitle = "圖源篩選"
    override val filterReset = "重設"
    override val filterNoAvailable = "該圖源沒有可用的篩選器。"
    override val filterApply = "套用篩選"
    override val filterAscending = "▲ 遞增"
    override val filterDescending = "▼ 遞減"
    override val globalSearchEnterQuery = "輸入關鍵字在所有已安裝及內建圖源中尋找。"
    override val globalSearchNoSources = "沒有可用圖源或尚未開始搜尋。"
    override val globalSearchViewAll = "查看全部"
    override fun globalSearchError(error: String) = "發生錯誤: $error"
    override val globalSearchNoResults = "該圖源中無相符結果"

    // Settings & Diagnostics & Reports
    override val settingsDownloadCustomPath = "自訂下載路徑"
    override val settingsDownloadCustomPathPlaceholder = "留空則使用預設路徑 (media/downloads)"
    override val settingsDownloadChooseFolder = "選擇資料夾"
    override val settingsDownloadUseDefault = "恢復預設"
    override fun settingsDownloadActivePath(path: String) = "目前使用：$path"
    override val settingsDownloadRestartRequired = "路徑變更會在重新啟動 MihonW 後生效。"
    override val settingsDownloadAheadTitle = "閱讀時自動預先下載"
    override val settingsDownloadAheadDesc = "在閱讀當前章節時，自動在背景下載後續未讀章節。"
    override val settingsDownloadAheadDisabled = "已停用"
    override fun settingsDownloadAheadChapters(count: Int) = "後 $count 話"
    override val settingsDeleteReadChaptersTitle = "自動刪除已讀章節"
    override val settingsDeleteReadChaptersDesc = "章節被標記為已讀後，自動刪除本機已下載的檔案以節省空間。"
    override val settingsSaveChapterAsCbzTitle = "下載章節儲存為 CBZ"
    override val settingsSaveChapterAsCbzDesc = "下載完成後，每章儲存為單一 .cbz 壓縮檔，而非資料夾。"
    override val settingsSplitTallImagesTitle = "下載時分割長圖"
    override val settingsSplitTallImagesDesc = "把下載到的超長頁面切成數個螢幕高度的片段，閱讀時載入更快。"
    override fun settingsTrackerLoggedInAs(user: String, server: String?) =
        "已登入為 $user" + if (!server.isNullOrBlank()) " ($server)" else ""
    override val settingsTrackerLogout = "登出"
    override fun settingsLibraryUpdateResult(checked: Int, newChapters: Int) =
        "已檢查 $checked 部作品，發現 $newChapters 個新章節"
    override val settingsLibraryUpdateCompleted = "書架更新已完成"
    override fun settingsLibraryUpdateFailed(msg: String) = "更新失敗: $msg"
    override val readerUnavailable = "目前執行環境中閱讀器無法使用。"
    override val readerOpeningChapter = "正在開啟章節…"
    override fun importReportTitle(id: Long) = "匯入報告 $id"
    override fun importReportManga(inserted: Long, merged: Long) = "漫畫：新增 $inserted 部，合併 $merged 部"
    override fun importReportChapters(inserted: Long, merged: Long) = "章節：新增 $inserted 話，合併 $merged 話"
    override fun importReportCategories(count: Long) = "關聯分類：$count 個"
    override fun importReportPreferences(imported: Long, skipped: Long) = "偏好設定：匯入 $imported 項，略過 $skipped 項"
    override fun importReportSkipCategories(categories: String) = "略過分類：$categories"
    override fun importReportCategory(category: String) = "分類：$category"
    override val mangaDetailBackToLibrary = "返回書架"

    // Phase 2d: Browse & Search Options
    override val browseGlobalSearchOnlyPinned = "僅置頂圖源"
    override val browseHideSource = "隱藏"
    override fun browseSourceHidden(name: String) = "已隱藏：$name"
    override val settingsResetHiddenSources = "恢復已隱藏圖源"
    override val settingsResetHiddenSourcesDesc = "取消隱藏所有已手動隱藏的圖源"
    override val settingsResetHiddenSourcesSuccess = "已恢復所有已隱藏圖源"
    override val browseFilterLanguageAll = "全部"
    override val settingsShowNsfw = "顯示 NSFW 圖源與擴充功能"
    override val settingsShowNsfwDesc = "允許瀏覽和搜尋成人 / 18+ 內容"

    // Phase 2d: Backup Granularity
    override val backupOptionsTitleExport = "備份匯出選項"
    override val backupOptionsTitleRestore = "備份還原選項"
    override val backupOptionsSubtitle = "選擇要包含的資料項"
    override val backupOptionChapterState = "章節狀態（已讀與書籤）"
    override val backupOptionCategories = "分類"
    override val backupOptionTracking = "追蹤"
    override val backupOptionHistory = "歷史"
    override val backupOptionReadProgress = "閱讀進度（頁碼）"
    override val backupOptionSettings = "設定與偏好"
    override val backupOptionsConfirmExport = "選擇位置並匯出"
    override val backupOptionsConfirmRestore = "還原"

    // Phase 2d: Onboarding
    override val onboardingWelcomeTitle = "歡迎使用 Mihon 桌面端"
    override val onboardingWelcomeDesc = "讓我們先完成幾項基本設定，開啟你的閱讀之旅。"
    override val onboardingStepLanguageAndTheme = "語言與外觀"
    override val onboardingStepRepo = "擴充功能儲存庫"
    override val onboardingStepRepoDesc = "添加第三方儲存庫以探索漫畫擴充，或跳過以直接查看已有圖源。"
    override val onboardingStepBackup = "備份與儲存"
    override val onboardingStepBackupDesc = "設定檔案備份位置與自動備份週期，保護你的書架資料安全。"
    override val onboardingRepoUrlPlaceholder = "https://example.com/index.min.json"
    override val onboardingRepoAdd = "添加儲存庫"
    override val onboardingBackupPathLabel = "備份儲存目錄"
    override val onboardingBackupPathBrowse = "瀏覽…"
    override val onboardingBackupIntervalLabel = "自動備份週期"
    override val onboardingBtnNext = "下一步"
    override val onboardingBtnPrev = "上一步"
    override val onboardingBtnSkip = "略過"
    override val onboardingBtnFinish = "開始使用"
    override val onboardingGoToBrowse = "前往瀏覽頁"
    override val settingsReshowOnboarding = "重新顯示首次執行引導"
    override val settingsReshowOnboardingDesc = "重新開啟初始設定精靈"
}
