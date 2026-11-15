package mihon.companion.data

import mihon.sync.core.model.AndroidBackupCategory
import mihon.sync.core.model.AndroidBackupChapter
import mihon.sync.core.model.AndroidBackupManga
import mihon.sync.transport.gdrive.DriveSnapshotStore

internal fun chapter(url: String, modified: Long = 1_000, read: Boolean = false, number: Float = 1f) =
    AndroidBackupChapter(url = url, name = url, read = read, chapterNumber = number, lastModifiedAt = modified)

internal fun manga(
    url: String,
    modified: Long = 1_000,
    chapters: List<AndroidBackupChapter> = emptyList(),
    categories: List<Long> = emptyList(),
) = AndroidBackupManga(
    source = 42,
    url = url,
    title = "Title $url",
    lastModifiedAt = modified,
    chapters = chapters,
    categories = categories,
)

internal fun category(name: String, order: Long) = AndroidBackupCategory(name = name, order = order)

/** Drive appDataFolder in memory: one document per device, like DriveRestSnapshotStore. */
internal class InMemoryDrive(
    private val files: MutableMap<String, ByteArray> = linkedMapOf(),
    private val name: String = "shared",
) : DriveSnapshotStore {
    override suspend fun read(): ByteArray? = files[name]
    override suspend fun write(data: ByteArray) {
        files[name] = data
    }
    override suspend fun readAll(): List<ByteArray> = files.values.toList()
    override fun deviceStore(deviceId: String): DriveSnapshotStore = InMemoryDrive(files, "mihon-sync-$deviceId.pb")
}
