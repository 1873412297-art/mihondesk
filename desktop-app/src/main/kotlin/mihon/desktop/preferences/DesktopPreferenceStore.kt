package mihon.desktop.preferences

import mihon.desktop.i18n.AppLanguage
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.security.PinHasher
import mihon.desktop.ui.theme.DesktopAppTheme
import mihon.desktop.window.WindowPlacement
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.BasicFileAttributes
import java.util.Properties

class DesktopPreferenceStore(private val file: Path) {

    private data class FileStamp(val lastModifiedMillis: Long, val size: Long)

    @Volatile
    private var cachedProperties: Properties? = null

    @Volatile
    private var cachedPreferences: DesktopPreferences? = null

    @Volatile
    private var cacheStamp: FileStamp? = null

    @Synchronized
    fun invalidateCache() {
        cachedProperties = null
        cachedPreferences = null
        cacheStamp = null
    }

    private fun currentStamp(): FileStamp = try {
        val attributes = Files.readAttributes(file, BasicFileAttributes::class.java)
        FileStamp(attributes.lastModifiedTime().toMillis(), attributes.size())
    } catch (_: IOException) {
        FileStamp(-1L, -1L)
    }

    private fun getOrReadProperties(): Properties {
        // A single stat per read keeps the cache honest across instances and external
        // writers while still avoiding a full file read + parse on hot paths.
        val stamp = currentStamp()
        if (stamp == cacheStamp) cachedProperties?.let { return it }
        synchronized(this) {
            val secondStamp = currentStamp()
            if (secondStamp == cacheStamp) cachedProperties?.let { return it }
            val loaded = readProperties()
            cachedProperties = loaded
            cacheStamp = secondStamp
            return loaded
        }
    }

    /** Apply an edit to the latest snapshot under the same lock as other preference writers. */
    @Synchronized
    fun updatePreferences(transform: (DesktopPreferences) -> DesktopPreferences): DesktopPreferences {
        val updated = transform(load())
        save(updated)
        return updated
    }

