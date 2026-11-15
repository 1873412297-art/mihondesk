package mihon.desktop.preferences

import mihon.desktop.i18n.AppLanguage
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.security.PinHasher
import mihon.desktop.ui.theme.DesktopAppTheme
import java.util.Properties

// Field-by-field mapping between DesktopPreferences and preferences.properties, grouped by feature
// so each reader/writer stays small. Keys, legacy aliases and defaults are part of the file format.

/** Reads every stored preference; missing or malformed values fall back to their defaults. */
internal fun Properties.readDesktopPreferences(): DesktopPreferences = DesktopPreferences()
    .let { readAppearance(it) }
    .let { readLibrary(it) }
    .let { readDownloads(it) }
    .let { readSecurity(it) }
    .let { readBackground(it) }
    .let { readSyncAndBrowse(it) }

private fun Properties.readAppearance(into: DesktopPreferences): DesktopPreferences = into.copy(
    themeMode = enumValueOrDefault(getProperty("theme"), ThemeMode.System),
    appTheme = enumValueOrDefault(getProperty("app_theme"), DesktopAppTheme.DEFAULT),
    themeDarkAmoled = getProperty("theme_dark_amoled")?.toBooleanStrictOrNull() ?: false,
    lastDestination = enumValueOrDefault(
        getProperty("destination"),
        DesktopDestination.Library,
    ),
    windowPlacement = readWindowPlacement(),
    language = AppLanguage.fromCode(getProperty("language")),
    useSystemTitleBar = getProperty("window.use_system_title_bar")
        ?.toBooleanStrictOrNull() ?: false,
    onboardingCompleted = getProperty("onboarding.completed")?.toBooleanStrictOrNull() ?: false,
    splashEveryLaunch = getProperty("splash.every_launch")?.toBooleanStrictOrNull() ?: true,
)

private fun Properties.readLibrary(into: DesktopPreferences): DesktopPreferences = into.copy(
    libraryDisplayMode = getProperty("library.display_mode") ?: "ComfortableGrid",
    libraryGridSize = getProperty("library.grid_size")?.toFloatOrNull() ?: 180f,
    libraryDetailPaneWidth =
    getProperty("library.detail_pane_width")?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
    libraryBadgeUnread = getProperty("library.badge_unread")?.toBooleanStrictOrNull() ?: true,
    libraryBadgeDownloaded = getProperty("library.badge_downloaded")?.toBooleanStrictOrNull()
        ?: true,
    librarySortMode = getProperty("library.sort_mode") ?: "None",
    librarySortAscending = getProperty("library.sort_ascending")?.toBooleanStrictOrNull() ?: true,
    libraryFilterUnread = getProperty("library.filter_unread") ?: "Disabled",
    libraryFilterDownloaded = getProperty("library.filter_downloaded") ?: "Disabled",
    libraryFilterStarted = getProperty("library.filter_started") ?: "Disabled",
    libraryFilterCompleted = getProperty("library.filter_completed") ?: "Disabled",
    libraryFilterBookmarked = getProperty("library.filter_bookmarked") ?: "Disabled",
)

private fun Properties.readDownloads(into: DesktopPreferences): DesktopPreferences = into.copy(
    downloadStoragePath = getProperty("download.storage_path") ?: "",
    downloadParallelCount = getProperty("download.parallel_count")?.toIntOrNull() ?: 3,
    downloadPageParallelCount = firstInt(
        "download.page_parallel_count",
        "download_parallel_page_limit",
    ) ?: 5,
    downloadAhead = getProperty("download.ahead")?.toIntOrNull() ?: 0,
    deleteDownloadedRead = getProperty("download.delete_read")?.toBooleanStrictOrNull() ?: false,
    saveChapterAsCbz = getProperty("download.save_as_cbz")?.toBooleanStrictOrNull() ?: false,
    splitTallImages = getProperty("download.split_tall_images")?.toBooleanStrictOrNull() ?: false,
    autoDownloadNewChapters = getProperty("library.auto_download_new")
        ?.toBooleanStrictOrNull() ?: false,
    autoDownloadUnreadOnly = getProperty("download.new_unread_only")
        ?.toBooleanStrictOrNull() ?: false,
    autoDownloadCategories = firstLongSet("download.new_categories"),
    autoDownloadCategoriesExclude = firstLongSet("download.new_categories_exclude"),
)

