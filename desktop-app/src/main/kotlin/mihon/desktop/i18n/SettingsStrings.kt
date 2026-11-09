package mihon.desktop.i18n

import mihon.desktop.ui.theme.DesktopAppTheme

interface SettingsStrings {
    // Settings
    val settingsTitle: String
    val settingsSearchPlaceholder: String
    val settingsSearchNoResults: String
    fun settingsSearchResultsCount(count: Int): String
    val settingsSectionGeneral: String
    val settingsSectionAppearance: String
    val settingsSectionReader: String
    val settingsSectionDownloads: String
    val settingsSectionTracking: String
    val settingsSectionBackup: String
    val settingsSectionAdvanced: String

    // Settings - General
    val settingsLanguageTitle: String
    val settingsLanguageSystem: String
    val settingsLanguageSimplifiedChinese: String
    val settingsLanguageTraditionalChinese: String
    val settingsLanguageEnglish: String
    val settingsAppInfoTitle: String
    fun settingsVersionLabel(version: String): String
    fun settingsPlatformLabel(platform: String): String

    // Settings - Appearance
    val settingsThemeModeTitle: String
    val settingsThemeSystem: String
    val settingsThemeLight: String
    val settingsThemeDark: String
    val settingsAppThemeTitle: String
    val settingsThemeAmoledTitle: String
    val settingsThemeAmoledSubtitle: String
    val settingsSplashEveryLaunchTitle: String
    val settingsSplashEveryLaunchSubtitle: String
    fun appThemeName(theme: DesktopAppTheme): String

    // Settings - Reader
    val settingsDefaultReadingMode: String
    val settingsDefaultScaleMode: String
    val settingsMouseWheelBehavior: String
    val settingsWheelScrollPage: String
    val settingsWheelFlipPage: String
    val settingsInvertWheel: String
    val settingsPageTransitions: String
    val settingsAnimateTransitions: String
    val settingsDoubleSpread: String

    // Settings - Downloads
    val settingsDownloadLocation: String
    val settingsDefaultStorageFolder: String
    val settingsParallelDownloads: String

    // Settings - Tracking
    val settingsTrackingTitle: String
    val settingsTrackingDescription: String
    fun settingsConnectedTrackers(count: Int): String
    val settingsTrackingPublicServices: String
    val settingsTrackingSelfHosted: String

    // Settings - Network
    val networkSettingsTitle: String
    val networkSettingsDescription: String
    val networkSettingsProxySystem: String
    val networkSettingsProxyDirect: String
    val networkSettingsProxyHost: String
    val networkSettingsPort: String
    val networkSettingsConnectTimeout: String
    val networkSettingsReadTimeout: String
    val networkSettingsSaved: String
    val networkSettingsSave: String

    // Settings - Backup
    val settingsBackupTitle: String
    val gdriveTitle: String
    val gdriveDescription: String
    val gdriveSignIn: String
    val gdriveSigningIn: String
    val gdriveSignOut: String
    val gdriveStatusSignedIn: String
    val gdriveStatusSignedOut: String
    val gdriveSyncNow: String
    val gdriveSyncing: String
    val gdriveLastSync: String
    val gdriveErrNotConfigured: String
    val gdriveErrCancelled: String
    val gdriveErrExpired: String
    val gdriveErrNetwork: String
    val gdriveErrCorrupt: String
    val gdriveErrUnknown: String
    val settingsBackupDescription: String
    val settingsExportBackupButton: String
    val settingsImportBackupButton: String

    // Settings - Sync
    val syncNever: String
    val runInBackgroundTitle: String
    val systemTitleBarTitle: String
    val systemTitleBarSummary: String
    val titleBarSystemMenu: String
    val runInBackgroundSummary: String
    val trayShow: String
    val trayQuit: String
    val trayHiddenTitle: String
    val trayHiddenHint: String

    // Settings - Advanced
    val settingsDiagnosticsTitle: String
    val settingsRunIntegrityCheck: String
    val settingsExportDiagnosticBundle: String
    fun settingsDatabaseIntegrity(status: String): String
    fun settingsOsInfo(info: String): String
    fun settingsJavaInfo(info: String): String
    fun settingsLogFilesCount(count: Int): String
    fun settingsBundleExportedTo(path: String): String

    // About
    val aboutTitle: String
    val aboutSubtitle: String
    fun aboutVersion(version: String): String
    val aboutRuntime: String
    val aboutDatabase: String
    val aboutProtocol: String
    val aboutLicensesTitle: String
    val aboutLicensesDesc1: String
    val aboutLicensesDesc2: String

