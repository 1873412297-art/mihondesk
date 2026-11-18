package mihon.companion.data

/** Merges a Mihon backup into the library; everything it changes is queued for upload to Drive. */
object MihonImport {
    suspend fun merge(store: LibraryStore, backup: MihonBackup, deviceId: String): MergeStats {
        val incoming = IncomingLibrary(
            mangas = backup.backupManga,
            categories = backup.backupCategories,
            sources = backup.backupSources,
            deviceId = deviceId,
        )
        return store.update { LibraryMerge.merge(it, incoming, markDirty = true) }
    }
}
