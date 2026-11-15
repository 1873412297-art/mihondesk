package mihon.companion.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import mihon.companion.CompanionApp
import java.util.concurrent.TimeUnit

/** Hourly background sync while the phone is online (Android may stretch the interval). */
class SyncScheduler(private val context: Context) {
    fun apply(enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Uploads local edits shortly after they happen. Re-requesting replaces the pending run, so a
     * burst of edits becomes one push.
     */
    fun requestSoon(delaySeconds: Long = 30) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SOON_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "drive-library-sync"
        const val SOON_NAME = "drive-library-sync-soon"
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val runtime = (applicationContext as CompanionApp).runtime
        runtime.library.load()
        val report = runtime.sync.syncNow() ?: return Result.success()
        return when {
            report.success -> Result.success()
            // Needs the user to sign in again: retrying in the background cannot fix that.
            report.errorMessage?.contains(DriveTokenSource.SIGN_IN_REQUIRED) == true -> Result.success()
            else -> Result.retry()
        }
    }
}
