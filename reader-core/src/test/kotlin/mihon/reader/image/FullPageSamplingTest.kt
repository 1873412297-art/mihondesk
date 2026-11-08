package mihon.reader.image

import io.kotest.matchers.shouldBe
import mihon.reader.model.FrameId
import mihon.reader.model.PageId
import org.junit.jupiter.api.Test

class FullPageSamplingTest {
    @Test
    fun `full pages use power of two sampling within the pixel budget`() {
        val key = fullPageTileKey(PageId("1", "large.jpg"), null, ImageMetadata(4000, 3000), 4_000_000)
        key.sampleSize shouldBe 2
        (key.bounds.width / key.sampleSize) * (key.bounds.height / key.sampleSize) shouldBe 3_000_000
    }

    @Test
    fun `animated pages preserve full frames and their frame identity`() {
        val frame = FrameId(PageId("1", "animated.gif"), 2)
        fullPageTileKey(frame.pageId, frame, ImageMetadata(4000, 3000, frameCount = 3), 4_000_000) shouldBe
            TileKey(frame.pageId, frame, IntRect(0, 0, 4000, 3000), sampleSize = 1)
    }
}
