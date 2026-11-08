package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.download.CleanReport
import mihon.desktop.download.DownloadCacheCleaner
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.storage.StorageUsage
import mihon.desktop.storage.StorageUsageCalculator
import java.nio.file.Path

/** Storage usage, download cache and image cache cleaning. */
@Composable
internal fun StorageCleanerCard(
    presenter: SettingsPresenter,
    downloadCacheCleaner: DownloadCacheCleaner?,
    downloadsDir: Path?,
    diskCacheDir: Path?,
    profileRoot: Path?,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val state = remember(downloadCacheCleaner, diskCacheDir, strings) {
        StorageCleanerState(scope, downloadCacheCleaner, diskCacheDir, strings)
    }
    LaunchedEffect(downloadsDir, profileRoot, state.usageRefresh) {
        state.refresh(presenter.store, profileRoot, downloadsDir)
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("storage-cleaner-card"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StorageCleanerHeader(strings, state.downloadSizeBytes)
            state.storageUsage?.let { StorageUsageRows(it) }
            StorageCleanerButtons(
                isCleaning = state.isCleaning,
                hasDownloadCleaner = downloadCacheCleaner != null,
                hasImageCache = diskCacheDir != null,
                onCleanReadChapters = state::cleanReadChapters,
                onCleanImageCache = state::cleanImageCache,
            )
            OrphanDownloadsSection(
                isCleaning = state.isCleaning,
                enabled = downloadCacheCleaner != null,
                preview = state.orphanPreview,
                onPreview = state::previewOrphans,
                onConfirm = state::deleteOrphans,
                onDismiss = state::dismissPreview,
            )
            state.message?.let { StorageCleanerMessage(it) }
        }
    }
}

/** All mutable state and the three async actions of the storage cleaner card. */
private class StorageCleanerState(
    private val scope: CoroutineScope,
    private val cleaner: DownloadCacheCleaner?,
    private val diskCacheDir: Path?,
    private val strings: DesktopStrings,
) {
    var downloadSizeBytes by mutableStateOf<Long?>(null)
    var storageUsage by mutableStateOf<StorageUsage?>(null)
    var usageRefresh by mutableStateOf(0)
    var isCleaning by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var orphanPreview by mutableStateOf<CleanReport?>(null)

    suspend fun refresh(preferenceStore: DesktopPreferenceStore, profileRoot: Path?, downloadsDir: Path?) {
        cleaner?.let { downloadSizeBytes = withContext(Dispatchers.IO) { it.calculateDownloadSize() } }
        if (profileRoot == null || downloadsDir == null) return
        storageUsage = withContext(Dispatchers.IO) {
            calculateUsage(preferenceStore, profileRoot, downloadsDir)
        }
    }

    fun cleanReadChapters() = guardCleanup {
        message = runCleanReadChapters(cleaner, strings)
        usageRefresh++
    }

    fun cleanImageCache() = guardCleanup {
        message = runCleanImageCache(cleaner, diskCacheDir, strings)
        usageRefresh++
    }

    fun previewOrphans() = guardCleanup {
        val report = previewOrphanDownloads(cleaner)
        if (report.deletedChaptersCount == 0) {
            message = strings.text(UiText.DeleteOrphanDownloadsNone)
        } else {
            orphanPreview = report
        }
    }

    fun deleteOrphans() {
        orphanPreview = null
        guardCleanup {
            message = runDeleteOrphanDownloads(cleaner, strings)
            usageRefresh++
        }
    }

    private fun guardCleanup(block: suspend () -> Unit) {
        scope.guardStorageCleanup(
            setBusy = { isCleaning = it },
            reportFailure = { message = strings.text(UiText.StorageCleanerFailed) },
            block = block,
        )
    }

    fun dismissPreview() {
        orphanPreview = null
    }
}

internal fun CoroutineScope.guardStorageCleanup(
    setBusy: (Boolean) -> Unit,
    reportFailure: (Exception) -> Unit,
    block: suspend () -> Unit,
) {
    launch {
        setBusy(true)
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            DesktopLogger.warn("StorageCleaner", "Storage cleanup failed", error)
            reportFailure(error)
        } finally {
            setBusy(false)
        }
    }
}

private fun calculateUsage(
    preferenceStore: DesktopPreferenceStore,
    profileRoot: Path,
    downloadsDir: Path,
): StorageUsage {
    val configuredBackup = preferenceStore.load().backupStoragePath
    val backupDir = configuredBackup.takeIf { it.isNotBlank() }
        ?.let { runCatching { Path.of(it) }.getOrNull() }
        ?: profileRoot.resolve("backups")
    return StorageUsageCalculator().calculate(profileRoot, downloadsDir, backupDir)
}

