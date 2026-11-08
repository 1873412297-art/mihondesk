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

internal class EnglishAdvancedStrings(private val core: CoreStrings) : AdvancedStrings, CoreStrings by core {
    // Tracker & Tracking
    override fun trackerConnectTitle(name: String) = "Connect to $name"
    override fun trackerAuthTitle(name: String) = "Authenticate with $name"
    override fun trackerLoginTitle(name: String) = "Log in to $name"
    override val trackerServerUrl = "Server URL (e.g. https://komga.example.com)"
    override val trackerUsername = "Username"
    override val trackerPasswordOrApiKey = "Password or API Key"
    override fun trackerAuthUrl(url: String) = "Authorization URL: $url"
    override val trackerToken = "API Token / Access Token"
    override val trackerAccountName = "Account Name (Optional)"
    override val trackerPassword = "Password"
    override val trackerConnect = "Connect"
    override val trackerLogin = "Log In"
    override val trackerLoginFailed = "Login failed. Check the account, credential and server address, then retry."
    override val trackerServerAuthHelp = "For an API key, leave username empty. For a password, enter both fields."
    override val trackingRequestFailed = "The tracking request failed. Check the connection and login, then retry."
    override fun trackingTitle(mangaTitle: String) = "Tracking - $mangaTitle"
    override val trackingNotTracking = "Not tracking"
    override val trackingNotLoggedIn = "Not logged in"
    override val trackingTrack = "Track"
    override val trackingEdit = "Edit"
    override val trackingRemove = "Remove"
    override fun trackingSearchTitle(name: String) = "Search $name"
    override val trackingSearch = "Search"
    override fun trackingTotalChapters(count: Long) = "$count chapters"
    override fun trackingEditTitle(title: String) = "Edit Tracking - $title"
    override val trackingChaptersRead = "Chapters Read"
    override val trackingScore = "Score"
    override fun trackStatusLabel(status: TrackStatus) = when (status) {
        TrackStatus.READING -> "Reading"
        TrackStatus.COMPLETED -> "Completed"
        TrackStatus.ON_HOLD -> "On Hold"
        TrackStatus.DROPPED -> "Dropped"
        TrackStatus.PLAN_TO_READ -> "Plan to Read"
        TrackStatus.REREADING -> "Rereading"
    }

