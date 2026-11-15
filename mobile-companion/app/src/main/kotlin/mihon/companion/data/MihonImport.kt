package mihon.companion.data

import java.io.InputStream

/** Merges a Mihon backup into the library; everything it changes is queued for upload to Drive. */
object MihonImport {
    suspend fun merge(store: LibraryStore, input: InputStream, deviceId: String): MergeStats {
        val backup = MihonBackupCodec.decode(input)
        val incoming = IncomingLibrary(
            mangas = backup.backupManga,
            categories = backup.backupCategories,
            sources = backup.backupSources,
            deviceId = deviceId,
        )
        return store.update { LibraryMerge.merge(it, incoming, markDirty = true) }
    }
}
