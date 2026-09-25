package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.sync.engine.SyncEngine
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.TimeUnit

class SyncWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val notifier = SyncNotifier(context)

    override suspend fun doWork(): Result {
        val appGraph = context.appGraph
        val syncPreferences = appGraph.syncPreferences
        val isManual = inputData.getBoolean(KEY_IS_MANUAL, false)

        if (!syncPreferences.isSyncEnabled.get()) {
            return Result.success()
        }

        val host = syncPreferences.syncHttpHost.get().trim()
        val port = syncPreferences.syncHttpPort.get()
        val token = syncPreferences.syncHttpToken.get().trim()
        if (host.isBlank() || token.isBlank()) {
            val errMsg = "HTTP sync server or token not configured"
            syncPreferences.lastSyncError.set(errMsg)
            if (isManual) notifier.showSyncError(errMsg)
            return Result.failure()
        }
        val transport = mihon.sync.transport.http.HttpTransport(
            baseUrl = "http://$host:$port",
            token = token,
            ownsClient = true,
        )
        val cleanupTransport: AutoCloseable = transport

        setForegroundSafely()

        return try {
            val database = appGraph.database
            val stateStore = AndroidSyncStateStore(database)
            val localRepository = AndroidSyncLocalRepository(database, appGraph.json)

            val engine = SyncEngine(
                repository = localRepository,
                transport = transport,
                stateStore = stateStore,
            )

            val report = engine.syncNow()
            if (report.success) {
                val pushedCursor = report.pushedChangeset?.cursor ?: 0L
                val summary = "Pulled ${report.pulledChangesetCount} changesets, " +
                    "merged ${report.mangaMerged} manga, ${report.chapterMerged} chapters. " +
                    "Pushed cursor $pushedCursor."
                syncPreferences.lastSyncTimestamp.set(System.currentTimeMillis())
                syncPreferences.lastSyncSummary.set(summary)
                syncPreferences.lastSyncError.set("")
                if (isManual) {
                    notifier.showSyncComplete(summary)
                } else {
                    notifier.cancelProgress()
                }
                logcat(LogPriority.INFO) { "Library sync completed successfully: $summary" }
                Result.success()
            } else {
                val err = report.errorMessage ?: "Unknown error"
                syncPreferences.lastSyncError.set(err)
                if (isManual) notifier.showSyncError(err)
                logcat(LogPriority.ERROR) { "Library sync failed: $err" }
                Result.retry()
            }
        } catch (e: mihon.sync.transport.http.SyncPairingException) {
            logcat(LogPriority.ERROR, e) { "Library sync failed: pairing expired" }
            val msg = SyncStrings.pairingExpired
            syncPreferences.lastSyncError.set(msg)
            if (isManual) notifier.showSyncError(msg)
            Result.failure()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Library sync worker crashed" }
            syncPreferences.lastSyncError.set(e.message ?: "Unknown error")
            if (isManual) notifier.showSyncError(e.message)
            Result.failure()
        } finally {
            try {
                cleanupTransport.close()
            } catch (_: Throwable) {}
            notifier.cancelProgress()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_SYNC_PROGRESS,
            notifier.showSyncProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        private const val TAG_MANUAL = "LibrarySyncWorker_Manual"
        private const val TAG_PERIODIC = "LibrarySyncWorker_Periodic"
        private const val KEY_IS_MANUAL = "is_manual"

        fun isManualJobRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_MANUAL)
        }

        fun isAnyJobRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_MANUAL) || context.workManager.isRunning(TAG_PERIODIC)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(KEY_IS_MANUAL to true))
                .addTag(TAG_MANUAL)
                .build()
            context.workManager.enqueueUniqueWork(
                TAG_MANUAL,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun setupPeriodicTask(context: Context, intervalMinutes: Int, onlyOnWifi: Boolean) {
            val workManager = context.workManager
            if (intervalMinutes <= 0) {
                workManager.cancelUniqueWork(TAG_PERIODIC)
                return
            }

            val effectiveMinutes = intervalMinutes.coerceAtLeast(15)
            val networkType = if (onlyOnWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                effectiveMinutes.toLong(),
                TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .addTag(TAG_PERIODIC)
                .build()

            workManager.enqueueUniquePeriodicWork(
                TAG_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
