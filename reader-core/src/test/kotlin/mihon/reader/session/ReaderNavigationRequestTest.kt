package mihon.reader.session

import io.kotest.matchers.shouldBe
import mihon.reader.model.PageDescriptor
import mihon.reader.model.PageId
import mihon.reader.model.ReaderPosition
import org.junit.jupiter.api.Test

class ReaderNavigationRequestTest {
    @Test
    fun `viewport observations preserve explicit navigation identity and destination`() {
        val selected = ready().reduce(ReaderAction.SelectPage(4))
        val scrolled = selected.reduce(ReaderAction.SetViewportAnchor(ReaderPosition(7, 30)))

        scrolled.viewportAnchor shouldBe ReaderPosition(7, 30)
        scrolled.navigationRequest shouldBe selected.navigationRequest
        scrolled.navigationRequest?.position shouldBe ReaderPosition(4)
    }

    @Test
    fun `selecting the same page again issues a new request to return to its top`() {
        val first = ready().reduce(ReaderAction.SelectPage(4))
        val scrolled = first.reduce(ReaderAction.SetViewportAnchor(ReaderPosition(4, 300)))
        val repeated = scrolled.reduce(ReaderAction.SelectPage(4))

        repeated.navigationRequest?.sequence shouldBe requireNotNull(first.navigationRequest).sequence + 1L
        repeated.navigationRequest?.position shouldBe ReaderPosition(4)
        repeated.viewportAnchor shouldBe ReaderPosition(4)
    }

    private fun ready() = ReaderState.ready(
        chapterId = 7,
        pages = List(8) { PageDescriptor(PageId("7", "$it.jpg"), 800, 1200) },
        selectedIndex = 0,
    )
}
