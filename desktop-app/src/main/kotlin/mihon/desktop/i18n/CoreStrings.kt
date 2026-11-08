package mihon.desktop.i18n

import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.ui.browse.ExtensionInstallFailure

interface CoreStrings {
    val appName: String

    // Navigation
    fun destinationLabel(destination: DesktopDestination): String
    fun destinationShortLabel(destination: DesktopDestination): String

    // Library
    val libraryTitle: String
    val libraryImportBackup: String
    val libraryImportLocal: String
    val librarySearchPlaceholder: String
    val libraryManageCategories: String
    val libraryEmptyTitle: String
    val libraryEmptySubtitle: String
    fun libraryNoMatchTitle(query: String): String
    val libraryNoMatchSubtitle: String
    val libraryRetry: String
    fun libraryUnreadCount(count: Int): String
    val libraryAllCategory: String
    val libraryOpenRandom: String
    val libraryEmptyRandom: String

    // Library Display, Filter & Sort
    val libraryDisplayMode: String
    val libraryDisplayComfortable: String
    val libraryDisplayCompact: String
    val libraryDisplayCoverOnly: String
    val libraryDisplayList: String
    val libraryGridSize: String
    val libraryFilterAndSort: String
    val libraryFilterTab: String
    val libraryFilterCycleHint: String
    val librarySortTab: String
    val libraryFilterReset: String
    val libraryFilterUnread: String
    val libraryFilterDownloaded: String
    val libraryFilterStarted: String
    val libraryFilterCompleted: String
    val libraryFilterBookmarked: String
    val librarySortDefault: String
    val librarySortAlphabetical: String
    val librarySortLastRead: String
    val librarySortLastUpdate: String
    val librarySortUnreadCount: String
    val librarySortTotalChapters: String
    val librarySortDateAdded: String
    val librarySortAscending: String
    val librarySortDescending: String

    // Library Batch Actions
    val libraryBatchSelect: String
    fun libraryBatchSelected(count: Int): String
    val libraryBatchSelectAll: String
    val libraryBatchDeselectAll: String
    val libraryBatchChangeCategory: String
    val libraryBatchMarkRead: String
    val libraryBatchMarkUnread: String
    val libraryBatchDownload: String
    val libraryBatchDownloadNext1: String
    val libraryBatchDownloadNext5: String
    val libraryBatchDownloadAllUnread: String
    val libraryBatchRemove: String
    val libraryBatchRemoveConfirmTitle: String
    fun libraryBatchRemoveConfirmMessage(count: Int): String
    val libraryBatchDone: String

    // Manga Detail
    fun mangaDetailSource(name: String): String
    val mangaDetailStatusOngoing: String
    val mangaDetailStatusCompleted: String
    val mangaDetailStatusUnknown: String
    val mangaDetailInLibrary: String
    val mangaDetailAddToLibrary: String
    val mangaDetailOrganizationRequiresLibrary: String
    val mangaDetailCategories: String
    val mangaDetailTracking: String
    fun mangaDetailResume(chapter: String): String
    fun mangaDetailStart(chapter: String): String
    val mangaDetailNoChapters: String
    val mangaDetailBack: String
    val mangaDetailChangeCover: String
    val mangaDetailResetCover: String
    val mangaDetailEditInfo: String
    val mangaDetailAuthor: String
    val mangaDetailArtist: String
    val mangaDetailStatus: String
    val mangaDetailGenres: String
    val mangaDetailNotes: String
    val mangaDetailResetToSource: String
    val mangaDetailSave: String
    val mangaDetailShowMore: String
    val mangaDetailShowLess: String
    val mangaDetailCopyLink: String
    val mangaDetailLinkCopied: String
    fun mangaDetailMissingChapters(count: Int): String

    // Missing Source Banner & Dialog
    fun missingSourceBannerTitle(extName: String, mangaCount: Int): String
    val missingSourceBannerNotFound: String
    val missingSourceBannerInstall: String
    val missingSourceBannerIgnore: String
    val missingSourceUnknownExtension: String
    fun missingSourceDialogTitle(mangaCount: Int, extCount: Int): String
    val missingSourceDialogInstallAll: String
    val missingSourceDialogLater: String
    fun missingSourceDialogNotFound(mangaCount: Int): String
    val missingSourceInstalling: String

