package mihon.desktop.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MirrorHealthTest {
    private val url = "https://cdn.jsdelivr.net/gh/u/r@main/icon.png"
    private val candidates = cdnMirrorCandidates(url)

    @Test
    fun `failed hosts are skipped and last good host goes first`() {
        var now = 1_000L
        val health = MirrorHealth(file = null, failureTtlMillis = 100, clock = { now })
        health.recordFailure("cdn.jsdelivr.net")
        health.recordSuccess("gcore.jsdelivr.net")
        val ordered = health.order(candidates)
        assertEquals("https://gcore.jsdelivr.net/gh/u/r@main/icon.png", ordered.first())
        assertFalse(ordered.any { it.contains("cdn.jsdelivr.net") })
        now += 100
        assertTrue(health.order(candidates).any { it.contains("cdn.jsdelivr.net") }, "TTL expired")
    }

    @Test
    fun `all hosts failed falls back to full list`() {
        val health = MirrorHealth(file = null, clock = { 5L })
        listOf("cdn.jsdelivr.net", "fastly.jsdelivr.net", "gcore.jsdelivr.net", "raw.githubusercontent.com")
            .forEach(health::recordFailure)
        assertEquals(candidates, health.order(candidates))
    }

    @Test
    fun `state persists across instances`(@TempDir dir: Path) {
        val file = dir.resolve("mirror-health.properties")
        MirrorHealth(file, clock = { 10L }).apply {
            recordFailure("cdn.jsdelivr.net")
            recordSuccess("fastly.jsdelivr.net")
        }
        val reloaded = MirrorHealth(file, clock = { 20L })
        assertEquals("fastly.jsdelivr.net", reloaded.lastGoodHost)
        assertTrue(reloaded.isRecentlyFailed("cdn.jsdelivr.net"))
        assertEquals("https://fastly.jsdelivr.net/gh/u/r@main/icon.png", reloaded.order(candidates).first())
    }
}
