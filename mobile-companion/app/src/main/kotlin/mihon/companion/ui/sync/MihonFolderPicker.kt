package mihon.companion.ui.sync

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import mihon.companion.CompanionLog
import mihon.companion.CompanionRuntime
import mihon.companion.R
import mihon.companion.mihonfolder.MihonImportOutcome

/** Grants and forgets access to Mihon's storage folder, then reads it straight away. */
internal class MihonFolderPicker(private val runtime: CompanionRuntime, private val resolver: ContentResolver) {

    /** Returns a message for the outcome of the first read, or null when it needs none. */
    suspend fun pick(uri: Uri): UiMessage? {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val previous = runtime.mihonFolder.folderUri()
        if (previous != null && previous != uri.toString()) release(Uri.parse(previous))
        runtime.mihonFolder.setFolder(uri.toString(), runtime.mihonSource.folderName(uri.toString()))
        return when (val outcome = runtime.refresher.refresh().mihon) {
            is MihonImportOutcome.Imported ->
                UiMessage(R.string.import_done, listOf(outcome.stats.mangaInserted, outcome.stats.mangaMerged))
            else -> null // Problems stay visible in the Mihon folder section.
        }
    }

    suspend fun clear() {
        runtime.mihonFolder.folderUri()?.let { release(Uri.parse(it)) }
        runtime.mihonFolder.clearFolder()
    }

    private fun release(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (alreadyGone: SecurityException) {
            CompanionLog.warn("MihonFolder", "Folder grant was already released", alreadyGone)
        }
    }
}
