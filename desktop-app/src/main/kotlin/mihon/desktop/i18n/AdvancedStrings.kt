package mihon.desktop.i18n

import mihon.desktop.reader.ReaderBackgroundColor
import mihon.desktop.reader.ReaderClickAction
import mihon.desktop.reader.ReaderColorFilter
import mihon.desktop.reader.ReaderPageTransition
import mihon.desktop.reader.ReaderWheelBehavior
import mihon.desktop.track.TrackStatus
import mihon.desktop.ui.browse.SourceListingMode
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.session.ReaderSessionError

interface AdvancedStrings {
    // Tracker & Tracking
    fun trackerConnectTitle(name: String): String
    fun trackerAuthTitle(name: String): String
    fun trackerLoginTitle(name: String): String
    val trackerServerUrl: String
    val trackerUsername: String
    val trackerPasswordOrApiKey: String
    fun trackerAuthUrl(url: String): String
    val trackerToken: String
    val trackerAccountName: String
    val trackerPassword: String
    val trackerConnect: String
    val trackerLogin: String
    val trackerLoginFailed: String
    val trackerServerAuthHelp: String
    val trackingRequestFailed: String
    fun trackingTitle(mangaTitle: String): String
    val trackingNotTracking: String
    val trackingNotLoggedIn: String
    val trackingTrack: String
    val trackingEdit: String
    val trackingRemove: String
    fun trackingSearchTitle(name: String): String
    val trackingSearch: String
    fun trackingTotalChapters(count: Long): String
    fun trackingEditTitle(title: String): String
    val trackingChaptersRead: String
    val trackingScore: String
    fun trackStatusLabel(status: TrackStatus): String

    // Reader Chrome, Settings & Messages
    val readerFullscreen: String
    val readerBorderless: String
    val readerShortcutsTitle: String
    val readerShortcutsHelp: String
    fun readerCropToggle(active: Boolean): String
    fun readerCoverOffsetToggle(active: Boolean): String
    val readerModeDualLtr: String
    val readerModeDualRtl: String
    val readerModeVertical: String
    fun readerModeLabel(mode: ReadingMode): String
    fun readerScaleLabel(scale: ScaleMode): String
    fun readerFilterLabel(filter: ReaderColorFilter): String
    fun readerBackgroundLabel(bg: ReaderBackgroundColor): String
    val readerPageTransitions: String
    fun readerPageTransitionLabel(transition: ReaderPageTransition): String
    val readerDualPageSplit: String
    fun readerDualPageSplitLabel(split: mihon.reader.layout.DualPageSplit): String
    val readerDualPageRotateToFit: String
    val readerSettingsDialogTitle: String
    val readerSettingsTabReading: String
    val readerSettingsTabGeneral: String
    val readerSettingsTabFilter: String
    val readerChapterTransitions: String
    val readerAlwaysShowChapterTransition: String
    val readerSkipReadChapters: String
    val readerSkipFilteredChapters: String
    val readerSkipDuplicateChapters: String
    val readerClickRegions: String
    val readerRegionLeft: String
    val readerRegionCenter: String
    val readerRegionRight: String
    fun readerActionLabel(action: ReaderClickAction): String
    fun readerLeftBoundary(percent: Int): String
    fun readerCenterBoundary(percent: Int): String
    fun readerWheelLabel(behavior: ReaderWheelBehavior): String
    val readerReserveCover: String
    val readerCropBordersPaged: String
    val readerWebtoonLayout: String
    val readerWebtoonWidthFull: String
    fun readerWebtoonWidthDp(width: Int): String
    fun readerWebtoonSidePaddingPercent(padding: Int): String
    val readerLoadingChapter: String
    val readerClosed: String
    fun readerErrorMessage(error: ReaderSessionError): String

