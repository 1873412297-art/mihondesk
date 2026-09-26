package mihon.desktop.image

import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CoverFailureRegistryTest {

    @BeforeEach
    fun setUp() {
        CoverFailureRegistry.clearAll()
    }

    @Test
    fun `records only 403, 404, and 410 error codes`() {
        CoverFailureRegistry.record(1L, "https://example.com/cover1.jpg", 404)
        CoverFailureRegistry.record(2L, "https://example.com/cover2.jpg", 410)
        CoverFailureRegistry.record(3L, "https://example.com/cover3.jpg", 200)
        CoverFailureRegistry.record(4L, "https://example.com/cover4.jpg", 403)
        CoverFailureRegistry.record(5L, "https://example.com/cover5.jpg", 429)
        CoverFailureRegistry.record(6L, "https://example.com/cover6.jpg", 500)
        CoverFailureRegistry.record(7L, "https://example.com/cover7.jpg", 502)

        val snapshot = CoverFailureRegistry.snapshot()
        snapshot.size shouldBe 3
        snapshot[1L] shouldBe "https://example.com/cover1.jpg"
        snapshot[2L] shouldBe "https://example.com/cover2.jpg"
        snapshot[4L] shouldBe "https://example.com/cover4.jpg"
    }

    @Test
    fun `deduplicates multiple records for the same mangaId`() {
        CoverFailureRegistry.record(1L, "https://example.com/cover-old.jpg", 404)
        CoverFailureRegistry.record(1L, "https://example.com/cover-new.jpg", 404)

        val snapshot = CoverFailureRegistry.snapshot()
        snapshot.size shouldBe 1
        snapshot[1L] shouldBe "https://example.com/cover-new.jpg"
    }

    @Test
    fun `clear removes specified mangaId and clearAll removes all`() {
        CoverFailureRegistry.record(1L, "https://example.com/1.jpg", 404)
        CoverFailureRegistry.record(2L, "https://example.com/2.jpg", 410)
        CoverFailureRegistry.record(3L, "https://example.com/3.jpg", 404)

        CoverFailureRegistry.clear(2L)
        val snapshotAfterClear = CoverFailureRegistry.snapshot()
        snapshotAfterClear.size shouldBe 2
        snapshotAfterClear.containsKey(2L) shouldBe false

        CoverFailureRegistry.clearAll()
        CoverFailureRegistry.snapshot().shouldBeEmpty()
    }
}
