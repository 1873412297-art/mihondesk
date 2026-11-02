package eu.kanade.tachiyomi.data.sync

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AndroidDriveSyncStateStoreTest {

    @TempDir
    lateinit var dir: File

    private fun store(file: File = File(dir, "sync/gdrive-state.json")) =
        AndroidDriveSyncStateStore(file, deviceId = { "phone-1" })

    @Test
    fun `fresh store starts at zero and reuses the provided device id`() = runBlocking {
        val s = store()
        assertEquals("phone-1", s.getDeviceId())
        assertEquals(0L, s.getLastPushWatermark())
        assertEquals(0L, s.getLastPushCursor())
        assertEquals(emptyMap<String, Long>(), s.getAllPeerPullWatermarks())
        assertEquals(0L, s.lastSyncAt())
    }

    @Test
    fun `watermarks cursors and peers persist across instances`() = runBlocking {
        val s = store()
        s.setLastPushWatermark(123L)
        assertEquals(1L, s.nextCursor())
        assertEquals(2L, s.nextCursor())
        s.setLastPushCursor(2L)
        s.setLastPullWatermark("pc", 77L)
        s.recordSync(at = 999L, summary = "ok")

        val reopened = store()
        assertEquals(123L, reopened.getLastPushWatermark())
        assertEquals(2L, reopened.getLastPushCursor())
        assertEquals(3L, reopened.nextCursor())
        assertEquals(77L, reopened.getLastPullWatermark("pc"))
        assertEquals(mapOf("pc" to 77L), reopened.getAllPeerPullWatermarks())
        assertEquals(999L, reopened.lastSyncAt())
        assertEquals("ok", reopened.lastSummary())
    }

    @Test
    fun `setting a push cursor never moves the cursor clock backwards`() = runBlocking {
        val s = store()
        s.setLastPushCursor(10L)
        assertEquals(11L, s.nextCursor())
    }

    @Test
    fun `corrupt file falls back to defaults`() = runBlocking {
        val file = File(dir, "gdrive-state.json")
        file.writeText("{not json")
        val s = store(file)
        assertEquals(0L, s.getLastPushWatermark())
        s.setLastPushWatermark(5L)
        assertEquals(5L, store(file).getLastPushWatermark())
    }

    @Test
    fun `reset forgets everything so a different account starts clean`() = runBlocking {
        val file = File(dir, "gdrive-state.json")
        val s = store(file)
        s.setLastPushWatermark(5L)
        s.recordSync(1L, "x")
        s.reset()
        assertFalse(file.exists())
        assertEquals(0L, s.getLastPushWatermark())
        assertEquals(0L, s.lastSyncAt())
    }
}
