package mihon.companion.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.companion.CompanionLog
import java.io.File
import java.io.IOException

/**
 * Owns the phone library: one in-memory [StoredLibrary] exposed as a [StateFlow], written through to
 * [file] atomically (temp file + rename) on every change. All writers go through [update].
 */
class LibraryStore(
    private val file: File,
    private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow(StoredLibrary())
    private var loaded = false

    val library: StateFlow<StoredLibrary> = state.asStateFlow()

    suspend fun load(): StoredLibrary = mutex.withLock { ensureLoaded() }

    /** Applies [transform] and persists the result; returns it together with [transform]'s extra value. */
    suspend fun <T> update(transform: (StoredLibrary) -> Pair<StoredLibrary, T>): T = mutex.withLock {
        val current = ensureLoaded()
        val (next, extra) = transform(current)
        if (next != current) {
            persist(next)
            state.value = next
        }
        extra
    }

    suspend fun edit(transform: (StoredLibrary) -> StoredLibrary) {
        update { transform(it) to Unit }
    }

    /** Marks every local change up to [seq] as delivered to Drive. */
    suspend fun acknowledge(seq: Long) = edit { if (seq > it.ackedSeq) it.copy(ackedSeq = seq) else it }

    private suspend fun ensureLoaded(): StoredLibrary {
        if (!loaded) {
            state.value = withContext(io) { read() }
            loaded = true
        }
        return state.value
    }

    private fun read(): StoredLibrary {
        if (!file.exists()) return StoredLibrary()
        return try {
            ProtoBuf.decodeFromByteArray<StoredLibrary>(file.readBytes())
        } catch (error: Exception) {
            // Keep the unreadable file for diagnosis instead of overwriting it with an empty library.
            val backup = File(file.parentFile, file.name + ".corrupt-" + System.currentTimeMillis())
            file.renameTo(backup)
            CompanionLog.warn(TAG, "Library file unreadable, moved to ${backup.name}; starting empty", error)
            StoredLibrary()
        }
    }

    private suspend fun persist(library: StoredLibrary) = withContext(io) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(ProtoBuf.encodeToByteArray(library))
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("Could not replace ${file.name}")
        }
    }

    private companion object {
        const val TAG = "LibraryStore"
    }
}