    // Reader Chrome, Settings & Messages
    override val readerFullscreen = "Fullscreen"
    override val readerBorderless = "Borderless"
    override val readerShortcutsTitle = "Keyboard Shortcuts"
    override val readerShortcutsHelp = "Shortcuts"
    override fun readerCropToggle(active: Boolean) = if (active) "Crop: On" else "Crop: Off"
    override fun readerCoverOffsetToggle(active: Boolean) = if (active) "Cover offset: On" else "Cover offset: Off"
    override val readerModeDualLtr = "Dual LTR"
    override val readerModeDualRtl = "Dual RTL"
    override val readerModeVertical = "Vertical"
    override fun readerModeLabel(mode: ReadingMode) = when (mode) {
        ReadingMode.SINGLE_LTR -> "Single LTR"
        ReadingMode.SINGLE_RTL -> "Single RTL"
        ReadingMode.DUAL_LTR -> readerModeDualLtr
        ReadingMode.DUAL_RTL -> readerModeDualRtl
        ReadingMode.VERTICAL -> readerModeVertical
        ReadingMode.WEBTOON -> "Webtoon"
    }
    override fun readerScaleLabel(scale: ScaleMode) = when (scale) {
        ScaleMode.ORIGINAL -> "Original"
        ScaleMode.FIT_WIDTH -> "Fit width"
        ScaleMode.FIT_HEIGHT -> "Fit height"
    }
    override fun readerFilterLabel(filter: ReaderColorFilter) = when (filter) {
        ReaderColorFilter.NONE -> "None"
        ReaderColorFilter.INVERT -> "Invert"
        ReaderColorFilter.GRAYSCALE -> "Grayscale"
        ReaderColorFilter.INVERT_GRAYSCALE -> "Invert grayscale"
        ReaderColorFilter.SEPIA -> "Sepia"
        ReaderColorFilter.NIGHT -> "Night"
        ReaderColorFilter.CUSTOM -> "Custom"
    }
    override fun readerBackgroundLabel(bg: ReaderBackgroundColor) = when (bg) {
        ReaderBackgroundColor.DARK_GRAY -> "Dark gray"
        ReaderBackgroundColor.BLACK -> "Black"
        ReaderBackgroundColor.WHITE -> "White"
        ReaderBackgroundColor.WARM_CREAM -> "Warm cream"
    }
    override val readerPageTransitions = "Page transitions"
    override fun readerPageTransitionLabel(transition: ReaderPageTransition) = when (transition) {
        ReaderPageTransition.NONE -> "None"
        ReaderPageTransition.FADE -> "Fade"
        ReaderPageTransition.SLIDE -> "Slide"
    }
    override val readerDualPageSplit = "Split wide pages"
    override fun readerDualPageSplitLabel(split: mihon.reader.layout.DualPageSplit) = when (split) {
        mihon.reader.layout.DualPageSplit.NEVER -> "Never"
        mihon.reader.layout.DualPageSplit.ALWAYS -> "Always"
        mihon.reader.layout.DualPageSplit.WIDE -> "Wide pages only"
    }
    override val readerDualPageRotateToFit = "Rotate wide pages to fit"
    override val readerSettingsDialogTitle = "Reader settings"
    override val readerSettingsTabReading = "Reading mode"
    override val readerSettingsTabGeneral = "General"
    override val readerSettingsTabFilter = "Color filter"
    override val readerChapterTransitions = "Chapter transitions"
    override val readerAlwaysShowChapterTransition = "Always show chapter transition"
    override val readerSkipReadChapters = "Skip read chapters"
    override val readerSkipFilteredChapters = "Skip filtered chapters"
    override val readerSkipDuplicateChapters = "Skip duplicate chapters"
    override val readerClickRegions = "Click regions"
    override val readerRegionLeft = "Left"
    override val readerRegionCenter = "Center"
    override val readerRegionRight = "Right"
    override fun readerActionLabel(action: ReaderClickAction) = action.name.lowercase()
    override fun readerLeftBoundary(percent: Int) = "Left boundary $percent%"
    override fun readerCenterBoundary(percent: Int) = "Center boundary $percent%"
    override fun readerWheelLabel(behavior: ReaderWheelBehavior) = when (behavior) {
        ReaderWheelBehavior.SCROLL -> "Wheel: scroll page"
        ReaderWheelBehavior.PAGE_NAVIGATION -> "Wheel: flip page"
    }
    override val readerReserveCover = "Reserve cover in dual-page modes"
    override val readerCropBordersPaged = "Smart crop borders (Paged)"
    override val readerWebtoonLayout = "Webtoon layout"
    override val readerWebtoonWidthFull = "Max width: Full screen"
    override fun readerWebtoonWidthDp(width: Int) = "Max width: ${width}dp"
    override fun readerWebtoonSidePaddingPercent(padding: Int) = "Side padding: $padding%"
    override val readerLoadingChapter = "Loading chapter…"
    override val readerClosed = "Reader closed"
    override fun readerErrorMessage(error: ReaderSessionError): String = when (error.cause) {
        is ReaderFailure.PageNotFound -> "This page could not be found. It may have been moved or deleted."
        is ReaderFailure.UnsupportedFormat -> "This chapter format is not supported."
        is ReaderFailure.RemoteImage ->
            "The page could not be downloaded.\n${error.cause?.cause?.message ?: error.cause?.message.orEmpty()}"
        is ReaderFailure.EncryptedContainer -> "Encrypted chapter containers are not supported."
        is ReaderFailure.UnsafePath,
        is ReaderFailure.ResourceChanged,
        -> "This local chapter is no longer available. Locate or re-import it."
        is ReaderFailure.CorruptContainer,
        is ReaderFailure.CorruptImage,
        -> "This page is corrupt or unreadable."
        is ReaderFailure.UnsupportedImage,
        is ReaderFailure.RegionUnavailable,
        -> "This image format is not supported."
        is ReaderFailure.LimitExceeded,
        is ReaderFailure.TooManyEntries,
        -> "This chapter exceeds the safe reader limits."
        is ReaderFailure.EmptyChapter -> "This chapter contains no readable pages."
        else -> when (error.code) {
            ReaderErrorCode.EMPTY_CHAPTER -> "This chapter contains no readable pages."
            ReaderErrorCode.SOURCE_UNAVAILABLE -> "This local chapter is unavailable. Locate or re-import it."
            ReaderErrorCode.PAGE_NOT_FOUND -> "This page could not be found."
            ReaderErrorCode.PAGE_DECODE_FAILED -> "This page could not be decoded."
            ReaderErrorCode.MEMORY_LIMIT_REACHED -> "The reader reached its memory limit."
            ReaderErrorCode.INVALID_PROGRESS -> "The saved reading position is invalid."
        }
    }

