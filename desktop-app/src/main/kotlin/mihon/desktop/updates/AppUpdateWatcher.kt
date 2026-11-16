package mihon.desktop.updates

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.logging.runSuspendCatching
import mihon.desktop.preferences.DesktopPreferenceStore
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** A release found by the background check, and its verified package when it was downloaded. */
data class AppUpdateDiscovery(
    val update: UpdateCheckResult.UpdateAvailable,
    val download: DownloadedAppUpdate? = null,
)

data class DownloadedAppUpdate(val file: Path, val sha256: String)

/**
 * Checks for a new MihonDesk release shortly after startup and then every [interval], and hands a
 * release to [onNewRelease] once per version. When the user turned on automatic downloads, the
 * package is first fetched and verified with [download], so the notification can offer to install
 * it; nothing is ever installed without the user. Checks are skipped while automatic checking is off.
 */
class AppUpdateWatcher(
    private val check: suspend () -> UpdateCheckResult,
    private val preferences: DesktopPreferenceStore,
    private val scope: CoroutineScope,
    private val onNewRelease: (AppUpdateDiscovery) -> Unit,
    private val download: (suspend (UpdateCheckResult.UpdateAvailable) -> DownloadedAppUpdate?)? = null,
    private val initialDelay: Duration = 1.minutes,
    private val interval: Duration = 24.hours,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {
    private var job: Job? = null
    private val mutableLatest = MutableStateFlow<AppUpdateDiscovery?>(null)

    /** The newest release seen in this session (announced or not), for the in-app update card. */
    val latest: StateFlow<AppUpdateDiscovery?> = mutableLatest.asStateFlow()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            // Let startup work (library update, extension checks) go first.
            delay(initialDelay)
            while (isActive) {
                checkOnce()
                delay(interval)
            }
        }
    }

    /** One check; returns the release that was announced, or null. Exposed for tests. */
    internal suspend fun checkOnce(): AppUpdateDiscovery? {
        val settings = withContext(ioDispatcher) { preferences.load() }
        if (!settings.appUpdateAutoCheck) return null
        val result = runSuspendCatching { check() }
            .onFailure { DesktopLogger.warn(TAG, "Automatic update check failed", it) }
            .getOrNull()
        return when (result) {
            is UpdateCheckResult.UpdateAvailable -> discover(result, settings.appUpdateAutoDownload)
            is UpdateCheckResult.CheckFailed -> {
                // Offline or rate-limited: try again at the next interval, without bothering the user.
                DesktopLogger.info(TAG, "Automatic update check failed: ${result.message}")
                null
            }
            is UpdateCheckResult.UpToDate, null -> null
        }
    }

    private suspend fun discover(
        result: UpdateCheckResult.UpdateAvailable,
        autoDownload: Boolean,
    ): AppUpdateDiscovery? {
        // A release without a package for this platform cannot be installed here.
        if (result.matchedAsset == null) return null
        val downloaded = download?.takeIf { autoDownload }?.let { fetch ->
            // A failed download falls back to announcing the release; the user can still download it.
            runSuspendCatching { fetch(result) }
                .onFailure { DesktopLogger.warn(TAG, "Background update download failed", it) }
                .getOrNull()
        }
        val discovery = AppUpdateDiscovery(result, downloaded)
        mutableLatest.value = discovery
        val version = result.release.tagName
        val alreadyNotified = withContext(ioDispatcher) {
            var seen = false
            preferences.updatePreferences { current ->
                seen = current.appUpdateNotifiedVersion == version
                if (seen) current else current.copy(appUpdateNotifiedVersion = version)
            }
            seen
        }
        if (alreadyNotified) return null
        onNewRelease(discovery)
        return discovery
    }

    override fun close() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val TAG = "AppUpdateWatcher"
    }
}
