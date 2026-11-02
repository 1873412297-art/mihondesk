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
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.TimeUnit

/** Syncs the library through Google Drive (appDataFolder), the only sync backend. */
class SyncWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val notifier = SyncNotifier(context)

    override suspend fun doWork(): Result {
        val appGraph = context.appGraph
        val syncPreferences = appGraph.syncPreferences
        val isManual = inputData.getBoolean(KEY_IS_MANUAL, false)

        if (!syncPreferences.googleDriveEnabled.get()) {
            return Result.success()
        }

        fun fail(error: DriveSyncError, retry: Boolean): Result {
            syncPreferences.lastSyncError.set(error.name)
            if (isManual) notifier.showSyncError(SyncStrings.driveError(error))
            return if (retry) Result.retry() else Result.failure()
        }

        val client = OkHttpClient()
        setForegroundSafely()

        return try {
            // Fail fast (without UI) when consent is needed so the settings screen can prompt.
            GoogleDriveAuth.silentAccessToken(context.applicationContext)

            val database = appGraph.database
            val legacyState = AndroidSyncStateStore(database)
            val stateStore = AndroidDriveSyncStateStore(
                file = AndroidDriveSyncStateStore.file(context),
                deviceId = { legacyState.getDeviceId() },
            )
            val engine = SyncEngine(
                repository = AndroidSyncLocalRepository(database, appGraph.json),
                transport = GoogleDriveAuth.transport(context, client),
                stateStore = stateStore,
            )

            val report = engine.syncNow()
            if (report.success) {
                val summary = SyncStrings.summary(
                    pulled = report.pulledChangesetCount,
                    pushed = if (report.pushedChangeset != null) 1 else 0,
                    changed = report.totalChanged,
                )
                val now = System.currentTimeMillis()
                stateStore.recordSync(now, summary)
                syncPreferences.lastSyncTimestamp.set(now)
                syncPreferences.lastSyncSummary.set(summary)
                syncPreferences.lastSyncError.set("")
                if (isManual) notifier.showSyncComplete(summary) else notifier.cancelProgress()
                logcat(LogPriority.INFO) { "Drive sync completed: $summary" }
                Result.success()
            } else {
                val error = DriveSyncError.classifyMessage(report.errorMessage)
                logcat(LogPriority.ERROR) { "Drive sync failed: $error" }
                fail(error, retry = error == DriveSyncError.NETWORK || error == DriveSyncError.UNKNOWN)
            }
        } catch (e: Exception) {
            val error = DriveSyncError.classify(e)
            logcat(LogPriority.ERROR, e) { "Drive sync failed: $error" }
            fail(error, retry = error == DriveSyncError.NETWORK)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
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
