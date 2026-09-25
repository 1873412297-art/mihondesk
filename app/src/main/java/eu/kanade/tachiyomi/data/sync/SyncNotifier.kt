package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify

@Inject
class SyncNotifier(
    private val context: Context,
) {
    private val progressNotificationBuilder = context.notificationBuilder(
        Notifications.CHANNEL_SYNC_PROGRESS,
    ) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_mihon)
        setAutoCancel(false)
        setOngoing(true)
        setOnlyAlertOnce(true)
    }

    private val completeNotificationBuilder = context.notificationBuilder(
        Notifications.CHANNEL_SYNC_COMPLETE,
    ) {
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_mihon)
        setAutoCancel(true)
    }

    fun showSyncProgress(): NotificationCompat.Builder {
        val builder = with(progressNotificationBuilder) {
            setContentTitle(SyncStrings.syncing)
            setContentText(SyncStrings.librarySync)
            setProgress(0, 0, true)
        }
        context.notify(Notifications.ID_SYNC_PROGRESS, builder.build())
        return builder
    }

    fun showSyncComplete(summary: String) {
        cancelProgress()
        val builder = with(completeNotificationBuilder) {
            setContentTitle(SyncStrings.librarySync)
            setContentText(summary.ifBlank { SyncStrings.lastSyncResult })
            setProgress(0, 0, false)
        }
        context.notify(Notifications.ID_SYNC_COMPLETE, builder.build())
    }

    fun showSyncError(errorMessage: String?) {
        cancelProgress()
        val builder = with(completeNotificationBuilder) {
            setContentTitle(SyncStrings.librarySync)
            setContentText(errorMessage ?: "Sync failed")
            setProgress(0, 0, false)
        }
        context.notify(Notifications.ID_SYNC_COMPLETE, builder.build())
    }

    fun cancelProgress() {
        context.cancelNotification(Notifications.ID_SYNC_PROGRESS)
    }
}