private fun Properties.readSecurity(into: DesktopPreferences): DesktopPreferences {
    val storedAppLockHash = getProperty("security.app_lock.pin_hash") ?: ""
    val storedAppLockSalt = getProperty("security.app_lock.pin_salt") ?: ""
    val storedAppLockIterations = getProperty("security.app_lock.pin_iterations")
        ?.toIntOrNull() ?: 0
    val hasValidPinCredential = PinHasher.isValidCredential(
        storedAppLockHash,
        storedAppLockSalt,
        storedAppLockIterations,
    )
    val appLockEnabled =
        (getProperty("security.app_lock.enabled")?.toBooleanStrictOrNull() ?: false) &&
            hasValidPinCredential
    val appLockIdleTimeoutMinutes = getProperty("security.app_lock.idle_timeout_minutes")
        ?.toIntOrNull()
        ?.takeIf { it in 0..(24 * 60) }
        ?: 5
    return into.copy(
        incognitoMode = getProperty("security.incognito_mode")?.toBooleanStrictOrNull() ?: false,
        appLockEnabled = appLockEnabled,
        appLockPinHash = if (hasValidPinCredential) storedAppLockHash else "",
        appLockPinSalt = if (hasValidPinCredential) storedAppLockSalt else "",
        appLockPinIterations = if (hasValidPinCredential) storedAppLockIterations else 0,
        appLockOnStartup = getProperty("security.app_lock.lock_on_startup")
            ?.toBooleanStrictOrNull() ?: true,
        appLockIdleTimeoutMinutes = appLockIdleTimeoutMinutes,
    )
}

private fun Properties.readBackground(into: DesktopPreferences): DesktopPreferences = into.copy(
    backupIntervalHours = getProperty("backup.interval_hours")?.toIntOrNull() ?: 0,
    backupStoragePath = getProperty("backup.storage_path") ?: "",
    backupRetentionCount = getProperty("backup.retention_count")?.toIntOrNull() ?: 10,
    lastAutoBackupEpochMillis = getProperty("backup.last_epoch_millis")?.toLongOrNull() ?: 0L,
    backgroundTasksEnabled =
    getProperty("background.tasks_enabled")?.toBooleanStrictOrNull() ?: false,
    runInBackgroundOnClose = getProperty("background.run_in_background_on_close")
        ?.toBooleanStrictOrNull() ?: false,
    lastLibraryUpdateEpochMillis = getProperty("library.last_update_epoch_millis")
        ?.toLongOrNull() ?: 0L,
    libraryUpdateIntervalHours = getProperty("library.update_interval_hours")?.toIntOrNull() ?: 0,
    libraryUpdateSkipCompleted = getProperty("library.update_skip_completed")
        ?.toBooleanStrictOrNull() ?: true,
    libraryUpdateSkipUnread = getProperty("library.update_skip_unread")
        ?.toBooleanStrictOrNull() ?: false,
    libraryUpdateSkipStarted = getProperty("library.update_skip_started")
        ?.toBooleanStrictOrNull() ?: false,
    libraryUpdateSkipOutsideReleasePeriod = getProperty("library.update_skip_outside_release_period")
        ?.toBooleanStrictOrNull() ?: false,
    libraryUpdateCategories = firstLongSet(
        "library.update_categories",
        "library_update_categories",
    ),
    libraryUpdateCategoriesExclude = firstLongSet(
        "library.update_categories_exclude",
        "library_update_categories_exclude",
    ),
    libraryUpdateOnlyOnAcPower = getProperty("library.update_only_on_ac_power")
        ?.toBooleanStrictOrNull() ?: false,
)