    // Browse, Sources, Extensions, Migration, Filters, Global Search
    override fun browseSourceLanguage(lang: String) = "Language: " + lang.uppercase()
    override val browseSourcePopular = "Popular"
    override val browseSourceLatest = "Latest"
    override val browseSearchTitlesPlaceholder = "Search titles..."
    override val browseSearchButton = "Search"
    override fun browseFiltersButton(count: Int) = if (count > 0) "Filters ($count)" else "Filters"
    override val browseNoMangaFound = "No manga found"
    override fun browseSourceUnsupportedOperation(mode: SourceListingMode) = when (mode) {
        SourceListingMode.Popular -> "This source does not support browsing, please use search"
        SourceListingMode.Latest -> "This source does not support latest updates, please use search"
        SourceListingMode.Search -> "This source does not support search"
    }
    override val browseInLibraryBadge = "IN LIBRARY"
    override val browsePrevPage = "Previous"
    override fun browsePageNumber(page: Int) = "Page $page"
    override val browseNextPage = "Next"
    override fun browseInstallExtensionTitle(name: String) = "Install Extension: $name"
    override fun browsePackageLabel(pkg: String) = "Package: $pkg"
    override fun browseVersionLabel(version: String) = "Version: $version"
    override fun browseLanguageLabel(lang: String) = "Language: $lang"
    override val browseNetworkPermissionNotice = "Review network permissions before installing extensions."
    override val browseTrustAndInstall = "Trust & Install"
    override val browseAddRepository = "Add"
    override val browseConfiguredRepositories = "Configured Repositories:"
    override val browseRemoveRepository = "Remove"
    override val browseSourceBrowse = "Browse"
    override val browsePin = "☆ Pin"
    override val browseMoreActions = "More actions"
    override val browsePinnedSection = "Pinned"
    override val browseUnpin = "★ Unpin"
    override val browseUpdate = "Update"
    override val browseDisable = "Disable"
    override val browseEnable = "Enable"
    override fun onlineDetailSource(name: String, lang: String) = "Source: $name (${lang.uppercase()})"
    override fun onlineDetailAuthor(author: String) = "Author: $author"
    override fun onlineDetailArtist(artist: String) = "Artist: $artist"
    override fun onlineDetailGenres(genres: String) = "Genres: $genres"
    override fun onlineDetailChapters(count: Int) = "Chapters ($count)"
    override fun onlineDetailScanlator(scanlator: String) = "Scanlator: $scanlator"
    override val migrateSelectSourceHeader = "Select a source to migrate manga from:"
    override val migrateNoMangaInLibrary = "No manga from online sources in library."
    override fun migrateMangaCount(count: Int) = "$count manga in library"
    override val migrateViewManga = "View Manga"
    override val migrateBackToSources = "Back to Sources"
    override val migrateSelectMangaHeader = "Select a manga to migrate to another source"
    override val migrateAction = "Migrate"
    override fun migrateDialogTitle(title: String) = "Migrate: $title"
    override val migrateDialogSubtitle =
        "Search for a matching title in target source to transfer reading progress, bookmarks, and categories."
    override val migrateNoOtherSources = "No other sources available for migration."
    override val migrateTargetSourceLabel = "Target Source:"
    override val migrateSearchPlaceholder = "Search title..."
    override val migrateSelectedBadge = "✓ Selected"
    override val migrateConfirm = "Confirm Migration"
    override val migrateAllButton = "Migrate All"
    override val migrateAllDialogTitle = "Migrate All Manga"
    override fun migrateAllDialogSubtitle(count: Int, sourceName: String) =
        "Automatically match and migrate all $count manga from $sourceName to the target source."
    override val migrateAutoMatchButton = "Auto Match"
    override val migrateAutoMatching = "Auto-matching..."
    override val migrateExactMatchBadge = "Exact match"
    override val migrateHighConfidenceBadge = "High confidence"
    override val migrateBatchProgressCardTitle = "Batch Migration in Progress"
    override fun migrateBatchProgress(current: Int, total: Int, title: String) =
        "Migrating ($current/$total): $title"
    override fun migrateBatchCounts(success: Int, failed: Int) =
        "Success: $success | Failed/Skipped: $failed"
    override val migrateBatchCancel = "Cancel Migration"
    override val migrateBatchReportTitle = "Batch Migration Results"
    override fun migrateBatchReportSummary(success: Int, failed: Int) =
        "Migration finished: $success succeeded, $failed failed or skipped."
    override fun migrateBatchAllSuccess(count: Int, targetSource: String) =
        "Successfully migrated all $count manga to $targetSource."
    override fun migrateBatchCancelled(success: Int, total: Int) =
        "Migration cancelled. Successfully migrated $success of $total manga."
    override fun migrateSuccessSnackbar(title: String, targetSource: String) =
        "Successfully migrated \"$title\" to $targetSource."
    override fun migrateFailedSnackbar(title: String, error: String) =
        "Migration failed for \"$title\": $error"
    override val migrateFailureNoMatch = "No matching manga found on target source"
    override val migrateFailureAmbiguous = "Multiple ambiguous matches found; requires manual selection"
    override fun migrateFailureError(error: String) = "Error: $error"
    override val migrateBatchFailuresListHeader = "Unmatched / Failed Manga:"
    override val migrateManualMigrate = "Manual Migrate"
    override val filterDialogTitle = "Source Filters"
    override val filterReset = "Reset"
    override val filterNoAvailable = "No filters available for this source."
    override val filterApply = "Apply Filters"
    override val filterAscending = "▲ Asc"
    override val filterDescending = "▼ Desc"
    override val globalSearchEnterQuery = "Enter a search query to search across all installed and built-in sources."
    override val globalSearchNoSources = "No sources available or search not started."
    override val globalSearchViewAll = "View All"
    override fun globalSearchError(error: String) = "Error: $error"
    override val globalSearchNoResults = "No results in this source"

