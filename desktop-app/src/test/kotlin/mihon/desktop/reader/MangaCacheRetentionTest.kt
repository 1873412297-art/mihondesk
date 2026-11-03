package mihon.desktop.reader

import io.kotest.matchers.shouldBe
import mihon.desktop.navigation.DesktopDestination
import org.junit.jupiter.api.Test

class MangaCacheRetentionTest {
    @Test
    fun `chapter changes and the same manga details keep the cache`() {
        mangaCacheRetention(DesktopDestination.Reader(4L), libraryMangaId = 7L, readerMangaId = 7L) shouldBe
            MangaCacheRetention.Switch(7L)
        mangaCacheRetention(DesktopDestination.Reader(5L), libraryMangaId = 7L, readerMangaId = 7L) shouldBe
            MangaCacheRetention.Switch(7L)
        mangaCacheRetention(DesktopDestination.Library, libraryMangaId = 7L, readerMangaId = null) shouldBe
            MangaCacheRetention.Switch(7L)
        mangaCacheRetention(DesktopDestination.MangaDetails(7L), libraryMangaId = null, readerMangaId = null) shouldBe
            MangaCacheRetention.Switch(7L)
    }

    @Test
    fun `leaving the manga clears and an unresolved reader does not`() {
        mangaCacheRetention(DesktopDestination.Library, libraryMangaId = null, readerMangaId = null) shouldBe
            MangaCacheRetention.Clear
        mangaCacheRetention(DesktopDestination.Browse, libraryMangaId = 7L, readerMangaId = 7L) shouldBe
            MangaCacheRetention.Clear
        mangaCacheRetention(DesktopDestination.Settings, libraryMangaId = 7L, readerMangaId = null) shouldBe
            MangaCacheRetention.Clear
        mangaCacheRetention(DesktopDestination.Reader(4L), libraryMangaId = null, readerMangaId = null) shouldBe
            MangaCacheRetention.Keep
        mangaCacheRetention(DesktopDestination.MangaDetails(8L), libraryMangaId = 7L, readerMangaId = 7L) shouldBe
            MangaCacheRetention.Switch(8L)
    }
}
