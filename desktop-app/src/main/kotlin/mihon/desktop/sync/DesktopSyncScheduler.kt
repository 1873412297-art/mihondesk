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
import mihon.desktop.library.db.SqlDelightLibraryRepository
import mihon.desktop.library.model.ImportCounts
import mihon.desktop.library.model.ImportReportItemRecord
import mihon.desktop.library.model.ImportReportRecord
import mihon.desktop.library.model.ImportStatus
import mihon.desktop.library.model.ImportType
import mihon.desktop.library.sync.SqlDelightSyncLocalRepository
import mihon.desktop.library.sync.SqlDelightSyncStateStore
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.sync.engine.SyncEngine
import mihon.sync.engine.SyncReport
import mihon.sync.transport.http.HttpTransport
import java.util.UUID

class DesktopSyncScheduler(
    private val preferenceStore: DesktopPreferenceStore,
    private val library: SqlDelightLibraryRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var scheduledJob: Job? = null
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
                } catch (_: Exception) {
                    // Ignore background scheduler errors and continue
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
        if (!prefs.syncEnabled || prefs.syncIntervalMinutes <= 0) {
            return@withLock null
        }
        val isConfigured = prefs.syncServerEnabled && prefs.syncServerToken.isNotBlank()
        if (!isConfigured) return@withLock null

        val intervalMillis = prefs.syncIntervalMinutes.toLong() * 60_000L
        val now = clock()
        if (now - prefs.lastSyncEpochMillis >= intervalMillis) {
            return@withLock performSyncLocked()
        }
        null
    }

    suspend fun syncNow(): SyncReport = syncMutex.withLock {
        performSyncLocked()
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
