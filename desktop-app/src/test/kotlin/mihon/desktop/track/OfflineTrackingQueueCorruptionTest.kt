package mihon.desktop.track

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class OfflineTrackingQueueCorruptionTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `corrupt queue is preserved instead of being overwritten by the next save`() = runBlocking {
        val queueFile = tempDir.resolve("queue.json")
        Files.writeString(queueFile, "{not json")
        val queue = OfflineTrackingQueue(queueFile)

        assertTrue(queue.peekAll().isEmpty())
        val preserved = tempDir.resolve("queue.json.corrupt")
        assertTrue(Files.exists(preserved))
        assertEquals("{not json", Files.readString(preserved))

        queue.enqueue(QueuedTrackingUpdate(mangaId = 1, trackerId = 2, chapterNumber = 3.0))

        assertEquals(1, queue.peekAll().size)
        assertEquals("{not json", Files.readString(preserved))
    }
}