    // Chapters & Chapter Actions
    val chapters: String
    val sortSourceOrder: String
    val sortChapterNumber: String
    val sortUploadDate: String
    val filterUnread: String
    val filterRead: String
    val filterUnreadOnly: String
    val filterReadOnly: String
    val filterDownloaded: String
    val filterDownloadedOnly: String
    val filterNotDownloadedOnly: String
    val filterBookmarked: String
    val filterBookmarkedOnly: String
    val filterNotBookmarkedOnly: String
    val markAsRead: String
    val markAsUnread: String
    val markPreviousAsRead: String
    val downloadChapter: String
    val downloadNext1: String
    val downloadNext5: String
    val downloadNext10: String
    val downloadNext25: String
    val downloadUnread: String
    val downloadAll: String
    val readerPreviousChapter: String
    val readerNextChapter: String
    val readerChapterList: String
    val readerCurrentChapter: String
    val readerChapterDrawerSearch: String
    val readerChapterDrawerNoResults: String
    val mangaDetailSearchChaptersPlaceholder: String
    val deleteDownload: String
    val bookmarkChapter: String
    val removeBookmark: String
    val chapterBatchSelect: String
    fun chapterBatchSelected(count: Int): String
    val chapterBatchSelectAll: String
    val chapterBatchInvert: String
    val chapterBatchBookmark: String
    val chapterBatchRemoveBookmark: String
    val chapterBatchMarkAsRead: String
    val chapterBatchMarkAsUnread: String
    val chapterBatchDownload: String
    val chapterBatchDeleteDownload: String
    val chapterOpenInWebView: String
    val mangaDetailCoverView: String
    val mangaDetailCoverSave: String
    val mangaDetailCoverSaved: String
    val mangaNotesTitle: String
    val mangaNotesEdit: String
    val mangaNotesSave: String
    val mangaNotesPlaceholder: String

    // Storage & Cache Cleaner
    val storageCleanerTitle: String
    val storageCleanerDescription: String
    val storageCleanerDownloadSize: String
    val storageUsageCache: String
    val storageUsageDatabase: String
    val storageUsageExtensions: String
    val storageUsageCovers: String
    val storageUsageLogs: String
    val storageUsageBackups: String
    val clearDatabaseTitle: String
    val clearDatabaseDescription: String
    val clearDatabaseKeepRead: String
    val clearDatabaseEmpty: String
    val clearDatabaseAction: String
    val clearDatabaseConfirm: String
    val clearDatabaseReadWarning: String
    val clearDatabaseFailed: String
    val downloadNewUnreadOnly: String
    val downloadNewCategories: String
    val downloadCategoryAny: String
    val downloadCategoryInclude: String
    val downloadCategoryExclude: String
    val downloadNoCategories: String
    fun clearDatabaseResult(count: Int): String
    val storageCleanerClearRead: String
    val storageCleanerClearReadSuccess: String
    val storageCleanerClearImageCache: String
    val storageCleanerClearImageCacheSuccess: String

    // Updates
    val updatesTitle: String
    val updatesCheckButton: String
    val updatesChecking: String
    val updatesEmptyTitle: String
    val updatesEmptySubtitle: String
    val updatesReadButton: String
    val updatesUpcomingButton: String
    val updatesCancelUpdate: String
    fun updatesResultSummary(mangaCount: Int, newChapterCount: Int): String
    val updatesFailedNotice: String
    fun updatesRetryAfter(time: String): String
    val updatesCancelledNotice: String

    // History
    val historyTitle: String
    val historyHeaderSubtitle: String
    val historySearchPlaceholder: String
    val historyClearAll: String
    val historyEmptyTitle: String
    val historyEmptySubtitle: String
    val historyToday: String
    val historyYesterday: String
    val historyResumeButton: String
    val historyDeleteButton: String
    val historyClearDialogTitle: String
    val historyClearDialogMessage: String
    val historyNoMatch: String
    fun historyGroupTitle(title: String): String

