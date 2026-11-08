package mihon.desktop.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mihon.desktop.activity.ActivityCenter
import mihon.desktop.activity.ActivityKind
import mihon.desktop.activity.SYNC_ACTIVITY_ID
import mihon.desktop.library.db.SqlDelightLibraryRepository
import mihon.desktop.library.model.ImportCounts
import mihon.desktop.library.model.ImportReportItemRecord
import mihon.desktop.library.model.ImportReportRecord
import mihon.desktop.library.model.ImportStatus
import mihon.desktop.library.model.ImportType
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.sync.gdrive.GoogleDriveSyncService
import mihon.sync.engine.SyncReport

/** A sync destination the scheduler drives. Google Drive is the only one. */
interface SyncBackend {
    /** Recorded as the import report source. */
    val sourceIdentifier: String

    /** Configured and signed in. */
    fun isReady(): Boolean

    /** Local edits are waiting to be pushed, measured against this backend's own watermark. */
    suspend fun hasLocalChanges(): Boolean

    /** Runs one sync cycle; null when the backend is not ready. */
    suspend fun syncNow(): SyncReport?
}

/**
 * Automatic sync decision: always when local changes are waiting to be pushed (reading progress
 * reaches other devices within a minute), otherwise once per configured interval to pull.
 */
fun shouldAutoSync(
    driveReady: Boolean,
    intervalMinutes: Int,
    lastSyncAt: Long,
    now: Long,
    localDirty: Boolean,
): Boolean {
    if (!driveReady) return false
    if (localDirty) return true
    if (intervalMinutes <= 0) return false
    return now - lastSyncAt >= intervalMinutes.toLong() * 60_000L
}

