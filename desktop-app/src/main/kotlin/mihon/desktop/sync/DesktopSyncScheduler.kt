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
import mihon.desktop.library.sync.SqlDelightSyncLocalRepository
import mihon.desktop.library.sync.SqlDelightSyncStateStore
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import mihon.sync.engine.SyncEngine
import mihon.sync.engine.SyncReport
import mihon.sync.transport.http.HttpTransport
import java.util.UUID

/**
 * Automatic sync decision: always when local changes are waiting to be pushed (reading progress
 * reaches other devices within a minute), otherwise once per configured interval to pull.
 */
fun shouldAutoSync(prefs: DesktopPreferences, now: Long, localDirty: Boolean): Boolean {
    if (!prefs.syncEnabled) return false
    if (!prefs.syncServerEnabled || prefs.syncServerToken.isBlank()) return false
    if (localDirty) return true
    if (prefs.syncIntervalMinutes <= 0) return false
    return now - prefs.lastSyncEpochMillis >= prefs.syncIntervalMinutes.toLong() * 60_000L
}

class DesktopSyncScheduler(
    private val preferenceStore: DesktopPreferenceStore,
    private val library: SqlDelightLibraryRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val activityCenter: ActivityCenter? = null,
    /** Quiet period that coalesces bursts of [requestSync] calls (e.g. several remote pushes). */
    private val requestDebounceMillis: Long = 3_000L,
    /** Test seam; defaults to a real sync against the in-app server. */
    private val runner: (suspend () -> SyncReport)? = null,
) {
    private var scheduledJob: Job? = null
    private var requestJob: Job? = null
    private val syncMutex = Mutex()

    @Volatile
    var lastReport: SyncReport? = null
        private set

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
        val prefs = preferenceStore.load()
        if (!prefs.syncEnabled || !prefs.syncServerEnabled || prefs.syncServerToken.isBlank()) {
            return@withLock null
        }
        val dirty = runner == null && runCatching { hasLocalChanges(prefs) }.getOrDefault(false)
        if (shouldAutoSync(prefs, clock(), dirty)) performTracked() else null
    }

    suspend fun syncNow(): SyncReport = syncMutex.withLock {
        performTracked()
    }

    /**
     * Asks for a sync soon (debounced). Used when a paired device pushed changes to the in-app
     * server, so they are pulled into this library right away instead of at the next interval.
     */
    fun requestSync() {
        val prefs = preferenceStore.load()
        if (!prefs.syncEnabled || !prefs.syncServerEnabled || prefs.syncServerToken.isBlank()) return
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

    /** Called by the in-app server for every accepted changeset; ignores this device's own pushes. */
    fun onRemoteChangeset(deviceId: String) {
        if (deviceId == preferenceStore.load().syncDeviceId) return
        requestSync()
    }

    private suspend fun hasLocalChanges(prefs: DesktopPreferences): Boolean =
        withContext(Dispatchers.IO) {
            // Never synced yet: the first sync follows the interval / manual trigger.
            if (prefs.syncDeviceId.isBlank()) return@withContext false
            val stateStore = SqlDelightSyncStateStore(library.database) { prefs.syncDeviceId }
            val repository = SqlDelightSyncLocalRepository(library.database)
            val watermark = stateStore.getLastPushWatermark()
            !repository.exportDelta(watermark).isEmpty || repository.exportTombstones(watermark).isNotEmpty()
        }

    private suspend fun performTracked(): SyncReport {
        val center = activityCenter
        center?.begin(SYNC_ACTIVITY_ID, ActivityKind.SYNC, retry = { requestSyncNow() })
        val report = try {
            runner?.invoke() ?: performSyncLocked()
        } catch (cancelled: CancellationException) {
            center?.remove(SYNC_ACTIVITY_ID)
            throw cancelled
        } catch (e: Exception) {
            center?.fail(SYNC_ACTIVITY_ID, e.message ?: e.javaClass.simpleName)
            throw e
        }
        if (report.success) {
            center?.succeed(SYNC_ACTIVITY_ID)
        } else {
            center?.fail(SYNC_ACTIVITY_ID, report.errorMessage ?: "Sync failed")
        }
        if (runner != null) lastReport = report
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

    private suspend fun performSyncLocked(): SyncReport = withContext(Dispatchers.IO) {
        val prefs = preferenceStore.load()

        var deviceId = prefs.syncDeviceId
        if (deviceId.isBlank()) {
            deviceId = UUID.randomUUID().toString()
            preferenceStore.updatePreferences { it.copy(syncDeviceId = deviceId) }
        }

        if (prefs.syncServerToken.isBlank()) {
            val errReport = SyncReport(
                success = false,
                pulledChangesetCount = 0,
                mangaInserted = 0,
                mangaMerged = 0,
                chapterInserted = 0,
                chapterMerged = 0,
                categoriesLinked = 0,
                pushedChangeset = null,
                errorMessage = "Builtin sync server token not configured",
            )
            lastReport = errReport
            return@withContext errReport
        }

        val sourceIdentifier = "http://127.0.0.1:${prefs.syncServerPort}"
        val transport = HttpTransport(
            baseUrl = sourceIdentifier,
            token = prefs.syncServerToken,
            ownsClient = true,
        )

        val syncLocalRepo = SqlDelightSyncLocalRepository(library.database)
        val syncStateStore = SqlDelightSyncStateStore(library.database) { deviceId }
        val engine = SyncEngine(syncLocalRepo, transport, syncStateStore, clock)

        val report = try {
            engine.syncNow()
        } finally {
            if (transport is AutoCloseable) {
                try {
                    transport.close()
                } catch (_: Throwable) {}
            }
        }
        lastReport = report
        if (report.success) {
            mihon.desktop.logging.DesktopLogger.info(
                "DesktopSyncScheduler",
                "Sync succeeded: pulled=${report.pulledChangesetCount}, pushed=${report.pushedChangeset != null}",
            )
        } else {
            mihon.desktop.logging.DesktopLogger.warn(
                "DesktopSyncScheduler",
                "Sync failed: ${report.errorMessage}",
            )
        }

        // Record import report in database
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
        } catch (_: Exception) {
            // Ignore DB report persistence error
        }

        val summary = if (report.success) {
            "Pulled: ${report.pulledChangesetCount}, Pushed: ${if (report.pushedChangeset != null) 1 else 0}, Changed: ${report.totalChanged}"
        } else {
            report.errorMessage ?: "Failed"
        }

        preferenceStore.updatePreferences {
            it.copy(
                lastSyncEpochMillis = now,
                lastSyncMessage = summary,
            )
        }

        report
    }
}
