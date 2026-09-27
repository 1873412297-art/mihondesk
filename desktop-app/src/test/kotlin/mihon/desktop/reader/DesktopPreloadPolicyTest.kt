package mihon.desktop.reader

import io.kotest.matchers.shouldBe
import mihon.reader.model.ReadingMode
import mihon.reader.prefetch.NavigationDirection
import mihon.reader.prefetch.PrefetchPolicy
import org.junit.jupiter.api.Test

class DesktopPreloadPolicyTest {

    @Test
    fun `default window matches the core prefetch policy exactly`() {
        val mode = ReadingMode.SINGLE_LTR
        DesktopPreloadPolicy.plan(20, 5, mode, NavigationDirection.FORWARD, PrefetchPolicy.AHEAD_PAGES) shouldBe
            PrefetchPolicy.plan(20, 5, mode, NavigationDirection.FORWARD)
        DesktopPreloadPolicy.plan(20, 5, mode, NavigationDirection.BACKWARD, PrefetchPolicy.AHEAD_PAGES) shouldBe
            PrefetchPolicy.plan(20, 5, mode, NavigationDirection.BACKWARD)
    }

    @Test
    fun `expanded window reaches further ahead nearest first`() {
        DesktopPreloadPolicy.plan(20, 5, ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD, 6) shouldBe
            listOf(6, 7, 8, 9, 10, 11, 4)
    }

    @Test
    fun `shrunk window prefetches fewer pages`() {
        DesktopPreloadPolicy.plan(20, 5, ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD, 1) shouldBe
            listOf(6, 4)
    }

    @Test
    fun `window clamps at chapter edges`() {
        DesktopPreloadPolicy.plan(8, 6, ReadingMode.SINGLE_LTR, NavigationDirection.FORWARD, 10) shouldBe
            listOf(7, 5)
    }

    @Test
    fun `dual page modes skip the spread partner then count ahead pages`() {
        DesktopPreloadPolicy.plan(20, 5, ReadingMode.DUAL_LTR, NavigationDirection.FORWARD, 2) shouldBe
            listOf(7, 8, 4)
        DesktopPreloadPolicy.plan(20, 5, ReadingMode.DUAL_LTR, NavigationDirection.BACKWARD, 2) shouldBe
            listOf(3, 2, 6)
    }

    @Test
    fun `clamp bounds the setting range`() {
        DesktopPreloadPolicy.clamp(0) shouldBe 1
        DesktopPreloadPolicy.clamp(1) shouldBe 1
        DesktopPreloadPolicy.clamp(10) shouldBe 10
        DesktopPreloadPolicy.clamp(99) shouldBe 10
    }
}
