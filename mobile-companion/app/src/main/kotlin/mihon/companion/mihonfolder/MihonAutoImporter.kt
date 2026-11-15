package mihon.companion.mihonfolder

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mihon.companion.CompanionLog
import mihon.companion.data.LibraryStore
import mihon.companion.data.MihonImport
import java.io.File

/**
 * Reads Mihon's newest automatic backup from the folder the user granted and merges it into the
 * library. Android does not let other apps read Mihon's database, so its auto-backups
 * (the `.tachibk` files in `<Mihon folder>/autobackup`) are the only data the phone can pick up by itself.
 * A backup is imported once; the next import happens when Mihon writes a newer one.
 */
class MihonAutoImporter(
    private val store: LibraryStore,
    private val source: MihonBackupSource,
    private val stateFile: File,
    private val deviceId: () -> String,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    @Volatile
    private var state = readState()
    private val mutableStatus = MutableStateFlow(statusOf(state, problem = null))

    val status: StateFlow<MihonFolderStatus> = mutableStatus.asStateFlow()

    /** The granted folder, if any. */
    fun folderUri(): String? = state.folderUri

    suspend fun setFolder(uri: String, name: String) = mutex.withLock {
        // A different folder starts over: its newest backup must be read even if it is old.
        save(MihonFolderState(folderUri = uri, folderName = name), problem = null)
    }

    suspend fun clearFolder() = mutex.withLock { save(MihonFolderState(), problem = null) }

    suspend fun importIfNewer(): MihonImportOutcome = mutex.withLock {
        val folder = state.folderUri ?: return@withLock MihonImportOutcome.NotConfigured
        val outcome = try {
            withContext(io) { importFrom(folder) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            CompanionLog.warn(TAG, "Reading the Mihon backup failed", error)
            MihonImportOutcome.Failed(error.message ?: error.javaClass.simpleName)
        }
        val problem = outcome.takeIf {
            it is MihonImportOutcome.NoBackups || it is MihonImportOutcome.PermissionLost ||
                it is MihonImportOutcome.Failed
        }
        mutableStatus.value = statusOf(state, problem)
        outcome
    }

    private suspend fun importFrom(folder: String): MihonImportOutcome {
        val file = when (val newest = source.newest(folder)) {
            NewestBackup.NoBackups -> return MihonImportOutcome.NoBackups
            NewestBackup.PermissionLost -> return MihonImportOutcome.PermissionLost
            is NewestBackup.Found -> newest.file
        }
        if (file.name == state.lastFile && file.lastModified == state.lastModified) return MihonImportOutcome.UpToDate
        val stats = source.open(file).use { MihonImport.merge(store, it, deviceId()) }
        save(state.copy(lastFile = file.name, lastModified = file.lastModified, lastReadAt = clock()), problem = null)
        return MihonImportOutcome.Imported(file.name, stats)
    }

    private fun save(next: MihonFolderState, problem: MihonImportOutcome?) {
        stateFile.parentFile?.mkdirs()
        val temp = File(stateFile.parentFile, stateFile.name + ".tmp")
        temp.writeText(json.encodeToString(MihonFolderState.serializer(), next))
        if (!temp.renameTo(stateFile)) {
            CompanionLog.warn(TAG, "Could not save the Mihon folder state")
        }
        state = next
        mutableStatus.value = statusOf(next, problem)
    }

    private fun readState(): MihonFolderState {
        if (!stateFile.exists()) return MihonFolderState()
        return try {
            json.decodeFromString(MihonFolderState.serializer(), stateFile.readText())
        } catch (error: Exception) {
            CompanionLog.warn(TAG, "Mihon folder state unreadable; the folder must be chosen again", error)
            MihonFolderState()
        }
    }

    private fun statusOf(state: MihonFolderState, problem: MihonImportOutcome?) = MihonFolderStatus(
        folderName = state.folderName,
        lastFile = state.lastFile,
        lastReadAt = state.lastReadAt,
        problem = problem,
    )

    private companion object {
        const val TAG = "MihonFolder"
    }
}
