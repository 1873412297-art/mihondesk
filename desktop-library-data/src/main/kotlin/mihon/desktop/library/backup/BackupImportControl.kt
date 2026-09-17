package mihon.desktop.library.backup

import kotlinx.coroutines.CancellationException

enum class BackupImportStage { READING, VALIDATING, RESTORING, COMMITTING }

data class BackupImportProgress(
    val stage: BackupImportStage = BackupImportStage.READING,
    val completedManga: Int = 0,
    val totalManga: Int = 0,
)

/** One control per restore. Cancellation and the final commit gate are mutually exclusive. */
class BackupImportControl(
    private val checkActive: () -> Unit = {},
    private val onProgress: (BackupImportProgress) -> Unit = {},
) {
    private val lock = Any()
    private var committing = false

    @Volatile
    var isCancellationRequested: Boolean = false
        private set

    fun cancel(): Boolean = synchronized(lock) {
        if (committing) return false
        isCancellationRequested = true
        true
    }

    fun checkpoint() = synchronized(lock) {
        if (isCancellationRequested) throw CancellationException("Backup restore cancelled")
        if (!committing) checkActive()
    }

    internal fun report(progress: BackupImportProgress) {
        checkpoint()
        onProgress(progress)
        checkpoint()
    }

    internal fun beginCommit(totalManga: Int) {
        synchronized(lock) {
            checkpoint()
            committing = true
        }
        onProgress(BackupImportProgress(BackupImportStage.COMMITTING, totalManga, totalManga))
    }
}