    @Synchronized
    fun load(): DesktopPreferences {
        if (currentStamp() == cacheStamp) cachedPreferences?.let { return it }
        val properties = getOrReadProperties()
        val storedAppLockHash = properties.getProperty("security.app_lock.pin_hash") ?: ""
        val storedAppLockSalt = properties.getProperty("security.app_lock.pin_salt") ?: ""
        val storedAppLockIterations = properties.getProperty("security.app_lock.pin_iterations")
            ?.toIntOrNull() ?: 0
        val hasValidPinCredential = PinHasher.isValidCredential(
            storedAppLockHash,
            storedAppLockSalt,
            storedAppLockIterations,
        )
        val appLockEnabled =
            (properties.getProperty("security.app_lock.enabled")?.toBooleanStrictOrNull() ?: false) &&
                hasValidPinCredential
        val appLockIdleTimeoutMinutes = properties.getProperty("security.app_lock.idle_timeout_minutes")
            ?.toIntOrNull()
            ?.takeIf { it in 0..(24 * 60) }
            ?: 5
        val loadedPreferences = DesktopPreferences(
            themeMode = enumValueOrDefault(properties.getProperty("theme"), ThemeMode.System),
            appTheme = enumValueOrDefault(properties.getProperty("app_theme"), DesktopAppTheme.DEFAULT),
            themeDarkAmoled = properties.getProperty("theme_dark_amoled")?.toBooleanStrictOrNull() ?: false,
            lastDestination = enumValueOrDefault(
                properties.getProperty("destination"),
                DesktopDestination.Library,
            ),
            windowPlacement = properties.readWindowPlacement(),
            language = AppLanguage.fromCode(properties.getProperty("language")),
            libraryDisplayMode = properties.getProperty("library.display_mode") ?: "ComfortableGrid",
            libraryGridSize = properties.getProperty("library.grid_size")?.toFloatOrNull() ?: 180f,
            libraryDetailPaneWidth =
            properties.getProperty("library.detail_pane_width")?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            libraryBadgeUnread = properties.getProperty("library.badge_unread")?.toBooleanStrictOrNull() ?: true,
            libraryBadgeDownloaded = properties.getProperty("library.badge_downloaded")?.toBooleanStrictOrNull()
                ?: true,
            librarySortMode = properties.getProperty("library.sort_mode") ?: "None",
            librarySortAscending = properties.getProperty("library.sort_ascending")?.toBooleanStrictOrNull() ?: true,
            libraryFilterUnread = properties.getProperty("library.filter_unread") ?: "Disabled",
            libraryFilterDownloaded = properties.getProperty("library.filter_downloaded") ?: "Disabled",
            libraryFilterStarted = properties.getProperty("library.filter_started") ?: "Disabled",
            libraryFilterCompleted = properties.getProperty("library.filter_completed") ?: "Disabled",
            libraryFilterBookmarked = properties.getProperty("library.filter_bookmarked") ?: "Disabled",
            downloadStoragePath = properties.getProperty("download.storage_path") ?: "",
            downloadParallelCount = properties.getProperty("download.parallel_count")?.toIntOrNull() ?: 3,
            downloadPageParallelCount = properties.firstInt(
                "download.page_parallel_count",
                "download_parallel_page_limit",
            ) ?: 5,
            downloadAhead = properties.getProperty("download.ahead")?.toIntOrNull() ?: 0,
            deleteDownloadedRead = properties.getProperty("download.delete_read")?.toBooleanStrictOrNull() ?: false,
            saveChapterAsCbz = properties.getProperty("download.save_as_cbz")?.toBooleanStrictOrNull() ?: false,
            splitTallImages = properties.getProperty("download.split_tall_images")?.toBooleanStrictOrNull() ?: false,
            incognitoMode = properties.getProperty("security.incognito_mode")?.toBooleanStrictOrNull() ?: false,
            appLockEnabled = appLockEnabled,
            appLockPinHash = if (hasValidPinCredential) storedAppLockHash else "",
            appLockPinSalt = if (hasValidPinCredential) storedAppLockSalt else "",
            appLockPinIterations = if (hasValidPinCredential) storedAppLockIterations else 0,
            appLockOnStartup = properties.getProperty("security.app_lock.lock_on_startup")
                ?.toBooleanStrictOrNull() ?: true,
            appLockIdleTimeoutMinutes = appLockIdleTimeoutMinutes,
            backupIntervalHours = properties.getProperty("backup.interval_hours")?.toIntOrNull() ?: 0,
            backupStoragePath = properties.getProperty("backup.storage_path") ?: "",
            backupRetentionCount = properties.getProperty("backup.retention_count")?.toIntOrNull() ?: 10,
            lastAutoBackupEpochMillis = properties.getProperty("backup.last_epoch_millis")?.toLongOrNull() ?: 0L,
            backgroundTasksEnabled =
            properties.getProperty("background.tasks_enabled")?.toBooleanStrictOrNull() ?: false,
            libraryUpdateIntervalHours = properties.getProperty("library.update_interval_hours")?.toIntOrNull() ?: 0,
            libraryUpdateSkipCompleted = properties.getProperty("library.update_skip_completed")
                ?.toBooleanStrictOrNull() ?: true,
            libraryUpdateSkipUnread = properties.getProperty("library.update_skip_unread")
                ?.toBooleanStrictOrNull() ?: false,
            libraryUpdateSkipStarted = properties.getProperty("library.update_skip_started")
                ?.toBooleanStrictOrNull() ?: false,
            libraryUpdateSkipOutsideReleasePeriod = properties.getProperty("library.update_skip_outside_release_period")
                ?.toBooleanStrictOrNull() ?: false,
            libraryUpdateCategories = properties.firstLongSet(
                "library.update_categories",
                "library_update_categories",
            ),
            libraryUpdateCategoriesExclude = properties.firstLongSet(
                "library.update_categories_exclude",
                "library_update_categories_exclude",
            ),
            libraryUpdateOnlyOnAcPower = properties.getProperty("library.update_only_on_ac_power")
                ?.toBooleanStrictOrNull() ?: false,
            autoDownloadNewChapters = properties.getProperty("library.auto_download_new")
                ?.toBooleanStrictOrNull() ?: false,
            autoDownloadUnreadOnly = properties.getProperty("download.new_unread_only")
                ?.toBooleanStrictOrNull() ?: false,
            autoDownloadCategories = properties.firstLongSet("download.new_categories"),
            autoDownloadCategoriesExclude = properties.firstLongSet("download.new_categories_exclude"),
            desktopNotificationsEnabled = properties.getProperty("notifications.desktop_enabled")
                ?.toBooleanStrictOrNull() ?: true,
            desktopNotificationsHideContent = properties.firstBoolean(
                "notifications.desktop_hide_content",
                "hide_notification_content",
            ) ?: false,
            lastLibraryUpdateEpochMillis = properties.getProperty("library.last_update_epoch_millis")
                ?.toLongOrNull() ?: 0L,
            syncEnabled = properties.getProperty("sync.enabled")?.toBooleanStrictOrNull() ?: false,
            syncIntervalMinutes = properties.getProperty("sync.interval_minutes")?.toIntOrNull() ?: 15,
            lastSyncEpochMillis = properties.getProperty("sync.last_epoch_millis")?.toLongOrNull() ?: 0L,
            lastSyncMessage = properties.getProperty("sync.last_message") ?: "",
            syncDeviceId = properties.getProperty("sync.device_id") ?: "",
            runInBackgroundOnClose = properties.getProperty("background.run_in_background_on_close")
                ?.toBooleanStrictOrNull() ?: false,
            useSystemTitleBar = properties.getProperty("window.use_system_title_bar")
                ?.toBooleanStrictOrNull() ?: false,
            onboardingCompleted = properties.getProperty("onboarding.completed")?.toBooleanStrictOrNull() ?: false,
            splashEveryLaunch = properties.getProperty("splash.every_launch")?.toBooleanStrictOrNull() ?: true,
            showNsfwSources = properties.firstBoolean("browse.show_nsfw", "show_nsfw") ?: false,
            hiddenSourceIds = properties.firstLongSet("browse.hidden_sources", "hidden_sources"),
            globalSearchOnlyPinned =
            properties.firstBoolean("browse.global_search_only_pinned", "global_search_only_pinned") ?: false,
        )
        cachedPreferences = loadedPreferences
        return loadedPreferences
    }