class DesktopSyncScheduler(
    private val preferenceStore: DesktopPreferenceStore,
    private val library: SqlDelightLibraryRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val activityCenter: ActivityCenter? = null,
    /** Quiet period that coalesces bursts of [requestSync] calls. */
    private val requestDebounceMillis: Long = 3_000L,
    /** Test seam; defaults to [googleDrive]. */
    private val backend: SyncBackend? = null,
) {
    private var scheduledJob: Job? = null
    private var requestJob: Job? = null
    private val syncMutex = Mutex()

    /** Google Drive sync, the only sync transport. */
    @Volatile
    var googleDrive: GoogleDriveSyncService? = null

    @Volatile
    var lastReport: SyncReport? = null
        private set

    private fun activeBackend(): SyncBackend? = backend ?: googleDrive

    fun start() {
        if (scheduledJob?.isActive == true) return
        scheduledJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    checkAndRunAutoSync()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    mihon.desktop.logging.DesktopLogger.warn("DesktopSyncScheduler", "Auto-sync check failed", e)
                }
                delay(60_000L) // check every minute
            }
        }
    }

    fun stop() {
        scheduledJob?.cancel()
        scheduledJob = null
    }

    suspend fun checkAndRunAutoSync(): SyncReport? = syncMutex.withLock {
        val drive = activeBackend()?.takeIf { it.isReady() } ?: return@withLock null
        val prefs = preferenceStore.load()
        val dirty = runCatching { drive.hasLocalChanges() }
            .onFailure {
                // A failed dirty check must not look like "no local changes": sync anyway.
                mihon.desktop.logging.DesktopLogger.warn(
                    "DesktopSyncScheduler",
                    "Cannot check for local sync changes; syncing anyway",
                    it,
                )
            }
            .getOrDefault(true)
        if (shouldAutoSync(true, prefs.syncIntervalMinutes, prefs.lastSyncEpochMillis, clock(), dirty)) {
            performTracked(drive)
        } else {
            null
        }
    }

    /** Syncs now; null when Drive is not signed in. */
    suspend fun syncNow(): SyncReport? = syncMutex.withLock {
        val drive = activeBackend()?.takeIf { it.isReady() } ?: return@withLock null
        performTracked(drive)
    }

    /** Asks for a sync soon (debounced), e.g. after library edits. */
    fun requestSync() {
        if (activeBackend()?.isReady() != true) return
        synchronized(this) {
            requestJob?.cancel()
            requestJob = scope.launch(Dispatchers.IO) {
                delay(requestDebounceMillis)
                try {
                    syncNow()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    mihon.desktop.logging.DesktopLogger.warn("DesktopSyncScheduler", "Requested sync failed", e)
                }
            }
        }
    }

    private suspend fun performTracked(drive: SyncBackend): SyncReport? {
        val center = activityCenter
        center?.begin(SYNC_ACTIVITY_ID, ActivityKind.SYNC, retry = { requestSyncNow() })
        val report = try {
            drive.syncNow()
        } catch (cancelled: CancellationException) {
            center?.remove(SYNC_ACTIVITY_ID)
            throw cancelled
        } catch (e: Exception) {
            center?.fail(SYNC_ACTIVITY_ID, e.message ?: e.javaClass.simpleName)
            throw e
        }
        if (report == null) {
            center?.remove(SYNC_ACTIVITY_ID)
            return null
        }
        if (report.success) {
            center?.succeed(SYNC_ACTIVITY_ID)
        } else {
            center?.fail(SYNC_ACTIVITY_ID, report.errorMessage ?: "Sync failed")
        }
        lastReport = report
        record(report, drive.sourceIdentifier)
        return report
    }

    private fun requestSyncNow() {
        scope.launch(Dispatchers.IO) {
            try {
                syncNow()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                mihon.desktop.logging.DesktopLogger.warn("DesktopSyncScheduler", "Retried sync failed", e)
            }
        }
    }

    private suspend fun record(report: SyncReport, sourceIdentifier: String) = withContext(Dispatchers.IO) {
        if (report.success) {
            mihon.desktop.logging.DesktopLogger.info(
                "DesktopSyncScheduler",
                "Sync succeeded: pulled=${report.pulledChangesetCount}, pushed=${report.pushedChangeset != null}",
            )
        } else {
            mihon.desktop.logging.DesktopLogger.warn("DesktopSyncScheduler", "Sync failed: ${report.errorMessage}")
        }

        val now = clock()
        try {
            val status = if (report.success) ImportStatus.SUCCEEDED else ImportStatus.FAILED
            val reportId = library.insertReport(
                ImportReportRecord(
                    importType = ImportType.SYNC,
                    sourcePath = sourceIdentifier,
                    status = status,
                    startedAt = now - report.durationMs,
                    finishedAt = now,
                    counts = ImportCounts(
                        mangaInserted = report.mangaInserted.toLong(),
                        mangaMerged = report.mangaMerged.toLong(),
                        chaptersInserted = report.chapterInserted.toLong(),
                        chaptersMerged = report.chapterMerged.toLong(),
                        categoriesLinked = report.categoriesLinked.toLong(),
                    ),
                ),
            )
            for (item in report.overriddenItems) {
                library.insertReportItem(
                    reportId = reportId,
                    value = ImportReportItemRecord(
                        itemType = item.entityType.name,
                        itemKey = item.entityKey,
                        outcome = "OVERRIDDEN",
                        reason = "LWW_REMOTE_WINS",
                        message = item.reason,
                    ),
                )
            }
            val errorMsg = report.errorMessage
            if (!report.success && errorMsg != null) {
                library.insertReportItem(
                    reportId = reportId,
                    value = ImportReportItemRecord(
                        itemType = "SYNC",
                        itemKey = "sync",
                        outcome = "FAILED",
                        reason = "ERROR",
                        message = errorMsg,
                    ),
                )
            }
        } catch (error: Exception) {
            mihon.desktop.logging.DesktopLogger.warn("Sync", "Failed to persist sync report", error)
        }

        val summary = if (report.success) {
            "Pulled: ${report.pulledChangesetCount}, Pushed: ${if (report.pushedChangeset != null) 1 else 0}, " +
                "Changed: ${report.totalChanged}"
        } else {
            report.errorMessage ?: "Failed"
        }
        preferenceStore.updatePreferences { it.copy(lastSyncEpochMillis = now, lastSyncMessage = summary) }
    }
}
