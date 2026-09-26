package mihon.desktop.ui.common

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaCoverTest {

    @Test
    fun `coverHeaders returns Referer with baseUrl when baseUrl is provided`() {
        val headers = coverHeaders("https://manga.bilibili.com", "https://image.bilibili.com/cover.jpg")
        headers shouldBe mapOf("Referer" to "https://manga.bilibili.com")
    }

    @Test
    fun `coverHeaders falls back to thumbnailUrl origin when baseUrl is null or blank`() {
        val headersFromNull = coverHeaders(null, "https://image.bilibili.com:8443/cover/pic.jpg?token=abc")
        headersFromNull shouldBe mapOf("Referer" to "https://image.bilibili.com:8443")

        val headersFromBlank = coverHeaders("   ", "http://static.example.org/pic.png")
        headersFromBlank shouldBe mapOf("Referer" to "http://static.example.org")
    }

    @Test
    fun `coverHeaders returns emptyMap when neither baseUrl nor valid thumbnailUrl origin is available`() {
        coverHeaders(null, null) shouldBe emptyMap()
        coverHeaders("", "") shouldBe emptyMap()
        coverHeaders(null, "/relative/path/cover.jpg") shouldBe emptyMap()
        coverHeaders(null, "invalid-url") shouldBe emptyMap()
    }
}
