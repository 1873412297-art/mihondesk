package mihon.companion.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.companion.mihonfolder.MihonAutoImporter
import mihon.companion.mihonfolder.MihonImportOutcome
import mihon.sync.engine.SyncReport

data class RefreshResult(val mihon: MihonImportOutcome, val sync: SyncReport?)

/**
 * The one "bring everything up to date" step: read Mihon's newest backup, then sync with Drive.
 * Runs when the app comes to the foreground, on "Sync now" and in the background worker.
 */
class LibraryRefresher(
    private val importer: MihonAutoImporter,
    private val sync: CompanionSyncService,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var lastForegroundAt = 0L

    suspend fun refresh(): RefreshResult = mutex.withLock {
        val mihon = importer.importIfNewer()
        RefreshResult(mihon, sync.syncNow())
    }

    /** Opening the app repeatedly within a minute does not start another round. */
    suspend fun refreshOnForeground(): RefreshResult? {
        val now = clock()
        if (now - lastForegroundAt < FOREGROUND_THROTTLE_MS) return null
        lastForegroundAt = now
        return refresh()
    }

    private companion object {
        const val FOREGROUND_THROTTLE_MS = 60_000L
    }
}
