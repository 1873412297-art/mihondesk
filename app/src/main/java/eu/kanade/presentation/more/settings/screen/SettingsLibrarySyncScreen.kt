package eu.kanade.presentation.more.settings.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.sync.SyncStrings
import eu.kanade.tachiyomi.data.sync.SyncWorker
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.util.collectAsState
import java.text.DateFormat
import java.util.Date

object SettingsLibrarySyncScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.syncing_library

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val syncPreferences = remember { context.appGraph.syncPreferences }

        val isSyncEnabled by syncPreferences.isSyncEnabled.collectAsState()
        val syncDirectoryUri by syncPreferences.syncDirectoryUri.collectAsState()
        val syncIntervalMinutes by syncPreferences.syncIntervalMinutes.collectAsState()
        val syncOnlyOnWifi by syncPreferences.syncOnlyOnWifi.collectAsState()
        val lastSyncTimestamp by syncPreferences.lastSyncTimestamp.collectAsState()
        val lastSyncSummary by syncPreferences.lastSyncSummary.collectAsState()
        val lastSyncError by syncPreferences.lastSyncError.collectAsState()

        val pickSyncLocation = syncLocationPicker(syncPreferences.syncDirectoryUri)

        val syncDirectorySubtitle = remember(syncDirectoryUri) {
            if (syncDirectoryUri.isBlank()) {
                SyncStrings.noDirectorySet
            } else {
                UniFile.fromUri(context, syncDirectoryUri.toUri())?.displayablePath
                    ?: syncDirectoryUri
            }
        }

        val lastSyncFormatted = remember(lastSyncTimestamp) {
            if (lastSyncTimestamp <= 0L) {
                SyncStrings.lastSyncNever
            } else {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastSyncTimestamp))
            }
        }

        val isRunning = remember { SyncWorker.isManualJobRunning(context) }

        return listOf(
            Preference.PreferenceGroup(
                title = SyncStrings.librarySync,
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = syncPreferences.isSyncEnabled,
                        title = SyncStrings.enableSync,
                        subtitle = SyncStrings.enableSyncSummary,
                        onValueChanged = { enabled ->
                            SyncWorker.setupPeriodicTask(
                                context = context,
                                intervalMinutes = if (enabled) syncPreferences.syncIntervalMinutes.get() else 0,
                                onlyOnWifi = syncPreferences.syncOnlyOnWifi.get(),
                            )
                            true
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = SyncStrings.syncDirectory,
                        subtitle = syncDirectorySubtitle,
                        enabled = isSyncEnabled,
                        onClick = {
                            try {
                                pickSyncLocation.launch(null)
                            } catch (e: ActivityNotFoundException) {
                                context.toast("File picker error")
                            }
                        },
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = syncPreferences.syncIntervalMinutes,
                        entries = mapOf(
                            0 to SyncStrings.syncFreqManual,
                            15 to SyncStrings.syncFreq15m,
                            60 to SyncStrings.syncFreq1h,
                            360 to SyncStrings.syncFreq6h,
                            1440 to SyncStrings.syncFreq24h,
                        ),
                        title = SyncStrings.syncFrequency,
                        subtitle = "%s",
                        enabled = isSyncEnabled,
                        onValueChanged = { interval ->
                            SyncWorker.setupPeriodicTask(
                                context = context,
                                intervalMinutes = if (isSyncEnabled) interval else 0,
                                onlyOnWifi = syncOnlyOnWifi,
                            )
                            true
                        },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = syncPreferences.syncOnlyOnWifi,
                        title = SyncStrings.syncOnlyOnWifi,
                        subtitle = SyncStrings.syncOnlyOnWifiSummary,
                        enabled = isSyncEnabled && syncIntervalMinutes > 0,
                        onValueChanged = { onlyWifi ->
                            SyncWorker.setupPeriodicTask(
                                context = context,
                                intervalMinutes = if (isSyncEnabled) syncIntervalMinutes else 0,
                                onlyOnWifi = onlyWifi,
                            )
                            true
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = SyncStrings.syncNow,
                        subtitle = if (isRunning) {
                            SyncStrings.syncing
                        } else {
                            "${SyncStrings.lastSync}: $lastSyncFormatted"
                        },
                        enabled = isSyncEnabled && syncDirectoryUri.isNotBlank(),
                        onClick = {
                            SyncWorker.runNow(context)
                            context.toast(SyncStrings.syncing)
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = SyncStrings.lastSyncResult,
                preferenceItems = listOf(
                    Preference.PreferenceItem.InfoPreference(
                        title = buildString {
                            append("${SyncStrings.lastSync}: $lastSyncFormatted")
                            if (lastSyncSummary.isNotBlank()) {
                                append("\n\n$lastSyncSummary")
                            }
                            if (lastSyncError.isNotBlank()) {
                                append("\n\nError: $lastSyncError")
                            }
                        },
                    ),
                    Preference.PreferenceItem.InfoPreference(
                        title = SyncStrings.localMangaNotice,
                    ),
                ),
            ),
        )
    }

    @Composable
    private fun syncLocationPicker(
        storageDirPref: tachiyomi.core.common.preference.Preference<String>,
    ): ManagedActivityResultLauncher<Uri?, Uri?> {
        val context = LocalContext.current

        return rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                try {
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: SecurityException) {
                    logcat(LogPriority.ERROR, e)
                }

                UniFile.fromUri(context, uri)?.let {
                    storageDirPref.set(it.uri.toString())
                }
            }
        }
    }
}