private suspend fun runCleanReadChapters(cleaner: DownloadCacheCleaner?, strings: DesktopStrings): String? {
    cleaner ?: return null
    val report = withContext(Dispatchers.IO) { cleaner.deleteReadChapters() }
    return "${strings.storageCleanerClearReadSuccess}: " +
        "${report.deletedChaptersCount} (${formatStorageSize(report.freedBytes)})"
}

private suspend fun runCleanImageCache(
    cleaner: DownloadCacheCleaner?,
    diskCacheDir: Path?,
    strings: DesktopStrings,
): String? {
    if (cleaner == null || diskCacheDir == null) return null
    val cleared = withContext(Dispatchers.IO) { cleaner.clearImageCaches(diskCacheDir) }
    return "${strings.storageCleanerClearImageCacheSuccess}: ${formatStorageSize(cleared)}"
}

private suspend fun previewOrphanDownloads(cleaner: DownloadCacheCleaner?): CleanReport =
    cleaner?.let { withContext(Dispatchers.IO) { it.deleteDownloadsNotInLibrary(dryRun = true) } }
        ?: CleanReport(deletedChaptersCount = 0, freedBytes = 0L)

private suspend fun runDeleteOrphanDownloads(cleaner: DownloadCacheCleaner?, strings: DesktopStrings): String? {
    cleaner ?: return null
    val report = withContext(Dispatchers.IO) { cleaner.deleteDownloadsNotInLibrary() }
    return strings.text(
        UiText.DeleteOrphanDownloadsDone,
        report.deletedChaptersCount.toString(),
        formatStorageSize(report.freedBytes),
    )
}

@Composable
private fun StorageCleanerHeader(strings: DesktopStrings, downloadSizeBytes: Long?) {
    Text(strings.storageCleanerTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        strings.storageCleanerDescription,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = "${strings.storageCleanerDownloadSize}: ${formatStorageSize(downloadSizeBytes ?: 0L)}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.testTag("storage-cleaner-size-label"),
    )
}

@Composable
private fun StorageUsageRows(usage: StorageUsage) {
    val strings = LocalStrings.current
    listOf(
        strings.storageUsageCache to usage.imageCache,
        strings.storageUsageDatabase to usage.database,
        strings.storageUsageExtensions to usage.extensions,
        strings.storageUsageCovers to usage.covers,
        strings.storageUsageBackups to usage.backups,
        strings.storageUsageLogs to usage.logs,
    ).forEach { (label, bytes) ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(formatStorageSize(bytes), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StorageCleanerButtons(
    isCleaning: Boolean,
    hasDownloadCleaner: Boolean,
    hasImageCache: Boolean,
    onCleanReadChapters: () -> Unit,
    onCleanImageCache: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(
            onClick = onCleanReadChapters,
            enabled = !isCleaning && hasDownloadCleaner,
            modifier = Modifier.testTag("clean-read-chapters-button"),
        ) {
            Text(strings.storageCleanerClearRead)
        }
        OutlinedButton(
            onClick = onCleanImageCache,
            enabled = !isCleaning && hasImageCache,
            modifier = Modifier.testTag("clean-image-cache-button"),
        ) {
            Text(strings.storageCleanerClearImageCache)
        }
    }
}

@Composable
private fun OrphanDownloadsSection(
    isCleaning: Boolean,
    enabled: Boolean,
    preview: CleanReport?,
    onPreview: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    OutlinedButton(
        onClick = onPreview,
        enabled = !isCleaning && enabled,
        modifier = Modifier.testTag("clean-orphan-downloads-button"),
    ) {
        Text(strings.text(UiText.DeleteOrphanDownloads))
    }
    preview?.let { report -> OrphanDownloadsDialog(strings, report, onConfirm, onDismiss) }
}

@Composable
private fun OrphanDownloadsDialog(
    strings: DesktopStrings,
    preview: CleanReport,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.text(UiText.DeleteOrphanDownloads)) },
        text = {
            Text(
                "${preview.deletedChaptersCount} · ${formatStorageSize(preview.freedBytes)}",
                modifier = Modifier.testTag("orphan-downloads-preview"),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("orphan-downloads-confirm")) {
                Text(strings.text(UiText.ContextDelete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.text(UiText.AppUpdateCancel)) }
        },
    )
}

@Composable
private fun StorageCleanerMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("storage-cleaner-message"),
    )
}
