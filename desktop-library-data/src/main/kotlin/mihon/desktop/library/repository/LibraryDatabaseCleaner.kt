package mihon.desktop.library.repository

data class NonLibrarySourceCount(val sourceId: Long, val sourceName: String, val mangaCount: Int)

/** Removes online records that are not in the library. Call from an IO dispatcher. */
interface LibraryDatabaseCleaner {
    fun nonLibrarySourceCounts(): List<NonLibrarySourceCount>

    /** Returns the number of manga removed. Their dependent rows cascade in one transaction. */
    fun clearNonLibraryManga(sourceIds: Set<Long>, keepReadManga: Boolean): Int
}
