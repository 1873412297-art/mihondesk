package mihon.desktop.library.db

import app.cash.sqldelight.db.SqlDriver
import mihon.desktop.library.reader.ReaderLibraryPort
import mihon.desktop.library.repository.LibraryDatabaseCleaner
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository

class SqlDelightLibraryRepository(
    private val driver: SqlDriver,
    val database: DesktopLibraryDatabase,
) : LibraryRepository by SqlDelightLibraryReadDelegate(database, driver),
    LibraryMutationPort by SqlDelightLibraryMutationDelegate(database),
    LibraryDatabaseCleaner by SqlDelightLibraryCleanerDelegate(database, driver),
    ReaderLibraryPort by SqlDelightReaderDelegate(database),
    AutoCloseable {
    override fun close() {
        driver.close()
    }
}
