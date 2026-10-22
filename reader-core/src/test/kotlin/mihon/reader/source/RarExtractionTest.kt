package mihon.reader.source

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import mihon.reader.memory.BoundedReaderMemoryBudget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * junrar's getInputStream extracts on a background thread through a pipe and swallows extraction
 * errors, so a failed extraction reached the decoder as a silently truncated page ("unrecognized
 * image signature") instead of a typed RAR error.
 */
class RarExtractionTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    /** The committed fixture's page is a benign text payload, not an image. */
    private val fixturePage = "Mihon W Task 3 benign archive fixture page.\n".toByteArray()

    @Test
    fun `a corrupt RAR payload fails loudly instead of yielding truncated page bytes`(): Unit = runBlocking {
        val archive = ArchiveFixtures.copyCommittedFixture(temporaryDirectory, "valid-rar4.rar")
        val bytes = Files.readAllBytes(archive)
        bytes[CORRUPT_OFFSET] = (bytes[CORRUPT_OFFSET].toInt() xor 0xFF).toByte()
        Files.write(archive, bytes)

        val source = LocalChapterSourceFactory(BoundedReaderMemoryBudget())
            .create(ArchiveFixtures.asset(temporaryDirectory, "valid-rar4.rar"))
        val page = source.pages().single()
        shouldThrow<ReaderFailure> {
            source.open(page.id).use { it.input.readBytes() }
        }
        source.close()
    }

    @Test
    fun `pages read across thread hops always carry the complete payload`(): Unit = runBlocking {
        ArchiveFixtures.copyCommittedFixture(temporaryDirectory, "valid-rar4.rar")
        val source = LocalChapterSourceFactory(BoundedReaderMemoryBudget())
            .create(ArchiveFixtures.asset(temporaryDirectory, "valid-rar4.rar"))
        val page = source.pages().single()
        val reads = (1..64).map {
            async(Dispatchers.IO) {
                val input = source.open(page.id)
                yield()
                withContext(Dispatchers.Default) { input.use { it.input.readBytes() } }
            }
        }.awaitAll()
        reads.forEach { bytes ->
            bytes.contentEquals(fixturePage) shouldBe true
        }
        source.close()
    }

    private companion object {
        const val CORRUPT_OFFSET = 70
    }
}
