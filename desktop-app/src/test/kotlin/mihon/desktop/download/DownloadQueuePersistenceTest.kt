package mihon.desktop.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class DownloadQueuePersistenceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `failure callback can persist again without holding the save lock`() {
        val blockedParent = tempDir.resolve("blocked")
        Files.writeString(blockedParent, "not a directory")
        val store = DownloadStore(blockedParent.resolve("downloads.json"))
        val failures = AtomicInteger()
        val workerFinished = AtomicBoolean(false)
        lateinit var persistence: DownloadQueuePersistence
        persistence = DownloadQueuePersistence(
            store = store,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            queue = { emptyList() },
            onSaved = {},
            onFailure = {
                if (failures.incrementAndGet() == 1) {
                    val worker = thread {
                        runCatching { persistence.persistImmediate() }
                        workerFinished.set(true)
                    }
                    worker.join(2_000)
                }
            },
        )

        runCatching { persistence.persistImmediate() }

        assertTrue(failures.get() >= 2, "the failure callback should be invoked for both saves")
        assertTrue(workerFinished.get(), "onFailure must not hold the persistence lock")
    }
}
