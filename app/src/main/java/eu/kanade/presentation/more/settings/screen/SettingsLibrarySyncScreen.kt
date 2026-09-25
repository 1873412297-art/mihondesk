package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.sync.SyncStrings
import eu.kanade.tachiyomi.data.sync.SyncWorker
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
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
        val scope = rememberCoroutineScope()
        val syncPreferences = remember { context.appGraph.syncPreferences }
        val navigator = LocalNavigator.currentOrThrow

        val isSyncEnabled by syncPreferences.isSyncEnabled.collectAsState()
        val syncPairingCode by syncPreferences.syncPairingCode.collectAsState()
        val syncHttpHost by syncPreferences.syncHttpHost.collectAsState()
        val syncHttpPort by syncPreferences.syncHttpPort.collectAsState()
        val syncHttpToken by syncPreferences.syncHttpToken.collectAsState()
        val syncIntervalMinutes by syncPreferences.syncIntervalMinutes.collectAsState()
        val syncOnlyOnWifi by syncPreferences.syncOnlyOnWifi.collectAsState()
        val lastSyncTimestamp by syncPreferences.lastSyncTimestamp.collectAsState()
        val lastSyncSummary by syncPreferences.lastSyncSummary.collectAsState()
        val lastSyncError by syncPreferences.lastSyncError.collectAsState()

        var testStatus by remember { mutableStateOf<String?>(null) }
        var showDiscovery by remember { mutableStateOf(false) }

        val lastSyncFormatted = remember(lastSyncTimestamp) {
            if (lastSyncTimestamp <= 0L) {
                SyncStrings.lastSyncNever
            } else {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastSyncTimestamp))
            }
        }

        val isRunning = remember { SyncWorker.isManualJobRunning(context) }

        val transportItems = mutableListOf<Preference.PreferenceItem<out Any, out Any>>()

        // Show LAN discovery dialog when requested
        if (showDiscovery) {
            eu.kanade.presentation.more.settings.screen.LanDiscoveryDialog(
                onDismiss = { showDiscovery = false },
                onPaired = { code ->
                    syncPreferences.updateFromPairingCode(code)
                    val msg = SyncStrings.discoverDevicesPairSuccess.format(code.host)
                    context.toast(msg)
                },
                onPreFill = { host, port ->
                    syncPreferences.syncHttpHost.set(host)
                    syncPreferences.syncHttpPort.set(port)
                },
            )
        }

        transportItems.add(
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
        )

        val pairingSubtitle = if (syncHttpHost.isNotBlank()) {
            "http://$syncHttpHost:$syncHttpPort"
        } else {
            SyncStrings.pairingCodeSummary
        }
        transportItems.add(
            Preference.PreferenceItem.EditTextPreference(
                preference = syncPreferences.syncPairingCode,
                title = SyncStrings.pairingCode,
                subtitle = pairingSubtitle,
                enabled = isSyncEnabled,
                onValueChanged = { raw ->
                    val parsed = mihon.sync.transport.http.SyncPairingCode.parseOrNull(raw)
                    if (parsed != null) {
                        syncPreferences.updateFromPairingCode(parsed)
                        true
                    } else {
                        context.toast(SyncStrings.pairingCodeInvalid)
                        false
                    }
                },
            ),
        )
        // Scan QR code to pair
        transportItems.add(
            Preference.PreferenceItem.TextPreference(
                title = SyncStrings.scanPairing,
                subtitle = SyncStrings.scanPairingHint,
                enabled = isSyncEnabled,
                onClick = { navigator.push(ScanSyncPairingScreen()) },
            ),
        )
        // Search LAN for auto-discovery
        transportItems.add(
            Preference.PreferenceItem.TextPreference(
                title = SyncStrings.discoverDevices,
                subtitle = SyncStrings.discoverDevicesSearching,
                enabled = isSyncEnabled,
                onClick = { showDiscovery = true },
            ),
        )
        transportItems.add(
            Preference.PreferenceItem.TextPreference(
                title = SyncStrings.testConnection,
                subtitle = testStatus,
                enabled = isSyncEnabled && syncHttpHost.isNotBlank() && syncHttpToken.isNotBlank(),
                onClick = {
                    scope.launch {
                        testStatus = SyncStrings.testingConnection
                        try {
                            val client = mihon.sync.transport.http.HttpTransport(
                                baseUrl = "http://$syncHttpHost:$syncHttpPort",
                                token = syncHttpToken,
                                ownsClient = true,
                            )
                            val isHealthy = client.checkHealth()
                            client.close()
                            testStatus =
                                if (isHealthy) SyncStrings.connectionSuccess else SyncStrings.connectionFailed
                            context.toast(testStatus ?: "")
                        } catch (e: Exception) {
                            testStatus = "${SyncStrings.connectionFailed}: ${e.message}"
                            context.toast(testStatus ?: "")
                        }
                    }
                },
            ),
        )

        transportItems.add(
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
        )

        transportItems.add(
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
        )

        val isSyncReady = syncHttpHost.isNotBlank() && syncHttpToken.isNotBlank()

        transportItems.add(
            Preference.PreferenceItem.TextPreference(
                title = SyncStrings.syncNow,
                subtitle = if (isRunning) {
                    SyncStrings.syncing
                } else {
                    "${SyncStrings.lastSync}: $lastSyncFormatted"
                },
                enabled = isSyncEnabled && isSyncReady,
                onClick = {
                    SyncWorker.runNow(context)
                    context.toast(SyncStrings.syncing)
                },
            ),
        )

        return listOf(
            Preference.PreferenceGroup(
                title = SyncStrings.librarySync,
                preferenceItems = transportItems,
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
}
