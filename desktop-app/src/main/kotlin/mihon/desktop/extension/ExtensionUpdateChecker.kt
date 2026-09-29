package mihon.desktop.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.ui.browse.countPendingExtensionUpdates
import kotlin.time.Duration.Companion.hours

/**
 * Periodically checks whether installed extensions have updates available in the configured
 * repositories. Notifies [onUpdatesAvailable] when the pending count changes and is positive;
 * it never installs anything by itself.
 *
 * The check runs once at startup (via [start]) and then every [intervalHours] hours, reusing
 * the same pending-update computation as [mihon.desktop.ui.browse.BrowsePresenter].
 */
class ExtensionUpdateChecker(
    private val installer: DesktopExtensionInstaller,
    private val storeService: ExtensionStoreService,
    private val onUpdatesAvailable: (Int) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    var intervalHours: Long = 24L,
) {
    private var job: Job? = null

    @Volatile
    private var lastNotifiedCount = 0

    fun start() {
        if (intervalHours <= 0) return
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                runCatching { checkNow() }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        DesktopLogger.warn("ExtensionUpdateChecker", "Scheduled update check failed", error)
                    }
                delay(intervalHours.hours)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun checkNow(): Int {
        val installed = installer.getInstalledExtensions()
        val available = storeService.getRepositories()
            .flatMap { repo ->
                try {
                    storeService.fetchRepository(repo)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    DesktopLogger.warn("ExtensionUpdateChecker", "Failed to fetch repository $repo", error)
                    emptyList()
                }
            }
            .let { selectAvailableStoreItems(installed, it) }

        val pending = countPendingExtensionUpdates(installed, available)
        if (pending > 0 && pending != lastNotifiedCount) {
            onUpdatesAvailable(pending)
        }
        lastNotifiedCount = pending
        return pending
    }
}