    // Settings & Diagnostics & Reports
    override val settingsDownloadCustomPath = "Custom Download Path"
    override val settingsDownloadCustomPathPlaceholder = "Leave blank for default (media/downloads)"
    override val settingsDownloadChooseFolder = "Choose Folder"
    override val settingsDownloadUseDefault = "Use Default"
    override fun settingsDownloadActivePath(path: String) = "Currently used: $path"
    override val settingsDownloadRestartRequired = "Path changes take effect after restarting MihonW."
    override val settingsDownloadAheadTitle = "Download Ahead While Reading"
    override val settingsDownloadAheadDesc = "Automatically download the next unread chapters while reading."
    override val settingsDownloadAheadDisabled = "Disabled"
    override fun settingsDownloadAheadChapters(count: Int) = if (count == 1) "1 chapter" else "$count chapters"
    override val settingsDeleteReadChaptersTitle = "Delete Read Chapters"
    override val settingsDeleteReadChaptersDesc =
        "Automatically delete downloaded chapter files when marked as read."
    override val settingsSaveChapterAsCbzTitle = "Save Downloaded Chapters as CBZ"
    override val settingsSaveChapterAsCbzDesc =
        "Publish each finished chapter download as a single .cbz archive instead of a folder."
    override val settingsSplitTallImagesTitle = "Split Tall Images When Downloading"
    override val settingsSplitTallImagesDesc =
        "Cut very tall downloaded pages into several screen-sized parts so the reader loads them faster."
    override fun settingsTrackerLoggedInAs(user: String, server: String?) =
        "Logged in as $user" + if (!server.isNullOrBlank()) " ($server)" else ""
    override val settingsTrackerLogout = "Log Out"
    override fun settingsLibraryUpdateResult(checked: Int, newChapters: Int) =
        "Checked $checked titles, $newChapters new chapters"
    override val settingsLibraryUpdateCompleted = "Update completed"
    override fun settingsLibraryUpdateFailed(msg: String) = "Update failed: $msg"
    override val readerUnavailable = "The reader is unavailable in this runtime."
    override val readerOpeningChapter = "Opening chapter…"
    override fun importReportTitle(id: Long) = "Report $id"
    override fun importReportManga(inserted: Long, merged: Long) = "Manga: $inserted inserted, $merged merged"
    override fun importReportChapters(inserted: Long, merged: Long) = "Chapters: $inserted inserted, $merged merged"
    override fun importReportCategories(count: Long) = "Categories linked: $count"
    override fun importReportPreferences(
        imported: Long,
        skipped: Long,
    ) = "Preferences: $imported imported, $skipped skipped"
    override fun importReportSkipCategories(categories: String) = "Skip categories: $categories"
    override fun importReportCategory(category: String) = "Category: $category"
    override val mangaDetailBackToLibrary = "Back to Library"

