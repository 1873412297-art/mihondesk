package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.sync.AndroidDriveSyncStateStore
import eu.kanade.tachiyomi.data.sync.DriveSyncError
import eu.kanade.tachiyomi.data.sync.GoogleDriveAuth
import eu.kanade.tachiyomi.data.sync.SyncStrings
import eu.kanade.tachiyomi.data.sync.SyncWorker
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.util.collectAsState
import java.text.DateFormat
import java.util.Date

/** Library sync settings: Google Drive (appDataFolder) is the only sync backend. */
object SettingsLibrarySyncScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.syncing_library

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val syncPreferences = remember { context.appGraph.syncPreferences }

        val signedIn by syncPreferences.googleDriveEnabled.collectAsState()
        val syncIntervalMinutes by syncPreferences.syncIntervalMinutes.collectAsState()
        val syncOnlyOnWifi by syncPreferences.syncOnlyOnWifi.collectAsState()
        val lastSyncTimestamp by syncPreferences.lastSyncTimestamp.collectAsState()
        val lastSyncSummary by syncPreferences.lastSyncSummary.collectAsState()
        val lastSyncError by syncPreferences.lastSyncError.collectAsState()
        var signingIn by remember { mutableStateOf(false) }

        fun onSignedIn() {
            signingIn = false
            syncPreferences.googleDriveEnabled.set(true)
            syncPreferences.lastSyncError.set("")
            SyncWorker.setupPeriodicTask(
                context,
                syncPreferences.syncIntervalMinutes.get(),
                syncPreferences.syncOnlyOnWifi.get(),
            )
            SyncWorker.runNow(context)
            context.toast(SyncStrings.signInSuccess)
        }

        fun onSignInFailed(error: DriveSyncError) {
            signingIn = false
            context.toast(SyncStrings.driveError(error))
        }

        val consentLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                onSignInFailed(DriveSyncError.CANCELLED)
                return@rememberLauncherForActivityResult
            }
            try {
                when (GoogleDriveAuth.fromConsentResult(context, result.data)) {
                    is GoogleDriveAuth.Outcome.Authorized -> onSignedIn()
                    is GoogleDriveAuth.Outcome.NeedsConsent -> onSignInFailed(DriveSyncError.AUTH_REQUIRED)
                }
            } catch (e: Exception) {
                onSignInFailed(DriveSyncError.classify(e))
            }
        }

        fun signIn() {
            if (signingIn) return
            signingIn = true
            scope.launch {
                try {
                    when (val outcome = GoogleDriveAuth.authorize(context)) {
                        is GoogleDriveAuth.Outcome.Authorized -> onSignedIn()
                        is GoogleDriveAuth.Outcome.NeedsConsent -> consentLauncher.launch(
                            IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build(),
                        )
                    }
                } catch (e: Exception) {
                    onSignInFailed(DriveSyncError.classify(e))
                }
            }
        }

        fun signOut() {
            SyncWorker.setupPeriodicTask(context, 0, syncOnlyOnWifi)
            AndroidDriveSyncStateStore(AndroidDriveSyncStateStore.file(context), deviceId = { "" }).reset()
            syncPreferences.clearDriveSignIn()
        }

        val lastSyncFormatted = remember(lastSyncTimestamp) {
            if (lastSyncTimestamp <= 0L) {
                SyncStrings.lastSyncNever
            } else {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastSyncTimestamp))
            }
        }
        val friendlyError = DriveSyncError.fromStored(lastSyncError)?.let { SyncStrings.driveError(it) }
        val isRunning = remember { SyncWorker.isManualJobRunning(context) }

        val accountItems = buildList<Preference.PreferenceItem<out Any, out Any>> {
            add(
                Preference.PreferenceItem.TextPreference(
                    title = SyncStrings.googleDrive,
                    subtitle = if (signedIn) SyncStrings.statusSignedIn else SyncStrings.googleDriveSummary,
                ),
            )
            if (signedIn) {
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = SyncStrings.syncNow,
                        subtitle = if (isRunning) {
                            SyncStrings.syncing
                        } else {
                            "${SyncStrings.lastSync}: $lastSyncFormatted"
                        },
                        onClick = {
                            SyncWorker.runNow(context)
                            context.toast(SyncStrings.syncing)
                        },
                    ),
                )
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = SyncStrings.signOut,
                        subtitle = SyncStrings.signOutSummary,
                        onClick = { signOut() },
                    ),
                )
            } else {
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = if (signingIn) SyncStrings.signingIn else SyncStrings.signIn,
                        subtitle = SyncStrings.statusSignedOut,
                        enabled = !signingIn,
                        onClick = { signIn() },
                    ),
                )
            }
            add(
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
                    enabled = signedIn,
                    onValueChanged = { interval ->
                        SyncWorker.setupPeriodicTask(context, interval, syncOnlyOnWifi)
                        true
                    },
                ),
            )
            add(
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncOnlyOnWifi,
                    title = SyncStrings.syncOnlyOnWifi,
                    subtitle = SyncStrings.syncOnlyOnWifiSummary,
                    enabled = signedIn && syncIntervalMinutes > 0,
                    onValueChanged = { onlyWifi ->
                        SyncWorker.setupPeriodicTask(context, syncIntervalMinutes, onlyWifi)
                        true
                    },
                ),
            )
        }

        return listOf(
            Preference.PreferenceGroup(
                title = SyncStrings.librarySync,
                preferenceItems = accountItems,
            ),
            Preference.PreferenceGroup(
                title = SyncStrings.lastSyncResult,
                preferenceItems = listOf(
                    Preference.PreferenceItem.InfoPreference(
                        title = buildString {
                            append("${SyncStrings.lastSync}: $lastSyncFormatted")
                            if (lastSyncSummary.isNotBlank()) append("\n\n$lastSyncSummary")
                            if (friendlyError != null) append("\n\n$friendlyError")
                        },
                    ),
                    Preference.PreferenceItem.InfoPreference(
                        title = SyncStrings.localMangaNotice,
                    ),
                ),
            ),
        )
    }
}
