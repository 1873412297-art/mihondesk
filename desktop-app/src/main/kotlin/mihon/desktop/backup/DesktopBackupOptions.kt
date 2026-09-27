package mihon.desktop.backup

import mihon.sync.core.model.AndroidBackup

data class DesktopBackupOptions(
    val chapterState: Boolean = true,
    val categories: Boolean = true,
    val tracking: Boolean = true,
    val history: Boolean = true,
    val readProgress: Boolean = true,
    val settings: Boolean = true,
) {
    fun hasAnySelected(): Boolean =
        chapterState || categories || tracking || history || readProgress || settings
}

fun AndroidBackup.filterByOptions(options: DesktopBackupOptions): AndroidBackup {
    val filteredManga = backupManga.map { manga ->
        manga.copy(
            categories = if (options.categories) manga.categories else emptyList(),
            tracking = if (options.tracking) manga.tracking else emptyList(),
            history = if (options.history) manga.history else emptyList(),
            chapters = manga.chapters.map { ch ->
                ch.copy(
                    read = if (options.chapterState) ch.read else false,
                    bookmark = if (options.chapterState) ch.bookmark else false,
                    lastPageRead = if (options.readProgress) ch.lastPageRead else 0L,
                )
            },
        )
    }
    return copy(
        backupManga = filteredManga,
        backupCategories = if (options.categories) backupCategories else emptyList(),
        backupPreferences = if (options.settings) backupPreferences else emptyList(),
        backupSourcePreferences = if (options.settings) backupSourcePreferences else emptyList(),
    )
}
