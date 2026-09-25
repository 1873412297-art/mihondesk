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
    val isSyncEnabled: Preference<Boolean> = preferenceStore.getBoolean("sync_enabled", false)

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

    val lastSyncError: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("sync_last_error"),
        "",
    )

    val syncPairingCode: Preference<String> = preferenceStore.getString("sync_pairing_code", "")

    val syncHttpHost: Preference<String> = preferenceStore.getString("sync_http_host", "")

    val syncHttpPort: Preference<Int> = preferenceStore.getInt("sync_http_port", 45831)

    val syncHttpToken: Preference<String> = preferenceStore.getString("sync_http_token", "")

    fun updateFromPairingCode(code: mihon.sync.transport.http.SyncPairingCode) {
        syncPairingCode.set(code.toUriString())
        syncHttpHost.set(code.host)
        syncHttpPort.set(code.port)
        syncHttpToken.set(code.token)
    }
}