    // Dialogs
    val dialogOk: String
    val dialogDone: String
    val dialogClose: String
    val dialogCancel: String
    val importDialogTitle: String
    val importDialogProgress: String
    val importDialogCompleteTitle: String
    val importDialogFailedTitle: String
    val importDialogRejectedTitle: String
    val backupDialogTitle: String
    fun backupExportSuccess(path: String): String
    fun backupExportFailed(err: String): String

    // Network Error Mapping (Cloudflare)
    val networkErrorCloudflare1005: String
    val networkErrorCloudflare1015: String
    val networkErrorCloudflare1016: String
    val networkErrorCloudflare1020: String
    fun networkErrorCloudflareOther(code: String): String

    // Incognito mode
    val incognitoTitle: String
    val incognitoBannerText: String
    val incognitoDisable: String
    val incognitoDescription: String

    // Stats
    val statsTitle: String
    val statsOverview: String
    val statsTotalManga: String
    val statsReadDuration: String
    val statsCompletedManga: String
    val statsChapters: String
    val statsTotalChapters: String
    val statsReadChapters: String
    val statsUnreadChapters: String
    val statsReadPercentage: String
    val statsTopGenres: String
    val statsNoGenres: String
    val statsStatuses: String
    val statsStatusOngoing: String
    val statsStatusCompleted: String
    val statsStatusHiatus: String
    val statsStatusCancelled: String
    val statsStatusUnknown: String
    val statsReadingProgress: String
    val statsProgressUnread: String
    val statsProgressInProgress: String
    val statsProgressFinished: String
    val statsCategories: String
    val statsNoCategories: String
    val statsTrackers: String
    val statsTrackedTitles: String
    val statsMeanScore: String
    val statsActiveTrackers: String
    val statsRefresh: String

    // Backup Automation
    val backupAutoTitle: String
    val backupAutoDescription: String
    val backupInterval: String
    val backupIntervalOff: String
    val backupInterval6Hours: String
    val backupInterval12Hours: String
    val backupIntervalDaily: String
    val backupInterval2Days: String
    val backupIntervalWeekly: String
    val backupLocation: String
    val backupLocationDefault: String
    val backupRetention: String
    val backupLastAutoBackup: String
    val backupNever: String
    val backupNow: String

    // Library Update & Automation
    val libraryUpdateTitle: String
    val libraryUpdateNow: String
    val libraryUpdating: String
    fun coverRepairAction(count: Int): String
    val coverRepairRunning: String
    val libraryUpdateInterval: String
    val libraryUpdateIntervalManual: String
    val libraryUpdateInterval6Hours: String
    val libraryUpdateInterval12Hours: String
    val libraryUpdateIntervalDaily: String
    val libraryUpdateInterval2Days: String
    val libraryUpdateIntervalWeekly: String
    val libraryUpdateSkipCompleted: String
    val libraryUpdateSkipUnread: String
    val libraryUpdateSkipOutsideReleasePeriod: String
    val libraryAutoDownloadNew: String
    val libraryUpdateOnlyOnAcPower: String
    val notificationsDesktopEnabled: String
    val libraryLastUpdate: String

    // Upcoming
    val upcomingMonthPattern: String

    // Web & Cookie Management
    val cookieManagerTitle: String
    val cookieManagerDescription: String
    val cookieManagerButton: String
    val openInBrowser: String

    // Categories
    val categoryManageTitle: String
    val categoryNewNameLabel: String
    val categoryAdd: String
    val categoryEmpty: String
    val categoryRename: String
    val categoryDelete: String
    val categoryRenameTitle: String
    val categoryNameLabel: String
    val categorySave: String
    val categorySetTitle: String
    val categoryNoneExist: String
    val categoryNoneCreated: String

    // Edit Manga Info
    val mangaDetailTitleLabel: String
    val mangaDetailStatusLicensed: String
    val mangaDetailStatusPublishingFinished: String
    val mangaDetailGenresLabel: String
    val mangaDetailDescriptionLabel: String
    val mangaDetailSaveSuccess: String
    fun mangaDetailStatusOption(status: Long): String

    // Cookie & Clearance Manager
    fun cookieConfiguredDomains(count: Int): String
    val cookieAddDomain: String
    val cookieNoCustomDomains: String
    fun cookieCountSubtitle(count: Int, hasCustomUa: Boolean): String
    val cookieDomainLabel: String
    val cookieRawCookiesLabel: String
    val cookieCustomUaLabel: String
    val cookieDelete: String
    val cookieSave: String
    fun cookieRemovedStatus(domain: String): String
    fun cookieSavedStatus(count: Int, domain: String): String
}