private fun Properties.readSyncAndBrowse(into: DesktopPreferences): DesktopPreferences = into.copy(
    desktopNotificationsEnabled = getProperty("notifications.desktop_enabled")
        ?.toBooleanStrictOrNull() ?: true,
    desktopNotificationsHideContent = firstBoolean(
        "notifications.desktop_hide_content",
        "hide_notification_content",
    ) ?: false,
    syncEnabled = getProperty("sync.enabled")?.toBooleanStrictOrNull() ?: false,
    syncIntervalMinutes = getProperty("sync.interval_minutes")?.toIntOrNull() ?: 15,
    lastSyncEpochMillis = getProperty("sync.last_epoch_millis")?.toLongOrNull() ?: 0L,
    lastSyncMessage = getProperty("sync.last_message") ?: "",
    syncDeviceId = getProperty("sync.device_id") ?: "",
    showNsfwSources = firstBoolean("browse.show_nsfw", "show_nsfw") ?: false,
    hiddenSourceIds = firstLongSet("browse.hidden_sources", "hidden_sources"),
    globalSearchOnlyPinned =
    firstBoolean("browse.global_search_only_pinned", "global_search_only_pinned") ?: false,
)

/** Writes every preference field; keys of other features already in the file are left alone. */
internal fun Properties.writeDesktopPreferences(preferences: DesktopPreferences) {
    writeAppearance(preferences)
    writeLibrary(preferences)
    writeDownloads(preferences)
    writeSecurity(preferences)
    writeBackground(preferences)
    writeSyncAndBrowse(preferences)
}

private fun Properties.writeAppearance(preferences: DesktopPreferences) {
    setProperty("theme", preferences.themeMode.name)
    setProperty("app_theme", preferences.appTheme.name)
    setProperty("theme_dark_amoled", preferences.themeDarkAmoled.toString())
    setProperty("destination", preferences.lastDestination.name)
    setProperty("language", preferences.language.code)
    setProperty("window.use_system_title_bar", preferences.useSystemTitleBar.toString())
    setProperty("onboarding.completed", preferences.onboardingCompleted.toString())
    setProperty("splash.every_launch", preferences.splashEveryLaunch.toString())
    remove("window.x")
    remove("window.y")
    remove("window.width")
    remove("window.height")
    remove("window.maximized")
    preferences.windowPlacement?.let { placement ->
        setProperty("window.x", placement.x.toString())
        setProperty("window.y", placement.y.toString())
        setProperty("window.width", placement.width.toString())
        setProperty("window.height", placement.height.toString())
        setProperty("window.maximized", placement.maximized.toString())
    }
}

private fun Properties.writeLibrary(preferences: DesktopPreferences) {
    setProperty("library.display_mode", preferences.libraryDisplayMode)
    setProperty("library.grid_size", preferences.libraryGridSize.toString())
    setProperty("library.detail_pane_width", preferences.libraryDetailPaneWidth.toString())
    setProperty("library.badge_unread", preferences.libraryBadgeUnread.toString())
    setProperty("library.badge_downloaded", preferences.libraryBadgeDownloaded.toString())
    setProperty("library.sort_mode", preferences.librarySortMode)
    setProperty("library.sort_ascending", preferences.librarySortAscending.toString())
    setProperty("library.filter_unread", preferences.libraryFilterUnread)
    setProperty("library.filter_downloaded", preferences.libraryFilterDownloaded)
    setProperty("library.filter_started", preferences.libraryFilterStarted)
    setProperty("library.filter_completed", preferences.libraryFilterCompleted)
    setProperty("library.filter_bookmarked", preferences.libraryFilterBookmarked)
    setProperty(
        "library.auto_download_new",
        preferences.autoDownloadNewChapters.toString(),
    )
}

