package mihon.desktop.track

import kotlinx.coroutines.runBlocking
import mihon.desktop.platform.CredentialStore
import mihon.desktop.preferences.DesktopPreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopTrackerStoreTest {

    @TempDir
    lateinit var tempDir: Path

    // In-memory stand-in for the OS keychain. The default WindowsCredentialStore talks to the real
    // Credential Manager, which is unavailable under a network/SSH logon (fails with 1312), so a
    // unit test must inject its own store rather than reach for the machine's.
    private class InMemoryCredentials : CredentialStore {
        private val values = mutableMapOf<String, String>()
        override fun read(target: String) = values[target]
        override fun write(target: String, secret: String): Boolean = true.also { values[target] = secret }
        override fun delete(target: String): Boolean = values.remove(target) != null
    }

    @Test
    fun `tracker store persists credentials and restores login`() = runBlocking {
        val prefFile = tempDir.resolve("preferences.properties")
        val prefStore = DesktopPreferenceStore(prefFile)
        val trackerStore = DesktopTrackerStore(prefStore, InMemoryCredentials())

        assertFalse(trackerStore.isLoggedIn(1L))
        assertNull(trackerStore.getLoginInfo(1L))

        // Save credentials for MAL (trackerId = 1)
        trackerStore.saveLogin(trackerId = 1L, username = "TestUser", token = "mypassword")
        assertTrue(trackerStore.isLoggedIn(1L))
        assertEquals("TestUser", trackerStore.getUsername(1L))
        assertEquals("mypassword", trackerStore.getAuthToken(1L))
        val info = trackerStore.getLoginInfo(1L)
        assertNotNull(info)
        assertEquals("TestUser", info?.username)
        assertEquals("mypassword", info?.token)

        // Save server URL for Komga (trackerId = 6)
        trackerStore.saveLogin(
            trackerId = 6L,
            username = "KomgaAdmin",
            token = "secret-key",
            serverUrl = "https://komga.local:8080",
        )
        assertTrue(trackerStore.isLoggedIn(6L))
        assertEquals("https://komga.local:8080", trackerStore.getServerUrl(6L))

        // Initialize DesktopTrackerManager with trackerStore to verify restore on boot
        val manager = DesktopTrackerManager(store = trackerStore)
        val mal = manager.get(1L)
        assertNotNull(mal)
        assertTrue(mal!!.isLoggedIn)
        assertEquals("TestUser", mal.username)

        val komga = manager.get(6L)
        assertNotNull(komga)
        assertTrue(komga!!.isLoggedIn)
        assertEquals("KomgaAdmin", komga.username)
        assertEquals("https://komga.local:8080", komga.serverUrl)
        assertTrue(trackerStore.isLoggedIn(6L))

        // Logout MAL
        manager.logout(1L)
        assertFalse(mal.isLoggedIn)
        assertFalse(trackerStore.isLoggedIn(1L))
        assertNull(trackerStore.getLoginInfo(1L))

        // Login via manager persists to store (fake tracker keeps this test independent of network).
        val fakeAniList = TrackerTestFakeTracker(id = 2L, name = "AniList")
        val loginManager = DesktopTrackerManager(listOf(fakeAniList), store = trackerStore)
        val success = loginManager.login(
            2L,
            mapOf("username" to "AniUser", "token" to "oauth-token-12345"),
        )
        assertTrue(success)
        val anilist = loginManager.get(2L)!!
        assertTrue(anilist.isLoggedIn)
        assertEquals("AniUser", anilist.username)
        assertTrue(trackerStore.isLoggedIn(2L))
        assertEquals("AniUser", trackerStore.getUsername(2L))
        assertEquals("oauth-token-12345", trackerStore.getAuthToken(2L))
    }

    @Test
    fun `rejected credential write does not report a new login`() = runBlocking {
        val preferences = DesktopPreferenceStore(tempDir.resolve("preferences.properties"))
        val stored = InMemoryCredentials()
        DesktopTrackerStore(preferences, stored).saveLogin(1L, "old", "old-token")
        val store = DesktopTrackerStore(preferences, RejectingWrites(stored))
        val tracker = TrackerTestFakeTracker(id = 1L)
        val manager = DesktopTrackerManager(listOf(tracker), store = store)

        assertTrue(tracker.isLoggedIn)
        assertFalse(manager.login(1L, mapOf("username" to "new", "token" to "new-token")))
        assertEquals("old", tracker.username)
        assertEquals("old-token", tracker.persistenceToken)
        assertEquals("old-token", store.getAuthToken(1L))
        assertEquals("old", store.getUsername(1L))
    }

    @Test
    fun `first login is rejected when the credential cannot be stored`() = runBlocking {
        val preferences = DesktopPreferenceStore(tempDir.resolve("fresh-preferences.properties"))
        val store = DesktopTrackerStore(preferences, RejectingWrites(InMemoryCredentials()))
        val tracker = TrackerTestFakeTracker(id = 2L)
        val manager = DesktopTrackerManager(listOf(tracker), store = store)

        assertFalse(manager.login(2L, mapOf("token" to "new-token")))
        assertFalse(tracker.isLoggedIn)
        assertFalse(store.isLoggedIn(2L))
        assertNull(store.getAuthToken(2L))
    }

    private class RejectingWrites(private val backing: CredentialStore) : CredentialStore {
        override fun read(target: String): String? = backing.read(target)
        override fun write(target: String, secret: String): Boolean = false
        override fun delete(target: String): Boolean = backing.delete(target)
    }
}
