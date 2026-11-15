package mihon.companion.ui.sync

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.companion.CompanionLog
import mihon.companion.CompanionRuntime
import mihon.companion.R
import mihon.companion.data.IncomingLibrary
import mihon.companion.data.LibraryMerge
import mihon.companion.data.MihonBackupCodec
import java.io.FileNotFoundException

/** Moves the library in and out of Mihon `.tachibk` files picked through the system file picker. */
internal class BackupTransfer(private val runtime: CompanionRuntime, private val resolver: ContentResolver) {

    suspend fun import(uri: Uri): UiMessage = guarded(R.string.import_failed) {
        val backup = withContext(Dispatchers.IO) {
            val input = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
            input.use(MihonBackupCodec::decode)
        }
        val incoming = IncomingLibrary(
            mangas = backup.backupManga,
            categories = backup.backupCategories,
            sources = backup.backupSources,
            deviceId = runtime.sync.deviceId(),
        )
        val stats = runtime.library.update { LibraryMerge.merge(it, incoming, markDirty = true) }
        UiMessage(R.string.import_done, listOf(stats.mangaInserted, stats.mangaMerged))
    }

    suspend fun export(uri: Uri): UiMessage = guarded(R.string.export_failed) {
        val backup = MihonBackupCodec.fromLibrary(runtime.library.load())
        withContext(Dispatchers.IO) {
            val output = resolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException(uri.toString())
            output.use { MihonBackupCodec.encode(backup, it) }
        }
        UiMessage(R.string.export_done, listOf(backup.backupManga.size))
    }

    private suspend fun guarded(failure: Int, block: suspend () -> UiMessage): UiMessage = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        CompanionLog.warn("BackupTransfer", "Backup transfer failed", error)
        UiMessage(failure, listOf(error.message ?: error.javaClass.simpleName))
    }
}