    // Phase 2d: Browse & Search Options
    override val browseGlobalSearchOnlyPinned = "Only pinned sources"
    override val browseHideSource = "Hide"
    override fun browseSourceHidden(name: String) = "Hidden: $name"
    override val settingsResetHiddenSources = "Reset hidden sources"
    override val settingsResetHiddenSourcesDesc = "Unhide all manually hidden sources"
    override val settingsResetHiddenSourcesSuccess = "All hidden sources restored"
    override val browseFilterLanguageAll = "All"
    override val settingsShowNsfw = "Show NSFW sources and extensions"
    override val settingsShowNsfwDesc = "Allow viewing and searching adult / 18+ content"

    // Phase 2d: Backup Granularity
    override val backupOptionsTitleExport = "Export Backup Options"
    override val backupOptionsTitleRestore = "Restore Backup Options"
    override val backupOptionsSubtitle = "Select data components to include"
    override val backupOptionChapterState = "Chapter state (read & bookmark)"
    override val backupOptionCategories = "Categories"
    override val backupOptionTracking = "Tracking"
    override val backupOptionHistory = "History"
    override val backupOptionReadProgress = "Read progress (page number)"
    override val backupOptionSettings = "Settings & preferences"
    override val backupOptionsConfirmExport = "Choose Location & Export"
    override val backupOptionsConfirmRestore = "Restore"

    // Phase 2d: Onboarding
    override val onboardingWelcomeTitle = "Welcome to Mihon Desktop"
    override val onboardingWelcomeDesc = "Let's quickly set up your preferences to get started."
    override val onboardingStepLanguageAndTheme = "Language & Theme"
    override val onboardingStepRepo = "Extension Repositories"
    override val onboardingStepRepoDesc =
        "Add third-party repositories to discover manga extensions, or skip to view installed sources."
    override val onboardingStepBackup = "Backup & Storage"
    override val onboardingStepBackupDesc =
        "Configure backup location and automated backup frequency to keep your data safe."
    override val onboardingRepoUrlPlaceholder = "https://example.com/index.min.json"
    override val onboardingRepoAdd = "Add Repository"
    override val onboardingBackupPathLabel = "Backup storage directory"
    override val onboardingBackupPathBrowse = "Browse…"
    override val onboardingBackupIntervalLabel = "Auto backup interval"
    override val onboardingBtnNext = "Next"
    override val onboardingBtnPrev = "Back"
    override val onboardingBtnSkip = "Skip"
    override val onboardingBtnFinish = "Get Started"
    override val onboardingGoToBrowse = "Go to Browse"
    override val settingsReshowOnboarding = "Show setup guide again"
    override val settingsReshowOnboardingDesc = "Re-run the welcome setup guide"
}
