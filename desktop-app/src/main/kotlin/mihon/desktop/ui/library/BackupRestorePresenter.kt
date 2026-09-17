package mihon.desktop.ui.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.library.backup.BackupImportControl
import mihon.desktop.library.backup.BackupImportProgress
import mihon.desktop.library.backup.BackupImportStage
import java.nio.file.Path

sealed interface BackupRestoreState {
    data object Idle : BackupRestoreState
    data class Running(val progress: BackupImportProgress, val cancelling: Boolean = false) : BackupRestoreState {
        val canCancel: Boolean get() = !cancelling && progress.stage != BackupImportStage.COMMITTING
    }
    data class Finished(val outcome: ImportActionState) : BackupRestoreState
    data object Cancelled : BackupRestoreState
}

class BackupRestorePresenter(
    private val scope: CoroutineScope,
    private val restore: suspend (Path, BackupImportControl) -> ImportActionState,
) : AutoCloseable {
    private val lock = Any()
    private val mutableState = MutableStateFlow<BackupRestoreState>(BackupRestoreState.Idle)
    val state = mutableState.asStateFlow()
    private var active: BackupImportControl? = null
    private var job: Job? = null
    private var closed = false

    fun start(path: Path): Boolean = synchronized(lock) {
        if (closed || active != null || !scope.isActive) return false
        lateinit var control: BackupImportControl
        lateinit var task: Job
        var terminal: BackupRestoreState? = null
        control = BackupImportControl(
            checkActive = { task.ensureActive() },
            onProgress = { progress ->
                synchronized(lock) {
                    if (active === control) {
                        mutableState.value = BackupRestoreState.Running(progress, control.isCancellationRequested)
                    }
                }
            },
        )
        active = control
        mutableState.value = BackupRestoreState.Running(BackupImportProgress())
        task = scope.launch(start = CoroutineStart.LAZY) {
            // Check cancellation inside the transaction. A late coroutine cancellation must not
            // turn a committed restore into a "cancelled" result at an IO dispatcher boundary.
            withContext(NonCancellable) {
                terminal = try {
                    BackupRestoreState.Finished(restore(path, control))
                } catch (_: CancellationException) {
                    BackupRestoreState.Cancelled
                } catch (_: Exception) {
                    BackupRestoreState.Finished(ImportActionState.Failed("Backup restore failed"))
                }
            }
        }
        job = task
        task.invokeOnCompletion {
            synchronized(lock) {
                if (active === control) {
                    active = null
                    job = null
                    mutableState.value = terminal ?: BackupRestoreState.Cancelled
                }
            }
        }
        task.start()
        true
    }

    fun cancel(): Boolean = synchronized(lock) {
        val current = mutableState.value as? BackupRestoreState.Running ?: return false
        if (active?.cancel() != true) return false
        mutableState.value = current.copy(cancelling = true)
        true
    }

    fun dismiss() = synchronized(lock) {
        if (active == null) mutableState.value = BackupRestoreState.Idle
    }

    override fun close() = synchronized(lock) {
        closed = true
        active?.cancel()
        job?.cancel()
        Unit
    }
}