    @Synchronized
    fun save(preferences: DesktopPreferences) {
        val properties = Properties().apply { putAll(getOrReadProperties()) }
        properties.setProperty("theme", preferences.themeMode.name)
        properties.setProperty("app_theme", preferences.appTheme.name)
        properties.setProperty("theme_dark_amoled", preferences.themeDarkAmoled.toString())
        properties.setProperty("destination", preferences.lastDestination.name)
        properties.setProperty("language", preferences.language.code)
        properties.setProperty("library.display_mode", preferences.libraryDisplayMode)
        properties.setProperty("library.grid_size", preferences.libraryGridSize.toString())
        properties.setProperty("library.detail_pane_width", preferences.libraryDetailPaneWidth.toString())
        properties.setProperty("library.badge_unread", preferences.libraryBadgeUnread.toString())
        properties.setProperty("library.badge_downloaded", preferences.libraryBadgeDownloaded.toString())
        properties.setProperty("library.sort_mode", preferences.librarySortMode)
        properties.setProperty("library.sort_ascending", preferences.librarySortAscending.toString())
        properties.setProperty("library.filter_unread", preferences.libraryFilterUnread)
        properties.setProperty("library.filter_downloaded", preferences.libraryFilterDownloaded)
        properties.setProperty("library.filter_started", preferences.libraryFilterStarted)
        properties.setProperty("library.filter_completed", preferences.libraryFilterCompleted)
        properties.setProperty("library.filter_bookmarked", preferences.libraryFilterBookmarked)
        properties.setProperty("download.storage_path", preferences.downloadStoragePath)
        properties.setProperty("download.parallel_count", preferences.downloadParallelCount.toString())
        properties.setProperty("download.page_parallel_count", preferences.downloadPageParallelCount.toString())
        properties.setProperty("download.ahead", preferences.downloadAhead.toString())
        properties.setProperty("download.delete_read", preferences.deleteDownloadedRead.toString())
        properties.setProperty("download.save_as_cbz", preferences.saveChapterAsCbz.toString())
        properties.setProperty("download.split_tall_images", preferences.splitTallImages.toString())
        properties.setProperty("security.incognito_mode", preferences.incognitoMode.toString())
        properties.setProperty("security.app_lock.enabled", preferences.appLockEnabled.toString())
        properties.setProperty("security.app_lock.pin_hash", preferences.appLockPinHash)
        properties.setProperty("security.app_lock.pin_salt", preferences.appLockPinSalt)
        properties.setProperty("security.app_lock.pin_iterations", preferences.appLockPinIterations.toString())
        properties.setProperty(
            "security.app_lock.lock_on_startup",
            preferences.appLockOnStartup.toString(),
        )
        properties.setProperty(
            "security.app_lock.idle_timeout_minutes",
            preferences.appLockIdleTimeoutMinutes.toString(),
        )
        properties.setProperty("backup.interval_hours", preferences.backupIntervalHours.toString())
        properties.setProperty("backup.storage_path", preferences.backupStoragePath)
        properties.setProperty("backup.retention_count", preferences.backupRetentionCount.toString())
        properties.setProperty("backup.last_epoch_millis", preferences.lastAutoBackupEpochMillis.toString())
        properties.setProperty("background.tasks_enabled", preferences.backgroundTasksEnabled.toString())
        properties.setProperty(
            "background.run_in_background_on_close",
            preferences.runInBackgroundOnClose.toString(),
        )
        properties.setProperty("window.use_system_title_bar", preferences.useSystemTitleBar.toString())
        properties.setProperty(
            "library.update_interval_hours",
            preferences.libraryUpdateIntervalHours.toString(),
        )
        properties.setProperty(
            "library.update_skip_completed",
            preferences.libraryUpdateSkipCompleted.toString(),
        )
        properties.setProperty(
            "library.update_skip_unread",
            preferences.libraryUpdateSkipUnread.toString(),
        )
        properties.setProperty(
            "library.update_skip_started",
            preferences.libraryUpdateSkipStarted.toString(),
        )
        properties.setProperty(
            "library.update_skip_outside_release_period",
            preferences.libraryUpdateSkipOutsideReleasePeriod.toString(),
        )
        properties.setProperty("library.update_categories", preferences.libraryUpdateCategories.encodeLongSet())
        properties.setProperty(
            "library.update_categories_exclude",
            preferences.libraryUpdateCategoriesExclude.encodeLongSet(),
        )
        properties.setProperty(
            "library.update_only_on_ac_power",
            preferences.libraryUpdateOnlyOnAcPower.toString(),
        )
        properties.setProperty(
            "library.auto_download_new",
            preferences.autoDownloadNewChapters.toString(),
        )
        properties.setProperty("download.new_unread_only", preferences.autoDownloadUnreadOnly.toString())
        properties.setProperty("download.new_categories", preferences.autoDownloadCategories.encodeLongSet())
        properties.setProperty(
            "download.new_categories_exclude",
            preferences.autoDownloadCategoriesExclude.encodeLongSet(),
        )
        properties.setProperty(
            "notifications.desktop_enabled",
            preferences.desktopNotificationsEnabled.toString(),
        )
        properties.setProperty(
            "notifications.desktop_hide_content",
            preferences.desktopNotificationsHideContent.toString(),
        )
        properties.setProperty(
            "library.last_update_epoch_millis",
            preferences.lastLibraryUpdateEpochMillis.toString(),
        )
        properties.setProperty("sync.enabled", preferences.syncEnabled.toString())
        properties.setProperty("sync.interval_minutes", preferences.syncIntervalMinutes.toString())
        properties.setProperty("sync.last_epoch_millis", preferences.lastSyncEpochMillis.toString())
        properties.setProperty("sync.last_message", preferences.lastSyncMessage)
        properties.setProperty("sync.device_id", preferences.syncDeviceId)
        LEGACY_SYNC_SERVER_KEYS.forEach(properties::remove)
        properties.setProperty("onboarding.completed", preferences.onboardingCompleted.toString())
        properties.setProperty("splash.every_launch", preferences.splashEveryLaunch.toString())
        properties.setProperty("browse.show_nsfw", preferences.showNsfwSources.toString())
        properties.setProperty("browse.hidden_sources", preferences.hiddenSourceIds.encodeLongSet())
        properties.setProperty("browse.global_search_only_pinned", preferences.globalSearchOnlyPinned.toString())
        properties.remove("window.x")
        properties.remove("window.y")
        properties.remove("window.width")
        properties.remove("window.height")
        properties.remove("window.maximized")
        preferences.windowPlacement?.let { placement ->
            properties.setProperty("window.x", placement.x.toString())
            properties.setProperty("window.y", placement.y.toString())
            properties.setProperty("window.width", placement.width.toString())
            properties.setProperty("window.height", placement.height.toString())
            properties.setProperty("window.maximized", placement.maximized.toString())
        }
        writeProperties(properties)
        cachedProperties = properties
        cachedPreferences = preferences
        cacheStamp = currentStamp()
    }

    @Synchronized
    fun property(key: String): String? = getOrReadProperties().getProperty(key)

    @Synchronized
    fun propertiesWithPrefix(prefix: String): Map<String, String> = getOrReadProperties().let { properties ->
        properties.stringPropertyNames().filter { it.startsWith(prefix) }
            .associateWith { properties.getProperty(it) }
    }

    @Synchronized
    fun update(block: Properties.() -> Unit) {
        val properties = Properties().apply { putAll(getOrReadProperties()) }
        properties.apply(block)
        writeProperties(properties)
        cachedProperties = properties
        cachedPreferences = null
        cacheStamp = currentStamp()
    }

    private fun readProperties(): Properties {
        if (!Files.exists(file)) return Properties()
        return try {
            Properties().apply {
                Files.newInputStream(file).use { input -> load(input) }
            }
        } catch (error: IOException) {
            return recoverCorruptPreferences(file, error, ::invalidateCache)
        } catch (error: IllegalArgumentException) {
            return recoverCorruptPreferences(file, error, ::invalidateCache)
        }
    }

    private fun writeProperties(properties: Properties) {
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.newOutputStream(temporary).use { properties.store(it, "Mihon W desktop preferences") }
        try {
            Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, file, REPLACE_EXISTING)
        }
    }
}
