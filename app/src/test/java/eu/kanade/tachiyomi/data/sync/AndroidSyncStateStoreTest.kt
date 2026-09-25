package eu.kanade.tachiyomi.data.sync

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.Database

class AndroidSyncStateStoreTest {

    private lateinit var database: Database
    private lateinit var store: AndroidSyncStateStore

    @BeforeEach
    fun setup() = runBlocking {
        database = AndroidSyncTestHelper.createInMemoryDatabase()
        store = AndroidSyncStateStore(database, defaultDeviceId = { "test-device-1" })
    }

    @Test
    fun `getDeviceId initializes default ID and persists it`() = runBlocking {
        val deviceId = store.getDeviceId()
        assertEquals("test-device-1", deviceId)

        // Second call with different generator should still return persisted deviceId
        val store2 = AndroidSyncStateStore(database, defaultDeviceId = { "another-device" })
        assertEquals("test-device-1", store2.getDeviceId())
    }

    @Test
    fun `cursor updates are persisted`() = runBlocking {
        assertEquals(0L, store.getLastPushCursor())
        assertEquals(0L, store.getLastPullCursor())

        store.setLastPushCursor(42L)
        store.setLastPullCursor(88L)

        assertEquals(42L, store.getLastPushCursor())
        assertEquals(88L, store.getLastPullCursor())

        // Recreating store should read persisted values
        val store2 = AndroidSyncStateStore(database)
        assertEquals(42L, store2.getLastPushCursor())
        assertEquals(88L, store2.getLastPullCursor())
    }

    @Test
    fun `nextCursor increments monotonically`() = runBlocking {
        val c1 = store.nextCursor()
        val c2 = store.nextCursor()
        val c3 = store.nextCursor()

        assertEquals(1L, c1)
        assertEquals(2L, c2)
        assertEquals(3L, c3)
    }

    @Test
    fun `push watermark is separated from push cursor and persisted`() = runBlocking {
        assertEquals(0L, store.getLastPushWatermark())
        assertEquals(0L, store.getLastPushCursor())

        store.setLastPushWatermark(1700000000000L)
        store.setLastPushCursor(5L)

        assertEquals(1700000000000L, store.getLastPushWatermark())
        assertEquals(5L, store.getLastPushCursor())

        val store2 = AndroidSyncStateStore(database)
        assertEquals(1700000000000L, store2.getLastPushWatermark())
        assertEquals(5L, store2.getLastPushCursor())
    }

    @Test
    fun `per-peer pull watermarks are tracked independently`() = runBlocking {
        assertEquals(0L, store.getLastPullWatermark("peer-desktop"))
        assertEquals(0L, store.getLastPullWatermark("peer-tablet"))

        store.setLastPullWatermark("peer-desktop", 100L)
        store.setLastPullWatermark("peer-tablet", 200L)

        assertEquals(100L, store.getLastPullWatermark("peer-desktop"))
        assertEquals(200L, store.getLastPullWatermark("peer-tablet"))

        val all = store.getAllPeerPullWatermarks()
        assertEquals(2, all.size)
        assertEquals(100L, all["peer-desktop"])
        assertEquals(200L, all["peer-tablet"])

        // Recreating store should still see all peer watermarks
        val store2 = AndroidSyncStateStore(database)
        assertEquals(100L, store2.getLastPullWatermark("peer-desktop"))
        assertEquals(200L, store2.getLastPullWatermark("peer-tablet"))
        assertEquals(all, store2.getAllPeerPullWatermarks())
    }
}
