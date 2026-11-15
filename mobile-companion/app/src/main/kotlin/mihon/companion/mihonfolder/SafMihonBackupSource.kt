package mihon.companion.mihonfolder

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import mihon.companion.CompanionLog
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Lists Mihon's backups through the Storage Access Framework. The user may pick either Mihon's
 * storage folder (which contains `autobackup/`) or the `autobackup` folder itself.
 */
class SafMihonBackupSource(private val context: Context) : MihonBackupSource {
    override fun newest(folderUri: String): NewestBackup {
        val root = try {
            DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
        } catch (denied: SecurityException) {
            CompanionLog.warn("MihonFolder", "Access to the Mihon folder was revoked", denied)
            return NewestBackup.PermissionLost
        }
        if (root == null || !root.canRead()) return NewestBackup.PermissionLost
        val candidates = listOfNotNull(root.findFile(AUTO_BACKUP_DIR)?.takeIf { it.isDirectory }, root)
        val newest = candidates.asSequence()
            .flatMap { it.listFiles().asSequence() }
            .filter { it.isFile && isBackupName(it.name) }
            .maxByOrNull { it.lastModified() }
            ?: return NewestBackup.NoBackups
        return NewestBackup.Found(MihonBackupFile(newest.name.orEmpty(), newest.lastModified(), newest.uri.toString()))
    }

    override fun open(file: MihonBackupFile): InputStream =
        context.contentResolver.openInputStream(Uri.parse(file.uri)) ?: throw FileNotFoundException(file.name)

    /** Display name of a granted folder, for the settings row. */
    fun folderName(folderUri: String): String =
        DocumentFile.fromTreeUri(context, Uri.parse(folderUri))?.name ?: folderUri

    private fun isBackupName(name: String?): Boolean =
        name != null && BACKUP_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

    private companion object {
        const val AUTO_BACKUP_DIR = "autobackup"

        /** Mihon writes `.tachibk`; older Tachiyomi builds wrote `.proto.gz`. */
        val BACKUP_EXTENSIONS = listOf(".tachibk", ".proto.gz")
    }
}