    // Downloads
    val downloadsTitle: String
    val downloadsPauseAll: String
    val downloadsResumeAll: String
    val downloadsClearCompleted: String
    val downloadsEmptyTitle: String
    val downloadsEmptySubtitle: String
    val downloadsStatusDownloading: String
    val downloadsStatusPaused: String
    val downloadsStatusCompleted: String
    val downloadsStatusError: String
    val downloadsCancel: String
    val downloadsRetry: String
    val downloadsRead: String
    fun downloadsActiveSpeed(activeCount: Int, speedText: String): String

    // Browse
    val browseTitle: String
    val browseTabSources: String
    val browseTabExtensions: String
    val browseTabMigration: String
    val browseManageRepositories: String
    val browseRefresh: String
    val browseInstall: String
    val browseUninstall: String
    val browseInstalledBadge: String
    val browseSearchPlaceholder: String
    val browseNetworkDomainsHeader: String
    val browseGlobalSearch: String
    val browseInstallFromFile: String
    val browseUpdateAll: String
    val browseOpenWebsite: String
    val browseLocalBadge: String
    fun browseSourceId(id: String): String

    // Extension Details & Source Preferences
    val extensionInfo: String
    val extensionOpenRepo: String
    val extensionEnableAll: String
    val extensionDisableAll: String
    val extensionClearCookies: String
    val extensionVersion: String
    val extensionLanguage: String
    val extensionAgeRating: String
    val extensionNsfwShort: String
    val extensionNsfwWarning: String
    val extensionUninstall: String
    val extensionUpdate: String
    val extensionTrust: String
    val extensionRevoke: String
    val extensionUntrusted: String
    val extensionObsolete: String
    val extensionObsoleteWarning: String
    val extensionIncognitoMode: String
    val extensionIncognitoSummary: String
    val extensionDebugInfoCopied: String
    val extensionCookiesCleared: String

    /** Label for the store (repository) an extension was listed by. */
    fun extensionStoreLabel(store: String): String
    fun extensionInstallFailureTitle(failure: ExtensionInstallFailure): String
    fun extensionInstallFailureHint(failure: ExtensionInstallFailure): String
    val sourcePreferencesTitle: String
    val sourcePreferencesEmpty: String
    val sourcePreferencesUnsupported: String
    val sourcePreferencesSaveError: String
    val actionOk: String
    val actionCancel: String
    val actionSettings: String

    // Reader
    val readerMangaDetails: String
    val readerModeSingleLtr: String
    val readerModeSingleRtl: String
    val readerModeWebtoon: String
    val readerScaleFitScreen: String
    val readerScaleFitWidth: String
    val readerScaleFitHeight: String
    val readerScaleOriginal: String
    val readerEscapeHint: String
    val readerColorFilter: String
    val readerFilterNone: String
    val readerFilterInvert: String
    val readerFilterGrayscale: String
    val readerFilterInvertGrayscale: String
    val readerFilterSepia: String
    val readerFilterNight: String
    val readerFilterCustom: String
    val readerCustomHue: String
    val readerCustomBrightness: String
    val readerCustomContrast: String
    val readerDimming: String
    fun readerCustomHueLabel(value: Int): String = "$readerCustomHue: $value°"
    fun readerCustomBrightnessLabel(value: Int): String = "$readerCustomBrightness: $value%"
    fun readerCustomContrastLabel(value: Int): String = "$readerCustomContrast: $value%"
    fun readerDimmingLabel(value: Int): String = "$readerDimming: $value%"
    val readerBackgroundColor: String
    val readerBgDarkGray: String
    val readerBgBlack: String
    val readerBgWhite: String
    val readerBgWarmCream: String
    val readerCropBorders: String
    val readerCropBordersWebtoon: String
    val readerWebtoonMaxWidth: String
    val readerWebtoonSidePadding: String
    val readerKeepScreenOn: String
    val readerPageFlash: String
    val readerWebtoonPreventDownsizing: String
    val readerWebtoonDoubleTapZoom: String
}