private fun Properties.writeDownloads(preferences: DesktopPreferences) {
    setProperty("download.storage_path", preferences.downloadStoragePath)
    setProperty("download.parallel_count", preferences.downloadParallelCount.toString())
    setProperty("download.page_parallel_count", preferences.downloadPageParallelCount.toString())
    setProperty("download.ahead", preferences.downloadAhead.toString())
    setProperty("download.delete_read", preferences.deleteDownloadedRead.toString())
    setProperty("download.save_as_cbz", preferences.saveChapterAsCbz.toString())
    setProperty("download.split_tall_images", preferences.splitTallImages.toString())
    setProperty("download.new_unread_only", preferences.autoDownloadUnreadOnly.toString())
    setProperty("download.new_categories", preferences.autoDownloadCategories.encodeLongSet())
    setProperty(
        "download.new_categories_exclude",
        preferences.autoDownloadCategoriesExclude.encodeLongSet(),
    )
}

private fun Properties.writeSecurity(preferences: DesktopPreferences) {
    setProperty("security.incognito_mode", preferences.incognitoMode.toString())
    setProperty("security.app_lock.enabled", preferences.appLockEnabled.toString())
    setProperty("security.app_lock.pin_hash", preferences.appLockPinHash)
    setProperty("security.app_lock.pin_salt", preferences.appLockPinSalt)
    setProperty("security.app_lock.pin_iterations", preferences.appLockPinIterations.toString())
    setProperty(
        "security.app_lock.lock_on_startup",
        preferences.appLockOnStartup.toString(),
    )
    setProperty(
        "security.app_lock.idle_timeout_minutes",
        preferences.appLockIdleTimeoutMinutes.toString(),
    )
}

private fun Properties.writeBackground(preferences: DesktopPreferences) {
    setProperty("backup.interval_hours", preferences.backupIntervalHours.toString())
    setProperty("backup.storage_path", preferences.backupStoragePath)
    setProperty("backup.retention_count", preferences.backupRetentionCount.toString())
    setProperty("backup.last_epoch_millis", preferences.lastAutoBackupEpochMillis.toString())
    setProperty("background.tasks_enabled", preferences.backgroundTasksEnabled.toString())
    setProperty(
        "background.run_in_background_on_close",
        preferences.runInBackgroundOnClose.toString(),
    )
    setProperty(
        "library.update_interval_hours",
        preferences.libraryUpdateIntervalHours.toString(),
    )
    setProperty(
        "library.update_skip_completed",
        preferences.libraryUpdateSkipCompleted.toString(),
    )
    setProperty(
        "library.update_skip_unread",
        preferences.libraryUpdateSkipUnread.toString(),
    )
    setProperty(
        "library.update_skip_started",
        preferences.libraryUpdateSkipStarted.toString(),
    )
    setProperty(
        "library.update_skip_outside_release_period",
        preferences.libraryUpdateSkipOutsideReleasePeriod.toString(),
    )
    setProperty("library.update_categories", preferences.libraryUpdateCategories.encodeLongSet())
    setProperty(
        "library.update_categories_exclude",
        preferences.libraryUpdateCategoriesExclude.encodeLongSet(),
    )
    setProperty(
        "library.update_only_on_ac_power",
        preferences.libraryUpdateOnlyOnAcPower.toString(),
    )
    setProperty(
        "library.last_update_epoch_millis",
        preferences.lastLibraryUpdateEpochMillis.toString(),
    )
}

private fun Properties.writeSyncAndBrowse(preferences: DesktopPreferences) {
    setProperty(
        "notifications.desktop_enabled",
        preferences.desktopNotificationsEnabled.toString(),
    )
    setProperty(
        "notifications.desktop_hide_content",
        preferences.desktopNotificationsHideContent.toString(),
    )
    setProperty("sync.enabled", preferences.syncEnabled.toString())
    setProperty("sync.interval_minutes", preferences.syncIntervalMinutes.toString())
    setProperty("sync.last_epoch_millis", preferences.lastSyncEpochMillis.toString())
    setProperty("sync.last_message", preferences.lastSyncMessage)
    setProperty("sync.device_id", preferences.syncDeviceId)
    LEGACY_SYNC_SERVER_KEYS.forEach(::remove)
    setProperty("browse.show_nsfw", preferences.showNsfwSources.toString())
    setProperty("browse.hidden_sources", preferences.hiddenSourceIds.encodeLongSet())
    setProperty("browse.global_search_only_pinned", preferences.globalSearchOnlyPinned.toString())
}
