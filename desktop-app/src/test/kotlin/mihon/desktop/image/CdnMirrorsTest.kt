package mihon.desktop.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CdnMirrorsTest {
    @Test
    fun `jsdelivr urls expand to mirrors and raw github`() {
        val url = "https://cdn.jsdelivr.net/gh/keiyoushi/extensions-source@main/" +
            "src/zh/vomic/res/mipmap-xhdpi/ic_launcher.png"
        assertEquals(
            listOf(
                url,
                "https://fastly.jsdelivr.net/gh/keiyoushi/extensions-source@main/src/zh/vomic/res/mipmap-xhdpi/ic_launcher.png",
                "https://gcore.jsdelivr.net/gh/keiyoushi/extensions-source@main/src/zh/vomic/res/mipmap-xhdpi/ic_launcher.png",
                "https://raw.githubusercontent.com/keiyoushi/extensions-source/main/src/zh/vomic/res/mipmap-xhdpi/ic_launcher.png",
            ),
            cdnMirrorCandidates(url),
        )
    }

    @Test
    fun `raw github urls keep primary first then jsdelivr mirrors`() {
        val url = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/icon/eu.kanade.x.png"
        val result = cdnMirrorCandidates(url)
        assertEquals(url, result.first())
        assertEquals("https://cdn.jsdelivr.net/gh/keiyoushi/extensions@repo/icon/eu.kanade.x.png", result[1])
        assertEquals(4, result.size)
    }

    @Test
    fun `other hosts are untouched`() {
        assertEquals(listOf("https://example.org/a.png"), cdnMirrorCandidates("https://example.org/a.png"))
    }
}
