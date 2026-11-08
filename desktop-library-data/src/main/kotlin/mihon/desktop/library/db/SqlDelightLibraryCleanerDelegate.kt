package mihon.desktop.library.db

import kotlinx.coroutines.flow.map
import mihon.desktop.library.repository.LibraryDatabaseCleaner
import mihon.desktop.library.repository.NonLibrarySourceCount

internal class SqlDelightLibraryCleanerDelegate(
    private val database: DesktopLibraryDatabase,
    driver: app.cash.sqldelight.db.SqlDriver,
) : LibraryDatabaseCleaner {
    private val queries = database.libraryQueries
    private val read = SqlDelightLibraryReadDelegate(database, driver)

    override fun nonLibrarySourceCounts(): List<NonLibrarySourceCount> {
        val names = read.allSourcesSnapshot().associate { it.sourceId to it.name }
        return read.allMangaSnapshot()
            .asSequence()
            .filter { !it.favorite && it.sourceId != 0L }
            .groupingBy { it.sourceId }
            .eachCount()
            .map { (sourceId, count) ->
                NonLibrarySourceCount(sourceId, names[sourceId] ?: "Source #$sourceId", count)
            }
            .sortedBy { it.sourceName.lowercase() }
    }

    override fun clearNonLibraryManga(sourceIds: Set<Long>, keepReadManga: Boolean): Int =
        database.transactionWithResult {
            val ids = sourceIds.filter { it != 0L }.toSet()
            if (ids.isEmpty()) return@transactionWithResult 0
            ids.sumOf { sourceId ->
                queries.deleteNonLibraryMangaBySource(sourceId, if (keepReadManga) 1L else 0L).value
            }.toInt()
        }
}
