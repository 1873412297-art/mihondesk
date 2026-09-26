package mihon.desktop.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CustomCoverManagerTest {

    @Test
    fun `sets, gets, and removes custom cover`(@TempDir tempDir: Path) {
        val manager = CustomCoverManager(tempDir)
        val mangaId = 42L

        assertFalse(manager.hasCustomCover(mangaId))
        assertNull(manager.getCustomCover(mangaId))

        // Create sample cover file
        val sample = tempDir.resolve("my_cover.png")
        Files.writeString(sample, "fake image content")

        val setTarget = manager.setCustomCover(mangaId, sample)
        assertTrue(Files.exists(setTarget))
        assertTrue(manager.hasCustomCover(mangaId))

        val retrieved = manager.getCustomCover(mangaId)
        assertNotNull(retrieved)
        assertEquals(setTarget, retrieved)

        // Remove custom cover
        val removed = manager.removeCustomCover(mangaId)
        assertTrue(removed)
        assertFalse(manager.hasCustomCover(mangaId))
        assertNull(manager.getCustomCover(mangaId))
    }

    @Test
    fun `replaces existing custom cover when setting new one`(@TempDir tempDir: Path) {
        val manager = CustomCoverManager(tempDir)
        val mangaId = 99L

        val sample1 = tempDir.resolve("sample1.jpg")
        Files.writeString(sample1, "content1")
        val sample2 = tempDir.resolve("sample2.png")
        Files.writeString(sample2, "content2")

        manager.setCustomCover(mangaId, sample1)
        assertEquals("jpg", manager.getCustomCover(mangaId)?.fileName?.toString()?.substringAfterLast('.'))

        manager.setCustomCover(mangaId, sample2)
        assertEquals("png", manager.getCustomCover(mangaId)?.fileName?.toString()?.substringAfterLast('.'))
        // Old jpg should have been cleaned up
        assertFalse(Files.exists(manager.customDir.resolve("custom_99.jpg")))
    }

    @Test
    fun `negative cache avoids disk stat and invalidates on set and remove`(@TempDir tempDir: Path) {
        val manager = CustomCoverManager(tempDir)
        val mangaId = 123L

        // Initial check: doesn't exist, populates negative cache
        assertNull(manager.getCustomCover(mangaId))

        // Create file behind manager's back to prove negative cache is consulted
        val stealthCover = manager.customDir.resolve("custom_123.jpg")
        Files.createDirectories(manager.customDir)
        Files.writeString(stealthCover, "stealth")
        // Negative cache hit: still returns null
        assertNull(manager.getCustomCover(mangaId))

        // setCustomCover invalidates negative cache
        val sample = tempDir.resolve("sample.png")
        Files.writeString(sample, "real")
        manager.setCustomCover(mangaId, sample)
        assertNotNull(manager.getCustomCover(mangaId))

        // removeCustomCover invalidates cache and sets negative cache
        manager.removeCustomCover(mangaId)
        assertNull(manager.getCustomCover(mangaId))
    }
}
