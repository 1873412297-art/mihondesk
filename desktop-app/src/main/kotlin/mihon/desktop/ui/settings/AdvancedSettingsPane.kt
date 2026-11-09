package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.diagnostics.DiagnosticBundleService
import mihon.desktop.diagnostics.DiagnosticSummary
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import java.nio.file.Path

@Composable
internal fun AdvancedSettingsPane(
    preferenceStore: DesktopPreferenceStore,
    presenter: SettingsPresenter,
    backgroundScheduler: mihon.desktop.platform.WindowsBackgroundScheduler?,
    scheduleCoordinator: BackgroundScheduleCoordinator,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
    diagnosticService: DiagnosticBundleService?,
    onOpenCookieManager: () -> Unit = {},
    downloadCacheCleaner: mihon.desktop.download.DownloadCacheCleaner? = null,
    downloadsDir: Path? = null,
    diskCacheDir: Path? = null,
    profileRoot: Path? = null,
    databaseCleaner: mihon.desktop.library.repository.LibraryDatabaseCleaner? = null,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf<DiagnosticSummary?>(null) }
    var checkingIntegrity by remember { mutableStateOf(false) }
    var integrityError by remember { mutableStateOf<String?>(null) }
    var bundleExportPath by remember { mutableStateOf<String?>(null) }
    var nonLibrarySources by remember {
        mutableStateOf(emptyList<mihon.desktop.library.repository.NonLibrarySourceCount>())
    }
    var selectedSources by remember { mutableStateOf(emptySet<Long>()) }
    var keepReadManga by remember { mutableStateOf(true) }
    var confirmDatabaseClear by remember { mutableStateOf(false) }
    var clearingDatabase by remember { mutableStateOf(false) }
    var databaseClearMessage by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(databaseCleaner) {
        nonLibrarySources = if (databaseCleaner == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) { databaseCleaner.nonLibrarySourceCounts() }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionAdvanced,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // Windows-only integrations: the task scheduler and the JBR title bar have no macOS equivalent.
        if (mihon.desktop.platform.DesktopPlatform.isWindows) {
            BackgroundSettingsCard(presenter, backgroundScheduler, scheduleCoordinator, onPreferencesChanged)
            SystemTitleBarCard(presenter, onPreferencesChanged)
        }
        RenderBackendCard(preferenceStore)
        NetworkSettingsCard(preferenceStore)

        // Storage & Cache Cleaner Card
        StorageCleanerCard(presenter, downloadCacheCleaner, downloadsDir, diskCacheDir, profileRoot)

        if (databaseCleaner != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("clear-database-card"),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        strings.clearDatabaseTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(strings.clearDatabaseDescription, style = MaterialTheme.typography.bodySmall)
                    if (nonLibrarySources.isEmpty()) {
                        Text(strings.clearDatabaseEmpty, modifier = Modifier.testTag("clear-database-empty"))
                    } else {
                        nonLibrarySources.forEach { source ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${source.sourceName} (${source.mangaCount})", modifier = Modifier.weight(1f))
                                Switch(
                                    checked = source.sourceId in selectedSources,
                                    onCheckedChange = { checked ->
                                        selectedSources = if (checked) {
                                            selectedSources + source.sourceId
                                        } else {
                                            selectedSources - source.sourceId
                                        }
                                    },
                                    modifier = Modifier.testTag("clear-database-source-${source.sourceId}"),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(strings.clearDatabaseKeepRead, modifier = Modifier.weight(1f))
                            Switch(
                                checked = keepReadManga,
                                onCheckedChange = { keepReadManga = it },
                                modifier = Modifier.testTag("clear-database-keep-read"),
                            )
                        }
                        Button(
                            onClick = { confirmDatabaseClear = true },
                            enabled = selectedSources.isNotEmpty() && !clearingDatabase,
                            modifier = Modifier.testTag("clear-database-button"),
                        ) {
                            Text(strings.clearDatabaseAction)
                        }
                    }
                    databaseClearMessage?.let { Text(it, modifier = Modifier.testTag("clear-database-message")) }
                }
            }
            if (confirmDatabaseClear) {
                AlertDialog(
                    onDismissRequest = { confirmDatabaseClear = false },
                    title = { Text(strings.clearDatabaseTitle) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(strings.clearDatabaseConfirm)
                            if (!keepReadManga) {
                                Text(strings.clearDatabaseReadWarning, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmDatabaseClear = false
                                clearingDatabase = true
                                scope.launch {
                                    try {
                                        val removed = withContext(Dispatchers.IO) {
                                            databaseCleaner.clearNonLibraryManga(selectedSources, keepReadManga)
                                        }
                                        nonLibrarySources = withContext(Dispatchers.IO) {
                                            databaseCleaner.nonLibrarySourceCounts()
                                        }
                                        selectedSources = emptySet()
                                        databaseClearMessage = strings.clearDatabaseResult(removed)
                                    } catch (error: Exception) {
                                        databaseClearMessage =
                                            "${strings.clearDatabaseFailed}: ${error.message.orEmpty()}"
                                    } finally {
                                        clearingDatabase = false
                                    }
                                }
                            },
                            modifier = Modifier.testTag("clear-database-confirm"),
                        ) { Text(strings.clearDatabaseAction) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDatabaseClear = false }) {
                            Text(strings.text(UiText.AppUpdateCancel))
                        }
                    },
                )
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    strings.cookieManagerTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    strings.cookieManagerDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onOpenCookieManager,
                    modifier = Modifier.testTag("open-cookie-manager-button"),
                ) {
                    Text(strings.cookieManagerButton)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    strings.settingsDiagnosticsTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = {
                            diagnosticService?.let {
                                scope.launch {
                                    checkingIntegrity = true
                                    integrityError = null
                                    try {
                                        summary = withContext(Dispatchers.Default) { it.createSummary() }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        mihon.desktop.logging.DesktopLogger.warn(
                                            "Settings",
                                            "Integrity check failed",
                                            error,
                                        )
                                        integrityError = strings.text(UiText.IntegrityCheckFailed)
                                    } finally {
                                        checkingIntegrity = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("check-integrity-button"),
                    ) {
                        if (checkingIntegrity) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                        } else {
                            Text(strings.settingsRunIntegrityCheck)
                        }
                    }

                    Button(
                        onClick = {
                            diagnosticService?.let {
                                scope.launch {
                                    val tempZip = Path.of(System.getProperty("java.io.tmpdir"))
                                        .resolve("mihon-diagnostics-${System.currentTimeMillis()}.zip")
                                    withContext(Dispatchers.IO) { it.exportBundle(tempZip) }
                                    bundleExportPath = tempZip.toString()
                                }
                            }
                        },
                        modifier = Modifier.testTag("export-diagnostic-bundle-button"),
                    ) {
                        Text(strings.settingsExportDiagnosticBundle)
                    }
                }

                integrityError?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("integrity-check-error"),
                    )
                }

                summary?.let { s ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        strings.settingsDatabaseIntegrity(s.databaseIntegrity.joinToString(", ")),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(strings.settingsOsInfo("${s.osName} ${s.osVersion} (${s.osArch})"))
                    Text(strings.settingsJavaInfo("${s.javaVersion} (${s.javaVendor})"))
                    Text(strings.settingsLogFilesCount(s.logFileCount))
                    if (s.snapshotCount > 0) {
                        val sizeKb = (s.snapshotSizeBytes + 1023) / 1024
                        Text(strings.text(UiText.MigrationSnapshotsSummary, s.snapshotCount, sizeKb))
                    }
                }

                bundleExportPath?.let { path ->
                    Text(
                        strings.settingsBundleExportedTo(path),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