    // Browse, Sources, Extensions, Migration, Filters, Global Search
    fun browseSourceLanguage(lang: String): String
    val browseSourcePopular: String
    val browseSourceLatest: String
    val browseSearchTitlesPlaceholder: String
    val browseSearchButton: String
    fun browseFiltersButton(count: Int): String
    val browseNoMangaFound: String
    fun browseSourceUnsupportedOperation(mode: SourceListingMode): String
    val browseInLibraryBadge: String
    val browsePrevPage: String
    fun browsePageNumber(page: Int): String
    val browseNextPage: String
    fun browseInstallExtensionTitle(name: String): String
    fun browsePackageLabel(pkg: String): String
    fun browseVersionLabel(version: String): String
    fun browseLanguageLabel(lang: String): String
    val browseNetworkPermissionNotice: String
    val browseTrustAndInstall: String
    val browseAddRepository: String
    val browseConfiguredRepositories: String
    val browseRemoveRepository: String
    val browseSourceBrowse: String
    val browsePin: String
    val browseMoreActions: String
    val browsePinnedSection: String
    val browseUnpin: String
    val browseUpdate: String
    val browseDisable: String
    val browseEnable: String
    fun onlineDetailSource(name: String, lang: String): String
    fun onlineDetailAuthor(author: String): String
    fun onlineDetailArtist(artist: String): String
    fun onlineDetailGenres(genres: String): String
    fun onlineDetailChapters(count: Int): String
    fun onlineDetailScanlator(scanlator: String): String
    val migrateSelectSourceHeader: String
    val migrateNoMangaInLibrary: String
    fun migrateMangaCount(count: Int): String
    val migrateViewManga: String
    val migrateBackToSources: String
    val migrateSelectMangaHeader: String
    val migrateAction: String
    fun migrateDialogTitle(title: String): String
    val migrateDialogSubtitle: String
    val migrateNoOtherSources: String
    val migrateTargetSourceLabel: String
    val migrateSearchPlaceholder: String
    val migrateSelectedBadge: String
    val migrateConfirm: String
    val migrateAllButton: String
    val migrateAllDialogTitle: String
    fun migrateAllDialogSubtitle(count: Int, sourceName: String): String
    val migrateAutoMatchButton: String
    val migrateAutoMatching: String
    val migrateExactMatchBadge: String
    val migrateHighConfidenceBadge: String
    val migrateBatchProgressCardTitle: String
    fun migrateBatchProgress(current: Int, total: Int, title: String): String
    fun migrateBatchCounts(success: Int, failed: Int): String
    val migrateBatchCancel: String
    val migrateBatchReportTitle: String
    fun migrateBatchReportSummary(success: Int, failed: Int): String
    fun migrateBatchAllSuccess(count: Int, targetSource: String): String
    fun migrateBatchCancelled(success: Int, total: Int): String
    fun migrateSuccessSnackbar(title: String, targetSource: String): String
    fun migrateFailedSnackbar(title: String, error: String): String
    val migrateFailureNoMatch: String
    val migrateFailureAmbiguous: String
    fun migrateFailureError(error: String): String
    val migrateBatchFailuresListHeader: String
    val migrateManualMigrate: String
    val filterDialogTitle: String
    val filterReset: String
    val filterNoAvailable: String
    val filterApply: String
    val filterAscending: String
    val filterDescending: String
    val globalSearchEnterQuery: String
    val globalSearchNoSources: String
    val globalSearchViewAll: String
    fun globalSearchError(error: String): String
    val globalSearchNoResults: String

    // Settings & Diagnostics & Reports
    val settingsDownloadCustomPath: String
    val settingsDownloadCustomPathPlaceholder: String
    val settingsDownloadChooseFolder: String
    val settingsDownloadUseDefault: String
    fun settingsDownloadActivePath(path: String): String
    val settingsDownloadRestartRequired: String
    val settingsDownloadAheadTitle: String
    val settingsDownloadAheadDesc: String
    val settingsDownloadAheadDisabled: String
    fun settingsDownloadAheadChapters(count: Int): String
    val settingsDeleteReadChaptersTitle: String
    val settingsDeleteReadChaptersDesc: String
    val settingsSaveChapterAsCbzTitle: String
    val settingsSaveChapterAsCbzDesc: String
    val settingsSplitTallImagesTitle: String
    val settingsSplitTallImagesDesc: String
    fun settingsTrackerLoggedInAs(user: String, server: String?): String
    val settingsTrackerLogout: String
    fun settingsLibraryUpdateResult(checked: Int, newChapters: Int): String
    val settingsLibraryUpdateCompleted: String
    fun settingsLibraryUpdateFailed(msg: String): String
    val readerUnavailable: String
    val readerOpeningChapter: String
    fun importReportTitle(id: Long): String
    fun importReportManga(inserted: Long, merged: Long): String
    fun importReportChapters(inserted: Long, merged: Long): String
    fun importReportCategories(count: Long): String
    fun importReportPreferences(imported: Long, skipped: Long): String
    fun importReportSkipCategories(categories: String): String
    fun importReportCategory(category: String): String
    val mangaDetailBackToLibrary: String

    // Phase 2d: Browse & Search Options
    val browseGlobalSearchOnlyPinned: String
    val browseHideSource: String
    fun browseSourceHidden(name: String): String
    val settingsResetHiddenSources: String
    val settingsResetHiddenSourcesDesc: String
    val settingsResetHiddenSourcesSuccess: String
    val browseFilterLanguageAll: String
    val settingsShowNsfw: String
    val settingsShowNsfwDesc: String

    // Phase 2d: Backup Granularity
    val backupOptionsTitleExport: String
    val backupOptionsTitleRestore: String
    val backupOptionsSubtitle: String
    val backupOptionChapterState: String
    val backupOptionCategories: String
    val backupOptionTracking: String
    val backupOptionHistory: String
    val backupOptionReadProgress: String
    val backupOptionSettings: String
    val backupOptionsConfirmExport: String
    val backupOptionsConfirmRestore: String

    // Phase 2d: Onboarding
    val onboardingWelcomeTitle: String
    val onboardingWelcomeDesc: String
    val onboardingStepLanguageAndTheme: String
    val onboardingStepRepo: String
    val onboardingStepRepoDesc: String
    val onboardingStepBackup: String
    val onboardingStepBackupDesc: String
    val onboardingRepoUrlPlaceholder: String
    val onboardingRepoAdd: String
    val onboardingBackupPathLabel: String
    val onboardingBackupPathBrowse: String
    val onboardingBackupIntervalLabel: String
    val onboardingBtnNext: String
    val onboardingBtnPrev: String
    val onboardingBtnSkip: String
    val onboardingBtnFinish: String
    val onboardingGoToBrowse: String
    val settingsReshowOnboarding: String
    val settingsReshowOnboardingDesc: String
}
