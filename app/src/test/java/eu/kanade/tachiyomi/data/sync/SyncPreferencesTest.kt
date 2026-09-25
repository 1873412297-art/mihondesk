package eu.kanade.tachiyomi.data.sync

import mihon.sync.transport.http.SyncPairingCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class SyncPreferencesTest {

    @Test
    fun `default values match specifications`() {
        val store = InMemoryPreferenceStore()
        val prefs = SyncPreferences(store)

        assertEquals("", prefs.syncPairingCode.get())
        assertEquals("", prefs.syncHttpHost.get())
        assertEquals(45831, prefs.syncHttpPort.get())
        assertEquals("", prefs.syncHttpToken.get())
    }

    @Test
    fun `updateFromPairingCode populates all http preferences`() {
        val store = InMemoryPreferenceStore()
        val prefs = SyncPreferences(store)

        val code = SyncPairingCode(
            host = "192.168.1.100",
            port = 45831,
            token = "secretToken123",
        )

        prefs.updateFromPairingCode(code)

        assertEquals("mihonsync://192.168.1.100:45831#secretToken123", prefs.syncPairingCode.get())
        assertEquals("192.168.1.100", prefs.syncHttpHost.get())
        assertEquals(45831, prefs.syncHttpPort.get())
        assertEquals("secretToken123", prefs.syncHttpToken.get())
    }

    @Test
    fun `pairing code string can be parsed and updated into preferences`() {
        val store = InMemoryPreferenceStore()
        val prefs = SyncPreferences(store)

        val rawUri = "mihonsync://10.0.0.5:8080#tok_abc"
        val parsed = SyncPairingCode.parseOrNull(rawUri)
        assertNotNull(parsed)

        prefs.updateFromPairingCode(parsed!!)

        assertEquals("10.0.0.5", prefs.syncHttpHost.get())
        assertEquals(8080, prefs.syncHttpPort.get())
        assertEquals("tok_abc", prefs.syncHttpToken.get())
    }
}
