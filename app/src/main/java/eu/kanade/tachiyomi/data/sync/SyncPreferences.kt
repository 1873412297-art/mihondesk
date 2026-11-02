package eu.kanade.tachiyomi.data.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class SyncPreferences(
    preferenceStore: PreferenceStore,
) {
    val syncIntervalMinutes: Preference<Int> = preferenceStore.getInt("sync_interval_minutes", 0) // 0 = manual only

    val syncOnlyOnWifi: Preference<Boolean> = preferenceStore.getBoolean("sync_only_on_wifi", false)

    val lastSyncTimestamp: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("sync_last_timestamp"),
        0L,
    )

    val lastSyncSummary: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("sync_last_summary"),
        "",
    )

    /** Holds a [DriveSyncError] name; anything else (e.g. legacy LAN messages) is ignored by the UI. */
    val lastSyncError: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("sync_last_error"),
        "",
    )

    /** True once the user has signed in to Google Drive; Drive is the only sync backend. */
    val googleDriveEnabled: Preference<Boolean> = preferenceStore.getBoolean("sync_gdrive_enabled", false)

    fun clearDriveSignIn() {
        googleDriveEnabled.set(false)
        lastSyncTimestamp.set(0L)
        lastSyncSummary.set("")
        lastSyncError.set("")
    }
}
