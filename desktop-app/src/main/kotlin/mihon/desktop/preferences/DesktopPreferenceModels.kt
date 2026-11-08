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

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class DesktopPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val appTheme: DesktopAppTheme = DesktopAppTheme.DEFAULT,
    val themeDarkAmoled: Boolean = false,
    val lastDestination: DesktopDestination = DesktopDestination.Library,
    val windowPlacement: WindowPlacement? = null,
    val language: AppLanguage = AppLanguage.System,
    val libraryDisplayMode: String = "ComfortableGrid",
    val libraryGridSize: Float = 180f,
    val libraryDetailPaneWidth: Int = 0,
    val libraryBadgeUnread: Boolean = true,
    val libraryBadgeDownloaded: Boolean = true,
    val librarySortMode: String = "None",
    val librarySortAscending: Boolean = true,
    val libraryFilterUnread: String = "Disabled",
    val libraryFilterDownloaded: String = "Disabled",
    val libraryFilterStarted: String = "Disabled",
    val libraryFilterCompleted: String = "Disabled",
    val libraryFilterBookmarked: String = "Disabled",
    val downloadStoragePath: String = "",
    val downloadParallelCount: Int = 3,
    val downloadPageParallelCount: Int = 5,
    val downloadAhead: Int = 0,
    val deleteDownloadedRead: Boolean = false,
    val saveChapterAsCbz: Boolean = false,
    // Android defaults this on; the desktop download path is newer and opt-in until proven.
    val splitTallImages: Boolean = false,
    val incognitoMode: Boolean = false,
    // App lock. Only a salted PBKDF2 hash, its salt and iteration count are persisted.
    val appLockEnabled: Boolean = false,
    val appLockPinHash: String = "",
    val appLockPinSalt: String = "",
    val appLockPinIterations: Int = 0,
    val appLockOnStartup: Boolean = true,
    val appLockIdleTimeoutMinutes: Int = 5,
    val backupIntervalHours: Int = 0,
    val backupStoragePath: String = "",
    val backupRetentionCount: Int = 10,
    val lastAutoBackupEpochMillis: Long = 0L,
    val backgroundTasksEnabled: Boolean = false,
    val libraryUpdateIntervalHours: Int = 0,
    val libraryUpdateSkipCompleted: Boolean = true,
    val libraryUpdateSkipUnread: Boolean = false,
    val libraryUpdateSkipStarted: Boolean = false,
    val libraryUpdateSkipOutsideReleasePeriod: Boolean = false,
    val libraryUpdateCategories: Set<Long> = emptySet(),
    val libraryUpdateCategoriesExclude: Set<Long> = emptySet(),
    val libraryUpdateOnlyOnAcPower: Boolean = false,
    val autoDownloadNewChapters: Boolean = false,
    val autoDownloadUnreadOnly: Boolean = false,
    val autoDownloadCategories: Set<Long> = emptySet(),
    val autoDownloadCategoriesExclude: Set<Long> = emptySet(),
    val desktopNotificationsEnabled: Boolean = true,
    val desktopNotificationsHideContent: Boolean = false,
    val lastLibraryUpdateEpochMillis: Long = 0L,
    val syncEnabled: Boolean = false,
    val syncIntervalMinutes: Int = 15,
    val lastSyncEpochMillis: Long = 0L,
    val lastSyncMessage: String = "",
    val syncDeviceId: String = "",
    val runInBackgroundOnClose: Boolean = false,
    /** Advanced: use the classic Windows caption instead of the custom title bar. */
    val useSystemTitleBar: Boolean = false,
    val onboardingCompleted: Boolean = false,
    /** Appearance: play the startup splash on every launch, not only the first one. */
    val splashEveryLaunch: Boolean = true,
    val showNsfwSources: Boolean = false,
    val hiddenSourceIds: Set<Long> = emptySet(),
    val globalSearchOnlyPinned: Boolean = false,
)
