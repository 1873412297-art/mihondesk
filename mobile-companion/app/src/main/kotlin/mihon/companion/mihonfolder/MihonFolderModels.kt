package mihon.companion.mihonfolder

import kotlinx.serialization.Serializable
import mihon.companion.data.MergeStats
import java.io.InputStream

/** One backup file Mihon wrote into its storage folder. */
data class MihonBackupFile(val name: String, val lastModified: Long, val uri: String)

sealed interface NewestBackup {
    data class Found(val file: MihonBackupFile) : NewestBackup
    data object NoBackups : NewestBackup

    /** The folder grant was revoked or the folder was removed. */
    data object PermissionLost : NewestBackup
}

/** Read access to Mihon's storage folder. Android implementation: [SafMihonBackupSource]. */
interface MihonBackupSource {
    fun newest(folderUri: String): NewestBackup
    fun open(file: MihonBackupFile): InputStream
}

sealed interface MihonImportOutcome {
    data object NotConfigured : MihonImportOutcome
    data object UpToDate : MihonImportOutcome
    data class Imported(val file: String, val stats: MergeStats) : MihonImportOutcome
    data object NoBackups : MihonImportOutcome
    data object PermissionLost : MihonImportOutcome
    data class Failed(val message: String) : MihonImportOutcome
}

/** What the sync screen shows about the Mihon folder. */
data class MihonFolderStatus(
    val folderName: String? = null,
    val lastFile: String? = null,
    val lastReadAt: Long = 0,
    /** Set when the last attempt hit a problem the user should see. */
    val problem: MihonImportOutcome? = null,
) {
    val configured: Boolean get() = folderName != null
}

@Serializable
internal data class MihonFolderState(
    val folderUri: String? = null,
    val folderName: String? = null,
    val lastFile: String? = null,
    val lastModified: Long = 0,
    val lastReadAt: Long = 0,
)
